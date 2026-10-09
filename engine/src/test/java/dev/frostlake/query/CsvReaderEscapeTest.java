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
 * How a CSV file's escapes and NULLs are read, by a staged-file query and by a COPY into a table alike.
 * Outside an enclosure ESCAPE_UNENCLOSED_FIELD — a backslash unless the format says otherwise — takes the
 * field delimiter, a line break or itself literally and is an ordinary character before anything else; no
 * escape sequence is read. The text backslash-N is NULL unless NULL_IF names other values or none.
 * RECORD_DELIMITER = NONE reads a file as one record and FIELD_DELIMITER = NONE a record as one field.
 * An unload's text loads back as the rows it was written from. Every answer is live-verified.
 */
public class CsvReaderEscapeTest extends MeasuredCellsTestSupport {

    @Test
    public void aStagedFileQueryReadsEscapesAndNulls() {
        engine.execute("CREATE OR REPLACE STAGE rst");
        engine.execute("CREATE OR REPLACE FILE FORMAT raw_file TYPE = CSV FIELD_DELIMITER = NONE RECORD_DELIMITER = NONE "
            + "ESCAPE_UNENCLOSED_FIELD = NONE NULL_IF = () EMPTY_FIELD_AS_NULL = FALSE");
        engine.execute("CREATE OR REPLACE FILE FORMAT pipes TYPE = CSV FIELD_DELIMITER = '|'");
        engine.execute("CREATE OR REPLACE FILE FORMAT pipes_keep TYPE = CSV FIELD_DELIMITER = '|' NULL_IF = ()");
        engine.execute("CREATE OR REPLACE FILE FORMAT pipes_raw TYPE = CSV FIELD_DELIMITER = '|' ESCAPE_UNENCLOSED_FIELD = NONE");
        engine.execute("CREATE OR REPLACE FILE FORMAT pipes_null TYPE = CSV FIELD_DELIMITER = '|' NULL_IF = ('NA', 'g\\\\h')");
        engine.execute("CREATE OR REPLACE FILE FORMAT pipes_quoted TYPE = CSV FIELD_DELIMITER = '|' "
            + "FIELD_OPTIONALLY_ENCLOSED_BY = '\"'");
        engine.execute("CREATE OR REPLACE FILE FORMAT pipes_quoted_esc TYPE = CSV FIELD_DELIMITER = '|' "
            + "FIELD_OPTIONALLY_ENCLOSED_BY = '\"' ESCAPE = '!'");
        engine.execute("COPY INTO @rst/f1 FROM (SELECT 'a\\\\\\\\b|c\\\\,d|e\\\\|f|\\\\N|\\\\\\\\N|g\\\\h|NA|x') FILE_FORMAT = (TYPE = CSV "
            + "COMPRESSION = NONE FIELD_DELIMITER = NONE ESCAPE_UNENCLOSED_FIELD = NONE) SINGLE = TRUE");
        assertAnswer(
            "a\\\\b|c\\,d|e\\|f|\\N|\\\\N|g\\h|NA|x | ",
            "SELECT $1 FROM @rst/f1 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "a\\b|c\\,d|e|f|null|null|g\\h|NA|x",
            "SELECT $1, $2, $3, $4, $5, $6, $7, $8 FROM @rst/f1 (FILE_FORMAT => 'pipes')");
        assertAnswer(
            "a\\b|c\\,d|e|f|\\N|\\N|g\\h|NA",
            "SELECT $1, $2, $3, $4, $5, $6, $7 FROM @rst/f1 (FILE_FORMAT => 'pipes_keep')");
        assertAnswer(
            "a\\\\b|c\\,d|e\\|f|null|\\\\N|g\\h|NA|x",
            "SELECT $1, $2, $3, $4, $5, $6, $7, $8, $9 FROM @rst/f1 (FILE_FORMAT => 'pipes_raw')");
        assertAnswer(
            "a\\b|c\\,d|e|f|\\N|\\N|null|null",
            "SELECT $1, $2, $3, $4, $5, $6, $7 FROM @rst/f1 (FILE_FORMAT => 'pipes_null')");
        assertAnswer(
            "a\\b|c,d|e\\|f|\\N|\\N|g\\h|NA|x",
            "SELECT $1 FROM @rst/f1");
        engine.execute("CREATE OR REPLACE TABLE lt (c1 VARCHAR, c2 VARCHAR, c3 VARCHAR, c4 VARCHAR, c5 VARCHAR, c6 VARCHAR, "
            + "c7 VARCHAR)");
        assertAnswer(
            "REFUSED Number of columns in file (8) does not match that of the corresponding table (7), use "
                + "file format option error_on_column_count_mismatch=false to ignore this error |   File 'f1', "
                + "line 1, character 31 |   Row 1, column \"LT\"[8] |   If you would like to continue loading when "
                + "an error is encountered, use other values such as 'SKIP_FILE' or 'CONTINUE' for the ON_ERROR "
                + "option. For more information on loading options, please run 'info loading_data' in a SQL client.",
            "COPY INTO lt FROM @rst/f1 FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|')");
        assertAnswer(
            "",
            "SELECT * FROM lt");
        engine.execute("TRUNCATE TABLE lt");
        assertAnswer(
            "REFUSED Number of columns in file (9) does not match that of the corresponding table (7), use "
                + "file format option error_on_column_count_mismatch=false to ignore this error |   File 'f1', "
                + "line 1, character 31 |   Row 1, column \"LT\"[9] |   If you would like to continue loading when "
                + "an error is encountered, use other values such as 'SKIP_FILE' or 'CONTINUE' for the ON_ERROR "
                + "option. For more information on loading options, please run 'info loading_data' in a SQL client.",
            "COPY INTO lt FROM @rst/f1 FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|' "
                + "ESCAPE_UNENCLOSED_FIELD = NONE) FORCE = TRUE");
        assertAnswer(
            "",
            "SELECT * FROM lt");
        engine.execute("TRUNCATE TABLE lt");
        assertAnswer(
            "REFUSED Number of columns in file (8) does not match that of the corresponding table (7), use "
                + "file format option error_on_column_count_mismatch=false to ignore this error |   File 'f1', "
                + "line 1, character 31 |   Row 1, column \"LT\"[8] |   If you would like to continue loading when "
                + "an error is encountered, use other values such as 'SKIP_FILE' or 'CONTINUE' for the ON_ERROR "
                + "option. For more information on loading options, please run 'info loading_data' in a SQL client.",
            "COPY INTO lt FROM @rst/f1 FILE_FORMAT = (FORMAT_NAME = 'pipes_keep') FORCE = TRUE");
        assertAnswer(
            "",
            "SELECT * FROM lt");
        engine.execute("COPY INTO @rst/f2 FROM (SELECT 'one|two\\\\\nthree|four') FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE "
            + "FIELD_DELIMITER = NONE ESCAPE_UNENCLOSED_FIELD = NONE) SINGLE = TRUE");
        assertAnswer(
            "one|two\\ | three|four | ",
            "SELECT $1 FROM @rst/f2 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "one|two | three|four",
            "SELECT $1, $2, $3 FROM @rst/f2 (FILE_FORMAT => 'pipes')");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM @rst/f2 (FILE_FORMAT => 'pipes')");
        assertAnswer(
            "2",
            "SELECT COUNT(*) FROM @rst/f2 (FILE_FORMAT => 'pipes_raw')");
        engine.execute("COPY INTO @rst/f3 FROM (SELECT '\"q|1\"|\"a\"\"b\"|\"a\\\\\\\\b\"|\"a!\"b\"|plain\\\\|x|\"\"|') FILE_FORMAT = (TYPE = "
            + "CSV COMPRESSION = NONE FIELD_DELIMITER = NONE ESCAPE_UNENCLOSED_FIELD = NONE) SINGLE = TRUE");
        assertAnswer(
            "\"q|1\"|\"a\"\"b\"|\"a\\\\b\"|\"a!\"b\"|plain\\|x|\"\"| | ",
            "SELECT $1 FROM @rst/f3 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "q|1|a\"b|a\\\\b|a\"b|plain|x||null",
            "SELECT $1, $2, $3, $4, $5, $6, $7 FROM @rst/f3 (FILE_FORMAT => 'pipes_quoted_esc')");
        engine.execute("COPY INTO @rst/f4 FROM (SELECT 'x\\\\ty|\\\\x41|\\\\101|tab\\\\\\\\t|\\\\0') FILE_FORMAT = (TYPE = CSV "
            + "COMPRESSION = NONE FIELD_DELIMITER = NONE ESCAPE_UNENCLOSED_FIELD = NONE) SINGLE = TRUE");
        assertAnswer(
            "x\\ty|\\x41|\\101|tab\\t|\\0",
            "SELECT $1, $2, $3, $4, $5 FROM @rst/f4 (FILE_FORMAT => 'pipes')");
        assertAnswer(
            "4|4|4|5|2",
            "SELECT LENGTH($1), LENGTH($2), LENGTH($3), LENGTH($4), LENGTH($5) FROM @rst/f4 (FILE_FORMAT => "
                + "'pipes')");
    }

