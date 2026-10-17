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
 * A staged JSON file is read by its format's STRIP_OUTER_ARRAY and STRIP_NULL_VALUES (live-verified): by default a
 * top-level array is one record, the whole array, and under STRIP_OUTER_ARRAY = TRUE each element is one;
 * STRIP_NULL_VALUES = TRUE drops each null-valued field at every depth and leaves a hole, undefined, for each null
 * array element.
 */
public class StagedJsonStripOptionTest extends MeasuredCellsTestSupport {

    @Test
    public void aJsonFileIsReadByItsStripOptions() {
        engine.execute("CREATE OR REPLACE STAGE jst");
        stageLocalFile("jst", "arr.json", "[{\"a\":1,\"b\":null},{\"a\":2}]\n");
        stageLocalFile("jst", "nulls.json", "{\"a\":1,\"b\":null,\"c\":[1,null,2]}\n");
        stageLocalFile("jst", "nested.json", "[[1,2],[3]]\n");
        engine.execute("CREATE OR REPLACE FILE FORMAT jf TYPE = JSON");
        engine.execute("CREATE OR REPLACE FILE FORMAT jfs TYPE = JSON STRIP_OUTER_ARRAY = TRUE");
        engine.execute("CREATE OR REPLACE FILE FORMAT jfn TYPE = JSON STRIP_NULL_VALUES = TRUE");
        engine.execute("CREATE OR REPLACE FILE FORMAT jfb TYPE = JSON STRIP_OUTER_ARRAY = TRUE STRIP_NULL_VALUES = "
            + "TRUE");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM @jst/arr.json (FILE_FORMAT => 'jf')", "1"},
            {"SELECT $1 FROM @jst/arr.json (FILE_FORMAT => 'jf')", "[{\"a\":1,\"b\":null},{\"a\":2}]"},
            {"SELECT $1 FROM @jst/arr.json (FILE_FORMAT => 'jfs') ORDER BY $1:a", "{\"a\":1,\"b\":null} ;; {\"a\":2}"},
            {"SELECT $1 FROM @jst/nulls.json (FILE_FORMAT => 'jf')", "{\"a\":1,\"b\":null,\"c\":[1,null,2]}"},
            {"SELECT $1 FROM @jst/nulls.json (FILE_FORMAT => 'jfn')", "{\"a\":1,\"c\":[1,undefined,2]}"},
            {"SELECT $1 FROM @jst/arr.json (FILE_FORMAT => 'jfb') ORDER BY $1:a", "{\"a\":1} ;; {\"a\":2}"},
            {"SELECT $1 FROM @jst/arr.json (FILE_FORMAT => 'jfn')", "[{\"a\":1},{\"a\":2}]"},
            {"SELECT $1 FROM @jst/nested.json (FILE_FORMAT => 'jfs') ORDER BY 1", "[1,2] ;; [3]"},
            {"SELECT COUNT(*) FROM @jst/nested.json (FILE_FORMAT => 'jf')", "1"},
            {"SELECT METADATA$FILE_ROW_NUMBER, $1:a FROM @jst/arr.json (FILE_FORMAT => 'jfs') ORDER BY 1",
                "1|1 ;; 2|2"},
        });
    }
}
