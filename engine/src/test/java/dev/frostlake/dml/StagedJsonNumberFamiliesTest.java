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
 * A staged JSON file types its numbers as PARSE_JSON types them: one written with an exponent is a DOUBLE, and one
 * written plainly that no NUMBER can hold is a DOUBLE too, in a staged-file query and in a COPY into a VARIANT alike.
 */
public class StagedJsonNumberFamiliesTest extends MeasuredCellsTestSupport {

    @Test
    public void aStagedFileReadsItsNumbersAsParseJsonDoes() {
        assumeFalse(isLiveSnowflake(), "unmeasured on the live account for staged files");
        engine.execute("CREATE OR REPLACE FILE FORMAT jx_ff TYPE = JSON");
        engine.execute("CREATE OR REPLACE STAGE jy_st");
        engine.execute("COPY INTO @jy_st/k.json FROM (SELECT '{\"a\":1.5e0,\"b\":"
            + "123456789012345678901234567890123456789012,\"c\":1.5,\"d\":42}') FILE_FORMAT = (TYPE = CSV "
            + "COMPRESSION = NONE FIELD_OPTIONALLY_ENCLOSED_BY = NONE ESCAPE_UNENCLOSED_FIELD = NONE) SINGLE = TRUE");
        engine.execute("CREATE OR REPLACE TABLE jv (v VARIANT)");
        engine.execute("COPY INTO jv FROM @jy_st/k.json FILE_FORMAT = (FORMAT_NAME = 'jx_ff')");
        assertCells(new String[][] {
            {"SELECT TYPEOF($1:a), TYPEOF($1:b), TYPEOF($1:c), TYPEOF($1:d) FROM @jy_st/k.json "
                + "(FILE_FORMAT => 'jx_ff')", "DOUBLE|DOUBLE|DECIMAL|INTEGER"},
            {"SELECT TYPEOF(v:a), TYPEOF(v:b), TYPEOF(v:c), TYPEOF(v:d) FROM jv", "DOUBLE|DOUBLE|DECIMAL|INTEGER"},
            {"SELECT TYPEOF(PARSE_JSON('{\"a\":1.5e0,\"b\":123456789012345678901234567890123456789012}'):a), "
                + "TYPEOF(PARSE_JSON('{\"a\":1.5e0,\"b\":123456789012345678901234567890123456789012}'):b)",
                "DOUBLE|DOUBLE"},
        });
    }
}
