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

package dev.frostlake.dml;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One FILE_FORMAT model whatever statement writes or reads it (live-verified): a stage's FILE_FORMAT = (…) is judged
 * as CREATE FILE FORMAT judges its options - an unknown parameter, an option outside the type's tree, a value outside
 * its domain and a parameter given twice are refused in the same words; a string's value is decoded, so a stage's
 * FIELD_DELIMITER = '\t' splits on a tab as a named format's does; DESCRIBE spells a tab '\t' and SHOW FILE FORMATS'
 * blob spells it as JSON does; a stage's COPY_OPTIONS take ON_ERROR in COPY's domain; and a staged-file query reads
 * TRIM_SPACE, SKIP_BLANK_LINES and NULL_IF holding a comma as a load reads them.
 */
public class FileFormatOptionModelTest extends MeasuredCellsTestSupport {

    private Path dir;

    @Override
    protected void teardownTest() {
        if (dir != null) {
            final File[] files = dir.toFile().listFiles();
            for (final File file : files != null ? files : new File[0]) {
                if (!file.delete()) {
                    file.deleteOnExit();
                }
            }
            if (!dir.toFile().delete()) {
                dir.toFile().deleteOnExit();
            }
        }
    }

    /** Writes a file under the test's directory and PUTs it, uncompressed, onto a stage. */
    private void put(final String name, final String content, final String stage) {
        try {
            if (dir == null) {
                dir = Files.createTempDirectory("fl_ff_");
            }
            final Path file = dir.resolve(name);
            Files.write(file, content.getBytes(StandardCharsets.UTF_8));
            engine.execute("PUT 'file://" + file.toAbsolutePath() + "' @" + stage + " AUTO_COMPRESS=FALSE");
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void aStagesFormatListIsJudgedAsACreateFileFormatIs() {
        assertAnswer("REFUSED SQL compilation error: | invalid parameter 'BOGUS_OPT'",
            "CREATE OR REPLACE FILE FORMAT ffv1 TYPE = CSV BOGUS_OPT = 1");
        assertAnswer("REFUSED SQL compilation error: | Option FIELD_DELIMITER is not valid for file format type JSON.",
            "CREATE OR REPLACE FILE FORMAT ffv2 TYPE = JSON FIELD_DELIMITER = '|'");
        assertAnswer("REFUSED SQL compilation error: | invalid value [-1] for parameter 'SKIP_HEADER'",
            "CREATE OR REPLACE FILE FORMAT ffv3 TYPE = CSV SKIP_HEADER = -1");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['yes'] for parameter 'TRIM_SPACE'",
            "CREATE OR REPLACE FILE FORMAT ffv4 TYPE = CSV TRIM_SPACE = 'yes'");
        assertAnswer("REFUSED SQL compilation error: | invalid parameter 'BOGUS_OPT'",
            "CREATE OR REPLACE STAGE fsv1 FILE_FORMAT = (TYPE = CSV BOGUS_OPT = 1)");
        assertAnswer("REFUSED SQL compilation error: | Option FIELD_DELIMITER is not valid for file format type JSON.",
            "CREATE OR REPLACE STAGE fsv2 FILE_FORMAT = (TYPE = JSON FIELD_DELIMITER = '|')");
        assertAnswer("REFUSED SQL compilation error: | invalid value [-1] for parameter 'SKIP_HEADER'",
            "CREATE OR REPLACE STAGE fsv3 FILE_FORMAT = (TYPE = CSV SKIP_HEADER = -1)");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['yes'] for parameter 'TRIM_SPACE'",
            "CREATE OR REPLACE STAGE fsv4 FILE_FORMAT = (TYPE = CSV TRIM_SPACE = 'yes')");
        assertAnswer("REFUSED SQL compilation error: conflicting values file format parameter 'FIELD_DELIMITER'",
            "CREATE OR REPLACE STAGE fsv5 FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|' FIELD_DELIMITER = ',')");
    }

