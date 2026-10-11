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
 * In a request of several statements, a refusal raised while a later statement compiles counts its lines and
 * positions from that statement's first token - its first line is line 1 - while the sentence that names the
 * failing statement places it in the request. Every answer is live-verified.
 */
public class LaterStatementErrorPlaceTest extends MeasuredCellsTestSupport {

    @Test
    public void placesCountFromTheStatement() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute(
            "INSERT INTO t VALUES (1, 2)");
        engine.execute(
            "ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT nosuch FROM t\" (at line 1, position 10). | SQL compilation error: error line 1 at "
                + "position 7 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; SELECT nosuch FROM t");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT a FROM t WHERE nosuch =...\" (at line 2, position 0). | SQL compilation error: error "
                + "line 1 at position 22 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1;\nSELECT a FROM t WHERE nosuch = 1");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT a FROM t WHERE a = (SEL...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 34 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; SELECT a FROM t WHERE a = (SELECT nosuch FROM t)");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT nosuch\" (at line 2, position 2). | SQL compilation error: error line 1 at position 7 | "
                + "invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple statements "
                + "failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' position 4 | "
                + "stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1;\n  SELECT nosuch");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT a, |   nosuch FROM t\" (at line 2, position 2). | SQL compilation error: error line 2 at "
                + "position 2 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1;\n  SELECT a,\n  nosuch FROM t");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT nosuch FROM t\" (at line 1, position 10). | SQL compilation error: error line 1 at "
                + "position 7 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "/* c */ SELECT 1; SELECT nosuch FROM t");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT nosuch FROM t\" (at line 1, position 18). | SQL compilation error: error line 1 at "
                + "position 7 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; /* d */ SELECT nosuch FROM t");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT nosuch FROM t\" (at line 1, position 0). | SQL compilation error: error line 1 at "
                + "position 7 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT nosuch FROM t; SELECT 1");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT nosuch FROM t\" (at line 1, position 0). | SQL compilation error: error line 1 at "
                + "position 7 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "/* c */ SELECT nosuch FROM t; SELECT 1");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT nosuch FROM t\" (at line 1, position 20). | SQL compilation error: error line 1 at "
                + "position 7 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; SELECT 2; SELECT nosuch FROM t");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT 'x' + TRUE\" (at line 1, position 10). | SQL compilation error: error line 1 at position "
                + "11 | Invalid argument types for function '+': (VARCHAR(1), BOOLEAN) in SYSTEM$MULTISTMT at '    "
                + "throw `Execution of multiple statements failed on statement {0} (at line {1}, position "
                + "{2}).`.replace('{1}', LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; SELECT 'x' + TRUE");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT UPPER(1, 2)\" (at line 1, position 10). | SQL compilation error: error line 1 at "
                + "position 7 | too many arguments for function [UPPER(1, 2)] expected 1, got 2 in SYSTEM$MULTISTMT "
                + "at '    throw `Execution of multiple statements failed on statement {0} (at line {1}, position "
                + "{2}).`.replace('{1}', LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; SELECT UPPER(1, 2)");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT * FROM nosuch_table\" (at line 1, position 10). | SQL compilation error: | Object "
                + "'NOSUCH_TABLE' does not exist or not authorized. Your primary role <ROLE> must have at least one "
                + "privilege granted on TABLE NOSUCH_TABLE. in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; SELECT * FROM nosuch_table");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT 1 / 0\" (at line 1, position 10). | Division by zero in SYSTEM$MULTISTMT at '    throw "
                + "`Execution of multiple statements failed on statement {0} (at line {1}, position "
                + "{2}).`.replace('{1}', LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; SELECT 1 / 0");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"EXECUTE IMMEDIATE 'SELECT nosu...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 7 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; EXECUTE IMMEDIATE 'SELECT nosuch FROM t'");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT |   nosuch FROM t\" (at line 3, position 0). | SQL compilation error: error line 2 at "
                + "position 2 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1;\n\nSELECT\n  nosuch FROM t");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"INSERT INTO t VALUES (nosuch, ...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 22 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; INSERT INTO t VALUES (nosuch, 1)");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT a FROM t ORDER BY nosuc...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 25 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; SELECT a FROM t ORDER BY nosuch");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT a FROM t GROUP BY nosuc...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 25 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; SELECT a FROM t GROUP BY nosuch");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT nosuch FROM t\" (at line 1, position 13). | SQL compilation error: error line 1 at "
                + "position 7 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1 ;   SELECT nosuch FROM t");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT a FROM t WHERE a IN (SE...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 50 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; SELECT a FROM t WHERE a IN (SELECT b FROM t WHERE nosuch = 1)");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"UPDATE t SET a = nosuch\" (at line 1, position 10). | SQL compilation error: error line 1 at "
                + "position 17 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of "
                + "multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; UPDATE t SET a = nosuch");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"DELETE FROM t WHERE nosuch = 1\" (at line 1, position 10). | SQL compilation error: error line "
                + "1 at position 20 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of "
                + "multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; DELETE FROM t WHERE nosuch = 1");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT nosuch_fn(a) FROM t\" (at line 1, position 10). | SQL compilation error: | Unknown "
                + "function NOSUCH_FN. in SYSTEM$MULTISTMT at '    throw `Execution of multiple statements failed on "
                + "statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' position 4 | stackstrace:  "
                + "| SYSTEM$MULTISTMT line: 10",
            "SELECT 1; SELECT nosuch_fn(a) FROM t");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT a FROM t t1 JOIN t t2 O...\" (at line 1, position 10). | SQL compilation error: | "
                + "ambiguous column name 'A' in SYSTEM$MULTISTMT at '    throw `Execution of multiple statements "
                + "failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' position 4 | "
                + "stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; SELECT a FROM t t1 JOIN t t2 ON t1.a = nosuch");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT TO_NUMBER('x')\" (at line 1, position 10). | Numeric value 'x' is not recognized in "
                + "SYSTEM$MULTISTMT at '    throw `Execution of multiple statements failed on statement {0} (at line "
                + "{1}, position {2}).`.replace('{1}', LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT "
                + "line: 10",
            "SELECT 1; SELECT TO_NUMBER('x')");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT a FROM t QUALIFY TRUE\" (at line 1, position 10). | SQL compilation error: error line 1 "
                + "at position 16 | found QUALIFY clause but no window function. in SYSTEM$MULTISTMT at '    throw "
                + "`Execution of multiple statements failed on statement {0} (at line {1}, position "
                + "{2}).`.replace('{1}', LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; SELECT a FROM t QUALIFY TRUE");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT a FROM t QUALIFY TRUE\" (at line 2, position 0). | SQL compilation error: error line 1 "
                + "at position 16 | found QUALIFY clause but no window function. in SYSTEM$MULTISTMT at '    throw "
                + "`Execution of multiple statements failed on statement {0} (at line {1}, position "
                + "{2}).`.replace('{1}', LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1;\nSELECT a FROM t QUALIFY TRUE");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"WITH c AS (SELECT nosuch FROM ...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 18 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; WITH c AS (SELECT nosuch FROM t) SELECT * FROM c");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT a FROM (SELECT nosuch F...\" (at line 1, position 10). | SQL compilation error: error "
                + "line 1 at position 22 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution "
                + "of multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; SELECT a FROM (SELECT nosuch FROM t)");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"DESCRIBE TABLE nosuch_table\" (at line 1, position 10). | SQL compilation error: | Table "
                + "'NOSUCH_TABLE' does not exist or not authorized. Your primary role <ROLE> must have at least one "
                + "privilege granted on TABLE NOSUCH_TABLE. in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; DESCRIBE TABLE nosuch_table");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT $1 FROM t WHERE nosuch\" (at line 1, position 10). | SQL compilation error: error line 1 "
                + "at position 23 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of "
                + "multiple statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', "
                + "LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; SELECT $1 FROM t WHERE nosuch");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT nosuch FROM t\" (at line 1, position 27). | SQL compilation error: error line 1 at "
                + "position 7 | invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT at '    throw `Execution of multiple "
                + "statements failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' "
                + "position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10",
            "SELECT 1; SELECT a FROM t; SELECT nosuch FROM t; SELECT 2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'NOSUCH'",
            "SELECT nosuch FROM t");
    }
}
