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
 * The constant parts of the expressions EXPLAIN writes, folded as the account folds them while the statement compiles
 * (live-verified): arithmetic and concatenation over constants, a deterministic built-in function over constants, the
 * day CURRENT_DATE() reads, a SQL function over constants (its body an expression or a query, secure or not, called
 * by name or by IDENTIFIER with named arguments), and a FROM-less scalar subquery of a constant. A number keeps its
 * scale, a text and a date are quoted. A SQL function's call over a column is written as its body, the column in its
 * parameter's place; a function that answers anew each time is not folded. A procedure's token called with arguments
 * its procedure does not take is refused as the query refuses it, at the call.
 */
public class ExplainConstantFoldTest extends MeasuredCellsTestSupport {

    @Test
    public void aPlanFoldsWhatTheStatementComputesFromConstants() {
        engine.execute("CREATE OR REPLACE PROCEDURE pr49(x NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN "
            + "RETURN x + 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fn49(x NUMBER) RETURNS NUMBER AS 'x + 1'");
        engine.execute("CREATE OR REPLACE FUNCTION fnq49(x NUMBER) RETURNS NUMBER AS 'SELECT x + 1'");
        engine.execute("CREATE OR REPLACE SECURE FUNCTION fns49(x NUMBER) RETURNS NUMBER AS 'x + 1'");
        engine.execute("CREATE OR REPLACE FUNCTION fnt49(x VARCHAR) RETURNS VARCHAR AS 'x || ''b'''");
        engine.execute("CREATE OR REPLACE FUNCTION fnr49(x NUMBER) RETURNS NUMBER AS 'x + UNIFORM(1, 10, "
            + "RANDOM())'");
        engine.execute("CREATE OR REPLACE TABLE e49 (n INT)");
        engine.execute("INSERT INTO e49 VALUES (1), (2)");
        engine.execute("SET atok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr49(NUMBER)', 'SESSION', 'USAGE'))");
        engine.execute("SET ftok = (SELECT SYSTEM$REFERENCE('FUNCTION', 'fn49(NUMBER)', 'SESSION', 'USAGE'))");
        assertAnswer("GlobalStats|null ;; Result|2 ;; Generator|1",
            "EXPLAIN SELECT 1 + 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|5 ;; Generator|1",
            "EXPLAIN SELECT ABS(-5) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'A' ;; Generator|1",
            "EXPLAIN SELECT UPPER('a') ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'ab' ;; Generator|1",
            "EXPLAIN SELECT 'a' || 'b' ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|3.0 ;; Generator|1",
            "EXPLAIN SELECT 1.5 * 2 ->> SELECT \"operation\", \"expressions\" FROM $1");
        // The day the statement runs on.
        final String today = answer("SELECT TO_VARCHAR(CURRENT_DATE(), 'YYYY-MM-DD')");
        assertAnswer("GlobalStats|null ;; Result|'" + today + "' ;; Generator|1",
            "EXPLAIN SELECT CURRENT_DATE() ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|5 ;; Generator|1",
            "EXPLAIN SELECT fn49(4) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|5 ;; Generator|1",
            "EXPLAIN SELECT fnq49(4) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|5 ;; Generator|1",
            "EXPLAIN SELECT fns49(4) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'ab' ;; Generator|1",
            "EXPLAIN SELECT fnt49('a') ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'a' ;; Generator|1",
            "EXPLAIN SELECT (SELECT 'a') ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|2 ;; Generator|1",
            "EXPLAIN SELECT (SELECT 1 + 1) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|E49.N + 1 ;; TableScan|N",
            "EXPLAIN SELECT fn49(n) FROM e49 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|5 + E49.N ;; TableScan|N",
            "EXPLAIN SELECT fn49(4) + n FROM e49 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : SQL "
            + "compilation error: error line 1 at position 15 | too many arguments for function [PR49(E49.N, "
            + "E49.N)] expected 1, got 2",
            "EXPLAIN SELECT IDENTIFIER($atok)(n, n) FROM e49 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : SQL "
            + "compilation error: error line 1 at position 15 | Invalid argument types for function 'PR49': ()",
            "EXPLAIN SELECT IDENTIFIER($atok)() FROM e49 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for "
            + "function [PR49(E49.N, E49.N)] expected 1, got 2",
            "SELECT IDENTIFIER($atok)(n, n) FROM e49");
        assertAnswer("5",
            "SELECT IDENTIFIER($ftok)(x => 4)");
        assertAnswer("5",
            "SELECT IDENTIFIER('fn49')(x => 4)");
        assertAnswer("6",
            "SELECT IDENTIFIER($ftok)(x => 4) + 1");
        assertAnswer("GlobalStats|null ;; Result|5 ;; Generator|1",
            "EXPLAIN SELECT IDENTIFIER('fn49')(x => 4) ->> SELECT \"operation\", \"expressions\" FROM $1");
        // The procedure is named by the token itself.
        final String token = answer("SELECT $atok");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | named arguments [Y] do not match any "
            + "signature for function " + token,
            "SELECT IDENTIFIER($atok)(y => 4)");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : SQL "
            + "compilation error: error line 1 at position 15 | named arguments [Y] do not match any signature for "
            + "function " + token,
            "EXPLAIN SELECT IDENTIFIER($atok)(y => 4) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("1|2 ;; 2|3",
            "SELECT n, IDENTIFIER($ftok)(x => n) FROM e49 ORDER BY 1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|1",
            "EXPLAIN SELECT 1 WHERE 2 > 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|E49.N ;; Filter|2 = E49.N ;; TableScan|N",
            "EXPLAIN SELECT n FROM e49 WHERE 1 + 1 = n ->> SELECT \"operation\", \"expressions\" FROM $1");
    }
}
