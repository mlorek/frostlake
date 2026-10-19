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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a Scala procedure's handler runs, and what of the session it may change: as a handler of any other
 * language. With its owner's rights its statements run in the database and schema the procedure lives in, the
 * caller is back where it was afterwards, and a SET or a USE is refused; with its caller's rights it stays where
 * the caller is, and where it moves the session is where the caller finds it. The handlers here compile against
 * this engine's own session class, so the class runs on the embedded engine alone.
 */
public class ScalaProcedureHomeContextTest {

    private static final String HERE = "SELECT CURRENT_DATABASE() || '.' || CURRENT_SCHEMA()";

    private static final String CALLER = " EXECUTE AS CALLER";

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_db.fl_home");
        engine.execute("CREATE SCHEMA test_db.fl_caller");
        engine.execute("CREATE SCHEMA test_db.fl_other");
        engine.execute("CREATE TABLE test_db.fl_home.audit_log (msg VARCHAR)");
        engine.execute("CREATE TABLE test_db.fl_caller.audit_log (msg VARCHAR)");
        engine.execute("USE SCHEMA test_db.fl_caller");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    /** A Scala procedure whose handler issues {@code statements} and answers the first cell of {@code answer}. */
    private void scala(final String name, final String rights, final String[] statements, final String answer) {
        final StringBuilder body = new StringBuilder();
        for (final String statement : statements) {
            body.append("s.sql(\"").append(statement).append("\").collect(); ");
        }
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "() RETURNS VARCHAR LANGUAGE SCALA "
            + "RUNTIME_VERSION = '2.12' HANDLER = 'H.run'" + rights + " AS $$ import "
            + "com.snowflake.snowpark_java.Session; object H { def run(s: Session): String = { " + body
            + "s.sql(\"" + answer + "\").collect()(0).getString(0) } } $$");
    }

    private String first(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String rootMessage(final Throwable thrown) {
        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return String.valueOf(root.getMessage());
    }

    @Test
    public void anOwnersRightsHandlerRunsWhereItsProcedureLives() {
        scala("test_db.fl_home.scala_own", "", new String[] {"INSERT INTO audit_log VALUES ('scala_own')"}, HERE);
        assertEquals("TEST_DB.FL_HOME", first("CALL test_db.fl_home.scala_own()"));
        assertEquals("TEST_DB.FL_CALLER", first(HERE));
        assertEquals("scala_own", first("SELECT msg FROM test_db.fl_home.audit_log"));
        assertEquals("0", first("SELECT COUNT(*) FROM test_db.fl_caller.audit_log"));
    }

    @Test
    public void aCallersRightsHandlerStaysWhereItsCallerIs() {
        scala("test_db.fl_home.scala_clr", CALLER, new String[] {"INSERT INTO audit_log VALUES ('scala_clr')"}, HERE);
        assertEquals("TEST_DB.FL_CALLER", first("CALL test_db.fl_home.scala_clr()"));
        assertEquals("scala_clr", first("SELECT msg FROM test_db.fl_caller.audit_log"));
        assertEquals("0", first("SELECT COUNT(*) FROM test_db.fl_home.audit_log"));
    }

    @Test
    public void anOwnersRightsHandlerChangesNothingOfTheSession() {
        final String[][] refused = {
            {"SET hv = 9", "Stored procedure execution error: Unsupported statement type 'SET'."},
            {"USE SCHEMA test_db.fl_other", "Stored procedure execution error: Unsupported statement type 'USE'."},
        };
        for (final String[] cell : refused) {
            scala("test_db.fl_home.scala_own", "", new String[] {cell[0]}, "SELECT 'done'");
            final RuntimeException refusal = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery("CALL test_db.fl_home.scala_own()");
                }
            });
            final String message = refusal.getMessage() + " / " + rootMessage(refusal);
            assertTrue(message.contains(cell[1]), cell[0] + " ==> " + message);
            assertEquals("TEST_DB.FL_CALLER", first(HERE), cell[0]);
            assertEquals("none", first("SELECT NVL(GETVARIABLE('HV')::VARCHAR, 'none')"), cell[0]);
        }
    }

    @Test
    public void aCallersRightsHandlerLeavesTheSessionWhereItPutIt() {
        scala("test_db.fl_home.scala_clr", CALLER, new String[] {"USE SCHEMA test_db.fl_other", "SET hv = 9"},
            "SELECT 'done'");
        assertEquals("done", first("CALL test_db.fl_home.scala_clr()"));
        assertEquals("TEST_DB.FL_OTHER", first(HERE));
        assertEquals("9", first("SELECT GETVARIABLE('HV')::VARCHAR"));
    }
}
