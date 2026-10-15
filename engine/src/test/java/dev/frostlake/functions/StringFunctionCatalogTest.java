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
 * The text functions the catalog lists, by what they answer and what they declare: case and length, character
 * codes, joining, cutting, padding and trimming, REPLACE, REVERSE, REPEAT, searching, splitting, digests,
 * pattern matches and the TRY_ decodings. A trim with no list takes the blank alone, an empty pattern replaces
 * nothing, part 0 of SPLIT_PART is part 1, and a code that names no character is refused (live-verified).
 */
public class StringFunctionCatalogTest extends MeasuredCellsTestSupport {

    @Test
    public void caseLengthAndCodes() {
        assertAnswer(
            "ABC|VARCHAR(9)[LOB]",
            "SELECT UPPER('aBc'), SYSTEM$TYPEOF(UPPER('aBc'))");
        assertAnswer(
            "abc|VARCHAR(3)[LOB]",
            "SELECT LOWER('aBc'), SYSTEM$TYPEOF(LOWER('aBc'))");
        assertAnswer(
            "Hello World-Foo Bar_Baz|VARCHAR(69)[LOB]",
            "SELECT INITCAP('hello wORLD-foo bar_baz'), SYSTEM$TYPEOF(INITCAP('hello wORLD-foo bar_baz'))");
        assertAnswer(
            "null|VARCHAR(134217728)[LOB]",
            "SELECT UPPER(NULL), SYSTEM$TYPEOF(UPPER(NULL))");
        assertAnswer(
            "123|VARCHAR(134217728)[LOB]",
            "SELECT UPPER(123), SYSTEM$TYPEOF(UPPER(123))");
        assertAnswer(
            "Hello world|VARCHAR(33)[LOB]",
            "SELECT INITCAP('hello world', ''), SYSTEM$TYPEOF(INITCAP('hello world', ''))");
        assertAnswer(
            "STRASSE|VARCHAR(18)[LOB]",
            "SELECT UPPER('stra\u00dfe'), SYSTEM$TYPEOF(UPPER('stra\u00dfe'))");
        assertAnswer(
            "true|VARCHAR(134217728)[LOB]",
            "SELECT LOWER(TRUE), SYSTEM$TYPEOF(LOWER(TRUE))");
        assertAnswer(
            "3|NUMBER(18,0)[SB8]",
            "SELECT LENGTH('abc'), SYSTEM$TYPEOF(LENGTH('abc'))");
        assertAnswer(
            "3|NUMBER(18,0)[SB8]",
            "SELECT LEN('abc'), SYSTEM$TYPEOF(LEN('abc'))");
        assertAnswer(
            "0|NUMBER(18,0)[SB8]",
            "SELECT LENGTH(''), SYSTEM$TYPEOF(LENGTH(''))");
        assertAnswer(
            "null|NUMBER(18,0)[SB1]",
            "SELECT LENGTH(NULL), SYSTEM$TYPEOF(LENGTH(NULL))");
        assertAnswer(
            "5|NUMBER(18,0)[SB8]",
            "SELECT LENGTH(12345), SYSTEM$TYPEOF(LENGTH(12345))");
        assertAnswer(
            "2|NUMBER(18,0)[SB8]",
            "SELECT LENGTH(TO_BINARY('ABCD', 'HEX')), SYSTEM$TYPEOF(LENGTH(TO_BINARY('ABCD', 'HEX')))");
        assertAnswer(
            "1|NUMBER(18,0)[SB8]",
            "SELECT LENGTH('\u00e9'), SYSTEM$TYPEOF(LENGTH('\u00e9'))");
        assertAnswer(
            "3|NUMBER(18,0)[SB8]",
            "SELECT OCTET_LENGTH('abc'), SYSTEM$TYPEOF(OCTET_LENGTH('abc'))");
        assertAnswer(
            "2|NUMBER(18,0)[SB8]",
            "SELECT OCTET_LENGTH('\u00e9'), SYSTEM$TYPEOF(OCTET_LENGTH('\u00e9'))");
        assertAnswer(
            "24|NUMBER(19,0)[SB16]",
            "SELECT BIT_LENGTH('abc'), SYSTEM$TYPEOF(BIT_LENGTH('abc'))");
        assertAnswer(
            "16|NUMBER(19,0)[SB16]",
            "SELECT BIT_LENGTH('\u00e9'), SYSTEM$TYPEOF(BIT_LENGTH('\u00e9'))");
        assertAnswer(
            "null|NUMBER(18,0)[SB1]",
            "SELECT OCTET_LENGTH(NULL), SYSTEM$TYPEOF(OCTET_LENGTH(NULL))");
        assertAnswer(
            "16|NUMBER(19,0)[SB16]",
            "SELECT BIT_LENGTH(TO_BINARY('ABCD', 'HEX')), SYSTEM$TYPEOF(BIT_LENGTH(TO_BINARY('ABCD', 'HEX')))");
        assertAnswer(
            "97|NUMBER(4,0)[SB2]",
            "SELECT ASCII('a'), SYSTEM$TYPEOF(ASCII('a'))");
        assertAnswer(
            "0|NUMBER(4,0)[SB2]",
            "SELECT ASCII(''), SYSTEM$TYPEOF(ASCII(''))");
        assertAnswer(
            "195|NUMBER(4,0)[SB2]",
            "SELECT ASCII('\u00e9'), SYSTEM$TYPEOF(ASCII('\u00e9'))");
        assertAnswer(
            "97|NUMBER(18,0)[SB8]",
            "SELECT UNICODE('a'), SYSTEM$TYPEOF(UNICODE('a'))");
        assertAnswer(
            "233|NUMBER(18,0)[SB8]",
            "SELECT UNICODE('\u00e9'), SYSTEM$TYPEOF(UNICODE('\u00e9'))");
        assertAnswer(
            "0|NUMBER(18,0)[SB8]",
            "SELECT UNICODE(''), SYSTEM$TYPEOF(UNICODE(''))");
        assertAnswer(
            "null|NUMBER(4,0)[SB1]",
            "SELECT ASCII(NULL), SYSTEM$TYPEOF(ASCII(NULL))");
        assertAnswer(
            "97|NUMBER(4,0)[SB2]",
            "SELECT ASCII('abc'), SYSTEM$TYPEOF(ASCII('abc'))");
        assertAnswer(
            "a|VARCHAR(1)[LOB]",
            "SELECT CHR(97), SYSTEM$TYPEOF(CHR(97))");
        assertAnswer(
            "a|VARCHAR(1)[LOB]",
            "SELECT CHAR(97), SYSTEM$TYPEOF(CHAR(97))");
        assertAnswer(
            "\u00e9|VARCHAR(1)[LOB]",
            "SELECT CHR(233), SYSTEM$TYPEOF(CHR(233))");
        assertAnswer(
            "\u0000|VARCHAR(1)[LOB]",
            "SELECT CHR(0), SYSTEM$TYPEOF(CHR(0))");
        assertAnswer(
            "REFUSED Invalid character code -1 in the CHR input",
            "SELECT CHR(-1), SYSTEM$TYPEOF(CHR(-1))");
        assertAnswer(
            "REFUSED Invalid character code 1114112 in the CHR input",
            "SELECT CHR(1114112), SYSTEM$TYPEOF(CHR(1114112))");
        assertAnswer(
            "null|VARCHAR(1)[LOB]",
            "SELECT CHR(NULL), SYSTEM$TYPEOF(CHR(NULL))");
        assertAnswer(
            "b|VARCHAR(1)[LOB]",
            "SELECT CHR(97.6), SYSTEM$TYPEOF(CHR(97.6))");
    }

