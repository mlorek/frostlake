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
package dev.frostlake.procedural;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A CALL converts each argument to its parameter's declared type as the value arrives, before the body runs: the
 * body of a SQL procedure reads the converted value, and SYSTEM$TYPEOF the declared type. A BOOLEAN parameter
 * reads a text that spells one, whatever its case and with blanks around it, and a number - zero is false,
 * anything else true -, and a text that spells none is the CALL's refusal, {@code Boolean value 'maybe' is not
 * recognized}. A NUMBER parameter reads a text and rounds to its scale, half away from zero, and a value past its
 * width is the number's range refusal; a FLOAT parameter reads a text, NaN and the infinities included; a text
 * parameter takes the value's text, its declared width not enforced; a DATE, a TIME and a TIMESTAMP parameter
 * read a text as one. An ARRAY parameter wraps a variant that is no array, an OBJECT parameter refuses one that is
 * no object, and a variant that holds a text or a number converts as that value does - {@code Failed to cast
 * variant value "x" to FIXED} when it does not fit. A named argument, a parameter's default and a block's
 * variant convert alike. The pairs no conversion carries are refused while the CALL compiles. Every answer is
 * live-verified.
 */
public class CallArgumentConversionTest extends MeasuredCellsTestSupport {

    @Test
    public void whatASqlBodyReceivesForEachParameterType() {
        assertAnswer(
            "Function SP_BOOLEAN successfully created.",
            "CREATE OR REPLACE PROCEDURE sp_boolean(a BOOLEAN) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "SYSTEM$TYPEOF(a) || ' / ' || NVL(a::VARCHAR, 'null'); END; $$");
        assertAnswer(
            "BOOLEAN[SB1] / true",
            "CALL sp_boolean(TRUE)");
        assertAnswer(
            "BOOLEAN[SB1] / false",
            "CALL sp_boolean(FALSE)");
        assertAnswer(
            "BOOLEAN[SB1] / false",
            "CALL sp_boolean('off')");
        assertAnswer(
            "BOOLEAN[SB1] / true",
            "CALL sp_boolean('true')");
        assertAnswer(
            "BOOLEAN[SB1] / true",
            "CALL sp_boolean('yes')");
        assertAnswer(
            "REFUSED Boolean value 'maybe' is not recognized",
            "CALL sp_boolean('maybe')");
        assertAnswer(
            "REFUSED Boolean value '' is not recognized",
            "CALL sp_boolean('')");
        assertAnswer(
            "BOOLEAN[SB1] / false",
            "CALL sp_boolean(0)");
        assertAnswer(
            "BOOLEAN[SB1] / true",
            "CALL sp_boolean(1)");
        assertAnswer(
            "BOOLEAN[SB1] / true",
            "CALL sp_boolean(2.5)");
        assertAnswer(
            "BOOLEAN[SB1] / null",
            "CALL sp_boolean(NULL)");
        assertAnswer(
            "Function SP_NUMBER successfully created.",
            "CREATE OR REPLACE PROCEDURE sp_number(a NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "SYSTEM$TYPEOF(a) || ' / ' || NVL(a::VARCHAR, 'null'); END; $$");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 7",
            "CALL sp_number(7)");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 7",
            "CALL sp_number('7')");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 3",
            "CALL sp_number(2.5)");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 3",
            "CALL sp_number('2.5')");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 2",
            "CALL sp_number(2.4)");
        assertAnswer(
            "NUMBER(38,0)[SB16] / -3",
            "CALL sp_number(-2.5)");
        assertAnswer(
            "REFUSED Numeric value 'x' is not recognized",
            "CALL sp_number('x')");
        assertAnswer(
            "REFUSED Numeric value '' is not recognized",
            "CALL sp_number('')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SP_NUMBER': (BOOLEAN)",
            "CALL sp_number(TRUE)");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 100",
            "CALL sp_number(1e2)");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 100",
            "CALL sp_number('1e2')");
        assertAnswer(
            "NUMBER(38,0)[SB16] / null",
            "CALL sp_number(NULL)");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 12345678901234567890123456789012345678",
            "CALL sp_number(12345678901234567890123456789012345678)");
        assertAnswer(
            "Function SP_NUMBER_10_2 successfully created.",
            "CREATE OR REPLACE PROCEDURE sp_number_10_2(a NUMBER(10,2)) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
                + "RETURN SYSTEM$TYPEOF(a) || ' / ' || NVL(a::VARCHAR, 'null'); END; $$");
        assertAnswer(
            "NUMBER(10,2)[SB8] / 1.25",
            "CALL sp_number_10_2(1.25)");
        assertAnswer(
            "NUMBER(10,2)[SB8] / 1.26",
            "CALL sp_number_10_2(1.255)");
        assertAnswer(
            "NUMBER(10,2)[SB8] / 1.25",
            "CALL sp_number_10_2(1.245)");
        assertAnswer(
            "NUMBER(10,2)[SB8] / 1.26",
            "CALL sp_number_10_2('1.255')");
        assertAnswer(
            "REFUSED Number out of representable range: type FIXED[SB8](10,2){nullable}, value 123456789.12",
            "CALL sp_number_10_2(123456789.12)");
        assertAnswer(
            "REFUSED Numeric value 'x' is not recognized",
            "CALL sp_number_10_2('x')");
        assertAnswer(
            "NUMBER(10,2)[SB8] / 7.00",
            "CALL sp_number_10_2(7)");
        assertAnswer(
            "Function SP_NUMBER_3_0 successfully created.",
            "CREATE OR REPLACE PROCEDURE sp_number_3_0(a NUMBER(3,0)) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
                + "RETURN SYSTEM$TYPEOF(a) || ' / ' || NVL(a::VARCHAR, 'null'); END; $$");
        assertAnswer(
            "NUMBER(3,0)[SB2] / 999",
            "CALL sp_number_3_0(999)");
        assertAnswer(
            "REFUSED Number out of representable range: type FIXED[SB2](3,0){nullable}, value 1000",
            "CALL sp_number_3_0(1000)");
        assertAnswer(
            "NUMBER(3,0)[SB2] / 12",
            "CALL sp_number_3_0('12')");
        assertAnswer(
            "NUMBER(3,0)[SB2] / 100",
            "CALL sp_number_3_0(99.5)");
        assertAnswer(
            "REFUSED Number out of representable range: type FIXED[SB2](3,0){nullable}, value 999.5",
            "CALL sp_number_3_0(999.5)");
        assertAnswer(
            "Function SP_FLOAT successfully created.",
            "CREATE OR REPLACE PROCEDURE sp_float(a FLOAT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "SYSTEM$TYPEOF(a) || ' / ' || NVL(a::VARCHAR, 'null'); END; $$");
        assertAnswer(
            "FLOAT[DOUBLE] / 1",
            "CALL sp_float(1)");
        assertAnswer(
            "FLOAT[DOUBLE] / 1.5",
            "CALL sp_float(1.5)");
        assertAnswer(
            "FLOAT[DOUBLE] / 1.5",
            "CALL sp_float('1.5')");
        assertAnswer(
            "REFUSED Numeric value 'x' is not recognized",
            "CALL sp_float('x')");
        assertAnswer(
            "FLOAT[DOUBLE] / NaN",
            "CALL sp_float('NaN')");
        assertAnswer(
            "FLOAT[DOUBLE] / inf",
            "CALL sp_float('inf')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SP_FLOAT': (BOOLEAN)",
            "CALL sp_float(TRUE)");
        assertAnswer(
            "FLOAT[DOUBLE] / null",
            "CALL sp_float(NULL)");
        assertAnswer(
            "Function SP_VARCHAR successfully created.",
            "CREATE OR REPLACE PROCEDURE sp_varchar(a VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "SYSTEM$TYPEOF(a) || ' / ' || NVL(a::VARCHAR, 'null'); END; $$");
        assertAnswer(
            "VARCHAR[LOB] / 42",
            "CALL sp_varchar(42)");
        assertAnswer(
            "VARCHAR[LOB] / 1.5",
            "CALL sp_varchar(1.50)");
        assertAnswer(
            "VARCHAR[LOB] / true",
            "CALL sp_varchar(TRUE)");
        assertAnswer(
            "VARCHAR[LOB] / 2024-01-02",
            "CALL sp_varchar('2024-01-02'::DATE)");
        answer(
            "CALL sp_varchar(TO_TIMESTAMP_NTZ('2024-01-02 03:04:05'))");
        assertAnswer(
            "VARCHAR[LOB] / 03:04:05",
            "CALL sp_varchar(TO_TIME('03:04:05'))");
        assertAnswer(
            "VARCHAR[LOB] / null",
            "CALL sp_varchar(NULL)");
        assertAnswer(
            "VARCHAR[LOB] / {\"a\":1}",
            "CALL sp_varchar(PARSE_JSON('{\"a\":1}'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SP_VARCHAR': (ARRAY)",
            "CALL sp_varchar(ARRAY_CONSTRUCT(1, 2))");
        assertAnswer(
            "VARCHAR[LOB] / 1.5",
            "CALL sp_varchar(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SP_VARCHAR': (BINARY(67108864))",
            "CALL sp_varchar(TO_BINARY('ABCD'))");
        assertAnswer(
            "Function SP_VARCHAR_3 successfully created.",
            "CREATE OR REPLACE PROCEDURE sp_varchar_3(a VARCHAR(3)) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
                + "RETURN SYSTEM$TYPEOF(a) || ' / ' || NVL(a::VARCHAR, 'null'); END; $$");
        assertAnswer(
            "VARCHAR[LOB] / abc",
            "CALL sp_varchar_3('abc')");
        assertAnswer(
            "VARCHAR[LOB] / abcd",
            "CALL sp_varchar_3('abcd')");
        assertAnswer(
            "VARCHAR[LOB] / 1234",
            "CALL sp_varchar_3(1234)");
        assertAnswer(
            "VARCHAR[LOB] / true",
            "CALL sp_varchar_3(TRUE)");
        assertAnswer(
            "Function SP_DATE successfully created.",
            "CREATE OR REPLACE PROCEDURE sp_date(a DATE) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "SYSTEM$TYPEOF(a) || ' / ' || NVL(a::VARCHAR, 'null'); END; $$");
        assertAnswer(
            "DATE[SB4] / 2024-01-31",
            "CALL sp_date('2024-01-31')");
        assertAnswer(
            "DATE[SB4] / 2024-01-31",
            "CALL sp_date('2024-01-31'::DATE)");
        assertAnswer(
            "DATE[SB4] / 2024-01-31",
            "CALL sp_date('2024-01-31 10:11:12'::TIMESTAMP_NTZ)");
        assertAnswer(
            "REFUSED Date 'x' is not recognized",
            "CALL sp_date('x')");
        assertAnswer(
            "REFUSED Date '31/01/2024' is not recognized",
            "CALL sp_date('31/01/2024')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SP_DATE': (NUMBER(8,0))",
            "CALL sp_date(20240131)");
        assertAnswer(
            "DATE[SB4] / 2024-01-31",
            "CALL sp_date('2024-01-31 10:11:12')");
        assertAnswer(
            "DATE[SB4] / null",
            "CALL sp_date(NULL)");
        assertAnswer(
            "Function SP_TIME successfully created.",
            "CREATE OR REPLACE PROCEDURE sp_time(a TIME) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "SYSTEM$TYPEOF(a) || ' / ' || NVL(a::VARCHAR, 'null'); END; $$");
        assertAnswer(
            "TIME(9)[SB8] / 10:11:12",
            "CALL sp_time('10:11:12')");
        assertAnswer(
            "TIME(9)[SB8] / 10:11:00",
            "CALL sp_time('10:11')");
        assertAnswer(
            "REFUSED Time 'x' is not recognized",
            "CALL sp_time('x')");
        assertAnswer(
            "TIME(9)[SB8] / 10:11:12",
            "CALL sp_time('2024-01-31 10:11:12'::TIMESTAMP_NTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SP_TIME': (NUMBER(5,0))",
            "CALL sp_time(36672)");
        assertAnswer(
            "Function SP_TIMESTAMP_NTZ successfully created.",
            "CREATE OR REPLACE PROCEDURE sp_timestamp_ntz(a TIMESTAMP_NTZ) RETURNS VARCHAR LANGUAGE SQL AS $$ "
                + "BEGIN RETURN SYSTEM$TYPEOF(a) || ' / ' || NVL(a::VARCHAR, 'null'); END; $$");
        answer(
            "CALL sp_timestamp_ntz('2024-01-31 10:11:12')");
        answer(
            "CALL sp_timestamp_ntz('2024-01-31')");
        answer(
            "CALL sp_timestamp_ntz('2024-01-31'::DATE)");
        assertAnswer(
            "REFUSED Timestamp 'x' is not recognized",
            "CALL sp_timestamp_ntz('x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SP_TIMESTAMP_NTZ': (NUMBER(10,0))",
            "CALL sp_timestamp_ntz(1706695872)");
        answer(
            "CALL sp_timestamp_ntz('2024-01-31 10:11:12 +02:00')");
        assertAnswer(
            "Function SP_VARIANT successfully created.",
            "CREATE OR REPLACE PROCEDURE sp_variant(a VARIANT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "SYSTEM$TYPEOF(a) || ' / ' || NVL(a::VARCHAR, 'null'); END; $$");
        assertAnswer(
            "VARIANT[LOB] / 1",
            "CALL sp_variant(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SP_VARIANT': (VARCHAR(1))",
            "CALL sp_variant('a')");
        assertAnswer(
            "VARIANT[LOB] / 1.5",
            "CALL sp_variant(1.5)");
        assertAnswer(
            "VARIANT[LOB] / true",
            "CALL sp_variant(TRUE)");
        assertAnswer(
            "VARIANT[LOB] / {\"a\":1}",
            "CALL sp_variant(PARSE_JSON('{\"a\":1}'))");
        assertAnswer(
            "VARIANT[LOB] / [1,2]",
            "CALL sp_variant(ARRAY_CONSTRUCT(1, 2))");
        assertAnswer(
            "VARIANT[LOB] / null",
            "CALL sp_variant(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SP_VARIANT': (DATE)",
            "CALL sp_variant('2024-01-31'::DATE)");
        assertAnswer(
            "Function SP_ARRAY successfully created.",
            "CREATE OR REPLACE PROCEDURE sp_array(a ARRAY) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "SYSTEM$TYPEOF(a) || ' / ' || NVL(a::VARCHAR, 'null'); END; $$");
        assertAnswer(
            "ARRAY[LOB] / [1,2]",
            "CALL sp_array(ARRAY_CONSTRUCT(1, 2))");
        assertAnswer(
            "ARRAY[LOB] / [1,2]",
            "CALL sp_array(PARSE_JSON('[1, 2]'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SP_ARRAY': (VARCHAR(6))",
            "CALL sp_array('[1, 2]')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SP_ARRAY': (NUMBER(1,0))",
            "CALL sp_array(1)");
        assertAnswer(
            "ARRAY[LOB] / [{\"a\":1}]",
            "CALL sp_array(PARSE_JSON('{\"a\":1}'))");
        assertAnswer(
            "ARRAY[LOB] / null",
            "CALL sp_array(NULL)");
        assertAnswer(
            "Function SP_OBJECT successfully created.",
            "CREATE OR REPLACE PROCEDURE sp_object(a OBJECT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "SYSTEM$TYPEOF(a) || ' / ' || NVL(a::VARCHAR, 'null'); END; $$");
        assertAnswer(
            "OBJECT[LOB] / {\"a\":1}",
            "CALL sp_object(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "OBJECT[LOB] / {\"a\":1}",
            "CALL sp_object(PARSE_JSON('{\"a\":1}'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SP_OBJECT': (VARCHAR(1))",
            "CALL sp_object('x')");
        assertAnswer(
            "REFUSED Failed to cast variant value [1] to OBJECT",
            "CALL sp_object(PARSE_JSON('[1]'))");
        assertAnswer(
            "OBJECT[LOB] / null",
            "CALL sp_object(NULL)");
        assertAnswer(
            "Function SP_BINARY successfully created.",
            "CREATE OR REPLACE PROCEDURE sp_binary(a BINARY) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "SYSTEM$TYPEOF(a) || ' / ' || NVL(a::VARCHAR, 'null'); END; $$");
        answer(
            "CALL sp_binary(TO_BINARY('ABCD'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SP_BINARY': (VARCHAR(4))",
            "CALL sp_binary('ABCD')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SP_BINARY': (VARCHAR(2))",
            "CALL sp_binary('zz')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SP_BINARY': (NUMBER(1,0))",
            "CALL sp_binary(1)");
        assertAnswer(
            "Function PB_IFF successfully created.",
            "CREATE OR REPLACE PROCEDURE pb_iff(a BOOLEAN) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN IFF(a, "
                + "'T', 'F'); END; $$");
        assertAnswer(
            "F",
            "CALL pb_iff('off')");
        assertAnswer(
            "REFUSED Boolean value 'maybe' is not recognized",
            "CALL pb_iff('maybe')");
        assertAnswer(
            "F",
            "CALL pb_iff(0)");
        assertAnswer(
            "F",
            "CALL pb_iff(NULL)");
        assertAnswer(
            "Function PB_IF successfully created.",
            "CREATE OR REPLACE PROCEDURE pb_if(a BOOLEAN) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF (a) THEN "
                + "RETURN 'T'; END IF; RETURN 'F'; END; $$");
        assertAnswer(
            "F",
            "CALL pb_if('off')");
        assertAnswer(
            "T",
            "CALL pb_if('true')");
        assertAnswer(
            "T",
            "CALL pb_if(1)");
        assertAnswer(
            "Function PN_PLUS successfully created.",
            "CREATE OR REPLACE PROCEDURE pn_plus(a NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN a + 1; "
                + "END; $$");
        assertAnswer(
            "8",
            "CALL pn_plus('7')");
        assertAnswer(
            "4",
            "CALL pn_plus(2.5)");
        assertAnswer(
            "Function PD_ADD successfully created.",
            "CREATE OR REPLACE PROCEDURE pd_add(a DATE) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "DATEADD(day, 1, a)::VARCHAR; END; $$");
        assertAnswer(
            "2024-02-01",
            "CALL pd_add('2024-01-31')");
        assertAnswer(
            "Function PB_NAMED successfully created.",
            "CREATE OR REPLACE PROCEDURE pb_named(a BOOLEAN, b NUMBER DEFAULT '7') RETURNS VARCHAR LANGUAGE SQL "
                + "AS $$ BEGIN RETURN SYSTEM$TYPEOF(a) || ' / ' || a::VARCHAR || ' / ' || SYSTEM$TYPEOF(b) || ' / ' "
                + "|| b::VARCHAR; END; $$");
        assertAnswer(
            "BOOLEAN[SB1] / false / NUMBER(38,0)[SB16] / 7",
            "CALL pb_named(a => 'off')");
        assertAnswer(
            "BOOLEAN[SB1] / true / NUMBER(38,0)[SB16] / 3",
            "CALL pb_named(b => '2.5', a => 1)");
        assertAnswer(
            "BOOLEAN[SB1] / false",
            "DECLARE t VARCHAR DEFAULT 'off'; r VARCHAR; BEGIN CALL sp_boolean(:t) INTO :r; RETURN r; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Boolean value "
                + "'maybe' is not recognized",
            "DECLARE t VARCHAR DEFAULT 'maybe'; r VARCHAR; BEGIN CALL sp_boolean(:t) INTO :r; RETURN r; END;");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 7",
            "DECLARE t VARCHAR DEFAULT '7'; r VARCHAR; BEGIN CALL sp_number(:t) INTO :r; RETURN r; END;");
    }

    @Test
    public void theTextsABooleanReadsAndTheVariantsThatConvert() {
        assertAnswer(
            "Function SB successfully created.",
            "CREATE OR REPLACE PROCEDURE sb(a BOOLEAN) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "NVL(a::VARCHAR, 'null'); END; $$");
        assertAnswer(
            "true",
            "CALL sb('T')");
        assertAnswer(
            "false",
            "CALL sb('F')");
        assertAnswer(
            "true",
            "CALL sb('Y')");
        assertAnswer(
            "false",
            "CALL sb('N')");
        assertAnswer(
            "true",
            "CALL sb('ON')");
        assertAnswer(
            "false",
            "CALL sb('OFF')");
        assertAnswer(
            "true",
            "CALL sb('1')");
        assertAnswer(
            "false",
            "CALL sb('0')");
        assertAnswer(
            "true",
            "CALL sb('yes')");
        assertAnswer(
            "false",
            "CALL sb('no')");
        assertAnswer(
            "true",
            "CALL sb('tRuE')");
        assertAnswer(
            "true",
            "CALL sb(' true')");
        assertAnswer(
            "true",
            "CALL sb('true ')");
        assertAnswer(
            "REFUSED Boolean value '2' is not recognized",
            "CALL sb('2')");
        assertAnswer(
            "true",
            "CALL sb('t')");
        assertAnswer(
            "false",
            "CALL sb('f')");
        assertAnswer(
            "true",
            "CALL sb('y')");
        assertAnswer(
            "false",
            "CALL sb('n')");
        assertAnswer(
            "true",
            "CALL sb('on')");
        assertAnswer(
            "REFUSED Boolean value '-1' is not recognized",
            "CALL sb('-1')");
        assertAnswer(
            "REFUSED Boolean value '0.0' is not recognized",
            "CALL sb('0.0')");
        assertAnswer(
            "false",
            "CALL sb(0.0)");
        assertAnswer(
            "true",
            "CALL sb(-1)");
        assertAnswer(
            "true",
            "CALL sb(0.4)");
        assertAnswer(
            "true",
            "CALL sb(1e0)");
        assertAnswer(
            "true",
            "CALL sb(1.5::FLOAT)");
        assertAnswer(
            "false",
            "CALL sb(0::FLOAT)");
        assertAnswer(
            "true",
            "CALL sb('NaN'::FLOAT)");
        assertAnswer(
            "true",
            "CALL sb(PARSE_JSON('true'))");
        assertAnswer(
            "false",
            "CALL sb(PARSE_JSON('\"off\"'))");
        assertAnswer(
            "REFUSED Failed to cast variant value 0 to BOOLEAN",
            "CALL sb(PARSE_JSON('0'))");
        assertAnswer(
            "REFUSED Failed to cast variant value \"maybe\" to BOOLEAN",
            "CALL sb(TO_VARIANT('maybe'))");
        assertAnswer(
            "Function SA successfully created.",
            "CREATE OR REPLACE PROCEDURE sa(a ARRAY) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "NVL(a::VARCHAR, 'null'); END; $$");
        assertAnswer(
            "[1]",
            "CALL sa(PARSE_JSON('1'))");
        assertAnswer(
            "[\"a\"]",
            "CALL sa(PARSE_JSON('\"a\"'))");
        assertAnswer(
            "null",
            "CALL sa(PARSE_JSON('null'))");
        assertAnswer(
            "[1.5]",
            "CALL sa(TO_VARIANT(1.5))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SA': (OBJECT)",
            "CALL sa(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "Function SO successfully created.",
            "CREATE OR REPLACE PROCEDURE so(a OBJECT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "NVL(a::VARCHAR, 'null'); END; $$");
        assertAnswer(
            "REFUSED Failed to cast variant value 1 to OBJECT",
            "CALL so(PARSE_JSON('1'))");
        assertAnswer(
            "null",
            "CALL so(PARSE_JSON('null'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SO': (ARRAY)",
            "CALL so(ARRAY_CONSTRUCT(1))");
        assertAnswer(
            "Function SV successfully created.",
            "CREATE OR REPLACE PROCEDURE sv(a VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "SYSTEM$TYPEOF(a) || ' / ' || NVL(a, 'null'); END; $$");
        assertAnswer(
            "VARCHAR[LOB] / a",
            "CALL sv(PARSE_JSON('\"a\"'))");
        assertAnswer(
            "VARCHAR[LOB] / 1.5",
            "CALL sv(PARSE_JSON('1.50'))");
        assertAnswer(
            "VARCHAR[LOB] / [1,2]",
            "CALL sv(PARSE_JSON('[1, 2]'))");
        assertAnswer(
            "VARCHAR[LOB] / null",
            "CALL sv(PARSE_JSON('null'))");
        assertAnswer(
            "VARCHAR[LOB] / 1.5",
            "CALL sv(1.50)");
        assertAnswer(
            "VARCHAR[LOB] / 1.5",
            "CALL sv(1.5e0)");
        assertAnswer(
            "VARCHAR[LOB] / 100",
            "CALL sv(100::FLOAT)");
        assertAnswer(
            "VARCHAR[LOB] / 0.333333",
            "CALL sv(1/3)");
        assertAnswer(
            "Function SN successfully created.",
            "CREATE OR REPLACE PROCEDURE sn(a NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "SYSTEM$TYPEOF(a) || ' / ' || NVL(a::VARCHAR, 'null'); END; $$");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 7",
            "CALL sn(PARSE_JSON('7'))");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 7",
            "CALL sn(PARSE_JSON('\"7\"'))");
        assertAnswer(
            "REFUSED Failed to cast variant value \"x\" to FIXED",
            "CALL sn(PARSE_JSON('\"x\"'))");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 3",
            "CALL sn(PARSE_JSON('2.5'))");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 3",
            "CALL sn(2.5::FLOAT)");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 7",
            "CALL sn(' 7 ')");
        assertAnswer(
            "REFUSED Numeric value '1,000' is not recognized",
            "CALL sn('1,000')");
        answer(
            "CALL sn('0x10')");
        assertAnswer(
            "Function SF successfully created.",
            "CREATE OR REPLACE PROCEDURE sf(a FLOAT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "SYSTEM$TYPEOF(a) || ' / ' || NVL(a::VARCHAR, 'null'); END; $$");
        assertAnswer(
            "FLOAT[DOUBLE] / 1.5",
            "CALL sf(PARSE_JSON('1.5'))");
        assertAnswer(
            "FLOAT[DOUBLE] / 1.5",
            "CALL sf(PARSE_JSON('\"1.5\"'))");
        assertAnswer(
            "FLOAT[DOUBLE] / 0.333333",
            "CALL sf(1/3)");
        assertAnswer(
            "FLOAT[DOUBLE] / -inf",
            "CALL sf('-inf')");
        assertAnswer(
            "FLOAT[DOUBLE] / inf",
            "CALL sf('Infinity')");
        assertAnswer(
            "Function SD successfully created.",
            "CREATE OR REPLACE PROCEDURE sd(a DATE) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "SYSTEM$TYPEOF(a) || ' / ' || NVL(a::VARCHAR, 'null'); END; $$");
        assertAnswer(
            "DATE[SB4] / 2024-01-31",
            "CALL sd(PARSE_JSON('\"2024-01-31\"'))");
        assertAnswer(
            "DATE[SB4] / 2024-01-31",
            "CALL sd('2024-01-31 10:11:12 +02:00'::TIMESTAMP_TZ)");
        assertAnswer(
            "DATE[SB4] / 2024-01-05",
            "CALL sd('2024-1-5')");
        assertAnswer(
            "DATE[SB4] / 2024-01-01",
            "CALL sd('01-JAN-2024')");
    }
}
