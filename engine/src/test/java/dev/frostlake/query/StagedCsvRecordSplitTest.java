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

package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A staged-file query splits a CSV file as INFER_SCHEMA does (live-verified): a field delimiter of several
 * characters, a record delimiter other than a line break, an escaped line break inside a record, ESCAPE inside an
 * enclosure, and a closing enclosure followed by anything but a delimiter refused at its place.
 */
public class StagedCsvRecordSplitTest extends MeasuredCellsTestSupport {

    @Test
    public void aStagedFileQuerySplitsAsTheFormatSays() {
        engine.execute("CREATE OR REPLACE STAGE cst");
        engine.execute("CREATE OR REPLACE FILE FORMAT ff_pq TYPE = CSV FIELD_DELIMITER = '|' "
            + "FIELD_OPTIONALLY_ENCLOSED_BY = '\"'");
        engine.execute("CREATE OR REPLACE FILE FORMAT ff_multi TYPE = CSV FIELD_DELIMITER = '||' "
            + "FIELD_OPTIONALLY_ENCLOSED_BY = '\"'");
        engine.execute("CREATE OR REPLACE FILE FORMAT ff_two TYPE = CSV FIELD_DELIMITER = '~|'");
        engine.execute("CREATE OR REPLACE FILE FORMAT ff_rec TYPE = CSV RECORD_DELIMITER = ';'");
        engine.execute("CREATE OR REPLACE FILE FORMAT ff_esc TYPE = CSV FIELD_OPTIONALLY_ENCLOSED_BY = '\"' ESCAPE "
            + "= '!'");
        engine.execute("CREATE OR REPLACE FILE FORMAT ff_plain TYPE = CSV");
        stageLocalFile("cst", "enc.csv", "1|\"a!\"b\"|z\n");
        stageLocalFile("cst", "multi.csv", "a||b||c\nd||e||f\n\"g||h\"||i||j\n");
        stageLocalFile("cst", "rec.csv", "a,b;c,d;e,f;");
        stageLocalFile("cst", "nl.csv", "one,two\\\nthree\nfour,five\n");
        stageLocalFile("cst", "esc.csv", "\"a!\"b\",x\n\"c!!d\",y\n");
        stageLocalFile("cst", "two.csv", "a~|b~|c\nd~|e~|f\n");
        assertCells(new String[][] {
            {"SELECT $1, $2, $3 FROM @cst/enc.csv (FILE_FORMAT => 'ff_pq')",
                "REFUSED Found character 'b' instead of field delimiter '|' |   File 'enc.csv', line 1, character "
                + "7 |   Row 1, column \"TRANSIENT_STAGE_TABLE\"[\"$2\":2]"},
            {"SELECT $1, $2, $3, $4 FROM @cst/multi.csv (FILE_FORMAT => 'ff_multi') ORDER BY 1",
                "a|b|c|null ;; d|e|f|null ;; g||h|i|j|null"},
            {"SELECT $1, $2, $3 FROM @cst/two.csv (FILE_FORMAT => 'ff_two') ORDER BY 1", "a|b|c ;; d|e|f"},
            {"SELECT $1, $2 FROM @cst/rec.csv (FILE_FORMAT => 'ff_rec') ORDER BY 1", "a|b ;; c|d ;; e|f"},
            {"SELECT COUNT(*) FROM @cst/rec.csv (FILE_FORMAT => 'ff_rec')", "3"},
            {"SELECT $1, $2 FROM @cst/nl.csv (FILE_FORMAT => 'ff_plain') ORDER BY 1", "four|five ;; one|two | three"},
            {"SELECT $1, $2 FROM @cst/esc.csv (FILE_FORMAT => 'ff_esc') ORDER BY 2", "a\"b|x ;; c!d|y"},
            {"SELECT $1 FROM @cst/enc.csv (FILE_FORMAT => 'ff_plain')", "1|\"a!\"b\"|z"},
            {"SELECT $1, $2 FROM @cst/multi.csv (FILE_FORMAT => 'ff_pq') ORDER BY 1", "a|null ;; d|null ;; g||h|null"},
        });
    }
}
