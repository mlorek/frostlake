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
 * NOT LIKE, NOT ILIKE and NOT RLIKE take no quantifier. The quantifier is refused, and the account reads on
 * into the parentheses after it: the token that opens them is refused too, and then the parenthesis behind a
 * name written first, or, in a select list, the closing parenthesis of a list of several patterns. A WHERE
 * clause's list is told by its first two lines alone (live-verified).
 */
public class NegatedLikeQuantifierLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void theQuantifierAndTheListAfterIt() {
        try {
            answer(
                "CREATE OR REPLACE TABLE nb_t (a INT)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax "
                    + "error line 1 at position 25 unexpected ''x''. | syntax error line 1 at position 33 unexpected "
                    + "')'.",
                "SELECT 'a' NOT LIKE ANY ('x', 'y')");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ANY'. | syntax "
                    + "error line 1 at position 26 unexpected ''x''. | syntax error line 1 at position 34 unexpected "
                    + "')'.",
                "SELECT 'a' NOT ILIKE ANY ('x', 'y')");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ALL'. | syntax "
                    + "error line 1 at position 25 unexpected ''x''. | syntax error line 1 at position 33 unexpected "
                    + "')'.",
                "SELECT 'a' NOT LIKE ALL ('x', 'y')");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax "
                    + "error line 1 at position 25 unexpected ''x''.",
                "SELECT 'a' NOT LIKE ANY ('x')");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax "
                    + "error line 1 at position 25 unexpected ''x''. | syntax error line 1 at position 33 unexpected "
                    + "')'.",
                "SELECT 'a' NOT LIKE ANY ('x', 'y') AS c");
            answer(
                "SELECT TIMESTAMP_NTZ '2020-01-02 03:04:05'");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS eu(INT)");
        }
    }

    @Test
    public void otherListsAndOtherClauses() {
        try {
            answer(
                "CREATE OR REPLACE TABLE nb_t (a INT)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax "
                    + "error line 1 at position 25 unexpected ''x''. | syntax error line 1 at position 38 unexpected "
                    + "')'.",
                "SELECT 'a' NOT LIKE ANY ('x', 'y', 'z')");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax "
                    + "error line 1 at position 25 unexpected '1'.",
                "SELECT 'a' NOT LIKE ANY (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax "
                    + "error line 1 at position 25 unexpected ')'.",
                "SELECT 'a' NOT LIKE ANY ()");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'.",
                "SELECT 'a' NOT LIKE ANY 'x'");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax "
                    + "error line 1 at position 25 unexpected 'SELECT'.",
                "SELECT 'a' NOT LIKE ANY (SELECT 'x')");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax "
                    + "error line 1 at position 25 unexpected ''x''. | syntax error line 1 at position 40 unexpected "
                    + "')'.",
                "SELECT 'a' NOT LIKE ANY ('x' || 'y', 'z')");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'ANY'. | syntax "
                    + "error line 1 at position 43 unexpected ''x''.",
                "SELECT 1 FROM nb_t WHERE 'a' NOT LIKE ANY ('x', 'y') AND 1 = 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax "
                    + "error line 1 at position 25 unexpected '('. | syntax error line 1 at position 35 unexpected "
                    + "')'.",
                "SELECT 'a' NOT LIKE ANY (('x'), 'y')");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ANY'. | syntax "
                    + "error line 1 at position 26 unexpected ''x''. | syntax error line 1 at position 34 unexpected "
                    + "')'.",
                "SELECT 'a' NOT RLIKE ANY ('x', 'y')");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax "
                    + "error line 1 at position 25 unexpected ''x''. | syntax error line 1 at position 33 unexpected "
                    + "')'.",
                "SELECT 'a' NOT LIKE ANY ('x', 'y'), 2");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax "
                    + "error line 1 at position 25 unexpected 'a'. | syntax error line 1 at position 31 unexpected "
                    + "')'.",
                "SELECT 'a' NOT LIKE ANY (a, 'y') FROM nb_t");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax "
                    + "error line 1 at position 25 unexpected ''x''. | syntax error line 1 at position 33 unexpected "
                    + "')'.",
                "SELECT 'a' NOT LIKE ANY ('x', 'y') ESCAPE '!'");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'ALL'. | syntax "
                    + "error line 1 at position 22 unexpected ''x''. | syntax error line 1 at position 30 unexpected "
                    + "')'.",
                "SELECT 'a' ILIKE ALL ('x', 'y')");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ALL'. | syntax "
                    + "error line 1 at position 26 unexpected ''x''. | syntax error line 1 at position 34 unexpected "
                    + "')'.",
                "SELECT 'a' NOT ILIKE ALL ('x', 'y')");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax "
                    + "error line 1 at position 25 unexpected 'nosuch_fn_zq7'. | syntax error line 1 at position 38 "
                    + "unexpected '('.",
                "SELECT 'a' NOT LIKE ANY (nosuch_fn_zq7('x'), 'y')");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax "
                    + "error line 1 at position 25 unexpected ''x''. | syntax error line 1 at position 40 unexpected "
                    + "')'.",
                "SELECT 'a' NOT LIKE ANY ('x', ('y', 'z'))");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax "
                    + "error line 1 at position 25 unexpected ''x''. | syntax error line 1 at position 33 unexpected "
                    + "')'.",
                "SELECT 'a' NOT LIKE ANY ('x', 'y') FROM nb_t WHERE 'b' NOT LIKE ALL ('p', 'q')");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS eu(INT)");
            answer(
                "DROP FUNCTION IF EXISTS eu2(INT)");
            answer(
                "DROP VIEW IF EXISTS nb_v");
        }
    }
}
