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
 * What a Python handler raises is reported as the account reports it: Python Interpreter Error, the traceback of
 * the handler's own source as the file _udf_code.py, caret lines included, the exception, and the routine and its
 * handler; a source that does not compile is reported by its line with no traceback, at CREATE as a module-level
 * failure is (all live-verified).
 */
public class PythonInterpreterErrorTest extends MeasuredCellsTestSupport {

    @Test
    public void tableFunctionAndFunction() {
        engine.execute("CREATE OR REPLACE TABLE ev (v INT)");
        engine.execute("INSERT INTO ev VALUES (1), (NULL), (2)");
        engine.execute("CREATE OR REPLACE FUNCTION py_strict(v INT) RETURNS TABLE (o INT) LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.11' HANDLER = 'H' AS $$\nclass H:\n    def process(self, v):\n        if v "
            + "is None:\n            raise ValueError('none given')\n        yield (v * 10,)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION py_s(v INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
            + "HANDLER = 'f' AS $$\ndef f(v):\n    raise ValueError('none given')\n$$");
        assertAnswer("REFUSED Python Interpreter Error: | Traceback (most recent call last): |   File "
                + "\"_udf_code.py\", line 5, in process |     raise ValueError('none given') | ValueError: none given "
                + "|  in function PY_STRICT with handler H",
            "SELECT ev.v, f.o FROM ev, TABLE(py_strict(ev.v)) f ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for "
                + "function 'PY_STRICT': (NULL)",
            "SELECT * FROM TABLE(py_strict(NULL))");
        assertAnswer("REFUSED Python Interpreter Error: | Traceback (most recent call last): |   File "
                + "\"_udf_code.py\", line 3, in f |     raise ValueError('none given') | ValueError: none given |  in "
                + "function PY_S with handler f",
            "SELECT py_s(1)");
    }

    @Test
    public void tracebackShapes() {
        engine.execute("CREATE OR REPLACE FUNCTION py_d(v INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
            + "HANDLER = 'f' AS $$\ndef g(v):\n    raise ValueError('deep ' + str(v))\n\ndef f(v):\n    return "
            + "g(v)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION py_t(v INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
            + "HANDLER = 'f' AS $$def f(v):\n    return v + 'a'$$");
        engine.execute("CREATE OR REPLACE FUNCTION py_k(v INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
            + "HANDLER = 'f' AS $$\ndef f(v):\n    d = {}\n    return d['missing']\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION py_m(v INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
            + "HANDLER = 'f' AS $$\ndef f(v):\n    raise RuntimeError('line one\\nline two')\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION py_u(v INT) RETURNS TABLE (o INT) LANGUAGE PYTHON RUNTIME_VERSION "
            + "= '3.11' HANDLER = 'H' AS $$\nclass H:\n    def process(self, v):\n        yield (v,)\n    def "
            + "end_partition(self):\n        raise KeyError('k')\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE py_p() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
            + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    raise "
            + "ValueError('proc fails')\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION py_z(v INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
            + "HANDLER = 'f' AS $$\ndef f(v):\n    return 1 / 0\n$$");
        assertAnswer("REFUSED Python Interpreter Error: | Traceback (most recent call last): |   File "
                + "\"_udf_code.py\", line 6, in f |     return g(v) |            ^^^^ |   File \"_udf_code.py\", line "
                + "3, in g |     raise ValueError('deep ' + str(v)) | ValueError: deep 1 |  in function PY_D with "
                + "handler f",
            "SELECT py_d(1)");
        assertAnswer("REFUSED Python Interpreter Error: | Traceback (most recent call last): |   File "
                + "\"_udf_code.py\", line 2, in f |     return v + 'a' |             ~~^~~~~ | TypeError: unsupported "
                + "operand type(s) for +: 'int' and 'str' |  in function PY_T with handler f",
            "SELECT py_t(1)");
        assertAnswer("REFUSED Python Interpreter Error: | Traceback (most recent call last): |   File "
                + "\"_udf_code.py\", line 4, in f |     return d['missing'] |            ~^^^^^^^^^^^ | KeyError: "
                + "'missing' |  in function PY_K with handler f",
            "SELECT py_k(1)");
        assertAnswer("REFUSED Python Interpreter Error: | Traceback (most recent call last): |   File "
                + "\"_udf_code.py\", line 3, in f |     raise RuntimeError('line one\\nline two') | RuntimeError: "
                + "line one | line two |  in function PY_M with handler f",
            "SELECT py_m(1)");
        assertAnswer("REFUSED Python Interpreter Error: | Traceback (most recent call last): |   File "
                + "\"_udf_code.py\", line 6, in end_partition |     raise KeyError('k') | KeyError: 'k' |  in "
                + "function PY_U with handler H",
            "SELECT * FROM TABLE(py_u(1))");
        assertAnswer("REFUSED Python Interpreter Error: | Traceback (most recent call last): |   File "
                + "\"_udf_code.py\", line 3, in run |     raise ValueError('proc fails') | ValueError: proc fails |  "
                + "in function PY_P with handler run",
            "CALL py_p()");
        assertAnswer("REFUSED Python Interpreter Error: | Traceback (most recent call last): |   File "
                + "\"_udf_code.py\", line 3, in f |     return 1 / 0 |            ~~^~~ | ZeroDivisionError: division "
                + "by zero |  in function PY_Z with handler f",
            "SELECT py_z(1)");
    }

    @Test
    public void refusedAtCreate() {
        assertAnswer("REFUSED Python Interpreter Error: |   File \"_udf_code.py\", line 2 |     def f(v) |            "
                + " ^ | SyntaxError: expected ':' |  in function PY_SE with handler f",
            "CREATE OR REPLACE FUNCTION py_se(v INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' HANDLER = "
                + "'f' AS $$\ndef f(v)\n    return 1\n$$");
        assertAnswer("REFUSED SQL compilation error: | Unknown function PY_SE.", "SELECT py_se(1)");
        assertAnswer("REFUSED Python Interpreter Error: | Traceback (most recent call last): |   File "
                + "\"_udf_code.py\", line 2, in <module> |     x = 1 / 0 |         ~~^~~ | ZeroDivisionError: "
                + "division by zero |  in function PY_MOD with handler f",
            "CREATE OR REPLACE FUNCTION py_mod(v INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' HANDLER = "
                + "'f' AS $$\nx = 1 / 0\ndef f(v):\n    return v\n$$");
        assertAnswer("REFUSED SQL compilation error: | Unknown function PY_MOD.", "SELECT py_mod(1)");
    }
}
