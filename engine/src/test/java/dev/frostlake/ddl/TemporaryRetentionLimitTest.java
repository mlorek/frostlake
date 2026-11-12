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
 * A TEMPORARY table keeps at most one day of Time Travel (documented: "For temporary and transient tables, the
 * retention period can be set to 0 or 1 day"). CREATE takes a longer DATA_RETENTION_TIME_IN_DAYS, up to the account's
 * 90 days, and keeps one day of it, where ALTER TABLE refuses one in the transient table's words and a TRANSIENT table
 * is refused at CREATE too (live-verified).
 */
public class TemporaryRetentionLimitTest extends MeasuredCellsTestSupport {

    private static final String SHOWN = "SHOW TABLES LIKE 'TRL' ->> SELECT \"kind\", \"retention_time\" FROM $1";

    @Test
    public void aTemporaryTableKeepsADayAtMost() {
        assertAnswer("Table TRL successfully created.",
            "CREATE OR REPLACE TEMPORARY TABLE trl (a INT) DATA_RETENTION_TIME_IN_DAYS = 2");
        assertAnswer("TEMPORARY|1", SHOWN);
        assertAnswer("REFUSED SQL compilation error: | invalid value [30] for parameter 'DATA_RETENTION_TIME_IN_DAYS'",
            "ALTER TABLE trl SET DATA_RETENTION_TIME_IN_DAYS = 30");
        assertAnswer("TEMPORARY|1", SHOWN);
        engine.execute("ALTER TABLE trl SET DATA_RETENTION_TIME_IN_DAYS = 0");
        assertAnswer("TEMPORARY|0", SHOWN);
        assertAnswer("REFUSED SQL compilation error: | Exceeds maximum allowable retention time (90 day(s)).",
            "CREATE OR REPLACE TEMPORARY TABLE trl (a INT) DATA_RETENTION_TIME_IN_DAYS = 91");
        engine.execute("CREATE OR REPLACE TEMPORARY TABLE trl (a INT) DATA_RETENTION_TIME_IN_DAYS = 90");
        assertAnswer("TEMPORARY|1", SHOWN);
        assertAnswer("REFUSED SQL compilation error: | invalid value [2] for parameter 'DATA_RETENTION_TIME_IN_DAYS'",
            "CREATE OR REPLACE TRANSIENT TABLE trt (a INT) DATA_RETENTION_TIME_IN_DAYS = 2");
    }
}
