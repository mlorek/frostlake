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
 * A fault in an argument of a call written in a SELECT list, when the argument begins with a name (live-verified):
 * live retries the argument from that leading name and refuses the token after it as well — a line placed before the
 * first, and the refused token itself when the name is followed by it.
 */
public class CallArgumentRetryLineTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE T (x INT, y INT)");
    }

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
    public void aPathOpeningTheArgumentIsRefusedAgainAtItsFirstDot() {
        assertEquals(refused(15, "y", 12, "."), refusal("SELECT ABS(T.x y) FROM T"));
        assertEquals(refused(15, ")", 12, "."), refusal("SELECT ABS(T.x.) FROM T"));
        assertEquals(refused(15, "<EOF>", 12, "."), refusal("SELECT ABS(T..x"));
        assertEquals(refused(17, "z", 12, "."), refusal("SELECT ABS(T.x.y z) FROM T"));
        assertEquals(refused(15, "AS", 12, "."), refusal("SELECT ABS(T.x AS y) FROM T"));
        assertEquals(refused(15, "y", 12, "."), refusal("SELECT ABS(T.x y)"));
        assertEquals(refused(15, "y", 12, "."), refusal("SELECT ABS(T.x y), 2 FROM T"));
        assertEquals(refused(19, "y", 16, "."), refusal("SELECT 1 + ABS(T.x y) FROM T"));
    }

    @Test
    public void everyArgumentIsRetriedFromItsOwnLeadingName() {
        assertEquals(refused(23, "y", 20, "."), refusal("SELECT CONCAT('a', T.x y) FROM T"));
        assertEquals(refused(18, "y", 15, "."), refusal("SELECT CONCAT(T.x y, 'a') FROM T"));
        assertEquals(refused(16, "z", 16, "z"), refusal("SELECT ABS(x, y z) FROM T"));
    }

    @Test
    public void aNameFollowedByTheRefusedTokenNamesItTwice() {
        assertEquals(refused(13, "y", 13, "y"), refusal("SELECT ABS(x y) FROM T"));
        assertEquals(refused(13, "y", 13, "y"), refusal("SELECT ABS(x y z) FROM T"));
        assertEquals(refused(15, "y", 15, "y"), refusal("SELECT ABS(\"x\" y) FROM T"));
        assertEquals(refused(13, "1", 13, "1"), refusal("SELECT ABS(x 1) FROM T"));
    }

    @Test
    public void noRetryAfterAnOperatorOrOutsideTheSelectList() {
        assertEquals(refused(14, ")"), refusal("SELECT ABS(x +) FROM T"));
        assertEquals(refused(30, "y"), refusal("SELECT x FROM T WHERE ABS(T.x y) = 1"));
    }
}
