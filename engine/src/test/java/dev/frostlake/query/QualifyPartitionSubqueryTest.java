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
 * A subquery reading the outer row as the one PARTITION BY key of an aggregate window the QUALIFY reads, with no
 * ORDER BY: refused as the window's argument is no group by expression, the argument named as the plan converts
 * it, and a COUNT(*) as an unsupported subquery at the subquery; a second key, an ORDER BY, an uncorrelated key
 * and a select list's window are answered (all live-verified).
 */
public class QualifyPartitionSubqueryTest extends MeasuredCellsTestSupport {

    @Test
    public void partitionKeys() {
        engine.execute("CREATE OR REPLACE TABLE rr (o INT, v INT, d NUMBER(10,2), s VARCHAR)");
        engine.execute("INSERT INTO rr VALUES (1, 1, 1.5, 'a'), (2, 2, 2.5, 'b'), (3, 4, 3.5, 'c')");
        assertAnswer("REFUSED SQL compilation error: | [CAST(R1.V AS NUMBER(38,0))] is not a valid group by expression",
            "SELECT o FROM rr r1 QUALIFY SUM(v) OVER (PARTITION BY (SELECT MAX(r2.v) FROM rr r2 WHERE r2.o < r1.o)) "
                + "> 0 ORDER BY o");
        assertAnswer("REFUSED SQL compilation error: | [CAST(R1.D AS NUMBER(10,2))] is not a valid group by expression",
            "SELECT o FROM rr r1 QUALIFY SUM(d) OVER (PARTITION BY (SELECT MAX(r2.v) FROM rr r2 WHERE r2.o < r1.o)) "
                + "> 0 ORDER BY o");
        assertAnswer("REFUSED SQL compilation error: | [R1.S] is not a valid group by expression",
            "SELECT o FROM rr r1 QUALIFY MAX(s) OVER (PARTITION BY (SELECT MAX(r2.v) FROM rr r2 WHERE r2.o < r1.o)) "
                + "> '' ORDER BY o");
        assertAnswer("REFUSED SQL compilation error: | [R1.V] is not a valid group by expression",
            "SELECT o FROM rr r1 QUALIFY COUNT(v) OVER (PARTITION BY (SELECT MAX(r2.v) FROM rr r2 WHERE r2.o < "
                + "r1.o)) > 0 ORDER BY o");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 57",
            "SELECT o FROM rr r1 QUALIFY COUNT(*) OVER (PARTITION BY (SELECT MAX(r2.v) FROM rr r2 WHERE r2.o < "
                + "r1.o)) > 0 ORDER BY o");
        assertAnswer("REFUSED SQL compilation error: | [CAST(CAST(R1.V AS NUMBER(38,3)) AS NUMBER(38,3))] is not a "
                + "valid group by expression",
            "SELECT o FROM rr r1 QUALIFY AVG(v) OVER (PARTITION BY (SELECT MAX(r2.v) FROM rr r2 WHERE r2.o < r1.o)) "
                + "> 0 ORDER BY o");
        assertAnswer("1 ;; 2 ;; 3",
            "SELECT o FROM rr r1 QUALIFY ROW_NUMBER() OVER (PARTITION BY (SELECT MAX(r2.v) FROM rr r2 WHERE r2.o < "
                + "r1.o) ORDER BY o) = 1 ORDER BY o");
        assertAnswer("REFUSED SQL compilation error: | [CAST(R1.V + 1 AS NUMBER(38,0))] is not a valid group by "
                + "expression",
            "SELECT o FROM rr r1 QUALIFY SUM(v + 1) OVER (PARTITION BY (SELECT MAX(r2.v) FROM rr r2 WHERE r2.o < "
                + "r1.o)) > 0 ORDER BY o");
        assertAnswer("1 ;; 2 ;; 3",
            "SELECT o FROM rr r1 QUALIFY SUM(v) OVER (PARTITION BY o, (SELECT MAX(r2.v) FROM rr r2 WHERE r2.o < "
                + "r1.o)) > 0 ORDER BY o");
        assertAnswer("1 ;; 2 ;; 3",
            "SELECT o FROM rr r1 QUALIFY SUM(v) OVER (PARTITION BY (SELECT MAX(r2.v) FROM rr r2)) > 0 ORDER BY o");
        assertAnswer("1|1 ;; 2|2 ;; 3|4",
            "SELECT o, SUM(v) OVER (PARTITION BY (SELECT MAX(r2.v) FROM rr r2 WHERE r2.o < r1.o)) FROM rr r1 ORDER "
                + "BY o");
        assertAnswer("1 ;; 2 ;; 3",
            "SELECT o FROM rr r1 QUALIFY SUM(v) OVER (PARTITION BY (SELECT MAX(r2.v) FROM rr r2 WHERE r2.o < r1.o) "
                + "ORDER BY o) > 0 ORDER BY o");
    }
}