    @Test
    public void joiningCuttingAndPadding() {
        assertAnswer(
            "abc|VARCHAR(3)[LOB]",
            "SELECT CONCAT('a', 'b', 'c'), SYSTEM$TYPEOF(CONCAT('a', 'b', 'c'))");
        assertAnswer(
            "null|VARCHAR(134217728)[LOB]",
            "SELECT CONCAT('a', NULL), SYSTEM$TYPEOF(CONCAT('a', NULL))");
        assertAnswer(
            "a1true|VARCHAR(134217728)[LOB]",
            "SELECT CONCAT('a', 1, TRUE), SYSTEM$TYPEOF(CONCAT('a', 1, TRUE))");
        assertAnswer(
            "a|VARCHAR(1)[LOB]",
            "SELECT CONCAT('a'), SYSTEM$TYPEOF(CONCAT('a'))");
        assertAnswer(
            "ABCD|BINARY[LOB]",
            "SELECT CONCAT(TO_BINARY('AB', 'HEX'), TO_BINARY('CD', 'HEX')), SYSTEM$TYPEOF(CONCAT(TO_BINARY('AB', "
                + "'HEX'), TO_BINARY('CD', 'HEX')))");
        assertAnswer(
            "a2024-01-02|VARCHAR(134217728)[LOB]",
            "SELECT CONCAT('a', '2024-01-02'::DATE), SYSTEM$TYPEOF(CONCAT('a', '2024-01-02'::DATE))");
        assertAnswer(
            "bcd|VARCHAR(6)[LOB]",
            "SELECT SUBSTR('abcdef', 2, 3), SYSTEM$TYPEOF(SUBSTR('abcdef', 2, 3))");
        assertAnswer(
            "bcdef|VARCHAR(6)[LOB]",
            "SELECT SUBSTRING('abcdef', 2), SYSTEM$TYPEOF(SUBSTRING('abcdef', 2))");
        assertAnswer(
            "ab|VARCHAR(6)[LOB]",
            "SELECT SUBSTR('abcdef', 0, 2), SYSTEM$TYPEOF(SUBSTR('abcdef', 0, 2))");
        assertAnswer(
            "ef|VARCHAR(6)[LOB]",
            "SELECT SUBSTR('abcdef', -2), SYSTEM$TYPEOF(SUBSTR('abcdef', -2))");
        assertAnswer(
            "|VARCHAR(6)[LOB]",
            "SELECT SUBSTR('abcdef', 2, -1), SYSTEM$TYPEOF(SUBSTR('abcdef', 2, -1))");
        assertAnswer(
            "|VARCHAR(6)[LOB]",
            "SELECT SUBSTR('abcdef', 10), SYSTEM$TYPEOF(SUBSTR('abcdef', 10))");
        assertAnswer(
            "null|VARCHAR(134217728)[LOB]",
            "SELECT SUBSTR(NULL, 1), SYSTEM$TYPEOF(SUBSTR(NULL, 1))");
        assertAnswer(
            "|VARCHAR(6)[LOB]",
            "SELECT SUBSTR('abcdef', 2, 0), SYSTEM$TYPEOF(SUBSTR('abcdef', 2, 0))");
        assertAnswer(
            "234|VARCHAR(134217728)[LOB]",
            "SELECT SUBSTR(123456, 2, 3), SYSTEM$TYPEOF(SUBSTR(123456, 2, 3))");
        assertAnswer(
            "xxa|VARCHAR(134217728)[LOB]",
            "SELECT LPAD('a', 3, 'x'), SYSTEM$TYPEOF(LPAD('a', 3, 'x'))");
        assertAnswer(
            "axy|VARCHAR(134217728)[LOB]",
            "SELECT RPAD('a', 3, 'xy'), SYSTEM$TYPEOF(RPAD('a', 3, 'xy'))");
        assertAnswer(
            "ab|VARCHAR(134217728)[LOB]",
            "SELECT LPAD('abc', 2, 'x'), SYSTEM$TYPEOF(LPAD('abc', 2, 'x'))");
        assertAnswer(
            "  a|VARCHAR(134217728)[LOB]",
            "SELECT LPAD('a', 3), SYSTEM$TYPEOF(LPAD('a', 3))");
        assertAnswer(
            "a|VARCHAR(134217728)[LOB]",
            "SELECT LPAD('a', 3, ''), SYSTEM$TYPEOF(LPAD('a', 3, ''))");
        assertAnswer(
            "|VARCHAR(134217728)[LOB]",
            "SELECT LPAD('a', 0, 'x'), SYSTEM$TYPEOF(LPAD('a', 0, 'x'))");
        assertAnswer(
            "|VARCHAR(134217728)[LOB]",
            "SELECT LPAD('a', -1, 'x'), SYSTEM$TYPEOF(LPAD('a', -1, 'x'))");
        assertAnswer(
            "null|VARCHAR(134217728)[LOB]",
            "SELECT RPAD(NULL, 3, 'x'), SYSTEM$TYPEOF(RPAD(NULL, 3, 'x'))");
    }

