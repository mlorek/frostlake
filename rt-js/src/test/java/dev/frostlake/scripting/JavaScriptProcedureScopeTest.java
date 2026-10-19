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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A JavaScript procedure's body is a frame of its own: a statement it issues binds none of the variables of a block
 * that calls it, so a {@code :name} in it is a bind nothing sets wherever the procedure is called from, and the
 * calling block names the failure at its own CALL (live-verified). How the failure of a statement the handler
 * issued is worded around that sentence is the runtime's own.
 */
public class JavaScriptProcedureScopeTest extends MeasuredCellsTestSupport {

    @Override
    protected void setupTest() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE js_binds_caller() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$
            var rs = snowflake.execute({sqlText: "SELECT :x"}); rs.next(); return rs.getColumnValue(1); $$""");
    }

    @Test
    public void aStatementAHandlerIssuesBindsNoneOfTheCallersVariables() {
        final String alone = answer("CALL js_binds_caller()");
        assertTrue(alone.startsWith("REFUSED ") && alone.contains("Bind variable :x not set."), alone);
        final String called = answer("EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'caller value'; r VARCHAR; "
            + "BEGIN CALL js_binds_caller() INTO :r; RETURN r; END; $$");
        assertTrue(called.startsWith("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : ")
            && called.contains("Bind variable :x not set."), called);
    }
}
