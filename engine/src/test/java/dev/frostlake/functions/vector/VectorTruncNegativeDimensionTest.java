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

package dev.frostlake.functions.vector;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VECTOR_TRUNC reads its dimension in 32 bits, and every negative dimension compiles. Each row read under it
 * sizes the result as four bytes an element, in 32 bits: a negative size fails with the account's internal
 * error, a size past 16777216 bytes is too long to return, and any other size reads the empty vector. A
 * dimension below 32 bits wraps, so -2147483649 is 2,147,483,647 and refused as too large at its sign. Every
 * cell is live-verified.
 */
public class VectorTruncNegativeDimensionTest extends BaseDatabaseTest {

    /** How each row fails; the account's incident number follows it. */
    private static final String ROW_FAILURE =
        "SQL execution internal error:\nProcessing aborted due to error 300010:2086363262";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE vec_store (v VECTOR(FLOAT,3))");
        engine.execute("CREATE TABLE vec_rows (v VECTOR(FLOAT,3))");
        engine.execute("INSERT INTO vec_rows SELECT [1,2,3]::VECTOR(FLOAT,3)");
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return String.valueOf(refused.getMessage());
    }

    @Test
    public void everyNegativeDimensionFailsEachRow() {
        for (final String dimension : new String[] {"-2", "-3", "-100", "-4096", "-1073741825", "-1610612736"}) {
            final String failed = refusal("SELECT VECTOR_TRUNC(v, " + dimension + ") FROM vec_rows");
            assertTrue(failed.startsWith(ROW_FAILURE), dimension + " -> " + failed);
        }
        final String withoutFrom = refusal("SELECT VECTOR_TRUNC([1,2,3]::VECTOR(FLOAT,3), -2)");
        assertTrue(withoutFrom.startsWith(ROW_FAILURE), withoutFrom);
        assertEquals(0, engine.executeQuery("SELECT VECTOR_TRUNC(v, -2) IS NULL FROM vec_store").getRows().size());
    }

    @Test
    public void aWrappedSizeReadsTheEmptyVector() {
        for (final String dimension : new String[] {"-2147483648", "-2147483647", "-2147483644", "-1073741824",
            "-1073741823"}) {
            assertEquals("[]", String.valueOf(engine.executeQuery("SELECT VECTOR_TRUNC(v, " + dimension
                + ") FROM vec_rows").getRows().get(0).getValue(0)), dimension);
        }
    }

    @Test
    public void aWrappedSizePastTheValueLimitIsTooLong() {
        assertEquals("Cannot return value of length 1073741824 as it exceeds the maximum length of 16777216",
            refusal("SELECT VECTOR_TRUNC(v, -1879048192) FROM vec_rows"));
        assertEquals("Cannot return value of length 1073741824 as it exceeds the maximum length of 16777216",
            refusal("SELECT VECTOR_TRUNC(v, -805306368) FROM vec_rows"));
    }

    @Test
    public void aDimensionBelowThirtyTwoBitsWraps() {
        assertEquals("SQL compilation error: error line 1 at position 23\nRequested truncation dimension 2,147,483,647 for "
            + "VECTOR_TRUNC should be less than or equal to the dimension of the provided vector (3).",
            refusal("SELECT VECTOR_TRUNC(v, -2147483649) FROM vec_rows"));
    }

    @Test
    public void aBadCastInsideStillFailsItsRow() {
        assertEquals("Array-like value being cast to a float vector has elements that are not real numbers",
            refusal("SELECT VECTOR_TRUNC([1,2,NULL]::VECTOR(FLOAT,3), -2)"));
    }
}
