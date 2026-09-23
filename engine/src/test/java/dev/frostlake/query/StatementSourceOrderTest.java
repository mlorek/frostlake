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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A missing relation or a duplicate alias anywhere in a query — in a subquery, a CTE or a derived table as in
 * the query itself — outranks every other compilation fault but a window frame's, over an empty table too.
 * The sources are checked scope by scope: a WITH clause's bodies, then a level's own FROM clause (its missing
 * relations, then its names in written order, a derived table's or a join condition's query where it is
 * written), then the queries in its other clauses in written order, each whole before the next. A CTE inside
 * a subquery compiles with it, whether or not its columns are read. Every cell is live-verified.
 */
public class StatementSourceOrderTest extends BaseDatabaseTest {

    private static final String DUPLICATE_X = "SQL compilation error:|duplicate alias 'X'";

    @BeforeEach
    public void createTables() {
        engine.execute("CREATE OR REPLACE TABLE re (a INT)");
        engine.execute("CREATE OR REPLACE TABLE ft (d DATE)");
        engine.execute("CREATE OR REPLACE TABLE full1 (a INT)");
        engine.execute("INSERT INTO full1 VALUES (1)");
    }

    /** The first row's first cell, empty for no rows, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String missing(final String name) {
        return hinted("SQL compilation error:|Object '" + name + "' does not exist or not authorized.");
    }

    private static String duplicate(final String name) {
        return "SQL compilation error:|duplicate alias '" + name + "'";
    }

    @Test
    public void aNestedDuplicateAliasOutranksEveryOtherFault() {
        final String[] statements = {
            "SELECT 1 FROM re WHERE a = (SELECT 1 FROM ft x, ft x) AND nosuch2 = 1",
            "SELECT nosuch2 FROM re WHERE EXISTS (SELECT 1 FROM ft x, ft x)",
            "SELECT nosuch2, (SELECT 1 FROM ft x, ft x) FROM re",
            "SELECT 1 FROM re GROUP BY nosuch2 HAVING COUNT(*) > (SELECT 1 FROM ft x, ft x)",
            "SELECT 1 FROM re WHERE UPPER(1, 2) = (SELECT 1 FROM ft x, ft x)",
            "SELECT NOSUCHFN(1) FROM re WHERE EXISTS (SELECT 1 FROM ft x, ft x)",
            "SELECT 1 FROM re JOIN ft ON nosuch2 = 1 WHERE EXISTS (SELECT 1 FROM ft x, ft x)",
            "SELECT 1 FROM re WHERE UPPER(ARRAY_CONSTRUCT()) = 'a' AND EXISTS (SELECT 1 FROM ft x, ft x)",
            "SELECT SUM(a), a FROM re WHERE EXISTS (SELECT 1 FROM ft x, ft x)",
            "SELECT 1 FROM re QUALIFY a = 1 AND EXISTS (SELECT 1 FROM ft x, ft x)",
            "SELECT 1 FROM re WHERE SUM(a) > 0 AND EXISTS (SELECT 1 FROM ft x, ft x)",
            "SELECT 1 FROM re WHERE EXISTS (SELECT 1 FROM ft x, ft x) GROUP BY 2",
            "SELECT a FROM re WHERE EXISTS (SELECT 1 FROM ft x, ft x) ORDER BY 5",
            "SELECT LEFT('a') FROM re WHERE EXISTS (SELECT 1 FROM ft x, ft x)",
            "SELECT 1 FROM re WHERE EXISTS (SELECT 1 FROM ft x, ft x) AND a.b.c.d.e = 1",
            "SELECT a FROM re, re r2 WHERE EXISTS (SELECT 1 FROM ft x, ft x)",
            "SELECT 1 FROM re WHERE EXISTS (SELECT 1 FROM ft x, ft x) AND a = (SELECT 1, 2)",
            "SELECT 1 FROM re WHERE NOSUCHFN(1) = 1 AND nosuch2 = 1 AND EXISTS (SELECT 1 FROM ft x, ft x)",
            "SELECT 1 FROM re WHERE EXISTS (SELECT 1 FROM ft x, ft x) ORDER BY nosuch2",
            "SELECT nosuch2 FROM re UNION ALL SELECT 1 FROM ft x, ft x",
            "SELECT nosuch2 FROM re UNION ALL SELECT (SELECT 1 FROM ft x, ft x)",
            "SELECT nosuch2 FROM re WHERE a IN (SELECT a FROM full1 x, full1 x)",
            "SELECT nosuch2, (SELECT 1 FROM ft x, ft x)",
            "WITH c AS (SELECT 1 AS a) SELECT nosuch2 FROM re WHERE EXISTS (SELECT 1 FROM c x, c x)",
            "SELECT 1 FROM re WHERE EXISTS (WITH c AS (SELECT 1 FROM ft x, ft x) SELECT 1 FROM c) AND nosuch2 = 1",
            "SELECT 1 FROM re WHERE nosuch2 = (SELECT 1 FROM ft x, full1 x)",
            "SELECT 1 FROM re WHERE nosuch2 = 1 AND EXISTS (SELECT 1 FROM ft WHERE EXISTS (SELECT 1 FROM ft x, ft x))",
            "SELECT 1 FROM re WHERE EXISTS (SELECT nosuch FROM ft WHERE EXISTS (SELECT 1 FROM ft x, ft x))",
        };
        for (final String sql : statements) {
            assertEquals(DUPLICATE_X, answer(sql), sql);
        }
        assertEquals(missing("NOSUCH"), answer("SELECT nosuch2 FROM re WHERE a = (SELECT 1 FROM nosuch)"));
        assertEquals(missing("NOSUCH3"),
            answer("SELECT 1 FROM re WHERE a = (SELECT nosuch FROM ft) AND a = (SELECT 1 FROM nosuch3)"));
        assertEquals(missing("NOSUCH3"),
            answer("SELECT 1 FROM re WHERE EXISTS (SELECT 1 FROM ft WHERE nosuch = 1) AND EXISTS (SELECT 1 FROM nosuch3)"));
        assertEquals(missing("NOSUCH3"), answer("SELECT nosuch2 FROM re WHERE EXISTS (SELECT 1 FROM ft, nosuch3)"));
        assertEquals("SQL compilation error: error line 1 at position 20|Window frame requires an ORDER BY clause.",
            answer("SELECT ROW_NUMBER() OVER (ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM re"
                + " WHERE EXISTS (SELECT 1 FROM ft x, ft x)"));
    }

    @Test
    public void sourcesAreCheckedScopeByScopeInWrittenOrder() {
        assertEquals(duplicate("A"), answer("SELECT 1 FROM re a, ft a WHERE EXISTS (SELECT 1 FROM nosuch)"));
        assertEquals(duplicate("Y"), answer("SELECT (SELECT 1 FROM nosuch) FROM re y, re y"));
        assertEquals(duplicate("Y"), answer("SELECT (SELECT 1 FROM ft x, ft x) FROM re y, re y"));
        assertEquals(missing("NOSUCH2"), answer("SELECT (SELECT 1 FROM ft x, ft x) FROM nosuch2"));
        assertEquals(missing("NOSUCH2"), answer("SELECT 1 FROM re, nosuch2 WHERE EXISTS (SELECT 1 FROM ft x, ft x)"));
        assertEquals(missing("NOSUCH"),
            answer("SELECT 1 FROM re WHERE a = (SELECT 1 FROM nosuch) AND EXISTS (SELECT 1 FROM ft x, ft x)"));
        assertEquals(DUPLICATE_X,
            answer("SELECT 1 FROM re WHERE EXISTS (SELECT 1 FROM ft x, ft x) AND a = (SELECT 1 FROM nosuch)"));
        assertEquals(DUPLICATE_X,
            answer("SELECT 1 FROM re WHERE a = (SELECT nosuch FROM ft) AND a = (SELECT 1 FROM ft x, ft x)"));
        assertEquals(missing("NOSUCH"), answer("SELECT 1 FROM re WHERE EXISTS (SELECT 1 FROM ft WHERE EXISTS"
            + " (SELECT 1 FROM nosuch)) AND EXISTS (SELECT 1 FROM ft x, ft x)"));
        assertEquals(DUPLICATE_X, answer("WITH c AS (SELECT nosuch FROM ft) SELECT 1 FROM re x, re x"));
        assertEquals(missing("NOSUCH"), answer("SELECT 1 FROM (SELECT 1 FROM nosuch) d, re d"));
        assertEquals(DUPLICATE_X, answer("SELECT 1 FROM (SELECT 1 FROM ft x, ft x) d, re d"));
        assertEquals(DUPLICATE_X, answer("SELECT 1 FROM re d JOIN ft e ON EXISTS (SELECT 1 FROM ft x, ft x) JOIN ft d ON TRUE"));
        assertEquals(DUPLICATE_X, answer("SELECT 1 FROM re x, re x UNION ALL SELECT 1 FROM nosuch"));
        assertEquals(DUPLICATE_X, answer("SELECT 1 FROM re WHERE EXISTS (SELECT 1 FROM ft x JOIN ft y ON nosuch = 1 JOIN ft x ON TRUE)"));
        assertEquals(DUPLICATE_X, answer("SELECT 1 FROM re WHERE a = (SELECT 1 FROM (SELECT 1 FROM ft x, ft x))"));
        assertEquals(missing("NOSUCH"), answer("SELECT 1 FROM re WHERE a = (WITH w AS (SELECT 1 FROM nosuch) SELECT 1 FROM w)"));
    }

    @Test
    public void aCteInsideASubqueryCompilesWithIt() {
        final Object[][] cells = {
            {"SELECT 1 FROM re WHERE a = (WITH w AS (SELECT nosuch FROM ft) SELECT 1 FROM w)", 46},
            {"SELECT 1 FROM re WHERE a = (WITH w AS (SELECT nosuch FROM ft) SELECT 1)", 46},
            {"SELECT (WITH w AS (SELECT nosuch FROM ft) SELECT 1)", 26},
            {"SELECT (WITH w AS (SELECT nosuch FROM ft) SELECT 1 FROM w) FROM re", 26},
            {"SELECT 1 FROM re WHERE a IN (WITH w AS (SELECT nosuch FROM ft) SELECT 1 FROM w)", 47},
            {"SELECT 1 FROM full1 WHERE a = (WITH w AS (SELECT nosuch FROM ft) SELECT 1 FROM w)", 49},
            {"SELECT 1 FROM re WHERE EXISTS (WITH w AS (SELECT nosuch FROM ft) SELECT 1 FROM w)", 49},
            {"SELECT 1 FROM re WHERE a = (WITH w AS (SELECT 1 AS b) SELECT nosuch FROM w)", 61},
            {"SELECT 1 FROM full1 WHERE EXISTS (WITH w AS (SELECT nosuch FROM ft) SELECT 1 FROM w)", 52},
        };
        for (final Object[] cell : cells) {
            assertEquals("SQL compilation error: error line 1 at position " + cell[1] + "|invalid identifier 'NOSUCH'",
                answer((String) cell[0]), (String) cell[0]);
        }
        assertEquals("SQL compilation error: error line 1 at position 49|too many arguments for function [UPPER(1, 2)]"
            + " expected 1, got 2", answer("SELECT 1 FROM re WHERE EXISTS (WITH w AS (SELECT UPPER(1, 2) FROM ft) SELECT 1 FROM w)"));
    }
}
