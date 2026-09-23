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

package dev.frostlake.procedural;

import dev.frostlake.BaseJdbcTest;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * NOT, AND and OR are values wherever a block takes one: a RETURN, a LET, an assignment, a DECLARE default
 * and a session SET. They bind below the comparisons ({@code NOT TRUE = FALSE} is TRUE), read NULL in three
 * values, read a number or a text as a boolean, and answer a BOOLEAN column; an untyped name they declare is
 * a BOOLEAN, and a typed one converts. A side that is no boolean is refused as the value's own evaluation
 * error, an unknown name in either side is an invalid identifier while the block compiles, and a missing
 * side is a syntax error at what follows. A procedure's declared RETURNS type never judges a RETURN of one,
 * which answers its BOOLEAN whatever the procedure declares, but it does judge a name one declared. Every
 * cell is live-verified.
 */
public class BlockBooleanOperatorValueTest extends BaseJdbcTest {

    /** The first column's type name and its boolean value, or the refusal on one line. */
    private String answer(final String sql) {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            final boolean value = rs.getBoolean(1);
            return rs.getMetaData().getColumnTypeName(1) + " " + (rs.wasNull() ? "null" : String.valueOf(value));
        } catch (final SQLException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The first column's type name and its value read as text, or the refusal on one line. */
    private String text(final String sql) {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getMetaData().getColumnTypeName(1) + " " + rs.getString(1);
        } catch (final SQLException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String block(final String body) {
        return "EXECUTE IMMEDIATE $$ " + body + " $$";
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(block(cell[0])), cell[0]);
        }
    }

    @Test
    public void aReturnedBooleanOperatorIsABoolean() {
        assertCells(new String[][] {
            {"BEGIN RETURN NOT TRUE; END;", "BOOLEAN false"},
            {"BEGIN RETURN TRUE AND FALSE; END;", "BOOLEAN false"},
            {"BEGIN RETURN TRUE OR FALSE; END;", "BOOLEAN true"},
            {"BEGIN RETURN NOT (1 = 2); END;", "BOOLEAN true"},
            {"BEGIN RETURN (NOT TRUE); END;", "BOOLEAN false"},
            {"BEGIN RETURN NOT TRUE AND FALSE; END;", "BOOLEAN false"},
            {"BEGIN RETURN NOT NOT TRUE; END;", "BOOLEAN true"},
            {"BEGIN RETURN TRUE AND FALSE OR TRUE; END;", "BOOLEAN true"},
            {"BEGIN RETURN TRUE AND TRUE AND TRUE; END;", "BOOLEAN true"},
            {"BEGIN RETURN NOT TRUE = FALSE; END;", "BOOLEAN true"},
            {"BEGIN RETURN NULL AND TRUE; END;", "BOOLEAN null"},
            {"BEGIN RETURN NOT NULL; END;", "BOOLEAN null"},
            {"BEGIN RETURN 1 AND 2; END;", "BOOLEAN true"},
            {"BEGIN RETURN 5 AND TRUE; END;", "BOOLEAN true"},
            {"BEGIN RETURN 'true' AND TRUE; END;", "BOOLEAN true"},
            {"BEGIN RETURN (SELECT TRUE) AND TRUE; END;", "BOOLEAN true"},
            {"BEGIN RETURN NOT EXISTS (SELECT 1); END;", "BOOLEAN false"},
            {"BEGIN IF (TRUE) THEN RETURN NOT FALSE; END IF; END;", "BOOLEAN true"},
            {"BEGIN LET y := 5; RETURN y > 3 AND y < 10; END;", "BOOLEAN true"},
            {"BEGIN LET x := 1; RETURN NOT x; END;", "BOOLEAN false"},
            {"BEGIN LET x := 1.5; RETURN NOT x; END;", "BOOLEAN false"},
            {"DECLARE c1 CURSOR FOR SELECT TRUE AS a; BEGIN FOR r IN c1 DO RETURN r.a AND TRUE; END FOR; END;",
                "BOOLEAN true"},
            {"DECLARE c1 CURSOR FOR SELECT 1 AS a; BEGIN OPEN c1; RETURN NOT TRUE; END;", "BOOLEAN false"},
        });
    }

