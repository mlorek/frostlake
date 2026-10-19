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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * One reading per conversion of a text argument, whatever function reads it (live-verified): BOOLAND, BOOLOR,
 * BOOLXOR and BOOLNOT read a text or a VARIANT as a NUMBER, CONDITIONAL_TRUE_EVENT and the boolean aggregates as
 * TO_BOOLEAN reads it, and a FLOAT is true when not zero, NaN included; a position, an occurrence, an index, a
 * count or a code point is the nearest whole number to what it gives, a text's included, where AS_DECIMAL and
 * TIME_SLICE refuse a fraction written as one; SUM, AVG, SKEW, RATIO_TO_REPORT, DIV0 over two texts and a
 * comparison with a FLOAT read a text as a cast to FLOAT reads it, where STDDEV and KURTOSIS read it as a decimal;
 * and a text, a VARIANT or a bare NULL beside an interval is refused at the operator.
 */
public class TextArgumentReadingTest extends MeasuredCellsTestSupport {

    @Test
    public void aBooleanArgumentIsReadAsItsFunctionReadsIt() {
        assertAnswer("REFUSED Numeric value 'no' is not recognized", "SELECT BOOLAND('no', 1)");
        assertAnswer("REFUSED Numeric value 'off' is not recognized", "SELECT BOOLOR('off', 0)");
        assertAnswer("REFUSED Numeric value 'abc' is not recognized", "SELECT BOOLXOR('abc', 0)");
        assertAnswer("REFUSED Numeric value 'no' is not recognized", "SELECT BOOLNOT('no')");
        assertAnswer("REFUSED Numeric value 'yes' is not recognized", "SELECT BOOLAND('yes', 'on')");
        assertAnswer("REFUSED Numeric value 'true' is not recognized", "SELECT BOOLAND(' true ', 1)");
        assertAnswer("true", "SELECT BOOLAND('1.5', 1)");
        assertAnswer("false", "SELECT BOOLAND('0', 1)");
        assertAnswer("false", "SELECT BOOLAND(0.0, 1)");
        assertAnswer("REFUSED Numeric value 't' is not recognized", "SELECT BOOLNOT('t')");
        assertAnswer("REFUSED Boolean value 'abc' is not recognized", "SELECT TO_BOOLEAN('abc')");
        assertAnswer("1", "SELECT CONDITIONAL_TRUE_EVENT(x) OVER (ORDER BY 1) FROM (SELECT 'yes' x)");
        assertAnswer("0", "SELECT CONDITIONAL_TRUE_EVENT(x) OVER (ORDER BY 1) FROM (SELECT 'no' x)");
        assertAnswer("REFUSED Boolean value 'abc' is not recognized",
            "SELECT CONDITIONAL_TRUE_EVENT(x) OVER (ORDER BY 1) FROM (SELECT 'abc' x)");
        assertAnswer("0", "SELECT CONDITIONAL_CHANGE_EVENT(x) OVER (ORDER BY 1) FROM (SELECT 'on' x)");
        assertAnswer("false", "SELECT NOT ('nan'::float)");
        assertAnswer("true", "SELECT 'nan'::float AND TRUE");
        assertAnswer("true", "SELECT BOOLOR_AGG(v) FROM (SELECT PARSE_JSON('\"true\"') v)");
        assertAnswer("true", "SELECT BOOLAND_AGG(v) FROM (SELECT PARSE_JSON('\"true\"') v)");
        assertAnswer("true", "SELECT BOOLXOR_AGG(v) FROM (SELECT PARSE_JSON('\"yes\"') v)");
        assertAnswer("true", "SELECT TO_BOOLEAN(PARSE_JSON('\"true\"'))");
        assertAnswer("true", "SELECT BOOLOR_AGG(v) FROM (SELECT 'yes' v)");
        assertAnswer("REFUSED Boolean value 'abc' is not recognized", "SELECT BOOLAND_AGG(v) FROM (SELECT 'abc' v)");
        assertAnswer("REFUSED Boolean value 'maybe' is not recognized", "SELECT TRUE UNION SELECT 'maybe'");
        assertAnswer("maybe ;; true", "SELECT 'maybe' UNION SELECT TRUE");
        assertAnswer("true ;; true", "SELECT * FROM (SELECT TRUE UNION ALL SELECT 'yes') ORDER BY 1");
        answer("SELECT * FROM (SELECT 'no' UNION ALL SELECT FALSE) ORDER BY 1");
        assertAnswer("<a>1</a>", "SELECT PARSE_XML('<a>1</a>', 'TRUE')");
        assertAnswer("<a>1</a>", "SELECT PARSE_XML('<a>1</a>', 'yes')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFF': (VARCHAR(3), NUMBER(1,0), NUMBER(1,0))",
            "SELECT IFF('yes', 1, 2)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFF': (VARCHAR(3), NUMBER(1,0), NUMBER(1,0))",
            "SELECT IFF('abc', 1, 2)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFF': (VARCHAR(3), NUMBER(1,0), NUMBER(1,0))",
            "SELECT COUNT_IF('yes')");
    }

