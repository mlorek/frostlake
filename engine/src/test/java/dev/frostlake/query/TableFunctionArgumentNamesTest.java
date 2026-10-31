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

package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * The names a table function's arguments read compile with the statement whatever rows reach the call: a column or
 * a function nothing answers to - written in the argument or inside a subquery there, correlated or not - is
 * refused under a query that reads no row of its FROM and over an empty relation to the call's left, as over rows
 * (all live-verified).
 */
public class TableFunctionArgumentNamesTest extends MeasuredCellsTestSupport {

    @Test
    public void namesUnderAQueryThatReadsNoRow() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 37 | invalid identifier 'NOSUCH_COL'",
            "SELECT * FROM TABLE(FLATTEN(INPUT => nosuch_col)) WHERE 1 = 0");
        assertAnswer("REFUSED SQL compilation error: | Unknown function NOSUCH_FN.",
            "SELECT * FROM TABLE(FLATTEN(INPUT => nosuch_fn(1))) WHERE 1 = 0");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 35 | invalid identifier 'NOSUCH_COL'",
            "SELECT * FROM TABLE(SPLIT_TO_TABLE(nosuch_col, ',')) WHERE 1 = 0");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 37 | invalid identifier 'NOSUCH_COL'",
            "SELECT * FROM TABLE(FLATTEN(INPUT => nosuch_col)) LIMIT 0");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 39 | invalid identifier 'NOSUCH_COL'",
            "SELECT * FROM LATERAL FLATTEN(INPUT => nosuch_col) WHERE 1 = 0");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 37 | invalid identifier 'NOSUCH_COL'",
            "SELECT * FROM TABLE(FLATTEN(INPUT => nosuch_col))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 45 | invalid identifier 'NOSUCH_COL'",
            "SELECT * FROM TABLE(FLATTEN(INPUT => (SELECT nosuch_col FROM fz))) WHERE 1 = 0");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 73 | invalid identifier 'NOSUCH_COL'",
            "SELECT * FROM TABLE(FLATTEN(INPUT => (SELECT ARRAY_AGG(id) FROM fz WHERE nosuch_col = 1))) WHERE 1 = 0");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 69 | invalid identifier 'NOSUCH_COL'",
            "SELECT fz.id FROM fz, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT((SELECT nosuch_col FROM fz f2)))) f WHERE "
                + "1 = 0");
        assertAnswer("", "SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('['))) LIMIT 0");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 20 | invalid argument for function "
                + "[FLATTEN] unexpected argument [NOSUCH_ARG] at position 3,",
            "SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[1]'), PATH => 'x', nosuch_arg => 1)) WHERE 1 = 0");
        assertAnswer("REFUSED SQL compilation error: | invalid type [NUMBER(19,5)] for parameter 'INPUT'",
            "SELECT * FROM TABLE(FLATTEN(INPUT => 1 + 'a')) WHERE 1 = 0");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void namesOverAnEmptyLeftRelation() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE ez (id INT)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 69 | invalid identifier 'NOSUCH_COL'",
            "SELECT fz.id FROM fz, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT((SELECT nosuch_col FROM fz f2)))) f");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 69 | invalid identifier 'NOSUCH_COL'",
            "SELECT ez.id FROM ez, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT((SELECT nosuch_col FROM fz f2)))) f");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 61 | invalid identifier 'NOSUCH_COL'",
            "SELECT ez.id FROM ez, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(nosuch_col))) f");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 61 | invalid identifier 'EZ.NOSUCH_COL'",
            "SELECT ez.id FROM ez, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(ez.nosuch_col))) f");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 15 | invalid identifier 'NOSUCH_COL'",
            "SELECT (SELECT nosuch_col FROM fz f2) FROM ez");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 41 | invalid identifier 'NOSUCH_COL'",
            "SELECT * FROM ez WHERE 1 = 0 AND (SELECT nosuch_col FROM fz f2) = 1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 45 | invalid identifier 'NOSUCH_COL'",
            "SELECT * FROM TABLE(FLATTEN(INPUT => (SELECT nosuch_col FROM fz)))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 73 | invalid identifier 'NOSUCH_COL'",
            "SELECT * FROM TABLE(FLATTEN(INPUT => (SELECT ARRAY_AGG(id) FROM ez WHERE nosuch_col = 1)))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 97 | invalid identifier 'F2.NOSUCH_COL'",
            "SELECT ez.id FROM ez, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT((SELECT MAX(f2.id) FROM fz f2 WHERE "
                + "f2.nosuch_col = ez.id)))) f");
        assertAnswer("REFUSED SQL compilation error: | Unknown function NOSUCH_FN.",
            "SELECT ez.id FROM ez, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(nosuch_fn(ez.id)))) f");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 43 | invalid identifier 'EZ.NOSUCH_COL'",
            "SELECT ez.id FROM ez, TABLE(SPLIT_TO_TABLE(ez.nosuch_col, ',')) s");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 71 | invalid identifier 'NOSUCH_COL'",
            "SELECT ez.id FROM ez, LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT((SELECT nosuch_col FROM fz f2))) f");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
