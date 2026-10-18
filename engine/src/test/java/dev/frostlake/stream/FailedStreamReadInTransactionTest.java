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

package dev.frostlake.stream;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A DML that reads a stream and fails inside an explicit transaction leaves the stream as it was (live-verified):
 * the failed statement's changes are rolled back, its read of the stream among them, so the COMMIT of the
 * transaction's other work does not advance the stream past changes no statement consumed. A DML that reads the
 * stream and succeeds advances it at COMMIT, as before.
 */
public class FailedStreamReadInTransactionTest extends MeasuredCellsTestSupport {

    @Test
    public void aFailedReadDoesNotAdvanceTheStream() {
        engine.execute("CREATE OR REPLACE TABLE stt (id INT)");
        engine.execute("CREATE OR REPLACE STREAM sts ON TABLE stt");
        engine.execute("INSERT INTO stt VALUES (0), (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE sink (id INT)");
        assertCells(new String[][] {
            {"BEGIN", "Statement executed successfully."},
            {"INSERT INTO sink SELECT 1/id FROM sts",
                "REFUSED DML operation to table SINK failed on column ID with error: Division by zero"},
            {"SELECT COUNT(*) FROM sts", "3"},
            {"INSERT INTO sink VALUES (7)", "1"},
            {"COMMIT", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM sts", "3"},
            {"SELECT COUNT(*) FROM sink", "1"},
            {"BEGIN", "Statement executed successfully."},
            {"COMMIT", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM sts", "3"},
            {"BEGIN", "Statement executed successfully."},
            {"INSERT INTO sink SELECT id FROM sts WHERE id > 0", "2"},
            {"SELECT COUNT(*) FROM sts", "3"},
            {"COMMIT", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM sts", "0"},
            {"SELECT id FROM sink ORDER BY 1", "1 ;; 2 ;; 7"},
        });
    }
}
