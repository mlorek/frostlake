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
 * The correlated subquery shapes live refuses as an unsupported subquery type, and their neighbours it answers:
 * an outer name beside the aggregates of a select list is answered only beside COUNT, or MIN and MAX over a
 * value that is not text, over one plain relation with no filter; an EXISTS whose body reads the outer row is
 * refused inside a subquery's filter; a window call or a windowed QUALIFY is refused in a correlated scalar
 * subquery. A window inside any subquery is that subquery's own. Every cell is live-verified.
 */
public class CorrelatedAggregateShapeTest extends BaseDatabaseTest {

    private static final String UNSUPPORTED =
        "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position ";

    @BeforeEach
    public void createTables() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE, 'a'), (7, FALSE, 'b')");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
        engine.execute("INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        engine.execute("CREATE OR REPLACE TABLE h (k INT, w INT)");
        engine.execute("INSERT INTO h VALUES (5, 500), (7, 700)");
        engine.execute("CREATE OR REPLACE VIEW vg AS SELECT id, v FROM g");
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

    @Test
    public void anOuterNameBesideCountMinOrMaxIsAnswered() {
        assertEquals("5:9|7:11", perRow("(SELECT COUNT(v) + COUNT(*) + fz.id FROM g)"));
        assertEquals("5:305|7:307", perRow("(SELECT MIN(id) * MAX(v) + fz.id FROM g)"));
        assertEquals("5:66|7:68", perRow("(SELECT MAX(v + 1) + fz.id FROM g)"));
        assertEquals("5:60a|7:60b", perRow("(SELECT MAX(v)::VARCHAR || fz.s FROM g)"));
        assertEquals("5:65|7:67", perRow("(SELECT MAX(DISTINCT v) + fz.id FROM g)"));
        assertEquals("5:7|7:9", perRow("(SELECT COUNT(s) + fz.id FROM g)"));
        assertEquals("5:65|7:67", perRow("(SELECT MAX(gg.v) + fz.id FROM g gg)"));
        assertEquals("5:65|7:67", perRow("(SELECT MAX(v) + fz.id FROM vg)"));
        assertEquals("5:66|7:68", perRow("(SELECT MAX(v) + fz.id FROM (SELECT v + 1 AS v FROM g))"));
        assertEquals("5:7|7:9", perRow("(SELECT COUNT(*) + fz.id FROM g ORDER BY 1)"));
        assertEquals("5:65|7:67", perRow("(SELECT MAX(v) + fz.id FROM g LIMIT 1)"));
        assertEquals("5:65|7:67", perRow("(SELECT TOP 1 MAX(v) + fz.id FROM g)"));
        assertEquals("5:60|7:60", perRow("(SELECT GREATEST(MAX(v), fz.id) FROM g)"));
        assertEquals("5:5|7:7", perRow("(SELECT IFF(COUNT(*) > 1, fz.id, 0) FROM g)"));
        assertEquals("5:5|7:7", perRow("(SELECT CASE WHEN MAX(v) > 55 THEN fz.id END FROM g)"));
    }

    @Test
    public void anyOtherShapeBesideTheOuterNameIsRefused() {
        final String[] refused = {
            "(SELECT SUM(v) + fz.id FROM g)",
            "(SELECT AVG(v) + fz.id FROM g)",
            "(SELECT COUNT(DISTINCT v) + fz.id FROM g)",
            "(SELECT ANY_VALUE(v) * 0 + fz.id FROM g)",
            "(SELECT MEDIAN(v) + fz.id FROM g)",
            "(SELECT COUNT_IF(v > 0) + fz.id FROM g)",
            "(SELECT MAX_BY(v, id) + fz.id FROM g)",
            "(SELECT MAX(v) + SUM(v) * 0 + fz.id FROM g)",
            "(SELECT MAX(s) || fz.s FROM g)",
            "(SELECT MAX(TO_VARCHAR(v)) || fz.s FROM g)",
            "(SELECT MAX(v) + fz.id FROM g WHERE TRUE)",
            "(SELECT COUNT(*) + fz.id FROM g WHERE g.id < 100)",
            "(SELECT MAX(g.v) + fz.id FROM g, h)",
            "(SELECT MAX(g.v) + fz.id FROM g JOIN g g2 ON g.id = g2.id)",
            "(SELECT MAX(v) + fz.id FROM (SELECT v FROM g WHERE v > 0))",
            "(SELECT MAX(x) + fz.id FROM (SELECT 1 AS x))",
            "(SELECT COUNT(*) + fz.id FROM TABLE(FLATTEN(input => [1,2])))",
            "(SELECT MAX(v) + fz.id FROM g SAMPLE (100))",
            "(SELECT MAX(v) + fz.id FROM g LIMIT 0)",
            "(SELECT TOP 0 MAX(v) + fz.id FROM g)",
            "(SELECT MAX(v) + fz.id AS m FROM g HAVING m > 66)",
        };
        for (final String subquery : refused) {
            assertEquals(UNSUPPORTED + "12", perRow(subquery), subquery);
        }
    }

    @Test
    public void aNestedExistsReadingTheOuterRowIsRefused() {
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT MAX(v) FROM g WHERE EXISTS (SELECT 1 FROM h WHERE h.k = fz.id))"));
        assertEquals(UNSUPPORTED + "12",
            perRow("(SELECT MAX(v) FROM g WHERE NOT EXISTS (SELECT 1 FROM h WHERE h.k = fz.id))"));
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT MAX(v) FROM g HAVING EXISTS (SELECT 1 FROM h WHERE h.k = fz.id))"));
        assertEquals(UNSUPPORTED + "11",
            perRow("EXISTS (SELECT 1 FROM g WHERE EXISTS (SELECT 1 FROM h WHERE h.k = fz.id))"));
        assertEquals("5:50|7:null",
            perRow("(SELECT MAX(v) FROM g WHERE g.id = fz.id AND EXISTS (SELECT 1 FROM h WHERE h.k = g.id))"));
        assertEquals("5:50|7:50", perRow("(SELECT MAX(v) FROM g WHERE EXISTS (SELECT 1 FROM h WHERE h.k = g.id))"));
        assertEquals("5:1|7:0", perRow("(SELECT COUNT(*) FROM g WHERE g.id = fz.id AND EXISTS (SELECT 1 FROM h))"));
        assertEquals("5:50|7:50", perRow("(SELECT MAX(v) FROM g WHERE g.id IN (SELECT h.k FROM h WHERE h.w > fz.id))"));
        assertEquals("5:null|7:null", perRow("(SELECT MAX(v) FROM g WHERE g.id = (SELECT MAX(k) FROM h WHERE h.w > fz.id))"));
        assertEquals("5:60|7:60", perRow("(SELECT MAX(v) FROM g WHERE g.id = fz.id OR EXISTS (SELECT 1 FROM h WHERE h.k = 5))"));
    }

    @Test
    public void aWindowInACorrelatedScalarSubqueryIsRefused() {
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT MAX(v) OVER () FROM g WHERE g.id = fz.id)"));
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT ROW_NUMBER() OVER (ORDER BY v) FROM g WHERE g.id = fz.id)"));
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT MAX(v) OVER () + fz.id FROM g LIMIT 1)"));
        assertEquals(UNSUPPORTED + "12", perRow("(SELECT MAX(MAX(v)) OVER () FROM g WHERE g.id = fz.id)"));
        assertEquals(UNSUPPORTED + "12",
            perRow("(SELECT COUNT(*) FROM g WHERE g.id = fz.id QUALIFY ROW_NUMBER() OVER (ORDER BY 1) = 1)"));
        assertEquals("5:true|7:false", perRow("EXISTS (SELECT ROW_NUMBER() OVER (ORDER BY v) FROM g WHERE g.id = fz.id)"));
    }

    @Test
    public void aWindowInsideASubqueryIsItsOwn() {
        assertEquals("5:60|7:60", perRow("(SELECT MAX(v) OVER () FROM g LIMIT 1)"));
        assertEquals("5", answer("SELECT id FROM fz WHERE id IN (SELECT ROW_NUMBER() OVER (ORDER BY v) + 4 FROM g) ORDER BY id"));
        assertEquals("5:2|7:2", perRow("(SELECT COUNT(*) FROM (SELECT ROW_NUMBER() OVER (ORDER BY v) AS r FROM g))"));
        assertEquals("5|7", answer("SELECT id FROM fz WHERE EXISTS (SELECT ROW_NUMBER() OVER (ORDER BY v) FROM g) ORDER BY id"));
        assertEquals("5:61|7:62",
            answer("SELECT id, ROW_NUMBER() OVER (ORDER BY id) + (SELECT MAX(v) OVER () FROM g LIMIT 1) FROM fz ORDER BY id"));
        assertEquals("5|7", answer("SELECT id FROM fz WHERE (SELECT MAX(v) OVER () FROM g LIMIT 1) > 55 ORDER BY id"));
        assertEquals("5|7",
            answer("SELECT id FROM fz GROUP BY id HAVING (SELECT MAX(v) OVER () FROM g LIMIT 1) > 55 ORDER BY id"));
        assertEquals("5", answer("SELECT fz.id FROM fz JOIN g ON g.id = fz.id AND (SELECT MAX(v) OVER () FROM g LIMIT 1) > 55"));
        assertEquals("5|7", answer("SELECT id FROM fz QUALIFY (SELECT MAX(v) OVER () FROM g LIMIT 1) > 55 ORDER BY id"));
        assertEquals("5:50|7:50", perRow("(SELECT SUM(v) OVER () FROM g WHERE g.id = 5)"));
        assertEquals("5:50|7:50", perRow("(SELECT MIN(v) FROM g QUALIFY ROW_NUMBER() OVER (ORDER BY 1) = 1)"));
    }
}
