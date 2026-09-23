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
 * The lines live reports when a statement runs into the next one without its semicolon and more tokens
 * follow. A query ending in an unaliased item takes the next word as its alias and the token after it is
 * refused — at the top level as one line, inside a block with the second line {@code MissingTerminator}'s
 * rule stacks. Otherwise, at the top level, the word itself is refused, and the recovery resumes the
 * interrupted query at the first name it can still take as an alias, stacking the next fault it meets.
 * An earlier missing separator is reported ahead of a later fault of the parse.
 */
public class MissingSeparatorFirstLineTest extends BaseDatabaseTest {

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
        final StringBuilder sql = new StringBuilder("EXECUTE IMMEDIATE $$\nDECLARE\n  v OBJECT;\nBEGIN\n");
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

    /** A typed LET after an unaliased item: LET is the alias, the name after it the fault. */
    @Test
    public void aTypedLetAfterAnUnaliasedItemRefusesItsName() {
        assertEquals(refused(line(6, 6, "x"), line(6, 8, "INT")), refusal(block("SELECT 'foo'", "LET x INT := 1;")));
        assertEquals(refused(line(6, 6, "x"), line(6, 8, "NUMBER")),
            refusal(block("SELECT 'foo'", "LET x NUMBER DEFAULT 1;")));
        assertEquals(refused(line(6, 6, "x"), line(6, 8, "CURSOR")),
            refusal(block("SELECT 'foo'", "LET x CURSOR FOR SELECT 1;")));
        assertEquals(refused(line(4, 6, "x"), line(4, 8, "INT")),
            refusal("EXECUTE IMMEDIATE $$\nBEGIN\n  SELECT 'foo'\n  LET x INT := 1;\nEND;\n$$"));
    }

    /** At the top level the swallowed word's follower is refused, one line whatever comes after. */
    @Test
    public void aSwallowedWordAtTheTopLevelIsOneLine() {
        assertEquals(refused(line(1, 20, ":")), refusal("SELECT 'foo' RETURN :v"));
        assertEquals(refused(line(1, 20, "1")), refusal("SELECT 'foo' RETURN 1"));
        assertEquals(refused(line(1, 20, "1")), refusal("SELECT 'foo' RETURN 1 2"));
        assertEquals(refused(line(1, 20, "a")), refusal("SELECT 'foo' RETURN a b"));
        assertEquals(refused(line(1, 20, "1")), refusal("SELECT 'foo' RETURN 1; SELECT 2"));
        assertEquals(refused(line(1, 23, "1")), refusal("SELECT a FROM t RETURN 1"));
        assertEquals(refused(line(1, 21, "1")), refusal("DELETE FROM t RETURN 1"));
        assertEquals(refused(line(1, 17, "x")), refusal("SELECT 'foo' LET x INT := 1"));
        assertEquals(refused(line(1, 19, "RETURN")), refusal("SELECT 'foo' BEGIN RETURN 1; END;"));
        assertEquals(refused(line(1, 18, "TABLES")), refusal("SELECT 'foo' SHOW TABLES"));
        assertEquals(refused(line(1, 22, "USING")), refusal("SELECT 1 MERGE INTO t USING t ON 1 = 1"));
    }

    /** A word the query cannot take is refused, and the recovery's next fault is stacked. */
    @Test
    public void theRecoveryResumesAtANameTheQueryCanTake() {
        assertEquals(refused(line(1, 9, "CREATE"), line(1, 24, "(")), refusal("SELECT 1 CREATE TABLE u (a INT)"));
        assertEquals(refused(line(1, 9, "GRANT"), line(1, 21, "ON")),
            refusal("SELECT 1 GRANT USAGE ON DATABASE d TO ROLE r"));
        assertEquals(refused(line(1, 13, "GRANT"), line(1, 37, "TO")),
            refusal("SELECT 'foo' GRANT SELECT ON TABLE t TO ROLE r"));
        assertEquals(refused(line(1, 9, "UPDATE"), line(1, 18, "SET")), refusal("SELECT 1 UPDATE t SET a = 1"));
        assertEquals(refused(line(1, 9, "ALTER"), line(1, 23, "ADD")), refusal("SELECT 1 ALTER TABLE t ADD COLUMN b INT"));
    }

    /** No name the query can take, or one it takes and reads on from, leaves one line. */
    @Test
    public void withNothingToResumeTheRefusalIsOneLine() {
        assertEquals(refused(line(1, 9, "SELECT")), refusal("SELECT 1 SELECT a FROM t"));
        assertEquals(refused(line(1, 9, "SELECT")), refusal("SELECT 1 SELECT 2"));
        assertEquals(refused(line(1, 14, "SELECT")), refusal("SELECT 1 AS x SELECT 2 x y z"));
        assertEquals(refused(line(1, 14, "CALL")), refusal("SELECT 1 AS x CALL p(a, b)"));
        assertEquals(refused(line(1, 18, "RETURN")), refusal("SELECT 'foo' AS a RETURN 1"));
        assertEquals(refused(line(1, 13, "DELETE")), refusal("SELECT 'foo' DELETE FROM t"));
        assertEquals(refused(line(1, 9, "INSERT")), refusal("SELECT 1 INSERT INTO t VALUES (1)"));
        assertEquals(refused(line(1, 9, "DROP")), refusal("SELECT 1 DROP TABLE t"));
    }

    /** A missing separator ahead of a later fault of the parse is reported first. */
    @Test
    public void theEarlierFaultIsReportedFirst() {
        assertEquals(refused(line(1, 9, "SELECT"), line(1, 20, "y")), refusal("SELECT 1 SELECT 2 x y"));
    }

    /** A swallowed word with nothing after it is simply the alias. */
    @Test
    public void aTrailingWordIsTheAlias() {
        assertEquals("foo", engine.executeQuery("SELECT 'foo' RETURN").getRows().get(0).getValue(0).toString());
    }
}
