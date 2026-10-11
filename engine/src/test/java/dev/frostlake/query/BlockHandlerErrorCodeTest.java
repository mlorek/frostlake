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
 * What a block's handler reads for a refusal the engine raised. A statement that fails is a STATEMENT_ERROR -
 * a syntax error in the text EXECUTE IMMEDIATE runs included - and a scripting statement's own expression an
 * EXPRESSION_ERROR; an exception the script raised is caught by its own name alone. SQLCODE and SQLSTATE read
 * the refusal's family: 1003 / 42000 for a syntax error, 904 / 42000 for an invalid identifier, 2003 / 42S02
 * for a missing object, 100038 / 22018 for a text that is no number, and so on. Every answer is
 * live-verified.
 */
public class BlockHandlerErrorCodeTest extends MeasuredCellsTestSupport {

    @Test
    public void theKindAndTheCode() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "statement 1003 42000",
            "BEGIN EXECUTE IMMEDIATE 'SELECT a a a FROM t'; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN "
                + "RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression "
                + "' || SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; "
                + "END");
        assertAnswer(
            "statement 1003 42000",
            "BEGIN EXECUTE IMMEDIATE 'SELECT (SELECT a FROM t LIMIT ''x'')'; RETURN 'none'; EXCEPTION WHEN "
                + "STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR "
                + "THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || "
                + "SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 1003 42000",
            "BEGIN CREATE OR REPLACE PROCEDURE pq() RETURNS INT LANGUAGE SQL AS BEGIN RETURN (SELECT a FROM t "
                + "LIMIT 'x'); END; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || "
                + "SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 1003 42000",
            "BEGIN CREATE OR REPLACE PROCEDURE pq2() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN (SELECT a FROM t "
                + "LIMIT 'x'); END $$; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || "
                + "SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 90203 42601",
            "BEGIN CREATE OR REPLACE MASKING POLICY mpq AS (val INT) RETURNS INT -> (SELECT a FROM t LIMIT 'x'); "
                + "RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || "
                + "SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE; WHEN "
                + "OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 90203 42601",
            "BEGIN CREATE OR REPLACE FUNCTION fq() RETURNS INT AS 'SELECT a FROM t LIMIT ''x'''; RETURN 'none'; "
                + "EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN "
                + "EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN "
                + "'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 904 42000",
            "BEGIN SELECT nosuch FROM t; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' "
                + "|| SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' "
                + "|| SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 2003 42S02",
            "BEGIN SELECT * FROM nosuch_table; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN "
                + "'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 100051 22012",
            "BEGIN SELECT 1/0; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE "
                + "|| ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || "
                + "SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 100038 22018",
            "BEGIN SELECT 'a'::INT; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || "
                + "SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 904 42000",
            "BEGIN EXECUTE IMMEDIATE 'SELECT nosuch FROM t'; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN "
                + "RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression "
                + "' || SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; "
                + "END");
        assertAnswer(
            "statement 1003 42000",
            "BEGIN EXECUTE IMMEDIATE 'nonsense'; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN "
                + "'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 100051 22012",
            "BEGIN EXECUTE IMMEDIATE 'SELECT 1/0'; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN "
                + "'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 100038 22018",
            "BEGIN INSERT INTO t VALUES ('x', 1); RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN "
                + "'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 2139 02000",
            "BEGIN CALL nosuch_proc(); RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || "
                + "SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 2139 02000",
            "BEGIN SELECT nosuchfn(1); RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || "
                + "SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 2002 42710",
            "BEGIN CREATE TABLE t (a INT); RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' "
                + "|| SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' "
                + "|| SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 2003 42S02",
            "BEGIN DROP TABLE nosuch_t; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || "
                + "SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ''x''.",
            "BEGIN SELECT a FROM t LIMIT 'x'; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN "
                + "'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "expression 100038 22018",
            "BEGIN LET x INT := 'abc'; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || "
                + "SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 939 22023",
            "BEGIN SELECT UPPER(1, 2); RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || "
                + "SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 904 42000",
            "BEGIN CREATE OR REPLACE VIEW vq AS SELECT nosuch FROM t; RETURN 'none'; EXCEPTION WHEN "
                + "STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR "
                + "THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || "
                + "SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 904 42000",
            "BEGIN CREATE OR REPLACE FUNCTION fq2() RETURNS INT AS 'nosuch + 1'; RETURN 'none'; EXCEPTION WHEN "
                + "STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR "
                + "THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || "
                + "SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 904 42000",
            "BEGIN CREATE OR REPLACE FUNCTION fq3() RETURNS INT AS 'SELECT nosuch FROM t'; RETURN 'none'; "
                + "EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN "
                + "EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN "
                + "'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 100040 22007",
            "BEGIN SELECT TO_DATE('x'); RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || "
                + "SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 2003 42S02",
            "BEGIN DELETE FROM nosuch_t; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' "
                + "|| SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' "
                + "|| SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 1430 42601",
            "BEGIN ALTER TABLE t ADD COLUMN a INT; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN "
                + "'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 2043 02000",
            "BEGIN USE SCHEMA nosuch_schema; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement "
                + "' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' "
                + "' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "expression 90150 22000",
            "BEGIN LET y INT := (SELECT a FROM t); RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN "
                + "'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 90150 22000",
            "BEGIN SELECT (SELECT a FROM t) FROM t; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN "
                + "'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "expression 100051 22012",
            "BEGIN LET z INT := 1/0; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || "
                + "SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 15 | invalid identifier 'NOSUCH'",
            "BEGIN LET w := nosuch + 1; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || "
                + "SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 1003 42000",
            "BEGIN EXECUTE IMMEDIATE 'SELECT a FROM t LIMIT ''x'''; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR "
                + "THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN "
                + "'expression ' || SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || "
                + "SQLSTATE; END");
        assertAnswer(
            "statement 1003 42000",
            "BEGIN EXECUTE IMMEDIATE 'SELECT 1 +'; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN "
                + "'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 2002 42710",
            "BEGIN EXECUTE IMMEDIATE 'CREATE TABLE t (a INT)'; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN "
                + "RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression "
                + "' || SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; "
                + "END");
        assertAnswer(
            "statement 100038 22018",
            "BEGIN EXECUTE IMMEDIATE 'SELECT ''a''::INT'; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN "
                + "RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression "
                + "' || SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; "
                + "END");
        assertAnswer(
            "statement 1003 42000",
            "BEGIN EXECUTE IMMEDIATE 'BEGIN SELECT a a a FROM t; END'; RETURN 'none'; EXCEPTION WHEN "
                + "STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR "
                + "THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || "
                + "SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 2003 42S02",
            "BEGIN EXECUTE IMMEDIATE 'SELECT * FROM nosuch_table'; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR "
                + "THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN "
                + "'expression ' || SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || "
                + "SQLSTATE; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected ''x''.",
            "BEGIN CREATE OR REPLACE VIEW vq2 AS SELECT a FROM t LIMIT 'x'; RETURN 'none'; EXCEPTION WHEN "
                + "STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR "
                + "THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || "
                + "SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected ''x''.",
            "BEGIN CREATE OR REPLACE TABLE tq AS SELECT a FROM t LIMIT 'x'; RETURN 'none'; EXCEPTION WHEN "
                + "STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR "
                + "THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || "
                + "SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 1003 42000",
            "BEGIN CREATE OR REPLACE PROCEDURE pq3() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN (SELECT a a a FROM "
                + "t); END'; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' "
                + "' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || SQLSTATE; "
                + "WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 2139 02000",
            "BEGIN CALL pq3(); RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE "
                + "|| ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || "
                + "SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 100038 22018",
            "BEGIN SELECT a FROM t WHERE b = 'x'; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN "
                + "'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 1104 42601",
            "BEGIN SELECT a FROM t GROUP BY b; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN "
                + "'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 2003 42S02",
            "BEGIN TRUNCATE TABLE nosuch_t; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement "
                + "' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' "
                + "' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "statement 938 22023",
            "BEGIN SELECT SYSTEM$TYPEOF(); RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' "
                + "|| SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' "
                + "|| SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 12 | invalid identifier 'OTHER'",
            "BEGIN RAISE OTHER; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE "
                + "|| ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || "
                + "SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
    }

    @Test
    public void theMessage() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "1003 42000 SQL compilation error: | syntax error line 1 at position 11 unexpected 'a'.",
            "BEGIN EXECUTE IMMEDIATE 'SELECT a a a FROM t'; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN "
                + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "1003 42000 SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''.",
            "BEGIN EXECUTE IMMEDIATE 'SELECT (SELECT a FROM t LIMIT ''x'')'; RETURN 'none'; EXCEPTION WHEN OTHER "
                + "THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "1003 42000 SQL compilation error: | syntax error line 1 at position 36 unexpected ''x''.",
            "BEGIN CREATE OR REPLACE PROCEDURE pq() RETURNS INT LANGUAGE SQL AS BEGIN RETURN (SELECT a FROM t "
                + "LIMIT 'x'); END; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' "
                + "' || SQLERRM; END");
        assertAnswer(
            "1003 42000 SQL compilation error: | syntax error line 1 at position 37 unexpected ''x''.",
            "BEGIN CREATE OR REPLACE PROCEDURE pq2() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN (SELECT a FROM t "
                + "LIMIT 'x'); END $$; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || "
                + "' ' || SQLERRM; END");
        assertAnswer(
            "90203 42601 Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "24 unexpected ''x''.",
            "BEGIN CREATE OR REPLACE MASKING POLICY mpq AS (val INT) RETURNS INT -> (SELECT a FROM t LIMIT 'x'); "
                + "RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "90203 42601 Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "23 unexpected ''x''.",
            "BEGIN CREATE OR REPLACE FUNCTION fq() RETURNS INT AS 'SELECT a FROM t LIMIT ''x'''; RETURN 'none'; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "904 42000 SQL compilation error: error line 1 at position 7 | invalid identifier 'NOSUCH'",
            "BEGIN SELECT nosuch FROM t; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "100051 22012 Division by zero",
            "BEGIN SELECT 1/0; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' "
                + "|| SQLERRM; END");
        assertAnswer(
            "100038 22018 Numeric value 'a' is not recognized",
            "BEGIN SELECT 'a'::INT; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || "
                + "' ' || SQLERRM; END");
        assertAnswer(
            "904 42000 SQL compilation error: error line 1 at position 7 | invalid identifier 'NOSUCH'",
            "BEGIN EXECUTE IMMEDIATE 'SELECT nosuch FROM t'; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN "
                + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "1003 42000 SQL compilation error: | syntax error line 1 at position 0 unexpected 'nonsense'.",
            "BEGIN EXECUTE IMMEDIATE 'nonsense'; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' "
                + "|| SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "100051 22012 Division by zero",
            "BEGIN EXECUTE IMMEDIATE 'SELECT 1/0'; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' "
                + "|| SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "100038 22018 DML operation to table T failed on column A with error: Numeric value 'x' is not "
                + "recognized",
            "BEGIN INSERT INTO t VALUES ('x', 1); RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' "
                + "|| SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "2139 02000 SQL compilation error: | Unknown function NOSUCH_PROC.",
            "BEGIN CALL nosuch_proc(); RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE "
                + "|| ' ' || SQLERRM; END");
        assertAnswer(
            "2139 02000 SQL compilation error: | Unknown function NOSUCHFN.",
            "BEGIN SELECT nosuchfn(1); RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE "
                + "|| ' ' || SQLERRM; END");
        assertAnswer(
            "2002 42710 SQL compilation error: | Object 'T' already exists.",
            "BEGIN CREATE TABLE t (a INT); RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ''x''.",
            "BEGIN SELECT a FROM t LIMIT 'x'; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "100038 22018 Numeric value 'abc' is not recognized",
            "BEGIN LET x INT := 'abc'; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE "
                + "|| ' ' || SQLERRM; END");
        assertAnswer(
            "939 22023 SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[UPPER(1, 2)] expected 1, got 2",
            "BEGIN SELECT UPPER(1, 2); RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE "
                + "|| ' ' || SQLERRM; END");
        assertAnswer(
            "904 42000 SQL compilation error: error line 1 at position 8 | invalid identifier 'NOSUCH'",
            "BEGIN CREATE OR REPLACE FUNCTION fq3() RETURNS INT AS 'SELECT nosuch FROM t'; RETURN 'none'; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "100040 22007 Date 'x' is not recognized",
            "BEGIN SELECT TO_DATE('x'); RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "1430 42601 SQL compilation error: | column 'A' already exists",
            "BEGIN ALTER TABLE t ADD COLUMN a INT; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' "
                + "|| SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "2043 02000 SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "BEGIN USE SCHEMA nosuch_schema; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "90150 22000 Single-row subquery returns more than one row.",
            "BEGIN LET y INT := (SELECT a FROM t); RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' "
                + "|| SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "90150 22000 Single-row subquery returns more than one row.",
            "BEGIN SELECT (SELECT a FROM t) FROM t; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' "
                + "' || SQLSTATE || ' ' || SQLERRM; END");
    }

    @Test
    public void whichHandlerCatches() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'a'. | syntax error "
                + "line 1 at position 19 unexpected 'FROM'.",
            "BEGIN SELECT a a a FROM t; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN 'other ' || SQLCODE; END");
        assertAnswer(
            "100051",
            "BEGIN SELECT 1/0; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN SQLCODE; END");
        assertAnswer(
            "22012",
            "BEGIN SELECT 1/0; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN SQLSTATE; END");
        assertAnswer(
            "Division by zero",
            "BEGIN SELECT 1/0; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN SQLERRM; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Division by zero",
            "BEGIN SELECT 1/0; RETURN 'none'; EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN 'expression'; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 19 : Division by zero",
            "BEGIN LET x INT := 1/0; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement'; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                + "error: | syntax error line 1 at position 11 unexpected 'a'.",
            "BEGIN EXECUTE IMMEDIATE 'SELECT a a a FROM t'; RETURN 'none'; EXCEPTION WHEN EXPRESSION_ERROR THEN "
                + "RETURN 'expression'; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                + "error: | syntax error line 1 at position 11 unexpected 'a'.",
            "BEGIN EXECUTE IMMEDIATE 'SELECT a a a FROM t'; RETURN 'none'; END");
        assertAnswer(
            "mine -20001 P0001 mine",
            "DECLARE e EXCEPTION (-20001, 'mine'); BEGIN RAISE e; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN "
                + "'statement'; WHEN e THEN RETURN 'mine ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "null",
            "DECLARE e EXCEPTION; BEGIN RAISE e; EXCEPTION WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "statement 1003",
            "BEGIN BEGIN EXECUTE IMMEDIATE 'SELECT a a a FROM t'; END; RETURN 'none'; EXCEPTION WHEN "
                + "STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE; WHEN OTHER THEN RETURN 'other ' || SQLCODE; "
                + "END");
        assertAnswer(
            "outer 100051",
            "BEGIN BEGIN SELECT 1/0; EXCEPTION WHEN EXPRESSION_ERROR THEN RETURN 'inner'; END; RETURN 'none'; "
                + "EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'outer ' || SQLCODE; END");
    }
}
