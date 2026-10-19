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
 * GENERATOR's ROWCOUNT and TIMELIMIT are constants. A column of a relation to the left, RANDOM(), SEQ4() and
 * UNIFORM over a RANDOM() are refused while the statement compiles, {@code argument 1 to function GENERATOR
 * needs to be constant, found 'GN.N'} - argument 2 for the time limit - where an expression of constants, a
 * CASE, a scalar subquery and a session variable are taken. A fraction is cut to its whole part, a NULL bound
 * is a bound not given, a time limit of 0 is none, a negative bound and one of another type are refused, and
 * with both bounds the rows stop at whichever comes first: a count past what 32 bits hold beside a time limit
 * of a second answers the rows the query's LIMIT asks for. A bound that reads a row is a constant all the
 * same when no row can change it - a NULL operand, a product with zero, a constant condition's branch, the
 * first argument of NVL that is no NULL - and the LATERAL spellings of the call are held to the same rule.
 * Every answer is live-verified.
 */
public class GeneratorConstantBoundsTest extends MeasuredCellsTestSupport {

    @Test
    public void aBoundIsAConstant() {
        try {
            assertAnswer(
                "Table GN successfully created.",
                "CREATE OR REPLACE TABLE gn (n INT)");
            assertAnswer(
                "2",
                "INSERT INTO gn VALUES (2), (3)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                    + "'GN.N'",
                "SELECT gn.n, COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => gn.n)) GROUP BY 1 ORDER BY 1");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 49 | invalid identifier 'N'",
                "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => n))");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                    + "'RANDOM()'",
                "SELECT COUNT(*) FROM (SELECT 1 FROM TABLE(GENERATOR(ROWCOUNT => RANDOM(), TIMELIMIT => 1)) LIMIT "
                    + "1)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                    + "'UNIFORM(1, 3, RANDOM())'",
                "SELECT COUNT(*) FROM (SELECT 1 FROM TABLE(GENERATOR(ROWCOUNT => UNIFORM(1, 3, RANDOM()), "
                    + "TIMELIMIT => 1)) LIMIT 5)");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 1 + 2))");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => ABS(-3)))");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT 2)))");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM gn)))");
            assertAnswer(
                "Statement executed successfully.",
                "SET gcount = 4");
            assertAnswer(
                "4",
                "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => $gcount))");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 2 to function GENERATOR needs to be constant, found "
                    + "'GN.N'",
                "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => 2, TIMELIMIT => gn.n))");
            answer(
                "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => DATEDIFF(day, CURRENT_DATE, CURRENT_DATE) + 2))");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 2.7))");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [-1] for function 'GENERATOR', parameter "
                    + "TIMELIMIT:  must be non-negative.",
                "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 3, TIMELIMIT => -1))");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'TIMELIMIT'",
                "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 3, TIMELIMIT => 'x'))");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 3, TIMELIMIT => 2.5))");
            assertAnswer(
                "4",
                "SELECT COUNT(*) FROM gn a, TABLE(GENERATOR(ROWCOUNT => 2)) x");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM (SELECT 1 FROM TABLE(GENERATOR(ROWCOUNT => 4294967297, TIMELIMIT => 1)) "
                    + "LIMIT 3)");
            assertAnswer(
                "5",
                "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 5, TIMELIMIT => 60))");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => CASE WHEN 1 = 1 THEN 2 ELSE 3 END))");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                    + "'SEQ4()'",
                "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => SEQ4(), TIMELIMIT => 1))");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => COALESCE(NULL, 2)))");
        } finally {
            answer(
                "UNSET gcount");
        }
    }

    @Test
    public void aNullBoundIsABoundNotGiven() {
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => NULL))");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 3, TIMELIMIT => NULL))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => NULL, TIMELIMIT => NULL))");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM (SELECT 1 FROM TABLE(GENERATOR(ROWCOUNT => NULL, TIMELIMIT => 1)) LIMIT 3)");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => NULL))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 0))");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 3, TIMELIMIT => 0))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [-1] for function 'GENERATOR', parameter ROWCOUNT:  "
                + "must be non-negative.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => -1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'ROWCOUNT'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => '3'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter 'ROWCOUNT'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => TRUE))");
        assertAnswer(
            "4",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 3, ROWCOUNT => 4))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => NULL::NUMBER))");
        assertAnswer(
            "5|0|4",
            "SELECT COUNT(*), MIN(s), MAX(s) FROM (SELECT SEQ4() AS s FROM TABLE(GENERATOR(ROWCOUNT => 5)))");
        assertAnswer(
            "6",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 3)) a, TABLE(GENERATOR(ROWCOUNT => 2)) b");
    }

    @Test
    public void aBoundNoRowCanChangeIsAConstant() {
        assertAnswer(
            "Table GN successfully created.",
            "CREATE OR REPLACE TABLE gn (n INT)");
        assertAnswer(
            "2",
            "INSERT INTO gn VALUES (2), (3)");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => NULL + gn.n))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => gn.n + NULL))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => NULL * gn.n))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => gn.n - NULL))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => -(NULL + gn.n)))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => (NULL + gn.n) + 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found 'GN.N'",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => NVL(NULL, gn.n)))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => IFF(TRUE, NULL, gn.n)))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => NULL::INT + gn.n))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => NULLIF(1, 1) + gn.n))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => ABS(NULL + gn.n)))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => NULL + RANDOM()))");
        assertAnswer(
            "4",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => COALESCE(NULL + gn.n, 2)))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => CASE WHEN gn.n IS NULL THEN NULL ELSE NULL END))");
        assertAnswer(
            "4",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => gn.n * 0 + 2))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => NULL / gn.n))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARCHAR(134217728)] for parameter 'ROWCOUNT'",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => gn.n || NULL))");
        assertAnswer(
            "4",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => IFF(FALSE, gn.n, 2)))");
        assertAnswer(
            "4",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => NVL(2, gn.n)))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'NULLIF(GN.N, GN.N)'",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => NULLIF(gn.n, gn.n)))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found 'N.N'",
            "SELECT n.n FROM gn n CROSS JOIN LATERAL GENERATOR(ROWCOUNT => n.n) g ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found 'GN.N'",
            "SELECT gn.n FROM gn, LATERAL GENERATOR(ROWCOUNT => gn.n) g ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found 'GN.N'",
            "SELECT gn.n FROM gn JOIN LATERAL GENERATOR(ROWCOUNT => gn.n) g ORDER BY 1");
        assertAnswer(
            "2 ;; 2 ;; 3 ;; 3",
            "SELECT gn.n FROM gn CROSS JOIN LATERAL GENERATOR(ROWCOUNT => 2) g ORDER BY 1");
        assertAnswer(
            "2 ;; 2 ;; 3 ;; 3",
            "SELECT gn.n FROM gn, LATERAL GENERATOR(ROWCOUNT => 2) g ORDER BY 1");
        assertAnswer(
            "2 ;; 2 ;; 3 ;; 3",
            "SELECT gn.n FROM gn, TABLE(GENERATOR(ROWCOUNT => 2, TIMELIMIT => NULL + gn.n)) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found 'A.N + "
                + "B.N'",
            "SELECT COUNT(*) FROM gn a, gn b, TABLE(GENERATOR(ROWCOUNT => a.n + b.n))");
        assertAnswer(
            "6",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM gn)))");
    }

    @Test
    public void constantsTheAccountFolds() {
        assertAnswer(
            "2",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => YEAR(CURRENT_DATE) - YEAR(CURRENT_DATE) + 2))");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => EXTRACT(day FROM '2024-01-03'::DATE)))");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => TO_NUMBER('3')))");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 3::INT))");
        assertAnswer(
            "2",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => LEAST(2, 3)))");
        assertAnswer(
            "2",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => IFF(CURRENT_USER() IS NULL, 1, 2)))");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => GREATEST(1, 2) + NVL(NULL, 1)))");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 2 * 1.5))");
        assertAnswer(
            "3",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => '3'::NUMBER))");
    }
}
