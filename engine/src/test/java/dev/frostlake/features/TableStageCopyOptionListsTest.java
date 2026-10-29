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
package dev.frostlake.features;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A table's STAGE_COPY_OPTIONS, at CREATE and at ALTER, reads its list as a stage's COPY_OPTIONS does (live-verified):
 * a NULL SIZE_LIMIT is no size - at ALTER it clears the size the table had - a list of names alone or an empty one is
 * taken and ignored, each value is judged as the stage's are, and a fault in the list is reported in the stage's lines.
 * An ALTER merges the options it writes into the table's. Another name given a list of properties is refused as one,
 * and STAGE_FILE_FORMAT given a list of names as a format of that name.
 */
public class TableStageCopyOptionListsTest extends MeasuredCellsTestSupport {

    /** NULL values, bare names and the recovery lines at CREATE and ALTER. */
    @Test
    public void nullValuesAndBareNames() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE ts805 (a INT) STAGE_COPY_OPTIONS = (SIZE_LIMIT = NULL)",
                "Table TS805 successfully created."},
            {"DESCRIBE TABLE ts805 TYPE = STAGE",
                "STAGE_FILE_FORMAT|TYPE|String|CSV|CSV ;; STAGE_FILE_FORMAT|RECORD_DELIMITER|String|\\n|\\n ;; "
                + "STAGE_FILE_FORMAT|FIELD_DELIMITER|String|,|, ;; STAGE_FILE_FORMAT|FILE_EXTENSION|String|| ;; "
                + "STAGE_FILE_FORMAT|SKIP_HEADER|Integer|0|0 ;; STAGE_FILE_FORMAT|PARSE_HEADER|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|DATE_FORMAT|String|AUTO|AUTO ;; STAGE_FILE_FORMAT|TIME_FORMAT|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|TIMESTAMP_FORMAT|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|BINARY_FORMAT|String|HEX|HEX ;; STAGE_FILE_FORMAT|ESCAPE|String|NONE|NONE ;; "
                + "STAGE_FILE_FORMAT|ESCAPE_UNENCLOSED_FIELD|String|\\\\|\\\\ ;; "
                + "STAGE_FILE_FORMAT|TRIM_SPACE|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|FIELD_OPTIONALLY_ENCLOSED_BY|String|NONE|NONE ;; "
                + "STAGE_FILE_FORMAT|NULL_IF|List|[\\\\N]|[\\\\N] ;; STAGE_FILE_FORMAT|COMPRESSION|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|ERROR_ON_COLUMN_COUNT_MISMATCH|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|VALIDATE_UTF8|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|SKIP_BLANK_LINES|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|REPLACE_INVALID_CHARACTERS|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|EMPTY_FIELD_AS_NULL|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|SKIP_BYTE_ORDER_MARK|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|ENCODING|String|UTF8|UTF8 ;; STAGE_FILE_FORMAT|MULTI_LINE|Boolean|true|true ;; "
                + "STAGE_COPY_OPTIONS|ON_ERROR|String|ABORT_STATEMENT|ABORT_STATEMENT ;; "
                + "STAGE_COPY_OPTIONS|SIZE_LIMIT|Long|| ;; STAGE_COPY_OPTIONS|PURGE|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|RETURN_FAILED_ONLY|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|ENFORCE_LENGTH|Boolean|true|true ;; "
                + "STAGE_COPY_OPTIONS|TRUNCATECOLUMNS|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|FORCE|Boolean|false|false ;; STAGE_LOCATION|URL|String||"},
            {"CREATE OR REPLACE TABLE ts805 (a INT) STAGE_COPY_OPTIONS = (PURGE)",
                "Table TS805 successfully created."},
            {"DESCRIBE TABLE ts805 TYPE = STAGE",
                "STAGE_FILE_FORMAT|TYPE|String|CSV|CSV ;; STAGE_FILE_FORMAT|RECORD_DELIMITER|String|\\n|\\n ;; "
                + "STAGE_FILE_FORMAT|FIELD_DELIMITER|String|,|, ;; STAGE_FILE_FORMAT|FILE_EXTENSION|String|| ;; "
                + "STAGE_FILE_FORMAT|SKIP_HEADER|Integer|0|0 ;; STAGE_FILE_FORMAT|PARSE_HEADER|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|DATE_FORMAT|String|AUTO|AUTO ;; STAGE_FILE_FORMAT|TIME_FORMAT|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|TIMESTAMP_FORMAT|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|BINARY_FORMAT|String|HEX|HEX ;; STAGE_FILE_FORMAT|ESCAPE|String|NONE|NONE ;; "
                + "STAGE_FILE_FORMAT|ESCAPE_UNENCLOSED_FIELD|String|\\\\|\\\\ ;; "
                + "STAGE_FILE_FORMAT|TRIM_SPACE|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|FIELD_OPTIONALLY_ENCLOSED_BY|String|NONE|NONE ;; "
                + "STAGE_FILE_FORMAT|NULL_IF|List|[\\\\N]|[\\\\N] ;; STAGE_FILE_FORMAT|COMPRESSION|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|ERROR_ON_COLUMN_COUNT_MISMATCH|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|VALIDATE_UTF8|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|SKIP_BLANK_LINES|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|REPLACE_INVALID_CHARACTERS|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|EMPTY_FIELD_AS_NULL|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|SKIP_BYTE_ORDER_MARK|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|ENCODING|String|UTF8|UTF8 ;; STAGE_FILE_FORMAT|MULTI_LINE|Boolean|true|true ;; "
                + "STAGE_COPY_OPTIONS|ON_ERROR|String|ABORT_STATEMENT|ABORT_STATEMENT ;; "
                + "STAGE_COPY_OPTIONS|SIZE_LIMIT|Long|| ;; STAGE_COPY_OPTIONS|PURGE|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|RETURN_FAILED_ONLY|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|ENFORCE_LENGTH|Boolean|true|true ;; "
                + "STAGE_COPY_OPTIONS|TRUNCATECOLUMNS|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|FORCE|Boolean|false|false ;; STAGE_LOCATION|URL|String||"},
            {"CREATE OR REPLACE TABLE ts805 (a INT) STAGE_COPY_OPTIONS = (PURGE, SIZE_LIMIT = 5)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 78 unexpected '='. | syntax error "
                + "line 1 at position 81 unexpected ')'."},
            {"CREATE OR REPLACE TABLE ts805 (a INT) STAGE_COPY_OPTIONS = (PURGE SIZE_LIMIT = 5)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 66 unexpected 'SIZE_LIMIT'. | syntax "
                + "error line 1 at position 66 unexpected 'SIZE_LIMIT'. | syntax error line 1 at position 80 "
                + "unexpected ')'."},
            {"DESCRIBE TABLE ts805 TYPE = STAGE",
                "STAGE_FILE_FORMAT|TYPE|String|CSV|CSV ;; STAGE_FILE_FORMAT|RECORD_DELIMITER|String|\\n|\\n ;; "
                + "STAGE_FILE_FORMAT|FIELD_DELIMITER|String|,|, ;; STAGE_FILE_FORMAT|FILE_EXTENSION|String|| ;; "
                + "STAGE_FILE_FORMAT|SKIP_HEADER|Integer|0|0 ;; STAGE_FILE_FORMAT|PARSE_HEADER|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|DATE_FORMAT|String|AUTO|AUTO ;; STAGE_FILE_FORMAT|TIME_FORMAT|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|TIMESTAMP_FORMAT|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|BINARY_FORMAT|String|HEX|HEX ;; STAGE_FILE_FORMAT|ESCAPE|String|NONE|NONE ;; "
                + "STAGE_FILE_FORMAT|ESCAPE_UNENCLOSED_FIELD|String|\\\\|\\\\ ;; "
                + "STAGE_FILE_FORMAT|TRIM_SPACE|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|FIELD_OPTIONALLY_ENCLOSED_BY|String|NONE|NONE ;; "
                + "STAGE_FILE_FORMAT|NULL_IF|List|[\\\\N]|[\\\\N] ;; STAGE_FILE_FORMAT|COMPRESSION|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|ERROR_ON_COLUMN_COUNT_MISMATCH|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|VALIDATE_UTF8|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|SKIP_BLANK_LINES|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|REPLACE_INVALID_CHARACTERS|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|EMPTY_FIELD_AS_NULL|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|SKIP_BYTE_ORDER_MARK|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|ENCODING|String|UTF8|UTF8 ;; STAGE_FILE_FORMAT|MULTI_LINE|Boolean|true|true ;; "
                + "STAGE_COPY_OPTIONS|ON_ERROR|String|ABORT_STATEMENT|ABORT_STATEMENT ;; "
                + "STAGE_COPY_OPTIONS|SIZE_LIMIT|Long|| ;; STAGE_COPY_OPTIONS|PURGE|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|RETURN_FAILED_ONLY|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|ENFORCE_LENGTH|Boolean|true|true ;; "
                + "STAGE_COPY_OPTIONS|TRUNCATECOLUMNS|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|FORCE|Boolean|false|false ;; STAGE_LOCATION|URL|String||"},
            {"CREATE OR REPLACE TABLE ts805 (a INT) STAGE_COPY_OPTIONS = (ON_ERROR = CONTINUE SIZE_LIMIT = NULL PURGE "
                + "= TRUE)",
                "Table TS805 successfully created."},
            {"DESCRIBE TABLE ts805 TYPE = STAGE",
                "STAGE_FILE_FORMAT|TYPE|String|CSV|CSV ;; STAGE_FILE_FORMAT|RECORD_DELIMITER|String|\\n|\\n ;; "
                + "STAGE_FILE_FORMAT|FIELD_DELIMITER|String|,|, ;; STAGE_FILE_FORMAT|FILE_EXTENSION|String|| ;; "
                + "STAGE_FILE_FORMAT|SKIP_HEADER|Integer|0|0 ;; STAGE_FILE_FORMAT|PARSE_HEADER|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|DATE_FORMAT|String|AUTO|AUTO ;; STAGE_FILE_FORMAT|TIME_FORMAT|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|TIMESTAMP_FORMAT|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|BINARY_FORMAT|String|HEX|HEX ;; STAGE_FILE_FORMAT|ESCAPE|String|NONE|NONE ;; "
                + "STAGE_FILE_FORMAT|ESCAPE_UNENCLOSED_FIELD|String|\\\\|\\\\ ;; "
                + "STAGE_FILE_FORMAT|TRIM_SPACE|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|FIELD_OPTIONALLY_ENCLOSED_BY|String|NONE|NONE ;; "
                + "STAGE_FILE_FORMAT|NULL_IF|List|[\\\\N]|[\\\\N] ;; STAGE_FILE_FORMAT|COMPRESSION|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|ERROR_ON_COLUMN_COUNT_MISMATCH|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|VALIDATE_UTF8|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|SKIP_BLANK_LINES|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|REPLACE_INVALID_CHARACTERS|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|EMPTY_FIELD_AS_NULL|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|SKIP_BYTE_ORDER_MARK|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|ENCODING|String|UTF8|UTF8 ;; STAGE_FILE_FORMAT|MULTI_LINE|Boolean|true|true ;; "
                + "STAGE_COPY_OPTIONS|ON_ERROR|String|CONTINUE|ABORT_STATEMENT ;; STAGE_COPY_OPTIONS|SIZE_LIMIT|Long|| "
                + ";; STAGE_COPY_OPTIONS|PURGE|Boolean|true|false ;; "
                + "STAGE_COPY_OPTIONS|RETURN_FAILED_ONLY|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|ENFORCE_LENGTH|Boolean|true|true ;; "
                + "STAGE_COPY_OPTIONS|TRUNCATECOLUMNS|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|FORCE|Boolean|false|false ;; STAGE_LOCATION|URL|String||"},
            {"CREATE OR REPLACE TABLE ts805 (a INT) STAGE_COPY_OPTIONS = (NOPE = 1)",
                "REFUSED SQL compilation error: invalid copy option 'NOPE' | "},
            {"CREATE OR REPLACE TABLE ts805 (a INT) STAGE_COPY_OPTIONS = (SIZE_LIMIT)",
                "Table TS805 successfully created."},
            {"CREATE OR REPLACE TABLE ts805 (a INT) STAGE_COPY_OPTIONS = (ON_ERROR = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'ON_ERROR'"},
            {"ALTER TABLE ts805 SET STAGE_COPY_OPTIONS = (SIZE_LIMIT = NULL)",
                "Statement executed successfully."},
            {"ALTER TABLE ts805 SET STAGE_COPY_OPTIONS = (PURGE)",
                "Statement executed successfully."},
            {"DESCRIBE TABLE ts805 TYPE = STAGE",
                "STAGE_FILE_FORMAT|TYPE|String|CSV|CSV ;; STAGE_FILE_FORMAT|RECORD_DELIMITER|String|\\n|\\n ;; "
                + "STAGE_FILE_FORMAT|FIELD_DELIMITER|String|,|, ;; STAGE_FILE_FORMAT|FILE_EXTENSION|String|| ;; "
                + "STAGE_FILE_FORMAT|SKIP_HEADER|Integer|0|0 ;; STAGE_FILE_FORMAT|PARSE_HEADER|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|DATE_FORMAT|String|AUTO|AUTO ;; STAGE_FILE_FORMAT|TIME_FORMAT|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|TIMESTAMP_FORMAT|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|BINARY_FORMAT|String|HEX|HEX ;; STAGE_FILE_FORMAT|ESCAPE|String|NONE|NONE ;; "
                + "STAGE_FILE_FORMAT|ESCAPE_UNENCLOSED_FIELD|String|\\\\|\\\\ ;; "
                + "STAGE_FILE_FORMAT|TRIM_SPACE|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|FIELD_OPTIONALLY_ENCLOSED_BY|String|NONE|NONE ;; "
                + "STAGE_FILE_FORMAT|NULL_IF|List|[\\\\N]|[\\\\N] ;; STAGE_FILE_FORMAT|COMPRESSION|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|ERROR_ON_COLUMN_COUNT_MISMATCH|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|VALIDATE_UTF8|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|SKIP_BLANK_LINES|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|REPLACE_INVALID_CHARACTERS|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|EMPTY_FIELD_AS_NULL|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|SKIP_BYTE_ORDER_MARK|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|ENCODING|String|UTF8|UTF8 ;; STAGE_FILE_FORMAT|MULTI_LINE|Boolean|true|true ;; "
                + "STAGE_COPY_OPTIONS|ON_ERROR|String|ABORT_STATEMENT|ABORT_STATEMENT ;; "
                + "STAGE_COPY_OPTIONS|SIZE_LIMIT|Long|| ;; STAGE_COPY_OPTIONS|PURGE|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|RETURN_FAILED_ONLY|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|ENFORCE_LENGTH|Boolean|true|true ;; "
                + "STAGE_COPY_OPTIONS|TRUNCATECOLUMNS|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|FORCE|Boolean|false|false ;; STAGE_LOCATION|URL|String||"},
            {"CREATE OR REPLACE STAGE st805 COPY_OPTIONS = (SIZE_LIMIT = NULL)",
                "Stage area ST805 successfully created."},
            {"DESCRIBE STAGE st805",
                "STAGE_FILE_FORMAT|TYPE|String|CSV|CSV ;; STAGE_FILE_FORMAT|RECORD_DELIMITER|String|\\n|\\n ;; "
                + "STAGE_FILE_FORMAT|FIELD_DELIMITER|String|,|, ;; STAGE_FILE_FORMAT|FILE_EXTENSION|String|| ;; "
                + "STAGE_FILE_FORMAT|SKIP_HEADER|Integer|0|0 ;; STAGE_FILE_FORMAT|PARSE_HEADER|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|DATE_FORMAT|String|AUTO|AUTO ;; STAGE_FILE_FORMAT|TIME_FORMAT|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|TIMESTAMP_FORMAT|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|BINARY_FORMAT|String|HEX|HEX ;; STAGE_FILE_FORMAT|ESCAPE|String|NONE|NONE ;; "
                + "STAGE_FILE_FORMAT|ESCAPE_UNENCLOSED_FIELD|String|\\\\|\\\\ ;; "
                + "STAGE_FILE_FORMAT|TRIM_SPACE|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|FIELD_OPTIONALLY_ENCLOSED_BY|String|NONE|NONE ;; "
                + "STAGE_FILE_FORMAT|NULL_IF|List|[\\\\N]|[\\\\N] ;; STAGE_FILE_FORMAT|COMPRESSION|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|ERROR_ON_COLUMN_COUNT_MISMATCH|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|VALIDATE_UTF8|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|SKIP_BLANK_LINES|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|REPLACE_INVALID_CHARACTERS|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|EMPTY_FIELD_AS_NULL|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|SKIP_BYTE_ORDER_MARK|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|ENCODING|String|UTF8|UTF8 ;; STAGE_FILE_FORMAT|MULTI_LINE|Boolean|true|true ;; "
                + "STAGE_COPY_OPTIONS|ON_ERROR|String|ABORT_STATEMENT|ABORT_STATEMENT ;; "
                + "STAGE_COPY_OPTIONS|SIZE_LIMIT|Long|| ;; STAGE_COPY_OPTIONS|PURGE|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|RETURN_FAILED_ONLY|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|ENFORCE_LENGTH|Boolean|true|true ;; "
                + "STAGE_COPY_OPTIONS|TRUNCATECOLUMNS|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|FORCE|Boolean|false|false ;; STAGE_LOCATION|URL|String|| ;; "
                + "DIRECTORY|ENABLE|Boolean|false|false ;; DIRECTORY|AUTO_REFRESH|Boolean|false|false"},
        });
    }

    /** What an ALTER merges, clears and refuses, and other names given a list. */
    @Test
    public void mergesAndJudgedValues() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE ts808 (a INT) STAGE_COPY_OPTIONS = (SIZE_LIMIT = 5)",
                "Table TS808 successfully created."},
        });
        engine.execute("DESCRIBE TABLE ts808 TYPE = STAGE");
        assertCells(new String[][] {
            {"SELECT \"property\", \"property_value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE "
                + "\"parent_property\" = 'STAGE_COPY_OPTIONS' AND \"property\" IN ('ON_ERROR', 'SIZE_LIMIT', 'PURGE', "
                + "'MATCH_BY_COLUMN_NAME', 'ENFORCE_LENGTH') ORDER BY 1",
                "ENFORCE_LENGTH|true ;; ON_ERROR|ABORT_STATEMENT ;; PURGE|false ;; SIZE_LIMIT|5"},
            {"ALTER TABLE ts808 SET STAGE_COPY_OPTIONS = (SIZE_LIMIT = NULL)",
                "Statement executed successfully."},
        });
        engine.execute("DESCRIBE TABLE ts808 TYPE = STAGE");
        assertCells(new String[][] {
            {"SELECT \"property\", \"property_value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE "
                + "\"parent_property\" = 'STAGE_COPY_OPTIONS' AND \"property\" IN ('ON_ERROR', 'SIZE_LIMIT', 'PURGE', "
                + "'MATCH_BY_COLUMN_NAME', 'ENFORCE_LENGTH') ORDER BY 1",
                "ENFORCE_LENGTH|true ;; ON_ERROR|ABORT_STATEMENT ;; PURGE|false ;; SIZE_LIMIT|"},
            {"ALTER TABLE ts808 SET STAGE_COPY_OPTIONS = (PURGE = TRUE)",
                "Statement executed successfully."},
        });
        engine.execute("DESCRIBE TABLE ts808 TYPE = STAGE");
        assertCells(new String[][] {
            {"SELECT \"property\", \"property_value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE "
                + "\"parent_property\" = 'STAGE_COPY_OPTIONS' AND \"property\" IN ('ON_ERROR', 'SIZE_LIMIT', 'PURGE', "
                + "'MATCH_BY_COLUMN_NAME', 'ENFORCE_LENGTH') ORDER BY 1",
                "ENFORCE_LENGTH|true ;; ON_ERROR|ABORT_STATEMENT ;; PURGE|true ;; SIZE_LIMIT|"},
            {"ALTER TABLE ts808 SET STAGE_COPY_OPTIONS = (PURGE)",
                "Statement executed successfully."},
        });
        engine.execute("DESCRIBE TABLE ts808 TYPE = STAGE");
        assertCells(new String[][] {
            {"SELECT \"property\", \"property_value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE "
                + "\"parent_property\" = 'STAGE_COPY_OPTIONS' AND \"property\" IN ('ON_ERROR', 'SIZE_LIMIT', 'PURGE', "
                + "'MATCH_BY_COLUMN_NAME', 'ENFORCE_LENGTH') ORDER BY 1",
                "ENFORCE_LENGTH|true ;; ON_ERROR|ABORT_STATEMENT ;; PURGE|true ;; SIZE_LIMIT|"},
            {"ALTER TABLE ts808 SET STAGE_COPY_OPTIONS = (SIZE_LIMIT)",
                "Statement executed successfully."},
        });
        engine.execute("DESCRIBE TABLE ts808 TYPE = STAGE");
        assertCells(new String[][] {
            {"SELECT \"property\", \"property_value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE "
                + "\"parent_property\" = 'STAGE_COPY_OPTIONS' AND \"property\" IN ('ON_ERROR', 'SIZE_LIMIT', 'PURGE', "
                + "'MATCH_BY_COLUMN_NAME', 'ENFORCE_LENGTH') ORDER BY 1",
                "ENFORCE_LENGTH|true ;; ON_ERROR|ABORT_STATEMENT ;; PURGE|true ;; SIZE_LIMIT|"},
            {"ALTER TABLE ts808 SET STAGE_COPY_OPTIONS = (ON_ERROR = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'ON_ERROR'"},
            {"ALTER TABLE ts808 SET STAGE_COPY_OPTIONS = (PURGE = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'PURGE'"},
            {"ALTER TABLE ts808 SET STAGE_COPY_OPTIONS = (SIZE_LIMIT = 1.5)",
                "REFUSED SQL compilation error: | invalid value [1.5] for parameter 'SIZE_LIMIT'"},
            {"ALTER TABLE ts808 SET STAGE_COPY_OPTIONS = (PURGE, SIZE_LIMIT)",
                "Statement executed successfully."},
        });
        engine.execute("DESCRIBE TABLE ts808 TYPE = STAGE");
        assertCells(new String[][] {
            {"SELECT \"property\", \"property_value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE "
                + "\"parent_property\" = 'STAGE_COPY_OPTIONS' AND \"property\" IN ('ON_ERROR', 'SIZE_LIMIT', 'PURGE', "
                + "'MATCH_BY_COLUMN_NAME', 'ENFORCE_LENGTH') ORDER BY 1",
                "ENFORCE_LENGTH|true ;; ON_ERROR|ABORT_STATEMENT ;; PURGE|true ;; SIZE_LIMIT|"},
            {"ALTER TABLE ts808 SET STAGE_COPY_OPTIONS = (PURGE SIZE_LIMIT)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'SIZE_LIMIT'. | syntax "
                + "error line 1 at position 50 unexpected 'SIZE_LIMIT'. | syntax error line 1 at position 60 "
                + "unexpected ')'."},
            {"ALTER TABLE ts808 SET STAGE_COPY_OPTIONS = (ON_ERROR = CONTINUE PURGE)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 69 unexpected ')'."},
            {"ALTER TABLE ts808 SET STAGE_COPY_OPTIONS = (PURGE, SIZE_LIMIT = 5)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected '='. | syntax error "
                + "line 1 at position 65 unexpected ')'."},
            {"ALTER TABLE ts808 SET STAGE_COPY_OPTIONS = (PURGE SIZE_LIMIT = 5)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'SIZE_LIMIT'. | syntax "
                + "error line 1 at position 50 unexpected 'SIZE_LIMIT'. | syntax error line 1 at position 64 "
                + "unexpected ')'."},
            {"ALTER TABLE ts808 SET STAGE_COPY_OPTIONS = (NOPE)",
                "Statement executed successfully."},
            {"ALTER TABLE ts808 SET STAGE_COPY_OPTIONS = ()",
                "Statement executed successfully."},
        });
        engine.execute("DESCRIBE TABLE ts808 TYPE = STAGE");
        assertCells(new String[][] {
            {"SELECT \"property\", \"property_value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE "
                + "\"parent_property\" = 'STAGE_COPY_OPTIONS' AND \"property\" IN ('ON_ERROR', 'SIZE_LIMIT', 'PURGE', "
                + "'MATCH_BY_COLUMN_NAME', 'ENFORCE_LENGTH') ORDER BY 1",
                "ENFORCE_LENGTH|true ;; ON_ERROR|ABORT_STATEMENT ;; PURGE|true ;; SIZE_LIMIT|"},
            {"ALTER TABLE ts808 SET STAGE_COPY_OPTIONS = (SIZE_LIMIT = NULL) COMMENT = 'c'",
                "Statement executed successfully."},
            {"ALTER TABLE ts808 SET COMMENT = 'c' STAGE_COPY_OPTIONS = (SIZE_LIMIT = NULL)",
                "Statement executed successfully."},
            {"CREATE OR REPLACE TABLE ts808 (a INT) STAGE_COPY_OPTIONS = (SIZE_LIMIT = NULL, PURGE = TRUE)",
                "Table TS808 successfully created."},
        });
        engine.execute("DESCRIBE TABLE ts808 TYPE = STAGE");
        assertCells(new String[][] {
            {"SELECT \"property\", \"property_value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE "
                + "\"parent_property\" = 'STAGE_COPY_OPTIONS' AND \"property\" IN ('ON_ERROR', 'SIZE_LIMIT', 'PURGE', "
                + "'MATCH_BY_COLUMN_NAME', 'ENFORCE_LENGTH') ORDER BY 1",
                "ENFORCE_LENGTH|true ;; ON_ERROR|ABORT_STATEMENT ;; PURGE|true ;; SIZE_LIMIT|"},
            {"CREATE OR REPLACE TABLE ts808 (a INT) STAGE_COPY_OPTIONS = (ON_ERROR = CONTINUE PURGE)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 85 unexpected ')'."},
            {"CREATE OR REPLACE TABLE ts808 (a INT) STAGE_COPY_OPTIONS = (PURGE, SIZE_LIMIT)",
                "Table TS808 successfully created."},
        });
        engine.execute("DESCRIBE TABLE ts808 TYPE = STAGE");
        assertCells(new String[][] {
            {"SELECT \"property\", \"property_value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE "
                + "\"parent_property\" = 'STAGE_COPY_OPTIONS' AND \"property\" IN ('ON_ERROR', 'SIZE_LIMIT', 'PURGE', "
                + "'MATCH_BY_COLUMN_NAME', 'ENFORCE_LENGTH') ORDER BY 1",
                "ENFORCE_LENGTH|true ;; ON_ERROR|ABORT_STATEMENT ;; PURGE|false ;; SIZE_LIMIT|"},
            {"CREATE OR REPLACE TABLE ts808 (a INT) STAGE_COPY_OPTIONS = (PURGE SIZE_LIMIT)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 66 unexpected 'SIZE_LIMIT'. | syntax "
                + "error line 1 at position 66 unexpected 'SIZE_LIMIT'. | syntax error line 1 at position 76 "
                + "unexpected ')'."},
            {"CREATE OR REPLACE TABLE ts808 (a INT) STAGE_COPY_OPTIONS = (SIZE_LIMIT = 1e3)",
                "REFUSED SQL compilation error: | invalid value [1e3] for parameter 'SIZE_LIMIT'"},
            {"CREATE OR REPLACE TABLE ts808 (a INT) STAGE_COPY_OPTIONS = (PURGE = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'PURGE'"},
            {"CREATE OR REPLACE TABLE ts808 (a INT) STAGE_COPY_OPTIONS = (NOPE)",
                "Table TS808 successfully created."},
            {"CREATE OR REPLACE TABLE ts808 (a INT) STAGE_COPY_OPTIONS = (SIZE_LIMIT = 5 SIZE_LIMIT = NULL)",
                "REFUSED SQL compilation error: conflicting values for copy option 'SIZE_LIMIT' | "},
            {"CREATE OR REPLACE TABLE ts808 (a INT) STAGE_COPY_OPTIONS = (ON_ERROR = (1))",
                "REFUSED Invalid value specified for property 'ON_ERROR'"},
            {"CREATE OR REPLACE TABLE ts808 (a INT) STAGE_COPY_OPTIONS = (INCLUDE_METADATA = (a = b))",
                "REFUSED SQL compilation error: | invalid value [TOK_PROPERTY_LIST] for parameter 'INCLUDE_METADATA'"},
            {"CREATE OR REPLACE TABLE ts808 (a INT) STAGE_COPY_OPTIONS = (SIZE_LIMIT = NULL) COMMENT = 'x'",
                "Table TS808 successfully created."},
        });
        engine.execute("DESCRIBE TABLE ts808 TYPE = STAGE");
        assertCells(new String[][] {
            {"SELECT \"property\", \"property_value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE "
                + "\"parent_property\" = 'STAGE_COPY_OPTIONS' AND \"property\" IN ('ON_ERROR', 'SIZE_LIMIT', 'PURGE', "
                + "'MATCH_BY_COLUMN_NAME', 'ENFORCE_LENGTH') ORDER BY 1",
                "ENFORCE_LENGTH|true ;; ON_ERROR|ABORT_STATEMENT ;; PURGE|false ;; SIZE_LIMIT|"},
            {"CREATE OR REPLACE TABLE ts808 (a INT) STAGE_COPY_OPTIONS = (SIZE_LIMIT = NULL), COMMENT = 'x'",
                "Table TS808 successfully created."},
            {"CREATE OR REPLACE TABLE ts808 (a INT) STAGE_FILE_FORMAT = (TYPE)",
                "REFUSED SQL compilation error: | File format 'TOK_CONSTANT_LIST' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on FILE FORMAT "
                + "TOK_CONSTANT_LIST."},
            {"CREATE OR REPLACE TABLE ts808 (a INT) CHANGE_TRACKING = (A = 1)",
                "REFUSED SQL compilation error: | invalid value [TOK_PROPERTY_LIST] for parameter 'CHANGE_TRACKING'"},
            {"CREATE OR REPLACE TABLE ts808 (a INT) DATA_RETENTION_TIME_IN_DAYS = (PURGE)",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter "
                + "'DATA_RETENTION_TIME_IN_DAYS'"},
            {"CREATE OR REPLACE STAGE st808 COPY_OPTIONS = (SIZE_LIMIT = 5)",
                "Stage area ST808 successfully created."},
            {"DESCRIBE STAGE st808 ->> SELECT \"property\", \"property_value\" FROM $1 WHERE \"parent_property\" = "
                + "'STAGE_COPY_OPTIONS' AND \"property\" IN ('ON_ERROR', 'SIZE_LIMIT', 'PURGE') ORDER BY 1",
                "ON_ERROR|ABORT_STATEMENT ;; PURGE|false ;; SIZE_LIMIT|5"},
            {"ALTER STAGE st808 SET COPY_OPTIONS = (SIZE_LIMIT = NULL)",
                "Statement executed successfully."},
            {"DESCRIBE STAGE st808 ->> SELECT \"property\", \"property_value\" FROM $1 WHERE \"parent_property\" = "
                + "'STAGE_COPY_OPTIONS' AND \"property\" IN ('ON_ERROR', 'SIZE_LIMIT', 'PURGE') ORDER BY 1",
                "ON_ERROR|ABORT_STATEMENT ;; PURGE|false ;; SIZE_LIMIT|"},
            {"ALTER STAGE st808 SET COPY_OPTIONS = (PURGE = TRUE)",
                "Statement executed successfully."},
            {"DESCRIBE STAGE st808 ->> SELECT \"property\", \"property_value\" FROM $1 WHERE \"parent_property\" = "
                + "'STAGE_COPY_OPTIONS' AND \"property\" IN ('ON_ERROR', 'SIZE_LIMIT', 'PURGE') ORDER BY 1",
                "ON_ERROR|ABORT_STATEMENT ;; PURGE|true ;; SIZE_LIMIT|"},
        });
    }
}
