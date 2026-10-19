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
 * How deep JavaScript procedures run inside one another, as SQL ones do: sixteen in full; a seventeenth runs and
 * answers as long as it issues no statement, and is ended by the first {@code snowflake.execute} it makes. The
 * sixteenth procedure's own {@code try} catches the refusal - {@code Stored procedure execution error: Stored
 * procedure exceeds the maximum allowed number of recursive calls.} -, and uncaught it is worded once more by
 * every procedure it leaves. Each recursion here ends by its own counter. The frames a JavaScript failure is
 * worded with differ between the account and this engine, so a refusal is asked for its sentence (live-verified).
 */
public class JavaScriptCallDepthTest extends MeasuredCellsTestSupport {

    private static final String REFUSAL = "Stored procedure execution error: Stored procedure exceeds the maximum "
        + "allowed number of recursive calls.";

    private void assertEnded(final String sql) {
        final String answered = answer(sql);
        assertTrue(answered.startsWith("REFUSED ") && answered.contains(REFUSAL), answered);
    }

    @Test
    public void sixteenBodiesRunAndTheNextIsEnded() {
        engine.execute("CREATE OR REPLACE PROCEDURE jrec(n FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ if (N <= 0) "
            + "{ return 0; } var rs = snowflake.execute({sqlText: 'CALL jrec(' + (N - 1) + ')'}); rs.next(); return "
            + "rs.getColumnValue(1) + 1; $$");
        assertAnswer("3.0", "CALL jrec(3)");
        assertAnswer("15.0", "CALL jrec(15)");
        // The seventeenth procedure answers 0 without a statement of its own.
        assertAnswer("16.0", "CALL jrec(16)");
        assertEnded("CALL jrec(20)");
    }

    @Test
    public void theDeepestBodyAnswersAndIssuesNothing() {
        engine.execute("CREATE OR REPLACE PROCEDURE jdeep(n FLOAT, what VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT "
            + "AS $$ if (N > 0) { var rs = snowflake.execute({sqlText: \"CALL jdeep(\" + (N - 1) + \", '\" + WHAT + "
            + "\"')\"}); rs.next(); return rs.getColumnValue(1); } if (WHAT === 'select') { var q = "
            + "snowflake.execute({sqlText: 'SELECT 5'}); q.next(); return 'select ' + q.getColumnValue(1); } return "
            + "'none'; $$");
        assertAnswer("select 5", "CALL jdeep(15, 'select')");
        assertAnswer("none", "CALL jdeep(16, 'none')");
        assertEnded("CALL jdeep(16, 'select')");
    }

    @Test
    public void theSixteenthBodyMayCatchTheRefusal() {
        engine.execute("CREATE OR REPLACE PROCEDURE jcatch(n FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ if (N "
            + ">= 40) { return 'bottom'; } try { var rs = snowflake.execute({sqlText: 'CALL jcatch(' + (N + 1) + "
            + "')'}); rs.next(); return rs.getColumnValue(1); } catch (err) { return N + ': ' + err.message; } $$");
        final String caught = answer("CALL jcatch(1)");
        // The seventeenth procedure's own try never sees it.
        assertTrue(caught.startsWith("16: ") && caught.contains(REFUSAL), caught);
    }
}
