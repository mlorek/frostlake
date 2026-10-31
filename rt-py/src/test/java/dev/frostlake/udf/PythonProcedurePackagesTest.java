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
 * A Python procedure's handler is handed a Snowpark session, which needs the snowflake-snowpark-python package
 * (all live-verified): a procedure whose PACKAGES names nothing, an empty list or none at all, a table procedure
 * too, is refused as it is created, "ModuleNotFoundError: No module named 'snowflake.snowpark' in function P1 with
 * handler main" after "Python Interpreter Error:", and one naming other packages when it is called. The name is
 * read in any case and with or without a version; a function needs no Snowpark.
 */
public class PythonProcedurePackagesTest extends MeasuredCellsTestSupport {

    @Test
    public void aProcedureNeedsTheSnowparkPackage() {
        assertAnswer("REFUSED Python Interpreter Error: | ModuleNotFoundError: No module named 'snowflake.snowpark' "
                + "in function P1 with handler main",
            "CREATE OR REPLACE PROCEDURE p1() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = "
                + "'main' AS $$\ndef main(session):\n    return 'ran'\n$$");
        assertAnswer("REFUSED SQL compilation error: | Unknown function P1.", "CALL p1()");
        assertAnswer("Function P2 successfully created.",
            "CREATE OR REPLACE PROCEDURE p2() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' PACKAGES = "
                + "('numpy') HANDLER = 'main' AS $$\ndef main(session):\n    return 'ran'\n$$");
        assertAnswer("REFUSED Python Interpreter Error: | ModuleNotFoundError: No module named 'snowflake.snowpark' "
                + "in function P2 with handler main",
            "CALL p2()");
        assertAnswer("Function P3 successfully created.",
            "CREATE OR REPLACE PROCEDURE p3() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'main' AS $$\ndef main(session):\n    return 'ran'\n$$");
        assertAnswer("ran", "CALL p3()");
        assertAnswer("Function P4 successfully created.",
            "CREATE OR REPLACE PROCEDURE p4() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' PACKAGES = "
                + "('SNOWFLAKE-SNOWPARK-PYTHON') HANDLER = 'main' AS $$\ndef main(session):\n    return 'ran'\n$$");
        assertAnswer("ran", "CALL p4()");
        assertAnswer("Function P5 successfully created.",
            "CREATE OR REPLACE PROCEDURE p5() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' PACKAGES = "
                + "('snowflake-snowpark-python==1.11.1') HANDLER = 'main' AS $$\ndef main(session):\n    return "
                + "'ran'\n$$");
        assertAnswer("REFUSED Python Interpreter Error: | ModuleNotFoundError: No module named 'snowflake.snowpark' "
                + "in function P6 with handler main",
            "CREATE OR REPLACE PROCEDURE p6() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' PACKAGES = () "
                + "HANDLER = 'main' AS $$\ndef main(session):\n    return 'ran'\n$$");
        assertAnswer("REFUSED SQL compilation error: | Unknown function P6.", "CALL p6()");
        assertAnswer("Function P7 successfully created.",
            "CREATE OR REPLACE PROCEDURE p7() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' PACKAGES = "
                + "('snowflake-snowpark-python', 'numpy') HANDLER = 'main' AS $$\ndef main(session):\n    return "
                + "'ran'\n$$");
        assertAnswer("ran", "CALL p7()");
        assertAnswer("Function F1 successfully created.",
            "CREATE OR REPLACE FUNCTION f1() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = 'h' "
                + "AS $$\ndef h():\n    return 'f'\n$$");
        assertAnswer("f", "SELECT f1()");
        assertAnswer("REFUSED Python Interpreter Error: | ModuleNotFoundError: No module named 'snowflake.snowpark' "
                + "in function P8 with handler main",
            "CREATE OR REPLACE PROCEDURE p8() RETURNS TABLE (a INT) LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER "
                + "= 'main' AS $$\ndef main(session):\n    return session.sql('SELECT 1 a')\n$$");
    }
}
