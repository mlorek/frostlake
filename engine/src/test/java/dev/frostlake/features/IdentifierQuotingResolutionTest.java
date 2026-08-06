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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A column reference resolves to a name and then has to MATCH one exactly, live-verified. Written
 * bare it folds to upper case; written in double quotes it is taken verbatim. So a column created as
 * {@code "lower"} is reachable only as {@code "lower"}, and one created bare is reachable bare or as
 * its upper-case quoted spelling — never as a lower-case quoted one.
 */
public class IdentifierQuotingResolutionTest extends BaseDatabaseTest {

    @BeforeEach
    public void createMixedCaseTable() {
        engine.execute("CREATE TABLE mixed (\"lower\" INTEGER, UPPER_COL INTEGER, \"MiXeD\" INTEGER)");
        engine.execute("INSERT INTO mixed VALUES (1, 2, 3)");
    }

    private void resolves(final String reference) {
        assertNotNull(engine.executeQuery("SELECT " + reference + " FROM mixed"),
            reference + " should resolve");
    }

    /**
     * {@code reportedAs} is the name a BARE reference is reported under, upper-cased the way it
     * resolved. A QUOTED reference that misses is reported under its upper-cased spelling too, where
     * a real account echoes it verbatim, quotes and all — the reference does not carry its quoting
     * this far, so pass null to check only that it was refused.
     */
    private void rejected(final String reference, final String reportedAs) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT " + reference + " FROM mixed");
            }
        }, reference + " should not resolve");
        assertTrue(e.getMessage().contains("invalid identifier"), "got: " + e.getMessage());
        if (reportedAs != null) {
            assertTrue(e.getMessage().contains("invalid identifier '" + reportedAs + "'"),
                "got: " + e.getMessage());
        }
    }

    /** The quoted spelling reaches a lower-case column; no bare spelling does. */
    @Test
    public void aLowerCaseColumnNeedsItsQuotes() {
        resolves("\"lower\"");
        rejected("lower", "LOWER");
        rejected("LOWER", "LOWER");
    }

    /** A bare-created column answers to the bare form and to its upper-case quoted spelling. */
    @Test
    public void aBareColumnAnswersToEitherUpperCaseSpelling() {
        resolves("upper_col");
        resolves("UPPER_COL");
        resolves("\"UPPER_COL\"");
        rejected("\"upper_col\"", null);
    }

    /** A mixed-case column is reachable only by its exact quoted spelling. */
    @Test
    public void aMixedCaseColumnIsReachableOnlyVerbatim() {
        resolves("\"MiXeD\"");
        rejected("\"mixed\"", null);
        rejected("mixed", "MIXED");
    }

    /** The value each spelling reaches is the column it names, not a neighbour. */
    @Test
    public void eachSpellingReachesItsOwnColumn() {
        assertEquals(1L, ((Number) engine.executeQuery("SELECT \"lower\" FROM mixed")
            .getRows().get(0).getValue(0)).longValue());
        assertEquals(2L, ((Number) engine.executeQuery("SELECT upper_col FROM mixed")
            .getRows().get(0).getValue(0)).longValue());
        assertEquals(3L, ((Number) engine.executeQuery("SELECT \"MiXeD\" FROM mixed")
            .getRows().get(0).getValue(0)).longValue());
    }

    /**
     * SHOW names its columns in lower case, so reading them back off RESULT_SCAN takes the quoted
     * spelling — the idiom the rule matters most for.
     */
    @Test
    public void showColumnsReadBackNeedTheirQuotes() {
        engine.execute("CREATE TABLE probe_t (a INTEGER)");
        engine.execute("SHOW TABLES LIKE 'PROBE_T'");
        assertNotNull(engine.executeQuery(
            "SELECT \"name\", \"rows\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));

        engine.execute("SHOW TABLES LIKE 'PROBE_T'");
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT name FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            }
        });
        assertTrue(e.getMessage().contains("invalid identifier 'NAME'"), "got: " + e.getMessage());
    }

    /** INFORMATION_SCHEMA names its columns in upper case, so the bare form reaches them. */
    @Test
    public void informationSchemaColumnsAnswerToTheBareForm() {
        assertNotNull(engine.executeQuery(
            "SELECT table_name, table_schema FROM INFORMATION_SCHEMA.TABLES"));
        assertNotNull(engine.executeQuery(
            "SELECT \"TABLE_NAME\" FROM INFORMATION_SCHEMA.TABLES"));
    }
}
