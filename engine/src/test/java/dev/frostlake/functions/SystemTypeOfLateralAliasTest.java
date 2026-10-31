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
 * SYSTEM$TYPEOF of an earlier select item's alias in a query over a table names the aliased expression's type
 * and storage, inside an expression too; a relation's column of the name wins over the alias, and an alias written
 * later is no name yet (all live-verified).
 */
public class SystemTypeOfLateralAliasTest extends MeasuredCellsTestSupport {

    @Test
    public void lateralAliases() {
        engine.execute("CREATE OR REPLACE TABLE t28 (s VARCHAR, n INT, v VARCHAR(10))");
        engine.execute("INSERT INTO t28 VALUES ('a', 1, 'b')");
        assertAnswer("A|VARCHAR(50331648)[LOB]|2|NUMBER(38,0)[SB1]|1|NUMBER(38,0)[SB1]",
            "SELECT UPPER(s) AS x, SYSTEM$TYPEOF(x), n + 1 AS y, SYSTEM$TYPEOF(y), n AS z, SYSTEM$TYPEOF(z) FROM t28");
        assertAnswer("5|NUMBER(1,0)[SB1]", "SELECT 5 AS y, SYSTEM$TYPEOF(y)");
        assertAnswer("a|VARCHAR(16777216)[LOB]", "SELECT s AS x, SYSTEM$TYPEOF(x) FROM t28");
        assertAnswer("b|VARCHAR(10)[LOB]", "SELECT v AS x, SYSTEM$TYPEOF(x) FROM t28");
        assertAnswer("2.5|NUMBER(38,1)[SB1]", "SELECT n * 2.5 AS y, SYSTEM$TYPEOF(y) FROM t28");
        assertAnswer("NUMBER(38,0)[SB1]|VARCHAR(16777216)[LOB]|VARCHAR(10)[LOB]",
            "SELECT SYSTEM$TYPEOF(n), SYSTEM$TYPEOF(s), SYSTEM$TYPEOF(v) FROM t28");
        assertAnswer("1|VARCHAR(10)[LOB]", "SELECT CAST(n AS VARCHAR(10)) AS c, SYSTEM$TYPEOF(c) FROM t28");
        assertAnswer("1|NUMBER(38,0)[SB1]", "SELECT n AS n2, SYSTEM$TYPEOF(n2 + 1) FROM t28");
        assertAnswer("1|VARCHAR(16777216)[LOB]", "SELECT n AS s, SYSTEM$TYPEOF(s) FROM t28");
        assertAnswer("5|NUMBER(1,0)[SB1]", "SELECT 5 AS y, SYSTEM$TYPEOF(y) FROM t28");
        assertAnswer("1|2|NUMBER(38,0)[SB1]", "SELECT n AS a, a + 1 AS b, SYSTEM$TYPEOF(b) FROM t28");
        assertAnswer("1|NUMBER(38,0)[SB1]", "SELECT t.n AS q, SYSTEM$TYPEOF(q) FROM t28 t");
        assertAnswer("1|NUMBER(38,0)[SB1]", "SELECT x.n AS q, SYSTEM$TYPEOF(q) FROM (SELECT n FROM t28) x");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'Q'",
            "SELECT SYSTEM$TYPEOF(q), n AS q FROM t28");
    }
}
