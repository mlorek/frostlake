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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A session's LAST_QUERY_ID and the results RESULT_SCAN reads are its own to keep: however many other sessions run
 * statements beside it, and however many statements another session runs, its last query and that query's result
 * stay, as the account keeps them; the result of a session that ended is still readable by its ID.
 */
public class SessionResultHistoryTest {

    private ConcurrentDatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new ConcurrentDatabaseEngine();
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

    @Test
    public void otherSessionsEvictNothingOfASessions() {
        final SessionContext a = engine.createSession();
        assertEquals("42", String.valueOf(one(a, "SELECT 42 AS answer")));
        assertEquals("42", String.valueOf(one(a, "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))")));
        final List<SessionContext> others = new ArrayList<SessionContext>();
        for (int i = 0; i < 300; i++) {
            final SessionContext other = engine.createSession();
            one(other, "SELECT " + i);
            others.add(other);
        }
        assertNotNull(one(a, "SELECT LAST_QUERY_ID()"));
        assertEquals("43", String.valueOf(one(a, "SELECT 43 AS answer")));
        final SessionContext busy = others.get(0);
        for (int i = 0; i < 600; i++) {
            one(busy, "SELECT " + i);
        }
        assertEquals("43", String.valueOf(one(a, "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))")));
    }

    @Test
    public void anEndedSessionsResultIsStillReadableByItsId() {
        final SessionContext a = engine.createSession();
        final SessionContext b = engine.createSession();
        assertEquals("7", String.valueOf(one(b, "SELECT 7 AS seven")));
        final String id = String.valueOf(one(b, "SELECT LAST_QUERY_ID()"));
        engine.endSession(b);
        assertEquals("7", String.valueOf(one(a, "SELECT * FROM TABLE(RESULT_SCAN('" + id + "'))")));
    }
}
