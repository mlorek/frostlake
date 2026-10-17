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
 * NaN, the infinities and arguments outside a function's domain (live-verified): ROUND, TRUNC, CEIL and FLOOR hand a
 * non-finite FLOAT through; SIGN of a FLOAT is a FLOAT, 0.0 for NaN; SQRT, LN, ACOS, ASIN, POWER, ATANH and ACOSH
 * outside their domain are refused "Invalid floating point operation: …", the arguments printed as C's %g prints
 * them, in a query and in a block alike.
 */
public class FloatingPointEdgeTest extends MeasuredCellsTestSupport {

    @Test
    public void nonFiniteValuesAndDomainFaultsAnswerAsLiveDoes() {
        assertCells(new String[][] {
            {"SELECT ROUND('inf'::FLOAT)", "Infinity"},
            {"SELECT ROUND('NaN'::FLOAT), ROUND('-inf'::FLOAT), ROUND('inf'::FLOAT, 2)", "NaN|-Infinity|Infinity"},
            {"SELECT ROUND('NaN'::FLOAT, -1), TRUNC('inf'::FLOAT), CEIL('inf'::FLOAT), FLOOR('-inf'::FLOAT)",
                "NaN|Infinity|Infinity|-Infinity"},
            {"SELECT ABS('-inf'::FLOAT), SIGN('NaN'::FLOAT), SIGN('-inf'::FLOAT), SIGN('inf'::FLOAT)",
                "Infinity|0.0|-1.0|1.0"},
            {"SELECT SYSTEM$TYPEOF(SIGN('inf'::FLOAT)), SYSTEM$TYPEOF(SIGN(-2.5))", "FLOAT[DOUBLE]|NUMBER(2,0)[SB1]"},
            {"SELECT SIGN(-2.5::FLOAT), SIGN(0::FLOAT), SIGN(-3)", "-1.0|0.0|-1"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN ROUND('inf'::FLOAT); END; $$", "Infinity"},
            {"SELECT SQRT(-1)", "REFUSED Invalid floating point operation: sqrt(-1)"},
            {"SELECT SQRT(-1::FLOAT)", "REFUSED Invalid floating point operation: sqrt(-1)"},
            {"SELECT SQRT(n) FROM (SELECT -4 AS n)", "REFUSED Invalid floating point operation: sqrt(-4)"},
            {"SELECT SQRT(-2.5)", "REFUSED Invalid floating point operation: sqrt(-2.5)"},
            {"SELECT SQRT(-2.50::NUMBER(10,2))", "REFUSED Invalid floating point operation: sqrt(-2.5)"},
            {"SELECT SQRT(-1e10)", "REFUSED Invalid floating point operation: sqrt(-1e+10)"},
            {"SELECT SQRT(-0.5::FLOAT)", "REFUSED Invalid floating point operation: sqrt(-0.5)"},
            {"SELECT LN(-1)", "REFUSED Invalid floating point operation: log(e,-1)"},
            {"SELECT LN(0)", "REFUSED Invalid floating point operation: log(e,0)"},
            {"SELECT LN(n) FROM (SELECT 0 AS n)", "REFUSED Invalid floating point operation: log(e,0)"},
            {"SELECT LN(-2.5)", "REFUSED Invalid floating point operation: log(e,-2.5)"},
            {"SELECT ACOS(2)", "REFUSED Invalid floating point operation: acos(2)"},
            {"SELECT ASIN(2)", "REFUSED Invalid floating point operation: asin(2)"},
            {"SELECT ACOS(1.5)", "REFUSED Invalid floating point operation: acos(1.5)"},
            {"SELECT POWER(-8, 0.5)", "REFUSED Invalid floating point operation: pow(-8, 0.5)"},
            {"SELECT POWER(-8, 1.5)", "REFUSED Invalid floating point operation: pow(-8, 1.5)"},
            {"SELECT POWER(-8.5, 0.5)", "REFUSED Invalid floating point operation: pow(-8.5, 0.5)"},
            {"SELECT ATANH(2)", "REFUSED Invalid floating point operation: atanh(2)"},
            {"SELECT ACOSH(0)", "REFUSED Invalid floating point operation: acosh(0)"},
            {"SELECT ATANH(-1.5)", "REFUSED Invalid floating point operation: atanh(-1.5)"},
            {"SELECT ACOSH(0.5)", "REFUSED Invalid floating point operation: acosh(0.5)"},
            {"SELECT TRY_TO_NUMBER(SQRT(-1)::VARCHAR)", "REFUSED Invalid floating point operation: sqrt(-1)"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN SQRT(-1); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : Invalid floating "
                + "point operation: sqrt(-1)"},
            {"SELECT LOG(10, -1)", "REFUSED Invalid floating point operation: log(10,-1)"},
            {"SELECT SQRT('NaN'::FLOAT), SQRT('inf'::FLOAT)", "NaN|Infinity"},
            {"SELECT SQRT(4)", "2.0"},
            {"SELECT LN(1)", "0.0"},
        });
    }
}
