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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The execute / update-count contract of Snowflake's JDBC driver, as assertions any connection can be held to
 * — Frostlake's in-process transport, its HTTP transport, and a live account's own driver. Every expectation
 * is what snowflake-jdbc answers.
 *
 * <p>A statement answers ROWS (a query, SHOW, DESCRIBE, EXPLAIN, CALL, LIST, an anonymous block) or an
 * UPDATE COUNT: the rows DML affected — every count of its grid added up — and 0 for DDL, USE, SET, a
 * transaction statement and every other statement that answers no rows. A walk over several statements
 * reports each in turn, and {@code getMoreResults()} answers true for rows and ALSO for an update count that
 * more results follow.
 */
final class UpdateCountContract {

    private UpdateCountContract() {
    }

    /** The objects every check uses. */
    static void setUp(final Statement statement) throws SQLException {
        statement.execute("CREATE OR REPLACE TABLE t (id INT, v VARCHAR)");
        statement.execute("CREATE OR REPLACE TABLE t2 (a INT)");
        statement.execute("CREATE OR REPLACE TABLE log (n INT)");
        statement.execute("CREATE OR REPLACE PROCEDURE p() RETURNS VARCHAR LANGUAGE SQL AS "
            + "$$ BEGIN INSERT INTO log VALUES (1); RETURN 'done'; END; $$");
    }

    /**
     * A statement's walk, spelled out: what {@code execute} answered, then each result as {@code rows:<first
     * column>} or {@code count:<n>}, each followed by what {@code getMoreResults()} answered, up to {@code end}.
     */
    static String walk(final Statement statement, final String sql) throws SQLException {
        boolean more = statement.execute(sql);
        final StringBuilder out = new StringBuilder().append(more);
        for (int i = 0; i < 12; i++) {
            if (more) {
                final ResultSet rows = statement.getResultSet();
                if (rows != null) {
                    out.append(" rows:").append(rows.getMetaData().getColumnLabel(1));
                } else {
                    out.append(" count:").append(statement.getUpdateCount());
                }
            } else {
                final int count = statement.getUpdateCount();
                if (count == -1) {
                    return out.append(" end").toString();
                }
                out.append(" count:").append(count);
            }
            more = statement.getMoreResults();
            out.append(' ').append(more);
        }
        return out.toString();
    }

    /** DDL, USE, SET, ALTER SESSION and the transaction statements all answer update count 0. */
    static void everyStatementWithoutRowsCountsZero(final Statement statement) throws SQLException {
        final String[] statements = {
            "CREATE OR REPLACE TABLE t3 (a INT)",
            "USE SCHEMA PUBLIC",
            "BEGIN TRANSACTION",
            "COMMIT",
            "BEGIN",
            "ROLLBACK",
            "TRUNCATE TABLE t2",
            "CREATE OR REPLACE TABLE t4 AS SELECT 1 AS a UNION ALL SELECT 2",
            "SET myvar = 5",
            "UNSET myvar",
            "ALTER SESSION SET QUERY_TAG = 'x'",
            "ALTER SESSION UNSET QUERY_TAG",
            "CREATE OR REPLACE VIEW v1 AS SELECT 1 AS a",
            "DROP VIEW v1",
            "ALTER TABLE t2 ADD COLUMN b INT",
            "COMMENT ON TABLE t2 IS 'c'",
            "CREATE OR REPLACE SEQUENCE sq",
            "EXECUTE IMMEDIATE 'CREATE OR REPLACE TABLE t5 (a INT)'",
            "EXECUTE IMMEDIATE 'SET zz = 3'",
        };
        for (final String sql : statements) {
            assertEquals("false count:0 false end", walk(statement, sql), sql);
            assertNull(statement.getResultSet(), sql);
        }
    }

