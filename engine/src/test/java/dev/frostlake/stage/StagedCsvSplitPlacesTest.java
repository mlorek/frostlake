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

package dev.frostlake.stage;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * How a staged CSV file splits and where its refusals place a fault: a line break after the last of a custom
 * RECORD_DELIMITER is one more record, an escaped record delimiter is taken literally, a FIELD_DELIMITER of
 * several characters splits each record, and a closing enclosure followed by text is refused at its line and
 * character - the record's own line under a delimiter of several characters (all live-verified). A COPY load
 * splits its files with the same reader. The files are written to a temporary directory and PUT on a stage.
 */
public class StagedCsvSplitPlacesTest extends MeasuredCellsTestSupport {

    /** Each file the stages hold, by name. */
    private static final Map<String, byte[]> FILES = new HashMap<>();

    static {
        FILES.put("semi.csv", "a,1;b,2;\n".getBytes(StandardCharsets.UTF_8));
        FILES.put("semi_nolf.csv", "a,1;b,2;".getBytes(StandardCharsets.UTF_8));
        FILES.put("semi_esc.csv", "a\\;b,1;c,2;".getBytes(StandardCharsets.UTF_8));
        FILES.put("pipes.csv", "a||1\nb||2\n".getBytes(StandardCharsets.UTF_8));
        FILES.put("pipes_bad.csv", "a||1||x\nb||2\n".getBytes(StandardCharsets.UTF_8));
        FILES.put("encl.csv", "\"a\"x,1\n".getBytes(StandardCharsets.UTF_8));
        FILES.put("pos1.csv", "\"a\"x||1\n".getBytes(StandardCharsets.UTF_8));
        FILES.put("pos2.csv", "b||2;\"a\"x||1;".getBytes(StandardCharsets.UTF_8));
        FILES.put("pos3.csv", "b||2\nc||3\n\"a\"x||1\n".getBytes(StandardCharsets.UTF_8));
        FILES.put("pos4.csv", "\"b\",2\n\"a\"x,1\n".getBytes(StandardCharsets.UTF_8));
    }

    private Path dir;

    @BeforeEach
    public void makeDirectory() throws IOException {
        dir = Files.createTempDirectory("staged_xml_");
    }

    @AfterEach
    public void removeDirectory() {
        final File[] children = dir.toFile().listFiles();
        if (children != null) {
            for (final File child : children) {
                child.delete();
            }
        }
        dir.toFile().delete();
    }

    /** Writes a file of {@link #FILES} and PUTs it on a stage, uncompressed. */
    private void put(final String stage, final String name) throws IOException {
        final Path file = dir.resolve(name);
        Files.write(file, FILES.get(name));
        engine.execute("PUT file://" + file + " @" + stage + " AUTO_COMPRESS=FALSE");
    }

