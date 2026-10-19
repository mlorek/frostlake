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
 * COSH computes as the account does - half the exponential plus half its reciprocal, expm1 near zero - so
 * COSH(1) is 1.5430806348152437 where the JDK's is a unit in the last place off, and NORMAL, UNIFORM and ZIPF
 * read their generator as RANDOM reads its seed: a whole number past 64 bits is no number (all live-verified).
 */
public class CoshAndGeneratorSeedTest extends MeasuredCellsTestSupport {

    @Test
    public void aGeneratorPast64BitsIsNoNumberAndCoshRoundsAsTheAccount() {
        assertAnswer("REFUSED Numeric value '10000000000000000000' is not recognized", "SELECT NORMAL(0, 0, 1e19)");
        assertAnswer("true", "SELECT NORMAL(0, 0, 9223372036854775807) IS NOT NULL");
        assertShapedAnswer("REFUSED Numeric value '<ID19>' is not recognized",
            "SELECT NORMAL(0, 0, 9223372036854775808)");
        assertAnswer("REFUSED Numeric value '-1000000000000000000000000000000' is not recognized",
            "SELECT NORMAL(0, 0, -1e30)");
        assertAnswer("true", "SELECT NORMAL(0, 0, 1.5) IS NOT NULL");
        assertAnswer("REFUSED Numeric value '1000000000000000000000000000000' is not recognized",
            "SELECT UNIFORM(1, 10, 1e30)");
        assertAnswer("REFUSED Numeric value '1000000000000000000000000000000' is not recognized",
            "SELECT ZIPF(1, 10, 1e30)");
        assertAnswer("REFUSED Numeric value '1000000000000000000000000000000' is not recognized",
            "SELECT RANDOM(1e30)");
        assertAnswer("true|false", "SELECT COSH(1) = 1.5430806348152437::FLOAT, COSH(1) = 1.543080634815244::FLOAT");
        assertAnswer("false|true",
            "SELECT COSH(0.4) = 1.0810723718384547::FLOAT, COSH(0.4) = 1.081072371838455::FLOAT");
        assertAnswer("true|false",
            "SELECT COSH(0.42) = 1.0895041877168452::FLOAT, COSH(0.67) = 1.2329729492112411::FLOAT");
        assertAnswer("false|false",
            "SELECT COSH(0.74) = 1.2865247150076995::FLOAT, COSH(0.65) = 1.218793302887456::FLOAT");
        assertAnswer("true|true", "SELECT COSH(0.75) = 1.2946832846768448::FLOAT, COSH(4) = 27.308232836016487::FLOAT");
        assertAnswer("true|false",
            "SELECT COSH(1.25) = 1.8884238771610158::FLOAT, COSH(-0.4) = 1.0810723718384547::FLOAT");
        assertAnswer("1.543080635", "SELECT COSH(1)::VARCHAR");
        assertAnswer("true|true", "SELECT COSH(709) > 0, COSH(710) > 0");
        assertAnswer("Infinity", "SELECT COSH(711)");
    }

    @Test
    public void aBlockVariableCastAndANormalGenerator() {
        assertAnswer("REFUSED Numeric value '1000000000000000000000000000000' is not recognized",
            "SELECT NORMAL(0, 0, 1e30)");
        assertAnswer("true", "SELECT NORMAL(0, 0, 1e18) IS NOT NULL");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 32 : SQL "
                + "compilation error: | invalid type [NUMBER(38,0)] for parameter 'AS_INTEGER(variantValue...)'",
            "BEGIN LET n NUMBER := 5; RETURN AS_INTEGER(n); END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | invalid type [NUMBER(10,2)] for parameter 'AS_NUMBER(variantValue...)'",
            "BEGIN LET n NUMBER(10,2) := 5; RETURN AS_NUMBER(n); END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | invalid type [NUMBER(38,0)] for parameter 'AS_INTEGER(variantValue...)'",
            "DECLARE n INT DEFAULT 5; BEGIN RETURN AS_INTEGER(n); END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 35 : SQL "
                + "compilation error: error line 1 at position 0 | Invalid argument types for function 'AS_VARCHAR': "
                + "(VARCHAR(1))",
            "BEGIN LET s VARCHAR := 'x'; RETURN AS_VARCHAR(s); END;");
    }
}
