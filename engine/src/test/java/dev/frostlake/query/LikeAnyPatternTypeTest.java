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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * LIKE ANY's, LIKE ALL's and ILIKE ANY's patterns (live-verified): a list of one ROW is the list of its values, in
 * as many parentheses as it is written, where a ROW beside another pattern stays a ROW; a subject or a pattern that
 * is a ROW, an ARRAY, an OBJECT, a MAP, a BINARY or a VECTOR is refused by its type, naming LIKE_ANY, LIKE_ALL or
 * ILIKE_ANY with the ESCAPE's type, a NULL pattern skipped. LIKE and ILIKE refuse those types alike, nowhere for
 * the NOT spellings, and LIKE ANY's query is matched as LIKE matches its rows.
 */
public class LikeAnyPatternTypeTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void createTheTables() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR)", "Table FZ successfully created."},
            {"INSERT INTO fz VALUES (5, TRUE, 'a'), (7, FALSE, 'b')", "2"},
            {"CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)", "Table G successfully created."},
            {"INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')", "2"},
            {"CREATE OR REPLACE TABLE pq (n INT, a ARRAY)", "Table PQ successfully created."},
            {"INSERT INTO pq SELECT 1, [1]", "1"},
            {"CREATE OR REPLACE TABLE pq1 (n INT)", "Table PQ1 successfully created."},
            {"INSERT INTO pq1 VALUES (1), (2)", "2"},
            {"CREATE OR REPLACE TABLE pq3 (n INT)", "Table PQ3 successfully created."},
            {"INSERT INTO pq3 VALUES (3)", "1"},
        });
    }

    @Test
    public void aRowWrittenAloneIsThePatternList() {
        assertCells(new String[][] {
            {"SELECT 'a' LIKE ANY (('a', 'b'))", "true"},
            {"SELECT 'c' LIKE ANY (('a', 'b'))", "false"},
            {"SELECT 'a' LIKE ALL (('a', 'b'))", "false"},
            {"SELECT 'a' LIKE ALL (('a', 'a%'))", "true"},
            {"SELECT 'A' ILIKE ANY (('a', 'b'))", "true"},
            {"SELECT 'a' LIKE ANY ((('a', 'b')))", "true"},
            {"SELECT 'a' LIKE ANY (('a', 'b'), 'c')",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, ROW(VARCHAR(1), VARCHAR(1)), VARCHAR(1))"},
            {"SELECT 'c' LIKE ANY (('a', 'b'), 'c')",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, ROW(VARCHAR(1), VARCHAR(1)), VARCHAR(1))"},
            {"SELECT 'd' LIKE ANY (('a', 'b'), ('c', 'd'))",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, ROW(VARCHAR(1), VARCHAR(1)), ROW(VARCHAR(1), VARCHAR(1)))"},
            {"SELECT 'a' LIKE ANY (('a'))", "true"},
            {"SELECT 'a!' LIKE ANY (('a!!', 'b')) ESCAPE '!'", "true"},
            {"SELECT s, s LIKE ANY (('a%', 'b%')) FROM fz ORDER BY id", "a|true ;; b|true"},
            {"SELECT 'a' LIKE ANY (('a', NULL))", "true"},
            {"SELECT 'a' LIKE ANY (('x', NULL))", "false"},
            {"SELECT 'a' LIKE ANY (('a', 1))", "true"},
            {"SELECT 'a' LIKE ANY (('a', 'b')) = TRUE", "true"},
        });
    }

    @Test
    public void aPatternOrSubjectOfAnotherTypeIsRefused() {
        assertCells(new String[][] {
            {"SELECT 'a' LIKE ANY ['a']",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, ARRAY)"},
            {"SELECT 'a' LIKE ANY ARRAY_CONSTRUCT('a')",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, ARRAY)"},
            {"SELECT 'a' LIKE ANY x'61'",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, BINARY(1))"},
            {"SELECT 'a' LIKE ANY (SELECT 'a', 'b')",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE': (VARCHAR(1), ROW(VARCHAR(1), VARCHAR(1)))"},
            {"SELECT 'a' LIKE ALL ['a']",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ALL': (VARCHAR(1), NULL, ARRAY)"},
            {"SELECT 'a' ILIKE ANY ['a']",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'ILIKE_ANY': (VARCHAR(1), NULL, ARRAY)"},
            {"SELECT 'a' LIKE ANY (['a'])",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, ARRAY)"},
            {"SELECT 'a' LIKE ANY (['a'], 'b')",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, ARRAY, VARCHAR(1))"},
            {"SELECT 'a' LIKE ANY {'a': 1}",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, OBJECT)"},
            {"SELECT 'a' LIKE ANY (x'61', 'a')",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, BINARY(1), VARCHAR(1))"},
            {"SELECT 'a' LIKE ANY TO_VARIANT('a')", "true"},
            {"SELECT 'a' LIKE ANY (SELECT 'a')", "true"},
            {"SELECT 'a' LIKE ANY (SELECT 'a' UNION ALL SELECT 'b')", "true"},
            {"SELECT 'a' LIKE ANY 1", "false"},
            {"SELECT 'a' LIKE ANY NULL", "null"},
            {"SELECT 'a' LIKE ANY (NULL, 'a')", "true"},
            {"SELECT 'a' LIKE ['a']",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE': (VARCHAR(1), ARRAY)"},
            {"SELECT 'a' LIKE x'61'",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE': (VARCHAR(1), BINARY(1))"},
            {"SELECT ['a'] LIKE ANY ('a')",
                "REFUSED SQL compilation error: error line 1 at position 13 | Invalid argument types for function "
                + "'LIKE_ANY': (ARRAY, NULL, VARCHAR(1))"},
            {"SELECT 'a' LIKE ANY ('a', ['a'])",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, VARCHAR(1), ARRAY)"},
            {"SELECT 'a' LIKE ANY (TO_VARIANT('a'))", "true"},
            {"SELECT 'a' LIKE ANY PARSE_JSON('\"a\"')", "true"},
            {"SELECT 'a' LIKE ANY OBJECT_CONSTRUCT('a', 1)",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, OBJECT)"},
            {"SELECT 'a' LIKE ANY ('a', x'61')",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, VARCHAR(1), BINARY(1))"},
            {"SELECT a LIKE ANY ('[1]') FROM pq",
                "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for function "
                + "'LIKE_ANY': (ARRAY, NULL, VARCHAR(3))"},
            {"SELECT 'a' LIKE ANY (SELECT s FROM fz)", "true"},
            {"SELECT 'a' LIKE ANY a FROM pq",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, ARRAY)"},
            {"SELECT 'a' LIKE ALL x'61'",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ALL': (VARCHAR(1), NULL, BINARY(1))"},
            {"SELECT 'a' LIKE ANY (SELECT 'a', 'b') ESCAPE '!'",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE': (VARCHAR(1), ROW(VARCHAR(1), VARCHAR(1)), VARCHAR(1))"},
            {"SELECT 'a' LIKE ANY ['a'] ESCAPE '!'",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), VARCHAR(1), ARRAY)"},
            {"SELECT 'a' LIKE ANY (('a', 'b'), NULL)",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, ROW(VARCHAR(1), VARCHAR(1)))"},
            {"SELECT 'a' LIKE ANY (('a', ['b']))",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, VARCHAR(1), ARRAY)"},
            {"SELECT 'a' LIKE ANY (('a', x'61'))",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, VARCHAR(1), BINARY(1))"},
            {"SELECT 'a' LIKE ANY ((('a', 'b'), 'c'))",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, ROW(VARCHAR(1), VARCHAR(1)), VARCHAR(1))"},
            {"SELECT 'a' LIKE ANY ((('a', 'b')), 'c')",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, ROW(VARCHAR(1), VARCHAR(1)), VARCHAR(1))"},
            {"SELECT {'a': 1} LIKE ANY ('a')",
                "REFUSED SQL compilation error: error line 1 at position 16 | Invalid argument types for function "
                + "'LIKE_ANY': (OBJECT, NULL, VARCHAR(1))"},
            {"SELECT x'61' LIKE ANY ('a')",
                "REFUSED SQL compilation error: error line 1 at position 13 | Invalid argument types for function "
                + "'LIKE_ANY': (BINARY(1), NULL, VARCHAR(1))"},
            {"SELECT 1 LIKE ANY ('1')", "true"},
            {"SELECT TO_VARIANT('a') LIKE ANY ('a')", "true"},
            {"SELECT '2020-01-01'::DATE LIKE ANY ('2020%')", "true"},
            {"SELECT 'a' LIKE ANY (1.5)", "false"},
            {"SELECT 'a' LIKE ANY (TRUE)", "false"},
            {"SELECT 'TRUE' LIKE ANY (TRUE)", "false"},
            {"SELECT 'a' LIKE ANY ('2020-01-01'::DATE)", "false"},
            {"SELECT 'a' LIKE ANY ({'a': 1}::MAP(VARCHAR, INT))",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, MAP(VARCHAR(134217728), NUMBER(38,0)))"},
            {"SELECT 'a' LIKE ANY (SELECT ['a'])",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE': (VARCHAR(1), ARRAY)"},
            {"SELECT 'a' LIKE ANY (SELECT 'a', 'b' FROM fz)",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE': (VARCHAR(1), ROW(VARCHAR(1), VARCHAR(1)))"},
            {"SELECT s LIKE ANY ['a'] FROM fz",
                "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(16777216), NULL, ARRAY)"},
            {"SELECT 'a' LIKE TRUE", "false"},
            {"SELECT 'a' ILIKE ['a']",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'ILIKE': (VARCHAR(1), ARRAY)"},
            {"SELECT 'a' LIKE ['a'] ESCAPE '!'",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE': (VARCHAR(1), ARRAY, VARCHAR(1))"},
            {"SELECT 'a' RLIKE ['a']",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'RLIKE': (VARCHAR(1), ARRAY)"},
            {"SELECT 'a' NOT LIKE ['a']",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'LIKE': (VARCHAR(1), ARRAY)"},
            {"SELECT ['a'] LIKE 'a'",
                "REFUSED SQL compilation error: error line 1 at position 13 | Invalid argument types for function "
                + "'LIKE': (ARRAY, VARCHAR(1))"},
            {"SELECT 'a' LIKE ANY ('a', 'b') ESCAPE '!'", "true"},
            {"SELECT 'a' LIKE ANY (('a', 'b')) ESCAPE NULL", "true"},
            {"SELECT 'a' LIKE ANY ['a', 'b']",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, ARRAY)"},
            {"SELECT 'a' LIKE ANY (['a'], ['b'])",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, ARRAY, ARRAY)"},
            {"SELECT 1 + ('a' LIKE ANY ['a'])",
                "REFUSED SQL compilation error: error line 1 at position 16 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, ARRAY)"},
            {"SELECT 'x' FROM fz WHERE s LIKE ANY x'61'",
                "REFUSED SQL compilation error: error line 1 at position 27 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(16777216), NULL, BINARY(1))"},
            {"SELECT 'a' LIKE ANY (NULL::ARRAY)",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, ARRAY)"},
            {"SELECT 'a' LIKE ANY (('a', 'b')) OR FALSE", "true"},
            {"SELECT 'a' LIKE ALL (('a%', '%a'))", "true"},
            {"SELECT 'a' LIKE ANY ((SELECT 'a'))", "true"},
            {"SELECT 'a' LIKE ANY (((SELECT 'a')))", "true"},
            {"SELECT 'a' IS NULL LIKE ANY ('a', NULL)",
                "REFUSED SQL compilation error: error line 1 at position 19 | Invalid argument types for function "
                + "'LIKE_ANY': (BOOLEAN, NULL, VARCHAR(1))"},
            {"SELECT 'a' LIKE ANY ('a', NULL) IS NULL",
                "REFUSED SQL compilation error: error line 1 at position 32 | Invalid argument types for function "
                + "'IS NULL': (ROW(VARCHAR(1), NULL))"},
            {"SELECT 'a' LIKE ANY ([1, 2]::VECTOR(INT, 2))",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, VECTOR(INT, 2))"},
            {"SELECT NULL LIKE ANY (['a'])",
                "REFUSED SQL compilation error: error line 1 at position 12 | Invalid argument types for function "
                + "'LIKE_ANY': (NULL, NULL, ARRAY)"},
            {"SELECT 'a' LIKE ANY (CAST(NULL AS VARCHAR), ['a'])",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE_ANY': (VARCHAR(1), NULL, VARCHAR(134217728), ARRAY)"},
            {"SELECT 'a' LIKE ANY ((1, 2))", "false"},
            {"SELECT 'a' ILIKE ANY (SELECT ['a'])",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'ILIKE_ANY': (VARCHAR(1), NULL, ARRAY)"},
            {"SELECT 'a' NOT ILIKE ['a']",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'ILIKE': (VARCHAR(1), ARRAY)"},
            {"SELECT 'a' NOT LIKE x'61'",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'LIKE': (VARCHAR(1), BINARY(1))"},
        });
    }

    @Test
    public void notLikeAnyStaysASyntaxError() {
        assertCells(new String[][] {
            {"SELECT 'a' NOT LIKE ANY ('a', 'b')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax "
                + "error line 1 at position 25 unexpected ''a''. | syntax error line 1 at position 33 unexpected ')'."},
            {"SELECT 'a' NOT LIKE ANY ('a')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax "
                + "error line 1 at position 25 unexpected ''a''."},
            {"SELECT 'a' NOT LIKE ANY ((1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax "
                + "error line 1 at position 25 unexpected '('."},
        });
    }
}
