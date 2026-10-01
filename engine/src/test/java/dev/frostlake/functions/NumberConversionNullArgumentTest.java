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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A numeric conversion refuses an untyped NULL beside its format while the statement compiles, over no rows
 * too: TO_NUMBER, TO_DECIMAL, TO_NUMERIC and their TRY_ twins answer {@code argument needs to be a string:
 * 'null'}, a typed NULL echoed as the plan's typed null. A precision or scale is a constant integer, so a
 * NULL or a decimal there is refused naming its position and the base function, and TO_DOUBLE and
 * TO_DECFLOAT, whose format exists over a text only, refuse an untyped NULL beside one as too many
 * arguments. Every cell is live-verified.
 */
public class NumberConversionNullArgumentTest extends BaseDatabaseTest {

    /** The first row's first cell, "no row", or the refusal on one line. */
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

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    @Test
    public void anUntypedNullBesideAFormatIsNoString() {
        final String noString = "SQL compilation error:|argument needs to be a string: 'null'";
        final String[] refused = {
            "SELECT TO_NUMBER(NULL, '9e9')", "SELECT TO_NUMBER(NULL, '999')", "SELECT TO_NUMBER(NULL, '999', 10, 2)",
            "SELECT TO_DECIMAL(NULL, '999')", "SELECT TO_NUMERIC(NULL, '999')", "SELECT TO_NUMBER(NULL, 'nonsense')",
            "SELECT TRY_TO_NUMBER(NULL, '9e9')", "SELECT TRY_TO_NUMBER(NULL, '999')", "SELECT TRY_TO_DECIMAL(NULL, '999')",
            "SELECT TRY_TO_NUMERIC(NULL, '999')", "SELECT TO_NUMBER(NULL, '999', NULL)",
            "SELECT TO_NUMBER(NULL, '999') FROM (SELECT 1 WHERE FALSE)",
        };
        for (final String sql : refused) {
            assertEquals(noString, answer(sql), sql);
        }
        assertCells(new String[][] {
            {"SELECT TO_NUMBER(NULL::NUMBER, '999')", "SQL compilation error:|argument needs to be a string: 'SYSTEM$NULL_TO_FIXED(null)'"},
            {"SELECT TO_NUMBER(1, 'nonsense')", "SQL compilation error:|argument needs to be a string: '1'"},
            {"SELECT TRY_TO_NUMBER(NULL)", "SQL compilation error:|Function TRY_CAST cannot be used with arguments of types NULL and NUMBER(38,0)"},
            {"SELECT TO_NUMBER(NULL)", "null"},
            {"SELECT TO_NUMBER(NULL::VARCHAR, '999')", "null"},
            {"SELECT TO_NUMBER(NULL, 5)", "null"},
            {"SELECT TO_NUMBER(NULL, 10, 2)", "null"},
        });
    }

    @Test
    public void aPrecisionOrScaleIsAConstantInteger() {
        assertCells(new String[][] {
            {"SELECT TO_NUMBER(NULL, NULL)", "SQL compilation error:|argument 2 to function TO_NUMBER needs to be an integer, found: 'null'"},
            {"SELECT TO_NUMBER('12', NULL)", "SQL compilation error:|argument 2 to function TO_NUMBER needs to be an integer, found: 'null'"},
            {"SELECT TO_NUMBER(NULL, 10.5)", "SQL compilation error:|argument 2 to function TO_NUMBER needs to be an integer, found: '10.5'"},
            {"SELECT TO_NUMBER('12', 5, NULL)", "SQL compilation error:|argument 3 to function TO_NUMBER needs to be an integer, found: 'null'"},
            {"SELECT TO_NUMBER('12', '99', 5, NULL)",
                "SQL compilation error:|argument 4 to function TO_NUMBER needs to be an integer, found: 'null'"},
            {"SELECT TO_DECIMAL(NULL, NULL)", "SQL compilation error:|argument 2 to function TO_DECIMAL needs to be an integer, found: 'null'"},
            {"SELECT TRY_TO_NUMBER(NULL, NULL)", "SQL compilation error:|argument 2 to function TO_NUMBER needs to be an integer, found: 'null'"},
            {"SELECT TRY_TO_NUMBER('12', NULL)", "SQL compilation error:|argument 2 to function TO_NUMBER needs to be an integer, found: 'null'"},
        });
    }

    @Test
    public void anUntypedNullBesideADoubleFormatIsTooManyArguments() {
        assertCells(new String[][] {
            {"SELECT TO_DOUBLE(NULL, '999')",
                "SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DOUBLE(NULL, '999')] expected 1, got 2"},
            {"SELECT TO_DOUBLE(NULL, NULL)",
                "SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DOUBLE(NULL, NULL)] expected 1, got 2"},
            {"SELECT TO_DOUBLE(NULL, '999') FROM (SELECT 1 WHERE FALSE)",
                "SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DOUBLE(NULL, '999')] expected 1, got 2"},
            {"SELECT TO_DECFLOAT(NULL, '999')",
                "SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DECFLOAT(NULL, '999')] expected 1, got 2"},
            {"SELECT TRY_TO_DOUBLE(NULL, '999')", "SQL compilation error:|Function TRY_CAST cannot be used with arguments of types NULL and FLOAT"},
            {"SELECT TRY_TO_DECFLOAT(NULL, '999')",
                "SQL compilation error:|Function TRY_CAST cannot be used with arguments of types NULL and DECFLOAT(38)"},
            {"SELECT TO_DOUBLE(NULL::VARCHAR, '999')", "null"},
            {"SELECT TO_DOUBLE(NULL)", "null"},
        });
    }
}
