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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.FrostlakeJdbc;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ALTER SESSION SET/UNSET observed through {@code SHOW PARAMETERS} — the SQL surface a real
 * account exposes, so the value round-trips run on every transport. The
 * {@code MULTI_STATEMENT_COUNT} cells stay on the plain embedded engine only: on a gated
 * transport (live, or the direct driver) a session count of 5 refuses every follow-up
 * single-statement request including the read-back, and {@code JdbcMultiStatementTest} owns
 * that two-sided surface via statement-scoped parameters. Engine-internal typing of stored
 * values (Long/Boolean/String) and {@code SessionContext.reset()} are pinned embedded-only.
 */
public class AlterSessionTest extends BaseDatabaseTest {

    /** The {@code value} column of {@code SHOW PARAMETERS LIKE '<name>'} (session scope). */
    private String parameterValue(final String name) {
        final ResultSet rs = engine.executeQuery("SHOW PARAMETERS LIKE '" + name + "'");
        assertEquals(1, rs.getRowCount(), name);
        return String.valueOf(rs.getRows().get(0).getValue(1));
    }

    /** True when statements ride a transport whose multi-statement gate refuses lone reads. */
    private static boolean multiStatementGated() {
        return isLiveSnowflake() || FrostlakeJdbc.enabled();
    }

    @Test
    public void queryTagRoundTripsThroughShowParameters() {
        engine.execute("ALTER SESSION SET QUERY_TAG = 'test_query'");
        assertEquals("test_query", parameterValue("QUERY_TAG"));
        engine.execute("ALTER SESSION UNSET QUERY_TAG");
        assertEquals("", parameterValue("QUERY_TAG"));
    }

    @Test
    public void numericParameterRoundTripsThroughShowParameters() {
        engine.execute("ALTER SESSION SET STATEMENT_TIMEOUT_IN_SECONDS = 3600");
        assertEquals("3600", parameterValue("STATEMENT_TIMEOUT_IN_SECONDS"));
        engine.execute("ALTER SESSION UNSET STATEMENT_TIMEOUT_IN_SECONDS");
    }

    @Test
    public void booleanParameterRoundTripsThroughShowParameters() {
        engine.execute("ALTER SESSION SET AUTOCOMMIT = TRUE");
        assertEquals("true", parameterValue("AUTOCOMMIT"));
    }

    @Test
    public void sessionParametersPersistAcrossStatements() {
        engine.execute("ALTER SESSION SET QUERY_TAG = 'persist_tag'");
        engine.execute("CREATE TABLE alter_session_t (id INTEGER)");
        assertEquals("persist_tag", parameterValue("QUERY_TAG"));
        engine.execute("ALTER SESSION UNSET QUERY_TAG");
    }

    @Test
    public void multiStatementCountRoundTripsThroughShowParameters() {
        if (multiStatementGated()) {
            return;
        }
        assertEquals("1", parameterValue("MULTI_STATEMENT_COUNT"));
        engine.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
        assertEquals("0", parameterValue("MULTI_STATEMENT_COUNT"));
        engine.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 5");
        assertEquals("5", parameterValue("MULTI_STATEMENT_COUNT"));
        engine.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 10");
        assertEquals("10", parameterValue("MULTI_STATEMENT_COUNT"));
        engine.execute("ALTER SESSION UNSET MULTI_STATEMENT_COUNT");
        assertEquals("1", parameterValue("MULTI_STATEMENT_COUNT"));
    }

    @Test
    public void storedSessionParameterValuesKeepTheirTypes() {
        if (multiStatementGated()) {
            return;
        }
        engine.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 5");
        assertEquals(5L,
            engine.getSessionContext().getSessionParameter("MULTI_STATEMENT_COUNT"));
        engine.execute("ALTER SESSION SET QUERY_TAG = 'test'");
        assertEquals("test", engine.getSessionContext().getSessionParameter("QUERY_TAG"));
        engine.execute("ALTER SESSION SET AUTOCOMMIT = FALSE");
        assertEquals(false, engine.getSessionContext().getSessionParameter("AUTOCOMMIT"));

        final var params = engine.getSessionContext().getAllSessionParameters();
        assertNotNull(params);
        assertEquals(5L, params.get("MULTI_STATEMENT_COUNT"));
        assertEquals("test", params.get("QUERY_TAG"));
        assertEquals(false, params.get("AUTOCOMMIT"));
    }

    @Test
    public void sessionResetRestoresDefaults() {
        if (multiStatementGated()) {
            return;
        }
        engine.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 5");
        engine.execute("ALTER SESSION SET QUERY_TAG = 'test'");
        engine.getSessionContext().reset();
        assertEquals(1, engine.getSessionContext().getSessionParameter("MULTI_STATEMENT_COUNT"));
        assertNull(engine.getSessionContext().getSessionParameter("QUERY_TAG"));
    }
}
