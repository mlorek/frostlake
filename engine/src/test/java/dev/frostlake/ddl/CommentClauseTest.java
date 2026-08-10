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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The COMMENT clause on CREATE statements, asserted through SQL the way a client reads it — the
 * SHOW family's {@code comment} column (SHOW FUNCTIONS / PROCEDURES carry it as {@code description}),
 * and DESCRIBE TABLE's {@code comment} cell for columns — never through engine internals, so the
 * same assertions hold against a live account.
 *
 * <p>Live-verified spellings this class encodes: the {@code =} is POSITIONAL — an object-level
 * comment requires it ({@code COMMENT = 'x'}) and a COLUMN-level comment forbids it
 * ({@code id INT COMMENT 'x'}; with {@code =} it is a syntax error at the {@code =}); SHOW spells
 * an unset comment as the EMPTY STRING while INFORMATION_SCHEMA and DESCRIBE spell it NULL, and
 * DESCRIBE alone distinguishes an explicitly EMPTY comment ({@code ''}) from an unset one (NULL).
 */
public class CommentClauseTest extends BaseDatabaseTest {

    private String showCell(final String sql, final String column) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), "expected exactly one row from: " + sql);
        final Object value = rs.getRows().get(0).getValue(rs.getColumnIndex(column));
        return value == null ? null : String.valueOf(value);
    }

    private Object descTableComment(final String table, final String column) {
        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE " + table);
        for (final Row row : rs.getRows()) {
            if (column.equalsIgnoreCase(String.valueOf(row.getValue(rs.getColumnIndex("name"))))) {
                return row.getValue(rs.getColumnIndex("comment"));
            }
        }
        throw new AssertionError("no column " + column + " in DESCRIBE TABLE " + table);
    }

    @Test
    public void testCreateDatabaseWithComment() {
        engine.execute("CREATE OR REPLACE DATABASE cmt_cl_db COMMENT = 'Test database'");
        assertEquals("Test database", showCell("SHOW DATABASES LIKE 'cmt_cl_db'", "comment"));
        engine.execute("DROP DATABASE cmt_cl_db");
    }

    @Test
    public void testCreateSchemaWithComment() {
        engine.execute("CREATE SCHEMA cmt_cl_schema COMMENT = 'Test schema'");
        assertEquals("Test schema", showCell("SHOW SCHEMAS LIKE 'cmt_cl_schema'", "comment"));
    }

    @Test
    public void testCreateTableWithComment() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR) COMMENT = 'User table'");
        assertEquals("User table", showCell("SHOW TABLES LIKE 'users'", "comment"));
    }

    @Test
    public void testCreateTableWithColumnComments() {
        // Column comments carry NO equals sign — that is the only spelling live accepts here.
        engine.execute("""
            CREATE TABLE users (\
            id INTEGER COMMENT 'User ID', \
            name VARCHAR COMMENT 'Full name', \
            email VARCHAR COMMENT 'Email address')\
            """);
        assertEquals("User ID", descTableComment("users", "ID"));
        assertEquals("Full name", descTableComment("users", "NAME"));
        assertEquals("Email address", descTableComment("users", "EMAIL"));
    }

    @Test
    public void testCreateTableWithTableAndColumnComments() {
        engine.execute("""
            CREATE TABLE products (\
            id INTEGER COMMENT 'Product ID', \
            name VARCHAR COMMENT 'Product name') \
            COMMENT = 'Product catalog'\
            """);
        assertEquals("Product catalog", showCell("SHOW TABLES LIKE 'products'", "comment"));
        assertEquals("Product ID", descTableComment("products", "ID"));
    }

    @Test
    public void testColumnCommentRejectsTheEqualsSign() {
        // Live refuses `COMMENT = '…'` on a COLUMN definition with a syntax error at the '='.
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE bad_col (id INTEGER COMMENT = 'User ID')");
            }
        });
        assertTrue(e.getMessage().toLowerCase().contains("syntax error"),
            "unexpected message: " + e.getMessage());
    }

    @Test
    public void testObjectCommentRequiresTheEqualsSign() {
        // The mirror image: a TABLE-level comment without '=' is a syntax error at the string.
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE bad_tbl (x INTEGER) COMMENT 'no equals'");
            }
        });
        assertTrue(e.getMessage().contains("syntax error"), "unexpected message: " + e.getMessage());
    }

    @Test
    public void testCreateViewWithComment() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        // Live-verified: the COMMENT property goes BEFORE AS (after the query it is a syntax error).
        engine.execute("CREATE VIEW active_users COMMENT = 'Active users view' AS SELECT * FROM users");
        assertEquals("Active users view", showCell("SHOW VIEWS LIKE 'active_users'", "comment"));
    }

    @Test
    public void testCreateStreamWithComment() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users COMMENT = 'User change stream'");
        assertEquals("User change stream", showCell("SHOW STREAMS LIKE 'user_stream'", "comment"));
    }

    @Test
    public void testCreateTaskWithComment() {
        engine.execute("CREATE WAREHOUSE cmt_cl_wh");
        // Live-verified: COMMENT goes BEFORE AS, the body after AS is a bare statement, and the
        // warehouse may be spelled as a quoted string.
        engine.execute("""
            CREATE TASK daily_task
            WAREHOUSE = 'cmt_cl_wh'
            SCHEDULE = 'USING CRON 0 9 * * * UTC'
            COMMENT = 'Daily processing task'
            AS SELECT 1
            """);
        assertEquals("Daily processing task", showCell("SHOW TASKS LIKE 'daily_task'", "comment"));
    }

    @Test
    public void testCreateWarehouseWithComment() {
        engine.execute("CREATE OR REPLACE WAREHOUSE cmt_cl_wh2 COMMENT = 'Test warehouse'");
        assertEquals("Test warehouse", showCell("SHOW WAREHOUSES LIKE 'cmt_cl_wh2'", "comment"));
    }

    @Test
    public void testCreateStageWithComment() {
        engine.execute("CREATE STAGE cmt_cl_stage URL = 's3://bucket/path' COMMENT = 'Test stage'");
        assertEquals("Test stage", showCell("SHOW STAGES LIKE 'cmt_cl_stage'", "comment"));
    }

    @Test
    public void testCreateFunctionWithComment() {
        // A function's comment surfaces in SHOW's `description` column, not a `comment` one.
        engine.execute("""
            CREATE FUNCTION add_numbers(x INTEGER, y INTEGER)
            RETURNS INTEGER
            COMMENT = 'Adds two numbers'
            AS 'x + y'
            """);
        assertEquals("Adds two numbers",
            showCell("SHOW USER FUNCTIONS LIKE 'add_numbers'", "description"));
    }

    @Test
    public void testCreateProcedureWithComment() {
        engine.execute("""
            CREATE PROCEDURE test_proc()
            RETURNS VARCHAR
            COMMENT = 'Test procedure'
            AS 'BEGIN RETURN ''done''; END;'
            """);
        assertEquals("Test procedure", showCell("SHOW PROCEDURES LIKE 'test_proc'", "description"));
    }

    @Test
    public void testCreateUserWithComment() {
        engine.execute("CREATE OR REPLACE USER cmt_cl_user PASSWORD = 'Sekrit!42x' COMMENT = 'Test user account'");
        assertEquals("Test user account", showCell("SHOW USERS LIKE 'cmt_cl_user'", "comment"));
        engine.execute("DROP USER cmt_cl_user");
    }

    @Test
    public void testCreateRoleWithComment() {
        engine.execute("CREATE OR REPLACE ROLE cmt_cl_role COMMENT = 'Analyst role'");
        assertEquals("Analyst role", showCell("SHOW ROLES LIKE 'cmt_cl_role'", "comment"));
        engine.execute("DROP ROLE cmt_cl_role");
    }

    // ── how an ABSENT comment is spelled, per reading surface ────────────────

    @Test
    public void testCreateWithoutComment() {
        // SHOW spells an unset comment as the EMPTY STRING, never NULL.
        engine.execute("CREATE OR REPLACE DATABASE cmt_cl_db2");
        assertEquals("", showCell("SHOW DATABASES LIKE 'cmt_cl_db2'", "comment"));
        engine.execute("DROP DATABASE cmt_cl_db2");
    }

    @Test
    public void testUnsetAndEmptyColumnCommentsAreDistinctInDescribe() {
        // DESCRIBE alone tells an unset comment (NULL) from an explicitly EMPTY one ('').
        engine.execute("CREATE TABLE spell (a INTEGER COMMENT 'set', b INTEGER, c INTEGER COMMENT '')");
        assertEquals("set", descTableComment("spell", "A"));
        assertNull(descTableComment("spell", "B"));
        assertEquals("", descTableComment("spell", "C"));
        // SHOW COLUMNS flattens both to the empty string. (LIKE precedes the IN scope.)
        assertEquals("", showCell(
            "SHOW COLUMNS LIKE 'b' IN TABLE spell", "comment"));
    }

    @Test
    public void testUnsetTableCommentPerSurface() {
        engine.execute("CREATE TABLE bare (x INTEGER)");
        // SHOW: empty string; INFORMATION_SCHEMA: NULL.
        assertEquals("", showCell("SHOW TABLES LIKE 'bare'", "comment"));
        final ResultSet rs = engine.executeQuery(
            "SELECT comment FROM information_schema.tables WHERE table_name = 'BARE'");
        assertEquals(1, rs.getRowCount());
        assertNull(rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testUncommentedRoutinesDescribeTheirKind() {
        // With no comment, SHOW describes the routine by KIND — not with an empty cell.
        engine.execute("CREATE FUNCTION fn_bare(x INTEGER) RETURNS INTEGER AS 'x'");
        assertEquals("user-defined function",
            showCell("SHOW USER FUNCTIONS LIKE 'fn_bare'", "description"));
        engine.execute("CREATE PROCEDURE proc_bare() RETURNS VARCHAR AS 'BEGIN RETURN ''x''; END;'");
        assertEquals("user-defined procedure",
            showCell("SHOW PROCEDURES LIKE 'proc_bare'", "description"));
    }

    // ── comment payloads ─────────────────────────────────────────────────────

    @Test
    public void testCommentWithSpecialCharacters() {
        engine.execute("CREATE OR REPLACE DATABASE cmt_cl_db3 COMMENT = 'Database with special chars: @#$%'");
        assertEquals("Database with special chars: @#$%",
            showCell("SHOW DATABASES LIKE 'cmt_cl_db3'", "comment"));
        engine.execute("DROP DATABASE cmt_cl_db3");
    }

    @Test
    public void testCommentWithQuotes() {
        engine.execute("CREATE OR REPLACE DATABASE cmt_cl_db4 COMMENT = 'Database with ''quotes'''");
        assertEquals("Database with 'quotes'", showCell("SHOW DATABASES LIKE 'cmt_cl_db4'", "comment"));
        engine.execute("DROP DATABASE cmt_cl_db4");
    }

    @Test
    public void testLongComment() {
        engine.execute("""
            CREATE OR REPLACE DATABASE cmt_cl_db5 COMMENT = 'This is a very long comment that describes the database in great detail. It contains multiple sentences and provides comprehensive documentation about the purpose and usage of this database object.'
            """);
        assertEquals("This is a very long comment that describes the database in great detail. It contains multiple sentences and provides comprehensive documentation about the purpose and usage of this database object.",
            showCell("SHOW DATABASES LIKE 'cmt_cl_db5'", "comment"));
        engine.execute("DROP DATABASE cmt_cl_db5");
    }
}
