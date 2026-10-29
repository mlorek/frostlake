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
 * A SQL function's query body the account refuses at its first word, for a row count that is no count or a window it
 * names: the lines after the first are those of its reading the framed body once more as an expression (live-verified):
 * a parenthesis it stops at is read as an outer-join marker, an operator with its operand, a comma as a list the frame
 * holds, and a nested query that holds the fault as the frame is read.
 */
public class SqlUdfBodyReadingLinesTest extends MeasuredCellsTestSupport {

    /** Bodies whose own LIMIT takes a parameter, their select lists and clauses varied one at a time. */
    @Test
    public void rowCountBodiesAreReadOn() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT MAX(a) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 12 unexpected 'a'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT MAX(+) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 15 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a(+) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT MAX(a) + 1 FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 12 unexpected 'a'. | syntax error line 1 at "
                + "position 19 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT 1 + MAX(a) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 19 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT MAX() FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT f() FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT ROW_NUMBER() OVER (ORDER BY a) "
                + "FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 39 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT COUNT(*) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected '*'. | syntax error line 1 at "
                + "position 15 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT COUNT(DISTINCT a) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected 'DISTINCT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT (a) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 9 unexpected 'a'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT (a + 1) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 9 unexpected 'a'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT ((a)) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 9 unexpected '('."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a, b FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a b FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a AS b FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT -a FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 11 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a + b FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a * b FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT * FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 10 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT t.a FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a::INT FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 15 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT CAST(a AS INT) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected 'a'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT 'x' FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a IN (1, 2) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 20 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a = (1) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 16 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT MAX(a) OVER () FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 12 unexpected 'a'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a, MAX(a) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 18 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a + (1) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 16 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT MAX(a, b) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 12 unexpected 'a'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT MAX((a)) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 12 unexpected '('."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT MAX(1) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 12 unexpected '1'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT CASE WHEN a = 1 THEN 2 END FROM t "
                + "LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 24 unexpected 'THEN'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT IFF(a = 1, 2, 3) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 12 unexpected 'a'. | syntax error line 1 at "
                + "position 17 unexpected ','."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT DISTINCT a FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a (+) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = 1 LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 29 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a > 0 LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 29 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a IN (1, 2) LIMIT "
                + "n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 35 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = 1 AND b = 2 "
                + "LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 39 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t ORDER BY a LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t GROUP BY a LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE (a = 1) LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 24 unexpected 'a'. | syntax error line 1 at "
                + "position 29 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t LIMIT n - 1$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 28 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t LIMIT n * 2$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 28 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t LIMIT n || 'x'$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 31 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t LIMIT n = 1$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 28 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t LIMIT n IS NULL$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 32 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t LIMIT (n)$$",
                "REFUSED SQL compilation error: error line 1 at position 8 | invalid identifier 'A'"},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$WITH x AS (SELECT 1 AS c) SELECT a FROM t "
                + "LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'WITH'. | syntax error line 1 at position 12 unexpected 'SELECT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t, t AS u LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 20 unexpected 'AS'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t LIMIT n OFFSET 1$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'."},
        });
    }

    /** Operators, casts, lists, subqueries and windows ahead of a parameter count. */
    @Test
    public void freshBodiesAreReadOn() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT, c INT)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a, b, c FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 16 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a, b + 1 FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 17 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a, (b) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 15 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a, MAX(b) OVER (ORDER BY a) FROM t "
                + "LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 36 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a::INT + 1 FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 19 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a IS NULL FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 18 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a NOT IN (1) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 21 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT MAX(a) - 1 FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 12 unexpected 'a'. | syntax error line 1 at "
                + "position 19 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT MAX(a)::INT FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 12 unexpected 'a'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT COUNT(*) + 1 FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected '*'. | syntax error line 1 at "
                + "position 15 unexpected ')'. | syntax error line 1 at position 21 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT (a)(+) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 9 unexpected 'a'. | syntax error line 1 at "
                + "position 13 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a(+) + 1 FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 17 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = b(+) LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 32 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = 1 OR b = 2 "
                + "LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 38 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a BETWEEN 1 AND 2 "
                + "LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 41 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a LIKE 'x' LIMIT "
                + "n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 34 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE NOT a = 1 LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT 'x' || a FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 17 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a IN (SELECT 1) "
                + "LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 39 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE EXISTS (SELECT 1) "
                + "LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 31 unexpected 'SELECT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT f(a, b) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 10 unexpected 'a'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT f(a + b) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 10 unexpected 'a'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT f(a + b + c) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 10 unexpected 'a'. | syntax error line 1 at "
                + "position 19 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = 1 LIMIT n + 1$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 29 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t LIMIT -n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 25 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t QUALIFY a = 1 LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 31 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t HAVING COUNT(*) > 1 LIMIT "
                + "n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 30 unexpected '*'. | syntax error line 1 at "
                + "position 31 unexpected ')'. | syntax error line 1 at position 37 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = (1 + 2) LIMIT "
                + "n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 35 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = (a, b) LIMIT "
                + "n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 34 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a IN (1, (SELECT "
                + "2)) LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 44 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT TOP 1 a FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = 1 LIMIT n "
                + "OFFSET 1$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 29 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM (SELECT a FROM t) LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 16 unexpected 'SELECT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t, (SELECT a FROM t) LIMIT "
                + "n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 36 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = 1 AND b IN (1) "
                + "LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 42 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t LIMIT n::INT$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 29 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a::VARCHAR(10) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 23 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = -1 LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 30 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = ABS(b) LIMIT "
                + "n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 34 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT ABS(a) + MAX(b) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 12 unexpected 'a'. | syntax error line 1 at "
                + "position 24 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = 1 WINDOW w AS "
                + "(ORDER BY a)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 29 unexpected 'WINDOW'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a, b FROM t WINDOW w AS (ORDER BY "
                + "a)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected 'FROM'. | syntax error line 1 "
                + "at position 33 unexpected 'ORDER'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT COUNT(*), MAX(a) FROM t WINDOW w "
                + "AS (ORDER BY a)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected '*'. | syntax error line 1 at "
                + "position 15 unexpected ')'. | syntax error line 1 at position 22 unexpected 'a'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE (a) = 1 LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 24 unexpected 'a'. | syntax error line 1 at "
                + "position 31 unexpected 'LIMIT'."},
        });
    }

    /** Sound and faulty subqueries under comparisons, IN and EXISTS, and a list ended by a window. */
    @Test
    public void subqueriesAreReadAsTheyRead() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT, c INT)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t JOIN t u ON t.a = u.a "
                + "LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 39 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t GROUP BY a HAVING a > 1 "
                + "LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 41 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t ORDER BY a + 1 LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 32 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT CASE WHEN a > 1 THEN 1 ELSE 2 END "
                + "FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 24 unexpected 'THEN'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a NOT BETWEEN 1 AND "
                + "2 LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 45 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a NOT LIKE 'x' "
                + "LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 38 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a IS NOT NULL LIMIT "
                + "n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 37 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = (SELECT MAX(a) "
                + "FROM t) LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 50 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = (SELECT MAX(a) "
                + "FROM t LIMIT n)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 28 unexpected 'SELECT'. | syntax error line "
                + "1 at position 39 unexpected 'a'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a IN (SELECT a FROM "
                + "t WHERE b IN (SELECT b FROM t LIMIT n))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax error line "
                + "1 at position 57 unexpected 'SELECT'. | syntax error line 1 at position 81 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT MAX(a + 1) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 12 unexpected 'a'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT MAX(a * 2) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 12 unexpected 'a'. | syntax error line 1 at "
                + "position 17 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a(+) = b(+) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 20 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = 1 AND (b = 2 OR "
                + "b = 3) LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 50 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a, b, MAX(c) OVER w FROM t WINDOW "
                + "w AS (ORDER BY a)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 26 unexpected 'w'. | syntax error line 1 at "
                + "position 59 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a, b c FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected 'c'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a, b FROM t, (SELECT a FROM t "
                + "LIMIT n)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected 'FROM'. | syntax error line 1 "
                + "at position 22 unexpected 'SELECT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT 1 + 2 * 3 FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 18 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT -(a) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a || b || c FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 20 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = 'x' || b LIMIT "
                + "n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 36 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT IFF(a > 1, 1, 0) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 12 unexpected 'a'. | syntax error line 1 at "
                + "position 17 unexpected ','."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT COALESCE(a, b) + 1 FROM t LIMIT "
                + "n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 17 unexpected 'a'. | syntax error line 1 at "
                + "position 27 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t LIMIT n + 1 OFFSET 2$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 29 unexpected 'OFFSET'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t LIMIT 1 OFFSET n + 1$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 37 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT COUNT(*) FROM t WHERE a IN (1, 2) "
                + "LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected '*'. | syntax error line 1 at "
                + "position 15 unexpected ')'. | syntax error line 1 at position 42 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a::NUMBER(10, 2) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 25 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT CAST(a AS INT) + 1 FROM t LIMIT "
                + "n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected 'a'. | syntax error line 1 at "
                + "position 27 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = ANY (SELECT 1) "
                + "LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 42 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE EXISTS (SELECT a "
                + "FROM t LIMIT n)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 31 unexpected 'SELECT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = 1 GROUP BY a "
                + "LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 29 unexpected 'GROUP'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT DISTINCT a + 1 FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 23 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a AS x, b FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 18 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT t.* FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 12 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT (a + 1) * 2 FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 9 unexpected 'a'. | syntax error line 1 at "
                + "position 20 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE (a + 1) = 2 LIMIT "
                + "n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 24 unexpected 'a'. | syntax error line 1 at "
                + "position 35 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = 1 LIMIT n || "
                + "'x'$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 29 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT MAX(a), MIN(b) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 12 unexpected 'a'."},
        });
    }

    /** Faulty queries nested in operands, in lists and in each other. */
    @Test
    public void faultyNestedQueriesAreReadAsTheFrame() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT, c INT)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = (SELECT a FROM "
                + "t LIMIT n) + 1$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 28 unexpected 'SELECT'. | syntax error line "
                + "1 at position 56 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = (SELECT a FROM "
                + "t LIMIT n) AND b = 1$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 28 unexpected 'SELECT'. | syntax error line "
                + "1 at position 62 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = (SELECT "
                + "COUNT(*) FROM t LIMIT n)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 28 unexpected 'SELECT'. | syntax error line "
                + "1 at position 41 unexpected '*'. | syntax error line 1 at position 42 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = (SELECT a + 1 "
                + "FROM t LIMIT n)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 28 unexpected 'SELECT'. | syntax error line "
                + "1 at position 41 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a IN (SELECT a FROM "
                + "t LIMIT n) OR b = 1$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax error line "
                + "1 at position 62 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a IN (1, (SELECT a "
                + "FROM t LIMIT n))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 33 unexpected 'SELECT'. | syntax error line "
                + "1 at position 58 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a, (SELECT a FROM t LIMIT n) FROM "
                + "t$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 12 unexpected 'SELECT'. | syntax error line "
                + "1 at position 37 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a, (SELECT MAX(a) FROM t LIMIT n) "
                + "FROM t$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 12 unexpected 'SELECT'. | syntax error line "
                + "1 at position 23 unexpected 'a'. | syntax error line 1 at position 26 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT 1 + (SELECT a FROM t LIMIT n)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected 'SELECT'. | syntax error line "
                + "1 at position 37 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT 1 + (SELECT MAX(a) FROM t LIMIT n) "
                + "* 2$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected 'SELECT'. | syntax error line "
                + "1 at position 24 unexpected 'a'. | syntax error line 1 at position 46 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = (SELECT a FROM "
                + "(SELECT a FROM t LIMIT n))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 28 unexpected 'SELECT'. | syntax error line "
                + "1 at position 43 unexpected 'SELECT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = (SELECT a FROM "
                + "t WHERE a = (SELECT a FROM t LIMIT n))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 28 unexpected 'SELECT'. | syntax error line "
                + "1 at position 55 unexpected 'SELECT'. | syntax error line 1 at position 79 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE (SELECT a FROM t "
                + "LIMIT n) = 1$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 24 unexpected 'SELECT'. | syntax error line "
                + "1 at position 52 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = (SELECT a FROM "
                + "t LIMIT -1)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 28 unexpected 'SELECT'. | syntax error line "
                + "1 at position 52 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = (SELECT a FROM "
                + "t WINDOW w AS (ORDER BY a))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 28 unexpected 'SELECT'. | syntax error line "
                + "1 at position 57 unexpected 'ORDER'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = 1 + (SELECT a "
                + "FROM t LIMIT n)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 32 unexpected 'SELECT'. | syntax error line "
                + "1 at position 56 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE b NOT IN (SELECT a "
                + "FROM t LIMIT n)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 33 unexpected 'SELECT'. | syntax error line "
                + "1 at position 57 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a > ALL (SELECT a "
                + "FROM t LIMIT n)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 32 unexpected 'SELECT'. | syntax error line "
                + "1 at position 56 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT CASE WHEN a = 1 THEN 2 END + 1 "
                + "FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 24 unexpected 'THEN'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = 1 AND NOT b = 2 "
                + "LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 43 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = (1) LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 31 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = ((1)) LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 33 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t WHERE a = (a + (b)) LIMIT "
                + "n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 37 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a, b FROM t WHERE a = 1$$",
                "REFUSED Declared return type 'NUMBER(38,0)' is incompatible with actual return type "
                + "'ROW(NUMBER(38,0), NUMBER(38,0))'"},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a, b FROM t ORDER BY a LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a, MAX(b) FROM t GROUP BY a LIMIT "
                + "n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 18 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a, b, (SELECT 1) FROM t LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 25 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t, t u WHERE t.a = u.a "
                + "LIMIT n$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 20 unexpected 'u'. | syntax error line 1 at "
                + "position 38 unexpected 'LIMIT'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t LIMIT n * (2)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 30 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fbody(n NUMBER) RETURNS NUMBER AS $$SELECT a FROM t LIMIT (n) + 1$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 24 unexpected 'n'. | syntax error line 1 at "
                + "position 30 unexpected ')'."},
        });
    }
}
