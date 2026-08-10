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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CURRENT_TIMESTAMP is statement-stable but NOT block-stable: within one statement every read
 * answers the same instant, while each statement inside a procedural block reads its own clock —
 * so time-ordered loaders that stamp rows across successive statements order correctly.
 */
public class BlockStatementClockTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(BlockStatementClockTest.class);

    @Test
    public void testEachBlockStatementReadsItsOwnClock() {
        // The generator between the two reads takes real work, so the two statements' instants
        // differ by far more than the clock's resolution — no sleep, not timing-sensitive.
        final ResultSet result = engine.executeQuery("""
            DECLARE
                t1 VARCHAR;
                t2 VARCHAR;
                burn NUMBER;
            BEGIN
                t1 := CURRENT_TIMESTAMP()::VARCHAR;
                burn := (SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 2000000)));
                t2 := CURRENT_TIMESTAMP()::VARCHAR;
                RETURN IFF(:t1 = :t2, 'frozen', 'advanced');
            END;
            """);

        assertEquals("advanced", result.getRows().get(0).getValue(0));

        logger.info("Block statements read fresh clocks");
    }

    @Test
    public void testWithinOneStatementTheClockIsStable() {
        final ResultSet result = engine.executeQuery(
            "SELECT CURRENT_TIMESTAMP() = CURRENT_TIMESTAMP()");

        assertEquals(true, result.getRows().get(0).getValue(0));

        logger.info("One statement answers one instant");
    }
}
