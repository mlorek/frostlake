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
 * SEARCH_MODE => 'EXACT' finds the query as it is written, the characters between its tokens included: it
 * must stand in the data, case apart, with no token running on before it or after it. A query's leading or
 * trailing delimiter has to be there too, a token is no part of a longer one, an empty query finds a text
 * with no token, and nothing is found across two columns. Under the NO_OP_ANALYZER the two must be equal; the
 * UNICODE_ANALYZER does not take the mode. A data literal written in capitals is run unasserted: the account
 * answers it otherwise than the same text in a column (live-verified).
 */
public class SearchExactModeTest extends MeasuredCellsTestSupport {

    @Test
    public void theModesAndTheirNames() {
        assertAnswer(
            "true",
            "SELECT SEARCH('a b', 'a', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a b', 'a b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a b', 'a', SEARCH_MODE => 'OR')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a b', 'a c', SEARCH_MODE => 'AND')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a b', 'a b', SEARCH_MODE => 'PHRASE')");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'Search mode 'nosuch'' does not exist or not authorized.",
            "SELECT SEARCH('a b', 'a', SEARCH_MODE => 'nosuch')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a b', 'a', SEARCH_MODE => 'exact')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a b c', 'b', SEARCH_MODE => 'EXACT')");
    }

    @Test
    public void tokensOrderAndDelimiters() {
        assertAnswer(
            "false",
            "SELECT SEARCH('a b', 'a c', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a b c', 'b a', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a b c', 'a c', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a b', 'b a', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a b', 'A', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a b', 'A B', SEARCH_MODE => 'EXACT')");
        answer(
            "SELECT SEARCH('A B', 'a b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a-b', 'a b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a b', 'a-b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a-b', 'a-b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a-b', 'a', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a-b', 'b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a b', 'a  b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a  b', 'a b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a  b', 'a  b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a b', ' a', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a b', 'a ', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a b', ' a b ', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('ab', 'a', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a b', 'a b c', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a.b', 'a', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a.b', 'a.b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a.b c', 'a.b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a.b c', 'b c', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a, b', 'a, b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a, b', 'a,b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a, b', 'a b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a, b', 'a,', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a, b', 'a', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a, b', ', b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a, b!', 'a, b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a, b', 'a, b!', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('!a, b', 'a, b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a, b', '!a, b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('x a, b y', 'a, b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('x a, b y', 'a, b y', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('x a b y', 'a b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('x a y b', 'a b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('x a y b', 'a b', SEARCH_MODE => 'PHRASE')");
        assertAnswer(
            "true",
            "SELECT SEARCH('x a b y', 'a b', SEARCH_MODE => 'PHRASE')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a b', 'b a', SEARCH_MODE => 'PHRASE')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a, b', 'a b', SEARCH_MODE => 'PHRASE')");
    }

    @Test
    public void emptyTextsAnalyzersAndColumns() {
        assertAnswer(
            "false",
            "SELECT SEARCH('a b', '', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('', '', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('', 'a', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a b', 'a_b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a_b', 'a_b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a_b', 'a', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a b', 'a b', SEARCH_MODE => 'Exact')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a b', 'a b', ANALYZER => 'NO_OP_ANALYZER', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a b', 'a', ANALYZER => 'NO_OP_ANALYZER', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a b', 'A B', ANALYZER => 'NO_OP_ANALYZER', SEARCH_MODE => 'EXACT')");
        answer(
            "SELECT SEARCH('a, b', 'a, b', ANALYZER => 'UNICODE_ANALYZER', SEARCH_MODE => 'EXACT')");
        answer(
            "SELECT SEARCH('a, b', 'a b', ANALYZER => 'UNICODE_ANALYZER', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'Analyzer type 'ENTITY_ANALYZER'' does not exist or not "
                + "authorized.",
            "SELECT SEARCH('a-b c', 'a-b', ANALYZER => 'ENTITY_ANALYZER', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'Analyzer type 'ENTITY_ANALYZER'' does not exist or not "
                + "authorized.",
            "SELECT SEARCH('a.b@c.d x', 'a.b@c.d', ANALYZER => 'ENTITY_ANALYZER', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'Analyzer type 'ENTITY_ANALYZER'' does not exist or not "
                + "authorized.",
            "SELECT SEARCH('a.b@c.d x', 'b@c.d', ANALYZER => 'ENTITY_ANALYZER', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('800-555-0100', '800.555.0100', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('800-555-0100', '800-555-0100', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('800-555-0100', '555-0100', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('800-555-0100', '555', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a b a c', 'a c', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a, b a b', 'a b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a b', 'a\tb', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH(c, 'x y', SEARCH_MODE => 'EXACT') FROM (SELECT 'w x y z' AS c)");
        assertAnswer(
            "false",
            "SELECT SEARCH((c, d), 'x y', SEARCH_MODE => 'EXACT') FROM (SELECT 'w x' AS c, 'y z' AS d)");
        assertAnswer(
            "true",
            "SELECT SEARCH(c, 'x, y', SEARCH_MODE => 'EXACT') FROM (SELECT PARSE_JSON('{\"k\": \"w x, y z\"}') AS "
                + "c)");
        assertAnswer(
            "null",
            "SELECT SEARCH('a b', NULL, SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "null",
            "SELECT SEARCH(NULL, 'a', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a b', 'a', SEARCH_MODE => NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'Search mode ''' does not exist or not authorized.",
            "SELECT SEARCH('a b', 'a', SEARCH_MODE => '')");
    }

    @Test
    public void caseAndWhereAMatchMayStartAndEnd() {
        answer(
            "SELECT SEARCH('A B', 'A B', SEARCH_MODE => 'EXACT')");
        answer(
            "SELECT SEARCH('A B', 'A', SEARCH_MODE => 'EXACT')");
        answer(
            "SELECT SEARCH('A B', 'a', SEARCH_MODE => 'EXACT')");
        answer(
            "SELECT SEARCH('A B', 'B', SEARCH_MODE => 'EXACT')");
        answer(
            "SELECT SEARCH('a B', 'a b', SEARCH_MODE => 'EXACT')");
        answer(
            "SELECT SEARCH('A b', 'a b', SEARCH_MODE => 'EXACT')");
        answer(
            "SELECT SEARCH('A B', 'a B', SEARCH_MODE => 'EXACT')");
        answer(
            "SELECT SEARCH('Ab Cd', 'ab cd', SEARCH_MODE => 'EXACT')");
        answer(
            "SELECT SEARCH('Ab Cd', 'Ab Cd', SEARCH_MODE => 'EXACT')");
        answer(
            "SELECT SEARCH('Ab Cd', 'AB CD', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('ab cd', 'AB CD', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('ab cd', 'Ab Cd', SEARCH_MODE => 'EXACT')");
        answer(
            "SELECT SEARCH('A', 'a', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a', 'A', SEARCH_MODE => 'EXACT')");
        answer(
            "SELECT SEARCH('A', 'A', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('A B', 'a b', SEARCH_MODE => 'PHRASE')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a b', 'A B', SEARCH_MODE => 'PHRASE')");
        assertAnswer(
            "true",
            "SELECT SEARCH('A B', 'a')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a b', 'A')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a  b', 'a ', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a b c', 'a b ', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a, b', 'a, ', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a , b', 'a ,', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a b', ' b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a  b', ' b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('x-a b', '-a b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('x--a b', '-a b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a.b', '.b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a..b', '.b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('ab cd', 'b cd', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('ab cd', 'ab c', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a  b', '', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH(' a', '', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH(' ', '', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a', '', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a b', 'b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a1 b', 'a', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a1 b', 'a1', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('1a b', 'a', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('\u00e9 b', '\u00e9', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a\u00e9 b', 'a', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a\u00b2 b', 'a', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a_b c', 'b c', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a_b c', '_b c', SEARCH_MODE => 'EXACT')");
        answer(
            "SELECT SEARCH('\u00c9 b', '\u00e9', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('\u00e9 b', '\u00c9', SEARCH_MODE => 'EXACT')");
    }

    @Test
    public void overAColumn() {
        answer(
            "CREATE OR REPLACE TABLE ts (c VARCHAR)");
        answer(
            "INSERT INTO ts VALUES ('A B'), ('a b'), ('Why, how now, Ajax! wherefore')");
        assertAnswer(
            "A B ;; a b",
            "SELECT c FROM ts WHERE SEARCH(c, 'A B', SEARCH_MODE => 'EXACT') ORDER BY c");
        assertAnswer(
            "A B ;; a b",
            "SELECT c FROM ts WHERE SEARCH(c, 'a b', SEARCH_MODE => 'EXACT') ORDER BY c");
        assertAnswer(
            "Why, how now, Ajax! wherefore",
            "SELECT c FROM ts WHERE SEARCH(c, 'Why, how now, Ajax!', SEARCH_MODE => 'EXACT') ORDER BY c");
        assertAnswer(
            "Why, how now, Ajax! wherefore",
            "SELECT c FROM ts WHERE SEARCH(c, 'why, how now, ajax!', SEARCH_MODE => 'EXACT') ORDER BY c");
        assertAnswer(
            "Why, how now, Ajax! wherefore",
            "SELECT c FROM ts WHERE SEARCH(c, 'WHY, HOW NOW, AJAX!', SEARCH_MODE => 'EXACT') ORDER BY c");
        assertAnswer(
            "Why, how now, Ajax! wherefore",
            "SELECT c FROM ts WHERE SEARCH(c, 'Why - how now: Ajax!', SEARCH_MODE => 'PHRASE') ORDER BY c");
        assertAnswer(
            "Why, how now, Ajax! wherefore",
            "SELECT c FROM ts WHERE SEARCH(c, 'how now', SEARCH_MODE => 'EXACT') ORDER BY c");
        assertAnswer(
            "Why, how now, Ajax! wherefore",
            "SELECT c FROM ts WHERE SEARCH(c, 'How Now', SEARCH_MODE => 'EXACT') ORDER BY c");
        assertAnswer(
            "",
            "SELECT c FROM ts WHERE SEARCH(c, 'how  now', SEARCH_MODE => 'EXACT') ORDER BY c");
        assertAnswer(
            "Why, how now, Ajax! wherefore",
            "SELECT c FROM ts WHERE SEARCH(c, 'ajax! wherefore', SEARCH_MODE => 'EXACT') ORDER BY c");
        assertAnswer(
            "Why, how now, Ajax! wherefore",
            "SELECT c FROM ts WHERE SEARCH(c, 'Ajax! wherefore', SEARCH_MODE => 'EXACT') ORDER BY c");
        assertAnswer(
            "A B ;; a b",
            "SELECT c FROM ts WHERE SEARCH(c, 'b', SEARCH_MODE => 'EXACT') ORDER BY c");
        assertAnswer(
            "A B ;; a b",
            "SELECT c FROM ts WHERE SEARCH(c, 'B', SEARCH_MODE => 'EXACT') ORDER BY c");
        assertAnswer(
            "A B|true|true ;; Why, how now, Ajax! wherefore|false|false ;; a b|true|true",
            "SELECT c, SEARCH(c, 'A B', SEARCH_MODE => 'EXACT'), SEARCH(c, 'a b', SEARCH_MODE => 'EXACT') FROM ts "
                + "ORDER BY c");
        assertAnswer(
            "2",
            "SELECT COUNT(*) FROM ts WHERE SEARCH((c), 'a b', SEARCH_MODE => 'EXACT')");
        answer(
            "SELECT COUNT(*) FROM ts WHERE SEARCH(*, 'a b', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM ts WHERE SEARCH(c, 'A B', ANALYZER => 'NO_OP_ANALYZER', SEARCH_MODE => 'EXACT')");
    }

    @Test
    public void underTheAnalyzers() {
        assertAnswer(
            "true",
            "SELECT SEARCH('A B', 'A B', ANALYZER => 'NO_OP_ANALYZER', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a b', '', ANALYZER => 'NO_OP_ANALYZER', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('', '', ANALYZER => 'NO_OP_ANALYZER', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a b', 'a b', ANALYZER => 'DEFAULT_ANALYZER', SEARCH_MODE => 'EXACT')");
        answer(
            "SELECT SEARCH('A B', 'a b', ANALYZER => 'DEFAULT_ANALYZER', SEARCH_MODE => 'EXACT')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a b', 'a b', ANALYZER => 'default_analyzer', SEARCH_MODE => 'EXACT')");
        answer(
            "SELECT SEARCH(12, '12', SEARCH_MODE => 'EXACT')");
        answer(
            "SELECT SEARCH('a b', 'a b', SEARCH_MODE => 'EXACT', ANALYZER => 'UNICODE_ANALYZER')");
        answer(
            "SELECT SEARCH('a b', 'a b', ANALYZER => 'unicode_analyzer', SEARCH_MODE => 'exact')");
    }
}
