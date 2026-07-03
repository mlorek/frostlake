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

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SAMPLE / TABLESAMPLE row sampling. Asserted via deterministic cases: 100% keeps all rows, 0% keeps none,
 * fixed {@code n ROWS} keeps exactly n (capped at the table size), and a REPEATABLE/SEED makes fractional
 * sampling reproducible. BERNOULLI / ROW / SYSTEM methods all parse and run.
 */
public class SampleClauseTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE s (id INTEGER)");
        for (int i = 1; i <= 10; i++) {
            engine.execute("INSERT INTO s VALUES (" + i + ")");
        }
    }

    private long count(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void sampleHundredPercentKeepsAllRows() {
        assertEquals(10, count("SELECT COUNT(*) FROM s SAMPLE (100)"));
    }

    @Test
    public void sampleZeroPercentKeepsNoRows() {
        assertEquals(0, count("SELECT COUNT(*) FROM s SAMPLE (0)"));
    }

    @Test
    public void sampleFixedRowsKeepsExactlyThatMany() {
        assertEquals(3, count("SELECT COUNT(*) FROM s SAMPLE (3 ROWS)"));
    }

    @Test
    public void sampleFixedRowsCappedAtTableSize() {
        assertEquals(10, count("SELECT COUNT(*) FROM s SAMPLE (100 ROWS)"));
    }

    @Test
    public void tablesampleKeywordIsAccepted() {
        assertEquals(4, count("SELECT COUNT(*) FROM s TABLESAMPLE (4 ROWS)"));
    }

    @Test
    public void sampleMethodsParseAndRun() {
        assertEquals(10, count("SELECT COUNT(*) FROM s SAMPLE BERNOULLI (100)"));
        assertEquals(10, count("SELECT COUNT(*) FROM s SAMPLE ROW (100)"));
        assertEquals(10, count("SELECT COUNT(*) FROM s SAMPLE SYSTEM (100)"));
    }

    @Test
    public void repeatableSeedIsDeterministic() {
        final Set<Object> first = idsOf("SELECT id FROM s SAMPLE (50) REPEATABLE (42)");
        final Set<Object> second = idsOf("SELECT id FROM s SAMPLE (50) REPEATABLE (42)");
        assertEquals(first, second, "the same seed must reproduce the same sampled rows");
        // SEED is an accepted synonym for REPEATABLE.
        final ResultSet rs = engine.executeQuery("SELECT id FROM s SAMPLE (50) SEED (42)");
        assertTrue(rs.getRowCount() <= 10);
    }

    private Set<Object> idsOf(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final Set<Object> ids = new HashSet<>();
        for (int i = 0; i < rs.getRowCount(); i++) {
            ids.add(rs.getRows().get(i).getValue(0));
        }
        return ids;
    }
}
