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
 * A position, a length or a count is a whole number whatever is written there. A text is read as the number it
 * spells, blanks around it and an exponent included, and refused on the row where it spells none; a fraction
 * goes to the nearest whole number, a half away from zero, in a decimal and a FLOAT alike. REPEAT rounds the
 * count times the text's length, so a fraction of the text repeats, and INSERT rounds its two cuts, not its
 * arguments. A negative start that counts back past the first character is empty whatever the length. A
 * BOOLEAN there is refused by the argument types of the call it is planned as. Every answer is live-verified.
 */
public class TextAndFractionCountsTest extends MeasuredCellsTestSupport {

    @Test
    public void aTextWhereAPositionOrACountStands() {
        engine.execute(
            "CREATE OR REPLACE TABLE tw (d DATE, t TIMESTAMP_NTZ, s VARCHAR(10), n NUMBER(10,2), i INT)");
        engine.execute(
            "INSERT INTO tw VALUES ('2024-05-06', '2024-05-06 10:11:12', 'abc', 1.5, 7), (NULL, NULL, NULL, NULL, "
                + "NULL)");
        try {
            assertAnswer(
                "bc",
                "SELECT SUBSTR('abc', '2')");
            assertAnswer(
                "b",
                "SELECT SUBSTR('abc', '2', '1')");
            assertAnswer(
                "REFUSED Numeric value 'x' is not recognized",
                "SELECT SUBSTR('abc', 'x')");
            assertAnswer(
                "REFUSED Numeric value 'x' is not recognized",
                "SELECT SUBSTR('abc', 1, 'x')");
            assertAnswer(
                "bcdef",
                "SELECT SUBSTR('abcdef', '1.6')");
            assertAnswer(
                "bcdef",
                "SELECT SUBSTR('abcdef', 1.6)");
            assertAnswer(
                "cd",
                "SELECT SUBSTR('abcdef', 2.5, 1.5)");
            assertAnswer(
                "cd",
                "SELECT SUBSTR('abcdef', '2.5', '1.5')");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'SUBSTR': (VARCHAR(3), BOOLEAN)",
                "SELECT SUBSTR('abc', TRUE)");
            assertAnswer(
                "bc",
                "SELECT SUBSTRING('abc', '2')");
            assertAnswer(
                "VARCHAR(3)[LOB]",
                "SELECT SYSTEM$TYPEOF(SUBSTR('abc', '2'))");
            assertAnswer(
                "REFUSED Numeric value 'abc' is not recognized",
                "SELECT SUBSTR(s, s) FROM tw");
            assertAnswer(
                "bc",
                "SELECT SUBSTR('abc', ' 2 ')");
            assertAnswer(
                "REFUSED Numeric value '' is not recognized",
                "SELECT SUBSTR('abc', '')");
            assertAnswer(
                "bc",
                "SELECT SUBSTR('abc', '2e0')");
            assertAnswer(
                "ab|bc|xxa|axx|aa|b",
                "SELECT LEFT('abc', '2'), RIGHT('abc', '2'), LPAD('a', '3', 'x'), RPAD('a', '3', 'x'), REPEAT('a',"
                    + " '2'), SPLIT_PART('a,b', ',', '2')");
            assertAnswer(
                "xbc|2|2|b",
                "SELECT INSERT('abc', '1', '1', 'x'), CHARINDEX('b', 'abc', '1'), POSITION('b', 'abc', '1'), "
                    + "REGEXP_SUBSTR('abc', 'b', '1')");
            assertAnswer(
                "REFUSED Numeric value 'x' is not recognized",
                "SELECT LEFT('abc', 'x')");
            assertAnswer(
                "REFUSED Numeric value 'x' is not recognized",
                "SELECT REPEAT('a', 'x')");
            assertAnswer(
                "REFUSED Numeric value 'x' is not recognized",
                "SELECT LPAD('a', 'x')");
            assertAnswer(
                "REFUSED Numeric value 'x' is not recognized",
                "SELECT SPLIT_PART('a,b', ',', 'x')");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void fractionsRoundAndTextsAreRead() {
        engine.execute(
            "CREATE OR REPLACE TABLE tf (s VARCHAR(10), n NUMBER(4,2), c VARCHAR(5))");
        engine.execute(
            "INSERT INTO tf VALUES ('abcdef', 1.5, '2'), ('abcdef', 2.4, ' 3 '), ('abcdef', NULL, NULL)");
        try {
            assertAnswer(
                "ab|ef|xxa|axx|aa",
                "SELECT LEFT('abcdef', 1.6), RIGHT('abcdef', 1.6), LPAD('a', 2.5, 'x'), RPAD('a', 2.5, 'x'), "
                    + "REPEAT('a', 1.6)");
            assertAnswer(
                "b",
                "SELECT SPLIT_PART('a,b,c', ',', '1.6')");
            assertAnswer(
                "b",
                "SELECT SPLIT_PART('a,b,c', ',', 2.0)");
            assertAnswer(
                "axcdef|5|5|bc",
                "SELECT INSERT('abcdef', 1.6, 1.6, 'x'), CHARINDEX('b', 'abcabc', 2.6), POSITION('b', 'abcabc', "
                    + "2.6), REGEXP_SUBSTR('abcabc', 'b.', 2.6)");
            assertAnswer(
                "xbc",
                "SELECT INSERT('abc', '1', '1', 'x')");
            assertAnswer(
                "2|2",
                "SELECT CHARINDEX('b', 'abc', '1'), POSITION('b', 'abc', '1')");
            assertAnswer(
                "b",
                "SELECT REGEXP_SUBSTR('abc', 'b', '1')");
            assertAnswer(
                "ab|ef|aa|xxa",
                "SELECT LEFT('abcdef', '1.6'), RIGHT('abcdef', ' 2 '), REPEAT('a', '1.6'), LPAD('a', '2.5', 'x')");
            assertAnswer(
                "ef|abcdef|b||bcd",
                "SELECT SUBSTR('abcdef', -1.5), SUBSTR('abcdef', 0.4), SUBSTR('abcdef', 2, 0.5), SUBSTR('abcdef', "
                    + "2, 0.4), SUBSTR('abcdef', 1.5::FLOAT, 2.5::FLOAT)");
            assertAnswer(
                "  ||  ||3|b|b",
                "SELECT SPACE('2') || '|', SPACE(1.6) || '|', LENGTH(SPACE(2.5)), STRTOK('a b c', ' ', '2'), "
                    + "STRTOK('a b c', ' ', 1.6)");
            assertAnswer(
                "[2]|[2,3]",
                "SELECT ARRAY_SLICE(ARRAY_CONSTRUCT(1,2,3), '1', '2'), ARRAY_SLICE(ARRAY_CONSTRUCT(1,2,3), 0.6, "
                    + "2.5)");
            assertAnswer(
                "bcdef|cdef|def|d",
                "SELECT SUBSTR('abcdef', 2.4), SUBSTR('abcdef', 2.5), SUBSTR('abcdef', -2.5), SUBSTR('abcdef', "
                    + "3.5, 1)");
            assertAnswer(
                "abc|ab||ababa|ab|abcxy",
                "SELECT LEFT('abcdef', 2.5), LEFT('abcdef', 2.4), RIGHT('abcdef', -0.5), REPEAT('ab', 2.5), "
                    + "LPAD('abc', 1.5), RPAD('abc', 4.5, 'xy')");
            assertAnswer(
                "||abc||",
                "SELECT SUBSTR('abc', 99999999999), SUBSTR('abc', -99999999999), LEFT('abc', 99999999999), "
                    + "REPEAT('a', 0), REPEAT('a', -1)");
            assertAnswer(
                "5|5",
                "SELECT POSITION('b', 'abcabc', '3'), CHARINDEX('b', 'abcabc', ' 3 ')");
            assertAnswer(
                "b|bc",
                "SELECT REGEXP_SUBSTR('abcabc', 'b', 1, '2'), REGEXP_SUBSTR('abcabc', 'b.', '3', 1.4)");
            assertAnswer(
                "REFUSED Numeric value 'x' is not recognized",
                "SELECT STRTOK('a b c', ' ', 'x')");
            assertAnswer(
                "REFUSED Numeric value 'x' is not recognized",
                "SELECT SPACE('x')");
            assertAnswer(
                "REFUSED Numeric value 'x' is not recognized",
                "SELECT INSERT('abc', 'x', 1, 'y')");
            assertAnswer(
                "REFUSED Numeric value 'x' is not recognized",
                "SELECT CHARINDEX('b', 'abc', 'x')");
            assertAnswer(
                "REFUSED Numeric value 'x' is not recognized",
                "SELECT ARRAY_SLICE(ARRAY_CONSTRUCT(1,2,3), 'x', 2)");
            assertAnswer(
                "bcdef|ab|aa|ab ;; bcdef|ab|aa|ab ;; null|null|null|null",
                "SELECT SUBSTR(s, n), LEFT(s, n), REPEAT('a', n), LPAD(s, n) FROM tf ORDER BY n");
            assertAnswer(
                "cdef|abc|def|aaa ;; bcdef|ab|ef|aa ;; null|null|null|null",
                "SELECT SUBSTR(s, c), LEFT(s, c), RIGHT(s, c), REPEAT('a', c) FROM tf ORDER BY c");
            assertAnswer(
                "VARCHAR(3)[LOB]|VARCHAR(3)[LOB]|VARCHAR(134217728)[LOB]",
                "SELECT SYSTEM$TYPEOF(SUBSTR('abc', 1.6)), SYSTEM$TYPEOF(LEFT('abc', '2')), "
                    + "SYSTEM$TYPEOF(REPEAT('a', '2'))");
            assertAnswer(
                "abxcdef|axdef|axdef",
                "SELECT INSERT('abcdef', 2.5, 0.5, 'x'), INSERT('abcdef', 2.4, 1.4, 'x'), INSERT('abcdef', '2', "
                    + "'2', 'x')");
            assertAnswer(
                "abcdef|cd|ab",
                "SELECT SUBSTR('abcdef', 1e0), SUBSTR('abcdef', 2.5e0, 1.5e0), LEFT('abcdef', 1.5e0)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'SUBSTR': (VARCHAR(3), NUMBER(1,0), BOOLEAN)",
                "SELECT LEFT('abc', TRUE)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'*': (BOOLEAN, NUMBER(18,0))",
                "SELECT REPEAT('a', TRUE)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'SUBSTR': (VARCHAR(3), NUMBER(1,0), BOOLEAN)",
                "SELECT SUBSTR('abc', 1, TRUE)");
            assertAnswer(
                "bc",
                "SELECT SUBSTR('abc', '2'::VARIANT)");
            assertAnswer(
                "ab",
                "SELECT LEFT('abc', 2::VARIANT)");
            assertAnswer(
                "bc",
                "SELECT SUBSTR('abc', TO_VARIANT(1.6))");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
