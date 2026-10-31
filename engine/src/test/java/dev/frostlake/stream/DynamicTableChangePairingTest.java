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
 * A dynamic table's changes, read through CHANGES and through a stream on it, by row identity: an updated row is a
 * DELETE and an INSERT with METADATA$ISUPDATE TRUE, a row deleted and inserted again is both (all live-verified).
 */
public class DynamicTableChangePairingTest extends MeasuredCellsTestSupport {

    @Test
    public void updatesPairByIdentity() {
        engine.execute("CREATE OR REPLACE TABLE dtb2490 (a INT)");
        engine.execute("INSERT INTO dtb2490 VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE dt2490 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS "
            + "SELECT a FROM dtb2490");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE dt2490b TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS "
            + "SELECT a * 10 AS b FROM dtb2490 WHERE a > 0");
        engine.execute("CREATE OR REPLACE STREAM sdt2490 ON DYNAMIC TABLE dt2490");
        assertAnswer("Statement executed successfully.", "SET ts2490a = CURRENT_TIMESTAMP()");
        assertAnswer("1|0", "UPDATE dtb2490 SET a = 10 WHERE a = 1");
        engine.execute("ALTER DYNAMIC TABLE dt2490 REFRESH");
        engine.execute("ALTER DYNAMIC TABLE dt2490b REFRESH");
        assertAnswer("Statement executed successfully.", "SET ts2490b = CURRENT_TIMESTAMP()");
        assertAnswer("1", "DELETE FROM dtb2490 WHERE a = 2");
        engine.execute("ALTER DYNAMIC TABLE dt2490 REFRESH");
        engine.execute("ALTER DYNAMIC TABLE dt2490b REFRESH");
        assertAnswer("1|DELETE|true ;; 10|INSERT|true",
            "SELECT a, METADATA$ACTION, METADATA$ISUPDATE FROM dt2490 CHANGES (INFORMATION => DEFAULT) AT (TIMESTAMP "
                + "=> $ts2490a) END (TIMESTAMP => $ts2490b) ORDER BY 1, 2");
        assertAnswer("1|DELETE|true ;; 2|DELETE|false ;; 10|INSERT|true",
            "SELECT a, METADATA$ACTION, METADATA$ISUPDATE FROM dt2490 CHANGES (INFORMATION => DEFAULT) AT (TIMESTAMP "
                + "=> $ts2490a) ORDER BY 1, 2");
        assertAnswer("10|DELETE|true ;; 100|INSERT|true",
            "SELECT b, METADATA$ACTION, METADATA$ISUPDATE FROM dt2490b CHANGES (INFORMATION => DEFAULT) AT "
                + "(TIMESTAMP => $ts2490a) END (TIMESTAMP => $ts2490b) ORDER BY 1, 2");
        assertAnswer("10|DELETE|true ;; 20|DELETE|false ;; 100|INSERT|true",
            "SELECT b, METADATA$ACTION, METADATA$ISUPDATE FROM dt2490b CHANGES (INFORMATION => DEFAULT) AT "
                + "(TIMESTAMP => $ts2490a) ORDER BY 1, 2");
        assertAnswer("1|DELETE|true ;; 2|DELETE|false ;; 10|INSERT|true",
            "SELECT a, METADATA$ACTION, METADATA$ISUPDATE FROM sdt2490 ORDER BY 1, 2");
        assertAnswer("Statement executed successfully.", "SET ts2490c = CURRENT_TIMESTAMP()");
        assertAnswer("1|0", "UPDATE dtb2490 SET a = a WHERE a = 3");
        engine.execute("ALTER DYNAMIC TABLE dt2490 REFRESH");
        engine.execute("ALTER DYNAMIC TABLE dt2490b REFRESH");
        assertAnswer("",
            "SELECT a, METADATA$ACTION, METADATA$ISUPDATE FROM dt2490 CHANGES (INFORMATION => DEFAULT) AT (TIMESTAMP "
                + "=> $ts2490c) ORDER BY 1, 2");
        assertAnswer("Statement executed successfully.", "SET ts2490d = CURRENT_TIMESTAMP()");
        assertAnswer("1", "DELETE FROM dtb2490 WHERE a = 3");
        assertAnswer("1", "INSERT INTO dtb2490 VALUES (3)");
        engine.execute("ALTER DYNAMIC TABLE dt2490 REFRESH");
        engine.execute("ALTER DYNAMIC TABLE dt2490b REFRESH");
        assertAnswer("3|DELETE|false ;; 3|INSERT|false",
            "SELECT a, METADATA$ACTION, METADATA$ISUPDATE FROM dt2490 CHANGES (INFORMATION => DEFAULT) AT (TIMESTAMP "
                + "=> $ts2490d) ORDER BY 1, 2");
        assertAnswer("30|DELETE|false ;; 30|INSERT|false",
            "SELECT b, METADATA$ACTION, METADATA$ISUPDATE FROM dt2490b CHANGES (INFORMATION => DEFAULT) AT "
                + "(TIMESTAMP => $ts2490d) ORDER BY 1, 2");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
