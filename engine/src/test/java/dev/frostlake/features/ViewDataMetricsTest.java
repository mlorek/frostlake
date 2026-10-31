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
 * Data metric functions on a view: its DATA_METRIC_SCHEDULE is a property its ALTER sets and unsets, the metrics are
 * added, dropped and listed as a table's are, and DATA_METRIC_FUNCTION_REFERENCES answers their rows in the View
 * domain whichever table-like domain is asked, at the table's level; a cron schedule is listed as its expression and
 * time zone (all live-verified).
 */
public class ViewDataMetricsTest extends MeasuredCellsTestSupport {

    @Test
    public void metricsOfAView() {
        engine.execute("CREATE OR REPLACE TABLE dt (a INT, b VARCHAR)");
        engine.execute("CREATE OR REPLACE VIEW dv AS SELECT a, b FROM dt");
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER VIEW dv SET DATA_METRIC_SCHEDULE = 'USING CRON 0 0 1 1 * UTC'");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER VIEW dv ADD DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (a)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER VIEW dv ADD DATA METRIC FUNCTION SNOWFLAKE.CORE.ROW_COUNT ON ()");
            assertAnswer(
                "SNOWFLAKE|CORE|NULL_COUNT|TABLE(NUMBER)|NUMBER(38,0)|TEST_DB|TEST_SCHEMA|DV|View|0 0 1 1 *"
                + " UTC|STARTED|DISABLED|DISABLED|MEDIUM||Table| ;;"
                + " SNOWFLAKE|CORE|ROW_COUNT||NUMBER(38,0)|TEST_DB|TEST_SCHEMA|DV|View|0 0 1 1 *"
                + " UTC|STARTED|DISABLED|DISABLED|MEDIUM||Table|",
                "SELECT METRIC_DATABASE_NAME, METRIC_SCHEMA_NAME, METRIC_NAME, METRIC_SIGNATURE,"
                + " METRIC_DATA_TYPE, REF_ENTITY_DATABASE_NAME, REF_ENTITY_SCHEMA_NAME, REF_ENTITY_NAME,"
                + " REF_ENTITY_DOMAIN, SCHEDULE, SCHEDULE_STATUS, DATA_QUALITY_NOTIFICATION_STATUS,"
                + " ANOMALY_DETECTION_STATUS, ANOMALY_DETECTION_SENSITIVITY_LEVEL, USE_ROLE, LEVEL,"
                + " EXCLUDE_TABLE_TYPES FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NA"
                + "ME => 'dv', REF_ENTITY_DOMAIN => 'VIEW'))");
            assertAnswer(
                "SNOWFLAKE|CORE|NULL_COUNT|TABLE(NUMBER)|NUMBER(38,0)|TEST_DB|TEST_SCHEMA|DV|View|0 0 1 1 *"
                + " UTC|STARTED|DISABLED|DISABLED|MEDIUM||Table| ;;"
                + " SNOWFLAKE|CORE|ROW_COUNT||NUMBER(38,0)|TEST_DB|TEST_SCHEMA|DV|View|0 0 1 1 *"
                + " UTC|STARTED|DISABLED|DISABLED|MEDIUM||Table|",
                "SELECT METRIC_DATABASE_NAME, METRIC_SCHEMA_NAME, METRIC_NAME, METRIC_SIGNATURE,"
                + " METRIC_DATA_TYPE, REF_ENTITY_DATABASE_NAME, REF_ENTITY_SCHEMA_NAME, REF_ENTITY_NAME,"
                + " REF_ENTITY_DOMAIN, SCHEDULE, SCHEDULE_STATUS, DATA_QUALITY_NOTIFICATION_STATUS,"
                + " ANOMALY_DETECTION_STATUS, ANOMALY_DETECTION_SENSITIVITY_LEVEL, USE_ROLE, LEVEL,"
                + " EXCLUDE_TABLE_TYPES FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NA"
                + "ME => 'dv', REF_ENTITY_DOMAIN => 'TABLE'))");
            assertAnswer(
                "SNOWFLAKE|CORE|NULL_COUNT|TABLE(NUMBER)|NUMBER(38,0)|TEST_DB|TEST_SCHEMA|DV|View|0 0 1 1 *"
                + " UTC|STARTED|DISABLED|DISABLED|MEDIUM||Table| ;;"
                + " SNOWFLAKE|CORE|ROW_COUNT||NUMBER(38,0)|TEST_DB|TEST_SCHEMA|DV|View|0 0 1 1 *"
                + " UTC|STARTED|DISABLED|DISABLED|MEDIUM||Table|",
                "SELECT METRIC_DATABASE_NAME, METRIC_SCHEMA_NAME, METRIC_NAME, METRIC_SIGNATURE,"
                + " METRIC_DATA_TYPE, REF_ENTITY_DATABASE_NAME, REF_ENTITY_SCHEMA_NAME, REF_ENTITY_NAME,"
                + " REF_ENTITY_DOMAIN, SCHEDULE, SCHEDULE_STATUS, DATA_QUALITY_NOTIFICATION_STATUS,"
                + " ANOMALY_DETECTION_STATUS, ANOMALY_DETECTION_SENSITIVITY_LEVEL, USE_ROLE, LEVEL,"
                + " EXCLUDE_TABLE_TYPES FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NA"
                + "ME => 'test_db.test_schema.dv', REF_ENTITY_DOMAIN => 'view'))");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER VIEW dv DROP DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (a)");
            assertAnswer(
                "SNOWFLAKE|CORE|ROW_COUNT||NUMBER(38,0)|TEST_DB|TEST_SCHEMA|DV|View|0 0 1 1 *"
                + " UTC|STARTED|DISABLED|DISABLED|MEDIUM||Table|",
                "SELECT METRIC_DATABASE_NAME, METRIC_SCHEMA_NAME, METRIC_NAME, METRIC_SIGNATURE,"
                + " METRIC_DATA_TYPE, REF_ENTITY_DATABASE_NAME, REF_ENTITY_SCHEMA_NAME, REF_ENTITY_NAME,"
                + " REF_ENTITY_DOMAIN, SCHEDULE, SCHEDULE_STATUS, DATA_QUALITY_NOTIFICATION_STATUS,"
                + " ANOMALY_DETECTION_STATUS, ANOMALY_DETECTION_SENSITIVITY_LEVEL, USE_ROLE, LEVEL,"
                + " EXCLUDE_TABLE_TYPES FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NA"
                + "ME => 'dv', REF_ENTITY_DOMAIN => 'VIEW'))");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER VIEW dv DROP DATA METRIC FUNCTION SNOWFLAKE.CORE.ROW_COUNT ON ()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER VIEW dv UNSET DATA_METRIC_SCHEDULE");
            assertAnswer(
                "",
                "SELECT METRIC_DATABASE_NAME, METRIC_SCHEMA_NAME, METRIC_NAME, METRIC_SIGNATURE,"
                + " METRIC_DATA_TYPE, REF_ENTITY_DATABASE_NAME, REF_ENTITY_SCHEMA_NAME, REF_ENTITY_NAME,"
                + " REF_ENTITY_DOMAIN, SCHEDULE, SCHEDULE_STATUS, DATA_QUALITY_NOTIFICATION_STATUS,"
                + " ANOMALY_DETECTION_STATUS, ANOMALY_DETECTION_SENSITIVITY_LEVEL, USE_ROLE, LEVEL,"
                + " EXCLUDE_TABLE_TYPES FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NA"
                + "ME => 'dv', REF_ENTITY_DOMAIN => 'VIEW'))");
        } finally {
            engine.execute("DROP VIEW IF EXISTS dv");
            engine.execute("DROP TABLE IF EXISTS dt");
        }
    }
}
