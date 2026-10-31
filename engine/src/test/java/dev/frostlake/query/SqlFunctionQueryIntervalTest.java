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
 * A SQL function whose body is a query over a table and a parameter is tagged by the interval its query reaches,
 * the argument's own read in for the parameter: {@code SELECT MAX(x) + a FROM other} over a NUMBER(10,2) column is
 * NUMBER(11,2)[SB2] for a 1 and [SB4] for a 1000000, a column argument brings its statistics and a NULL the empty
 * interval, and a body that is no aggregate, {@code SELECT x + a FROM other WHERE x = 1.25}, is typed from its query
 * too. A correlated scalar subquery reads the enclosing query's column the same way: {@code (SELECT MAX(x) + t.n
 * FROM other)} is [SB2] over a NUMBER(5,2) column holding 1.5 and 2.5 (live-verified).
 */
public class SqlFunctionQueryIntervalTest extends MeasuredCellsTestSupport {

    @Test
    public void aQueryBodyReadsItsArgumentsInterval() {
        engine.execute("CREATE OR REPLACE TABLE t (n NUMBER(5,2), big NUMBER(38,0))");
        engine.execute("INSERT INTO t VALUES (1.5, 5), (2.5, 3000000000)");
        engine.execute("CREATE OR REPLACE TABLE other (x NUMBER(10,2), w VARCHAR(9))");
        engine.execute("INSERT INTO other VALUES (1.25, 'p'), (2.5, 'qq')");
        engine.execute("CREATE OR REPLACE FUNCTION sq_maxa(a NUMBER) RETURNS NUMBER AS 'SELECT MAX(x) + a FROM other'");
        engine.execute("CREATE OR REPLACE FUNCTION sq_cnt(a NUMBER) RETURNS NUMBER AS 'SELECT COUNT(*) + a FROM "
            + "other'");
        engine.execute("CREATE OR REPLACE FUNCTION sq_where(a NUMBER) RETURNS NUMBER AS 'SELECT MAX(x) FROM other "
            + "WHERE x > a'");
        engine.execute("CREATE OR REPLACE FUNCTION sq_two(a NUMBER, b NUMBER) RETURNS NUMBER AS 'SELECT MAX(x) + a "
            + "* b FROM other'");
        engine.execute("CREATE OR REPLACE FUNCTION sq_row(a NUMBER) RETURNS NUMBER AS 'SELECT x + a FROM other "
            + "WHERE x = 1.25'");
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(sq_maxa(1)), SYSTEM$TYPEOF(sq_maxa(1.5))", "NUMBER(11,2)[SB2]|NUMBER(11,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(sq_maxa(n)) FROM t LIMIT 1", "NUMBER(11,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(sq_maxa(big)) FROM t LIMIT 1", "NUMBER(38,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(sq_maxa(-5))", "NUMBER(11,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(sq_maxa(1000000))", "NUMBER(11,2)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(sq_maxa(NULL))", "NUMBER(38,2)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(sq_cnt(1))", "NUMBER(19,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(sq_cnt(n)) FROM t LIMIT 1", "NUMBER(21,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(sq_where(1))", "NUMBER(10,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(sq_two(2, 3))", "NUMBER(11,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(sq_two(n, big)) FROM t LIMIT 1", "NUMBER(38,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(sq_row(7))", "NUMBER(11,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF((SELECT MAX(x) + t.n FROM other)) FROM t LIMIT 1", "NUMBER(11,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF((SELECT MAX(x) + t.big FROM other)) FROM t LIMIT 1", "NUMBER(38,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF((SELECT MAX(x) FROM other WHERE x > t.n)) FROM t LIMIT 1", "NUMBER(10,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF((SELECT MAX(o.x) + t.n * 100 FROM other o)) FROM t LIMIT 1", "NUMBER(11,2)[SB2]"},
            {"SELECT sq_maxa(1), sq_maxa(n), sq_two(n, big) FROM t ORDER BY 1, 2",
                "3.50|4.00|10.00 ;; 3.50|5.00|7500000002.50"},
        });
    }
}
