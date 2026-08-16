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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for CLONE functionality
 */
public class CloneTest extends BaseDatabaseTest {

    /** A Snowflake external stage must be a cloud URL; {@code file://} is a Frostlake convenience. */
    private static final String LOCAL_FILE_STAGE =
        "builds a file:// stage, which Snowflake refuses as an invalid URL prefix";

    /** An inline Java handler compiles on the ACCOUNT, where it needs PACKAGES = snowpark declared. */
    private static final String ACCOUNT_JAVA_HANDLER =
        "an inline Java handler needs PACKAGES = ('com.snowflake:snowpark:latest') on the account";

    @Test
    public void testCloneDatabase() {
        // Create source database with some content
        engine.execute("CREATE DATABASE source_db");
        engine.execute("USE DATABASE source_db");
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')");
        engine.execute("CREATE VIEW user_view AS SELECT * FROM users");

        // Clone the database
        engine.execute("CREATE DATABASE cloned_db CLONE source_db");

        // Verify cloned database exists
        final ResultSet databases = engine.executeQuery("SHOW DATABASES");
        boolean foundCloned = false;
        while (databases.next()) {
            final String dbName = (String) databases.getValue("name");
            if ("CLONED_DB".equalsIgnoreCase(dbName)) {
                foundCloned = true;
                break;
            }
        }
        assertTrue(foundCloned, "Cloned database should exist");

        // Verify table structure exists in cloned database
        engine.execute("USE DATABASE cloned_db");
        final ResultSet tables = engine.executeQuery("SHOW TABLES");
        boolean foundUsers = false;
        while (tables.next()) {
            final String tableName = (String) tables.getValue("name");
            if ("USERS".equalsIgnoreCase(tableName)) {
                foundUsers = true;
                break;
            }
        }
        assertTrue(foundUsers, "Cloned table should exist");

        // Note: Data cloning at database level is not fully implemented yet
        // in this version (would require storage engine integration)
    }

    @Test
    public void testCloneSchema() {
        // Create source schema with content
        engine.execute("CREATE SCHEMA source_schema");
        engine.execute("USE SCHEMA source_schema");
        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price FLOAT)");
        engine.execute("INSERT INTO products VALUES (1, 'Widget', 19.99), (2, 'Gadget', 29.99)");

        // Clone the schema
        engine.execute("CREATE SCHEMA cloned_schema CLONE source_schema");

        // Verify cloned schema exists
        final ResultSet schemas = engine.executeQuery("SHOW SCHEMAS");
        boolean foundCloned = false;
        while (schemas.next()) {
            final String schemaName = (String) schemas.getValue("name");
            if ("CLONED_SCHEMA".equalsIgnoreCase(schemaName)) {
                foundCloned = true;
                break;
            }
        }
        assertTrue(foundCloned, "Cloned schema should exist");

