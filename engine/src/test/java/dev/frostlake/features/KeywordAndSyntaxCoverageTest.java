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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Snowflake grammar constructs that were each previously rejected as an "SQL syntax error":
 * {@code ALTER SESSION UNSET}, a table alias in UPDATE/DELETE, the {@code NUMERIC(p,s)} type, PIVOT over a
 * parenthesized subquery, {@code CREATE TABLE … LIKE}, and a handful of reserved keywords used as plain
 * column, alias, CTE, or VARIANT-path names.
 */
public class KeywordAndSyntaxCoverageTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void alterSessionUnset() {
        engine.execute("ALTER SESSION SET QUERY_TAG = 'x'");
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER SESSION UNSET QUERY_TAG");
                engine.execute("ALTER SESSION UNSET QUERY_TAG, TIMEZONE");
            }
        });
    }

    @Test
    public void updateWithTableAlias() {
        engine.execute("CREATE TABLE ua (x INT, y INT)");
        engine.execute("INSERT INTO ua VALUES (1, 10), (2, 20)");
        engine.execute("UPDATE ua AS t SET t.x = 99 WHERE t.y = 20");
        assertEquals(99, ((Number) scalar("SELECT x FROM ua WHERE y = 20")).intValue());
        // unqualified alias form too
        engine.execute("UPDATE ua u SET u.x = 5 WHERE u.y = 10");
        assertEquals(5, ((Number) scalar("SELECT x FROM ua WHERE y = 10")).intValue());
    }

    @Test
    public void deleteWithTableAlias() {
        engine.execute("CREATE TABLE da (x INT)");
        engine.execute("INSERT INTO da VALUES (1), (2), (3)");
        engine.execute("DELETE FROM da AS d WHERE d.x = 2");
        assertEquals(2L, ((Number) scalar("SELECT COUNT(*) FROM da")).longValue());
    }

    @Test
    public void numericTypeAndCast() {
        engine.execute("CREATE TABLE nt (a NUMERIC(18,5))");
        engine.execute("INSERT INTO nt VALUES (1.5)");
        assertEquals(0, new java.math.BigDecimal("1.5").compareTo(new java.math.BigDecimal(scalar("SELECT a FROM nt").toString())));
        assertEquals(0, new java.math.BigDecimal("7").compareTo(new java.math.BigDecimal(scalar("SELECT 7::NUMERIC(38,0)").toString())));
    }

    @Test
    public void pivotOverSubquery() {
        engine.execute("CREATE TABLE pv (k INT, cat INT, v INT)");
        engine.execute("INSERT INTO pv VALUES (1, 10, 5), (1, 20, 7)");
        // PIVOT applied to a parenthesized subquery (not just a bare table name).
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM (SELECT cat, v FROM pv) PIVOT(SUM(v) FOR cat IN (10, 20)) p");
        assertEquals(1, rs.getRowCount());
    }

    @Test
    public void createTableLikeCopiesStructureOnly() {
        engine.execute("CREATE TABLE src_like (a INT, b VARCHAR)");
        engine.execute("INSERT INTO src_like VALUES (1, 'x'), (2, 'y')");
        engine.execute("CREATE OR REPLACE TEMPORARY TABLE cp_like LIKE src_like");
        // Same columns …
        assertEquals(2L, ((Number) scalar(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'CP_LIKE'")).longValue());
        // … but no data (LIKE copies structure only, unlike CLONE).
        assertEquals(0L, ((Number) scalar("SELECT COUNT(*) FROM cp_like")).longValue());
    }

    @Test
    public void reservedWordsAsColumnNames() {
        // START/CLUSTER/IDENTITY/CHANGES/STREAM/NETWORK/NUMERIC are keyword tokens that are also valid as
        // ordinary column names.
        engine.execute("CREATE TABLE rw (start INT, cluster INT, identity INT, changes INT, "
            + "stream INT, network INT, numeric INT)");
        engine.execute("INSERT INTO rw VALUES (1, 2, 3, 4, 5, 6, 7)");
        assertEquals(5, ((Number) scalar("SELECT stream FROM rw")).intValue());
        assertEquals(1, ((Number) scalar("SELECT start FROM rw")).intValue());
    }

    @Test
    public void nextAsColumnNameAndAlias() {
        // NEXT is a keyword token here only for FETCH FIRST/NEXT n ROWS, and is not reserved in Snowflake,
        // so it has to stay usable as an ordinary column name and column alias.
        engine.execute("CREATE TABLE nx (next INT)");
        engine.execute("INSERT INTO nx VALUES (7)");
        assertEquals(7, ((Number) scalar("SELECT next FROM nx")).intValue());
        assertEquals(8, ((Number) scalar("SELECT next + 1 AS next FROM nx")).intValue());
    }

    @Test
    public void unpivotAsCteName() {
        final ResultSet rs = engine.executeQuery("WITH unpivot AS (SELECT 1 AS x) SELECT x FROM unpivot");
        assertEquals(1, ((Number) rs.getRows().get(0).getValue(0)).intValue());
    }

    @Test
    public void isDistinctFromIsNullSafe() {
        assertEquals(Boolean.TRUE, scalar("SELECT 1 IS DISTINCT FROM 2"));
        assertEquals(Boolean.FALSE, scalar("SELECT 1 IS DISTINCT FROM 1"));
        // NULL is treated as a comparable value (unlike =): NULL vs 1 differ, NULL vs NULL are equal.
        assertEquals(Boolean.TRUE, scalar("SELECT NULL IS DISTINCT FROM 1"));
        assertEquals(Boolean.TRUE, scalar("SELECT NULL IS NOT DISTINCT FROM NULL"));
        assertEquals(Boolean.FALSE, scalar("SELECT NULL IS DISTINCT FROM NULL"));
    }

    @Test
    public void reservedWordsAsBareTableAliases() {
        engine.execute("CREATE TABLE ta (x INT)");
        engine.execute("INSERT INTO ta VALUES (9)");
        // STREAM / IDENTITY as a bare (no-AS) table alias.
        assertEquals(9, ((Number) scalar("SELECT stream.x FROM ta stream")).intValue());
        assertEquals(9, ((Number) scalar("SELECT identity.x FROM ta identity")).intValue());
    }

    @Test
    public void startTransactionBeginsAndCommits() {
        engine.execute("CREATE TABLE tx (x INT)");
        engine.execute("START TRANSACTION");
        engine.execute("INSERT INTO tx VALUES (1)");
        engine.execute("COMMIT");
        assertEquals(1L, ((Number) scalar("SELECT COUNT(*) FROM tx")).longValue());
    }

    @Test
    public void partitionByParenthesizedList() {
        engine.execute("CREATE TABLE pw (a INT, b INT, x INT)");
        engine.execute("INSERT INTO pw VALUES (1, 1, 5), (1, 1, 6), (2, 2, 9)");
        // PARTITION BY (a, b) — a parenthesized key list, equivalent to the unparenthesised form.
        final ResultSet rs = engine.executeQuery(
            "SELECT x FROM pw QUALIFY ROW_NUMBER() OVER (PARTITION BY (a, b) ORDER BY x) = 1 ORDER BY x");
        assertEquals(2, rs.getRowCount()); // one row per (a,b) group
    }

    @Test
    public void alterTableAddColumnWithoutKeyword() {
        engine.execute("CREATE TABLE ac (x INT)");
        engine.execute("INSERT INTO ac VALUES (1)");
        // ALTER TABLE … ADD <col> <type> — the COLUMN keyword is optional in Snowflake.
        engine.execute("ALTER TABLE ac ADD checksum_group VARCHAR");
        assertEquals(2L, ((Number) scalar(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'AC'")).longValue());
    }

    @Test
    public void keywordAsVariantPathField() {
        engine.execute("CREATE TABLE vp (v VARIANT)");
        engine.execute("INSERT INTO vp SELECT PARSE_JSON('{\"database\":\"d1\",\"update\":\"u1\"}')");
        // DATABASE is a keyword but is valid as a VARIANT path key (e.g. v:database) — and it stays valid
        // inside a function argument, e.g. lower(v:database::string).
        assertEquals("d1", scalar("SELECT v:database::string FROM vp"));
        assertEquals("D1", scalar("SELECT UPPER(v:database::string) FROM vp"));
        // UPDATE likewise — v:update::VARCHAR reads a keyword-named field.
        assertEquals("u1", scalar("SELECT v:update::VARCHAR FROM vp"));
        // A keyword path key mixed among other cast path keys in one SELECT list.
        assertEquals("d1", scalar("SELECT v:database::VARCHAR AS a, v:update::VARCHAR AS b FROM vp"));
    }

    @Test
    public void reservedKeywordsAsVariantPathKeys() {
        // A VARIANT path key is just a JSON field name, so ANY reserved keyword is valid there — via `:`
        // (object access) and `.` (nested field access), with or without a trailing cast.
        final ResultSet rs = engine.executeQuery("""
            WITH r AS (
              SELECT {'create': 1, 'drop': 2, 'alter': {'show': 1}} AS j
            )
            SELECT j:create::int AS c, j:drop AS d, j:alter.show AS s1, j:alter:show AS s2
            FROM r
            """);
        final Row row = rs.getRows().get(0);
        assertEquals(1L, ((Number) row.getValue(0)).longValue());
        assertEquals("2", String.valueOf(row.getValue(1)));
        assertEquals("1", String.valueOf(row.getValue(2)));
        assertEquals("1", String.valueOf(row.getValue(3)));
    }

    @Test
    public void moreReservedKeywordPathKeys() {
        engine.execute("CREATE TABLE vp2 (v VARIANT)");
        engine.execute("INSERT INTO vp2 SELECT PARSE_JSON('{\"from\":\"f\",\"group\":\"g\",\"order\":\"o\",\"where\":\"w\",\"case\":\"c\"}')");
        assertEquals("f", scalar("SELECT v:from::VARCHAR FROM vp2"));
        assertEquals("g", scalar("SELECT v:group::VARCHAR FROM vp2"));
        assertEquals("o", scalar("SELECT v:order::VARCHAR FROM vp2"));
        assertEquals("w", scalar("SELECT v:where::VARCHAR FROM vp2"));
        assertEquals("c", scalar("SELECT v:case::VARCHAR FROM vp2"));
    }

    @Test
    public void insertStringFunction() {
        // INSERT(base, pos, len, new) — a keyword that also names a string function, and one that is often
        // nested, e.g. TO_TIMESTAMP_NTZ(INSERT(INSERT(...))).
        assertEquals("azzzef", scalar("SELECT INSERT('abcdef', 2, 3, 'zzz')"));
    }

    @Test
    public void alterTableAddMultipleColumns() {
        // ALTER TABLE ... ADD col1 t1, col2 t2 — two columns added in a single statement.
        engine.execute("CREATE TABLE mc (a INT)");
        engine.execute("ALTER TABLE mc ADD b VARCHAR, c VARCHAR");
        assertEquals(3, engine.executeQuery("SELECT * FROM mc").getColumns().size());
    }
}
