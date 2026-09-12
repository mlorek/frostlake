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

package dev.frostlake.storage;

import dev.frostlake.BaseDatabaseTest;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An exact NUMBER column holds ONE carrier whatever path a value arrives by — INSERT of a literal,
 * INSERT … VALUES, a VARIANT cast, UPDATE, MERGE, CTAS: a scale-0 column a Long, a scaled column a
 * BigDecimal at its scale. The SQL-visible half is two-sided (DISTINCT, GROUP BY, NULLIF, IN, equality,
 * MIN / MAX, joins, DECODE, casts, ARRAY_AGG DISTINCT all see one number, and a whole value written into
 * NUMBER(10,2) reads back with its two decimals); the carrier classes themselves are the engine's own
 * and are pinned embedded only.
 */
public class ExactCarrierWriteTest extends BaseDatabaseTest {

    @BeforeEach
    public void writeByEveryPath() {
        engine.execute("CREATE TABLE c0 (a NUMBER(38,0), b NUMBER(38,0), d NUMBER(10,2), k VARCHAR)");
        engine.execute("INSERT INTO c0 SELECT 4.4, 9.6, 1.234, 'lit'");
        engine.execute("INSERT INTO c0 SELECT v:x::NUMBER(38,0), v:y::NUMBER(38,0), v:z::NUMBER(10,2), 'var' "
            + "FROM (SELECT PARSE_JSON('{\"x\": 7.2, \"y\": 8, \"z\": 2.5}') AS v)");
        engine.execute("UPDATE c0 SET b = 6.0, d = 3 WHERE a = 4");
        engine.execute("INSERT INTO c0 VALUES (4, 6, 3.00, 'val')");
        engine.execute("MERGE INTO c0 t USING (SELECT 7 AS a) s ON t.a = s.a WHEN MATCHED THEN UPDATE SET b = 8.0");
        engine.execute("CREATE TABLE c1 AS SELECT a, b, d FROM c0");
    }

    private List<Row> rows(final String sql) {
        return engine.executeQuery(sql).getRows();
    }

    private static String cell(final Row row, final int index) {
        return String.valueOf(row.getValue(index));
    }

    @Test
    public void everyWritePathReadsBackTheSameNumbers() {
        final List<Row> all = rows("SELECT a, b, d, k FROM c0 ORDER BY k");
        assertEquals(3, all.size());
        assertEquals("4, 6, 3.00, lit", cell(all.get(0), 0) + ", " + cell(all.get(0), 1) + ", " + cell(all.get(0), 2) + ", " + cell(all.get(0), 3));
        assertEquals("4, 6, 3.00, val", cell(all.get(1), 0) + ", " + cell(all.get(1), 1) + ", " + cell(all.get(1), 2) + ", " + cell(all.get(1), 3));
        assertEquals("7, 8, 2.50, var", cell(all.get(2), 0) + ", " + cell(all.get(2), 1) + ", " + cell(all.get(2), 2) + ", " + cell(all.get(2), 3));
        final Row distinct = rows("SELECT COUNT(DISTINCT a), COUNT(DISTINCT b), COUNT(DISTINCT d) FROM c0").get(0);
        assertEquals("2", cell(distinct, 0));
        assertEquals("2", cell(distinct, 1));
        assertEquals("2", cell(distinct, 2));
        final List<Row> grouped = rows("SELECT a, COUNT(*) FROM c0 GROUP BY a ORDER BY a");
        assertEquals(2, grouped.size());
        assertEquals("4", cell(grouped.get(0), 0));
        assertEquals("2", cell(grouped.get(0), 1));
        assertEquals("7", cell(grouped.get(1), 0));
        assertEquals("1", cell(grouped.get(1), 1));
        final List<Row> unique = rows("SELECT DISTINCT a, b FROM c0 ORDER BY a, b");
        assertEquals(2, unique.size());
        assertEquals("4", cell(unique.get(0), 0));
        assertEquals("6", cell(unique.get(0), 1));
        assertEquals("2", cell(rows("SELECT COUNT(*) FROM c0 WHERE a = 4 AND b = 6 AND d = 3").get(0), 0));
    }

