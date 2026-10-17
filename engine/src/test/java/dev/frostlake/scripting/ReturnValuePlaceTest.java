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

package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A RETURN value's fault is placed where the value is written, at its first parenthesis when it has one: {@code
 * RETURN (ABS(1, 2))} is the uncaught EXPRESSION_ERROR at the parenthesis, its own refusal placed inside the value
 * (live-verified).
 */
public class ReturnValuePlaceTest extends MeasuredCellsTestSupport {

    @Test
    public void aReturnValuesFaultIsPlacedWhereTheValueIsWritten() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1)");
        engine.execute("CREATE OR REPLACE FUNCTION n1(a INT, b INT) RETURNS INT AS $$ a + b $$");
        assertCells(new String[][] {
            {"BEGIN RETURN (ABS(1, 2)); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 1 | too many arguments for function [ABS(1, 2)] expected 1, got 2"},
            {"BEGIN RETURN 12 + DATEADD('nosuchpart', 1, CURRENT_DATE()); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: ['nosuchpart'] is not a valid date/time component for function DATEADD."},
            {"BEGIN FOR i IN 1 TO 3 DO RETURN ABS(1, 2); END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 32 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [ABS(1, 2)] expected 1, got 2"},
            {"BEGIN RETURN ((ABS(1, 2))); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 2 | too many arguments for function [ABS(1, 2)] expected 1, got 2"},
            {"BEGIN RETURN (1 + ABS(1, 2)); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 5 | too many arguments for function [ABS(1, 2)] expected 1, got 2"},
        });
    }
}
