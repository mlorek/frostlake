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
 * GET_PATH over a path written as a text literal. A path is a chain of steps - a bare name, a quoted name, a
 * name or a position between brackets - joined by a dot or a colon; a name reaches into an object only and a
 * position into an array only. Such a path is judged while the statement compiles, over a NULL source and a
 * source with no row too, and the refusal says what is wrong and where: an invalid token, an invalid field, a
 * dangling dot, an empty field name, a dangling or an empty square bracket, an invalid number, a dangling
 * quote, and a position past what an array counts. A bare name is ASCII letters, digits, {@code _ $ @} and
 * the characters of Latin-1 short of its last, no digit first; inside quotes a backslash escapes and a
 * doubled quote stands for one. Every answer is live-verified.
 */
public class GetPathLiteralPathTest extends MeasuredCellsTestSupport {

    @Test
    public void stepsAndWhereAPathIsRefused() {
        assertAnswer(
            "[7,8]|[1,2]|[3,4]|5",
            "SELECT GET_PATH(PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a b\":{\"c\":[1,2]},\"a.b\":[3,4],"
                + "\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), 'a[0][1]'), GET_PATH(PARSE_JSON('{\"a\":[[1,[7,"
                + "8]],2],\"a b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), '\"a "
                + "b\".c'), GET_PATH(PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a b\":{\"c\":[1,2]},\"a.b\":[3,4],"
                + "\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), '\"a.b\"'), GET_PATH(PARSE_JSON('{\"a\":[[1,[7,"
                + "8]],2],\"a b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), "
                + "'k.m.n[0]')");
        assertAnswer(
            "[1,2]|[5]|{\"n\":[5]}|9",
            "SELECT GET_PATH(PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a b\":{\"c\":[1,2]},\"a.b\":[3,4],"
                + "\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), '[''a b''].c'), GET_PATH(PARSE_JSON('{\"a\":[[1,"
                + "[7,8]],2],\"a b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), "
                + "'k[''m''].n'), GET_PATH(PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a b\":{\"c\":[1,2]},\"a.b\":[3,4],"
                + "\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), 'k[\"m\"]'), GET_PATH(PARSE_JSON('{\"a\":[[1,[7,"
                + "8]],2],\"a b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), 'A[0]')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a.': dangling dot at position 2.",
            "SELECT GET_PATH(PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a b\":{\"c\":[1,2]},\"a.b\":[3,4],"
                + "\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), 'a.'), GET_PATH(PARSE_JSON('{\"a\":[[1,[7,8]],2],"
                + "\"a b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), 'nope')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a[': dangling square bracket at position 2.",
            "SELECT GET_PATH(PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a b\":{\"c\":[1,2]},\"a.b\":[3,4],"
                + "\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), 'a[')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a[x]': invalid number at position 3.",
            "SELECT GET_PATH(PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a b\":{\"c\":[1,2]},\"a.b\":[3,4],"
                + "\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), 'a[x]')");
        assertAnswer(
            "REFUSED Invalid extraction path '.a': invalid token at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a b\":{\"c\":[1,2]},\"a.b\":[3,4],"
                + "\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), '.a')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a..b': empty (unquoted) field name at position 2.",
            "SELECT GET_PATH(PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a b\":{\"c\":[1,2]},\"a.b\":[3,4],"
                + "\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), 'a..b')");
        assertAnswer(
            "REFUSED Bad compound object's field path name '' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a b\":{\"c\":[1,2]},\"a.b\":[3,4],"
                + "\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), '')");
        assertAnswer(
            "REFUSED Invalid extraction path ' a': invalid token at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a b\":{\"c\":[1,2]},\"a.b\":[3,4],"
                + "\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), ' a')");
        assertAnswer(
            "4|1|5",
            "SELECT PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a b\":{\"c\":[1,2]},\"a.b\":[3,4],"
                + "\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'):\"a.b\"[1], PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}')['a b']['c'][0], "
                + "PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},"
                + "\"s\":1,\"A\":[9]}'):k['m'].n[0]");
        assertAnswer(
            "REFUSED Invalid extraction path 'a-b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'a-b')");
        assertAnswer(
            "REFUSED Invalid extraction path '1a': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '1a')");
        assertAnswer(
            "3",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'a1')");
        assertAnswer(
            "4",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '_a')");
        assertAnswer(
            "5",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'a$')");
        assertAnswer(
            "6",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '\u00e9')");
        assertAnswer(
            "7",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '\"\"')");
        assertAnswer(
            "8",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '\"a''b\"')");
        assertAnswer(
            "11",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'A_B')");
        assertAnswer(
            "14",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '$a')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'a_b')");
        assertAnswer(
            "1",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '[\"a-b\"]')");
        assertAnswer(
            "1",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '[''a-b'']')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '[0]')");
        assertAnswer(
            "10",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '[''0'']')");
        assertAnswer(
            "10",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '\"0\"')");
        assertAnswer(
            "REFUSED Invalid extraction path '0': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '0')");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n:b')");
        assertAnswer(
            "[4,5]",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n:b:c')");
        assertAnswer(
            "5",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n.b:c[1]')");
        assertAnswer(
            "[4,5]",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n:b.c')");
        assertAnswer(
            "REFUSED Invalid extraction path ':n': invalid token at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), ':n')");
        assertAnswer(
            "REFUSED Invalid extraction path 'n:': dangling dot at position 2.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n:')");
        assertAnswer(
            "4",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n[''b''][''c''][0]')");
        assertAnswer(
            "[4,5]",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n[''b'']:c')");
        assertAnswer(
            "5",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n.b.c[ 1 ]')");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n . b')");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n .b')");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n. b')");
        assertAnswer(
            "{\"b\":{\"c\":[4,5]}}",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n ')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[1] ': invalid token at position 6.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'arr[1] ')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[0] [1]': invalid token at position 6.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'arr[0] [1]')");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n[''b'' ]')");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n[ ''b'']')");
        assertAnswer(
            "20",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'arr[01]')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[+1]': invalid number at position 5.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'arr[+1]')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[1.0]': invalid number at position 5.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'arr[1.0]')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[1e0]': invalid number at position 5.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'arr[1e0]')");
        assertAnswer(
            "REFUSED Invalid extraction path '99,999,999,999,999,999,999': array index 99,999,999,999,999,999,999 "
                + "is off limits; must be between 0 and 2,147,483,647.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'arr[99999999999999999999]')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[-1]': invalid number at position 5.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'arr[-1]')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'arr[\"1\"]')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'arr[''1'']')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr.1': invalid field at position 4.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'arr.1')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr.[1]': empty (unquoted) field name at position 4.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'arr.[1]')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[1]b': invalid token at position 6.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'arr[1]b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[1].': dangling dot at position 7.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'arr[1].')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a b': invalid token at position 2.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'a b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[1': dangling square bracket at position 4.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'arr[1')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[]': empty square brackets at position 4.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'arr[]')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr]': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'arr]')");
    }

    @Test
    public void quotesNumbersAndNames() {
        assertAnswer(
            "REFUSED Invalid extraction path 'n['b': dangling quote at position 3.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n[''b')");
        assertAnswer(
            "REFUSED Invalid extraction path '\"n': dangling quote at position 1.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '\"n')");
        assertAnswer(
            "REFUSED Invalid extraction path '[': dangling square bracket at position 1.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '[')");
        assertAnswer(
            "REFUSED Invalid extraction path '.': invalid token at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '.')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n.\"\"')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n['''']')");
        assertAnswer(
            "9",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '\"a\\\\\"b\"')");
        assertAnswer(
            "9",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '\"a\"\"b\"')");
        assertAnswer(
            "8",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '[''a\\\\''b'']')");
        assertAnswer(
            "[4,5]",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n.\"b\".c')");
        assertAnswer(
            "REFUSED Invalid extraction path 'n\"b\"': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n\"b\"')");
        assertAnswer(
            "REFUSED Invalid extraction path '\"n\"b': invalid token at position 3.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '\"n\"b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'n[b]': invalid number at position 3.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n[b]')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'N')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n.B')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'a[0]')");
        assertAnswer(
            "12",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '\"a[0]\"')");
        assertAnswer(
            "13",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '\"a:b\"')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'a:b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a,b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'a,b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a/b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'a/b')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'a@b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a#b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'a#b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a%b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'a%b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a+b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'a+b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a=b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'a=b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a*b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'a*b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a(b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'a(b')");
        assertAnswer(
            "REFUSED Invalid extraction path '-a': invalid token at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '-a')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a\tb': invalid token at position 2.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'a\tb')");
        assertAnswer(
            "15",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '\"a b\"')");
        assertAnswer(
            "REFUSED Invalid extraction path 'n | .b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n\n.b')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), NULL)");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'arr[1][0]')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[ ]': empty square brackets at position 4.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'arr[ ]')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '[1]')");
        assertAnswer(
            "REFUSED Invalid extraction path ':': invalid token at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), ':')");
        assertAnswer(
            "REFUSED Invalid extraction path ']': invalid token at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), ']')");
        assertAnswer(
            "REFUSED Invalid extraction path '\"': dangling quote at position 1.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '\"')");
        assertAnswer(
            "REFUSED Invalid extraction path ''': invalid token at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), '''')");
        assertAnswer(
            "REFUSED Invalid extraction path 'n..b': empty (unquoted) field name at position 2.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n..b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'n.:b': empty (unquoted) field name at position 2.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n.:b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'n::b': empty (unquoted) field name at position 2.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'n::b')");
        assertAnswer(
            "20",
            "SELECT GET_PATH(PARSE_JSON('[10,20,30]'), '[1]')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('[10,20,30]'), '[''1'']')");
        assertAnswer(
            "REFUSED Invalid extraction path '1': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('[10,20,30]'), '1')");
        assertAnswer(
            "30 ;; {\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), p) FROM (SELECT 'n.b' AS p UNION ALL SELECT "
                + "'arr[2]') ORDER BY 1");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n.' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), p) FROM (SELECT 'n.' AS p)");
        assertAnswer(
            "REFUSED Bad compound object's field path name '5' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 5)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'arr', 'x')] expected 2, got 3",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'arr', 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'GET_PATH': (VARCHAR(7), VARCHAR(1))",
            "SELECT GET_PATH('{\"a\":1}', 'a')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(5, 'a')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a.': dangling dot at position 2.",
            "SELECT GET_PATH(NULL, 'a.')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a.': dangling dot at position 2.",
            "SELECT GET_PATH(PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,"
                + "\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,\"$a\":14,\"a b\":15,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), 'a.') FROM (SELECT 1) WHERE 1 = 0");
    }

    @Test
    public void blanksAndWhatEndsAName() {
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), '\"n\" .b')");
        assertAnswer(
            "{\"b\":{\"c\":[4,5]}}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), '\"n\" ')");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), '\"n\".b')");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), '\"n\"[\"b\"]')");
        assertAnswer(
            "[20,21]",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'arr [1]')");
        assertAnswer(
            "REFUSED Invalid extraction path 'n. ': dangling dot at position 2.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'n. ')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr['x'': dangling square bracket at position 4.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'arr[''x''')");
        assertAnswer(
            "REFUSED Invalid extraction path '2,147,483,648': array index 2,147,483,648 is off limits; must be "
                + "between 0 and 2,147,483,647.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'arr[2147483648]')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'arr[2147483647]')");
        assertAnswer(
            "16",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), '@a')");
        assertAnswer(
            "17",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'a@')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a~b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'a~b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a!b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'a!b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a&b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'a&b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a|b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'a|b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a<b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'a<b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a>b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'a>b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a?b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'a?b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a;b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'a;b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a{b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'a{b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a}b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'a}b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a\\b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'a\\\\b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a'b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'a''b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a)b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'a)b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a^b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'a^b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a`b': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'a`b')");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'n.b ')");
        assertAnswer(
            "[4,5]",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'n .b .c')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), '[ 0 ]')");
        assertAnswer(
            "REFUSED Invalid extraction path '['n'] .b': invalid token at position 5.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), '[''n''] .b')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'n.$')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), '$')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), '_')");
        assertAnswer(
            "REFUSED Invalid extraction path 'n.9': invalid field at position 2.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'n.9')");
        assertAnswer(
            "REFUSED Invalid extraction path '\u65e5\u672c': invalid token at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), '\u65e5\u672c')");
        assertAnswer(
            "REFUSED Invalid extraction path 'a\u65e5': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'a\u65e5')");
        assertAnswer(
            "20",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'arr[1][0]')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[1 ]x': invalid token at position 7.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'arr[1 ]x')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[1 2]': dangling square bracket at position 4.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'arr[1 2]')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[ '1' x]': dangling square bracket at position 4.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'arr[ ''1'' x]')");
        assertAnswer(
            "REFUSED Invalid extraction path 'n['b'': dangling square bracket at position 2.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'n[''b''')");
        assertAnswer(
            "REFUSED Invalid extraction path 'n[\"b': dangling quote at position 3.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'n[\"b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'n.\"b': dangling quote at position 3.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'n.\"b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'n.'b'': invalid field at position 2.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'n.''b''')");
        assertAnswer(
            "REFUSED Invalid extraction path ''n'': invalid token at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), '''n''')");
        assertAnswer(
            "REFUSED Invalid extraction path 'n.b.c[0': dangling square bracket at position 6.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'n.b.c[0')");
        assertAnswer(
            "REFUSED Invalid extraction path 'n.b.c[': dangling square bracket at position 6.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'n.b.c[')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[1].[0]': empty (unquoted) field name at position 7.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'arr[1].[0]')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[1]:[0]': empty (unquoted) field name at position 7.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'arr[1]:[0]')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[1]. ': dangling dot at position 7.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'arr[1]. ')");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n  .  b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'n  .  b')");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'n.\tb')");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'n.b\t')");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), '\"n\"\t.b')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'N.B')");
        assertAnswer(
            "REFUSED Invalid extraction path 'n.[': empty (unquoted) field name at position 2.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'n.[')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[0]]': invalid token at position 6.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'arr[0]]')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[[0]]': invalid number at position 5.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'arr[[0]]')");
        assertAnswer(
            "REFUSED Invalid extraction path 'n.b.c.': dangling dot at position 6.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'n.b.c.')");
        assertAnswer(
            "REFUSED Invalid extraction path 'n.b.c:': dangling dot at position 6.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'n.b.c:')");
        assertAnswer(
            "REFUSED Invalid extraction path 'x y': invalid token at position 2.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'x y')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), 'x  ')");
        assertAnswer(
            "REFUSED Invalid extraction path '  ': invalid token at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), '  ')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), '\"a\\\\\"b\"')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), '\"a\\\\nb\"')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,"
                + "5]}},\"arr\":[10,[20,21],30]}'), '\"a\\\\\\\\b\"')");
    }

    @Test
    public void lettersPastAsciiAndEscapes() {
        assertAnswer(
            "REFUSED Invalid extraction path '\u00ff': invalid token at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'\u00ff')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'\u00df')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'\u00b5')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'\u00d7')");
        assertAnswer(
            "REFUSED Invalid extraction path '\u0100': invalid token at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'\u0100')");
        assertAnswer(
            "REFUSED Invalid extraction path '\u03b1': invalid token at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'\u03b1')");
        assertAnswer(
            "REFUSED Invalid extraction path '\u044f': invalid token at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'\u044f')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'a\u00e9')");
        assertAnswer(
            "null",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'a\u00d7')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[12x]': invalid number at position 5.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'arr[12x]')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[ x]': invalid number at position 6.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'arr[ x]')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[ +1]': invalid number at position 6.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'arr[ +1]')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[1x]': invalid number at position 5.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'arr[1x]')");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'n  .b')");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n.  b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'n.  b')");
        assertAnswer(
            "[20,21]",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'arr  [1]')");
        assertAnswer(
            "[20,21]",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'arr[  1  ]')");
        assertAnswer(
            "2",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'\"c\\\\nd\"')");
        assertAnswer(
            "3",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'\"e\\\\\\\\f\"')");
        assertAnswer(
            "4",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'\"g\\\\th\"')");
        assertAnswer(
            "5",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'\"u\\\\u00e9\"')");
        assertAnswer(
            "2",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'[''c\\\\nd'']')");
        assertAnswer(
            "7",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'\"q\\\\\"r\"')");
        assertAnswer(
            "7",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'\"q\"\"r\"')");
        assertAnswer(
            "7",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'[''q\"r'']')");
        assertAnswer(
            "7",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'[\"q\\\\\"r\"]')");
        assertAnswer(
            "{\"b\":{\"c\":[4,5]}}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'n  ')");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'n \t.b')");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'n. \"b\"')");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'n [''b'']')");
        assertAnswer(
            "5",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'u\u00e9')");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'\"n\"  .b')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[1 x]': dangling square bracket at position 4.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'arr[1 x]')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr['1'x]': dangling square bracket at position 4.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'arr[''1''x]')");
        assertAnswer(
            "REFUSED Invalid extraction path 'arr[1': dangling square bracket at position 4.",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'arr[1')");
        assertAnswer(
            "{\"c\":[4,5]}",
            "SELECT GET_PATH(PARSE_JSON('{\"a1\":3,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],"
                + "\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), "
                + "'n :b')");
    }
}
