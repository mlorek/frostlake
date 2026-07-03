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

public class ExecuteAsTest {

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
    public void testExecuteAsOwner() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE my_proc() " +
            "RETURNS VARCHAR " +
            "LANGUAGE SQL " +
            "COMMENT = 'owner proc' " +
            "EXECUTE AS OWNER " +
            "AS $$ BEGIN RETURN 'ok'; END $$"
        );
        ResultSet rs = engine.executeQuery("SHOW PROCEDURES");
        assertNotNull(rs);
        int execAsIdx = rs.getColumnIndex("execute_as");
        boolean found = false;
        for (int i = 0; i < rs.getRowCount(); i++) {
            if ("MY_PROC".equalsIgnoreCase(rs.getRows().get(i).getValue(1).toString())) {
                found = true;
                assertEquals("OWNER", rs.getRows().get(i).getValue(execAsIdx).toString());
            }
        }
        assertTrue(found, "Procedure MY_PROC should be visible in SHOW PROCEDURES");
    }

    @Test
    public void testExecuteAsCaller() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE caller_proc(n INTEGER) " +
            "RETURNS INTEGER " +
            "LANGUAGE SQL " +
            "COMMENT = 'caller proc' " +
            "EXECUTE AS CALLER " +
            "AS $$ BEGIN RETURN n; END $$"
        );
        ResultSet rs = engine.executeQuery("SHOW PROCEDURES");
        assertNotNull(rs);
        int execAsIdx = rs.getColumnIndex("execute_as");
        boolean found = false;
        for (int i = 0; i < rs.getRowCount(); i++) {
            if ("CALLER_PROC".equalsIgnoreCase(rs.getRows().get(i).getValue(1).toString())) {
                found = true;
                assertEquals("CALLER", rs.getRows().get(i).getValue(execAsIdx).toString());
            }
        }
        assertTrue(found, "Procedure CALLER_PROC should be visible in SHOW PROCEDURES");
    }

    @Test
    public void testDefaultExecuteAsIsOwner() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE default_proc() " +
            "RETURNS VARCHAR " +
            "LANGUAGE SQL " +
            "AS $$ BEGIN RETURN 'default'; END $$"
        );
        ResultSet rs = engine.executeQuery("SHOW PROCEDURES");
        int execAsIdx = rs.getColumnIndex("execute_as");
        for (int i = 0; i < rs.getRowCount(); i++) {
            if ("DEFAULT_PROC".equalsIgnoreCase(rs.getRows().get(i).getValue(1).toString())) {
                assertEquals("OWNER", rs.getRows().get(i).getValue(execAsIdx).toString(),
                    "Default execute_as should be OWNER");
            }
        }
    }

    @Test
    public void testCommentBeforeExecuteAs() {
        // COMMENT clause before EXECUTE AS clause
        engine.execute(
            "CREATE OR REPLACE PROCEDURE commented_proc() " +
            "RETURNS VARCHAR " +
            "LANGUAGE SQL " +
            "COMMENT = 'my comment' " +
            "EXECUTE AS OWNER " +
            "AS $$ BEGIN RETURN 'hello'; END $$"
        );
        ResultSet rs = engine.executeQuery("SHOW PROCEDURES");
        int commentIdx = rs.getColumnIndex("description");
        int execAsIdx = rs.getColumnIndex("execute_as");
        for (int i = 0; i < rs.getRowCount(); i++) {
            if ("COMMENTED_PROC".equalsIgnoreCase(rs.getRows().get(i).getValue(1).toString())) {
                assertEquals("my comment", rs.getRows().get(i).getValue(commentIdx));
                assertEquals("OWNER", rs.getRows().get(i).getValue(execAsIdx));
            }
        }
    }
}
