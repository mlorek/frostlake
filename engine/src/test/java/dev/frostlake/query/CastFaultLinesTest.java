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
 * A fault inside a cast's parentheses that is no comma: the fault, and then whatever the statement holds wrong
 * once the cast is read past — a select item's own cast and a cast whose fault lies at or after its AS as a
 * whole, a nested cast with a token where its AS belongs by giving its parenthesis up. After TOP n and after an
 * earlier select item the fault is where the item stops being one, not the list's comma. Every answer is
 * live-verified.
 */
public class CastFaultLinesTest extends MeasuredCellsTestSupport {

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
    public void afterAnEarlierItemOrTop() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ','. | syntax error "
                + "line 1 at position 25 unexpected ')'.",
            "SELECT TOP 1 a, CAST(1, 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ','. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "SELECT TOP 1 a, TRY_CAST(1, 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ','. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "SELECT TOP 1 a, TRY_CAST(1, 2) AS INT) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'FROM'.",
            "SELECT TOP 1 a, CAST(b AS INT FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'FROM'.",
            "SELECT a, TRY_CAST(b AS INT FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'FROM'.",
            "SELECT a, b, TRY_CAST(b AS INT FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'FROM'.",
            "SELECT a, CAST(b AS INT FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'FROM'.",
            "SELECT TRY_CAST(b AS INT FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ','. | syntax error "
                + "line 1 at position 19 unexpected ')'.",
            "SELECT a, CAST(1, 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ','. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "SELECT a, TRY_CAST(1, 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ','. | syntax error "
                + "line 1 at position 26 unexpected ')'.",
            "SELECT TOP 1 TRY_CAST(1, 2) FROM t");
        assertAnswer(
            "",
            "SELECT TOP 1 a, b FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','. | syntax error "
                + "line 1 at position 32 unexpected ')'.",
            "SELECT TOP 1 a, UPPER(CAST(1, 2)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ','. | syntax error "
                + "line 1 at position 28 unexpected ')'.",
            "SELECT DISTINCT a, CAST(1, 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'y'.",
            "SELECT TOP 1 a, b x y FROM t");
    }

