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
 * GET_PATH over a path that is no literal - a column here - which is read with the row. That reading takes
 * what the literal one refuses: a separator first, a quoted name right after another step, a position behind
 * a minus sign (only -0 names an element), a position past what an array counts, blanks after a closing
 * bracket and any character past ASCII in a bare name. It refuses a second blank after a separator, where the
 * literal reading takes a run of them. A path it cannot read is {@code Bad compound object's field path name
 * 'n.' in GET_PATH}, the empty path included; a NULL path answers NULL. A join of two literals is a literal.
 * Every answer is live-verified.
 */
public class GetPathRuntimePathTest extends MeasuredCellsTestSupport {

    @Test
    public void whatARowsPathMayBe() {
        assertAnswer(
            "{\"b\":{\"c\":[4,5]}}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT '.n' AS p)");
        assertAnswer(
            "{\"b\":{\"c\":[4,5]}}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT ':n' AS p)");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'n\"b\"' AS p)");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'arr[-1]' AS p)");
        assertAnswer(
            "20",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'arr[1] [0]' AS p)");
        assertAnswer(
            "[20,21]",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'arr[1] ' AS p)");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'arr[99999999999999999999]' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name '' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT '' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name ' n' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT ' n' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n.' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'n.' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name '\"n\"b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT '\"n\"b' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n 'b'' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'n ''b''' AS p)");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT '\"n\" .b' AS p)");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT '\"n\".b' AS p)");
        assertAnswer(
            "[4,5]",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'n[\"b\"]\"c\"' AS p)");
        assertAnswer(
            "[4,5]",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'n\"b\".\"c\"' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n\"b\"c' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'n\"b\"c' AS p)");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'n \"b\"' AS p)");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'arr[1]\"x\"' AS p)");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT '\"n\"\"b\"' AS p)");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'n.b ' AS p)");
        assertAnswer(
            "16",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT '@a' AS p)");
        assertAnswer(
            "17",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'a@' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a~b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'a~b' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a!b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'a!b' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a&b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'a&b' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a|b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'a|b' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a<b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'a<b' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a>b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'a>b' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a?b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'a?b' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a;b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'a;b' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a{b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'a{b' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a}b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'a}b' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a\\b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'a\\\\b' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a'b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'a''b' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a)b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'a)b' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a^b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'a^b' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a`b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'a`b' AS p)");
        assertAnswer(
            "5",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'a$' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name '1a' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT '1a' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n | .b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'n\n.b' AS p)");
        assertAnswer(
            "[20,21]",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'arr[01]' AS p)");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'arr[ -1 ]' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr[- 1]' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'arr[- 1]' AS p)");
        assertAnswer(
            "10",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'arr[-0]' AS p)");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'arr[2147483648]' AS p)");
    }

    @Test
    public void whatARowsPathMayNotBe() {
        assertAnswer(
            "20",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'arr[1][0]' AS p)");
        assertAnswer(
            "5",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'n.b.c[1]' AS p)");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'n[''b'']' AS p)");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'n . b' AS p)");
        assertAnswer(
            "[20,21]",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'arr [1]' AS p)");
        assertAnswer(
            "{\"b\":{\"c\":[4,5]}}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT '. n' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name '..n' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT '..n' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n..b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'n..b' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr.[1]' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'arr.[1]' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr[1].[0]' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'arr[1].[0]' AS p)");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT '$' AS p)");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT '_' AS p)");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT '\u65e5\u672c' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n.9' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'n.9' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a-b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'a-b' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'a b' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name '[' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT '[' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr[' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'arr[' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr[1' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'arr[1' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr[]' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'arr[]' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr[x]' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'arr[x]' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name '\"n' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT '\"n' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n['b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'n[''b' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr[+1]' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'arr[+1]' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr[1.0]' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'arr[1.0]' AS p)");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'arr[-1][0]' AS p)");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'arr[-2]' AS p)");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'n\t.b' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n.b[' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'n.b[' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name ''n'' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT '''n''' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n.b.c.' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT 'n.b.c.' AS p)");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), p) FROM (SELECT NULL::VARCHAR AS p)");
        assertAnswer(
            "{\"c\":[4,5]}|[20,21]",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'n' || '.b'), GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), CONCAT('arr', '[1]'))");
        assertAnswer(
            "REFUSED Invalid extraction path 'n.': dangling dot at position 2.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'n' || '.')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), TRUE)");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'n.b'::VARIANT)");
        assertAnswer(
            "null|{\"b\":{\"c\":[4,5]}}|[20,21]",
            "SELECT GET(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},"
                + "\"arr\":[10,[20,21],30]}'), 'n.b'), GET(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,"
                + "\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), 'n'), GET(PARSE_JSON('{\"a1\":3,"
                + "\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), "
                + "'arr')[1]");
    }

    @Test
    public void blanksAndEscapes() {
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'n  .b' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n.  b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'n.  b' AS p)");
        assertAnswer(
            "{\"b\":{\"c\":[4,5]}}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'n  ' AS p)");
        assertAnswer(
            "[20,21]",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'arr  [1]' AS p)");
        assertAnswer(
            "[20,21]",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'arr[  1  ]' AS p)");
        assertAnswer(
            "20",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'arr[1]  [0]' AS p)");
        assertAnswer(
            "[20,21]",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'arr[1]  ' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name '.  n' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT '.  n' AS p)");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'n \t.b' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n.'b'' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'n.''b''' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n['b']x' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'n[''b'']x' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr[1]x' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'arr[1]x' AS p)");
        assertAnswer(
            "{\"b\":{\"c\":[4,5]}}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'n \t' AS p)");
        assertAnswer(
            "{\"b\":{\"c\":[4,5]}}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'n\t\t' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n.\t\tb' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'n.\t\tb' AS p)");
        assertAnswer(
            "[20,21]",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'arr[1 ]' AS p)");
        assertAnswer(
            "[20,21]",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'arr[ 1]' AS p)");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'n[  ''b''  ]' AS p)");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT '\"n\"  .b' AS p)");
        assertAnswer(
            "{\"b\":{\"c\":[4,5]}}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT '\"n\"  ' AS p)");
        assertAnswer(
            "2",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT '\"c\\\\nd\"' AS p)");
        assertAnswer(
            "3",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT '\"e\\\\\\\\f\"' AS p)");
        assertAnswer(
            "4",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT '\"g\\\\th\"' AS p)");
        assertAnswer(
            "5",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT '\"u\\\\u00e9\"' AS p)");
        assertAnswer(
            "2",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT '[''c\\\\nd'']' AS p)");
        assertAnswer(
            "7",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT '\"q\\\\\"r\"' AS p)");
        assertAnswer(
            "7",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT '\"q\"\"r\"' AS p)");
        assertAnswer(
            "7",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT '[''q\"r'']' AS p)");
        assertAnswer(
            "7",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT '[\"q\\\\\"r\"]' AS p)");
        assertAnswer(
            "6",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT '\u00ff' AS p)");
        assertAnswer(
            "5",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'u\u00e9' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr[12x]' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'arr[12x]' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr[1x]' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'arr[1x]' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr[ x]' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'arr[ x]' AS p)");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'n. \"b\"' AS p)");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'n .\"b\"' AS p)");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'n :b' AS p)");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'n: b' AS p)");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "p) FROM (SELECT 'n [''b'']' AS p)");
    }
}
