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

package dev.frostlake.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A time-travel clause on a CTE's name is passed over and the CTE read, AT and BEFORE alike and whether or not a
 * table of that name exists; a CHANGES clause on it is refused for change tracking on an internal view, whose
 * number the account gives by the query's shape and this engine cannot know, so that refusal is asked for its
 * sentence (all live-verified).
 */
public class CteTimeTravelTest extends MeasuredCellsTestSupport {

    private static final String UNTRACKED = "REFUSED SQL compilation error: Change tracking is not enabled or has been "
        + "missing for the time range requested on table 'SYS$_VIEW_";

    private void assertChangesRefused(final String sql) {
        final String answered = answer(sql);
        assertTrue(answered.startsWith(UNTRACKED) && answered.endsWith("'."), answered);
    }

    @Test
    public void timeTravelOnACte() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1), (2), (3)");
        assertAnswer("42",
            "WITH t AS (SELECT 42 a) SELECT * FROM t AT(TIMESTAMP => CURRENT_TIMESTAMP()::TIMESTAMP_LTZ) ORDER BY 1");
        assertAnswer("42",
            "WITH c AS (SELECT 42 a) SELECT * FROM c AT(TIMESTAMP => CURRENT_TIMESTAMP()::TIMESTAMP_LTZ)");
        assertAnswer("42",
            "WITH t AS (SELECT 42 a) SELECT * FROM t BEFORE(TIMESTAMP => CURRENT_TIMESTAMP()::TIMESTAMP_LTZ) ORDER "
                + "BY 1");
        assertAnswer("1 ;; 2 ;; 3", "SELECT * FROM t AT(TIMESTAMP => CURRENT_TIMESTAMP()::TIMESTAMP_LTZ) ORDER BY 1");
    }

    @Test
    public void changesOfACteAreRefused() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertChangesRefused("WITH t AS (SELECT 42 a) SELECT * FROM t CHANGES(INFORMATION => DEFAULT) AT(TIMESTAMP "
            + "=> CURRENT_TIMESTAMP()::TIMESTAMP_LTZ)");
        assertChangesRefused("WITH c AS (SELECT 42 a) SELECT * FROM c CHANGES(INFORMATION => DEFAULT) AT(TIMESTAMP "
            + "=> CURRENT_TIMESTAMP()::TIMESTAMP_LTZ)");
    }
}
