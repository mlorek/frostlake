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
 * A SQL UDF expression body that does not read is refused at CREATE, at the token its frame of parentheses
 * breaks on, in the frame's positions: one further on the body's first line. An unfinished {@code IS} predicate,
 * and an operand {@code COLLATE} or {@code ESCAPE} cannot take, is refused at that keyword. A body that closes
 * the frame itself is refused at what follows, unless a semicolon ends the statement there. The body is judged
 * before the function's existence. Every cell is live-verified.
 */
public class SqlUdfUnreadableBodyTest extends BaseDatabaseTest {

    private static final String REFUSED = "Compilation of SQL UDF failed: SQL compilation error:";

    private static final String PARAMETERS = "(x INT, y INT, z INT, t INT, a INT, b INT, c INT, v INT, w INT)";

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

    /** Each {name, body, expected} cell as a function of the shared parameters returning INT. */
    private void assertBodies(final String[][] cells) {
        for (final String[] cell : cells) {
            final String ddl = "CREATE FUNCTION " + cell[0] + PARAMETERS + " RETURNS INT AS $$" + cell[1] + "$$";
            assertEquals(cell[2], create(ddl), ddl);
        }
    }

    @Test
    public void aBodyThatIsNotSqlIsRefusedWhereItBreaks() {
        engine.execute("CREATE FUNCTION fx() RETURNS INT AS '1'");
        final String[][] cells = {
            {"CREATE FUNCTION f3() RETURNS INT AS 'this is not sql'", syntax("1", "6", "is")},
            {"CREATE FUNCTION f4() RETURNS INT AS '  this is not sql'", syntax("1", "8", "is")},
            {"CREATE FUNCTION f5() RETURNS INT AS $$this is\nnot sql$$", syntax("1", "6", "is")},
            {"CREATE FUNCTION IF NOT EXISTS fx() RETURNS INT AS 'this is not sql'", syntax("1", "6", "is")},
            {"CREATE FUNCTION f6() RETURNS INT AS $$this is not sql;$$", syntax("1", "6", "is")},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], create(cell[0]), cell[0]);
        }
    }

    @Test
    public void aStrayTokenIsNamedInTheFramesPositions() {
        assertBodies(new String[][] {
            {"s1", "hello world", syntax("1", "7", "world")},
            {"s2", "a b c", syntax("1", "3", "b")},
            {"s3", "1 2 3", syntax("1", "3", "2")},
            {"s4", "SELEC 1", syntax("1", "7", "1")},
            {"s5", "1 * * 2", syntax("1", "5", "*")},
            {"s6", "1 = = 2", syntax("1", "5", "=")},
            {"s7", "1 AND AND 2", syntax("1", "7", "AND")},
            {"s8", "1 +* 2", syntax("1", "4", "*")},
            {"s9", "TRUE FALSE", syntax("1", "6", "FALSE")},
            {"s10", "NULL NULL", syntax("1", "6", "NULL")},
            {"s11", "x IS NULL NULL", syntax("1", "11", "NULL")},
            {"s12", "x AND y z", syntax("1", "9", "z")},
            {"s13", "IFF(x, 1, 2) 3", syntax("1", "14", "3")},
            {"s14", "x:a b", syntax("1", "5", "b")},
            {"s15", "1 2\n3", syntax("1", "3", "2")},
            {"s16", "\n1 2", syntax("2", "2", "2")},
            {"s17", "  1 2", syntax("1", "5", "2")},
            {"s18", "1 2;", syntax("1", "3", "2")},
            {"s19", "DATE '2024-01-01' 5", syntax("1", "19", "5")},
            {"s20", "INTERVAL '1 day' 5", syntax("1", "18", "5")},
            {"s21", "x COLLATE 'en' 5", syntax("1", "16", "5")},
            {"s22", "x BETWEEN 1 AND 2 3", syntax("1", "19", "3")},
        });
    }

    @Test
    public void aConstructMissingAPartIsRefusedAtTheTokenInItsPlace() {
        assertBodies(new String[][] {
            {"p1", "ABS(1 2)", syntax("1", "7", "2")},
            {"p2", "IFF(TRUE 1, 2)", syntax("1", "10", "1")},
            {"p3", "CASE WHEN 1 THEN 2", syntax("1", "19", ")")},
            {"p4", "CASE 1 WHEN", syntax("1", "12", ")")},
            {"p5", "x BETWEEN 1 2", syntax("1", "13", "2")},
            {"p6", "x IN 1", syntax("1", "6", "1")},
            {"p7", "x NOT 5", syntax("1", "7", "5")},
            {"p8", "x :: 5", syntax("1", "6", "5")},
            {"p9", "CAST(1 AS 5)", syntax("1", "11", "5")},
            {"p10", "1 ORDER BY 1", syntax("1", "3", "ORDER")},
            {"p11", "1 FROM t", syntax("1", "3", "FROM")},
            {"p12", "1 WHERE TRUE", syntax("1", "3", "WHERE")},
            {"p13", "EXISTS 1", syntax("1", "8", "1")},
            {"p14", "x[1", syntax("1", "4", ")")},
            {"p15", "{'a' 1}", syntax("1", "6", "1")},
            {"p16", "[1 2]", syntax("1", "4", "2")},
            {"p17", "(1 + 2", syntax("1", "8", "<EOF>")},
            {"p18", "COUNT(*) OVER (ORDER)", syntax("1", "21", ")")},
            {"p19", "x LIKE 5 5", syntax("1", "10", "5")},
            {"p20", "ABS(1,)", syntax("1", "7", ")")},
            {"p21", "IFF(1,,2)", syntax("1", "7", ",")},
            {"p22", "CASE END", syntax("1", "9", ")")},
            {"p23", "CASE WHEN END", syntax("1", "14", ")")},
            {"p24", "CASE ELSE 1 END", syntax("1", "6", "ELSE")},
            {"p25", "- -", syntax("1", "4", ")")},
            {"p26", "x IN (1 2)", syntax("1", "9", "2")},
            {"p27", "x IN ()", syntax("1", "7", ")")},
            {"p28", "ARRAY_CONSTRUCT(1 2)", syntax("1", "19", "2")},
            {"p29", "OBJECT_CONSTRUCT('a' 1)", syntax("1", "22", "1")},
            {"p30", "TRY_CAST(1 AS)", syntax("1", "14", ")")},
            {"p31", "1::5", syntax("1", "4", "5")},
            {"p32", "x NOT BETWEEN 1 2", syntax("1", "17", "2")},
            {"p33", "IF (TRUE) THEN 1 END IF", syntax("1", "11", "THEN")},
        });
    }

    @Test
    public void anUnfinishedPredicateIsRefusedAtItsKeyword() {
        assertBodies(new String[][] {
            {"k1", "x IS NOT sql", syntax("1", "3", "IS")},
            {"k2", "x IS 5", syntax("1", "3", "IS")},
            {"k3", "x IS NOT NOT NULL", syntax("1", "3", "IS")},
            {"k4", "x IS\nNOT sql", syntax("1", "3", "IS")},
            {"k5", "x IS TRUE", syntax("1", "3", "IS")},
            {"k6", "x IS NOT TRUE", syntax("1", "3", "IS")},
            {"k7", "x IS 5 + 1", syntax("1", "3", "IS")},
            {"k8", "x IS NULL IS 5", syntax("1", "11", "IS")},
            {"k9", "x IS NOT\nsql", syntax("1", "3", "IS")},
            {"k10", "(x IS 5", syntax("1", "4", "IS")},
            {"k11", "ABS(x IS 5)", syntax("1", "7", "IS")},
            {"k12", "ABS(x IS)", syntax("1", "7", "IS")},
            {"k13", "(x IS)", syntax("1", "4", "IS")},
            {"k14", "ABS((x IS", syntax("1", "8", "IS")},
            {"k15", "IFF(x IS, 1, 2)", syntax("1", "7", "IS")},
            {"k16", "(this is not sql)", syntax("1", "7", "is")},
            {"k17", "x IS NOT NULL IS NOT", syntax("1", "15", "IS")},
            {"k18", "x IS", syntax("1", "3", "IS")},
            {"k19", "CASE WHEN x IS", syntax("1", "13", "IS")},
            {"k20", "x NOT IS NULL", syntax("1", "7", "IS")},
            {"k21", "x IS DISTINCT 1", syntax("1", "3", "IS", "1", "3", "IS")},
            {"k22", "x IS NOT DISTINCT 5", syntax("1", "3", "IS", "1", "3", "IS")},
            {"k23", "x IS DISTINCT 5 + 1", syntax("1", "3", "IS", "1", "3", "IS")},
            {"k24", "(x IS DISTINCT", syntax("1", "4", "IS", "1", "4", "IS")},
            {"k25", "x IS NOT DISTINCT", syntax("1", "3", "IS", "1", "3", "IS")},
            {"k26", "x LIKE 'a' ESCAPE 5", syntax("1", "12", "ESCAPE")},
            {"k27", "(x LIKE 'a' ESCAPE", syntax("1", "13", "ESCAPE")},
            {"k28", "x ESCAPE", syntax("1", "3", "ESCAPE")},
            {"k29", "x LIKE 'a' ESCAPE 'b' ESCAPE", syntax("1", "23", "ESCAPE")},
            {"k30", "x COLLATE", syntax("1", "3", "COLLATE")},
            {"k31", "(x COLLATE", syntax("1", "4", "COLLATE")},
            {"k32", "ABS(x COLLATE", syntax("1", "7", "COLLATE")},
            {"k33", "x COLLATE 'a' COLLATE", syntax("1", "15", "COLLATE")},
        });
    }

    @Test
    public void aBlockOrAStatementThatDoesNotReadIsRefusedInTheExpressionFrame() {
        assertBodies(new String[][] {
            {"b1", "BEGIN RETURN 1 END", syntax("1", "7", "RETURN")},
            {"b2", "BEGIN RETURN 1 END;", syntax("1", "7", "RETURN")},
            {"b3", "DECLARE v INT; BEGIN RETURN v END;", syntax("1", "9", "v")},
            {"b4", "BEGIN\n RETURN 1\nEND", syntax("2", "1", "RETURN")},
            {"b5", "BEGIN RETURN 1; END garbage", syntax("1", "7", "RETURN")},
            {"b6", "LET v := 1", syntax("1", "5", "v")},
            {"b7", "RETURN 1", syntax("1", "8", "1")},
            {"b8", "SHOW TABLES", syntax("1", "6", "TABLES")},
            {"b9", "DELETE FROM t", syntax("1", "1", "DELETE")},
        });
    }

    @Test
    public void aBodyThatClosesTheFrameItselfEndsThereOnlyAtASemicolon() {
        assertBodies(new String[][] {
            {"e1", "1) + 1", syntax("1", "7", ")")},
            {"e2", "1) garbage", syntax("1", "4", "garbage")},
            {"e3", "1)", syntax("1", "3", ")")},
            {"e4", "1));", syntax("1", "3", ")")},
            {"e5", "1) garbage;", syntax("1", "4", "garbage")},
            {"e6", "1 + 2)", syntax("1", "7", ")")},
            {"e7", "ABS(1))", syntax("1", "8", ")")},
            {"e8", "1); x", "created"},
            {"e9", "1);garbage here", "created"},
            {"e10", "(1)) + 1;", "created"},
            {"e11", "1) ; ; x", "created"},
            {"e12", "1)\n;", "created"},
        });
    }
}
