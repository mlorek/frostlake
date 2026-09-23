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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * A SHOW listing's STARTS WITH, LIMIT and WITH PRIVILEGES are refused where live's parser stops: at the word after
 * one left unfinished, at a modifier the listing does not read — and WITH PRIVILEGES on a listing it does not filter
 * is an unsupported feature, judged after a LIMIT 0 and before the scope is looked up (live-verified).
 */
public class ShowModifierSyntaxTest extends BaseDatabaseTest {

    private String answer(final String sql) {
        try {
            return "rows " + engine.executeQuery(sql).getRowCount();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String syntax(final int position, final String token) {
        return "SQL compilation error:|syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    private static String unsupported(final String listing) {
        return "Unsupported feature 'SHOW " + listing + " ... WITH PRIVILEGES <>'.";
    }

    @Test
    public void anUnfinishedModifierIsRefusedAtTheWordAfterIt() {
        assertEquals(syntax(17, "<EOF>"), answer("SHOW TABLES LIMIT"));
        assertEquals(syntax(26, "<EOF>"), answer("SHOW TABLES IN abc y LIMIT"));
        assertEquals(syntax(30, "<EOF>"), answer("SHOW TABLES IN abc LIMIT LIMIT"));
        assertEquals(syntax(17, ";"), answer("SHOW TABLES LIMIT;"));
        assertEquals(syntax(18, "FROM"), answer("SHOW TABLES LIMIT FROM 'x'"));
        assertEquals(syntax(18, "x"), answer("SHOW TABLES LIMIT x"));
        assertEquals(syntax(33, "<EOF>"), answer("SHOW TABLES STARTS WITH 'a' LIMIT"));
        assertEquals(syntax(31, "<EOF>"), answer("SHOW TABLES IN DATABASE x LIMIT"));
        assertEquals(syntax(20, "<EOF>"), answer("SHOW DATABASES LIMIT"));
        assertEquals(syntax(18, "<EOF>"), answer("SHOW TABLES STARTS"));
        assertEquals(syntax(16, "<EOF>"), answer("SHOW TABLES WITH"));
        assertEquals(syntax(17, "x"), answer("SHOW TABLES WITH x"));
        assertEquals(syntax(27, "<EOF>"), answer("SHOW TABLES WITH PRIVILEGES"));
        assertEquals(syntax(30, "<EOF>"), answer("SHOW DATABASES WITH PRIVILEGES"));
        assertEquals(syntax(24, "<EOF>"), answer("SHOW HYBRID TABLES LIMIT"));
    }

    @Test
    public void aModifierTheListingDoesNotReadIsRefusedAtItsWord() {
        assertEquals(syntax(18, "LIMIT"), answer("SHOW PRIMARY KEYS LIMIT 1"));
        assertEquals(syntax(25, "LIMIT"), answer("SHOW PRIMARY KEYS IN abc LIMIT 1"));
        assertEquals(syntax(27, "LIMIT"), answer("SHOW PRIMARY KEYS IN abc y LIMIT 1"));
        assertEquals(syntax(24, "LIMIT"), answer("SHOW TERSE PRIMARY KEYS LIMIT 1"));
        assertEquals(syntax(30, "STARTS"), answer("SHOW PRIMARY KEYS IN TABLE t1 STARTS WITH 'x'"));
        assertEquals(syntax(17, "STARTS"), answer("SHOW UNIQUE KEYS STARTS WITH 'x'"));
        assertEquals(syntax(22, "<EOF>"), answer("SHOW PRIMARY KEYS WITH"));
        assertEquals(syntax(23, "x"), answer("SHOW PRIMARY KEYS WITH x"));
        assertEquals(syntax(23, "PRIVILEGES"), answer("SHOW PRIMARY KEYS WITH PRIVILEGES USAGE"));
        assertEquals(syntax(16, "PRIVILEGES"), answer("SHOW TASKS WITH PRIVILEGES USAGE"));
        assertEquals(syntax(16, "PRIVILEGES"), answer("SHOW ROLES WITH PRIVILEGES"));
        assertEquals(syntax(32, "PRIVILEGES"), answer("SHOW TASKS STARTS WITH 'a' WITH PRIVILEGES USAGE"));
        assertEquals(syntax(15, "LIMIT"), answer("SHOW VARIABLES LIMIT 1"));
        assertEquals(syntax(27, "STARTS"), answer("SHOW ORGANIZATION ACCOUNTS STARTS WITH 'A'"));
        assertEquals(syntax(11, "STARTS") + "|syntax error line 1 at position 23 unexpected ''A''.",
            answer("SHOW LOCKS STARTS WITH 'A'"));
    }

    @Test
    public void withPrivilegesIsUnsupportedWhereItDoesNotFilter() {
        assertEquals(unsupported("TABLES"), answer("SHOW TABLES WITH PRIVILEGES SELECT"));
        assertEquals(unsupported("TABLES"), answer("SHOW TERSE TABLES WITH PRIVILEGES SELECT, INSERT"));
        assertEquals(unsupported("TABLES HISTORY"), answer("SHOW TABLES HISTORY WITH PRIVILEGES SELECT"));
        assertEquals(unsupported("DATABASES HISTORY"), answer("SHOW DATABASES HISTORY WITH PRIVILEGES USAGE"));
        assertEquals(unsupported("MATERIALIZED_VIEWS"), answer("SHOW MATERIALIZED VIEWS WITH PRIVILEGES SELECT"));
        assertEquals(unsupported("KEY_VALUE_TABLES"), answer("SHOW HYBRID TABLES WITH PRIVILEGES SELECT"));
        assertEquals(unsupported("MASKING_POLICYS"), answer("SHOW MASKING POLICIES WITH PRIVILEGES USAGE"));
        assertEquals(unsupported("FUNCTIONS"), answer("SHOW USER FUNCTIONS WITH PRIVILEGES USAGE"));
        assertEquals(unsupported("PROCEDURES"), answer("SHOW BUILTIN PROCEDURES WITH PRIVILEGES USAGE"));
        assertEquals(unsupported("COMPUTE_POOL_INSTANCE_FAMILIESS"),
            answer("SHOW COMPUTE POOL INSTANCE FAMILIES WITH PRIVILEGES USAGE"));
        assertEquals("rows 0", answer("SHOW DATABASES LIKE 'FL_NO_SUCH%' WITH PRIVILEGES USAGE"));
        assertEquals("rows 0", answer("SHOW SCHEMAS LIKE 'FL_NO_SUCH%' WITH PRIVILEGES USAGE"));
    }

    @Test
    public void theModifiersAreJudgedBeforeTheScopeIsLookedUp() {
        final String zero = "page size \"0\" must be greater than 0 in limit clause";
        assertEquals(unsupported("TABLES"), answer("SHOW TABLES IN FL_NO_SUCH WITH PRIVILEGES SELECT"));
        assertEquals(unsupported("TABLES"), answer("SHOW TABLES IN SCHEMA fl_no_such WITH PRIVILEGES SELECT"));
        assertEquals(unsupported("TABLES"), answer("SHOW TABLES IN DATABASE fl_no_such WITH PRIVILEGES SELECT"));
        assertEquals(zero, answer("SHOW TABLES IN SCHEMA fl_no_such LIMIT 0"));
        assertEquals(zero, answer("SHOW FUNCTIONS IN SCHEMA fl_no_such LIMIT 0"));
        assertEquals(zero, answer("SHOW TABLES LIKE 'x' IN SCHEMA fl_no_such LIMIT 0 WITH PRIVILEGES SELECT"));
        assertEquals("SQL compilation error:|Must specify the full search path starting from database for ABC",
            answer("SHOW TABLES IN ACCOUNT abc LIMIT 0"));
        assertEquals(syntax(30, "<EOF>"), answer("SHOW TABLES IN ACCOUNT x LIMIT"));
    }

    @Test
    public void listingsThatReadTheModifiersRunThem() {
        assertEquals("rows 0", answer("SHOW HYBRID TABLES LIKE 'FL_NO_SUCH%' LIMIT 1"));
        assertEquals("rows 0", answer("SHOW HYBRID TABLES LIKE 'FL_NO_SUCH%' STARTS WITH 'A'"));
        assertEquals("rows 1", answer("SHOW ACCOUNTS LIMIT 1"));
        assertEquals("rows 1", answer("SHOW ACCOUNTS STARTS WITH 'A'"));
    }
}
