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
 * A Python handler's report is not cut at 4096 bytes as a Java handler's is: it is cut as every failed statement's
 * message is, past 16384 bytes of UTF-8, to its first 16384 characters ended by {@code ... (Original error message
 * truncated since it exceeded ERROR_MSG_MAX_SIZE).}, twice over (all live-verified).
 */
public class PythonReportLengthTest extends MeasuredCellsTestSupport {

    private static final String CUT = "... (Original error message truncated since it exceeded ERROR_MSG_MAX_SIZE).";

    /** The report of a ValueError raised on the handler's third line, as the account words it. */
    private static String report(final String line, final String text, final String function) {
        return "Python Interpreter Error:\nTraceback (most recent call last):\n  File \"_udf_code.py\", line 3, in f\n"
            + "    " + line + "\nValueError: " + text + "\n in function " + function + " with handler f";
    }

    /** A message as {@code answer} reads a refusal: its lines joined. */
    private static String refused(final String message) {
        return "REFUSED " + message.replace("\n", " | ");
    }

    @Test
    public void aReportIsNotCutAt4096Bytes() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION pl655(n INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' HANDLER = 'f' \
            AS $$
            def f(n):
                raise ValueError('a' * n)
            $$""");
        assertAnswer(refused(report("raise ValueError('a' * n)", "a".repeat(100), "PL655")), "SELECT pl655(100)");
        assertAnswer(refused(report("raise ValueError('a' * n)", "a".repeat(5000), "PL655")), "SELECT pl655(5000)");
        assertAnswer(refused(report("raise ValueError('a' * n)", "a".repeat(20000), "PL655").substring(0, 16384)
            + CUT), "SELECT pl655(20000)");
    }

    @Test
    public void aReportIsCutAsEveryMessageIs() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION pl656(n INT, s VARCHAR) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' \
            HANDLER = 'f' AS $$
            def f(n, s):
                raise ValueError(s * n)
            $$""");
        assertAnswer(refused(report("raise ValueError(s * n)", "a".repeat(16211), "PL656")),
            "SELECT pl656(16211, 'a')");
        assertAnswer(refused(report("raise ValueError(s * n)", "a".repeat(16212), "PL656")),
            "SELECT pl656(16212, 'a')");
        assertAnswer(refused(report("raise ValueError(s * n)", "é".repeat(9000), "PL656") + CUT + CUT),
            "SELECT pl656(9000, 'é')");
    }
}
