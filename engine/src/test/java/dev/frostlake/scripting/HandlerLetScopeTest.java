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
 * A handler and the names its block's BEGIN section declared with LET. Such a name - a variable, a cursor, a
 * RESULTSET - is not passed to the block's handlers: the block compiles, a handler that never touches the name
 * runs, and reading it fails when the read is reached - in an expression, a condition, a call's argument, a
 * bind of a statement, an OPEN or a FOR over a LET cursor, a RETURN TABLE of a LET RESULTSET, a block nested in
 * the handler - while a read on a branch not taken costs nothing. A handler that assigns the name first, by
 * {@code :=}, a SELECT INTO or in a block of its own, or declares it again, reads its own value; an assignment
 * on a branch not taken, and one the BEGIN section made, change nothing. What ran before the read stays done.
 * The failure ends the body: no handler of it catches the failure, an enclosing block's neither, and the
 * statement that called the body fails and may be caught there. The account reports the read as an internal
 * failure of its own and this engine as a name that resolves to nothing, so the class asserts only that both
 * refuse. A handler of a block nested in the BEGIN section reads the outer block's LET, and a LET of a branch or
 * of a nested block is no name to the handler at all. Every answer is live-verified.
 */
public class HandlerLetScopeTest extends MeasuredCellsTestSupport {

    /** The read is refused on both sides, each in its own words. */
    private void assertRefused(final String sql) {
        final String answered = answer(sql);
        assertTrue(answered.startsWith("REFUSED "), answered);
    }

    /** The body the block called was ended by the read, and the block's own handler caught the CALL's failure. */
    private void assertCaughtByTheCaller(final String sql) {
        final String answered = answer(sql);
        assertTrue(answered.startsWith("caller: "), answered);
    }

