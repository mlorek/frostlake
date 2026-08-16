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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SPLIT_TO_TABLE splits a string into one row per part.
 *
 * <p>Its two parameters are DOCUMENTED as STRING and DELIMITER, and Snowflake does not accept either
 * by name — {@code SPLIT_TO_TABLE(STRING => 'a,b', DELIMITER => ',')} is refused outright. This class
 * was written against the named form and passed, which is the whole reason it is written positionally
 * now: the engine had been accepting a spelling no account will run. Whether a table function takes
 * {@code NAME => value} is a property of the FUNCTION — FLATTEN does, GENERATOR ignores what it cannot
 * use, this one refuses — so the refusals below are asserted, not assumed.
 */
public class SplitToTableTest extends BaseDatabaseTest {

    @Test
    public void testBasicSplitWithCommaDelimiter() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(SPLIT_TO_TABLE('apple,banana,cherry', ',')) ORDER BY INDEX");
        assertEquals(3, result.getRowCount());

        assertEquals(3, result.getColumns().size());
        assertEquals("SEQ", result.getColumns().get(0).getName());
        assertEquals("INDEX", result.getColumns().get(1).getName());
        assertEquals("VALUE", result.getColumns().get(2).getName());

        // SEQ numbers the INPUT RECORD, so all three rows share it and it starts at 1; INDEX is the
        // 1-based position of the part.
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(1L, result.getRows().get(0).getValue(1));
        assertEquals("apple", result.getRows().get(0).getValue(2));

        assertEquals(1L, result.getRows().get(1).getValue(0));
        assertEquals(2L, result.getRows().get(1).getValue(1));
        assertEquals("banana", result.getRows().get(1).getValue(2));

