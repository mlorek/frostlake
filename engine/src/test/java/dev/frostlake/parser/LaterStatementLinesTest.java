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
 * After a fault live reads past, the statements after it are read on their own and a fault of theirs is one more line,
 * the end of the input as much as any token. A semicolon where a head's name belongs passes over the next statement; a
 * word run in after a finished statement, or into another inside a SELECT list's bracket, ends the report; and a
 * statement that cannot open ends it — unnamed straight after a faulted statement, named after a clean one
 * (live-verified).
 */
public class LaterStatementLinesTest extends BaseDatabaseTest {

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
    public void aLaterStatementRunningOutOfInputIsReported() {
        assertEquals(lines(at(15, "RENAME"), at(37, "y")), refusal("ALTER TABLE t1 RENAME TO; SELECT 1 x y"));
        assertEquals(lines(at(15, "RENAME"), at(39, "<EOF>")), refusal("ALTER TABLE t1 RENAME TO; SELECT 1 FROM"));
        assertEquals(lines(at(15, "RENAME"), at(32, "<EOF>")), refusal("ALTER TABLE t1 RENAME TO; SELECT"));
        assertEquals(lines(at(15, "RENAME"), at(41, "RENAME")),
            refusal("ALTER TABLE t1 RENAME TO; ALTER TABLE t1 RENAME TO"));
        assertEquals(lines(at(18, ";"), at(31, "y")), refusal("ALTER TABLE t1 ADD; SELECT 1 x y"));
        assertEquals(lines(at(10, ";"), at(23, "y")), refusal("SELECT 1 +; SELECT 1 x y"));
        assertEquals(lines(at(13, ";"), at(26, "y")), refusal("SELECT 1 FROM; SELECT 1 x y"));
        assertEquals(lines(at(9, ";"), at(22, "y")), refusal("SELECT (1; SELECT 1 x y"));
        assertEquals(lines(at(10, ";"), at(23, "y")), refusal("DROP TABLE; SELECT 1 x y"));
        assertEquals(lines(at(11, ";"), at(24, "y")), refusal("DELETE FROM; SELECT 1 x y"));
        assertEquals(lines(at(5, ";"), at(18, "y")), refusal("GRANT; SELECT 1 x y"));
        assertEquals(lines(at(0, "DESC"), at(17, "y")), refusal("DESC; SELECT 1 x y"));
        assertEquals(lines(at(16, ")"), at(30, "y")), refusal("SELECT CAST(1 AS); SELECT 1 x y"));
        assertEquals(lines(at(15, "RENAME"), at(39, "y")), refusal("ALTER TABLE t1 RENAME TO; ; SELECT 1 x y"));
    }

    @Test
    public void eachFaultDecidesWhetherTheNextStatementIsRead() {
        assertEquals(lines(at(15, "RENAME"), at(47, "y")), refusal("ALTER TABLE t1 RENAME TO; SELECT 1; SELECT 2 x y"));
        assertEquals(lines(at(10, ";"), at(22, ";"), at(35, "y")), refusal("DROP TABLE; SELECT 1 +; SELECT 1 x y"));
        assertEquals(lines(at(10, ";"), at(23, "y")), refusal("SELECT 1 +; SELECT 1 x y; SELECT 2 x y"));
        assertEquals(lines(at(15, "RENAME"), at(37, "y")),
            refusal("ALTER TABLE t1 RENAME TO; SELECT 1 x y; SELECT 2 x y"));
        assertEquals(lines(at(10, ";"), at(23, "(")), refusal("SELECT 1 +; SELECT 1 x (; SELECT 2 x y"));
        assertEquals(lines(at(10, ";"), at(24, "y")), refusal("SELECT 1 +; (SELECT 1 x y)"));
    }

