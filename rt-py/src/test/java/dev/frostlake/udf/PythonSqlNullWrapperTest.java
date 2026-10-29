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
 * A SQL NULL a Python handler's VARIANT parameter takes, a function's, a procedure's and a table function's alike
 * (live-verified): a sqlNullWrapper, whose type has no __module__, holds is_sql_null = True and nothing else, is
 * truthy, equals only itself and serializes to no JSON; a JSON null is None. An ARRAY or an OBJECT parameter takes None
 * for both. Handed back for a VARIANT the wrapper is a SQL NULL, where None is a JSON null; for a VARCHAR it is its
 * text, and inside a list or a dict no value at all. A TIME or a timestamp argument arrives cut to its parameter's
 * precision.
 */
public class PythonSqlNullWrapperTest extends MeasuredCellsTestSupport {

    /** The wrapper and None over a function, a procedure and the containers. */
    @Test
    public void nullVariantArguments() {
        engine.execute("CREATE OR REPLACE FUNCTION pw794(v VARIANT, w VARCHAR) RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.11' HANDLER = 'f' AS $$\nimport json\ndef f(v, w):\n    try:\n        if w == "
            + "'type':\n            r = type(v).__module__ + '.' + type(v).__qualname__\n        elif w == 'repr':\n   "
            + "         r = repr(v)\n        elif w == 'str':\n            r = str(v)\n        elif w == 'none':\n     "
            + "       r = str(v is None) + '|' + str(v == None)\n        elif w == 'bool':\n            r = "
            + "str(bool(v))\n        elif w == 'dir':\n            r = str([n for n in dir(v) if not "
            + "n.startswith('__')])\n        elif w == 'json':\n            r = json.dumps(v)\n        elif w == "
            + "'mro':\n            r = str([c.__name__ for c in type(v).__mro__])\n        else:\n            r = "
            + "'?'\n    except BaseException as e:\n        r = 'E:' + type(e).__name__ + ': ' + str(e)\n    return "
            + "r.replace('-', '/')\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE pp794(v VARIANT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, v):\n    return "
            + "(type(v).__module__ + '.' + type(v).__qualname__ + ':' + repr(v) + ':' + str(v is None)).replace('-', "
            + "'/')\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION pa794(v ARRAY, o OBJECT) RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.11' HANDLER = 'f' AS $$\ndef f(v, o):\n    return type(v).__qualname__ + ':' + "
            + "repr(v) + '|' + type(o).__qualname__ + ':' + repr(o)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION pr794(v VARIANT) RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' HANDLER = 'f' AS $$\ndef f(v):\n    return v\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION pt794(t TIME) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' HANDLER = 'f' AS $$\ndef f(t):\n    return type(t).__name__ + ':' + repr(t)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION pt3794(t TIME(3)) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' HANDLER = 'f' AS $$\ndef f(t):\n    return type(t).__name__ + ':' + repr(t)\n$$");
        assertCells(new String[][] {
            {"SELECT pw794(NULL, 'type')",
                "E:AttributeError: __module__"},
            {"SELECT pw794(NULL, 'none')",
                "False|False"},
            {"SELECT pw794(NULL, 'bool')",
                "True"},
            {"SELECT pw794(NULL, 'dir')",
                "['is_sql_null']"},
            {"SELECT pw794(NULL, 'json')",
                "E:TypeError: Object of type sqlNullWrapper is not JSON serializable"},
            {"SELECT pw794(NULL, 'mro')",
                "['sqlNullWrapper', 'object']"},
            {"SELECT pw794(PARSE_JSON('null'), 'type')",
                "builtins.NoneType"},
            {"SELECT pw794(PARSE_JSON('null'), 'repr')",
                "None"},
            {"SELECT pw794(PARSE_JSON('null'), 'str')",
                "None"},
            {"SELECT pw794(PARSE_JSON('null'), 'none')",
                "True|True"},
            {"SELECT pw794(PARSE_JSON('null'), 'bool')",
                "False"},
            {"SELECT pw794(PARSE_JSON('null'), 'dir')",
                "[]"},
            {"SELECT pw794(PARSE_JSON('null'), 'json')",
                "null"},
            {"SELECT pw794(PARSE_JSON('null'), 'mro')",
                "['NoneType', 'object']"},
            {"SELECT pw794(NULL::VARIANT, 'type')",
                "E:AttributeError: __module__"},
            {"SELECT pw794(TO_VARIANT(NULL), 'type')",
                "E:AttributeError: __module__"},
            {"SELECT pw794(PARSE_JSON(NULL), 'type')",
                "E:AttributeError: __module__"},
            {"SELECT pw794(PARSE_JSON('[null]'), 'repr')",
                "[None]"},
            {"SELECT pw794(OBJECT_CONSTRUCT_KEEP_NULL('a', NULL), 'repr')",
                "{'a': None}"},
            {"SELECT pw794(c, 'type') FROM (SELECT NULL::VARIANT AS c)",
                "E:AttributeError: __module__"},
            {"SELECT pw794(c, 'type') FROM (SELECT PARSE_JSON('null') AS c)",
                "builtins.NoneType"},
            {"CALL pp794(NULL)",
                "REFUSED Python Interpreter Error: | Traceback (most recent call last): |   File \"_udf_code.py\", "
                + "line 3, in run |     return (type(v).__module__ + '.' + type(v).__qualname__ + ':' + repr(v) + ':' "
                + "+ str(v is None)).replace('-', '/') |             ^^^^^^^^^^^^^^^^^^ | AttributeError: __module__ | "
                + " in function PP794 with handler run"},
            {"CALL pp794(PARSE_JSON('null'))",
                "builtins.NoneType:None:True"},
            {"CALL pp794(NULL::VARIANT)",
                "REFUSED Python Interpreter Error: | Traceback (most recent call last): |   File \"_udf_code.py\", "
                + "line 3, in run |     return (type(v).__module__ + '.' + type(v).__qualname__ + ':' + repr(v) + ':' "
                + "+ str(v is None)).replace('-', '/') |             ^^^^^^^^^^^^^^^^^^ | AttributeError: __module__ | "
                + " in function PP794 with handler run"},
            {"CALL pp794(PARSE_JSON('[null]'))",
                "builtins.list:[None]:False"},
            {"SELECT pa794(NULL, NULL)",
                "NoneType:None|NoneType:None"},
            {"SELECT pa794(PARSE_JSON('null'), PARSE_JSON('null'))",
                "NoneType:None|NoneType:None"},
            {"SELECT pa794(ARRAY_CONSTRUCT(NULL), OBJECT_CONSTRUCT_KEEP_NULL('a', NULL))",
                "list:[None]|dict:{'a': None}"},
            {"SELECT pr794(NULL) IS NULL, IS_NULL_VALUE(pr794(NULL))",
                "true|null"},
            {"SELECT pr794(PARSE_JSON('null')) IS NULL, IS_NULL_VALUE(pr794(PARSE_JSON('null')))",
                "false|true"},
            {"SELECT TO_JSON(pr794(PARSE_JSON('[null]')))",
                "[null]"},
            {"SELECT pt794('10:11:12.345'::TIME(3))",
                "time:datetime.time(10, 11, 12, 345000)"},
            {"SELECT pt794('10:11:12.123456789'::TIME(9))",
                "time:datetime.time(10, 11, 12, 123456)"},
            {"SELECT pt794('10:11:12.9'::TIME(0))",
                "time:datetime.time(10, 11, 12)"},
            {"SELECT pt794('23:59:59.999999999'::TIME)",
                "time:datetime.time(23, 59, 59, 999999)"},
            {"SELECT pt794('00:00:00'::TIME)",
                "time:datetime.time(0, 0)"},
            {"SELECT pt794('10:11:12.1234567'::TIME(7))",
                "time:datetime.time(10, 11, 12, 123456)"},
            {"SELECT pt794('10:11:12.000001'::TIME(6))",
                "time:datetime.time(10, 11, 12, 1)"},
            {"SELECT pt794('10:11:12.0000005'::TIME(7))",
                "time:datetime.time(10, 11, 12)"},
            {"SELECT pt3794('10:11:12.123456'::TIME(6))",
                "time:datetime.time(10, 11, 12, 123000)"},
            {"SELECT pt3794('10:11:12.9999'::TIME(4))",
                "time:datetime.time(10, 11, 12, 999000)"},
        });
    }

