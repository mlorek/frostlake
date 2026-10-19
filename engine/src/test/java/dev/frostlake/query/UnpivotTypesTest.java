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
package dev.frostlake.query;

import static dev.frostlake.query.QueryAnswers.assertCells;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * The columns an UNPIVOT lists must share one type, read in the relation's column order: the first column of
 * another type is refused by name, even a NUMBER of another precision or scale; texts of different widths join in
 * the widest, and the name column is a VARCHAR of no width (live-verified).
 */
public class UnpivotTypesTest extends BaseDatabaseTest {

    private static String conflict(final String column) {
        return "SQL compilation error:|The type of column '" + column
            + "' conflicts with the type of other columns in the UNPIVOT list.";
    }

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE w (id INT, a NUMBER(5,1), b NUMBER(10,3), s VARCHAR(3), t VARCHAR(10), d DATE,"
            + " f FLOAT, i2 INT)");
        engine.execute("INSERT INTO w VALUES (1, 1.5, 2.125, 'abc', 'abcdefghij', '2024-01-01', 1.5e0, 7)");
    }

    @Test
    public void columnsOfAnotherTypeAreRefused() {
        assertCells(engine, new String[][] {
            {"SELECT q, v FROM w UNPIVOT (v FOR q IN (a, b)) ORDER BY q", conflict("B")},
            {"SELECT q, v FROM w UNPIVOT (v FOR q IN (b, a)) ORDER BY q", conflict("B")},
            {"SELECT q, v FROM w UNPIVOT (v FOR q IN (a, d)) ORDER BY q", conflict("D")},
            {"SELECT q, v FROM w UNPIVOT (v FOR q IN (a, s)) ORDER BY q", conflict("S")},
            {"SELECT q, v FROM w UNPIVOT (v FOR q IN (a, f)) ORDER BY q", conflict("F")},
            {"SELECT q, v FROM w UNPIVOT (v FOR q IN (i2, a)) ORDER BY q", conflict("I2")},
            {"SELECT q, v FROM w UNPIVOT (v FOR q IN (d, s)) ORDER BY q", conflict("D")},
            {"SELECT q, v FROM (SELECT 1 AS id, 1 AS x, 2.5 AS y) UNPIVOT (v FOR q IN (x, y)) ORDER BY q",
                conflict("Y")},
            {"CREATE TABLE wu AS SELECT id, q, v FROM w UNPIVOT (v FOR q IN (a, b))", conflict("B")},
        });
    }

    @Test
    public void textsJoinInTheWidest() {
        assertCells(engine, new String[][] {
            {"SELECT q, v, LENGTH(v) FROM w UNPIVOT (v FOR q IN (s, t)) ORDER BY q", "S, abc, 3 | T, abcdefghij, 10"},
            {"SELECT SYSTEM$TYPEOF(v), SYSTEM$TYPEOF(q) FROM w UNPIVOT (v FOR q IN (s, t)) ORDER BY q",
                "VARCHAR(10)[LOB], VARCHAR[LOB] | VARCHAR(10)[LOB], VARCHAR[LOB]"},
            {"SELECT q, v FROM w UNPIVOT (v FOR q IN (i2)) ORDER BY q", "I2, 7"},
        });
    }
}
