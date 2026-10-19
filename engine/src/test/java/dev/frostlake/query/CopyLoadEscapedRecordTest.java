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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * A COPY load reads a record as a staged-file query of the same file does: an escaped line break carries the record
 * on into the next line, and ESCAPE takes the next character literally inside an enclosure, in a load and in a
 * validation alike. The values are the ones live's staged-file query answers for the same files; embedded only,
 * since COPY INTO is not run on the live account.
 */
public class CopyLoadEscapedRecordTest extends MeasuredCellsTestSupport {

    @Override
    protected void setupTest() {
        Assumptions.assumeFalse(isLiveSnowflake(), "COPY INTO is not run on the live account");
    }

    @Test
    public void aLoadReadsRecordsAsAStagedFileQueryDoes() {
        engine.execute("CREATE OR REPLACE STAGE lst");
        stageLocalFile("lst", "nl.csv", "one,two\\\nthree\nfour,five\n");
        stageLocalFile("lst", "esc.csv", "\"a!\"b\",x\n\"c!!d\",y\n");
        engine.execute("CREATE OR REPLACE TABLE lt (a VARCHAR, b VARCHAR)");
        engine.execute("COPY INTO lt FROM @lst/nl.csv FILE_FORMAT = (TYPE = CSV)");
        assertCells(new String[][] {
            {"SELECT a, b FROM lt ORDER BY 1", "four|five ;; one|two | three"},
        });
        engine.execute("TRUNCATE TABLE lt");
        engine.execute("COPY INTO lt FROM @lst/esc.csv FILE_FORMAT = (TYPE = CSV FIELD_OPTIONALLY_ENCLOSED_BY = "
            + "'\"' ESCAPE = '!')");
        assertCells(new String[][] {
            {"SELECT a, b FROM lt ORDER BY 2", "a\"b|x ;; c!d|y"},
        });
        engine.execute("TRUNCATE TABLE lt");
        engine.execute("COPY INTO lt FROM @lst/nl.csv FILE_FORMAT = (TYPE = CSV) VALIDATION_MODE = RETURN_ALL_ERRORS");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM lt", "0"},
        });
    }
}
