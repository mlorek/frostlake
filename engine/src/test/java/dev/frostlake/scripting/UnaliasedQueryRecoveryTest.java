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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The lines live reports when a block statement runs into the next one without its semicolon, in the two
 * shapes that depend on what surrounds the fault (live-verified):
 *
 * <ul>
 *   <li>a block with no DECLARE section is refused as the block it is, line for line as one with a
 *       DECLARE section, where Frostlake gave the block up and reported a later token;</li>
 *   <li>after a query ending in an unaliased select item, the recovery resumes the query, and after the
 *       fault it meets there runs once more, so a third line is stacked — or none at all when the resumed
 *       query reads on without a fault.</li>
 * </ul>
 */
public class UnaliasedQueryRecoveryTest extends BaseDatabaseTest {

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

    /** A block with no DECLARE section: BEGIN, the two statements one per line, END. */
    private static String bare(final String first, final String second) {
        return "EXECUTE IMMEDIATE $$\nBEGIN\n  " + first + "\n  " + second + "\nEND;\n$$";
    }

    /** The same block with a DECLARE section ahead of it, so the statements stand two lines lower. */
    private static String declared(final String first, final String second) {
        return "EXECUTE IMMEDIATE $$\nDECLARE\n  v OBJECT;\nBEGIN\n  " + first + "\n  " + second + "\nEND;\n$$";
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
    public void aBlockWithoutDeclarationsIsRefusedAsABlock() {
        assertEquals(refused(line(4, 4, ":="), line(4, 9, "z")), refusal(bare("SELECT 'foo'", "x := y z w;")));
        assertEquals(refused(line(4, 9, "a"), line(4, 11, "b")), refusal(bare("SELECT 'foo'", "RETURN a b c d;")));
        assertEquals(refused(line(4, 2, "x"), line(4, 4, ":=")), refusal(bare("SELECT 'foo' AS a", "x := y z w;")));
        assertEquals(refused(line(4, 2, "RETURN"), line(4, 9, "a")),
            refusal(bare("SELECT 'foo' AS a", "RETURN a b c d;")));
        assertEquals(refused(line(4, 2, "LET"), line(4, 6, "x")), refusal(bare("SELECT 'foo' AS a", "LET x;")));
        assertEquals(refused(line(6, 4, ":="), line(6, 9, "z")), refusal(declared("SELECT 'foo'", "x := y z w;")));
        assertEquals(refused(line(6, 2, "LET"), line(6, 6, "x")), refusal(declared("SELECT 'foo' AS a", "LET x;")));
    }

    @Test
    public void afterAnUnaliasedItemTheRecoveryRunsOnceMore() {
        assertEquals(refused(line(4, 2, "GRANT"), line(4, 26, "TO"), line(4, 34, "r")),
            refusal(bare("SELECT 'foo'", "GRANT SELECT ON TABLE t TO ROLE r;")));
        assertEquals(refused(line(4, 2, "UPDATE"), line(4, 11, "SET"), line(4, 17, "=")),
            refusal(bare("SELECT 'foo'", "UPDATE t SET a = b WHERE c = d;")));
        assertEquals(refused(line(4, 2, "CREATE"), line(4, 17, "("), line(4, 20, "INT")),
            refusal(bare("SELECT 'foo'", "CREATE TABLE u (a INT);")));
        assertEquals(refused(line(4, 2, "GRANT"), line(4, 14, "ON"), line(4, 26, "d")),
            refusal(bare("SELECT 'foo'", "GRANT USAGE ON DATABASE d TO ROLE r;")));
        assertEquals(refused(line(6, 2, "GRANT"), line(6, 26, "TO"), line(6, 34, "r")),
            refusal(declared("SELECT 'foo'", "GRANT SELECT ON TABLE t TO ROLE r;")));
        assertEquals(refused(line(4, 2, "DELETE"), line(4, 16, "USING"), line(4, 22, "u")),
            refusal(bare("SELECT 'foo'", "DELETE FROM t USING u WHERE a = b;")));
        assertEquals(refused(line(4, 2, "REVOKE"), line(4, 39, "x"), line(4, 41, "y")),
            refusal(bare("SELECT 'foo'", "REVOKE SELECT ON TABLE t FROM ROLE r x y;")));
        assertEquals(refused(line(4, 2, "DELETE"), line(4, 28, "c"), line(5, 0, "END")),
            refusal(bare("SELECT 'foo'", "DELETE FROM t WHERE a = b c;")));
        assertEquals(refused(line(4, 2, "SELECT"), line(4, 20, "b"), line(4, 22, "c")),
            refusal(bare("SELECT 'foo'", "SELECT DISTINCT a b c FROM t;")));
    }

    @Test
    public void aResumedQueryThatReadsOnLeavesOneLine() {
        assertEquals(refused(line(4, 2, "DELETE")), refusal(bare("SELECT 'foo'", "DELETE FROM t WHERE a = b;")));
        assertEquals(refused(line(4, 2, "REVOKE")), refusal(bare("SELECT 'foo'", "REVOKE SELECT ON TABLE t FROM ROLE r;")));
        assertEquals(refused(line(6, 2, "DELETE")), refusal(declared("SELECT 'foo'", "DELETE FROM t WHERE a = b;")));
        assertEquals(refused(line(4, 2, "DROP")), refusal(bare("SELECT 'foo'", "DROP TABLE t;")));
        assertEquals(refused(line(4, 2, "DELETE")), refusal(bare("SELECT 'foo'", "DELETE FROM t;")));
    }

    @Test
    public void insertAndWithResumeNothing() {
        assertEquals(refused(line(4, 2, "INSERT"), line(4, 16, "VALUES")),
            refusal(bare("SELECT 'foo'", "INSERT INTO t VALUES (a b c);")));
        assertEquals(refused(line(4, 2, "WITH"), line(4, 9, "AS")),
            refusal(bare("SELECT 'foo'", "WITH c AS (SELECT 1) SELECT * FROM c x y z;")));
        assertEquals(refused(line(1, 13, "WITH")), refusal("SELECT 'foo' WITH c AS (SELECT 1) SELECT * FROM c x y z"));
        assertEquals(refused(line(1, 16, "WITH")), refusal("SELECT a FROM t WITH c AS (SELECT 1) SELECT * FROM c x y z"));
    }

    @Test
    public void afterAnAliasedItemTheSameStatementsStopAtTwoLines() {
        assertEquals(refused(line(4, 2, "GRANT"), line(4, 26, "TO")),
            refusal(bare("SELECT 'foo' AS a", "GRANT SELECT ON TABLE t TO ROLE r;")));
        assertEquals(refused(line(4, 2, "UPDATE"), line(4, 11, "SET")),
            refusal(bare("SELECT 'foo' AS a", "UPDATE t SET a = b WHERE c = d;")));
        assertEquals(refused(line(4, 2, "DELETE"), line(4, 16, "WHERE")),
            refusal(bare("SELECT 'foo' AS a", "DELETE FROM t WHERE a = b;")));
        assertEquals(refused(line(4, 2, "DELETE"), line(4, 16, "WHERE")),
            refusal(bare("SELECT a FROM t", "DELETE FROM t WHERE a = b;")));
        assertEquals(refused(line(4, 2, "UPDATE"), line(4, 11, "SET")),
            refusal(bare("LET a := 1", "UPDATE t SET a = b WHERE c = d e f;")));
    }

    @Test
    public void aBareBeginStillStartsATransaction() {
        engine.execute("BEGIN");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("ROLLBACK");
        engine.execute("BEGIN;");
        engine.execute("INSERT INTO t VALUES (2)");
        engine.execute("COMMIT");
        assertEquals(2L, ((Number) engine.executeQuery("SELECT SUM(a) FROM t").getRows().get(0).getValue(0)).longValue());
    }
}
