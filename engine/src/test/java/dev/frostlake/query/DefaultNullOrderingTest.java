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
 * DEFAULT_NULL_ORDERING places the NULLs of every sort key that names no NULLS clause: FIRST puts them first
 * ascending and last descending, LAST and the default the other way round. A query's ORDER BY, its LIMIT and FETCH,
 * a window's ORDER BY with its ranks and frames, WITHIN GROUP, MATCH_RECOGNIZE and a dynamic PIVOT's columns follow
 * it, as do the sort keys EXPLAIN and a refusal print; an explicit NULLS clause, and a JSON null, keep their place
 * (all live-verified).
 */
public class DefaultNullOrderingTest extends MeasuredCellsTestSupport {

    @Test
    public void sortsWindowsAndWithinGroupFollowTheSessionPlacement() {
        assertAnswer("Table T731 successfully created.", "CREATE OR REPLACE TABLE t731 (g INT, a INT, s VARCHAR)");
        assertAnswer("4", "INSERT INTO t731 VALUES (1, 1, 'x'), (1, NULL, NULL), (2, 2, 'y'), (2, NULL, 'z')");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET DEFAULT_NULL_ORDERING = 'FIRST'");
        assertAnswer("null ;; null ;; 1 ;; 2", "SELECT a FROM t731 ORDER BY a");
        assertAnswer("2 ;; 1 ;; null ;; null", "SELECT a FROM t731 ORDER BY a DESC");
        assertAnswer("1 ;; 2 ;; null ;; null", "SELECT a FROM t731 ORDER BY a NULLS LAST");
        assertAnswer("2 ;; 1 ;; null ;; null", "SELECT a FROM t731 ORDER BY a DESC NULLS LAST");
        assertAnswer("null|1 ;; null|2 ;; 1|3 ;; 2|4",
            "SELECT a, ROW_NUMBER() OVER (ORDER BY a) rn FROM t731 ORDER BY rn");
        assertAnswer("2|1 ;; 1|2 ;; null|3 ;; null|4",
            "SELECT a, ROW_NUMBER() OVER (ORDER BY a DESC) rn FROM t731 ORDER BY rn");
        assertAnswer("1|n,1 ;; 2|n,2",
            "SELECT g, LISTAGG(COALESCE(TO_VARCHAR(a), 'n'), ',') WITHIN GROUP (ORDER BY a) l FROM t731 GROUP BY g "
                + "ORDER BY g");
        assertAnswer("[1,2]", "SELECT ARRAY_AGG(a) WITHIN GROUP (ORDER BY a) FROM t731");
        assertAnswer("1|null ;; 1|null ;; 2|null ;; 2|null",
            "SELECT g, FIRST_VALUE(a) OVER (PARTITION BY g ORDER BY a) f FROM t731 ORDER BY g, f");
        assertAnswer("null", "SELECT a FROM t731 ORDER BY a LIMIT 1");
        assertAnswer("null ;; x ;; y ;; z", "SELECT s FROM t731 ORDER BY s");
        assertAnswer("null ;; null ;; null ;; 1 ;; 2",
            "SELECT a FROM (SELECT a FROM t731 UNION ALL SELECT NULL) ORDER BY a");
        assertAnswer("null ;; null ;; 0 ;; 1 ;; 2", "SELECT a FROM t731 UNION ALL SELECT 0 ORDER BY 1");
        assertAnswer("null|1 ;; null|1", "SELECT a, RANK() OVER (ORDER BY a) r FROM t731 QUALIFY r = 1");
        assertAnswer("1|2", "SELECT MIN_BY(g, a), MAX_BY(g, a) FROM t731");
        assertAnswer("null|null ;; null|null ;; 1|null ;; 2|1",
            "SELECT a, LAG(a) OVER (ORDER BY a) l FROM t731 ORDER BY a");
        assertAnswer("null ;; null ;; 1 ;; 2", "SELECT a FROM t731 ORDER BY ALL");
        assertAnswer("2", "SELECT COUNT(*) FROM t731 WHERE a IS NULL");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET DEFAULT_NULL_ORDERING = 'LAST'");
        assertAnswer("1 ;; 2 ;; null ;; null", "SELECT a FROM t731 ORDER BY a");
        assertAnswer("null ;; null ;; 2 ;; 1", "SELECT a FROM t731 ORDER BY a DESC");
        assertAnswer("Statement executed successfully.", "ALTER SESSION UNSET DEFAULT_NULL_ORDERING");
        assertAnswer("null ;; null ;; 2 ;; 1", "SELECT a FROM t731 ORDER BY a DESC");
    }