    @Test
    public void aWholeNumberArgumentIsTheNearestWholeNumber() {
        assertAnswer("b", "SELECT REGEXP_SUBSTR('ab', '.', 1.6)");
        assertAnswer("2", "SELECT REGEXP_INSTR('ab', '.', 1.6)");
        assertAnswer("[\"b\"]", "SELECT REGEXP_SUBSTR_ALL('ab', '.', 1.6)");
        assertAnswer("b", "SELECT REGEXP_SUBSTR('ab', '.', 1, 1.6)");
        assertAnswer("2", "SELECT REGEXP_INSTR('ab', '.', 1, 1.6)");
        assertAnswer("1", "SELECT REGEXP_COUNT('abab', 'a', 1.6)");
        assertAnswer("abxb", "SELECT REGEXP_REPLACE('abab', 'a', 'x', 1.6)");
        assertAnswer("abxb", "SELECT REGEXP_REPLACE('abab', 'a', 'x', 1, 1.6)");
        assertAnswer("[0,1,2]", "SELECT ARRAY_GENERATE_RANGE(0, 2.6)");
        assertAnswer("[0,1,2]", "SELECT ARRAY_GENERATE_RANGE(0, '2.6')");
        assertAnswer("[2,3]", "SELECT ARRAY_SLICE([1, 2, 3, 4], 0.6, 2.6)");
        assertAnswer("[1,9,2]", "SELECT ARRAY_INSERT([1, 2], 0.6, 9)");
        assertAnswer("[1,3]", "SELECT ARRAY_REMOVE_AT([1, 2, 3], 0.6)");
        assertAnswer("B", "SELECT CHAR(65.6)");
        assertAnswer("1", "SELECT GETBIT(2, 0.6)");
        assertAnswer("<b>2</b>", "SELECT XMLGET(PARSE_XML('<a><b>1</b><b>2</b></a>'), 'b', 0.6)");
        assertAnswer("2", "SELECT EDITDISTANCE('abc', 'xyz', 1.6)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [NUMBER(2,1)] for parameter 'slice-length'",
            "SELECT TIME_SLICE('2024-01-01 10:07:00'::TIMESTAMP_NTZ, 5.6, 'MINUTE')");
        assertAnswer("3", "SELECT LENGTH(RANDSTR(2.6, 1))");
        assertAnswer("REFUSED SQL compilation error: | argument 3 to function AS_DECIMAL needs to be an integer, "
                + "found: '1.6'",
            "SELECT AS_DECIMAL(TO_VARIANT(1.256), 10, 1.6)");
        assertAnswer("ab", "SELECT LEFT('abc', 1.6)");
        assertAnswer("[1,9,2]", "SELECT ARRAY_INSERT([1, 2], '1', 9)");
        assertAnswer("B", "SELECT CHAR('66')");
    }

