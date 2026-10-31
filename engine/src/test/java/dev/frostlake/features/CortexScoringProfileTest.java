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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * ALTER CORTEX SEARCH SERVICE adds and drops a scoring profile: ADD SCORING PROFILE [IF NOT EXISTS] name 'json' or
 * $$json$$, DROP SCORING PROFILE [IF EXISTS] name. Over a service that is not there both are refused as any other
 * action is, the DROP with IF EXISTS and an ALTER IF EXISTS answering the skipped-statement status; a profile that
 * is not a string is a syntax error at the word, and a missing profile or name one at the end (all live-verified).
 */
public class CortexScoringProfileTest extends MeasuredCellsTestSupport {

    @Test
    public void scoringProfileActions() {
        assertAnswer("REFUSED SQL compilation error: | Cortex Search Service 'TEST_DB.TEST_SCHEMA.NOSUCH_CSS' does "
                + "not exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "CORTEX SEARCH SERVICE TEST_DB.TEST_SCHEMA.NOSUCH_CSS.",
            "ALTER CORTEX SEARCH SERVICE nosuch_css ADD SCORING PROFILE p1 '{\"functions\": {\"numeric_boosts\": "
                + "[]}}'");
        assertAnswer("REFUSED SQL compilation error: | Cortex Search Service 'TEST_DB.TEST_SCHEMA.NOSUCH_CSS' does "
                + "not exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "CORTEX SEARCH SERVICE TEST_DB.TEST_SCHEMA.NOSUCH_CSS.",
            "ALTER CORTEX SEARCH SERVICE nosuch_css ADD SCORING PROFILE IF NOT EXISTS p1 '{}'");
        assertAnswer("REFUSED SQL compilation error: | Cortex Search Service 'TEST_DB.TEST_SCHEMA.NOSUCH_CSS' does "
                + "not exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "CORTEX SEARCH SERVICE TEST_DB.TEST_SCHEMA.NOSUCH_CSS.",
            "ALTER CORTEX SEARCH SERVICE nosuch_css DROP SCORING PROFILE p1");
        assertAnswer("Cortex Search Service NOSUCH_CSS does not exist.",
            "ALTER CORTEX SEARCH SERVICE nosuch_css DROP SCORING PROFILE IF EXISTS p1");
        assertAnswer("Cortex Search Service NOSUCH_CSS does not exist.",
            "ALTER CORTEX SEARCH SERVICE IF EXISTS nosuch_css ADD SCORING PROFILE p1 '{}'");
        assertAnswer("Cortex Search Service NOSUCH_CSS does not exist.",
            "ALTER CORTEX SEARCH SERVICE IF EXISTS nosuch_css DROP SCORING PROFILE p1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'notastring'.",
            "ALTER CORTEX SEARCH SERVICE nosuch_css ADD SCORING PROFILE p1 notastring");
        assertAnswer("REFUSED SQL compilation error: | Cortex Search Service 'TEST_DB.TEST_SCHEMA.NOSUCH_CSS' does "
                + "not exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "CORTEX SEARCH SERVICE TEST_DB.TEST_SCHEMA.NOSUCH_CSS.",
            "ALTER CORTEX SEARCH SERVICE nosuch_css ADD SCORING PROFILE p1 $${\"a\": 1}$$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected '<EOF>'.",
            "ALTER CORTEX SEARCH SERVICE nosuch_css ADD SCORING PROFILE p1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected '<EOF>'.",
            "ALTER CORTEX SEARCH SERVICE nosuch_css DROP SCORING PROFILE");
        assertAnswer("REFUSED SQL compilation error: | Cortex Search Service 'TEST_DB.TEST_SCHEMA.NOSUCH_CSS' does "
                + "not exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "CORTEX SEARCH SERVICE TEST_DB.TEST_SCHEMA.NOSUCH_CSS.",
            "ALTER CORTEX SEARCH SERVICE nosuch_css ADD SCORING PROFILE \"Mixed\" '{}'");
        assertAnswer("REFUSED SQL compilation error: | Cortex Search Service 'TEST_DB.TEST_SCHEMA.NOSUCH_CSS' does "
                + "not exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "CORTEX SEARCH SERVICE TEST_DB.TEST_SCHEMA.NOSUCH_CSS.",
            "ALTER CORTEX SEARCH SERVICE nosuch_css SET FULL_INDEX_BUILD_INTERVAL_DAYS = 7");
        assertAnswer("REFUSED SQL compilation error: | Cortex Search Service 'TEST_DB.TEST_SCHEMA.NOSUCH_CSS' does "
                + "not exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "CORTEX SEARCH SERVICE TEST_DB.TEST_SCHEMA.NOSUCH_CSS.",
            "ALTER CORTEX SEARCH SERVICE nosuch_css SET REQUEST_LOGGING = TRUE");
        assertAnswer("REFUSED SQL compilation error: | Cortex Search Service 'TEST_DB.TEST_SCHEMA.NOSUCH_CSS' does "
                + "not exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "CORTEX SEARCH SERVICE TEST_DB.TEST_SCHEMA.NOSUCH_CSS.",
            "ALTER CORTEX SEARCH SERVICE nosuch_css SET AUTO_SUSPEND = NULL");
    }
}