    @Test
    public void aHandlerMayNotReadALetOfItsBlock() {
        assertRefused(
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 5; SELECT 1/0; EXCEPTION WHEN OTHER THEN RETURN y; END; $$");
        assertAnswer(
            "5",
            "EXECUTE IMMEDIATE $$ DECLARE y NUMBER DEFAULT 1; BEGIN y := 5; SELECT 1/0; EXCEPTION WHEN OTHER THEN "
                + "RETURN y; END; $$");
        assertAnswer(
            "5",
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 5; RETURN y; EXCEPTION WHEN OTHER THEN RETURN y; END; $$");
        assertAnswer(
            "7",
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 5; SELECT 1/0; EXCEPTION WHEN OTHER THEN LET y := 7; RETURN y; "
                + "END; $$");
        assertAnswer(
            "assigned",
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 5; SELECT 1/0; EXCEPTION WHEN OTHER THEN y := 7; RETURN "
                + "'assigned'; END; $$");
        assertRefused(
            "EXECUTE IMMEDIATE $$ BEGIN LET y NUMBER := 5; SELECT 1/0; EXCEPTION WHEN OTHER THEN RETURN :y; END; "
                + "$$");
        assertRefused(
            "EXECUTE IMMEDIATE $$ DECLARE r NUMBER; BEGIN LET y := 5; SELECT 1/0; EXCEPTION WHEN OTHER THEN "
                + "SELECT :y INTO :r; RETURN r; END; $$");
        assertAnswer(
            "inner handler reads 5",
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 5; BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN RETURN 'inner "
                + "handler reads ' || y; END; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 75 | invalid identifier 'Y'",
            "EXECUTE IMMEDIATE $$ BEGIN BEGIN LET y := 5; SELECT 1/0; END; EXCEPTION WHEN OTHER THEN RETURN y; "
                + "END; $$");
        assertRefused(
            "EXECUTE IMMEDIATE $$ BEGIN LET c CURSOR FOR SELECT 1; SELECT 1/0; EXCEPTION WHEN OTHER THEN OPEN c; "
                + "RETURN 'opened'; END; $$");
        assertRefused(
            "EXECUTE IMMEDIATE $$ BEGIN LET rs RESULTSET := (SELECT 1 AS a); SELECT 1/0; EXCEPTION WHEN OTHER "
                + "THEN RETURN TABLE(rs); END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 80 | invalid identifier 'I'",
            "EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO SELECT 1/0; END FOR; EXCEPTION WHEN OTHER THEN RETURN "
                + "i; END; $$");
        assertAnswer(
            "Function LP successfully created.",
            "CREATE OR REPLACE PROCEDURE lp(p NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN LET y := p + 1; "
                + "SELECT 1/0; EXCEPTION WHEN OTHER THEN RETURN p; END; $$");
        assertAnswer(
            "4",
            "CALL lp(4)");
        assertAnswer(
            "Function LQ successfully created.",
            "CREATE OR REPLACE PROCEDURE lq(p NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN LET y := p + 1; "
                + "SELECT 1/0; EXCEPTION WHEN OTHER THEN RETURN y; END; $$");
        assertRefused(
            "CALL lq(4)");
        assertRefused(
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 5; SELECT 1/0; EXCEPTION WHEN STATEMENT_ERROR CONTINUE THEN LET "
                + "z := y; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 35 |  Variable with name 'Y' declared twice.",
            "EXECUTE IMMEDIATE $$ DECLARE y NUMBER DEFAULT 1; BEGIN LET y := 5; SELECT 1/0; EXCEPTION WHEN OTHER "
                + "THEN RETURN y; END; $$");
        assertRefused(
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'one'); BEGIN LET y := 5; RAISE e1; EXCEPTION "
                + "WHEN e1 THEN RETURN SQLERRM || ' ' || y; END; $$");
        assertAnswer(
            "7",
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 5; SELECT 1/0; EXCEPTION WHEN OTHER THEN y := 7; RETURN y; END; "
                + "$$");
        assertAnswer(
            "no read",
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 5; SELECT 1/0; EXCEPTION WHEN OTHER THEN IF (FALSE) THEN RETURN "
                + "y; END IF; RETURN 'no read'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 87 | invalid identifier 'Y'",
            "EXECUTE IMMEDIATE $$ BEGIN IF (TRUE) THEN LET y := 5; END IF; SELECT 1/0; EXCEPTION WHEN OTHER THEN "
                + "RETURN y; END; $$");
        assertRefused(
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 5; SELECT 1/0; EXCEPTION WHEN OTHER THEN BEGIN RETURN y; END; "
                + "END; $$");
        assertAnswer(
            "handled",
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 5; SELECT 1/0; EXCEPTION WHEN OTHER THEN RETURN 'handled'; END; "
                + "$$");
        assertRefused(
            "EXECUTE IMMEDIATE $$ DECLARE z NUMBER; BEGIN LET y := 5; z := 1/0; EXCEPTION WHEN OTHER THEN RETURN "
                + "SQLERRM || ' ' || y; END; $$");
        assertAnswer(
            "5",
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 5; BEGIN LET w := 6; SELECT 1/0; EXCEPTION WHEN OTHER THEN "
                + "RETURN y; END; END; $$");
    }

    @Test
    public void assigningCatchingAndWhatRanBefore() {
        assertRefused(
            "EXECUTE IMMEDIATE $$ BEGIN BEGIN LET y := 5; SELECT 1/0; EXCEPTION WHEN OTHER THEN RETURN y; END; "
                + "RETURN 'after'; EXCEPTION WHEN OTHER THEN RETURN 'outer: ' || SQLCODE || ' ' || SQLSTATE || ' ' "
                + "|| SQLERRM; END; $$");
        assertAnswer(
            "Function LQ2 successfully created.",
            "CREATE OR REPLACE PROCEDURE lq2(p NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN LET y := p + 1; "
                + "SELECT 1/0; EXCEPTION WHEN OTHER THEN RETURN y; END; $$");
        assertCaughtByTheCaller(
            "EXECUTE IMMEDIATE $$ BEGIN CALL lq2(4); RETURN 'no'; EXCEPTION WHEN OTHER THEN RETURN 'caller: ' || "
                + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertRefused(
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; LET y := 5; RETURN 'no'; EXCEPTION WHEN OTHER THEN RETURN "
                + "NVL(y::VARCHAR, 'null'); END; $$");
        assertRefused(
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 5; SELECT 1/0; EXCEPTION WHEN OTHER THEN IF (y = 5) THEN RETURN "
                + "'five'; END IF; RETURN 'not'; END; $$");
        assertRefused(
            "EXECUTE IMMEDIATE $$ BEGIN LET y := -5; SELECT 1/0; EXCEPTION WHEN OTHER THEN RETURN ABS(y); END; $$");
        assertAnswer(
            "9",
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 5; SELECT 1/0; EXCEPTION WHEN OTHER THEN SELECT 9 INTO :y; "
                + "RETURN y; END; $$");
        assertRefused(
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 5; SELECT 1/0; EXCEPTION WHEN OTHER THEN y := y + 1; RETURN y; "
                + "END; $$");
        assertAnswer(
            "Table HL_T successfully created.",
            "CREATE OR REPLACE TABLE hl_t (n NUMBER)");
        assertRefused(
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 5; INSERT INTO hl_t VALUES (1); SELECT 1/0; EXCEPTION WHEN OTHER "
                + "THEN INSERT INTO hl_t VALUES (2); RETURN y; END; $$");
        assertAnswer(
            "2",
            "SELECT COUNT(*) FROM hl_t");
        assertAnswer(
            "continued 5",
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 5; SELECT 1/0; RETURN 'continued ' || y; EXCEPTION WHEN OTHER "
                + "CONTINUE THEN LET z := 1; END; $$");
        assertRefused(
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 5; SELECT 1/0; EXCEPTION WHEN OTHER THEN IF (FALSE) THEN y := 1; "
                + "END IF; RETURN y; END; $$");
        assertAnswer(
            "7",
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 5; SELECT 1/0; EXCEPTION WHEN OTHER THEN BEGIN y := 7; END; "
                + "RETURN y; END; $$");
        assertRefused(
            "EXECUTE IMMEDIATE $$ BEGIN LET c CURSOR FOR SELECT 1 AS a; SELECT 1/0; EXCEPTION WHEN OTHER THEN FOR "
                + "r IN c DO RETURN r.a; END FOR; RETURN 'none'; END; $$");
        assertAnswer(
            "2",
            "EXECUTE IMMEDIATE $$ BEGIN LET rs RESULTSET := (SELECT 1 AS a); SELECT 1/0; EXCEPTION WHEN OTHER "
                + "THEN rs := (SELECT 2 AS a); RETURN TABLE(rs); END; $$");
        assertRefused(
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 5; LET w := 6; SELECT 1/0; EXCEPTION WHEN OTHER THEN y := 7; "
                + "RETURN y + w; END; $$");
        assertRefused(
            "EXECUTE IMMEDIATE $$ DECLARE d NUMBER DEFAULT 1; BEGIN LET y := 5; SELECT 1/0; EXCEPTION WHEN OTHER "
                + "THEN d := y; RETURN d; END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 5; SELECT 1/0; EXCEPTION WHEN OTHER THEN LET z := 1; RETURN z; "
                + "END; $$");
        assertRefused(
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 5; BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN y := 8; END; "
                + "SELECT 1/0; EXCEPTION WHEN OTHER THEN RETURN y; END; $$");
        assertRefused(
            "EXECUTE IMMEDIATE $$ DECLARE y NUMBER DEFAULT 1; BEGIN BEGIN LET y := 5; SELECT 1/0; EXCEPTION WHEN "
                + "OTHER THEN RETURN y; END; END; $$");
        assertAnswer(
            "no read",
            "EXECUTE IMMEDIATE $$ DECLARE y NUMBER DEFAULT 1; BEGIN BEGIN LET y := 5; SELECT 1/0; EXCEPTION WHEN "
                + "OTHER THEN RETURN 'no read'; END; END; $$");
    }
}
