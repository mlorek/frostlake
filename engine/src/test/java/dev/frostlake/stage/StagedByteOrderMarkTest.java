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
 * A staged file's byte order mark: a CSV file's is passed over unless SKIP_BYTE_ORDER_MARK = FALSE reads it into
 * the first field, and a JSON file's is passed over whatever the option says (live-verified). A load reads the files
 * as a staged-file query does.
 */
public class StagedByteOrderMarkTest extends MeasuredCellsTestSupport {

    private static final String CSV = "﻿1,a\n2,b\n";
    private static final String JSON = "﻿{\"a\":1}";

    private void stageTheFiles() {
        engine.execute("CREATE OR REPLACE STAGE st_bom");
        stageLocalFile("st_bom", "bom.csv", CSV);
        stageLocalFile("st_bom", "plain.csv", "1,a\n2,b\n");
        stageLocalFile("st_bom", "bom.json", JSON);
        engine.execute("CREATE OR REPLACE FILE FORMAT f_bom TYPE = CSV");
        engine.execute("CREATE OR REPLACE FILE FORMAT f_bom_kept TYPE = CSV SKIP_BYTE_ORDER_MARK = FALSE");
        engine.execute("CREATE OR REPLACE FILE FORMAT fj_bom TYPE = JSON");
        engine.execute("CREATE OR REPLACE FILE FORMAT fj_bom_kept TYPE = JSON SKIP_BYTE_ORDER_MARK = FALSE");
    }

    @Test
    public void aCsvFilesMarkIsPassedOverUnlessTheFormatKeepsIt() {
        stageTheFiles();
        assertAnswer("1|1|49|a ;; 2|1|50|b",
            "SELECT $1, LENGTH($1), UNICODE($1), $2 FROM @st_bom/bom.csv (FILE_FORMAT => 'f_bom') ORDER BY $2");
        assertAnswer("﻿1|2|65279|a ;; 2|1|50|b",
            "SELECT $1, LENGTH($1), UNICODE($1), $2 FROM @st_bom/bom.csv (FILE_FORMAT => 'f_bom_kept') ORDER BY $2");
        assertAnswer("1|1 ;; 2|1", "SELECT $1, LENGTH($1) FROM @st_bom/plain.csv (FILE_FORMAT => 'f_bom') ORDER BY $2");
        assertAnswer("1|a ;; 2|b",
            "SELECT TRY_TO_NUMBER($1), $2 FROM @st_bom/bom.csv (FILE_FORMAT => 'f_bom') ORDER BY $2");
        assertAnswer("null|a ;; 2|b",
            "SELECT TRY_TO_NUMBER($1), $2 FROM @st_bom/bom.csv (FILE_FORMAT => 'f_bom_kept') ORDER BY $2");
    }

    @Test
    public void aJsonFilesMarkIsPassedOverEitherWay() {
        stageTheFiles();
        assertAnswer("{\"a\":1}", "SELECT $1 FROM @st_bom/bom.json (FILE_FORMAT => 'fj_bom')");
        assertAnswer("{\"a\":1}", "SELECT $1 FROM @st_bom/bom.json (FILE_FORMAT => 'fj_bom_kept')");
        assertAnswer("1", "SELECT $1:a FROM @st_bom/bom.json (FILE_FORMAT => 'fj_bom')");
    }

    @Test
    public void theOptionIsListedWithItsDefault() {
        stageTheFiles();
        engine.execute("DESCRIBE FILE FORMAT f_bom");
        assertAnswer("SKIP_BYTE_ORDER_MARK|true|true", "SELECT \"property\", \"property_value\", \"property_default\" "
            + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"property\" = 'SKIP_BYTE_ORDER_MARK'");
        engine.execute("DESCRIBE FILE FORMAT fj_bom_kept");
        assertAnswer("SKIP_BYTE_ORDER_MARK|false|true", "SELECT \"property\", \"property_value\", \"property_default\" "
            + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"property\" = 'SKIP_BYTE_ORDER_MARK'");
    }

    /** A load reads both files as the query reads them; the account's COPY is not run here. */
    @Test
    public void aLoadPassesTheMarksOverAsTheQueryDoes() {
        if (isLiveSnowflake()) {
            return;
        }
        stageTheFiles();
        engine.execute("CREATE OR REPLACE TABLE t_bom (id INT, s VARCHAR)");
        engine.execute("COPY INTO t_bom FROM @st_bom/bom.csv FILE_FORMAT = (FORMAT_NAME = 'f_bom')");
        assertAnswer("1|a ;; 2|b", "SELECT id, s FROM t_bom ORDER BY s");
        engine.execute("CREATE OR REPLACE TABLE j_bom (v VARIANT)");
        engine.execute("COPY INTO j_bom FROM @st_bom/bom.json FILE_FORMAT = (FORMAT_NAME = 'fj_bom')");
        assertAnswer("{\"a\":1}", "SELECT v FROM j_bom");
    }
}