    /** Custom and multi-character delimiters, an escape and an enclosure. */
    @Test
    public void recordSplitting() throws IOException {
        engine.execute("CREATE OR REPLACE STAGE spl_st");
        put("spl_st", "semi.csv");
        put("spl_st", "semi_nolf.csv");
        put("spl_st", "semi_esc.csv");
        put("spl_st", "pipes.csv");
        put("spl_st", "pipes_bad.csv");
        put("spl_st", "encl.csv");
        engine.execute("CREATE OR REPLACE FILE FORMAT f_semi TYPE = CSV RECORD_DELIMITER = ';'");
        engine.execute("CREATE OR REPLACE FILE FORMAT f_semi_esc TYPE = CSV RECORD_DELIMITER = ';' "
                + "ESCAPE_UNENCLOSED_FIELD = '\\\\'");
        engine.execute("CREATE OR REPLACE FILE FORMAT f_pipes TYPE = CSV FIELD_DELIMITER = '||'");
        engine.execute("CREATE OR REPLACE FILE FORMAT f_csv TYPE = CSV");
        assertAnswer("1|a|1|1 ;; 2|b|2|1 ;; 3| | |null|1",
            "SELECT METADATA$FILE_ROW_NUMBER, $1, $2, LENGTH($1) FROM @spl_st/semi.csv (FILE_FORMAT => 'f_semi') "
                + "ORDER BY 1");
        assertAnswer("3", "SELECT COUNT(*) FROM @spl_st/semi.csv (FILE_FORMAT => 'f_semi')");
        assertAnswer("1|a|1 ;; 2|b|2",
            "SELECT METADATA$FILE_ROW_NUMBER, $1, $2 FROM @spl_st/semi_nolf.csv (FILE_FORMAT => 'f_semi') ORDER BY 1");
        assertAnswer("1|a;b|1 ;; 2|c|2",
            "SELECT METADATA$FILE_ROW_NUMBER, $1, $2 FROM @spl_st/semi_esc.csv (FILE_FORMAT => 'f_semi_esc') ORDER "
                + "BY 1");
        assertAnswer("1|a;b|1 ;; 2|c|2",
            "SELECT METADATA$FILE_ROW_NUMBER, $1, $2 FROM @spl_st/semi_esc.csv (FILE_FORMAT => 'f_semi') ORDER BY 1");
        assertAnswer("1|a|1 ;; 2|b|2",
            "SELECT METADATA$FILE_ROW_NUMBER, $1, $2 FROM @spl_st/pipes.csv (FILE_FORMAT => 'f_pipes') ORDER BY 1");
        assertAnswer("1|a|1|x ;; 2|b|2|null",
            "SELECT METADATA$FILE_ROW_NUMBER, $1, $2, $3 FROM @spl_st/pipes_bad.csv (FILE_FORMAT => 'f_pipes') ORDER "
                + "BY 1");
        assertAnswer("\"a\"x|1", "SELECT $1, $2 FROM @spl_st/encl.csv (FILE_FORMAT => 'f_csv')");
    }

    /** Where a closing enclosure followed by text is refused. */
    @Test
    public void faultPlaces() throws IOException {
        engine.execute("CREATE OR REPLACE STAGE pos_st");
        put("pos_st", "pos1.csv");
        put("pos_st", "pos2.csv");
        put("pos_st", "pos3.csv");
        put("pos_st", "pos4.csv");
        engine.execute("CREATE OR REPLACE FILE FORMAT f_pq TYPE = CSV FIELD_DELIMITER = '||' "
                + "FIELD_OPTIONALLY_ENCLOSED_BY = '\"'");
        engine.execute("CREATE OR REPLACE FILE FORMAT f_pqs TYPE = CSV FIELD_DELIMITER = '||' RECORD_DELIMITER = ';' "
                + "FIELD_OPTIONALLY_ENCLOSED_BY = '\"'");
        engine.execute("CREATE OR REPLACE FILE FORMAT f_q TYPE = CSV FIELD_OPTIONALLY_ENCLOSED_BY = '\"'");
        assertAnswer("REFUSED Found character 'x' instead of field delimiter '||' |   File 'pos1.csv', line 1, "
                + "character 4 |   Row 1, column \"TRANSIENT_STAGE_TABLE\"[\"$1\":1]",
            "SELECT $1, $2 FROM @pos_st/pos1.csv (FILE_FORMAT => 'f_pq')");
        assertAnswer("REFUSED Found character 'x' instead of field delimiter '||' |   File 'pos2.csv', line 1, "
                + "character 9 |   Row 2, column \"TRANSIENT_STAGE_TABLE\"[\"$1\":1]",
            "SELECT $1, $2 FROM @pos_st/pos2.csv (FILE_FORMAT => 'f_pqs')");
        assertAnswer("REFUSED Found character 'x' instead of field delimiter '||' |   File 'pos3.csv', line 1, "
                + "character 4 |   Row 3, column \"TRANSIENT_STAGE_TABLE\"[\"$1\":1]",
            "SELECT $1, $2 FROM @pos_st/pos3.csv (FILE_FORMAT => 'f_pq')");
        assertAnswer("REFUSED Found character 'x' instead of field delimiter ',' |   File 'pos4.csv', line 2, "
                + "character 4 |   Row 2, column \"TRANSIENT_STAGE_TABLE\"[\"$1\":1]",
            "SELECT $1, $2 FROM @pos_st/pos4.csv (FILE_FORMAT => 'f_q')");
    }
}
