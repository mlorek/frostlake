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
package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The lines after a fault inside the braces of a star read as a value, or straight after them inside a call:
 * a comma, a word, a literal, a second modifier, DISTINCT, a RENAME or a REPLACE, another pair of braces
 * (live-verified).
 */
public class BracedStarLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void aCommaAndAModifierInsideTheBraces() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 27 unexpected '1'. | syntax error line 1 at position 34 unexpected ')'.",
            "SELECT TO_JSON({* REPLACE (1 AS id)}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 26 unexpected 'id'. | syntax error line 1 at position 29 unexpected 'AS'.",
            "SELECT TO_JSON({* RENAME (id AS k)}) FROM FZ ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 20 unexpected '}'.",
            "SELECT TO_JSON({*, 1}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ','. | syntax error "
                + "line 1 at position 43 unexpected '}'.",
            "SELECT TO_JSON({* EXCLUDE (id), EXCLUDE (b)}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'ILIKE'. | syntax "
                + "error line 1 at position 38 unexpected '}'.",
            "SELECT TO_JSON({* EXCLUDE b ILIKE 'i%'}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '{'. | syntax error "
                + "line 1 at position 21 unexpected '}'.",
            "SELECT TO_JSON({*} {*}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'x'.",
            "SELECT TO_JSON({*} x) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 27 unexpected '('.",
            "SELECT TO_JSON({*} REPLACE (1 AS id)) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'DISTINCT'. | syntax "
                + "error line 1 at position 26 unexpected '}'.",
            "SELECT TO_JSON({DISTINCT *}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','.",
            "SELECT TO_JSON({*,}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ','. | syntax error "
                + "line 1 at position 21 unexpected '}'.",
            "SELECT TO_JSON({id, *}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(FZ.ID, FZ.B))'",
            "SELECT TO_JSON(DISTINCT {*}) FROM FZ ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 27 unexpected '1'. | syntax error line 1 at position 34 unexpected ')'.",
            "SELECT TO_JSON({* REPLACE (1 AS id)})");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 27 unexpected '1'. | syntax error line 1 at position 34 unexpected ')'.",
            "SELECT TO_JSON({* REPLACE (1 AS id), 2}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 27 unexpected '1'. | syntax error line 1 at position 42 unexpected ')'.",
            "SELECT TO_JSON({* REPLACE (1 AS id, 2 AS b)}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 33 unexpected '}'.",
            "SELECT TO_JSON({* REPLACE 1 AS id}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 26 unexpected 'id'. | syntax error line 1 at position 29 unexpected 'AS'.",
            "SELECT TO_JSON({* RENAME (id AS k)}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 28 unexpected 'AS'.",
            "SELECT TO_JSON({* RENAME id AS k}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 26 unexpected 'id'. | syntax error line 1 at position 29 unexpected 'AS'.",
            "SELECT TO_JSON({* RENAME (id AS k, b AS c)}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 23 unexpected '}'.",
            "SELECT TO_JSON({*, 1, 2}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 20 unexpected '}'. | syntax error line 1 at position 20 unexpected '}'.",
            "SELECT TO_JSON({*, x}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'.",
            "SELECT TO_JSON({* x}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'. | syntax error "
                + "line 1 at position 21 unexpected '}'.",
            "SELECT TO_JSON({* x y}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'.",
            "SELECT TO_JSON({* 1}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'x'.",
            "SELECT TO_JSON({* EXCLUDE (id) x}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'x'.",
            "SELECT TO_JSON({* EXCLUDE (id x)}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','. | syntax error "
                + "line 1 at position 31 unexpected '}'. | syntax error line 1 at position 31 unexpected '}'.",
            "SELECT TO_JSON({* EXCLUDE id, b}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ','. | syntax error "
                + "line 1 at position 33 unexpected '}'.",
            "SELECT TO_JSON({* EXCLUDE (id), 1}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'EXCLUDE'. | syntax "
                + "error line 1 at position 39 unexpected '('.",
            "SELECT TO_JSON({* EXCLUDE (id) EXCLUDE (b)}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'ILIKE'. | syntax "
                + "error line 1 at position 39 unexpected 'x'. | syntax error line 1 at position 40 unexpected '}'.",
            "SELECT TO_JSON({* EXCLUDE b ILIKE 'i%' x}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'x'.",
            "SELECT TO_JSON({* ILIKE 'i%' x}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'EXCLUDE'. | syntax "
                + "error line 1 at position 37 unexpected '('.",
            "SELECT TO_JSON({* ILIKE 'i%' EXCLUDE (b)}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','. | syntax error "
                + "line 1 at position 31 unexpected '}'.",
            "SELECT TO_JSON({* ILIKE 'i%', 1}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '{'. | syntax error "
                + "line 1 at position 21 unexpected '}'. | syntax error line 1 at position 25 unexpected '}'.",
            "SELECT TO_JSON({*} {*} {*}) FROM FZ");
    }

    @Test
    public void wordsBracesAndPlacesAroundThem() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'x'. | syntax error "
                + "line 1 at position 21 unexpected 'y'.",
            "SELECT TO_JSON({*} x y) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '1'.",
            "SELECT TO_JSON({*} 1) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'EXCLUDE'. | syntax "
                + "error line 1 at position 27 unexpected '('.",
            "SELECT TO_JSON({*} EXCLUDE (id)) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 26 unexpected '('.",
            "SELECT TO_JSON({*} RENAME (id AS k)) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'ALL'. | syntax error "
                + "line 1 at position 21 unexpected '}'.",
            "SELECT TO_JSON({ALL *}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'DISTINCT'. | syntax "
                + "error line 1 at position 28 unexpected '}'.",
            "SELECT TO_JSON({DISTINCT * x}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'DISTINCT'. | syntax "
                + "error line 1 at position 26 unexpected ','. | syntax error line 1 at position 29 unexpected '}'.",
            "SELECT TO_JSON({DISTINCT *, 1}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 18 unexpected ','. | syntax error line 1 at position 19 unexpected '}'.",
            "SELECT TO_JSON({*,,}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 39 unexpected 'b'.",
            "SELECT TO_JSON({*, }) FROM FZ WHERE id b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ','. | syntax error "
                + "line 1 at position 24 unexpected '}'. | syntax error line 1 at position 24 unexpected '}'.",
            "SELECT TO_JSON({id, *, b}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '*'.",
            "SELECT TO_JSON({id *}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '1'. | syntax error "
                + "line 1 at position 20 unexpected '}'.",
            "SELECT TO_JSON({1, *}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x'.",
            "SELECT TO_JSON({FZ.* x}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ','. | syntax error "
                + "line 1 at position 23 unexpected '}'.",
            "SELECT TO_JSON({FZ.*, 1}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 30 unexpected '1'. | syntax error line 1 at position 37 unexpected ')'.",
            "SELECT TO_JSON({FZ.* REPLACE (1 AS id)}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected ','. | syntax error "
                + "line 1 at position 12 unexpected '}'.",
            "SELECT {*, 1} FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'.",
            "SELECT {* x} FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'y'.",
            "SELECT {*} x y FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 18 unexpected '('.",
            "SELECT {* REPLACE (1 AS id)} FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected ','. | syntax error "
                + "line 1 at position 15 unexpected '}'.",
            "SELECT 1, {*, 1} FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 20 unexpected '}'. | syntax error line 1 at position 28 unexpected 'y'.",
            "SELECT TO_JSON({*, 1}), 2 x y FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 20 unexpected '}'. | syntax error line 1 at position 40 unexpected 'b'.",
            "SELECT TO_JSON({*, 1}) FROM FZ WHERE id b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'x'. | syntax error "
                + "line 1 at position 39 unexpected 'b'.",
            "SELECT TO_JSON({*} x) FROM FZ WHERE id b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','.",
            "SELECT TO_JSON({*, 1) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '}'.",
            "SELECT TO_JSON({*}} FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 20 unexpected '}'.",
            "SELECT TO_JSON({*, 1}, 2) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ','. | syntax error "
                + "line 1 at position 23 unexpected '}'.",
            "SELECT TO_JSON(1, {*, 1}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ','. | syntax error "
                + "line 1 at position 36 unexpected '}'.",
            "SELECT 1 FROM FZ WHERE TO_JSON({*, 1}) = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 43 unexpected '1'. | syntax error line 1 at position 51 unexpected '}'.",
            "SELECT 1 FROM FZ WHERE TO_JSON({* REPLACE (1 AS id)}) = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected ','. | syntax error "
                + "line 1 at position 39 unexpected '}'.",
            "SELECT 1 FROM FZ ORDER BY TO_JSON({*, 1})");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 20 unexpected '}'. | syntax error line 1 at position 35 unexpected 'y'.",
            "SELECT TO_JSON({*, 1}); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ','. | syntax error "
                + "line 1 at position 26 unexpected '}'.",
            "SELECT UPPER(TO_JSON({*, 1})) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 33 unexpected '1'. | syntax error line 1 at position 40 unexpected ')'.",
            "SELECT UPPER(TO_JSON({* REPLACE (1 AS id)})) FROM FZ");
    }

    @Test
    public void whatStandsAfterTheComma() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 21 unexpected '}'.",
            "SELECT TO_JSON({*, 1,}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 23 unexpected '}'.",
            "SELECT TO_JSON({*, x, 1}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 23 unexpected '}'. | syntax error line 1 at position 23 unexpected '}'.",
            "SELECT TO_JSON({*, 1, x}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 21 unexpected 'y'. | syntax error line 1 at position 21 unexpected 'y'.",
            "SELECT TO_JSON({*, x y}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 29 unexpected '}'.",
            "SELECT TO_JSON({*, UPPER('a')}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 22 unexpected '}'.",
            "SELECT TO_JSON({*, 'a'}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 24 unexpected '}'. | syntax error line 1 at position 21 unexpected '.'.",
            "SELECT TO_JSON({*, FZ.id}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 25 unexpected '}'.",
            "SELECT TO_JSON({*, 1 + id}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 25 unexpected '}'.",
            "SELECT TO_JSON({*, id + 1}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 20 unexpected '}'.",
            "SELECT TO_JSON({*, *}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 22 unexpected '}'.",
            "SELECT TO_JSON({*, {*}}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 20 unexpected '}'.",
            "SELECT TO_JSON({*, 1} x) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'. | syntax error "
                + "line 1 at position 22 unexpected 'z'.",
            "SELECT TO_JSON({* x y z}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'.",
            "SELECT TO_JSON({* 1 2}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'x'. | syntax error "
                + "line 1 at position 34 unexpected '}'.",
            "SELECT TO_JSON({* EXCLUDE (id) x y}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'x'. | syntax error "
                + "line 1 at position 32 unexpected '}'.",
            "SELECT TO_JSON({* ILIKE 'i%' x y}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '}'.",
            "SELECT TO_JSON({* EXCLUDE}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ')'.",
            "SELECT TO_JSON({* EXCLUDE (id, )}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '}'.",
            "SELECT TO_JSON({* ILIKE}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'.",
            "SELECT TO_JSON({* ILIKE x}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'DISTINCT'.",
            "SELECT TO_JSON({DISTINCT}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'DISTINCT'. | syntax "
                + "error line 1 at position 27 unexpected '}'.",
            "SELECT TO_JSON({DISTINCT id}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'DISTINCT'. | syntax "
                + "error line 1 at position 38 unexpected '}'.",
            "SELECT TO_JSON({DISTINCT * EXCLUDE (b)}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'DISTINCT'. | syntax "
                + "error line 1 at position 26 unexpected '}'.",
            "SELECT TO_JSON({DISTINCT *} x) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '1'.",
            "SELECT TO_JSON({1}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '1'. | syntax error "
                + "line 1 at position 20 unexpected '}'.",
            "SELECT TO_JSON({1, 2}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '}'.",
            "SELECT TO_JSON({id}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ','. | syntax error "
                + "line 1 at position 21 unexpected '}'. | syntax error line 1 at position 21 unexpected '}'.",
            "SELECT TO_JSON({id, b}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'b'.",
            "SELECT TO_JSON({id b}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ','. | syntax error "
                + "line 1 at position 23 unexpected '}'.",
            "SELECT TO_JSON({id.*, 1}) FROM FZ");
        assertAnswer(
            "",
            "SELECT TO_JSON({}) FROM FZ");
    }

    @Test
    public void afterTheBracesAndInOtherPlaces() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'x'. | syntax error "
                + "line 1 at position 21 unexpected 'y'.",
            "SELECT TO_JSON({*} x y z) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'x'. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "SELECT TO_JSON({*} x, 1) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '1'.",
            "SELECT TO_JSON({*} 1 2) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '{'.",
            "SELECT TO_JSON({*} {) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '{'. | syntax error "
                + "line 1 at position 21 unexpected '}'. | syntax error line 1 at position 24 unexpected ')'.",
            "SELECT TO_JSON({*} {*} x) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | Invalid argument types for function "
                + "'+': (OBJECT, NUMBER(1,0))",
            "SELECT TO_JSON({*} + 1) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'.",
            "SELECT TO_JSON({*}, {*} x) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 42 unexpected 'id'. | syntax error line 1 at position 50 unexpected '}'.",
            "SELECT 1 FROM FZ WHERE TO_JSON({* RENAME (id AS k)}) = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'x'.",
            "SELECT 1 FROM FZ WHERE TO_JSON({* x y}) = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'x'.",
            "SELECT 1 FROM FZ WHERE TO_JSON({*} x y) = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'DISTINCT'. | syntax "
                + "error line 1 at position 42 unexpected '}'.",
            "SELECT 1 FROM FZ WHERE TO_JSON({DISTINCT *}) = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 46 unexpected '1'. | syntax error line 1 at position 54 unexpected '}'.",
            "SELECT 1 FROM FZ ORDER BY TO_JSON({* REPLACE (1 AS id)})");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'x'.",
            "SELECT 1 FROM FZ ORDER BY TO_JSON({* x y})");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 27 unexpected '1'. | syntax error line 1 at position 34 unexpected ')'.",
            "SELECT TO_JSON({* REPLACE (1 AS id) x}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 27 unexpected '1'. | syntax error line 1 at position 34 unexpected ')'.",
            "SELECT TO_JSON({* REPLACE (1 AS id)} x) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 27 unexpected 'id'. | syntax error line 1 at position 30 unexpected 'AS'.",
            "SELECT TO_JSON({* REPLACE (id AS id)}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 26 unexpected '1'. | syntax error line 1 at position 32 unexpected ')'.",
            "SELECT TO_JSON({* RENAME (1 AS k)}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 27 unexpected '1'.",
            "SELECT TO_JSON({* REPLACE (1)}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'REPLACE'.",
            "SELECT TO_JSON({* REPLACE ()}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'REPLACE'.",
            "SELECT TO_JSON({* REPLACE}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'RENAME'.",
            "SELECT TO_JSON({* RENAME}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 41 unexpected '}'.",
            "SELECT TO_JSON({* REPLACE 1 AS id, 2 AS b}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'REPLACE'.",
            "SELECT TO_JSON({* REPLACE 1}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 27 unexpected '}'.",
            "SELECT TO_JSON({* RENAME id}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 28 unexpected 'AS'.",
            "SELECT TO_JSON({* RENAME id AS k, b AS c}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ','. | syntax error "
                + "line 1 at position 28 unexpected '}'.",
            "SELECT ARRAY_CONSTRUCT({*, 1}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ','. | syntax error "
                + "line 1 at position 13 unexpected '}'.",
            "SELECT [{*, 1}] FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ','. | syntax error "
                + "line 1 at position 13 unexpected '}'.",
            "SELECT ({*, 1}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected ','. | syntax error "
                + "line 1 at position 12 unexpected '}'.",
            "SELECT {*, 1} x FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected ','. | syntax error "
                + "line 1 at position 12 unexpected '}'.",
            "SELECT {*, x} FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 12 unexpected 'y'.",
            "SELECT {* x y} FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'DISTINCT'. | syntax "
                + "error line 1 at position 18 unexpected '}'.",
            "SELECT {DISTINCT *} FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '{'.",
            "SELECT {*} {*} FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '('.",
            "SELECT {*} REPLACE (1 AS id) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 17 unexpected '('.",
            "SELECT {* RENAME (id AS k)} FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected ','. | syntax error "
                + "line 1 at position 12 unexpected '}'.",
            "SELECT {*, 1}, 2 x y FROM FZ");
    }
}
