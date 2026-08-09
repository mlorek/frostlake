/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Grammar-breadth singletons from the Snowflake-corpus audit: LIMIT NULL, bare DESCRIBE,
 * INSERT OVERWRITE INTO (the TABLE-keyword form is rejected), comma-separated ALTER SESSION SET,
 * USE SECONDARY ROLES lists, $$-quoted column comments, the rejection of DATE_PART's FROM form
 * (EXTRACT-only), star function arguments, and the honest rejection of AT (STREAM =&gt; ...)
 * time travel.
 */
public class SyntaxBreadthSingletonsTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void limitNullMeansNoLimit() {
        engine.execute("CREATE TABLE ln (a INTEGER)");
        engine.execute("INSERT INTO ln VALUES (1), (2), (3)");
        assertEquals(3, engine.executeQuery("SELECT a FROM ln ORDER BY a LIMIT NULL").getRowCount());
        assertEquals(2, engine.executeQuery("SELECT a FROM ln ORDER BY a LIMIT NULL OFFSET 1").getRowCount());
        assertEquals(3, engine.executeQuery("SELECT a FROM ln WHERE a > 0 LIMIT NULL").getRowCount(),
            "the streaming WHERE+LIMIT path honours LIMIT NULL too");
        assertEquals(1, engine.executeQuery("SELECT 1 ORDER BY 1 LIMIT NULL OFFSET 0").getRowCount());
    }

    @Test
    public void bareDescribeIsRejectedLikeSnowflake() {
        engine.execute("CREATE TABLE bd (id INTEGER, name VARCHAR)");
        // Live-verified: DESCRIBE requires the object type (DESCRIBE bd is a syntax error there).
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("DESCRIBE bd");
            }
        });
        assertEquals(2, engine.executeQuery("DESCRIBE TABLE bd").getRowCount());
        assertEquals(2, engine.executeQuery("DESCRIBE TABLE test_db.test_schema.bd").getRowCount());
    }

    @Test
    public void insertOverwriteIntoTruncatesThenInserts() {
        engine.execute("CREATE TABLE io (a INTEGER)");
        engine.execute("INSERT INTO io VALUES (1), (2)");
        engine.execute("INSERT OVERWRITE INTO io SELECT 9");
        final ResultSet rs = engine.executeQuery("SELECT a FROM io");
        assertEquals(1, rs.getRowCount(), "OVERWRITE must replace the previous rows");
        assertEquals(9L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        // Live-verified: the TABLE-keyword form (INSERT OVERWRITE TABLE t) is a syntax error.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT OVERWRITE TABLE io SELECT 8");
            }
        });
    }

    @Test
    public void alterSessionSetAppliesEveryCommaSeparatedAssignment() {
        engine.execute("ALTER SESSION SET autocommit = FALSE, QUERY_TAG = 'qtag', JSON_INDENT = 1");
        // The statement runs on both backends; only the read-back is embedded-only.
        Assumptions.assumeFalse(isLiveSnowflake(),
            "reads the applied values back through engine.getSessionContext()/isAutoCommit(), which exist "
            + "only on the embedded engine — a live session's parameters live on the account");
        assertEquals("qtag", engine.getSessionContext().getSessionParameter("QUERY_TAG"));
        assertEquals(1L, ((Number) engine.getSessionContext().getSessionParameter("JSON_INDENT")).longValue());
        assertFalse(engine.isAutoCommit(), "AUTOCOMMIT in the list must drive the transaction mode");
        engine.execute("ALTER SESSION SET autocommit = TRUE");
        assertTrue(engine.isAutoCommit());
    }

    @Test
    public void useSecondaryRolesAcceptsARoleList() {
        // Secondary roles must be granted to the executing user just like the primary one:
        // live-verified, the activation otherwise fails "Requested role 'SR_A' is not assigned to the
        // executing user." The working spelling is GRANT ROLE <role> TO USER <user> — live Snowflake
        // rejects GRANT ROLE r TO USER IDENTIFIER(CURRENT_USER()) with a syntax error at the '(' —
        // so the executing user's name is read first and spliced into the statement.
        final String user = String.valueOf(scalar("SELECT CURRENT_USER()"));
        // The account is stateful across runs, so create tolerantly and hand everything back below.
        engine.execute("CREATE ROLE IF NOT EXISTS sr_a");
        engine.execute("CREATE ROLE IF NOT EXISTS sr_b");
        // Live: the executing user already exists (no-op). Embedded: CURRENT_USER() is not in the
        // catalog until something creates it, and GRANT ROLE ... TO USER needs it to exist.
        engine.execute("CREATE USER IF NOT EXISTS \"" + user + "\"");
        engine.execute("GRANT ROLE sr_a TO USER \"" + user + "\"");
        engine.execute("GRANT ROLE sr_b TO USER \"" + user + "\"");
        engine.execute("USE SECONDARY ROLES sr_a, sr_b");
        assertEquals(1L, ((Number) scalar("SELECT 1")).longValue(), "the session stays usable");

        engine.execute("USE SECONDARY ROLES NONE");
        engine.execute("REVOKE ROLE sr_a FROM USER \"" + user + "\"");
        engine.execute("REVOKE ROLE sr_b FROM USER \"" + user + "\"");
        engine.execute("DROP ROLE IF EXISTS sr_a");
        engine.execute("DROP ROLE IF EXISTS sr_b");
    }

    @Test
    public void dollarQuotedColumnComment() {
        engine.execute("CREATE TABLE dq (id INTEGER COMMENT $$some comment$$)");
        // The CREATE runs on both backends; only the read-back is embedded-only.
        Assumptions.assumeFalse(isLiveSnowflake(),
            "reads the stored column comment through engine.getCatalog(), which live Snowflake never "
            + "populates (the account's comment lives in its own INFORMATION_SCHEMA)");
        assertEquals("some comment", engine.getCatalog().getDatabase("TEST_DB").getSchema("TEST_SCHEMA")
            .getTable("DQ").getColumn("ID").getComment());
    }

    @Test
    public void datePartRejectsTheFromForm() {
        // Live-verified: the FROM argument form belongs to EXTRACT only; DATE_PART rejects it.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT DATE_PART('month' FROM CAST('2024-04-08' AS DATE))");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT DATE_PART('year' FROM CAST('2024-04-08' AS DATE))");
            }
        });
        // The comma form works.
        assertEquals(4L, ((Number) scalar("SELECT DATE_PART('month', CAST('2024-04-08' AS DATE))")).longValue());
    }

    @Test
    public void starFunctionArgumentParsesButUnknownFunctionStillFails() {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT MINHASH(5, *)");
            }
        });
        assertFalse(String.valueOf(e.getMessage()).contains("syntax"),
            "MINHASH(5, *) must be a runtime gap, not a grammar gap: " + e.getMessage());
    }

    @Test
    public void valuesAliasInsideTheParens() {
        // Snowflake allows the derived-table alias INSIDE the parens, with AS optional:
        // SELECT * FROM (VALUES (0) foo(bar)) yields one column named BAR.
        final ResultSet rs = engine.executeQuery("SELECT * FROM (VALUES (0) foo(bar))");
        assertEquals(1, rs.getColumns().size());
        assertEquals("BAR", rs.getColumns().get(0).getName());
        assertEquals(2, engine.executeQuery(
            "SELECT v1.col1 FROM (VALUES (1, 'one'), (2, 'two') AS v1 (col1, col2))").getRowCount());
        assertEquals(7L, ((Number) scalar("SELECT bar FROM (VALUES (7) foo(bar)) WHERE foo.bar = 7")).longValue());
        // The alias after the closing paren still works and still wins.
        assertEquals("N", engine.executeQuery("SELECT * FROM (VALUES (1), (2)) t(n)").getColumns().get(0).getName());
    }

    @Test
    public void flattenAcceptsABareSubqueryInput() {
        // Snowflake allows an unparenthesized subquery as a named-argument value:
        // TABLE(FLATTEN(input => SELECT ...)) — two rows, one VALUE column here.
        final ResultSet rs = engine.executeQuery(
            "SELECT value FROM TABLE(FLATTEN(input => SELECT PARSE_JSON('[1,2]')))");
        assertEquals(2, rs.getRowCount());
        assertEquals("VALUE", rs.getColumns().get(0).getName());
        assertEquals(3, engine.executeQuery(
            "SELECT value FROM LATERAL FLATTEN(input => SELECT PARSE_JSON('[7,8,9]'))").getRowCount(),
            "the bare FLATTEN table source takes the subquery form too");
    }

    @Test
    public void usingJoinMergesColumnsAndAcceptsQualifiedNames() {
        // USING may name a qualified column (USING (t2.c)); SELECT * outputs ONE merged column.
        final ResultSet cte = engine.executeQuery(
            "with t1 as (select 1 as c), t2 as (select 1 as c) select * from t1 join t2 using (t2.c)");
        assertEquals(1, cte.getColumns().size());
        assertEquals("C", cte.getColumns().get(0).getName());
        assertEquals(1, cte.getRowCount());

        engine.execute("CREATE TABLE uj1 (k INTEGER, x VARCHAR)");
        engine.execute("CREATE TABLE uj2 (k INTEGER, y VARCHAR)");
        engine.execute("INSERT INTO uj1 VALUES (1, 'l')");
        engine.execute("INSERT INTO uj2 VALUES (1, 'r')");
        assertEquals(3, engine.executeQuery("SELECT * FROM uj1 JOIN uj2 USING (k)").getColumns().size(),
            "the using column appears once in SELECT *");
        assertEquals(3, engine.executeQuery("SELECT * FROM uj1 NATURAL JOIN uj2").getColumns().size(),
            "NATURAL JOIN merges the common column the same way");
        assertEquals(4, engine.executeQuery("SELECT uj1.k, uj2.k, x, y FROM uj1 JOIN uj2 USING (k)")
            .getColumns().size(), "both sides stay individually addressable");
    }

    @Test
    public void updateAcceptsFromBeforeSet() {
        engine.execute("CREATE TABLE upd (sample_query_id VARCHAR, qry_hash_count INTEGER)");
        engine.execute("INSERT INTO upd VALUES ('match-id', 0), ('other-id', 0)");
        // Snowflake accepts the FROM clause BEFORE SET, with an alias on the target.
        engine.execute("UPDATE upd u FROM (SELECT 5195 AS new_count, 'match-id' AS query_id) b "
            + "SET qry_hash_count = new_count WHERE u.sample_query_id = b.query_id");
        assertEquals(5195L, ((Number) scalar(
            "SELECT qry_hash_count FROM upd WHERE sample_query_id = 'match-id'")).longValue());
        assertEquals(0L, ((Number) scalar(
            "SELECT qry_hash_count FROM upd WHERE sample_query_id = 'other-id'")).longValue());
        // The documented SET-then-FROM order keeps working.
        engine.execute("UPDATE upd u SET qry_hash_count = 7 FROM (SELECT 'other-id' AS query_id) b "
            + "WHERE u.sample_query_id = b.query_id");
        assertEquals(7L, ((Number) scalar(
            "SELECT qry_hash_count FROM upd WHERE sample_query_id = 'other-id'")).longValue());
    }

    @Test
    public void curlyBraceStarSelectItems() {
        // Live-verified: the BRACED star is an OBJECT constructor over the row ({*} is
        // OBJECT_CONSTRUCT(*)), so it projects ONE column keyed by the column names — not the star's
        // N columns. The star modifiers still choose which columns take part.
        engine.execute("CREATE TABLE cbs (col1 INTEGER, col2 VARCHAR, other INTEGER)");
        engine.execute("INSERT INTO cbs VALUES (1, 'a', 9)");
        assertEquals(1, engine.executeQuery("SELECT {*} FROM cbs").getColumns().size());
        assertEquals("{\"COL1\":1,\"COL2\":\"a\",\"OTHER\":9}",
            String.valueOf(scalar("SELECT {*} FROM cbs")));
        assertEquals(1, engine.executeQuery("SELECT {* EXCLUDE (col1)} FROM cbs").getColumns().size());
        assertEquals("{\"COL2\":\"a\",\"OTHER\":9}",
            String.valueOf(scalar("SELECT {* EXCLUDE (col1)} FROM cbs")));
        assertEquals("{\"OTHER\":9}",
            String.valueOf(scalar("SELECT {* EXCLUDE (col1, col2)} FROM cbs")));
        assertEquals("{\"COL1\":1}", String.valueOf(scalar("SELECT {* ILIKE 'col1%'} FROM cbs")));
        assertEquals("{\"COL1\":1,\"COL2\":\"a\",\"OTHER\":9}",
            String.valueOf(scalar("SELECT {cbs.*} FROM cbs")));
        // A braced star takes only the modifiers that PICK columns: the projection-REWRITING RENAME and
        // REPLACE are rejected there (live-verified), though they stay legal on a plain star.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT {* RENAME (col1 AS c1)} FROM cbs");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT {* REPLACE (col1 + 100 AS col1)} FROM cbs");
            }
        });
        assertEquals(3, engine.executeQuery("SELECT * RENAME (col1 AS c1) FROM cbs").getColumns().size());
        // It is one item among many, not a whole-list shorthand.
        assertEquals(2, engine.executeQuery("SELECT {*}, col1 FROM cbs").getColumns().size());
        // Unlike the star it constructs from, it does NOT need a FROM clause: with no row to read it
        // simply constructs the empty object. Live-verified on a real account —
        // SELECT {*} answers {}, as does SELECT {* EXCLUDE (a)} and SELECT OBJECT_CONSTRUCT(*).
        assertEquals("{}", String.valueOf(scalar("SELECT {*}")));
        assertEquals("{}", String.valueOf(scalar("SELECT {* EXCLUDE (col1)}")));
        // The braces must not disturb JSON object literals or the plain star.
        assertEquals(3, engine.executeQuery("SELECT * FROM cbs").getColumns().size());
        assertEquals(3, engine.executeQuery("SELECT cbs.* FROM cbs").getColumns().size());
        assertEquals("{\"a\":1}", String.valueOf(scalar("SELECT {'a': 1}")));
    }

    @Test
    public void curlyBraceStarColumnIsLabelledWithItsSourceForm() {
        // Live-verified: the output column's NAME is the item's own source form with its identifiers
        // upper-cased — SELECT {* EXCLUDE (a)} reports the column as {* EXCLUDE (A)}.
        engine.execute("CREATE TABLE cbl (a INTEGER, b INTEGER, c VARCHAR)");
        engine.execute("INSERT INTO cbl VALUES (1, 2, 'x')");
        assertEquals("{*}", engine.executeQuery("SELECT {*} FROM cbl").getColumns().get(0).getName());
        assertEquals("{* EXCLUDE (A)}",
            engine.executeQuery("SELECT {* EXCLUDE (a)} FROM cbl").getColumns().get(0).getName());
        assertEquals("{CBL.*}",
            engine.executeQuery("SELECT {cbl.*} FROM cbl").getColumns().get(0).getName());
        // An explicit alias still wins over the echoed source form.
        assertEquals("ROW_OBJ",
            engine.executeQuery("SELECT {*} AS row_obj FROM cbl").getColumns().get(0).getName());
        assertEquals("{\"A\":1,\"B\":2,\"C\":\"x\"}",
            String.valueOf(scalar("SELECT {*} AS row_obj FROM cbl")));
        // A NULL column is omitted from the object, exactly as OBJECT_CONSTRUCT omits a NULL value.
        engine.execute("INSERT INTO cbl VALUES (3, NULL, NULL)");
        assertEquals("{\"A\":3}",
            String.valueOf(scalar("SELECT {*} FROM cbl WHERE a = 3")));
    }

    @Test
    public void temporaryFileFormat() {
        engine.execute("CREATE TEMPORARY FILE FORMAT ff_tmp TYPE=PARQUET COMPRESSION=auto");
        engine.execute("CREATE TEMP FILE FORMAT ff_tmp2 TYPE=CSV");
        assertEquals(1, engine.executeQuery("SHOW FILE FORMATS LIKE 'FF_TMP'").getRowCount());
    }

    /**
     * A stream offset is a valid CHANGES start point, so what fails here is the END point: a window
     * that runs into the future is refused before any change is read.
     */
    @Test
    public void aFutureChangesEndPointIsRejected() {
        engine.execute("CREATE TABLE ct (c1 INTEGER)");
        engine.execute("CREATE STREAM s1 ON TABLE ct");
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT c1 FROM ct CHANGES (INFORMATION => APPEND_ONLY) "
                    + "AT (STREAM => 's1') END (TIMESTAMP => '2030-01-01'::TIMESTAMP)");
            }
        });
        assertTrue(e.getMessage().contains("Future data is not yet available for table CT."),
            "unexpected message: " + e.getMessage());
    }
}
