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
 * A column that is NULL on every row is tagged by the empty interval through a derived table, a common table
 * expression, a view, a set operation, a join and an aggregate's result, whatever its type — a date, a text — and
 * so is what is computed from it by arithmetic, a conversion, a call that passes a NULL on and a CASE none of
 * whose reachable branches holds a value. A column that holds a value keeps the tag its values give
 * (live-verified).
 */
public class DerivedRelationNullTagTest extends MeasuredCellsTestSupport {

    @Test
    public void aColumnOfNullsThroughADerivedRelation() {
        try {
            answer(
                "CREATE OR REPLACE TABLE tn (i INT, d DATE, s VARCHAR, ts TIMESTAMP_NTZ)");
            answer(
                "INSERT INTO tn VALUES (NULL, NULL, NULL, NULL)");
            answer(
                "CREATE OR REPLACE TABLE tw (d DATE)");
            answer(
                "INSERT INTO tw VALUES ('2024-05-06'), (NULL)");
            answer(
                "CREATE OR REPLACE TABLE td (d1 DATE, d2 DATE, d3 DATE, d4 DATE, d5 DATE, d6 DATE, ts "
                    + "TIMESTAMP_NTZ)");
            answer(
                "INSERT INTO td VALUES ('2024-01-10', '2024-05-01', '2023-12-30', '2024-01-10', '2023-01-05', "
                    + "'0100-01-01', '2024-01-01 10:20:30'), ('2024-03-01', '2024-06-01', '2024-01-05', '2024-12-20',"
                    + " '2024-01-10', '0120-06-01', '2024-03-31 23:59:59')");
            answer(
                "CREATE OR REPLACE VIEW vn AS SELECT d, s FROM tn");
            answer(
                "CREATE OR REPLACE TABLE tsrc (c NUMBER(10,4))");
            answer(
                "INSERT INTO tsrc VALUES (1.5), (120.25)");
            answer(
                "CREATE OR REPLACE FUNCTION tf_lit() RETURNS TABLE (a VARCHAR, b NUMBER) AS $$ SELECT 'abc', 1 $$");
            answer(
                "CREATE OR REPLACE FUNCTION t_num() RETURNS TABLE (a NUMBER(5,2)) AS $$ SELECT 1.26 $$");
            answer(
                "CREATE OR REPLACE FUNCTION t_nu() RETURNS TABLE (a NUMBER(5,2)) AS $$ SELECT NULL::NUMBER(5,2) $$");
            answer(
                "CREATE OR REPLACE FUNCTION t_src2() RETURNS TABLE (a NUMBER(5,2)) AS $$ SELECT c FROM tsrc $$");
            answer(
                "CREATE OR REPLACE FUNCTION t_same() RETURNS TABLE (a NUMBER(10,4)) AS $$ SELECT c FROM tsrc $$");
            answer(
                "CREATE OR REPLACE FUNCTION t_div(n INT) RETURNS TABLE (a INT) AS $$ SELECT n $$");
            assertAnswer(
                "NUMBER(4,0)[SB2]|NUMBER(4,0)[SB1]|NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)), SYSTEM$TYPEOF(DAYOFYEAR(d)), SYSTEM$TYPEOF(MONTH(d)) FROM tw "
                    + "LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]|NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)), SYSTEM$TYPEOF(ASCII(s)) FROM (SELECT d, s FROM tn)");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(dd)) FROM (SELECT d AS dd FROM tn)");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "WITH c AS (SELECT d FROM tn) SELECT SYSTEM$TYPEOF(YEAR(d)) FROM c");
            assertAnswer(
                "NUMBER(4,0)[SB1]|NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)), SYSTEM$TYPEOF(ASCII(s)) FROM vn");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT d FROM tn WHERE i IS NULL)");
            assertAnswer(
                "NUMBER(4,0)[SB1] ;; NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT d FROM tn UNION ALL SELECT d FROM tn)");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT MAX(d) AS d FROM tn)");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT d FROM tn LIMIT 1)");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT DISTINCT d FROM tn)");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT d FROM tn ORDER BY d)");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(x.d)) FROM (SELECT d FROM tn) x JOIN tw y ON TRUE LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT NULL::DATE AS d)");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT d + 1 AS d FROM tn)");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT * FROM tn)");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT * FROM (SELECT d FROM tn))");
            assertAnswer(
                "NUMBER(4,0)[SB1]|NUMBER(2,0)[SB1]|NUMBER(4,0)[SB2]|NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(d)), SYSTEM$TYPEOF(MONTH(d)), SYSTEM$TYPEOF(YEAR(d)), "
                    + "SYSTEM$TYPEOF(DAY(d)) FROM tw LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT d FROM tw WHERE d IS NULL)");
            assertAnswer(
                "NUMBER(4,0)[SB1] ;; NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(d)) FROM (SELECT d FROM tw)");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS tf_lit()");
            answer(
                "DROP FUNCTION IF EXISTS t_num()");
            answer(
                "DROP FUNCTION IF EXISTS t_nu()");
            answer(
                "DROP FUNCTION IF EXISTS t_src2()");
            answer(
                "DROP FUNCTION IF EXISTS t_same()");
            answer(
                "DROP FUNCTION IF EXISTS t_div(INT)");
        }
    }

    @Test
    public void nullsOfOtherTypesAndSetOperations() {
        try {
            answer(
                "CREATE OR REPLACE TABLE tn (i INT, d DATE, s VARCHAR, ts TIMESTAMP_NTZ)");
            answer(
                "INSERT INTO tn VALUES (NULL, NULL, NULL, NULL)");
            answer(
                "CREATE OR REPLACE TABLE tw (d DATE)");
            answer(
                "INSERT INTO tw VALUES ('2024-05-06'), (NULL)");
            answer(
                "CREATE OR REPLACE TABLE td (d1 DATE, d2 DATE, d3 DATE, d4 DATE, d5 DATE, d6 DATE, ts "
                    + "TIMESTAMP_NTZ)");
            answer(
                "INSERT INTO td VALUES ('2024-01-10', '2024-05-01', '2023-12-30', '2024-01-10', '2023-01-05', "
                    + "'0100-01-01', '2024-01-01 10:20:30'), ('2024-03-01', '2024-06-01', '2024-01-05', '2024-12-20',"
                    + " '2024-01-10', '0120-06-01', '2024-03-31 23:59:59')");
            answer(
                "CREATE OR REPLACE VIEW vn AS SELECT d, s FROM tn");
            answer(
                "CREATE OR REPLACE TABLE tsrc (c NUMBER(10,4))");
            answer(
                "INSERT INTO tsrc VALUES (1.5), (120.25)");
            answer(
                "CREATE OR REPLACE FUNCTION tf_lit() RETURNS TABLE (a VARCHAR, b NUMBER) AS $$ SELECT 'abc', 1 $$");
            answer(
                "CREATE OR REPLACE FUNCTION t_num() RETURNS TABLE (a NUMBER(5,2)) AS $$ SELECT 1.26 $$");
            answer(
                "CREATE OR REPLACE FUNCTION t_nu() RETURNS TABLE (a NUMBER(5,2)) AS $$ SELECT NULL::NUMBER(5,2) $$");
            answer(
                "CREATE OR REPLACE FUNCTION t_src2() RETURNS TABLE (a NUMBER(5,2)) AS $$ SELECT c FROM tsrc $$");
            answer(
                "CREATE OR REPLACE FUNCTION t_same() RETURNS TABLE (a NUMBER(10,4)) AS $$ SELECT c FROM tsrc $$");
            answer(
                "CREATE OR REPLACE FUNCTION t_div(n INT) RETURNS TABLE (a INT) AS $$ SELECT n $$");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(ts)) FROM tn");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(ts)) FROM (SELECT ts FROM tn)");
            assertAnswer(
                "NUMBER(18,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(LENGTH(s)) FROM (SELECT s FROM tn)");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(i + 1) FROM (SELECT i FROM tn)");
            assertAnswer(
                "NUMBER(4,0)[SB1] ;; NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(s)) FROM (SELECT s FROM tn UNION ALL SELECT s FROM tn)");
            assertAnswer(
                "NUMBER(4,0)[SB2] ;; NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT d FROM tn UNION ALL SELECT '2024-01-01'::DATE)");
            assertAnswer(
                "NUMBER(4,0)[SB1] ;; NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT CASE WHEN FALSE THEN d END AS d FROM tw)");
            assertAnswer(
                "NUMBER(4,0)[SB2] ;; NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT NULLIF(d, d) AS d FROM tw)");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS tf_lit()");
            answer(
                "DROP FUNCTION IF EXISTS t_num()");
            answer(
                "DROP FUNCTION IF EXISTS t_nu()");
            answer(
                "DROP FUNCTION IF EXISTS t_src2()");
            answer(
                "DROP FUNCTION IF EXISTS t_same()");
            answer(
                "DROP FUNCTION IF EXISTS t_div(INT)");
        }
    }

    @Test
    public void whatIsComputedFromAColumnOfNulls() {
        try {
            answer(
                "CREATE OR REPLACE TABLE td (d1 DATE, d6 DATE, ts TIMESTAMP_NTZ, t6 TIMESTAMP_NTZ, lz "
                    + "TIMESTAMP_LTZ, tz TIMESTAMP_TZ)");
            answer(
                "INSERT INTO td VALUES ('2024-01-10', '0100-01-01', '2024-01-01 10:20:30', '0100-01-01 00:00:00', "
                    + "'0100-01-01 00:00:00', '0100-01-01 00:00:00 +00:00'), ('2024-03-01', '0120-06-01', "
                    + "'2024-03-31 23:59:59', '0120-06-01 00:00:00', '0120-06-01 00:00:00', '0120-06-01 00:00:00 "
                    + "+00:00')");
            answer(
                "CREATE OR REPLACE TABLE tn (i INT, d DATE, s VARCHAR, ts TIMESTAMP_NTZ)");
            answer(
                "INSERT INTO tn VALUES (NULL, NULL, NULL, NULL)");
            answer(
                "CREATE OR REPLACE TABLE tw (d DATE)");
            answer(
                "INSERT INTO tw VALUES ('2024-05-06'), (NULL)");
            answer(
                "CREATE OR REPLACE TABLE tm (d DATE)");
            answer(
                "INSERT INTO tm VALUES ('0100-01-01'), ('0120-06-01'), ('2024-05-06')");
            answer(
                "CREATE OR REPLACE TABLE ti (i INT)");
            answer(
                "INSERT INTO ti VALUES (3), (300)");
            answer(
                "CREATE OR REPLACE VIEW vd AS SELECT d6, d1 FROM td");
            answer(
                "CREATE OR REPLACE FUNCTION t_mul(n INT) RETURNS TABLE (a INT) AS $$ SELECT n * 1000 $$");
            answer(
                "CREATE OR REPLACE FUNCTION t_div(n INT) RETURNS TABLE (a INT) AS $$ SELECT n $$");
            answer(
                "CREATE OR REPLACE FUNCTION t_d() RETURNS TABLE (a DATE) AS $$ SELECT d6 FROM td $$");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(UPPER(s))) FROM tn");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d::TIMESTAMP_NTZ)) FROM tn");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(dd)) FROM (SELECT d::TIMESTAMP_NTZ AS dd FROM tn)");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(x)) FROM (SELECT s || 'x' AS x FROM tn)");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(x)) FROM (SELECT UPPER(s) AS x FROM tn)");
            assertAnswer(
                "NUMBER(4,0)[SB1] ;; NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(x)) FROM (SELECT CASE WHEN TRUE THEN NULL ELSE d END AS x FROM tw)");
            assertAnswer(
                "NUMBER(4,0)[SB1] ;; NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(x)) FROM (SELECT IFF(FALSE, d, NULL) AS x FROM tw)");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(x)) FROM (SELECT COALESCE(d, d) AS x FROM tn)");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(x)) FROM (SELECT CASE WHEN i = 1 THEN d END AS x FROM tn)");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(x)) FROM (SELECT MIN(d) AS x FROM tn GROUP BY i)");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(x)) FROM (SELECT ANY_VALUE(d) AS x FROM tn)");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(x)) FROM (SELECT LAG(d) OVER (ORDER BY i) AS x FROM tn)");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(a.x)) FROM (SELECT d AS x FROM tn) a JOIN (SELECT i FROM tn) b ON TRUE");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(a.d)) FROM tn a LEFT JOIN tn b ON a.i = b.i");
            assertAnswer(
                "1",
                "INSERT INTO tn VALUES (1, '2024-01-01', 'a', '2024-01-01 00:00:00')");
            assertAnswer(
                "NUMBER(4,0)[SB2]|NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)), SYSTEM$TYPEOF(ASCII(s)) FROM (SELECT d, s FROM tn) LIMIT 1");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS t_mul(INT)");
            answer(
                "DROP FUNCTION IF EXISTS t_div(INT)");
            answer(
                "DROP FUNCTION IF EXISTS t_d()");
        }
    }
}
