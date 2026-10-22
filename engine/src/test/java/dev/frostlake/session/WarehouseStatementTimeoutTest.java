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
package dev.frostlake.session;

import dev.frostlake.LiveSnowflake;
import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A statement run on a warehouse is timed by the lowest non-zero of the session's STATEMENT_TIMEOUT_IN_SECONDS and the
 * warehouse's (documented): the warehouse's 1 second ends a statement the session would let run, the session's 1
 * second one the warehouse would, and a warehouse's 0 leaves the session's. Engine-only: the account's warehouses are
 * shared.
 */
public class WarehouseStatementTimeoutTest extends MeasuredCellsTestSupport {

    private static final String ENDED = "REFUSED Statement reached its statement or warehouse timeout of 1 second(s) "
        + "and was canceled.";

    @Test
    public void theLowerTimeoutEndsTheStatement() {
        assumeFalse(LiveSnowflake.enabled());
        engine.execute("CREATE OR REPLACE WAREHOUSE wt_wh WITH WAREHOUSE_SIZE = 'XSMALL'");
        engine.execute("USE WAREHOUSE wt_wh");
        engine.execute("ALTER WAREHOUSE wt_wh SET STATEMENT_TIMEOUT_IN_SECONDS = 1");
        assertAnswer(ENDED, "SELECT SYSTEM$WAIT(3)");
        engine.execute("ALTER SESSION SET STATEMENT_TIMEOUT_IN_SECONDS = 5");
        assertAnswer(ENDED, "SELECT SYSTEM$WAIT(3)");
        engine.execute("ALTER WAREHOUSE wt_wh SET STATEMENT_TIMEOUT_IN_SECONDS = 0");
        engine.execute("ALTER SESSION SET STATEMENT_TIMEOUT_IN_SECONDS = 1");
        assertAnswer(ENDED, "SELECT SYSTEM$WAIT(3)");
        engine.execute("ALTER WAREHOUSE wt_wh UNSET STATEMENT_TIMEOUT_IN_SECONDS");
        engine.execute("ALTER SESSION SET STATEMENT_TIMEOUT_IN_SECONDS = 5");
        final String waited = answer("SELECT SYSTEM$WAIT(1)");
        assertFalse(waited.startsWith("REFUSED "), waited);
    }
}
