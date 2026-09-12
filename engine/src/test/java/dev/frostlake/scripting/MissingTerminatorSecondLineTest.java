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
 * The SECOND line the account stacks when a block statement runs into the next word without its
 * semicolon (the first line is {@link MissingTerminatorTest}'s subject). After the first line, the
 * recovery resumes at the first word — from the refused token onwards — that the grammar can read as a
 * name, takes it, and refuses the token after it; a semicolon there is consumed and the token after
 * that is refused instead. No name before the statement's semicolon means no second line.
 *
 * <pre>
 *   SELECT 'foo' AS a  then  RETURN 1;            'RETURN', then '1'
 *   LET a := 1         then  INSERT INTO t …      'INSERT', then 'VALUES'   (t is the first name)
 *   LET a := 1         then  BREAK;               'BREAK', then the block's END
 *   SELECT 'foo'       then  RETURN :v + 1;       ':', then '+'             (v is the first name)
 *   SELECT 'foo'       then  RETURN 1 + 2;        '1' alone
 *   SELECT a FROM t AS x  then  RETURN 1;         'RETURN' twice, at the same place
 * </pre>
 *
 * <p>Inside an IF, LOOP, CASE or FOR body the second line lands past the construct instead: on the
 * token after its closing semicolon, or on FOR itself in {@code END FOR}.
 */
public class MissingTerminatorSecondLineTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (a INT)");
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
    public void theTokenAfterTheRefusedWordIsTheSecondLine() {
        final String returnThenOne = refused(line(4, 2, "RETURN"), line(4, 9, "1"));
        assertEquals(returnThenOne, refusal(block("SELECT 'foo' AS a", "RETURN 1;")));
        assertEquals(returnThenOne, refusal(block("SELECT 'foo' b", "RETURN 1;")));
        assertEquals(returnThenOne, refusal(block("SELECT 1 ORDER BY 1", "RETURN 1;")));
        assertEquals(returnThenOne, refusal(block("SELECT 1 LIMIT 1", "RETURN 1;")));
        assertEquals(returnThenOne, refusal(block("SELECT 'foo' WHERE TRUE", "RETURN 1;")));
        assertEquals(returnThenOne, refusal(block("INSERT INTO t VALUES (1)", "RETURN 1;")));
        assertEquals(returnThenOne, refusal(block("UPDATE t SET a = 2", "RETURN 1;")));
        assertEquals(refused(line(4, 2, "RETURN"), line(4, 9, "a")), refusal(block("LET a := 1", "RETURN a;")));
        assertEquals(refused(line(4, 2, "x"), line(4, 4, ":=")), refusal(block("SELECT 'foo' AS a", "x := 2;")));
    }

    @Test
    public void aReservedWordIsSkippedUntilTheFirstName() {
        assertEquals(refused(line(4, 2, "INSERT"), line(4, 16, "VALUES")),
            refusal(block("LET a := 1", "INSERT INTO t VALUES (2);")));
        assertEquals(refused(line(4, 2, "UPDATE"), line(4, 11, "SET")),
            refusal(block("LET a := 1", "UPDATE t SET a = 3;")));
        assertEquals(refused(line(4, 2, "BEGIN"), line(4, 15, "1")),
            refusal(block("LET a := 1", "BEGIN RETURN 1; END;")));
        assertEquals(refused(line(4, 2, "CASE"), line(4, 7, "WHEN")),
            refusal(block("LET a := 1", "CASE WHEN TRUE THEN RETURN 1; END CASE;")));
    }

    @Test
    public void aSemicolonAfterTheNameIsConsumed() {
        assertEquals(refused(line(4, 2, "BREAK"), line(5, 0, "END")), refusal(block("LET a := 1", "BREAK;")));
        assertEquals(refused(line(4, 2, "SELECT"), line(5, 0, "END")), refusal(block("LET a := 1", "SELECT 2 FROM t;")));
        assertEquals(refused(line(4, 2, "RETURN"), line(5, 2, "RETURN")),
            refusal(block("LET a := 1", "RETURN;", "RETURN 2;")));
    }

    @Test
    public void afterASwallowedAliasTheSearchStartsAtTheRefusedToken() {
        assertEquals(refused(line(6, 9, ":"), line(7, 0, "END")),
            refusal("EXECUTE IMMEDIATE $$\nDECLARE\n  v OBJECT;\nBEGIN\n  SELECT 'foo'\n  RETURN :v;\nEND;\n$$"));
        assertEquals(refused(line(6, 9, ":"), line(6, 12, "+")),
            refusal("EXECUTE IMMEDIATE $$\nDECLARE\n  v OBJECT;\nBEGIN\n  SELECT 'foo'\n  RETURN :v + 1;\nEND;\n$$"));
        assertEquals(refused(line(4, 6, "x"), line(4, 8, ":=")), refusal(block("SELECT 'foo'", "LET x := 1;")));
        assertEquals(refused(line(4, 9, "a"), line(5, 2, "LET")), refusal(block("SELECT 'foo'", "RETURN a;", "LET b := 1;")));
        assertEquals(refused(line(4, 9, "CASE"), line(4, 14, "WHEN")),
            refusal(block("SELECT 'foo'", "RETURN CASE WHEN TRUE THEN 1 END;")));
        assertEquals(refused(line(4, 9, "TRUE"), line(5, 0, "END")), refusal(block("SELECT 'foo'", "RETURN TRUE;")));
    }

    /** A DELETE ending at its target takes the next word as the target's alias, as a query does. */
    @Test
    public void aDeleteTargetTakesTheWordAsItsAlias() {
        assertEquals(refused(line(4, 9, "1")), refusal(block("DELETE FROM t", "RETURN 1;")));
        assertEquals(refused(line(4, 9, "a"), line(5, 0, "END")), refusal(block("DELETE FROM t", "RETURN a;")));
        assertEquals(refused(line(4, 2, "RETURN"), line(4, 9, "1")), refusal(block("DELETE FROM t AS x", "RETURN 1;")));
    }

    @Test
    public void aTableAliasedWithAsNamesTheWordTwice() {
        final String twice = refused(line(4, 2, "RETURN"), line(4, 2, "RETURN"));
        assertEquals(twice, refusal(block("SELECT a FROM t AS x", "RETURN 1;")));
        assertEquals(twice, refusal(block("SELECT a FROM (SELECT 1 AS a) AS x", "RETURN 1;")));
        assertEquals(twice, refusal(block("SELECT a FROM t AS x, t AS y", "RETURN 1;")));
        assertEquals(refused(line(4, 2, "SELECT"), line(4, 2, "SELECT")), refusal(block("SELECT a FROM t AS x", "SELECT 2;")));
        // A bare table alias, or a clause after the aliased table, is the ordinary pair.
        assertEquals(refused(line(4, 2, "RETURN"), line(4, 9, "1")), refusal(block("SELECT a FROM t x", "RETURN 1;")));
        assertEquals(refused(line(4, 2, "RETURN"), line(4, 9, "1")),
            refusal(block("SELECT a FROM t AS x WHERE TRUE", "RETURN 1;")));
    }

    @Test
    public void noNameBeforeTheSemicolonMeansOneLine() {
        assertEquals(refused(line(4, 9, "1")), refusal(block("SELECT 'foo'", "RETURN 1;")));
        assertEquals(refused(line(4, 9, "1")), refusal(block("SELECT 'foo'", "RETURN 1 + 2;")));
        assertEquals(refused(line(4, 2, "SELECT")), refusal(block("LET a := 1", "SELECT 2;")));
        assertEquals(refused(line(4, 2, "CALL")), refusal(block("LET a := 1", "CALL p();")));
        assertEquals(refused(line(4, 2, "NULL")), refusal(block("LET a := 1", "NULL;")));
        assertEquals(refused(line(4, 9, "-")), refusal(block("SELECT 'foo'", "RETURN - 1;")));
    }

    @Test
    public void nestedBlocksAndHandlersBehaveAsTheOuterBlock() {
        assertEquals(refused(line(5, 4, "RETURN"), line(5, 11, "1")),
            refusal("EXECUTE IMMEDIATE $$\nBEGIN\n  BEGIN\n    SELECT 'foo' AS a\n    RETURN 1;\n  END;\nEND;\n$$"));
        assertEquals(refused(line(7, 4, "RETURN"), line(7, 11, "1")),
            refusal("EXECUTE IMMEDIATE $$\nBEGIN\n  RETURN 0;\nEXCEPTION\n  WHEN OTHER THEN\n    SELECT 'foo' AS a\n"
                + "    RETURN 1;\nEND;\n$$"));
        assertEquals(refused(line(6, 2, "RETURN"), line(6, 9, "1")),
            refusal("EXECUTE IMMEDIATE $$\nDECLARE\n  x INT;\nBEGIN\n  SELECT 'foo' AS a\n  RETURN 1;\nEND;\n$$"));
    }

    @Test
    public void insideAControlBodyTheSecondLineLandsPastTheConstruct() {
        assertEquals(refused(line(5, 4, "RETURN"), line(7, 0, "END")),
            refusal("EXECUTE IMMEDIATE $$\nBEGIN\n  IF (TRUE) THEN\n    SELECT 'foo' AS a\n    RETURN 1;\n  END IF;\nEND;\n$$"));
        assertEquals(refused(line(5, 11, ":"), line(7, 0, "END")),
            refusal("EXECUTE IMMEDIATE $$\nBEGIN\n  IF (TRUE) THEN\n    SELECT 'foo'\n    RETURN :v;\n  END IF;\nEND;\n$$"));
        assertEquals(refused(line(5, 4, "RETURN"), line(7, 2, "RETURN")),
            refusal("EXECUTE IMMEDIATE $$\nBEGIN\n  IF (TRUE) THEN\n    SELECT 'foo' AS a\n    RETURN 1;\n  END IF;\n"
                + "  RETURN 3;\nEND;\n$$"));
        assertEquals(refused(line(5, 4, "RETURN"), line(7, 0, "END")),
            refusal("EXECUTE IMMEDIATE $$\nBEGIN\n  LOOP\n    SELECT 'foo' AS a\n    RETURN 1;\n  END LOOP;\nEND;\n$$"));
        assertEquals(refused(line(5, 4, "RETURN"), line(7, 0, "END")),
            refusal("EXECUTE IMMEDIATE $$\nBEGIN\n  CASE WHEN TRUE THEN\n    SELECT 'foo' AS a\n    RETURN 1;\n  END CASE;\n"
                + "END;\n$$"));
        assertEquals(refused(line(5, 4, "RETURN"), line(6, 6, "FOR")),
            refusal("EXECUTE IMMEDIATE $$\nBEGIN\n  FOR i IN 1 TO 2 DO\n    SELECT 'foo' AS a\n    RETURN 1;\n  END FOR;\n"
                + "END;\n$$"));
    }
}
