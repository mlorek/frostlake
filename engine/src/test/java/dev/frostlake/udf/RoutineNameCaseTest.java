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

package dev.frostlake.udf;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A function or procedure is named as any identifier is — an unquoted name upper-cased, a quoted one as
 * written — and every statement reaches it by that exact name. {@code SELECT mixedcase()} does not call
 * {@code "mixedCase"}, and neither DROP, DESCRIBE, COMMENT, ALTER, GRANT, GET_DDL, CALL nor a table function
 * finds it by another case. {@code "a"} and {@code A} are two routines, a refusal quotes a name only when it
 * needs quotes, and SHOW lists a schema's routines by name, then by argument types. Every cell is
 * live-verified.
 */
public class RoutineNameCaseTest extends BaseDatabaseTest {

    private static final String ERROR = "SQL compilation error:|";

    /** The first row's first cell, "no row", or the refusal on one line. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** One column of every row, joined with spaces. */
    private String column(final String sql, final int index) {
        final StringBuilder values = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            values.append(values.length() > 0 ? " " : "").append(row.getValue(index));
        }
        return values.toString();
    }

    /** One cell of a row. */
    private String cell(final String sql, final int row, final int index) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(row).getValue(index));
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    private String missing(final String kind, final String name) {
        return hinted(ERROR + kind + " 'TEST_DB.TEST_SCHEMA." + name + "' does not exist or not authorized.");
    }

    @Test
    public void aCallReachesAFunctionByItsExactName() {
        engine.execute("CREATE FUNCTION \"mixedCase\"() RETURNS VARCHAR AS $$ 'm' $$");
        engine.execute("CREATE FUNCTION \"namedCase\"(x INT) RETURNS INT AS $$ x $$");
        assertCells(new String[][] {
            {"SELECT \"mixedCase\"()", "m"},
            {"SELECT mixedcase()", ERROR + "Unknown function MIXEDCASE."},
            {"SELECT \"MIXEDCASE\"()", ERROR + "Unknown function MIXEDCASE."},
            {"SELECT test_db.test_schema.\"mixedCase\"()", "m"},
            {"SELECT test_db.test_schema.mixedCase()", ERROR + "Unknown user-defined function TEST_DB.TEST_SCHEMA.MIXEDCASE."},
            {"SELECT \"nosuchCase\"(), \"otherCase\"()", ERROR + "Unknown functions \"nosuchCase\", \"otherCase\"."},
            {"SELECT IDENTIFIER('\"mixedCase\"')()", "m"},
            {"SELECT IDENTIFIER('mixedCase')()", ERROR + "Unknown function MIXEDCASE."},
            {"SELECT \"mixedCase\"(*)", "m"},
            {"SELECT \"namedCase\"(x => 5)", "5"},
            {"SELECT namedcase(x => 5)", ERROR + "Unknown function NAMEDCASE."},
            {"CREATE FUNCTION badcaller() RETURNS VARCHAR AS $$ mixedcase() $$", ERROR + "Unknown function MIXEDCASE."},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x VARCHAR := \"mixedCase\"(); RETURN x; END; $$", "m"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x VARCHAR := mixedcase(); RETURN x; END; $$",
                "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : " + ERROR
                    + "Unknown function MIXEDCASE."},
        });
        engine.execute("CREATE FUNCTION caller() RETURNS VARCHAR AS $$ \"mixedCase\"() $$");
        assertEquals("m", answer("SELECT caller()"));
    }

    @Test
    public void namesDifferingInCaseAreTwoRoutines() {
        engine.execute("CREATE FUNCTION \"mixedCase\"() RETURNS VARCHAR AS $$ 'm' $$");
        engine.execute("CREATE OR REPLACE FUNCTION MIXEDCASE() RETURNS VARCHAR AS $$ 'up' $$");
        assertEquals("mup", answer("SELECT \"mixedCase\"() || MIXEDCASE()"));
        for (final String name : new String[] {"b", "A", "a", "B", "_z"}) {
            engine.execute("CREATE FUNCTION \"" + name + "\"() RETURNS VARCHAR AS $$ '" + name + "' $$");
        }
        assertEquals("A B MIXEDCASE _z a b mixedCase", column("SHOW USER FUNCTIONS", 1));
        assertEquals("aA", answer("SELECT \"a\"() || a()"));
        engine.execute("CREATE FUNCTION \"a b\"() RETURNS INT AS $$ 3 $$");
        assertCells(new String[][] {
            {"CREATE FUNCTION \"b\"() RETURNS INT AS $$ 3 $$", ERROR + "Object '\"b\"' already exists."},
            {"CREATE FUNCTION \"a b\"() RETURNS INT AS $$ 3 $$", ERROR + "Object '\"a b\"' already exists."},
            {"CREATE FUNCTION b() RETURNS INT AS $$ 3 $$", ERROR + "Object 'B' already exists."},
            {"ALTER FUNCTION \"b\"() RENAME TO \"A\"", ERROR + "Object 'A' already exists."},
            {"ALTER FUNCTION \"b\"() RENAME TO a", ERROR + "Object 'A' already exists."},
        });
    }

    @Test
    public void overloadsAreListedByTheirArgumentTypes() {
        final String[][] overloads = {
            {"f", "x VARCHAR", "VARCHAR", "varchar"}, {"f", "x INT", "NUMBER", "number"}, {"f", "", "", "none"},
            {"f", "x INT, y INT", "NUMBER, NUMBER", "number2"}, {"f", "x DATE", "DATE", "date"},
            {"\"f\"", "x BOOLEAN", "BOOLEAN", "quoted"}, {"f", "x BOOLEAN", "BOOLEAN", "boolean"},
        };
        for (final String[] overload : overloads) {
            engine.execute("CREATE FUNCTION " + overload[0] + "(" + overload[1] + ") RETURNS INT AS $$ 1 $$");
            engine.execute("COMMENT ON FUNCTION " + overload[0] + "(" + overload[2] + ") IS '" + overload[3] + "'");
        }
        assertEquals("none boolean date number number2 varchar quoted",
            column("SHOW USER FUNCTIONS LIKE 'f'", 9));
        assertEquals("F F F F F F f", column("SHOW USER FUNCTIONS LIKE 'f'", 1));
    }

    @Test
    public void aRoutineStatementNamesTheExactRoutine() {
        engine.execute("CREATE FUNCTION \"mixedCase\"() RETURNS VARCHAR AS $$ 'm' $$");
        engine.execute("CREATE PROCEDURE \"procCase\"() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'p'; END; $$");
        engine.execute("CREATE PROCEDURE \"a\"() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'a'; END; $$");
        engine.execute("CREATE FUNCTION f(x INT) RETURNS INT AS $$ 1 $$");
        engine.execute("CREATE FUNCTION f(x DATE) RETURNS INT AS $$ 2 $$");
        assertCells(new String[][] {
            {"DESCRIBE FUNCTION \"mixedCase\"()", "signature"},
            {"DESCRIBE FUNCTION mixedCase()", missing("Function", "MIXEDCASE")},
            {"DESCRIBE FUNCTION \"nosuchCase\"()", missing("Function", "\"nosuchCase\"")},
            {"DESCRIBE PROCEDURE test_schema.\"nosuchCase\"()", missing("Procedure", "\"nosuchCase\"")},
            {"DESCRIBE PROCEDURE proccase()", missing("Procedure", "PROCCASE")},
            {"DESCRIBE FUNCTION f(VARCHAR)", missing("Function", "F")},
            {"COMMENT ON FUNCTION mixedcase() IS 'x'", missing("Function", "MIXEDCASE")},
            {"COMMENT ON PROCEDURE proccase() IS 'x'", missing("Procedure", "PROCCASE")},
            {"ALTER FUNCTION mixedcase() SET COMMENT = 'x'", missing("Function", "MIXEDCASE")},
            {"ALTER PROCEDURE a() SET COMMENT = 'x'", missing("Procedure", "A")},
            {"GRANT USAGE ON FUNCTION mixedcase() TO ROLE PUBLIC", missing("Function", "MIXEDCASE")},
            {"GRANT USAGE ON PROCEDURE proccase() TO ROLE PUBLIC", missing("Procedure", "PROCCASE")},
            {"SHOW GRANTS ON FUNCTION mixedcase()", missing("Function", "MIXEDCASE")},
            {"SELECT GET_DDL('FUNCTION', 'mixedCase()')", ERROR + "Object 'mixedCase()' does not exist or not authorized."},
            {"SELECT GET_DDL('PROCEDURE', 'proccase()')", ERROR + "Object 'proccase()' does not exist or not authorized."},
            {"SELECT GET_DDL('FUNCTION', '\"mixedCase\"()')",
                "CREATE OR REPLACE FUNCTION \"mixedCase\"()\nRETURNS VARCHAR\nLANGUAGE SQL\nAS ' ''m'' ';"},
            {"DROP FUNCTION mixedcase()", missing("Function", "MIXEDCASE")},
            {"DROP PROCEDURE proccase()", missing("Procedure", "PROCCASE")},
            {"DROP FUNCTION f()", missing("Function", "F")},
        });
        assertEquals("(X DATE)", cell("DESCRIBE FUNCTION f(DATE)", 0, 1));
        assertEquals("(X NUMBER)", cell("DESCRIBE FUNCTION f(NUMBER)", 0, 1));
        assertEquals("TEST_DB.TEST_SCHEMA.\"mixedCase\"()", cell("SHOW GRANTS ON FUNCTION \"mixedCase\"()", 0, 3));
        engine.execute("ALTER FUNCTION f(DATE) SET COMMENT = 'd'");
        assertEquals("d user-defined function", column("SHOW USER FUNCTIONS LIKE 'F'", 9));
        engine.execute("ALTER FUNCTION \"mixedCase\"() RENAME TO \"otherCase\"");
        assertEquals("m", answer("SELECT \"otherCase\"()"));
        engine.execute("ALTER FUNCTION \"otherCase\"() RENAME TO otherCase");
        assertEquals("m", answer("SELECT othercase()"));
        engine.execute("DROP FUNCTION f(DATE)");
        assertEquals("1", answer("SELECT f(1)"));
    }

    @Test
    public void droppingOrReplacingAnOverloadLeavesItsSiblings() {
        engine.execute("CREATE FUNCTION g(x INT) RETURNS VARCHAR AS $$ 'int' $$");
        engine.execute("CREATE FUNCTION g(x VARCHAR) RETURNS VARCHAR AS $$ 'varchar' $$");
        engine.execute("DROP FUNCTION g(VARCHAR)");
        assertEquals("int", answer("SELECT g(1)"));
        engine.execute("CREATE FUNCTION h(x INT) RETURNS VARCHAR AS $$ 'int' $$");
        engine.execute("CREATE FUNCTION h(x VARCHAR) RETURNS VARCHAR AS $$ 'v1' $$");
        engine.execute("CREATE OR REPLACE FUNCTION h(x VARCHAR) RETURNS VARCHAR AS $$ 'v2' $$");
        assertEquals("int", answer("SELECT h(1)"));
        assertEquals("v2", answer("SELECT h('a')"));
    }

    @Test
    public void aCallReachesAProcedureByItsExactName() {
        engine.execute("CREATE PROCEDURE \"procCase\"() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'p'; END; $$");
        engine.execute("CREATE PROCEDURE \"a\"(x INT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'a'; END; $$");
        engine.execute("CREATE PROCEDURE \"A\"() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END; $$");
        engine.execute("CREATE PROCEDURE \"procTab\"() RETURNS TABLE(a INT) LANGUAGE SQL AS"
            + " $$ DECLARE r RESULTSET DEFAULT (SELECT 7 AS a); BEGIN RETURN TABLE(r); END; $$");
        engine.execute("CREATE FUNCTION \"tabCase\"() RETURNS TABLE(a INT) AS $$ SELECT 1 $$");
        assertCells(new String[][] {
            {"CALL \"procCase\"()", "p"},
            {"CALL proccase()", ERROR + "Unknown function PROCCASE."},
            {"CALL PROCCASE()", ERROR + "Unknown function PROCCASE."},
            {"CALL \"nosuchCase\"()", ERROR + "Unknown function \"nosuchCase\"."},
            {"CALL test_schema.\"nosuchCase\"()", ERROR + "Unknown user-defined function TEST_SCHEMA.\"nosuchCase\"."},
            {"CALL test_db.test_schema.\"nosuchCase\"()",
                ERROR + "Unknown user-defined function TEST_DB.TEST_SCHEMA.\"nosuchCase\"."},
            {"CALL \"a\"(1, 2)", "SQL compilation error: error line 0 at position -1|too many arguments for function [a(1, 2)] expected 1, got 2"},
            {"CALL \"a\"(1)", "a"},
            {"CALL a()", "A"},
            {"CREATE PROCEDURE \"procCase\"() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'p'; END; $$",
                ERROR + "Object '\"procCase\"' already exists."},
            {"EXECUTE IMMEDIATE $$ BEGIN CALL \"procCase\"(); RETURN 'ok'; END; $$", "ok"},
            {"EXECUTE IMMEDIATE $$ BEGIN CALL proccase(); RETURN 'ok'; END; $$",
                "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : " + ERROR + "Unknown function PROCCASE."},
            {"SELECT * FROM TABLE(\"procTab\"())", "7"},
            {"SELECT * FROM TABLE(proctab())", ERROR + "Unknown table function PROCTAB"},
            {"SELECT * FROM TABLE(\"tabCase\"())", "1"},
            {"SELECT * FROM TABLE(tabcase())", ERROR + "Unknown table function TABCASE"},
        });
        assertEquals("procCase", engine.executeQuery("CALL \"procCase\"()").getColumns().get(0).getName());
        assertEquals("A a procCase procTab", column("SHOW USER PROCEDURES", 1));
    }
}
