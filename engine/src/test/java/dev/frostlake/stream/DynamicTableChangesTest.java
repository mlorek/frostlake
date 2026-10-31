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
 * A CHANGES clause over a dynamic table: the rows its refreshes added and removed between two points, an INSERT
 * and a DELETE, APPEND_ONLY and a point CURRENT_TIMESTAMP names refused in the account's words, a point before
 * the table's first refresh refused as one its history does not reach, and the table's base table tracking its
 * changes from the table's creation (all live-verified).
 */
public class DynamicTableChangesTest extends MeasuredCellsTestSupport {

    @Test
    public void changesAfterAnInsert() {
        engine.execute("CREATE OR REPLACE TABLE dtb2073 (a INT)");
        engine.execute("INSERT INTO dtb2073 VALUES (1)");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE dt2073 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS "
            + "SELECT a FROM dtb2073");
        assertAnswer("1", "INSERT INTO dtb2073 VALUES (2)");
        assertAnswer("Statement executed successfully.", "SET ts2073 = CURRENT_TIMESTAMP()");
        engine.execute("ALTER DYNAMIC TABLE dt2073 REFRESH");
        assertAnswer("2|INSERT|false",
            "SELECT a, METADATA$ACTION, METADATA$ISUPDATE FROM dt2073 CHANGES (INFORMATION => DEFAULT) AT (TIMESTAMP "
                + "=> $ts2073) ORDER BY a");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking of type APPEND_ONLY is "
                + "not supported on queries with 'DYNAMIC_TABLE' inside views, saw 'DT2073'. (inside a view).",
            "SELECT a, METADATA$ACTION FROM dt2073 CHANGES (INFORMATION => APPEND_ONLY) AT (TIMESTAMP => $ts2073)");
        assertAnswer("1", "SELECT COUNT(*) FROM dt2073 CHANGES (INFORMATION => DEFAULT) BEFORE (TIMESTAMP => $ts2073)");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 105: | Query contains time travel to a "
                + "version containing function 'CURRENT_TIMESTAMP', but change tracking is not supported on queries "
                + "with non-deterministic functions in versions. (inside a view).",
            "SELECT COUNT(*) FROM dt2073 CHANGES (INFORMATION => DEFAULT) AT (TIMESTAMP => $ts2073) END (TIMESTAMP "
                + "=> CURRENT_TIMESTAMP())");
        assertAnswer("1", "SELECT a FROM dt2073 AT (TIMESTAMP => $ts2073) ORDER BY a");
    }

    @Test
    public void changesAfterAnUpdateAndADelete() {
        engine.execute("CREATE OR REPLACE TABLE dtb2073 (a INT)");
        engine.execute("INSERT INTO dtb2073 VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE dt2073 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS "
            + "SELECT a FROM dtb2073");
        assertAnswer("Statement executed successfully.", "SET ts2073a = CURRENT_TIMESTAMP()");
        assertAnswer("1|0", "UPDATE dtb2073 SET a = 10 WHERE a = 1");
        engine.execute("ALTER DYNAMIC TABLE dt2073 REFRESH");
        assertAnswer("Statement executed successfully.", "SET ts2073b = CURRENT_TIMESTAMP()");
        assertAnswer("1", "DELETE FROM dtb2073 WHERE a = 2");
        engine.execute("ALTER DYNAMIC TABLE dt2073 REFRESH");
        assertAnswer("2|DELETE|false",
            "SELECT a, METADATA$ACTION, METADATA$ISUPDATE FROM dt2073 CHANGES (INFORMATION => DEFAULT) AT (TIMESTAMP "
                + "=> $ts2073b) ORDER BY a, 2");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 78: | Query contains time travel to a version "
                + "containing function 'CURRENT_TIMESTAMP', but change tracking is not supported on queries with "
                + "non-deterministic functions in versions. (inside a view).",
            "SELECT COUNT(*) FROM dt2073 CHANGES (INFORMATION => DEFAULT) AT (TIMESTAMP => CURRENT_TIMESTAMP())");
        assertAnswer("REFUSED Time travel data is not available for table DT2073. The requested time is either beyond "
                + "the allowed time travel period or before the object creation time.",
            "SELECT COUNT(*) FROM dt2073 CHANGES (INFORMATION => DEFAULT) AT (TIMESTAMP => DATEADD(day, -1, "
                + "$ts2073a::TIMESTAMP_LTZ))");
        assertAnswer("3", "SELECT COUNT(*) FROM dtb2073 CHANGES (INFORMATION => DEFAULT) AT (TIMESTAMP => $ts2073a)");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking of type APPEND_ONLY is "
                + "not supported on queries with 'DYNAMIC_TABLE' inside views, saw 'DT2073'. (inside a view).",
            "SELECT COUNT(*) FROM dt2073 CHANGES (INFORMATION => APPEND_ONLY) AT (TIMESTAMP => $ts2073a)");
        assertAnswer("2|false",
            "SELECT a, METADATA$ROW_ID IS NULL FROM dt2073 CHANGES (INFORMATION => DEFAULT) AT (TIMESTAMP => "
                + "$ts2073b) ORDER BY a");
    }
}
