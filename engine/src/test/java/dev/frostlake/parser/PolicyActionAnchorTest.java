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

package dev.frostlake.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Where live refuses a fault in an ALTER of a policy — masking, row access, projection, aggregation or join alike
 * (live-verified): a SET with nothing after it at the end of input, any fault in an UNSET list at the UNSET, any fault
 * in a SET's TAG or property list at the list's first word (the word after BODY when no arrow follows it), a RENAME at
 * the RENAME, and a fault in a new body where it falls, up to the first token after the body.
 */
public class PolicyActionAnchorTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String line(final int position, final String token) {
        return "SQL compilation error:\nsyntax error line 1 at position " + position + " unexpected '" + token
            + "'.";
    }

    @Test
    public void aSetWithNothingAfterItIsRefusedAtTheEndOfInput() {
        assertEquals(line(27, "<EOF>"), refusal("ALTER MASKING POLICY mp SET"));
        assertEquals(line(31, "<EOF>"), refusal("ALTER ROW ACCESS POLICY rap SET"));
        assertEquals(line(37, "<EOF>"), refusal("ALTER MASKING POLICY IF EXISTS mp SET"));
        assertEquals(line(30, "<EOF>"), refusal("ALTER PROJECTION POLICY pp SET"));
        assertEquals(line(32, "<EOF>"), refusal("ALTER MASKING POLICY mp SET BODY"));
        assertEquals(line(35, "<EOF>"), refusal("ALTER MASKING POLICY mp SET BODY ->"));
        assertEquals(line(23, "<EOF>"), refusal("ALTER MASKING POLICY mp"));
    }

    @Test
    public void aFaultInAPropertyOrTagListIsRefusedAtTheListsFirstWord() {
        assertEquals(line(28, "COMMENT"), refusal("ALTER MASKING POLICY mp SET COMMENT"));
        assertEquals(line(28, "COMMENT"), refusal("ALTER MASKING POLICY mp SET COMMENT ="));
        assertEquals(line(28, "COMMENT"), refusal("ALTER MASKING POLICY mp SET COMMENT = 'c' x"));
        assertEquals(line(28, "COMMENT"), refusal("ALTER MASKING POLICY mp SET COMMENT = 'c', TAG tg = 'v'"));
        assertEquals(line(28, "COMMENT"), refusal("ALTER MASKING POLICY mp SET COMMENT = 'c', BODY -> val"));
        assertEquals(line(28, "COMMENT"), refusal("ALTER MASKING POLICY mp SET COMMENT = ((1))"));
        assertEquals(line(28, "COMMENT"), refusal("ALTER MASKING POLICY mp SET COMMENT = +1"));
        assertEquals(line(28, "foo"), refusal("ALTER MASKING POLICY mp SET foo = 1 + 2"));
        assertEquals(line(28, "a"), refusal("ALTER MASKING POLICY mp SET a.b = 1"));
        assertEquals(line(28, "TAG"), refusal("ALTER MASKING POLICY mp SET TAG"));
        assertEquals(line(28, "TAG"), refusal("ALTER MASKING POLICY mp SET TAG tg = 'v' tg2 = 'w'"));
        assertEquals(line(28, "SECURE"), refusal("ALTER MASKING POLICY mp SET SECURE"));
        assertEquals(line(28, "SET"), refusal("ALTER MASKING POLICY mp SET SET"));
        assertEquals(line(32, "COMMENT"), refusal("ALTER ROW ACCESS POLICY rap SET COMMENT"));
        assertEquals(line(31, "COMMENT"), refusal("ALTER PROJECTION POLICY pp SET COMMENT"));
        assertEquals(line(25, "COMMENT"), refusal("ALTER JOIN POLICY jp SET COMMENT"));
        assertEquals(line(32, "COMMENT"), refusal("ALTER AGGREGATION POLICY ap SET COMMENT"));
    }

    @Test
    public void aBodyNotFollowedByItsArrowIsRefusedAtTheWordAfterBody() {
        assertEquals(line(33, "="), refusal("ALTER MASKING POLICY mp SET BODY ="));
        assertEquals(line(33, "="), refusal("ALTER MASKING POLICY mp SET BODY = 1 x"));
        assertEquals(line(33, "="), refusal("ALTER MASKING POLICY mp SET BODY = 1, foo"));
        assertEquals(line(33, "val"), refusal("ALTER MASKING POLICY mp SET BODY val"));
    }

    @Test
    public void aFaultAfterANewBodyIsRefusedWhereItFalls() {
        assertEquals(line(47, "x"), refusal("ALTER MASKING POLICY mp SET BODY -> UPPER(val) x"));
        assertEquals(line(39, ","), refusal("ALTER MASKING POLICY mp SET BODY -> val,"));
        assertEquals(line(39, ","), refusal("ALTER MASKING POLICY mp SET BODY -> val, COMMENT = 'x'"));
        assertEquals(line(40, "COMMENT"), refusal("ALTER MASKING POLICY mp SET BODY -> val COMMENT = 'x'"));
    }

    @Test
    public void aFaultInAnUnsetListIsRefusedAtTheUnset() {
        assertEquals(line(24, "UNSET"), refusal("ALTER MASKING POLICY mp UNSET"));
        assertEquals(line(24, "UNSET"), refusal("ALTER MASKING POLICY mp UNSET COMMENT x"));
        assertEquals(line(24, "UNSET"), refusal("ALTER MASKING POLICY mp UNSET COMMENT COMMENT"));
        assertEquals(line(24, "UNSET"), refusal("ALTER MASKING POLICY mp UNSET COMMENT, TAG tg"));
        assertEquals(line(24, "UNSET"), refusal("ALTER MASKING POLICY mp UNSET COMMENT,"));
        assertEquals(line(24, "UNSET"), refusal("ALTER MASKING POLICY mp UNSET TAG tg x"));
        assertEquals(line(28, "UNSET"), refusal("ALTER ROW ACCESS POLICY rap UNSET"));
        assertEquals(line(27, "UNSET"), refusal("ALTER PROJECTION POLICY pp UNSET"));
        assertEquals(line(21, "UNSET"), refusal("ALTER JOIN POLICY jp UNSET"));
        assertEquals(line(28, "UNSET"), refusal("ALTER AGGREGATION POLICY ap UNSET"));
    }

    @Test
    public void everyPolicyKindsRenameIsRefusedAtItsRename() {
        assertEquals(line(24, "RENAME"), refusal("ALTER MASKING POLICY mp RENAME TO mp2 x"));
        assertEquals(line(27, "RENAME"), refusal("ALTER PROJECTION POLICY pp RENAME"));
        assertEquals(line(27, "RENAME"), refusal("ALTER PROJECTION POLICY pp RENAME TO"));
        assertEquals(line(21, "RENAME"), refusal("ALTER JOIN POLICY jp RENAME"));
        assertEquals(line(28, "RENAME"), refusal("ALTER AGGREGATION POLICY ap RENAME"));
    }

    @Test
    public void anUnknownActionIsRefusedAtItsWord() {
        assertEquals(line(24, "x"), refusal("ALTER MASKING POLICY mp x"));
    }
}
