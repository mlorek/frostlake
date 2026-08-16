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
 * A call left open, with nothing inside it, at the very end of a SELECT list. The account reports the end of
 * the input and then a second line pointing BACKWARDS at the call's '(' — for a call standing in the list
 * itself, a whole item or an operand, in a query of its own, of an INSERT or of a CREATE … AS.
 *
 * <pre>
 *   SELECT ABS(                '&lt;EOF&gt;' at 11, then '(' at 10
 *   SELECT UPPER(--1, 1)       '&lt;EOF&gt;' at 20, then '(' at 12 — the comment runs to the end
 *   SELECT ABS(1               '&lt;EOF&gt;' alone — the call has an argument
 * </pre>
 */
public class UnclosedCallLinesTest extends BaseDatabaseTest {

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

    private static String line(final int line, final int position, final String token) {
        return "\nsyntax error line " + line + " at position " + position + " unexpected '" + token + "'.";
    }

    /** The end-of-input line at {@code end}, then the '(' at {@code paren}, both on line 1. */
    private static String endThenParen(final int end, final int paren) {
        return "SQL compilation error:" + line(1, end, "<EOF>") + line(1, paren, "(");
    }

    @Test
    public void theEndThenTheCallsOpeningBracket() {
        assertEquals(endThenParen(11, 10), refusal("SELECT ABS("));
        assertEquals(endThenParen(13, 12), refusal("SELECT COUNT("));
        assertEquals(endThenParen(11, 10), refusal("SELECT IFF("));
        assertEquals(endThenParen(16, 15), refusal("SELECT COALESCE("));
        assertEquals(endThenParen(15, 14), refusal("SELECT TO_CHAR("));
        assertEquals(endThenParen(11, 10), refusal("select abs("));
        assertEquals(endThenParen(12, 11), refusal("SELECT ABS ("));
    }

    @Test
    public void aCommentOrALineBreakAfterTheBracket() {
        assertEquals(endThenParen(20, 12), refusal("SELECT UPPER(--1, 1)"));
        assertEquals(endThenParen(16, 10), refusal("SELECT ABS( -- x"));
        assertEquals(endThenParen(20, 12), refusal("SELECT UPPER(/* x */"));
        assertEquals("SQL compilation error:" + line(2, 0, "<EOF>") + line(1, 10, "("), refusal("SELECT ABS(\n"));
        assertEquals("SQL compilation error:" + line(2, 4, "<EOF>") + line(1, 10, "("), refusal("SELECT ABS(\n-- c"));
    }

    @Test
    public void anyItemOrOperandOfTheList() {
        assertEquals(endThenParen(14, 13), refusal("SELECT 1, ABS("));
        assertEquals(endThenParen(15, 14), refusal("SELECT 1 + ABS("));
        assertEquals(endThenParen(21, 20), refusal("SELECT ABS(1), UPPER("));
        assertEquals(endThenParen(22, 21), refusal("SELECT ABS(1) + UPPER("));
        assertEquals(endThenParen(20, 19), refusal("SELECT DISTINCT ABS("));
        assertEquals(endThenParen(16, 15), refusal("SELECT t.a, ABS("));
        assertEquals(endThenParen(27, 26), refusal("SELECT UPPER('a') || LOWER("));
        assertEquals(endThenParen(16, 15), refusal("SELECT 1 x, ABS("));
        assertEquals(endThenParen(24, 23), refusal("SELECT ABS(1) AS y, ABS("));
        assertEquals(endThenParen(20, 19), refusal("SELECT 2, 3 + UPPER("));
        assertEquals(endThenParen(16, 15), refusal("SELECT (1), ABS("));
        assertEquals(endThenParen(14, 13), refusal("SELECT a.b, c("));
    }

    @Test
    public void aQueryInsideAnotherStatementOrASetOperation() {
        assertEquals(endThenParen(25, 24), refusal("INSERT INTO t SELECT ABS("));
        assertEquals(endThenParen(29, 28), refusal("INSERT INTO t (a) SELECT ABS("));
        assertEquals(endThenParen(30, 29), refusal("CREATE TABLE t2 AS SELECT ABS("));
        assertEquals(endThenParen(45, 44), refusal("SELECT a FROM t WHERE a = 1 UNION SELECT ABS("));
    }

    @Test
    public void aCallWithAnArgumentOrNoCallAtAllIsTheEndAlone() {
        assertEquals("SQL compilation error:" + line(1, 12, "<EOF>"), refusal("SELECT ABS(1"));
        assertEquals("SQL compilation error:" + line(1, 13, "<EOF>"), refusal("SELECT ABS(1,"));
        assertEquals("SQL compilation error:" + line(1, 8, "<EOF>"), refusal("SELECT ("));
    }
}