    @Test
    public void aWordRunInOrAFaultLiveGivesTheInputUpAtAddsNothing() {
        assertEquals(lines(at(11, "y")), refusal("SELECT 1 x y; SELECT 2 x y"));
        assertEquals(lines(at(11, "y")), refusal("SELECT 1 x y; SELECT 2 FROM"));
        assertEquals(lines(at(11, "(")), refusal("SELECT 1 x (; SELECT 1 x y"));
        assertEquals(lines(at(12, ";")), refusal("UPDATE t SET; SELECT 1 x y"));
        assertEquals(lines(at(12, ";")), refusal("CREATE TABLE; SELECT 1 x y"));
        assertEquals(lines(at(11, ";")), refusal("INSERT INTO; SELECT 1 x y"));
        assertEquals(lines(at(10, ";"), at(24, ";")), refusal("DROP TABLE; CREATE TABLE; SELECT 1 x y"));
    }

    @Test
    public void aLaterStatementThatCannotOpenEndsTheReport() {
        assertEquals(lines(at(15, "RENAME")), refusal("ALTER TABLE t1 RENAME TO; 1"));
        assertEquals(lines(at(15, "RENAME")), refusal("ALTER TABLE t1 RENAME TO; x"));
        assertEquals(lines(at(15, "RENAME")), refusal("ALTER TABLE t1 RENAME TO; x y"));
        assertEquals(lines(at(15, "RENAME")), refusal("ALTER TABLE t1 RENAME TO; FROM t"));
        assertEquals(lines(at(15, "RENAME")), refusal("ALTER TABLE t1 RENAME TO; )"));
        assertEquals(lines(at(10, ";")), refusal("SELECT 1 +; x y"));
        assertEquals(lines(at(10, ";")), refusal("SELECT 1 +; RETURN 1"));
        assertEquals(lines(at(10, ";")), refusal("SELECT 1 +; x; SELECT 1 x y"));
        assertEquals(lines(at(10, ";")), refusal("SELECT 1 +; 1; SELECT 2 x y z"));
        assertEquals(lines(at(10, ";"), at(23, "x")), refusal("SELECT 1 +; (SELECT 1) x y"));
        assertEquals(lines(at(10, ";"), at(44, "y")), refusal("SELECT 1 +; WITH c AS (SELECT 1) SELECT 1 x y"));
        assertEquals(lines(at(10, "x")), refusal("SELECT 1; x y"));
    }

    @Test
    public void onlyTheStatementStraightAfterAFaultedOneEndsTheReportUnnamed() {
        assertEquals(lines(at(10, ";"), at(22, "x")), refusal("SELECT 1 +; SELECT 2; x; SELECT 3 x y"));
        assertEquals(lines(at(15, "RENAME"), at(36, "x")), refusal("ALTER TABLE t1 RENAME TO; SELECT 1; x y"));
        assertEquals(lines(at(10, ";"), at(22, "1")), refusal("SELECT 1 +; SELECT 2; 1; SELECT 3 x y"));
        assertEquals(lines(at(10, ";"), at(32, "x")), refusal("SELECT 1 +; SELECT 2; SELECT 3; x y"));
        assertEquals(lines(at(10, ";"), at(22, "x")), refusal("SELECT 1 +; SELECT 2; x y z; SELECT 3 x y"));
        assertEquals(lines(at(10, ";")), refusal("SELECT 1 +;; x; SELECT 1 x y"));
        assertEquals(lines(at(15, "RENAME")), refusal("ALTER TABLE t1 RENAME TO;; x y"));
        assertEquals(lines(at(10, ";"), at(23, "y")), refusal("SELECT 1 +; SELECT 2 x y; x; SELECT 3 x y"));
        assertEquals(lines(at(10, ";"), at(22, ";")), refusal("SELECT 1 +; SELECT 2 +; x; SELECT 3 x y"));
    }

