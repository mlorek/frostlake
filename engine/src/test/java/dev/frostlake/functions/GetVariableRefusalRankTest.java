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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * GETVARIABLE's "argument 0 … needs to be constant" is the last refusal of a statement's compilation: an argument
 * count or an argument type anywhere in the statement, a generator's constant argument, a predicate type, the ORDER
 * BY position, an aggregate in WHERE, the grouped select list, a QUALIFY without a window, a subquery's refusal and a
 * later set-operation arm's all come first — while a value's fault computing the rows comes after it. Of two, the
 * one written first speaks (all live-verified).
 */
public class GetVariableRefusalRankTest extends BaseDatabaseTest {

    private static final String REFUSED = "SQL compilation error:\n";
    private static final String NAME_ONE =
        REFUSED + "argument 0 to function GETVARIABLE needs to be constant, found 'CAST(1 AS VARCHAR(134217728))'";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (a INT, b INT)");
        engine.execute("CREATE TABLE rt (n INT, g VARCHAR(5), d DATE)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String at(final int position, final String sentence) {
        return "SQL compilation error: error line 1 at position " + position + "\n" + sentence;
    }

    @Test
    public void anArgumentCountAnywhereComesFirst() {
        assertEquals(at(23, "too many arguments for function [ABS(1, 2)] expected 1, got 2"),
            refusal("SELECT GETVARIABLE(1), ABS(1, 2)"));
        assertEquals(at(23, "too many arguments for function [UPPER(RT.D, 1)] expected 1, got 2"),
            refusal("SELECT GETVARIABLE(n), UPPER(d, 1) FROM RT"));
        assertEquals(at(24, "too many arguments for function [ABS(1, 2)] expected 1, got 2"),
            refusal("SELECT GETVARIABLE(n) + ABS(1, 2) FROM RT"));
        assertEquals(at(30, "too many arguments for function [ABS(1, 2)] expected 1, got 2"),
            refusal("SELECT CONCAT(GETVARIABLE(1), ABS(1, 2))"));
        assertEquals(at(7, "too many arguments for function [ABS((CAST(1 AS VARCHAR(134217728))), 1)] expected 1, got 2"),
            refusal("SELECT ABS(GETVARIABLE(1), 1)"));
        assertEquals(at(23, "too many arguments for function [RANDOM(1, 2)] expected 1, got 2"),
            refusal("SELECT GETVARIABLE(1), RANDOM(1, 2)"));
        assertEquals(at(31, "too many arguments for function [ABS(1, 2)] expected 1, got 2"),
            refusal("SELECT GETVARIABLE(1), (SELECT ABS(1, 2))"));
    }

    @Test
    public void everyOtherCompilationRefusalComesFirst() {
        assertEquals(REFUSED + "argument 1 to function RANDOM needs to be constant, found 'RT.N'",
            refusal("SELECT GETVARIABLE(1), RANDOM(n) FROM RT"));
        assertEquals(REFUSED + "argument 2 to function UNIFORM needs to be constant, found 'RT.N'",
            refusal("SELECT GETVARIABLE(1), UNIFORM(1, n, 2) FROM RT"));
        assertEquals(at(40, "Invalid argument types for function '+': (VARCHAR(1), BOOLEAN)"),
            refusal("SELECT GETVARIABLE(1) FROM RT WHERE 'a' + TRUE = 1"));
        assertEquals(at(27, "Invalid argument types for function '+': (VARCHAR(1), BOOLEAN)"),
            refusal("SELECT GETVARIABLE(1), 'a' + TRUE"));
        assertEquals(REFUSED + "Invalid data type [NUMBER(38,0)] for predicate [RT.N]",
            refusal("SELECT GETVARIABLE(1) FROM RT WHERE n"));
        assertEquals(at(30, "found QUALIFY clause but no window function."), refusal("SELECT GETVARIABLE(1) FROM RT QUALIFY 1 = 1"));
        assertEquals(REFUSED + "Invalid aggregate function in where clause [SUM(RT.N)]",
            refusal("SELECT GETVARIABLE(1), SUM(n) FROM RT WHERE SUM(n) > 1"));
        assertEquals(REFUSED + "[9] is not a valid order by expression", refusal("SELECT GETVARIABLE(1), ABS(n) FROM RT ORDER BY 9"));
        assertEquals(at(19, "'T.A' in select clause is neither an aggregate nor in the group by clause."),
            refusal("SELECT GETVARIABLE(a), a FROM T GROUP BY b"));
        assertEquals(at(40, "found QUALIFY clause but no window function."),
            refusal("SELECT GETVARIABLE(1), (SELECT a FROM T QUALIFY a = 1) FROM T"));
        assertEquals(at(39, "Invalid argument types for function '+': (VARCHAR(1), BOOLEAN)"),
            refusal("SELECT GETVARIABLE(1) UNION SELECT 'x' + TRUE"));
    }

    @Test
    public void itComesBeforeAValuesFaultAndTheFirstWrittenSpeaks() {
        assertEquals(NAME_ONE, refusal("SELECT GETVARIABLE(1), 1 + 'a'::DATE"));
        assertEquals(NAME_ONE, refusal("SELECT 1 + 'a'::DATE, GETVARIABLE(1)"));
        assertEquals(NAME_ONE, refusal("SELECT GETVARIABLE(1), 1/0"));
        assertEquals(NAME_ONE, refusal("SELECT 1/0, GETVARIABLE(1)"));
        assertEquals(NAME_ONE, refusal("SELECT GETVARIABLE(1) FROM T WHERE GETVARIABLE(2) = 'x'"));
        assertEquals(NAME_ONE, refusal("SELECT GETVARIABLE(1) FROM T ORDER BY GETVARIABLE(2)"));
        assertEquals(NAME_ONE, refusal("SELECT GETVARIABLE(1), GETVARIABLE(2)"));
        assertEquals(NAME_ONE, refusal("SELECT GETVARIABLE(1), RANDOM(1) FROM T WHERE a = (SELECT GETVARIABLE(2))"));
        assertEquals(REFUSED + "argument 0 to function GETVARIABLE needs to be constant, found 'CAST(RT.N AS VARCHAR(134217728))'",
            refusal("SELECT GETVARIABLE(n) FROM RT"));
    }
}
