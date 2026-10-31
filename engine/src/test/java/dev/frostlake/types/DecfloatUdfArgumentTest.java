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

package dev.frostlake.types;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A DECFLOAT argument converts to a SQL function's exact NUMBER parameter, rounding half away from zero at the
 * parameter's scale, and the body is typed at the parameter's type; a SQL table function refuses it, and a
 * DECFLOAT parameter takes nothing but a DECFLOAT (all live-verified).
 */
public class DecfloatUdfArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void scalarFunctions() {
        assertAnswer("Function F2244 successfully created.",
            "CREATE OR REPLACE FUNCTION f2244(x NUMBER) RETURNS NUMBER AS 'x + 1'");
        assertAnswer("3", "SELECT f2244(1.5::DECFLOAT)");
        assertAnswer("Function G2244 successfully created.",
            "CREATE OR REPLACE FUNCTION g2244(x NUMBER(10,2)) RETURNS NUMBER(10,2) AS 'x'");
        assertAnswer("1.26", "SELECT g2244(1.257::DECFLOAT)");
        assertAnswer("1.257", "SELECT g2244(1.257)");
        assertAnswer("Function H2244 successfully created.",
            "CREATE OR REPLACE FUNCTION h2244(x FLOAT) RETURNS FLOAT AS 'x'");
        assertAnswer("1.5", "SELECT h2244(1.5::DECFLOAT)");
        assertAnswer("Function K2244 successfully created.",
            "CREATE OR REPLACE FUNCTION k2244(x VARCHAR) RETURNS VARCHAR AS 'x'");
        assertAnswer("1.5", "SELECT k2244(1.5::DECFLOAT)");
        assertAnswer("Function D2244 successfully created.",
            "CREATE OR REPLACE FUNCTION d2244(x DECFLOAT) RETURNS DECFLOAT AS 'x'");
        assertAnswer("1.5", "SELECT d2244(1.5::DECFLOAT)");
        assertAnswer("4|-1|101", "SELECT f2244(2.5::DECFLOAT), f2244(-1.5::DECFLOAT), f2244(1e2::DECFLOAT)");
        assertAnswer("NUMBER(38,0)[SB1]", "SELECT SYSTEM$TYPEOF(f2244(1.5::DECFLOAT))");
        assertAnswer("3", "SELECT f2244(TO_DECFLOAT('1.5'))");
        assertAnswer("Function P2244 successfully created.",
            "CREATE OR REPLACE PROCEDURE p2244(x NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN x + 1; END; "
                + "$$");
        assertAnswer("3", "CALL p2244(1.5::DECFLOAT)");
        assertAnswer("Function T2244 successfully created.",
            "CREATE OR REPLACE FUNCTION t2244(x NUMBER) RETURNS TABLE (y NUMBER) AS 'SELECT x'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for "
                + "function 'T2244': (DECFLOAT(38))",
            "SELECT * FROM TABLE(t2244(1.5::DECFLOAT))");
        assertAnswer("2.5", "SELECT f2244(1.5)");
    }

    @Test
    public void typesAndColumns() {
        engine.execute("CREATE OR REPLACE FUNCTION d2244(x DECFLOAT) RETURNS DECFLOAT AS 'x'");
        engine.execute("CREATE OR REPLACE FUNCTION f2244(x NUMBER) RETURNS NUMBER AS 'x + 1'");
        engine.execute("SET w = (SELECT 1::DECFLOAT / 3)");
        engine.execute("SET n = 1.5");
        assertShape("C:DECFLOAT(38,0)", "SELECT 1.5::DECFLOAT AS c");
        assertShape("C:DECFLOAT(38,0)", "SELECT $w AS c");
        assertShape("C:NUMBER(2,1)", "SELECT $n AS c");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 21 | Invalid argument types for "
                + "function 'D2244': (NUMBER(2,1))",
            "SELECT SYSTEM$TYPEOF(d2244(1.5))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'D2244': (NUMBER(2,1))",
            "SELECT d2244(1.5), d2244(1.5)::VARCHAR");
        assertShape("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'D2244': (NUMBER(2,1))",
            "SELECT d2244(1.5) AS c");
        assertShape("C:NUMBER(38,0)", "SELECT f2244(1.5::DECFLOAT) AS c");
        assertAnswer("NUMBER(38,0)[SB1]", "SELECT SYSTEM$TYPEOF(f2244(TO_DECFLOAT('2.5')))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 21 | Invalid argument types for "
                + "function 'D2244': (NUMBER(1,0))",
            "SELECT SYSTEM$TYPEOF(d2244(2)), SYSTEM$TYPEOF(d2244(1.5::FLOAT))");
        assertShape("C:DECFLOAT(38,0)", "SELECT $w + 1 AS c");
        assertShape("C:VARCHAR(134217728)", "SELECT $w::VARCHAR AS c");
    }
}
