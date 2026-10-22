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
 * A Python function's NaN or infinity given an exact NUMBER, whatever its scale, is refused as the integer
 * conversion's error, and a time with an offset is a TIME of its wall clock (all live-verified).
 */
public class PythonNanReturnTest extends MeasuredCellsTestSupport {

    @Test
    public void nanAndInfinity() {
        assertAnswer("Function R5 successfully created.",
            "CREATE OR REPLACE FUNCTION r5() RETURNS NUMBER(10,2) LANGUAGE PYTHON RUNTIME_VERSION='3.12' HANDLER='h' "
                + "AS $$\ndef h():\n    return float('nan')\n$$");
        assertAnswer("REFUSED Python Interpreter Error: | ValueError: cannot convert NaN to integer in function R5 "
                + "with handler h",
            "SELECT r5()");
        assertAnswer("Function R6 successfully created.",
            "CREATE OR REPLACE FUNCTION r6() RETURNS NUMBER(10,2) LANGUAGE PYTHON RUNTIME_VERSION='3.12' HANDLER='h' "
                + "AS $$\ndef h():\n    return float('inf')\n$$");
        assertAnswer("REFUSED Python Interpreter Error: | OverflowError: cannot convert Infinity to integer in "
                + "function R6 with handler h",
            "SELECT r6()");
        assertAnswer("Function R7 successfully created.",
            "CREATE OR REPLACE FUNCTION r7() RETURNS NUMBER(10,2) LANGUAGE PYTHON RUNTIME_VERSION='3.12' HANDLER='h' "
                + "AS $$\nimport decimal\ndef h():\n    return decimal.Decimal('NaN')\n$$");
        assertAnswer("REFUSED Python Interpreter Error: | ValueError: cannot convert NaN to integer in function R7 "
                + "with handler h",
            "SELECT r7()");
        assertAnswer("Function R8 successfully created.",
            "CREATE OR REPLACE FUNCTION r8() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.12' HANDLER='h' AS "
                + "$$\ndef h():\n    return float('nan')\n$$");
        assertAnswer("REFUSED Python Interpreter Error: | ValueError: cannot convert NaN to integer in function R8 "
                + "with handler h",
            "SELECT r8()");
        assertAnswer("Function R9 successfully created.",
            "CREATE OR REPLACE FUNCTION r9() RETURNS NUMBER(10,2) LANGUAGE PYTHON RUNTIME_VERSION='3.12' HANDLER='h' "
                + "AS $$\ndef h():\n    return float('-inf')\n$$");
        assertAnswer("REFUSED Python Interpreter Error: | OverflowError: cannot convert Infinity to integer in "
                + "function R9 with handler h",
            "SELECT r9()");
        assertAnswer("Function R10 successfully created.",
            "CREATE OR REPLACE FUNCTION r10() RETURNS FLOAT LANGUAGE PYTHON RUNTIME_VERSION='3.12' HANDLER='h' AS "
                + "$$\ndef h():\n    return float('nan')\n$$");
        assertAnswer("NaN", "SELECT r10()");
        assertAnswer("Function R31 successfully created.",
            "CREATE OR REPLACE FUNCTION r31() RETURNS TIME LANGUAGE PYTHON RUNTIME_VERSION='3.12' HANDLER='h' AS "
                + "$$\nimport datetime\ndef h():\n    return datetime.time(1, 2, 3, tzinfo=datetime.timezone.utc)\n$$");
        assertAnswer("01:02:03", "SELECT r31()");
        assertAnswer("Function R32 successfully created.",
            "CREATE OR REPLACE FUNCTION r32() RETURNS TIME LANGUAGE PYTHON RUNTIME_VERSION='3.12' HANDLER='h' AS "
                + "$$\nimport datetime\ndef h():\n    return datetime.time(1, 2, 3)\n$$");
        assertAnswer("01:02:03", "SELECT r32()");
        assertAnswer("Function R33 successfully created.",
            "CREATE OR REPLACE FUNCTION r33() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION='3.12' HANDLER='h' AS "
                + "$$\ndef h():\n    return float('inf')\n$$");
        assertAnswer("REFUSED Python Interpreter Error: | OverflowError: cannot convert Infinity to integer in "
                + "function R33 with handler h",
            "SELECT r33()");
        assertAnswer("Function R34 successfully created.",
            "CREATE OR REPLACE FUNCTION r34() RETURNS TIME LANGUAGE PYTHON RUNTIME_VERSION='3.12' HANDLER='h' AS "
                + "$$\nimport datetime\ndef h():\n    return datetime.time(1, 2, 3, "
                + "tzinfo=datetime.timezone(datetime.timedelta(hours=2)))\n$$");
        assertAnswer("01:02:03", "SELECT r34()");
        assertAnswer("Function R35 successfully created.",
            "CREATE OR REPLACE FUNCTION r35() RETURNS TIMESTAMP_NTZ LANGUAGE PYTHON RUNTIME_VERSION='3.12' "
                + "HANDLER='h' AS $$\nimport datetime\ndef h():\n    return datetime.datetime(2024, 1, 2, 3, 4, 5, "
                + "tzinfo=datetime.timezone.utc)\n$$");
        assertAnswer("REFUSED Datetime object must be naive to be convertible to TIMESTAMP_NTZ in function R35 with "
                + "handler h",
            "SELECT r35()");
        assertAnswer("Function R36 successfully created.",
            "CREATE OR REPLACE FUNCTION r36() RETURNS NUMBER(5,1) LANGUAGE PYTHON RUNTIME_VERSION='3.12' HANDLER='h' "
                + "AS $$\nimport decimal\ndef h():\n    return decimal.Decimal('Infinity')\n$$");
        assertAnswer("REFUSED Python Interpreter Error: | OverflowError: cannot convert Infinity to integer in "
                + "function R36 with handler h",
            "SELECT r36()");
    }
}
