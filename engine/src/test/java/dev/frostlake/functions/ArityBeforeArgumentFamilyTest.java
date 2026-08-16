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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A call with the wrong number of arguments is refused for the COUNT before any argument's family is
 * judged: ABS(ARRAY_CONSTRUCT(), 1) is "too many arguments", LOG(TRUE) "not enough arguments", for a
 * scalar, an aggregate and a column argument alike. Every cell is live-verified.
 */
public class ArityBeforeArgumentFamilyTest extends BaseDatabaseTest {

    private void assertRefused(final String sql, final String message) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(message), sql + " -> " + refused.getMessage());
    }

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    /** Too many arguments is refused for the count even when an argument is of a refused family. */
    @Test
    public void tooManyArgumentsOutranksTheArgumentFamily() {
        assertRefused("SELECT ROUND(2.5, 0, ARRAY_CONSTRUCT(), NULL)",
            "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [ROUND(2.5, 0, ARRAY_CONSTRUCT(), null)] expected 3, got 4");
        assertRefused("SELECT ABS(ARRAY_CONSTRUCT(), 1)",
            "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [ABS(ARRAY_CONSTRUCT(), 1)] expected 1, got 2");
        assertRefused("SELECT UPPER(ARRAY_CONSTRUCT(), 1)",
            "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [UPPER(ARRAY_CONSTRUCT(), 1)] expected 1, got 2");
        assertRefused("SELECT SUM(ARRAY_CONSTRUCT(), 1)",
            "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [SUM(ARRAY_CONSTRUCT(), 1)] expected 1, got 2");
        assertRefused("SELECT ABS(TRUE, 1)",
            "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [ABS(TRUE, 1)] expected 1, got 2");
        assertRefused("SELECT SQRT(ARRAY_CONSTRUCT(), 1, 2)",
            "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [SQRT(ARRAY_CONSTRUCT(), 1, 2)] expected 1, got 3");
        assertRefused("SELECT LENGTH(ARRAY_CONSTRUCT(), 'x')",
            "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [LENGTH(ARRAY_CONSTRUCT(), 'x')] expected 1, got 2");
        assertRefused("SELECT MAX(ARRAY_CONSTRUCT(), 1)",
            "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [MAX(ARRAY_CONSTRUCT(), 1)] expected 1, got 2");
        assertRefused("SELECT AVG(ARRAY_CONSTRUCT(), 1)",
            "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [AVG(ARRAY_CONSTRUCT(), 1)] expected 1, got 2");
    }

    /** Too few arguments is refused for the count first, too. */
    @Test
    public void tooFewArgumentsOutranksTheArgumentFamily() {
        assertRefused("SELECT LOG(TRUE)",
            "SQL compilation error: error line 1 at position 7\nnot enough arguments for function [LOG(TRUE)], expected 2, got 1");
        assertRefused("SELECT LOG(ARRAY_CONSTRUCT())",
            "SQL compilation error: error line 1 at position 7\nnot enough arguments for function [LOG(ARRAY_CONSTRUCT())], expected 2, got 1");
        assertRefused("SELECT ABS()",
            "SQL compilation error: error line 1 at position 7\nnot enough arguments for function [ABS()], expected 1, got 0");
        assertRefused("SELECT ROUND()",
            "SQL compilation error: error line 1 at position 7\nnot enough arguments for function [ROUND()], expected 1, got 0");
        assertRefused("SELECT SUBSTR(ARRAY_CONSTRUCT())",
            "SQL compilation error: error line 1 at position 7\nnot enough arguments for function [SUBSTR(ARRAY_CONSTRUCT())], expected 2, got 1");
        assertRefused("SELECT LPAD(ARRAY_CONSTRUCT())",
            "SQL compilation error: error line 1 at position 7\nnot enough arguments for function [LPAD(ARRAY_CONSTRUCT())], expected 2, got 1");
    }

    /** With the right count, the family refusal stands; a variadic COUNT takes the extra argument. */
    @Test
    public void theFamilyIsJudgedOnceTheCountIsRight() {
        assertRefused("SELECT ROUND(2.5, 0, NULL, 1)",
            "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [ROUND(2.5, 0, null, 1)] expected 3, got 4");
        assertRefused("SELECT UPPER(ARRAY_CONSTRUCT())",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'UPPER': (ARRAY)");
        assertRefused("SELECT ABS(ARRAY_CONSTRUCT())",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ABS': (ARRAY)");
        assertEquals("1",
            rows("SELECT COUNT(ARRAY_CONSTRUCT(), 1)"));
    }

    /** Over table columns, and for an aggregate, the count is judged first as well. */
    @Test
    public void columnArgumentsAndAggregatesAlike() {
        engine.execute("CREATE OR REPLACE TABLE ta (a ARRAY, n NUMBER)");
        assertRefused("SELECT ABS(a, 1) FROM ta",
            "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [ABS(TA.A, 1)] expected 1, got 2");
        assertRefused("SELECT UPPER(a, n) FROM ta",
            "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [UPPER(TA.A, TA.N)] expected 1, got 2");
        assertRefused("SELECT ROUND(n, 0, a, 1) FROM ta",
            "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [ROUND(TA.N, 0, TA.A, 1)] expected 3, got 4");
        assertRefused("SELECT SUM(a, 1) FROM ta",
            "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [SUM(TA.A, 1)] expected 1, got 2");
        assertRefused("SELECT ABS(a) FROM ta",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ABS': (ARRAY)");
    }
}
