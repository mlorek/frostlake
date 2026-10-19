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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What QUERY_HISTORY_BY_SESSION lists for a statement beyond its text and times (live-verified): its kind in the
 * account's words, the code of the error it failed with, the cells every statement carries alike, the transaction
 * it ran in, whether a warehouse ran it, and the rows it answered, inserted and produced — for a write the rows of
 * the table it wrote, as they stand once it is done.
 */
public class QueryHistoryKindsTest extends MeasuredCellsTestSupport {

    /** The latest row a statement of this text left in the session's history, as the columns read it. */
    private String listed(final String text, final String columns) {
        return answer("SELECT " + columns + " FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION(RESULT_LIMIT => "
            + "1000)) WHERE QUERY_TEXT = '" + text.replace("'", "''") + "' ORDER BY START_TIME DESC LIMIT 1");
    }

    /** Runs each statement, a refusal included, then asserts each one's latest row reads as measured. */
    private void assertListed(final String columns, final String[][] cells) {
        for (final String[] cell : cells) {
            answer(cell[0]);
        }
        for (final String[] cell : cells) {
            assertEquals(cell[1], listed(cell[0], columns), cell[0]);
        }
    }

    private static final String KIND = "QUERY_TYPE, EXECUTION_STATUS, ERROR_CODE";

