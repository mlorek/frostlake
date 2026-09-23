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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a CREATE does to the session's current database and schema.
 *
 * <p>★ CREATING SOMETHING MOVES YOU INTO IT. A CREATE DATABASE leaves the session in that database at
 * its PUBLIC schema; a CREATE SCHEMA leaves it in that schema. Frostlake already did all of this — the
 * task that prompted these cells asked whether it did, and the answer was yes on every one, which is
 * why they are pinned here rather than fixed.
 *
 * <p>★ A QUALIFIED CREATE SCHEMA MOVES THE DATABASE TOO. {@code CREATE SCHEMA other_db.s} relocates the
 * session into other_db, so every unqualified name after it resolves somewhere else entirely. That is
 * the cell worth knowing about: a migration script can depend on it without ever saying so.
 *
 * <p>★ ONLY AN ACTUAL CREATION SWITCHES. An IF NOT EXISTS that creates nothing leaves the context
 * alone, and so does a CREATE that FAILS — the switch is the creation's effect, not the statement's.
 *
 * <p>★ AND A DROP MOVES YOU OUT, asymmetrically: dropping the current SCHEMA falls back to PUBLIC with
 * the database unchanged, while dropping the current DATABASE leaves the session with NO context at all
 * — both CURRENT_DATABASE() and CURRENT_SCHEMA() answer NULL. Frostlake left the dropped name current,
 * which is a pair that names nothing and fails the next time anything resolves an unqualified name.
 *
 * <p>NOT FIXED HERE: {@code CREATE TRANSIENT DATABASE} and {@code CREATE TRANSIENT SCHEMA} are syntax
 * errors, though live accepts both and switches into them — tracked on its own, because accepting the
 * word without modelling what it means would trade a syntax error for a silent lie.
 */
public class CreateContextSwitchTest extends BaseDatabaseTest {

    /** The session's current database and schema, as one string. */
    private String context() {
        final ResultSet rs = engine.executeQuery("SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()");
        rs.next();
        return String.valueOf(rs.getValue(0)) + "." + String.valueOf(rs.getValue(1));
    }

