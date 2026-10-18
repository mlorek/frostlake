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
 * A stage's COPY_OPTIONS list, of CREATE and ALTER STAGE, judges values another option list does not read
 * (live-verified): SIZE_LIMIT = NULL leaves the size unset, and NULL is an invalid value of any other option, as
 * a number with a fraction or an exponent is; a parenthesized property list is {@code invalid value
 * [TOK_PROPERTY_LIST]}, a parenthesized list of values {@code Invalid value specified for property}, FILE_FORMAT
 * is no copy option, and a list of names alone is taken and ignored. COPY_OPTIONS stays a plain name elsewhere.
 */
public class StageCopyOptionValuesTest extends MeasuredCellsTestSupport {

    @Test
    public void stageCopyOptionValuesAreJudged() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE STAGE so_s COPY_OPTIONS = (SIZE_LIMIT = NULL)",
                "Stage area SO_S successfully created."},
            {"DESCRIBE STAGE so_s ->> SELECT \"property_value\" FROM $1 WHERE \"property\" = 'SIZE_LIMIT'", ""},
            {"CREATE OR REPLACE STAGE so_s COPY_OPTIONS = (ON_ERROR = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'ON_ERROR'"},
            {"CREATE OR REPLACE STAGE so_s COPY_OPTIONS = (PURGE = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'PURGE'"},
            {"CREATE OR REPLACE STAGE so_s COPY_OPTIONS = (FORCE = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'FORCE'"},
            {"CREATE OR REPLACE STAGE so_s COPY_OPTIONS = (MATCH_BY_COLUMN_NAME = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'MATCH_BY_COLUMN_NAME'"},
            {"CREATE OR REPLACE STAGE so_s COPY_OPTIONS = (TRUNCATECOLUMNS = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'TRUNCATECOLUMNS'"},
            {"CREATE OR REPLACE STAGE so_s COPY_OPTIONS = (SIZE_LIMIT = 1.5)",
                "REFUSED SQL compilation error: | invalid value [1.5] for parameter 'SIZE_LIMIT'"},
            {"CREATE OR REPLACE STAGE so_s COPY_OPTIONS = (SIZE_LIMIT = 1e3)",
                "REFUSED SQL compilation error: | invalid value [1e3] for parameter 'SIZE_LIMIT'"},
            {"CREATE OR REPLACE STAGE so_s COPY_OPTIONS = (SIZE_LIMIT = -1.5)",
                "REFUSED SQL compilation error: | invalid value [-1.5] for parameter 'SIZE_LIMIT'"},
            {"CREATE OR REPLACE STAGE so_s COPY_OPTIONS = (INCLUDE_METADATA = (a = METADATA$FILENAME))",
                "REFUSED SQL compilation error: | invalid value [TOK_PROPERTY_LIST] for parameter 'INCLUDE_METADATA'"},
            {"CREATE OR REPLACE STAGE so_s COPY_OPTIONS = (FILE_PROCESSOR = (SCANNER = x))",
                "REFUSED SQL compilation error: | invalid value [TOK_PROPERTY_LIST] for parameter 'FILE_PROCESSOR'"},
            {"CREATE OR REPLACE STAGE so_s COPY_OPTIONS = (FILE_FORMAT = (TYPE = CSV))",
                "REFUSED SQL compilation error: invalid copy option 'FILE_FORMAT' | "},
            {"CREATE OR REPLACE STAGE so_s COPY_OPTIONS = (ON_ERROR = (CONTINUE))",
                "REFUSED Invalid value specified for property 'ON_ERROR'"},
            {"CREATE OR REPLACE STAGE so_s COPY_OPTIONS = (PURGE)", "Stage area SO_S successfully created."},
            {"CREATE OR REPLACE STAGE so_s COPY_OPTIONS = (NOPE)", "Stage area SO_S successfully created."},
            {"DESCRIBE STAGE so_s ->> SELECT \"property_value\" FROM $1 WHERE \"property\" = 'PURGE'", "false"},
            {"DESCRIBE STAGE so_s ->> SELECT \"property_value\" FROM $1 WHERE \"property\" = 'SIZE_LIMIT'", ""},
            {"CREATE OR REPLACE STAGE so_s COPY_OPTIONS = (ON_ERROR = NULL, PURGE = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'ON_ERROR'"},
            {"CREATE OR REPLACE STAGE so_s COPY_OPTIONS = (SIZE_LIMIT = NULL, PURGE = TRUE)",
                "Stage area SO_S successfully created."},
            {"DESCRIBE STAGE so_s ->> SELECT \"property_value\" FROM $1 WHERE \"property\" IN ('SIZE_LIMIT', 'PURGE') "
                + "ORDER BY 1",
                " ;; true"},
            {"CREATE OR REPLACE STAGE so_s", "Stage area SO_S successfully created."},
            {"ALTER STAGE so_s SET COPY_OPTIONS = (SIZE_LIMIT = NULL)", "Statement executed successfully."},
            {"ALTER STAGE so_s SET COPY_OPTIONS = (ON_ERROR = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'ON_ERROR'"},
            {"ALTER STAGE so_s SET COPY_OPTIONS = (SIZE_LIMIT = 1.5)",
                "REFUSED SQL compilation error: | invalid value [1.5] for parameter 'SIZE_LIMIT'"},
            {"ALTER STAGE so_s SET COPY_OPTIONS = (PURGE)", "Statement executed successfully."},
            {"ALTER STAGE so_s SET COPY_OPTIONS = (FILE_FORMAT = (TYPE = CSV))",
                "REFUSED SQL compilation error: invalid copy option 'FILE_FORMAT' | "},
            {"CREATE OR REPLACE STAGE so_s COPY_OPTIONS = (ENFORCE_LENGTH = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'ENFORCE_LENGTH'"},
            {"CREATE OR REPLACE STAGE so_s COPY_OPTIONS = (RETURN_FAILED_ONLY = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'RETURN_FAILED_ONLY'"},
            {"CREATE OR REPLACE STAGE so_s COPY_OPTIONS = (LOAD_UNCERTAIN_FILES = NULL)",
                "REFUSED SQL compilation error: invalid copy option 'LOAD_UNCERTAIN_FILES' | "},
            {"CREATE OR REPLACE STAGE so_s COPY_OPTIONS = (SIZE_LIMIT = 10.0)",
                "REFUSED SQL compilation error: | invalid value [10.0] for parameter 'SIZE_LIMIT'"},
            {"CREATE OR REPLACE STAGE so_s COPY_OPTIONS = (SIZE_LIMIT = .5)",
                "REFUSED SQL compilation error: | invalid value [.5] for parameter 'SIZE_LIMIT'"},
            {"CREATE OR REPLACE STAGE so_s COPY_OPTIONS = (SIZE_LIMIT = 'x')",
                "REFUSED SQL compilation error: | invalid value ['x'] for parameter 'SIZE_LIMIT'"},
        });
    }

    @Test
    public void copyOptionsListsAndTheWordAsAName() {
        engine.execute("CREATE OR REPLACE TABLE t_co (copy_options INT)");
        engine.execute("INSERT INTO t_co VALUES (1)");
        assertCells(new String[][] {
            {"SELECT copy_options FROM t_co", "1"},
            {"SELECT 1 AS copy_options", "1"},
            {"SELECT 1 copy_options", "1"},
            {"SELECT t.copy_options FROM t_co t", "1"},
            {"SELECT copy_options.copy_options FROM t_co copy_options", "1"},
            {"SHOW FUNCTIONS IN COPY_OPTIONS x",
                "REFUSED SQL compilation error: Object type or Class 'COPY_OPTIONS' does not exist or not authorized."},
            {"SHOW TABLES IN COPY_OPTIONS x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'."},
            {"DROP COPY_OPTIONS x",
                "REFUSED SQL compilation error: Object type or Class 'COPY_OPTIONS' does not exist or not authorized."},
            {"CREATE OR REPLACE STAGE so_s2 COPY_OPTIONS = 'x'", "Stage area SO_S2 successfully created."},
            {"CREATE OR REPLACE STAGE so_s2 \"COPY_OPTIONS\" = (PURGE = TRUE)",
                "REFUSED SQL compilation error: | invalid property '\"COPY_OPTIONS\"' for 'STAGE'"},
            {"DESCRIBE STAGE so_s2 ->> SELECT \"property\", \"property_value\" FROM $1 WHERE \"parent_property\" = "
                + "'STAGE_COPY_OPTIONS' AND \"property\" IN ('PURGE', 'SIZE_LIMIT', 'ON_ERROR') ORDER BY 1",
                "ON_ERROR|ABORT_STATEMENT ;; PURGE|false ;; SIZE_LIMIT|"},
            {"CREATE OR REPLACE STAGE so_s2 COPY_OPTIONS = (SIZE_LIMIT = NULL, SIZE_LIMIT = 5)",
                "Stage area SO_S2 successfully created."},
            {"DESCRIBE STAGE so_s2 ->> SELECT \"property\", \"property_value\" FROM $1 WHERE \"parent_property\" = "
                + "'STAGE_COPY_OPTIONS' AND \"property\" IN ('PURGE', 'SIZE_LIMIT', 'ON_ERROR') ORDER BY 1",
                "ON_ERROR|ABORT_STATEMENT ;; PURGE|false ;; SIZE_LIMIT|5"},
            {"CREATE OR REPLACE STAGE so_s2 COPY_OPTIONS = (SIZE_LIMIT = 5, SIZE_LIMIT = NULL)",
                "REFUSED SQL compilation error: conflicting values for copy option 'SIZE_LIMIT' | "},
            {"DESCRIBE STAGE so_s2 ->> SELECT \"property\", \"property_value\" FROM $1 WHERE \"parent_property\" = "
                + "'STAGE_COPY_OPTIONS' AND \"property\" IN ('PURGE', 'SIZE_LIMIT', 'ON_ERROR') ORDER BY 1",
                "ON_ERROR|ABORT_STATEMENT ;; PURGE|false ;; SIZE_LIMIT|5"},
            {"CREATE OR REPLACE STAGE so_s2 COPY_OPTIONS = (ON_ERROR = ('a'))",
                "REFUSED Invalid value specified for property 'ON_ERROR'"},
            {"CREATE OR REPLACE STAGE so_s2 COPY_OPTIONS = (PURGE = (a = 1))",
                "REFUSED SQL compilation error: | invalid value [TOK_PROPERTY_LIST] for parameter 'PURGE'"},
            {"CREATE OR REPLACE STAGE so_s2 COPY_OPTIONS = (PURGE = (TRUE))",
                "REFUSED Invalid value specified for property 'PURGE'"},
            {"CREATE OR REPLACE STAGE so_s2 COPY_OPTIONS = (INCLUDE_METADATA = NULL)",
                "Stage area SO_S2 successfully created."},
            {"CREATE OR REPLACE STAGE so_s2 COPY_OPTIONS = (SIZE_LIMIT = null)",
                "Stage area SO_S2 successfully created."},
            {"DESCRIBE STAGE so_s2 ->> SELECT \"property\", \"property_value\" FROM $1 WHERE \"parent_property\" = "
                + "'STAGE_COPY_OPTIONS' AND \"property\" IN ('PURGE', 'SIZE_LIMIT', 'ON_ERROR') ORDER BY 1",
                "ON_ERROR|ABORT_STATEMENT ;; PURGE|false ;; SIZE_LIMIT|"},
            {"CREATE OR REPLACE STAGE so_s2 COPY_OPTIONS = (PURGE = 1.5)",
                "REFUSED SQL compilation error: | invalid value [1.5] for parameter 'PURGE'"},
            {"CREATE OR REPLACE STAGE so_s2 COPY_OPTIONS = ()", "Stage area SO_S2 successfully created."},
            {"DESCRIBE STAGE so_s2 ->> SELECT \"property\", \"property_value\" FROM $1 WHERE \"parent_property\" = "
                + "'STAGE_COPY_OPTIONS' AND \"property\" IN ('PURGE', 'SIZE_LIMIT', 'ON_ERROR') ORDER BY 1",
                "ON_ERROR|ABORT_STATEMENT ;; PURGE|false ;; SIZE_LIMIT|"},
            {"CREATE OR REPLACE STAGE so_s2 COPY_OPTIONS = (\"PURGE\")", "Stage area SO_S2 successfully created."},
            {"CREATE OR REPLACE STAGE so_s2 COPY_OPTIONS = (PURGE = TRUE ON_ERROR = (CONTINUE))",
                "REFUSED Invalid value specified for property 'ON_ERROR'"},
            {"CREATE OR REPLACE STAGE so_s2 COPY_OPTIONS = (ON_ERROR = NULL SIZE_LIMIT = 1.5)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'ON_ERROR'"},
            {"CREATE OR REPLACE STAGE so_s2 COPY_OPTIONS = (NOPE = NULL)",
                "REFUSED SQL compilation error: invalid copy option 'NOPE' | "},
            {"CREATE OR REPLACE STAGE so_s2 COPY_OPTIONS = (SIZE_LIMIT = 1e400)",
                "REFUSED SQL compilation error: | invalid value [1e400] for parameter 'SIZE_LIMIT'"},
            {"ALTER STAGE so_s2 SET COPY_OPTIONS = (SIZE_LIMIT = 7)", "Statement executed successfully."},
            {"DESCRIBE STAGE so_s2 ->> SELECT \"property\", \"property_value\" FROM $1 WHERE \"parent_property\" = "
                + "'STAGE_COPY_OPTIONS' AND \"property\" IN ('PURGE', 'SIZE_LIMIT', 'ON_ERROR') ORDER BY 1",
                "ON_ERROR|ABORT_STATEMENT ;; PURGE|false ;; SIZE_LIMIT|7"},
            {"ALTER STAGE so_s2 SET COPY_OPTIONS = (PURGE)", "Statement executed successfully."},
            {"DESCRIBE STAGE so_s2 ->> SELECT \"property\", \"property_value\" FROM $1 WHERE \"parent_property\" = "
                + "'STAGE_COPY_OPTIONS' AND \"property\" IN ('PURGE', 'SIZE_LIMIT', 'ON_ERROR') ORDER BY 1",
                "ON_ERROR|ABORT_STATEMENT ;; PURGE|false ;; SIZE_LIMIT|"},
        });
    }
}
