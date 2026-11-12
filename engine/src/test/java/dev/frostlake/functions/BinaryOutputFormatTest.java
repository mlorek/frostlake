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
 * BINARY_OUTPUT_FORMAT is the text a BINARY converts to where no format is written: "the one-argument version of
 * TO_CHAR, TO_VARCHAR" (documented), and a cast to VARCHAR alike. An explicit format still wins. The parameter was
 * kept and shown, and every conversion was HEX.
 */
public class BinaryOutputFormatTest extends MeasuredCellsTestSupport {

    private static final String VALUES = "SELECT TO_VARCHAR(TO_BINARY('48454C50')), TO_CHAR(TO_BINARY('48454C50')), "
        + "TO_BINARY('48454C50')::VARCHAR, TO_VARCHAR(TO_BINARY('48454C50'), 'HEX')";

    @Test
    public void base64IsTheTextOfEveryConversionWithoutAFormat() {
        try {
            engine.execute("ALTER SESSION SET BINARY_OUTPUT_FORMAT = 'BASE64'");
            assertAnswer("SEVMUA==|SEVMUA==|SEVMUA==|48454C50", VALUES);
            engine.execute("ALTER SESSION SET BINARY_OUTPUT_FORMAT = 'hex'");
            assertAnswer("48454C50|48454C50|48454C50|48454C50", VALUES);
        } finally {
            engine.execute("ALTER SESSION UNSET BINARY_OUTPUT_FORMAT");
        }
        assertAnswer("48454C50|48454C50|48454C50|48454C50", VALUES);
    }
}
