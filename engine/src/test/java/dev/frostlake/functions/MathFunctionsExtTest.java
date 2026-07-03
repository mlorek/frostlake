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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MathFunctionsExtTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    private double qd(final String sql) {
        ResultSet rs = engine.executeQuery(sql);
        return ((Number) rs.getRows().get(0).getValue(0)).doubleValue();
    }

    private long ql(final String sql) {
        ResultSet rs = engine.executeQuery(sql);
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    // PI
    @Test public void testPi() {
        assertEquals(Math.PI, qd("SELECT PI()"), 1e-10);
    }

    // CBRT
    @Test public void testCbrt() {
        assertEquals(2.0, qd("SELECT CBRT(8)"), 1e-10);
        assertEquals(-2.0, qd("SELECT CBRT(-8)"), 1e-10);
    }

    // SQUARE
    @Test public void testSquare() {
        assertEquals(9.0, qd("SELECT SQUARE(3)"), 1e-10);
        assertEquals(0.0, qd("SELECT SQUARE(0)"), 1e-10);
    }

    // FACTORIAL
    @Test public void testFactorial() {
        assertEquals(1L, ql("SELECT FACTORIAL(0)"));
        assertEquals(1L, ql("SELECT FACTORIAL(1)"));
        assertEquals(120L, ql("SELECT FACTORIAL(5)"));
        assertEquals(3628800L, ql("SELECT FACTORIAL(10)"));
    }
    @Test public void testFactorialNegativeThrows() {
        assertThrows(RuntimeException.class, () -> engine.executeQuery("SELECT FACTORIAL(-1)"));
    }

    // Trig functions
    @Test public void testSin() {
        assertEquals(0.0, qd("SELECT SIN(0)"), 1e-10);
        assertEquals(1.0, qd("SELECT SIN(PI()/2)"), 1e-10);
    }
    @Test public void testCos() {
        assertEquals(1.0, qd("SELECT COS(0)"), 1e-10);
        assertEquals(-1.0, qd("SELECT COS(PI())"), 1e-10);
    }
    @Test public void testTan() {
        assertEquals(0.0, qd("SELECT TAN(0)"), 1e-10);
    }
    @Test public void testCot() {
        assertEquals(1.0 / Math.tan(1.0), qd("SELECT COT(1)"), 1e-10);
    }
    @Test public void testAsin() {
        assertEquals(Math.PI / 2, qd("SELECT ASIN(1)"), 1e-10);
        assertEquals(0.0, qd("SELECT ASIN(0)"), 1e-10);
    }
    @Test public void testAcos() {
        assertEquals(0.0, qd("SELECT ACOS(1)"), 1e-10);
        assertEquals(Math.PI, qd("SELECT ACOS(-1)"), 1e-10);
    }
    @Test public void testAtan() {
        assertEquals(0.0, qd("SELECT ATAN(0)"), 1e-10);
        assertEquals(Math.PI / 4, qd("SELECT ATAN(1)"), 1e-10);
    }
    @Test public void testAtan2() {
        assertEquals(Math.PI / 4, qd("SELECT ATAN2(1, 1)"), 1e-10);
        assertEquals(Math.PI / 2, qd("SELECT ATAN2(1, 0)"), 1e-10);
    }

    // Hyperbolic
    @Test public void testSinh() {
        assertEquals(0.0, qd("SELECT SINH(0)"), 1e-10);
        assertEquals(Math.sinh(1.0), qd("SELECT SINH(1)"), 1e-10);
    }
    @Test public void testCosh() {
        assertEquals(1.0, qd("SELECT COSH(0)"), 1e-10);
        assertEquals(Math.cosh(1.0), qd("SELECT COSH(1)"), 1e-10);
    }
    @Test public void testTanh() {
        assertEquals(0.0, qd("SELECT TANH(0)"), 1e-10);
        assertEquals(Math.tanh(1.0), qd("SELECT TANH(1)"), 1e-10);
    }

    // DEGREES / RADIANS
    @Test public void testDegrees() {
        assertEquals(180.0, qd("SELECT DEGREES(PI())"), 1e-10);
        assertEquals(0.0, qd("SELECT DEGREES(0)"), 1e-10);
    }
    @Test public void testRadians() {
        assertEquals(Math.PI, qd("SELECT RADIANS(180)"), 1e-10);
        assertEquals(0.0, qd("SELECT RADIANS(0)"), 1e-10);
    }

    // Bitwise
    @Test public void testBitand() {
        assertEquals(12L, ql("SELECT BITAND(14, 13)"));  // 1110 & 1101 = 1100 = 12
        assertEquals(0L,  ql("SELECT BITAND(5, 2)"));    // 101 & 010 = 000
    }
    @Test public void testBitor() {
        assertEquals(15L, ql("SELECT BITOR(14, 13)"));   // 1110 | 1101 = 1111 = 15
        assertEquals(7L,  ql("SELECT BITOR(5, 2)"));     // 101 | 010 = 111
    }
    @Test public void testBitxor() {
        assertEquals(3L, ql("SELECT BITXOR(14, 13)"));   // 1110 ^ 1101 = 0011 = 3
        assertEquals(7L, ql("SELECT BITXOR(5, 2)"));     // 101 ^ 010 = 111
    }
    @Test public void testBitnot() {
        assertEquals(~5L, ql("SELECT BITNOT(5)"));
        assertEquals(~0L, ql("SELECT BITNOT(0)"));
    }
    @Test public void testBitShiftLeft() {
        assertEquals(8L,  ql("SELECT BITSHIFTLEFT(1, 3)"));
        assertEquals(40L, ql("SELECT BITSHIFTLEFT(5, 3)"));
    }
    @Test public void testBitShiftRight() {
        assertEquals(1L, ql("SELECT BITSHIFTRIGHT(8, 3)"));
        assertEquals(2L, ql("SELECT BITSHIFTRIGHT(20, 3)"));
    }

    // WIDTH_BUCKET
    @Test public void testWidthBucketInRange() {
        assertEquals(3L, ql("SELECT WIDTH_BUCKET(5.35, 0.024, 10.06, 5)"));
    }
    @Test public void testWidthBucketBelowMin() {
        assertEquals(0L, ql("SELECT WIDTH_BUCKET(-1, 0, 10, 5)"));
    }
    @Test public void testWidthBucketAboveMax() {
        assertEquals(6L, ql("SELECT WIDTH_BUCKET(11, 0, 10, 5)"));
    }

    // HAVERSINE
    @Test public void testHaversine() {
        // NYC (40.7, -74.0) to London (51.5, -0.1) ≈ 5570 km
        double dist = qd("SELECT HAVERSINE(40.7, -74.0, 51.5, -0.1)");
        assertTrue(dist > 5000 && dist < 6000, "Expected ~5570 km, got " + dist);
    }
    @Test public void testHaversineSamePoint() {
        assertEquals(0.0, qd("SELECT HAVERSINE(10, 20, 10, 20)"), 1e-6);
    }

    // RANDOM / UNIFORM / NORMAL
    @Test public void testRandomReturnsSignedInteger() {
        // Snowflake RANDOM() is a signed 64-bit integer, not a fraction in [0, 1).
        Object r = engine.executeQuery("SELECT RANDOM()").getRows().get(0).getValue(0);
        assertTrue(r instanceof Long, "RANDOM() should return a 64-bit integer, got " + r);
    }
    @Test public void testUniformIntegerBoundsAreInclusive() {
        long r = ((Number) engine.executeQuery("SELECT UNIFORM(5, 10, RANDOM())").getRows().get(0).getValue(0)).longValue();
        assertTrue(r >= 5 && r <= 10, "UNIFORM(5,10) integer bounds are inclusive, got " + r);
    }
    @Test public void testNormalReturnsDouble() {
        // NORMAL() is random — just check it returns a number
        ResultSet rs = engine.executeQuery("SELECT NORMAL(0, 1)");
        assertNotNull(rs.getRows().get(0).getValue(0));
    }
}
