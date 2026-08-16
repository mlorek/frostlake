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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code COMMENT IF EXISTS ON <object-type> … IS '<text>'} — live-verified to complete SILENTLY for
 * every missing target (database, schema, table, column, the column's table, view, stream, task,
 * warehouse, stage, function, procedure, user, role), and to behave exactly like the plain command
 * when the target exists. Each set comment is read back through the SHOW / DESCRIBE cell a client
 * would use, so the same assertions hold against a live account.
 */
public class CommentIfExistsTest extends BaseDatabaseTest {

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

    private void silently(final String sql) {
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, "expected silent success for: " + sql);
    }

    @Test
    public void testCommentIfExistsOnDatabase() {
        silently("COMMENT IF EXISTS ON DATABASE cmt_ife_nodb IS 'Comment'");
        engine.execute("CREATE OR REPLACE DATABASE cmt_ife_db");
        engine.execute("COMMENT IF EXISTS ON DATABASE cmt_ife_db IS 'Updated comment'");
        assertEquals("Updated comment", showCell("SHOW DATABASES LIKE 'cmt_ife_db'", "comment"));
        engine.execute("DROP DATABASE cmt_ife_db");
    }

    @Test
    public void testCommentIfExistsOnSchema() {
        silently("COMMENT IF EXISTS ON SCHEMA cmt_ife_noschema IS 'Comment'");
        engine.execute("CREATE SCHEMA cmt_ife_schema");
        engine.execute("COMMENT IF EXISTS ON SCHEMA cmt_ife_schema IS 'Schema comment'");
        assertEquals("Schema comment", showCell("SHOW SCHEMAS LIKE 'cmt_ife_schema'", "comment"));
    }

    @Test
    public void testCommentIfExistsOnTable() {
        silently("COMMENT IF EXISTS ON TABLE cmt_ife_notable IS 'Comment'");
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("COMMENT IF EXISTS ON TABLE users IS 'User table'");
        assertEquals("User table", showCell("SHOW TABLES LIKE 'users'", "comment"));
    }

    @Test
    public void testCommentIfExistsOnColumn() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        // Both the missing COLUMN and the missing TABLE of a column reference stay silent.
        silently("COMMENT IF EXISTS ON COLUMN users.nonexistent IS 'Comment'");
        silently("COMMENT IF EXISTS ON COLUMN cmt_ife_notable.name IS 'Comment'");
        engine.execute("COMMENT IF EXISTS ON COLUMN users.name IS 'User name'");
        assertEquals("User name", descTableComment("users", "NAME"));
    }

    @Test
    public void testCommentIfExistsOnView() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        silently("COMMENT IF EXISTS ON VIEW cmt_ife_noview IS 'Comment'");
        engine.execute("CREATE VIEW active_users AS SELECT * FROM users");
        engine.execute("COMMENT IF EXISTS ON VIEW active_users IS 'Active users'");
        assertEquals("Active users", showCell("SHOW VIEWS LIKE 'active_users'", "comment"));
    }

    @Test
    public void testCommentIfExistsOnFunction() {
        silently("COMMENT IF EXISTS ON FUNCTION cmt_ife_nofn() IS 'Comment'");
        engine.execute("""
            CREATE FUNCTION add_numbers(x INTEGER, y INTEGER)
            RETURNS INTEGER AS 'x + y'
            """);
        engine.execute("COMMENT IF EXISTS ON FUNCTION add_numbers(INTEGER, INTEGER) IS 'Addition function'");
        assertEquals("Addition function",
            showCell("SHOW USER FUNCTIONS LIKE 'add_numbers'", "description"));
    }

    @Test
    public void testCommentIfExistsOnProcedure() {
        silently("COMMENT IF EXISTS ON PROCEDURE cmt_ife_noproc() IS 'Comment'");
        engine.execute("""
            CREATE PROCEDURE test_proc()
            RETURNS VARCHAR AS 'BEGIN RETURN ''done''; END;'
            """);
        engine.execute("COMMENT IF EXISTS ON PROCEDURE test_proc() IS 'Test procedure'");
        assertEquals("Test procedure", showCell("SHOW PROCEDURES LIKE 'test_proc'", "description"));
    }

    @Test
    public void testCommentIfExistsOnStream() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        silently("COMMENT IF EXISTS ON STREAM cmt_ife_nostream IS 'Comment'");
        engine.execute("CREATE STREAM user_stream ON TABLE users");
        engine.execute("COMMENT IF EXISTS ON STREAM user_stream IS 'User stream'");
        assertEquals("User stream", showCell("SHOW STREAMS LIKE 'user_stream'", "comment"));
    }

    @Test
    public void testCommentIfExistsOnTask() {
        engine.execute("CREATE WAREHOUSE cmt_ife_wh");
        silently("COMMENT IF EXISTS ON TASK cmt_ife_notask IS 'Comment'");
        engine.execute("""
            CREATE TASK daily_task
            WAREHOUSE = 'cmt_ife_wh'
            SCHEDULE = 'USING CRON 0 9 * * * UTC'
            AS SELECT 1
            """);
        engine.execute("COMMENT IF EXISTS ON TASK daily_task IS 'Daily task'");
        assertEquals("Daily task", showCell("SHOW TASKS LIKE 'daily_task'", "comment"));
    }

    @Test
    public void testCommentIfExistsOnWarehouse() {
        silently("COMMENT IF EXISTS ON WAREHOUSE cmt_ife_nowh IS 'Comment'");
        engine.execute("CREATE OR REPLACE WAREHOUSE cmt_ife_wh2");
        engine.execute("COMMENT IF EXISTS ON WAREHOUSE cmt_ife_wh2 IS 'Test warehouse'");
        assertEquals("Test warehouse", showCell("SHOW WAREHOUSES LIKE 'cmt_ife_wh2'", "comment"));
    }

    @Test
    public void testCommentIfExistsOnStage() {
        silently("COMMENT IF EXISTS ON STAGE cmt_ife_nostage IS 'Comment'");
        engine.execute("CREATE STAGE cmt_ife_stage URL = 's3://bucket/path'");
        engine.execute("COMMENT IF EXISTS ON STAGE cmt_ife_stage IS 'Test stage'");
        assertEquals("Test stage", showCell("SHOW STAGES LIKE 'cmt_ife_stage'", "comment"));
    }

    @Test
    public void testCommentIfExistsOnUser() {
        silently("COMMENT IF EXISTS ON USER cmt_ife_nouser IS 'Comment'");
        engine.execute("CREATE OR REPLACE USER cmt_ife_user PASSWORD = 'Sekrit!42x'");
        engine.execute("COMMENT IF EXISTS ON USER cmt_ife_user IS 'Test user'");
        assertEquals("Test user", showCell("SHOW USERS LIKE 'cmt_ife_user'", "comment"));
        engine.execute("DROP USER cmt_ife_user");
    }

    @Test
    public void testCommentIfExistsOnRole() {
        silently("COMMENT IF EXISTS ON ROLE cmt_ife_norole IS 'Comment'");
        engine.execute("CREATE OR REPLACE ROLE cmt_ife_role");
        engine.execute("COMMENT IF EXISTS ON ROLE cmt_ife_role IS 'Test role'");
        assertEquals("Test role", showCell("SHOW ROLES LIKE 'cmt_ife_role'", "comment"));
        engine.execute("DROP ROLE cmt_ife_role");
    }
}
