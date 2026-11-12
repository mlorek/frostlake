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
 * A staged file's five metadata columns (documented, "Querying metadata for staged files"): beside METADATA$FILENAME
 * and METADATA$FILE_ROW_NUMBER, METADATA$FILE_CONTENT_KEY, a checksum of the file, METADATA$FILE_LAST_MODIFIED, when
 * it was last modified, and METADATA$START_SCAN_TIME, when the scan began. Each reads in a staged-file query and in a
 * COPY transformation; the three were invalid identifiers.
 */
public class StageMetadataColumnsTest extends MeasuredCellsTestSupport {

    private void stageTwoFiles() {
        engine.execute("CREATE STAGE smc_st");
        engine.execute("COPY INTO @smc_st/f1.csv FROM (SELECT * FROM VALUES ('1', 'one'), ('2', 'two')) "
            + "FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
        engine.execute("COPY INTO @smc_st/f2.csv FROM (SELECT * FROM VALUES ('3', 'three')) "
            + "FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
    }

    @Test
    public void aStagedFileQueryReadsEveryColumn() {
        stageTwoFiles();
        assertAnswer("f1.csv|1|true|true|true|1 ;; f1.csv|2|true|true|true|2 ;; f2.csv|1|true|true|true|3",
            "SELECT METADATA$FILENAME, METADATA$FILE_ROW_NUMBER, METADATA$FILE_CONTENT_KEY IS NOT NULL, "
                + "METADATA$FILE_LAST_MODIFIED IS NOT NULL, METADATA$START_SCAN_TIME IS NOT NULL, $1 FROM @smc_st "
                + "ORDER BY 1, 2");
        // The key is the file's: one for each file's records, and another for another file.
        assertAnswer("f1.csv|1 ;; f2.csv|1", "SELECT METADATA$FILENAME, COUNT(DISTINCT METADATA$FILE_CONTENT_KEY) "
            + "FROM @smc_st GROUP BY 1 ORDER BY 1");
        assertAnswer("2", "SELECT COUNT(DISTINCT METADATA$FILE_CONTENT_KEY) FROM @smc_st");
    }

    @Test
    public void aCopyTransformationReadsThem() {
        stageTwoFiles();
        engine.execute("CREATE TABLE smc_t (f STRING, k STRING, m TIMESTAMP_NTZ, s TIMESTAMP_LTZ, a STRING)");
        engine.execute("COPY INTO smc_t FROM (SELECT METADATA$FILENAME, METADATA$FILE_CONTENT_KEY, "
            + "METADATA$FILE_LAST_MODIFIED, METADATA$START_SCAN_TIME, $1 FROM @smc_st) FILE_FORMAT = (TYPE = CSV)");
        assertAnswer("f1.csv|true|true|true|1 ;; f1.csv|true|true|true|2 ;; f2.csv|true|true|true|3",
            "SELECT f, k IS NOT NULL, m IS NOT NULL, s IS NOT NULL, a FROM smc_t ORDER BY a");
        assertAnswer("2", "SELECT COUNT(DISTINCT k) FROM smc_t");
    }
}
