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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The {@code COMMENT ON <object-type> … IS '<text>'} command, asserted through SQL — each comment
 * is read back through the same SHOW / DESCRIBE cell a client would use ({@code comment}, or
 * {@code description} for functions and procedures), so the assertions hold against a live account.
 *
 * <p>The refusal shapes are live-verified: a missing database is spelled bare
 * ({@code Database 'X' does not exist or not authorized.}), a missing table, view, stream, function
 * or procedure FULLY QUALIFIED, a missing COLUMN as a bare {@code Object}, and a function or
 * procedure whose SIGNATURE matches nothing gets the same plain does-not-exist as a missing name —
 * without the argument list the statement spelled.
 */
public class CommentCommandTest extends BaseDatabaseTest {

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

    private String messageOf(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return e.getMessage();
    }

    // ── each object kind ─────────────────────────────────────────────────────

    @Test
    public void testCommentOnDatabase() {
        engine.execute("CREATE OR REPLACE DATABASE cmt_cmd_db");
        engine.execute("COMMENT ON DATABASE cmt_cmd_db IS 'Updated comment'");
        assertEquals("Updated comment", showCell("SHOW DATABASES LIKE 'cmt_cmd_db'", "comment"));
        engine.execute("DROP DATABASE cmt_cmd_db");
    }

    @Test
    public void testCommentOnSchema() {
        engine.execute("CREATE SCHEMA cmt_cmd_schema");
        engine.execute("COMMENT ON SCHEMA cmt_cmd_schema IS 'Schema comment'");
        assertEquals("Schema comment", showCell("SHOW SCHEMAS LIKE 'cmt_cmd_schema'", "comment"));
    }

