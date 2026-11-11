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
 * An event table judges its properties as a table does, refusing a name it does not know as a table's, but in its own
 * words where its kind differs: ROW_TIMESTAMP and ERROR_LOGGING are no event table's properties,
 * ENABLE_SCHEMA_EVOLUTION and BASE_LOCATION unsupported features, a parameter is refused at 'EVENT_TABLE' and the
 * log and trace levels at 'TABLE', and a catalog is read as an integration's name. Its properties may come before a
 * CLONE's source, never after it, and a clustering key reads its own columns. Every answer is live-verified.
 */
public class EventTablePropertiesTest extends MeasuredCellsTestSupport {

    private static final String EVENT = "CREATE OR REPLACE EVENT TABLE et972 ";
    private static final String MADE = "Table ET972 successfully created.";

    @Test
    public void propertiesAreJudgedAsATablesInTheEventTablesWords() {
        assertCells(new String[][] {
            {EVENT + "COMMENT = 'c'", MADE},
            {EVENT + "CHANGE_TRACKING = TRUE", MADE},
            {EVENT + "STAGE_FILE_FORMAT = (TYPE = CSV)", MADE},
            {EVENT + "STAGE_COPY_OPTIONS = (ON_ERROR = CONTINUE)", MADE},
            {EVENT + "COPY GRANTS", MADE},
            {EVENT + "x = 1", "REFUSED SQL compilation error: | invalid property 'x' for 'TABLE'"},
            {EVENT + "x = (1)", "REFUSED SQL compilation error: | invalid property 'x' for 'TABLE'"},
            {EVENT + "x = 1 ROW_TIMESTAMP = TRUE",
                "REFUSED SQL compilation error: | invalid property 'x' for 'TABLE'"},
            {EVENT + "COMMENT = 'c' COMMENT = 'd'", "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {EVENT + "DATA_RETENTION_TIME_IN_DAYS = 'x'",
                "REFUSED SQL compilation error: | invalid value ['x'] for parameter 'DATA_RETENTION_TIME_IN_DAYS'"},
            {EVENT + "CHANGE_TRACKING = 'x'",
                "REFUSED SQL compilation error: | invalid value ['x'] for parameter 'CHANGE_TRACKING'"},
            {EVENT + "ROW_TIMESTAMP = 'x'",
                "REFUSED SQL compilation error: | invalid value ['x'] for parameter 'ROW_TIMESTAMP'"},
            {EVENT + "ROW_TIMESTAMP = TRUE",
                "REFUSED SQL compilation error: | invalid property 'ROW_TIMESTAMP' for 'EVENT_TABLE'"},
            {EVENT + "ERROR_LOGGING = TRUE",
                "REFUSED SQL compilation error: | invalid property 'ERROR_LOGGING' for 'EVENT_TABLE'"},
            {EVENT + "ENABLE_SCHEMA_EVOLUTION = TRUE", "REFUSED Unsupported feature 'ENABLE_SCHEMA_EVOLUTION'."},
            {EVENT + "BASE_LOCATION = 'x'", "REFUSED Unsupported feature 'BASE_LOCATION'."},
            {EVENT + "TIMEZONE = 'UTC'",
                "REFUSED SQL compilation error: | cannot set parameter 'TIMEZONE' at 'EVENT_TABLE'"},
            {EVENT + "LOG_LEVEL = 'INFO'",
                "REFUSED SQL compilation error: | cannot set parameter 'LOG_LEVEL' at 'TABLE'"},
            {EVENT + "CATALOG = 'x'",
                "REFUSED Catalog integration x is not valid. Please ensure required properties are valid."},
        });
    }

    @Test
    public void aCloneTakesPropertiesBeforeItsSourceAndAKeyReadsTheEventTablesColumns() {
        engine.execute("CREATE OR REPLACE EVENT TABLE es972 COMMENT = 'src'");
        engine.execute("CREATE OR REPLACE TABLE pt972 (a INT)");
        assertCells(new String[][] {
            {EVENT + "COMMENT = 'c' CLONE es972", MADE},
            {"SELECT COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'TEST_SCHEMA' "
                + "AND TABLE_NAME = 'ET972'", "c"},
            {EVENT + "DEFAULT = 1 CLONE pt972",
                "REFUSED SQL compilation error: | invalid property 'DEFAULT' for 'TABLE'"},
            {EVENT + "CLONE pt972 x = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'x'."},
            {EVENT + "(a INT)", "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '('."},
            {EVENT + "CLUSTER BY (timestamp)", MADE},
            {EVENT + "CLUSTER BY (nosuch)",
                "REFUSED SQL compilation error: error line 1 at position 48 | invalid identifier 'NOSUCH'"},
            {EVENT + "CLUSTER BY (a)",
                "REFUSED SQL compilation error: error line 1 at position 48 | invalid identifier 'A'"},
        });
    }
}
