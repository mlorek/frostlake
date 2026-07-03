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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test that LEFT keyword can be used as an identifier in appropriate contexts
 */
public class LeftAsIdentifierTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setup() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
    }

    @AfterEach
    public void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testLeftAsColumnAlias() {
        // LEFT can be used as unquoted identifier (Snowflake compatible)
        ResultSet rs = engine.executeQuery("SELECT 1 AS left");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        rs.next();
        assertEquals(1L, rs.getValue("LEFT"));
    }

    @Test
    public void testLeftAndRightAsIdentifiers() {
        // LEFT and RIGHT can be used as unquoted identifiers (Snowflake compatible)
        ResultSet rs = engine.executeQuery("SELECT 10 AS left, 20 AS right");
        assertNotNull(rs);
        rs.next();
        assertEquals(10L, rs.getValue("LEFT"));
        assertEquals(20L, rs.getValue("RIGHT"));
    }

    @Test
    public void testLeftAsTableAliasWithAs() {
        engine.execute("CREATE TABLE test_table (id INT, value VARCHAR)");
        engine.execute("INSERT INTO test_table VALUES (1, 'test')");

        // LEFT as table alias with AS keyword (Snowflake compatible)
        ResultSet rs = engine.executeQuery("SELECT * FROM test_table AS left");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
    }
}
