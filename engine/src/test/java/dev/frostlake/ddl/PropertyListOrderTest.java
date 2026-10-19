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
 * Which of several refused names a property list answers for. The classes come in one order - a repeated name,
 * an unknown one, a value of another type, another kind's property, another level's parameter - and within a
 * class the name refused is not the first written but the first of a table hashed by name: {@code A = 1 P = 2}
 * and {@code P = 1 A = 2} both refuse P, of thirteen names K1 to K13 the one refused is K1 in either order. A
 * repeated name alone is refused where it is read. COPY takes no value: a number or a switch value is an
 * invalid value of it, a text, a word or a list an invalid type. Every answer is live-verified.
 */
public class PropertyListOrderTest extends MeasuredCellsTestSupport {

    @Test
    public void ofSeveralNamesTheOneRefused() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT, y INT)");
        answer(
            "INSERT INTO t1 VALUES (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'P' for 'TABLE'",
            "CREATE OR REPLACE TABLE t_p (a INT) A = 1 P = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'P' for 'TABLE'",
            "CREATE OR REPLACE TABLE t_p (a INT) P = 1 A = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'P' for 'TABLE'",
            "CREATE OR REPLACE TABLE t_p (a INT) B = 1 P = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'P' for 'TABLE'",
            "CREATE OR REPLACE TABLE t_p (a INT) P = 1 B = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'P' for 'TABLE'",
            "CREATE OR REPLACE TABLE t_p (a INT) P = 1 A = 2 B = 3");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'A' for 'TABLE'",
            "CREATE OR REPLACE TABLE t_p (a INT) b = 1 A = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'A' for 'TABLE'",
            "CREATE OR REPLACE TABLE t_p (a INT) \"b\" = 1 A = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'A' for 'TABLE'",
            "CREATE OR REPLACE TABLE t_p (a INT) A = 1 \"b\" = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [5] for parameter 'CHANGE_TRACKING'",
            "CREATE OR REPLACE TABLE t_p (a INT) COMMENT = 5 CHANGE_TRACKING = 5");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [5] for parameter 'CHANGE_TRACKING'",
            "CREATE OR REPLACE TABLE t_p (a INT) CHANGE_TRACKING = 5 COMMENT = 5");
        assertAnswer(
            "REFUSED SQL compilation error: | cannot set parameter 'TIMEZONE' at 'TABLE'",
            "CREATE OR REPLACE TABLE t_p (a INT) TIMEZONE = 'UTC' LOG_LEVEL = 'INFO'");
        assertAnswer(
            "REFUSED SQL compilation error: | cannot set parameter 'TIMEZONE' at 'TABLE'",
            "CREATE OR REPLACE TABLE t_p (a INT) LOG_LEVEL = 'INFO' TIMEZONE = 'UTC'");
        assertAnswer(
            "REFUSED SQL compilation error: | cannot set parameter 'AUTOCOMMIT' at 'TABLE'",
            "CREATE OR REPLACE TABLE t_p (a INT) TIMEZONE = 'UTC' AUTOCOMMIT = TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: | cannot set parameter 'AUTOCOMMIT' at 'TABLE'",
            "CREATE OR REPLACE TABLE t_p (a INT) AUTOCOMMIT = TRUE TIMEZONE = 'UTC'");
        assertAnswer(
            "REFUSED SQL compilation error: | duplicate property 'A';",
            "CREATE OR REPLACE TABLE t_p (a INT) A = 1 A = 2 B = 3 B = 4");
        assertAnswer(
            "REFUSED SQL compilation error: | duplicate property 'B';",
            "CREATE OR REPLACE TABLE t_p (a INT) B = 1 B = 2 A = 3 A = 4");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type of property ''x'' for 'COPY'",
            "CREATE OR REPLACE TABLE t_p (a INT) COPY = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type of property ''x'' for 'COPY'",
            "CREATE OR REPLACE VIEW v_p COPY = 'x' AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type of property 'GRANTS' for 'COPY'",
            "CREATE OR REPLACE VIEW v_p COPY = GRANTS AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'P' for 'TABLE'",
            "CREATE OR REPLACE VIEW v_p A = 1 P = 2 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'P' for 'TABLE'",
            "CREATE OR REPLACE VIEW v_p P = 1 A = 2 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'P' for 'TABLE'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p A = 1 P = 2 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'P' for 'TABLE'",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_p P = 1 A = 2 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'BAR' for 'TABLE'",
            "CREATE OR REPLACE VIEW v_p BAR = 1 FOO = 2 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'BAR' for 'TABLE'",
            "CREATE OR REPLACE VIEW v_p FOO = 1 BAR = 2 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'BAR' for 'TABLE'",
            "CREATE OR REPLACE VIEW v_p ZED = 1 FOO = 2 BAR = 3 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'BAR' for 'TABLE'",
            "CREATE OR REPLACE TABLE t_p (a INT) BAR = 1 FOO = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'BAR' for 'TABLE'",
            "CREATE OR REPLACE TABLE t_p (a INT) FOO = 1 BAR = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'BAR' for 'TABLE'",
            "CREATE OR REPLACE TABLE t_p (a INT) ZED = 1 FOO = 2 BAR = 3");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [1] for parameter 'COPY'",
            "CREATE OR REPLACE VIEW v_p COPY = 1 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [1] for parameter 'COPY'",
            "CREATE OR REPLACE TABLE t_p (a INT) COPY = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [TRUE] for parameter 'COPY'",
            "CREATE OR REPLACE VIEW v_p COPY = TRUE AS SELECT x, y FROM t1");
    }

    @Test
    public void aViewsAndATablesClasses() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT, y INT)");
        answer(
            "INSERT INTO t1 VALUES (1, 2)");
        assertAnswer(
            "View V_P successfully created.",
            "CREATE OR REPLACE VIEW v_p DATA_METRIC_SCHEDULE = '5 MINUTE' AS SELECT x, y FROM t1");
        assertAnswer(
            "View V_P successfully created.",
            "CREATE OR REPLACE VIEW v_p ROW_TIMESTAMP = FALSE AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'ROW_TIMESTAMP' for 'VIEW'",
            "CREATE OR REPLACE VIEW v_p ROW_TIMESTAMP = TRUE AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'ERROR_LOGGING' for 'VIEW'",
            "CREATE OR REPLACE VIEW v_p ERROR_LOGGING = TRUE AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | cannot set parameter 'TIMEZONE' at 'VIEW'",
            "CREATE OR REPLACE VIEW v_p ERROR_LOGGING = TRUE TIMEZONE = 'UTC' AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | options STAGE_FILE_FORMAT and {1} are incompatible.",
            "CREATE OR REPLACE VIEW v_p STAGE_FILE_FORMAT = 'x' ERROR_LOGGING = TRUE AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | options ENABLE_SCHEMA_EVOLUTION and {1} are incompatible.",
            "CREATE OR REPLACE VIEW v_p ENABLE_SCHEMA_EVOLUTION = 5 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | options LOCATION and {1} are incompatible.",
            "CREATE OR REPLACE VIEW v_p LOCATION = 'x' AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | cannot set parameter 'TIMEZONE' at 'VIEW'",
            "CREATE OR REPLACE VIEW v_p TIMEZONE = 'UTC' DATA_RETENTION_TIME_IN_DAYS = 0 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [1] for parameter 'COPY'",
            "CREATE OR REPLACE VIEW v_p CHANGE_TRACKING = TRUE COPY = 1 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "CREATE OR REPLACE VIEW v_p DATA_METRIC_SCHEDULE = 'x' DATA_RETENTION_TIME_IN_DAYS = 0 AS SELECT x, y "
                + "FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | options BASE_LOCATION and {1} are incompatible.",
            "CREATE OR REPLACE VIEW v_p BASE_LOCATION = 'x' AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'FOO' for 'TABLE'",
            "CREATE OR REPLACE TABLE t_p (a INT) COPY = 1 FOO = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [5] for parameter 'CHANGE_TRACKING'",
            "CREATE OR REPLACE TABLE t_p (a INT) COPY = 1 CHANGE_TRACKING = 5");
        assertAnswer(
            "REFUSED Unsupported feature 'AUTO_REFRESH'.",
            "CREATE OR REPLACE TABLE t_p (a INT) AUTO_REFRESH = TRUE TIMEZONE = 'UTC'");
        assertAnswer(
            "REFUSED Unsupported feature 'AUTO_REFRESH'.",
            "CREATE OR REPLACE TABLE t_p (a INT) TIMEZONE = 'UTC' AUTO_REFRESH = TRUE");
        assertAnswer(
            "REFUSED Invalid schedule was specified. Please refer to the docs on what constitutes a valid "
                + "schedule.",
            "CREATE OR REPLACE TABLE t_p (a INT) DATA_METRIC_SCHEDULE = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'K1' for 'TABLE'",
            "CREATE OR REPLACE TABLE t_p (a INT) K1 = 1 K2 = 1 K3 = 1 K4 = 1 K5 = 1 K6 = 1 K7 = 1 K8 = 1 K9 = 1 "
                + "K10 = 1 K11 = 1 K12 = 1 K13 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'K1' for 'TABLE'",
            "CREATE OR REPLACE TABLE t_p (a INT) K1 = 1 K2 = 1 K3 = 1 K4 = 1 K5 = 1 K6 = 1 K7 = 1 K8 = 1 K9 = 1 "
                + "K10 = 1 K11 = 1 K12 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'K1' for 'TABLE'",
            "CREATE OR REPLACE TABLE t_p (a INT) K13 = 1 K12 = 1 K11 = 1 K10 = 1 K9 = 1 K8 = 1 K7 = 1 K6 = 1 K5 = "
                + "1 K4 = 1 K3 = 1 K2 = 1 K1 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [-1] for parameter 'COPY'",
            "CREATE OR REPLACE TABLE t_p (a INT) COPY = -1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type of property 'CONTINUE' for 'COPY'",
            "CREATE OR REPLACE TABLE t_p (a INT) COPY = CONTINUE");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type of property 'TOK_PROPERTY_LIST' for 'COPY'",
            "CREATE OR REPLACE TABLE t_p (a INT) COPY = (a = 1)");
    }
}
