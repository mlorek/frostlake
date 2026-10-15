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
 * GROUPING is named GROUPING_ID wherever a refusal echoes the call, and answers 1 over what is no key of the
 * GROUP BY; a bare star under ALL or DISTINCT is refused over the statement's text; LISTAGG's delimiter is
 * echoed from the plan (live-verified).
 */
public class GroupingEchoAndStarQuantifierTest extends MeasuredCellsTestSupport {

    @Test
    public void groupingIsNamedGroupingId() {
        answer(
            "CREATE OR REPLACE TABLE rt (n INT, g VARCHAR)");
        answer(
            "INSERT INTO rt VALUES (1, 'a'), (2, 'b')");
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO FZ VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE FUNCTION tf1(x INT) RETURNS TABLE (v INT) AS 'SELECT x'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[GROUPING_ID()], expected 1, got 0",
            "SELECT GROUPING() FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid function type [GROUPING_ID] for window function.",
            "SELECT GROUPING(n) OVER () FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | GROUPING function without a GROUP BY.",
            "SELECT GROUPING(n) FROM rt");
        assertAnswer(
            "0 ;; 0",
            "SELECT GROUPING(n) FROM rt GROUP BY n ORDER BY 1");
        assertAnswer(
            "0 ;; 0",
            "SELECT GROUPING(n, g, n) FROM rt GROUP BY n, g ORDER BY 1");
        assertAnswer(
            "1 ;; 1",
            "SELECT GROUPING(n + 1) FROM rt GROUP BY n");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 16 | invalid identifier 'NOSUCH'",
            "SELECT GROUPING(nosuch) FROM rt GROUP BY n");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 16 | 'RT.G' in select clause is neither an "
                + "aggregate nor in the group by clause.",
            "SELECT GROUPING(g) FROM rt GROUP BY n");
        assertAnswer(
            "1 ;; 1",
            "SELECT GROUPING(n => 1) FROM rt GROUP BY n");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'GROUPING_ID(DISTINCT RT.N)'",
            "SELECT GROUPING(DISTINCT n) FROM rt GROUP BY n");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | 'RT.G' in select clause is neither an "
                + "aggregate nor in the group by clause.",
            "SELECT GROUPING(*) FROM rt GROUP BY n");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[GROUPING_ID()], expected 1, got 0",
            "SELECT GROUPING_ID() FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid function type [GROUPING_ID] for window function.",
            "SELECT GROUPING_ID(n) OVER () FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'IGNORE'.",
            "SELECT GROUPING(n) IGNORE NULLS FROM rt GROUP BY n");
        assertAnswer(
            "REFUSED SQL compilation error: | Function GROUPING_ID does not support WITHIN GROUP clause.",
            "SELECT GROUPING(n) WITHIN GROUP (ORDER BY n) FROM rt GROUP BY n");
        assertAnswer(
            "REFUSED SQL compilation error: | GROUPING function without a GROUP BY.",
            "SELECT n FROM rt WHERE GROUPING(n) = 0");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 35 | not enough arguments for function "
                + "[GROUPING_ID()], expected 1, got 0",
            "SELECT n FROM rt GROUP BY n HAVING GROUPING() = 0");
        assertAnswer(
            "1 ;; 1",
            "SELECT GROUPING(1) FROM rt GROUP BY n");
        assertAnswer(
            "1 ;; 1",
            "SELECT GROUPING('n') FROM rt GROUP BY n");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid function type [GROUPING_ID] for window function.",
            "SELECT GROUPING(n) OVER (ORDER BY n) FROM rt GROUP BY n");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid function type [GROUPING_ID] for window function.",
            "SELECT GROUPING(n, g) OVER () FROM rt");
        assertAnswer(
            "1 ;; 1",
            "SELECT GROUPING(SUM(n)) FROM rt GROUP BY n");
        assertAnswer(
            "0 ;; 0",
            "SELECT SUM(GROUPING(n)) FROM rt GROUP BY n");
        assertAnswer(
            "0 ;; 0 ;; 1",
            "SELECT grouping(n) AS c FROM rt GROUP BY ROLLUP (n) ORDER BY 1");
        assertShape(
            "GROUPING(N):NUMBER(18,0), GROUPING_ID(N):NUMBER(18,0)",
            "SELECT GROUPING(n), GROUPING_ID(n) FROM rt GROUP BY n");
    }

    @Test
    public void theDelimiterOfListagg() {
        answer(
            "CREATE OR REPLACE TABLE rt (n INT, g VARCHAR)");
        answer(
            "INSERT INTO rt VALUES (1, 'a'), (2, 'b')");
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO FZ VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE FUNCTION tf1(x INT) RETURNS TABLE (v INT) AS 'SELECT x'");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found '(SELECT "
                + "',' AS \"','\" FROM (VALUES (null)) DUAL)'",
            "SELECT LISTAGG(n, (SELECT ',')) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found "
                + "'CASE_FLATTENED(RT.N = 1, ',', ';')'",
            "SELECT LISTAGG(n, CASE WHEN n = 1 THEN ',' ELSE ';' END) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found '(SELECT "
                + "RT.G AS \"G\" FROM RT AS RT LIMIT 1 OFFSET 0)'",
            "SELECT LISTAGG(n, (SELECT g FROM rt LIMIT 1)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found "
                + "'IFF(RT.N = 1, ',', ';')'",
            "SELECT LISTAGG(n, IFF(n = 1, ',', ';')) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found 'RT.G || "
                + "',''",
            "SELECT LISTAGG(n, g || ',') FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found "
                + "'UPPER(RT.G)'",
            "SELECT LISTAGG(n, UPPER(g)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found "
                + "'NVL(RT.G, ',')'",
            "SELECT LISTAGG(n, NVL(g, ',')) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found "
                + "'CAST(RT.G AS VARCHAR(1))'",
            "SELECT LISTAGG(n, g::VARCHAR(1)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found 'CASE "
                + "RT.N WHEN CAST(1 AS NUMBER(38,0)) THEN ',' ELSE SYSTEM$NULL_TO_TEXT(null) END'",
            "SELECT LISTAGG(n, CASE n WHEN 1 THEN ',' END) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found '(SELECT "
                + "MAX(RT.G) AS \"MAX(G)\" FROM RT AS RT)'",
            "SELECT LISTAGG(n, (SELECT MAX(g) FROM rt)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found '(SELECT "
                + "',' AS \"','\" FROM RT AS RT LIMIT 1 OFFSET 0)'",
            "SELECT LISTAGG(n, (SELECT ',' FROM rt LIMIT 1)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found '(SELECT "
                + "',' AS \"','\" FROM (VALUES (null)) DUAL WHERE 1 = 1)'",
            "SELECT LISTAGG(n, (SELECT ',' WHERE 1 = 1)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found "
                + "'CASE_FLATTENED(CAST(TRUE AS BOOLEAN), ',', SYSTEM$NULL_TO_TEXT(null))'",
            "SELECT LISTAGG(n, CASE WHEN TRUE THEN ',' END) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found "
                + "'IFNULL(RT.G, ',')'",
            "SELECT LISTAGG(n, COALESCE(g, ',')) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found 'RT.N'",
            "SELECT LISTAGG(n, n) FROM rt");
        assertAnswer(
            "1,;2",
            "SELECT LISTAGG(n, ',' || ';') WITHIN GROUP (ORDER BY n) FROM rt");
    }

    @Test
    public void aStarUnderAQuantifier() {
        answer(
            "CREATE OR REPLACE TABLE rt (n INT, g VARCHAR)");
        answer(
            "INSERT INTO rt VALUES (1, 'a'), (2, 'b')");
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO FZ VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE FUNCTION tf1(x INT) RETURNS TABLE (v INT) AS 'SELECT x'");
        assertAnswer(
            "REFUSED Unsupported feature 'TOK_STAR'.",
            "SELECT COUNT(ALL *) FROM FZ3");
        assertAnswer(
            "REFUSED Unsupported feature 'TOK_STAR'.",
            "SELECT COUNT(ALL *) FROM FZ");
        assertAnswer(
            "REFUSED Unsupported feature 'TOK_STAR'.",
            "SELECT COUNT(DISTINCT *) FROM FZ3");
        assertAnswer(
            "REFUSED Unsupported feature 'TOK_STAR'.",
            "SELECT COUNT(ALL *) OVER () FROM FZ");
        assertAnswer(
            "REFUSED Unsupported feature 'TOK_STAR'.",
            "SELECT HASH_AGG(ALL *) FROM FZ");
        assertAnswer(
            "REFUSED Unsupported feature 'TOK_STAR'.",
            "SELECT NOSUCHFN(DISTINCT *) FROM FZ");
        assertAnswer(
            "REFUSED Unsupported feature 'TOK_STAR'.",
            "SELECT NOSUCHFN(ALL *) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NOSUCHFN.",
            "SELECT NOSUCHFN(*) FROM FZ");
        assertAnswer(
            "REFUSED Unsupported feature 'TOK_STAR'.",
            "SELECT SUM(ALL *) FROM FZ");
        assertAnswer(
            "2",
            "SELECT COUNT(ALL FZ.*) FROM FZ");
        assertAnswer(
            "REFUSED Unsupported feature 'TOK_STAR'.",
            "SELECT COUNT(ALL *), nosuch FROM FZ");
        assertAnswer(
            "REFUSED Unsupported feature 'TOK_STAR'.",
            "SELECT COUNT(DISTINCT *), nosuch FROM FZ");
        assertAnswer(
            "2",
            "SELECT COUNT(ALL id) FROM FZ");
        assertAnswer(
            "2",
            "SELECT COUNT(ALL id, b) FROM FZ");
        assertAnswer(
            "2",
            "SELECT COUNT(DISTINCT id, b) FROM FZ");
        assertAnswer(
            "REFUSED Unsupported feature 'TOK_STAR'.",
            "SELECT COUNT(ALL *) FROM FZ WHERE nosuch = 1");
        assertAnswer(
            "2",
            "SELECT COUNT(ALL * EXCLUDE b) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid function 'COUNT'",
            "SELECT COUNT(ALL) FROM FZ");
    }

    @Test
    public void flattenAndQualifiedTableFunctions() {
        answer(
            "CREATE OR REPLACE TABLE rt (n INT, g VARCHAR)");
        answer(
            "INSERT INTO rt VALUES (1, 'a'), (2, 'b')");
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO FZ VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE FUNCTION tf1(x INT) RETURNS TABLE (v INT) AS 'SELECT x'");
        assertAnswer(
            "2|1",
            "SELECT COUNT(f.seq), COUNT(DISTINCT f.seq) FROM FZ, TABLE(FLATTEN(input => [1])) f");
        assertAnswer(
            "2",
            "SELECT COUNT(DISTINCT f.seq) FROM FZ, LATERAL FLATTEN(input => ARRAY_CONSTRUCT(FZ.id, 1)) f");
        assertAnswer(
            "1",
            "SELECT COUNT(DISTINCT seq) FROM TABLE(FLATTEN(input => [1, 2]))");
        assertAnswer(
            "0 ;; 0 ;; 1 ;; 1",
            "SELECT f.index FROM FZ, TABLE(FLATTEN(input => [1, 2])) f ORDER BY 1");
        assertAnswer(
            "1",
            "SELECT tf1.v FROM TABLE(test_db.test_schema.tf1(1))");
        assertAnswer(
            "1",
            "SELECT tf1.v FROM TABLE(test_schema.tf1(1))");
        assertAnswer(
            "1",
            "SELECT tf1.v FROM TABLE(tf1(1))");
        assertAnswer(
            "1",
            "SELECT t.v FROM TABLE(test_schema.tf1(1)) t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'TEST_SCHEMA.TF1.V'",
            "SELECT test_schema.tf1.v FROM TABLE(test_schema.tf1(1))");
        assertAnswer(
            "1",
            "SELECT v FROM TABLE(test_db.test_schema.tf1(1))");
        assertAnswer(
            "1",
            "SELECT flatten.value FROM TABLE(FLATTEN(input => [1]))");
        assertAnswer(
            "a",
            "SELECT split_to_table.value FROM TABLE(SPLIT_TO_TABLE('a', ','))");
    }
}
