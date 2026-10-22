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

package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A CTAS or an INSERT whose query converts a value its column cannot hold is refused inside the write's DML
 * envelope, naming the table and the column; a UNION that converts while it deduplicates, and a query alone,
 * are refused with the bare sentence (all live-verified).
 */
public class CtasConversionEnvelopeTest extends MeasuredCellsTestSupport {

    @Test
    public void envelopes() {
        engine.execute("CREATE OR REPLACE TABLE tst (id INT)");
        engine.execute("INSERT INTO tst VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE tsd (d DATE)");
        engine.execute("INSERT INTO tsd VALUES ('2024-01-01')");
        assertAnswer("REFUSED DML operation to table TEST_DB.TEST_SCHEMA.TSW failed on column ID with error: Numeric "
                + "value '1e39' is not recognized",
            "CREATE OR REPLACE TABLE tsw AS SELECT id FROM tst UNION ALL SELECT '1e39'");
        assertAnswer("REFUSED DML operation to table TEST_DB.TEST_SCHEMA.TSW2 failed on column ID with error: Numeric "
                + "value '1e39' is not recognized",
            "CREATE OR REPLACE TABLE tsw2 AS SELECT '1e39'::NUMBER AS id");
        assertAnswer("REFUSED DML operation to table TEST_DB.TEST_SCHEMA.TSW3 failed on column D with error: Date 'x' "
                + "is not recognized",
            "CREATE OR REPLACE TABLE tsw3 AS SELECT d FROM tsd UNION ALL SELECT 'x'");
        assertAnswer("REFUSED DML operation to table TEST_DB.TEST_SCHEMA.TSW4 failed on column ID with error: Numeric "
                + "value 'x' is not recognized",
            "CREATE OR REPLACE TABLE tsw4 AS SELECT TO_NUMBER('x') AS id");
        assertAnswer("REFUSED DML operation to table TEST_DB.TEST_SCHEMA.TSW5 failed on column ID with error: Numeric "
                + "value 'abc' is not recognized",
            "CREATE OR REPLACE TABLE tsw5 AS SELECT id FROM tst UNION ALL SELECT 'abc'");
        assertAnswer("REFUSED DML operation to table TST failed on column ID with error: Numeric value '1e39' is not "
                + "recognized",
            "INSERT INTO tst SELECT id FROM tst UNION ALL SELECT '1e39'");
        assertAnswer("REFUSED DML operation to table TEST_DB.TEST_SCHEMA.TSW6 failed on column ID with error: Numeric "
                + "value 'x' is not recognized",
            "CREATE OR REPLACE TABLE tsw6 (id INT) AS SELECT 'x'");
        assertAnswer("REFUSED Numeric value '1e39' is not recognized",
            "CREATE OR REPLACE TABLE tsw7 AS SELECT id FROM tst UNION SELECT '1e39'");
        assertAnswer("REFUSED DML operation to table TEST_DB.TEST_SCHEMA.TSW8 failed on column ID with error: Numeric "
                + "value '1e39' is not recognized",
            "CREATE OR REPLACE TEMPORARY TABLE tsw8 AS SELECT id FROM tst UNION ALL SELECT '1e39'");
        assertAnswer("REFUSED Numeric value '1e39' is not recognized", "SELECT id FROM tst UNION ALL SELECT '1e39'");
        assertAnswer("REFUSED DML operation to table TEST_DB.TEST_SCHEMA.TSW9 failed on column ID with error: Numeric "
                + "value 'x' is not recognized",
            "CREATE OR REPLACE TABLE tsw9 AS SELECT id FROM tst UNION ALL SELECT 'x'");
        assertAnswer("REFUSED DML operation to table TEST_DB.TEST_SCHEMA.TSW10 failed on column ID with error: "
                + "Numeric value 'x' is not recognized",
            "CREATE OR REPLACE TABLE tsw10 AS SELECT CAST('x' AS INT) AS id");
        assertAnswer("REFUSED DML operation to table TEST_DB.TEST_SCHEMA.TSW11 failed on column D with error: Date "
                + "'2024-13-45' is not recognized",
            "CREATE OR REPLACE TABLE tsw11 AS SELECT d FROM tsd UNION ALL SELECT '2024-13-45'");
    }
}
