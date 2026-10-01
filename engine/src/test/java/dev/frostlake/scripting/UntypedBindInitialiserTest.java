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
 * An untyped declaration cannot infer its type from an initialiser that reads a bind: a bind written alone takes
 * its variable's type, except that no declaration is inferred as a VARIANT, and a cast takes its target's, while a
 * bind anywhere else — an operand, a call's argument, a NOT, AND or OR, a subquery — refuses the declaration while
 * the block compiles, before anything runs, reachable or not. A bind no variable declares is refused ahead of
 * that. Every cell is live-verified.
 */
public class UntypedBindInitialiserTest extends BaseDatabaseTest {

    private static final String INFERRED = "SQL compilation error: error line 1 at position %d\n"
        + " variable '%s' cannot have its type inferred from initializer";

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

    @Test
    public void aBindInsideTheInitialiserGivesNoType() {
        final String[][] cells = {
            {"LET a := 1; LET b := (SELECT :a); RETURN b;", "18", "B"},
            {"LET a := 1; LET b := (SELECT :a + 1); RETURN b;", "18", "B"},
            {"LET a := 1; LET b := (SELECT TO_NUMBER(:a)); RETURN b;", "18", "B"},
            {"LET a := 1; LET b := :a + 1; RETURN b;", "18", "B"},
            {"LET x := 1; LET t := SYSTEM$TYPEOF(:x); RETURN t;", "18", "T"},
            {"LET s := 'ab'; LET b := UPPER(:s); RETURN b;", "21", "B"},
            {"LET a := 1; LET b := (SELECT 1) + :a; RETURN b;", "18", "B"},
            {"LET a := 1; LET b := IFF(TRUE, :a, 0); RETURN b;", "18", "B"},
            {"LET a := 1; LET b := TO_NUMBER(:a); RETURN b;", "18", "B"},
            {"LET a := 1; LET b := (SELECT :a::NUMBER); RETURN b;", "18", "B"},
            {"LET f := TRUE; LET b := NOT :f; RETURN b;", "21", "B"},
            {"LET f := TRUE; LET b := :f AND TRUE; RETURN b;", "21", "B"},
            {"LET f := TRUE; LET b := TRUE OR :f; RETURN b;", "21", "B"},
            {"LET f := TRUE; LET b := CASE WHEN :f THEN 1 ELSE 2 END; RETURN b;", "21", "B"},
            {"LET a := 1; LET b := :a = 1; RETURN b;", "18", "B"},
            {"LET a := 1; LET b := -:a; RETURN b;", "18", "B"},
            {"LET a := 1; LET b := :a::NUMBER + 1; RETURN b;", "18", "B"},
            {"LET a := 1; LET b := :a IS NULL; RETURN b;", "18", "B"},
            {"LET a := 1; LET b := :a IN (1, 2); RETURN b;", "18", "B"},
            {"LET a := 1; LET b := [:a]; RETURN b;", "18", "B"},
            {"LET a := 1; LET b := EXISTS (SELECT :a); RETURN b;", "18", "B"},
            {"LET a := 1; LET b := (SELECT (SELECT :a)); RETURN b;", "18", "B"},
            {"LET a := 1; LET b := (SELECT :a WHERE TRUE); RETURN b;", "18", "B"},
            {"LET a := 1; IF (FALSE) THEN LET b := (SELECT :a); END IF; RETURN 5;", "34", "B"},
            {"LET a := 1; BEGIN LET b := :a + 1; END; RETURN 5;", "24", "B"},
            {"FOR i IN 1 TO 2 DO LET b := :i + 1; END FOR; RETURN 5;", "25", "B"},
            {"LET a := 1; LET b := :a + missing; RETURN b;", "18", "B"},
            {"LET a := 1; LET b := (SELECT missing) + :a; RETURN b;", "18", "B"},
            {"LET a := 1; LET b := :a + 1; LET y NUMBER := :nosuch; RETURN 1;", "18", "B"},
        };
        for (final String[] cell : cells) {
            assertEquals(String.format(INFERRED, Integer.parseInt(cell[1]), cell[2]), refusal(block(cell[0])), cell[0]);
        }
        assertEquals(String.format(INFERRED, 21, "B"),
            refusal("EXECUTE IMMEDIATE $$DECLARE a DEFAULT 1; b DEFAULT (SELECT :a); BEGIN RETURN b; END;$$"));
        assertEquals(String.format(INFERRED, 21, "B"),
            refusal("EXECUTE IMMEDIATE $$DECLARE a DEFAULT 1; b DEFAULT :a + 1; BEGIN RETURN b; END;$$"));
    }

