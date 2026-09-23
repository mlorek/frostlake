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
 * A bracketed list written straight after a literal in a SELECT list (live-verified): live reads the bracket as an
 * outer-join marker and refuses the first token inside it, and when a comma follows inside the bracket, the first
 * fault of what comes after the comma — read as the list's next items — is one more line.
 */
public class JoinMarkerListLinesTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String line(final int position, final String token) {
        return "\nsyntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void aCommaInsideTheBracketReadsOnAsTheListsNextItems() {
        assertEquals("SQL compilation error:" + line(10, "2") + line(14, ")"), refusal("SELECT 1 (2, 3)"));
        assertEquals("SQL compilation error:" + line(10, "2") + line(14, ")"), refusal("SELECT 1 (2, 3) x"));
        assertEquals("SQL compilation error:" + line(10, "2") + line(17, ")"), refusal("SELECT 1 (2, 3, 4)"));
        assertEquals("SQL compilation error:" + line(10, "'x'") + line(18, ")"), refusal("SELECT 1 ('x', 'y')"));
        engine.execute("CREATE TABLE t1 (a INT)");
        assertEquals("SQL compilation error:" + line(10, "2") + line(14, ")"), refusal("SELECT 1 (2, 3) FROM t1"));
    }

    @Test
    public void withoutACommaTheFirstTokenInsideIsTheOnlyLine() {
        assertEquals("SQL compilation error:" + line(10, "2"), refusal("SELECT 1 (2)"));
        assertEquals("SQL compilation error:" + line(10, "2"), refusal("SELECT 1 (2 3)"));
    }
}
