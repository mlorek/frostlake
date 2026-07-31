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

package dev.frostlake.query;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * MINUS is Snowflake's synonym for the EXCEPT set operator. Verifies MINUS / MINUS ALL behave exactly like
 * EXCEPT / EXCEPT ALL, and that adding the MINUS keyword token did not stop {@code minus} being usable as an
 * identifier (it is listed in the grammar's {@code identifier} rule).
 */
public class MinusSetOperatorTest {

    private static DatabaseEngine engine;

    @BeforeAll
    public static void setup() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterAll
    public static void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @BeforeEach
    public void setupData() {
        engine.execute("DROP TABLE IF EXISTS set1");
        engine.execute("DROP TABLE IF EXISTS set2");

        engine.execute("CREATE TABLE set1 (id INT, value VARCHAR)");
        engine.execute("INSERT INTO set1 VALUES (1, 'A')");
        engine.execute("INSERT INTO set1 VALUES (2, 'B')");
        engine.execute("INSERT INTO set1 VALUES (3, 'C')");
        engine.execute("INSERT INTO set1 VALUES (3, 'C')");   // duplicate
        engine.execute("INSERT INTO set1 VALUES (4, 'D')");

        engine.execute("CREATE TABLE set2 (id INT, value VARCHAR)");
        engine.execute("INSERT INTO set2 VALUES (3, 'C')");
        engine.execute("INSERT INTO set2 VALUES (4, 'D')");
        engine.execute("INSERT INTO set2 VALUES (5, 'E')");
        engine.execute("INSERT INTO set2 VALUES (6, 'F')");
    }

    @Test
    public void minusBehavesLikeExcept() {
        final ResultSet result = engine.executeQuery("""
            SELECT id, value FROM set1
            MINUS
            SELECT id, value FROM set2
            ORDER BY id
            """);

        // Rows in set1 not in set2 (distinct): (1,A), (2,B).
        assertEquals(2, result.getRows().size());
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals("A", result.getRows().get(0).getValue(1));
        assertEquals(2L, result.getRows().get(1).getValue(0));
        assertEquals("B", result.getRows().get(1).getValue(1));
    }

    @Test
    public void minusAllIsRejected() {
        // Live-Snowflake verified: ALL applies only to UNION ("Unsupported feature 'MINUS ALL'").
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT id, value FROM set1 MINUS ALL SELECT id, value FROM set2");
            }
        });
        assertTrue(e.getMessage().contains("Unsupported feature"), "unexpected: " + e.getMessage());
    }

    @Test
    public void minusIsReversible() {
        final ResultSet result = engine.executeQuery("""
            SELECT id, value FROM set2
            MINUS
            SELECT id, value FROM set1
            ORDER BY id
            """);

        // Rows in set2 not in set1: (5,E), (6,F).
        assertEquals(2, result.getRows().size());
        assertEquals(5L, result.getRows().get(0).getValue(0));
        assertEquals(6L, result.getRows().get(1).getValue(0));
    }

    @Test
    public void minusStillUsableAsIdentifier() {
        // Adding the MINUS keyword must not stop "minus" working as a column alias.
        final ResultSet result = engine.executeQuery("SELECT id AS minus FROM set1 WHERE id = 1");
        assertEquals(1, result.getRows().size());
        assertEquals("MINUS", result.getColumns().get(0).getName().toUpperCase());
    }
}
