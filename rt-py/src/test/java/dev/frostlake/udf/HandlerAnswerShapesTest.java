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
 * A handler's answer: bytes past a declared BINARY(1) refused in lower-case hex, RETURNS CHAR and
 * VARCHAR(16777216) written out, a Python bytearray, a dict keyed by a tuple, by None and by numbers, and a
 * class with __str__ (all live-verified).
 */
public class HandlerAnswerShapesTest extends MeasuredCellsTestSupport {

    @Test
    public void javaScriptAnswers() {
        engine.execute("CREATE OR REPLACE PROCEDURE jb1() RETURNS BINARY(1) LANGUAGE JAVASCRIPT AS $$ return new "
            + "Uint8Array([0x80, 0xff]); $$");
        engine.execute("CREATE OR REPLACE PROCEDURE jb2() RETURNS BINARY(1) LANGUAGE JAVASCRIPT AS $$ return new "
            + "Uint8Array(40).fill(171); $$");
        engine.execute("CREATE OR REPLACE PROCEDURE jc1() RETURNS CHAR LANGUAGE JAVASCRIPT AS $$ return 'ab'; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE jc2() RETURNS VARCHAR(16777216) LANGUAGE JAVASCRIPT AS $$ return "
            + "'ab'; $$");
        assertAnswer("REFUSED Binary value '80ff' is too long and would be truncated", "CALL jb1()");
        assertAnswer("REFUSED Binary value "
                + "'abababababababababababababababababababababababababababababababababababababababab' is too long and "
                + "would be truncated",
            "CALL jb2()");
        assertAnswer("REFUSED String 'ab' is too long and would be truncated", "CALL jc1()");
        assertAnswer("ab", "CALL jc2()");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"value\"'",
            "SELECT \"value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE 1 = 0");
        assertAnswer("signature|() ;; returns|VARCHAR(1) ;; language|JAVASCRIPT ;; null handling|CALLED ON NULL INPUT "
                + ";; volatility|VOLATILE ;; execute as|OWNER ;; body| return 'ab'; ",
            "DESCRIBE PROCEDURE jc1()");
        assertAnswer("signature|() ;; returns|VARCHAR(16777216) ;; language|JAVASCRIPT ;; null handling|CALLED ON "
                + "NULL INPUT ;; volatility|VOLATILE ;; execute as|OWNER ;; body| return 'ab'; ",
            "DESCRIBE PROCEDURE jc2()");
    }

    @Test
    public void pythonAnswers() {
        engine.execute("CREATE OR REPLACE PROCEDURE pb() RETURNS BINARY LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
            + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return "
            + "bytearray(b'ab')\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE pk1() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
            + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return {(1,"
            + " 2): 'x'}\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE pk2() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
            + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return "
            + "{None: 1}\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE pk3() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
            + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return {1: "
            + "'a', 2.5: 'b'}\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE ps() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
            + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\nclass A:\n    def "
            + "__str__(self):\n        return 'A!'\n\ndef run(session):\n    return A()\n$$");
        assertAnswer("6162", "CALL pb()");
        assertAnswer("{\"(1, 2)\":\"x\"}", "CALL pk1()");
        assertAnswer("{\"null\":1}", "CALL pk2()");
        assertAnswer("{\"1\":\"a\",\"2.5\":\"b\"}", "CALL pk3()");
        assertAnswer("A!", "CALL ps()");
    }
}
