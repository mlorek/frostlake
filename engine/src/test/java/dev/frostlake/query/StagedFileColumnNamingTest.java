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

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * The column a staged file's refusal names: the positional column being read where the query reads one, none
 * under COUNT(*), a constant or METADATA$ columns alone (all live-verified).
 */
public class StagedFileColumnNamingTest extends MeasuredCellsTestSupport {

    @Test
    public void stagedRefusalColumns() {
        engine.execute("CREATE OR REPLACE STAGE st2489");
        // The fixtures, written by the test: a lone 0xE9 (Latin-1 e-acute, no UTF-8 sequence) inside a JSON
        // string and an XML text, and a CSV field whose enclosure is followed by 'x' instead of the delimiter.
        stageLocalBytes("st2489", "bad.json", latin1("{\"a\":\"caf\u00e9\",\"n\":1}\n"));
        stageLocalBytes("st2489", "bad.xml", latin1("<root><a>caf\u00e9</a></root>"));
        stageLocalBytes("st2489", "pos2.csv", latin1("b||2;\"a\"x||1;"));
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

    /** The text's bytes in Latin-1, one byte a character, so that an e-acute is the single byte 0xE9. */
    private static byte[] latin1(final String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }
}
