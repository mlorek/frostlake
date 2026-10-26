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
 * A grouped query's select alias reads as the type of the item it names wherever the query reads it: a later
 * item, HAVING, ORDER BY and QUALIFY. OBJECT_PICK over an alias of an ARRAY aggregate picks the array's keys, an
 * alias cast to VARIANT is one text key, and SYSTEM$TYPEOF names the ARRAY (all live-verified).
 */
public class GroupedAliasTypesTest extends MeasuredCellsTestSupport {

    @Test
    public void laterItemsHavingAndOrderBy() {
        engine.execute("CREATE OR REPLACE TABLE pk753 (o OBJECT)");
        engine.execute("INSERT INTO pk753 SELECT OBJECT_CONSTRUCT('a', 1, 'b', 2, 'c', 3)");
        assertAnswer("[\"a\",\"b\",\"c\"]|{\"a\":1,\"b\":2,\"c\":3}",
            "SELECT ARRAY_AGG(x) WITHIN GROUP (ORDER BY x) a, OBJECT_PICK(ANY_VALUE(o), a) p FROM pk753, (SELECT 'b' "
                + "x, 1 k UNION ALL SELECT 'c', 1 UNION ALL SELECT 'a', 2)");
        assertAnswer("1|[\"b\",\"c\"]|{\"b\":2,\"c\":3} ;; 2|[\"a\"]|{\"a\":1}",
            "SELECT k, ARRAY_AGG(x) WITHIN GROUP (ORDER BY x) a, OBJECT_PICK(ANY_VALUE(o), a) p FROM pk753, (SELECT "
                + "'b' x, 1 k UNION ALL SELECT 'c', 1 UNION ALL SELECT 'a', 2) GROUP BY k ORDER BY k");
        assertAnswer("[\"a\",\"b\",\"c\"]|[\"a\",\"b\",\"c\"]|{\"a\":1,\"b\":2,\"c\":3}",
            "SELECT ARRAY_AGG(x) WITHIN GROUP (ORDER BY x) a, a b, OBJECT_PICK(ANY_VALUE(o), b) p FROM pk753, "
                + "(SELECT 'b' x, 1 k UNION ALL SELECT 'c', 1 UNION ALL SELECT 'a', 2)");
        assertAnswer("[\"a\",\"b\",\"c\"]|{}",
            "SELECT ARRAY_AGG(x) WITHIN GROUP (ORDER BY x)::VARIANT a, OBJECT_PICK(ANY_VALUE(o), a) p FROM pk753, "
                + "(SELECT 'b' x, 1 k UNION ALL SELECT 'c', 1 UNION ALL SELECT 'a', 2)");
        assertAnswer("[\"a\",\"c\"]|{\"a\":1,\"c\":3}",
            "SELECT ARRAY_CONSTRUCT('a', 'c') a, OBJECT_PICK(ANY_VALUE(o), a) p FROM pk753");
        assertAnswer("[\"a\",\"b\",\"c\"]|ARRAY[LOB]",
            "SELECT ARRAY_AGG(x) WITHIN GROUP (ORDER BY x) a, SYSTEM$TYPEOF(a) t FROM (SELECT 'b' x, 1 k UNION ALL "
                + "SELECT 'c', 1 UNION ALL SELECT 'a', 2)");
        assertAnswer("[\"b\",\"c\",\"a\"]|3",
            "SELECT ARRAY_UNIQUE_AGG(x) a, ARRAY_SIZE(OBJECT_KEYS(OBJECT_PICK(ANY_VALUE(o), a))) n FROM pk753, "
                + "(SELECT 'b' x, 1 k UNION ALL SELECT 'c', 1 UNION ALL SELECT 'a', 2)");
        assertAnswer("1|[\"b\",\"c\"]",
            "SELECT k, ARRAY_AGG(x) WITHIN GROUP (ORDER BY x) a FROM pk753, (SELECT 'b' x, 1 k UNION ALL SELECT 'c', "
                + "1 UNION ALL SELECT 'a', 2) GROUP BY k HAVING ARRAY_SIZE(OBJECT_KEYS(OBJECT_PICK(ANY_VALUE(o), "
                + "a))) = 2 ORDER BY k");
        assertAnswer("2|[\"a\"] ;; 1|[\"b\",\"c\"]",
            "SELECT k, ARRAY_AGG(x) WITHIN GROUP (ORDER BY x) a FROM pk753, (SELECT 'b' x, 1 k UNION ALL SELECT 'c', "
                + "1 UNION ALL SELECT 'a', 2) GROUP BY k ORDER BY ARRAY_SIZE(OBJECT_KEYS(OBJECT_PICK(ANY_VALUE(o), "
                + "a))), k");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 33 | invalid identifier 'A'",
            "SELECT OBJECT_PICK(ANY_VALUE(o), a) p, ARRAY_AGG(x) WITHIN GROUP (ORDER BY x) a FROM pk753, (SELECT 'b' "
                + "x, 1 k UNION ALL SELECT 'c', 1 UNION ALL SELECT 'a', 2)");
    }

    @Test
    public void moreShapesOfTheAlias() {
        engine.execute("CREATE OR REPLACE TABLE pk754 (o OBJECT)");
        engine.execute("INSERT INTO pk754 SELECT OBJECT_CONSTRUCT('a', 1, 'b', 2, 'c', 3)");
        assertAnswer("1|[\"b\",\"c\"] ;; 2|[\"a\"]",
            "SELECT k, ARRAY_AGG(x) WITHIN GROUP (ORDER BY x) a FROM pk754, (SELECT 'b' x, 1 k UNION ALL SELECT 'c', "
                + "1 UNION ALL SELECT 'a', 2) GROUP BY k HAVING SYSTEM$TYPEOF(a) LIKE 'ARRAY%' ORDER BY k");
        assertAnswer("1|[\"b\",\"c\"] ;; 2|[\"a\"]",
            "SELECT k, ARRAY_AGG(x) WITHIN GROUP (ORDER BY x)::VARIANT a FROM pk754, (SELECT 'b' x, 1 k UNION ALL "
                + "SELECT 'c', 1 UNION ALL SELECT 'a', 2) GROUP BY k HAVING "
                + "ARRAY_SIZE(OBJECT_KEYS(OBJECT_PICK(ANY_VALUE(o), a))) = 0 ORDER BY k");
        assertAnswer("1|[\"b\",\"c\"]|1",
            "SELECT k, ARRAY_AGG(x) WITHIN GROUP (ORDER BY x) a, ROW_NUMBER() OVER (ORDER BY k) rn FROM pk754, "
                + "(SELECT 'b' x, 1 k UNION ALL SELECT 'c', 1 UNION ALL SELECT 'a', 2) GROUP BY k QUALIFY "
                + "ARRAY_SIZE(OBJECT_KEYS(OBJECT_PICK(ANY_VALUE(o), a))) = 2");
        assertAnswer("1|[\"b\",\"c\"] ;; 2|[\"a\"]",
            "SELECT k, ARRAY_AGG(x) WITHIN GROUP (ORDER BY x) a FROM pk754, (SELECT 'b' x, 1 k UNION ALL SELECT 'c', "
                + "1 UNION ALL SELECT 'a', 2) GROUP BY k ORDER BY ARRAY_SIZE(OBJECT_KEYS(OBJECT_PICK(ANY_VALUE(o), "
                + "a))) DESC, k");
        assertAnswer("1|[\"b\",\"c\",\"a\"]",
            "SELECT k, ARRAY_CAT(ARRAY_AGG(x) WITHIN GROUP (ORDER BY x), ARRAY_CONSTRUCT('a')) a FROM pk754, (SELECT "
                + "'b' x, 1 k UNION ALL SELECT 'c', 1 UNION ALL SELECT 'a', 2) GROUP BY k HAVING "
                + "ARRAY_SIZE(OBJECT_KEYS(OBJECT_PICK(ANY_VALUE(o), a))) >= 2 ORDER BY k");
        assertAnswer("REFUSED SQL compilation error: | [\"values\".X] is not a valid group by expression",
            "SELECT k, ARRAY_AGG(x) WITHIN GROUP (ORDER BY x) x FROM pk754, (SELECT 'b' x, 1 k UNION ALL SELECT 'c', "
                + "1 UNION ALL SELECT 'a', 2) GROUP BY k HAVING ARRAY_SIZE(OBJECT_KEYS(OBJECT_PICK(ANY_VALUE(o), "
                + "x))) = 2 ORDER BY k");
        assertAnswer("[\"a\",\"b\",\"c\"]",
            "SELECT ARRAY_AGG(x) WITHIN GROUP (ORDER BY x) a FROM pk754, (SELECT 'b' x, 1 k UNION ALL SELECT 'c', 1 "
                + "UNION ALL SELECT 'a', 2) HAVING ARRAY_SIZE(OBJECT_KEYS(OBJECT_PICK(ANY_VALUE(o), a))) = 3");
        assertAnswer("2|[\"a\"]",
            "SELECT k, ARRAY_AGG(x) WITHIN GROUP (ORDER BY x) a FROM pk754, (SELECT 'b' x, 1 k UNION ALL SELECT 'c', "
                + "1 UNION ALL SELECT 'a', 2) GROUP BY k HAVING OBJECT_PICK(ANY_VALUE(o), a) = OBJECT_CONSTRUCT('a', "
                + "1) ORDER BY k");
    }
}
