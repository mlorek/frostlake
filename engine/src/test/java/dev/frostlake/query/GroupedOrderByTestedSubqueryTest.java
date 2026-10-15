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
 * A grouped or DISTINCT query's ORDER BY holds no subquery, whatever reads it. A key that tests one - with
 * EXISTS, IN, or a quantified comparison - is refused as a key that reads one as a value is, the subquery
 * re-printed from its plan: {@code [EXISTS(SELECT 1 AS "1" FROM G AS G)] is not a valid order by expression},
 * and an IN or a quantified comparison by its quantifier alone, {@code [ANY(SELECT G.ID AS "ID" FROM G AS G)]},
 * {@code ALL} for NOT IN. An aggregate of the query around it is one correlated value there,
 * {@code CORRELATION(MAX(FZ.ID))}, with the rewrites the plan makes of it. The keys are judged in the order
 * written, a subquery an aggregate takes as its argument is no key of its own, and the same subquery named by
 * the select list's alias stays a legal key. Every answer is live-verified.
 */
public class GroupedOrderByTestedSubqueryTest extends MeasuredCellsTestSupport {

    @Test
    public void aKeyThatTestsASubquery() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR, f FLOAT, d NUMBER(10,2), dt DATE, va "
                + "VARIANT, n5 NUMBER(5,0), n38 NUMBER(38,10), ts TIMESTAMP_NTZ, n20 NUMBER(20,0), n21 NUMBER(21,0), "
                + "n109 NUMBER(10,9), n3837 NUMBER(38,37), n242 NUMBER(24,2), n232 NUMBER(23,2))");
        answer(
            "INSERT INTO fz SELECT 5, TRUE, 'a', 1.5, 2.25, '2020-01-01'::DATE, PARSE_JSON('1'), 5, 1.5, "
                + "'2020-01-01'::TIMESTAMP_NTZ, 5, 5, 1.5, 1.5, 1.5, 1.5");
        answer(
            "INSERT INTO fz SELECT 7, FALSE, 'b', 2.5, 3.25, '2020-01-02'::DATE, PARSE_JSON('2'), 7, 2.5, "
                + "'2020-01-02'::TIMESTAMP_NTZ, 7, 7, 2.5, 2.5, 2.5, 2.5");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
        answer(
            "INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\" FROM G AS "
                + "G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT MAX(fz.id) FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM G AS G)] is not a valid order by "
                + "expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM G AS G WHERE G.ID = "
                + "CORRELATION(FZ.ID))] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM g WHERE g.id = fz.id), id");
        answer(
            "SELECT id FROM fz GROUP BY id ORDER BY NOT EXISTS (SELECT MAX(fz.id) FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [ANY(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\" FROM G AS G)] "
                + "is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY id IN (SELECT MAX(fz.id) FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [ANY(SELECT G.ID AS \"ID\" FROM G AS G)] is not a valid order by "
                + "expression",
            "SELECT id FROM fz GROUP BY id ORDER BY id IN (SELECT id FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [ALL(SELECT G.ID AS \"ID\" FROM G AS G)] is not a valid order by "
                + "expression",
            "SELECT id FROM fz GROUP BY id ORDER BY id NOT IN (SELECT id FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\" FROM G AS "
                + "G)] is not a valid order by expression",
            "SELECT DISTINCT id FROM fz ORDER BY EXISTS (SELECT MAX(fz.id) FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM G AS G)] is not a valid order by "
                + "expression",
            "SELECT DISTINCT id FROM fz ORDER BY EXISTS (SELECT 1 FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [ANY(SELECT G.ID AS \"ID\" FROM G AS G)] is not a valid order by "
                + "expression",
            "SELECT DISTINCT id FROM fz ORDER BY id IN (SELECT id FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\" FROM G AS G)] is "
                + "not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY (SELECT MAX(fz.id) FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)] is not a valid "
                + "order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY (SELECT 1), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [(SELECT MAX(G.ID) AS \"MAX(ID)\" FROM G AS G)] is not a valid "
                + "order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY (SELECT MAX(id) FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\" FROM G AS "
                + "G WHERE G.ID > 1)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id > 1), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\", MIN(G.ID) "
                + "AS \"MIN(G.ID)\" FROM G AS G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT MAX(fz.id), MIN(g.id) FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.ID)) AS \"M\" FROM G AS GG)] is "
                + "not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT MAX(fz.id) AS m FROM g gg), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), "
                + "COUNT(FZ.ID))) AS \"AVG(FZ.ID)\" FROM G AS G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT AVG(fz.id) FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(PERCENTILE_CONT(CAST(FZ.ID AS NUMBER(38,"
                + "3)), 0.5)) AS \"MEDIAN(FZ.ID)\" FROM G AS G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT MEDIAN(fz.id) FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(SUM(IFF(CAST(FZ.B AS BOOLEAN), 1, 0))) "
                + "AS \"COUNT_IF(FZ.B)\" FROM G AS G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT COUNT_IF(fz.b) FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.B)) AS \"MAX(FZ.B)\" FROM G AS "
                + "G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT MAX(fz.b) FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(FZ.ID) AS \"ID\" FROM G AS G)] is not a "
                + "valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT fz.id FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(FZ.B) AS \"B\" FROM G AS G)] is not a "
                + "valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT fz.b FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT G.ID AS \"ID\" FROM G AS G)] is not a valid order by "
                + "expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT g.id FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT G.ID AS \"ID\", G.V AS \"V\", G.S AS \"S\" FROM G AS "
                + "G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT * FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT G.ID AS \"ID\", G.V AS \"V\", G.S AS \"S\" FROM G AS "
                + "G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT g.* FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT G.ID AS \"ID\", G.V AS \"V\" FROM G AS G WHERE G.V > "
                + "1)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT id, v FROM g WHERE v > 1), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT G.ID AS \"ID\" FROM G AS G WHERE G.V > 1  ORDER BY "
                + "ID ASC NULLS LAST LIMIT 1 OFFSET 0)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT id FROM g WHERE v > 1 ORDER BY id LIMIT 1), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.ID)) + 1 AS \"MAX(FZ.ID) + 1\" "
                + "FROM G AS G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT MAX(fz.id) + 1 FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\", 1 AS "
                + "\"1\" FROM G AS G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT MAX(fz.id), 1 FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM G AS G  HAVING "
                + "CORRELATION(MAX(FZ.ID)) > 1)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM g HAVING MAX(fz.id) > 1), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT COUNT(*) AS \"COUNT(*)\" FROM G AS G)] is not a "
                + "valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT COUNT(*) FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT MAX(G.ID) AS \"MAX(G.ID)\" FROM G AS G)] is not a "
                + "valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT MAX(g.id) FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT MAX(G.ID) AS \"MAX(G.ID)\" FROM G AS G GROUP BY "
                + "G.V)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT MAX(g.id) FROM g GROUP BY g.v), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM G AS G WHERE (G.ID = "
                + "CORRELATION(FZ.ID)) AND (G.V > 1))] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM g WHERE g.id = fz.id AND g.v > 1), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM G AS G INNER JOIN G AS G2 ON (G.ID = "
                + "G2.ID))] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM g JOIN g g2 ON g.id = g2.id), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT DISTINCT G.ID AS \"ID\" FROM G AS G)] is not a valid "
                + "order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT DISTINCT id FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM (SELECT G.ID AS \"ID\" FROM G AS G) "
                + "Q)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM (SELECT id FROM g) q), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)] is not a "
                + "valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT SET_COMBINE(WB$0.\"1\", WB$1.\"2\") AS \"1\" FROM "
                + "(SELECT OB$0.\"1\" AS \"1\" FROM (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL) OB$0) WB$0 UNION "
                + "ALL (SELECT OB$1.\"2\" AS \"2\" FROM (SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL) OB$1) WB$1)] "
                + "is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 UNION ALL SELECT 2), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM G AS G WHERE G.S = 'a')] is not a "
                + "valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM g WHERE g.s = 'a'), id");
    }

    @Test
    public void theKeysAreJudgedInTheOrderWritten() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR, f FLOAT, d NUMBER(10,2), dt DATE, va "
                + "VARIANT, n5 NUMBER(5,0), n38 NUMBER(38,10), ts TIMESTAMP_NTZ, n20 NUMBER(20,0), n21 NUMBER(21,0), "
                + "n109 NUMBER(10,9), n3837 NUMBER(38,37), n242 NUMBER(24,2), n232 NUMBER(23,2))");
        answer(
            "INSERT INTO fz SELECT 5, TRUE, 'a', 1.5, 2.25, '2020-01-01'::DATE, PARSE_JSON('1'), 5, 1.5, "
                + "'2020-01-01'::TIMESTAMP_NTZ, 5, 5, 1.5, 1.5, 1.5, 1.5");
        answer(
            "INSERT INTO fz SELECT 7, FALSE, 'b', 2.5, 3.25, '2020-01-02'::DATE, PARSE_JSON('2'), 7, 2.5, "
                + "'2020-01-02'::TIMESTAMP_NTZ, 7, 7, 2.5, 2.5, 2.5, 2.5");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
        answer(
            "INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\" FROM G AS "
                + "G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY CASE WHEN EXISTS (SELECT MAX(fz.id) FROM g) THEN 1 END, id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\" FROM G AS "
                + "G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT MAX(fz.id) FROM g) DESC NULLS LAST");
        answer(
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT MAX(fz.id) FROM g) AND TRUE, id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\" FROM G AS "
                + "G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY (EXISTS (SELECT MAX(fz.id) FROM g)), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\" FROM G AS "
                + "G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY id, EXISTS (SELECT MAX(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(F.ID)) AS \"MAX(F.ID)\" FROM G AS "
                + "X)] is not a valid order by expression",
            "SELECT id FROM fz f GROUP BY id ORDER BY EXISTS (SELECT MAX(f.id) FROM g x), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\" FROM G AS "
                + "G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY ALL ORDER BY EXISTS (SELECT MAX(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MAX(FZ.ID)] can "
                + "only appear in having or select clause",
            "SELECT MAX(id) FROM fz ORDER BY EXISTS (SELECT MAX(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MAX(FZ.ID)] can "
                + "only appear in having or select clause",
            "SELECT id FROM fz ORDER BY EXISTS (SELECT MAX(fz.id) FROM g), id");
        assertAnswer(
            "5 ;; 7",
            "SELECT id FROM fz ORDER BY EXISTS (SELECT 1 FROM g), id");
        assertAnswer(
            "5 ;; 7",
            "SELECT id FROM fz GROUP BY id HAVING EXISTS (SELECT MAX(fz.id) FROM g) ORDER BY id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT COUNT(*) AS \"COUNT(*)\" FROM G AS G)] is not a "
                + "valid order by expression",
            "SELECT id, COUNT(*) FROM fz GROUP BY id ORDER BY EXISTS (SELECT COUNT(*) FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MAX(FZ.ID)] can "
                + "only appear in having or select clause",
            "SELECT id, EXISTS (SELECT MAX(fz.id) FROM g) AS e FROM fz GROUP BY id ORDER BY e, id");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MAX(FZ.ID)] can "
                + "only appear in having or select clause",
            "SELECT id, EXISTS (SELECT MAX(fz.id) FROM g) AS e FROM fz GROUP BY id ORDER BY 2, id");
        assertAnswer(
            "5|true ;; 7|true",
            "SELECT id, EXISTS (SELECT 1 FROM g) AS e FROM fz GROUP BY id ORDER BY e, id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\" FROM G AS "
                + "G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id, b ORDER BY EXISTS (SELECT MAX(fz.id) FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\" FROM G AS "
                + "G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT MAX(fz.id) FROM g), EXISTS (SELECT MIN(fz.id) "
                + "FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 | invalid identifier 'NOSUCH'",
            "SELECT id FROM fz GROUP BY id ORDER BY nosuch, EXISTS (SELECT MAX(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.B] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY b, EXISTS (SELECT MAX(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\" FROM G AS "
                + "G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT MAX(fz.id) FROM g), b");
        assertAnswer(
            "REFUSED SQL compilation error: | [ANY(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\" FROM G AS G)] "
                + "is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY 1 = ANY (SELECT MAX(fz.id) FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [ALL(SELECT G.ID AS \"ID\" FROM G AS G)] is not a valid order by "
                + "expression",
            "SELECT id FROM fz GROUP BY id ORDER BY id = ALL (SELECT id FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [(SELECT MAX(G.ID) AS \"MAX(ID)\" FROM G AS G)] is not a valid "
                + "order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY id > (SELECT MAX(id) FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\" FROM G AS "
                + "G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT MAX(fz.id) FROM g) LIMIT 1");
        assertAnswer(
            "5",
            "SELECT id FROM fz GROUP BY id QUALIFY ROW_NUMBER() OVER (ORDER BY EXISTS (SELECT MAX(fz.id) FROM g)) "
                + "= 1");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\" FROM G AS "
                + "G WHERE G.ID = ANY(SELECT G.ID AS \"ID\" FROM G AS G))] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id IN (SELECT id "
                + "FROM g)), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(SUM(FZ.ID)) AS \"s m\" FROM "
                + "TEST_DB.TEST_SCHEMA.G AS G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT SUM(fz.id) AS \"s m\" FROM "
                + "test_db.test_schema.g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\" FROM G AS "
                + "G LIMIT 1 OFFSET 0)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT MAX(fz.id) FROM g LIMIT 1), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\" FROM G AS "
                + "G LIMIT 1 OFFSET 0)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT TOP 1 MAX(fz.id) FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\" FROM G AS "
                + "G INNER JOIN G AS G2)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT MAX(fz.id) FROM g, g g2), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\" FROM G AS "
                + "G LEFT OUTER JOIN G AS G2 ON (G.ID = G2.ID))] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT MAX(fz.id) FROM g LEFT JOIN g g2 ON g.id = "
                + "g2.id), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\" FROM G AS "
                + "G GROUP BY G.V)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT MAX(fz.id) FROM g GROUP BY g.v), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\", G.V AS "
                + "\"V\" FROM G AS G GROUP BY G.V  ORDER BY G.V ASC NULLS LAST)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT MAX(fz.id), g.v FROM g GROUP BY g.v ORDER BY "
                + "g.v), id");
    }

    @Test
    public void whateverTheSubqueryReads() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR, f FLOAT, d NUMBER(10,2), dt DATE, va "
                + "VARIANT, n5 NUMBER(5,0), n38 NUMBER(38,10), ts TIMESTAMP_NTZ, n20 NUMBER(20,0), n21 NUMBER(21,0), "
                + "n109 NUMBER(10,9), n3837 NUMBER(38,37), n242 NUMBER(24,2), n232 NUMBER(23,2))");
        answer(
            "INSERT INTO fz SELECT 5, TRUE, 'a', 1.5, 2.25, '2020-01-01'::DATE, PARSE_JSON('1'), 5, 1.5, "
                + "'2020-01-01'::TIMESTAMP_NTZ, 5, 5, 1.5, 1.5, 1.5, 1.5");
        answer(
            "INSERT INTO fz SELECT 7, FALSE, 'b', 2.5, 3.25, '2020-01-02'::DATE, PARSE_JSON('2'), 7, 2.5, "
                + "'2020-01-02'::TIMESTAMP_NTZ, 7, 7, 2.5, 2.5, 2.5, 2.5");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
        answer(
            "INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        assertAnswer(
            "5 ;; 7",
            "SELECT id FROM fz GROUP BY id ORDER BY id IN (1, 2), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)] is not a valid "
                + "order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY (SELECT 1) + 1, id");
        assertAnswer(
            "REFUSED SQL compilation error: | [(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)] is not a valid "
                + "order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY ABS((SELECT 1)), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [(SELECT MAX(G.ID) AS \"MAX(ID)\" FROM G AS G)] is not a valid "
                + "order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY COALESCE((SELECT MAX(id) FROM g), 0), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM G AS G)] is not a valid order by "
                + "expression",
            "SELECT id, EXISTS (SELECT 1 FROM g) FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM g), id");
        assertAnswer(
            "5|true ;; 7|true",
            "SELECT DISTINCT id, EXISTS (SELECT 1 FROM g) e FROM fz ORDER BY e, id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM G AS G)] is not a valid order by "
                + "expression",
            "SELECT DISTINCT id, EXISTS (SELECT 1 FROM g) FROM fz ORDER BY EXISTS (SELECT 1 FROM g), id");
        assertAnswer(
            "5 ;; 7",
            "SELECT id FROM fz GROUP BY id ORDER BY MAX(IFF(EXISTS (SELECT 1 FROM g), 1, 0)), id");
        assertAnswer(
            "7 ;; 5",
            "SELECT id FROM fz GROUP BY id ORDER BY MAX(id IN (SELECT id FROM g)), id");
        assertAnswer(
            "7 ;; 5",
            "SELECT id FROM fz GROUP BY id ORDER BY COUNT_IF(EXISTS (SELECT 1 FROM g WHERE g.id = fz.id)), id");
        answer(
            "SELECT id FROM fz GROUP BY id ORDER BY NOT EXISTS (SELECT 1 FROM g), id");
        answer(
            "SELECT id FROM fz GROUP BY id ORDER BY NOT (id IN (SELECT id FROM g)), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [ALL(SELECT CORRELATION(MAX(FZ.ID)) AS \"MAX(FZ.ID)\" FROM G AS G)] "
                + "is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY id NOT IN (SELECT MAX(fz.id) FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [ALL(SELECT G.ID AS \"ID\" FROM G AS G)] is not a valid order by "
                + "expression",
            "SELECT id FROM fz GROUP BY id ORDER BY id <> ALL (SELECT id FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [ANY(SELECT G.ID AS \"ID\" FROM G AS G)] is not a valid order by "
                + "expression",
            "SELECT id FROM fz GROUP BY id ORDER BY id > ANY (SELECT id FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [ANY(SELECT G.ID AS \"ID\" FROM G AS G)] is not a valid order by "
                + "expression",
            "SELECT id FROM fz GROUP BY id ORDER BY id = SOME (SELECT id FROM g), id");
        answer(
            "SELECT id FROM fz GROUP BY id ORDER BY (id, id) IN (SELECT id, v FROM g), id");
        answer(
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM g) OR EXISTS (SELECT 2 FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)] is not a valid "
                + "order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY (SELECT 1) = (SELECT 2), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [ANY(SELECT G.ID AS \"ID\" FROM G AS G)] is not a valid order by "
                + "expression",
            "SELECT id FROM fz GROUP BY id ORDER BY id IN (SELECT id FROM g) DESC, EXISTS (SELECT 1 FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM G AS G)] is not a valid order by "
                + "expression",
            "SELECT id FROM fz GROUP BY id ORDER BY 1, EXISTS (SELECT 1 FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | [(SELECT ARRAY_AGG(G.ID) AS \"ARRAY_AGG(ID)\" FROM G AS G)] is not "
                + "a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY ARRAY_CONTAINS(1, (SELECT ARRAY_AGG(id) FROM g)), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM G AS G WHERE EXISTS(SELECT 1 AS "
                + "\"1\" FROM G AS G2 WHERE G2.ID = CORRELATION(G.ID)))] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM g WHERE EXISTS (SELECT 1 FROM g g2 "
                + "WHERE g2.id = g.id)), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CORRELATION(FZ.ID) + G.ID AS \"FZ.ID + G.ID\" FROM G "
                + "AS G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT fz.id + g.id FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT MAX(CORRELATION(FZ.ID) + G.ID) AS \"MAX(FZ.ID + "
                + "G.ID)\" FROM G AS G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT MAX(fz.id + g.id) FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT COUNT(DISTINCT G.ID) AS \"COUNT(DISTINCT G.ID)\" "
                + "FROM G AS G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT COUNT(DISTINCT g.id) FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CAST(G.ID AS VARCHAR(134217728)) AS "
                + "\"G.ID::VARCHAR\", UPPER(G.S) AS \"X\" FROM G AS G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT g.id::VARCHAR, UPPER(g.s) x FROM g), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT CASE_FLATTENED(G.ID > 1, 1, 2) AS \"CASE WHEN G.ID > "
                + "1 THEN 1 ELSE 2 END\" FROM G AS G)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT CASE WHEN g.id > 1 THEN 1 ELSE 2 END FROM g), "
                + "id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM G AS G WHERE ((G.ID >= 1) AND (G.ID "
                + "<= 6)) AND (G.S IS NOT NULL))] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM g WHERE g.id BETWEEN 1 AND 6 AND g.s IS "
                + "NOT NULL), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM G AS G WHERE (G.ID IN (5, 6)) OR "
                + "(G.S LIKE 'a%'))] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM g WHERE g.id IN (5, 6) OR g.s LIKE "
                + "'a%'), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM G AS G QUALIFY ROW_NUMBER() OVER "
                + "(ORDER BY G.ID ASC NULLS LAST) = 1)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM g QUALIFY ROW_NUMBER() OVER (ORDER BY "
                + "g.id) = 1), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM G AS G  ORDER BY G.ID DESC NULLS "
                + "FIRST, G.V ASC NULLS LAST)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM g ORDER BY g.id DESC, g.v), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM G AS G LIMIT 2 OFFSET 1)] is not a "
                + "valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM g LIMIT 2 OFFSET 1), id");
        answer(
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM g x NATURAL JOIN g y), id");
        answer(
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM g x JOIN g y USING (id)), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM G AS X INNER JOIN G AS Y)] is not a "
                + "valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM g x CROSS JOIN g y), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM G AS X FULL OUTER JOIN G AS Y ON "
                + "(X.ID = Y.ID))] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM g x FULL JOIN g y ON x.id = y.id), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM (VALUES (1), (2)) V)] is not a valid "
                + "order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM (VALUES (1), (2)) v(a)), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM TABLE (FLATTEN(1 => "
                + "ARRAY_CONSTRUCT(1, 2))))] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM TABLE(FLATTEN(ARRAY_CONSTRUCT(1, 2)))), "
                + "id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT C.A AS \"A\" FROM (SELECT 1 AS \"A\" FROM (VALUES "
                + "(null)) DUAL) C)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (WITH c AS (SELECT 1 a) SELECT a FROM c), id");
        answer(
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM g GROUP BY ALL), id");
        answer(
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT g.v, COUNT(*) c FROM g GROUP BY ROLLUP (g.v)), "
                + "id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT SET_COMBINE(WB$0.\"1\", WB$1.\"2\") AS \"1\" FROM "
                + "(SELECT OB$0.\"1\" AS \"1\" FROM (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL) OB$0) WB$0 MINUS "
                + "(SELECT OB$1.\"2\" AS \"2\" FROM (SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL) OB$1) WB$1)] is "
                + "not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 EXCEPT SELECT 2), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT SET_COMBINE(WB$0.\"1\", WB$1.\"2\") AS \"1\" FROM "
                + "(SELECT OB$0.\"1\" AS \"1\" FROM (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL) OB$0) WB$0 UNION "
                + "(SELECT OB$1.\"2\" AS \"2\" FROM (SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL) OB$1) WB$1)] is "
                + "not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 UNION SELECT 2), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [EXISTS(SELECT SET_COMBINE(WB$0.\"1\", WB$1.\"1\") AS \"1\" FROM "
                + "(SELECT OB$0.\"1\" AS \"1\" FROM (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL) OB$0) WB$0 "
                + "INTERSECT (SELECT OB$1.\"1\" AS \"1\" FROM (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL) OB$1) "
                + "WB$1)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 INTERSECT SELECT 1), id");
        assertAnswer(
            "REFUSED SQL compilation error: | [(SELECT SET_COMBINE(WB$0.\"1\", WB$1.\"2\") AS \"1\" FROM (SELECT "
                + "OB$0.\"1\" AS \"1\" FROM (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL) OB$0) WB$0 UNION ALL "
                + "(SELECT OB$1.\"2\" AS \"2\" FROM (SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL) OB$1) WB$1 LIMIT 1 "
                + "OFFSET 0)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY (SELECT 1 UNION ALL SELECT 2 LIMIT 1), id");
    }
}