    /** The refusal a statement gives, or ACCEPTED. */
    private String answer(final String sql) {
        try {
            engine.execute(sql);
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** Put the session back in test_db and drop whatever the test made — live is stateful. */
    private void cleanUp(final String... databases) {
        for (final String db : databases) {
            try {
                engine.execute("DROP DATABASE IF EXISTS " + db);
            } catch (final RuntimeException ignored) {
                // best-effort: a test that already dropped it is fine
            }
        }
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA test_schema");
    }

    /** ★ Creating a database moves the session into it, at PUBLIC. */
    @Test
    public void acreateDatabaseMovesTheSessionIntoIt() {
        try {
            assertEquals("TEST_DB.TEST_SCHEMA", context(), "the baseline this starts from");
            engine.execute("CREATE DATABASE fl434a_one");
            assertEquals("FL434A_ONE.PUBLIC", context());
            engine.execute("CREATE DATABASE fl434a_two");
            assertEquals("FL434A_TWO.PUBLIC", context(), "and the next one moves it again");
        } finally {
            cleanUp("fl434a_one", "fl434a_two");
        }
    }

    /** ★ Creating a schema moves the session into it — and a QUALIFIED one moves the database too. */
    @Test
    public void acreateSchemaMovesTheSessionIntoIt() {
        try {
            engine.execute("CREATE DATABASE fl434b_one");
            engine.execute("CREATE DATABASE fl434b_two");
            engine.execute("CREATE SCHEMA fl434b_s1");
            assertEquals("FL434B_TWO.FL434B_S1", context());
            engine.execute("CREATE SCHEMA fl434b_s2");
            assertEquals("FL434B_TWO.FL434B_S2", context());
            engine.execute("CREATE SCHEMA fl434b_one.fl434b_s3");
            assertEquals("FL434B_ONE.FL434B_S3", context(),
                "★ a schema created INTO another database relocates the session there");
            engine.execute("CREATE OR REPLACE SCHEMA fl434b_s4");
            assertEquals("FL434B_ONE.FL434B_S4", context(), "replacing switches like creating");
        } finally {
            cleanUp("fl434b_one", "fl434b_two");
        }
    }

    /** ★ A creation that does not happen does not switch — IF NOT EXISTS, and a failure. */
    @Test
    public void acreationThatDoesNotHappenDoesNotSwitch() {
        try {
            engine.execute("CREATE DATABASE fl434c_one");
            engine.execute("CREATE SCHEMA fl434c_s1");
            engine.execute("CREATE SCHEMA fl434c_s2");
            engine.execute("USE SCHEMA fl434c_s1");
            assertEquals("FL434C_ONE.FL434C_S1", context());
            assertEquals("ACCEPTED", answer("CREATE SCHEMA IF NOT EXISTS fl434c_s2"));
            assertEquals("FL434C_ONE.FL434C_S1", context(),
                "IF NOT EXISTS created nothing, so it moved nothing");
            assertEquals("SQL compilation error:|Object 'FL434C_S2' already exists.",
                answer("CREATE SCHEMA fl434c_s2"));
            assertEquals("FL434C_ONE.FL434C_S1", context(), "and a refused create leaves it alone");
            assertEquals("SQL compilation error:|Object 'FL434C_ONE' already exists.",
                answer("CREATE DATABASE fl434c_one"));
            assertEquals("FL434C_ONE.FL434C_S1", context());
        } finally {
            cleanUp("fl434c_one");
        }
    }

    /** A schema created into a database that is not there refuses, and moves nothing. */
    @Test
    public void aqualifiedCreateIntoAMissingDatabaseRefuses() {
        try {
            engine.execute("CREATE DATABASE fl434d_one");
            assertEquals("FL434D_ONE.PUBLIC", context());
            assertEquals(hinted("SQL compilation error:|Database 'FL434D_MISSING' does not exist"
                + " or not authorized."),
                answer("CREATE SCHEMA fl434d_missing.s"));
            assertEquals("FL434D_ONE.PUBLIC", context());
        } finally {
            cleanUp("fl434d_one");
        }
    }

    /** ★ Dropping the current SCHEMA falls back to PUBLIC, the database unchanged. */
    @Test
    public void droppingTheCurrentSchemaFallsBackToPublic() {
        try {
            engine.execute("CREATE DATABASE fl434e_one");
            engine.execute("CREATE SCHEMA fl434e_s1");
            assertEquals("FL434E_ONE.FL434E_S1", context());
            engine.execute("DROP SCHEMA fl434e_s1");
            assertEquals("FL434E_ONE.PUBLIC", context());
        } finally {
            cleanUp("fl434e_one");
        }
    }

    /** ★ Dropping the current DATABASE leaves the session with no context at all. */
    @Test
    public void droppingTheCurrentDatabaseClearsBoth() {
        try {
            engine.execute("CREATE DATABASE fl434f_one");
            assertEquals("FL434F_ONE.PUBLIC", context());
            engine.execute("DROP DATABASE fl434f_one");
            assertEquals("null.null", context());
        } finally {
            cleanUp("fl434f_one");
        }
    }

    /**
     * ★ And with NO context at all, a fully qualified name still resolves — which is the thing that
     * makes clearing the context safe. A two-part name still needs the session, because its database
     * has to come from somewhere.
     */
    @Test
    public void afullyQualifiedNameNeedsNoContext() {
        try {
            engine.execute("CREATE DATABASE fl434i_one");
            engine.execute("CREATE SCHEMA fl434i_one.fl434i_s1");
            engine.execute("CREATE TABLE fl434i_one.fl434i_s1.t (id INT)");
            engine.execute("INSERT INTO fl434i_one.fl434i_s1.t VALUES (1)");
            engine.execute("CREATE DATABASE fl434i_two");
            engine.execute("DROP DATABASE fl434i_two");
            assertEquals("null.null", context(), "the session is left with nothing");
            final ResultSet rs = engine.executeQuery(
                "SELECT COUNT(*) FROM fl434i_one.fl434i_s1.t");
            rs.next();
            assertEquals("1", String.valueOf(rs.getValue(0)),
                "and the fully qualified read still works");
        } finally {
            cleanUp("fl434i_one", "fl434i_two");
        }
    }

    /** The switch ESCAPES a block, and SURVIVES a rollback — it is session state, not transactional. */
    @Test
    public void theswitchEscapesABlockAndSurvivesARollback() {
        try {
            engine.execute("CREATE DATABASE fl434g_one");
            engine.execute("BEGIN\n  CREATE SCHEMA fl434g_inblock;\n  RETURN 1;\nEND");
            assertEquals("FL434G_ONE.FL434G_INBLOCK", context(),
                "a schema created inside a block leaves the session there");
            engine.execute("BEGIN");
            engine.execute("CREATE SCHEMA fl434g_intxn");
            assertEquals("FL434G_ONE.FL434G_INTXN", context());
            engine.execute("ROLLBACK");
            assertEquals("FL434G_ONE.FL434G_INTXN", context(),
                "and rolling back does not put it back");
        } finally {
            cleanUp("fl434g_one");
        }
    }

    /** USE is the control: it moves the same way and is the only thing that used to. */
    @Test
    public void useIsTheControl() {
        try {
            engine.execute("CREATE DATABASE fl434h_one");
            engine.execute("CREATE SCHEMA fl434h_s1");
            engine.execute("USE DATABASE fl434h_one");
            assertEquals("FL434H_ONE.PUBLIC", context(), "USE DATABASE lands on PUBLIC");
            engine.execute("USE SCHEMA fl434h_s1");
            assertEquals("FL434H_ONE.FL434H_S1", context());
            engine.execute("USE SCHEMA fl434h_one.PUBLIC");
            assertEquals("FL434H_ONE.PUBLIC", context(), "and the qualified form works too");
        } finally {
            cleanUp("fl434h_one");
        }
    }
}
