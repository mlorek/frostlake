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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code //} opens a line comment exactly as {@code --} does: everything to the end of the line is
 * ignored, so two slashes never divide. Inside a string, a quoted name or a dollar-quoted body the two
 * slashes are ordinary text. Live-verified.
 */
public class SlashLineCommentTest extends BaseDatabaseTest {

    /** The one cell of a single-row, single-column query, as text. */
    private String scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? "NULL" : value.toString();
    }

    /** Everything after the two slashes is gone, to the end of the line. */
    @Test
    public void twoSlashesOpenALineComment() {
        assertEquals("1", scalar("SELECT 1 // c"));
        assertEquals("1", scalar("SELECT 1 //c"));
        assertEquals("1", scalar("SELECT 1 //"));
        assertEquals("1", scalar("// leading\nSELECT 1"));
        assertEquals("1", scalar("SELECT 1 WHERE 1 = 1 // c"));
        assertEquals("1", scalar("SELECT 1 /* a */ // b"));
        assertEquals("1", scalar("SELECT 1 -- d"));
    }

    /** The comment ends at the newline, so the next line is still part of the statement. */
    @Test
    public void theCommentEndsAtTheLine() {
        assertEquals("2", scalar("SELECT 1 // c\n + 1"));
    }

    /** Two slashes never divide: {@code 4 // 2} is 4 with a comment, not 2. */
    @Test
    public void twoSlashesNeverDivide() {
        assertEquals("4", scalar("SELECT 4 // 2"));
        assertEquals("2.000000", scalar("SELECT 4 / 2"));
        assertEquals("2.000000", scalar("SELECT 4 / /* x */ 2"));
    }

    /** Inside a string or a quoted name the two slashes are text. */
    @Test
    public void twoSlashesInsideALiteralAreText() {
        assertEquals("x//y", scalar("SELECT 'x//y'"));
        assertEquals("//", scalar("SELECT '//'"));
        assertEquals("1", scalar("SELECT 1 AS \"a//b\""));
    }

    /** A SQL UDF body carries its own line comments, and one that ENDS in a comment is refused. */
    @Test
    public void aRoutineBodyCarriesTheComment() {
        engine.execute("CREATE OR REPLACE FUNCTION slash_query() RETURNS NUMBER AS $$ SELECT 1 // c\n $$");
        assertEquals("1", scalar("SELECT slash_query()"));
        engine.execute("CREATE OR REPLACE FUNCTION slash_expr() RETURNS NUMBER AS $$ 1 // c\n $$");
        assertEquals("1", scalar("SELECT slash_expr()"));

        final RuntimeException statementEnded = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE OR REPLACE FUNCTION slash_cut() RETURNS NUMBER"
                    + " AS $$ SELECT 1 // c; $$");
            }
        });
        assertTrue(statementEnded.getMessage().contains("unexpected 'SELECT'"),
            "unexpected refusal: " + statementEnded.getMessage());

        final RuntimeException expressionEnded = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE OR REPLACE FUNCTION slash_cut_expr() RETURNS NUMBER AS $$1 // c$$");
            }
        });
        assertTrue(expressionEnded.getMessage().contains("unexpected '<EOF>'"),
            "unexpected refusal: " + expressionEnded.getMessage());
    }
}
