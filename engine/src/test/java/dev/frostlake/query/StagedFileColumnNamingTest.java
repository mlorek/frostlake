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
 * The column a staged file's refusal names: the positional column being read where the query reads one, none
 * under COUNT(*), a constant or METADATA$ columns alone (all live-verified).
 */
public class StagedFileColumnNamingTest extends MeasuredCellsTestSupport {

    @Test
    public void stagedRefusalColumns() {
        engine.execute("CREATE OR REPLACE STAGE st2489");
        engine.execute("PUT file:///home/nous/.claude/jobs/6e695449/tmp/w240/enc2/bad.json @st2489 "
            + "AUTO_COMPRESS=FALSE");
        engine.execute("PUT file:///home/nous/.claude/jobs/6e695449/tmp/w240/enc2/bad.xml @st2489 AUTO_COMPRESS=FALSE");
        engine.execute("PUT file:///home/nous/.claude/jobs/6e695449/tmp/w240/enc3/pos2.csv @st2489 "
            + "AUTO_COMPRESS=FALSE");
        engine.execute("CREATE OR REPLACE FILE FORMAT fj2489 TYPE = JSON");
        engine.execute("CREATE OR REPLACE FILE FORMAT fx2489 TYPE = XML");
        engine.execute("CREATE OR REPLACE FILE FORMAT fc2489 TYPE = CSV FIELD_DELIMITER = '||' RECORD_DELIMITER = "
            + "';' FIELD_OPTIONALLY_ENCLOSED_BY = '\"'");
        assertShapedAnswer("REFUSED Error parsing JSON: invalid UTF-8 sequence: 0xE9 |   File 'stages/<QID>/bad.json',"
                + " line 1, character 10 |   Row 0, column ",
            "SELECT COUNT(*) FROM @st2489/bad.json (FILE_FORMAT => 'fj2489')");
        assertShapedAnswer("REFUSED Error parsing XML: invalid UTF-8 sequence: 0xE9 |   File 'stages/<QID>/bad.xml', "
                + "line 1, character 13 |   Row 0, column ",
            "SELECT COUNT(*) FROM @st2489/bad.xml (FILE_FORMAT => 'fx2489')");
        assertShapedAnswer("REFUSED Error parsing JSON: invalid UTF-8 sequence: 0xE9 |   File 'stages/<QID>/bad.json',"
                + " line 1, character 10 |   Row 0, column $1",
            "SELECT $1:a FROM @st2489/bad.json (FILE_FORMAT => 'fj2489')");
        assertShapedAnswer("REFUSED Error parsing JSON: invalid UTF-8 sequence: 0xE9 |   File 'stages/<QID>/bad.json',"
                + " line 1, character 10 |   Row 0, column ",
            "SELECT METADATA$FILENAME FROM @st2489/bad.json (FILE_FORMAT => 'fj2489')");
        assertShapedAnswer("REFUSED Error parsing JSON: invalid UTF-8 sequence: 0xE9 |   File 'stages/<QID>/bad.json',"
                + " line 1, character 10 |   Row 0, column ",
            "SELECT 1 FROM @st2489/bad.json (FILE_FORMAT => 'fj2489')");
        assertShapedAnswer("REFUSED Error parsing JSON: invalid UTF-8 sequence: 0xE9 |   File 'stages/<QID>/bad.json',"
                + " line 1, character 10 |   Row 0, column $1",
            "SELECT COUNT($1) FROM @st2489/bad.json (FILE_FORMAT => 'fj2489')");
        assertShapedAnswer("REFUSED Error parsing XML: invalid UTF-8 sequence: 0xE9 |   File 'stages/<QID>/bad.xml', "
                + "line 1, character 13 |   Row 0, column $1",
            "SELECT $1 FROM @st2489/bad.xml (FILE_FORMAT => 'fx2489')");
        assertShapedAnswer("REFUSED Error parsing JSON: invalid UTF-8 sequence: 0xE9 |   File 'stages/<QID>/bad.json',"
                + " line 1, character 10 |   Row 0, column $1",
            "SELECT COUNT(*) FROM @st2489/bad.json (FILE_FORMAT => 'fj2489') WHERE $1 IS NOT NULL");
        assertAnswer("REFUSED Found character 'x' instead of field delimiter '||' |   File 'pos2.csv', line 1, "
                + "character 9 |   Row 2, column \"TRANSIENT_STAGE_TABLE\"[1]",
            "SELECT COUNT(*) FROM @st2489/pos2.csv (FILE_FORMAT => 'fc2489')");
        assertAnswer("REFUSED Found character 'x' instead of field delimiter '||' |   File 'pos2.csv', line 1, "
                + "character 9 |   Row 2, column \"TRANSIENT_STAGE_TABLE\"[\"$1\":1]",
            "SELECT $1, $2 FROM @st2489/pos2.csv (FILE_FORMAT => 'fc2489')");
        assertAnswer("REFUSED Found character 'x' instead of field delimiter '||' |   File 'pos2.csv', line 1, "
                + "character 9 |   Row 2, column \"TRANSIENT_STAGE_TABLE\"[\"$1\":1]",
            "SELECT $2 FROM @st2489/pos2.csv (FILE_FORMAT => 'fc2489')");
        assertAnswer("REFUSED Found character 'x' instead of field delimiter '||' |   File 'pos2.csv', line 1, "
                + "character 9 |   Row 2, column \"TRANSIENT_STAGE_TABLE\"[1]",
            "SELECT METADATA$FILE_ROW_NUMBER FROM @st2489/pos2.csv (FILE_FORMAT => 'fc2489')");
        assertAnswer("REFUSED Found character 'x' instead of field delimiter '||' |   File 'pos2.csv', line 1, "
                + "character 9 |   Row 2, column \"TRANSIENT_STAGE_TABLE\"[1]",
            "SELECT 1 FROM @st2489/pos2.csv (FILE_FORMAT => 'fc2489')");
        assertAnswer("REFUSED Found character 'x' instead of field delimiter '||' |   File 'pos2.csv', line 1, "
                + "character 9 |   Row 2, column \"TRANSIENT_STAGE_TABLE\"[\"$1\":1]",
            "SELECT COUNT(*) FROM @st2489/pos2.csv (FILE_FORMAT => 'fc2489') WHERE $2 = '1'");
        assertAnswer("REFUSED Found character 'x' instead of field delimiter '||' |   File 'pos2.csv', line 1, "
                + "character 9 |   Row 2, column \"TRANSIENT_STAGE_TABLE\"[\"$1\":1]",
            "SELECT t.$1 FROM @st2489/pos2.csv (FILE_FORMAT => 'fc2489') t");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
