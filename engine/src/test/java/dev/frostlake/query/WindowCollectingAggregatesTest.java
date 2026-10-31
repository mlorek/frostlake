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
 * ARRAY_AGG, ARRAY_UNIQUE_AGG, ARRAY_UNION_AGG and OBJECT_AGG keep their ARRAY and OBJECT types as window
 * functions, in a result's columns, SYSTEM$TYPEOF and a table created from them; and a WITHIN GROUP ordering
 * orders what a windowed ARRAY_AGG or LISTAGG collects, DISTINCT included (all live-verified).
 */
public class WindowCollectingAggregatesTest extends MeasuredCellsTestSupport {

    @Test
    public void windowedCollectingCallsAreArraysAndObjects() {
        assertAnswer("Table T735C successfully created.", "CREATE OR REPLACE TABLE t735c (g INT, i INT, s VARCHAR)");
        assertAnswer("3", "INSERT INTO t735c VALUES (1, 1, 'a'), (1, 2, 'b'), (2, 3, NULL)");
        assertShape("A:ARRAY, O:OBJECT, U:ARRAY",
            "SELECT ARRAY_AGG(i) OVER (PARTITION BY g) a, OBJECT_AGG(s, i::VARIANT) OVER (PARTITION BY g) o, "
                + "ARRAY_UNIQUE_AGG(i) OVER () u FROM t735c");
        assertAnswer("1|[1,2]|ARRAY[LOB] ;; 2|[1,2]|ARRAY[LOB] ;; 3|[3]|ARRAY[LOB]",
            "SELECT i, ARRAY_AGG(i) OVER (PARTITION BY g) a, SYSTEM$TYPEOF(ARRAY_AGG(i) OVER (PARTITION BY g)) ta "
                + "FROM t735c ORDER BY i");
        assertAnswer("1|{\"a\":1,\"b\":2}|OBJECT[LOB] ;; 2|{\"a\":1,\"b\":2}|OBJECT[LOB] ;; 3|{}|OBJECT[LOB]",
            "SELECT i, OBJECT_AGG(s, i::VARIANT) OVER (PARTITION BY g) o, SYSTEM$TYPEOF(OBJECT_AGG(s, i::VARIANT) "
                + "OVER (PARTITION BY g)) t FROM t735c ORDER BY i");
        assertAnswer("1|[1,2,3]|ARRAY[LOB] ;; 2|[1,2,3]|ARRAY[LOB] ;; 3|[1,2,3]|ARRAY[LOB]",
            "SELECT i, ARRAY_UNIQUE_AGG(i) OVER () u, SYSTEM$TYPEOF(ARRAY_UNIQUE_AGG(i) OVER ()) t FROM t735c ORDER "
                + "BY i");
        assertAnswer("1|[1,2,3]|ARRAY[LOB] ;; 2|[1,2,3]|ARRAY[LOB] ;; 3|[1,2,3]|ARRAY[LOB]",
            "SELECT i, ARRAY_UNION_AGG(a) OVER () u, SYSTEM$TYPEOF(ARRAY_UNION_AGG(a) OVER ()) t FROM (SELECT i, "
                + "ARRAY_CONSTRUCT(i) a FROM t735c) ORDER BY i");
        assertAnswer("1|[2,1] ;; 2|[2,1] ;; 3|[3]",
            "SELECT i, ARRAY_AGG(i) WITHIN GROUP (ORDER BY i DESC) OVER (PARTITION BY g) a FROM t735c ORDER BY i");
        assertAnswer("1|[1] ;; 2|[1,2] ;; 3|[3]",
            "SELECT i, ARRAY_AGG(i) OVER (PARTITION BY g ORDER BY i) a FROM t735c ORDER BY i");
        assertAnswer("Table C735 successfully created.",
            "CREATE OR REPLACE TABLE c735 AS SELECT g, i, ARRAY_AGG(i) OVER (PARTITION BY g) a, OBJECT_AGG(s, "
                + "i::VARIANT) OVER (PARTITION BY g) o, ARRAY_UNIQUE_AGG(i) OVER () u FROM t735c");
        assertAnswer("G|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; I|NUMBER(38,0)|COLUMN|Y|null|"
                + "N|N|null|null|null|null|null|null ;; A|ARRAY|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; O|"
                + "OBJECT|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; U|ARRAY|COLUMN|Y|null|N|N|null|null|null|"
                + "null|null|null",
            "DESCRIBE TABLE c735");
        assertAnswer("1|1|1|3 ;; 2|1|1|3 ;; 3|3|null|3", "SELECT i, a[0], o:a, u[2] FROM c735 ORDER BY i");
        assertAnswer("1|ARRAY|OBJECT ;; 2|ARRAY|OBJECT ;; 3|ARRAY|OBJECT",
            "SELECT i, TYPEOF(a), TYPEOF(o) FROM c735 ORDER BY i");
        assertAnswer("3", "SELECT ARRAY_SIZE(ARRAY_AGG(i) OVER ()) FROM t735c LIMIT 1");
        assertAnswer("1|1 ;; 2|1 ;; 3|3", "SELECT i, (ARRAY_AGG(i) OVER (PARTITION BY g))[0] f FROM t735c ORDER BY i");
    }

