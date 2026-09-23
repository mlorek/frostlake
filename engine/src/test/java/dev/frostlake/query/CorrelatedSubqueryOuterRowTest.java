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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A correlated subquery reads the outer row in the ON of its joins and in the body of a derived table of its
 * FROM, and a subquery in HAVING reads the group's own row. Live refuses the shapes it cannot turn into a join:
 * an outer join whose ON reads the outer row, and a derived table that reads it beyond a plain filter. A
 * subquery's reference to a column its grouped query neither groups nor aggregates is refused as the query's
 * own reference would be. Every cell is live-verified.
 */
public class CorrelatedSubqueryOuterRowTest extends BaseDatabaseTest {

    private static final String UNSUPPORTED =
        "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position ";

    @BeforeEach
    public void createTables() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE h (k INT, w INT)");
        engine.execute("INSERT INTO h VALUES (5, 500), (7, 700)");
    }

    /** Every row, its cells joined by a colon, the rows by a bar; or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                if (out.length() > 0) {
                    out.append('|');
                }
                for (int i = 0; i < row.getValues().size(); i++) {
                    if (i > 0) {
                        out.append(':');
                    }
                    out.append(row.getValue(i));
                }
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** {@code SELECT id, <subquery> FROM fz ORDER BY id}. */
    private String perRow(final String subquery) {
        return answer("SELECT id, " + subquery + " FROM fz ORDER BY id");
    }

    private static String ungrouped(final String reference) {
        return "SQL compilation error:|[" + reference + "] is not a valid group by expression";
    }

    @Test
    public void aJoinOnReadsTheOuterRow() {
        assertEquals("5:50|7:50", perRow("(SELECT SUM(v) FROM g JOIN h ON h.k = g.id AND h.w > fz.id)"));
        assertEquals("5:1|7:0", perRow("(SELECT COUNT(*) FROM g JOIN h ON h.k = g.id AND h.w > fz.id * 90)"));
        assertEquals("5:220|7:220", perRow("(SELECT SUM(v) FROM g JOIN h ON h.k = g.id OR h.w > fz.id)"));
        assertEquals("5:null|7:50", perRow("(SELECT SUM(v) FROM g JOIN h ON h.k = g.id AND fz.id > 6)"));
        assertEquals("5:2|7:2", perRow("(SELECT COUNT(*) FROM g JOIN h ON h.k = fz.id)"));
        assertEquals("5", answer("SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g JOIN h ON h.k = g.id AND h.k = fz.id)"
            + " ORDER BY id"));
        assertEquals("5", answer("SELECT id FROM fz WHERE id IN (SELECT g.id FROM g JOIN h ON h.k = g.id AND h.w > fz.id)"
            + " ORDER BY id"));
        assertEquals("5:1|7:0", answer("""
            SELECT fz.id, l.c FROM fz, LATERAL (SELECT COUNT(*) AS c FROM g JOIN h ON h.k = g.id AND h.k = fz.id) l
            ORDER BY 1"""));
    }

    @Test
    public void anOuterJoinWhoseOnReadsTheOuterRowIsRefused() {
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT COUNT(*) FROM g LEFT JOIN h ON h.k = g.id AND h.k = fz.id)"));
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT SUM(v) FROM g RIGHT JOIN h ON h.k = g.id AND h.w > fz.id)"));
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT SUM(v) FROM g FULL JOIN h ON h.k = g.id AND h.w > fz.id)"));
        assertEquals(UNSUPPORTED + "12",
            perRow("(SELECT SUM(v) FROM g JOIN h ON h.k = g.id LEFT JOIN h h2 ON h2.k = fz.id)"));
        assertEquals(UNSUPPORTED + "11", perRow("EXISTS (SELECT 1 FROM g LEFT JOIN h ON h.k = g.id AND h.k = fz.id)"));
        assertEquals("5:50|7:null", perRow("(SELECT SUM(v) FROM g LEFT JOIN h ON h.k = g.id WHERE g.id = fz.id)"));
    }

    @Test
    public void aDerivedTableReadsTheOuterRow() {
        assertEquals("5:50|7:null", perRow("(SELECT MAX(v) FROM (SELECT v FROM g WHERE g.id = fz.id))"));
        assertEquals("5:1|7:0", perRow("(SELECT COUNT(*) FROM (SELECT v FROM g WHERE g.id = fz.id))"));
        assertEquals("5:50|7:null", perRow("(SELECT MAX(v) FROM (SELECT v FROM g WHERE g.id = fz.id) d WHERE d.v > 0)"));
        assertEquals("5:50|7:null", perRow("(SELECT MAX(v) FROM (SELECT v FROM g WHERE g.id = fz.id) d JOIN h ON h.k = 5)"));
        assertEquals("5:50|7:null", perRow("(SELECT MAX(v) FROM (SELECT v FROM (SELECT v, id FROM g) WHERE id = fz.id))"));
        assertEquals("5:1|7:0",
            perRow("(SELECT COUNT(*) FROM (SELECT v FROM g WHERE g.id = fz.id) d, h WHERE h.k = d.v / 10)"));
        assertEquals("5", answer("SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM (SELECT v FROM g WHERE g.id = fz.id))"
            + " ORDER BY id"));
        assertEquals("5", answer("SELECT id FROM fz WHERE id IN (SELECT d.id FROM (SELECT id FROM g WHERE g.v > fz.id * 9) d)"
            + " ORDER BY id"));
        assertEquals("5:50|7:null", answer("""
            SELECT fz.id, l.m FROM fz, LATERAL (SELECT MAX(v) AS m FROM (SELECT v FROM g WHERE g.id = fz.id)) l
            ORDER BY 1"""));
    }

    @Test
    public void aDerivedTableReadingTheOuterRowBeyondAPlainFilterIsRefused() {
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT MAX(x) FROM (SELECT v + fz.id AS x FROM g))"));
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT MAX(v) FROM (SELECT v FROM g WHERE g.id + fz.id = 10))"));
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT MAX(v) FROM (SELECT v FROM g WHERE fz.b))"));
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT MAX(v) FROM (SELECT v FROM g WHERE g.id = fz.id LIMIT 1))"));
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT MAX(v) FROM (SELECT v FROM g WHERE g.id = fz.id GROUP BY v))"));
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT MAX(v) FROM (SELECT DISTINCT v FROM g WHERE g.id = fz.id))"));
        assertEquals(UNSUPPORTED + "12",
            perRow("(SELECT MAX(v) FROM (SELECT v FROM g WHERE g.id = fz.id UNION ALL SELECT 1))"));
    }

    @Test
    public void aHavingSubqueryReadsTheGroupRow() {
        assertEquals("5", answer("SELECT id FROM fz GROUP BY id HAVING (SELECT MAX(v) FROM g WHERE g.id = fz.id) > 0"));
        assertEquals("7:1",
            answer("SELECT id, COUNT(*) FROM fz GROUP BY id HAVING (SELECT COUNT(*) FROM g WHERE g.id = fz.id) = 0"));
        assertEquals("5", answer("SELECT id FROM fz GROUP BY id HAVING EXISTS (SELECT 1 FROM g WHERE g.id = fz.id)"));
        assertEquals("7", answer("SELECT id FROM fz GROUP BY id HAVING NOT EXISTS (SELECT 1 FROM g WHERE g.id = fz.id)"));
        assertEquals("5", answer("SELECT id FROM fz GROUP BY id HAVING id IN (SELECT g.id FROM g WHERE g.v > fz.id)"));
        assertEquals("5",
            answer("SELECT id FROM fz GROUP BY id HAVING COUNT(*) = (SELECT COUNT(*) FROM g WHERE g.id = fz.id)"));
        assertEquals("7", answer("SELECT id FROM fz GROUP BY id HAVING (SELECT fz.id + 1) > 6"));
        assertEquals("5|7",
            answer("SELECT id FROM fz GROUP BY id HAVING (SELECT MAX(v) FROM g WHERE g.id = id) > 0 ORDER BY id"));
        assertEquals("5:1",
            answer("SELECT id, COUNT(*) FROM fz GROUP BY ALL HAVING (SELECT COUNT(*) FROM g WHERE g.id = fz.id) = 1"));
        assertEquals("5:50|7:null", answer("SELECT id, (SELECT MAX(v) FROM g WHERE g.id = fz.id) FROM fz GROUP BY id ORDER BY id"));
    }

    @Test
    public void aSubqueryReadingAnUngroupedColumnIsRefused() {
        assertEquals(ungrouped("FZ.ID"),
            answer("SELECT b, COUNT(*) FROM fz GROUP BY b HAVING (SELECT COUNT(*) FROM g WHERE g.id = fz.id) > 0"));
        assertEquals(ungrouped("FZ.ID"), answer("SELECT b FROM fz GROUP BY b HAVING EXISTS (SELECT 1 FROM g WHERE g.id = fz.id)"));
        assertEquals(ungrouped("FZ.B"), answer("SELECT id FROM fz GROUP BY id HAVING (SELECT COUNT(*) FROM g WHERE b) > 0"));
        assertEquals(ungrouped("F.ID"),
            answer("SELECT b, COUNT(*) FROM fz f GROUP BY b HAVING (SELECT COUNT(*) FROM g WHERE g.id = f.id) > 0"));
        assertEquals(ungrouped("FZ.ID"),
            answer("SELECT id + 1 AS k FROM fz GROUP BY k HAVING (SELECT COUNT(*) FROM g WHERE g.id = fz.id) >= 0"));
        assertEquals(ungrouped("FZ.ID"),
            answer("SELECT COUNT(*) FROM fz HAVING (SELECT COUNT(*) FROM g WHERE g.id = fz.id) > 0"));
        assertEquals(ungrouped("FZ.ID"),
            answer("SELECT b FROM fz GROUP BY b HAVING (SELECT COUNT(*) FROM g WHERE g.id = fz.id AND fz.b) > 0"));
        assertEquals(ungrouped("FZ.B"),
            answer("SELECT id FROM fz GROUP BY id HAVING (SELECT COUNT(*) FROM g WHERE g.id = fz.id AND fz.b) > 0"));
        assertEquals(ungrouped("FZ.ID"), answer("SELECT b FROM fz GROUP BY b HAVING b IN (SELECT fz.id > 5 FROM g)"));
        assertEquals(ungrouped("FZ.ID"),
            answer("SELECT b FROM fz GROUP BY ROLLUP (b) HAVING (SELECT COUNT(*) FROM g WHERE g.id = fz.id) >= 0"));
        assertEquals("SQL compilation error: error line 1 at position 47|'FZ.ID' in select clause is neither an aggregate"
            + " nor in the group by clause.",
            answer("SELECT b, (SELECT COUNT(*) FROM g WHERE g.id = fz.id) FROM fz GROUP BY b"));
    }
}