    @Test
    public void aStatementThatCannotOpenIsTheReportsLastLine() {
        assertEquals(lines(at(10, ";"), at(22, "FROM")), refusal("DROP TABLE; SELECT 1; FROM t; SELECT 2 x y"));
        assertEquals(lines(at(10, ";"), at(22, "FROM")), refusal("SELECT 1 +; SELECT 2; FROM t x y"));
        assertEquals(lines(at(10, "FROM")), refusal("SELECT 1; FROM t; SELECT 2 x y"));
        assertEquals(lines(at(10, "1")), refusal("SELECT 1; 1; SELECT 2 x y"));
        assertEquals(lines(at(0, "FROM")), refusal("FROM t x y; SELECT 1 x y"));
        assertEquals(lines(at(10, ";"), at(22, "x")), refusal("SELECT 1 +; SELECT 2; x"));
        assertEquals(lines(at(10, ";"), at(22, "RETURN")), refusal("SELECT 1 +; SELECT 2; RETURN 1; SELECT 3 x y"));
        assertEquals(lines(at(10, ";"), at(22, "RETURN")), refusal("SELECT 1 +; SELECT 2; RETURN 1"));
        assertEquals(lines(at(10, ";"), at(22, "LET")), refusal("SELECT 1 +; SELECT 2; LET x := 1; SELECT 3 x y"));
    }

    @Test
    public void aSemicolonWhereAHeadsNameBelongsPassesOverTheNextStatement() {
        assertEquals(lines(at(12, ";"), at(35, "y")), refusal("CREATE TABLE; SELECT 2; SELECT 3 x y"));
        assertEquals(lines(at(12, ";"), at(41, "y")), refusal("CREATE TABLE; SELECT 1 x y z; SELECT 2 x y"));
        assertEquals(lines(at(12, ";"), at(37, "y")), refusal("CREATE TABLE; DROP TABLE; SELECT 1 x y"));
        assertEquals(lines(at(12, ";"), at(28, "y")), refusal("CREATE TABLE; x; SELECT 1 x y"));
        assertEquals(lines(at(12, ";")), refusal("CREATE TABLE; SELECT 2; x; SELECT 1 x y"));
        assertEquals(lines(at(12, ";"), at(27, "y")), refusal("CREATE TABLE; ; SELECT 1 x y"));
        assertEquals(lines(at(12, ";")), refusal("UPDATE t SET; SELECT 1 x y z"));
        assertEquals(lines(at(20, ";")), refusal("UPDATE t SET a = 1, ; SELECT 1 x y"));
        assertEquals(lines(at(21, ";")), refusal("INSERT OVERWRITE INTO; SELECT 1 x y"));
        assertEquals(lines(at(33, ";")), refusal("CREATE OR REPLACE TRANSIENT TABLE; SELECT 1 x y"));
        assertEquals(lines(at(26, ";")), refusal("CREATE TABLE IF NOT EXISTS; SELECT 1 x y"));
        assertEquals(lines(at(10, ";"), at(33, "y")), refusal("MERGE INTO; SELECT 2; SELECT 3 x y"));
        assertEquals(lines(at(4, ";"), at(27, "y")), refusal("CALL; SELECT 2; SELECT 3 x y"));
        assertEquals(lines(at(6, ";"), at(29, "y")), refusal("WITH c; SELECT 2; SELECT 3 x y"));
        assertEquals(lines(at(10, ";")), refusal("WITH c (a); SELECT 1 x y"));
        assertEquals(lines(at(11, ";")), refusal("WITH c AS (; SELECT 1 x y"));
        assertEquals(lines(at(23, ";")), refusal("WITH c AS (SELECT 1), d; SELECT 1 x y"));
        assertEquals(lines(at(10, ";"), at(24, ";"), at(47, "y")),
            refusal("SELECT 1 +; CREATE TABLE; SELECT 2; SELECT 3 x y"));
    }

