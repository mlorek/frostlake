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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Optional keywords must be exactly that — optional: each pair here runs the same query with and
 * without the optional token (or with defaults spelled out) and asserts identical results, row by
 * row. File-level coverage elsewhere exercises each SPELLING; this class pins the EQUIVALENCE.
 */
public class OptionalKeywordEquivalenceTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE a (k INTEGER, av VARCHAR)");
        engine.execute("CREATE TABLE b (k INTEGER, bv VARCHAR)");
        engine.execute("INSERT INTO a VALUES (1, 'a1'), (2, 'a2'), (3, 'a3')");
        engine.execute("INSERT INTO b VALUES (2, 'b2'), (3, 'b3'), (4, 'b4')");
    }

    private void assertSameResults(final String sqlA, final String sqlB) {
        final ResultSet first = engine.executeQuery(sqlA);
        final ResultSet second = engine.executeQuery(sqlB);
        assertEquals(first.getRowCount(), second.getRowCount(), "row counts differ");
        assertEquals(first.getColumns().size(), second.getColumns().size(), "widths differ");
        for (int r = 0; r < first.getRowCount(); r++) {
            final Row rowA = first.getRows().get(r);
            final Row rowB = second.getRows().get(r);
            for (int c = 0; c < first.getColumns().size(); c++) {
                assertEquals(String.valueOf(rowA.getValue(c)), String.valueOf(rowB.getValue(c)),
                    "row " + r + " col " + c);
            }
        }
    }

    @Test
    public void innerAndOuterKeywordsAreOptional() {
        assertSameResults(
            "SELECT a.k, av, bv FROM a JOIN b ON b.k = a.k ORDER BY a.k",
            "SELECT a.k, av, bv FROM a INNER JOIN b ON b.k = a.k ORDER BY a.k");
        assertSameResults(
            "SELECT a.k, av, bv FROM a LEFT JOIN b ON b.k = a.k ORDER BY a.k",
            "SELECT a.k, av, bv FROM a LEFT OUTER JOIN b ON b.k = a.k ORDER BY a.k");
        assertSameResults(
            "SELECT a.k, b.k, av, bv FROM a FULL JOIN b ON b.k = a.k ORDER BY a.k NULLS LAST, b.k",
            "SELECT a.k, b.k, av, bv FROM a FULL OUTER JOIN b ON b.k = a.k ORDER BY a.k NULLS LAST, b.k");
    }

    @Test
    public void orderByDefaultsSpelledOutChangeNothing() {
        engine.execute("CREATE TABLE n (v INTEGER)");
        engine.execute("INSERT INTO n VALUES (2), (NULL), (1)");
        assertSameResults(
            "SELECT v FROM n ORDER BY v",
            "SELECT v FROM n ORDER BY v ASC NULLS LAST");
        assertSameResults(
            "SELECT v FROM n ORDER BY v DESC",
            "SELECT v FROM n ORDER BY v DESC NULLS FIRST");
    }

    @Test
    public void tempAbbreviatesTemporary() {
        engine.execute("CREATE TEMP TABLE t_short (x INTEGER)");
        engine.execute("CREATE TEMPORARY TABLE t_long (x INTEGER)");
        engine.execute("INSERT INTO t_short VALUES (1)");
        engine.execute("INSERT INTO t_long VALUES (1)");
        assertSameResults("SELECT COUNT(*) FROM t_short", "SELECT COUNT(*) FROM t_long");
    }

    @Test
    public void flattenDefaultsEqualExplicitArguments() {
        engine.execute("CREATE TABLE j (v VARIANT)");
        engine.execute("INSERT INTO j SELECT PARSE_JSON('{\"xs\": [1, 2, 3]}')");
        assertSameResults(
            "SELECT f.value::INTEGER FROM j, LATERAL FLATTEN(input => v:xs) f ORDER BY 1",
            "SELECT f.value::INTEGER FROM j,"
                + " LATERAL FLATTEN(input => v:xs, outer => FALSE, recursive => FALSE, mode => 'BOTH') f"
                + " ORDER BY 1");
    }

    @Test
    public void unionDefaultsToDistinctNotAll() {
        // UNION without ALL deduplicates — the one optional keyword that CHANGES semantics,
        // pinned here from the other side: the default is DISTINCT.
        final ResultSet plain = engine.executeQuery(
            "SELECT k FROM a WHERE k <= 2 UNION SELECT k FROM b WHERE k <= 2 ORDER BY k");
        assertEquals(2, plain.getRowCount());
        final ResultSet all = engine.executeQuery(
            "SELECT k FROM a WHERE k <= 2 UNION ALL SELECT k FROM b WHERE k <= 2 ORDER BY k");
        assertEquals(3, all.getRowCount());
        assertTrue(plain.getRowCount() < all.getRowCount());
    }
}
