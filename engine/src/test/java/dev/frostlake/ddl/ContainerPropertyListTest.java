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
 * ALTER SCHEMA and ALTER DATABASE take several properties per SET, apart or comma-separated, COMMENT among them, and
 * a comma list per UNSET, as ALTER TABLE does; a property given twice and a name the container does not take are
 * refused (live-verified).
 */
public class ContainerPropertyListTest extends MeasuredCellsTestSupport {

    private static final String DONE = "Statement executed successfully.";
    private static final String LISTED =
        "SELECT \"key\", \"value\", \"level\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1";
    private static final String S = "test_db.s1430";

    @Test
    public void aSchemaTakesAListOfProperties() {
        answer("CREATE SCHEMA IF NOT EXISTS " + S);
        try {
            assertAnswer(DONE, "ALTER SCHEMA " + S + " SET DATA_RETENTION_TIME_IN_DAYS = 3 LOG_EVENT_LEVEL = 'ERROR'");
            answer("SHOW PARAMETERS LIKE '%E%EL' IN SCHEMA " + S);
            assertAnswer("LOG_EVENT_LEVEL|ERROR|SCHEMA ;; LOG_LEVEL|OFF| ;; METRIC_LEVEL|NONE| ;; TRACE_LEVEL|OFF|",
                LISTED);
            answer("SHOW PARAMETERS LIKE 'DATA_RETENTION%' IN SCHEMA " + S);
            assertAnswer("DATA_RETENTION_TIME_IN_DAYS|3|SCHEMA", LISTED);
            assertAnswer(DONE, "ALTER SCHEMA " + S + " SET DATA_RETENTION_TIME_IN_DAYS = 2, LOG_EVENT_LEVEL = 'INFO'");
            answer("SHOW PARAMETERS LIKE '%E%EL' IN SCHEMA " + S);
            assertAnswer("LOG_EVENT_LEVEL|INFO|SCHEMA ;; LOG_LEVEL|OFF| ;; METRIC_LEVEL|NONE| ;; TRACE_LEVEL|OFF|",
                LISTED);
            assertAnswer(DONE, "ALTER SCHEMA " + S + " UNSET DATA_RETENTION_TIME_IN_DAYS, LOG_EVENT_LEVEL");
            answer("SHOW PARAMETERS LIKE '%E%EL' IN SCHEMA " + S);
            assertAnswer("LOG_EVENT_LEVEL|OFF| ;; LOG_LEVEL|OFF| ;; METRIC_LEVEL|NONE| ;; TRACE_LEVEL|OFF|", LISTED);
            assertAnswer(DONE, "ALTER SCHEMA " + S
                + " SET MAX_DATA_EXTENSION_TIME_IN_DAYS = 5 DATA_RETENTION_TIME_IN_DAYS = 4");
            answer("SHOW PARAMETERS LIKE '%DAYS' IN SCHEMA " + S);
            assertAnswer("DATA_RETENTION_TIME_IN_DAYS|4|SCHEMA ;; MAX_DATA_EXTENSION_TIME_IN_DAYS|5|SCHEMA ;; "
                + "STAGE_UNDROP_RETENTION_TIME_IN_DAYS|0|", LISTED);
        } finally {
            answer("DROP SCHEMA IF EXISTS " + S);
        }
    }

    @Test
    public void commentStandsInTheListAndARepeatOrAnUnknownNameIsRefused() {
        answer("CREATE SCHEMA IF NOT EXISTS " + S);
        try {
            assertCells(new String[][] {
                {"ALTER SCHEMA " + S + " SET DATA_RETENTION_TIME_IN_DAYS = 1 DATA_RETENTION_TIME_IN_DAYS = 2",
                    "REFUSED SQL compilation error: | duplicate property 'DATA_RETENTION_TIME_IN_DAYS';"},
                {"ALTER SCHEMA " + S + " SET DATA_RETENTION_TIME_IN_DAYS = 1 COMMENT = 'c1430'", DONE},
            });
            answer("SHOW SCHEMAS LIKE 'S1430' IN DATABASE test_db");
            assertAnswer("c1430|1", "SELECT \"comment\", \"retention_time\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertCells(new String[][] {
                {"ALTER SCHEMA " + S + " SET COMMENT = 'c2' DATA_RETENTION_TIME_IN_DAYS = 6", DONE},
                {"ALTER SCHEMA " + S + " SET DATA_RETENTION_TIME_IN_DAYS = 1 FOO = 2",
                    "REFUSED SQL compilation error: | invalid property 'FOO' for 'SCHEMA'"},
                {"ALTER SCHEMA " + S + " UNSET DATA_RETENTION_TIME_IN_DAYS, COMMENT", DONE},
            });
        } finally {
            answer("DROP SCHEMA IF EXISTS " + S);
        }
    }

    @Test
    public void aDatabaseTakesAListOfProperties() {
        try {
            assertAnswer(DONE, "ALTER DATABASE test_db SET DATA_RETENTION_TIME_IN_DAYS = 3 LOG_EVENT_LEVEL = 'ERROR'");
            answer("SHOW PARAMETERS LIKE '%E%EL' IN DATABASE test_db");
            assertAnswer("LOG_EVENT_LEVEL|ERROR|DATABASE ;; LOG_LEVEL|OFF| ;; METRIC_LEVEL|NONE| ;; TRACE_LEVEL|OFF|",
                LISTED);
            assertCells(new String[][] {
                {"ALTER DATABASE test_db SET DATA_RETENTION_TIME_IN_DAYS = 2, LOG_EVENT_LEVEL = 'WARN'", DONE},
                {"ALTER DATABASE test_db UNSET DATA_RETENTION_TIME_IN_DAYS, LOG_EVENT_LEVEL", DONE},
            });
            answer("SHOW PARAMETERS LIKE '%E%EL' IN DATABASE test_db");
            assertAnswer("LOG_EVENT_LEVEL|OFF| ;; LOG_LEVEL|OFF| ;; METRIC_LEVEL|NONE| ;; TRACE_LEVEL|OFF|", LISTED);
            assertCells(new String[][] {
                {"ALTER DATABASE test_db SET COMMENT = 'd1430' DATA_RETENTION_TIME_IN_DAYS = 1", DONE},
                {"ALTER DATABASE test_db UNSET COMMENT, DATA_RETENTION_TIME_IN_DAYS", DONE},
            });
        } finally {
            answer("ALTER DATABASE test_db UNSET DATA_RETENTION_TIME_IN_DAYS");
            answer("ALTER DATABASE test_db UNSET LOG_EVENT_LEVEL");
            answer("ALTER DATABASE test_db UNSET COMMENT");
        }
    }
}
