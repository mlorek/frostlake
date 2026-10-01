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
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A block's SELECT … INTO targets compile with the block (live-verified): a target naming no variable its statement
 * sees is refused before any of the block runs, at the target's name — "invalid identifier", or its own sentence for a
 * cursor or a RESULTSET — while a FOR loop's variable and a procedure's parameter may be assigned. A word standing
 * between the select item and INTO is the item's alias, so the INTO clause after it is the statement's own.
 */
public class SelectIntoTargetTest extends BaseDatabaseTest {

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

    @Test
    public void aTargetNamingNothingIsAnInvalidIdentifier() {
        assertEquals(invalid(1, 36, "ZZ"), refusal("DECLARE x INT; BEGIN SELECT 1 INTO :zz; RETURN x; END;"));
        assertEquals(invalid(1, 35, "ZZ"), refusal("DECLARE x INT; BEGIN SELECT 1 INTO zz; RETURN x; END;"));
        assertEquals(invalid(1, 20, "T"), refusal("BEGIN SELECT 1 INTO t; END;"));
        assertEquals(invalid(1, 21, "T"), refusal("BEGIN SELECT 1 INTO :t; END;"));
        assertEquals(invalid(1, 43, "ZZ"), refusal("DECLARE x INT; BEGIN SELECT 1, 2 INTO :x, :zz; RETURN x; END;"));
        assertEquals(invalid(1, 38, "ZZ"), refusal("DECLARE x INT; BEGIN SELECT 1, 2 INTO zz, x; RETURN x; END;"));
        assertEquals(invalid(1, 35, "\"zz\""), refusal("DECLARE x INT; BEGIN SELECT 1 INTO \"zz\"; RETURN x; END;"));
        assertEquals(invalid(1, 38, "X"), refusal("DECLARE \"x\" INT; BEGIN SELECT 1 INTO :x; RETURN 1; END;"));
        assertEquals(invalid(1, 36, "ZZ"),
            refusal("EXECUTE IMMEDIATE $$DECLARE x INT; BEGIN SELECT 1 INTO :zz; RETURN x; END;$$"));
    }

