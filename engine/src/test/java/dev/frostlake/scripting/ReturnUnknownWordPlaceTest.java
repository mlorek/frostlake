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
 * A word a block's RETURN or IF condition cannot resolve - an unknown word where LAST_DAY or a date function reads its
 * unit - is an invalid identifier placed at the expression's root (live-verified): the operator a first operand
 * stands before ({@code IS}, {@code =}, {@code ::}, {@code ||}, {@code BETWEEN}), a bracketed expression's own root,
 * else the first token; a LET's value is placed at its start.
 */
public class ReturnUnknownWordPlaceTest extends MeasuredCellsTestSupport {

    @Test
    public void theRootPlacesTheWord() {
        assertCells(new String[][] {
            {"BEGIN RETURN LAST_DAY(CURRENT_DATE(), nosuch_var_x9) IS NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 40 | invalid identifier 'NOSUCH_VAR_X9'"},
            {"BEGIN\n  RETURN LAST_DAY(CURRENT_DATE(), nosuch_var_x9) IS NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 2 at position 9 : SQL compilation "
                + "error: error line 1 at position 40 | invalid identifier 'NOSUCH_VAR_X9'"},
            {"BEGIN RETURN 1 + LAST_DAY(CURRENT_DATE(), nosuch_var_x9) IS NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 44 | invalid identifier 'NOSUCH_VAR_X9'"},
            {"BEGIN RETURN LAST_DAY(CURRENT_DATE(),   nosuch_var_x9) IS NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 42 | invalid identifier 'NOSUCH_VAR_X9'"},
            {"BEGIN RETURN LAST_DAY(CURRENT_DATE(), nosuch_var_x9) = CURRENT_DATE(); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 40 | invalid identifier 'NOSUCH_VAR_X9'"},
            {"BEGIN IF (LAST_DAY(CURRENT_DATE(), nosuch_var_x9) IS NULL) THEN RETURN 1; END IF; RETURN 2; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 10 : SQL compilation "
                + "error: error line 1 at position 40 | invalid identifier 'NOSUCH_VAR_X9'"},
            {"BEGIN RETURN 1 + 2 + LAST_DAY(CURRENT_DATE(), nosuch_var_x9) IS NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 48 | invalid identifier 'NOSUCH_VAR_X9'"},
            {"BEGIN RETURN (LAST_DAY(CURRENT_DATE(), nosuch_var_x9)); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 1 | invalid identifier 'NOSUCH_VAR_X9'"},
            {"BEGIN RETURN LAST_DAY(CURRENT_DATE(), nosuch_var_x9)::VARCHAR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 39 | invalid identifier 'NOSUCH_VAR_X9'"},
            {"BEGIN RETURN 'abc' || LAST_DAY(CURRENT_DATE(), nosuch_var_x9); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 6 | invalid identifier 'NOSUCH_VAR_X9'"},
            {"BEGIN RETURN LAST_DAY(CURRENT_DATE(), nosuch_var_x9); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 0 | invalid identifier 'NOSUCH_VAR_X9'"},
            {"BEGIN RETURN NOT (LAST_DAY(CURRENT_DATE(), nosuch_var_x9) IS NULL); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 0 | invalid identifier 'NOSUCH_VAR_X9'"},
            {"BEGIN RETURN COALESCE(LAST_DAY(CURRENT_DATE(), nosuch_var_x9), CURRENT_DATE()); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 0 | invalid identifier 'NOSUCH_VAR_X9'"},
            {"BEGIN LET b BOOLEAN := LAST_DAY(CURRENT_DATE(), nosuch_var_x9) IS NULL; RETURN b; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 23 : SQL compilation "
                + "error: error line 1 at position 0 | invalid identifier 'NOSUCH_VAR_X9'"},
            {"BEGIN RETURN (LAST_DAY(CURRENT_DATE(), nosuch_var_x9) IS NULL); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 41 | invalid identifier 'NOSUCH_VAR_X9'"},
            {"BEGIN RETURN LAST_DAY(CURRENT_DATE(), nosuch_var_x9) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 40 | invalid identifier 'NOSUCH_VAR_X9'"},
            {"BEGIN RETURN LAST_DAY(CURRENT_DATE(), nosuch_var_x9) BETWEEN CURRENT_DATE() AND CURRENT_DATE(); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 40 | invalid identifier 'NOSUCH_VAR_X9'"},
            {"BEGIN IF (TRUE) THEN RETURN 0; ELSEIF (LAST_DAY(CURRENT_DATE(), nosuch_var_x9) IS NULL) THEN "
                + "RETURN 1; END IF; RETURN 2; END;",
                "0"},
            {"BEGIN RETURN DATEADD(nosuch_unit_x9, 1, CURRENT_DATE()) IS NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 43 | invalid identifier 'NOSUCH_UNIT_X9'"},
            {"BEGIN RETURN DATE_PART(nosuch_unit_x9, CURRENT_DATE()) IS NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 42 | invalid identifier 'NOSUCH_UNIT_X9'"},
            {"BEGIN IF (DATEADD(nosuch_unit_x9, 1, CURRENT_DATE()) IS NULL) THEN RETURN 1; END IF; RETURN 2; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 10 : SQL compilation "
                + "error: error line 1 at position 43 | invalid identifier 'NOSUCH_UNIT_X9'"},
            {"BEGIN RETURN DATEDIFF(nosuch_unit_x9, CURRENT_DATE(), CURRENT_DATE()) > 0; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 57 | invalid identifier 'NOSUCH_UNIT_X9'"},
        });
    }

