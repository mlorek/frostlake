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
 * A CREATE DYNAMIC TABLE judges the properties it is written with as a table's, in the dynamic table's own words and
 * before the warehouse is looked for: a name written twice, its own TARGET_LAG among them, is a duplicate; a name it
 * does not know - CHANGE_TRACKING, ERROR_LOGGING, QUERY_TAG, MAX_FILE_SIZE and CLUSTER among them - is an invalid
 * property, the first of a table hashed by name; a value of another type is refused as a table's is, its own
 * TARGET_LAG, SCHEDULER and INITIALIZE taking a text and its REFRESH_MODE a mode; ENABLE_SCHEMA_EVOLUTION,
 * BASE_LOCATION and another kind of table's properties are unsupported features; a parameter it takes no value of is
 * refused at 'DYNAMIC_TABLE'; and a catalog is read as an integration's name. Its log and trace levels, SCHEDULER, a
 * table's parameters, its staging properties and a storage serialization policy are taken, and so the warehouse is
 * looked for. Every answer is live-verified.
 */
public class DynamicTablePropertyListTest extends MeasuredCellsTestSupport {

    private static final String DT = "CREATE OR REPLACE DYNAMIC TABLE dt971 TARGET_LAG = '1 day' "
        + "WAREHOUSE = nosuch_wh_w9 ";

    private static final String NO_WAREHOUSE = "REFUSED Warehouse 'NOSUCH_WH_W9' does not exist.";

    private static String dt(final String properties) {
        return DT + properties + " AS SELECT a FROM pt971";
    }

    private static String invalid(final String name) {
        return "REFUSED SQL compilation error: | invalid property '" + name + "' for 'DYNAMIC_TABLE'";
    }

    private static String invalidValue(final String value, final String name) {
        return "REFUSED SQL compilation error: | invalid value [" + value + "] for parameter '" + name + "'";
    }

    private static String cannotSet(final String name) {
        return "REFUSED SQL compilation error: | cannot set parameter '" + name + "' at 'DYNAMIC_TABLE'";
    }

    @Test
    public void namesAreJudgedBeforeTheWarehouse() {
        engine.execute("CREATE OR REPLACE TABLE pt971 (a INT)");
        assertCells(new String[][] {
            {dt("x = 1"), invalid("x")},
            {dt("x = 1 y = 2"), invalid("x")},
            {dt("x = (1)"), invalid("x")},
            {dt("x = 'a'"), invalid("x")},
            {dt("x = 1, COMMENT = 'c'"), invalid("x")},
            {dt("CASE = 1"), invalid("CASE")},
            {dt("CHANGE_TRACKING = TRUE"), invalid("CHANGE_TRACKING")},
            {dt("CHANGE_TRACKING = 'x'"), invalid("CHANGE_TRACKING")},
            {dt("x = 1 CHANGE_TRACKING = TRUE"), invalid("CHANGE_TRACKING")},
            {dt("ERROR_LOGGING = TRUE"), invalid("ERROR_LOGGING")},
            {dt("QUERY_TAG = 'x'"), invalid("QUERY_TAG")},
            {dt("MAX_FILE_SIZE = 1"), invalid("MAX_FILE_SIZE")},
            {dt("CLUSTER = 1"), invalid("CLUSTER")},
            {dt("DATA_RETENTION_TIME_IN_DAYS = 1 x = 1"), invalid("x")},
            {dt("x = 1 DATA_RETENTION_TIME_IN_DAYS = 'x'"), invalid("x")},
            {dt("DEFAULT = 1 DEFAULT = 2"), "REFUSED SQL compilation error: | duplicate property 'DEFAULT';"},
            {dt("COMMENT = 'c' COMMENT = 'd'"), "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {"CREATE OR REPLACE DYNAMIC TABLE dt971 TARGET_LAG = '1 day' TARGET_LAG = '2 days' "
                + "WAREHOUSE = nosuch_wh_w9 AS SELECT a FROM pt971",
                "REFUSED SQL compilation error: | duplicate property 'TARGET_LAG';"},
        });
    }