    @Test
    public void trimmingReplacingAndRepeating() {
        assertAnswer(
            "a|VARCHAR(5)[LOB]",
            "SELECT TRIM('  a  '), SYSTEM$TYPEOF(TRIM('  a  '))");
        assertAnswer(
            "a  |VARCHAR(5)[LOB]",
            "SELECT LTRIM('  a  '), SYSTEM$TYPEOF(LTRIM('  a  '))");
        assertAnswer(
            "  a|VARCHAR(5)[LOB]",
            "SELECT RTRIM('  a  '), SYSTEM$TYPEOF(RTRIM('  a  '))");
        assertAnswer(
            "a|VARCHAR(5)[LOB]",
            "SELECT TRIM('xxaxx', 'x'), SYSTEM$TYPEOF(TRIM('xxaxx', 'x'))");
        assertAnswer(
            "axy|VARCHAR(5)[LOB]",
            "SELECT LTRIM('xyaxy', 'xy'), SYSTEM$TYPEOF(LTRIM('xyaxy', 'xy'))");
        assertAnswer(
            "a|VARCHAR(1)[LOB]",
            "SELECT TRIM('a', ''), SYSTEM$TYPEOF(TRIM('a', ''))");
        assertAnswer(
            "null|VARCHAR(134217728)[LOB]",
            "SELECT TRIM(NULL), SYSTEM$TYPEOF(TRIM(NULL))");
        assertAnswer(
            "null|VARCHAR(3)[LOB]",
            "SELECT TRIM(' a ', NULL), SYSTEM$TYPEOF(TRIM(' a ', NULL))");
        assertAnswer(
            "3|NUMBER(18,0)[SB8]",
            "SELECT LENGTH(TRIM(CHR(9) || 'a' || CHR(10))), SYSTEM$TYPEOF(LENGTH(TRIM(CHR(9) || 'a' || CHR(10))))");
        assertAnswer(
            "axcaxc|VARCHAR[LOB]",
            "SELECT REPLACE('abcabc', 'b', 'x'), SYSTEM$TYPEOF(REPLACE('abcabc', 'b', 'x'))");
        assertAnswer(
            "acac|VARCHAR[LOB]",
            "SELECT REPLACE('abcabc', 'b'), SYSTEM$TYPEOF(REPLACE('abcabc', 'b'))");
        assertAnswer(
            "abc|VARCHAR[LOB]",
            "SELECT REPLACE('abc', '', 'x'), SYSTEM$TYPEOF(REPLACE('abc', '', 'x'))");
        assertAnswer(
            "null|VARCHAR[LOB]",
            "SELECT REPLACE('abc', 'b', NULL), SYSTEM$TYPEOF(REPLACE('abc', 'b', NULL))");
        assertAnswer(
            "null|VARCHAR[LOB]",
            "SELECT REPLACE(NULL, 'a', 'b'), SYSTEM$TYPEOF(REPLACE(NULL, 'a', 'b'))");
        assertAnswer(
            "ba|VARCHAR[LOB]",
            "SELECT REPLACE('aaa', 'aa', 'b'), SYSTEM$TYPEOF(REPLACE('aaa', 'aa', 'b'))");
        assertAnswer(
            "abc|VARCHAR[LOB]",
            "SELECT REPLACE('abc', 'B', 'x'), SYSTEM$TYPEOF(REPLACE('abc', 'B', 'x'))");
        assertAnswer(
            "cba|VARCHAR(3)[LOB]",
            "SELECT REVERSE('abc'), SYSTEM$TYPEOF(REVERSE('abc'))");
        assertAnswer(
            "|VARCHAR(1)[LOB]",
            "SELECT REVERSE(''), SYSTEM$TYPEOF(REVERSE(''))");
        assertAnswer(
            "null|VARCHAR(134217728)[LOB]",
            "SELECT REVERSE(NULL), SYSTEM$TYPEOF(REVERSE(NULL))");
        assertAnswer(
            "321|VARCHAR(134217728)[LOB]",
            "SELECT REVERSE(123), SYSTEM$TYPEOF(REVERSE(123))");
        assertAnswer(
            "CDAB|BINARY[LOB]",
            "SELECT REVERSE(TO_BINARY('ABCD', 'HEX')), SYSTEM$TYPEOF(REVERSE(TO_BINARY('ABCD', 'HEX')))");
        assertAnswer(
            "20-10-4202|VARCHAR(134217728)[LOB]",
            "SELECT REVERSE('2024-01-02'::DATE), SYSTEM$TYPEOF(REVERSE('2024-01-02'::DATE))");
        assertAnswer(
            "ababab|VARCHAR(134217728)[LOB]",
            "SELECT REPEAT('ab', 3), SYSTEM$TYPEOF(REPEAT('ab', 3))");
        assertAnswer(
            "|VARCHAR(134217728)[LOB]",
            "SELECT REPEAT('ab', 0), SYSTEM$TYPEOF(REPEAT('ab', 0))");
        assertAnswer(
            "|VARCHAR(134217728)[LOB]",
            "SELECT REPEAT('ab', -1), SYSTEM$TYPEOF(REPEAT('ab', -1))");
        assertAnswer(
            "null|VARCHAR(134217728)[LOB]",
            "SELECT REPEAT('ab', NULL), SYSTEM$TYPEOF(REPEAT('ab', NULL))");
        assertAnswer(
            "[   ]|VARCHAR(134217728)[LOB]",
            "SELECT '[' || SPACE(3) || ']', SYSTEM$TYPEOF('[' || SPACE(3) || ']')");
        assertAnswer(
            "[]|VARCHAR(134217728)[LOB]",
            "SELECT '[' || SPACE(0) || ']', SYSTEM$TYPEOF('[' || SPACE(0) || ']')");
        assertAnswer(
            "null|VARCHAR(134217728)[LOB]",
            "SELECT SPACE(NULL), SYSTEM$TYPEOF(SPACE(NULL))");
        assertAnswer(
            "[]|VARCHAR(134217728)[LOB]",
            "SELECT '[' || SPACE(-1) || ']', SYSTEM$TYPEOF('[' || SPACE(-1) || ']')");
    }

