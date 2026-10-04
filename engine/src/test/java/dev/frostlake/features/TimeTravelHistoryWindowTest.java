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

package dev.frostlake.features;

import dev.frostlake.DatabaseEngine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Time travel to a point whose history the engine no longer keeps is refused, not answered with an empty table.
 * Table storage keeps a bounded window of snapshots; once more writes evicted the oldest, a point before the oldest
 * one kept read as a table with no rows at all.
 */
public class TimeTravelHistoryWindowTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE tw");
        engine.execute("USE SCHEMA tw.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String outcome(final String sql) {
        try {
            return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
        } catch (final RuntimeException refused) {
            return refused.getMessage();
        }
    }

    @Test
    public void aPointBeforeTheKeptHistoryIsRefused() {
        engine.execute("CREATE TABLE r (a INT)");
        engine.execute("INSERT INTO r VALUES (0)");
        engine.execute("SET t = CURRENT_TIMESTAMP()");
        assertEquals("1", outcome("SELECT COUNT(*) FROM r AT(TIMESTAMP => $t)"), "inside the window");
        for (int i = 1; i <= 250; i++) {
            engine.execute("INSERT INTO r VALUES (" + i + ")");
        }
        final String refusal = "Time travel data is not available for table R. The requested time is either beyond"
            + " the allowed time travel period or before the object creation time.";
        assertEquals(refusal, outcome("SELECT COUNT(*) FROM r AT(TIMESTAMP => $t)"));
        assertEquals(refusal, outcome("SELECT COUNT(*) FROM r BEFORE(TIMESTAMP => $t)"));
        assertEquals("251", outcome("SELECT COUNT(*) FROM r AT(OFFSET => 0)"), "the kept history still reads");
    }
}
