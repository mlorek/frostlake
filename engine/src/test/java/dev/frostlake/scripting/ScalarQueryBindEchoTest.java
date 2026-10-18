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
 * A block's variable bound into the query of one of the block's own expressions — an assignment, a LET, a RETURN
 * — is echoed in an argument-count refusal as the plan converts it to the variable's declared type
 * (live-verified): {@code ABS(TO_NUMBER(:s), 1)} for an INT, {@code TO_NUMBER(:s, 10, 2)} for a NUMBER(10,2),
 * {@code CAST(:s AS FLOAT)} and {@code CAST(:s AS DATE)}, a text as written, and an operand in parentheses.
 */
public class ScalarQueryBindEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void aBindIsEchoedAsItsConversion() {
        engine.execute("CREATE OR REPLACE TABLE tp (k INT, v VARCHAR)");
        engine.execute("INSERT INTO tp VALUES (1, 'a'), (2, 'b')");
        assertCells(new String[][] {
            {"DECLARE s INT DEFAULT 1; x INT; BEGIN x := (SELECT ABS(:s, 1)); RETURN x; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 43 : SQL compilation "
                + "error: error line 1 at position 23 | too many arguments for function [ABS(TO_NUMBER(:s), 1)] "
                + "expected 1, got 2"},
            {"DECLARE s VARCHAR DEFAULT 'a'; x VARCHAR; BEGIN x := (SELECT UPPER(:s, 1)); RETURN x; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 23 | too many arguments for function [UPPER(:s, 1)] expected 1, "
                + "got 2"},
            {"DECLARE s INT DEFAULT 1; x VARCHAR; BEGIN x := (SELECT ABS(:s, 1)); RETURN x; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL compilation "
                + "error: error line 1 at position 23 | too many arguments for function [ABS(TO_NUMBER(:s), 1)] "
                + "expected 1, got 2"},
            {"DECLARE s FLOAT DEFAULT 1.5; x FLOAT; BEGIN x := (SELECT ABS(:s, 1)); RETURN x; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 49 : SQL compilation "
                + "error: error line 1 at position 23 | too many arguments for function [ABS(CAST(:s AS FLOAT), 1)] "
                + "expected 1, got 2"},
            {"DECLARE s DATE DEFAULT '2024-01-15'::DATE; x INT; BEGIN x := (SELECT YEAR(:s, 1)); RETURN x; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL compilation "
                + "error: error line 1 at position 23 | too many arguments for function [YEAR(CAST(:s AS DATE), 1)] "
                + "expected 1, got 2"},
            {"DECLARE s NUMBER(10,2) DEFAULT 1.5; x NUMBER(10,2); BEGIN x := (SELECT ABS(:s, 1)); RETURN x; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL compilation "
                + "error: error line 1 at position 23 | too many arguments for function [ABS(TO_NUMBER(:s, 10, 2), "
                + "1)] expected 1, got 2"},
            {"DECLARE s BOOLEAN DEFAULT TRUE; x BOOLEAN; BEGIN x := (SELECT NOT(:s, 1)); RETURN x; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL compilation "
                + "error: error line 1 at position 23 | Invalid argument types for function 'NOT': (ROW(BOOLEAN, "
                + "NUMBER(1,0)))"},
            {"DECLARE s INT DEFAULT 1; x INT; BEGIN x := (SELECT ABS(:s, :s)); RETURN x; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 43 : SQL compilation "
                + "error: error line 1 at position 23 | too many arguments for function [ABS(TO_NUMBER(:s), "
                + "TO_NUMBER(:s))] expected 1, got 2"},
            {"DECLARE s INT DEFAULT 1; x INT; BEGIN x := (SELECT ABS(:s + 1, 1)); RETURN x; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 43 : SQL compilation "
                + "error: error line 1 at position 23 | too many arguments for function [ABS((TO_NUMBER(:s)) + 1, "
                + "1)] expected 1, got 2"},
            {"DECLARE s INT DEFAULT 1; x INT; BEGIN x := (SELECT ABS(:s, 1) FROM tp); RETURN x; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 43 : SQL compilation "
                + "error: error line 1 at position 23 | too many arguments for function [ABS(TO_NUMBER(:s), 1)] "
                + "expected 1, got 2"},
            {"DECLARE s VARCHAR DEFAULT 'a'; x INT; BEGIN x := (SELECT LENGTH(:s, 1)); RETURN x; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 49 : SQL compilation "
                + "error: error line 1 at position 23 | too many arguments for function [LENGTH(:s, 1)] expected 1, "
                + "got 2"},
            {"DECLARE s INT DEFAULT 1; x INT; BEGIN x := (SELECT ABS(1, :s)); RETURN x; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 43 : SQL compilation "
                + "error: error line 1 at position 23 | too many arguments for function [ABS(1, TO_NUMBER(:s))] "
                + "expected 1, got 2"},
            {"BEGIN LET s INT := 1; LET x INT := (SELECT ABS(:s, 1)); RETURN x; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 35 : SQL compilation "
                + "error: error line 1 at position 23 | too many arguments for function [ABS(TO_NUMBER(:s), 1)] "
                + "expected 1, got 2"},
            {"DECLARE s INT DEFAULT 1; BEGIN RETURN (SELECT ABS(:s, 1)); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: error line 1 at position 15 | too many arguments for function [ABS(TO_NUMBER(:s), 1)] "
                + "expected 1, got 2"},
            {"DECLARE s VARCHAR DEFAULT 'a'; BEGIN RETURN (SELECT UPPER(:s, 1)); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 44 : SQL compilation "
                + "error: error line 1 at position 15 | too many arguments for function [UPPER(:s, 1)] expected 1, "
                + "got 2"},
            {"DECLARE s INT DEFAULT 1; x INT; BEGIN x := (SELECT ABS(ABS(:s), 1)); RETURN x; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 43 : SQL compilation "
                + "error: error line 1 at position 23 | too many arguments for function [ABS(ABS(TO_NUMBER(:s)), 1)] "
                + "expected 1, got 2"},
            {"DECLARE s VARCHAR DEFAULT '1'; x INT; BEGIN x := (SELECT ABS(:s, 1)); RETURN x; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 49 : SQL compilation "
                + "error: error line 1 at position 23 | too many arguments for function [ABS(:s, 1)] expected 1, got "
                + "2"},
            {"DECLARE s INT DEFAULT 1; x INT; BEGIN x := (SELECT ABS(:s, 1, 2)); RETURN x; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 43 : SQL compilation "
                + "error: error line 1 at position 23 | too many arguments for function [ABS(TO_NUMBER(:s), 1, 2)] "
                + "expected 1, got 3"},
        });
    }
}
