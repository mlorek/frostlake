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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Executes Snowflake Scripting loop control flow in anonymous BEGIN…END blocks and asserts the COMPUTED
 * result (not merely that the block parses): a bare {@code LOOP … END LOOP} exited by {@code BREAK},
 * {@code CONTINUE} skipping the rest of an iteration, and {@code BREAK} inside a {@code WHILE} loop.
 * Complements the cursor-FOR / proc-body loop coverage elsewhere, which never asserts loop execution.
 */
public class LoopBreakContinueTest {

    private static final Logger logger = LoggerFactory.getLogger(LoopBreakContinueTest.class);

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
        assertNotNull(rs, "block should return a result set");
        assertEquals(1, rs.getRowCount(), "RETURN should produce exactly one row");
        return ((Number) rs.getRows().get(0).getValue(0)).intValue();
    }

    @Test
    public void loopWithBreakStopsWhenConditionMet() {
        logger.info("Testing bare LOOP exited by BREAK");
        final int result = runReturningInt("""
            BEGIN
                LET counter INTEGER := 0;
                LOOP
                    counter := counter + 1;
                    IF counter >= 5 THEN
                        BREAK;
                    END IF;
                END LOOP;
                RETURN counter;
            END;
            """);
        assertEquals(5, result, "LOOP must run until BREAK fires at counter = 5");
    }

    @Test
    public void continueSkipsRemainderOfIteration() {
        logger.info("Testing CONTINUE skipping the rest of a loop iteration");
        // i runs 1..10 (then BREAK at 11); CONTINUE skips the odd values, so only the 5 even values increment.
        final int result = runReturningInt("""
            BEGIN
                LET i INTEGER := 0;
                LET evens INTEGER := 0;
                LOOP
                    i := i + 1;
                    IF i > 10 THEN
                        BREAK;
                    END IF;
                    IF MOD(i, 2) = 1 THEN
                        CONTINUE;
                    END IF;
                    evens := evens + 1;
                END LOOP;
                RETURN evens;
            END;
            """);
        assertEquals(5, result, "CONTINUE must skip odd i, counting only the 5 evens in 1..10");
    }

    @Test
    public void breakExitsWhileLoopEarly() {
        logger.info("Testing BREAK inside a WHILE loop");
        final int result = runReturningInt("""
            BEGIN
                LET i INTEGER := 0;
                WHILE i < 100 DO
                    i := i + 1;
                    IF i = 7 THEN
                        BREAK;
                    END IF;
                END WHILE;
                RETURN i;
            END;
            """);
        assertEquals(7, result, "BREAK must exit the WHILE loop early at i = 7");
    }

    @Test
    public void whileLoopComputesRunningTotal() {
        logger.info("Testing WHILE loop execution accumulating a total");
        // A plain WHILE that actually executes and accumulates: 1 + 2 + 3 + 4 + 5 = 15.
        final int result = runReturningInt("""
            BEGIN
                LET i INTEGER := 0;
                LET total INTEGER := 0;
                WHILE i < 5 DO
                    i := i + 1;
                    total := total + i;
                END WHILE;
                RETURN total;
            END;
            """);
        assertEquals(15, result, "WHILE must sum 1..5 = 15");
    }
}
