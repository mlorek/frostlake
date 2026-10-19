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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A negated LIKE given a quantifier, where it stands: after the quantifier and the token that opens its list the
 * account gives up the list's own parenthesis and recovers to a comma where the predicate is an item of a select
 * list, an ORDER BY or a GROUP BY, or to a comma or a closing parenthesis where one is open around it; the first
 * closing parenthesis then left without a partner is refused — a call's own, a FROM subquery's own, the list's own
 * after a comma at the top of a list. A WHERE, a HAVING and a QUALIFY report nothing more, a query in parentheses in a
 * select list has its SELECT refused first, a join's ON condition is refused at the ON alone, and NOT REGEXP is NOT
 * RLIKE (live-verified).
 */
public class NegatedQuantifierPlaceTest extends MeasuredCellsTestSupport {

    @Test
    public void aQuantifierInEachClause() {
        assertCells(new String[][] {
            {"SELECT 1 FROM (SELECT 'a' c) GROUP BY c HAVING c NOT LIKE ANY ('x', 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected 'ANY'. | syntax "
                + "error line 1 at position 63 unexpected ''x''."},
            {"SELECT c FROM (SELECT 'a' c) QUALIFY c NOT LIKE ANY ('x', 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'ANY'. | syntax "
                + "error line 1 at position 53 unexpected ''x''."},
            {"SELECT 1 FROM (SELECT 'a' c) t1 JOIN (SELECT 'a' d) t2 ON c NOT LIKE ANY ('x', 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'ON'."},
            {"SELECT c FROM (SELECT 'a' c) ORDER BY c NOT LIKE ANY ('x', 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'ANY'. | syntax "
                + "error line 1 at position 54 unexpected ''x''. | syntax error line 1 at position 62 unexpected ')'."},
            {"SELECT c FROM (SELECT 'a' c) GROUP BY c NOT LIKE ANY ('x', 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'ANY'. | syntax "
                + "error line 1 at position 54 unexpected ''x''. | syntax error line 1 at position 62 unexpected ')'."},
            {"SELECT UPPER(c NOT LIKE ANY ('x', 'y')) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax "
                + "error line 1 at position 29 unexpected ''x''. | syntax error line 1 at position 38 unexpected ')'."},
            {"SELECT (SELECT 'a' NOT LIKE ANY ('x', 'y'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 28 unexpected 'ANY'. | syntax error line 1 at position 33 unexpected "
                + "''x''. | syntax error line 1 at position 41 unexpected ')'."},
            {"SELECT 'a' NOT REGEXP ANY ('x', 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'ANY'. | syntax "
                + "error line 1 at position 27 unexpected ''x''. | syntax error line 1 at position 35 unexpected ')'."},
            {"SELECT 1 WHERE 'a' NOT LIKE ANY (f('x'), 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'ANY'. | syntax "
                + "error line 1 at position 33 unexpected 'f'."},
            {"SELECT 1 WHERE 'a' NOT LIKE ANY ('x', 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'ANY'. | syntax "
                + "error line 1 at position 33 unexpected ''x''."},
            {"SELECT 'a' NOT ILIKE ALL ('x', 'y') FROM (SELECT 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ALL'. | syntax "
                + "error line 1 at position 26 unexpected ''x''. | syntax error line 1 at position 34 unexpected ')'."},
            {"SELECT 1 FROM (SELECT 'a' c) WHERE c NOT LIKE ANY (UPPER('x'), 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'ANY'. | syntax "
                + "error line 1 at position 51 unexpected 'UPPER'."},
            {"SELECT 1 FROM (SELECT 'a' c) GROUP BY c HAVING c NOT LIKE ANY ('x')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected 'ANY'. | syntax "
                + "error line 1 at position 63 unexpected ''x''."},
            {"SELECT c FROM (SELECT 'a' c) ORDER BY c NOT LIKE ANY ('x')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'ANY'. | syntax "
                + "error line 1 at position 54 unexpected ''x''."},
            {"SELECT UPPER(c NOT LIKE ANY ('x')) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax "
                + "error line 1 at position 29 unexpected ''x''. | syntax error line 1 at position 33 unexpected ')'."},
            {"SELECT 1 FROM (SELECT 'a' c) t1 JOIN (SELECT 'a' d) t2 ON c NOT LIKE ANY (f('x'), 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'ON'."},
        });
    }

