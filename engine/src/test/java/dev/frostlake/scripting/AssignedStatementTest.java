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
 * A statement written in parentheses as an assignment's value fills a RESULTSET, and no other target (all
 * live-verified): a cursor is never assigned and an exception is no variable, both refused while the block compiles;
 * a scalar refuses a command such as SHOW, DESCRIBE, DROP or an EXECUTE IMMEDIATE while the block compiles, and any
 * other statement — DML, CREATE TABLE, SET — when the assignment runs, as the syntax error of the query the account
 * evaluates it as. Nothing of the statement runs.
 */
public class AssignedStatementTest extends BaseDatabaseTest {

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

    private static String notPermitted(final int position, final String name) {
        return """
            SQL compilation error: error line 1 at position %d
             Assignment to variable '%s' is not permitted.""".formatted(position, name);
    }

    private static String invalid(final int position, final String name) {
        return """
            SQL compilation error: error line 1 at position %d
            invalid identifier '%s'""".formatted(position, name);
    }

    private static String invalidValue(final String named) {
        return """
            SQL compilation error:
            Invalid expression value (%s) for assignment.""".formatted(named);
    }

    /** The uncaught EXPRESSION_ERROR of a statement assigned to a scalar, as far as its first syntax line. */
    private static String expressionError(final int line, final int position, final int queryPosition,
                                          final String token) {
        return """
            Uncaught exception of type 'EXPRESSION_ERROR' on line %d at position %d : SQL compilation error:
            syntax error line 1 at position %d unexpected '%s'.""".formatted(line, position, queryPosition, token);
    }

    private void assertStartsWith(final String expected, final String actual) {
        assertTrue(actual.startsWith(expected), actual);
    }

    @Test
    public void aCursorIsNeverAssigned() {
        engine.execute("CREATE OR REPLACE TABLE ts (a INT)");
        engine.execute("INSERT INTO ts VALUES (1)");
        assertEquals(notPermitted(39, "C"),
            refusal("DECLARE c CURSOR FOR SELECT 1; BEGIN c := (DELETE FROM ts); RETURN 1; END;"));
        assertEquals(notPermitted(39, "C"), refusal("DECLARE c CURSOR FOR SELECT 1; BEGIN c := 5; RETURN 1; END;"));
        assertEquals(notPermitted(39, "C"),
            refusal("DECLARE c CURSOR FOR SELECT 1; BEGIN c := (SELECT 1); RETURN 1; END;"));
        assertEquals(notPermitted(35, "C"), refusal("BEGIN LET c CURSOR FOR SELECT 1; c := 5; RETURN 1; END;"));
        // The target is judged before the value.
        assertEquals(notPermitted(39, "C"),
            refusal("DECLARE c CURSOR FOR SELECT 1; BEGIN c := nosuch; RETURN 1; END;"));
        assertEquals(notPermitted(55, "C"), refusal(
            "DECLARE c CURSOR FOR SELECT 1; BEGIN IF (1 = 2) THEN c := (CALL SYSTEM$TYPEOF(1)); END IF; RETURN 1; END;"));
        assertEquals(notPermitted(54, "C"),
            refusal("DECLARE c CURSOR FOR SELECT 1; x INT; BEGIN x := 1; c := 2; RETURN x; END;"));
        assertEquals("1", value("SELECT COUNT(*) FROM ts"));
    }

    @Test
    public void anExceptionIsNoVariable() {
        engine.execute("CREATE OR REPLACE TABLE ts (a INT)");
        engine.execute("INSERT INTO ts VALUES (1)");
        assertEquals(invalid(43, "E"), refusal("DECLARE e EXCEPTION (-20002, 'x'); BEGIN e := 5; RETURN 1; END;"));
        assertEquals(invalid(43, "E"),
            refusal("DECLARE e EXCEPTION (-20002, 'x'); BEGIN e := (DELETE FROM ts); RETURN 1; END;"));
        assertEquals(invalid(59, "E"), refusal(
            "DECLARE e EXCEPTION (-20002, 'x'); BEGIN IF (1 = 2) THEN e := (SHOW TABLES); END IF; RETURN 1; END;"));
        assertEquals("1", value("SELECT COUNT(*) FROM ts"));
    }

    @Test
    public void aScalarRefusesACommandWhileTheBlockCompiles() {
        engine.execute("CREATE OR REPLACE TABLE ts (a INT)");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        // The account names the statement in its own rendering, which only the sentence around it shares.
        final String[] commands = {"SHOW TABLES", "TRUNCATE TABLE ts", "DESC TABLE ts", "DROP VIEW IF EXISTS v1",
            "CREATE SCHEMA s1", "ALTER SESSION UNSET QUERY_TAG", "LIST @~", "BEGIN TRANSACTION", "UNDROP TABLE t7",
            "COMMENT ON TABLE ts IS 'c'", "CREATE SEQUENCE sq1", "ALTER TABLE ts SWAP WITH t"};
        for (final String command : commands) {
            final String refused = refusal("DECLARE x INT; BEGIN IF (1 = 2) THEN x := (" + command + "); END IF;"
                + " RETURN 1; END;");
            assertStartsWith("SQL compilation error:\nInvalid expression value (", refused);
            assertTrue(refused.endsWith(") for assignment."), refused);
        }
        assertEquals(invalidValue("?SqlExecuteImmediateDynamic?"), refusal(
            "DECLARE x INT; BEGIN IF (1 = 2) THEN x := (EXECUTE IMMEDIATE 'SELECT 1'); END IF; RETURN 1; END;"));
        assertEquals(invalidValue("?SqlPsmBlock?"),
            refusal("DECLARE x INT; BEGIN x := (BEGIN RETURN 1; END); RETURN x; END;"));
        assertEquals(invalidValue("?SqlPsmBlock?"),
            refusal("DECLARE x INT; BEGIN IF (1 = 2) THEN x := (BEGIN RETURN 1; END); END IF; RETURN 1; END;"));
    }

