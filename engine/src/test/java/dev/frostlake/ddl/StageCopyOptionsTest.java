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
 * A stage's copy options are judged one after another as written: a name a stage does not take, a value its option
 * does not take, and an option given again with another value are refused. ALTER STAGE … SET COPY_OPTIONS replaces
 * the stage's whole set, ALTER TABLE … SET STAGE_COPY_OPTIONS merges into the table's, a refused CREATE OR REPLACE
 * leaves the stage it would replace, and DESC lists each value as the account spells it (live-verified).
 */
public class StageCopyOptionsTest extends MeasuredCellsTestSupport {

    private static final String INVALID = "REFUSED SQL compilation error: invalid copy option '";

    private static final String S = "CREATE OR REPLACE STAGE sco1437 COPY_OPTIONS = ";

    private static final String VALUE = "SELECT \"property_value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"
        + " WHERE \"parent_property\" = 'STAGE_COPY_OPTIONS' AND \"property\" = 'ON_ERROR'";

    private static final String COPY = "SELECT \"property\", \"property_value\""
        + " FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"parent_property\" = 'STAGE_COPY_OPTIONS'";

    private static final String DEFAULTS = "RETURN_FAILED_ONLY|false ;; ENFORCE_LENGTH|true ;; TRUNCATECOLUMNS|false";

    private static String invalidValue(final String written, final String parameter) {
        return "REFUSED SQL compilation error: | invalid value [" + written + "] for parameter '" + parameter + "'";
    }

    @Test
    public void aNameOrAValueTheStageDoesNotTakeIsRefused() {
        assertCells(new String[][] {
            {S + "(NOPE = 1)", INVALID + "NOPE' | "},
            {S + "(nope = 1)", INVALID + "nope' | "},
            {S + "(\"on_error\" = CONTINUE)", INVALID + "\"on_error\"' | "},
            {S + "(VALIDATION_MODE = RETURN_ERRORS)", INVALID + "VALIDATION_MODE' | "},
            {S + "(SKIP_HEADER = 1)", INVALID + "SKIP_HEADER' | "},
            {S + "(ON_ERROR = 'NOPE')", invalidValue("'NOPE'", "ON_ERROR")},
            {S + "(ON_ERROR = SKIP_FILE_X)", invalidValue("SKIP_FILE_X", "ON_ERROR")},
            {S + "(ON_ERROR = 'SKIP_FILE_101%')", invalidValue("'SKIP_FILE_101%'", "ON_ERROR")},
            {S + "(ON_ERROR = TRUE)", invalidValue("TRUE", "ON_ERROR")},
            {S + "(SIZE_LIMIT = '100')", invalidValue("'100'", "SIZE_LIMIT")},
            {S + "(SIZE_LIMIT = -1)", invalidValue("-1", "SIZE_LIMIT")},
            {S + "(SIZE_LIMIT = 99999999999999999999)", invalidValue("99999999999999999999", "SIZE_LIMIT")},
            {S + "(PURGE = 'TRUE')", invalidValue("'TRUE'", "PURGE")},
            {S + "(RETURN_FAILED_ONLY = 1)", invalidValue("1", "RETURN_FAILED_ONLY")},
            {S + "(MATCH_BY_COLUMN_NAME = TRUE)", invalidValue("TRUE", "MATCH_BY_COLUMN_NAME")},
            {S + "(LOAD_MODE = NOPE)", invalidValue("NOPE", "LOAD_MODE")},
            {S + "(PURGE = TRUE purge = FALSE)", "REFUSED SQL compilation error: conflicting values for copy option "
                + "'PURGE' | "},
            {S + "(ON_ERROR = continue ON_ERROR = CONTINUE)", "REFUSED SQL compilation error: conflicting values for "
                + "copy option 'ON_ERROR' | "},
            {S + "(NOPE = 1 PURGE = 'x')", INVALID + "NOPE' | "},
            {S + "(PURGE = 'x' NOPE = 1)", invalidValue("'x'", "PURGE")},
            {S + "(NOPE = 1) FILE_FORMAT = (TYPE = NOPE)", INVALID + "NOPE' | "},
            {"CREATE OR REPLACE STAGE sco1437 FILE_FORMAT = (TYPE = NOPE) COPY_OPTIONS = (NOPE = 1)",
                invalidValue("NOPE", "TYPE")},
            {S + "(ON_ERROR = CONTINUE ON_ERROR = CONTINUE SIZE_LIMIT = 9223372036854775807)",
                "Stage area SCO1437 successfully created."},
            {S + "(ON_ERROR = 'SKIP_FILE_100%' LOAD_MODE = 'FULL_INGEST' MATCH_BY_COLUMN_NAME = NONE)",
                "Stage area SCO1437 successfully created."},
        });
    }

