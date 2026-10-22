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

package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The type of a SQL function's answer when its argument is an untyped NULL: a parameter declared without a
 * length keeps none, a sized text parameter's NULL is the unbounded width, a sized binary keeps its width; a
 * typed NULL and a value keep theirs (all live-verified).
 */
public class SqlFunctionNullArgumentTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void nullArgumentTypes() {
        engine.execute("CREATE OR REPLACE FUNCTION wb(x BINARY) RETURNS BINARY AS 'x'");
        engine.execute("CREATE OR REPLACE FUNCTION wv(x VARCHAR) RETURNS VARCHAR AS 'x'");
        engine.execute("CREATE OR REPLACE FUNCTION wv5(x VARCHAR(5)) RETURNS VARCHAR AS 'x'");
        engine.execute("CREATE OR REPLACE FUNCTION wb2(x BINARY) RETURNS BINARY(2) AS 'x'");
        engine.execute("CREATE OR REPLACE FUNCTION wb4(x BINARY(4)) RETURNS BINARY AS 'x'");
        engine.execute("CREATE OR REPLACE FUNCTION ws(x STRING) RETURNS STRING AS 'x'");
        engine.execute("CREATE OR REPLACE FUNCTION wt(x TEXT) RETURNS TEXT AS 'x'");
        engine.execute("CREATE OR REPLACE FUNCTION wc(x CHAR) RETURNS CHAR AS 'x'");
        engine.execute("CREATE OR REPLACE FUNCTION wvb(x VARBINARY) RETURNS VARBINARY AS 'x'");
        engine.execute("CREATE OR REPLACE FUNCTION wn(x NUMBER) RETURNS NUMBER AS 'x'");
        assertAnswer("BINARY[LOB]", "SELECT SYSTEM$TYPEOF(wb(NULL))");
        assertAnswer("VARCHAR[LOB]", "SELECT SYSTEM$TYPEOF(wv(NULL))");
        assertAnswer("VARCHAR(134217728)[LOB]", "SELECT SYSTEM$TYPEOF(wv5(NULL))");
        assertAnswer("BINARY[LOB]", "SELECT SYSTEM$TYPEOF(wb2(NULL))");
        assertAnswer("BINARY(4)[LOB]", "SELECT SYSTEM$TYPEOF(wb4(NULL))");
        assertAnswer("VARCHAR[LOB]", "SELECT SYSTEM$TYPEOF(ws(NULL))");
        assertAnswer("VARCHAR[LOB]", "SELECT SYSTEM$TYPEOF(wt(NULL))");
        assertAnswer("VARCHAR(134217728)[LOB]", "SELECT SYSTEM$TYPEOF(wc(NULL))");
        assertAnswer("BINARY[LOB]", "SELECT SYSTEM$TYPEOF(wvb(NULL))");
        assertAnswer("NUMBER(38,0)[SB1]", "SELECT SYSTEM$TYPEOF(wn(NULL))");
        assertAnswer("VARCHAR(3)[LOB]", "SELECT SYSTEM$TYPEOF(wv('abc'))");
        assertAnswer("BINARY[LOB]", "SELECT SYSTEM$TYPEOF(wb(TO_BINARY('AB', 'HEX')))");
        assertAnswer("VARCHAR(3)[LOB]", "SELECT SYSTEM$TYPEOF(wv5('abc'))");
        assertAnswer("VARCHAR(3)[LOB]", "SELECT SYSTEM$TYPEOF(wv(NULL::VARCHAR(3)))");
    }
}
