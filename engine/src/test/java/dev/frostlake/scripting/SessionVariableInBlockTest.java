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

/**
 * What {@code $name} reads in a block: the session's variable, wherever it is written - a RETURN, an
 * assignment, a LET, a CASE operand, a FOR bound, a condition, a function's argument, a statement, a cursor's
 * and a RESULTSET's query, a dynamic text - whatever variable, parameter, inner LET or loop counter of the block
 * carries the same name; the block's own is read by its bare name or as {@code :name}. With no session variable
 * of the name the block is refused while it compiles - {@code Session variable '$PW' does not exist} - though it
 * declares a variable of that name. The session variables the class sets are unset whatever the assertions
 * make of the statements between. Every answer is live-verified.
 */
public class SessionVariableInBlockTest extends MeasuredCellsTestSupport {

    @Test
    public void dollarNameIsTheSessionsVariable() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "SET pv = 'session'");
            assertAnswer(
                "Statement executed successfully.",
                "SET pn = 2");
            assertAnswer(
                "session",
                "EXECUTE IMMEDIATE $$ DECLARE pv VARCHAR DEFAULT 'local'; BEGIN RETURN $pv; END; $$");
            assertAnswer(
                "session/local",
                "EXECUTE IMMEDIATE $$ DECLARE pv VARCHAR DEFAULT 'local'; BEGIN RETURN $pv || '/' || pv; END; $$");
            assertAnswer(
                "session",
                "EXECUTE IMMEDIATE $$ DECLARE pv VARCHAR DEFAULT 'local'; r VARCHAR; BEGIN SELECT $pv INTO :r; "
                    + "RETURN r; END; $$");
            assertAnswer(
                "read the session",
                "EXECUTE IMMEDIATE $$ DECLARE pv VARCHAR DEFAULT 'local'; BEGIN IF ($pv = 'local') THEN RETURN "
                    + "'read the local'; ELSE RETURN 'read the session'; END IF; END; $$");
            assertAnswer(
                "session",
                "EXECUTE IMMEDIATE $$ DECLARE pv VARCHAR; BEGIN RETURN $pv; END; $$");
            assertAnswer(
                "session",
                "EXECUTE IMMEDIATE $$ DECLARE pv VARCHAR DEFAULT 'local'; BEGIN LET y := $pv; RETURN y; END; $$");
            assertAnswer(
                "session",
                "EXECUTE IMMEDIATE $$ DECLARE pv VARCHAR DEFAULT 'local'; y VARCHAR; BEGIN y := $pv; RETURN y; "
                    + "END; $$");
            assertAnswer(
                "session/local",
                "EXECUTE IMMEDIATE $$ DECLARE pv VARCHAR DEFAULT 'local'; BEGIN RETURN $pv || '/' || :pv; END; $$");
            assertAnswer(
                "session/local",
                "EXECUTE IMMEDIATE $$ DECLARE pv VARCHAR DEFAULT 'local'; r VARCHAR; BEGIN SELECT $pv || '/' || "
                    + ":pv INTO :r; RETURN r; END; $$");
            assertAnswer(
                "session/local",
                "EXECUTE IMMEDIATE $$ DECLARE pv VARCHAR DEFAULT 'local'; BEGIN RETURN (SELECT $pv || '/' || "
                    + ":pv); END; $$");
            assertAnswer(
                "case session",
                "EXECUTE IMMEDIATE $$ DECLARE pv VARCHAR DEFAULT 'local'; BEGIN CASE ($pv) WHEN 'local' THEN "
                    + "RETURN 'case local'; WHEN 'session' THEN RETURN 'case session'; ELSE RETURN 'case other'; END "
                    + "CASE; END; $$");
            assertAnswer(
                "2",
                "EXECUTE IMMEDIATE $$ DECLARE pn NUMBER DEFAULT 5; c NUMBER DEFAULT 0; BEGIN FOR i IN 1 TO $pn DO "
                    + "c := c + 1; END FOR; RETURN c; END; $$");
            assertAnswer(
                "2",
                "EXECUTE IMMEDIATE $$ DECLARE pn NUMBER DEFAULT 5; c NUMBER DEFAULT 0; BEGIN WHILE (c < $pn) DO c "
                    + ":= c + 1; END WHILE; RETURN c; END; $$");
            assertAnswer(
                "session",
                "EXECUTE IMMEDIATE $$ DECLARE pv VARCHAR DEFAULT 'local'; BEGIN EXECUTE IMMEDIATE 'SELECT $pv'; "
                    + "RETURN (SELECT $1 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))); END; $$");
            assertAnswer(
                "session|local",
                "EXECUTE IMMEDIATE $$ DECLARE pv VARCHAR DEFAULT 'local'; BEGIN LET rs RESULTSET := (SELECT $pv "
                    + "AS a, :pv AS b); RETURN TABLE(rs); END; $$");
            assertAnswer(
                "session",
                "EXECUTE IMMEDIATE $$ DECLARE pv VARCHAR DEFAULT 'local'; r VARCHAR; BEGIN LET c CURSOR FOR "
                    + "SELECT $pv; OPEN c; FETCH c INTO r; CLOSE c; RETURN r; END; $$");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 50 | Session variable '$PW' does not "
                    + "exist",
                "EXECUTE IMMEDIATE $$ DECLARE pw VARCHAR DEFAULT 'local'; BEGIN RETURN $pw; END; $$");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 61 | Session variable '$PW' does not "
                    + "exist",
                "EXECUTE IMMEDIATE $$ DECLARE pw VARCHAR DEFAULT 'local'; r VARCHAR; BEGIN SELECT $pw INTO :r; "
                    + "RETURN r; END; $$");
            assertAnswer(
                "session/inner",
                "EXECUTE IMMEDIATE $$ DECLARE pv VARCHAR DEFAULT 'local'; BEGIN BEGIN LET pv := 'inner'; RETURN "
                    + "$pv || '/' || pv; END; END; $$");
            assertAnswer(
                "session/1",
                "EXECUTE IMMEDIATE $$ DECLARE pv VARCHAR DEFAULT 'local'; BEGIN FOR pv IN 1 TO 1 DO RETURN $pv || "
                    + "'/' || pv; END FOR; END; $$");
            assertAnswer(
                "session/local",
                "EXECUTE IMMEDIATE $$ DECLARE pv VARCHAR DEFAULT 'local'; BEGIN RETURN GETVARIABLE('PV') || '/' "
                    + "|| pv; END; $$");
            assertAnswer(
                "SESSION/7",
                "EXECUTE IMMEDIATE $$ DECLARE pv VARCHAR DEFAULT 'local'; BEGIN RETURN UPPER($pv) || '/' || "
                    + "LENGTH($pv); END; $$");
            answer(
                "EXECUTE IMMEDIATE $$ DECLARE pv VARCHAR DEFAULT 'local'; BEGIN RETURN $PV || '/' || $\"PV\"; "
                    + "END; $$");
            assertAnswer(
                "Function VP successfully created.",
                "CREATE OR REPLACE PROCEDURE vp(pv VARCHAR) RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                    + "BEGIN RETURN $pv || '/' || pv; END; $$");
            assertAnswer(
                "session/arg",
                "CALL vp('arg')");
            assertAnswer(
                "Function VQ successfully created.",
                "CREATE OR REPLACE PROCEDURE vq(pv VARCHAR) RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                    + "DECLARE r VARCHAR; BEGIN SELECT $pv || '/' || :pv INTO :r; RETURN r; END; $$");
            assertAnswer(
                "session/arg",
                "CALL vq('arg')");
            assertAnswer(
                "session",
                "EXECUTE IMMEDIATE $$ BEGIN RETURN $pv; END; $$");
        } finally {
            answer(
                "UNSET pv");
            answer(
                "UNSET pn");
        }
    }
}
