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
 * OBJECT_PICK names the keys it picks either as arguments of their own or as the text elements of one ARRAY, a NULL
 * element naming none (live-verified): a VARIANT holding an array is a single key, and an ARRAY beside further keys,
 * or a structured ARRAY, is refused by the argument types.
 */
public class ObjectPickKeyArrayTest extends MeasuredCellsTestSupport {

    @Test
    public void anArrayNamesTheKeysItHolds() {
        assertCells(new String[][] {
            {"SELECT OBJECT_PICK(OBJECT_CONSTRUCT('a', 1, 'b', 2, 'c', 3), ARRAY_CONSTRUCT('a', 'b'))",
                "{\"a\":1,\"b\":2}"},
            {"SELECT OBJECT_PICK(OBJECT_CONSTRUCT('a', 1, 'b', 2, 'c', 3), ARRAY_CONSTRUCT('a', 'zz'))", "{\"a\":1}"},
            {"SELECT OBJECT_PICK(OBJECT_CONSTRUCT('a', 1, 'b', 2, 'c', 3), ARRAY_CONSTRUCT())", "{}"},
            {"SELECT OBJECT_PICK(OBJECT_CONSTRUCT('a', 1, 'b', 2, 'c', 3), ARRAY_CONSTRUCT('a'), 'b')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_PICK': (OBJECT, ARRAY, VARCHAR(1))"},
            {"SELECT OBJECT_PICK(OBJECT_CONSTRUCT('a', 1, 'b', 2, 'c', 3), PARSE_JSON('[\"c\"]'))", "{}"},
            {"SELECT OBJECT_PICK(OBJECT_CONSTRUCT('a', 1, 'b', 2, 'c', 3), ['b', 'c'])", "{\"b\":2,\"c\":3}"},
            {"SELECT OBJECT_PICK({'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT), ['c','a'])",
                "REFUSED SQL compilation error: | Argument number 1 for function 'OBJECT_PICK' needs to be a "
                + "string literal."},
            {"SELECT OBJECT_PICK({'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT), 'c', 'a')",
                "{\"c\":2,\"a\":1}"},
            {"SELECT OBJECT_PICK(OBJECT_CONSTRUCT('a', 1, 'b', 2, 'c', 3), ARRAY_CONSTRUCT('a', NULL))", "{\"a\":1}"},
            {"SELECT OBJECT_PICK(OBJECT_CONSTRUCT('a', 1, 'b', 2, 'c', 3), 'a', 'b')", "{\"a\":1,\"b\":2}"},
        });
    }

    @Test
    public void onlyAnArrayTypedArgumentNamesKeys() {
        engine.execute("CREATE OR REPLACE TABLE t_op (o OBJECT, k ARRAY, v VARIANT)");
        engine.execute("INSERT INTO t_op SELECT OBJECT_CONSTRUCT('a', 1, 'b', 2, 'c', 3), ARRAY_CONSTRUCT('b', "
            + "'c'), PARSE_JSON('[\"b\"]')");
        assertCells(new String[][] {
            {"SELECT OBJECT_PICK(OBJECT_CONSTRUCT('a', 1, 'b', 2, 'c', 3), PARSE_JSON('\"a\"'))", "{\"a\":1}"},
            {"SELECT OBJECT_PICK(o, k) FROM (SELECT OBJECT_CONSTRUCT('a', 1, 'b', 2, 'c', 3) AS o, "
                + "ARRAY_CONSTRUCT('a') AS k)",
                "{\"a\":1}"},
            {"SELECT OBJECT_PICK(o, k) FROM t_op", "{\"b\":2,\"c\":3}"},
            {"SELECT OBJECT_PICK(o, v) FROM t_op", "{}"},
            {"SELECT OBJECT_PICK(OBJECT_CONSTRUCT('a', 1, 'b', 2, 'c', 3), ['a', 'b']::ARRAY(VARCHAR))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_PICK': (OBJECT, ARRAY(VARCHAR(134217728)))"},
            {"SELECT OBJECT_PICK(OBJECT_CONSTRUCT('a', 1, 'b', 2, 'c', 3), ARRAY_CONSTRUCT('a', 1))", "{\"a\":1}"},
            {"SELECT OBJECT_PICK(OBJECT_CONSTRUCT('a', 1, 'b', 2, 'c', 3), ARRAY_CONSTRUCT(ARRAY_CONSTRUCT('a'), 'b'))",
                "{\"b\":2}"},
            {"SELECT OBJECT_PICK(OBJECT_CONSTRUCT('a', 1, 'b', 2, 'c', 3), ARRAY_CONSTRUCT('a', 'a'))", "{\"a\":1}"},
            {"SELECT OBJECT_PICK(OBJECT_CONSTRUCT('a', 1, 'b', 2, 'c', 3), NULL::ARRAY)", "{}"},
            {"SELECT OBJECT_PICK(OBJECT_CONSTRUCT('a', 1, 'b', 2, 'c', 3), ARRAY_CONSTRUCT('a'), ARRAY_CONSTRUCT('b'))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_PICK': (OBJECT, ARRAY, ARRAY)"},
            {"SELECT OBJECT_PICK(OBJECT_CONSTRUCT('a', 1, 'b', 2, 'c', 3), 'a', ARRAY_CONSTRUCT('b'))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_PICK': (OBJECT, VARCHAR(1), ARRAY)"},
        });
    }
}
