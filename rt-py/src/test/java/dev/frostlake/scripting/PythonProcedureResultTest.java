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
package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * What a CALL of a Python procedure answers: one row in a column named after the procedure and typed by its
 * RETURNS - a NULL when the handler returns {@code None} - holding the handler's value as that type. A whole
 * NUMBER takes {@code int(value)}, which truncates, and a NUMBER with a scale rounds half to even; a FLOAT takes
 * {@code float(value)}, a text type {@code str(value)} and a BOOLEAN {@code bool(value)}; a DATE takes a date or a
 * datetime's date and a TIMESTAMP a datetime - {@code Return value should be a date object} for anything else
 * -; an ARRAY takes a list and an OBJECT a dict; a VARIANT takes whatever JSON holds; a BINARY takes bytes. A
 * value the declared width cannot hold is refused, each refusal naming the procedure and its handler. For a
 * VARIANT {@code None} is a JSON null, for an ARRAY and an OBJECT a SQL NULL; inside a semi-structured answer
 * every float is a DOUBLE, a tuple a list, and a date, a time, a datetime, a {@code Decimal} and {@code bytes}
 * the text {@code str(value)} gives; any other class is {@code TypeError: Object of type set is not
 * serializable}. Every answer is live-verified.
 */
public class PythonProcedureResultTest extends MeasuredCellsTestSupport {

