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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A Snowflake-Scripting condition is PARENTHESIZED — {@code IF (<condition>) THEN} — and a routine
 * whose body writes it bare is rejected at CREATE time (live-verified, cell by cell): the refusal
 * carries two body-relative syntax-error lines, a PROCEDURE anchoring the first on the condition's
 * first token, a FUNCTION block body anchoring on the keyword itself under the
 * {@code Compilation of SQL UDF failed:} wrapper. {@code IF EXISTS} / {@code IF NOT EXISTS} inside
 * ordinary SQL statements of a body stay legal, as do parenthesized conditions everywhere.
 */
public class ScriptingConditionParenthesesTest extends BaseDatabaseTest {

    private void assertRefused(final String sql, final String message) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertEquals(message, e.getMessage());
    }

    @Test
    public void bareIfConditionIsRefusedAtCreate() {
        assertRefused(
            "CREATE OR REPLACE PROCEDURE p1(n INTEGER) RETURNS VARCHAR LANGUAGE SQL AS"
                + " $$ BEGIN IF n > 10 THEN RETURN 'a'; END IF; RETURN 'b'; END $$",
            "SQL compilation error:\nsyntax error line 1 at position 10 unexpected 'n'."
                + "\nsyntax error line 1 at position 17 unexpected 'THEN'.");
    }

    @Test
    public void parenthesizedConditionsAreAccepted() {
        engine.execute("CREATE OR REPLACE PROCEDURE p2(n INTEGER) RETURNS VARCHAR LANGUAGE SQL AS"
            + " $$ BEGIN IF (n > 10) THEN RETURN 'a'; END IF; RETURN 'b'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p5(n INTEGER) RETURNS VARCHAR LANGUAGE SQL AS"
            + " $$ DECLARE c INTEGER DEFAULT 0; BEGIN WHILE (c < n) DO c := c + 1; END WHILE; RETURN 'x'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p7(n INTEGER) RETURNS VARCHAR LANGUAGE SQL AS"
            + " $$ DECLARE c INTEGER DEFAULT 0; BEGIN REPEAT c := c + 1; UNTIL (c >= n) END REPEAT; RETURN 'x'; END $$");
    }

    @Test
    public void bareElseifConditionIsRefusedAtCreate() {
        assertRefused(
            "CREATE OR REPLACE PROCEDURE p3(n INTEGER) RETURNS VARCHAR LANGUAGE SQL AS"
                + " $$ BEGIN IF (n > 10) THEN RETURN 'a'; ELSEIF n > 5 THEN RETURN 'm'; END IF; RETURN 'b'; END $$",
            "SQL compilation error:\nsyntax error line 1 at position 43 unexpected 'n'."
                + "\nsyntax error line 1 at position 49 unexpected 'THEN'.");
    }

    @Test
    public void bareWhileConditionIsRefusedAtCreate() {
        assertRefused(
            "CREATE OR REPLACE PROCEDURE p4(n INTEGER) RETURNS VARCHAR LANGUAGE SQL AS"
                + " $$ DECLARE c INTEGER DEFAULT 0; BEGIN WHILE c < n DO c := c + 1; END WHILE; RETURN 'x'; END $$",
            "SQL compilation error:\nsyntax error line 1 at position 42 unexpected 'c'."
                + "\nsyntax error line 1 at position 48 unexpected 'DO'.");
    }

    @Test
    public void bareUntilConditionIsRefusedAtCreate() {
        assertRefused(
            "CREATE OR REPLACE PROCEDURE p6(n INTEGER) RETURNS VARCHAR LANGUAGE SQL AS"
                + " $$ DECLARE c INTEGER DEFAULT 0; BEGIN REPEAT c := c + 1; UNTIL c >= n END REPEAT; RETURN 'x'; END $$",
            "SQL compilation error:\nsyntax error line 1 at position 61 unexpected 'c'."
                + "\nsyntax error line 1 at position 68 unexpected 'END'.");
    }

    @Test
    public void functionBlockBodyAnchorsOnTheKeyword() {
        assertRefused(
            "CREATE OR REPLACE FUNCTION f9(n INTEGER) RETURNS VARCHAR AS"
                + " $$ BEGIN IF n > 10 THEN RETURN 'a'; END IF; RETURN 'b'; END $$",
            "Compilation of SQL UDF failed: SQL compilation error:"
                + "\nsyntax error line 1 at position 8 unexpected 'IF'."
                + "\nsyntax error line 1 at position 18 unexpected 'THEN'.");
    }

    @Test
    public void ifExistsInsideABodyStaysLegal() {
        engine.execute("CREATE OR REPLACE PROCEDURE p8() RETURNS VARCHAR LANGUAGE SQL AS"
            + " $$ BEGIN DROP TABLE IF EXISTS test_db.test_schema.nosuch; RETURN 'x'; END $$");
    }
}
