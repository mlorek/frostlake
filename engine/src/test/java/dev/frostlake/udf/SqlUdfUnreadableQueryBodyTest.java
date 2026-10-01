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
 * A SQL UDF body holding a query that runs out of text is refused at CREATE at the query's keyword, in the positions
 * of the frame of parentheses the body is read in: the body's first word, the first query written in parentheses
 * inside it, and that query's closing parenthesis when the query stops there. A bare CASE is refused at what follows
 * it, a CAST abandoned before its AS leaves the frame closed, a parenthesised list of values is a ROW no declared type
 * takes, a literal left open is refused at the frame's end, and a character that is no part of any expression is
 * refused with the character after it rather than dropped. A body the account reads with a bare select-list alias is
 * created, and so is one this grammar stops reading on a word, which the account's grammar may read. Every cell is
 * live-verified.
 */
public class SqlUdfUnreadableQueryBodyTest extends BaseDatabaseTest {

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

    /** Each {name, signature and RETURNS, body, expected} cell. */
    private void assertBodies(final String[][] cells) {
        for (final String[] cell : cells) {
            final String ddl = "CREATE FUNCTION " + cell[0] + " AS $$" + cell[1] + "$$";
            assertEquals(cell[2], create(ddl), ddl);
        }
    }

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (a INT)");
    }

    @Test
    public void aQueryBodyThatDoesNotReadIsRefusedAtItsKeyword() {
        assertBodies(new String[][] {
            {"q1() RETURNS INT", "SELECT 1 +", syntax("1", "1", "SELECT")},
            {"q2() RETURNS INT", "SELECT a FROM", syntax("1", "1", "SELECT")},
            {"q3() RETURNS INT", "SELECT 1 WHERE", syntax("1", "1", "SELECT")},
            {"q4() RETURNS INT", "SELECT 1 UNION", syntax("1", "1", "SELECT")},
            {"q5() RETURNS INT", "SELECT CASE", syntax("1", "1", "SELECT")},
            {"q6() RETURNS INT", "SELECT", syntax("1", "1", "SELECT")},
            {"q7(x INT) RETURNS INT", "SELECT x IS", syntax("1", "1", "SELECT")},
            {"q8() RETURNS TABLE (a INT)", "SELECT 1 +", syntax("1", "1", "SELECT")},
            {"q25() RETURNS INT", "  SELECT 1 +", syntax("1", "3", "SELECT")},
            {"q26() RETURNS INT", "\nSELECT 1 +", syntax("2", "0", "SELECT")},
            {"q27() RETURNS INT", "select 1 +", syntax("1", "1", "select")},
            {"q28() RETURNS INT", "SELECT 1 UNION SELECT", syntax("1", "1", "SELECT")},
            {"q36() RETURNS INT", "SELECT 1;garbage", syntax("1", "1", "SELECT")},
            {"q37() RETURNS INT", "SELECT 1 FROM t JOIN", syntax("1", "1", "SELECT")},
            {"n6() RETURNS INT", "SELECT 1 + -- c\n", syntax("1", "1", "SELECT")},
        });
    }

    @Test
    public void theFirstParenthesisedQueryInsideIsNamedToo() {
        assertBodies(new String[][] {
            {"q9() RETURNS INT", "WITH c AS (SELECT 1 AS a) SELECT a FROM c WHERE",
                syntax("1", "1", "WITH", "1", "12", "SELECT")},
            {"q10() RETURNS INT", "SELECT 1 FROM (SELECT", syntax("1", "1", "SELECT", "1", "16", "SELECT")},
            {"q19() RETURNS INT", "SELECT 1 FROM (SELECT 1) WHERE", syntax("1", "1", "SELECT", "1", "16", "SELECT")},
            {"q20() RETURNS INT", "SELECT 1 FROM (SELECT 1 +",
                syntax("1", "1", "SELECT", "1", "16", "SELECT", "1", "26", ")")},
            {"q21() RETURNS INT", "SELECT (SELECT 1 +)", syntax("1", "1", "SELECT", "1", "9", "SELECT", "1", "19", ")")},
            {"q24() RETURNS INT", "WITH c AS (SELECT 1 +) SELECT a FROM c",
                syntax("1", "1", "WITH", "1", "12", "SELECT", "1", "22", ")")},
            {"q29() RETURNS INT", "SELECT 1 UNION (SELECT 1 +)",
                syntax("1", "1", "SELECT", "1", "17", "SELECT", "1", "27", ")")},
            {"q34() RETURNS INT", "SELECT EXISTS (SELECT", syntax("1", "1", "SELECT", "1", "16", "SELECT")},
            {"q38() RETURNS INT", "SELECT 1 FROM (WITH c AS (SELECT 1) SELECT", syntax("1", "1", "SELECT", "1", "16", "WITH")},
            {"n4() RETURNS INT", "SELECT 1 FROM (SELECT 1) t1, (SELECT 2) t2 WHERE",
                syntax("1", "1", "SELECT", "1", "16", "SELECT")},
            {"n5() RETURNS INT", "WITH c AS (SELECT 1 AS a), d AS (SELECT 2 AS b) SELECT",
                syntax("1", "1", "WITH", "1", "12", "SELECT")},
            {"n10() RETURNS INT", "SELECT 1 FROM (SELECT 1 FROM (SELECT 1)) WHERE",
                syntax("1", "1", "SELECT", "1", "16", "SELECT")},
            {"n11() RETURNS INT", "SELECT 1 FROM (SELECT 1 FROM (SELECT 1 +))",
                syntax("1", "1", "SELECT", "1", "16", "SELECT", "1", "41", ")")},
            {"n12() RETURNS INT", "SELECT 1 FROM (SELECT 1 +) WHERE",
                syntax("1", "1", "SELECT", "1", "16", "SELECT", "1", "26", ")")},
            {"n13() RETURNS INT", "SELECT (SELECT 1), (SELECT 2) FROM", syntax("1", "1", "SELECT", "1", "9", "SELECT")},
            {"n14() RETURNS INT", "SELECT a FROM t WHERE a = (SELECT", syntax("1", "1", "SELECT", "1", "28", "SELECT")},
        });
    }

    @Test
    public void anExpressionBodyNamesTheQueryItBrokeIn() {
        assertBodies(new String[][] {
            {"q11() RETURNS INT", "(SELECT", syntax("1", "2", "SELECT", "1", "9", "<EOF>")},
            {"q12(x INT) RETURNS INT", "x IN (SELECT", syntax("1", "7", "SELECT", "1", "14", "<EOF>")},
            {"q15() RETURNS INT", "(SELECT 1", syntax("1", "11", "<EOF>")},
            {"e2(x INT) RETURNS INT", "(SELECT 1 +)", syntax("1", "2", "SELECT")},
            {"e3(x INT) RETURNS INT", "ABS((SELECT 1 +))", syntax("1", "6", "SELECT")},
            {"e4(x INT) RETURNS INT", "x + (SELECT", syntax("1", "6", "SELECT", "1", "13", "<EOF>")},
            {"e5(x INT) RETURNS INT", "(SELECT 1 FROM)", syntax("1", "2", "SELECT")},
            {"e6(x INT) RETURNS INT", "EXISTS (SELECT 1 WHERE)", syntax("1", "23", ")")},
            {"e7(x INT) RETURNS INT", "(SELECT 1) + (SELECT", syntax("1", "15", "SELECT", "1", "22", "<EOF>")},
            {"e8(x INT) RETURNS INT", "x IN (SELECT 1 +", syntax("1", "7", "SELECT")},
            {"e10(x INT) RETURNS INT", "(SELECT 1 FROM (SELECT", syntax("1", "2", "SELECT", "1", "17", "SELECT")},
            {"e11(x INT) RETURNS INT", "1 + (SELECT 1) 2", syntax("1", "16", "2")},
        });
    }

    @Test
    public void aWholeParenthesisedQueryIsReadAsAnExpression() {
        assertBodies(new String[][] {
            {"q13() RETURNS INT", "SELECT 1 FROM (SELECT 1)) x", syntax("1", "27", "x")},
            {"q14() RETURNS INT", "SELECT ABS(1)) + 1", syntax("1", "19", ")")},
            {"q30() RETURNS INT", "(SELECT 1) UNION SELECT", syntax("1", "12", "UNION")},
            {"v22() RETURNS INT", "(SELECT 1) EXCEPT SELECT", syntax("1", "12", "EXCEPT")},
            {"v23() RETURNS INT", "(SELECT 1) UNION (SELECT", syntax("1", "12", "UNION", "1", "19", "SELECT")},
            {"v24() RETURNS INT", "(SELECT 1) UNION ALL SELECT 1 FROM", syntax("1", "12", "UNION")},
            {"m6() RETURNS INT", "(SELECT 1) UNION SELECT 2", "created"},
            {"m7() RETURNS INT", "(SELECT 1) UNION ALL (SELECT 2)", "created"},
            {"m11() RETURNS INT", "(SELECT 1) + (SELECT 2)", "created"},
        });
    }

    @Test
    public void aBodyTheAccountReadsWithABareSelectAliasIsCreated() {
        assertBodies(new String[][] {
            {"q16() RETURNS INT", "SELECT CASE WHEN TRUE THEN 1 END CASE", "created"},
            {"n19() RETURNS INT", "SELECT 1 CASE", "created"},
            {"n20() RETURNS INT", "SELECT 1 JOIN", "created"},
            {"n21() RETURNS INT", "SELECT 1 WHEN", "created"},
            {"n22() RETURNS INT", "WITH c AS (SELECT 1 AS a) SELECT a FROM c", "created"},
        });
    }

    @Test
    public void aBareCaseIsRefusedAtWhatFollowsIt() {
        assertBodies(new String[][] {
            {"c1(x INT) RETURNS BOOLEAN", "CASE", syntax("1", "5", ")")},
            {"c2(x INT) RETURNS BOOLEAN", "CASE\n", syntax("2", "0", ")")},
            {"c3(x INT) RETURNS BOOLEAN", "1 + CASE", syntax("1", "9", ")")},
            {"c4(x INT) RETURNS BOOLEAN", "(CASE", syntax("1", "6", ")", "1", "7", "<EOF>")},
            {"c5(x INT) RETURNS BOOLEAN", "1 + (CASE", syntax("1", "10", ")", "1", "11", "<EOF>")},
            {"c6(x INT) RETURNS BOOLEAN", "CASE x", syntax("1", "7", ")")},
            {"c9(x INT) RETURNS BOOLEAN", "ABS(CASE", syntax("1", "9", ")", "1", "10", "<EOF>")},
            {"c10(x INT) RETURNS BOOLEAN", "ABS(CASE)", syntax("1", "9", ")")},
            {"c11(x INT) RETURNS BOOLEAN", "CASE CASE", syntax("1", "10", ")")},
            {"c12(x INT) RETURNS BOOLEAN", "NOT CASE", syntax("1", "9", ")")},
            {"c13(x INT) RETURNS BOOLEAN", "-CASE", syntax("1", "6", ")")},
            {"c14(x INT) RETURNS BOOLEAN", "x = CASE", syntax("1", "9", ")")},
            {"c15(x INT) RETURNS BOOLEAN", "CASE -- c\n", syntax("2", "0", ")")},
            {"c16(x INT) RETURNS BOOLEAN", "IFF(TRUE, CASE", syntax("1", "15", ")", "1", "16", "<EOF>")},
            {"c17(x INT) RETURNS BOOLEAN", "((CASE", syntax("1", "7", ")", "1", "8", "<EOF>")},
            {"c18(x INT) RETURNS BOOLEAN", "CASE)", syntax("1", "5", ")", "1", "6", ")")},
            {"c19(x INT) RETURNS BOOLEAN", "  CASE", syntax("1", "7", ")")},
            {"c20(x INT) RETURNS BOOLEAN", "x AND CASE", syntax("1", "11", ")")},
            {"c25(x INT) RETURNS BOOLEAN", "x IN (CASE", syntax("1", "11", ")", "1", "12", "<EOF>")},
            {"c26(x INT) RETURNS BOOLEAN", "CASE /* c */", syntax("1", "13", ")")},
            {"c27(x INT) RETURNS BOOLEAN", "\nCASE", syntax("2", "4", ")")},
            {"c28(x INT) RETURNS BOOLEAN", "x::INT + CASE", syntax("1", "14", ")")},
        });
    }

    @Test
    public void aCastAbandonedBeforeItsAsLeavesTheFrameClosed() {
        assertBodies(new String[][] {
            {"k1(x INT) RETURNS INT", "CAST(", syntax("1", "6", ")")},
            {"k2(x INT) RETURNS INT", "CAST(1", syntax("1", "7", ")")},
            {"k3(x INT) RETURNS INT", "TRY_CAST(", syntax("1", "10", ")")},
            {"k4(x INT) RETURNS INT", "TRY_CAST(1", syntax("1", "11", ")")},
            {"k5(x INT) RETURNS INT", "CAST(1 +", syntax("1", "9", ")")},
            {"k6(x INT) RETURNS INT", "(CAST(", syntax("1", "7", ")")},
            {"k7(x INT) RETURNS INT", "CAST(CAST(1", syntax("1", "12", ")")},
            {"k8(x INT) RETURNS INT", "CAST(x", syntax("1", "7", ")")},
            {"k9(x INT) RETURNS INT", "1 + CAST(", syntax("1", "10", ")")},
            // A call around it, or the AS read, keeps the end of input's line.
            {"k10(x INT) RETURNS INT", "ABS(CAST(1", syntax("1", "11", ")", "1", "12", "<EOF>")},
            {"k11(x INT) RETURNS INT", "CAST(1 AS", syntax("1", "10", ")", "1", "11", "<EOF>")},
            {"k12(x INT) RETURNS INT", "TRY_CAST(1 AS", syntax("1", "14", ")", "1", "15", "<EOF>")},
            {"k13(x INT) RETURNS INT", "CAST(1 AS NUMBER(38,", syntax("1", "21", ")", "1", "22", "<EOF>")},
            {"k14(x INT) RETURNS INT", "CAST(1 AS INT", syntax("1", "15", "<EOF>")},
            {"k15(x INT) RETURNS INT", "CAST(ABS(1", syntax("1", "12", "<EOF>")},
        });
    }

    @Test
    public void aParenthesisedListOfValuesIsARowNoDeclaredTypeTakes() {
        assertBodies(new String[][] {
            {"w1(x INT, y INT) RETURNS INT", "1,2",
                "Declared return type 'NUMBER(38,0)' is incompatible with actual return type 'ROW(NUMBER(1,0), NUMBER(1,0))'"},
            {"w2(x INT, y INT) RETURNS INT", "1,2,3", "Declared return type 'NUMBER(38,0)' is incompatible with actual"
                + " return type 'ROW(NUMBER(1,0), NUMBER(1,0), NUMBER(1,0))'"},
            {"w3(x INT, y INT) RETURNS INT", "x, 'a'",
                "Declared return type 'NUMBER(38,0)' is incompatible with actual return type 'ROW(NUMBER(38,0), VARCHAR(1))'"},
            {"w4(x INT, y INT) RETURNS VARIANT", "1,2",
                "Declared return type 'VARIANT' is incompatible with actual return type 'ROW(NUMBER(1,0), NUMBER(1,0))'"},
            {"w5(x INT, y INT) RETURNS INT", "(1,2)",
                "Declared return type 'NUMBER(38,0)' is incompatible with actual return type 'ROW(NUMBER(1,0), NUMBER(1,0))'"},
            {"w6(x INT, y INT) RETURNS INT", "1,", syntax("1", "3", ")")},
            {"w7(x INT, y INT) RETURNS INT", "1,2,", syntax("1", "5", ")")},
        });
    }

    @Test
    public void aCharacterTheLexerCannotReadIsRefusedWithTheCharacterAfterIt() {
        final String zeroWidthSpace = "\u200B";
        assertBodies(new String[][] {
            {"u1(x INT) RETURNS INT", "x +" + zeroWidthSpace + "1", syntax("1", "4", zeroWidthSpace + "1")},
            {"u2(x INT) RETURNS INT", "x \\ 1", syntax("1", "3", "\\ ")},
            {"u3(x INT) RETURNS INT", "x + 1 #", syntax("1", "7", "#)")},
            {"u4(x INT) RETURNS INT", "x + 1 # 2", syntax("1", "7", "# ")},
            {"u5(x INT) RETURNS INT", "x + 1 \\", syntax("1", "7", "\\)")},
            {"u6(x INT) RETURNS INT", zeroWidthSpace, syntax("1", "1", zeroWidthSpace + ")")},
            {"u7(x INT) RETURNS INT", "x" + zeroWidthSpace, syntax("1", "2", zeroWidthSpace + ")")},
            {"u8(x INT) RETURNS INT", "x +" + zeroWidthSpace + zeroWidthSpace + "1",
                syntax("1", "4", zeroWidthSpace + zeroWidthSpace)},
            // A quote the token takes leaves the rest of the frame an unterminated literal.
            {"u9(x INT) RETURNS INT", "x +" + zeroWidthSpace + "'a'",
                syntax("1", "4", zeroWidthSpace + "'") + "|parse error line 1 at position 9 near '<EOF>'."},
            // Inside a query the query's keyword is refused first.
            {"u10(x INT) RETURNS INT", "SELECT " + zeroWidthSpace + "1", syntax("1", "1", "SELECT")},
        });
    }

    @Test
    public void aQueryThisGrammarStopsReadingOnAWordIsLeftToTheAccount() {
        assertBodies(new String[][] {
            {"g1() RETURNS INT", "SELECT MAX(a) FROM t ORDER BY ALL", "created"},
            {"g2() RETURNS INT", "SELECT a FROM t ORDER BY ALL LIMIT 1", "created"},
            {"g3() RETURNS TABLE (a INT)", "SELECT a FROM t ORDER BY ALL", "created"},
            {"g4(x INT) RETURNS BOOLEAN", "x IN (SELECT a FROM t ORDER BY ALL)", "created"},
            {"g5() RETURNS INT", "SELECT COUNT(*) FROM t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m"
                + " ALL ROWS PER MATCH PATTERN (p) DEFINE p AS a > 0)", "created"},
        });
    }

    @Test
    public void aStringLeftOpenIsRefusedAtTheFramesEnd() {
        assertBodies(new String[][] {
            {"l1() RETURNS INT", "'abc", REFUSED + "|parse error line 1 at position 6 near '<EOF>'."},
            {"l2() RETURNS INT", "1 + 'abc", REFUSED + "|parse error line 1 at position 10 near '<EOF>'."},
            {"l3() RETURNS INT", "SELECT 'abc", REFUSED + "|parse error line 1 at position 13 near '<EOF>'."},
            // A literal after a semicolon stands in a later statement, never read while the first does not.
            {"l4() RETURNS INT", "BEGIN RETURN 1; END 'abc", syntax("1", "7", "RETURN")},
            {"l5() RETURNS INT", "DECLARE v INT DEFAULT 1; BEGIN RETURN 'x; END", syntax("1", "9", "v")},
        });
    }
}