    /** The wrapper's attributes, type, equality, answers and a table function's NULL. */
    @Test
    public void wrapperSurface() {
        engine.execute("CREATE OR REPLACE FUNCTION pw795(v VARIANT, w VARCHAR) RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.11' HANDLER = 'f' AS $$\ndef f(v, w):\n    try:\n        if w == 'attr':\n         "
            + "   r = repr(v.is_sql_null)\n        elif w == 'call':\n            r = str(callable(v.is_sql_null))\n   "
            + "     elif w == 'tattr':\n            r = repr(type(v).is_sql_null)\n        elif w == 'name':\n         "
            + "   r = type(v).__name__ + '|' + type(v).__qualname__\n        elif w == 'glob':\n            r = "
            + "str('sqlNullWrapper' in globals()) + '|' + str('sqlNullWrapper' in dir(__builtins__))\n        elif w "
            + "== 'eq':\n            r = str(v == v) + '|' + str(hash(v) == hash(v)) + '|' + str(v != None)\n        "
            + "elif w == 'repr':\n            r = repr(v).split(' at ')[0] + '|' + str(v).split(' at ')[0]\n        "
            + "elif w == 'dict':\n            r = str(getattr(v, '__dict__', 'none'))\n        elif w == 'slots':\n    "
            + "        r = str(getattr(type(v), '__slots__', 'none'))\n        elif w == 'doc':\n            r = "
            + "repr(type(v).__doc__)\n        elif w == 'bases':\n            r = str([c.__name__ for c in "
            + "type(v).__bases__])\n        elif w == 'tdict':\n            r = str(sorted(type(v).__dict__.keys()))\n "
            + "       elif w == 'cls':\n            r = repr(type(v))\n        elif w == 'vmod':\n            r = "
            + "repr(getattr(v, '__module__', 'missing'))\n        elif w == 'isinst':\n            r = "
            + "str(isinstance(v, (int, str, float, list, dict, bool)))\n        elif w == 'new':\n            r = "
            + "repr(type(type(v)()).__name__)\n        elif w == 'same':\n            import sys\n            r = "
            + "str(getattr(sys.modules[__name__], 'kept', None) is v)\n            setattr(sys.modules[__name__], "
            + "'kept', v)\n        elif w == 'fmt':\n            r = f'{v!s}'.split(' at ')[0] + '|' + format(v, "
            + "'').split(' at ')[0]\n        else:\n            r = '?'\n    except BaseException as e:\n        r = "
            + "'E:' + type(e).__name__ + ': ' + str(e)\n    return r\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION prs795(v VARIANT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' HANDLER = 'f' AS $$\ndef f(v):\n    return v\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION pra795(v VARIANT) RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' HANDLER = 'f' AS $$\ndef f(v):\n    return [v, 1]\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION pro795(v VARIANT) RETURNS OBJECT LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' HANDLER = 'f' AS $$\ndef f(v):\n    return {'k': v}\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE ppr795(v VARIANT) RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION "
            + "= '3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, v):\n    "
            + "return v\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION ptf795(v VARIANT) RETURNS TABLE (t VARCHAR) LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.11' HANDLER = 'H' AS $$\nclass H:\n    def process(self, v):\n        yield "
            + "(type(v).__name__,)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION pts795(t TIMESTAMP_NTZ(3), z TIMESTAMP_NTZ(0)) RETURNS VARCHAR "
            + "LANGUAGE PYTHON RUNTIME_VERSION = '3.11' HANDLER = 'f' AS $$\ndef f(t, z):\n    return (repr(t) + '|' + "
            + "repr(z))\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION pt0795(t TIME(0)) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' HANDLER = 'f' AS $$\ndef f(t):\n    return repr(t)\n$$");
        assertCells(new String[][] {
            {"SELECT pw795(NULL, 'attr')",
                "True"},
            {"SELECT pw795(NULL, 'call')",
                "False"},
            {"SELECT pw795(NULL, 'tattr')",
                "True"},
            {"SELECT pw795(NULL, 'name')",
                "sqlNullWrapper|sqlNullWrapper"},
            {"SELECT pw795(NULL, 'glob')",
                "False|False"},
            {"SELECT pw795(NULL, 'eq')",
                "True|True|True"},
            {"SELECT pw795(NULL, 'repr')",
                "<sqlNullWrapper object|<sqlNullWrapper object"},
            {"SELECT pw795(NULL, 'dict')",
                "none"},
            {"SELECT pw795(NULL, 'slots')",
                "none"},
            {"SELECT pw795(NULL, 'doc')",
                "None"},
            {"SELECT pw795(NULL, 'bases')",
                "['object']"},
            {"SELECT pw795(NULL, 'tdict')",
                "['__doc__', 'is_sql_null']"},
            {"SELECT pw795(NULL, 'cls')",
                "<class 'sqlNullWrapper'>"},
            {"SELECT pw795(NULL, 'vmod')",
                "'missing'"},
            {"SELECT pw795(NULL, 'isinst')",
                "False"},
            {"SELECT pw795(NULL, 'new')",
                "'sqlNullWrapper'"},
            {"SELECT pw795(NULL, 'fmt')",
                "<sqlNullWrapper object|<sqlNullWrapper object"},
            {"SELECT pw795(NULL, 'same')",
                "False"},
            {"SELECT pw795(NULL, 'same')",
                "False"},
            {"SELECT prs795(NULL) IS NULL",
                "false"},
            {"SELECT TO_JSON(pra795(NULL))",
                "REFUSED Python Interpreter Error: | TypeError: Object of type sqlNullWrapper is not serializable in "
                + "function PRA795 with handler f"},
            {"SELECT TO_JSON(pro795(NULL))",
                "REFUSED Python Interpreter Error: | TypeError: Object of type sqlNullWrapper is not serializable in "
                + "function PRO795 with handler f"},
            {"CALL ppr795(NULL)",
                "null"},
            {"CALL ppr795(PARSE_JSON('null'))",
                "null"},
            {"SELECT * FROM TABLE(ptf795(NULL))",
                "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'PTF795': (NULL)"},
            {"SELECT * FROM TABLE(ptf795(PARSE_JSON('null')))",
                "NoneType"},
            {"SELECT pts795('2024-01-02 10:11:12.123456789'::TIMESTAMP_NTZ(9), '2024-01-02 "
                + "10:11:12.9'::TIMESTAMP_NTZ(1))",
                "datetime.datetime(2024, 1, 2, 10, 11, 12, 123000)|datetime.datetime(2024, 1, 2, 10, 11, 12)"},
            {"SELECT pt0795('10:11:12.9'::TIME(1))",
                "datetime.time(10, 11, 12)"},
        });
    }

    /** A procedure's answer of the wrapper and of None, and a TIME at its precision. */
    @Test
    public void procedureAnswers() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        assertCells(new String[][] {
            {"CREATE OR REPLACE PROCEDURE ppr796(v VARIANT) RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, v):\n    return "
                + "v\n$$",
                "Function PPR796 successfully created."},
            {"CALL ppr796(NULL)",
                "null"},
            {"SELECT $1 IS NULL, IS_NULL_VALUE($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "true|null"},
            {"CALL ppr796(PARSE_JSON('null'))",
                "null"},
            {"SELECT $1 IS NULL, IS_NULL_VALUE($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "false|true"},
            {"CREATE OR REPLACE PROCEDURE ppt796(t TIME(3)) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, t):\n    return "
                + "repr(t)\n$$",
                "Function PPT796 successfully created."},
            {"CALL ppt796('10:11:12.9876'::TIME(4))",
                "datetime.time(10, 11, 12, 987000)"},
            {"ALTER SESSION UNSET TIMEZONE",
                "Statement executed successfully."},
        });
    }
}
