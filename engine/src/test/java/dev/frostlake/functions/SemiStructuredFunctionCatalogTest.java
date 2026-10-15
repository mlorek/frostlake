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
 * The array and object functions the catalog lists: ARRAY_CONSTRUCT, ARRAY_SIZE, ARRAY_APPEND, ARRAY_PREPEND,
 * ARRAY_CAT, ARRAY_CONTAINS, ARRAY_DISTINCT, ARRAY_REMOVE, ARRAY_SLICE, ARRAYS_OVERLAP, OBJECT_CONSTRUCT and
 * its KEEP_NULL and star forms, OBJECT_KEYS, OBJECT_DELETE, GET_IGNORE_CASE, IS_OBJECT and IS_VARCHAR. A key
 * written twice is refused, and a key spelled exactly is read before one of another case (live-verified).
 */
public class SemiStructuredFunctionCatalogTest extends MeasuredCellsTestSupport {

    @Test
    public void arraysBuiltAndMeasured() {
        assertAnswer(
            "[]|ARRAY[LOB]",
            "SELECT ARRAY_CONSTRUCT(), SYSTEM$TYPEOF(ARRAY_CONSTRUCT())");
        assertAnswer(
            "[1,\"a\",undefined,true]|ARRAY[LOB]",
            "SELECT ARRAY_CONSTRUCT(1, 'a', NULL, TRUE), SYSTEM$TYPEOF(ARRAY_CONSTRUCT(1, 'a', NULL, TRUE))");
        assertAnswer(
            "[[1],2]|ARRAY[LOB]",
            "SELECT ARRAY_CONSTRUCT(ARRAY_CONSTRUCT(1), 2), SYSTEM$TYPEOF(ARRAY_CONSTRUCT(ARRAY_CONSTRUCT(1), 2))");
        assertAnswer(
            "[1,2]|ARRAY[LOB]",
            "SELECT [1, 2], SYSTEM$TYPEOF([1, 2])");
        assertAnswer(
            "[\"2024-01-02\"]|ARRAY[LOB]",
            "SELECT ARRAY_CONSTRUCT('2024-01-02'::DATE), SYSTEM$TYPEOF(ARRAY_CONSTRUCT('2024-01-02'::DATE))");
        assertAnswer(
            "3|NUMBER(9,0)[SB4]",
            "SELECT ARRAY_SIZE(ARRAY_CONSTRUCT(1, 2, 3)), SYSTEM$TYPEOF(ARRAY_SIZE(ARRAY_CONSTRUCT(1, 2, 3)))");
        assertAnswer(
            "0|NUMBER(9,0)[SB4]",
            "SELECT ARRAY_SIZE(ARRAY_CONSTRUCT()), SYSTEM$TYPEOF(ARRAY_SIZE(ARRAY_CONSTRUCT()))");
        assertAnswer(
            "null|NUMBER(9,0)[SB4]",
            "SELECT ARRAY_SIZE(NULL), SYSTEM$TYPEOF(ARRAY_SIZE(NULL))");
        assertAnswer(
            "2|NUMBER(9,0)[SB4]",
            "SELECT ARRAY_SIZE(PARSE_JSON('[1,[2,3]]')), SYSTEM$TYPEOF(ARRAY_SIZE(PARSE_JSON('[1,[2,3]]')))");
        assertAnswer(
            "null|NUMBER(9,0)[SB4]",
            "SELECT ARRAY_SIZE(PARSE_JSON('{\"a\":1}')), SYSTEM$TYPEOF(ARRAY_SIZE(PARSE_JSON('{\"a\":1}')))");
        assertAnswer(
            "null|NUMBER(9,0)[SB4]",
            "SELECT ARRAY_SIZE(PARSE_JSON('1')), SYSTEM$TYPEOF(ARRAY_SIZE(PARSE_JSON('1')))");
        assertAnswer(
            "[1,2,3,4]|ARRAY[LOB]",
            "SELECT ARRAY_APPEND(ARRAY_CONSTRUCT(1, 2, 3), 4), SYSTEM$TYPEOF(ARRAY_APPEND(ARRAY_CONSTRUCT(1, 2, "
                + "3), 4))");
        assertAnswer(
            "[0,1,2,3]|ARRAY[LOB]",
            "SELECT ARRAY_PREPEND(ARRAY_CONSTRUCT(1, 2, 3), 0), SYSTEM$TYPEOF(ARRAY_PREPEND(ARRAY_CONSTRUCT(1, 2, "
                + "3), 0))");
        assertAnswer(
            "[1,2,3,undefined]|ARRAY[LOB]",
            "SELECT ARRAY_APPEND(ARRAY_CONSTRUCT(1, 2, 3), NULL), SYSTEM$TYPEOF(ARRAY_APPEND(ARRAY_CONSTRUCT(1, 2,"
                + " 3), NULL))");
        assertAnswer(
            "null|ARRAY[LOB]",
            "SELECT ARRAY_APPEND(NULL, 1), SYSTEM$TYPEOF(ARRAY_APPEND(NULL, 1))");
        assertAnswer(
            "[1,2,3,[9]]|ARRAY[LOB]",
            "SELECT ARRAY_APPEND(ARRAY_CONSTRUCT(1, 2, 3), ARRAY_CONSTRUCT(9)), "
                + "SYSTEM$TYPEOF(ARRAY_APPEND(ARRAY_CONSTRUCT(1, 2, 3), ARRAY_CONSTRUCT(9)))");
        assertAnswer(
            "[\"a\"]|ARRAY[LOB]",
            "SELECT ARRAY_APPEND(ARRAY_CONSTRUCT(), 'a'), SYSTEM$TYPEOF(ARRAY_APPEND(ARRAY_CONSTRUCT(), 'a'))");
        assertAnswer(
            "[1,2,3,4]|ARRAY[LOB]",
            "SELECT ARRAY_CAT(ARRAY_CONSTRUCT(1, 2, 3), ARRAY_CONSTRUCT(4)), "
                + "SYSTEM$TYPEOF(ARRAY_CAT(ARRAY_CONSTRUCT(1, 2, 3), ARRAY_CONSTRUCT(4)))");
        assertAnswer(
            "null|ARRAY[LOB]",
            "SELECT ARRAY_CAT(ARRAY_CONSTRUCT(1, 2, 3), NULL), SYSTEM$TYPEOF(ARRAY_CAT(ARRAY_CONSTRUCT(1, 2, 3), "
                + "NULL))");
        assertAnswer(
            "[]|ARRAY[LOB]",
            "SELECT ARRAY_CAT(ARRAY_CONSTRUCT(), ARRAY_CONSTRUCT()), SYSTEM$TYPEOF(ARRAY_CAT(ARRAY_CONSTRUCT(), "
                + "ARRAY_CONSTRUCT()))");
        assertAnswer(
            "REFUSED Right argument of ARRAY_CAT is not an array",
            "SELECT ARRAY_CAT(ARRAY_CONSTRUCT(1, 2, 3), 4), SYSTEM$TYPEOF(ARRAY_CAT(ARRAY_CONSTRUCT(1, 2, 3), 4))");
    }

