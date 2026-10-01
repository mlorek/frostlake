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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where a block places a refusal raised in a SELECT … INTO (live-verified).
 *
 * <p>The statement runs its own text less the INTO clause: the INTO keyword goes with the one whitespace
 * character before it and everything up to the last target, and every other space and line break stays. A
 * statement error is placed in that text, inside the uncaught-exception sentence. An undeclared variable is the
 * block's own compilation error and is placed in the block: the statement's line plus the line in its text,
 * and the statement's column plus the column in its text on EVERY line, where a plain statement's later line
 * keeps its own column.
 */
public class SelectIntoPlacementTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (1, TRUE), (7, FALSE)");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return "ACCEPTED " + rs.getRowCount();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String undeclared(final int line, final int position) {
        return "SQL compilation error: error line " + line + " at position " + position + "|invalid identifier 'NOSUCH'";
    }

    /** An undeclared variable in the query is placed in the block, past the statement's column. */
    @Test
    public void anUndeclaredVariableIsPlacedInTheBlock() {
        assertEquals(undeclared(1, 54), answer(
            "EXECUTE IMMEDIATE $$ DECLARE c INT; BEGIN SELECT COUNT(*) INTO :c FROM IDENTIFIER(:nosuch); RETURN c; END; $$"));
        assertEquals(undeclared(1, 29), answer(
            "EXECUTE IMMEDIATE $$ DECLARE c INT; BEGIN SELECT :nosuch INTO :c; RETURN c; END; $$"));
        assertEquals(undeclared(1, 54), answer(
            "EXECUTE IMMEDIATE $$ DECLARE c INT; BEGIN SELECT COUNT(*) INTO c FROM IDENTIFIER(:nosuch); RETURN c; END; $$"));
        assertEquals(undeclared(1, 64), answer(
            "EXECUTE IMMEDIATE $$ DECLARE a INT; b INT; BEGIN SELECT 1, COUNT(*) INTO :a, :b FROM IDENTIFIER(:nosuch); RETURN a; END; $$"));
        assertEquals(undeclared(1, 58), answer(
            "EXECUTE IMMEDIATE $$ DECLARE c INT; BEGIN SELECT COUNT(*) INTO :c FROM FZ, IDENTIFIER(:nosuch); RETURN c; END; $$"));
        assertEquals(undeclared(1, 55), answer(
            "EXECUTE IMMEDIATE $$ BEGIN LET c INT := 0; SELECT COUNT(*) INTO :c FROM IDENTIFIER(:nosuch); RETURN c; END; $$"));
        assertEquals(undeclared(1, 60), answer(
            "EXECUTE IMMEDIATE $$ DECLARE c INT; BEGIN BEGIN SELECT COUNT(*) INTO :c FROM IDENTIFIER(:nosuch); END; RETURN c; END; $$"));
        assertEquals(undeclared(1, 80), answer("EXECUTE IMMEDIATE $$ DECLARE c INT; BEGIN WITH w AS (SELECT 1 AS x) "
            + "SELECT COUNT(*) INTO :c FROM IDENTIFIER(:nosuch); RETURN c; END; $$"));
    }

    /** The spaces around INTO: only the one before it goes. */
    @Test
    public void onlyTheSpaceBeforeIntoGoes() {
        assertEquals(undeclared(1, 58), answer(
            "EXECUTE IMMEDIATE $$ DECLARE c INT; BEGIN SELECT COUNT(*)   INTO   :c   FROM IDENTIFIER(:nosuch); RETURN c; END; $$"));
        assertEquals(undeclared(1, 56), answer(
            "EXECUTE IMMEDIATE $$ DECLARE c INT; BEGIN SELECT COUNT(*) INTO :c   FROM IDENTIFIER(:nosuch); RETURN c; END; $$"));
        assertEquals(undeclared(1, 56), answer(
            "EXECUTE IMMEDIATE $$ DECLARE c INT; BEGIN SELECT COUNT(*)   INTO :c FROM IDENTIFIER(:nosuch); RETURN c; END; $$"));
    }

    /** Line breaks stay, and a later line is still offset by the statement's column. */
    @Test
    public void lineBreaksStay() {
        assertEquals(undeclared(2, 39), answer(
            "EXECUTE IMMEDIATE $$ DECLARE c INT; BEGIN SELECT COUNT(*) INTO :c\n FROM IDENTIFIER(:nosuch); RETURN c; END; $$"));
        assertEquals(undeclared(2, 34), answer(
            "EXECUTE IMMEDIATE $$ DECLARE c INT; BEGIN SELECT COUNT(*) INTO :c FROM\n IDENTIFIER(:nosuch); RETURN c; END; $$"));
        assertEquals(undeclared(3, 20), answer("EXECUTE IMMEDIATE $$ DECLARE c INT; BEGIN\n  SELECT COUNT(*) INTO :c\n"
            + "  FROM IDENTIFIER(:nosuch);\n RETURN c; END; $$"));
        assertEquals(undeclared(2, 39), answer(
            "EXECUTE IMMEDIATE $$ DECLARE c INT; BEGIN SELECT COUNT(*)\n INTO :c FROM IDENTIFIER(:nosuch); RETURN c; END; $$"));
        assertEquals(undeclared(1, 54), answer(
            "EXECUTE IMMEDIATE $$ DECLARE c INT; BEGIN SELECT COUNT(*)\nINTO :c FROM IDENTIFIER(:nosuch); RETURN c; END; $$"));
        assertEquals(undeclared(3, 34), answer("EXECUTE IMMEDIATE $$ DECLARE c INT;\nBEGIN\n"
            + "  SELECT COUNT(*) INTO :c FROM IDENTIFIER(:nosuch);\n  RETURN c;\nEND; $$"));
        assertEquals(undeclared(2, 32), answer(
            "EXECUTE IMMEDIATE $$ DECLARE c INT; BEGIN\nSELECT COUNT(*) INTO :c FROM IDENTIFIER(:nosuch);\nRETURN c; END; $$"));
    }

    /** A plain statement's later line keeps its own column. */
    @Test
    public void aPlainStatementsLaterLineKeepsItsColumn() {
        assertEquals(undeclared(1, 39), answer("EXECUTE IMMEDIATE $$ BEGIN SELECT COUNT(*) FROM IDENTIFIER(:nosuch); END; $$"));
        assertEquals(undeclared(2, 12), answer("EXECUTE IMMEDIATE $$ BEGIN SELECT COUNT(*) FROM\n IDENTIFIER(:nosuch); END; $$"));
        assertEquals(undeclared(3, 18), answer(
            "EXECUTE IMMEDIATE $$ BEGIN\n  SELECT COUNT(*)\n  FROM IDENTIFIER(:nosuch);\nEND; $$"));
    }

    /** A statement error keeps the statement's place and is placed in the text the statement runs. */
    @Test
    public void aStatementErrorIsPlacedInItsOwnText() {
        assertEquals("Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation error: "
            + "error line 1 at position 7|invalid identifier 'NOSUCH'",
            answer("EXECUTE IMMEDIATE $$ BEGIN SELECT nosuch; END; $$"));
        assertEquals("Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation error: "
            + "error line 1 at position 7|invalid identifier 'NOSUCH'",
            answer("EXECUTE IMMEDIATE $$ DECLARE c INT; BEGIN SELECT nosuch INTO :c; RETURN c; END; $$"));
        assertEquals("Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation error: "
            + "error line 1 at position 30|invalid identifier 'NOSUCH'",
            answer("EXECUTE IMMEDIATE $$ DECLARE c INT; BEGIN SELECT COUNT(*) INTO :c FROM FZ WHERE nosuch = 1; RETURN c; END; $$"));
        assertEquals("Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation error: "
            + "error line 1 at position 32|invalid identifier 'NOSUCH'",
            answer("EXECUTE IMMEDIATE $$ DECLARE c INT; BEGIN SELECT COUNT(*)   INTO :c FROM FZ WHERE nosuch = 1; RETURN c; END; $$"));
        assertEquals("Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation error: "
            + "error line 1 at position 24|invalid identifier 'NOSUCH'",
            answer("EXECUTE IMMEDIATE $$ DECLARE c INT; BEGIN SELECT id INTO :c FROM FZ WHERE nosuch = 1; RETURN c; END; $$"));
    }

    /** What the statement answers is unchanged. */
    @Test
    public void theValueIsUnchanged() {
        assertEquals("ACCEPTED 1", answer(
            "EXECUTE IMMEDIATE $$ DECLARE c INT; BEGIN SELECT COUNT(*)   INTO   :c\n FROM FZ WHERE id > 0; RETURN c; END; $$"));
        final ResultSet rs = engine.executeQuery(
            "EXECUTE IMMEDIATE $$ DECLARE c INT; BEGIN SELECT COUNT(*)\nINTO :c FROM FZ WHERE id > 5; RETURN c; END; $$");
        assertEquals("1", String.valueOf(rs.getRows().get(0).getValue(0)));
    }
}