        assertEquals(1L, result.getRows().get(2).getValue(0));
        assertEquals(3L, result.getRows().get(2).getValue(1));
        assertEquals("cherry", result.getRows().get(2).getValue(2));
    }

    @Test
    public void testSplitWithPipeDelimiter() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(SPLIT_TO_TABLE('red|green|blue', '|')) ORDER BY INDEX");
        assertEquals(3, result.getRowCount());
        assertEquals("red", result.getRows().get(0).getValue(2));
        assertEquals("green", result.getRows().get(1).getValue(2));
        assertEquals("blue", result.getRows().get(2).getValue(2));
    }

    @Test
    public void testSplitWithSpaceDelimiter() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(SPLIT_TO_TABLE('one two three', ' ')) ORDER BY INDEX");
        assertEquals(3, result.getRowCount());
        assertEquals("one", result.getRows().get(0).getValue(2));
        assertEquals("two", result.getRows().get(1).getValue(2));
        assertEquals("three", result.getRows().get(2).getValue(2));
    }

    @Test
    public void testSplitWithMultiCharacterDelimiter() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(SPLIT_TO_TABLE('alpha::beta::gamma', '::')) ORDER BY INDEX");
        assertEquals(3, result.getRowCount());
        assertEquals("alpha", result.getRows().get(0).getValue(2));
        assertEquals("beta", result.getRows().get(1).getValue(2));
        assertEquals("gamma", result.getRows().get(2).getValue(2));
    }

    @Test
    public void testSplitEmptyString() {
        final ResultSet result = engine.executeQuery("SELECT * FROM TABLE(SPLIT_TO_TABLE('', ',')) ORDER BY INDEX");
        assertEquals(1, result.getRowCount());
        assertEquals("", result.getRows().get(0).getValue(2));
    }

    @Test
    public void testSplitSingleValue() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(SPLIT_TO_TABLE('onlyOne', ',')) ORDER BY INDEX");
        assertEquals(1, result.getRowCount());
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(1L, result.getRows().get(0).getValue(1));
        assertEquals("onlyOne", result.getRows().get(0).getValue(2));
    }

    @Test
    public void testSplitWithTrailingDelimiter() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(SPLIT_TO_TABLE('a,b,c,', ',')) ORDER BY INDEX");
        assertEquals(4, result.getRowCount());
        assertEquals("a", result.getRows().get(0).getValue(2));
        assertEquals("b", result.getRows().get(1).getValue(2));
        assertEquals("c", result.getRows().get(2).getValue(2));
        assertEquals("", result.getRows().get(3).getValue(2));
    }

    @Test
    public void testSplitWithLeadingDelimiter() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(SPLIT_TO_TABLE(',a,b,c', ',')) ORDER BY INDEX");
        assertEquals(4, result.getRowCount());
        assertEquals("", result.getRows().get(0).getValue(2));
        assertEquals("a", result.getRows().get(1).getValue(2));
        assertEquals("b", result.getRows().get(2).getValue(2));
        assertEquals("c", result.getRows().get(3).getValue(2));
    }

    @Test
    public void testSplitWithConsecutiveDelimiters() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(SPLIT_TO_TABLE('a,,b,,c', ',')) ORDER BY INDEX");
        assertEquals(5, result.getRowCount());
        assertEquals("a", result.getRows().get(0).getValue(2));
        assertEquals("", result.getRows().get(1).getValue(2));
        assertEquals("b", result.getRows().get(2).getValue(2));
        assertEquals("", result.getRows().get(3).getValue(2));
        assertEquals("c", result.getRows().get(4).getValue(2));
    }

    /** An EMPTY delimiter splits nowhere — the whole string comes back as a single row. */
    @Test
    public void anEmptyDelimiterDoesNotSplit() {
        final ResultSet result = engine.executeQuery("SELECT * FROM TABLE(SPLIT_TO_TABLE('abc', '')) ORDER BY INDEX");
        assertEquals(1, result.getRowCount());
        assertEquals("abc", result.getRows().get(0).getValue(2));
    }

    /** A NULL on either side contributes NO rows — not an empty-string row. */
    @Test
    public void aNullStringOrDelimiterYieldsNoRows() {
        assertEquals(0, engine.executeQuery(
            "SELECT * FROM TABLE(SPLIT_TO_TABLE(NULL, ',')) ORDER BY INDEX").getRowCount());
        assertEquals(0, engine.executeQuery(
            "SELECT * FROM TABLE(SPLIT_TO_TABLE('a,b', NULL)) ORDER BY INDEX").getRowCount());
    }

    @Test
    public void testSplitWithWhereClause() {
        final ResultSet result = engine.executeQuery("""
            SELECT VALUE FROM TABLE(SPLIT_TO_TABLE('apple,banana,cherry', ','))
            WHERE INDEX > 1
            ORDER BY INDEX
            """);
        assertEquals(2, result.getRowCount());
        assertEquals("banana", result.getRows().get(0).getValue(0));
        assertEquals("cherry", result.getRows().get(1).getValue(0));
    }

    @Test
    public void testSplitWithAlias() {
        final ResultSet result = engine.executeQuery(
            "SELECT VALUE, INDEX FROM TABLE(SPLIT_TO_TABLE('x,y,z', ',')) s ORDER BY INDEX");
        assertEquals(3, result.getRowCount());
        assertEquals("x", result.getRows().get(0).getValue(0));
        assertEquals(1L, result.getRows().get(0).getValue(1));
        assertEquals("y", result.getRows().get(1).getValue(0));
        assertEquals(2L, result.getRows().get(1).getValue(1));
        assertEquals("z", result.getRows().get(2).getValue(0));
        assertEquals(3L, result.getRows().get(2).getValue(1));
    }

    @Test
    public void testSplitWithOrderBy() {
        final ResultSet result = engine.executeQuery("""
            SELECT VALUE FROM TABLE(SPLIT_TO_TABLE('zebra,apple,monkey', ','))
            ORDER BY VALUE
            """);
        assertEquals(3, result.getRowCount());
        assertEquals("apple", result.getRows().get(0).getValue(0));
        assertEquals("monkey", result.getRows().get(1).getValue(0));
        assertEquals("zebra", result.getRows().get(2).getValue(0));
    }

    // ---- the refusals ------------------------------------------------------------------------

    /**
     * No parameter may be passed by name, and the refusal reports the FIRST named one with its
     * position in the argument list. The name is upper-cased however it was written, and a quoted one
     * keeps its quotes.
     */
    @Test
    public void aNamedArgumentIsRefused() {
        refused("SELECT * FROM TABLE(SPLIT_TO_TABLE(STRING => 'a,b', DELIMITER => ',')) ORDER BY INDEX",
            "invalid argument for function [SPLIT_TO_TABLE] unexpected argument [STRING] at position 1,");
        refused("SELECT * FROM TABLE(SPLIT_TO_TABLE(string => 'a,b')) ORDER BY INDEX",
            "invalid argument for function [SPLIT_TO_TABLE] unexpected argument [STRING] at position 1,");
        refused("SELECT * FROM TABLE(SPLIT_TO_TABLE(\"string\" => 'a,b'))",
            "invalid argument for function [SPLIT_TO_TABLE] unexpected argument [\"STRING\"] at position 1,");
        // A name after a positional argument is reported at ITS position, not at 1.
        refused("SELECT * FROM TABLE(SPLIT_TO_TABLE('a,b', DELIMITER => ',')) ORDER BY INDEX",
            "invalid argument for function [SPLIT_TO_TABLE] unexpected argument [DELIMITER] at position 2,");
        refused("SELECT * FROM TABLE(SPLIT_TO_TABLE('test', INVALID_PARAM => 'value')) ORDER BY INDEX",
            "invalid argument for function [SPLIT_TO_TABLE] unexpected argument [INVALID_PARAM] at position 2,");
    }

    /**
     * Both arguments are required — there is no default delimiter. Live spells the three arity
     * refusals three different ways, and the one-argument call is the odd one: it names the internal
     * SPLIT rewrite the arguments were folded into, and carries no position at all.
     */
    @Test
    public void aCallWithoutExactlyTwoArgumentsIsRefused() {
        assertEquals("SQL compilation error: error line 1 at position 20\n"
            + "not enough arguments for function [SPLIT_TO_TABLE], expected 1, got 0",
            messageOf("SELECT * FROM TABLE(SPLIT_TO_TABLE()) ORDER BY INDEX"));
        assertEquals("SQL compilation error: error line 0 at position -1\n"
            + "not enough arguments for function [SPLIT('a,b,c' AS \"1\")], expected 2, got 1",
            messageOf("SELECT * FROM TABLE(SPLIT_TO_TABLE('a,b,c')) ORDER BY INDEX"));
        assertEquals("SQL compilation error: error line 1 at position 20\n"
            + "too many arguments for function [SPLIT_TO_TABLE] expected 2, got 3",
            messageOf("SELECT * FROM TABLE(SPLIT_TO_TABLE('a', ',', 'x')) ORDER BY INDEX"));
    }

    /** Both parameters take a VARCHAR, and a number is refused rather than converted. */
    @Test
    public void aNonStringArgumentIsRefused() {
        assertEquals("SQL compilation error:\ninvalid type [NUMBER(3,0)] for parameter '1'",
            messageOf("SELECT * FROM TABLE(SPLIT_TO_TABLE(123, ',')) ORDER BY INDEX"));
        assertEquals("SQL compilation error:\ninvalid type [NUMBER(1,0)] for parameter '2'",
            messageOf("SELECT * FROM TABLE(SPLIT_TO_TABLE('1,2', 1)) ORDER BY INDEX"));
    }

    private void refused(final String sql, final String expectedDetail) {
        assertEquals("SQL compilation error: error line 1 at position 20\n" + expectedDetail,
            messageOf(sql), sql);
    }

    /** The root-cause message of a statement that must fail. */
    private String messageOf(final String sql) {
        final RuntimeException thrown = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, "Snowflake refuses this statement: " + sql);
        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return String.valueOf(root.getMessage());
    }
}
