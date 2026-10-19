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

package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A dynamic table's parameters (live-verified): SHOW PARAMETERS IN TABLE lists the nineteen a dynamic table has,
 * its own values at TABLE level and the rest inherited, and ALTER DYNAMIC TABLE ... SET gives it LOG_LEVEL,
 * LOG_EVENT_LEVEL, TRACE_LEVEL, DATA_METRIC_SCHEDULE, DEFAULT_DDL_COLLATION, ENABLE_DATA_COMPACTION,
 * QUOTED_IDENTIFIERS_IGNORE_CASE and MAX_DATA_EXTENSION_TIME_IN_DAYS, each judged as the account judges it. NULL
 * and UNSET take the table's own value away.
 */
public class DynamicTableParametersTest extends MeasuredCellsTestSupport {

    @Test
    public void showParametersAndSetOnADynamicTable() {
        engine.execute("CREATE OR REPLACE TABLE dsrc (a INT)");
        engine.execute("INSERT INTO dsrc VALUES (1)");
        final Object wh = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        final String warehouse = wh == null ? "COMPUTE_WH" : String.valueOf(wh);
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE dpt TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT a FROM dsrc");
        assertCells(new String[][] {
            {"SHOW PARAMETERS IN TABLE dpt ->> SELECT COUNT(*) FROM $1", "19"},
            {"SHOW PARAMETERS IN TABLE dpt ->> SELECT \"key\", \"value\", \"default\", \"level\" FROM $1 ORDER BY 1",
                "ALLOW_ROW_TIMESTAMP|false|false| ;; ALLOW_V3_EXPRESSION_DEFAULTS|false|false| ;; CATALOG||| ;; "
                + "CATALOG_SYNC||| ;; DATA_METRIC_SCHEDULE|60 MINUTES|60 MINUTES| ;; "
                + "DATA_RETENTION_TIME_IN_DAYS|1|1| ;; DEFAULT_DDL_COLLATION||| ;; ENABLE_DATA_COMPACTION|true|true| "
                + ";; EXTERNAL_VOLUME||| ;; ICEBERG_MERGE_ON_READ_BEHAVIOR|auto|auto| ;; LOG_EVENT_LEVEL|OFF|OFF| ;; "
                + "LOG_LEVEL|OFF|OFF| ;; MAX_DATA_EXTENSION_TIME_IN_DAYS|14|14| ;; "
                + "QUOTED_IDENTIFIERS_IGNORE_CASE|false|false| ;; REPLACE_INVALID_CHARACTERS|false|false| ;; "
                + "STORAGE_SERIALIZATION_POLICY|OPTIMIZED|OPTIMIZED| ;; TARGET_FILE_SIZE|AUTO|AUTO| ;; "
                + "TRACE_LEVEL|OFF|OFF| ;; VENDED_CREDENTIAL_STORAGE_REGION|||"},
            {"SHOW PARAMETERS IN TABLE dsrc ->> SELECT COUNT(*) FROM $1", "21"},
            {"ALTER DYNAMIC TABLE dpt SET LOG_LEVEL = WARN", "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'LOG_LEVEL' IN TABLE dpt ->> SELECT \"key\", \"value\", \"default\", \"level\" "
                + "FROM $1",
                "LOG_LEVEL|WARN|OFF|TABLE"},
            {"ALTER DYNAMIC TABLE dpt SET DATA_METRIC_SCHEDULE = '5 MINUTE'", "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'DATA_METRIC_SCHEDULE' IN TABLE dpt ->> SELECT \"key\", \"value\", \"default\", "
                + "\"level\" FROM $1",
                "DATA_METRIC_SCHEDULE|5 MINUTE|60 MINUTES|TABLE"},
            {"ALTER DYNAMIC TABLE dpt SET DATA_RETENTION_TIME_IN_DAYS = 3", "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'DATA_RETENTION_TIME_IN_DAYS' IN TABLE dpt ->> SELECT \"key\", \"value\", "
                + "\"default\", \"level\" FROM $1",
                "DATA_RETENTION_TIME_IN_DAYS|3|1|TABLE"},
            {"ALTER DYNAMIC TABLE dpt UNSET LOG_LEVEL", "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'LOG_LEVEL' IN TABLE dpt ->> SELECT \"key\", \"value\", \"default\", \"level\" "
                + "FROM $1",
                "LOG_LEVEL|OFF|OFF|"},
            {"ALTER DYNAMIC TABLE dpt SET TRACE_LEVEL = ALWAYS", "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'TRACE_LEVEL' IN TABLE dpt ->> SELECT \"key\", \"value\", \"default\", \"level\" "
                + "FROM $1",
                "TRACE_LEVEL|ALWAYS|OFF|TABLE"},
            {"ALTER DYNAMIC TABLE dpt SET LOG_EVENT_LEVEL = WARN", "Statement executed successfully."},
            {"ALTER DYNAMIC TABLE dpt SET MAX_DATA_EXTENSION_TIME_IN_DAYS = 5", "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'MAX_DATA_EXTENSION_TIME_IN_DAYS' IN TABLE dpt ->> SELECT \"key\", \"value\", "
                + "\"default\", \"level\" FROM $1",
                "MAX_DATA_EXTENSION_TIME_IN_DAYS|5|14|TABLE"},
            {"ALTER DYNAMIC TABLE dpt SET LOG_LEVEL = NOPE",
                "REFUSED Invalid value for log level. Allowed values are TRACE,DEBUG,INFO,WARN,ERROR,FATAL,OFF."},
            {"ALTER DYNAMIC TABLE dpt SET LOG_LEVEL = 'ERROR'", "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'LOG_LEVEL' IN TABLE dpt ->> SELECT \"key\", \"value\", \"default\", \"level\" "
                + "FROM $1",
                "LOG_LEVEL|ERROR|OFF|TABLE"},
            {"ALTER DYNAMIC TABLE dpt SET DEFAULT_DDL_COLLATION = 'en-ci'", "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'DEFAULT_DDL_COLLATION' IN TABLE dpt ->> SELECT \"key\", \"value\", \"default\", "
                + "\"level\" FROM $1",
                "DEFAULT_DDL_COLLATION|en-ci||TABLE"},
            {"ALTER DYNAMIC TABLE dpt UNSET DATA_METRIC_SCHEDULE", "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'DATA_METRIC_SCHEDULE' IN TABLE dpt ->> SELECT \"key\", \"value\", \"default\", "
                + "\"level\" FROM $1",
                "DATA_METRIC_SCHEDULE|60 MINUTES|60 MINUTES|"},
            {"ALTER DYNAMIC TABLE dpt SET LOG_LEVEL = WARN TRACE_LEVEL = OFF", "Statement executed successfully."},
            {"SHOW PARAMETERS IN TABLE dpt ->> SELECT COUNT(*) FROM $1 WHERE \"level\" = 'TABLE'", "6"},
        });
    }

