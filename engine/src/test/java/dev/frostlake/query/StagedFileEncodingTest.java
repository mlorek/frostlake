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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A staged-file query reads a CSV file in its format's ENCODING, a stage's own format's included, and a byte the set
 * cannot read refuses the field that holds it when the query reads that field - Invalid UTF8 detected in string
 * 'caf0xE9', the byte spelled as its value, with the file, the line and character of the field, and the row - while
 * a count of the file's rows reads no field and answers; REPLACE_INVALID_CHARACTERS = TRUE reads each such sequence
 * as U+FFFD instead. A JSON file's byte that is no UTF-8 refuses the file as the account's JSON parser words it, and
 * ENCODING is no option of a JSON format (live-verified).
 */
public class StagedFileEncodingTest extends MeasuredCellsTestSupport {

    /** Bytes as written: a text piece as its ASCII bytes, a number as one byte of that value. */
    private static byte[] bytes(final Object... pieces) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (final Object piece : pieces) {
            if (piece instanceof Integer) {
                out.write(((Integer) piece).intValue());
            } else {
                final String text = (String) piece;
                for (int i = 0; i < text.length(); i++) {
                    out.write(text.charAt(i));
                }
            }
        }
        return out.toByteArray();
    }

    /** PUTs a file of exactly these bytes onto a stage, uncompressed. */
    private void stageBytes(final String stage, final String fileName, final byte[] content) {
        try {
            final Path file = Files.createTempDirectory("fl_stage_bytes").resolve(fileName);
            Files.write(file, content);
            engine.execute("PUT file://" + file.toAbsolutePath() + " @" + stage + " AUTO_COMPRESS=FALSE");
        } catch (final IOException e) {
            throw new RuntimeException("could not stage " + fileName, e);
        }
    }

    @Test
    public void aStagedFileIsReadInItsEncoding() {
        engine.execute("CREATE OR REPLACE STAGE enc_st");
        stageBytes("enc_st", "latin.csv", bytes("caf", 0xE9, ",1\nok,2\n"));
        stageBytes("enc_st", "good.csv", bytes("caf", 0xC3, 0xA9, ",3\n"));
        stageBytes("enc_st", "bad.json", bytes("{\"a\":\"caf", 0xE9, "\",\"n\":1}\n"));
        stageBytes("enc_st", "mixed.csv", bytes("x", 0xFF, "y,4\n"));
        stageBytes("enc_st", "win.csv", bytes(0x80, "uro,5\n"));
        engine.execute("CREATE OR REPLACE FILE FORMAT f_utf8 TYPE = CSV");
        engine.execute("CREATE OR REPLACE FILE FORMAT f_latin TYPE = CSV ENCODING = 'ISO-8859-1'");
        engine.execute("CREATE OR REPLACE FILE FORMAT f_win TYPE = CSV ENCODING = 'WINDOWS1252'");
        engine.execute("CREATE OR REPLACE FILE FORMAT f_rep TYPE = CSV REPLACE_INVALID_CHARACTERS = TRUE");
        engine.execute("CREATE OR REPLACE FILE FORMAT f_json TYPE = JSON");
        engine.execute("CREATE OR REPLACE FILE FORMAT f_json_rep TYPE = JSON REPLACE_INVALID_CHARACTERS = TRUE");
        engine.execute("CREATE OR REPLACE STAGE enc_st2 FILE_FORMAT = (TYPE = CSV ENCODING = 'ISO-8859-1')");
        stageBytes("enc_st2", "latin.csv", bytes("caf", 0xE9, ",1\nok,2\n"));
        assertCells(new String[][] {
            {"SELECT $1, $2 FROM @enc_st/latin.csv (FILE_FORMAT => 'f_utf8')",
                "REFUSED Invalid UTF8 detected in string 'caf0xE9' |   File 'latin.csv', line 1, character 1 |   "
                + "Row 1, column \"TRANSIENT_STAGE_TABLE\"[\"$1\":1]"},
            {"SELECT $1, LENGTH($1) FROM @enc_st/latin.csv (FILE_FORMAT => 'f_latin')", "caf\u00e9|4 ;; ok|2"},
            {"SELECT $1, LENGTH($1), HEX_ENCODE($1) FROM @enc_st/latin.csv (FILE_FORMAT => 'f_rep')",
                "caf\ufffd|4|636166EFBFBD ;; ok|2|6F6B"},
            {"SELECT $1, LENGTH($1) FROM @enc_st/good.csv (FILE_FORMAT => 'f_latin')", "caf\u00c3\u00a9|5"},
            {"SELECT $1, LENGTH($1) FROM @enc_st/good.csv (FILE_FORMAT => 'f_utf8')", "caf\u00e9|4"},
            {"SELECT $1, $2 FROM @enc_st/mixed.csv (FILE_FORMAT => 'f_utf8')",
                "REFUSED Invalid UTF8 detected in string 'x0xFFy' |   File 'mixed.csv', line 1, character 1 |   "
                + "Row 1, column \"TRANSIENT_STAGE_TABLE\"[\"$1\":1]"},
            {"SELECT HEX_ENCODE($1) FROM @enc_st/mixed.csv (FILE_FORMAT => 'f_rep')", "78EFBFBD79"},
            {"SELECT $1 FROM @enc_st/win.csv (FILE_FORMAT => 'f_win')", "\u20acuro"},
            {"SELECT $1 FROM @enc_st/win.csv (FILE_FORMAT => 'f_latin')", "\u0080uro"},
        });
        assertShapedAnswer("REFUSED Error parsing JSON: invalid UTF-8 sequence: 0xE9 |   File "
                + "'stages/<QID>/bad.json', line 1, character 10 |   Row 0, column $1",
            "SELECT $1 FROM @enc_st/bad.json (FILE_FORMAT => 'f_json')");
        assertCells(new String[][] {
            {"SELECT $1 FROM @enc_st/bad.json (FILE_FORMAT => 'f_json_rep')", "{\"a\":\"caf\ufffd\",\"n\":1}"},
            {"SELECT $1:a::VARCHAR, LENGTH($1:a::VARCHAR) FROM @enc_st/bad.json (FILE_FORMAT => 'f_json_rep')",
                "caf\ufffd|4"},
            {"SELECT $1 FROM @enc_st2/latin.csv", "caf\u00e9 ;; ok"},
            {"SELECT COUNT(*) FROM @enc_st/latin.csv (FILE_FORMAT => 'f_utf8')", "2"},
            {"SELECT METADATA$FILE_ROW_NUMBER, $1 FROM @enc_st/latin.csv (FILE_FORMAT => 'f_utf8') ORDER BY 1",
                "REFUSED Invalid UTF8 detected in string 'caf0xE9' |   File 'latin.csv', line 1, character 1 |   "
                + "Row 1, column \"TRANSIENT_STAGE_TABLE\"[\"$1\":1]"},
            {"CREATE OR REPLACE FILE FORMAT f_json_latin TYPE = JSON ENCODING = 'ISO-8859-1'",
                "REFUSED SQL compilation error: | Option ENCODING is not valid for file format type JSON."},
            {"SELECT $1 FROM @enc_st/bad.json (FILE_FORMAT => 'f_json_latin')",
                "REFUSED SQL compilation error: | File format 'F_JSON_LATIN' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on FILE FORMAT F_JSON_LATIN."},
        });
    }
}
