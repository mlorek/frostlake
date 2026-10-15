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
 * A null treatment written inside a call's parentheses. FIRST_VALUE and LAST_VALUE take IGNORE NULLS or RESPECT
 * NULLS there or after the parenthesis; NTH_VALUE, LAG and LEAD after it alone; no other function takes one.
 * Written inside a function that takes none, the word is the one line - {@code SUM(n IGNORE NULLS)}, in a window
 * or not, and nothing after it is read. LAG, LEAD and NTH_VALUE are read as closed at the word, so the
 * parenthesis after it is named too: their own, or the one of a call around them, which takes the window -
 * {@code ABS(LAG(n IGNORE NULLS) OVER (ORDER BY n))} names the last parenthesis. An IGNORE the parenthesis
 * follows at once is dropped and nothing more is named. Every answer is live-verified.
 */
public class InsideNullTreatmentTest extends MeasuredCellsTestSupport {

    @Test
    public void aTreatmentAmongNamedArguments() {
        answer(
            "CREATE OR REPLACE TABLE rt (n INT)");
        answer(
            "INSERT INTO rt VALUES (1), (2)");
        answer(
            "CREATE OR REPLACE TABLE rt2 (n INT)");
        answer(
            "INSERT INTO rt2 VALUES (1), (NULL), (3)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'IGNORE'.",
            "SELECT MEDIAN(x => n IGNORE NULLS) OVER () FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'IGNORE'.",
            "SELECT MEDIAN(n IGNORE NULLS) OVER () FROM rt2");
        assertAnswer(
            "1 ;; 1 ;; 1",
            "SELECT FIRST_VALUE(x => n IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "1 ;; 1 ;; 1",
            "SELECT FIRST_VALUE(n IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "1 ;; 1 ;; 1",
            "SELECT FIRST_VALUE(x => n) IGNORE NULLS OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 30 unexpected ')'.",
            "SELECT LAG(x => n IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'IGNORE'.",
            "SELECT SUM(x => n IGNORE NULLS) OVER () FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'RESPECT'.",
            "SELECT SUM(x => n RESPECT NULLS) OVER () FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'.",
            "SELECT SUM(n IGNORE NULLS) OVER () FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'IGNORE'.",
            "SELECT MEDIAN(x => n) IGNORE NULLS OVER () FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'IGNORE'.",
            "SELECT MIN(x => n IGNORE NULLS, 1) OVER () FROM rt2");
    }

