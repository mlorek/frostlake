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

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A UUID beside the numeric functions, the arithmetic operators, the string functions and GREATEST / LEAST, as the
 * account types it (live-verified): it reads as no number, so every numeric function and operator refuses it while
 * the statement compiles, at the call or the operator; a string function converts it to a text nothing bounds; and
 * GREATEST and LEAST refuse a number beside it before any row, wherever the call stands.
 */
public class UuidOperandTypingTest extends MeasuredCellsTestSupport {

    private static String invalid(final int position, final String function, final String types) {
        return "REFUSED SQL compilation error: error line 1 at position " + position
            + " | Invalid argument types for function '" + function + "': (" + types + ")";
    }

    private static String notConvertible(final String parameter) {
        return "REFUSED SQL compilation error: | Can not convert parameter '" + parameter
            + "' of type [NUMBER(1,0)] into expected type [UUID]";
    }

    private void table() {
        answer("CREATE OR REPLACE TABLE ut (u UUID, s VARCHAR)");
        answer("INSERT INTO ut VALUES ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'x')");
    }

    @Test
    public void theNumericFunctionsAndOperatorsRefuseAUuid() {
        table();
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(ZEROIFNULL(u)) FROM ut", invalid(21, "ZEROIFNULL", "UUID")},
            {"SELECT ZEROIFNULL(u) FROM ut", invalid(7, "ZEROIFNULL", "UUID")},
            {"SELECT ABS(u) FROM ut", invalid(7, "ABS", "UUID")},
            {"SELECT MOD(u, 2) FROM ut", invalid(7, "MOD", "UUID, NUMBER(1,0)")},
            {"SELECT TRUNC(u) FROM ut", invalid(7, "TRUNC", "UUID")},
            {"SELECT u + 1 FROM ut", invalid(9, "+", "UUID, NUMBER(1,0)")},
            {"SELECT -u FROM ut", invalid(7, "NEGATE", "UUID")},
            {"SELECT NULLIFZERO(u) FROM ut", notConvertible("0")},
            {"SELECT SYSTEM$TYPEOF(ZEROIFNULL(s)) FROM ut", "FLOAT[DOUBLE]"},
        });
    }

    @Test
    public void aStringFunctionReadsAUuidAsATextNothingBounds() {
        table();
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(u || 'x') FROM ut", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(u || u), SYSTEM$TYPEOF(s || u) FROM ut",
                "VARCHAR(134217728)[LOB]|VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CONCAT(u, 'x')) FROM ut", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CONCAT_WS('-', u, 'x')) FROM ut", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(u::VARCHAR), SYSTEM$TYPEOF(UPPER(u)) FROM ut",
                "VARCHAR[LOB]|VARCHAR(134217728)[LOB]"},
        });
    }

    @Test
    public void greatestAndLeastRefuseANumberBesideAUuidBeforeAnyRow() {
        table();
        assertCells(new String[][] {
            {"SELECT GREATEST(u, 1) IS NULL FROM ut", notConvertible("1")},
            {"SELECT SYSTEM$TYPEOF(GREATEST(u, 1)) FROM ut", notConvertible("1")},
            {"SELECT LEAST(u, 1) FROM ut", notConvertible("1")},
            {"SELECT GREATEST(u, u) = u FROM ut", "true"},
        });
    }
}
