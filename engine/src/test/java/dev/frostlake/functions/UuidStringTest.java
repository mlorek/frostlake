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

package dev.frostlake.functions;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class UuidStringTest {

    private static final Pattern UUID_PATTERN =
        Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
            Pattern.CASE_INSENSITIVE);

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    @Test
    public void testUuidStringFunction() {
        ResultSet rs = engine.executeQuery("SELECT UUID_STRING()");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        String uuid = rs.getRows().get(0).getValue(0).toString();
        assertTrue(UUID_PATTERN.matcher(uuid).matches(), "Expected UUID format, got: " + uuid);
    }

    @Test
    public void testUuidStringGeneratesUniqueValues() {
        ResultSet r1 = engine.executeQuery("SELECT UUID_STRING()");
        ResultSet r2 = engine.executeQuery("SELECT UUID_STRING()");
        String u1 = r1.getRows().get(0).getValue(0).toString();
        String u2 = r2.getRows().get(0).getValue(0).toString();
        assertNotEquals(u1, u2, "Two UUID_STRING() calls should produce different values");
    }

    @Test
    public void testUuidStringAsDefault() {
        engine.execute("""
            CREATE OR REPLACE TABLE sample_generate_uuid (
              id UUID DEFAULT UUID_STRING() NOT NULL,
              sample_column VARCHAR)
            """);

        engine.execute("INSERT INTO sample_generate_uuid (sample_column) VALUES ('hello')");
        engine.execute("INSERT INTO sample_generate_uuid (sample_column) VALUES ('world')");

        ResultSet rs = engine.executeQuery("SELECT id, sample_column FROM sample_generate_uuid");
        assertEquals(2, rs.getRowCount());

        String id1 = rs.getRows().get(0).getValue(0).toString();
        String id2 = rs.getRows().get(1).getValue(0).toString();

        assertTrue(UUID_PATTERN.matcher(id1).matches(), "id1 should be UUID: " + id1);
        assertTrue(UUID_PATTERN.matcher(id2).matches(), "id2 should be UUID: " + id2);
        assertNotEquals(id1, id2, "Each row should get a distinct UUID");
    }

    @Test
    public void testExplicitUuidInsert() {
        engine.execute("""
            CREATE TABLE with_uuid (id UUID, name VARCHAR)
            """);
        engine.execute("INSERT INTO with_uuid VALUES (UUID_STRING(), 'Alice')");

        ResultSet rs = engine.executeQuery("SELECT id FROM with_uuid");
        assertEquals(1, rs.getRowCount());
        assertTrue(UUID_PATTERN.matcher(rs.getRows().get(0).getValue(0).toString()).matches());
    }
}
