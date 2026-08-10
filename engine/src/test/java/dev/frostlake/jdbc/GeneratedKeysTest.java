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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GeneratedKeysTest extends BaseJdbcTest {

    private static final String RETURN_GENERATED_KEYS =
        "asks the driver for generated keys (RETURN_GENERATED_KEYS / column indexes / column "
        + "names); Snowflake's own driver answers that request with "
        + "SnowflakeLoggedFeatureNotSupportedException — generated-key retrieval is a Frostlake "
        + "driver feature, not an account feature";

    @Test
    public void testStatementWithoutGeneratedKeys() throws SQLException {
        statement.execute("CREATE TABLE gen_keys1 (id INTEGER, name VARCHAR)");
        statement.executeUpdate("INSERT INTO gen_keys1 VALUES (1, 'Alice')");

        // Without RETURN_GENERATED_KEYS flag, should return empty result set
        final ResultSet rs = statement.getGeneratedKeys();
        assertNotNull(rs);
        assertFalse(rs.next());
        rs.close();
    }

    @Test
    public void testStatementWithGeneratedKeysFlag() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), RETURN_GENERATED_KEYS);
        statement.execute("CREATE TABLE gen_keys2 (id INTEGER, name VARCHAR)");
        statement.executeUpdate(
            "INSERT INTO gen_keys2 VALUES (1, 'Alice')",
            Statement.RETURN_GENERATED_KEYS
        );

        final ResultSet rs = statement.getGeneratedKeys();
        assertNotNull(rs);
        assertTrue(rs.next());
        // Should have a generated key
        final long key = rs.getLong(1);
        assertTrue(key > 0);
        assertFalse(rs.next());
        rs.close();
    }

    @Test
    public void testStatementExecuteWithGeneratedKeys() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), RETURN_GENERATED_KEYS);
        statement.execute("CREATE TABLE gen_keys3 (id INTEGER, name VARCHAR)");
        statement.execute(
            "INSERT INTO gen_keys3 VALUES (1, 'Alice')",
            Statement.RETURN_GENERATED_KEYS
        );

        final ResultSet rs = statement.getGeneratedKeys();
        assertNotNull(rs);
        assertTrue(rs.next());
        final long key = rs.getLong(1);
        assertTrue(key > 0);
        rs.close();
    }

    @Test
    public void testStatementWithColumnIndexes() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), RETURN_GENERATED_KEYS);
        statement.execute("CREATE TABLE gen_keys4 (id INTEGER, name VARCHAR)");
        statement.executeUpdate(
            "INSERT INTO gen_keys4 VALUES (1, 'Alice')",
            new int[]{1}
        );

        final ResultSet rs = statement.getGeneratedKeys();
        assertNotNull(rs);
        assertTrue(rs.next());
        final long key = rs.getLong(1);
        assertTrue(key > 0);
        rs.close();
    }

    @Test
    public void testStatementWithColumnNames() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), RETURN_GENERATED_KEYS);
        statement.execute("CREATE TABLE gen_keys5 (id INTEGER, name VARCHAR)");
        statement.executeUpdate(
            "INSERT INTO gen_keys5 VALUES (1, 'Alice')",
            new String[]{"id"}
        );

        final ResultSet rs = statement.getGeneratedKeys();
        assertNotNull(rs);
        assertTrue(rs.next());
        final long key = rs.getLong(1);
        assertTrue(key > 0);
        rs.close();
    }

    @Test
    public void testPreparedStatementWithGeneratedKeys() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), RETURN_GENERATED_KEYS);
        statement.execute("CREATE TABLE gen_keys6 (id INTEGER, name VARCHAR)");

        final PreparedStatement pstmt = connection.prepareStatement(
            "INSERT INTO gen_keys6 VALUES (?, ?)",
            Statement.RETURN_GENERATED_KEYS
        );

        pstmt.setInt(1, 1);
        pstmt.setString(2, "Alice");
        pstmt.executeUpdate();

        final ResultSet rs = pstmt.getGeneratedKeys();
        assertNotNull(rs);
        assertTrue(rs.next());
        final long key = rs.getLong(1);
        assertTrue(key > 0);
        assertFalse(rs.next());
        rs.close();

        pstmt.close();
    }

    @Test
    public void testPreparedStatementWithColumnIndexes() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), RETURN_GENERATED_KEYS);
        statement.execute("CREATE TABLE gen_keys7 (id INTEGER, name VARCHAR)");

        final PreparedStatement pstmt = connection.prepareStatement(
            "INSERT INTO gen_keys7 VALUES (?, ?)",
            new int[]{1}
        );

        pstmt.setInt(1, 1);
        pstmt.setString(2, "Alice");
        pstmt.executeUpdate();

        final ResultSet rs = pstmt.getGeneratedKeys();
        assertNotNull(rs);
        assertTrue(rs.next());
        final long key = rs.getLong(1);
        assertTrue(key > 0);
        rs.close();

        pstmt.close();
    }

    @Test
    public void testPreparedStatementWithColumnNames() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), RETURN_GENERATED_KEYS);
        statement.execute("CREATE TABLE gen_keys8 (id INTEGER, name VARCHAR)");

        final PreparedStatement pstmt = connection.prepareStatement(
            "INSERT INTO gen_keys8 VALUES (?, ?)",
            new String[]{"id"}
        );

        pstmt.setInt(1, 1);
        pstmt.setString(2, "Alice");
        pstmt.executeUpdate();

        final ResultSet rs = pstmt.getGeneratedKeys();
        assertNotNull(rs);
        assertTrue(rs.next());
        final long key = rs.getLong(1);
        assertTrue(key > 0);
        rs.close();

        pstmt.close();
    }

    @Test
    public void testMultipleInserts() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), RETURN_GENERATED_KEYS);
        statement.execute("CREATE TABLE gen_keys9 (id INTEGER, name VARCHAR)");

        statement.executeUpdate(
            "INSERT INTO gen_keys9 VALUES (1, 'Alice')",
            Statement.RETURN_GENERATED_KEYS
        );

        final ResultSet rs1 = statement.getGeneratedKeys();
        assertTrue(rs1.next());
        final long key1 = rs1.getLong(1);
        rs1.close();

        // Second insert - should get different key
        statement.executeUpdate(
            "INSERT INTO gen_keys9 VALUES (2, 'Bob')",
            Statement.RETURN_GENERATED_KEYS
        );

        final ResultSet rs2 = statement.getGeneratedKeys();
        assertTrue(rs2.next());
        final long key2 = rs2.getLong(1);
        rs2.close();

        // Keys should be different
        assertTrue(key1 != key2);
    }

    @Test
    public void testGeneratedKeysAfterSelect() throws SQLException {
        statement.execute("CREATE TABLE gen_keys10 (id INTEGER, name VARCHAR)");
        statement.execute("INSERT INTO gen_keys10 VALUES (1, 'Alice')");

        // SELECT should not generate keys
        statement.executeQuery("SELECT * FROM gen_keys10");

        final ResultSet rs = statement.getGeneratedKeys();
        assertNotNull(rs);
        assertFalse(rs.next());
        rs.close();
    }

    @Test
    public void testGeneratedKeysAfterUpdate() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), RETURN_GENERATED_KEYS);
        statement.execute("CREATE TABLE gen_keys11 (id INTEGER, name VARCHAR)");
        statement.execute("INSERT INTO gen_keys11 VALUES (1, 'Alice')");

        // UPDATE should not generate keys
        statement.executeUpdate(
            "UPDATE gen_keys11 SET name = 'Bob' WHERE id = 1",
            Statement.RETURN_GENERATED_KEYS
        );

        final ResultSet rs = statement.getGeneratedKeys();
        assertNotNull(rs);
        assertFalse(rs.next());
        rs.close();
    }

    @Test
    public void testGeneratedKeysResultSetMetadata() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), RETURN_GENERATED_KEYS);
        statement.execute("CREATE TABLE gen_keys12 (id INTEGER, name VARCHAR)");
        statement.executeUpdate(
            "INSERT INTO gen_keys12 VALUES (1, 'Alice')",
            Statement.RETURN_GENERATED_KEYS
        );

        final ResultSet rs = statement.getGeneratedKeys();
        assertNotNull(rs);

        // Check metadata
        assertEquals(1, rs.getMetaData().getColumnCount());
        assertEquals("GENERATED_KEY", rs.getMetaData().getColumnName(1));

        rs.close();
    }

    @Test
    public void testPreparedStatementExecuteWithGeneratedKeys() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), RETURN_GENERATED_KEYS);
        statement.execute("CREATE TABLE gen_keys13 (id INTEGER, name VARCHAR)");

        final PreparedStatement pstmt = connection.prepareStatement(
            "INSERT INTO gen_keys13 VALUES (?, ?)",
            Statement.RETURN_GENERATED_KEYS
        );

        pstmt.setInt(1, 1);
        pstmt.setString(2, "Alice");
        pstmt.execute();

        final ResultSet rs = pstmt.getGeneratedKeys();
        assertNotNull(rs);
        assertTrue(rs.next());
        final long key = rs.getLong(1);
        assertTrue(key > 0);
        rs.close();

        pstmt.close();
    }

    @Test
    public void testPreparedStatementMultipleExecutions() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), RETURN_GENERATED_KEYS);
        statement.execute("CREATE TABLE gen_keys14 (id INTEGER, name VARCHAR)");

        final PreparedStatement pstmt = connection.prepareStatement(
            "INSERT INTO gen_keys14 VALUES (?, ?)",
            Statement.RETURN_GENERATED_KEYS
        );

        // First execution
        pstmt.setInt(1, 1);
        pstmt.setString(2, "Alice");
        pstmt.executeUpdate();

        final ResultSet rs1 = pstmt.getGeneratedKeys();
        assertTrue(rs1.next());
        final long key1 = rs1.getLong(1);
        rs1.close();

        // Second execution
        pstmt.setInt(1, 2);
        pstmt.setString(2, "Bob");
        pstmt.executeUpdate();

        final ResultSet rs2 = pstmt.getGeneratedKeys();
        assertTrue(rs2.next());
        final long key2 = rs2.getLong(1);
        rs2.close();

        // Keys should be different
        assertTrue(key1 != key2);

        pstmt.close();
    }

    @Test
    public void testEmptyGeneratedKeysAfterNonInsert() throws SQLException {
        statement.execute("CREATE TABLE gen_keys15 (id INTEGER, name VARCHAR)");

        // Create without generated keys flag
        statement.execute("CREATE TABLE gen_keys15_temp (id INTEGER)");

        final ResultSet rs = statement.getGeneratedKeys();
        assertNotNull(rs);
        assertFalse(rs.next());
        rs.close();
    }
}
