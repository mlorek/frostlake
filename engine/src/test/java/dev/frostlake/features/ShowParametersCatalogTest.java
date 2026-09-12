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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

/**
 * SHOW PARAMETERS lists every parameter the account knows, not only the ones the engine has opinions
 * about. Frostlake used to carry twelve hand-written rows, so asking for a thirteenth returned NO ROWS —
 * a tool reading a parameter it had never heard of got nothing rather than the default.
 *
 * <p>★ THE ROWS ARE A TRANSCRIPTION, and the test asserts them cell by cell because none of it is
 * derivable: WEEK_START defaults to 0, LOCK_TIMEOUT to 43200, QUERY_TAG to the empty string, and the
 * descriptions are Snowflake's own prose — several lines long, trailing spaces and all.
 *
 * <p>★ THE COUNT IS DELIBERATELY NOT ASSERTED. An account with a different edition lists a different
 * number of parameters, and pinning 160 would make this test a report on the account rather than on the
 * engine (the same trap #325 recorded for system compute pools).
 */
public class ShowParametersCatalogTest extends BaseDatabaseTest {

    private Row soleRow(final String parameter) {
        final ResultSet rs = engine.executeQuery("SHOW PARAMETERS LIKE '" + parameter + "'");
        assertEquals(1, rs.getRows().size(), parameter + " should be listed exactly once");
        return rs.getRows().get(0);
    }

    private void assertRow(final String parameter, final String value, final String defaultValue,
                           final String level, final String type) {
        final Row row = soleRow(parameter);
        assertEquals(parameter, String.valueOf(row.getValue(0)));
        assertEquals(value, String.valueOf(row.getValue(1)));
        assertEquals(defaultValue, String.valueOf(row.getValue(2)));
        assertEquals(level, String.valueOf(row.getValue(3)));
        assertEquals(type, String.valueOf(row.getValue(5)));
    }

    @Test
    public void aParameterTheEngineHasNoOpinionAboutIsStillListed() {
        assertRow("WEEK_START", "0", "0", "", "NUMBER");
        assertEquals("Defines the first day of the week:\n"
            + "0: legacy Snowflake behavior; 1: Monday .. 7: Sunday.",
            String.valueOf(soleRow("WEEK_START").getValue(4)));
    }

    @Test
    public void settingItMovesTheValueAndTheLevelButNotTheDefault() {
        engine.execute("ALTER SESSION SET WEEK_START = 1");
        try {
            assertRow("WEEK_START", "1", "0", "SESSION", "NUMBER");
        } finally {
            engine.execute("ALTER SESSION UNSET WEEK_START");
        }
        // UNSET puts the default back and blanks the level again.
        assertRow("WEEK_START", "0", "0", "", "NUMBER");
    }

    @Test
    public void theTypeWordIsOneOfThreeAndTheDefaultsAreTheAccountsOwn() {
        assertRow("AUTOCOMMIT", "true", "true", "", "BOOLEAN");
        assertRow("BINARY_OUTPUT_FORMAT", "HEX", "HEX", "", "STRING");
        assertRow("LOCK_TIMEOUT", "43200", "43200", "", "NUMBER");
        assertRow("TIMEZONE", "America/Los_Angeles", "America/Los_Angeles", "", "STRING");
        // An empty default is empty, not null — QUERY_TAG starts unset.
        assertRow("QUERY_TAG", "", "", "", "STRING");
    }

    @Test
    public void aLikePatternMatchesEveryParameterItNames() {
        final ResultSet week = engine.executeQuery("SHOW PARAMETERS LIKE '%WEEK%'");
        assertEquals(2, week.getRows().size());
        assertEquals("WEEK_OF_YEAR_POLICY", String.valueOf(week.getRows().get(0).getValue(0)));
        assertEquals("WEEK_START", String.valueOf(week.getRows().get(1).getValue(0)));
        assertEquals(0, engine.executeQuery("SHOW PARAMETERS LIKE 'NO_SUCH_PARAMETER'").getRows().size());
    }

    @Test
    public void theDescriptionKeepsTheAccountsOwnLineBreaks() {
        final String autocommit = String.valueOf(soleRow("AUTOCOMMIT").getValue(4));
        assertEquals("The autocommit property determines whether is statement should to be implicitly\n"
            + "wrapped within a transaction or not. If autocommit is set to true, then a \n"
            + "statement that requires a transaction is executed within a transaction \n"
            + "implicitly. If autocommit is off then an explicit commit or rollback is required\n"
            + "to close a transaction. The default autocommit value is true.", autocommit);
    }
}