    @Test
    public void eachStatementIsListedUnderTheAccountsKind() {
        assertListed(KIND, new String[][] {
            {"SET hk_v = 1", "SET|SUCCESS|null"},
            {"UNSET hk_v", "UNSET|SUCCESS|null"},
            {"CREATE TABLE hk_t (a INT)", "CREATE_TABLE|SUCCESS|null"},
            {"INSERT INTO hk_t VALUES (1), (2)", "INSERT|SUCCESS|null"},
            {"CREATE TABLE hk_c AS SELECT a FROM hk_t", "CREATE_TABLE_AS_SELECT|SUCCESS|null"},
            {"CREATE TABLE hk_l LIKE hk_t", "CREATE_TABLE|SUCCESS|null"},
            {"CREATE TABLE hk_k CLONE hk_t", "CREATE_TABLE|SUCCESS|null"},
            {"CREATE TEMPORARY TABLE hk_tmp (a INT)", "CREATE_TABLE|SUCCESS|null"},
            {"CREATE VIEW hk_v1 AS SELECT a FROM hk_t", "CREATE_VIEW|SUCCESS|null"},
            {"CREATE SECURE VIEW hk_v2 AS SELECT 1 AS x", "CREATE_VIEW|SUCCESS|null"},
            {"CREATE MATERIALIZED VIEW hk_mv AS SELECT a FROM hk_t", "CREATE_TABLE_AS_SELECT|SUCCESS|null"},
            {"CREATE SEQUENCE hk_seq", "CREATE_SEQUENCE|SUCCESS|null"},
            {"CREATE STREAM hk_st ON TABLE hk_t", "CREATE_STREAM|SUCCESS|null"},
            {"CREATE STAGE hk_stage", "CREATE|SUCCESS|null"},
            {"CREATE FUNCTION hk_f() RETURNS INT AS '1'", "CREATE|SUCCESS|null"},
            {"CREATE PROCEDURE hk_p() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'", "CREATE|SUCCESS|null"},
            {"CREATE FILE FORMAT hk_ff TYPE = CSV", "CREATE|SUCCESS|null"},
            {"CREATE SCHEMA hk_s", "CREATE|SUCCESS|null"},
            {"USE SCHEMA test_db.test_schema", "USE|SUCCESS|null"},
            {"INSERT OVERWRITE INTO hk_c SELECT 3", "INSERT|SUCCESS|null"},
            {"INSERT ALL INTO hk_t INTO hk_t SELECT 5", "INSERT|SUCCESS|null"},
            {"UPDATE hk_t SET a = 4 WHERE a = 5", "UPDATE|SUCCESS|null"},
            {"DELETE FROM hk_t WHERE a = 4", "DELETE|SUCCESS|null"},
            {"MERGE INTO hk_t USING (SELECT 9 AS x) s ON hk_t.a = s.x WHEN NOT MATCHED THEN INSERT (a) VALUES (s.x)",
                "MERGE|SUCCESS|null"},
            {"SELECT a FROM hk_t ORDER BY a", "SELECT|SUCCESS|null"},
            {"WITH c AS (SELECT 1 AS x) SELECT x FROM c", "SELECT|SUCCESS|null"},
            {"SHOW TABLES LIKE 'HK_T'", "SHOW|SUCCESS|null"},
            {"SHOW PARAMETERS LIKE 'TIMEZONE' IN SESSION", "SHOW|SUCCESS|null"},
            {"DESCRIBE TABLE hk_t", "DESCRIBE|SUCCESS|null"},
            {"DESC FUNCTION hk_f()", "DESCRIBE|SUCCESS|null"},
            {"ALTER SESSION SET TIMEZONE = 'UTC'", "ALTER_SESSION|SUCCESS|null"},
            {"ALTER SESSION UNSET TIMEZONE", "ALTER_SESSION|SUCCESS|null"},
            {"BEGIN", "BEGIN|SUCCESS|null"},
            {"COMMIT", "COMMIT|SUCCESS|null"},
            {"START TRANSACTION", "BEGIN|SUCCESS|null"},
            {"ROLLBACK", "ROLLBACK|SUCCESS|null"},
            {"TRUNCATE TABLE hk_c", "TRUNCATE_TABLE|SUCCESS|null"},
            {"TRUNCATE hk_l", "TRUNCATE_TABLE|SUCCESS|null"},
            {"ALTER TABLE hk_t ADD COLUMN b INT", "ALTER_TABLE_ADD_COLUMN|SUCCESS|null"},
            {"ALTER TABLE hk_l ALTER COLUMN a SET NOT NULL", "ALTER_TABLE_MODIFY_COLUMN|SUCCESS|null"},
            {"ALTER TABLE hk_t RENAME COLUMN b TO bb", "RENAME_COLUMN|SUCCESS|null"},
            {"ALTER TABLE hk_t DROP COLUMN bb", "ALTER_TABLE_DROP_COLUMN|SUCCESS|null"},
            {"ALTER TABLE hk_t SET COMMENT = 'x'", "ALTER|SUCCESS|null"},
            {"ALTER TABLE hk_t UNSET COMMENT", "ALTER|SUCCESS|null"},
            {"ALTER TABLE hk_t SET DATA_RETENTION_TIME_IN_DAYS = 1", "ALTER|SUCCESS|null"},
            {"ALTER TABLE hk_t CLUSTER BY (a)", "ALTER_TABLE|SUCCESS|null"},
            {"ALTER TABLE hk_t DROP CLUSTERING KEY", "ALTER_TABLE_DROP_CLUSTERING_KEY|SUCCESS|null"},
            {"ALTER TABLE hk_t ADD CONSTRAINT hk_pk PRIMARY KEY (a)", "CREATE CONSTRAINT|SUCCESS|null"},
            {"ALTER TABLE hk_t ADD SEARCH OPTIMIZATION", "CREATE_TABLE_AS_SELECT|SUCCESS|null"},
            {"ALTER TABLE hk_k SWAP WITH hk_l", "ALTER|SUCCESS|null"},
            {"ALTER TABLE hk_k RENAME TO hk_k2", "RENAME_TABLE|SUCCESS|null"},
            {"ALTER VIEW hk_v1 SET COMMENT = 'c'", "ALTER|SUCCESS|null"},
            {"ALTER VIEW hk_v1 SET SECURE", "ALTER_VIEW_MODIFY_SECURITY|SUCCESS|null"},
            {"ALTER VIEW hk_v1 RENAME TO hk_v3", "RENAME_VIEW|SUCCESS|null"},
            {"ALTER SEQUENCE hk_seq SET INCREMENT = 2", "ALTER|SUCCESS|null"},
            {"ALTER FUNCTION hk_f() SET COMMENT = 'c'", "ALTER|SUCCESS|null"},
            {"ALTER SCHEMA hk_s RENAME TO hk_s2", "RENAME_SCHEMA|SUCCESS|null"},
            {"COMMENT ON TABLE hk_t IS 'c'", "ALTER|SUCCESS|null"},
            {"COMMENT ON COLUMN hk_t.a IS 'c'", "ALTER_TABLE_MODIFY_COLUMN|SUCCESS|null"},
            {"COMMENT ON VIEW hk_v3 IS 'c'", "ALTER|SUCCESS|null"},
            {"EXPLAIN SELECT a FROM hk_t", "EXPLAIN|SUCCESS|null"},
            {"EXPLAIN USING TEXT SELECT a FROM hk_t", "EXPLAIN|SUCCESS|null"},
            {"CALL hk_p()", "CALL|SUCCESS|null"},
            {"EXECUTE IMMEDIATE 'SELECT 7'", "SELECT|SUCCESS|null"},
            {"EXECUTE IMMEDIATE 'CREATE TABLE hk_e (a INT)'", "CREATE_TABLE|SUCCESS|null"},
            {"EXECUTE IMMEDIATE $$BEGIN RETURN 1; END$$", "CALL|SUCCESS|null"},
            {"BEGIN RETURN 2; END", "CALL|SUCCESS|null"},
            {"DECLARE x INT DEFAULT 1; BEGIN RETURN x; END", "CALL|SUCCESS|null"},
            {"LIST @hk_stage", "LIST_FILES|SUCCESS|null"},
            {"REMOVE @hk_stage/x", "REMOVE_FILES|SUCCESS|null"},
            {"SELECT hk_seq.NEXTVAL", "SELECT|SUCCESS|null"},
            {"DROP VIEW hk_v3", "DROP|SUCCESS|null"},
            {"DROP MATERIALIZED VIEW hk_mv", "DROP|SUCCESS|null"},
            {"DROP STREAM hk_st", "DROP_STREAM|SUCCESS|null"},
            {"DROP TABLE hk_c", "DROP|SUCCESS|null"},
            {"UNDROP TABLE hk_c", "RESTORE|SUCCESS|null"},
            {"DROP STAGE hk_stage", "DROP|SUCCESS|null"},
            {"DROP FUNCTION hk_f()", "DROP|SUCCESS|null"},
            {"DROP SEQUENCE hk_seq", "DROP|SUCCESS|null"},
            {"DROP FILE FORMAT hk_ff", "DROP|SUCCESS|null"},
            {"DROP SCHEMA hk_s2", "DROP|SUCCESS|null"},
        });
    }

