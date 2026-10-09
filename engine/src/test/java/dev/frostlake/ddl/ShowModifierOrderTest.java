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

/**
 * A listing's modifiers are read in one order and once each. The engine refused a modifier written after the
 * privilege list somewhere else than the account, or read on past it.
 *
 * <p>On the account the order is STARTS WITH, LIMIT, WITH PRIVILEGES. One written again, or after a later one, is
 * refused at its own word; a STARTS WITH after the privilege list stacks a second line at its prefix; a second WITH
 * is read on and refused at the word after it. Every cell is live-verified.
 */
public class ShowModifierOrderTest extends BaseDatabaseTest {

    /** The statement's refusal with its lines joined, or "ok". */
    private String outcome(final String sql) {
        try {
            engine.executeQuery(sql);
            return "ok";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String syntax(final int position, final String token) {
        return "SQL compilation error:|syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void aModifierWrittenAgainIsRefusedAtItsWord() {
        assertEquals(syntax(28, "STARTS"), outcome("SHOW TABLES STARTS WITH 'a' STARTS WITH 'b'"));
        assertEquals(syntax(20, "LIMIT"), outcome("SHOW TABLES LIMIT 1 LIMIT 2"));
        assertEquals(syntax(40, "PRIVILEGES"), outcome("SHOW SCHEMAS WITH PRIVILEGES USAGE WITH PRIVILEGES USAGE"));
        assertEquals(syntax(39, "<EOF>"), outcome("SHOW SCHEMAS WITH PRIVILEGES USAGE WITH"));
        assertEquals(syntax(27, "STARTS"), outcome("SHOW ROLES STARTS WITH 'A' STARTS WITH 'B'"));
        assertEquals(syntax(19, "LIMIT"), outcome("SHOW ROLES LIMIT 1 LIMIT 2"));
    }

    @Test
    public void aModifierWrittenAfterALaterOneIsRefusedAtItsWord() {
        assertEquals(syntax(20, "STARTS"), outcome("SHOW TABLES LIMIT 1 STARTS WITH 'a'"));
        assertEquals(syntax(19, "STARTS"), outcome("SHOW ROLES LIMIT 1 STARTS WITH 'A'"));
        assertEquals(syntax(20, "ROOT"), outcome("SHOW TABLES LIMIT 1 ROOT ONLY"));
        assertEquals(syntax(35, "LIMIT"), outcome("SHOW TABLES WITH PRIVILEGES SELECT LIMIT 1"));
        assertEquals(syntax(35, "LIMIT"), outcome("SHOW SCHEMAS WITH PRIVILEGES USAGE LIMIT 1"));
        assertEquals(syntax(35, "ROOT"), outcome("SHOW TABLES WITH PRIVILEGES SELECT ROOT ONLY"));
        assertEquals(syntax(43, "LIMIT"), outcome("SHOW SCHEMAS LIMIT 1 WITH PRIVILEGES USAGE LIMIT 2"));
        assertEquals(syntax(36, "LIMIT"), outcome("SHOW TABLES WITH PRIVILEGES SELECT, LIMIT 1"));
    }

    @Test
    public void aStartsWithAfterThePrivilegesStacksItsPrefix() {
        assertEquals(syntax(35, "STARTS") + "|syntax error line 1 at position 47 unexpected ''a''.",
            outcome("SHOW TABLES WITH PRIVILEGES SELECT STARTS WITH 'a'"));
        assertEquals(syntax(35, "STARTS") + "|syntax error line 1 at position 47 unexpected ''A''.",
            outcome("SHOW SCHEMAS WITH PRIVILEGES USAGE STARTS WITH 'A'"));
        assertEquals(syntax(35, "STARTS"), outcome("SHOW SCHEMAS WITH PRIVILEGES USAGE STARTS"));
        assertEquals(syntax(51, "STARTS") + "|syntax error line 1 at position 63 unexpected ''B''.",
            outcome("SHOW SCHEMAS STARTS WITH 'A' WITH PRIVILEGES USAGE STARTS WITH 'B'"));
    }

    @Test
    public void theThreeInOrderStillRun() {
        assertEquals("ok", outcome("SHOW SCHEMAS STARTS WITH 'A' LIMIT 1 WITH PRIVILEGES USAGE"));
        assertEquals("ok", outcome("SHOW TABLES STARTS WITH 'a' LIMIT 1"));
    }
}
