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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseJdbcTest;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A string LIMIT or OFFSET value is judged for each statement of a request as that statement runs, not for the
 * request's text as a whole: the statements before the refused one still run, and the refusal is that statement's
 * failure inside the request (live-verified).
 */
public class SubqueryLimitStringRequestTest extends BaseJdbcTest {

    @Override
    protected void setupTest() throws SQLException {
        statement.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
        statement.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        statement.execute("INSERT INTO t VALUES (1, 2), (3, 4)");
    }

    @Override
    protected void teardownTest() throws SQLException {
        statement.execute("ALTER SESSION UNSET MULTI_STATEMENT_COUNT");
    }

    @Test
    public void theStatementsBeforeTheRefusedOneStillRun() throws SQLException {
        final String failed = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.execute("INSERT INTO t VALUES (9, 9); SELECT (SELECT a FROM t LIMIT 'x')");
            }
        }).getMessage();
        assertTrue(failed.contains("Execution of multiple statements failed on statement \"SELECT (SELECT a FROM t LIMIT")
            && failed.contains(" unexpected ''x''."), failed);
        try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM t WHERE a = 9")) {
            assertTrue(rs.next());
            assertEquals("1", rs.getString(1));
        }
    }
}
