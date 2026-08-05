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

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * AVG result typing, live-verified against Snowflake: fixed-point inputs (INTEGER / NUMBER(p,s))
 * yield a NUMBER whose scale is (max input scale) + 6, rounded HALF_UP with trailing zeros KEPT
 * (AVG of 90 and 95 is exactly 92.500000); any FLOAT input keeps the double average; an empty
 * input is NULL.
 */
public class AvgTypingTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void integerInputsAverageToScaleSixDecimal() {
        engine.execute("CREATE TABLE ints (v INTEGER)");
        engine.execute("INSERT INTO ints VALUES (90), (95)");
        // Live: AVG of the integers 90 and 95 is exactly 92.500000 (scale 6).
        assertEquals("92.500000", String.valueOf(scalar("SELECT AVG(v) FROM ints")));
    }

    @Test
    public void trailingZerosAreKept() {
        engine.execute("CREATE TABLE twos (v INTEGER)");
        engine.execute("INSERT INTO twos VALUES (2), (2)");
        // Live: AVG(2, 2) renders 2.000000 — the scale-6 zeros are NOT stripped.
        assertEquals("2.000000", String.valueOf(scalar("SELECT AVG(v) FROM twos")));
    }

    @Test
    public void scaledDecimalInputsAddSixToTheirScale() {
        engine.execute("CREATE TABLE scaled (v NUMBER(5,1))");
        engine.execute("INSERT INTO scaled VALUES (12.3), (45.6)");
        // Live: AVG over NUMBER(5,1) values has scale 1 + 6 = 7.
        assertEquals("28.9500000", String.valueOf(scalar("SELECT AVG(v) FROM scaled")));
    }

    @Test
    public void floatInputsKeepTheDoubleAverage() {
        engine.execute("CREATE TABLE dbls (v DOUBLE)");
        // ::DOUBLE produces real runtime doubles (a bare 1.5 literal is stored as a fixed-point decimal).
        engine.execute("INSERT INTO dbls SELECT 1.5::DOUBLE UNION ALL SELECT 2.5::DOUBLE");
        final Object result = scalar("SELECT AVG(v) FROM dbls");
        assertInstanceOf(Double.class, result, "FLOAT input keeps AVG on the double path");
        assertEquals(2.0, result);
    }

    @Test
    public void emptyInputIsNull() {
        engine.execute("CREATE TABLE none (v INTEGER)");
        assertNull(scalar("SELECT AVG(v) FROM none"));
    }
}
