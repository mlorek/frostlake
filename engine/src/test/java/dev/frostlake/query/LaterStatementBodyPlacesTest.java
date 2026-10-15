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
 * Where a refusal is placed in a statement that is not the request's first, or that a comment opens: a
 * string LIMIT and a view's body count from the statement's own first token and line, as its other places
 * do, while a text EXECUTE IMMEDIATE runs is placed as it is written (live-verified).
 */
public class LaterStatementBodyPlacesTest extends MeasuredCellsTestSupport {

    @Test
    public void aStringLimit() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT (SELECT a FROM t LIMIT ...\" (at line 1, position 29). | SQL compilation error: | syntax "
                + "error line 1 at position 30 unexpected ''x''. in SYSTEM$MULTISTMT at '    throw `Execution of "
                + "multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "INSERT INTO t VALUES (9, 9); SELECT (SELECT a FROM t LIMIT 'x')");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT a FROM t LIMIT 'x'\" (at line 1, position 29). | SQL compilation error: | syntax error "
                + "line 1 at position 22 unexpected ''x''. in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "INSERT INTO t VALUES (8, 8); SELECT a FROM t LIMIT 'x'");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT (SELECT a FROM t LIMIT ...\" (at line 2, position 0). | SQL compilation error: | syntax "
                + "error line 1 at position 30 unexpected ''x''. in SYSTEM$MULTISTMT at '    throw `Execution of "
                + "multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1;\nSELECT (SELECT a FROM t LIMIT 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ''x''.",
            "SELECT a FROM t LIMIT 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''.",
            "SELECT (SELECT a FROM t LIMIT 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ''x''.",
            "/* c */ SELECT a FROM t LIMIT 'x'");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT a FROM t LIMIT 'x'\" (at line 1, position 18). | SQL compilation error: | syntax error "
                + "line 1 at position 22 unexpected ''x''. in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; /* c */ SELECT a FROM t LIMIT 'x'");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT a FROM t LIMIT 'x'\" (at line 1, position 12). | SQL compilation error: | syntax error "
                + "line 1 at position 22 unexpected ''x''. in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1;   SELECT a FROM t LIMIT 'x'");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT a FROM t LIMIT 'x'\" (at line 1, position 20). | SQL compilation error: | syntax error "
                + "line 1 at position 22 unexpected ''x''. in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; SELECT 2; SELECT a FROM t LIMIT 'x'");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT a FROM t | LIMIT 'x'\" (at line 3, position 0). | SQL compilation error: | syntax error "
                + "line 2 at position 6 unexpected ''x''. in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1;\n\nSELECT a FROM t\nLIMIT 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 2 at position 6 unexpected ''x''.",
            "SELECT a FROM t\nLIMIT 'x'");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT a FROM t LIMIT 'x'\" (at line 1, position 10). | SQL compilation error: | syntax error "
                + "line 1 at position 22 unexpected ''x''. in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; SELECT a FROM t LIMIT 'x'; SELECT 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ''x''.",
            "SELECT a FROM t LIMIT 'x' OFFSET 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ''x''.",
            "SELECT a FROM t LIMIT 1 OFFSET 'x'");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT a FROM t LIMIT 1 OFFSET...\" (at line 1, position 10). | SQL compilation error: | syntax "
                + "error line 1 at position 31 unexpected ''x''. in SYSTEM$MULTISTMT at '    throw `Execution of "
                + "multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; SELECT a FROM t LIMIT 1 OFFSET 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ''x''.",
            "SELECT a FROM t FETCH FIRST 'x' ROWS ONLY");
    }

    @Test
    public void aViewsBody() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"CREATE OR REPLACE VIEW vx AS S...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 36 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; CREATE OR REPLACE VIEW vx AS SELECT nosuch FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 36 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE VIEW vx AS SELECT nosuch FROM t");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"CREATE OR REPLACE VIEW vx AS | S...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 2 at position 7 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; CREATE OR REPLACE VIEW vx AS\nSELECT nosuch FROM t");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"CREATE OR REPLACE VIEW vx AS S...\" (at line 2, position 0). | SQL compilation error: error "
                + "line 1 at position 36 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1;\nCREATE OR REPLACE VIEW vx AS SELECT nosuch FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 36 | invalid identifier 'NOSUCH'",
            "/* c */ CREATE OR REPLACE VIEW vx AS SELECT nosuch FROM t");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"CREATE OR REPLACE VIEW vx AS S...\" (at line 1, position 18). | SQL compilation error: error "
                + "line 1 at position 36 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; /* c */ CREATE OR REPLACE VIEW vx AS SELECT nosuch FROM t");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"CREATE OR REPLACE VIEW vx (c) ...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 40 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; CREATE OR REPLACE VIEW vx (c) AS SELECT nosuch FROM t");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"CREATE OR REPLACE VIEW vx AS S...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 39 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; CREATE OR REPLACE VIEW vx AS SELECT a, nosuch FROM t WHERE nosuch2 = 1");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"CREATE OR REPLACE VIEW vx AS S...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 51 | invalid identifier 'NOSUCH2' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; CREATE OR REPLACE VIEW vx AS SELECT a FROM t WHERE nosuch2 = 1");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"CREATE OR REPLACE TABLE tx AS ...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 37 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; CREATE OR REPLACE TABLE tx AS SELECT nosuch FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 37 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE TABLE tx AS SELECT nosuch FROM t");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"CREATE OR REPLACE MATERIALIZED...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 49 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; CREATE OR REPLACE MATERIALIZED VIEW mx AS SELECT nosuch FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 49 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE MATERIALIZED VIEW mx AS SELECT nosuch FROM t");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"CREATE OR REPLACE FUNCTION fx(...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 8 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; CREATE OR REPLACE FUNCTION fx() RETURNS INT AS 'SELECT nosuch FROM t'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION fx() RETURNS INT AS 'SELECT nosuch FROM t'");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT nosuch FROM t\" (at line 1, position 10). | SQL compilation error: error line 1 at "
                + "position 7 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; SELECT nosuch FROM t");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"INSERT INTO t SELECT nosuch, 1...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 21 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; INSERT INTO t SELECT nosuch, 1 FROM t");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT a FROM t WHERE nosuch =...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 22 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; SELECT a FROM t WHERE nosuch = 1");
        assertAnswer(
            "Drop statement executed successfully (VX already dropped).",
            "DROP VIEW IF EXISTS vx");
        assertAnswer(
            "Drop statement executed successfully (TX already dropped).",
            "DROP TABLE IF EXISTS tx");
    }

    @Test
    public void aViewsBodyMore() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 51 | invalid identifier 'NOSUCH'",
            "/* c */ CREATE OR REPLACE VIEW vx AS SELECT a FROM t WHERE nosuch = 1");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"CREATE OR REPLACE VIEW vx AS S...\" (at line 1, position 10). | SQL compilation error: | syntax "
                + "error line 1 at position 51 unexpected ''x''. in SYSTEM$MULTISTMT at '    throw `Execution of "
                + "multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; CREATE OR REPLACE VIEW vx AS SELECT a FROM t LIMIT 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected ''x''.",
            "/* c */ CREATE OR REPLACE VIEW vx AS SELECT a FROM t LIMIT 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected ''x''.",
            "CREATE OR REPLACE VIEW vx AS SELECT a FROM t LIMIT 'x'");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"CREATE OR REPLACE VIEW vx AS S...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 42 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; CREATE OR REPLACE VIEW vx AS SELECT UPPER(nosuch) FROM t");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"CREATE OR REPLACE VIEW vx AS (...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 37 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; CREATE OR REPLACE VIEW vx AS (SELECT nosuch FROM t)");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"CREATE OR REPLACE VIEW vx AS W...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 47 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; CREATE OR REPLACE VIEW vx AS WITH c AS (SELECT nosuch FROM t) SELECT * FROM c");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"CREATE OR REPLACE SECURE VIEW ...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 43 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; CREATE OR REPLACE SECURE VIEW vx AS SELECT nosuch FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 49 | invalid identifier 'NOSUCH'",
            "/* c */ CREATE OR REPLACE MATERIALIZED VIEW mx AS SELECT nosuch FROM t");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"CREATE OR REPLACE VIEW vx | AS | S...\" (at line 2, position 0). | SQL compilation error: error "
                + "line 3 at position 7 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1;\nCREATE OR REPLACE VIEW vx\nAS\nSELECT nosuch FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 2 at position 7 | invalid identifier 'NOSUCH'",
            "/* c */\nCREATE OR REPLACE VIEW vx AS\nSELECT nosuch FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 37 | invalid identifier 'NOSUCH'",
            "/* c */ CREATE OR REPLACE TABLE tx AS SELECT nosuch FROM t");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"CREATE OR REPLACE TABLE tx AS ...\" (at line 1, position 18). | SQL compilation error: error "
                + "line 1 at position 37 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; /* c */ CREATE OR REPLACE TABLE tx AS SELECT nosuch FROM t");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"CREATE OR REPLACE VIEW vx AS S...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 36 | too many arguments for function [SUM(T.A, T.B)] expected 1, got 2 in "
                + "SYSTEM$MULTISTMT at '    throw `Execution of multiple statements failed on statement {0} (at line "
                + "{1}, position {2}).`.replace('{1}', LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT "
                + "line: 10",
            "SELECT 1; CREATE OR REPLACE VIEW vx AS SELECT SUM(a, b) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 36 | too many arguments for function "
                + "[SUM(T.A, T.B)] expected 1, got 2",
            "/* c */ CREATE OR REPLACE VIEW vx AS SELECT SUM(a, b) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 36 | too many arguments for function "
                + "[SUM(T.A, T.B)] expected 1, got 2",
            "CREATE OR REPLACE VIEW vx AS SELECT SUM(a, b) FROM t");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"CREATE OR REPLACE VIEW vx AS S...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 54 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; CREATE OR REPLACE VIEW vx AS SELECT a FROM t ORDER BY nosuch");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"CREATE OR REPLACE VIEW vx AS S...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 63 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; CREATE OR REPLACE VIEW vx AS SELECT a FROM t WHERE a = (SELECT nosuch FROM t)");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"CREATE VIEW IF NOT EXISTS vx A...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 39 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; CREATE VIEW IF NOT EXISTS vx AS SELECT nosuch FROM t");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"CREATE OR REPLACE VIEW vx AS S...\" (at line 1, position 20). | SQL compilation error: error "
                + "line 1 at position 36 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; SELECT 2; CREATE OR REPLACE VIEW vx AS SELECT nosuch FROM t");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                + "error: error line 1 at position 36 | invalid identifier 'NOSUCH'",
            "BEGIN CREATE OR REPLACE VIEW vx AS SELECT nosuch FROM t; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                + "error: error line 1 at position 36 | invalid identifier 'NOSUCH'",
            "/* c */ BEGIN CREATE OR REPLACE VIEW vx AS SELECT nosuch FROM t; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 36 | invalid identifier 'NOSUCH'",
            "EXECUTE IMMEDIATE 'CREATE OR REPLACE VIEW vx AS SELECT nosuch FROM t'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 44 | invalid identifier 'NOSUCH'",
            "EXECUTE IMMEDIATE '/* c */ CREATE OR REPLACE VIEW vx AS SELECT nosuch FROM t'");
        assertAnswer(
            "Drop statement executed successfully (VX already dropped).",
            "DROP VIEW IF EXISTS vx");
        assertAnswer(
            "Drop statement executed successfully (TX already dropped).",
            "DROP TABLE IF EXISTS tx");
    }
}
