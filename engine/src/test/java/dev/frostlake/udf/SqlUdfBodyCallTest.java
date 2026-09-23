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

package dev.frostlake.udf;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A SQL UDF body's function calls are judged at CREATE: every call to a name nothing declares is named in one
 * sentence, an inner call before the call holding it; a call reaching back to the routine is a cycle; a call
 * written with OVER must name a window function; and a function the account builds in is never unknown. The
 * body's other names speak first. Every cell is live-verified.
 */
public class SqlUdfBodyCallTest extends BaseDatabaseTest {

    private static final String ERROR = "SQL compilation error:|";

    /** "created", or the refusal on one line. */
    private String create(final String sql) {
        try {
            engine.execute(sql);
            return "created";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The first row's first cell, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    @Test
    public void everyUnknownFunctionIsNamedInOneSentence() {
        engine.execute("CREATE FUNCTION f2() RETURNS INT AS '1'");
        final String[][] cells = {
            {"CREATE FUNCTION u1() RETURNS INT AS 'nosuchfunc(1)'", ERROR + "Unknown function NOSUCHFUNC."},
            {"CREATE FUNCTION u2() RETURNS VARIANT AS 'nosuchfunc(1)'", ERROR + "Unknown function NOSUCHFUNC."},
            {"CREATE FUNCTION u3() RETURNS INT AS 'SELECT nosuchfunc(1)'", ERROR + "Unknown function NOSUCHFUNC."},
            {"CREATE FUNCTION u4() RETURNS INT AS 'nosucha(1) + nosuchb(2)'", ERROR + "Unknown functions NOSUCHA, NOSUCHB."},
            {"CREATE FUNCTION u5() RETURNS INT AS 'nosucha(nosuchb(2))'", ERROR + "Unknown functions NOSUCHB, NOSUCHA."},
            {"CREATE FUNCTION u6() RETURNS INT AS 'ABS(nosuchd(1)) + nosuche(2)'", ERROR + "Unknown functions NOSUCHD, NOSUCHE."},
            {"CREATE FUNCTION u7() RETURNS INT AS 'CASE WHEN nosuchx(1) > 0 THEN nosuchy(2) ELSE 0 END'",
                ERROR + "Unknown functions NOSUCHX, NOSUCHY."},
            {"CREATE FUNCTION u8() RETURNS INT AS '(SELECT nosuchc(1))'", ERROR + "Unknown function NOSUCHC."},
            {"CREATE FUNCTION u9() RETURNS INT AS 'nosuchf(1) + test_schema.nosuchg(2)'", ERROR + "Unknown function NOSUCHF."},
            {"CREATE FUNCTION u10() RETURNS INT AS 'test_schema.nosuchg(2) + test_db.test_schema.nosuchh(3)'",
                ERROR + "Unknown user-defined functions TEST_SCHEMA.NOSUCHG, TEST_DB.TEST_SCHEMA.NOSUCHH."},
            {"CREATE FUNCTION u11() RETURNS INT AS 'nosuchschema.f2()'", ERROR + "Unknown user-defined function NOSUCHSCHEMA.F2."},
            {"CREATE FUNCTION u12() RETURNS INT AS 'SNOWFLAKE.NOSUCH.F()'", ERROR + "Unknown user-defined function SNOWFLAKE.NOSUCH.F."},
            {"CREATE FUNCTION u13() RETURNS INT AS 'SELECT COUNT(*) FROM TABLE(FLATTEN(PARSE_JSON(''[1]''))) WHERE nosuch(value) > 0'",
                ERROR + "Unknown function NOSUCH."},
            {"CREATE FUNCTION u14() RETURNS TABLE (a INT) AS 'SELECT nosuch(1)'", ERROR + "Unknown function NOSUCH."},
            // The body's other names speak first.
            {"CREATE FUNCTION u15(x INT) RETURNS INT AS 'nosuchfunc(y)'",
                "SQL compilation error: error line 1 at position 12|invalid identifier 'Y'"},
            {"CREATE FUNCTION u16() RETURNS INT AS 'SELECT a FROM nosuchtable WHERE nosuch(a) > 0'",
                hinted(ERROR + "Object 'TEST_DB.TEST_SCHEMA.NOSUCHTABLE' does not exist or not authorized.")},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], create(cell[0]), cell[0]);
        }
    }

    @Test
    public void aFunctionTheAccountBuildsInIsNeverUnknown() {
        assertEquals("created", create("CREATE FUNCTION h3text() RETURNS VARCHAR AS 'H3_LATLNG_TO_CELL_STRING(37.7887, -122.3931, 12)'"));
        assertEquals("created", create("CREATE FUNCTION h3query() RETURNS INT AS 'SELECT H3_GET_RESOLUTION(617700169958293503)'"));
        assertEquals("created", create("CREATE FUNCTION quoted() RETURNS INT AS '\"abs\"(-1)'"));
        assertEquals("created", create("CREATE FUNCTION branches(x INT) RETURNS INT AS 'IFF(x > 0, COALESCE(x, 0), DECODE(x, 1, 2, 3))'"));
        assertEquals("created", create("CREATE FUNCTION numbered() RETURNS INT AS 'ROW_NUMBER() OVER (ORDER BY 1)'"));
        assertEquals("1", answer("SELECT quoted()"));
    }

    @Test
    public void aCallReachingBackToTheRoutineIsACycle() {
        engine.execute("CREATE FUNCTION v7() RETURNS INT AS '1'");
        assertEquals("Detected a cycle in SQL UDF: V7", create("CREATE OR REPLACE FUNCTION v7() RETURNS INT AS 'v7()'"));
        assertEquals("Detected a cycle in SQL UDF: V11", create("CREATE FUNCTION v11(x INT) RETURNS INT AS 'v11()'"));
        assertEquals("Detected a cycle in SQL UDF: E2", create("CREATE FUNCTION e2() RETURNS INT AS 'nosuch(1) + e2()'"));
        assertEquals("Detected a cycle in SQL UDF: lc", create("CREATE FUNCTION \"lc\"() RETURNS INT AS '\"lc\"()'"));
        engine.execute("CREATE FUNCTION v9() RETURNS INT AS '1'");
        engine.execute("CREATE FUNCTION v10() RETURNS INT AS 'v9()'");
        assertEquals("Detected a cycle in SQL UDF: V9", create("CREATE OR REPLACE FUNCTION v9() RETURNS INT AS 'v10()'"));
        assertEquals(ERROR + "Unknown function V9X.", create("CREATE FUNCTION v8() RETURNS INT AS 'v9x()'"));
    }

    @Test
    public void aCallWithOverMustNameAWindowFunction() {
        assertEquals(ERROR + "Invalid function type [NOSUCHX] for window function.",
            create("CREATE FUNCTION w1() RETURNS INT AS 'nosuchx(1) OVER (ORDER BY 1)'"));
    }
}
