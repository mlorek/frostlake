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

package dev.frostlake.dml;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A COPY transformation reads the staged file's metadata columns as a staged-file query does: METADATA$FILENAME, the
 * file's path beneath the stage, and METADATA$FILE_ROW_NUMBER, the record's number in it (documented, "Querying
 * metadata for staged files"). Both were unknown to the transformation and refused each row, which ON_ERROR = CONTINUE
 * turned into a load of nothing.
 */
public class CopyTransformationMetadataTest extends MeasuredCellsTestSupport {

    @Test
    public void aCsvLoadReadsTheFileNameAndTheRowNumber() {
        engine.execute("CREATE STAGE ctm_st FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");
        engine.execute("COPY INTO @ctm_st/f1.csv.gz FROM (SELECT * FROM VALUES ('1', 'one'), ('2', 'two') ORDER BY 1) "
            + "FILE_FORMAT = (TYPE = CSV COMPRESSION = GZIP) HEADER = TRUE SINGLE = TRUE");
        assertAnswer("f1.csv.gz|1|1 ;; f1.csv.gz|2|2",
            "SELECT METADATA$FILENAME, METADATA$FILE_ROW_NUMBER, $1 FROM @ctm_st ORDER BY 2");
        engine.execute("CREATE TABLE ctm_home (fname VARCHAR, rn INT, id INT)");
        engine.execute("COPY INTO ctm_home FROM (SELECT METADATA$FILENAME, METADATA$FILE_ROW_NUMBER, t.$1 "
            + "FROM @ctm_st t)");
        assertAnswer("f1.csv.gz|1|1 ;; f1.csv.gz|2|2", "SELECT * FROM ctm_home ORDER BY rn");
        engine.execute("CREATE TABLE ctm_home2 (fname VARCHAR, id INT)");
        engine.execute("COPY INTO ctm_home2 FROM (SELECT METADATA$FILENAME, $1 FROM @ctm_st) ON_ERROR = CONTINUE");
        assertAnswer("f1.csv.gz|1 ;; f1.csv.gz|2", "SELECT * FROM ctm_home2 ORDER BY id");
    }

    @Test
    public void aJsonLoadReadsTheFileNameAndTheRecordNumber() {
        engine.execute("CREATE STAGE ctm_js FILE_FORMAT = (TYPE = JSON)");
        engine.execute("COPY INTO @ctm_js/d.json FROM (SELECT OBJECT_CONSTRUCT('k', column1) FROM VALUES (1), (2) "
            + "ORDER BY column1) FILE_FORMAT = (TYPE = JSON COMPRESSION = NONE) SINGLE = TRUE");
        engine.execute("CREATE TABLE ctm_json (fname VARCHAR, rn INT, k INT)");
        engine.execute("COPY INTO ctm_json FROM (SELECT METADATA$FILENAME, METADATA$FILE_ROW_NUMBER, $1:k "
            + "FROM @ctm_js)");
        assertAnswer("d.json|1|1 ;; d.json|2|2", "SELECT * FROM ctm_json ORDER BY k");
    }
}
