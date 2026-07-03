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
 * CALL of a {@code LANGUAGE SQL} stored procedure actually executes its BEGIN…END body (parameters,
 * control flow, loops) and returns the body's RETURN value. Previously the body's procedural
 * statements were only logged and the call returned NULL.
 */
public class SqlProcedureCallTest extends BaseDatabaseTest {

    private Object callScalar(final String call) {
        final ResultSet rs = engine.executeQuery(call);
        assertEquals(1, rs.getRowCount(), "expected a single-row CALL result");
        return rs.getRows().get(0).getValue(0);
    }

    private long callLong(final String call) {
        return ((Number) callScalar(call)).longValue();
    }

    private String callString(final String call) {
        return String.valueOf(callScalar(call));
    }

    @Test
    public void noParamScalarReturn() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE add_two() RETURNS INTEGER LANGUAGE SQL "
            + "AS $$ BEGIN RETURN 1 + 1; END $$");
        assertEquals(2L, callLong("CALL add_two()"));
    }

    @Test
    public void parameterUsedInReturn() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE double_it(x INTEGER) RETURNS INTEGER LANGUAGE SQL "
            + "AS $$ BEGIN RETURN x * 2; END $$");
        assertEquals(42L, callLong("CALL double_it(21)"));
    }

    @Test
    public void controlFlowInBodySelectsBranch() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE classify(n INTEGER) RETURNS VARCHAR LANGUAGE SQL "
            + "AS $$ BEGIN IF (n > 0) THEN RETURN 'pos'; ELSE RETURN 'neg'; END IF; END $$");
        assertEquals("pos", callString("CALL classify(5)"));
        assertEquals("neg", callString("CALL classify(-3)"));
    }

    @Test
    public void loopInBodyAccumulatesAndReturns() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE sum_to(n INTEGER) RETURNS INTEGER LANGUAGE SQL "
            + "AS $$ DECLARE s INTEGER DEFAULT 0; BEGIN FOR i IN 1 TO n DO s := s + i; END FOR; "
            + "RETURN s; END $$");
        assertEquals(15L, callLong("CALL sum_to(5)"));
    }

    @Test
    public void defaultParameterBoundAndReturned() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE greet(name VARCHAR DEFAULT 'World') RETURNS VARCHAR LANGUAGE SQL "
            + "AS $$ BEGIN RETURN name; END $$");
        assertEquals("World", callString("CALL greet()"));
        assertEquals("Alice", callString("CALL greet('Alice')"));
    }

    @Test
    public void bodyStatementsExecuteAsSideEffect() {
        engine.execute("CREATE TABLE proc_log (v INTEGER)");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE log_and_return(v INTEGER) RETURNS INTEGER LANGUAGE SQL "
            + "AS $$ BEGIN INSERT INTO proc_log VALUES (v); RETURN v; END $$");
        assertEquals(7L, callLong("CALL log_and_return(7)"));
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM proc_log WHERE v = 7");
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }
}