    @Test
    public void anExplainedStatementIsJudgedByItsOwnHead() {
        assertEquals(lines(at(19, ";")), refusal("EXPLAIN INSERT INTO; SELECT 1 x y"));
        assertEquals(lines(at(20, ";")), refusal("EXPLAIN UPDATE t SET; SELECT 1 x y"));
        assertEquals(lines(at(18, ";")), refusal("EXPLAIN MERGE INTO; SELECT 1 x y"));
        assertEquals(lines(at(19, ";")), refusal("EXPLAIN INSERT INTO; SELECT 1 FROM"));
        assertEquals(lines(at(30, ";")), refusal("EXPLAIN USING TEXT INSERT INTO; SELECT 1 x y"));
        assertEquals(lines(at(31, ";")), refusal("EXPLAIN USING JSON UPDATE t SET; SELECT 1 x y"));
        assertEquals(lines(at(17, ";")), refusal("EXPLAIN WITH c AS; SELECT 1 x y"));
        assertEquals(lines(at(13, ";")), refusal("EXPLAIN USING; SELECT 1 x y"));
        assertEquals(lines(at(14, ";")), refusal("EXPLAIN UPDATE; SELECT 1 x y"));
        assertEquals(lines(at(18, ";"), at(31, "y")), refusal("EXPLAIN SELECT 1 +; SELECT 1 x y"));
        assertEquals(lines(at(39, ";"), at(52, "y")), refusal("EXPLAIN USING TEXT UPDATE t SET a = 1 +; SELECT 1 x y"));
        assertEquals(lines(at(16, ";"), at(29, "y")), refusal("EXPLAIN UPDATE t; SELECT 1 x y"));
        assertEquals(lines(at(19, ";"), at(42, "y")), refusal("EXPLAIN INSERT INTO; SELECT 2; SELECT 3 x y"));
        assertEquals(lines(at(18, ";"), at(31, "y")), refusal("EXPLAIN USING TEXT; SELECT 1 x y"));
        assertEquals(lines(at(7, ";"), at(20, "y")), refusal("EXPLAIN; SELECT 1 x y"));
        assertEquals(lines(at(13, ";"), at(36, "y")), refusal("EXPLAIN USING; SELECT 2; SELECT 3 x y"));
    }

    @Test
    public void anyOtherFaultOfThoseStatementsIsReadPast() {
        assertEquals(lines(at(14, ";"), at(27, "y")), refusal("CREATE TABLE t; SELECT 1 x y"));
        assertEquals(lines(at(11, ";"), at(24, "y")), refusal("CREATE VIEW; SELECT 1 x y"));
        assertEquals(lines(at(6, ";"), at(19, "y")), refusal("CREATE; SELECT 1 x y"));
        assertEquals(lines(at(15, ";"), at(28, "y")), refusal("CREATE TABLE IF; SELECT 1 x y"));
        assertEquals(lines(at(22, ")"), at(36, "y")), refusal("CREATE TABLE u (a INT,); SELECT 1 x y"));
        assertEquals(lines(at(6, ";"), at(19, "y")), refusal("INSERT; SELECT 1 x y"));
        assertEquals(lines(at(22, ";"), at(35, "y")), refusal("INSERT INTO t VALUES (; SELECT 1 x y"));
        assertEquals(lines(at(8, ";"), at(21, "y")), refusal("UPDATE t; SELECT 1 x y"));
        assertEquals(lines(at(14, ";"), at(27, "y")), refusal("UPDATE t SET a; SELECT 1 x y"));
        assertEquals(lines(at(20, ";"), at(33, "y")), refusal("UPDATE t SET a = 1 +; SELECT 1 x y"));
        assertEquals(lines(at(24, ";"), at(37, "y")), refusal("UPDATE t SET a = 1 WHERE; SELECT 1 x y"));
        assertEquals(lines(at(12, ";"), at(25, "y")), refusal("MERGE INTO t; SELECT 1 x y"));
        assertEquals(lines(at(7, ";"), at(20, "y")), refusal("CALL p(; SELECT 1 x y"));
        assertEquals(lines(at(6, ";"), at(19, "y")), refusal("CALL p; SELECT 1 x y"));
        assertEquals(lines(at(4, ";"), at(17, "y")), refusal("WITH; SELECT 1 x y"));
        assertEquals(lines(at(31, ";"), at(44, "y")), refusal("WITH c AS (SELECT 1) SELECT 1 +; SELECT 1 x y"));
        assertEquals(lines(at(21, ")"), at(44, "y")), refusal("WITH c AS (SELECT 1 +) SELECT 1; SELECT 1 x y"));
        assertEquals(lines(at(20, ";"), at(33, "y")), refusal("WITH c AS (SELECT 1); SELECT 1 x y"));
        assertEquals(lines(at(13, "1"), at(37, "y")), refusal("CREATE TABLE 1; SELECT 2; SELECT 3 x y"));
        assertEquals(lines(at(7, "1"), at(21, "y")), refusal("WITH c 1; SELECT 1 x y"));
        assertEquals(lines(at(5, "1"), at(29, "y")), refusal("CALL 1; SELECT 2; SELECT 3 x y"));
        assertEquals(lines(at(7, ";"), at(20, "y")), refusal("SET x =; SELECT 1 x y"));
        assertEquals(lines(at(7, ";"), at(20, "y")), refusal("SET (x); SELECT 1 x y"));
        assertEquals(lines(at(3, ";"), at(16, "y")), refusal("SET; SELECT 1 x y"));
    }

