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
 * A select item's ALIAS followed by '(' in a statement of a BEGIN … END block. The '(' is the first line, as
 * outside a block, and the account's recovery then stacks a second: it resumes at the first word from the '('
 * on that can be read as a name, takes it, and refuses the token after it — a semicolon there is consumed and
 * the token after it refused instead. No name before the statement's semicolon leaves the one line.
 *
 * <pre>
 *   SELECT 1 foo ('x') FROM t;  RETURN 1;       '(' then 'RETURN'   (t is the name, its ';' is consumed)
 *   SELECT 1 foo (a) FROM t;                    '(' then ')'        (a is the name)
 *   SELECT 1 foo ('x') + 1;                     '(' alone
 * </pre>
 *
 * <p>A declaration section and an exception handler change nothing; inside a LOOP body the second line lands
 * past the construct, as it does for a statement without its semicolon.
 */
public class AliasParenthesisSecondLineTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    /** An anonymous block of one line, run through EXECUTE IMMEDIATE. */
    private static String body(final String block) {
        return "EXECUTE IMMEDIATE $$ " + block + " $$";
    }

    /** A block whose body is the given lines, one statement per line, run through EXECUTE IMMEDIATE. */
    private static String block(final String... statements) {
        final StringBuilder sql = new StringBuilder("EXECUTE IMMEDIATE $$\nBEGIN\n");
        for (final String statement : statements) {
            sql.append("  ").append(statement).append('\n');
        }
        return sql.append("END;\n$$").toString();
    }

    private static String line(final int line, final int position, final String token) {
        return "\nsyntax error line " + line + " at position " + position + " unexpected '" + token + "'.";
    }

    private static String refused(final String... lines) {
        final StringBuilder message = new StringBuilder("SQL compilation error:");
        for (final String each : lines) {
            message.append(each);
        }
        return message.toString();
    }

    @Test
    public void theFirstNameFromTheBracketOnIsTakenAndTheTokenAfterItRefused() {
        assertEquals(refused(line(1, 20, "("), line(1, 34, "RETURN")),
            refusal(body("BEGIN SELECT 1 foo ('x') FROM t; RETURN 1; END")));
        assertEquals(refused(line(1, 20, "("), line(1, 22, ")")),
            refusal(body("BEGIN SELECT 1 foo (a) FROM t; RETURN 1; END")));
        assertEquals(refused(line(1, 20, "("), line(1, 33, "WHERE")),
            refusal(body("BEGIN SELECT 1 foo ('x') FROM t WHERE a = 1; RETURN 1; END")));
        assertEquals(refused(line(1, 20, "("), line(1, 31, "WHERE")),
            refusal(body("BEGIN SELECT 1 foo (2) FROM t WHERE TRUE; END")));
        assertEquals(refused(line(1, 20, "("), line(1, 31, "AS")),
            refusal(body("BEGIN SELECT 1 foo (2) FROM t AS x; RETURN 1; END")));
        assertEquals(refused(line(1, 25, "("), line(1, 39, "RETURN")),
            refusal(body("BEGIN SELECT 'a' AS foo ('x') FROM t; RETURN 1; END")));
    }

    @Test
    public void aSemicolonAfterTheNameIsConsumed() {
        assertEquals(refused(line(1, 20, "("), line(1, 34, "END")),
            refusal(body("BEGIN SELECT 1 foo ('x') FROM t; END")));
    }

    @Test
    public void theNextStatementsFirstWordCanBeTheName() {
        assertEquals(refused(line(3, 15, "("), line(4, 9, "1")), refusal(block("SELECT 1 foo ('x')", "RETURN 1;")));
        assertEquals(refused(line(3, 17, "("), line(4, 9, "2")), refusal(block("SELECT 'a' foo ('x')", "RETURN 2;")));
        assertEquals(refused(line(3, 15, "("), line(4, 6, "y")), refusal(block("SELECT 1 foo ('x')", "LET y := 1;")));
    }

    @Test
    public void noNameBeforeTheSemicolonLeavesOneLine() {
        assertEquals(refused(line(1, 20, "(")), refusal(body("BEGIN SELECT 1 foo ('x') + 1; RETURN 1; END")));
        // The next statement's own fault still speaks.
        assertEquals(refused(line(1, 20, "("), line(1, 38, "(")),
            refusal(body("BEGIN SELECT 1 foo (2); SELECT 3 bar (4); END")));
    }

    @Test
    public void declarationsAndHandlersChangeNothing() {
        assertEquals(refused(line(1, 35, "("), line(1, 49, "RETURN")),
            refusal(body("DECLARE x INT; BEGIN SELECT 1 foo ('x') FROM t; RETURN 1; END")));
        assertEquals(refused(line(1, 51, "("), line(1, 65, "RETURN")),
            refusal(body("DECLARE c CURSOR FOR SELECT 1; BEGIN SELECT 1 foo ('x') FROM t; RETURN 1; END")));
        assertEquals(refused(line(1, 56, "("), line(1, 70, "RETURN")),
            refusal(body("BEGIN RETURN 0; EXCEPTION WHEN OTHER THEN SELECT 1 foo ('x') FROM t; RETURN 1; END")));
    }

    @Test
    public void insideALoopTheSecondLineLandsPastIt() {
        assertEquals(refused(line(1, 25, "("), line(1, 56, "RETURN")),
            refusal(body("BEGIN LOOP SELECT 1 foo ('x') FROM t; BREAK; END LOOP; RETURN 2; END")));
    }
}
