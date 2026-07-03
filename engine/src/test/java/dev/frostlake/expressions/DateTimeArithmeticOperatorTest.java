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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code +} / {@code -} operators on DATE and TIMESTAMP values (previously they threw "Cannot
 * add/subtract"): {@code DATE ± int} shifts by days and stays a DATE, {@code TIMESTAMP ± int} shifts by
 * days preserving the time, {@code DATE − DATE} yields the integer day count, and {@code ± INTERVAL}
 * applies the unit — a time-component interval promoting a DATE to a TIMESTAMP. Non-date operands still
 * fail, so ordinary string/number arithmetic is unaffected.
 */
public class DateTimeArithmeticOperatorTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (d DATE, ts TIMESTAMP, d2 DATE)");
        engine.execute("INSERT INTO t VALUES ('2020-01-15', '2020-01-15 10:30:00', '2020-01-10')");
    }

    private Object scalar(final String query) {
        final ResultSet rs = engine.executeQuery(query);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void datePlusIntegerAddsDaysStayingADate() {
        assertEquals("2020-01-20", scalar("SELECT d + 5 FROM t").toString());
    }

    @Test
    public void dateMinusIntegerSubtractsDays() {
        assertEquals("2020-01-08", scalar("SELECT d - 7 FROM t").toString());
    }

    @Test
    public void integerPlusDateIsCommutative() {
        assertEquals("2020-01-20", scalar("SELECT 5 + d FROM t").toString());
    }

    @Test
    public void dateMinusDateIsIntegerDays() {
        assertEquals(5L, ((Number) scalar("SELECT d - d2 FROM t")).longValue());
        assertEquals(-5L, ((Number) scalar("SELECT d2 - d FROM t")).longValue());
    }

    @Test
    public void timestampPlusIntegerAddsDaysPreservingTime() {
        // Still a TIMESTAMP (time preserved) — rendered with a 'T' separator.
        final String result = scalar("SELECT ts + 1 FROM t").toString();
        assertTrue(result.startsWith("2020-01-16"), result);
        assertTrue(result.contains("10:30"), result);
    }

    @Test
    public void datePlusDateOnlyIntervalStaysADate() {
        assertEquals("2020-01-20", scalar("SELECT d + INTERVAL '5' DAY FROM t").toString());
        assertEquals("2020-02-15", scalar("SELECT d + INTERVAL '1' MONTH FROM t").toString());
        assertEquals("2021-01-15", scalar("SELECT d + INTERVAL '1' YEAR FROM t").toString());
    }

    @Test
    public void datePlusTimeIntervalPromotesToTimestamp() {
        final String result = scalar("SELECT d + INTERVAL '2' HOUR FROM t").toString();
        assertTrue(result.contains("T02:00") || result.contains("02:00"), result);
    }

    @Test
    public void timestampPlusAndMinusIntervalAppliesTheUnit() {
        assertTrue(scalar("SELECT ts + INTERVAL '2' HOUR FROM t").toString().contains("12:30"));
        assertTrue(scalar("SELECT ts - INTERVAL '30' MINUTE FROM t").toString().contains("10:00"));
    }

    @Test
    public void currentDateArithmeticWorksInWhere() {
        // A very old cutoff keeps the row — exercises DATE-column vs CURRENT_DATE-minus-int comparison.
        final long count = ((Number) scalar("SELECT COUNT(*) FROM t WHERE d > CURRENT_DATE - 100000")).longValue();
        assertEquals(1L, count);
    }

    @Test
    public void nullTemporalArithmeticYieldsNull() {
        engine.execute("INSERT INTO t (d) VALUES (NULL)");
        assertNull(scalar("SELECT d + 5 FROM t WHERE d IS NULL"));
    }

    @Test
    public void nonDateStringArithmeticStillErrors() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT 'hello' + 5");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT 'hello' - 'world'");
            }
        });
    }

    @Test
    public void plainNumericArithmeticIsUnaffected() {
        assertEquals(15L, ((Number) scalar("SELECT 10 + 5")).longValue());
        assertEquals(3L, ((Number) scalar("SELECT 10 - 7")).longValue());
    }
}
