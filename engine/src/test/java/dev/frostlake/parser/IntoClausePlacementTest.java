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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Every query block reads an INTO clause, and only a block's own SELECT … INTO statement may carry one
 * (live-verified): anywhere else — a set operation, a subquery, a derived table, a CTE, CREATE TABLE … AS, a view,
 * INSERT … SELECT, EXPLAIN, UPDATE, DELETE, MERGE, and a block's RESULTSET, cursor, RETURN, assignment, condition and
 * FOR source — the statement is refused while it compiles, at the SELECT of the query block carrying it, before any
 * name in it is judged.
 */
public class IntoClausePlacementTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private String value(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    private static String notAllowed(final int line, final int position) {
        return "SQL compilation error: error line " + line + " at position " + position
            + " INTO clause is not allowed in this context";
    }

    private static String syntax(final int position, final String token) {
        return """
            SQL compilation error:
            syntax error line 1 at position %d unexpected '%s'.""".formatted(position, token);
    }

    private static String invalid(final int position, final String name) {
        return """
            SQL compilation error: error line 1 at position %d
            invalid identifier '%s'""".formatted(position, name);
    }

    private static String declaredTwice(final int position, final String name) {
        return """
            SQL compilation error: error line 1 at position %d
             Variable with name '%s' declared twice.""".formatted(position, name);
    }

    /** {@code DECLARE x INT; BEGIN <statement> RETURN x; END;} */
    private static String block(final String statement) {
        return "DECLARE x INT; BEGIN " + statement + " RETURN x; END;";
    }

    @Test
    public void everyQueryBlockOfAStatementIsRefusedAtItsSelect() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertEquals(notAllowed(1, 1), refusal("(SELECT 1 INTO t)"));
        assertEquals(notAllowed(1, 0), refusal("SELECT 1 INTO t UNION SELECT 2"));
        assertEquals(notAllowed(1, 15), refusal("SELECT 1 UNION SELECT 2 INTO t"));
        assertEquals(notAllowed(1, 0), refusal("SELECT 1 INTO t MINUS SELECT 2"));
        assertEquals(notAllowed(1, 0), refusal("SELECT 1 INTO t UNION ALL SELECT 2 INTO u"));
        assertEquals(notAllowed(3, 4), refusal("SELECT 1\n  UNION\n    SELECT 2 INTO t"));
        assertEquals(notAllowed(1, 15), refusal("SELECT * FROM (SELECT 1 INTO t)"));
        assertEquals(notAllowed(1, 8), refusal("SELECT (SELECT 1 INTO t)"));
        assertEquals(notAllowed(1, 23), refusal("SELECT 1 WHERE EXISTS (SELECT 1 INTO t)"));
        assertEquals(notAllowed(1, 11), refusal("WITH c AS (SELECT 1 INTO t) SELECT * FROM c"));
        assertEquals(notAllowed(1, 0), refusal("SELECT TOP 1 1 INTO t"));
    }

    @Test
    public void everyStatementCarryingAQueryIsRefusedToo() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertEquals(notAllowed(1, 18), refusal("CREATE TABLE u AS SELECT 1 INTO t"));
        assertEquals(notAllowed(1, 19), refusal("CREATE TABLE u AS (SELECT 1 INTO t)"));
        assertEquals(notAllowed(1, 52),
            refusal("CREATE OR REPLACE TABLE u AS WITH c AS (SELECT 1 a) SELECT a INTO t FROM c"));
        assertEquals(notAllowed(1, 17), refusal("CREATE VIEW v AS SELECT 1 INTO t"));
        assertEquals(notAllowed(1, 14), refusal("INSERT INTO t SELECT 1 INTO u"));
        assertEquals(notAllowed(1, 15), refusal("INSERT INTO t (SELECT 1 INTO u)"));
        assertEquals(notAllowed(1, 8), refusal("EXPLAIN SELECT 1 INTO t"));
        assertEquals(notAllowed(1, 18), refusal("UPDATE t SET a = (SELECT 1 INTO u)"));
        assertEquals(notAllowed(1, 26), refusal("DELETE FROM t WHERE a IN (SELECT 1 INTO u)"));
        assertEquals(notAllowed(1, 20),
            refusal("MERGE INTO t USING (SELECT 1 a INTO u) s ON t.a = s.a WHEN MATCHED THEN DELETE"));
    }

    @Test
    public void theRefusalComesBeforeAnyName() {
        assertEquals(notAllowed(1, 20), refusal("SELECT nosuch FROM (SELECT 1 INTO t)"));
        assertEquals(notAllowed(1, 0), refusal("SELECT 1 INTO t UNION SELECT nosuch"));
        assertEquals(notAllowed(1, 33), refusal("SELECT a FROM nosuch_table UNION SELECT 2 INTO t"));
    }

    @Test
    public void aTargetListThatDoesNotReadIsASyntaxError() {
        assertEquals(syntax(16, "INTO"), refusal("SELECT 1 INTO t INTO u"));
        assertEquals(syntax(14, "5"), refusal("SELECT 1 INTO 5"));
        assertEquals(syntax(15, "1"), refusal("SELECT 1 INTO :1"));
        assertEquals(syntax(15, "."), refusal("SELECT 1 INTO t.a"));
        assertEquals(syntax(15, "\"t\""), refusal("SELECT 1 INTO :\"t\""));
        assertEquals(syntax(30, "\"x\""), refusal("SELECT * FROM (SELECT 1 INTO :\"x\")"));
        assertEquals(syntax(21, "\"x\""), refusal("BEGIN SELECT 1 INTO :\"x\"; END;"));
        assertEquals(syntax(75, "\"x\""),
            refusal("DECLARE x INT; BEGIN LET r RESULTSET := (SELECT 1 INTO :x); SELECT 1 INTO :\"x\"; END;"));
    }

    @Test
    public void inABlockOnlyItsOwnSelectIntoTakesTheClause() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        assertEquals(notAllowed(1, 41), refusal(block("LET r RESULTSET := (SELECT 1 INTO :x);")));
        assertEquals(notAllowed(1, 38), refusal(block("LET c CURSOR FOR SELECT 1 INTO :x;")));
        assertEquals(notAllowed(1, 36), refusal(block("SELECT * FROM (SELECT 1 INTO :x);")));
        assertEquals(notAllowed(1, 21), refusal(block("SELECT 1 INTO :x UNION SELECT 2;")));
        assertEquals(notAllowed(1, 21), refusal(block("SELECT 1 INTO :x FROM t UNION SELECT 2;")));
        assertEquals(notAllowed(1, 36), refusal(block("SELECT 1 UNION SELECT 2 INTO :x;")));
        assertEquals(notAllowed(1, 27), refusal(block("x := (SELECT 1 INTO :x);")));
        assertEquals(notAllowed(1, 26), refusal(block("IF ((SELECT 1 INTO :x) = 1) THEN RETURN 1; END IF;")));
        assertEquals(notAllowed(1, 31), refusal(block("FOR r IN (SELECT 1 INTO :x) DO RETURN 1; END FOR;")));
        assertEquals(notAllowed(1, 52), refusal(block("SELECT 1 INTO :x WHERE EXISTS (SELECT 1 INTO :x);")));
        assertEquals(notAllowed(1, 29), refusal("DECLARE x INT; BEGIN RETURN (SELECT 1 INTO :x); END;"));
        assertEquals(notAllowed(1, 28), refusal("DECLARE x INT; c CURSOR FOR SELECT 1 INTO :x; BEGIN RETURN x; END;"));
        assertEquals(notAllowed(1, 36),
            refusal("DECLARE x INT; r RESULTSET DEFAULT (SELECT 1 INTO :x); BEGIN RETURN x; END;"));
        assertEquals(notAllowed(3, 10), refusal("""
            DECLARE x INT; BEGIN
              SELECT *
                FROM (SELECT 1 INTO :x);
              RETURN x;
            END;"""));
        assertEquals("1", value(block("WITH c AS (SELECT 1 a) SELECT a INTO :x FROM c;")));
    }

    @Test
    public void anIntoInTheSelectListOfABlocksSelectIntoIsRefusedAtThatStatement() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        final String declare = "DECLARE x INT; y INT; BEGIN ";
        assertEquals(notAllowed(1, 28), refusal(declare + "SELECT (SELECT 1 INTO :y) INTO :x; RETURN x; END;"));
        assertEquals(notAllowed(1, 28), refusal(declare + "SELECT 1 + (SELECT 1 INTO :y) INTO :x; RETURN x; END;"));
        assertEquals(notAllowed(1, 28), refusal(declare + "SELECT (SELECT 1 INTO :y), 2 INTO :x, :y; RETURN x; END;"));
        assertEquals(notAllowed(1, 28), refusal(declare + "SELECT (SELECT 1 INTO :y) INTO :zz; RETURN x; END;"));
        assertEquals(notAllowed(1, 28), refusal(declare + "SELECT (SELECT 1 INTO :y) INTO :x, :x; RETURN x; END;"));
        assertEquals(notAllowed(2, 0), refusal(declare + """

            SELECT
              (SELECT 1 INTO :y)
              INTO :x;
            RETURN x;
            END;"""));
        assertEquals(notAllowed(1, 63),
            refusal(declare + "SELECT a INTO :x FROM t WHERE a = (SELECT 1 INTO :y); RETURN x; END;"));
        assertEquals(notAllowed(1, 51), refusal(declare + "SELECT a INTO :x FROM (SELECT 1 a INTO :y); RETURN x; END;"));
        assertEquals(notAllowed(1, 62),
            refusal(declare + "SELECT a INTO :x FROM t ORDER BY (SELECT 1 INTO :y); RETURN x; END;"));
        assertEquals(notAllowed(1, 39),
            refusal(declare + "WITH c AS (SELECT 1 a INTO :y) SELECT a INTO :x FROM c; RETURN x; END;"));
    }

    @Test
    public void theBlockIsRefusedWholeAndInItsCompileOrder() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertEquals(notAllowed(1, 52),
            refusal("DECLARE x INT; BEGIN IF (1 = 2) THEN SELECT * FROM (SELECT 1 INTO :x); END IF; RETURN 5; END;"));
        assertEquals(notAllowed(1, 60),
            refusal("DECLARE x INT; BEGIN RETURN nosuch_ret; LET r RESULTSET := (SELECT 1 INTO :x); END;"));
        assertEquals(notAllowed(1, 21), refusal(block("SELECT :nosuch INTO :x UNION SELECT 2;")));
        assertEquals(notAllowed(1, 41), refusal(block("LET r RESULTSET := (SELECT nosuch INTO :x);")));
        assertEquals(notAllowed(1, 36), refusal("BEGIN RETURN ?; LET r RESULTSET := (SELECT 1 INTO :x); END;"));
        assertEquals(notAllowed(1, 73), refusal(
            "DECLARE x INT; BEGIN LET y INT := 1; LET y INT := 2; LET r RESULTSET := (SELECT 1 INTO :x); END;"));
        assertEquals(declaredTwice(15, "X"),
            refusal("DECLARE x INT; x INT; BEGIN LET r RESULTSET := (SELECT 1 INTO :x); RETURN 1; END;"));
        assertEquals(declaredTwice(15, "X"), refusal("DECLARE x INT; x INT; BEGIN RETURN ?; END;"));
        assertEquals(notAllowed(1, 47), refusal("BEGIN INSERT INTO t VALUES (7); SELECT * FROM (SELECT 1 INTO :x); END;"));
        assertEquals("0", value("SELECT COUNT(*) FROM t"));
    }

    @Test
    public void aRoutineBodyIsJudgedWhereTheAccountCompilesIt() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        final String udf = "Compilation of SQL UDF failed: " + notAllowed(1, 1);
        assertEquals(udf, refusal("CREATE OR REPLACE FUNCTION into_fn() RETURNS INT AS 'SELECT 1 INTO t'"));
        assertEquals(udf,
            refusal("CREATE OR REPLACE FUNCTION into_tfn() RETURNS TABLE (a INT) AS 'SELECT 1 INTO t UNION SELECT 2'"));
        assertEquals(notAllowed(1, 6), refusal("CREATE OR REPLACE PROCEDURE into_proc() RETURNS INT LANGUAGE SQL"
            + " AS $$BEGIN SELECT 1 INTO :x UNION SELECT 2; RETURN 1; END$$"));
        assertEquals(notAllowed(2, 22), refusal("""
            CREATE OR REPLACE PROCEDURE into_proc_unquoted() RETURNS INT LANGUAGE SQL AS
            BEGIN
              LET r RESULTSET := (SELECT 1 INTO :x);
              RETURN 1;
            END"""));
        // A task's body waits for the task to run, created at the top level or by a block.
        engine.execute("CREATE OR REPLACE TASK into_task AS SELECT 1 INTO t UNION SELECT 2");
        engine.execute("DROP TASK IF EXISTS into_task");
        assertEquals("1", value("BEGIN CREATE OR REPLACE TASK rb_tk AS SELECT 1 INTO t UNION SELECT 2; RETURN 1; END;"));
        engine.execute("DROP TASK IF EXISTS rb_tk");
        // A view's query is the block's to judge.
        assertEquals(notAllowed(1, 53),
            refusal("BEGIN CREATE OR REPLACE VIEW rb_v2 AS SELECT 1 UNION SELECT 2 INTO t; RETURN 1; END;"));
    }

    @Test
    public void aBlocksSelectIntoTakesTopAndAll() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        assertEquals("1", value(block("SELECT TOP 1 a INTO :x FROM t ORDER BY a;")));
        assertEquals("1", value(block("SELECT ALL a INTO :x FROM t;")));
        assertEquals("1", value(block("SELECT DISTINCT TOP 1 a INTO :x FROM t;")));
        assertEquals("1", value(block("SELECT ALL TOP 1 a INTO :x FROM t;")));
        assertEquals("1", value(block("SELECT TOP 2 a INTO :x FROM t WHERE a > 0 ORDER BY a DESC;")));
        assertEquals("1", value(block("SELECT TOP 1 a INTO x FROM t;")));
        assertEquals("1", value(block("WITH c AS (SELECT 1 a) SELECT TOP 1 a INTO :x FROM c;")));
        assertEquals("2",
            value("DECLARE x INT; y INT; BEGIN SELECT TOP 1 a, a + 1 INTO :x, :y FROM t; RETURN y; END;"));
        assertEquals(invalid(42, "ZZ"), refusal(block("SELECT TOP 1 a INTO :zz FROM t;")));
        assertEquals(notAllowed(1, 21), refusal(block("SELECT TOP 1 1 INTO :x UNION SELECT 2;")));
        engine.execute("CREATE OR REPLACE PROCEDURE rb_top() RETURNS INT LANGUAGE SQL AS $$DECLARE x INT;"
            + " BEGIN SELECT TOP 1 a INTO :x FROM t ORDER BY a; RETURN x; END$$");
        try {
            assertEquals("1", value("CALL rb_top()"));
        } finally {
            engine.execute("DROP PROCEDURE IF EXISTS rb_top()");
        }
    }

    @Test
    public void aHierarchicalSelectIntoIsRefusedWhenItRuns() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        final String atRun = "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 21 : ";
        assertEquals(atRun + notAllowed(1, 0), refusal(block("SELECT a INTO :x FROM t CONNECT BY a = PRIOR a + 1;")));
        assertEquals(atRun + notAllowed(1, 0), refusal(
            block("SELECT a INTO :x FROM t START WITH a = 1 CONNECT BY a = PRIOR a + 1 ORDER BY 1;")));
        assertEquals(atRun + notAllowed(1, 0),
            refusal(block("SELECT a INTO :zz FROM t START WITH a = 1 CONNECT BY a = PRIOR a + 1;")));
        assertEquals(atRun + notAllowed(1, 23), refusal(
            block("WITH c AS (SELECT 1 a) SELECT a INTO :x FROM c CONNECT BY a = PRIOR a + 1;")));
        assertEquals("1", value("DECLARE x INT; BEGIN IF (1 = 2) THEN SELECT a INTO :x FROM t START WITH a = 1"
            + " CONNECT BY a = PRIOR a + 1; END IF; RETURN 1; END;"));
        // Beside a set operation the query is no statement of the block's, and is refused as the block compiles.
        assertEquals(notAllowed(1, 21),
            refusal(block("SELECT a INTO :x FROM t CONNECT BY a = PRIOR a + 1 UNION SELECT 2;")));
        engine.execute("CREATE OR REPLACE PROCEDURE rp_cb() RETURNS INT LANGUAGE SQL AS $$DECLARE x INT;"
            + " BEGIN SELECT a INTO :x FROM t CONNECT BY a = PRIOR a + 1; RETURN x; END$$");
        try {
            assertEquals(atRun + notAllowed(1, 0), refusal("CALL rp_cb()"));
        } finally {
            engine.execute("DROP PROCEDURE IF EXISTS rp_cb()");
        }
    }

    private static String width(final int position) {
        return """
            SQL compilation error: error line 1 at position %d
            Invalid character length: 0. Must be between 1 and 134,217,728.""".formatted(position);
    }

    @Test
    public void aBlockJudgesItsIntoClausesWithItsTypesInTheOrderWritten() {
        // An INTO clause standing where none may is judged where its INTO stands among the block's declared types,
        // integer literals, DECLARE sections and routine statements.
        assertEquals(width(25),
            refusal("DECLARE x INT; y VARCHAR(0); BEGIN SELECT 1 INTO :x UNION SELECT 2; RETURN x; END;"));
        assertEquals(notAllowed(1, 21), refusal(block("SELECT 1 INTO :x UNION SELECT 2; LET y VARCHAR(0) := 'a';")));
        assertEquals(width(39), refusal(block("SELECT 1::VARCHAR(0) INTO :x UNION SELECT 2;")));
        assertEquals(notAllowed(1, 21), refusal(block("SELECT 1 INTO :x UNION SELECT 1::VARCHAR(0);")));
        assertEquals("""
            SQL compilation error: Error line 1 at position 37
            Integer literal is out of representable range: 123456789012345678901234567890123456789012""",
            refusal(block("LET z NUMBER := 123456789012345678901234567890123456789012;"
                + " SELECT 1 INTO :x UNION SELECT 2;")));
        assertEquals(notAllowed(1, 21), refusal("DECLARE x INT; BEGIN SELECT 1 INTO :x UNION SELECT 2;"
            + " RETURN 123456789012345678901234567890123456789012; END;"));
        assertEquals(notAllowed(1, 21), refusal(block("SELECT 1 INTO :x UNION SELECT 2;"
            + " CREATE OR REPLACE FUNCTION ip_cobol() RETURNS INT LANGUAGE COBOL AS 'x';")));
        assertEquals(notAllowed(1, 21),
            refusal(block("SELECT 1 INTO :x UNION SELECT 2; DECLARE a INT; a INT; BEGIN RETURN 1; END;")));
        // Of two nested clauses, the one whose INTO comes first is refused, at its own SELECT.
        assertEquals(notAllowed(1, 36),
            refusal("DECLARE x INT; y INT; BEGIN SELECT (SELECT 1 INTO :y) INTO :x UNION SELECT 2; RETURN 1; END;"));
        assertEquals(width(54),
            refusal("DECLARE x INT; y INT; BEGIN SELECT (SELECT 1::VARCHAR(0) INTO :y) INTO :x; RETURN x; END;"));
        // A block a RESULTSET is filled from is judged with the block, on a branch that never runs too.
        assertEquals(notAllowed(1, 63), refusal("DECLARE x INT; BEGIN IF (FALSE) THEN LET r RESULTSET :="
            + " (BEGIN SELECT 1 INTO :x UNION SELECT 2; RETURN 1; END); END IF; RETURN 'ok'; END;"));
        // In a procedure's body a DECLARE item repeating a parameter waits for the INTO clause.
        assertEquals(notAllowed(1, 22), refusal("CREATE OR REPLACE PROCEDURE ip_param(p INT) RETURNS INT LANGUAGE SQL"
            + " AS $$ DECLARE p INT; BEGIN SELECT 1 INTO :p UNION SELECT 2; RETURN 1; END; $$"));
        // A task's body is the task's, compiled when the task runs.
        assertEquals(width(83), refusal("BEGIN CREATE OR REPLACE TASK iptk AS SELECT 1 INTO t UNION SELECT 2;"
            + " LET y VARCHAR(0) := 'a'; RETURN 1; END;"));
    }

    @Test
    public void aFunctionBodyClosingItsFrameIsJudgedForItsIntoClauses() {
        // The statement a body ends at a semicolon after closing its own frame stands in the frame's positions.
        assertEquals("Compilation of SQL UDF failed: " + notAllowed(1, 7),
            refusal("CREATE OR REPLACE FUNCTION ip_framed() RETURNS INT AS '1) + (SELECT 1 INTO t);'"));
        assertEquals("Compilation of SQL UDF failed: " + notAllowed(1, 9),
            refusal("CREATE OR REPLACE FUNCTION ip_framed() RETURNS INT AS '(1)) + (SELECT 2 INTO t);'"));
        assertEquals("Compilation of SQL UDF failed: " + notAllowed(1, 1),
            refusal("CREATE OR REPLACE FUNCTION ip_framed() RETURNS INT AS 'SELECT 1 INTO t);'"));
        assertEquals("Compilation of SQL UDF failed: " + notAllowed(1, 2),
            refusal("CREATE OR REPLACE FUNCTION ip_framed() RETURNS INT AS '(SELECT 1 INTO t)'"));
    }
}
