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
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSetColumn;
import org.junit.jupiter.api.Test;

/**
 * PIVOT's aggregate takes an alias that ends each pivoted column's name, and UNPIVOT's columns take aliases the
 * name column carries; a pivot value written twice, and two equal value aliases, are refused (live-verified, as
 * documented).
 */
public class PivotAliasTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE s (emp INT, mon VARCHAR, amt NUMBER(10,2))");
        engine.execute("INSERT INTO s VALUES (1, 'JAN', 10), (1, 'JAN', 5), (1, 'FEB', 7)");
        engine.execute("CREATE TABLE m (id INT, jan INT, feb INT)");
        engine.execute("INSERT INTO m VALUES (1, 1, 2)");
    }

    /** The column names a query answers, joined. */
    private String names(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final ResultSetColumn column : engine.executeQuery(sql).getColumns()) {
            out.append(out.length() > 0 ? ", " : "").append(column.getName());
        }
        return out.toString();
    }

    @Test
    public void anAggregateAliasEndsEachPivotedName() {
        assertEquals("EMP, 'JAN_TOTAL', 'FEB_TOTAL'",
            names("SELECT * FROM s PIVOT (SUM(amt) AS total FOR mon IN ('JAN', 'FEB'))"));
        assertEquals("EMP, 'JAN_TOTAL', 'FEB_TOTAL'",
            names("SELECT * FROM s PIVOT (SUM(amt) total FOR mon IN ('JAN', 'FEB'))"));
        assertEquals("EMP, 'JAN_cnt', 'FEB_cnt'",
            names("SELECT * FROM s PIVOT (COUNT(amt) AS \"cnt\" FOR mon IN ('JAN', 'FEB'))"));
        assertEquals("EMP, J_A, 'FEB_A'",
            names("SELECT * FROM s PIVOT (AVG(amt) AS a FOR mon IN ('JAN' AS j, 'FEB'))"));
        assertEquals("EMP, 'FEB_T', 'JAN_T'",
            names("SELECT * FROM s PIVOT (SUM(amt) AS t FOR mon IN (ANY ORDER BY mon))"));
        assertCells(engine, new String[][] {
            {"SELECT * FROM s PIVOT (SUM(amt) AS total FOR mon IN ('JAN', 'FEB'))", "1, 15.00, 7.00"},
            {"SELECT * FROM s PIVOT (AVG(amt) AS a FOR mon IN ('JAN' AS j, 'FEB'))", "1, 7.50000000, 7.00000000"},
        });
    }

    @Test
    public void aRepeatedPivotColumnIsRefused() {
        assertCells(engine, new String[][] {
            {"SELECT * FROM s PIVOT (SUM(amt) FOR mon IN ('JAN', 'JAN'))",
                "SQL compilation error:|The column ''JAN'' was specified multiple times for 'FROM'"},
            {"SELECT * FROM s PIVOT (SUM(amt) FOR mon IN ('JAN' AS x, 'FEB' AS x))",
                "SQL compilation error: error line 1 at position 56|Duplicate column alias 'X'."},
            {"SELECT * FROM s PIVOT (SUM(amt) FOR mon IN ('JAN', 'jan'))", "1, 15.00, null"},
        });
    }

    @Test
    public void unpivotedColumnsCarryTheirAliases() {
        assertCells(engine, new String[][] {
            {"SELECT * FROM m UNPIVOT (v FOR mon IN (jan AS january, feb february)) ORDER BY mon",
                "1, FEBRUARY, 2 | 1, JANUARY, 1"},
            {"SELECT * FROM m UNPIVOT (v FOR mon IN (jan AS january, feb)) ORDER BY mon", "1, FEB, 2 | 1, JANUARY, 1"},
            {"SELECT * FROM m UNPIVOT (v FOR mon IN (jan AS x, feb AS x)) ORDER BY v", "1, X, 1 | 1, X, 2"},
        });
    }
}