    @Test
    public void aScalarRefusesAnyOtherStatementWhenTheAssignmentRuns() {
        engine.execute("CREATE OR REPLACE TABLE ts (a INT)");
        engine.execute("INSERT INTO ts VALUES (1)");
        // On a branch that never runs, the block runs.
        final String[] statements = {"DELETE FROM ts", "INSERT ALL INTO ts SELECT 1", "ALTER TABLE ts ADD COLUMN b INT",
            "CREATE VIEW v1 AS SELECT 1 AS a", "CREATE STAGE st1", "UNSET v1", "CREATE OR REPLACE TABLE t8 (a INT)",
            "COPY INTO ts FROM @~/nosuch_dir"};
        for (final String statement : statements) {
            assertEquals("1", value("DECLARE x INT; BEGIN IF (1 = 2) THEN x := (" + statement + "); END IF;"
                + " RETURN 1; END;"));
        }
        // Run, it is the syntax error of SELECT * FROM (<statement>), placed at its first word.
        assertStartsWith(expressionError(1, 31, 15, "DELETE"),
            refusal("DECLARE x VARIANT; BEGIN x := (DELETE FROM ts); RETURN x; END;"));
        assertStartsWith(expressionError(1, 27, 22, "INTO"),
            refusal("DECLARE x INT; BEGIN x := (INSERT INTO ts VALUES (3)); RETURN x; END;"));
        assertStartsWith(expressionError(1, 27, 15, "UPDATE"),
            refusal("DECLARE x INT; BEGIN x := (UPDATE ts SET a = a WHERE 1 = 0); RETURN x; END;"));
        assertStartsWith(expressionError(1, 27, 21, "INTO"), refusal("DECLARE x INT; BEGIN x := (MERGE INTO ts"
            + " USING (SELECT 1 a) s ON ts.a = s.a WHEN MATCHED THEN DELETE); RETURN x; END;"));
        assertStartsWith(expressionError(1, 31, 15, "SET"),
            refusal("DECLARE x VARIANT; BEGIN x := (SET v1 = 1); RETURN x; END;"));
        assertStartsWith(expressionError(1, 27, 21, "v1"), refusal("DECLARE x INT; BEGIN x := (UNSET v1); RETURN x; END;"));
        assertStartsWith(expressionError(1, 27, 22, "OVERWRITE"),
            refusal("DECLARE x INT; BEGIN x := (INSERT OVERWRITE INTO ts VALUES (1)); RETURN x; END;"));
        assertStartsWith(expressionError(1, 27, 20, "INTO"),
            refusal("DECLARE x INT; BEGIN x := (COPY INTO ts FROM @~/nosuch_dir); RETURN x; END;"));
        assertStartsWith(expressionError(1, 43, 15, "DELETE"),
            refusal("DECLARE x INT; BEGIN IF (1 = 1) THEN x := (DELETE FROM ts); END IF; RETURN x; END;"));
        assertStartsWith(expressionError(3, 8, 15, "CREATE"), refusal("""
            DECLARE x INT;
            BEGIN
              x := (CREATE TABLE t9 (a INT));
              RETURN x;
            END;"""));
        // A FOR loop's record and an untyped declaration are scalars too.
        assertStartsWith(expressionError(1, 66, 15, "DELETE"), refusal("DECLARE c CURSOR FOR SELECT 1 AS a; BEGIN"
            + " FOR rec IN c DO rec := (DELETE FROM ts); END FOR; RETURN 1; END;"));
        assertStartsWith(expressionError(1, 33, 15, "DELETE"),
            refusal("DECLARE x DEFAULT 1; BEGIN x := (DELETE FROM ts); RETURN x; END;"));
        // Nothing ran.
        assertEquals("1", value("SELECT COUNT(*) FROM ts"));
        assertEquals("0", value("SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'T9'"));
    }

    @Test
    public void theRefusalIsAHandledCompilationError() {
        engine.execute("CREATE OR REPLACE TABLE ts (a INT)");
        assertStartsWith("""
            1003 42000 SQL compilation error:
            syntax error line 1 at position 15 unexpected 'DELETE'.""", value("DECLARE x INT; BEGIN x := (DELETE FROM ts);"
            + " RETURN x; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END;"));
        engine.execute("CREATE OR REPLACE PROCEDURE rp_param(x INT) RETURNS INT LANGUAGE SQL"
            + " AS $$BEGIN x := (DELETE FROM ts); RETURN 1; END$$");
        try {
            assertStartsWith(expressionError(1, 12, 15, "DELETE"), refusal("CALL rp_param(1)"));
        } finally {
            engine.execute("DROP PROCEDURE IF EXISTS rp_param(INT)");
        }
    }

    @Test
    public void aResultsetTakesTheStatement() {
        engine.execute("CREATE OR REPLACE TABLE ts (a INT)");
        engine.execute("INSERT INTO ts VALUES (1)");
        final ResultSet rs = engine.executeQuery("DECLARE r RESULTSET; BEGIN BEGIN r := (DELETE FROM ts WHERE 1 = 0);"
            + " END; RETURN TABLE(r); END;");
        assertEquals("number of rows deleted", rs.getColumns().get(0).getName());
        assertEquals("0", String.valueOf(rs.getRows().get(0).getValue(0)));
        // The RESULTSET of an inner block shadows the scalar of the same name.
        assertEquals("1", value("DECLARE x INT; BEGIN BEGIN LET x RESULTSET := (SELECT 1);"
            + " x := (DELETE FROM ts WHERE 1 = 0); END; RETURN 1; END;"));
    }
}
