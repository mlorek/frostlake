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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A VECTOR is no ARRAY. Where a function reads its argument as a semi-structured value (ARRAY_SIZE, GET,
 * GET_PATH, AS_ARRAY, a subscript or a path) a vector answers NULL and IS_ARRAY answers FALSE; the array
 * functions typed for an ARRAY refuse it by their argument types at compile time, FLATTEN refuses it as its
 * input, and ARRAY_CAT, ARRAY_COMPACT and ARRAY_TO_STRING refuse any value that is not an array when the row is
 * read. TO_ARRAY and TO_VARIANT still convert a vector into the array of its elements. Every cell is
 * live-verified.
 */
public class VectorArrayArgumentTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE vt (v VECTOR(INT, 3))");
        engine.execute("INSERT INTO vt SELECT [1,2,3]::VECTOR(INT,3)");
    }

    /** Every row's first cell, lower-cased and joined by a bar. */
    private String answer(final String sql) {
        final StringBuilder answer = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (answer.length() > 0) {
                answer.append(" | ");
            }
            answer.append(String.valueOf(row.getValue(0)).toLowerCase());
        }
        return answer.toString();
    }

    @Test
    public void anArrayFunctionRefusesAVector() {
        for (final String[] cell : new String[][] {
            {"SELECT ARRAY_CONTAINS(1::VARIANT, [1,2,3]::VECTOR(FLOAT,3))", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_CONTAINS': (VARIANT, VECTOR(FLOAT, 3))"},
            {"SELECT ARRAY_CONTAINS(1::VARIANT, v) FROM vt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_CONTAINS': (VARIANT, VECTOR(INT, 3))"},
            {"SELECT ARRAY_POSITION(1::VARIANT, [1,2,3]::VECTOR(FLOAT,3))", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_POSITION': (VARIANT, VECTOR(FLOAT, 3))"},
            {"SELECT ARRAY_POSITION(1::VARIANT, v) FROM vt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_POSITION': (VARIANT, VECTOR(INT, 3))"},
            {"SELECT ARRAY_SLICE([1,2,3]::VECTOR(FLOAT,3), 0, 1)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_SLICE': (VECTOR(FLOAT, 3), NUMBER(1,0), NUMBER(1,0))"},
            {"SELECT ARRAY_SLICE(v, 0, 1) FROM vt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_SLICE': (VECTOR(INT, 3), NUMBER(1,0), NUMBER(1,0))"},
            {"SELECT ARRAY_TO_STRING([1,2,3]::VECTOR(FLOAT,3), ',')", "Left argument of string is not an array"},
            {"SELECT ARRAY_TO_STRING(v, ',') FROM vt", "Left argument of string is not an array"},
            {"SELECT ARRAY_CAT([1,2,3]::VECTOR(FLOAT,3), [1])", "Left argument of ARRAY_CAT is not an array"},
            {"SELECT ARRAY_CAT(v, [1]) FROM vt", "Left argument of ARRAY_CAT is not an array"},
            {"SELECT ARRAY_APPEND([1,2,3]::VECTOR(FLOAT,3), 1)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_APPEND': (VECTOR(FLOAT, 3), NUMBER(1,0))"},
            {"SELECT ARRAY_APPEND(v, 1) FROM vt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_APPEND': (VECTOR(INT, 3), NUMBER(1,0))"},
            {"SELECT ARRAY_COMPACT([1,2,3]::VECTOR(FLOAT,3))", "First argument of ARRAY_COMPACT is not an array"},
            {"SELECT ARRAY_COMPACT(v) FROM vt", "First argument of ARRAY_COMPACT is not an array"},
            {"SELECT ARRAY_DISTINCT([1,2,3]::VECTOR(FLOAT,3))", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_DISTINCT': (VECTOR(FLOAT, 3))"},
            {"SELECT ARRAY_DISTINCT(v) FROM vt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_DISTINCT': (VECTOR(INT, 3))"},
            {"SELECT ARRAY_MAX([1,2,3]::VECTOR(FLOAT,3))", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_MAX': (VECTOR(FLOAT, 3))"},
            {"SELECT ARRAY_MAX(v) FROM vt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_MAX': (VECTOR(INT, 3))"},
            {"SELECT ARRAY_MIN([1,2,3]::VECTOR(FLOAT,3))", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_MIN': (VECTOR(FLOAT, 3))"},
            {"SELECT ARRAY_MIN(v) FROM vt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_MIN': (VECTOR(INT, 3))"},
            {"SELECT ARRAY_SORT([1,2,3]::VECTOR(FLOAT,3))", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_SORT': (VECTOR(FLOAT, 3))"},
            {"SELECT ARRAY_SORT(v) FROM vt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_SORT': (VECTOR(INT, 3))"},
            {"SELECT ARRAY_REVERSE([1,2,3]::VECTOR(FLOAT,3))", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_REVERSE': (VECTOR(FLOAT, 3))"},
            {"SELECT ARRAY_REVERSE(v) FROM vt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_REVERSE': (VECTOR(INT, 3))"},
            {"SELECT ARRAY_INSERT([1,2,3]::VECTOR(FLOAT,3), 0, 1)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_INSERT': (VECTOR(FLOAT, 3), NUMBER(1,0), NUMBER(1,0))"},
            {"SELECT ARRAY_INSERT(v, 0, 1) FROM vt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_INSERT': (VECTOR(INT, 3), NUMBER(1,0), NUMBER(1,0))"},
            {"SELECT ARRAY_PREPEND([1,2,3]::VECTOR(FLOAT,3), 1)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_PREPEND': (VECTOR(FLOAT, 3), NUMBER(1,0))"},
            {"SELECT ARRAY_PREPEND(v, 1) FROM vt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_PREPEND': (VECTOR(INT, 3), NUMBER(1,0))"},
            {"SELECT ARRAY_REMOVE([1,2,3]::VECTOR(FLOAT,3), 1)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_REMOVE': (VECTOR(FLOAT, 3), NUMBER(1,0))"},
            {"SELECT ARRAY_REMOVE(v, 1) FROM vt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_REMOVE': (VECTOR(INT, 3), NUMBER(1,0))"},
            {"SELECT ARRAYS_OVERLAP([1,2,3]::VECTOR(FLOAT,3), [1])", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAYS_OVERLAP': (VECTOR(FLOAT, 3), ARRAY)"},
            {"SELECT ARRAYS_OVERLAP(v, [1]) FROM vt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAYS_OVERLAP': (VECTOR(INT, 3), ARRAY)"},
            {"SELECT ARRAY_INTERSECTION([1,2,3]::VECTOR(FLOAT,3), [1])", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_INTERSECTION': (VECTOR(FLOAT, 3), ARRAY)"},
            {"SELECT ARRAY_INTERSECTION(v, [1]) FROM vt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_INTERSECTION': (VECTOR(INT, 3), ARRAY)"},
            {"SELECT ARRAY_EXCEPT([1,2,3]::VECTOR(FLOAT,3), [1])", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_EXCEPT': (VECTOR(FLOAT, 3), ARRAY)"},
            {"SELECT ARRAY_EXCEPT(v, [1]) FROM vt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_EXCEPT': (VECTOR(INT, 3), ARRAY)"},
            {"SELECT ARRAY_FLATTEN([1,2,3]::VECTOR(FLOAT,3))", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_FLATTEN': (VECTOR(FLOAT, 3))"},
            {"SELECT ARRAY_FLATTEN(v) FROM vt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_FLATTEN': (VECTOR(INT, 3))"},
            {"SELECT COUNT(*) FROM TABLE(FLATTEN([1,2,3]::VECTOR(FLOAT,3)))", "SQL compilation error:\ninvalid type [VECTOR(FLOAT, 3)] for parameter '1'"},
            {"SELECT ARRAY_CAT(1, [1])", "Left argument of ARRAY_CAT is not an array"},
            {"SELECT ARRAY_CAT([1], 1)", "Right argument of ARRAY_CAT is not an array"},
            {"SELECT ARRAY_COMPACT(1)", "First argument of ARRAY_COMPACT is not an array"},
            {"SELECT ARRAY_TO_STRING(1, ',')", "Left argument of string is not an array"},
            {"SELECT ARRAY_CAT(PARSE_JSON('1'), [1])", "Left argument of ARRAY_CAT is not an array"},
            {"SELECT ARRAY_COMPACT(PARSE_JSON('1'))", "First argument of ARRAY_COMPACT is not an array"},
            {"SELECT ARRAY_TO_STRING(PARSE_JSON('1'), ',')", "Left argument of string is not an array"},
            {"SELECT ARRAY_CAT([1], [1,2,3]::VECTOR(FLOAT,3))", "Right argument of ARRAY_CAT is not an array"},
            {"SELECT ARRAY_REMOVE_AT([1,2,3]::VECTOR(FLOAT,3), 0)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAY_REMOVE_AT': (VECTOR(FLOAT, 3), NUMBER(1,0))"},
            {"SELECT ARRAYS_ZIP([1,2,3]::VECTOR(FLOAT,3), [1])", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAYS_ZIP': (VECTOR(FLOAT, 3), ARRAY)"},
            {"SELECT ARRAYS_TO_OBJECT(['a'], [1,2,3]::VECTOR(FLOAT,3))", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ARRAYS_TO_OBJECT': (ARRAY, VECTOR(FLOAT, 3))"},
            {"SELECT ARRAY_TO_STRING([1,2,3]::VECTOR(INT,3), ',')", "Left argument of string is not an array"},
            {"SELECT ARRAY_CAT(OBJECT_CONSTRUCT(), [1])", "Left argument of ARRAY_CAT is not an array"},
        }) {
            final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery(cell[0]);
                }
            }, cell[0]);
            assertTrue(String.valueOf(refused.getMessage()).contains(cell[1]),
                cell[0] + " should be refused with [" + cell[1] + "] but read: " + refused.getMessage());
        }
    }

    @Test
    public void aVectorReadAsAnArrayAnswersNull() {
        for (final String[] cell : new String[][] {
            {"SELECT ARRAY_SIZE([1,2,3]::VECTOR(FLOAT,3))", "null"},
            {"SELECT ARRAY_SIZE(v) FROM vt", "null"},
            {"SELECT GET([1,2,3]::VECTOR(FLOAT,3), 0)", "null"},
            {"SELECT GET(v, 0) FROM vt", "null"},
            {"SELECT IS_ARRAY([1,2,3]::VECTOR(FLOAT,3))", "false"},
            {"SELECT IS_ARRAY(v) FROM vt", "false"},
            {"SELECT TYPEOF([1,2,3]::VECTOR(FLOAT,3))", "vector"},
            {"SELECT TYPEOF(v) FROM vt", "vector"},
            {"SELECT [1,2,3]::VECTOR(FLOAT,3)[0]", "null"},
            {"SELECT v[0] FROM vt", "null"},
            {"SELECT [1,2,3]::VECTOR(FLOAT,3):x", "null"},
            {"SELECT v:x FROM vt", "null"},
            {"SELECT TO_ARRAY(v) FROM vt", "[1,2,3]"},
            {"SELECT ARRAY_SIZE([1,2,3]::VECTOR(FLOAT,3)[0])", "null"},
            {"SELECT ARRAY_SIZE(v[0]) FROM vt", "null"},
            {"SELECT ARRAY_CONSTRUCT(v) FROM vt", "[[1,2,3]]"},
            {"SELECT TO_VARIANT(v) FROM vt", "[1,2,3]"},
            {"SELECT ARRAY_CONTAINS([1,2,3]::VECTOR(FLOAT,3), [1])", "false"},
            {"SELECT ARRAY_CONTAINS(v, [1]) FROM vt", "false"},
            {"SELECT ARRAY_SIZE(PARSE_JSON('[1,2]')::VARIANT)", "2"},
            {"SELECT ARRAY_SIZE(OBJECT_CONSTRUCT())", "null"},
            {"SELECT ARRAY_SIZE(1)", "null"},
            {"SELECT SYSTEM$TYPEOF(ARRAY_SIZE([1,2,3]::VECTOR(FLOAT,3)))", "number(9,0)[sb4]"},
            {"SELECT ARRAY_SIZE(NULL::VECTOR(FLOAT,3))", "null"},
            {"SELECT AS_ARRAY([1,2,3]::VECTOR(FLOAT,3))", "null"},
            {"SELECT GET([1,2,3]::VECTOR(INT,3), 5)", "null"},
            {"SELECT ARRAY_SIZE(TO_ARRAY([1,2,3]::VECTOR(INT,3)))", "3"},
            {"SELECT GET_PATH([1,2,3]::VECTOR(INT,3), '[0]')", "null"},
            {"SELECT ARRAY_CONTAINS(1::VARIANT, ARRAY_CONSTRUCT_COMPACT([1,2,3]::VECTOR(INT,3)))", "false"},
            {"SELECT IS_ARRAY(TO_VARIANT([1,2,3]::VECTOR(INT,3)))", "false"},
            {"SELECT ARRAY_SIZE(TO_VARIANT([1,2,3]::VECTOR(INT,3)))", "null"},
            {"SELECT v[0] FROM (SELECT TO_VARIANT([1,2,3]::VECTOR(INT,3)) v)", "null"},
        }) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }
}
