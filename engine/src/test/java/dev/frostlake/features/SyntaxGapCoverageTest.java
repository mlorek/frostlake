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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Grammar constructs that were each previously rejected as an "SQL syntax error", surfaced by a
 * deterministic parse sweep of real Snowflake procedure/view bodies:
 * <ul>
 *   <li>a boolean-valued lambda body — {@code FILTER(arr, p -> NOT is_null_value(p))},</li>
 *   <li>a boolean-valued CASE branch — {@code CASE WHEN c THEN TRUE ELSE a != b OR c != d END},</li>
 *   <li>a CTE before {@code SELECT … INTO} — {@code WITH cte AS (…) SELECT … INTO :a, :b FROM cte},</li>
 *   <li>CTAS with an explicit typed column list — {@code CREATE TABLE t (id VARCHAR) AS SELECT …}.</li>
 * </ul>
 */
public class SyntaxGapCoverageTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    // ---- boolean-valued lambda bodies (FILTER / TRANSFORM) ----

    @Test
    public void lambdaBodyWithNot() {
        // p -> NOT (p > 2) — a lambda whose body is a top-level NOT (a booleanExpr, not a bare expression).
        assertEquals("[1,2]", String.valueOf(scalar("SELECT FILTER([1, 2, 3, 4], x -> NOT (x > 2))")));
    }

    @Test
    public void lambdaBodyWithAnd() {
        assertEquals("[2,3]", String.valueOf(scalar("SELECT FILTER([1, 2, 3, 4], x -> x > 1 AND x < 4)")));
    }

    @Test
    public void lambdaBodyWithNotFunction() {
        // The real form from the sweep: FILTER(arr, p -> NOT IS_NULL_VALUE(p)).
        assertEquals("[1,2,3]", String.valueOf(scalar("SELECT FILTER([1, 2, 3], p -> NOT IS_NULL_VALUE(p))")));
    }

    // ---- boolean-valued CASE branches ----

    @Test
    public void caseElseIsBareBooleanOr() {
        // ELSE a != b OR c != d — a bare boolean in the ELSE branch (no parentheses).
        assertEquals(Boolean.TRUE, scalar("SELECT CASE WHEN 1 = 2 THEN TRUE ELSE 3 != 4 OR 5 != 5 END"));
    }

    @Test
    public void caseThenIsBareBooleanAnd() {
        assertEquals(Boolean.TRUE, scalar("SELECT CASE WHEN 1 = 1 THEN 2 = 2 AND 3 = 3 ELSE FALSE END"));
    }

    @Test
    public void simpleCaseThenIsBareBoolean() {
        assertEquals(Boolean.TRUE, scalar("SELECT CASE 1 WHEN 1 THEN 'a' != 'b' ELSE FALSE END"));
    }

    @Test
    public void caseReturningValueStillWorks() {
        // Regression: a CASE that yields ordinary values must be unaffected.
        assertEquals(100, ((Number) scalar("SELECT CASE WHEN 1 = 1 THEN 100 ELSE 200 END")).intValue());
    }

    // ---- WITH before SELECT ... INTO ----

    @Test
    public void withClauseBeforeSelectInto() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p_with_into() RETURNS VARCHAR LANGUAGE SQL AS $$
            DECLARE
                x INTEGER;
                y INTEGER;
            BEGIN
                WITH cte AS (SELECT 10 AS a, 20 AS b) SELECT a, b INTO :x, :y FROM cte;
                RETURN :x || '/' || :y;
            END $$""");
        assertEquals("10/20", String.valueOf(scalar("CALL p_with_into()")));
    }

    // ---- CTAS with an explicit typed column list ----

    @Test
    public void ctasWithTypedColumnList() {
        engine.execute("CREATE OR REPLACE TEMPORARY TABLE ct_typed (ITEM_CODE VARCHAR) AS SELECT UPPER('abc') AS x");
        assertEquals("ABC", scalar("SELECT ITEM_CODE FROM ct_typed"));
        // The declared name AND type define the table (not the SELECT's output column).
        assertEquals("ITEM_CODE", scalar(
            "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'CT_TYPED'"));
        assertEquals("VARCHAR", scalar(
            "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'CT_TYPED'"));
    }

    @Test
    public void ctasWithTypedMultiColumnList() {
        engine.execute("CREATE OR REPLACE TABLE ct_multi (a NUMBER(10,0), b VARCHAR) AS SELECT 5, 'hi'");
        final ResultSet rs = engine.executeQuery("SELECT a, b FROM ct_multi");
        assertEquals(5, ((Number) rs.getRows().get(0).getValue(0)).intValue());
        assertEquals("hi", rs.getRows().get(0).getValue(1));
    }

    @Test
    public void ctasNamesOnlyListStillWorks() {
        // Regression: the names-only (no types) CTAS list must still take types from the SELECT.
        engine.execute("CREATE OR REPLACE TABLE ct_names (nm, tot) AS SELECT 'x', 9");
        final ResultSet rs = engine.executeQuery("SELECT nm, tot FROM ct_names");
        assertEquals("x", rs.getRows().get(0).getValue(0));
        assertEquals(9, ((Number) rs.getRows().get(0).getValue(1)).intValue());
    }

    // ---- a semi-structured path key that is a reserved keyword ----

    @Test
    public void variantPathKeyMayBeAReservedKeyword() {
        // src:account.status — 'account' is otherwise the ACCOUNT keyword, but a JSON path key may be any word.
        engine.execute("CREATE OR REPLACE TABLE vk (src VARIANT)");
        engine.execute("INSERT INTO vk SELECT OBJECT_CONSTRUCT('account', OBJECT_CONSTRUCT('status', 'OK'))");
        assertEquals("OK", String.valueOf(scalar("SELECT src:account.status::VARCHAR FROM vk")));
    }

    // ---- DO as an identifier (table alias + column qualifier) ----
    // `DO` is only the WHILE/FOR ... DO loop keyword; it is not reserved in Snowflake, so it must also be
    // usable as an ordinary name — e.g. the real loader's `FROM detection_output do ... SELECT do.col`.

    @Test
    public void doUsableAsTableAliasAndColumnQualifier() {
        engine.execute("CREATE OR REPLACE TABLE dt (region_id INTEGER, item_code INTEGER)");
        engine.execute("INSERT INTO dt VALUES (1, 10), (2, 20)");
        final ResultSet rs = engine.executeQuery(
            "SELECT do.region_id, do.item_code FROM dt do WHERE do.region_id = 1");
        assertEquals(1, ((Number) rs.getRows().get(0).getValue(0)).intValue());
        assertEquals(10, ((Number) rs.getRows().get(0).getValue(1)).intValue());
    }

    // ---- trailing comma in the FROM list (Snowflake allows it, as in the SELECT list) ----

    @Test
    public void trailingCommaInFromList() {
        engine.execute("CREATE OR REPLACE TABLE tca (id INTEGER)");
        engine.execute("CREATE OR REPLACE TABLE tcb (id INTEGER)");
        engine.execute("INSERT INTO tca VALUES (1), (2)");
        engine.execute("INSERT INTO tcb VALUES (1)");
        // trailing comma after the last FROM item, before WHERE — was a parse error
        final ResultSet rs = engine.executeQuery("SELECT tca.id FROM tca, tcb, WHERE tca.id = tcb.id");
        assertEquals(1, rs.getRowCount());
        assertEquals(1, ((Number) rs.getRows().get(0).getValue(0)).intValue());
    }

    @Test
    public void trailingCommaInSelectAndFromTogether() {
        engine.execute("CREATE OR REPLACE TABLE tcc (a INTEGER, b INTEGER)");
        engine.execute("INSERT INTO tcc VALUES (7, 8)");
        // both a trailing SELECT-list comma and a trailing FROM-list comma
        final ResultSet rs = engine.executeQuery("SELECT a, b, FROM tcc, WHERE a = 7");
        assertEquals(7, ((Number) rs.getRows().get(0).getValue(0)).intValue());
        assertEquals(8, ((Number) rs.getRows().get(0).getValue(1)).intValue());
    }

    @Test
    public void doAsIdentifierDoesNotBreakWhileDoLoop() {
        // Regression: DO must still parse as the loop keyword despite also being an allowed identifier.
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p_while_do() RETURNS INTEGER LANGUAGE SQL AS $$
            DECLARE i INTEGER DEFAULT 0;
            BEGIN
              WHILE i < 3 DO i := i + 1; END WHILE;
              RETURN i;
            END $$""");
        assertEquals(3, ((Number) scalar("CALL p_while_do()")).intValue());
    }
}