    @Test
    public void aTabInADelimiterIsKeptDecodedAndSpelledByEachListing() {
        assertAnswer("File format FFTAB successfully created.",
            "CREATE OR REPLACE FILE FORMAT fftab TYPE = CSV FIELD_DELIMITER = '\\t'");
        final String described = answer("DESCRIBE FILE FORMAT fftab");
        assertTrue(described.contains("FIELD_DELIMITER|String|\\t|,"), described);
        assertTrue(described.contains("RECORD_DELIMITER|String|\\n|\\n"), described);
        assertTrue(described.contains("ESCAPE_UNENCLOSED_FIELD|String|\\\\|\\\\"), described);
        assertTrue(described.contains("NULL_IF|List|[\\\\N]|[\\\\N]"), described);
        final String shown = answer("SHOW FILE FORMATS LIKE 'FFTAB'");
        assertTrue(shown.contains("\"RECORD_DELIMITER\":\"\\n\",\"FIELD_DELIMITER\":\"\\t\""), shown);
        assertTrue(shown.contains("\"ESCAPE_UNENCLOSED_FIELD\":\"\\\\\""), shown);
        assertTrue(shown.contains("\"NULL_IF\":[\"\\\\N\"]"), shown);
    }

    @Test
    public void aStagesOnErrorTakesWhatACopyTakes() {
        assertAnswer("Stage area FSO1 successfully created.",
            "CREATE OR REPLACE STAGE fso1 COPY_OPTIONS = (ON_ERROR = 'SKIP_FILE_12.5%')");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['SKIP_FILE_150%'] for parameter 'ON_ERROR'",
            "CREATE OR REPLACE STAGE fso2 COPY_OPTIONS = (ON_ERROR = 'SKIP_FILE_150%')");
        assertAnswer("Stage area FSO3 successfully created.",
            "CREATE OR REPLACE STAGE fso3 COPY_OPTIONS = (ON_ERROR = 'SKIP_FILE_10%')");
        assertAnswer("Stage area FSO4 successfully created.",
            "CREATE OR REPLACE STAGE fso4 COPY_OPTIONS = (ON_ERROR = 'SKIP_FILE_3')");
        assertAnswer("Stage area FSO5 successfully created.",
            "CREATE OR REPLACE STAGE fso5 COPY_OPTIONS = (ON_ERROR = 'SKIP_FILE_0%')");
        assertAnswer("Stage area FSO6 successfully created.",
            "CREATE OR REPLACE STAGE fso6 COPY_OPTIONS = (ON_ERROR = 'SKIP_FILE_100%')");
        assertAnswer("Stage area FSO7 successfully created.",
            "CREATE OR REPLACE STAGE fso7 COPY_OPTIONS = (ON_ERROR = 'skip_file_5%')");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['SKIP_FILE_-1'] for parameter 'ON_ERROR'",
            "CREATE OR REPLACE STAGE fso8 COPY_OPTIONS = (ON_ERROR = 'SKIP_FILE_-1')");
        assertAnswer("Stage area FSO9 successfully created.",
            "CREATE OR REPLACE STAGE fso9 COPY_OPTIONS = (ON_ERROR = SKIP_FILE_7)");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['SKIP_FILE_'] for parameter 'ON_ERROR'",
            "CREATE OR REPLACE STAGE fso10 COPY_OPTIONS = (ON_ERROR = 'SKIP_FILE_')");
        final String share = answer("DESCRIBE STAGE fso3");
        assertTrue(share.contains("STAGE_COPY_OPTIONS|ON_ERROR|String|SKIP_FILE_10%|ABORT_STATEMENT"), share);
        final String count = answer("DESCRIBE STAGE fso4");
        assertTrue(count.contains("STAGE_COPY_OPTIONS|ON_ERROR|String|SKIP_FILE_3|ABORT_STATEMENT"), count);
    }

    @Test
    public void aStagedFileQueryReadsTheFormatAsALoadDoes() {
        engine.execute("CREATE OR REPLACE STAGE fst ENCRYPTION = (TYPE = 'SNOWFLAKE_SSE')");
        engine.execute("CREATE OR REPLACE STAGE fst2 ENCRYPTION = (TYPE = 'SNOWFLAKE_SSE')"
            + " FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '\\t')");
        put("blank.csv", "a,b\n\nc,d\n", "fst");
        put("trim.csv", " a , b \n", "fst");
        put("encl.csv", "\"a\"x,b\n", "fst");
        put("na.csv", "NA,1,2\n", "fst");
        put("tab.tsv", "1\t2\n", "fst");
        put("tab.tsv", "1\t2\n", "fst2");
        engine.execute("CREATE OR REPLACE FILE FORMAT fftr TYPE = CSV TRIM_SPACE = TRUE");
        engine.execute("CREATE OR REPLACE FILE FORMAT ffsb TYPE = CSV SKIP_BLANK_LINES = TRUE");
        engine.execute("CREATE OR REPLACE FILE FORMAT ffen TYPE = CSV FIELD_OPTIONALLY_ENCLOSED_BY = '\"'");
        engine.execute("CREATE OR REPLACE FILE FORMAT ffn1 TYPE = CSV NULL_IF = ('NA')");
        engine.execute("CREATE OR REPLACE FILE FORMAT ffn2 TYPE = CSV NULL_IF = ('NA', 'x,y')");
        engine.execute("CREATE OR REPLACE FILE FORMAT fftab TYPE = CSV FIELD_DELIMITER = '\\t'");
        assertAnswer(" a | b ", "SELECT $1, $2 FROM @fst/trim.csv");
        assertAnswer("a|b", "SELECT $1, $2 FROM @fst/trim.csv (FILE_FORMAT => 'fftr')");
        assertAnswer("3", "SELECT COUNT(*) FROM @fst/blank.csv");
        assertAnswer("2", "SELECT COUNT(*) FROM @fst/blank.csv (FILE_FORMAT => 'ffsb')");
        assertAnswer("a|b ;; c|d ;; null|null", "SELECT $1, $2 FROM @fst/blank.csv ORDER BY 1");
        assertAnswer("REFUSED Found character 'x' instead of field delimiter ',' |   File 'encl.csv', line 1,"
            + " character 4 |   Row 1, column \"TRANSIENT_STAGE_TABLE\"[\"$1\":1]",
            "SELECT $1, $2 FROM @fst/encl.csv (FILE_FORMAT => 'ffen')");
        assertAnswer("\"a\"x|b", "SELECT $1, $2 FROM @fst/encl.csv");
        assertAnswer("true|1|2", "SELECT $1 IS NULL, $2, $3 FROM @fst/na.csv (FILE_FORMAT => 'ffn1')");
        assertAnswer("true|1|2", "SELECT $1 IS NULL, $2, $3 FROM @fst/na.csv (FILE_FORMAT => 'ffn2')");
        assertAnswer("c1|TEXT ;; c2|NUMBER(1, 0) ;; c3|NUMBER(1, 0)", "SELECT COLUMN_NAME, TYPE FROM TABLE("
            + "INFER_SCHEMA(LOCATION => '@fst/na.csv', FILE_FORMAT => 'ffn1')) ORDER BY ORDER_ID");
        assertAnswer("c1|TEXT ;; c2|NUMBER(1, 0) ;; c3|NUMBER(1, 0)", "SELECT COLUMN_NAME, TYPE FROM TABLE("
            + "INFER_SCHEMA(LOCATION => '@fst/na.csv', FILE_FORMAT => 'ffn2')) ORDER BY ORDER_ID");
        assertAnswer("c1|TEXT ;; c2|TEXT", "SELECT COLUMN_NAME, TYPE FROM TABLE("
            + "INFER_SCHEMA(LOCATION => '@fst/trim.csv', FILE_FORMAT => 'fftr')) ORDER BY ORDER_ID");
        assertAnswer("1|2", "SELECT $1, $2 FROM @fst/tab.tsv (FILE_FORMAT => 'fftab')");
        assertAnswer("1|2", "SELECT $1, $2 FROM @fst2/tab.tsv");
        final String described = answer("DESCRIBE STAGE fst2");
        assertTrue(described.contains("STAGE_FILE_FORMAT|FIELD_DELIMITER|String|\\t|,"), described);
    }
}
