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

package dev.frostlake.transaction;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ALTER SESSION SET or UNSET AUTOCOMMIT COMMITS an open transaction — and it is the statement that
 * commits, not a change of value: setting AUTOCOMMIT to the value it already held commits just the
 * same. No other session parameter does this; TIMEZONE and QUERY_TAG leave the transaction open.
 *
 * <p>A later ROLLBACK is what makes it visible in one session: the inserted row is still there,
 * because there was nothing left to roll back.
 *
 * <p>CURRENT_TRANSACTION() answers that transaction's id as digits while one is open, and NULL
 * outside — so {@code CURRENT_TRANSACTION() IS NOT NULL} is the plain test for being in one. It used
 * to answer NULL always.
 */
public class AutocommitCommitsOpenTransactionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE tx (a INT)");
    }

    /** The first cell of a query, as text. */
    private String one(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** How many rows survive a ROLLBACK when the given statement ran inside the transaction. */
    private String survivingRows(final String statementInsideTransaction) {
        engine.execute("CREATE OR REPLACE TABLE tx (a INT)");
        engine.execute("BEGIN TRANSACTION");
        engine.execute("INSERT INTO tx VALUES (1)");
        engine.execute(statementInsideTransaction);
        engine.execute("ROLLBACK");
        final String count = one("SELECT COUNT(*) FROM tx");
        engine.execute("ALTER SESSION UNSET AUTOCOMMIT");
        return count;
    }

    /** Setting AUTOCOMMIT commits what is open, in either direction. */
    @Test
    public void settingAutocommitCommitsTheOpenTransaction() {
        assertEquals("1", survivingRows("ALTER SESSION SET AUTOCOMMIT = FALSE"));
        assertEquals("1", survivingRows("ALTER SESSION SET AUTOCOMMIT = TRUE"));
    }

    /** Unsetting it commits too. */
    @Test
    public void unsettingAutocommitCommitsTheOpenTransaction() {
        assertEquals("1", survivingRows("ALTER SESSION UNSET AUTOCOMMIT"));
    }

    /** Every other session parameter leaves the transaction alone. */
    @Test
    public void anotherParameterDoesNotCommit() {
        assertEquals("0", survivingRows("ALTER SESSION SET TIMEZONE = 'UTC'"));
        assertEquals("0", survivingRows("ALTER SESSION SET QUERY_TAG = 'q'"));
    }

    /** CURRENT_TRANSACTION() answers an id inside a transaction and NULL outside one. */
    @Test
    public void currentTransactionAnswersTheOpenTransaction() {
        assertEquals("false", one("SELECT CURRENT_TRANSACTION() IS NOT NULL"));
        engine.execute("BEGIN TRANSACTION");
        assertEquals("true", one("SELECT CURRENT_TRANSACTION() IS NOT NULL"));
        assertEquals("true", one("SELECT CURRENT_TRANSACTION() RLIKE '^[0-9]+$'"),
            "the id is decimal digits");
        engine.execute("ROLLBACK");
        assertEquals("false", one("SELECT CURRENT_TRANSACTION() IS NOT NULL"));
    }

    /** The id holds still while the transaction runs, and a second transaction gets its own. */
    @Test
    public void theIdIsStableWithinOneTransactionAndNewForTheNext() {
        engine.execute("BEGIN TRANSACTION");
        final String first = one("SELECT CURRENT_TRANSACTION()");
        engine.execute("INSERT INTO tx VALUES (1)");
        assertEquals(first, one("SELECT CURRENT_TRANSACTION()"));
        engine.execute("ROLLBACK");

        engine.execute("BEGIN TRANSACTION");
        final String second = one("SELECT CURRENT_TRANSACTION()");
        engine.execute("ROLLBACK");
        assertTrue(second.matches("[0-9]+"), "unexpected id: " + second);
        assertNotEquals(first, second, "a second transaction gets its own id");
    }

    /** And the commit leaves no transaction open — CURRENT_TRANSACTION() reads NULL right after. */
    @Test
    public void theCommitClosesTheTransaction() {
        engine.execute("BEGIN TRANSACTION");
        engine.execute("INSERT INTO tx VALUES (1)");
        engine.execute("ALTER SESSION SET AUTOCOMMIT = TRUE");
        assertEquals("false", one("SELECT CURRENT_TRANSACTION() IS NOT NULL"));
        engine.execute("ALTER SESSION UNSET AUTOCOMMIT");
    }
}
