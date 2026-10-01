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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The account compiles a text before it counts its statements (live-verified), so a text that will not
 * parse is refused for its syntax whatever MULTI_STATEMENT_COUNT asks for — in whichever statement the
 * fault lies — while a fault found only when a statement runs leaves the count refusal to answer.
 */
public class StatementCountSyntaxFirstTest extends BaseJdbcTest {

    private SQLException refusal(final String sql) {
        return assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.execute(sql);
            }
        });
    }

    private void assertSyntaxError(final String sql, final String lines) {
        assertEquals("SQL compilation error:\n" + lines, refusal(sql).getMessage(), sql);
    }

    private void assertCountRefused(final String sql, final int actual, final int desired) {
        final SQLException e = refusal(sql);
        assertEquals("Actual statement count " + actual + " did not match the desired statement count "
            + desired + ".", e.getMessage(), sql);
        assertEquals("0A000", e.getSQLState(), sql);
        assertEquals(8, e.getErrorCode(), sql);
    }

    /** The per-statement parameter, through each transport's own unwrap surface. */
    private void setStatementCount(final int n) throws SQLException {
        if (isLiveSnowflake()) {
            statement.unwrap(net.snowflake.client.api.statement.SnowflakeStatement.class)
                .setParameter("MULTI_STATEMENT_COUNT", n);
        } else {
            statement.unwrap(DirectStatement.class).setParameter("MULTI_STATEMENT_COUNT", n);
        }
    }

    @AfterEach
    public void restoreSingleStatementDefault() throws SQLException {
        setStatementCount(1);
    }

    /** A word run into a complete statement is refused at the word through the count gate too. */
    @Test
    public void aWordRunIntoAStatementIsRefusedThroughTheGate() {
        assertSyntaxError("SELECT 1 AS x COMMENT = 'x'; SELECT 1",
            "syntax error line 1 at position 14 unexpected 'COMMENT'.");
    }

    @Test
    public void aFaultInTheFirstStatementWinsOverTheCount() {
        assertSyntaxError("SELECT 1 x y; SELECT 2", "syntax error line 1 at position 11 unexpected 'y'.");
        assertSyntaxError("SELECT 1 x y; SELECT 2 x y", "syntax error line 1 at position 11 unexpected 'y'.");
        assertSyntaxError("SELECT (1; SELECT 2", "syntax error line 1 at position 9 unexpected ';'.");
        assertSyntaxError("SELECT 1 +; SELECT 2", "syntax error line 1 at position 10 unexpected ';'.");
    }

    @Test
    public void aFaultInALaterStatementWinsOverTheCount() {
        assertSyntaxError("SELECT 1; SELECT 2 x y", "syntax error line 1 at position 21 unexpected 'y'.");
        assertSyntaxError("SELECT 1; SELECT 2 x y; SELECT 3", "syntax error line 1 at position 21 unexpected 'y'.");
        assertSyntaxError("SELECT 1;\nSELECT 2 x y", "syntax error line 2 at position 11 unexpected 'y'.");
        assertSyntaxError("SELECT 1; SELECT 2 FROM", "syntax error line 1 at position 23 unexpected '<EOF>'.");
        assertSyntaxError("SELECT 1; BEGIN SELECT 1 x y; END",
            "syntax error line 1 at position 27 unexpected 'y'.\nsyntax error line 1 at position 30 unexpected 'END'.");
    }

    @Test
    public void statementShapedFaultsWinOverTheCount() throws SQLException {
        statement.execute("CREATE TABLE t1 (a INT)");
        assertSyntaxError("DELETE FROM t1 ('x'); SELECT 1", "syntax error line 1 at position 15 unexpected '('.");
        assertSyntaxError("CREATE FUNCTION o1() RETURNS INT AS '1' LANGUAGE SQL; SELECT 1",
            "syntax error line 1 at position 40 unexpected 'LANGUAGE'.");
        assertSyntaxError("SELECT 'a;b' x y; SELECT 2", "syntax error line 1 at position 15 unexpected 'y'.");
    }

    @Test
    public void aFaultWinsWhateverCountWasAskedFor() throws SQLException {
        setStatementCount(2);
        assertSyntaxError("SELECT 1 x y", "syntax error line 1 at position 11 unexpected 'y'.");
        assertSyntaxError("SELECT 1; SELECT 2; SELECT 3 x y", "syntax error line 1 at position 31 unexpected 'y'.");
        assertSyntaxError("SELECT 1 x y; SELECT 2; SELECT 3", "syntax error line 1 at position 11 unexpected 'y'.");
        assertCountRefused("SELECT 1", 1, 2);
    }

    @Test
    public void aFaultFoundOnlyWhenAStatementRunsLeavesTheCountToAnswer() {
        assertCountRefused("SELECT 1; SELECT 2", 2, 1);
        assertCountRefused("SELECT nosuch FROM nowhere; SELECT 2", 2, 1);
        assertCountRefused("SELECT 1; SELECT nosuch FROM nowhere", 2, 1);
        // The text inside EXECUTE IMMEDIATE is compiled only when it runs.
        assertCountRefused("SELECT 1; EXECUTE IMMEDIATE 'SELECT 1 x y'", 2, 1);
    }
}
