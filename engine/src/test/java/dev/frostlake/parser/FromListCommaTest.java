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
 * A comma after a FROM list's last table reference carries the list on: live refuses the first token that cannot begin
 * the next table reference (live-verified).
 */
public class FromListCommaTest extends BaseDatabaseTest {

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
    public void theTokenAfterTheCommaIsRefused() {
        assertEquals(lines(at(1, 17, "1")), refusal("SELECT 1 FROM t, 1"));
        assertEquals(lines(at(1, 17, "1")), refusal("SELECT 1 FROM t, 1 WHERE TRUE"));
        assertEquals(lines(at(1, 18, "1")), refusal("SELECT 1 FROM t, (1)"));
        assertEquals(lines(at(1, 17, "+")), refusal("SELECT 1 FROM t, +"));
        assertEquals(lines(at(1, 17, ",")), refusal("SELECT 1 FROM t, ,"));
        assertEquals(lines(at(1, 17, "1")), refusal("SELECT * FROM t, 1 x"));
        assertEquals(lines(at(1, 17, "1.5")), refusal("SELECT 1 FROM t, 1.5"));
        assertEquals(lines(at(1, 17, "-")), refusal("SELECT 1 FROM t, -1"));
        assertEquals(lines(at(1, 17, "NULL")), refusal("SELECT 1 FROM t, NULL"));
        assertEquals(lines(at(1, 20, "1")), refusal("SELECT 1 FROM t, t, 1"));
        assertEquals(lines(at(1, 34, "1")), refusal("SELECT 1 FROM t JOIN t u ON TRUE, 1"));
        assertEquals(lines(at(1, 17, "1")), refusal("SELECT 1 FROM t, 1)"));
        assertEquals(lines(at(1, 17, ")")), refusal("SELECT 1 FROM t, )"));
        assertEquals(lines(at(1, 19, "1")), refusal("SELECT 1 FROM t u, 1"));
    }
}
