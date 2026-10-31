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

package dev.frostlake.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.DatabaseEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A stream on a join view reads the tables its view names in the view's own schema even where the session's current
 * schema is in another database, or there is none: the view, its CHANGES and the stream answer alike. The live
 * account allows no CREATE DATABASE, so the other database is the embedded engine's own check (see
 * JoinViewStreamReadElsewhereTest for the schema measured on the account).
 */
public class JoinViewStreamWithoutCurrentSchemaTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE d");
        engine.execute("USE SCHEMA d.PUBLIC");
        engine.execute("CREATE TABLE a (id INT, x STRING)");
        engine.execute("CREATE TABLE b (id INT, y STRING)");
        engine.execute("INSERT INTO a VALUES (1, 'a1'), (2, 'a2')");
        engine.execute("INSERT INTO b VALUES (1, 'b1'), (2, 'b2')");
        engine.execute("CREATE VIEW jv AS SELECT a.id, x, y FROM a JOIN b ON a.id = b.id");
        engine.execute("CREATE STREAM js ON VIEW jv");
        engine.execute("UPDATE a SET x = 'a1u' WHERE id = 1");
        engine.execute("CREATE DATABASE other");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private long count(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void readFromAnotherDatabasesSchema() {
        assertEquals(2L, count("SELECT COUNT(*) FROM d.PUBLIC.jv"));
        assertEquals(2L, count("SELECT COUNT(*) FROM d.PUBLIC.js"));
        assertEquals(2L, count("SELECT COUNT(*) FROM d.PUBLIC.jv CHANGES(INFORMATION => DEFAULT) "
            + "AT(STREAM => 'd.PUBLIC.js')"));
    }

    @Test
    public void readWithNoCurrentSchema() {
        engine.execute("DROP SCHEMA other.PUBLIC");
        assertEquals(2L, count("SELECT COUNT(*) FROM d.PUBLIC.js"));
        assertEquals(2L, count("SELECT COUNT(*) FROM d.PUBLIC.jv CHANGES(INFORMATION => DEFAULT) "
            + "AT(STREAM => 'd.PUBLIC.js')"));
    }
}
