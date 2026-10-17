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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A COPY load refuses a number too wide for its column as an INSERT of the value is refused — {@code Numeric value
 * '12345' is out of range} into a NUMBER(2,0) — so the record is skipped under ON_ERROR = CONTINUE and the load
 * aborted under ABORT_STATEMENT, where the number was written. The INSERT's sentences are live-verified; embedded
 * only, since COPY INTO is not run on the live account.
 */
public class CopyLoadNumberRangeTest extends MeasuredCellsTestSupport {

    @Override
    protected void setupTest() {
        Assumptions.assumeFalse(isLiveSnowflake(), "COPY INTO is not run on the live account");
    }

    @Test
    public void aNumberTooWideForItsColumnIsTheRecordsRefusal() {
        engine.execute("CREATE OR REPLACE STAGE nst");
        stageLocalFile("nst", "n.csv", "12345\n99\n");
        engine.execute("CREATE OR REPLACE TABLE tn (id NUMBER(2, 0))");
        assertCells(new String[][] {
            {"INSERT INTO tn VALUES ('12345')",
                "REFUSED DML operation to table TN failed on column ID with error: Numeric value '12345' is out of "
                + "range"},
        });
        engine.execute("COPY INTO tn FROM @nst/n.csv FILE_FORMAT = (TYPE = CSV) ON_ERROR = CONTINUE");
        assertCells(new String[][] {
            {"SELECT id FROM tn ORDER BY 1", "99"},
        });
        engine.execute("TRUNCATE TABLE tn");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM tn", "0"},
        });
        final String aborted = answer("COPY INTO tn FROM @nst/n.csv FILE_FORMAT = (TYPE = CSV) FORCE = TRUE "
            + "ON_ERROR = ABORT_STATEMENT");
        assertTrue(aborted.startsWith("REFUSED ")
            && aborted.contains("Numeric value '12345' is out of range"), aborted);
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM tn", "0"},
        });
    }
}
