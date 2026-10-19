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
package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A query assigned in parentheses, res := (SELECT …), that does not parse reports the faults the query has,
 * where they stand in the text, the end a clause still waits at being the parenthesis that closes the query.
 * A refusal the query earns when it runs is placed from the opening parenthesis: one column further in on its
 * first line, and lines down when the query stands below it. A query a RESULTSET is declared with keeps its
 * own places. TOP takes a number written out, so a colon after it reads a path of a column TOP
 * (live-verified).
 */
public class AssignedQueryLinesAndPlacesTest extends MeasuredCellsTestSupport {

    @Test
    public void theQuerysOwnSyntaxLines() {
        answer(
            "CREATE OR REPLACE TABLE tp (k INT, v INT)");
        answer(
            "INSERT INTO tp VALUES (1, 10), (2, 20)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 107 unexpected ')'.",
            "DECLARE s NUMBER(10,2) DEFAULT 1; res RESULTSET; BEGIN res := (SELECT v AS c11 FROM tp ORDER BY v "
                + "OFFSET :s); RETURN TABLE(res); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 79 unexpected 'AS'. | syntax error "
                + "line 1 at position 86 unexpected 'FROM'.",
            "DECLARE s NUMBER(10,2) DEFAULT 2; res RESULTSET; BEGIN res := (SELECT TOP :s v AS c14 FROM tp ORDER "
                + "BY v); RETURN TABLE(res); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 92 unexpected ':'.",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT * FROM tp UNPIVOT (x FOR y IN (:s))); "
                + "RETURN TABLE(res); END;");
        assertAnswer(
            "20",
            "DECLARE s NUMBER(10,2) DEFAULT 1; res RESULTSET; BEGIN res := (SELECT v FROM tp ORDER BY v LIMIT 1 "
                + "OFFSET :s); RETURN TABLE(res); END;");
        assertAnswer(
            "10",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT v FROM tp ORDER BY v LIMIT :s); RETURN "
                + "TABLE(res); END;");
        assertAnswer(
            "10",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT TOP 1 v FROM tp ORDER BY v); RETURN "
                + "TABLE(res); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 96 unexpected ')'.",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT v AS c FROM tp ORDER BY v OFFSET :s); "
                + "RETURN TABLE(res); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 8 | invalid identifier 'TOP'",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT TOP :s v FROM tp ORDER BY v); RETURN "
                + "TABLE(res); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 83 unexpected ';'.",
            "DECLARE s INT DEFAULT 1; x INT; BEGIN SELECT v INTO :x FROM tp ORDER BY v OFFSET :s; RETURN x; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: error line 1 at position 7 | invalid identifier 'TOP'",
            "DECLARE s INT DEFAULT 1; x INT; BEGIN SELECT TOP :s v INTO :x FROM tp ORDER BY v; RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '<EOF>'.",
            "SELECT v FROM tp ORDER BY v OFFSET 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'x'.",
            "SELECT v AS c11 FROM tp ORDER BY v OFFSET 1 x");
        assertAnswer(
            "K|1 ;; V|10 ;; K|2 ;; V|20",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT * FROM tp UNPIVOT (x FOR y IN (k, v))); "
                + "RETURN TABLE(res); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 92 unexpected ':'.",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT * FROM tp UNPIVOT (x FOR y IN (:s, "
                + "v))); RETURN TABLE(res); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 42 | Bind variable :s not set.",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT * FROM tp PIVOT (SUM(v) FOR k IN "
                + "(:s))); RETURN TABLE(res); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 91 unexpected ':'.",
            "DECLARE s INT DEFAULT 1; x INT; BEGIN SELECT COUNT(*) INTO :x FROM tp UNPIVOT (x FOR y IN (:s)); "
                + "RETURN x; END;");
    }

    @Test
    public void placesCountFromTheParenthesis() {
        answer(
            "CREATE OR REPLACE TABLE tp (k INT, v INT)");
        answer(
            "INSERT INTO tp VALUES (1, 10), (2, 20)");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 8 | invalid identifier 'MISSING'",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT missing FROM tp WHERE k = :s); RETURN "
                + "TABLE(res); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 8 | invalid identifier 'MISSING'",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT missing FROM tp); RETURN TABLE(res); "
                + "END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 12 | invalid identifier 'MISSING'",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT :s, missing FROM tp); RETURN "
                + "TABLE(res); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 24 | invalid identifier 'MISSING'",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT k FROM tp WHERE missing = :s); RETURN "
                + "TABLE(res); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: | Object 'NOSUCH_ZQ7' does not exist or not authorized. Your primary role <ROLE> must have "
                + "at least one privilege granted on TABLE NOSUCH_ZQ7.",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT k FROM nosuch_zq7 WHERE k = :s); RETURN "
                + "TABLE(res); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 8 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT ABS(1, 2) FROM tp); RETURN TABLE(res); "
                + "END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 43 : SQL compilation "
                + "error: error line 1 at position 23 | invalid identifier 'MISSING'",
            "DECLARE s INT DEFAULT 1; x INT; BEGIN x := (SELECT missing FROM tp WHERE k = :s); RETURN x; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 2 at position 8 | invalid identifier 'MISSING'",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (\n SELECT missing FROM tp WHERE k = :s); "
                + "RETURN TABLE(res); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 44 : SQL compilation "
                + "error: error line 1 at position 7 | invalid identifier 'MISSING'",
            "BEGIN LET s INT := 1; LET res RESULTSET := (SELECT missing FROM tp WHERE k = :s); RETURN TABLE(res); "
                + "END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 48 : SQL compilation "
                + "error: error line 1 at position 7 | invalid identifier 'MISSING'",
            "DECLARE s INT DEFAULT 1; res RESULTSET DEFAULT (SELECT missing FROM tp WHERE k = :s); BEGIN RETURN "
                + "TABLE(res); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : Date 'x' is not "
                + "recognized",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT k FROM tp WHERE k = :s AND TO_DATE('x') "
                + "IS NULL); RETURN TABLE(res); END;");
    }

    @Test
    public void rowCountsAndListsOfNames() {
        answer(
            "CREATE OR REPLACE TABLE tp (k INT, v INT)");
        answer(
            "INSERT INTO tp VALUES (1, 10), (2, 20)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 90 unexpected ')'.",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT v FROM tp ORDER BY v OFFSET 1); RETURN "
                + "TABLE(res); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 76 unexpected ')'.",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT v FROM tp WHERE); RETURN TABLE(res); "
                + "END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 95 unexpected ':'.",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT * FROM tp UNPIVOT (x FOR y IN (k, "
                + ":s))); RETURN TABLE(res); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ':'.",
            "DECLARE s INT DEFAULT 1; x INT; BEGIN SELECT COUNT(*) INTO :x FROM tp UNPIVOT (x FOR y IN (k, :s)); "
                + "RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 70 unexpected 'FROM'.",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT TOP :s * FROM tp); RETURN TABLE(res); "
                + "END;");
        assertAnswer(
            "20",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT v FROM tp LIMIT :s OFFSET :s); RETURN "
                + "TABLE(res); END;");
    }

    @Test
    public void theDeclaredFormsAndQueriesInsideConstructs() {
        answer(
            "CREATE OR REPLACE TABLE tn (s VARCHAR(10), n NUMBER(10,2))");
        answer(
            "INSERT INTO tn VALUES (NULL, 1.5)");
        answer(
            "CREATE OR REPLACE TABLE tp (k INT, v INT)");
        answer(
            "INSERT INTO tp VALUES (1, 10), (2, 20)");
        answer(
            "CREATE OR REPLACE TABLE tq (n NUMBER(10,2), s VARCHAR)");
        answer(
            "INSERT INTO tq VALUES (1.5, 'ab')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 9 | invalid identifier 'MISSING'",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := ( SELECT missing FROM tp); RETURN TABLE(res); "
                + "END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 2 at position 1 | invalid identifier 'MISSING'",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT k,\n missing FROM tp); RETURN "
                + "TABLE(res); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 36 | invalid identifier 'MISSING'",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT k FROM tp WHERE k = (SELECT missing "
                + "FROM tp)); RETURN TABLE(res); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 19 | invalid identifier 'MISSING'",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (WITH c AS (SELECT missing FROM tp) SELECT * "
                + "FROM c); RETURN TABLE(res); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 81 | invalid identifier 'NOSUCH'",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT k FROM tp WHERE k = :nosuch); RETURN "
                + "TABLE(res); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 28 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN LET res RESULTSET := (SELECT ABS(1, 2) FROM tp); RETURN TABLE(res); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "DECLARE res RESULTSET DEFAULT (SELECT ABS(1, 2) FROM tp); BEGIN RETURN TABLE(res); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 68 : SQL compilation "
                + "error: error line 1 at position 8 | invalid identifier 'MISSING'",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN IF (TRUE) THEN res := (SELECT missing FROM tp WHERE k "
                + "= :s); END IF; RETURN TABLE(res); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 72 : SQL compilation "
                + "error: error line 1 at position 8 | invalid identifier 'MISSING'",
            "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN FOR i IN 1 TO 1 DO res := (SELECT missing FROM tp "
                + "WHERE k = :s); END FOR; RETURN TABLE(res); END;");
    }
}
