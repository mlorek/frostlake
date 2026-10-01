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

package dev.frostlake.jdbc;

import dev.frostlake.BaseJdbcTest;
import net.snowflake.client.api.statement.SnowflakeStatement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The text of an EXECUTE IMMEDIATE standing as a statement of a request is held to the count the request asks for —
 * the statement's own MULTI_STATEMENT_COUNT when the client sets one, else the session's — while one inside a
 * Snowflake Scripting block is held to the session's; and a request that is one EXECUTE IMMEDIATE of several
 * statements hands back each statement's answer, where as one statement of a request of several it answers its own
 * row (all live-verified).
 */
public class ExecuteImmediateRequestCountTest extends BaseJdbcTest {

    private static final String TEXT_OF_TWO = "EXECUTE IMMEDIATE 'SELECT 1 AS a; SELECT 2 AS b'";

    private static final String BLOCK_OF_TWO = "BEGIN EXECUTE IMMEDIATE 'SELECT 1; SELECT 2'; RETURN 'ran'; END;";

    @AfterEach
    public void restoreSingleStatementDefault() throws SQLException {
        try (Statement reset = connection.createStatement()) {
            setStatementCount(reset, 1);
            reset.execute("ALTER SESSION UNSET MULTI_STATEMENT_COUNT");
        }
    }

    /** The per-statement parameter, through each transport's own unwrap surface. */
    private static void setStatementCount(final Statement target, final int n) throws SQLException {
        if (isLiveSnowflake()) {
            target.unwrap(SnowflakeStatement.class).setParameter("MULTI_STATEMENT_COUNT", n);
        } else {
            target.unwrap(DirectStatement.class).setParameter("MULTI_STATEMENT_COUNT", n);
        }
    }

    /** Every answer the request hands back, each as its first column's label and first value. */
    private List<String> answers(final Integer statementCount, final String sql) throws SQLException {
        final List<String> answers = new ArrayList<String>();
        try (Statement target = connection.createStatement()) {
            if (statementCount != null) {
                setStatementCount(target, statementCount.intValue());
            }
            boolean rows = target.execute(sql);
            while (true) {
                if (rows) {
                    try (ResultSet rs = target.getResultSet()) {
                        rs.next();
                        answers.add(rs.getMetaData().getColumnLabel(1).toLowerCase() + "=" + rs.getString(1));
                    }
                } else if (target.getUpdateCount() == -1) {
                    break;
                } else {
                    answers.add("update=" + target.getUpdateCount());
                }
                rows = target.getMoreResults();
            }
        }
        return answers;
    }

    private String refusal(final Integer statementCount, final String sql) {
        return assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                answers(statementCount, sql);
            }
        }, sql).getMessage();
    }

    private void setSessionCount(final int n) throws SQLException {
        try (Statement target = connection.createStatement()) {
            target.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = " + n);
        }
    }

    @Test
    public void theStatementsCountGatesTheText() throws SQLException {
        assertEquals("Actual statement count 2 did not match the desired statement count 1.", refusal(null, TEXT_OF_TWO));
        assertEquals(List.of("a=1", "b=2"), answers(0, TEXT_OF_TWO));
        assertEquals("Actual statement count 1 did not match the desired statement count 2.", refusal(2, TEXT_OF_TWO));
        setSessionCount(0);
        assertEquals("Actual statement count 2 did not match the desired statement count 1.", refusal(1, TEXT_OF_TWO));
        assertEquals(List.of("a=1", "b=2"), answers(null, TEXT_OF_TWO));
    }

    @Test
    public void aBlockIsHeldToTheSessionsCount() throws SQLException {
        assertEquals("""
            Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Actual statement count 2 did not \
            match the desired statement count 1.""", refusal(0, BLOCK_OF_TWO));
        setSessionCount(0);
        assertEquals(List.of("anonymous block=ran"), answers(1, BLOCK_OF_TWO));
    }

    /** A failing statement of several, named the way the account's multi-statement helper names it. */
    private static String failedStatement(final String statement, final int position, final String failure) {
        return "JavaScript execution error: Uncaught Execution of multiple statements failed on statement \""
            + statement + "\" (at line 1, position " + position + ").\n" + failure + " in SYSTEM$MULTISTMT at '"
            + "    throw `Execution of multiple statements failed on statement {0} (at line {1}, position {2}).`"
            + ".replace('{1}', LINES[i])' position 4\nstackstrace: \nSYSTEM$MULTISTMT line: 10";
    }

    @Test
    public void whatAStatementEarnsWhileCompilingComesAfterTheCount() throws SQLException {
        final String count = "Actual statement count 2 did not match the desired statement count 1.";
        // The account's parser reads the whole text before it is counted.
        assertEquals("""
            SQL compilation error:
            syntax error line 1 at position 32 unexpected 'FROM'.
            syntax error line 1 at position 37 unexpected '2'.""",
            refusal(null, "EXECUTE IMMEDIATE $$SELECT 1; SELECT SUBSTRING('ab' FROM 2)$$"));
        // What a statement earns while it compiles comes after the count, in a block's assignment too.
        assertEquals(count, refusal(null, "EXECUTE IMMEDIATE $$SELECT 1; SELECT 1 = ANY (SELECT 1) || 'x'$$"));
        assertEquals(count, refusal(null, "EXECUTE IMMEDIATE $$SELECT TO_VARCHAR(INTERVAL '1' DAY(0)); SELECT 1$$"));
        assertEquals("Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 33 : " + count,
            refusal(null, "DECLARE r RESULTSET; BEGIN r := (EXECUTE IMMEDIATE $$SELECT 1;"
                + " SELECT INTERVAL '1 day 2 hours'$$); RETURN TABLE(r); END;"));
        // Under a count of any number each statement compiles when it runs, and one that fails is named.
        assertEquals(failedStatement("SELECT 1=ANY(SELECT 1)||'x'", 0, """
            SQL compilation error:
            Invalid query block: ||."""), refusal(0, "EXECUTE IMMEDIATE $$SELECT 1=ANY(SELECT 1)||'x'; SELECT 1$$"));
        assertEquals(failedStatement("SELECT INTERVAL '1' DAY(0)", 10,
            "Invalid specification for type INTERVAL: INTERVAL DAY"),
            refusal(0, "EXECUTE IMMEDIATE $$SELECT 1; SELECT INTERVAL '1' DAY(0)$$"));
    }

    @Test
    public void oneStatementOfSeveralAnswersItsOwnRow() throws SQLException {
        setSessionCount(0);
        assertEquals(List.of("z=0", "multiple statement execution=Multiple statements executed successfully."),
            answers(null, "SELECT 0 AS z; " + TEXT_OF_TWO));
        assertEquals(List.of("multiple statement execution=Multiple statements executed successfully.", "c=3"),
            answers(null, TEXT_OF_TWO + "; SELECT 3 AS c"));
    }
}
