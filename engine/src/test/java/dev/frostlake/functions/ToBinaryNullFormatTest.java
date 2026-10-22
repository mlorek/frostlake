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
 * TO_BINARY and TRY_TO_BINARY given a NULL format answer NULL, and TO_BINARY over two NULLs is refused as one
 * argument too many (all live-verified).
 */
public class ToBinaryNullFormatTest extends MeasuredCellsTestSupport {

    @Test
    public void nullFormat() {
        assertAnswer("null", "SELECT TO_BINARY('4869', NULL)");
        assertAnswer("null", "SELECT TRY_TO_BINARY('4869', NULL)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[TO_BINARY(NULL, NULL)] expected 1, got 2",
            "SELECT TO_BINARY(NULL, NULL)");
        assertAnswer("true", "SELECT TO_BINARY('4869', NULL) IS NULL");
    }
}
