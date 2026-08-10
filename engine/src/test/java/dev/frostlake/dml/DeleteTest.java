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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for DELETE command
 */
public class DeleteTest extends BaseDatabaseTest {

    @Test
    public void testBasicDelete() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30)");

        engine.execute("DELETE FROM users WHERE id = 1");

        final ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(0, result.getRowCount());
    }

    @Test
    public void testDeleteWithWhere() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 25), (3, 'Charlie', 35)");

        engine.execute("DELETE FROM users WHERE age < 30");

        final ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(2, result.getRowCount());
    }

    @Test
    public void testDeleteMultipleRows() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("""
            INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 25), (3, 'Charlie', 35), (4, 'Diana', 28)
            """);

        engine.execute("DELETE FROM users WHERE age > 25 AND age < 35");

        final ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(2, result.getRowCount());

        // Verify remaining rows
        final List<Row> rows = result.getRows();
        assertTrue(hasName(rows, "Bob"));
        assertTrue(hasName(rows, "Charlie"));
    }

    @Test
    public void testDeleteNoMatch() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30)");

        engine.execute("DELETE FROM users WHERE id = 999");

        final ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(1, result.getRowCount());
    }

    @Test
    public void testDeleteAll() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 25), (3, 'Charlie', 35)");

        engine.execute("DELETE FROM users");

        final ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(0, result.getRowCount());
    }

    @Test
    public void testDeleteWithComplexWhere() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("""
            INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 25), (3, 'Charlie', 35), (4, 'Diana', 28)
            """);

        engine.execute("DELETE FROM users WHERE age = 30 OR age > 33");

        final ResultSet result = engine.executeQuery("SELECT * FROM users");
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

        final ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(2, result.getRowCount());

        final List<Row> rows = result.getRows();
        assertTrue(hasName(rows, "Alice"));
        assertTrue(hasName(rows, "Charlie"));
        assertFalse(hasName(rows, "Bob"));
    }
    /** Whether any row carries the given value in its second column. */
    private boolean hasName(final List<Row> rows, final String name) {
        for (final Row row : rows) {
            if (name.equals(row.getValue(1))) {
                return true;
            }
        }
        return false;
    }
}
