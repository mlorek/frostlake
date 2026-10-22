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
 * EXPLAIN lists the expressions each operator computes as the account resolves them: columns named by their
 * relation, a scan the columns it reads, a Filter's conditions, a join's keys and its other conditions, an
 * Aggregate's aggExprs and groupKeys, a Sort's keys with their NULLS placement, a Limit's rowCount and offset, a
 * union's UNION_ALL columns, and the conversions, CASE, IN, LIKE, BETWEEN and AVG the plan rewrites (all
 * live-verified). Plans the account shapes from table statistics and metadata are left out.
 */
public class ExplainExpressionsTest extends MeasuredCellsTestSupport {

    @Test
    public void operatorExpressions() {
        engine.execute("CREATE OR REPLACE TABLE et (a INT, b VARCHAR)");
        engine.execute("CREATE OR REPLACE TABLE eu (a INT, b VARCHAR)");
        engine.execute("INSERT INTO et VALUES (1, 'x'), (2, 'y'), (3, 'z')");
        engine.execute("INSERT INTO eu VALUES (1, 'x'), (5, 'q')");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET.A, ET.B|null|"
                + "null|null ;; 1|1|[0]|Filter|null|null|ET.A > 1|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a, b FROM et WHERE a > 1");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET.A|null|null|"
                + "null ;; 1|1|[0]|SortWithLimit|null|null|sortKey: [ET.A ASC NULLS LAST], rowCount: 2|null|null|null "
                + ";; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM et ORDER BY a LIMIT 2");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET.B, SUM(ET.A)|"
                + "null|null|null ;; 1|1|[0]|Aggregate|null|null|aggExprs: [SUM(ET.A)], groupKeys: [ET.B]|null|null|"
                + "null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT b, SUM(a) FROM et GROUP BY b");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A, EU.B|null|"
                + "null|null ;; 1|1|[0]|InnerJoin|null|null|joinKey: (EU.A = ET.A)|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|null|A, B|1|1|1024 ;; 1|3|[1]|JoinFilter|null|null|joinKey: (EU.A "
                + "= ET.A)|null|null|null ;; 1|4|[3]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT et.a, eu.b FROM et JOIN eu ON et.a = eu.a");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A, ET.B, "
                + "EU.A, EU.B|null|null|null ;; 1|1|[0]|LeftOuterJoin|null|null|joinKey: (EU.A = ET.A)|null|null|null "
                + ";; 1|2|[1]|Filter|null|null|EU.A IS NOT NULL|null|null|null ;; 1|3|[2]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EU|null|A, B|1|1|1024 ;; 1|4|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|A, B|1|"
                + "1|1024",
            "EXPLAIN USING TABULAR SELECT * FROM et LEFT JOIN eu ON et.a = eu.a");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET.A|null|null|"
                + "null ;; 1|1|[0]|Limit|null|null|rowCount: 2|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM et LIMIT 2");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET.A + 1, "
                + "UPPER(ET.B)|null|null|null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a + 1 AS x, UPPER(b) FROM et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|UNION_ALL(ET.A, "
                + "EU.A)|null|null|null ;; 1|1|[0]|UnionAll|null|null|null|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024 ;; 1|3|[1]|TableScan|TEST_DB.TEST_SCHEMA.EU|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM et UNION ALL SELECT a FROM eu");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET.B|null|null|"
                + "null ;; 1|1|[0]|Aggregate|null|null|groupKeys: [ET.B]|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET|null|B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT DISTINCT b FROM et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET.B, COUNT(*)|"
                + "null|null|null ;; 1|1|[0]|Filter|null|null|COUNT(*) > 1|null|null|null ;; 1|2|[1]|Aggregate|null|"
                + "null|aggExprs: [COUNT(*)], groupKeys: [ET.B]|null|null|null ;; 1|3|[2]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET|null|B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT b, COUNT(*) FROM et GROUP BY b HAVING COUNT(*) > 1");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|E.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|(E.A = 1) AND (E.B = 'x')|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET|E|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM et e WHERE e.a = 1 AND b = 'x'");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET.A, ET.B|null|"
                + "null|null ;; 1|1|[0]|Filter|null|null|ET.A IN 1 IN 2|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT * FROM et WHERE a IN (1, 2)");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|STARTSWITH(ET.B, 'x')|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM et WHERE b LIKE 'x%'");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|"
                + "CASE_FLATTENED(ET.A > 1, 'x', 'y')|null|null|null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|"
                + "A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT CASE WHEN a > 1 THEN 'x' ELSE 'y' END FROM et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|TO_CHAR(ET.A)|"
                + "null|null|null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a::VARCHAR FROM et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A|null|null|"
                + "null ;; 1|1|[0]|InnerJoin|null|null|joinKey: (EU.A = ET.A)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EU|null|A|1|1|1024 ;; 1|3|[1]|JoinFilter|null|null|joinKey: (EU.A = ET.A)|null|"
                + "null|null ;; 1|4|[3]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT et.a FROM et, eu WHERE et.a = eu.a");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET.A|null|null|"
                + "null ;; 1|1|[0]|SortWithLimit|null|null|sortKey: [ET.A ASC NULLS LAST], rowCount: 1|null|null|null "
                + ";; 1|2|[1]|Filter|null|null|ET.A > 1|null|null|null ;; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.ET|"
                + "null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM et WHERE a > 1 ORDER BY a LIMIT 1");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET.B, "
                + "SCALED_ROUND_INT_DIVIDE(SUM(ET.A), COUNT(ET.A))|null|null|null ;; 1|1|[0]|Sort|null|null|"
                + "SCALED_ROUND_INT_DIVIDE(SUM(ET.A), COUNT(ET.A)) ASC NULLS LAST|null|null|null ;; 1|2|[1]|Aggregate|"
                + "null|null|aggExprs: [SUM(ET.A), COUNT(ET.A)], groupKeys: [ET.B]|null|null|null ;; 1|3|[2]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT b, AVG(a) AS m FROM et GROUP BY b ORDER BY m");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET.A, "
                + "COUNT(DISTINCT ET.B)|null|null|null ;; 1|1|[0]|Aggregate|null|null|aggExprs: [COUNT(DISTINCT "
                + "ET.B)], groupKeys: [ET.A]|null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|A, B|1|1|"
                + "1024",
            "EXPLAIN USING TABULAR SELECT a, COUNT(DISTINCT b) FROM et GROUP BY a");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'A'",
            "EXPLAIN USING TABULAR SELECT a FROM et e JOIN eu u ON e.a = u.a AND e.b = u.b");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|NOT((ET.A = 1) OR (ET.B = 'y'))|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM et WHERE NOT (a = 1 OR b = 'y')");
    }

    @Test
    public void expressionForms() {
        engine.execute("CREATE OR REPLACE TABLE et (a INT, b VARCHAR)");
        engine.execute("CREATE OR REPLACE TABLE eu (a INT, b VARCHAR)");
        engine.execute("INSERT INTO et VALUES (1, 'x'), (2, 'y'), (3, 'z')");
        engine.execute("INSERT INTO eu VALUES (1, 'x'), (5, 'q')");
        engine.execute("CREATE OR REPLACE TABLE ew (a INT, b VARCHAR, c NUMBER(10,2), d DATE)");
        engine.execute("INSERT INTO ew VALUES (1, 'x', 1.5, '2024-01-01'), (2, NULL, NULL, NULL), (5, 'q', 2.25, "
            + "'2024-02-01')");
        engine.execute("CREATE OR REPLACE VIEW ew_v AS SELECT a, b FROM ew WHERE a > 1");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|EW.B IS NULL|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE b IS NULL");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|EW.B IS NOT NULL|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE b IS NOT NULL");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|(EW.A >= 2) AND (EW.A <= 3)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE a BETWEEN 2 AND 3");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|NOT(EW.A IN 1 IN 2)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE a NOT IN (1, 2)");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|ENDSWITH(EW.B, 'x')|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE b LIKE '%x'");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|CONTAINS(EW.B, 'x')|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE b LIKE '%x%'");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|EW.B LIKE 'x_y'|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE b LIKE 'x_y'");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|EW.B ILIKE 'x%'|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE b ILIKE 'x%'");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|NOT(STARTSWITH(EW.B, 'x'))|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE b NOT LIKE 'x%'");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|(EW.A) + (EW.C "
                + "* 2), (EW.A + 1) * 2, NEGATE(EW.A), SCALED_ROUND_INT_DIVIDE(EW.A, 2), EW.A % 2|null|null|null ;; 1|"
                + "1|[0]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, C|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a + c * 2, (a + 1) * 2, -a, a / 2, a % 2 FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A, EW.C, "
                + "TO_CHAR(EW.D), TO_DATE(EW.B), TO_DOUBLE(EW.A), TRY_TO_NUMBER(EW.B)|null|null|null ;; 1|1|[0]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, B, C, D|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a::INT, c::NUMBER(10,2), d::VARCHAR, b::DATE, CAST(a AS DOUBLE), "
                + "TRY_CAST(b AS INT) FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|IFNULL(EW.B, "
                + "'n'), IFF(EW.A > 1, 'p', 'q'), NVL(EW.B, 'z'), LOWER(EW.B), CONCAT(EW.B, 'x'), EW.B || 'x'|null|"
                + "null|null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT COALESCE(b, 'n'), IFF(a > 1, 'p', 'q'), NVL(b, 'z'), lower(b), CONCAT(b, "
                + "'x'), b || 'x' FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|((EW.A > 1) AND (EW.B = 'x')) OR (EW.C > 2)|null|null|null ;; 1|2|"
                + "[1]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, B, C|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE a > 1 AND b = 'x' OR c > 2");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|(EW.A > 1) AND ((EW.B = 'x') OR (EW.C > 2)) AND (EW.D IS NULL)|"
                + "null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, B, C, D|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE a > 1 AND (b = 'x' OR c > 2) AND d IS NULL");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A + 1, "
                + "COUNT(*)|null|null|null ;; 1|1|[0]|Aggregate|null|null|aggExprs: [COUNT(*)], groupKeys: [EW.A + 1]|"
                + "null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a + 1, COUNT(*) FROM ew GROUP BY a + 1");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.B, COUNT(*)|"
                + "null|null|null ;; 1|1|[0]|Filter|null|null|COUNT(*) > 1|null|null|null ;; 1|2|[1]|Aggregate|null|"
                + "null|aggExprs: [COUNT(*)], groupKeys: [EW.B]|null|null|null ;; 1|3|[2]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT b, COUNT(*) AS n FROM ew GROUP BY b HAVING n > 1");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.B, SUM(EW.A)|"
                + "null|null|null ;; 1|1|[0]|Sort|null|null|SUM(EW.A) DESC NULLS FIRST|null|null|null ;; 1|2|[1]|"
                + "Aggregate|null|null|aggExprs: [SUM(EW.A)], groupKeys: [EW.B]|null|null|null ;; 1|3|[2]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT b, SUM(a) FROM ew GROUP BY 1 ORDER BY 2 DESC");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A, SUM(EW.A) "
                + "OVER (PARTITION BY EW.B ORDER BY EW.A DESC NULLS FIRST)|null|null|null ;; 1|1|[0]|WindowFunction|"
                + "null|null|SUM(EW.A) OVER (PARTITION BY EW.B ORDER BY EW.A DESC NULLS FIRST)|null|null|null ;; 1|2|"
                + "[1]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a, SUM(a) OVER (PARTITION BY b ORDER BY a DESC) FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A, COUNT(*) "
                + "OVER ()|null|null|null ;; 1|1|[0]|WindowFunction|null|null|COUNT(*) OVER ()|null|null|null ;; 1|2|"
                + "[1]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a, COUNT(*) OVER () FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Limit|null|null|rowCount: 2, offset: 1|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew LIMIT 2 OFFSET 1");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|SortWithLimit|null|null|sortKey: [EW.A ASC NULLS LAST], rowCount: 2, offset: 1|"
                + "null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew ORDER BY a LIMIT 2 OFFSET 1");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Limit|null|null|rowCount: 2|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT TOP 2 a FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Limit|null|null|rowCount: 2|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew FETCH FIRST 2 ROWS ONLY");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|UNION_ALL(ET.A, "
                + "EU.A)|null|null|null ;; 1|1|[0]|Aggregate|null|null|groupKeys: [UNION_ALL(ET.A, EU.A)]|null|null|"
                + "null ;; 1|2|[1]|UnionAll|null|null|null|null|null|null ;; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.ET|"
                + "null|A|1|1|1024 ;; 1|4|[2]|TableScan|TEST_DB.TEST_SCHEMA.EU|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM et UNION SELECT a FROM eu");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|UNION_ALL(ET.A, "
                + "EU.A), UNION_ALL(ET.B, EU.B)|null|null|null ;; 1|1|[0]|UnionAll|null|null|null|null|null|null ;; 1|"
                + "2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|A, B|1|1|1024 ;; 1|3|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EU|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a, b FROM et UNION ALL SELECT a, b FROM eu");
        assertAnswer("null|null|null|GlobalStats|null|null|null|3|3|3072 ;; 1|0|null|Result|null|null|UNION_ALL(ET.A, "
                + "EU.A, EW.A)|null|null|null ;; 1|1|[0]|UnionAll|null|null|null|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024 ;; 1|3|[1]|TableScan|TEST_DB.TEST_SCHEMA.EU|null|A|1|1|1024 "
                + ";; 1|4|[1]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM et UNION ALL SELECT a FROM eu UNION ALL SELECT a FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A|null|null|"
                + "null ;; 1|1|[0]|Aggregate|null|null|groupKeys: [ET.A]|null|null|null ;; 1|2|[1]|Aggregate|null|"
                + "null|groupKeys: [ET.A]|null|null|null ;; 1|3|[2]|SemiJoin|null|null|joinKey: (EU.A = ET.A)|null|"
                + "null|null ;; 1|4|[3]|Aggregate|null|null|groupKeys: [EU.A]|null|null|null ;; 1|5|[4]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EU|null|A|1|1|1024 ;; 1|6|[3]|Aggregate|null|null|groupKeys: [ET.A]|null|null|"
                + "null ;; 1|7|[6]|JoinFilter|null|null|joinKey: (EU.A = ET.A)|null|null|null ;; 1|8|[7]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM et INTERSECT SELECT a FROM eu");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A|null|null|"
                + "null ;; 1|1|[0]|Aggregate|null|null|groupKeys: [ET.A]|null|null|null ;; 1|2|[1]|Aggregate|null|"
                + "null|groupKeys: [ET.A]|null|null|null ;; 1|3|[2]|AntiJoin|null|null|joinKey: (EU.A = ET.A)|null|"
                + "null|null ;; 1|4|[3]|Aggregate|null|null|groupKeys: [EU.A]|null|null|null ;; 1|5|[4]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EU|null|A|1|1|1024 ;; 1|6|[3]|Aggregate|null|null|groupKeys: [ET.A]|null|null|"
                + "null ;; 1|7|[6]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM et EXCEPT SELECT a FROM eu");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A, EW.B|null|"
                + "null|null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT x.a, x.s FROM (SELECT a, b AS s FROM ew) x");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|SUM(EW.A)|null|"
                + "null|null ;; 1|1|[0]|Aggregate|null|null|aggExprs: [SUM(EW.A)]|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT s FROM (SELECT SUM(a) AS s FROM ew)");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|EW.A > 1|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew_v");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|EW.B = 'x'|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR WITH c AS (SELECT a, b FROM ew) SELECT a FROM c WHERE b = 'x'");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A, EU.B|null|"
                + "null|null ;; 1|1|[0]|LeftOuterJoin|null|null|joinKey: (EU.A = ET.A)|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|null|A, B|1|1|1024 ;; 1|3|[1]|Filter|null|null|ET.A IS NOT NULL|"
                + "null|null|null ;; 1|4|[3]|JoinFilter|null|null|joinKey: (EU.A = ET.A)|null|null|null ;; 1|5|[4]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT et.a, eu.b FROM et RIGHT JOIN eu ON et.a = eu.a");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A, EU.B|null|"
                + "null|null ;; 1|1|[0]|FullOuterJoin|null|null|joinKey: (EU.A = ET.A)|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|null|A, B|1|1|1024 ;; 1|3|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET|"
                + "null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT et.a, eu.b FROM et FULL JOIN eu ON et.a = eu.a");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A, EU.B|null|"
                + "null|null ;; 1|1|[0]|CartesianJoin|null|null|null|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EU|null|B|1|1|1024 ;; 1|3|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT et.a, eu.b FROM et CROSS JOIN eu");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A|null|null|"
                + "null ;; 1|1|[0]|CartesianJoin|null|null|joinFilter: ET.A < EU.A|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|null|A|1|1|1024 ;; 1|3|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|"
                + "A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT et.a FROM et JOIN eu ON et.a < eu.a");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A|null|null|"
                + "null ;; 1|1|[0]|InnerJoin|null|null|joinKey: (EU.A = ET.A)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EU|null|A|1|1|1024 ;; 1|3|[1]|JoinFilter|null|null|joinKey: (EU.A = ET.A)|null|"
                + "null|null ;; 1|4|[3]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM et JOIN eu USING (a)");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A|null|null|"
                + "null ;; 1|1|[0]|InnerJoin|null|null|joinKey: (EU.A = ET.A) AND (EU.B = ET.B)|null|null|null ;; 1|2|"
                + "[1]|TableScan|TEST_DB.TEST_SCHEMA.EU|null|A, B|1|1|1024 ;; 1|3|[1]|JoinFilter|null|null|joinKey: "
                + "(EU.A = ET.A) AND (EU.B = ET.B)|null|null|null ;; 1|4|[3]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|A, "
                + "B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT et.a FROM et JOIN eu ON et.a = eu.a AND et.b = eu.b");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A, EW.B|null|"
                + "null|null ;; 1|1|[0]|Aggregate|null|null|groupKeys: [EW.A, EW.B]|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT DISTINCT a, b FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|COUNT(DISTINCT "
                + "EW.A), SUM(DISTINCT EW.A)|null|null|null ;; 1|1|[0]|Aggregate|null|null|aggExprs: [COUNT(DISTINCT "
                + "EW.A), SUM(DISTINCT EW.A)]|null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A|1|1|"
                + "1024",
            "EXPLAIN USING TABULAR SELECT COUNT(DISTINCT a), SUM(DISTINCT a) FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A, EW.B, "
                + "EW.C, EW.D|null|null|null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, B, C, D|1|1|1024",
            "EXPLAIN USING TABULAR SELECT ew.* FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A, EW.C, "
                + "EW.D|null|null|null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, C, D|1|1|1024",
            "EXPLAIN USING TABULAR SELECT * EXCLUDE (b) FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A, ET.B, "
                + "EU.A, EU.B|null|null|null ;; 1|1|[0]|InnerJoin|null|null|joinKey: (EU.A = ET.A)|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.EU|null|A, B|1|1|1024 ;; 1|3|[1]|JoinFilter|null|null|"
                + "joinKey: (EU.A = ET.A)|null|null|null ;; 1|4|[3]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|A, B|1|1|"
                + "1024",
            "EXPLAIN USING TABULAR SELECT * FROM et, eu WHERE et.a = eu.a");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|E.A, E.B|null|"
                + "null|null ;; 1|1|[0]|InnerJoin|null|null|joinKey: (U.A = E.A)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EU|U|A|1|1|1024 ;; 1|3|[1]|JoinFilter|null|null|joinKey: (U.A = E.A)|null|null|"
                + "null ;; 1|4|[3]|TableScan|TEST_DB.TEST_SCHEMA.ET|E|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT e.* FROM et e JOIN eu u ON e.a = u.a");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|UPPER(EW.B)|"
                + "null|null|null ;; 1|1|[0]|Filter|null|null|(UPPER(EW.B)) = 'X'|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT UPPER(b) AS u FROM ew WHERE UPPER(b) = 'X'");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|(EW.C = 1.5) AND (EW.D = '2024-01-01')|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, C, D|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE c = 1.5 AND d = '2024-01-01'");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|SortWithLimit|null|null|sortKey: [EW.C DESC NULLS LAST], rowCount: 3|null|null|"
                + "null ;; 1|2|[1]|Filter|null|null|EW.B = 'x'|null|null|null ;; 1|3|[2]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A, B, C|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE b = 'x' ORDER BY c DESC NULLS LAST LIMIT 3");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT \"A\" FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|EW.A = 1|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE a IN (1)");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|CASE EW.A WHEN "
                + "1 THEN 'one' WHEN 2 THEN 'two' ELSE null END|null|null|null ;; 1|1|[0]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT CASE a WHEN 1 THEN 'one' WHEN 2 THEN 'two' END FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|"
                + "CASE_FLATTENED(EW.A = 1, 'one', null)|null|null|null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.EW|"
                + "null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT CASE WHEN a = 1 THEN 'one' END FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.B|null|null|"
                + "null ;; 1|1|[0]|Sort|null|null|COUNT(*) DESC NULLS FIRST|null|null|null ;; 1|2|[1]|Aggregate|null|"
                + "null|aggExprs: [COUNT(*)], groupKeys: [EW.B]|null|null|null ;; 1|3|[2]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT b FROM ew GROUP BY b ORDER BY COUNT(*) DESC");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ARRAY_AGG(EW.A),"
                + " LISTAGG(EW.B)|null|null|null ;; 1|1|[0]|Aggregate|null|null|aggExprs: [ARRAY_AGG(EW.A), "
                + "LISTAGG(EW.B)]|null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT ARRAY_AGG(a), LISTAGG(b, ',') FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|EW.A IN 1 IN 2 IN 5|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE a = 1 OR a = 2 OR a = 5");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|NOT(EW.A = 1)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE NOT a = 1");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|EW.B IN 'x' IN 'q'|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE b IN ('x', 'q')");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A, "
                + "(SUM(EW.C)) / (COUNT(EW.C))|null|null|null ;; 1|1|[0]|Aggregate|null|null|aggExprs: [SUM(EW.C), "
                + "COUNT(EW.C)], groupKeys: [EW.A]|null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, "
                + "C|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a, AVG(c) FROM ew GROUP BY a");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|SUBSTR(EW.B, 1, "
                + "2), DATE_ADDDAYSTODATE(1, EW.D), EXTRACT(year from EW.D)|null|null|null ;; 1|1|[0]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|B, D|1|1|1024",
            "EXPLAIN USING TABULAR SELECT SUBSTR(b, 1, 2), DATEADD(day, 1, d), YEAR(d) FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|(EW.C IS NOT NULL) AND (EW.B IS NULL)|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, B, C|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE c IS NOT NULL AND b IS NULL");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|Limit|null|null|rowCount: 0|null|null|null ;; 1|2|[1]|Generator|null|null|0|null|"
                + "null|null",
            "EXPLAIN USING TABULAR SELECT * FROM ew LIMIT 0");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|(EW.A > 1) AND (EW.A < 5) AND (EW.B = 'q')|null|null|null ;; 1|2|"
                + "[1]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE a > 1 AND a < 5 AND b = 'q'");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|((EW.A > 1) OR (EW.B = 'x')) AND ((EW.C < 2) OR (EW.D IS NULL))|"
                + "null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, B, C, D|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE (a > 1 OR b = 'x') AND (c < 2 OR d IS NULL)");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|MAX(EW.A)|null|"
                + "null|null ;; 1|1|[0]|Aggregate|null|null|aggExprs: [MAX(EW.A)]|null|null|null ;; 1|2|[1]|Filter|"
                + "null|null|EW.B = 'x'|null|null|null ;; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT MAX(a) FROM ew WHERE b = 'x'");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|COUNT(*)|null|"
                + "null|null ;; 1|1|[0]|Aggregate|null|null|aggExprs: [COUNT(*)]|null|null|null ;; 1|2|[1]|Filter|"
                + "null|null|EW.B = 'x'|null|null|null ;; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT COUNT(*) FROM ew WHERE b = 'x'");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.B, MAX(EW.A)|"
                + "null|null|null ;; 1|1|[0]|SortWithLimit|null|null|sortKey: [EW.B ASC NULLS LAST], rowCount: 1|null|"
                + "null|null ;; 1|2|[1]|Aggregate|null|null|aggExprs: [MAX(EW.A)], groupKeys: [EW.B]|null|null|null "
                + ";; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT b, MAX(a) FROM ew GROUP BY b ORDER BY b LIMIT 1");
    }

    @Test
    public void moreExpressionForms() {
        engine.execute("CREATE OR REPLACE TABLE et (a INT, b VARCHAR)");
        engine.execute("CREATE OR REPLACE TABLE eu (a INT, b VARCHAR)");
        engine.execute("INSERT INTO et VALUES (1, 'x'), (2, 'y'), (3, 'z')");
        engine.execute("INSERT INTO eu VALUES (1, 'x'), (5, 'q')");
        engine.execute("CREATE OR REPLACE TABLE ew (a INT, b VARCHAR, c NUMBER(10,2), d DATE)");
        engine.execute("INSERT INTO ew VALUES (1, 'x', 1.5, '2024-01-01'), (2, NULL, NULL, NULL), (5, 'q', 2.25, "
            + "'2024-02-01')");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A + (EW.A * "
                + "2)|null|null|null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a + (a * 2) FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.C + (EW.A), "
                + "(EW.A) + EW.C|null|null|null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, C|1|1|1024",
            "EXPLAIN USING TABULAR SELECT c + a, a + c FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|(EW.A) = (EW.C * 2)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A, C|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE a = c * 2");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|(UPPER(EW.B)) = (LOWER(EW.B))|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE UPPER(b) = LOWER(b)");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|(EW.A + 1) > 2|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE a + 1 > 2");
        assertAnswer("REFUSED SQL compilation error: | invalid value [HOUR] for parameter 'HOUR date/time part'",
            "EXPLAIN USING TABULAR SELECT MONTH(d), DAY(d), HOUR(d), DAYOFWEEK(d), QUARTER(d) FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|"
                + "DATE_ADDMONTHSTODATE(1, EW.D), DATE_ADDDAYSTOTIMESTAMP(1, TO_TIMESTAMP(EW.D)), "
                + "DATE_DIFFDATEINDAYS(EW.D, EW.D)|null|null|null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|D|"
                + "1|1|1024",
            "EXPLAIN USING TABULAR SELECT DATEADD(month, 1, d), DATEADD(day, 1, d::TIMESTAMP), DATEDIFF(day, d, d) "
                + "FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|Limit|null|null|rowCount: 0|null|null|null ;; 1|2|[1]|Generator|null|null|0|null|"
                + "null|null",
            "EXPLAIN USING TABULAR SELECT a FROM ew LIMIT 0");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|SortWithLimit|null|null|sortKey: [EW.A ASC NULLS LAST], rowCount: 0|null|null|null "
                + ";; 1|2|[1]|Generator|null|null|0|null|null|null",
            "EXPLAIN USING TABULAR SELECT a FROM ew ORDER BY a LIMIT 0");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|NOT((EW.A >= 2) AND (EW.A <= 3))|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE a NOT BETWEEN 2 AND 3");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|EW.B = 'x'|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE b LIKE 'x'");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|NOT(EW.B ILIKE 'x%')|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE b NOT ILIKE 'x%'");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|SUM(EW.A) OVER "
                + "(ORDER BY EW.A ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|SUM(EW.A) OVER "
                + "(ORDER BY EW.A ASC NULLS LAST)|null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A|1|"
                + "1|1024",
            "EXPLAIN USING TABULAR SELECT SUM(a) OVER (ORDER BY a ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A|null|null|"
                + "null ;; 1|1|[0]|InnerJoin|null|null|joinKey: (EU.A = ET.A), joinFilter: ET.B > EU.B|null|null|null "
                + ";; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.EU|null|A, B|1|1|1024 ;; 1|3|[1]|JoinFilter|null|null|"
                + "joinKey: (EU.A = ET.A), joinFilter: ET.B > EU.B|null|null|null ;; 1|4|[3]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT et.a FROM et JOIN eu ON et.a = eu.a AND et.b > eu.b");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A|null|null|"
                + "null ;; 1|1|[0]|InnerJoin|null|null|joinKey: (EU.A = ET.A), joinFilter: ET.B > EU.B|null|null|null "
                + ";; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.EU|null|A, B|1|1|1024 ;; 1|3|[1]|JoinFilter|null|null|"
                + "joinKey: (EU.A = ET.A), joinFilter: ET.B > EU.B|null|null|null ;; 1|4|[3]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT et.a FROM et, eu WHERE et.a = eu.a AND et.b > eu.b");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A|null|null|"
                + "null ;; 1|1|[0]|LeftOuterJoin|null|null|joinKey: (EU.A = ET.A) AND (EU.B = ET.B)|null|null|null ;; "
                + "1|2|[1]|Filter|null|null|(EU.A IS NOT NULL) AND (EU.B IS NOT NULL)|null|null|null ;; 1|3|[2]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|null|A, B|1|1|1024 ;; 1|4|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET|"
                + "null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT et.a FROM et LEFT JOIN eu ON et.a = eu.a AND et.b = eu.b");
        assertAnswer("null|null|null|GlobalStats|null|null|null|3|3|3072 ;; 1|0|null|Result|null|null|UNION_ALL(ET.A, "
                + "EU.A, EW.A)|null|null|null ;; 1|1|[0]|Aggregate|null|null|groupKeys: [UNION_ALL(ET.A, EU.A, EW.A)]|"
                + "null|null|null ;; 1|2|[1]|UnionAll|null|null|null|null|null|null ;; 1|3|[2]|Aggregate|null|null|"
                + "groupKeys: [ET.A]|null|null|null ;; 1|4|[3]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024 ;; 1|"
                + "5|[2]|Aggregate|null|null|groupKeys: [EU.A]|null|null|null ;; 1|6|[5]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EU|null|A|1|1|1024 ;; 1|7|[2]|Aggregate|null|null|groupKeys: [EW.A]|null|null|"
                + "null ;; 1|8|[7]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM et UNION SELECT a FROM eu UNION SELECT a FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A, "
                + "SCALED_ROUND_INT_DIVIDE(SUM(EW.A), COUNT(EW.A))|null|null|null ;; 1|1|[0]|Filter|null|null|"
                + "(SCALED_ROUND_INT_DIVIDE(SUM(EW.A), COUNT(EW.A))) > 1|null|null|null ;; 1|2|[1]|Aggregate|null|"
                + "null|aggExprs: [SUM(EW.A), COUNT(EW.A)], groupKeys: [EW.A]|null|null|null ;; 1|3|[2]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a, AVG(a) FROM ew GROUP BY a HAVING AVG(a) > 1");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|IFNULL(EW.B, "
                + "'n'), NULLIF(EW.B, 'x'), ZEROIFNULL(EW.C)|null|null|null ;; 1|1|[0]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|B, C|1|1|1024",
            "EXPLAIN USING TABULAR SELECT COALESCE(b, 'n', 'm'), NULLIF(b, 'x'), ZEROIFNULL(c) FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A, EW.C, "
                + "TO_BOOLEAN(EW.B), TO_TIMESTAMP(EW.D), TO_DOUBLE(EW.A), TO_CHAR(EW.C), TRY_TO_DATE(EW.B)|null|null|"
                + "null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, B, C, D|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a::NUMBER(10,2), c::INT, b::BOOLEAN, d::TIMESTAMP, a::FLOAT, c::VARCHAR(5),"
                + " TRY_CAST(b AS DATE) FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|EW.D > '2024-01-15'|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A, D|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE d > '2024-01-15'::DATE");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ROUND(EW.C, 1), "
                + "ABS(EW.A), LENGTH(EW.B), TRIM(EW.B)|null|null|null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.EW|"
                + "null|A, B, C|1|1|1024",
            "EXPLAIN USING TABULAR SELECT ROUND(c, 1), ABS(a), LENGTH(b), TRIM(b) FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.B, SUM(EW.A)|"
                + "null|null|null ;; 1|1|[0]|GroupingSets|null|null|SUM(EW.A)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT b, SUM(a) FROM ew GROUP BY ROLLUP (b)");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A, SUM(EW.A) "
                + "OVER (PARTITION BY EW.B)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|SUM(EW.A) OVER "
                + "(PARTITION BY EW.B)|null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a, SUM(a) OVER (PARTITION BY b) FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A, "
                + "FIRST_VALUE(EW.A) OVER (PARTITION BY EW.B ORDER BY EW.A ASC NULLS LAST)|null|null|null ;; 1|1|[0]|"
                + "WindowFunction|null|null|FIRST_VALUE(EW.A) OVER (PARTITION BY EW.B ORDER BY EW.A ASC NULLS LAST)|"
                + "null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a, FIRST_VALUE(a) OVER (PARTITION BY b ORDER BY a) FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|(EW.A > 1) AND (EW.B = 'q')|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM (SELECT a, b FROM ew WHERE a > 1) x WHERE x.b = 'q'");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|SortWithLimit|null|null|sortKey: [EW.A ASC NULLS LAST], rowCount: 2|null|null|null "
                + ";; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT x.a FROM (SELECT a FROM ew ORDER BY a LIMIT 2) x");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a, NULL FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|EW.A IN 1 IN null|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE a IN (1, NULL)");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A, EU.A|null|"
                + "null|null ;; 1|1|[0]|InnerJoin|null|null|joinKey: (EU.A = ET.A)|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|null|A|1|1|1024 ;; 1|3|[1]|JoinFilter|null|null|joinKey: (EU.A = "
                + "ET.A)|null|null|null ;; 1|4|[3]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT et.a, eu.a FROM et INNER JOIN eu ON eu.a = et.a");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A|null|null|"
                + "null ;; 1|1|[0]|InnerJoin|null|null|joinKey: (EU.A = ET.A + 1)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EU|null|A|1|1|1024 ;; 1|3|[1]|JoinFilter|null|null|joinKey: (EU.A = ET.A + 1)|"
                + "null|null|null ;; 1|4|[3]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT et.a FROM et JOIN eu ON et.a + 1 = eu.a");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|1|1024 ;; 1|0|null|Result|null|null|EU.A|null|null|"
                + "null ;; 1|1|[0]|CartesianJoin|null|null|null|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EU|null|A|1|1|1024 ;; 1|3|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|null|1|0|0",
            "EXPLAIN USING TABULAR SELECT eu.a FROM eu CROSS JOIN et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|0|0 ;; 1|0|null|Result|null|null|COUNT(*)|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|COUNT(*) > 1|null|null|null ;; 1|2|[1]|Aggregate|null|null|"
                + "aggExprs: [COUNT(*)]|null|null|null ;; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|null|1|0|0",
            "EXPLAIN USING TABULAR SELECT COUNT(*) FROM ew HAVING COUNT(*) > 1");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Sort|null|null|EW.B ASC NULLS LAST|null|null|null ;; 1|2|[1]|Filter|null|null|EW.A "
                + "> 1|null|null|null ;; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE a > 1 ORDER BY b");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|Limit|null|null|rowCount: 0|null|null|null ;; 1|2|[1]|Generator|null|null|0|null|"
                + "null|null",
            "EXPLAIN USING TABULAR SELECT TOP 0 a FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|IFF(EW.B IS "
                + "NULL, 0, 1), EW.B IS NULL|null|null|null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|B|1|1|"
                + "1024",
            "EXPLAIN USING TABULAR SELECT IFF(b IS NULL, 0, 1), b IS NULL FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A, EW.B|null|"
                + "null|null ;; 1|1|[0]|SortWithLimit|null|null|sortKey: [EW.A ASC NULLS LAST], rowCount: 2|null|null|"
                + "null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a, b FROM ew ORDER BY 1 LIMIT 2");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.B|null|null|"
                + "null ;; 1|1|[0]|Sort|null|null|EW.B ASC NULLS LAST|null|null|null ;; 1|2|[1]|Aggregate|null|null|"
                + "groupKeys: [EW.B]|null|null|null ;; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT DISTINCT b FROM ew ORDER BY b");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A, SUM(EW.C)|"
                + "null|null|null ;; 1|1|[0]|SortWithLimit|null|null|sortKey: [SUM(EW.C) DESC NULLS FIRST], rowCount: "
                + "1|null|null|null ;; 1|2|[1]|Aggregate|null|null|aggExprs: [SUM(EW.C)], groupKeys: [EW.A]|null|null|"
                + "null ;; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, C|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a, SUM(c) FROM ew GROUP BY a ORDER BY SUM(c) DESC LIMIT 1");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A - 1, EW.A "
                + "* EW.A, (EW.C) * (EW.C), NEGATE(EW.C), SCALED_ROUND_INT_DIVIDE(EW.A, EW.A), (EW.C) / (EW.A)|null|"
                + "null|null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, C|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a - 1, a * a, c * c, -c, a / a, c / a FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|'x' || EW.B || "
                + "'y'|null|null|null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT 'x' || b || 'y' FROM ew");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|NOT((EW.A > 1) AND (EW.B = 'x'))|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE NOT (a > 1 AND b = 'x')");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|(EW.A > 1) OR (EW.B IS NULL)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE a > 1 OR b IS NULL");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.B|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|EW.B = 'x'|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT b FROM ew WHERE b IN ('x')");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Filter|null|null|(EW.A IN 1 IN 2) AND (NOT(EW.B IN 'x' IN 'y'))|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE a IN (1, 2) AND b NOT IN ('x', 'y')");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|Limit|null|null|rowCount: 2|null|null|null ;; 1|2|[1]|Filter|null|null|EW.A > 1|"
                + "null|null|null ;; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew WHERE a > 1 LIMIT 2");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A|null|null|"
                + "null ;; 1|1|[0]|SortWithLimit|null|null|sortKey: [EW.A ASC NULLS FIRST, EW.B DESC NULLS LAST], "
                + "rowCount: 2|null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.EW|null|A, B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a FROM ew ORDER BY a NULLS FIRST, b DESC NULLS LAST LIMIT 2");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.B, COUNT(*)|"
                + "null|null|null ;; 1|1|[0]|Aggregate|null|null|aggExprs: [COUNT(*)], groupKeys: [EW.B]|null|null|"
                + "null ;; 1|2|[1]|Filter|null|null|EW.B = 'x'|null|null|null ;; 1|3|[2]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|B|1|1|1024",
            "EXPLAIN USING TABULAR SELECT b, COUNT(*) FROM ew GROUP BY b HAVING b = 'x'");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|EW.A, COUNT(*)|"
                + "null|null|null ;; 1|1|[0]|Sort|null|null|EW.A ASC NULLS LAST|null|null|null ;; 1|2|[1]|Aggregate|"
                + "null|null|aggExprs: [COUNT(*)], groupKeys: [EW.A]|null|null|null ;; 1|3|[2]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.EW|null|A|1|1|1024",
            "EXPLAIN USING TABULAR SELECT a, COUNT(*) FROM ew GROUP BY a ORDER BY a");
    }
}
