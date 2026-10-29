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
 * SPLIT_PART's part is a whole number, judged while the statement compiles and over a table with no row too:
 * an exact NUMBER with a fraction the plan knows — a literal, a column holding one value, a cast, an expression —
 * is refused, echoed as the plan prints it, where what is computed from literals is computed first, so a whole
 * number written with a fraction is read and a NULL answers NULL, and so is the one whole value a column's
 * statistics leave an expression (what the plan knows is in {@link SplitPartFoldedPartTest}). A
 * BOOLEAN, a date, a time, a timestamp and a BINARY are no part at all, refused at the call; a FLOAT, a text and
 * a VARIANT are read and rounded when the row is read (live-verified).
 */
public class SplitPartPartTest extends MeasuredCellsTestSupport {

    @Test
    public void aFractionWrittenOrReadFromAColumn() {
        answer(
            "CREATE OR REPLACE TABLE tp (n NUMBER(4,2), f FLOAT, i INT, s VARCHAR)");
        answer(
            "INSERT INTO tp VALUES (1.6, 1.6, 2, '1.6')");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'1.6'",
            "SELECT SPLIT_PART('a,b,c', ',', 1.6)");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', 2.0)");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', '1.6')");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'TP.N'",
            "SELECT SPLIT_PART('a,b,c', ',', n) FROM tp");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', f) FROM tp");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', i) FROM tp");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', s) FROM tp");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', 1.6::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'1.6'",
            "SELECT SPLIT_PART('a,b,c', ',', 1.6e0)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'CAST(1.6 AS NUMBER(4,2))'",
            "SELECT SPLIT_PART('a,b,c', ',', 1.6::NUMBER(4,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'-1.5'",
            "SELECT SPLIT_PART('a,b,c', ',', -1.5)");
        assertAnswer(
            "null",
            "SELECT SPLIT_PART('a,b,c', ',', NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'(CAST(1 AS NUMBER(3,1))) + 0.6'",
            "SELECT SPLIT_PART('a,b,c', ',', 1 + 0.6)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'1.6'",
            "SELECT SPLIT_PART('a,b,c', ',', 1.6) FROM tp WHERE 1 = 0");
        assertAnswer(
            "a",
            "SELECT SPLIT_PART('a,b,c', ',', 1.0)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'1.6'",
            "SELECT SPLIT_PART('a,b,c', ',', 16e-1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'1.6'",
            "SELECT SPLIT_PART('a,b,c', ',', (1.6))");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', ROUND(1.6))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SPLIT_PART': (VARCHAR(5), VARCHAR(1), BOOLEAN)",
            "SELECT SPLIT_PART('a,b,c', ',', TRUE)");
        assertAnswer(
            "REFUSED Numeric value 'a' is not recognized",
            "SELECT SPLIT_PART('a,b,c', ',', 'a')");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', 1.6::VARIANT)");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', n::INT) FROM tp");
    }

    @Test
    public void theOtherCountsRoundAFraction() {
        answer(
            "CREATE OR REPLACE TABLE tp (n NUMBER(4,2), f FLOAT, i INT, s VARCHAR)");
        answer(
            "INSERT INTO tp VALUES (1.6, 1.6, 2, '1.6')");
        assertAnswer(
            "a,b,c",
            "SELECT SPLIT_PART('a,b,c', 1.6, 1)");
        assertAnswer(
            "1",
            "SELECT SPLIT_PART(1.6, '.', 1)");
        assertAnswer(
            "b",
            "SELECT STRTOK('a b c', ' ', 1.6)");
        assertAnswer(
            "b",
            "SELECT STRTOK('a b c', ' ', n) FROM tp");
        assertAnswer(
            "bcdef",
            "SELECT SUBSTR('abcdef', 1.6)");
        assertAnswer(
            "ab",
            "SELECT LEFT('abcdef', 1.6)");
        assertAnswer(
            "aba",
            "SELECT REPEAT('ab', 1.6)");
        assertAnswer(
            "xxa",
            "SELECT LPAD('a', 2.6, 'x')");
        assertAnswer(
            "3",
            "SELECT GET(ARRAY_CONSTRUCT(1, 2, 3), 1.6)");
        assertAnswer(
            "[2]",
            "SELECT ARRAY_SLICE(ARRAY_CONSTRUCT(1, 2, 3), 0.6, 2)");
        assertAnswer(
            "axcdef",
            "SELECT INSERT('abcdef', 1.6, 1, 'x')");
        assertAnswer(
            "a",
            "SELECT REGEXP_SUBSTR('aaa', 'a', 1.6)");
        assertAnswer(
            "2",
            "SELECT CHARINDEX('a', 'aaa', 1.6)");
    }

    @Test
    public void castsCallsAndExpressions() {
        answer(
            "CREATE OR REPLACE TABLE tp (n NUMBER(4,2), f FLOAT, i INT, s VARCHAR)");
        answer(
            "INSERT INTO tp VALUES (1.6, 1.6, 2, '1.6')");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', 1.6::NUMBER(10,0))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'CAST(TP.N AS NUMBER(4,1))'",
            "SELECT SPLIT_PART('a,b,c', ',', n::NUMBER(4,1)) FROM tp");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'TP.N + (CAST(1 AS NUMBER(4,2)))'",
            "SELECT SPLIT_PART('a,b,c', ',', n + 1) FROM tp");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', i + 0.0) FROM tp");
        assertAnswer(
            "a",
            "SELECT SPLIT_PART('a,b,c', ',', TRUNC(1.6))");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', CEIL(n)) FROM tp");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'ABS(1.6)'",
            "SELECT SPLIT_PART('a,b,c', ',', ABS(1.6))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'IFF(CAST(TRUE AS BOOLEAN), 1.6, CAST(2 AS NUMBER(2,1)))'",
            "SELECT SPLIT_PART('a,b,c', ',', IFF(TRUE, 1.6, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'IFNULL(TP.N, CAST(1 AS NUMBER(4,2)))'",
            "SELECT SPLIT_PART('a,b,c', ',', COALESCE(n, 1)) FROM tp");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', 1.5 + 0.5)");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', 2.50 - 0.50)");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', -2.0)");
        assertAnswer(
            "a",
            "SELECT SPLIT_PART('a,b,c', ',', +1.0)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'1.6'",
            "SELECT SPLIT_PART('a,b,c', ',', 1.60)");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', 2.)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'0.6'",
            "SELECT SPLIT_PART('a,b,c', ',', .6)");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', 1.6::DECFLOAT)");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', 1.6::DOUBLE)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'(CAST(TP.N AS NUMBER(10,8))) / 1'",
            "SELECT SPLIT_PART('a,b,c', ',', n / 1) FROM tp");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', 4 / 2)");
    }

    @Test
    public void aggregatesColumnsAndNulls() {
        answer(
            "CREATE OR REPLACE TABLE tp (n NUMBER(4,2), f FLOAT, i INT, s VARCHAR)");
        answer(
            "INSERT INTO tp VALUES (1.6, 1.6, 2, '1.6')");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', SQRT(4))");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', LENGTH('ab'))");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', n::VARCHAR) FROM tp");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'GREATEST(1.6, CAST(1 AS NUMBER(2,1)))'",
            "SELECT SPLIT_PART('a,b,c', ',', GREATEST(1.6, 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'CASE_FLATTENED(CAST(TRUE AS BOOLEAN), 1.6, CAST(2 AS NUMBER(2,1)))'",
            "SELECT SPLIT_PART('a,b,c', ',', CASE WHEN TRUE THEN 1.6 ELSE 2 END)");
        assertAnswer(
            "null",
            "SELECT SPLIT_PART('a,b,c', ',', NULL::NUMBER(4,2))");
        assertAnswer(
            "null",
            "SELECT SPLIT_PART('a,b,c', ',', NULLIF(1.6, 1.6))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'MAX(TP.N)'",
            "SELECT SPLIT_PART('a,b,c', ',', MAX(n)) FROM tp");
        assertAnswer(
            "a",
            "SELECT SPLIT_PART('a,b,c', ',', COUNT(*)) FROM tp");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'\"values\".X'",
            "SELECT SPLIT_PART('a,b,c', ',', x) FROM (SELECT 1.6 AS x)");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', x) FROM (SELECT 2.0 AS x)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'TP.N'",
            "SELECT SPLIT_PART('a,b,c', ',', tp.n) FROM tp");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'TP.N'",
            "SELECT SPLIT_PART('a,b,c', ',', \"N\") FROM tp");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', 2.0::NUMBER(4,1))");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', 2::NUMBER(4,1))");
    }

    @Test
    public void whereTheRefusalStandsAndOtherTypes() {
        answer(
            "CREATE OR REPLACE TABLE tp (n NUMBER(4,2), f FLOAT, i INT, s VARCHAR)");
        answer(
            "INSERT INTO tp VALUES (1.6, 1.6, 2, '1.6')");
        assertAnswer(
            "b",
            "DECLARE p NUMBER DEFAULT 2; BEGIN RETURN SPLIT_PART('a,b,c', ',', :p); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 38 | invalid identifier 'NOSUCH_ZQ7'",
            "SELECT SPLIT_PART('a,b,c', ',', 1.6), nosuch_zq7 FROM tp");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'3.5'",
            "SELECT SPLIT_PART(1, 2, 3.5)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'1.6'",
            "SELECT 1 FROM tp WHERE SPLIT_PART('a,b,c', ',', 1.6) = 'a'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[SPLIT_PART('a,b,c', ',', 1.6, 4)] expected 3, got 4",
            "SELECT SPLIT_PART('a,b,c', ',', 1.6, 4)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'1.6'",
            "SELECT SPLIT_PART('a,b,c', ',', 1.6) IS NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'TP.N'",
            "SELECT UPPER(SPLIT_PART('a,b,c', ',', n)) FROM tp");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SPLIT_PART': (VARCHAR(5), VARCHAR(1), BOOLEAN)",
            "SELECT SPLIT_PART('a,b,c', ',', TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SPLIT_PART': (VARCHAR(5), VARCHAR(1), DATE)",
            "SELECT SPLIT_PART('a,b,c', ',', CURRENT_DATE())");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SPLIT_PART': (VARCHAR(5), VARCHAR(1), ARRAY)",
            "SELECT SPLIT_PART('a,b,c', ',', ARRAY_CONSTRUCT(1))");
        assertAnswer(
            "a,b,c",
            "SELECT SPLIT_PART('a,b,c', TRUE, 1)");
    }

    @Test
    public void whatIsComputedFromLiterals() {
        answer(
            "CREATE OR REPLACE TABLE tq (n2 NUMBER(4,2), n3 NUMBER(4,2), i INT)");
        answer(
            "INSERT INTO tq VALUES (0.02, 0.03, 2)");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', 0.5 * 4)");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', ABS(-2.0))");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', FLOOR(2.5))");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', IFF(TRUE, 2.0, 3.5))");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', COALESCE(NULL, 2.0))");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', NVL(2.0, 1.5))");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', GREATEST(2.0, 1.5))");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', LEAST(2.0, 2.5))");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', 2.0::NUMBER(5,3))");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', CAST('2.0' AS NUMBER(4,1)))");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', TO_NUMBER('2.0', 4, 1))");
        assertAnswer(
            "a",
            "SELECT SPLIT_PART('a,b,c', ',', 1.6 - 0.6)");
        assertAnswer(
            "c",
            "SELECT SPLIT_PART('a,b,c', ',', -1.0)");
        assertAnswer(
            "null",
            "SELECT SPLIT_PART('a,b,c', ',', 2.0 + NULL)");
        assertAnswer(
            "null",
            "SELECT SPLIT_PART('a,b,c', ',', NULL + 1.5)");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', DIV0(4.0, 2.0))");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', SQUARE(1.5))");
        assertAnswer(
            "c",
            "SELECT SPLIT_PART('a,b,c', ',', 2.5::INT)");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', CASE WHEN TRUE THEN 2.0 END)");
    }

    @Test
    public void theValueStatisticsLeaveAndOtherTypes() {
        answer(
            "CREATE OR REPLACE TABLE tq (n2 NUMBER(4,2), n3 NUMBER(4,2), i INT)");
        answer(
            "INSERT INTO tq VALUES (0.02, 0.03, 2)");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', n2 * 100) FROM tq");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', n2 + 1.98) FROM tq");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', MAX(n2) * 100) FROM tq");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', i + 0.0) FROM tq");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', i * 1.0) FROM tq");
        assertAnswer(
            "b",
            "SELECT SPLIT_PART('a,b,c', ',', i::NUMBER(4,1)) FROM tq");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SPLIT_PART': (VARCHAR(5), VARCHAR(1), TIMESTAMP_LTZ(9))",
            "SELECT SPLIT_PART('a,b,c', ',', CURRENT_TIMESTAMP())");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SPLIT_PART': (VARCHAR(5), VARCHAR(1), TIME(9))",
            "SELECT SPLIT_PART('a,b,c', ',', '12:00'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SPLIT_PART': (VARCHAR(5), VARCHAR(1), BINARY(67108864))",
            "SELECT SPLIT_PART('a,b,c', ',', TO_BINARY('AB'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SPLIT_PART': (VARCHAR(5), VARCHAR(1), OBJECT)",
            "SELECT SPLIT_PART('a,b,c', ',', OBJECT_CONSTRUCT())");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SPLIT_PART': (VARCHAR(5), VARCHAR(1), DATE)",
            "SELECT SPLIT_PART('a,b,c', ',', '2024-01-02'::DATE)");
    }
}
