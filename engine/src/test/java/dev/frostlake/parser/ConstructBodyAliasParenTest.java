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
 * An alias followed by '(' that ends a statement of a control construct's body is refused at the '('; the construct's
 * END ends the report, while an ELSE or ELSEIF after the statement stacks lines of its own (live-verified).
 */
public class ConstructBodyAliasParenTest extends BaseDatabaseTest {

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
    public void theConstructsEndClosesTheReport() {
        assertEquals(lines(at(1, 34, "(")), refusal("BEGIN IF (TRUE) THEN SELECT 1 foo (2); END IF; END"));
        assertEquals(lines(at(1, 34, "(")), refusal("BEGIN IF (TRUE) THEN SELECT 1 foo (2); END IF; RETURN 1; END"));
        assertEquals(lines(at(1, 24, "(")), refusal("BEGIN LOOP SELECT 1 foo (2); END LOOP; END"));
        assertEquals(lines(at(1, 49, "(")), refusal("BEGIN IF (TRUE) THEN RETURN 1; ELSE SELECT 1 foo (2); END IF; END"));
        assertEquals(lines(at(1, 35, "(")), refusal("BEGIN WHILE (TRUE) DO SELECT 1 foo (2); END WHILE; END"));
        assertEquals(lines(at(1, 38, "(")), refusal("BEGIN FOR i IN 1 TO 2 DO SELECT 1 foo (2); END FOR; END"));
        assertEquals(lines(at(1, 34, "(")), refusal("BEGIN IF (TRUE) THEN SELECT 1 foo (2); END IF; RETURN 1; END"));
        assertEquals(lines(at(1, 26, "(")), refusal("BEGIN REPEAT SELECT 1 foo (2); UNTIL (TRUE) END REPEAT; END"));
        assertEquals(lines(at(1, 39, "(")), refusal("BEGIN CASE WHEN TRUE THEN SELECT 1 foo (2); END CASE; END"));
    }

    @Test
    public void anElseOrElseifStacksItsOwnLines() {
        assertEquals(lines(at(1, 34, "("), at(1, 39, "ELSE"), at(1, 51, "1")), refusal("BEGIN IF (TRUE) THEN SELECT 1 foo (2); ELSE RETURN 1; END IF; END"));
        assertEquals(lines(at(1, 34, "("), at(1, 39, "ELSEIF"), at(1, 46, "(")), refusal("BEGIN IF (TRUE) THEN SELECT 1 foo (2); ELSEIF (FALSE) THEN RETURN 1; END IF; END"));
        assertEquals(lines(at(1, 34, "("), at(1, 39, "ELSE"), at(1, 48, "x")), refusal("BEGIN IF (TRUE) THEN SELECT 1 foo (2); ELSE LET x := 1; END IF; END"));
    }

    @Test
    public void aStatementOfTheBlockItselfKeepsItsReading() {
        assertEquals(lines(at(1, 19, "(")), refusal("BEGIN SELECT 1 foo (2); END"));
        assertEquals(lines(at(1, 36, "("), at(1, 58, "END")), refusal("BEGIN IF (TRUE) THEN SELECT 'a' foo ('x') FROM t; END IF; END"));
    }
}
