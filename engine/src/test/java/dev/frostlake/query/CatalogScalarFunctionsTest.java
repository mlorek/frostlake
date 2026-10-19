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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * The scalar functions the catalog had no entry for, as the account answers them: the date parts and shifts
 * (ADD_MONTHS keeps a month's end, DAYNAME and MONTHNAME are three letters, DAYOFWEEK counts from Sunday as 0),
 * the hyperbolic functions, the hex digests and the two halves of MD5 as numbers, the context functions, and
 * TRY_TO_BOOLEAN, which reads the words TO_BOOLEAN reads. A SHA2 digest size is 224, 256, 384 or 512 after
 * rounding, judged before the message is read; CONVERT_TIMEZONE knows a zone by its own name in the zone
 * database and nothing else; NORMAL takes two numbers and a generator, judges the mean and the deviation left to
 * right, and answers the same number for the same generator value. Every answer is live-verified.
 */
public class CatalogScalarFunctionsTest extends MeasuredCellsTestSupport {

    @Test
    public void datePartsAndShifts() {
        assertAnswer(
            "2024-02-29",
            "SELECT ADD_MONTHS('2024-01-31'::DATE, 1)");
        assertAnswer(
            "DATE[SB4]",
            "SELECT SYSTEM$TYPEOF(ADD_MONTHS('2024-01-31'::DATE, 1))");
        assertAnswer(
            "2025-02-28",
            "SELECT ADD_MONTHS('2024-02-29'::DATE, 12)");
        assertAnswer(
            "DATE[SB4]",
            "SELECT SYSTEM$TYPEOF(ADD_MONTHS('2024-02-29'::DATE, 12))");
        assertAnswer(
            "TIMESTAMP_NTZ(9)[SB16]",
            "SELECT SYSTEM$TYPEOF(ADD_MONTHS('2024-01-31 10:20:30.123'::TIMESTAMP_NTZ, -2))");
        assertAnswer(
            "2024-03-31",
            "SELECT ADD_MONTHS('2024-01-31'::DATE, 1.5)");
        assertAnswer(
            "DATE[SB4]",
            "SELECT SYSTEM$TYPEOF(ADD_MONTHS('2024-01-31'::DATE, 1.5))");
        assertAnswer(
            "TIMESTAMP_TZ(9)[SB16]",
            "SELECT SYSTEM$TYPEOF(ADD_MONTHS('2024-01-31 10:00:00 +01:00'::TIMESTAMP_TZ, 1))");
        assertAnswer(
            "2024-02-29T00:00",
            "SELECT ADD_MONTHS('2024-01-30', 1)");
        assertAnswer(
            "TIMESTAMP_NTZ(9)[SB16]",
            "SELECT SYSTEM$TYPEOF(ADD_MONTHS('2024-01-30', 1))");
        assertAnswer(
            "null",
            "SELECT ADD_MONTHS(NULL, 1)");
        assertAnswer(
            "NULL[LOB]",
            "SELECT SYSTEM$TYPEOF(ADD_MONTHS(NULL, 1))");
        assertAnswer(
            "2024-01-01T08:00",
            "SELECT CONVERT_TIMEZONE('America/Los_Angeles', 'UTC', '2024-01-01 00:00:00'::TIMESTAMP_NTZ)");
        assertAnswer(
            "TIMESTAMP_NTZ(9)[SB16]",
            "SELECT SYSTEM$TYPEOF(CONVERT_TIMEZONE('America/Los_Angeles', 'UTC', '2024-01-01 "
                + "00:00:00'::TIMESTAMP_NTZ))");
        assertAnswer(
            "TIMESTAMP_TZ(9)[SB16]",
            "SELECT SYSTEM$TYPEOF(CONVERT_TIMEZONE('UTC', '2024-01-01 00:00:00 +01:00'::TIMESTAMP_TZ))");
        assertAnswer(
            "2024-07-01T06:00",
            "SELECT CONVERT_TIMEZONE('Europe/Warsaw', 'America/New_York', '2024-07-01 12:00:00')");
        assertAnswer(
            "TIMESTAMP_NTZ(9)[SB16]",
            "SELECT SYSTEM$TYPEOF(CONVERT_TIMEZONE('Europe/Warsaw', 'America/New_York', '2024-07-01 12:00:00'))");
        assertAnswer(
            "Wed",
            "SELECT DAYNAME('2024-01-31'::DATE)");
        assertAnswer(
            "VARCHAR(3)[LOB]",
            "SELECT SYSTEM$TYPEOF(DAYNAME('2024-01-31'::DATE))");
        assertAnswer(
            "Wed",
            "SELECT DAYNAME('2024-01-31 10:20:30.123'::TIMESTAMP_NTZ)");
        assertAnswer(
            "VARCHAR(3)[LOB]",
            "SELECT SYSTEM$TYPEOF(DAYNAME('2024-01-31 10:20:30.123'::TIMESTAMP_NTZ))");
        assertAnswer(
            "Jan",
            "SELECT MONTHNAME('2024-01-31'::DATE)");
        assertAnswer(
            "VARCHAR(3)[LOB]",
            "SELECT SYSTEM$TYPEOF(MONTHNAME('2024-01-31'::DATE))");
        assertAnswer(
            "3",
            "SELECT DAYOFWEEK('2024-01-31'::DATE)");
        assertAnswer(
            "NUMBER(2,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(DAYOFWEEK('2024-01-31'::DATE))");
        assertAnswer(
            "0",
            "SELECT DAYOFWEEK('2024-02-04'::DATE)");
        assertAnswer(
            "NUMBER(2,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(DAYOFWEEK('2024-02-04'::DATE))");
        assertAnswer(
            "31",
            "SELECT DAYOFYEAR('2024-01-31'::DATE)");
        assertAnswer(
            "NUMBER(4,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(DAYOFYEAR('2024-01-31'::DATE))");
        assertAnswer(
            "5",
            "SELECT WEEK('2024-01-31'::DATE)");
        assertAnswer(
            "NUMBER(2,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(WEEK('2024-01-31'::DATE))");
        assertAnswer(
            "1",
            "SELECT WEEKOFYEAR('2024-12-30'::DATE)");
        assertAnswer(
            "NUMBER(2,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(WEEKOFYEAR('2024-12-30'::DATE))");
        assertAnswer(
            "1",
            "SELECT QUARTER('2024-01-31'::DATE)");
        assertAnswer(
            "NUMBER(2,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(QUARTER('2024-01-31'::DATE))");
        assertAnswer(
            "1",
            "SELECT QUARTER('2024-01-31 10:20:30.123'::TIMESTAMP_NTZ)");
        assertAnswer(
            "NUMBER(2,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(QUARTER('2024-01-31 10:20:30.123'::TIMESTAMP_NTZ))");
        assertAnswer(
            "20",
            "SELECT MINUTE('2024-01-31 10:20:30.123'::TIMESTAMP_NTZ)");
        assertAnswer(
            "NUMBER(2,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(MINUTE('2024-01-31 10:20:30.123'::TIMESTAMP_NTZ))");
        assertAnswer(
            "20",
            "SELECT MINUTE('10:20:30'::TIME)");
        assertAnswer(
            "NUMBER(2,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(MINUTE('10:20:30'::TIME))");
        assertAnswer(
            "2024-02-05",
            "SELECT NEXT_DAY('2024-01-31'::DATE, 'mo')");
        assertAnswer(
            "DATE[SB4]",
            "SELECT SYSTEM$TYPEOF(NEXT_DAY('2024-01-31'::DATE, 'mo'))");
        assertAnswer(
            "2024-02-02",
            "SELECT NEXT_DAY('2024-01-31'::DATE, 'Friday')");
        assertAnswer(
            "DATE[SB4]",
            "SELECT SYSTEM$TYPEOF(NEXT_DAY('2024-01-31'::DATE, 'Friday'))");
        assertAnswer(
            "2024-02-07",
            "SELECT NEXT_DAY('2024-01-31 10:20:30.123'::TIMESTAMP_NTZ, 'we')");
        assertAnswer(
            "DATE[SB4]",
            "SELECT SYSTEM$TYPEOF(NEXT_DAY('2024-01-31 10:20:30.123'::TIMESTAMP_NTZ, 'we'))");
        assertAnswer(
            "2024-01-29",
            "SELECT PREVIOUS_DAY('2024-01-31'::DATE, 'mo')");
        assertAnswer(
            "DATE[SB4]",
            "SELECT SYSTEM$TYPEOF(PREVIOUS_DAY('2024-01-31'::DATE, 'mo'))");
        assertAnswer(
            "2024-01-28",
            "SELECT PREVIOUS_DAY('2024-01-31'::DATE, 'sunday')");
        assertAnswer(
            "DATE[SB4]",
            "SELECT SYSTEM$TYPEOF(PREVIOUS_DAY('2024-01-31'::DATE, 'sunday'))");
        assertAnswer(
            "REFUSED Unknown timezone: 'Nowhere/Zone'",
            "SELECT CONVERT_TIMEZONE('Nowhere/Zone', '2024-01-01 00:00:00'::TIMESTAMP_NTZ)");
        assertAnswer(
            "REFUSED Weekday 'xx' is not recognized",
            "SELECT NEXT_DAY('2024-01-31'::DATE, 'xx')");
        assertAnswer(
            "REFUSED Weekday 'm' is not recognized",
            "SELECT NEXT_DAY('2024-01-31'::DATE, 'm')");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [MINUTE] for parameter 'MINUTE date/time part'",
            "SELECT MINUTE('2024-01-31'::DATE)");
        assertAnswer(
            "Wed",
            "SELECT DAYNAME('2024-01-31')");
        assertAnswer(
            "null",
            "SELECT QUARTER(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[ADD_MONTHS(CAST('2024-01-31' AS DATE))], expected 2, got 1",
            "SELECT ADD_MONTHS('2024-01-31'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[DAYNAME()], expected 1, got 0",
            "SELECT DAYNAME()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[CONVERT_TIMEZONE('UTC')], expected 2, got 1",
            "SELECT CONVERT_TIMEZONE('UTC')");
    }

    @Test
    public void numbersHashesAndContext() {
        assertAnswer(
            "1.0",
            "SELECT COSH(0)");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(COSH(0))");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(COSH(1))");
        assertAnswer(
            "0.0",
            "SELECT SINH(0)");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(SINH(0))");
        assertAnswer(
            "1.1752011936438014",
            "SELECT SINH(1)");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(SINH(1))");
        assertAnswer(
            "0.0",
            "SELECT TANH(0)");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(TANH(0))");
        assertAnswer(
            "0.7615941559557649",
            "SELECT TANH(1)");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(TANH(1))");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(COSH('1'))");
        assertAnswer(
            "null",
            "SELECT TANH(NULL)");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(TANH(NULL))");
        assertAnswer(
            "0cc175b9c0f1b6a831c399e269772661",
            "SELECT MD5_HEX('a')");
        assertAnswer(
            "VARCHAR(32)[LOB]",
            "SELECT SYSTEM$TYPEOF(MD5_HEX('a'))");
        assertAnswer(
            "86f7e437faa5a7fce15d1ddcb9eaeaea377667b8",
            "SELECT SHA1_HEX('a')");
        assertAnswer(
            "VARCHAR(40)[LOB]",
            "SELECT SYSTEM$TYPEOF(SHA1_HEX('a'))");
        assertAnswer(
            "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
            "SELECT SHA2_HEX('a')");
        assertAnswer(
            "VARCHAR(128)[LOB]",
            "SELECT SYSTEM$TYPEOF(SHA2_HEX('a'))");
        assertAnswer(
            "1f40fc92da241694750979ee6cf582f2d5d7d28e18335de05abc54d0560e0f5302860c652bf08d560252aa5e74210546f369fbbbce8c12cfc7957b2652fe9a75",
            "SELECT SHA2_HEX('a', 512)");
        assertAnswer(
            "VARCHAR(128)[LOB]",
            "SELECT SYSTEM$TYPEOF(SHA2_HEX('a', 512))");
        assertAnswer(
            "abd37534c7d9a2efb9465de931cd7055ffdb8879563ae98078d6d6d5",
            "SELECT SHA2_HEX('a', 224)");
        assertAnswer(
            "VARCHAR(128)[LOB]",
            "SELECT SYSTEM$TYPEOF(SHA2_HEX('a', 224))");
        assertAnswer(
            "NUMBER(38,0)[SB16]",
            "SELECT SYSTEM$TYPEOF(MD5_NUMBER_LOWER64('a'))");
        assertAnswer(
            "919145239626757800",
            "SELECT MD5_NUMBER_UPPER64('a')");
        assertAnswer(
            "NUMBER(38,0)[SB16]",
            "SELECT SYSTEM$TYPEOF(MD5_NUMBER_UPPER64('a'))");
        assertAnswer(
            "c4ca4238a0b923820dcc509a6f75849b",
            "SELECT MD5_HEX(1)");
        assertAnswer(
            "VARCHAR(32)[LOB]",
            "SELECT SYSTEM$TYPEOF(MD5_HEX(1))");
        assertAnswer(
            "null",
            "SELECT MD5_HEX(NULL)");
        assertAnswer(
            "VARCHAR(32)[LOB]",
            "SELECT SYSTEM$TYPEOF(MD5_HEX(NULL))");
        assertAnswer(
            "TEST_DB",
            "SELECT CURRENT_DATABASE()");
        assertAnswer(
            "VARCHAR[LOB]",
            "SELECT SYSTEM$TYPEOF(CURRENT_DATABASE())");
        assertAnswer(
            "TEST_SCHEMA",
            "SELECT CURRENT_SCHEMA()");
        assertAnswer(
            "VARCHAR[LOB]",
            "SELECT SYSTEM$TYPEOF(CURRENT_SCHEMA())");
        assertAnswer(
            "true",
            "SELECT TRY_TO_BOOLEAN('yes')");
        assertAnswer(
            "BOOLEAN[SB1]",
            "SELECT SYSTEM$TYPEOF(TRY_TO_BOOLEAN('yes'))");
        assertAnswer(
            "true",
            "SELECT TRY_TO_BOOLEAN('on')");
        assertAnswer(
            "BOOLEAN[SB1]",
            "SELECT SYSTEM$TYPEOF(TRY_TO_BOOLEAN('on'))");
        assertAnswer(
            "true",
            "SELECT TRY_TO_BOOLEAN('t')");
        assertAnswer(
            "BOOLEAN[SB1]",
            "SELECT SYSTEM$TYPEOF(TRY_TO_BOOLEAN('t'))");
        assertAnswer(
            "true",
            "SELECT TRY_TO_BOOLEAN('1')");
        assertAnswer(
            "BOOLEAN[SB1]",
            "SELECT SYSTEM$TYPEOF(TRY_TO_BOOLEAN('1'))");
        assertAnswer(
            "null",
            "SELECT TRY_TO_BOOLEAN('2')");
        assertAnswer(
            "BOOLEAN[SB1]",
            "SELECT SYSTEM$TYPEOF(TRY_TO_BOOLEAN('2'))");
        assertAnswer(
            "null",
            "SELECT TRY_TO_BOOLEAN('abc')");
        assertAnswer(
            "BOOLEAN[SB1]",
            "SELECT SYSTEM$TYPEOF(TRY_TO_BOOLEAN('abc'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types NULL and "
                + "BOOLEAN",
            "SELECT TRY_TO_BOOLEAN(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types NULL and "
                + "BOOLEAN",
            "SELECT SYSTEM$TYPEOF(TRY_TO_BOOLEAN(NULL))");
        assertAnswer(
            "true",
            "SELECT TRY_TO_BOOLEAN(' true ')");
        assertAnswer(
            "BOOLEAN[SB1]",
            "SELECT SYSTEM$TYPEOF(TRY_TO_BOOLEAN(' true '))");
        assertAnswer(
            "false",
            "SELECT TRY_TO_BOOLEAN('F')");
        assertAnswer(
            "BOOLEAN[SB1]",
            "SELECT SYSTEM$TYPEOF(TRY_TO_BOOLEAN('F'))");
        assertAnswer(
            "false",
            "SELECT TRY_TO_BOOLEAN('n')");
        assertAnswer(
            "BOOLEAN[SB1]",
            "SELECT SYSTEM$TYPEOF(TRY_TO_BOOLEAN('n'))");
        assertAnswer(
            "VARCHAR[LOB]",
            "SELECT SYSTEM$TYPEOF(CURRENT_ROLE())");
        assertAnswer(
            "VARCHAR[LOB]",
            "SELECT SYSTEM$TYPEOF(CURRENT_USER())");
        assertAnswer(
            "VARCHAR[LOB]",
            "SELECT SYSTEM$TYPEOF(CURRENT_WAREHOUSE())");
        assertAnswer(
            "VARCHAR[LOB]",
            "SELECT SYSTEM$TYPEOF(CURRENT_SESSION())");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(NORMAL(0, 1, RANDOM()))");
        assertAnswer(
            "5.0",
            "SELECT NORMAL(5, 0, RANDOM())");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NORMAL': (NUMBER(1,0), NUMBER(1,0))",
            "SELECT NORMAL(0, 1)");
        assertAnswer(
            "REFUSED Invalid parameter value: 100. Reason: invalid SHA2 digest size",
            "SELECT SHA2_HEX('a', 100)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types NUMBER(1,"
                + "0) and BOOLEAN",
            "SELECT TRY_TO_BOOLEAN(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types NUMBER(2,"
                + "1) and BOOLEAN",
            "SELECT TRY_TO_BOOLEAN(1.5)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[COSH()], expected 1, got 0",
            "SELECT COSH()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[MD5_NUMBER_LOWER64()], expected 1, got 0",
            "SELECT MD5_NUMBER_LOWER64()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[CURRENT_DATABASE(1)] expected 0, got 1",
            "SELECT CURRENT_DATABASE(1)");
        assertAnswer(
            "true|true|true",
            "SELECT CURRENT_ROLE() IS NOT NULL, CURRENT_USER() IS NOT NULL, CURRENT_SESSION() IS NOT NULL");
        assertAnswer(
            "true|true",
            "SELECT MD5_NUMBER_LOWER64('a') = 3585878926028121697, MD5_NUMBER_UPPER64('a') = 919145239626757800");
    }

    @Test
    public void booleanWordsDigestSizesZonesAndNormal() {
        assertAnswer(
            "true|true|true|true|false|false",
            "SELECT TRY_TO_BOOLEAN('y'), TRY_TO_BOOLEAN('T'), TRY_TO_BOOLEAN('Yes'), TRY_TO_BOOLEAN('ON'), "
                + "TRY_TO_BOOLEAN('off'), TRY_TO_BOOLEAN('0')");
        assertAnswer(
            "null|null|true|null|null|null",
            "SELECT TRY_TO_BOOLEAN('tr'), TRY_TO_BOOLEAN('truee'), TRY_TO_BOOLEAN(' t '), TRY_TO_BOOLEAN('-1'), "
                + "TRY_TO_BOOLEAN('0.0'), TRY_TO_BOOLEAN('')");
        assertAnswer(
            "null|null|null|null|null|null",
            "SELECT TRY_TO_BOOLEAN('2'), TRY_TO_BOOLEAN('1.0'), TRY_TO_BOOLEAN('00'), TRY_TO_BOOLEAN('1e0'), "
                + "TRY_TO_BOOLEAN('+1'), TRY_TO_BOOLEAN('nan')");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types NUMBER(1,"
                + "0) and BOOLEAN",
            "SELECT TRY_TO_BOOLEAN(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types NUMBER(1,"
                + "0) and BOOLEAN",
            "SELECT TRY_TO_BOOLEAN(0)");
        assertAnswer(
            "true",
            "SELECT TRY_TO_BOOLEAN(TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types VARIANT "
                + "and BOOLEAN",
            "SELECT TRY_TO_BOOLEAN(TO_VARIANT('t'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types VARIANT "
                + "and BOOLEAN",
            "SELECT TRY_TO_BOOLEAN(TO_VARIANT(1))");
        assertAnswer(
            "true|true|true|false|false|false",
            "SELECT TO_BOOLEAN('t'), TO_BOOLEAN('y'), TO_BOOLEAN('on'), TO_BOOLEAN('F'), TO_BOOLEAN('n'), "
                + "TO_BOOLEAN('Off')");
        assertAnswer(
            "REFUSED Boolean value '2' is not recognized",
            "SELECT TO_BOOLEAN('2')");
        assertAnswer(
            "REFUSED Boolean value 'tr' is not recognized",
            "SELECT TO_BOOLEAN('tr')");
        assertAnswer(
            "true|false|true",
            "SELECT 't'::BOOLEAN, 'no'::BOOLEAN, '1'::BOOLEAN");
        assertAnswer(
            "REFUSED Invalid parameter value: 100. Reason: invalid SHA2 digest size",
            "SELECT SHA2('a', 100)");
        assertAnswer(
            "REFUSED Invalid parameter value: 0. Reason: invalid SHA2 digest size",
            "SELECT SHA2_BINARY('a', 0)");
        assertAnswer(
            "REFUSED Invalid parameter value: 0. Reason: invalid SHA2 digest size",
            "SELECT SHA2('a', NULL)");
        assertAnswer(
            "true",
            "SELECT SHA2('a', 256) = SHA2('a')");
        assertAnswer(
            "54a59b9f22b0b80880d8427e548b7c23abd873486e1f035dce9cd697e85175033caa88e6d57bc35efae0b5afd3145f31",
            "SELECT SHA2_HEX('a', 384)");
        assertAnswer(
            "REFUSED Invalid parameter value: 2. Reason: invalid SHA2 digest size",
            "SELECT SHA2('a', 1.5)");
        assertAnswer(
            "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
            "SELECT SHA2('a', '256')");
        assertAnswer(
            "REFUSED Invalid parameter value: -256. Reason: invalid SHA2 digest size",
            "SELECT SHA2('a', -256)");
        assertAnswer(
            "REFUSED Unknown timezone: 'Nowhere/Zone'",
            "SELECT CONVERT_TIMEZONE('Nowhere/Zone', 'UTC', '2024-01-01 00:00:00'::TIMESTAMP_NTZ)");
        assertAnswer(
            "REFUSED Unknown timezone: 'Nowhere/Zone'",
            "SELECT CONVERT_TIMEZONE('UTC', 'Nowhere/Zone', '2024-01-01 00:00:00'::TIMESTAMP_NTZ)");
        assertAnswer(
            "REFUSED Unknown timezone: 'utc'",
            "SELECT CONVERT_TIMEZONE('utc', '2024-01-01 00:00:00 +01:00'::TIMESTAMP_TZ)::VARCHAR");
        assertAnswer(
            "REFUSED Unknown timezone: 'america/los_angeles'",
            "SELECT CONVERT_TIMEZONE('america/los_angeles', '2024-01-01 00:00:00 +01:00'::TIMESTAMP_TZ)::VARCHAR");
        assertAnswer(
            "REFUSED Unknown timezone: '+01:00'",
            "SELECT CONVERT_TIMEZONE('+01:00', '2024-01-01 00:00:00 +00:00'::TIMESTAMP_TZ)::VARCHAR");
        assertAnswer(
            "REFUSED Unknown timezone: ''",
            "SELECT CONVERT_TIMEZONE('', '2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "null",
            "SELECT CONVERT_TIMEZONE(NULL, '2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "REFUSED Unknown timezone: 'PST'",
            "SELECT CONVERT_TIMEZONE('PST', '2024-01-01'::TIMESTAMP_NTZ)::VARCHAR");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NORMAL': (NUMBER(1,0), NUMBER(1,0))",
            "SELECT NORMAL(0, 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[NORMAL(0)], expected 2, got 1",
            "SELECT NORMAL(0)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[NORMAL()], expected 2, got 0",
            "SELECT NORMAL()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[NORMAL(0, 1, 2, 3)] expected 3, got 4",
            "SELECT NORMAL(0, 1, 2, 3)");
        assertAnswer(
            "REFUSED Numeric value 'a' is not recognized",
            "SELECT NORMAL('a', 1, RANDOM())");
        assertAnswer(
            "REFUSED Invalid parameter value: -1. Reason: stddev must not be negative",
            "SELECT NORMAL(0, -1, RANDOM())");
        assertAnswer(
            "REFUSED Invalid parameter value: NULL. Reason: mean must not be NULL",
            "SELECT NORMAL(NULL, 1, RANDOM())");
        assertAnswer(
            "null",
            "SELECT NORMAL(0, 1, NULL)");
        assertAnswer(
            "5.0",
            "SELECT NORMAL(5, 0, 7)");
        assertAnswer(
            "0.5",
            "SELECT NORMAL(0.5, 0, RANDOM())");
        assertAnswer(
            "3.7621956910836314|3.626860407847019|0.9640275800758169",
            "SELECT COSH(2), SINH(2), TANH(2)");
        assertAnswer(
            "1.1276259652063807|0.5210953054937474|0.46211715726000974",
            "SELECT COSH(0.5), SINH(0.5), TANH(0.5)");
        assertAnswer(
            "11013.232920103324|11013.232874703393|0.9999999958776927",
            "SELECT COSH(10), SINH(10), TANH(10)");
        assertAnswer(
            "1.1169973830808557E308",
            "SELECT COSH(710)");
        assertAnswer(
            "1.1169973830808557E308",
            "SELECT SINH(710)");
        assertAnswer(
            "1.0",
            "SELECT TANH(710)");
        assertAnswer(
            "REFUSED Numeric value 'a' is not recognized",
            "SELECT COSH('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COSH': (BOOLEAN)",
            "SELECT COSH(TRUE)");
    }

    @Test
    public void digestSizesZoneNamesAndNormalValues() {
        assertAnswer(
            "abd37534c7d9a2efb9465de931cd7055ffdb8879563ae98078d6d6d5",
            "SELECT SHA2('a', 224)");
        assertAnswer(
            "54a59b9f22b0b80880d8427e548b7c23abd873486e1f035dce9cd697e85175033caa88e6d57bc35efae0b5afd3145f31",
            "SELECT SHA2('a', 384)");
        assertAnswer(
            "1f40fc92da241694750979ee6cf582f2d5d7d28e18335de05abc54d0560e0f5302860c652bf08d560252aa5e74210546f369fbbbce8c12cfc7957b2652fe9a75",
            "SELECT SHA2('a', 512)");
        assertAnswer(
            "REFUSED Numeric value 'abc' is not recognized",
            "SELECT SHA2('a', 'abc')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SHA2': (VARCHAR(1), BOOLEAN)",
            "SELECT SHA2('a', TRUE)");
        assertAnswer(
            "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
            "SELECT SHA2('a', 256.4)");
        assertAnswer(
            "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
            "SELECT SHA2('a', 255.5)");
        assertAnswer(
            "REFUSED Invalid parameter value: 100. Reason: invalid SHA2 digest size",
            "SELECT SHA2_BINARY('a', 100)");
        assertAnswer(
            "REFUSED Invalid parameter value: 0. Reason: invalid SHA2 digest size",
            "SELECT SHA2_HEX('a', NULL)");
        assertAnswer(
            "REFUSED Invalid parameter value: 100. Reason: invalid SHA2 digest size",
            "SELECT SHA2(NULL, 100)");
        assertAnswer(
            "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
            "SELECT SHA2('a', 256::FLOAT)");
        assertAnswer(
            "REFUSED Invalid parameter value: 0. Reason: invalid SHA2 digest size",
            "SELECT SHA2(NULL, NULL)");
        assertAnswer(
            "ABD37534C7D9A2EFB9465DE931CD7055FFDB8879563AE98078D6D6D5",
            "SELECT SHA2_BINARY('a', 224)");
        assertAnswer(
            "REFUSED Invalid parameter value: 1. Reason: invalid SHA2 digest size",
            "SELECT SHA2_BINARY(NULL, 1)");
        assertAnswer(
            "REFUSED Invalid parameter value: 1. Reason: invalid SHA2 digest size",
            "SELECT SHA2('a', 1)");
        assertAnswer(
            "1f40fc92da241694750979ee6cf582f2d5d7d28e18335de05abc54d0560e0f5302860c652bf08d560252aa5e74210546f369fbbbce8c12cfc7957b2652fe9a75",
            "SELECT SHA2_HEX('a', '512')");
        assertAnswer(
            "REFUSED Numeric value '' is not recognized",
            "SELECT SHA2('a', '')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[SHA2('a', 256, 1)] expected 2, got 3",
            "SELECT SHA2('a', 256, 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[SHA2()], expected 1, got 0",
            "SELECT SHA2()");
        assertAnswer(
            "REFUSED Unknown timezone: 'Z'",
            "SELECT CONVERT_TIMEZONE('Z', '2024-07-01 12:00:00 +00:00'::TIMESTAMP_TZ)::VARCHAR");
        assertAnswer(
            "REFUSED Unknown timezone: 'UTC+1'",
            "SELECT CONVERT_TIMEZONE('UTC+1', '2024-07-01 12:00:00 +00:00'::TIMESTAMP_TZ)::VARCHAR");
        assertAnswer(
            "REFUSED Unknown timezone: 'GMT+1'",
            "SELECT CONVERT_TIMEZONE('GMT+1', '2024-07-01 12:00:00 +00:00'::TIMESTAMP_TZ)::VARCHAR");
        assertAnswer(
            "REFUSED Unknown timezone: '+0100'",
            "SELECT CONVERT_TIMEZONE('+0100', '2024-07-01 12:00:00 +00:00'::TIMESTAMP_TZ)::VARCHAR");
        assertAnswer(
            "REFUSED Unknown timezone: ' UTC'",
            "SELECT CONVERT_TIMEZONE(' UTC', '2024-07-01 12:00:00 +00:00'::TIMESTAMP_TZ)::VARCHAR");
        assertAnswer(
            "REFUSED Unknown timezone: 'localtime'",
            "SELECT CONVERT_TIMEZONE('localtime', '2024-07-01 12:00:00 +00:00'::TIMESTAMP_TZ)::VARCHAR");
        assertAnswer(
            "REFUSED Unknown timezone: 'posixrules'",
            "SELECT CONVERT_TIMEZONE('posixrules', '2024-07-01 12:00:00 +00:00'::TIMESTAMP_TZ)::VARCHAR");
        assertAnswer(
            "REFUSED Unknown timezone: 'SystemV/EST5'",
            "SELECT CONVERT_TIMEZONE('SystemV/EST5', '2024-07-01 12:00:00 +00:00'::TIMESTAMP_TZ)::VARCHAR");
        assertAnswer(
            "REFUSED Unknown timezone: 'Europe/London '",
            "SELECT CONVERT_TIMEZONE('Europe/London ', '2024-07-01 12:00:00 +00:00'::TIMESTAMP_TZ)::VARCHAR");
        assertAnswer(
            "REFUSED Unknown timezone: 'europe/warsaw'",
            "SELECT CONVERT_TIMEZONE('europe/warsaw', '2024-07-01 12:00:00 +00:00'::TIMESTAMP_TZ)::VARCHAR");
        assertAnswer(
            "null",
            "SELECT CONVERT_TIMEZONE('UTC', NULL)");
        assertAnswer(
            "null",
            "SELECT CONVERT_TIMEZONE(NULL, 'UTC', '2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "null",
            "SELECT CONVERT_TIMEZONE('UTC', NULL, '2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "null",
            "SELECT CONVERT_TIMEZONE('UTC', 'UTC', NULL)");
        assertAnswer(
            "null",
            "SELECT CONVERT_TIMEZONE('Nowhere', NULL)");
        assertAnswer(
            "null",
            "SELECT CONVERT_TIMEZONE('Nowhere', 'UTC', NULL)");
        assertAnswer(
            "null",
            "SELECT CONVERT_TIMEZONE(NULL, 'Nowhere', '2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "REFUSED Unknown timezone: '1'",
            "SELECT CONVERT_TIMEZONE(1, '2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'CONVERT_TIMEZONE': (VARCHAR(3), NUMBER(1,0))",
            "SELECT CONVERT_TIMEZONE('UTC', 1)");
        assertAnswer(
            "REFUSED Timestamp 'x' is not recognized",
            "SELECT CONVERT_TIMEZONE('UTC', 'x')");
        assertAnswer(
            "REFUSED Numeric value 'a' is not recognized",
            "SELECT NORMAL(0, 1, 'a')");
        assertAnswer(
            "REFUSED Numeric value 'a' is not recognized",
            "SELECT NORMAL(0, 'a', RANDOM())");
        assertAnswer(
            "REFUSED Invalid parameter value: NULL. Reason: stddev must not be NULL",
            "SELECT NORMAL(0, NULL, RANDOM())");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NORMAL': (BOOLEAN, NUMBER(1,0), NUMBER(19,0))",
            "SELECT NORMAL(TRUE, 1, RANDOM())");
        assertAnswer(
            "null",
            "SELECT NORMAL(0, 0, NULL)");
        assertAnswer(
            "REFUSED Invalid parameter value: NULL. Reason: mean must not be NULL",
            "SELECT NORMAL(NULL, NULL, NULL)");
        assertAnswer(
            "REFUSED Invalid parameter value: NULL. Reason: mean must not be NULL",
            "SELECT NORMAL(NULL, -1, RANDOM())");
        assertAnswer(
            "REFUSED Invalid parameter value: -1. Reason: stddev must not be negative",
            "SELECT NORMAL(0, -1, NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NORMAL': (NUMBER(1,0), BOOLEAN, NUMBER(19,0))",
            "SELECT NORMAL(0, TRUE, RANDOM())");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NORMAL': (NUMBER(1,0), NUMBER(1,0), BOOLEAN)",
            "SELECT NORMAL(0, 1, TRUE)");
        assertAnswer(
            "1.0",
            "SELECT NORMAL('1', '0', RANDOM())");
        assertAnswer(
            "0.0",
            "SELECT NORMAL(0, 0, 1.5)");
        assertAnswer(
            "0.0",
            "SELECT NORMAL(0, 0, '1')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NORMAL': (NUMBER(1,0), NUMBER(1,0), DATE)",
            "SELECT NORMAL(0, 0, '2024-01-01'::DATE)");
        assertAnswer(
            "1.5",
            "SELECT NORMAL(1.5, 0, 1)");
        assertAnswer(
            "true",
            "SELECT NORMAL(0, 1, 7) = NORMAL(0, 1, 7)");
        assertAnswer(
            "FLOAT[DOUBLE]|BINARY(64)[LOB]|VARCHAR(128)[LOB]|VARCHAR(128)[LOB]",
            "SELECT SYSTEM$TYPEOF(NORMAL(0, 0, 1)), SYSTEM$TYPEOF(SHA2_BINARY('a')), SYSTEM$TYPEOF(SHA2('a')), "
                + "SYSTEM$TYPEOF(SHA2_HEX('a', 512))");
    }

    @Test
    public void theDetailsBesideThem() {
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'CONVERT_TIMEZONE': (VARCHAR(3), BOOLEAN)",
            "SELECT CONVERT_TIMEZONE('UTC', TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'CONVERT_TIMEZONE': (VARCHAR(3), VARCHAR(3), NUMBER(1,0))",
            "SELECT CONVERT_TIMEZONE('UTC', 'UTC', 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'CONVERT_TIMEZONE': (VARCHAR(3), NUMBER(2,1))",
            "SELECT CONVERT_TIMEZONE('UTC', 1.5)");
        assertAnswer(
            "2024-01-01T00:00",
            "SELECT CONVERT_TIMEZONE('UTC', 'UTC', '2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_TZ(9)]",
            "SELECT CONVERT_TIMEZONE('UTC', '10:00:00'::TIME)");
        assertAnswer(
            "REFUSED Invalid parameter value: 0. Reason: Position must be positive",
            "SELECT REGEXP_COUNT('abc', '', 0)");
        assertAnswer(
            "REFUSED Invalid parameter value: 0. Reason: Occurrence must be positive",
            "SELECT REGEXP_INSTR('abc', '', 1, 0)");
        assertAnswer(
            "REFUSED Invalid parameter value: 0. Reason: Position must be positive",
            "SELECT REGEXP_REPLACE('abc', '', '-', 0)");
        assertAnswer(
            "REFUSED Invalid parameter value: x. Reason: Unknown parameter: 'x'",
            "SELECT REGEXP_SUBSTR('abc', '', 1, 1, 'x')");
        assertAnswer(
            "REFUSED Invalid parameter value: 0. Reason: Position must be positive",
            "SELECT REGEXP_SUBSTR_ALL('abc', '', 0)");
        assertAnswer(
            "REFUSED Invalid parameter value: -1. Reason: Occurrence must not be negative",
            "SELECT REGEXP_REPLACE('abc', '', '-', 1, -1)");
        assertAnswer(
            "REFUSED Invalid parameter value: 2. Reason: Return option must be 0, 1, or NULL",
            "SELECT REGEXP_INSTR('abc', '', 1, 1, 2)");
        assertAnswer(
            "0",
            "SELECT REGEXP_COUNT('', 'a')");
        assertAnswer(
            "-",
            "SELECT REGEXP_REPLACE('', 'a*', '-')");
        assertAnswer(
            "true",
            "SELECT REGEXP_SUBSTR('', '') IS NULL");
        assertAnswer(
            "null",
            "SELECT REGEXP_LIKE('abc', NULL)");
        assertAnswer(
            "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
            "SELECT SHA2('a', ' 256 ')");
        assertAnswer(
            "abd37534c7d9a2efb9465de931cd7055ffdb8879563ae98078d6d6d5",
            "SELECT SHA2('a', 223.5)");
        assertAnswer(
            "REFUSED Invalid parameter value: 225. Reason: invalid SHA2 digest size",
            "SELECT SHA2('a', 224.5)");
        assertAnswer(
            "REFUSED Invalid parameter value: 0. Reason: invalid SHA2 digest size",
            "SELECT SHA2_HEX('a', -0.4)");
        assertAnswer(
            "54A59B9F22B0B80880D8427E548B7C23ABD873486E1F035DCE9CD697E85175033CAA88E6D57BC35EFAE0B5AFD3145F31",
            "SELECT SHA2_BINARY('a', '384')::VARCHAR");
        assertAnswer(
            "REFUSED Invalid parameter value: -1.5. Reason: stddev must not be negative",
            "SELECT NORMAL(0, -1.5, 1)");
        assertAnswer(
            "REFUSED Invalid parameter value: -1. Reason: stddev must not be negative",
            "SELECT NORMAL(0, '-1', 1)");
        assertAnswer(
            "REFUSED Numeric value 'a' is not recognized",
            "SELECT NORMAL('a', NULL, 1)");
        assertAnswer(
            "REFUSED Invalid parameter value: NULL. Reason: mean must not be NULL",
            "SELECT NORMAL(NULL, 'a', 1)");
        assertAnswer(
            "0.0",
            "SELECT NORMAL(0, 0::FLOAT, 1)");
        assertAnswer(
            "0.0",
            "SELECT NORMAL(0, -0.0, 1)");
        assertAnswer(
            "Infinity",
            "SELECT NORMAL(1e400, 0, 1)");
        assertAnswer(
            "0.0",
            "SELECT NORMAL(0, 0, -1)");
        assertAnswer(
            "3.0",
            "SELECT NORMAL(2, 0, RANDOM()) + 1");
        assertAnswer(
            "1",
            "SELECT AS_INTEGER(TO_VARIANT(1), 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[AS_DECIMAL(CAST(1.25 AS VARIANT), 10, 1, 1)] expected 3, got 4",
            "SELECT AS_DECIMAL(TO_VARIANT(1.25), 10, 1, 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[AS_INTEGER()], expected 1, got 0",
            "SELECT AS_INTEGER()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[AS_TIMESTAMP_TZ()], expected 1, got 0",
            "SELECT AS_TIMESTAMP_TZ()");
        assertAnswer(
            "123.5",
            "SELECT AS_DECIMAL(TO_VARIANT(123.456), 4, 1)");
        assertAnswer(
            "REFUSED Number out of representable range: type FIXED, value 123.5",
            "SELECT AS_DECIMAL(TO_VARIANT(123.456), 3, 1)");
        assertAnswer(
            "null|null|true|null",
            "SELECT TRY_TO_BOOLEAN(''), TRY_TO_BOOLEAN('  '), TRY_TO_BOOLEAN('TRUE '), TRY_TO_BOOLEAN('t r')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[TRY_TO_BOOLEAN()], expected 1, got 0",
            "SELECT TRY_TO_BOOLEAN()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[TRY_TO_BOOLEAN('t', 1)] expected 1, got 2",
            "SELECT TRY_TO_BOOLEAN('t', 1)");
        assertAnswer(
            "true|false",
            "SELECT TO_BOOLEAN(' on '), TO_BOOLEAN('  n')");
        assertAnswer(
            "[1,undefined,[2]]",
            "SELECT ARRAY_FLATTEN([[1], [NULL], [[2]]])");
        assertAnswer(
            "3",
            "SELECT ARRAY_FLATTEN([[1, 2], [3, 4]])[2]");
        assertAnswer(
            "true|true|true|true|true|true",
            "SELECT IS_DECIMAL(1e0), IS_DECIMAL(1.50), IS_INTEGER(1e0), IS_DOUBLE(1e0), IS_INTEGER(100), "
                + "IS_INTEGER(1.0)");
        assertAnswer(
            "true|1",
            "SELECT STRIP_NULL_VALUE(PARSE_JSON('{\"a\": null, \"b\": 1}'):a) IS NULL, "
                + "STRIP_NULL_VALUE(PARSE_JSON('{\"a\": null, \"b\": 1}'):b)");
    }
}
