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

package dev.frostlake.features;

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test to verify INFORMATION_SCHEMA exists in every database
 */
public class InformationSchemaPerDatabaseTest extends BaseJdbcTest {

    @Test
    public void testInformationSchemaExistsInEveryDatabase() throws SQLException {
        // Create multiple databases
        statement.execute("CREATE DATABASE db1");
        statement.execute("CREATE DATABASE db2");
        statement.execute("CREATE DATABASE db3");

        // Test INFORMATION_SCHEMA exists in db1
        statement.execute("USE DATABASE db1");
        statement.execute("CREATE TABLE db1_table (id INTEGER)");

        ResultSet rs1 = statement.executeQuery("SHOW SCHEMAS");
        boolean foundInfoSchema1 = false;
        while (rs1.next()) {
            if ("INFORMATION_SCHEMA".equals(rs1.getString("name"))) {
                foundInfoSchema1 = true;
                break;
            }
        }
        assertTrue(foundInfoSchema1, "INFORMATION_SCHEMA should exist in db1");
        rs1.close();

        // Verify INFORMATION_SCHEMA.TABLES shows db1's tables
        ResultSet tables1 = statement.executeQuery(
            "SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_CATALOG = 'DB1'"
        );
        boolean foundDb1Table = false;
        while (tables1.next()) {
            if ("db1_table".equalsIgnoreCase(tables1.getString("TABLE_NAME"))) {
                foundDb1Table = true;
                break;
            }
        }
        assertTrue(foundDb1Table, "Should find db1_table in INFORMATION_SCHEMA.TABLES");
        tables1.close();

        // Test INFORMATION_SCHEMA exists in db2
        statement.execute("USE DATABASE db2");
        statement.execute("CREATE TABLE db2_table (id INTEGER)");

        ResultSet rs2 = statement.executeQuery("SHOW SCHEMAS");
        boolean foundInfoSchema2 = false;
        while (rs2.next()) {
            if ("INFORMATION_SCHEMA".equals(rs2.getString("name"))) {
                foundInfoSchema2 = true;
                break;
            }
        }
        assertTrue(foundInfoSchema2, "INFORMATION_SCHEMA should exist in db2");
        rs2.close();

        // Verify INFORMATION_SCHEMA.TABLES shows db2's tables (not db1's)
        ResultSet tables2 = statement.executeQuery(
            "SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_CATALOG = 'DB2'"
        );
        boolean foundDb2Table = false;
        while (tables2.next()) {
            if ("db2_table".equalsIgnoreCase(tables2.getString("TABLE_NAME"))) {
                foundDb2Table = true;
                break;
            }
        }
        assertTrue(foundDb2Table, "Should find db2_table in INFORMATION_SCHEMA.TABLES");
        tables2.close();

        // Test fully qualified access to INFORMATION_SCHEMA in different database
        statement.execute("USE DATABASE db3");
        ResultSet crossDbQuery = statement.executeQuery(
            "SELECT * FROM db1.INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'db1_table'"
        );
        assertTrue(crossDbQuery.next(), "Should be able to query INFORMATION_SCHEMA in db1 from db3 context");
        crossDbQuery.close();

        // Verify all databases show up in INFORMATION_SCHEMA.DATABASES regardless of current database
        ResultSet allDbs = statement.executeQuery("SELECT COUNT(*) as db_count FROM INFORMATION_SCHEMA.DATABASES");
        assertTrue(allDbs.next());
        int dbCount = allDbs.getInt("db_count");
        assertTrue(dbCount >= 4, "Should have at least 4 databases (TEST_DB, DB1, DB2, DB3)");
        allDbs.close();
    }

    @Test
    public void testCannotDropInformationSchema() throws SQLException {
        statement.execute("CREATE DATABASE test_drop_db");
        statement.execute("USE DATABASE test_drop_db");

        // Attempt to drop INFORMATION_SCHEMA should fail
        assertThrows(SQLException.class, () ->
            statement.execute("DROP SCHEMA INFORMATION_SCHEMA"),
            "Should not be able to drop INFORMATION_SCHEMA"
        );

        // Verify INFORMATION_SCHEMA still exists
        ResultSet rs = statement.executeQuery("SHOW SCHEMAS");
        boolean found = false;
        while (rs.next()) {
            if ("INFORMATION_SCHEMA".equals(rs.getString("name"))) {
                found = true;
                break;
            }
        }
        assertTrue(found, "INFORMATION_SCHEMA should still exist after failed drop attempt");
        rs.close();
    }

    @Test
    public void testInformationSchemaIsReadOnly() throws SQLException {
        // Attempt to create a table in INFORMATION_SCHEMA should fail
        assertThrows(SQLException.class, () ->
            statement.execute("CREATE TABLE INFORMATION_SCHEMA.custom_table (id INTEGER)"),
            "Should not be able to create tables in INFORMATION_SCHEMA"
        );

        // Attempt to drop a system view should fail
        assertThrows(SQLException.class, () ->
            statement.execute("DROP VIEW INFORMATION_SCHEMA.TABLES"),
            "Should not be able to drop system views"
        );
    }
}
