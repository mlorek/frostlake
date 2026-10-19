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
 * A range FOR's bounds are judged as the block reaches them (live-verified): a miscounted call in the bound the
 * counter starts from is placed as a value given to a variable — eight columns in, fifteen when the bound
 * calls a user's function — and one in the other bound as an expression of its own, the upper bound being the
 * start of a REVERSE loop; the uncaught exception stands at the bound's first token, a parenthesis too. A
 * subquery's name that resolves to nothing is refused ahead of the types of the expression holding it.
 */
public class ForBoundPlacesTest extends MeasuredCellsTestSupport {

    @Test
    public void aBoundIsPlacedAsTheCounterReadsIt() {
        engine.execute("CREATE OR REPLACE FUNCTION f2() RETURNS INT AS '1'");
        engine.execute("CREATE OR REPLACE FUNCTION n1(a INT, b INT) RETURNS INT AS 'a + b'");
        engine.execute("CREATE OR REPLACE TABLE t1 (x INT, s VARCHAR, c INT)");
        engine.execute("INSERT INTO t1 VALUES (1, 'a', 1)");
        assertCells(new String[][] {
            {"BEGIN FOR i IN 1 TO ABS(1, 2) DO RETURN 1; END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 20 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [ABS(1, 2)] expected 1, got 2"},
            {"BEGIN FOR i IN ABS(1, 2) TO 3 DO RETURN 1; END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 15 : SQL compilation "
                + "error: error line 1 at position 8 | too many arguments for function [ABS(1, 2)] expected 1, got 2"},
            {"BEGIN FOR i IN 1 TO (ABS(1, 2)) DO RETURN 1; END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 20 : SQL compilation "
                + "error: error line 1 at position 1 | too many arguments for function [ABS(1, 2)] expected 1, got 2"},
            {"BEGIN FOR i IN (ABS(1, 2)) TO 3 DO RETURN 1; END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 15 : SQL compilation "
                + "error: error line 1 at position 9 | too many arguments for function [ABS(1, 2)] expected 1, got 2"},
            {"BEGIN FOR i IN 1 TO 1 + ABS(1, 2) DO RETURN 1; END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 20 : SQL compilation "
                + "error: error line 1 at position 4 | too many arguments for function [ABS(1, 2)] expected 1, got 2"},
            {"BEGIN FOR i IN 1 + ABS(1, 2) TO 3 DO RETURN 1; END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 15 : SQL compilation "
                + "error: error line 1 at position 12 | too many arguments for function [ABS(1, 2)] expected 1, got 2"},
            {"BEGIN FOR i IN REVERSE 1 TO ABS(1, 2) DO RETURN 1; END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 28 : SQL compilation "
                + "error: error line 1 at position 8 | too many arguments for function [ABS(1, 2)] expected 1, got 2"},
            {"BEGIN FOR i IN REVERSE ABS(1, 2) TO 3 DO RETURN 1; END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 23 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [ABS(1, 2)] expected 1, got 2"},
            {"BEGIN FOR i IN 1 TO   ABS(1, 2) DO RETURN 1; END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [ABS(1, 2)] expected 1, got 2"},
            {"BEGIN FOR i IN   ABS(1, 2) TO 3 DO RETURN 1; END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 17 : SQL compilation "
                + "error: error line 1 at position 8 | too many arguments for function [ABS(1, 2)] expected 1, got 2"},
            {"BEGIN FOR i IN 1 TO f2(1) DO RETURN 1; END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 20 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [F2(1)] expected 0, got 1"},
            {"BEGIN FOR i IN f2(1) TO 3 DO RETURN 1; END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 15 : SQL compilation "
                + "error: error line 1 at position 15 | too many arguments for function [F2(1)] expected 0, got 1"},
            {"BEGIN FOR i IN 1 TO 'x'::INT DO RETURN 1; END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 20 : Numeric value 'x' "
                + "is not recognized"},
            {"BEGIN FOR i IN 'x'::INT TO 3 DO RETURN 1; END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 15 : Numeric value 'x' "
                + "is not recognized"},
            {"BEGIN RETURN 12 + (1 IN (SELECT nosuchcol FROM t1)); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 26 | invalid identifier 'NOSUCHCOL'"},
            {"BEGIN RETURN 12 + (SELECT nosuchcol FROM t1); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 20 | invalid identifier 'NOSUCHCOL'"},
            {"BEGIN RETURN (SELECT nosuchcol FROM t1) + 12; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 15 | invalid identifier 'NOSUCHCOL'"},
            {"BEGIN RETURN 12 + (1 IN (SELECT 1)); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 10 | Invalid argument types for function '+': (NUMBER(2,0), "
                + "BOOLEAN)"},
        });
    }
}
