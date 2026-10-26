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
 * The syntax-error lines live reports around a value query's alias, an IN list's query with a fault in a later
 * call, a first-item call's first argument in a call's argument, a method's argument followed by a value or
 * standing in a call's brackets, and an external table's virtual columns before a faulty type (all
 * live-verified).
 */
public class ValueQueryRecoveryLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void measuredLeftovers() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 75 unexpected ','. | syntax "
                + "error line 1 at position 76 unexpected '2'. | syntax error line 1 at position 79 unexpected 'AS'.",
            "CREATE OR REPLACE EXTERNAL TABLE tp_e (a INT AS (value:a::INT), b VARCHAR(5,2) AS (value:b::VARCHAR)) "
                + "LOCATION = @nosuch FILE_FORMAT = (TYPE = CSV)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 17 unexpected 'AS'.",
            "SELECT (SELECT n AS m, ABS(1 2) FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'z'. | syntax "
                + "error line 1 at position 13 unexpected 'w'. | syntax error line 1 at position 13 unexpected 'w'.",
            "SELECT x!y z w FROM pg");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'z'. | syntax "
                + "error line 1 at position 18 unexpected 'FROM'.",
            "SELECT ABS(x!y z) FROM pg");
    }

    @Test
    public void aliasesCallsAndMethods() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 17 unexpected 'AS'.",
            "SELECT (SELECT n AS m, ABS(1 2) FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 17 unexpected 'm'.",
            "SELECT (SELECT n m, ABS(1 2) FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 17 unexpected 'AS'.",
            "SELECT (SELECT n AS m, 1 2 FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 49 unexpected '2'. | syntax error line 1 at position 52 unexpected "
                + "'FROM'.",
            "SELECT * FROM rt WHERE n = (SELECT n AS m, ABS(1 2) FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 20 unexpected 'AS'. | syntax error line 1 at position 32 "
                + "unexpected '2'. | syntax error line 1 at position 42 unexpected ')'.",
            "SELECT (SELECT 1, n AS m, ABS(1 2) FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 24 unexpected '2'. | syntax error line 1 at position 39 unexpected "
                + "')'.",
            "SELECT (SELECT n, ABS(1 2) AS m FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 35 unexpected '2'. | syntax error line 1 at position 45 unexpected "
                + "')'.",
            "SELECT UPPER((SELECT n AS m, ABS(1 2) FROM rt))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 45 unexpected '2'. | syntax error line 1 at position 48 unexpected "
                + "'FROM'.",
            "SELECT * FROM rt WHERE n IN (SELECT n, ABS(1 2) FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 45 unexpected '2'. | syntax error line 1 at position 48 unexpected "
                + "'FROM'. | syntax error line 1 at position 67 unexpected ')'.",
            "SELECT * FROM rt WHERE n IN (SELECT n, ABS(1 2) FROM rt WHERE n > 0)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 49 unexpected '2'. | syntax error line 1 at position 52 unexpected "
                + "'FROM'.",
            "SELECT * FROM rt WHERE n NOT IN (SELECT n, ABS(1 2) FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 25 unexpected 'n'. | syntax error line 1 at position 27 unexpected "
                + "'IGNORE'.",
            "SELECT UPPER((SELECT SUM(n IGNORE NULLS) FROM rt))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 28 unexpected 'n'. | syntax error line 1 at position 30 unexpected "
                + "'IGNORE'.",
            "SELECT COALESCE((SELECT SUM(n IGNORE NULLS) FROM rt), 1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 25 unexpected '1'. | syntax error line 1 at position 37 unexpected "
                + "')'.",
            "SELECT UPPER((SELECT ABS(1 2) FROM rt))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 25 unexpected 'n'. | syntax error line 1 at position 27 unexpected "
                + "'RESPECT'.",
            "SELECT UPPER((SELECT SUM(n RESPECT NULLS) FROM rt))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'z'. | syntax "
                + "error line 1 at position 13 unexpected 'w'. | syntax error line 1 at position 13 unexpected 'w'.",
            "SELECT x!y z w FROM pg");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'z'. | syntax "
                + "error line 1 at position 13 unexpected 'w'. | syntax error line 1 at position 13 unexpected 'w'.",
            "SELECT x!y z w v FROM pg");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'z'. | syntax "
                + "error line 1 at position 13 unexpected 'w'. | syntax error line 1 at position 13 unexpected 'w'.",
            "SELECT x!y z w");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'z'. | syntax "
                + "error line 1 at position 13 unexpected '1'. | syntax error line 1 at position 13 unexpected '1'.",
            "SELECT x!y z 1 FROM pg");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'w'. | syntax "
                + "error line 1 at position 13 unexpected 'w'.",
            "SELECT x!y(z w) FROM pg");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'w'. | syntax "
                + "error line 1 at position 13 unexpected 'w'.",
            "SELECT ABS(z w) FROM pg");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'z'. | syntax "
                + "error line 1 at position 18 unexpected 'FROM'.",
            "SELECT ABS(x!y z) FROM pg");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'z'. | syntax "
                + "error line 1 at position 22 unexpected 'FROM'.",
            "SELECT ABS(x!y z) + 1 FROM pg");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'z'. | syntax "
                + "error line 1 at position 21 unexpected 'FROM'.",
            "SELECT ABS(x!y z, 1) FROM pg");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'z'. | syntax "
                + "error line 1 at position 20 unexpected 'FROM'.",
            "SELECT UPPER(x!y z) FROM pg WHERE 1 = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'FROM'.",
            "SELECT ABS(x!y(z) FROM pg");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'FROM'. | syntax "
                + "error line 1 at position 20 unexpected ')'.",
            "SELECT ABS(1 FROM pg)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 75 unexpected ','. | syntax "
                + "error line 1 at position 76 unexpected '2'. | syntax error line 1 at position 79 unexpected 'AS'.",
            "CREATE OR REPLACE EXTERNAL TABLE tp_e (a INT AS (value:a::INT), b VARCHAR(5,2) AS (value:b::VARCHAR)) "
                + "LOCATION = @nosuch FILE_FORMAT = (TYPE = CSV)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 100 unexpected ','. | syntax "
                + "error line 1 at position 101 unexpected '2'. | syntax error line 1 at position 104 unexpected 'AS'.",
            "CREATE OR REPLACE EXTERNAL TABLE tp_e (a INT AS (value:a::INT), b INT AS (value:b::INT), c VARCHAR(5,2) "
                + "AS (value:c::VARCHAR)) LOCATION = @nosuch FILE_FORMAT = (TYPE = CSV)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 72 unexpected '('.",
            "CREATE OR REPLACE EXTERNAL TABLE tp_e (a INT AS (value:a::INT), b NUMBER() AS (value:b::INT)) LOCATION "
                + "= @nosuch FILE_FORMAT = (TYPE = CSV)");
    }

    @Test
    public void moreShapes() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 17 unexpected 'AS'.",
            "SELECT (SELECT n AS m, 1 x y FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 20 unexpected 'AS'. | syntax error line 1 at position 28 "
                + "unexpected '2'. | syntax error line 1 at position 37 unexpected ')'.",
            "SELECT (SELECT 1, n AS m, 1 2 FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 20 unexpected 'm'. | syntax error line 1 at position 29 unexpected "
                + "'2'. | syntax error line 1 at position 39 unexpected ')'.",
            "SELECT (SELECT 1, n m, ABS(1 2) FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 17 unexpected 'AS'.",
            "SELECT (SELECT n AS m, ABS(1 2))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 20 unexpected 'AS'.",
            "SELECT 1, (SELECT n AS m, ABS(1 2) FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 38 unexpected '2'. | syntax error line 1 at position 48 unexpected "
                + "')'.",
            "SELECT COALESCE((SELECT n AS m, ABS(1 2) FROM rt), 1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 50 unexpected '2'. | syntax error line 1 at position 53 unexpected "
                + "'FROM'.",
            "SELECT * FROM rt WHERE n IN (SELECT n AS m, ABS(1 2) FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 25 unexpected '1'.",
            "SELECT UPPER((SELECT ABS(1 2)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 28 unexpected '1'. | syntax error line 1 at position 40 unexpected "
                + "')'.",
            "SELECT COALESCE((SELECT ABS(1 2) FROM rt), 1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 25 unexpected '1'. | syntax error line 1 at position 49 unexpected "
                + "')'.",
            "SELECT UPPER((SELECT ABS(1 2) FROM rt WHERE n > 0))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 39 unexpected 'n'.",
            "SELECT * FROM rt WHERE n = (SELECT SUM(n IGNORE NULLS) FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 25 unexpected 'n'. | syntax error line 1 at position 27 unexpected "
                + "'IGNORE'.",
            "SELECT UPPER((SELECT SUM(n IGNORE NULLS)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 25 unexpected 'n'. | syntax error line 1 at position 27 unexpected "
                + "'x'.",
            "SELECT UPPER((SELECT ABS(n x) FROM rt))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'z'. | syntax "
                + "error line 1 at position 17 unexpected 'w'.",
            "SELECT ABS(x!y z w) FROM pg");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'z'. | syntax "
                + "error line 1 at position 16 unexpected 'FROM'.",
            "SELECT x!y z, 1 FROM pg");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 17 unexpected 'AS'.",
            "SELECT (SELECT n AS m, ABS(1 2) FROM rt) FROM rt");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 17 unexpected 'AS'.",
            "SELECT (SELECT n AS m, SUM(1 2) FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 32 unexpected '2'. | syntax error line 1 at position 42 unexpected "
                + "')'.",
            "SELECT (SELECT n, m AS k, ABS(1 2) FROM rt)");
    }
}
