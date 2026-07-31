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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Snowflake Scripting error variables {@code SQLCODE}, {@code SQLERRM}, and {@code SQLSTATE},
 * available inside an EXCEPTION handler. A user-defined exception exposes its declared code and
 * message with SQLSTATE 'P0001'; division by zero maps to its Snowflake code (100051 / '22012');
 * a missing object reports SQLSTATE '42S02'; other engine errors carry a generic statement-error
 * code. Values are handler-scoped: a nested handled block restores the enclosing handler's values.
 */
public class SqlErrorVariablesTest extends BaseDatabaseTest {

    private Object ret(final String block) {
        final ResultSet rs = engine.executeQuery(block);
        assertEquals(1, rs.getRowCount());
        return rs.getRows().get(0).getValue(0);
    }

    // ── user-defined exception: declared code + message, SQLSTATE 'P0001' ──────────────────────────

    @Test
    public void userExceptionSqlcode() {
        final Object v = ret(
            "DECLARE e EXCEPTION (-20001, 'boom'); BEGIN RAISE e;"
            + " EXCEPTION WHEN e THEN RETURN SQLCODE; END");
        assertEquals(-20001L, ((Number) v).longValue());
    }

    @Test
    public void userExceptionSqlerrm() {
        assertEquals("boom", ret(
            "DECLARE e EXCEPTION (-20001, 'boom'); BEGIN RAISE e;"
            + " EXCEPTION WHEN e THEN RETURN SQLERRM; END"));
    }

    @Test
    public void userExceptionSqlstate() {
        assertEquals("P0001", ret(
            "DECLARE e EXCEPTION (-20001, 'boom'); BEGIN RAISE e;"
            + " EXCEPTION WHEN e THEN RETURN SQLSTATE; END"));
    }

    // ── division by zero: the Snowflake-documented code and state ─────────────────────────────────

    @Test
    public void divisionByZeroSqlstate() {
        assertEquals("22012", ret(
            "BEGIN RETURN 1 / 0; EXCEPTION WHEN OTHER THEN RETURN SQLSTATE; END"));
    }

    @Test
    public void divisionByZeroSqlcode() {
        final Object v = ret(
            "BEGIN RETURN 1 / 0; EXCEPTION WHEN OTHER THEN RETURN SQLCODE; END");
        assertEquals(100051L, ((Number) v).longValue());
    }

    @Test
    public void divisionByZeroSqlerrm() {
        final Object v = ret(
            "BEGIN RETURN 1 / 0; EXCEPTION WHEN OTHER THEN RETURN SQLERRM; END");
        assertTrue(String.valueOf(v).contains("Division by zero"),
            "SQLERRM should carry the error message, got: " + v);
    }

    // ── generic engine error: statement-error fallback code/state, message preserved ──────────────

    @Test
    public void missingObjectSqlstateIs42S02() {
        // Snowflake reports missing-object compilation errors with SQLSTATE '42S02'.
        assertEquals("42S02", ret(
            "BEGIN SELECT * FROM no_such_table_xyz;"
            + " EXCEPTION WHEN OTHER THEN RETURN SQLSTATE; END"));
    }

    @Test
    public void genericStatementErrorSqlerrmMentionsCause() {
        final Object v = ret(
            "BEGIN SELECT * FROM no_such_table_xyz;"
            + " EXCEPTION WHEN OTHER THEN RETURN SQLERRM; END");
        assertTrue(String.valueOf(v).toUpperCase().contains("NO_SUCH_TABLE_XYZ"),
            "SQLERRM should mention the failing object, got: " + v);
    }

    // ── error variables are usable in DML inside the handler, bound as :SQLCODE / :SQLERRM ────────

    @Test
    public void handlerLogsSqlerrmViaInsert() {
        // They are scripting variables, so an embedded SQL statement reaches them only through the bind
        // form; written bare inside the INSERT they would be column names ("invalid identifier").
        engine.execute("CREATE TABLE err_log (code INTEGER, msg VARCHAR)");
        engine.executeQuery(
            "DECLARE e EXCEPTION (-20007, 'kaput'); BEGIN RAISE e;"
            + " EXCEPTION WHEN e THEN INSERT INTO err_log VALUES (:SQLCODE, :SQLERRM); RETURN 'ok'; END");
        final ResultSet rs = engine.executeQuery("SELECT code, msg FROM err_log");
        assertEquals(1, rs.getRowCount());
        assertEquals(-20007L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals("kaput", rs.getRows().get(0).getValue(1));
    }

    // ── nesting: an inner handled block must not clobber the outer handler's values ───────────────

    @Test
    public void nestedHandlerRestoresOuterErrorInfo() {
        final Object v = ret(
            "DECLARE e1 EXCEPTION (-20001, 'outer'); e2 EXCEPTION (-20002, 'inner');"
            + " BEGIN RAISE e1;"
            + " EXCEPTION WHEN e1 THEN"
            + "   BEGIN RAISE e2; EXCEPTION WHEN e2 THEN NULL; END;"
            + "   RETURN SQLCODE || ':' || SQLERRM; END");
        assertEquals("-20001:outer", String.valueOf(v));
    }
}
