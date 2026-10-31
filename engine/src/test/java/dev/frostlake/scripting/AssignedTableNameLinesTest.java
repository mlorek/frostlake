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
package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A block's plain assignment of a query in parentheses whose faults are FROM items TABLE( over a bare name, the target
 * declared of any type or not at all, a LET of a RESULTSET too: the account reads the query as a value, where TABLE(
 * takes a call (live-verified). It refuses the ')' after the name, takes it for the call's own, and TABLE( either
 * closes at the next ')', leaving the query one ')' short, refused at the token after its parenthesis, or closes before
 * the alias, the join or the clause after the call, refused there. A LET of another type reads its query as a
 * statement; a block run on its own and a procedure's body as it is created read the assignment as EXECUTE IMMEDIATE's
 * text does.
 */
public class AssignedTableNameLinesTest extends MeasuredCellsTestSupport {

    /** One TABLE( over a name in a declared INT's query, the query and what follows it varied. */
    @Test
    public void aPlainAssignmentReadsItsQueryAsAValue() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1)");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(rs)); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax error "
                + "line 1 at position 96 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT * FROM "
                + "(SELECT 1 FROM TABLE(rs))); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 102 unexpected ')'. | syntax error "
                + "line 1 at position 105 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(rs) t, TABLE(rs) u); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax error "
                + "line 1 at position 96 unexpected 't'. | syntax error line 1 at position 107 unexpected ')'. | "
                + "syntax error line 1 at position 109 unexpected 'u'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(rs)) + 1; RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax error "
                + "line 1 at position 97 unexpected '+'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(rs) AS t); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax error "
                + "line 1 at position 96 unexpected 'AS'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(rs) JOIN t1 ON TRUE); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax error "
                + "line 1 at position 96 unexpected 'JOIN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM t1, TABLE(rs)); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 98 unexpected ')'. | syntax error "
                + "line 1 at position 100 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(rs) WHERE a = 1); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax error "
                + "line 1 at position 96 unexpected 'WHERE'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(x.rs)); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 96 unexpected ')'. | syntax error "
                + "line 1 at position 98 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); LET m INT := 1 + (SELECT "
                + "COUNT(*) FROM TABLE(rs)); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 77 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 106 unexpected ')'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(rs)) ; RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax error "
                + "line 1 at position 97 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT a FROM "
                + "TABLE(rs) ORDER BY a); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 87 unexpected ')'. | syntax error "
                + "line 1 at position 89 unexpected 'ORDER'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT a FROM "
                + "TABLE(rs) t WHERE t.a = 1); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 87 unexpected ')'. | syntax error "
                + "line 1 at position 89 unexpected 't'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := ((SELECT COUNT(*) "
                + "FROM TABLE(rs))); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 95 unexpected ')'. | syntax error "
                + "line 1 at position 98 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(nosuch)); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 98 unexpected ')'. | syntax error "
                + "line 1 at position 100 unexpected ';'."},
        });
    }

    /** Derived tables, joins, set operations, clauses, quoted and qualified names, other targets. */
    @Test
    public void eachClauseAfterTheCallIsRefused() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1)");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT * FROM "
                + "(SELECT 1 FROM TABLE(rs)) t); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 102 unexpected ')'. | syntax error "
                + "line 1 at position 107 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(rs), t1); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax error "
                + "line 1 at position 95 unexpected ','."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(rs) LEFT JOIN t1 ON TRUE); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax error "
                + "line 1 at position 96 unexpected 'LEFT'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(a.b.rs)); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 98 unexpected ')'. | syntax error "
                + "line 1 at position 100 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(rs)) - (SELECT 1); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax error "
                + "line 1 at position 97 unexpected '-'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(rs) t); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax error "
                + "line 1 at position 96 unexpected 't'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(rs) GROUP BY 1); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax error "
                + "line 1 at position 96 unexpected 'GROUP'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM t1 JOIN TABLE(rs) ON TRUE); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 102 unexpected ')'. | syntax error "
                + "line 1 at position 104 unexpected 'ON'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (WITH c AS (SELECT "
                + "1) SELECT COUNT(*) FROM TABLE(rs)); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 115 unexpected ')'. | syntax error "
                + "line 1 at position 117 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(rs) UNION ALL SELECT 1); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax error "
                + "line 1 at position 96 unexpected 'UNION'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(rs) QUALIFY TRUE); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax error "
                + "line 1 at position 96 unexpected 'QUALIFY'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(rs) LIMIT 1); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax error "
                + "line 1 at position 96 unexpected 'LIMIT'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(\"rs\")); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 96 unexpected ')'. | syntax error "
                + "line 1 at position 98 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(rs)) || 'x'; RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax error "
                + "line 1 at position 97 unexpected '||'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); nn := (SELECT COUNT(*) "
                + "FROM TABLE(rs)); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 95 unexpected ')'. | syntax error "
                + "line 1 at position 97 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); rs := (SELECT COUNT(*) "
                + "FROM TABLE(rs)); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 95 unexpected ')'. | syntax error "
                + "line 1 at position 97 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(rs) SAMPLE (10)); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax error "
                + "line 1 at position 96 unexpected 'SAMPLE'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(rs) AS t (a)); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax error "
                + "line 1 at position 96 unexpected 'AS'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(rs) WHERE a IN (SELECT 1)); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax error "
                + "line 1 at position 96 unexpected 'WHERE'."},
        });
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1)");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; v VARCHAR; BEGIN rs := "
                + "(SELECT 1 AS a); v := (SELECT COUNT(*) FROM TABLE(rs)); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 133 unexpected ')'. | syntax error "
                + "line 1 at position 135 unexpected ';'."},
        });
    }

    /** A block run on its own and a procedure body created with such an assignment. */
    @Test
    public void blocksAndProcedureBodiesReadSoToo() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1)");
        assertCells(new String[][] {
            {"DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; BEGIN rs := (SELECT 1 AS a); n := (SELECT "
                + "COUNT(*) FROM TABLE(rs)); RETURN n; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 121 unexpected ')'. | syntax error "
                + "line 1 at position 123 unexpected ';'."},
            {"DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; BEGIN rs := (SELECT 1 AS a); n := (SELECT "
                + "COUNT(*) FROM TABLE(rs) t JOIN t1 ON TRUE); RETURN n; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 121 unexpected ')'. | syntax error "
                + "line 1 at position 123 unexpected 't'."},
            {"CREATE OR REPLACE PROCEDURE pq() RETURNS INT LANGUAGE SQL AS $$ DECLARE rs RESULTSET; n INT; BEGIN rs := "
                + "(SELECT 1 AS a); n := (SELECT COUNT(*) FROM TABLE(rs)); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax error "
                + "line 1 at position 96 unexpected ';'."},
            {"CREATE OR REPLACE PROCEDURE pq() RETURNS INT LANGUAGE SQL AS $$ DECLARE rs RESULTSET; n INT; BEGIN rs := "
                + "(SELECT 1 AS a); n := (SELECT COUNT(*) FROM TABLE(rs) t); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax error "
                + "line 1 at position 96 unexpected 't'."},
        });
    }
}
