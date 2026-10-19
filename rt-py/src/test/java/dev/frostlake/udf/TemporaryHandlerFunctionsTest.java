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
 * A TEMPORARY Python or Java function is created without its handler judged (live-verified): CREATE answers
 * {@code Temporary function TE_PY successfully created, but not validated.}, and the first row that reaches a call
 * is refused in the sentence CREATE would have given, while a call no row reaches answers. A JavaScript or SQL
 * temporary function is judged as a permanent one is, and a Python procedure whose body defines no handler is
 * refused {@code Could not find handler in function P with handler nosuch} at the call.
 */
public class TemporaryHandlerFunctionsTest extends MeasuredCellsTestSupport {

    @Test
    public void temporaryHandlerFunctionsAreJudgedAtTheirFirstCall() {
        engine.execute("CREATE OR REPLACE TABLE tf_t (a INT)");
        engine.execute("INSERT INTO tf_t VALUES (1), (2)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TEMPORARY FUNCTION te_py(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'f' AS 'def f(x):\n    return x'",
                "Temporary function TE_PY successfully created, but not validated."},
            {"SELECT te_py(3)", "3"},
            {"CREATE OR REPLACE TEMPORARY FUNCTION te_py2(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'nosuch' AS 'def f(x):\n    return x'",
                "Temporary function TE_PY2 successfully created, but not validated."},
            {"SELECT te_py2(3)", "REFUSED Could not find handler in function TE_PY2 with handler nosuch"},
            {"CREATE OR REPLACE TEMPORARY FUNCTION te_py3(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'f' AS 'def f(x) return x'",
                "Temporary function TE_PY3 successfully created, but not validated."},
            {"CREATE OR REPLACE TEMPORARY FUNCTION te_py4(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'2.7' HANDLER = 'f' AS 'def f(x):\n    return x'",
                "REFUSED SQL compilation error: | invalid value '2.7' for property 'RUNTIME_VERSION'"},
            {"CREATE OR REPLACE FUNCTION pe_py2(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "HANDLER = 'nosuch' AS 'def f(x):\n    return x'",
                "REFUSED Could not find handler in function PE_PY2 with handler nosuch"},
            {"CREATE OR REPLACE TEMPORARY FUNCTION te_j(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS "
                + "'class H { public static int f(int x) { return x; } }'",
                "Temporary function TE_J successfully created, but not validated."},
            {"SELECT te_j(3)", "3"},
            {"CREATE OR REPLACE TEMPORARY FUNCTION te_j2(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS "
                + "'class H { nope }'",
                "Temporary function TE_J2 successfully created, but not validated."},
            {"SELECT te_j2(3)",
                "REFUSED Error while compiling source: /InlineCode.java:1: error: <identifier> expected | class H { "
                + "nope } |               ^ | 1 error |  in function TE_J2 with handler H.f"},
            {"CREATE OR REPLACE TEMPORARY FUNCTION te_j3(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.g' AS "
                + "'class H { public static int f(int x) { return x; } }'",
                "Temporary function TE_J3 successfully created, but not validated."},
            {"SELECT te_j3(3)",
                "REFUSED Failed to find a public method named \"g\" with 1 arguments in function TE_J3 with handler "
                + "H.g"},
            {"CREATE OR REPLACE TEMPORARY FUNCTION te_js(x DOUBLE) RETURNS DOUBLE LANGUAGE JAVASCRIPT AS 'return X;'",
                "Function TE_JS successfully created."},
            {"SELECT te_js(3)", "3.0"},
            {"CREATE OR REPLACE TEMPORARY FUNCTION te_js2(x DOUBLE) RETURNS DOUBLE LANGUAGE JAVASCRIPT AS 'return (;'",
                "Function TE_JS2 successfully created."},
            {"CREATE OR REPLACE TEMPORARY FUNCTION te_sql(x NUMBER) RETURNS NUMBER AS 'x'",
                "Function TE_SQL successfully created."},
            {"CREATE OR REPLACE TEMPORARY FUNCTION te_sql2(x NUMBER) RETURNS NUMBER AS 'nosuch_col'",
                "REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier 'NOSUCH_COL'"},
            {"CREATE OR REPLACE TEMPORARY AGGREGATE FUNCTION ae_t1(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON "
                + "RUNTIME_VERSION = '3.11' HANDLER = 'D' AS $$\nclass D:\n    def __init__(self):\n        self._s = "
                + "0\n    @property\n    def aggregate_state(self):\n        return self._s\n    def accumulate(self, "
                + "x):\n        self._s += x\n$$",
                "Temporary function AE_T1 successfully created, but not validated."},
            {"SELECT ae_t1(a) FROM tf_t", "REFUSED Could not find merge method in function AE_T1 with handler D"},
            {"CREATE OR REPLACE TEMPORARY AGGREGATE FUNCTION ae_tj(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = "
                + "'H' AS 'public class H { }'",
                "Temporary function AE_TJ successfully created, but not validated."},
            {"SELECT ae_tj(a) FROM tf_t",
                "REFUSED Failed to find a public method named \"initialize\" with 0 arguments | The initialize() "
                + "method is required for Java UDAFs in function AE_TJ with handler H"},
            {"CREATE OR REPLACE TEMPORARY PROCEDURE tp_py() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS 'def run(s):\n    return 1'",
                "Function TP_PY successfully created."},
            {"CALL tp_py()", "1"},
            {"CREATE OR REPLACE TEMPORARY PROCEDURE tp_py2() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'nosuch' AS 'def run(s):\n    return 1'",
                "Function TP_PY2 successfully created."},
            {"CALL tp_py2()", "REFUSED Could not find handler in function TP_PY2 with handler nosuch"},
            {"CREATE OR REPLACE TEMPORARY PROCEDURE tp_js() RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'return 1;'",
                "Function TP_JS successfully created."},
            {"DROP FUNCTION ae_t1(NUMBER)", "AE_T1 successfully dropped."},
            {"DROP FUNCTION te_py(NUMBER)", "TE_PY successfully dropped."},
        });
    }

    @Test
    public void proceduresAndTheOtherTemporaryForms() {
        engine.execute("CREATE OR REPLACE TABLE pp_t (a INT)");
        engine.execute("INSERT INTO pp_t VALUES (1)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE PROCEDURE pp_py2() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'nosuch' AS 'def run(s):\n    return 1'",
                "Function PP_PY2 successfully created."},
            {"CALL pp_py2()", "REFUSED Could not find handler in function PP_PY2 with handler nosuch"},
            {"CREATE OR REPLACE PROCEDURE pp_py3() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS 'def run(s) return 1'",
                "Function PP_PY3 successfully created."},
            {"CREATE OR REPLACE TEMPORARY PROCEDURE tp_py3() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS 'def run(s) return 1'",
                "Function TP_PY3 successfully created."},
            {"CREATE OR REPLACE PROCEDURE pp_py4(x INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS 'def run(s):\n    return 1'",
                "Function PP_PY4 successfully created."},
            {"CREATE OR REPLACE TEMPORARY FUNCTION te_py6(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'f' AS 'def f(x, y):\n    return x'",
                "Temporary function TE_PY6 successfully created, but not validated."},
            {"SELECT te_py6(3)",
                "REFUSED Python function is defined with 2 arguments, but UDF definition contains 1 arguments in "
                + "function TE_PY6 with handler f"},
            {"CREATE OR REPLACE TEMPORARY FUNCTION te_py7(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'f' AS 'def f(x):\n    return x' COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 2 at position 14 unexpected 'COMMENT'."},
            {"SHOW USER FUNCTIONS LIKE 'TE_PY7' ->> SELECT \"name\", \"description\" FROM $1", ""},
            {"CREATE OR REPLACE TEMPORARY SECURE FUNCTION te_py8(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON "
                + "RUNTIME_VERSION = '3.11' HANDLER = 'f' AS 'def f(x):\n    return x'",
                "Temporary function TE_PY8 successfully created, but not validated."},
            {"CREATE TEMPORARY FUNCTION IF NOT EXISTS te_py9(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION "
                + "= '3.11' HANDLER = 'nosuch' AS 'def f(x):\n    return x'",
                "Temporary function TE_PY9 successfully created, but not validated."},
            {"CREATE TEMPORARY FUNCTION IF NOT EXISTS te_py9(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION "
                + "= '3.11' HANDLER = 'f' AS 'def f(x):\n    return x'",
                "TE_PY9 already exists, statement succeeded."},
            {"SELECT te_py9(3)", "REFUSED Could not find handler in function TE_PY9 with handler nosuch"},
            {"CREATE OR REPLACE TEMPORARY FUNCTION te_py10(x NUMBER) RETURNS TABLE (y NUMBER) LANGUAGE PYTHON "
                + "RUNTIME_VERSION = '3.11' HANDLER = 'nosuch' AS 'class T:\n    def process(self, x):\n        yield "
                + "(x,)'",
                "Temporary function TE_PY10 successfully created, but not validated."},
            {"SELECT * FROM TABLE(te_py10(3))",
                "REFUSED Could not find handler class in function TE_PY10 with handler nosuch"},
            {"DESCRIBE FUNCTION te_py2_nosuch(NUMBER)",
                "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.TE_PY2_NOSUCH' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.TE_PY2_NOSUCH."},
            {"CREATE OR REPLACE TEMPORARY FUNCTION te_py11(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'nosuch' AS 'def f(x):\n    return x'",
                "Temporary function TE_PY11 successfully created, but not validated."},
            {"DESCRIBE FUNCTION te_py11(NUMBER) ->> SELECT \"property\", \"value\" FROM $1 WHERE \"property\" IN "
                + "('handler', 'language')",
                "language|PYTHON ;; handler|nosuch"},
            {"SELECT GET_DDL('FUNCTION', 'te_py11(NUMBER)')",
                "CREATE OR REPLACE FUNCTION \"TE_PY11\"(\"X\" NUMBER(38,0)) | RETURNS NUMBER(38,0) | LANGUAGE PYTHON "
                + "| RUNTIME_VERSION = '3.11' | HANDLER = 'nosuch' | AS 'def f(x): |     return x';"},
            {"SELECT te_py11(a) FROM pp_t WHERE FALSE", ""},
            {"SELECT COUNT(*) FROM INFORMATION_SCHEMA.FUNCTIONS WHERE FUNCTION_NAME = 'TE_PY11'", "1"},
        });
    }
}
