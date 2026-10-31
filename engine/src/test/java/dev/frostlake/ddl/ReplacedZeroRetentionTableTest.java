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
 * A table replaced while it kept its data for no day is still listed by SHOW TABLES HISTORY, dropped and with the
 * rows it had, while UNDROP no longer finds it (live-verified).
 */
public class ReplacedZeroRetentionTableTest extends MeasuredCellsTestSupport {

    @Test
    public void theVersionIsListedButNotRestored() {
        assertAnswer("Table T9 successfully created.",
            "CREATE OR REPLACE TABLE t9 (a INT) DATA_RETENTION_TIME_IN_DAYS = 0");
        assertAnswer("1", "INSERT INTO t9 VALUES (1)");
        assertAnswer("Table T9 successfully created.", "CREATE OR REPLACE TABLE t9 (b INT)");
        answer("SHOW TABLES HISTORY LIKE 'T9'");
        assertAnswer("T9|true|0 ;; T9|false|1", "SELECT \"name\", \"dropped_on\" IS NULL, \"rows\" FROM "
            + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 2 DESC, 3");
        assertAnswer("Statement executed successfully.", "ALTER TABLE t9 RENAME TO t9n");
        assertAnswer("REFUSED Table T9 did not exist or was purged.", "UNDROP TABLE t9");
    }
}
