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
 * How far an exception declaration reaches. It belongs to its block: a block nested in that one may declare the
 * name again for itself, and after it the outer block's RAISE raises the outer exception; a called procedure, a
 * dynamic block and a scripting function declare for themselves and change nothing of their caller's. A handler
 * catches the DECLARATION its block sees: an exception of the same name that a nested block, a called
 * procedure or a dynamic block declared and let out is another one, caught by OTHER - and by STATEMENT_ERROR
 * when a statement let it out. A handler that names an exception its block does not see is {@code invalid
 * identifier 'E2'} while the block compiles. Every answer is live-verified.
 */
public class ExceptionDeclarationScopeTest extends MeasuredCellsTestSupport {

    @Test
    public void aDeclarationBelongsToItsBlock() {
        assertAnswer(
            "-20001 outer",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'outer'); BEGIN DECLARE e1 EXCEPTION (-20002, "
                + "'inner'); BEGIN NULL; END; RAISE e1; EXCEPTION WHEN e1 THEN RETURN SQLCODE || ' ' || SQLERRM; "
                + "END; $$");
        assertAnswer(
            "Function EXC_P successfully created.",
            "CREATE OR REPLACE PROCEDURE exc_p() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE e1 EXCEPTION (-20002, "
                + "'callee message'); BEGIN RETURN 'ok'; END; $$");
        assertAnswer(
            "-20001 caller message",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'caller message'); BEGIN CALL exc_p(); RAISE e1; "
                + "EXCEPTION WHEN e1 THEN RETURN SQLCODE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "-20001 outer",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'outer'); BEGIN EXECUTE IMMEDIATE 'DECLARE e1 "
                + "EXCEPTION (-20009, ''dynamic''); BEGIN RETURN 1; END;'; RAISE e1; EXCEPTION WHEN e1 THEN RETURN "
                + "SQLCODE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "Function SFU_EXC successfully created.",
            "CREATE OR REPLACE FUNCTION sfu_exc(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE e EXCEPTION "
                + "(-20077, 'udf exc'); BEGIN IF (n > 1) THEN RAISE e; END IF; RETURN n; EXCEPTION WHEN e THEN "
                + "RETURN -1; END; $$");
        assertAnswer(
            "-20001 block exc r=-1",
            "EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'block exc'); r NUMBER; BEGIN r := (SELECT "
                + "sfu_exc(5)); RAISE e; EXCEPTION WHEN e THEN RETURN SQLCODE || ' ' || SQLERRM || ' r=' || r; END; "
                + "$$");
        assertAnswer(
            "other -20002 inner",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'outer'); BEGIN DECLARE e1 EXCEPTION (-20002, "
                + "'inner'); BEGIN RAISE e1; END; EXCEPTION WHEN e1 THEN RETURN 'outer e1 ' || SQLCODE; WHEN OTHER "
                + "THEN RETURN 'other ' || SQLCODE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 123 | invalid identifier 'E2'",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'outer'); BEGIN DECLARE e2 EXCEPTION (-20002, "
                + "'inner'); BEGIN RAISE e2; END; EXCEPTION WHEN e2 THEN RETURN 'outer names e2 ' || SQLCODE; WHEN "
                + "OTHER THEN RETURN 'other ' || SQLCODE; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 70 | invalid identifier 'E2'",
            "EXECUTE IMMEDIATE $$ BEGIN DECLARE e2 EXCEPTION (-20002, 'inner'); BEGIN NULL; END; RAISE e2; "
                + "EXCEPTION WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "inner handler -20001",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'outer'); BEGIN BEGIN RAISE e1; EXCEPTION WHEN e1 "
                + "THEN RETURN 'inner handler ' || SQLCODE; END; EXCEPTION WHEN e1 THEN RETURN 'outer e1 ' || "
                + "SQLCODE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 |  Variable with name 'E1' declared twice.",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'one'); e1 EXCEPTION (-20002, 'two'); BEGIN RAISE "
                + "e1; EXCEPTION WHEN e1 THEN RETURN 'outer e1 ' || SQLCODE; WHEN OTHER THEN RETURN 'other ' || "
                + "SQLCODE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "as e2 two",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'one'); e2 EXCEPTION (-20001, 'two'); BEGIN RAISE "
                + "e2; EXCEPTION WHEN e1 THEN RETURN 'as e1 ' || SQLERRM; WHEN e2 THEN RETURN 'as e2 ' || SQLERRM; "
                + "END; $$");
        assertAnswer(
            "Function EXC_R successfully created.",
            "CREATE OR REPLACE PROCEDURE exc_r() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE e1 EXCEPTION (-20002, "
                + "'callee message'); BEGIN RAISE e1; END; $$");
        assertAnswer(
            "other -20002 Uncaught exception of type 'E1' on line 1 at position 56 : callee message",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'caller message'); BEGIN CALL exc_r(); RETURN "
                + "'no'; EXCEPTION WHEN e1 THEN RETURN 'outer e1 ' || SQLCODE; WHEN OTHER THEN RETURN 'other ' || "
                + "SQLCODE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "other -20009 Uncaught exception of type 'E1' on line 1 at position 48 : dynamic",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'outer'); BEGIN EXECUTE IMMEDIATE 'DECLARE e1 "
                + "EXCEPTION (-20009, ''dynamic''); BEGIN RAISE e1; END;'; RETURN 'no'; EXCEPTION WHEN e1 THEN "
                + "RETURN 'outer e1 ' || SQLCODE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLERRM; END; "
                + "$$");
        assertAnswer(
            "statement -20002 Uncaught exception of type 'E1' on line 1 at position 56 : callee message",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'caller message'); BEGIN CALL exc_r(); RETURN "
                + "'no'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLERRM; WHEN "
                + "OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "null",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION; BEGIN RAISE e1; EXCEPTION WHEN e1 THEN RETURN SQLCODE || "
                + "' ' || SQLERRM; END; $$");
        assertAnswer(
            "-20001 one",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20001, 'one'); e1 NUMBER; BEGIN RAISE e1; EXCEPTION WHEN "
                + "OTHER THEN RETURN SQLCODE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 9 |  Invalid error code '-19,999'. Must be "
                + "between -20,999 and -20,000",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-19999, 'low'); BEGIN RAISE e1; EXCEPTION WHEN OTHER THEN "
                + "RETURN SQLCODE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "inner caught",
            "EXECUTE IMMEDIATE $$ BEGIN LET n := 1; DECLARE e3 EXCEPTION (-20003, 'three'); BEGIN RAISE e3; "
                + "EXCEPTION WHEN e3 THEN RETURN 'inner caught'; END; END; $$");
    }
}