    @Test
    public void aNumberTextIsReadByItsFamily() {
        assertAnswer("1.0", "SELECT '1d'::FLOAT");
        assertAnswer("2.0", "SELECT TO_DOUBLE('2f')");
        assertAnswer("true", "SELECT 1.5::FLOAT = '1.5d'");
        assertAnswer("1.0", "SELECT AVG(v) FROM (SELECT '1d' v)");
        assertAnswer("0.5", "SELECT DIV0('1d', '2f')");
        assertAnswer("REFUSED Numeric value 'x' is not recognized", "SELECT KURTOSIS(v) FROM (SELECT 'x' v)");
        assertAnswer("null", "SELECT SKEW(v) FROM (SELECT ' 1' v)");
        assertAnswer("REFUSED Numeric value 'x' is not recognized", "SELECT VARIANCE(v) FROM (SELECT 'x' v)");
        assertAnswer("null", "SELECT VARIANCE(v) FROM (SELECT ' 1' v)");
        assertAnswer("null", "SELECT KURTOSIS(v) FROM (SELECT 'inf'::FLOAT v)");
        assertAnswer("16.0", "SELECT '0x10'::FLOAT");
        assertAnswer("1.0", "SELECT RATIO_TO_REPORT('0x10') OVER ()");
        assertAnswer("16.0", "SELECT AVG(v) FROM (SELECT '0x10' v)");
        assertAnswer("1.0", "SELECT ' 1'::FLOAT");
        assertAnswer("REFUSED Numeric value '0x10' is not recognized", "SELECT TO_NUMBER('0x10')");
        assertAnswer("REFUSED Numeric value 'x' is not recognized", "SELECT RATIO_TO_REPORT('x') OVER ()");
        assertAnswer("1.0", "SELECT RATIO_TO_REPORT(v) OVER () FROM (SELECT ' 2' v)");
        assertAnswer("REFUSED Numeric value 'x' is not recognized", "SELECT SUM(v) OVER () FROM (SELECT 'x' v)");
        assertAnswer("100.0|0.5|5.0", "SELECT '1e2'::FLOAT, '.5'::FLOAT, '5.'::FLOAT");
        assertAnswer("1.0|-0.0", "SELECT '+1'::FLOAT, '-0'::FLOAT");
    }

