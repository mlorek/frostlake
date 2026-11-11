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

import org.junit.jupiter.api.Test;

/**
 * A lateral SQL table function is typed by the statistics of the relation to its left (live-verified): a stored
 * table's column and a constant, a union of constants too, carry an interval; a VALUES list, a window's column, a
 * table function's column and a union with a VALUES list carry none, and the function is tagged by its declared width.
 * A column whose values are drawn anew for each row - SEQ4, SEQ8, a RANDOM - leaves the call no plan: Unsupported
 * subquery type cannot be evaluated.
 */
public class LateralSqlFunctionLeftRelationsTest extends MeasuredCellsTestSupport {

    @Test
    public void theRelationToTheLeftDecides() {
        engine.execute("CREATE OR REPLACE FUNCTION t_div932(n INT) RETURNS TABLE (a INT) AS 'SELECT n'");
        engine.execute("CREATE OR REPLACE TABLE ld932 (i INT)");
        engine.execute("INSERT INTO ld932 VALUES (3), (300)");
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT SEQ4() AS i FROM TABLE(GENERATOR(ROWCOUNT => 3))) s, "
                + "TABLE(t_div932(s.i)) LIMIT 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT 1 AS i FROM TABLE(GENERATOR(ROWCOUNT => 3))) s, "
                + "TABLE(t_div932(s.i)) LIMIT 1",
                "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT SEQ4() AS i FROM ld932) s, TABLE(t_div932(s.i)) LIMIT 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (VALUES (3), (300)) v(i), TABLE(t_div932(v.i)) LIMIT 1",
                "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT ROW_NUMBER() OVER (ORDER BY i) AS i FROM ld932) s, "
                + "TABLE(t_div932(s.i)) LIMIT 1",
                "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT UNIFORM(1, 10, RANDOM()) AS i FROM ld932) s, "
                + "TABLE(t_div932(s.i)) LIMIT 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT SYSTEM$TYPEOF(a) FROM ld932 s, TABLE(t_div932(s.i)) LIMIT 1", "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT i FROM ld932) s, TABLE(t_div932(s.i)) LIMIT 1", "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT value::INT AS i FROM TABLE(FLATTEN(INPUT => "
                + "ARRAY_CONSTRUCT(3, 300)))) s, TABLE(t_div932(s.i)) LIMIT 1",
                "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT SEQ4() AS i FROM TABLE(GENERATOR(ROWCOUNT => 3))) s, "
                + "TABLE(t_div932(s.i + 0)) LIMIT 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT column1 AS i FROM VALUES (3), (300) UNION ALL SELECT i FROM "
                + "ld932) s, TABLE(t_div932(s.i)) LIMIT 1",
                "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT i FROM ld932 UNION ALL SELECT column1 FROM VALUES (3), "
                + "(300)) s, TABLE(t_div932(s.i)) LIMIT 1",
                "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM TABLE(GENERATOR(ROWCOUNT => 3)) g, (SELECT 5 AS i) s, "
                + "TABLE(t_div932(s.i)) LIMIT 1",
                "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT SEQ8() AS i FROM TABLE(GENERATOR(ROWCOUNT => 3))) s, "
                + "TABLE(t_div932(s.i)) LIMIT 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT 3 AS i UNION ALL SELECT 300) s, TABLE(t_div932(s.i)) LIMIT 1",
                "NUMBER(38,0)[SB2]"},
        });
    }
}
