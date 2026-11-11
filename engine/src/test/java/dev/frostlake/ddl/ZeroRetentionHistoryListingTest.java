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
 * A version purged with a retention of 0 days is still listed by SHOW ... HISTORY, its dropped_on set, while UNDROP no
 * longer finds it (live-verified): a table replaced, a table dropped and a schema dropped alike.
 */
public class ZeroRetentionHistoryListingTest extends MeasuredCellsTestSupport {

    @Test
    public void zeroRetentionVersionsAreListedButNotUndropped() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE zr916 (a INT) DATA_RETENTION_TIME_IN_DAYS = 0",
                "Table ZR916 successfully created."},
            {"CREATE OR REPLACE TABLE zr916 (b INT)", "Table ZR916 successfully created."},
            {"SHOW TABLES HISTORY LIKE 'ZR916' ->> SELECT \"name\", \"dropped_on\" IS NOT NULL FROM $1 ORDER BY 2",
                "ZR916|false ;; ZR916|true"},
            {"CREATE OR REPLACE TABLE zd916 (a INT) DATA_RETENTION_TIME_IN_DAYS = 0",
                "Table ZD916 successfully created."},
            {"DROP TABLE zd916", "ZD916 successfully dropped."},
            {"SHOW TABLES HISTORY LIKE 'ZD916' ->> SELECT \"name\", \"dropped_on\" IS NOT NULL FROM $1 ORDER BY 2",
                "ZD916|true"},
            {"UNDROP TABLE zd916", "REFUSED Table ZD916 did not exist or was purged."},
            {"CREATE OR REPLACE SCHEMA zs916 DATA_RETENTION_TIME_IN_DAYS = 0", "Schema ZS916 successfully created."},
            {"DROP SCHEMA zs916", "ZS916 successfully dropped."},
            {"SHOW SCHEMAS HISTORY LIKE 'ZS916' ->> SELECT \"name\", \"dropped_on\" IS NOT NULL FROM $1", "ZS916|true"},
            {"UNDROP SCHEMA zs916", "REFUSED Schema ZS916 did not exist or was purged."},
            {"USE SCHEMA test_schema", "Statement executed successfully."},
        });
    }
}
