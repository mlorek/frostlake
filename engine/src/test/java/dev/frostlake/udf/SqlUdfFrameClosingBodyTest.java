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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A SQL UDF body is read inside a frame of parentheses of its own, as a statement a semicolon ends. A body that
 * closes that frame itself and then writes a semicolon holds only what stands before the semicolon: nothing after
 * it is read, the function is created, and a call answers what the frame holds — grouped by the frame, so
 * {@code (1)) + 1;} answers 2. The statement is compiled at CREATE like any other body: one that stops before its
 * expression does is refused at the semicolon, a query clause after the frame's closing parenthesis at its keyword,
 * and a call reaching back to the routine through such a body is a cycle. Every cell is live-verified.
 */
public class SqlUdfFrameClosingBodyTest extends BaseDatabaseTest {

    private static final String REFUSED = "Compilation of SQL UDF failed: SQL compilation error:|";

    /** "created", or the refusal on one line. */
    private String create(final String sql) {
        try {
            engine.execute(sql);
            return "created";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The first row's cells joined with '|', or the refusal on one line. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                final StringBuilder cells = new StringBuilder();
                for (int i = 0; i < row.getValues().size(); i++) {
                    cells.append(i > 0 ? "|" : "").append(row.getValue(i));
                }
                return cells.toString();
            }
            return "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String syntax(final String line, final String position, final String token) {
        return REFUSED + "syntax error line " + line + " at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void aBodyEndingItsFrameAtASemicolonAnswersWhatTheFrameHolds() {
        final String[][] cells = {
            {"CREATE FUNCTION f1() RETURNS INT AS $$1);garbage here$$", "SELECT f1()", "1"},
            {"CREATE FUNCTION f2() RETURNS INT AS $$SELECT ABS(1));$$", "SELECT f2()", "1"},
            {"CREATE FUNCTION f4() RETURNS INT AS $$1); x$$", "SELECT f4()", "1"},
            {"CREATE FUNCTION f5() RETURNS INT AS $$(1)) + 1;$$", "SELECT f5()", "2"},
            {"CREATE FUNCTION f6() RETURNS INT AS $$1) ; ; x$$", "SELECT f6()", "1"},
            {"CREATE FUNCTION f7() RETURNS INT AS $$1)\n;$$", "SELECT f7()", "1"},
            {"CREATE FUNCTION f8() RETURNS INT AS $$(SELECT 1));$$", "SELECT f8()", "1"},
            {"CREATE FUNCTION f9(x INT) RETURNS INT AS $$x + 1); garbage$$", "SELECT f9(5)", "6"},
            {"CREATE FUNCTION f10(x INT) RETURNS INT AS $$x) * 2;$$", "SELECT f10(5)", "10"},
            {"CREATE FUNCTION f11(x INT) RETURNS INT AS $$ (x + 1)) * 2 ; nonsense$$", "SELECT f11(5)", "12"},
            {"CREATE FUNCTION f18() RETURNS INT AS $$1);$$", "SELECT f18()", "1"},
            {"CREATE FUNCTION f19() RETURNS INT AS $$SELECT 2) ;$$", "SELECT f19()", "2"},
            {"CREATE FUNCTION f21() RETURNS INT AS $$1) ; SELECT 'garbage' FROM nowhere$$", "SELECT f21()", "1"},
            {"CREATE FUNCTION g1() RETURNS VARCHAR AS $$'a;b');$$", "SELECT g1()", "a;b"},
            {"CREATE FUNCTION g2() RETURNS VARCHAR AS $$'x' || ';' ) ; tail$$", "SELECT g2()", "x;"},
            {"CREATE FUNCTION g3() RETURNS INT AS $$1) -- c ;\n; tail$$", "SELECT g3()", "1"},
            {"CREATE FUNCTION g4() RETURNS INT AS $$1) /* ; */ + 2; tail$$", "SELECT g4(), SYSTEM$TYPEOF(g4())",
                "3|NUMBER(2,0)[SB1]"},
            {"CREATE FUNCTION g7(x INT) RETURNS INT AS $$ABS(x)) ;$$", "SELECT g7(-3), SYSTEM$TYPEOF(g7(-3))",
                "3|NUMBER(2,0)[SB1]"},
            {"CREATE FUNCTION g10() RETURNS INT AS $$(SELECT 1)) ; ; $$", "SELECT g10()", "1"},
            {"CREATE FUNCTION g15() RETURNS INT AS $$1) ;\ngarbage$$", "SELECT g15()", "1"},
        };
        for (final String[] cell : cells) {
            assertEquals("created", create(cell[0]), cell[0]);
            assertEquals(cell[2], answer(cell[1]), cell[1]);
        }
    }