    @Test
    public void valuesAreJudgedAsATablesAndItsOwnByTheirForms() {
        engine.execute("CREATE OR REPLACE TABLE pt971 (a INT)");
        assertCells(new String[][] {
            {dt("DATA_RETENTION_TIME_IN_DAYS = 'x'"), invalidValue("'x'", "DATA_RETENTION_TIME_IN_DAYS")},
            {dt("DATA_RETENTION_TIME_IN_DAYS = -1"), invalidValue("-1", "DATA_RETENTION_TIME_IN_DAYS")},
            {dt("COMMENT = 1"), invalidValue("1", "COMMENT")},
            {dt("ROW_TIMESTAMP = 'x'"), invalidValue("'x'", "ROW_TIMESTAMP")},
            {dt("MAX_DATA_EXTENSION_TIME_IN_DAYS = 'x'"), invalidValue("'x'", "MAX_DATA_EXTENSION_TIME_IN_DAYS")},
            {dt("TIMEZONE = 5"), invalidValue("5", "TIMEZONE")},
            {dt("SCHEDULER = 5"), invalidValue("5", "SCHEDULER")},
            {dt("INITIALIZE = 1"), invalidValue("1", "INITIALIZE")},
            {dt("COPY = 1"), invalidValue("1", "COPY")},
            {dt("REFRESH_MODE = x"),
                "REFUSED SQL compilation error: | invalid value 'X' for property 'REFRESH_MODE'"},
            {"CREATE OR REPLACE DYNAMIC TABLE dt971 TARGET_LAG = 5 WAREHOUSE = nosuch_wh_w9 AS SELECT a FROM pt971",
                invalidValue("5", "TARGET_LAG")},
            {"CREATE OR REPLACE DYNAMIC TABLE dt971 TARGET_LAG = '1 day' WAREHOUSE = 'x' AS SELECT a FROM pt971",
                "REFUSED Warehouse 'x' does not exist."},
            {dt("REFRESH_MODE = 'FULL'"), NO_WAREHOUSE},
            {dt("INITIALIZE = 'ON_CREATE'"), NO_WAREHOUSE},
            {dt("COMMENT = NULL"), NO_WAREHOUSE},
        });
    }

    @Test
    public void kindsAndParametersAreRefusedInTheDynamicTablesWords() {
        engine.execute("CREATE OR REPLACE TABLE pt971 (a INT)");
        assertCells(new String[][] {
            {dt("ENABLE_SCHEMA_EVOLUTION = TRUE"), "REFUSED Unsupported feature 'ENABLE_SCHEMA_EVOLUTION'."},
            {dt("ENABLE_SCHEMA_EVOLUTION = 'x'"), "REFUSED Unsupported feature 'ENABLE_SCHEMA_EVOLUTION'."},
            {dt("BASE_LOCATION = 'x'"), "REFUSED Unsupported feature 'BASE_LOCATION'."},
            {dt("AUTO_REFRESH = TRUE"), "REFUSED Unsupported feature 'AUTO_REFRESH'."},
            {dt("TIMEZONE = 'UTC'"), cannotSet("TIMEZONE")},
            {dt("COMMENT = 'c' TIMEZONE = 'UTC'"), cannotSet("TIMEZONE")},
            {dt("DATE_OUTPUT_FORMAT = 'YYYY'"), cannotSet("DATE_OUTPUT_FORMAT")},
            {dt("SUSPEND_TASK_AFTER_NUM_FAILURES = 1"), cannotSet("SUSPEND_TASK_AFTER_NUM_FAILURES")},
            {dt("MAX_CONCURRENCY_LEVEL = 8"), cannotSet("MAX_CONCURRENCY_LEVEL")},
            {dt("CATALOG = 'x'"),
                "REFUSED Catalog integration x is not valid. Please ensure required properties are valid."},
            {dt("EXTERNAL_VOLUME = 'x'"), "REFUSED EXTERNAL_VOLUME is not supported on table DT971."},
        });
    }

    @Test
    public void whatItTakesLeavesTheWarehouseToBeLookedFor() {
        engine.execute("CREATE OR REPLACE TABLE pt971 (a INT)");
        assertCells(new String[][] {
            {dt("MAX_DATA_EXTENSION_TIME_IN_DAYS = 1"), NO_WAREHOUSE},
            {dt("DEFAULT_DDL_COLLATION = 'en'"), NO_WAREHOUSE},
            {dt("ROW_TIMESTAMP = TRUE"), NO_WAREHOUSE},
            {dt("STAGE_FILE_FORMAT = (TYPE = CSV)"), NO_WAREHOUSE},
            {dt("STAGE_COPY_OPTIONS = (ON_ERROR = CONTINUE)"), NO_WAREHOUSE},
            {dt("LOG_LEVEL = 'INFO'"), NO_WAREHOUSE},
            {dt("TRACE_LEVEL = 'ALWAYS'"), NO_WAREHOUSE},
            {dt("SCHEDULER = 'ENABLE'"), NO_WAREHOUSE},
            {dt("QUOTED_IDENTIFIERS_IGNORE_CASE = TRUE"), NO_WAREHOUSE},
            {dt("STORAGE_SERIALIZATION_POLICY = 'COMPATIBLE'"), NO_WAREHOUSE},
        });
    }
}
