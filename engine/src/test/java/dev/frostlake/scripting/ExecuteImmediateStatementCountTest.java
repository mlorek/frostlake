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
 * The text of an EXECUTE IMMEDIATE is counted against the session's MULTI_STATEMENT_COUNT as a request is
 * (live-verified): once it parses, before any of it runs. Under the default of 1 a text of two statements is refused
 * with the request gate's sentence, in a block as the statement's uncaught error; under 0 it runs as a multi-statement
 * request, whose own answer is the one-row "Multiple statements executed successfully.".
 */
public class ExecuteImmediateStatementCountTest extends BaseDatabaseTest {

    private static final String TWO_FOR_ONE = "Actual statement count 2 did not match the desired statement count 1.";

    @Override
    protected void setupTest() {
        // The JDBC-mode harness lifts the session's count for its own scripts; these cells start from the default.
        if (FrostlakeJdbc.enabled()) {
            engine.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 1");
        }
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

    private static String uncaught(final int position, final String error) {
        return "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position " + position + " : " + error;
    }

    @Test
    public void twoStatementsAreRefusedBeforeEitherRuns() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        assertEquals(TWO_FOR_ONE, refusal("EXECUTE IMMEDIATE 'SELECT 1; SELECT 2'"));
        assertEquals(TWO_FOR_ONE, refusal("EXECUTE IMMEDIATE $$INSERT INTO t VALUES (6, 6); SELECT 1$$"));
        assertEquals("0", value("SELECT COUNT(*) FROM t WHERE a = 6"));
        assertEquals(TWO_FOR_ONE,
            refusal("EXECUTE IMMEDIATE $$INSERT INTO t VALUES (7, 7); SELECT (SELECT a FROM t LIMIT 'x')$$"));
        assertEquals(TWO_FOR_ONE, refusal("EXECUTE IMMEDIATE $$SELECT 1; SELECT nosuch_col$$"));
        assertEquals(TWO_FOR_ONE, refusal("EXECUTE IMMEDIATE 'EXECUTE IMMEDIATE ''SELECT 1; SELECT 2'''"));
        assertEquals("0", value("SELECT COUNT(*) FROM t WHERE a = 7"));
    }

    @Test
    public void aTextThatDoesNotParseIsItsSyntaxError() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        final String first = refusal("EXECUTE IMMEDIATE $$SELECT 1; SELECT FROM WHERE$$");
        assertTrue(first.startsWith("""
            SQL compilation error:
            syntax error line 1 at position 17 unexpected 'FROM'."""), first);
        assertEquals("""
            SQL compilation error:
            syntax error line 1 at position 62 unexpected ''x''.""",
            refusal("EXECUTE IMMEDIATE $$INSERT INTO t VALUES (5, 5) ->> SELECT (SELECT a FROM t LIMIT 'x')$$"));
        assertEquals("0", value("SELECT COUNT(*) FROM t WHERE a = 5"));
    }

    @Test
    public void oneStatementIsOneWhateverFollowsIt() {
        assertEquals("1", value("EXECUTE IMMEDIATE 'SELECT 1;'"));
        assertEquals("1", value("EXECUTE IMMEDIATE 'SELECT 1; '"));
        assertEquals("1", value("EXECUTE IMMEDIATE 'SELECT 1;;'"));
        assertEquals("1", value("EXECUTE IMMEDIATE 'SELECT 1; -- c'"));
        assertEquals("null", value("EXECUTE IMMEDIATE 'BEGIN SELECT 1; SELECT 2; END'"));
        assertEquals("1", value("EXECUTE IMMEDIATE 'DECLARE x INT; BEGIN x := 1; RETURN x; END'"));
    }

    @Test
    public void inABlockTheRefusalIsTheStatementsUncaughtError() {
        assertEquals(uncaught(6, TWO_FOR_ONE), refusal("BEGIN EXECUTE IMMEDIATE 'SELECT 1; SELECT 2'; RETURN 1; END;"));
        assertEquals(uncaught(26, TWO_FOR_ONE),
            refusal("BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE 'SELECT 1; SELECT 2'); RETURN TABLE(r); END;"));
        assertEquals(uncaught(33, TWO_FOR_ONE), refusal(
            "DECLARE r RESULTSET; BEGIN r := (EXECUTE IMMEDIATE 'SELECT 1; SELECT 2'); RETURN TABLE(r); END;"));
        assertEquals(uncaught(26, TWO_FOR_ONE), refusal("BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE"
            + " 'CREATE OR REPLACE TABLE m1 (a INT); CREATE OR REPLACE TABLE m2 (a INT)'); RETURN TABLE(r); END;"));
        assertEquals(0, engine.executeQuery("SHOW TABLES LIKE 'M_'").getRowCount());
        assertEquals("8 0A000 " + TWO_FOR_ONE, value("BEGIN EXECUTE IMMEDIATE 'SELECT 1; SELECT 2'; RETURN 1;"
            + " EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END;"));
    }

    /**
     * The count the text answers to is its request's: the live harness pins every request's own count to 1, which the
     * account applies to the text whatever the session's is, so this runs embedded only (the session-level cells are
     * measured over a plain connection).
     */
    @Test
    public void underACountOfZeroTheTextRunsAsAMultiStatementRequest() {
        assumeFalse(isLiveSnowflake(), "the live harness pins each request's MULTI_STATEMENT_COUNT to 1");
        engine.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
        try {
            engine.execute("EXECUTE IMMEDIATE 'CREATE OR REPLACE TABLE ms1 (a INT); CREATE OR REPLACE TABLE ms2 (a INT)'");
            final ResultSet answer = engine.executeQuery("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertEquals("multiple statement execution", answer.getColumns().get(0).getName());
            assertEquals("Multiple statements executed successfully.", String.valueOf(answer.getRows().get(0).getValue(0)));
            assertEquals(2, engine.executeQuery("SHOW TABLES LIKE 'MS_'").getRowCount());
            assertEquals("Multiple statements executed successfully.", value(
                "BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE 'SELECT 1 AS x; SELECT 2 AS y'); RETURN TABLE(r); END;"));
            assertEquals("7", value("EXECUTE IMMEDIATE 'SELECT 7 AS single'"));
            // The client reads each statement's answer in turn, the first one first.
            final ResultSet first = engine.executeQuery("EXECUTE IMMEDIATE 'SELECT 1 AS x; SELECT 2 AS y'");
            assertEquals("X", first.getColumns().get(0).getName());
            assertEquals("Multiple statements executed successfully.",
                value("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            final String failed = refusal("EXECUTE IMMEDIATE 'SELECT 1 AS x; SELECT nosuch'");
            assertTrue(failed.startsWith("""
                JavaScript execution error: Uncaught Execution of multiple statements failed on statement \
                "SELECT nosuch" (at line 1, position 15).
                SQL compilation error: error line 1 at position 7
                invalid identifier 'NOSUCH' in SYSTEM$MULTISTMT"""), failed);
        } finally {
            engine.execute("ALTER SESSION UNSET MULTI_STATEMENT_COUNT");
        }
        assertEquals(TWO_FOR_ONE, refusal("EXECUTE IMMEDIATE 'SELECT 1; SELECT 2'"));
    }
}
