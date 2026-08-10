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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The COPY INTO &lt;table&gt; load-option surface, measured cell by cell on a real account over an
 * empty internal stage: every documented ON_ERROR spelling and VALIDATION_MODE mode compiles; a bad
 * option value refuses with the invalid-value shape in one of its three renderings (a quoted string
 * keeps its quotes, a bareword and a negative integer echo bare); an unknown option is an
 * {@code invalid parameter}; a repeated option — and the inverse pair ENFORCE_LENGTH /
 * TRUNCATECOLUMNS at one polarity — is a conflict, whose message drops the prefix newline and adds
 * a trailing one; MATCH_BY_COLUMN_NAME over headerless CSV and VALIDATION_MODE over a transform
 * refuse with their own single-line sentences; and a FILES entry the stage does not hold aborts
 * naming the location exactly as the statement wrote it, PATTERN notwithstanding.
 */
public class CopyOptionSurfaceTest extends BaseDatabaseTest {

    @BeforeEach
    public void createFixtures() {
        engine.execute("CREATE OR REPLACE STAGE copt_st");
        engine.execute("CREATE OR REPLACE TABLE copt_t (a INTEGER, b VARCHAR)");
    }

    private RuntimeException refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
    }

    @Test
    public void everyDocumentedOnErrorSpellingCompiles() {
        engine.execute("COPY INTO copt_t FROM @copt_st ON_ERROR = CONTINUE");
        engine.execute("COPY INTO copt_t FROM @copt_st ON_ERROR = 'SKIP_FILE'");
        engine.execute("COPY INTO copt_t FROM @copt_st ON_ERROR = SKIP_FILE_3");
        engine.execute("COPY INTO copt_t FROM @copt_st ON_ERROR = 'SKIP_FILE_3%'");
        engine.execute("COPY INTO copt_t FROM @copt_st ON_ERROR = ABORT_STATEMENT");
    }

    @Test
    public void booleanOptionsAndSizeLimitCompile() {
        engine.execute("COPY INTO copt_t FROM @copt_st ENFORCE_LENGTH = FALSE TRUNCATECOLUMNS = TRUE");
        engine.execute("COPY INTO copt_t FROM @copt_st LOAD_UNCERTAIN_FILES = TRUE");
        engine.execute("COPY INTO copt_t FROM @copt_st RETURN_FAILED_ONLY = TRUE");
        engine.execute("COPY INTO copt_t FROM @copt_st SIZE_LIMIT = 0");
    }

    @Test
    public void validationModeModesCompileOverAnEmptyStage() {
        engine.executeQuery("COPY INTO copt_t FROM @copt_st VALIDATION_MODE = RETURN_2_ROWS");
        engine.executeQuery("COPY INTO copt_t FROM @copt_st VALIDATION_MODE = RETURN_ALL_ERRORS");
    }

    @Test
    public void badValuesRefuseWithTheirMeasuredRenderings() {
        // A quoted string echoes WITH its quotes…
        assertEquals("SQL compilation error:\ninvalid value ['BOGUS'] for parameter 'ON_ERROR'",
            refusal("COPY INTO copt_t FROM @copt_st ON_ERROR = 'BOGUS'").getMessage());
        assertEquals("SQL compilation error:\ninvalid value ['WRONG'] for parameter"
                + " 'MATCH_BY_COLUMN_NAME'",
            refusal("COPY INTO copt_t FROM @copt_st MATCH_BY_COLUMN_NAME = 'WRONG'").getMessage());
        assertEquals("SQL compilation error:\ninvalid value ['WRONG_MODE'] for parameter"
                + " 'VALIDATION_MODE'",
            refusal("COPY INTO copt_t FROM @copt_st VALIDATION_MODE = 'WRONG_MODE'").getMessage());
        assertEquals("SQL compilation error:\ninvalid value ['TRUE'] for parameter 'FORCE'",
            refusal("COPY INTO copt_t FROM @copt_st FORCE = 'TRUE'").getMessage());
        // …while a negative integer and a bareword echo bare.
        assertEquals("SQL compilation error:\ninvalid value [-5] for parameter 'SIZE_LIMIT'",
            refusal("COPY INTO copt_t FROM @copt_st SIZE_LIMIT = -5").getMessage());
        assertEquals("SQL compilation error:\ninvalid value [MAYBE] for parameter 'PURGE'",
            refusal("COPY INTO copt_t FROM @copt_st PURGE = MAYBE").getMessage());
    }

    @Test
    public void unknownOptionRefusesAsInvalidParameter() {
        assertEquals("SQL compilation error:\ninvalid parameter 'NO_SUCH_COPY_OPT'",
            refusal("COPY INTO copt_t FROM @copt_st NO_SUCH_COPY_OPT = 1").getMessage());
    }

    @Test
    public void repeatedAndInverseOptionsConflict() {
        // The conflict sentence sits on the prefix LINE (no newline before it) and ends with one.
        assertEquals("SQL compilation error: conflicting values for copy option 'ON_ERROR'\n",
            refusal("COPY INTO copt_t FROM @copt_st ON_ERROR = CONTINUE ON_ERROR = SKIP_FILE")
                .getMessage());
        // ENFORCE_LENGTH and TRUNCATECOLUMNS are one option worn two ways: both TRUE conflicts,
        // and the message blames TRUNCATECOLUMNS.
        assertEquals("SQL compilation error: conflicting values for copy option 'TRUNCATECOLUMNS'\n",
            refusal("COPY INTO copt_t FROM @copt_st ENFORCE_LENGTH = TRUE TRUNCATECOLUMNS = TRUE")
                .getMessage());
    }

    @Test
    public void matchByColumnNameNeedsAParsedCsvHeader() {
        assertEquals("SQL compilation error: match_by_column_name option is not supported for file"
                + " format CSV without PARSE_HEADER = TRUE",
            refusal("COPY INTO copt_t FROM @copt_st MATCH_BY_COLUMN_NAME = CASE_SENSITIVE")
                .getMessage());
    }

    @Test
    public void validationModeRefusesATransformSource() {
        assertEquals("SQL compilation error:\nVALIDATION_MODE does not support COPY with transform.",
            refusal("COPY INTO copt_t FROM (SELECT $1, $2 FROM @copt_st)"
                + " VALIDATION_MODE = RETURN_ERRORS").getMessage());
    }

    @Test
    public void missingNamedFileAbortsEchoingTheLocationAsWritten() {
        // FILES beats PATTERN: the pattern matches nothing, yet the named file's absence is what
        // aborts — and the stage reference keeps the statement's own lower-case spelling.
        final String message = refusal(
            "COPY INTO copt_t FROM @copt_st PATTERN = '.*' FILES = ('x.csv')").getMessage();
        assertTrue(message.startsWith("Remote file '@copt_st/x.csv' was not found."), message);
    }
}
