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
 * SYSTEM$TYPEOF types a correlated scalar subquery that is no aggregate, which the statement could not run:
 * its one item's declared type and storage, under arithmetic, in a WHERE and over a UNION ALL, and a subquery two
 * or three levels down whose aggregate select list reads the outermost query; the subquery run as a value, and one
 * of several columns, are refused (all live-verified).
 */
public class CorrelatedSubqueryTypeOfTest extends MeasuredCellsTestSupport {

    @Test
    public void aCorrelatedSubqueryIsTypedNotRun() {
        assertAnswer("Table T737 successfully created.",
            "CREATE OR REPLACE TABLE t737 (n NUMBER(5,2), big NUMBER(38,0))");
        assertAnswer("2", "INSERT INTO t737 VALUES (1.5, 5), (2.5, 3000000000)");
        assertAnswer("Table O737 successfully created.", "CREATE OR REPLACE TABLE o737 (x NUMBER(10,2), w VARCHAR(9))");
        assertAnswer("2", "INSERT INTO o737 VALUES (1.25, 'p'), (2.5, 'qq')");
        assertAnswer("NUMBER(11,2)[SB2]",
            "SELECT SYSTEM$TYPEOF((SELECT x + t737.n FROM o737 WHERE x = 1.25)) FROM t737 LIMIT 1");
        assertAnswer("NUMBER(11,2)[SB2]",
            "SELECT SYSTEM$TYPEOF((SELECT (SELECT MAX(x) + t737.n FROM o737) FROM o737 WHERE x = 1.25)) FROM t737 "
                + "LIMIT 1");
        assertAnswer("NUMBER(10,2)[SB2]",
            "SELECT SYSTEM$TYPEOF((SELECT x FROM o737 WHERE x = t737.n)) FROM t737 LIMIT 1");
        assertAnswer("VARCHAR(9)[LOB]",
            "SELECT SYSTEM$TYPEOF((SELECT w FROM o737 WHERE x = t737.n)) FROM t737 LIMIT 1");
        assertAnswer("NUMBER(12,2)[SB2]",
            "SELECT SYSTEM$TYPEOF(1 + (SELECT x + t737.n FROM o737 WHERE x = 1.25)) FROM t737 LIMIT 1");
        assertAnswer("NUMBER(38,2)[SB8] ;; NUMBER(38,2)[SB8]",
            "SELECT SYSTEM$TYPEOF((SELECT x + t737.big FROM o737 WHERE x = 1.25)) FROM t737 ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8",
            "SELECT (SELECT x + t737.n FROM o737 WHERE x = 1.25) FROM t737 ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 15",
            "SELECT TYPEOF((SELECT x + t737.n FROM o737 WHERE x = 1.25)::VARIANT) FROM t737 LIMIT 1");
        assertAnswer("1.50 ;; 2.50",
            "SELECT n FROM t737 WHERE SYSTEM$TYPEOF((SELECT x + t737.n FROM o737 WHERE x = 1.25)) LIKE 'NUMBER%' "
                + "ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 21 | Unsupported: Scalar subquery with "
                + "multi-column SELECT clause.",
            "SELECT SYSTEM$TYPEOF((SELECT x + t737.n, w FROM o737 WHERE x = 1.25)) FROM t737 LIMIT 1");
        assertAnswer("NUMBER(11,2)[SB2]",
            "SELECT SYSTEM$TYPEOF((SELECT x + t737.n FROM o737 WHERE x = 1.25 UNION ALL SELECT 1)) FROM t737 LIMIT 1");
    }

    @Test
    public void aNestedSubqueryReadingTheOutermostQueryIsTyped() {
        assertAnswer("Table T739 successfully created.",
            "CREATE OR REPLACE TABLE t739 (n NUMBER(5,2), big NUMBER(38,0))");
        assertAnswer("2", "INSERT INTO t739 VALUES (1.5, 5), (2.5, 3000000000)");
        assertAnswer("Table O739 successfully created.", "CREATE OR REPLACE TABLE o739 (x NUMBER(10,2), w VARCHAR(9))");
        assertAnswer("2", "INSERT INTO o739 VALUES (1.25, 'p'), (2.5, 'qq')");
        assertAnswer("NUMBER(11,2)[SB2]",
            "SELECT SYSTEM$TYPEOF((SELECT (SELECT MAX(x) + t739.n FROM o739) FROM o739)) FROM t739 LIMIT 1");
        assertAnswer("NUMBER(11,2)[SB2]",
            "SELECT SYSTEM$TYPEOF((SELECT (SELECT MAX(x) + n FROM o739) FROM o739)) FROM t739 LIMIT 1");
        assertAnswer("NUMBER(38,0)[SB8]",
            "SELECT SYSTEM$TYPEOF((SELECT (SELECT COUNT(*) + t739.big FROM o739) FROM o739 WHERE x = 1.25)) FROM "
                + "t739 LIMIT 1");
        assertAnswer("VARCHAR(134217728)[LOB]",
            "SELECT SYSTEM$TYPEOF((SELECT (SELECT MAX(w) || t739.n FROM o739) FROM o739 WHERE x = 1.25)) FROM t739 "
                + "LIMIT 1");
        assertAnswer("NUMBER(11,2)[SB2]",
            "SELECT SYSTEM$TYPEOF((SELECT (SELECT (SELECT MAX(x) + t739.n FROM o739) FROM o739 WHERE x = 2.5) FROM "
                + "o739 WHERE x = 1.25)) FROM t739 LIMIT 1");
        assertAnswer("REFUSED SQL compilation error: | [O739.X] is not a valid group by expression",
            "SELECT SYSTEM$TYPEOF((SELECT (SELECT MAX(x) + o739.x FROM o739) FROM o739)) FROM t739 LIMIT 1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 46 | invalid identifier 'T739.NOPE'",
            "SELECT SYSTEM$TYPEOF((SELECT (SELECT MAX(x) + t739.nope FROM o739) FROM o739)) FROM t739 LIMIT 1");
        assertAnswer("NUMBER(11,2)[SB2]",
            "SELECT SYSTEM$TYPEOF((SELECT (SELECT MAX(x) + t739.n FROM o739) FROM o739 oo WHERE oo.x = 1.25)) FROM "
                + "t739 LIMIT 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8",
            "SELECT (SELECT (SELECT MAX(x) + t739.n FROM o739) FROM o739 WHERE x = 1.25) FROM t739 ORDER BY 1");
    }
}
