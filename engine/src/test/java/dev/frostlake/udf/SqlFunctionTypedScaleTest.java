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
 * A SQL function's number is held at the scale its call is typed with: the call is typed by the body over its
 * arguments' own types, so fx(n) over a NUMBER(10,2) column holding 1.5, the body n + 0.5, is 2.00 where the
 * same body over the literal 1.5 is 2.0 — with one signature or several, in an expression, a cast and an
 * aggregate (live-verified).
 */
public class SqlFunctionTypedScaleTest extends MeasuredCellsTestSupport {

    @Test
    public void theScaleTheCallIsTypedWith() {
        try {
            answer(
                "CREATE OR REPLACE TABLE tq (n NUMBER(10,2), s VARCHAR)");
            answer(
                "INSERT INTO tq VALUES (1.5, 'ab')");
            answer(
                "CREATE OR REPLACE FUNCTION fo(n NUMBER) RETURNS VARCHAR AS $$ 'num' $$");
            answer(
                "CREATE OR REPLACE FUNCTION fo(s VARCHAR) RETURNS VARCHAR AS $$ 'var' $$");
            answer(
                "CREATE OR REPLACE FUNCTION fw(n NUMBER) RETURNS NUMBER AS $$ n + 0.5 $$");
            answer(
                "CREATE OR REPLACE FUNCTION fw(s VARCHAR) RETURNS VARCHAR AS $$ s || '!' $$");
            answer(
                "CREATE OR REPLACE FUNCTION fx(n NUMBER) RETURNS NUMBER AS 'n + 0.5'");
            answer(
                "CREATE OR REPLACE FUNCTION fy(n NUMBER(10,2)) RETURNS NUMBER(12,4) AS $$ n * 2 $$");
            assertAnswer(
                "num|var|2.00|ab!",
                "SELECT fo(n), fo(s), fw(n), fw(s) FROM tq");
            assertAnswer(
                "2.00",
                "SELECT fx(n) FROM tq");
            assertAnswer(
                "2.00|NUMBER(11,2)[SB2]",
                "SELECT fx(n), SYSTEM$TYPEOF(fx(n)) FROM tq");
            assertAnswer(
                "2.0|1.5|1.7500",
                "SELECT fx(1.5), fx(1), fx(1.25::NUMBER(10,4))");
            assertAnswer(
                "3.00",
                "SELECT fx(n) + 1 FROM tq");
            assertAnswer(
                "2.00",
                "SELECT fx(n)::VARCHAR FROM tq");
            assertAnswer(
                "2.5",
                "SELECT fx(2)");
            assertAnswer(
                "3.00|NUMBER(11,2)[SB2]",
                "SELECT fy(n), SYSTEM$TYPEOF(fy(n)) FROM tq");
            assertAnswer(
                "3.00|4.00",
                "SELECT fy(1.5), fy(2)");
            assertAnswer(
                "2.00|NUMBER(11,2)[SB2]",
                "SELECT fw(n), SYSTEM$TYPEOF(fw(n)) FROM tq");
            assertAnswer(
                "2.00 ;; 1.00",
                "SELECT fx(n) FROM tq UNION ALL SELECT 1");
            assertAnswer(
                "Table TR successfully created.",
                "CREATE OR REPLACE TABLE tr AS SELECT fx(n) AS c FROM tq");
            assertAnswer(
                "2.00|NUMBER(11,2)[SB2]",
                "SELECT c, SYSTEM$TYPEOF(c) FROM tr");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS fo(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS fo(VARCHAR)");
            answer(
                "DROP FUNCTION IF EXISTS fw(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS fw(VARCHAR)");
            answer(
                "DROP FUNCTION IF EXISTS fx(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS fy(NUMBER)");
        }
    }

    @Test
    public void bodiesArgumentsAndAggregates() {
        try {
            answer(
                "CREATE OR REPLACE TABLE tq (n NUMBER(10,2), m NUMBER(10,4), i INT, s VARCHAR)");
            answer(
                "INSERT INTO tq VALUES (1.5, 1.25, 2, 'ab')");
            answer(
                "CREATE OR REPLACE FUNCTION fx(n NUMBER) RETURNS NUMBER AS 'n + 0.5'");
            answer(
                "CREATE OR REPLACE FUNCTION fz(a NUMBER, b NUMBER) RETURNS NUMBER AS 'a * b'");
            answer(
                "CREATE OR REPLACE FUNCTION fc(n NUMBER) RETURNS NUMBER AS '(n + 0.5)::NUMBER(10,3)'");
            answer(
                "CREATE OR REPLACE FUNCTION fd(n NUMBER) RETURNS NUMBER AS 'n / 2'");
            answer(
                "CREATE OR REPLACE FUNCTION ff(n NUMBER) RETURNS FLOAT AS 'n + 0.5'");
            answer(
                "CREATE OR REPLACE FUNCTION fq(n NUMBER) RETURNS NUMBER AS '(SELECT n + 0.5)'");
            answer(
                "CREATE OR REPLACE FUNCTION fi(n NUMBER) RETURNS NUMBER AS 'IFF(n > 1, n, 0)'");
            assertAnswer(
                "1.7500|NUMBER(11,4)[SB2]",
                "SELECT fx(m), SYSTEM$TYPEOF(fx(m)) FROM tq");
            assertAnswer(
                "2.2500|1.875000|4",
                "SELECT fz(n, n), fz(n, m), fz(i, i) FROM tq");
            assertAnswer(
                "2.50",
                "SELECT fx(fx(n)) FROM tq");
            assertAnswer(
                "1.50",
                "SELECT n FROM tq WHERE fx(n) = 2 ORDER BY fx(n)");
            assertAnswer(
                "2.000|2.000|NUMBER(10,3)[SB2]",
                "SELECT fc(n), fc(1.5), SYSTEM$TYPEOF(fc(n)) FROM tq");
            assertAnswer(
                "0.75000000|1.500000|1.000000",
                "SELECT fd(n), fd(3), fd(i) FROM tq");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function FF.",
                "SELECT ff(n) FROM tq");
            assertAnswer(
                "2.5|4.00",
                "SELECT fx(i), fx(i) + n FROM tq");
            assertAnswer(
                "2.00|2.00|2.00000000",
                "SELECT SUM(fx(n)), MAX(fx(n)), AVG(fx(n)) FROM tq");
            assertAnswer(
                "null|0.50",
                "SELECT fx(NULL::NUMBER(10,2)), fx(n * 0) FROM tq");
            assertAnswer(
                "2.00|2.0",
                "SELECT fq(n), fq(1.5) FROM tq");
            assertAnswer(
                "1.50|0.0|1.2500",
                "SELECT fi(n), fi(0.50), fi(m) FROM tq");
            assertAnswer(
                "2.00x|2.000",
                "SELECT fx(n) || 'x', fx(n)::NUMBER(10,3) FROM tq");
            assertAnswer(
                "2.00",
                "SELECT fx(n) AS c FROM tq GROUP BY fx(n)");
            assertAnswer(
                "2.0",
                "SELECT fx(s::NUMBER(5,1)) FROM (SELECT '1.5' AS s)");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS fx(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS fz(NUMBER, NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS fc(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS fd(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS ff(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS fq(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS fi(NUMBER)");
        }
    }
}
