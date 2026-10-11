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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The one-statement gate counts a text as it is read to run it (live-verified): two dollars inside a name open
 * no dollar-quoted body, a carriage return ends a line comment, and a word of a block used as a name opens no
 * block — so no second statement rides in behind any of them — while a semicolon the lexer keeps inside a
 * string, a quoted name, a dollar-quoted body or a comment ends no statement.
 */
public class StatementCountLexingTest extends BaseJdbcTest {

    @BeforeEach
    public void threeRows() throws SQLException {
        statement.execute("CREATE OR REPLACE TABLE sc (a INT)");
        statement.execute("INSERT INTO sc VALUES (1), (2), (3)");
    }

    @AfterEach
    public void restoreSingleStatementDefault() throws SQLException {
        setStatementCount(1);
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

    private int rowsLeft() throws SQLException {
        try (final ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM sc")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** A text of two statements is refused under a count of one, and its second statement does not run. */
    private void assertTwoStatements(final String sql) throws SQLException {
        final SQLException refused = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.execute(sql);
            }
        }, sql);
        assertEquals("Actual statement count 2 did not match the desired statement count 1.", refused.getMessage(),
            sql);
        assertEquals("0A000", refused.getSQLState(), sql);
        assertEquals(8, refused.getErrorCode(), sql);
        assertEquals(3, rowsLeft(), sql);
    }

    @Test
    public void dollarsInsideANameOpenNoBody() throws SQLException {
        assertTwoStatements("SELECT 1 AS a$$b; DELETE FROM sc");
        assertTwoStatements("SELECT 1 AS a$$b /* $$ */; DELETE FROM sc");
        assertTwoStatements("SELECT 1 AS a$b; DELETE FROM sc");
    }

    @Test
    public void aCarriageReturnEndsALineComment() throws SQLException {
        assertTwoStatements("SELECT 1 -- x\r; DELETE FROM sc");
        assertTwoStatements("SELECT 1 // c\r; DELETE FROM sc");
    }

    @Test
    public void aBlocksWordUsedAsANameOpensNoBlock() throws SQLException {
        assertTwoStatements("SELECT 1 AS begin, 2 AS b; DELETE FROM sc");
        assertTwoStatements("SELECT 1 AS begin; DELETE FROM sc");
    }

    @Test
    public void whatTheLexerKeepsWholeEndsNoStatement() throws SQLException {
        for (final String one : new String[] {"SELECT 'a;b'", "SELECT $$a;b$$", "SELECT 1 AS \"a;b\"",
            "SELECT 1 -- c; DELETE FROM sc", "SELECT 1 /* c; */", "SELECT 1 AS a$$b", "SELECT 1 -- x\rAS c",
            "SELECT 'it''s; x'", "SELECT 1;", "SELECT 1; -- trailing comment", "SELECT 1; ;"}) {
            statement.execute(one);
        }
        assertEquals(3, rowsLeft());
    }

    @Test
    public void aBlockIsOneStatement() throws SQLException {
        statement.execute("BEGIN LET x := 1; RETURN x; END;");
        statement.execute("DECLARE x INT DEFAULT 1; BEGIN RETURN x; END;");
        statement.execute("BEGIN IF (1 = 1) THEN RETURN 1; END IF; RETURN 2; END;");
        statement.execute("BEGIN RETURN CASE WHEN 1 = 1 THEN 1 ELSE 2 END; END;");
        assertTwoStatements("BEGIN LET x := 1; RETURN x; END; DELETE FROM sc");
        assertTwoStatements("SELECT CASE WHEN 1 = 1 THEN 1 END; DELETE FROM sc");
    }

    @Test
    public void aCountOfTwoRunsBoth() throws SQLException {
        setStatementCount(2);
        statement.execute("SELECT 1 AS a$$b; DELETE FROM sc");
        setStatementCount(1);
        assertEquals(0, rowsLeft());
    }
}
