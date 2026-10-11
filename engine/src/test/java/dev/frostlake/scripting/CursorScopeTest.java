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
package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * How far a cursor's name reaches, and what a cursor reports out of turn. The name belongs to its block: a
 * block nested in that one - by DECLARE or by LET - a called procedure and a dynamic block each declare the
 * name for themselves, the outer cursor keeps its position, and a body run from the block sees none of its
 * cursors ({@code invalid identifier 'C'} while that body compiles). A name written twice in one block is
 * {@code Variable with name 'C' declared twice.} A RESULTSET's name is scoped the same way, and a block nested in
 * a FOR over a cursor reads the loop's record. An OPEN of an open cursor is {@code Stored procedure execution
 * error: CURSOR 'C' is already open.} (92215) and a CLOSE of one that is not open {@code CURSOR C is not open}
 * (92201), both statement errors; a FETCH from a cursor that is not open fails with that sentence alone, which
 * no handler catches. Every answer is live-verified.
 */
public class CursorScopeTest extends MeasuredCellsTestSupport {

    @Test
    public void aCursorNameBelongsToItsBlock() {
        assertAnswer(
            "Table T1 successfully created.",
            "CREATE OR REPLACE TABLE t1 (n NUMBER)");
        assertAnswer(
            "3",
            "INSERT INTO t1 VALUES (1), (2), (3)");
        assertAnswer(
            "1,2",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT n FROM t1 ORDER BY n; v NUMBER; w NUMBER; BEGIN "
                + "OPEN c; FETCH c INTO v; DECLARE c CURSOR FOR SELECT 100; BEGIN OPEN c; FETCH c INTO w; CLOSE c; "
                + "END; FETCH c INTO w; CLOSE c; RETURN v || ',' || w; END; $$");
        assertAnswer(
            "Function CUR_P successfully created.",
            "CREATE OR REPLACE PROCEDURE cur_p() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE c CURSOR FOR SELECT "
                + "100 AS n; v NUMBER; BEGIN OPEN c; FETCH c INTO v; CLOSE c; RETURN v::VARCHAR; END; $$");
        assertAnswer(
            "1,100,2",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT n FROM t1 ORDER BY n; v NUMBER; w NUMBER; r "
                + "VARCHAR; BEGIN OPEN c; FETCH c INTO v; CALL cur_p() INTO :r; FETCH c INTO w; CLOSE c; RETURN v || "
                + "',' || r || ',' || w; END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 1 AS n; v NUMBER; BEGIN OPEN c; EXECUTE IMMEDIATE "
                + "'DECLARE c CURSOR FOR SELECT 2; w NUMBER; BEGIN OPEN c; FETCH c INTO w; RETURN w; END;'; FETCH c "
                + "INTO v; RETURN v; END; $$");
        assertAnswer(
            "1,2",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT n FROM t1 ORDER BY n; v NUMBER; w NUMBER; BEGIN "
                + "OPEN c; FETCH c INTO v; BEGIN LET c CURSOR FOR SELECT 100; OPEN c; FETCH c INTO w; CLOSE c; END; "
                + "FETCH c INTO w; CLOSE c; RETURN v || ',' || w; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 32 |  Variable with name 'C' declared twice.",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 1; c CURSOR FOR SELECT 2; BEGIN RETURN 'two in one "
                + "section'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 38 |  Variable with name 'C' declared twice.",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 1; BEGIN LET c CURSOR FOR SELECT 2; RETURN 'a LET "
                + "over a DECLARE'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 32 |  Variable with name 'C' declared twice.",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 7; c NUMBER DEFAULT 5; BEGIN RETURN c; END; $$");
        assertAnswer(
            "1,2",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT n FROM t1 ORDER BY n; v NUMBER; w NUMBER; BEGIN "
                + "OPEN c; FETCH c INTO v; DECLARE c CURSOR FOR SELECT 100; BEGIN OPEN c; FETCH c INTO w; END; FETCH "
                + "c INTO w; CLOSE c; RETURN v || ',' || w; END; $$");
        answer(
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT n FROM t1 ORDER BY n; s VARCHAR DEFAULT ''; BEGIN "
                + "FOR r IN c DO DECLARE c CURSOR FOR SELECT r.n * 10 AS m; BEGIN FOR q IN c DO s := s || q.m || ' "
                + "'; END FOR; END; END FOR; RETURN s; END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET DEFAULT (SELECT 1 AS a); BEGIN DECLARE rs RESULTSET "
                + "DEFAULT (SELECT 2 AS a); BEGIN NULL; END; RETURN TABLE(rs); END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 75 : Stored procedure "
                + "execution error: CURSOR 'C' is already open.",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT n FROM t1 ORDER BY n; v NUMBER; BEGIN OPEN c; OPEN "
                + "c; FETCH c INTO v; RETURN v; END; $$");
        assertAnswer(
            "REFUSED CURSOR C is not open",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT n FROM t1 ORDER BY n; v NUMBER; BEGIN FETCH c INTO "
                + "v; RETURN v; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 84 : CURSOR C is not open",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT n FROM t1 ORDER BY n; v NUMBER; BEGIN OPEN c; CLOSE "
                + "c; CLOSE c; RETURN 'closed twice'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 92 | invalid identifier 'C'",
            "EXECUTE IMMEDIATE $$ DECLARE v NUMBER; BEGIN BEGIN LET c CURSOR FOR SELECT 5; OPEN c; FETCH c INTO "
                + "v; END; OPEN c; RETURN v; END; $$");
        assertAnswer(
            "Function CUR_Q successfully created.",
            "CREATE OR REPLACE PROCEDURE cur_q() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v NUMBER; BEGIN OPEN "
                + "c; FETCH c INTO v; RETURN v; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 76 : SQL compilation "
                + "error: error line 1 at position 30 | invalid identifier 'C'",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT n FROM t1 ORDER BY n; r VARCHAR; BEGIN OPEN c; CALL "
                + "cur_q() INTO :r; RETURN r; END; $$");
    }

    @Test
    public void openFetchAndCloseOutOfTurn() {
        assertAnswer(
            "Table T1 successfully created.",
            "CREATE OR REPLACE TABLE t1 (n NUMBER)");
        assertAnswer(
            "3",
            "INSERT INTO t1 VALUES (1), (2), (3)");
        assertAnswer(
            "REFUSED CURSOR C is not open",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT n FROM t1 ORDER BY n; v NUMBER; BEGIN FETCH c INTO "
                + "v; RETURN v; EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE || ' ' "
                + "|| SQLERRM; END; $$");
        assertAnswer(
            "statement 92215 P0000 Stored procedure execution error: CURSOR 'C' is already open.",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT n FROM t1 ORDER BY n; v NUMBER; BEGIN OPEN c; OPEN "
                + "c; RETURN 1; EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE || ' ' "
                + "|| SQLERRM; END; $$");
        assertAnswer(
            "statement 92201 P0000 CURSOR C is not open",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT n FROM t1 ORDER BY n; v NUMBER; BEGIN CLOSE c; "
                + "RETURN 1; EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE "
                + "|| ' ' || SQLERRM; WHEN STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || "
                + "' ' || SQLERRM; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; "
                + "END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 67 : CURSOR C is not open",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT n FROM t1 ORDER BY n; v NUMBER; BEGIN CLOSE c; "
                + "RETURN 1; END; $$");
        assertAnswer(
            "REFUSED CURSOR C is not open",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT n FROM t1 ORDER BY n; v NUMBER; BEGIN OPEN c; CLOSE "
                + "c; FETCH c INTO v; RETURN v; EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE "
                + "|| ' ' || SQLSTATE || ' ' || SQLERRM; WHEN STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || "
                + "' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE "
                + "|| ' ' || SQLERRM; END; $$");
        assertAnswer(
            "REFUSED CURSOR C is not open",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT n FROM t1 ORDER BY n; v NUMBER; BEGIN OPEN c; CLOSE "
                + "c; FETCH c INTO v; RETURN v; END; $$");
        assertAnswer(
            "reopened 1",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT n FROM t1 ORDER BY n; v NUMBER; BEGIN OPEN c; FETCH "
                + "c INTO v; CLOSE c; OPEN c; FETCH c INTO v; CLOSE c; RETURN 'reopened ' || v; END; $$");
        assertAnswer(
            "past the end -1",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT n FROM t1 ORDER BY n; v NUMBER; BEGIN OPEN c; FETCH "
                + "c INTO v; FETCH c INTO v; FETCH c INTO v; FETCH c INTO v; RETURN 'past the end ' || NVL(v, -1); "
                + "END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 72 | invalid identifier 'NOSUCH'",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT n FROM t1 ORDER BY n; v NUMBER; BEGIN OPEN nosuch; "
                + "RETURN 1; END; $$");
        assertAnswer(
            "REFUSED CURSOR C is not open",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT n FROM t1 ORDER BY n; v NUMBER; BEGIN FETCH c INTO "
                + "v; RETURN v; END; $$");
        assertAnswer(
            "REFUSED CURSOR C is not open",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT n FROM t1 ORDER BY n; v NUMBER; BEGIN BEGIN FETCH c "
                + "INTO v; EXCEPTION WHEN OTHER THEN RETURN 'inner caught ' || SQLERRM; END; RETURN 'not caught'; "
                + "END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET DEFAULT (SELECT 1 AS a); BEGIN EXECUTE IMMEDIATE 'DECLARE "
                + "rs RESULTSET DEFAULT (SELECT 2 AS a); BEGIN RETURN TABLE(rs); END;'; RETURN TABLE(rs); END; $$");
        assertAnswer(
            "19 29 39 ",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT n FROM t1 ORDER BY n; v NUMBER; s VARCHAR DEFAULT "
                + "''; BEGIN FOR r IN c DO BEGIN LET c CURSOR FOR SELECT 9; LET w NUMBER := 0; OPEN c; FETCH c INTO "
                + "w; CLOSE c; s := s || r.n || w || ' '; END; END FOR; RETURN s; END; $$");
    }
}
