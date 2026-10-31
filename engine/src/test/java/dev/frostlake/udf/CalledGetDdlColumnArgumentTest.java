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
 * A called SQL function whose body hands a parameter to GET_DDL: a call argument that reads a column is refused
 * while the statement compiles, echoed from the plan in the place GET_DDL reads it, under SYSTEM$TYPEOF too; a
 * constant argument names the object it looks up (all live-verified).
 */
public class CalledGetDdlColumnArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void calledGetDdl() {
        engine.execute("CREATE OR REPLACE TABLE gtt2096 (x INT, k VARCHAR)");
        engine.execute("INSERT INTO gtt2096 VALUES (1, 'table')");
        engine.execute("CREATE OR REPLACE FUNCTION gdp2096(t VARCHAR) RETURNS VARCHAR AS $$ GET_DDL('table', t) $$");
        engine.execute("CREATE OR REPLACE FUNCTION gdq2096(k VARCHAR, t VARCHAR) RETURNS VARCHAR AS $$ GET_DDL(k, t) "
            + "$$");
        engine.execute("CREATE OR REPLACE FUNCTION gdr2096(t VARCHAR) RETURNS VARCHAR AS $$ GET_DDL(t, 'gtt2096') $$");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [CAST(GTT2096.X AS VARCHAR(134217728))] for "
                + "function '2', parameter EXPORT_DDL: constant arguments expected",
            "SELECT SYSTEM$TYPEOF(gdp2096(x::VARCHAR)) FROM gtt2096");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [CAST(GTT2096.X AS VARCHAR(134217728))] for "
                + "function '2', parameter EXPORT_DDL: constant arguments expected",
            "SELECT gdp2096(x::VARCHAR) FROM gtt2096");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [CAST(GTT2096.X AS VARCHAR(134217728))] for "
                + "function '2', parameter EXPORT_DDL: constant arguments expected",
            "SELECT gdq2096('table', x::VARCHAR) FROM gtt2096");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [GTT2096.K] for function '1', parameter "
                + "EXPORT_DDL: constant arguments expected",
            "SELECT gdq2096(k, 'gtt2096') FROM gtt2096");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [GTT2096.K] for function '1', parameter "
                + "EXPORT_DDL: constant arguments expected",
            "SELECT gdr2096(k) FROM gtt2096");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [CAST(GTT2096.X AS VARCHAR(134217728))] for "
                + "function '2', parameter EXPORT_DDL: constant arguments expected",
            "SELECT GET_DDL('table', x::VARCHAR) FROM gtt2096");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [GTT2096.K] for function '1', parameter "
                + "EXPORT_DDL: constant arguments expected",
            "SELECT GET_DDL(k, 'gtt2096') FROM gtt2096");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [GTT2096.K] for function '2', parameter "
                + "EXPORT_DDL: constant arguments expected",
            "SELECT gdp2096(k) FROM gtt2096");
        assertAnswer("true", "SELECT LENGTH(gdp2096('gtt2096')) > 0");
    }
}