    @Test
    public void insideAndAfterTheParentheses() {
        answer(
            "CREATE OR REPLACE TABLE rt (n INT)");
        answer(
            "INSERT INTO rt VALUES (1), (2)");
        answer(
            "CREATE OR REPLACE TABLE rt2 (n INT)");
        answer(
            "INSERT INTO rt2 VALUES (1), (NULL), (3)");
        assertAnswer(
            "1 ;; 1 ;; 1",
            "SELECT FIRST_VALUE(n IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "1 ;; 1 ;; 1",
            "SELECT FIRST_VALUE(n) IGNORE NULLS OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "3 ;; 3 ;; 3",
            "SELECT LAST_VALUE(n IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "3 ;; 3 ;; 3",
            "SELECT LAST_VALUE(n) IGNORE NULLS OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 34 unexpected ')'.",
            "SELECT NTH_VALUE(n, 1 IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "1 ;; 1 ;; 1",
            "SELECT NTH_VALUE(n, 1) IGNORE NULLS OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 26 unexpected ')'.",
            "SELECT LEAD(n IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "3 ;; null ;; null",
            "SELECT LEAD(n) IGNORE NULLS OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 25 unexpected ')'.",
            "SELECT LAG(n IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'IGNORE'.",
            "SELECT ARRAY_AGG(n IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'IGNORE'.",
            "SELECT ARRAY_AGG(n) IGNORE NULLS OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'.",
            "SELECT MIN(n IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'IGNORE'.",
            "SELECT MIN(n) IGNORE NULLS OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'.",
            "SELECT MAX(n IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'IGNORE'.",
            "SELECT MAX(n) IGNORE NULLS OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'IGNORE'.",
            "SELECT COUNT(n IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'IGNORE'.",
            "SELECT COUNT(n) IGNORE NULLS OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'.",
            "SELECT AVG(n IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'IGNORE'.",
            "SELECT AVG(n) IGNORE NULLS OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'IGNORE'.",
            "SELECT ANY_VALUE(n IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'IGNORE'.",
            "SELECT ANY_VALUE(n) IGNORE NULLS OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'IGNORE'.",
            "SELECT LISTAGG(n IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'IGNORE'.",
            "SELECT LISTAGG(n) IGNORE NULLS OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "1 ;; 1 ;; 1",
            "SELECT FIRST_VALUE(n RESPECT NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'RESPECT'. | syntax "
                + "error line 1 at position 26 unexpected ')'.",
            "SELECT LAG(n RESPECT NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 31 unexpected ')'.",
            "SELECT LEAD(x => n IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 44 unexpected ')'.",
            "SELECT NTH_VALUE(x => n, y => 1 IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "3 ;; 3 ;; 3",
            "SELECT LAST_VALUE(x => n IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'IGNORE'.",
            "SELECT MEDIAN(n IGNORE NULLS) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'.",
            "SELECT SUM(n IGNORE NULLS) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | Missing window specification for function [FIRST_VALUE(RT2.N)].",
            "SELECT FIRST_VALUE(n IGNORE NULLS) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'.",
            "SELECT ABS(n IGNORE NULLS) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 28 unexpected ')'.",
            "SELECT LAG(n IGNORE NULLS, 1) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 28 unexpected ')'.",
            "SELECT LAG(n, 1 IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
    }

    @Test
    public void whereTheCallStands() {
        answer(
            "CREATE OR REPLACE TABLE rt (n INT)");
        answer(
            "INSERT INTO rt VALUES (1), (2)");
        answer(
            "CREATE OR REPLACE TABLE rt2 (n INT)");
        answer(
            "INSERT INTO rt2 VALUES (1), (NULL), (3)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 25 unexpected ')'.",
            "SELECT LAG(n IGNORE NULLS) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 26 unexpected ')'.",
            "SELECT LEAD(n IGNORE NULLS) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 34 unexpected ')'.",
            "SELECT NTH_VALUE(n, 1 IGNORE NULLS) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 25 unexpected ')'.",
            "SELECT LAG(n IGNORE NULLS) OVER (ORDER BY n), 1 FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 28 unexpected ')'.",
            "SELECT 1, LAG(n IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 25 unexpected ')'.",
            "SELECT LAG(n IGNORE NULLS) OVER (ORDER BY n) + 1 FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 25 unexpected ')'.",
            "SELECT LAG(n IGNORE NULLS) OVER (ORDER BY n) AS c FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 25 unexpected ')'.",
            "SELECT LAG(n IGNORE NULLS) OVER (ORDER BY n), LAG(n IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'.",
            "SELECT SUM(n IGNORE NULLS) OVER (), SUM(n IGNORE NULLS) OVER () FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'.",
            "SELECT SUM(n IGNORE NULLS), LAG(n IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 25 unexpected ')'.",
            "SELECT LAG(n IGNORE NULLS) OVER (ORDER BY n), SUM(n IGNORE NULLS) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'IGNORE'.",
            "SELECT ABS(SUM(n IGNORE NULLS)) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 29 unexpected ')'.",
            "SELECT LAG(ABS(n IGNORE NULLS)) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 48 unexpected ')'.",
            "SELECT ABS(LAG(n IGNORE NULLS) OVER (ORDER BY n)) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 30 unexpected ')'.",
            "SELECT ABS(LAG(n IGNORE NULLS)) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'IGNORE'.",
            "SELECT 1 FROM rt2 WHERE ABS(n IGNORE NULLS) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 44 unexpected ')'.",
            "SELECT 1 FROM rt2 QUALIFY LAG(n IGNORE NULLS) OVER (ORDER BY n) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'IGNORE'.",
            "SELECT nosuch(n IGNORE NULLS) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'IGNORE'.",
            "SELECT test_db.test_schema.nosuch(n IGNORE NULLS) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'.",
            "SELECT SUM(n IGNORE) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'.",
            "SELECT SUM(n IGNORE NULLS x) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'.",
            "SELECT LAG(n IGNORE) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 27 unexpected ')'.",
            "SELECT LAG(n IGNORE NULLS x) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ','. | syntax error "
                + "line 1 at position 36 unexpected ')'.",
            "SELECT FIRST_VALUE(n IGNORE NULLS, 1) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 27 unexpected ')'.",
            "SELECT FIRST_VALUE(n IGNORE) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: duplicated use of null handling option",
            "SELECT FIRST_VALUE(n IGNORE NULLS) IGNORE NULLS OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: duplicated use of null handling option",
            "SELECT FIRST_VALUE(n RESPECT NULLS) IGNORE NULLS OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'NULLS'. | syntax "
                + "error line 1 at position 23 unexpected ')'.",
            "SELECT LAG(IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'NULLS'.",
            "SELECT SUM(IGNORE NULLS) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'IGNORE'.",
            "SELECT SUM(DISTINCT n IGNORE NULLS) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 25 unexpected ')'.",
            "SELECT LAG(n IGNORE NULLS) OVER (ORDER BY n) FROM rt2 WHERE SUM(n IGNORE NULLS) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'IGNORE'.",
            "SELECT RATIO_TO_REPORT(n IGNORE NULLS) OVER () FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'NULLS'.",
            "SELECT ROW_NUMBER(IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'IGNORE'.",
            "SELECT CONDITIONAL_TRUE_EVENT(n > 1 IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'ignore'. | syntax "
                + "error line 1 at position 25 unexpected ')'.",
            "SELECT lag(n ignore nulls) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'IGNORE'.",
            "SELECT \"LAG\"(n IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 26 unexpected ')'.",
            "SELECT LAG (n IGNORE NULLS) OVER (ORDER BY n) FROM rt2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 25 unexpected ')'.",
            "SELECT LAG(n IGNORE NULLS) OVER (ORDER BY n) FROM rt2; SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 25 unexpected ')'.",
            "SELECT LAG(n IGNORE NULLS) OVER (ORDER BY n)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'.",
            "SELECT LAG(n IGNORE NULLS");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'IGNORE'.",
            "SELECT SUM(n IGNORE NULLS");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'IGNORE'. | syntax "
                + "error line 1 at position 48 unexpected 'ORDER'. | syntax error line 1 at position 58 unexpected "
                + "')'.",
            "SELECT * FROM (SELECT LAG(n IGNORE NULLS) OVER (ORDER BY n) FROM rt2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'IGNORE'.",
            "SELECT * FROM (SELECT SUM(n IGNORE NULLS) FROM rt2) x");
    }
}