    @Test
    public void searchingAndSplitting() {
        assertAnswer(
            "2|NUMBER(9,0)[SB4]",
            "SELECT CHARINDEX('b', 'abcabc'), SYSTEM$TYPEOF(CHARINDEX('b', 'abcabc'))");
        assertAnswer(
            "5|NUMBER(9,0)[SB4]",
            "SELECT CHARINDEX('b', 'abcabc', 3), SYSTEM$TYPEOF(CHARINDEX('b', 'abcabc', 3))");
        assertAnswer(
            "0|NUMBER(9,0)[SB4]",
            "SELECT CHARINDEX('z', 'abc'), SYSTEM$TYPEOF(CHARINDEX('z', 'abc'))");
        assertAnswer(
            "1|NUMBER(9,0)[SB4]",
            "SELECT CHARINDEX('', 'abc'), SYSTEM$TYPEOF(CHARINDEX('', 'abc'))");
        assertAnswer(
            "2|NUMBER(9,0)[SB4]",
            "SELECT POSITION('b', 'abcabc'), SYSTEM$TYPEOF(POSITION('b', 'abcabc'))");
        assertAnswer(
            "2|NUMBER(9,0)[SB4]",
            "SELECT POSITION('b' IN 'abcabc'), SYSTEM$TYPEOF(POSITION('b' IN 'abcabc'))");
        assertAnswer(
            "null|NUMBER(9,0)[SB4]",
            "SELECT CHARINDEX('b', NULL), SYSTEM$TYPEOF(CHARINDEX('b', NULL))");
        assertAnswer(
            "2|NUMBER(9,0)[SB4]",
            "SELECT CHARINDEX('b', 'abc', 0), SYSTEM$TYPEOF(CHARINDEX('b', 'abc', 0))");
        assertAnswer(
            "0|NUMBER(9,0)[SB4]",
            "SELECT CHARINDEX('b', 'abc', 10), SYSTEM$TYPEOF(CHARINDEX('b', 'abc', 10))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT CONTAINS('abc', 'b'), SYSTEM$TYPEOF(CONTAINS('abc', 'b'))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT STARTSWITH('abc', 'ab'), SYSTEM$TYPEOF(STARTSWITH('abc', 'ab'))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT ENDSWITH('abc', 'bc'), SYSTEM$TYPEOF(ENDSWITH('abc', 'bc'))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT CONTAINS('abc', ''), SYSTEM$TYPEOF(CONTAINS('abc', ''))");
        assertAnswer(
            "false|BOOLEAN[ROWINDEX]",
            "SELECT CONTAINS('abc', 'B'), SYSTEM$TYPEOF(CONTAINS('abc', 'B'))");
        assertAnswer(
            "null|BOOLEAN[ROWINDEX]",
            "SELECT CONTAINS(NULL, 'a'), SYSTEM$TYPEOF(CONTAINS(NULL, 'a'))");
        assertAnswer(
            "null|BOOLEAN[ROWINDEX]",
            "SELECT STARTSWITH('abc', NULL), SYSTEM$TYPEOF(STARTSWITH('abc', NULL))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT CONTAINS(12345, 23), SYSTEM$TYPEOF(CONTAINS(12345, 23))");
        assertAnswer(
            "[\"a\",\"b\",\"c\"]|ARRAY[LOB]",
            "SELECT SPLIT('a,b,c', ','), SYSTEM$TYPEOF(SPLIT('a,b,c', ','))");
        assertAnswer(
            "[\"abc\"]|ARRAY[LOB]",
            "SELECT SPLIT('abc', ''), SYSTEM$TYPEOF(SPLIT('abc', ''))");
        assertAnswer(
            "[\"a,b\"]|ARRAY[LOB]",
            "SELECT SPLIT('a,b', 'x'), SYSTEM$TYPEOF(SPLIT('a,b', 'x'))");
        assertAnswer(
            "[\"\"]|ARRAY[LOB]",
            "SELECT SPLIT('', ','), SYSTEM$TYPEOF(SPLIT('', ','))");
        assertAnswer(
            "null|ARRAY[LOB]",
            "SELECT SPLIT(NULL, ','), SYSTEM$TYPEOF(SPLIT(NULL, ','))");
        assertAnswer(
            "null|ARRAY[LOB]",
            "SELECT SPLIT('a,b', NULL), SYSTEM$TYPEOF(SPLIT('a,b', NULL))");
        assertAnswer(
            "[\"a\",\"\",\"b\"]|ARRAY[LOB]",
            "SELECT SPLIT('a,,b', ','), SYSTEM$TYPEOF(SPLIT('a,,b', ','))");
        assertAnswer(
            "b|VARCHAR(5)[LOB]",
            "SELECT SPLIT_PART('a,b,c', ',', 2), SYSTEM$TYPEOF(SPLIT_PART('a,b,c', ',', 2))");
        assertAnswer(
            "c|VARCHAR(5)[LOB]",
            "SELECT SPLIT_PART('a,b,c', ',', -1), SYSTEM$TYPEOF(SPLIT_PART('a,b,c', ',', -1))");
        assertAnswer(
            "a|VARCHAR(5)[LOB]",
            "SELECT SPLIT_PART('a,b,c', ',', 0), SYSTEM$TYPEOF(SPLIT_PART('a,b,c', ',', 0))");
        assertAnswer(
            "|VARCHAR(5)[LOB]",
            "SELECT SPLIT_PART('a,b,c', ',', 5), SYSTEM$TYPEOF(SPLIT_PART('a,b,c', ',', 5))");
        assertAnswer(
            "a,b,c|VARCHAR(5)[LOB]",
            "SELECT SPLIT_PART('a,b,c', '', 1), SYSTEM$TYPEOF(SPLIT_PART('a,b,c', '', 1))");
        assertAnswer(
            "null|VARCHAR(134217728)[LOB]",
            "SELECT SPLIT_PART(NULL, ',', 1), SYSTEM$TYPEOF(SPLIT_PART(NULL, ',', 1))");
        assertAnswer(
            "null|VARCHAR(5)[LOB]",
            "SELECT SPLIT_PART('a,b,c', NULL, 1), SYSTEM$TYPEOF(SPLIT_PART('a,b,c', NULL, 1))");
        assertAnswer(
            "a|VARCHAR(5)[LOB]",
            "SELECT STRTOK('a b c'), SYSTEM$TYPEOF(STRTOK('a b c'))");
        assertAnswer(
            "b|VARCHAR(5)[LOB]",
            "SELECT STRTOK('a,b;c', ',;', 2), SYSTEM$TYPEOF(STRTOK('a,b;c', ',;', 2))");
        assertAnswer(
            "b|VARCHAR(4)[LOB]",
            "SELECT STRTOK('a,,b', ',', 2), SYSTEM$TYPEOF(STRTOK('a,,b', ',', 2))");
        assertAnswer(
            "null|VARCHAR(3)[LOB]",
            "SELECT STRTOK('a b', ' ', 5), SYSTEM$TYPEOF(STRTOK('a b', ' ', 5))");
        assertAnswer(
            "null|VARCHAR(3)[LOB]",
            "SELECT STRTOK('a b', ' ', 0), SYSTEM$TYPEOF(STRTOK('a b', ' ', 0))");
        assertAnswer(
            "null|VARCHAR(134217728)[LOB]",
            "SELECT STRTOK(NULL), SYSTEM$TYPEOF(STRTOK(NULL))");
        assertAnswer(
            "a b|VARCHAR(3)[LOB]",
            "SELECT STRTOK('a b', '', 1), SYSTEM$TYPEOF(STRTOK('a b', '', 1))");
    }

