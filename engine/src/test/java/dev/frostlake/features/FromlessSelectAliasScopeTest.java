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
 * A SELECT with no FROM still has a scope: its own item list. The names it publishes resolve in its
 * WHERE exactly as a relation's columns do, by the ordinary identifier rules — an unquoted reference
 * folds to upper case, a quoted one matches verbatim, and an item written without an alias publishes
 * its own source text. The scope reaches that select's own clauses and no further: a query nested in
 * the predicate has a scope of its own and cannot see these names.
 *
 * <p>The predicate is then held to the clause rules a WHERE over a relation is held to, and a name the
 * scope cannot answer outranks every one of them, wherever in the predicate it stands. Live-verified.
 */
public class FromlessSelectAliasScopeTest extends BaseDatabaseTest {

    /** The one cell of a single-row, single-column query, as text. */
    private String scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? "NULL" : value.toString();
    }

    /** Asserts a query answers no row at all. */
    private void assertNoRow(final String sql) {
        assertEquals(0, engine.executeQuery(sql).getRowCount(), sql);
    }

    /** Asserts a statement is refused with a message carrying {@code fragment}. */
    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        assertTrue(refused.getMessage() != null && refused.getMessage().contains(fragment),
            sql + " should be refused with \"" + fragment + "\" but read: " + refused.getMessage());
    }

    /** A published name resolves in the predicate, and the row is kept or dropped by its value. */
    @Test
    public void aPublishedNameResolvesInThePredicate() {
        assertEquals("1", scalar("SELECT 1 AS a WHERE a = 1"));
        assertEquals("1", scalar("SELECT 1 AS a WHERE A = 1"));
        assertEquals("1", scalar("SELECT 1 AS a WHERE a IS NOT NULL"));
        assertEquals("1", scalar("SELECT 1 AS a WHERE a IN (1, 2)"));
        assertEquals("1", scalar("SELECT 1 AS a WHERE (a) = 1"));
        assertEquals("1", scalar("SELECT 1 AS a WHERE ABS(a) = 1"));
        assertEquals("1", scalar("SELECT 1 AS a WHERE a::VARCHAR = '1'"));
        assertEquals("x", scalar("SELECT 'x' AS a WHERE a = 'x'"));
        assertNoRow("SELECT 1 AS a WHERE a = 2");
    }

    /** Every item publishes, whatever it computes, and a later item may be named too. */
    @Test
    public void everyItemPublishes() {
        assertEquals("1", scalar("SELECT 1 AS a, 2 AS b WHERE a + b = 3"));
        assertEquals("2", scalar("SELECT 1 + 1 AS a WHERE a = 2"));
        assertEquals("1", scalar("SELECT 1 AS a, a + 1 AS b WHERE b = 2"));
        assertEquals("1", scalar("SELECT 1 AS a, 2 AS b WHERE b = 2"));
        assertEquals("NULL", scalar("SELECT NULL AS a WHERE a IS NULL"));
    }

    /** The names resolve by the identifier rules: unquoted folds, quoted matches verbatim. */
    @Test
    public void theNamesFollowTheIdentifierRules() {
        assertEquals("1", scalar("SELECT 1 AS \"a\" WHERE \"a\" = 1"));
        assertEquals("1", scalar("SELECT 1 AS a WHERE \"A\" = 1"));
        assertRefused("SELECT 1 AS \"a\" WHERE a = 1", "invalid identifier 'A'");
    }

    /** An item written without an alias publishes its own source text, quotable verbatim. */
    @Test
    public void anUnaliasedItemPublishesItsSourceText() {
        assertEquals("1", scalar("SELECT 1 WHERE \"1\" = 1"));
        assertEquals("2", scalar("SELECT 1 + 1 WHERE \"1 + 1\" = 2"));
    }

    /** A name the scope cannot answer is reported at its own place, ahead of every clause rule. */
    @Test
    public void anUnknownNameIsReportedWhereItStands() {
        assertRefused("SELECT 1 AS a WHERE b = 1",
            "error line 1 at position 20\ninvalid identifier 'B'");
        assertRefused("SELECT 1 AS a WHERE b",
            "error line 1 at position 20\ninvalid identifier 'B'");
        assertRefused("SELECT 1 AS a WHERE a = 1 AND b = 2",
            "error line 1 at position 30\ninvalid identifier 'B'");
        assertRefused("SELECT 1 AS a WHERE MAX(b) = 1",
            "error line 1 at position 24\ninvalid identifier 'B'");
        assertRefused("SELECT 1 AS a WHERE MAX(a) = 1 AND b = 1",
            "error line 1 at position 35\ninvalid identifier 'B'");
    }

    /** Two items of one name make it ambiguous — but only where the predicate reads it. */
    @Test
    public void twoItemsOfOneNameAreAmbiguous() {
        assertEquals("1", scalar("SELECT 1 AS a, 2 AS a"));
        assertEquals("1", scalar("SELECT 1 AS a, 2 AS a WHERE 1 = 1"));
        assertRefused("SELECT 1 AS a, 2 AS a WHERE a = 1", "ambiguous column name 'A'");
        assertRefused("SELECT 1 AS a, 1 AS A WHERE a = 1", "ambiguous column name 'A'");
        assertRefused("SELECT 1 AS a, 2 AS a WHERE MAX(a) = 1", "ambiguous column name 'A'");
        assertRefused("SELECT 1 AS a, 2 AS a WHERE a", "ambiguous column name 'A'");
    }

    /** The predicate keeps the clause rules: no aggregate, and no VARCHAR or NUMBER condition. */
    @Test
    public void thePredicateKeepsTheClauseRules() {
        assertRefused("SELECT 1 AS a WHERE MAX(a) = 1",
            "Invalid aggregate function in where clause [MAX(A)]");
        assertRefused("SELECT 1 AS a WHERE MIN(a) = 1",
            "Invalid aggregate function in where clause [MIN(A)]");
        assertRefused("SELECT 1 AS a WHERE COUNT(*) = 1",
            "Invalid aggregate function in where clause [COUNT(*)]");
        assertRefused("SELECT 1 AS a WHERE a", "Invalid data type [NUMBER(1,0)] for predicate [A]");
        assertRefused("SELECT 'x' AS a WHERE a", "Invalid data type [VARCHAR(1)] for predicate [A]");
    }

    /** A BOOLEAN item IS a condition, and NOT over a number still converts. */
    @Test
    public void aBooleanItemIsACondition() {
        assertEquals("true", scalar("SELECT TRUE AS a WHERE a").toLowerCase());
        assertEquals("1", scalar("SELECT 1 AS a WHERE IFF(a = 1, TRUE, FALSE)"));
        assertNoRow("SELECT 1 AS a WHERE NOT a");
    }

    /** The scope ends at the select's own clauses: a query nested in the predicate cannot see it. */
    @Test
    public void aNestedQueryCannotSeeTheScope() {
        assertRefused("SELECT 1 AS a WHERE (SELECT a) = 1", "invalid identifier 'A'");
        assertRefused("SELECT 1 AS a WHERE EXISTS (SELECT 1 WHERE a = 1)", "invalid identifier 'A'");
        assertEquals("1", scalar("SELECT 1 AS a WHERE a IN (SELECT 1)"));
        assertEquals("1", scalar("SELECT 1 AS a WHERE a = (SELECT 1)"));
        assertEquals("1", scalar("SELECT 1 WHERE EXISTS (SELECT 1 AS a WHERE a = 1)"));
        assertEquals("1", scalar("SELECT (SELECT 1 AS a WHERE a > 0) AS x"));
    }

    /** The other clauses of the same select read the names as they always did. */
    @Test
    public void theOtherClausesKeepReadingTheNames() {
        assertEquals("1", scalar("SELECT 1 AS a HAVING a = 1"));
        assertEquals("1", scalar("SELECT 1 AS a GROUP BY a"));
        assertEquals("1", scalar("SELECT 1 AS a ORDER BY a"));
        assertEquals("1", scalar("SELECT 1 AS a WHERE a > 0 GROUP BY a"));
        assertEquals("1", scalar("SELECT DISTINCT 1 AS a WHERE a = 1"));
        assertRefused("SELECT 1 AS a HAVING b = 1", "invalid identifier 'B'");
    }
}