    @Test
    public void aTextBesideAnIntervalIsRefused() {
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for "
                + "function '+': (VARCHAR(10), INTERVAL)",
            "SELECT '2024-01-02' + INTERVAL '1 day'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for "
                + "function '+': (VARCHAR(10), INTERVAL)",
            "SELECT '01/02/2024' + INTERVAL '1 day'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 18 | Invalid argument types for "
                + "function '+': (VARCHAR(8), INTERVAL)",
            "SELECT '2024-1-2' + INTERVAL '1 day'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 34 | Invalid argument types for "
                + "function '+': (VARCHAR(10), INTERVAL)",
            "SELECT SYSTEM$TYPEOF('2024-01-02' + INTERVAL '1 day')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 29 | Invalid argument types for "
                + "function '+': (VARCHAR(19), INTERVAL)",
            "SELECT '2024-01-02 10:00:00' + INTERVAL '1 hour'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 43 | Invalid argument types for "
                + "function '+': (VARCHAR(19), INTERVAL)",
            "SELECT SYSTEM$TYPEOF('2024-01-02 10:00:00' + INTERVAL '1 hour')");
        assertAnswer("true", "SELECT '2024-01-02'::DATE = '01/02/2024'");
        assertAnswer("2024-01-03T00:00", "SELECT DATEADD(day, 1, '01/02/2024')");
        assertAnswer("REFUSED SQL compilation error: | Function DATE_TRUNC does not support VARCHAR(8) argument type",
            "SELECT DATE_TRUNC('month', '2024-1-5')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for "
                + "function '-': (VARCHAR(10), INTERVAL)",
            "SELECT '2024-01-02' - INTERVAL '1 day'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 24 | Invalid argument types for "
                + "function '+': (INTERVAL, VARCHAR(10))",
            "SELECT INTERVAL '1 day' + '2024-01-02'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 18 | Invalid argument types for "
                + "function '+': (VARCHAR(8), INTERVAL)",
            "SELECT '10:00:00' + INTERVAL '1 hour'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 29 | Invalid argument types for "
                + "function '+': (VARCHAR(19), INTERVAL)",
            "SELECT '2024-01-02T10:00:00' + INTERVAL '1 hour'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 13 | Invalid argument types for "
                + "function '+': (VARCHAR(3), INTERVAL)",
            "SELECT 'abc' + INTERVAL '1 day'");
    }

    @Test
    public void theEdgesOfEachReading() {
        engine.execute("CREATE OR REPLACE TABLE pt413 (a INT)");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [CAST(' true' AS BOOLEAN)] for function '2', "
                + "parameter IMPORT_DDL: constant arguments expected",
            "SELECT GET_DDL('TABLE', 'pt413', ' true')");
        assertAnswer("create or replace TABLE TEST_DB.TEST_SCHEMA.PT413 ( | 	A NUMBER(38,0) | );",
            "SELECT GET_DDL('TABLE', 'pt413', 'yes')");
        assertAnswer("1.0", "SELECT SUM(v) FROM (SELECT '1d' v)");
        assertAnswer("16.0", "SELECT SUM(v) FROM (SELECT '0x10' v)");
        assertAnswer("REFUSED Numeric value '1d' is not recognized", "SELECT 1 = '1d'");
        assertAnswer("true", "SELECT 1.5::FLOAT < '0x10'");
        assertAnswer("REFUSED Numeric value '0x2' is not recognized", "SELECT DIV0(1, '0x2')");
        assertAnswer("true", "SELECT BOOLAND(PARSE_JSON('\"1\"'), 1)");
        assertAnswer("true", "SELECT BOOLAND(PARSE_JSON('true'), 1)");
        assertAnswer("REFUSED Failed to cast variant value \"yes\" to FIXED",
            "SELECT BOOLAND(PARSE_JSON('\"yes\"'), 1)");
        assertAnswer("REFUSED Numeric value 'nan' is not recognized", "SELECT BOOLAND('nan', 1)");
        assertAnswer("REFUSED Numeric value '0x1' is not recognized", "SELECT BOOLAND('1e2', 1), BOOLAND('0x1', 1)");
        assertAnswer("REFUSED Numeric value '' is not recognized", "SELECT BOOLAND('', 1)");
        assertAnswer("1", "SELECT CONDITIONAL_TRUE_EVENT(x) OVER (ORDER BY 1) FROM (SELECT 2.5 x)");
        assertAnswer("1", "SELECT CONDITIONAL_TRUE_EVENT(x) OVER (ORDER BY 1) FROM (SELECT ' on ' x)");
        assertAnswer("1", "SELECT CONDITIONAL_TRUE_EVENT(x) OVER (ORDER BY 1) FROM (SELECT PARSE_JSON('\"yes\"') x)");
        assertAnswer("REFUSED Boolean value 'nan' is not recognized", "SELECT NOT 'nan'");
        assertAnswer("REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'slice-length'",
            "SELECT TIME_SLICE('2024-01-01 10:07:00'::TIMESTAMP_NTZ, '5', 'MINUTE')");
        assertAnswer("2024-01-01T10:05", "SELECT TIME_SLICE('2024-01-01 10:07:00'::TIMESTAMP_NTZ, 5.0, 'MINUTE')");
        assertAnswer("1.26", "SELECT AS_DECIMAL(TO_VARIANT(1.256), 10, 2.0)");
        assertAnswer("REFUSED SQL compilation error: | argument 2 to function AS_DECIMAL needs to be an integer, "
                + "found: '10.6'",
            "SELECT AS_DECIMAL(TO_VARIANT(1.256), 10.6, 2)");
        assertAnswer("C", "SELECT CHAR('66.6')");
        assertAnswer("B", "SELECT CHAR(' 66')");
        assertAnswer("[0,2]", "SELECT ARRAY_GENERATE_RANGE(0, 3, 1.6)");
        assertAnswer("1", "SELECT GETBIT(2, '1')");
        assertAnswer("<b>2</b>", "SELECT XMLGET(PARSE_XML('<a><b>1</b><b>2</b></a>'), 'b', '1')");
        assertAnswer("3", "SELECT REGEXP_INSTR('abab', 'a', '2')");
        assertAnswer("REFUSED Numeric value '1d' is not recognized",
            "SELECT KURTOSIS(v) FROM (SELECT '1d' v UNION ALL SELECT '2' UNION ALL SELECT '4' UNION ALL SELECT '8')");
        answer("SELECT SKEW(v) FROM (SELECT '0x1' v UNION ALL SELECT '2' UNION ALL SELECT '4')");
        assertAnswer("REFUSED Numeric value '1d' is not recognized",
            "SELECT STDDEV(v) FROM (SELECT '1d' v UNION ALL SELECT '3')");
        assertAnswer("2024-01-03", "SELECT '2024-01-02'::DATE + INTERVAL '1 day'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 34 | Invalid argument types for "
                + "function '+': (VARIANT, INTERVAL)",
            "SELECT PARSE_JSON('\"2024-01-02\"') + INTERVAL '1 day'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 12 | Invalid argument types for "
                + "function '+': (NULL, INTERVAL)",
            "SELECT NULL + INTERVAL '1 day'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 32 | Invalid argument types for "
                + "function '+': (VARCHAR(134217728), INTERVAL)",
            "SELECT TO_VARCHAR('2024-01-02') + INTERVAL '1 day'");
    }
}
