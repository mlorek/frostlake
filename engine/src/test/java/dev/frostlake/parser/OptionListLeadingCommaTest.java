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
package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A comma before the first option of a parenthesized option list. A comma stands between two options only, so
 * each leading one is refused, and then the parenthesis closing the list, after which nothing more of the
 * statement is reported: {@code DIRECTORY = (, ENABLE = TRUE)} is ',' and ')'. It holds for a stage's DIRECTORY,
 * FILE_FORMAT, ENCRYPTION, COPY_OPTIONS and CREDENTIALS, a table's staging properties, ALTER STAGE and COPY
 * INTO. A list the statement never closes has no line for a parenthesis: a lone comma is followed by the end of
 * input where the text stops right after it or inside an option, and is all there is after a whole option.
 * Every answer is live-verified.
 */
public class OptionListLeadingCommaTest extends MeasuredCellsTestSupport {

    @Test
    public void aCommaBeforeTheFirstOption() {
        answer(
            "CREATE OR REPLACE TABLE ct (a INT)");
        answer(
            "CREATE OR REPLACE STAGE cs");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 55 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (, ENABLE = TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 55 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (, ENABLE = TRUE) COMMENT = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 41 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (,)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 42 unexpected ','. | syntax error line 1 at position 57 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (, , ENABLE = TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 56 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (, ENABLE = TRUE,)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (, ENABLE = TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 41 unexpected '<EOF>'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (,");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (ENABLE = TRUE,)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected ','.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (ENABLE = TRUE,, AUTO_REFRESH = FALSE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax error "
                + "line 1 at position 80 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = ( , ENABLE = TRUE , AUTO_REFRESH = FALSE )");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 52 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (, FOO = TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 52 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (, ENABLE = 5)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 48 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (, ENABLE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 55 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (, ENABLE = TRUE) FILE_FORMAT = (, TYPE = CSV)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ','. | syntax error "
                + "line 1 at position 54 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc FILE_FORMAT = (, TYPE = CSV)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ','. | syntax error "
                + "line 1 at position 54 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc FILE_FORMAT = (, TYPE = CSV) DIRECTORY = (ENABLE = TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax error "
                + "line 1 at position 65 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc ENCRYPTION = (, TYPE = 'SNOWFLAKE_SSE')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected ','. | syntax error "
                + "line 1 at position 64 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc COPY_OPTIONS = (, ON_ERROR = CONTINUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected ','. | syntax error "
                + "line 1 at position 113 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc URL = 's3://fl-probe-bucket/p/' CREDENTIALS = (, AWS_KEY_ID = 'x' "
                + "AWS_SECRET_KEY = 'y')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ','. | syntax error "
                + "line 1 at position 68 unexpected ')'.",
            "CREATE OR REPLACE TABLE pt (a INT) STAGE_FILE_FORMAT = (, TYPE = CSV)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected ','. | syntax error "
                + "line 1 at position 78 unexpected ')'.",
            "CREATE OR REPLACE TABLE pt (a INT) STAGE_COPY_OPTIONS = (, ON_ERROR = CONTINUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ','. | syntax error "
                + "line 1 at position 47 unexpected ')'.",
            "ALTER STAGE cs SET DIRECTORY = (, ENABLE = TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ','. | syntax error "
                + "line 1 at position 46 unexpected ')'.",
            "ALTER STAGE cs SET FILE_FORMAT = (, TYPE = CSV)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ','. | syntax error "
                + "line 1 at position 49 unexpected ')'.",
            "COPY INTO ct FROM @cs FILE_FORMAT = (, TYPE = CSV)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ','. | syntax error "
                + "line 1 at position 49 unexpected ')'.",
            "COPY INTO @cs FROM ct FILE_FORMAT = (, TYPE = CSV)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 81 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (ENABLE = TRUE) FILE_FORMAT = (TYPE = CSV,)");
        assertAnswer(
            "Stage area PC successfully created.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (ENABLE = TRUE, AUTO_REFRESH = FALSE)");
        assertAnswer(
            "Stage area PC successfully created.",
            "CREATE OR REPLACE STAGE pc FILE_FORMAT = (TYPE = CSV, SKIP_HEADER = 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected ','.",
            "CREATE OR REPLACE STAGE pc FILE_FORMAT = (TYPE = CSV,, SKIP_HEADER = 1)");
        assertAnswer(
            "File format PF successfully created.",
            "CREATE OR REPLACE FILE FORMAT pf TYPE = CSV, SKIP_HEADER = 1");
        answer(
            "CREATE OR REPLACE FILE FORMAT pf , TYPE = CSV");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '<EOF>'.",
            "CREATE OR REPLACE FILE FORMAT pf TYPE = CSV,");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 55 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (, ENABLE = TRUE) , COMMENT = 'x'");
        answer(
            "CREATE OR REPLACE STAGE pc DIRECTORY = (ENABLE = TRUE) , COMMENT = 'x'");
        answer(
            "CREATE OR REPLACE STAGE pc DIRECTORY = (ENABLE = TRUE), FILE_FORMAT = (TYPE = CSV)");
    }

    @Test
    public void aListLeftOpen() {
        answer(
            "CREATE OR REPLACE TABLE ct (a INT)");
        answer(
            "CREATE OR REPLACE STAGE cs");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 42 unexpected ','.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (, ,");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 76 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (, ENABLE = TRUE AUTO_REFRESH = FALSE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ','. | syntax error "
                + "line 1 at position 75 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc FILE_FORMAT = (, TYPE = CSV NULL_IF = ('a', 'b'))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ','.",
            "CREATE OR REPLACE STAGE pc FILE_FORMAT = (, TYPE = CSV NULL_IF = ('a', 'b')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (ENABLE = TRUE,)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 70 unexpected ','. | syntax error "
                + "line 1 at position 82 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (ENABLE = TRUE) FILE_FORMAT = (, TYPE = CSV) COMMENT = 'z'");
    }

    @Test
    public void otherStatementsLists() {
        answer(
            "CREATE OR REPLACE TABLE ct (a INT)");
        answer(
            "CREATE OR REPLACE STAGE cs");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 42 unexpected ','. | syntax error line 1 at position 45 unexpected '<EOF>'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (, , ,");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 48 unexpected '<EOF>'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (, ENABLE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 50 unexpected '<EOF>'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (, ENABLE =");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 42 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (, )");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 42 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (, ) FILE_FORMAT = (TYPE = CSV)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 55 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (, ENABLE = TRUE); ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected ','. | syntax error "
                + "line 1 at position 66 unexpected ')'.",
            "CREATE OR REPLACE TEMPORARY STAGE pc2 FILE_FORMAT = (, TYPE = JSON)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected ','. | syntax error "
                + "line 1 at position 59 unexpected ')'.",
            "CREATE STAGE IF NOT EXISTS pc3 FILE_FORMAT = (, TYPE = JSON)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ','. | syntax error "
                + "line 1 at position 56 unexpected ')'.",
            "ALTER STAGE cs SET COPY_OPTIONS = (, ON_ERROR = CONTINUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ','. | syntax error "
                + "line 1 at position 57 unexpected ')'.",
            "ALTER STAGE cs SET ENCRYPTION = (, TYPE = 'SNOWFLAKE_SSE')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ','. | syntax error "
                + "line 1 at position 49 unexpected ')'.",
            "COPY INTO ct FROM @cs FILE_FORMAT = (, TYPE = CSV) ON_ERROR = CONTINUE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected ','. | syntax error "
                + "line 1 at position 69 unexpected ')'.",
            "COPY INTO ct FROM @cs ON_ERROR = CONTINUE FILE_FORMAT = (, TYPE = CSV)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected ','. | syntax error "
                + "line 1 at position 66 unexpected ')'.",
            "COPY INTO ct FROM (SELECT $1 FROM @cs) FILE_FORMAT = (, TYPE = CSV)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected ','. | syntax error "
                + "line 1 at position 67 unexpected ')'.",
            "COPY INTO @cs/x FROM (SELECT a FROM ct) FILE_FORMAT = (, TYPE = CSV) OVERWRITE = TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ','. | syntax error "
                + "line 1 at position 68 unexpected ')'.",
            "CREATE OR REPLACE TABLE pt (a INT) STAGE_FILE_FORMAT = (, TYPE = CSV) COMMENT = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 70 unexpected ','. | syntax error "
                + "line 1 at position 82 unexpected ')'.",
            "CREATE OR REPLACE TABLE pt (a INT) COMMENT = 'x' STAGE_FILE_FORMAT = (, TYPE = CSV)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ','. | syntax error "
                + "line 1 at position 58 unexpected ''a''.",
            "CREATE OR REPLACE FILE FORMAT pf2 TYPE = CSV NULL_IF = (, 'a')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected ')'.",
            "CREATE OR REPLACE FILE FORMAT pf2 TYPE = CSV NULL_IF = ('a',)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected ','.",
            "CREATE OR REPLACE FILE FORMAT pf2 TYPE = CSV NULL_IF = ('a',, 'b')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected ','. | syntax error "
                + "line 1 at position 66 unexpected ''a''. | syntax error line 1 at position 70 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc FILE_FORMAT = (TYPE = CSV NULL_IF = (, 'a'))");
        answer(
            "CREATE OR REPLACE STAGE pc FILE_FORMAT = (TYPE = CSV NULL_IF = ('a',))");
    }
}
