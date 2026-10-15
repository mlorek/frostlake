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

import static dev.frostlake.query.QueryAnswers.assertCells;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * A SQL function whose body is a query is expanded into the statement as the scalar subquery it is: its value is
 * shown at the call's scale, and a body the account cannot evaluate correlated is refused where its parameter
 * reads the row, over no row too, while a constant argument runs it and SYSTEM$TYPEOF types it (live-verified).
 */
public class SqlFunctionQueryBodyTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (n NUMBER(5,2), big NUMBER(38,0))");
        engine.execute("INSERT INTO t VALUES (1.5, 5), (2.5, 3000000000)");
        engine.execute("CREATE FUNCTION sq_p(a NUMBER) RETURNS NUMBER AS 'SELECT a + 1'");
        engine.execute("CREATE FUNCTION inc_p(a NUMBER) RETURNS NUMBER AS 'a + 1'");
        engine.execute("CREATE FUNCTION sq_where(a NUMBER) RETURNS NUMBER AS 'SELECT a + 1 WHERE a > 0'");
    }

    @Test
    public void aValueIsShownAtTheCallsScale() {
        assertCells(engine, new String[][] {
            {"SELECT sq_p(n) FROM t ORDER BY n", "2.50 | 3.50"},
            {"SELECT inc_p(n) FROM t ORDER BY n", "2.50 | 3.50"},
            {"SELECT SYSTEM$TYPEOF(sq_p(n)) FROM t LIMIT 1", "NUMBER(6,2)[SB2]"},
            {"SELECT sq_p(big) FROM t ORDER BY big", "6 | 3000000001"},
        });
    }

    @Test
    public void aBodyCorrelatedThroughItsParameterIsJudged() {
        assertCells(engine, new String[][] {
            {"SELECT sq_where(n) FROM t", "SQL compilation error:|"
                + "Unsupported subquery type cannot be evaluated inside Function object: SQ_WHERE"},
            {"SELECT sq_where(n) FROM t WHERE 1 = 0", "SQL compilation error:|"
                + "Unsupported subquery type cannot be evaluated inside Function object: SQ_WHERE"},
            {"SELECT SYSTEM$TYPEOF(sq_where(n)) FROM t ORDER BY n", "NUMBER(6,2)[SB2] | NUMBER(6,2)[SB2]"},
            {"SELECT sq_where(1)", "2"},
            {"SELECT sq_where(-1)", "null"},
        });
    }
}
