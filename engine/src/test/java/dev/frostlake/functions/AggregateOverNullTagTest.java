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
 * SYSTEM$TYPEOF of an aggregate a FROM-less select computes, at least as wide as its accumulator - SUM SB8 and AVG
 * SB16 over NULL or a constant, where over a table the values decide - and LAST_QUERY_ID's echo of DUAL's column,
 * printed as $1 with a conversion to a number as SYSTEM$NULL_TO_FIXED or SYSTEM$NULL_TO_REAL (all live-verified).
 */
public class AggregateOverNullTagTest extends MeasuredCellsTestSupport {

    @Test
    public void aggregateTags() {
        engine.execute("CREATE OR REPLACE TABLE e2051 (i INT, d NUMBER(10,2), f FLOAT)");
        engine.execute("CREATE OR REPLACE TABLE v2051 (i INT, d NUMBER(10,2), f FLOAT)");
        engine.execute("INSERT INTO v2051 VALUES (1, 1.5, 1.5)");
        assertAnswer("NUMBER(30,0)[SB8]|NULL[LOB]|NUMBER(36,6)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(NULL)), SYSTEM$TYPEOF(MAX(NULL)), SYSTEM$TYPEOF(AVG(NULL))");
        assertAnswer("NUMBER(38,0)[SB8]|NUMBER(38,6)[SB16]|NUMBER(22,2)[SB8]|NUMBER(28,8)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(NULL::INT)), SYSTEM$TYPEOF(AVG(NULL::INT)), SYSTEM$TYPEOF(SUM(NULL::NUMBER(10,"
                + "2))), SYSTEM$TYPEOF(AVG(NULL::NUMBER(10,2)))");
        assertAnswer("FLOAT[DOUBLE]|FLOAT[DOUBLE]|NUMBER(38,0)[SB1]|NUMBER(10,2)[SB1]",
            "SELECT SYSTEM$TYPEOF(SUM(NULL::FLOAT)), SYSTEM$TYPEOF(AVG(NULL::FLOAT)), SYSTEM$TYPEOF(MIN(NULL::INT)), "
                + "SYSTEM$TYPEOF(MAX(NULL::NUMBER(10,2)))");
        assertAnswer("NUMBER(38,0)[SB1]|NUMBER(38,6)[SB1]|NUMBER(22,2)[SB1]|NUMBER(28,8)[SB1]|FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(SUM(i)), SYSTEM$TYPEOF(AVG(i)), SYSTEM$TYPEOF(SUM(d)), SYSTEM$TYPEOF(AVG(d)), "
                + "SYSTEM$TYPEOF(SUM(f)) FROM e2051");
        assertAnswer("NUMBER(38,0)[SB8]|NUMBER(38,6)[SB8]|NUMBER(22,2)[SB8]|NUMBER(28,8)[SB16]|FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(SUM(i)), SYSTEM$TYPEOF(AVG(i)), SYSTEM$TYPEOF(SUM(d)), SYSTEM$TYPEOF(AVG(d)), "
                + "SYSTEM$TYPEOF(SUM(f)) FROM v2051");
        assertAnswer("NUMBER(12,3)[SB1]|NUMBER(38,6)[SB16]|FLOAT[DOUBLE]|NULL[LOB]",
            "SELECT SYSTEM$TYPEOF(MEDIAN(NULL)), SYSTEM$TYPEOF(VARIANCE(NULL)), SYSTEM$TYPEOF(STDDEV(NULL)), "
                + "SYSTEM$TYPEOF(MIN(NULL))");
        assertAnswer("NUMBER(30,0)[SB1]|NUMBER(36,6)[SB1]",
            "SELECT SYSTEM$TYPEOF(SUM(NULL)), SYSTEM$TYPEOF(AVG(NULL)) FROM e2051");
        assertAnswer("NUMBER(30,0)[SB1]|NUMBER(36,6)[SB1]",
            "SELECT SYSTEM$TYPEOF(SUM(NULL)), SYSTEM$TYPEOF(AVG(NULL)) FROM v2051");
        assertAnswer("NUMBER(18,0)[SB8]|NUMBER(13,0)[SB8]|NUMBER(19,6)[SB16]",
            "SELECT SYSTEM$TYPEOF(COUNT(NULL)), SYSTEM$TYPEOF(SUM(1)), SYSTEM$TYPEOF(AVG(1))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found 'SYSTEM$NULL_TO_FIXED(DUAL.$1)'",
            "SELECT LAST_QUERY_ID(TO_NUMBER($1))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found 'SYSTEM$NULL_TO_FIXED(VALUES.$1)'",
            "SELECT LAST_QUERY_ID(TO_NUMBER($1)) FROM DUAL");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found 'SYSTEM$NULL_TO_REAL(DUAL.$1)'",
            "SELECT LAST_QUERY_ID(TO_DOUBLE($1))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found 'DUAL.$1'",
            "SELECT LAST_QUERY_ID($1)");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found '(SYSTEM$NULL_TO_FIXED(DUAL.$1)) + 1'",
            "SELECT LAST_QUERY_ID(TO_NUMBER($1) + 1)");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found 'SYSTEM$NULL_TO_FIXED(DUAL.$1)'",
            "SELECT LAST_QUERY_ID($1::INT)");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found 'SYSTEM$NULL_TO_FIXED(DUAL.$1)'",
            "SELECT LAST_QUERY_ID(TO_NUMBER($1, 10, 2))");
    }
}
