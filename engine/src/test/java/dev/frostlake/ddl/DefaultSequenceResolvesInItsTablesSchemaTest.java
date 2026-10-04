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

package dev.frostlake.ddl;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A column DEFAULT is evaluated where its table lives. An unqualified {@code seq.NEXTVAL} in a default was looked up in
 * the writing session's schema: an INSERT from elsewhere failed with an invalid identifier, or silently drew from a
 * same-named sequence there. A view's body already resolves in the view's own schema, and a default now does the same.
 */
public class DefaultSequenceResolvesInItsTablesSchemaTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE d1");
        engine.execute("CREATE SCHEMA d1.s1");
        engine.execute("USE SCHEMA d1.s1");
        engine.execute("CREATE SEQUENCE sq");
        engine.execute("CREATE TABLE td (id INT DEFAULT sq.NEXTVAL, v INT)");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " ; " : "");
            for (int i = 0; i < row.size(); i++) {
                out.append(i > 0 ? "|" : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    @Test
    public void anInsertFromAnotherSchemaDrawsFromTheTablesSequence() {
        engine.execute("CREATE DATABASE elsewhere");
        engine.execute("USE SCHEMA elsewhere.PUBLIC");
        engine.execute("INSERT INTO d1.s1.td (v) VALUES (1)");
        engine.execute("CREATE SEQUENCE sq START = 500");
        engine.execute("INSERT INTO d1.s1.td (v) VALUES (2)");
        assertEquals("1|1 ; 2|2", rows("SELECT id, v FROM d1.s1.td ORDER BY v"));
        assertEquals("ELSEWHERE|PUBLIC", rows("SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()"),
            "the session's context is put back");
    }

    @Test
    public void anUpdateToDefaultFromAnotherSchemaDrawsFromTheTablesSequence() {
        engine.execute("INSERT INTO td (v) VALUES (1)");
        engine.execute("CREATE DATABASE elsewhere");
        engine.execute("USE SCHEMA elsewhere.PUBLIC");
        engine.execute("CREATE SEQUENCE sq START = 500");
        engine.execute("UPDATE d1.s1.td SET id = DEFAULT WHERE v = 1");
        assertEquals("2|1", rows("SELECT id, v FROM d1.s1.td"));
    }
}
