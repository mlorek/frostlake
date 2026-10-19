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
 * A year and a day of the year read from a DATE, TIMESTAMP_NTZ or TIMESTAMP_LTZ column are tagged by the least
 * and the greatest day the column's table holds, whatever the statement filters: the calendar's year and a
 * week's, and a day of the year while both days stand in one year. The days are read through MIN, MAX and
 * ANY_VALUE, a derived relation, a conversion to a DATE or to a timestamp that keeps no offset of its own, a
 * DATE moved by a whole number of days, DATEADD, DATE_TRUNC and LAST_DAY, and what COALESCE, IFF, GREATEST and
 * LEAST choose; they follow the table as it changes. A TIMESTAMP_TZ column is read only through a cast to a
 * DATE (live-verified).
 */
public class DateColumnStatisticsTagTest extends MeasuredCellsTestSupport {

    @Test
    public void aDayOfTheYearAndAYear() {
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
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(d1)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(d2)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(d3)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(d4)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(d5)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(d6)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(d1)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d6)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(d5)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(MONTH(d1)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAY(d1)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(QUARTER(d4)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(WEEK(d4)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFWEEK(d1)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(WEEKOFYEAR(d1)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(ts)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(HOUR(ts)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(ts)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(dayofyear, d1)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(dayofyear FROM d2)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(5,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(d1) + 1) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(5,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(d1) * 3) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(d1)) FROM td WHERE d1 < '2024-02-01'");
            assertAnswer(
                "NUMBER(4,0)[SB1] ;; NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(d1)) FROM (SELECT d1 FROM td)");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(MAX(d1))) FROM td");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(d1 + 100)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(DATEADD(day, 100, d1))) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(9,0)[SB4]",
                "SELECT SYSTEM$TYPEOF(DATEDIFF(day, d1, d4)) FROM td LIMIT 1");
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
    public void weekYearsAggregatesAndOtherTimestamps() {
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
                "NUMBER(5,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d6) + 1) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(5,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(d6) * 2) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(5,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(d6) + YEAR(d6)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(8,0)[SB4]",
                "SELECT SYSTEM$TYPEOF(YEAR(d6) * 1000) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(MIN(d1))) FROM td");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(MAX(d6))) FROM td");
            assertAnswer(
                "NUMBER(4,0)[SB1] ;; NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(ANY_VALUE(d6))) FROM td");
            assertAnswer(
                "NUMBER(5,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(MIN(d6)) + 1) FROM td");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAROFWEEK(d6)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAROFWEEKISO(d6)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(year, d6)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(year FROM d6)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('yy', d6)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(yearofweek, d6)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(doy FROM d1)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(t6)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(lz)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(tz)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(t6)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(t6::DATE)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d6::TIMESTAMP_NTZ)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(TO_DATE(d6))) FROM td LIMIT 1");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS t_mul(INT)");
            answer(
                "DROP FUNCTION IF EXISTS t_div(INT)");
            answer(
                "DROP FUNCTION IF EXISTS t_d()");
        }
    }

    @Test
    public void throughDerivedRelations() {
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
                "SELECT SYSTEM$TYPEOF(YEAR(d6)) FROM (SELECT d6 FROM td) LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "WITH c AS (SELECT d6 FROM td) SELECT SYSTEM$TYPEOF(YEAR(d6)) FROM c LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d6)) FROM vd LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT d6 AS d FROM td UNION ALL SELECT d1 FROM td) LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT d6 AS d FROM td UNION ALL SELECT d6 FROM td) LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(x.d6)) FROM td x JOIN td y ON TRUE LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT MAX(d6) AS d FROM td)");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT d6 AS d FROM td WHERE d6 > '0110-01-01')");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT d6 + 1 AS d FROM td) LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT DISTINCT d6 AS d FROM td) LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT d6 AS d FROM td ORDER BY 1 LIMIT 1)");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT d6 AS d FROM td GROUP BY d6) LIMIT 1");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS t_mul(INT)");
            answer(
                "DROP FUNCTION IF EXISTS t_div(INT)");
            answer(
                "DROP FUNCTION IF EXISTS t_d()");
        }
    }

    @Test
    public void theDaysFollowTheTable() {
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
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM tm LIMIT 1");
            assertAnswer(
                "1",
                "DELETE FROM tm WHERE d > '2000-01-01'");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM tm LIMIT 1");
            assertAnswer(
                "1",
                "INSERT INTO tm VALUES ('2024-05-06')");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM tm LIMIT 1");
            assertAnswer(
                "1|0",
                "UPDATE tm SET d = '0110-01-01' WHERE d > '2000-01-01'");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM tm LIMIT 1");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS t_mul(INT)");
            answer(
                "DROP FUNCTION IF EXISTS t_div(INT)");
            answer(
                "DROP FUNCTION IF EXISTS t_d()");
        }
    }

    @Test
    public void conversionsAndMovedDates() {
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
                "CREATE OR REPLACE FUNCTION t_d() RETURNS TABLE (a DATE) AS $$ SELECT d6 FROM td $$");
            answer(
                "CREATE OR REPLACE FUNCTION t_d1() RETURNS TABLE (a DATE) AS $$ SELECT d6 + 1 FROM td $$");
            answer(
                "CREATE OR REPLACE FUNCTION t_ts() RETURNS TABLE (a TIMESTAMP_NTZ) AS $$ SELECT t6 FROM td $$");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(yearofweekiso, d6)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(lz)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(lz::DATE)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(lz::TIMESTAMP_NTZ)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(tz::DATE)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d6::TIMESTAMP_LTZ)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d6::TIMESTAMP)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(d6::TIMESTAMP_TZ)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(DATE(d6))) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(TO_TIMESTAMP(d6))) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(TO_TIMESTAMP_NTZ(d6))) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d6 + 1)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d6 - 1)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(1 + d6)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(d6 + 36500)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d6 - 36500)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(d1 + 1)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(d1 - 9)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(d1 - 10)) FROM td LIMIT 1");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS t_d()");
            answer(
                "DROP FUNCTION IF EXISTS t_d1()");
            answer(
                "DROP FUNCTION IF EXISTS t_ts()");
        }
    }

    @Test
    public void callsThatMoveOrChooseADate() {
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
                "CREATE OR REPLACE FUNCTION t_d() RETURNS TABLE (a DATE) AS $$ SELECT d6 FROM td $$");
            answer(
                "CREATE OR REPLACE FUNCTION t_d1() RETURNS TABLE (a DATE) AS $$ SELECT d6 + 1 FROM td $$");
            answer(
                "CREATE OR REPLACE FUNCTION t_ts() RETURNS TABLE (a TIMESTAMP_NTZ) AS $$ SELECT t6 FROM td $$");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(DATEADD(day, 1, d6))) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(DATEADD(year, 1, d6))) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(DATE_TRUNC(year, d6))) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(LAST_DAY(d6))) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(GREATEST(d6, d6))) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(COALESCE(d6, d1))) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(IFF(TRUE, d6, d1))) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(NVL(d6, d6))) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(x)) FROM (SELECT d6::TIMESTAMP_NTZ AS x FROM td) LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(x)) FROM (SELECT TO_DATE(t6) AS x FROM td) LIMIT 1");
            assertAnswer(
                "NUMBER(5,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(a) + 1) FROM TABLE(t_d()) LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(a)) FROM TABLE(t_d1()) LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(a)) FROM TABLE(t_ts()) LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d6 + 1.0)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d6 + 1 + 1)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR((d6 + 1)::TIMESTAMP_NTZ)) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(TO_DATE(ts))) FROM td LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(ts::DATE + 1)) FROM td LIMIT 1");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS t_d()");
            answer(
                "DROP FUNCTION IF EXISTS t_d1()");
            answer(
                "DROP FUNCTION IF EXISTS t_ts()");
        }
    }
}
