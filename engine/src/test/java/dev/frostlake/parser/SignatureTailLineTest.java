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
 * After a fault inside the signature a DESCRIBE reads after the object's name, the rest of the statement is read on
 * past the signature and its first fault is one more line; a tail that reads whole adds none, and a DROP stacks no
 * such line (live-verified).
 */
public class SignatureTailLineTest extends BaseDatabaseTest {

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

    private static String refused(final String... lines) {
        final StringBuilder message = new StringBuilder("SQL compilation error:");
        for (final String each : lines) {
            message.append(each);
        }
        return message.toString();
    }

    @Test
    public void theTailsFaultIsStacked() {
        assertEquals(refused(line(21, "b"), line(25, "<EOF>")), refusal("DESCRIBE TABLE t1 (a b) x"));
        assertEquals(refused(line(19, "TRUE"), line(23, ")"), line(26, "<EOF>")), refusal("DESCRIBE TABLE t1 (TRUE) x"));
        assertEquals(refused(line(32, "'t1'"), line(40, "<EOF>")),
            refusal("DESCRIBE TABLE IDENTIFIER(UPPER('t1')) x"));
    }

    @Test
    public void aWholeTailOrADropAddsNothing() {
        assertEquals(refused(line(19, "("), line(21, ")")), refusal("DESCRIBE TABLE t1 ((a)) TYPE = COLUMNS"));
        assertEquals(refused(line(19, "("), line(21, ")")), refusal("DESCRIBE TABLE t1 ((a)) x = 1"));
        assertEquals(refused(line(17, "b")), refusal("DROP TABLE t1 (a b) x"));
    }
}