    @Test
    public void aQueryBodyWithOneClosingParenthesisTooManyRunsWhatItsFrameHolds() {
        assertEquals("created", create("CREATE FUNCTION f3(value OBJECT) RETURNS ARRAY AS $$ SELECT ARRAY_SORT("
            + "ARRAY_DISTINCT(ARRAY_CONSTRUCT_COMPACT(IFF(IS_OBJECT(value:a), 'X', null)))));$$"));
        assertEquals("X", answer("SELECT ARRAY_TO_STRING(f3(OBJECT_CONSTRUCT('a', OBJECT_CONSTRUCT())), ',')"));
        assertEquals("0", answer("SELECT ARRAY_SIZE(f3(OBJECT_CONSTRUCT('a', 1)))"));
    }

    @Test
    public void aTableFunctionBodyEndingItsFrameAtASemicolonAnswersItsRows() {
        assertEquals("created", create("CREATE FUNCTION tf1() RETURNS TABLE (a INT) AS $$SELECT 1 AS a);$$"));
        assertEquals("1", answer("SELECT * FROM TABLE(tf1())"));
        assertEquals("created", create("CREATE FUNCTION tf2(x INT) RETURNS TABLE (a INT) AS "
            + "$$SELECT x + 1 AS a UNION ALL SELECT x + 2) ; junk$$"));
        assertEquals("23", answer("SELECT SUM(a) FROM TABLE(tf2(10))"));
        // Without the semicolon the extra parenthesis is refused where it stands.
        assertEquals(syntax("1", "15", ")"), create("CREATE FUNCTION tf3() RETURNS TABLE (a INT) AS $$SELECT 1 AS a)$$"));
    }