    /** DML answers its affected rows: every count of its grid added up, through EXECUTE IMMEDIATE too. */
    static void dmlCountsItsRows(final Statement statement) throws SQLException {
        assertEquals("false count:3 false end", walk(statement, "INSERT INTO t VALUES (1,'a'),(2,'b'),(3,'c')"));
        assertEquals("false count:2 false end", walk(statement, "UPDATE t SET v = 'x' WHERE id <= 2"));
        assertEquals("false count:1 false end", walk(statement, "DELETE FROM t WHERE id = 3"));
        assertEquals("false count:2 false end", walk(statement,
            "MERGE INTO t USING (SELECT 1 AS id, 'm' AS v UNION ALL SELECT 9, 'n') s ON t.id = s.id "
            + "WHEN MATCHED THEN UPDATE SET v = s.v WHEN NOT MATCHED THEN INSERT VALUES (s.id, s.v)"));
        assertEquals("false count:2 false end", walk(statement,
            "INSERT ALL INTO t VALUES (a, 'm') INTO log VALUES (a) SELECT 300 AS a"));
        assertEquals("false count:1 false end", walk(statement, "INSERT OVERWRITE INTO t2 (a) SELECT 7"));
        assertEquals("false count:2 false end", walk(statement,
            "EXECUTE IMMEDIATE 'INSERT INTO t VALUES (200, ''e''), (201, ''f'')'"));
    }

    /** COPY INTO a table counts the rows it loaded, and an unload counts 0. */
    static void copyCountsTheRowsItLoaded(final Statement statement) throws SQLException {
        statement.execute("CREATE OR REPLACE STAGE st");
        assertEquals("false count:0 false end", walk(statement,
            "COPY INTO @st/u FROM (SELECT 1 UNION ALL SELECT 2) FILE_FORMAT = (TYPE = CSV) SINGLE = TRUE"));
        assertEquals("false count:2 false end", walk(statement,
            "COPY INTO t2 (a) FROM @st/u FILE_FORMAT = (TYPE = CSV)"));
        assertEquals("false count:0 false end", walk(statement,
            "COPY INTO t2 (a) FROM @st/none FILE_FORMAT = (TYPE = CSV)"));
    }

    /** A query, SHOW, DESCRIBE, EXPLAIN, CALL, LIST and an anonymous block answer rows. */
    static void rowAnsweringStatementsAnswerRows(final Statement statement) throws SQLException {
        assertEquals("true rows:ID false end", walk(statement, "SELECT * FROM t ORDER BY id"));
        assertEquals("true rows:X false end", walk(statement, "SELECT 1 AS x WHERE FALSE"));
        assertEquals("true rows:created_on false end", walk(statement, "SHOW TABLES LIKE 'T2'"));
        assertEquals("true rows:name false end", walk(statement, "DESCRIBE TABLE t2"));
        assertEquals("true rows:P false end", walk(statement, "CALL p()"));
        assertEquals("true rows:ONE false end", walk(statement, "EXECUTE IMMEDIATE 'SELECT 1 AS one'"));
        assertEquals("true rows:anonymous block false end", walk(statement, "BEGIN\n  RETURN 5;\nEND;"));
        assertEquals("true rows:N false end", walk(statement, "SELECT COUNT(*) AS n FROM log"));
    }

