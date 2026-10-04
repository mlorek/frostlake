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

package dev.frostlake.features;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SHOW PARAMETERS reports the zone the engine runs in. A session that sets none runs in UTC, and the parameter row
 * used to say America/Los_Angeles, the documented default, with an empty level. The account sets UTC here, as
 * qa-develop's does (live-verified: value UTC, default America/Los_Angeles, level ACCOUNT, IN SESSION and IN ACCOUNT
 * alike).
 */
public class TimezoneIsTheAccountsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** The value, default and level cells of the parameter's row. */
    private List<Object> timezone(final String show) {
        final Row row = engine.executeQuery(show).getRows().get(0);
        return List.of(row.getValue(1), row.getValue(2), row.getValue(3));
    }

    @Test
    public void anUnsetSessionReadsTheAccountsUtc() {
        final List<Object> account = List.of("UTC", "America/Los_Angeles", "ACCOUNT");
        assertEquals(account, timezone("SHOW PARAMETERS LIKE 'TIMEZONE'"));
        assertEquals(account, timezone("SHOW PARAMETERS LIKE 'TIMEZONE' IN SESSION"));
        assertEquals(account, timezone("SHOW PARAMETERS LIKE 'TIMEZONE' IN ACCOUNT"));
        assertTrue(String.valueOf(engine.executeQuery("SELECT TO_VARCHAR(CURRENT_TIMESTAMP()) t").getRows().get(0)
            .getValue(0)).endsWith("Z"), "and UTC is the zone in force");
    }

    @Test
    public void aSessionSettingIsTheSessionsAndUnsetReturnsToTheAccount() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/New_York'");
        assertEquals(List.of("America/New_York", "America/Los_Angeles", "SESSION"),
            timezone("SHOW PARAMETERS LIKE 'TIMEZONE' IN SESSION"));
        assertEquals(List.of("UTC", "America/Los_Angeles", "ACCOUNT"),
            timezone("SHOW PARAMETERS LIKE 'TIMEZONE' IN ACCOUNT"), "the account's own setting is unchanged");
        engine.execute("ALTER SESSION UNSET TIMEZONE");
        assertEquals(List.of("UTC", "America/Los_Angeles", "ACCOUNT"), timezone("SHOW PARAMETERS LIKE 'TIMEZONE'"));
    }

    @Test
    public void inUserWithNoNameListsTheSessionsOwnUser() {
        final String user = String.valueOf(engine.executeQuery("SELECT CURRENT_USER()").getRows().get(0).getValue(0));
        assertEquals(List.of("UTC", "America/Los_Angeles", "ACCOUNT"),
            timezone("SHOW PARAMETERS LIKE 'TIMEZONE' IN USER"), "live answers it for the current user");
        assertEquals(timezone("SHOW PARAMETERS LIKE 'TIMEZONE' IN USER " + user),
            timezone("SHOW PARAMETERS LIKE 'TIMEZONE' IN USER"));
    }

    @Test
    public void aParameterTheAccountLeavesAloneKeepsAnEmptyLevel() {
        assertEquals(List.of("0", "0", ""), timezone("SHOW PARAMETERS LIKE 'WEEK_START' IN ACCOUNT"));
        engine.execute("ALTER SESSION SET WEEK_START = 1");
        assertEquals(List.of("0", "0", ""), timezone("SHOW PARAMETERS LIKE 'WEEK_START' IN ACCOUNT"),
            "IN ACCOUNT leaves the session's settings out");
    }
}
