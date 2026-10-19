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
 * The places inside a text an EXECUTE IMMEDIATE runs are the text's own. A text the request hands over
 * itself is placed as it is written: a comment that opens it re-bases nothing, so a bare stage passed to a
 * function is refused at its call's place past the comment, on the comment's line or after it. A text a
 * block runs - written in the block, held in a variable, in a procedure - is a statement of its own, placed
 * as a request is: a comment that opens it is not counted, for a refusal while it compiles, a syntax error
 * and the sentence that names the failing statement of several alike, while blanks and line breaks before
 * it count. That sentence quotes the statement without the comment that may close it and carries the
 * statement's own refusal placed in the statement. Every answer is live-verified.
 */
public class DynamicTextPlacesTest extends MeasuredCellsTestSupport {

    @Test
    public void aRequestsOwnTextIsPlacedAsWritten() {
        engine.execute(
            "CREATE OR REPLACE TABLE t2 (v VARCHAR)");
        engine.execute(
            "CREATE OR REPLACE STAGE st");
        engine.execute(
            "ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 20 | invalid argument for function "
                    + "[UPPER] unexpected argument [@st] at position 0,",
                "EXECUTE IMMEDIATE $$  /* c */ SELECT 1, UPPER(@st)$$");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid argument for function "
                    + "[UPPER] unexpected argument [@st] at position 0,",
                "EXECUTE IMMEDIATE $$SELECT UPPER(@st)$$");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 10 | invalid argument for function "
                    + "[UPPER] unexpected argument [@st] at position 0,",
                "EXECUTE IMMEDIATE $$   SELECT UPPER(@st)$$");
            assertAnswer(
                "REFUSED SQL compilation error: error line 2 at position 7 | invalid argument for function "
                    + "[UPPER] unexpected argument [@st] at position 0,",
                "EXECUTE IMMEDIATE $$-- c\nSELECT UPPER(@st)$$");
            assertAnswer(
                "REFUSED SQL compilation error: error line 2 at position 7 | invalid argument for function "
                    + "[UPPER] unexpected argument [@st] at position 0,",
                "EXECUTE IMMEDIATE $$/* c */\nSELECT UPPER(@st)$$");
            assertAnswer(
                "REFUSED SQL compilation error: error line 3 at position 7 | invalid argument for function "
                    + "[UPPER] unexpected argument [@st] at position 0,",
                "EXECUTE IMMEDIATE $$\n\nSELECT UPPER(@st)$$");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 15 | invalid identifier 'NOSUCH'",
                "EXECUTE IMMEDIATE $$/* c */ SELECT nosuch$$");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 9 | invalid identifier 'NOSUCH'",
                "EXECUTE IMMEDIATE $$  SELECT nosuch$$");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 10 | invalid argument for function "
                    + "[UPPER] unexpected argument [@st] at position 0,",
                "  /* c */ SELECT 1, UPPER(@st)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid argument for function "
                    + "[UPPER] unexpected argument [@st] at position 0,",
                "/* c */ SELECT UPPER(@st)");
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
    public void theFailingStatementsPlace() {
        engine.execute(
            "CREATE OR REPLACE TABLE t2 (v VARCHAR)");
        engine.execute(
            "ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
        try {
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"SELECT "
                    + "nosuch FROM t2\" (at line 1, position 0). | SQL compilation error: error line 1 at position 7 "
                    + "| invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                    + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                    + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "BEGIN EXECUTE IMMEDIATE $$/* c */ SELECT nosuch FROM t2; SELECT 1$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"SELECT "
                    + "nosuch FROM t2\" (at line 1, position 0). | SQL compilation error: error line 1 at position 7 "
                    + "| invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                    + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                    + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "BEGIN EXECUTE IMMEDIATE $$-- c\nSELECT nosuch FROM t2; SELECT 1$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"SELECT "
                    + "nosuch FROM t2\" (at line 3, position 0). | SQL compilation error: error line 1 at position 7 "
                    + "| invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                    + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                    + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "BEGIN EXECUTE IMMEDIATE $$SELECT 1;\n/* d */\nSELECT nosuch FROM t2$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"SELECT "
                    + "nosuch FROM t2\" (at line 2, position 2). | SQL compilation error: error line 1 at position 7 "
                    + "| invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                    + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                    + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "BEGIN EXECUTE IMMEDIATE $$SELECT 1; -- d\n  SELECT nosuch FROM t2$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"SELECT "
                    + "nosuch FROM t2\" (at line 1, position 12). | SQL compilation error: error line 1 at position "
                    + "7 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                    + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                    + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "BEGIN EXECUTE IMMEDIATE $$SELECT 1;   SELECT nosuch FROM t2$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"SELECT "
                    + "nosuch FROM t2\" (at line 1, position 2). | SQL compilation error: error line 1 at position 7 "
                    + "| invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                    + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                    + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "BEGIN EXECUTE IMMEDIATE $$  SELECT nosuch FROM t2; SELECT 1$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"SELECT "
                    + "nosuch FROM t2\" (at line 1, position 26). | SQL compilation error: error line 1 at position "
                    + "7 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                    + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                    + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "BEGIN EXECUTE IMMEDIATE $$SELECT 1; /* a */ /* b */ SELECT nosuch FROM t2$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"SELECT "
                    + "nosuch FROM t2\" (at line 1, position 18). | SQL compilation error: error line 1 at position "
                    + "7 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                    + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                    + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "BEGIN EXECUTE IMMEDIATE $$SELECT 1 /* t */; SELECT nosuch FROM t2$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"SELECT "
                    + "nosuch FROM t2\" (at line 3, position 2). | SQL compilation error: error line 1 at position 7 "
                    + "| invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                    + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                    + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "BEGIN EXECUTE IMMEDIATE $$SELECT 1;\n\n  SELECT nosuch FROM t2 /* tail */; SELECT 2$$; RETURN 1; "
                    + "END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"SELECT /* in "
                    + "*/ nosuch FROM t2\" (at line 1, position 10). | SQL compilation error: error line 1 at "
                    + "position 16 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of "
                    + "multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                    + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "BEGIN EXECUTE IMMEDIATE $$SELECT 1; SELECT /* in */ nosuch FROM t2$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"SELECT "
                    + "nosuch FROM t2\" (at line 1, position 16). | SQL compilation error: error line 1 at position "
                    + "7 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                    + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                    + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "BEGIN EXECUTE IMMEDIATE $$SELECT 1;/* d */SELECT nosuch FROM t2$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"SELECT "
                    + "nosuch FROM t2\" (at line 3, position 3). | SQL compilation error: error line 1 at position 7 "
                    + "| invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                    + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                    + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "BEGIN EXECUTE IMMEDIATE $$SELECT 1; /* d */\n\n   SELECT nosuch FROM t2$$; RETURN 1; END;");
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
    public void aBlocksTextIsPlacedAsARequestIs() {
        engine.execute(
            "CREATE OR REPLACE TABLE t2 (v VARCHAR)");
        engine.execute(
            "CREATE OR REPLACE STAGE st");
        engine.execute(
            "ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
        try {
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: error line 1 at position 7 | invalid identifier 'NOSUCH'",
                "BEGIN EXECUTE IMMEDIATE $$/* c */ SELECT nosuch$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: error line 1 at position 10 | invalid argument for function [UPPER] unexpected "
                    + "argument [@st] at position 0,",
                "BEGIN EXECUTE IMMEDIATE $$  /* c */ SELECT 1, UPPER(@st)$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: error line 1 at position 7 | invalid identifier 'NOSUCH'",
                "BEGIN EXECUTE IMMEDIATE $$-- c\nSELECT nosuch$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: error line 1 at position 10 | invalid identifier 'NOSUCH'",
                "BEGIN EXECUTE IMMEDIATE $$   SELECT nosuch$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: error line 1 at position 7 | invalid argument for function [UPPER] unexpected argument "
                    + "[@st] at position 0,",
                "BEGIN EXECUTE IMMEDIATE $$/* c */\nSELECT UPPER(@st)$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: error line 1 at position 7 | invalid identifier 'NOSUCH'",
                "BEGIN EXECUTE IMMEDIATE $$SELECT nosuch$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: error line 3 at position 7 | invalid identifier 'NOSUCH'",
                "BEGIN EXECUTE IMMEDIATE $$\n\nSELECT nosuch$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: error line 1 at position 7 | invalid identifier 'NOSUCH'",
                "BEGIN EXECUTE IMMEDIATE $$/* a */ /* b */ SELECT nosuch$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: error line 2 at position 2 | invalid identifier 'NOSUCH'",
                "BEGIN EXECUTE IMMEDIATE $$/* c */ SELECT 1,\n  nosuch$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: error line 1 at position 7 | invalid identifier 'NOSUCH'",
                "BEGIN EXECUTE IMMEDIATE $$/* c */ SELECT nosuch /* t */$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 48 : SQL compilation "
                    + "error: error line 1 at position 7 | invalid identifier 'NOSUCH'",
                "BEGIN LET s VARCHAR := '/* c */ SELECT nosuch'; EXECUTE IMMEDIATE :s; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 66 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"SELECT "
                    + "nosuch FROM t2\" (at line 1, position 0). | SQL compilation error: error line 1 at position 7 "
                    + "| invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                    + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                    + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "BEGIN LET s VARCHAR := '/* c */ SELECT nosuch FROM t2; SELECT 1'; EXECUTE IMMEDIATE :s; RETURN "
                    + "1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: error line 1 at position 7 | invalid identifier 'NOSUCH'",
                "BEGIN EXECUTE IMMEDIATE '/* c */ SELECT nosuch'; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"SELECT "
                    + "nosuch FROM t2\" (at line 1, position 0). | SQL compilation error: error line 1 at position 7 "
                    + "| invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                    + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                    + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "BEGIN EXECUTE IMMEDIATE '/* c */ SELECT nosuch FROM t2; SELECT 1'; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: | Object 'NOSUCH_TABLE' does not exist or not authorized. Your primary role <ROLE> "
                    + "must have at least one privilege granted on TABLE NOSUCH_TABLE.",
                "BEGIN EXECUTE IMMEDIATE $$/* c */ SELECT 1 FROM nosuch_table$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: error line 1 at position 7 | too many arguments for function [UPPER(1, 2)] expected 1, "
                    + "got 2",
                "BEGIN EXECUTE IMMEDIATE $$/* c */ SELECT UPPER(1, 2)$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: | syntax error line 1 at position 10 unexpected '<EOF>'.",
                "BEGIN EXECUTE IMMEDIATE $$/* c */ SELECT 1 +$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: | syntax error line 1 at position 20 unexpected '<EOF>'.",
                "BEGIN EXECUTE IMMEDIATE $$/* c */ SELECT 1; SELECT 2 +$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: | syntax error line 1 at position 28 unexpected '<EOF>'.",
                "BEGIN EXECUTE IMMEDIATE $$SELECT 1; /* d */ SELECT 2 +$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: error line 1 at position 22 | invalid identifier 'NOSUCH'",
                "BEGIN EXECUTE IMMEDIATE $$/* c */ BEGIN LET x NUMBER := nosuch; RETURN x; END;$$; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 15 | invalid identifier 'NOSUCH'",
                "EXECUTE IMMEDIATE $$/* c */ SELECT nosuch$$");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '<EOF>'.",
                "EXECUTE IMMEDIATE $$/* c */ SELECT 1 +$$");
            assertAnswer(
                "Function P1300 successfully created.",
                "CREATE OR REPLACE PROCEDURE p1300() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN EXECUTE IMMEDIATE "
                    + "'/* c */ SELECT nosuch'; RETURN 1; END; $$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation "
                    + "error: error line 1 at position 7 | invalid identifier 'NOSUCH'",
                "CALL p1300()");
            assertAnswer(
                "Function P1300B successfully created.",
                "CREATE OR REPLACE PROCEDURE p1300b() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN EXECUTE IMMEDIATE "
                    + "'/* c */ SELECT nosuch FROM t2; SELECT 1'; RETURN 1; END; $$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : JavaScript "
                    + "execution error: Uncaught Execution of multiple statements failed on statement \"SELECT "
                    + "nosuch FROM t2\" (at line 1, position 0). | SQL compilation error: error line 1 at position 7 "
                    + "| invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                    + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                    + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
                "CALL p1300b()");
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
