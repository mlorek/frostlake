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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An unnamed bind that nothing supplied is refused where the statement is COMPILED. A block is refused
 * whole, before any of it runs, in one of two sentences — the scripting one for a {@code ?} of its own
 * and the {@code {2}} one for a {@code ?} inside an embedded SQL statement. A view's query and a UDF's
 * body are refused with a sentence of their own, because a definition may carry no bind at all. A cursor
 * declaration is the exception the account makes, written DECLARE or LET alike, and a stored procedure's body is not held to the rule.
 * Live-verified.
 */
public class UnnamedBindRefusalTest extends BaseDatabaseTest {

    /** Runs a block and returns its RETURN value as text. */
    private String block(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? "NULL" : value.toString();
    }

    /** Asserts a statement is refused with a message carrying {@code fragment}. */
    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
        assertTrue(refused.getMessage() != null && refused.getMessage().contains(fragment),
            sql + " should be refused with \"" + fragment + "\" but read: " + refused.getMessage());
    }

    /** A block's own {@code ?} names the stored procedure, and the refusal is a compilation one. */
    @Test
    public void aScriptingBindNamesTheStoredProcedure() {
        assertRefused("BEGIN LET x := ?; RETURN x; END;",
            "Unexpected unnamed bind in SQL stored procedure.");
        assertRefused("BEGIN RETURN ?; END;", "Unexpected unnamed bind in SQL stored procedure.");
        assertRefused("EXECUTE IMMEDIATE $$ BEGIN RETURN ?; END; $$",
            "Unexpected unnamed bind in SQL stored procedure.");
    }

    /** A {@code ?} inside an embedded statement names the literal {2} instead. */
    @Test
    public void aBindInsideAnEmbeddedStatementNamesTheBraces() {
        assertRefused("BEGIN SELECT ?; END;", "Unexpected unnamed bind in {2}.");
        assertRefused("DECLARE rs RESULTSET DEFAULT (SELECT ?); BEGIN RETURN TABLE(rs); END;",
            "Unexpected unnamed bind in {2}.");
    }

    /** The refusal is a COMPILATION one: it is not the uncaught exception a run-time fault gives. */
    @Test
    public void theBlockIsRefusedBeforeItRuns() {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("BEGIN SELECT ?; END;");
            }
        });
        assertTrue(refused.getMessage().startsWith("SQL compilation error:"),
            "the block must be refused as it compiles: " + refused.getMessage());
        assertTrue(!refused.getMessage().contains("Uncaught exception"),
            "no statement of the block may have run: " + refused.getMessage());
    }

    /** A cursor declaration is the exception, written DECLARE or LET alike. */
    @Test
    public void aCursorDeclarationIsAccepted() {
        assertEquals("1", block("DECLARE c1 CURSOR FOR SELECT ?; BEGIN RETURN 1; END;"));
        assertEquals("1", block("BEGIN LET c CURSOR FOR SELECT ?; RETURN 1; END;"));
    }

    /** Its bind is the one OPEN supplies, so the cursor reads the rows it asked for. */
    @Test
    public void aCursorBindIsSuppliedByOpen() {
        engine.execute("CREATE OR REPLACE TABLE cursor_bind (i INT)");
        engine.execute("INSERT INTO cursor_bind VALUES (1), (2)");
        assertEquals("1", block("BEGIN LET c CURSOR FOR SELECT i FROM cursor_bind WHERE i = ?;"
            + " OPEN c USING (1); LET n := 0; FOR r IN c DO n := n + 1; END FOR; RETURN n; END;"));
    }

    /** A view's query and a UDF's body may carry no bind at all, in a sentence of their own. */
    @Test
    public void aDefinitionMayCarryNoBind() {
        final String noBinds = "Bind variables not allowed in view and UDF definitions.";
        assertRefused("CREATE VIEW bind_view AS SELECT ? AS c", noBinds);
        assertRefused("CREATE OR REPLACE VIEW bind_view2 AS SELECT ? AS c", noBinds);
        assertRefused("CREATE OR REPLACE FUNCTION bind_fn() RETURNS NUMBER AS 'SELECT ?'", noBinds);
        assertRefused("CREATE OR REPLACE FUNCTION bind_fn2() RETURNS NUMBER AS $$ ? $$", noBinds);
    }

    /** A stored procedure's body is NOT held to that rule — the account creates it. */
    @Test
    public void aStoredProcedureBodyIsCreated() {
        engine.execute("CREATE OR REPLACE PROCEDURE bind_proc() RETURNS NUMBER LANGUAGE SQL"
            + " AS $$ BEGIN RETURN ?; END; $$");
    }

    /** An ordinary statement keeps the unset-bind sentence, an UPDATE over an empty table included. */
    @Test
    public void anOrdinaryStatementKeepsTheUnsetSentence() {
        engine.execute("CREATE OR REPLACE TABLE bind_target (i INT)");
        final String unset = "Bind variable ? not set.";
        assertRefused("SELECT ?", unset);
        assertRefused("INSERT INTO bind_target VALUES (?)", unset);
        assertRefused("UPDATE bind_target SET i = ? WHERE i = 1", unset);
        assertRefused("UPDATE bind_target SET i = ?", unset);
        assertRefused("DELETE FROM bind_target WHERE i = ?", unset);
    }
}
