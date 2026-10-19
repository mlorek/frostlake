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

package dev.frostlake.dml;

import dev.frostlake.DatabaseEngine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A load's options may be separated by commas, as a stage's are (live-verified): {@code FILE_FORMAT = (TYPE = CSV),
 * ON_ERROR = CONTINUE} and {@code ON_ERROR = CONTINUE, FORCE = TRUE} each run the COPY, and a doubled comma is a syntax
 * error at the second.
 */
public class CopyOptionCommaTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE cc_db");
        engine.execute("USE SCHEMA cc_db.PUBLIC");
        engine.execute("CREATE TABLE ct (a INT)");
        engine.execute("CREATE STAGE cs");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String status(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    @Test
    public void aCommaStandsBetweenALoadsOptions() {
        assertEquals("Copy executed with 0 files processed.",
            status("COPY INTO ct FROM @cs FILE_FORMAT = (TYPE = CSV), ON_ERROR = CONTINUE"));
        assertEquals("Copy executed with 0 files processed.",
            status("COPY INTO ct FROM @cs FILE_FORMAT = (TYPE = CSV) ON_ERROR = CONTINUE, FORCE = TRUE"));
        assertTrue(refusal("COPY INTO ct FROM @cs ON_ERROR = CONTINUE, , FORCE = TRUE")
            .contains("syntax error line 1 at position 43 unexpected ','."), "the second comma is the fault");
    }
}
