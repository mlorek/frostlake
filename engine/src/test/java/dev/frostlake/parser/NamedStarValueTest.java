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
 * A star, a DISTINCT or an ALL written as a named argument's value. A name takes a value and none of the three
 * is one, so the token is named, and what follows decides what else is: the parenthesis right after a star
 * closes the call, and so does whatever a DISTINCT is followed by up to it - {@code COUNT(x => *) OVER ()} is the
 * star alone. Anything else gives the call up: a comma right after the star is named with it, and the list
 * around the call is read on - in a select list an arrow outside parentheses is named and its item given up, a
 * parenthesis closing nothing is named and ends the reading, so {@code COUNT(x => *, y => 1, 2)} is the star,
 * the comma, the arrow and the parenthesis; among another call's arguments that call's own parenthesis is the
 * one named; in a WHERE nothing after the comma is. Every answer is live-verified.
 */
public class NamedStarValueTest extends MeasuredCellsTestSupport {

    @Test
    public void aStarGivenAName() {
        answer(
            "CREATE OR REPLACE TABLE rt (n INT)");
        answer(
            "INSERT INTO rt VALUES (1), (2)");
        answer(
            "CREATE OR REPLACE TABLE rt2 (n INT)");
        answer(
            "INSERT INTO rt2 VALUES (1), (NULL), (3)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'.",
            "SELECT COUNT(x => *) OVER () FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'.",
            "SELECT COUNT(x => *) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | function COUNT does not support named "
                + "arguments",
            "SELECT COUNT(x => n) OVER () FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'.",
            "SELECT COUNT(x => *)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '*'.",
            "SELECT SUM(x => *) OVER () FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 23 unexpected '=>'.",
            "SELECT COUNT(x => *, y => 1) OVER () FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'DISTINCT'.",
            "SELECT COUNT(x => DISTINCT n) OVER () FROM rt");
        assertAnswer(
            "2 ;; 2",
            "SELECT COUNT(*) OVER () FROM rt");
    }

    @Test
    public void whatFollowsTheStar() {
        answer(
            "CREATE OR REPLACE TABLE rt (n INT)");
        answer(
            "INSERT INTO rt VALUES (1), (2)");
        answer(
            "CREATE OR REPLACE TABLE rt2 (n INT)");
        answer(
            "INSERT INTO rt2 VALUES (1), (NULL), (3)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 22 unexpected ')'.",
            "SELECT COUNT(x => *, 1) OVER () FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'.",
            "SELECT COUNT(x => *) OVER (PARTITION BY n) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'.",
            "SELECT COUNT(x => *) + 1 FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'.",
            "SELECT COUNT(x => *) OVER () AS c, 2 FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '*'.",
            "SELECT ABS(x => *) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'.",
            "SELECT COUNT(x => *) OVER (ORDER BY n ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 23 unexpected '=>'. | "
                + "syntax error line 1 at position 31 unexpected '=>'.",
            "SELECT COUNT(x => *, y => 1, z => 2) OVER () FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 23 unexpected '=>'.",
            "SELECT COUNT(x => *, y => 1) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'DISTINCT'.",
            "SELECT COUNT(x => DISTINCT n) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'ALL'.",
            "SELECT COUNT(x => ALL n) OVER () FROM rt");
    }

