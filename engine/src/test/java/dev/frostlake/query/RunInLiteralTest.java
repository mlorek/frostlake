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
 * A number or a string run in after a complete statement, and whether the statements after it are read.
 * They are read after a value — an operand of a WHERE, a HAVING, a QUALIFY, a GROUP BY or ORDER BY item, a
 * select item, an assigned value — after a FROM list's table, alias, derived table or table function, after
 * a DROP's name, a SHOW's one-word kind or the name its scope ends in, a DESCRIBE's name, an UNSET's
 * name, BEGIN WORK or TRANSACTION and a plain CREATE DATABASE or SCHEMA's name. A number alone is read
 * past after a single unqualified name ending a USE, a TRUNCATE, an UNDROP, a GRANT's or REVOKE's role or
 * user, a transaction's NAME, an EXECUTE TASK or a DROP STAGE, and — its line reported twice — after the
 * name a CREATE ROLE, SEQUENCE or STAGE ends in, a CREATE TABLE's LIKE or CLONE source and an ALTER SESSION
 * SET's value. Everywhere else the report ends at the literal: after a CASCADE, a signature, a call's or a
 * VALUES row's parenthesis, a LIMIT's count, a sort direction, an EXISTS, a SAMPLE, a USING list, COMMIT,
 * an alias of a select item, a DELETE's target. After a join's ON condition the ON is refused instead. A
 * word in the same places is listed beside each literal. Every answer is live-verified.
 */
public class RunInLiteralTest extends MeasuredCellsTestSupport {

