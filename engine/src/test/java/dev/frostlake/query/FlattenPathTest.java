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
 * FLATTEN's PATH reads the paths GET_PATH reads with a row: two positions in a row, quoted and bracketed
 * names, a colon for a dot, a separator first. An empty and a NULL path select the whole input, a path that
 * finds nothing or a scalar answers no rows - one all-NULL row with OUTER - and a path that cannot be read is
 * {@code Bad compound object's field path name 'a.' in FLATTEN}. The PATH column spells a path its own way,
 * whatever was written: a member that is no plain name - empty, a digit first, anything but letters, digits
 * and underscores - goes between brackets and single quotes, with a quote, a backslash and a line break
 * escaped. Every answer is live-verified.
 */
public class FlattenPathTest extends MeasuredCellsTestSupport {

    @Test
    public void pathsThatSelect() {
        assertAnswer(
            "[7,8]",
            "SELECT PARSE_JSON('{\"a\":[[1,[7,8]],2]}'):a[0][1] AS direct");
        assertAnswer(
            "null|a[0][1][0]|0|7 ;; null|a[0][1][1]|1|8",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => "
                + "'a[0][1]')) ORDER BY path, index");
        assertAnswer(
            "[0][1][0]|7 ;; [0][1][1]|8",
            "SELECT path, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[[1,[7,8]],2]'), PATH => '[0][1]')) ORDER "
                + "BY path");
        assertAnswer(
            "[1,2]",
            "SELECT PARSE_JSON('{\"a b\":{\"c\":[1,2]}}'):\"a b\".c AS direct2");
        assertAnswer(
            "null|['a b'].c[0]|0|1 ;; null|['a b'].c[1]|1|2",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => '\"a "
                + "b\".c')) ORDER BY path, index");
        assertAnswer(
            "null|['a.b'][0]|0|3 ;; null|['a.b'][1]|1|4",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => "
                + "'\"a.b\"')) ORDER BY path, index");
        assertAnswer(
            "null|a[0]|0|[1,[7,8]] ;; null|a[1]|1|2",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => 'a')) "
                + "ORDER BY path, index");
        assertAnswer(
            "null|a[0][0]|0|1 ;; null|a[0][1]|1|[7,8]",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => 'a[0]')) "
                + "ORDER BY path, index");
        assertAnswer(
            "null|k.m.n[0]|0|5",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => 'k.m.n')) "
                + "ORDER BY path, index");
        assertAnswer(
            "n|k.m.n|null|[5]",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => 'k.m')) "
                + "ORDER BY path, index");
        assertAnswer(
            "n|k.m.n|null|[5]",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => "
                + "'k[''m'']')) ORDER BY path, index");
        assertAnswer(
            "null|k.m.n[0]|0|5",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => "
                + "'[''k''][''m''].n')) ORDER BY path, index");
        assertAnswer(
            "n|k.m.n|null|[5]",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => "
                + "'k[\"m\"]')) ORDER BY path, index");
        assertAnswer(
            "null|A[0]|0|9",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => 'A')) "
                + "ORDER BY path, index");
        assertAnswer(
            "A|A|null|[9] ;; a b|['a b']|null|{\"c\":[1,2]} ;; a.b|['a.b']|null|[3,4] ;; a|a|null|[[1,[7,8]],2] "
                + ";; k|k|null|{\"m\":{\"n\":[5]}} ;; s|s|null|1",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => '')) "
                + "ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => 's')) "
                + "ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => 'nope')) "
                + "ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => 'a[9]')) "
                + "ORDER BY path, index");
        assertAnswer(
            "null|null|null|null",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => 's', "
                + "OUTER => TRUE)) ORDER BY path, index");
        assertAnswer(
            "null|null|null|null",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => 'nope.x', "
                + "OUTER => TRUE)) ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => 'a[1]')) "
                + "ORDER BY path, index");
        assertAnswer(
            "null|null|null|null",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => 'a[1]', "
                + "OUTER => TRUE)) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a.' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => 'a.')) "
                + "ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a[' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => 'a[')) "
                + "ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a[x]' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => 'a[x]')) "
                + "ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => 'a[-1]')) "
                + "ORDER BY path, index");
        assertAnswer(
            "null|a[0]|0|[1,[7,8]] ;; null|a[1]|1|2",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => '.a')) "
                + "ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a..b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => 'a..b')) "
                + "ORDER BY path, index");
        assertAnswer(
            "null|a[0][0]|0|1 ;; null|a[0][1]|1|[7,8]",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => 'a[ 0 "
                + "]')) ORDER BY path, index");
        assertAnswer(
            "null|a[0][0]|0|1 ;; null|a[0][1]|1|[7,8]",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => 'a [0]')) "
                + "ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name ' a' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => ' a')) "
                + "ORDER BY path, index");
        assertAnswer(
            "n|k.m.n|null|[5] ;; null|k.m.n[0]|0|5",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => 'k.m', "
                + "RECURSIVE => TRUE)) ORDER BY path, index");
        assertAnswer(
            "n|k.m.n|null|[5]",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => "
                + "'\"k\".\"m\"')) ORDER BY path, index");
        assertAnswer(
            "n|k.m.n|null|[5]",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => 'k:m')) "
                + "ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => "
                + "'a[0][1][0]')) ORDER BY path, index");
        assertAnswer(
            "A|A|null|[9] ;; a b|['a b']|null|{\"c\":[1,2]} ;; a.b|['a.b']|null|[3,4] ;; a|a|null|[[1,[7,8]],2] "
                + ";; k|k|null|{\"m\":{\"n\":[5]}} ;; s|s|null|1",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => NULL)) "
                + "ORDER BY path, index");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter 'PATH'",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[[1,[7,8]],2],\"a "
                + "b\":{\"c\":[1,2]},\"a.b\":[3,4],\"k\":{\"m\":{\"n\":[5]}},\"s\":1,\"A\":[9]}'), PATH => 5)) ORDER "
                + "BY path, index");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM (SELECT 'a' AS p) t, TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[1]}'), PATH => "
                + "t.p))");
    }

    @Test
    public void howThePathColumnSpellsAName() {
        assertAnswer(
            "|[''] ;; $a|['$a'] ;; 0|['0'] ;; 1a|['1a'] ;; A_B|A_B ;; _a|_a ;; a b|['a b'] ;; a\"b|['a\"b'] ;; "
                + "a$|['a$'] ;; a'b|['a\\'b'] ;; a-b|['a-b'] ;; a1|a1 ;; a:b|['a:b'] ;; a[0]|['a[0]'] ;; arr|arr ;; "
                + "n|n ;; \u00e9|\u00e9",
            "SELECT key, path FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,\"_a\":4,"
                + "\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,\"a:b\":13,"
                + "\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'))) ORDER BY key");
        assertAnswer(
            "x y|['x y']|null ;; p q|['x y']['p q']|null ;; null|['x y']['p q'][0]|0 ;; t|['x y']['p "
                + "q'][0].t|null ;; r s|['x y']['p q'][0]['r s']|null ;; u|u|null ;; v w|u['v w']|null",
            "SELECT key, path, index FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"x y\":{\"p q\":[{\"r s\":1,"
                + "\"t\":2}]},\"u\":{\"v w\":3}}'), RECURSIVE => TRUE)) ORDER BY path");
        assertAnswer(
            "null|['a b']|null|null",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a b\":[]}'), PATH => '\"a "
                + "b\"', OUTER => TRUE)) ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'[''a''''b'']')) ORDER BY path, index");
        assertAnswer(
            "null|['a\\'b'][0]|0|1",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a''b\":[1]}'), PATH => "
                + "'\"a''b\"')) ORDER BY path, index");
        assertAnswer(
            "null|k[0]|0|1",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"k\":[1]}'), PATH => "
                + "'[\"k\"]')) ORDER BY path, index");
        assertAnswer(
            "null|['a b'][0]|0|1",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a b\":[1]}'), PATH => "
                + "'[''a b'']')) ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"0\":[1]}'), PATH => "
                + "'[0]')) ORDER BY path, index");
        assertAnswer(
            "null|['0'][0]|0|1",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"0\":[1]}'), PATH => "
                + "'[''0'']')) ORDER BY path, index");
        assertAnswer(
            "null|['0'][0]|0|1",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"0\":[1]}'), PATH => "
                + "'\"0\"')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name '0' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"0\":[1]}'), PATH => '0')) "
                + "ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[[1],[2]]'), PATH => "
                + "'[''1'']')) ORDER BY path, index");
        assertAnswer(
            "null|[1][0]|0|2",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[[1],[2]]'), PATH => '[1]')) "
                + "ORDER BY path, index");
        assertAnswer(
            "e|['a-b'].e|null|2 ;; c-d|['a-b']['c-d']|null|1",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":{\"c-d\":1,"
                + "\"e\":2}}'), PATH => '\"a-b\"')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a-b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":{\"c-d\":1,"
                + "\"e\":2}}'), PATH => 'a-b')) ORDER BY path, index");
        assertAnswer(
            "|['']['']|null|1",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"\":{\"\":1}}'), PATH => "
                + "'\"\"')) ORDER BY path, index");
        assertAnswer(
            "|[''] ;; |['']['']",
            "SELECT key, path FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"\":{\"\":1}}'), RECURSIVE => TRUE)) "
                + "ORDER BY path");
        assertAnswer(
            "a\\b|['a\\\\b'] ;; c | d|['c\\nd'] ;; e\tf|['e\\tf']",
            "SELECT key, path FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\\\\\\\\b\":1,\"c\\\\nd\":2,"
                + "\"e\\\\tf\":3}'))) ORDER BY key");
        assertAnswer(
            "9|['9'] ;; Ab|Ab ;; a1_$|['a1_$'] ;; a9|a9 ;; aB|aB",
            "SELECT key, path FROM TABLE(FLATTEN(INPUT => OBJECT_CONSTRUCT('Ab', 1, 'aB', 2, 'a1_$', 3, '9', 4, "
                + "'a9', 5))) ORDER BY key");
        assertAnswer(
            "c|n.b.c|null|[4,5] ;; null|n.b.c[0]|0|4 ;; null|n.b.c[1]|1|5",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => 'n.b', "
                + "RECURSIVE => TRUE)) ORDER BY path, index");
        assertAnswer(
            "c|n.b.c|null|[4,5]",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'n:b')) ORDER BY path, index");
        assertAnswer(
            "null|arr[0]|0|10 ;; null|arr[1]|1|20 ;; null|arr[2]|2|30",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'arr')) ORDER BY path, index");
        assertAnswer(
            "c|n.b.c|null|[4,5]",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => 'n . "
                + "b')) ORDER BY path, index");
        assertAnswer(
            "c|n.b.c|null|[4,5]",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'n[''b'' ]')) ORDER BY path, index");
        assertAnswer(
            "null|arr[0]|0|10 ;; null|arr[1]|1|20 ;; null|arr[2]|2|30",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => 'arr "
                + "')) ORDER BY path, index");
        assertAnswer(
            "c|n.b.c|null|[4,5]",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => 'n "
                + ".b')) ORDER BY path, index");
        assertAnswer(
            "c|n.b.c|null|[4,5]",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => 'n. "
                + "b')) ORDER BY path, index");
    }

    @Test
    public void pathsThatAreRefused() {
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr.1' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'arr.1')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr.[1]' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'arr.[1]')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr[1]b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'arr[1]b')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr[1].' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'arr[1].')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => 'a "
                + "b')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr[1' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'arr[1')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr[]' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'arr[]')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr]' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'arr]')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n['b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'n[''b')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name '\"n' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'\"n')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name '[' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => '[')) "
                + "ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name '.' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => '.')) "
                + "ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'n.\"\"')) ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'n['''']')) ORDER BY path, index");
        assertAnswer(
            "c|n.b.c|null|[4,5]",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'n\"b\"')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name '\"n\"b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'\"n\"b')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n[b]' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'n[b]')) ORDER BY path, index");
        assertAnswer(
            "b|n.b|null|{\"c\":[4,5]}",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => ':n')) "
                + "ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n:' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => 'n:')) "
                + "ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'arr[01]')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr[+1]' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'arr[+1]')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr[1.0]' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'arr[1.0]')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr[1e0]' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'arr[1e0]')) ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'arr[99999999999999999999]')) ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'arr[\"1\"]')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a,b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => 'a,"
                + "b')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a/b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'a/b')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name '-a' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => '-a')) "
                + "ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a\tb' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'a\tb')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr[ ]' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => 'arr[ "
                + "]')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name ':' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => ':')) "
                + "ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name ']' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => ']')) "
                + "ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name '\"' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => '\"')) "
                + "ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name ''' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => '''')) "
                + "ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n.:b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'n.:b')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n::b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'n::b')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name '..n' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'..n')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name '.[0]' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'.[0]')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name '.' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => '.')) "
                + "ORDER BY path, index");
        assertAnswer(
            "b|n.b|null|{\"c\":[4,5]}",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => '. "
                + "n')) ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'n[''b''][''c''] [0]')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name '1a' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => '1a')) "
                + "ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a-b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'a-b')) ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => 'a$')) "
                + "ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => '$a')) "
                + "ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'\u00e9')) ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'a:b')) ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'a@b')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a+b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'a+b')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a=b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'a=b')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a*b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'a*b')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a(b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'a(b')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a#b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'a#b')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a%b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a-b\":1,\"1a\":2,\"a1\":3,"
                + "\"_a\":4,\"a$\":5,\"\u00e9\":6,\"\":7,\"a''b\":8,\"a\\\\\"b\":9,\"0\":10,\"A_B\":11,\"a[0]\":12,"
                + "\"a:b\":13,\"$a\":14,\"a b\":15,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,20,30]}'), PATH => "
                + "'a%b')) ORDER BY path, index");
    }

    @Test
    public void stepsWithNoSeparatorAndBlanks() {
        assertAnswer(
            "c|n.b.c|null|[4,5]",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'n\"b\"')) "
                + "ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name '\"n\"b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => '\"n\"b')) "
                + "ORDER BY path, index");
        assertAnswer(
            "c|n.b.c|null|[4,5]",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'n \"b\"')) "
                + "ORDER BY path, index");
        assertAnswer(
            "null|n.b.c[0]|0|4 ;; null|n.b.c[1]|1|5",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => "
                + "'n\"b\".\"c\"')) ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => "
                + "'arr[1]\"x\"')) ORDER BY path, index");
        assertAnswer(
            "c|n.b.c|null|[4,5]",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => '\"n\" .b')) "
                + "ORDER BY path, index");
        assertAnswer(
            "c|n.b.c|null|[4,5]",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'n.b ')) "
                + "ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => '@a')) ORDER "
                + "BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'a@')) ORDER "
                + "BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a~b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'a~b')) ORDER "
                + "BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a|b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'a|b')) ORDER "
                + "BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a'b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'a''b')) "
                + "ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a\\b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'a\\\\b')) "
                + "ORDER BY path, index");
        assertAnswer(
            "null|n.b.c[0]|0|4 ;; null|n.b.c[1]|1|5",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => "
                + "'n[\"b\"]\"c\"')) ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => "
                + "'\"n\"\"b\"')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n\"b\"c' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'n\"b\"c')) "
                + "ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n 'b'' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'n ''b''')) "
                + "ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'arr[1] "
                + "[0]')) ORDER BY path, index");
        assertAnswer(
            "null|arr[1][0]|0|20 ;; null|arr[1][1]|1|21",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'arr[1] ')) "
                + "ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'arr[-1]')) "
                + "ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'arr[-2]')) "
                + "ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'arr[-0]')) "
                + "ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'arr[ -1 ]')) "
                + "ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr[- 1]' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'arr[- 1]')) "
                + "ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => "
                + "'arr[2147483648]')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'arr[1].[0]' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => "
                + "'arr[1].[0]')) ORDER BY path, index");
        assertAnswer(
            "c|n.b.c|null|[4,5]",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'n\t.b')) "
                + "ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => '$')) ORDER "
                + "BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => '_')) ORDER "
                + "BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => "
                + "'\u65e5\u672c')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a!b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'a!b')) ORDER "
                + "BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a&b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'a&b')) ORDER "
                + "BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a<b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'a<b')) ORDER "
                + "BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a>b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'a>b')) ORDER "
                + "BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a?b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'a?b')) ORDER "
                + "BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a;b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'a;b')) ORDER "
                + "BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a{b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'a{b')) ORDER "
                + "BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a}b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'a}b')) ORDER "
                + "BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a)b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'a)b')) ORDER "
                + "BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a^b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'a^b')) ORDER "
                + "BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'a`b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'a`b')) ORDER "
                + "BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name ''n'' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => '''n''')) "
                + "ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n.b.c.' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'n.b.c.')) "
                + "ORDER BY path, index");
        assertAnswer(
            "",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'arr[1][0]')) "
                + "ORDER BY path, index");
        assertAnswer(
            "null|arr[1][0]|0|20 ;; null|arr[1][1]|1|21",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,\"a$\":5,\"$a\":14,"
                + "\"@a\":16,\"a@\":17,\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30]}'), PATH => 'arr[1]')) "
                + "ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name '.  n' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,"
                + "\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), PATH => '.  n')) ORDER BY path, index");
        assertAnswer(
            "c|n.b.c|null|[4,5]",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,"
                + "\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), PATH => 'n  .b')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n.  b' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,"
                + "\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), PATH => 'n.  b')) ORDER BY path, index");
        assertAnswer(
            "null|arr[1][0]|0|20 ;; null|arr[1][1]|1|21",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,"
                + "\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), PATH => 'arr  [1]')) ORDER BY path, index");
        assertAnswer(
            "null|arr[1][0]|0|20 ;; null|arr[1][1]|1|21",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,"
                + "\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), PATH => 'arr[  1  ]')) ORDER BY path, index");
        assertAnswer(
            "null|arr[1][0]|0|20 ;; null|arr[1][1]|1|21",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,"
                + "\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), PATH => 'arr[1]  ')) ORDER BY path, index");
        assertAnswer(
            "b|n.b|null|{\"c\":[4,5]}",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,"
                + "\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), PATH => 'n  ')) ORDER BY path, index");
        assertAnswer(
            "c|n.b.c|null|[4,5]",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,"
                + "\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), PATH => '\"n\"  .b')) ORDER BY path, index");
        assertAnswer(
            "c|n.b.c|null|[4,5]",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,"
                + "\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), PATH => 'n. \"b\"')) ORDER BY path, index");
        assertAnswer(
            "REFUSED Bad compound object's field path name 'n.'b'' in FLATTEN",
            "SELECT key, path, index, value FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a1\":3,"
                + "\"n\":{\"b\":{\"c\":[4,5]}},\"arr\":[10,[20,21],30],\"c\\\\nd\":2,\"e\\\\\\\\f\":3,\"g\\\\th\":4,"
                + "\"u\\\\u00e9\":5,\"\\\\u00ff\":6,\"q\\\\\"r\":7}'), PATH => 'n.''b''')) ORDER BY path, index");
    }
}
