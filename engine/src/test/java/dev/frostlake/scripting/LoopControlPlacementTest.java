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
 * Where a BREAK, a CONTINUE and an EXIT may stand. Each stands inside a loop of its own body - a block nested in
 * the loop and a handler of such a block are inside it - or the body is refused when it starts to run, at the
 * word, on a branch never taken and after a RETURN too: {@code Break used outside of loop}, the one sentence
 * for the three words. One that names a label names a loop around it, or is {@code Label 'NOSUCH' not found}. A
 * procedure or a function whose body holds one is created all the same and refused when it is called - as its
 * own compilation error from the session, as the CALL's failure from a block - so a body never ends or skips a
 * loop of the body that called it, nor a loop of a later statement. EXIT is BREAK's other spelling. Every loop
 * here ends by its bounds whatever a BREAK does. Every answer is live-verified.
 */
public class LoopControlPlacementTest extends MeasuredCellsTestSupport {

    @Test
    public void outsideALoopAndWithAnUnknownLabel() {
        assertAnswer(
            "Function BREAKER successfully created.",
            "CREATE OR REPLACE PROCEDURE breaker() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BREAK; END; $$");
        assertAnswer(
            "Function CONTINUER successfully created.",
            "CREATE OR REPLACE PROCEDURE continuer() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN CONTINUE; RETURN "
                + "'after continue'; END; $$");
        assertAnswer(
            "Function FBREAK successfully created.",
            "CREATE OR REPLACE FUNCTION fbreak() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN BREAK; END; $$");
        assertAnswer(
            "a1b1a2b2a3b3",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; BEGIN FOR i IN 1 TO 3 DO s := s || 'a' || i || "
                + "'b' || i; END FOR; RETURN s; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 |  Break used outside of loop",
            "CALL breaker()");
        assertAnswer(
            "a1b1a2b2a3b3",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; BEGIN FOR i IN 1 TO 3 DO s := s || 'a' || i || "
                + "'b' || i; END FOR; RETURN s; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 |  Break used outside of loop",
            "CALL continuer()");
        assertAnswer(
            "a1b1a2b2a3b3",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; BEGIN FOR i IN 1 TO 3 DO s := s || 'a' || i || "
                + "'b' || i; END FOR; RETURN s; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 86 : SQL compilation "
                + "error: error line 1 at position 7 |  Break used outside of loop",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; x NUMBER; BEGIN FOR i IN 1 TO 3 DO s := s || 'a' "
                + "|| i; CALL breaker(); s := s || 'b' || i; END FOR; RETURN s; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 86 : SQL compilation "
                + "error: error line 1 at position 7 |  Break used outside of loop",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; x NUMBER; BEGIN FOR i IN 1 TO 3 DO s := s || 'a' "
                + "|| i; CALL continuer(); s := s || 'b' || i; END FOR; RETURN s; END; $$");
        answer(
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; x NUMBER; BEGIN FOR i IN 1 TO 3 DO s := s || 'a' "
                + "|| i; x := (SELECT fbreak()); s := s || 'b' || i; END FOR; RETURN s; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 86 : SQL compilation "
                + "error: error line 1 at position 6 |  Break used outside of loop",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; x NUMBER; BEGIN FOR i IN 1 TO 3 DO s := s || 'a' "
                + "|| i; EXECUTE IMMEDIATE 'BEGIN BREAK; END;'; s := s || 'b' || i; END FOR; RETURN s; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 86 : SQL compilation "
                + "error: error line 1 at position 6 |  Break used outside of loop",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; x NUMBER; BEGIN FOR i IN 1 TO 3 DO s := s || 'a' "
                + "|| i; EXECUTE IMMEDIATE 'BEGIN CONTINUE; END;'; s := s || 'b' || i; END FOR; RETURN s; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 26 |  Label 'NOSUCH' not found",
            "EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO BREAK nosuch; END FOR; RETURN 'after'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 26 |  Label 'NOSUCH' not found",
            "EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO CONTINUE nosuch; END FOR; RETURN 'after'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 |  Break used outside of loop",
            "EXECUTE IMMEDIATE $$ BEGIN BREAK; RETURN 'after break'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 |  Break used outside of loop",
            "EXECUTE IMMEDIATE $$ BEGIN CONTINUE; RETURN 'after continue'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 22 |  Break used outside of loop",
            "EXECUTE IMMEDIATE $$ BEGIN IF (TRUE) THEN BREAK; END IF; RETURN 'after break in a branch'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 29 |  Break used outside of loop",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN 'never broke'; BREAK; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 23 |  Break used outside of loop",
            "EXECUTE IMMEDIATE $$ BEGIN IF (FALSE) THEN BREAK; END IF; RETURN 'a branch not taken'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 |  Break used outside of loop",
            "CALL breaker()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 |  Break used outside of loop",
            "SELECT fbreak()");
        assertAnswer(
            "Function LABELLED successfully created.",
            "CREATE OR REPLACE PROCEDURE labelled() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BREAK nosuch; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 |  Break used outside of loop",
            "CALL labelled()");
        assertAnswer(
            "a1b1a2b2a3b3",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; BEGIN FOR i IN 1 TO 3 DO s := s || 'a' || i || "
                + "'b' || i; END FOR; RETURN s; END; $$");
    }

