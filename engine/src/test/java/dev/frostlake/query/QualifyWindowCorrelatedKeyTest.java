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
 * A subquery reading the outer row as the ordering key of a window the QUALIFY reads is refused, at the subquery's
 * SELECT, as {@code Unsupported subquery type cannot be evaluated} (live-verified), while the same key in a select
 * list's window, an uncorrelated key and a window's argument are answered.
 */
public class QualifyWindowCorrelatedKeyTest extends MeasuredCellsTestSupport {

    @Test
    public void aCorrelatedKeyOfAQualifyWindowIsRefused() {
        engine.execute("CREATE OR REPLACE TABLE rr (o INT, v INT)");
        engine.execute("INSERT INTO rr VALUES (1, 1), (2, 2), (3, 4)");
        assertCells(new String[][] {
            {"SELECT r1.o FROM rr r1 QUALIFY ROW_NUMBER() OVER (ORDER BY (SELECT COUNT(*) FROM rr r2 WHERE r2.o "
                + "<= r1.o) DESC) = 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 60"},
            {"SELECT ROW_NUMBER() OVER (ORDER BY (SELECT COUNT(*) FROM rr r2 WHERE r2.o <= r1.o)) FROM rr r1",
                "1 ;; 2 ;; 3"},
            {"SELECT ROW_NUMBER() OVER (PARTITION BY (SELECT COUNT(*) FROM rr r2 WHERE r2.o <= r1.o) ORDER BY "
                + "o) FROM rr r1",
                "1 ;; 1 ;; 1"},
            {"SELECT SUM((SELECT COUNT(*) FROM rr r2 WHERE r2.o <= r1.o)) OVER () FROM rr r1", "6 ;; 6 ;; 6"},
            {"SELECT ROW_NUMBER() OVER (ORDER BY (SELECT COUNT(*) FROM rr r2)) FROM rr r1 ORDER BY 1", "1 ;; 2 ;; 3"},
            {"SELECT SUM((SELECT MAX(v) FROM rr)) OVER () FROM rr r1 LIMIT 1", "12"},
            {"SELECT o, RANK() OVER (ORDER BY (SELECT r2.v FROM rr r2 WHERE r2.o = r1.o)) FROM rr r1 ORDER BY o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 33"},
            {"SELECT o,\n  ROW_NUMBER() OVER (ORDER BY (SELECT COUNT(*) FROM rr r2 WHERE r2.o <= r1.o)) FROM rr r1",
                "1|1 ;; 2|2 ;; 3|3"},
        });
    }
}
