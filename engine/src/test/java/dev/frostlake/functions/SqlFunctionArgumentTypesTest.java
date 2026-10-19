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
 * A SQL function's exact parameter takes its type from the argument's declared type, not from the digits it
 * holds: the argument's own type where its scale is the parameter's or larger, else the parameter's scale over the
 * wider of the two whole parts - 7::NUMBER(10,0) into a NUMBER is a NUMBER(10,0), a NUMBER(10,0) column into a
 * NUMBER(10,2) a NUMBER(12,2) - and a binary literal argument keeps its width, X'AB' a BINARY(1), in a scalar
 * function and a table function alike (all live-verified).
 */
public class SqlFunctionArgumentTypesTest extends MeasuredCellsTestSupport {

    @Test
    public void aParameterIsTypedByItsArgumentsDeclaredType() {
        engine.execute("CREATE OR REPLACE FUNCTION n0(x NUMBER) RETURNS VARCHAR AS 'SYSTEM$TYPEOF(x)'");
        engine.execute("CREATE OR REPLACE FUNCTION n2(x NUMBER(10,2)) RETURNS VARCHAR AS 'SYSTEM$TYPEOF(x) || '' '' |"
            + "| x'");
        engine.execute("CREATE OR REPLACE TABLE tc (i NUMBER(10,0), n NUMBER(10,4))");
        engine.execute("INSERT INTO tc VALUES (3, 1.2345)");
        engine.execute("CREATE OR REPLACE FUNCTION tb(b BINARY) RETURNS TABLE (t VARCHAR) AS 'SELECT "
            + "SYSTEM$TYPEOF(b)'");
        engine.execute("CREATE OR REPLACE FUNCTION bs(b BINARY) RETURNS VARCHAR AS 'SYSTEM$TYPEOF(b)'");
        assertAnswer("NUMBER(10,0)[SB1]", "SELECT n0(7::NUMBER(10,0))");
        assertAnswer("NUMBER(38,2)[SB2] 2.00", "SELECT n2(2::NUMBER(38,0))");
        assertAnswer("NUMBER(10,4)[SB2] 1.2345|NUMBER(10,4)[SB2]", "SELECT n2(n), n0(n) FROM tc");
        assertAnswer("NUMBER(12,2)[SB2] 3.00", "SELECT n2(i) FROM tc");
        assertAnswer("NUMBER(10,0)[SB1]", "SELECT n0(i) FROM tc");
        assertAnswer("NUMBER(1,0)[SB1]", "SELECT n0(7)");
        assertAnswer("NUMBER(2,1)[SB1]", "SELECT n0(7.5)");
        assertAnswer("NUMBER(10,2)[SB1] 1.00", "SELECT n2(1)");
        assertAnswer("NUMBER(10,2)[SB2] 1.50", "SELECT n2(1.5::NUMBER(4,1))");
        assertAnswer("NUMBER(12,3)[SB4] 123.456", "SELECT n2(123.456::NUMBER(12,3))");
        assertAnswer("NUMBER(20,0)[SB16]", "SELECT n0(12345678901234567890)");
        assertAnswer("NUMBER(38,10)[SB8]", "SELECT n0(5::NUMBER(38,10))");
        assertAnswer("BINARY(1)[LOB]", "SELECT * FROM TABLE(tb(X'AB'))");
        assertAnswer("BINARY[LOB]", "SELECT * FROM TABLE(tb(TO_BINARY('AB', 'HEX')))");
        assertAnswer("BINARY(2)[LOB]", "SELECT * FROM TABLE(tb(X'ABCD'))");
        assertAnswer("BINARY(1)[LOB]", "SELECT bs(X'AB')");
        assertAnswer("BINARY[LOB]", "SELECT bs(TO_BINARY('ABCD', 'HEX'))");
    }
}
