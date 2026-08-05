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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EXECUTE IMMEDIATE as a STATEMENT — live-verified: it is not an expression, so {@code SELECT EXECUTE
 * IMMEDIATE '...'} is a syntax error. At SESSION level its SQL source must be a string literal, a
 * {@code $$…$$} literal or a variable, and it takes no USING clause; both restrictions lift inside a
 * Snowflake Scripting block, where the source may be any expression and USING binds the {@code ?}
 * placeholders. The statement runs the dynamic SQL and returns the inner query's result set.
 */
public class ExecuteImmediateExpressionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (id INTEGER, v INTEGER)");
        engine.execute("INSERT INTO t VALUES (1, 10), (2, 20), (3, 30)");
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void literalSqlReturnsScalar() {
        assertEquals(1L, ((Number) scalar("EXECUTE IMMEDIATE 'SELECT 1'")).longValue());
    }

    @Test
    public void dynamicQueryReturnsAggregate() {
        assertEquals(60L, ((Number) scalar("EXECUTE IMMEDIATE 'SELECT SUM(v) FROM t'")).longValue());
    }

    @Test
    public void topLevelUsingClauseIsRejected() {
        // Live-verified: a session-level EXECUTE IMMEDIATE has no working USING clause at all. An
        // argument that is not a bare name — the literal 2 here — is a SYNTAX error at the
        // argument's own position; a list of names fails "Unsupported statement type 'EXECUTE'."
        // instead (both wordings measured; the names cell is pinned in ExecuteImmediateUsingTest).
        // USING is a Snowflake Scripting feature, legal only inside a BEGIN…END block, which the
        // procedural tests cover.
        final RuntimeException using = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("EXECUTE IMMEDIATE 'SELECT v FROM t WHERE id = ?' USING (2)");
            }
        });
        assertTrue(using.getMessage().contains("syntax error line 1 at position 56 unexpected '2'."),
            "unexpected: " + using.getMessage());
    }

    @Test
    public void topLevelSqlSourceMustBeALiteralOrVariable() {
        // Live-verified: at session level the SQL source is a string literal, a $$…$$ literal or a
        // variable — an arbitrary expression such as a `||` concatenation is a syntax error there.
        final RuntimeException concat = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("EXECUTE IMMEDIATE 'SELECT v FROM t WHERE id = ' || '3'");
            }
        });
        assertTrue(concat.getMessage().contains("unexpected '||'"), "unexpected: " + concat.getMessage());
        // The accepted spellings: a plain literal, a $$-quoted literal and a session variable.
        assertEquals(30L, ((Number) scalar("EXECUTE IMMEDIATE 'SELECT v FROM t WHERE id = 3'")).longValue());
        assertEquals(30L, ((Number) scalar("EXECUTE IMMEDIATE $$SELECT v FROM t WHERE id = 3$$")).longValue());
        engine.execute("SET stmt = 'SELECT v FROM t WHERE id = 3'");
        assertEquals(30L, ((Number) scalar("EXECUTE IMMEDIATE $stmt")).longValue());
    }

    @Test
    public void inBlockSourceAndUsingStayLegal() {
        // The very same forms the session level refuses are legal inside a scripting block.
        assertEquals(30L, ((Number) engine.executeQuery("""
            DECLARE s VARCHAR;
            BEGIN
              s := 'SELECT v FROM t WHERE id = ' || '3';
              EXECUTE IMMEDIATE s;
              RETURN 30;
            END;""").getRows().get(0).getValue(0)).longValue());
        assertEquals(2L, ((Number) engine.executeQuery("""
            DECLARE
              res RESULTSET;
              v INTEGER DEFAULT 2;
            BEGIN
              res := (EXECUTE IMMEDIATE 'SELECT id FROM t WHERE id = ?' USING (v));
              RETURN 2;
            END;""").getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void emptyResultYieldsNoRows() {
        assertEquals(0, engine.executeQuery("EXECUTE IMMEDIATE 'SELECT v FROM t WHERE id = 99'").getRowCount());
    }

    @Test
    public void selectEmbeddedFormIsRejected() {
        // EXECUTE IMMEDIATE cannot appear inside a SELECT list — it is a statement, not an expression.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT EXECUTE IMMEDIATE 'SELECT 1'");
            }
        });
    }
}
