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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bare identifier in a scripting block must name something in scope, or the block is refused.
 *
 * <p>Frostlake used to resolve an unknown name to NULL, so {@code RETURN missing_name} quietly gave
 * back NULL where live raises {@code invalid identifier 'MISSING_NAME'} — a typo'd variable produced a
 * plausible wrong answer instead of an error.
 *
 * <p>The refusals below are asserted from BOTH sides on purpose. Refusing too much is the worse
 * failure: a script Snowflake runs must keep running here, so every shape live accepts —
 * loop variables, cursor records, procedure parameters, embedded SQL naming real columns — has its own
 * test alongside the rejections.
 */
public class ScriptingIdentifierResolutionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (id INTEGER, nm VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'a'), (2, 'b')");
    }

    private String block(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private void refused(final String sql, final String expectedName) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, "Snowflake refuses this block: " + sql);
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        final String message = String.valueOf(root.getMessage());
        assertTrue(message.contains("invalid identifier") && message.contains(expectedName),
            "expected an invalid-identifier refusal naming " + expectedName + ", was: " + message);
    }

    @Test
    public void anUndeclaredNameIsRefused() {
        refused("BEGIN RETURN missing_name; END", "MISSING_NAME");
    }

    /**
     * The whole block is compiled before any of it runs, so a reference on a branch this execution
     * will never take is refused just the same. This is the measurement that rules out checking the
     * name when it is evaluated.
     */
    @Test
    public void anUnreachableReferenceIsRefusedToo() {
        refused("BEGIN IF (1 = 2) THEN RETURN missing_name; END IF; RETURN 99; END", "MISSING_NAME");
        refused("BEGIN LET n INTEGER := 0; WHILE (n > 0) DO RETURN missing_name; END WHILE;"
            + " RETURN 98; END", "MISSING_NAME");
    }

    /** A column of a table is not in scope in a scripting expression — only inside a SQL statement. */
    @Test
    public void aBareColumnNameIsRefused() {
        refused("BEGIN RETURN id; END", "ID");
    }

    /** Scope is ordered: a name is invisible before its own declaration. */
    @Test
    public void useBeforeDeclarationIsRefused() {
        refused("BEGIN RETURN later_v; LET later_v INTEGER := 1; END", "LATER_V");
    }

    /** An inner block's variables are gone once it ends. */
    @Test
    public void anInnerBlocksVariableIsRefusedOutside() {
        refused("BEGIN BEGIN LET inner_v INTEGER := 1; END; RETURN inner_v; END", "INNER_V");
    }

    /** A loop's counter and a cursor's record both end with their loop. */
    @Test
    public void loopVariablesAreRefusedAfterTheLoop() {
        refused("BEGIN FOR i IN 1 TO 2 DO NULL; END FOR; RETURN i; END", "I");
        refused("DECLARE c CURSOR FOR SELECT id, nm FROM t;"
            + " BEGIN FOR r IN c DO NULL; END FOR; RETURN r.nm; END", "R");
    }

    /** Assignment to a name that was never declared is refused, like any other reference. */
    @Test
    public void assigningToAnUndeclaredNameIsRefused() {
        refused("BEGIN missing_name := 1; RETURN 2; END", "MISSING_NAME");
    }

    /** Cursor and exception names must resolve where a statement names one directly. */
    @Test
    public void unknownCursorAndExceptionNamesAreRefused() {
        refused("BEGIN OPEN nocur; RETURN 1; END", "NOCUR");
        refused("BEGIN RAISE nosuch_exc; RETURN 1; END", "NOSUCH_EXC");
    }

    /** A quoted name is refused the same way, and reported with its quotes. */
    @Test
    public void aQuotedUnknownNameIsRefused() {
        refused("BEGIN RETURN \"missing_name\"; END", "\"missing_name\"");
    }

    // ---- the other direction: everything live accepts must still run -------------------------

    @Test
    public void declaredVariablesAndLoopCountersResolve() {
        assertEquals("7", block("DECLARE v INTEGER DEFAULT 7; BEGIN RETURN v; END"));
        assertEquals("6", block("BEGIN LET total INTEGER := 0; FOR i IN 1 TO 3 DO"
            + " total := total + i; END FOR; RETURN total; END"));
        assertEquals("2", block("BEGIN LET x INTEGER := 1; RETURN x + 1; END"));
    }

    @Test
    public void aCursorRecordFieldResolves() {
        assertEquals("a", block("DECLARE c CURSOR FOR SELECT id, nm FROM t;"
            + " BEGIN FOR r IN c DO RETURN r.nm; END FOR; RETURN 'none'; END"));
    }

    /** A procedure's parameters are in scope in its body — and only its own. */
    @Test
    public void procedureParametersResolve() {
        engine.execute("CREATE OR REPLACE PROCEDURE ap(x INTEGER) RETURNS INTEGER LANGUAGE SQL"
            + " AS $$ BEGIN RETURN x + 1; END $$");
        assertEquals("5", block("CALL ap(4)"));
    }

    /**
     * A bad name in a procedure body surfaces at CALL, not at CREATE — live compiles a body when it
     * runs it, so the CREATE below must succeed and only the CALL may fail.
     */
    @Test
    public void aBadNameInAProcedureBodyFailsAtCallNotAtCreate() {
        engine.execute("CREATE OR REPLACE PROCEDURE bp(x INTEGER) RETURNS INTEGER LANGUAGE SQL"
            + " AS $$ BEGIN RETURN y + 1; END $$");
        refused("CALL bp(4)", "Y");
    }

    /**
     * Names inside an embedded SQL statement belong to the query layer, which resolves them against
     * the tables when the statement runs — this pass must not touch them.
     */
    @Test
    public void namesInsideEmbeddedSqlAreLeftToTheQueryLayer() {
        assertEquals("b", block("DECLARE v INTEGER DEFAULT 2;"
            + " BEGIN RETURN (SELECT nm FROM t WHERE id = :v); END"));
        assertEquals("2", block("BEGIN RETURN (SELECT COUNT(*) FROM t); END"));
        assertEquals("1", block("DECLARE v VARCHAR;"
            + " BEGIN SELECT nm INTO :v FROM t WHERE id = 1; RETURN IFF(v = 'a', 1, 0); END"));
    }

    /**
     * The script-supplied names need no declaration. Both answer NULL outside an exception handler,
     * live-measured, which is exactly what makes them worth asserting: the validator must let a name
     * through that resolves to nothing at run time.
     */
    @Test
    public void scriptSuppliedNamesResolve() {
        assertEquals("null", block("BEGIN RETURN SQLCODE; END"));
        assertEquals("null", block("BEGIN SELECT 1; RETURN SQLROWCOUNT; END"));
    }

    /** An exception declared in the block is a name, and its handler still runs. */
    @Test
    public void aDeclaredExceptionResolves() {
        assertEquals("caught", block("DECLARE e EXCEPTION (-20001, 'boom');"
            + " BEGIN RAISE e; EXCEPTION WHEN e THEN RETURN 'caught'; END"));
    }

    /**
     * The refusal carries the position of the name it names, live-measured. The expected offset is
     * taken from the statement itself rather than counted by hand, so this asserts the RULE.
     *
     * <p>No {@code ExpressionSource} origin is involved: this check walks the parse tree of the
     * statement as submitted, where every token already knows its absolute place.
     */
    @Test
    public void theRefusalCarriesTheIdentifiersPosition() {
        assertRefusedAt("BEGIN RETURN missing_name; END", "missing_name", "MISSING_NAME");
        assertRefusedAt("BEGIN IF (1 = 2) THEN RETURN missing_name; END IF; RETURN 99; END",
            "missing_name", "MISSING_NAME");
        assertRefusedAt("BEGIN RETURN id; END", "id", "ID");
    }

    /**
     * A {@code :name} in a scripting expression names a block variable, so an unknown one is refused
     * exactly like a bare identifier — at the COLON. This used to be ACCEPTED outright: the reference
     * never reached the evaluator, so an unbound name quietly produced a value.
     *
     * <p>A {@code :name} inside EMBEDDED SQL is a different question, answered at run time, and has
     * its own two-wording rule — see BindVariableDmlTest.
     */
    @Test
    public void anUnknownBindNameInAScriptingExpressionIsRefusedAtItsColon() {
        // Echoed as written, not folded: live reports 'nope' here and 'NoPe' for :NoPe, while a
        // bare identifier in the same position is upper-cased.
        assertRefusedAt("BEGIN RETURN :nope; END", ":nope", "nope");
        assertRefusedAt("BEGIN RETURN :NoPe; END", ":NoPe", "NoPe");
        // A subquery inside a scripting expression binds its :names from the block too, and reports
        // them the same way — as written, at the colon. The COLUMN names in there stay the query
        // layer's business, resolved against the tables when the subquery runs.
        assertRefusedAt("BEGIN RETURN (SELECT :nope); END", ":nope", "nope");
        assertRefusedAt("BEGIN RETURN (SELECT nm FROM t WHERE id = :nope); END", ":nope", "nope");
        assertEquals("7", block("DECLARE nope INTEGER DEFAULT 7; BEGIN RETURN :nope; END"));
    }

    private void assertRefusedAt(final String sql, final String token, final String reported) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertEquals("SQL compilation error: error line 1 at position " + sql.indexOf(token)
            + "\ninvalid identifier '" + reported + "'", root.getMessage(), sql);
    }
}
