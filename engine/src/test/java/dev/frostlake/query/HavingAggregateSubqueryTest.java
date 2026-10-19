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
 * A scalar subquery under a MAX, MIN, SUM, COUNT or ANY_VALUE of a HAVING clause, DISTINCT or not, that reads the
 * grouped relation is answered group by group where it has no aggregate of its own and reads the outer row only in
 * WHERE conditions comparing its columns with outer columns, the aggregate standing under comparisons, arithmetic,
 * AND, OR or a COALESCE; one with an aggregate of its own is refused, {@code Aggregate functions cannot be nested},
 * naming the inner call nested in the outer one as the account prints them, and a shape no decorrelation takes is
 * refused as an unsupported subquery. An aggregate under an OR whose other side is a constant TRUE is never planned,
 * the condition settled for every group (all live-verified).
 */
public class HavingAggregateSubqueryTest extends MeasuredCellsTestSupport {

    @Test
    public void aCorrelatedSubqueryUnderAHavingAggregate() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
        answer("SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g)) > 0 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(CORRELATION(FZ.ID) "
                + "+ G.V)] nested in [MAX(MAX(CORRELATION(FZ.ID) + G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(fz.id + g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(g.v) FROM g WHERE fz.b)) > 0 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(g.v) FROM g WHERE g.id < MAX(fz.id))) > 0");
        answer("SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(fz.b::INT + g.v) FROM g)) > 0 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 41",
            "SELECT id FROM fz GROUP BY id HAVING SUM((SELECT g.v FROM g WHERE g.id = fz.id)) IS NULL ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 15",
            "SELECT id, MAX((SELECT g.v FROM g WHERE g.id = fz.id)) AS m FROM fz GROUP BY id ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 41",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id + 1)) > 0 ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING COUNT((SELECT g.v FROM g WHERE g.id = fz.id)) = 1 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [COUNT(*)] nested in "
                + "[MAX(COUNT(*))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT COUNT(*) FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
        answer("SELECT b FROM fz GROUP BY b HAVING MIN((SELECT MAX(fz.id) FROM g)) > 5 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 41",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id LIMIT 1)) > 0 ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id >= fz.id)) > 0 ORDER BY id");
    }

    @Test
    public void anAggregateNestedThroughASubqueryIsRefusedNamingBothCalls() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE g2 (id INT, v INT)");
        engine.execute("INSERT INTO g2 VALUES (5, 50), (5, 51)");
        engine.execute("CREATE OR REPLACE TABLE fz2 (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz2 VALUES (5, TRUE), (5, FALSE), (7, FALSE)");
        assertAnswer("7",
            "SELECT id FROM fz GROUP BY id HAVING COUNT((SELECT g.v FROM g WHERE g.id = fz.id)) = 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 41",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) IS NULL ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MIN((SELECT g.v FROM g WHERE g.id = fz.id)) IS NOT NULL ORDER BY id");
        assertAnswer("7",
            "SELECT id FROM fz GROUP BY id HAVING COALESCE(MAX((SELECT g.v FROM g WHERE g.id = fz.id)), 0) = 0 ORDER "
                + "BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING SUM((SELECT g.v FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 41",
            "SELECT id FROM fz GROUP BY id HAVING AVG((SELECT g.v FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0 OR TRUE ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0 AND COUNT(*) > 0 "
                + "ORDER BY id");
        answer("SELECT id FROM fz GROUP BY id HAVING NOT (MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0) ORDER BY "
            + "id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) = 50 ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) + 1 > 0 ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0 AND MIN((SELECT "
                + "g.v FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 47",
            "SELECT id, b FROM fz GROUP BY id, b HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id AND fz.b)) > 0 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 41",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g2.v FROM g2 WHERE g2.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("",
            "SELECT id FROM fz2 GROUP BY id HAVING SUM((SELECT g.v FROM g WHERE g.id = fz2.id)) > 60 ORDER BY id");
        assertAnswer("",
            "SELECT id FROM fz2 GROUP BY id HAVING COUNT((SELECT g.v FROM g WHERE g.id = fz2.id)) = 2 ORDER BY id");
        answer("SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g)) = 7 ORDER BY b");
        answer("SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MIN(fz.id) FROM g)) = 5 ORDER BY b");
        answer("SELECT b FROM fz GROUP BY b HAVING MAX((SELECT COUNT(fz.id) FROM g)) = 2 ORDER BY b");
        answer("SELECT b FROM fz GROUP BY b HAVING MAX((SELECT COUNT(fz.id) FROM g)) = 1 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 14",
            "SELECT b, MAX((SELECT MAX(fz.id) FROM g)) FROM fz GROUP BY b ORDER BY b");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 36",
            "SELECT b FROM fz GROUP BY b HAVING (SELECT MAX(fz.id) FROM g) = 7 ORDER BY b");
        answer("SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(fz.id) + 1 FROM g)) = 8 ORDER BY b");
        answer("SELECT b FROM fz WHERE id < 7 GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g)) = 5 ORDER BY b");
        answer("SELECT b FROM fz WHERE id < 7 GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g)) = 7 ORDER BY b");
        answer("SELECT b FROM fz GROUP BY b HAVING SUM((SELECT MAX(fz.id) FROM g)) = 7 ORDER BY b");
        answer("SELECT b FROM fz GROUP BY b HAVING SUM((SELECT MAX(fz.id) FROM g)) = 14 ORDER BY b");
        answer("SELECT b FROM fz2 GROUP BY b HAVING SUM((SELECT MAX(fz2.id) FROM g)) = 14 ORDER BY b");
        answer("SELECT b FROM fz2 GROUP BY b HAVING SUM((SELECT MAX(fz2.id) FROM g)) = 7 ORDER BY b");
        answer("SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g WHERE g.id > 100)) IS NULL ORDER BY "
            + "b");
        answer("SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g WHERE g.id = 5)) = 7 ORDER BY b");
        answer("SELECT b FROM fz GROUP BY b HAVING MAX((SELECT SUM(fz.id) FROM g)) = 12 ORDER BY b");
        answer("SELECT b FROM fz GROUP BY b HAVING MAX((SELECT SUM(fz.id) FROM g)) = 24 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(G.V AS "
                + "NUMBER(38,0)))] nested in [MAX(SCALED_ROUND_INT_DIVIDE(CAST(SUM(CAST(G.V AS NUMBER(38,0))) AS "
                + "NUMBER(38,0)), CAST(COUNT(G.V) AS NUMBER(18,0))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT AVG(g.v) FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 41",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v + fz.id FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 41",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT fz.id FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 41",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT fz.id FROM g WHERE g.id = 5)) > 0 ORDER BY id");
        assertAnswer("5 ;; 7", "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT fz.id)) > 0 ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id ORDER BY g.v)) > 0 ORDER "
                + "BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT DISTINCT g.v FROM g WHERE g.id = fz.id)) > 0 ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v) FROM g WHERE g.id = fz.id GROUP BY g.id)) > 0 "
                + "ORDER BY id");
        answer("SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g WHERE g.v > fz.id)) > 0 ORDER BY b");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v) FROM g WHERE g.id = 5)) > 0 ORDER BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = 5)) > 0 ORDER BY id");
    }

    @Test
    public void aSubqueryReadingTheGroupIsAnsweredPerGroup() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE fz2 (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz2 VALUES (5, TRUE), (5, FALSE), (7, FALSE)");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id >= fz.id)) = 60 ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MIN((SELECT g.v FROM g WHERE g.id >= fz.id)) = 50 ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING COUNT((SELECT g.v FROM g WHERE g.id >= fz.id)) = 2 ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING SUM((SELECT g.v FROM g WHERE g.id >= fz.id)) = 110 ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz2 GROUP BY id HAVING COUNT((SELECT g.v FROM g WHERE g.id >= fz2.id)) = 2 ORDER BY id");
        assertAnswer("",
            "SELECT id FROM fz2 GROUP BY id HAVING COUNT((SELECT g.v FROM g WHERE g.id >= fz2.id)) = 4 ORDER BY id");
        assertAnswer("5|true",
            "SELECT id, b FROM fz GROUP BY id, b HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("5|true",
            "SELECT id, b FROM fz GROUP BY id, b HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id AND g.v > fz.id)) "
                + "> 0 ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE fz.id = g.id)) > 0 ORDER BY id");
        assertAnswer("5|1",
            "SELECT id, COUNT(*) FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0 ORDER BY "
                + "id");
        assertAnswer("5",
            "SELECT id FROM fz WHERE b GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("5",
            "SELECT f.id FROM fz f GROUP BY f.id HAVING MAX((SELECT g.v FROM g WHERE g.id = f.id)) > 0 ORDER BY 1");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) IS NOT NULL ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) BETWEEN 1 AND 100 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 41",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) IN (50, 60) ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING (MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0) = TRUE ORDER BY "
                + "id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id <> fz.id)) > 0 ORDER BY id");
        assertAnswer("7",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id < fz.id)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[SUM(CAST(MAX(G.V) AS NUMBER(38,0)))]",
            "SELECT id FROM fz GROUP BY id HAVING SUM((SELECT MAX(g.v) FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MIN(G.V)] nested in "
                + "[MIN(MIN(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MIN((SELECT MIN(g.v) FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [COUNT(*)] nested in "
                + "[COUNT(COUNT(*))]",
            "SELECT id FROM fz GROUP BY id HAVING COUNT((SELECT COUNT(*) FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(G.V AS "
                + "NUMBER(38,0)))] nested in [MAX(SUM(CAST(G.V AS NUMBER(38,0))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT SUM(g.v) FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX((MAX(G.V)) + 1)]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v) + 1 FROM g WHERE g.id = fz.id)) > 0 ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(v) FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v) FROM g WHERE g.id = fz.id AND g.v > 0)) > 0 "
                + "ORDER BY id");
        assertAnswer("5 ;; 7", "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v) FROM g)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v) FROM g WHERE g.id > fz.id)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [COUNT(G.V)] nested in "
                + "[MAX(COUNT(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT COUNT(g.v) FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v) FROM g WHERE g.id = fz.id GROUP BY g.v)) > 0 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(G.V AS "
                + "NUMBER(38,0)))] nested in [MAX(SCALED_ROUND_INT_DIVIDE(CAST(SUM(CAST(G.V AS NUMBER(38,0))) AS "
                + "NUMBER(38,0)), CAST(COUNT(G.V) AS NUMBER(18,0))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT AVG(g.v) FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V * 2)] nested "
                + "in [MAX(MAX(G.V * 2))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v * 2) FROM g WHERE g.id = fz.id)) > 0 ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [ANY_VALUE(G.V)] nested "
                + "in [MAX(ANY_VALUE(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT ANY_VALUE(g.v) FROM g WHERE g.id = fz.id)) > 0 ORDER "
                + "BY id");
    }

    @Test
    public void aPerGroupSubqueryUnderAnExpressionOrACall() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE gs (id INT, s VARCHAR)");
        engine.execute("INSERT INTO gs VALUES (5, 'a'), (6, 'b')");
        answer("SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id) + 1) > 0 ORDER BY id");
        answer("SELECT id FROM fz GROUP BY id HAVING MAX(COALESCE((SELECT g.v FROM g WHERE g.id = fz.id), 0)) >= 0 "
            + "ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING ANY_VALUE((SELECT g.v FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX(DISTINCT (SELECT g.v FROM g WHERE g.id = fz.id)) > 0 ORDER BY "
                + "id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING COUNT(DISTINCT (SELECT g.v FROM g WHERE g.id >= fz.id)) = 2 ORDER "
                + "BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 98",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0 ORDER BY "
                + "MAX((SELECT g.v FROM g WHERE g.id = fz.id))");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT gs.s FROM gs WHERE gs.id = fz.id)) = 'a' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 93",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0 AND MAX((SELECT "
                + "g.v FROM g WHERE g.id = fz.id + 0)) > 0 ORDER BY id");
        assertAnswer("5",
            "SELECT fz.id FROM fz JOIN g h ON h.id = fz.id GROUP BY fz.id HAVING MAX((SELECT g.v FROM g WHERE g.id = "
                + "fz.id)) > 0 ORDER BY 1");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g JOIN g h ON h.id = g.id WHERE g.id = "
                + "fz.id)) > 0 ORDER BY id");
        assertAnswer("5",
            "SELECT fz.id FROM fz GROUP BY fz.id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0 ORDER BY 1");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING SUM((SELECT g.v FROM g WHERE g.id = fz.id)) IS NOT NULL ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 41",
            "SELECT id FROM fz GROUP BY id HAVING MIN((SELECT g.v FROM g WHERE g.id = fz.id)) IS NULL ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 46",
            "SELECT id FROM fz GROUP BY id HAVING NOT (MIN((SELECT g.v FROM g WHERE g.id = fz.id)) IS NULL) ORDER BY "
                + "id");
        assertAnswer("",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) <> 50 ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) >= 50 ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING 0 < MAX((SELECT g.v FROM g WHERE g.id = fz.id)) ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) * 2 = 100 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(X.V)] nested in "
                + "[MAX(MAX(X.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(x.v) FROM g x WHERE x.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MIN(G.V)] nested in "
                + "[MAX((MIN(G.V)) - 1)]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MIN(g.v) - 1 FROM g WHERE g.id = fz.id)) > 0 ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [COUNT(*)] nested in "
                + "[SUM(CAST(COUNT(*) AS NUMBER(18,0)))]",
            "SELECT id FROM fz GROUP BY id HAVING SUM((SELECT COUNT(*) FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[COUNT(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING COUNT((SELECT MAX(g.v) FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MIN(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MIN((SELECT MAX(g.v) FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[ANY_VALUE(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING ANY_VALUE((SELECT MAX(g.v) FROM g WHERE g.id = fz.id)) > 0 ORDER "
                + "BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(GS.S)] nested in "
                + "[MAX(MAX(GS.S))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(gs.s) FROM gs WHERE gs.id = fz.id)) = 'a' ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(G.V AS "
                + "NUMBER(38,0)))] nested in [SUM(SUM(CAST(G.V AS NUMBER(38,0))))]",
            "SELECT id FROM fz GROUP BY id HAVING SUM((SELECT SUM(g.v) FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v) FROM g WHERE g.id = fz.id)) IS NULL ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(2 * (MAX(G.V)))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT 2 * MAX(g.v) FROM g WHERE g.id = fz.id)) > 0 ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V - "
                + "CORRELATION(FZ.ID))] nested in [MAX(MAX(G.V - CORRELATION(FZ.ID)))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v - fz.id) FROM g WHERE g.id = fz.id)) > 0 ORDER "
                + "BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [COUNT(DISTINCT G.V)] "
                + "nested in [MAX(COUNT(DISTINCT G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT COUNT(DISTINCT g.v) FROM g WHERE g.id = fz.id)) > 0 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v) AS m FROM g WHERE g.id = fz.id)) > 0 ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v) FROM g WHERE g.id = fz.id)) > 0 AND "
                + "MIN((SELECT MIN(g.v) FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
    }

    @Test
    public void anOrSettledTrueLeavesTheSubqueryUnplanned() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) > 0 OR TRUE ORDER "
                + "BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz GROUP BY id HAVING TRUE OR MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) > 0 ORDER "
                + "BY id");
        assertAnswer("false ;; true",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(g.v) FROM g WHERE fz.b)) > 0 OR TRUE ORDER BY b");
        assertAnswer("false ;; true",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(g.v) FROM g WHERE g.id < MAX(fz.id))) > 0 OR TRUE "
                + "ORDER BY b");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz GROUP BY id HAVING (MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) > 0 OR TRUE) AND "
                + "id > 0 ORDER BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) > 0 OR 1 = 1 ORDER "
                + "BY id");
        answer("SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) > 0 OR id > 0 "
            + "ORDER BY id");
        assertAnswer("false ;; true",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(g.v) FROM g WHERE g.id < fz.id)) > 0 OR TRUE ORDER "
                + "BY b");
        assertAnswer("false ;; true",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0 OR TRUE ORDER BY b");
        assertAnswer("REFUSED SQL compilation error: | [FZ.B] is not a valid group by expression",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(g.v) FROM g WHERE g.id < fz.id AND fz.b)) > 0 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) > 0 OR NULL ORDER "
                + "BY id");
        assertAnswer("",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) > 0 AND FALSE ORDER "
                + "BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz GROUP BY id HAVING (MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) > 0) OR (TRUE) "
                + "ORDER BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) > 0 OR TRUE OR id > "
                + "0 ORDER BY id");
    }

    @Test
    public void anOrNotSettledStillRefusesTheNesting() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) IS NULL OR id > 100 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) = 60 OR id > 100 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) > 55 OR id < 6 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING (MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) = 60 OR id > 100) "
                + "AND id > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) = 60 OR FALSE ORDER "
                + "BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) = 60 OR 1 = 0 ORDER "
                + "BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) = 60 AND TRUE ORDER "
                + "BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING NOT (MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) = 60 OR id > "
                + "100) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) = 60 OR MAX(id) > "
                + "100 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) = 60 OR (SELECT "
                + "COUNT(*) FROM g) > 100 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING COALESCE(MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) = 60, "
                + "FALSE) OR id > 100 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) = 60 OR id > 100 OR "
                + "MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) = 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(g.v) FROM g WHERE fz.b)) = 60 OR b IS NULL ORDER BY b");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[SUM(CAST(MAX(G.V) AS NUMBER(38,0)))]",
            "SELECT id FROM fz GROUP BY id HAVING SUM((SELECT MAX(v) FROM g WHERE g.id < fz.id)) = 60 OR id > 100 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[COUNT(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING COUNT((SELECT MAX(v) FROM g WHERE g.id < fz.id)) = 1 OR id > 100 "
                + "ORDER BY id");
    }
}
