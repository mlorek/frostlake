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
 * A refusal in a block's expression is placed in the expression as the account compiles it, each variable ahead of
 * the place written out as its cast, a VARIANT, OBJECT or ARRAY one read back through PARSE_JSON as well, and a bind
 * as a placeholder; an untyped LET over a function no one defined is refused as the block compiles, bare (all
 * live-verified).
 */
public class BoundVariablePlaceTest extends MeasuredCellsTestSupport {

    @Test
    public void blockExpressionRefusals() {
        assertAnswer("REFUSED SQL compilation error: | Unknown function NOSUCHFN_ZZ.",
            "EXECUTE IMMEDIATE $$ BEGIN LET n := NOSUCHFN_ZZ(1); RETURN n; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL "
                + "compilation error: | Unknown function NOSUCHFN_ZZ.",
            "EXECUTE IMMEDIATE $$ DECLARE n NUMBER; BEGIN n := NOSUCHFN_ZZ(1); RETURN n; END; $$");
        assertAnswer("REFUSED SQL compilation error: | Unknown function NOSUCHFN_ZZ.",
            "EXECUTE IMMEDIATE $$ BEGIN LET n := UPPER(NOSUCHFN_ZZ(1)); RETURN n; END; $$");
        assertAnswer("REFUSED SQL compilation error: | Unknown function NOSUCHFN_ZZ.",
            "EXECUTE IMMEDIATE $$ BEGIN LET n := 1; IF (n > 0) THEN LET m := NOSUCHFN_ZZ(2); END IF; RETURN n; END; "
                + "$$");
        assertAnswer("REFUSED SQL compilation error: | Unknown function NOSUCHFN_ZZ.",
            "EXECUTE IMMEDIATE $$ BEGIN LET n := 1 + NOSUCHFN_ZZ(1); RETURN n; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: error line 1 at position 43 | Invalid argument types for function '||': "
                + "(BOOLEAN, VARCHAR(1), BOOLEAN)",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(3.5::FLOAT); RETURN IS_DOUBLE(v) || ' ' ||"
                + " IS_DECIMAL(v); END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: error line 1 at position 40 | Invalid argument types for function '||': "
                + "(BOOLEAN, VARCHAR(1), BOOLEAN)",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(3.5::FLOAT); RETURN IS_DOUBLE(v)||' '||"
                + "IS_DECIMAL(v); END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: error line 1 at position 41 | Invalid argument types for function '||': "
                + "(BOOLEAN, VARCHAR(1), BOOLEAN)",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(3.5::FLOAT); RETURN IS_DOUBLE(:v) || ' ' |"
                + "| IS_DECIMAL(v); END; $$");
    }

    @Test
    public void eachVariableWrittenOutAheadOfThePlace() {
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : SQL "
                + "compilation error: error line 1 at position 15 | Invalid argument types for function '||': "
                + "(BOOLEAN, VARCHAR(1), BOOLEAN)",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN (1 > 0) || ' ' || TRUE; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 40 : SQL "
                + "compilation error: error line 1 at position 31 | Invalid argument types for function '||': "
                + "(BOOLEAN, VARCHAR(1), BOOLEAN)",
            "EXECUTE IMMEDIATE $$ DECLARE n NUMBER; BEGIN n := 1; RETURN (n > 0) || ' ' || TRUE; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 43 : SQL "
                + "compilation error: error line 1 at position 19 | Invalid argument types for function '||': "
                + "(BOOLEAN, VARCHAR(1), BOOLEAN)",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR; BEGIN s := 'a'; RETURN (s > 'b') || ' ' || TRUE; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: error line 1 at position 38 | Invalid argument types for function '||': "
                + "(BOOLEAN, VARCHAR(1), BOOLEAN)",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(3.5::FLOAT); RETURN (v > 0) || ' ' || "
                + "TRUE; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: error line 1 at position 43 | Invalid argument types for function '||': "
                + "(BOOLEAN, VARCHAR(1), BOOLEAN)",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(3.5::FLOAT); RETURN IS_DOUBLE(v) || ' ' ||"
                + " TRUE; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 72 : SQL "
                + "compilation error: error line 1 at position 51 | Invalid argument types for function '||': "
                + "(BOOLEAN, VARCHAR(1), BOOLEAN)",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(3.5::FLOAT); LET r VARCHAR := "
                + "IS_DOUBLE(v) || ' ' || TRUE; RETURN r; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: error line 1 at position 42 | Invalid argument types for function '||': "
                + "(BOOLEAN, VARCHAR(1), BOOLEAN)",
            "EXECUTE IMMEDIATE $$ DECLARE o OBJECT; BEGIN o := OBJECT_CONSTRUCT('a', 1); RETURN IS_OBJECT(o) || ' ' |"
                + "| TRUE; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 55 : SQL "
                + "compilation error: error line 1 at position 43 | Invalid argument types for function '||': "
                + "(BOOLEAN, VARCHAR(1), BOOLEAN)",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT('x'); RETURN IS_DOUBLE(v) || ' ' || TRUE; "
                + "END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 66 : SQL "
                + "compilation error: error line 1 at position 43 | Invalid argument types for function '||': "
                + "(BOOLEAN, VARCHAR(1), BOOLEAN)",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(12345.5::FLOAT); RETURN IS_DOUBLE(v) || ' "
                + "' || TRUE; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 44 : SQL "
                + "compilation error: error line 1 at position 31 | Invalid argument types for function '||': "
                + "(BOOLEAN, VARCHAR(1), BOOLEAN)",
            "EXECUTE IMMEDIATE $$ DECLARE n NUMBER; BEGIN n := 12345; RETURN (n > 0) || ' ' || TRUE; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: error line 1 at position 41 | Invalid argument types for function '||': "
                + "(BOOLEAN, VARCHAR(1), BOOLEAN)",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(3.5::FLOAT); RETURN IS_DOUBLE(:v) || ' ' |"
                + "| TRUE; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 56 : SQL "
                + "compilation error: error line 1 at position 40 | Invalid argument types for function '||': "
                + "(BOOLEAN, VARCHAR(1), BOOLEAN)",
            "EXECUTE IMMEDIATE $$ DECLARE a ARRAY; BEGIN a := ARRAY_CONSTRUCT(1); RETURN IS_ARRAY(a) || ' ' || TRUE; "
                + "END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 40 : SQL "
                + "compilation error: error line 1 at position 37 | Invalid argument types for function '||': "
                + "(BOOLEAN, VARCHAR(1), BOOLEAN)",
            "EXECUTE IMMEDIATE $$ DECLARE n NUMBER; BEGIN n := 1; RETURN IS_INTEGER(n) || ' ' || TRUE; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 40 : SQL "
                + "compilation error: error line 1 at position 31 | Invalid argument types for function '||': "
                + "(BOOLEAN, VARCHAR(1), BOOLEAN)",
            "EXECUTE IMMEDIATE $$ DECLARE n NUMBER; BEGIN n := 1; RETURN (n > 0) || ' ' || (n > 1); END; $$");
    }
}
