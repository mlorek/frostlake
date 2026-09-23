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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code WITH PRIVILEGES} names privileges the account KNOWS, and keeps only the objects the session's
 * role holds one of them on. Frostlake took any identifier as a privilege and then ignored the
 * modifier entirely, so a listing filtered by a privilege that does not exist answered everything —
 * the opposite of what the modifier is for.
 *
 * <p>The filter reads the privilege NAMED and nothing else. Owning a schema is not USAGE on it, which
 * is why the owned schemas below answer nothing.
 */
public class ShowWithPrivilegesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("CREATE OR REPLACE SCHEMA wp1");
        engine.execute("CREATE OR REPLACE SCHEMA wp2");
        engine.execute("USE SCHEMA test_schema");
    }

    /** The refusal a statement raises, or "OK <n> rows". */
    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            int rows = 0;
            while (rs.next()) {
                rows++;
            }
            return "OK " + rows + " rows";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** The names a listing answers. */
    private String namesOf(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        while (rs.next()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            out.append(String.valueOf(rs.getValue("name")));
        }
        return out.toString();
    }

    /** A word that is no privilege is a syntax error where it stands. */
    @Test
    public void aWordThatIsNoPrivilegeIsRefused() {
        assertEquals("SQL compilation error: syntax error line 1 at position 31 unexpected 'BOGUS'.",
            outcome("SHOW DATABASES WITH PRIVILEGES BOGUS"));
        assertEquals("SQL compilation error: syntax error line 1 at position 28 unexpected 'BOGUS'.",
            outcome("SHOW TABLES WITH PRIVILEGES BOGUS"));
        assertEquals("SQL compilation error: syntax error line 1 at position 29"
            + " unexpected '\"USAGE\"'.",
            outcome("SHOW SCHEMAS WITH PRIVILEGES \"USAGE\""),
            "a quoted word is not a privilege name");
    }

    /** A privilege whose name is two words reads as one privilege. */
    @Test
    public void aTwoWordPrivilegeReads() {
        assertEquals("OK 0 rows", outcome("SHOW SCHEMAS WITH PRIVILEGES CREATE TABLE"));
    }

    /** Owning a schema is not USAGE on it, so the owned schemas are filtered out. */
    @Test
    public void owningIsNotUsage() {
        assertTrue(namesOf("SHOW SCHEMAS").contains("WP1"), "the schemas are there to begin with");
        assertEquals("OK 0 rows", outcome("SHOW SCHEMAS WITH PRIVILEGES USAGE"));
    }

    /** OWNERSHIP does answer for what the role owns. */
    @Test
    public void ownershipAnswersForWhatTheRoleOwns() {
        final String owned = namesOf("SHOW SCHEMAS WITH PRIVILEGES OWNERSHIP");
        assertTrue(owned.contains("WP1") && owned.contains("WP2"), owned);
    }

}
