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
 * A grouped or aggregating select list's later item typed from an earlier item's alias, as an ungrouped list
 * types it: SYSTEM$TYPEOF and arithmetic over the alias (all live-verified).
 */
public class GroupedAliasTypeofTest extends MeasuredCellsTestSupport {

    @Test
    public void earlierAliasTypes() {
        engine.execute("CREATE OR REPLACE TABLE t28 (s VARCHAR, n INT, v VARCHAR(10))");
        engine.execute("INSERT INTO t28 VALUES ('x', 1, 'y')");
        assertAnswer("1|NUMBER(38,0)[SB1]", "SELECT n AS g, SYSTEM$TYPEOF(g) FROM t28 GROUP BY n");
        assertAnswer("1|NUMBER(18,0)[SB1]", "SELECT COUNT(*) AS k, SYSTEM$TYPEOF(k) FROM t28");
        assertAnswer("y|VARCHAR(10)[LOB]", "SELECT v AS w, SYSTEM$TYPEOF(w) FROM t28 GROUP BY v");
        assertAnswer("1|NUMBER(38,0)[SB8]", "SELECT SUM(n) AS k, SYSTEM$TYPEOF(k + 1) FROM t28");
        assertAnswer("1|NUMBER(38,0)[SB1] ;; null|NUMBER(38,0)[SB1]",
            "SELECT n AS g, SYSTEM$TYPEOF(g) FROM t28 GROUP BY GROUPING SETS ((n), ())");
        assertAnswer("x|VARCHAR(16777216)[LOB]", "SELECT MAX(s) AS m, SYSTEM$TYPEOF(m) FROM t28");
        assertAnswer("1|NUMBER(38,0)[SB1]", "SELECT n AS g, SYSTEM$TYPEOF(g) FROM t28");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void moreAliasTypes() {
        engine.execute("CREATE OR REPLACE TABLE t28 (s VARCHAR, n INT, v VARCHAR(10))");
        engine.execute("INSERT INTO t28 VALUES ('x', 1, 'y')");
        assertAnswer("1|NUMBER(38,0)[SB1]", "SELECT n AS g, SYSTEM$TYPEOF(g) FROM t28 GROUP BY ALL");
        assertAnswer("1|NUMBER(18,0)[SB8]", "SELECT COUNT(*) AS k, SYSTEM$TYPEOF(k) FROM t28 GROUP BY n");
        assertAnswer("1|2|NUMBER(38,0)[SB1]", "SELECT n AS g, g + 1 AS h, SYSTEM$TYPEOF(h) FROM t28 GROUP BY n");
        assertAnswer("1|NUMBER(38,0)[SB1] ;; null|NUMBER(38,0)[SB1]",
            "SELECT n AS g, SYSTEM$TYPEOF(g) FROM t28 GROUP BY ROLLUP (n)");
        assertAnswer("1.000000|NUMBER(38,6)[SB8]", "SELECT AVG(n) AS a, SYSTEM$TYPEOF(a) FROM t28");
        assertAnswer("1|NUMBER(38,0)[SB1]", "SELECT n AS g, SYSTEM$TYPEOF(g) FROM t28 GROUP BY n HAVING COUNT(*) > 0");
        assertAnswer("x|VARCHAR(134217728)[LOB]", "SELECT LISTAGG(s) AS l, SYSTEM$TYPEOF(l) FROM t28");
        assertAnswer("1|0.333333", "SELECT COUNT(*) AS k, k / 3 FROM t28");
        assertAnswer("1|0.142857", "SELECT SUM(n) AS k, k / 7 FROM t28");
        assertAnswer("1.000000|1.5000000", "SELECT AVG(n) AS a, a * 1.5 FROM t28");
        assertAnswer("1|NUMBER(38,0)[SB1]|1", "SELECT n AS g, SYSTEM$TYPEOF(g), COUNT(*) FROM t28 GROUP BY n");
        assertAnswer("x|VARCHAR(16777217)[LOB]", "SELECT s AS g, SYSTEM$TYPEOF(g || 'x') FROM t28 GROUP BY s");
        assertAnswer("y|VARCHAR(10)[LOB]", "SELECT MIN(v) AS m, SYSTEM$TYPEOF(m) FROM t28");
        assertAnswer("1|NUMBER(38,0)[SB1] ;; null|NUMBER(38,0)[SB1]",
            "SELECT n AS g, SYSTEM$TYPEOF(g) FROM t28 GROUP BY CUBE (n)");
        assertAnswer("1|NUMBER(18,0)[SB8]|NUMBER(19,0)[SB16]",
            "SELECT COUNT(*) AS k, SYSTEM$TYPEOF(k), SYSTEM$TYPEOF(k + 1) FROM t28 GROUP BY s");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
