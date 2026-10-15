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
package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The kind a statement is named by where a SQL function's block body may not hold it: a query, an EXPLAIN and a
 * CALL are SELECT, a load is INSERT and an unload UNLOAD, a CREATE, an ALTER and a rename are named by what
 * they make or change where the account tells that apart and CREATE, ALTER, RENAME or DROP otherwise, an
 * EXECUTE IMMEDIATE is UNKNOWN, and a cursor's statements and an AWAIT have sentences of their own. The first
 * such statement as written is the one named, in a branch, a loop, a handler or a nested block too, quoted and
 * unquoted bodies alike (live-verified).
 */
public class SnowscriptStatementKindTest extends MeasuredCellsTestSupport {

    @Test
    public void aQuotedAndAnUnquotedBody() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN SELECT 1; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN SELECT 1; RETURN 1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: INSERT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN INSERT INTO t VALUES (3, 4); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: INSERT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN INSERT INTO t VALUES (3, 4); RETURN 1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: UPDATE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN UPDATE t SET a = 1; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: UPDATE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN UPDATE t SET a = 1; RETURN 1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DELETE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN DELETE FROM t; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DELETE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN DELETE FROM t; RETURN 1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: MERGE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN MERGE INTO t USING t s ON t.a = s.a WHEN MATCHED "
                + "THEN UPDATE SET b = 1; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: MERGE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN MERGE INTO t USING t s ON t.a = s.a WHEN "
                + "MATCHED THEN UPDATE SET b = 1; RETURN 1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_TABLE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE TABLE z (x INT); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_TABLE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN CREATE TABLE z (x INT); RETURN 1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DROP",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN DROP TABLE z; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DROP",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN DROP TABLE z; RETURN 1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CALL p(); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN CALL p(); RETURN 1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SHOW",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN SHOW TABLES; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SHOW",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN SHOW TABLES; RETURN 1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DESCRIBE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN DESCRIBE TABLE t; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DESCRIBE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN DESCRIBE TABLE t; RETURN 1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: UNKNOWN",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN EXECUTE IMMEDIATE 'SELECT 1'; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: UNKNOWN",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN EXECUTE IMMEDIATE 'SELECT 1'; RETURN 1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: TRUNCATE_TABLE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN TRUNCATE TABLE t; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: TRUNCATE_TABLE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN TRUNCATE TABLE t; RETURN 1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: USE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN USE SCHEMA test_schema; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: USE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN USE SCHEMA test_schema; RETURN 1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SET",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN SET v = 1; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SET",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN SET v = 1; RETURN 1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN WITH c AS (SELECT 1) SELECT * FROM c; RETURN 1; "
                + "END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN WITH c AS (SELECT 1) SELECT * FROM c; RETURN "
                + "1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER_TABLE_ADD_COLUMN",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t ADD COLUMN c INT; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER_TABLE_ADD_COLUMN",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN ALTER TABLE t ADD COLUMN c INT; RETURN 1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: resultset declaration",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN LET r RESULTSET := (SELECT 1); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: resultset declaration",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN LET r RESULTSET := (SELECT 1); RETURN 1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: select into statement",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN SELECT a INTO :x FROM t; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: select into statement",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN SELECT a INTO :x FROM t; RETURN 1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_VIEW",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE VIEW vz AS SELECT 1; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_VIEW",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN CREATE VIEW vz AS SELECT 1; RETURN 1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: INSERT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN COPY INTO t FROM @s; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: INSERT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN COPY INTO t FROM @s; RETURN 1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN SELECT 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN SELECT 1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN LET x := 1; SELECT 1; INSERT INTO t VALUES (3, "
                + "4); RETURN x; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN IF (TRUE) THEN SELECT 1; END IF; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: INSERT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN IF (TRUE) THEN INSERT INTO t VALUES (3, 4); "
                + "END IF; RETURN 1; END$$");
    }

    @Test
    public void createsDropsAndAlters() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_TABLE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE OR REPLACE TABLE z (x INT); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_TABLE_AS_SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE TABLE z AS SELECT 1 x; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_TABLE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE TEMPORARY TABLE z (x INT); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_TABLE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE TABLE z LIKE t; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_TABLE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE TABLE z CLONE t; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_TABLE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE TABLE IF NOT EXISTS z (x INT); RETURN 1; "
                + "END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_VIEW",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE OR REPLACE VIEW vz AS SELECT 1 x; RETURN "
                + "1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_VIEW",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE SECURE VIEW vz AS SELECT 1 x; RETURN 1; "
                + "END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_TABLE_AS_SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE MATERIALIZED VIEW mz AS SELECT a FROM t; "
                + "RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_SEQUENCE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE SEQUENCE sq; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE FUNCTION g() RETURNS INT AS '1'; RETURN "
                + "1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE PROCEDURE pz() RETURNS INT LANGUAGE SQL "
                + "AS 'BEGIN RETURN 1; END'; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE STAGE sg; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_STREAM",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE STREAM sz ON TABLE t; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE FILE FORMAT ff TYPE = CSV; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_TAG",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE TAG tg; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_TABLE_AS_SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE DYNAMIC TABLE dz TARGET_LAG = DOWNSTREAM "
                + "WAREHOUSE = nosuch_sig_zq_7f3a91 AS SELECT a FROM t; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DROP",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN DROP VIEW vz; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DROP",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN DROP TABLE IF EXISTS z; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DROP",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN DROP FUNCTION g(); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DROP",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN DROP SEQUENCE sq; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: RESTORE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN UNDROP TABLE z; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER_TABLE_DROP_COLUMN",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t DROP COLUMN b; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: RENAME_TABLE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t RENAME TO t2; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: RENAME_COLUMN",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t RENAME COLUMN a TO c; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t SET COMMENT = 'x'; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_CONSTRAINT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t ADD PRIMARY KEY (a); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER_TABLE_MODIFY_COLUMN",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t ALTER COLUMN a SET NOT NULL; "
                + "RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER_TABLE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t CLUSTER BY (a); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER_TABLE_ADD_COLUMN",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t ADD c INT; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER_TABLE_ADD_COLUMN",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t ADD COLUMN c INT, d INT; RETURN 1; "
                + "END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t SWAP WITH t2; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t UNSET COMMENT; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER_TABLE_MODIFY_COLUMN",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t MODIFY COLUMN a COMMENT 'x'; "
                + "RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DROP_CONSTRAINT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t DROP PRIMARY KEY; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: RENAME_VIEW",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER VIEW vz RENAME TO vy; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER SEQUENCE sq SET INCREMENT = 2; RETURN 1; "
                + "END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER STAGE sg SET COMMENT = 'x'; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: UNSET",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN UNSET v; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN COMMENT ON TABLE t IS 'x'; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN EXPLAIN SELECT 1; RETURN 1; END");
    }

    @Test
    public void queriesWritesAndTheSession() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN SELECT 1 UNION SELECT 2; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN (SELECT 1); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: INSERT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN INSERT OVERWRITE INTO t VALUES (1, 2); RETURN 1; "
                + "END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: INSERT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN INSERT ALL INTO t VALUES (1, 2) SELECT 1; RETURN "
                + "1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: INSERT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN INSERT INTO t SELECT a, b FROM t; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: TRUNCATE_TABLE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN TRUNCATE t; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: TRUNCATE_TABLE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN TRUNCATE TABLE IF EXISTS t; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DESCRIBE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN DESC TABLE t; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DESCRIBE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN DESCRIBE VIEW vz; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DESCRIBE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN DESCRIBE FUNCTION g(); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SHOW",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN SHOW COLUMNS IN TABLE t; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SHOW",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN SHOW PARAMETERS; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SHOW",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN SHOW VARIABLES; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: USE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN USE DATABASE test_db; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SET",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN SET (v, w) = (1, 2); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DELETE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN DELETE FROM t WHERE a = 1; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: UPDATE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN UPDATE t SET a = 1 WHERE b = 2; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CALL SYSTEM$WAIT(0); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: UNKNOWN",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN EXECUTE IMMEDIATE 'SELECT ' || 1; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: select into statement",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN SELECT 1 INTO :x; RETURN 1; END");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN NULL; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: cursor declaration",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN LET c CURSOR FOR SELECT 1; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: await statement",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN AWAIT ALL; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ASYNC (SELECT 1); RETURN 1; END");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CONTINUE; RETURN 1; END");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN BREAK; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_TABLE_AS_SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN CREATE TABLE z AS SELECT 1 x; RETURN 1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER_TABLE_DROP_COLUMN",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN ALTER TABLE t DROP COLUMN b; RETURN 1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: UNSET",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN UNSET v; RETURN 1; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'VALUES'. | syntax error line 1 at position 15 unexpected '1'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN VALUES (1); RETURN 1; END$$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_SEQUENCE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN CREATE SEQUENCE sq; RETURN 1; END$$");
    }

    @Test
    public void theFirstStatementAsWritten() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: INSERT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN INSERT INTO t VALUES (3, 4); SELECT 1; RETURN 1; "
                + "END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DROP",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN IF (TRUE) THEN RETURN 1; ELSE DROP TABLE z; END "
                + "IF; SELECT 1; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN (SELECT 1); SELECT 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN SELECT 1; RETURN (SELECT 1); END");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCHFN(1)",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN nosuchfn(1); SELECT 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN SELECT 1; RETURN nosuchfn(1); END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: resultset declaration",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN LET r RESULTSET := (SELECT 1); SELECT 1; RETURN "
                + "1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN SELECT 1; LET r RESULTSET := (SELECT 1); RETURN "
                + "1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: UPDATE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN FOR i IN 1 TO 2 DO UPDATE t SET a = 1; END FOR; "
                + "RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DELETE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN 1; EXCEPTION WHEN OTHER THEN DELETE FROM "
                + "t; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SHOW",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN BEGIN SHOW TABLES; END; RETURN 1; END");
    }

    @Test
    public void moreCreatesAndAlters() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_TABLE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE TRANSIENT TABLE z (x INT); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_EVENT_TABLE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE EVENT TABLE ev; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_TABLE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE HYBRID TABLE hz (x INT PRIMARY KEY); "
                + "RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_ICEBERG_TABLE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE ICEBERG TABLE iz (x INT) CATALOG = "
                + "'SNOWFLAKE' EXTERNAL_VOLUME = 'nosuch_sig_zq_7f3a91' BASE_LOCATION = 'x'; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE SCHEMA nosuch_sig_zq_7f3a91; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_TASK",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE TASK tk AS SELECT 1; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_MASKING_POLICY",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE MASKING POLICY mp AS (v INT) RETURNS INT "
                + "-> v; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_ROW_ACCESS_POLICY",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE ROW ACCESS POLICY rp AS (v INT) RETURNS "
                + "BOOLEAN -> TRUE; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_TABLE_AS_SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE TABLE z (x INT) AS SELECT 1; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_VIEW",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE VIEW vz (x) AS SELECT 1; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_VIEW",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE RECURSIVE VIEW rv (x) AS SELECT 1; RETURN "
                + "1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_TABLE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE OR ALTER TABLE z (x INT); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_TABLE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE TABLE z (x INT) CLUSTER BY (x); RETURN 1; "
                + "END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE TEMPORARY STAGE sg; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CREATE OR REPLACE FUNCTION g() RETURNS INT AS "
                + "'1'; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DROP",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN DROP SCHEMA IF EXISTS nosuch_sig_zq_7f3a91; "
                + "RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: RESTORE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN UNDROP SCHEMA nosuch_sig_zq_7f3a91; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DROP",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN DROP STAGE sg; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DROP",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN DROP PROCEDURE pz(); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DROP_TAG",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN DROP TAG tg; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_CONSTRAINT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t ADD CONSTRAINT c UNIQUE (a); "
                + "RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DROP_CONSTRAINT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t DROP CONSTRAINT c; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: RENAME",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t RENAME CONSTRAINT c TO d; RETURN "
                + "1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER_TABLE_DROP_CLUSTERING_KEY",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t DROP CLUSTERING KEY; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_TABLE_AS_SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t ADD SEARCH OPTIMIZATION; RETURN 1; "
                + "END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER_TABLE_MODIFY_COLUMN",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t ALTER COLUMN a SET DATA TYPE "
                + "NUMBER(20); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER_TABLE_MODIFY_COLUMN",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t ALTER a DROP NOT NULL; RETURN 1; "
                + "END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER_TABLE_MODIFY_COLUMN",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t ALTER COLUMN a SET MASKING POLICY "
                + "mp; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER_SET_TAG",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t SET TAG tg = 'x'; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER_TABLE_MANAGE_ROW_ACCESS_POLICY",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t ADD ROW ACCESS POLICY rp ON (a); "
                + "RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t SET DATA_RETENTION_TIME_IN_DAYS = "
                + "1; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER_TABLE_DROP_COLUMN",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t DROP COLUMN a, b; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER_TABLE_ADD_COLUMN",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t ADD COLUMN IF NOT EXISTS c INT; "
                + "RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER_AUTO_RECLUSTER",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t SUSPEND RECLUSTER; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: RENAME_TABLE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE IF EXISTS t RENAME TO t2; RETURN 1; "
                + "END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER_TABLE_DROP_COLUMN",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t DROP b; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_CONSTRAINT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t ADD UNIQUE (a); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DROP_CONSTRAINT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t DROP UNIQUE (a); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_CONSTRAINT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TABLE t ADD FOREIGN KEY (a) REFERENCES t2 "
                + "(a); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER_VIEW_MODIFY_SECURITY",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER VIEW vz SET SECURE; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER VIEW vz SET COMMENT = 'x'; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: RENAME_FUNCTION",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER FUNCTION g() RENAME TO h; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER FUNCTION g() SET COMMENT = 'x'; RETURN 1; "
                + "END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: RENAME",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER PROCEDURE pz() RENAME TO pq; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER STREAM sz SET COMMENT = 'x'; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TASK tk SUSPEND; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: RENAME",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER TAG tg RENAME TO tg2; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: RENAME_SEQUENCE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER SEQUENCE sq RENAME TO sq2; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: RENAME_STAGE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER STAGE sg RENAME TO sg2; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: RENAME_FILE_FORMAT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER FILE FORMAT ff RENAME TO ff2; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: RENAME_SCHEMA",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER SCHEMA nosuch_sig_zq_7f3a91 RENAME TO "
                + "nosuch_purge_zq_7f3a91; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER_MATERIALIZED_VIEW_LIFECYCLE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER MATERIALIZED VIEW mz SUSPEND; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER_DYNAMIC_TABLE_LIFECYCLE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN ALTER DYNAMIC TABLE dz SUSPEND; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER_TABLE_MODIFY_COLUMN",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN COMMENT ON COLUMN t.a IS 'x'; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: ALTER",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN COMMENT IF EXISTS ON TABLE t IS 'x'; RETURN 1; "
                + "END");
    }

    @Test
    public void moreStatements() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: LIST_FILES",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN LIST @~; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN EXPLAIN USING TEXT SELECT 1; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DESCRIBE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN DESCRIBE RESULT LAST_QUERY_ID(); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: select into statement",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CALL p() INTO :x; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DELETE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN DELETE FROM t USING t s WHERE t.a = s.a; RETURN "
                + "1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: UPDATE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN UPDATE t SET a = s.a FROM t s WHERE t.b = s.b; "
                + "RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: INSERT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN COPY INTO t FROM (SELECT $1, $2 FROM @sg); "
                + "RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: UNLOAD",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN COPY INTO @sg FROM t; RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected 'VIEW'. | syntax "
                + "error line 1 at position 79 unexpected 'mz'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN TRUNCATE MATERIALIZED VIEW mz; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: UNKNOWN",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN EXECUTE IMMEDIATE FROM @sg/x.sql; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: open cursor statement",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN OPEN c; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: fetch cursor statement",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN FETCH c INTO x; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: close cursor statement",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN CLOSE c; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN x := (SELECT 1); RETURN 1; END");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN LET x := 1; RETURN 1; END");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN x := 1; RETURN 1; END");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN TABLE(r); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: select into statement",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN WITH c AS (SELECT 1 x) SELECT x INTO :y FROM c; "
                + "RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: INSERT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN INSERT INTO t (a) VALUES (1); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: MERGE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN MERGE INTO t USING t s ON t.a = s.a WHEN NOT "
                + "MATCHED THEN INSERT (a) VALUES (s.a); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT_FOR_UPDATE",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN SELECT 1 FROM t FOR UPDATE; RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN SELECT * FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: cursor declaration",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS DECLARE c CURSOR FOR SELECT 1; BEGIN RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: resultset declaration",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS DECLARE r RESULTSET; BEGIN RETURN 1; END");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS DECLARE e EXCEPTION; BEGIN RETURN 1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: cursor declaration",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS DECLARE c CURSOR FOR SELECT 1; BEGIN SELECT 1; RETURN "
                + "1; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN SELECT 1; OPEN c; RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'VALUES'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN 1; VALUES (1); END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'VALUES'.",
            "BEGIN VALUES (1); RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'VALUES'.",
            "BEGIN LET x := 1; VALUES (1); RETURN 1; END");
    }

    @Test
    public void aVariableAssignedAQuery() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN LET x := (SELECT 1); RETURN x; END");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN LET x INT := (SELECT 1); RETURN x; END");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"+\"((SELECT 1 AS \"1\" FROM (VALUES (null)) "
                + "DUAL), 1)",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN LET x := 0; x := (SELECT 1) + 1; RETURN x; END");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN LET x := 0; x := ((SELECT 1)); RETURN x; END");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS DECLARE x INT DEFAULT (SELECT 1); BEGIN RETURN x; END");
    }

    @Test
    public void aBodyThatIsOneStatement() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f_t1() RETURNS NUMBER AS $$ SELECT 1; $$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f_t1() RETURNS NUMBER AS $$ WITH c AS (SELECT 1 AS a) SELECT a FROM c; $$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f_t1() RETURNS NUMBER AS $$ (SELECT 1); $$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f_t1() RETURNS NUMBER AS 'SELECT 1;'");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f_t1() RETURNS NUMBER AS $$ SELECT 1;; $$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f_t1() RETURNS NUMBER AS $$ SELECT 1; -- c $$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SELECT",
            "CREATE OR REPLACE FUNCTION f_t1() RETURNS NUMBER AS $$ SELECT 1 UNION SELECT 2; $$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: INSERT",
            "CREATE OR REPLACE FUNCTION f_t1() RETURNS NUMBER AS $$ INSERT INTO t VALUES (1, 2); $$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: SHOW",
            "CREATE OR REPLACE FUNCTION f_t1() RETURNS NUMBER AS $$ SHOW TABLES; $$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'VALUES'. | syntax error line 1 at position 10 unexpected '1'.",
            "CREATE OR REPLACE FUNCTION f_t1() RETURNS NUMBER AS $$ VALUES (1); $$");
        assertAnswer(
            "REFUSED Actual statement count 2 did not match the desired statement count 1.",
            "CREATE OR REPLACE FUNCTION f_t1() RETURNS NUMBER AS $$ SELECT 1; SELECT 2; $$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: DELETE",
            "CREATE OR REPLACE FUNCTION f_t1() RETURNS NUMBER AS $$ DELETE FROM t; $$");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: CREATE_TABLE",
            "CREATE OR REPLACE FUNCTION f_t1() RETURNS NUMBER AS $$ CREATE TABLE z (x INT); $$");
    }
}
