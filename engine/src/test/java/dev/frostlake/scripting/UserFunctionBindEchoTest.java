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
 * A block's call of a user's function given the wrong number of arguments echoes each variable the call reads as the
 * bind its query reads (live-verified): {@code TO_NUMBER(:X_9)}, a text bare, {@code CAST(:X_9 AS FLOAT)}, numbered by
 * the literals the block writes and the values its variables are declared with, in the block's order. A LET's own
 * value that is such a call echoes the variables by their names.
 */
public class UserFunctionBindEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void aVariableReadsAsANumberedBind() {
        engine.execute("CREATE OR REPLACE FUNCTION n926(a INT, b INT) RETURNS INT AS 'a + b'");
        assertCells(new String[][] {
            {"BEGIN LET x := 1; RETURN n926(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N926(TO_NUMBER(:X_9), 1, 2)] "
                + "expected 2, got 3"},
            {"BEGIN LET x := 1; RETURN n926(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N926(TO_NUMBER(:X_9), 1, 2)] "
                + "expected 2, got 3"},
            {"BEGIN LET y := 1; LET x := 2; RETURN n926(x, y, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N926(TO_NUMBER(:X_13), "
                + "TO_NUMBER(:Y_9), 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; LET q := 5; RETURN n926(q, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N926(TO_NUMBER(:Q_15), 1, "
                + "2)] expected 2, got 3"},
            {"BEGIN LET x := 1; RETURN n926(x, x, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N926(TO_NUMBER(:X_7), "
                + "TO_NUMBER(:X_7), 2)] expected 2, got 3"},
            {"BEGIN LET abc := 1; RETURN n926(abc, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 27 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N926(TO_NUMBER(:ABC_9), 1, "
                + "2)] expected 2, got 3"},
            {"BEGIN LET s := 'a'; RETURN n926(s, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 27 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N926(:S_9, 1, 2)] expected "
                + "2, got 3"},
            {"BEGIN LET x INT := 1; RETURN n926(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N926(TO_NUMBER(:X_9), 1, 2)] "
                + "expected 2, got 3"},
            {"DECLARE x INT DEFAULT 1; BEGIN RETURN n926(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N926(TO_NUMBER(:X_9), 1, 2)] "
                + "expected 2, got 3"},
            {"BEGIN LET x := 1; LET r := n926(x, 1, 2); RETURN r; END;",
                "REFUSED SQL compilation error: error line 1 at position 27 | too many arguments for function "
                + "[N926(X, 1, 2)] expected 2, got 3"},
        });
    }

    @Test
    public void literalsAndDeclarationsNumberTheBinds() {
        engine.execute("CREATE OR REPLACE FUNCTION n929(a INT, b INT) RETURNS INT AS 'a + b'");
        assertCells(new String[][] {
            {"BEGIN LET x := 1 + 2; RETURN n929(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N929(TO_NUMBER(:X_14), 1, "
                + "2)] expected 2, got 3"},
            {"BEGIN LET s := 'a'; LET x := 1; RETURN n929(x, s, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 39 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N929(TO_NUMBER(:X_13), :S_9, "
                + "2)] expected 2, got 3"},
            {"BEGIN LET x := 1; RETURN n929(x, 1 + 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N929(TO_NUMBER(:X_11), 1 + "
                + "1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; RETURN n929(x, ABS(1), 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N929(TO_NUMBER(:X_9), "
                + "ABS(1), 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; LET y := x; RETURN n929(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N929(TO_NUMBER(:Y_14), 1, "
                + "2)] expected 2, got 3"},
            {"BEGIN LET x := 1; LET y := 2; LET z := 3; RETURN n929(z, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 49 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N929(TO_NUMBER(:Z_21), 1, "
                + "2)] expected 2, got 3"},
            {"BEGIN LET x := 1.5; RETURN n929(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 27 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N929(CAST(:X_9 AS FLOAT), 1, "
                + "2)] expected 2, got 3"},
            {"BEGIN LET x := 1; RETURN n929(x, 1, 2, 3); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N929(TO_NUMBER(:X_11), 1, 2, "
                + "3)] expected 2, got 4"},
            {"BEGIN LET x := 1; IF (TRUE) THEN RETURN n929(x, 1, 2); END IF; RETURN 0; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 40 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N929(TO_NUMBER(:X_13), 1, "
                + "2)] expected 2, got 3"},
            {"BEGIN LET x := 1; LET r := n929(x + 1, 1, 2); RETURN r; END;",
                "REFUSED SQL compilation error: error line 1 at position 27 | too many arguments for function "
                + "[N929(X + 1, 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; RETURN n929(x, 'b', 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N929(TO_NUMBER(:X_9), 'b', "
                + "2)] expected 2, got 3"},
            {"BEGIN LET x := 1; RETURN n929(x); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation "
                + "error: error line 1 at position 7 | Invalid argument types for function 'N929': (NUMBER(38,0))"},
            {"BEGIN LET x := 1; LET s := 'a'; RETURN n929(x, s, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 39 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N929(TO_NUMBER(:X_9), :S_13, "
                + "2)] expected 2, got 3"},
            {"BEGIN LET x := TRUE; RETURN n929(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 28 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N929(CAST(:X_9 AS BOOLEAN), "
                + "1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; LET q := 2; RETURN n929(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N929(TO_NUMBER(:X_11), 1, "
                + "2)] expected 2, got 3"},
        });
    }

    @Test
    public void eachValueShapeDrawsItsOwnCount() {
        engine.execute("CREATE OR REPLACE FUNCTION n930(a INT, b INT) RETURNS INT AS 'a + b'");
        assertCells(new String[][] {
            {"BEGIN LET x := ABS(1); RETURN n930(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N930(TO_NUMBER(:X_11), 1, "
                + "2)] expected 2, got 3"},
            {"BEGIN LET x := 1 + 2 + 3; RETURN n930(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 33 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N930(TO_NUMBER(:X_19), 1, "
                + "2)] expected 2, got 3"},
            {"BEGIN LET x := -1; RETURN n930(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N930(TO_NUMBER(:X_10), 1, "
                + "2)] expected 2, got 3"},
            {"BEGIN LET x := (1); RETURN n930(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 27 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N930(TO_NUMBER(:X_9), 1, 2)] "
                + "expected 2, got 3"},
            {"BEGIN LET y := 1; LET x := y + 1; RETURN n930(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 41 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N930(TO_NUMBER(:X_19), 1, "
                + "2)] expected 2, got 3"},
            {"BEGIN LET x := 'a' || 'b'; RETURN n930(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 34 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N930(:X_14, 1, 2)] expected "
                + "2, got 3"},
            {"BEGIN LET x := 1 * 2; RETURN n930(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N930(TO_NUMBER(:X_14), 1, "
                + "2)] expected 2, got 3"},
            {"BEGIN LET x := 1; x := 2; RETURN n930(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 33 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N930(TO_NUMBER(:X_11), 1, "
                + "2)] expected 2, got 3"},
            {"BEGIN LET x := 1; RETURN n930(x, 1, 2) + 1; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N930(TO_NUMBER(:X_11), 1, "
                + "2)] expected 2, got 3"},
            {"BEGIN LET x DATE := '2024-01-01'::DATE; RETURN n930(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N930(CAST(:X_11 AS DATE), 1, "
                + "2)] expected 2, got 3"},
            {"BEGIN LET x := 1; RETURN n930(x, 2); END;", "3"},
            {"BEGIN LET x := 1; RETURN n930(x, n930(1, 2), 3); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N930(TO_NUMBER(:X_11), "
                + "N930(1, 2), 3)] expected 2, got 3"},
            {"BEGIN LET x := 1 + 2; LET y := 5; RETURN n930(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 41 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N930(TO_NUMBER(:Y_20), 1, "
                + "2)] expected 2, got 3"},
            {"BEGIN LET x := ABS(-1) + 2; RETURN n930(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 35 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [N930(TO_NUMBER(:X_17), 1, "
                + "2)] expected 2, got 3"},
            {"BEGIN LET x := NULL; RETURN n930(x, 1, 2); END;",
                "REFUSED SQL compilation error: error line 1 at position 6 |  variable 'X' cannot have its type "
                + "inferred from initializer"},
        });
    }

    @Test
    public void aBodyWithoutABlockNeedsItsSemicolon() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE PROCEDURE bp930() RETURNS INT LANGUAGE SQL AS 'SELECT 1'",
                "REFUSED SQL compilation error: | syntax error line 3 at position 4 unexpected '<EOF>'."},
            {"CALL bp930()", "REFUSED SQL compilation error: | Unknown function BP930."},
            {"CREATE OR REPLACE PROCEDURE bp930() RETURNS INT LANGUAGE SQL AS 'SELECT 2;'",
                "Function BP930 successfully created."},
            {"CALL bp930()", "null"},
        });
    }
}
