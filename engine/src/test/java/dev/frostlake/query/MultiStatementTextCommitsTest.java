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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * Each statement of a text of several statements is a statement of its own: what it changed stays when a
 * later one fails to compile or to run - an invalid identifier, a missing table, a value list of another
 * width, a division by zero, a bare stage - in a block, under a handler and in a procedure. A text that does
 * not parse runs nothing. Inside a transaction the block opened the text's statements still commit on their
 * own: a ROLLBACK undoes what the block itself changed before and after the text and leaves the text's
 * changes, the transaction is the same one after the text, a text of one statement belongs to it, and a
 * transaction the text opens itself is the text's to end. Every answer is live-verified.
 */
public class MultiStatementTextCommitsTest extends MeasuredCellsTestSupport {

    @Test
    public void whatTheEarlierStatementsChangedStays() {
        engine.execute(
            "CREATE OR REPLACE TABLE t2 (v VARCHAR)");
        engine.execute(
            "CREATE OR REPLACE STAGE st");
        engine.execute(
            "ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
        try {
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"SELECT "
                    + "nosuch FROM t2\" (at line 1, position 29). | SQL compilation error: error line 1 at position "
                    + "7 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                    + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                    + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "BEGIN EXECUTE IMMEDIATE $$INSERT INTO t2 VALUES ('a'); SELECT nosuch FROM t2$$; RETURN 1; END;");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM t2");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"SELECT * "
                    + "FROM nosuch_table\" (at line 1, position 29). | SQL compilation error: | Object "
                    + "'NOSUCH_TABLE' does not exist or not authorized. Your primary role <ROLE> must have at least "
                    + "one privilege granted on TABLE NOSUCH_TABLE. in SYSTEM$MULTISTMT at '    throw `Execution of "
                    + "multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                    + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "BEGIN EXECUTE IMMEDIATE $$INSERT INTO t2 VALUES ('b'); SELECT * FROM nosuch_table$$; RETURN 1; "
                    + "END;");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM t2");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"INSERT INTO "
                    + "t2 VALUES (1, 2)\" (at line 1, position 29). | SQL compilation error: | Insert value list "
                    + "does not match column list expecting 1 but got 2 in SYSTEM$MULTISTMT at '    throw `Execution "
                    + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                    + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "BEGIN EXECUTE IMMEDIATE $$INSERT INTO t2 VALUES ('c'); INSERT INTO t2 VALUES (1, 2); INSERT INTO "
                    + "t2 VALUES ('d')$$; RETURN 1; END;");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM t2");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"SELECT 1 / "
                    + "0\" (at line 1, position 29). | Division by zero in SYSTEM$MULTISTMT at '    throw `Execution "
                    + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                    + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "BEGIN EXECUTE IMMEDIATE $$INSERT INTO t2 VALUES ('e'); SELECT 1 / 0; INSERT INTO t2 VALUES "
                    + "('f')$$; RETURN 1; END;");
            assertAnswer(
                "4",
                "SELECT COUNT(*) FROM t2");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"SELECT "
                    + "UPPER(1, 2)\" (at line 1, position 29). | SQL compilation error: error line 1 at position 7 | "
                    + "too many arguments for function [UPPER(1, 2)] expected 1, got 2 in SYSTEM$MULTISTMT at '    "
                    + "throw `Execution of multiple statements failed on statement {0} (at line {1}, position "
                    + "{2}).`.replace('{1}', LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "BEGIN EXECUTE IMMEDIATE $$INSERT INTO t2 VALUES ('g'); SELECT UPPER(1, 2)$$; RETURN 1; END;");
            assertAnswer(
                "5",
                "SELECT COUNT(*) FROM t2");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"SELECT "
                    + "LOWER(UPPER(@st))\" (at line 1, position 29). | SQL compilation error: error line 1 at "
                    + "position 13 | invalid argument for function [UPPER] unexpected argument [@st] at position 0, "
                    + "in SYSTEM$MULTISTMT at '    throw `Execution of multiple statements failed on statement {0} "
                    + "(at line {1}, position {2}).`.replace('{1}', LINES[i])' position 4 | stackstrace:  | "
                    + "SYSTEM$MULTISTMT line: 10",
                "BEGIN EXECUTE IMMEDIATE $$INSERT INTO t2 VALUES ('h'); SELECT LOWER(UPPER(@st))$$; RETURN 1; END;");
            assertAnswer(
                "6",
                "SELECT COUNT(*) FROM t2");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: | syntax error line 1 at position 49 unexpected '@st'.",
                "BEGIN EXECUTE IMMEDIATE $$INSERT INTO t2 VALUES ('i'); SELECT UPPER(@st), (@st)$$; RETURN 1; END;");
            assertAnswer(
                "6",
                "SELECT COUNT(*) FROM t2");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"SELECT "
                    + "UPPER(@st)\" (at line 1, position 37). | SQL compilation error: error line 1 at position 7 | "
                    + "invalid argument for function [UPPER] unexpected argument [@st] at position 0, in "
                    + "SYSTEM$MULTISTMT at '    throw `Execution of multiple statements failed on statement {0} (at "
                    + "line {1}, position {2}).`.replace('{1}', LINES[i])' position 4 | stackstrace:  | "
                    + "SYSTEM$MULTISTMT line: 10",
                "BEGIN EXECUTE IMMEDIATE $$/* c */ INSERT INTO t2 VALUES ('j'); /* d */ SELECT UPPER(@st)$$; "
                    + "RETURN 1; END;");
            assertAnswer(
                "7",
                "SELECT COUNT(*) FROM t2");
            assertAnswer(
                "JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                    + "\"SELECT nosuch FROM t2\" (at line 1, position 29). | SQL compilation error: error line 1 at "
                    + "position 7 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of "
                    + "multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                    + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "BEGIN EXECUTE IMMEDIATE $$INSERT INTO t2 VALUES ('k'); SELECT nosuch FROM t2$$; RETURN 1; "
                    + "EXCEPTION WHEN OTHER THEN RETURN SQLERRM; END;");
            assertAnswer(
                "8",
                "SELECT COUNT(*) FROM t2");
            assertAnswer(
                "rolled back",
                "BEGIN BEGIN TRANSACTION; EXECUTE IMMEDIATE $$INSERT INTO t2 VALUES ('l'); SELECT nosuch FROM "
                    + "t2$$; COMMIT; RETURN 'committed'; EXCEPTION WHEN OTHER THEN ROLLBACK; RETURN 'rolled back'; "
                    + "END;");
            assertAnswer(
                "9",
                "SELECT COUNT(*) FROM t2");
            assertAnswer(
                "1",
                "BEGIN EXECUTE IMMEDIATE $$INSERT INTO t2 VALUES ('m'); INSERT INTO t2 VALUES ('n')$$; RETURN 1; "
                    + "END;");
            assertAnswer(
                "11",
                "SELECT COUNT(*) FROM t2");
            assertAnswer(
                "Multiple statements executed successfully.",
                "BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE $$INSERT INTO t2 VALUES ('o'); SELECT COUNT(*) AS n "
                    + "FROM t2$$); RETURN TABLE(r); END;");
            assertAnswer(
                "12",
                "SELECT COUNT(*) FROM t2");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"INSERT INTO "
                    + "t3 VALUES ('x')\" (at line 1, position 65). | DML operation to table T3 failed on column A "
                    + "with error: Numeric value 'x' is not recognized in SYSTEM$MULTISTMT at '    throw `Execution "
                    + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                    + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "BEGIN EXECUTE IMMEDIATE $$INSERT INTO t2 VALUES ('p'); CREATE OR REPLACE TABLE t3 (a INT); "
                    + "INSERT INTO t3 VALUES ('x'); INSERT INTO t2 VALUES ('q')$$; RETURN 1; END;");
            assertAnswer(
                "13",
                "SELECT COUNT(*) FROM t2");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM t3");
            assertAnswer(
                "Function P1301 successfully created.",
                "CREATE OR REPLACE PROCEDURE p1301() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN EXECUTE IMMEDIATE "
                    + "'INSERT INTO t2 VALUES (''r''); SELECT nosuch FROM t2'; RETURN 1; END; $$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"SELECT "
                    + "nosuch FROM t2\" (at line 1, position 29). | SQL compilation error: error line 1 at position "
                    + "7 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                    + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                    + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "CALL p1301()");
            assertAnswer(
                "14",
                "SELECT COUNT(*) FROM t2");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"DELETE FROM "
                    + "t2 WHERE nosuch = ...\" (at line 1, position 71). | SQL compilation error: error line 1 at "
                    + "position 21 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of "
                    + "multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                    + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "BEGIN EXECUTE IMMEDIATE $$INSERT INTO t2 VALUES ('s'); UPDATE t2 SET v = v || '!' WHERE v = 's'; "
                    + "DELETE FROM t2 WHERE nosuch = 1$$; RETURN 1; END;");
            assertAnswer(
                "15",
                "SELECT COUNT(*) FROM t2");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM t2 WHERE v = 's!'");
            assertAnswer(
                "2",
                "BEGIN EXECUTE IMMEDIATE $$INSERT INTO t2 VALUES ('t'); SELECT 1; SELECT 2$$; RETURN 2; END;");
            assertAnswer(
                "16",
                "SELECT COUNT(*) FROM t2");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : SQL compilation "
                    + "error: error line 1 at position 7 | invalid identifier 'NOSUCH'",
                "BEGIN INSERT INTO t2 VALUES ('u'); SELECT nosuch FROM t2; RETURN 1; END;");
            assertAnswer(
                "17",
                "SELECT COUNT(*) FROM t2");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 51 : Division by zero",
                "BEGIN INSERT INTO t2 VALUES ('w'); LET x NUMBER := 1 / 0; RETURN 1; END;");
            assertAnswer(
                "18",
                "SELECT COUNT(*) FROM t2");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION UNSET MULTI_STATEMENT_COUNT");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
            engine.execute(
                "ROLLBACK");
            engine.execute(
                "ALTER SESSION UNSET MULTI_STATEMENT_COUNT");
        }
    }

    @Test
    public void insideATransactionTheBlockOpened() {
        engine.execute(
            "CREATE OR REPLACE TABLE t2 (v VARCHAR)");
        engine.execute(
            "ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
        try {
            assertAnswer(
                "rolled back",
                "BEGIN BEGIN TRANSACTION; INSERT INTO t2 VALUES ('x1'); EXECUTE IMMEDIATE $$INSERT INTO t2 VALUES "
                    + "('x2'); INSERT INTO t2 VALUES ('x3')$$; INSERT INTO t2 VALUES ('x4'); ROLLBACK; RETURN "
                    + "'rolled back'; EXCEPTION WHEN OTHER THEN ROLLBACK; RETURN 'failed: ' || SQLERRM; END;");
            assertAnswer(
                "x2,x3",
                "SELECT LISTAGG(v, ',') WITHIN GROUP (ORDER BY v) FROM t2");
            assertAnswer(
                "rolled back",
                "BEGIN BEGIN TRANSACTION; EXECUTE IMMEDIATE $$INSERT INTO t2 VALUES ('y1')$$; ROLLBACK; RETURN "
                    + "'rolled back'; EXCEPTION WHEN OTHER THEN ROLLBACK; RETURN 'failed: ' || SQLERRM; END;");
            assertAnswer(
                "x2,x3",
                "SELECT LISTAGG(v, ',') WITHIN GROUP (ORDER BY v) FROM t2");
            assertAnswer(
                "rolled back",
                "BEGIN BEGIN TRANSACTION; INSERT INTO t2 VALUES ('z1'); EXECUTE IMMEDIATE $$INSERT INTO t2 VALUES "
                    + "('z2'); SELECT nosuch FROM t2$$; COMMIT; RETURN 'committed'; EXCEPTION WHEN OTHER THEN "
                    + "ROLLBACK; RETURN 'rolled back'; END;");
            assertAnswer(
                "x2,x3,z2",
                "SELECT LISTAGG(v, ',') WITHIN GROUP (ORDER BY v) FROM t2");
            assertAnswer(
                "same:open",
                "BEGIN BEGIN TRANSACTION; LET a VARCHAR := CURRENT_TRANSACTION(); EXECUTE IMMEDIATE $$INSERT INTO "
                    + "t2 VALUES ('w1'); INSERT INTO t2 VALUES ('w2')$$; LET b VARCHAR := CURRENT_TRANSACTION(); "
                    + "ROLLBACK; RETURN IFF(a = b, 'same', 'other') || ':' || IFF(b IS NULL, 'none', 'open'); "
                    + "EXCEPTION WHEN OTHER THEN ROLLBACK; RETURN 'failed: ' || SQLERRM; END;");
            assertAnswer(
                "w1,w2,x2,x3,z2",
                "SELECT LISTAGG(v, ',') WITHIN GROUP (ORDER BY v) FROM t2");
            assertAnswer(
                "committed",
                "BEGIN BEGIN TRANSACTION; INSERT INTO t2 VALUES ('v1'); EXECUTE IMMEDIATE $$INSERT INTO t2 VALUES "
                    + "('v2'); INSERT INTO t2 VALUES ('v3')$$; COMMIT; RETURN 'committed'; EXCEPTION WHEN OTHER THEN "
                    + "ROLLBACK; RETURN 'failed: ' || SQLERRM; END;");
            assertAnswer(
                "v1,v2,v3,w1,w2,x2,x3,z2",
                "SELECT LISTAGG(v, ',') WITHIN GROUP (ORDER BY v) FROM t2");
            assertAnswer(
                "1",
                "BEGIN EXECUTE IMMEDIATE $$BEGIN TRANSACTION; INSERT INTO t2 VALUES ('u1'); ROLLBACK$$; RETURN 1; "
                    + "END;");
            assertAnswer(
                "v1,v2,v3,w1,w2,x2,x3,z2",
                "SELECT LISTAGG(v, ',') WITHIN GROUP (ORDER BY v) FROM t2");
            assertAnswer(
                "failed",
                "BEGIN EXECUTE IMMEDIATE $$BEGIN TRANSACTION; INSERT INTO t2 VALUES ('s1'); COMMIT; INSERT INTO "
                    + "t2 VALUES ('s2'); SELECT nosuch FROM t2$$; RETURN 1; EXCEPTION WHEN OTHER THEN RETURN "
                    + "'failed'; END;");
            assertAnswer(
                "s1,s2,v1,v2,v3,w1,w2,x2,x3,z2",
                "SELECT LISTAGG(v, ',') WITHIN GROUP (ORDER BY v) FROM t2");
            assertAnswer(
                "same:open",
                "BEGIN BEGIN TRANSACTION; LET a VARCHAR := CURRENT_TRANSACTION(); EXECUTE IMMEDIATE $$SELECT 1; "
                    + "SELECT 2$$; LET b VARCHAR := CURRENT_TRANSACTION(); ROLLBACK; RETURN IFF(a = b, 'same', "
                    + "'other') || ':' || IFF(b IS NULL, 'none', 'open'); EXCEPTION WHEN OTHER THEN ROLLBACK; RETURN "
                    + "'failed: ' || SQLERRM; END;");
            assertAnswer(
                "rolled back",
                "BEGIN BEGIN TRANSACTION; INSERT INTO t2 VALUES ('r1'); EXECUTE IMMEDIATE $$SELECT 1; SELECT 2$$; "
                    + "ROLLBACK; RETURN 'rolled back'; EXCEPTION WHEN OTHER THEN ROLLBACK; RETURN 'failed: ' || "
                    + "SQLERRM; END;");
            assertAnswer(
                "s1,s2,v1,v2,v3,w1,w2,x2,x3,z2",
                "SELECT LISTAGG(v, ',') WITHIN GROUP (ORDER BY v) FROM t2");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION UNSET MULTI_STATEMENT_COUNT");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
            engine.execute(
                "ROLLBACK");
            engine.execute(
                "ALTER SESSION UNSET MULTI_STATEMENT_COUNT");
        }
    }
}
