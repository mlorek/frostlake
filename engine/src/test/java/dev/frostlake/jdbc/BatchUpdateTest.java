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
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BatchUpdateTest extends BaseJdbcTest {

    // Statement batch tests

    @Test
    public void testStatementBatchInsert() throws SQLException {
        statement.execute("CREATE TABLE batch1 (id INTEGER, name VARCHAR)");

        statement.addBatch("INSERT INTO batch1 VALUES (1, 'Alice')");
        statement.addBatch("INSERT INTO batch1 VALUES (2, 'Bob')");
        statement.addBatch("INSERT INTO batch1 VALUES (3, 'Charlie')");

        int[] results = statement.executeBatch();

        assertEquals(3, results.length);
        assertEquals(Statement.SUCCESS_NO_INFO, results[0]);
        assertEquals(Statement.SUCCESS_NO_INFO, results[1]);
        assertEquals(Statement.SUCCESS_NO_INFO, results[2]);

        ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM batch1");
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

        int[] results = statement.executeBatch();
        assertEquals(0, results.length);

        ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM batch2");
        assertTrue(rs.next());
        assertEquals(0, rs.getInt(1));
        rs.close();
    }

    @Test
    public void testStatementEmptyBatch() throws SQLException {
        statement.execute("CREATE TABLE batch3 (id INTEGER)");
        int[] results = statement.executeBatch();
        assertEquals(0, results.length);
    }

    @Test
    public void testStatementBatchWithNullSQL() throws SQLException {
        assertThrows(SQLException.class, () -> statement.addBatch(null));
    }

    @Test
    public void testStatementBatchWithEmptySQL() throws SQLException {
        assertThrows(SQLException.class, () -> statement.addBatch(""));
    }

    // PreparedStatement batch tests

    @Test
    public void testPreparedStatementBatchInsert() throws SQLException {
        statement.execute("CREATE TABLE batch4 (id INTEGER, name VARCHAR)");

        PreparedStatement pstmt = connection.prepareStatement("INSERT INTO batch4 VALUES (?, ?)");

        pstmt.setInt(1, 1);
        pstmt.setString(2, "Alice");
        pstmt.addBatch();

        pstmt.setInt(1, 2);
        pstmt.setString(2, "Bob");
        pstmt.addBatch();

        pstmt.setInt(1, 3);
        pstmt.setString(2, "Charlie");
        pstmt.addBatch();

        int[] results = pstmt.executeBatch();

        assertEquals(3, results.length);
        assertEquals(Statement.SUCCESS_NO_INFO, results[0]);
        assertEquals(Statement.SUCCESS_NO_INFO, results[1]);
        assertEquals(Statement.SUCCESS_NO_INFO, results[2]);

        ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM batch4");
        assertTrue(rs.next());
        assertEquals(3, rs.getInt(1));
        rs.close();

        pstmt.close();
    }

    @Test
    public void testPreparedStatementBatchUpdate() throws SQLException {
        statement.execute("CREATE TABLE batch5 (id INTEGER, value INTEGER)");
        statement.execute("INSERT INTO batch5 VALUES (1, 100), (2, 200), (3, 300)");

        PreparedStatement pstmt = connection.prepareStatement("UPDATE batch5 SET value = ? WHERE id = ?");

        pstmt.setInt(1, 150);
        pstmt.setInt(2, 1);
        pstmt.addBatch();

        pstmt.setInt(1, 250);
        pstmt.setInt(2, 2);
        pstmt.addBatch();

        int[] results = pstmt.executeBatch();

        assertEquals(2, results.length);

        ResultSet rs = statement.executeQuery("SELECT value FROM batch5 WHERE id = 1");
        assertTrue(rs.next());
        assertEquals(150, rs.getInt(1));
        rs.close();

        pstmt.close();
    }

    @Test
    public void testPreparedStatementClearBatch() throws SQLException {
        statement.execute("CREATE TABLE batch6 (id INTEGER)");

        PreparedStatement pstmt = connection.prepareStatement("INSERT INTO batch6 VALUES (?)");

        pstmt.setInt(1, 1);
        pstmt.addBatch();
        pstmt.setInt(1, 2);
        pstmt.addBatch();

        pstmt.clearBatch();

        int[] results = pstmt.executeBatch();
        assertEquals(0, results.length);

        ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM batch6");
        assertTrue(rs.next());
        assertEquals(0, rs.getInt(1));
        rs.close();

        pstmt.close();
    }

    @Test
    public void testPreparedStatementEmptyBatch() throws SQLException {
        statement.execute("CREATE TABLE batch7 (id INTEGER)");
        PreparedStatement pstmt = connection.prepareStatement("INSERT INTO batch7 VALUES (?)");

        int[] results = pstmt.executeBatch();
        assertEquals(0, results.length);

        pstmt.close();
    }

    @Test
    public void testPreparedStatementBatchWithManyValues() throws SQLException {
        statement.execute("CREATE TABLE batch8 (id INTEGER, name VARCHAR)");

        PreparedStatement pstmt = connection.prepareStatement("INSERT INTO batch8 VALUES (?, ?)");

        for (int i = 1; i <= 10; i++) {
            pstmt.setInt(1, i);
            pstmt.setString(2, "User" + i);
            pstmt.addBatch();
        }

        int[] results = pstmt.executeBatch();

        assertEquals(10, results.length);
        for (int i = 0; i < 10; i++) {
            assertEquals(Statement.SUCCESS_NO_INFO, results[i]);
        }

        ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM batch8");
        assertTrue(rs.next());
        assertEquals(10, rs.getInt(1));
        rs.close();

        pstmt.close();
    }

    @Test
    public void testPreparedStatementBatchReuseAfterExecution() throws SQLException {
        statement.execute("CREATE TABLE batch9 (id INTEGER)");

        PreparedStatement pstmt = connection.prepareStatement("INSERT INTO batch9 VALUES (?)");

        pstmt.setInt(1, 1);
        pstmt.addBatch();
        pstmt.setInt(1, 2);
        pstmt.addBatch();

        int[] results1 = pstmt.executeBatch();
        assertEquals(2, results1.length);

        pstmt.setInt(1, 3);
        pstmt.addBatch();

        int[] results2 = pstmt.executeBatch();
        assertEquals(1, results2.length);

        ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM batch9");
        assertTrue(rs.next());
        assertEquals(3, rs.getInt(1));
        rs.close();

        pstmt.close();
    }

    @Test
    public void testPreparedStatementBatchWithNullValues() throws SQLException {
        statement.execute("CREATE TABLE batch10 (id INTEGER, name VARCHAR)");

        PreparedStatement pstmt = connection.prepareStatement("INSERT INTO batch10 VALUES (?, ?)");

        pstmt.setInt(1, 1);
        pstmt.setNull(2, Types.VARCHAR);
        pstmt.addBatch();

        pstmt.setInt(1, 2);
        pstmt.setString(2, "Bob");
        pstmt.addBatch();

        int[] results = pstmt.executeBatch();

        assertEquals(2, results.length);
        assertEquals(Statement.SUCCESS_NO_INFO, results[0]);
        assertEquals(Statement.SUCCESS_NO_INFO, results[1]);

        ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM batch10");
        assertTrue(rs.next());
        assertEquals(2, rs.getInt(1));
        rs.close();

        pstmt.close();
    }

    @Test
    public void testLargeBatch() throws SQLException {
        statement.execute("CREATE TABLE batch11 (id INTEGER, value INTEGER)");

        PreparedStatement pstmt = connection.prepareStatement("INSERT INTO batch11 VALUES (?, ?)");

        for (int i = 1; i <= 50; i++) {
            pstmt.setInt(1, i);
            pstmt.setInt(2, i * 10);
            pstmt.addBatch();
        }

        int[] results = pstmt.executeBatch();

        assertEquals(50, results.length);
        for (int i = 0; i < 50; i++) {
            assertEquals(Statement.SUCCESS_NO_INFO, results[i]);
        }

        ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM batch11");
        assertTrue(rs.next());
        assertEquals(50, rs.getInt(1));
        rs.close();

        pstmt.close();
    }
}
