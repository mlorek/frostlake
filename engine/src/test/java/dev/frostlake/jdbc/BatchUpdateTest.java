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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.BatchUpdateException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BatchUpdateTest extends BaseJdbcTest {

    private static final String EMPTY_BATCH_SQL =
        "asserts that the DRIVER rejects an empty / null batch statement, which is Frostlake's own "
        + "contract; the Snowflake JDBC driver accepts it and only fails later, when the batch runs";

    // Statement batch tests

    @Test
    public void testStatementBatchInsert() throws SQLException {
        statement.execute("CREATE TABLE batch1 (id INTEGER, name VARCHAR)");

        statement.addBatch("INSERT INTO batch1 VALUES (1, 'Alice')");
        statement.addBatch("INSERT INTO batch1 VALUES (2, 'Bob')");
        statement.addBatch("INSERT INTO batch1 VALUES (3, 'Charlie')");

        final int[] results = statement.executeBatch();

        assertEquals(3, results.length);
        assertEquals(1, results[0]);
        assertEquals(1, results[1]);
        assertEquals(1, results[2]);

        final ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM batch1");
        assertTrue(rs.next());
        assertEquals(3, rs.getInt(1));
        rs.close();
    }

    @Test
    public void testStatementClearBatch() throws SQLException {
        statement.execute("CREATE TABLE batch2 (id INTEGER)");

        statement.addBatch("INSERT INTO batch2 VALUES (1)");
        statement.addBatch("INSERT INTO batch2 VALUES (2)");
        statement.clearBatch();

        final int[] results = statement.executeBatch();
        assertEquals(0, results.length);

        final ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM batch2");
        assertTrue(rs.next());
        assertEquals(0, rs.getInt(1));
        rs.close();
    }

    @Test
    public void testStatementEmptyBatch() throws SQLException {
        statement.execute("CREATE TABLE batch3 (id INTEGER)");
        final int[] results = statement.executeBatch();
        assertEquals(0, results.length);
    }

    @Test
    public void testStatementBatchWithNullSQL() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), EMPTY_BATCH_SQL);
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.addBatch(null);
            }
        });
    }

    @Test
    public void testStatementBatchWithEmptySQL() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), EMPTY_BATCH_SQL);
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.addBatch("");
            }
        });
    }

    // PreparedStatement batch tests

    @Test
    public void testPreparedStatementBatchInsert() throws SQLException {
        statement.execute("CREATE TABLE batch4 (id INTEGER, name VARCHAR)");

        final PreparedStatement pstmt = connection.prepareStatement("INSERT INTO batch4 VALUES (?, ?)");

        pstmt.setInt(1, 1);
        pstmt.setString(2, "Alice");
        pstmt.addBatch();

        pstmt.setInt(1, 2);
        pstmt.setString(2, "Bob");
        pstmt.addBatch();

        pstmt.setInt(1, 3);
        pstmt.setString(2, "Charlie");
        pstmt.addBatch();

        final int[] results = pstmt.executeBatch();

        assertEquals(3, results.length);
        assertEquals(1, results[0]);
        assertEquals(1, results[1]);
        assertEquals(1, results[2]);

        final ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM batch4");
        assertTrue(rs.next());
        assertEquals(3, rs.getInt(1));
        rs.close();

        pstmt.close();
    }

    @Test
    public void testPreparedStatementBatchUpdate() throws SQLException {
        statement.execute("CREATE TABLE batch5 (id INTEGER, value INTEGER)");
        statement.execute("INSERT INTO batch5 VALUES (1, 100), (2, 200), (3, 300)");

        final PreparedStatement pstmt = connection.prepareStatement("UPDATE batch5 SET value = ? WHERE id = ?");

        pstmt.setInt(1, 150);
        pstmt.setInt(2, 1);
        pstmt.addBatch();

        pstmt.setInt(1, 250);
        pstmt.setInt(2, 2);
        pstmt.addBatch();

        final int[] results = pstmt.executeBatch();

        assertEquals(2, results.length);

        final ResultSet rs = statement.executeQuery("SELECT value FROM batch5 WHERE id = 1");
        assertTrue(rs.next());
        assertEquals(150, rs.getInt(1));
        rs.close();

        pstmt.close();
    }

    @Test
    public void testPreparedStatementClearBatch() throws SQLException {
        statement.execute("CREATE TABLE batch6 (id INTEGER)");

        final PreparedStatement pstmt = connection.prepareStatement("INSERT INTO batch6 VALUES (?)");

        pstmt.setInt(1, 1);
        pstmt.addBatch();
        pstmt.setInt(1, 2);
        pstmt.addBatch();

        pstmt.clearBatch();

        final int[] results = pstmt.executeBatch();
        assertEquals(0, results.length);

        final ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM batch6");
        assertTrue(rs.next());
        assertEquals(0, rs.getInt(1));
        rs.close();

        pstmt.close();
    }

    @Test
    public void testPreparedStatementEmptyBatch() throws SQLException {
        statement.execute("CREATE TABLE batch7 (id INTEGER)");
        final PreparedStatement pstmt = connection.prepareStatement("INSERT INTO batch7 VALUES (?)");

        final int[] results = pstmt.executeBatch();
        assertEquals(0, results.length);

        pstmt.close();
    }

    @Test
    public void testPreparedStatementBatchWithManyValues() throws SQLException {
        statement.execute("CREATE TABLE batch8 (id INTEGER, name VARCHAR)");

        final PreparedStatement pstmt = connection.prepareStatement("INSERT INTO batch8 VALUES (?, ?)");

        for (int i = 1; i <= 10; i++) {
            pstmt.setInt(1, i);
            pstmt.setString(2, "User" + i);
            pstmt.addBatch();
        }

        final int[] results = pstmt.executeBatch();

        assertEquals(10, results.length);
        for (int i = 0; i < 10; i++) {
            assertEquals(1, results[i]);
        }

        final ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM batch8");
        assertTrue(rs.next());
        assertEquals(10, rs.getInt(1));
        rs.close();

        pstmt.close();
    }

    @Test
    public void testPreparedStatementBatchReuseAfterExecution() throws SQLException {
        statement.execute("CREATE TABLE batch9 (id INTEGER)");

        final PreparedStatement pstmt = connection.prepareStatement("INSERT INTO batch9 VALUES (?)");

        pstmt.setInt(1, 1);
        pstmt.addBatch();
        pstmt.setInt(1, 2);
        pstmt.addBatch();

        final int[] results1 = pstmt.executeBatch();
        assertEquals(2, results1.length);

        pstmt.setInt(1, 3);
        pstmt.addBatch();

        final int[] results2 = pstmt.executeBatch();
        assertEquals(1, results2.length);

        final ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM batch9");
        assertTrue(rs.next());
        assertEquals(3, rs.getInt(1));
        rs.close();

        pstmt.close();
    }

    @Test
    public void testPreparedStatementBatchWithNullValues() throws SQLException {
        statement.execute("CREATE TABLE batch10 (id INTEGER, name VARCHAR)");

        final PreparedStatement pstmt = connection.prepareStatement("INSERT INTO batch10 VALUES (?, ?)");

        pstmt.setInt(1, 1);
        pstmt.setNull(2, Types.VARCHAR);
        pstmt.addBatch();

        pstmt.setInt(1, 2);
        pstmt.setString(2, "Bob");
        pstmt.addBatch();

        final int[] results = pstmt.executeBatch();

        assertEquals(2, results.length);
        assertEquals(1, results[0]);
        assertEquals(1, results[1]);

        final ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM batch10");
        assertTrue(rs.next());
        assertEquals(2, rs.getInt(1));
        rs.close();

        pstmt.close();
    }

    // Failure surface (live-verified)

    @Test
    public void testStatementBatchFailureContinuesAndThrowsBatchUpdateException() throws SQLException {
        statement.execute("CREATE TABLE batch12 (id INTEGER)");

        statement.addBatch("INSERT INTO batch12 VALUES (20)");
        statement.addBatch("INSERT INTO missing_t VALUES (1)");
        statement.addBatch("INSERT INTO batch12 VALUES (21)");

        final BatchUpdateException e = assertThrows(BatchUpdateException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.executeBatch();
            }
        });
        assertTrue(e.getMessage().contains("does not exist"), e.getMessage());
        assertEquals(3, e.getUpdateCounts().length);
        assertEquals(1, e.getUpdateCounts()[0]);
        assertEquals(Statement.EXECUTE_FAILED, e.getUpdateCounts()[1]);
        assertEquals(1, e.getUpdateCounts()[2]);

        // Entries after the failing one still executed.
        final ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM batch12");
        assertTrue(rs.next());
        assertEquals(2, rs.getInt(1));
        rs.close();
    }

    @Test
    public void testStatementBatchTwoFailuresReportTheFirst() throws SQLException {
        statement.execute("CREATE TABLE batch13 (id INTEGER)");

        statement.addBatch("INSERT INTO missing_a VALUES (1)");
        statement.addBatch("INSERT INTO batch13 VALUES (40)");
        statement.addBatch("INSERT INTO missing_b VALUES (2)");

        final BatchUpdateException e = assertThrows(BatchUpdateException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.executeBatch();
            }
        });
        assertTrue(e.getMessage().contains("MISSING_A"), e.getMessage());
        assertEquals(Statement.EXECUTE_FAILED, e.getUpdateCounts()[0]);
        assertEquals(1, e.getUpdateCounts()[1]);
        assertEquals(Statement.EXECUTE_FAILED, e.getUpdateCounts()[2]);
    }

    @Test
    public void testPreparedBatchFailureIsThePlainStatementException() throws SQLException {
        final PreparedStatement pstmt = connection.prepareStatement("INSERT INTO missing_p VALUES (?)");

        // Live's driver describes the statement at the first addBatch, so the compile error can
        // surface there; Frostlake compiles at execution, so it surfaces at executeBatch. Either
        // way the batch refuses with the statement's own plain SQLException — never a
        // BatchUpdateException.
        final SQLException e = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                pstmt.setInt(1, 1);
                pstmt.addBatch();
                pstmt.executeBatch();
            }
        });
        assertFalse(e instanceof BatchUpdateException, e.getClass().getName());
        assertTrue(e.getMessage().contains("does not exist"), e.getMessage());
        pstmt.close();
    }

    @Test
    public void testPreparedBatchStopsAtFirstFailingRow() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(),
            "live array binding is all-or-nothing; Frostlake executes per row, keeping rows before the failure");
        statement.execute("CREATE TABLE batch14 (n NUMBER(10,0))");

        final PreparedStatement pstmt = connection.prepareStatement("INSERT INTO batch14 VALUES (?)");
        pstmt.setString(1, "10");
        pstmt.addBatch();
        pstmt.setString(1, "abc");
        pstmt.addBatch();
        pstmt.setString(1, "12");
        pstmt.addBatch();

        final SQLException e = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                pstmt.executeBatch();
            }
        });
        assertFalse(e instanceof BatchUpdateException, e.getClass().getName());
        assertTrue(e.getMessage().contains("Numeric value 'abc' is not recognized"), e.getMessage());

        // The row before the failure was applied; the row after it was never attempted.
        final ResultSet rs = statement.executeQuery("SELECT n FROM batch14");
        assertTrue(rs.next());
        assertEquals(10, rs.getInt(1));
        assertFalse(rs.next());
        rs.close();
        pstmt.close();
    }

    @Test
    public void testMixedBindTypesRefusedAtAddBatch() throws SQLException {
        statement.execute("CREATE TABLE batch15 (n NUMBER(10,0))");

        final PreparedStatement pstmt = connection.prepareStatement("INSERT INTO batch15 VALUES (?)");
        pstmt.setInt(1, 10);
        pstmt.addBatch();
        pstmt.setString(1, "abc");

        final SQLException e = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                pstmt.addBatch();
            }
        });
        assertEquals("Array bind for values with mixed types not supported. "
            + "Previous type: JAVA_BIGDECIMAL, Current type: JAVA_STRING at Column: 1, Row: 2.",
            e.getMessage());
        assertEquals("0A000", e.getSQLState());
        assertEquals(200023, e.getErrorCode());
        pstmt.close();
    }

    @Test
    public void testLargeBatch() throws SQLException {
        statement.execute("CREATE TABLE batch11 (id INTEGER, value INTEGER)");

        final PreparedStatement pstmt = connection.prepareStatement("INSERT INTO batch11 VALUES (?, ?)");

        for (int i = 1; i <= 50; i++) {
            pstmt.setInt(1, i);
            pstmt.setInt(2, i * 10);
            pstmt.addBatch();
        }

        final int[] results = pstmt.executeBatch();

        assertEquals(50, results.length);
        for (int i = 0; i < 50; i++) {
            assertEquals(1, results[i]);
        }

        final ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM batch11");
        assertTrue(rs.next());
        assertEquals(50, rs.getInt(1));
        rs.close();

        pstmt.close();
    }
}
