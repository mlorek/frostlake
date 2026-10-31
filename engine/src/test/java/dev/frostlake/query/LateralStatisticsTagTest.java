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
 * A SQL table function called beside the relation to its left is tagged by the statistics of that relation's columns,
 * whatever rows the function is called for (live-verified): a derived relation that filters rows, a CTE, a view, LIMIT
 * and MAX hand on their base column's statistics, a computed argument its own interval, and a body is typed with each
 * parameter standing for its argument's interval - n % 7, a CASE, n * n.
 */
public class LateralStatisticsTagTest extends MeasuredCellsTestSupport {

    /** Relations to the left that filter the rows the function is called for. */
    @Test
    public void filteredRelations() {
        engine.execute("CREATE OR REPLACE TABLE ti (i INT)");
        engine.execute("INSERT INTO ti VALUES (3), (300)");
        engine.execute("CREATE OR REPLACE TABLE tj (i INT)");
        engine.execute("INSERT INTO tj VALUES (3), (70000)");
        engine.execute("CREATE OR REPLACE TABLE td (d NUMBER(10,2))");
        engine.execute("INSERT INTO td VALUES (1.5), (3000.25)");
        engine.execute("CREATE OR REPLACE FUNCTION t_div(n INT) RETURNS TABLE (a INT) AS 'SELECT n'");
        engine.execute("CREATE OR REPLACE FUNCTION t_mod(n INT) RETURNS TABLE (a INT) AS 'SELECT n % 7'");
        engine.execute("CREATE OR REPLACE FUNCTION t_case(n INT) RETURNS TABLE (a INT) AS 'SELECT CASE WHEN n > 100 "
            + "THEN n ELSE 0 END'");
        engine.execute("CREATE OR REPLACE FUNCTION t_add(n INT, m INT) RETURNS TABLE (a INT) AS 'SELECT n + m'");
        engine.execute("CREATE OR REPLACE FUNCTION t_sq(n INT) RETURNS TABLE (a INT) AS 'SELECT n * n'");
        engine.execute("CREATE OR REPLACE FUNCTION t_from(n INT) RETURNS TABLE (a INT) AS 'SELECT n FROM ti WHERE ti.i "
            + "= 3'");
        engine.execute("CREATE OR REPLACE FUNCTION t_dec(n NUMBER(10,2)) RETURNS TABLE (a NUMBER(10,2)) AS 'SELECT n'");
        engine.execute("CREATE OR REPLACE FUNCTION t_raw(n INT) RETURNS TABLE (a NUMBER) AS 'SELECT n'");
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT * FROM tj WHERE i < 100) s, TABLE(t_div(s.i)) LIMIT 1",
                "NUMBER(38,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT * FROM ti WHERE i < 100) s, TABLE(t_div(s.i * 1000)) LIMIT 1",
                "NUMBER(38,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT * FROM ti WHERE i < 100) s, TABLE(t_case(s.i)) LIMIT 1",
                "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT * FROM ti WHERE i < 100) s, TABLE(t_add(s.i, 5)) LIMIT 1",
                "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT * FROM ti WHERE i < 100) s, TABLE(t_div(5)) LIMIT 1",
                "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT * FROM ti WHERE i < 100) s, TABLE(t_div(s.i)) WHERE s.i < 10 LIMIT "
                + "1",
                "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT i FROM ti WHERE i < 100 LIMIT 1) s, TABLE(t_div(s.i)) LIMIT 1",
                "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT 3 AS i) s, TABLE(t_div(s.i)) LIMIT 1",
                "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT * FROM ti WHERE i < 100) s, TABLE(t_sq(s.i)) LIMIT 1",
                "NUMBER(38,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT MAX(i) AS i FROM ti WHERE i < 100) s, TABLE(t_div(s.i)) LIMIT 1",
                "NUMBER(38,0)[SB2]"},
            {"WITH s AS (SELECT * FROM ti WHERE i < 100) SELECT SYSTEM$TYPEOF(a) FROM s, TABLE(t_div(s.i)) LIMIT 1",
                "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT * FROM ti WHERE i < 100) s, TABLE(t_from(s.i)) LIMIT 1",
                "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT * FROM tj WHERE i < 100) s, TABLE(t_mod(s.i)) LIMIT 1",
                "NUMBER(38,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT * FROM td WHERE d < 100) s, TABLE(t_dec(s.d)) LIMIT 1",
                "NUMBER(10,2)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT * FROM ti WHERE i < 100) s, TABLE(t_raw(s.i)) LIMIT 1",
                "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT * FROM ti WHERE i < 100) s, TABLE(t_add(s.i, s.i)) LIMIT 1",
                "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT * FROM ti WHERE i < 100) s, TABLE(t_div(s.i)) x, TABLE(t_div(x.a)) "
                + "LIMIT 1",
                "REFUSED SQL compilation error: | ambiguous column name 'A'"},
            {"SELECT SYSTEM$TYPEOF(b.a) FROM (SELECT * FROM ti WHERE i < 100) s, TABLE(t_div(s.i)) x, "
                + "TABLE(t_div(x.a)) b LIMIT 1",
                "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT * FROM ti WHERE i < 100) s, TABLE(t_div(s.i)) WHERE a < 10 LIMIT 1",
                "NUMBER(38,0)[SB2]"},
            {"SELECT a FROM (SELECT * FROM ti WHERE i < 100) s, TABLE(t_div(s.i))",
                "3"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT * FROM ti WHERE i > 1000) s, TABLE(t_div(s.i)) LIMIT 1",
                ""},
            {"SELECT SYSTEM$TYPEOF(MAX(a)) FROM (SELECT * FROM ti WHERE i > 1000) s, TABLE(t_div(s.i))",
                ""},
        });
    }

    /** Where the statistics of the relation to the left come from. */
    @Test
    public void statisticsSources() {
        engine.execute("CREATE OR REPLACE TABLE ti (i INT)");
        engine.execute("INSERT INTO ti VALUES (3), (300)");
        engine.execute("CREATE OR REPLACE TABLE tn (i INT)");
        engine.execute("INSERT INTO tn VALUES (3), (NULL), (300)");
        engine.execute("CREATE OR REPLACE VIEW tv AS SELECT * FROM ti");
        engine.execute("CREATE OR REPLACE FUNCTION t_div(n INT) RETURNS TABLE (a INT) AS 'SELECT n'");
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(i) FROM (VALUES (3), (300)) v(i) LIMIT 1",
                "NUMBER(3,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT * FROM tn WHERE i IS NULL) s, TABLE(t_div(s.i)) LIMIT 1",
                "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT * FROM tv WHERE i < 100) s, TABLE(t_div(s.i)) LIMIT 1",
                "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT i FROM ti WHERE i < 100 UNION ALL SELECT 70000) s, "
                + "TABLE(t_div(s.i)) LIMIT 1",
                "NUMBER(38,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT 3 AS i UNION ALL SELECT 300) s, TABLE(t_div(s.i)) WHERE s.i < 100 "
                + "LIMIT 1",
                "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT i FROM ti WHERE i < 100) s, TABLE(t_div(s.i + 0)) LIMIT 1",
                "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT * FROM ti WHERE i < 100) s, TABLE(t_div(IFF(s.i > 0, s.i, 0))) "
                + "LIMIT 1",
                "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT * FROM ti WHERE i < 100) s, TABLE(t_div(n => s.i)) LIMIT 1",
                "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM (SELECT * FROM ti WHERE i < 100) s, LATERAL t_div(s.i) LIMIT 1",
                "NUMBER(38,0)[SB2]"},
        });
    }
}
