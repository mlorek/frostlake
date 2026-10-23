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
 * A DECFLOAT literal is echoed as written, its word in lower case - [ARRAY_CONSTRUCT(decfloat '1')] - and a
 * PERCENTILE_CONT or PERCENTILE_DISC fraction that is no constant is echoed through the cast the plan gives it: a
 * FLOAT or a DECFLOAT as CAST(... AS NUMBER(18,0)), an exact NUMBER cast as written, a numeric text as
 * TO_NUMBER('0.1', 18, 1) (all live-verified).
 */
public class PercentileFractionEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void fractionEchoes() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("Table ST50 successfully created.", "CREATE OR REPLACE TABLE st50 (ar ARRAY(DECFLOAT))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [ARRAY_CONSTRUCT(decfloat '1')] in VALUES "
                + "clause",
            "INSERT INTO st50 VALUES (ARRAY_CONSTRUCT(DECFLOAT '1'))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [ARRAY_CONSTRUCT(decfloat '-1.5e300')] in "
                + "VALUES clause",
            "INSERT INTO st50 VALUES (ARRAY_CONSTRUCT(DECFLOAT '-1.5e300'))");
        assertAnswer("REFUSED SQL compilation error: | argument 0 to function PERCENTILE_CONT needs to be constant, "
                + "found 'CAST(CAST(0.1 AS FLOAT) AS NUMBER(18,0))'",
            "SELECT PERCENTILE_CONT(0.1::FLOAT) WITHIN GROUP (ORDER BY x) FROM (SELECT DECFLOAT '0' x UNION ALL "
                + "SELECT DECFLOAT '1')");
        assertAnswer("REFUSED SQL compilation error: | argument 0 to function PERCENTILE_CONT needs to be constant, "
                + "found 'CAST(decfloat '0.1' AS NUMBER(18,0))'",
            "SELECT PERCENTILE_CONT(DECFLOAT '0.1') WITHIN GROUP (ORDER BY x) FROM (SELECT DECFLOAT '0' x UNION ALL "
                + "SELECT DECFLOAT '1')");
        assertAnswer("REFUSED SQL compilation error: | argument 0 to function PERCENTILE_CONT needs to be constant, "
                + "found 'CAST(0.1 AS NUMBER(3,2))'",
            "SELECT PERCENTILE_CONT(0.1::NUMBER(3,2)) WITHIN GROUP (ORDER BY x) FROM (SELECT 0 x UNION ALL SELECT 1)");
        assertAnswer("REFUSED SQL compilation error: | argument 0 to function PERCENTILE_CONT needs to be constant, "
                + "found 'TO_NUMBER('0.1', 18, 1)'",
            "SELECT PERCENTILE_CONT('0.1') WITHIN GROUP (ORDER BY x) FROM (SELECT 0 x UNION ALL SELECT 1)");
        assertAnswer("REFUSED SQL compilation error: | argument 0 to function PERCENTILE_DISC needs to be constant, "
                + "found 'CAST(CAST(0.1 AS FLOAT) AS NUMBER(18,0))'",
            "SELECT PERCENTILE_DISC(0.1::FLOAT) WITHIN GROUP (ORDER BY x) FROM (SELECT 0 x UNION ALL SELECT 1)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [ARRAY_CONSTRUCT(decfloat '1', 2)] in "
                + "VALUES clause",
            "INSERT INTO st50 VALUES (ARRAY_CONSTRUCT(DECFLOAT '1', 2))");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
