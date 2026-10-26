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
 * ROUND, TRUNC, CEIL and FLOOR over a FLOAT scale round in FLOAT arithmetic to the scale's own value and answer a
 * FLOAT, and ROUND takes no mode beside one (all live-verified).
 */
public class RoundFloatScaleTest extends MeasuredCellsTestSupport {

    @Test
    public void aFloatScaleRoundsInFloatArithmetic() {
        engine.execute("CREATE OR REPLACE TABLE rf (n NUMBER(10,4), f FLOAT)");
        engine.execute("INSERT INTO rf VALUES (1.2345, 2.0)");
        assertAnswer("1.23|FLOAT[DOUBLE]", "SELECT ROUND(1.2345, 2::FLOAT), SYSTEM$TYPEOF(ROUND(1.2345, 2::FLOAT))");
        answer("SELECT ROUND(1.2345, 2.5::FLOAT)");
        answer("SELECT ROUND(1.2389, 2.5::FLOAT), TRUNC(1.2389, 2.5::FLOAT)");
        assertAnswer("1.23|FLOAT[DOUBLE]", "SELECT TRUNC(1.2345, 2::FLOAT), SYSTEM$TYPEOF(TRUNC(1.2345, 2::FLOAT))");
        assertAnswer("1.23|NUMBER(6,2)[SB4]", "SELECT ROUND(1.2345, 2.0E0), SYSTEM$TYPEOF(ROUND(1.2345, 2.0E0))");
        assertAnswer("20.0|FLOAT[DOUBLE]", "SELECT ROUND(15, -1::FLOAT), SYSTEM$TYPEOF(ROUND(15, -1::FLOAT))");
        assertAnswer("3.0|-3.0|1.0", "SELECT ROUND(2.5, 0::FLOAT), ROUND(-2.5, 0::FLOAT), ROUND(0.5, 0::FLOAT)");
        assertAnswer("1.0|1.01", "SELECT ROUND(1.005, 2::FLOAT), ROUND(1.005, 2)");
        assertAnswer("null|FLOAT[DOUBLE]",
            "SELECT ROUND(1.2345, NULL::FLOAT), SYSTEM$TYPEOF(ROUND(1.2345, NULL::FLOAT))");
        assertAnswer("1.24|FLOAT[DOUBLE]", "SELECT CEIL(1.2345, 2::FLOAT), SYSTEM$TYPEOF(CEIL(1.2345, 2::FLOAT))");
        assertAnswer("1.23|FLOAT[DOUBLE]", "SELECT FLOOR(1.2345, 2::FLOAT), SYSTEM$TYPEOF(FLOOR(1.2345, 2::FLOAT))");
        assertAnswer("1.23", "SELECT TRUNCATE(1.2345, 2::FLOAT)");
        assertAnswer("1.23|FLOAT[DOUBLE]", "SELECT ROUND(n, 2::FLOAT), SYSTEM$TYPEOF(ROUND(n, 2::FLOAT)) FROM rf");
        assertAnswer("1.23|FLOAT[DOUBLE]", "SELECT ROUND(n, f), SYSTEM$TYPEOF(ROUND(n, f)) FROM rf");
        assertAnswer("1.23|FLOAT[DOUBLE]",
            "SELECT ROUND(1.2345::FLOAT, 2::FLOAT), SYSTEM$TYPEOF(ROUND(1.2345::FLOAT, 2::FLOAT))");
        answer("SELECT ROUND(12345, 2.7::FLOAT), ROUND(1.2345, -0.5::FLOAT)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ROUND] expected 2, got 3",
            "SELECT ROUND(1.2345, 2::FLOAT, 'HALF_TO_EVEN')");
        assertAnswer("1.23", "SELECT ROUND(1.2345, TO_DOUBLE('2'))");
        assertAnswer("1.2300|NUMBER(18,4)[SB8]",
            "SELECT ROUND(1.2345, 2::DECFLOAT), SYSTEM$TYPEOF(ROUND(1.2345, 2::DECFLOAT))");
        assertAnswer("1.0E300|NaN", "SELECT ROUND(1e300, -300::FLOAT), ROUND(123.456, 400::FLOAT)");
        assertAnswer("NaN|NaN", "SELECT ROUND(1.2345, 'NaN'::FLOAT), ROUND(1.2345, 'inf'::FLOAT)");
        assertAnswer("1.233288287", "SELECT TO_VARCHAR(ROUND(1.2345, 2.5::FLOAT))");
    }
}
