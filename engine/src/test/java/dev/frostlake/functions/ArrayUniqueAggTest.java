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
 * ARRAY_UNIQUE_AGG(expr) — collects DISTINCT non-NULL scalar inputs (one element per row) into an ARRAY.
 * Element order is not guaranteed, so assertions compare the sorted set of numeric elements.
 */
public class ArrayUniqueAggTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE u (g INTEGER, v INTEGER)");
        engine.execute("INSERT INTO u VALUES (1,1),(1,2),(1,2),(1,NULL),(1,3)");   // dup 2, one NULL
        engine.execute("INSERT INTO u VALUES (2,5),(2,5)");
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
    public void distinctNonNullValues() {
        final ResultSet rs = engine.executeQuery(
            "SELECT g, ARRAY_UNIQUE_AGG(v) FROM u GROUP BY g ORDER BY g");
        assertEquals(2, rs.getRowCount());
        // g = 1: {1,2,3} — duplicate 2 collapsed, NULL dropped
        assertEquals(List.of(1.0, 2.0, 3.0), sortedElems(rs.getRows().get(0).getValue(1)));
        // g = 2: {5}
        assertEquals(List.of(5.0), sortedElems(rs.getRows().get(1).getValue(1)));
    }

    @Test
    public void allNullGroupYieldsEmptyArray() {
        engine.execute("CREATE TABLE u_n (g INTEGER, v INTEGER)");
        engine.execute("INSERT INTO u_n VALUES (9, NULL), (9, NULL)");
        final Object result = engine.executeQuery(
            "SELECT ARRAY_UNIQUE_AGG(v) FROM u_n GROUP BY g").getRows().get(0).getValue(0);
        assertEquals("[]", result.toString());
    }
}
