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
package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The lines the account reports for a window frame's offset that is no literal, in a query a block assigns and in a
 * query used as a value (live-verified). In a query a block assigns - res := (...), a scalar's x := (...), LET res
 * RESULTSET := (...) - an offset of any kind names its two lines and then the token after the name the query's FROM
 * reads first, the query's ')' or the WHERE or LIMIT after the name. In a scalar subquery the subquery's SELECT is
 * refused first; a query whose first item is a call then names that call's '(', and any other query the offset's own
 * two lines. A derived table's and a common table expression's query names the offset's two lines.
 */
public class FrameOffsetAssignedQueryLinesTest extends MeasuredCellsTestSupport {

    /** Offsets in assigned queries, scalar subqueries and their neighbours. */
    @Test
    public void assignedAndValueQueries() {
        engine.execute("CREATE OR REPLACE TABLE ev801 (v INT)");
        assertCells(new String[][] {
            {"DECLARE k INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN :k "
                + "PRECEDING AND CURRENT ROW) FROM ev801); RETURN TABLE(res); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 98 unexpected ':'. | syntax error "
                + "line 1 at position 101 unexpected 'PRECEDING'. | syntax error line 1 at position 138 unexpected "
                + "')'."},
            {"DECLARE k INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN :k "
                + "PRECEDING AND CURRENT ROW) FROM ev801 WHERE v > 0); RETURN TABLE(res); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 98 unexpected ':'. | syntax error "
                + "line 1 at position 101 unexpected 'PRECEDING'. | syntax error line 1 at position 139 unexpected "
                + "'WHERE'."},
            {"DECLARE k INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN :k "
                + "FOLLOWING AND UNBOUNDED FOLLOWING) FROM ev801); RETURN TABLE(res); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 98 unexpected ':'. | syntax error "
                + "line 1 at position 101 unexpected 'FOLLOWING'. | syntax error line 1 at position 125 unexpected "
                + "'FOLLOWING'."},
            {"DECLARE k INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v "
                + "PRECEDING AND CURRENT ROW) FROM ev801); RETURN TABLE(res); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 98 unexpected 'v'. | syntax error "
                + "line 1 at position 110 unexpected 'AND'. | syntax error line 1 at position 137 unexpected ')'."},
            {"DECLARE k INT DEFAULT 1; x INT; BEGIN x := (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN :k PRECEDING AND "
                + "CURRENT ROW) FROM ev801 LIMIT 1); RETURN x; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 88 unexpected ':'. | syntax error "
                + "line 1 at position 91 unexpected 'PRECEDING'. | syntax error line 1 at position 129 unexpected "
                + "'LIMIT'."},
            {"DECLARE k INT DEFAULT 1; BEGIN LET res RESULTSET := (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN :k "
                + "PRECEDING AND CURRENT ROW) FROM ev801); RETURN TABLE(res); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 97 unexpected ':'. | syntax error "
                + "line 1 at position 100 unexpected 'PRECEDING'. | syntax error line 1 at position 137 unexpected "
                + "')'."},
            {"SELECT (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev801 LIMIT 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 18 unexpected '('."},
            {"SELECT (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN 1 + 1 PRECEDING AND CURRENT ROW) FROM ev801 LIMIT 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 18 unexpected '('."},
            {"SELECT (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN :k PRECEDING AND CURRENT ROW) FROM ev801 LIMIT 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 18 unexpected '('."},
            {"SELECT (SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev801 LIMIT 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 55 unexpected 'v'. | syntax error line 1 at position 67 unexpected 'AND'."},
            {"SELECT 1, (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev801 LIMIT 1) "
                + "FROM ev801",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 21 unexpected '('."},
            {"WITH c AS (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev801) SELECT * "
                + "FROM c",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'v'. | syntax error "
                + "line 1 at position 67 unexpected 'AND'."},
            {"SELECT (SELECT MAX(v) OVER (ORDER BY v RANGE BETWEEN v PRECEDING AND CURRENT ROW) FROM ev801 LIMIT 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 18 unexpected '('."},
            {"SELECT (SELECT 1 + SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev801 LIMIT 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 56 unexpected 'v'. | syntax error line 1 at position 68 unexpected 'AND'."},
            {"BEGIN RETURN (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev801 LIMIT "
                + "1); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 26 unexpected ')'."},
        });
    }

    /** Derived tables, a JOIN, binds, sums and assigned queries with a WHERE or a LIMIT. */
    @Test
    public void moreShapes() {
        engine.execute("CREATE OR REPLACE TABLE ev803 (v INT)");
        assertCells(new String[][] {
            {"SELECT * FROM ev803 a JOIN (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) AS "
                + "s FROM ev803) b ON TRUE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 72 unexpected 'v'. | syntax error "
                + "line 1 at position 84 unexpected 'AND'."},
            {"SELECT * FROM (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN :k PRECEDING AND CURRENT ROW) FROM ev803)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected ':'. | syntax error "
                + "line 1 at position 62 unexpected 'PRECEDING'."},
            {"SELECT * FROM (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev803 t1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'v'. | syntax error "
                + "line 1 at position 71 unexpected 'AND'."},
            {"DECLARE k INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v "
                + "PRECEDING AND CURRENT ROW) FROM ev803 WHERE v > 0); RETURN TABLE(res); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 98 unexpected 'v'. | syntax error "
                + "line 1 at position 110 unexpected 'AND'. | syntax error line 1 at position 138 unexpected 'WHERE'."},
            {"DECLARE k INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN 1 + 1 "
                + "PRECEDING AND CURRENT ROW) FROM ev803); RETURN TABLE(res); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 100 unexpected '+'. | syntax error "
                + "line 1 at position 114 unexpected 'AND'. | syntax error line 1 at position 141 unexpected ')'."},
            {"DECLARE k INT DEFAULT 1; x INT; BEGIN x := (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND "
                + "CURRENT ROW) FROM ev803 LIMIT 1); RETURN x; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 88 unexpected 'v'. | syntax error "
                + "line 1 at position 100 unexpected 'AND'. | syntax error line 1 at position 128 unexpected 'LIMIT'."},
            {"DECLARE k INT DEFAULT 1; BEGIN LET res RESULTSET := (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v "
                + "PRECEDING AND CURRENT ROW) FROM ev803); RETURN TABLE(res); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 97 unexpected 'v'. | syntax error "
                + "line 1 at position 109 unexpected 'AND'. | syntax error line 1 at position 136 unexpected ')'."},
            {"SELECT (SELECT -SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev803 LIMIT 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 53 unexpected 'v'. | syntax error line 1 at position 65 unexpected 'AND'."},
            {"SELECT (SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 1 + 1 PRECEDING AND CURRENT ROW) FROM ev803 LIMIT "
                + "1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 57 unexpected '+'. | syntax error line 1 at position 71 unexpected 'AND'."},
            {"SELECT (SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN :k PRECEDING AND CURRENT ROW) FROM ev803 LIMIT 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 55 unexpected ':'. | syntax error line 1 at position 58 unexpected "
                + "'PRECEDING'."},
            {"BEGIN RETURN (SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev803 "
                + "LIMIT 1); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 27 unexpected '('."},
        });
    }
}
