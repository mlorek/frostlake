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
 * A NULL in any argument of a text function answers NULL, whatever place it stands in: the position and the
 * length of SUBSTR, the count of LEFT, RIGHT and LPAD, the pad, the part of SPLIT_PART and STRTOK, the start of
 * POSITION, the position, the occurrence and the parameters of the REGEXP functions, the characters TRIM and
 * TRANSLATE read, REPLACE's replacement, and the bounds of ARRAY_SLICE - written as the bare word, as a typed
 * NULL, read from a column or bound from a variable of a block. The call keeps the type it declares over a
 * value. Every answer is live-verified.
 */
public class NullArgumentAnswersNullTest extends MeasuredCellsTestSupport {

    @Test
    public void aNullInAnyArgument() {
        engine.execute(
            "CREATE OR REPLACE TABLE tq (s VARCHAR(10), n INT)");
        engine.execute(
            "INSERT INTO tq VALUES ('abc', NULL)");
        try {
            assertAnswer(
                "null",
                "SELECT SUBSTR('abc', NULL)");
            assertAnswer(
                "null",
                "SELECT SUBSTR('abc', NULL, 1)");
            assertAnswer(
                "null",
                "SELECT SUBSTR('abc', 1, NULL)");
            assertAnswer(
                "null",
                "SELECT SUBSTR('abc', NULL, NULL)");
            assertAnswer(
                "null",
                "SELECT SUBSTR(NULL, 1)");
            assertAnswer(
                "null",
                "SELECT SUBSTRING('abc', NULL, 1)");
            assertAnswer(
                "null",
                "SELECT SUBSTR('abc', NULL::INT)");
            assertAnswer(
                "null",
                "SELECT SUBSTR('abc', 1, NULL::INT)");
            assertAnswer(
                "null",
                "SELECT SUBSTR('abc', NULL::VARCHAR)");
            assertAnswer(
                "null",
                "SELECT LPAD('a', NULL)");
            assertAnswer(
                "null",
                "SELECT LPAD('a', 3, NULL)");
            assertAnswer(
                "null",
                "SELECT LPAD('a', NULL, 'x')");
            assertAnswer(
                "null",
                "SELECT LPAD(NULL, 3)");
            assertAnswer(
                "null",
                "SELECT LPAD('a', 3, NULL::VARCHAR)");
            assertAnswer(
                "null",
                "SELECT RPAD('a', NULL)");
            assertAnswer(
                "null",
                "SELECT RPAD('a', 3, NULL)");
            assertAnswer(
                "null",
                "SELECT RPAD('a', NULL, 'x')");
            assertAnswer(
                "null",
                "SELECT RPAD(NULL, 3)");
            assertAnswer(
                "null",
                "SELECT LEFT('abc', NULL)");
            assertAnswer(
                "null",
                "SELECT RIGHT('abc', NULL)");
            assertAnswer(
                "null",
                "SELECT LEFT(NULL, 1)");
            assertAnswer(
                "null",
                "SELECT RIGHT(NULL, 1)");
            assertAnswer(
                "null",
                "SELECT LEFT('abc', NULL::INT)");
            assertAnswer(
                "null",
                "SELECT SPLIT_PART('a,b', ',', NULL)");
            assertAnswer(
                "null",
                "SELECT SPLIT_PART('a,b', NULL, 1)");
            assertAnswer(
                "null",
                "SELECT SPLIT_PART(NULL, ',', 1)");
            assertAnswer(
                "null",
                "SELECT POSITION('b', 'abc', NULL)");
            assertAnswer(
                "null",
                "SELECT POSITION(NULL, 'abc')");
            assertAnswer(
                "null",
                "SELECT POSITION('b', NULL)");
            assertAnswer(
                "null",
                "SELECT POSITION('b' IN NULL)");
            assertAnswer(
                "null",
                "SELECT CHARINDEX('b', 'abc', NULL)");
            assertAnswer(
                "null",
                "SELECT CHARINDEX(NULL, 'abc')");
            assertAnswer(
                "null",
                "SELECT CHARINDEX('b', NULL)");
            assertAnswer(
                "null",
                "SELECT STRTOK('a b', ' ', NULL)");
            assertAnswer(
                "null",
                "SELECT STRTOK('a b', NULL, 1)");
            assertAnswer(
                "null",
                "SELECT STRTOK('a b', NULL)");
            assertAnswer(
                "null",
                "SELECT STRTOK(NULL, ' ', 1)");
            assertAnswer(
                "null",
                "SELECT STRTOK(NULL)");
            assertAnswer(
                "null",
                "SELECT STRTOK_TO_ARRAY('a b', NULL)");
            assertAnswer(
                "null",
                "SELECT STRTOK_TO_ARRAY(NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_SUBSTR('abc', 'b', NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_SUBSTR('abc', 'b', 1, NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_SUBSTR('abc', 'b', 1, 1, NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_SUBSTR('abc', 'b', 1, 1, 'c', NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_SUBSTR('abc', NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_SUBSTR(NULL, 'b')");
            assertAnswer(
                "null",
                "SELECT REGEXP_REPLACE('abc', 'b', 'x', NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_REPLACE('abc', 'b', NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_REPLACE('abc', 'b', 'x', 1, NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_REPLACE('abc', 'b', 'x', 1, 1, NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_REPLACE('abc', NULL, 'x')");
            assertAnswer(
                "null",
                "SELECT REGEXP_REPLACE(NULL, 'b', 'x')");
            assertAnswer(
                "null",
                "SELECT REGEXP_REPLACE('abc', NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_INSTR('abc', 'b', NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_INSTR('abc', 'b', 1, NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_INSTR('abc', 'b', 1, 1, NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_INSTR('abc', 'b', 1, 1, 0, NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_INSTR('abc', 'b', 1, 1, 0, 'c', NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_INSTR('abc', NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_COUNT('abc', 'b', NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_COUNT('abc', 'b', 1, NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_COUNT('abc', NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_LIKE('abc', 'b', NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_LIKE('abc', NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_LIKE(NULL, 'b')");
            assertAnswer(
                "null",
                "SELECT RLIKE('abc', NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_SUBSTR_ALL('abc', 'b', NULL)");
            assertAnswer(
                "null",
                "SELECT REGEXP_SUBSTR_ALL('abc', NULL)");
            assertAnswer(
                "null",
                "SELECT ARRAY_SLICE(ARRAY_CONSTRUCT(1,2), NULL, 2)");
            assertAnswer(
                "null",
                "SELECT ARRAY_SLICE(ARRAY_CONSTRUCT(1,2), NULL::INT, 2)");
            assertAnswer(
                "null",
                "SELECT ARRAY_SLICE(ARRAY_CONSTRUCT(1,2), 0, NULL)");
            assertAnswer(
                "null",
                "SELECT ARRAY_SLICE(ARRAY_CONSTRUCT(1,2,3), NULL, NULL)");
            assertAnswer(
                "null",
                "SELECT ARRAY_SLICE(NULL, 0, 1)");
            assertAnswer(
                "null",
                "SELECT REPEAT('a', NULL)");
            assertAnswer(
                "null",
                "SELECT REPEAT(NULL, 2)");
            assertAnswer(
                "null",
                "SELECT INSERT('abc', NULL, 1, 'x')");
            assertAnswer(
                "null",
                "SELECT INSERT('abc', 1, NULL, 'x')");
            assertAnswer(
                "null",
                "SELECT INSERT('abc', 1, 1, NULL)");
            assertAnswer(
                "null",
                "SELECT SPACE(NULL)");
            assertAnswer(
                "null",
                "SELECT TRANSLATE('abc', NULL, 'x')");
            assertAnswer(
                "null",
                "SELECT TRANSLATE('abc', 'a', NULL)");
            assertAnswer(
                "null",
                "SELECT REPLACE('abc', NULL)");
            assertAnswer(
                "null",
                "SELECT REPLACE('abc', 'a', NULL)");
            assertAnswer(
                "null",
                "SELECT REPLACE('abc', NULL, 'x')");
            assertAnswer(
                "null",
                "SELECT CONCAT_WS(NULL, 'a', 'b')");
            assertAnswer(
                "null",
                "SELECT CONCAT_WS(',', 'a', NULL)");
            assertAnswer(
                "null",
                "SELECT LTRIM('abc', NULL)");
            assertAnswer(
                "null",
                "SELECT RTRIM('abc', NULL)");
            assertAnswer(
                "null",
                "SELECT TRIM('abc', NULL)");
            assertAnswer(
                "null",
                "SELECT STARTSWITH('abc', NULL)");
            assertAnswer(
                "null",
                "SELECT ENDSWITH('abc', NULL)");
            assertAnswer(
                "null",
                "SELECT CONTAINS('abc', NULL)");
            assertAnswer(
                "null",
                "SELECT CHR(NULL)");
            assertAnswer(
                "null",
                "SELECT ROUND(1.5, NULL)");
            assertAnswer(
                "null",
                "SELECT TRUNC(1.5, NULL)");
            assertAnswer(
                "null",
                "SELECT POWER(2, NULL)");
            assertAnswer(
                "null",
                "SELECT MOD(5, NULL)");
            assertAnswer(
                "null",
                "SELECT LOG(NULL, 2)");
            assertAnswer(
                "null",
                "SELECT LOG(2, NULL)");
            assertAnswer(
                "null",
                "SELECT LEAST(1, NULL)");
            assertAnswer(
                "null",
                "SELECT GREATEST(1, NULL)");
            assertAnswer(
                "null",
                "SELECT DATEADD(DAY, NULL, CURRENT_DATE)");
            assertAnswer(
                "null",
                "SELECT DATEADD(DAY, 1, NULL)");
            assertAnswer(
                "null",
                "SELECT DATE_FROM_PARTS(2024, NULL, 1)");
            assertAnswer(
                "null",
                "SELECT TIME_FROM_PARTS(1, NULL, 1)");
            assertAnswer(
                "null",
                "SELECT TIMESTAMP_FROM_PARTS(2024, 1, 1, NULL, 0, 0)");
            assertAnswer(
                "null",
                "SELECT ADD_MONTHS('2024-01-01'::DATE, NULL)");
            assertAnswer(
                "null",
                "SELECT ADD_MONTHS(NULL, 1)");
            assertAnswer(
                "null",
                "SELECT BITSHIFTLEFT(1, NULL)");
            assertAnswer(
                "null",
                "SELECT BITAND(1, NULL)");
            assertAnswer(
                "null",
                "SELECT ARRAY_POSITION(NULL, ARRAY_CONSTRUCT(1))");
            assertAnswer(
                "null",
                "SELECT ARRAY_CONTAINS(NULL, ARRAY_CONSTRUCT(1))");
            assertAnswer(
                "null",
                "SELECT ARRAY_INSERT(ARRAY_CONSTRUCT(1), NULL, 2)");
            assertAnswer(
                "null",
                "SELECT ARRAY_REMOVE_AT(ARRAY_CONSTRUCT(1), NULL)");
            assertAnswer(
                "null",
                "SELECT ARRAY_GENERATE_RANGE(0, NULL)");
            assertAnswer(
                "null",
                "SELECT ARRAY_GENERATE_RANGE(NULL, 3)");
            assertAnswer(
                "null",
                "SELECT ARRAY_SIZE(NULL)");
            assertAnswer(
                "null",
                "SELECT SPLIT('a,b', NULL)");
            assertAnswer(
                "null",
                "SELECT SPLIT(NULL, ',')");
            assertAnswer(
                "null",
                "SELECT EDITDISTANCE('a', NULL)");
            assertAnswer(
                "null",
                "SELECT EDITDISTANCE('a', 'b', NULL)");
            assertAnswer(
                "null",
                "SELECT JAROWINKLER_SIMILARITY('a', NULL)");
            assertAnswer(
                "null",
                "SELECT INITCAP('a b', NULL)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 2 to function TO_NUMBER needs to be an integer, found: "
                    + "'null'",
                "SELECT TO_NUMBER('1', NULL)");
            assertAnswer(
                "REFUSED SQL compilation error: | Format argument for function 'TO_DATE' needs to be a string",
                "SELECT TO_DATE('2024-01-01', NULL)");
            assertAnswer(
                "null",
                "SELECT LIKE('abc', NULL)");
            assertAnswer(
                "null",
                "SELECT 'abc' LIKE NULL");
            assertAnswer(
                "null",
                "SELECT 'abc' LIKE 'a%' ESCAPE NULL");
            assertAnswer(
                "null",
                "SELECT ILIKE('abc', NULL)");
            assertAnswer(
                "null",
                "SELECT HEX_ENCODE('a', NULL)");
            assertAnswer(
                "null",
                "SELECT BASE64_ENCODE('a', NULL)");
            assertAnswer(
                "null",
                "SELECT DATE_TRUNC(NULL, CURRENT_DATE)");
            assertAnswer(
                "null",
                "SELECT NEXT_DAY(CURRENT_DATE, NULL)");
            assertAnswer(
                "null",
                "SELECT DATEDIFF(DAY, NULL, CURRENT_DATE)");
            assertAnswer(
                "null",
                "SELECT TIME_SLICE(CURRENT_TIMESTAMP, NULL, 'HOUR')");
            assertAnswer(
                "null",
                "SELECT CONVERT_TIMEZONE(NULL, CURRENT_TIMESTAMP)");
            assertAnswer(
                "{\"a\":1}",
                "SELECT OBJECT_INSERT(OBJECT_CONSTRUCT('a', 1), NULL, 2)");
            assertAnswer(
                "{\"a\":1}",
                "SELECT OBJECT_DELETE(OBJECT_CONSTRUCT('a', 1), NULL)");
            assertAnswer(
                "null",
                "SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT(1, 2), NULL)");
            assertAnswer(
                "a",
                "SELECT LISTAGG('a', NULL)");
            assertAnswer(
                "null",
                "SELECT WIDTH_BUCKET(5, 0, 10, NULL)");
            assertAnswer(
                "null",
                "SELECT WIDTH_BUCKET(5, NULL, 10, 5)");
            assertAnswer(
                "null|null|null|null",
                "SELECT SUBSTR(s, n), LPAD(s, n), LEFT(s, n), SUBSTR(s, 1, n) FROM tq");
            assertAnswer(
                "null|null",
                "SELECT RPAD(s, n), RIGHT(s, n) FROM tq");
            assertAnswer(
                "null|null|null",
                "SELECT SPLIT_PART(s, 'b', n), STRTOK(s, 'b', n), POSITION('b', s, n) FROM tq");
            assertAnswer(
                "null|null|null|null",
                "SELECT REGEXP_SUBSTR(s, 'b', n), REGEXP_REPLACE(s, 'b', 'x', n), REGEXP_INSTR(s, 'b', n), "
                    + "REGEXP_COUNT(s, 'b', n) FROM tq");
            assertAnswer(
                "null|null",
                "SELECT ARRAY_SLICE(ARRAY_CONSTRUCT(1,2,3), n, 2), ARRAY_SLICE(ARRAY_CONSTRUCT(1,2,3), 0, n) FROM "
                    + "tq");
            assertAnswer(
                "null|null|null",
                "SELECT REPEAT(s, n), LPAD(s, 5, NULL::VARCHAR), INSERT(s, n, 1, s) FROM tq");
            assertShape(
                "SUBSTR('ABC', NULL):VARCHAR(3), LPAD('A', NULL):VARCHAR(134217728), LEFT('ABC', NULL):VARCHAR(3),"
                    + " SPLIT_PART('A,B', ',', NULL):VARCHAR(3)",
                "SELECT SUBSTR('abc', NULL), LPAD('a', NULL), LEFT('abc', NULL), SPLIT_PART('a,b', ',', NULL)");
            assertShape(
                "SUBSTR('ABC', 1, NULL):VARCHAR(3), LPAD('A', 3, NULL):VARCHAR(134217728), POSITION('B', 'ABC', "
                    + "NULL):NUMBER(9,0), STRTOK('A B', ' ', NULL):VARCHAR(3)",
                "SELECT SUBSTR('abc', 1, NULL), LPAD('a', 3, NULL), POSITION('b', 'abc', NULL), STRTOK('a b', ' ',"
                    + " NULL)");
            assertShape(
                "REGEXP_SUBSTR('ABC', 'B', NULL):VARCHAR(3), REGEXP_REPLACE('ABC', 'B', 'X', "
                    + "NULL):VARCHAR(134217728), ARRAY_SLICE(ARRAY_CONSTRUCT(1,2), NULL, 2):ARRAY",
                "SELECT REGEXP_SUBSTR('abc', 'b', NULL), REGEXP_REPLACE('abc', 'b', 'x', NULL), "
                    + "ARRAY_SLICE(ARRAY_CONSTRUCT(1,2), NULL, 2)");
            assertAnswer(
                "VARCHAR(3)[LOB]|VARCHAR(134217728)[LOB]|VARCHAR(3)[LOB]",
                "SELECT SYSTEM$TYPEOF(SUBSTR('abc', NULL)), SYSTEM$TYPEOF(LPAD('a', NULL)), "
                    + "SYSTEM$TYPEOF(LEFT('abc', NULL))");
            assertAnswer(
                "VARCHAR(3)[LOB]|VARCHAR(3)[LOB]|NUMBER(9,0)[SB4]",
                "SELECT SYSTEM$TYPEOF(SUBSTR('abc', 1, NULL)), SYSTEM$TYPEOF(SPLIT_PART('a,b', ',', NULL)), "
                    + "SYSTEM$TYPEOF(POSITION('b', 'abc', NULL))");
            assertAnswer(
                "null|null|null",
                "DECLARE s NUMBER(10,2); r RESULTSET; BEGIN r := (SELECT SUBSTR('abc', :s), LPAD('a', :s), "
                    + "LEFT('abc', :s)); RETURN TABLE(r); END;");
            assertAnswer(
                "null|null|null",
                "DECLARE s VARCHAR(10); r RESULTSET; BEGIN r := (SELECT SUBSTR('abc', :s), LPAD('a', :s), "
                    + "SPLIT_PART('a,b', ',', :s)); RETURN TABLE(r); END;");
            assertAnswer(
                "null|null",
                "DECLARE s NUMBER(10,2); r RESULTSET; BEGIN r := (SELECT ARRAY_SLICE(ARRAY_CONSTRUCT(1,2), :s, 2),"
                    + " SUBSTR('abc', 1, :s)); RETURN TABLE(r); END;");
            assertAnswer(
                "null",
                "DECLARE s NUMBER(10,2); BEGIN RETURN SUBSTR('abc', s); END;");
            assertAnswer(
                "null",
                "DECLARE s NUMBER(10,2); BEGIN RETURN LPAD('a', s); END;");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM tq WHERE SUBSTR(s, n) IS NULL");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tq WHERE LEFT(s, n) = 'a'");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void typesAndRowsOverANullArgument() {
        engine.execute(
            "CREATE OR REPLACE TABLE tq (s VARCHAR(10), n INT, d DATE)");
        engine.execute(
            "INSERT INTO tq VALUES ('abc', NULL, NULL)");
        engine.execute(
            "CREATE OR REPLACE TABLE tw (s VARCHAR(10), n NUMBER(10,2), d DATE, t TIMESTAMP_NTZ)");
        engine.execute(
            "INSERT INTO tw VALUES ('abc', 1.5, '2024-05-06', '2024-05-06 10:11:12')");
        try {
            assertShape(
                "SUBSTR('ABC', NULL):VARCHAR(3), LPAD('A', NULL):VARCHAR(134217728), LEFT('ABC', NULL):VARCHAR(3),"
                    + " RIGHT('ABC', NULL):VARCHAR(3), SPLIT_PART('A,B', ',', NULL):VARCHAR(3)",
                "SELECT SUBSTR('abc', NULL), LPAD('a', NULL), LEFT('abc', NULL), RIGHT('abc', NULL), "
                    + "SPLIT_PART('a,b', ',', NULL)");
            assertShape(
                "SUBSTR('ABC', 1, NULL):VARCHAR(3), LPAD('A', 3, NULL):VARCHAR(134217728), RPAD('A', 3, "
                    + "NULL):VARCHAR(134217728), POSITION('B', 'ABC', NULL):NUMBER(9,0), STRTOK('A B', ' ', "
                    + "NULL):VARCHAR(3)",
                "SELECT SUBSTR('abc', 1, NULL), LPAD('a', 3, NULL), RPAD('a', 3, NULL), POSITION('b', 'abc', "
                    + "NULL), STRTOK('a b', ' ', NULL)");
            assertShape(
                "REGEXP_SUBSTR('ABC', 'B', NULL):VARCHAR(3), REGEXP_REPLACE('ABC', 'B', 'X', "
                    + "NULL):VARCHAR(134217728), ARRAY_SLICE(ARRAY_CONSTRUCT(1,2), NULL, 2):ARRAY",
                "SELECT REGEXP_SUBSTR('abc', 'b', NULL), REGEXP_REPLACE('abc', 'b', 'x', NULL), "
                    + "ARRAY_SLICE(ARRAY_CONSTRUCT(1,2), NULL, 2)");
            assertShape(
                "REPLACE('ABC', 'A', NULL):VARCHAR(134217728), TRANSLATE('ABC', 'A', NULL):VARCHAR(134217728), "
                    + "LTRIM('ABC', NULL):VARCHAR(3), TRIM('ABC', NULL):VARCHAR(3), INITCAP('A B', NULL):VARCHAR(9)",
                "SELECT REPLACE('abc', 'a', NULL), TRANSLATE('abc', 'a', NULL), LTRIM('abc', NULL), TRIM('abc', "
                    + "NULL), INITCAP('a b', NULL)");
            assertShape(
                "SUBSTR(S, N):VARCHAR(10), LPAD(S, N):VARCHAR(134217728), LEFT(S, N):VARCHAR(10), REPLACE(S, 'A', "
                    + "NULL):VARCHAR(134217728)",
                "SELECT SUBSTR(s, n), LPAD(s, n), LEFT(s, n), REPLACE(s, 'a', NULL) FROM tq");
            assertAnswer(
                "VARCHAR(3)[LOB]",
                "SELECT SYSTEM$TYPEOF(SUBSTR('abc', NULL))");
            assertAnswer(
                "VARCHAR(134217728)[LOB]",
                "SELECT SYSTEM$TYPEOF(LPAD('a', NULL))");
            assertAnswer(
                "VARCHAR(3)[LOB]",
                "SELECT SYSTEM$TYPEOF(LEFT('abc', NULL))");
            assertAnswer(
                "VARCHAR(3)[LOB]",
                "SELECT SYSTEM$TYPEOF(SPLIT_PART('a,b', ',', NULL))");
            assertAnswer(
                "VARCHAR(3)[LOB]",
                "SELECT SYSTEM$TYPEOF(SUBSTR('abc', 1, NULL))");
            assertAnswer(
                "NUMBER(9,0)[SB4]",
                "SELECT SYSTEM$TYPEOF(POSITION('b', 'abc', NULL))");
            assertAnswer(
                "VARCHAR(3)[LOB]",
                "SELECT SYSTEM$TYPEOF(STRTOK('a b', ' ', NULL))");
            assertAnswer(
                "VARCHAR(3)[LOB]",
                "SELECT SYSTEM$TYPEOF(REGEXP_SUBSTR('abc', 'b', NULL))");
            assertAnswer(
                "VARCHAR[LOB]",
                "SELECT SYSTEM$TYPEOF(REPLACE('abc', 'a', NULL))");
            assertAnswer(
                "VARCHAR(3)[LOB]",
                "SELECT SYSTEM$TYPEOF(LTRIM('abc', NULL))");
            assertAnswer(
                "ARRAY[LOB]",
                "SELECT SYSTEM$TYPEOF(ARRAY_SLICE(ARRAY_CONSTRUCT(1,2), NULL, 2))");
            assertAnswer(
                "VARCHAR(8)[LOB]",
                "SELECT SYSTEM$TYPEOF(HEX_ENCODE('a', NULL))");
            assertAnswer(
                "VARCHAR(9)[LOB]",
                "SELECT SYSTEM$TYPEOF(INITCAP('a b', NULL))");
            assertAnswer(
                "null|bc|null|ab|  a",
                "SELECT SUBSTR('abc', NULL::INT, 1), SUBSTR('abc', '2'), SUBSTR('abc', NULL::VARCHAR, 1), "
                    + "LEFT('abc', '2'), LPAD('a', '3')");
            assertAnswer(
                "null|null|null",
                "SELECT CONVERT_TIMEZONE(NULL, '2024-01-01'::TIMESTAMP_NTZ), CONVERT_TIMEZONE('UTC', NULL), "
                    + "CONVERT_TIMEZONE('UTC', 'America/New_York', NULL)");
            assertAnswer(
                "null|null",
                "SELECT CONVERT_TIMEZONE('UTC', NULL, '2024-01-01'::TIMESTAMP_NTZ), CONVERT_TIMEZONE(NULL, 'UTC', "
                    + "'2024-01-01'::TIMESTAMP_NTZ)");
            assertAnswer(
                "null|null",
                "SELECT TIME_SLICE('2024-01-01 10:20:30'::TIMESTAMP_NTZ, NULL, 'HOUR'), "
                    + "TIME_SLICE(NULL::TIMESTAMP_NTZ, 1, 'HOUR')");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(TIME_SLICE('2024-01-01 10:20:30'::TIMESTAMP_NTZ, NULL, 'HOUR'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Function TIME_SLICE does not support TIMESTAMP_LTZ(9) argument "
                    + "type",
                "SELECT SYSTEM$TYPEOF(TIME_SLICE(CURRENT_TIMESTAMP, 1, 'HOUR'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Function TIME_SLICE does not support TIMESTAMP_LTZ(9) argument "
                    + "type",
                "SELECT TIME_SLICE(CURRENT_TIMESTAMP, 1, 'HOUR') IS NOT NULL");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(TIME_SLICE(CURRENT_TIMESTAMP, NULL, 'HOUR'))");
            assertAnswer(
                "null",
                "SELECT TIME_SLICE('2024-01-01 10:20:30'::TIMESTAMP_NTZ, 1, NULL)");
            assertAnswer(
                "null",
                "SELECT TIME_SLICE('2024-01-01'::DATE, NULL, 'DAY')");
            assertAnswer(
                "null|null|null",
                "SELECT REGEXP_LIKE('abc', NULL, 'i'), REGEXP_COUNT('abc', 'b', 1, NULL), REGEXP_INSTR('abc', 'b',"
                    + " 1, 1, 0, NULL)");
            assertAnswer(
                "null|null|null",
                "SELECT STRTOK_TO_ARRAY('a b', NULL), SPLIT('a,b', NULL), ARRAY_TO_STRING(ARRAY_CONSTRUCT(1, 2), "
                    + "NULL)");
            assertAnswer(
                "null|a|null|null|null|null",
                "SELECT CONCAT('a', NULL), CONCAT_WS(NULL, 'a'), 'a' || NULL, REVERSE(NULL), UPPER(NULL), "
                    + "LENGTH(NULL)");
            assertAnswer(
                "null|null|null|null|null",
                "SELECT LPAD(s, 5, NULL), RPAD(s, 5, NULL), SUBSTR(s, 1, NULL), TRIM(s, NULL), REPLACE(s, NULL, "
                    + "'x') FROM tw");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM tw WHERE REPLACE(s, 'a', NULL) IS NULL");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tw WHERE LTRIM(s, NULL) = 'abc'");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
