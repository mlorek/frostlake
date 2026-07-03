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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code :name} bind-variable references inside plain SQL statements of a Snowflake Scripting block
 * (INSERT VALUES, WHERE predicates, UPDATE SET, arbitrary expressions), resolved from the block's
 * procedural scope — including a stored procedure's parameters. Previously these failed at parse
 * time ("bind variable not ported"); only the bare-name form worked.
 */
public class BindVariableDmlTest extends BaseDatabaseTest {

    private Object ret(final String block) {
        final ResultSet rs = engine.executeQuery(block);
        assertEquals(1, rs.getRowCount());
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void insertValuesBindVarInBlockBody() {
        engine.execute("CREATE TABLE t (v VARCHAR)");
        ret("DECLARE v VARCHAR DEFAULT 'hello'; BEGIN INSERT INTO t VALUES (:v); RETURN 'ok'; END");
        final ResultSet rs = engine.executeQuery("SELECT v FROM t");
        assertEquals(1, rs.getRowCount());
        assertEquals("hello", rs.getRows().get(0).getValue(0));
    }

    // The motivating case: a stored procedure's parameter referenced as :param in the body's DML.
    @Test
    public void procedureParameterBindVarInInsert() {
        engine.execute("CREATE TABLE call_log (v VARCHAR)");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE log_it(v VARCHAR) RETURNS VARCHAR LANGUAGE SQL "
            + "AS $$ BEGIN INSERT INTO call_log VALUES (:v); RETURN 'logged'; END $$");
        assertEquals("logged", ret("CALL log_it('x')"));
        final ResultSet rs = engine.executeQuery("SELECT v FROM call_log");
        assertEquals(1, rs.getRowCount());
        assertEquals("x", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void bindVarInWherePredicate() {
        engine.execute("CREATE TABLE nums (n INTEGER)");
        engine.execute("INSERT INTO nums VALUES (1), (2), (3)");
        ret("DECLARE x INTEGER DEFAULT 2; BEGIN DELETE FROM nums WHERE n = :x; RETURN 'ok'; END");
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM nums WHERE n = 2");
        assertEquals(0L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        final ResultSet all = engine.executeQuery("SELECT COUNT(*) FROM nums");
        assertEquals(2L, ((Number) all.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void bindVarInUpdateSet() {
        engine.execute("CREATE TABLE t (id INTEGER, s VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'old'), (2, 'keep')");
        ret("DECLARE nv VARCHAR DEFAULT 'new';"
            + " BEGIN UPDATE t SET s = :nv WHERE id = 1; RETURN 'ok'; END");
        final ResultSet rs = engine.executeQuery("SELECT s FROM t ORDER BY id");
        assertEquals("new", rs.getRows().get(0).getValue(0));
        assertEquals("keep", rs.getRows().get(1).getValue(0));
    }

    // A bind variable participates in a larger expression.
    @Test
    public void bindVarInArithmeticExpression() {
        engine.execute("CREATE TABLE t (n INTEGER)");
        ret("DECLARE x INTEGER DEFAULT 41; BEGIN INSERT INTO t VALUES (:x + 1); RETURN 'ok'; END");
        final ResultSet rs = engine.executeQuery("SELECT n FROM t");
        assertEquals(42L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    // A declared-but-unset variable resolves to NULL (not an error).
    @Test
    public void declaredNullBindVarYieldsNull() {
        engine.execute("CREATE TABLE t (v VARCHAR)");
        ret("DECLARE v VARCHAR; BEGIN INSERT INTO t VALUES (:v); RETURN 'ok'; END");
        final ResultSet rs = engine.executeQuery("SELECT v FROM t");
        assertEquals(1, rs.getRowCount());
        assertNull(rs.getRows().get(0).getValue(0));
    }

    // An undeclared bind variable is an error — not a silent NULL.
    @Test
    public void undefinedBindVarErrors() {
        engine.execute("CREATE TABLE t (v VARCHAR)");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("BEGIN INSERT INTO t VALUES (:nope); RETURN 'ok'; END");
            }
        });
        assertTrue(ex.getMessage().contains("Bind variable not defined"),
            "unexpected message: " + ex.getMessage());
    }
}
