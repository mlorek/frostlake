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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A number of seconds as a time-travel point names the instant TO_TIMESTAMP reads it as. It was read as the last
 * millisecond of its second, up to 999 ms late, so the point saw writes made after it, or was still in the future
 * while its second lasted.
 */
public class TimeTravelEpochSecondsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE tts");
        engine.execute("USE SCHEMA tts.PUBLIC");
        engine.execute("CREATE TABLE y (a INT)");
        engine.execute("INSERT INTO y VALUES (1)");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** A count, or the refusal's message: the outcome of reading a point either way. */
    private String outcome(final String sql) {
        try {
            final Row row = engine.executeQuery(sql).getRows().get(0);
            return String.valueOf(row.getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    @Test
    public void secondsWithAFractionNameTheirMillisecond() {
        engine.execute("SET s = DATE_PART(epoch_millisecond, CURRENT_TIMESTAMP()) / 1000");
        engine.execute("INSERT INTO y VALUES (2)");
        assertEquals("1", outcome("SELECT COUNT(*) FROM y AT(TIMESTAMP => $s)"), "the write after the point is not seen");
        assertEquals("1", outcome("SELECT COUNT(*) FROM y AT(TIMESTAMP => TO_TIMESTAMP($s))"));
        assertEquals("1", outcome("SELECT COUNT(*) FROM y BEFORE(TIMESTAMP => $s)"));
    }

    @Test
    public void wholeSecondsReadAsToTimestampReadsThem() {
        engine.execute("SET s = DATE_PART(epoch_second, CURRENT_TIMESTAMP())");
        engine.execute("INSERT INTO y VALUES (2)");
        assertEquals(outcome("SELECT COUNT(*) FROM y AT(TIMESTAMP => TO_TIMESTAMP($s))"),
            outcome("SELECT COUNT(*) FROM y AT(TIMESTAMP => $s)"));
        assertEquals(outcome("SELECT COUNT(*) FROM y BEFORE(TIMESTAMP => TO_TIMESTAMP($s))"),
            outcome("SELECT COUNT(*) FROM y BEFORE(TIMESTAMP => $s)"));
    }
}
