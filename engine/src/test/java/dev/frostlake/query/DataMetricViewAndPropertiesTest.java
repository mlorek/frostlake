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
 * A data metric function a table or a view does not carry, refused alike by DROP and MODIFY, and the columns of
 * DATA_METRIC_FUNCTION_REFERENCES: texts at the full width and PROPERTIES a VARIANT object (all live-verified).
 */
public class DataMetricViewAndPropertiesTest extends MeasuredCellsTestSupport {

    @Test
    public void dataMetricReferences() {
        engine.execute("CREATE OR REPLACE TABLE dt (a INT)");
        engine.execute("CREATE OR REPLACE VIEW dv AS SELECT a FROM dt");
        engine.execute("ALTER TABLE dt SET DATA_METRIC_SCHEDULE = 'USING CRON 0 0 1 1 * UTC'");
        engine.execute("ALTER VIEW dv SET DATA_METRIC_SCHEDULE = 'USING CRON 0 0 1 1 * UTC'");
        assertAnswer("REFUSED SQL compilation error: Data metric function NULL_COUNT is not attached to View DV.",
            "ALTER VIEW dv DROP DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (a)");
        assertAnswer("REFUSED SQL compilation error: Data metric function NULL_COUNT is not attached to Table DT.",
            "ALTER TABLE dt DROP DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (a)");
        assertAnswer("REFUSED SQL compilation error: Data metric function NULL_COUNT is not attached to View DV.",
            "ALTER VIEW dv MODIFY DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (a) SUSPEND");
        assertAnswer("REFUSED SQL compilation error: Data metric function NULL_COUNT is not attached to Table DT.",
            "ALTER TABLE dt MODIFY DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (a) SUSPEND");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE dt ADD DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (a)");
        assertShape("METRIC_DATABASE_NAME:VARCHAR(134217728), METRIC_SCHEMA_NAME:VARCHAR(134217728), "
                + "METRIC_NAME:VARCHAR(134217728), METRIC_SIGNATURE:VARCHAR(134217728), "
                + "METRIC_DATA_TYPE:VARCHAR(134217728), REF_ENTITY_DATABASE_NAME:VARCHAR(134217728), "
                + "REF_ENTITY_SCHEMA_NAME:VARCHAR(134217728), REF_ENTITY_NAME:VARCHAR(134217728), "
                + "REF_ENTITY_DOMAIN:VARCHAR(134217728), REF_ARGUMENTS:VARCHAR(134217728), REF_ID:VARCHAR(134217728), "
                + "SCHEDULE:VARCHAR(134217728), SCHEDULE_STATUS:VARCHAR(134217728), "
                + "DATA_QUALITY_NOTIFICATION_STATUS:VARCHAR(134217728), ANOMALY_DETECTION_STATUS:VARCHAR(134217728), "
                + "ANOMALY_DETECTION_SENSITIVITY_LEVEL:VARCHAR(134217728), USE_ROLE:VARCHAR(134217728), "
                + "LEVEL:VARCHAR(134217728), EXCLUDE_TABLE_TYPES:VARCHAR(134217728), PROPERTIES:VARIANT",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'TEST_DB.TEST_SCHEMA.DT', REF_ENTITY_DOMAIN => 'TABLE'))");
        assertAnswer("NULL_COUNT|Table|{\"anomaly_detection_sensitivity_level\":\"MEDIUM\","
                + "\"anomaly_detection_status\":\"DISABLED\",\"data_quality_notification_status\":\"DISABLED\","
                + "\"exclude_table_types\":null,\"filter\":null,\"group_limit\":null,\"level\":\"Table\","
                + "\"use_role\":null,\"within_group\":null}|STARTED",
            "SELECT METRIC_NAME, REF_ENTITY_DOMAIN, PROPERTIES, SCHEDULE_STATUS FROM "
                + "TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'TEST_DB.TEST_SCHEMA.DT', REF_ENTITY_DOMAIN => 'TABLE'))");
        assertAnswer("OBJECT",
            "SELECT TYPEOF(PROPERTIES) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME "
                + "=> 'TEST_DB.TEST_SCHEMA.DT', REF_ENTITY_DOMAIN => 'TABLE'))");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE dt MODIFY DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (a) SUSPEND");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE dt DROP DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (a)");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
