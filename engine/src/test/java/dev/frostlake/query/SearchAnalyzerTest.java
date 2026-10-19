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
 * SEARCH cuts its text by the analyzer named: the default one on everything that is no letter or digit, the
 * Unicode one at the word boundaries, so what a point, an apostrophe or an underscore joins stays whole, and
 * the no-op one not at all. PHRASE asks for the query's tokens side by side. The options are judged while the
 * statement compiles - over a table with no row too - and each is a text literal or NULL. Every answer is
 * live-verified.
 */
public class SearchAnalyzerTest extends MeasuredCellsTestSupport {

    @Test
    public void theTokensAnAnalyzerCuts() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        engine.execute(
            "INSERT INTO t VALUES (1)");
        engine.execute(
            "CREATE OR REPLACE VIEW vw AS SELECT a FROM t");
        engine.execute(
            "CREATE OR REPLACE TAG tg");
        engine.execute(
            "CREATE OR REPLACE TAG tg2");
        engine.execute(
            "ALTER TABLE t SET TAG tg2 = 'v1'");
        engine.execute(
            "ALTER TABLE t MODIFY COLUMN a SET TAG tg2 = 'c1'");
        engine.execute(
            "CREATE OR REPLACE TABLE ts (a VARCHAR)");
        engine.execute(
            "INSERT INTO ts VALUES ('x y'), ('X-Y z.w'), ('hello world'), (NULL)");
        engine.execute(
            "CREATE OR REPLACE TABLE te (a VARCHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'PATTERN_ANALYZER' does not exist or not authorized.",
            "SELECT SEARCH(a, 'x', ANALYZER => 'PATTERN_ANALYZER') FROM te");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'PATTERN_ANALYZER' does not exist or not authorized.",
            "SELECT SEARCH(a, 'x', ANALYZER => 'PATTERN_ANALYZER') FROM ts");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'PATTERN_ANALYZER' does not exist or not authorized.",
            "SELECT SEARCH('x y', 'x', ANALYZER => 'PATTERN_ANALYZER')");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'PATTERN_ANALYZER' does not exist or not authorized.",
            "SELECT SEARCH(a, 'x', ANALYZER => 'PATTERN_ANALYZER') FROM ts WHERE FALSE");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'NOSUCH_ANALYZER' does not exist or not authorized.",
            "SELECT COUNT(*) FROM te WHERE SEARCH(a, 'x', ANALYZER => 'NOSUCH_ANALYZER')");
        assertAnswer(
            "REFUSED SQL compilation error: | Object '' does not exist or not authorized.",
            "SELECT SEARCH(a, 'x', ANALYZER => '') FROM te");
        assertAnswer(
            "",
            "SELECT SEARCH(a, 'x', ANALYZER => NULL) FROM te");
        assertAnswer(
            "true ;; false ;; true ;; null",
            "SELECT SEARCH(a, 'x', ANALYZER => NULL) FROM ts ORDER BY a");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function SEARCH needs to be constant, found 'TS.A'",
            "SELECT SEARCH(a, 'x', ANALYZER => a) FROM ts");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function SEARCH needs to be constant, found "
                + "''DEFAULT' || '_ANALYZER''",
            "SELECT SEARCH(a, 'x', ANALYZER => 'DEFAULT' || '_ANALYZER') FROM ts ORDER BY a");
        assertAnswer(
            "REFUSED SQL compilation error: | argument needs to be a string: '2'",
            "SELECT SEARCH(a, 'x', ANALYZER => 1) FROM te");
        assertAnswer(
            "X-Y z.w|false ;; hello world|false ;; x y|false ;; null|null",
            "SELECT a, SEARCH(a, 'x', ANALYZER => 'NO_OP_ANALYZER') FROM ts ORDER BY a");
        assertAnswer(
            "X-Y z.w|false ;; hello world|false ;; x y|false ;; null|null",
            "SELECT a, SEARCH(a, 'x', ANALYZER => 'no_op_analyzer') FROM ts ORDER BY a");
        assertAnswer(
            "X-Y z.w|true ;; hello world|false ;; x y|true ;; null|null",
            "SELECT a, SEARCH(a, 'x', ANALYZER => 'DEFAULT_ANALYZER') FROM ts ORDER BY a");
        assertAnswer(
            "X-Y z.w|true ;; hello world|false ;; x y|true ;; null|null",
            "SELECT a, SEARCH(a, 'x', ANALYZER => 'default_analyzer') FROM ts ORDER BY a");
        assertAnswer(
            "X-Y z.w|true ;; hello world|false ;; x y|true ;; null|null",
            "SELECT a, SEARCH(a, 'x', ANALYZER => 'UNICODE_ANALYZER') FROM ts ORDER BY a");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'Analyzer type 'ENTITY_ANALYZER'' does not exist or not "
                + "authorized.",
            "SELECT a, SEARCH(a, 'x', ANALYZER => 'ENTITY_ANALYZER') FROM ts ORDER BY a");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'NGRAM_ANALYZER' does not exist or not authorized.",
            "SELECT a, SEARCH(a, 'x', ANALYZER => 'NGRAM_ANALYZER') FROM ts ORDER BY a");
        assertAnswer(
            "true|false|false|true|false|false|false|false|false|false|false|false|false|false|true|false",
            "SELECT SEARCH('x y', 'x y', ANALYZER => 'NO_OP_ANALYZER'), SEARCH('x y', 'X Y', ANALYZER => "
                + "'NO_OP_ANALYZER'), SEARCH('x y', 'x', ANALYZER => 'NO_OP_ANALYZER'), SEARCH('x', 'x', ANALYZER => "
                + "'NO_OP_ANALYZER'), SEARCH('X', 'x', ANALYZER => 'NO_OP_ANALYZER'), SEARCH('x y', 'x  y', ANALYZER "
                + "=> 'NO_OP_ANALYZER'), SEARCH('x y', 'y x', ANALYZER => 'NO_OP_ANALYZER'), SEARCH('X-Y z.w', 'x', "
                + "ANALYZER => 'NO_OP_ANALYZER'), SEARCH('X-Y z.w', 'y', ANALYZER => 'NO_OP_ANALYZER'), SEARCH('X-Y "
                + "z.w', 'z.w', ANALYZER => 'NO_OP_ANALYZER'), SEARCH('X-Y z.w', 'z', ANALYZER => 'NO_OP_ANALYZER'), "
                + "SEARCH('X-Y z.w', 'w', ANALYZER => 'NO_OP_ANALYZER'), SEARCH('X-Y z.w', 'x-y', ANALYZER => "
                + "'NO_OP_ANALYZER'), SEARCH('a_b', 'a', ANALYZER => 'NO_OP_ANALYZER'), SEARCH('a_b', 'a_b', "
                + "ANALYZER => 'NO_OP_ANALYZER'), SEARCH('don''t', 'don', ANALYZER => 'NO_OP_ANALYZER')");
        assertAnswer(
            "false|false|false|true|false|true|false|true|false|false|false|false|false|false|false",
            "SELECT SEARCH('1.5 kg', '1', ANALYZER => 'NO_OP_ANALYZER'), SEARCH('1.5 kg', '1.5', ANALYZER => "
                + "'NO_OP_ANALYZER'), SEARCH('www.x.com', 'x', ANALYZER => 'NO_OP_ANALYZER'), SEARCH('www.x.com', "
                + "'www.x.com', ANALYZER => 'NO_OP_ANALYZER'), SEARCH('user@example.com', 'example', ANALYZER => "
                + "'NO_OP_ANALYZER'), SEARCH('user@example.com', 'user@example.com', ANALYZER => 'NO_OP_ANALYZER'), "
                + "SEARCH('192.168.1.1', '192', ANALYZER => 'NO_OP_ANALYZER'), SEARCH('192.168.1.1', '192.168.1.1', "
                + "ANALYZER => 'NO_OP_ANALYZER'), SEARCH('http://x.com/a?b=1', 'x', ANALYZER => 'NO_OP_ANALYZER'), "
                + "SEARCH('x y', '', ANALYZER => 'NO_OP_ANALYZER'), SEARCH('', 'x', ANALYZER => 'NO_OP_ANALYZER'), "
                + "SEARCH('x y', ' ', ANALYZER => 'NO_OP_ANALYZER'), SEARCH('x, y', 'x,', ANALYZER => "
                + "'NO_OP_ANALYZER'), SEARCH('x y', 'x z', ANALYZER => 'NO_OP_ANALYZER'), SEARCH('\u00dcber', "
                + "'\u00fcber', ANALYZER => 'NO_OP_ANALYZER')");
        assertAnswer(
            "true|true|true|true|true|true|true|true|true|true|false|false|true|false|true|false",
            "SELECT SEARCH('x y', 'x y', ANALYZER => 'UNICODE_ANALYZER'), SEARCH('x y', 'X Y', ANALYZER => "
                + "'UNICODE_ANALYZER'), SEARCH('x y', 'x', ANALYZER => 'UNICODE_ANALYZER'), SEARCH('x', 'x', "
                + "ANALYZER => 'UNICODE_ANALYZER'), SEARCH('X', 'x', ANALYZER => 'UNICODE_ANALYZER'), SEARCH('x y', "
                + "'x  y', ANALYZER => 'UNICODE_ANALYZER'), SEARCH('x y', 'y x', ANALYZER => 'UNICODE_ANALYZER'), "
                + "SEARCH('X-Y z.w', 'x', ANALYZER => 'UNICODE_ANALYZER'), SEARCH('X-Y z.w', 'y', ANALYZER => "
                + "'UNICODE_ANALYZER'), SEARCH('X-Y z.w', 'z.w', ANALYZER => 'UNICODE_ANALYZER'), SEARCH('X-Y z.w', "
                + "'z', ANALYZER => 'UNICODE_ANALYZER'), SEARCH('X-Y z.w', 'w', ANALYZER => 'UNICODE_ANALYZER'), "
                + "SEARCH('X-Y z.w', 'x-y', ANALYZER => 'UNICODE_ANALYZER'), SEARCH('a_b', 'a', ANALYZER => "
                + "'UNICODE_ANALYZER'), SEARCH('a_b', 'a_b', ANALYZER => 'UNICODE_ANALYZER'), SEARCH('don''t', 'don',"
                + " ANALYZER => 'UNICODE_ANALYZER')");
        assertAnswer(
            "false|true|false|true|false|true|false|true|false|false|false|false|true|true|true",
            "SELECT SEARCH('1.5 kg', '1', ANALYZER => 'UNICODE_ANALYZER'), SEARCH('1.5 kg', '1.5', ANALYZER => "
                + "'UNICODE_ANALYZER'), SEARCH('www.x.com', 'x', ANALYZER => 'UNICODE_ANALYZER'), SEARCH('www.x.com',"
                + " 'www.x.com', ANALYZER => 'UNICODE_ANALYZER'), SEARCH('user@example.com', 'example', ANALYZER => "
                + "'UNICODE_ANALYZER'), SEARCH('user@example.com', 'user@example.com', ANALYZER => "
                + "'UNICODE_ANALYZER'), SEARCH('192.168.1.1', '192', ANALYZER => 'UNICODE_ANALYZER'), "
                + "SEARCH('192.168.1.1', '192.168.1.1', ANALYZER => 'UNICODE_ANALYZER'), SEARCH('http://x.com/a?b=1',"
                + " 'x', ANALYZER => 'UNICODE_ANALYZER'), SEARCH('x y', '', ANALYZER => 'UNICODE_ANALYZER'), "
                + "SEARCH('', 'x', ANALYZER => 'UNICODE_ANALYZER'), SEARCH('x y', ' ', ANALYZER => "
                + "'UNICODE_ANALYZER'), SEARCH('x, y', 'x,', ANALYZER => 'UNICODE_ANALYZER'), SEARCH('x y', 'x z', "
                + "ANALYZER => 'UNICODE_ANALYZER'), SEARCH('\u00dcber', '\u00fcber', ANALYZER => 'UNICODE_ANALYZER')");
        assertAnswer(
            "true|true|true|true|true|true|true|true|true|true|true|true|true|true|true|true",
            "SELECT SEARCH('x y', 'x y', ANALYZER => 'DEFAULT_ANALYZER'), SEARCH('x y', 'X Y', ANALYZER => "
                + "'DEFAULT_ANALYZER'), SEARCH('x y', 'x', ANALYZER => 'DEFAULT_ANALYZER'), SEARCH('x', 'x', "
                + "ANALYZER => 'DEFAULT_ANALYZER'), SEARCH('X', 'x', ANALYZER => 'DEFAULT_ANALYZER'), SEARCH('x y', "
                + "'x  y', ANALYZER => 'DEFAULT_ANALYZER'), SEARCH('x y', 'y x', ANALYZER => 'DEFAULT_ANALYZER'), "
                + "SEARCH('X-Y z.w', 'x', ANALYZER => 'DEFAULT_ANALYZER'), SEARCH('X-Y z.w', 'y', ANALYZER => "
                + "'DEFAULT_ANALYZER'), SEARCH('X-Y z.w', 'z.w', ANALYZER => 'DEFAULT_ANALYZER'), SEARCH('X-Y z.w', "
                + "'z', ANALYZER => 'DEFAULT_ANALYZER'), SEARCH('X-Y z.w', 'w', ANALYZER => 'DEFAULT_ANALYZER'), "
                + "SEARCH('X-Y z.w', 'x-y', ANALYZER => 'DEFAULT_ANALYZER'), SEARCH('a_b', 'a', ANALYZER => "
                + "'DEFAULT_ANALYZER'), SEARCH('a_b', 'a_b', ANALYZER => 'DEFAULT_ANALYZER'), SEARCH('don''t', 'don',"
                + " ANALYZER => 'DEFAULT_ANALYZER')");
        assertAnswer(
            "true|true|true|true|true|true|true|true|true|false|false|false|true|true|true",
            "SELECT SEARCH('1.5 kg', '1', ANALYZER => 'DEFAULT_ANALYZER'), SEARCH('1.5 kg', '1.5', ANALYZER => "
                + "'DEFAULT_ANALYZER'), SEARCH('www.x.com', 'x', ANALYZER => 'DEFAULT_ANALYZER'), SEARCH('www.x.com',"
                + " 'www.x.com', ANALYZER => 'DEFAULT_ANALYZER'), SEARCH('user@example.com', 'example', ANALYZER => "
                + "'DEFAULT_ANALYZER'), SEARCH('user@example.com', 'user@example.com', ANALYZER => "
                + "'DEFAULT_ANALYZER'), SEARCH('192.168.1.1', '192', ANALYZER => 'DEFAULT_ANALYZER'), "
                + "SEARCH('192.168.1.1', '192.168.1.1', ANALYZER => 'DEFAULT_ANALYZER'), SEARCH('http://x.com/a?b=1',"
                + " 'x', ANALYZER => 'DEFAULT_ANALYZER'), SEARCH('x y', '', ANALYZER => 'DEFAULT_ANALYZER'), "
                + "SEARCH('', 'x', ANALYZER => 'DEFAULT_ANALYZER'), SEARCH('x y', ' ', ANALYZER => "
                + "'DEFAULT_ANALYZER'), SEARCH('x, y', 'x,', ANALYZER => 'DEFAULT_ANALYZER'), SEARCH('x y', 'x z', "
                + "ANALYZER => 'DEFAULT_ANALYZER'), SEARCH('\u00dcber', '\u00fcber', ANALYZER => 'DEFAULT_ANALYZER')");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'Analyzer type 'ENTITY_ANALYZER'' does not exist or not "
                + "authorized.",
            "SELECT SEARCH('x y', 'x y', ANALYZER => 'ENTITY_ANALYZER'), SEARCH('x y', 'X Y', ANALYZER => "
                + "'ENTITY_ANALYZER'), SEARCH('x y', 'x', ANALYZER => 'ENTITY_ANALYZER'), SEARCH('x', 'x', ANALYZER "
                + "=> 'ENTITY_ANALYZER'), SEARCH('X', 'x', ANALYZER => 'ENTITY_ANALYZER'), SEARCH('x y', 'x  y', "
                + "ANALYZER => 'ENTITY_ANALYZER'), SEARCH('x y', 'y x', ANALYZER => 'ENTITY_ANALYZER'), SEARCH('X-Y "
                + "z.w', 'x', ANALYZER => 'ENTITY_ANALYZER'), SEARCH('X-Y z.w', 'y', ANALYZER => 'ENTITY_ANALYZER'), "
                + "SEARCH('X-Y z.w', 'z.w', ANALYZER => 'ENTITY_ANALYZER'), SEARCH('X-Y z.w', 'z', ANALYZER => "
                + "'ENTITY_ANALYZER'), SEARCH('X-Y z.w', 'w', ANALYZER => 'ENTITY_ANALYZER'), SEARCH('X-Y z.w', "
                + "'x-y', ANALYZER => 'ENTITY_ANALYZER'), SEARCH('a_b', 'a', ANALYZER => 'ENTITY_ANALYZER'), "
                + "SEARCH('a_b', 'a_b', ANALYZER => 'ENTITY_ANALYZER'), SEARCH('don''t', 'don', ANALYZER => "
                + "'ENTITY_ANALYZER')");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'Analyzer type 'ENTITY_ANALYZER'' does not exist or not "
                + "authorized.",
            "SELECT SEARCH('1.5 kg', '1', ANALYZER => 'ENTITY_ANALYZER'), SEARCH('1.5 kg', '1.5', ANALYZER => "
                + "'ENTITY_ANALYZER'), SEARCH('www.x.com', 'x', ANALYZER => 'ENTITY_ANALYZER'), SEARCH('www.x.com', "
                + "'www.x.com', ANALYZER => 'ENTITY_ANALYZER'), SEARCH('user@example.com', 'example', ANALYZER => "
                + "'ENTITY_ANALYZER'), SEARCH('user@example.com', 'user@example.com', ANALYZER => 'ENTITY_ANALYZER'),"
                + " SEARCH('192.168.1.1', '192', ANALYZER => 'ENTITY_ANALYZER'), SEARCH('192.168.1.1', '192.168.1.1',"
                + " ANALYZER => 'ENTITY_ANALYZER'), SEARCH('http://x.com/a?b=1', 'x', ANALYZER => 'ENTITY_ANALYZER'),"
                + " SEARCH('x y', '', ANALYZER => 'ENTITY_ANALYZER'), SEARCH('', 'x', ANALYZER => 'ENTITY_ANALYZER'),"
                + " SEARCH('x y', ' ', ANALYZER => 'ENTITY_ANALYZER'), SEARCH('x, y', 'x,', ANALYZER => "
                + "'ENTITY_ANALYZER'), SEARCH('x y', 'x z', ANALYZER => 'ENTITY_ANALYZER'), SEARCH('\u00dcber', "
                + "'\u00fcber', ANALYZER => 'ENTITY_ANALYZER')");
        assertAnswer(
            "false",
            "SELECT SEARCH('x y', 'x z', ANALYZER => 'NO_OP_ANALYZER', SEARCH_MODE => 'OR')");
        assertAnswer(
            "false",
            "SELECT SEARCH('x y', 'x z', ANALYZER => 'NO_OP_ANALYZER', SEARCH_MODE => 'AND')");
        assertAnswer(
            "false",
            "SELECT SEARCH('x y', 'x z', ANALYZER => 'UNICODE_ANALYZER', SEARCH_MODE => 'AND')");
        assertAnswer(
            "true",
            "SELECT SEARCH('x y', 'x z', ANALYZER => 'UNICODE_ANALYZER', SEARCH_MODE => 'OR')");
        assertAnswer(
            "false",
            "SELECT SEARCH('x y', 'y x', ANALYZER => 'DEFAULT_ANALYZER', SEARCH_MODE => 'PHRASE')");
        assertAnswer(
            "true",
            "SELECT SEARCH('x y', 'x y', ANALYZER => 'NO_OP_ANALYZER', SEARCH_MODE => 'PHRASE')");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'Search mode 'NOSUCH'' does not exist or not authorized.",
            "SELECT SEARCH('x y', 'x', SEARCH_MODE => 'NOSUCH')");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'Search mode 'NOSUCH'' does not exist or not authorized.",
            "SELECT SEARCH(a, 'x', SEARCH_MODE => 'NOSUCH') FROM te");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [NOSUCH] do not match "
                + "any signature for function SEARCH",
            "SELECT SEARCH(a, 'x', NOSUCH => 'y') FROM te");
        assertAnswer(
            "false ;; false ;; false ;; null",
            "SELECT SEARCH((a), 'x', ANALYZER => 'NO_OP_ANALYZER') FROM ts ORDER BY a");
        assertAnswer(
            "false",
            "SELECT SEARCH(PARSE_JSON('{\"k\": \"x y\"}'), 'x', ANALYZER => 'NO_OP_ANALYZER')");
        assertAnswer(
            "false",
            "SELECT SEARCH(PARSE_JSON('{\"k\": \"x y\"}'), 'x y', ANALYZER => 'NO_OP_ANALYZER')");
        assertAnswer(
            "false",
            "SELECT SEARCH(ARRAY_CONSTRUCT('x y', 'z'), 'z', ANALYZER => 'NO_OP_ANALYZER')");
    }
}
