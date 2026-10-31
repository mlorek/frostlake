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

package dev.frostlake.features;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY lists a row for each refresh of a dynamic table, the latest
 * first: what started it (CREATION, MANUAL), what it did (INCREMENTAL, FULL, NO_DATA), the rows and micro-partitions
 * it changed, the dynamic table it depended on and the inputs whose data changed for it, each with its partitions added
 * and removed since the refresh before; a creation refreshes the dynamic tables it reads first, and
 * a refresh records one for each table of the chain it refreshes. A refresh stays listed under the name its table
 * held when it ran, past the table's replacement, DROP, UNDROP and RENAME, and the rows come by table, the last
 * name first, a RESULT_LIMIT keeping the first of them; so a read that must see the whole run asks for every row
 * and keeps the refreshes since the setup's timestamp, a limit's count is read without that bound, and a default
 * limit is compared with the limit it stands for: an earlier run's tables of these names answer too (all
 * live-verified).
 */
public class DynamicTableRefreshHistoryTest extends MeasuredCellsTestSupport {

    @Test
    public void aChainsRefreshes() {
        engine.execute("SET t747 = CURRENT_TIMESTAMP()");
        engine.execute("CREATE OR REPLACE TABLE ds747 (a INT)");
        engine.execute("INSERT INTO ds747 VALUES (1)");
        final Object wh = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        final String warehouse = wh == null ? "COMPUTE_WH" : String.valueOf(wh);
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE d747a TARGET_LAG = \'1 hour\' WAREHOUSE = " + warehouse
            + " AS SELECT a FROM ds747");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE d747b TARGET_LAG = \'1 hour\' WAREHOUSE = " + warehouse
            + " AS SELECT a FROM d747a");
        answer("SELECT NAME, SCHEMA_NAME, DATABASE_NAME, STATE, STATE_CODE, STATE_MESSAGE, REFRESH_TRIGGER, "
            + "REFRESH_ACTION, TARGET_LAG_SEC, QUALIFIED_NAME, STATISTICS, LAST_COMPLETED_DEPENDENCY, "
            + "INPUTS_WITH_CHANGED_DATA, REINIT_REASON, SECONDARY_ROLE_NAMES FROM "
            + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => 'TEST_DB.TEST_SCHEMA.D747', "
            + "RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747 ORDER BY REFRESH_START_TIME, NAME");
        // Each input whose data changed, with its micro-partitions since the refresh before: a table is one
        // partition while it holds rows, which the INSERT rewrites, and a refresh adds one for what it writes.
        assertAnswer("D747A|CREATION|INCREMENTAL|[{\"kind\":\"TABLE\",\"name\":\"TEST_DB.TEST_SCHEMA.DS747\","
                + "\"statistics\":{\"numAddedPartitions\":1,\"numRegisteredRows\":1,"
                + "\"numRemovedPartitions\":0,\"numUnregisteredRows\":0}}] ;; D747A|CREATION|NO_DATA|null ;; "
                + "D747B|CREATION|INCREMENTAL|[{\"kind\":\"DYNAMIC_TABLE\","
                + "\"name\":\"TEST_DB.TEST_SCHEMA.D747A\",\"statistics\":{\"numAddedPartitions\":1,"
                + "\"numRegisteredRows\":1,\"numRemovedPartitions\":0,\"numUnregisteredRows\":0}}]",
            "SELECT NAME, REFRESH_TRIGGER, REFRESH_ACTION, INPUTS_WITH_CHANGED_DATA FROM "
                + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => 'TEST_DB.TEST_SCHEMA.D747', "
                + "RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747 ORDER BY REFRESH_START_TIME, NAME");
        assertAnswer("D747A|true|true|true|true|false|true|null|true ;; D747A|true|true|true|true|false|null|null|"
                + "true ;; D747B|true|true|true|true|false|true|null|true",
            "SELECT NAME, QUERY_ID IS NOT NULL, DATA_TIMESTAMP IS NOT NULL, GRAPH_HISTORY_VALID_FROM IS NOT NULL, "
                + "REFRESH_START_TIME <= REFRESH_END_TIME, COMPLETION_TARGET IS NULL, WAREHOUSE = COALESCE((SELECT "
                + "CURRENT_WAREHOUSE()), 'COMPUTE_WH'), EXECUTE_AS_USER = CURRENT_USER(), DATA_TIMESTAMP <= "
                + "REFRESH_START_TIME FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => "
                + "'TEST_DB.TEST_SCHEMA.D747', RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747 ORDER BY "
                + "REFRESH_START_TIME, NAME");
        assertAnswer("1", "INSERT INTO ds747 VALUES (2)");
        assertAnswer("2", "ALTER DYNAMIC TABLE d747b REFRESH ->> SELECT \"refreshed_dt_count\" FROM $1");
        assertAnswer("No new data", "ALTER DYNAMIC TABLE d747a REFRESH ->> SELECT \"statistics\" FROM $1");
        answer("SELECT NAME, SCHEMA_NAME, DATABASE_NAME, STATE, STATE_CODE, STATE_MESSAGE, REFRESH_TRIGGER, "
            + "REFRESH_ACTION, TARGET_LAG_SEC, QUALIFIED_NAME, STATISTICS, LAST_COMPLETED_DEPENDENCY, "
            + "INPUTS_WITH_CHANGED_DATA, REINIT_REASON, SECONDARY_ROLE_NAMES FROM "
            + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => 'TEST_DB.TEST_SCHEMA.D747', "
            + "RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747 ORDER BY REFRESH_START_TIME, NAME");
        assertAnswer("D747A|CREATION|INCREMENTAL|[{\"kind\":\"TABLE\",\"name\":\"TEST_DB.TEST_SCHEMA.DS747\","
                + "\"statistics\":{\"numAddedPartitions\":1,\"numRegisteredRows\":1,"
                + "\"numRemovedPartitions\":0,\"numUnregisteredRows\":0}}] ;; D747A|CREATION|NO_DATA|null ;; "
                + "D747B|CREATION|INCREMENTAL|[{\"kind\":\"DYNAMIC_TABLE\","
                + "\"name\":\"TEST_DB.TEST_SCHEMA.D747A\",\"statistics\":{\"numAddedPartitions\":1,"
                + "\"numRegisteredRows\":1,\"numRemovedPartitions\":0,\"numUnregisteredRows\":0}}] ;; "
                + "D747A|MANUAL|INCREMENTAL|[{\"kind\":\"TABLE\",\"name\":\"TEST_DB.TEST_SCHEMA.DS747\","
                + "\"statistics\":{\"numAddedPartitions\":1,\"numRegisteredRows\":2,"
                + "\"numRemovedPartitions\":1,\"numUnregisteredRows\":1}}] ;; "
                + "D747B|MANUAL|INCREMENTAL|[{\"kind\":\"DYNAMIC_TABLE\","
                + "\"name\":\"TEST_DB.TEST_SCHEMA.D747A\",\"statistics\":{\"numAddedPartitions\":1,"
                + "\"numRegisteredRows\":1,\"numRemovedPartitions\":0,\"numUnregisteredRows\":0}}] ;; "
                + "D747A|MANUAL|NO_DATA|null",
            "SELECT NAME, REFRESH_TRIGGER, REFRESH_ACTION, INPUTS_WITH_CHANGED_DATA FROM "
                + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => 'TEST_DB.TEST_SCHEMA.D747', "
                + "RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747 ORDER BY REFRESH_START_TIME, NAME");
        assertAnswer("D747A|true|true|true|true|false|true|null|true ;; D747A|true|true|true|true|false|null|null|"
                + "true ;; D747B|true|true|true|true|false|true|null|true ;; D747A|true|true|true|true|false|true|"
                + "null|true ;; D747B|true|true|true|true|false|true|null|true ;; D747A|true|true|true|true|false|"
                + "null|null|true",
            "SELECT NAME, QUERY_ID IS NOT NULL, DATA_TIMESTAMP IS NOT NULL, GRAPH_HISTORY_VALID_FROM IS NOT NULL, "
                + "REFRESH_START_TIME <= REFRESH_END_TIME, COMPLETION_TARGET IS NULL, WAREHOUSE = COALESCE((SELECT "
                + "CURRENT_WAREHOUSE()), 'COMPUTE_WH'), EXECUTE_AS_USER = CURRENT_USER(), DATA_TIMESTAMP <= "
                + "REFRESH_START_TIME FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => "
                + "'TEST_DB.TEST_SCHEMA.D747', RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747 ORDER BY "
                + "REFRESH_START_TIME, NAME");
        assertAnswer("D747A|CREATION|INCREMENTAL|1 ;; D747A|CREATION|NO_DATA|1 ;; D747A|MANUAL|INCREMENTAL|1 ;; D747A|"
                + "MANUAL|NO_DATA|1 ;; D747B|CREATION|INCREMENTAL|1 ;; D747B|MANUAL|INCREMENTAL|1",
            "SELECT NAME, REFRESH_TRIGGER, REFRESH_ACTION, COUNT(*) FROM "
                + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => 'TEST_DB.TEST_SCHEMA.D747', "
                + "RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747 GROUP BY 1, 2, 3 ORDER BY 1, 2, 3");
        assertAnswer("4",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => 'D747A', "
                + "RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747");
        assertAnswer("4",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => 'd747a', "
                + "RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747");
        assertAnswer("4",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => "
                + "'TEST_DB.TEST_SCHEMA.D747A', RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747");
        assertAnswer("4",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => "
                + "'test_db.test_schema.d747a', RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747");
        assertAnswer("4",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => 'TEST_SCHEMA.D747A',"
                + " RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747");
        assertAnswer("0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => 'NOSUCH747', "
                + "RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747");
        assertAnswer("2",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => "
                + "'TEST_DB.TEST_SCHEMA.D747B', RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747");
        assertAnswer("6",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => "
                + "'test_db.test_schema.d747', RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747");
        assertAnswer("1",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => "
                + "'TEST_DB.TEST_SCHEMA.D747', RESULT_LIMIT => 1))");
        assertAnswer("0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => "
                + "'TEST_DB.TEST_SCHEMA.D747', ERROR_ONLY => TRUE, RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME "
                + ">= $t747");
        assertAnswer("true",
            "SELECT (SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => 'D747A', "
                + "RESULT_LIMIT => 0))) = (SELECT COUNT(*) FROM "
                + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => 'D747A', RESULT_LIMIT => 100)))");
        assertAnswer("REFUSED SQL compilation error: | Value for parameter RESULT_LIMIT exceeds maximum allowable "
                + "value (10,000).",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => 'D747A', "
                + "RESULT_LIMIT => 10001)) WHERE REFRESH_START_TIME >= $t747");
        assertAnswer("4",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => 'D747A', "
                + "NAME_PREFIX => 'TEST_DB', RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747");
        assertAnswer("D747A|NO_DATA",
            "SELECT NAME, REFRESH_ACTION FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => 'D747A',"
                + " RESULT_LIMIT => 1)) WHERE REFRESH_START_TIME >= $t747");
        answer("CREATE OR REPLACE DYNAMIC TABLE d747f TARGET_LAG = \'1 hour\' WAREHOUSE = " + warehouse
            + " REFRESH_MODE = FULL AS SELECT a FROM ds747");
        assertAnswer("1", "INSERT INTO ds747 VALUES (3)");
        assertAnswer("{\"insertedRows\":3,\"copiedRows\":0,\"deletedRows\":2}",
            "ALTER DYNAMIC TABLE d747f REFRESH ->> SELECT \"statistics\" FROM $1");
        assertAnswer("No new data", "ALTER DYNAMIC TABLE d747f REFRESH ->> SELECT \"statistics\" FROM $1");
        answer("SELECT REFRESH_TRIGGER, REFRESH_ACTION, STATISTICS FROM "
            + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => 'D747F', RESULT_LIMIT => 10000)) "
            + "WHERE REFRESH_START_TIME >= $t747 ORDER BY REFRESH_START_TIME");
        assertAnswer("{\"insertedRows\":1,\"copiedRows\":0,\"deletedRows\":0}",
            "ALTER DYNAMIC TABLE d747a REFRESH ->> SELECT \"statistics\" FROM $1");
        assertAnswer("D747F|MANUAL|NO_DATA ;; D747F|MANUAL|FULL",
            "SELECT NAME, REFRESH_TRIGGER, REFRESH_ACTION FROM "
                + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => 'TEST_DB.TEST_SCHEMA.D747', "
                + "RESULT_LIMIT => 2)) WHERE REFRESH_START_TIME >= $t747 ORDER BY REFRESH_START_TIME DESC");
    }

    @Test
    public void refreshesByTheirDeterministicColumns() {
        engine.execute("SET t747 = CURRENT_TIMESTAMP()");
        engine.execute("CREATE OR REPLACE TABLE es747 (a INT)");
        engine.execute("INSERT INTO es747 VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE ee747 (a INT)");
        final Object wh = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        final String warehouse = wh == null ? "COMPUTE_WH" : String.valueOf(wh);
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE e747a TARGET_LAG = \'1 hour\' WAREHOUSE = " + warehouse
            + " AS SELECT a FROM es747");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE e747b TARGET_LAG = \'1 hour\' WAREHOUSE = " + warehouse
            + " AS SELECT a FROM e747a");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE e747c TARGET_LAG = DOWNSTREAM WAREHOUSE = " + warehouse
            + " AS SELECT a FROM e747a");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE e747e TARGET_LAG = \'1 hour\' WAREHOUSE = " + warehouse
            + " AS SELECT a FROM ee747");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE e747f TARGET_LAG = \'1 hour\' WAREHOUSE = " + warehouse
            + " REFRESH_MODE = FULL AS SELECT a FROM es747");
        assertAnswer("E747A|CREATION|INCREMENTAL|SUCCEEDED|SUCCESS|null|3600|1|0|0|1|0|0|null|1|TABLE|"
                + "TEST_DB.TEST_SCHEMA.ES747|false|false|false ;; E747A|CREATION|NO_DATA|SUCCEEDED|SUCCESS|null|3600|"
                + "0|0|0|0|0|0|null|null|null|null|true|false|false ;; E747B|CREATION|INCREMENTAL|SUCCEEDED|SUCCESS|"
                + "null|3600|1|0|0|1|0|0|TEST_DB.TEST_SCHEMA.E747A|1|DYNAMIC_TABLE|TEST_DB.TEST_SCHEMA.E747A|false|"
                + "false|false ;; E747A|CREATION|NO_DATA|SUCCEEDED|SUCCESS|null|3600|0|0|0|0|0|0|null|null|null|null|"
                + "true|false|false ;; E747C|CREATION|INCREMENTAL|SUCCEEDED|SUCCESS|null|null|1|0|0|1|0|0|"
                + "TEST_DB.TEST_SCHEMA.E747A|1|DYNAMIC_TABLE|TEST_DB.TEST_SCHEMA.E747A|false|true|false ;; E747E|"
                + "CREATION|INCREMENTAL|SUCCEEDED|SUCCESS|null|3600|0|0|0|0|0|0|null|0|null|null|false|false|false ;; "
                + "E747F|CREATION|FULL|SUCCEEDED|SUCCESS|null|3600|1|0|0|1|0|0|null|1|TABLE|TEST_DB.TEST_SCHEMA.ES747|"
                + "false|false|false",
            "SELECT NAME, REFRESH_TRIGGER, REFRESH_ACTION, STATE, STATE_CODE, STATE_MESSAGE, TARGET_LAG_SEC, "
                + "STATISTICS:numInsertedRows, STATISTICS:numDeletedRows, STATISTICS:numCopiedRows, "
                + "STATISTICS:numAddedPartitions, STATISTICS:numRemovedPartitions, STATISTICS:queuedTimeMs, "
                + "LAST_COMPLETED_DEPENDENCY:qualified_name, ARRAY_SIZE(INPUTS_WITH_CHANGED_DATA), "
                + "INPUTS_WITH_CHANGED_DATA[0]:kind, INPUTS_WITH_CHANGED_DATA[0]:name, WAREHOUSE IS NULL, "
                + "COMPLETION_TARGET IS NULL, QUERY_ID IS NULL FROM "
                + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => 'TEST_DB.TEST_SCHEMA.E747', "
                + "RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747 ORDER BY REFRESH_START_TIME, NAME");
        assertAnswer("1", "INSERT INTO es747 VALUES (2)");
        assertAnswer("2", "ALTER DYNAMIC TABLE e747b REFRESH ->> SELECT \"refreshed_dt_count\" FROM $1");
        assertAnswer("2", "ALTER DYNAMIC TABLE e747c REFRESH ->> SELECT \"refreshed_dt_count\" FROM $1");
        assertAnswer("No new data", "ALTER DYNAMIC TABLE e747e REFRESH ->> SELECT \"statistics\" FROM $1");
        assertAnswer("1", "DELETE FROM es747 WHERE a = 1");
        assertAnswer("{\"insertedRows\":1,\"copiedRows\":0,\"deletedRows\":1}",
            "ALTER DYNAMIC TABLE e747f REFRESH ->> SELECT \"statistics\" FROM $1");
        // The refresh deletes the one row of the partition the creation wrote, so it copies none and adds no
        // partition (live-verified).
        assertAnswer("{\"insertedRows\":0,\"copiedRows\":0,\"deletedRows\":1}",
            "ALTER DYNAMIC TABLE e747a REFRESH ->> SELECT \"statistics\" FROM $1");
        assertAnswer("E747A|CREATION|INCREMENTAL|SUCCEEDED|SUCCESS|null|3600|1|0|0|1|0|0|null|1|TABLE|"
                + "TEST_DB.TEST_SCHEMA.ES747|false|false|false ;; E747A|CREATION|NO_DATA|SUCCEEDED|SUCCESS|null|"
                + "3600|0|0|0|0|0|0|null|null|null|null|true|false|false ;; E747B|CREATION|INCREMENTAL|SUCCEEDED|"
                + "SUCCESS|null|3600|1|0|0|1|0|0|TEST_DB.TEST_SCHEMA.E747A|1|DYNAMIC_TABLE|"
                + "TEST_DB.TEST_SCHEMA.E747A|false|false|false ;; E747A|CREATION|NO_DATA|SUCCEEDED|SUCCESS|null|"
                + "3600|0|0|0|0|0|0|null|null|null|null|true|false|false ;; E747C|CREATION|INCREMENTAL|SUCCEEDED|"
                + "SUCCESS|null|null|1|0|0|1|0|0|TEST_DB.TEST_SCHEMA.E747A|1|DYNAMIC_TABLE|"
                + "TEST_DB.TEST_SCHEMA.E747A|false|true|false ;; E747E|CREATION|INCREMENTAL|SUCCEEDED|SUCCESS|null|"
                + "3600|0|0|0|0|0|0|null|0|null|null|false|false|false ;; E747F|CREATION|FULL|SUCCEEDED|SUCCESS|"
                + "null|3600|1|0|0|1|0|0|null|1|TABLE|TEST_DB.TEST_SCHEMA.ES747|false|false|false ;; E747A|MANUAL|"
                + "INCREMENTAL|SUCCEEDED|SUCCESS|null|3600|1|0|0|1|0|0|null|1|TABLE|TEST_DB.TEST_SCHEMA.ES747|"
                + "false|false|false ;; E747B|MANUAL|INCREMENTAL|SUCCEEDED|SUCCESS|null|3600|1|0|0|1|0|0|"
                + "TEST_DB.TEST_SCHEMA.E747A|1|DYNAMIC_TABLE|TEST_DB.TEST_SCHEMA.E747A|false|false|false ;; E747A|"
                + "MANUAL|NO_DATA|SUCCEEDED|SUCCESS|null|3600|0|0|0|0|0|0|null|null|null|null|true|false|false ;; "
                + "E747C|MANUAL|INCREMENTAL|SUCCEEDED|SUCCESS|null|null|1|0|0|1|0|0|TEST_DB.TEST_SCHEMA.E747A|1|"
                + "DYNAMIC_TABLE|TEST_DB.TEST_SCHEMA.E747A|false|true|false ;; E747E|MANUAL|NO_DATA|SUCCEEDED|"
                + "SUCCESS|null|3600|0|0|0|0|0|0|null|null|null|null|true|false|false ;; E747F|MANUAL|FULL|"
                + "SUCCEEDED|SUCCESS|null|3600|1|1|0|1|1|0|null|1|TABLE|TEST_DB.TEST_SCHEMA.ES747|false|false|"
                + "false ;; E747A|MANUAL|INCREMENTAL|SUCCEEDED|SUCCESS|null|3600|0|1|0|0|1|0|null|1|TABLE|"
                + "TEST_DB.TEST_SCHEMA.ES747|false|false|false",
            "SELECT NAME, REFRESH_TRIGGER, REFRESH_ACTION, STATE, STATE_CODE, STATE_MESSAGE, TARGET_LAG_SEC, "
                + "STATISTICS:numInsertedRows, STATISTICS:numDeletedRows, STATISTICS:numCopiedRows, "
                + "STATISTICS:numAddedPartitions, STATISTICS:numRemovedPartitions, STATISTICS:queuedTimeMs, "
                + "LAST_COMPLETED_DEPENDENCY:qualified_name, ARRAY_SIZE(INPUTS_WITH_CHANGED_DATA), "
                + "INPUTS_WITH_CHANGED_DATA[0]:kind, INPUTS_WITH_CHANGED_DATA[0]:name, WAREHOUSE IS NULL, "
                + "COMPLETION_TARGET IS NULL, QUERY_ID IS NULL FROM "
                + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => 'TEST_DB.TEST_SCHEMA.E747', "
                + "RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747 ORDER BY REFRESH_START_TIME, NAME");
        assertAnswer("6",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => 'E747A', "
                + "RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747");
        assertAnswer("E747A|INCREMENTAL",
            "SELECT NAME, REFRESH_ACTION FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => 'E747A',"
                + " RESULT_LIMIT => 1)) WHERE REFRESH_START_TIME >= $t747");
        assertAnswer("E747C|CREATION ;; E747C|MANUAL",
            "SELECT NAME, REFRESH_TRIGGER FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => "
                + "'e747c', RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747 ORDER BY REFRESH_START_TIME");
        assertAnswer("0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => '\"e747a\"', "
                + "RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747");
        assertAnswer("0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => "
                + "'nosuch_db.test_schema.e747a', RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747");
        assertAnswer("14",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => "
                + "'TEST_DB.TEST_SCHEMA.E747', ERROR_ONLY => FALSE, RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME "
                + ">= $t747");
        assertAnswer("3",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => "
                + "'TEST_DB.TEST_SCHEMA.E747', RESULT_LIMIT => 3))");
        assertAnswer("true",
            "SELECT (SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => "
                + "'TEST_DB.TEST_SCHEMA.E747', RESULT_LIMIT => -1))) = (SELECT COUNT(*) FROM "
                + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => 'TEST_DB.TEST_SCHEMA.E747', "
                + "RESULT_LIMIT => 100)))");
        assertAnswer("REFUSED SQL compilation error: | Invalid identifier DYNAMIC_TABLE_REFRESH_HISTORY",
            "SELECT * FROM TABLE(DYNAMIC_TABLE_REFRESH_HISTORY())");
    }

    @Test
    public void outlivesItsTable() {
        engine.execute("SET t747 = CURRENT_TIMESTAMP()");
        engine.execute("CREATE OR REPLACE TABLE hs747 (a INT)");
        engine.execute("INSERT INTO hs747 VALUES (1)");
        final Object wh = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        final String warehouse = wh == null ? "COMPUTE_WH" : String.valueOf(wh);
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE h747a TARGET_LAG = \'1 hour\' WAREHOUSE = " + warehouse
            + " AS SELECT a FROM hs747");
        assertAnswer("H747A|CREATION|INCREMENTAL",
            "SELECT NAME, REFRESH_TRIGGER, REFRESH_ACTION FROM "
                + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => 'H747A', RESULT_LIMIT => 10000)) "
                + "WHERE REFRESH_START_TIME >= $t747 ORDER BY REFRESH_START_TIME");
        assertAnswer("No new data", "ALTER DYNAMIC TABLE h747a REFRESH ->> SELECT \"statistics\" FROM $1");
        answer("CREATE OR REPLACE DYNAMIC TABLE h747a TARGET_LAG = \'1 hour\' WAREHOUSE = " + warehouse
            + " AS SELECT a + 1 AS a FROM hs747");
        assertAnswer("2", "SELECT * FROM h747a");
        assertAnswer("H747A|CREATION|INCREMENTAL ;; H747A|MANUAL|NO_DATA ;; H747A|CREATION|INCREMENTAL",
            "SELECT NAME, REFRESH_TRIGGER, REFRESH_ACTION FROM "
                + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => 'H747A', RESULT_LIMIT => 10000)) "
                + "WHERE REFRESH_START_TIME >= $t747 ORDER BY REFRESH_START_TIME");
        assertAnswer("H747A|TEST_DB.TEST_SCHEMA.H747A|CREATION|INCREMENTAL ;; H747A|TEST_DB.TEST_SCHEMA.H747A|MANUAL|"
                + "NO_DATA ;; H747A|TEST_DB.TEST_SCHEMA.H747A|CREATION|INCREMENTAL",
            "SELECT NAME, QUALIFIED_NAME, REFRESH_TRIGGER, REFRESH_ACTION FROM "
                + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => 'TEST_DB.TEST_SCHEMA.H747', "
                + "RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747 ORDER BY REFRESH_START_TIME, NAME");
        assertAnswer("H747A successfully dropped.", "DROP DYNAMIC TABLE h747a");
        assertAnswer("H747A|CREATION|INCREMENTAL ;; H747A|MANUAL|NO_DATA ;; H747A|CREATION|INCREMENTAL",
            "SELECT NAME, REFRESH_TRIGGER, REFRESH_ACTION FROM "
                + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => 'H747A', RESULT_LIMIT => 10000)) "
                + "WHERE REFRESH_START_TIME >= $t747 ORDER BY REFRESH_START_TIME");
        assertAnswer("H747A|TEST_DB.TEST_SCHEMA.H747A|CREATION|INCREMENTAL ;; H747A|TEST_DB.TEST_SCHEMA.H747A|MANUAL|"
                + "NO_DATA ;; H747A|TEST_DB.TEST_SCHEMA.H747A|CREATION|INCREMENTAL",
            "SELECT NAME, QUALIFIED_NAME, REFRESH_TRIGGER, REFRESH_ACTION FROM "
                + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => 'TEST_DB.TEST_SCHEMA.H747', "
                + "RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747 ORDER BY REFRESH_START_TIME, NAME");
        assertAnswer("3",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => 'H747A', "
                + "RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747");
        assertAnswer("Dynamic_table H747A successfully restored.", "UNDROP DYNAMIC TABLE h747a");
        assertAnswer("2", "SELECT * FROM h747a");
        assertAnswer("H747A|CREATION|INCREMENTAL ;; H747A|MANUAL|NO_DATA ;; H747A|CREATION|INCREMENTAL",
            "SELECT NAME, REFRESH_TRIGGER, REFRESH_ACTION FROM "
                + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => 'H747A', RESULT_LIMIT => 10000)) "
                + "WHERE REFRESH_START_TIME >= $t747 ORDER BY REFRESH_START_TIME");
        assertAnswer("Statement executed successfully.", "ALTER DYNAMIC TABLE h747a RENAME TO h747b");
        assertAnswer("H747A|CREATION|INCREMENTAL ;; H747A|MANUAL|NO_DATA ;; H747A|CREATION|INCREMENTAL",
            "SELECT NAME, REFRESH_TRIGGER, REFRESH_ACTION FROM "
                + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => 'H747A', RESULT_LIMIT => 10000)) "
                + "WHERE REFRESH_START_TIME >= $t747 ORDER BY REFRESH_START_TIME");
        assertAnswer("",
            "SELECT NAME, REFRESH_TRIGGER, REFRESH_ACTION FROM "
                + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => 'H747B', RESULT_LIMIT => 10000)) "
                + "WHERE REFRESH_START_TIME >= $t747 ORDER BY REFRESH_START_TIME");
        assertAnswer("H747A|TEST_DB.TEST_SCHEMA.H747A|CREATION|INCREMENTAL ;; H747A|TEST_DB.TEST_SCHEMA.H747A|MANUAL|"
                + "NO_DATA ;; H747A|TEST_DB.TEST_SCHEMA.H747A|CREATION|INCREMENTAL",
            "SELECT NAME, QUALIFIED_NAME, REFRESH_TRIGGER, REFRESH_ACTION FROM "
                + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => 'TEST_DB.TEST_SCHEMA.H747', "
                + "RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747 ORDER BY REFRESH_START_TIME, NAME");
        assertAnswer("1", "INSERT INTO hs747 VALUES (2)");
        assertAnswer("{\"insertedRows\":1,\"copiedRows\":0,\"deletedRows\":0}",
            "ALTER DYNAMIC TABLE h747b REFRESH ->> SELECT \"statistics\" FROM $1");
        assertAnswer("H747A|TEST_DB.TEST_SCHEMA.H747A|CREATION|INCREMENTAL ;; H747A|TEST_DB.TEST_SCHEMA.H747A|MANUAL|"
                + "NO_DATA ;; H747A|TEST_DB.TEST_SCHEMA.H747A|CREATION|INCREMENTAL ;; H747B|TEST_DB.TEST_SCHEMA.H747B|"
                + "MANUAL|INCREMENTAL",
            "SELECT NAME, QUALIFIED_NAME, REFRESH_TRIGGER, REFRESH_ACTION FROM "
                + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => 'TEST_DB.TEST_SCHEMA.H747', "
                + "RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747 ORDER BY REFRESH_START_TIME, NAME");
        assertAnswer("H747B successfully dropped.", "DROP DYNAMIC TABLE h747b");
        assertAnswer("Table H747B successfully created.", "CREATE OR REPLACE TABLE h747b (a INT)");
        assertAnswer("H747B|MANUAL|INCREMENTAL",
            "SELECT NAME, REFRESH_TRIGGER, REFRESH_ACTION FROM "
                + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => 'H747B', RESULT_LIMIT => 10000)) "
                + "WHERE REFRESH_START_TIME >= $t747 ORDER BY REFRESH_START_TIME");
        assertAnswer("H747A|TEST_DB.TEST_SCHEMA.H747A|CREATION|INCREMENTAL ;; H747A|TEST_DB.TEST_SCHEMA.H747A|MANUAL|"
                + "NO_DATA ;; H747A|TEST_DB.TEST_SCHEMA.H747A|CREATION|INCREMENTAL ;; H747B|TEST_DB.TEST_SCHEMA.H747B|"
                + "MANUAL|INCREMENTAL",
            "SELECT NAME, QUALIFIED_NAME, REFRESH_TRIGGER, REFRESH_ACTION FROM "
                + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => 'TEST_DB.TEST_SCHEMA.H747', "
                + "RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747 ORDER BY REFRESH_START_TIME, NAME");
        assertAnswer("H747B successfully dropped.", "DROP TABLE h747b");
        assertAnswer("H747A|TEST_DB.TEST_SCHEMA.H747A|CREATION|INCREMENTAL ;; H747A|TEST_DB.TEST_SCHEMA.H747A|MANUAL|"
                + "NO_DATA ;; H747A|TEST_DB.TEST_SCHEMA.H747A|CREATION|INCREMENTAL ;; H747B|TEST_DB.TEST_SCHEMA.H747B|"
                + "MANUAL|INCREMENTAL",
            "SELECT NAME, QUALIFIED_NAME, REFRESH_TRIGGER, REFRESH_ACTION FROM "
                + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => 'TEST_DB.TEST_SCHEMA.H747', "
                + "RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t747 ORDER BY REFRESH_START_TIME, NAME");
    }
}