    @Test
    public void theRecoveryClosesAParenthesisEarly() {
        assertCells(new String[][] {
            {"SELECT c FROM (SELECT 'a' c) ORDER BY c NOT LIKE ANY (f('x'), 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'ANY'. | syntax "
                + "error line 1 at position 54 unexpected 'f'. | syntax error line 1 at position 65 unexpected ')'."},
            {"SELECT LOWER(UPPER(c NOT LIKE ANY ('x'))) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'ANY'. | syntax "
                + "error line 1 at position 35 unexpected ''x''. | syntax error line 1 at position 40 unexpected ')'."},
            {"SELECT UPPER(c NOT LIKE ANY ('x'), 1) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax "
                + "error line 1 at position 29 unexpected ''x''. | syntax error line 1 at position 36 unexpected ')'."},
            {"SELECT 1 WHERE EXISTS (SELECT 'a' NOT LIKE ANY ('x'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'ANY'. | syntax "
                + "error line 1 at position 48 unexpected ''x''. | syntax error line 1 at position 52 unexpected ')'."},
            {"SELECT 1 FROM (SELECT 'a' NOT LIKE ANY ('x', 'y'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'ANY'. | syntax "
                + "error line 1 at position 40 unexpected ''x''. | syntax error line 1 at position 49 unexpected ')'."},
            {"SELECT (SELECT 'a' NOT LIKE ANY ('x'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 28 unexpected 'ANY'. | syntax error line 1 at position 33 unexpected "
                + "''x''."},
            {"SELECT 1, (SELECT 'a' NOT LIKE ANY ('x', 'y'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 31 unexpected 'ANY'. | syntax error line 1 at position 36 unexpected "
                + "''x''. | syntax error line 1 at position 44 unexpected ')'."},
            {"SELECT 1 FROM (SELECT 'a' c) t1 LEFT JOIN (SELECT 'a' d) t2 ON c NOT LIKE ANY ('x')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'ON'."},
            {"SELECT 1 FROM (SELECT 'a' c) t1 JOIN (SELECT 'a' d) t2 ON c = d AND c NOT LIKE ANY ('x')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'ON'."},
            {"SELECT 'a' NOT REGEXP ALL ('x')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'ALL'. | syntax "
                + "error line 1 at position 27 unexpected ''x''."},
            {"SELECT IFF('a' NOT LIKE ANY ('x', 'y'), 1, 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax "
                + "error line 1 at position 29 unexpected ''x''. | syntax error line 1 at position 44 unexpected ')'."},
            {"SELECT 1 FROM (SELECT 'a' c) WHERE UPPER(c NOT LIKE ANY ('x')) IS NULL",
                "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'ANY'. | syntax "
                + "error line 1 at position 57 unexpected ''x''. | syntax error line 1 at position 61 unexpected ')'."},
            {"SELECT c FROM (SELECT 'a' c) ORDER BY UPPER(c NOT LIKE ANY ('x', 'y'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'ANY'. | syntax "
                + "error line 1 at position 60 unexpected ''x''. | syntax error line 1 at position 69 unexpected ')'."},
            {"SELECT 1 FROM (SELECT 'a' c) t1 JOIN (SELECT 'a' d) t2 ON (c NOT LIKE ANY ('x'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'ON'."},
            {"SELECT 1 FROM (SELECT 'a' c) t1 INNER JOIN (SELECT 'a' d) t2 ON c NOT LIKE ANY ('x', 'y') WHERE 1 = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'ON'."},
            {"SELECT (SELECT 'a' NOT LIKE ANY ('x', 'y') FROM (SELECT 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 28 unexpected 'ANY'. | syntax error line 1 at position 33 unexpected "
                + "''x''. | syntax error line 1 at position 41 unexpected ')'."},
            {"SELECT 'a' NOT LIKE ANY ('x', 'y'), (SELECT 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax "
                + "error line 1 at position 25 unexpected ''x''. | syntax error line 1 at position 33 unexpected ')'."},
            {"SELECT ARRAY_CONSTRUCT('a' NOT LIKE ANY ('x', 'y'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'ANY'. | syntax "
                + "error line 1 at position 41 unexpected ''x''. | syntax error line 1 at position 50 unexpected ')'."},
        });
    }
}
