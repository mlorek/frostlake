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
 * A character no token opens with, written outside quotes: the account refuses it as a token of two characters
 * - the character and the one after it, so both halves of one outside the Basic Multilingual Plane - and
 * reports nothing else; the recovery readings run on a text that holds such a character in a string
 * (live-verified).
 */
public class UnknownCharacterLineTest extends MeasuredCellsTestSupport {

    @Test
    public void aStrayCharacterIsOneToken() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        answer(
            "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '\ud83d\ude00'.",
            "SELECT \ud83d\ude00");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '\ud83d\ude00'.",
            "SELECT 1 \ud83d\ude00 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '\ud83d\ude00'.",
            "SELECT 1 \ud83d\ude00");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'.",
            "SELECT '\ud83d\ude00', YEAR('\ud83d\ude00' x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '\u00a7'.",
            "SELECT \u00a7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '\u00a7 '.",
            "SELECT 1 \u00a7 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '\u00a7'.",
            "SELECT 1 \u00a7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '\u00a7 '.",
            "SELECT \u00a7 FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '\u00a7 '.",
            "SELECT a \u00a7 FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '\ud83d\ude00'.",
            "SELECT \ud83d\ude00 FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '\ud83d\ude00'.",
            "SELECT a \ud83d\ude00 FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '\ud83d\ude00'.",
            "SELECT \ud83d\ude00\ud83d\ude00");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '\ud83d\ude00'.",
            "SELECT \ud83d\ude00 \ud83d\ude00");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '\ud83d\ude00'.",
            "SELECT a FROM t WHERE a = \ud83d\ude00");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '\ud83d\ude00'.",
            "SELECT a FROM t WHERE a = 1 \ud83d\ude00 x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '\u20ac'.",
            "SELECT \u20ac");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '\u00e9'.",
            "SELECT 1 \u00e9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '\u00e9'.",
            "SELECT \u00e9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '#'.",
            "SELECT #");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '# '.",
            "SELECT 1 # 2");
    }

    @Test
    public void theRecoveryReadingsBesideOne() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        answer(
            "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'y'.",
            "SELECT '\ud83d\ude00' x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'x'. | syntax error "
                + "line 1 at position 18 unexpected 'y'.",
            "SELECT '\ud83d\ude00', (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '('. | syntax error "
                + "line 1 at position 32 unexpected ')'.",
            "SELECT '\ud83d\ude00' FROM t JOIN USING (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'y'.",
            "SELECT '\ud83d\ude00'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'x'.",
            "SELECT '\ud83d\ude00' FROM t WHERE a IN (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x'. | syntax error "
                + "line 1 at position 23 unexpected 'y'.",
            "SELECT '\ud83d\ude00', 1 IN (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'.",
            "SELECT '\ud83d\ude00', * EXCLUDE (a x y) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 23 unexpected 'x'.",
            "SELECT '\ud83d\ude00', (SELECT a x y FROM t)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax error "
                + "line 1 at position 30 unexpected 'y'.",
            "SELECT '\ud83d\ude00' FROM t, LATERAL x y");
    }
}
