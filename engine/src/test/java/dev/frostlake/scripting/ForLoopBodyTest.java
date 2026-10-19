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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A FOR loop's body opens with DO and closes with END FOR, or opens with LOOP and closes with END LOOP, as a
 * WHILE's does; the two spellings do not mix.
 */
public class ForLoopBodyTest extends BaseDatabaseTest {

    private static final String SYNTAX = "SQL compilation error:\nsyntax error line 1 at position ";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE flb (a INT)");
        engine.execute("INSERT INTO flb VALUES (1), (2)");
    }

    private String answer(final String block) {
        return String.valueOf(engine.executeQuery("EXECUTE IMMEDIATE $$ " + block + " $$").getRows().get(0).getValue(0));
    }

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    @Test
    public void aForLoopOpensWithLoopAndClosesWithEndLoop() {
        assertEquals("6", answer("BEGIN LET s := 0; FOR j IN 1 TO 3 LOOP s := s + j; END LOOP; RETURN s; END;"));
        assertEquals("321",
            answer("BEGIN LET s := ''; FOR j IN REVERSE 1 TO 3 LOOP s := s || j; END LOOP; RETURN s; END;"));
        assertEquals("6", answer("BEGIN LET s := 0; FOR j IN 1 TO 3 LOOP s := s + j; END LOOP lbl; RETURN s; END;"));
        assertEquals("4", answer("BEGIN LET s := 0; FOR j IN 1 TO 2 LOOP FOR k IN 1 TO 2 DO s := s + 1; END FOR;"
            + " END LOOP; RETURN s; END;"));
        assertEquals("3", answer("BEGIN LET s := 0; FOR j IN 1 TO 9 LOOP s := s + j; IF (j = 2) THEN BREAK; END IF;"
            + " END LOOP; RETURN s; END;"));
    }

    @Test
    public void aCursorsAndAResultsetsRowsAreLoopedOverTheSameWay() {
        assertEquals("3", answer("DECLARE c CURSOR FOR SELECT a FROM flb ORDER BY a; s INT DEFAULT 0;"
            + " BEGIN FOR r IN c LOOP s := s + r.a; END LOOP; RETURN s; END;"));
        assertEquals("3", answer("DECLARE rs RESULTSET DEFAULT (SELECT a FROM flb ORDER BY a); s INT DEFAULT 0;"
            + " BEGIN FOR r IN rs LOOP s := s + r.a; END LOOP; RETURN s; END;"));
    }

    @Test
    public void theTwoSpellingsDoNotMix() {
        assertEquals(SYNTAX + "56 unexpected 'FOR'.", refusalOf(
            "EXECUTE IMMEDIATE $$ BEGIN LET s := 0; FOR j IN 1 TO 3 LOOP s := s + j; END FOR; RETURN s; END; $$"));
        assertEquals(SYNTAX + "54 unexpected 'LOOP'.", refusalOf(
            "EXECUTE IMMEDIATE $$ BEGIN LET s := 0; FOR j IN 1 TO 3 DO s := s + j; END LOOP; RETURN s; END; $$"));
    }

    @Test
    public void anEmptyBodyAndAMissingSemicolonAreRefusedAsAfterDo() {
        assertEquals(SYNTAX + "27 unexpected 'END'.", refusalOf("BEGIN FOR j IN 1 TO 3 LOOP END LOOP; RETURN 1; END;"));
        assertEquals(SYNTAX + "68 unexpected 'i'.\nsyntax error line 1 at position 71 unexpected 'END'.", refusalOf(
            "EXECUTE IMMEDIATE $$ BEGIN LET i := 0; FOR j IN 1 TO 3 LOOP i := i + 1; END LOOP RETURN i; END; $$"));
    }
}
