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
package dev.frostlake.task;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A task's body and an alert's condition, the readings that were left: a word that opens no body is refused
 * alone; a DESCRIBE as a body is read as the DESCRIBE it is; a COMMENT after the body belongs to the body and
 * is no clause of the task; an alert's condition that is no query is refused at its first word and read without
 * its brackets (live-verified). No task here is resumed.
 */
public class TaskBodyAndAlertConditionLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void aTasksBody() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'FROM'. | syntax "
                + "error line 1 at position 64 unexpected 'WHERE'. | syntax error line 1 at position 69 unexpected "
                + "'<EOF>'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS SELECT FROM WHERE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 76 unexpected 'FROM'. | syntax "
                + "error line 1 at position 80 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS CREATE VIEW v AS SELECT FROM");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'VALUES'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS VALUES (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'VALUES'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS VALUES (1), (2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'VALUES'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS VALUES 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'VALUES'.",
            "VALUES (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'VALUES'.",
            "VALUES (1), (2) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'IF'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS IF (TRUE) THEN SELECT 1; END IF");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'IF'.",
            "IF (TRUE) THEN SELECT 1; END IF");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'WHILE'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS WHILE (FALSE) DO SELECT 1; END WHILE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'RETURN'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS RETURN 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'LET'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS LET x := 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 84 unexpected 'z'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS BEGIN CREATE TABLE t9 (x INT) y z; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'z'.",
            "BEGIN CREATE TABLE t9 (x INT) y z; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ';'.",
            "BEGIN CREATE TABLE t9 (x INT) y; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'z'.",
            "CREATE TABLE t9 (x INT) y z");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'.",
            "CREATE TABLE t9 (x INT) y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 83 unexpected ';'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS BEGIN CREATE TABLE t9 (x INT) y; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 72 unexpected 'b'. | syntax error "
                + "line 1 at position 76 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS DESCRIBE TABLE t (a b) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 72 unexpected 'b'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS DESCRIBE TABLE t (a b)");
        assertAnswer(
            "Task TK successfully created.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS DESCRIBE TABLE t x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 67 unexpected 'VIEW'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS DROP TABLE t9 (VIEW)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 72 unexpected 'b'. | syntax error "
                + "line 1 at position 82 unexpected 'd'. | syntax error line 1 at position 82 unexpected 'd'. | "
                + "syntax error line 1 at position 83 unexpected ')'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS DESCRIBE TABLE t (a b) x = (c d)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 77 unexpected ')'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS CREATE TABLE t9 (x INT, y)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE TABLE t9 (x NOSUCHTYPE)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE(1, 2)'.",
            "CREATE TABLE t9 (x NOSUCHTYPE(1, 2))");
    }

    @Test
    public void anAlertsCondition() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'CREATE'. | syntax "
                + "error line 1 at position 86 unexpected ')'.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (CREATE TABLE zz (x INT))) THEN SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected 'y'.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1 x y)) THEN SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'DROP'.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (DROP TABLE zz)) THEN SELECT 1");
        assertAnswer(
            "Alert AL successfully created.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (SHOW TABLES)) THEN SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'VALUES'. | syntax "
                + "error line 1 at position 73 unexpected ')'.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (VALUES (1))) THEN SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 69 unexpected 'FROM'. | syntax "
                + "error line 1 at position 73 unexpected ')'.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT FROM)) THEN SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'CREATE'. | syntax "
                + "error line 1 at position 86 unexpected 'y'.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (CREATE TABLE zz (x INT) y)) THEN "
                + "SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected ')'.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS ()) THEN SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'CREATE'.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (CREATE VIEW vv AS SELECT 1)) THEN "
                + "SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'INSERT'. | syntax "
                + "error line 1 at position 90 unexpected ')'.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (INSERT INTO t VALUES (1, 2))) THEN "
                + "SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'DELETE'.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (DELETE FROM t)) THEN SELECT 1");
        assertAnswer(
            "Alert AL successfully created.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (CALL nosuch())) THEN SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'VALUES'.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (VALUES 1)) THEN SELECT 1");
        assertAnswer(
            "Alert AL successfully created.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (WITH c AS (SELECT 1) SELECT * FROM c)) "
                + "THEN SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'DESCRIBE'.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (DESCRIBE TABLE t)) THEN SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'TRUNCATE'.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (TRUNCATE TABLE t)) THEN SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'UPDATE'.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (UPDATE t SET a = 1)) THEN SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'CREATE'. | syntax "
                + "error line 1 at position 93 unexpected ')'.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (CREATE TABLE zz (x INT, y INT))) THEN "
                + "SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'CREATE'.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (CREATE TABLE zz AS SELECT 1)) THEN "
                + "SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'DROP'.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (DROP TABLE zz CASCADE)) THEN SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 75 unexpected ')'.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1 FROM)) THEN SELECT 1");
        assertAnswer(
            "Alert AL successfully created.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS ((SELECT 1))) THEN SELECT 1");
    }

    @Test
    public void aCommentAfterTheBody() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 69 unexpected '='.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS SELECT 1 COMMENT = 'c'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 66 unexpected 'COMMENT'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS CALL nosuch() COMMENT = 'c'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 80 unexpected 'COMMENT'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS INSERT INTO t VALUES (1, 2) COMMENT = 'c'");
        assertAnswer(
            "Task TK successfully created.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS USE SCHEMA test_schema");
        assertAnswer(
            "create or replace task TK | \tschedule='60 MINUTE' | \tas USE SCHEMA test_schema;",
            "SELECT GET_DDL('TASK', 'tk')");
        assertAnswer(
            "TK successfully dropped.",
            "DROP TASK IF EXISTS tk");
        assertAnswer(
            "Drop statement executed successfully (V already dropped).",
            "DROP VIEW IF EXISTS v");
        assertAnswer(
            "Drop statement executed successfully (T9 already dropped).",
            "DROP TABLE IF EXISTS t9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 63 unexpected 'COMMENT'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS SELECT 1 x COMMENT = 'c'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected '='.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS DELETE FROM t COMMENT = 'c'");
        assertAnswer(
            "Task TK successfully created.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS CREATE TABLE t9 (x INT) COMMENT = 'c'");
        assertAnswer(
            "create or replace task TK | \tschedule='60 MINUTE' | \tas CREATE TABLE t9 (x INT) COMMENT = 'c';",
            "SELECT GET_DDL('TASK', 'tk')");
        assertAnswer(
            "Task TK successfully created.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' COMMENT = 'c' AS SELECT 1");
        assertAnswer(
            "create or replace task TK | \tschedule='60 MINUTE' | \tCOMMENT='c' | \tas SELECT 1;",
            "SELECT GET_DDL('TASK', 'tk')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 76 unexpected '='.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS SELECT 1 FROM t COMMENT = 'c'");
    }

    @Test
    public void otherBodies() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'VALUES'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS VALUES (1) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'CASE'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS CASE WHEN TRUE THEN 1 END");
        assertAnswer(
            "Task TK successfully created.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS (SELECT 1)");
        assertAnswer(
            "Task TK successfully created.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS WITH c AS (SELECT 1) SELECT * FROM c");
        assertAnswer(
            "create or replace task TK | \tschedule='60 MINUTE' | \tas WITH c AS (SELECT 1) SELECT * FROM c;",
            "SELECT GET_DDL('TASK', 'tk')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 70 unexpected '1'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS DESCRIBE TABLE t (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 70 unexpected '<EOF>'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS DESCRIBE TABLE t x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 70 unexpected 'x'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS DROP TABLE t9 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 85 unexpected 'z'.",
            "CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS CREATE TABLE t9 (x NOSUCHTYPE) y z");
        assertAnswer(
            "TK successfully dropped.",
            "DROP TASK IF EXISTS tk");
    }
}