    @Test
    public void digestsPatternsAndDecodings() {
        assertAnswer(
            "0cc175b9c0f1b6a831c399e269772661|VARCHAR(32)[LOB]",
            "SELECT MD5('a'), SYSTEM$TYPEOF(MD5('a'))");
        assertAnswer(
            "86f7e437faa5a7fce15d1ddcb9eaeaea377667b8|VARCHAR(40)[LOB]",
            "SELECT SHA1('a'), SYSTEM$TYPEOF(SHA1('a'))");
        assertAnswer(
            "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb|VARCHAR(128)[LOB]",
            "SELECT SHA2('a'), SYSTEM$TYPEOF(SHA2('a'))");
        assertAnswer(
            "abd37534c7d9a2efb9465de931cd7055ffdb8879563ae98078d6d6d5|VARCHAR(128)[LOB]",
            "SELECT SHA2('a', 224), SYSTEM$TYPEOF(SHA2('a', 224))");
        assertAnswer(
            "1f40fc92da241694750979ee6cf582f2d5d7d28e18335de05abc54d0560e0f5302860c652bf08d560252aa5e74210546f369fbbbce8c12cfc7957b2652fe9a75|VARCHAR(128)[LOB]",
            "SELECT SHA2('a', 512), SYSTEM$TYPEOF(SHA2('a', 512))");
        assertAnswer(
            "null|VARCHAR(32)[LOB]",
            "SELECT MD5(NULL), SYSTEM$TYPEOF(MD5(NULL))");
        assertAnswer(
            "c4ca4238a0b923820dcc509a6f75849b|VARCHAR(32)[LOB]",
            "SELECT MD5(1), SYSTEM$TYPEOF(MD5(1))");
        assertAnswer(
            "REFUSED Invalid parameter value: 100. Reason: invalid SHA2 digest size",
            "SELECT SHA2('a', 100), SYSTEM$TYPEOF(SHA2('a', 100))");
        assertAnswer(
            "0cc175b9c0f1b6a831c399e269772661|VARCHAR(32)[LOB]",
            "SELECT MD5_HEX('a'), SYSTEM$TYPEOF(MD5_HEX('a'))");
        assertAnswer(
            "86f7e437faa5a7fce15d1ddcb9eaeaea377667b8|VARCHAR(40)[LOB]",
            "SELECT SHA1_HEX('a'), SYSTEM$TYPEOF(SHA1_HEX('a'))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT REGEXP_LIKE('abc', 'a.c'), SYSTEM$TYPEOF(REGEXP_LIKE('abc', 'a.c'))");
        assertAnswer(
            "false|BOOLEAN[ROWINDEX]",
            "SELECT REGEXP_LIKE('abc', 'b'), SYSTEM$TYPEOF(REGEXP_LIKE('abc', 'b'))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT REGEXP_LIKE('ABC', 'abc', 'i'), SYSTEM$TYPEOF(REGEXP_LIKE('ABC', 'abc', 'i'))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT 'abc' REGEXP 'a.*', SYSTEM$TYPEOF('abc' REGEXP 'a.*')");
        assertAnswer(
            "null|BOOLEAN[ROWINDEX]",
            "SELECT REGEXP_LIKE(NULL, 'a'), SYSTEM$TYPEOF(REGEXP_LIKE(NULL, 'a'))");
        assertAnswer(
            "null|BOOLEAN[ROWINDEX]",
            "SELECT REGEXP_LIKE('abc', NULL), SYSTEM$TYPEOF(REGEXP_LIKE('abc', NULL))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT REGEXP_LIKE('a\\nb', 'a.b', 's'), SYSTEM$TYPEOF(REGEXP_LIKE('a\\nb', 'a.b', 's'))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT 'abc' LIKE ANY ('a%', 'z%'), SYSTEM$TYPEOF('abc' LIKE ANY ('a%', 'z%'))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT 'abc' LIKE ALL ('a%', '%c'), SYSTEM$TYPEOF('abc' LIKE ALL ('a%', '%c'))");
        assertAnswer(
            "false|BOOLEAN[ROWINDEX]",
            "SELECT 'abc' LIKE ALL ('a%', 'z%'), SYSTEM$TYPEOF('abc' LIKE ALL ('a%', 'z%'))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT 'ABC' ILIKE ANY ('a%', 'z%'), SYSTEM$TYPEOF('ABC' ILIKE ANY ('a%', 'z%'))");
        assertAnswer(
            "false|BOOLEAN[ROWINDEX]",
            "SELECT 'abc' LIKE ANY ('z%', NULL), SYSTEM$TYPEOF('abc' LIKE ANY ('z%', NULL))");
        assertAnswer(
            "null|BOOLEAN[ROWINDEX]",
            "SELECT NULL LIKE ANY ('a%'), SYSTEM$TYPEOF(NULL LIKE ANY ('a%'))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT 'abc' LIKE ANY ('a%', NULL), SYSTEM$TYPEOF('abc' LIKE ANY ('a%', NULL))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT 'a%c' LIKE ANY ('a^%c') ESCAPE '^', SYSTEM$TYPEOF('a%c' LIKE ANY ('a^%c') ESCAPE '^')");
        assertAnswer(
            "abc|VARCHAR(3)[LOB]",
            "SELECT TRY_BASE64_DECODE_STRING('YWJj'), SYSTEM$TYPEOF(TRY_BASE64_DECODE_STRING('YWJj'))");
        assertAnswer(
            "616263|BINARY(3)[LOB]",
            "SELECT TRY_BASE64_DECODE_BINARY('YWJj'), SYSTEM$TYPEOF(TRY_BASE64_DECODE_BINARY('YWJj'))");
        assertAnswer(
            "abc|VARCHAR(3)[LOB]",
            "SELECT TRY_HEX_DECODE_STRING('616263'), SYSTEM$TYPEOF(TRY_HEX_DECODE_STRING('616263'))");
        assertAnswer(
            "null|VARCHAR(1)[LOB]",
            "SELECT TRY_HEX_DECODE_STRING('zz'), SYSTEM$TYPEOF(TRY_HEX_DECODE_STRING('zz'))");
        assertAnswer(
            "616263|BINARY(3)[LOB]",
            "SELECT TRY_HEX_DECODE_BINARY('616263'), SYSTEM$TYPEOF(TRY_HEX_DECODE_BINARY('616263'))");
        assertAnswer(
            "null|VARCHAR(1)[LOB]",
            "SELECT TRY_HEX_DECODE_STRING('FF'), SYSTEM$TYPEOF(TRY_HEX_DECODE_STRING('FF'))");
    }

