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
 * A task's body and an alert's action are any one statement: a CREATE, an ALTER or a DROP, a TRUNCATE, a SHOW,
 * a USE, a SET, a GRANT, a COMMIT, an EXPLAIN as well as a query, DML, a CALL or a block. The body is read as
 * the statement it is - a fault in it is reported where that statement reports it - and stored as written,
 * without its ';' and unjudged. A flow chain is no body, and a COMMENT after a body of another family is a
 * syntax error. Every answer is live-verified.
 */
public class TaskBodyStatementTest extends MeasuredCellsTestSupport {

    @Test
    public void aBodyOfAnyFamily() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute(
            "INSERT INTO t VALUES (1, 2)");
        assertAnswer(
            "Task TK_CTAS2 successfully created.",
            "CREATE OR REPLACE TASK tk_ctas2 SCHEDULE = '60 MINUTE' AS CREATE OR REPLACE TABLE t5 AS SELECT a "
                + "FROM t");
        assertAnswer(
            "Task TK_DDL successfully created.",
            "CREATE OR REPLACE TASK tk_ddl SCHEDULE = '60 MINUTE' AS CREATE OR REPLACE TABLE t6 (x INT)");
        assertAnswer(
            "Task TK_VIEW successfully created.",
            "CREATE OR REPLACE TASK tk_view SCHEDULE = '60 MINUTE' AS CREATE VIEW v9 AS SELECT 1 AS c");
        assertAnswer(
            "Task TK_ALTER successfully created.",
            "CREATE OR REPLACE TASK tk_alter SCHEDULE = '60 MINUTE' AS ALTER TABLE t ADD COLUMN c INT");
        assertAnswer(
            "Task TK_DROP successfully created.",
            "CREATE OR REPLACE TASK tk_drop SCHEDULE = '60 MINUTE' AS DROP TABLE t7");
        assertAnswer(
            "Task TK_TRUNC successfully created.",
            "CREATE OR REPLACE TASK tk_trunc SCHEDULE = '60 MINUTE' AS TRUNCATE TABLE t");
        assertAnswer(
            "Task TK_GRANT successfully created.",
            "CREATE OR REPLACE TASK tk_grant SCHEDULE = '60 MINUTE' AS GRANT SELECT ON TABLE t TO ROLE "
                + "nosuch_role_zzq");
        assertAnswer(
            "Task TK_COPY successfully created.",
            "CREATE OR REPLACE TASK tk_copy SCHEDULE = '60 MINUTE' AS COPY INTO t FROM @nosuch_stage");
        assertAnswer(
            "Task TK_SHOW successfully created.",
            "CREATE OR REPLACE TASK tk_show SCHEDULE = '60 MINUTE' AS SHOW TABLES");
        assertAnswer(
            "Task TK_USE successfully created.",
            "CREATE OR REPLACE TASK tk_use SCHEDULE = '60 MINUTE' AS USE SCHEMA test_schema");
        assertAnswer(
            "Task TK_SET successfully created.",
            "CREATE OR REPLACE TASK tk_set SCHEDULE = '60 MINUTE' AS SET v = 1");
        assertAnswer(
            "Task TK_DESC successfully created.",
            "CREATE OR REPLACE TASK tk_desc SCHEDULE = '60 MINUTE' AS DESCRIBE TABLE t");
        assertAnswer(
            "Task TK_COMMIT successfully created.",
            "CREATE OR REPLACE TASK tk_commit SCHEDULE = '60 MINUTE' AS COMMIT");
        assertAnswer(
            "Task TK_TASK successfully created.",
            "CREATE OR REPLACE TASK tk_task SCHEDULE = '60 MINUTE' AS CREATE TASK inner_t AS SELECT 1");
        assertAnswer(
            "Task TK_SESS successfully created.",
            "CREATE OR REPLACE TASK tk_sess SCHEDULE = '60 MINUTE' AS ALTER SESSION SET TIMEZONE = 'UTC'");
        assertAnswer(
            "Task TK_EXPL successfully created.",
            "CREATE OR REPLACE TASK tk_expl SCHEDULE = '60 MINUTE' AS EXPLAIN SELECT 1");
        assertAnswer(
            "Task TK_UNDROP successfully created.",
            "CREATE OR REPLACE TASK tk_undrop SCHEDULE = '60 MINUTE' AS UNDROP TABLE t7");
        assertAnswer(
            "Task TK_COMMENT successfully created.",
            "CREATE OR REPLACE TASK tk_comment SCHEDULE = '60 MINUTE' AS COMMENT ON TABLE t IS 'x'");
        assertAnswer(
            "Task TK_SEL successfully created.",
            "CREATE OR REPLACE TASK tk_sel SCHEDULE = '60 MINUTE' AS SELECT 1");
        assertAnswer(
            "Task TK_INS successfully created.",
            "CREATE OR REPLACE TASK tk_ins SCHEDULE = '60 MINUTE' AS INSERT INTO t VALUES (1, 2)");
        assertAnswer(
            "Task TK_CALL successfully created.",
            "CREATE OR REPLACE TASK tk_call SCHEDULE = '60 MINUTE' AS CALL nosuch_proc()");
        assertAnswer(
            "Task TK_EI successfully created.",
            "CREATE OR REPLACE TASK tk_ei SCHEDULE = '60 MINUTE' AS EXECUTE IMMEDIATE 'SELECT 1'");
        assertAnswer(
            "Task TK_BLK successfully created.",
            "CREATE OR REPLACE TASK tk_blk SCHEDULE = '60 MINUTE' AS BEGIN CREATE OR REPLACE TABLE t8 (x INT); END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected 'nonsense'.",
            "CREATE OR REPLACE TASK tk_bad1 SCHEDULE = '60 MINUTE' AS nonsense words here");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 80 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_bad2 SCHEDULE = '60 MINUTE' AS CREATE OR REPLACE TABLE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 87 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_bad5 SCHEDULE = '60 MINUTE' AS CREATE TABLE t9 (x INT) nosuch");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 67 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_bad6 SCHEDULE = '60 MINUTE' AS DROP TABLE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_bad7 SCHEDULE = '60 MINUTE' AS ALTER TABLE t ADD");
        assertAnswer(
            "Task TK_MISSING successfully created.",
            "CREATE OR REPLACE TASK tk_missing SCHEDULE = '60 MINUTE' AS SELECT * FROM nosuch_table");
        assertAnswer(
            "Task TK_WITH successfully created.",
            "CREATE OR REPLACE TASK tk_with SCHEDULE = '60 MINUTE' AS WITH c AS (SELECT 1 AS x) SELECT * FROM c");
        assertAnswer(
            "Task TK_RENAME successfully created.",
            "CREATE OR REPLACE TASK tk_rename SCHEDULE = '60 MINUTE' AS ALTER TABLE t RENAME TO t11");
        assertAnswer(
            "Task TK_SEQ successfully created.",
            "CREATE OR REPLACE TASK tk_seq SCHEDULE = '60 MINUTE' AS CREATE SEQUENCE s1");
        assertAnswer(
            "Task TK_SCHEMA successfully created.",
            "CREATE OR REPLACE TASK tk_schema SCHEDULE = '60 MINUTE' AS CREATE SCHEMA sx");
        assertAnswer(
            "Task TK_LS successfully created.",
            "CREATE OR REPLACE TASK tk_ls SCHEDULE = '60 MINUTE' AS LIST @~");
        assertAnswer(
            "Task TK_DEL successfully created.",
            "CREATE OR REPLACE TASK tk_del SCHEDULE = '60 MINUTE' AS DELETE FROM t");
        assertAnswer(
            "Task TK_UPD successfully created.",
            "CREATE OR REPLACE TASK tk_upd SCHEDULE = '60 MINUTE' AS UPDATE t SET a = 1");
        assertAnswer(
            "Task TK_MERGE successfully created.",
            "CREATE OR REPLACE TASK tk_merge SCHEDULE = '60 MINUTE' AS MERGE INTO t USING (SELECT 1 AS a) s ON "
                + "t.a = s.a WHEN MATCHED THEN UPDATE SET b = 1");
        assertAnswer(
            "Task TK_REFRESH successfully created.",
            "CREATE OR REPLACE TASK tk_refresh SCHEDULE = '60 MINUTE' AS ALTER DYNAMIC TABLE nosuch_dt REFRESH");
        assertAnswer(
            "Task TK_EXEC successfully created.",
            "CREATE OR REPLACE TASK tk_exec SCHEDULE = '60 MINUTE' AS EXECUTE TASK tk_sel");
        assertAnswer(
            "Task TK_ALTERTASK successfully created.",
            "CREATE OR REPLACE TASK tk_altertask SCHEDULE = '60 MINUTE' AS ALTER TASK tk_sel SUSPEND");
        assertAnswer(
            "Task TK_ROLLBACK successfully created.",
            "CREATE OR REPLACE TASK tk_rollback SCHEDULE = '60 MINUTE' AS ROLLBACK");
        assertAnswer(
            "Task TK_UNSET successfully created.",
            "CREATE OR REPLACE TASK tk_unset SCHEDULE = '60 MINUTE' AS UNSET v");
        assertAnswer(
            "Task TK_FN successfully created.",
            "CREATE OR REPLACE TASK tk_fn SCHEDULE = '60 MINUTE' AS CREATE FUNCTION f9() RETURNS INT AS '1'");
        assertAnswer(
            "Task TK_PROC successfully created.",
            "CREATE OR REPLACE TASK tk_proc SCHEDULE = '60 MINUTE' AS CREATE PROCEDURE p9() RETURNS INT LANGUAGE "
                + "SQL AS 'BEGIN RETURN 1; END'");
        assertAnswer(
            "Task TK_INSALL successfully created.",
            "CREATE OR REPLACE TASK tk_insall SCHEDULE = '60 MINUTE' AS INSERT ALL INTO t VALUES (1, 2) SELECT 1");
        assertAnswer(
            "Task TK_PAREN successfully created.",
            "CREATE OR REPLACE TASK tk_paren SCHEDULE = '60 MINUTE' AS (SELECT 1)");
        assertAnswer(
            "Task TK_SEMI successfully created.",
            "CREATE OR REPLACE TASK tk_semi SCHEDULE = '60 MINUTE' AS SELECT 1;");
        assertAnswer(
            "Task TK_DDLSEMI successfully created.",
            "CREATE OR REPLACE TASK tk_ddlsemi SCHEDULE = '60 MINUTE' AS CREATE OR REPLACE TABLE t6 (x INT);");
        assertAnswer(
            "Task TK_DROPX successfully created.",
            "CREATE OR REPLACE TASK tk_dropx SCHEDULE = '60 MINUTE' AS DROP TABLE IF EXISTS t7 CASCADE");
        assertAnswer(
            "Task TK_STREAM successfully created.",
            "CREATE OR REPLACE TASK tk_stream SCHEDULE = '60 MINUTE' AS CREATE STREAM st1 ON TABLE t");
        assertAnswer(
            "Task TK_STAGE successfully created.",
            "CREATE OR REPLACE TASK tk_stage SCHEDULE = '60 MINUTE' AS CREATE STAGE sg1");
        assertAnswer(
            "Task TK_FF successfully created.",
            "CREATE OR REPLACE TASK tk_ff SCHEDULE = '60 MINUTE' AS CREATE FILE FORMAT ff1 TYPE = CSV");
        assertAnswer(
            "Task TK_DT successfully created.",
            "CREATE OR REPLACE TASK tk_dt SCHEDULE = '60 MINUTE' AS CREATE DYNAMIC TABLE dt1 TARGET_LAG = '1 "
                + "minute' WAREHOUSE = w AS SELECT a FROM t");
        assertAnswer(
            "Task TK_ALTV successfully created.",
            "CREATE OR REPLACE TASK tk_altv SCHEDULE = '60 MINUTE' AS ALTER VIEW v9 SET COMMENT = 'x'");
        assertAnswer(
            "Task TK_CLONE successfully created.",
            "CREATE OR REPLACE TASK tk_clone SCHEDULE = '60 MINUTE' AS CREATE TABLE t12 CLONE t");
        assertAnswer(
            "Task TK_LIKE successfully created.",
            "CREATE OR REPLACE TASK tk_like SCHEDULE = '60 MINUTE' AS CREATE TABLE t13 LIKE t");
        assertAnswer(
            "Task TK_SWAP successfully created.",
            "CREATE OR REPLACE TASK tk_swap SCHEDULE = '60 MINUTE' AS ALTER TABLE t SWAP WITH t11");
        assertAnswer(
            "Task TK_USEDB successfully created.",
            "CREATE OR REPLACE TASK tk_usedb SCHEDULE = '60 MINUTE' AS USE DATABASE test_db");
        assertAnswer(
            "Task TK_USEROLE successfully created.",
            "CREATE OR REPLACE TASK tk_userole SCHEDULE = '60 MINUTE' AS USE ROLE nosuch_role_zzq");
        assertAnswer(
            "Task TK_USEWH successfully created.",
            "CREATE OR REPLACE TASK tk_usewh SCHEDULE = '60 MINUTE' AS USE WAREHOUSE nosuch_wh_zzq");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected 'LET'.",
            "CREATE OR REPLACE TASK tk_let SCHEDULE = '60 MINUTE' AS LET x := 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'RETURN'.",
            "CREATE OR REPLACE TASK tk_return SCHEDULE = '60 MINUTE' AS RETURN 1");
        assertAnswer(
            "Task TK_DECL successfully created.",
            "CREATE OR REPLACE TASK tk_decl SCHEDULE = '60 MINUTE' AS DECLARE x INT; BEGIN x := 1; END");
        assertAnswer(
            "REFUSED Invalid statement for task definition. Expecting one of the following: STATEMENT",
            "CREATE OR REPLACE TASK tk_pipe SCHEDULE = '60 MINUTE' AS SELECT 1 ->> SELECT * FROM $1");
        assertAnswer(
            "Task TK_TRUNC2 successfully created.",
            "CREATE OR REPLACE TASK tk_trunc2 SCHEDULE = '60 MINUTE' AS TRUNCATE t");
        assertAnswer(
            "Task TK_GETRES successfully created.",
            "CREATE OR REPLACE TASK tk_getres SCHEDULE = '60 MINUTE' AS SELECT * FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_empty SCHEDULE = '60 MINUTE' AS ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ''SELECT 1''.",
            "CREATE OR REPLACE TASK tk_str SCHEDULE = '60 MINUTE' AS 'SELECT 1'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected '$$SELECT 1$$'.",
            "CREATE OR REPLACE TASK tk_dollar SCHEDULE = '60 MINUTE' AS $$SELECT 1$$");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 78 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_comment_only SCHEDULE = '60 MINUTE' AS /* nothing */");
        assertAnswer(
            "Task TK_LEAD successfully created.",
            "CREATE OR REPLACE TASK tk_lead SCHEDULE = '60 MINUTE' AS /* c */ CREATE OR REPLACE TABLE t6 (x INT)");
        assertAnswer(
            "Task TK_NL successfully created.",
            "CREATE OR REPLACE TASK tk_nl SCHEDULE = '60 MINUTE' AS CREATE OR REPLACE TABLE t6 (\n  x INT\n)");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TASK tk_ddl MODIFY AS CREATE OR REPLACE TABLE t7 (y INT)");
        assertAnswer(
            "TK_DDL|CREATE OR REPLACE TABLE t7 (y INT)",
            "SHOW TASKS LIKE 'TK_DDL' ->> SELECT \"name\", \"definition\" FROM $1");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TASK tk_ddl MODIFY AS DROP TABLE t7");
        assertAnswer(
            "TK_DDL|DROP TABLE t7",
            "SHOW TASKS LIKE 'TK_DDL' ->> SELECT \"name\", \"definition\" FROM $1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'nonsense'.",
            "ALTER TASK tk_ddl MODIFY AS nonsense words");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '<EOF>'.",
            "ALTER TASK tk_ddl MODIFY AS CREATE OR REPLACE TABLE");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TASK tk_ddl MODIFY AS SHOW TABLES");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TASK tk_ddl MODIFY AS SELECT * FROM nosuch_table");
        assertAnswer(
            "TK_CTAS2|CREATE OR REPLACE TABLE t5 AS SELECT a FROM t",
            "DESCRIBE TASK tk_ctas2 ->> SELECT \"name\", \"definition\" FROM $1");
        assertAnswer(
            "create or replace task TK_CTAS2 | \tschedule='60 MINUTE' | \tas CREATE OR REPLACE TABLE t5 AS SELECT "
                + "a FROM t;",
            "SELECT GET_DDL('TASK', 'tk_ctas2')");
        assertAnswer(
            "create or replace task TK_NL | \tschedule='60 MINUTE' | \tas CREATE OR REPLACE TABLE t6 ( |   x INT "
                + "| );",
            "SELECT GET_DDL('TASK', 'tk_nl')");
        assertAnswer(
            "REFUSED Nonexistent warehouse NOSUCH_WH_ZZQ was specified.",
            "CREATE OR REPLACE TASK tk_w WAREHOUSE = nosuch_wh_zzq SCHEDULE = '60 MINUTE' AS CREATE OR REPLACE "
                + "TABLE t6 (x INT)");
        assertAnswer(
            "Task TK_N successfully created.",
            "CREATE OR REPLACE TASK tk_n AS CREATE OR REPLACE TABLE t6 (x INT)");
        assertAnswer(
            "Task TK_AFTER successfully created.",
            "CREATE OR REPLACE TASK tk_after AFTER tk_ddl AS DROP TABLE IF EXISTS t7");
        assertAnswer(
            "Task TK_WHEN successfully created.",
            "CREATE OR REPLACE TASK tk_when SCHEDULE = '60 MINUTE' WHEN 1 = 1 AS CREATE OR REPLACE TABLE t6 (x "
                + "INT)");
        assertAnswer(
            "Task TK_INE successfully created.",
            "CREATE TASK IF NOT EXISTS tk_ine SCHEDULE = '60 MINUTE' AS TRUNCATE TABLE t");
    }

    @Test
    public void anAlertsAction() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute(
            "INSERT INTO t VALUES (1, 2)");
        assertAnswer(
            "Alert AL1 successfully created.",
            "CREATE OR REPLACE ALERT al1 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN CREATE OR REPLACE "
                + "TABLE t8 (x INT)");
        assertAnswer(
            "Alert AL2 successfully created.",
            "CREATE OR REPLACE ALERT al2 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN DROP TABLE t8");
        assertAnswer(
            "Alert AL3 successfully created.",
            "CREATE OR REPLACE ALERT al3 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN INSERT INTO t VALUES "
                + "(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 79 unexpected 'nonsense'.",
            "CREATE OR REPLACE ALERT al4 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN nonsense words");
        assertAnswer(
            "Alert AL5 successfully created.",
            "CREATE OR REPLACE ALERT al5 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN SHOW TABLES");
        assertAnswer(
            "Alert AL6 successfully created.",
            "CREATE OR REPLACE ALERT al6 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN ALTER TABLE t ADD "
                + "COLUMN c INT");
        assertAnswer(
            "Alert AL7 successfully created.",
            "CREATE OR REPLACE ALERT al7 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN CALL nosuch_proc()");
        assertAnswer(
            "Alert AL8 successfully created.",
            "CREATE OR REPLACE ALERT al8 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN SELECT 1");
        assertAnswer(
            "Alert AL9 successfully created.",
            "CREATE OR REPLACE ALERT al9 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN TRUNCATE TABLE t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 103 unexpected '<EOF>'.",
            "CREATE OR REPLACE ALERT al10 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN CREATE OR REPLACE "
                + "TABLE");
        assertAnswer(
            "Alert AL11 successfully created.",
            "CREATE OR REPLACE ALERT al11 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN SELECT * FROM "
                + "nosuch_table");
        assertAnswer(
            "Alert AL12 successfully created.",
            "CREATE OR REPLACE ALERT al12 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN BEGIN CREATE OR "
                + "REPLACE TABLE t8 (x INT); END");
        assertAnswer(
            "Alert AL13 successfully created.",
            "CREATE OR REPLACE ALERT al13 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN EXECUTE IMMEDIATE "
                + "'SELECT 1'");
        assertAnswer(
            "Alert AL14 successfully created.",
            "CREATE OR REPLACE ALERT al14 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN USE SCHEMA "
                + "test_schema");
        assertAnswer(
            "Alert AL15 successfully created.",
            "CREATE OR REPLACE ALERT al15 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN SET v = 1");
        assertAnswer(
            "Alert AL16 successfully created.",
            "CREATE OR REPLACE ALERT al16 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN CREATE VIEW v9 AS "
                + "SELECT 1 AS c");
        assertAnswer(
            "Alert AL17 successfully created.",
            "CREATE OR REPLACE ALERT al17 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN COMMIT");
        assertAnswer(
            "Alert AL18 successfully created.",
            "CREATE OR REPLACE ALERT al18 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN DESCRIBE TABLE t");
        assertAnswer(
            "Alert AL19 successfully created.",
            "CREATE OR REPLACE ALERT al19 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN DELETE FROM t");
        assertAnswer(
            "Alert AL20 successfully created.",
            "CREATE OR REPLACE ALERT al20 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN CREATE OR REPLACE "
                + "TABLE t5 AS SELECT a FROM t");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER ALERT al3 MODIFY ACTION CREATE OR REPLACE TABLE t8 (x INT)");
        assertAnswer(
            "AL3|SELECT 1|CREATE OR REPLACE TABLE t8 (x INT)",
            "SHOW ALERTS LIKE 'AL3' ->> SELECT \"name\", \"condition\", \"action\" FROM $1");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER ALERT al3 MODIFY ACTION DROP TABLE t8");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'nonsense'.",
            "ALTER ALERT al3 MODIFY ACTION nonsense words");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER ALERT al3 MODIFY ACTION SHOW TABLES");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER ALERT al3 MODIFY CONDITION EXISTS (SELECT 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'CREATE'. | syntax "
                + "error line 1 at position 64 unexpected ')'.",
            "ALTER ALERT al3 MODIFY CONDITION EXISTS (CREATE TABLE zz (x INT))");
        assertAnswer(
            "AL1|SELECT 1|CREATE OR REPLACE TABLE t8 (x INT) ;; AL11|SELECT 1|SELECT * FROM nosuch_table ;; "
                + "AL12|SELECT 1|BEGIN CREATE OR REPLACE TABLE t8 (x INT); END ;; AL13|SELECT 1|EXECUTE IMMEDIATE "
                + "'SELECT 1' ;; AL14|SELECT 1|USE SCHEMA test_schema ;; AL15|SELECT 1|SET v = 1 ;; AL16|SELECT "
                + "1|CREATE VIEW v9 AS SELECT 1 AS c ;; AL17|SELECT 1|COMMIT ;; AL18|SELECT 1|DESCRIBE TABLE t ;; "
                + "AL19|SELECT 1|DELETE FROM t ;; AL2|SELECT 1|DROP TABLE t8 ;; AL20|SELECT 1|CREATE OR REPLACE "
                + "TABLE t5 AS SELECT a FROM t ;; AL3|SELECT 2|SHOW TABLES ;; AL5|SELECT 1|SHOW TABLES ;; AL6|SELECT "
                + "1|ALTER TABLE t ADD COLUMN c INT ;; AL7|SELECT 1|CALL nosuch_proc() ;; AL8|SELECT 1|SELECT 1 ;; "
                + "AL9|SELECT 1|TRUNCATE TABLE t",
            "SHOW ALERTS ->> SELECT \"name\", \"condition\", \"action\" FROM $1 ORDER BY \"name\"");
        assertAnswer(
            "Alert ALX successfully created.",
            "CREATE OR REPLACE ALERT alx SCHEDULE = '60 MINUTE' IF (EXISTS (SHOW TABLES)) THEN SELECT 1");
        assertAnswer(
            "Alert ALZ successfully created.",
            "CREATE OR REPLACE ALERT alz SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT * FROM nosuch_table)) THEN "
                + "SELECT 1");
    }

    @Test
    public void aFaultInTheBody() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute(
            "INSERT INTO t VALUES (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 77 unexpected ')'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS CREATE TABLE t9 (x INT,)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 78 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS ALTER TABLE t ADD COLUMN");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 68 unexpected 'x'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS DROP TABLE t7 x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS TRUNCATE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS SHOW");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS USE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS GRANT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS SET");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'x'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS COMMIT x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS DROP t7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 71 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS CREATE OR REPLACE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS ALTER");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS CREATE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS EXPLAIN");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected 'DESCRIBE'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS DESCRIBE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 71 unexpected 'b'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS DROP TABLE t7 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 80 unexpected 'z'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS CREATE TABLE t9 (x INT) y z");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'x'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS INSERT INTO t VALUES (1, 2) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'y'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS DELETE FROM t WHERE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 66 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS UPDATE t SET");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 63 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS COPY INTO");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS CALL");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 71 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS EXECUTE IMMEDIATE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 84 unexpected 'y'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS CREATE TABLE t9 AS SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 68 unexpected 'RENAME'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS ALTER TABLE t RENAME TO");
        assertAnswer(
            "Task TK_E successfully created.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS USE SCHEMA");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 70 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS SHOW TABLES LIKE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS SET v =");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS UNSET");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 75 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS COMMENT ON TABLE t IS");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS DROP VIEW IF EXISTS");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 69 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS CREATE SEQUENCE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 71 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS ALTER SESSION SET");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 66 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS UNDROP TABLE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS LIST");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 80 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS GRANT SELECT ON TABLE t TO");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 71 unexpected 'x'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS TRUNCATE TABLE t x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 70 unexpected ';'.",
            "CREATE OR REPLACE TASK tk_e SCHEDULE = '60 MINUTE' AS BEGIN DROP TABLE; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ')'.",
            "CREATE TABLE t9 (x INT,)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '<EOF>'.",
            "ALTER TABLE t ADD COLUMN");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'.",
            "DROP TABLE t7 x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '<EOF>'.",
            "TRUNCATE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected '<EOF>'.",
            "SHOW");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 3 unexpected '<EOF>'.",
            "USE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected '<EOF>'.",
            "GRANT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 3 unexpected '<EOF>'.",
            "SET");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'x'.",
            "COMMIT x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '<EOF>'.",
            "DROP t7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '<EOF>'.",
            "CREATE OR REPLACE");
        assertAnswer(
            "TK_E|USE SCHEMA",
            "SHOW TASKS ->> SELECT \"name\", \"definition\" FROM $1 ORDER BY \"name\"");
        assertAnswer(
            "Task TK_C1 successfully created.",
            "CREATE OR REPLACE TASK tk_c1 SCHEDULE = '60 MINUTE' AS /* c */ CREATE OR REPLACE TABLE t6 (x INT) /* "
                + "d */");
        assertAnswer(
            "Task TK_C2 successfully created.",
            "CREATE OR REPLACE TASK tk_c2 SCHEDULE = '60 MINUTE' AS CREATE OR REPLACE TABLE t6 (x INT) -- e");
        assertAnswer(
            "Task TK_C3 successfully created.",
            "CREATE OR REPLACE TASK tk_c3 SCHEDULE = '60 MINUTE' AS\n  DROP TABLE IF EXISTS t7  ;  ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 79 unexpected 'COMMENT'.",
            "CREATE OR REPLACE TASK tk_c4 SCHEDULE = '60 MINUTE' AS DROP TABLE IF EXISTS t7 COMMENT = 'x'");
        assertAnswer(
            "Task TK_C5 successfully created.",
            "CREATE OR REPLACE TASK tk_c5 SCHEDULE = '60 MINUTE' COMMENT = 'x' AS DROP TABLE IF EXISTS t7");
        assertAnswer(
            "TK_C1|CREATE OR REPLACE TABLE t6 (x INT)| ;; TK_C2|CREATE OR REPLACE TABLE t6 (x INT)| ;; TK_C3|DROP "
                + "TABLE IF EXISTS t7| ;; TK_C5|DROP TABLE IF EXISTS t7|x",
            "SHOW TASKS LIKE 'TK_C%' ->> SELECT \"name\", \"definition\", \"comment\" FROM $1 ORDER BY \"name\"");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 90 unexpected '<EOF>'.",
            "CREATE OR REPLACE ALERT al_e SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN DROP TABLE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 106 unexpected 'z'.",
            "CREATE OR REPLACE ALERT al_e SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN CREATE TABLE t9 (x "
                + "INT) y z");
        assertAnswer(
            "REFUSED Invalid statement for task definition. Expecting one of the following: STATEMENT",
            "ALTER TASK tk_c1 MODIFY AS SELECT 1 ->> SELECT * FROM $1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'x'.",
            "ALTER TASK tk_c1 MODIFY AS DROP TABLE t7 x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '<EOF>'.",
            "ALTER ALERT al_e MODIFY ACTION DROP TABLE");
    }
}