    @Test
    public void theTargetComesBeforeTheNamesOfItsQuery() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertEquals(invalid(1, 41, "ZZ"), refusal("DECLARE x INT; BEGIN SELECT nosuch INTO :zz FROM t; RETURN x; END;"));
        assertEquals(invalid(1, 36, "ZZ"),
            refusal("DECLARE x INT; BEGIN SELECT a INTO :zz FROM nosuch_table; RETURN x; END;"));
        assertEquals(invalid(1, 21, "ZZ"), refusal("BEGIN SELECT 1 INTO :zz FROM t WHERE nosuch = 1; END;"));
    }

    @Test
    public void aBindOrARepeatedTargetOfTheStatementSpeaksFirst() {
        assertEquals(invalid(1, 28, "NOSUCH"), refusal("DECLARE x INT; BEGIN SELECT :nosuch INTO :zz; RETURN x; END;"));
        assertEquals(invalid(1, 36, "NOSUCH"),
            refusal("DECLARE x INT; BEGIN SELECT 1 INTO :zz WHERE :nosuch = 1; RETURN x; END;"));
        assertEquals(at(1, 21, " Repeated variable with name 'ZZ' inside the INTO clause."),
            refusal("DECLARE x INT; BEGIN SELECT 1, 2 INTO :zz, :zz; RETURN x; END;"));
        assertEquals(at(1, 21, " Repeated variable with name 'X' inside the INTO clause."),
            refusal("DECLARE x INT; BEGIN SELECT 1, 2, 3 INTO :zz, :x, :x; RETURN x; END;"));
    }

    @Test
    public void theWholeBlockIsCompiledBeforeAnyOfItRuns() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertEquals(invalid(1, 52, "ZZ"),
            refusal("DECLARE x INT; BEGIN IF (1 = 2) THEN SELECT 1 INTO :zz; END IF; RETURN 5; END;"));
        assertEquals(invalid(1, 21, "Y"), refusal("BEGIN SELECT 1 INTO :y; LET y INT := 2; RETURN y; END;"));
        assertEquals(invalid(1, 69, "INNER_V"),
            refusal("DECLARE x INT; BEGIN BEGIN LET inner_v INT := 1; END; SELECT 1 INTO :inner_v; RETURN x; END;"));
        assertEquals(invalid(1, 80, "ZZ"), refusal(
            "DECLARE x INT; BEGIN SELECT 1 INTO :x; EXCEPTION WHEN OTHER THEN SELECT 2 INTO :zz; RETURN 0; END;"));
        assertEquals(invalid(5, 10, "ZZ"), refusal("""
            DECLARE x INT;
            BEGIN
              LET y INT := 1;
              SELECT 1
                INTO :zz;
              RETURN x;
            END;"""));
        assertEquals(invalid(1, 47, "ZZ"), refusal("BEGIN INSERT INTO t VALUES (9); SELECT 1 INTO :zz; END;"));
        assertEquals("0", value("SELECT COUNT(*) FROM t"));
    }

    @Test
    public void aCursorAResultsetOrAnExceptionIsNoTarget() {
        assertEquals(at(1, 52, " Invalid use of cursor 'C'."),
            refusal("DECLARE c CURSOR FOR SELECT 1; BEGIN SELECT 1 INTO :c; RETURN 1; END;"));
        assertEquals(at(1, 61, " Invalid use of resultset 'R'."),
            refusal("DECLARE r RESULTSET DEFAULT (SELECT 1); BEGIN SELECT 1 INTO :r; RETURN 1; END;"));
        assertEquals(invalid(1, 56, "E"),
            refusal("DECLARE e EXCEPTION (-20002, 'x'); BEGIN SELECT 1 INTO :e; RETURN 1; END;"));
    }

    @Test
    public void aVariableTheStatementSeesIsAssigned() {
        assertEquals("7", value("DECLARE x INT; BEGIN SELECT 7 INTO :X; RETURN x; END;"));
        assertEquals("1", value("BEGIN FOR i IN 1 TO 2 DO SELECT 5 INTO :i; END FOR; RETURN 1; END;"));
        assertEquals("1", value("BEGIN FOR i IN 1 TO 2 DO SELECT 5 INTO i; END FOR; RETURN 1; END;"));
        assertEquals("2", value(
            "BEGIN LET y INT := 1; SELECT 1/0 INTO :y; EXCEPTION WHEN OTHER THEN SELECT 2 INTO :y; RETURN y; END;"));
        engine.execute("CREATE OR REPLACE PROCEDURE into_param(a INT) RETURNS INT LANGUAGE SQL"
            + " AS $$BEGIN SELECT 5 INTO :a; RETURN a; END$$");
        assertEquals("5", value("CALL into_param(1)"));
    }

    @Test
    public void aProcedureIsCreatedAndRefusedWhenCalled() {
        engine.execute("CREATE OR REPLACE PROCEDURE into_missing(a INT) RETURNS INT LANGUAGE SQL"
            + " AS $$BEGIN SELECT 1 INTO :zz; RETURN 1; END$$");
        assertEquals(invalid(1, 21, "ZZ"), refusal("CALL into_missing(1)"));
        engine.execute("""
            CREATE OR REPLACE PROCEDURE into_missing_unquoted(a INT) RETURNS INT LANGUAGE SQL AS
            BEGIN
              SELECT 5 INTO :zz;
              RETURN a;
            END""");
        assertEquals(invalid(2, 17, "ZZ"), refusal("CALL into_missing_unquoted(1)"));
    }

    @Test
    public void aWordBeforeIntoIsTheItemsAlias() {
        assertEquals(invalid(1, 29, "T"), refusal("BEGIN SELECT 'foo' COPY INTO t FROM @s; END;"));
        assertEquals("foo", value("DECLARE t VARCHAR; BEGIN SELECT 'foo' COPY INTO t; RETURN t; END;"));
        assertEquals("foo", value("DECLARE t VARCHAR; BEGIN SELECT 'foo' MERGE INTO t; RETURN t; END;"));
        final String missingStage = refusal("DECLARE t INT; BEGIN SELECT 'foo' COPY INTO t FROM @s; RETURN t; END;");
        assertTrue(missingStage.startsWith("Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 21 : "
            + "SQL compilation error:\nStage 'TEST_DB.TEST_SCHEMA.S' does not exist or not authorized."), missingStage);
    }

    @Test
    public void aStatementRunIntoTheIntoClauseFailsWhereTheSelectIntoDoes() {
        assertEquals(syntax(3, 13, "USING") + "\n" + syntaxLine(3, 19, "u"), refusal("""
            BEGIN
            SELECT 'foo'
            MERGE INTO t USING u ON a = b WHEN MATCHED THEN DELETE;
            END;"""));
        assertEquals(syntax(1, 32, "USING") + "\n" + syntaxLine(1, 38, "u"),
            refusal("BEGIN SELECT 'foo' MERGE INTO t USING u ON a = b WHEN MATCHED THEN DELETE; RETURN 1; END;"));
        assertEquals(syntax(1, 31, "FILES") + "\n" + syntaxLine(1, 37, "="),
            refusal("BEGIN SELECT 'foo' COPY INTO t FILES = ('a'); END;"));
        assertEquals(syntax(3, 2, "MERGE") + "\n" + syntaxLine(3, 8, "INTO"), refusal("""
            BEGIN
              SELECT 1 AS a
              MERGE INTO t USING u ON a = b WHEN MATCHED THEN DELETE;
            END;"""));
    }

    private static String syntaxLine(final int line, final int position, final String token) {
        return "syntax error line " + line + " at position " + position + " unexpected '" + token + "'.";
    }

    private static String syntax(final int line, final int position, final String token) {
        return "SQL compilation error:\n" + syntaxLine(line, position, token);
    }
}