    @Test
    public void characterCodesAndTrimSets() {
        assertAnswer(
            "A|VARCHAR(1)[LOB]",
            "SELECT CHR(65.4), SYSTEM$TYPEOF(CHR(65.4))");
        assertAnswer(
            "B|VARCHAR(1)[LOB]",
            "SELECT CHR(65.5), SYSTEM$TYPEOF(CHR(65.5))");
        assertAnswer(
            "\u0000|VARCHAR(1)[LOB]",
            "SELECT CHR(0), SYSTEM$TYPEOF(CHR(0))");
        assertAnswer(
            "REFUSED Invalid character code -1 in the CHR input",
            "SELECT CHR(-1), SYSTEM$TYPEOF(CHR(-1))");
        assertAnswer(
            "REFUSED Invalid character code 1114112 in the CHR input",
            "SELECT CHR(1114112), SYSTEM$TYPEOF(CHR(1114112))");
        assertAnswer(
            "REFUSED Invalid character code 55296 in the CHR input",
            "SELECT CHR(55296), SYSTEM$TYPEOF(CHR(55296))");
        assertAnswer(
            "A|VARCHAR(1)[LOB]",
            "SELECT CHR('65'), SYSTEM$TYPEOF(CHR('65'))");
        assertAnswer(
            "\udbff\udfff|VARCHAR(1)[LOB]",
            "SELECT CHR(1114111), SYSTEM$TYPEOF(CHR(1114111))");
        assertAnswer(
            "null|VARCHAR(1)[LOB]",
            "SELECT CHR(NULL), SYSTEM$TYPEOF(CHR(NULL))");
        assertAnswer(
            "\u20ac|VARCHAR(1)[LOB]",
            "SELECT CHR(8364), SYSTEM$TYPEOF(CHR(8364))");
        assertAnswer(
            "2|NUMBER(18,0)[SB8]",
            "SELECT LENGTH(LTRIM(CHR(9) || 'a')), SYSTEM$TYPEOF(LENGTH(LTRIM(CHR(9) || 'a')))");
        assertAnswer(
            "2|NUMBER(18,0)[SB8]",
            "SELECT LENGTH(RTRIM('a' || CHR(10))), SYSTEM$TYPEOF(LENGTH(RTRIM('a' || CHR(10))))");
        assertAnswer(
            "5|NUMBER(18,0)[SB8]",
            "SELECT LENGTH(TRIM(CHR(9) || ' a ' || CHR(13))), SYSTEM$TYPEOF(LENGTH(TRIM(CHR(9) || ' a ' || "
                + "CHR(13))))");
        assertAnswer(
            "1|NUMBER(18,0)[SB8]",
            "SELECT LENGTH(LTRIM('  a')), SYSTEM$TYPEOF(LENGTH(LTRIM('  a')))");
        assertAnswer(
            "1|NUMBER(18,0)[SB8]",
            "SELECT LENGTH(TRIM('  a  ')), SYSTEM$TYPEOF(LENGTH(TRIM('  a  ')))");
        assertAnswer(
            "2|NUMBER(18,0)[SB8]",
            "SELECT LENGTH(RTRIM('a' || CHR(160))), SYSTEM$TYPEOF(LENGTH(RTRIM('a' || CHR(160))))");
        assertAnswer(
            "2|NUMBER(18,0)[SB8]",
            "SELECT LENGTH(TRIM(CHR(11) || 'a')), SYSTEM$TYPEOF(LENGTH(TRIM(CHR(11) || 'a')))");
        assertAnswer(
            "a|VARCHAR(3)[LOB]",
            "SELECT LTRIM('xxa', 'x'), SYSTEM$TYPEOF(LTRIM('xxa', 'x'))");
        assertAnswer(
            " a|VARCHAR(2)[LOB]",
            "SELECT LTRIM(' a', ''), SYSTEM$TYPEOF(LTRIM(' a', ''))");
        assertAnswer(
            "a|VARCHAR(2)[LOB]",
            "SELECT TRIM(CHR(9) || 'a', CHR(9)), SYSTEM$TYPEOF(TRIM(CHR(9) || 'a', CHR(9)))");
    }

