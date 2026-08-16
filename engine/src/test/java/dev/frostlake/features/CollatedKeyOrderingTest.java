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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A collation decides what SORTS, what GROUPS and what counts as one value — not only what compares
 * equal. ORDER BY, GROUP BY, DISTINCT, a window's PARTITION BY and ORDER BY, WITHIN GROUP orderings and
 * the MIN / MAX extremes all read the collation of the key they are given, whether it is declared on the
 * column or written into the key. Two rules separate the reported values: a GROUP, a DISTINCT and an
 * aggregate's DISTINCT report the smallest of their folded values by raw text, while MIN and MAX keep
 * the first value they met among those that tie. Live-verified.
 */
public class CollatedKeyOrderingTest extends BaseDatabaseTest {

    /** A table of mixed-case letters, one column plain and one declared case-insensitive. */
    private void createMixedCase() {
        engine.execute("CREATE OR REPLACE TABLE coll_case (s VARCHAR, c VARCHAR COLLATE 'en-ci')");
        engine.execute("INSERT INTO coll_case VALUES ('a','a'),('A','A'),('b','b'),('B','B')");
    }

    /** The query's rows as one string, columns joined by ',' and rows by '|'. */
    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        for (int r = 0; r < rs.getRowCount(); r++) {
            if (r > 0) {
                text.append('|');
            }
            for (int c = 0; c < rs.getColumns().size(); c++) {
                if (c > 0) {
                    text.append(',');
                }
                final Object value = rs.getRows().get(r).getValue(c);
                text.append(value == null ? "NULL" : value.toString());
            }
        }
        return text.toString();
    }

    /** ORDER BY sorts under the key's collation, declared on the column or written into the key. */
    @Test
    public void orderBySortsUnderTheCollation() {
        createMixedCase();
        assertEquals("A|a|B|b", rows("SELECT s FROM coll_case ORDER BY s COLLATE 'en-ci', s"));
        assertEquals("A|a|B|b", rows("SELECT c FROM coll_case ORDER BY c, s"));
        assertEquals("B|b|A|a", rows("SELECT c FROM coll_case ORDER BY c DESC, s"));
        assertEquals("A|a", rows("SELECT s FROM coll_case ORDER BY s COLLATE 'en-ci', s LIMIT 2"));
    }

    /** A locale collation orders punctuation its own way: under 'en', '+' sorts after '-'. */
    @Test
    public void orderBySortsPunctuationUnderTheLocale() {
        engine.execute("CREATE OR REPLACE TABLE coll_punct (s VARCHAR, e VARCHAR COLLATE 'en')");
        engine.execute("INSERT INTO coll_punct VALUES ('-','-'),('+','+')");
        assertEquals("+|-", rows("SELECT s FROM coll_punct ORDER BY s"));
        assertEquals("-|+", rows("SELECT e FROM coll_punct ORDER BY e"));
        assertEquals("-,+", rows("SELECT MAX(s), MAX(e) FROM coll_punct"));
        assertEquals("+,-", rows("SELECT MIN(s), MIN(e) FROM coll_punct"));
    }

    /** Values equal under the collation are ONE group, and one DISTINCT value. */
    @Test
    public void groupingAndDistinctFoldUnderTheCollation() {
        createMixedCase();
        assertEquals("2", rows("SELECT COUNT(DISTINCT s COLLATE 'en-ci') FROM coll_case"));
        assertEquals("2", rows("SELECT COUNT(DISTINCT c) FROM coll_case"));
        assertEquals("A,2|B,2", rows("SELECT s COLLATE 'en-ci' AS k, COUNT(*) FROM coll_case GROUP BY 1 ORDER BY 1"));
        assertEquals("A,2|B,2", rows("SELECT c AS k, COUNT(*) FROM coll_case GROUP BY 1 ORDER BY 1"));
        assertEquals("A|B", rows("SELECT DISTINCT c FROM coll_case ORDER BY 1"));
        assertEquals("A|B", rows("SELECT c FROM coll_case GROUP BY c HAVING COUNT(*) = 2 ORDER BY 1"));
    }

    /** A folded group reports the smallest of its values by RAW text, whatever order they arrived in. */
    @Test
    public void aFoldedGroupReportsItsLeastRawValue() {
        engine.execute("CREATE OR REPLACE TABLE coll_upper_first (c VARCHAR COLLATE 'en-ci')");
        engine.execute("INSERT INTO coll_upper_first VALUES ('A'),('a')");
        engine.execute("CREATE OR REPLACE TABLE coll_lower_first (c VARCHAR COLLATE 'en-ci')");
        engine.execute("INSERT INTO coll_lower_first VALUES ('a'),('A')");
        assertEquals("A,2", rows("SELECT c, COUNT(*) FROM coll_upper_first GROUP BY 1"));
        assertEquals("A,2", rows("SELECT c, COUNT(*) FROM coll_lower_first GROUP BY 1"));
        assertEquals("A", rows("SELECT DISTINCT c FROM coll_upper_first"));
        assertEquals("A", rows("SELECT DISTINCT c FROM coll_lower_first"));

        engine.execute("CREATE OR REPLACE TABLE coll_mixed (c VARCHAR COLLATE 'en-ci')");
        engine.execute("INSERT INTO coll_mixed VALUES ('ab'),('AB'),('Ab')");
        assertEquals("AB,3", rows("SELECT c, COUNT(*) FROM coll_mixed GROUP BY 1"));
    }

    /** MIN and MAX order by the collation, and keep the FIRST of the values that tie under it. */
    @Test
    public void extremesOrderByTheCollationAndKeepTheFirstTie() {
        createMixedCase();
        assertEquals("a,b", rows("SELECT MIN(c), MAX(c) FROM coll_case"));
        assertEquals("A,b", rows("SELECT MIN(s), MAX(s) FROM coll_case"));
        assertEquals("a,b", rows("SELECT MIN(s COLLATE 'en-ci'), MAX(s COLLATE 'en-ci') FROM coll_case"));

        engine.execute("CREATE OR REPLACE TABLE coll_upper_first (c VARCHAR COLLATE 'en-ci')");
        engine.execute("INSERT INTO coll_upper_first VALUES ('Ab'),('aB')");
        assertEquals("Ab,Ab", rows("SELECT MIN(c), MAX(c) FROM coll_upper_first"));
    }

    /** A window's PARTITION BY folds under the collation, and its ORDER BY ranks folded values as peers. */
    @Test
    public void windowKeysFoldUnderTheCollation() {
        createMixedCase();
        assertEquals("A,1|B,1|a,2|b,2",
            rows("SELECT s, ROW_NUMBER() OVER (PARTITION BY s COLLATE 'en-ci' ORDER BY s) FROM coll_case ORDER BY s, 2"));
        assertEquals("a,2|A,2|b,2|B,2",
            rows("SELECT c, COUNT(*) OVER (PARTITION BY c) FROM coll_case ORDER BY c, 2"));
        assertEquals("a,1|A,1|b,2|B,2",
            rows("SELECT c, DENSE_RANK() OVER (ORDER BY c) FROM coll_case ORDER BY 2, c"));
        assertEquals("A|B",
            rows("SELECT s FROM coll_case QUALIFY ROW_NUMBER() OVER (PARTITION BY s COLLATE 'en-ci' ORDER BY s) = 1 ORDER BY s"));
    }

    /** A WITHIN GROUP ordering and an aggregate's own DISTINCT read the collation too. */
    @Test
    public void withinGroupAndAggregateDistinctFoldUnderTheCollation() {
        createMixedCase();
        assertEquals("AaBb", rows("SELECT LISTAGG(c, '') WITHIN GROUP (ORDER BY c, s) FROM coll_case"));
        assertEquals("[\"A\",\"B\"]", rows("SELECT ARRAY_AGG(DISTINCT c) WITHIN GROUP (ORDER BY c) FROM coll_case")
            .replace("\n", "").replace("  ", "").replace(" ", ""));
    }
}
