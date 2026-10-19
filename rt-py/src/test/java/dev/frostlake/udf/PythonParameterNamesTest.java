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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Python function's arguments are its handler's parameters alone: a parameter named like a built-in - type,
 * len, str, max, min, list, id - shadows the built-in in the handler only, and a helper of the body that reads a
 * parameter's name finds no such global (all live-verified).
 */
public class PythonParameterNamesTest extends MeasuredCellsTestSupport {

    @Test
    public void argumentsAreTheHandlersParametersAlone() {
        assertAnswer("Function F1 successfully created.",
            "CREATE OR REPLACE FUNCTION f1(id INT, type VARCHAR) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'h' AS $$\ndef kind(v):\n    return type(v).__name__\ndef h(id, type):\n    "
                + "return kind(id) + '/' + str(len(type))\n$$");
        assertAnswer("int/3", "SELECT f1(1, 'abc')");
        assertAnswer("Function F2 successfully created.",
            "CREATE OR REPLACE FUNCTION f2(len INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' HANDLER = "
                + "'h' AS $$\ndef size(s):\n    return len(s)\ndef h(n):\n    return size('abcd') + n\n$$");
        assertAnswer("5", "SELECT f2(1)");
        assertAnswer("Function F3 successfully created.",
            "CREATE OR REPLACE FUNCTION f3(x INT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' HANDLER = "
                + "'h' AS $$\ndef helper():\n    return str(x)\ndef h(y):\n    return helper()\n$$");
        // The account reports a handler's exception in a traceback of its own; the NameError is the same on both.
        final String helperRead = answer("SELECT f3(5)");
        assertTrue(helperRead.contains("NameError: name 'x' is not defined"), helperRead);
        assertAnswer("Function F4 successfully created.",
            "CREATE OR REPLACE FUNCTION f4(str VARCHAR) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "HANDLER = 'h' AS $$\ndef h(s):\n    return str(s) + '!'\n$$");
        assertAnswer("a!", "SELECT f4('a')");
        assertAnswer("Function F5 successfully created.",
            "CREATE OR REPLACE FUNCTION f5(x INT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' HANDLER = "
                + "'h' AS $$\ndef h(y):\n    return str(globals().get('X')) + '|' + str(globals().get('x'))\n$$");
        assertAnswer("None|None", "SELECT f5(3)");
        assertAnswer("Function F6 successfully created.",
            "CREATE OR REPLACE FUNCTION f6(max INT, min INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "HANDLER = 'h' AS $$\ndef h(a, b):\n    return max(a, b) - min(a, b)\n$$");
        assertAnswer("5", "SELECT f6(2, 7)");
        assertAnswer("Function F7 successfully created.",
            "CREATE OR REPLACE FUNCTION f7(list ARRAY) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "HANDLER = 'h' AS $$\ndef h(a):\n    return str(list(a))\n$$");
        assertAnswer("[1, 2]", "SELECT f7([1, 2])");
        assertAnswer("Function F8 successfully created.",
            "CREATE OR REPLACE FUNCTION f8(id INT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' HANDLER "
                + "= 'h' AS $$\ndef h(n):\n    return str(id(n) > 0)\n$$");
        assertAnswer("True", "SELECT f8(1)");
    }
}
