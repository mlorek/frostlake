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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DELETE command
 */
public class DeleteTest extends BaseDatabaseTest {

    @Test
    public void testBasicDelete() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30)");

        engine.execute("DELETE FROM users WHERE id = 1");

        ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(0, result.getRowCount());
    }

    @Test
    public void testDeleteWithWhere() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 25), (3, 'Charlie', 35)");

        engine.execute("DELETE FROM users WHERE age < 30");

        ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(2, result.getRowCount());
    }

    @Test
    public void testDeleteMultipleRows() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("""
            INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 25), (3, 'Charlie', 35), (4, 'Diana', 28)
            """);

        engine.execute("DELETE FROM users WHERE age > 25 AND age < 35");

        ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(2, result.getRowCount());

        // Verify remaining rows
        List<Row> rows = result.getRows();
        assertTrue(rows.stream().anyMatch((final var r) -> r.getValue(1).equals("Bob")));
        assertTrue(rows.stream().anyMatch((final var r) -> r.getValue(1).equals("Charlie")));
    }

    @Test
    public void testDeleteNoMatch() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30)");

        engine.execute("DELETE FROM users WHERE id = 999");

        ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(1, result.getRowCount());
    }

    @Test
    public void testDeleteAll() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 25), (3, 'Charlie', 35)");

        engine.execute("DELETE FROM users");

        ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(0, result.getRowCount());
    }

    @Test
    public void testDeleteWithComplexWhere() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("""
            INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 25), (3, 'Charlie', 35), (4, 'Diana', 28)
            """);

        engine.execute("DELETE FROM users WHERE age = 30 OR age > 33");

        ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(2, result.getRowCount());
    }

    @Test
    public void testDeleteAndReinsert() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice')");
        engine.execute("DELETE FROM users WHERE id = 1");

        ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(0, result.getRowCount());

        // Reinsert same data
        engine.execute("INSERT INTO users VALUES (1, 'Alice')");
        result = engine.executeQuery("SELECT * FROM users");
        assertEquals(1, result.getRowCount());
    }

    @Test
    public void testDeletePreservesOtherRows() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 25), (3, 'Charlie', 35)");

        engine.execute("DELETE FROM users WHERE id = 2");

        ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(2, result.getRowCount());

        List<Row> rows = result.getRows();
        assertTrue(rows.stream().anyMatch((final var r) -> r.getValue(1).equals("Alice")));
        assertTrue(rows.stream().anyMatch((final var r) -> r.getValue(1).equals("Charlie")));
        assertFalse(rows.stream().anyMatch((final var r) -> r.getValue(1).equals("Bob")));
    }
}