    /**
     * executeUpdate answers the first statement's count, and refuses a statement that answers rows — after
     * running it: a refused CALL has done its work.
     */
    static void executeUpdateRefusesRows(final Statement statement) throws SQLException {
        assertEquals(2, statement.executeUpdate("INSERT INTO t VALUES (10, 'u'), (11, 'u')"));
        assertEquals(2, statement.getUpdateCount());
        assertEquals(0, statement.executeUpdate("CREATE OR REPLACE TABLE t6 (a INT)"));
        assertEquals(0, statement.getUpdateCount());
        assertEquals(0, statement.executeUpdate("SET v2 = 1"));
        assertRefusedUpdate(statement, "SELECT 1", "Statement 'SELECT 1' cannot be executed using current API.");
        assertRefusedUpdate(statement, "SHOW TABLES LIKE 'T6'",
            "Statement 'SHOW TABLES LIKE 'T6...' cannot be executed using current API.");
        assertRefusedUpdate(statement, "SELECT 1234567890123",
            "Statement 'SELECT 1234567890123' cannot be executed using current API.");
        assertRefusedUpdate(statement, "CALL p()", "Statement 'CALL p()' cannot be executed using current API.");
        try (final ResultSet ran = statement.executeQuery("SELECT COUNT(*) FROM log")) {
            assertTrue(ran.next());
            assertEquals(1, ran.getInt(1), "the refused CALL still ran");
        }
        assertEquals(2L, statement.executeLargeUpdate("INSERT INTO t VALUES (12, 'l'), (13, 'l')"));
        assertEquals(2L, statement.getLargeUpdateCount());
    }