    @Test
    public void aDeclaredOrAssignedBooleanOperatorIsABoolean() {
        assertCells(new String[][] {
            {"BEGIN LET x := NOT TRUE; RETURN x; END;", "BOOLEAN false"},
            {"BEGIN LET x := TRUE AND FALSE; RETURN x; END;", "BOOLEAN false"},
            {"BEGIN LET x BOOLEAN := NOT TRUE; RETURN x; END;", "BOOLEAN false"},
            {"BEGIN LET x := NOT TRUE; RETURN (x); END;", "BOOLEAN false"},
            {"BEGIN LET x := NOT TRUE; RETURN x OR :x; END;", "BOOLEAN false"},
            {"BEGIN LET x := NOT TRUE; RETURN x = FALSE; END;", "BOOLEAN true"},
            {"DECLARE x BOOLEAN DEFAULT NOT FALSE; BEGIN RETURN x; END;", "BOOLEAN true"},
            {"DECLARE x DEFAULT NOT TRUE; BEGIN RETURN x; END;", "BOOLEAN false"},
            {"DECLARE x := TRUE OR FALSE; BEGIN RETURN x; END;", "BOOLEAN true"},
            {"DECLARE x BOOLEAN := TRUE AND FALSE; BEGIN RETURN x; END;", "BOOLEAN false"},
            {"DECLARE x BOOLEAN; BEGIN x := 1 = 1 AND 2 > 1; RETURN x; END;", "BOOLEAN true"},
            {"DECLARE x BOOLEAN; BEGIN x := NOT x; RETURN x; END;", "BOOLEAN null"},
            {"BEGIN LET x := TRUE; x := NOT x AND x; RETURN x; END;", "BOOLEAN false"},
        });
        assertEquals("VARCHAR BOOLEAN[SB1]", text(block("BEGIN LET x := NOT TRUE; RETURN SYSTEM$TYPEOF(:x); END;")));
        assertEquals("VARCHAR false", text(block("BEGIN LET x VARCHAR := TRUE AND FALSE; RETURN x; END;")));
        assertEquals("VARCHAR false", text(block("DECLARE x VARCHAR DEFAULT NOT TRUE; BEGIN RETURN x; END;")));
        assertEquals("NUMBER 0", text(block("DECLARE x NUMBER DEFAULT TRUE AND FALSE; BEGIN RETURN x; END;")));
        assertEquals("NUMBER 1", text(block("BEGIN LET x := 1; x := x > 0 AND x < 2; RETURN x; END;")));
        assertEquals("NUMBER 1", text(block("BEGIN LET x := 0; x := NOT x; RETURN x; END;")));
    }

    @Test
    public void aSessionVariableTakesABooleanOperator() throws SQLException {
        statement.execute("SET bool_op_one = TRUE AND FALSE");
        statement.execute("SET (bool_op_two, bool_op_three) = (NOT FALSE, TRUE OR FALSE)");
        try (ResultSet rs = statement.executeQuery("SELECT $bool_op_one, $bool_op_two, $bool_op_three")) {
            rs.next();
            assertEquals(false, rs.getBoolean(1));
            assertEquals(true, rs.getBoolean(2));
            assertEquals(true, rs.getBoolean(3));
        }
    }

