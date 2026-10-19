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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The listing modifiers and scopes of SHOW GRANTS, SHOW PARAMETERS and SHOW ACCOUNTS. The engine read none of them
 * and refused each at its first word, or took scopes the account does not.
 *
 * <p>On the account every grant listing reads a LIMIT and its count, and a LIKE on the bare form that narrows
 * nothing; PARAMETERS reads no modifier and is asked about the session, the account, a user, a database, a schema,
 * a warehouse, a table, a stream or a task, a container without a name being the session's current one; ACCOUNTS
 * takes no scope. What the listing does not read is refused where the account's parser stops. Every cell is
 * live-verified.
 */
public class ShowGrantsParametersTailTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t1 (a INT)");
    }

    /** The statement's refusal with its lines joined, or "ok". */
    private String outcome(final String sql) {
        try {
            engine.executeQuery(sql);
            return "ok";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private int rows(final String sql) {
        return engine.executeQuery(sql).getRows().size();
    }

    private static String syntax(final int position, final String token) {
        return "SQL compilation error:|syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    private static String cannotShow(final String qualifiers, final String name) {
        return "SQL compilation error:|Unsupported statement type 'Cannot show parameters of SqlIdentifier{"
            + "qualifierNames=[" + qualifiers + "], identifierName=" + name + "}'.";
    }

    @Test
    public void aGrantListingReadsALimitAndItsCount() {
        assertEquals("ok", outcome("SHOW GRANTS LIMIT 1"));
        assertTrue(rows("SHOW GRANTS LIMIT 1") <= 1);
        assertEquals("ok", outcome("SHOW GRANTS ON TABLE t1 LIMIT 1"));
        assertEquals(1, rows("SHOW GRANTS ON TABLE t1 LIMIT 1"));
        assertEquals("ok", outcome("SHOW GRANTS TO ROLE PUBLIC LIMIT 1"));
        assertEquals("ok", outcome("SHOW GRANTS OF ROLE PUBLIC LIMIT 1"));
        assertEquals(syntax(17, "<EOF>"), outcome("SHOW GRANTS LIMIT"));
        assertEquals(syntax(32, "<EOF>"), outcome("SHOW GRANTS TO ROLE PUBLIC LIMIT"));
        assertEquals(syntax(20, "FROM"), outcome("SHOW GRANTS LIMIT 1 FROM 'A'"));
        assertEquals(syntax(32, "FROM"), outcome("SHOW GRANTS ON TABLE t1 LIMIT 1 FROM 'A'"));
        assertEquals("page size \"0\" must be greater than 0 in limit clause", outcome("SHOW GRANTS LIMIT 0"));
        assertEquals("page size \"0\" must be greater than 0 in limit clause",
            outcome("SHOW GRANTS ON TABLE t1 LIMIT 0"));
        assertEquals(syntax(18, "-"), outcome("SHOW GRANTS LIMIT -1"));
        assertEquals(syntax(18, "'a'"), outcome("SHOW GRANTS LIMIT 'a'"));
        assertEquals(syntax(20, "LIMIT"), outcome("SHOW GRANTS LIMIT 1 LIMIT 2"));
    }

    @Test
    public void aGrantListingReadsNoOtherModifier() {
        assertEquals(syntax(16, "<EOF>"), outcome("SHOW GRANTS WITH"));
        assertEquals(syntax(17, "PRIVILEGES"), outcome("SHOW GRANTS WITH PRIVILEGES USAGE"));
        assertEquals(syntax(29, "PRIVILEGES"), outcome("SHOW GRANTS ON TABLE t1 WITH PRIVILEGES USAGE"));
        assertEquals(syntax(24, "<EOF>"), outcome("SHOW GRANTS LIMIT 1 WITH"));
        assertEquals(syntax(12, "STARTS") + "|syntax error line 1 at position 24 unexpected ''A''.",
            outcome("SHOW GRANTS STARTS WITH 'A'"));
        assertEquals(syntax(12, "STARTS"), outcome("SHOW GRANTS STARTS"));
        assertEquals(syntax(24, "STARTS"), outcome("SHOW GRANTS ON TABLE t1 STARTS WITH 'A'"));
        assertEquals(syntax(20, "STARTS"), outcome("SHOW GRANTS LIMIT 1 STARTS WITH 'A'"));
        assertEquals(syntax(12, "ROOT"), outcome("SHOW GRANTS ROOT ONLY"));
    }

    @Test
    public void theBareListingReadsALikeThatNarrowsNothing() {
        assertEquals(rows("SHOW GRANTS"), rows("SHOW GRANTS LIKE 'NO_SUCH_GRANT_ANYWHERE%'"));
        assertEquals("ok", outcome("SHOW GRANTS LIKE 'A%' LIMIT 1"));
        assertEquals(syntax(16, "<EOF>"), outcome("SHOW GRANTS LIKE"));
        assertEquals(syntax(22, "LIKE"), outcome("SHOW GRANTS LIKE 'A%' LIKE 'B%'"));
        assertEquals(syntax(20, "LIKE"), outcome("SHOW GRANTS LIMIT 1 LIKE 'A%'"));
        assertEquals(syntax(24, "LIKE"), outcome("SHOW GRANTS ON TABLE t1 LIKE 'A%'"));
        assertEquals(syntax(27, "LIKE"), outcome("SHOW GRANTS TO ROLE PUBLIC LIKE 'A%'"));
    }

    @Test
    public void nothingFollowsAnInAfterGrants() {
        assertEquals(syntax(14, "<EOF>"), outcome("SHOW GRANTS IN"));
        assertEquals(syntax(15, "ACCOUNT"), outcome("SHOW GRANTS IN ACCOUNT"));
        assertEquals(syntax(15, "abc"), outcome("SHOW GRANTS IN abc"));
        assertEquals(syntax(15, "SCHEMA"), outcome("SHOW GRANTS IN SCHEMA abc"));
        assertEquals(syntax(15, "DATABASE"), outcome("SHOW GRANTS IN DATABASE"));
    }

    @Test
    public void theOtherGrantListingsReadALimitToo() {
        engine.execute("CREATE DATABASE ROLE st_dbrole");
        assertEquals("ok", outcome("SHOW GRANTS TO DATABASE ROLE st_dbrole LIMIT 1"));
        assertEquals("ok", outcome("SHOW GRANTS OF DATABASE ROLE st_dbrole LIMIT 1"));
        assertEquals("ok", outcome("SHOW GRANTS ON DATABASE ROLE st_dbrole LIMIT 1"));
        assertEquals(1, rows("SHOW GRANTS ON DATABASE ROLE st_dbrole LIMIT 1"));
        assertEquals(syntax(44, "<EOF>"), outcome("SHOW GRANTS TO DATABASE ROLE st_dbrole LIMIT"));
        assertEquals("ok", outcome("SHOW FUTURE GRANTS IN SCHEMA test_schema LIMIT 1"));
        assertEquals("ok", outcome("SHOW FUTURE GRANTS IN DATABASE test_db LIMIT 1"));
    }

    @Test
    public void parametersReadsNoModifier() {
        assertEquals(syntax(16, "LIMIT"), outcome("SHOW PARAMETERS LIMIT 1"));
        assertEquals(syntax(20, "<EOF>"), outcome("SHOW PARAMETERS WITH"));
        assertEquals(syntax(21, "PRIVILEGES"), outcome("SHOW PARAMETERS WITH PRIVILEGES USAGE"));
        assertEquals(syntax(16, "STARTS") + "|syntax error line 1 at position 28 unexpected ''A''.",
            outcome("SHOW PARAMETERS STARTS WITH 'A'"));
        assertEquals(syntax(16, "ROOT"), outcome("SHOW PARAMETERS ROOT ONLY"));
        assertEquals(syntax(26, "LIMIT"), outcome("SHOW PARAMETERS LIKE 'A%' LIMIT 1"));
        assertEquals(syntax(27, "LIMIT"), outcome("SHOW PARAMETERS IN SESSION LIMIT 1"));
        assertEquals(syntax(28, "LIMIT"), outcome("SHOW PARAMETERS IN TABLE t1 LIMIT 1"));
        assertEquals(syntax(32, "<EOF>"), outcome("SHOW PARAMETERS IN TABLE t1 WITH"));
        assertEquals(syntax(31, "LIMIT"), outcome("SHOW PARAMETERS IN ACCOUNT abc LIMIT 1"));
        assertEquals(syntax(20, "<EOF>"), outcome("SHOW PARAMETERS LIKE"));
    }

    @Test
    public void aContainerWithoutANameIsTheSessionsOwn() {
        assertTrue(rows("SHOW PARAMETERS IN DATABASE") > 0);
        assertTrue(rows("SHOW PARAMETERS IN SCHEMA") > 0);
        assertTrue(rows("SHOW PARAMETERS IN USER") > 0);
        assertTrue(rows("SHOW PARAMETERS FOR SESSION") > 0);
        assertEquals("SQL compilation error:|This session does not have a current task",
            outcome("SHOW PARAMETERS IN TASK"));
        assertEquals("SQL compilation error:|This session does not have a current stream",
            outcome("SHOW PARAMETERS IN STREAM"));
        assertEquals(syntax(24, "<EOF>"), outcome("SHOW PARAMETERS IN TABLE"));
        assertEquals(syntax(18, "<EOF>"), outcome("SHOW PARAMETERS IN"));
        assertEquals(syntax(19, "<EOF>"), outcome("SHOW PARAMETERS FOR"));
        assertEquals(syntax(27, "abc"), outcome("SHOW PARAMETERS IN SESSION abc"));
    }

    @Test
    public void aNamelessContainerTakesAModifiersWordAsItsName() {
        assertEquals(syntax(33, "1"), outcome("SHOW PARAMETERS IN ACCOUNT LIMIT 1"));
        assertEquals(syntax(34, "1"), outcome("SHOW PARAMETERS IN DATABASE LIMIT 1"));
        assertEquals(syntax(39, "'A'"), outcome("SHOW PARAMETERS IN ACCOUNT STARTS WITH 'A'"));
    }

    @Test
    public void whatIsNoScopeIsRefusedByName() {
        assertEquals(cannotShow("", "ABC"), outcome("SHOW PARAMETERS IN abc"));
        assertEquals(cannotShow("", "TEST_DB"), outcome("SHOW PARAMETERS IN test_db"));
        assertEquals(cannotShow("A", "B"), outcome("SHOW PARAMETERS IN a.b"));
        assertEquals(cannotShow("B, A", "C"), outcome("SHOW PARAMETERS IN a.b.c"));
        assertEquals(cannotShow("", "x"), outcome("SHOW PARAMETERS IN \"x\""));
        assertEquals(cannotShow("", "ROLE"), outcome("SHOW PARAMETERS IN ROLE"));
        assertEquals(cannotShow("", "ROLE"), outcome("SHOW PARAMETERS IN ROLE PUBLIC"));
        assertEquals(cannotShow("", "VIEW"), outcome("SHOW PARAMETERS IN VIEW t1"));
        assertEquals(cannotShow("", "ABC"), outcome("SHOW PARAMETERS LIKE 'A%' IN abc"));
        assertEquals(syntax(31, "x"), outcome("SHOW PARAMETERS IN ROLE PUBLIC x"));
        assertEquals(syntax(23, "def"), outcome("SHOW PARAMETERS IN abc def"));
    }

    @Test
    public void aNameThatIsNoSuchContainerIsRefused() {
        final String none = "SQL compilation error:|Object does not exist, or operation cannot be performed.";
        assertEquals(none, outcome("SHOW PARAMETERS IN ACCOUNT abc"));
        assertEquals(none, outcome("SHOW PARAMETERS IN SCHEMA a.b.c"));
        assertEquals(none, outcome("SHOW PARAMETERS IN DATABASE a.b"));
        assertEquals(none, outcome("SHOW PARAMETERS IN WAREHOUSE a.b"));
        assertEquals(hinted("SQL compilation error:|Database 'ABC' does not exist or not authorized."),
            outcome("SHOW PARAMETERS IN DATABASE abc"));
        assertEquals(hinted("SQL compilation error:|Schema 'TEST_DB.ABC' does not exist or not authorized."),
            outcome("SHOW PARAMETERS IN SCHEMA abc"));
    }

    @Test
    public void accountsTakesNoScope() {
        final String unsupported = "SQL compilation error:|Unsupported statement type. correct syntax: 'SHOW ACCOUNTS'.";
        for (final String scope : new String[] {"IN ACCOUNT", "IN abc", "IN DATABASE", "IN DATABASE abc", "IN SCHEMA abc",
            "IN ACCOUNT abc", "IN a.b", "IN ACCOUNT LIMIT 1"}) {
            assertEquals(unsupported, outcome("SHOW ACCOUNTS " + scope), scope);
        }
        assertEquals(unsupported, outcome("SHOW ACCOUNTS LIKE 'A%' IN ACCOUNT"));
        assertEquals(unsupported, outcome("SHOW ACCOUNTS HISTORY IN ACCOUNT"));
        assertEquals(syntax(16, "<EOF>"), outcome("SHOW ACCOUNTS IN"));
        assertEquals(syntax(22, "IN"), outcome("SHOW ACCOUNTS LIMIT 1 IN ACCOUNT"));
        assertEquals(syntax(27, "IN"), outcome("SHOW ORGANIZATION ACCOUNTS IN ACCOUNT"));
    }
}