    @Test
    public void aWordRunInEndsTheReportButAfterADropAShowAnUnsetOrAFromListAlias() {
        assertEquals(lines(at(11, "y")), refusal("SELECT 1 x y; SELECT 2 x y z"));
        assertEquals(lines(at(11, "y")), refusal("SELECT 1 x y z; SELECT 1 x y z"));
        assertEquals(lines(at(11, "y")), refusal("SELECT 1 x y; SELECT 2 x y z; SELECT 3 x y"));
        assertEquals(lines(at(11, "y")), refusal("SELECT 1 x y; SELECT 1 FROM t JOIN u ON; SELECT 1 x y"));
        assertEquals(lines(at(11, "y")), refusal("SELECT 1 x y; DROP TABLE t x; SELECT 2 x y"));
        assertEquals(lines(at(16, "y")), refusal("DELETE FROM t x y z; SELECT 1 x y"));
        assertEquals(lines(at(28, "x")), refusal("SELECT 1 FROM t WHERE a = 1 x; SELECT 1 x y"));
        assertEquals(lines(at(25, "x")), refusal("INSERT INTO t VALUES (1) x; SELECT 1 x y"));
        assertEquals(lines(at(13, "x"), at(27, "y")), refusal("DROP TABLE t x; SELECT 1 x y"));
        assertEquals(lines(at(13, "x"), at(26, ";"), at(39, "y")), refusal("DROP TABLE t x; SELECT 1 +; SELECT 2 x y"));
        assertEquals(lines(at(13, "x"), at(29, "<EOF>")), refusal("DROP TABLE t x; SELECT 1 FROM"));
        assertEquals(lines(at(12, "x"), at(26, "y")), refusal("SHOW TABLES x; SELECT 1 x y"));
        assertEquals(lines(at(12, "x"), at(27, "y"), at(41, "y")),
            refusal("SHOW TABLES x; SHOW TABLES y; SELECT 1 x y"));
        assertEquals(lines(at(8, "y"), at(22, "y")), refusal("UNSET x y; SELECT 1 x y"));
        assertEquals(lines(at(8, "y"), at(21, ";"), at(34, "y")), refusal("UNSET x y; SELECT 1 +; SELECT 2 x y"));
        assertEquals(lines(at(10, ";"), at(30, "y"), at(44, "y")),
            refusal("SELECT 1 +; SELECT 1 FROM t x y; SELECT 2 x y"));
        assertEquals(lines(at(25, "y"), at(39, "y")), refusal("SELECT 1 FROM t JOIN u x y; SELECT 2 x y"));
    }

