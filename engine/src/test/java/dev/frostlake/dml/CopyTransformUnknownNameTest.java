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

import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A COPY transformation's item that names what the staged fields cannot answer is refused as the statement compiles,
 * before any file is read, so ON_ERROR = CONTINUE loads nothing and reports no success over it; the names the staged
 * fields answer still load.
 */
public class CopyTransformUnknownNameTest extends MeasuredCellsTestSupport {

    @Test
    public void anUnknownNameIsRefusedBeforeAnyRow() {
        assumeFalse(isLiveSnowflake(), "the account's sentence and place for a COPY are unmeasured");
        engine.execute("CREATE STAGE ctu_st FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");
        engine.execute("COPY INTO @ctu_st/f.csv FROM (SELECT * FROM VALUES ('1', 'one')) "
            + "FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) HEADER = TRUE SINGLE = TRUE");
        engine.execute("CREATE TABLE ctu_t (a VARCHAR, b VARCHAR)");
        assertCells(new String[][] {
            {"COPY INTO ctu_t FROM (SELECT no_such_name FROM @ctu_st) ON_ERROR = CONTINUE",
                "REFUSED SQL compilation error: error line 1 at position 29 | invalid identifier 'NO_SUCH_NAME'"},
            {"SELECT COUNT(*) FROM ctu_t", "0"},
        });
        engine.execute("COPY INTO ctu_t FROM (SELECT $1, s.$2 FROM @ctu_st s)");
        assertCells(new String[][] {
            {"SELECT a, b FROM ctu_t", "1|one"},
        });
    }
}
