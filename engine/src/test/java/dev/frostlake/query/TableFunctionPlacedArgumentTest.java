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
 * A built-in table function's call that writes a positional argument after a named one is read as the account reads
 * it (live-verified): FLATTEN binds each positional argument to the parameter of its place among every argument written
 * — INPUT, PATH, OUTER, RECURSIVE, MODE — beside the named ones, LATERAL FLATTEN alike; a positional argument of the
 * wrong type is refused by its place's number, a missing INPUT and more than five arguments at the function's name.
 * GENERATOR leaves such an argument unread, and SPLIT_TO_TABLE and STRTOK_SPLIT_TO_TABLE refuse the name they take
 * none of first.
 */
public class TableFunctionPlacedArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void aTrailingPositionalArgumentIsReadByItsPlace() {
        engine.execute("CREATE OR REPLACE TABLE fl (v VARIANT)");
        engine.execute("INSERT INTO fl SELECT PARSE_JSON('[1,2]')");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 1, 2))", "1"},
            {"SELECT COUNT(*) FROM TABLE(FLATTEN(PATH => '', PARSE_JSON('[1]')))",
                "REFUSED SQL compilation error: error line 1 at position 27 | missing required argument [INPUT] "
                + "for function [FLATTEN]"},
            {"SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[1]'), ''))", "1"},
            {"SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[1,2]}'), 'a'))", "2"},
            {"SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[1,2]}'), 'a', TRUE))", "2"},
            {"SELECT COUNT(*) FROM TABLE(FLATTEN(PARSE_JSON('{\"a\":[1,2]}'), PATH => 'a', TRUE))", "2"},
            {"SELECT COUNT(*) FROM TABLE(FLATTEN(PARSE_JSON('{\"a\":[1,2]}'), OUTER => TRUE, 'a'))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter '3'"},
            {"SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[1,2]}'), PATH => 'a', 'b'))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter '3'"},
            {"SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[1,2]}'), 'a', PATH => 'a'))", "2"},
            {"SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[1]'), 'x', TRUE, TRUE, 'ARRAY', 6))",
                "REFUSED SQL compilation error: error line 1 at position 27 | too many arguments for function "
                + "[FLATTEN] expected 5, got 6"},
            {"SELECT COUNT(*) FROM fl, LATERAL FLATTEN(INPUT => fl.v, '')", "2"},
            {"SELECT COUNT(*) FROM TABLE(STRTOK_SPLIT_TO_TABLE(x => 'a b', ' '))",
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid argument for function "
                + "[STRTOK_SPLIT_TO_TABLE] unexpected argument [X] at position 1,"},
            {"SELECT COUNT(*) FROM TABLE(SPLIT_TO_TABLE(x => 'a b', ' '))",
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid argument for function "
                + "[SPLIT_TO_TABLE] unexpected argument [X] at position 1,"},
            {"SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[[1],[2]]'), '', FALSE, TRUE))", "4"},
            {"SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":1}'), '', FALSE, FALSE, 'OBJECT'))", "1"},
            {"SELECT VALUE FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[1,2]}'), 'a')) ORDER BY 1", "1 ;; 2"},
        });
    }
}
