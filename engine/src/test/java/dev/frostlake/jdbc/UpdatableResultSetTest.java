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
import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UpdatableResultSetTest extends BaseJdbcTest {

    private DirectResultSet createUpdatableResultSet(final String sql, final String tableName, final String... keyColumns) throws SQLException {
        statement.execute(sql);
        // Read through the connection's session scope: a raw sharedEngine call resolves against the
        // engine's GLOBAL context, and the connection's USE test_db no longer leaks into it.
        ResultSet engineResultSet = ((DirectConnection) connection)
            .executeScoped("SELECT * FROM " + tableName).getResultSets().get(0);
        return new DirectResultSet(statement, engineResultSet, sharedEngine, tableName, Arrays.asList(keyColumns));
    }

    @Test
    public void testUpdateRow() throws SQLException {
        statement.execute("CREATE TABLE upd_test1 (id INTEGER, name VARCHAR)");
        statement.execute("INSERT INTO upd_test1 VALUES (1, 'Alice'), (2, 'Bob')");

        DirectResultSet rs = createUpdatableResultSet("SELECT * FROM upd_test1", "upd_test1", "id");

        // Move to first row
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));
        assertEquals("Alice", rs.getString("name"));

        // Update name
        rs.updateString("name", "Alice Updated");
        rs.updateRow();

        // Verify update
        rs.close();
        java.sql.ResultSet verifyRs = statement.executeQuery("SELECT name FROM upd_test1 WHERE id = 1");
        assertTrue(verifyRs.next());
        assertEquals("Alice Updated", verifyRs.getString("name"));
        verifyRs.close();
    }

    @Test
    public void testUpdateMultipleColumns() throws SQLException {
        statement.execute("CREATE TABLE upd_test2 (id INTEGER, name VARCHAR, age INTEGER)");
        statement.execute("INSERT INTO upd_test2 VALUES (1, 'John', 25)");

        DirectResultSet rs = createUpdatableResultSet("SELECT * FROM upd_test2", "upd_test2", "id");

        assertTrue(rs.next());
        rs.updateString("name", "John Updated");
        rs.updateInt("age", 30);
        rs.updateRow();

        rs.close();
        java.sql.ResultSet verifyRs = statement.executeQuery("SELECT * FROM upd_test2 WHERE id = 1");
        assertTrue(verifyRs.next());
        assertEquals("John Updated", verifyRs.getString("name"));
        assertEquals(30, verifyRs.getInt("age"));
        verifyRs.close();
    }

    @Test
    public void testCancelRowUpdates() throws SQLException {
        statement.execute("CREATE TABLE upd_test3 (id INTEGER, name VARCHAR)");
        statement.execute("INSERT INTO upd_test3 VALUES (1, 'Original')");

        DirectResultSet rs = createUpdatableResultSet("SELECT * FROM upd_test3", "upd_test3", "id");

        assertTrue(rs.next());
        rs.updateString("name", "Modified");
        rs.cancelRowUpdates();

        // Verify original value unchanged (no updateRow() called)
        rs.close();
        java.sql.ResultSet verifyRs = statement.executeQuery("SELECT name FROM upd_test3 WHERE id = 1");
        assertTrue(verifyRs.next());
        assertEquals("Original", verifyRs.getString("name"));
        verifyRs.close();
    }

    @Test
    public void testInsertRow() throws SQLException {
        statement.execute("CREATE TABLE upd_test4 (id INTEGER, name VARCHAR)");

        DirectResultSet rs = createUpdatableResultSet("SELECT * FROM upd_test4", "upd_test4", "id");

        rs.moveToInsertRow();
        rs.updateInt("id", 1);
        rs.updateString("name", "New Row");
        rs.insertRow();
        rs.moveToCurrentRow();

        rs.close();
        java.sql.ResultSet verifyRs = statement.executeQuery("SELECT * FROM upd_test4 WHERE id = 1");
        assertTrue(verifyRs.next());
        assertEquals("New Row", verifyRs.getString("name"));
        verifyRs.close();
    }

    @Test
    public void testInsertMultipleRows() throws SQLException {
        statement.execute("CREATE TABLE upd_test5 (id INTEGER, value VARCHAR)");

        DirectResultSet rs = createUpdatableResultSet("SELECT * FROM upd_test5", "upd_test5", "id");

        rs.moveToInsertRow();
        rs.updateInt("id", 1);
        rs.updateString("value", "First");
        rs.insertRow();

        rs.moveToInsertRow();
        rs.updateInt("id", 2);
        rs.updateString("value", "Second");
        rs.insertRow();

        rs.moveToCurrentRow();
        rs.close();

        java.sql.ResultSet verifyRs = statement.executeQuery("SELECT COUNT(*) FROM upd_test5");
        assertTrue(verifyRs.next());
        assertEquals(2, verifyRs.getInt(1));
        verifyRs.close();
    }

    @Test
    public void testDeleteRow() throws SQLException {
        statement.execute("CREATE TABLE upd_test6 (id INTEGER, name VARCHAR)");
        statement.execute("INSERT INTO upd_test6 VALUES (1, 'To Delete'), (2, 'To Keep')");

        DirectResultSet rs = createUpdatableResultSet("SELECT * FROM upd_test6", "upd_test6", "id");

        // Delete first row
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));
        rs.deleteRow();

        rs.close();
        java.sql.ResultSet verifyRs = statement.executeQuery("SELECT COUNT(*) FROM upd_test6");
        assertTrue(verifyRs.next());
        assertEquals(1, verifyRs.getInt(1));
        verifyRs.close();

        verifyRs = statement.executeQuery("SELECT id FROM upd_test6");
        assertTrue(verifyRs.next());
        assertEquals(2, verifyRs.getInt("id"));
        verifyRs.close();
    }

    @Test
    public void testUpdateWithMultipleKeyColumns() throws SQLException {
        statement.execute("CREATE TABLE upd_test7 (id1 INTEGER, id2 INTEGER, value VARCHAR)");
        statement.execute("INSERT INTO upd_test7 VALUES (1, 1, 'Original')");

        DirectResultSet rs = createUpdatableResultSet("SELECT * FROM upd_test7", "upd_test7", "id1", "id2");

        assertTrue(rs.next());
        rs.updateString("value", "Updated");
        rs.updateRow();

        rs.close();
        java.sql.ResultSet verifyRs = statement.executeQuery("SELECT value FROM upd_test7 WHERE id1 = 1 AND id2 = 1");
        assertTrue(verifyRs.next());
        assertEquals("Updated", verifyRs.getString("value"));
        verifyRs.close();
    }

    @Test
    public void testUpdateNull() throws SQLException {
        statement.execute("CREATE TABLE upd_test8 (id INTEGER, name VARCHAR)");
        statement.execute("INSERT INTO upd_test8 VALUES (1, 'Original')");

        DirectResultSet rs = createUpdatableResultSet("SELECT * FROM upd_test8", "upd_test8", "id");

        assertTrue(rs.next());
        rs.updateNull("name");
        rs.updateRow();

        rs.close();
        java.sql.ResultSet verifyRs = statement.executeQuery("SELECT name FROM upd_test8 WHERE id = 1");
        assertTrue(verifyRs.next());
        assertEquals(null, verifyRs.getString("name"));
        verifyRs.close();
    }

    @Test
    public void testInsertRowWithNull() throws SQLException {
        statement.execute("CREATE TABLE upd_test9 (id INTEGER, name VARCHAR)");

        DirectResultSet rs = createUpdatableResultSet("SELECT * FROM upd_test9", "upd_test9", "id");

        rs.moveToInsertRow();
        rs.updateInt("id", 1);
        rs.updateNull("name");
        rs.insertRow();
        rs.moveToCurrentRow();

        rs.close();
        java.sql.ResultSet verifyRs = statement.executeQuery("SELECT name FROM upd_test9 WHERE id = 1");
        assertTrue(verifyRs.next());
        assertEquals(null, verifyRs.getString("name"));
        verifyRs.close();
    }

    @Test
    public void testUpdateNumericTypes() throws SQLException {
        statement.execute("CREATE TABLE upd_test10 (id INTEGER, int_val INTEGER, long_val BIGINT, double_val DOUBLE)");
        statement.execute("INSERT INTO upd_test10 VALUES (1, 10, 100, 1.5)");

        DirectResultSet rs = createUpdatableResultSet("SELECT * FROM upd_test10", "upd_test10", "id");

        assertTrue(rs.next());
        rs.updateInt("int_val", 20);
        rs.updateLong("long_val", 200L);
        rs.updateDouble("double_val", 2.5);
        rs.updateRow();

        rs.close();
        java.sql.ResultSet verifyRs = statement.executeQuery("SELECT * FROM upd_test10 WHERE id = 1");
        assertTrue(verifyRs.next());
        assertEquals(20, verifyRs.getInt("int_val"));
        assertEquals(200L, verifyRs.getLong("long_val"));
        assertEquals(2.5, verifyRs.getDouble("double_val"), 0.001);
        verifyRs.close();
    }

    @Test
    public void testUpdateByColumnIndex() throws SQLException {
        statement.execute("CREATE TABLE upd_test11 (id INTEGER, name VARCHAR)");
        statement.execute("INSERT INTO upd_test11 VALUES (1, 'Original')");

        DirectResultSet rs = createUpdatableResultSet("SELECT * FROM upd_test11", "upd_test11", "id");

        assertTrue(rs.next());
        rs.updateString(2, "Updated By Index");  // Column 2 is "name"
        rs.updateRow();

        rs.close();
        java.sql.ResultSet verifyRs = statement.executeQuery("SELECT name FROM upd_test11 WHERE id = 1");
        assertTrue(verifyRs.next());
        assertEquals("Updated By Index", verifyRs.getString("name"));
        verifyRs.close();
    }

    @Test
    public void testInsertRowByColumnIndex() throws SQLException {
        statement.execute("CREATE TABLE upd_test12 (id INTEGER, name VARCHAR)");

        DirectResultSet rs = createUpdatableResultSet("SELECT * FROM upd_test12", "upd_test12", "id");

        rs.moveToInsertRow();
        rs.updateInt(1, 1);  // Column 1 is "id"
        rs.updateString(2, "By Index");  // Column 2 is "name"
        rs.insertRow();
        rs.moveToCurrentRow();

        rs.close();
        java.sql.ResultSet verifyRs = statement.executeQuery("SELECT name FROM upd_test12 WHERE id = 1");
        assertTrue(verifyRs.next());
        assertEquals("By Index", verifyRs.getString("name"));
        verifyRs.close();
    }

    @Test
    public void testUpdateRowWithoutUpdate() throws SQLException {
        statement.execute("CREATE TABLE upd_test13 (id INTEGER, name VARCHAR)");
        statement.execute("INSERT INTO upd_test13 VALUES (1, 'Original')");

        DirectResultSet rs = createUpdatableResultSet("SELECT * FROM upd_test13", "upd_test13", "id");

        assertTrue(rs.next());

        // Try to call updateRow() without any updates
        assertThrows(SQLException.class, () -> rs.updateRow());

        rs.close();
    }

    @Test
    public void testInsertRowWithoutValues() throws SQLException {
        statement.execute("CREATE TABLE upd_test14 (id INTEGER, name VARCHAR)");

        DirectResultSet rs = createUpdatableResultSet("SELECT * FROM upd_test14", "upd_test14", "id");

        rs.moveToInsertRow();

        // Try to insert without setting values
        assertThrows(SQLException.class, () -> rs.insertRow());

        rs.close();
    }

    @Test
    public void testUpdateRowOnInsertRow() throws SQLException {
        statement.execute("CREATE TABLE upd_test15 (id INTEGER, name VARCHAR)");

        DirectResultSet rs = createUpdatableResultSet("SELECT * FROM upd_test15", "upd_test15", "id");

        rs.moveToInsertRow();
        rs.updateInt("id", 1);
        rs.updateString("name", "Test");

        // Try to call updateRow() while on insert row
        assertThrows(SQLException.class, () -> rs.updateRow());

        rs.close();
    }

    @Test
    public void testDeleteRowOnInsertRow() throws SQLException {
        statement.execute("CREATE TABLE upd_test16 (id INTEGER, name VARCHAR)");

        DirectResultSet rs = createUpdatableResultSet("SELECT * FROM upd_test16", "upd_test16", "id");

        rs.moveToInsertRow();

        // Try to call deleteRow() while on insert row
        assertThrows(SQLException.class, () -> rs.deleteRow());

        rs.close();
    }

    @Test
    public void testInsertRowOnNormalRow() throws SQLException {
        statement.execute("CREATE TABLE upd_test17 (id INTEGER, name VARCHAR)");
        statement.execute("INSERT INTO upd_test17 VALUES (1, 'Existing')");

        DirectResultSet rs = createUpdatableResultSet("SELECT * FROM upd_test17", "upd_test17", "id");

        assertTrue(rs.next());

        // Try to call insertRow() without moveToInsertRow()
        assertThrows(SQLException.class, () -> rs.insertRow());

        rs.close();
    }

    @Test
    public void testNonUpdatableResultSet() throws SQLException {
        statement.execute("CREATE TABLE upd_test18 (id INTEGER, name VARCHAR)");
        statement.execute("INSERT INTO upd_test18 VALUES (1, 'Test')");

        // Create non-updatable ResultSet (without engine/tableName)
        ResultSet engineResultSet = ((DirectConnection) connection)
            .executeScoped("SELECT * FROM upd_test18").getResultSets().get(0);
        DirectResultSet rs = new DirectResultSet(statement, engineResultSet);

        assertTrue(rs.next());

        // Try to update on non-updatable ResultSet
        assertThrows(SQLException.class, () -> rs.updateString("name", "Updated"));
        assertThrows(SQLException.class, () -> rs.updateRow());
        assertThrows(SQLException.class, () -> rs.deleteRow());
        assertThrows(SQLException.class, () -> rs.moveToInsertRow());

        rs.close();
    }

    @Test
    public void testUpdateObjectMethod() throws SQLException {
        statement.execute("CREATE TABLE upd_test19 (id INTEGER, value VARCHAR)");
        statement.execute("INSERT INTO upd_test19 VALUES (1, 'Original')");

        DirectResultSet rs = createUpdatableResultSet("SELECT * FROM upd_test19", "upd_test19", "id");

        assertTrue(rs.next());
        rs.updateObject("value", "Updated via Object");
        rs.updateRow();

        rs.close();
        java.sql.ResultSet verifyRs = statement.executeQuery("SELECT value FROM upd_test19 WHERE id = 1");
        assertTrue(verifyRs.next());
        assertEquals("Updated via Object", verifyRs.getString("value"));
        verifyRs.close();
    }

    @Test
    public void testMoveToInsertAndBack() throws SQLException {
        statement.execute("CREATE TABLE upd_test20 (id INTEGER, name VARCHAR)");
        statement.execute("INSERT INTO upd_test20 VALUES (1, 'Existing')");

        DirectResultSet rs = createUpdatableResultSet("SELECT * FROM upd_test20", "upd_test20", "id");

        assertTrue(rs.next());
        assertEquals("Existing", rs.getString("name"));

        // Move to insert row
        rs.moveToInsertRow();
        rs.updateInt("id", 2);
        rs.updateString("name", "New");

        // Move back to current row without inserting
        rs.moveToCurrentRow();

        // Verify we're back on the existing row
        assertEquals("Existing", rs.getString("name"));

        rs.close();

        // Verify no insertion happened
        java.sql.ResultSet verifyRs = statement.executeQuery("SELECT COUNT(*) FROM upd_test20");
        assertTrue(verifyRs.next());
        assertEquals(1, verifyRs.getInt(1));
        verifyRs.close();
    }
}
