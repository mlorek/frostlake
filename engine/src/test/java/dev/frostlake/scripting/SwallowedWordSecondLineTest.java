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
 * A block statement left without its semicolon takes the next statement's first word as its bare ALIAS when it
 * ends in an unaliased select item or table reference, and the account refuses the token after that word. The
 * second line is the one it stacks for any statement run into the next word: from the refused token on, the
 * first word that can be read as a name is taken and the token after it refused, a semicolon there consumed.
 * {@link MissingTerminatorSecondLineTest} holds the shapes this parser refuses at the missing semicolon itself;
 * these are the ones it reads past it.
 *
 * <pre>
 *   SELECT 'foo'  then  CONTINUE :v;        ':' then 'END'      (v is the name, its ';' is consumed)
 *   SELECT 'foo'  then  RETURN a 1;         'a' then '1'        (the refused word is the name)
 *   SELECT 'foo'  then  LET x;              'x' then 'END'
 * </pre>
 */
public class SwallowedWordSecondLineTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
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
    public void theNameAfterTheRefusedTokenIsTaken() {
        assertEquals(refused(line(4, 11, ":"), line(5, 0, "END")), refusal(block("SELECT 'foo'", "CONTINUE :v;")));
        assertEquals(refused(line(6, 11, ":"), line(7, 0, "END")),
            refusal("EXECUTE IMMEDIATE $$\nDECLARE\n  v OBJECT;\nBEGIN\n  SELECT 'foo'\n  CONTINUE :v;\nEND;\n$$"));
        assertEquals(refused(line(4, 11, ":"), line(4, 14, "+")), refusal(block("SELECT 'foo'", "CONTINUE :v + 1;")));
        assertEquals(refused(line(4, 11, ":"), line(4, 13, ",")), refusal(block("SELECT 'foo'", "CONTINUE :v, 1;")));
    }

    @Test
    public void aRefusedNameIsItselfTheNameTaken() {
        assertEquals(refused(line(4, 9, "a"), line(4, 11, "1")), refusal(block("SELECT 'foo'", "RETURN a 1;")));
        assertEquals(refused(line(4, 9, "a"), line(4, 10, ",")), refusal(block("SELECT 'foo'", "RETURN a, b;")));
        assertEquals(refused(line(4, 9, "a"), line(4, 10, ",")), refusal(block("SELECT 'foo'", "RETURN a, b, c;")));
        assertEquals(refused(line(4, 9, "abc"), line(4, 13, "1")), refusal(block("SELECT 'foo'", "RETURN abc 1;")));
        assertEquals(refused(line(4, 9, "a"), line(4, 11, "1")), refusal(block("SELECT 1", "RETURN a 1;")));
        assertEquals(refused(line(4, 9, "a"), line(4, 11, "1")), refusal(block("SELECT a FROM t", "RETURN a 1;")));
    }

    @Test
    public void aSemicolonAfterTheNameIsConsumed() {
        assertEquals(refused(line(4, 6, "x"), line(5, 0, "END")), refusal(block("SELECT 'foo'", "LET x;")));
        assertEquals(refused(line(4, 6, "x"), line(5, 2, "RETURN")), refusal(block("SELECT 'foo'", "LET x;", "RETURN 1;")));
        assertEquals(refused(line(4, 6, "z"), line(5, 2, "LET")), refusal(block("SELECT 'foo'", "LET z;", "LET w := 2;")));
        assertEquals(refused(line(4, 6, "q"), line(5, 0, "END")), refusal(block("SELECT 2 FROM t", "LET q;")));
    }

    /** A word after a written alias is refused the same way, the alias already standing. */
    @Test
    public void aWordAfterAnAliasIsRefusedTheSameWay() {
        assertEquals(refused(line(1, 18, "y"), line(1, 21, "RETURN")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN SELECT 1 x y; RETURN 1; END $$"));
        assertEquals(refused(line(1, 18, "y"), line(1, 20, "z")), refusal("EXECUTE IMMEDIATE $$ BEGIN SELECT 1 x y z; END $$"));
    }
}
