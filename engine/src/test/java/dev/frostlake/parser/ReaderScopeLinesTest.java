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
 * Syntax-line readers outside a statement's own select list: a bracketed literal read through in a CTE's and a
 * derived table's query and before a call's last argument, a join's USING without its list in any query, and a
 * dotted unset session variable named whole (all live-verified).
 */
public class ReaderScopeLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void readerScopes() {
        engine.execute("CREATE OR REPLACE TABLE T (x INT, y INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'. | syntax "
                + "error line 1 at position 23 unexpected ')'.",
            "SELECT ABS(ABS((1 x y)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x'. | syntax "
                + "error line 1 at position 28 unexpected ')'.",
            "SELECT ABS(ABS(ABS((1 x y))))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'x'. | syntax "
                + "error line 1 at position 35 unexpected ')'.",
            "WITH c AS (SELECT ABS(ABS((1 x y)))) SELECT * FROM c");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'x'. | syntax "
                + "error line 1 at position 39 unexpected ')'.",
            "SELECT * FROM (SELECT ABS(ABS((1 x y))))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'x'. | syntax "
                + "error line 1 at position 33 unexpected ')'.",
            "SELECT 1; SELECT ABS(ABS((1 x y)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'. | syntax "
                + "error line 1 at position 23 unexpected ')'.",
            "SELECT ABS(ABS((1 x y))); SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'. | syntax "
                + "error line 1 at position 27 unexpected ')'.",
            "SELECT IFF(TRUE, (1 x y), 2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SHARE'.",
            "SELECT * FROM T ORDER BY 1 FOR SHARE");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'x'.",
            "SELECT * FROM T FOR UPDATE NOWAIT x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                + "error line 1 at position 10 unexpected ')'.",
            "SET x ((1)); SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                + "error line 1 at position 10 unexpected ','.",
            "SET x ((1), 2); SELECT 1");
        assertAnswer("REFUSED SQL compilation error: error line 0 at position -1 | Session variable '$X.Y' does not "
                + "exist",
            "SELECT $x.y");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Session variable '$X' does not exist",
            "SELECT $x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'USING'.",
            "WITH c AS (SELECT * FROM T JOIN T u USING x) SELECT * FROM c");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'USING'. | "
                + "syntax error line 1 at position 47 unexpected ')'.",
            "SELECT * FROM (SELECT * FROM T JOIN T u USING x)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'USING'.",
            "SELECT 1; SELECT * FROM T JOIN T u USING x");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void moreReaderScopes() {
        engine.execute("CREATE OR REPLACE TABLE T (x INT, y INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'. | syntax "
                + "error line 1 at position 30 unexpected ')'.",
            "WITH c AS (SELECT ABS((1 x y))) SELECT * FROM c");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'x'. | syntax "
                + "error line 1 at position 34 unexpected ')'.",
            "SELECT * FROM (SELECT ABS((1 x y)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'x'. | syntax "
                + "error line 1 at position 26 unexpected ')'.",
            "SELECT COALESCE((1 x y), 2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'.",
            "SELECT CAST((1 x) AS INT)");
        assertAnswer("REFUSED SQL compilation error: error line 0 at position -1 | Session variable '$X.Y.Z' does not "
                + "exist",
            "SELECT $x.y.z");
        assertAnswer("REFUSED SQL compilation error: error line 0 at position -1 | Session variable '$X.Y' does not "
                + "exist",
            "SELECT $x.y FROM T");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'USING'.",
            "SELECT 1 FROM T JOIN T u USING x; SELECT 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'USING'.",
            "(SELECT * FROM T JOIN T u USING x)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'.",
            "SELECT * FROM T WHERE x IN (SELECT * FROM T JOIN T u USING x)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'USING'.",
            "WITH c AS (SELECT * FROM T JOIN T u USING x), d AS (SELECT 1) SELECT * FROM c");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'.",
            "SELECT IFF(TRUE, 1, (1 x y))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'x'. | syntax "
                + "error line 1 at position 36 unexpected 'FROM'.",
            "WITH c AS (SELECT ABS(ABS((1 x y))) FROM T) SELECT * FROM c");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'x'. | syntax "
                + "error line 1 at position 40 unexpected 'FROM'.",
            "SELECT * FROM (SELECT ABS(ABS((1 x y))) FROM T) d");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'. | syntax "
                + "error line 1 at position 23 unexpected ')'.",
            "SELECT ABS(ABS((1 x y))) FROM T");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