    @Test
    public void equalitySeamsSeeOneNumber() {
        final List<Row> predicates = rows("SELECT NULLIF(a, 4), NULLIF(b, 6), NULLIF(d, 3), a IN (4, 7), b = 6.0, d = 3 FROM c0 ORDER BY k");
        assertNull(predicates.get(0).getValue(0));
        assertNull(predicates.get(0).getValue(1));
        assertNull(predicates.get(0).getValue(2));
        assertNull(predicates.get(1).getValue(0));
        assertNull(predicates.get(1).getValue(1));
        assertNull(predicates.get(1).getValue(2));
        assertEquals("7", cell(predicates.get(2), 0));
        assertEquals("8", cell(predicates.get(2), 1));
        assertEquals("2.50", cell(predicates.get(2), 2));
        for (int r = 0; r < 3; r++) {
            assertEquals("true", cell(predicates.get(r), 3).toLowerCase(), "a IN (4, 7) on row " + r);
        }
        assertEquals("true", cell(predicates.get(0), 4).toLowerCase());
        assertEquals("true", cell(predicates.get(0), 5).toLowerCase());
        assertEquals("false", cell(predicates.get(2), 4).toLowerCase());
        assertEquals("false", cell(predicates.get(2), 5).toLowerCase());
        final Row extremes = rows("SELECT MIN(COALESCE(a, b)), MAX(COALESCE(a, b)), MIN(d), MAX(d), SUM(a), SUM(d) FROM c0").get(0);
        assertEquals("4", cell(extremes, 0));
        assertEquals("7", cell(extremes, 1));
        assertEquals("2.50", cell(extremes, 2));
        assertEquals("3.00", cell(extremes, 3));
        assertEquals("15", cell(extremes, 4));
        assertEquals("8.50", cell(extremes, 5));
        final List<Row> joined = rows("SELECT x.k, y.k FROM c0 x JOIN c0 y ON x.a = y.a AND x.b = y.b AND x.k < y.k ORDER BY 1, 2");
        assertEquals(1, joined.size());
        assertEquals("lit", cell(joined.get(0), 0));
        assertEquals("val", cell(joined.get(0), 1));
        final List<Row> decoded = rows("SELECT DECODE(a, 4, 'four', 'other'), DECODE(d, 3, 'three', 'other') FROM c0 ORDER BY k");
        assertEquals("four", cell(decoded.get(0), 0));
        assertEquals("three", cell(decoded.get(0), 1));
        assertEquals("other", cell(decoded.get(2), 0));
        assertEquals("other", cell(decoded.get(2), 1));
        final List<Row> texts = rows("SELECT a::VARCHAR, b::VARCHAR, d::VARCHAR, TO_JSON(ARRAY_CONSTRUCT(a, b, d)) FROM c0 ORDER BY k");
        assertEquals("[4,6,3]", cell(texts.get(0), 3));
        assertEquals("3.00", cell(texts.get(0), 2));
        assertEquals("[7,8,2.5]", cell(texts.get(2), 3));
        final Row collected = rows("SELECT ARRAY_AGG(DISTINCT a) WITHIN GROUP (ORDER BY a), ARRAY_AGG(DISTINCT d) WITHIN GROUP (ORDER BY d) FROM c0").get(0);
        assertEquals("[4,7]", cell(collected, 0));
        assertEquals("[2.5,3]", cell(collected, 1));
        final List<Row> copied = rows("SELECT COUNT(DISTINCT a), MIN(d), a::VARCHAR FROM c1 GROUP BY a ORDER BY a");
        assertEquals(2, copied.size());
        assertEquals("3.00", cell(copied.get(0), 1));
        assertEquals("2.50", cell(copied.get(1), 1));
    }

    @Test
    public void everyWritePathStoresTheColumnsCarrier() {
        if (isLiveSnowflake()) {
            return;
        }
        for (final String table : new String[] {"c0", "c1"}) {
            for (final Row row : rows("SELECT a, b, d FROM " + table)) {
                assertEquals(Long.class, row.getValue(0).getClass(), table + ".a " + row.getValue(0));
                assertEquals(Long.class, row.getValue(1).getClass(), table + ".b " + row.getValue(1));
                assertEquals(BigDecimal.class, row.getValue(2).getClass(), table + ".d " + row.getValue(2));
                assertEquals(2, ((BigDecimal) row.getValue(2)).scale(), table + ".d scale");
            }
        }
        // Beyond the long range a scale-0 column holds a whole BigDecimal, by every path too.
        engine.execute("CREATE TABLE wide (a NUMBER(38,0))");
        engine.execute("INSERT INTO wide SELECT 12345678901234567890123456789");
        engine.execute("INSERT INTO wide VALUES (99999999999999999999)");
        engine.execute("UPDATE wide SET a = a + 1 WHERE a = 99999999999999999999");
        for (final Row row : rows("SELECT a FROM wide")) {
            assertEquals(BigDecimal.class, row.getValue(0).getClass(), String.valueOf(row.getValue(0)));
            assertEquals(0, ((BigDecimal) row.getValue(0)).scale());
        }
        assertTrue(rows("SELECT a FROM wide WHERE a = 100000000000000000000").size() == 1);
    }
}
