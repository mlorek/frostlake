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
 * LAST_QUERY_ID refuses an index over a block's scaled NUMBER variable and echoes it over the variable's binding
 * (live-verified): beside a number written out, a sum, a difference or a remainder prints the number as written -
 * {@code CAST(2 + 1 AS NUMBER(18,0))} for {@code n + 1} over a NUMBER(10,2) holding 2, a cast literal folded too -
 * and the variable at its declared type where the number has more whole digits than the type takes; a quotient and
 * a product print as before.
 */
public class ScaledVariableIndexEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void aWrittenNumberBesideTheVariable() {
        assertCells(new String[][] {
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(2 + 1 AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 1.50; RETURN LAST_QUERY_ID(n + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 41 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(1.5 + 1 AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n - 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(2 - 1 AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(1 + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(1 + 2 AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n + 0.5) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(2 + 0.5 AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(2 + 2 AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n * 2) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(2 * 2 AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n / 2) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST((CAST(2 AS "
                + "NUMBER(16,8))) / 2 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n + 1.25) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(2 + 1.25 AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n - 0.5) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(2 - 0.5 AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(2 - n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(2 - 2 AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n + 1 + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST((2 + 1) + 1 AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,0) := 2; RETURN LAST_QUERY_ID(n + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '2 + 1'"},
            {"BEGIN LET n NUMBER(5,3) := 2; RETURN LAST_QUERY_ID(n + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(2 + 1 AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(-n + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST((NEGATE(2)) + 1 "
                + "AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID((n) + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(2 + 1 AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n + 1::NUMBER(4,1)) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(2 + 1 AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n % 2) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(2 % 2 AS "
                + "NUMBER(18,0))'"},
            {"SELECT LAST_QUERY_ID(CAST('2' AS NUMBER(10,2)) + 1) IS NOT NULL",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(1 AS NUMBER(10,2))) AS NUMBER(18,0))'"},
            {"SELECT LAST_QUERY_ID(CAST('2' AS NUMBER(10,2)) - 1) IS NOT NULL",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 - (CAST(1 AS NUMBER(10,2))) AS NUMBER(18,0))'"},
            {"SELECT LAST_QUERY_ID(1 + CAST('2' AS NUMBER(10,2))) IS NOT NULL",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(1 AS NUMBER(10,2))) + 2 AS NUMBER(18,0))'"},
            {"SELECT LAST_QUERY_ID(CAST('2' AS NUMBER(10,2)) + 0.5) IS NOT NULL",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(0.5 AS NUMBER(10,2))) AS NUMBER(18,0))'"},
        });
    }

    @Test
    public void moreShapes() {
        assertCells(new String[][] {
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n + 1.125) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST((CAST(2 AS "
                + "NUMBER(11,3))) + 1.125 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(1.125 + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(1.125 + (CAST(2 "
                + "AS NUMBER(11,3))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(10,3) := 1; RETURN LAST_QUERY_ID(n + m) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST((CAST(2 AS "
                + "NUMBER(11,3))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET f FLOAT := 1.5; RETURN LAST_QUERY_ID(n + f) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 58 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(2.0 + 1.5 AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n % 0.5) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(2 % 0.5 AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n - 1.125) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST((CAST(2 AS "
                + "NUMBER(11,3))) - 1.125 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID((n + 1) * 2) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST((2 + 1) * 2 AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n * 2 + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST((2 * 2) + 1 AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n + 1 - 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST((2 + 1) - 1 AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(ABS(n) + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST((ABS(2)) + 1 AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET k NUMBER(10,2) := 3; RETURN LAST_QUERY_ID(n + k + 1) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST((2 + 3) + 1 AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET i INT := 5; RETURN LAST_QUERY_ID(i + 1.5) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST((CAST(5 AS "
                + "NUMBER(38,1))) + 1.5 AS NUMBER(37,0))'"},
            {"BEGIN LET i INT := 5; RETURN LAST_QUERY_ID(i + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '5 + 1'"},
            {"BEGIN LET n NUMBER(38,2) := 2; RETURN LAST_QUERY_ID(n + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(2 + 1 AS "
                + "NUMBER(36,0))'"},
            {"BEGIN LET n NUMBER(4,2) := 2; RETURN LAST_QUERY_ID(n + 1000) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST((CAST(2 AS "
                + "NUMBER(4,2))) + 1000 AS NUMBER(18,0))'"},
            {"SELECT LAST_QUERY_ID(2 + 1::NUMBER(4,1)) IS NOT NULL",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(4,1))) + (CAST(1 AS NUMBER(4,1))) AS NUMBER(18,0))'"},
            {"SELECT LAST_QUERY_ID(1.5 + 1) IS NOT NULL",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(1.5 + (CAST(1 AS NUMBER(3,1))) AS NUMBER(18,0))'"},
            {"SELECT LAST_QUERY_ID(2.00 + 1) IS NOT NULL",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'2 + 1'"},
        });
    }
}
