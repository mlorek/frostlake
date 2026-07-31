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

package dev.frostlake.scripting;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integer-range FOR loop — {@code FOR i IN [REVERSE] start TO end DO … END FOR} — executed in anonymous
 * BEGIN…END blocks. Covers ascending and REVERSE iteration order, variable bounds, and BREAK.
 */
public class RangeForLoopTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private int runReturningInt(final String block) {
        final ResultSet rs = engine.executeQuery(block);
        return ((Number) rs.getRows().get(0).getValue(0)).intValue();
    }

    @Test
    public void ascendingRangeSumsCounter() {
        assertEquals(15, runReturningInt("""
            BEGIN
                LET total INTEGER := 0;
                FOR i IN 1 TO 5 DO
                    total := total + i;
                END FOR;
                RETURN total;
            END;
            """), "1+2+3+4+5 = 15");
    }

    @Test
    public void ascendingRangeIteratesInOrder() {
        // Build the digits in iteration order: 1, 2, 3 → 123.
        assertEquals(123, runReturningInt("""
            BEGIN
                LET acc INTEGER := 0;
                FOR i IN 1 TO 3 DO
                    acc := acc * 10 + i;
                END FOR;
                RETURN acc;
            END;
            """));
    }

    @Test
    public void reverseRangeIteratesHighToLow() {
        // REVERSE 1 TO 3 visits 3, 2, 1 → 321.
        assertEquals(321, runReturningInt("""
            BEGIN
                LET acc INTEGER := 0;
                FOR i IN REVERSE 1 TO 3 DO
                    acc := acc * 10 + i;
                END FOR;
                RETURN acc;
            END;
            """));
    }

    @Test
    public void rangeBoundsCanBeVariables() {
        assertEquals(9, runReturningInt("""
            BEGIN
                LET lo INTEGER := 2;
                LET hi INTEGER := 4;
                LET total INTEGER := 0;
                FOR i IN lo TO hi DO
                    total := total + i;
                END FOR;
                RETURN total;
            END;
            """), "2+3+4 = 9");
    }

    @Test
    public void breakExitsRangeLoop() {
        assertEquals(4, runReturningInt("""
            BEGIN
                LET last INTEGER := 0;
                FOR i IN 1 TO 100 DO
                    last := i;
                    IF (i = 4) THEN
                        BREAK;
                    END IF;
                END FOR;
                RETURN last;
            END;
            """));
    }
}
