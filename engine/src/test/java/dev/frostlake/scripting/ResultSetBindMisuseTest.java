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

import dev.frostlake.LiveSnowflake;
import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A bind of a RESULTSET or a cursor binds nothing (live-verified). In a block's own expression, or in a query written
 * in one, it is refused while the block compiles, at the bind and by the name as written, on a branch that never runs
 * too: " Invalid use of resultset 'rs'." and " Invalid use of cursor 'c'.", a procedure's at its call. In a statement
 * the block runs it is refused as the statement compiles: a value as "Bind variable :rs not set." at its colon, a
 * SELECT ... INTO's counted without its INTO clause, and TABLE('s operand as "Bind variable for object \"rs\" AS rs not
 * set", placed nowhere.
 */
public class ResultSetBindMisuseTest extends MeasuredCellsTestSupport {

    /** Assignments, a LET, a RETURN and conditions whose queries bind a RESULTSET or a cursor. */
    @Test
    public void aBlockExpressionBindsNoRowSource() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1)");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(:rs)); RETURN n; END $$",
                "REFUSED SQL compilation error: error line 1 at position 92 |  Invalid use of resultset 'rs'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT COUNT(*) "
                + "FROM TABLE(:rs) t); RETURN n; END $$",
                "REFUSED SQL compilation error: error line 1 at position 92 |  Invalid use of resultset 'rs'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := 1 + (SELECT COUNT(*) "
                + "FROM TABLE(:rs)); RETURN n; END $$",
                "REFUSED SQL compilation error: error line 1 at position 96 |  Invalid use of resultset 'rs'."},
        });
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1)");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; v VARCHAR; BEGIN rs := "
                + "(SELECT 1 AS a); RETURN (SELECT COUNT(*) FROM TABLE(:rs)); RETURN n; END $$",
                "REFUSED SQL compilation error: error line 1 at position 133 |  Invalid use of resultset 'rs'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; v VARCHAR; BEGIN rs := "
                + "(SELECT 1 AS a); LET m INT := (SELECT COUNT(*) FROM TABLE(:rs)); RETURN n; END $$",
                "REFUSED SQL compilation error: error line 1 at position 139 |  Invalid use of resultset 'rs'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; v VARCHAR; BEGIN rs := "
                + "(SELECT 1 AS a); IF ((SELECT COUNT(*) FROM TABLE(:rs)) > 0) THEN n := 1; END IF; RETURN n; END $$",
                "REFUSED SQL compilation error: error line 1 at position 130 |  Invalid use of resultset 'rs'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; v VARCHAR; BEGIN rs := "
                + "(SELECT 1 AS a); n := (SELECT COUNT(*) FROM TABLE(:c)); RETURN n; END $$",
                "REFUSED SQL compilation error: error line 1 at position 131 |  Invalid use of cursor 'c'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; v VARCHAR; BEGIN rs := "
                + "(SELECT 1 AS a); n := (SELECT :rs); RETURN n; END $$",
                "REFUSED SQL compilation error: error line 1 at position 111 |  Invalid use of resultset 'rs'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; v VARCHAR; BEGIN rs := "
                + "(SELECT 1 AS a); IF (FALSE) THEN n := (SELECT COUNT(*) FROM TABLE(:rs)); END IF; RETURN n; END $$",
                "REFUSED SQL compilation error: error line 1 at position 147 |  Invalid use of resultset 'rs'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; v VARCHAR; BEGIN rs := "
                + "(SELECT 1 AS a); n := (SELECT COUNT(*) FROM TABLE(:RS)); RETURN n; END $$",
                "REFUSED SQL compilation error: error line 1 at position 131 |  Invalid use of resultset 'RS'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; v VARCHAR; BEGIN rs := "
                + "(SELECT 1 AS a); n := (SELECT COUNT(*) FROM (SELECT * FROM TABLE(:rs))); RETURN n; END $$",
                "REFUSED SQL compilation error: error line 1 at position 146 |  Invalid use of resultset 'rs'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; v VARCHAR; BEGIN rs := "
                + "(SELECT 1 AS a); v := (SELECT COUNT(*) FROM TABLE(:rs)); RETURN n; END $$",
                "REFUSED SQL compilation error: error line 1 at position 131 |  Invalid use of resultset 'rs'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; v VARCHAR; BEGIN rs := "
                + "(SELECT 1 AS a); RETURN TABLE(:rs); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 111 unexpected ':'. | syntax error "
                + "line 1 at position 114 unexpected ')'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; v VARCHAR; BEGIN rs := "
                + "(SELECT 1 AS a); n := (SELECT COUNT(*) FROM TABLE(:rs) WHERE :n IS NULL); RETURN n; END $$",
                "REFUSED SQL compilation error: error line 1 at position 131 |  Invalid use of resultset 'rs'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; v VARCHAR; BEGIN rs := "
                + "(SELECT 1 AS a); n := (SELECT COUNT(*) FROM TABLE(:nosuch)); RETURN n; END $$",
                "REFUSED SQL compilation error: error line 1 at position 131 | invalid identifier 'nosuch'"},
        });
    }

    /** A RESULTSET's query, a SELECT ... INTO and an INSERT binding a RESULTSET or a cursor. */
    @Test
    public void aStatementTheBlockRunsBindsNoRowSource() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1)");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; v VARCHAR; BEGIN rs := "
                + "(SELECT 1 AS a); LET r2 RESULTSET := (SELECT * FROM TABLE(:rs)); RETURN n; END $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 119 : SQL compilation "
                + "error: error line 0 at position -1 | Bind variable for object \"rs\" AS rs not set"},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; v VARCHAR; BEGIN rs := "
                + "(SELECT 1 AS a); SELECT COUNT(*) INTO :n FROM TABLE(:rs); RETURN n; END $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 98 : SQL compilation "
                + "error: error line 0 at position -1 | Bind variable for object \"rs\" AS rs not set"},
        });
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1)");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; BEGIN rs := (SELECT 1 AS "
                + "a); LET r2 RESULTSET := (SELECT * FROM TABLE(:RS)); RETURN n; END $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 108 : SQL compilation "
                + "error: error line 0 at position -1 | Bind variable for object RS AS RS not set"},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; BEGIN rs := (SELECT 1 AS "
                + "a); LET r2 RESULTSET := (SELECT :rs); RETURN n; END $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 108 : SQL compilation "
                + "error: error line 1 at position 7 | Bind variable :rs not set."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; BEGIN rs := (SELECT 1 AS "
                + "a); SELECT :rs INTO :n; RETURN n; END $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 87 : SQL compilation "
                + "error: error line 1 at position 7 | Bind variable :rs not set."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; BEGIN rs := (SELECT 1 AS "
                + "a); LET r2 RESULTSET := (SELECT * FROM TABLE(:c)); RETURN n; END $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 108 : SQL compilation "
                + "error: error line 0 at position -1 | Bind variable for object \"c\" AS c not set"},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; BEGIN rs := (SELECT 1 AS "
                + "a); SELECT COUNT(*) INTO :n FROM t1 WHERE a = :rs; RETURN n; END $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 87 : SQL compilation "
                + "error: error line 1 at position 34 | Bind variable :rs not set."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; BEGIN rs := (SELECT 1 AS "
                + "a); LET r2 RESULTSET := (SELECT * FROM t1 WHERE a = :rs); RETURN n; END $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 108 : SQL compilation "
                + "error: error line 1 at position 27 | Bind variable :rs not set."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; BEGIN rs := (SELECT 1 AS "
                + "a); INSERT INTO t1 SELECT * FROM TABLE(:rs); RETURN n; END $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 87 : SQL compilation "
                + "error: error line 0 at position -1 | Bind variable for object \"rs\" AS rs not set"},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; BEGIN rs := (SELECT 1 AS "
                + "a); LET r2 RESULTSET := (SELECT * FROM TABLE(rs)); RETURN n; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 130 unexpected ')'. | syntax error "
                + "line 1 at position 132 unexpected ';'."},
        });
    }

    /** A block run on its own, and a procedure created with such a bind and called. */
    @Test
    public void blocksAndProceduresBindNoRowSource() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1)");
        assertCells(new String[][] {
            {"DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; BEGIN rs := (SELECT 1 AS a); n := (SELECT "
                + "COUNT(*) FROM TABLE(:rs)); RETURN n; END;",
                "REFUSED SQL compilation error: error line 1 at position 119 |  Invalid use of resultset 'rs'."},
            {"DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; BEGIN rs := (SELECT 1 AS a); n := (SELECT :c); "
                + "RETURN n; END;",
                "REFUSED SQL compilation error: error line 1 at position 99 |  Invalid use of cursor 'c'."},
            {"DECLARE rs RESULTSET; n INT; c CURSOR FOR SELECT 1 AS a; BEGIN rs := (SELECT 1 AS a); IF (FALSE) THEN "
                + "RETURN (SELECT :rs); END IF; RETURN n; END;",
                "REFUSED SQL compilation error: error line 1 at position 117 |  Invalid use of resultset 'rs'."},
            {"CREATE OR REPLACE PROCEDURE pq() RETURNS INT LANGUAGE SQL AS $$ DECLARE rs RESULTSET; n INT; BEGIN rs := "
                + "(SELECT 1 AS a); n := (SELECT COUNT(*) FROM TABLE(:rs)); RETURN n; END $$",
                "Function PQ successfully created."},
            {"CALL pq()",
                "REFUSED SQL compilation error: error line 1 at position 92 |  Invalid use of resultset 'rs'."},
        });
    }

    /**
     * A CALL's argument binding a RESULTSET or a cursor, which the account fails with an internal error, on a branch
     * that never runs too: Frostlake refuses the bind while the block compiles, as a block's own expression's. Engine
     * only: the account's answer is an incident.
     */
    @Test
    public void aCallBindsNoRowSource() {
        assumeFalse(LiveSnowflake.enabled());
        engine.execute("CREATE OR REPLACE PROCEDURE pc(x VARCHAR) RETURNS VARCHAR LANGUAGE SQL"
            + " AS 'BEGIN RETURN x; END'");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; c CURSOR FOR SELECT 1 AS a; v VARCHAR; BEGIN rs := (SELECT 1 "
                + "AS a); CALL pc(:rs); RETURN v; END $$",
                "REFUSED SQL compilation error: error line 1 at position 99 |  Invalid use of resultset 'rs'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; c CURSOR FOR SELECT 1 AS a; v VARCHAR; BEGIN rs := (SELECT 1 "
                + "AS a); CALL pc(:c); RETURN v; END $$",
                "REFUSED SQL compilation error: error line 1 at position 99 |  Invalid use of cursor 'c'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; c CURSOR FOR SELECT 1 AS a; v VARCHAR; BEGIN rs := (SELECT 1 "
                + "AS a); CALL pc(:rs) INTO :v; RETURN v; END $$",
                "REFUSED SQL compilation error: error line 1 at position 99 |  Invalid use of resultset 'rs'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; c CURSOR FOR SELECT 1 AS a; v VARCHAR; BEGIN rs := (SELECT 1 "
                + "AS a); v := (CALL pc(:rs)); RETURN v; END $$",
                "REFUSED SQL compilation error: error line 1 at position 105 |  Invalid use of resultset 'rs'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; c CURSOR FOR SELECT 1 AS a; v VARCHAR; BEGIN rs := (SELECT 1 "
                + "AS a); IF (FALSE) THEN CALL pc(:rs); END IF; RETURN v; END $$",
                "REFUSED SQL compilation error: error line 1 at position 115 |  Invalid use of resultset 'rs'."},
            {"DECLARE rs RESULTSET; v VARCHAR; BEGIN rs := (SELECT 1 AS a); CALL pc(:rs); RETURN v; END;",
                "REFUSED SQL compilation error: error line 1 at position 70 |  Invalid use of resultset 'rs'."},
        });
    }
}
