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
package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * SPLIT_PART's part with a fraction in its type is judged by what the plan knows of its value. A value it knows is
 * folded - one with a fraction refused while the statement compiles, a whole one read as the part it names, past 32
 * bits refused alike - and it knows a literal, what is computed from literals, a scalar subquery it folds, the one
 * value a table's statistics leave an expression over its columns, and a cast or a TO_NUMBER of a FLOAT or a text
 * expression over columns that each hold one value. An integer division and a ROUND, TRUNC, CEIL or FLOOR to a scale
 * fold only to a whole number, a decimal division only to an exact quotient, and what is built over one that does not
 * fold is not folded itself. A NULL beside the value, or a WHERE the statistics prove false, leaves the plan nothing to
 * fold. Any other part is read where the row is, held in 32 bits at its own scale, and there names no part: every row
 * answers the empty text (live-verified).
 */
public class SplitPartFoldedPartTest extends MeasuredCellsTestSupport {

    private static final String NOT_INTEGER =
        "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: ";

    private static final String OUT_OF_RANGE = "REFUSED Number out of representable range: type FIXED[SB4](";

    /** Over one row, and over two that are the same, the statistics leave each column one value. */
    @Test
    public void aValueTheStatisticsLeaveOneIsFolded() {
        answer("CREATE OR REPLACE TABLE t1 (n NUMBER(4,2), f FLOAT, i INT)");
        answer("INSERT INTO t1 VALUES (1.6, 1.6, 2)");
        answer("CREATE OR REPLACE TABLE t2 (n NUMBER(4,2), f FLOAT, i INT)");
        answer("INSERT INTO t2 VALUES (1.6, 1.6, 2), (1.6, 1.6, 2)");
        assertCells(new String[][] {
            {"SELECT SPLIT_PART('a,b,c', ',', i + 0) FROM t1",
                "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', i - 0) FROM t1",
                "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', i * 1) FROM t1",
                "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', i / 1) FROM t1",
                "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', i / 3) FROM t1",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', i % 2) FROM t1",
                "a"},
            {"SELECT SPLIT_PART('a,b,c', ',', n + 0) FROM t1",
                NOT_INTEGER + "'T1.N + (CAST(0 AS NUMBER(4,2)))'"},
            {"SELECT SPLIT_PART('a,b,c', ',', n - 0) FROM t1",
                NOT_INTEGER + "'T1.N - (CAST(0 AS NUMBER(4,2)))'"},
            {"SELECT SPLIT_PART('a,b,c', ',', n * 1) FROM t1",
                NOT_INTEGER + "'CAST(T1.N AS NUMBER(5,2))'"},
            {"SELECT SPLIT_PART('a,b,c', ',', n * 2) FROM t1",
                NOT_INTEGER + "'T1.N * 2'"},
            {"SELECT SPLIT_PART('a,b,c', ',', n / 1) FROM t1",
                NOT_INTEGER + "'(CAST(T1.N AS NUMBER(10,8))) / 1'"},
            {"SELECT SPLIT_PART('a,b,c', ',', n % 1) FROM t1",
                NOT_INTEGER + "'T1.N % (CAST(1 AS NUMBER(4,2)))'"},
            {"SELECT SPLIT_PART('a,b,c', ',', f + 0) FROM t1",
                "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', f * 1) FROM t1",
                "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', f / 1) FROM t1",
                "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', i + 0.5) FROM t1",
                NOT_INTEGER + "'(CAST(T1.I AS NUMBER(38,1))) + 0.5'"},
            {"SELECT SPLIT_PART('a,b,c', ',', i * 0.5) FROM t1",
                "a"},
            {"SELECT SPLIT_PART('a,b,c', ',', i * 1.5) FROM t1",
                "c"},
            {"SELECT SPLIT_PART('a,b,c', ',', i - 0.5) FROM t1",
                NOT_INTEGER + "'(CAST(T1.I AS NUMBER(38,1))) - 0.5'"},
            {"SELECT SPLIT_PART('a,b,c', ',', 1 + 0.5) FROM t1",
                NOT_INTEGER + "'(CAST(1 AS NUMBER(3,1))) + 0.5'"},
            {"SELECT SPLIT_PART('a,b,c', ',', 1.5 * 2) FROM t1",
                "c"},
            {"SELECT SPLIT_PART('a,b,c', ',', 3 * 0.5) FROM t1",
                NOT_INTEGER + "'3 * 0.5'"},
            {"SELECT SPLIT_PART('a,b,c', ',', 2 / 3) FROM t1",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', 4 / 2) FROM t1",
                "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', 5.5 % 2) FROM t1",
                NOT_INTEGER + "'5.5 % (CAST(2 AS NUMBER(3,1)))'"},
            {"SELECT SPLIT_PART('a,b,c', ',', -n) FROM t1",
                NOT_INTEGER + "'NEGATE(T1.N)'"},
            {"SELECT SPLIT_PART('a,b,c', ',', ABS(n)) FROM t1",
                NOT_INTEGER + "'ABS(T1.N)'"},
            {"SELECT SPLIT_PART('a,b,c', ',', ROUND(n, 1)) FROM t1",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', n::NUMBER(4,2)) FROM t1",
                NOT_INTEGER + "'T1.N'"},
            {"SELECT SPLIT_PART('a,b,c', ',', i::NUMBER(4,2)) FROM t1",
                "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', f::NUMBER(4,2)) FROM t1",
                NOT_INTEGER + "'1.6'"},
            {"SELECT SPLIT_PART('a,b,c', ',', IFF(TRUE, n, n)) FROM t1",
                NOT_INTEGER + "'IFF(CAST(TRUE AS BOOLEAN), T1.N, T1.N)'"},
            {"SELECT SPLIT_PART('a,b,c', ',', COALESCE(n, 0)) FROM t1",
                NOT_INTEGER + "'IFNULL(T1.N, CAST(0 AS NUMBER(4,2)))'"},
            {"SELECT SPLIT_PART('a,b,c', ',', i + 0) FROM t2",
                "b ;; b"},
            {"SELECT SPLIT_PART('a,b,c', ',', i - 0) FROM t2",
                "b ;; b"},
            {"SELECT SPLIT_PART('a,b,c', ',', i * 1) FROM t2",
                "b ;; b"},
            {"SELECT SPLIT_PART('a,b,c', ',', i / 1) FROM t2",
                "b ;; b"},
            {"SELECT SPLIT_PART('a,b,c', ',', i / 3) FROM t2",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', i % 2) FROM t2",
                "a ;; a"},
            {"SELECT SPLIT_PART('a,b,c', ',', n + 0) FROM t2",
                NOT_INTEGER + "'T2.N + (CAST(0 AS NUMBER(4,2)))'"},
            {"SELECT SPLIT_PART('a,b,c', ',', n - 0) FROM t2",
                NOT_INTEGER + "'T2.N - (CAST(0 AS NUMBER(4,2)))'"},
            {"SELECT SPLIT_PART('a,b,c', ',', n * 1) FROM t2",
                NOT_INTEGER + "'CAST(T2.N AS NUMBER(5,2))'"},
            {"SELECT SPLIT_PART('a,b,c', ',', n * 2) FROM t2",
                NOT_INTEGER + "'T2.N * 2'"},
            {"SELECT SPLIT_PART('a,b,c', ',', n / 1) FROM t2",
                NOT_INTEGER + "'(CAST(T2.N AS NUMBER(10,8))) / 1'"},
            {"SELECT SPLIT_PART('a,b,c', ',', n % 1) FROM t2",
                NOT_INTEGER + "'T2.N % (CAST(1 AS NUMBER(4,2)))'"},
            {"SELECT SPLIT_PART('a,b,c', ',', f + 0) FROM t2",
                "b ;; b"},
            {"SELECT SPLIT_PART('a,b,c', ',', f * 1) FROM t2",
                "b ;; b"},
            {"SELECT SPLIT_PART('a,b,c', ',', f / 1) FROM t2",
                "b ;; b"},
            {"SELECT SPLIT_PART('a,b,c', ',', i + 0.5) FROM t2",
                NOT_INTEGER + "'(CAST(T2.I AS NUMBER(38,1))) + 0.5'"},
            {"SELECT SPLIT_PART('a,b,c', ',', i * 0.5) FROM t2",
                "a ;; a"},
            {"SELECT SPLIT_PART('a,b,c', ',', i * 1.5) FROM t2",
                "c ;; c"},
            {"SELECT SPLIT_PART('a,b,c', ',', i - 0.5) FROM t2",
                NOT_INTEGER + "'(CAST(T2.I AS NUMBER(38,1))) - 0.5'"},
            {"SELECT SPLIT_PART('a,b,c', ',', 1 + 0.5) FROM t2",
                NOT_INTEGER + "'(CAST(1 AS NUMBER(3,1))) + 0.5'"},
            {"SELECT SPLIT_PART('a,b,c', ',', 1.5 * 2) FROM t2",
                "c ;; c"},
            {"SELECT SPLIT_PART('a,b,c', ',', 3 * 0.5) FROM t2",
                NOT_INTEGER + "'3 * 0.5'"},
            {"SELECT SPLIT_PART('a,b,c', ',', 2 / 3) FROM t2",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', 4 / 2) FROM t2",
                "b ;; b"},
            {"SELECT SPLIT_PART('a,b,c', ',', 5.5 % 2) FROM t2",
                NOT_INTEGER + "'5.5 % (CAST(2 AS NUMBER(3,1)))'"},
            {"SELECT SPLIT_PART('a,b,c', ',', -n) FROM t2",
                NOT_INTEGER + "'NEGATE(T2.N)'"},
            {"SELECT SPLIT_PART('a,b,c', ',', ABS(n)) FROM t2",
                NOT_INTEGER + "'ABS(T2.N)'"},
            {"SELECT SPLIT_PART('a,b,c', ',', ROUND(n, 1)) FROM t2",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', n::NUMBER(4,2)) FROM t2",
                NOT_INTEGER + "'T2.N'"},
            {"SELECT SPLIT_PART('a,b,c', ',', i::NUMBER(4,2)) FROM t2",
                "b ;; b"},
            {"SELECT SPLIT_PART('a,b,c', ',', f::NUMBER(4,2)) FROM t2",
                NOT_INTEGER + "'1.6'"},
            {"SELECT SPLIT_PART('a,b,c', ',', IFF(TRUE, n, n)) FROM t2",
                NOT_INTEGER + "'IFF(CAST(TRUE AS BOOLEAN), T2.N, T2.N)'"},
            {"SELECT SPLIT_PART('a,b,c', ',', COALESCE(n, 0)) FROM t2",
                NOT_INTEGER + "'IFNULL(T2.N, CAST(0 AS NUMBER(4,2)))'"},
            {"SELECT SPLIT_PART('a,b,c', ',', (f + 0)::NUMBER(4,2)) FROM t1",
                NOT_INTEGER + "'1.6'"},
            {"SELECT SPLIT_PART('a,b,c', ',', MOD(n, 2)) FROM t1",
                NOT_INTEGER + "'MOD(T1.N, CAST(2 AS NUMBER(4,2)))'"},
        });
    }

