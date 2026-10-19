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
 * What a query may reach. TO_QUERY compiles one query or one CALL: any other statement is no valid FROM item,
 * a second statement after a semicolon a syntax error at its first word, a flow of statements an unsupported
 * feature, and its text and every bind are strings. A stored procedure called in a FROM clause runs its
 * statements as child jobs of the query: one that reads runs - a SELECT, a SHOW, a DESCRIBE, a CALL of a
 * procedure that reads, NEXTVAL - and one that writes is refused where it stands in the body, by its type and,
 * for a DML, a CREATE and a DROP, its object; the text an EXECUTE IMMEDIATE runs and a procedure the body calls
 * are child jobs too. An owner's-rights procedure may not SET or UNSET a session variable, a caller's-rights one
 * may. An EXPLAIN carries nothing out. Every answer is live-verified.
 */
public class ToQueryAndFromProceduresTest extends MeasuredCellsTestSupport {

    @Test
    public void whatToQueryCompilesAndAPlanCarriesOut() {
        engine.execute(
            "CREATE OR REPLACE TABLE tq (a INT)");
        engine.execute(
            "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
        engine.execute(
            "CREATE OR REPLACE SEQUENCE sq");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_one() RETURNS INT LANGUAGE SQL AS $$ BEGIN DELETE FROM tq; RETURN 1; "
                + "END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_tab() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN DELETE FROM tq WHERE a = 3; RETURN TABLE(r); END $$");
        try {
            answer(
                "SELECT * FROM TABLE(TO_QUERY('SELECT a FROM tq ORDER BY a'))");
            answer(
                "SELECT * FROM TABLE(TO_QUERY(SQL => 'SELECT a FROM tq ORDER BY a'))");
            assertAnswer(
                "1",
                "SELECT * FROM TABLE(TO_QUERY('WITH c AS (SELECT 1 AS x) SELECT x FROM c'))");
            assertAnswer(
                "1",
                "SELECT * FROM TABLE(TO_QUERY('(SELECT 1 AS x)'))");
            assertAnswer(
                "1",
                "SELECT * FROM TABLE(TO_QUERY('SELECT 1 AS x;'))");
            answer(
                "SELECT * FROM TABLE(TO_QUERY('SELECT 1 AS x UNION ALL SELECT 2 ORDER BY 1'))");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'VALUES'.",
                "SELECT * FROM TABLE(TO_QUERY('VALUES (1), (2)'))");
            assertAnswer(
                "1",
                "SELECT * FROM TABLE(TO_QUERY('SELECT 1 AS x -- c'))");
            assertAnswer(
                "1",
                "SELECT * FROM TABLE(TO_QUERY('  SELECT 1 AS x  '))");
            assertAnswer(
                "REFUSED SQL compilation error: | [1] is not a valid FROM item",
                "SELECT * FROM TABLE(TO_QUERY('DELETE FROM tq'))");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tq");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "REFUSED SQL compilation error: | [1] is not a valid FROM item",
                "SELECT * FROM TABLE(TO_QUERY(SQL => 'TRUNCATE TABLE tq'))");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tq");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "REFUSED SQL compilation error: | [1] is not a valid FROM item",
                "SELECT * FROM TABLE(\"TO_QUERY\"('DELETE FROM tq'))");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tq");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "REFUSED SQL compilation error: | [1] is not a valid FROM item",
                "SELECT * FROM TABLE(TO_QUERY('INSERT INTO tq VALUES (9)'))");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tq");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "REFUSED SQL compilation error: | [1] is not a valid FROM item",
                "SELECT * FROM TABLE(TO_QUERY('UPDATE tq SET a = 0'))");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tq");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "REFUSED SQL compilation error: | [1] is not a valid FROM item",
                "SELECT * FROM TABLE(TO_QUERY('MERGE INTO tq USING (SELECT 1 AS a) s ON tq.a = s.a WHEN MATCHED "
                    + "THEN DELETE'))");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tq");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "1",
                "SELECT * FROM TABLE(TO_QUERY('CALL p_one()'))");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tq");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'DELETE'.",
                "SELECT * FROM TABLE(TO_QUERY('SELECT 1; DELETE FROM tq'))");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tq");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "REFUSED Unsupported feature 'SELECT 1'.",
                "SELECT * FROM TABLE(TO_QUERY('SELECT 1 ->> DELETE FROM tq'))");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tq");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "REFUSED Unsupported feature 'SELECT 1'.",
                "SELECT * FROM TABLE(TO_QUERY('SELECT 1 ->> SELECT 2'))");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tq");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "REFUSED SQL compilation error: | [1] is not a valid FROM item",
                "SELECT * FROM TABLE(TO_QUERY('BEGIN DELETE FROM tq; RETURN 1; END'))");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tq");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "REFUSED SQL compilation error: | [1] is not a valid FROM item",
                "SELECT * FROM TABLE(TO_QUERY('EXECUTE IMMEDIATE ''DELETE FROM tq'''))");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tq");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'SELECT'.",
                "SELECT * FROM TABLE(TO_QUERY('SELECT 1; SELECT 2'))");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tq");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "REFUSED SQL compilation error: | [1] is not a valid FROM item",
                "SELECT * FROM TABLE(TO_QUERY('CREATE TABLE made (a INTEGER)'))");
            assertAnswer(
                "",
                "SHOW TABLES LIKE 'MADE'");
            assertAnswer(
                "Drop statement executed successfully (MADE already dropped).",
                "DROP TABLE IF EXISTS made");
            assertAnswer(
                "REFUSED SQL compilation error: | [1] is not a valid FROM item",
                "SELECT * FROM TABLE(TO_QUERY('CREATE OR REPLACE TEMPORARY TABLE made2 AS SELECT 1 AS a'))");
            assertAnswer(
                "",
                "SHOW TABLES LIKE 'MADE2'");
            assertAnswer(
                "Drop statement executed successfully (MADE2 already dropped).",
                "DROP TABLE IF EXISTS made2");
            assertAnswer(
                "REFUSED SQL compilation error: | [1] is not a valid FROM item",
                "SELECT * FROM TABLE(TO_QUERY('SHOW TABLES'))");
            assertAnswer(
                "REFUSED SQL compilation error: | [1] is not a valid FROM item",
                "SELECT * FROM TABLE(TO_QUERY('DESCRIBE TABLE tq'))");
            assertAnswer(
                "1",
                "SELECT * FROM TABLE(TO_QUERY('EXPLAIN SELECT 1'))");
            assertAnswer(
                "REFUSED SQL compilation error: | [1] is not a valid FROM item",
                "SELECT * FROM TABLE(TO_QUERY('SET tqv = 1'))");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Session variable '$TQV' does not "
                    + "exist",
                "SELECT $tqv");
            assertAnswer(
                "REFUSED SQL compilation error: | [1] is not a valid FROM item",
                "SELECT * FROM TABLE(TO_QUERY('USE SCHEMA test_schema'))");
            assertAnswer(
                "1",
                "SELECT * FROM TABLE(TO_QUERY('SELECT sq.NEXTVAL'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Empty SQL statement.",
                "SELECT * FROM TABLE(TO_QUERY(''))");
            assertAnswer(
                "REFUSED SQL compilation error: | Empty SQL statement.",
                "SELECT * FROM TABLE(TO_QUERY('   '))");
            assertAnswer(
                "REFUSED SQL compilation error: | Empty SQL statement.",
                "SELECT * FROM TABLE(TO_QUERY(';'))");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 20 | missing required argument [SQL] for "
                    + "function [TO_QUERY]",
                "SELECT * FROM TABLE(TO_QUERY(NULL))");
            assertAnswer(
                "REFUSED SQL compilation error: | argument needs to be a string: '0'",
                "SELECT * FROM TABLE(TO_QUERY(1))");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 20 | not enough arguments for function "
                    + "[TO_QUERY], expected 1, got 0",
                "SELECT * FROM TABLE(TO_QUERY())");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'SELEC'.",
                "SELECT * FROM TABLE(TO_QUERY('SELEC 1'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'NOPE_TQ' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE NOPE_TQ.",
                "SELECT * FROM TABLE(TO_QUERY('SELECT * FROM nope_tq'))");
            assertAnswer(
                "2",
                "SELECT * FROM TABLE(TO_QUERY('SELECT * FROM TABLE(TO_QUERY(''SELECT 2 AS y''))'))");
            assertAnswer(
                "REFUSED SQL compilation error: | argument needs to be a string: '0'",
                "SELECT * FROM TABLE(TO_QUERY('SELECT a FROM tq WHERE a = :v ORDER BY a', v => 2))");
            assertAnswer(
                "REFUSED SQL compilation error: | argument needs to be a string: '0'",
                "SELECT * FROM TABLE(TO_QUERY('DELETE FROM tq WHERE a = :v', v => 2))");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tq");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "REFUSED SQL compilation error: | [1] is not a valid FROM item",
                "EXPLAIN SELECT * FROM TABLE(TO_QUERY('DELETE FROM tq'))");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tq");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tq");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tq");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'DELETE' for child job from stored procedure in FROM clause on TABLE 'TQ'.",
                "SELECT * FROM TABLE(p_tab()) ORDER BY a");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tq");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'CALL'.",
                "EXPLAIN CALL p_one()");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tq");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            answer(
                "EXPLAIN DELETE FROM tq");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tq");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            answer(
                "EXPLAIN INSERT INTO tq VALUES (7)");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tq");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            answer(
                "EXPLAIN UPDATE tq SET a = 5");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tq");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tq");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "",
                "SHOW TABLES LIKE 'MADE3'");
            assertAnswer(
                "Sequence SQ successfully created.",
                "CREATE OR REPLACE SEQUENCE sq");
            assertAnswer(
                "1",
                "SELECT sq.NEXTVAL");
            answer(
                "SELECT sq.NEXTVAL");
            answer(
                "SELECT sq.NEXTVAL");
            answer(
                "SELECT sq.NEXTVAL");
            assertAnswer(
                "Statement executed successfully.",
                "UNSET tqv");
            assertAnswer(
                "P_ONE successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_one()");
            assertAnswer(
                "P_TAB successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_tab()");
        } finally {
            answer(
                "DROP TABLE IF EXISTS tq");
            answer(
                "DROP TABLE IF EXISTS tq2");
            answer(
                "DROP TABLE IF EXISTS tq3");
            answer(
                "DROP TABLE IF EXISTS tq9");
            answer(
                "DROP SEQUENCE IF EXISTS sq");
            answer(
                "DROP SEQUENCE IF EXISTS sq3");
            answer(
                "DROP SEQUENCE IF EXISTS sq4");
            answer(
                "DROP VIEW IF EXISTS vw4");
            answer(
                "DROP VIEW IF EXISTS vw9");
        }
    }

    @Test
    public void childJobsOfAProcedureInFrom() {
        engine.execute(
            "CREATE OR REPLACE TABLE tq (a INT)");
        engine.execute(
            "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
        engine.execute(
            "CREATE OR REPLACE TABLE tq2 (a INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
        engine.execute(
            "CREATE OR REPLACE TABLE tq3 (a INT)");
        engine.execute(
            "CREATE OR REPLACE SEQUENCE sq3");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_w() RETURNS INT LANGUAGE SQL AS $$ BEGIN DELETE FROM tq2; RETURN 1; "
                + "END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_r() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN (SELECT COUNT(*) FROM "
                + "tq2); END $$");
        try {
            assertAnswer(
                "Function P_INS successfully created.",
                "CREATE OR REPLACE PROCEDURE p_ins() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN INSERT INTO tq2 VALUES (9); RETURN TABLE(r); END "
                    + "$$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'INSERT' for child job from stored procedure in FROM clause on TABLE 'TQ2'.",
                "SELECT * FROM TABLE(p_ins()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_UPD successfully created.",
                "CREATE OR REPLACE PROCEDURE p_upd() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN UPDATE tq2 SET a = 0; RETURN TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'UPDATE' for child job from stored procedure in FROM clause on TABLE 'TQ2'.",
                "SELECT * FROM TABLE(p_upd()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_DEL successfully created.",
                "CREATE OR REPLACE PROCEDURE p_del() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN DELETE FROM tq2; RETURN TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'DELETE' for child job from stored procedure in FROM clause on TABLE 'TQ2'.",
                "SELECT * FROM TABLE(p_del()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_MRG successfully created.",
                "CREATE OR REPLACE PROCEDURE p_mrg() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN MERGE INTO tq2 USING (SELECT 1 AS a) s ON tq2.a "
                    + "= s.a WHEN MATCHED THEN DELETE; RETURN TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'MERGE' for child job from stored procedure in FROM clause on TABLE 'TQ2'.",
                "SELECT * FROM TABLE(p_mrg()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_TRN successfully created.",
                "CREATE OR REPLACE PROCEDURE p_trn() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN TRUNCATE TABLE tq2; RETURN TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'TRUNCATE_TABLE' for child job from stored procedure in FROM clause.",
                "SELECT * FROM TABLE(p_trn()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_CRT successfully created.",
                "CREATE OR REPLACE PROCEDURE p_crt() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN CREATE TABLE made_c (a INT); RETURN TABLE(r); "
                    + "END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'CREATE' for child job from stored procedure in FROM clause on TABLE 'MADE_C'.",
                "SELECT * FROM TABLE(p_crt()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_CTT successfully created.",
                "CREATE OR REPLACE PROCEDURE p_ctt() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN CREATE OR REPLACE TEMPORARY TABLE made_t (a "
                    + "INT); RETURN TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'CREATE' for child job from stored procedure in FROM clause on TABLE 'MADE_T'.",
                "SELECT * FROM TABLE(p_ctt()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_CTA successfully created.",
                "CREATE OR REPLACE PROCEDURE p_cta() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN CREATE OR REPLACE TABLE made_a AS SELECT 1 AS a; "
                    + "RETURN TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'CREATE' for child job from stored procedure in FROM clause on TABLE 'MADE_A'.",
                "SELECT * FROM TABLE(p_cta()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_DRP successfully created.",
                "CREATE OR REPLACE PROCEDURE p_drp() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN DROP TABLE IF EXISTS tq3; RETURN TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'DROP' for child job from stored procedure in FROM clause on TABLE 'TQ3'.",
                "SELECT * FROM TABLE(p_drp()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_ALT successfully created.",
                "CREATE OR REPLACE PROCEDURE p_alt() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN ALTER TABLE tq2 ADD COLUMN b INT; RETURN "
                    + "TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'ALTER_TABLE_ADD_COLUMN' for child job from stored procedure in FROM clause.",
                "SELECT * FROM TABLE(p_alt()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_CVW successfully created.",
                "CREATE OR REPLACE PROCEDURE p_cvw() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN CREATE OR REPLACE VIEW made_v AS SELECT 1 AS a; "
                    + "RETURN TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'CREATE' for child job from stored procedure in FROM clause on VIEW 'MADE_V'.",
                "SELECT * FROM TABLE(p_cvw()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_CMT successfully created.",
                "CREATE OR REPLACE PROCEDURE p_cmt() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN COMMENT ON TABLE tq2 IS 'c'; RETURN TABLE(r); "
                    + "END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'ALTER' for child job from stored procedure in FROM clause.",
                "SELECT * FROM TABLE(p_cmt()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_SETV successfully created.",
                "CREATE OR REPLACE PROCEDURE p_setv() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r "
                    + "RESULTSET DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN SET l3v = 1; RETURN TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : Stored procedure execution error: Unsupported "
                    + "statement type 'SET'.",
                "SELECT * FROM TABLE(p_setv()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_LET successfully created.",
                "CREATE OR REPLACE PROCEDURE p_let() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN LET x := 1; RETURN TABLE(r); END $$");
            assertAnswer(
                "1 ;; 2 ;; 3",
                "SELECT * FROM TABLE(p_let()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_SEL successfully created.",
                "CREATE OR REPLACE PROCEDURE p_sel() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN LET x INT := (SELECT COUNT(*) FROM tq2); RETURN "
                    + "TABLE(r); END $$");
            assertAnswer(
                "1 ;; 2 ;; 3",
                "SELECT * FROM TABLE(p_sel()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_SHW successfully created.",
                "CREATE OR REPLACE PROCEDURE p_shw() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN SHOW TABLES; RETURN TABLE(r); END $$");
            assertAnswer(
                "1 ;; 2 ;; 3",
                "SELECT * FROM TABLE(p_shw()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_USE successfully created.",
                "CREATE OR REPLACE PROCEDURE p_use() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN USE SCHEMA test_schema; RETURN TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : Stored procedure execution error: Unsupported "
                    + "statement type 'USE'.",
                "SELECT * FROM TABLE(p_use()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_EXI successfully created.",
                "CREATE OR REPLACE PROCEDURE p_exi() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN EXECUTE IMMEDIATE 'DELETE FROM tq2'; RETURN "
                    + "TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'DELETE' for child job from stored procedure in FROM clause on TABLE 'TQ2'.",
                "SELECT * FROM TABLE(p_exi()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_EXS successfully created.",
                "CREATE OR REPLACE PROCEDURE p_exs() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN EXECUTE IMMEDIATE 'SELECT 1'; RETURN TABLE(r); "
                    + "END $$");
            assertAnswer(
                "1 ;; 2 ;; 3",
                "SELECT * FROM TABLE(p_exs()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_SEQ successfully created.",
                "CREATE OR REPLACE PROCEDURE p_seq() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN LET x INT := (SELECT sq3.NEXTVAL); RETURN "
                    + "TABLE(r); END $$");
            assertAnswer(
                "1 ;; 2 ;; 3",
                "SELECT * FROM TABLE(p_seq()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_CAL successfully created.",
                "CREATE OR REPLACE PROCEDURE p_cal() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN CALL p_w(); RETURN TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : Uncaught exception of type 'STATEMENT_ERROR' on "
                    + "line 1 at position 7 : SQL compilation error: Invalid statement type 'DELETE' for child job "
                    + "from stored procedure in FROM clause on TABLE 'TQ2'.",
                "SELECT * FROM TABLE(p_cal()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_CAR successfully created.",
                "CREATE OR REPLACE PROCEDURE p_car() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN CALL p_r(); RETURN TABLE(r); END $$");
            assertAnswer(
                "1 ;; 2 ;; 3",
                "SELECT * FROM TABLE(p_car()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_INS2 successfully created.",
                "CREATE OR REPLACE PROCEDURE p_ins2() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r "
                    + "RESULTSET DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN INSERT INTO tq VALUES (8); RETURN "
                    + "TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'INSERT' for child job from stored procedure in FROM clause on TABLE 'TQ'.",
                "SELECT * FROM TABLE(p_ins2()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_CSEQ successfully created.",
                "CREATE OR REPLACE PROCEDURE p_cseq() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r "
                    + "RESULTSET DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN CREATE OR REPLACE SEQUENCE made_s; "
                    + "RETURN TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'CREATE' for child job from stored procedure in FROM clause on SEQUENCE 'MADE_S'.",
                "SELECT * FROM TABLE(p_cseq()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            answer(
                "CALL p_del()");
            assertAnswer(
                "3/0",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            answer(
                "SELECT * FROM TABLE(TO_QUERY('CALL p_del()'))");
            assertAnswer(
                "3/0",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "1",
                "SELECT * FROM TABLE(TO_QUERY('CALL p_w()'))");
            assertAnswer(
                "3/0",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "3",
                "INSERT OVERWRITE INTO tq VALUES (1), (2), (3)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "1 ;; 2 ;; 3",
                "SELECT * FROM TABLE(p_sel()) ORDER BY a");
            assertAnswer(
                "2",
                "SELECT * FROM TABLE(TO_QUERY('SELECT a FROM tq WHERE a = :v ORDER BY a', v => '2'))");
            assertAnswer(
                "abc",
                "SELECT * FROM TABLE(TO_QUERY(SQL => 'SELECT :x AS y', x => 'abc'))");
            assertAnswer(
                "REFUSED SQL compilation error: | argument needs to be a string: '0'",
                "SELECT * FROM TABLE(TO_QUERY('SELECT :x AS y', x => NULL))");
            assertAnswer(
                "REFUSED SQL compilation error: | argument needs to be a string: '0'",
                "SELECT * FROM TABLE(TO_QUERY('SELECT :x AS y', x => TRUE))");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Bind variable :x not set.",
                "SELECT * FROM TABLE(TO_QUERY('SELECT :x AS y'))");
            assertAnswer(
                "2",
                "SELECT * FROM TABLE(TO_QUERY('SELECT a FROM tq WHERE a = TO_NUMBER(:v) ORDER BY a', v => '2'))");
            assertAnswer(
                "1",
                "SELECT * FROM TABLE(TO_QUERY('SELECT 1 AS x; -- c'))");
            assertAnswer(
                "1",
                "SELECT * FROM TABLE(TO_QUERY('SELECT 1 AS x;;'))");
            assertAnswer(
                "2",
                "SELECT * FROM TABLE(TO_QUERY('SELECT 1 AS x', SQL => 'SELECT 2 AS x'))");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 20 | missing required argument [SQL] for "
                    + "function [TO_QUERY]",
                "SELECT * FROM TABLE(TO_QUERY(INPUT => 'SELECT 1 AS x'))");
            assertAnswer(
                "REFUSED Unsupported feature 'SHOW TABLES'.",
                "SELECT * FROM TABLE(TO_QUERY('SHOW TABLES ->> SELECT 1'))");
            assertAnswer(
                "1",
                "SELECT * FROM TABLE(TO_QUERY('CALL p_r()'))");
            assertAnswer(
                "1 ;; 2 ;; 3",
                "SELECT * FROM TABLE(TO_QUERY('CALL p_sel()')) ORDER BY 1");
            answer(
                "SELECT * FROM TABLE(TO_QUERY('EXPLAIN SELECT a FROM tq'))");
            assertAnswer(
                "REFUSED SQL compilation error: | [1] is not a valid FROM item",
                "SELECT * FROM TABLE(TO_QUERY('EXPLAIN DELETE FROM tq'))");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "Statement executed successfully.",
                "UNSET l3v");
            assertAnswer(
                "",
                "SHOW TABLES LIKE 'MADE_%'");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Drop statement executed successfully (MADE_C already dropped).",
                "DROP TABLE IF EXISTS made_c");
            assertAnswer(
                "Drop statement executed successfully (MADE_T already dropped).",
                "DROP TABLE IF EXISTS made_t");
            assertAnswer(
                "Drop statement executed successfully (MADE_A already dropped).",
                "DROP TABLE IF EXISTS made_a");
            assertAnswer(
                "Drop statement executed successfully (MADE_V already dropped).",
                "DROP VIEW IF EXISTS made_v");
            assertAnswer(
                "Drop statement executed successfully (MADE_S already dropped).",
                "DROP SEQUENCE IF EXISTS made_s");
            assertAnswer(
                "P_INS successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_ins()");
            assertAnswer(
                "P_UPD successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_upd()");
            assertAnswer(
                "P_DEL successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_del()");
            assertAnswer(
                "P_MRG successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_mrg()");
            assertAnswer(
                "P_TRN successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_trn()");
            assertAnswer(
                "P_CRT successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_crt()");
            assertAnswer(
                "P_CTT successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_ctt()");
            assertAnswer(
                "P_CTA successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_cta()");
            assertAnswer(
                "P_DRP successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_drp()");
            assertAnswer(
                "P_ALT successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_alt()");
            assertAnswer(
                "P_CVW successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_cvw()");
            assertAnswer(
                "P_CMT successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_cmt()");
            assertAnswer(
                "P_SETV successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_setv()");
            assertAnswer(
                "P_LET successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_let()");
            assertAnswer(
                "P_SEL successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_sel()");
            assertAnswer(
                "P_SHW successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_shw()");
            assertAnswer(
                "P_USE successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_use()");
            assertAnswer(
                "P_EXI successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_exi()");
            assertAnswer(
                "P_EXS successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_exs()");
            assertAnswer(
                "P_SEQ successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_seq()");
            assertAnswer(
                "P_CAL successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_cal()");
            assertAnswer(
                "P_CAR successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_car()");
            assertAnswer(
                "P_INS2 successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_ins2()");
            assertAnswer(
                "P_CSEQ successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_cseq()");
            assertAnswer(
                "P_W successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_w()");
            assertAnswer(
                "P_R successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_r()");
        } finally {
            answer(
                "DROP TABLE IF EXISTS tq");
            answer(
                "DROP TABLE IF EXISTS tq2");
            answer(
                "DROP TABLE IF EXISTS tq3");
            answer(
                "DROP TABLE IF EXISTS tq9");
            answer(
                "DROP SEQUENCE IF EXISTS sq");
            answer(
                "DROP SEQUENCE IF EXISTS sq3");
            answer(
                "DROP SEQUENCE IF EXISTS sq4");
            answer(
                "DROP VIEW IF EXISTS vw4");
            answer(
                "DROP VIEW IF EXISTS vw9");
        }
    }

    @Test
    public void alterKindsAndSessionVariables() {
        engine.execute(
            "CREATE OR REPLACE TABLE tq (a INT)");
        engine.execute(
            "INSERT INTO tq VALUES (1), (2), (3)");
        engine.execute(
            "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
        engine.execute(
            "CREATE OR REPLACE SEQUENCE sq4");
        engine.execute(
            "CREATE OR REPLACE VIEW vw4 AS SELECT 1 AS a");
        try {
            assertAnswer(
                "Function P_REN successfully created.",
                "CREATE OR REPLACE PROCEDURE p_ren() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN ALTER TABLE tq2 RENAME TO tq9; RETURN TABLE(r); "
                    + "END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'RENAME_TABLE' for child job from stored procedure in FROM clause.",
                "SELECT * FROM TABLE(p_ren()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_DCOL successfully created.",
                "CREATE OR REPLACE PROCEDURE p_dcol() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r "
                    + "RESULTSET DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN ALTER TABLE tq2 DROP COLUMN a; RETURN "
                    + "TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'ALTER_TABLE_DROP_COLUMN' for child job from stored procedure in FROM clause.",
                "SELECT * FROM TABLE(p_dcol()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_SCM successfully created.",
                "CREATE OR REPLACE PROCEDURE p_scm() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN ALTER TABLE tq2 SET COMMENT = 'c'; RETURN "
                    + "TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'ALTER' for child job from stored procedure in FROM clause.",
                "SELECT * FROM TABLE(p_scm()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_CLU successfully created.",
                "CREATE OR REPLACE PROCEDURE p_clu() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN ALTER TABLE tq2 CLUSTER BY (a); RETURN TABLE(r); "
                    + "END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'ALTER_TABLE' for child job from stored procedure in FROM clause.",
                "SELECT * FROM TABLE(p_clu()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_ASEQ successfully created.",
                "CREATE OR REPLACE PROCEDURE p_aseq() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r "
                    + "RESULTSET DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN ALTER SEQUENCE sq4 SET INCREMENT = 2; "
                    + "RETURN TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'ALTER' for child job from stored procedure in FROM clause.",
                "SELECT * FROM TABLE(p_aseq()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_AVW successfully created.",
                "CREATE OR REPLACE PROCEDURE p_avw() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN ALTER VIEW vw4 RENAME TO vw9; RETURN TABLE(r); "
                    + "END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'RENAME_VIEW' for child job from stored procedure in FROM clause.",
                "SELECT * FROM TABLE(p_avw()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_CFN successfully created.",
                "CREATE OR REPLACE PROCEDURE p_cfn() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN CREATE OR REPLACE FUNCTION made_f() RETURNS INT "
                    + "AS '1'; RETURN TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'CREATE' for child job from stored procedure in FROM clause on FUNCTION 'MADE_F'.",
                "SELECT * FROM TABLE(p_cfn()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_CST successfully created.",
                "CREATE OR REPLACE PROCEDURE p_cst() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN CREATE OR REPLACE STAGE made_st; RETURN "
                    + "TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'CREATE' for child job from stored procedure in FROM clause on STAGE 'MADE_ST'.",
                "SELECT * FROM TABLE(p_cst()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_UND successfully created.",
                "CREATE OR REPLACE PROCEDURE p_und() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN UNDROP TABLE tq_gone; RETURN TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'RESTORE' for child job from stored procedure in FROM clause.",
                "SELECT * FROM TABLE(p_und()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_ISL successfully created.",
                "CREATE OR REPLACE PROCEDURE p_isl() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN INSERT INTO tq2 SELECT a FROM tq; RETURN "
                    + "TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'INSERT' for child job from stored procedure in FROM clause on TABLE 'TQ2'.",
                "SELECT * FROM TABLE(p_isl()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_OVW successfully created.",
                "CREATE OR REPLACE PROCEDURE p_ovw() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN INSERT OVERWRITE INTO tq2 VALUES (5); RETURN "
                    + "TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'INSERT' for child job from stored procedure in FROM clause on TABLE 'TQ2'.",
                "SELECT * FROM TABLE(p_ovw()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_DVW successfully created.",
                "CREATE OR REPLACE PROCEDURE p_dvw() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN DROP VIEW IF EXISTS vw4; RETURN TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'DROP' for child job from stored procedure in FROM clause on VIEW 'VW4'.",
                "SELECT * FROM TABLE(p_dvw()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_DSQ successfully created.",
                "CREATE OR REPLACE PROCEDURE p_dsq() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN DROP SEQUENCE IF EXISTS sq4; RETURN TABLE(r); "
                    + "END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'DROP' for child job from stored procedure in FROM clause on SEQUENCE 'SQ4'.",
                "SELECT * FROM TABLE(p_dsq()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_CMC successfully created.",
                "CREATE OR REPLACE PROCEDURE p_cmc() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN COMMENT ON COLUMN tq2.a IS 'c'; RETURN TABLE(r); "
                    + "END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'ALTER_TABLE_MODIFY_COLUMN' for child job from stored procedure in FROM clause.",
                "SELECT * FROM TABLE(p_cmc()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_CTL successfully created.",
                "CREATE OR REPLACE PROCEDURE p_ctl() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN CREATE TABLE made_l LIKE tq2; RETURN TABLE(r); "
                    + "END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'CREATE' for child job from stored procedure in FROM clause on TABLE 'MADE_L'.",
                "SELECT * FROM TABLE(p_ctl()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_CTC successfully created.",
                "CREATE OR REPLACE PROCEDURE p_ctc() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN CREATE TABLE made_k CLONE tq2; RETURN TABLE(r); "
                    + "END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'CREATE' for child job from stored procedure in FROM clause on TABLE 'MADE_K'.",
                "SELECT * FROM TABLE(p_ctc()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_DSC successfully created.",
                "CREATE OR REPLACE PROCEDURE p_dsc() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN DESCRIBE TABLE tq2; RETURN TABLE(r); END $$");
            assertAnswer(
                "1 ;; 2 ;; 3",
                "SELECT * FROM TABLE(p_dsc()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_EXP successfully created.",
                "CREATE OR REPLACE PROCEDURE p_exp() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN EXPLAIN SELECT 1; RETURN TABLE(r); END $$");
            assertAnswer(
                "1 ;; 2 ;; 3",
                "SELECT * FROM TABLE(p_exp()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_UNSV successfully created.",
                "CREATE OR REPLACE PROCEDURE p_unsv() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r "
                    + "RESULTSET DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN UNSET l4v; RETURN TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : Stored procedure execution error: Unsupported "
                    + "statement type 'UNSET'.",
                "SELECT * FROM TABLE(p_unsv()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_LST successfully created.",
                "CREATE OR REPLACE PROCEDURE p_lst() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                    + "DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN SHOW SEQUENCES; RETURN TABLE(r); END $$");
            assertAnswer(
                "1 ;; 2 ;; 3",
                "SELECT * FROM TABLE(p_lst()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_SET successfully created.",
                "CREATE OR REPLACE PROCEDURE p_set() RETURNS INT LANGUAGE SQL AS $$ BEGIN SET l4v = 1; RETURN 1; "
                    + "END $$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure "
                    + "execution error: Unsupported statement type 'SET'.",
                "CALL p_set()");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Session variable '$L4V' does not "
                    + "exist",
                "SELECT $l4v");
            assertAnswer(
                "Function P_UNSET successfully created.",
                "CREATE OR REPLACE PROCEDURE p_unset() RETURNS INT LANGUAGE SQL AS $$ BEGIN UNSET l4v; RETURN 1; "
                    + "END $$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure "
                    + "execution error: Unsupported statement type 'UNSET'.",
                "CALL p_unset()");
            assertAnswer(
                "Function P_SETC successfully created.",
                "CREATE OR REPLACE PROCEDURE p_setc() RETURNS INT LANGUAGE SQL EXECUTE AS CALLER AS $$ BEGIN SET "
                    + "l4v = 2; RETURN 1; END $$");
            assertAnswer(
                "1",
                "CALL p_setc()");
            assertAnswer(
                "2",
                "SELECT $l4v");
            assertAnswer(
                "Function P_UNSETC successfully created.",
                "CREATE OR REPLACE PROCEDURE p_unsetc() RETURNS INT LANGUAGE SQL EXECUTE AS CALLER AS $$ BEGIN "
                    + "UNSET l4v; RETURN 1; END $$");
            assertAnswer(
                "1",
                "CALL p_unsetc()");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Session variable '$L4V' does not "
                    + "exist",
                "SELECT $l4v");
            assertAnswer(
                "Function P_SETCT successfully created.",
                "CREATE OR REPLACE PROCEDURE p_setct() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                    + "DECLARE r RESULTSET DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN SET l4v = 3; RETURN "
                    + "TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'SET' for child job from stored procedure in FROM clause.",
                "SELECT * FROM TABLE(p_setct()) ORDER BY a");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Session variable '$L4V' does not "
                    + "exist",
                "SELECT $l4v");
            assertAnswer(
                "Function P_DELCT successfully created.",
                "CREATE OR REPLACE PROCEDURE p_delct() RETURNS TABLE (a INT) LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                    + "DECLARE r RESULTSET DEFAULT (SELECT a FROM tq ORDER BY a); BEGIN DELETE FROM tq2; RETURN "
                    + "TABLE(r); END $$");
            assertAnswer(
                "REFUSED Stored procedure execution error on line 1 position 20: Uncaught exception of type "
                    + "'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation error: Invalid statement type "
                    + "'DELETE' for child job from stored procedure in FROM clause on TABLE 'TQ2'.",
                "SELECT * FROM TABLE(p_delct()) ORDER BY a");
            assertAnswer(
                "3/1",
                "SELECT (SELECT COUNT(*) FROM tq) || '/' || (SELECT COUNT(*) FROM tq2)");
            assertAnswer(
                "Table TQ2 successfully created.",
                "CREATE OR REPLACE TABLE tq2 AS SELECT 1 AS a");
            assertAnswer(
                "Function P_USEC successfully created.",
                "CREATE OR REPLACE PROCEDURE p_usec() RETURNS INT LANGUAGE SQL EXECUTE AS CALLER AS $$ BEGIN USE "
                    + "SCHEMA test_schema; RETURN 1; END $$");
            assertAnswer(
                "1",
                "CALL p_usec()");
            assertAnswer(
                "Function P_USE successfully created.",
                "CREATE OR REPLACE PROCEDURE p_use() RETURNS INT LANGUAGE SQL AS $$ BEGIN USE SCHEMA test_schema; "
                    + "RETURN 1; END $$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure "
                    + "execution error: Unsupported statement type 'USE'.",
                "CALL p_use()");
            assertAnswer(
                "REFUSED SQL compilation error: | argument needs to be a string: '0'",
                "SELECT * FROM TABLE(TO_QUERY(SQL => 1))");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 't'.",
                "SELECT * FROM TABLE(TO_QUERY('SELECT 1 AS x') t)");
            assertAnswer(
                "1",
                "SELECT t.x FROM TABLE(TO_QUERY('SELECT 1 AS x')) t");
            assertAnswer(
                "Statement executed successfully.",
                "UNSET l4v");
            assertAnswer(
                "",
                "SHOW TABLES LIKE 'MADE_%'");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Drop statement executed successfully (MADE_L already dropped).",
                "DROP TABLE IF EXISTS made_l");
            assertAnswer(
                "Drop statement executed successfully (MADE_K already dropped).",
                "DROP TABLE IF EXISTS made_k");
            assertAnswer(
                "Drop statement executed successfully (MADE_F already dropped).",
                "DROP FUNCTION IF EXISTS made_f()");
            assertAnswer(
                "Drop statement executed successfully (MADE_ST already dropped).",
                "DROP STAGE IF EXISTS made_st");
            assertAnswer(
                "Drop statement executed successfully (TQ9 already dropped).",
                "DROP TABLE IF EXISTS tq9");
            assertAnswer(
                "VW4 successfully dropped.",
                "DROP VIEW IF EXISTS vw4");
            assertAnswer(
                "Drop statement executed successfully (VW9 already dropped).",
                "DROP VIEW IF EXISTS vw9");
            assertAnswer(
                "SQ4 successfully dropped.",
                "DROP SEQUENCE IF EXISTS sq4");
            assertAnswer(
                "P_REN successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_ren()");
            assertAnswer(
                "P_DCOL successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_dcol()");
            assertAnswer(
                "P_SCM successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_scm()");
            assertAnswer(
                "P_CLU successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_clu()");
            assertAnswer(
                "P_ASEQ successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_aseq()");
            assertAnswer(
                "P_AVW successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_avw()");
            assertAnswer(
                "P_CFN successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_cfn()");
            assertAnswer(
                "P_CST successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_cst()");
            assertAnswer(
                "P_UND successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_und()");
            assertAnswer(
                "P_ISL successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_isl()");
            assertAnswer(
                "P_OVW successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_ovw()");
            assertAnswer(
                "P_DVW successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_dvw()");
            assertAnswer(
                "P_DSQ successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_dsq()");
            assertAnswer(
                "P_CMC successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_cmc()");
            assertAnswer(
                "P_CTL successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_ctl()");
            assertAnswer(
                "P_CTC successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_ctc()");
            assertAnswer(
                "P_DSC successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_dsc()");
            assertAnswer(
                "P_EXP successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_exp()");
            assertAnswer(
                "P_UNSV successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_unsv()");
            assertAnswer(
                "P_LST successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_lst()");
            assertAnswer(
                "P_SET successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_set()");
            assertAnswer(
                "P_UNSET successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_unset()");
            assertAnswer(
                "P_SETC successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_setc()");
            assertAnswer(
                "P_UNSETC successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_unsetc()");
            assertAnswer(
                "P_SETCT successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_setct()");
            assertAnswer(
                "P_DELCT successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_delct()");
            assertAnswer(
                "P_USEC successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_usec()");
            assertAnswer(
                "P_USE successfully dropped.",
                "DROP PROCEDURE IF EXISTS p_use()");
        } finally {
            answer(
                "DROP TABLE IF EXISTS tq");
            answer(
                "DROP TABLE IF EXISTS tq2");
            answer(
                "DROP TABLE IF EXISTS tq3");
            answer(
                "DROP TABLE IF EXISTS tq9");
            answer(
                "DROP SEQUENCE IF EXISTS sq");
            answer(
                "DROP SEQUENCE IF EXISTS sq3");
            answer(
                "DROP SEQUENCE IF EXISTS sq4");
            answer(
                "DROP VIEW IF EXISTS vw4");
            answer(
                "DROP VIEW IF EXISTS vw9");
        }
    }
}