    @Test
    public void aFailureIsListedWithItsErrorsCode() {
        engine.execute("CREATE TABLE hf_t (a INT)");
        assertListed(KIND, new String[][] {
            {"SELECT 1 FROM hf_no_such_table", "SELECT|FAILED_WITH_ERROR|2003"},
            {"SELECT nosuch FROM hf_t", "SELECT|FAILED_WITH_ERROR|904"},
            {"INSERT INTO hf_t SELECT 1, 2", "INSERT|FAILED_WITH_ERROR|2020"},
            {"SELECT * FROM hf_t WHERE", "UNKNOWN|FAILED_WITH_ERROR|1003"},
            {"SELECT 1 / 0", "SELECT|FAILED_WITH_ERROR|100051"},
            {"SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "SELECT|FAILED_WITH_ERROR|711"},
            {"INSERT INTO hf_t SELECT 1 / 0", "INSERT|FAILED_WITH_ERROR|100051"},
        });
        assertAnswer("2020/21S01", "EXECUTE IMMEDIATE $$\nBEGIN\n  INSERT INTO hf_t SELECT 1, 2;\n"
            + "  RETURN 'inserted';\nEXCEPTION WHEN OTHER THEN\n  RETURN SQLCODE || '/' || SQLSTATE;\nEND;\n$$");
    }

    @Test
    public void aRefusedForUpdateLeavesItsOwnRowAlone() {
        engine.execute("CREATE TABLE hu_t (id INT)");
        answer("SELECT * FROM hu_t FOR UPDATE");
        assertEquals("SELECT_FOR_UPDATE|FAILED_WITH_ERROR",
            listed("SELECT * FROM hu_t FOR UPDATE", "QUERY_TYPE, EXECUTION_STATUS"));
        assertAnswer("0", "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION(RESULT_LIMIT => "
            + "1000)) WHERE QUERY_TEXT ILIKE 'EXPLAIN%HU_T%'");
    }

    private static final String ALIKE = "false|2|1|0|0|0|0|0|0|0|0|0|0|0|false|false|false|false|false|false";

    @Test
    public void everyStatementCarriesTheCellsTheAccountAnswersAlike() {
        engine.execute("CREATE TABLE hc_t (a INT)");
        assertListed("IS_CLIENT_GENERATED_STATEMENT, QUERY_HASH_VERSION, QUERY_PARAMETERIZED_HASH_VERSION, "
            + "OUTBOUND_DATA_TRANSFER_BYTES, INBOUND_DATA_TRANSFER_BYTES, EXTERNAL_FUNCTION_TOTAL_INVOCATIONS, "
            + "EXTERNAL_FUNCTION_TOTAL_SENT_ROWS, EXTERNAL_FUNCTION_TOTAL_RECEIVED_ROWS, "
            + "EXTERNAL_FUNCTION_TOTAL_SENT_BYTES, EXTERNAL_FUNCTION_TOTAL_RECEIVED_BYTES, "
            + "QUERY_ACCELERATION_BYTES_SCANNED, QUERY_RETRY_TIME, FAULT_HANDLING_TIME, LIST_EXTERNAL_FILE_TIME, "
            + "BYTES_WRITTEN_TO_RESULT IS NULL, CREDITS_USED_CLOUD_SERVICES IS NULL, RELEASE_VERSION IS NULL, "
            + "QUERY_HASH IS NULL, QUERY_PARAMETERIZED_HASH IS NULL, AUTHN_EVENT_ID IS NULL", new String[][] {
                {"SET hc_v = 1", ALIKE},
                {"SELECT 1 FROM hc_no_such_table", ALIKE},
                {"INSERT INTO hc_t VALUES (1)", ALIKE},
            });
    }

    private static final String RUN = "TRANSACTION_ID = 0, CLUSTER_NUMBER, WAREHOUSE_SIZE IS NULL, "
        + "ROWS_WRITTEN_TO_RESULT, ROWS_INSERTED, ROWS_PRODUCED";

    @Test
    public void aWriteRunsInATransactionOnAWarehouse() {
        engine.execute("CREATE PROCEDURE hw_p() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        assertListed(RUN, new String[][] {
            {"CREATE TABLE hw_t (a INT)", "true|null|true|1|0|0"},
            {"INSERT INTO hw_t VALUES (1), (2), (3)", "false|1|false|1|3|3"},
            {"UPDATE hw_t SET a = 10 WHERE a = 1", "false|1|false|1|0|3"},
            {"DELETE FROM hw_t WHERE a = 2", "false|1|false|1|0|2"},
            {"MERGE INTO hw_t USING (SELECT 3 AS x UNION ALL SELECT 4) s ON hw_t.a = s.x WHEN MATCHED THEN UPDATE "
                + "SET a = 30 WHEN NOT MATCHED THEN INSERT (a) VALUES (s.x)", "false|1|false|1|1|3"},
            {"INSERT INTO hw_t SELECT a FROM hw_t", "false|1|false|1|3|6"},
            {"SELECT SEQ4() FROM TABLE(GENERATOR(ROWCOUNT => 4))", "true|1|false|4|0|4"},
            {"SELECT * FROM hw_t ORDER BY a LIMIT 2", "true|1|false|2|0|2"},
            {"UPDATE hw_t SET a = a WHERE a > 100", "false|1|false|1|0|0"},
            {"DELETE FROM hw_t WHERE a > 100", "false|1|false|1|0|0"},
            {"INSERT INTO hw_t SELECT 1, 2", "true|null|true|0|0|0"},
            {"SELECT 2 / 0", "true|1|false|0|0|0"},
            {"INSERT INTO hw_t SELECT 1 / 0", "false|1|false|0|0|0"},
            {"CREATE TABLE hw_c AS SELECT a FROM hw_t", "false|1|false|1|6|6"},
            {"CREATE TABLE hw_k CLONE hw_t", "false|null|true|1|0|0"},
            {"TRUNCATE TABLE hw_c", "false|null|true|1|0|0"},
            {"INSERT ALL INTO hw_t INTO hw_t SELECT 5", "false|1|false|1|2|8"},
            {"CALL hw_p()", "true|1|false|1|0|1"},
            {"BEGIN RETURN 1; END", "true|1|false|1|0|1"},
            {"EXECUTE IMMEDIATE 'INSERT INTO hw_t VALUES (7)'", "false|1|false|1|1|9"},
            {"SET hw_v = 1", "true|null|true|1|0|0"},
            {"SHOW TABLES LIKE 'HW_T'", "true|null|true|1|0|0"},
            {"SELECT 'constant'", "true|null|true|1|0|0"},
        });
    }