    @Test
    public void theAnswerIsTypedByReturns() {
        assertAnswer(
            "Function P00 successfully created.",
            "CREATE OR REPLACE PROCEDURE p00() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return 7\n$$");
        assertAnswer(
            "7",
            "CALL p00()");
        assertShape(
            "P00:NUMBER(38,0)",
            "CALL p00()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 7",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P01 successfully created.",
            "CREATE OR REPLACE PROCEDURE p01() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return 7.5\n$$");
        assertAnswer(
            "7",
            "CALL p01()");
        assertShape(
            "P01:NUMBER(38,0)",
            "CALL p01()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 7",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P02 successfully created.",
            "CREATE OR REPLACE PROCEDURE p02() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return '7'\n$$");
        assertAnswer(
            "7",
            "CALL p02()");
        assertShape(
            "P02:NUMBER(38,0)",
            "CALL p02()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 7",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P03 successfully created.",
            "CREATE OR REPLACE PROCEDURE p03() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return True\n$$");
        assertAnswer(
            "1",
            "CALL p03()");
        assertShape(
            "P03:NUMBER(38,0)",
            "CALL p03()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 1",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P04 successfully created.",
            "CREATE OR REPLACE PROCEDURE p04() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return None\n$$");
        assertAnswer(
            "null",
            "CALL p04()");
        assertShape(
            "P04:NUMBER(38,0)",
            "CALL p04()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P05 successfully created.",
            "CREATE OR REPLACE PROCEDURE p05() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return 'x'\n$$");
        assertAnswer(
            "REFUSED Python Interpreter Error: | ValueError: invalid literal for int() with base 10: 'x' in "
                + "function P05 with handler run",
            "CALL p05()");
        assertShape(
            "REFUSED Python Interpreter Error: | ValueError: invalid literal for int() with base 10: 'x' in "
                + "function P05 with handler run",
            "CALL p05()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P06 successfully created.",
            "CREATE OR REPLACE PROCEDURE p06() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport decimal\ndef run(session):\n    "
                + "return decimal.Decimal('2.5')\n$$");
        assertAnswer(
            "2",
            "CALL p06()");
        assertShape(
            "P06:NUMBER(38,0)",
            "CALL p06()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 2",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P07 successfully created.",
            "CREATE OR REPLACE PROCEDURE p07() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return "
                + "12345678901234567890123456789012345678\n$$");
        assertAnswer(
            "12345678901234567890123456789012345678",
            "CALL p07()");
        assertShape(
            "P07:NUMBER(38,0)",
            "CALL p07()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 12345678901234567890123456789012345678",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P08 successfully created.",
            "CREATE OR REPLACE PROCEDURE p08() RETURNS NUMBER(10,2) LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return "
                + "1.38875\n$$");
        assertAnswer(
            "1.39",
            "CALL p08()");
        assertShape(
            "P08:NUMBER(10,2)",
            "CALL p08()");
        assertAnswer(
            "NUMBER(10,2)[SB8] / 1.39",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P09 successfully created.",
            "CREATE OR REPLACE PROCEDURE p09() RETURNS NUMBER(10,2) LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport decimal\ndef "
                + "run(session):\n    return decimal.Decimal('1.38875')\n$$");
        assertAnswer(
            "1.39",
            "CALL p09()");
        assertShape(
            "P09:NUMBER(10,2)",
            "CALL p09()");
        assertAnswer(
            "NUMBER(10,2)[SB8] / 1.39",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P10 successfully created.",
            "CREATE OR REPLACE PROCEDURE p10() RETURNS NUMBER(10,2) LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return "
                + "7\n$$");
        assertAnswer(
            "7.00",
            "CALL p10()");
        assertShape(
            "P10:NUMBER(10,2)",
            "CALL p10()");
        assertAnswer(
            "NUMBER(10,2)[SB8] / 7.00",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P11 successfully created.",
            "CREATE OR REPLACE PROCEDURE p11() RETURNS NUMBER(3,0) LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return "
                + "1000\n$$");
        assertAnswer(
            "REFUSED Python value cannot be losslessly represented as a Snowflake NUMBER with a precision of 3, "
                + "value: 1000 in function P11 with handler run",
            "CALL p11()");
        assertShape(
            "REFUSED Python value cannot be losslessly represented as a Snowflake NUMBER with a precision of 3, "
                + "value: 1000 in function P11 with handler run",
            "CALL p11()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P12 successfully created.",
            "CREATE OR REPLACE PROCEDURE p12() RETURNS FLOAT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return 1\n$$");
        assertAnswer(
            "1.0",
            "CALL p12()");
        shape(
            "CALL p12()");
        assertAnswer(
            "FLOAT[DOUBLE] / 1",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P13 successfully created.",
            "CREATE OR REPLACE PROCEDURE p13() RETURNS FLOAT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return '1.5'\n$$");
        assertAnswer(
            "1.5",
            "CALL p13()");
        shape(
            "CALL p13()");
        assertAnswer(
            "FLOAT[DOUBLE] / 1.5",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P14 successfully created.",
            "CREATE OR REPLACE PROCEDURE p14() RETURNS FLOAT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return "
                + "float('nan')\n$$");
        assertAnswer(
            "NaN",
            "CALL p14()");
        shape(
            "CALL p14()");
        assertAnswer(
            "FLOAT[DOUBLE] / NaN",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P15 successfully created.",
            "CREATE OR REPLACE PROCEDURE p15() RETURNS FLOAT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return None\n$$");
        assertAnswer(
            "null",
            "CALL p15()");
        shape(
            "CALL p15()");
        assertAnswer(
            "FLOAT[DOUBLE] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P16 successfully created.",
            "CREATE OR REPLACE PROCEDURE p16() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return 7\n$$");
        assertAnswer(
            "7",
            "CALL p16()");
        assertShape(
            "P16:VARCHAR(134217728)",
            "CALL p16()");
        assertAnswer(
            "VARCHAR(134217728)[LOB] / 7",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P17 successfully created.",
            "CREATE OR REPLACE PROCEDURE p17() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return [1, 2]\n$$");
        assertAnswer(
            "[1, 2]",
            "CALL p17()");
        assertShape(
            "P17:VARCHAR(134217728)",
            "CALL p17()");
        assertAnswer(
            "VARCHAR(134217728)[LOB] / [1, 2]",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P18 successfully created.",
            "CREATE OR REPLACE PROCEDURE p18() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return {'a': 1}\n$$");
        assertAnswer(
            "{'a': 1}",
            "CALL p18()");
        assertShape(
            "P18:VARCHAR(134217728)",
            "CALL p18()");
        assertAnswer(
            "VARCHAR(134217728)[LOB] / {'a': 1}",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P19 successfully created.",
            "CREATE OR REPLACE PROCEDURE p19() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return True\n$$");
        assertAnswer(
            "True",
            "CALL p19()");
        assertShape(
            "P19:VARCHAR(134217728)",
            "CALL p19()");
        assertAnswer(
            "VARCHAR(134217728)[LOB] / True",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P20 successfully created.",
            "CREATE OR REPLACE PROCEDURE p20() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport datetime\ndef run(session):\n    "
                + "return datetime.date(2024, 1, 2)\n$$");
        assertAnswer(
            "2024-01-02",
            "CALL p20()");
        assertShape(
            "P20:VARCHAR(134217728)",
            "CALL p20()");
        assertAnswer(
            "VARCHAR(134217728)[LOB] / 2024-01-02",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P21 successfully created.",
            "CREATE OR REPLACE PROCEDURE p21() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return None\n$$");
        assertAnswer(
            "null",
            "CALL p21()");
        assertShape(
            "P21:VARCHAR(134217728)",
            "CALL p21()");
        assertAnswer(
            "VARCHAR(134217728)[LOB] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P22 successfully created.",
            "CREATE OR REPLACE PROCEDURE p22() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return 1.5\n$$");
        assertAnswer(
            "1.5",
            "CALL p22()");
        assertShape(
            "P22:VARCHAR(134217728)",
            "CALL p22()");
        assertAnswer(
            "VARCHAR(134217728)[LOB] / 1.5",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P23 successfully created.",
            "CREATE OR REPLACE PROCEDURE p23() RETURNS BOOLEAN LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return True\n$$");
        assertAnswer(
            "true",
            "CALL p23()");
        assertShape(
            "P23:BOOLEAN",
            "CALL p23()");
        assertAnswer(
            "BOOLEAN[SB1] / true",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P24 successfully created.",
            "CREATE OR REPLACE PROCEDURE p24() RETURNS BOOLEAN LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return 1\n$$");
        assertAnswer(
            "true",
            "CALL p24()");
        assertShape(
            "P24:BOOLEAN",
            "CALL p24()");
        assertAnswer(
            "BOOLEAN[SB1] / true",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P25 successfully created.",
            "CREATE OR REPLACE PROCEDURE p25() RETURNS BOOLEAN LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return 'true'\n$$");
        assertAnswer(
            "true",
            "CALL p25()");
        assertShape(
            "P25:BOOLEAN",
            "CALL p25()");
        assertAnswer(
            "BOOLEAN[SB1] / true",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P26 successfully created.",
            "CREATE OR REPLACE PROCEDURE p26() RETURNS BOOLEAN LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return None\n$$");
        assertAnswer(
            "null",
            "CALL p26()");
        assertShape(
            "P26:BOOLEAN",
            "CALL p26()");
        assertAnswer(
            "BOOLEAN[SB1] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P27 successfully created.",
            "CREATE OR REPLACE PROCEDURE p27() RETURNS BOOLEAN LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return 'maybe'\n$$");
        assertAnswer(
            "true",
            "CALL p27()");
        assertShape(
            "P27:BOOLEAN",
            "CALL p27()");
        assertAnswer(
            "BOOLEAN[SB1] / true",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P28 successfully created.",
            "CREATE OR REPLACE PROCEDURE p28() RETURNS DATE LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport datetime\ndef run(session):\n    "
                + "return datetime.date(2024, 1, 2)\n$$");
        assertAnswer(
            "2024-01-02",
            "CALL p28()");
        assertShape(
            "P28:DATE",
            "CALL p28()");
        assertAnswer(
            "DATE[SB4] / 2024-01-02",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P29 successfully created.",
            "CREATE OR REPLACE PROCEDURE p29() RETURNS DATE LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return "
                + "'2024-01-02'\n$$");
        assertAnswer(
            "REFUSED Return value should be a date object in function P29 with handler run",
            "CALL p29()");
        assertShape(
            "REFUSED Return value should be a date object in function P29 with handler run",
            "CALL p29()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P30 successfully created.",
            "CREATE OR REPLACE PROCEDURE p30() RETURNS DATE LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport datetime\ndef run(session):\n    "
                + "return datetime.datetime(2024, 1, 2, 3, 4, 5)\n$$");
        assertAnswer(
            "2024-01-02",
            "CALL p30()");
        assertShape(
            "P30:DATE",
            "CALL p30()");
        assertAnswer(
            "DATE[SB4] / 2024-01-02",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P31 successfully created.",
            "CREATE OR REPLACE PROCEDURE p31() RETURNS DATE LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return None\n$$");
        assertAnswer(
            "null",
            "CALL p31()");
        assertShape(
            "P31:DATE",
            "CALL p31()");
        assertAnswer(
            "DATE[SB4] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P32 successfully created.",
            "CREATE OR REPLACE PROCEDURE p32() RETURNS TIME LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport datetime\ndef run(session):\n    "
                + "return datetime.time(3, 4, 5)\n$$");
        assertAnswer(
            "03:04:05",
            "CALL p32()");
        assertShape(
            "P32:TIME",
            "CALL p32()");
        assertAnswer(
            "TIME(9)[SB8] / 03:04:05",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P33 successfully created.",
            "CREATE OR REPLACE PROCEDURE p33() RETURNS TIMESTAMP_NTZ LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport datetime\ndef "
                + "run(session):\n    return datetime.datetime(2024, 1, 2, 3, 4, 5, 678901)\n$$");
        answer(
            "CALL p33()");
        assertShape(
            "P33:TIMESTAMP_NTZ",
            "CALL p33()");
        answer(
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P34 successfully created.",
            "CREATE OR REPLACE PROCEDURE p34() RETURNS TIMESTAMP_NTZ LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return "
                + "'2024-01-02 03:04:05'\n$$");
        assertAnswer(
            "REFUSED Return value should be a datetime object in function P34 with handler run",
            "CALL p34()");
        assertShape(
            "REFUSED Return value should be a datetime object in function P34 with handler run",
            "CALL p34()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P35 successfully created.",
            "CREATE OR REPLACE PROCEDURE p35() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return {'a': 1, "
                + "'b': [1, 2]}\n$$");
        assertAnswer(
            "{\"a\":1,\"b\":[1,2]}",
            "CALL p35()");
        assertShape(
            "P35:VARIANT",
            "CALL p35()");
        assertAnswer(
            "VARIANT[LOB] / {\"a\":1,\"b\":[1,2]}",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P36 successfully created.",
            "CREATE OR REPLACE PROCEDURE p36() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return [1, None]\n$$");
        assertAnswer(
            "[1,null]",
            "CALL p36()");
        assertShape(
            "P36:VARIANT",
            "CALL p36()");
        assertAnswer(
            "VARIANT[LOB] / [1,null]",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P37 successfully created.",
            "CREATE OR REPLACE PROCEDURE p37() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return 1\n$$");
        assertAnswer(
            "1",
            "CALL p37()");
        assertShape(
            "P37:VARIANT",
            "CALL p37()");
        assertAnswer(
            "VARIANT[LOB] / 1",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P38 successfully created.",
            "CREATE OR REPLACE PROCEDURE p38() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return 'a'\n$$");
        assertAnswer(
            "a",
            "CALL p38()");
        assertShape(
            "P38:VARIANT",
            "CALL p38()");
        assertAnswer(
            "VARIANT[LOB] / a",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P39 successfully created.",
            "CREATE OR REPLACE PROCEDURE p39() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return None\n$$");
        assertAnswer(
            "null",
            "CALL p39()");
        assertShape(
            "P39:VARIANT",
            "CALL p39()");
        assertAnswer(
            "VARIANT[LOB] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P40 successfully created.",
            "CREATE OR REPLACE PROCEDURE p40() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return True\n$$");
        assertAnswer(
            "true",
            "CALL p40()");
        assertShape(
            "P40:VARIANT",
            "CALL p40()");
        assertAnswer(
            "VARIANT[LOB] / true",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P41 successfully created.",
            "CREATE OR REPLACE PROCEDURE p41() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return 1.5\n$$");
        assertAnswer(
            "1.5",
            "CALL p41()");
        assertShape(
            "P41:VARIANT",
            "CALL p41()");
        assertAnswer(
            "VARIANT[LOB] / 1.5",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P42 successfully created.",
            "CREATE OR REPLACE PROCEDURE p42() RETURNS ARRAY LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return [1, 2]\n$$");
        assertAnswer(
            "[1,2]",
            "CALL p42()");
        assertShape(
            "P42:ARRAY",
            "CALL p42()");
        assertAnswer(
            "ARRAY[LOB] / [1,2]",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P43 successfully created.",
            "CREATE OR REPLACE PROCEDURE p43() RETURNS ARRAY LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return 1\n$$");
        assertAnswer(
            "REFUSED python value needs to be a list to convert to Snowflake ARRAY in function P43 with handler "
                + "run",
            "CALL p43()");
        assertShape(
            "REFUSED python value needs to be a list to convert to Snowflake ARRAY in function P43 with handler "
                + "run",
            "CALL p43()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P44 successfully created.",
            "CREATE OR REPLACE PROCEDURE p44() RETURNS ARRAY LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return {'a': 1}\n$$");
        assertAnswer(
            "REFUSED python value needs to be a list to convert to Snowflake ARRAY in function P44 with handler "
                + "run",
            "CALL p44()");
        assertShape(
            "REFUSED python value needs to be a list to convert to Snowflake ARRAY in function P44 with handler "
                + "run",
            "CALL p44()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P45 successfully created.",
            "CREATE OR REPLACE PROCEDURE p45() RETURNS ARRAY LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return (1, 2)\n$$");
        assertAnswer(
            "REFUSED python value needs to be a list to convert to Snowflake ARRAY in function P45 with handler "
                + "run",
            "CALL p45()");
        assertShape(
            "REFUSED python value needs to be a list to convert to Snowflake ARRAY in function P45 with handler "
                + "run",
            "CALL p45()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P46 successfully created.",
            "CREATE OR REPLACE PROCEDURE p46() RETURNS OBJECT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return {'a': 1}\n$$");
        assertAnswer(
            "{\"a\":1}",
            "CALL p46()");
        assertShape(
            "P46:OBJECT",
            "CALL p46()");
        assertAnswer(
            "OBJECT[LOB] / {\"a\":1}",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P47 successfully created.",
            "CREATE OR REPLACE PROCEDURE p47() RETURNS OBJECT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return [1]\n$$");
        assertAnswer(
            "REFUSED python value needs to be a dict to convert to Snowflake OBJECT in function P47 with handler "
                + "run",
            "CALL p47()");
        assertShape(
            "REFUSED python value needs to be a dict to convert to Snowflake OBJECT in function P47 with handler "
                + "run",
            "CALL p47()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P48 successfully created.",
            "CREATE OR REPLACE PROCEDURE p48() RETURNS BINARY LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return "
                + "b'\\xab\\xcd'\n$$");
        assertAnswer(
            "ABCD",
            "CALL p48()");
        assertShape(
            "P48:BINARY",
            "CALL p48()");
        assertAnswer(
            "BINARY(67108864)[LOB] / ABCD",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P49 successfully created.",
            "CREATE OR REPLACE PROCEDURE p49() RETURNS BINARY LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return 'ABCD'\n$$");
        assertAnswer(
            "REFUSED Python Interpreter Error: | TypeError: cannot convert 'str' object to bytes in function P49 "
                + "with handler run",
            "CALL p49()");
        assertShape(
            "REFUSED Python Interpreter Error: | TypeError: cannot convert 'str' object to bytes in function P49 "
                + "with handler run",
            "CALL p49()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P50 successfully created.",
            "CREATE OR REPLACE PROCEDURE p50() RETURNS BINARY LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return None\n$$");
        assertAnswer(
            "null",
            "CALL p50()");
        assertShape(
            "P50:BINARY",
            "CALL p50()");
        assertAnswer(
            "BINARY(67108864)[LOB] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }

    @Test
    public void roundingRangeAndNulls() {
        assertAnswer(
            "Function XP00 successfully created.",
            "CREATE OR REPLACE PROCEDURE xp00() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport decimal\ndef run(session):\n    "
                + "return decimal.Decimal('3.5')\n$$");
        assertAnswer(
            "3",
            "CALL xp00()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 3",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XP01 successfully created.",
            "CREATE OR REPLACE PROCEDURE xp01() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport decimal\ndef run(session):\n    "
                + "return decimal.Decimal('2.7')\n$$");
        assertAnswer(
            "2",
            "CALL xp01()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 2",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XP02 successfully created.",
            "CREATE OR REPLACE PROCEDURE xp02() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport decimal\ndef run(session):\n    "
                + "return decimal.Decimal('-2.5')\n$$");
        assertAnswer(
            "-2",
            "CALL xp02()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / -2",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XP03 successfully created.",
            "CREATE OR REPLACE PROCEDURE xp03() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport decimal\ndef run(session):\n    "
                + "return decimal.Decimal('-2.7')\n$$");
        assertAnswer(
            "-2",
            "CALL xp03()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / -2",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XP04 successfully created.",
            "CREATE OR REPLACE PROCEDURE xp04() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport decimal\ndef run(session):\n    "
                + "return decimal.Decimal('0.5')\n$$");
        assertAnswer(
            "0",
            "CALL xp04()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 0",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XP05 successfully created.",
            "CREATE OR REPLACE PROCEDURE xp05() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport decimal\ndef run(session):\n    "
                + "return decimal.Decimal('1.5')\n$$");
        assertAnswer(
            "1",
            "CALL xp05()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 1",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XP06 successfully created.",
            "CREATE OR REPLACE PROCEDURE xp06() RETURNS NUMBER(10,2) LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport decimal\ndef "
                + "run(session):\n    return decimal.Decimal('1.385')\n$$");
        assertAnswer(
            "1.38",
            "CALL xp06()");
        assertAnswer(
            "NUMBER(10,2)[SB8] / 1.38",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XP07 successfully created.",
            "CREATE OR REPLACE PROCEDURE xp07() RETURNS NUMBER(10,2) LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport decimal\ndef "
                + "run(session):\n    return decimal.Decimal('1.375')\n$$");
        assertAnswer(
            "1.38",
            "CALL xp07()");
        assertAnswer(
            "NUMBER(10,2)[SB8] / 1.38",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XP08 successfully created.",
            "CREATE OR REPLACE PROCEDURE xp08() RETURNS NUMBER(10,2) LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport decimal\ndef "
                + "run(session):\n    return decimal.Decimal('1.365')\n$$");
        assertAnswer(
            "1.36",
            "CALL xp08()");
        assertAnswer(
            "NUMBER(10,2)[SB8] / 1.36",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XP09 successfully created.",
            "CREATE OR REPLACE PROCEDURE xp09() RETURNS NUMBER(10,2) LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport decimal\ndef "
                + "run(session):\n    return decimal.Decimal('-1.385')\n$$");
        assertAnswer(
            "-1.38",
            "CALL xp09()");
        assertAnswer(
            "NUMBER(10,2)[SB8] / -1.38",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XP10 successfully created.",
            "CREATE OR REPLACE PROCEDURE xp10() RETURNS NUMBER(10,2) LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport decimal\ndef "
                + "run(session):\n    return decimal.Decimal('123456789.12')\n$$");
        assertAnswer(
            "REFUSED Decimal value cannot be losslessly represented as a Snowflake NUMBER with a precision of 10 "
                + "and a scale of 2 in function XP10 with handler run",
            "CALL xp10()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XF00 successfully created.",
            "CREATE OR REPLACE PROCEDURE xf00() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return 2.7\n$$");
        assertAnswer(
            "2",
            "CALL xf00()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 2",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XF01 successfully created.",
            "CREATE OR REPLACE PROCEDURE xf01() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return 3.5\n$$");
        assertAnswer(
            "3",
            "CALL xf01()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 3",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XF02 successfully created.",
            "CREATE OR REPLACE PROCEDURE xf02() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return -2.7\n$$");
        assertAnswer(
            "-2",
            "CALL xf02()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / -2",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XF03 successfully created.",
            "CREATE OR REPLACE PROCEDURE xf03() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return 1e20\n$$");
        assertAnswer(
            "100000000000000000000",
            "CALL xf03()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 100000000000000000000",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XN4 successfully created.",
            "CREATE OR REPLACE PROCEDURE xn4() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return None\n$$");
        assertAnswer(
            "null",
            "CALL xn4()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 81 | Invalid argument types for function "
                + "'||': (BOOLEAN, VARCHAR(3), VARCHAR(134217728), VARCHAR(3), VARCHAR(134217728))",
            "SELECT ($1 IS NULL) || ' / ' || NVL(IS_NULL_VALUE($1)::VARCHAR, 'null') || ' / ' || NVL(TYPEOF($1), "
                + "'null') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XN5 successfully created.",
            "CREATE OR REPLACE PROCEDURE xn5() RETURNS ARRAY LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return None\n$$");
        assertAnswer(
            "null",
            "CALL xn5()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 81 | Invalid argument types for function "
                + "'||': (BOOLEAN, VARCHAR(3), VARCHAR(134217728), VARCHAR(3), VARCHAR(134217728))",
            "SELECT ($1 IS NULL) || ' / ' || NVL(IS_NULL_VALUE($1)::VARCHAR, 'null') || ' / ' || NVL(TYPEOF($1), "
                + "'null') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XN6 successfully created.",
            "CREATE OR REPLACE PROCEDURE xn6() RETURNS OBJECT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return None\n$$");
        assertAnswer(
            "null",
            "CALL xn6()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 81 | Invalid argument types for function "
                + "'||': (BOOLEAN, VARCHAR(3), VARCHAR(134217728), VARCHAR(3), VARCHAR(134217728))",
            "SELECT ($1 IS NULL) || ' / ' || NVL(IS_NULL_VALUE($1)::VARCHAR, 'null') || ' / ' || NVL(TYPEOF($1), "
                + "'null') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XN7 successfully created.",
            "CREATE OR REPLACE PROCEDURE xn7() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return {'a': "
                + "None}\n$$");
        assertAnswer(
            "{\"a\":null}",
            "CALL xn7()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 81 | Invalid argument types for function "
                + "'||': (BOOLEAN, VARCHAR(3), VARCHAR(134217728), VARCHAR(3), VARCHAR(134217728))",
            "SELECT ($1 IS NULL) || ' / ' || NVL(IS_NULL_VALUE($1)::VARCHAR, 'null') || ' / ' || NVL(TYPEOF($1), "
                + "'null') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XR1 successfully created.",
            "CREATE OR REPLACE PROCEDURE xr1() RETURNS NUMBER(10,2) LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return "
                + "1.38875\n$$");
        assertAnswer(
            "1.39",
            "CALL xr1()");
        assertAnswer(
            "1.39",
            "DECLARE r VARCHAR; BEGIN CALL xr1() INTO :r; RETURN r; END;");
    }

    @Test
    public void whatASemiStructuredAnswerHolds() {
        assertAnswer(
            "Function N6 successfully created.",
            "CREATE OR REPLACE PROCEDURE n6() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return None\n$$");
        assertAnswer(
            "null",
            "CALL n6()");
        assertAnswer(
            "value / NULL_VALUE",
            "SELECT IFF($1 IS NULL, 'sql-null', 'value') || ' / ' || NVL(TYPEOF($1), 'no-type') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function N7 successfully created.",
            "CREATE OR REPLACE PROCEDURE n7() RETURNS ARRAY LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return None\n$$");
        assertAnswer(
            "null",
            "CALL n7()");
        assertAnswer(
            "sql-null / no-type",
            "SELECT IFF($1 IS NULL, 'sql-null', 'value') || ' / ' || NVL(TYPEOF($1), 'no-type') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function N8 successfully created.",
            "CREATE OR REPLACE PROCEDURE n8() RETURNS OBJECT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return None\n$$");
        assertAnswer(
            "null",
            "CALL n8()");
        assertAnswer(
            "sql-null / no-type",
            "SELECT IFF($1 IS NULL, 'sql-null', 'value') || ' / ' || NVL(TYPEOF($1), 'no-type') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function DD successfully created.",
            "CREATE OR REPLACE PROCEDURE dd() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return 1.5\n$$");
        assertAnswer(
            "1.5",
            "CALL dd()");
        assertAnswer(
            "VARIANT[LOB] / 1.5",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function DE successfully created.",
            "CREATE OR REPLACE PROCEDURE de() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return {'f': 2.5, "
                + "'i': 4, 'n': float('nan'), 'm': float('inf')}\n$$");
        assertAnswer(
            "{\"f\":2.500000000000000e+00,\"i\":4,\"m\":Infinity,\"n\":NaN}",
            "CALL de()");
        assertAnswer(
            "VARIANT[LOB] / {\"f\":2.500000000000000e+00,\"i\":4,\"m\":Infinity,\"n\":NaN}",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function DF successfully created.",
            "CREATE OR REPLACE PROCEDURE df() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport datetime\ndef run(session):\n    "
                + "return datetime.datetime(2024, 1, 2, 3, 4, 5)\n$$");
        answer(
            "CALL df()");
        assertAnswer(
            "2024_01_02 03;04;05",
            "SELECT REPLACE(REPLACE($1::VARCHAR, '-', '_'), ':', ';') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function DG successfully created.",
            "CREATE OR REPLACE PROCEDURE dg() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport decimal\ndef run(session):\n    "
                + "return decimal.Decimal('1.50')\n$$");
        assertAnswer(
            "1.50",
            "CALL dg()");
        assertAnswer(
            "VARIANT[LOB] / 1.50",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function DH successfully created.",
            "CREATE OR REPLACE PROCEDURE dh() RETURNS ARRAY LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return [1.5, 2, "
                + "1e21]\n$$");
        assertAnswer(
            "[1.500000000000000e+00,2,1.000000000000000e+21]",
            "CALL dh()");
        assertAnswer(
            "ARRAY[LOB] / [1.500000000000000e+00,2,1.000000000000000e+21]",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function DI successfully created.",
            "CREATE OR REPLACE PROCEDURE di() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport datetime\ndef run(session):\n    "
                + "return datetime.date(2024, 1, 2)\n$$");
        assertAnswer(
            "2024-01-02",
            "CALL di()");
        assertAnswer(
            "2024_01_02",
            "SELECT REPLACE(REPLACE($1::VARCHAR, '-', '_'), ':', ';') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function W3 successfully created.",
            "CREATE OR REPLACE PROCEDURE w3() RETURNS VARCHAR(2) LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return "
                + "'ab'\n$$");
        assertAnswer(
            "ab",
            "CALL w3()");
        assertShape(
            "W3:VARCHAR(2)",
            "CALL w3()");
        assertAnswer(
            "VARCHAR(2)[LOB] / ab",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function W4 successfully created.",
            "CREATE OR REPLACE PROCEDURE w4() RETURNS VARCHAR(2) LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return "
                + "'abc'\n$$");
        assertAnswer(
            "abc",
            "CALL w4()");
        assertShape(
            "W4:VARCHAR(2)",
            "CALL w4()");
        answer(
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P0 successfully created.",
            "CREATE OR REPLACE PROCEDURE p0() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return 2.0\n$$");
        assertAnswer(
            "2.0",
            "CALL p0()");
        assertAnswer(
            "VARIANT[LOB] / 2",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P1 successfully created.",
            "CREATE OR REPLACE PROCEDURE p1() RETURNS ARRAY LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return [2.0, -0.0, "
                + "1e21, 1.5, 1e-7, 123456.789]\n$$");
        assertAnswer(
            "[2.000000000000000e+00,-0.000000000000000e+00,1.000000000000000e+21,1.500000000000000e+00,"
                + "1.000000000000000e-07,1.234567890000000e+05]",
            "CALL p1()");
        assertAnswer(
            "ARRAY[LOB] / [2.000000000000000e+00,-0.000000000000000e+00,1.000000000000000e+21,"
                + "1.500000000000000e+00,1.000000000000000e-07,1.234567890000000e+05]",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P2 successfully created.",
            "CREATE OR REPLACE PROCEDURE p2() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport datetime\ndef run(session):\n    "
                + "return {'d': datetime.datetime(2024, 1, 2, 3, 4, 5, 678000), 't': datetime.time(3, 4, 5), 'day': "
                + "datetime.date(2024, 1, 2)}\n$$");
        answer(
            "CALL p2()");
        assertAnswer(
            "{\"d\";\"2024_01_02 03;04;05.678000\",\"day\";\"2024_01_02\",\"t\";\"03;04;05\"}",
            "SELECT REPLACE(REPLACE($1::VARCHAR, '-', '_'), ':', ';') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P3 successfully created.",
            "CREATE OR REPLACE PROCEDURE p3() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport datetime\ndef run(session):\n    "
                + "return datetime.datetime(2024, 1, 2, 3, 4, 5, 678000)\n$$");
        answer(
            "CALL p3()");
        assertAnswer(
            "2024_01_02 03;04;05.678000",
            "SELECT REPLACE(REPLACE($1::VARCHAR, '-', '_'), ':', ';') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P4 successfully created.",
            "CREATE OR REPLACE PROCEDURE p4() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport datetime\ndef run(session):\n    "
                + "return datetime.datetime(2024, 1, 2, 3, 4, 5, tzinfo=datetime.timezone.utc)\n$$");
        answer(
            "CALL p4()");
        assertAnswer(
            "2024_01_02 03;04;05+00;00",
            "SELECT REPLACE(REPLACE($1::VARCHAR, '-', '_'), ':', ';') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P5 successfully created.",
            "CREATE OR REPLACE PROCEDURE p5() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport datetime\ndef run(session):\n    "
                + "return datetime.time(3, 4, 5)\n$$");
        assertAnswer(
            "03:04:05",
            "CALL p5()");
        assertAnswer(
            "03;04;05",
            "SELECT REPLACE(REPLACE($1::VARCHAR, '-', '_'), ':', ';') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P6 successfully created.",
            "CREATE OR REPLACE PROCEDURE p6() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return "
                + "b'\\xab\\xcd'\n$$");
        assertAnswer(
            "b'\\xab\\xcd'",
            "CALL p6()");
        assertAnswer(
            "VARIANT[LOB] / b'\\xab\\xcd'",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P7 successfully created.",
            "CREATE OR REPLACE PROCEDURE p7() RETURNS ARRAY LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return "
                + "[b'\\xab\\xcd']\n$$");
        assertAnswer(
            "[\"b'\\\\xab\\\\xcd'\"]",
            "CALL p7()");
        assertAnswer(
            "ARRAY[LOB] / [\"b'\\\\xab\\\\xcd'\"]",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P8 successfully created.",
            "CREATE OR REPLACE PROCEDURE p8() RETURNS ARRAY LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\nimport decimal\ndef run(session):\n    "
                + "return [decimal.Decimal('1.50'), decimal.Decimal('2'), decimal.Decimal('1E+3')]\n$$");
        assertAnswer(
            "[\"1.50\",\"2\",\"1E+3\"]",
            "CALL p8()");
        assertAnswer(
            "ARRAY[LOB] / [\"1.50\",\"2\",\"1E+3\"]",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function P9 successfully created.",
            "CREATE OR REPLACE PROCEDURE p9() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return (1, 2)\n$$");
        assertAnswer(
            "[1,2]",
            "CALL p9()");
        assertAnswer(
            "VARIANT[LOB] / [1,2]",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function PA successfully created.",
            "CREATE OR REPLACE PROCEDURE pa() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return {1: 'a', 2.5: "
                + "'b', True: 'c'}\n$$");
        assertAnswer(
            "{\"1\":\"c\",\"2.5\":\"b\"}",
            "CALL pa()");
        assertAnswer(
            "VARIANT[LOB] / {\"1\":\"c\",\"2.5\":\"b\"}",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function PB successfully created.",
            "CREATE OR REPLACE PROCEDURE pb() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return {'a': None, "
                + "'b': [None]}\n$$");
        assertAnswer(
            "{\"a\":null,\"b\":[null]}",
            "CALL pb()");
        assertAnswer(
            "VARIANT[LOB] / {\"a\":null,\"b\":[null]}",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function PC successfully created.",
            "CREATE OR REPLACE PROCEDURE pc() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return {1, 2}\n$$");
        assertAnswer(
            "REFUSED Python Interpreter Error: | TypeError: Object of type set is not serializable in function PC "
                + "with handler run",
            "CALL pc()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function PD successfully created.",
            "CREATE OR REPLACE PROCEDURE pd() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return "
                + "float('nan')\n$$");
        assertAnswer(
            "NaN",
            "CALL pd()");
        assertAnswer(
            "VARIANT[LOB] / NaN",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function PE successfully created.",
            "CREATE OR REPLACE PROCEDURE pe() RETURNS ARRAY LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return [True, 'x', 1, "
                + "None]\n$$");
        assertAnswer(
            "[true,\"x\",1,null]",
            "CALL pe()");
        assertAnswer(
            "ARRAY[LOB] / [true,\"x\",1,null]",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function PF successfully created.",
            "CREATE OR REPLACE PROCEDURE pf() RETURNS BINARY(2) LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES "
                + "= ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return "
                + "b'\\x01\\x02\\x03'\n$$");
        assertAnswer(
            "REFUSED System byte length limit (2) exceeded by string '\u0001\u0002\u0003...' in function PF with "
                + "handler run",
            "CALL pf()");
        assertShape(
            "REFUSED System byte length limit (2) exceeded by string '\u0001\u0002\u0003...' in function PF with "
                + "handler run",
            "CALL pf()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function PG successfully created.",
            "CREATE OR REPLACE PROCEDURE pg() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return object()\n$$");
        assertAnswer(
            "REFUSED Python Interpreter Error: | TypeError: Object of type object is not serializable in function "
                + "PG with handler run",
            "CALL pg()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function PH successfully created.",
            "CREATE OR REPLACE PROCEDURE ph() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return "
                + "float('inf')\n$$");
        assertAnswer(
            "Infinity",
            "CALL ph()");
        assertAnswer(
            "VARIANT[LOB] / inf",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }
}
