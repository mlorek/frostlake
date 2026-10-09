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
 * INSERT in a select list is the string function's name wherever it stands, after a comma as at the
 * list's start: the token after it is refused, not the INSERT. An ON after it is refused, and then the
 * token after the name the ON is followed by, which ends the report. The token after a bare RLIKE is
 * refused twice unless it is a word; LIKE and ILIKE refuse it once. Every answer is live-verified.
 */
public class InsertCallNameLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void theTokenAfterTheNameIsRefused() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'FROM'.",
            "SELECT 'foo', INSERT FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '<EOF>'.",
            "SELECT 'foo', INSERT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '<EOF>'.",
            "SELECT 1, INSERT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ON'. | syntax "
                + "error line 1 at position 26 unexpected 'x'.",
            "SELECT 'foo', INSERT ON t x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ON'. | syntax "
                + "error line 1 at position 32 unexpected 'TO'.",
            "SELECT 'foo', INSERT ON TABLE t TO ROLE r x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'ON'. | syntax "
                + "error line 1 at position 25 unexpected 'TO'.",
            "SELECT INSERT ON TABLE t TO ROLE r");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'ON'. | syntax "
                + "error line 1 at position 23 unexpected 'x'.",
            "SELECT 1 + INSERT ON t x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'FROM'. | syntax "
                + "error line 1 at position 13 unexpected 'FROM'.",
            "SELECT RLIKE FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '<EOF>'. | syntax "
                + "error line 1 at position 12 unexpected '<EOF>'.",
            "SELECT RLIKE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'FROM'. | syntax "
                + "error line 1 at position 20 unexpected 'FROM'.",
            "SELECT 'foo', RLIKE FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '<EOF>'. | syntax "
                + "error line 1 at position 15 unexpected '<EOF>'.",
            "SELECT 1, RLIKE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'FROM'. | syntax "
                + "error line 1 at position 17 unexpected 'FROM'.",
            "SELECT 1 + RLIKE FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'FROM'.",
            "SELECT INSERT FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "SELECT INSERT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '1'.",
            "SELECT INSERT 1 2 3");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'FROM'.",
            "SELECT ILIKE FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'FROM'.",
            "SELECT LIKE FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'FROM'.",
            "SELECT 'foo', ILIKE FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'FROM'.",
            "SELECT 'foo', LIKE FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '<EOF>'.",
            "SELECT 1, ILIKE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'REGEXP'.",
            "SELECT REGEXP FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ';'. | syntax "
                + "error line 1 at position 33 unexpected 'y'.",
            "SELECT 'foo', INSERT; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'FROM'. | syntax "
                + "error line 1 at position 40 unexpected 'y'.",
            "SELECT 'foo', INSERT FROM t; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'FROM'.",
            "SELECT 1, 2, INSERT FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'FROM'.",
            "SELECT a, INSERT FROM t WHERE a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x'.",
            "SELECT 'foo', INSERT x");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | not enough arguments for function "
                + "[INSERT(1)], expected 4, got 1",
            "SELECT 'foo', INSERT (1)");
        assertAnswer(
            "foo|xbc",
            "SELECT 'foo', INSERT('abc', 1, 1, 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ','.",
            "SELECT 'foo', INSERT, 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'UPDATE'.",
            "SELECT 'foo', UPDATE FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'DELETE'.",
            "SELECT 'foo', DELETE FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier 'MERGE'",
            "SELECT 'foo', MERGE FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'.",
            "SELECT 'foo', SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'DROP'.",
            "SELECT 'foo', DROP FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'CREATE'.",
            "SELECT 'foo', CREATE FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier 'REPLACE'",
            "SELECT 'foo', REPLACE FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier 'LEFT'",
            "SELECT 'foo', LEFT FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier 'RIGHT'",
            "SELECT 'foo', RIGHT FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '<EOF>'.",
            "SELECT 1 FROM t WHERE a = INSERT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'ON'.",
            "SELECT 1 FROM t WHERE INSERT ON t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'.",
            "SELECT RLIKE x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected ';'. | syntax "
                + "error line 1 at position 12 unexpected ';'. | syntax error line 1 at position 25 unexpected 'y'.",
            "SELECT RLIKE; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'FROM'. | syntax "
                + "error line 1 at position 13 unexpected 'FROM'. | syntax error line 1 at position 32 "
                + "unexpected 'y'.",
            "SELECT RLIKE FROM t; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '<EOF>'. | syntax "
                + "error line 1 at position 27 unexpected '<EOF>'.",
            "SELECT 1 FROM t WHERE RLIKE");
    }
}
