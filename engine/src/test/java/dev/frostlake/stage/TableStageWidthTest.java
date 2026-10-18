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

import org.junit.jupiter.api.Test;

/**
 * A CSV query over a table's stage reads the table's columns (live-verified): {@code $2} past a one-column table is an
 * invalid identifier, and each record is held to the table's width as a load holds it — the file's first record of
 * another width is {@code Number of columns in file (F) does not match that of the corresponding table (T), …}, a
 * later one holding more fields {@code Field delimiter '|' found while expecting record delimiter '\n'}, each naming
 * the file, the place, the row and the column — unless the format sets ERROR_ON_COLUMN_COUNT_MISMATCH = FALSE. A star
 * reads a named stage's CSV fields not at all: a bare star is {@code SELECT with no columns}, and ORDER BY 1 names no
 * column.
 */
public class TableStageWidthTest extends MeasuredCellsTestSupport {

    @Test
    public void aTableStageReadsItsTablesWidth() {
        engine.execute("CREATE OR REPLACE TABLE t3 (a INT) STAGE_FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|')");
        stageLocalFile("%t3", "p1.csv", "1|a\n2|b\n");
        engine.execute("CREATE OR REPLACE TABLE t7 (a INT, b VARCHAR)");
        stageLocalFile("%t7", "p1.csv", "1|a\n2|b\n");
        engine.execute("CREATE OR REPLACE TABLE t5 (a INT, b VARCHAR) STAGE_FILE_FORMAT = (TYPE = CSV "
            + "FIELD_DELIMITER = '|')");
        stageLocalFile("%t5", "p1.csv", "1|a\n2|b\n");
        engine.execute("CREATE OR REPLACE TABLE t8 (a INT, b VARCHAR) STAGE_FILE_FORMAT = (TYPE = CSV "
            + "FIELD_DELIMITER = '|')");
        stageLocalFile("%t8", "p1.csv", "1|a\n2|b|c\n");
        engine.execute("CREATE OR REPLACE TABLE t9 (a INT, b VARCHAR, c VARCHAR)");
        stageLocalFile("%t9", "p1.csv", "1,a\n");
        engine.execute("CREATE OR REPLACE STAGE stn");
        stageLocalFile("stn", "n1.csv", "1,a\n2,b\n");
        engine.execute("CREATE OR REPLACE STAGE stx FILE_FORMAT = (TYPE = XML)");
        stageLocalFile("stx", "x1.xml", "<r><a>1</a></r>");
        assertCells(new String[][] {
            {"SELECT $1, $2 FROM @%t3",
                "REFUSED SQL compilation error: error line 1 at position 11 | invalid identifier '$2'"},
            {"SELECT $1 FROM @%t3 ORDER BY 1",
                "REFUSED Number of columns in file (2) does not match that of the corresponding table (1), use "
                + "file format option error_on_column_count_mismatch=false to ignore this error |   File "
                + "'@T3/p1.csv', line 2, character 1 |   Row 1 starts at line 1, column \"T3\"[2]"},
            {"SELECT $1, $2 FROM @%t7",
                "REFUSED Number of columns in file (1) does not match that of the corresponding table (2), use "
                + "file format option error_on_column_count_mismatch=false to ignore this error |   File "
                + "'@T7/p1.csv', line 2, character 1 |   Row 1 starts at line 1, column \"T7\"[\"A\":1]"},
            {"SELECT $1 FROM @%t7",
                "REFUSED Number of columns in file (1) does not match that of the corresponding table (2), use "
                + "file format option error_on_column_count_mismatch=false to ignore this error |   File "
                + "'@T7/p1.csv', line 2, character 1 |   Row 1 starts at line 1, column \"T7\"[\"A\":1]"},
            {"SELECT COUNT(*) FROM @%t7",
                "REFUSED Number of columns in file (1) does not match that of the corresponding table (2), use "
                + "file format option error_on_column_count_mismatch=false to ignore this error |   File "
                + "'@T7/p1.csv', line 2, character 1 |   Row 1 starts at line 1, column \"T7\"[\"A\":1]"},
            {"SELECT $1, $2 FROM @%t5 (FILE_FORMAT => NULL)",
                "REFUSED Number of columns in file (1) does not match that of the corresponding table (2), use "
                + "file format option error_on_column_count_mismatch=false to ignore this error |   File "
                + "'@T5/p1.csv', line 2, character 1 |   Row 1 starts at line 1, column \"T5\"[\"A\":1]"},
            {"SELECT $1, $2 FROM @%t5 ORDER BY 1", "1|a ;; 2|b"},
            {"SELECT $3 FROM @%t5",
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '$3'"},
            {"SELECT $1, $2 FROM @%t8 ORDER BY 1",
                "REFUSED Field delimiter '|' found while expecting record delimiter '\\n' |   File '@T8/p1.csv', "
                + "line 2, character 4 |   Row 2, column \"T8\"[\"B\":2]"},
            {"SELECT $1 FROM @%t9",
                "REFUSED Number of columns in file (2) does not match that of the corresponding table (3), use "
                + "file format option error_on_column_count_mismatch=false to ignore this error |   File "
                + "'@T9/p1.csv', line 1, character 4 |   Row 1, column \"T9\"[\"B\":2]"},
            {"SELECT * FROM @%t5", "1|a ;; 2|b"},
            {"SELECT * FROM @stn",
                "REFUSED SQL compilation error: error line 1 at position 0 | SELECT with no columns"},
            {"SELECT * FROM @stn ORDER BY 1",
                "REFUSED SQL compilation error: | [1] is not a valid order by expression"},
            {"SELECT $1 FROM @stn ORDER BY 1", "1 ;; 2"},
            {"SELECT $1, $2 FROM @%t7 (FILE_FORMAT => NULL)",
                "REFUSED Number of columns in file (1) does not match that of the corresponding table (2), use "
                + "file format option error_on_column_count_mismatch=false to ignore this error |   File "
                + "'@T7/p1.csv', line 2, character 1 |   Row 1 starts at line 1, column \"T7\"[\"A\":1]"},
            {"SELECT METADATA$FILENAME, $1 FROM @%t5 ORDER BY 2", "@T5/p1.csv|1 ;; @T5/p1.csv|2"},
            {"SELECT $2 FROM @%t3",
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '$2'"},
            {"SELECT $1 FROM @%t3 (FILE_FORMAT => NULL) ORDER BY 1", "1|a ;; 2|b"},
            {"SELECT t.$1 FROM @%t5 t ORDER BY 1", "1 ;; 2"},
            {"SELECT * FROM @stn (FILE_FORMAT => NULL)",
                "REFUSED SQL compilation error: error line 1 at position 0 | SELECT with no columns"},
            {"SELECT COUNT(*) FROM @%t8",
                "REFUSED Field delimiter '|' found while expecting record delimiter '\\n' |   File '@T8/p1.csv', "
                + "line 2, character 4 |   Row 2, column \"T8\"[\"B\":2]"},
            {"SELECT $1 FROM @%t8 ORDER BY 1",
                "REFUSED Field delimiter '|' found while expecting record delimiter '\\n' |   File '@T8/p1.csv', "
                + "line 2, character 4 |   Row 2, column \"T8\"[\"B\":2]"},
        });
    }

    @Test
    public void aStarAndAFormatThatTurnsTheCheckOff() {
        engine.execute("CREATE OR REPLACE STAGE stn");
        stageLocalFile("stn", "n1.csv", "1,a\n2,b\n");
        engine.execute("CREATE OR REPLACE STAGE stj FILE_FORMAT = (TYPE = JSON)");
        stageLocalFile("stj", "j1.json", "{\"a\":1}");
        engine.execute("CREATE OR REPLACE TABLE t7 (a INT, b VARCHAR)");
        stageLocalFile("%t7", "p1.csv", "1|a\n2|b\n");
        engine.execute("CREATE OR REPLACE FILE FORMAT ffx TYPE = CSV ERROR_ON_COLUMN_COUNT_MISMATCH = FALSE");
        engine.execute("CREATE OR REPLACE FILE FORMAT ffp TYPE = CSV FIELD_DELIMITER = '|' "
            + "ERROR_ON_COLUMN_COUNT_MISMATCH = FALSE");
        engine.execute("CREATE OR REPLACE TABLE t9 (a INT)");
        stageLocalFile("%t9", "p1.csv", "1|a|b\n2|c|d\n");
        assertCells(new String[][] {
            {"SELECT * FROM @stj", "{\"a\":1}"},
            {"SELECT s.* FROM @stn s",
                "REFUSED SQL compilation error: error line 1 at position 0 | SELECT with no columns"},
            {"SELECT *, $1 FROM @stn ORDER BY 1", "1 ;; 2"},
            {"SELECT $1, $2 FROM @%t7 (FILE_FORMAT => 'ffx') ORDER BY 1", "1|a|null ;; 2|b|null"},
            {"SELECT $1, $2 FROM @%t7 (FILE_FORMAT => 'ffp') ORDER BY 1", "1|a ;; 2|b"},
            {"SELECT * FROM @%t7 (FILE_FORMAT => 'ffp') ORDER BY 1", "1|a ;; 2|b"},
            {"SELECT $1 FROM @%t9 (FILE_FORMAT => 'ffp') ORDER BY 1", "1 ;; 2"},
            {"SELECT * FROM @%t9 (FILE_FORMAT => 'ffp') ORDER BY 1", "1 ;; 2"},
            {"SELECT $2 FROM @%t9 (FILE_FORMAT => 'ffp')",
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '$2'"},
            {"SELECT COUNT(*) FROM @%t9", "2"},
            {"SELECT $1 FROM @stn ORDER BY 1", "1 ;; 2"},
        });
    }
}
