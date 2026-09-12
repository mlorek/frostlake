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
 * A FOR loop's counter is read-only for as long as it is in scope. Assigning to it is refused while the
 * block COMPILES, anchored on the {@code :=}:
 *
 * <pre>
 *   SQL compilation error: error line 1 at position 28
 *    Assignment to variable 'I' is not permitted.
 * </pre>
 *
 * An integer range makes a counter; a cursor loop's record does not, and a LET or DECLARE of the name
 * hides it until the scope that declared it ends. Every cell was measured on a real account.
 */
public class ForCounterAssignmentTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return String.valueOf(refused.getMessage()).replace('\n', '|');
    }

    private String refusedBlock(final String block) {
        return refusal("EXECUTE IMMEDIATE $$ " + block + " $$");
    }

    private String answer(final String block) {
        return String.valueOf(engine.executeQuery("EXECUTE IMMEDIATE $$ " + block + " $$")
            .getRows().get(0).getValue(0));
    }

    private static String notPermitted(final int position, final String name) {
        return "SQL compilation error: error line 1 at position " + position + "| Assignment to variable '"
            + name + "' is not permitted.";
    }

    @Test
    public void assigningToTheCounterIsRefusedAtTheAssignment() {
        assertEquals(notPermitted(28, "I"), refusedBlock("BEGIN FOR i IN 1 TO 1 DO i := 5; END FOR; RETURN 'ok'; END;"));
        assertEquals(notPermitted(27, "I"), refusedBlock("BEGIN FOR i IN 1 TO 1 DO i:=5; END FOR; RETURN 'ok'; END;"),
            "anchored on the ':='");
        assertEquals(notPermitted(28, "I"), refusedBlock("BEGIN FOR i IN 1 TO 1 DO I := 5; END FOR; RETURN 'ok'; END;"));
        assertEquals(notPermitted(32, "i"),
            refusedBlock("BEGIN FOR \"i\" IN 1 TO 1 DO \"i\" := 5; END FOR; RETURN 'ok'; END;"),
            "a quoted counter keeps its case");
        assertEquals(notPermitted(28, "I"),
            refusedBlock("BEGIN FOR i IN 1 TO 1 DO i := i + 1; END FOR; RETURN 'ok'; END;"));
        assertEquals(notPermitted(36, "I"),
            refusedBlock("BEGIN FOR i IN REVERSE 1 TO 3 DO i := 5; END FOR; RETURN 'ok'; END;"));
        assertEquals(notPermitted(28, "I"),
            refusedBlock("BEGIN FOR i IN 1 TO 1 DO i := 5; i := 6; END FOR; RETURN 'ok'; END;"), "the first one");
    }

    /** It is compiled, not run: an empty loop is refused too, and it comes before a later fault. */
    @Test
    public void itIsACompileTimeRefusal() {
        assertEquals(notPermitted(28, "I"), refusedBlock("BEGIN FOR i IN 1 TO 0 DO i := 5; END FOR; RETURN 'ok'; END;"));
        assertEquals(notPermitted(28, "I"),
            refusedBlock("BEGIN FOR i IN 1 TO 1 DO i := 5; END FOR; RETURN missing; END;"),
            "ahead of a later unknown name");
        assertEquals(notPermitted(28, "I"), refusedBlock(
            "BEGIN FOR i IN 1 TO 1 DO i := 5; END FOR; FOR i IN 1 TO 1 DO NULL; END FOR; RETURN 'ok'; END;"));
        engine.execute("CREATE OR REPLACE PROCEDURE fca_p() RETURNS VARCHAR LANGUAGE SQL AS"
            + " $$ BEGIN FOR i IN 1 TO 1 DO i := 5; END FOR; RETURN 'ok'; END; $$");
        assertEquals(notPermitted(28, "I"), refusal("CALL fca_p()"), "a procedure is created, and refused at its CALL");
    }

    /** The counter is read-only wherever it is in scope: inner loops, branches and nested blocks. */
    @Test
    public void theCounterIsReadOnlyThroughoutItsLoop() {
        assertEquals(notPermitted(47, "I"), refusedBlock(
            "BEGIN FOR i IN 1 TO 2 DO FOR j IN 1 TO 2 DO i := 5; END FOR; END FOR; RETURN 'ok'; END;"));
        assertEquals(notPermitted(43, "I"), refusedBlock(
            "BEGIN FOR i IN 1 TO 1 DO IF (TRUE) THEN i := 5; END IF; END FOR; RETURN 'ok'; END;"));
        assertEquals(notPermitted(34, "I"), refusedBlock(
            "BEGIN FOR i IN 1 TO 1 DO BEGIN i := 5; END; END FOR; RETURN 'ok'; END;"));
        assertEquals(notPermitted(45, "I"), refusedBlock(
            "BEGIN FOR i IN 1 TO 1 DO WHILE (FALSE) DO i := 5; END WHILE; END FOR; RETURN 'ok'; END;"));
        assertEquals(notPermitted(57, "I"), refusedBlock(
            "DECLARE i INTEGER DEFAULT 0; BEGIN FOR i IN 1 TO 1 DO i := 5; END FOR; RETURN i; END;"),
            "a counter hides an outer variable of its name");
        assertEquals(notPermitted(51, "I"), refusedBlock(
            "BEGIN FOR i IN 1 TO 1 DO BEGIN LET i := 5; END; i := 6; END FOR; RETURN 'ok'; END;"),
            "an inner block's LET hides it only until that block's END");
    }

    /** Everything else is assignable: a redeclared name, a cursor loop's record, the name after END FOR. */
    @Test
    public void whatIsNotACounterIsAssignable() {
        assertEquals("ok", answer("BEGIN FOR i IN 1 TO 1 DO LET i := 5; END FOR; RETURN 'ok'; END;"));
        assertEquals("ok", answer("BEGIN FOR i IN 1 TO 1 DO LET i := 5; i := 6; END FOR; RETURN 'ok'; END;"));
        assertEquals("ok", answer(
            "BEGIN FOR i IN 1 TO 1 DO DECLARE i INT DEFAULT 0; BEGIN i := 5; END; END FOR; RETURN 'ok'; END;"));
        assertEquals("ok", answer(
            "DECLARE c CURSOR FOR SELECT 1 AS a; BEGIN FOR r IN c DO r := 5; END FOR; RETURN 'ok'; END;"));
        assertEquals("5", answer(
            "DECLARE i INTEGER DEFAULT 0; BEGIN FOR i IN 1 TO 1 DO NULL; END FOR; i := 5; RETURN i; END;"));
        assertEquals("5", answer(
            "DECLARE x INTEGER DEFAULT 0; BEGIN FOR i IN 1 TO 1 DO x := 5; END FOR; RETURN x; END;"));
        assertEquals("ok", answer("BEGIN FOR i IN 1 TO 1 DO SELECT 1 INTO :i; END FOR; RETURN 'ok'; END;"));
        assertEquals("ok", answer(
            "BEGIN FOR i IN 1 TO 1 DO FOR i IN 1 TO 1 DO NULL; END FOR; END FOR; RETURN 'ok'; END;"));
    }

    /** An assignment to a name that does not exist is refused at its ':=' as well. */
    @Test
    public void anUnknownTargetIsRefusedAtItsAssignment() {
        assertEquals("SQL compilation error: error line 1 at position 34|invalid identifier 'MISSING'",
            refusedBlock("BEGIN FOR i IN 1 TO 1 DO missing := 5; i := 5; END FOR; RETURN 'ok'; END;"));
    }
}
