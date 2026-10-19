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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A SQL function's expression body that reads, and holds what no statement takes: a value after a quantifier, OVER
 * after what is no call, a leading {@code !}, a class instance's method. The first two are refused as the body's
 * own compilation failure, {@code Compilation of SQL UDF failed: …}; an unknown function and a function that takes
 * no window as the statement's. A body with LIKE ANY over one pattern, or with {@code !EXISTS}, is created. Every
 * row is live-verified.
 */
public class SqlUdfBodyOperandTest extends BaseDatabaseTest {

    /** The type the function returns, its body, and the answer of its CREATE or the words it is refused in. */
    private static final String[][] CASES = {
            {"BOOLEAN", "x = ANY 1",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nInvalid query block: 1."},
            {"BOOLEAN", "x = ANY 5",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nInvalid query block: 5."},
            {"BOOLEAN", "x = ANY (1)",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nInvalid query block: 1."},
            {"BOOLEAN", "x = ALL 1",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nInvalid query block: 1."},
            {"BOOLEAN", "x > ANY 1",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nInvalid query block: 1."},
            {"BOOLEAN", "x = ANY y",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nInvalid query block: y."},
            {"BOOLEAN", "x = ANY (y)",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nInvalid query block: y."},
            {"BOOLEAN", "x = ANY (x + 1)",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nInvalid query block: +."},
            {"BOOLEAN", "x = ANY (SELECT 1)",
                "Function UB successfully created."},
            {"BOOLEAN", "x = ANY NULL",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nInvalid query block: NULL."},
            {"BOOLEAN", "x = ANY 'a'",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nInvalid query block: 'a'."},
            {"BOOLEAN", "x = ANY",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nsyntax error line 1 at position 8 unexpected ')'."},
            {"BOOLEAN", "x = ANY (1, 2)",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nInvalid query block: (."},
            {"BOOLEAN", "x = ANY ABS(1)",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nInvalid query block: ABS."},
            {"BOOLEAN", "x = ANY 1 + 2",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nInvalid query block: +."},
            {"BOOLEAN", "x = ANY -1",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nInvalid query block: -."},
            {"BOOLEAN", "x = SOME 1",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nInvalid query block: 1."},
            {"INT", "x OVER ()",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\ninvalid function 'X'"},
            {"INT", "y OVER ()",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\ninvalid function 'Y'"},
            {"INT", "x OVER (ORDER BY y)",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\ninvalid function 'X'"},
            {"INT", "1) OVER ();",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\ninvalid function '1'"},
            {"INT", "1 OVER ()",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\ninvalid function '1'"},
            {"INT", "x + 1 OVER ()",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\ninvalid function '1'"},
            {"INT", "(x) OVER ()",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\ninvalid function 'X'"},
            {"INT", "ABS(x) OVER ()",
                "REFUSED SQL compilation error:\nInvalid function type [ABS] for window function."},
            {"INT", "x OVER (",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nsyntax error line 1 at position 10 unexpected '<EOF>'."},
            {"INT", "'a' OVER ()",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\ninvalid function ''a''"},
            {"INT", "NULL OVER ()",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\ninvalid function 'null'"},
            {"INT", "nosuch OVER ()",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\ninvalid function 'NOSUCH'"},
            {"INT", "x OVER () + 1",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\ninvalid function 'X'"},
            {"INT", "x OVER w",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nsyntax error line 1 at position 8 unexpected 'w'."},
            {"INT", "SUM(x) OVER ()",
                "Function UB successfully created."},
            {"INT", "x OVER (PARTITION BY x)",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\ninvalid function 'X'"},
            {"INT", "x !",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nsyntax error line 1 at position 4 unexpected ')'."},
            {"INT", "!x",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"INT", "x !! y",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nsyntax error line 1 at position 4 unexpected '!'."},
            {"INT", "x!y()",
                "REFUSED SQL compilation error:\nUnknown function X!Y."},
            {"INT", "x!y(1)",
                "REFUSED SQL compilation error:\nUnknown function X!Y."},
            {"INT", "!1",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"INT", "! x",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"INT", "x ! = y",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nsyntax error line 1 at position 5 unexpected '='."},
            {"INT", "!(x)",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"INT", "x !y()",
                "REFUSED SQL compilation error:\nUnknown function X!Y."},
            {"INT", "!",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nsyntax error line 1 at position 2 unexpected ')'."},
            {"INT", "1 + !x",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nsyntax error line 1 at position 5 unexpected '!'."},
            {"INT", "NOT !x",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"INT", "x!y() + 1",
                "REFUSED SQL compilation error:\nUnknown function X!Y."},
            {"INT", "!x + 1",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"INT", "!!x",
                "REFUSED SQL compilation error:\nUnknown functions \"!\", \"!\"."},
            {"INT", "x != y",
                "REFUSED Declared return type 'NUMBER(38,0)' is incompatible with actual return type 'BOOLEAN'"},
            {"INT", "x !< y",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nsyntax error line 1 at position 4 unexpected '<'."},
            {"BOOLEAN", "x LIKE ANY 'a'",
                "Function UB successfully created."},
            {"BOOLEAN", "x LIKE ALL y",
                "Function UB successfully created."},
            {"BOOLEAN", "!EXISTS (SELECT 1)",
                "Function UB successfully created."},
            {"BOOLEAN", "x = ANY (x, y)",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nInvalid query block: (."},
            {"BOOLEAN", "x OVER () OVER ()",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\ninvalid function 'X'"},
            {"BOOLEAN", "x!y() OVER ()",
                "REFUSED SQL compilation error:\nInvalid function type [X!Y] for window function."},
            {"BOOLEAN", "!x!y()",
                "REFUSED SQL compilation error:\nUnknown functions X!Y, \"!\"."},
            {"BOOLEAN", "x LIKE ANY",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nsyntax error line 1 at position 11 unexpected ')'."},
            {"BOOLEAN", "x = ANY x!y()",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nInvalid query block: x."},
            {"BOOLEAN", "(x = ANY 1) OVER ()",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error:\nInvalid query block: 1."},
    };

    @Test
    public void aBodyIsRefusedAsAStatementHoldingTheSameIs() {
        for (final String[] one : CASES) {
            String answered;
            try {
                final ResultSet rs = engine.executeQuery("CREATE OR REPLACE FUNCTION ub(x INT, y INT) RETURNS " + one[0]
                    + " AS $$" + one[1] + "$$");
                answered = rs.next() ? String.valueOf(rs.getValue(0)) : "";
            } catch (final RuntimeException refused) {
                answered = "REFUSED " + refused.getMessage();
            }
            if (one[2].startsWith("REFUSED ")) {
                assertTrue(answered.startsWith("REFUSED ") && answered.contains(one[2].substring("REFUSED ".length())),
                    one[1] + " -> " + answered);
            } else {
                assertEquals(one[2], answered, one[1]);
            }
        }
    }
}
