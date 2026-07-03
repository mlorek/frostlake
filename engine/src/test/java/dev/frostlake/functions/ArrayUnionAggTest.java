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
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ARRAY_UNION_AGG(array) — aggregates ARRAY-valued rows into their duplicate-free union. Element order is
 * not guaranteed by Snowflake, so assertions compare the sorted set of numeric elements.
 */
public class ArrayUnionAggTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE arr_t (g INTEGER, a ARRAY)");
        engine.execute("INSERT INTO arr_t VALUES (1, ARRAY_CONSTRUCT(1, 2, 3))");
        engine.execute("INSERT INTO arr_t VALUES (1, ARRAY_CONSTRUCT(3, 4))");   // overlaps 3
        engine.execute("INSERT INTO arr_t VALUES (1, NULL)");                    // contributes nothing
        engine.execute("INSERT INTO arr_t VALUES (2, ARRAY_CONSTRUCT(5, 5))");   // dup within one array
    }

    private List<Double> sortedElems(final Object arrayResult) {
        final ArrayNode node = ArrayFunctionHelper.parseArray(arrayResult);
        final List<Double> out = new ArrayList<>();
        for (final JsonNode el : node) {
            out.add(el.doubleValue());
        }
        Collections.sort(out);
        return out;
    }

    @Test
    public void unionsAcrossRowsWithDedup() {
        final ResultSet rs = engine.executeQuery(
            "SELECT g, ARRAY_UNION_AGG(a) FROM arr_t GROUP BY g ORDER BY g");
        assertEquals(2, rs.getRowCount());
        // g = 1: union of [1,2,3] and [3,4] with the NULL row ignored → {1,2,3,4}
        assertEquals(List.of(1.0, 2.0, 3.0, 4.0), sortedElems(rs.getRows().get(0).getValue(1)));
        // g = 2: [5,5] collapses to {5}
        assertEquals(List.of(5.0), sortedElems(rs.getRows().get(1).getValue(1)));
    }

    @Test
    public void allNullGroupYieldsEmptyArray() {
        engine.execute("CREATE TABLE arr_n (g INTEGER, a ARRAY)");
        engine.execute("INSERT INTO arr_n VALUES (7, NULL), (7, NULL)");
        final Object result = engine.executeQuery(
            "SELECT ARRAY_UNION_AGG(a) FROM arr_n GROUP BY g").getRows().get(0).getValue(0);
        assertEquals("[]", result.toString());
    }
}