    @Test
    public void dynamicTableParameterValuesAreJudged() {
        engine.execute("CREATE OR REPLACE TABLE dsrc2 (a INT)");
        final Object wh = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        final String warehouse = wh == null ? "COMPUTE_WH" : String.valueOf(wh);
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE dq TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT a FROM dsrc2");
        assertCells(new String[][] {
            {"ALTER DYNAMIC TABLE dq SET LOG_EVENT_LEVEL = WARN", "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'LOG_EVENT_LEVEL' IN TABLE dq ->> SELECT \"key\", \"value\", \"default\", "
                + "\"level\" FROM $1",
                "LOG_EVENT_LEVEL|WARN|OFF|TABLE"},
            {"ALTER DYNAMIC TABLE dq SET TRACE_LEVEL = NOPE",
                "REFUSED Invalid value for trace level. Allowed values are ALWAYS,ON_EVENT,PROPAGATE,OFF."},
            {"ALTER DYNAMIC TABLE dq SET TRACE_LEVEL = ON_EVENT", "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'TRACE_LEVEL' IN TABLE dq ->> SELECT \"key\", \"value\", \"default\", \"level\" "
                + "FROM $1",
                "TRACE_LEVEL|ON_EVENT|OFF|TABLE"},
            {"ALTER DYNAMIC TABLE dq SET LOG_EVENT_LEVEL = NOPE",
                "REFUSED Invalid value for log event level. Allowed values are TRACE,DEBUG,INFO,WARN,ERROR,FATAL,OFF."},
            {"ALTER DYNAMIC TABLE dq SET LOG_LEVEL = NULL", "Statement executed successfully."},
            {"ALTER DYNAMIC TABLE dq SET LOG_LEVEL = 5",
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'LOG_LEVEL'"},
            {"ALTER DYNAMIC TABLE dq SET LOG_LEVEL = debug", "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'LOG_LEVEL' IN TABLE dq ->> SELECT \"key\", \"value\", \"default\", \"level\" FROM "
                + "$1",
                "LOG_LEVEL|DEBUG|OFF|TABLE"},
            {"ALTER DYNAMIC TABLE dq SET DATA_METRIC_SCHEDULE = 'USING CRON 0 8 * * * UTC'",
                "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'DATA_METRIC_SCHEDULE' IN TABLE dq ->> SELECT \"key\", \"value\", \"default\", "
                + "\"level\" FROM $1",
                "DATA_METRIC_SCHEDULE|USING CRON 0 8 * * * UTC|60 MINUTES|TABLE"},
            {"ALTER DYNAMIC TABLE dq SET DATA_METRIC_SCHEDULE = 'TRIGGER_ON_CHANGES'",
                "Statement executed successfully."},
            {"ALTER DYNAMIC TABLE dq SET DATA_METRIC_SCHEDULE = 'nope'",
                "REFUSED Invalid schedule was specified. Please refer to the docs on what constitutes a valid "
                + "schedule."},
            {"ALTER DYNAMIC TABLE dq SET DATA_METRIC_SCHEDULE = 5",
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'DATA_METRIC_SCHEDULE'"},
            {"SHOW PARAMETERS LIKE 'DATA_METRIC_SCHEDULE' IN TABLE dq ->> SELECT \"key\", \"value\", \"default\", "
                + "\"level\" FROM $1",
                "DATA_METRIC_SCHEDULE|TRIGGER_ON_CHANGES|60 MINUTES|TABLE"},
            {"ALTER DYNAMIC TABLE dq SET MAX_DATA_EXTENSION_TIME_IN_DAYS = 'x'",
                "REFUSED SQL compilation error: | invalid value ['x'] for parameter 'MAX_DATA_EXTENSION_TIME_IN_DAYS'"},
            {"ALTER DYNAMIC TABLE dq SET MAX_DATA_EXTENSION_TIME_IN_DAYS = -1",
                "REFUSED SQL compilation error: | invalid value [-1] for parameter 'MAX_DATA_EXTENSION_TIME_IN_DAYS'"},
            {"ALTER DYNAMIC TABLE dq SET DATA_RETENTION_TIME_IN_DAYS = 2", "Statement executed successfully."},
            {"ALTER DYNAMIC TABLE dq UNSET DATA_RETENTION_TIME_IN_DAYS", "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'DATA_RETENTION_TIME_IN_DAYS' IN TABLE dq ->> SELECT \"key\", \"value\", "
                + "\"default\", \"level\" FROM $1",
                "DATA_RETENTION_TIME_IN_DAYS|1|1|"},
            {"ALTER DYNAMIC TABLE dq SET DEFAULT_DDL_COLLATION = 5",
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'DEFAULT_DDL_COLLATION'"},
            {"ALTER DYNAMIC TABLE dq SET LOG_LEVEL = WARN, TRACE_LEVEL = ALWAYS", "Statement executed successfully."},
            {"SHOW PARAMETERS IN TABLE dq ->> SELECT \"key\", \"value\" FROM $1 WHERE \"level\" = 'TABLE' ORDER BY 1",
                "DATA_METRIC_SCHEDULE|TRIGGER_ON_CHANGES ;; LOG_EVENT_LEVEL|WARN ;; LOG_LEVEL|WARN ;; "
                + "TRACE_LEVEL|ALWAYS"},
            {"ALTER DYNAMIC TABLE dq UNSET LOG_LEVEL, TRACE_LEVEL, LOG_EVENT_LEVEL",
                "Statement executed successfully."},
            {"SHOW PARAMETERS IN TABLE dq ->> SELECT \"key\", \"value\" FROM $1 WHERE \"level\" = 'TABLE' ORDER BY 1",
                "DATA_METRIC_SCHEDULE|TRIGGER_ON_CHANGES"},
            {"ALTER DYNAMIC TABLE dq SET ENABLE_DATA_COMPACTION = FALSE", "Statement executed successfully."},
            {"ALTER DYNAMIC TABLE dq SET NOPE_PARAM = 1",
                "REFUSED SQL compilation error: | invalid property 'NOPE_PARAM' for 'DYNAMIC_TABLE'"},
            {"ALTER DYNAMIC TABLE dq SET QUOTED_IDENTIFIERS_IGNORE_CASE = TRUE", "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'QUOTED_IDENTIFIERS_IGNORE_CASE' IN TABLE dq ->> SELECT \"key\", \"value\", "
                + "\"default\", \"level\" FROM $1",
                "QUOTED_IDENTIFIERS_IGNORE_CASE|true|false|TABLE"},
            {"SHOW PARAMETERS LIKE 'LOG%' IN TABLE dq ->> SELECT \"key\" FROM $1 ORDER BY 1",
                "LOG_EVENT_LEVEL ;; LOG_LEVEL"},
            {"SHOW PARAMETERS IN TABLE dq ->> SELECT \"key\", \"description\" FROM $1 WHERE \"key\" IN ('LOG_LEVEL', "
                + "'TRACE_LEVEL') ORDER BY 1",
                "LOG_LEVEL|LOG_LEVEL to use when filtering events ;; TRACE_LEVEL|Trace level value to use when "
                + "generating/filtering trace events"},
            {"SHOW PARAMETERS IN TABLE dq ->> SELECT \"key\", \"type\" FROM $1 ORDER BY 1",
                "ALLOW_ROW_TIMESTAMP|BOOLEAN ;; ALLOW_V3_EXPRESSION_DEFAULTS|BOOLEAN ;; CATALOG|STRING ;; "
                + "CATALOG_SYNC|STRING ;; DATA_METRIC_SCHEDULE|STRING ;; DATA_RETENTION_TIME_IN_DAYS|NUMBER ;; "
                + "DEFAULT_DDL_COLLATION|STRING ;; ENABLE_DATA_COMPACTION|BOOLEAN ;; EXTERNAL_VOLUME|STRING ;; "
                + "ICEBERG_MERGE_ON_READ_BEHAVIOR|STRING ;; LOG_EVENT_LEVEL|STRING ;; LOG_LEVEL|STRING ;; "
                + "MAX_DATA_EXTENSION_TIME_IN_DAYS|NUMBER ;; QUOTED_IDENTIFIERS_IGNORE_CASE|BOOLEAN ;; "
                + "REPLACE_INVALID_CHARACTERS|BOOLEAN ;; STORAGE_SERIALIZATION_POLICY|STRING ;; "
                + "TARGET_FILE_SIZE|STRING ;; TRACE_LEVEL|STRING ;; VENDED_CREDENTIAL_STORAGE_REGION|STRING"},
        });
    }
}
