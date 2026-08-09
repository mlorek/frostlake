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

package dev.frostlake.procedures;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import java.util.List;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snowflake's scoped-transaction rule, live-verified: a transaction STARTED inside a stored
 * procedure must be completed inside it — returning with it open rolls the transaction back and the
 * CALL fails with "Scoped transaction started in stored procedure is incomplete and it was rolled
 * back." A transaction opened by the CALLER passes through untouched, and a committed-inside
 * transaction works normally.
 */
public class ScopedTransactionTest extends BaseDatabaseTest {

    private long count(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void openScopedTransactionRollsBackAndErrors() {
        engine.execute("CREATE TABLE sct_t (i INTEGER)");
        engine.execute("""
            CREATE PROCEDURE p_open_txn() RETURNS VARCHAR LANGUAGE SQL AS
            BEGIN
                BEGIN TRANSACTION;
                INSERT INTO sct_t VALUES (1);
                RETURN 'left open';
            END
            """);
        final RuntimeException scoped = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CALL p_open_txn()");
            }
        });
        assertTrue(scoped.getMessage().contains(
            "Scoped transaction started in stored procedure is incomplete and it was rolled back."),
            scoped.getMessage());
        assertEquals(0L, count("SELECT COUNT(*) FROM sct_t"), "the open transaction's writes roll back");
    }

    @Test
    public void committedInsideTransactionWorks() {
        engine.execute("CREATE TABLE sct_c (i INTEGER)");
        engine.execute("""
            CREATE PROCEDURE p_closed_txn() RETURNS VARCHAR LANGUAGE SQL AS
            BEGIN
                BEGIN TRANSACTION;
                INSERT INTO sct_c VALUES (2);
                COMMIT;
                RETURN 'committed';
            END
            """);
        engine.execute("CALL p_closed_txn()");
        assertEquals(1L, count("SELECT COUNT(*) FROM sct_c"));
    }

    @Test
    public void callerOpenedTransactionPassesThrough() {
        engine.execute("CREATE TABLE sct_o (i INTEGER)");
        engine.execute("""
            CREATE PROCEDURE p_no_txn() RETURNS VARCHAR LANGUAGE SQL AS
            BEGIN
                INSERT INTO sct_o VALUES (3);
                RETURN 'ok';
            END
            """);
        engine.execute("BEGIN TRANSACTION");
        engine.execute("CALL p_no_txn()");
        engine.execute("COMMIT");
        assertEquals(1L, count("SELECT COUNT(*) FROM sct_o"),
            "a transaction the CALLER opened is not scoped to the procedure");
    }

    /**
     * A procedure runs in the caller's session and may not reconfigure it. Live refuses the whole
     * family by statement type — every USE form is USE, a session parameter change is ALTER_SESSION,
     * and SET is SET — while SHOW, GRANT, DDL and COMMIT are all fine inside a body.
     */
    @Test
    public void aProcedureMayNotChangeTheSessionItRunsIn() {
        engine.execute("CREATE TABLE unsupported_sink (k INTEGER)");
        assertUnsupported("ALTER_SESSION", "ALTER SESSION SET AUTOCOMMIT = FALSE;");
        assertUnsupported("USE", "USE DATABASE test_db;");
        assertUnsupported("USE", "USE SCHEMA test_schema;");
        assertUnsupported("USE", "USE ROLE PUBLIC;");
        // SET is refused live too, but a bare SET inside a body reaches neither of the paths this
        // rule guards — it is a session variable the engine routes elsewhere. Left uncovered rather
        // than asserted against behaviour that has not been aligned.
    }

    /** The statements a procedure body may run are untouched by that rule. */
    @Test
    public void aProcedureStillRunsTheStatementsLiveAllows() {
        engine.execute("CREATE TABLE allowed_sink (k INTEGER)");
        for (final String body : List.of("SHOW TABLES;", "CREATE OR REPLACE TABLE MADE (K NUMBER);",
                "COMMIT;", "INSERT INTO allowed_sink VALUES (1);")) {
            engine.execute("CREATE OR REPLACE PROCEDURE allowed_proc() RETURNS STRING LANGUAGE SQL AS $$"
                + " BEGIN " + body + " RETURN 'ok'; END $$");
            assertEquals("ok", engine.executeQuery("CALL allowed_proc()")
                .getRows().get(0).getValue(0), "a procedure must still run: " + body);
        }
    }

    /** The session change must be refused even when it is nested inside a BEGIN…END body. */
    private void assertUnsupported(final String statementType, final String body) {
        engine.execute("CREATE OR REPLACE PROCEDURE unsupported_proc() RETURNS STRING LANGUAGE SQL AS $$"
            + " BEGIN " + body + " RETURN 'ok'; END $$");
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("CALL unsupported_proc()");
            }
        });
        assertTrue(error.getMessage().contains(
            "Stored procedure execution error: Unsupported statement type '" + statementType + "'."),
            "unexpected message for " + body + ": " + error.getMessage());
    }
}
