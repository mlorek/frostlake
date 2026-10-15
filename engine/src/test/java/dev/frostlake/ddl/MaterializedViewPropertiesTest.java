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
 * The {@code name = value} properties a materialized view is created with. They are judged as a view's, under
 * the kind's own name: a name nobody knows is a table's - {@code invalid property 'FOO' for 'TABLE'} - a value
 * of another type is refused as written, a parameter of another level {@code cannot} be {@code set at
 * 'MATERIALIZED_VIEW'} and a table's parameter is a schema the statement may not operate on. As a table of a
 * kind it takes a table's staging properties, is refused another kind's, schema evolution and a file location
 * as an unsupported feature, and takes change tracking and a row timestamp only switched off. The schedule its
 * metrics run on is one of six counts of minutes or a CRON expression. Every answer is live-verified.
 */
public class MaterializedViewPropertiesTest extends MeasuredCellsTestSupport {

    @Test
    public void aNameItDoesNotKnowIsATables() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT, y INT)");
        answer(
            "INSERT INTO t1 VALUES (1, 2)");
        answer(
            "CREATE OR REPLACE MATERIALIZED VIEW mv0 AS SELECT x, y FROM t1");
        answer(
            "CREATE OR REPLACE FUNCTION f1(x OBJECT(a INT)) RETURNS VARCHAR AS $$ 's' $$");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'FOO' for 'TABLE'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p CLUSTER BY (x) FOO = 1 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [1] for parameter 'COPY'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p CLUSTER BY (x) COPY = 1 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'FOO' for 'TABLE'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p FOO = 1 CLUSTER BY (x) AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'FOO' for 'TABLE'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p FOO = 'a' AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'CHANGE_TRACKING' for 'MATERIALIZED_VIEW'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p CHANGE_TRACKING = TRUE AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p DATA_RETENTION_TIME_IN_DAYS = 0 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'AS'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p CLUSTER BY (x) FOO AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'AS'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p CLUSTER BY (x) FOO = AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'BAR' for 'TABLE'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p CLUSTER BY (x) FOO = 1 BAR = 2 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'AS'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p FOO AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'FOO' for 'TABLE'",
            "CREATE OR REPLACE VIEW v_p FOO = 1 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'AS'.",
            "CREATE OR REPLACE VIEW v_p FOO AS SELECT x, y FROM t1");
        assertAnswer(
            "View V_P successfully created.",
            "CREATE OR REPLACE VIEW v_p CHANGE_TRACKING = TRUE AS SELECT x, y FROM t1");
    }

    @Test
    public void whatAMaterializedViewTakesAndRefuses() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT, y INT)");
        answer(
            "INSERT INTO t1 VALUES (1, 2)");
        answer(
            "CREATE OR REPLACE MATERIALIZED VIEW mv0 AS SELECT x, y FROM t1");
        answer(
            "CREATE OR REPLACE VIEW v0 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'BAR' for 'TABLE'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p BAR = 1 FOO = 2 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'BAR' for 'TABLE'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p ZED = 1 FOO = 2 BAR = 3 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | duplicate property 'FOO';",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p FOO = 1 FOO = 2 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | duplicate property 'COMMENT';",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p COMMENT = 'a' COMMENT = 'b' AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [TRUE] for parameter 'COPY'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p COPY = TRUE AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type of property ''x'' for 'COPY'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p COPY = 'x' AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [FALSE] for parameter 'COPY'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p COPY = FALSE AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECURE' for 'TABLE'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p SECURE = TRUE AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [5] for parameter 'CHANGE_TRACKING'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p CHANGE_TRACKING = 5 AS SELECT x, y FROM t1");
        assertAnswer(
            "Materialized view MV_P successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p CHANGE_TRACKING = FALSE AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED Unsupported feature 'ENABLE_SCHEMA_EVOLUTION'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p ENABLE_SCHEMA_EVOLUTION = TRUE AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'ERROR_LOGGING' for 'MATERIALIZED_VIEW'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p ERROR_LOGGING = TRUE AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'ROW_TIMESTAMP' for 'MATERIALIZED_VIEW'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p ROW_TIMESTAMP = TRUE AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED Invalid schedule was specified. Please refer to the docs on what constitutes a valid "
                + "schedule.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p DATA_METRIC_SCHEDULE = 'TRIGGER_ON_CHANGES' AS SELECT x, y "
                + "FROM t1");
        assertAnswer(
            "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p MAX_DATA_EXTENSION_TIME_IN_DAYS = 1 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p DEFAULT_DDL_COLLATION = 'en' AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | cannot set parameter 'TIMEZONE' at 'MATERIALIZED_VIEW'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p TIMEZONE = 'UTC' AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [1] for parameter 'TIMEZONE'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p TIMEZONE = 1 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value ['x'] for parameter 'DATA_RETENTION_TIME_IN_DAYS'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p DATA_RETENTION_TIME_IN_DAYS = 'x' AS SELECT x, y FROM t1");
        assertAnswer(
            "Materialized view MV_P successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p COMMENT = 'c' AS SELECT x, y FROM t1");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), reserved:VARCHAR(16777216), "
                + "database_name:VARCHAR(16777216), schema_name:VARCHAR(16777216), cluster_by:VARCHAR(16777216), "
                + "rows:NUMBER(38,0), bytes:NUMBER(38,0), source_database_name:VARCHAR(16777216), "
                + "source_schema_name:VARCHAR(16777216), source_table_name:VARCHAR(16777216), "
                + "refreshed_on:TIMESTAMP_LTZ, compacted_on:TIMESTAMP_LTZ, owner:VARCHAR(16777216), "
                + "invalid:VARCHAR(16777216), invalid_reason:VARCHAR(16777216), behind_by:VARCHAR(16777216), "
                + "comment:VARCHAR(16777216), text:VARCHAR(16777216), is_secure:VARCHAR(16777216), "
                + "automatic_clustering:VARCHAR(16777216), owner_role_type:VARCHAR(16777216)",
            "SHOW MATERIALIZED VIEWS LIKE 'MV_P' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "MV_P|c|",
            "SELECT \"name\", \"comment\", \"cluster_by\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [5] for parameter 'COMMENT'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p COMMENT = 5 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'FOO' for 'TABLE'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p FOO = 1 CHANGE_TRACKING = TRUE AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'FOO' for 'TABLE'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p CHANGE_TRACKING = TRUE FOO = 1 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p CHANGE_TRACKING = TRUE DATA_RETENTION_TIME_IN_DAYS = 0 AS "
                + "SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p DATA_RETENTION_TIME_IN_DAYS = 0 CHANGE_TRACKING = TRUE AS "
                + "SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p QUOTED_IDENTIFIERS_IGNORE_CASE = TRUE AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED Unsupported feature 'AUTO_REFRESH'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p AUTO_REFRESH = TRUE AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p LOG_LEVEL = 'INFO' AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property '\"foo\"' for 'TABLE'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p \"foo\" = 1 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | duplicate property 'COMMENT';",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p COMMENT = 'k' CLUSTER BY (x) COMMENT = 'l' AS SELECT x, y "
                + "FROM t1");
        assertAnswer(
            "Materialized view MV_P successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p CLUSTER BY (x) CLUSTER BY (y) AS SELECT x, y FROM t1");
        assertAnswer(
            "Materialized view MV_P successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p COMMENT = 'k' CLUSTER BY (y, x) AS SELECT x, y FROM t1");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), reserved:VARCHAR(16777216), "
                + "database_name:VARCHAR(16777216), schema_name:VARCHAR(16777216), cluster_by:VARCHAR(16777216), "
                + "rows:NUMBER(38,0), bytes:NUMBER(38,0), source_database_name:VARCHAR(16777216), "
                + "source_schema_name:VARCHAR(16777216), source_table_name:VARCHAR(16777216), "
                + "refreshed_on:TIMESTAMP_LTZ, compacted_on:TIMESTAMP_LTZ, owner:VARCHAR(16777216), "
                + "invalid:VARCHAR(16777216), invalid_reason:VARCHAR(16777216), behind_by:VARCHAR(16777216), "
                + "comment:VARCHAR(16777216), text:VARCHAR(16777216), is_secure:VARCHAR(16777216), "
                + "automatic_clustering:VARCHAR(16777216), owner_role_type:VARCHAR(16777216)",
            "SHOW MATERIALIZED VIEWS LIKE 'MV_P' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "MV_P|k|LINEAR(y, x)",
            "SELECT \"name\", \"comment\", \"cluster_by\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [1] for parameter 'COPY'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p CHANGE_TRACKING = TRUE COPY = 1 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'FOO' for 'TABLE'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p COPY = 1 FOO = 2 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED Unsupported feature 'ENABLE_SCHEMA_EVOLUTION'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p ENABLE_SCHEMA_EVOLUTION = 5 AS SELECT x, y FROM t1");
        assertAnswer(
            "Materialized view MV_P successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p STAGE_FILE_FORMAT = 'x' AS SELECT x, y FROM t1");
    }

    @Test
    public void theKindJudgesTheNamesItKnowsClassByClass() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT, y INT)");
        answer(
            "INSERT INTO t1 VALUES (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [5] for parameter 'CHANGE_TRACKING'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p ENABLE_SCHEMA_EVOLUTION = TRUE CHANGE_TRACKING = 5 AS "
                + "SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [5] for parameter 'CHANGE_TRACKING'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p CHANGE_TRACKING = 5 ENABLE_SCHEMA_EVOLUTION = TRUE AS "
                + "SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'FOO' for 'TABLE'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p AUTO_REFRESH = TRUE FOO = 1 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'FOO' for 'TABLE'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p ENABLE_SCHEMA_EVOLUTION = TRUE FOO = 1 AS SELECT x, y FROM "
                + "t1");
        assertAnswer(
            "REFUSED Unsupported feature 'ENABLE_SCHEMA_EVOLUTION'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p ENABLE_SCHEMA_EVOLUTION = FALSE AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'ERROR_LOGGING' for 'MATERIALIZED_VIEW'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p ERROR_LOGGING = FALSE AS SELECT x, y FROM t1");
        assertAnswer(
            "Materialized view MV_P successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p ROW_TIMESTAMP = FALSE AS SELECT x, y FROM t1");
        assertAnswer(
            "Materialized view MV_P successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p STAGE_COPY_OPTIONS = 'x' AS SELECT x, y FROM t1");
        assertAnswer(
            "Materialized view MV_P successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p DATA_METRIC_SCHEDULE = '5 MINUTE' AS SELECT x, y FROM t1");
        assertAnswer(
            "Materialized view MV_P successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p DATA_METRIC_SCHEDULE = 'USING CRON 0 0 * * * UTC' AS SELECT "
                + "x, y FROM t1");
        assertAnswer(
            "REFUSED Invalid schedule was specified. Please refer to the docs on what constitutes a valid "
                + "schedule.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p DATA_METRIC_SCHEDULE = 'x' AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [5] for parameter 'DATA_METRIC_SCHEDULE'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p DATA_METRIC_SCHEDULE = 5 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | cannot set parameter 'TIMEZONE' at 'MATERIALIZED_VIEW'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p TIMEZONE = 'UTC' CHANGE_TRACKING = TRUE AS SELECT x, y FROM "
                + "t1");
        assertAnswer(
            "REFUSED SQL compilation error: | cannot set parameter 'TIMEZONE' at 'MATERIALIZED_VIEW'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p CHANGE_TRACKING = TRUE TIMEZONE = 'UTC' AS SELECT x, y FROM "
                + "t1");
        assertAnswer(
            "REFUSED SQL compilation error: | cannot set parameter 'TIMEZONE' at 'MATERIALIZED_VIEW'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p TIMEZONE = 'UTC' DATA_RETENTION_TIME_IN_DAYS = 0 AS SELECT "
                + "x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | cannot set parameter 'TIMEZONE' at 'MATERIALIZED_VIEW'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p DATA_RETENTION_TIME_IN_DAYS = 0 TIMEZONE = 'UTC' AS SELECT "
                + "x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'ERROR_LOGGING' for 'MATERIALIZED_VIEW'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p ERROR_LOGGING = TRUE CHANGE_TRACKING = TRUE AS SELECT x, y "
                + "FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'ERROR_LOGGING' for 'MATERIALIZED_VIEW'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p CHANGE_TRACKING = TRUE ERROR_LOGGING = TRUE AS SELECT x, y "
                + "FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'ERROR_LOGGING' for 'MATERIALIZED_VIEW'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p ROW_TIMESTAMP = TRUE ERROR_LOGGING = TRUE AS SELECT x, y "
                + "FROM t1");
        assertAnswer(
            "REFUSED Unsupported feature 'ENABLE_SCHEMA_EVOLUTION'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p ENABLE_SCHEMA_EVOLUTION = TRUE DATA_RETENTION_TIME_IN_DAYS "
                + "= 0 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED Unsupported feature 'ENABLE_SCHEMA_EVOLUTION'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p ENABLE_SCHEMA_EVOLUTION = TRUE ERROR_LOGGING = TRUE AS "
                + "SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED Unsupported feature 'ENABLE_SCHEMA_EVOLUTION'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p ENABLE_SCHEMA_EVOLUTION = TRUE TIMEZONE = 'UTC' AS SELECT x,"
                + " y FROM t1");
        assertAnswer(
            "REFUSED Unsupported feature 'AUTO_REFRESH'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p AUTO_REFRESH = TRUE ENABLE_SCHEMA_EVOLUTION = TRUE AS "
                + "SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED Unsupported feature 'AUTO_REFRESH'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p ENABLE_SCHEMA_EVOLUTION = TRUE AUTO_REFRESH = TRUE AS "
                + "SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED Unsupported feature 'LOCATION'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p LOCATION = 'x' AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED Unsupported feature 'PATTERN'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p PATTERN = 'x' AS SELECT x, y FROM t1");
        assertAnswer(
            "Materialized view MV_P successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p CHANGE_TRACKING = FALSE STAGE_FILE_FORMAT = 'x' COMMENT = "
                + "'z' CLUSTER BY (x) AS SELECT x, y FROM t1");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), reserved:VARCHAR(16777216), "
                + "database_name:VARCHAR(16777216), schema_name:VARCHAR(16777216), cluster_by:VARCHAR(16777216), "
                + "rows:NUMBER(38,0), bytes:NUMBER(38,0), source_database_name:VARCHAR(16777216), "
                + "source_schema_name:VARCHAR(16777216), source_table_name:VARCHAR(16777216), "
                + "refreshed_on:TIMESTAMP_LTZ, compacted_on:TIMESTAMP_LTZ, owner:VARCHAR(16777216), "
                + "invalid:VARCHAR(16777216), invalid_reason:VARCHAR(16777216), behind_by:VARCHAR(16777216), "
                + "comment:VARCHAR(16777216), text:VARCHAR(16777216), is_secure:VARCHAR(16777216), "
                + "automatic_clustering:VARCHAR(16777216), owner_role_type:VARCHAR(16777216)",
            "SHOW MATERIALIZED VIEWS LIKE 'MV_P' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "MV_P|z|LINEAR(x)",
            "SELECT \"name\", \"comment\", \"cluster_by\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED SQL compilation error: | options STAGE_FILE_FORMAT and {1} are incompatible.",
            "CREATE OR REPLACE VIEW v_p STAGE_FILE_FORMAT = 'x' AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | options ENABLE_SCHEMA_EVOLUTION and {1} are incompatible.",
            "CREATE OR REPLACE VIEW v_p ENABLE_SCHEMA_EVOLUTION = TRUE AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | options AUTO_REFRESH and {1} are incompatible.",
            "CREATE OR REPLACE VIEW v_p AUTO_REFRESH = TRUE AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED Invalid schedule was specified. Please refer to the docs on what constitutes a valid "
                + "schedule.",
            "CREATE OR REPLACE VIEW v_p DATA_METRIC_SCHEDULE = 'TRIGGER_ON_CHANGES' AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED Invalid schedule was specified. Please refer to the docs on what constitutes a valid "
                + "schedule.",
            "CREATE OR REPLACE VIEW v_p DATA_METRIC_SCHEDULE = 'x' AS SELECT x, y FROM t1");
    }

    @Test
    public void twoRefusedNamesOfTwoClasses() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT, y INT)");
        answer(
            "INSERT INTO t1 VALUES (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | cannot set parameter 'TIMEZONE' at 'MATERIALIZED_VIEW'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p ERROR_LOGGING = TRUE TIMEZONE = 'UTC' AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | cannot set parameter 'TIMEZONE' at 'MATERIALIZED_VIEW'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p TIMEZONE = 'UTC' ERROR_LOGGING = TRUE AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p ERROR_LOGGING = TRUE DATA_RETENTION_TIME_IN_DAYS = 0 AS "
                + "SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p DATA_METRIC_SCHEDULE = 'x' DATA_RETENTION_TIME_IN_DAYS = 0 "
                + "AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | cannot set parameter 'TIMEZONE' at 'MATERIALIZED_VIEW'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p DATA_METRIC_SCHEDULE = 'x' TIMEZONE = 'UTC' AS SELECT x, y "
                + "FROM t1");
        assertAnswer(
            "REFUSED Invalid schedule was specified. Please refer to the docs on what constitutes a valid "
                + "schedule.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p CHANGE_TRACKING = TRUE DATA_METRIC_SCHEDULE = 'x' AS SELECT "
                + "x, y FROM t1");
        assertAnswer(
            "REFUSED Invalid schedule was specified. Please refer to the docs on what constitutes a valid "
                + "schedule.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p DATA_METRIC_SCHEDULE = 'x' CHANGE_TRACKING = TRUE AS SELECT "
                + "x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'CHANGE_TRACKING' for 'MATERIALIZED_VIEW'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p ROW_TIMESTAMP = TRUE CHANGE_TRACKING = TRUE AS SELECT x, y "
                + "FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'CHANGE_TRACKING' for 'MATERIALIZED_VIEW'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p CHANGE_TRACKING = TRUE ROW_TIMESTAMP = TRUE AS SELECT x, y "
                + "FROM t1");
        assertAnswer(
            "REFUSED Unsupported feature 'BASE_LOCATION'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p BASE_LOCATION = 'x' AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [5] for parameter 'CHANGE_TRACKING'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p COPY = 1 CHANGE_TRACKING = 5 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [5] for parameter 'CHANGE_TRACKING'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p CHANGE_TRACKING = 5 COPY = 'x' AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [1] for parameter 'COPY'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p COPY = 1 AUTO_REFRESH = TRUE AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED Invalid schedule was specified. Please refer to the docs on what constitutes a valid "
                + "schedule.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p DATA_METRIC_SCHEDULE = '7 MINUTE' AS SELECT x, y FROM t1");
        assertAnswer(
            "Materialized view MV_P successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p DATA_METRIC_SCHEDULE = '5 minute' AS SELECT x, y FROM t1");
        assertAnswer(
            "Materialized view MV_P successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p DATA_METRIC_SCHEDULE = '60 MINUTE' AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED Invalid schedule was specified. Please refer to the docs on what constitutes a valid "
                + "schedule.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p DATA_METRIC_SCHEDULE = '1 HOUR' AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED Invalid schedule was specified. Please refer to the docs on what constitutes a valid "
                + "schedule.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p DATA_METRIC_SCHEDULE = 'USING CRON x' AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED Invalid schedule was specified. Please refer to the docs on what constitutes a valid "
                + "schedule.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p DATA_METRIC_SCHEDULE = x AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED Invalid schedule was specified. Please refer to the docs on what constitutes a valid "
                + "schedule.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p DATA_METRIC_SCHEDULE = '0 MINUTE' AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | duplicate property 'COMMENT';",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p COMMENT = 'a' COMMENT = 5 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | duplicate property 'COMMENT';",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p COMMENT = 5 COMMENT = 'a' AS SELECT x, y FROM t1");
    }
}