    private static void assertRefusedUpdate(final Statement statement, final String sql, final String message) {
        final SQLException refused = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.executeUpdate(sql);
            }
        }, sql);
        assertEquals(message, refused.getMessage(), sql);
        assertEquals("0A000", refused.getSQLState(), sql);
        assertEquals(200042, refused.getErrorCode(), sql);
    }

    /**
     * executeQuery hands one statement's grid back as rows, whatever the statement: a count grid, a status.
     * Each on a fresh statement: Snowflake's driver leaves getUpdateCount() where the statement's previous
     * execution left it, so only a fresh one says -1 on both sides.
     */
    static void executeQueryReadsOneStatementsGrid(final Connection connection) throws SQLException {
        assertGrid(connection, "INSERT INTO t VALUES (20, 'q')", "number of rows inserted", "1");
        assertGrid(connection, "UPDATE t SET v = 'qq' WHERE id = 20", "number of rows updated", "1");
        assertGrid(connection, "CREATE OR REPLACE TABLE t7 (a INT)", "status", "Table T7 successfully created.");
        assertGrid(connection, "USE SCHEMA PUBLIC", "status", "Statement executed successfully.");
        assertGrid(connection, "INSERT ALL INTO t VALUES (a, 'm') INTO log VALUES (a) SELECT 310 AS a",
            "number of rows inserted into T", "1");
        assertGrid(connection, "INSERT ALL INTO t VALUES (a, 'm') INTO t VALUES (a + 1, 'n') SELECT 320 AS a",
            "number of rows inserted", "2");
    }

    private static void assertGrid(final Connection connection, final String sql, final String column,
                                   final String firstCell) throws SQLException {
        try (final Statement statement = connection.createStatement()) {
            try (final ResultSet grid = statement.executeQuery(sql)) {
                assertEquals(column, grid.getMetaData().getColumnLabel(1), sql);
                assertTrue(grid.next(), sql);
                assertEquals(firstCell, grid.getString(1), sql);
                assertFalse(grid.next(), sql);
            }
            assertEquals(-1, statement.getUpdateCount(), sql);
            assertFalse(statement.getMoreResults(), sql);
        }
    }

    /** A request of several statements walks as live's driver walks it, getMoreResults() quirk included. */
    static void severalStatementsWalkInOrder(final Statement statement) throws SQLException {
        statement.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
        assertEquals("false count:1 true rows:ONE true count:1 false count:0 false end", walk(statement,
            "INSERT INTO t VALUES (30,'m'); SELECT 1 AS one; UPDATE t SET v = 'mm' WHERE id = 30; "
            + "CREATE OR REPLACE TABLE t8 (a INT)"));
        assertEquals("true rows:TWO false count:1 false end",
            walk(statement, "SELECT 2 AS two; INSERT INTO t VALUES (31,'n')"));
        assertEquals("true rows:A true count:1 true rows:B false end",
            walk(statement, "SELECT 1 AS a; INSERT INTO t VALUES (32,'r'); SELECT 2 AS b"));
        assertEquals("true rows:A true count:0 true count:1 false count:0 false end",
            walk(statement, "SELECT 1 AS a; BEGIN; INSERT INTO t VALUES (33,'y'); COMMIT"));
        assertEquals(1, statement.executeUpdate("INSERT INTO t2 (a) VALUES (3); INSERT INTO t2 (a) VALUES (4), (5)"));
        assertFalse(statement.getMoreResults());
        assertEquals(2, statement.getUpdateCount());
        final SQLException refused = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.executeQuery("INSERT INTO t2 (a) VALUES (7); SELECT 7 AS seven");
            }
        });
        assertEquals("Query executed successfully, but the first statement returned an update count "
            + "(result set required).", refused.getMessage());
        assertEquals("01000", refused.getSQLState());
        assertEquals(200048, refused.getErrorCode());
        try (final ResultSet ran = statement.executeQuery("SELECT COUNT(*) FROM t2 WHERE a = 7")) {
            assertTrue(ran.next());
            assertEquals(1, ran.getInt(1), "the refused request still ran");
        }
        statement.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 1");
    }

    /**
     * A batch reports each statement's count, SUCCESS_NO_INFO for one that answers rows, and leaves the last
     * statement's count; a prepared batch runs as one array-bound statement and leaves the whole batch's count.
     */
    static void batchesCountEachStatement(final Connection connection) throws SQLException {
        try (final Statement batch = connection.createStatement()) {
            batch.addBatch("INSERT INTO t VALUES (40, 'b')");
            batch.addBatch("INSERT INTO t VALUES (41, 'b'), (42, 'c')");
            batch.addBatch("CREATE OR REPLACE TABLE t9 (a INT)");
            batch.addBatch("UPDATE t SET v = 'bb' WHERE id >= 40 AND id <= 42");
            assertEquals("[1, 2, 0, 3]", Arrays.toString(batch.executeBatch()));
            assertEquals(3, batch.getUpdateCount());
        }
        try (final Statement batch = connection.createStatement()) {
            batch.addBatch("INSERT INTO t VALUES (43, 'b')");
            batch.addBatch("SELECT 1");
            assertEquals("[1, " + Statement.SUCCESS_NO_INFO + "]", Arrays.toString(batch.executeBatch()));
        }
        try (final PreparedStatement prepared = connection.prepareStatement("INSERT INTO t VALUES (?, ?)")) {
            prepared.setInt(1, 50);
            prepared.setString(2, "p");
            prepared.addBatch();
            prepared.setInt(1, 51);
            prepared.setString(2, "p");
            prepared.addBatch();
            assertEquals("[1, 1]", Arrays.toString(prepared.executeBatch()));
            assertEquals(2, prepared.getUpdateCount());
        }
    }

    /** A prepared statement follows the same rule as a plain one. */
    static void preparedStatementsFollowTheRule(final Connection connection) throws SQLException {
        try (final PreparedStatement insert = connection.prepareStatement("INSERT INTO t VALUES (?, ?)")) {
            insert.setInt(1, 70);
            insert.setString(2, "pp");
            assertFalse(insert.execute());
            assertNull(insert.getResultSet());
            assertEquals(1, insert.getUpdateCount());
        }
        try (final PreparedStatement query = connection.prepareStatement("SELECT id FROM t WHERE id = ?")) {
            query.setInt(1, 70);
            assertTrue(query.execute());
            assertEquals(-1, query.getUpdateCount());
            final SQLException refused = assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    query.executeUpdate();
                }
            });
            assertEquals("Statement 'SELECT id FROM t WHE...' cannot be executed using current API.",
                refused.getMessage());
        }
        try (final PreparedStatement create = connection.prepareStatement("CREATE OR REPLACE TABLE t10 (a INT)")) {
            assertFalse(create.execute());
            assertEquals(0, create.getUpdateCount());
        }
    }
}
