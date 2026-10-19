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
 * A faulty query that is a statement of a BEGIN ... END block: the lines it reports as a statement of its own,
 * then the block's reading of the place it was given up at - the first name from there is taken and the token
 * after it refused, a semicolon there gives the block up, an END on the way closes it (live-verified). Every
 * text is a syntax error, so none of it runs.
 */
public class BlockQueryLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void theQuerysOwnLinesAndTheBlocksAfterThem() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'. | syntax error "
                + "line 1 at position 26 unexpected '2'.",
            "BEGIN SELECT 1 AS 1, 2 AS 2; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'.",
            "BEGIN SELECT 1 AS 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'. | syntax error "
                + "line 1 at position 26 unexpected '2'. | syntax error line 1 at position 34 unexpected '3'.",
            "BEGIN SELECT 1 AS 1, 2 AS 2, 3 AS 3; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'. | syntax error "
                + "line 1 at position 26 unexpected '2'.",
            "BEGIN SELECT 1 AS 1, 2 AS 2 FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'y'. | syntax error "
                + "line 1 at position 18 unexpected ','.",
            "BEGIN SELECT 1 x y, 2 z w; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'x'. | syntax error "
                + "line 1 at position 18 unexpected 'y'. | syntax error line 1 at position 19 unexpected ')'.",
            "BEGIN SELECT (1 x y); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'x'. | syntax error "
                + "line 1 at position 41 unexpected ')'.",
            "BEGIN SELECT 1 FROM FZ WHERE id IN (1 x y); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'y'. | syntax error "
                + "line 1 at position 27 unexpected 'z'.",
            "BEGIN SELECT 1 FROM FZ x y z; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 52 unexpected ')'.",
            "BEGIN SELECT 1 FROM FZ WHERE HASH(* REPLACE (1 AS id)) = 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 47 unexpected 'AS'.",
            "BEGIN SELECT 1 FROM FZ WHERE HASH(* RENAME (id AS k)) = 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'x'.",
            "BEGIN SELECT 1 FROM FZ WHERE HASH(* EXCLUDE (id) x) = 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 28 unexpected '('. | syntax error line 1 at position 36 unexpected ')'.",
            "BEGIN SELECT HASH(* REPLACE (1 AS id)) FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'x'. | syntax error "
                + "line 1 at position 35 unexpected 'y'.",
            "BEGIN SELECT * REPLACE (1 AS id) x y FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'. | syntax error "
                + "line 1 at position 30 unexpected ')'.",
            "BEGIN SELECT * EXCLUDE (id x y) FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ','. | syntax error "
                + "line 1 at position 29 unexpected ')'. | syntax error line 1 at position 40 unexpected 'END'.",
            "BEGIN SELECT UPPER(CAST(1, 2)) FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','. | syntax error "
                + "line 1 at position 22 unexpected ')'.",
            "BEGIN SELECT CAST(1, 2); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','. | syntax error "
                + "line 1 at position 22 unexpected ')'. | syntax error line 1 at position 33 unexpected 'END'.",
            "BEGIN SELECT CAST(1, 2) FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '2'.",
            "BEGIN SELECT CAST(1 2) FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ','. | syntax error "
                + "line 1 at position 51 unexpected 'b'. | syntax error line 1 at position 54 unexpected 'END'.",
            "BEGIN SELECT 1 FROM FZ WHERE CAST(1, 2) = 1 AND id b; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'CAST'. | syntax "
                + "error line 1 at position 29 unexpected 'CAST'.",
            "BEGIN SELECT 1 FROM FZ LIMIT CAST(1, 2); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ','. | syntax error "
                + "line 1 at position 45 unexpected ')'.",
            "BEGIN SELECT 1 FROM FZ ORDER BY id, CAST(1, 2); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected ','. | syntax error "
                + "line 1 at position 34 unexpected '1'. | syntax error line 1 at position 48 unexpected 'END'.",
            "BEGIN SELECT CASE CAST(1, 2) WHEN 1 THEN 1 END; END;");
    }

    @Test
    public void statementsAroundTheQuery() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'. | syntax error "
                + "line 1 at position 26 unexpected '2'.",
            "BEGIN SELECT 1 AS 1, 2 AS 2; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 52 unexpected ')'.",
            "BEGIN SELECT 1 FROM FZ WHERE HASH(* REPLACE (1 AS id)) = 1; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ','. | syntax error "
                + "line 1 at position 29 unexpected ')'. | syntax error line 1 at position 40 unexpected 'RETURN'.",
            "BEGIN SELECT UPPER(CAST(1, 2)) FROM FZ; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'. | syntax error "
                + "line 1 at position 38 unexpected '2'.",
            "BEGIN LET q := 1; SELECT 1 AS 1, 2 AS 2; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 64 unexpected ')'.",
            "BEGIN LET q := 1; SELECT 1 FROM FZ WHERE HASH(* REPLACE (1 AS id)) = 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ','. | syntax error "
                + "line 1 at position 41 unexpected ')'. | syntax error line 1 at position 52 unexpected 'END'.",
            "BEGIN LET q := 1; SELECT UPPER(CAST(1, 2)) FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'. | syntax error "
                + "line 1 at position 26 unexpected '2'.",
            "BEGIN SELECT 1 AS 1, 2 AS 2; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 52 unexpected ')'.",
            "BEGIN SELECT 1 FROM FZ WHERE HASH(* REPLACE (1 AS id)) = 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ','. | syntax error "
                + "line 1 at position 29 unexpected ')'. | syntax error line 1 at position 40 unexpected 'END'.",
            "BEGIN SELECT UPPER(CAST(1, 2)) FROM FZ; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'. | syntax error "
                + "line 1 at position 26 unexpected '2'.",
            "BEGIN SELECT 1 AS 1, 2 AS 2 END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 52 unexpected ')'.",
            "BEGIN SELECT 1 FROM FZ WHERE HASH(* REPLACE (1 AS id)) = 1 END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ','. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "BEGIN SELECT UPPER(CAST(1, 2)) FROM FZ END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '1'. | syntax error "
                + "line 1 at position 41 unexpected '2'.",
            "DECLARE q INT; BEGIN SELECT 1 AS 1, 2 AS 2; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 67 unexpected ')'.",
            "DECLARE q INT; BEGIN SELECT 1 FROM FZ WHERE HASH(* REPLACE (1 AS id)) = 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 44 unexpected ')'. | syntax error line 1 at position 55 unexpected 'END'.",
            "DECLARE q INT; BEGIN SELECT UPPER(CAST(1, 2)) FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '1'. | syntax error "
                + "line 1 at position 41 unexpected '2'.",
            "BEGIN IF (TRUE) THEN SELECT 1 AS 1, 2 AS 2; END IF; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'. | syntax error "
                + "line 1 at position 32 unexpected '2'.",
            "BEGIN BEGIN SELECT 1 AS 1, 2 AS 2; END; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected '1'. | syntax error "
                + "line 1 at position 62 unexpected '2'.",
            "BEGIN RETURN 1; EXCEPTION WHEN OTHER THEN SELECT 1 AS 1, 2 AS 2; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '1'. | syntax error "
                + "line 1 at position 45 unexpected '2'.",
            "BEGIN FOR i IN 1 TO 2 DO SELECT 1 AS 1, 2 AS 2; END FOR; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 44 unexpected ')'. | syntax error line 1 at position 63 unexpected 'END'.",
            "BEGIN IF (TRUE) THEN SELECT UPPER(CAST(1, 2)) FROM FZ; END IF; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ','. | syntax error "
                + "line 1 at position 35 unexpected ')'. | syntax error line 1 at position 51 unexpected 'END'.",
            "BEGIN BEGIN SELECT UPPER(CAST(1, 2)) FROM FZ; END; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'. | syntax error "
                + "line 1 at position 26 unexpected '2'. | syntax error line 1 at position 41 unexpected '3'. | "
                + "syntax error line 1 at position 49 unexpected '4'.",
            "BEGIN SELECT 1 AS 1, 2 AS 2; SELECT 3 AS 3, 4 AS 4; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ','. | syntax error "
                + "line 1 at position 29 unexpected ')'. | syntax error line 1 at position 59 unexpected ','. | "
                + "syntax error line 1 at position 63 unexpected ')'.",
            "BEGIN SELECT UPPER(CAST(1, 2)) FROM FZ; SELECT UPPER(CAST(3, 4)) FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ','.",
            "BEGIN LET q := CAST(1, 2); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','.",
            "BEGIN RETURN CAST(1, 2); END;");
        answer(
            "BEGIN RETURN UPPER(CAST(1, 2)); END;");
        answer(
            "BEGIN LET q := (SELECT 1 AS 1, 2 AS 2); END;");
        answer(
            "BEGIN LET r RESULTSET := (SELECT 1 AS 1, 2 AS 2); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'. | syntax error "
                + "line 1 at position 26 unexpected '2'.",
            "BEGIN SELECT 1 AS 1, 2 AS 2 INTO :q; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '1'. | syntax error "
                + "line 1 at position 20 unexpected '2'.",
            "EXECUTE IMMEDIATE 'SELECT 1 AS 1, 2 AS 2'");
    }

    @Test
    public void theSameQueriesOutsideABlock() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '1'. | syntax error "
                + "line 1 at position 20 unexpected '2'.",
            "SELECT 1 AS 1, 2 AS 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '1'.",
            "SELECT 1 AS 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '1'. | syntax error "
                + "line 1 at position 20 unexpected '2'. | syntax error line 1 at position 28 unexpected '3'.",
            "SELECT 1 AS 1, 2 AS 2, 3 AS 3");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '1'. | syntax error "
                + "line 1 at position 20 unexpected '2'.",
            "SELECT 1 AS 1, 2 AS 2 FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'y'.",
            "SELECT 1 x y, 2 z w");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'.",
            "SELECT 1 FROM FZ WHERE id IN (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'y'.",
            "SELECT 1 FROM FZ x y z");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 39 unexpected '1'.",
            "SELECT 1 FROM FZ WHERE HASH(* REPLACE (1 AS id)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 38 unexpected 'id'.",
            "SELECT 1 FROM FZ WHERE HASH(* RENAME (id AS k)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'x'.",
            "SELECT 1 FROM FZ WHERE HASH(* EXCLUDE (id) x) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 22 unexpected '('.",
            "SELECT HASH(* REPLACE (1 AS id)) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'.",
            "SELECT * REPLACE (1 AS id) x y FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x'.",
            "SELECT * EXCLUDE (id x y) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "SELECT UPPER(CAST(1, 2)) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ','. | syntax error "
                + "line 1 at position 16 unexpected ')'.",
            "SELECT CAST(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ','. | syntax error "
                + "line 1 at position 16 unexpected ')'.",
            "SELECT CAST(1, 2) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '2'.",
            "SELECT CAST(1 2) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ','. | syntax error "
                + "line 1 at position 45 unexpected 'b'.",
            "SELECT 1 FROM FZ WHERE CAST(1, 2) = 1 AND id b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'CAST'. | syntax "
                + "error line 1 at position 23 unexpected 'CAST'.",
            "SELECT 1 FROM FZ LIMIT CAST(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected ','. | syntax error "
                + "line 1 at position 39 unexpected ')'.",
            "SELECT 1 FROM FZ ORDER BY id, CAST(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ','. | syntax error "
                + "line 1 at position 28 unexpected '1'.",
            "SELECT CASE CAST(1, 2) WHEN 1 THEN 1 END");
    }

    @Test
    public void selectListsAndBrackets() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'. | syntax error "
                + "line 1 at position 25 unexpected 'y'. | syntax error line 1 at position 28 unexpected 'END'.",
            "BEGIN SELECT 1 AS 1, 2 x y; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'. | syntax error "
                + "line 1 at position 30 unexpected 'y'. | syntax error line 1 at position 33 unexpected 'END'.",
            "BEGIN SELECT 1 AS 1 FROM FZ x y; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'. | syntax error "
                + "line 1 at position 26 unexpected '2'. | syntax error line 1 at position 45 unexpected 'b'. | "
                + "syntax error line 1 at position 48 unexpected 'END'.",
            "BEGIN SELECT 1 AS 1, 2 AS 2 FROM FZ WHERE id b; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'. | syntax error "
                + "line 1 at position 26 unexpected '2'. | syntax error line 1 at position 45 unexpected 'b'. | "
                + "syntax error line 1 at position 47 unexpected 'c'.",
            "BEGIN SELECT 1 AS 1, 2 AS 2 FROM FZ WHERE id b c; END;");
        answer(
            "BEGIN SELECT 1 AS 1, (2 x y); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'x'.",
            "BEGIN SELECT (1 x); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'x'. | syntax error "
                + "line 1 at position 18 unexpected 'y'. | syntax error line 1 at position 20 unexpected 'z'.",
            "BEGIN SELECT (1 x y z); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'x'. | syntax error "
                + "line 1 at position 18 unexpected 'y'. | syntax error line 1 at position 19 unexpected ')'.",
            "BEGIN SELECT (1 x y) FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'x'.",
            "BEGIN SELECT 1 FROM FZ WHERE id IN (1 x); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'x'. | syntax error "
                + "line 1 at position 42 unexpected 'z'.",
            "BEGIN SELECT 1 FROM FZ WHERE id IN (1 x y z); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'x'. | syntax error "
                + "line 1 at position 41 unexpected ')'.",
            "BEGIN SELECT 1 FROM FZ WHERE id IN (1 x y) AND b; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'x'.",
            "BEGIN SELECT 1 FROM FZ WHERE id IN (1 x, 2); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x'. | syntax error "
                + "line 1 at position 23 unexpected 'y'. | syntax error line 1 at position 24 unexpected ')'.",
            "BEGIN SELECT 1 IN (1 x y); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x'. | syntax error "
                + "line 1 at position 25 unexpected ')'.",
            "BEGIN SELECT 1 IN (1 x, 2); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'x'. | syntax error "
                + "line 1 at position 39 unexpected 'y'. | syntax error line 1 at position 40 unexpected ')'.",
            "BEGIN SELECT CASE WHEN 1 = 1 THEN (1 x y) END; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'x'. | syntax error "
                + "line 1 at position 31 unexpected 'x'. | syntax error line 1 at position 33 unexpected 'y'.",
            "BEGIN SELECT COUNT(DISTINCT id x y) FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x'. | syntax error "
                + "line 1 at position 23 unexpected 'y'. | syntax error line 1 at position 24 unexpected ')'.",
            "BEGIN SELECT UPPER(1 x y) FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'.",
            "BEGIN SELECT UPPER('a' x) FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 22 unexpected '1'. | syntax error line 1 at position 26 unexpected 'y'. "
                + "| syntax error line 1 at position 27 unexpected ')'.",
            "BEGIN SELECT (SELECT (1 x y)); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'.",
            "BEGIN SELECT * EXCLUDE (id x) FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'. | syntax error "
                + "line 1 at position 31 unexpected ')'.",
            "BEGIN SELECT * EXCLUDE (id x, b) FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'. | syntax error "
                + "line 1 at position 28 unexpected 'y'.",
            "BEGIN SELECT * REPLACE (1 x y) FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'x'. | syntax error "
                + "line 1 at position 36 unexpected ')'.",
            "BEGIN SELECT 1 FROM FZ WHERE (id x y); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '1'. | syntax error "
                + "line 1 at position 19 unexpected 'y'.",
            "SELECT 1 AS 1, 2 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '1'. | syntax error "
                + "line 1 at position 24 unexpected 'y'.",
            "SELECT 1 AS 1 FROM FZ x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '1'. | syntax error "
                + "line 1 at position 20 unexpected '2'. | syntax error line 1 at position 39 unexpected 'b'.",
            "SELECT 1 AS 1, 2 AS 2 FROM FZ WHERE id b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '1'. | syntax error "
                + "line 1 at position 20 unexpected '2'. | syntax error line 1 at position 39 unexpected 'b'.",
            "SELECT 1 AS 1, 2 AS 2 FROM FZ WHERE id b c");
        answer(
            "SELECT 1 AS 1, (2 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'.",
            "SELECT (1 x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y z)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'.",
            "SELECT 1 FROM FZ WHERE id IN (1 x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'.",
            "SELECT 1 FROM FZ WHERE id IN (1 x y z)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'.",
            "SELECT 1 FROM FZ WHERE id IN (1 x y) AND b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'.",
            "SELECT 1 FROM FZ WHERE id IN (1 x, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax error "
                + "line 1 at position 17 unexpected 'y'.",
            "SELECT 1 IN (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax error "
                + "line 1 at position 19 unexpected ')'.",
            "SELECT 1 IN (1 x, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'x'. | syntax error "
                + "line 1 at position 33 unexpected 'y'.",
            "SELECT CASE WHEN 1 = 1 THEN (1 x y) END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'. | syntax error "
                + "line 1 at position 25 unexpected 'x'.",
            "SELECT COUNT(DISTINCT id x y) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax error "
                + "line 1 at position 17 unexpected 'y'.",
            "SELECT UPPER(1 x y) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'x'.",
            "SELECT UPPER('a' x) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 16 unexpected '1'. | syntax error line 1 at position 20 unexpected 'y'.",
            "SELECT (SELECT (1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x'.",
            "SELECT * EXCLUDE (id x) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x'. | syntax error "
                + "line 1 at position 25 unexpected ')'.",
            "SELECT * EXCLUDE (id x, b) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'. | syntax error "
                + "line 1 at position 22 unexpected 'y'.",
            "SELECT * REPLACE (1 x y) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'.",
            "SELECT 1 FROM FZ WHERE (id x y)");
    }

    @Test
    public void starModifiers() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 27 unexpected '('. | syntax error line 1 at position 31 unexpected 'AS'.",
            "BEGIN SELECT HASH(* RENAME (id AS k)) FROM FZ; END;");
        answer(
            "BEGIN SELECT HASH(* EXCLUDE (id) EXCLUDE (b)) FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'x'.",
            "BEGIN SELECT HASH(* ILIKE 'i%' x) FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'x'. | syntax error "
                + "line 1 at position 33 unexpected 'y'. | syntax error line 1 at position 34 unexpected ')'.",
            "BEGIN SELECT HASH(* ILIKE 'i%' x y) FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 52 unexpected ','.",
            "BEGIN SELECT 1 FROM FZ WHERE HASH(* REPLACE (1 AS id, 2 AS b)) = 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 48 unexpected 'AS'.",
            "BEGIN SELECT 1 FROM FZ WHERE HASH(* REPLACE (id AS id)) = 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 50 unexpected ')'.",
            "BEGIN SELECT 1 FROM FZ WHERE HASH(* RENAME (1 AS k)) = 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 52 unexpected ')'.",
            "BEGIN SELECT 1 FROM FZ WHERE HASH(* REPLACE (1 AS id)) = 1 AND id b; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 55 unexpected ')'.",
            "BEGIN SELECT 1 FROM FZ ORDER BY HASH(* REPLACE (1 AS id)); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 55 unexpected ')'.",
            "BEGIN SELECT 1 FROM FZ GROUP BY HASH(* REPLACE (1 AS id)); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'ON'. | syntax error "
                + "line 1 at position 42 unexpected '('.",
            "BEGIN SELECT 1 FROM FZ a JOIN FZ b ON HASH(* REPLACE (1 AS id)) = 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 28 unexpected '('. | syntax error line 1 at position 36 unexpected ')'.",
            "BEGIN SELECT HASH(* REPLACE (1 AS id)); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 28 unexpected '('. | syntax error line 1 at position 36 unexpected ')'.",
            "BEGIN SELECT HASH(* REPLACE (1 AS id)) x FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'x'. | syntax error "
                + "line 1 at position 35 unexpected 'y'.",
            "BEGIN SELECT * REPLACE (1 AS id) x y; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'REPLACE'.",
            "BEGIN SELECT 1 FROM FZ WHERE HASH(* REPLACE) = 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'REPLACE'.",
            "BEGIN SELECT 1 FROM FZ WHERE HASH(* REPLACE (1)) = 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 48 unexpected ')'.",
            "BEGIN SELECT 1 FROM FZ WHERE HASH(* REPLACE (1 x)) = 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 47 unexpected ')'.",
            "BEGIN SELECT 1 FROM FZ WHERE HASH(* REPLACE (id)) = 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 46 unexpected ')'.",
            "BEGIN SELECT 1 FROM FZ WHERE HASH(* RENAME (id)) = 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 46 unexpected 'AS'.",
            "BEGIN SELECT 1 FROM FZ WHERE HASH(* RENAME id AS k) = 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 21 unexpected '('.",
            "SELECT HASH(* RENAME (id AS k)) FROM FZ");
        answer(
            "SELECT HASH(* EXCLUDE (id) EXCLUDE (b)) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'.",
            "SELECT HASH(* ILIKE 'i%' x) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'. | syntax error "
                + "line 1 at position 27 unexpected 'y'.",
            "SELECT HASH(* ILIKE 'i%' x y) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 39 unexpected '1'.",
            "SELECT 1 FROM FZ WHERE HASH(* REPLACE (1 AS id, 2 AS b)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 39 unexpected 'id'.",
            "SELECT 1 FROM FZ WHERE HASH(* REPLACE (id AS id)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 38 unexpected '1'.",
            "SELECT 1 FROM FZ WHERE HASH(* RENAME (1 AS k)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 39 unexpected '1'. | syntax error line 1 at position 60 unexpected 'b'.",
            "SELECT 1 FROM FZ WHERE HASH(* REPLACE (1 AS id)) = 1 AND id b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 42 unexpected '1'.",
            "SELECT 1 FROM FZ ORDER BY HASH(* REPLACE (1 AS id))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 42 unexpected '1'.",
            "SELECT 1 FROM FZ GROUP BY HASH(* REPLACE (1 AS id))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'ON'.",
            "SELECT 1 FROM FZ a JOIN FZ b ON HASH(* REPLACE (1 AS id)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 22 unexpected '('.",
            "SELECT HASH(* REPLACE (1 AS id))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 22 unexpected '('.",
            "SELECT HASH(* REPLACE (1 AS id)) x FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'.",
            "SELECT * REPLACE (1 AS id) x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'REPLACE'.",
            "SELECT 1 FROM FZ WHERE HASH(* REPLACE) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 39 unexpected '1'.",
            "SELECT 1 FROM FZ WHERE HASH(* REPLACE (1)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 39 unexpected '1'.",
            "SELECT 1 FROM FZ WHERE HASH(* REPLACE (1 x)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 39 unexpected 'id'.",
            "SELECT 1 FROM FZ WHERE HASH(* REPLACE (id)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'RENAME'. | syntax "
                + "error line 1 at position 38 unexpected 'id'.",
            "SELECT 1 FROM FZ WHERE HASH(* RENAME (id)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'RENAME'.",
            "SELECT 1 FROM FZ WHERE HASH(* RENAME id AS k) = 1");
    }

    @Test
    public void castsAndOtherReadings() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ','. | syntax error "
                + "line 1 at position 45 unexpected ')'.",
            "BEGIN SELECT 1 FROM FZ GROUP BY id, CAST(1, 2); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ','. | syntax error "
                + "line 1 at position 39 unexpected ')'. | syntax error line 1 at position 45 unexpected 'THEN'. | "
                + "syntax error line 1 at position 57 unexpected 'END'.",
            "BEGIN SELECT CASE WHEN UPPER(CAST(1, 2)) = 1 THEN 1 END; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ','. | syntax error "
                + "line 1 at position 29 unexpected 'AS'. | syntax error line 1 at position 35 unexpected ')'.",
            "BEGIN SELECT UPPER(CAST(1, 2 AS INT)); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 25 unexpected '('.",
            "BEGIN SELECT (SELECT CAST(1, 2)); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'VALUES'.",
            "BEGIN SELECT * FROM (VALUES (CAST(1, 2))); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '2'. | syntax error "
                + "line 1 at position 27 unexpected 'FROM'.",
            "BEGIN SELECT CAST(1 2) + 1 FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'FROM'. | syntax "
                + "error line 1 at position 48 unexpected 'b'. | syntax error line 1 at position 51 unexpected 'END'.",
            "BEGIN SELECT id, CAST(b AS INT FROM FZ WHERE id b; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '2'.",
            "BEGIN SELECT TOP 1 id, (1 2) FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','. | syntax error "
                + "line 1 at position 22 unexpected ')'.",
            "BEGIN SELECT CAST(1, 2), 3; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','. | syntax error "
                + "line 1 at position 22 unexpected ')'. | syntax error line 1 at position 27 unexpected 'END'.",
            "BEGIN SELECT CAST(1, 2) x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','. | syntax error "
                + "line 1 at position 22 unexpected ')'. | syntax error line 1 at position 26 unexpected 'FROM'.",
            "BEGIN SELECT CAST(1, 2) x FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ','. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "BEGIN SELECT UPPER(CAST(1, 2)); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ','. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "BEGIN SELECT UPPER(CAST(1, 2)), 3; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ','.",
            "BEGIN SELECT 1 FROM FZ WHERE CAST(1, 2) = 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ','. | syntax error "
                + "line 1 at position 51 unexpected 'b'. | syntax error line 1 at position 53 unexpected 'c'.",
            "BEGIN SELECT 1 FROM FZ WHERE CAST(1, 2) = 1 AND id b c; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ')'.",
            "BEGIN SELECT 1 ); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ')'. | syntax error "
                + "line 1 at position 26 unexpected 'END'.",
            "BEGIN SELECT 1 ) FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '1'.",
            "BEGIN SELECT 1 FROM FZ 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '1'.",
            "BEGIN SELECT 1 FROM FZ 1 2; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ';'.",
            "BEGIN SELECT 1 FROM FZ JOIN USING; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'y'. | syntax error "
                + "line 1 at position 38 unexpected 'y'.",
            "BEGIN SELECT 1 FROM FZ JOIN LATERAL x y; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SHARE'.",
            "BEGIN SELECT 1 FROM FZ FOR SHARE; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ';'.",
            "BEGIN SELECT 1 +; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ';'.",
            "BEGIN SELECT 1 FROM FZ WHERE; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 25 unexpected 'y'. | syntax error line 1 at position 26 unexpected ')'.",
            "BEGIN SELECT (SELECT 1 x y); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 47 unexpected ')'.",
            "BEGIN SELECT 1 FROM FZ WHERE id = (SELECT 1 x y); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 48 unexpected ')'.",
            "BEGIN SELECT 1 FROM FZ WHERE id IN (SELECT 1 x y); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'y'.",
            "BEGIN SELECT 1 FROM (SELECT 1 x y); END;");
        answer(
            "BEGIN SELECT 1 FROM FZ JOIN INNER n; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'k'.",
            "BEGIN SELECT * RENAME id k FROM FZ; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected ','. | syntax error "
                + "line 1 at position 39 unexpected ')'.",
            "SELECT 1 FROM FZ GROUP BY id, CAST(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ','. | syntax error "
                + "line 1 at position 33 unexpected ')'. | syntax error line 1 at position 39 unexpected 'THEN'.",
            "SELECT CASE WHEN UPPER(CAST(1, 2)) = 1 THEN 1 END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','. | syntax error "
                + "line 1 at position 23 unexpected 'AS'. | syntax error line 1 at position 29 unexpected ')'.",
            "SELECT UPPER(CAST(1, 2 AS INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 19 unexpected '('.",
            "SELECT (SELECT CAST(1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'VALUES'. | syntax "
                + "error line 1 at position 31 unexpected '2'.",
            "SELECT * FROM (VALUES (CAST(1, 2)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '2'. | syntax error "
                + "line 1 at position 21 unexpected 'FROM'.",
            "SELECT CAST(1 2) + 1 FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'FROM'. | syntax "
                + "error line 1 at position 42 unexpected 'b'.",
            "SELECT id, CAST(b AS INT FROM FZ WHERE id b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '2'.",
            "SELECT TOP 1 id, (1 2) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ','. | syntax error "
                + "line 1 at position 16 unexpected ')'.",
            "SELECT CAST(1, 2), 3");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ','. | syntax error "
                + "line 1 at position 16 unexpected ')'.",
            "SELECT CAST(1, 2) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ','. | syntax error "
                + "line 1 at position 16 unexpected ')'.",
            "SELECT CAST(1, 2) x FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "SELECT UPPER(CAST(1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "SELECT UPPER(CAST(1, 2)), 3");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ','.",
            "SELECT 1 FROM FZ WHERE CAST(1, 2) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ','. | syntax error "
                + "line 1 at position 45 unexpected 'b'.",
            "SELECT 1 FROM FZ WHERE CAST(1, 2) = 1 AND id b c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected ')'.",
            "SELECT 1 )");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected ')'.",
            "SELECT 1 ) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '1'.",
            "SELECT 1 FROM FZ 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '1'.",
            "SELECT 1 FROM FZ 1 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '<EOF>'.",
            "SELECT 1 FROM FZ JOIN USING");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'y'. | syntax error "
                + "line 1 at position 32 unexpected 'y'.",
            "SELECT 1 FROM FZ JOIN LATERAL x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'SHARE'.",
            "SELECT 1 FROM FZ FOR SHARE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '<EOF>'.",
            "SELECT 1 +");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '<EOF>'.",
            "SELECT 1 FROM FZ WHERE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 19 unexpected 'y'.",
            "SELECT (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'.",
            "SELECT 1 FROM FZ WHERE id = (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'SELECT'.",
            "SELECT 1 FROM FZ WHERE id IN (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'y'.",
            "SELECT 1 FROM (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'n'. | syntax error "
                + "line 1 at position 28 unexpected 'n'.",
            "SELECT 1 FROM FZ JOIN INNER n");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'k'.",
            "SELECT * RENAME id k FROM FZ");
    }

    @Test
    public void laterStatements() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'.",
            "BEGIN SELECT 1 AS 1; SELECT 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'. | syntax error "
                + "line 1 at position 26 unexpected '2'. | syntax error line 1 at position 38 unexpected 'x'. | "
                + "syntax error line 1 at position 41 unexpected 'END'.",
            "BEGIN SELECT 1 AS 1, 2 AS 2; RETURN 1 x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'x'. | syntax error "
                + "line 1 at position 41 unexpected ')'.",
            "BEGIN SELECT 1 FROM FZ WHERE id IN (1 x y); RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ','. | syntax error "
                + "line 1 at position 29 unexpected ')'. | syntax error line 1 at position 50 unexpected 'END'.",
            "BEGIN SELECT UPPER(CAST(1, 2)) FROM FZ; SELECT 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ','. | syntax error "
                + "line 1 at position 29 unexpected ')'. | syntax error line 1 at position 50 unexpected 'RETURN'.",
            "BEGIN SELECT UPPER(CAST(1, 2)) FROM FZ; SELECT 1; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ','. | syntax error "
                + "line 1 at position 29 unexpected ')'. | syntax error line 1 at position 40 unexpected 'LET'.",
            "BEGIN SELECT UPPER(CAST(1, 2)) FROM FZ; LET q := 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'CAST'. | syntax "
                + "error line 1 at position 29 unexpected 'CAST'.",
            "BEGIN SELECT 1 FROM FZ LIMIT CAST(1, 2); RETURN 1 x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'x'. | syntax error "
                + "line 1 at position 18 unexpected 'y'. | syntax error line 1 at position 19 unexpected ')'.",
            "BEGIN SELECT (1 x y); RETURN 1 x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'REPLACE'. | syntax "
                + "error line 1 at position 52 unexpected ')'.",
            "BEGIN SELECT 1 FROM FZ WHERE HASH(* REPLACE (1 AS id)) = 1; RETURN 1 x; END;");
    }
}
