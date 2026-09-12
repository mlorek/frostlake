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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * USE resolves its name exactly, as every other SQL reference does, and the session then carries the
 * object's stored name: a database or schema created with a quoted lower-case name is selected by that
 * name and reported by CURRENT_DATABASE() / CURRENT_SCHEMA() in its own case, while a quoted name in the
 * wrong case — or a bare one folding to a name nothing is stored under — selects nothing. Every failed USE
 * answers the same sentence, naming nothing, and leaves the context as it was. USE without an object kind
 * takes one part as a database and two as a schema. Every expectation is live-verified.
 */
public class UseStatementResolutionTest extends BaseDatabaseTest {

    private static final String MISSING = "Object does not exist, or operation cannot be performed.";

    /** These databases live outside test_db, so the per-test recreate never clears them. */
    @AfterEach
    public void dropOutsideDatabases() {
        engine.execute("USE DATABASE test_db");
        engine.execute("DROP DATABASE IF EXISTS \"use dsn db\"");
        engine.execute("DROP DATABASE IF EXISTS use_plain_db");
        engine.execute("DROP DATABASE IF EXISTS \"UseMiXed\"");
    }

    private String context() {
        final ResultSet result = engine.executeQuery("SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()");
        return result.getRows().get(0).getValue(0) + " / " + result.getRows().get(0).getValue(1);
    }

    private String count(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private RuntimeException refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
    }

    private void assertMissing(final String sql) {
        final String message = String.valueOf(refusal(sql).getMessage());
        assertTrue(message.contains("SQL compilation error:") && message.contains(MISSING), sql + " -> " + message);
    }

    @Test
    public void aQuotedLowerCaseNameIsSelectedAndReportedInItsOwnCase() {
        engine.execute("CREATE OR REPLACE DATABASE \"use dsn db\"");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE DATABASE \"use dsn db\"");
        assertEquals("use dsn db / PUBLIC", context());
        // CREATE SCHEMA activates the new schema, in its own case too.
        engine.execute("CREATE OR REPLACE SCHEMA \"low sch\"");
        assertEquals("use dsn db / low sch", context());
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("USE SCHEMA \"low sch\"");
        assertEquals("use dsn db / low sch", context());
        // Unqualified names resolve inside the selected pair, and reach the same rows as the full name.
        engine.execute("CREATE TABLE t1 (i INT)");
        engine.execute("INSERT INTO t1 VALUES (1), (2)");
        assertEquals("2", count("SELECT COUNT(*) FROM t1"));
        assertEquals("2", count("SELECT COUNT(*) FROM \"use dsn db\".\"low sch\".t1"));
        engine.execute("USE SCHEMA \"use dsn db\".PUBLIC");
        assertEquals("use dsn db / PUBLIC", context());
        engine.execute("USE SCHEMA \"use dsn db\".\"low sch\"");
        assertEquals("use dsn db / low sch", context());
        final ResultSet databases = engine.executeQuery("SHOW DATABASES LIKE 'use dsn db'");
        assertEquals(1, databases.getRowCount());
        assertEquals("Y", String.valueOf(databases.getRows().get(0).getValue(databases.getColumnIndex("is_current"))));
    }

    @Test
    public void aNameThatFoldsOrIsQuotedInTheWrongCaseSelectsNothing() {
        engine.execute("CREATE OR REPLACE DATABASE use_plain_db");
        assertEquals("USE_PLAIN_DB / PUBLIC", context());
        engine.execute("USE DATABASE test_db");
        assertMissing("USE DATABASE \"use_plain_db\"");
        assertMissing("USE SCHEMA \"public\"");
        assertMissing("USE SCHEMA test_db.\"public\"");
        assertEquals("TEST_DB / PUBLIC", context());
        engine.execute("USE DATABASE \"USE_PLAIN_DB\"");
        engine.execute("USE SCHEMA \"PUBLIC\"");
        assertEquals("USE_PLAIN_DB / PUBLIC", context());
        engine.execute("CREATE OR REPLACE DATABASE \"UseMiXed\"");
        assertEquals("UseMiXed / PUBLIC", context());
        engine.execute("USE DATABASE test_db");
        assertMissing("USE DATABASE usemixed");
        assertMissing("USE DATABASE \"usemixed\"");
        assertMissing("USE DATABASE \"USEMIXED\"");
        engine.execute("USE DATABASE \"UseMiXed\"");
        assertEquals("UseMiXed / PUBLIC", context());
    }

    @Test
    public void aFailedUseLeavesTheContextAsItWas() {
        engine.execute("CREATE OR REPLACE DATABASE \"use dsn db\"");
        engine.execute("USE DATABASE test_db");
        assertEquals("TEST_DB / PUBLIC", context());
        assertMissing("USE DATABASE use_nope_db");
        assertMissing("USE SCHEMA use_nope_sch");
        assertMissing("USE SCHEMA use_nope_db.public");
        // The database exists and the schema does not: the database does not move either.
        assertMissing("USE SCHEMA \"use dsn db\".nope");
        assertEquals("TEST_DB / PUBLIC", context());
    }

    @Test
    public void useWithoutAKindTakesOnePartAsADatabaseAndTwoAsASchema() {
        engine.execute("CREATE OR REPLACE DATABASE \"use dsn db\"");
        engine.execute("CREATE OR REPLACE SCHEMA \"low sch\"");
        engine.execute("USE test_db");
        assertEquals("TEST_DB / PUBLIC", context());
        engine.execute("USE \"use dsn db\"");
        assertEquals("use dsn db / PUBLIC", context());
        engine.execute("USE test_db.test_schema");
        assertEquals("TEST_DB / TEST_SCHEMA", context());
        engine.execute("USE \"use dsn db\".\"low sch\"");
        assertEquals("use dsn db / low sch", context());
        engine.execute("USE IDENTIFIER('\"use dsn db\"')");
        assertEquals("use dsn db / PUBLIC", context());
        // One part is always a database, never a schema of the current one.
        assertMissing("USE public");
        assertMissing("USE \"low sch\"");
        final String threeParts = String.valueOf(refusal("USE \"use dsn db\".\"low sch\".x").getMessage());
        assertTrue(threeParts.contains("error line USE <identifier> is of the form USE <db.schema> or USE <db> at position {1}"),
            threeParts);
        assertTrue(threeParts.contains("invalid identifier '{2}'"), threeParts);
        assertEquals("use dsn db / PUBLIC", context());
    }

    @Test
    public void droppingTheCurrentQuotedSchemaOrDatabaseMovesTheContext() {
        engine.execute("CREATE OR REPLACE DATABASE \"use dsn db\"");
        engine.execute("CREATE OR REPLACE SCHEMA \"low sch\"");
        assertEquals("use dsn db / low sch", context());
        engine.execute("DROP SCHEMA \"low sch\"");
        assertEquals("use dsn db / PUBLIC", context());
        engine.execute("CREATE SCHEMA \"low sch\"");
        engine.execute("DROP SCHEMA \"use dsn db\".PUBLIC");
        assertEquals("use dsn db / low sch", context());
        // With PUBLIC gone there is nowhere to fall back to.
        engine.execute("DROP SCHEMA \"low sch\"");
        assertEquals("use dsn db / null", context());
        engine.execute("DROP DATABASE \"use dsn db\"");
        assertEquals("null / null", context());
    }
}