    @Test
    public void theStatementsOfAnExplicitTransactionShareIt() {
        engine.execute("CREATE TABLE ht_t (a INT)");
        engine.execute("INSERT INTO ht_t VALUES (1), (2)");
        assertListed(RUN, new String[][] {
            {"begin transaction", "false|null|true|1|0|0"},
            {"select 'in a transaction'", "false|null|true|1|0|0"},
            {"insert into ht_t values (99)", "false|1|false|1|1|3"},
            {"commit", "false|null|true|1|0|0"},
        });
        final String latest = "(SELECT TRANSACTION_ID FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION("
            + "RESULT_LIMIT => 1000)) WHERE QUERY_TEXT = '%s' ORDER BY START_TIME DESC LIMIT 1)";
        assertAnswer("1", "SELECT COUNT(DISTINCT t) FROM (SELECT " + String.format(latest, "begin transaction")
            + " AS t UNION ALL SELECT " + String.format(latest, "select ''in a transaction''")
            + " UNION ALL SELECT " + String.format(latest, "insert into ht_t values (99)")
            + " UNION ALL SELECT " + String.format(latest, "commit") + ")");
        assertAnswer("true", "SELECT " + String.format(latest, "commit") + "::VARCHAR = LAST_TRANSACTION()");
        answer("TRUNCATE TABLE ht_t");
        assertAnswer("true",
            "SELECT " + String.format(latest, "TRUNCATE TABLE ht_t") + "::VARCHAR = LAST_TRANSACTION()");
    }
}