    @Test
    public void tokensPatternsAndParts() {
        assertAnswer(
            "a b|VARCHAR(3)[LOB]",
            "SELECT STRTOK('a b', ''), SYSTEM$TYPEOF(STRTOK('a b', ''))");
        assertAnswer(
            "null|VARCHAR(3)[LOB]",
            "SELECT STRTOK('a b', '', 2), SYSTEM$TYPEOF(STRTOK('a b', '', 2))");
        assertAnswer(
            "null|VARCHAR(1)[LOB]",
            "SELECT STRTOK('', ''), SYSTEM$TYPEOF(STRTOK('', ''))");
        assertAnswer(
            "null|VARCHAR(3)[LOB]",
            "SELECT STRTOK('a.b', '.', 0), SYSTEM$TYPEOF(STRTOK('a.b', '.', 0))");
        assertAnswer(
            "null|VARCHAR(3)[LOB]",
            "SELECT STRTOK('a.b', '.', 3), SYSTEM$TYPEOF(STRTOK('a.b', '.', 3))");
        assertAnswer(
            "null|VARCHAR(3)[LOB]",
            "SELECT STRTOK('a.b', '.', -1), SYSTEM$TYPEOF(STRTOK('a.b', '.', -1))");
        assertAnswer(
            "null|VARCHAR(1)[LOB]",
            "SELECT STRTOK('', '.', 1), SYSTEM$TYPEOF(STRTOK('', '.', 1))");
        assertAnswer(
            "a|VARCHAR(3)[LOB]",
            "SELECT STRTOK('..a', '.', 1), SYSTEM$TYPEOF(STRTOK('..a', '.', 1))");
        assertAnswer(
            "abc|VARCHAR[LOB]",
            "SELECT REPLACE('abc', '', 'x'), SYSTEM$TYPEOF(REPLACE('abc', '', 'x'))");
        assertAnswer(
            "abc|VARCHAR[LOB]",
            "SELECT REPLACE('abc', '', ''), SYSTEM$TYPEOF(REPLACE('abc', '', ''))");
        assertAnswer(
            "|VARCHAR[LOB]",
            "SELECT REPLACE('', '', 'x'), SYSTEM$TYPEOF(REPLACE('', '', 'x'))");
        assertAnswer(
            "ac|VARCHAR[LOB]",
            "SELECT REPLACE('abc', 'b'), SYSTEM$TYPEOF(REPLACE('abc', 'b'))");
        assertAnswer(
            "a|VARCHAR(3)[LOB]",
            "SELECT SPLIT_PART('a,b', ',', 0), SYSTEM$TYPEOF(SPLIT_PART('a,b', ',', 0))");
        assertAnswer(
            "b|VARCHAR(3)[LOB]",
            "SELECT SPLIT_PART('a,b', ',', -1), SYSTEM$TYPEOF(SPLIT_PART('a,b', ',', -1))");
        assertAnswer(
            "|VARCHAR(3)[LOB]",
            "SELECT SPLIT_PART('a,b', ',', 3), SYSTEM$TYPEOF(SPLIT_PART('a,b', ',', 3))");
        assertAnswer(
            "a,b|VARCHAR(3)[LOB]",
            "SELECT SPLIT_PART('a,b', '', 1), SYSTEM$TYPEOF(SPLIT_PART('a,b', '', 1))");
        assertAnswer(
            "a,b|VARCHAR(3)[LOB]",
            "SELECT SPLIT_PART('a,b', '', 0), SYSTEM$TYPEOF(SPLIT_PART('a,b', '', 0))");
        assertAnswer(
            "|VARCHAR(3)[LOB]",
            "SELECT SPLIT_PART('a,b', '', 2), SYSTEM$TYPEOF(SPLIT_PART('a,b', '', 2))");
        assertAnswer(
            "|VARCHAR(3)[LOB]",
            "SELECT SPLIT_PART('a,b', ',', -3), SYSTEM$TYPEOF(SPLIT_PART('a,b', ',', -3))");
    }
}