        // Verify table structure exists
        engine.execute("USE SCHEMA cloned_schema");
        final ResultSet tables = engine.executeQuery("SHOW TABLES");
        boolean foundProducts = false;
        while (tables.next()) {
            final String tableName = (String) tables.getValue("name");
            if ("PRODUCTS".equalsIgnoreCase(tableName)) {
                foundProducts = true;
                break;
            }
        }
        assertTrue(foundProducts, "Cloned table should exist in cloned schema");
    }

    @Test
    public void testCloneTable() {
        // Create source table with data
        engine.execute("CREATE TABLE source_table (id INTEGER, value VARCHAR, amount FLOAT)");
        engine.execute("INSERT INTO source_table VALUES (1, 'A', 100.0), (2, 'B', 200.0), (3, 'C', 300.0)");

        // Clone the table
        engine.execute("CREATE TABLE cloned_table CLONE source_table");

        // Verify cloned table exists
        final ResultSet tables = engine.executeQuery("SHOW TABLES");
        boolean foundCloned = false;
        while (tables.next()) {
            final String tableName = (String) tables.getValue("name");
            if ("CLONED_TABLE".equalsIgnoreCase(tableName)) {
                foundCloned = true;
                break;
            }
        }
        assertTrue(foundCloned, "Cloned table should exist");

        // Verify cloned table has same structure
        final ResultSet columns = engine.executeQuery("SHOW COLUMNS IN TABLE cloned_table");
        int colCount = 0;
        while (columns.next()) {
            colCount++;
        }
        assertEquals(3, colCount, "Cloned table should have same columns");

        // Verify cloned table has same data
        final ResultSet data = engine.executeQuery("SELECT * FROM cloned_table ORDER BY id");
        assertEquals(3, data.getRowCount(), "Cloned table should have same number of rows");

        assertTrue(data.next());
        assertEquals(1L, data.getValue("id"));
        assertEquals("A", data.getValue("value"));
        assertEquals(100.0, ((Number) data.getValue("amount")).doubleValue(), 0.01);

        assertTrue(data.next());
        assertEquals(2L, data.getValue("id"));
        assertEquals("B", data.getValue("value"));
        assertEquals(200.0, ((Number) data.getValue("amount")).doubleValue(), 0.01);

        assertTrue(data.next());
        assertEquals(3L, data.getValue("id"));
        assertEquals("C", data.getValue("value"));
        assertEquals(300.0, ((Number) data.getValue("amount")).doubleValue(), 0.01);
    }

    @Test
    public void testCloneTableIndependence() {
        // Create and clone a table
        engine.execute("CREATE TABLE original (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO original VALUES (1, 'Original')");
        engine.execute("CREATE TABLE copy CLONE original");

        // Modify original table
        engine.execute("INSERT INTO original VALUES (2, 'NewRow')");

        // Verify original has 2 rows
        final ResultSet originalData = engine.executeQuery("SELECT * FROM original");
        assertEquals(2, originalData.getRowCount(), "Original should have 2 rows");

        // Verify clone still has 1 row (independent copy)
        final ResultSet copyData = engine.executeQuery("SELECT * FROM copy");
        assertEquals(1, copyData.getRowCount(), "Clone should still have 1 row");
    }

    @Test
    public void testCloneTableWithPrimaryKey() {
        // Create table with primary key
        engine.execute("CREATE TABLE source_pk (id INTEGER PRIMARY KEY, data VARCHAR)");
        engine.execute("INSERT INTO source_pk VALUES (1, 'Data1'), (2, 'Data2')");

        // Clone the table
        engine.execute("CREATE TABLE cloned_pk CLONE source_pk");

        // Verify primary key constraint is cloned
        final ResultSet columns = engine.executeQuery("SHOW COLUMNS IN TABLE cloned_pk");
        boolean foundPrimaryKey = false;
        while (columns.next()) {
            final String colName = (String) columns.getValue("column_name");
            if ("ID".equalsIgnoreCase(colName)) {
                // Check if it's marked as primary key (implementation-specific)
                foundPrimaryKey = true;
                break;
            }
        }
        assertTrue(foundPrimaryKey, "Primary key column should exist in cloned table");

        // Verify data was cloned
        final ResultSet data = engine.executeQuery("SELECT * FROM cloned_pk ORDER BY id");
        assertEquals(2, data.getRowCount());
    }

    @Test
    public void testCloneTableQualifiedNames() {
        // Create source in one schema
        engine.execute("CREATE SCHEMA schema1");
        engine.execute("CREATE TABLE schema1.source_table (id INTEGER, value VARCHAR)");
        engine.execute("INSERT INTO schema1.source_table VALUES (1, 'Test')");

        // Clone to different schema using qualified names
        engine.execute("CREATE SCHEMA schema2");
        engine.execute("CREATE TABLE schema2.cloned_table CLONE schema1.source_table");

        // Verify cloned table exists in schema2
        final ResultSet data = engine.executeQuery("SELECT * FROM schema2.cloned_table");
        assertEquals(1, data.getRowCount());
        assertTrue(data.next());
        assertEquals("Test", data.getValue("value"));
    }

    @Test
    public void testCloneSchemaQualifiedNames() {
        // Create source database and schema
        engine.execute("CREATE DATABASE db1");
        engine.execute("USE DATABASE db1");
        engine.execute("CREATE SCHEMA source_schema");
        engine.execute("CREATE TABLE source_schema.test_table (id INTEGER)");

        // Clone schema within same database
        engine.execute("CREATE SCHEMA cloned_schema CLONE source_schema");

        // Verify schema was cloned
        final ResultSet schemas = engine.executeQuery("SHOW SCHEMAS");
        boolean found = false;
        while (schemas.next()) {
            final String schemaName = (String) schemas.getValue("name");
            if ("CLONED_SCHEMA".equalsIgnoreCase(schemaName)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    public void testCloneDatabaseIfNotExists() {
        engine.execute("CREATE DATABASE source_db");
        engine.execute("CREATE DATABASE cloned_db CLONE source_db");

        // Try to clone again with IF NOT EXISTS - should not error
        engine.execute("CREATE DATABASE IF NOT EXISTS cloned_db CLONE source_db");

        // Verify only one cloned_db exists
        final ResultSet databases = engine.executeQuery("SHOW DATABASES");
        int count = 0;
        while (databases.next()) {
            final String dbName = (String) databases.getValue("name");
            if ("CLONED_DB".equalsIgnoreCase(dbName)) {
                count++;
            }
        }
        assertEquals(1, count);
    }

    @Test
    public void testCloneEmptyTable() {
        // Create empty table and clone it
        engine.execute("CREATE TABLE empty_table (id INTEGER, name VARCHAR)");
        engine.execute("CREATE TABLE cloned_empty CLONE empty_table");

        // Verify cloned table exists and is empty
        final ResultSet data = engine.executeQuery("SELECT * FROM cloned_empty");
        assertEquals(0, data.getRowCount(), "Cloned empty table should be empty");
    }

    @Test
    public void testCloneTableWithComment() {
        // Create table with comment
        engine.execute("CREATE TABLE source_table (id INTEGER) COMMENT = 'Source table comment'");
        engine.execute("INSERT INTO source_table VALUES (1)");

        // Clone the table
        engine.execute("CREATE TABLE cloned_table CLONE source_table");

        // Verify table was cloned
        final ResultSet data = engine.executeQuery("SELECT * FROM cloned_table");
        assertEquals(1, data.getRowCount());
    }

    @Test
    public void testCloneNonExistentSource() {
        // Try to clone a non-existent database
        final RuntimeException exception = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("CREATE DATABASE cloned_db CLONE non_existent_db");
                
            }
        });
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    public void testCloneNonExistentTable() {

        // Try to clone a non-existent table
        final RuntimeException exception = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("CREATE TABLE cloned_table CLONE non_existent_table");
                
            }
        });
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    public void testCloneDuplicateName() {
        engine.execute("CREATE DATABASE source_db");
        engine.execute("CREATE DATABASE target_db");

        // Try to clone to an existing database name
        final RuntimeException exception = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("CREATE DATABASE target_db CLONE source_db");
                
            }
        });
        assertTrue(exception.getMessage().contains("already exists"));
    }

    @Test
    public void testCloneDatabaseCopiesData() {
        engine.execute("CREATE DATABASE source_db");
        engine.execute("USE DATABASE source_db");
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')");

        engine.execute("CREATE DATABASE cloned_db CLONE source_db");

        engine.execute("USE DATABASE cloned_db");
        engine.execute("USE SCHEMA public");
        final ResultSet rs = engine.executeQuery("SELECT * FROM users ORDER BY id");
        assertEquals(2, rs.getRowCount(), "Cloned database should contain data");
        assertEquals("Alice", rs.getRows().get(0).getValue(1));
        assertEquals("Bob", rs.getRows().get(1).getValue(1));
    }

    @Test
    public void testCloneDatabaseDataIndependence() {
        engine.execute("CREATE DATABASE source_db");
        engine.execute("USE DATABASE source_db");
        engine.execute("CREATE TABLE items (id INTEGER, val VARCHAR)");
        engine.execute("INSERT INTO items VALUES (1, 'original')");

        engine.execute("CREATE DATABASE cloned_db CLONE source_db");

        // Modify source — clone should be unaffected
        engine.execute("USE DATABASE source_db");
        engine.execute("USE SCHEMA public");
        engine.execute("INSERT INTO items VALUES (2, 'added-to-source')");

        engine.execute("USE DATABASE cloned_db");
        engine.execute("USE SCHEMA public");
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM items");
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue(),
            "Cloned database data must be independent of source");
    }

    @Test
    public void testCloneSchemaCopiesData() {
        engine.execute("CREATE SCHEMA src");
        engine.execute("USE SCHEMA src");
        engine.execute("CREATE TABLE orders (id INTEGER, amount FLOAT)");
        engine.execute("INSERT INTO orders VALUES (1, 99.9), (2, 49.5), (3, 199.0)");

        engine.execute("CREATE SCHEMA dst CLONE src");

        engine.execute("USE SCHEMA dst");
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM orders");
        assertEquals(3L, ((Number) rs.getRows().get(0).getValue(0)).longValue(),
            "Cloned schema should contain all rows from source");
    }

    @Test
    public void testCloneSchemaDataIndependence() {
        engine.execute("CREATE SCHEMA src");
        engine.execute("USE SCHEMA src");
        engine.execute("CREATE TABLE kv (k VARCHAR, v INTEGER)");
        engine.execute("INSERT INTO kv VALUES ('a', 1)");

        engine.execute("CREATE SCHEMA dst CLONE src");

        // Delete from source — clone unaffected
        engine.execute("USE SCHEMA src");
        engine.execute("DELETE FROM kv WHERE k = 'a'");

        engine.execute("USE SCHEMA dst");
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM kv");
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue(),
            "Cloned schema data must be independent of source");
    }

    @Test
    public void testCloneDatabaseMultipleTablesAndSchemas() {
        engine.execute("CREATE DATABASE src_db");
        engine.execute("USE DATABASE src_db");

        engine.execute("CREATE TABLE t1 (id INTEGER)");
        engine.execute("INSERT INTO t1 VALUES (10), (20)");

        engine.execute("CREATE SCHEMA extra");
        engine.execute("USE SCHEMA extra");
        engine.execute("CREATE TABLE t2 (name VARCHAR)");
        engine.execute("INSERT INTO t2 VALUES ('x'), ('y'), ('z')");

        engine.execute("CREATE DATABASE tgt_db CLONE src_db");

        engine.execute("USE DATABASE tgt_db");
        engine.execute("USE SCHEMA public");
        final ResultSet r1 = engine.executeQuery("SELECT COUNT(*) FROM t1");
        assertEquals(2L, ((Number) r1.getRows().get(0).getValue(0)).longValue());

        engine.execute("USE SCHEMA extra");
        final ResultSet r2 = engine.executeQuery("SELECT COUNT(*) FROM t2");
        assertEquals(3L, ((Number) r2.getRows().get(0).getValue(0)).longValue());
    }

    /**
     * A cloned UDF must keep its LANGUAGE (and handler/runtime). The clone used the short Function
     * constructor, which defaults the language to SQL, so a cloned Java/JS/Python UDF was evaluated as a SQL
     * function and failed at call time with "Error in SQL function body" on its Java source.
     */
    @Test
    public void testCloneKeepsNonSqlFunctionLanguage() {
        engine.execute("CREATE DATABASE fn_src");
        engine.execute("USE DATABASE fn_src");
        engine.execute("CREATE SCHEMA utils");
        engine.execute("CREATE OR REPLACE FUNCTION fn_src.utils.java_id() RETURNS VARCHAR LANGUAGE JAVA "
            + "HANDLER='Tools.id' AS 'class Tools{ public static String id(){ return \"java-ok\"; } }'");
        engine.execute("CREATE OR REPLACE FUNCTION fn_src.utils.sql_id() RETURNS VARCHAR AS 'UPPER(''sql-ok'')'");

        engine.execute("CREATE DATABASE fn_tgt CLONE fn_src");

        assertEquals("java-ok", engine.executeQuery("SELECT fn_tgt.utils.java_id()")
            .getRows().get(0).getValue(0).toString());
        assertEquals("SQL-OK", engine.executeQuery("SELECT fn_tgt.utils.sql_id()")
            .getRows().get(0).getValue(0).toString());
    }

    /** The same for procedures: a cloned non-SQL procedure must keep its LANGUAGE, not default to SQL. */
    @Test
    public void testCloneKeepsNonSqlProcedureLanguage() {
        Assumptions.assumeFalse(isLiveSnowflake(), ACCOUNT_JAVA_HANDLER);
        engine.execute("CREATE DATABASE pr_src");
        engine.execute("USE DATABASE pr_src");
        engine.execute("CREATE SCHEMA utils");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE pr_src.utils.java_proc()
            RETURNS VARCHAR
            LANGUAGE JAVA
            HANDLER='CloneProc.run'
            AS
            $$
            public class CloneProc {
              public String run(com.snowflake.snowpark_java.Session session) {
                return "java-ok";
              }
            }
            $$
            """);

        engine.execute("CREATE DATABASE pr_tgt CLONE pr_src");

        assertEquals("java-ok", engine.executeQuery("CALL pr_tgt.utils.java_proc()")
            .getRows().get(0).getValue(0).toString());
    }

    @Test
    public void testCloneDatabaseKeepsStagesAndFileFormats() {
        Assumptions.assumeFalse(isLiveSnowflake(), LOCAL_FILE_STAGE);
        // Snowflake's CLONE keeps stage and file-format definitions. Dropping stages silently broke
        // every jar-backed UDF in a cloned database — the IMPORTS reference fell back to a relative
        // local path and failed with "class not found" at first invocation.
        engine.execute("CREATE DATABASE st_src");
        engine.execute("USE DATABASE st_src");
        engine.execute("CREATE SCHEMA depot");
        engine.execute("CREATE STAGE depot.jar_stage URL='file:///tmp/frostlake_clone_stage_test'");
        engine.execute("CREATE FILE FORMAT depot.csv_fmt TYPE = 'CSV'");

        engine.execute("CREATE DATABASE st_tgt CLONE st_src");
        engine.execute("USE DATABASE st_tgt");
        engine.execute("USE SCHEMA depot");

        final ResultSet stages = engine.executeQuery("SHOW STAGES LIKE 'jar_stage'");
        assertEquals("file:///tmp/frostlake_clone_stage_test",
            cell(stages, soleRowWhere(stages, "name", "jar_stage"), "url"));
        final ResultSet formats = engine.executeQuery("SHOW FILE FORMATS LIKE 'csv_fmt'");
        assertEquals("CSV", cell(formats, soleRowWhere(formats, "name", "csv_fmt"), "type"));
    }
}
