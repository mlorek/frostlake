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
 * A bracketed query right after a select item's alias is refused at its '(' even when a stray ')' follows it, as it is
 * without one (live-verified).
 */
public class BracketedQueryAfterAliasTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String lines(final String... lines) {
        return "SQL compilation error:\n" + String.join("\n", lines);
    }

    private static String at(final int line, final int position, final String token) {
        return "syntax error line " + line + " at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void theBracketIsRefused() {
        assertEquals(lines(at(1, 11, "(")), refusal("SELECT a x (SELECT 1))"));
        assertEquals(lines(at(1, 21, "(")), refusal("SELECT CONCAT('a') x (SELECT 1) SELECT 'b')"));
        assertEquals(lines(at(1, 26, "(")), refusal("SELECT CONCAT('a'  )    x (SELECT 1) SELECT 'b')"));
        assertEquals(lines(at(1, 11, "(")), refusal("SELECT a x (SELECT 1) SELECT 'b'"));
        assertEquals(lines(at(1, 11, "(")), refusal("SELECT a x (SELECT 1)"));
    }
}
