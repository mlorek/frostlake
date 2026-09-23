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

package dev.frostlake.udf;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A SQL UDF expression body that stops before its expression does is refused at CREATE where the frame the
 * account reads it in runs out: at the frame's closing parenthesis, one past the body, and at the end of input
 * too while the body leaves a parenthesis of its own open; or at the end of input alone when the body's open
 * parenthesis takes the closing one. Positions follow the body as written, trailing blanks and line breaks
 * included. Every cell is live-verified.
 */
public class SqlUdfUnfinishedBodyTest extends BaseDatabaseTest {

    private static final String REFUSED = "Compilation of SQL UDF failed: SQL compilation error:";

    /** "created", or the refusal on one line. */
    private String create(final String sql) {
        try {
            engine.execute(sql);
            return "created";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** Live's body syntax error, one per {line, position, token} triple, on one line. */
    private static String syntax(final String... errors) {
        final StringBuilder refusal = new StringBuilder(REFUSED);
        for (int i = 0; i < errors.length; i += 3) {
            refusal.append("|syntax error line ").append(errors[i]).append(" at position ").append(errors[i + 1])
                .append(" unexpected '").append(errors[i + 2]).append("'.");
        }
        return refusal.toString();
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], create(cell[0]), cell[0]);
        }
    }

    @Test
    public void anUnfinishedBodyIsRefusedAtTheClosingParenthesisOfItsFrame() {
        assertCells(new String[][] {
            {"CREATE FUNCTION uf1() RETURNS INT AS $$1 +$$", syntax("1", "4", ")")},
            {"CREATE FUNCTION uf2() RETURNS INT AS $$1 -$$", syntax("1", "4", ")")},
            {"CREATE FUNCTION uf3() RETURNS INT AS $$1 *$$", syntax("1", "4", ")")},
            {"CREATE FUNCTION uf4(a BOOLEAN) RETURNS BOOLEAN AS $$a AND$$", syntax("1", "6", ")")},
            {"CREATE FUNCTION uf5() RETURNS INT AS '1 +'", syntax("1", "4", ")")},
            {"CREATE FUNCTION uf6() RETURNS INT AS $$1 +-$$", syntax("1", "5", ")")},
            {"CREATE FUNCTION uf7() RETURNS INT AS $$1 BETWEEN$$", syntax("1", "10", ")")},
            {"CREATE FUNCTION uf8() RETURNS VARCHAR AS $$'a' ||$$", syntax("1", "7", ")")},
            {"CREATE FUNCTION uf9() RETURNS INT AS $$CASE WHEN 1 = 1 THEN 1$$", syntax("1", "23", ")")},
            {"CREATE FUNCTION uf10(x INT) RETURNS BOOLEAN AS $$x NOT IN$$", syntax("1", "9", ")")},
            {"CREATE FUNCTION uf11(x INT) RETURNS BOOLEAN AS $$x IS NULL AND$$", syntax("1", "14", ")")},
            {"CREATE FUNCTION uf12(x INT) RETURNS BOOLEAN AS $$x BETWEEN 1 AND$$", syntax("1", "16", ")")},
            {"CREATE FUNCTION uf13(x INT) RETURNS BOOLEAN AS $$x:$$", syntax("1", "3", ")")},
            {"CREATE FUNCTION uf14(x INT) RETURNS BOOLEAN AS $$x[$$", syntax("1", "3", ")")},
            {"CREATE FUNCTION uf15() RETURNS ARRAY AS $$[1,$$", syntax("1", "4", ")")},
            {"CREATE FUNCTION uf16() RETURNS OBJECT AS $${'a':$$", syntax("1", "6", ")")},
            {"CREATE FUNCTION uf17() RETURNS INT AS $$(1 + 2) *$$", syntax("1", "10", ")")},
            {"CREATE FUNCTION uf18() RETURNS BOOLEAN AS $$NOT$$", syntax("1", "4", ")")},
            {"CREATE FUNCTION uf19() RETURNS INT AS $$-$$", syntax("1", "2", ")")},
            {"CREATE FUNCTION uf20() RETURNS INT AS $$1::$$", syntax("1", "4", ")")},
            {"CREATE FUNCTION uf21() RETURNS TABLE (a INT) AS $$1 +$$", syntax("1", "4", ")")},
        });
    }

    @Test
    public void thePositionFollowsTheBodyAsWritten() {
        assertCells(new String[][] {
            {"CREATE FUNCTION up1() RETURNS INT AS $$1 + $$", syntax("1", "5", ")")},
            {"CREATE FUNCTION up2() RETURNS INT AS $$1 +   $$", syntax("1", "7", ")")},
            {"CREATE FUNCTION up3() RETURNS INT AS $$ 1 +$$", syntax("1", "5", ")")},
            {"CREATE FUNCTION up4() RETURNS INT AS $$1 + /* c */$$", syntax("1", "12", ")")},
            {"CREATE FUNCTION up5() RETURNS INT AS $$1 +\n$$", syntax("2", "0", ")")},
            {"CREATE FUNCTION up6() RETURNS INT AS $$1 +\n  $$", syntax("2", "2", ")")},
            {"CREATE FUNCTION up7() RETURNS INT AS $$1\n+$$", syntax("2", "1", ")")},
            {"CREATE FUNCTION up8() RETURNS INT AS $$\n1 +$$", syntax("2", "3", ")")},
            {"CREATE FUNCTION up9() RETURNS INT AS $$1 +\n2 *$$", syntax("2", "3", ")")},
            {"CREATE FUNCTION up10(s VARCHAR) RETURNS VARCHAR AS $$s ||\n\n$$", syntax("3", "0", ")")},
        });
    }

    @Test
    public void anOpenParenthesisOfTheBodyAddsTheEndOfInput() {
        assertCells(new String[][] {
            {"CREATE FUNCTION uo1() RETURNS INT AS $$($$", syntax("1", "2", ")", "1", "3", "<EOF>")},
            {"CREATE FUNCTION uo2() RETURNS INT AS $$IFF(TRUE, 1,$$", syntax("1", "13", ")", "1", "14", "<EOF>")},
            {"CREATE FUNCTION uo3() RETURNS INT AS $$CAST(1 AS$$", syntax("1", "10", ")", "1", "11", "<EOF>")},
            {"CREATE FUNCTION uo4() RETURNS INT AS $$((1 +$$", syntax("1", "6", ")", "1", "7", "<EOF>")},
            {"CREATE FUNCTION uo5() RETURNS INT AS $$ABS(1 +$$", syntax("1", "8", ")", "1", "9", "<EOF>")},
            {"CREATE FUNCTION uo6() RETURNS BOOLEAN AS $$EXISTS ($$", syntax("1", "9", ")", "1", "10", "<EOF>")},
            {"CREATE FUNCTION uo7(x INT) RETURNS BOOLEAN AS $$x = ANY ($$", syntax("1", "10", ")", "1", "11", "<EOF>")},
            {"CREATE FUNCTION uo8() RETURNS INT AS $$(1 +\n$$", syntax("2", "0", ")", "2", "1", "<EOF>")},
            {"CREATE FUNCTION uo9() RETURNS OBJECT AS $$OBJECT_CONSTRUCT('a',$$", syntax("1", "22", ")", "1", "23", "<EOF>")},
        });
    }

    @Test
    public void anOpenParenthesisThatTakesTheClosingOneLeavesTheEndOfInput() {
        assertCells(new String[][] {
            {"CREATE FUNCTION ue1() RETURNS INT AS $$ABS($$", syntax("1", "6", "<EOF>")},
            {"CREATE FUNCTION ue2() RETURNS INT AS $$1 + (2$$", syntax("1", "8", "<EOF>")},
            {"CREATE FUNCTION ue3() RETURNS INT AS $$ABS(1$$", syntax("1", "7", "<EOF>")},
            {"CREATE FUNCTION ue4() RETURNS BOOLEAN AS $$CASE WHEN (1 = 1$$", syntax("1", "18", "<EOF>")},
            {"CREATE FUNCTION ue5() RETURNS INT AS $$ABS(\n$$", syntax("2", "1", "<EOF>")},
            {"CREATE FUNCTION ue6() RETURNS INT AS $$(1 + -- c$$", syntax("1", "11", "<EOF>")},
            {"CREATE FUNCTION ue7() RETURNS ARRAY AS $$ARRAY_CONSTRUCT($$", syntax("1", "18", "<EOF>")},
            {"CREATE FUNCTION ue8() RETURNS INT AS $$COUNT(*) OVER ($$", syntax("1", "17", "<EOF>")},
        });
    }

    @Test
    public void aBodyWithNothingToReadIsUnfinishedToo() {
        assertCells(new String[][] {
            {"CREATE FUNCTION un1() RETURNS INT AS $$$$", syntax("1", "1", ")")},
            {"CREATE FUNCTION un2() RETURNS INT AS $$ $$", syntax("1", "2", ")")},
            {"CREATE FUNCTION un3() RETURNS INT AS ''", syntax("1", "1", ")")},
            {"CREATE FUNCTION un4() RETURNS INT AS $$/* c */$$", syntax("1", "8", ")")},
            {"CREATE FUNCTION un5() RETURNS INT AS $$-- c$$", syntax("1", "6", "<EOF>")},
        });
    }

    @Test
    public void aFinishedBodyIsCreated() {
        assertCells(new String[][] {
            {"CREATE FUNCTION uc1() RETURNS INT AS $$(1 + 2)$$", "created"},
            {"CREATE FUNCTION uc2(x INT) RETURNS BOOLEAN AS $$x IS NOT NULL$$", "created"},
            {"CREATE FUNCTION uc3() RETURNS INT AS $$ABS(-1) + 1$$", "created"},
        });
    }
}
