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

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * SHOW GRANTS ON lists a relation under its own kind whichever relation word names it — TABLE, VIEW, MATERIALIZED
 * VIEW, DYNAMIC TABLE, EVENT TABLE, EXTERNAL TABLE, ICEBERG TABLE or none — and refuses a name no relation carries in
 * the words of the kind written; the privilege views list the owner's rows of each relation under TABLE
 * (live-verified).
 */
public class ShowGrantsOnRelationKindsTest extends MeasuredCellsTestSupport {

    private static final String GRANTS = "SELECT \"privilege\", \"granted_on\", \"grant_option\" FROM"
        + " TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1";

    private static String missing(final String kind, final String name) {
        return "REFUSED SQL compilation error: | " + kind + " '" + name + "' does not exist or not authorized. Your "
            + "primary role <ROLE> must have at least one privilege granted on TABLE " + name + ".";
    }

    @Test
    public void aRelationIsListedUnderItsOwnKindWhicheverWordNamesIt() {
        answer("CREATE OR REPLACE TABLE gk (a INT)");
        answer("CREATE OR REPLACE MATERIALIZED VIEW gk_mv AS SELECT a FROM gk");
        answer("CREATE OR REPLACE EVENT TABLE gk_ev");
        answer("CREATE OR REPLACE VIEW gk_v AS SELECT a FROM gk");
        final String warehouse = answer("SELECT COALESCE(CURRENT_WAREHOUSE(), 'COMPUTE_WH')");
        answer("CREATE OR REPLACE DYNAMIC TABLE gk_dt TARGET_LAG = '1 day' WAREHOUSE = " + warehouse
            + " AS SELECT a FROM gk");
        final String[][] shown = {
            {"DYNAMIC TABLE gk_dt", "DYNAMIC_TABLE"}, {"TABLE gk_dt", "DYNAMIC_TABLE"}, {"VIEW gk_dt", "DYNAMIC_TABLE"},
            {"gk_dt", "DYNAMIC_TABLE"},
            {"MATERIALIZED VIEW gk_mv", "MATERIALIZED_VIEW"}, {"TABLE gk_mv", "MATERIALIZED_VIEW"},
            {"VIEW gk_mv", "MATERIALIZED_VIEW"}, {"gk_mv", "MATERIALIZED_VIEW"},
            {"EVENT TABLE gk_ev", "EVENT_TABLE"}, {"TABLE gk_ev", "EVENT_TABLE"}, {"VIEW gk_ev", "EVENT_TABLE"},
            {"gk_ev", "EVENT_TABLE"},
            {"MATERIALIZED VIEW gk_v", "VIEW"}, {"EVENT TABLE gk", "TABLE"}, {"DYNAMIC TABLE gk", "TABLE"},
            {"EXTERNAL TABLE gk", "TABLE"}, {"ICEBERG TABLE gk", "TABLE"},
        };
        for (final String[] cell : shown) {
            answer("SHOW GRANTS ON " + cell[0]);
            assertAnswer("OWNERSHIP|" + cell[1] + "|true", GRANTS);
        }
        assertCells(new String[][] {
            {"SELECT TABLE_NAME, PRIVILEGE_TYPE, IS_GRANTABLE FROM TEST_DB.INFORMATION_SCHEMA.TABLE_PRIVILEGES WHERE "
                + "TABLE_SCHEMA = 'TEST_SCHEMA' AND TABLE_NAME LIKE 'GK%' ORDER BY 1, 2", "GK|OWNERSHIP|YES ;; "
                + "GK_DT|OWNERSHIP|YES ;; GK_EV|OWNERSHIP|YES ;; GK_MV|OWNERSHIP|YES ;; GK_V|OWNERSHIP|YES"},
            {"SELECT OBJECT_NAME, OBJECT_TYPE FROM TEST_DB.INFORMATION_SCHEMA.OBJECT_PRIVILEGES WHERE OBJECT_SCHEMA = "
                + "'TEST_SCHEMA' AND OBJECT_NAME LIKE 'GK%' ORDER BY 1", "GK|TABLE ;; GK_DT|TABLE ;; GK_EV|TABLE ;; "
                + "GK_MV|TABLE ;; GK_V|TABLE"},
        });
    }

    @Test
    public void aMissingRelationIsRefusedInTheWordsOfTheKindWritten() {
        assertCells(new String[][] {
            {"SHOW GRANTS ON DYNAMIC TABLE nosuch_dt", missing("Dynamic table", "TEST_DB.TEST_SCHEMA.NOSUCH_DT")},
            {"SHOW GRANTS ON EVENT TABLE \"nosuch_ev\"", missing("Event table", "TEST_DB.TEST_SCHEMA.\"nosuch_ev\"")},
            {"SHOW GRANTS ON EXTERNAL TABLE nosuch_et", missing("External table", "TEST_DB.TEST_SCHEMA.NOSUCH_ET")},
            {"SHOW GRANTS ON ICEBERG TABLE nosuch_it", missing("Iceberg table", "TEST_DB.TEST_SCHEMA.NOSUCH_IT")},
            {"SHOW GRANTS ON MATERIALIZED VIEW nosuch_mv", missing("Materialized view", "NOSUCH_MV")},
            {"SHOW GRANTS ON MATERIALIZED VIEW \"nosuch_mv\"", missing("Materialized view", "\"nosuch_mv\"")},
            {"SHOW GRANTS ON MATERIALIZED VIEW test_schema.nosuch_mv",
                missing("Materialized view", "TEST_DB.TEST_SCHEMA.NOSUCH_MV")},
        });
    }
}
