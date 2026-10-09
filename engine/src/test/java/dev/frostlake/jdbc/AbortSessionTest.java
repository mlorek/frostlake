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
package dev.frostlake.jdbc;

import dev.frostlake.BaseJdbcTest;
import dev.frostlake.LiveSnowflake;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SYSTEM$ABORT_SESSION ends the session whose CURRENT_SESSION() it is given (live-verified): the function answers
 * {@code session [<number>] terminated.}, the session's open transaction never commits, and every later statement
 * on it is refused with "Session no longer exists.  New login required to access the service." The engine rolls
 * the transaction back as the aborting statement ends, as it does for a released session; the account leaves it
 * running, its locks held, until SYSTEM$ABORT_TRANSACTION or its own idle limit ends it. A number
 * no session has — zero and a negative one included — answers the same sentence and ends nothing; NULL, text that
 * is no number, a number with a fraction and a boolean are refused.
 */
public class AbortSessionTest extends BaseJdbcTest {

    private static final String GONE = "Session no longer exists.  New login required to access the service.";

    /** Another session on the same account or engine as the test's own connection. */
    private Connection another() throws SQLException {
        return LiveSnowflake.enabled() ? LiveSnowflake.open() : new DirectConnection(sharedEngine);
    }

    private static String cell(final Statement on, final String sql) throws SQLException {
        try (ResultSet rs = on.executeQuery(sql)) {
            assertTrue(rs.next(), sql);
            return rs.getString(1);
        }
    }

    private String refusal(final Statement on, final String sql) {
        return assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                on.execute(sql);
            }
        }, sql).getMessage();
    }

    @Test
    public void anAbortedSessionRefusesEveryLaterStatement() throws SQLException {
        try (Connection other = another(); Statement on = other.createStatement()) {
            final String number = cell(on, "SELECT CURRENT_SESSION()");
            try (ResultSet rs = statement.executeQuery("SELECT SYSTEM$ABORT_SESSION(" + number + ")")) {
                assertEquals("SYSTEM$ABORT_SESSION(" + number + ")", rs.getMetaData().getColumnLabel(1));
                assertTrue(rs.next());
                assertEquals("session [" + number + "] terminated.", rs.getString(1));
            }
            assertTrue(refusal(on, "SELECT 1").contains(GONE));
            assertTrue(refusal(on, "SELECT CURRENT_SESSION()").contains(GONE));
            assertFalse(other.isValid(5));
            // The aborting session lives on, and aborting the same number again answers the same sentence.
            assertEquals("session [" + number + "] terminated.",
                cell(statement, "SELECT SYSTEM$ABORT_SESSION('" + number + "')"));
            assertEquals("1", cell(statement, "SELECT 1"));
        }
    }

    @Test
    public void theAbortedSessionsTransactionNeverCommits() throws SQLException {
        statement.execute("CREATE TABLE test_db.public.abort_t (k INT, v INT)");
        statement.execute("INSERT INTO test_db.public.abort_t VALUES (1, 1)");
        try (Connection other = another(); Statement on = other.createStatement()) {
            on.execute("BEGIN");
            on.execute("UPDATE test_db.public.abort_t SET v = 2 WHERE k = 1");
            final String number = cell(on, "SELECT CURRENT_SESSION()");
            final String transaction = cell(on, "SELECT CURRENT_TRANSACTION()");
            statement.execute("SELECT SYSTEM$ABORT_SESSION(" + number + ")");
            assertTrue(refusal(on, "COMMIT").contains(GONE));
            assertEquals("1", cell(statement, "SELECT v FROM test_db.public.abort_t"));
            if (isLiveSnowflake()) {
                // The account leaves the dead session's transaction running, the row locked, until it is ended.
                statement.execute("SELECT SYSTEM$ABORT_TRANSACTION(" + transaction + ")");
            }
            assertEquals(1, statement.executeUpdate("UPDATE test_db.public.abort_t SET v = 3 WHERE k = 1"));
            assertEquals("3", cell(statement, "SELECT v FROM test_db.public.abort_t"));
        }
    }

    @Test
    public void aSessionThatAbortsItselfGetsItsAnswerFirst() throws SQLException {
        try (Connection other = another(); Statement on = other.createStatement()) {
            final String number = cell(on, "SELECT CURRENT_SESSION()");
            assertEquals("session [" + number + "] terminated.",
                cell(on, "SELECT SYSTEM$ABORT_SESSION(CURRENT_SESSION())"));
            assertTrue(refusal(on, "SELECT 1").contains(GONE));
        }
    }

    @Test
    public void aNumberNoSessionHasAnswersTheSameAndEndsNothing() throws SQLException {
        assertEquals("session [1234567890123456] terminated.",
            cell(statement, "SELECT SYSTEM$ABORT_SESSION(1234567890123456)"));
        assertEquals("session [0] terminated.", cell(statement, "SELECT SYSTEM$ABORT_SESSION(0)"));
        assertEquals("session [-1] terminated.", cell(statement, "SELECT SYSTEM$ABORT_SESSION(-1)"));
        assertEquals("session [1234567890123456] terminated.",
            cell(statement, "SELECT SYSTEM$ABORT_SESSION('1234567890123456')"));
        // A number wider than 64 bits wraps into them.
        assertEquals("session [7766279631452241919] terminated.",
            cell(statement, "SELECT SYSTEM$ABORT_SESSION(99999999999999999999)"));
        assertEquals("session [1234567890123456] terminated.",
            cell(statement, "CALL SYSTEM$ABORT_SESSION(1234567890123456)"));
        assertEquals("1", cell(statement, "SELECT 1 WHERE SYSTEM$ABORT_SESSION(1234567890123456) IS NOT NULL"));
        assertEquals("1", cell(statement, "SELECT 1"));
    }

    @Test
    public void whatIsNoSessionNumberIsRefused() {
        assertTrue(refusal(statement, "SELECT SYSTEM$ABORT_SESSION(NULL)").contains("inputs may not be null"));
        assertTrue(refusal(statement, "SELECT SYSTEM$ABORT_SESSION('abc')").contains(
            "argument 0 to function SqlIdentifier{qualifierNames=[], identifierName=SYSTEM$ABORT_SESSION} needs to"
                + " be constant, found 'TO_NUMBER('abc', 18, 0)'"));
        assertTrue(refusal(statement, "SELECT SYSTEM$ABORT_SESSION(1.5)").contains(
            "needs to be constant, found 'CAST(1.5 AS NUMBER(18,0))'"));
        assertTrue(refusal(statement, "SELECT SYSTEM$ABORT_SESSION(TRUE)").contains(
            "Invalid argument types for function 'SYSTEM$ABORT_SESSION': (BOOLEAN)"));
        assertTrue(refusal(statement, "SELECT SYSTEM$ABORT_SESSION(1, 2)").contains(
            "too many arguments for function [SYSTEM$ABORT_SESSION(1, 2)] expected 1, got 2"));
        assertTrue(refusal(statement, "SELECT SYSTEM$ABORT_SESSION()").contains(
            "not enough arguments for function [SYSTEM$ABORT_SESSION()], expected 1, got 0"));
    }
}
