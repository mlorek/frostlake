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

package dev.frostlake.executor;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The grammar-driven script splitter shared by the HTTP init-file runner and the console script
 * runner. The character-level splitters it replaced broke on exactly these inputs: a {@code '$$'}
 * INSIDE a string literal flipped their dollar-parity, and a multi-line literal whose first line
 * ended with {@code ;} split mid-literal.
 */
public class SqlScriptSplitterTest {

    @Test
    public void splitsSimpleStatements() {
        final List<String> parts = SqlScriptSplitter.split("SELECT 1; SELECT 2; SELECT 3");
        assertEquals(3, parts.size());
        assertEquals("SELECT 1", parts.get(0).trim());
        assertEquals("SELECT 3", parts.get(2).trim());
    }

    @Test
    public void dollarDollarInsideLiteralDoesNotFlipGrouping() {
        final List<String> parts = SqlScriptSplitter.split("SELECT '$$'; SELECT 2; SELECT 3;");
        assertEquals(3, parts.size());
        assertEquals("SELECT '$$'", parts.get(0).trim());
        assertEquals("SELECT 2", parts.get(1).trim());
    }

    @Test
    public void semicolonInsideMultiLineLiteralDoesNotSplit() {
        final List<String> parts = SqlScriptSplitter.split("SELECT 'a;\nb' AS v;\nSELECT 2");
        assertEquals(2, parts.size());
        assertTrue(parts.get(0).contains("'a;\nb'"), "literal must stay whole: " + parts.get(0));
    }

    @Test
    public void dollarQuotedBodyStaysOneStatement() {
        final String script = """
            CREATE PROCEDURE p()
            RETURNS INTEGER
            LANGUAGE SQL
            AS $$
            BEGIN
                RETURN 1;
            END;
            $$;
            SELECT 9
            """;
        final List<String> parts = SqlScriptSplitter.split(script);
        assertEquals(2, parts.size());
        assertTrue(parts.get(0).contains("RETURN 1;"), "body must stay whole: " + parts.get(0));
        assertEquals("SELECT 9", parts.get(1).trim());
    }

    @Test
    public void unparseableScriptFallsBackToLexerSplit() {
        // Not valid SQL, but the literal-aware lexer fallback still splits at the semicolons
        // without cutting the literal that contains one.
        final List<String> parts = SqlScriptSplitter.split("FROBNICATE 'a;b'; GLORP 2");
        assertEquals(2, parts.size());
        assertTrue(parts.get(0).contains("'a;b'"), "literal must stay whole: " + parts.get(0));
    }
}
