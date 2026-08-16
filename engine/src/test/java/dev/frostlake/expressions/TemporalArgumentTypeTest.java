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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Temporal arithmetic is far narrower than it looks: a DATE shifts by a number and subtracts from
 * another DATE, and TIMESTAMPs subtract from each other — everything else in the family is REFUSED,
 * with both types spelled in the order written:
 * {@code Invalid argument types for function '-': (TIMESTAMP_NTZ(9), DATE)}.
 *
 * <p>All live-measured. What makes the boundary worth pinning is how close the legal and illegal
 * shapes sit: a DATE shifted by a number is fine while a TIMESTAMP shifted by one is not, and a DATE
 * difference is fine while a TIME difference is not.
 */
public class TemporalArgumentTypeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE tr_s (d DATE, d2 DATE, t TIME, t2 TIME,"
            + " s TIMESTAMP_NTZ, s2 TIMESTAMP_NTZ, l TIMESTAMP_LTZ)");
    }

    private void assertRefused(final String expression, final String types) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT " + expression + " AS c FROM tr_s");
            }
        }, expression);
        assertTrue(ex.getMessage().contains("Invalid argument types for function " + types),
            expression + " -> " + ex.getMessage());
    }

    private void assertAccepted(final String expression) {
        engine.executeQuery("SELECT " + expression + " AS c FROM tr_s");
    }

    /** A TIMESTAMP cannot be shifted by a number, though a DATE can. */
    @Test
    public void aTimestampDoesNotShiftByANumber() {
        assertRefused("s + 1", "'+': (TIMESTAMP_NTZ(9), NUMBER(1,0))");
        assertRefused("s - 1", "'-': (TIMESTAMP_NTZ(9), NUMBER(1,0))");
        assertRefused("l + 1", "'+': (TIMESTAMP_LTZ(9), NUMBER(1,0))");
        assertAccepted("d + 1");
    }

    /** Nor can a TIME. */
    @Test
    public void aTimeDoesNotShiftByANumber() {
        assertRefused("t + 1", "'+': (TIME(9), NUMBER(1,0))");
        assertRefused("t - 1", "'-': (TIME(9), NUMBER(1,0))");
    }

    /** A TIME pairs with no other TIME, under either operator — unlike a DATE, which subtracts. */
    @Test
    public void twoTimesDoNotCombine() {
        assertRefused("t - t2", "'-': (TIME(9), TIME(9))");
        assertRefused("t + t2", "'+': (TIME(9), TIME(9))");
        assertAccepted("d - d2");
    }

    /** A TIMESTAMP and a DATE do not subtract in either order, though two TIMESTAMPs do. */
    @Test
    public void aTimestampAndADateDoNotSubtract() {
        assertRefused("s - d", "'-': (TIMESTAMP_NTZ(9), DATE)");
        assertRefused("d - s", "'-': (DATE, TIMESTAMP_NTZ(9))");
        assertRefused("l - d", "'-': (TIMESTAMP_LTZ(9), DATE)");
        assertAccepted("s - s2");
    }

    /** The refusal is a COMPILATION error, so it lands over a table with no rows. */
    @Test
    public void theRefusalIsAtCompileTime() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT s + 1 AS c FROM tr_s");
            }
        });
        assertTrue(ex.getMessage().startsWith("SQL compilation error:"), ex.getMessage());
    }
}
