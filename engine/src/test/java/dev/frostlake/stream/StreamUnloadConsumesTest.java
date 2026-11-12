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
 * An unload of a stream consumes it, as a DML does ("a COPY INTO location transaction", documented): an autocommit
 * COPY INTO @stage FROM (SELECT … FROM stream) moves the stream past what it wrote, as one inside a transaction does at
 * its COMMIT. The autocommit unload left the stream where it was, so the next unload exported the same changes again.
 */
public class StreamUnloadConsumesTest extends MeasuredCellsTestSupport {

    @Test
    public void anUnloadMovesTheStreamOn() {
        engine.execute("CREATE OR REPLACE TABLE suc_t (id INT, v VARCHAR)");
        engine.execute("CREATE OR REPLACE STREAM suc_s ON TABLE suc_t");
        engine.execute("CREATE OR REPLACE STAGE suc_st");
        engine.execute("INSERT INTO suc_t VALUES (1, 'a'), (2, 'b')");
        engine.execute("COPY INTO @suc_st/x FROM (SELECT id, v, METADATA$ACTION FROM suc_s) "
            + "FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
        assertAnswer("0", "SELECT COUNT(*) FROM suc_s");
        assertAnswer("1|a|INSERT ;; 2|b|INSERT", "SELECT $1, $2, $3 FROM @suc_st/x ORDER BY 1");
        engine.execute("INSERT INTO suc_t VALUES (3, 'c')");
        engine.execute("BEGIN");
        engine.execute("COPY INTO @suc_st/y FROM (SELECT id, v, METADATA$ACTION FROM suc_s) "
            + "FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
        engine.execute("COMMIT");
        assertAnswer("0", "SELECT COUNT(*) FROM suc_s");
        assertAnswer("3|c|INSERT", "SELECT $1, $2, $3 FROM @suc_st/y");
    }
}