    @Test
    public void everySortAndEveryPrintedKeyFollowsIt() {
        assertAnswer("Table T733 successfully created.", "CREATE OR REPLACE TABLE t733 (g INT, a INT, s VARCHAR)");
        assertAnswer("4", "INSERT INTO t733 VALUES (1, 1, 'x'), (1, NULL, NULL), (2, 2, 'y'), (2, NULL, 'z')");
        assertAnswer("View V733 successfully created.",
            "CREATE OR REPLACE VIEW v733 AS SELECT a, ROW_NUMBER() OVER (ORDER BY a, g) rn FROM t733");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET DEFAULT_NULL_ORDERING = 'FIRST'");
        assertAnswer("DEFAULT_NULL_ORDERING|FIRST|LAST|SESSION|User-visible parameter that allows customizing null "
                + "ordering:- FIRST: nulls are ordered first in ASC order and last in DESC order- LAST: nulls are "
                + "ordered last in ASC order and first in ASC order|STRING",
            "SHOW PARAMETERS LIKE 'DEFAULT_NULL_ORDERING' IN SESSION");
        assertAnswer("[1,2,1,2]", "SELECT ARRAY_AGG(g) WITHIN GROUP (ORDER BY a, g) FROM t733");
        assertAnswer("[2,1,1,2]", "SELECT ARRAY_AGG(g) WITHIN GROUP (ORDER BY a DESC, g) FROM t733");
        assertAnswer("z,y,x", "SELECT LISTAGG(s, ',') WITHIN GROUP (ORDER BY s DESC) FROM t733");
        assertAnswer("[1,2,undefined]", "SELECT ARRAY_SORT(ARRAY_CONSTRUCT(2, NULL, 1))");
        assertAnswer("[undefined,2,1]", "SELECT ARRAY_SORT(ARRAY_CONSTRUCT(2, NULL, 1), FALSE)");
        assertAnswer("null", "SELECT TOP 1 a FROM t733 ORDER BY a");
        assertAnswer("null|1|1 ;; null|2|3 ;; 1|1|4 ;; 2|2|6",
            "SELECT a, g, SUM(g) OVER (ORDER BY a, g ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) s FROM t733 "
                + "ORDER BY s");
        assertAnswer("null|3 ;; null|3 ;; 1|4 ;; 2|6",
            "SELECT a, SUM(g) OVER (ORDER BY a RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) s FROM t733 ORDER "
                + "BY a, s");
        assertAnswer("null|3 ;; null|3 ;; 1|1 ;; 2|3",
            "SELECT a, SUM(g) OVER (ORDER BY a RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) s FROM t733 ORDER BY a, s");
        assertAnswer("null|1 ;; null|1 ;; 1|1 ;; 2|1",
            "SELECT a, NTH_VALUE(a, 3) OVER (ORDER BY a ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) n "
                + "FROM t733 ORDER BY a");
        assertAnswer("null|0.5 ;; null|0.5 ;; 1|0.75 ;; 2|1.0",
            "SELECT a, CUME_DIST() OVER (ORDER BY a) c FROM t733 ORDER BY a, c");
        assertAnswer("null|1 ;; null|2 ;; 1|3 ;; 2|4",
            "SELECT a, m FROM t733 MATCH_RECOGNIZE (ORDER BY a, g MEASURES MATCH_NUMBER() AS m ALL ROWS PER MATCH "
                + "PATTERN (x) DEFINE x AS TRUE) ORDER BY m");
        assertAnswer("null ;; 1 ;; 2", "SELECT DISTINCT a FROM t733 ORDER BY a");
        assertAnswer("null ;; 1 ;; 2", "SELECT a FROM t733 GROUP BY a ORDER BY a");
        assertAnswer("null ;; null ;; 1 ;; 2", "SELECT a FROM t733 ORDER BY a ASC");
        assertAnswer("null|1 ;; null|2 ;; 1|3 ;; 2|4", "SELECT a, rn FROM v733 ORDER BY rn");
        assertAnswer("true|null ;; false|1 ;; false|null",
            "SELECT v IS NULL, TO_VARCHAR(v) FROM (SELECT PARSE_JSON('null') v UNION ALL SELECT NULL UNION ALL "
                + "SELECT PARSE_JSON('1')) ORDER BY v");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|T733.A|null|"
                + "null|null ;; 1|1|[0]|Sort|null|null|T733.A ASC NULLS FIRST|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.T733, TEST_DB.TEST_SCHEMA.T733|null|A, A|1|1|1024",
            "EXPLAIN SELECT a FROM t733 ORDER BY a");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|T733.A, "
                + "ROW_NUMBER() OVER (ORDER BY T733.A ASC NULLS FIRST)|null|null|null ;; 1|1|[0]|WindowFunction|null|"
                + "null|ROW_NUMBER() OVER (ORDER BY T733.A ASC NULLS FIRST)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.T733, TEST_DB.TEST_SCHEMA.T733|null|A, A|1|1|1024",
            "EXPLAIN SELECT a, ROW_NUMBER() OVER (ORDER BY a) FROM t733");
        assertAnswer("REFUSED SQL compilation error: | Window function [ROW_NUMBER() OVER (ORDER BY T733.A ASC NULLS "
                + "FIRST)] appears outside of SELECT, QUALIFY, and ORDER BY clauses.",
            "SELECT a FROM t733 WHERE ROW_NUMBER() OVER (ORDER BY a) = 1");
        assertAnswer("REFUSED SQL compilation error: | [ROW_NUMBER() OVER (ORDER BY T733.G ASC NULLS FIRST)] is not a "
                + "valid order by expression",
            "SELECT a FROM t733 GROUP BY a ORDER BY ROW_NUMBER() OVER (ORDER BY g)");
        assertAnswer("null ;; null ;; 1 ;; 2",
            "EXECUTE IMMEDIATE $$BEGIN LET rs RESULTSET := (SELECT a FROM t733 ORDER BY a); RETURN TABLE(rs); END$$");
        assertAnswer("null|1 ;; null|2",
            "SELECT a, g FROM t733 QUALIFY ROW_NUMBER() OVER (PARTITION BY g ORDER BY a) = 1 ORDER BY g");
        assertAnswer("REFUSED SQL compilation error: | invalid value [MIDDLE] for parameter 'DEFAULT_NULL_ORDERING'. "
                + "Accepted values: 'LAST', 'FIRST'.'",
            "ALTER SESSION SET DEFAULT_NULL_ORDERING = 'MIDDLE'");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET DEFAULT_NULL_ORDERING = 'last'");
        assertAnswer("1 ;; 2 ;; null ;; null", "SELECT a FROM t733 ORDER BY a");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET DEFAULT_NULL_ORDERING = 'first'");
        assertAnswer("null ;; null ;; 1 ;; 2", "SELECT a FROM t733 ORDER BY a");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET DEFAULT_NULL_ORDERING = FIRST");
        assertAnswer("2 ;; 1 ;; null ;; null", "SELECT a FROM t733 ORDER BY a DESC");
        assertAnswer("REFUSED SQL compilation error: | invalid value [1] for parameter 'DEFAULT_NULL_ORDERING'",
            "ALTER SESSION SET DEFAULT_NULL_ORDERING = 1");
        assertAnswer("DEFAULT_NULL_ORDERING|FIRST|LAST|SESSION|User-visible parameter that allows customizing null "
                + "ordering:- FIRST: nulls are ordered first in ASC order and last in DESC order- LAST: nulls are "
                + "ordered last in ASC order and first in ASC order|STRING",
            "SHOW PARAMETERS LIKE 'DEFAULT_NULL_ORDERING' IN SESSION");
    }

    @Test
    public void rowLimitsSetOperationsAndPivotColumnsFollowIt() {
        assertAnswer("Table T734 successfully created.", "CREATE OR REPLACE TABLE t734 (g INT, a INT)");
        assertAnswer("4", "INSERT INTO t734 VALUES (1, 1), (1, NULL), (2, 2), (2, NULL)");
        assertAnswer("1|2|3", "SELECT * FROM t734 PIVOT (SUM(g) FOR a IN (ANY ORDER BY a))");
        assertAnswer("3|2|1", "SELECT * FROM t734 PIVOT (SUM(g) FOR a IN (ANY ORDER BY a DESC))");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET DEFAULT_NULL_ORDERING = 'FIRST'");
        assertAnswer("3|1|2", "SELECT * FROM t734 PIVOT (SUM(g) FOR a IN (ANY ORDER BY a))");
        assertAnswer("2|1|3", "SELECT * FROM t734 PIVOT (SUM(g) FOR a IN (ANY ORDER BY a DESC))");
        assertAnswer("1|2|3", "SELECT * FROM t734 PIVOT (SUM(g) FOR a IN (ANY ORDER BY a NULLS LAST))");
        answer("SELECT a, PERCENT_RANK() OVER (ORDER BY a) p FROM t734 ORDER BY a, p");
        assertAnswer("null|1 ;; null|2 ;; 1|2 ;; 2|2",
            "SELECT a, CONDITIONAL_TRUE_EVENT(a IS NULL) OVER (ORDER BY a, g) e FROM t734 ORDER BY e, a");
        assertAnswer("null|2 ;; 1|1", "SELECT a, g FROM t734 ORDER BY a, g LIMIT 2 OFFSET 1");
        assertAnswer("null|1", "SELECT a, g FROM t734 ORDER BY a, g FETCH FIRST 1 ROWS ONLY");
        assertAnswer("null ;; null", "SELECT * FROM (SELECT a FROM t734 ORDER BY a LIMIT 2) ORDER BY 1 NULLS LAST");
        assertAnswer("null ;; 1 ;; 2",
            "SELECT a FROM t734 WHERE g = 1 UNION SELECT a FROM t734 WHERE g = 2 ORDER BY a");
        assertAnswer("null|2 ;; null|4 ;; 1|1 ;; 2|1",
            "SELECT a, COUNT(*) FROM t734 GROUP BY ROLLUP (a) ORDER BY a, 2");
    }
}
