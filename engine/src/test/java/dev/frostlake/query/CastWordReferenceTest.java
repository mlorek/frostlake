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
 * CAST and TRY_CAST read as nothing but a cast where a value is read: with no parenthesis after the word the
 * token after it is refused, the end of input included, and the word is no qualifier — in a select item the
 * dot and the name after it are read past and the token after them is refused, anywhere else the dot is. The
 * words stay aliases, a star's qualifier, and names when quoted. Every answer is live-verified.
 */
public class CastWordReferenceTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute(
            "CREATE OR REPLACE TABLE FY (id INT)");
    }

    @Test
    public void theWordWhereAValueIsRead() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'FROM'.",
            "SELECT cast.a FROM t cast");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'FROM'.",
            "SELECT cast.a FROM t AS cast");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'FROM'.",
            "SELECT CAST FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '<EOF>'.",
            "SELECT cast");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'FROM'.",
            "SELECT TRY_CAST FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '<EOF>'.",
            "SELECT try_cast");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'FROM'.",
            "SELECT try_cast.a FROM t try_cast");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'FROM'.",
            "SELECT try_cast.a FROM t AS try_cast");
        assertAnswer(
            "",
            "SELECT a FROM t AS cast");
        assertAnswer(
            "",
            "SELECT a FROM t cast");
        assertAnswer(
            "",
            "SELECT a AS cast FROM t");
        assertAnswer(
            "",
            "SELECT a try_cast FROM t");
        assertAnswer(
            "",
            "SELECT \"CAST\".a FROM t AS \"CAST\"");
        assertAnswer(
            "",
            "SELECT a cast FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'FROM'.",
            "SELECT 1 + cast FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '+'.",
            "SELECT cast + 1 FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '='.",
            "SELECT a FROM t WHERE cast = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '.'.",
            "SELECT a FROM t WHERE cast.a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '.'.",
            "SELECT a FROM t cast WHERE cast.a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ')'.",
            "SELECT UPPER(cast) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '<EOF>'.",
            "SELECT a FROM t ORDER BY cast");
        assertAnswer(
            "",
            "SELECT cast.* FROM t cast");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'T.CAST'",
            "SELECT t.cast FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected ','.",
            "SELECT cast, a FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'FROM'.",
            "SELECT a, cast FROM t");
    }
}
