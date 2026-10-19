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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An approximate numeric is a FLOAT by family, whatever it is called. A value typed through the DOUBLE spelling —
 * a {@code ::DOUBLE} cast, SQRT, LN, TO_DOUBLE — was aggregated as an exact NUMBER(38,9) or NUMBER(38,12),
 * because the aggregate rules tested the name FLOAT. {@code x::FLOAT} and {@code x::DOUBLE} then disagreed, and a
 * CTAS over the aggregate stored 1.2345e-11 as zero.
 */
public class DoubleAggregatesStayApproximateTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE f (x NUMBER(20,15))");
        engine.execute("INSERT INTO f VALUES (0.000000000012345)");
    }

    private String typeOf(final String expression) {
        return String.valueOf(engine.executeQuery("SELECT SYSTEM$TYPEOF(" + expression + ") FROM f")
            .getRows().get(0).getValue(0));
    }

    @Test
    public void anAggregateOverADoubleNamedValueIsAFloat() {
        final String[] aggregates = {"SUM(x::FLOAT)", "SUM(x::DOUBLE)", "SUM(x::DOUBLE PRECISION)", "SUM(SQRT(x))",
            "SUM(LN(x))", "AVG(TO_DOUBLE(x))", "MEDIAN(x::DOUBLE)", "VARIANCE(x::DOUBLE)",
            "PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY x::DOUBLE)", "AVG(x::DOUBLE) OVER ()"};
        for (final String aggregate : aggregates) {
            assertEquals("FLOAT[DOUBLE]", typeOf(aggregate), aggregate);
        }
        assertEquals("FLOAT[DOUBLE]", typeOf("x::DOUBLE"), "the cast itself");
    }

    @Test
    public void aCtasOverOneKeepsTheSmallValue() {
        engine.execute("CREATE TABLE g AS SELECT SUM(x::DOUBLE) b, AVG(TO_DOUBLE(x)) c FROM f");
        assertEquals("FLOAT", describeCell("g", "B", "type"));
        assertEquals("FLOAT", describeCell("g", "C", "type"));
        final Object b = engine.executeQuery("SELECT b FROM g").getRows().get(0).getValue(0);
        assertEquals(1.2345e-11, ((Number) b).doubleValue(), 1e-24, "not truncated to the exact scale");
    }
}
