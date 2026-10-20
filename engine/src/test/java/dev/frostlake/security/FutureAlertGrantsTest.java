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
package dev.frostlake.security;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The future grants on a schema's alerts are a new alert's (documented: a future grant applies to the objects of its
 * kind created after it). Engine only: GRANT is not run on the live account.
 */
public class FutureAlertGrantsTest extends BaseDatabaseTest {

    @Test
    public void aNewAlertTakesTheFutureGrantsOnAlerts() {
        assumeFalse(isLiveSnowflake(), "GRANT is not run on the live account");
        engine.execute("CREATE ROLE fa_r");
        engine.execute("GRANT OPERATE ON FUTURE ALERTS IN SCHEMA test_db.test_schema TO ROLE fa_r");
        engine.execute("CREATE ALERT fa_al WAREHOUSE = COMPUTE_WH SCHEDULE = '60 MINUTE'"
            + " IF (EXISTS (SELECT 1)) THEN SELECT 1");
        final List<String> grants = new ArrayList<>();
        for (final Row row : engine.executeQuery("SHOW GRANTS ON ALERT fa_al").getRows()) {
            grants.add(row.getValue(1) + "|" + row.getValue(2) + "|" + row.getValue(3) + "|" + row.getValue(5));
        }
        assertTrue(grants.contains("OPERATE|ALERT|TEST_DB.TEST_SCHEMA.FA_AL|FA_R"), grants.toString());
    }
}
