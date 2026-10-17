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
 * A comma may stand between a statement's own properties (live-verified): a stage's options, a table's, a view's and
 * a schema's properties. Before the first property, or doubled, a comma is a syntax error at itself alone; a table
 * takes one after its last property, a stage and a schema refuse it at the end of input; one after a CLUSTER BY or
 * after WITH MANAGED ACCESS is refused at the comma. What the properties set is kept.
 */
public class PropertyListCommaTest extends MeasuredCellsTestSupport {

    @Test
    public void aCommaStandsBetweenAStatementsProperties() {
        engine.execute("CREATE OR REPLACE TABLE ct (a INT)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FILE FORMAT pf , TYPE = CSV",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ','."},
            {"CREATE OR REPLACE FILE FORMAT pf TYPE = CSV, COMMENT = 'f'", "File format PF successfully created."},
            {"CREATE OR REPLACE STAGE pc DIRECTORY = (ENABLE = TRUE) , COMMENT = 'x'",
                "Stage area PC successfully created."},
            {"CREATE OR REPLACE STAGE pc DIRECTORY = (ENABLE = TRUE), FILE_FORMAT = (TYPE = CSV)",
                "Stage area PC successfully created."},
            {"CREATE OR REPLACE STAGE pc COMMENT = 'x', DIRECTORY = (ENABLE = TRUE)",
                "Stage area PC successfully created."},
        });
        engine.execute("SHOW STAGES LIKE 'PC'");
        assertCells(new String[][] {
            {"SELECT \"name\", \"comment\", \"directory_enabled\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "PC|x|Y"},
            {"CREATE OR REPLACE STAGE pc , COMMENT = 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ','."},
            {"CREATE OR REPLACE STAGE pc COMMENT = 'x',",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '<EOF>'."},
            {"CREATE OR REPLACE STAGE pc COMMENT = 'x',, DIRECTORY = (ENABLE = TRUE)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','."},
            {"CREATE OR REPLACE STAGE pc FILE_FORMAT = (TYPE = CSV), COPY_OPTIONS = (ON_ERROR = CONTINUE), "
                + "COMMENT = 'y'",
                "Stage area PC successfully created."},
            {"CREATE OR REPLACE STAGE pc DIRECTORY = (ENABLE = TRUE), , COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ','."},
            {"CREATE OR REPLACE STAGE pc DIRECTORY = (ENABLE = TRUE) COMMENT = 'w'",
                "Stage area PC successfully created."},
            {"CREATE OR REPLACE TABLE pt (a INT) COMMENT = 'x', CHANGE_TRACKING = TRUE",
                "Table PT successfully created."},
        });
        engine.execute("SHOW TABLES LIKE 'PT'");
        assertCells(new String[][] {
            {"SELECT \"name\", \"comment\", \"change_tracking\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "PT|x|ON"},
            {"CREATE OR REPLACE TABLE pt (a INT) , COMMENT = 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ','."},
            {"CREATE OR REPLACE TABLE pt (a INT) COMMENT = 'x',", "Table PT successfully created."},
            {"CREATE OR REPLACE TABLE pt (a INT) COMMENT = 'x', , CHANGE_TRACKING = TRUE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected ','."},
            {"CREATE OR REPLACE TABLE pt (a INT) COMMENT = 'x', CHANGE_TRACKING = TRUE,",
                "Table PT successfully created."},
            {"CREATE OR REPLACE TABLE pt (a INT) CLUSTER BY (a), COMMENT = 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected ','."},
            {"CREATE OR REPLACE TABLE pt (a INT) COMMENT = 'x', AS SELECT 1", "Table PT successfully created."},
            {"CREATE OR REPLACE TABLE pt COMMENT = 'x', CHANGE_TRACKING = TRUE AS SELECT 1 a",
                "Table PT successfully created."},
            {"CREATE OR REPLACE TABLE pt COMMENT = 'x', AS SELECT 1 a", "Table PT successfully created."},
            {"CREATE OR REPLACE TABLE pt CLONE ct COMMENT = 'x', CHANGE_TRACKING = TRUE",
                "Table PT successfully created."},
            {"CREATE OR REPLACE TABLE pt LIKE ct COMMENT = 'x', CHANGE_TRACKING = TRUE",
                "Table PT successfully created."},
            {"CREATE OR REPLACE TABLE pt COMMENT = 'x', (a INT)", "Table PT successfully created."},
            {"CREATE OR REPLACE VIEW pv COMMENT = 'x', CHANGE_TRACKING = TRUE AS SELECT a FROM ct",
                "View PV successfully created."},
        });
        engine.execute("SHOW VIEWS LIKE 'PV'");
        assertCells(new String[][] {
            {"SELECT \"name\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "PV|x"},
            {"CREATE OR REPLACE VIEW pv COMMENT = 'x', AS SELECT a FROM ct", "View PV successfully created."},
            {"CREATE OR REPLACE VIEW pv , COMMENT = 'x' AS SELECT a FROM ct",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ','."},
            {"CREATE OR REPLACE VIEW pv CHANGE_TRACKING = TRUE, (b) AS SELECT a FROM ct",
                "View PV successfully created."},
            {"CREATE OR REPLACE SCHEMA psch COMMENT = 'x', DATA_RETENTION_TIME_IN_DAYS = 1",
                "Schema PSCH successfully created."},
        });
        engine.execute("SHOW SCHEMAS LIKE 'PSCH'");
        assertCells(new String[][] {
            {"SELECT \"name\", \"comment\", \"retention_time\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "PSCH|x|1"},
            {"CREATE OR REPLACE SCHEMA psch COMMENT = 'x',",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '<EOF>'."},
            {"CREATE OR REPLACE SCHEMA psch , COMMENT = 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ','."},
            {"CREATE OR REPLACE SCHEMA psch WITH MANAGED ACCESS, COMMENT = 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected ','."},
            {"CREATE OR REPLACE SCHEMA psch COMMENT = 'x', , DATA_RETENTION_TIME_IN_DAYS = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected ','."},
            {"USE SCHEMA test_db.test_schema", "Statement executed successfully."},
        });
    }
}
