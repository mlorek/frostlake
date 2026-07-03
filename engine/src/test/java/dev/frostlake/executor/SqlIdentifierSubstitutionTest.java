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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link SqlIdentifierSubstitution} — lexer-driven binding of a parameter name into a SQL
 * body. The cases pin the behaviour that a plain {@code \b} regex got wrong: a name that also occurs
 * inside a string literal, a double-quoted identifier, or a comment must be left untouched.
 */
public class SqlIdentifierSubstitutionTest {

    @Test
    public void substitutesBareIdentifier() {
        assertEquals("SELECT 42 + 1",
            SqlIdentifierSubstitution.substitute("SELECT x + 1", "x", "42"));
    }

    @Test
    public void isCaseInsensitive() {
        assertEquals("SELECT 42 + 1",
            SqlIdentifierSubstitution.substitute("SELECT X + 1", "x", "42"));
    }

    @Test
    public void substitutesEveryOccurrence() {
        assertEquals("9 + 9 + 9",
            SqlIdentifierSubstitution.substitute("val + val + val", "val", "9"));
    }

    @Test
    public void leavesStringLiteralUntouched() {
        // The name inside the 'val' string must not be substituted — only the bare identifier.
        assertEquals("CASE WHEN col = 'val' THEN 1 END",
            SqlIdentifierSubstitution.substitute("CASE WHEN val = 'val' THEN 1 END", "val", "col"));
    }

    @Test
    public void leavesQuotedIdentifierUntouched() {
        assertEquals("SELECT \"val\" + 42",
            SqlIdentifierSubstitution.substitute("SELECT \"val\" + val", "val", "42"));
    }

    @Test
    public void leavesCommentUntouched() {
        assertEquals("SELECT 42 -- val here\n+ 1",
            SqlIdentifierSubstitution.substitute("SELECT val -- val here\n+ 1", "val", "42"));
    }

    @Test
    public void doesNotMatchWithinLongerIdentifier() {
        // 'value' is a different token than 'val' and must not be partially rewritten.
        assertEquals("SELECT value + 42",
            SqlIdentifierSubstitution.substitute("SELECT value + val", "val", "42"));
    }

    @Test
    public void substitutesWithColumnExpression() {
        assertEquals("t.salary * 2",
            SqlIdentifierSubstitution.substitute("val * 2", "val", "t.salary"));
    }
}