    @Test
    public void withinGroupOrdersAWindowedCollector() {
        assertAnswer("Table T736C successfully created.", "CREATE OR REPLACE TABLE t736c (g INT, i INT, s VARCHAR)");
        assertAnswer("4", "INSERT INTO t736c VALUES (1, 1, 'a'), (1, 2, 'b'), (2, 3, NULL), (2, 4, 'c')");
        assertAnswer("1|[2,1] ;; 2|[2,1] ;; 3|[4,3] ;; 4|[4,3]",
            "SELECT i, ARRAY_AGG(i) WITHIN GROUP (ORDER BY i DESC) OVER (PARTITION BY g) a FROM t736c ORDER BY i");
        assertAnswer("1|b,a ;; 2|b,a ;; 3|c ;; 4|c",
            "SELECT i, LISTAGG(s, ',') WITHIN GROUP (ORDER BY s DESC) OVER (PARTITION BY g) l FROM t736c ORDER BY i");
        assertAnswer("1|[\"c\",\"b\",\"a\"] ;; 2|[\"c\",\"b\",\"a\"] ;; 3|[\"c\",\"b\",\"a\"] ;; 4|[\"c\",\"b\",\"a\"]",
            "SELECT i, ARRAY_AGG(s) WITHIN GROUP (ORDER BY i DESC) OVER () a FROM t736c ORDER BY i");
        assertAnswer("1|[2,1] ;; 2|[2,1] ;; 3|[2,1] ;; 4|[2,1]",
            "SELECT i, ARRAY_AGG(DISTINCT g) WITHIN GROUP (ORDER BY g DESC) OVER () a FROM t736c ORDER BY i");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 54 | Window frame requires an ORDER BY "
                + "clause.",
            "SELECT i, ARRAY_AGG(i) WITHIN GROUP (ORDER BY i DESC) OVER (PARTITION BY g ROWS BETWEEN UNBOUNDED "
                + "PRECEDING AND UNBOUNDED FOLLOWING) a FROM t736c ORDER BY i");
        assertAnswer("1|ARRAY[LOB] ;; 2|ARRAY[LOB] ;; 3|ARRAY[LOB] ;; 4|ARRAY[LOB]",
            "SELECT i, SYSTEM$TYPEOF(ARRAY_AGG(i) WITHIN GROUP (ORDER BY i) OVER ()) t FROM t736c ORDER BY i");
        assertAnswer("1|VARCHAR(134217728)[LOB] ;; 2|VARCHAR(134217728)[LOB] ;; 3|VARCHAR(134217728)[LOB] ;; 4|"
                + "VARCHAR(134217728)[LOB]",
            "SELECT i, SYSTEM$TYPEOF(LISTAGG(s) OVER ()) t FROM t736c ORDER BY i");
        assertShape("A:ARRAY, L:VARCHAR(134217728), U:ARRAY",
            "SELECT ARRAY_AGG(i) WITHIN GROUP (ORDER BY i) OVER () a, LISTAGG(s) OVER () l, "
                + "ARRAY_UNION_AGG(ARRAY_CONSTRUCT(i)) OVER () u FROM t736c");
        assertAnswer("1|{\"a\":1,\"b\":2} ;; 2|{\"a\":1,\"b\":2} ;; 3|{\"c\":4} ;; 4|{\"c\":4}",
            "SELECT i, OBJECT_AGG(s, i) OVER (PARTITION BY g) o FROM t736c ORDER BY i");
        assertAnswer("1|1 ;; 2|1 ;; 3|3 ;; 4|3",
            "SELECT i, ARRAY_AGG(i) OVER (PARTITION BY g) [0] f FROM t736c ORDER BY i");
    }
}
