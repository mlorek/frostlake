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

/**
 * Snowflake-Scripting loop-control and cursor-scope behavior that was previously untested (and, for
 * two cases, defective):
 *
 * <ul>
 *   <li>CONTINUE must skip only the rest of the current iteration; its signal must not leak past the
 *       loop and abort the enclosing block (so a RETURN after the loop still runs). This is verified
 *       across WHILE, FOR-range, and REPEAT.</li>
 *   <li>RETURN inside a loop exits the loop and the block immediately.</li>
 *   <li>A procedural cursor is scoped to its execution: a cursor name can be re-declared by a later
 *       top-level statement on the same session without a spurious "Cursor already declared" error.</li>
 *   <li>A DECLAREd exception is caught by its named WHEN handler, and WHEN OTHER catches a runtime
 *       error.</li>
 * </ul>
 */
public class LoopContinueAndCursorScopeTest extends BaseDatabaseTest {

    /** Execute an anonymous block and return its RETURNed value as a long. */
    private long retLong(final String block) {
        final ResultSet rs = engine.executeQuery(block);
        assertEquals(1, rs.getRowCount());
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    /** Execute an anonymous block and return its RETURNed value as a string. */
    private String retString(final String block) {
        final ResultSet rs = engine.executeQuery(block);
        assertEquals(1, rs.getRowCount());
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    // ── CONTINUE does not escape the loop (regression: the flag used to abort the enclosing block, so
    //    the RETURN after the loop never ran and the block produced no row) ──────────────────────────

    @Test
    public void continueInWhileLoopSkipsIteration() {
        assertEquals(6L, retLong(
            "DECLARE i INTEGER DEFAULT 0; s INTEGER DEFAULT 0;"
            + " BEGIN WHILE (i < 5) DO i := i + 1; IF (MOD(i,2) = 1) THEN CONTINUE; END IF;"
            + " s := s + i; END WHILE; RETURN s; END"));
    }

    @Test
    public void continueInForRangeSkipsIteration() {
        assertEquals(6L, retLong(
            "DECLARE s INTEGER DEFAULT 0;"
            + " BEGIN FOR i IN 1 TO 5 DO IF (MOD(i,2) = 1) THEN CONTINUE; END IF;"
            + " s := s + i; END FOR; RETURN s; END"));
    }

    @Test
    public void continueInRepeatLoopSkipsIteration() {
        assertEquals(6L, retLong(
            "DECLARE i INTEGER DEFAULT 0; s INTEGER DEFAULT 0;"
            + " BEGIN REPEAT i := i + 1; IF (MOD(i,2) = 1) THEN CONTINUE; END IF;"
            + " s := s + i; UNTIL (i >= 5) END REPEAT; RETURN s; END"));
    }

    // ── RETURN exits a loop (and the block) immediately ────────────────────────────────────────────

    @Test
    public void returnExitsWhileLoopEarly() {
        assertEquals(3L, retLong(
            "DECLARE i INTEGER DEFAULT 0;"
            + " BEGIN WHILE (i < 10) DO i := i + 1; IF (i = 3) THEN RETURN i; END IF; END WHILE;"
            + " RETURN -1; END"));
    }

    @Test
    public void returnExitsForLoopEarly() {
        assertEquals(4L, retLong(
            "BEGIN FOR i IN 1 TO 10 DO IF (i = 4) THEN RETURN i; END IF; END FOR; RETURN -1; END"));
    }

    // ── Cursor scope: a cursor name is reusable across separate top-level statements ────────────────

    @Test
    public void cursorForLoopReusableAcrossStatements() {
        engine.execute("CREATE TABLE cscope (n INTEGER)");
        engine.execute("INSERT INTO cscope VALUES (1),(2),(3)");
        final String block =
            "DECLARE s INTEGER DEFAULT 0; c CURSOR FOR SELECT n FROM cscope;"
            + " BEGIN FOR r IN c DO s := s + r.n; END FOR; RETURN s; END";
        assertEquals(6L, retLong(block));
        // Re-running the same block re-declares cursor c; it must not fail "already declared".
        assertEquals(6L, retLong(block));
    }

    @Test
    public void cursorOpenFetchCloseReusableAcrossStatements() {
        engine.execute("CREATE TABLE cvals (n INTEGER)");
        engine.execute("INSERT INTO cvals VALUES (10),(20)");
        final String block =
            "DECLARE v INTEGER; c CURSOR FOR SELECT n FROM cvals ORDER BY n;"
            + " BEGIN OPEN c; FETCH c INTO v; CLOSE c; RETURN v; END";
        assertEquals(10L, retLong(block));
        assertEquals(10L, retLong(block));
    }

    // ── Exception handling: named handler catches a DECLAREd exception; OTHER catches a runtime error ─

    @Test
    public void userDefinedExceptionCaughtByNamedHandler() {
        assertEquals("caught", retString(
            "DECLARE e EXCEPTION (-20001, 'boom'); BEGIN RAISE e;"
            + " EXCEPTION WHEN e THEN RETURN 'caught'; END"));
    }

    @Test
    public void whenOtherCatchesRuntimeError() {
        assertEquals("handled", retString(
            "BEGIN RETURN 1 / 0; EXCEPTION WHEN OTHER THEN RETURN 'handled'; END"));
    }
}
