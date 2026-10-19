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
 * A key given twice to OBJECT_CONSTRUCT, to OBJECT_CONSTRUCT_KEEP_NULL or in an object constant is refused as
 * "Duplicate field key 'a'" once both pairs hold a value: a pair whose value is NULL is dropped first, except
 * under KEEP_NULL, keys differing in case are two keys, and a query no row reaches raises nothing (all
 * live-verified).
 */
public class ObjectDuplicateKeyTest extends MeasuredCellsTestSupport {

    @Test
    public void aKeyGivenTwiceIsRefused() {
        assertAnswer("REFUSED Duplicate field key 'a'", "SELECT OBJECT_CONSTRUCT('a', 1, 'a', 2)");
        assertAnswer("{\"a\":2}", "SELECT OBJECT_CONSTRUCT('a', NULL, 'a', 2)");
        assertAnswer("{\"a\":1}", "SELECT OBJECT_CONSTRUCT('a', 1, 'a', NULL)");
        assertAnswer("REFUSED Duplicate field key 'a'", "SELECT OBJECT_CONSTRUCT_KEEP_NULL('a', 1, 'a', 2)");
        assertAnswer("REFUSED Duplicate field key 'a'", "SELECT OBJECT_CONSTRUCT_KEEP_NULL('a', NULL, 'a', 2)");
        assertAnswer("{\"A\":2,\"a\":1}", "SELECT OBJECT_CONSTRUCT('a', 1, 'A', 2)");
        assertAnswer("REFUSED Duplicate field key 'a'", "SELECT {'a': 1, 'a': 2}");
        assertAnswer("REFUSED Duplicate field key 'a'", "SELECT OBJECT_CONSTRUCT('a', 1, 'b', 2, 'a', 3)");
        assertAnswer("{}", "SELECT OBJECT_CONSTRUCT(NULL, 1, NULL, 2)");
        assertAnswer("", "SELECT OBJECT_CONSTRUCT('a', 1, 'a', 2) FROM (SELECT 1) WHERE 1 = 0");
        assertAnswer("REFUSED Duplicate field key 'a'",
            "SELECT OBJECT_CONSTRUCT(k, v, k, v) FROM (SELECT 'a' AS k, 1 AS v)");
        assertAnswer("", "SELECT OBJECT_CONSTRUCT(k, v, k, v) FROM (SELECT 'a' AS k, 1 AS v) WHERE v > 5");
        assertAnswer("REFUSED Duplicate field key 'a'", "SELECT OBJECT_CONSTRUCT_KEEP_NULL('a', NULL, 'a', NULL)");
        assertAnswer("REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types "
                + "OBJECT and VARCHAR(134217728)",
            "SELECT TRY_CAST(OBJECT_CONSTRUCT('a', 1, 'a', 2) AS VARCHAR)");
    }

    @Test
    public void anObjectConstantRefusesARepeatedKey() {
        assertAnswer("{\"a\":2}", "SELECT {'a': NULL, 'a': 2}");
        assertAnswer("{\"a\":1}", "SELECT {'a': 1, 'a': NULL}");
        assertAnswer("REFUSED Duplicate field key 'a'", "SELECT {'a': 1, 'b': 2, 'a': 3}");
        assertAnswer("{\"A\":2,\"a\":1}", "SELECT {'a': 1, 'A': 2}");
        assertAnswer("", "SELECT {'a': 1, 'a': 2} FROM (SELECT 1) WHERE 1 = 0");
        assertAnswer("", "SELECT {'a': x, 'a': x} FROM (SELECT 1 AS x) WHERE x > 5");
    }
}
