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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * An aggregate in a subquery's WHERE over the subquery's own columns is refused like any aggregate in a WHERE, even
 * when the query around it reads a relation with the same column names: a name the subquery's own relations resolve
 * is the subquery's, since its scope hides the enclosing one, so the aggregate is no value offered by the enclosing
 * query. A QUALIFY without a window is refused ahead of what its own subqueries refuse about placement, and behind
 * what the other clauses' subqueries do (all live-verified).
 */
public class SubqueryOwnWhereAggregateTest extends BaseDatabaseTest {

    private static final String SUM_IN_WHERE = "SQL compilation error:\nInvalid aggregate function in where clause [SUM(T.A)]";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (a INT, b INT)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    @Test
    public void anAggregateOverTheSubquerysOwnColumnsIsRefusedInItsWhere() {
        assertEquals(SUM_IN_WHERE, refusal("SELECT a FROM T WHERE a = (SELECT a FROM T WHERE SUM(a) > 1)"));
        assertEquals(SUM_IN_WHERE, refusal("SELECT 1 FROM T WHERE a = (SELECT a FROM T WHERE SUM(a) > 1)"));
        assertEquals(SUM_IN_WHERE, refusal("SELECT a, (SELECT a FROM T WHERE SUM(a) > 1) FROM T"));
        assertEquals(SUM_IN_WHERE, refusal("SELECT GETVARIABLE(1) FROM T WHERE a = (SELECT a FROM T WHERE SUM(a) > 1)"));
    }

    @Test
    public void aQualifyWithoutAWindowComesBeforeOnlyItsOwnSubquerysRefusal() {
        assertEquals("SQL compilation error: error line 1 at position 16\nfound QUALIFY clause but no window function.",
            refusal("SELECT a FROM T QUALIFY a = (SELECT a FROM T WHERE SUM(a) > 1)"));
        assertEquals(SUM_IN_WHERE, refusal("SELECT a FROM T WHERE a = (SELECT a FROM T WHERE SUM(a) > 1) QUALIFY 1 = 1"));
        assertEquals(SUM_IN_WHERE, refusal("SELECT (SELECT a FROM T WHERE SUM(a) > 1) FROM T QUALIFY 1 = 1"));
        assertEquals(SUM_IN_WHERE,
            refusal("SELECT a FROM T GROUP BY a HAVING a = (SELECT a FROM T WHERE SUM(a) > 1) QUALIFY 1 = 1"));
        assertEquals(SUM_IN_WHERE, refusal("SELECT a FROM T QUALIFY 1 = 1 ORDER BY (SELECT a FROM T WHERE SUM(a) > 1)"));
    }
}
