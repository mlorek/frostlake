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
 * A table function written bare where a FROM clause names a relation: the lines the account reports, in a
 * statement, a subquery, a routine's body and a block (live-verified).
 */
public class BareCallInFromLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void aBareCallAndWhatFollowsIt() {
        answer(
            "CREATE OR REPLACE TABLE cb (a INT)");
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO FZ VALUES (5, TRUE), (7, FALSE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '('. | syntax error "
                + "line 1 at position 39 unexpected ')'.",
            "SELECT * FROM SPLIT_TO_TABLE('a,b', ',')");
        answer(
            "WITH c AS (SELECT * FROM FLATTEN(INPUT => PARSE_JSON('[1]'))) SELECT COUNT(*) FROM c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '('.",
            "SELECT * FROM FLATTEN(INPUT => PARSE_JSON('[1]'))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '('.",
            "SELECT * FROM FLATTEN(PARSE_JSON('[1]'))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '('. | syntax error "
                + "line 1 at position 39 unexpected ')'.",
            "SELECT * FROM SPLIT_TO_TABLE('a,b', ',') t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '('. | syntax error "
                + "line 1 at position 39 unexpected ')'.",
            "SELECT * FROM SPLIT_TO_TABLE('a,b', ',') WHERE x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '('. | syntax error "
                + "line 1 at position 43 unexpected ')'.",
            "SELECT * FROM FZ, SPLIT_TO_TABLE('a,b', ',')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '('. | syntax error "
                + "line 1 at position 39 unexpected ')'.",
            "SELECT * FROM SPLIT_TO_TABLE('a,b', ','), FZ");
        answer(
            "SELECT (SELECT COUNT(*) FROM FLATTEN(INPUT => PARSE_JSON('[1]')))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '('. | syntax error "
                + "line 1 at position 61 unexpected ')'.",
            "SELECT 1 WHERE EXISTS (SELECT 1 FROM SPLIT_TO_TABLE('a', ','))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '('. | syntax error "
                + "line 1 at position 55 unexpected ')'.",
            "SELECT * FROM (SELECT * FROM SPLIT_TO_TABLE('a,b', ','))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '('. | syntax error "
                + "line 1 at position 52 unexpected ')'.",
            "INSERT INTO cb SELECT 1 FROM SPLIT_TO_TABLE('a', ',')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected '('. | syntax error "
                + "line 1 at position 66 unexpected ')'.",
            "CREATE OR REPLACE VIEW vb AS SELECT * FROM SPLIT_TO_TABLE('a', ',')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '('.",
            "SELECT * FROM SPLIT_TO_TABLE('a,b')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '('.",
            "SELECT * FROM SPLIT_TO_TABLE()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '('. | syntax error "
                + "line 1 at position 45 unexpected ')'.",
            "SELECT * FROM FZ JOIN SPLIT_TO_TABLE('a', ',') ON TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '('. | syntax error "
                + "line 1 at position 46 unexpected ')'.",
            "SELECT * FROM STRTOK_SPLIT_TO_TABLE('a b', ' ')");
    }

    @Test
    public void inARoutinesBodyAndABlock() {
        answer(
            "CREATE OR REPLACE TABLE cb (a INT)");
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO FZ VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE FUNCTION fb() RETURNS TABLE (v VARIANT) AS 'SELECT value FROM FLATTEN(INPUT => "
                + "PARSE_JSON(''[1]''))'");
        answer(
            "CREATE FUNCTION fs() RETURNS INT AS $$(SELECT COUNT(*) FROM FLATTEN(INPUT => PARSE_JSON('[1]')))$$");
        answer(
            "EXECUTE IMMEDIATE $$ BEGIN LET c := (SELECT COUNT(*) FROM FLATTEN(INPUT => PARSE_JSON('[1]'))); "
                + "RETURN c; END; $$");
        answer(
            "CREATE PROCEDURE pb() RETURNS INT LANGUAGE SQL AS $$ BEGIN LET c := (SELECT COUNT(*) FROM "
                + "FLATTEN(INPUT => PARSE_JSON('[1]'))); RETURN c; END; $$");
        answer(
            "CALL pb()");
        assertAnswer(
            "Function PB2 successfully created.",
            "CREATE PROCEDURE pb2() RETURNS INT LANGUAGE SQL AS $$ BEGIN SELECT * FROM nosuchtable; RETURN 1; "
                + "END; $$");
        answer(
            "CREATE PROCEDURE pb3() RETURNS INT LANGUAGE SQL AS $$ BEGIN LET c := (SELECT 1 FROM x y z); RETURN "
                + "c; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'z'. | syntax error "
                + "line 1 at position 28 unexpected 'RETURN'.",
            "CREATE PROCEDURE pb4() RETURNS INT LANGUAGE SQL AS $$ BEGIN SELECT 1 FROM x y z; RETURN 1; END; $$");
        answer(
            "CREATE PROCEDURE pb5() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN (SELECT 1 FROM x y z); END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'z'. | syntax error "
                + "line 1 at position 52 unexpected 'RETURN'.",
            "CREATE PROCEDURE pb6() RETURNS INT LANGUAGE SQL AS $$ BEGIN IF (1 = 1) THEN SELECT 1 FROM x y z; END "
                + "IF; RETURN 1; END; $$");
    }
}