    @Test
    public void testCommentOnTable() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("COMMENT ON TABLE users IS 'User table comment'");
        assertEquals("User table comment", showCell("SHOW TABLES LIKE 'users'", "comment"));
    }

    @Test
    public void testCommentOnColumn() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, email VARCHAR)");
        engine.execute("COMMENT ON COLUMN users.name IS 'User full name'");
        engine.execute("COMMENT ON COLUMN users.email IS 'User email address'");
        assertEquals("User full name", descTableComment("users", "NAME"));
        assertEquals("User email address", descTableComment("users", "EMAIL"));
    }

    @Test
    public void testCommentOnView() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE VIEW active_users AS SELECT * FROM users");
        engine.execute("COMMENT ON VIEW active_users IS 'Active users only'");
        assertEquals("Active users only", showCell("SHOW VIEWS LIKE 'active_users'", "comment"));
    }

    @Test
    public void testCommentOnStream() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users");
        engine.execute("COMMENT ON STREAM user_stream IS 'Stream comment'");
        assertEquals("Stream comment", showCell("SHOW STREAMS LIKE 'user_stream'", "comment"));
    }

    @Test
    public void testCommentOnTask() {
        engine.execute("CREATE WAREHOUSE cmt_cmd_wh");
        engine.execute("""
            CREATE TASK daily_task
            WAREHOUSE = 'cmt_cmd_wh'
            SCHEDULE = 'USING CRON 0 9 * * * UTC'
            AS SELECT 1
            """);
        engine.execute("COMMENT ON TASK daily_task IS 'Daily task comment'");
        assertEquals("Daily task comment", showCell("SHOW TASKS LIKE 'daily_task'", "comment"));
    }

    @Test
    public void testCommentOnWarehouse() {
        engine.execute("CREATE OR REPLACE WAREHOUSE cmt_cmd_wh2");
        engine.execute("COMMENT ON WAREHOUSE cmt_cmd_wh2 IS 'Warehouse comment'");
        assertEquals("Warehouse comment", showCell("SHOW WAREHOUSES LIKE 'cmt_cmd_wh2'", "comment"));
    }

    @Test
    public void testCommentOnStage() {
        engine.execute("CREATE STAGE cmt_cmd_stage URL = 's3://bucket/path'");
        engine.execute("COMMENT ON STAGE cmt_cmd_stage IS 'Stage comment'");
        assertEquals("Stage comment", showCell("SHOW STAGES LIKE 'cmt_cmd_stage'", "comment"));
    }

    @Test
    public void testCommentOnFunction() {
        engine.execute("""
            CREATE FUNCTION add_numbers(x INTEGER, y INTEGER)
            RETURNS INTEGER AS 'x + y'
            """);
        engine.execute("COMMENT ON FUNCTION add_numbers(INTEGER, INTEGER) IS 'Function comment'");
        assertEquals("Function comment",
            showCell("SHOW USER FUNCTIONS LIKE 'add_numbers'", "description"));
    }

    @Test
    public void testCommentOnProcedure() {
        engine.execute("""
            CREATE PROCEDURE test_proc()
            RETURNS VARCHAR AS 'BEGIN RETURN ''done''; END;'
            """);
        engine.execute("COMMENT ON PROCEDURE test_proc() IS 'Procedure comment'");
        assertEquals("Procedure comment", showCell("SHOW PROCEDURES LIKE 'test_proc'", "description"));
    }

    @Test
    public void testCommentOnUser() {
        engine.execute("CREATE OR REPLACE USER cmt_cmd_user PASSWORD = 'Sekrit!42x'");
        engine.execute("COMMENT ON USER cmt_cmd_user IS 'User comment'");
        assertEquals("User comment", showCell("SHOW USERS LIKE 'cmt_cmd_user'", "comment"));
        engine.execute("DROP USER cmt_cmd_user");
    }

    @Test
    public void testCommentOnRole() {
        engine.execute("CREATE OR REPLACE ROLE cmt_cmd_role");
        engine.execute("COMMENT ON ROLE cmt_cmd_role IS 'Role comment'");
        assertEquals("Role comment", showCell("SHOW ROLES LIKE 'cmt_cmd_role'", "comment"));
        engine.execute("DROP ROLE cmt_cmd_role");
    }

    // ── update + qualified forms ─────────────────────────────────────────────

    @Test
    public void testUpdateExistingComment() {
        engine.execute("CREATE OR REPLACE DATABASE cmt_cmd_db2 COMMENT = 'Initial comment'");
        assertEquals("Initial comment", showCell("SHOW DATABASES LIKE 'cmt_cmd_db2'", "comment"));
        engine.execute("COMMENT ON DATABASE cmt_cmd_db2 IS 'Updated comment'");
        assertEquals("Updated comment", showCell("SHOW DATABASES LIKE 'cmt_cmd_db2'", "comment"));
        engine.execute("DROP DATABASE cmt_cmd_db2");
    }

    @Test
    public void testCommentOnQualifiedSchema() {
        engine.execute("CREATE SCHEMA test_db.cmt_q_schema");
        engine.execute("COMMENT ON SCHEMA test_db.cmt_q_schema IS 'Qualified schema comment'");
        assertEquals("Qualified schema comment",
            showCell("SHOW SCHEMAS LIKE 'cmt_q_schema'", "comment"));
    }

    @Test
    public void testCommentOnQualifiedTable() {
        engine.execute("CREATE TABLE test_db.test_schema.users (id INTEGER)");
        engine.execute("COMMENT ON TABLE test_db.test_schema.users IS 'Qualified table comment'");
        assertEquals("Qualified table comment", showCell("SHOW TABLES LIKE 'users'", "comment"));
    }

    @Test
    public void testCommentOnQualifiedColumn() {
        engine.execute("CREATE TABLE test_db.test_schema.users (id INTEGER, name VARCHAR)");
        engine.execute("COMMENT ON COLUMN test_db.test_schema.users.name IS 'Qualified column comment'");
        assertEquals("Qualified column comment", descTableComment("users", "NAME"));
    }

    // ── comment payloads ─────────────────────────────────────────────────────

    @Test
    public void testCommentWithSpecialCharacters() {
        engine.execute("CREATE OR REPLACE DATABASE cmt_cmd_db3");
        engine.execute("COMMENT ON DATABASE cmt_cmd_db3 IS 'Special chars: @#$%^&*()'");
        assertEquals("Special chars: @#$%^&*()", showCell("SHOW DATABASES LIKE 'cmt_cmd_db3'", "comment"));
        engine.execute("DROP DATABASE cmt_cmd_db3");
    }

    @Test
    public void testCommentWithQuotes() {
        engine.execute("CREATE OR REPLACE DATABASE cmt_cmd_db4");
        engine.execute("COMMENT ON DATABASE cmt_cmd_db4 IS 'Comment with ''quotes'''");
        assertEquals("Comment with 'quotes'", showCell("SHOW DATABASES LIKE 'cmt_cmd_db4'", "comment"));
        engine.execute("DROP DATABASE cmt_cmd_db4");
    }

    // ── refusal shapes ───────────────────────────────────────────────────────

    @Test
    public void testCommentOnNonExistentObject() {
        assertEquals("SQL compilation error:\nDatabase 'CMT_CMD_NODB' does not exist or not authorized.",
            messageOf("COMMENT ON DATABASE cmt_cmd_nodb IS 'Comment'"));
    }

    @Test
    public void testCommentOnNonExistentTable() {
        assertEquals("SQL compilation error:\nTable 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized.",
            messageOf("COMMENT ON TABLE nosuch IS 'Comment'"));
    }

    @Test
    public void testCommentOnNonExistentColumn() {
        engine.execute("CREATE TABLE users (id INTEGER)");
        // The missing COLUMN is a bare 'Object'; a missing TABLE under a column reference is the
        // fully qualified Table shape.
        assertEquals("SQL compilation error:\nObject 'NONEXISTENT' does not exist or not authorized.",
            messageOf("COMMENT ON COLUMN users.nonexistent IS 'Comment'"));
        assertEquals("SQL compilation error:\nTable 'TEST_DB.TEST_SCHEMA.NOTABLE' does not exist or not authorized.",
            messageOf("COMMENT ON COLUMN notable.name IS 'Comment'"));
    }

    // ── function / procedure signatures ──────────────────────────────────────

    @Test
    public void testCommentOnFunctionWithVarcharArguments() {
        engine.execute("""
            CREATE FUNCTION concat_strings(a VARCHAR, b VARCHAR, c VARCHAR)
            RETURNS VARCHAR AS 'a || b || c'
            """);
        engine.execute("COMMENT ON FUNCTION concat_strings(VARCHAR, VARCHAR, VARCHAR) IS 'Concatenates three strings'");
        assertEquals("Concatenates three strings",
            showCell("SHOW USER FUNCTIONS LIKE 'concat_strings'", "description"));
    }

    @Test
    public void testCommentOnFunctionWithMixedArguments() {
        engine.execute("""
            CREATE FUNCTION format_data(name VARCHAR, age INTEGER, salary DECIMAL)
            RETURNS VARCHAR AS 'name || age || salary'
            """);
        engine.execute("COMMENT ON FUNCTION format_data(VARCHAR, INTEGER, DECIMAL) IS 'Formats user data'");
        assertEquals("Formats user data",
            showCell("SHOW USER FUNCTIONS LIKE 'format_data'", "description"));
    }

    @Test
    public void testCommentOnFunctionWithNoArguments() {
        // The cast keeps the declared VARCHAR compatible with the body's type — a bare
        // CURRENT_TIMESTAMP body is refused at CREATE (see UdfReturnTypeCompatibilityTest).
        engine.execute("""
            CREATE FUNCTION get_timestamp()
            RETURNS VARCHAR AS 'CURRENT_TIMESTAMP::VARCHAR'
            """);
        engine.execute("COMMENT ON FUNCTION get_timestamp() IS 'Returns current timestamp'");
        assertEquals("Returns current timestamp",
            showCell("SHOW USER FUNCTIONS LIKE 'get_timestamp'", "description"));
    }

    @Test
    public void testCommentOnProcedureWithArguments() {
        engine.execute("""
            CREATE PROCEDURE update_user(user_id INTEGER, new_name VARCHAR)
            RETURNS VARCHAR AS 'BEGIN RETURN ''done''; END;'
            """);
        engine.execute("COMMENT ON PROCEDURE update_user(INTEGER, VARCHAR) IS 'Updates user name'");
        assertEquals("Updates user name", showCell("SHOW PROCEDURES LIKE 'update_user'", "description"));
    }

    @Test
    public void testCommentOnFunctionWithWrongSignature() {
        engine.execute("""
            CREATE FUNCTION add_numbers(x INTEGER, y INTEGER)
            RETURNS INTEGER AS 'x + y'
            """);
        assertEquals("SQL compilation error:\nFunction 'TEST_DB.TEST_SCHEMA.ADD_NUMBERS' does not exist or not authorized.",
            messageOf("COMMENT ON FUNCTION add_numbers(VARCHAR, VARCHAR) IS 'Wrong signature'"));
    }

    @Test
    public void testCommentOnFunctionWithWrongArgumentCount() {
        engine.execute("""
            CREATE FUNCTION add_numbers(x INTEGER, y INTEGER)
            RETURNS INTEGER AS 'x + y'
            """);
        assertEquals("SQL compilation error:\nFunction 'TEST_DB.TEST_SCHEMA.ADD_NUMBERS' does not exist or not authorized.",
            messageOf("COMMENT ON FUNCTION add_numbers(INTEGER) IS 'Wrong count'"));
    }
}
