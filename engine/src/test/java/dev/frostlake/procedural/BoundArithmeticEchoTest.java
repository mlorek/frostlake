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

package dev.frostlake.procedural;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A block's call refused for its quantifier echoes its arguments as SQL computes them: each NUMBER variable as
 * its value's conversion, in parentheses beside an operator, a negation as NEGATE, a text variable as its
 * literal (all live-verified).
 */
public class BoundArithmeticEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void quantifiedCalls() {
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL "
                + "compilation error: | invalid use of 'all' for function 'ABS(ALL (CAST('5' AS NUMBER(38,0))) + 1)'",
            "EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN ABS(ALL n + 1); END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL "
                + "compilation error: | invalid use of 'all' for function 'ABS(ALL (CAST('5' AS NUMBER(38,0))) * 2)'",
            "EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN ABS(ALL n * 2); END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL "
                + "compilation error: | invalid use of 'all' for function 'ABS(ALL CAST('5' AS NUMBER(38,0)))'",
            "EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN ABS(ALL n); END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL "
                + "compilation error: | invalid use of 'all' for function 'ABS(ALL 1 + (CAST('5' AS NUMBER(38,0))))'",
            "EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN ABS(ALL 1 + n); END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL "
                + "compilation error: | invalid use of 'all' for function 'ABS(ALL (CAST('5' AS NUMBER(38,0))) + "
                + "(CAST('5' AS NUMBER(38,0))))'",
            "EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN ABS(ALL n + n); END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL "
                + "compilation error: | invalid use of 'all' for function 'ABS(ALL (CAST('5' AS NUMBER(38,0))) + 1)'",
            "EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN ABS(ALL (n + 1)); END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL "
                + "compilation error: | invalid use of 'all' for function 'ABS(ALL NEGATE(CAST('5' AS NUMBER(38,0))))'",
            "EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN ABS(ALL -n); END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 42 : SQL "
                + "compilation error: | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST('1.50' AS "
                + "NUMBER(10,2)))'",
            "EXECUTE IMMEDIATE $$ BEGIN LET n NUMBER(10,2) := 1.50; RETURN UPPER(DISTINCT n); END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 42 : SQL "
                + "compilation error: | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST('1.50' AS "
                + "NUMBER(10,2)))'",
            "EXECUTE IMMEDIATE $$ BEGIN LET n NUMBER(10,2) := 1.50; RETURN IFF(TRUE, UPPER(DISTINCT n), 'x'); END; $$");
        assertAnswer("true", "EXECUTE IMMEDIATE $$ BEGIN LET i INT := 1; RETURN LAST_QUERY_ID(i) IS NOT NULL; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 36 : SQL "
                + "compilation error: | invalid use of 'distinct' for function 'UPPER(DISTINCT 'a' || 'b')'",
            "EXECUTE IMMEDIATE $$ BEGIN LET s VARCHAR := 'a'; RETURN UPPER(DISTINCT s || 'b'); END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL "
                + "compilation error: | invalid use of 'all' for function 'ABS(ALL (CAST('5' AS NUMBER(38,0))) + 1)'",
            "EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN ABS(ALL n + 1) + 1; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 42 : SQL "
                + "compilation error: | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST('1.50' AS "
                + "NUMBER(10,2)))'",
            "EXECUTE IMMEDIATE $$ BEGIN LET n NUMBER(10,2) := 1.50; RETURN COALESCE(UPPER(DISTINCT n), 'x'); END; $$");
    }
}
