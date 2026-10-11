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
 * How deep bodies run inside one another. A body is a procedure's call, a block the session sends and a dynamic
 * text that is a block; sixteen run inside one another in full. A seventeenth still runs - an IF, an assignment
 * of an expression, a function call and a RETURN answer - but the first statement it issues, a query read by
 * one of its expressions included, ends it: its own handler does not see the refusal, and the statement that
 * started it fails with {@code Stored procedure execution error: Stored procedure exceeds the maximum allowed
 * number of recursive calls.}, 90235 / 42601, worded once more by every body it leaves. The sixteenth body may
 * handle it. Nothing the bodies wrote is undone: the rows stay, in the open transaction when there is one, and
 * that transaction stays open. A dynamic text that is one statement is no level: a CALL run by EXECUTE IMMEDIATE
 * nests as a CALL written out does, and sixteen EXECUTE IMMEDIATE statements run inside one another. Every
 * recursion here ends by its own counter. Every answer is live-verified.
 */
public class CallDepthLimitTest extends MeasuredCellsTestSupport {

    @Test
    public void sixteenBodiesRunAndTheNextIsEnded() {
        assertAnswer(
            "Function REC successfully created.",
            "CREATE OR REPLACE PROCEDURE rec(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE r NUMBER; BEGIN "
                + "IF (n <= 0) THEN RETURN 0; END IF; CALL rec(:n - 1) INTO :r; RETURN r + 1; END; $$");
        assertAnswer(
            "3",
            "CALL rec(3)");
        assertAnswer(
            "8",
            "CALL rec(8)");
        assertAnswer(
            "12",
            "CALL rec(12)");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : Uncaught exception "
                + "of type 'STATEMENT_ERROR' on line 1 at position 60 : Uncaught exception of type 'STATEMENT_ERROR' "
                + "on line 1 at position 60 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 "
                + ": Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 60 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 60 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 60 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 60 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 60 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 60 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : "
                + "Stored procedure execution error: Stored procedure exceeds the maximum allowed number of "
                + "recursive calls.",
            "CALL rec(20)");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : Uncaught exception "
                + "of type 'STATEMENT_ERROR' on line 1 at position 60 : Uncaught exception of type 'STATEMENT_ERROR' "
                + "on line 1 at position 60 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 "
                + ": Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 60 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 60 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 60 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 60 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 60 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 60 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : "
                + "Stored procedure execution error: Stored procedure exceeds the maximum allowed number of "
                + "recursive calls.",
            "CALL rec(40)");
        assertAnswer(
            "Table TR successfully created.",
            "CREATE OR REPLACE TABLE tr (n NUMBER)");
        assertAnswer(
            "Function REC_INS successfully created.",
            "CREATE OR REPLACE PROCEDURE rec_ins(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE r NUMBER; "
                + "BEGIN INSERT INTO tr VALUES (:n); IF (n >= 150) THEN RETURN n; END IF; CALL rec_ins(:n + 1) INTO "
                + ":r; RETURN r; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 : Uncaught exception "
                + "of type 'STATEMENT_ERROR' on line 1 at position 90 : Uncaught exception of type 'STATEMENT_ERROR' "
                + "on line 1 at position 90 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 "
                + ": Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 90 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 90 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 90 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 90 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 90 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 90 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 : "
                + "Stored procedure execution error: Stored procedure exceeds the maximum allowed number of "
                + "recursive calls.",
            "CALL rec_ins(1)");
        assertAnswer(
            "16 / 16",
            "SELECT COUNT(*) || ' / ' || NVL(MAX(n), 0) FROM tr");
        assertAnswer(
            "statement: 90235 42601 Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 90 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 90 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 90 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 90 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 90 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 90 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 90 : Stored procedure execution error: Stored "
                + "procedure exceeds the maximum allowed number of recursive calls.",
            "EXECUTE IMMEDIATE $$ DECLARE r NUMBER; BEGIN CALL rec_ins(1) INTO :r; RETURN r; EXCEPTION WHEN "
                + "STATEMENT_ERROR THEN RETURN 'statement: ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN "
                + "OTHER THEN RETURN 'other: ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
    }

    @Test
    public void aFailedDeepCallKeepsWhatItWrote() {
        assertAnswer(
            "Table TR successfully created.",
            "CREATE OR REPLACE TABLE tr (n NUMBER)");
        assertAnswer(
            "Function REC_INS successfully created.",
            "CREATE OR REPLACE PROCEDURE rec_ins(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE r NUMBER; "
                + "BEGIN INSERT INTO tr VALUES (:n); IF (n >= 150) THEN RETURN n; END IF; CALL rec_ins(:n + 1) INTO "
                + ":r; RETURN r; END; $$");
        assertAnswer(
            "Statement executed successfully.",
            "TRUNCATE TABLE tr");
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tr VALUES (-1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 : Uncaught "
                    + "exception of type 'STATEMENT_ERROR' on line 1 at position 90 : Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 90 : Uncaught exception of type 'STATEMENT_ERROR' on "
                    + "line 1 at position 90 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 "
                    + ": Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 : Uncaught exception "
                    + "of type 'STATEMENT_ERROR' on line 1 at position 90 : Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 90 : Uncaught exception of type 'STATEMENT_ERROR' on "
                    + "line 1 at position 90 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 "
                    + ": Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 : Uncaught exception "
                    + "of type 'STATEMENT_ERROR' on line 1 at position 90 : Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 90 : Uncaught exception of type 'STATEMENT_ERROR' on "
                    + "line 1 at position 90 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 "
                    + ": Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 : Stored procedure "
                    + "execution error: Stored procedure exceeds the maximum allowed number of recursive calls.",
                "CALL rec_ins(1)");
            assertAnswer(
                "17 / 16",
                "SELECT COUNT(*) || ' / ' || NVL(MAX(n), 0) FROM tr");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
        } finally {
            answer(
                "ROLLBACK");
        }
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM tr");
    }

    @Test
    public void dynamicStatementsAreNoLevels() {
        assertAnswer(
            "50",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT 'SELECT 1'; BEGIN FOR i IN 1 TO 2 DO s := 'EXECUTE "
                + "IMMEDIATE ''' || REPLACE(s, '''', '''''') || ''''; END FOR; EXECUTE IMMEDIATE :s; RETURN "
                + "LENGTH(s); EXCEPTION WHEN OTHER THEN RETURN 'other: ' || SQLCODE || ' ' || SQLSTATE || ' ' || "
                + "LEFT(SQLERRM, 200); END; $$");
        assertAnswer(
            "110",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT 'SELECT 1'; BEGIN FOR i IN 1 TO 4 DO s := 'EXECUTE "
                + "IMMEDIATE ''' || REPLACE(s, '''', '''''') || ''''; END FOR; EXECUTE IMMEDIATE :s; RETURN "
                + "LENGTH(s); EXCEPTION WHEN OTHER THEN RETURN 'other: ' || SQLCODE || ' ' || SQLSTATE || ' ' || "
                + "LEFT(SQLERRM, 200); END; $$");
        assertAnswer(
            "662",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT 'SELECT 1'; BEGIN FOR i IN 1 TO 8 DO s := 'EXECUTE "
                + "IMMEDIATE ''' || REPLACE(s, '''', '''''') || ''''; END FOR; EXECUTE IMMEDIATE :s; RETURN "
                + "LENGTH(s); EXCEPTION WHEN OTHER THEN RETURN 'other: ' || SQLCODE || ' ' || SQLSTATE || ' ' || "
                + "LEFT(SQLERRM, 200); END; $$");
        assertAnswer(
            "8414",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT 'SELECT 1'; BEGIN FOR i IN 1 TO 12 DO s := 'EXECUTE "
                + "IMMEDIATE ''' || REPLACE(s, '''', '''''') || ''''; END FOR; EXECUTE IMMEDIATE :s; RETURN "
                + "LENGTH(s); EXCEPTION WHEN OTHER THEN RETURN 'other: ' || SQLCODE || ' ' || SQLSTATE || ' ' || "
                + "LEFT(SQLERRM, 200); END; $$");
        assertAnswer(
            "131366",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT 'SELECT 1'; BEGIN FOR i IN 1 TO 16 DO s := 'EXECUTE "
                + "IMMEDIATE ''' || REPLACE(s, '''', '''''') || ''''; END FOR; EXECUTE IMMEDIATE :s; RETURN "
                + "LENGTH(s); EXCEPTION WHEN OTHER THEN RETURN 'other: ' || SQLCODE || ' ' || SQLSTATE || ' ' || "
                + "LEFT(SQLERRM, 200); END; $$");
    }

    @Test
    public void theExactDepthAndWhatCountsAsALevel() {
        assertAnswer(
            "Function REC successfully created.",
            "CREATE OR REPLACE PROCEDURE rec(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE r NUMBER; BEGIN "
                + "IF (n <= 0) THEN RETURN 0; END IF; CALL rec(:n - 1) INTO :r; RETURN r + 1; END; $$");
        assertAnswer(
            "14",
            "CALL rec(14)");
        assertAnswer(
            "15",
            "CALL rec(15)");
        assertAnswer(
            "16",
            "CALL rec(16)");
        assertAnswer(
            "14",
            "EXECUTE IMMEDIATE $$ DECLARE r NUMBER; BEGIN CALL rec(14) INTO :r; RETURN r; END; $$");
        assertAnswer(
            "15",
            "EXECUTE IMMEDIATE $$ DECLARE r NUMBER; BEGIN CALL rec(15) INTO :r; RETURN r; EXCEPTION WHEN OTHER "
                + "THEN RETURN 'other: ' || SQLCODE || ' ' || SQLSTATE; END; $$");
        assertAnswer(
            "Function REC_C successfully created.",
            "CREATE OR REPLACE PROCEDURE rec_c(n NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r VARCHAR; "
                + "BEGIN IF (n >= 40) THEN RETURN 'bottom'; END IF; CALL rec_c(:n + 1) INTO :r; RETURN r; EXCEPTION "
                + "WHEN OTHER THEN RETURN n || ': ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "16: 90235 42601 Stored procedure execution error: Stored procedure exceeds the maximum allowed "
                + "number of recursive calls.",
            "CALL rec_c(1)");
        assertAnswer(
            "Function MIX successfully created.",
            "CREATE OR REPLACE PROCEDURE mix(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN IF (n <= 0) THEN "
                + "RETURN 0; END IF; EXECUTE IMMEDIATE 'CALL mix(' || (n - 1) || ')'; RETURN n; END; $$");
        assertAnswer(
            "14",
            "CALL mix(14)");
        assertAnswer(
            "15",
            "CALL mix(15)");
        assertAnswer(
            "16",
            "CALL mix(16)");
        assertAnswer(
            "Table STEPS successfully created.",
            "CREATE OR REPLACE TABLE steps (lvl NUMBER, txt VARCHAR)");
        assertAnswer(
            "1",
            "INSERT INTO steps SELECT 0, 'SELECT 1'");
        assertAnswer(
            "40",
            "INSERT INTO steps SELECT n, 'BEGIN LET s VARCHAR := (SELECT txt FROM steps WHERE lvl = ' || (n - 1) "
                + "|| '); EXECUTE IMMEDIATE :s; RETURN ' || n || '; END;' FROM (SELECT ROW_NUMBER() OVER (ORDER BY "
                + "SEQ4()) AS n FROM TABLE(GENERATOR(ROWCOUNT => 40)))");
        assertAnswer(
            "41 / 40",
            "SELECT COUNT(*) || ' / ' || MAX(lvl) FROM steps");
        assertAnswer(
            "ran 5",
            "EXECUTE IMMEDIATE $$ BEGIN LET s VARCHAR := (SELECT txt FROM steps WHERE lvl = 5); EXECUTE IMMEDIATE "
                + ":s; RETURN 'ran 5'; EXCEPTION WHEN OTHER THEN RETURN 'other: ' || SQLCODE || ' ' || SQLSTATE || ' "
                + "' || RIGHT(SQLERRM, 120); END; $$");
        assertAnswer(
            "ran 14",
            "EXECUTE IMMEDIATE $$ BEGIN LET s VARCHAR := (SELECT txt FROM steps WHERE lvl = 14); EXECUTE "
                + "IMMEDIATE :s; RETURN 'ran 14'; EXCEPTION WHEN OTHER THEN RETURN 'other: ' || SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || RIGHT(SQLERRM, 120); END; $$");
        assertAnswer(
            "ran 15",
            "EXECUTE IMMEDIATE $$ BEGIN LET s VARCHAR := (SELECT txt FROM steps WHERE lvl = 15); EXECUTE "
                + "IMMEDIATE :s; RETURN 'ran 15'; EXCEPTION WHEN OTHER THEN RETURN 'other: ' || SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || RIGHT(SQLERRM, 120); END; $$");
        assertAnswer(
            "other: 90235 42601  position 62 : Stored procedure execution error: Stored procedure exceeds the "
                + "maximum allowed number of recursive calls.",
            "EXECUTE IMMEDIATE $$ BEGIN LET s VARCHAR := (SELECT txt FROM steps WHERE lvl = 16); EXECUTE "
                + "IMMEDIATE :s; RETURN 'ran 16'; EXCEPTION WHEN OTHER THEN RETURN 'other: ' || SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || RIGHT(SQLERRM, 120); END; $$");
        assertAnswer(
            "other: 90235 42601  position 62 : Stored procedure execution error: Stored procedure exceeds the "
                + "maximum allowed number of recursive calls.",
            "EXECUTE IMMEDIATE $$ BEGIN LET s VARCHAR := (SELECT txt FROM steps WHERE lvl = 17); EXECUTE "
                + "IMMEDIATE :s; RETURN 'ran 17'; EXCEPTION WHEN OTHER THEN RETURN 'other: ' || SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || RIGHT(SQLERRM, 120); END; $$");
        assertAnswer(
            "other: 90235 42601  position 63 : Stored procedure execution error: Stored procedure exceeds the "
                + "maximum allowed number of recursive calls.",
            "EXECUTE IMMEDIATE $$ BEGIN LET s VARCHAR := (SELECT txt FROM steps WHERE lvl = 30); EXECUTE "
                + "IMMEDIATE :s; RETURN 'ran 30'; EXCEPTION WHEN OTHER THEN RETURN 'other: ' || SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || RIGHT(SQLERRM, 120); END; $$");
    }

    @Test
    public void whatTheDeepestBodyMayDo() {
        assertAnswer(
            "Table TR successfully created.",
            "CREATE OR REPLACE TABLE tr (n NUMBER)");
        assertAnswer(
            "Function LEAF successfully created.",
            "CREATE OR REPLACE PROCEDURE leaf() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'leaf'; END; $$");
        assertAnswer(
            "Function DEEP successfully created.",
            "CREATE OR REPLACE PROCEDURE deep(n NUMBER, what VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE "
                + "r VARCHAR; x NUMBER; BEGIN IF (n > 0) THEN CALL deep(:n - 1, :what) INTO :r; RETURN r; END IF; IF "
                + "(what = 'expr') THEN x := 1 + 1; RETURN 'expr ' || x; ELSEIF (what = 'function') THEN x := "
                + "ABS(-7); RETURN 'function ' || x; ELSEIF (what = 'select') THEN SELECT 5 INTO :x; RETURN 'select "
                + "' || x; ELSEIF (what = 'subquery') THEN x := (SELECT 6); RETURN 'subquery ' || x; ELSEIF (what = "
                + "'count') THEN x := (SELECT COUNT(*) FROM tr); RETURN 'count ' || x; ELSEIF (what = 'insert') THEN "
                + "INSERT INTO tr VALUES (99); RETURN 'insert'; ELSEIF (what = 'leaf') THEN CALL leaf() INTO :r; "
                + "RETURN 'called ' || r; ELSEIF (what = 'dynamic') THEN EXECUTE IMMEDIATE 'SELECT 1'; RETURN "
                + "'dynamic'; ELSEIF (what = 'handler') THEN BEGIN SELECT 5 INTO :x; RETURN 'no refusal ' || x; "
                + "EXCEPTION WHEN OTHER THEN RETURN 'this level caught ' || SQLCODE; END; END IF; RETURN 'none'; "
                + "END; $$");
        assertAnswer(
            "none",
            "CALL deep(15, 'none')");
        assertAnswer(
            "none",
            "CALL deep(16, 'none')");
        assertAnswer(
            "expr 2",
            "CALL deep(15, 'expr')");
        assertAnswer(
            "expr 2",
            "CALL deep(16, 'expr')");
        assertAnswer(
            "function 7",
            "CALL deep(15, 'function')");
        assertAnswer(
            "function 7",
            "CALL deep(16, 'function')");
        assertAnswer(
            "select 5",
            "CALL deep(15, 'select')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception "
                + "of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' "
                + "on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 "
                + ": Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : "
                + "Stored procedure execution error: Stored procedure exceeds the maximum allowed number of "
                + "recursive calls.",
            "CALL deep(16, 'select')");
        assertAnswer(
            "subquery 6",
            "CALL deep(15, 'subquery')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception "
                + "of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' "
                + "on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 "
                + ": Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : "
                + "Stored procedure execution error: Stored procedure exceeds the maximum allowed number of "
                + "recursive calls.",
            "CALL deep(16, 'subquery')");
        assertAnswer(
            "count 0",
            "CALL deep(15, 'count')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception "
                + "of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' "
                + "on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 "
                + ": Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : "
                + "Stored procedure execution error: Stored procedure exceeds the maximum allowed number of "
                + "recursive calls.",
            "CALL deep(16, 'count')");
        assertAnswer(
            "insert",
            "CALL deep(15, 'insert')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception "
                + "of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' "
                + "on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 "
                + ": Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : "
                + "Stored procedure execution error: Stored procedure exceeds the maximum allowed number of "
                + "recursive calls.",
            "CALL deep(16, 'insert')");
        assertAnswer(
            "called leaf",
            "CALL deep(15, 'leaf')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception "
                + "of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' "
                + "on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 "
                + ": Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : "
                + "Stored procedure execution error: Stored procedure exceeds the maximum allowed number of "
                + "recursive calls.",
            "CALL deep(16, 'leaf')");
        assertAnswer(
            "dynamic",
            "CALL deep(15, 'dynamic')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception "
                + "of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' "
                + "on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 "
                + ": Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : "
                + "Stored procedure execution error: Stored procedure exceeds the maximum allowed number of "
                + "recursive calls.",
            "CALL deep(16, 'dynamic')");
        assertAnswer(
            "no refusal 5",
            "CALL deep(15, 'handler')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception "
                + "of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' "
                + "on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 "
                + ": Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 1 at position 52 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : "
                + "Stored procedure execution error: Stored procedure exceeds the maximum allowed number of "
                + "recursive calls.",
            "CALL deep(16, 'handler')");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM tr");
        assertAnswer(
            "select 5",
            "EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN CALL deep(14, 'select') INTO :r; RETURN r; EXCEPTION "
                + "WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || RIGHT(SQLERRM, 110); END; $$");
        assertAnswer(
            "90235 42601 52 : Stored procedure execution error: Stored procedure exceeds the maximum allowed "
                + "number of recursive calls.",
            "EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN CALL deep(15, 'select') INTO :r; RETURN r; EXCEPTION "
                + "WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || RIGHT(SQLERRM, 110); END; $$");
        assertAnswer(
            "none",
            "EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN CALL deep(15, 'none') INTO :r; RETURN r; EXCEPTION "
                + "WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || RIGHT(SQLERRM, 110); END; $$");
    }
}