    @Test
    public void arraysSearchedAndCut() {
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT ARRAY_CONTAINS(2, ARRAY_CONSTRUCT(1, 2, 3)), SYSTEM$TYPEOF(ARRAY_CONTAINS(2, "
                + "ARRAY_CONSTRUCT(1, 2, 3)))");
        assertAnswer(
            "false|BOOLEAN[ROWINDEX]",
            "SELECT ARRAY_CONTAINS(9, ARRAY_CONSTRUCT(1, 2, 3)), SYSTEM$TYPEOF(ARRAY_CONTAINS(9, "
                + "ARRAY_CONSTRUCT(1, 2, 3)))");
        assertAnswer(
            "false|BOOLEAN[ROWINDEX]",
            "SELECT ARRAY_CONTAINS('2'::VARIANT, ARRAY_CONSTRUCT(1, 2, 3)), "
                + "SYSTEM$TYPEOF(ARRAY_CONTAINS('2'::VARIANT, ARRAY_CONSTRUCT(1, 2, 3)))");
        assertAnswer(
            "null|BOOLEAN[ROWINDEX]",
            "SELECT ARRAY_CONTAINS(NULL, ARRAY_CONSTRUCT(1, 2, 3)), SYSTEM$TYPEOF(ARRAY_CONTAINS(NULL, "
                + "ARRAY_CONSTRUCT(1, 2, 3)))");
        assertAnswer(
            "null|BOOLEAN[ROWINDEX]",
            "SELECT ARRAY_CONTAINS(2, NULL), SYSTEM$TYPEOF(ARRAY_CONTAINS(2, NULL))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT ARRAY_CONTAINS(NULL, ARRAY_CONSTRUCT(1, NULL)), SYSTEM$TYPEOF(ARRAY_CONTAINS(NULL, "
                + "ARRAY_CONSTRUCT(1, NULL)))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT ARRAY_CONTAINS('a'::VARIANT, ARRAY_CONSTRUCT('a')), SYSTEM$TYPEOF(ARRAY_CONTAINS('a'::VARIANT,"
                + " ARRAY_CONSTRUCT('a')))");
        assertAnswer(
            "[1,2,3]|ARRAY[LOB]",
            "SELECT ARRAY_DISTINCT(ARRAY_CONSTRUCT(1, 2, 1, 3, 2)), "
                + "SYSTEM$TYPEOF(ARRAY_DISTINCT(ARRAY_CONSTRUCT(1, 2, 1, 3, 2)))");
        assertAnswer(
            "[]|ARRAY[LOB]",
            "SELECT ARRAY_DISTINCT(ARRAY_CONSTRUCT()), SYSTEM$TYPEOF(ARRAY_DISTINCT(ARRAY_CONSTRUCT()))");
        assertAnswer(
            "null|ARRAY[LOB]",
            "SELECT ARRAY_DISTINCT(NULL), SYSTEM$TYPEOF(ARRAY_DISTINCT(NULL))");
        assertAnswer(
            "[1,\"1\",undefined]|ARRAY[LOB]",
            "SELECT ARRAY_DISTINCT(ARRAY_CONSTRUCT(1, '1', NULL, NULL)), "
                + "SYSTEM$TYPEOF(ARRAY_DISTINCT(ARRAY_CONSTRUCT(1, '1', NULL, NULL)))");
        assertAnswer(
            "[2,3]|ARRAY[LOB]",
            "SELECT ARRAY_REMOVE(ARRAY_CONSTRUCT(1, 2, 1, 3), 1), SYSTEM$TYPEOF(ARRAY_REMOVE(ARRAY_CONSTRUCT(1, 2,"
                + " 1, 3), 1))");
        assertAnswer(
            "[1,2,3]|ARRAY[LOB]",
            "SELECT ARRAY_REMOVE(ARRAY_CONSTRUCT(1, 2, 3), 9), SYSTEM$TYPEOF(ARRAY_REMOVE(ARRAY_CONSTRUCT(1, 2, "
                + "3), 9))");
        assertAnswer(
            "null|ARRAY[LOB]",
            "SELECT ARRAY_REMOVE(ARRAY_CONSTRUCT(1, 2, 3), NULL), SYSTEM$TYPEOF(ARRAY_REMOVE(ARRAY_CONSTRUCT(1, 2,"
                + " 3), NULL))");
        assertAnswer(
            "null|ARRAY[LOB]",
            "SELECT ARRAY_REMOVE(NULL, 1), SYSTEM$TYPEOF(ARRAY_REMOVE(NULL, 1))");
        assertAnswer(
            "[\"b\"]|ARRAY[LOB]",
            "SELECT ARRAY_REMOVE(ARRAY_CONSTRUCT('a', 'b'), 'a'::VARIANT), "
                + "SYSTEM$TYPEOF(ARRAY_REMOVE(ARRAY_CONSTRUCT('a', 'b'), 'a'::VARIANT))");
        assertAnswer(
            "[1,2]|ARRAY[LOB]",
            "SELECT ARRAY_SLICE(ARRAY_CONSTRUCT(1, 2, 3), 0, 2), SYSTEM$TYPEOF(ARRAY_SLICE(ARRAY_CONSTRUCT(1, 2, "
                + "3), 0, 2))");
        assertAnswer(
            "[2,3]|ARRAY[LOB]",
            "SELECT ARRAY_SLICE(ARRAY_CONSTRUCT(1, 2, 3), 1, 10), SYSTEM$TYPEOF(ARRAY_SLICE(ARRAY_CONSTRUCT(1, 2, "
                + "3), 1, 10))");
        assertAnswer(
            "[2]|ARRAY[LOB]",
            "SELECT ARRAY_SLICE(ARRAY_CONSTRUCT(1, 2, 3), -2, -1), SYSTEM$TYPEOF(ARRAY_SLICE(ARRAY_CONSTRUCT(1, 2,"
                + " 3), -2, -1))");
        assertAnswer(
            "[]|ARRAY[LOB]",
            "SELECT ARRAY_SLICE(ARRAY_CONSTRUCT(1, 2, 3), 2, 1), SYSTEM$TYPEOF(ARRAY_SLICE(ARRAY_CONSTRUCT(1, 2, "
                + "3), 2, 1))");
        assertAnswer(
            "null|ARRAY[LOB]",
            "SELECT ARRAY_SLICE(ARRAY_CONSTRUCT(1, 2, 3), NULL, 2), SYSTEM$TYPEOF(ARRAY_SLICE(ARRAY_CONSTRUCT(1, "
                + "2, 3), NULL, 2))");
        assertAnswer(
            "null|ARRAY[LOB]",
            "SELECT ARRAY_SLICE(NULL, 0, 1), SYSTEM$TYPEOF(ARRAY_SLICE(NULL, 0, 1))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT ARRAYS_OVERLAP(ARRAY_CONSTRUCT(1, 2, 3), ARRAY_CONSTRUCT(3, 4)), "
                + "SYSTEM$TYPEOF(ARRAYS_OVERLAP(ARRAY_CONSTRUCT(1, 2, 3), ARRAY_CONSTRUCT(3, 4)))");
        assertAnswer(
            "false|BOOLEAN[ROWINDEX]",
            "SELECT ARRAYS_OVERLAP(ARRAY_CONSTRUCT(1, 2, 3), ARRAY_CONSTRUCT(4)), "
                + "SYSTEM$TYPEOF(ARRAYS_OVERLAP(ARRAY_CONSTRUCT(1, 2, 3), ARRAY_CONSTRUCT(4)))");
        assertAnswer(
            "null|BOOLEAN[ROWINDEX]",
            "SELECT ARRAYS_OVERLAP(ARRAY_CONSTRUCT(1, 2, 3), NULL), "
                + "SYSTEM$TYPEOF(ARRAYS_OVERLAP(ARRAY_CONSTRUCT(1, 2, 3), NULL))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT ARRAYS_OVERLAP(ARRAY_CONSTRUCT(NULL), ARRAY_CONSTRUCT(NULL)), "
                + "SYSTEM$TYPEOF(ARRAYS_OVERLAP(ARRAY_CONSTRUCT(NULL), ARRAY_CONSTRUCT(NULL)))");
        assertAnswer(
            "false|BOOLEAN[ROWINDEX]",
            "SELECT ARRAYS_OVERLAP(ARRAY_CONSTRUCT(), ARRAY_CONSTRUCT()), "
                + "SYSTEM$TYPEOF(ARRAYS_OVERLAP(ARRAY_CONSTRUCT(), ARRAY_CONSTRUCT()))");
    }

    @Test
    public void objectsBuiltAndRead() {
        answer(
            "CREATE OR REPLACE TABLE ta (g INT, x NUMBER(10,2), y NUMBER(10,2), s VARCHAR, b INT)");
        answer(
            "INSERT INTO ta VALUES (1, 1.0, 2.0, 'a', 12), (1, 2.0, 4.5, 'b', 10), (2, 3.0, 5.0, NULL, 6), (2, "
                + "NULL, 7.0, 'c', NULL), (2, 5.0, NULL, 'a', 3)");
        answer(
            "CREATE OR REPLACE TABLE te (x NUMBER(10,2), y NUMBER(10,2), b INT)");
        assertAnswer(
            "{\"a\":1,\"b\":\"x\"}|OBJECT[LOB]",
            "SELECT OBJECT_CONSTRUCT('a', 1, 'b', 'x'), SYSTEM$TYPEOF(OBJECT_CONSTRUCT('a', 1, 'b', 'x'))");
        assertAnswer(
            "{}|OBJECT[LOB]",
            "SELECT OBJECT_CONSTRUCT('a', NULL), SYSTEM$TYPEOF(OBJECT_CONSTRUCT('a', NULL))");
        assertAnswer(
            "{\"a\":null}|OBJECT[LOB]",
            "SELECT OBJECT_CONSTRUCT_KEEP_NULL('a', NULL), SYSTEM$TYPEOF(OBJECT_CONSTRUCT_KEEP_NULL('a', NULL))");
        assertAnswer(
            "{}|OBJECT[LOB]",
            "SELECT OBJECT_CONSTRUCT(), SYSTEM$TYPEOF(OBJECT_CONSTRUCT())");
        assertAnswer(
            "REFUSED Duplicate field key 'a'",
            "SELECT OBJECT_CONSTRUCT('a', 1, 'a', 2), SYSTEM$TYPEOF(OBJECT_CONSTRUCT('a', 1, 'a', 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid number of arguments for "
                + "[OBJECT_CONSTRUCT], expected 2, got 1",
            "SELECT OBJECT_CONSTRUCT('a'), SYSTEM$TYPEOF(OBJECT_CONSTRUCT('a'))");
        assertAnswer(
            "{}|OBJECT[LOB]",
            "SELECT OBJECT_CONSTRUCT(NULL, 1), SYSTEM$TYPEOF(OBJECT_CONSTRUCT(NULL, 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function OBJECT_CONSTRUCT does not support NUMBER(1,0) argument "
                + "type for keys",
            "SELECT OBJECT_CONSTRUCT(1, 2), SYSTEM$TYPEOF(OBJECT_CONSTRUCT(1, 2))");
        assertAnswer(
            "{\"a\":2,\"b\":1}|OBJECT[LOB]",
            "SELECT OBJECT_CONSTRUCT('b', 1, 'a', 2), SYSTEM$TYPEOF(OBJECT_CONSTRUCT('b', 1, 'a', 2))");
        assertAnswer(
            "[\"a\",\"b\"]|ARRAY[LOB]",
            "SELECT OBJECT_KEYS(OBJECT_CONSTRUCT('b', 1, 'a', 2)), SYSTEM$TYPEOF(OBJECT_KEYS(OBJECT_CONSTRUCT('b',"
                + " 1, 'a', 2)))");
        assertAnswer(
            "[]|ARRAY[LOB]",
            "SELECT OBJECT_KEYS(OBJECT_CONSTRUCT()), SYSTEM$TYPEOF(OBJECT_KEYS(OBJECT_CONSTRUCT()))");
        assertAnswer(
            "null|ARRAY[LOB]",
            "SELECT OBJECT_KEYS(NULL), SYSTEM$TYPEOF(OBJECT_KEYS(NULL))");
        assertAnswer(
            "REFUSED Failed to cast variant value [1] to OBJECT",
            "SELECT OBJECT_KEYS(PARSE_JSON('[1]')), SYSTEM$TYPEOF(OBJECT_KEYS(PARSE_JSON('[1]')))");
        assertAnswer(
            "[\"a\"]|ARRAY[LOB]",
            "SELECT OBJECT_KEYS(PARSE_JSON('{\"a\":{\"b\":1}}')), "
                + "SYSTEM$TYPEOF(OBJECT_KEYS(PARSE_JSON('{\"a\":{\"b\":1}}')))");
        assertAnswer(
            "{\"b\":2}|OBJECT[LOB]",
            "SELECT OBJECT_DELETE(OBJECT_CONSTRUCT('a', 1, 'b', 2), 'a'), "
                + "SYSTEM$TYPEOF(OBJECT_DELETE(OBJECT_CONSTRUCT('a', 1, 'b', 2), 'a'))");
        assertAnswer(
            "{\"a\":1}|OBJECT[LOB]",
            "SELECT OBJECT_DELETE(OBJECT_CONSTRUCT('a', 1), 'z'), "
                + "SYSTEM$TYPEOF(OBJECT_DELETE(OBJECT_CONSTRUCT('a', 1), 'z'))");
        assertAnswer(
            "{}|OBJECT[LOB]",
            "SELECT OBJECT_DELETE(OBJECT_CONSTRUCT('a', 1, 'b', 2), 'a', 'b'), "
                + "SYSTEM$TYPEOF(OBJECT_DELETE(OBJECT_CONSTRUCT('a', 1, 'b', 2), 'a', 'b'))");
        assertAnswer(
            "null|OBJECT[LOB]",
            "SELECT OBJECT_DELETE(NULL, 'a'), SYSTEM$TYPEOF(OBJECT_DELETE(NULL, 'a'))");
        assertAnswer(
            "{\"a\":1}|OBJECT[LOB]",
            "SELECT OBJECT_DELETE(OBJECT_CONSTRUCT('a', 1), NULL), "
                + "SYSTEM$TYPEOF(OBJECT_DELETE(OBJECT_CONSTRUCT('a', 1), NULL))");
        assertAnswer(
            "1|VARIANT[LOB]",
            "SELECT GET_IGNORE_CASE(OBJECT_CONSTRUCT('Aa', 1), 'aA'), "
                + "SYSTEM$TYPEOF(GET_IGNORE_CASE(OBJECT_CONSTRUCT('Aa', 1), 'aA'))");
        assertAnswer(
            "null|VARIANT[LOB]",
            "SELECT GET_IGNORE_CASE(OBJECT_CONSTRUCT('a', 1), 'z'), "
                + "SYSTEM$TYPEOF(GET_IGNORE_CASE(OBJECT_CONSTRUCT('a', 1), 'z'))");
        assertAnswer(
            "1|VARIANT[LOB]",
            "SELECT GET_IGNORE_CASE(OBJECT_CONSTRUCT('a', 1, 'A', 2), 'a'), "
                + "SYSTEM$TYPEOF(GET_IGNORE_CASE(OBJECT_CONSTRUCT('a', 1, 'A', 2), 'a'))");
        assertAnswer(
            "2|VARIANT[LOB]",
            "SELECT GET_IGNORE_CASE(OBJECT_CONSTRUCT('a', 1, 'A', 2), 'A'), "
                + "SYSTEM$TYPEOF(GET_IGNORE_CASE(OBJECT_CONSTRUCT('a', 1, 'A', 2), 'A'))");
        assertAnswer(
            "null|VARIANT[LOB]",
            "SELECT GET_IGNORE_CASE(NULL, 'a'), SYSTEM$TYPEOF(GET_IGNORE_CASE(NULL, 'a'))");
        assertAnswer(
            "null|VARIANT[LOB]",
            "SELECT GET_IGNORE_CASE(PARSE_JSON('[1]'), 'a'), SYSTEM$TYPEOF(GET_IGNORE_CASE(PARSE_JSON('[1]'), "
                + "'a'))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT IS_OBJECT(PARSE_JSON('{}')), SYSTEM$TYPEOF(IS_OBJECT(PARSE_JSON('{}')))");
        assertAnswer(
            "false|BOOLEAN[ROWINDEX]",
            "SELECT IS_OBJECT(PARSE_JSON('[]')), SYSTEM$TYPEOF(IS_OBJECT(PARSE_JSON('[]')))");
        assertAnswer(
            "null|BOOLEAN[ROWINDEX]",
            "SELECT IS_OBJECT(NULL), SYSTEM$TYPEOF(IS_OBJECT(NULL))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT IS_VARCHAR(PARSE_JSON('\"a\"')), SYSTEM$TYPEOF(IS_VARCHAR(PARSE_JSON('\"a\"')))");
        assertAnswer(
            "false|BOOLEAN[ROWINDEX]",
            "SELECT IS_VARCHAR(PARSE_JSON('1')), SYSTEM$TYPEOF(IS_VARCHAR(PARSE_JSON('1')))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT IS_VARCHAR('a'::VARIANT), SYSTEM$TYPEOF(IS_VARCHAR('a'::VARIANT))");
        assertAnswer(
            "false|BOOLEAN[ROWINDEX]",
            "SELECT IS_OBJECT(PARSE_JSON('null')), SYSTEM$TYPEOF(IS_OBJECT(PARSE_JSON('null')))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT IS_CHAR(PARSE_JSON('\"a\"')), SYSTEM$TYPEOF(IS_CHAR(PARSE_JSON('\"a\"')))");
        assertAnswer(
            "{\"B\":12,\"G\":1,\"S\":\"a\",\"X\":1,\"Y\":2} ;; {\"B\":10,\"G\":1,\"S\":\"b\",\"X\":2,\"Y\":4.5}",
            "SELECT OBJECT_CONSTRUCT(*) FROM ta WHERE g = 1 ORDER BY x");
        assertAnswer(
            "{\"B\":null,\"G\":2,\"S\":\"c\",\"X\":null,\"Y\":7}",
            "SELECT OBJECT_CONSTRUCT_KEEP_NULL(*) FROM ta WHERE x IS NULL");
        assertAnswer(
            "{\"G\":2,\"S\":\"c\",\"Y\":7}",
            "SELECT OBJECT_CONSTRUCT(*) FROM ta WHERE x IS NULL");
    }

    @Test
    public void repeatedKeysAndKeysByCase() {
        assertAnswer(
            "{\"A\":2,\"a\":1}|OBJECT[LOB]",
            "SELECT OBJECT_CONSTRUCT('a', 1, 'A', 2), SYSTEM$TYPEOF(OBJECT_CONSTRUCT('a', 1, 'A', 2))");
        assertAnswer(
            "{\"a\":2}|OBJECT[LOB]",
            "SELECT OBJECT_CONSTRUCT('a', NULL, 'a', 2), SYSTEM$TYPEOF(OBJECT_CONSTRUCT('a', NULL, 'a', 2))");
        assertAnswer(
            "REFUSED Duplicate field key 'a'",
            "SELECT OBJECT_CONSTRUCT_KEEP_NULL('a', NULL, 'a', 2), SYSTEM$TYPEOF(OBJECT_CONSTRUCT_KEEP_NULL('a', "
                + "NULL, 'a', 2))");
        assertAnswer(
            "REFUSED Duplicate field key 'a'",
            "SELECT OBJECT_CONSTRUCT('a', 1, 'b', 2, 'a', 3), SYSTEM$TYPEOF(OBJECT_CONSTRUCT('a', 1, 'b', 2, 'a', "
                + "3))");
        assertAnswer(
            "REFUSED Duplicate field key 'b'",
            "SELECT OBJECT_CONSTRUCT('b', 1, 'a', 2, 'b', 3, 'a', 4), SYSTEM$TYPEOF(OBJECT_CONSTRUCT('b', 1, 'a', "
                + "2, 'b', 3, 'a', 4))");
        assertAnswer(
            "REFUSED Duplicate field key 'a'",
            "SELECT OBJECT_CONSTRUCT_KEEP_NULL('a', 1, 'a', NULL), SYSTEM$TYPEOF(OBJECT_CONSTRUCT_KEEP_NULL('a', "
                + "1, 'a', NULL))");
        assertAnswer(
            "{\"a\":2}|OBJECT[LOB]",
            "SELECT OBJECT_CONSTRUCT(NULL, 1, 'a', 2), SYSTEM$TYPEOF(OBJECT_CONSTRUCT(NULL, 1, 'a', 2))");
        assertAnswer(
            "REFUSED Duplicate field key 'a'",
            "SELECT OBJECT_CONSTRUCT('a', 1, NULL, 2, 'a', 3), SYSTEM$TYPEOF(OBJECT_CONSTRUCT('a', 1, NULL, 2, "
                + "'a', 3))");
        assertAnswer(
            "REFUSED Failed to cast variant value 1 to OBJECT",
            "SELECT OBJECT_KEYS(PARSE_JSON('1')), SYSTEM$TYPEOF(OBJECT_KEYS(PARSE_JSON('1')))");
        assertAnswer(
            "REFUSED Failed to cast variant value [1] to OBJECT",
            "SELECT OBJECT_KEYS(PARSE_JSON('[1]')), SYSTEM$TYPEOF(OBJECT_KEYS(PARSE_JSON('[1]')))");
        assertAnswer(
            "null|ARRAY[LOB]",
            "SELECT OBJECT_KEYS(NULL), SYSTEM$TYPEOF(OBJECT_KEYS(NULL))");
        assertAnswer(
            "REFUSED Failed to cast variant value \"a\" to OBJECT",
            "SELECT OBJECT_KEYS(PARSE_JSON('\"a\"')), SYSTEM$TYPEOF(OBJECT_KEYS(PARSE_JSON('\"a\"')))");
        assertAnswer(
            "null|ARRAY[LOB]",
            "SELECT OBJECT_KEYS(PARSE_JSON('null')), SYSTEM$TYPEOF(OBJECT_KEYS(PARSE_JSON('null')))");
        assertAnswer(
            "[]|ARRAY[LOB]",
            "SELECT OBJECT_KEYS(PARSE_JSON('{}')), SYSTEM$TYPEOF(OBJECT_KEYS(PARSE_JSON('{}')))");
        assertAnswer(
            "REFUSED Failed to cast variant value true to OBJECT",
            "SELECT OBJECT_KEYS(PARSE_JSON('true')), SYSTEM$TYPEOF(OBJECT_KEYS(PARSE_JSON('true')))");
        assertAnswer(
            "[]|ARRAY[LOB]",
            "SELECT OBJECT_KEYS(OBJECT_CONSTRUCT()), SYSTEM$TYPEOF(OBJECT_KEYS(OBJECT_CONSTRUCT()))");
        assertAnswer(
            "2|VARIANT[LOB]",
            "SELECT GET_IGNORE_CASE(OBJECT_CONSTRUCT('a', 1, 'A', 2), 'A'), "
                + "SYSTEM$TYPEOF(GET_IGNORE_CASE(OBJECT_CONSTRUCT('a', 1, 'A', 2), 'A'))");
        assertAnswer(
            "1|VARIANT[LOB]",
            "SELECT GET_IGNORE_CASE(OBJECT_CONSTRUCT('a', 1, 'A', 2), 'a'), "
                + "SYSTEM$TYPEOF(GET_IGNORE_CASE(OBJECT_CONSTRUCT('a', 1, 'A', 2), 'a'))");
        assertAnswer(
            "1|VARIANT[LOB]",
            "SELECT GET_IGNORE_CASE(OBJECT_CONSTRUCT('Ab', 1, 'aB', 2), 'AB'), "
                + "SYSTEM$TYPEOF(GET_IGNORE_CASE(OBJECT_CONSTRUCT('Ab', 1, 'aB', 2), 'AB'))");
        assertAnswer(
            "1|VARIANT[LOB]",
            "SELECT GET_IGNORE_CASE(OBJECT_CONSTRUCT('Ab', 1, 'aB', 2), 'ab'), "
                + "SYSTEM$TYPEOF(GET_IGNORE_CASE(OBJECT_CONSTRUCT('Ab', 1, 'aB', 2), 'ab'))");
        assertAnswer(
            "1|VARIANT[LOB]",
            "SELECT GET_IGNORE_CASE(OBJECT_CONSTRUCT('b', 1), 'B'), "
                + "SYSTEM$TYPEOF(GET_IGNORE_CASE(OBJECT_CONSTRUCT('b', 1), 'B'))");
        assertAnswer(
            "null|VARIANT[LOB]",
            "SELECT GET_IGNORE_CASE(OBJECT_CONSTRUCT('b', 1), 'c'), "
                + "SYSTEM$TYPEOF(GET_IGNORE_CASE(OBJECT_CONSTRUCT('b', 1), 'c'))");
        assertAnswer(
            "1.5|VARIANT[LOB]",
            "SELECT GET_IGNORE_CASE(OBJECT_CONSTRUCT('b', 1.50), 'B'), "
                + "SYSTEM$TYPEOF(GET_IGNORE_CASE(OBJECT_CONSTRUCT('b', 1.50), 'B'))");
        assertAnswer(
            "2|VARIANT[LOB]",
            "SELECT GET_IGNORE_CASE(PARSE_JSON('{\"b\": 2.0}'), 'B'), "
                + "SYSTEM$TYPEOF(GET_IGNORE_CASE(PARSE_JSON('{\"b\": 2.0}'), 'B'))");
        assertAnswer(
            "null|VARIANT[LOB]",
            "SELECT GET_IGNORE_CASE(ARRAY_CONSTRUCT(7, 8), 1), SYSTEM$TYPEOF(GET_IGNORE_CASE(ARRAY_CONSTRUCT(7, "
                + "8), 1))");
        assertAnswer(
            "null|VARIANT[LOB]",
            "SELECT GET_IGNORE_CASE(OBJECT_CONSTRUCT('b', 1), NULL), "
                + "SYSTEM$TYPEOF(GET_IGNORE_CASE(OBJECT_CONSTRUCT('b', 1), NULL))");
    }
}
