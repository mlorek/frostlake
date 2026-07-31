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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Grammar-breadth singletons from the Snowflake-corpus audit: LIMIT NULL, bare DESCRIBE,
 * INSERT OVERWRITE TABLE, comma-separated ALTER SESSION SET, USE SECONDARY ROLES lists,
 * $$-quoted column comments, DATE_PART('part' FROM expr), star function arguments, and the
 * honest rejection of AT (STREAM =&gt; ...) time travel.
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
    public void bareDescribeResolvesTheTable() {
        engine.execute("CREATE TABLE bd (id INTEGER, name VARCHAR)");
        assertEquals(2, engine.executeQuery("DESCRIBE bd").getRowCount());
        assertEquals(2, engine.executeQuery("DESCRIBE test_db.test_schema.bd").getRowCount());
    }

    @Test
    public void insertOverwriteTableKeywordTruncatesThenInserts() {
        engine.execute("CREATE TABLE io (a INTEGER)");
        engine.execute("INSERT INTO io VALUES (1), (2)");
        engine.execute("INSERT OVERWRITE TABLE io SELECT 9");
        final ResultSet rs = engine.executeQuery("SELECT a FROM io");
        assertEquals(1, rs.getRowCount(), "OVERWRITE must replace the previous rows");
        assertEquals(9L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void alterSessionSetAppliesEveryCommaSeparatedAssignment() {
        engine.execute("ALTER SESSION SET autocommit = FALSE, QUERY_TAG = 'qtag', JSON_INDENT = 1");
        assertEquals("qtag", engine.getSessionContext().getSessionParameter("QUERY_TAG"));
        assertEquals(1L, ((Number) engine.getSessionContext().getSessionParameter("JSON_INDENT")).longValue());
        assertFalse(engine.isAutoCommit(), "AUTOCOMMIT in the list must drive the transaction mode");
        engine.execute("ALTER SESSION SET autocommit = TRUE");
        assertTrue(engine.isAutoCommit());
    }

    @Test
    public void useSecondaryRolesAcceptsARoleList() {
        engine.execute("CREATE ROLE sr_a");
        engine.execute("CREATE ROLE sr_b");
        engine.execute("USE SECONDARY ROLES sr_a, sr_b");
        assertEquals(1L, ((Number) scalar("SELECT 1")).longValue(), "the session stays usable");
    }

    @Test
    public void dollarQuotedColumnComment() {
        engine.execute("CREATE TABLE dq (id INTEGER COMMENT $$some comment$$)");
        assertEquals("some comment", engine.getCatalog().getDatabase("TEST_DB").getSchema("TEST_SCHEMA")
            .getTable("DQ").getColumn("ID").getComment());
    }

    @Test
    public void datePartAcceptsAQuotedPartWithFrom() {
        assertEquals(4L, ((Number) scalar("SELECT DATE_PART('month' FROM CAST('2024-04-08' AS DATE))")).longValue());
        assertEquals(2024L, ((Number) scalar("SELECT DATE_PART('year' FROM CAST('2024-04-08' AS DATE))")).longValue());
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
        engine.execute("CREATE TABLE cbs (col1 INTEGER, col2 VARCHAR, other INTEGER)");
        engine.execute("INSERT INTO cbs VALUES (1, 'a', 9)");
        assertEquals(3, engine.executeQuery("SELECT {*} FROM cbs").getColumns().size());
        assertEquals(2, engine.executeQuery("SELECT {* EXCLUDE (col1)} FROM cbs").getColumns().size());
        assertEquals(1, engine.executeQuery("SELECT {* EXCLUDE (col1, col2)} FROM cbs").getColumns().size());
        assertEquals("COL1", engine.executeQuery("SELECT {* ILIKE 'col1%'} FROM cbs")
            .getColumns().get(0).getName());
        assertEquals(3, engine.executeQuery("SELECT {cbs.*} FROM cbs").getColumns().size());
        // The braces must not disturb JSON object literals or the plain star.
        assertEquals(3, engine.executeQuery("SELECT * FROM cbs").getColumns().size());
        assertEquals("{\"a\":1}", String.valueOf(scalar("SELECT {'a': 1}")));
    }

    @Test
    public void temporaryFileFormat() {
        engine.execute("CREATE TEMPORARY FILE FORMAT ff_tmp TYPE=PARQUET COMPRESSION=auto");
        engine.execute("CREATE TEMP FILE FORMAT ff_tmp2 TYPE=CSV");
        assertEquals(1, engine.executeQuery("SHOW FILE FORMATS LIKE 'FF_TMP'").getRowCount());
    }

    @Test
    public void streamTimeTravelPointIsRejectedHonestly() {
        engine.execute("CREATE TABLE ct (c1 INTEGER)");
        engine.execute("SET ts2 = '2030-01-01'");
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT c1 FROM ct CHANGES (INFORMATION => APPEND_ONLY) "
                    + "AT (STREAM => 's1') END (TIMESTAMP => $ts2)");
            }
        });
        assertTrue(e.getMessage().contains("not supported"), "unexpected message: " + e.getMessage());
    }
}
