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
package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A DROP's and a DESCRIBE's signature and properties, the readings that were left: the statement ends at its
 * first ';', one written inside brackets too, and nothing after it is read; a ':' after a word value is named
 * twice; a value this grammar cannot read but the account can — a stage reference of its own spelling, a
 * backticked name — leaves the property to be judged as any other (live-verified).
 */
public class SignatureSemicolonAndColonLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void aSemicolonInsideBrackets() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s'.",
            "DESCRIBE TABLE t1 (@s;x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '('. | syntax error "
                + "line 1 at position 21 unexpected ')'.",
            "DESCRIBE TABLE t1 ((a);x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ';'.",
            "DESCRIBE TABLE t1 (a;x) y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ';'.",
            "DESCRIBE TABLE t1 (a;x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ';'.",
            "DESCRIBE TABLE t1 (a;)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ';'.",
            "DESCRIBE TABLE t1 (;)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ';'.",
            "DESCRIBE TABLE t1 (a, b;c)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s'.",
            "DESCRIBE TABLE t1 (@s;x) y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s'.",
            "DESCRIBE TABLE t1 (@s)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ';'.",
            "DESCRIBE TABLE t1 (a) x = (1;2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '@s'.",
            "DESCRIBE TABLE t1 (a) x = (@s;1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '@s'.",
            "DROP TABLE t1 (@s;x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '('.",
            "DROP TABLE t1 ((a);x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ';'.",
            "DROP TABLE t1 (a;x) y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ';'.",
            "DROP TABLE t1 (a;x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ';'.",
            "DROP TABLE t1 (a;)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ';'.",
            "DESCRIBE TABLE t1 (a;x); SELECT 1 x y");
    }

    @Test
    public void valuesTheAccountReadsWhole() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = @s!=x");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = @S3://b/k");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = @s!=x, y = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = `b`");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter '`b`'",
            "DESCRIBE TABLE t1 (a) `b` = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = @s/p!=x");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = @~/p");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = @%t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = @s.t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = @s x");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = `b` y = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = @s!=x");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [`b`] for parameter 'TYPE'",
            "DESCRIBE TABLE t1 TYPE = `b`");
    }

    @Test
    public void aColonAfterAWordValue() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ':'. | syntax error "
                + "line 1 at position 28 unexpected ':'.",
            "DESCRIBE TABLE t1 (a) x = y :");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '@s'.",
            "DESCRIBE TABLE t1 x = 1, @s");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ':'. | syntax error "
                + "line 1 at position 28 unexpected ':'.",
            "DESCRIBE TABLE t1 (a) x = y : z");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '::'. | syntax error "
                + "line 1 at position 28 unexpected '::'. | syntax error line 1 at position 32 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = y :: z");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = :y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ':'. | syntax error "
                + "line 1 at position 27 unexpected ':'.",
            "DESCRIBE TABLE t1 (a) x = y:z");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected ':'. | syntax error "
                + "line 1 at position 24 unexpected ':'.",
            "DESCRIBE TABLE t1 x = y :");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ':'. | syntax error "
                + "line 1 at position 28 unexpected ':'. | syntax error line 1 at position 42 unexpected 'y'.",
            "DESCRIBE TABLE t1 (a) x = y :; SELECT 1 x y");
    }
}
