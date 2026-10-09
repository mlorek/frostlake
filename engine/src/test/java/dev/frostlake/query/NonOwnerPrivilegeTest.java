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
 * What a statement needs on an object another role owns. No role is exempt: with a table, a view, a sequence,
 * a function, a procedure, a stage, a file format, a stream and a schema given away to a role the session does
 * not hold, ACCOUNTADMIN is refused with the privilege the statement asks for — SELECT to read, also as the
 * source of another statement and as the target of an UPDATE, a DELETE or a MERGE, which read first; INSERT,
 * UPDATE, DELETE and TRUNCATE to write; MODIFY to alter or comment; USAGE to call a routine or enter a
 * schema; READ, WRITE or any privilege at all for a stage — in the account's sentence, which names the
 * secondary roles when the session uses them. A view's query that reads such a table is refused under the
 * view's name. What only describes an object is answered. Every answer is live-verified.
 */
public class NonOwnerPrivilegeTest extends MeasuredCellsTestSupport {

    @Test
    public void whatEachStatementAsksOnAnotherRolesObjects() {
        try {
            engine.execute("USE ROLE ACCOUNTADMIN");
            engine.execute("USE SECONDARY ROLES NONE");
            engine.execute("DROP ROLE IF EXISTS flq_pv_r");
            engine.execute("CREATE ROLE flq_pv_r");
            engine.execute("CREATE OR REPLACE SCHEMA pv_s2");
            engine.execute("CREATE OR REPLACE TABLE pv_s2.t2 (a INT)");
            engine.execute("USE SCHEMA test_schema");
            engine.execute("CREATE OR REPLACE TABLE t1 (a INT, b INT)");
            engine.execute("INSERT INTO t1 VALUES (1, 2)");
            engine.execute("CREATE OR REPLACE TABLE kept (a INT)");
            engine.execute("INSERT INTO kept VALUES (1)");
            engine.execute("CREATE OR REPLACE VIEW v1 AS SELECT a FROM kept");
            engine.execute("CREATE OR REPLACE VIEW v_over_t1 AS SELECT a FROM t1");
            engine.execute("CREATE OR REPLACE SEQUENCE q1");
            engine.execute("CREATE OR REPLACE FUNCTION f1() RETURNS INT AS '1'");
            engine.execute("CREATE OR REPLACE PROCEDURE p1() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
            engine.execute("CREATE OR REPLACE STAGE st1");
            engine.execute("CREATE OR REPLACE STAGE mine_st");
            engine.execute("CREATE OR REPLACE FILE FORMAT ff1 TYPE = CSV");
            engine.execute("CREATE OR REPLACE STREAM sm1 ON TABLE kept");
            engine.execute("CREATE OR REPLACE STREAM sm_mine ON TABLE kept");
            engine.execute("GRANT OWNERSHIP ON TABLE t1 TO ROLE flq_pv_r COPY CURRENT GRANTS");
            engine.execute("GRANT OWNERSHIP ON VIEW v1 TO ROLE flq_pv_r COPY CURRENT GRANTS");
            engine.execute("GRANT OWNERSHIP ON SEQUENCE q1 TO ROLE flq_pv_r COPY CURRENT GRANTS");
            engine.execute("GRANT OWNERSHIP ON FUNCTION f1() TO ROLE flq_pv_r COPY CURRENT GRANTS");
            engine.execute("GRANT OWNERSHIP ON PROCEDURE p1() TO ROLE flq_pv_r COPY CURRENT GRANTS");
            engine.execute("GRANT OWNERSHIP ON STAGE st1 TO ROLE flq_pv_r COPY CURRENT GRANTS");
            engine.execute("GRANT OWNERSHIP ON FILE FORMAT ff1 TO ROLE flq_pv_r COPY CURRENT GRANTS");
            engine.execute("GRANT OWNERSHIP ON STREAM sm1 TO ROLE flq_pv_r COPY CURRENT GRANTS");
            engine.execute("GRANT OWNERSHIP ON SCHEMA pv_s2 TO ROLE flq_pv_r COPY CURRENT GRANTS");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "SELECT * FROM t1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "SELECT COUNT(*) FROM t1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "SELECT k.a FROM kept k JOIN t1 ON t1.a = k.a");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "SELECT (SELECT MAX(a) FROM t1)");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "SELECT * FROM t1 WHERE 1 = 0");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "SELECT * FROM test_db.test_schema.t1");
            assertAnswer(
                "REFUSED SQL compilation error: | Failure during expansion of view 'V_OVER_T1': SQL access "
                + "control error: | Insufficient privileges to operate on table 'T1'. Your primary role <ROLE> "
                + "must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "SELECT * FROM v_over_t1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have INSERT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "INSERT INTO t1 VALUES (3, 4)");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "INSERT INTO kept SELECT a FROM t1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have INSERT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "INSERT INTO t1 SELECT 1, 2");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "UPDATE t1 SET b = 5");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "UPDATE kept SET a = t1.b FROM t1 WHERE t1.a = kept.a");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "DELETE FROM t1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "DELETE FROM t1 WHERE a = 99");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have TRUNCATE granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "TRUNCATE TABLE t1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "MERGE INTO t1 USING kept k ON t1.a = k.a WHEN MATCHED THEN UPDATE SET b = 9");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "MERGE INTO t1 USING kept k ON FALSE WHEN NOT MATCHED THEN INSERT (a) VALUES (1)");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "MERGE INTO t1 USING kept k ON t1.a = k.a WHEN MATCHED THEN DELETE");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "MERGE INTO kept USING t1 ON t1.a = kept.a WHEN MATCHED THEN UPDATE SET a = 1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have INSERT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "COPY INTO t1 FROM @mine_st");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have MODIFY granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "ALTER TABLE t1 ADD COLUMN c INT");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have MODIFY granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "ALTER TABLE t1 DROP COLUMN b");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have MODIFY granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "ALTER TABLE t1 RENAME COLUMN b TO bb");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have MODIFY granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "ALTER TABLE t1 ALTER COLUMN b COMMENT 'c'");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have MODIFY granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "ALTER TABLE t1 SET COMMENT = 'x'");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have MODIFY granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "ALTER TABLE t1 CLUSTER BY (a)");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have MODIFY granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "ALTER TABLE t1 SET DATA_RETENTION_TIME_IN_DAYS = 0");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have MODIFY granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "ALTER TABLE t1 ADD CONSTRAINT t1_pk PRIMARY KEY (a)");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have MODIFY granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "COMMENT ON TABLE t1 IS 'x'");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have MODIFY granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "COMMENT ON COLUMN t1.a IS 'x'");
            assertAnswer(
                "A|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "B|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
                "DESCRIBE TABLE t1");
            engine.execute("SHOW TABLES LIKE 'T1' IN SCHEMA test_schema");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "T1|TEST_SCHEMA|A|{\"type\":\"FIXED\",\"precision\":38,\"scale\":0,\"nullable\":true}|true||COLUMN|||TEST_DB||null|null ;; T1|TEST_SCHEMA|B|{\"type\":\"FIXED\",\"precision\":38,\"scale\":0,\"nullable\":true}|true||COLUMN|||TEST_DB||null|null",
                "SHOW COLUMNS IN TABLE t1");
            assertAnswer(
                "create or replace TABLE T1 ( | 	A NUMBER(38,0), | 	B NUMBER(38,0) | );",
                "SELECT GET_DDL('TABLE', 't1')");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "CREATE OR REPLACE STREAM s_new ON TABLE t1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "CREATE OR REPLACE TABLE c_new CLONE t1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "CREATE OR REPLACE TABLE l_new LIKE t1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "CREATE OR REPLACE TABLE cc_new AS SELECT * FROM t1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "CREATE OR REPLACE VIEW vv_new AS SELECT * FROM t1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "CREATE OR REPLACE MATERIALIZED VIEW mm_new AS SELECT a FROM t1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on view 'V1'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.V1.",
                "SELECT * FROM v1");
            assertAnswer(
                "A|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
                "DESCRIBE VIEW v1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on view 'V1'. Your "
                + "primary role <ROLE> must have MODIFY granted on TABLE TEST_DB.TEST_SCHEMA.V1.",
                "ALTER VIEW v1 SET COMMENT = 'x'");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on view 'V1'. Your "
                + "primary role <ROLE> must have MODIFY granted on TABLE TEST_DB.TEST_SCHEMA.V1.",
                "COMMENT ON VIEW v1 IS 'x'");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on view 'V1'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.V1.",
                "CREATE OR REPLACE VIEW v_new AS SELECT * FROM v1");
            assertAnswer(
                "1",
                "SELECT q1.NEXTVAL");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on sequence 'Q1'. Your "
                + "primary role <ROLE> must have MODIFY granted on SEQUENCE TEST_DB.TEST_SCHEMA.Q1.",
                "ALTER SEQUENCE q1 SET INCREMENT 2");
            assertAnswer(
                "Table Q_NEW successfully created.",
                "CREATE OR REPLACE TABLE q_new (a INT DEFAULT q1.NEXTVAL)");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on function 'F1'. Your "
                + "primary role <ROLE> must have USAGE granted on FUNCTION TEST_DB.TEST_SCHEMA.F1().",
                "SELECT f1()");
            assertAnswer(
                "signature|() ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
                "DESCRIBE FUNCTION f1()");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on function 'F1'. Your "
                + "primary role <ROLE> must have MODIFY granted on FUNCTION TEST_DB.TEST_SCHEMA.F1().",
                "ALTER FUNCTION f1() SET COMMENT = 'x'");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on procedure 'P1'. Your "
                + "primary role <ROLE> must have USAGE granted on PROCEDURE TEST_DB.TEST_SCHEMA.P1().",
                "CALL p1()");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on stage 'ST1'. Your "
                + "primary role <ROLE> must have at least one privilege granted on STAGE TEST_DB.TEST_SCHEMA.ST1.",
                "LIST @st1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on stage 'ST1'. Your "
                + "primary role <ROLE> must have WRITE granted on STAGE TEST_DB.TEST_SCHEMA.ST1.",
                "REMOVE @st1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on stage 'ST1'. Your "
                + "primary role <ROLE> must have WRITE granted on STAGE TEST_DB.TEST_SCHEMA.ST1.",
                "COPY INTO @st1/x FROM (SELECT 1)");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on stage 'ST1'. Your "
                + "primary role <ROLE> must have READ granted on STAGE TEST_DB.TEST_SCHEMA.ST1.",
                "SELECT $1 FROM @st1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on stage 'ST1'. Your "
                + "primary role <ROLE> must have MODIFY granted on STAGE TEST_DB.TEST_SCHEMA.ST1.",
                "ALTER STAGE st1 SET COMMENT = 'x'");
            assertAnswer(
                "STAGE_FILE_FORMAT|TYPE|String|CSV|CSV ;; STAGE_FILE_FORMAT|RECORD_DELIMITER|String|\\n|\\n ;; "
                + "STAGE_FILE_FORMAT|FIELD_DELIMITER|String|,|, ;; STAGE_FILE_FORMAT|FILE_EXTENSION|String|| ;; "
                + "STAGE_FILE_FORMAT|SKIP_HEADER|Integer|0|0 ;; "
                + "STAGE_FILE_FORMAT|PARSE_HEADER|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|DATE_FORMAT|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|TIME_FORMAT|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|TIMESTAMP_FORMAT|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|BINARY_FORMAT|String|HEX|HEX ;; STAGE_FILE_FORMAT|ESCAPE|String|NONE|NONE "
                + ";; STAGE_FILE_FORMAT|ESCAPE_UNENCLOSED_FIELD|String|\\\\|\\\\ ;; "
                + "STAGE_FILE_FORMAT|TRIM_SPACE|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|FIELD_OPTIONALLY_ENCLOSED_BY|String|NONE|NONE ;; "
                + "STAGE_FILE_FORMAT|NULL_IF|List|[\\\\N]|[\\\\N] ;; STAGE_FILE_FORMAT|COMPRESSION|String|AUTO|AUTO "
                + ";; STAGE_FILE_FORMAT|ERROR_ON_COLUMN_COUNT_MISMATCH|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|VALIDATE_UTF8|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|SKIP_BLANK_LINES|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|REPLACE_INVALID_CHARACTERS|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|EMPTY_FIELD_AS_NULL|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|SKIP_BYTE_ORDER_MARK|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|ENCODING|String|UTF8|UTF8 ;; STAGE_FILE_FORMAT|MULTI_LINE|Boolean|true|true "
                + ";; STAGE_COPY_OPTIONS|ON_ERROR|String|ABORT_STATEMENT|ABORT_STATEMENT ;; "
                + "STAGE_COPY_OPTIONS|SIZE_LIMIT|Long|| ;; STAGE_COPY_OPTIONS|PURGE|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|RETURN_FAILED_ONLY|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|ENFORCE_LENGTH|Boolean|true|true ;; "
                + "STAGE_COPY_OPTIONS|TRUNCATECOLUMNS|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|FORCE|Boolean|false|false ;; STAGE_LOCATION|URL|String|| ;; "
                + "DIRECTORY|ENABLE|Boolean|false|false ;; DIRECTORY|AUTO_REFRESH|Boolean|false|false",
                "DESCRIBE STAGE st1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on stage 'ST1'. Your "
                + "primary role <ROLE> must have READ granted on STAGE TEST_DB.TEST_SCHEMA.ST1.",
                "COPY INTO kept FROM @st1");
            assertAnswer(
                "TYPE|String|CSV|CSV ;; RECORD_DELIMITER|String|\\n|\\n ;; FIELD_DELIMITER|String|,|, ;; "
                + "FILE_EXTENSION|String|| ;; SKIP_HEADER|Integer|0|0 ;; PARSE_HEADER|Boolean|false|false ;; "
                + "DATE_FORMAT|String|AUTO|AUTO ;; TIME_FORMAT|String|AUTO|AUTO ;; "
                + "TIMESTAMP_FORMAT|String|AUTO|AUTO ;; BINARY_FORMAT|String|HEX|HEX ;; ESCAPE|String|NONE|NONE "
                + ";; ESCAPE_UNENCLOSED_FIELD|String|\\\\|\\\\ ;; TRIM_SPACE|Boolean|false|false ;; "
                + "FIELD_OPTIONALLY_ENCLOSED_BY|String|NONE|NONE ;; NULL_IF|List|[\\\\N]|[\\\\N] ;; "
                + "COMPRESSION|String|AUTO|AUTO ;; ERROR_ON_COLUMN_COUNT_MISMATCH|Boolean|true|true ;; "
                + "VALIDATE_UTF8|Boolean|true|true ;; SKIP_BLANK_LINES|Boolean|false|false ;; "
                + "REPLACE_INVALID_CHARACTERS|Boolean|false|false ;; EMPTY_FIELD_AS_NULL|Boolean|true|true ;; "
                + "SKIP_BYTE_ORDER_MARK|Boolean|true|true ;; ENCODING|String|UTF8|UTF8 ;; "
                + "MULTI_LINE|Boolean|true|true",
                "DESCRIBE FILE FORMAT ff1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on file_format 'FF1'. "
                + "Your primary role <ROLE> must have MODIFY granted on FILE FORMAT TEST_DB.TEST_SCHEMA.FF1.",
                "ALTER FILE FORMAT ff1 SET COMMENT = 'x'");
            assertAnswer(
                "",
                "SELECT $1 FROM @mine_st (FILE_FORMAT => 'ff1')");
            assertAnswer(
                "Stage area ST_NEW successfully created.",
                "CREATE OR REPLACE STAGE st_new FILE_FORMAT = ff1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on stream 'SM1'. Your "
                + "primary role <ROLE> must have SELECT granted on STREAM TEST_DB.TEST_SCHEMA.SM1.",
                "SELECT * FROM sm1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'PV_S2'. Your "
                + "primary role <ROLE> must have USAGE granted on SCHEMA TEST_DB.PV_S2.",
                "USE SCHEMA pv_s2");
            assertAnswer(
                "",
                "SELECT * FROM pv_s2.t2");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'PV_S2'. Your "
                + "primary role <ROLE> must have CREATE TABLE granted on SCHEMA TEST_DB.PV_S2.",
                "CREATE TABLE pv_s2.x_new (a INT)");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'PV_S2'. Your "
                + "primary role <ROLE> must have MODIFY granted on SCHEMA TEST_DB.PV_S2.",
                "ALTER SCHEMA pv_s2 SET COMMENT = 'x'");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'PV_S2'. Your "
                + "primary role <ROLE> must have MONITOR granted on SCHEMA TEST_DB.PV_S2.",
                "DESCRIBE SCHEMA pv_s2");
            assertAnswer(
                "T2 successfully dropped.",
                "DROP TABLE pv_s2.t2");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON STREAM sm_mine TO ROLE flq_pv_r");
            engine.execute("USE SECONDARY ROLES ALL");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role ACCOUNTADMIN or one of your secondary roles must have SELECT granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.T1.",
                "SELECT * FROM t1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role ACCOUNTADMIN or one of your secondary roles must have INSERT granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.T1.",
                "INSERT INTO t1 VALUES (3, 4)");
            engine.execute("USE SECONDARY ROLES NONE");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT SELECT ON TABLE t1 TO ROLE ACCOUNTADMIN");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT ROLE flq_pv_r TO ROLE ACCOUNTADMIN");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM t1");
            assertAnswer(
                "1",
                "INSERT INTO t1 VALUES (3, 4)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER TABLE t1 SET COMMENT = 'x'");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.PV_S2.T2' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.PV_S2.T2.",
                "SELECT COUNT(*) FROM pv_s2.t2");
        } finally {
            engine.execute("USE ROLE ACCOUNTADMIN");
            engine.execute("USE SECONDARY ROLES NONE");
            engine.execute("GRANT ROLE flq_pv_r TO ROLE ACCOUNTADMIN");
            engine.execute("DROP ROLE IF EXISTS flq_pv_r");
        }
    }

    @Test
    public void eachPrivilegeOpensWhatItNamesAndNoMore() {
        try {
            engine.execute("USE ROLE ACCOUNTADMIN");
            engine.execute("USE SECONDARY ROLES NONE");
            engine.execute("DROP ROLE IF EXISTS flq_pw_a");
            engine.execute("DROP ROLE IF EXISTS flq_pw_b");
            engine.execute("CREATE ROLE flq_pw_a");
            engine.execute("CREATE ROLE flq_pw_b");
            engine.execute("CREATE OR REPLACE SCHEMA pw_s2");
            engine.execute("CREATE OR REPLACE TABLE pw_s2.t2 (a INT)");
            engine.execute("USE SCHEMA test_schema");
            engine.execute("CREATE OR REPLACE TABLE t1 (a INT, b INT)");
            engine.execute("INSERT INTO t1 VALUES (1, 2)");
            engine.execute("CREATE OR REPLACE TABLE t1b (a INT)");
            engine.execute("CREATE OR REPLACE TABLE kept (a INT)");
            engine.execute("INSERT INTO kept VALUES (1)");
            engine.execute("CREATE OR REPLACE VIEW vb AS SELECT a FROM t1");
            engine.execute("GRANT OWNERSHIP ON TABLE t1 TO ROLE flq_pw_a COPY CURRENT GRANTS");
            engine.execute("GRANT OWNERSHIP ON TABLE t1b TO ROLE flq_pw_a COPY CURRENT GRANTS");
            engine.execute("GRANT OWNERSHIP ON VIEW vb TO ROLE flq_pw_b COPY CURRENT GRANTS");
            engine.execute("GRANT OWNERSHIP ON TABLE pw_s2.t2 TO ROLE flq_pw_a COPY CURRENT GRANTS");
            engine.execute("GRANT OWNERSHIP ON SCHEMA pw_s2 TO ROLE flq_pw_a COPY CURRENT GRANTS");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1B'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1B.",
                "INSERT INTO t1 SELECT a FROM t1b");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T2'. Your "
                + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.PW_S2.T2.",
                "SELECT * FROM pw_s2.t2");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T2'. Your "
                + "primary role <ROLE> must have INSERT granted on TABLE TEST_DB.PW_S2.T2.",
                "INSERT INTO pw_s2.t2 VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Failure during expansion of view 'VB': SQL access control "
                + "error: | Insufficient privileges to operate on table 'T1'. Your primary role <ROLE> must have "
                + "SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "SELECT * FROM vb");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'PW_S2'. Your "
                + "primary role <ROLE> must have MODIFY granted on SCHEMA TEST_DB.PW_S2.",
                "COMMENT ON SCHEMA pw_s2 IS 'x'");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT SELECT ON VIEW vb TO ROLE ACCOUNTADMIN");
            assertAnswer(
                "REFUSED SQL compilation error: | Failure during expansion of view 'VB': SQL access control "
                + "error: | Insufficient privileges to operate on table 'T1'. Your primary role <ROLE> must have "
                + "SELECT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "SELECT * FROM vb");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT SELECT ON TABLE t1 TO ROLE ACCOUNTADMIN");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM t1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have UPDATE granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "UPDATE t1 SET b = 5");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have DELETE granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "DELETE FROM t1 WHERE a = 99");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have UPDATE granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "MERGE INTO t1 USING kept k ON t1.a = k.a WHEN MATCHED THEN UPDATE SET b = 9");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have INSERT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "MERGE INTO t1 USING kept k ON FALSE WHEN NOT MATCHED THEN INSERT (a) VALUES (1)");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have DELETE granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "MERGE INTO t1 USING kept k ON t1.a = 99 WHEN MATCHED THEN DELETE");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have INSERT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "MERGE INTO t1 USING kept k ON t1.a = 99 WHEN MATCHED THEN DELETE WHEN NOT MATCHED THEN INSERT "
                + "(a) VALUES (7)");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have INSERT granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "INSERT INTO t1 VALUES (3, 4)");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have TRUNCATE granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "TRUNCATE TABLE t1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have MODIFY granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "ALTER TABLE t1 SET COMMENT = 'x'");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on stream source without "
                + "CHANGE_TRACKING enabled 'T1'. Your primary role <ROLE> must have MODIFY granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.T1.",
                "CREATE OR REPLACE STREAM s_new ON TABLE t1");
            assertAnswer(
                "Table C_NEW successfully created.",
                "CREATE OR REPLACE TABLE c_new CLONE t1");
            assertAnswer(
                "Table L_NEW successfully created.",
                "CREATE OR REPLACE TABLE l_new LIKE t1");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT UPDATE ON TABLE t1 TO ROLE ACCOUNTADMIN");
            assertAnswer(
                "1|0",
                "UPDATE t1 SET b = 5");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have DELETE granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "DELETE FROM t1 WHERE a = 99");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT INSERT ON TABLE t1 TO ROLE PUBLIC");
            assertAnswer(
                "1",
                "INSERT INTO t1 VALUES (3, 4)");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE INSERT ON TABLE t1 FROM ROLE PUBLIC");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT ALL ON TABLE t1 TO ROLE ACCOUNTADMIN");
            assertAnswer(
                "0",
                "DELETE FROM t1 WHERE a = 99");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE t1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have MODIFY granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "ALTER TABLE t1 SET COMMENT = 'x'");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'T1'. Your "
                + "primary role <ROLE> must have OWNERSHIP granted on TABLE TEST_DB.TEST_SCHEMA.T1.",
                "DROP TABLE t1");
        } finally {
            engine.execute("USE ROLE ACCOUNTADMIN");
            engine.execute("GRANT ROLE flq_pw_a TO ROLE ACCOUNTADMIN");
            engine.execute("GRANT ROLE flq_pw_b TO ROLE ACCOUNTADMIN");
            engine.execute("DROP ROLE IF EXISTS flq_pw_a");
            engine.execute("DROP ROLE IF EXISTS flq_pw_b");
        }
    }
}
