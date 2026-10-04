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
 * An object's creation is stamped with the creating statement's instant, the one its CURRENT_TIMESTAMP reads. It
 * was stamped from the wall clock at microsecond resolution while the statement clock reads milliseconds, so a table
 * created a moment ago could be created after the next statement's CURRENT_TIMESTAMP.
 */
public class CreatedStampsFollowTheClockTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE cs");
        engine.execute("USE SCHEMA cs.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private Object cell(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        return row.getValue(0);
    }

    @Test
    public void aTableIsCreatedAtItsStatementsInstant() {
        engine.execute("CREATE TABLE x AS SELECT CURRENT_TIMESTAMP() AS ts");
        assertEquals(true, cell("SELECT created = (SELECT ts FROM x)"
            + " FROM information_schema.tables WHERE table_name = 'X'"));
    }

    @Test
    public void nothingIsCreatedAfterALaterStatementsCurrentTimestamp() {
        for (int i = 0; i < 100; i++) {
            engine.execute("CREATE OR REPLACE TABLE x (a INT)");
            assertEquals(true, cell("SELECT TIMESTAMPDIFF(microsecond, created, CURRENT_TIMESTAMP()) >= 0"
                + " FROM information_schema.tables WHERE table_name = 'X'"), "table, run " + i);
        }
        engine.execute("CREATE SEQUENCE sq");
        assertEquals(true, cell("SELECT created <= CURRENT_TIMESTAMP()"
            + " FROM information_schema.sequences WHERE sequence_name = 'SQ'"), "sequence");
        engine.execute("CREATE FILE FORMAT ff TYPE = CSV");
        assertEquals(true, cell("SELECT created <= CURRENT_TIMESTAMP()"
            + " FROM information_schema.file_formats WHERE file_format_name = 'FF'"), "file format");
    }
}