    @Test
    public void aWordRunIntoAnotherInASelectListBracketEndsTheReport() {
        assertEquals(lines(at(14, "x")), refusal("SELECT ABS((1 x y)); SELECT 1 x y"));
        assertEquals(lines(at(10, "x"), at(25, "y")), refusal("SELECT (1 x); SELECT 1 x y"));
        assertEquals(lines(at(13, "x"), at(28, "y")), refusal("SELECT ABS(1 x); SELECT 1 x y"));
        assertEquals(lines(at(26, "y"), at(41, "y")), refusal("SELECT EXISTS (SELECT 1 x y); SELECT 1 x y"));
        assertEquals(lines(at(30, "y"), at(45, "y")), refusal("SELECT NOT EXISTS (SELECT 1 x y); SELECT 1 x y"));
        assertEquals(lines(at(11, ")"), at(25, "y")), refusal("SELECT (1 +); SELECT 1 x y"));
        assertEquals(lines(at(26, "y"), at(41, "y")), refusal("SELECT * FROM (SELECT 1 x y); SELECT 1 x y"));
        assertEquals(lines(at(25, "x"), at(42, "y")), refusal("SELECT 1 FROM t WHERE (1 x y); SELECT 1 x y"));
        assertEquals(lines(at(28, "x"), at(45, "y")), refusal("SELECT 1 FROM t GROUP BY (1 x y); SELECT 1 x y"));
        assertEquals(lines(at(10, ","), at(44, "y")), refusal("SELECT 1 +, (SELECT a b FROM t); SELECT 1 x y"));
    }

    @Test
    public void aTokenTheStatementBeforeCannotTakeIsARunInLikeAnyOther() {
        assertEquals(lines(at(14, "COMMENT")), refusal("SELECT 1 AS x COMMENT = 'x'; SELECT 1 x y"));
        assertEquals(lines(at(14, "COMMENT")), refusal("SELECT 1 AS x COMMENT = 'x'; SELECT 1 +"));
        assertEquals(lines(at(28, "COMMENT")), refusal("SELECT 1 FROM t WHERE a = 1 COMMENT; SELECT 1 x y"));
        assertEquals(lines(at(28, "x")), refusal("SELECT 1 FROM t WHERE a = 1 x; SELECT 1 +"));
        assertEquals(lines(at(16, "y")), refusal("DELETE FROM t x y z; SELECT 1 FROM"));
        assertEquals(lines(at(25, "x")), refusal("INSERT INTO t VALUES (1) x; SELECT 1 +"));
        assertEquals(lines(at(18, "x")), refusal("USE SCHEMA PUBLIC x y; SELECT 1 x y"));
        assertEquals(lines(at(22, "x")), refusal("SELECT 1 AS x LIMIT 1 x; SELECT 1 x y"));
        assertEquals(lines(at(8, ")")), refusal("SELECT 1) x; SELECT 1 x y z"));
        assertEquals(lines(at(8, ")")), refusal("SELECT 1); SELECT 2 x y z"));
        assertEquals(lines(at(13, "SELECT"), at(32, "y")), refusal("DROP TABLE t SELECT; SELECT 1 x y"));
        assertEquals(lines(at(8, "COMMENT"), at(28, "y")), refusal("UNSET x COMMENT; SELECT 1 x y"));
        assertEquals(lines(at(28, "'q'"), at(44, "y")), refusal("SELECT 1 FROM t WHERE a = 1 'q'; SELECT 1 x y"));
    }

    @Test
    public void aTransactionsBeginIsNoBlock() {
        assertEquals(lines(at(10, ";"), at(30, "y")), refusal("SELECT 1 +; BEGIN; SELECT 1 x y"));
        assertEquals(lines(at(10, ";"), at(32, "<EOF>")), refusal("SELECT 1 +; BEGIN; SELECT 1 FROM"));
        assertEquals(lines(at(10, ";"), at(35, "y")), refusal("SELECT 1 +; BEGIN WORK; SELECT 1 x y"));
    }
}