    @Test
    public void aFaultThatIsNoComma() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '2'.",
            "SELECT CAST(1 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '2'.",
            "SELECT CAST(1 AS 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'AS'.",
            "SELECT CAST(1 + AS INT) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ')'.",
            "SELECT CAST(b AS NUMBER(10,)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'AS'.",
            "SELECT CAST(AS INT) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '2'.",
            "SELECT TRY_CAST(1 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '2'.",
            "SELECT TRY_CAST(1 AS 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'AS'.",
            "SELECT TRY_CAST(1 + AS INT) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ')'.",
            "SELECT TRY_CAST(b AS NUMBER(10,)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'AS'.",
            "SELECT TRY_CAST(AS INT) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected ')'.",
            "SELECT CAST() FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ')'.",
            "SELECT CAST(1) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ')'.",
            "SELECT CAST(1 AS) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'INT'.",
            "SELECT CAST(1 AS INT INT) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ','. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "SELECT CAST(1 AS INT, 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '2'.",
            "SELECT a, CAST(1 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '2'.",
            "SELECT CAST(1 2), a FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '2'. | syntax error "
                + "line 1 at position 22 unexpected ')'.",
            "SELECT UPPER(CAST(1 2)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '2'. | syntax error "
                + "line 1 at position 35 unexpected '<EOF>'.",
            "SELECT 1 FROM t WHERE CAST(1 2) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '2'. | syntax error "
                + "line 1 at position 19 unexpected 'y'.",
            "SELECT CAST(1 2) x y FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'y'.",
            "SELECT CAST(1 AS INT) x y FROM t");
    }

    @Test
    public void theShapesMeasuredSecond() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '2'. | syntax error "
                + "line 1 at position 25 unexpected ')'.",
            "SELECT UPPER(CAST(1 2), 3) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '2'. | syntax error "
                + "line 1 at position 17 unexpected ')'.",
            "SELECT (CAST(1 2)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '2'. | syntax error "
                + "line 1 at position 21 unexpected ')'.",
            "SELECT (CAST(1 2) + 1) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '2'. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "SELECT UPPER(LOWER(CAST(1 2))) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '2'. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT a FROM t WHERE a IN (CAST(1 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '2'. | syntax error "
                + "line 1 at position 37 unexpected ','.",
            "SELECT a FROM t WHERE a IN (CAST(1 2), 3)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '2'. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT a FROM t WHERE UPPER(CAST(1 2)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '2'. | syntax error "
                + "line 1 at position 45 unexpected '<EOF>'.",
            "SELECT a FROM t WHERE CAST(1 2) = 1 AND a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '2'. | syntax error "
                + "line 1 at position 36 unexpected 'ORDER'.",
            "SELECT a FROM t WHERE CAST(1 2) = 1 ORDER BY a");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '2'.",
            "SELECT a FROM t WHERE a = CAST(1 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '2'.",
            "SELECT a FROM t ORDER BY CAST(1 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '2'.",
            "SELECT a FROM t ORDER BY CAST(1 2), a");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '2'.",
            "SELECT a FROM t GROUP BY CAST(1 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '2'. | syntax error "
                + "line 1 at position 36 unexpected '<EOF>'.",
            "SELECT a FROM t HAVING CAST(1 2) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '2'.",
            "SELECT CAST(1 AS 2), a FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '2'. | syntax error "
                + "line 1 at position 22 unexpected 'y'.",
            "SELECT CAST(1 AS 2) x y FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '2'.",
            "SELECT UPPER(CAST(1 AS 2)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '2'.",
            "SELECT a FROM t WHERE CAST(1 AS 2) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'AS'. | syntax error "
                + "line 1 at position 26 unexpected 'y'.",
            "SELECT CAST(1 + AS INT) x y FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'AS'.",
            "SELECT UPPER(CAST(1 + AS INT)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'AS'.",
            "SELECT a FROM t WHERE CAST(1 + AS INT) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'AS'.",
            "SELECT UPPER(CAST(AS INT)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'AS'.",
            "SELECT a FROM t WHERE CAST(AS INT) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'AS'. | syntax error "
                + "line 1 at position 22 unexpected 'y'.",
            "SELECT CAST(AS INT) x y FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '2'. | syntax error "
                + "line 1 at position 32 unexpected 'b'.",
            "SELECT CAST(1 2) FROM t WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '2'. | syntax error "
                + "line 1 at position 29 unexpected 'y'.",
            "SELECT CAST(1 2); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ','. | syntax error "
                + "line 1 at position 16 unexpected ')'.",
            "SELECT CAST(1, 2); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '2'. | syntax error "
                + "line 1 at position 25 unexpected '4'.",
            "SELECT CAST(1 2), CAST(3 4) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '2'.",
            "SELECT 1 + CAST(1 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '2'.",
            "SELECT CAST(1 2 3) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'FROM'.",
            "SELECT CAST(1 AS INT) AS FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ')'. | syntax error "
                + "line 1 at position 32 unexpected 'y'.",
            "SELECT CAST(b AS NUMBER(10,)) x y FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ')'.",
            "SELECT UPPER(CAST(b AS NUMBER(10,))) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '('. | syntax error "
                + "line 1 at position 24 unexpected ','. | syntax error line 1 at position 26 unexpected ')'.",
            "SELECT CAST(b AS NUMBER(,2)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '2'.",
            "SELECT CAST(b AS NUMBER(10 2)) FROM t");
        assertAnswer(
            "",
            "SELECT CAST(b AS VARCHAR() ) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'AS'. | syntax error "
                + "line 1 at position 25 unexpected ')'.",
            "SELECT CAST(1 AS INT AS x) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '2'.",
            "SELECT CAST((1 2) AS INT) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '2'.",
            "SELECT CAST(UPPER(1 2) AS INT) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','. | syntax error "
                + "line 1 at position 32 unexpected ')'.",
            "SELECT TOP 1 a, UPPER(CAST(1, 2)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'y'.",
            "SELECT TOP 1 a, b x y, c FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '2'.",
            "SELECT TOP 1 a, CAST(1 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ','. | syntax error "
                + "line 1 at position 27 unexpected ')'.",
            "SELECT TOP 1 a, (CAST(1, 2)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '2'.",
            "SELECT TOP 1 a, 1 2 FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'FROM'.",
            "SELECT a, TRY_CAST(b AS INT FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'FROM'.",
            "SELECT a, b, TRY_CAST(b AS INT FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'FROM'.",
            "SELECT a, UPPER(b FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '<EOF>'.",
            "SELECT a, TRY_CAST(b AS INT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'FROM'.",
            "SELECT a, (TRY_CAST(b AS INT FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'FROM'.",
            "SELECT a, UPPER(TRY_CAST(b AS INT) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'FROM'.",
            "SELECT DISTINCT a, TRY_CAST(b AS INT FROM t");
    }
}
