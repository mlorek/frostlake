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
 * The lines live reports for a fault in an INSERT's column list (live-verified): the list reads on at the next comma
 * or bracket, the statement reads on after the list, a first item that is no name gives the list up, and a call
 * written as an item is refused at its bracket, the token after it, and the token after the first ')' after that.
 */
public class InsertColumnListRecoveryTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String refused(final Object... positionsAndTokens) {
        final StringBuilder message = new StringBuilder("SQL compilation error:");
        for (int i = 0; i < positionsAndTokens.length; i += 2) {
            message.append("\nsyntax error line 1 at position ").append(positionsAndTokens[i])
                .append(" unexpected '").append(positionsAndTokens[i + 1]).append("'.");
        }
        return message.toString();
    }

    @Test
    public void aLaterItemsFaultIsRefusedAloneAndTheListReadsOn() {
        assertEquals(refused(19, "'b'"), refusal("INSERT INTO t1 (a, 'b') VALUES (1)"));
        assertEquals(refused(18, "b"), refusal("INSERT INTO t1 (a b) VALUES (1)"));
        assertEquals(refused(19, "1"), refusal("INSERT INTO t1 (a, 1) VALUES (1)"));
        assertEquals(refused(21, "c"), refusal("INSERT INTO t1 (a, b c) VALUES (1)"));
        assertEquals(refused(18, "b"), refusal("INSERT INTO t1 (a b c) VALUES (1)"));
        assertEquals(refused(19, "'b'", 24, "'c'"), refusal("INSERT INTO t1 (a, 'b', 'c') VALUES (1)"));
        assertEquals(refused(18, ")"), refusal("INSERT INTO t1 (a,) VALUES (1)"));
        assertEquals(refused(29, "'b'"), refusal("INSERT OVERWRITE INTO t1 (a, 'b') VALUES (1)"));
        assertEquals(refused(19, "'b'"), refusal("INSERT INTO t1 (a, 'b') SELECT 1"));
    }

    @Test
    public void theStatementReadsOnAfterTheList() {
        assertEquals(refused(19, "'b'", 35, "x"), refusal("INSERT INTO t1 (a, 'b') VALUES (1) x"));
        assertEquals(refused(19, "'b'", 35, "y"), refusal("INSERT INTO t1 (a, 'b') SELECT 1 x y"));
        assertEquals(refused(19, "'b'", 40, "x"), refusal("INSERT INTO t1 (a, 'b') VALUES (1), (2) x"));
        assertEquals(refused(19, "'b'", 34, "x"), refusal("INSERT INTO t1 (a, 'b') VALUES (1 x"));
        assertEquals(refused(19, "'b'", 24, "x"), refusal("INSERT INTO t1 (a, 'b') x VALUES (1)"));
        assertEquals(refused(19, "'b'", 33, "<EOF>"), refusal("INSERT INTO t1 (a, 'b' VALUES (1)"));
    }

    @Test
    public void aFirstItemThatIsNoNameGivesTheListUp() {
        assertEquals(refused(16, "1", 19, "VALUES"), refusal("INSERT INTO t1 (1) VALUES (1)"));
        assertEquals(refused(16, "'a'", 24, "VALUES"), refusal("INSERT INTO t1 ('a', b) VALUES (1)"));
        assertEquals(refused(16, ")", 18, "VALUES"), refusal("INSERT INTO t1 () VALUES (1)"));
    }

    @Test
    public void aCallWrittenAsAnItemIsRefusedAtItsBracket() {
        assertEquals(refused(21, "(", 22, "'a'", 26, ")"), refusal("INSERT INTO t1 (UPPER('a')) VALUES (1)"));
        assertEquals(refused(21, "(", 22, "a", 24, ")"), refusal("INSERT INTO t1 (UPPER(a)) VALUES (1)"));
        assertEquals(refused(24, "(", 25, "b", 27, ")"), refusal("INSERT INTO t1 (a, UPPER(b)) VALUES (1)"));
        assertEquals(refused(21, "(", 22, "a", 24, ","), refusal("INSERT INTO t1 (UPPER(a), b) VALUES (1)"));
        assertEquals(refused(18, "(", 19, "b", 21, ")"), refusal("INSERT INTO t1 (a (b)) VALUES (1)"));
        assertEquals(refused(21, "(", 22, "'a'", 27, "x"), refusal("INSERT INTO t1 (UPPER('a') x) VALUES (1)"));
        assertEquals(refused(21, "("), refusal("INSERT INTO t1 (UPPER()) VALUES (1)"));
    }
}
