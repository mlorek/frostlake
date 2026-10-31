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
 * MAP_PICK's and MAP_DELETE's keys, several of them with an ARRAY among them, are refused by the conversion
 * the first key's type asks of the others, or as the function's arguments' types (all live-verified).
 */
public class MapMixedKeyListTest extends MeasuredCellsTestSupport {

    @Test
    public void mixedKeys() {
        assertAnswer("REFUSED SQL compilation error: | Can not convert parameter 'ARRAY_CONSTRUCT('b')' of type "
                + "[ARRAY] into expected type [VARCHAR(1)]",
            "SELECT MAP_PICK({'a': 1, 'b': 2}::MAP(VARCHAR, NUMBER), 'a', ['b'])");
        assertAnswer("REFUSED SQL compilation error: | Can not convert parameter ''b'' of type [VARCHAR(1)] into "
                + "expected type [ARRAY]",
            "SELECT MAP_PICK({'a': 1, 'b': 2}::MAP(VARCHAR, NUMBER), ['a'], 'b')");
        assertAnswer("REFUSED SQL compilation error: | Function MAP_PICK cannot be used with arguments of types "
                + "MAP(VARCHAR(134217728), NUMBER(38,0)) and ARRAY",
            "SELECT MAP_PICK({'a': 1, 'b': 2}::MAP(VARCHAR, NUMBER), ['a'], ['b'])");
        assertAnswer("REFUSED SQL compilation error: | Can not convert parameter 'ARRAY_CONSTRUCT('b')' of type "
                + "[ARRAY] into expected type [VARCHAR(1)]",
            "SELECT MAP_DELETE({'a': 1, 'b': 2}::MAP(VARCHAR, NUMBER), 'a', ['b'])");
        assertAnswer("REFUSED SQL compilation error: | Can not convert parameter ''b'' of type [VARCHAR(1)] into "
                + "expected type [ARRAY]",
            "SELECT MAP_DELETE({'a': 1, 'b': 2}::MAP(VARCHAR, NUMBER), ['a'], 'b')");
        assertAnswer("{\"a\":1}", "SELECT MAP_PICK({'a': 1, 'b': 2}::MAP(VARCHAR, NUMBER), ['a'])");
        assertAnswer("REFUSED SQL compilation error: | Function MAP_PICK cannot be used with arguments of types "
                + "MAP(VARCHAR(134217728), NUMBER(38,0)) and ARRAY",
            "SELECT MAP_PICK({'a': 1, 'b': 2}::MAP(VARCHAR, NUMBER), ['a'], NULL)");
        assertAnswer("REFUSED SQL compilation error: | Function MAP_DELETE cannot be used with arguments of types "
                + "MAP(VARCHAR(134217728), NUMBER(38,0)) and ARRAY",
            "SELECT MAP_DELETE({'a': 1, 'b': 2}::MAP(VARCHAR, NUMBER), NULL, ['a'])");
    }
}
