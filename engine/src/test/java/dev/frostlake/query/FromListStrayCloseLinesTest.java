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
 * After a fault in a FROM list a ')' that closes no group of the statement is read past, a comma after it too,
 * and the next clause's fault is reported — in the next statement after a semicolon. A refused EXECUTE IMMEDIATE
 * text is read for its faults as a statement of its own is. Every answer is live-verified.
 */
public class FromListStrayCloseLinesTest extends MeasuredCellsTestSupport {

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
    public void aCloseThatClosesNoGroup() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '2'. | syntax error "
                + "line 1 at position 37 unexpected 'b'.",
            "SELECT 1 FROM FZ, 2 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'FROM'. | syntax "
                + "error line 1 at position 36 unexpected 'b'.",
            "SELECT 1 FROM FZ d FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '2'. | syntax error "
                + "line 1 at position 47 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x), 2 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '('. | syntax error "
                + "line 1 at position 53 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x), HASH(1) FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '2'. | syntax error "
                + "line 1 at position 55 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x), 2 z FROM FZ) d, FY WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'FROM'. | syntax "
                + "error line 1 at position 46 unexpected 'y'.",
            "SELECT a FROM (SELECT 1 a) d FROM FZ) WHERE x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'FROM'. | syntax "
                + "error line 1 at position 53 unexpected 'y'.",
            "SELECT a FROM (SELECT HASH(*), 1) a FROM FZ) WHERE x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'y'. | syntax error "
                + "line 1 at position 29 unexpected '2'. | syntax error line 1 at position 48 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x y, 2 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 43 unexpected 'FROM'. | syntax error line 1 at position 60 unexpected "
                + "'y'.",
            "SELECT a FROM (SELECT HASH(* REPLACE, 1) a FROM FZ) WHERE x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '2'. | syntax error "
                + "line 1 at position 28 unexpected 'b'.",
            "SELECT 1 FROM FZ, 2 WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '2'. | syntax error "
                + "line 1 at position 38 unexpected 'b'.",
            "SELECT 1 FROM FZ, 2 x FROM FZ WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '2'. | syntax error "
                + "line 1 at position 32 unexpected 'b'.",
            "SELECT 1 FROM 2 FROM FZ WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'FROM'. | syntax "
                + "error line 1 at position 35 unexpected 'b'.",
            "SELECT 1 FROM FZ d FROM FZ WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '2'. | syntax error "
                + "line 1 at position 38 unexpected 'b'.",
            "SELECT 1 FROM FZ, 2 FROM FZ)) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '2'. | syntax error "
                + "line 1 at position 41 unexpected 'b'.",
            "SELECT 1 FROM FZ, 2 FROM FZ), FY WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '2'.",
            "SELECT 1 FROM FZ, 2 FROM FZ) JOIN FY ON a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '2'. | syntax error "
                + "line 1 at position 41 unexpected 'y'.",
            "SELECT 1 FROM FZ, 2 FROM FZ); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '2'. | syntax error "
                + "line 1 at position 37 unexpected 'b'.",
            "SELECT 1 FROM FZ, 2) FROM FZ WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '2'. | syntax error "
                + "line 1 at position 40 unexpected 'b'.",
            "SELECT 1 FROM FZ, 2 FROM FZ) ORDER BY a b");
    }

    @Test
    public void theTextOfExecuteImmediate() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '1'. | syntax error "
                + "line 1 at position 20 unexpected '2'.",
            "EXECUTE IMMEDIATE 'SELECT 1 AS 1, 2 AS 2'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 39 unexpected '1'. | syntax error line 1 at position 59 unexpected 'y'.",
            "EXECUTE IMMEDIATE 'SELECT 1 FROM FZ WHERE HASH(* REPLACE (1 AS id)) = 1 AND x y'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '1'. | syntax error "
                + "line 1 at position 20 unexpected '2'.",
            "SELECT 1 AS 1, 2 AS 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 39 unexpected '1'. | syntax error line 1 at position 59 unexpected 'y'.",
            "SELECT 1 FROM FZ WHERE HASH(* REPLACE (1 AS id)) = 1 AND x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'.",
            "EXECUTE IMMEDIATE 'SELECT CASE WHEN 1 = 1 THEN x y END'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "EXECUTE IMMEDIATE 'SELECT UPPER(CAST(1, 2)) FROM FZ'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'FROM'.",
            "EXECUTE IMMEDIATE 'SELECT a, UPPER(b FROM FZ'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'.",
            "BEGIN SELECT CASE WHEN 1 = 1 THEN x y END; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '2'.",
            "BEGIN SELECT CAST(1 2) FROM FZ; END;");
    }

    @Test
    public void theFromFormAndAliasCellsThatAgree() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'FROM'.",
            "SELECT ((EXTRACT('wks' FROM d)) = 1) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'FROM'.",
            "SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM d) AS INT) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'FROM'.",
            "SELECT ((EXTRACT('wks' FROM d))) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x 'y' FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 33 unexpected 'x'.",
            "WITH c AS (SELECT HASH(* REPLACE x) h FROM FZ) SELECT * FROM c WHERE a b");
    }
}