    /** Over rows that differ and over no row, only a constant is known. */
    @Test
    public void overRowsThatDifferEveryRowReadsTheEmptyText() {
        answer("CREATE OR REPLACE TABLE dr (n NUMBER(4,2), f FLOAT, i INT, s VARCHAR)");
        answer("INSERT INTO dr VALUES (1.6, 1.6, 2, '1.6'), (2.4, 2.4, 3, '2.4')");
        answer("CREATE OR REPLACE TABLE er (n NUMBER(4,2), f FLOAT, i INT, s VARCHAR)");
        assertCells(new String[][] {
            {"SELECT SPLIT_PART('a,b,c', ',', n) FROM dr",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', n + 0) FROM dr",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', n * 2) FROM dr",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', n * 1) FROM dr",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', n / 1) FROM dr",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', -n) FROM dr",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', ABS(n)) FROM dr",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', f::NUMBER(4,2)) FROM dr",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', s::NUMBER(4,2)) FROM dr",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', i + 0.5) FROM dr",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', i * 0.5) FROM dr",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', i / 3) FROM dr",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', ROUND(n, 1)) FROM dr",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', IFF(TRUE, n, n)) FROM dr",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', n::NUMBER(4,2)) FROM dr",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', i::NUMBER(4,2)) FROM dr",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', f) FROM dr",
                "b ;; b"},
            {"SELECT SPLIT_PART('a,b,c', ',', s) FROM dr",
                "b ;; b"},
            {"SELECT SPLIT_PART('a,b,c', ',', (SELECT 1.6)) FROM dr",
                NOT_INTEGER + "'1.6'"},
            {"SELECT SPLIT_PART('a,b,c', ',', (SELECT MAX(n) FROM dr)) FROM dr",
                NOT_INTEGER + "'2.4'"},
            {"SELECT SPLIT_PART('a,b,c', ',', MOD(5.5, 2)) FROM dr",
                NOT_INTEGER + "'MOD(5.5, CAST(2 AS NUMBER(3,1)))'"},
            {"SELECT SPLIT_PART('a,b,c', ',', MOD(n, 1)) FROM dr",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', n % 1) FROM dr",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', n / 2) FROM dr",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', i / 2) FROM dr",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', n) FROM er",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', n + 0) FROM er",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', n * 2) FROM er",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', n * 1) FROM er",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', n / 1) FROM er",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', -n) FROM er",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', ABS(n)) FROM er",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', f::NUMBER(4,2)) FROM er",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', s::NUMBER(4,2)) FROM er",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', i + 0.5) FROM er",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', i * 0.5) FROM er",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', i / 3) FROM er",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', ROUND(n, 1)) FROM er",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', IFF(TRUE, n, n)) FROM er",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', n::NUMBER(4,2)) FROM er",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', i::NUMBER(4,2)) FROM er",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', f) FROM er",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', s) FROM er",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', (SELECT 1.6)) FROM er",
                NOT_INTEGER + "'1.6'"},
            {"SELECT SPLIT_PART('a,b,c', ',', (SELECT MAX(n) FROM dr)) FROM er",
                NOT_INTEGER + "'2.4'"},
            {"SELECT SPLIT_PART('a,b,c', ',', MOD(5.5, 2)) FROM er",
                NOT_INTEGER + "'MOD(5.5, CAST(2 AS NUMBER(3,1)))'"},
            {"SELECT SPLIT_PART('a,b,c', ',', MOD(n, 1)) FROM er",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', n % 1) FROM er",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', n / 2) FROM er",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', i / 2) FROM er",
                ""},
        });
    }

    /** A WHERE the statistics prove false of every row leaves the plan no value to fold; a constant is still judged. */
    @Test
    public void aWhereTheStatisticsCannotProveStopsTheFold() {
        answer("CREATE OR REPLACE TABLE tw (n NUMBER(4,2), i INT, f FLOAT)");
        answer("INSERT INTO tw VALUES (1.6, 2, 1.6)");
        answer("CREATE OR REPLACE TABLE tb (i NUMBER(38,0))");
        answer("INSERT INTO tb VALUES (3000000000)");
        answer("CREATE OR REPLACE TABLE tb (i NUMBER(38,0))");
        answer("INSERT INTO tb VALUES (3000000000)");
        answer("CREATE OR REPLACE TABLE tx (i NUMBER(10,0))");
        answer("INSERT INTO tx VALUES (3000000000)");
        answer("CREATE OR REPLACE TABLE tp (n NUMBER(4,2), i INT)");
        answer("INSERT INTO tp VALUES (1.6, 2)");
        assertCells(new String[][] {
            {"SELECT SPLIT_PART('a,b,c', ',', n) FROM tw WHERE i < 0",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', n) FROM tw WHERE i > 0",
                NOT_INTEGER + "'TW.N'"},
            {"SELECT SPLIT_PART('a,b,c', ',', n) FROM tw WHERE i = 2",
                NOT_INTEGER + "'TW.N'"},
            {"SELECT SPLIT_PART('a,b,c', ',', n) FROM tw WHERE f > 5",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', n * 2) FROM tw WHERE i < 0",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', f::NUMBER(4,2)) FROM tw WHERE i < 0",
                ""},
            {"SELECT SPLIT_PART('a,b', ',', i / 1) FROM tb WHERE i < 0",
                ""},
            {"SELECT SPLIT_PART('a,b', ',', i / 1) FROM tb WHERE i > 0",
                NOT_INTEGER + "'SCALED_ROUND_INT_DIVIDE(TB.I, 1)'"},
            {"SELECT SPLIT_PART('a,b', ',', i / 1) FROM tb",
                NOT_INTEGER + "'SCALED_ROUND_INT_DIVIDE(TB.I, 1)'"},
            {"SELECT SPLIT_PART('a,b', ',', i / 2) FROM tb",
                ""},
            {"SELECT SPLIT_PART('a,b', ',', i / 7) FROM tb",
                OUT_OF_RANGE + "38,6){nullable}, value 428571428.571429"},
            {"SELECT SPLIT_PART('a,b,c', ',', (SELECT 1.6)) FROM tw WHERE i < 0",
                NOT_INTEGER + "'1.6'"},
            {"SELECT SPLIT_PART('a,b', ',', i / 1) FROM tb WHERE i < 0",
                ""},
            {"SELECT SPLIT_PART('a,b', ',', i * 1) FROM tb WHERE i < 0",
                ""},
            {"SELECT SPLIT_PART('a,b', ',', i + 0) FROM tb WHERE i < 0",
                ""},
            {"SELECT SPLIT_PART('a,b', ',', i / 3) FROM tb WHERE i < 0",
                ""},
            {"SELECT SPLIT_PART('a,b', ',', (i + 0) / 1) FROM tb WHERE i < 0",
                ""},
            {"SELECT SPLIT_PART('a,b', ',', i / 1) FROM tx WHERE i < 0",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', n / 1) FROM tp WHERE i < 0",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', i / 1) FROM tp WHERE i < 0",
                ""},
            {"SELECT SPLIT_PART('a,b', ',', i - 2999999999) FROM tb WHERE i < 0",
                ""},
        });
    }

    /** Integer divisions and roundings to a scale fold only to whole numbers, decimal divisions to exact ones. */
    @Test
    public void aDivisionOrARoundingFoldsOnlyToAWholeNumber() {
        answer("CREATE OR REPLACE TABLE tq (i INT)");
        answer("INSERT INTO tq VALUES (3)");
        assertCells(new String[][] {
            {"SELECT SPLIT_PART('a,b,c', ',', 1 / 8)",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', 1 / 64)",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', 1 / 128)",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', 3 / 2)",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', 6 / 3)",
                "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', 1 / 3 * 3)",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', (1 / 8) * 8)",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', i / 2) FROM tq",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', i / 3) FROM tq",
                "a"},
            {"SELECT SPLIT_PART('a,b,c', ',', i / 6) FROM tq",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', i / 9) FROM tq",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', 1.5 / 3)",
                NOT_INTEGER + "'(CAST(1.5 AS NUMBER(8,7))) / 3'"},
            {"SELECT SPLIT_PART('a,b,c', ',', ROUND(1.6, 1))",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', ROUND(1.6, 0))",
                "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', ROUND(2.0, 1))",
                "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', TRUNC(1.6, 1))",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', CEIL(1.6, 1))",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', 1.6 / 3)",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', 3 / 1.5)",
                "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', 1 / 0.5)",
                "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', 4.5 / 1.5)",
                "c"},
            {"SELECT SPLIT_PART('a,b,c', ',', FLOOR(1.6, 1))",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', FLOOR(2.0, 1))",
                "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', TRUNC(2.0, 1))",
                "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', CEIL(1.2, 0))",
                "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', ROUND(1.25, 1) * 2)",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', ROUND(1.5, 1) + 0.5)",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', 2147.483647::NUMBER(10,6))",
                NOT_INTEGER + "'2147.483647'"},
            {"SELECT SPLIT_PART('a,b,c', ',', (SELECT 2147.48))",
                NOT_INTEGER + "'2147.48'"},
            {"SELECT SPLIT_PART('a,b,c', ',', 0.5 * 4)",
                "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', 10 / 4 * 2)",
                ""},
            {"SELECT SPLIT_PART('a,b,c', ',', MOD(4.0, 2.5))",
                NOT_INTEGER + "'MOD(CAST(4 AS NUMBER(3,1)), 2.5)'"},
        });
    }

    /** A NULL beside the one value, and texts that spell it differently, leave the plan no value to fold. */
    @Test
    public void aNullOrAnotherSpellingBesideTheValueStopsTheFold() {
        answer("CREATE OR REPLACE TABLE tz (n NUMBER(4,2), f FLOAT, s VARCHAR)");
        answer("INSERT INTO tz VALUES (1.6, 1.6, '1.6'), (NULL, NULL, NULL)");
        answer("CREATE OR REPLACE TABLE ts (s VARCHAR, f FLOAT)");
        answer("INSERT INTO ts VALUES ('1.6', 1.6), ('1.60', 1.6)");
        answer("CREATE OR REPLACE TABLE tf (f FLOAT, s VARCHAR)");
        answer("INSERT INTO tf VALUES (1.999, '1.999')");
        answer("CREATE OR REPLACE TABLE tn (i NUMBER(38,0), n NUMBER(4,2))");
        answer("INSERT INTO tn VALUES (3000000000, 1.6), (NULL, NULL)");
        assertCells(new String[][] {
            {"SELECT SPLIT_PART('a,b,c', ',', n) FROM tz ORDER BY 1 NULLS LAST",
                " ;; null"},
            {"SELECT SPLIT_PART('a,b,c', ',', f::NUMBER(4,2)) FROM tz ORDER BY 1 NULLS LAST",
                " ;; null"},
            {"SELECT SPLIT_PART('a,b,c', ',', s::NUMBER(4,2)) FROM tz ORDER BY 1 NULLS LAST",
                " ;; null"},
            {"SELECT SPLIT_PART('a,b,c', ',', s::NUMBER(4,2)) FROM ts",
                " ;; "},
            {"SELECT SPLIT_PART('a,b,c', ',', f::NUMBER(4,2)) FROM ts",
                NOT_INTEGER + "'1.6'"},
            {"SELECT SPLIT_PART('a,b,c', ',', f::NUMBER(4,2)) FROM tf",
                "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', s::NUMBER(4,2)) FROM tf",
                "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', f::NUMBER(4,1)) FROM tf",
                "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', TO_NUMBER(s, 4, 2)) FROM tf",
                "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', f::NUMBER(4,2) * 1) FROM tf",
                "b"},
            {"SELECT SPLIT_PART('a,b', ',', i) FROM tn",
                OUT_OF_RANGE + "38,0){nullable}, value 3000000000"},
            {"SELECT SPLIT_PART('a,b', ',', i / 1) FROM tn",
                OUT_OF_RANGE + "38,6){nullable}, value 3000000000.000000"},
            {"SELECT SPLIT_PART('a,b,c', ',', n * 2) FROM tn ORDER BY 1 NULLS LAST",
                " ;; null"},
            {"SELECT SPLIT_PART('a,b,c', ',', n + 0) FROM tn ORDER BY 1 NULLS LAST",
                " ;; null"},
        });
    }

    /** A part the plan does not know is read in its own type held in 32 bits, the digits past the point counted in. */
    @Test
    public void aPartReadWhereTheRowIsIsHeldInThirtyTwoBits() {
        answer("CREATE OR REPLACE TABLE tm (i NUMBER(38,0))");
        answer("INSERT INTO tm VALUES (3000000000), (1)");
        answer("CREATE OR REPLACE TABLE te (i NUMBER(38,0))");
        answer("CREATE OR REPLACE TABLE tt (i NUMBER(38,0))");
        answer("INSERT INTO tt VALUES (2)");
        answer("CREATE OR REPLACE TABLE tb (i NUMBER(38,0))");
        answer("INSERT INTO tb VALUES (3000000000)");
        answer("CREATE OR REPLACE TABLE tx (i NUMBER(10,0))");
        answer("INSERT INTO tx VALUES (3000000000)");
        assertCells(new String[][] {
            {"SELECT SPLIT_PART('a,b', ',', i / 1) FROM tm",
                OUT_OF_RANGE + "38,6){nullable}, value 3000000000.000000"},
            {"SELECT SPLIT_PART('a,b', ',', i) FROM tm",
                OUT_OF_RANGE + "38,0){nullable}, value 3000000000"},
            {"SELECT SPLIT_PART('a,b', ',', i / 2) FROM tm",
                OUT_OF_RANGE + "38,6){nullable}, value 1500000000.000000"},
            {"SELECT SPLIT_PART('a,b', ',', i / 1) FROM te",
                ""},
            {"SELECT SPLIT_PART('a,b', ',', i / 1) FROM tt WHERE i < 0",
                ""},
            {"SELECT SPLIT_PART('a,b', ',', i / 1) FROM tt",
                "b"},
            {"SELECT SPLIT_PART('a,b', ',', i / 1) FROM tx",
                NOT_INTEGER + "'(CAST(TX.I AS NUMBER(16,6))) / 1'"},
            {"SELECT SPLIT_PART('a,b', ',', i / 7) FROM tm",
                OUT_OF_RANGE + "38,6){nullable}, value 428571428.571429"},
            {"SELECT SPLIT_PART('a,b', ',', i / 1000000) FROM tb",
                ""},
            {"SELECT SPLIT_PART('a,b', ',', i / 1400000) FROM tb",
                ""},
        });
    }
}
