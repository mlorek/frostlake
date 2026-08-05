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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Binding an argument to a user-defined function parameter declared with a STRUCTURED type
 * ({@code OBJECT(x VARCHAR)}, {@code ARRAY(INT)}).
 *
 * <p>Snowflake matches these EXACTLY, in both directions: a plain {@code OBJECT} / {@code VARIANT} /
 * {@code ARRAY} argument is refused by a structured parameter, and a structured argument is refused by
 * a plain semi-structured parameter. Live-verified on a real account.
 */
public class StructuredTypeUdfArgumentTest extends BaseDatabaseTest {

    private static final String OBJ_X = "CAST(OBJECT_CONSTRUCT('x','a') AS OBJECT(x VARCHAR))";

    @Override
    protected void setupTest() {
        engine.execute(
            "CREATE FUNCTION f_struct(o OBJECT(x VARCHAR)) RETURNS VARCHAR AS $$ o:x::VARCHAR $$");
        engine.execute("CREATE FUNCTION f_plain(o OBJECT) RETURNS VARCHAR AS $$ o:x::VARCHAR $$");
        engine.execute("CREATE FUNCTION f_variant(o VARIANT) RETURNS VARCHAR AS $$ o:x::VARCHAR $$");
        engine.execute("CREATE FUNCTION f_arr(v ARRAY(INT)) RETURNS INT AS $$ v[0]::INT $$");
        engine.execute("CREATE FUNCTION f_plain_arr(v ARRAY) RETURNS INT AS $$ v[0]::INT $$");
    }

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    private void assertInvalidArguments(final String sql, final String funcName) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(error.getMessage())
                .contains("Invalid argument types for function '" + funcName + "'"),
            "expected an argument-type error for " + funcName + ", got: " + error.getMessage());
    }

    /** A cast to the declared structured type is what binds. */
    @Test
    public void structuredParameterAcceptsTheMatchingStructuredArgument() {
        // Live: f_struct(CAST(OBJECT_CONSTRUCT('x','a') AS OBJECT(x VARCHAR))) is 'a', and the ::
        // spelling of the same cast binds identically.
        assertEquals("a", scalar("SELECT f_struct(" + OBJ_X + ")"));
        assertEquals("a", scalar("SELECT f_struct(OBJECT_CONSTRUCT('x','a')::OBJECT(x VARCHAR))"));
        assertEquals(1L, ((Number) scalar("SELECT f_arr(CAST([1,2] AS ARRAY(INT)))")).longValue());
    }

    /** A plain semi-structured argument does NOT bind to a structured parameter. */
    @Test
    public void structuredParameterRejectsPlainSemiStructuredArguments() {
        // Live: f_struct(OBJECT_CONSTRUCT('x','a')) fails "Invalid argument types for function
        // 'F_STRUCT': (OBJECT)", f_struct(PARSE_JSON('{"x":"a"}')) fails with (VARIANT), and
        // f_arr([1,2]) fails with (ARRAY).
        assertInvalidArguments("SELECT f_struct(OBJECT_CONSTRUCT('x','a'))", "F_STRUCT");
        assertInvalidArguments("SELECT f_struct(PARSE_JSON('{\"x\":\"a\"}'))", "F_STRUCT");
        assertInvalidArguments("SELECT f_arr([1,2])", "F_ARR");
    }

    /** A DIFFERENT structured type does not bind either — the field names and base types must match. */
    @Test
    public void structuredParameterRejectsADifferentStructuredType() {
        // Live: against f_struct(o OBJECT(x VARCHAR)), a wider OBJECT(x VARCHAR, y INT), a renamed
        // OBJECT(y VARCHAR), an OBJECT(x INT) and a MAP(VARCHAR,VARCHAR) all fail "Invalid argument
        // types"; only the LENGTH of a field type is ignored.
        assertInvalidArguments(
            "SELECT f_struct(CAST(OBJECT_CONSTRUCT('x','a','y',1) AS OBJECT(x VARCHAR, y INT)))",
            "F_STRUCT");
        assertInvalidArguments(
            "SELECT f_struct(CAST(OBJECT_CONSTRUCT('y','a') AS OBJECT(y VARCHAR)))", "F_STRUCT");
        assertInvalidArguments(
            "SELECT f_struct(CAST(OBJECT_CONSTRUCT('x',5) AS OBJECT(x INT)))", "F_STRUCT");
        assertInvalidArguments(
            "SELECT f_struct(CAST(OBJECT_CONSTRUCT('x','a') AS MAP(VARCHAR,VARCHAR)))", "F_STRUCT");
        // Live: f_struct(CAST(... AS OBJECT(x VARCHAR(10)))) IS accepted — the declared length of a
        // field does not take part in the match.
        assertEquals("a",
            scalar("SELECT f_struct(CAST(OBJECT_CONSTRUCT('x','a') AS OBJECT(x VARCHAR(10))))"));
    }

    /** The rule runs in BOTH directions: a plain parameter refuses a structured argument. */
    @Test
    public void plainSemiStructuredParameterRejectsAStructuredArgument() {
        // Live: f_plain(o OBJECT) called with CAST(... AS OBJECT(x VARCHAR)) fails "Invalid argument
        // types for function 'F_PLAIN': (OBJECT(x VARCHAR(134217728)))", the VARIANT parameter fails
        // the same way, and so does a plain ARRAY parameter given an ARRAY(INT).
        assertInvalidArguments("SELECT f_plain(" + OBJ_X + ")", "F_PLAIN");
        assertInvalidArguments("SELECT f_variant(" + OBJ_X + ")", "F_VARIANT");
        assertInvalidArguments("SELECT f_plain_arr(CAST([1,2] AS ARRAY(INT)))", "F_PLAIN_ARR");
        // ... while the plain argument each of them was declared for still binds.
        assertEquals("a", scalar("SELECT f_plain(OBJECT_CONSTRUCT('x','a'))"));
        assertEquals("a", scalar("SELECT f_variant(OBJECT_CONSTRUCT('x','a'))"));
    }

    /** A bare NULL argument is accepted by a structured parameter. */
    @Test
    public void bareNullBindsToAStructuredParameter() {
        // Live: f_struct(NULL) returns NULL rather than an argument-type error.
        assertNull(scalar("SELECT f_struct(NULL)"));
    }

    /** A structured COLUMN binds by its declared type, exactly like a cast expression. */
    @Test
    public void structuredColumnBindsByItsDeclaredType() {
        // Live: with t(o OBJECT(x VARCHAR), po OBJECT), f_struct(o) is 'a' while f_struct(po) fails
        // "Invalid argument types for function 'F_STRUCT': (OBJECT)".
        engine.execute("CREATE TABLE udf_cols (o OBJECT(x VARCHAR), po OBJECT)");
        engine.execute("INSERT INTO udf_cols SELECT " + OBJ_X + ", OBJECT_CONSTRUCT('x','a')");
        assertEquals("a", scalar("SELECT f_struct(o) FROM udf_cols"));
        assertInvalidArguments("SELECT f_struct(po) FROM udf_cols", "F_STRUCT");
    }
}