    @Test
    public void anUnknownUnitInsideTheExpression() {
        assertCells(new String[][] {
            {"BEGIN RETURN DATEADD(nosuch_unit_x9, 1, CURRENT_DATE()) IS NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 43 | invalid identifier 'NOSUCH_UNIT_X9'"},
            {"BEGIN RETURN DATE_TRUNC(nosuch_unit_x9, CURRENT_DATE()) IS NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 43 | invalid identifier 'NOSUCH_UNIT_X9'"},
            {"BEGIN RETURN DATEADD(nosuch_unit_x9, 1, CURRENT_DATE()); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 0 | invalid identifier 'NOSUCH_UNIT_X9'"},
            {"BEGIN LET d DATE := DATEADD(nosuch_unit_x9, 1, CURRENT_DATE()); RETURN d; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 20 : SQL compilation "
                + "error: error line 1 at position 0 | invalid identifier 'NOSUCH_UNIT_X9'"},
            {"SELECT DATEADD(nosuch_unit_x9, 1, CURRENT_DATE())",
                "REFUSED SQL compilation error: ['NOSUCH_UNIT_X9'] is not a valid date/time component for function "
                + "DATEADD."},
            {"SELECT DATEADD(nosuch_unit_x9, 1, CURRENT_DATE()) IS NULL",
                "REFUSED SQL compilation error: ['NOSUCH_UNIT_X9'] is not a valid date/time component for function "
                + "DATEADD."},
            {"BEGIN RETURN DATEDIFF(nosuch_unit_x9, CURRENT_DATE(), CURRENT_DATE()) IS NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 57 | invalid identifier 'NOSUCH_UNIT_X9'"},
            {"BEGIN RETURN EXTRACT(nosuch_unit_x9 FROM CURRENT_DATE()) IS NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | invalid value [nosuch_unit_x9] for parameter 'EXTRACT date/time part'"},
            {"BEGIN RETURN DATE_PART(nosuch_unit_x9, CURRENT_DATE()) IS NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 42 | invalid identifier 'NOSUCH_UNIT_X9'"},
            {"BEGIN IF (DATEADD(nosuch_unit_x9, 1, CURRENT_DATE()) IS NULL) THEN RETURN 1; END IF; RETURN 2; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 10 : SQL compilation "
                + "error: error line 1 at position 43 | invalid identifier 'NOSUCH_UNIT_X9'"},
            {"BEGIN RETURN DATEADD(day, 1, '2024-01-01'::DATE) IS NULL; END;", "false"},
            {"BEGIN RETURN DATEADD('nosuch_unit_x9', 1, CURRENT_DATE()) IS NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: ['nosuch_unit_x9'] is not a valid date/time component for function DATEADD."},
            {"BEGIN RETURN TIMEADD(nosuch_unit_x9, 1, CURRENT_TIME()) IS NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 43 | invalid identifier 'NOSUCH_UNIT_X9'"},
        });
    }
}
