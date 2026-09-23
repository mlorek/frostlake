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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The scoped query-history table functions, each listing QUERY_HISTORY's columns for one session, one user
 * or one warehouse — the caller's by default — and each reachable only under INFORMATION_SCHEMA.
 */
public class QueryHistoryScopedTest extends BaseDatabaseTest {

    private String one(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** BY_SESSION lists the caller's own statements. */
    @Test
    public void bySessionListsTheCallersStatements() {
        engine.execute("SELECT 'marker-by-session'");
        assertEquals("1", one("SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) "
            + "WHERE QUERY_TEXT = 'SELECT ''marker-by-session'''"));
        assertEquals("true", one("SELECT TO_VARCHAR(SESSION_ID) = CURRENT_SESSION() "
            + "FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) ORDER BY START_TIME DESC LIMIT 1"));
    }

    /** The session may be named, by argument name or by position, and an unknown one lists nothing. */
    @Test
    public void theSessionMayBeNamed() {
        assertEquals("true", one("SELECT COUNT(*) > 0 FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION("
            + "SESSION_ID => CURRENT_SESSION()::NUMBER))"));
        assertEquals("true", one("SELECT COUNT(*) > 0 FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION("
            + "CURRENT_SESSION()::NUMBER))"));
        assertEquals("0", one("SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION("
            + "SESSION_ID => 1))"));
    }

    /** RESULT_LIMIT caps it, as it caps QUERY_HISTORY. */
    @Test
    public void resultLimitCapsIt() {
        engine.execute("SELECT 1");
        engine.execute("SELECT 2");
        engine.execute("SELECT 3");
        assertEquals("2", one("SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION("
            + "RESULT_LIMIT => 2))"));
    }

    /** BY_USER lists the calling user's statements by default, and takes the user by name or position. */
    @Test
    public void byUserDefaultsToTheCaller() {
        assertEquals("true", one("SELECT COUNT(*) > 0 FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_USER()) "
            + "WHERE USER_NAME = CURRENT_USER()"));
        assertEquals("true", one("SELECT COUNT(*) > 0 FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_USER("
            + "USER_NAME => CURRENT_USER()))"));
        assertEquals("true", one("SELECT COUNT(*) > 0 FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_USER("
            + "CURRENT_USER()))"));
    }

    /** BY_WAREHOUSE takes the warehouse by name. */
    @Test
    public void byWarehouseTakesTheWarehouse() {
        assertEquals("true", one("SELECT COUNT(*) >= 0 FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_WAREHOUSE("
            + "WAREHOUSE_NAME => CURRENT_WAREHOUSE()))"));
    }
}
