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
 * PARSE_JSON and TRY_PARSE_JSON take an optional parameter: {@code 'd'} allows an object to name a key twice, "the
 * returned object has a single instance of that key with the last value specified for that key", and {@code 's'}, the
 * default, does not (documented). Both were refused as one argument too many.
 */
public class ParseJsonDuplicateKeysTest extends MeasuredCellsTestSupport {

    @Test
    public void dKeepsTheLastValueAndSIsStrict() {
        assertAnswer("{\"a\":2}", "SELECT PARSE_JSON('{\"a\":1,\"a\":2}', 'd')");
        assertAnswer("{\"a\":2}", "SELECT TRY_PARSE_JSON('{\"a\":1,\"a\":2}', 'd')");
        assertAnswer("{\"a\":{\"b\":3},\"c\":1}", "SELECT PARSE_JSON('{\"a\":{\"b\":1,\"b\":3},\"c\":1}', 'd')");
        assertAnswer("REFUSED Error parsing JSON: duplicate object attribute \"a\", pos 10",
            "SELECT PARSE_JSON('{\"a\":1,\"a\":2}', 's')");
        assertAnswer("REFUSED Error parsing JSON: duplicate object attribute \"a\", pos 10",
            "SELECT PARSE_JSON('{\"a\":1,\"a\":2}')");
        assertAnswer("null", "SELECT TRY_PARSE_JSON('{\"a\":1,\"a\":2}', 's')");
    }
}
