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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Where an argument-type refusal is placed depends on how the account writes each name out ahead of the refused
 * operator: in a stored procedure's body, and in a block whose outermost DECLARE section declares a cursor or a
 * RESULTSET with a query, every name is written as a bind, one column wider than a plain name; a cursor or a
 * RESULTSET declared anywhere else, or an exception, changes nothing. Every cell is live-verified.
 */
public class BlockNamesWrittenAsBindsTest extends BaseDatabaseTest {

    private static final String NUMBER_BOOLEAN = "Invalid argument types for function '+': (NUMBER(38,0), BOOLEAN)";

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        return refused.getMessage();
    }

    private String answer(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        return String.valueOf(row.getValue(0));
    }

    private static String block(final String body) {
        return "EXECUTE IMMEDIATE $$" + body + "$$";
    }

    private static String uncaught(final int at, final int inner, final String sentence) {
        return "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position " + at
            + " : SQL compilation error: error line 1 at position " + inner + "\n" + sentence;
    }

    @Test
    public void aDeclaredCursorOrQueryResultsetBindsEveryName() {
        final String[][] cells = {
            {"DECLARE c CURSOR FOR SELECT TRUE AS f; total NUMBER DEFAULT 0; BEGIN FOR r IN c DO total := total + TRUE;"
                + " END FOR; RETURN total; END;", "92", "31"},
            {"DECLARE c CURSOR FOR SELECT TRUE AS f; total NUMBER DEFAULT 0; BEGIN FOR r IN c DO RETURN total + TRUE;"
                + " END FOR; RETURN 1; END;", "90", "23"},
            {"DECLARE c CURSOR FOR SELECT TRUE AS f; total NUMBER DEFAULT 0; BEGIN FOR r IN c DO total := 1; END FOR;"
                + " RETURN total + TRUE; END;", "111", "23"},
            {"DECLARE c CURSOR FOR SELECT 1; total NUMBER DEFAULT 0; BEGIN RETURN total + TRUE; END;", "68", "23"},
            {"DECLARE rs RESULTSET DEFAULT (SELECT 1); total NUMBER DEFAULT 0; BEGIN RETURN total + TRUE; END;", "78",
                "23"},
            {"DECLARE c CURSOR FOR SELECT 1; BEGIN LET t := 0; BEGIN RETURN t + TRUE; END; END;", "62", "19"},
        };
        for (final String[] cell : cells) {
            assertEquals(uncaught(Integer.parseInt(cell[1]), Integer.parseInt(cell[2]), NUMBER_BOOLEAN),
                refusal(block(cell[0])), cell[0]);
        }
        assertEquals(uncaught(76, 11, "Invalid argument types for function '+': (DATE, BOOLEAN)"), refusal(block(
            "DECLARE c CURSOR FOR SELECT 1; BEGIN LET d := TO_DATE('2024-01-01'); RETURN d + TRUE; END;")));
        assertEquals(uncaught(59, 5, "Invalid argument types for function '+': (VARCHAR(2), BOOLEAN)"), refusal(block(
            "DECLARE c CURSOR FOR SELECT 1; BEGIN LET s := 'ab'; RETURN s + TRUE; END;")));
        assertEquals("SQL compilation error: error line 1 at position 92\n" + NUMBER_BOOLEAN, refusal(block(
            "DECLARE c CURSOR FOR SELECT TRUE AS f; total NUMBER DEFAULT 0; BEGIN FOR r IN c DO LET q := total + TRUE;"
                + " END FOR; RETURN 1; END;")));
    }

    @Test
    public void otherBlocksWriteANameAsItself() {
        final String[][] cells = {
            {"DECLARE total NUMBER DEFAULT 0; BEGIN FOR i IN 1 TO 1 DO total := total + TRUE; END FOR; RETURN total;"
                + " END;", "66", "30"},
            {"DECLARE total NUMBER DEFAULT 0; BEGIN WHILE (total < 1) DO total := total + TRUE; END WHILE; RETURN total;"
                + " END;", "68", "30"},
            {"DECLARE total NUMBER DEFAULT 0; BEGIN IF (TRUE) THEN total := total + TRUE; END IF; RETURN total; END;",
                "62", "30"},
            {"DECLARE total NUMBER DEFAULT 0; BEGIN LOOP total := total + TRUE; BREAK; END LOOP; RETURN total; END;",
                "52", "30"},
            {"DECLARE total NUMBER DEFAULT 0; BEGIN REPEAT total := total + TRUE; UNTIL (total > 0) END REPEAT;"
                + " RETURN total; END;", "54", "30"},
            {"DECLARE total NUMBER DEFAULT 0; BEGIN FOR i IN 1 TO 1 DO total := i + total + TRUE; END FOR; RETURN total;"
                + " END;", "66", "50"},
            {"DECLARE e EXCEPTION (-20001, 'm'); BEGIN LET t := 0; RETURN t + TRUE; END;", "60", "18"},
            {"DECLARE rs RESULTSET; BEGIN LET t := 0; RETURN t + TRUE; END;", "47", "18"},
            {"BEGIN LET t := 0; DECLARE c CURSOR FOR SELECT 1; BEGIN RETURN t + TRUE; END; END;", "62", "18"},
            {"BEGIN LET t := 0; DECLARE c CURSOR FOR SELECT 1; BEGIN LET z := 1; END; RETURN t + TRUE; END;", "79", "18"},
            {"BEGIN LET total := 0; IF (FALSE) THEN LET c CURSOR FOR SELECT 1; END IF; RETURN total + TRUE; END;", "80",
                "22"},
            {"BEGIN LET total := 0; SELECT :total; RETURN total + TRUE; END;", "44", "22"},
            {"BEGIN LET total := 0; SELECT 1; RETURN total + TRUE; END;", "39", "22"},
            {"BEGIN LET total := 0; IF (FALSE) THEN DECLARE c CURSOR FOR SELECT 1; BEGIN RETURN 1; END; END IF;"
                + " RETURN total + TRUE; END;", "105", "22"},
            {"BEGIN LET total := 0; LET rs RESULTSET := (SELECT 1); RETURN total + TRUE; END;", "61", "22"},
            {"BEGIN LET total := 0; LET q NUMBER := (SELECT 1); RETURN total + TRUE; END;", "57", "22"},
            {"BEGIN LET total := 0; LET c CURSOR FOR SELECT ?; OPEN c USING (total); RETURN total + TRUE; END;", "78",
                "22"},
        };
        for (final String[] cell : cells) {
            assertEquals(uncaught(Integer.parseInt(cell[1]), Integer.parseInt(cell[2]), NUMBER_BOOLEAN),
                refusal(block(cell[0])), cell[0]);
        }
        assertEquals("-1", answer(block(
            "BEGIN LET total := 0; RETURN total + TRUE; EXCEPTION WHEN OTHER THEN RETURN -1; END;")));
    }

    @Test
    public void aProcedureBodyBindsEveryName() {
        engine.execute("CREATE OR REPLACE PROCEDURE bnb_local() RETURNS NUMBER LANGUAGE SQL AS"
            + " $$BEGIN LET x := 1; RETURN x + TRUE; END;$$");
        assertEquals(uncaught(25, 19, NUMBER_BOOLEAN), refusal("CALL bnb_local()"));
        engine.execute("CREATE OR REPLACE PROCEDURE bnb_date(d DATE) RETURNS VARCHAR LANGUAGE SQL AS"
            + " $$BEGIN RETURN d + TRUE; END;$$");
        assertEquals(uncaught(13, 11, "Invalid argument types for function '+': (DATE, BOOLEAN)"),
            refusal("CALL bnb_date('2024-01-01')"));
        engine.execute("CREATE OR REPLACE PROCEDURE bnb_let(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS"
            + " $$BEGIN LET y := n + TRUE; RETURN 1; END;$$");
        assertEquals("SQL compilation error: error line 1 at position 15\n" + NUMBER_BOOLEAN, refusal("CALL bnb_let(1)"));
        engine.execute("CREATE OR REPLACE PROCEDURE bnb_typed(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS"
            + " $$BEGIN LET y NUMBER := 1 + n + TRUE; RETURN 1; END;$$");
        assertEquals(uncaught(22, 31, NUMBER_BOOLEAN), refusal("CALL bnb_typed(1)"));
        engine.execute("CREATE OR REPLACE PROCEDURE bnb_set(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS"
            + " $$BEGIN LET x := 0; x := x + n + TRUE; RETURN x; END;$$");
        assertEquals(uncaught(23, 48, NUMBER_BOOLEAN), refusal("CALL bnb_set(1)"));
        engine.execute("CREATE OR REPLACE PROCEDURE bnb_declared() RETURNS NUMBER LANGUAGE SQL AS"
            + " $$DECLARE t NUMBER DEFAULT 1; BEGIN RETURN t + TRUE; END;$$");
        assertEquals(uncaught(41, 19, NUMBER_BOOLEAN), refusal("CALL bnb_declared()"));
        engine.execute("CREATE OR REPLACE PROCEDURE bnb_nested(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS"
            + " $$BEGIN BEGIN RETURN n + TRUE; END; END;$$");
        assertEquals(uncaught(19, 19, NUMBER_BOOLEAN), refusal("CALL bnb_nested(1)"));
        engine.execute("CREATE OR REPLACE PROCEDURE bnb_text() RETURNS NUMBER LANGUAGE SQL AS"
            + " $$BEGIN LET s := 'ab'; RETURN s + TRUE; END;$$");
        assertEquals(uncaught(28, 5, "Invalid argument types for function '+': (VARCHAR(2), BOOLEAN)"),
            refusal("CALL bnb_text()"));
    }
}
