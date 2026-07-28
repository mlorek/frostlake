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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LateralFlattenTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
        engine.execute("CREATE TABLE events (id INTEGER, tags VARIANT)");
        engine.execute("INSERT INTO events SELECT 1, PARSE_JSON('[\"a\",\"b\",\"c\"]')");
        engine.execute("INSERT INTO events SELECT 2, PARSE_JSON('[\"x\",\"y\"]')");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    @Test
    public void testLateralFlattenShorthand() {
        // LATERAL FLATTEN without TABLE(...)
        ResultSet rs = engine.executeQuery(
            "SELECT e.id, f.value FROM events e, LATERAL FLATTEN(INPUT => e.tags) f ORDER BY e.id, f.value");
        assertNotNull(rs);
        assertEquals(5, rs.getRowCount());
    }

    @Test
    public void testLateralFlattenJoinSyntax() {
        // Live-Snowflake verified: a lateral table FUNCTION with an ON predicate — even ON TRUE —
        // is "Unsupported feature ...". The comma-lateral form is the supported spelling.
        try {
            engine.executeQuery(
                "SELECT e.id, f.value FROM events e JOIN LATERAL FLATTEN(INPUT => e.tags) f ON TRUE");
            throw new AssertionError("lateral FLATTEN with ON must be rejected");
        } catch (final RuntimeException expected) {
            assertNotNull(expected.getMessage());
        }
        ResultSet rs = engine.executeQuery(
            "SELECT e.id, f.value FROM events e, LATERAL FLATTEN(INPUT => e.tags) f ORDER BY e.id, f.value");
        assertNotNull(rs);
        assertEquals(5, rs.getRowCount());
    }
}
