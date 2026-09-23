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
 * An aggregate subquery whose items read the row of the query around it beside the aggregate answers per
 * outer row: {@code (SELECT MAX(v) + fz.id FROM g)} is 65 for id 5 and 67 for id 7. The outer name is read
 * from the outer row, never from the inner table's column of the same name, under a scalar subquery in the
 * select list, WHERE, ORDER BY and UPDATE SET, and under a LATERAL whose query groups. Every cell is
 * live-verified.
 */
public class CorrelatedAggregateOuterReadTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTables() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE, 'a'), (7, FALSE, 'b')");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
        engine.execute("INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
    }

    /** Every row, its cells joined by a colon, the rows by a bar. */
    private String rows(final String sql) {
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
    }

    @Test
    public void anOuterNameBesideTheAggregateIsReadPerRow() {
        assertEquals("5:65|7:67", rows("SELECT id, (SELECT MAX(v) + fz.id FROM g) AS x FROM fz ORDER BY id"));
        assertEquals("5:7|7:9", rows("SELECT id, (SELECT COUNT(*) + fz.id FROM g) AS x FROM fz ORDER BY id"));
        assertEquals("5:55|7:57", rows("SELECT id, (SELECT fz.id + MIN(v) FROM g) FROM fz ORDER BY id"));
        assertEquals("5:67|7:69", rows("SELECT id, (SELECT MAX(v) + fz.id + COUNT(*) FROM g) FROM fz ORDER BY id"));
        assertEquals("5:7|7:9", rows("SELECT id, (SELECT COUNT(v) + fz.id FROM g) FROM fz ORDER BY id"));
        assertEquals("5:10|7:14", rows("SELECT id, (SELECT COUNT(*) * fz.id FROM g) FROM fz ORDER BY id"));
        assertEquals("5:60a|7:60b", rows("SELECT id, (SELECT MAX(v) || fz.s FROM g) FROM fz ORDER BY id"));
        assertEquals("5:50|7:60",
            rows("SELECT id, (SELECT CASE WHEN fz.id > 6 THEN MAX(v) ELSE MIN(v) END FROM g) FROM fz ORDER BY id"));
        assertEquals("5:60|7:50", rows("SELECT id, (SELECT IFF(fz.b, MAX(v), MIN(v)) FROM g) FROM fz ORDER BY id"));
        assertEquals("5:61|7:60", rows("SELECT id, (SELECT MAX(v) + b::INT FROM g) FROM fz ORDER BY id"));
        assertEquals("7:67", rows("SELECT id, (SELECT MAX(v) + fz.id FROM g) FROM fz WHERE id = 7"));
        assertEquals("5:66|7:68", rows("SELECT id, (SELECT (SELECT MAX(v) + fz.id FROM g) + 1) FROM fz ORDER BY id"));
        assertEquals("7:67|5:65", rows("SELECT id, (SELECT MAX(v) + fz.id AS m FROM g) AS x FROM fz ORDER BY x DESC"));
        assertEquals("5:60|7:null",
            rows("SELECT id, (SELECT MAX(v) FROM g HAVING MAX(v) > fz.id * 10) FROM fz ORDER BY id"));
    }

    @Test
    public void theOuterReadDecidesFiltersOrderAndUpdates() {
        assertEquals("7", rows("SELECT id FROM fz WHERE (SELECT COUNT(*) + fz.id FROM g) > 8 ORDER BY id"));
        assertEquals("5|7", rows("SELECT id FROM fz WHERE id + 60 = (SELECT MAX(v) + fz.id FROM g) ORDER BY id"));
        assertEquals("7", rows("SELECT id FROM fz WHERE (SELECT MAX(v) + fz.id FROM g) = 67"));
        assertEquals("7|5", rows("SELECT id FROM fz ORDER BY (SELECT MAX(v) - fz.id * 20 FROM g)"));
        engine.execute("UPDATE fz SET s = (SELECT MAX(v) + fz.id FROM g)::VARCHAR");
        assertEquals("5:65|7:67", rows("SELECT id, s FROM fz ORDER BY id"));
    }

    @Test
    public void aLateralGroupReadsItsOwnOuterRow() {
        assertEquals("5:65|7:67",
            rows("SELECT fz.id, l.x FROM fz, LATERAL (SELECT MAX(v) + fz.id AS x FROM g) l ORDER BY 1"));
        assertEquals("5:7|7:9",
            rows("SELECT fz.id, l.x FROM fz, LATERAL (SELECT COUNT(*) + fz.id AS x FROM g) l ORDER BY 1"));
        assertEquals("5:60|7:50",
            rows("SELECT fz.id, l.x FROM fz, LATERAL (SELECT IFF(fz.b, MAX(v), MIN(v)) AS x FROM g) l ORDER BY 1"));
        assertEquals("5:55|5:65|7:57|7:67", rows("""
            SELECT fz.id, l.x FROM fz, LATERAL (SELECT g.s, MAX(v) + fz.id AS x FROM g GROUP BY g.s) l
            ORDER BY 1, 2"""));
        assertEquals("5:aa|5:ca|7:ab|7:cb", rows("""
            SELECT fz.id, l.x FROM fz, LATERAL (SELECT g.s || fz.s AS x, COUNT(*) AS n FROM g GROUP BY g.s) l
            ORDER BY 1, 2"""));
        assertEquals("5:6|5:6|5:7|7:8|7:8|7:9", rows("""
            SELECT fz.id, l.x FROM fz, LATERAL (SELECT g.s, COUNT(*) + fz.id AS x FROM g GROUP BY ROLLUP (g.s)) l
            ORDER BY 1, 2"""));
    }
}
