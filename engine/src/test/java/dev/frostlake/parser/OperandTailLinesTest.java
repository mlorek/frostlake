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
 * Words left hanging after an operand, and how a windowed value is told. An OVER with no window after it has the
 * next token named; a {@code !} is followed by a method's name and its parenthesis, so a token that is no name
 * is named and then the {@code !}, and after a value that is no name the method's name and then its
 * parenthesis. IS UNKNOWN is refused at its IS, as IS TRUE is. A value written with OVER is no function:
 * {@code invalid function} tells it as its tree prints it - a query as {@code (SELECT 1 AS "1" FROM (VALUES
 * (null)) DUAL)}, a call with a window of its own as {@code SUM(X) OVER (ORDER BY X ASC NULLS LAST)}, a list of
 * values as {@code ROW(1, 2)} - and a function of the wrong kind is named in the order written, a call's before a
 * later operation's. Every answer is live-verified.
 */
public class OperandTailLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void whatFollowsAnOverOrABang() {
        answer(
            "CREATE OR REPLACE TABLE pg (x INT, y INT)");
        answer(
            "INSERT INTO pg VALUES (1, 2)");
        answer(
            "CREATE OR REPLACE TABLE po (x INT)");
        answer(
            "INSERT INTO po VALUES (1), (2)");
        answer(
            "CREATE OR REPLACE TABLE pq (x INT, b BOOLEAN)");
        answer(
            "INSERT INTO pq VALUES (1, TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '!'. | syntax error "
                + "line 1 at position 9 unexpected '!'.",
            "SELECT x !! y FROM pg");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '1'. | syntax error "
                + "line 1 at position 8 unexpected '!'.",
            "SELECT x!1 FROM pg");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'FROM'. | syntax "
                + "error line 1 at position 8 unexpected '!'.",
            "SELECT x! FROM pg");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '~'. | syntax error "
                + "line 1 at position 8 unexpected '!'.",
            "SELECT x!~y FROM pg");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'y'. | syntax error "
                + "line 1 at position 12 unexpected '('.",
            "SELECT (x)!y() FROM pg");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'y'. | syntax error "
                + "line 1 at position 10 unexpected '('.",
            "SELECT 1!y()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'z'. | syntax error "
                + "line 1 at position 14 unexpected '('.",
            "SELECT x!y()!z() FROM pg");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'nosuch'.",
            "SELECT x OVER (nosuch) FROM po");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'.",
            "SELECT x OVER (x) FROM po");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'w'.",
            "SELECT x OVER w FROM po");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'FROM'.",
            "SELECT x OVER FROM po");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'FROM'.",
            "SELECT ROW_NUMBER() OVER FROM po");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'IS'.",
            "SELECT b IS TRUE FROM pq");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'select'. | syntax "
                + "error line 1 at position 8 unexpected '!'.",
            "SELECT x!select() FROM pg");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '('.",
            "SELECT x IS NULL OVER () FROM po");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'IS'.",
            "SELECT b IS NOT TRUE FROM pq");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'IS'.",
            "SELECT b IS FALSE FROM pq");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'IS'.",
            "SELECT b IS UNKNOWN FROM pq");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'w'.",
            "SELECT x OVER w, 1 FROM po");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'FROM'.",
            "SELECT x ! y FROM pg");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'FROM'.",
            "SELECT x!y FROM pg");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function X!Y.",
            "SELECT x!y() FROM pg");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function \"!\".",
            "SELECT !x FROM pg");
        assertAnswer(
            "true",
            "SELECT x != y FROM pg");
    }

    @Test
    public void aWindowedValueIsToldAsParsed() {
        answer(
            "CREATE OR REPLACE TABLE pg (x INT, y INT)");
        answer(
            "INSERT INTO pg VALUES (1, 2)");
        answer(
            "CREATE OR REPLACE TABLE po (x INT)");
        answer(
            "INSERT INTO po VALUES (1), (2)");
        answer(
            "CREATE OR REPLACE TABLE pq (x INT, b BOOLEAN)");
        answer(
            "INSERT INTO pq VALUES (1, TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid query block: (.",
            "SELECT 1 = ANY ((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid function 'ROW(1, 2)'",
            "SELECT (1, 2) OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid function '(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)'",
            "SELECT (SELECT 1) OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid function '(SELECT X AS \"X\" FROM PO AS PO)'",
            "SELECT (SELECT x FROM po) OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid function 'SUM(X) OVER (ORDER BY X ASC NULLS LAST)'",
            "SELECT SUM(x) OVER (ORDER BY x) OVER () FROM po");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid function '\"COUNT(*)\"() OVER ()'",
            "SELECT COUNT(*) OVER () OVER () FROM po");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid function type [ABS] for window function.",
            "SELECT ABS(x) OVER (), x::INT OVER () FROM pq");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid function type [NOSUCHFN] for window function.",
            "SELECT nosuchfn(1) OVER (), x::INT OVER () FROM pq");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid function type [CAST] for window function.",
            "SELECT x::INT OVER (), ABS(x) OVER () FROM pq");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid function type [CASE] for window function.",
            "SELECT CASE WHEN x = 1 THEN 1 END OVER (), ABS(x) OVER () FROM pq");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid function type [ABS] for window function.",
            "SELECT ABS(x) OVER (), CASE WHEN x = 1 THEN 1 END OVER () FROM pq");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid function 'X'",
            "SELECT x OVER () FROM po");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid function type [+] for window function.",
            "SELECT (x + 1) OVER () FROM po");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid function '1'",
            "SELECT x + 1 OVER () FROM po");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid function '1'",
            "SELECT 1 OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid function ''a''",
            "SELECT 'a' OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid function 'null'",
            "SELECT NULL OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid function 'X'",
            "SELECT 1 FROM po WHERE x OVER () = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid function 'X'",
            "SELECT x OVER (PARTITION BY x) FROM po");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid function 'X'",
            "SELECT x OVER (ORDER BY nosuch) FROM po");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid query block: 1.",
            "SELECT 1 = ANY (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid query block: (.",
            "SELECT 1 = ANY (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid query block: 1.",
            "SELECT 1 = ANY 1");
        assertAnswer(
            "true",
            "SELECT 1 = ALL (SELECT 1 AS x)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid function '(SELECT 1 AS \"1\", 2 AS \"2\" FROM (VALUES "
                + "(null)) DUAL)'",
            "SELECT (SELECT 1, 2) OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid function '(SELECT X AS \"X\" FROM PO AS PO  ORDER BY X ASC "
                + "NULLS LAST LIMIT 1 OFFSET 0)'",
            "SELECT (SELECT x FROM po ORDER BY x LIMIT 1) OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid function 'SUM(X) OVER (PARTITION BY X ORDER BY X DESC NULLS "
                + "FIRST)'",
            "SELECT SUM(x) OVER (PARTITION BY x ORDER BY x DESC) OVER () FROM po");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid function 'ROW_NUMBER() OVER (ORDER BY X ASC NULLS LAST)'",
            "SELECT ROW_NUMBER() OVER (ORDER BY x) OVER () FROM po");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid function 'COUNT(X) OVER ()'",
            "SELECT COUNT(x) OVER () OVER () FROM po");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid function 'SUM(X) OVER ()'",
            "SELECT SUM(x) OVER () OVER (ORDER BY x) FROM po");
    }
}
