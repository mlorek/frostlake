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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An aggregate belongs to the SELECT list and to HAVING — never to WHERE, to a join's ON condition, or
 * to a GROUP BY key, all three of which are evaluated before the grouping that would give it rows. Live
 * names the clause it found one in:
 *
 * <pre>
 *   WHERE SUM(b) &gt; 0            Invalid aggregate function in where clause [SUM(G.B)]
 *   JOIN … ON SUM(g.b) = g2.a   Invalid aggregate function in ON clause [SUM(G.B)]
 *   GROUP BY SUM(b)             [SUM(G.B)] is not a valid group by expression
 *   SUM(ROW_NUMBER() OVER (…))  Window function […] may not appear inside an aggregate function.
 * </pre>
 *
 * <p>Frostlake answered "Unknown function SUM." for the WHERE cases — misleading, since SUM is a
 * function it knows perfectly well — and ACCEPTED the ON and GROUP BY ones outright. The cause was the
 * same in every case: an aggregate reaching a place the aggregate machinery does not run falls through
 * to the SCALAR resolver, which finds no scalar of that name and reports the only thing it can.
 *
 * <p>Live prints the call canonicalised where this prints it as written, so these assert the sentence
 * around the brackets.
 */
public class MisplacedAggregateTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE g (a INT, b INT)");
        engine.execute("INSERT INTO g VALUES (1, 10), (1, 20), (2, 30)");
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', ' ');
        }
    }

    private int value(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return ((Number) rs.getValue(0)).intValue();
    }

    /** WHERE names the clause, for a plain aggregate and for COUNT(*). */
    @Test
    public void whereNamesTheClause() {
        assertTrue(refusal("SELECT a FROM g WHERE SUM(b) > 0")
            .contains("Invalid aggregate function in where clause"),
            refusal("SELECT a FROM g WHERE SUM(b) > 0"));
        assertTrue(refusal("SELECT a FROM g WHERE COUNT(*) > 0")
            .contains("Invalid aggregate function in where clause"),
            refusal("SELECT a FROM g WHERE COUNT(*) > 0"));
    }

    /** Nesting it inside another call changes nothing. */
    @Test
    public void nestingItInsideAnotherCallChangesNothing() {
        assertTrue(refusal("SELECT a FROM g WHERE ABS(SUM(b)) > 0")
            .contains("Invalid aggregate function in where clause"),
            refusal("SELECT a FROM g WHERE ABS(SUM(b)) > 0"));
    }

    /** A join's ON condition names its own clause. */
    @Test
    public void anOnConditionNamesItsOwnClause() {
        assertTrue(refusal("SELECT g.a FROM g JOIN g g2 ON SUM(g.b) = g2.a")
            .contains("Invalid aggregate function in ON clause"),
            refusal("SELECT g.a FROM g JOIN g g2 ON SUM(g.b) = g2.a"));
    }

    /** A GROUP BY key gets the grouped-validation sentence instead. */
    @Test
    public void aGroupByKeyGetsTheGroupedSentence() {
        assertTrue(refusal("SELECT COUNT(*) FROM g GROUP BY SUM(b)")
            .contains("is not a valid group by expression"),
            refusal("SELECT COUNT(*) FROM g GROUP BY SUM(b)"));
    }

    /** A window INSIDE an aggregate has a sentence of its own. */
    @Test
    public void aWindowInsideAnAggregateIsRefused() {
        assertTrue(refusal("SELECT SUM(ROW_NUMBER() OVER (ORDER BY b)) FROM g")
            .contains("may not appear inside an aggregate function."),
            refusal("SELECT SUM(ROW_NUMBER() OVER (ORDER BY b)) FROM g"));
        assertTrue(refusal("SELECT a FROM g WHERE a = (SELECT MAX(ROW_NUMBER() OVER (ORDER BY b)) FROM g)")
            .contains("may not appear inside an aggregate function."),
            refusal("SELECT a FROM g WHERE a = (SELECT MAX(ROW_NUMBER() OVER (ORDER BY b)) FROM g)"));
    }

    /** And the places an aggregate DOES belong are untouched. */
    @Test
    public void thePlacesItBelongsAreUntouched() {
        assertEquals(60, value("SELECT SUM(b) FROM g"));
        assertEquals(3, value("SELECT COUNT(*) FROM g HAVING SUM(b) > 0"));
        assertEquals(2, value("SELECT COUNT(*) FROM g GROUP BY a HAVING SUM(b) > 0"));
    }
}
