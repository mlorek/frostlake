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
 * EXPLAIN plans more than a query and a DML statement: a CREATE TABLE … AS SELECT and a materialized view are a bare
 * Result over the CreateTableAsSelect that names the object over what the query reads; a CREATE, DROP, ALTER,
 * UNDROP or TRUNCATE, a SHOW, a DESCRIBE, a session variable's SET and a LIST are compiled — a missing object, a
 * taken name, an unknown parameter is refused as the statement refuses it — and planned as a bare Result; USE,
 * CALL, COMMENT, UNSET, REMOVE, EXECUTE, VALUES, EXPLAIN and the transaction statements stay syntax errors. Nothing
 * explained is done (all live-verified).
 */
public class ExplainPlannedStatementsTest extends MeasuredCellsTestSupport {

    @Test
    public void aStatementIsPlannedAndNothingIsDone() {
        engine.execute("CREATE OR REPLACE TABLE et (a INT, b VARCHAR)");
        engine.execute("INSERT INTO et VALUES (1, 'x'), (2, 'y')");
        engine.execute("CREATE OR REPLACE TABLE eu (a INT)");
        engine.execute("DROP TABLE IF EXISTS ex");
        engine.execute("DROP VIEW IF EXISTS ev");
        engine.execute("DROP SEQUENCE IF EXISTS es");
        engine.execute("DROP MATERIALIZED VIEW IF EXISTS ew");
        engine.execute("DROP STREAM IF EXISTS est2");
        engine.execute("DROP STAGE IF EXISTS est");
        engine.execute("DROP SCHEMA IF EXISTS es2");
        engine.execute("DROP FUNCTION IF EXISTS ef()");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EX|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN CREATE TABLE ex AS SELECT a FROM et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EX|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN CREATE OR REPLACE TABLE ex AS SELECT a FROM et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EX|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN CREATE TABLE IF NOT EXISTS ex AS SELECT a FROM et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EX|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN CREATE TEMPORARY TABLE ex AS SELECT a FROM et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EX|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN CREATE TRANSIENT TABLE ex AS SELECT a FROM et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EX|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN CREATE TABLE ex (z) AS SELECT a FROM et");
        answer("EXPLAIN CREATE TABLE test_db.test_schema.ex AS SELECT a FROM et");
        assertAnswer("GlobalStats: |     partitionsTotal=1 |     partitionsAssigned=1 |     bytesAssigned=1024 | "
                + "Operations: | 1:0     ->Result   | 1:1          ->CreateTableAsSelect  TEST_DB.TEST_SCHEMA.EX   | "
                + "1:2               ->TableScan  TEST_DB.TEST_SCHEMA.ET  A  {partitionsTotal=1, partitionsAssigned=1,"
                + " bytesAssigned=1024} | ",
            "EXPLAIN USING TEXT CREATE TABLE ex AS SELECT a FROM et");
        assertAnswer("{\"GlobalStats\":{\"partitionsTotal\":1,\"partitionsAssigned\":1,\"bytesAssigned\":1024},"
                + "\"Operations\":[[{\"id\":0,\"operation\":\"Result\"},{\"id\":1,"
                + "\"operation\":\"CreateTableAsSelect\",\"expressions\":[\"TEST_DB.TEST_SCHEMA.EX\"],"
                + "\"parentOperators\":[0]},{\"id\":2,\"operation\":\"TableScan\","
                + "\"objects\":[\"TEST_DB.TEST_SCHEMA.ET\"],\"expressions\":[\"A\"],\"partitionsAssigned\":1,"
                + "\"partitionsTotal\":1,\"bytesAssigned\":1024,\"parentOperators\":[1]}]]}",
            "EXPLAIN USING JSON CREATE TABLE ex AS SELECT a FROM et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EX|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN USING TABULAR CREATE TABLE ex AS SELECT a FROM et");
        assertAnswer("REFUSED SQL compilation error: | Object 'ET' already exists.",
            "EXPLAIN CREATE TABLE et AS SELECT a FROM eu");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.ET|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|null|A|0|0|0",
            "EXPLAIN CREATE OR REPLACE TABLE et AS SELECT a FROM eu");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EW|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|null|METADATA$PARTITION_NAME, A|1|1|1024",
            "EXPLAIN CREATE MATERIALIZED VIEW ew AS SELECT a FROM et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN CREATE TABLE ex (a INT)");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN CREATE TABLE ex LIKE et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN CREATE TABLE ex CLONE et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN CREATE VIEW ev AS SELECT a FROM et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN CREATE OR REPLACE VIEW ev AS SELECT a FROM et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN CREATE SEQUENCE es");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN TRUNCATE TABLE et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN DROP TABLE et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN ALTER TABLE et ADD COLUMN z INT");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN SHOW TABLES");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN DESCRIBE TABLE et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN SET v = 1");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET.A, ET.B|null|"
                + "null|null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|A, B|1|1|1024",
            "EXPLAIN TABLE et");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'USE'.",
            "EXPLAIN USE SCHEMA test_schema");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'CALL'.",
            "EXPLAIN CALL nosuch_p()");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'VALUES'.",
            "EXPLAIN VALUES (1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'EXPLAIN'.",
            "EXPLAIN EXPLAIN SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'COMMIT'.",
            "EXPLAIN COMMIT");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN CREATE SCHEMA es2");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN CREATE STAGE est");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN CREATE FUNCTION ef() RETURNS INT AS '1'");
        assertAnswer("REFUSED SQL compilation error: | View 'TEST_DB.TEST_SCHEMA.NOSUCH_V' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_V.",
            "EXPLAIN DROP VIEW nosuch_v");
        assertAnswer("REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "EXPLAIN DROP TABLE nosuch_t");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN DROP TABLE IF EXISTS nosuch_t");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN ALTER SESSION SET TIMEZONE = 'UTC'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'UNSET'.",
            "EXPLAIN UNSET v");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN SHOW SCHEMAS");
        assertAnswer("REFUSED SQL compilation error: | View 'NOSUCH_V' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_V.",
            "EXPLAIN DESCRIBE VIEW nosuch_v");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN DESC TABLE et");
        assertAnswer("REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "EXPLAIN DESCRIBE TABLE nosuch_t");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'BEGIN'.",
            "EXPLAIN BEGIN");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'ROLLBACK'.",
            "EXPLAIN ROLLBACK");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'COMMENT'.",
            "EXPLAIN COMMENT ON TABLE et IS 'c'");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN UNDROP TABLE et");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'EXECUTE'.",
            "EXPLAIN EXECUTE IMMEDIATE 'SELECT 1'");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN CREATE STREAM est2 ON TABLE et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN LIST @%et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN ALTER TABLE et RENAME TO et9");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN ALTER TABLE et SWAP WITH eu");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN SHOW COLUMNS IN TABLE et");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 34 | invalid identifier 'NOSUCH'",
            "EXPLAIN CREATE TABLE ex AS SELECT nosuch FROM et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EX|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN CREATE TABLE ex (a INT) AS SELECT a FROM et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN SHOW TABLES LIKE 'E%'");
        answer("EXPLAIN CREATE OR REPLACE TABLE ex AS SELECT a, b FROM et WHERE a > 1 ORDER BY a");
        assertAnswer("REFUSED SQL compilation error: | Object 'NOSUCH_T' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "EXPLAIN TABLE nosuch_t");
        answer("EXPLAIN TABLE et ORDER BY a");
        assertAnswer("REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "EXPLAIN DROP SCHEMA nosuch_s");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EX|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN CREATE TABLE ex AS WITH c AS (SELECT a FROM et) SELECT a FROM c");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EX|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN CREATE TABLE ex AS (SELECT a FROM et)");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EX|null|null|null ;; 1|2|[1]|"
                + "Generator|null|null|1|null|null|null",
            "EXPLAIN CREATE TABLE ex AS SELECT 1 AS a");
        assertAnswer("REFUSED SQL compilation error: | View 'TEST_DB.TEST_SCHEMA.NOSUCH_V' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_V.",
            "EXPLAIN ALTER VIEW nosuch_v RENAME TO x");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN SHOW VIEWS");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN SHOW PARAMETERS");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN CREATE OR REPLACE SEQUENCE es START 5");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN CREATE OR REPLACE TABLE ex CLONE et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN SET (p, q) = (1, 2)");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|0|0 ;; 1|0|null|Result|null|null|number of rows "
                + "deleted|null|null|null ;; 1|1|[0]|Delete|TEST_DB.TEST_SCHEMA.ET|null|null|null|null|null ;; 1|2|"
                + "[1]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|null|1|0|0",
            "EXPLAIN DELETE FROM et");
        assertAnswer("", "SHOW TABLES LIKE 'EX'");
        assertAnswer("", "SHOW VIEWS LIKE 'EV'");
        assertAnswer("", "SHOW SEQUENCES LIKE 'ES'");
        assertAnswer("", "SHOW SCHEMAS LIKE 'ES2'");
        assertAnswer("", "SHOW STAGES LIKE 'EST'");
        assertAnswer("2", "SELECT COUNT(*) FROM et");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Session variable '$V' does not exist",
            "SELECT $v");
    }

    @Test
    public void aStatementIsCompiledForItsPlan() {
        engine.execute("CREATE OR REPLACE TABLE et (a INT, b VARCHAR)");
        engine.execute("INSERT INTO et VALUES (1, 'x'), (2, 'y')");
        engine.execute("CREATE OR REPLACE TABLE eu (a INT)");
        engine.execute("CREATE OR REPLACE SEQUENCE es0");
        engine.execute("CREATE OR REPLACE VIEW ev0 AS SELECT a FROM et");
        engine.execute("DROP TABLE IF EXISTS ex");
        engine.execute("DROP VIEW IF EXISTS ev");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EX|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN CREATE TABLE ex AS SELECT a + 1 AS c FROM et");
        assertAnswer("Result > CreateTableAsSelect{TEST_DB.TEST_SCHEMA.EX} > TableScan[TEST_DB.TEST_SCHEMA.ET]{A}",
            "SELECT LISTAGG(\"operation\" || COALESCE('[' || \"objects\" || ']', '') || COALESCE('{' || "
                + "\"expressions\" || '}', ''), ' > ') WITHIN GROUP (ORDER BY \"id\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EX|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|null|A, B|1|1|1024",
            "EXPLAIN CREATE TABLE ex AS SELECT * FROM et");
        assertAnswer("Result > CreateTableAsSelect{TEST_DB.TEST_SCHEMA.EX} > TableScan[TEST_DB.TEST_SCHEMA.ET]{A, B}",
            "SELECT LISTAGG(\"operation\" || COALESCE('[' || \"objects\" || ']', '') || COALESCE('{' || "
                + "\"expressions\" || '}', ''), ' > ') WITHIN GROUP (ORDER BY \"id\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        answer("EXPLAIN CREATE TABLE ex AS SELECT a FROM et LIMIT 1");
        answer("SELECT LISTAGG(\"operation\" || COALESCE('[' || \"objects\" || ']', '') || COALESCE('{' || "
            + "\"expressions\" || '}', ''), ' > ') WITHIN GROUP (ORDER BY \"id\") FROM "
            + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        answer("EXPLAIN CREATE TABLE ex AS SELECT a, COUNT(*) AS n FROM et GROUP BY a");
        answer("SELECT LISTAGG(\"operation\" || COALESCE('[' || \"objects\" || ']', '') || COALESCE('{' || "
            + "\"expressions\" || '}', ''), ' > ') WITHIN GROUP (ORDER BY \"id\") FROM "
            + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EX|null|null|null ;; 1|2|[1]|"
                + "UnionAll|null|null|null|null|null|null ;; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024 "
                + ";; 1|4|[2]|TableScan|TEST_DB.TEST_SCHEMA.EU|null|A|0|0|0",
            "EXPLAIN CREATE TABLE ex AS SELECT a FROM et UNION ALL SELECT a FROM eu");
        assertAnswer("Result > CreateTableAsSelect{TEST_DB.TEST_SCHEMA.EX} > UnionAll > "
                + "TableScan[TEST_DB.TEST_SCHEMA.ET]{A} > TableScan[TEST_DB.TEST_SCHEMA.EU]{A}",
            "SELECT LISTAGG(\"operation\" || COALESCE('[' || \"objects\" || ']', '') || COALESCE('{' || "
                + "\"expressions\" || '}', ''), ' > ') WITHIN GROUP (ORDER BY \"id\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        answer("EXPLAIN CREATE TABLE ex AS SELECT e.a FROM et e WHERE e.a = 1");
        answer("SELECT LISTAGG(\"operation\" || COALESCE('[' || \"objects\" || ']', '') || COALESCE('{' || "
            + "\"expressions\" || '}', ''), ' > ') WITHIN GROUP (ORDER BY \"id\") FROM "
            + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        answer("EXPLAIN CREATE TABLE ex AS SELECT et.a FROM et JOIN eu ON et.a = eu.a");
        answer("SELECT LISTAGG(\"operation\" || COALESCE('[' || \"objects\" || ']', '') || COALESCE('{' || "
            + "\"expressions\" || '}', ''), ' > ') WITHIN GROUP (ORDER BY \"id\") FROM "
            + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EX|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN CREATE TABLE test_schema.ex AS SELECT a FROM et");
        assertAnswer("Result > CreateTableAsSelect{TEST_DB.TEST_SCHEMA.EX} > TableScan[TEST_DB.TEST_SCHEMA.ET]{A}",
            "SELECT LISTAGG(\"operation\" || COALESCE('[' || \"objects\" || ']', '') || COALESCE('{' || "
                + "\"expressions\" || '}', ''), ' > ') WITHIN GROUP (ORDER BY \"id\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.\"Ex\"|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN CREATE OR REPLACE TABLE \"Ex\" AS SELECT a FROM et");
        assertAnswer("Result > CreateTableAsSelect{TEST_DB.TEST_SCHEMA.\"Ex\"} > TableScan[TEST_DB.TEST_SCHEMA.ET]{A}",
            "SELECT LISTAGG(\"operation\" || COALESCE('[' || \"objects\" || ']', '') || COALESCE('{' || "
                + "\"expressions\" || '}', ''), ' > ') WITHIN GROUP (ORDER BY \"id\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EX|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN CREATE TABLE IDENTIFIER('ex') AS SELECT a FROM et");
        assertAnswer("Result > CreateTableAsSelect{TEST_DB.TEST_SCHEMA.EX} > TableScan[TEST_DB.TEST_SCHEMA.ET]{A}",
            "SELECT LISTAGG(\"operation\" || COALESCE('[' || \"objects\" || ']', '') || COALESCE('{' || "
                + "\"expressions\" || '}', ''), ' > ') WITHIN GROUP (ORDER BY \"id\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EX|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN CREATE TABLE ex (z INT) AS SELECT a FROM et");
        assertAnswer("Result > CreateTableAsSelect{TEST_DB.TEST_SCHEMA.EX} > TableScan[TEST_DB.TEST_SCHEMA.ET]{A}",
            "SELECT LISTAGG(\"operation\" || COALESCE('[' || \"objects\" || ']', '') || COALESCE('{' || "
                + "\"expressions\" || '}', ''), ' > ') WITHIN GROUP (ORDER BY \"id\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN CREATE TABLE IF NOT EXISTS et AS SELECT a FROM eu");
        assertAnswer("Result",
            "SELECT LISTAGG(\"operation\" || COALESCE('[' || \"objects\" || ']', '') || COALESCE('{' || "
                + "\"expressions\" || '}', ''), ' > ') WITHIN GROUP (ORDER BY \"id\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        answer("EXPLAIN CREATE MATERIALIZED VIEW ew AS SELECT a, b FROM et WHERE a > 0");
        answer("SELECT LISTAGG(\"operation\" || COALESCE('[' || \"objects\" || ']', '') || COALESCE('{' || "
            + "\"expressions\" || '}', ''), ' > ') WITHIN GROUP (ORDER BY \"id\") FROM "
            + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        assertAnswer("REFUSED SQL compilation error: | Object 'EV0' already exists as VIEW",
            "EXPLAIN CREATE OR REPLACE MATERIALIZED VIEW ev0 AS SELECT a FROM et");
        assertShapedAnswer("REFUSED Query <QID> has no result because it failed",
            "SELECT LISTAGG(\"operation\" || COALESCE('[' || \"objects\" || ']', '') || COALESCE('{' || "
                + "\"expressions\" || '}', ''), ' > ') WITHIN GROUP (ORDER BY \"id\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        answer("EXPLAIN CREATE TABLE ex AS SELECT a FROM et ORDER BY a");
        answer("SELECT LISTAGG(\"operation\" || COALESCE('[' || \"objects\" || ']', '') || COALESCE('{' || "
            + "\"expressions\" || '}', ''), ' > ') WITHIN GROUP (ORDER BY \"id\") FROM "
            + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        assertAnswer("GlobalStats: |     partitionsTotal=0 |     partitionsAssigned=0 |     bytesAssigned=0 | "
                + "Operations: | 1:0     ->Result   | ",
            "EXPLAIN USING TEXT SHOW TABLES");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 15 | invalid identifier '\"operation\"'",
            "SELECT LISTAGG(\"operation\" || COALESCE('[' || \"objects\" || ']', '') || COALESCE('{' || "
                + "\"expressions\" || '}', ''), ' > ') WITHIN GROUP (ORDER BY \"id\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        assertAnswer("{\"GlobalStats\":{\"partitionsTotal\":0,\"partitionsAssigned\":0,\"bytesAssigned\":0},"
                + "\"Operations\":[[{\"id\":0,\"operation\":\"Result\"}]]}",
            "EXPLAIN USING JSON SHOW TABLES");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 15 | invalid identifier '\"operation\"'",
            "SELECT LISTAGG(\"operation\" || COALESCE('[' || \"objects\" || ']', '') || COALESCE('{' || "
                + "\"expressions\" || '}', ''), ' > ') WITHIN GROUP (ORDER BY \"id\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        assertAnswer("GlobalStats: |     partitionsTotal=0 |     partitionsAssigned=0 |     bytesAssigned=0 | "
                + "Operations: | 1:0     ->Result   | 1:1          ->CreateTableAsSelect  TEST_DB.TEST_SCHEMA.EX   | "
                + "1:2               ->Generator  1   | ",
            "EXPLAIN USING TEXT CREATE TABLE ex AS SELECT 1 AS a");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 15 | invalid identifier '\"operation\"'",
            "SELECT LISTAGG(\"operation\" || COALESCE('[' || \"objects\" || ']', '') || COALESCE('{' || "
                + "\"expressions\" || '}', ''), ' > ') WITHIN GROUP (ORDER BY \"id\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        answer("EXPLAIN CREATE TABLE test_db.test_schema.ex AS SELECT a FROM et");
        answer("SELECT \"expressions\" = CURRENT_ACCOUNT() || '.TEST_DB.TEST_SCHEMA.EX', \"expressions\" = "
            + "CURRENT_ACCOUNT_NAME() || '.TEST_DB.TEST_SCHEMA.EX', \"expressions\" = "
            + "CURRENT_ORGANIZATION_NAME() || '.' || CURRENT_ACCOUNT_NAME() || '.TEST_DB.TEST_SCHEMA.EX', "
            + "\"expressions\" LIKE '%.TEST_DB.TEST_SCHEMA.EX', REGEXP_COUNT(\"expressions\", '[.]') FROM "
            + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"operation\" = 'CreateTableAsSelect'");
        answer("EXPLAIN CREATE MATERIALIZED VIEW test_db.test_schema.ew AS SELECT a FROM et");
        assertAnswer("true|3",
            "SELECT \"expressions\" LIKE '%.TEST_DB.TEST_SCHEMA.EW', REGEXP_COUNT(\"expressions\", '[.]') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"operation\" = 'CreateTableAsSelect'");
        assertAnswer("REFUSED SQL compilation error: | Object 'ET' already exists.", "EXPLAIN CREATE TABLE et (a INT)");
        assertAnswer("REFUSED SQL compilation error: | Object 'NOSUCH_T' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "EXPLAIN CREATE TABLE ex AS SELECT a FROM nosuch_t");
        assertAnswer("REFUSED SQL compilation error: | Unknown function NOSUCH_FN.",
            "EXPLAIN CREATE TABLE ex AS SELECT a FROM et WHERE nosuch_fn(a) > 0");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 33 | invalid identifier 'NOSUCH'",
            "EXPLAIN CREATE VIEW ev AS SELECT nosuch FROM et");
        assertAnswer("REFUSED SQL compilation error: | Object 'ET' already exists as TABLE",
            "EXPLAIN CREATE VIEW et AS SELECT 1 AS a");
        assertAnswer("REFUSED SQL compilation error: | Object 'EV0' already exists.",
            "EXPLAIN CREATE VIEW ev0 AS SELECT 1 AS a");
        assertAnswer("REFUSED SQL compilation error: | Object 'ES0' already exists.", "EXPLAIN CREATE SEQUENCE es0");
        assertAnswer("REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "EXPLAIN ALTER TABLE nosuch_t ADD COLUMN z INT");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN ALTER TABLE et ADD COLUMN a INT");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN ALTER TABLE et DROP COLUMN nosuch_c");
        assertAnswer("REFUSED SQL compilation error: | Object 'EU' already exists.",
            "EXPLAIN ALTER TABLE et RENAME TO eu");
        assertAnswer("REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "EXPLAIN TRUNCATE TABLE nosuch_t");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN TRUNCATE TABLE IF EXISTS nosuch_t");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN UNDROP TABLE nosuch_t");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN UNDROP TABLE et");
        assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "EXPLAIN SHOW TABLES IN SCHEMA nosuch_s");
        assertAnswer("REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "EXPLAIN SHOW COLUMNS IN TABLE nosuch_t");
        assertAnswer("REFUSED SQL compilation error: | Sequence 'TEST_DB.TEST_SCHEMA.NOSUCH_SEQ' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on SEQUENCE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SEQ.",
            "EXPLAIN DROP SEQUENCE nosuch_seq");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN DROP SEQUENCE es0");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN DROP VIEW ev0");
        assertAnswer("REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type 'VIEW'.",
            "EXPLAIN DROP VIEW et");
        assertAnswer("REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'.",
            "EXPLAIN DROP TABLE ev0");
        assertAnswer("REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.NOSUCH_F' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_F.",
            "EXPLAIN DROP FUNCTION nosuch_f()");
        assertAnswer("REFUSED SQL compilation error: | Object 'NOSUCH_T' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "EXPLAIN CREATE TABLE ex CLONE nosuch_t");
        assertAnswer("REFUSED SQL compilation error: | Object 'NOSUCH_T' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "EXPLAIN CREATE TABLE ex LIKE nosuch_t");
        assertAnswer("REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "EXPLAIN CREATE STREAM est2 ON TABLE nosuch_t");
        assertAnswer("REFUSED SQL compilation error: | Unknown function NOSUCH_FN.", "EXPLAIN SET v = nosuch_fn()");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 24 | invalid identifier 'NOSUCH'",
            "EXPLAIN SET v = (SELECT nosuch FROM et)");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN SET v = (SELECT COUNT(*) FROM et)");
        assertAnswer("REFUSED SQL compilation error: | Stage 'TEST_DB.TEST_SCHEMA.NOSUCH_STAGE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_STAGE.",
            "EXPLAIN LIST @nosuch_stage");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'REMOVE'.",
            "EXPLAIN REMOVE @%et");
        assertAnswer("REFUSED SQL compilation error: | invalid parameter 'nosuch_param'",
            "EXPLAIN ALTER SESSION SET nosuch_param = 1");
        assertAnswer("REFUSED SQL compilation error: | Sequence 'TEST_DB.TEST_SCHEMA.NOSUCH_SEQ' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on SEQUENCE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SEQ.",
            "EXPLAIN DESCRIBE SEQUENCE nosuch_seq");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN DESCRIBE VIEW ev0");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN DESC VIEW et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN DESCRIBE TABLE ev0");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN SHOW SEQUENCES");
        assertAnswer("REFUSED SQL compilation error: | Object 'TEST_SCHEMA' already exists.",
            "EXPLAIN CREATE SCHEMA test_schema");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN DROP SCHEMA test_schema");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN ALTER TABLE et SET COMMENT = 'c'");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN ALTER VIEW ev0 SET COMMENT = 'c'");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|1|null|null|null "
                + ";; 1|1|[0]|Generator|null|null|1|null|null|null",
            "EXPLAIN SELECT 1;");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EX|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN CREATE TABLE ex AS SELECT a FROM et;");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN SHOW TABLES;");
        answer("EXPLAIN MERGE INTO et USING eu ON et.a = eu.a WHEN MATCHED THEN DELETE");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EU|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|null|A|0|0|0",
            "EXPLAIN CREATE OR REPLACE TABLE eu AS SELECT a FROM eu");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'FOR'.",
            "EXPLAIN CREATE TABLE ex AS SELECT a FROM et FOR UPDATE");
        answer("EXPLAIN CREATE TABLE ex AS VALUES (1)");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EX|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|null|A, B|1|1|1024",
            "EXPLAIN CREATE TABLE ex AS TABLE et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN SHOW TERSE TABLES");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN DESCRIBE RESULT LAST_QUERY_ID()");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN SHOW USER FUNCTIONS");
        assertAnswer("REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.NOSUCH_F' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_F.",
            "EXPLAIN DESCRIBE FUNCTION nosuch_f()");
        assertAnswer("", "SHOW TABLES LIKE 'EX'");
        assertAnswer("2", "SELECT COUNT(*) FROM et");
        answer("SHOW SEQUENCES LIKE 'ES0'");
        answer("SHOW VIEWS LIKE 'EV0'");
    }

    @Test
    public void anObjectNamedWithItsDatabaseIsNamedWithTheAccountFirst() {
        engine.execute("CREATE OR REPLACE TABLE et (a INT, b VARCHAR)");
        engine.execute("INSERT INTO et VALUES (1, 'x'), (2, 'y')");
        answer("EXPLAIN CREATE TABLE test_db.test_schema.ex AS SELECT a FROM et");
        assertAnswer("true|3", "SELECT \"expressions\" = CURRENT_ACCOUNT() || '.TEST_DB.TEST_SCHEMA.EX', "
            + "REGEXP_COUNT(\"expressions\", '[.]') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) "
            + "WHERE \"operation\" = 'CreateTableAsSelect'");
        answer("EXPLAIN CREATE MATERIALIZED VIEW test_db.test_schema.ew AS SELECT a FROM et");
        assertAnswer("true", "SELECT \"expressions\" = CURRENT_ACCOUNT() || '.TEST_DB.TEST_SCHEMA.EW' "
            + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"operation\" = 'CreateTableAsSelect'");
        answer("EXPLAIN CREATE TABLE test_schema.ex AS SELECT a FROM et");
        assertAnswer("TEST_DB.TEST_SCHEMA.EX", "SELECT \"expressions\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) "
            + "WHERE \"operation\" = 'CreateTableAsSelect'");
        assertAnswer("", "SHOW TABLES LIKE 'EX'");
    }
}
