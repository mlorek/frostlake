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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MathFunctionsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        // Create test table with numeric data
        engine.execute("CREATE TABLE test_numbers (id INTEGER, value NUMBER)");
        engine.execute("INSERT INTO test_numbers VALUES (1, 10.5)");
        engine.execute("INSERT INTO test_numbers VALUES (2, -5.7)");
        engine.execute("INSERT INTO test_numbers VALUES (3, 0)");
        engine.execute("INSERT INTO test_numbers VALUES (4, 100)");
        engine.execute("INSERT INTO test_numbers VALUES (5, 2.71828)"); // approximately e
    }

    // SIGN Tests

    @Test
    public void testSignPositive() {
        ResultSet result = engine.executeQuery(
            "SELECT SIGN(value) as sign_val FROM test_numbers WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        assertEquals(1, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("sign_val"))).intValue());
    }

    @Test
    public void testSignNegative() {
        ResultSet result = engine.executeQuery(
            "SELECT SIGN(value) as sign_val FROM test_numbers WHERE id = 2"
        );
        assertEquals(1, result.getRowCount());
        assertEquals(-1, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("sign_val"))).intValue());
    }

    @Test
    public void testSignZero() {
        ResultSet result = engine.executeQuery(
            "SELECT SIGN(value) as sign_val FROM test_numbers WHERE id = 3"
        );
        assertEquals(1, result.getRowCount());
        assertEquals(0, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("sign_val"))).intValue());
    }

    // TRUNC Tests

    @Test
    public void testTruncNoScale() {
        ResultSet result = engine.executeQuery(
            "SELECT TRUNC(value) as truncated FROM test_numbers WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        // TRUNC(10.5) = 10
        assertEquals(10, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("truncated"))).intValue());
    }

    @Test
    public void testTruncNegativeNoScale() {
        ResultSet result = engine.executeQuery(
            "SELECT TRUNC(value) as truncated FROM test_numbers WHERE id = 2"
        );
        assertEquals(1, result.getRowCount());
        // TRUNC(-5.7) = -5
        assertEquals(-5, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("truncated"))).intValue());
    }

    @Test
    public void testTruncWithScale() {
        ResultSet result = engine.executeQuery(
            "SELECT TRUNC(10.56789, 2) as truncated FROM test_numbers WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        // TRUNC(10.56789, 2) = 10.56
        assertEquals(10.56, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("truncated"))).doubleValue(), 0.001);
    }

    @Test
    public void testTruncZeroScale() {
        ResultSet result = engine.executeQuery(
            "SELECT TRUNC(value, 0) as truncated FROM test_numbers WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        // TRUNC(10.5, 0) = 10
        assertEquals(10, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("truncated"))).intValue());
    }

    // EXP Tests

    @Test
    public void testExpZero() {
        ResultSet result = engine.executeQuery(
            "SELECT EXP(0) as exp_val FROM test_numbers WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        // EXP(0) = 1
        assertEquals(1.0, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("exp_val"))).doubleValue(), 0.0001);
    }

    @Test
    public void testExpOne() {
        ResultSet result = engine.executeQuery(
            "SELECT EXP(1) as exp_val FROM test_numbers WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        // EXP(1) = e ≈ 2.71828
        assertEquals(Math.E, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("exp_val"))).doubleValue(), 0.0001);
    }

    @Test
    public void testExpTwo() {
        ResultSet result = engine.executeQuery(
            "SELECT EXP(2) as exp_val FROM test_numbers WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        // EXP(2) = e^2 ≈ 7.389
        assertEquals(Math.E * Math.E, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("exp_val"))).doubleValue(), 0.001);
    }

    // LN Tests

    @Test
    public void testLnOne() {
        ResultSet result = engine.executeQuery(
            "SELECT LN(1) as ln_val FROM test_numbers WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        // LN(1) = 0
        assertEquals(0.0, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("ln_val"))).doubleValue(), 0.0001);
    }

    @Test
    public void testLnE() {
        ResultSet result = engine.executeQuery(
            "SELECT LN(value) as ln_val FROM test_numbers WHERE id = 5"
        );
        assertEquals(1, result.getRowCount());
        // LN(e) ≈ 1
        assertEquals(1.0, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("ln_val"))).doubleValue(), 0.001);
    }

    @Test
    public void testLnPositive() {
        ResultSet result = engine.executeQuery(
            "SELECT LN(value) as ln_val FROM test_numbers WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        // LN(10.5) ≈ 2.35
        double expected = Math.log(10.5);
        assertEquals(expected, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("ln_val"))).doubleValue(), 0.0001);
    }

    @Test
    public void testLnNegativeThrowsError() {
        assertThrows(RuntimeException.class, () -> {
            engine.executeQuery("SELECT LN(value) as ln_val FROM test_numbers WHERE id = 2");
        });
    }

    @Test
    public void testLnZeroThrowsError() {
        assertThrows(RuntimeException.class, () -> {
            engine.executeQuery("SELECT LN(value) as ln_val FROM test_numbers WHERE id = 3");
        });
    }

    // LOG Tests

    @Test
    public void testLogBase10() {
        ResultSet result = engine.executeQuery(
            "SELECT LOG(value) as log_val FROM test_numbers WHERE id = 4"
        );
        assertEquals(1, result.getRowCount());
        // LOG(100) = 2 (base 10)
        assertEquals(2.0, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("log_val"))).doubleValue(), 0.0001);
    }

    @Test
    public void testLogBase10Ten() {
        ResultSet result = engine.executeQuery(
            "SELECT LOG(10) as log_val FROM test_numbers WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        // LOG(10) = 1 (base 10)
        assertEquals(1.0, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("log_val"))).doubleValue(), 0.0001);
    }

    @Test
    public void testLogCustomBase() {
        ResultSet result = engine.executeQuery(
            "SELECT LOG(2, 8) as log_val FROM test_numbers WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        // LOG(2, 8) = 3 (log base 2 of 8)
        assertEquals(3.0, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("log_val"))).doubleValue(), 0.0001);
    }

    @Test
    public void testLogCustomBase10() {
        ResultSet result = engine.executeQuery(
            "SELECT LOG(10, 1000) as log_val FROM test_numbers WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        // LOG(10, 1000) = 3 (log base 10 of 1000)
        assertEquals(3.0, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("log_val"))).doubleValue(), 0.0001);
    }

    // Combined Tests

    @Test
    public void testExpLnInverse() {
        ResultSet result = engine.executeQuery(
            "SELECT EXP(LN(value)) as result FROM test_numbers WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        // EXP(LN(x)) should equal x
        assertEquals(10.5, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("result"))).doubleValue(), 0.0001);
    }

    @Test
    public void testSignAbsCombination() {
        ResultSet result = engine.executeQuery(
            "SELECT SIGN(value) * ABS(value) as result FROM test_numbers WHERE id = 2"
        );
        assertEquals(1, result.getRowCount());
        // SIGN(-5.7) * ABS(-5.7) = -1 * 5.7 = -5.7
        assertEquals(-5.7, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("result"))).doubleValue(), 0.0001);
    }

    @Test
    public void testTruncVsRound() {
        ResultSet result = engine.executeQuery(
            "SELECT TRUNC(10.9) as trunc_val, ROUND(10.9) as round_val FROM test_numbers WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        // TRUNC(10.9) = 10, ROUND(10.9) = 11
        assertEquals(10, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("trunc_val"))).intValue());
        assertEquals(11, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("round_val"))).intValue());
    }
}
