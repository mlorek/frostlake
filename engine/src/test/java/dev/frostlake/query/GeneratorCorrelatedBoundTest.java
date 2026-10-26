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
 * A GENERATOR bound reading the row around its query is refused as the plan holds it: an outer column inside a
 * LATERAL subquery, a value subquery or an EXISTS reads as CORRELATION(GN.N), a column of the FROM to its left as
 * GN.N, a correlated TIMELIMIT alone is argument 1; a subquery bound the account folds - a MIN, a MAX or a COUNT
 * of a table, a constant - is answered, and one it does not is echoed as its plan: a SUM adds NUMBER(38,0), an
 * ORDER BY reads ASC NULLS LAST with its LIMIT's OFFSET 0, an outer column a correlation (all live-verified).
 */
public class GeneratorCorrelatedBoundTest extends MeasuredCellsTestSupport {

    @Test
    public void outerRowAndSubqueryBounds() {
        engine.execute("CREATE OR REPLACE TABLE gn (n INT)");
        engine.execute("INSERT INTO gn VALUES (2), (3)");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'CORRELATION(GN.N)'",
            "SELECT gn.n FROM gn, LATERAL (SELECT 1 AS one FROM TABLE(GENERATOR(ROWCOUNT => gn.n))) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'CORRELATION(GN.N)'",
            "SELECT (SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => gn.n))) FROM gn ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'GN.N + 1'",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => gn.n + 1))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'CORRELATION(GN.N) + 1'",
            "SELECT (SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => gn.n + 1))) FROM gn ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'CORRELATION(GN.N) * 2'",
            "SELECT gn.n FROM gn, LATERAL (SELECT 1 AS one FROM TABLE(GENERATOR(ROWCOUNT => gn.n * 2))) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'CORRELATION(GN.N)'",
            "SELECT gn.n FROM gn WHERE EXISTS (SELECT 1 FROM TABLE(GENERATOR(ROWCOUNT => gn.n))) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'CORRELATION(GN.N)'",
            "SELECT gn.n FROM gn, LATERAL (SELECT 1 AS one FROM TABLE(GENERATOR(TIMELIMIT => gn.n))) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT MAX(X.N) AS \"MAX(X.N)\" FROM GN AS X WHERE X.N <= CORRELATION(GN.N))'",
            "SELECT COUNT(*) FROM gn, TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x.n) FROM gn x WHERE x.n <= gn.n)))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM gn)))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT 2)))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM gn)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT GN.N AS \"N\" FROM GN AS GN  ORDER BY N ASC NULLS LAST LIMIT 1 OFFSET 0)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT n FROM gn ORDER BY n LIMIT 1)))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MIN(n) + 1 FROM gn)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT SUM(CAST(GN.N AS NUMBER(38,0))) AS \"SUM(N)\" FROM GN AS GN)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT SUM(n) FROM gn)))");
    }
}
