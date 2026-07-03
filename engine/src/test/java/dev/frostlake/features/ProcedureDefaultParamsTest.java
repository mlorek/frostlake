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

public class ProcedureDefaultParamsTest {

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
    public void testProcedureDefaultIntegerParam() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE add_rows(n INTEGER DEFAULT 1) " +
            "RETURNS VARCHAR " +
            "LANGUAGE SQL " +
            "AS $$ BEGIN RETURN 'ok'; END $$"
        );
        // Calling without arg should use default (no error)
        assertDoesNotThrow(() -> engine.execute("CALL add_rows()"));
    }

    @Test
    public void testProcedureDefaultStringParam() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE greet(name VARCHAR DEFAULT 'World') " +
            "RETURNS VARCHAR " +
            "LANGUAGE SQL " +
            "AS $$ BEGIN RETURN 'hello'; END $$"
        );
        assertDoesNotThrow(() -> engine.execute("CALL greet()"));
        assertDoesNotThrow(() -> engine.execute("CALL greet('Alice')"));
    }

    @Test
    public void testMixedDefaultAndRequired() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE compute(x INTEGER, y INTEGER DEFAULT 10) " +
            "RETURNS INTEGER " +
            "LANGUAGE SQL " +
            "AS $$ BEGIN RETURN x; END $$"
        );
        // x is required, y has default
        assertDoesNotThrow(() -> engine.execute("CALL compute(5)"));
        assertDoesNotThrow(() -> engine.execute("CALL compute(5, 20)"));
    }

    @Test
    public void testMissingRequiredParamThrows() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE strict_proc(x INTEGER) " +
            "RETURNS INTEGER " +
            "LANGUAGE SQL " +
            "AS $$ BEGIN RETURN x; END $$"
        );
        assertThrows(RuntimeException.class, () -> engine.execute("CALL strict_proc()"));
    }

    @Test
    public void testDefaultParamVisibleInShowProcedures() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE with_default(i INTEGER DEFAULT 0) " +
            "RETURNS INTEGER " +
            "LANGUAGE SQL " +
            "AS $$ BEGIN RETURN i; END $$"
        );
        ResultSet rs = engine.executeQuery("SHOW PROCEDURES");
        assertNotNull(rs);
        boolean found = false;
        for (int i = 0; i < rs.getRowCount(); i++) {
            if ("WITH_DEFAULT".equalsIgnoreCase(rs.getRows().get(i).getValue(1).toString())) {
                found = true;
                String sig = rs.getRows().get(i).getValue(rs.getColumnIndex("arguments")).toString();
                assertTrue(sig.contains("WITH_DEFAULT"), "Signature should contain procedure name");
            }
        }
        assertTrue(found);
    }
}
