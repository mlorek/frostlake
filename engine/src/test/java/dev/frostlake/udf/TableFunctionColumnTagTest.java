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
package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A SQL table function's column is tagged by the values its body computes, read as if the call were written
 * in place: the body's own interval at the scale the column declares, a constant argument's value, the empty
 * interval for a NULL, and the days a DATE or a timestamp column of the body reads (live-verified).
 */
public class TableFunctionColumnTagTest extends MeasuredCellsTestSupport {

    @Test
    public void theBodysOwnInterval() {
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
                "VARCHAR[LOB]|NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(a), SYSTEM$TYPEOF(b) FROM TABLE(tf_lit())");
            assertAnswer(
                "NUMBER(5,2)[SB1]",
                "SELECT SYSTEM$TYPEOF(a) FROM TABLE(t_num())");
            assertAnswer(
                "NUMBER(5,2)[SB1]",
                "SELECT SYSTEM$TYPEOF(a) FROM TABLE(t_nu())");
            assertAnswer(
                "NUMBER(5,2)[SB2]",
                "SELECT SYSTEM$TYPEOF(a) FROM TABLE(t_src2()) LIMIT 1");
            assertAnswer(
                "NUMBER(10,4)[SB4]",
                "SELECT SYSTEM$TYPEOF(a) FROM TABLE(t_same()) LIMIT 1");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(a) FROM TABLE(t_div(3))");
            assertAnswer(
                "NUMBER(38,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(a) FROM TABLE(t_div(300))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(a + 1) FROM TABLE(t_div(3))");
            assertAnswer(
                "NUMBER(10,4)[SB4]",
                "SELECT SYSTEM$TYPEOF(c) FROM tsrc LIMIT 1");
            assertAnswer(
                "NUMBER(10,4)[SB4]",
                "SELECT SYSTEM$TYPEOF(c) FROM (SELECT c FROM tsrc) LIMIT 1");
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
    public void argumentsAndSetOperations() {
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
                "NUMBER(38,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(a) FROM TABLE(t_mul(3))");
            assertAnswer(
                "NUMBER(38,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(a) FROM TABLE(t_mul(30))");
            assertAnswer(
                "NUMBER(38,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(a + 1) FROM TABLE(t_mul(3))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(a)) FROM TABLE(t_d()) LIMIT 1");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 35 | Invalid argument types for function "
                    + "'T_DIV': (NULL)",
                "SELECT SYSTEM$TYPEOF(a) FROM TABLE(t_div(NULL))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(a) FROM TABLE(t_div(1 + 2))");
            assertAnswer(
                "NUMBER(38,0)[SB1] ;; NUMBER(38,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(a) FROM TABLE(t_div(3)) UNION ALL SELECT SYSTEM$TYPEOF(a) FROM "
                    + "TABLE(t_div(300))");
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
    public void aDateColumnOfTheBody() {
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
                "NUMBER(5,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(a) + 1) FROM TABLE(t_d()) LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(a)) FROM TABLE(t_d1()) LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(a)) FROM TABLE(t_ts()) LIMIT 1");
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
