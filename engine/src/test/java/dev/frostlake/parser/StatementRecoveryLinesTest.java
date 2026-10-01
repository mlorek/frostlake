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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Recovery lines around statements: what a later statement adds after an earlier fault, a stray ')' inside a block, a
 * bracketed query after an alias, MERGE's ON at the end of input and POSITION's IN form with a comma (live-verified).
 */
public class StatementRecoveryLinesTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String lines(final String... lines) {
        return "SQL compilation error:\n" + String.join("\n", lines);
    }

    private static String at(final int position, final String token) {
        return "syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void afterAFaultALaterStatementAddsOnlyWhatLiveReads() {
        assertEquals(lines(at(11, "y")), refusal("SELECT 1 x y; SELECT 1) x"));
        assertEquals(lines(at(11, "y")), refusal("SELECT 1 x y; ;"));
        assertEquals(lines(at(11, "y")), refusal("SELECT 1 x y;;"));
        assertEquals(lines(at(10, ";")), refusal("SELECT 1 +; ;"));
        assertEquals(lines(at(10, ";"), at(20, ")")), refusal("SELECT 1 +; SELECT 1) x"));
        assertEquals(lines(at(19, "<EOF>")), refusal("SELECT 1; SELECT (2"));
        assertEquals(lines(at(15, "RENAME"), at(37, "y")), refusal("ALTER TABLE t1 RENAME TO; SELECT 1 x y"));
    }

    @Test
    public void aStrayParenthesisInABlockReadsOnToTheNextStatement() {
        assertEquals(lines(at(14, ")"), at(19, "RETURN")), refusal("BEGIN SELECT 1) x; RETURN 1 1; END"));
        assertEquals(lines(at(14, ")"), at(19, "LET")), refusal("BEGIN SELECT 1) x; LET a := 1; END"));
        assertEquals(lines(at(14, ")"), at(29, "END")), refusal("BEGIN SELECT 1) x; SELECT 2; END"));
        assertEquals(lines(at(14, ")"), at(19, "END")), refusal("BEGIN SELECT 1) x; END"));
        assertEquals(lines(at(14, ")"), at(18, "y")), refusal("BEGIN SELECT 1) x y; RETURN 1; END"));
        assertEquals(lines(at(14, ")")), refusal("BEGIN SELECT 1); RETURN 1 1; END"));
        assertEquals(lines(at(18, ")")), refusal("BEGIN LET a := (1)); RETURN 1 1; END"));
    }

    @Test
    public void aBracketedQueryAfterAnAliasIsRefusedAtItsBracketAlone() {
        assertEquals(lines(at(11, "(")), refusal("SELECT a x (SELECT 1) FROM t)"));
        assertEquals(lines(at(11, "(")), refusal("SELECT a x (SELECT 1) FROM t"));
    }

    @Test
    public void mergesJoinConditionEndingInAnOpenCallIsTheEndOfInputTwice() {
        assertEquals(lines(at(30, "<EOF>"), at(30, "<EOF>")), refusal("MERGE INTO t USING t s ON ABS("));
        assertEquals(lines(at(34, "<EOF>"), at(34, "<EOF>")), refusal("MERGE INTO t USING t s ON a = ABS("));
        assertEquals(lines(at(40, "<EOF>"), at(40, "<EOF>")), refusal("MERGE INTO t USING t s ON a = 1 AND ABS("));
        assertEquals(lines(at(31, "<EOF>")), refusal("MERGE INTO t USING t s ON ABS(1"));
    }

    @Test
    public void positionsInFormRefusesTheCallsParenthesisAfterAComma() {
        assertEquals(lines(at(18, "<="), at(20, ">"), at(27, "'x'"), at(35, ")")),
            refusal("SELECT POSITION(1 <=> 1 IN 'x', 'y')"));
    }
}
