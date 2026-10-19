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

import dev.frostlake.BaseJdbcTest;
import dev.frostlake.LiveSnowflake;
import dev.frostlake.jdbc.DirectConnection;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A global a JavaScript function sets in one session is not there for a function another session calls, nor
 * for the same session's next statement (live-verified): the two sessions run their statements one after another
 * on the test's thread, which is how one session's value reached another's function while every call on a thread
 * shared one JavaScript world.
 */
public class JavaScriptGlobalsAcrossSessionsTest extends BaseJdbcTest {

    /** Another session on the same account or engine as the test's own connection, in the test's schema. */
    private Connection another() throws SQLException {
        final Connection opened = LiveSnowflake.enabled() ? LiveSnowflake.open()
            : new DirectConnection(sharedEngine);
        try (Statement use = opened.createStatement()) {
            use.execute("USE SCHEMA test_db.public");
        }
        return opened;
    }

    private static String cell(final Statement on, final String sql) throws SQLException {
        try (ResultSet rs = on.executeQuery(sql)) {
            assertTrue(rs.next(), sql);
            return rs.getString(1);
        }
    }

    @Test
    public void aGlobalOneSessionSetsIsNotAnotherSessions() throws SQLException {
        statement.execute("""
            CREATE OR REPLACE FUNCTION js_set(v VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT
            AS $$ globalThis.shared = V; return 'set'; $$""");
        statement.execute("""
            CREATE OR REPLACE FUNCTION js_get(x FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT
            AS $$ return typeof globalThis.shared === 'undefined' ? 'nothing' : String(globalThis.shared); $$""");
        try (Connection other = another(); Statement theirs = other.createStatement()) {
            assertEquals("set", cell(statement, "SELECT js_set('session A value')"));
            assertEquals("nothing", cell(theirs, "SELECT js_get(1)"));
            assertEquals("nothing", cell(statement, "SELECT js_get(1)"));
            assertEquals("set", cell(theirs, "SELECT js_set('session B value')"));
            assertEquals("nothing", cell(statement, "SELECT js_get(1)"));
            assertEquals("nothing", cell(theirs, "SELECT js_get(1)"));
        }
    }
}