    @Test
    public void theLiteralEndsTheReportOrLetsItGoOn() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '1'.",
            "DROP TABLE t CASCADE 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ''q''.",
            "DROP TABLE t CASCADE 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '1.5'.",
            "DROP TABLE t CASCADE 1.5; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '1'.",
            "DROP FUNCTION f(INT) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ''q''.",
            "DROP STAGE s 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '1'.",
            "SHOW TABLES LIKE 'a' 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ''b''.",
            "SHOW TABLES LIKE 'a' 'b'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ''b''.",
            "SHOW TABLES STARTS WITH 'a' 'b'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '2'.",
            "SHOW TABLES LIMIT 1 2; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'.",
            "SHOW MATERIALIZED VIEWS 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected ''q''.",
            "SHOW x 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '1'.",
            "UNSET (x) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ''q''.",
            "USE SCHEMA s 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'.",
            "USE SCHEMA s.t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '1'.",
            "COMMIT 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected ''q''.",
            "COMMIT 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ''q''.",
            "TRUNCATE TABLE t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '2'.",
            "SELECT 1 FROM t LIMIT 1 2; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected ''q''.",
            "SELECT 1 FROM t LIMIT 1 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '1'.",
            "CALL p() 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected ''q''.",
            "CALL p() 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ''q''.",
            "SELECT 1 FROM t WHERE EXISTS (SELECT 1) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '1'.",
            "SELECT 1 FROM t WHERE NOT EXISTS (SELECT 1) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '1'.",
            "SELECT 1 INTO t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ''q''. | syntax "
                + "error line 1 at position 44 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = 1 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '2'. | syntax "
                + "error line 1 at position 42 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = 1 2; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ''q''. | syntax "
                + "error line 1 at position 47 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a IN (1) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax "
                + "error line 1 at position 46 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a IS NULL 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'. | syntax "
                + "error line 1 at position 41 unexpected 'y'.",
            "SELECT 1 FROM t GROUP BY a 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '1'. | syntax "
                + "error line 1 at position 53 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = 1 ORDER BY a 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '2'. | syntax "
                + "error line 1 at position 33 unexpected 'y'.",
            "UPDATE t SET a = 1 2; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '2'.",
            "INSERT INTO t VALUES (1) 2; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '1'. | syntax "
                + "error line 1 at position 30 unexpected 'y'.",
            "SELECT 1 FROM t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected '1'. | syntax "
                + "error line 1 at position 20 unexpected 'y'.",
            "USE s 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'. | syntax "
                + "error line 1 at position 29 unexpected 'y'.",
            "USE DATABASE d 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '1'. | syntax "
                + "error line 1 at position 27 unexpected 'y'.",
            "USE SCHEMA s 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '1'. | syntax "
                + "error line 1 at position 25 unexpected 'y'.",
            "USE ROLE r 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '1'. | syntax "
                + "error line 1 at position 30 unexpected 'y'.",
            "USE WAREHOUSE w 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '1'. | syntax "
                + "error line 1 at position 31 unexpected 'y'.",
            "TRUNCATE TABLE t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'. | syntax "
                + "error line 1 at position 29 unexpected 'y'.",
            "UNDROP TABLE t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '1'. | syntax "
                + "error line 1 at position 37 unexpected 'y'.",
            "GRANT ROLE r TO ROLE s 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '1'. | syntax "
                + "error line 1 at position 27 unexpected 'y'.",
            "DROP STAGE s 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '1'. | syntax "
                + "error line 1 at position 27 unexpected 'y'.",
            "DROP TABLE t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ''q''. | syntax "
                + "error line 1 at position 29 unexpected 'y'.",
            "DROP TABLE t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'. | syntax "
                + "error line 1 at position 29 unexpected 'y'.",
            "DROP TABLE t.u 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected ''q''. | syntax "
                + "error line 1 at position 28 unexpected 'y'.",
            "DROP VIEW v 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '1'. | syntax "
                + "error line 1 at position 26 unexpected 'y'.",
            "DROP VIEW v 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '1'. | syntax "
                + "error line 1 at position 28 unexpected 'y'.",
            "DROP SCHEMA s 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected ''q''. | syntax "
                + "error line 1 at position 30 unexpected 'y'.",
            "DROP SCHEMA s 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ''q''. | syntax "
                + "error line 1 at position 32 unexpected 'y'.",
            "DROP DATABASE d 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ''q''.",
            "USE DATABASE d 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected ''q''.",
            "USE ROLE r 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ''q''.",
            "USE WAREHOUSE w 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected ''q''.",
            "USE s 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '1'.",
            "TRUNCATE TABLE t.u 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ''q''.",
            "UNDROP TABLE t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '1'. | syntax "
                + "error line 1 at position 26 unexpected 'y'.",
            "SHOW TABLES 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected ''q''. | syntax "
                + "error line 1 at position 28 unexpected 'y'.",
            "SHOW TABLES 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '1'.",
            "COMMIT WORK 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '1'.",
            "ROLLBACK 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '3'.",
            "SELECT 1 FROM t LIMIT 1 OFFSET 2 3; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '2'.",
            "SELECT 1 FROM t ORDER BY a LIMIT 1 2; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '2'.",
            "CALL p(1) 2; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ''q''.",
            "SELECT 1 INTO t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '1'. | syntax "
                + "error line 1 at position 14 unexpected '1'.",
            "DELETE FROM t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '2'. | syntax "
                + "error line 1 at position 40 unexpected 'y'.",
            "DELETE FROM t WHERE a = 1 2; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '1'. | syntax "
                + "error line 1 at position 37 unexpected 'y'.",
            "DROP TABLE IF EXISTS t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'RENAME'. | "
                + "syntax error line 1 at position 40 unexpected 'y'.",
            "ALTER TABLE t RENAME TO u 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '1'.",
            "CREATE TABLE t (a INT) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1'.",
            "COMMENT ON TABLE t IS 'x' 1; SELECT 1 x y");
    }

    @Test
    public void aNumberAStringAndAWordAfterEachStatement() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '1'.",
            "DROP FUNCTION f(INT) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ''q''.",
            "DROP FUNCTION f(INT) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'zz'.",
            "DROP FUNCTION f(INT) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'. | syntax "
                + "error line 1 at position 29 unexpected 'y'.",
            "DROP ROLE zz_r 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ''q''. | syntax "
                + "error line 1 at position 31 unexpected 'y'.",
            "DROP ROLE zz_r 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'zz'. | syntax "
                + "error line 1 at position 30 unexpected 'y'.",
            "DROP ROLE zz_r zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '1'. | syntax "
                + "error line 1 at position 34 unexpected 'y'.",
            "DROP WAREHOUSE zz_w 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ''q''. | syntax "
                + "error line 1 at position 36 unexpected 'y'.",
            "DROP WAREHOUSE zz_w 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'zz'. | syntax "
                + "error line 1 at position 35 unexpected 'y'.",
            "DROP WAREHOUSE zz_w zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '1'. | syntax "
                + "error line 1 at position 30 unexpected 'y'.",
            "DROP SEQUENCE q 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ''q''. | syntax "
                + "error line 1 at position 32 unexpected 'y'.",
            "DROP SEQUENCE q 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'zz'. | syntax "
                + "error line 1 at position 31 unexpected 'y'.",
            "DROP SEQUENCE q zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'.",
            "DROP STAGE s.t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ''q''.",
            "DROP STAGE s.t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'zz'.",
            "DROP STAGE s.t zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'. | syntax "
                + "error line 1 at position 29 unexpected 'y'.",
            "DROP STAGE \"s\" 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ''q''.",
            "DROP STAGE \"s\" 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'zz'.",
            "DROP STAGE \"s\" zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '1'. | syntax "
                + "error line 1 at position 31 unexpected 'y'.",
            "DROP TABLE a.b.c 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ''q''. | syntax "
                + "error line 1 at position 33 unexpected 'y'.",
            "DROP TABLE a.b.c 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'zz'. | syntax "
                + "error line 1 at position 32 unexpected 'y'.",
            "DROP TABLE a.b.c zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '1'.",
            "DROP TABLE t RESTRICT 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ''q''.",
            "DROP TABLE t RESTRICT 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'zz'.",
            "DROP TABLE t RESTRICT zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '1'.",
            "DROP PROCEDURE p() 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ''q''.",
            "DROP PROCEDURE p() 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'zz'.",
            "DROP PROCEDURE p() zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'. | syntax "
                + "error line 1 at position 29 unexpected 'y'.",
            "DROP STREAM st 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ''q''. | syntax "
                + "error line 1 at position 31 unexpected 'y'.",
            "DROP STREAM st 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'zz'. | syntax "
                + "error line 1 at position 30 unexpected 'y'.",
            "DROP STREAM st zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '1'. | syntax "
                + "error line 1 at position 27 unexpected 'y'.",
            "DROP TASK tk 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ''q''. | syntax "
                + "error line 1 at position 29 unexpected 'y'.",
            "DROP TASK tk 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'zz'. | syntax "
                + "error line 1 at position 28 unexpected 'y'.",
            "DROP TASK tk zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '1'. | syntax "
                + "error line 1 at position 34 unexpected 'y'.",
            "DROP FILE FORMAT ff 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ''q''. | syntax "
                + "error line 1 at position 36 unexpected 'y'.",
            "DROP FILE FORMAT ff 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'zz'. | syntax "
                + "error line 1 at position 35 unexpected 'y'.",
            "DROP FILE FORMAT ff zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'. | syntax "
                + "error line 1 at position 29 unexpected 'y'.",
            "DROP USER zz_u 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ''q''. | syntax "
                + "error line 1 at position 31 unexpected 'y'.",
            "DROP USER zz_u 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'zz'. | syntax "
                + "error line 1 at position 30 unexpected 'y'.",
            "DROP USER zz_u zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'.",
            "USE SCHEMA s.t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ''q''.",
            "USE SCHEMA s.t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'zz'.",
            "USE SCHEMA s.t zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'. | syntax "
                + "error line 1 at position 29 unexpected 'y'.",
            "USE SCHEMA \"s\" 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ''q''.",
            "USE SCHEMA \"s\" 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'zz'.",
            "USE SCHEMA \"s\" zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '1'. | syntax "
                + "error line 1 at position 27 unexpected 'y'.",
            "USE ROLE \"r\" 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ''q''.",
            "USE ROLE \"r\" 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'zz'.",
            "USE ROLE \"r\" zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'.",
            "USE SECONDARY ROLES ALL 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected ''q''.",
            "USE SECONDARY ROLES ALL 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'zz'.",
            "USE SECONDARY ROLES ALL zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '1'. | syntax "
                + "error line 1 at position 39 unexpected 'y'.",
            "USE SECONDARY ROLES NONE 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ''q''.",
            "USE SECONDARY ROLES NONE 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'zz'.",
            "USE SECONDARY ROLES NONE zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '1'. | syntax "
                + "error line 1 at position 25 unexpected 'y'.",
            "TRUNCATE t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected ''q''.",
            "TRUNCATE t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'zz'.",
            "TRUNCATE t zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'. | syntax "
                + "error line 1 at position 41 unexpected 'y'.",
            "TRUNCATE TABLE IF EXISTS t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ''q''.",
            "TRUNCATE TABLE IF EXISTS t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'zz'.",
            "TRUNCATE TABLE IF EXISTS t zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '1'. | syntax "
                + "error line 1 at position 33 unexpected 'y'.",
            "TRUNCATE TABLE \"t\" 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ''q''.",
            "TRUNCATE TABLE \"t\" 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'zz'.",
            "TRUNCATE TABLE \"t\" zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '1'.",
            "TRUNCATE TABLE t.u 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ''q''.",
            "TRUNCATE TABLE t.u 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'zz'.",
            "TRUNCATE TABLE t.u zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '1'. | syntax "
                + "error line 1 at position 30 unexpected 'y'.",
            "UNDROP SCHEMA s 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ''q''.",
            "UNDROP SCHEMA s 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'zz'.",
            "UNDROP SCHEMA s zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'. | syntax "
                + "error line 1 at position 32 unexpected 'y'.",
            "UNDROP DATABASE d 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ''q''.",
            "UNDROP DATABASE d 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'zz'.",
            "UNDROP DATABASE d zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '1'.",
            "UNDROP TABLE t.u 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ''q''.",
            "UNDROP TABLE t.u 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'zz'.",
            "UNDROP TABLE t.u zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '1'. | syntax "
                + "error line 1 at position 37 unexpected 'y'.",
            "GRANT ROLE r TO ROLE s 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ''q''.",
            "GRANT ROLE r TO ROLE s 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'zz'.",
            "GRANT ROLE r TO ROLE s zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '1'. | syntax "
                + "error line 1 at position 37 unexpected 'y'.",
            "GRANT ROLE r TO USER u 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ''q''.",
            "GRANT ROLE r TO USER u 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'zz'.",
            "GRANT ROLE r TO USER u zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '1'. | syntax "
                + "error line 1 at position 48 unexpected 'y'.",
            "GRANT SELECT ON TABLE t TO ROLE r 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ''q''.",
            "GRANT SELECT ON TABLE t TO ROLE r 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'zz'.",
            "GRANT SELECT ON TABLE t TO ROLE r zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1'. | syntax "
                + "error line 1 at position 40 unexpected 'y'.",
            "REVOKE ROLE r FROM ROLE s 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''q''.",
            "REVOKE ROLE r FROM ROLE s 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'zz'.",
            "REVOKE ROLE r FROM ROLE s zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '1'. | syntax "
                + "error line 1 at position 51 unexpected 'y'.",
            "REVOKE SELECT ON TABLE t FROM ROLE r 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ''q''.",
            "REVOKE SELECT ON TABLE t FROM ROLE r 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '1'. | syntax "
                + "error line 1 at position 48 unexpected 'y'.",
            "GRANT USAGE ON SCHEMA s TO ROLE r 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ''q''.",
            "GRANT USAGE ON SCHEMA s TO ROLE r 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'zz'.",
            "GRANT USAGE ON SCHEMA s TO ROLE r zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '1'. | syntax "
                + "error line 1 at position 51 unexpected 'y'.",
            "GRANT OWNERSHIP ON TABLE t TO ROLE r 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ''q''.",
            "GRANT OWNERSHIP ON TABLE t TO ROLE r 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'zz'.",
            "GRANT OWNERSHIP ON TABLE t TO ROLE r zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1'.",
            "COMMENT ON TABLE t IS 'x' 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''q''.",
            "COMMENT ON TABLE t IS 'x' 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'zz'.",
            "COMMENT ON TABLE t IS 'x' zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'.",
            "COMMENT ON COLUMN t.a IS 'x' 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ''q''.",
            "COMMENT ON COLUMN t.a IS 'x' 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'zz'.",
            "COMMENT ON COLUMN t.a IS 'x' zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'.",
            "CREATE TABLE t2 (a INT) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected ''q''.",
            "CREATE TABLE t2 (a INT) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ';'. | syntax "
                + "error line 1 at position 39 unexpected 'y'.",
            "CREATE TABLE t2 (a INT) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '1'. | syntax "
                + "error line 1 at position 31 unexpected 'y'.",
            "CREATE SCHEMA s2 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ''q''. | syntax "
                + "error line 1 at position 33 unexpected 'y'.",
            "CREATE SCHEMA s2 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ';'. | syntax "
                + "error line 1 at position 32 unexpected 'y'.",
            "CREATE SCHEMA s2 zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '1'. | syntax "
                + "error line 1 at position 33 unexpected 'y'.",
            "CREATE DATABASE d2 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ''q''. | syntax "
                + "error line 1 at position 35 unexpected 'y'.",
            "CREATE DATABASE d2 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ';'. | syntax "
                + "error line 1 at position 34 unexpected 'y'.",
            "CREATE DATABASE d2 zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'. | syntax "
                + "error line 1 at position 15 unexpected '1'. | syntax error line 1 at position 29 unexpected 'y'.",
            "CREATE ROLE r2 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ''q''.",
            "CREATE ROLE r2 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ';'. | syntax "
                + "error line 1 at position 30 unexpected 'y'.",
            "CREATE ROLE r2 zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '1'. | syntax "
                + "error line 1 at position 19 unexpected '1'. | syntax error line 1 at position 33 unexpected 'y'.",
            "CREATE SEQUENCE q2 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ''q''.",
            "CREATE SEQUENCE q2 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ';'. | syntax "
                + "error line 1 at position 34 unexpected 'y'.",
            "CREATE SEQUENCE q2 zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '1'. | syntax "
                + "error line 1 at position 16 unexpected '1'. | syntax error line 1 at position 30 unexpected 'y'.",
            "CREATE STAGE s2 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ''q''.",
            "CREATE STAGE s2 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ';'. | syntax "
                + "error line 1 at position 31 unexpected 'y'.",
            "CREATE STAGE s2 zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'.",
            "CREATE TABLE t2 AS SELECT 1 c 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''q''.",
            "CREATE TABLE t2 AS SELECT 1 c 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'zz'.",
            "CREATE TABLE t2 AS SELECT 1 c zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '1'. | syntax "
                + "error line 1 at position 48 unexpected 'y'.",
            "CREATE VIEW v2 AS SELECT a FROM t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ''q''. | syntax "
                + "error line 1 at position 50 unexpected 'y'.",
            "CREATE VIEW v2 AS SELECT a FROM t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'y'.",
            "CREATE VIEW v2 AS SELECT a FROM t zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '1'. | syntax "
                + "error line 1 at position 23 unexpected '1'. | syntax error line 1 at position 37 unexpected 'y'.",
            "CREATE TABLE t2 LIKE t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ''q''.",
            "CREATE TABLE t2 LIKE t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ';'. | syntax "
                + "error line 1 at position 38 unexpected 'y'.",
            "CREATE TABLE t2 LIKE t zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'. | syntax "
                + "error line 1 at position 24 unexpected '1'. | syntax error line 1 at position 38 unexpected 'y'.",
            "CREATE TABLE t2 CLONE t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected ''q''.",
            "CREATE TABLE t2 CLONE t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ';'. | syntax "
                + "error line 1 at position 39 unexpected 'y'.",
            "CREATE TABLE t2 CLONE t zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '1'.",
            "ALTER TABLE t ADD COLUMN c INT 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ''q''.",
            "ALTER TABLE t ADD COLUMN c INT 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'zz'.",
            "ALTER TABLE t ADD COLUMN c INT zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '1'.",
            "ALTER TABLE t DROP COLUMN c 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ''q''.",
            "ALTER TABLE t DROP COLUMN c 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'zz'.",
            "ALTER TABLE t DROP COLUMN c zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'RENAME'. | "
                + "syntax error line 1 at position 49 unexpected 'y'.",
            "ALTER TABLE t RENAME COLUMN a TO z 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'RENAME'. | "
                + "syntax error line 1 at position 51 unexpected 'y'.",
            "ALTER TABLE t RENAME COLUMN a TO z 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'RENAME'. | "
                + "syntax error line 1 at position 50 unexpected 'y'.",
            "ALTER TABLE t RENAME COLUMN a TO z zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected '1'. | syntax "
                + "error line 1 at position 56 unexpected '1'. | syntax error line 1 at position 70 unexpected 'y'.",
            "ALTER SESSION SET QUOTED_IDENTIFIERS_IGNORE_CASE = TRUE 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ''q''.",
            "ALTER SESSION SET QUOTED_IDENTIFIERS_IGNORE_CASE = TRUE 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected ';'. | syntax "
                + "error line 1 at position 71 unexpected 'y'.",
            "ALTER SESSION SET QUOTED_IDENTIFIERS_IGNORE_CASE = TRUE zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'.",
            "ALTER TABLE t SET COMMENT = 'x' 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ''q''.",
            "ALTER TABLE t SET COMMENT = 'x' 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ';'. | syntax "
                + "error line 1 at position 47 unexpected 'y'.",
            "ALTER TABLE t SET COMMENT = 'x' zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '1'.",
            "ALTER SESSION UNSET QUOTED_IDENTIFIERS_IGNORE_CASE 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected ''q''.",
            "ALTER SESSION UNSET QUOTED_IDENTIFIERS_IGNORE_CASE 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'zz'.",
            "ALTER SESSION UNSET QUOTED_IDENTIFIERS_IGNORE_CASE zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '1'. | syntax "
                + "error line 1 at position 31 unexpected 'y'.",
            "DESCRIBE TABLE t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ''q''. | syntax "
                + "error line 1 at position 33 unexpected 'y'.",
            "DESCRIBE TABLE t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '1'. | syntax "
                + "error line 1 at position 27 unexpected 'y'.",
            "DESC TABLE t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ''q''. | syntax "
                + "error line 1 at position 29 unexpected 'y'.",
            "DESC TABLE t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '1'. | syntax "
                + "error line 1 at position 30 unexpected 'y'.",
            "DESCRIBE VIEW v 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ''q''. | syntax "
                + "error line 1 at position 32 unexpected 'y'.",
            "DESCRIBE VIEW v 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'. | syntax "
                + "error line 1 at position 38 unexpected 'y'.",
            "SHOW TABLES IN SCHEMA s 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected ''q''. | syntax "
                + "error line 1 at position 40 unexpected 'y'.",
            "SHOW TABLES IN SCHEMA s 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'zz'.",
            "SHOW TABLES IN SCHEMA s zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '1'. | syntax "
                + "error line 1 at position 31 unexpected 'y'.",
            "SHOW TABLES IN s 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ''q''. | syntax "
                + "error line 1 at position 33 unexpected 'y'.",
            "SHOW TABLES IN s 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'y'.",
            "SHOW TABLES IN s zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'. | syntax "
                + "error line 1 at position 32 unexpected 'y'.",
            "SHOW TERSE TABLES 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ''q''. | syntax "
                + "error line 1 at position 34 unexpected 'y'.",
            "SHOW TERSE TABLES 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'zz'. | syntax "
                + "error line 1 at position 33 unexpected 'y'.",
            "SHOW TERSE TABLES zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '1'. | syntax "
                + "error line 1 at position 27 unexpected 'y'.",
            "SHOW SCHEMAS 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ''q''. | syntax "
                + "error line 1 at position 29 unexpected 'y'.",
            "SHOW SCHEMAS 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'zz'. | syntax "
                + "error line 1 at position 28 unexpected 'y'.",
            "SHOW SCHEMAS zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '1'. | syntax "
                + "error line 1 at position 30 unexpected 'y'.",
            "SHOW PARAMETERS 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ''q''. | syntax "
                + "error line 1 at position 32 unexpected 'y'.",
            "SHOW PARAMETERS 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'zz'. | syntax "
                + "error line 1 at position 31 unexpected 'y'.",
            "SHOW PARAMETERS zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'. | syntax "
                + "error line 1 at position 38 unexpected 'y'.",
            "SHOW COLUMNS IN TABLE t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected ''q''. | syntax "
                + "error line 1 at position 40 unexpected 'y'.",
            "SHOW COLUMNS IN TABLE t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'zz'.",
            "SHOW COLUMNS IN TABLE t zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '1'. | syntax "
                + "error line 1 at position 36 unexpected 'y'.",
            "SHOW GRANTS TO ROLE r 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ''q''. | syntax "
                + "error line 1 at position 38 unexpected 'y'.",
            "SHOW GRANTS TO ROLE r 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'zz'.",
            "SHOW GRANTS TO ROLE r zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '1'.",
            "SHOW TABLES HISTORY 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ''q''.",
            "SHOW TABLES HISTORY 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'zz'.",
            "SHOW TABLES HISTORY zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'. | syntax "
                + "error line 1 at position 29 unexpected 'y'.",
            "SHOW VARIABLES 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ''q''. | syntax "
                + "error line 1 at position 31 unexpected 'y'.",
            "SHOW VARIABLES 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'zz'. | syntax "
                + "error line 1 at position 30 unexpected 'y'.",
            "SHOW VARIABLES zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '1'. | syntax "
                + "error line 1 at position 47 unexpected 'y'.",
            "SHOW TABLES LIKE 'a' IN SCHEMA s 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ''q''. | syntax "
                + "error line 1 at position 49 unexpected 'y'.",
            "SHOW TABLES LIKE 'a' IN SCHEMA s 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'zz'.",
            "SHOW TABLES LIKE 'a' IN SCHEMA s zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '1'. | syntax "
                + "error line 1 at position 26 unexpected 'y'.",
            "SHOW GRANTS 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected ''q''. | syntax "
                + "error line 1 at position 28 unexpected 'y'.",
            "SHOW GRANTS 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'zz'. | syntax "
                + "error line 1 at position 27 unexpected 'y'.",
            "SHOW GRANTS zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'.",
            "SHOW PRIMARY KEYS 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ''q''.",
            "SHOW PRIMARY KEYS 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'zz'.",
            "SHOW PRIMARY KEYS zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'.",
            "SHOW FILE FORMATS 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ''q''.",
            "SHOW FILE FORMATS 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'zz'.",
            "SHOW FILE FORMATS zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '1'.",
            "SHOW USER FUNCTIONS 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ''q''.",
            "SHOW USER FUNCTIONS 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'zz'.",
            "SHOW USER FUNCTIONS zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '1'. | syntax "
                + "error line 1 at position 24 unexpected 'y'.",
            "SET x = 1 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ''q''. | syntax "
                + "error line 1 at position 26 unexpected 'y'.",
            "SET x = 1 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'zz'.",
            "SET x = 1 zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '1'. | syntax "
                + "error line 1 at position 26 unexpected 'y'.",
            "SET x = 'a' 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected ''q''. | syntax "
                + "error line 1 at position 28 unexpected 'y'.",
            "SET x = 'a' 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'zz'.",
            "SET x = 'a' zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '1'.",
            "SET (x, y) = (1, 2) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ''q''.",
            "SET (x, y) = (1, 2) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'zz'.",
            "SET (x, y) = (1, 2) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'. | syntax "
                + "error line 1 at position 22 unexpected 'y'.",
            "UNSET x 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected ''q''. | syntax "
                + "error line 1 at position 24 unexpected 'y'.",
            "UNSET x 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'zz'. | syntax "
                + "error line 1 at position 23 unexpected 'y'.",
            "UNSET x zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '1'.",
            "UNSET (x) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ''q''.",
            "UNSET (x) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'zz'.",
            "UNSET (x) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '1'.",
            "UNSET (x, y) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ''q''.",
            "UNSET (x, y) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'zz'.",
            "UNSET (x, y) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '1'.",
            "COMMIT 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected ''q''.",
            "COMMIT 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'zz'.",
            "COMMIT zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '1'.",
            "ROLLBACK 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected ''q''.",
            "ROLLBACK 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'zz'.",
            "ROLLBACK zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '1'.",
            "COMMIT WORK 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected ''q''.",
            "COMMIT WORK 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'zz'.",
            "COMMIT WORK zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '1'.",
            "ROLLBACK WORK 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected ''q''.",
            "ROLLBACK WORK 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'zz'.",
            "ROLLBACK WORK zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '1'. | syntax "
                + "error line 1 at position 25 unexpected 'y'.",
            "BEGIN WORK 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected ''q''. | syntax "
                + "error line 1 at position 27 unexpected 'y'.",
            "BEGIN WORK 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'zz'. | syntax "
                + "error line 1 at position 26 unexpected 'y'.",
            "BEGIN WORK zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'. | syntax "
                + "error line 1 at position 32 unexpected 'y'.",
            "BEGIN TRANSACTION 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ''q''. | syntax "
                + "error line 1 at position 34 unexpected 'y'.",
            "BEGIN TRANSACTION 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'zz'. | syntax "
                + "error line 1 at position 33 unexpected 'y'.",
            "BEGIN TRANSACTION zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'.",
            "START TRANSACTION 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ''q''.",
            "START TRANSACTION 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'zz'.",
            "START TRANSACTION zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '1'. | syntax "
                + "error line 1 at position 27 unexpected 'y'.",
            "BEGIN NAME n 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ''q''.",
            "BEGIN NAME n 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'zz'.",
            "BEGIN NAME n zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '1'. | syntax "
                + "error line 1 at position 39 unexpected 'y'.",
            "BEGIN TRANSACTION NAME n 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ''q''.",
            "BEGIN TRANSACTION NAME n 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'zz'.",
            "BEGIN TRANSACTION NAME n zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '1'. | syntax "
                + "error line 1 at position 39 unexpected 'y'.",
            "START TRANSACTION NAME n 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ''q''.",
            "START TRANSACTION NAME n 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'zz'.",
            "START TRANSACTION NAME n zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '1'.",
            "INSERT INTO t VALUES (1) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ''q''.",
            "INSERT INTO t VALUES (1) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'zz'.",
            "INSERT INTO t VALUES (1) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'.",
            "INSERT INTO t (a) VALUES (1) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ''q''.",
            "INSERT INTO t (a) VALUES (1) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'zz'.",
            "INSERT INTO t (a) VALUES (1) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'.",
            "INSERT INTO t VALUES (1), (2) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''q''.",
            "INSERT INTO t VALUES (1), (2) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'zz'.",
            "INSERT INTO t VALUES (1), (2) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '1'. | syntax "
                + "error line 1 at position 37 unexpected 'y'.",
            "INSERT INTO t SELECT 1 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ''q''. | syntax "
                + "error line 1 at position 39 unexpected 'y'.",
            "INSERT INTO t SELECT 1 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'y'.",
            "INSERT INTO t SELECT 1 zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'. | syntax "
                + "error line 1 at position 44 unexpected 'y'.",
            "INSERT INTO t SELECT a FROM t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''q''. | syntax "
                + "error line 1 at position 46 unexpected 'y'.",
            "INSERT INTO t SELECT a FROM t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'y'.",
            "INSERT INTO t SELECT a FROM t zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '1'.",
            "INSERT OVERWRITE INTO t VALUES (1) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ''q''.",
            "INSERT OVERWRITE INTO t VALUES (1) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'zz'.",
            "INSERT OVERWRITE INTO t VALUES (1) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '1'. | syntax "
                + "error line 1 at position 14 unexpected '1'.",
            "DELETE FROM t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected ''q''.",
            "DELETE FROM t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'.",
            "DELETE FROM t zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '1'.",
            "DELETE FROM t x 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ''q''.",
            "DELETE FROM t x 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'zz'.",
            "DELETE FROM t x zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1'. | syntax "
                + "error line 1 at position 40 unexpected 'y'.",
            "DELETE FROM t WHERE a = 1 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''q''. | syntax "
                + "error line 1 at position 42 unexpected 'y'.",
            "DELETE FROM t WHERE a = 1 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'zz'.",
            "DELETE FROM t WHERE a = 1 zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'y'.",
            "DELETE FROM t USING n zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '1'. | syntax "
                + "error line 1 at position 52 unexpected 'y'.",
            "DELETE FROM t USING n WHERE t.a = n.a 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ''q''. | syntax "
                + "error line 1 at position 54 unexpected 'y'.",
            "DELETE FROM t USING n WHERE t.a = n.a 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'zz'.",
            "DELETE FROM t USING n WHERE t.a = n.a zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '1'. | syntax "
                + "error line 1 at position 45 unexpected 'y'.",
            "UPDATE t SET a = 1 WHERE a = 2 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ''q''. | syntax "
                + "error line 1 at position 47 unexpected 'y'.",
            "UPDATE t SET a = 1 WHERE a = 2 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'zz'.",
            "UPDATE t SET a = 1 WHERE a = 2 zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1'. | syntax "
                + "error line 1 at position 40 unexpected 'y'.",
            "UPDATE t SET a = 1 FROM n 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''q''. | syntax "
                + "error line 1 at position 42 unexpected 'y'.",
            "UPDATE t SET a = 1 FROM n 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'y'.",
            "UPDATE t SET a = 1 FROM n zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '1'. | syntax "
                + "error line 1 at position 42 unexpected 'y'.",
            "UPDATE t SET a = 1, b = 'x' 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ''q''. | syntax "
                + "error line 1 at position 44 unexpected 'y'.",
            "UPDATE t SET a = 1, b = 'x' 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'zz'.",
            "UPDATE t SET a = 1, b = 'x' zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected '1'.",
            "MERGE INTO t USING n ON t.a = n.a WHEN MATCHED THEN DELETE 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected ''q''.",
            "MERGE INTO t USING n ON t.a = n.a WHEN MATCHED THEN DELETE 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'zz'.",
            "MERGE INTO t USING n ON t.a = n.a WHEN MATCHED THEN DELETE zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 69 unexpected '1'. | syntax "
                + "error line 1 at position 83 unexpected 'y'.",
            "MERGE INTO t USING n ON t.a = n.a WHEN MATCHED THEN UPDATE SET a = 1 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 69 unexpected ''q''. | syntax "
                + "error line 1 at position 85 unexpected 'y'.",
            "MERGE INTO t USING n ON t.a = n.a WHEN MATCHED THEN UPDATE SET a = 1 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 69 unexpected 'zz'.",
            "MERGE INTO t USING n ON t.a = n.a WHEN MATCHED THEN UPDATE SET a = 1 zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 78 unexpected '1'.",
            "MERGE INTO t USING n ON t.a = n.a WHEN NOT MATCHED THEN INSERT (a) VALUES (1) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 78 unexpected ''q''.",
            "MERGE INTO t USING n ON t.a = n.a WHEN NOT MATCHED THEN INSERT (a) VALUES (1) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 78 unexpected 'zz'.",
            "MERGE INTO t USING n ON t.a = n.a WHEN NOT MATCHED THEN INSERT (a) VALUES (1) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '1'.",
            "COPY INTO t FROM @s 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ''q''.",
            "COPY INTO t FROM @s 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'.",
            "LIST @s 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected ''q''.",
            "LIST @s 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '1'.",
            "REMOVE @s 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ''q''.",
            "REMOVE @s 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '1'. | syntax "
                + "error line 1 at position 36 unexpected 'y'.",
            "EXECUTE IMMEDIATE 'x' 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ''q''. | syntax "
                + "error line 1 at position 38 unexpected 'y'.",
            "EXECUTE IMMEDIATE 'x' 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '1'. | syntax "
                + "error line 1 at position 30 unexpected 'y'.",
            "EXECUTE TASK tk 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ''q''.",
            "EXECUTE TASK tk 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'zz'.",
            "EXECUTE TASK tk zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'. | syntax "
                + "error line 1 at position 38 unexpected 'y'.",
            "EXPLAIN SELECT 1 FROM t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected ''q''. | syntax "
                + "error line 1 at position 40 unexpected 'y'.",
            "EXPLAIN SELECT 1 FROM t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'y'.",
            "EXPLAIN SELECT 1 FROM t zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '1'. | syntax "
                + "error line 1 at position 23 unexpected 'y'.",
            "SELECT 1 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected ''q''. | syntax "
                + "error line 1 at position 25 unexpected 'y'.",
            "SELECT 1 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'y'.",
            "SELECT 1 zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '1'. | syntax "
                + "error line 1 at position 30 unexpected 'y'.",
            "SELECT a FROM t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ''q''. | syntax "
                + "error line 1 at position 32 unexpected 'y'.",
            "SELECT a FROM t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'y'.",
            "SELECT a FROM t zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '1'.",
            "SELECT 1 c 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected ''q''.",
            "SELECT 1 c 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'zz'.",
            "SELECT 1 c zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '1'.",
            "SELECT 1 AS c 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected ''q''.",
            "SELECT 1 AS c 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'zz'.",
            "SELECT 1 AS c zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'. | syntax "
                + "error line 1 at position 32 unexpected 'y'.",
            "SELECT a FROM t x 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ''q''. | syntax "
                + "error line 1 at position 34 unexpected 'y'.",
            "SELECT a FROM t x 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'zz'. | syntax "
                + "error line 1 at position 33 unexpected 'y'.",
            "SELECT a FROM t x zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '1'. | syntax "
                + "error line 1 at position 35 unexpected 'y'.",
            "SELECT a FROM t AS x 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ''q''. | syntax "
                + "error line 1 at position 37 unexpected 'y'.",
            "SELECT a FROM t AS x 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'zz'. | syntax "
                + "error line 1 at position 36 unexpected 'y'.",
            "SELECT a FROM t AS x zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '1'. | syntax "
                + "error line 1 at position 30 unexpected 'y'.",
            "SELECT * FROM t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ''q''. | syntax "
                + "error line 1 at position 32 unexpected 'y'.",
            "SELECT * FROM t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'y'.",
            "SELECT * FROM t zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'. | syntax "
                + "error line 1 at position 32 unexpected 'y'.",
            "SELECT t.* FROM t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ''q''. | syntax "
                + "error line 1 at position 34 unexpected 'y'.",
            "SELECT t.* FROM t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'y'.",
            "SELECT t.* FROM t zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'. | syntax "
                + "error line 1 at position 43 unexpected 'y'.",
            "SELECT a FROM t HAVING a = 1 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ''q''. | syntax "
                + "error line 1 at position 45 unexpected 'y'.",
            "SELECT a FROM t HAVING a = 1 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'zz'.",
            "SELECT a FROM t HAVING a = 1 zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'. | syntax "
                + "error line 1 at position 44 unexpected 'y'.",
            "SELECT a FROM t QUALIFY a = 1 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''q''. | syntax "
                + "error line 1 at position 46 unexpected 'y'.",
            "SELECT a FROM t QUALIFY a = 1 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'zz'.",
            "SELECT a FROM t QUALIFY a = 1 zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'.",
            "SELECT a FROM t ORDER BY a DESC 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ''q''.",
            "SELECT a FROM t ORDER BY a DESC 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'zz'.",
            "SELECT a FROM t ORDER BY a DESC zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '1'.",
            "SELECT a FROM t ORDER BY a NULLS LAST 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ''q''.",
            "SELECT a FROM t ORDER BY a NULLS LAST 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'zz'.",
            "SELECT a FROM t ORDER BY a NULLS LAST zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '1'.",
            "SELECT a FROM t FETCH FIRST 1 ROWS ONLY 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ''q''.",
            "SELECT a FROM t FETCH FIRST 1 ROWS ONLY 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '1'.",
            "SELECT a FROM t LIMIT 1 OFFSET 2 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ''q''.",
            "SELECT a FROM t LIMIT 1 OFFSET 2 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'zz'.",
            "SELECT a FROM t LIMIT 1 OFFSET 2 zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '1'. | syntax "
                + "error line 1 at position 39 unexpected 'y'.",
            "SELECT a FROM t OFFSET 2 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ''q''. | syntax "
                + "error line 1 at position 41 unexpected 'y'.",
            "SELECT a FROM t OFFSET 2 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'zz'. | syntax "
                + "error line 1 at position 40 unexpected 'y'.",
            "SELECT a FROM t OFFSET 2 zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '1'. | syntax "
                + "error line 1 at position 36 unexpected 'y'.",
            "SELECT TOP 1 a FROM t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ''q''. | syntax "
                + "error line 1 at position 38 unexpected 'y'.",
            "SELECT TOP 1 a FROM t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'y'.",
            "SELECT TOP 1 a FROM t zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN n ON t.a = n.a 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN n ON t.a = n.a 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'zz'.",
            "SELECT 1 FROM t JOIN n ON t.a = n.a zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '1'.",
            "SELECT 1 FROM t JOIN n USING (a) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ''q''.",
            "SELECT 1 FROM t JOIN n USING (a) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'. | syntax "
                + "error line 1 at position 43 unexpected 'y'.",
            "SELECT 1 FROM t CROSS JOIN n 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ''q''. | syntax "
                + "error line 1 at position 45 unexpected 'y'.",
            "SELECT 1 FROM t CROSS JOIN n 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'.",
            "SELECT 1 FROM t CROSS JOIN n zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '1'. | syntax "
                + "error line 1 at position 45 unexpected 'y'.",
            "SELECT 1 FROM t NATURAL JOIN n 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ''q''. | syntax "
                + "error line 1 at position 47 unexpected 'y'.",
            "SELECT 1 FROM t NATURAL JOIN n 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'y'.",
            "SELECT 1 FROM t NATURAL JOIN n zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '1'. | syntax "
                + "error line 1 at position 33 unexpected 'y'.",
            "SELECT 1 FROM t, n 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ''q''. | syntax "
                + "error line 1 at position 35 unexpected 'y'.",
            "SELECT 1 FROM t, n 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'y'.",
            "SELECT 1 FROM t, n zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '1'. | syntax "
                + "error line 1 at position 39 unexpected 'y'.",
            "SELECT 1 FROM (SELECT 1) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ''q''. | syntax "
                + "error line 1 at position 41 unexpected 'y'.",
            "SELECT 1 FROM (SELECT 1) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'y'.",
            "SELECT 1 FROM (SELECT 1) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'. | syntax "
                + "error line 1 at position 41 unexpected 'y'.",
            "SELECT 1 FROM (SELECT 1) x 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ''q''. | syntax "
                + "error line 1 at position 43 unexpected 'y'.",
            "SELECT 1 FROM (SELECT 1) x 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'zz'. | syntax "
                + "error line 1 at position 42 unexpected 'y'.",
            "SELECT 1 FROM (SELECT 1) x zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'. | syntax "
                + "error line 1 at position 38 unexpected 'y'.",
            "SELECT 1 UNION SELECT 2 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected ''q''. | syntax "
                + "error line 1 at position 40 unexpected 'y'.",
            "SELECT 1 UNION SELECT 2 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'y'.",
            "SELECT 1 UNION SELECT 2 zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '1'.",
            "(SELECT 1) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected ''q''.",
            "(SELECT 1) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'zz'.",
            "(SELECT 1) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '1'. | syntax "
                + "error line 1 at position 51 unexpected 'y'.",
            "WITH c AS (SELECT 1) SELECT 1 FROM c 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ''q''. | syntax "
                + "error line 1 at position 53 unexpected 'y'.",
            "WITH c AS (SELECT 1) SELECT 1 FROM c 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'y'.",
            "WITH c AS (SELECT 1) SELECT 1 FROM c zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'VALUES'.",
            "VALUES (1) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'VALUES'.",
            "VALUES (1) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'VALUES'.",
            "VALUES (1) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'. | syntax "
                + "error line 1 at position 41 unexpected 'y'.",
            "SELECT 1 FROM (VALUES (1)) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ''q''. | syntax "
                + "error line 1 at position 43 unexpected 'y'.",
            "SELECT 1 FROM (VALUES (1)) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'y'.",
            "SELECT 1 FROM (VALUES (1)) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '1'. | syntax "
                + "error line 1 at position 63 unexpected 'y'.",
            "SELECT 1 FROM TABLE(FLATTEN(ARRAY_CONSTRUCT(1))) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected ''q''. | syntax "
                + "error line 1 at position 65 unexpected 'y'.",
            "SELECT 1 FROM TABLE(FLATTEN(ARRAY_CONSTRUCT(1))) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected 'y'.",
            "SELECT 1 FROM TABLE(FLATTEN(ARRAY_CONSTRUCT(1))) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '1'. | syntax "
                + "error line 1 at position 54 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a BETWEEN 1 AND 2 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ''q''. | syntax "
                + "error line 1 at position 56 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a BETWEEN 1 AND 2 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'zz'.",
            "SELECT 1 FROM t WHERE a BETWEEN 1 AND 2 zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '1'. | syntax "
                + "error line 1 at position 47 unexpected 'y'.",
            "SELECT 1 FROM t WHERE b LIKE 'x' 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ''q''. | syntax "
                + "error line 1 at position 49 unexpected 'y'.",
            "SELECT 1 FROM t WHERE b LIKE 'x' 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'zz'.",
            "SELECT 1 FROM t WHERE b LIKE 'x' zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'. | syntax "
                + "error line 1 at position 38 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'zz'.",
            "SELECT 1 FROM t WHERE a zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax "
                + "error line 1 at position 46 unexpected 'y'.",
            "SELECT 1 FROM t WHERE NOT a = 1 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ''q''. | syntax "
                + "error line 1 at position 48 unexpected 'y'.",
            "SELECT 1 FROM t WHERE NOT a = 1 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'zz'.",
            "SELECT 1 FROM t WHERE NOT a = 1 zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '1'. | syntax "
                + "error line 1 at position 50 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a IS NOT NULL 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected ''q''. | syntax "
                + "error line 1 at position 52 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a IS NOT NULL 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'zz'.",
            "SELECT 1 FROM t WHERE a IS NOT NULL zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '1'. | syntax "
                + "error line 1 at position 55 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = ANY (SELECT 1) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ''q''. | syntax "
                + "error line 1 at position 57 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = ANY (SELECT 1) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'zz'.",
            "SELECT 1 FROM t WHERE a = ANY (SELECT 1) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'. | syntax "
                + "error line 1 at position 44 unexpected 'y'.",
            "SELECT 1 FROM t WHERE (a = 1) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''q''. | syntax "
                + "error line 1 at position 46 unexpected 'y'.",
            "SELECT 1 FROM t WHERE (a = 1) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'zz'.",
            "SELECT 1 FROM t WHERE (a = 1) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '1'. | syntax "
                + "error line 1 at position 47 unexpected 'y'.",
            "SELECT 1 FROM t WHERE ABS(a) = 1 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ''q''. | syntax "
                + "error line 1 at position 49 unexpected 'y'.",
            "SELECT 1 FROM t WHERE ABS(a) = 1 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'zz'.",
            "SELECT 1 FROM t WHERE ABS(a) = 1 zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '1'. | syntax "
                + "error line 1 at position 51 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = (SELECT 1) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ''q''. | syntax "
                + "error line 1 at position 53 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = (SELECT 1) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'zz'.",
            "SELECT 1 FROM t WHERE a = (SELECT 1) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '1'. | syntax "
                + "error line 1 at position 52 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a IN (SELECT 1) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ''q''. | syntax "
                + "error line 1 at position 54 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a IN (SELECT 1) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'zz'.",
            "SELECT 1 FROM t WHERE a IN (SELECT 1) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'. | syntax "
                + "error line 1 at position 41 unexpected 'y'.",
            "SELECT 1 FROM t WHERE TRUE 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ''q''. | syntax "
                + "error line 1 at position 43 unexpected 'y'.",
            "SELECT 1 FROM t WHERE TRUE 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'zz'.",
            "SELECT 1 FROM t WHERE TRUE zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '1'. | syntax "
                + "error line 1 at position 45 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = NULL 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ''q''. | syntax "
                + "error line 1 at position 47 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = NULL 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'zz'.",
            "SELECT 1 FROM t WHERE a = NULL zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'. | syntax "
                + "error line 1 at position 43 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = -1 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ''q''. | syntax "
                + "error line 1 at position 45 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = -1 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'zz'.",
            "SELECT 1 FROM t WHERE a = -1 zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '1'. | syntax "
                + "error line 1 at position 52 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a::INT = 1::INT 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ''q''. | syntax "
                + "error line 1 at position 54 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a::INT = 1::INT 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'zz'.",
            "SELECT 1 FROM t WHERE a::INT = 1::INT zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '1'.",
            "SELECT 1 FROM t SAMPLE (10) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ''q''.",
            "SELECT 1 FROM t SAMPLE (10) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'.",
            "SELECT 1 FROM t GROUP BY ALL 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ''q''.",
            "SELECT 1 FROM t GROUP BY ALL 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'zz'.",
            "SELECT 1 FROM t GROUP BY ALL zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'. | syntax "
                + "error line 1 at position 44 unexpected 'y'.",
            "SELECT 1 FROM t GROUP BY a, b 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'y'.",
            "SELECT 1 FROM t GROUP BY a, b 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '1'.",
            "SELECT 1 FROM t GROUP BY ROLLUP (a) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected ''q''.",
            "SELECT 1 FROM t GROUP BY ROLLUP (a) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'zz'.",
            "SELECT 1 FROM t GROUP BY ROLLUP (a) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '1'. | syntax "
                + "error line 1 at position 55 unexpected 'y'.",
            "SELECT CASE WHEN a = 1 THEN 2 END FROM t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ''q''. | syntax "
                + "error line 1 at position 57 unexpected 'y'.",
            "SELECT CASE WHEN a = 1 THEN 2 END FROM t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected 'y'.",
            "SELECT CASE WHEN a = 1 THEN 2 END FROM t zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '1'. | syntax "
                + "error line 1 at position 28 unexpected 'y'.",
            "SELECT ABS(1) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'.",
            "SELECT ABS(1) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '1'. | syntax "
                + "error line 1 at position 25 unexpected 'y'.",
            "SELECT (1) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected ''q''. | syntax "
                + "error line 1 at position 27 unexpected 'y'.",
            "SELECT (1) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'y'.",
            "SELECT (1) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '1'. | syntax "
                + "error line 1 at position 35 unexpected 'y'.",
            "SELECT a::INT FROM t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ''q''. | syntax "
                + "error line 1 at position 37 unexpected 'y'.",
            "SELECT a::INT FROM t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'.",
            "SELECT a::INT FROM t zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'. | syntax "
                + "error line 1 at position 43 unexpected 'y'.",
            "SELECT CAST(a AS INT) FROM t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ''q''. | syntax "
                + "error line 1 at position 45 unexpected 'y'.",
            "SELECT CAST(a AS INT) FROM t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'.",
            "SELECT CAST(a AS INT) FROM t zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '1'. | syntax "
                + "error line 1 at position 37 unexpected 'y'.",
            "SELECT COUNT(*) FROM t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ''q''. | syntax "
                + "error line 1 at position 39 unexpected 'y'.",
            "SELECT COUNT(*) FROM t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'y'.",
            "SELECT COUNT(*) FROM t zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '1'. | syntax "
                + "error line 1 at position 45 unexpected 'y'.",
            "SELECT COUNT(*) OVER () FROM t 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ''q''. | syntax "
                + "error line 1 at position 47 unexpected 'y'.",
            "SELECT COUNT(*) OVER () FROM t 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'y'.",
            "SELECT COUNT(*) OVER () FROM t zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '1'. | syntax "
                + "error line 1 at position 48 unexpected 'y'.",
            "SELECT 1 FROM t AT (OFFSET => -1) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ''q''. | syntax "
                + "error line 1 at position 50 unexpected 'y'.",
            "SELECT 1 FROM t AT (OFFSET => -1) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'y'.",
            "SELECT 1 FROM t AT (OFFSET => -1) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '1'.",
            "CALL p(1) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ''q''.",
            "CALL p(1) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'zz'.",
            "CALL p(1) zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '1'.",
            "CALL s.p() 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected ''q''.",
            "CALL s.p() 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'zz'.",
            "CALL s.p() zz; SELECT 1 x y");
    }

    @Test
    public void otherLiteralsAndLaterStatements() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        engine.execute("CREATE OR REPLACE TABLE tt (a INT, s VARCHAR(5), o OBJECT, v VARIANT, tm TIME, d DATE, ts "
            + "TIMESTAMP_NTZ, tz TIMESTAMP_TZ, bo BOOLEAN)");
        engine.execute("INSERT INTO tt SELECT 86400, 'abc', OBJECT_CONSTRUCT('x', '2024-01-02'), PARSE_JSON('{\"x\": "
            + "\"2024-01-02\"}'), '03:04:05'::TIME, '2024-01-02'::DATE, '2024-01-02 03:04:05'::TIMESTAMP_NTZ, "
            + "'2024-01-02 03:04:05 +02:00'::TIMESTAMP_TZ, TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '1.5'. | syntax "
                + "error line 1 at position 44 unexpected 'y'.",
            "SELECT a FROM t WHERE a = 1 1.5; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '1.5'. | syntax "
                + "error line 1 at position 29 unexpected 'y'.",
            "USE SCHEMA s 1.5; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '$$q$$'. | syntax "
                + "error line 1 at position 46 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = 1 $$q$$; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '$$q$$'.",
            "COMMIT $$q$$; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'TRUE'.",
            "SELECT 1 FROM t WHERE a = 1 TRUE; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'NULL'.",
            "SELECT 1 FROM t WHERE a = 1 NULL; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'TRUE'.",
            "COMMIT TRUE; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '1'. | syntax "
                + "error line 1 at position 30 unexpected 'y'.",
            "USE SCHEMA s 1 zz; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '1'. | syntax "
                + "error line 1 at position 32 unexpected 'y'.",
            "SELECT 1 FROM t 1 2; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '1'.",
            "COMMIT 1 2; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '1'.",
            "SELECT 1; COMMIT 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 19 unexpected '1'.",
            "SELECT 1 +; COMMIT 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 40 unexpected '2'. | syntax error line 1 at position 54 unexpected 'y'.",
            "SELECT 1 +; SELECT 1 FROM t WHERE a = 1 2; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '1'.",
            "COMMIT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '1'.",
            "USE SCHEMA s 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '2'.",
            "SELECT 1 FROM t WHERE a = 1 2");
    }
}
