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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * INFORMATION_SCHEMA's creation and alteration times are TIMESTAMP_LTZ(3) values in the session's zone, as SHOW's
 * created_on is. They were raw instants, which compared as text, rendered in the host's zone and ignored the
 * session's TIMEZONE: in a session on Asia/Tokyo a table created a moment ago was not created within the last hour.
 */
public class InformationSchemaTimesTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE ist");
        engine.execute("USE SCHEMA ist.PUBLIC");
        engine.execute("CREATE TABLE x (a INT)");
        engine.execute("CREATE SEQUENCE sq");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private List<Object> row(final String sql) {
        final List<Row> rows = engine.executeQuery(sql).getRows();
        return rows.get(0).getValues();
    }

    @Test
    public void timesReadAsInstantsInTheSessionsZone() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'");
        final String recent = " > DATEADD(hour, -1, CURRENT_TIMESTAMP())";
        assertEquals(List.of(true, true, true, "+09:00"), row("SELECT created" + recent + ", last_altered" + recent
            + ", created <= CURRENT_TIMESTAMP(), TO_VARCHAR(created, 'TZH:TZM')"
            + " FROM information_schema.tables WHERE table_name = 'X'"));
        assertEquals(List.of(true, "+09:00"), row("SELECT created" + recent + ", TO_VARCHAR(created, 'TZH:TZM')"
            + " FROM information_schema.schemata WHERE schema_name = 'PUBLIC'"));
        assertEquals(List.of(true), row("SELECT created" + recent
            + " FROM information_schema.databases WHERE database_name = 'IST'"));
        assertEquals(List.of(true), row("SELECT created" + recent
            + " FROM information_schema.sequences WHERE sequence_name = 'SQ'"));
    }
}
