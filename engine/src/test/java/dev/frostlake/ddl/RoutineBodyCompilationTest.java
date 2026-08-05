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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snowflake compiles a routine's SQL body at CREATE time; Frostlake mirrors that for the shapes it can
 * judge. The check deliberately FAILS OPEN: Frostlake's grammar is a subset of Snowflake's, so a body the
 * engine cannot fully parse means "unrecognised construct", not "invalid SQL" — rejecting those would
 * break working deployment scripts the account itself accepts. What IS rejected is a shape the engine can
 * positively read and knows to be wrong: a LANGUAGE SQL procedure body that is one plain statement (a
 * block is required), and a TABLE function body that is a scripting block (a query is required).
 *
 * <p>A SCALAR SQL UDF, by contrast, accepts all three body shapes — expression, query and scripting block
 * — and {@link ScriptingUdfBodyTest} covers the block form and the restricted language it may use.
 */
public class RoutineBodyCompilationTest extends BaseDatabaseTest {

    @Test
    public void procedureBodyThatIsNotABlockIsRejected() {
        final RuntimeException failure = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(
                    "CREATE PROCEDURE p_bad() RETURNS VARCHAR LANGUAGE SQL AS 'SELECT 1 + 1'");
            }
        });
        // The compiler raises exactly "SQL compilation error:\nsyntax error …"; the engine's uniform error
        // wrapping ("Failed to execute SQL: Failed to execute CREATE statement: …") prefixes every message.
        assertTrue(failure.getMessage().contains("SQL compilation error:\nsyntax error"), failure.getMessage());
    }

    @Test
    public void procedureBodyThatIsABareStatementIsRejected() {
        // A bare SELECT is a perfectly good SQL statement but NOT a scripting block — Snowflake rejects it.
        final RuntimeException failure = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(
                    "CREATE PROCEDURE p_bad2(a INTEGER) RETURNS INTEGER LANGUAGE SQL AS 'SELECT a + 1'");
            }
        });
        // Only the compilation-error HEAD is asserted: the hint that follows it ("… a BEGIN ... END
        // block is required") is Frostlake's wording, while a real account reports the same rejection as
        // a plain syntax error.
        assertTrue(failure.getMessage().contains("SQL compilation error"), failure.getMessage());
    }

    @Test
    public void procedureCompilationErrorIsNotSwallowedByIfNotExists() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE PROCEDURE IF NOT EXISTS p_bad3() RETURNS VARCHAR LANGUAGE SQL "
                    + "AS 'SELECT 1 + 1'");
            }
        });
    }

    @Test
    public void procedureBodyThatIsABlockSucceeds() {
        engine.execute("CREATE PROCEDURE p_ok() RETURNS VARCHAR LANGUAGE SQL AS BEGIN RETURN 'x'; END");
        final ResultSet result = engine.executeQuery("CALL p_ok()");
        assertEquals(1, result.getRowCount());
        assertEquals("x", result.getRows().get(0).getValue(0));
    }

    @Test
    public void procedureBodyWithADeclareSectionSucceeds() {
        engine.execute("""
            CREATE PROCEDURE p_declared(a INTEGER) RETURNS INTEGER LANGUAGE SQL AS
            $$
            DECLARE
              total INTEGER;
            BEGIN
              total := a + 1;
              RETURN total;
            END
            $$
            """);
        final ResultSet result = engine.executeQuery("CALL p_declared(41)");
        assertEquals(42, ((Number) result.getRows().get(0).getValue(0)).intValue());
    }

    @Test
    public void sqlUdfBodyThatIsAScriptingBlockIsAccepted() {
        // Live-verified: a SCALAR SQL UDF may have a scripting block for a body, and it runs.
        // This test previously asserted the opposite — a rejection inferred as the symmetric mirror of the
        // (correct) procedure rule, never probed. ScriptingUdfBodyTest covers the shape in full.
        engine.execute("CREATE FUNCTION f_block(x INTEGER) RETURNS INTEGER AS 'BEGIN RETURN x; END'");
        final ResultSet result = engine.executeQuery("SELECT f_block(4) AS v");
        assertEquals(4, ((Number) result.getRows().get(0).getValue(0)).intValue());
    }

    @Test
    public void tableFunctionBodyThatIsAScriptingBlockIsRejected() {
        // The one UDF shape a block is still wrong for: live, a RETURNS TABLE(...) function with a
        // BEGIN...END body is an ordinary SQL-UDF syntax error.
        final RuntimeException failure = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE FUNCTION f_bad_table() RETURNS TABLE(x INTEGER) "
                    + "AS 'BEGIN RETURN TABLE(SELECT 1); END'");
            }
        });
        assertTrue(failure.getMessage().contains("SQL compilation error"), failure.getMessage());
    }

    @Test
    public void sqlUdfExpressionBodySucceeds() {
        engine.execute("CREATE FUNCTION f_expr(x INTEGER) RETURNS INTEGER AS 'x + 10'");
        final ResultSet result = engine.executeQuery("SELECT f_expr(5) AS v");
        assertEquals(15, ((Number) result.getRows().get(0).getValue(0)).intValue());
    }

    @Test
    public void sqlUdfQueryBodySucceeds() {
        engine.execute("CREATE TABLE udf_src (n INTEGER)");
        engine.execute("INSERT INTO udf_src VALUES (7), (8)");
        engine.execute("CREATE FUNCTION f_query() RETURNS INTEGER AS 'SELECT MAX(n) FROM udf_src'");
        final ResultSet result = engine.executeQuery("SELECT f_query() AS v");
        assertEquals(8, ((Number) result.getRows().get(0).getValue(0)).intValue());
    }

    @Test
    public void nonSqlRoutineBodiesAreNotCompiled() {
        // JavaScript / Python bodies are not SQL — their own runtimes compile them (and, with the optional
        // module absent, only a CALL fails). CREATE must still succeed.
        engine.execute("CREATE PROCEDURE p_js() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS 'not sql at all'");
        engine.execute("CREATE FUNCTION f_py(x INTEGER) RETURNS INTEGER LANGUAGE PYTHON AS 'not sql at all'");

        final ResultSet procedures = engine.executeQuery("SHOW PROCEDURES");
        assertTrue(showListsName(procedures, "P_JS"));
        final ResultSet functions = engine.executeQuery("SHOW USER FUNCTIONS");
        assertTrue(showListsName(functions, "F_PY"));
    }

    private boolean showListsName(final ResultSet result, final String expected) {
        for (int row = 0; row < result.getRowCount(); row++) {
            for (int col = 0; col < result.getColumns().size(); col++) {
                final Object value = result.getRows().get(row).getValue(col);
                if (expected.equalsIgnoreCase(String.valueOf(value))) {
                    return true;
                }
            }
        }
        return false;
    }
}
