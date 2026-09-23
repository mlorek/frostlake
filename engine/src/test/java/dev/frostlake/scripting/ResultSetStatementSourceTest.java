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
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A RESULTSET is filled from any statement the account runs as SQL, declared, LET or assigned alike, and holds what
 * the statement answers where it stands — a DML statement's counts, a SHOW or DESCRIBE listing, a DDL statement's
 * status, a CALL of a SYSTEM$ function's one row — while an EXECUTE IMMEDIATE's USING list names variables only: a
 * literal, a session variable or an expression there is a syntax error at it (all live-verified).
 */
public class ResultSetStatementSourceTest extends BaseDatabaseTest {

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

    /** A refusal while the block runs: the uncaught STATEMENT_ERROR at {@code at}, around the statement's own. */
    private static String uncaught(final int at, final String refusal) {
        return "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position " + at + " : " + refusal;
    }

    /** A refusal the account raises while a block compiles, at a name the argument misuses. */
    private static String misused(final int position, final String sentence) {
        return """
            SQL compilation error: error line 1 at position %d
             %s""".formatted(position, sentence);
    }

    @Test
    public void aDmlStatementFillsTheResultsetWithItsCounts() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertEquals("5", value("BEGIN LET r RESULTSET := (INSERT INTO t VALUES (2)); RETURN 5; END;"));
        assertEquals("1", value("SELECT COUNT(*) FROM t WHERE a = 2"));
        ResultSet rs = engine.executeQuery(
            "BEGIN LET r RESULTSET := (INSERT INTO t VALUES (3), (4)); RETURN TABLE(r); END;");
        assertEquals("number of rows inserted", rs.getColumns().get(0).getName());
        assertEquals("2", String.valueOf(rs.getRows().get(0).getValue(0)));
        rs = engine.executeQuery("BEGIN LET r RESULTSET := (UPDATE t SET a = a + 10 WHERE a = 3); RETURN TABLE(r); END;");
        assertEquals("number of rows updated", rs.getColumns().get(0).getName());
        assertEquals("1", String.valueOf(rs.getRows().get(0).getValue(0)));
        rs = engine.executeQuery("BEGIN LET r RESULTSET := (DELETE FROM t WHERE a = 13); RETURN TABLE(r); END;");
        assertEquals("number of rows deleted", rs.getColumns().get(0).getName());
        assertEquals("1", String.valueOf(rs.getRows().get(0).getValue(0)));
        assertEquals("1", value("BEGIN LET r RESULTSET := (MERGE INTO t USING (SELECT 4 AS a) s ON t.a = s.a"
            + " WHEN MATCHED THEN UPDATE SET a = 40); RETURN TABLE(r); END;"));
        assertEquals("1", value("DECLARE res RESULTSET DEFAULT (INSERT INTO t VALUES (1)); BEGIN RETURN TABLE(res); END;"));
        assertEquals("1", value("DECLARE res RESULTSET; BEGIN res := (UPDATE t SET a = 0 WHERE a = 1); RETURN TABLE(res); END;"));
        engine.execute("CREATE OR REPLACE TABLE tv (v VARCHAR(10), a ARRAY, o OBJECT, b BINARY)");
        assertEquals("ok", value("DECLARE s VARCHAR(10); res RESULTSET; BEGIN res := (INSERT INTO tv (v, a, o)"
            + " VALUES (:s, :s, :s)); RETURN 'ok'; END;"));
    }

    /** The one result column's declared type, with the parameters both engines report, and its value. */
    private String typedValue(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        final DataType type = rs.getColumns().get(0).getDataType();
        final String typed = type instanceof NumericType
            ? "NUMBER(" + ((NumericType) type).getPrecision() + "," + ((NumericType) type).getScale() + ")"
            : type instanceof StringType ? "VARCHAR(" + ((StringType) type).getMaxLength() + ")" : String.valueOf(type);
        return typed + " " + rs.getRows().get(0).getValue(0);
    }

    @Test
    public void aCountGridFieldKeepsItsCountType() {
        engine.execute("CREATE OR REPLACE TABLE tp (a INT, b VARCHAR)");
        assertEquals("NUMBER(19,0) 1", typedValue("BEGIN LET r RESULTSET := (INSERT INTO tp VALUES (1, 'x'));"
            + " FOR row_var IN r DO RETURN row_var.\"number of rows inserted\"; END FOR; RETURN 0; END;"));
        assertEquals("NUMBER(19,0) 0", typedValue("BEGIN LET r RESULTSET := (UPDATE tp SET b = 'y');"
            + " FOR row_var IN r DO RETURN row_var.\"number of multi-joined rows updated\"; END FOR; RETURN 0; END;"));
        assertEquals("NUMBER(20,0) 2", typedValue("BEGIN LET r RESULTSET := (INSERT INTO tp VALUES (2, 'z'));"
            + " FOR row_var IN r DO RETURN row_var.\"number of rows inserted\" + 1; END FOR; RETURN 0; END;"));
        assertEquals("VARCHAR(134217728) one", typedValue("BEGIN LET r RESULTSET := (INSERT INTO tp VALUES (3, 'w'));"
            + " FOR row_var IN r DO IF (row_var.\"number of rows inserted\" = 1) THEN RETURN 'one'; END IF; END FOR;"
            + " RETURN 'none'; END;"));
        // A listing's text field reads as any text does.
        assertEquals("VARCHAR(134217728) TP", typedValue("BEGIN LET r RESULTSET := (SHOW TABLES LIKE 'TP');"
            + " FOR row_var IN r DO RETURN row_var.\"name\"; END FOR; RETURN 0; END;"));
    }

    @Test
    public void aListingFillsTheResultsetWithItsRows() {
        engine.execute("CREATE OR REPLACE TABLE tp (a INT, b VARCHAR)");
        ResultSet rs = engine.executeQuery("BEGIN LET r RESULTSET := (SHOW TABLES LIKE 'TP'); RETURN TABLE(r); END;");
        assertEquals("TP", cell(rs, rs.getRows().get(0), "name"));
        rs = engine.executeQuery("DECLARE res RESULTSET; BEGIN res := (SHOW TABLES LIKE 'TP'); RETURN TABLE(res); END;");
        assertEquals(1, rs.getRowCount());
        rs = engine.executeQuery("DECLARE res RESULTSET DEFAULT (SHOW TABLES LIKE 'TP'); BEGIN RETURN TABLE(res); END;");
        assertEquals(1, rs.getRowCount());
        rs = engine.executeQuery("BEGIN LET r RESULTSET := (DESCRIBE TABLE tp); RETURN TABLE(r); END;");
        assertEquals(2, rs.getRowCount());
        assertEquals("A", cell(rs, rs.getRows().get(0), "name"));
        rs = engine.executeQuery("BEGIN LET r RESULTSET := (DESC TABLE tp); RETURN TABLE(r); END;");
        assertEquals(2, rs.getRowCount());
        // The listing's own fault, and nothing after it.
        assertEquals(syntax(49, "y"), refusal("BEGIN LET r RESULTSET := (SHOW TABLES IN DATASET y); RETURN 1; END;"));
        assertEquals(syntax(56, "y"),
            refusal("DECLARE r RESULTSET; BEGIN r := (SHOW TABLES IN DATASET y); RETURN 1; END;"));
        assertEquals(syntax(52, "y"),
            refusal("DECLARE r RESULTSET DEFAULT (SHOW TABLES IN DATASET y); BEGIN RETURN 1; END;"));
    }

    @Test
    public void aDdlStatementFillsTheResultsetWithItsStatus() {
        engine.execute("CREATE OR REPLACE TABLE tp (a INT)");
        assertEquals("Table TQ successfully created.",
            value("BEGIN LET r RESULTSET := (CREATE TABLE tq (a INT)); RETURN TABLE(r); END;"));
        assertEquals("TQ successfully dropped.",
            value("BEGIN LET r RESULTSET := (DROP TABLE IF EXISTS tq); RETURN TABLE(r); END;"));
        assertEquals("Statement executed successfully.",
            value("BEGIN LET r RESULTSET := (TRUNCATE TABLE tp); RETURN TABLE(r); END;"));
        final String values = refusal("BEGIN LET r RESULTSET := (VALUES (1)); RETURN TABLE(r); END;");
        assertTrue(values.startsWith(syntax(26, "VALUES")), values);
    }

    @Test
    public void aSystemFunctionIsCalled() {
        // An account answers a CALL its result cache already holds with an empty result, so the cache is off here.
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        try {
            callsSystemFunctions();
        } finally {
            engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
        }
    }

    private void callsSystemFunctions() {
        ResultSet rs = engine.executeQuery("CALL SYSTEM$TYPEOF(1)");
        assertEquals("SYSTEM$TYPEOF", rs.getColumns().get(0).getName());
        assertEquals("NUMBER(1,0)[SB1]", String.valueOf(rs.getRows().get(0).getValue(0)));
        rs = engine.executeQuery("call system$typeof(1)");
        assertEquals("SYSTEM$TYPEOF", rs.getColumns().get(0).getName());
        assertEquals("waited 0 seconds", value("CALL SYSTEM$WAIT(0)"));
        assertEquals("waited 1 milliseconds", value("CALL SYSTEM$WAIT(1, 'MILLISECONDS')"));
        assertEquals("waited 2 milliseconds", value("SELECT SYSTEM$WAIT(2, 'MILLISECONDS')"));
        assertEquals("waited 0 minutes", value("SELECT SYSTEM$WAIT(0, 'MINUTES')"));
        rs = engine.executeQuery("BEGIN LET rs RESULTSET := (CALL SYSTEM$WAIT(0)); RETURN TABLE(rs); END;");
        assertEquals("SYSTEM$WAIT", rs.getColumns().get(0).getName());
        assertEquals("waited 0 seconds", String.valueOf(rs.getRows().get(0).getValue(0)));
        assertEquals("NUMBER(1,0)[SB1]",
            value("DECLARE res RESULTSET DEFAULT (CALL SYSTEM$TYPEOF(1)); BEGIN RETURN TABLE(res); END;"));
        assertEquals("NUMBER(1,0)[SB1]",
            value("DECLARE res RESULTSET; BEGIN res := (CALL SYSTEM$TYPEOF(1)); RETURN TABLE(res); END;"));
        assertEquals("1", value("BEGIN CALL SYSTEM$WAIT(0); RETURN 1; END;"));
        assertEquals("""
            Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 26 : SQL compilation error:
            Unknown function SYSTEM$NOSUCH.""",
            refusal("BEGIN LET r RESULTSET := (CALL SYSTEM$NOSUCH(1)); RETURN TABLE(r); END;"));
    }

    @Test
    public void aSystemCallIsJudgedInItsOwnText() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        try {
            judgesSystemCalls();
        } finally {
            engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
        }
    }

    private void judgesSystemCalls() {
        // A name is placed in the CALL's own text, on the line it stands on.
        assertEquals(invalid(19, "NOSUCH"), refusal("CALL SYSTEM$TYPEOF(nosuch)"));
        assertEquals(invalid(27, "NOSUCH"), refusal("CALL SYSTEM$TYPEOF((SELECT nosuch))"));
        assertEquals("""
            SQL compilation error: error line 2 at position 2
            invalid identifier 'NOSUCH'""", refusal("""
            CALL SYSTEM$TYPEOF(
              nosuch)"""));
        // The count of arguments has no place.
        assertEquals("""
            SQL compilation error: error line 0 at position -1
            not enough arguments for function [SYSTEM$TYPEOF()], expected 1, got 0""", refusal("CALL SYSTEM$TYPEOF()"));
        assertEquals("""
            SQL compilation error: error line 0 at position -1
            too many arguments for function [SYSTEM$TYPEOF(1, 2, 3)] expected 1, got 3""",
            refusal("CALL SYSTEM$TYPEOF(1, 2, 3)"));
        assertEquals("""
            SQL compilation error: error line 0 at position -1
            too many arguments for function [SYSTEM$TYPEOF(1, 2)] expected 1, got 2""", refusal("CALL system$typeof(1, 2)"));
        assertEquals("NUMBER(7,6)[SB4]", value("CALL SYSTEM$TYPEOF(1/0)"));
        // In a block the call is judged when it runs, in its own text; only a bind is judged with the block.
        assertEquals("1", value("BEGIN IF (1 = 2) THEN CALL SYSTEM$TYPEOF(nosuch); END IF; RETURN 1; END;"));
        assertEquals("1", value("BEGIN IF (1 = 2) THEN CALL SYSTEM$TYPEOF(); END IF; RETURN 1; END;"));
        assertEquals(uncaught(6, invalid(19, "NOSUCH")), refusal("BEGIN CALL SYSTEM$TYPEOF(nosuch); RETURN 1; END;"));
        assertEquals(uncaught(6, """
            SQL compilation error: error line 0 at position -1
            not enough arguments for function [SYSTEM$TYPEOF()], expected 1, got 0"""),
            refusal("BEGIN CALL SYSTEM$TYPEOF(); RETURN 1; END;"));
        assertEquals("""
            Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 2 : SQL compilation error: error line 2 \
            at position 4
            invalid identifier 'NOSUCH'""", refusal("""
            BEGIN
              CALL SYSTEM$TYPEOF(
                nosuch);
              RETURN 1;
            END;"""));
        assertEquals(invalid(25, "NOSUCH"), refusal("BEGIN CALL SYSTEM$TYPEOF(:nosuch); RETURN 1; END;"));
        assertEquals(uncaught(42, invalid(19, "V")),
            refusal("BEGIN LET v INT := 7; LET r RESULTSET := (CALL SYSTEM$TYPEOF(v)); RETURN TABLE(r); END;"));
        assertEquals("NUMBER(38,0)[SB1]",
            value("BEGIN LET v INT := 7; LET r RESULTSET := (CALL SYSTEM$TYPEOF(:v)); RETURN TABLE(r); END;"));
    }

    @Test
    public void aBlockFillsTheResultsetWithWhatItReturns() {
        engine.execute("CREATE OR REPLACE TABLE ts (a INT)");
        assertEquals("4", value("BEGIN LET r RESULTSET := (DECLARE z INT DEFAULT 4; BEGIN RETURN z; END);"
            + " RETURN TABLE(r); END;"));
        assertEquals("3", value("DECLARE r RESULTSET; BEGIN r := (DECLARE z INT DEFAULT 3; BEGIN RETURN z; END);"
            + " RETURN TABLE(r); END;"));
        assertEquals("a", value("DECLARE r RESULTSET DEFAULT (BEGIN RETURN 'a'; END); BEGIN RETURN TABLE(r); END;"));
        assertEquals("1", value("BEGIN LET r RESULTSET := (BEGIN RETURN 1; END); RETURN 1; END;"));
        assertEquals("h", value("BEGIN LET r RESULTSET := (BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN RETURN 'h'; END);"
            + " RETURN TABLE(r); END;"));
        final ResultSet rs = engine.executeQuery("BEGIN LET r RESULTSET := (BEGIN LET q RESULTSET :="
            + " (SELECT 1 AS a UNION ALL SELECT 2); RETURN TABLE(q); END); RETURN TABLE(r); END;");
        assertEquals("A", rs.getColumns().get(0).getName());
        assertEquals(2, rs.getRowCount());
        // The block runs where the RESULTSET is filled, and one that returns nothing holds NULL.
        assertEquals("null", value("BEGIN LET r RESULTSET := (BEGIN INSERT INTO ts VALUES (20); END); RETURN TABLE(r); END;"));
        assertEquals("1", value("SELECT COUNT(*) FROM ts WHERE a = 20"));
        // It compiles when it runs, as a block of its own: a fault is placed in its own text, inside the statement's.
        assertEquals(uncaught(26, invalid(13, "NOSUCH")),
            refusal("BEGIN LET r RESULTSET := (BEGIN RETURN nosuch; END); RETURN 1; END;"));
        assertEquals(uncaught(26, misused(22, "Variable with name 'Y' declared twice.")),
            refusal("BEGIN LET r RESULTSET := (BEGIN LET y INT := 1; LET y INT := 2; RETURN y; END); RETURN 1; END;"));
        assertEquals("1", value("BEGIN IF (1 = 2) THEN LET r RESULTSET := (BEGIN RETURN nosuch; END); END IF; RETURN 1; END;"));
        assertEquals("1", value("BEGIN IF (1 = 2) THEN LET r RESULTSET := (BEGIN LET y INT := 1; LET y INT := 2;"
            + " RETURN y; END); END IF; RETURN 1; END;"));
        assertEquals("1", value("BEGIN IF (1 = 2) THEN LET r RESULTSET := (BEGIN SELECT 1 INTO :zz; RETURN 1; END);"
            + " END IF; RETURN 1; END;"));
        assertEquals("1", value("BEGIN IF (1 = 2) THEN LET r RESULTSET := (BEGIN RETURN ?; END); END IF; RETURN 1; END;"));
        assertEquals("3", value("BEGIN LET r RESULTSET := (BEGIN LET c CURSOR FOR SELECT 1; RETURN 1; END);"
            + " LET c CURSOR FOR SELECT 2; RETURN 3; END;"));
        // The block ends at its END: a semicolon after it is a syntax error.
        assertEquals(syntax(45, ";"), refusal("BEGIN LET r RESULTSET := (BEGIN RETURN 1; END;); RETURN 1; END;"));
    }

    @Test
    public void aParenthesizedStatementEndsAtItsParenthesis() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertEquals(syntax(37, ";"), refusal("BEGIN LET r RESULTSET := (SHOW TABLES;); RETURN 1; END;"));
        assertEquals(syntax(50, ";"), refusal("BEGIN LET r RESULTSET := (INSERT INTO t VALUES (1);); RETURN 1; END;"));
        assertEquals(syntax(32, ";"), refusal("BEGIN LET r RESULTSET := (COMMIT;); RETURN 1; END;"));
        assertEquals(syntax(47, ";"), refusal("BEGIN LET r RESULTSET := (CALL SYSTEM$TYPEOF(1);); RETURN 1; END;"));
        assertEquals(syntax(54, ";"), refusal("BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE 'SELECT 1';); RETURN 1; END;"));
        assertEquals(syntax(44, ";"), refusal("DECLARE r RESULTSET; BEGIN r := (SHOW TABLES;); RETURN 1; END;"));
        assertEquals(syntax(57, ";"), refusal("DECLARE r RESULTSET; BEGIN r := (INSERT INTO t VALUES (2);); RETURN 1; END;"));
        assertEquals(syntax(40, ";"), refusal("DECLARE r RESULTSET DEFAULT (SHOW TABLES;); BEGIN RETURN 1; END;"));
        assertEquals(syntax(38, ";"), refusal("DECLARE x INT; BEGIN x := (SHOW TABLES;); RETURN 1; END;"));
        assertEquals(syntax(43, ";"),
            refusal("BEGIN LET r RESULTSET := (BEGIN TRANSACTION;); ROLLBACK; RETURN 1; END;"));
        assertEquals(syntax(36, ";"), refusal("BEGIN LET r RESULTSET := (BEGIN WORK;); ROLLBACK; RETURN 1; END;"));
        // Judged with the block, before any name and on a branch that never runs; a procedure's at CREATE.
        assertEquals(syntax(37, ";"), refusal("BEGIN LET r RESULTSET := (SHOW TABLES;); RETURN nosuch; END;"));
        assertEquals(syntax(53, ";"),
            refusal("BEGIN IF (1 = 2) THEN LET r RESULTSET := (SHOW TABLES;); END IF; RETURN 1; END;"));
        assertEquals(syntax(37, ";"), refusal("CREATE OR REPLACE PROCEDURE rp_semi() RETURNS INT LANGUAGE SQL"
            + " AS $$BEGIN LET r RESULTSET := (SHOW TABLES;); RETURN 1; END$$"));
        assertEquals("""
            SQL compilation error:
            syntax error line 3 at position 2 unexpected ';'.""", refusal("""
            BEGIN
              LET r RESULTSET := (SHOW TABLES
              ;);
              RETURN 1;
            END;"""));
        assertEquals("0", value("SELECT COUNT(*) FROM t"));
        // BEGIN alone in the parentheses starts a transaction, as BEGIN; does.
        assertEquals("1", value("BEGIN LET r RESULTSET := (BEGIN); ROLLBACK; RETURN 1; END;"));
        assertEquals("1", value("BEGIN LET r RESULTSET := (begin ); ROLLBACK; RETURN 1; END;"));
        assertEquals("1", value("DECLARE r RESULTSET DEFAULT (BEGIN); BEGIN ROLLBACK; RETURN 1; END;"));
        assertEquals("1", value("BEGIN LET r RESULTSET := (BEGIN); LET s RESULTSET := (COMMIT); RETURN 1; END;"));
        assertEquals(syntax(31, ";"), refusal("BEGIN LET r RESULTSET := (BEGIN;); RETURN 1; END;"));
    }

    @Test
    public void aStatementSourceSetsTheRowCountAsItWouldStandingAlone() {
        engine.execute("CREATE OR REPLACE TABLE ts (a INT)");
        engine.execute("INSERT INTO ts VALUES (1)");
        assertEquals("1", value("DECLARE r RESULTSET; BEGIN r := (DELETE FROM ts); RETURN SQLROWCOUNT; END;"));
        assertEquals("2", value("BEGIN LET r RESULTSET := (INSERT INTO ts VALUES (5), (6)); RETURN SQLROWCOUNT; END;"));
        assertEquals("false",
            value("BEGIN LET r RESULTSET := (INSERT INTO ts VALUES (5), (6)); RETURN SQLNOTFOUND; END;"));
        assertEquals("found", value("BEGIN LET r RESULTSET := (INSERT INTO ts VALUES (1));"
            + " IF (SQLFOUND) THEN RETURN 'found'; END IF; RETURN 'not found'; END;"));
        assertEquals("false", value("BEGIN LET r RESULTSET := (UPDATE ts SET a = a WHERE 1 = 0); RETURN SQLFOUND; END;"));
        assertEquals("3", value("DECLARE r RESULTSET DEFAULT (INSERT INTO ts VALUES (15), (16), (17));"
            + " BEGIN RETURN SQLROWCOUNT; END;"));
        assertEquals("1", value("BEGIN LET r RESULTSET := (INSERT INTO ts VALUES (14));"
            + " LET s RESULTSET := (DELETE FROM ts WHERE a = 14); RETURN SQLROWCOUNT; END;"));
        assertEquals("2", value("BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE 'INSERT INTO ts VALUES (8), (9)');"
            + " RETURN SQLROWCOUNT; END;"));
        // Any other statement leaves no count, whatever ran before it.
        assertEquals("null", value("BEGIN LET r RESULTSET := (CREATE TABLE tq3 (a INT)); RETURN SQLROWCOUNT; END;"));
        assertEquals("null",
            value("BEGIN INSERT INTO ts VALUES (7); LET r RESULTSET := (SELECT 1); RETURN SQLROWCOUNT; END;"));
        assertEquals("null", value("BEGIN INSERT INTO ts VALUES (7); LET r RESULTSET := (EXECUTE IMMEDIATE 'SELECT 1');"
            + " RETURN SQLROWCOUNT; END;"));
        assertEquals("null", value("DECLARE r RESULTSET; BEGIN INSERT INTO ts VALUES (10); r := (SHOW TABLES);"
            + " RETURN SQLROWCOUNT; END;"));
        assertEquals("null",
            value("BEGIN INSERT INTO ts VALUES (13); LET r RESULTSET := (COMMIT); RETURN SQLROWCOUNT; END;"));
        assertEquals("null", value("BEGIN LET r RESULTSET := (BEGIN INSERT INTO ts VALUES (30); END);"
            + " RETURN SQLROWCOUNT; END;"));
        engine.execute("CREATE OR REPLACE PROCEDURE rb_scalar() RETURNS INT LANGUAGE SQL AS $$BEGIN RETURN 42; END$$");
        try {
            assertEquals("null", value("BEGIN INSERT INTO ts VALUES (12); LET r RESULTSET := (CALL rb_scalar());"
                + " RETURN SQLROWCOUNT; END;"));
        } finally {
            engine.execute("DROP PROCEDURE IF EXISTS rb_scalar()");
        }
    }

    @Test
    public void theWaitRoundsItsAmountAndReadsItsUnitAsSpelled() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        try {
            waitsAsWritten();
        } finally {
            engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
        }
    }

    private static String notANumber(final String text) {
        return "SQL compilation error:\nargument 0 to function SqlIdentifier{qualifierNames=[], identifierName=SYSTEM$WAIT}"
            + " needs to be constant, found 'TO_NUMBER('" + text + "', 18, 0)'";
    }

    private static String unknownUnit(final String unit) {
        return """
            SQL compilation error: error line %s at position {1}

            Invalid unit of time '{2}'.
            Use one of DAYS, HOURS, MINUTES, SECONDS, MILLISECONDS, MICROSECONDS, NANOSECONDS.""".formatted(unit);
    }

    private void waitsAsWritten() {
        assertEquals("waited 1 milliseconds", value("SELECT SYSTEM$WAIT('1', 'MILLISECONDS')"));
        assertEquals("waited 1 milliseconds", value("SELECT SYSTEM$WAIT(' 1 ', 'MILLISECONDS')"));
        assertEquals("waited 2 milliseconds", value("SELECT SYSTEM$WAIT('1.5', 'MILLISECONDS')"));
        assertEquals("waited 3 milliseconds", value("SELECT SYSTEM$WAIT('2.5', 'MILLISECONDS')"));
        assertEquals("waited -2 seconds", value("SELECT SYSTEM$WAIT('-1.5')"));
        assertEquals("waited 2 milliseconds", value("SELECT SYSTEM$WAIT(1.5::FLOAT, 'MILLISECONDS')"));
        assertEquals("waited 5 microseconds", value("SELECT SYSTEM$WAIT(5, 'MICROSECONDS')"));
        assertEquals("waited 5 nanoseconds", value("SELECT SYSTEM$WAIT(5, 'NANOSECONDS')"));
        assertEquals("waited 0 days", value("SELECT SYSTEM$WAIT(0, 'DAYS')"));
        assertEquals("waited 0 milliseconds", value("SELECT SYSTEM$WAIT(0, 'MILLI' || 'SECONDS')"));
        assertEquals("waited 1 milliseconds", value("CALL SYSTEM$WAIT('1', 'MILLISECONDS')"));
        // A negative amount answers at once, however large.
        assertEquals("waited -1 hours", value("SELECT SYSTEM$WAIT(-1, 'HOURS')"));
        assertEquals("waited -100000000000 hours", value("SELECT SYSTEM$WAIT(-100000000000, 'HOURS')"));
        // The amount's text is read first, then a NULL, then the unit.
        assertEquals(notANumber("abc"), refusal("SELECT SYSTEM$WAIT('abc', 'foo')"));
        assertEquals(notANumber("x"), refusal("SELECT SYSTEM$WAIT('x', NULL)"));
        assertEquals(notANumber("1,000"), refusal("SELECT SYSTEM$WAIT('1,000', 'MILLISECONDS')"));
        assertEquals("inputs may not be null", refusal("SELECT SYSTEM$WAIT(NULL)"));
        assertEquals("inputs may not be null", refusal("SELECT SYSTEM$WAIT(0, NULL)"));
        assertEquals("inputs may not be null", refusal("SELECT SYSTEM$WAIT(NULL, 'foo')"));
        assertEquals(unknownUnit("seconds"), refusal("SELECT SYSTEM$WAIT(0, 'seconds')"));
        assertEquals(unknownUnit(" Milliseconds "), refusal("SELECT SYSTEM$WAIT(1, ' Milliseconds ')"));
        assertEquals(unknownUnit("seconds"), refusal("CALL SYSTEM$WAIT(0, 'seconds')"));
    }

    @Test
    public void usingNamesVariablesOnly() {
        assertEquals(syntax(62, "5"),
            refusal("BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE 'SELECT ?' USING (5)); RETURN TABLE(r); END;"));
        assertEquals(syntax(62, "'a'"),
            refusal("BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE 'SELECT ?' USING ('a')); RETURN TABLE(r); END;"));
        assertEquals(syntax(62, "NULL"),
            refusal("BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE 'SELECT ?' USING (NULL)); RETURN TABLE(r); END;"));
        assertEquals(syntax(42, "5"), refusal("BEGIN EXECUTE IMMEDIATE 'SELECT ?' USING (5); RETURN 1; END;"));
        assertEquals(syntax(67, "-"),
            refusal("DECLARE x INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'SELECT ?' USING (-5); RETURN 1; END;"));
        assertEquals(syntax(67, ")"),
            refusal("DECLARE x INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'SELECT ?' USING (); RETURN 1; END;"));
        assertEquals(syntax(57, "$v"),
            refusal("DECLARE x INT; BEGIN EXECUTE IMMEDIATE 'SELECT ?' USING ($v); RETURN 1; END;"));
        assertEquals(syntax(36, "5"), refusal("EXECUTE IMMEDIATE 'SELECT ?' USING (5)"));
        assertEquals(syntax(36, "$v"), refusal("EXECUTE IMMEDIATE 'SELECT ?' USING ($v)"));
        assertEquals(syntax(37, "."), refusal("EXECUTE IMMEDIATE 'SELECT ?' USING (x.y)"));
        assertEquals("SQL compilation error:\nUnsupported statement type 'EXECUTE'.",
            refusal("EXECUTE IMMEDIATE 'SELECT ?' USING (x)"));
        final String expression = refusal(
            "DECLARE x INT DEFAULT 5; BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE 'SELECT ?' USING (x + 1)); RETURN TABLE(r); END;");
        assertTrue(expression.startsWith(syntax(89, "+")), expression);
    }

    @Test
    public void aUsingNameCompilesWithTheBlock() {
        assertEquals("5", value(
            "DECLARE x INT DEFAULT 5; BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE 'SELECT ?' USING (x)); RETURN TABLE(r); END;"));
        assertEquals("3", value("DECLARE x INT DEFAULT 3; BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE 'SELECT ?' USING (x));"
            + " LET c CURSOR FOR r; OPEN c; FETCH c INTO x; RETURN x; END;"));
        assertEquals("1", value("DECLARE x INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'SELECT ?' USING (x, x); RETURN 1; END;"));
        assertEquals(invalid(67, "NOSUCH"),
            refusal("DECLARE x INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'SELECT ?' USING (nosuch); RETURN 1; END;"));
        assertEquals(invalid(67, "\"x\""),
            refusal("DECLARE x INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'SELECT ?' USING (\"x\"); RETURN 1; END;"));
        assertEquals(invalid(67, "TRUE"),
            refusal("DECLARE x INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'SELECT ?' USING (TRUE); RETURN 1; END;"));
        assertEquals(invalid(57, "FALSE"),
            refusal("DECLARE x INT; BEGIN EXECUTE IMMEDIATE 'SELECT ?' USING (FALSE); RETURN 1; END;"));
        assertEquals(invalid(67, "CURRENT_DATE"),
            refusal("DECLARE x INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'SELECT ?' USING (CURRENT_DATE); RETURN 1; END;"));
        assertEquals(invalid(73, "NOSUCH_USING"), refusal(
            "DECLARE x INT; BEGIN IF (1 = 2) THEN EXECUTE IMMEDIATE 'SELECT ?' USING (nosuch_using); END IF; RETURN 1; END;"));
        assertEquals(invalid(28, "NOSUCH_RET"), refusal(
            "DECLARE x INT; BEGIN RETURN nosuch_ret; EXECUTE IMMEDIATE 'SELECT ?' USING (nosuch_using); END;"));
        engine.execute("CREATE OR REPLACE PROCEDURE using_missing() RETURNS INT LANGUAGE SQL"
            + " AS $$BEGIN EXECUTE IMMEDIATE 'SELECT ?' USING (nosuch); RETURN 1; END$$");
        assertEquals(invalid(42, "NOSUCH"), refusal("CALL using_missing()"));
    }

    @Test
    public void aUsingNameIsJudgedByWhatItNames() {
        // The status variables bind NULL outside a handler; SQLSTATE and SQLERRM name nothing there.
        assertEquals("1", value("BEGIN EXECUTE IMMEDIATE 'SELECT ?' USING (sqlcode); RETURN 1; END;"));
        assertEquals("null",
            value("BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE 'SELECT ?' USING (sqlcode)); RETURN TABLE(r); END;"));
        assertEquals("null",
            value("BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE 'SELECT ?' USING (sqlrowcount)); RETURN TABLE(r); END;"));
        assertEquals("null",
            value("BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE 'SELECT ?' USING (sqlnotfound)); RETURN TABLE(r); END;"));
        assertEquals("null",
            value("BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE 'SELECT ?' USING (activity_count)); RETURN TABLE(r); END;"));
        assertEquals(invalid(42, "SQLSTATE"), refusal("BEGIN EXECUTE IMMEDIATE 'SELECT ?' USING (sqlstate); RETURN 1; END;"));
        assertEquals(invalid(42, "SQLERRM"), refusal("BEGIN EXECUTE IMMEDIATE 'SELECT ?' USING (sqlerrm); RETURN 1; END;"));
        // Inside a handler, and a block nested in one, they bind the caught error.
        assertEquals("100051", value("BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN LET r RESULTSET :="
            + " (EXECUTE IMMEDIATE 'SELECT ?' USING (sqlcode)); RETURN TABLE(r); END;"));
        assertEquals("22012", value("BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN LET r RESULTSET :="
            + " (EXECUTE IMMEDIATE 'SELECT ?' USING (sqlstate)); RETURN TABLE(r); END;"));
        assertEquals("Division by zero", value("BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN BEGIN LET r RESULTSET :="
            + " (EXECUTE IMMEDIATE 'SELECT ?' USING (sqlerrm)); RETURN TABLE(r); END; END;"));
        assertEquals("1",
            value("BEGIN RETURN 1; EXCEPTION WHEN OTHER THEN EXECUTE IMMEDIATE 'SELECT ?' USING (sqlerrm); RETURN 2; END;"));
        // A cursor or a RESULTSET is misused as a value, and an exception is no variable: all refused while the block
        // compiles, on a branch that never runs too.
        assertEquals(misused(73, "Invalid use of cursor 'C'."),
            refusal("DECLARE c CURSOR FOR SELECT 1; BEGIN EXECUTE IMMEDIATE 'SELECT ?' USING (c); RETURN 1; END;"));
        assertEquals(misused(89, "Invalid use of cursor 'C'."), refusal("DECLARE c CURSOR FOR SELECT 1;"
            + " BEGIN IF (1 = 2) THEN EXECUTE IMMEDIATE 'SELECT ?' USING (c); END IF; RETURN 1; END;"));
        assertEquals(misused(85, "Invalid use of cursor 'C'."), refusal("BEGIN IF (1 = 2) THEN LET c CURSOR FOR SELECT 1;"
            + " EXECUTE IMMEDIATE 'SELECT ?' USING (c); END IF; RETURN 1; END;"));
        assertEquals(misused(89, "Invalid use of cursor 'C'."), refusal("BEGIN LET c CURSOR FOR SELECT 1;"
            + " LET r RESULTSET := (EXECUTE IMMEDIATE 'SELECT ?' USING (c)); RETURN 1; END;"));
        assertEquals(misused(73, "Invalid use of resultset 'R'."), refusal(
            "BEGIN LET r RESULTSET := (SELECT 1); EXECUTE IMMEDIATE 'SELECT ?' USING (r); RETURN 1; END;"));
        assertEquals(misused(63, "Invalid use of resultset 'R'."),
            refusal("DECLARE r RESULTSET; BEGIN EXECUTE IMMEDIATE 'SELECT ?' USING (r); RETURN 1; END;"));
        assertEquals(misused(82, "Invalid use of resultset 'R'."), refusal(
            "DECLARE r RESULTSET DEFAULT (SELECT 1); BEGIN EXECUTE IMMEDIATE 'SELECT ?' USING (r); RETURN 1; END;"));
        assertEquals(invalid(77, "E"),
            refusal("DECLARE e EXCEPTION (-20002, 'x'); BEGIN EXECUTE IMMEDIATE 'SELECT ?' USING (e); RETURN 1; END;"));
        assertEquals("6", value("DECLARE e EXCEPTION (-20002, 'x'); e INT DEFAULT 6; BEGIN LET r RESULTSET :="
            + " (EXECUTE IMMEDIATE 'SELECT ?' USING (e)); RETURN TABLE(r); END;"));
        // A FOR loop's record binds nothing: its placeholder is refused unset when the text runs.
        assertEquals(uncaught(58, """
            SQL compilation error: error line 1 at position 7
            Bind variable ? not set."""), refusal("DECLARE c CURSOR FOR SELECT 1 AS a; BEGIN FOR rec IN c DO"
            + " EXECUTE IMMEDIATE 'SELECT ?' USING (rec); END FOR; RETURN 1; END;"));
        assertEquals(uncaught(75, """
            SQL compilation error: error line 1 at position 10
            Bind variable ? not set."""), refusal("DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT 1 AS a; BEGIN FOR rec IN c"
            + " DO EXECUTE IMMEDIATE 'SELECT ?, ?' USING (x, rec); END FOR; RETURN 1; END;"));
        // A loop counter and a procedure's parameter bind their values.
        assertEquals("3", value("BEGIN FOR i IN 3 TO 3 DO LET r RESULTSET := (EXECUTE IMMEDIATE 'SELECT ?' USING (i));"
            + " RETURN TABLE(r); END FOR; END;"));
        engine.execute("CREATE OR REPLACE PROCEDURE rp_using(p INT) RETURNS TABLE () LANGUAGE SQL"
            + " AS $$BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE 'SELECT ?' USING (p)); RETURN TABLE(r); END$$");
        try {
            assertEquals("4", value("CALL rp_using(4)"));
        } finally {
            engine.execute("DROP PROCEDURE IF EXISTS rp_using(INT)");
        }
    }

    @Test
    public void aUsingNameWaitsWithTheBlocksOtherBareNames() {
        // A USING name the block cannot resolve is refused with the block's other bare names, in their order.
        assertEquals(invalid(13, "NOSUCH_RET"),
            refusal("BEGIN RETURN nosuch_ret; EXECUTE IMMEDIATE 'SELECT ?' USING (sqlstate); END;"));
        assertEquals(invalid(34, "NOSUCH_RET"),
            refusal("DECLARE e EXCEPTION; BEGIN RETURN nosuch_ret; EXECUTE IMMEDIATE 'SELECT ?' USING (e); END;"));
        assertEquals(invalid(25, "NOSUCH_COND"), refusal("DECLARE x INT; BEGIN IF (nosuch_cond) THEN RETURN 1; END IF;"
            + " EXECUTE IMMEDIATE 'SELECT ?' USING (nosuch_using); END;"));
        assertEquals(invalid(57, "NOSUCH_USING"), refusal(
            "DECLARE x INT; BEGIN EXECUTE IMMEDIATE 'SELECT ?' USING (nosuch_using); RETURN nosuch_ret; END;"));
        // A LET's names come first, and a bind is judged before any bare name.
        assertEquals(invalid(85, "NOSUCH_LET"), refusal(
            "DECLARE x INT; BEGIN EXECUTE IMMEDIATE 'SELECT ?' USING (nosuch_using); LET y INT := nosuch_let; END;"));
        assertEquals(invalid(79, "nosuch_bind"), refusal(
            "DECLARE x INT; BEGIN EXECUTE IMMEDIATE 'SELECT ?' USING (nosuch_using); RETURN :nosuch_bind; END;"));
        // A cursor or a RESULTSET is refused where the compile meets it, ahead of a bare name written before it.
        assertEquals(misused(92, "Invalid use of cursor 'C'."), refusal(
            "DECLARE c CURSOR FOR SELECT 1; BEGIN RETURN nosuch_ret; EXECUTE IMMEDIATE 'SELECT ?' USING (c); END;"));
        assertEquals(misused(82, "Invalid use of resultset 'R'."),
            refusal("DECLARE r RESULTSET; BEGIN RETURN nosuch_ret; EXECUTE IMMEDIATE 'SELECT ?' USING (r); END;"));
    }

    @Test
    public void openStillTakesExpressions() {
        assertEquals("1", value("DECLARE c CURSOR FOR SELECT ?; BEGIN OPEN c USING (5); RETURN 1; END;"));
        assertEquals("1", value("DECLARE x INT DEFAULT 5; c CURSOR FOR SELECT ?; BEGIN OPEN c USING (x + 1); RETURN 1; END;"));
        assertEquals("5", value("DECLARE x INT DEFAULT 5; c CURSOR FOR SELECT ?; BEGIN OPEN c USING (x);"
            + " FETCH c INTO x; RETURN x; END;"));
    }
}
