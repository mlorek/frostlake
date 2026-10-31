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
 * A table CREATE OR REPLACE replaces is kept as a dropped version, as DROP keeps one: SHOW TABLES HISTORY lists it
 * with its rows, and once its name is free UNDROP brings back the most recent version - a permanent, a transient
 * and a temporary table's, and one a CTAS or a CLONE replaced, alike - while CREATE OR ALTER keeps none and time
 * travel past the replace is refused (all live-verified).
 */
public class ReplacedTableUndropTest extends MeasuredCellsTestSupport {

    @Test
    public void aReplacedTableComesBackByUndrop() {
        assertAnswer("Table T6 successfully created.", "CREATE OR REPLACE TABLE t6 (a INT)");
        assertAnswer("2", "INSERT INTO t6 VALUES (1), (9)");
        assertAnswer("Table T6 successfully created.", "CREATE OR REPLACE TABLE t6 (b INT)");
        assertAnswer("1", "INSERT INTO t6 VALUES (5)");
        engine.execute("SHOW TABLES HISTORY LIKE 'T6'");
        assertAnswer("T6|true|1 ;; T6|false|2",
            "SELECT \"name\", \"dropped_on\" IS NULL, \"rows\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 2 "
                + "DESC, 3");
        assertAnswer("REFUSED SQL compilation error: | Object 'T6' already exists.", "UNDROP TABLE t6");
        assertAnswer("Statement executed successfully.", "ALTER TABLE t6 RENAME TO t6_new");
        assertAnswer("Table T6 successfully restored.", "UNDROP TABLE t6");
        assertAnswer("1 ;; 9", "SELECT * FROM t6 ORDER BY 1");
        assertShape("A:NUMBER(38,0)", "SELECT * FROM t6");
        assertAnswer("5", "SELECT * FROM t6_new");
        assertAnswer("Table T7 successfully created.", "CREATE OR REPLACE TABLE t7 (a INT)");
        assertAnswer("1", "INSERT INTO t7 VALUES (1)");
        assertAnswer("Table T7 successfully created.", "CREATE OR REPLACE TABLE t7 (b INT)");
        assertAnswer("1", "INSERT INTO t7 VALUES (2)");
        assertAnswer("Table T7 successfully created.", "CREATE OR REPLACE TABLE t7 (c INT)");
        assertAnswer("1", "INSERT INTO t7 VALUES (3)");
        engine.execute("SHOW TABLES HISTORY LIKE 'T7'");
        assertAnswer("T7|true|1 ;; T7|false|1 ;; T7|false|1",
            "SELECT \"name\", \"dropped_on\" IS NULL, \"rows\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 2 "
                + "DESC, 3");
        assertAnswer("Statement executed successfully.", "ALTER TABLE t7 RENAME TO t7c");
        assertAnswer("Table T7 successfully restored.", "UNDROP TABLE t7");
        assertAnswer("2", "SELECT * FROM t7");
        assertAnswer("Statement executed successfully.", "ALTER TABLE t7 RENAME TO t7b");
        assertAnswer("Table T7 successfully restored.", "UNDROP TABLE t7");
        assertAnswer("1", "SELECT * FROM t7");
        assertAnswer("Statement executed successfully.", "ALTER TABLE t7 RENAME TO t7a");
        assertAnswer("REFUSED Table T7 did not exist or was purged.", "UNDROP TABLE t7");
        assertAnswer("Table T8 successfully created.", "CREATE OR REPLACE TABLE t8 AS SELECT 1 AS a");
        assertAnswer("Table T8 successfully created.", "CREATE OR REPLACE TABLE t8 AS SELECT 2 AS b");
        assertAnswer("Statement executed successfully.", "ALTER TABLE t8 RENAME TO t8n");
        assertAnswer("Table T8 successfully restored.", "UNDROP TABLE t8");
        assertAnswer("1", "SELECT * FROM t8");
        assertAnswer("Table T9 successfully created.",
            "CREATE OR REPLACE TABLE t9 (a INT) DATA_RETENTION_TIME_IN_DAYS = 0");
        assertAnswer("1", "INSERT INTO t9 VALUES (1)");
        assertAnswer("Table T9 successfully created.", "CREATE OR REPLACE TABLE t9 (b INT)");
        engine.execute("SHOW TABLES HISTORY LIKE 'T9'");
        assertAnswer("Statement executed successfully.", "ALTER TABLE t9 RENAME TO t9n");
        assertAnswer("REFUSED Table T9 did not exist or was purged.", "UNDROP TABLE t9");
        assertAnswer("Table T10 successfully created.", "CREATE OR REPLACE TRANSIENT TABLE t10 (a INT)");
        assertAnswer("1", "INSERT INTO t10 VALUES (1)");
        assertAnswer("Table T10 successfully created.", "CREATE OR REPLACE TRANSIENT TABLE t10 (b INT)");
        assertAnswer("Statement executed successfully.", "ALTER TABLE t10 RENAME TO t10n");
        assertAnswer("Table T10 successfully restored.", "UNDROP TABLE t10");
        assertAnswer("1", "SELECT * FROM t10");
        assertAnswer("Table T11 successfully created.", "CREATE OR REPLACE TABLE t11 (a INT)");
        assertAnswer("1", "INSERT INTO t11 VALUES (1)");
        assertAnswer("Table T11 successfully created.", "CREATE OR REPLACE TABLE t11 CLONE t6");
        assertAnswer("Statement executed successfully.", "ALTER TABLE t11 RENAME TO t11n");
        assertAnswer("Table T11 successfully restored.", "UNDROP TABLE t11");
        assertAnswer("1", "SELECT * FROM t11");
        assertAnswer("Table T12 successfully created.", "CREATE OR REPLACE TABLE t12 (a INT)");
        assertAnswer("1", "INSERT INTO t12 VALUES (1)");
        assertAnswer("Table T12 successfully created.", "CREATE OR REPLACE TABLE t12 (b INT)");
        assertAnswer("T12 successfully dropped.", "DROP TABLE t12");
        assertAnswer("Table T12 successfully restored.", "UNDROP TABLE t12");
        assertAnswer("", "SELECT * FROM t12");
        assertAnswer("T12 successfully dropped.", "DROP TABLE t12");
        assertAnswer("Table T12 successfully restored.", "UNDROP TABLE t12");
        assertAnswer("", "SELECT * FROM t12");
        assertAnswer("Table T13 successfully created.", "CREATE OR REPLACE TEMPORARY TABLE t13 (a INT)");
        assertAnswer("1", "INSERT INTO t13 VALUES (1)");
        assertAnswer("Table T13 successfully created.", "CREATE OR REPLACE TEMPORARY TABLE t13 (b INT)");
        assertAnswer("Statement executed successfully.", "ALTER TABLE t13 RENAME TO t13n");
        assertAnswer("Table T13 successfully restored.", "UNDROP TABLE t13");
        assertAnswer("Table T14 successfully created.", "CREATE OR REPLACE TABLE t14 (a INT)");
        assertAnswer("1", "INSERT INTO t14 VALUES (1)");
        assertAnswer("Statement executed successfully.", "CREATE OR ALTER TABLE t14 (a INT, b INT)");
        engine.execute("SHOW TABLES HISTORY LIKE 'T14'");
        assertAnswer("T14|true|1",
            "SELECT \"name\", \"dropped_on\" IS NULL, \"rows\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 2 "
                + "DESC, 3");
        assertAnswer("Table T15 successfully created.", "CREATE OR REPLACE TABLE t15 (a INT)");
        assertAnswer("1", "INSERT INTO t15 VALUES (1)");
        assertAnswer("Table T15 successfully created.", "CREATE OR REPLACE TABLE t15 (b INT)");
        assertAnswer("REFUSED Time travel data is not available for table T15. The requested time is either beyond "
                + "the allowed time travel period or before the object creation time.",
            "SELECT COUNT(*) FROM t15 AT(OFFSET => -1)");
    }
}