    @Test
    public void aSideThatIsNoBooleanOrNoNameIsRefused() {
        final String uncaught = "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position ";
        final String invalid = "SQL compilation error: error line 1 at position ";
        final String syntax = "SQL compilation error:|syntax error line 1 at position ";
        assertCells(new String[][] {
            {"BEGIN RETURN NOT 'abc'; END;", uncaught + "14 : Boolean value 'abc' is not recognized"},
            {"BEGIN RETURN TRUE AND 'abc'; END;", uncaught + "14 : Boolean value 'abc' is not recognized"},
            {"BEGIN LET x := 'a'; RETURN NOT x; END;", uncaught + "28 : Boolean value 'a' is not recognized"},
            {"BEGIN RETURN TRUE AND nosuch; END;", invalid + "23|invalid identifier 'NOSUCH'"},
            {"BEGIN RETURN NOT nosuch; END;", invalid + "18|invalid identifier 'NOSUCH'"},
            {"BEGIN RETURN NOT TRUE AND nosuch; END;", invalid + "27|invalid identifier 'NOSUCH'"},
            {"BEGIN LET x := 2; RETURN x > 1 OR nosuch; END;", invalid + "35|invalid identifier 'NOSUCH'"},
            {"BEGIN RETURN TRUE AND :nosuch; END;", invalid + "23|invalid identifier 'nosuch'"},
            {"DECLARE x BOOLEAN DEFAULT NOT nosuch; BEGIN RETURN 1; END;", invalid + "31|invalid identifier 'NOSUCH'"},
            {"BEGIN LET y := 1; LET x := y > 0 AND nosuch; RETURN x; END;", invalid + "38|invalid identifier 'NOSUCH'"},
            {"BEGIN LET x := 1; x := NOT nosuch; RETURN x; END;", invalid + "28|invalid identifier 'NOSUCH'"},
            {"BEGIN RETURN TRUE AND; END;", syntax + "22 unexpected ';'."},
            {"BEGIN RETURN NOT; END;", syntax + "17 unexpected ';'."},
            {"BEGIN RETURN NOT TRUE OR; END;", syntax + "25 unexpected ';'."},
            {"BEGIN LET x := AND TRUE; RETURN x; END;", syntax + "16 unexpected 'AND'."},
        });
    }

    @Test
    public void aProceduresDeclaredTypeJudgesOnlyANameABooleanOperatorDeclared() throws SQLException {
        final String[][] procedures = {
            {"p_date_not", "DATE", "RETURN NOT TRUE;"},
            {"p_date_eq", "DATE", "RETURN 1 = 1;"},
            {"p_boolean_and", "BOOLEAN", "RETURN TRUE AND FALSE;"},
            {"p_varchar_not", "VARCHAR", "RETURN NOT FALSE;"},
            {"p_number_or", "NUMBER", "RETURN TRUE OR FALSE;"},
            {"p_narrow_not", "NUMBER(1,0)", "RETURN NOT TRUE;"},
            {"p_date_let_not", "DATE", "LET x := NOT TRUE; RETURN x;"},
            {"p_date_let_eq", "DATE", "LET x := 1 = 1; RETURN x;"},
        };
        for (final String[] procedure : procedures) {
            statement.execute("CREATE OR REPLACE PROCEDURE " + procedure[0] + "() RETURNS " + procedure[1]
                + " LANGUAGE SQL AS $$ BEGIN " + procedure[2] + " END; $$");
        }
        statement.execute("""
            CREATE OR REPLACE PROCEDURE p_param_not(b BOOLEAN) RETURNS BOOLEAN LANGUAGE SQL AS $$
            BEGIN RETURN NOT b; END; $$""");
        statement.execute("CREATE OR REPLACE FUNCTION f_not() RETURNS BOOLEAN LANGUAGE SQL AS $$ BEGIN RETURN NOT TRUE; END $$");
        assertEquals("BOOLEAN false", answer("CALL p_date_not()"));
        assertEquals("BOOLEAN true", answer("CALL p_date_eq()"));
        assertEquals("BOOLEAN false", answer("CALL p_boolean_and()"));
        assertEquals("BOOLEAN true", answer("CALL p_varchar_not()"));
        assertEquals("BOOLEAN true", answer("CALL p_number_or()"));
        assertEquals("BOOLEAN false", answer("CALL p_narrow_not()"));
        assertEquals("BOOLEAN false", answer("CALL p_param_not(TRUE)"));
        assertEquals("BOOLEAN false", answer("SELECT f_not()"));
        final String judged = "SQL compilation error: error line 1 at position %d|"
            + " Declared return type 'DATE' is incompatible with actual return type 'BOOLEAN'";
        assertEquals(String.format(judged, 26), answer("CALL p_date_let_not()"));
        assertEquals(String.format(judged, 23), answer("CALL p_date_let_eq()"));
    }
}
