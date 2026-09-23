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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The binds of the SQL a block runs compile with the block (live-verified): a {@code :name} no variable in its scope
 * declares is refused before any of the block runs, on a branch that never runs too, at the bind, its name folded as
 * an unquoted identifier is — and in the order of the statements, so an earlier statement's bind speaks before a later
 * statement's INTO target. A RESULTSET's query, CALL or DML statement binds as SQL does; an EXECUTE IMMEDIATE's text
 * is a scripting expression. A bind written with a quoted name is a syntax error at the name, anywhere.
 */
public class BlockBindCompilationTest extends BaseDatabaseTest {

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

    private static String at(final int line, final int position, final String detail) {
        return "SQL compilation error: error line " + line + " at position " + position + "\n" + detail;
    }

    private static String invalid(final int line, final int position, final String name) {
        return at(line, position, "invalid identifier '" + name + "'");
    }

    private static String syntax(final int position, final String token) {
        return "SQL compilation error:\nsyntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void aBindNothingDeclaresIsRefusedBeforeTheBlockRuns() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertEquals(invalid(1, 29, "NOSUCH"), refusal("BEGIN IF (1 = 2) THEN SELECT :nosuch; END IF; RETURN 1; END;"));
        assertEquals(invalid(1, 44, "NOSUCH"),
            refusal("BEGIN IF (1 = 2) THEN INSERT INTO t VALUES (:nosuch); END IF; RETURN 1; END;"));
        assertEquals(invalid(1, 39, "NOSUCH"), refusal("BEGIN INSERT INTO nosuch_table VALUES (:nosuch); END;"));
        assertEquals(invalid(1, 13, "NOSUCH"), refusal("BEGIN SELECT :nosuch FROM nosuch_table; END;"));
        assertEquals(invalid(1, 25, "NOSUCH"), refusal("BEGIN SELECT nosuch_col, :nosuch FROM t; END;"));
        assertEquals(invalid(1, 23, "NOSUCH"), refusal("BEGIN UPDATE t SET a = :nosuch; END;"));
        assertEquals(invalid(1, 30, "NOSUCH"), refusal("BEGIN DELETE FROM t WHERE a = :nosuch; END;"));
        assertEquals(invalid(1, 13, "NOSUCH"),
            refusal("BEGIN SELECT :nosuch; EXCEPTION WHEN OTHER THEN RETURN 'caught'; END;"));
        assertEquals(invalid(1, 13, "NOSUCH"), refusal("BEGIN SELECT :NoSuch; END;"));
        assertEquals(invalid(3, 12, "NOSUCH"), refusal("""
            BEGIN
              SELECT 1 FROM t
              WHERE a = :nosuch; END;"""));
        assertEquals(invalid(1, 45, "Y"),
            refusal("BEGIN LET x INT := 1; IF (x = 1) THEN SELECT :y; END IF; LET y INT := 2; RETURN y; END;"));
        assertEquals(invalid(1, 40, "X"), refusal("DECLARE \"x\" INT DEFAULT 1; BEGIN SELECT :x; RETURN 1; END;"));
        assertEquals(invalid(1, 40, "X"), refusal("BEGIN BEGIN LET x INT := 1; END; SELECT :x; END;"));
        assertEquals(invalid(1, 68, "I"),
            refusal("BEGIN FOR i IN 1 TO 2 DO INSERT INTO t VALUES (:i); END FOR; SELECT :i; END;"));
        assertEquals("0", value("SELECT COUNT(*) FROM t"));
    }

    @Test
    public void theStatementsAreJudgedInTheirOrder() {
        assertEquals(invalid(1, 47, "NOSUCH_PLAIN"),
            refusal("DECLARE x INT; BEGIN SELECT 1 INTO :zz; SELECT :nosuch_plain; RETURN x; END;"));
        assertEquals(invalid(1, 28, "NOSUCH_PLAIN"),
            refusal("DECLARE x INT; BEGIN SELECT :nosuch_plain; SELECT 1 INTO :zz; RETURN x; END;"));
        assertEquals(invalid(1, 13, "NOSUCH"), refusal("BEGIN SELECT :nosuch; SELECT 1, 2 INTO :x, :x; END;"));
        assertEquals(invalid(1, 23, "NOSUCH"), refusal("BEGIN SELECT 1; SELECT :nosuch; RETURN nosuch_ret; END;"));
        assertEquals(invalid(1, 13, "NOSUCH"), refusal("BEGIN SELECT :nosuch; LET q INT := nosuch_let; END;"));
        assertEquals(invalid(1, 13, "NOSUCH"), refusal("BEGIN SELECT :nosuch; OPEN nosuch_cur; END;"));
        assertEquals(invalid(1, 13, "NOSUCH"),
            refusal("BEGIN SELECT :nosuch; LET r RESULTSET := (SELECT :nosuch2); END;"));
        assertEquals(invalid(1, 33, "NOSUCH2"),
            refusal("BEGIN LET r RESULTSET := (SELECT :nosuch2); SELECT :nosuch; END;"));
        assertEquals(at(1, 13, "invalid identifier 'nosuch2'"), refusal("BEGIN RETURN :nosuch2; SELECT :nosuch; END;"));
        assertEquals(invalid(1, 13, "NOSUCH"), refusal("BEGIN SELECT :nosuch; CALL nosuch_proc(:nosuch2); END;"));
        assertEquals(invalid(1, 23, "NOSUCH2"), refusal("BEGIN CALL nosuch_proc(:nosuch2); SELECT :nosuch; END;"));
    }