    @Test
    public void aBindAloneOrUnderACastIsTyped() {
        final String[][] cells = {
            {"LET a := 1; LET b := :a; RETURN b;", "1"},
            {"LET a := 1; LET b := (:a); RETURN b;", "1"},
            {"LET a := 1; LET b := ((:a)); RETURN b;", "1"},
            {"LET a := 1; LET b := :a::NUMBER; RETURN b;", "1"},
            {"LET a := 1; LET b := CAST(:a AS NUMBER); RETURN b;", "1"},
            {"LET a := 1; LET b := (:a)::NUMBER; RETURN b;", "1"},
            {"LET a := 1; LET b := (:a + 1)::NUMBER; RETURN b;", "2"},
            {"LET s := '7'; LET b := TRY_CAST(:s AS NUMBER); RETURN b;", "7"},
            {"LET a := 1; LET b := (SELECT :a)::NUMBER; RETURN b;", "1"},
            {"LET a := 1; LET b := CAST((SELECT :a) AS VARCHAR); RETURN b;", "1"},
            {"LET a := 1; LET b := :a::NUMBER::VARCHAR; RETURN b;", "1"},
            {"LET a := 1; LET b NUMBER := (SELECT :a); RETURN b;", "1"},
            {"LET x := 1; LET t VARCHAR := SYSTEM$TYPEOF(:x); RETURN t;", "NUMBER(38,0)[SB16]"},
            {"LET x := 1; LET t := SYSTEM$TYPEOF(x); RETURN t;", "NUMBER(38,0)[SB16]"},
            {"LET s := 'ab'; LET b := UPPER(s); RETURN b;", "AB"},
            {"LET a := 1; LET b := 1; b := :a + 1; RETURN b;", "2"},
            {"FOR i IN 1 TO 2 DO LET b := :i; END FOR; RETURN 5;", "5"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(block(cell[0])), cell[0]);
        }
        assertEquals("1", answer("EXECUTE IMMEDIATE $$DECLARE a DEFAULT 1; b DEFAULT :a; BEGIN RETURN b; END;$$"));
    }

    @Test
    public void aBindAloneOfAVariantGivesNoType() {
        assertEquals(String.format(INFERRED, 26, "W"), refusal(block("LET v VARIANT := 1; LET w := :v; RETURN 1;")));
        assertEquals(String.format(INFERRED, 26, "W"), refusal(block("LET v VARIANT := 1; LET w := (:v); RETURN 1;")));
        assertEquals(String.format(INFERRED, 29, "W"),
            refusal("EXECUTE IMMEDIATE $$DECLARE v VARIANT DEFAULT 1; w DEFAULT :v; BEGIN RETURN 1; END;$$"));
        assertEquals("1", answer(block("LET o OBJECT := OBJECT_CONSTRUCT('a', 1); LET w := :o; RETURN 1;")));
    }

    @Test
    public void theRefusalComesBeforeAnythingRuns() {
        assertEquals(String.format(INFERRED, 60, "B"), refusal(block(
            "LET a := 1; CREATE OR REPLACE TABLE ubi_mark (x INT); LET b := (SELECT :a); RETURN b;")));
        assertEquals(0, engine.executeQuery("SHOW TABLES LIKE 'UBI_MARK'").getRows().size());
    }

    @Test
    public void anUndeclaredBindIsRefusedFirst() {
        final String[][] cells = {
            {"LET b := :nosuch + 1; RETURN b;", "15"},
            {"LET b := missing + :nosuch; RETURN b;", "25"},
            {"LET b := (SELECT missing) + :nosuch; RETURN b;", "34"},
            {"LET b := (SELECT :nosuch FROM (SELECT 1 AS z)); RETURN b;", "23"},
            {"LET b := NULL + :nosuch; RETURN b;", "22"},
        };
        for (final String[] cell : cells) {
            assertEquals("SQL compilation error: error line 1 at position " + cell[1] + "\ninvalid identifier 'nosuch'",
                refusal(block(cell[0])), cell[0]);
        }
    }

    @Test
    public void aProcedureIsRefusedWhenCalled() {
        engine.execute("CREATE OR REPLACE PROCEDURE ubi_p(a NUMBER) RETURNS NUMBER LANGUAGE SQL AS"
            + " $$BEGIN LET b := :a + 1; RETURN b; END;$$");
        assertEquals(String.format(INFERRED, 6, "B"), refusal("CALL ubi_p(1)"));
        engine.execute("CREATE OR REPLACE PROCEDURE ubi_q(a NUMBER) RETURNS NUMBER LANGUAGE SQL AS"
            + " $$BEGIN LET b := a + 1; RETURN b; END;$$");
        assertEquals("2", answer("CALL ubi_q(1)"));
    }
}
