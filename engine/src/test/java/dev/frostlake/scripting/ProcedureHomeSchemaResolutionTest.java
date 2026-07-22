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

package dev.frostlake.scripting;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A stored procedure's unqualified / partially-qualified object references resolve against the
 * procedure's <em>home</em> database and schema (where the procedure lives) — matching Snowflake — not the
 * caller's current database. Here {@code LIB.UTIL.SAVE_ENTRY} internally calls {@code UTIL.IS_ALLOWED} and
 * merges into {@code UTIL.SETTINGS}, so those must resolve against LIB.UTIL when it is invoked from another
 * current database. The caller's context is restored after the call.
 */
public class ProcedureHomeSchemaResolutionTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        // Home of the helper function/table/procedure: LIB.UTIL.
        engine.execute("CREATE DATABASE lib");
        engine.execute("USE DATABASE lib");
        engine.execute("CREATE SCHEMA util");
        engine.execute("USE SCHEMA util");
        engine.execute("""
            CREATE OR REPLACE FUNCTION UTIL.IS_ALLOWED(code VARCHAR)
            RETURNS BOOLEAN LANGUAGE SQL AS $$ UPPER(code) IN ('A', 'B', 'C') $$
            """);
        engine.execute("CREATE TABLE UTIL.SETTINGS (name VARCHAR, label VARCHAR, code VARCHAR)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE UTIL.SAVE_ENTRY(p_name VARCHAR, p_label VARCHAR, p_code VARCHAR)
            RETURNS VARCHAR LANGUAGE SQL AS
            $$ BEGIN
                 IF (NOT UTIL.IS_ALLOWED(:p_code)) THEN RETURN 'invalid'; END IF;
                 MERGE INTO UTIL.SETTINGS t
                   USING (SELECT :p_name AS name, :p_label AS label) s
                   ON t.name = s.name AND t.label = s.label
                   WHEN NOT MATCHED THEN INSERT (name, label, code)
                     VALUES (:p_name, :p_label, :p_code);
                 RETURN 'saved';
               END $$
            """);
        // Switch the session to a different database, so the procedure is called from outside its home.
        engine.execute("CREATE DATABASE appdb");
        engine.execute("USE DATABASE appdb");
        engine.execute("CREATE SCHEMA work");
        engine.execute("USE SCHEMA work");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void crossDatabaseCallResolvesInternalReferencesAgainstHomeDatabase() {
        final ResultSet rs = engine.executeQuery(
            "CALL lib.util.save_entry('n1', 'label1', 'A')");
        assertEquals("saved", rs.getRows().get(0).getValue(0));

        // The MERGE resolved to the procedure's home-database table, not appdb.
        assertEquals(1L, ((Number) scalar(
            "SELECT COUNT(*) FROM lib.util.settings")).longValue());
    }

    @Test
    public void callerCurrentDatabaseIsRestoredAfterTheCall() {
        engine.execute("CALL lib.util.save_entry('n1', 'l', 'A')");
        assertEquals("APPDB", scalar("SELECT CURRENT_DATABASE()"));
        assertEquals("WORK", scalar("SELECT CURRENT_SCHEMA()"));
    }

    @Test
    public void invalidCodeIsStillDetectedCrossDatabase() {
        assertEquals("invalid",
            engine.executeQuery("CALL lib.util.save_entry('n1', 'l', 'ZZ')").getRows().get(0).getValue(0));
    }
}
