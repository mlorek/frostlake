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
 * Which unresolvable name a block reports when it holds several. A subquery of a block's expression compiles
 * as a query does, its select list ahead of its WHERE, its JOIN's ON, its GROUP BY and its QUALIFY, whether its
 * side of an AND or an OR runs or not. The block compile resolves every bind, assignment target and cursor name,
 * and the type of every untyped declaration, before any bare name of an expression; the bare names come last,
 * a statement list's declarations ahead of its other statements, and a statement's own parts in the order written,
 * save a REPEAT's condition, which comes ahead of its body. Every cell is live-verified.
 */
public class ScriptingNameResolutionOrderTest extends BaseDatabaseTest {

    private static final String EXPRESSION_ERROR = "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position ";

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
        return "EXECUTE IMMEDIATE $$BEGIN " + body + " END;$$";
    }

    private static String invalid(final int position, final String name) {
        return "SQL compilation error: error line 1 at position " + position + "\ninvalid identifier '" + name + "'";
    }

    @Test
    public void aSubqueryReportsItsSelectListFirst() {
        final String[][] cells = {
            {"LET a NUMBER := (SELECT missing FROM (SELECT 1 AS b) WHERE missing2 = 1); RETURN 5;", "22", "23", "MISSING"},
            {"RETURN (SELECT missing FROM (SELECT 1 AS b) WHERE missing2 = 1);", "13", "15", "MISSING"},
            {"LET a NUMBER := 1; a := (SELECT missing FROM (SELECT 1 AS b) WHERE missing2 = 1); RETURN 5;", "30", "23",
                "MISSING"},
            {"IF ((SELECT missing FROM (SELECT 1 AS b) WHERE missing2 = 1) = 1) THEN RETURN 1; END IF; RETURN 5;", "10",
                "15", "MISSING"},
            {"LET a NUMBER := (SELECT missing FROM (SELECT 1 AS b) GROUP BY missing2); RETURN 5;", "22", "23", "MISSING"},
            {"LET a NUMBER := (SELECT missing FROM (SELECT 1 AS b) QUALIFY missing2 = 1); RETURN 5;", "22", "23",
                "MISSING"},
            {"LET a NUMBER := (SELECT missing FROM (SELECT 1 AS b) x JOIN (SELECT 2 AS c) y ON missing3 = 1); RETURN 5;",
                "22", "23", "MISSING"},
            {"LET a NUMBER := (SELECT missing FROM (SELECT 1 AS b) WHERE (SELECT missing3) = 1); RETURN 5;", "22", "23",
                "MISSING"},
            {"LET a NUMBER := 1 + (SELECT missing FROM (SELECT 1 AS b) WHERE missing2 = 1); RETURN 5;", "22", "27",
                "MISSING"},
            {"RETURN IFF(TRUE, 1, (SELECT missing FROM (SELECT 1 AS b) WHERE missing2 = 1));", "13", "28", "MISSING"},
            {"LET a NUMBER := 1; a := (SELECT missing FROM (SELECT 1 AS b) WHERE missing2 = 1) + (SELECT missing3);"
                + " RETURN 5;", "30", "23", "MISSING"},
            {"LET a BOOLEAN := EXISTS (SELECT missing FROM (SELECT 1 AS b) WHERE missing2 = 1); RETURN 5;", "23", "30",
                "MISSING"},
            {"LET a BOOLEAN := 1 IN (SELECT missing FROM (SELECT 1 AS b) WHERE missing2 = 1); RETURN 5;", "23", "28",
                "MISSING"},
            {"LET a BOOLEAN := 1 = ANY (SELECT missing FROM (SELECT 1 AS b) WHERE missing2 = 1); RETURN 5;", "23", "31",
                "MISSING"},
            {"LET a BOOLEAN := 1 = ALL (SELECT missing FROM (SELECT 1 AS b) WHERE missing2 = 1); RETURN 5;", "23", "31",
                "MISSING"},
            {"LET a BOOLEAN := 1 = SOME (SELECT missing FROM (SELECT 1 AS b) WHERE missing2 = 1); RETURN 5;", "23", "32",
                "MISSING"},
            {"RETURN 1 > ANY (SELECT missing FROM (SELECT 1 AS b) WHERE missing2 = 1);", "13", "23", "MISSING"},
            {"IF (1 <> ALL (SELECT missing FROM (SELECT 1 AS b) WHERE missing2 = 1)) THEN RETURN 1; END IF; RETURN 2;",
                "10", "24", "MISSING"},
            {"LET a BOOLEAN := 1 + 22 = ANY (SELECT missing FROM (SELECT 1 AS b)); RETURN 5;", "23", "36", "MISSING"},
            {"LET a BOOLEAN := TRUE AND 1 = ANY (SELECT missing FROM (SELECT 1 AS b)); RETURN 5;", "23", "40",
                "MISSING"},
            {"LET a NUMBER := (SELECT MAX(b) FROM (SELECT 1 AS b) WHERE missing2 = 1 HAVING missing3 = 1); RETURN 5;",
                "22", "57", "MISSING2"},
            {"LET a NUMBER := (SELECT b FROM (SELECT 1 AS b) x JOIN (SELECT 2 AS c) y ON missing3 = 1"
                + " WHERE missing2 = 1); RETURN 5;", "22", "74", "MISSING3"},
        };
        for (final String[] cell : cells) {
            assertEquals(EXPRESSION_ERROR + cell[1] + " : " + invalid(Integer.parseInt(cell[2]), cell[3]),
                refusal(block(cell[0])), cell[0]);
        }
        assertEquals(EXPRESSION_ERROR + "25 : " + invalid(23, "MISSING"), refusal("EXECUTE IMMEDIATE $$DECLARE a NUMBER"
            + " DEFAULT (SELECT missing FROM (SELECT 1 AS b) WHERE missing2 = 1); BEGIN RETURN a; END;$$"));
    }

    @Test
    public void aSettledSideOfAConnectiveStillCompiles() {
        assertEquals(EXPRESSION_ERROR + "10 : " + invalid(25, "MISSING"), refusal(block(
            "IF (FALSE AND (SELECT missing FROM (SELECT 1 AS b)) = 1) THEN RETURN 1; END IF; RETURN 5;")));
        assertEquals(EXPRESSION_ERROR + "10 : " + invalid(23, "MISSING"), refusal(block(
            "IF (TRUE OR (SELECT missing FROM (SELECT 1 AS b)) = 1) THEN RETURN 1; END IF; RETURN 5;")));
        assertEquals(EXPRESSION_ERROR + "23 : " + invalid(33, "MISSING"), refusal(block(
            "LET x BOOLEAN := FALSE AND (SELECT missing FROM (SELECT 1 AS b) WHERE missing2 = 1) = 1; RETURN 5;")));
        assertEquals("5", answer(block("IF (FALSE AND (SELECT b FROM (SELECT 1 AS b)) = 1) THEN RETURN 1; END IF;"
            + " RETURN 5;")));
        assertEquals(EXPRESSION_ERROR + "10 : SQL compilation error:\nUnknown function MISSING_FN.",
            refusal(block("IF (FALSE AND missing_fn() = 1) THEN RETURN 1; END IF; RETURN 5;")));
    }

    @Test
    public void bindsTargetsAndCursorsResolveBeforeBareNames() {
        final String[][] cells = {
            {"LET x NUMBER := missing; LET y NUMBER := :nosuch; RETURN 1;", "47", "nosuch"},
            {"IF (missing = 1) THEN RETURN :nosuch; END IF; RETURN 1;", "35", "nosuch"},
            {"RETURN missing + (SELECT :nosuch);", "31", "nosuch"},
            {"LET x NUMBER := 1; x := missing + :nosuch; RETURN 1;", "40", "nosuch"},
            {"LET b NUMBER := missing + :nosuch; RETURN b;", "32", "nosuch"},
            {"RETURN missing + :nosuch;", "23", "nosuch"},
            {"RETURN missing; LET y NUMBER := :nosuch;", "38", "nosuch"},
            {"LET c := missing + 1; LET y NUMBER := :nosuch; RETURN 1;", "44", "nosuch"},
            {"BEGIN RETURN missing; END; LET y NUMBER := :nosuch; RETURN 1;", "49", "nosuch"},
            {"RETURN missing; EXCEPTION WHEN OTHER THEN RETURN :nosuch;", "55", "nosuch"},
            {"FOR i IN missing TO 2 DO RETURN :nosuch; END FOR; RETURN 1;", "38", "nosuch"},
            {"RETURN missing; nosuchtarget := 1;", "35", "NOSUCHTARGET"},
            {"RETURN missing; OPEN nosuchcursor;", "27", "NOSUCHCURSOR"},
            {"LET c := missing + 1; nosuchtarget := 1; RETURN 1;", "41", "NOSUCHTARGET"},
            {"LET c := missing + 1; OPEN nosuchcursor; RETURN 1;", "33", "NOSUCHCURSOR"},
            {"LET a := 1; nosuchtarget := 1; LET y NUMBER := :nosuch; RETURN 1;", "31", "NOSUCHTARGET"},
            {"LET y NUMBER := :nosuch; nosuchtarget := 1; RETURN 1;", "22", "nosuch"},
        };
        for (final String[] cell : cells) {
            assertEquals(invalid(Integer.parseInt(cell[1]), cell[2]), refusal(block(cell[0])), cell[0]);
        }
        assertEquals(invalid(53, "nosuch"), refusal(
            "EXECUTE IMMEDIATE $$DECLARE c DEFAULT missing + 1; BEGIN LET y NUMBER := :nosuch; RETURN 1; END;$$"));
    }

    @Test
    public void untypedDeclarationsAreInferredBeforeBareNames() {
        final String inferred = "SQL compilation error: error line 1 at position %d\n"
            + " variable '%s' cannot have its type inferred from initializer";
        assertEquals(String.format(inferred, 31, "C"), refusal(block("LET x NUMBER := missing; LET c := :x + 1; RETURN 1;")));
        assertEquals(String.format(inferred, 22, "C"), refusal(block("RETURN missing; LET c := missing2;")));
        assertEquals(String.format(inferred, 28, "D"), refusal(block("LET c := missing + 1; LET d := missing2; RETURN 1;")));
        assertEquals(invalid(39, "MISSING2"), refusal(block("RETURN missing; LET c := (SELECT missing2);")));
        assertEquals(invalid(39, "MISSING2"), refusal(block("RETURN missing; LET c := (SELECT missing2) + 1;")));
        assertEquals(invalid(45, "MISSING2"), refusal(block("LET c := missing + 1; LET d := (SELECT missing2); RETURN 1;")));
        assertEquals(invalid(23, "MISSING"), refusal(block("LET c := (SELECT missing) + 1; LET y NUMBER := :nosuch; RETURN 1;")));
    }

    @Test
    public void aListsDeclarationsResolveBeforeItsOtherStatements() {
        final String[][] cells = {
            {"RETURN missing; LET c := missing2 + 1;", "31", "MISSING2"},
            {"RETURN missing; LET x NUMBER := missing2; LET d := missing3 + 1;", "38", "MISSING2"},
            {"LET c := missing + 1; LET d := missing2 + 1; RETURN 1;", "15", "MISSING"},
            {"LET c NUMBER := (SELECT 1) + missing; LET d := missing2 + 1; RETURN 1;", "35", "MISSING"},
            {"RETURN (SELECT 1) + missing; LET d := missing2 + 1;", "44", "MISSING2"},
            {"IF (missing = 1) THEN RETURN 1; END IF; LET x NUMBER := (SELECT 1) + missing2; RETURN 1;", "75", "MISSING2"},
            {"IF (FALSE) THEN RETURN missing; END IF; LET c := missing2 + 1;", "55", "MISSING2"},
            {"LET x NUMBER := 1; RETURN missing; x := missing2;", "32", "MISSING"},
            {"LET x NUMBER := 1; x := missing; LET y NUMBER := missing2; RETURN 1;", "55", "MISSING2"},
            {"WHILE (missing) DO RETURN 1; END WHILE; LET x NUMBER := missing2; RETURN 1;", "62", "MISSING2"},
            {"CASE (missing) WHEN 1 THEN RETURN 1; END CASE; LET x NUMBER := missing2; RETURN 1;", "69", "MISSING2"},
            {"RETURN missing; RETURN (SELECT 1) + missing2;", "13", "MISSING"},
            {"RETURN missing; BEGIN LET x NUMBER := missing2; END;", "13", "MISSING"},
            {"RETURN missing; IF (TRUE) THEN LET x NUMBER := missing2; END IF;", "13", "MISSING"},
            {"IF (TRUE) THEN RETURN missing; LET x NUMBER := missing2; END IF;", "53", "MISSING2"},
            {"IF (missing) THEN LET x NUMBER := missing2; END IF;", "10", "MISSING"},
            {"IF (TRUE) THEN RETURN missing; ELSE LET x NUMBER := missing2; END IF;", "28", "MISSING"},
            {"LOOP RETURN missing; LET x NUMBER := missing2; END LOOP;", "43", "MISSING2"},
            {"RETURN missing; EXCEPTION WHEN OTHER THEN LET x NUMBER := missing2;", "13", "MISSING"},
            {"RETURN missing; FOR i IN missing2 TO 2 DO RETURN 1; END FOR;", "13", "MISSING"},
            {"RETURN missing2 + missing;", "13", "MISSING2"},
        };
        for (final String[] cell : cells) {
            assertEquals(invalid(Integer.parseInt(cell[1]), cell[2]), refusal(block(cell[0])), cell[0]);
        }
        assertEquals(invalid(25, "MISSING"), refusal(
            "EXECUTE IMMEDIATE $$DECLARE x NUMBER DEFAULT missing; BEGIN LET y NUMBER := missing2; RETURN 1; END;$$"));
    }

    @Test
    public void aStatementsPartsResolveInTheOrderWrittenSaveARepeatsCondition() {
        final String[][] cells = {
            {"REPEAT RETURN missing; UNTIL (missing2) END REPEAT;", "36", "MISSING2"},
            {"REPEAT LET q NUMBER := missing; UNTIL (missing2) END REPEAT;", "45", "MISSING2"},
            {"REPEAT RETURN missing; UNTIL (TRUE) END REPEAT; RETURN missing2;", "20", "MISSING"},
            {"IF (TRUE) THEN RETURN missing; ELSEIF (missing2) THEN RETURN 1; END IF;", "28", "MISSING"},
            {"CASE WHEN TRUE THEN RETURN missing; WHEN missing2 THEN RETURN 1; END CASE;", "33", "MISSING"},
            {"LET x := 1; CASE x WHEN 1 THEN RETURN missing; WHEN missing2 THEN RETURN 1; END CASE;", "44", "MISSING"},
            {"IF (TRUE) THEN RETURN missing; ELSE RETURN missing2; END IF;", "28", "MISSING"},
        };
        for (final String[] cell : cells) {
            assertEquals(invalid(Integer.parseInt(cell[1]), cell[2]), refusal(block(cell[0])), cell[0]);
        }
    }

    @Test
    public void aProceduresDeclaredReturnIsJudgedBeforeBareNames() {
        final String incompatible = "SQL compilation error: error line 1 at position %d\n"
            + " Declared return type 'DATE' is incompatible with actual return type 'NUMBER(1,0)'";
        engine.execute("CREATE OR REPLACE PROCEDURE snro_a() RETURNS DATE LANGUAGE SQL AS"
            + " $$BEGIN RETURN missing; RETURN 5; END;$$");
        assertEquals(String.format(incompatible, 22), refusal("CALL snro_a()"));
        engine.execute("CREATE OR REPLACE PROCEDURE snro_b() RETURNS DATE LANGUAGE SQL AS"
            + " $$BEGIN LET x NUMBER := missing; RETURN 5; END;$$");
        assertEquals(String.format(incompatible, 31), refusal("CALL snro_b()"));
    }
}