    @Test
    public void theStatementTheFrameEndsIsCompiledAtCreate() {
        engine.execute("CREATE TABLE t (a INT)");
        final String[][] cells = {
            {"CREATE FUNCTION f12() RETURNS INT AS $$'abc');$$",
                "Declared return type 'NUMBER(38,0)' is incompatible with actual return type 'VARCHAR(3)'"},
            {"CREATE FUNCTION f13() RETURNS INT AS $$nosuch);$$",
                "SQL compilation error: error line 1 at position 1|invalid identifier 'NOSUCH'"},
            {"CREATE FUNCTION f20() RETURNS VARCHAR AS $$1);$$",
                "Declared return type 'VARCHAR(134217728)' is incompatible with actual return type 'NUMBER(1,0)'"},
            {"CREATE FUNCTION h8() RETURNS INT AS $$SELECT CURRENT_DATE);$$",
                "Declared return type 'NUMBER(38,0)' is incompatible with actual return type 'DATE'"},
            {"CREATE FUNCTION h10() RETURNS INT AS $$SELECT 1, 2);$$",
                "Declared return type 'NUMBER(38,0)' is incompatible with actual return type 'ROW(NUMBER(1,0), NUMBER(1,0))'"},
            {"CREATE FUNCTION h11(x INT) RETURNS INT AS $$SELECT nosuch FROM (SELECT 1 AS a));$$",
                "SQL compilation error: error line 1 at position 8|invalid identifier 'NOSUCH'"},
            {"CREATE FUNCTION g11() RETURNS INT AS $$SELECT COUNT(*) FROM nosuchtable);$$",
                hinted("SQL compilation error:|Object 'TEST_DB.TEST_SCHEMA.NOSUCHTABLE' does not exist or not authorized.")},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], create(cell[0]), cell[0]);
        }
    }

    @Test
    public void aStatementTheFrameEndsThatDoesNotReadIsRefusedWhereItBreaks() {
        final String[][] cells = {
            {"CREATE FUNCTION f14() RETURNS INT AS $$1) +;$$", syntax("1", "5", ";")},
            {"CREATE FUNCTION f15() RETURNS INT AS $$SELECT 1 +);$$", syntax("1", "1", "SELECT")},
            {"CREATE FUNCTION f17() RETURNS INT AS $$1) $$", syntax("1", "4", ")")},
            {"CREATE FUNCTION g8() RETURNS INT AS $$1) + (2;$$", syntax("1", "8", ";")},
            {"CREATE FUNCTION g9() RETURNS INT AS $$ABS(1;$$", syntax("1", "6", ";")},
            {"CREATE FUNCTION g12() RETURNS INT AS $$1) 2;$$", syntax("1", "4", "2")},
            {"CREATE FUNCTION g13() RETURNS INT AS $$1));$$", syntax("1", "3", ")")},
            {"CREATE FUNCTION g14() RETURNS INT AS $$BEGIN RETURN 1; END);$$", syntax("1", "7", "RETURN")},
            // The frame's characters are read to the end: a literal left open after the semicolon is refused.
            {"CREATE FUNCTION g5() RETURNS INT AS $$1); 'unterminated$$",
                REFUSED + "parse error line 1 at position 19 near '<EOF>'."},
            {"CREATE FUNCTION g6() RETURNS INT AS $$1); \"unterminated$$",
                REFUSED + "parse error line 1 at position 19 near '<EOF>'."},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], create(cell[0]), cell[0]);
        }
    }

    @Test
    public void aQueryClauseAfterTheFramesClosingParenthesisIsRefusedAtItsKeyword() {
        final String[][] cells = {
            {"CREATE FUNCTION b1() RETURNS INT AS $$SELECT 1) UNION ALL SELECT 2;$$", syntax("1", "11", "UNION")},
            {"CREATE FUNCTION b2() RETURNS TABLE (a INT) AS $$SELECT 1 AS a) UNION ALL SELECT 2;$$",
                syntax("1", "16", "UNION")},
            {"CREATE FUNCTION e1() RETURNS INT AS $$1) UNION SELECT 2;$$", syntax("1", "4", "UNION")},
            {"CREATE FUNCTION e2() RETURNS INT AS $$SELECT 1) ORDER BY 1;$$", syntax("1", "11", "ORDER")},
            {"CREATE FUNCTION e3() RETURNS INT AS $$SELECT 1) LIMIT 1;$$", syntax("1", "11", "LIMIT")},
            {"CREATE FUNCTION e4() RETURNS INT AS $$SELECT 1) FROM t;$$", syntax("1", "11", "FROM")},
            {"CREATE FUNCTION e5() RETURNS INT AS $$(SELECT 1)) UNION SELECT 2;$$", syntax("1", "13", "UNION")},
            {"CREATE FUNCTION e6() RETURNS INT AS $$SELECT 1) EXCEPT SELECT 2;$$", syntax("1", "11", "EXCEPT")},
            {"CREATE FUNCTION e7() RETURNS TABLE (a INT) AS $$SELECT 1 AS a) ORDER BY 1;$$", syntax("1", "16", "ORDER")},
            {"CREATE FUNCTION e8() RETURNS INT AS $$SELECT 1) x;$$", syntax("1", "11", "x")},
            {"CREATE FUNCTION fa2() RETURNS INT AS $$SELECT 1) UNION ALL SELECT 2 +;$$", syntax("1", "11", "UNION")},
            {"CREATE FUNCTION fa3() RETURNS INT AS $$SELECT 1) QUALIFY TRUE;$$", syntax("1", "11", "QUALIFY")},
            {"CREATE FUNCTION fa4() RETURNS INT AS $$SELECT 1) WHERE TRUE;$$", syntax("1", "11", "WHERE")},
            {"CREATE FUNCTION fa5() RETURNS INT AS $$SELECT 1) GROUP BY 1;$$", syntax("1", "11", "GROUP")},
            {"CREATE FUNCTION fa6() RETURNS INT AS $$SELECT 1) MINUS SELECT 2;$$", syntax("1", "11", "MINUS")},
            {"CREATE FUNCTION fa7() RETURNS INT AS $$SELECT 1) INTERSECT SELECT 2;$$", syntax("1", "11", "INTERSECT")},
            {"CREATE FUNCTION fa8() RETURNS INT AS $$1) ORDER BY 1;$$", syntax("1", "4", "ORDER")},
            {"CREATE FUNCTION fa9() RETURNS INT AS $$1) AS x;$$", syntax("1", "4", "AS")},
            {"CREATE FUNCTION fa10() RETURNS INT AS $$SELECT 1) , 2;$$", syntax("1", "11", ",")},
            {"CREATE FUNCTION fa12() RETURNS INT AS $$(SELECT 1) UNION (SELECT 2)) ORDER BY 1;$$",
                syntax("1", "30", "ORDER")},
            {"CREATE FUNCTION fa13() RETURNS INT AS $$SELECT 1) FETCH FIRST 1 ROWS ONLY;$$", syntax("1", "11", "FETCH")},
            {"CREATE FUNCTION fa14() RETURNS INT AS $$SELECT 1) OFFSET 1;$$", syntax("1", "11", "OFFSET")},
            {"CREATE FUNCTION fa15() RETURNS INT AS $$SELECT 1) JOIN t;$$", syntax("1", "11", "JOIN")},
            // A predicate continues the expression the frame holds.
            {"CREATE FUNCTION e9() RETURNS INT AS $$SELECT 1) IS NULL;$$",
                "Declared return type 'NUMBER(38,0)' is incompatible with actual return type 'BOOLEAN'"},
            {"CREATE FUNCTION e10() RETURNS BOOLEAN AS $$SELECT 1) IS NULL;$$", "created"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], create(cell[0]), cell[0]);
        }
        assertEquals("false", answer("SELECT e10()"));
    }

    @Test
    public void aCycleThroughABodyThatClosesItsFrameIsRefused() {
        final String[][] cells = {
            {"CREATE FUNCTION cy1() RETURNS INT AS $$1$$", "created"},
            {"CREATE FUNCTION cy2() RETURNS INT AS $$cy1());$$", "created"},
            {"CREATE OR REPLACE FUNCTION cy1() RETURNS INT AS $$cy2()$$", "Detected a cycle in SQL UDF: CY1"},
            {"CREATE FUNCTION cw1() RETURNS INT AS $$1$$", "created"},
            {"CREATE FUNCTION cw2() RETURNS INT AS $$cw1()) + 1;$$", "created"},
            {"CREATE OR REPLACE FUNCTION cw1() RETURNS INT AS $$cw2()$$", "Detected a cycle in SQL UDF: CW1"},
            {"CREATE FUNCTION cv1() RETURNS INT AS $$1$$", "created"},
            {"CREATE FUNCTION cv2() RETURNS INT AS $$SELECT cv1());$$", "created"},
            {"CREATE OR REPLACE FUNCTION cv1() RETURNS INT AS $$cv2() + 1$$", "Detected a cycle in SQL UDF: CV1"},
            // Through a table function called as a relation.
            {"CREATE FUNCTION ct1() RETURNS INT AS $$1$$", "created"},
            {"CREATE FUNCTION ct2() RETURNS TABLE (a INT) AS $$SELECT ct1() AS a);$$", "created"},
            {"CREATE OR REPLACE FUNCTION ct1() RETURNS INT AS $$SELECT COUNT(*) FROM TABLE(ct2())$$",
                "Detected a cycle in SQL UDF: CT1"},
            // A call after the semicolon is never read, so it closes no cycle.
            {"CREATE FUNCTION cu1() RETURNS INT AS $$1$$", "created"},
            {"CREATE FUNCTION cu2() RETURNS INT AS $$1); cu1()$$", "created"},
            {"CREATE OR REPLACE FUNCTION cu1() RETURNS INT AS $$cu2()$$", "created"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], create(cell[0]), cell[0]);
        }
        assertEquals("1", answer("SELECT cy1()"));
        assertEquals("2", answer("SELECT cw2()"));
        assertEquals("1", answer("SELECT ct1()"));
        assertEquals("1", answer("SELECT cu1()"));
    }

    @Test
    public void theBodyIsKeptAsWritten() {
        engine.execute("CREATE FUNCTION kept() RETURNS INT AS $$1) ;\ngarbage$$");
        final ResultSet described = engine.executeQuery("DESCRIBE FUNCTION kept()");
        assertEquals("1) ;\ngarbage", cell(described, soleRowWhere(described, "property", "body"), "value"));
    }
}
