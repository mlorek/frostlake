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
 * A statement that is no query, written in the parentheses a FROM clause reads a query in, is refused at its
 * first word: DESCRIBE, DESC, SHOW, EXPLAIN, CALL, USE and VALUES are names there, and what follows one is what
 * the parse stops at. A word that stands alone, or with one name after it, is the table it names
 * (live-verified).
 */
public class StatementWordInFromTest extends MeasuredCellsTestSupport {

    @Test
    public void theStatementsFirstWord() {
        try {
            answer(
                "CREATE OR REPLACE TABLE rt_ctas (a INT, b VARCHAR)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'DESCRIBE'.",
                "SELECT \"name\", \"type\" FROM (DESCRIBE TABLE rt_ctas) ORDER BY \"name\"");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'DESCRIBE'.",
                "SELECT * FROM (DESCRIBE TABLE rt_ctas)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'DESC'.",
                "SELECT * FROM (DESC TABLE rt_ctas)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'DESCRIBE' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have at least one privilege granted on TABLE DESCRIBE.",
                "SELECT * FROM (describe rt_ctas)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'SHOW'.",
                "SELECT * FROM (SHOW TABLES IN SCHEMA test_db.test_schema)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'EXPLAIN'.",
                "SELECT * FROM (EXPLAIN SELECT 1)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'CALL'.",
                "SELECT * FROM (CALL nosuch_proc_zq7())");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'SHOW'.",
                "SELECT * FROM (SHOW TABLES IN SCHEMA test_db.test_schema) t");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'DESCRIBE' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have at least one privilege granted on TABLE DESCRIBE.",
                "SELECT * FROM (DESCRIBE)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'SET'.",
                "SELECT * FROM (SET zq7 = 1)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'USE'.",
                "SELECT * FROM (USE SCHEMA test_db.test_schema)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ')'.",
                "SELECT * FROM (WITH)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'VALUES'.",
                "SELECT * FROM (VALUES)");
            assertAnswer(
                "",
                "SELECT * FROM (rt_ctas)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'DESCRIBE'.",
                "SELECT * FROM (DESCRIBE TABLE rt_ctas) d JOIN rt_ctas r ON TRUE");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'DESCRIBE'.",
                "SELECT * FROM rt_ctas, (DESCRIBE TABLE rt_ctas)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 2 at position 0 unexpected 'DESCRIBE'.",
                "SELECT * FROM (\nDESCRIBE TABLE rt_ctas)");
        } finally {
            answer(
                "UNSET zq7");
            answer(
                "DROP TABLE IF EXISTS rt_x");
            answer(
                "DROP TABLE IF EXISTS rt_y");
        }
    }

    @Test
    public void otherClausesAndOtherStatements() {
        try {
            answer(
                "CREATE OR REPLACE TABLE rt_ctas (a INT, b VARCHAR)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SHOW' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE SHOW.",
                "SELECT * FROM (SHOW)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'DELETE'.",
                "SELECT * FROM (DELETE FROM rt_ctas)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'CREATE'.",
                "SELECT * FROM (CREATE TABLE rt_y (a INT))");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'DROP'.",
                "SELECT * FROM (DROP TABLE rt_ctas)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TRUNCATE' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have at least one privilege granted on TABLE TRUNCATE.",
                "SELECT * FROM (TRUNCATE rt_ctas)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'COMMENT' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE COMMENT.",
                "SELECT * FROM (COMMENT)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'LIST' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE LIST.",
                "SELECT * FROM (LIST)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'DESCRIBE'.",
                "SELECT * FROM (DESCRIBE TABLE rt_ctas");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM rt_ctas");
        } finally {
            answer(
                "UNSET zq7");
            answer(
                "DROP TABLE IF EXISTS rt_x");
            answer(
                "DROP TABLE IF EXISTS rt_y");
        }
    }

    @Test
    public void moreStatementsAndPlaces() {
        answer(
            "CREATE OR REPLACE TABLE rt_ctas (a INT, b VARCHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'DESCRIBE'.",
            "SELECT * FROM (DESCRIBE VIEW rt_ctas)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'SHOW'.",
            "SELECT * FROM (SHOW SCHEMAS IN DATABASE test_db)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'CALL'.",
            "SELECT * FROM (CALL a.b())");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'USE'.",
            "SELECT * FROM (USE DATABASE test_db)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'VALUES'.",
            "SELECT * FROM (VALUES 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'describe'.",
            "SELECT * FROM (describe table rt_ctas)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'DESCRIBE'.",
            "SELECT * FROM (DESCRIBE TABLE rt_ctas) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'SHOW'.",
            "SELECT * FROM (SHOW TABLES IN SCHEMA test_db.test_schema) LIMIT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'DESC' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE DESC.",
            "SELECT * FROM (DESC rt_ctas)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'USE' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE USE.",
            "SELECT * FROM (USE s)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'EXPLAIN' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE EXPLAIN.",
            "SELECT * FROM (EXPLAIN)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'CALL' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE CALL.",
            "SELECT * FROM (CALL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'SHOW'.",
            "SELECT * FROM (SHOW x y z)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'DESCRIBE'.",
            "SELECT * FROM (DESCRIBE TABLE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'DESCRIBE'.",
            "SELECT * FROM (DESCRIBE TABLE rt_ctas), rt_ctas");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'VALUES'.",
            "SELECT * FROM (VALUES) v");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'EXPLAIN'.",
            "SELECT * FROM (EXPLAIN SELECT 1) e WHERE 1 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'DESCRIBE'.",
            "SELECT * FROM (DESCRIBE rt_ctas x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'SHOW'.",
            "SELECT * FROM (SHOW TABLES LIKE 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'USE' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE USE.",
            "SELECT * FROM (USE)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'DESC' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE DESC.",
            "SELECT * FROM (DESC)");
        assertAnswer(
            "1",
            "SELECT * FROM (VALUES (1)) v");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM rt_ctas");
    }
}
