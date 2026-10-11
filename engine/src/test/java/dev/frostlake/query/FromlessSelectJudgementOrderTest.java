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
 * A select with no FROM is judged in the order a select over a relation is: the names of every item, of the
 * WHERE and of the ORDER BY first, then each subquery whole in the order written, the ORDER BY's included, and
 * only then the select's own argument types, arities and function names. Every answer is live-verified.
 */
public class FromlessSelectJudgementOrderTest extends MeasuredCellsTestSupport {

    @Test
    public void namesThenSubqueriesThenTypes() {
        engine.execute(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 28 | invalid identifier 'NOSUCH'",
            "SELECT (SELECT 'x' + TRUE), nosuch");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | invalid identifier 'NOSUCH'",
            "SELECT 'o' + TRUE, nosuch, (SELECT 'x' + TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 40 | invalid identifier 'NOSUCH'",
            "SELECT 'o' + TRUE, (SELECT 'x' + TRUE), nosuch");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 33 | invalid identifier 'NOSUCH'",
            "SELECT (SELECT 'x' + TRUE) WHERE nosuch = 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | Invalid argument types for function "
                + "'+': (VARCHAR(1), BOOLEAN)",
            "SELECT (SELECT 'x' + TRUE), (SELECT nosuch)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 44 | Invalid argument types for function "
                + "'+': (VARCHAR(1), BOOLEAN)",
            "SELECT 'o' + TRUE AS a ORDER BY (SELECT 'x' + TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 28 | invalid identifier 'NOSUCH'",
            "SELECT (SELECT 'x' + TRUE), nosuch FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 40 | invalid identifier 'NOSUCH'",
            "SELECT (SELECT 'x' + TRUE) FROM g WHERE nosuch = 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | Invalid argument types for function "
                + "'+': (VARCHAR(1), BOOLEAN)",
            "SELECT (SELECT 'x' + TRUE), (SELECT nosuch) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 51 | Invalid argument types for function "
                + "'+': (VARCHAR(1), BOOLEAN)",
            "SELECT 'o' + TRUE AS a FROM g ORDER BY (SELECT 'x' + TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 15 | invalid identifier 'NOSUCH'",
            "SELECT (SELECT nosuch), (SELECT 'x' + TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | invalid identifier 'NOSUCH'",
            "SELECT 'o' + TRUE, nosuch");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'NOSUCH'",
            "SELECT nosuch, 'o' + TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | invalid identifier 'NOSUCH'",
            "SELECT 'o' + TRUE WHERE nosuch = 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'NOSUCH'",
            "SELECT 'o' + TRUE ORDER BY nosuch");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 23 | invalid identifier 'NOSUCH'",
            "SELECT 1 AS a ORDER BY nosuch, (SELECT 'x' + TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 36 | invalid identifier 'NOSUCH'",
            "SELECT (SELECT 'x' + TRUE) ORDER BY nosuch");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 43 | invalid identifier 'NOSUCH'",
            "SELECT 1 WHERE (SELECT 'x' + TRUE) = 1 AND nosuch = 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 47 | invalid identifier 'NOSUCH'",
            "SELECT 1 WHERE 'o' + TRUE = 1 ORDER BY (SELECT nosuch)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | Invalid argument types for function "
                + "'+': (VARCHAR(1), BOOLEAN)",
            "SELECT (SELECT 'x' + TRUE), (SELECT 'y' + TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 35 | invalid identifier 'NOSUCH'",
            "SELECT 'o' + TRUE, (SELECT 1 WHERE nosuch = 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 37 | invalid identifier 'NOSUCH'",
            "SELECT (SELECT (SELECT 'x' + TRUE)), nosuch");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'NOSUCH'",
            "SELECT nosuch_fn(1), nosuch");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'NOSUCH'",
            "SELECT nosuch, nosuch_fn(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | invalid identifier 'NOSUCH'",
            "SELECT UPPER(1, 2), nosuch");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 29 | invalid identifier 'NOSUCH'",
            "SELECT (SELECT UPPER(1, 2)), nosuch");
    }
}