    @Test
    public void insideALoop() {
        assertAnswer(
            "a1b1a2",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; x NUMBER; BEGIN FOR i IN 1 TO 3 DO s := s || 'a' "
                + "|| i; IF (i = 2) THEN BREAK; END IF; s := s || 'b' || i; END FOR; RETURN s; END; $$");
        assertAnswer(
            "a1b1a2a3b3",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; x NUMBER; BEGIN FOR i IN 1 TO 3 DO s := s || 'a' "
                + "|| i; IF (i = 2) THEN CONTINUE; END IF; s := s || 'b' || i; END FOR; RETURN s; END; $$");
        assertAnswer(
            "a1b1a2",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; x NUMBER; BEGIN FOR i IN 1 TO 3 DO s := s || 'a' "
                + "|| i; BEGIN IF (i = 2) THEN BREAK; END IF; END; s := s || 'b' || i; END FOR; RETURN s; END; $$");
        assertAnswer(
            "a1",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; x NUMBER; BEGIN FOR i IN 1 TO 3 DO s := s || 'a' "
                + "|| i; BEGIN x := 1/0; EXCEPTION WHEN OTHER THEN BREAK; END; s := s || 'b' || i; END FOR; RETURN "
                + "s; END; $$");
        assertAnswer(
            "11 21 ",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; BEGIN FOR i IN 1 TO 2 DO FOR j IN 1 TO 3 DO IF (j "
                + "= 2) THEN BREAK; END IF; s := s || i || j || ' '; END FOR; END FOR; RETURN s; END; $$");
        assertAnswer(
            "11 ",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; BEGIN FOR i IN 1 TO 2 DO FOR j IN 1 TO 3 DO IF (j "
                + "= 2) THEN BREAK outer; END IF; s := s || i || j || ' '; END FOR inner; END FOR outer; RETURN s; "
                + "END; $$");
        assertAnswer(
            "11 21 ",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; BEGIN FOR i IN 1 TO 2 DO FOR j IN 1 TO 3 DO IF (j "
                + "= 2) THEN CONTINUE outer; END IF; s := s || i || j || ' '; END FOR inner; END FOR outer; RETURN "
                + "s; END; $$");
        assertAnswer(
            "11 21 ",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; BEGIN FOR i IN 1 TO 2 DO FOR j IN 1 TO 3 DO IF (j "
                + "= 2) THEN BREAK inner; END IF; s := s || i || j || ' '; END FOR inner; END FOR outer; RETURN s; "
                + "END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; i NUMBER DEFAULT 0; BEGIN WHILE (i < 3) DO i := i "
                + "+ 1; IF (i = 2) THEN BREAK; END IF; s := s || i; END WHILE; RETURN s; END; $$");
        assertAnswer(
            "13",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; i NUMBER DEFAULT 0; BEGIN REPEAT i := i + 1; IF "
                + "(i = 2) THEN CONTINUE; END IF; s := s || i; UNTIL (i >= 3) END REPEAT; RETURN s; END; $$");
        assertAnswer(
            "a1b1a2",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; x NUMBER; BEGIN FOR i IN 1 TO 3 DO s := s || 'a' "
                + "|| i; IF (i = 2) THEN EXIT; END IF; s := s || 'b' || i; END FOR; RETURN s; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 |  Break used outside of loop",
            "EXECUTE IMMEDIATE $$ BEGIN EXIT; RETURN 'after exit'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 134 |  Break used outside of loop",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; x NUMBER; BEGIN FOR i IN 1 TO 3 DO s := s || i; x "
                + ":= 1/0; END FOR; RETURN s; EXCEPTION WHEN OTHER THEN BREAK; END; $$");
        assertAnswer(
            "123",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; BEGIN FOR i IN 1 TO 3 DO s := s || i; BEGIN "
                + "CONTINUE; END; s := s || 'x'; END FOR; RETURN s; END; $$");
        assertAnswer(
            "11 ",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; BEGIN FOR i IN 1 TO 2 DO FOR j IN 1 TO 3 DO IF (j "
                + "= 2) THEN EXIT outer; END IF; s := s || i || j || ' '; END FOR inner; END FOR outer; RETURN s; "
                + "END; $$");
        answer(
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; BEGIN FOR i IN 1 TO 3 DO EXIT WHEN i = 2; s := s "
                + "|| i; END FOR; RETURN s; END; $$");
        assertAnswer(
            "5",
            "EXECUTE IMMEDIATE $$ DECLARE exit NUMBER DEFAULT 4; BEGIN exit := exit + 1; RETURN exit; END; $$");
        assertAnswer(
            "12",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR DEFAULT ''; BEGIN FOR i IN 1 TO 3 DO s := s || i; IF (i = 2) "
                + "THEN BREAK; END IF; END FOR wrong; RETURN s; END; $$");
    }
}
