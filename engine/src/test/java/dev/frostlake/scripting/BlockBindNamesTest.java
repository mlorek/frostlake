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
 * Where an anonymous block places the refusal of an expression's argument types, as the account writes the names
 * before the operator out (live-verified). A name is written as a bind, {@code (:x)} rather than {@code (x)}, one
 * column wider, when a nested BEGIN … END block or an exception handler declares it and the expression reads it
 * directly there, or when its declaration shadows a name an enclosing scope declares, wherever it is read; a LET over
 * a name an enclosing DECLARE section declares is written as it stands either way.
 */
public class BlockBindNamesTest extends MeasuredCellsTestSupport {

    /** Each block, run by EXECUTE IMMEDIATE, and what it answers. */
    private static final String[][] BLOCKS = {
            {"BEGIN LET x := 1; BEGIN LET x := TRUE; RETURN x + 1; END; END;",
                refused(46, 14, "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))")},
            {"BEGIN LET x := TRUE; RETURN x + 1; END;",
                refused(28, 13, "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))")},
            {"BEGIN LET x := 1; BEGIN LET y := TRUE; RETURN y + 1; END; END;",
                refused(46, 14, "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))")},
            {"BEGIN LET xy := 1; BEGIN LET xy := TRUE; RETURN xy + 1; END; END;",
                refused(48, 15, "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))")},
            {"DECLARE x INT := 1; BEGIN BEGIN LET x := TRUE; RETURN x + 1; END; END;",
                refused(54, 13, "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))")},
            {"BEGIN LET x := 1; BEGIN LET x := TRUE; RETURN 2 + x + 1; END; END;",
                refused(46, 2, "Invalid argument types for function '+': (NUMBER(1,0), BOOLEAN)")},
            {"BEGIN LET x := 1; BEGIN LET x := TRUE; LET z := x + 1; RETURN z; END; END;",
                "REFUSED SQL compilation error: error line 1 at position 48 | Invalid argument types for function '+': "
                    + "(BOOLEAN, NUMBER(1,0))"},
            {"BEGIN LET x := 1; BEGIN LET x := TRUE; RETURN x + 1; END; RETURN 0; END;",
                refused(46, 14, "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))")},
            {"BEGIN LET x := 1; BEGIN LET x := 'abc'; RETURN x + TRUE; END; END;",
                refused(47, 5, "Invalid argument types for function '+': (VARCHAR(3), BOOLEAN)")},
            {"BEGIN LET x := 1; BEGIN LET x := TRUE; END; RETURN x + TRUE; END;",
                refused(51, 18, "Invalid argument types for function '+': (NUMBER(38,0), BOOLEAN)")},
            {"BEGIN LET x := TRUE; BEGIN LET x := 1; END; RETURN x + 1; END;",
                refused(51, 13, "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))")},
            {"BEGIN LET x := TRUE; BEGIN RETURN x + 1; END; END;",
                refused(34, 13, "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))")},
            {"BEGIN LET x := 1; BEGIN LET x := TRUE; RETURN x + x; END; END;",
                refused(46, 14, "Invalid argument types for function '+': (BOOLEAN, BOOLEAN)")},
            {"BEGIN LET x := 1; LET x := TRUE; RETURN x + 1; END;",
                "REFUSED SQL compilation error: error line 1 at position 18 |  Variable with name 'X' declared twice."},
            {"BEGIN LET x := 1; BEGIN LET x := TRUE; BEGIN RETURN x + 1; END; END; END;",
                refused(52, 14, "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))")},
            {"BEGIN LET x := 1; BEGIN LET x BOOLEAN := TRUE; RETURN x + 1; END; END;",
                refused(54, 14, "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))")},
            {"BEGIN LET x := 1; BEGIN DECLARE x BOOLEAN DEFAULT TRUE; BEGIN RETURN x + 1; END; END; END;",
                refused(69, 14, "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))")},
            {"BEGIN IF (TRUE) THEN LET x := TRUE; RETURN x + 1; END IF; END;",
                refused(43, 13, "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))")},
            {"BEGIN FOR i IN 1 TO 1 DO LET x := TRUE; RETURN x + 1; END FOR; END;",
                refused(47, 13, "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))")},
            {"BEGIN LOOP LET x := TRUE; RETURN x + 1; END LOOP; END;",
                refused(33, 13, "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))")},
            {"BEGIN LET x := 1; IF (TRUE) THEN LET x := TRUE; RETURN x + 1; END IF; END;",
                refused(55, 14, "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))")},
            {"DECLARE x BOOLEAN DEFAULT TRUE; BEGIN RETURN x + 1; END;",
                refused(45, 13, "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))")},
            {"BEGIN BEGIN LET x := TRUE; BEGIN LET y := 1; RETURN x + y + TRUE; END; END; END;",
                refused(52, 13, "Invalid argument types for function '+': (BOOLEAN, NUMBER(38,0))")},
            {"BEGIN LET x := TRUE; BEGIN LET x := x; RETURN x + 1; END; END;",
                refused(46, 14, "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))")},
            {"BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN LET x := TRUE; RETURN x + 1; END;",
                refused(66, 14, "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))")},
            {"BEGIN WHILE (TRUE) DO LET x := TRUE; RETURN x + 1; END WHILE; END;",
                refused(44, 13, "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))")},
            {"BEGIN CASE WHEN TRUE THEN LET x := TRUE; RETURN x + 1; END CASE; END;",
                refused(48, 13, "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))")},
            {"BEGIN BEGIN LET x := TRUE; IF (TRUE) THEN RETURN x + 1; END IF; END; END;",
                refused(49, 13, "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))")},
            {"BEGIN BEGIN LET x := TRUE; LET z := 2; RETURN z + x; END; END;",
                refused(46, 19, "Invalid argument types for function '+': (NUMBER(38,0), BOOLEAN)")},
            {"BEGIN BEGIN LET x := 'abc'; RETURN 1 + x || TRUE; END; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 35 : Numeric value 'abc' "
                    + "is not recognized"},
            {"DECLARE c CURSOR FOR SELECT 1; BEGIN BEGIN LET x := TRUE; RETURN x + 1; END; END;",
                refused(65, 14, "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))")},
            {"BEGIN BEGIN LET x := TRUE; LET y := x + 1; RETURN 0; END; END;",
                "REFUSED SQL compilation error: error line 1 at position 36 | Invalid argument types for function '+': "
                    + "(BOOLEAN, NUMBER(1,0))"},
            {"BEGIN BEGIN LET x := TRUE; RETURN x + (SELECT 1); END; END;",
                refused(34, 21, "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))")},
    };

    /** A block's refusal: the statement's place in the block, then the place in the expression as compiled. */
    private static String refused(final int statement, final int place, final String message) {
        return "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position " + statement
            + " : SQL compilation error: error line 1 at position " + place + " | " + message;
    }

    @Test
    public void aNameIsWrittenAsABindByWhereItIsDeclaredAndRead() {
        for (final String[] block : BLOCKS) {
            assertAnswer(block[1], "EXECUTE IMMEDIATE $$" + block[0] + "$$");
        }
    }
}
