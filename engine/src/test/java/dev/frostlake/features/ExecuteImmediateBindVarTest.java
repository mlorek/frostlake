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

public class ExecuteImmediateBindVarTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    @Test
    public void testExecuteImmediateWithBindVar() {
        ResultSet rs = engine.executeQuery(
            "DECLARE dyn_sql STRING DEFAULT 'SELECT 1 as c'; " +
            "BEGIN " +
            "  EXECUTE IMMEDIATE :dyn_sql; " +
            "  RETURN dyn_sql; " +
            "END");
        assertNotNull(rs);
    }

    @Test
    public void testExecuteImmediateAssignThenExecute() {
        engine.execute("CREATE TABLE bind_t (n INTEGER)");
        engine.execute("INSERT INTO bind_t VALUES (42)");
        ResultSet rs = engine.executeQuery(
            "DECLARE q STRING DEFAULT 'SELECT n FROM bind_t'; " +
            "BEGIN " +
            "  RETURN q; " +
            "END");
        assertNotNull(rs);
        assertEquals("SELECT n FROM bind_t", rs.getRows().get(0).getValue(0).toString());
    }
}
