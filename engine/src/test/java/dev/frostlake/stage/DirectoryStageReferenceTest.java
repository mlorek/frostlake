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

package dev.frostlake.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

/**
 * DIRECTORY(@stage) reads a named stage alone and reads its name on its own: a path is a syntax error naming the
 * whole reference at its '@'; an empty part or a fourth one is a syntax error placed in the name's own text; a user's
 * or a table's stage is refused as a kind the function does not take; a stage that cannot be found — its database or
 * schema missing too — is refused naming the reference as written; and a stage without its directory table is
 * refused as not enabled. Every cell is live-verified.
 */
public class DirectoryStageReferenceTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (x INT)");
        engine.execute("CREATE STAGE st");
        engine.execute("CREATE STAGE \"MySt\"");
        engine.execute("CREATE STAGE ds DIRECTORY = (ENABLE = TRUE)");
    }

    /** Every row's first cell, a bar between rows, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                out.append(out.length() > 0 ? " | " : "").append(row.getValue(0));
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String syntax(final int position, final String token) {
        return "SQL compilation error:|syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void aPathIsASyntaxErrorAtTheReference() {
        assertEquals(syntax(24, "@st/x"), answer("SELECT * FROM DIRECTORY(@st/x)"));
        assertEquals(syntax(24, "@st/"), answer("SELECT * FROM DIRECTORY(@st/)"));
        assertEquals(syntax(24, "@ds/x"), answer("SELECT * FROM DIRECTORY(@ds/x)"));
        assertEquals(syntax(24, "@%T/x"), answer("SELECT * FROM DIRECTORY(@%T/x)"));
        assertEquals(syntax(24, "@~/x"), answer("SELECT * FROM DIRECTORY(@~/x)"));
        assertEquals(syntax(24, "@..st/x"), answer("SELECT * FROM DIRECTORY(@..st/x)"));
        assertEquals(syntax(24, "@test_db..%t/x"), answer("SELECT * FROM DIRECTORY(@test_db..%t/x)"));
    }

    @Test
    public void theNameIsReadOnItsOwn() {
        assertEquals(syntax(0, "."), answer("SELECT * FROM DIRECTORY(@..st)"));
        assertEquals(syntax(0, "."), answer("SELECT * FROM DIRECTORY( @..st)"));
        assertEquals(syntax(0, "."), answer("SELECT * FROM DIRECTORY(@..st) x"));
        assertEquals(syntax(0, "."), answer("SELECT * FROM t, DIRECTORY(@..st)"));
        assertEquals(syntax(0, "."), answer("SELECT * FROM DIRECTORY(\n@..st)"));
        assertEquals(syntax(0, "."), answer("SELECT * FROM DIRECTORY(@...st)"));
        assertEquals(syntax(0, "."), answer("SELECT * FROM DIRECTORY(@..%t)"));
        assertEquals(syntax(8, "."), answer("SELECT * FROM DIRECTORY(@test_db..st)"));
        assertEquals(syntax(8, "."), answer("SELECT * FROM DIRECTORY(@test_db...st)"));
        assertEquals(syntax(8, "."), answer("SELECT * FROM DIRECTORY(@test_db..%t)"));
        assertEquals(syntax(8, "."), answer("SELECT * FROM DIRECTORY(@test_db....st)"));
        assertEquals(syntax(4, "."), answer("SELECT * FROM DIRECTORY(@\"a\"..st)"));
        assertEquals(syntax(5, "."), answer("SELECT * FROM DIRECTORY(@a.b.c.d)"));
        assertEquals(syntax(5, "."), answer("SELECT * FROM DIRECTORY(@a.b.c.d.e)"));
    }

    @Test
    public void aUsersOrATablesStageIsNotTaken() {
        final String refused = "SQL compilation error: Argument 1 to function 'DIRECTORY' provides a user or table stage. "
            + "These stage kinds are not supported by this function.";
        assertEquals(refused, answer("SELECT * FROM DIRECTORY(@%T)"));
        assertEquals(refused, answer("SELECT * FROM DIRECTORY(@~)"));
    }

    @Test
    public void aStageThatCannotBeFoundIsNamedAsWritten() {
        assertEquals("SQL compilation error: Stage '@nosuch' provided to the function 'DIRECTORY' does not exist or is "
            + "not authorized.", answer("SELECT * FROM DIRECTORY(@nosuch)"));
        assertEquals("SQL compilation error: Stage '@NOSUCH' provided to the function 'DIRECTORY' does not exist or is "
            + "not authorized.", answer("SELECT * FROM DIRECTORY(@NOSUCH)"));
        assertEquals("SQL compilation error: Stage '@public.nosuch' provided to the function 'DIRECTORY' does not exist "
            + "or is not authorized.", answer("SELECT * FROM DIRECTORY(@public.nosuch)"));
        assertEquals("SQL compilation error: Stage '@test_db.nosch.st' provided to the function 'DIRECTORY' does not "
            + "exist or is not authorized.", answer("SELECT * FROM DIRECTORY(@test_db.nosch.st)"));
        assertEquals("SQL compilation error: Stage '@nodb.public.st' provided to the function 'DIRECTORY' does not "
            + "exist or is not authorized.", answer("SELECT * FROM DIRECTORY(@nodb.public.st)"));
    }

    @Test
    public void aStageWithoutItsDirectoryTable() {
        assertEquals("DIRECTORY not enabled for the stage st", answer("SELECT * FROM DIRECTORY(@st)"));
        assertEquals("DIRECTORY not enabled for the stage \"MySt\"", answer("SELECT * FROM DIRECTORY(@\"MySt\")"));
        assertEquals("", answer("SELECT relative_path FROM DIRECTORY(@ds)"));
    }
}
