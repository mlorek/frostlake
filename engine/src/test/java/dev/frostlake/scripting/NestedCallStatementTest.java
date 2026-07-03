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
 * CALL of a stored procedure nested inside procedural control flow (IF / WHILE / FOR bodies).
 * Previously such a CALL was a silent no-op; it now executes the procedure — with arguments
 * evaluated in the enclosing procedural scope — via the same multi-language CALL dispatch used at
 * top level. A bare CALL statement discards the callee's return value.
 */
public class NestedCallStatementTest extends BaseDatabaseTest {

    /** A log table plus a SQL procedure that inserts its argument into it. */
    private void createLogger() {
        engine.execute("CREATE TABLE call_log (v VARCHAR)");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE log_it(v VARCHAR) RETURNS VARCHAR LANGUAGE SQL "
            + "AS $$ BEGIN INSERT INTO call_log VALUES (v); RETURN 'logged'; END $$");
    }

    private long logCount() {
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM call_log");
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void callInsideIfBranchExecutes() {
        createLogger();
        engine.executeQuery(
            "BEGIN IF (1 = 1) THEN CALL log_it('taken'); END IF; RETURN 'done'; END");
        assertEquals(1L, logCount());
        final ResultSet rs = engine.executeQuery("SELECT v FROM call_log");
        assertEquals("taken", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void callInsideFalseBranchIsSkipped() {
        createLogger();
        engine.executeQuery(
            "BEGIN IF (1 = 2) THEN CALL log_it('never'); END IF; RETURN 'done'; END");
        assertEquals(0L, logCount());
    }

    @Test
    public void callInsideForLoopRunsPerIterationWithCounter() {
        createLogger();
        engine.executeQuery(
            "BEGIN FOR i IN 1 TO 3 DO CALL log_it(i); END FOR; RETURN 'done'; END");
        assertEquals(3L, logCount());
        final ResultSet rs = engine.executeQuery("SELECT v FROM call_log ORDER BY v");
        assertEquals("1", rs.getRows().get(0).getValue(0));
        assertEquals("2", rs.getRows().get(1).getValue(0));
        assertEquals("3", rs.getRows().get(2).getValue(0));
    }

    @Test
    public void callInsideWhileLoop() {
        createLogger();
        engine.executeQuery(
            "DECLARE i INTEGER DEFAULT 0;"
            + " BEGIN WHILE (i < 2) DO i := i + 1; CALL log_it('w'); END WHILE; RETURN i; END");
        assertEquals(2L, logCount());
    }

    @Test
    public void variableArgumentWithQuoteIsEscaped() {
        createLogger();
        engine.executeQuery(
            "DECLARE msg VARCHAR DEFAULT 'it''s fine';"
            + " BEGIN IF (1 = 1) THEN CALL log_it(:msg); END IF; RETURN 'done'; END");
        assertEquals(1L, logCount());
        final ResultSet rs = engine.executeQuery("SELECT v FROM call_log");
        assertEquals("it's fine", rs.getRows().get(0).getValue(0));
    }

    // The callee RETURNs 'logged', but a bare CALL discards it — the caller's own RETURN must win.
    @Test
    public void calleeReturnDoesNotClobberCallerReturn() {
        createLogger();
        final ResultSet rs = engine.executeQuery(
            "BEGIN IF (1 = 1) THEN CALL log_it('x'); END IF; RETURN 'caller'; END");
        assertEquals(1, rs.getRowCount());
        assertEquals("caller", rs.getRows().get(0).getValue(0));
        assertEquals(1L, logCount());
    }
}
