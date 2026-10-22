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
 * Spaces in hex text outside TO_BINARY: HEX_DECODE_BINARY and HEX_DECODE_STRING refuse a space anywhere, their
 * TRY_ forms answer NULL, and an X'...' literal and TO_BINARY pass over one (all live-verified).
 */
public class HexInputSpacesTest extends MeasuredCellsTestSupport {

    @Test
    public void hexSpaces() {
        assertAnswer("REFUSED The following string is not a legal hex-encoded value: '48 69'",
            "SELECT HEX_DECODE_BINARY('48 69')");
        assertAnswer("null", "SELECT TRY_HEX_DECODE_BINARY('48 69')");
        assertAnswer("REFUSED The following string is not a legal hex-encoded value: '48 69'",
            "SELECT HEX_DECODE_STRING('48 69')");
        assertAnswer("null", "SELECT TRY_HEX_DECODE_STRING('48 69')");
        assertAnswer("4869", "SELECT X'48 69'");
        assertAnswer("4869", "SELECT TO_BINARY('48 69', 'HEX')");
        assertAnswer("REFUSED The following string is not a legal hex-encoded value: ' 4869 '",
            "SELECT HEX_DECODE_BINARY(' 4869 ')");
        assertAnswer("REFUSED The following string is not a legal hex-encoded value: '4869 '",
            "SELECT HEX_DECODE_STRING('4869 ')");
    }
}
