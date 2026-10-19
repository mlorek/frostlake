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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

/**
 * The array functions on the row: a VARIANT that holds no array is read as a one-element array where the function
 * takes it, a whole-number or flag position converts a text and a VARIANT as their casts do, and GET's index is a
 * number or a text (live-verified).
 */
public class ArrayVariantArgumentsTest extends BaseDatabaseTest {

    private void assertAnswers(final String[][] cells) {
        for (final String[] cell : cells) {
            String answer;
            try {
                final Row row = engine.executeQuery(cell[0]).getRows().get(0);
                answer = String.valueOf(row.getValue(0));
            } catch (final RuntimeException refused) {
                answer = String.valueOf(refused.getMessage()).replace('\n', '|');
            }
            assertEquals(cell[1], answer, cell[0]);
        }
    }

    @Test
    public void aVariantHoldingNoArrayIsOneElement() {
        assertAnswers(new String[][] {
            {"SELECT ARRAY_SLICE(TO_VARIANT('abc'), 0, 1)", "[\"abc\"]"},
            {"SELECT ARRAY_CONTAINS(1::VARIANT, TO_VARIANT(1))", "true"},
            {"SELECT ARRAY_POSITION(1::VARIANT, TO_VARIANT(1))", "0"},
            {"SELECT ARRAY_APPEND(TO_VARIANT('abc'), 1)", "[\"abc\",1]"},
            {"SELECT ARRAY_PREPEND(TO_VARIANT('abc'), 1)", "[1,\"abc\"]"},
            {"SELECT ARRAY_DISTINCT(TO_VARIANT(1))", "[1]"},
            {"SELECT ARRAY_INSERT(TO_VARIANT('a'), 0, 1)", "[1,\"a\"]"},
            {"SELECT ARRAY_REMOVE(TO_VARIANT(1), 1)", "[]"},
            {"SELECT ARRAY_REVERSE(TO_VARIANT(1))", "[1]"},
            {"SELECT ARRAY_SORT(TO_VARIANT(1))", "[1]"},
            {"SELECT ARRAYS_OVERLAP(TO_VARIANT(1), [1])", "true"},
            {"SELECT ARRAY_INTERSECTION(TO_VARIANT(1), [1])", "[1]"},
            {"SELECT ARRAY_EXCEPT(TO_VARIANT(1), [2])", "[1]"},
            {"SELECT ARRAY_MAX(TO_VARIANT(3))", "3"},
            {"SELECT ARRAY_MIN(TO_VARIANT(3))", "3"},
            {"SELECT ARRAY_SLICE(PARSE_JSON('{\"a\":1}'), 0, 1)", "[{\"a\":1}]"},
            {"SELECT ARRAY_SIZE(TO_VARIANT('abc'))", "null"},
            {"SELECT ARRAY_CAT(TO_VARIANT(1), [2])", "Left argument of ARRAY_CAT is not an array"},
            {"SELECT ARRAY_COMPACT(TO_VARIANT(1))", "First argument of ARRAY_COMPACT is not an array"},
        });
    }

    @Test
    public void aPositionConvertsTextAndVariants() {
        assertAnswers(new String[][] {
            {"SELECT ARRAY_SLICE([1], 'abc', 1)", "Numeric value 'abc' is not recognized"},
            {"SELECT ARRAY_SLICE([1], TO_VARIANT('abc'), 1)", "Failed to cast variant value \"abc\" to FIXED"},
            {"SELECT ARRAY_SLICE([1, 2, 3], TO_VARIANT(1), 2)", "[2]"},
            {"SELECT ARRAY_INSERT([1], 'abc', 1)", "Numeric value 'abc' is not recognized"},
            {"SELECT ARRAY_INSERT([1], TO_VARIANT(1), 1)", "[1,1]"},
            {"SELECT ARRAY_INSERT([1], '0', 2)", "[2,1]"},
            {"SELECT ARRAY_REMOVE_AT([1], TO_VARIANT(1))", "[1]"},
            {"SELECT ARRAY_REMOVE_AT([1, 2], '0')", "[2]"},
            {"SELECT ARRAY_GENERATE_RANGE(TO_VARIANT(1), 3)", "[1,2]"},
            {"SELECT ARRAY_GENERATE_RANGE('1', 3)", "[1,2]"},
            {"SELECT ARRAY_SORT([1], 'abc')", "Boolean value 'abc' is not recognized"},
            {"SELECT ARRAY_SORT([1], TO_VARIANT(1))", "Failed to cast variant value 1 to BOOLEAN"},
            {"SELECT ARRAY_SORT([2, 1], TO_VARIANT(FALSE))", "[2,1]"},
        });
    }

    @Test
    public void getsIndexIsANumberOrAText() {
        final String refused = "SQL compilation error: error line 1 at position 7|"
            + "Invalid argument types for function 'GET': (ARRAY, ";
        assertAnswers(new String[][] {
            {"SELECT GET([1], X'00')", refused + "BINARY(1))"},
            {"SELECT GET([1], OBJECT_CONSTRUCT('a', 1))", refused + "OBJECT)"},
            {"SELECT GET([1], [1])", refused + "ARRAY)"},
            {"SELECT GET([1], '0')", "null"},
            {"SELECT GET([1], TO_VARIANT(0))", "null"},
            {"SELECT GET([1], 0)", "1"},
        });
    }
}
