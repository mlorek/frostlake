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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * LISTAGG semantics: the delimiter defaults to the EMPTY string (Snowflake), the optional second
 * argument sets an explicit delimiter (previously dropped — the executor treated it as a second
 * column and aggregated the wrong one), NULLs are skipped, and it works grouped and ungrouped.
 */
public class ListAggTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE la (k INTEGER, v VARCHAR)");
        engine.execute("INSERT INTO la VALUES (1, 'a'), (1, 'b'), (2, 'c'), (2, NULL)");
    }

    /** First column of the first row as a String (null-safe). */
    private String agg(final String sql) {
        final ResultSet r = engine.executeQuery(sql);
        final Object v = r.getRows().get(0).getValue(0);
        return v == null ? null : v.toString();
    }

    @Test
    public void defaultDelimiterIsEmptyString() {
        // Snowflake's single-argument LISTAGG joins with the empty string, not a comma.
        assertEquals("abc", agg("SELECT LISTAGG(v) AS r FROM la"));
    }

    @Test
    public void explicitDelimiterIsApplied() {
        assertEquals("a|b|c", agg("SELECT LISTAGG(v, '|') AS r FROM la"));
    }

    @Test
    public void delimiterMayContainComma() {
        // The split of value/delimiter must keep a comma inside the delimiter literal intact.
        assertEquals("a, b, c", agg("SELECT LISTAGG(v, ', ') AS r FROM la"));
    }

    @Test
    public void nullValuesAreSkipped() {
        // Group k=2 has ('c', NULL) — only 'c' contributes.
        assertEquals("c", agg("SELECT LISTAGG(v, '|') AS r FROM la WHERE k = 2"));
    }

    @Test
    public void numericValuesAreStringified() {
        assertEquals("1-1-2-2", agg("SELECT LISTAGG(k, '-') AS r FROM la"));
    }

    @Test
    public void groupedListaggAppliesPerGroup() {
        final ResultSet r = engine.executeQuery(
            "SELECT k, LISTAGG(v, '-') AS r FROM la GROUP BY k ORDER BY k");
        assertEquals(2, r.getRowCount());
        assertEquals("a-b", r.getRows().get(0).getValue(r.getColumnIndex("r")).toString());
        assertEquals("c", r.getRows().get(1).getValue(r.getColumnIndex("r")).toString());
    }

    @Test
    public void withinGroupOrdersAscending() {
        assertEquals("a|b|c", agg("SELECT LISTAGG(v, '|') WITHIN GROUP (ORDER BY v) AS r FROM la"));
    }

    @Test
    public void withinGroupOrdersDescending() {
        // Proves the values are reordered (insertion order is a,b,c) rather than concatenated as-scanned.
        assertEquals("c|b|a", agg("SELECT LISTAGG(v, '|') WITHIN GROUP (ORDER BY v DESC) AS r FROM la"));
    }
}
