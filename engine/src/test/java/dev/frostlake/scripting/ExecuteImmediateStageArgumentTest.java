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

package dev.frostlake.scripting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.FrostlakeJdbc;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A bare stage in the text of an EXECUTE IMMEDIATE is judged in the two phases a request's is (live-verified): where
 * it stands as the text parses, before its statements are counted and before any of them runs; the call it is passed
 * to as its statement compiles, after the count. In a text of several statements that statement compiles when it
 * runs, and it fails named the way a multi-statement request names its failed statement, placed in its own text.
 */
public class ExecuteImmediateStageArgumentTest extends BaseDatabaseTest {

    private static final String TWO_FOR_ONE = "Actual statement count 2 did not match the desired statement count 1.";

    private static final String UPPER_ARGUMENT =
        "invalid argument for function [UPPER] unexpected argument [@st] at position 0,";

    @Override
    protected void setupTest() {
        // The JDBC-mode harness lifts the session's count for its own scripts; these cells start from the default.
        if (FrostlakeJdbc.enabled()) {
            engine.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 1");
        }
        engine.execute("CREATE OR REPLACE TABLE t2 (v VARCHAR)");
        engine.execute("CREATE OR REPLACE STAGE st");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private String value(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    @Test
    public void aTextOfOneStatementIsJudgedForTheCallItPassesTheStageTo() {
        assertEquals("SQL compilation error: error line 1 at position 7\n" + UPPER_ARGUMENT,
            refusal("EXECUTE IMMEDIATE $$SELECT UPPER(@st)$$"));
    }

    @Test
    public void whereTheStageStandsIsRefusedBeforeTheCountAndTheCallAfterIt() {
        final String rows = value("SELECT COUNT(*) FROM t2");
        assertEquals("""
            SQL compilation error:
            syntax error line 1 at position 41 unexpected '@st'.""",
            refusal("EXECUTE IMMEDIATE $$INSERT INTO t2 VALUES ('sixth'); SELECT (@st)$$"));
        assertEquals(TWO_FOR_ONE, refusal("EXECUTE IMMEDIATE $$INSERT INTO t2 VALUES ('fifth'); SELECT UPPER(@st)$$"));
        assertEquals(rows, value("SELECT COUNT(*) FROM t2"));
    }

    /** Inside a block the text answers to the session's count, which the live harness leaves to the test. */
    @Test
    public void inABlockAStatementOfSeveralIsJudgedWhenItRuns() {
        engine.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
        try {
            final String failed = refusal("BEGIN EXECUTE IMMEDIATE $$INSERT INTO t2 VALUES ('fourth'); "
                + "SELECT UPPER(@st)$$; RETURN 1; END;");
            assertTrue(failed.startsWith("""
                Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : JavaScript execution error: \
                Uncaught Execution of multiple statements failed on statement "SELECT UPPER(@st)" (at line 1, \
                position 34).
                SQL compilation error: error line 1 at position 7
                """ + UPPER_ARGUMENT + " in SYSTEM$MULTISTMT"), failed);
        } finally {
            engine.execute("ALTER SESSION UNSET MULTI_STATEMENT_COUNT");
        }
    }

    /**
     * The count a top-level text answers to is its request's, which the live harness pins to 1, so these run embedded
     * only (they were measured over a plain connection under a session count of 0).
     */
    @Test
    public void underACountOfZeroEachStatementIsJudgedInItsOwnText() {
        assumeFalse(isLiveSnowflake(), "the live harness pins each request's MULTI_STATEMENT_COUNT to 1");
        engine.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
        try {
            final String second = refusal(
                "EXECUTE IMMEDIATE $$INSERT INTO t2 VALUES ('first'); SELECT 1; SELECT LOWER(UPPER(@st))$$");
            assertTrue(second.startsWith("""
                JavaScript execution error: Uncaught Execution of multiple statements failed on statement \
                "SELECT LOWER(UPPER(@st))" (at line 1, position 43).
                SQL compilation error: error line 1 at position 13
                """ + UPPER_ARGUMENT + " in SYSTEM$MULTISTMT"), second);
            final String commented = refusal(
                "EXECUTE IMMEDIATE $$/* c */ INSERT INTO t2 VALUES ('third');   /* d */ SELECT UPPER(@st)$$");
            assertTrue(commented.startsWith("""
                JavaScript execution error: Uncaught Execution of multiple statements failed on statement \
                "SELECT UPPER(@st)" (at line 1, position 51).
                SQL compilation error: error line 1 at position 7
                """ + UPPER_ARGUMENT), commented);
            assertEquals("""
                SQL compilation error:
                syntax error line 1 at position 54 unexpected '@st'.""",
                refusal("EXECUTE IMMEDIATE $$INSERT INTO t2 VALUES ('second'); SELECT UPPER(@st), (@st)$$"));
        } finally {
            engine.execute("ALTER SESSION UNSET MULTI_STATEMENT_COUNT");
        }
    }
}
