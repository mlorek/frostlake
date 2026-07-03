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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class InformationSchemaExtTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    private ResultSet q(final String sql) {
        return engine.executeQuery(sql);
    }

    // ── DATABASES ─────────────────────────────────────────────────────────────

    @Test
    public void testDatabasesHasNewColumns() {
        ResultSet rs = q("SELECT DATABASE_NAME, IS_TRANSIENT, LAST_ALTERED FROM INFORMATION_SCHEMA.DATABASES");
        assertNotNull(rs);
        assertTrue(rs.getRowCount() > 0);
        assertNotNull(rs.getColumnIndex("IS_TRANSIENT"));
        assertNotNull(rs.getColumnIndex("LAST_ALTERED"));
    }

    // ── SCHEMATA ──────────────────────────────────────────────────────────────

    @Test
    public void testSchemataHasNewColumns() {
        ResultSet rs = q("SELECT SCHEMA_NAME, IS_TRANSIENT, IS_MANAGED_ACCESS, RETENTION_TIME_DAYS FROM INFORMATION_SCHEMA.SCHEMATA");
        assertTrue(rs.getRowCount() > 0);
        assertNotNull(rs.getColumnIndex("RETENTION_TIME_DAYS"));
    }

    // ── TABLES ────────────────────────────────────────────────────────────────

    @Test
    public void testTablesHasOwnerAndType() {
        engine.execute("CREATE TABLE t1 (id INTEGER)");
        ResultSet rs = q("SELECT TABLE_NAME, TABLE_OWNER, TABLE_TYPE, BYTES, LAST_ALTERED FROM INFORMATION_SCHEMA.TABLES");
        assertTrue(rs.getRowCount() >= 1);
        assertNotNull(rs.getColumnIndex("TABLE_OWNER"));
        assertNotNull(rs.getColumnIndex("BYTES"));
    }

    @Test
    public void testTablesTransientFlag() {
        engine.execute("CREATE TRANSIENT TABLE trans_t (id INTEGER)");
        ResultSet rs = q("SELECT TABLE_NAME, IS_TRANSIENT FROM INFORMATION_SCHEMA.TABLES");
        boolean found = false;
        for (int i = 0; i < rs.getRowCount(); i++) {
            if ("TRANS_T".equalsIgnoreCase(rs.getRows().get(i).getValue(0).toString())) {
                assertEquals("Y", rs.getRows().get(i).getValue(1).toString());
                found = true;
            }
        }
        assertTrue(found, "TRANS_T should appear in TABLES view (rows: " + rs.getRowCount() + ")");
    }

    // ── COLUMNS ───────────────────────────────────────────────────────────────

    @Test
    public void testColumnsHasNumericPrecision() {
        engine.execute("CREATE TABLE nums (id INTEGER, val FLOAT)");
        ResultSet rs = q("SELECT COLUMN_NAME, DATA_TYPE, NUMERIC_PRECISION, IS_IDENTITY FROM INFORMATION_SCHEMA.COLUMNS");
        assertTrue(rs.getRowCount() >= 2);
        assertNotNull(rs.getColumnIndex("NUMERIC_PRECISION"));
        assertNotNull(rs.getColumnIndex("IS_IDENTITY"));
    }

    @Test
    public void testColumnsIdentityFlag() {
        engine.execute("CREATE TABLE auto_t (id INTEGER AUTOINCREMENT, name VARCHAR)");
        ResultSet rs = q("SELECT COLUMN_NAME, IS_IDENTITY FROM INFORMATION_SCHEMA.COLUMNS");
        assertTrue(rs.getRowCount() >= 2);
    }

    // ── VIEWS ─────────────────────────────────────────────────────────────────

    @Test
    public void testViewsHasOwnerAndOptions() {
        engine.execute("CREATE TABLE base (id INTEGER)");
        engine.execute("CREATE VIEW v1 AS SELECT * FROM base");
        ResultSet rs = q("SELECT TABLE_NAME, TABLE_OWNER, CHECK_OPTION, IS_UPDATABLE FROM INFORMATION_SCHEMA.VIEWS");
        assertTrue(rs.getRowCount() >= 1);
        assertNotNull(rs.getColumnIndex("TABLE_OWNER"));
        assertNotNull(rs.getColumnIndex("CHECK_OPTION"));
    }

    // ── PROCEDURES ────────────────────────────────────────────────────────────

    @Test
    public void testProceduresView() {
        engine.execute("""
            CREATE PROCEDURE greet(name VARCHAR)
            RETURNS VARCHAR
            LANGUAGE SQL
            AS $$
                BEGIN RETURN 'Hello'; END
            $$
            """);
        ResultSet rs = q("SELECT PROCEDURE_NAME, PROCEDURE_LANGUAGE, ARGUMENT_SIGNATURE, DATA_TYPE FROM INFORMATION_SCHEMA.PROCEDURES");
        assertEquals(1, rs.getRowCount());
        assertEquals("GREET", rs.getRows().get(0).getValue(0).toString());
        assertEquals("SQL", rs.getRows().get(0).getValue(1).toString());
    }

    // ── FUNCTIONS ─────────────────────────────────────────────────────────────

    @Test
    public void testFunctionsView() {
        engine.execute("CREATE FUNCTION add_one(n INTEGER) RETURNS INTEGER AS $$ SELECT 1 $$");
        ResultSet rs = q("SELECT FUNCTION_NAME, FUNCTION_LANGUAGE, IS_TABLE_FUNCTION, ARGUMENT_SIGNATURE FROM INFORMATION_SCHEMA.FUNCTIONS");
        assertEquals(1, rs.getRowCount());
        assertEquals("ADD_ONE", rs.getRows().get(0).getValue(0).toString());
        assertEquals("N", rs.getRows().get(0).getValue(2).toString());
    }

    @Test
    public void testFunctionsViewTableFunction() {
        engine.execute("CREATE FUNCTION rows_fn() RETURNS TABLE(v VARCHAR) AS $$ SELECT 'a' $$");
        ResultSet rs = q("SELECT FUNCTION_NAME, IS_TABLE_FUNCTION FROM INFORMATION_SCHEMA.FUNCTIONS");
        assertEquals(1, rs.getRowCount());
        assertEquals("Y", rs.getRows().get(0).getValue(1).toString());
    }

    // ── SEQUENCES ─────────────────────────────────────────────────────────────

    @Test
    public void testSequencesView() {
        engine.execute("CREATE SEQUENCE seq1 START 10 INCREMENT 5");
        ResultSet rs = q("SELECT SEQUENCE_NAME, START_VALUE FROM INFORMATION_SCHEMA.SEQUENCES");
        assertEquals(1, rs.getRowCount());
        assertEquals("SEQ1", rs.getRows().get(0).getValue(0).toString());
    }

    // ── TABLE_CONSTRAINTS ─────────────────────────────────────────────────────

    @Test
    public void testTableConstraintsHasRelyColumn() {
        engine.execute("CREATE TABLE ct (id INTEGER PRIMARY KEY, val VARCHAR UNIQUE)");
        ResultSet rs = q("SELECT CONSTRAINT_NAME, CONSTRAINT_TYPE FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS");
        assertTrue(rs.getRowCount() >= 2, "Expected at least PK and UNIQUE constraints");
        assertNotNull(rs.getColumnIndex("CONSTRAINT_TYPE"));
    }

    // ── REFERENTIAL_CONSTRAINTS ───────────────────────────────────────────────

    @Test
    public void testReferentialConstraintsView() {
        ResultSet rs = q("SELECT CONSTRAINT_NAME, MATCH_OPTION, UPDATE_RULE, DELETE_RULE FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS");
        assertNotNull(rs);
        assertNotNull(rs.getColumnIndex("UPDATE_RULE"));
    }

    // ── ENABLED_ROLES ─────────────────────────────────────────────────────────

    @Test
    public void testEnabledRolesView() {
        ResultSet rs = q("SELECT ROLE_NAME, ROLE_OWNER FROM INFORMATION_SCHEMA.ENABLED_ROLES");
        assertNotNull(rs);
        assertTrue(rs.getRowCount() > 0, "Should have at least system roles");
        assertNotNull(rs.getColumnIndex("ROLE_NAME"));
    }

    // ── APPLICABLE_ROLES ──────────────────────────────────────────────────────

    @Test
    public void testApplicableRolesView() {
        ResultSet rs = q("SELECT GRANTEE, ROLE_NAME, IS_GRANTABLE FROM INFORMATION_SCHEMA.APPLICABLE_ROLES");
        assertNotNull(rs);
        assertNotNull(rs.getColumnIndex("GRANTEE"));
    }

    // ── TABLE_PRIVILEGES / OBJECT_PRIVILEGES / USAGE_PRIVILEGES ──────────────

    @Test
    public void testTablePrivilegesView() {
        ResultSet rs = q("SELECT GRANTOR, GRANTEE, TABLE_NAME, PRIVILEGE_TYPE FROM INFORMATION_SCHEMA.TABLE_PRIVILEGES");
        assertNotNull(rs);
        assertNotNull(rs.getColumnIndex("PRIVILEGE_TYPE"));
    }

    @Test
    public void testObjectPrivilegesView() {
        ResultSet rs = q("SELECT OBJECT_NAME, OBJECT_TYPE, PRIVILEGE_TYPE FROM INFORMATION_SCHEMA.OBJECT_PRIVILEGES");
        assertNotNull(rs);
        assertNotNull(rs.getColumnIndex("OBJECT_TYPE"));
    }

    @Test
    public void testUsagePrivilegesView() {
        ResultSet rs = q("SELECT OBJECT_NAME, PRIVILEGE_TYPE FROM INFORMATION_SCHEMA.USAGE_PRIVILEGES");
        assertNotNull(rs);
        assertNotNull(rs.getColumnIndex("PRIVILEGE_TYPE"));
    }

    // ── STREAMS ───────────────────────────────────────────────────────────────

    @Test
    public void testStreamsView() {
        engine.execute("CREATE TABLE src (id INTEGER)");
        engine.execute("CREATE STREAM s1 ON TABLE src");
        ResultSet rs = q("SELECT STREAM_NAME, TABLE_NAME, SOURCE_TYPE, MODE FROM INFORMATION_SCHEMA.STREAMS");
        assertEquals(1, rs.getRowCount());
        assertEquals("S1", rs.getRows().get(0).getValue(0).toString());
        assertNotNull(rs.getColumnIndex("MODE"));
    }

    // ── TASKS ─────────────────────────────────────────────────────────────────

    @Test
    public void testTasksView() {
        engine.execute("CREATE WAREHOUSE w1");
        engine.execute("USE WAREHOUSE w1");
        engine.execute("CREATE TASK t1 WAREHOUSE = 'w1' SCHEDULE = '1 MINUTE' AS SELECT 1 FROM (SELECT 1) t");
        ResultSet rs = q("SELECT TASK_NAME, STATE FROM INFORMATION_SCHEMA.TASKS");
        assertEquals(1, rs.getRowCount());
        assertEquals("T1", rs.getRows().get(0).getValue(0).toString());
    }

    // ── PIPES ─────────────────────────────────────────────────────────────────

    @Test
    public void testPipesView() {
        engine.execute("CREATE STAGE my_stage URL='s3://my-bucket/data'");
        engine.execute("CREATE TABLE load_target (id INTEGER, name VARCHAR)");
        engine.execute("CREATE PIPE p1 AS COPY INTO load_target FROM @my_stage");
        ResultSet rs = q("SELECT PIPE_NAME, IS_AUTOINGEST_ENABLED FROM INFORMATION_SCHEMA.PIPES");
        assertEquals(1, rs.getRowCount());
        assertEquals("P1", rs.getRows().get(0).getValue(0).toString());
    }

    // ── TAGS ──────────────────────────────────────────────────────────────────

    @Test
    public void testTagsView() {
        engine.execute("CREATE TAG env ALLOWED_VALUES 'prod', 'dev'");
        ResultSet rs = q("SELECT TAG_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.TAGS");
        assertEquals(1, rs.getRowCount());
        assertEquals("ENV", rs.getRows().get(0).getValue(0).toString());
    }

    // ── Scoped to current database ────────────────────────────────────────────

    @Test
    public void testViewsScopedToCurrentDatabase() {
        engine.execute("CREATE TABLE t_local (id INTEGER)");
        engine.execute("CREATE DATABASE other_db");
        engine.execute("USE DATABASE other_db");
        engine.execute("CREATE TABLE t_other (id INTEGER)");

        // Switch back — TABLES should only show test_db tables
        engine.execute("USE DATABASE test_db");
        ResultSet rs = q("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES");
        for (int i = 0; i < rs.getRowCount(); i++) {
            assertNotEquals("T_OTHER", rs.getRows().get(i).getValue(0).toString(),
                "TABLES should not show tables from other databases");
        }
    }
}
