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
 * A unit's place in a block's own expression (live-verified): a variable named like a unit is echoed as the plan
 * converts its text — {@code TO_NUMBER('5')}, {@code TO_DOUBLE('5.5')}, {@code TO_BOOLEAN('true')} — and a
 * bare word in LAST_DAY's second place that names no part is the name nothing declares.
 */
public class BlockUnitWordEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void aUnitsPlaceInABlockExpression() {
        assertCells(new String[][] {
            {"BEGIN RETURN LAST_DAY(CURRENT_DATE(), nosuch_var_x9); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 0 | invalid identifier 'NOSUCH_VAR_X9'"},
            {"BEGIN RETURN LAST_DAY('2024-01-15'::DATE, nosuch_var_x9); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 0 | invalid identifier 'NOSUCH_VAR_X9'"},
            {"BEGIN RETURN LAST_DAY(CURRENT_DATE, nosuch_var_x9); END;",
                "REFUSED SQL compilation error: error line 1 at position 22 | invalid identifier 'CURRENT_DATE'"},
            {"BEGIN LET r BOOLEAN := LAST_DAY(CURRENT_DATE(), nosuch_var_x9) IS NULL; RETURN r; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 23 : SQL compilation "
                + "error: error line 1 at position 0 | invalid identifier 'NOSUCH_VAR_X9'"},
            {"BEGIN LET day := 5; RETURN DATEADD(day, 1, '2024-01-01'::DATE); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 27 : SQL compilation "
                + "error: Date/time component [TO_NUMBER('5') ]for function DATEADD needs to be an identifier or a "
                + "string literal."},
            {"BEGIN LET day := 'x'; RETURN DATEADD(day, 1, '2024-01-01'::DATE); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL compilation "
                + "error: ['x'] is not a valid date/time component for function DATEADD."},
            {"BEGIN LET day NUMBER := 5; RETURN DATEADD(day, 1, '2024-01-01'::DATE); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 34 : SQL compilation "
                + "error: Date/time component [TO_NUMBER('5') ]for function DATEADD needs to be an identifier or a "
                + "string literal."},
            {"BEGIN LET month := 5; RETURN DATEDIFF(month, '2024-01-01'::DATE, '2024-03-01'::DATE); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL compilation "
                + "error: Date/time component [TO_NUMBER('5') ]for function DATEDIFF needs to be an identifier or a "
                + "string literal."},
            {"BEGIN LET day FLOAT := 5.5; RETURN DATEADD(day, 1, '2024-01-01'::DATE); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 35 : SQL compilation "
                + "error: Date/time component [TO_DOUBLE('5.5') ]for function DATEADD needs to be an identifier or a "
                + "string literal."},
            {"BEGIN LET day := 5.5; RETURN DATEADD(day, 1, '2024-01-01'::DATE); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL compilation "
                + "error: Date/time component [TO_DOUBLE('5.5') ]for function DATEADD needs to be an identifier or a "
                + "string literal."},
            {"BEGIN LET day := TRUE; RETURN DATEADD(day, 1, '2024-01-01'::DATE); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: Date/time component [TO_BOOLEAN('true') ]for function DATEADD needs to be an identifier or "
                + "a string literal."},
            {"BEGIN LET year := 5; RETURN DATE_TRUNC(year, '2024-01-01'::DATE); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 28 : SQL compilation "
                + "error: Date/time component [TO_NUMBER('5') ]for function DATE_TRUNC needs to be an identifier or "
                + "a string literal."},
            {"BEGIN LET day INT := 5; RETURN DATEADD(day, 1, '2024-01-01'::DATE); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 31 : SQL compilation "
                + "error: Date/time component [TO_NUMBER('5') ]for function DATEADD needs to be an identifier or a "
                + "string literal."},
        });
    }
}