    @Test
    public void aResultsetStatementBindsAsSql() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertEquals(invalid(1, 33, "NOSUCH"), refusal("BEGIN LET r RESULTSET := (SELECT :nosuch); RETURN 1; END;"));
        assertEquals(invalid(1, 36, "NOSUCH"),
            refusal("DECLARE r RESULTSET DEFAULT (SELECT :nosuch); BEGIN RETURN 1; END;"));
        assertEquals(invalid(1, 48, "NOSUCH"),
            refusal("BEGIN LET r RESULTSET := (INSERT INTO t VALUES (:nosuch)); RETURN 1; END;"));
        assertEquals(invalid(1, 43, "NOSUCH"),
            refusal("BEGIN LET r RESULTSET := (UPDATE t SET a = :nosuch); RETURN 1; END;"));
        assertEquals(invalid(1, 53, "NOSUCH"),
            refusal("DECLARE r RESULTSET DEFAULT (DELETE FROM t WHERE a = :nosuch); BEGIN RETURN 1; END;"));
        assertEquals(invalid(1, 43, "NOSUCH"),
            refusal("BEGIN LET r RESULTSET := (CALL nosuch_proc(:nosuch)); RETURN 1; END;"));
        assertEquals(at(1, 44, "invalid identifier 'nosuch'"),
            refusal("BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE :nosuch); RETURN 1; END;"));
        // Assigned a query, a RESULTSET binds as SQL too; a scalar assigned one binds as a scripting expression.
        assertEquals(invalid(1, 40, "NOSUCH"), refusal("DECLARE r RESULTSET; BEGIN r := (SELECT :nosuch); RETURN 1; END;"));
        assertEquals(at(1, 34, "invalid identifier 'nosuch'"),
            refusal("DECLARE x INT; BEGIN x := (SELECT :nosuch); RETURN x; END;"));
        assertEquals("0", value("SELECT COUNT(*) FROM t"));
    }

    @Test
    public void anExecuteImmediateTextIsAScriptingExpression() {
        assertEquals(invalid(1, 24, "NOSUCH"), refusal("BEGIN EXECUTE IMMEDIATE nosuch; END;"));
        assertEquals(at(1, 24, "invalid identifier 'nosuch'"), refusal("BEGIN EXECUTE IMMEDIATE :nosuch; END;"));
        assertEquals(at(1, 24, "invalid identifier 'nosuch'"), refusal("BEGIN EXECUTE IMMEDIATE :nosuch || 'x'; END;"));
    }

    @Test
    public void aDeclaredNameBinds() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertEquals("1",
            value("BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN INSERT INTO t VALUES (:sqlcode); RETURN 1; END;"));
        assertEquals("1", value("BEGIN FOR i IN 1 TO 2 DO BEGIN INSERT INTO t VALUES (:i); END; END FOR; RETURN 1; END;"));
        assertEquals("1", value("""
            DECLARE x INT DEFAULT 3;
            BEGIN
              UPDATE t SET a = :x WHERE 1 = 0;
              DELETE FROM t WHERE a = :x;
              MERGE INTO t USING (SELECT :x AS b) s ON t.a = s.b WHEN MATCHED THEN DELETE;
              RETURN 1;
            END;"""));
        assertEquals("1", value("DECLARE x INT DEFAULT 1; BEGIN SELECT :X, :x; RETURN 1; END;"));
        assertEquals("1", value("DECLARE \"X\" INT DEFAULT 1; BEGIN SELECT :x; RETURN 1; END;"));
        assertEquals("1", value("BEGIN SELECT :sqlrowcount, :sqlfound, :sqlnotfound; RETURN 1; END;"));
        assertEquals("3", value("SELECT COUNT(*) FROM t"));
    }

    @Test
    public void aProcedureBodyBindsItsParameters() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE PROCEDURE bind_param(a INT) RETURNS INT LANGUAGE SQL AS "
            + "$$BEGIN INSERT INTO t VALUES (:a); RETURN 1; END$$");
        assertEquals("1", value("CALL bind_param(5)"));
        assertEquals("5", value("SELECT a FROM t"));
        engine.execute("CREATE OR REPLACE PROCEDURE bind_missing() RETURNS INT LANGUAGE SQL AS "
            + "$$BEGIN SELECT :nosuch; RETURN 1; END$$");
        assertEquals(invalid(1, 13, "NOSUCH"), refusal("CALL bind_missing()"));
    }

    @Test
    public void aBindWithAQuotedNameIsASyntaxError() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertEquals(syntax(14, "\"x\""), refusal("BEGIN RETURN :\"x\"; END;"));
        assertEquals(syntax(41, "\"x\""), refusal("DECLARE \"x\" INT DEFAULT 1; BEGIN RETURN :\"x\"; END;"));
        assertEquals(syntax(20, "\"x\""), refusal("BEGIN LET y INT := :\"x\"; RETURN y; END;"));
        assertEquals(syntax(39, "\"X\""), refusal("DECLARE x INT DEFAULT 1; BEGIN SELECT :\"X\"; END;"));
        assertEquals(syntax(54, "\"X\""), refusal("DECLARE x INT DEFAULT 1; BEGIN INSERT INTO t VALUES (:\"X\"); END;"));
        assertEquals(syntax(8, "\"x\""), refusal("SELECT :\"x\""));
        assertEquals(syntax(16, "\"x\""), refusal("SELECT 1 WHERE :\"x\" = 1"));
        assertEquals(syntax(14, "\"x\""), refusal(
            "CREATE OR REPLACE PROCEDURE quoted_bind() RETURNS INT LANGUAGE SQL AS $$BEGIN RETURN :\"x\"; END$$"));
        assertEquals("1", value("SELECT PARSE_JSON('{\"a b\": 1}'):\"a b\""));
    }
}
