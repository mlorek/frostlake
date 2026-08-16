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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A select item's ALIAS followed by '(' is refused AT THE PARENTHESIS, as one line: whatever the
 * bracket holds and whatever follows it in the statement adds nothing. The alias may be bare,
 * written after AS, quoted, or a word such as COLLATE that reads as one when no string follows it,
 * and the rule holds inside a CREATE … AS, an INSERT … SELECT and a scripting block alike.
 *
 * <pre>
 *   SELECT 'a' foo ('x')                          '(' at 15
 *   SELECT 1 foo (2) FROM (SELECT 1 AS z)         '(' at 13, and nothing for the FROM
 *   SELECT 1 (2)                                  '2' — a plain expression is not an alias
 * </pre>
 *
 * <p>Inside a block the recovery stacks a line after the '(' (scripting's
 * {@code AliasParenthesisSecondLineTest}); inside a subquery or a CTE the lines differ again
 * ({@link NestedAliasParenthesisTest}).
 */
public class AliasParenthesisAnchorTest extends BaseDatabaseTest {

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

    private static String at(final int line, final int position, final String token) {
        return "SQL compilation error:\nsyntax error line " + line + " at position " + position
            + " unexpected '" + token + "'.";
    }

    @Test
    public void theParenthesisAfterAnAliasIsNamed() {
        assertEquals(at(1, 15, "("), refusal("SELECT 'a' foo ('x')"));
        assertEquals(at(1, 18, "("), refusal("SELECT 'a' AS foo ('x')"));
        assertEquals(at(1, 13, "("), refusal("SELECT 1 foo (2)"));
        assertEquals(at(1, 15, "("), refusal("SELECT 1 \"foo\" (2)"));
        assertEquals(at(1, 20, "("), refusal("SELECT 'a' AS \"x y\" ('z')"));
        assertEquals(at(1, 12, "("), refusal("SELECT 1 foo(2)"));
        assertEquals(at(1, 16, "("), refusal("SELECT 1 x, 2 y ('z')"));
        assertEquals(at(1, 14, "("), refusal("SELECT a, b c (1) FROM t"));
    }

    @Test
    public void collateWithoutAStringIsAnAliasToo() {
        assertEquals(at(1, 19, "("), refusal("SELECT 'a' collate ('x')"));
        assertEquals(at(1, 19, "("), refusal("SELECT 'a' COLLATE ('x') FROM (SELECT 1 AS z)"));
        assertEquals(at(1, 28, "("), refusal("SELECT 'a' COLLATE 'en' foo ('x')"));
    }

    @Test
    public void whateverTheBracketHoldsTheLineIsTheSame() {
        assertEquals(at(1, 15, "("), refusal("SELECT 'a' foo ()"));
        assertEquals(at(1, 15, "("), refusal("SELECT 'a' foo ("));
        assertEquals(at(1, 16, "("), refusal("SELECT 1 AS foo ("));
        assertEquals(at(1, 13, "("), refusal("SELECT 1 foo ())"));
        assertEquals(at(1, 13, "("), refusal("SELECT 1 foo ((2))"));
        assertEquals(at(1, 13, "("), refusal("SELECT 1 foo (DISTINCT 2)"));
        assertEquals(at(1, 13, "("), refusal("SELECT 1 foo (*)"));
        assertEquals(at(1, 13, "("), refusal("SELECT 1 foo (SELECT 2)"));
        assertEquals(at(1, 13, "("), refusal("SELECT 1 foo (SELECT 2 FROM)"));
    }

    @Test
    public void nothingAfterTheBracketAddsALine() {
        assertEquals(at(1, 13, "("), refusal("SELECT 1 foo (2) FROM (SELECT 1 AS z)"));
        assertEquals(at(1, 13, "("), refusal("SELECT 1 foo (2), 3"));
        assertEquals(at(1, 13, "("), refusal("SELECT 1 foo (2) WHERE TRUE"));
        assertEquals(at(1, 13, "("), refusal("SELECT 1 foo (2) + (3)"));
        assertEquals(at(1, 13, "("), refusal("SELECT 1 foo (2) foo2 (3)"));
        assertEquals(at(1, 13, "("), refusal("SELECT 1 foo (2) UNION SELECT 3 bar (4)"));
        assertEquals(at(1, 13, "("), refusal("SELECT 1 foo ('x') FROM (SELECT 1 AS z) WHERE (1 = 1)"));
        assertEquals(at(1, 13, "("), refusal("SELECT 1 foo (2) FROM t WHERE a = 1 GROUP BY 1"));
    }

    @Test
    public void theRuleHoldsInsideOtherStatements() {
        assertEquals(at(1, 32, "("), refusal("CREATE TABLE t2 AS SELECT 1 foo (2)"));
        assertEquals(at(1, 31, "("), refusal("CREATE VIEW v2 AS SELECT 1 foo (2)"));
        assertEquals(at(1, 27, "("), refusal("INSERT INTO t SELECT 1 foo (2)"));
        assertEquals(at(1, 20, "("), refusal("EXECUTE IMMEDIATE $$ BEGIN SELECT 1 foo ('x'); END $$"));
        assertEquals(at(3, 15, "("),
            refusal("EXECUTE IMMEDIATE $$\nBEGIN\n  SELECT 1 foo ('x');\n  RETURN 1;\nEND;\n$$"));
    }

    @Test
    public void commentsAndLineBreaksKeepThePosition() {
        assertEquals(at(2, 0, "("), refusal("SELECT 'a' foo\n('x')"));
        assertEquals(at(1, 23, "("), refusal("SELECT 'a' foo /* c */ ('x')"));
        assertEquals(at(1, 13, "("), refusal("SELECT 1 foo (2) -- trailing comment"));
    }

    /** The shapes that are not an alias before a bracket keep the line both engines already agree on. */
    @Test
    public void otherBracketsKeepTheirOwnAnchors() {
        assertEquals(at(1, 10, "2"), refusal("SELECT 1 (2)"));
        assertEquals(at(1, 12, "'x'"), refusal("SELECT 'a' ('x')"));
        assertEquals(at(1, 35, "'x'"), refusal("SELECT * FROM (SELECT 1 AS a) foo ('x')"));
        assertEquals(at(1, 35, "2"), refusal("SELECT 1 FROM (SELECT 1 AS z) foo (2)"));
        assertEquals(at(1, 13, "'x'"), refusal("SELECT 1 foo 'x'"));
    }
}
