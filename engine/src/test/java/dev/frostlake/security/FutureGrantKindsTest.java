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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * A future grant on materialized views, dynamic tables, event tables or file formats is applied to each one created
 * afterwards in its scope, as one on tables is (live-verified): SHOW GRANTS ON the new object lists the privilege
 * beside its OWNERSHIP, granted in the owner's name.
 */
public class FutureGrantKindsTest extends MeasuredCellsTestSupport {

    private static final String ACCOUNT_GRANTS =
        "creates a role and grants to it, which the account this suite is checked against is not altered by";

    @Override
    protected void setupTest() {
        Assumptions.assumeFalse(isLiveSnowflake(), ACCOUNT_GRANTS);
        engine.execute("CREATE ROLE IF NOT EXISTS fg_r");
        engine.execute("CREATE OR REPLACE TABLE fg_src (a INT)");
    }

    /** A statement's rows with each creation time masked. */
    private String granted(final String sql) {
        return answer(sql).replaceAll("\\d{4}-\\d{2}-\\d{2}T[0-9:.]+Z", "<TS>");
    }

    private static String rows(final String privilege, final String kind, final String name) {
        return "<TS>|" + privilege + "|" + kind + "|TEST_DB.TEST_SCHEMA." + name + "|ROLE|FG_R|false|SYSADMIN|ROLE ;; "
            + "<TS>|OWNERSHIP|" + kind + "|TEST_DB.TEST_SCHEMA." + name + "|ROLE|SYSADMIN|true|SYSADMIN|ROLE";
    }

    @Test
    public void eachKindTakesItsFutureGrantWhenItIsCreated() {
        engine.execute("GRANT SELECT ON FUTURE MATERIALIZED VIEWS IN SCHEMA test_db.test_schema TO ROLE fg_r");
        engine.execute("GRANT SELECT ON FUTURE DYNAMIC TABLES IN SCHEMA test_db.test_schema TO ROLE fg_r");
        engine.execute("GRANT SELECT ON FUTURE EVENT TABLES IN SCHEMA test_db.test_schema TO ROLE fg_r");
        engine.execute("GRANT USAGE ON FUTURE FILE FORMATS IN SCHEMA test_db.test_schema TO ROLE fg_r");
        engine.execute("CREATE MATERIALIZED VIEW fg_mv AS SELECT a FROM fg_src");
        assertEquals(rows("SELECT", "MATERIALIZED_VIEW", "FG_MV"), granted("SHOW GRANTS ON MATERIALIZED VIEW fg_mv"));
        engine.execute("CREATE DYNAMIC TABLE fg_dt TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT a "
            + "FROM fg_src");
        assertEquals(rows("SELECT", "DYNAMIC_TABLE", "FG_DT"), granted("SHOW GRANTS ON DYNAMIC TABLE fg_dt"));
        engine.execute("CREATE EVENT TABLE fg_et");
        assertEquals(rows("SELECT", "EVENT_TABLE", "FG_ET"), granted("SHOW GRANTS ON EVENT TABLE fg_et"));
        engine.execute("CREATE FILE FORMAT fg_ff TYPE = CSV");
        assertEquals(rows("USAGE", "FILE_FORMAT", "FG_FF"), granted("SHOW GRANTS ON FILE FORMAT fg_ff"));
    }
}
