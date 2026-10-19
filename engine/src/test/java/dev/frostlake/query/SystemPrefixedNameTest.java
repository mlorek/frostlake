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
 * A word that begins SYSTEM$ is a plain name wherever it calls nothing: an alias, a column, a table, a view, a
 * schema, a common table expression, a variable and a class — SELECT 1 AS system$foo answers 1, DROP TABLE
 * system$foo misses the table, SHOW SYSTEM$FOO misses the class and SELECT SYSTEM$FOO reads a column that does
 * not exist. A table, a view and a function so named are created, filled, read and dropped, and a function of
 * the schema is called by its name; only a call that names no function is an unknown function (live-verified).
 */
public class SystemPrefixedNameTest extends MeasuredCellsTestSupport {

    @Test
    public void asAnAliasAndAsAnObjectsName() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        assertAnswer(
            "1",
            "SELECT 1 AS system$foo");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'SYSTEM$FOO' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE SYSTEM$FOO.",
            "DESCRIBE TABLE SYSTEM$FOO");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.SYSTEM$FOO' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.SYSTEM$FOO.",
            "DROP TABLE SYSTEM$FOO");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN SYSTEM$FOO");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN SCHEMA SYSTEM$FOO");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN DATABASE SYSTEM$FOO");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN x.SYSTEM$FOO");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN SYSTEM$FOO.x");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'SYSTEM$FOO' does not exist or not authorized.",
            "SHOW SYSTEM$FOO");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'SYSTEM$FOO' does not exist or not authorized.",
            "SHOW TERSE SYSTEM$FOO");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'SYSTEM$FOO' does not exist or not authorized.",
            "SHOW SYSTEM$FOO LIKE 'a'");
        assertAnswer(
            "REFUSED SQL compilation error: Object type or Class 'SYSTEM$FOO' does not exist or not authorized.",
            "DROP SYSTEM$FOO x");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'SYSTEM$FOO' does not exist or not authorized.",
            "SHOW FUNCTIONS IN SYSTEM$FOO x");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'SYSTEM$FOO' does not exist or not authorized.",
            "SHOW PROCEDURES IN SYSTEM$FOO x");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'SYSTEM$FOO' does not exist or not authorized.",
            "SHOW ROLES IN SYSTEM$FOO x");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'SYSTEM$TYPEOF' does not exist or not authorized.",
            "SHOW SYSTEM$TYPEOF");
        assertAnswer(
            "REFUSED SQL compilation error: Object type or Class 'SYSTEM$TYPEOF' does not exist or not authorized.",
            "DROP SYSTEM$TYPEOF x");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.SYSTEM$FOO' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.SYSTEM$FOO.",
            "SHOW FUNCTIONS IN SYSTEM$FOO");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type VIEW in "
                + "INSTANCE'.",
            "SHOW VIEWS IN SYSTEM$FOO x");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'y'.",
            "SHOW FUNCTIONS IN SYSTEM$FOO x y");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'y'.",
            "SHOW TABLES IN SYSTEM$FOO x y");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'x'.",
            "SHOW SYSTEM$FOO x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '<EOF>'.",
            "DROP SYSTEM$FOO");
    }

    @Test
    public void asATablesAViewsAndAColumnsName() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        assertAnswer(
            "Table SYSTEM$T successfully created.",
            "CREATE OR REPLACE TABLE system$t (a INT)");
        assertAnswer(
            "1",
            "INSERT INTO system$t VALUES (1)");
        assertAnswer(
            "1",
            "SELECT a FROM system$t");
        assertAnswer(
            "1",
            "SELECT system$t.a FROM system$t");
        assertAnswer(
            "SYSTEM$T successfully dropped.",
            "DROP TABLE IF EXISTS system$t");
        assertAnswer(
            "1",
            "SELECT system$foo FROM (SELECT 1 AS system$foo)");
        assertAnswer(
            "1",
            "SELECT 1 AS \"system$foo\"");
        assertAnswer(
            "1",
            "SELECT q.system$c FROM (SELECT 1 AS system$c) q");
        assertAnswer(
            "View SYSTEM$V successfully created.",
            "CREATE OR REPLACE VIEW system$v AS SELECT 1 AS a");
        assertAnswer(
            "1",
            "SELECT a FROM system$v");
        assertAnswer(
            "SYSTEM$V successfully dropped.",
            "DROP VIEW IF EXISTS system$v");
        assertAnswer(
            "1",
            "BEGIN LET system$x := 1; RETURN system$x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'SYSTEM$FOO'",
            "SELECT SYSTEM$FOO");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SYSTEM$FOO.",
            "SELECT SYSTEM$FOO()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'SYSTEM$TYPEOF'",
            "SELECT SYSTEM$TYPEOF");
        assertAnswer(
            "1",
            "SELECT 1 system$foo");
        assertAnswer(
            "",
            "SELECT a system$a FROM t1");
        assertAnswer(
            "",
            "SELECT * FROM t1 system$a");
        assertAnswer(
            "",
            "SELECT system$a.a FROM t1 system$a");
        assertAnswer(
            "Table TS2 successfully created.",
            "CREATE OR REPLACE TABLE ts2 (system$c INT)");
        assertAnswer(
            "",
            "SELECT system$c FROM ts2");
        assertAnswer(
            "TS2 successfully dropped.",
            "DROP TABLE IF EXISTS ts2");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "USE SCHEMA system$foo");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.SYSTEM$FOO' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.SYSTEM$FOO.",
            "DESCRIBE SCHEMA system$foo");
        assertAnswer(
            "1",
            "SELECT 1 AS system$");
        assertAnswer(
            "1",
            "SELECT 1 AS system$1");
    }

    @Test
    public void inMoreStatements() {
        assertAnswer(
            "1",
            "SELECT 1 AS system$stream_has_data");
        assertAnswer(
            "1",
            "SELECT system$typeof FROM (SELECT 1 AS system$typeof)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier "
                + "'SYSTEM$STREAM_HAS_DATA'",
            "SELECT SYSTEM$STREAM_HAS_DATA");
        assertAnswer(
            "1",
            "SELECT t.system$x FROM (SELECT 1 AS system$x) t");
        assertAnswer(
            "Table SYSTEM$T successfully created.",
            "CREATE OR REPLACE TABLE system$t (a INT)");
        assertAnswer(
            "1",
            "INSERT INTO system$t (a) VALUES (1)");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM system$t");
        assertAnswer(
            "1|0",
            "UPDATE system$t SET a = 2");
        assertAnswer(
            "2",
            "SELECT a FROM system$t");
        assertAnswer(
            "1",
            "DELETE FROM system$t");
        assertAnswer(
            "SYSTEM$T successfully dropped.",
            "DROP TABLE system$t");
        assertAnswer(
            "View SYSTEM$V successfully created.",
            "CREATE OR REPLACE VIEW system$v (a) AS SELECT 1");
        assertAnswer(
            "1",
            "SELECT a FROM system$v");
        assertAnswer(
            "SYSTEM$V successfully dropped.",
            "DROP VIEW system$v");
        assertAnswer(
            "Table SYSTEM$T2 successfully created.",
            "CREATE TABLE IF NOT EXISTS system$t2 (a INT)");
        assertAnswer(
            "SYSTEM$T2 successfully dropped.",
            "DROP TABLE IF EXISTS system$t2");
        assertAnswer(
            "1",
            "WITH system$c AS (SELECT 1 AS a) SELECT a FROM system$c");
        assertAnswer(
            "1",
            "SELECT 1 AS system$c ORDER BY system$c");
        assertAnswer(
            "2",
            "SELECT system$a + 1 FROM (SELECT 1 AS system$a)");
        assertAnswer(
            "1",
            "SELECT system$a.b FROM (SELECT 1 AS b) system$a");
        assertAnswer(
            "Function SYSTEM$F successfully created.",
            "CREATE OR REPLACE FUNCTION system$f() RETURNS INT AS '1'");
        assertAnswer(
            "1",
            "SELECT system$f()");
        assertAnswer(
            "SYSTEM$F successfully dropped.",
            "DROP FUNCTION IF EXISTS system$f()");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SYSTEM$NOSUCH_ZQ7.",
            "CALL system$nosuch_zq7()");
        assertAnswer(
            "NUMBER(1,0)[SB1]",
            "SELECT SYSTEM$TYPEOF (1)");
        assertAnswer(
            "NUMBER(1,0)[SB1]",
            "SELECT system$typeof(1) AS system$typeof");
        assertAnswer(
            "1|2",
            "SELECT 1 AS system$typeof, 2 AS SYSTEM$WAIT");
        assertAnswer(
            "Table TS3 successfully created.",
            "CREATE OR REPLACE TABLE ts3 (system$typeof INT, b INT)");
        assertAnswer(
            "1",
            "INSERT INTO ts3 (system$typeof, b) VALUES (1, 2)");
        assertAnswer(
            "1|2",
            "SELECT system$typeof, b FROM ts3 WHERE system$typeof = 1");
        assertAnswer(
            "TS3 successfully dropped.",
            "DROP TABLE IF EXISTS ts3");
        assertAnswer(
            "1",
            "SELECT $1 AS system$v FROM VALUES (1)");
        assertAnswer(
            "1|2",
            "SELECT 1 AS \"SYSTEM$FOO\", system$foo FROM (SELECT 2 AS system$foo)");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'system$foo'.",
            "SHOW TABLES LIKE system$foo");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'SYSTEM$FOO.A'",
            "SELECT SYSTEM$FOO.a");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'SYSTEM$FOO.NEXTVAL'",
            "SELECT system$foo.nextval");
    }
}
