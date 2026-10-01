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
 * The limits a correlated subquery may carry: an OFFSET of 0 or NULL keeps the one row of an aggregate or a
 * FROM-less select; a LIMIT, FETCH or TOP of 0 folds a subquery over a FROM away — answered whatever its shape —
 * unless its select list is an aggregate without a GROUP BY or reads the outer row; and BOOLAND_AGG or BOOLOR_AGG
 * over a condition the statistics settle is folded like a constant beside an outer name. Every cell is
 * live-verified.
 */
public class CorrelatedSubqueryLimitShapeTest extends BaseDatabaseTest {

    private static final String UNSUPPORTED =
        "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position ";

    @BeforeEach
    public void createTables() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
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
        return answer("SELECT id, " + subquery + " AS x FROM fz ORDER BY id");
    }

    @Test
    public void anOffsetOfZeroOrNullKeepsTheRow() {
        assertEquals("5:65|7:67", perRow("(SELECT MAX(v) + fz.id FROM g ORDER BY 1 LIMIT 1 OFFSET 0)"));
        assertEquals("5:65|7:67", perRow("(SELECT MAX(v) + fz.id FROM g LIMIT 1 OFFSET 0)"));
        assertEquals("5:65|7:67", perRow("(SELECT MAX(v) + fz.id FROM g LIMIT 2 OFFSET 0)"));
        assertEquals("5:65|7:67", perRow("(SELECT MAX(v) + fz.id FROM g LIMIT 1 OFFSET NULL)"));
        assertEquals("5:65|7:67", perRow("(SELECT MAX(v) + fz.id FROM g OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY)"));
        assertEquals("5:6|7:8", perRow("(SELECT fz.id + 1 LIMIT 1 OFFSET 0)"));
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT MAX(v) FROM g WHERE g.id = fz.id LIMIT 1 OFFSET 0)"));
    }

    @Test
    public void aLimitOfZeroFoldsASubqueryOverAFromAway() {
        assertEquals("5:null|7:null", perRow("(SELECT v FROM g WHERE g.id = fz.id LIMIT 0)"));
        assertEquals("5:null|7:null", perRow("(SELECT v FROM g WHERE g.id + fz.id = 10 LIMIT 0)"));
        assertEquals("5:null|7:null", perRow("(SELECT v FROM g WHERE g.id = fz.id ORDER BY v LIMIT 0)"));
        assertEquals("5:null|7:null", perRow("(SELECT v FROM g WHERE g.id = fz.id GROUP BY v LIMIT 0)"));
        assertEquals("5:null|7:null", perRow("(SELECT MAX(v) FROM g WHERE g.id = fz.id GROUP BY g.id LIMIT 0)"));
        assertEquals("5:null|7:null", perRow("(SELECT TOP 0 v FROM g WHERE g.id = fz.id)"));
        assertEquals("5:null|7:null", perRow("(SELECT v FROM g WHERE g.id = fz.id FETCH FIRST 0 ROWS ONLY)"));
        assertEquals("", answer("SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.id = fz.id LIMIT 0)"));
        assertEquals("", answer("SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.id + fz.id = 10 LIMIT 0)"));
        assertEquals("", answer("SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.id = fz.id LIMIT 0 OFFSET 1)"));
        assertEquals("",
            answer("SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.id = fz.id FETCH FIRST 0 ROWS ONLY)"));
        assertEquals("", answer("SELECT id FROM fz WHERE EXISTS (SELECT TOP 0 1 FROM g WHERE g.id = fz.id)"));
        assertEquals("", answer("SELECT id FROM fz WHERE id IN (SELECT id FROM g WHERE g.id = fz.id LIMIT 0)"));
        assertEquals("5|7",
            answer("SELECT id FROM fz WHERE NOT EXISTS (SELECT 1 FROM g WHERE g.id = fz.id LIMIT 0) ORDER BY id"));
    }

    @Test
    public void aLimitOfZeroBesideAnAggregateOrAnOuterNameIsRefused() {
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT MAX(v) + fz.id FROM g LIMIT 0)"));
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT TOP 0 MAX(v) + fz.id FROM g)"));
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT MAX(v) + fz.id FROM g FETCH FIRST 0 ROWS ONLY)"));
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT MAX(v) FROM g WHERE g.id = fz.id LIMIT 0)"));
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT COUNT(*) FROM g WHERE g.id = fz.id LIMIT 0)"));
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT SUM(v) + fz.id FROM g LIMIT 0)"));
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT v + fz.id FROM g LIMIT 0)"));
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT fz.id + 1 LIMIT 0)"));
        assertEquals(UNSUPPORTED + "24",
            answer("SELECT id FROM fz WHERE EXISTS (SELECT MAX(v) FROM g WHERE g.id = fz.id LIMIT 0)"));
    }

    @Test
    public void aBooleanAggregateTheStatisticsSettleIsFoldedBesideAnOuterName() {
        assertEquals("5:6|7:8", perRow("(SELECT BOOLAND_AGG(v > 0)::INT + fz.id FROM g)"));
        assertEquals("5:6|7:8", perRow("(SELECT BOOLOR_AGG(v > 0)::INT + fz.id FROM g)"));
        assertEquals("5:5|7:7", perRow("(SELECT BOOLAND_AGG(v > 100)::INT + fz.id FROM g)"));
        assertEquals("5:6|7:8", perRow("(SELECT BOOLAND_AGG(TRUE)::INT + fz.id FROM g)"));
        assertEquals("5:6|7:8", perRow("(SELECT BOOLAND_AGG(v IS NOT NULL)::INT + fz.id FROM g)"));
        assertEquals("5:6|7:8", perRow("(SELECT IFF(BOOLAND_AGG(v > 0), 1, 0) + fz.id FROM g)"));
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT BOOLOR_AGG(v > 55)::INT + fz.id FROM g)"));
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT BOOLAND_AGG(v > 55)::INT + fz.id FROM g)"));
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT BOOLXOR_AGG(v > 0)::INT + fz.id FROM g)"));
    }
}
