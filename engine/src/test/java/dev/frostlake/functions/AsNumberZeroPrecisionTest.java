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
 * AS_NUMBER and AS_DECIMAL with a precision of 0 are typed NUMBER(0,0), which holds any value (live-verified).
 */
public class AsNumberZeroPrecisionTest extends MeasuredCellsTestSupport {

    @Test
    public void zeroPrecision() {
        engine.execute("CREATE OR REPLACE TABLE tsr (a INT)");
        engine.execute("INSERT INTO tsr VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE STREAM ssr ON TABLE tsr");
        engine.execute("CREATE OR REPLACE STREAM sap ON TABLE tsr APPEND_ONLY = TRUE");
        engine.execute("CREATE OR REPLACE VIEW vsr AS SELECT a FROM tsr");
        engine.execute("CREATE OR REPLACE STREAM svs ON VIEW vsr");
        assertAnswer("NUMBER(0,0)[SB1]", "SELECT SYSTEM$TYPEOF(AS_NUMBER(TO_VARIANT(5), 0))");
        assertAnswer("5|123|5",
            "SELECT AS_NUMBER(TO_VARIANT(5), 0), AS_NUMBER(TO_VARIANT(123.4), 0), AS_DECIMAL(TO_VARIANT(5), 0)");
        assertShape("C:NUMBER(0,0), D:NUMBER(0,0)",
            "SELECT AS_NUMBER(TO_VARIANT(5), 0) AS c, AS_DECIMAL(TO_VARIANT(5), 0) AS d");
        assertAnswer("NUMBER(0,0)[SB1]|NUMBER(0,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(AS_DECIMAL(TO_VARIANT(5), 0)), SYSTEM$TYPEOF(AS_NUMBER(TO_VARIANT(5), 0, 0))");
        assertAnswer("NUMBER(3,0)[SB1]|NUMBER(3,1)[SB1]",
            "SELECT SYSTEM$TYPEOF(AS_NUMBER(TO_VARIANT(5), 3)), SYSTEM$TYPEOF(AS_NUMBER(TO_VARIANT(5), 3, 1))");
    }
}
