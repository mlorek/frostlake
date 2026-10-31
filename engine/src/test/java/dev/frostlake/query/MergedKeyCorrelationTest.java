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
 * A bare USING or NATURAL key read by a correlated subquery is the merged key, the first of its copies that is
 * not NULL, in the select list, WHERE, IN, under GROUP BY and past a later join; a LATERAL derived table reads
 * the relations to its left one by one and refuses the bare key as ambiguous, while a LATERAL table function's
 * argument reads the merged key and refuses a name an ON join shares (all live-verified).
 */
public class MergedKeyCorrelationTest extends MeasuredCellsTestSupport {

    @Test
    public void aCorrelatedSubqueryReadsTheMergedKey() {
        assertAnswer("Table P726 successfully created.", "CREATE OR REPLACE TABLE p726 (kk INT, v INT)");
        assertAnswer("2", "INSERT INTO p726 VALUES (1, 10), (2, 20)");
        assertAnswer("Table Q726 successfully created.", "CREATE OR REPLACE TABLE q726 (kk INT, w INT)");
        assertAnswer("2", "INSERT INTO q726 VALUES (1, 30), (3, 40)");
        assertAnswer("Table R726 successfully created.", "CREATE OR REPLACE TABLE r726 (k INT, z INT)");
        assertAnswer("3", "INSERT INTO r726 VALUES (1, 100), (2, 200), (3, 300)");
        assertAnswer("1|true|true ;; 2|true|true ;; 3|true|true",
            "SELECT kk, EXISTS (SELECT 1 FROM r726 WHERE r726.k = kk) eq, EXISTS (SELECT 1 FROM r726 WHERE r726.k >= "
                + "kk AND r726.k <= kk) rng FROM p726 FULL JOIN q726 USING (kk) ORDER BY 1");
        assertAnswer("1|1 ;; 2|2 ;; 3|3",
            "SELECT kk, (SELECT COUNT(*) FROM r726 WHERE r726.k <= kk) m FROM p726 FULL JOIN q726 USING (kk) ORDER "
                + "BY 1");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'KK'",
            "SELECT kk, x.z FROM p726 FULL JOIN q726 USING (kk), LATERAL (SELECT z FROM r726 WHERE r726.k = kk) x "
                + "ORDER BY 1");
        assertAnswer("1|100 ;; 2|200 ;; 3|300",
            "SELECT kk, x.z FROM (SELECT * FROM p726 FULL JOIN q726 USING (kk)) d, LATERAL (SELECT z FROM r726 WHERE "
                + "r726.k = d.kk) x ORDER BY 1");
        assertAnswer("1|1 ;; 2|2 ;; 3|3",
            "SELECT kk, (SELECT COUNT(*) FROM r726 WHERE r726.k <= kk) m FROM p726 NATURAL FULL JOIN q726 ORDER BY 1");
        assertAnswer("1|1 ;; 3|3",
            "SELECT kk, (SELECT COUNT(*) FROM r726 WHERE r726.k <= kk) m FROM p726 RIGHT JOIN q726 USING (kk) ORDER "
                + "BY 1");
        assertAnswer("1|1 ;; 2|2",
            "SELECT kk, (SELECT COUNT(*) FROM r726 WHERE r726.k <= kk) m FROM p726 LEFT JOIN q726 USING (kk) ORDER "
                + "BY 1");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'KK'",
            "SELECT kk, x.z FROM p726 RIGHT JOIN q726 USING (kk), LATERAL (SELECT z FROM r726 WHERE r726.k = kk) x "
                + "ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'KK'",
            "SELECT kk, x.z FROM p726 LEFT JOIN q726 USING (kk), LATERAL (SELECT z FROM r726 WHERE r726.k = kk) x "
                + "ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3",
            "SELECT kk FROM p726 FULL JOIN q726 USING (kk) WHERE EXISTS (SELECT 1 FROM r726 WHERE r726.k > kk - 1 "
                + "AND r726.k < kk + 1) ORDER BY 1");
        assertAnswer("1|100 ;; 2|200 ;; 3|300",
            "SELECT kk, (SELECT MAX(z) FROM r726 WHERE r726.k BETWEEN kk AND kk) m FROM p726 FULL JOIN q726 USING "
                + "(kk) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'KK'",
            "SELECT kk, ARRAY_AGG(x.z) WITHIN GROUP (ORDER BY x.z) zs FROM p726 FULL JOIN q726 USING (kk), LATERAL "
                + "(SELECT z FROM r726 WHERE r726.k <= kk) x GROUP BY kk ORDER BY 1");
    }

    @Test
    public void lateralItemsReadTheKeyAsTheAccountDoes() {
        assertAnswer("Table P727 successfully created.", "CREATE OR REPLACE TABLE p727 (kk INT, v INT)");
        assertAnswer("2", "INSERT INTO p727 VALUES (1, 10), (2, 20)");
        assertAnswer("Table Q727 successfully created.", "CREATE OR REPLACE TABLE q727 (kk INT, w INT)");
        assertAnswer("2", "INSERT INTO q727 VALUES (1, 30), (3, 40)");
        assertAnswer("Table R727 successfully created.", "CREATE OR REPLACE TABLE r727 (k INT, z INT)");
        assertAnswer("3", "INSERT INTO r727 VALUES (1, 100), (2, 200), (3, 300)");
        assertAnswer("1|1 ;; 2|2 ;; 3|3",
            "SELECT kk, f.value FROM p727 FULL JOIN q727 USING (kk), LATERAL FLATTEN(input => ARRAY_CONSTRUCT(kk)) f "
                + "ORDER BY 1");
        assertAnswer("1|100 ;; 2|200",
            "SELECT kk, x.z FROM p727 FULL JOIN q727 USING (kk), LATERAL (SELECT z FROM r727 WHERE r727.k = p727.kk) "
                + "x ORDER BY 1");
        assertAnswer("1|100 ;; 3|300",
            "SELECT kk, x.z FROM p727 FULL JOIN q727 USING (kk), LATERAL (SELECT z FROM r727 WHERE r727.k = q727.kk) "
                + "x ORDER BY 1");
        assertAnswer("1|1 ;; 2|2 ;; 3|3",
            "SELECT kk, s.value FROM p727 FULL JOIN q727 USING (kk), TABLE(SPLIT_TO_TABLE(kk::VARCHAR, ',')) s ORDER "
                + "BY 1");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'KK'",
            "SELECT kk, x.z FROM p727 NATURAL JOIN q727, LATERAL (SELECT z FROM r727 WHERE r727.k = kk) x ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'KK'",
            "SELECT kk, x.z FROM p727 JOIN q727 USING (kk), LATERAL (SELECT z FROM r727 WHERE r727.k = kk) x ORDER "
                + "BY 1");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'KK'",
            "SELECT kk, x.e FROM p727 FULL JOIN q727 USING (kk), LATERAL (SELECT kk + 0 AS e) x ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3",
            "SELECT kk FROM p727 FULL JOIN q727 USING (kk) WHERE kk IN (SELECT k FROM r727 WHERE r727.k <= kk) ORDER "
                + "BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12",
            "SELECT kk, (SELECT COUNT(*) FROM r727 WHERE r727.k IN (SELECT r2.k FROM r727 r2 WHERE r2.k <= kk)) n "
                + "FROM p727 FULL JOIN q727 USING (kk) ORDER BY 1");
        assertAnswer("1|1 ;; 2|2 ;; 3|3",
            "SELECT kk, (SELECT COUNT(*) FROM r727 WHERE r727.k <= kk) m FROM p727 FULL JOIN q727 USING (kk) JOIN "
                + "r727 rr ON rr.k = kk ORDER BY 1");
        assertAnswer("1|100 ;; 2|200 ;; 3|300",
            "SELECT kk, x.z FROM p727 FULL JOIN q727 USING (kk) JOIN r727 rr ON rr.k = kk, LATERAL (SELECT rr.z AS "
                + "z) x ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'KK'",
            "SELECT kk, x.z FROM p727 FULL JOIN q727 USING (kk) JOIN r727 rr ON rr.k = kk, LATERAL (SELECT z FROM "
                + "r727 WHERE r727.k = kk) x ORDER BY 1");
        assertAnswer("1|1 ;; 2|2 ;; 3|3",
            "SELECT kk, (SELECT COUNT(*) FROM r727 WHERE r727.k <= kk) m FROM p727 FULL JOIN q727 USING (kk) GROUP "
                + "BY kk ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 96 | found QUALIFY clause but no window "
                + "function.",
            "SELECT kk, (SELECT COUNT(*) FROM r727 WHERE r727.k <= kk) m FROM p727 FULL JOIN q727 USING (kk) QUALIFY "
                + "m >= 1 ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'KK'",
            "SELECT kk, x.z FROM p727 FULL JOIN q727 USING (kk), LATERAL (SELECT MAX(z) z FROM r727 WHERE r727.k <= "
                + "kk) x ORDER BY 1");
    }

    @Test
    public void aSharedNameIsAmbiguousInALateralItem() {
        assertAnswer("Table P728 successfully created.", "CREATE OR REPLACE TABLE p728 (kk INT, v INT)");
        assertAnswer("2", "INSERT INTO p728 VALUES (1, 10), (2, 20)");
        assertAnswer("Table Q728 successfully created.", "CREATE OR REPLACE TABLE q728 (kk INT, w INT)");
        assertAnswer("2", "INSERT INTO q728 VALUES (1, 30), (3, 40)");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'KK'",
            "SELECT p728.kk, x.e FROM p728 JOIN q728 ON p728.kk = q728.kk, LATERAL (SELECT kk + 0 AS e) x ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'KK'",
            "SELECT p728.kk, f.value FROM p728 JOIN q728 ON p728.kk = q728.kk, LATERAL FLATTEN(input => "
                + "ARRAY_CONSTRUCT(kk)) f ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'KK'",
            "SELECT p728.kk, (SELECT kk + 0) e FROM p728 JOIN q728 ON p728.kk = q728.kk ORDER BY 1");
        assertAnswer("1|1 ;; 2|2 ;; 3|3",
            "SELECT kk, f.value FROM p728 FULL JOIN q728 USING (kk), LATERAL FLATTEN(input => ARRAY_CONSTRUCT(kk, "
                + "w)) f WHERE f.index = 0 ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'KK'",
            "SELECT kk, x.e FROM p728 LEFT JOIN q728 USING (kk), LATERAL (SELECT kk AS e) x ORDER BY 1");
    }
}