    @Test
    public void theListReadOn() {
        answer(
            "CREATE OR REPLACE TABLE rt (n INT)");
        answer(
            "INSERT INTO rt VALUES (1), (2)");
        answer(
            "CREATE OR REPLACE TABLE rt2 (n INT)");
        answer(
            "INSERT INTO rt2 VALUES (1), (NULL), (3)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 25 unexpected ')'.",
            "SELECT COUNT(x => *, 1, 2) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 22 unexpected ')'.",
            "SELECT COUNT(x => *, y) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 23 unexpected '=>'. | "
                + "syntax error line 1 at position 30 unexpected ')'.",
            "SELECT COUNT(x => *, y => 1, 2) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 26 unexpected '=>'.",
            "SELECT COUNT(x => *, 1, y => 2) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 21 unexpected ')'.",
            "SELECT COUNT(x => * 2) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 21 unexpected ')'.",
            "SELECT COUNT(x => *, ) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 20 unexpected ')'.",
            "SELECT COUNT(x => *,) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'DISTINCT'. | syntax "
                + "error line 1 at position 32 unexpected '=>'.",
            "SELECT COUNT(x => DISTINCT n, y => 1) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'DISTINCT'. | syntax "
                + "error line 1 at position 31 unexpected ')'.",
            "SELECT COUNT(x => DISTINCT n, 1) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'ALL'. | syntax error "
                + "line 1 at position 27 unexpected '=>'.",
            "SELECT COUNT(x => ALL n, y => 1) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 23 unexpected '=>'.",
            "SELECT COUNT(x => *, y => 1) + 1 FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '*'. | syntax error "
                + "line 1 at position 22 unexpected ','. | syntax error line 1 at position 26 unexpected '=>'.",
            "SELECT 1, COUNT(x => *, y => 1) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 23 unexpected '=>'.",
            "SELECT COUNT(x => *, y => 1), 2 FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 23 unexpected '=>'. | "
                + "syntax error line 1 at position 32 unexpected '=>'.",
            "SELECT COUNT(x => *, y => 1), z => 2 FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 22 unexpected ')'.",
            "SELECT COUNT(x => *, 1), 2 FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 32 unexpected ')'.",
            "SELECT COUNT(x => *, ABS(y => 1)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 27 unexpected ')'.",
            "SELECT COUNT(x => *, ABS(1)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 24 unexpected ')'.",
            "SELECT COUNT(x => *, (1)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '*'.",
            "SELECT COUNT(1, x => *) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '*'.",
            "SELECT COUNT(x => 1, y => *) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '*'. | syntax error "
                + "line 1 at position 27 unexpected ','. | syntax error line 1 at position 31 unexpected '=>'.",
            "SELECT COUNT(x => 1, y => *, z => 2) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 23 unexpected '=>'.",
            "SELECT COUNT(x => *, y => *) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 22 unexpected ')'.",
            "SELECT COUNT(x => *, *) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 23 unexpected '=>'.",
            "SELECT COUNT(x => *, y => 1) FROM rt WHERE n > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 22 unexpected ')'.",
            "SELECT COUNT(x => *, 1) FROM rt WHERE n > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','.",
            "SELECT COUNT(x => *, 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected '<EOF>'.",
            "SELECT COUNT(x => *");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 23 unexpected '=>'.",
            "SELECT COUNT(x => *, y => 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 22 unexpected ')'.",
            "SELECT COUNT(x => *, 1); SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '*'. | syntax error "
                + "line 1 at position 23 unexpected ','. | syntax error line 1 at position 27 unexpected ')'.",
            "SELECT ABS(COUNT(x => *, 1)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '*'. | syntax error "
                + "line 1 at position 23 unexpected ','. | syntax error line 1 at position 32 unexpected ')'.",
            "SELECT ABS(COUNT(x => *, y => 1)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '*'.",
            "SELECT ABS(COUNT(x => *)) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '*'. | syntax error "
                + "line 1 at position 35 unexpected ','.",
            "SELECT 1 FROM rt WHERE COUNT(x => *, 1) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '*'.",
            "SELECT 1 FROM rt WHERE COUNT(x => *) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 26 unexpected ')'.",
            "SELECT COUNT(x => *, 1 + 2) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 23 unexpected '=>'. | "
                + "syntax error line 1 at position 34 unexpected ')'.",
            "SELECT COUNT(x => *, y => 1 + 2, 3) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 25 unexpected ')'.",
            "SELECT COUNT(x => *, n, n) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '*'. | syntax error "
                + "line 1 at position 19 unexpected ','. | syntax error line 1 at position 23 unexpected '=>'. | "
                + "syntax error line 1 at position 30 unexpected ')'.",
            "SELECT COUNT(x => *, y => 1, z) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'DISTINCT'.",
            "SELECT COUNT(x => DISTINCT) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'DISTINCT'.",
            "SELECT COUNT(x => DISTINCT *) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | function COUNT does not support named "
                + "arguments",
            "SELECT COUNT(DISTINCT x => n) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | function COUNT does not support named "
                + "arguments",
            "SELECT COUNT(ALL x => n) FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'DISTINCT'.",
            "SELECT COUNT(x => n, DISTINCT n) FROM rt");
    }
}
