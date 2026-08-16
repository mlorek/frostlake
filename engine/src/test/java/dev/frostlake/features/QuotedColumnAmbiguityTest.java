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
 * Ambiguity is decided on the name as the reference MEANS it: an unquoted one upper-cased, a quoted one
 * verbatim. An unquoted {@code TABLE_NAME} beside a quoted {@code "table_name"} names one column, not
 * two, however the two relations are joined — only two columns of exactly the same name are ambiguous.
 * Live-verified.
 */
public class QuotedColumnAmbiguityTest extends BaseDatabaseTest {

    /** Two relations, one with an unquoted column and one with the quoted lower-case spelling. */
    private void createBothSpellings() {
        engine.execute("CREATE OR REPLACE TABLE amb_upper (TABLE_NAME VARCHAR, n INT)");
        engine.execute("CREATE OR REPLACE TABLE amb_quoted (\"table_name\" VARCHAR, m INT)");
        engine.execute("INSERT INTO amb_upper VALUES ('x', 1)");
        engine.execute("INSERT INTO amb_quoted VALUES ('x', 2)");
    }

    /** The one cell of a single-row, single-column query, as text. */
    private String scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? "NULL" : value.toString();
    }

    /** A join over the two spellings resolves each reference to its own column. */
    @Test
    public void aJoinOverBothSpellingsResolves() {
        createBothSpellings();
        assertEquals("x", scalar("SELECT TABLE_NAME FROM amb_upper LEFT JOIN amb_quoted"
            + " ON amb_quoted.\"table_name\" = amb_upper.TABLE_NAME"));
        assertEquals("x", scalar("SELECT TABLE_NAME FROM amb_upper LEFT JOIN amb_quoted"
            + " ON amb_quoted.\"table_name\" = amb_upper.TABLE_NAME ORDER BY TABLE_NAME"));
        assertEquals("x", scalar("SELECT TABLE_NAME AS t FROM amb_upper LEFT JOIN amb_quoted"
            + " ON \"table_name\" = TABLE_NAME"));
        assertEquals("1", scalar("SELECT n FROM amb_upper LEFT JOIN amb_quoted"
            + " ON \"table_name\" = TABLE_NAME WHERE TABLE_NAME = 'x'"));
        assertEquals("x", scalar("SELECT TABLE_NAME FROM amb_upper LEFT JOIN"
            + " (SELECT \"table_name\" FROM amb_quoted) s ON s.\"table_name\" = TABLE_NAME"));
    }

    /** A cross join resolves them too, in the select list and in a predicate. */
    @Test
    public void aCrossJoinOverBothSpellingsResolves() {
        createBothSpellings();
        final ResultSet both = engine.executeQuery(
            "SELECT TABLE_NAME, \"table_name\" FROM amb_upper, amb_quoted");
        assertEquals(1, both.getRowCount());
        assertEquals("x", both.getRows().get(0).getValue(0).toString());
        assertEquals("x", both.getRows().get(0).getValue(1).toString());
        assertEquals("2", scalar("SELECT m FROM amb_upper, amb_quoted"
            + " WHERE \"table_name\" = 'x' AND TABLE_NAME = 'x'"));
        assertEquals("1", scalar("SELECT COUNT(*) FROM amb_upper, amb_quoted GROUP BY TABLE_NAME"));
    }

    /** An unquoted reference means the upper-cased name, whichever case it was typed in. */
    @Test
    public void anUnquotedReferenceMeansTheUpperCasedName() {
        createBothSpellings();
        assertEquals("x", scalar("SELECT table_name FROM amb_upper, amb_quoted"));
        assertEquals("x", scalar("SELECT \"TABLE_NAME\" FROM amb_upper, amb_quoted"));
        assertEquals("x", scalar("SELECT \"table_name\" FROM amb_quoted, amb_upper"));
    }

    /** Two columns of EXACTLY the same name are still ambiguous. */
    @Test
    public void twoColumnsOfTheSameNameAreStillAmbiguous() {
        createBothSpellings();
        engine.execute("CREATE OR REPLACE TABLE amb_upper_too (TABLE_NAME VARCHAR)");
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TABLE_NAME FROM amb_upper, amb_upper_too");
            }
        });
        assertTrue(refused.getMessage().contains("ambiguous column name 'TABLE_NAME'"),
            "unexpected refusal: " + refused.getMessage());
    }
}
