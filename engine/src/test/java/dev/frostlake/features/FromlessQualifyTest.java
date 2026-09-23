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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A FROM-less query evaluates a window function in its QUALIFY over its one row, as it does in its select list,
 * and judges a QUALIFY as a query with a FROM does: an unresolvable column or function in the predicate is
 * named before the clause is refused for holding no window function. Every cell is live-verified.
 */
public class FromlessQualifyTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTable() {
        engine.execute("CREATE TABLE t (x NUMBER)");
    }

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String answer(final String sql) {
        final StringBuilder answer = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (answer.length() > 0) {
                answer.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    answer.append(", ");
                }
                answer.append(row.getValue(i));
            }
        }
        return answer.toString();
    }

    @Test
    public void aWindowInQualifyIsEvaluatedOverTheOneRow() {
        for (final String[] cell : new String[][] {
            {"SELECT 1 AS a QUALIFY ROW_NUMBER() OVER (ORDER BY a) = 1", "1"},
            {"SELECT 1 AS a QUALIFY ROW_NUMBER() OVER (ORDER BY 1) = 1", "1"},
            {"SELECT 1 AS a QUALIFY ROW_NUMBER() OVER (PARTITION BY a ORDER BY a) = 1", "1"},
            {"SELECT 1 AS a QUALIFY ROW_NUMBER() OVER (ORDER BY a) = 2", ""},
            {"SELECT 1 AS a QUALIFY COUNT(*) OVER () = 1", "1"},
            {"SELECT 1 AS a, 2 AS b QUALIFY RANK() OVER (ORDER BY b DESC) = 1", "1, 2"},
            {"SELECT 'x' AS a QUALIFY LAG(a) OVER (ORDER BY a) IS NULL", "x"},
            {"SELECT 1 AS a WHERE TRUE QUALIFY ROW_NUMBER() OVER (ORDER BY a) = 1", "1"},
            {"SELECT 1 AS a WHERE FALSE QUALIFY ROW_NUMBER() OVER (ORDER BY a) = 1", ""},
            {"SELECT ROW_NUMBER() OVER (ORDER BY 1) AS r QUALIFY r = 1", "1"},
            {"SELECT 1 AS a QUALIFY ROW_NUMBER() OVER (ORDER BY a) = 1 ORDER BY a", "1"},
            {"SELECT COUNT(*) AS c QUALIFY ROW_NUMBER() OVER (ORDER BY c) = 1", "1"},
            {"SELECT 1 AS a QUALIFY SUM(a) OVER () = 1", "1"},
            {"SELECT 1 AS a QUALIFY ROW_NUMBER() OVER (ORDER BY a) = 1 LIMIT 1", "1"},
            {"SELECT * FROM (SELECT 1 AS a QUALIFY ROW_NUMBER() OVER (ORDER BY a) = 1)", "1"},
        }) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    @Test
    public void aNameInQualifyIsJudgedBeforeTheClause() {
        for (final String[] cell : new String[][] {
            {"SELECT 1 AS a QUALIFY a = 1", "SQL compilation error: error line 1 at position 14\nfound QUALIFY clause but no window function."},
            {"SELECT 1 AS a QUALIFY missing = 1", "SQL compilation error: error line 1 at position 22\ninvalid identifier 'MISSING'"},
            {"SELECT 1 AS a QUALIFY ROW_NUMBER() OVER (ORDER BY missing) = 1", "SQL compilation error: error line 1 at position 50\ninvalid identifier 'MISSING'"},
            {"SELECT x FROM t QUALIFY missing = 1", "SQL compilation error: error line 1 at position 24\ninvalid identifier 'MISSING'"},
            {"SELECT 1 AS a QUALIFY a = 1 AND missing = 1", "SQL compilation error: error line 1 at position 32\ninvalid identifier 'MISSING'"},
            {"SELECT 1 AS a QUALIFY UPPER(missing) = 'x'", "SQL compilation error: error line 1 at position 28\ninvalid identifier 'MISSING'"},
            {"SELECT x FROM t QUALIFY x = 1", "SQL compilation error: error line 1 at position 16\nfound QUALIFY clause but no window function."},
            {"SELECT 1 AS a QUALIFY nosuch(a) = 1", "SQL compilation error:\nUnknown function NOSUCH."},
            {"SELECT x FROM t QUALIFY nosuch(x) = 1", "SQL compilation error:\nUnknown function NOSUCH."},
            {"SELECT 1 AS a QUALIFY (SELECT missing) = 1", "SQL compilation error: error line 1 at position 30\ninvalid identifier 'MISSING'"},
        }) {
            final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery(cell[0]);
                }
            }, cell[0]);
            assertTrue(String.valueOf(refused.getMessage()).contains(cell[1]),
                cell[0] + " should be refused with [" + cell[1] + "] but read: " + refused.getMessage());
        }
    }
}