    @Test
    public void aLoadReadsThemTheSameWay() {
        engine.execute("CREATE OR REPLACE STAGE cst");
        engine.execute("CREATE OR REPLACE FILE FORMAT keep_all TYPE = CSV NULL_IF = ()");
        engine.execute("CREATE OR REPLACE TABLE lt (id INT, name VARCHAR)");
        engine.execute("COPY INTO @cst/f1 FROM (SELECT '1,\\\\N\n2,a\\\\,b\n3,x\\\\\\\\y\n4,\\\\\\\\N\n5,p\\\\q') FILE_FORMAT = (TYPE = CSV "
            + "COMPRESSION = NONE FIELD_DELIMITER = NONE ESCAPE_UNENCLOSED_FIELD = NONE) SINGLE = TRUE");
        assertAnswer(
            "cst/f1|LOADED|5|5|1|0|null|null|null|null",
            "COPY INTO lt FROM @cst/f1 FILE_FORMAT = (TYPE = 'CSV')");
        assertAnswer(
            "1|null|true ;; 2|a,b|false ;; 3|x\\y|false ;; 4|null|true ;; 5|p\\q|false",
            "SELECT id, name, name IS NULL FROM lt ORDER BY id");
        engine.execute("TRUNCATE TABLE lt");
        assertAnswer(
            "cst/f1|LOADED|5|5|1|0|null|null|null|null",
            "COPY INTO lt FROM @cst/f1 FILE_FORMAT = (TYPE = 'CSV' NULL_IF = ()) FORCE = TRUE");
        assertAnswer(
            "1|\\N ;; 2|a,b ;; 3|x\\y ;; 4|\\N ;; 5|p\\q",
            "SELECT id, name FROM lt ORDER BY id");
        engine.execute("TRUNCATE TABLE lt");
        assertAnswer(
            "cst/f1|LOADED|5|5|1|0|null|null|null|null",
            "COPY INTO lt FROM @cst/f1 FILE_FORMAT = (FORMAT_NAME = 'keep_all') FORCE = TRUE");
        assertAnswer(
            "1|\\N ;; 2|a,b ;; 3|x\\y ;; 4|\\N ;; 5|p\\q",
            "SELECT id, name FROM lt ORDER BY id");
        engine.execute("TRUNCATE TABLE lt");
        assertAnswer(
            "cst/f1|LOADED|5|5|1|0|null|null|null|null",
            "COPY INTO lt FROM @cst/f1 FILE_FORMAT = (TYPE = 'CSV' ESCAPE_UNENCLOSED_FIELD = NONE "
                + "ERROR_ON_COLUMN_COUNT_MISMATCH = FALSE) FORCE = TRUE");
        assertAnswer(
            "1|null ;; 2|a\\ ;; 3|x\\\\y ;; 4|\\\\N ;; 5|p\\q",
            "SELECT id, name FROM lt ORDER BY id");
        engine.execute("TRUNCATE TABLE lt");
        assertAnswer(
            "cst/f1|LOADED|5|5|1|0|null|null|null|null",
            "COPY INTO lt FROM @cst/f1 FILE_FORMAT = (TYPE = 'CSV' NULL_IF = ('p\\\\q')) FORCE = TRUE");
        assertAnswer(
            "1|\\N ;; 2|a,b ;; 3|x\\y ;; 4|\\N ;; 5|null",
            "SELECT id, name FROM lt ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE src (id INT, name VARCHAR, note VARCHAR)");
        engine.execute("INSERT INTO src VALUES (1, 'a,b', NULL), (2, 'x\\\\y', 'n'), (3, 'plain', 'q\"r')");
        assertAnswer(
            "3|32|32",
            "COPY INTO @cst/round FROM (SELECT * FROM src ORDER BY id) FILE_FORMAT = (TYPE = CSV COMPRESSION "
                + "= NONE) SINGLE = TRUE");
        engine.execute("CREATE OR REPLACE TABLE dst (id INT, name VARCHAR, note VARCHAR)");
        assertAnswer(
            "cst/round|LOADED|3|3|1|0|null|null|null|null",
            "COPY INTO dst FROM @cst/round FILE_FORMAT = (TYPE = CSV)");
        assertAnswer(
            "1|a,b|null ;; 2|x\\y|n ;; 3|plain|q\"r",
            "SELECT id, name, note FROM dst ORDER BY id");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM src s JOIN dst d ON s.id = d.id AND s.name = d.name AND EQUAL_NULL(s.note, "
                + "d.note)");
        assertAnswer(
            "3|42|42",
            "COPY INTO @cst/roundq FROM (SELECT * FROM src ORDER BY id) FILE_FORMAT = (TYPE = CSV COMPRESSION "
                + "= NONE FIELD_OPTIONALLY_ENCLOSED_BY = '\"') SINGLE = TRUE");
        engine.execute("TRUNCATE TABLE dst");
        assertAnswer(
            "cst/roundq|LOADED|3|3|1|0|null|null|null|null",
            "COPY INTO dst FROM @cst/roundq FILE_FORMAT = (TYPE = CSV FIELD_OPTIONALLY_ENCLOSED_BY = '\"')");
        assertAnswer(
            "1|a,b|null ;; 2|x\\y|n ;; 3|plain|q\"r",
            "SELECT id, name, note FROM dst ORDER BY id");
    }
}