    @Test
    public void eachValueIsListedAsTheAccountSpellsIt() {
        final String[][] spelled = {
            {"(ON_ERROR = continue)", "continue"},
            {"(ON_ERROR = skip_file)", "SKIP_FILE"},
            {"(ON_ERROR = skip_file_3)", "SKIP_FILE_3"},
            {"(ON_ERROR = 'skip_file')", "skip_file"},
            {"(ON_ERROR = 'skip_file_10%')", "skip_file_10%"},
            {"(ON_ERROR = 'SKIP_FILE_10.5%')", "SKIP_FILE_10.5%"},
            {"(ON_ERROR = 'SKIP_FILE_10')", "SKIP_FILE_10"},
        };
        for (final String[] cell : spelled) {
            answer(S + cell[0]);
            answer("DESC STAGE sco1437");
            assertAnswer(cell[1], VALUE);
        }
        answer(S + "(MATCH_BY_COLUMN_NAME = CASE_INSENSITIVE LOAD_MODE = FULL_INGEST)");
        answer("DESC STAGE sco1437");
        assertAnswer("ON_ERROR|String|ABORT_STATEMENT|ABORT_STATEMENT ;; SIZE_LIMIT|Long|| ;; "
            + "PURGE|Boolean|false|false ;; RETURN_FAILED_ONLY|Boolean|false|false ;; "
            + "ENFORCE_LENGTH|Boolean|true|true ;; TRUNCATECOLUMNS|Boolean|false|false ;; FORCE|Boolean|false|false ;; "
            + "MATCH_BY_COLUMN_NAME|String|CASE_INSENSITIVE|NONE ;; LOAD_MODE|String|FULL_INGEST|FULL_INGEST",
            "SELECT \"property\", \"property_type\", \"property_value\", \"property_default\""
                + " FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"parent_property\" = 'STAGE_COPY_OPTIONS'");
        answer(S + "(LOAD_MODE = add_files_copy MATCH_BY_COLUMN_NAME = 'case_insensitive' SIZE_LIMIT = 0"
            + " PURGE = true)");
        answer("DESC STAGE sco1437");
        assertAnswer("ON_ERROR|ABORT_STATEMENT ;; SIZE_LIMIT|0 ;; PURGE|true ;; " + DEFAULTS + " ;; FORCE|false ;; "
            + "MATCH_BY_COLUMN_NAME|case_insensitive ;; LOAD_MODE|ADD_FILES_COPY", COPY);
    }

    @Test
    public void anAlterReplacesTheStagesSetAndATablesMerges() {
        answer("CREATE OR REPLACE STAGE sca1437 COPY_OPTIONS = (SIZE_LIMIT = 10 PURGE = TRUE)");
        answer("ALTER STAGE sca1437 SET COPY_OPTIONS = (FORCE = TRUE)");
        answer("DESC STAGE sca1437");
        assertAnswer("ON_ERROR|ABORT_STATEMENT ;; SIZE_LIMIT| ;; PURGE|false ;; " + DEFAULTS + " ;; FORCE|true", COPY);
        assertCells(new String[][] {
            {"ALTER STAGE sca1437 SET COPY_OPTIONS = (PURGE = TRUE)", "Statement executed successfully."},
            {"ALTER STAGE sca1437 SET COPY_OPTIONS = (FORCE = TRUE NOPE = 1)", INVALID + "NOPE' | "},
            {"ALTER STAGE sca1437 SET COPY_OPTIONS = (ON_ERROR = 'NOPE')", invalidValue("'NOPE'", "ON_ERROR")},
            {"ALTER STAGE sca1437 SET COMMENT = 'd'", "Statement executed successfully."},
            {"CREATE OR REPLACE STAGE sca1437 COPY_OPTIONS = (SIZE_LIMIT = '10')", invalidValue("'10'", "SIZE_LIMIT")},
        });
        answer("DESC STAGE sca1437");
        assertAnswer("ON_ERROR|ABORT_STATEMENT ;; SIZE_LIMIT| ;; PURGE|true ;; " + DEFAULTS + " ;; FORCE|false", COPY);
        answer("ALTER STAGE sca1437 SET COPY_OPTIONS = ()");
        answer("DESC STAGE sca1437");
        assertAnswer("ON_ERROR|ABORT_STATEMENT ;; SIZE_LIMIT| ;; PURGE|false ;; " + DEFAULTS + " ;; FORCE|false", COPY);

        answer("CREATE OR REPLACE TABLE tco1437 (a INT) STAGE_COPY_OPTIONS = (ON_ERROR = continue PURGE = TRUE)");
        answer("ALTER TABLE tco1437 SET STAGE_COPY_OPTIONS = (FORCE = TRUE)");
        answer("DESC TABLE tco1437 TYPE = STAGE");
        assertAnswer("ON_ERROR|continue ;; SIZE_LIMIT| ;; PURGE|true ;; " + DEFAULTS + " ;; FORCE|true", COPY);
        assertCells(new String[][] {
            {"ALTER TABLE tco1437 SET STAGE_COPY_OPTIONS = (SIZE_LIMIT = 'x')", invalidValue("'x'", "SIZE_LIMIT")},
            {"ALTER TABLE tco1437 SET STAGE_COPY_OPTIONS = (ON_ERROR = CONTINUE ON_ERROR = SKIP_FILE)",
                "REFUSED SQL compilation error: conflicting values for copy option 'ON_ERROR' | "},
            {"CREATE OR REPLACE TABLE tco1437 (a INT) STAGE_COPY_OPTIONS = (NOPE = 1)", INVALID + "NOPE' | "},
            {"CREATE OR REPLACE TABLE tco1437 (a INT) STAGE_COPY_OPTIONS = (ON_ERROR = 'NOPE')",
                invalidValue("'NOPE'", "ON_ERROR")},
        });
    }
}
