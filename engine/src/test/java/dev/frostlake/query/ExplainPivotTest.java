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
 * EXPLAIN of a PIVOT, its values written or read as the query runs — IN (ANY), IN (a subquery) — answers a plan
 * (live-verified): a Pivot over the scan where the source keeps a column beside the aggregated and the FOR one,
 * otherwise an Aggregate over the scan for written values and over a Pivot for read ones. A dynamic pivot's values
 * are read while the statement compiles, and a refusal is placed in the EXPLAIN statement.
 */
public class ExplainPivotTest extends MeasuredCellsTestSupport {

    @Test
    public void aPivotIsPlannedByItsShape() {
        engine.execute("CREATE OR REPLACE TABLE et (a INT, b VARCHAR)");
        engine.execute("INSERT INTO et VALUES (1, 'x'), (2, 'y'), (3, 'z')");
        engine.execute("CREATE OR REPLACE TABLE ep (k INT, a INT, b VARCHAR)");
        engine.execute("INSERT INTO ep VALUES (1, 1, 'x'), (1, 2, 'y'), (2, 3, 'x')");
        answer("EXPLAIN SELECT * FROM et PIVOT (SUM(a) FOR b IN (ANY ORDER BY b))");
        assertAnswer("Result>Aggregate>Pivot>TableScan",
            "SELECT LISTAGG(\"operation\", '>') WITHIN GROUP (ORDER BY \"id\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        answer("EXPLAIN SELECT * FROM et PIVOT (SUM(a) FOR b IN (SELECT DISTINCT b FROM et))");
        assertAnswer("Result>Aggregate>Pivot>TableScan",
            "SELECT LISTAGG(\"operation\", '>') WITHIN GROUP (ORDER BY \"id\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        answer("EXPLAIN SELECT * FROM et PIVOT (SUM(a) FOR b IN ('x', 'y'))");
        assertAnswer("Result>Aggregate>TableScan",
            "SELECT LISTAGG(\"operation\", '>') WITHIN GROUP (ORDER BY \"id\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        answer("EXPLAIN SELECT * FROM ep PIVOT (SUM(a) FOR b IN (ANY ORDER BY b))");
        assertAnswer("Result>Pivot>TableScan",
            "SELECT LISTAGG(\"operation\", '>') WITHIN GROUP (ORDER BY \"id\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        answer("EXPLAIN SELECT * FROM ep PIVOT (SUM(a) FOR b IN ('x', 'y'))");
        assertAnswer("Result>Pivot>TableScan",
            "SELECT LISTAGG(\"operation\", '>') WITHIN GROUP (ORDER BY \"id\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        answer("EXPLAIN SELECT * FROM ep AS e PIVOT (MAX(a) FOR b IN (ANY))");
        assertAnswer("Result>Pivot>TableScan",
            "SELECT LISTAGG(\"operation\", '>') WITHIN GROUP (ORDER BY \"id\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        answer("EXPLAIN SELECT k FROM ep PIVOT (SUM(a) FOR b IN (ANY))");
        assertAnswer("Result>Pivot>TableScan",
            "SELECT LISTAGG(\"operation\", '>') WITHIN GROUP (ORDER BY \"id\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        answer("EXPLAIN SELECT * FROM ep PIVOT (AVG(a) FOR b IN (SELECT b FROM ep WHERE k = 1))");
        assertAnswer("Result>Pivot>TableScan",
            "SELECT LISTAGG(\"operation\", '>') WITHIN GROUP (ORDER BY \"id\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        answer("EXPLAIN SELECT * FROM (SELECT a, b FROM ep) PIVOT (SUM(a) FOR b IN (ANY))");
        assertAnswer("Result>Aggregate>Pivot>TableScan",
            "SELECT LISTAGG(\"operation\", '>') WITHIN GROUP (ORDER BY \"id\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        answer("EXPLAIN SELECT * FROM (SELECT k, a, b FROM ep) PIVOT (SUM(a) FOR b IN (ANY))");
        assertAnswer("Result>Pivot>TableScan",
            "SELECT LISTAGG(\"operation\", '>') WITHIN GROUP (ORDER BY \"id\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        answer("EXPLAIN SELECT * FROM ep PIVOT (SUM(a) FOR b IN (ANY)) ORDER BY 1");
        assertAnswer("Result>Sort>Pivot>TableScan",
            "SELECT LISTAGG(\"operation\", '>') WITHIN GROUP (ORDER BY \"id\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        answer("EXPLAIN SELECT * FROM et PIVOT (SUM(a) FOR b IN (ANY)) WHERE 1 = 0");
        answer("SELECT LISTAGG(\"operation\", '>') WITHIN GROUP (ORDER BY \"id\") FROM "
            + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
        answer("EXPLAIN SELECT * FROM et PIVOT (SUM(a) FOR b IN (SELECT b FROM et WHERE nosuch = 1))");
        answer("EXPLAIN SELECT * FROM et PIVOT (SUM(a) FOR b IN (SELECT b / 0 FROM et))");
        answer("SELECT LISTAGG(\"operation\", '>') WITHIN GROUP (ORDER BY \"id\") FROM "
            + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"id\" IS NOT NULL");
    }

    @Test
    public void aDynamicPivotCompilesForItsPlan() {
        engine.execute("CREATE OR REPLACE TABLE et (a INT, b VARCHAR)");
        engine.execute("INSERT INTO et VALUES (1, 'x'), (2, 'y'), (3, 'z')");
        assertAnswer("1|2|3", "SELECT * FROM et PIVOT (SUM(a) FOR b IN (ANY ORDER BY b))");
        answer("EXPLAIN SELECT * FROM et PIVOT (SUM(a) FOR b IN (ANY ORDER BY b))");
        answer("EXPLAIN SELECT * FROM et PIVOT (SUM(a) FOR b IN (SELECT DISTINCT b FROM et))");
        answer("EXPLAIN USING TEXT SELECT * FROM et PIVOT (SUM(a) FOR b IN (ANY))");
        answer("EXPLAIN SELECT * FROM et PIVOT (SUM(a) FOR b IN ('x', 'y'))");
        answer("EXPLAIN SELECT * FROM et PIVOT (SUM(a) FOR b IN (ANY)) WHERE 1 = 0");
        answer("EXPLAIN SELECT * FROM et PIVOT (SUM(a) FOR b IN (SELECT b FROM et WHERE nosuch = 1))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 15 | invalid identifier 'NOSUCH'",
            "EXPLAIN SELECT nosuch FROM et PIVOT (SUM(a) FOR b IN (ANY))");
    }
}
