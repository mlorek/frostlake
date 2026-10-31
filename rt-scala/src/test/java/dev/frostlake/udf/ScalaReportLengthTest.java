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
package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A Scala handler's report of more than 4096 bytes of UTF-8 is cut as a Java handler's is, to its first 4093 and
 * ended by {@code ...} before the sentence that names the function and its handler (live-verified).
 */
public class ScalaReportLengthTest extends MeasuredCellsTestSupport {

    /** The report's lines before the exception's text, joined as {@code answer} joins them. */
    private static final String HEAD = "REFUSED User Error Report:  | Java Stack Trace: | "
        + "java.lang.IllegalStateException: ";

    @Test
    public void aReportPast4096BytesIsCut() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION sc655(n INT) RETURNS INT LANGUAGE SCALA RUNTIME_VERSION = '2.12' \
            HANDLER = 'H.f' AS $$
            object H { def f(n: Int): Int = throw new IllegalStateException("a" * n) }
            $$""");
        assertAnswer(HEAD + "a".repeat(100) + " | \tat function_handler_0//H$.f(ScalaUDF:2) | \tat "
            + "function_handler_0//H.f(ScalaUDF) |  in function SC655 with handler H.f", "SELECT sc655(100)");
        assertAnswer(HEAD + "a".repeat(4022) + "... in function SC655 with handler H.f", "SELECT sc655(5000)");
    }
}
