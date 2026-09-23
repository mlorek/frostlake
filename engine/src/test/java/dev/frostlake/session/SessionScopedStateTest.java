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

package dev.frostlake.session;

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.ExecutionResult;
import dev.frostlake.http.SessionContext;
import dev.frostlake.storage.ResultSet;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * State that belongs to ONE session of a shared engine: the last transaction it ended, the statement it runs,
 * and a transaction it left open when it expired. Two sessions of one engine never read each other's.
 */
public class SessionScopedStateTest {

    private ConcurrentDatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new ConcurrentDatabaseEngine();
        final SessionContext setup = engine.createSession();
        engine.execute("CREATE OR REPLACE DATABASE ssdb", setup);
        engine.execute("CREATE OR REPLACE TABLE ssdb.public.t (a INT)", setup);
        engine.removeSession(setup.getSessionId());
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private Object one(final SessionContext session, final String sql) {
        final ExecutionResult result = engine.execute(sql, session);
        if (!result.isSuccess()) {
            throw new IllegalStateException(sql + " failed: " + result.getErrorMessage());
        }
        final List<ResultSet> sets = result.getResultSets();
        final ResultSet rs = sets.get(sets.size() - 1);
        rs.next();
        return rs.getValue(0);
    }

    /** B's LAST_TRANSACTION is B's: A committing one leaves B's NULL, and B's own ROLLBACK sets it. */
    @Test
    public void lastTransactionIsEachSessionsOwn() {
        final SessionContext a = engine.createSession();
        final SessionContext b = engine.createSession();
        assertNull(one(b, "SELECT LAST_TRANSACTION()"));
        engine.execute("BEGIN", a);
        engine.execute("INSERT INTO ssdb.public.t VALUES (1)", a);
        engine.execute("COMMIT", a);
        assertEquals(false, one(a, "SELECT LAST_TRANSACTION() IS NULL"));
        assertNull(one(b, "SELECT LAST_TRANSACTION()"));
        engine.execute("BEGIN", b);
        engine.execute("ROLLBACK", b);
        assertEquals(false, one(b, "SELECT LAST_TRANSACTION() IS NULL"));
    }

    /** Each session reads its own statement. */
    @Test
    public void currentStatementIsEachSessionsOwn() {
        final SessionContext b = engine.createSession();
        assertEquals("SELECT CURRENT_STATEMENT()", one(b, "SELECT CURRENT_STATEMENT()"));
    }

    /** An expired session's open transaction is rolled back, as a released one's is. */
    @Test
    public void anExpiredSessionsTransactionIsRolledBack() {
        final SessionContext idle = engine.createSession();
        engine.execute("BEGIN", idle);
        engine.execute("INSERT INTO ssdb.public.t VALUES (99)", idle);
        final SessionContext other = engine.createSession();
        assertEquals(1, engine.expireIdleSessions(-1L) >= 1 ? 1 : 0);
        assertEquals(0L, ((Number) one(other, "SELECT COUNT(*) FROM ssdb.public.t WHERE a = 99")).longValue());
        engine.execute("SHOW TRANSACTIONS", other);
        assertEquals(0L, ((Number) one(other,
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))")).longValue());
    }
}
