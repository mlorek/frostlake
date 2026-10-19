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
 * The storage tag SYSTEM$TYPEOF gives a call whose argument is always NULL: the empty interval, [SB1], for the
 * date parts, MONTHS_BETWEEN, the character codes, the lengths, the string distances, MOD, TRUNC and the bitwise
 * functions - over a typed NULL constant, a column that holds nothing but NULLs, a variable of a block, an
 * aggregate over such a column, and a call that passes its NULL argument on. DATEDIFF, POSITION and the others
 * that keep their declared width are here too, and so are REPLACE and the REGEXP functions, which are not read
 * through. A calendar part of a constant date folds to its one value. A part declares the same width under
 * every word that names it. Every answer is live-verified.
 */
public class NullStorageTagTest extends MeasuredCellsTestSupport {

    @Test
    public void aCallOverATypedNull() {
        engine.execute(
            "CREATE OR REPLACE TABLE tn (d DATE, t TIMESTAMP_NTZ, s VARCHAR(10), n NUMBER(10,2), i INT)");
        engine.execute(
            "INSERT INTO tn VALUES (NULL, NULL, NULL, NULL, NULL)");
        try {
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(NULL::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(MONTH(NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(MONTH(NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAY(NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAY(NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFMONTH(NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFMONTH(NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(NULL::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFWEEK(NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFWEEK(NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFWEEKISO(NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFWEEKISO(NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(WEEK(NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(WEEK(NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(WEEKOFYEAR(NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(WEEKOFYEAR(NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(WEEKISO(NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(WEEKISO(NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(QUARTER(NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(QUARTER(NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAROFWEEK(NULL::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAROFWEEK(NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAROFWEEKISO(NULL::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAROFWEEKISO(NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(HOUR(NULL::TIME))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(HOUR(NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(MINUTE(NULL::TIME))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(MINUTE(NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(SECOND(NULL::TIME))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(SECOND(NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(year FROM NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('year', NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(month FROM NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('month', NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(day FROM NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('day', NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(dayofyear FROM NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('dayofyear', NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(dayofweek FROM NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('dayofweek', NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(week FROM NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('week', NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(quarter FROM NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('quarter', NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(yearofweek FROM NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('yearofweek', NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(yearofweekiso FROM NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('yearofweekiso', NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(weekiso FROM NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('weekiso', NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(hour FROM NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('hour', NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(minute FROM NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('minute', NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(second FROM NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('second', NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(18,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(epoch_second FROM NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(18,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('epoch_second', NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(9,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(nanosecond FROM NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(9,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('nanosecond', NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('year', NULL::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(year FROM NULL::DATE))");
            assertAnswer(
                "NUMBER(9,0)[SB4]",
                "SELECT SYSTEM$TYPEOF(DATEDIFF(DAY, NULL::DATE, CURRENT_DATE))");
            assertAnswer(
                "NUMBER(9,0)[SB4]",
                "SELECT SYSTEM$TYPEOF(DATEDIFF(YEAR, NULL::DATE, '2024-01-01'::DATE))");
            assertAnswer(
                "NUMBER(27,6)[SB1]",
                "SELECT SYSTEM$TYPEOF(MONTHS_BETWEEN(NULL::DATE, '2024-01-01'::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(NULL::VARCHAR(10)))");
            assertAnswer(
                "NUMBER(18,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(UNICODE(NULL::VARCHAR(10)))");
            assertAnswer(
                "NUMBER(18,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(LENGTH(NULL::VARCHAR(10)))");
            assertAnswer(
                "NUMBER(18,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(LEN(NULL::VARCHAR(10)))");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function CHAR_LENGTH.",
                "SELECT SYSTEM$TYPEOF(CHAR_LENGTH(NULL::VARCHAR(10)))");
            assertAnswer(
                "NUMBER(18,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(OCTET_LENGTH(NULL::VARCHAR(10)))");
            assertAnswer(
                "NUMBER(19,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(BIT_LENGTH(NULL::VARCHAR(10)))");
            assertAnswer(
                "NUMBER(9,0)[SB4]",
                "SELECT SYSTEM$TYPEOF(POSITION('a', NULL::VARCHAR(10)))");
            assertAnswer(
                "NUMBER(9,0)[SB4]",
                "SELECT SYSTEM$TYPEOF(CHARINDEX('a', NULL::VARCHAR(10)))");
            assertAnswer(
                "NUMBER(18,0)[SB8]",
                "SELECT SYSTEM$TYPEOF(REGEXP_COUNT(NULL::VARCHAR(10), 'a'))");
            assertAnswer(
                "NUMBER(18,0)[SB8]",
                "SELECT SYSTEM$TYPEOF(REGEXP_INSTR(NULL::VARCHAR(10), 'a'))");
            assertAnswer(
                "NUMBER(9,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EDITDISTANCE(NULL::VARCHAR(10), 'a'))");
            assertAnswer(
                "NUMBER(9,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EDITDISTANCE('a', NULL::VARCHAR(10)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(JAROWINKLER_SIMILARITY(NULL::VARCHAR(10), 'a'))");
            assertAnswer(
                "NUMBER(9,0)[SB4]",
                "SELECT SYSTEM$TYPEOF(ARRAY_SIZE(NULL::ARRAY))");
            assertAnswer(
                "NUMBER(9,0)[SB4]",
                "SELECT SYSTEM$TYPEOF(ARRAY_POSITION(1, NULL::ARRAY))");
            assertAnswer(
                "NUMBER(10,2)[SB1]",
                "SELECT SYSTEM$TYPEOF(MOD(NULL::NUMBER(10,2), 3))");
            assertAnswer(
                "NUMBER(10,2)[SB1]",
                "SELECT SYSTEM$TYPEOF(MOD(3, NULL::NUMBER(10,2)))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(MOD(NULL::INT, 3))");
            assertAnswer(
                "NUMBER(38,6)[SB1]",
                "SELECT SYSTEM$TYPEOF(DIV0(NULL::INT, 1))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ABS(NULL::INT))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(SIGN(NULL::INT))");
            assertAnswer(
                "NUMBER(10,2)[SB1]",
                "SELECT SYSTEM$TYPEOF(ABS(NULL::NUMBER(10,2)))");
            assertAnswer(
                "NUMBER(11,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(CEIL(NULL::NUMBER(10,2)))");
            assertAnswer(
                "NUMBER(11,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(FLOOR(NULL::NUMBER(10,2)))");
            assertAnswer(
                "NUMBER(11,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ROUND(NULL::NUMBER(10,2)))");
            assertAnswer(
                "NUMBER(11,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(TRUNC(NULL::NUMBER(10,2)))");
            assertAnswer(
                "FLOAT[DOUBLE]",
                "SELECT SYSTEM$TYPEOF(SQUARE(NULL::INT))");
            assertAnswer(
                "NUMBER(37,0)[SB16]",
                "SELECT SYSTEM$TYPEOF(FACTORIAL(NULL::INT))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(BITAND(NULL::INT, 1))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(BITOR(NULL::INT, 1))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(BITXOR(NULL::INT, 1))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(BITNOT(NULL::INT))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(BITSHIFTLEFT(NULL::INT, 1))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(BITSHIFTRIGHT(NULL::INT, 1))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(LEAST(NULL::INT, 1))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(GREATEST(NULL::INT, 1))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(NVL(NULL::INT, NULL))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ZEROIFNULL(NULL::INT))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULLIFZERO(NULL::INT))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(TO_NUMBER(NULL::VARCHAR(10)))");
            assertAnswer(
                "NUMBER(18,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(LENGTH(NULL::BINARY))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(WIDTH_BUCKET(NULL::INT, 0, 10, 5))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL::INT + 1)");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL::INT * 2)");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(-NULL::INT)");
            assertAnswer(
                "NUMBER(16,8)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL::NUMBER(10,2) / 3)");
            assertAnswer(
                "NUMBER(11,2)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL::NUMBER(10,2) + 1.5)");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL::INT - 1)");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL::INT % 2)");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR('2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR('2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(ASCII('a'))");
            assertAnswer(
                "NUMBER(9,0)[SB4]",
                "SELECT SYSTEM$TYPEOF(EDITDISTANCE('ab', 'a'))");
            assertAnswer(
                "NUMBER(4,2)[SB1]",
                "SELECT SYSTEM$TYPEOF(MOD(10.25, 3))");
            assertAnswer(
                "NUMBER(19,0)[SB8]",
                "SELECT SYSTEM$TYPEOF(HASH(NULL::INT))");
            assertAnswer(
                "ARRAY[LOB]",
                "SELECT SYSTEM$TYPEOF(STRTOK_TO_ARRAY(NULL::VARCHAR))");
            assertAnswer(
                "VARCHAR(7)[LOB]",
                "SELECT SYSTEM$TYPEOF(SOUNDEX(NULL::VARCHAR(10)))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(TRY_TO_NUMBER(NULL::VARCHAR(10)))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(TRY_TO_NUMBER('x'))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(IFF(TRUE, NULL::INT, 1))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(IFF(FALSE, 1, NULL::INT))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(CASE WHEN TRUE THEN NULL::INT END)");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(COALESCE(NULL::INT, NULL::INT))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULLIF(NULL::INT, 1))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(COALESCE(NULL::DATE, NULL)))");
            assertAnswer(
                "NUMBER(18,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(LENGTH(UPPER(NULL::VARCHAR(10))))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(DATEADD(DAY, 1, NULL::DATE)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]|NUMBER(4,0)[SB1]|NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)), SYSTEM$TYPEOF(DAYOFYEAR(d)), SYSTEM$TYPEOF(MONTH(d)) FROM tn");
            assertAnswer(
                "NUMBER(4,0)[SB1]|NUMBER(2,0)[SB1]|NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(t)), SYSTEM$TYPEOF(HOUR(t)), SYSTEM$TYPEOF(EXTRACT(year FROM t)) FROM "
                    + "tn");
            assertAnswer(
                "NUMBER(4,0)[SB1]|NUMBER(18,0)[SB1]|NUMBER(9,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(s)), SYSTEM$TYPEOF(LENGTH(s)), SYSTEM$TYPEOF(EDITDISTANCE(s, 'a')) "
                    + "FROM tn");
            assertAnswer(
                "NUMBER(10,2)[SB1]|NUMBER(10,2)[SB1]|NUMBER(38,0)[SB1]|NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(MOD(n, 3)), SYSTEM$TYPEOF(ABS(n)), SYSTEM$TYPEOF(MOD(i, 3)), "
                    + "SYSTEM$TYPEOF(i + 1) FROM tn");
            assertAnswer(
                "NUMBER(4,0)[SB1]|NULL[LOB]",
                "DECLARE s DATE; r RESULTSET; BEGIN r := (SELECT SYSTEM$TYPEOF(YEAR(COALESCE(:s, NULL))), "
                    + "SYSTEM$TYPEOF(YEAR(:s))); RETURN TABLE(r); END;");
            assertAnswer(
                "NUMBER(4,0)[SB1]|NUMBER(9,0)[SB1]",
                "DECLARE s VARCHAR(10); r RESULTSET; BEGIN r := (SELECT SYSTEM$TYPEOF(ASCII(:s)), "
                    + "SYSTEM$TYPEOF(EDITDISTANCE(:s, 'a'))); RETURN TABLE(r); END;");
            assertAnswer(
                "NUMBER(10,2)[SB1]",
                "DECLARE s NUMBER(10,2); r RESULTSET; BEGIN r := (SELECT SYSTEM$TYPEOF(MOD(:s, 3))); RETURN "
                    + "TABLE(r); END;");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void constantsColumnsAndNestedCalls() {
        engine.execute(
            "CREATE OR REPLACE TABLE tn (d DATE, t TIMESTAMP_NTZ, s VARCHAR(10), n NUMBER(10,2), i INT)");
        engine.execute(
            "INSERT INTO tn VALUES (NULL, NULL, NULL, NULL, NULL)");
        engine.execute(
            "CREATE OR REPLACE TABLE tw (d DATE, t TIMESTAMP_NTZ, s VARCHAR(10), n NUMBER(10,2), i INT)");
        engine.execute(
            "INSERT INTO tw VALUES ('2024-05-06', '2024-05-06 10:11:12', 'abc', 1.5, 7), (NULL, NULL, NULL, NULL, "
                + "NULL)");
        engine.execute(
            "CREATE OR REPLACE TABLE te (d DATE, s VARCHAR(10), n NUMBER(10,2))");
        try {
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR('2024-12-31'::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(MONTH('2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR('0001-01-01'::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(TO_DATE('2024-05-06')))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(dayofyear FROM '2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(HOUR('10:00:00'::TIME))");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(DATE '2024-05-06'))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(DATE '2024-05-06'))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('dayofyear', '2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAROFWEEK('2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(WEEK('2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR('2024-05-06 10:00:00'::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(18,0)[SB8]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(epoch_second FROM '2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(year FROM '2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(ASCII('a'))");
            assertAnswer(
                "NUMBER(18,0)[SB8]",
                "SELECT SYSTEM$TYPEOF(UNICODE('a'))");
            assertAnswer(
                "NUMBER(18,0)[SB8]",
                "SELECT SYSTEM$TYPEOF(LENGTH('abc'))");
            assertAnswer(
                "NUMBER(18,0)[SB8]",
                "SELECT SYSTEM$TYPEOF(OCTET_LENGTH('abc'))");
            assertAnswer(
                "NUMBER(19,0)[SB16]",
                "SELECT SYSTEM$TYPEOF(BIT_LENGTH('abc'))");
            assertAnswer(
                "NUMBER(9,0)[SB4]",
                "SELECT SYSTEM$TYPEOF(EDITDISTANCE('ab', 'a'))");
            assertAnswer(
                "NUMBER(9,0)[SB4]",
                "SELECT SYSTEM$TYPEOF(POSITION('b', 'abc'))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(MOD(7, 3))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(BITAND(7, 3))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ABS(-5))");
            assertAnswer(
                "NUMBER(5,0)[SB4]",
                "SELECT SYSTEM$TYPEOF(TRUNC(15.75))");
            assertAnswer(
                "NUMBER(9,0)[SB4]",
                "SELECT SYSTEM$TYPEOF(DATEDIFF(DAY, '2024-01-01'::DATE, '2024-01-03'::DATE))");
            assertAnswer(
                "NUMBER(27,6)[SB16]",
                "SELECT SYSTEM$TYPEOF(MONTHS_BETWEEN('2024-03-01'::DATE, '2024-01-01'::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB2]|NUMBER(18,0)[SB8]|NUMBER(10,2)[SB2]",
                "SELECT SYSTEM$TYPEOF(ASCII(s)), SYSTEM$TYPEOF(LENGTH(s)), SYSTEM$TYPEOF(MOD(n, 3)) FROM tw LIMIT "
                    + "1");
            assertAnswer(
                "",
                "SELECT SYSTEM$TYPEOF(YEAR(d)), SYSTEM$TYPEOF(ASCII(s)), SYSTEM$TYPEOF(LENGTH(s)), "
                    + "SYSTEM$TYPEOF(MOD(n, 3)) FROM te");
            assertAnswer(
                "",
                "SELECT SYSTEM$TYPEOF(YEAR(d)), SYSTEM$TYPEOF(ASCII(s)), SYSTEM$TYPEOF(MOD(n, 3)) FROM (SELECT * "
                    + "FROM te)");
            assertAnswer(
                "NUMBER(4,0)[SB1]|NUMBER(18,0)[SB1]|NUMBER(19,0)[SB1]|NUMBER(18,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)), SYSTEM$TYPEOF(OCTET_LENGTH(s)), SYSTEM$TYPEOF(BIT_LENGTH(s)), "
                    + "SYSTEM$TYPEOF(UNICODE(s)) FROM tn");
            assertAnswer(
                "NUMBER(9,0)[SB1]|NUMBER(4,0)[SB1]|NUMBER(11,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EDITDISTANCE('a', s)), SYSTEM$TYPEOF(JAROWINKLER_SIMILARITY(s, 'a')), "
                    + "SYSTEM$TYPEOF(TRUNC(n)) FROM tn");
            assertAnswer(
                "NUMBER(38,0)[SB1]|NUMBER(38,0)[SB1]|NUMBER(38,0)[SB1]|NUMBER(10,2)[SB1]",
                "SELECT SYSTEM$TYPEOF(BITAND(i, 1)), SYSTEM$TYPEOF(BITNOT(i)), SYSTEM$TYPEOF(BITSHIFTLEFT(i, 1)), "
                    + "SYSTEM$TYPEOF(MOD(3, n)) FROM tn");
            assertAnswer(
                "NUMBER(4,0)[SB1]|NUMBER(9,0)[SB1]|NUMBER(18,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(t)), SYSTEM$TYPEOF(EXTRACT(nanosecond FROM t)), "
                    + "SYSTEM$TYPEOF(DATE_PART(epoch_second, t)) FROM tn");
            assertAnswer(
                "NUMBER(27,6)[SB1]|NUMBER(9,0)[SB4]|NUMBER(9,0)[SB4]",
                "SELECT SYSTEM$TYPEOF(MONTHS_BETWEEN(d, CURRENT_DATE)), SYSTEM$TYPEOF(DATEDIFF(DAY, d, "
                    + "CURRENT_DATE)), SYSTEM$TYPEOF(POSITION('a', s)) FROM tn");
            assertAnswer(
                "NUMBER(4,0)[SB1]|NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(x.d)), SYSTEM$TYPEOF(ASCII(x.s)) FROM tn x JOIN tw y ON TRUE LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB2]|NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)), SYSTEM$TYPEOF(ASCII(s)) FROM tw WHERE d IS NULL");
            assertAnswer(
                "NUMBER(4,0)[SB1]|NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)), SYSTEM$TYPEOF(ASCII(s)) FROM tn WHERE i IS NULL");
            assertAnswer(
                "NUMBER(4,0)[SB1]|NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(MAX(d))), SYSTEM$TYPEOF(ASCII(MAX(s))) FROM tn");
            assertAnswer(
                "NUMBER(5,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(NULL::DATE) + 1)");
            assertAnswer(
                "NUMBER(18,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(LENGTH(LOWER(NULL::VARCHAR(10))))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(UPPER(NULL::VARCHAR(10))))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(NVL(NULL::DATE, NULL::DATE)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(IFF(TRUE, NULL::DATE, CURRENT_DATE)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(SUBSTR(NULL::VARCHAR(10), 1)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(TRIM(NULL::VARCHAR(10))))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(NULL::VARCHAR(10) || 'a'))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(TO_DATE(NULL::VARCHAR(10))))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(DATE_TRUNC(month, NULL::DATE)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(LAST_DAY(NULL::DATE)))");
            assertAnswer(
                "NUMBER(18,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(LENGTH(TO_VARCHAR(NULL::INT)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(CONCAT(NULL::VARCHAR(10), NULL::VARCHAR(10))))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(dayofweekiso FROM NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(weekofyear FROM NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(dayofmonth FROM NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(epoch_millisecond FROM NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [millisecond] for parameter 'EXTRACT date/time "
                    + "part'",
                "SELECT SYSTEM$TYPEOF(EXTRACT(millisecond FROM NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [microsecond] for parameter 'EXTRACT date/time "
                    + "part'",
                "SELECT SYSTEM$TYPEOF(EXTRACT(microsecond FROM NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(9,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(timezone_hour FROM NULL::TIMESTAMP_TZ))");
            assertAnswer(
                "NUMBER(9,0)[SB4]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(nanosecond FROM '10:00:00'::TIME))");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(yearofweekiso FROM '2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(weekiso FROM '2024-05-06'::DATE))");
            assertShape(
                "EXTRACT(YEAROFWEEKISO FROM D):NUMBER(4,0), EXTRACT(WEEKISO FROM D):NUMBER(2,0), "
                    + "EXTRACT(NANOSECOND FROM T):NUMBER(9,0), DATE_PART(DAYOFWEEKISO, D):NUMBER(2,0)",
                "SELECT EXTRACT(yearofweekiso FROM d), EXTRACT(weekiso FROM d), EXTRACT(nanosecond FROM t), "
                    + "DATE_PART(dayofweekiso, d) FROM tw");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void callsThatPassANullOn() {
        engine.execute(
            "CREATE OR REPLACE TABLE tn (d DATE, t TIMESTAMP_NTZ, s VARCHAR(10), n NUMBER(10,2), i INT)");
        engine.execute(
            "INSERT INTO tn VALUES (NULL, NULL, NULL, NULL, NULL)");
        engine.execute(
            "CREATE OR REPLACE TABLE tw (d DATE, t TIMESTAMP_NTZ, s VARCHAR(10), n NUMBER(10,2), i INT)");
        engine.execute(
            "INSERT INTO tw VALUES ('2024-05-06', '2024-05-06 10:11:12', 'abc', 1.5, 7), (NULL, NULL, NULL, NULL, "
                + "NULL)");
        engine.execute(
            "CREATE OR REPLACE VIEW vn AS SELECT d, s FROM tn");
        try {
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(LTRIM(NULL::VARCHAR(10))))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(RTRIM(NULL::VARCHAR(10))))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(LEFT(NULL::VARCHAR(10), 1)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(RIGHT(NULL::VARCHAR(10), 1)))");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(ASCII(REPLACE(NULL::VARCHAR(10), 'a')))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(LPAD(NULL::VARCHAR(10), 2)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(REVERSE(NULL::VARCHAR(10))))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(INITCAP(NULL::VARCHAR(10))))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(DATEADD(day, 1, NULL::DATE)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(ADD_MONTHS(NULL::DATE, 1)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(NULL::DATE + 1))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(NULL::VARCHAR(10) || 'a'))");
            assertAnswer(
                "NUMBER(18,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(LENGTH(TO_CHAR(NULL::DATE)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(TO_VARCHAR(NULL::INT)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(TO_TIMESTAMP(NULL::VARCHAR)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(NULL::VARCHAR::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(CAST(NULL AS DATE)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(TRY_TO_DATE(NULL::VARCHAR)))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(MOD(ABS(NULL::INT), 3))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(MOD(NULL::INT + 1, 3))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(MOD(-NULL::INT, 3))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(COALESCE(NULL::VARCHAR, NULL::VARCHAR)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(NULLIF(NULL::VARCHAR, 'a')))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(CASE WHEN TRUE THEN NULL::VARCHAR END))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(GREATEST(NULL::DATE, CURRENT_DATE)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(TO_DATE(NULL)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(TO_VARCHAR(NULL)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(NULL::TIMESTAMP_NTZ::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(NULL::INT::VARCHAR))");
            assertAnswer(
                "NUMBER(18,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(LENGTH(SUBSTR(NULL::VARCHAR(10), 1)))");
            assertAnswer(
                "NUMBER(18,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(LENGTH(TRIM(NULL::VARCHAR(10))))");
            assertAnswer(
                "NUMBER(18,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(LENGTH(NULL::VARCHAR(10)))");
            assertAnswer(
                "NUMBER(18,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(LENGTH(CONCAT(NULL::VARCHAR(10), NULL::VARCHAR(10))))");
            assertAnswer(
                "NUMBER(18,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(LENGTH(CONCAT(NULL::VARCHAR(10), 'a')))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(CONCAT_WS(',', NULL::VARCHAR(10), 'a')))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(SPLIT_PART(NULL::VARCHAR(10), ',', 1)))");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(ASCII(REGEXP_SUBSTR(NULL::VARCHAR(10), 'a')))");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(ASCII(REGEXP_REPLACE(NULL::VARCHAR(10), 'a', 'b')))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(TRANSLATE(NULL::VARCHAR(10), 'a', 'b')))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(REPEAT(NULL::VARCHAR(10), 2)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(DATE_TRUNC(month, NULL::TIMESTAMP_NTZ)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(DATEADD(month, NULL::INT, CURRENT_DATE)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(TIME_SLICE(NULL::TIMESTAMP_NTZ, 1, 'HOUR')))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(NEXT_DAY(NULL::DATE, 'mo')))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(PREVIOUS_DAY(NULL::DATE, 'mo')))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(HOUR(TO_TIME(NULL::VARCHAR)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(CONVERT_TIMEZONE('UTC', NULL::TIMESTAMP_NTZ)))");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(DATE_FROM_PARTS(NULL::INT, 1, 1)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(TO_TIMESTAMP_NTZ(NULL::VARCHAR)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(TO_DATE('2024-05-06')))");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(TO_DATE('2024-05-06')))");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(year FROM '2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(dayofyear FROM TO_DATE('2024-05-06')))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(dayofyear, DATE '2024-05-06'))");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(DATEADD(day, 1, '2024-05-06'::DATE)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR('2024-05-06 10:00:00'::TIMESTAMP_NTZ::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(HOUR(TO_TIME('10:00:00')))");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR('2024-05-06'::DATE + 1))");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(LAST_DAY('2024-05-06'::DATE)))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(MONTH(TO_TIMESTAMP('2024-05-06 10:00:00')))");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAROFWEEKISO('2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(WEEKISO('2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(DATE_TRUNC(month, '2024-05-06'::DATE)))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(TO_DATE('2024-05-06', 'YYYY-MM-DD')))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR('2024-05-06'::VARCHAR::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(doy FROM '2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(dy, '2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(month FROM '2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(hour FROM '10:00:00'::TIME))");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(year, '2024-05-06 10:00:00'::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(dayofyear, '2024-05-06 10:00:00'::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(CURRENT_DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB1]|NUMBER(4,0)[SB1]|NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(MIN(d))), SYSTEM$TYPEOF(YEAR(ANY_VALUE(d))), "
                    + "SYSTEM$TYPEOF(ASCII(MIN(s))) FROM tn");
            assertAnswer(
                "NUMBER(4,0)[SB2]|NUMBER(4,0)[SB2] ;; NUMBER(4,0)[SB2]|NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(MAX(d))), SYSTEM$TYPEOF(ASCII(MAX(s))) FROM tw");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAR(MAX(d))) FROM tn GROUP BY i");
            assertAnswer(
                "NUMBER(38,0)[SB1]|NUMBER(18,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(MOD(SUM(i), 3)), SYSTEM$TYPEOF(MOD(COUNT(i), 3)) FROM tn");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT d FROM tw WHERE d IS NULL)");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(YEAR(d)) FROM (SELECT d FROM tw) LIMIT 1");
            assertAnswer(
                "NUMBER(4,0)[SB2]|NUMBER(18,0)[SB8]|NUMBER(2,0)[SB1]|NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(ASCII(s)), SYSTEM$TYPEOF(LENGTH(s)), SYSTEM$TYPEOF(HOUR(t)), "
                    + "SYSTEM$TYPEOF(MOD(i, 3)) FROM tw LIMIT 1");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void theWidthADatePartDeclares() {
        engine.execute(
            "CREATE OR REPLACE TABLE tw (d DATE, t TIMESTAMP_NTZ, s VARCHAR(10), n NUMBER(10,2), i INT)");
        engine.execute(
            "INSERT INTO tw VALUES ('2024-05-06', '2024-05-06 10:11:12', 'abc', 1.5, 7), (NULL, NULL, NULL, NULL, "
                + "NULL)");
        try {
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(dow FROM NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(wk, NULL::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(doy, NULL::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(yrs, NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(q, NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(hh, NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(mi, NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [SS] for parameter 'DATE_PART date/time part'",
                "SELECT SYSTEM$TYPEOF(DATE_PART(ss, NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(9,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(ns, NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(9,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(timezone_minute, NULL::TIMESTAMP_TZ))");
            assertAnswer(
                "NUMBER(9,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(tzh, NULL::TIMESTAMP_TZ))");
            assertAnswer(
                "NUMBER(9,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(tzm, NULL::TIMESTAMP_TZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(weekday, NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(dayofweek_iso, NULL::DATE))");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [WEEKOFYEARISO] for parameter 'DATE_PART "
                    + "date/time part'",
                "SELECT SYSTEM$TYPEOF(DATE_PART(weekofyeariso, NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(week_iso, NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(mon, NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(d, NULL::DATE))");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [MS] for parameter 'DATE_PART date/time part'",
                "SELECT SYSTEM$TYPEOF(DATE_PART(ms, NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [US] for parameter 'DATE_PART date/time part'",
                "SELECT SYSTEM$TYPEOF(DATE_PART(us, NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(18,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(epoch, NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(epoch_milliseconds, NULL::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(dayofweekiso, NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(weekofyear, NULL::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(yearofweek, NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(WEEKOFYEAR(NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFWEEKISO(NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(WEEKISO(NULL::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(YEAROFWEEKISO(NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DAYOFWEEK(NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(QUARTER(NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(HOUR(NULL::TIME))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(SECOND(NULL::TIME))");
            assertShape(
                "REFUSED SQL compilation error: | invalid value [MS] for parameter 'DATE_PART date/time part'",
                "SELECT DATE_PART(dow, d), DATE_PART(wk, d), EXTRACT(doy FROM d), DATE_PART(dayofweekiso, d), "
                    + "DATE_PART(weekofyear, d), DATE_PART(timezone_hour, t::TIMESTAMP_TZ), "
                    + "DATE_PART(timezone_minute, t::TIMESTAMP_TZ), DATE_PART(ms, t), DATE_PART(us, t), "
                    + "DATE_PART(epoch_millisecond, t) FROM tw");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
