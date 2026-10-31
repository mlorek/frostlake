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
 * A Python routine's source runs as a module named after its handler - h_module, Gen_module, run_module - with
 * the file _udf_code.py, on sys.modules, its globals holding the module's own names alone, for a function, a
 * table function and a procedure; an exception class it defines is reported qualified by that module,
 * h_module.MyErr: boom (all live-verified).
 */
public class PythonModuleNamespaceTest extends MeasuredCellsTestSupport {

    @Test
    public void moduleNames() {
        assertAnswer("Function NS40 successfully created.",
            "CREATE OR REPLACE FUNCTION ns40() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = "
                + "'h' AS $$\ndef h():\n    return str(sorted(k for k in globals() if k.startswith('__')))\n$$");
        assertAnswer("['__builtins__', '__cached__', '__doc__', '__file__', '__loader__', '__name__', '__package__', "
                + "'__spec__']",
            "SELECT ns40()");
        assertAnswer("Function NS41 successfully created.",
            "CREATE OR REPLACE FUNCTION ns41() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = "
                + "'h' AS $$\ndef h():\n    return repr(__name__)\n$$");
        assertAnswer("'h_module'", "SELECT ns41()");
        assertAnswer("Function NS42 successfully created.",
            "CREATE OR REPLACE FUNCTION ns42() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = "
                + "'h' AS $$\ndef h():\n    return repr(__doc__)\n$$");
        assertAnswer("None", "SELECT ns42()");
        assertAnswer("Function NS43 successfully created.",
            "CREATE OR REPLACE FUNCTION ns43() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = "
                + "'h' AS $$\n'''Module help.'''\ndef h():\n    return repr(__doc__)\n$$");
        assertAnswer("'Module help.'", "SELECT ns43()");
        assertAnswer("Function NS44 successfully created.",
            "CREATE OR REPLACE FUNCTION ns44() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = "
                + "'h' AS $$\ndef h():\n    return repr((__spec__, __package__))\n$$");
    }

    @Test
    public void everyRoutineKind() {
        assertAnswer("Function NM40 successfully created.",
            "CREATE OR REPLACE FUNCTION nm40() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = "
                + "'calc' AS $$\ndef calc():\n    return repr((__name__, __file__, __cached__))\n$$");
        assertAnswer("('calc_module', '_udf_code.py', None)", "SELECT nm40()");
        assertAnswer("Function NM41 successfully created.",
            "CREATE OR REPLACE FUNCTION nm41() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = "
                + "'h' AS $$\ndef h():\n    return repr((__name__, __file__, __cached__))\n$$");
        assertAnswer("('h_module', '_udf_code.py', None)", "SELECT nm41()");
        assertAnswer("Function NM42 successfully created.",
            "CREATE OR REPLACE FUNCTION nm42() RETURNS TABLE (s VARCHAR) LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
                + "HANDLER = 'Gen' AS $$\nclass Gen:\n    def process(self):\n        yield (repr((__name__, "
                + "sorted(k for k in globals() if k.startswith('__')))),)\n$$");
        assertAnswer("('Gen_module', ['__builtins__', '__cached__', '__doc__', '__file__', '__loader__', '__name__', "
                + "'__package__', '__spec__'])",
            "SELECT * FROM TABLE(nm42())");
        assertAnswer("Function NP40 successfully created.",
            "CREATE OR REPLACE PROCEDURE np40() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return repr((__name__,"
                + " sorted(k for k in globals() if k.startswith('__'))))\n$$");
        assertAnswer("('run_module', ['__builtins__', '__cached__', '__doc__', '__file__', '__loader__', '__name__', "
                + "'__package__', '__spec__'])",
            "CALL np40()");
        assertAnswer("Function NM43 successfully created.",
            "CREATE OR REPLACE FUNCTION nm43() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = "
                + "'h' AS $$\nimport sys\ndef h():\n    return repr((__name__ in sys.modules, '_udf_code' in "
                + "sys.modules))\n$$");
        assertAnswer("(True, False)", "SELECT nm43()");
    }

    @Test
    public void exceptionsNamedByTheirModule() {
        assertAnswer("Function UE40 successfully created.",
            "CREATE OR REPLACE FUNCTION ue40() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = 'h' AS "
                + "$$\nclass MyErr(Exception):\n    pass\ndef h():\n    raise MyErr('boom')\n$$");
        assertAnswer("REFUSED Python Interpreter Error: | Traceback (most recent call last): |   File "
                + "\"_udf_code.py\", line 5, in h |     raise MyErr('boom') | h_module.MyErr: boom |  in function "
                + "UE40 with handler h",
            "SELECT ue40()");
        assertAnswer("Function UE41 successfully created.",
            "CREATE OR REPLACE FUNCTION ue41() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = 'calc' "
                + "AS $$\nclass MyErr(ValueError):\n    pass\ndef calc():\n    raise MyErr('bad value')\n$$");
        assertAnswer("REFUSED Python Interpreter Error: | Traceback (most recent call last): |   File "
                + "\"_udf_code.py\", line 5, in calc |     raise MyErr('bad value') | calc_module.MyErr: bad value |  "
                + "in function UE41 with handler calc",
            "SELECT ue41()");
        assertAnswer("Function UE42 successfully created.",
            "CREATE OR REPLACE FUNCTION ue42() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = 'h' AS "
                + "$$\ndef h():\n    class Inner(Exception):\n        pass\n    raise Inner('deep')\n$$");
        assertAnswer("REFUSED Python Interpreter Error: | Traceback (most recent call last): |   File "
                + "\"_udf_code.py\", line 5, in h |     raise Inner('deep') | h_module.h.<locals>.Inner: deep |  in "
                + "function UE42 with handler h",
            "SELECT ue42()");
        assertAnswer("Function UE43 successfully created.",
            "CREATE OR REPLACE FUNCTION ue43() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = 'h' AS "
                + "$$\ndef h():\n    return repr((type(h).__module__, h.__module__, h.__qualname__))\n$$");
        assertAnswer("Function UE44 successfully created.",
            "CREATE OR REPLACE FUNCTION ue44() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = 'h' AS "
                + "$$\nimport json\ndef h():\n    raise json.JSONDecodeError('x', 'doc', 0)\n$$");
        assertAnswer("REFUSED Python Interpreter Error: | Traceback (most recent call last): |   File "
                + "\"_udf_code.py\", line 4, in h |     raise json.JSONDecodeError('x', 'doc', 0) | "
                + "json.decoder.JSONDecodeError: x: line 1 column 1 (char 0) |  in function UE44 with handler h",
            "SELECT ue44()");
    }
}
