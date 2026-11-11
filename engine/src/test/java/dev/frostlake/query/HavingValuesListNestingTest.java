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
 * A HAVING aggregate over a subquery whose own aggregate reads only an outer column is a nesting of aggregates. A
 * grouped query over a VALUES list, written with an alias and its columns, is refused as one over a table is where
 * no statistics answer it: the column named by the list's alias and typed as the list types it, alone or beside a
 * table, under any HAVING aggregate, a COUNT of the column alone answered. An outer SUM reads the correlated value at
 * the type its own aggregate answers - a MAX's its column's, NUMBER(38,0) included, a SUM's twelve digits wider - and a
 * list's decimal column beside a literal is cast to its type as a table's is not (all live-verified).
 */
public class HavingValuesListNestingTest extends MeasuredCellsTestSupport {

    @Test
    public void aValuesListNestsAsATableWould() {
        engine.execute("CREATE OR REPLACE TABLE g980 (id INT, v INT)");
        engine.execute("INSERT INTO g980 VALUES (5, 50), (6, 60)");
        assertCells(new String[][] {
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM "
                + "g980)) = 7 ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[MAX(CORRELATION(MAX(FZ.ID)))]"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING SUM((SELECT MAX(fz.id) FROM "
                + "g980)) = 14 ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[SUM(CORRELATION(CAST(MAX(FZ.ID) AS NUMBER(1,0))))]"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MIN((SELECT MAX(fz.id) FROM "
                + "g980)) = 7 ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[MIN(CORRELATION(MAX(FZ.ID)))]"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING COUNT((SELECT MIN(fz.id) "
                + "FROM g980)) = 7 ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MIN(FZ.ID)] nested in "
                + "[COUNT(CORRELATION(MIN(FZ.ID)))]"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT MAX(fz.id) + 1 "
                + "FROM g980)) = 7 ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[MAX(CORRELATION(MAX(FZ.ID)) + 1)]"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING SUM((SELECT SUM(fz.id) FROM "
                + "g980)) = 7 ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ.ID AS "
                + "NUMBER(1,0)))] nested in [SUM(CORRELATION(CAST(SUM(CAST(FZ.ID AS NUMBER(1,0))) AS NUMBER(13,0))))]"},
            {"SELECT b FROM (VALUES (5.5, TRUE), (700, FALSE)) fz(id, b) GROUP BY b HAVING SUM((SELECT MAX(fz.id) "
                + "FROM g980)) = 14 ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[SUM(CORRELATION(CAST(MAX(FZ.ID) AS NUMBER(4,1))))]"},
            {"SELECT b FROM (VALUES (5.5, TRUE), (700, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT MAX(fz.id) + "
                + "1 FROM g980)) = 7 ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[MAX(CORRELATION(CAST(MAX(FZ.ID) AS NUMBER(4,1))) + 1)]"},
            {"SELECT b FROM (VALUES (5.5, TRUE), (700, FALSE)) fz(id, b) GROUP BY b HAVING SUM((SELECT SUM(fz.id) "
                + "FROM g980)) = 7 ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ.ID AS "
                + "NUMBER(4,1)))] nested in [SUM(CORRELATION(CAST(SUM(CAST(FZ.ID AS NUMBER(4,1))) AS NUMBER(16,1))))]"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING SUM((SELECT MAX(fz.id) FROM "
                + "g980 WHERE g980.id = 5)) = 14 ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[SUM(CORRELATION(CAST(MAX(FZ.ID) AS NUMBER(1,0))))]"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT SUM(fz.id) FROM "
                + "g980)) = 14 ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ.ID AS "
                + "NUMBER(1,0)))] nested in [MAX(CORRELATION(SUM(CAST(FZ.ID AS NUMBER(1,0)))))]"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, NULL)) fz(id, b) GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM "
                + "g980)) = 7 ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[MAX(CORRELATION(MAX(FZ.ID)))]"},
            {"SELECT b FROM (VALUES (5, 'x'), (7, 'y')) fz(id, b) GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM "
                + "g980)) = 7 ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[MAX(CORRELATION(MAX(FZ.ID)))]"},
            {"SELECT b FROM (VALUES ('5', TRUE), ('7', FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT MAX(fz.id) "
                + "FROM g980)) = '7' ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[MAX(CORRELATION(MAX(FZ.ID)))]"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) AS fz(id, b) GROUP BY b HAVING MAX((SELECT MAX(fz.id) "
                + "FROM g980)) = 7 ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[MAX(CORRELATION(MAX(FZ.ID)))]"},
            {"SELECT fz.b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b), g980 GROUP BY fz.b HAVING MAX((SELECT "
                + "MAX(fz.id) FROM g980)) = 7 ORDER BY 1",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[MAX(CORRELATION(MAX(FZ.ID)))]"},
        });
    }

    @Test
    public void anOuterSumReadsTheValueAtItsAggregatesType() {
        engine.execute("CREATE OR REPLACE TABLE g980 (id INT, v INT)");
        engine.execute("INSERT INTO g980 VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE n980 (id NUMBER(1,0), b BOOLEAN)");
        engine.execute("INSERT INTO n980 VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE d980 (id NUMBER(10,2), b BOOLEAN)");
        engine.execute("INSERT INTO d980 VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE i980 (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO i980 VALUES (5, TRUE), (7, FALSE)");
        assertCells(new String[][] {
            {"SELECT b FROM n980 GROUP BY b HAVING SUM((SELECT MAX(n980.id) FROM g980 WHERE g980.id = 5)) = 14 "
                + "ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(N980.ID)] nested in "
                + "[SUM(CORRELATION(CAST(MAX(N980.ID) AS NUMBER(1,0))))]"},
            {"SELECT b FROM n980 GROUP BY b HAVING MAX((SELECT MAX(n980.id) + 1 FROM g980 WHERE g980.id = 5)) = 8 "
                + "ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(N980.ID)] nested in "
                + "[MAX(CORRELATION(MAX(N980.ID)) + 1)]"},
            {"SELECT b FROM n980 GROUP BY b HAVING SUM((SELECT SUM(n980.id) FROM g980 WHERE g980.id = 5)) = 14 "
                + "ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(N980.ID AS "
                + "NUMBER(1,0)))] nested in [SUM(CORRELATION(CAST(SUM(CAST(N980.ID AS NUMBER(1,0))) AS "
                + "NUMBER(13,0))))]"},
            {"SELECT b FROM n980 GROUP BY b HAVING MAX((SELECT SUM(n980.id) FROM g980 WHERE g980.id = 5)) = 14 "
                + "ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(N980.ID AS "
                + "NUMBER(1,0)))] nested in [MAX(CORRELATION(SUM(CAST(N980.ID AS NUMBER(1,0)))))]"},
            {"SELECT b FROM d980 GROUP BY b HAVING SUM((SELECT MAX(d980.id) FROM g980 WHERE g980.id = 5)) = 14 "
                + "ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(D980.ID)] nested in "
                + "[SUM(CORRELATION(CAST(MAX(D980.ID) AS NUMBER(10,2))))]"},
            {"SELECT b FROM d980 GROUP BY b HAVING SUM((SELECT SUM(d980.id) FROM g980 WHERE g980.id = 5)) = 14 "
                + "ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(D980.ID AS "
                + "NUMBER(10,2)))] nested in [SUM(CORRELATION(CAST(SUM(CAST(D980.ID AS NUMBER(10,2))) AS "
                + "NUMBER(22,2))))]"},
            {"SELECT b FROM i980 GROUP BY b HAVING SUM((SELECT MAX(i980.id) FROM g980 WHERE g980.id = 5)) = 14 "
                + "ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(I980.ID)] nested in "
                + "[SUM(CORRELATION(CAST(MAX(I980.ID) AS NUMBER(38,0))))]"},
            {"SELECT b FROM i980 GROUP BY b HAVING SUM((SELECT SUM(i980.id) FROM g980 WHERE g980.id = 5)) = 14 "
                + "ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(I980.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST(SUM(CAST(I980.ID AS NUMBER(38,0))) AS "
                + "NUMBER(38,0))))]"},
            {"SELECT b FROM i980 GROUP BY b HAVING MAX((SELECT SUM(i980.id) FROM g980 WHERE g980.id = 5)) = 14 "
                + "ORDER BY b",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(I980.ID AS "
                + "NUMBER(38,0)))] nested in [MAX(CORRELATION(SUM(CAST(I980.ID AS NUMBER(38,0)))))]"},
        });
    }
}
