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
 * TABLE <name> is a query wherever one stands: a statement with ORDER BY, LIMIT, OFFSET and FETCH, a parenthesized
 * query, a set operation's operand, a derived table, a CTE's body, a CREATE TABLE … AS and an INSERT's source; a
 * WHERE or an alias after the name, TABLE(name) and SELECT … FROM TABLE name are syntax errors (all live-verified).
 */
public class TableQueryStatementTest extends MeasuredCellsTestSupport {

    @Test
    public void tableNameIsAQuery() {
        engine.execute("CREATE OR REPLACE TABLE tt (a INT, b INT)");
        engine.execute("INSERT INTO tt VALUES (3, 4), (1, 2)");
        engine.execute("CREATE OR REPLACE VIEW v_tt AS SELECT a FROM tt");
        answer("TABLE tt");
        assertAnswer("1|2 ;; 3|4", "TABLE tt ORDER BY a");
        assertAnswer("3|4 ;; 1|2", "TABLE tt ORDER BY a DESC");
        assertAnswer("1|2 ;; 3|4", "TABLE test_db.test_schema.tt ORDER BY 1");
        assertAnswer("1|2", "TABLE tt ORDER BY a LIMIT 1");
        answer("TABLE tt LIMIT 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'WHERE'.",
            "TABLE tt WHERE a = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected '('.", "TABLE(tt)");
        assertAnswer("1|2 ;; 3|4", "(TABLE tt) ORDER BY 1");
        assertAnswer("1|2 ;; 1|2 ;; 3|4 ;; 3|4", "TABLE tt UNION ALL TABLE tt ORDER BY 1");
        assertAnswer("1|2 ;; 1|2 ;; 3|4 ;; 3|4", "SELECT a, b FROM tt UNION ALL TABLE tt ORDER BY 1");
        assertAnswer("1|2 ;; 3|4", "SELECT * FROM (TABLE tt) ORDER BY 1");
        assertAnswer("1|2 ;; 3|4", "WITH c AS (TABLE tt) SELECT * FROM c ORDER BY 1");
        assertAnswer("Table TT2 successfully created.", "CREATE OR REPLACE TABLE tt2 AS TABLE tt");
        assertAnswer("2", "SELECT COUNT(*) FROM tt2");
        assertAnswer("REFUSED SQL compilation error: | Object 'NO_SUCH_TT' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NO_SUCH_TT.",
            "TABLE no_such_tt");
        assertAnswer("1 ;; 3", "TABLE v_tt ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'AS'.",
            "TABLE tt AS x");
        answer("TABLE tt FETCH FIRST 1 ROW ONLY");
        assertAnswer("2", "INSERT INTO tt2 TABLE tt");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'tt'.",
            "SELECT * FROM TABLE tt");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '<EOF>'.",
            "TABLE tt ORDER BY a OFFSET 1");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|TT.A, TT.B|null|"
                + "null|null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.TT|null|A, B|1|1|1024",
            "EXPLAIN TABLE tt");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected ','.",
            "TABLE tt, tt");
    }
}
