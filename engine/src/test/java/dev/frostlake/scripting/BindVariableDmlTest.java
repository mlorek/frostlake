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
        // Live does not call this a bind-variable problem: it reports the NAME as an unresolvable
        // identifier, at the COLON's offset — measured across INSERT VALUES, UPDATE SET,
        // UPDATE/DELETE WHERE and a subquery, the position is the colon every time.
        assertEquals("SQL compilation error: error line 1 at position 28\n"
            + "invalid identifier 'NOPE'", ex.getMessage());
    }

    @Test
    public void numericBindParsesAndFailsCleanlyWhenUnbound() {
        // SELECT :1 is grammar-accepted (numeric binds); without a bound value the engine reports
        // the missing bind rather than a syntax error.
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT :1");
            }
        });
        assertTrue(e.getMessage().contains("Bind variable"),
            "expected a missing-bind error, got: " + e.getMessage());
    }

    /**
     * Every statement family reports an unresolved {@code :name} at the COLON's offset — live-measured
     * for INSERT VALUES, UPDATE SET, UPDATE WHERE and DELETE WHERE alike. The expected offset is taken
     * from the statement itself rather than counted by hand, so the claim is the RULE and not a
     * transcription of four numbers.
     */
    @Test
    public void everyDmlFamilyReportsTheColonsPosition() {
        engine.execute("CREATE TABLE bt (id INTEGER, v VARCHAR)");
        engine.execute("INSERT INTO bt VALUES (1, 'a')");
        assertBindRefusedAtItsColon("BEGIN INSERT INTO bt VALUES (2, :nope); RETURN 'ok'; END");
        assertBindRefusedAtItsColon("BEGIN UPDATE bt SET v = :nope WHERE id = 1; RETURN 'ok'; END");
        assertBindRefusedAtItsColon("BEGIN UPDATE bt SET v = 'z' WHERE id = :nope; RETURN 'ok'; END");
        assertBindRefusedAtItsColon("BEGIN DELETE FROM bt WHERE id = :nope; RETURN 'ok'; END");
    }

    private void assertBindRefusedAtItsColon(final String sql) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        assertEquals("SQL compilation error: error line 1 at position " + sql.indexOf(":nope")
            + "\ninvalid identifier 'NOPE'", ex.getMessage(), sql);
    }

    /**
     * A variant path step may be a KEYWORD the grammar admits as an identifier — the colon after
     * it is still path syntax, not a bind variable, so {@code src:value:attributes:core.x} reads
     * the nested field instead of refusing ":core" as a dotted bind.
     */
    @Test
    public void testVariantPathStepAfterKeywordNamedField() {
        engine.execute("CREATE TABLE vt (src VARIANT)");
        engine.execute("INSERT INTO vt SELECT PARSE_JSON('{\"value\":{\"attributes\":{\"core\":{\"x\":\"deep\"}}}}')");

        final ResultSet result = engine.executeQuery("""
            DECLARE r STRING;
            BEGIN
                SELECT src:value:attributes:core.x::STRING INTO :r FROM vt;
                RETURN :r;
            END;
            """);

        assertEquals("deep", result.getRows().get(0).getValue(0));
    }
}
