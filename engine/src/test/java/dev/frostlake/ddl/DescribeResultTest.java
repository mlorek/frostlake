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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests DESCRIBE RESULT '&lt;queryId&gt;' / LAST_QUERY_ID() — the column metadata of a prior query's cached result.
 */
public class DescribeResultTest extends BaseDatabaseTest {

    private Set<String> columnNames(final ResultSet rs) {
        final Set<String> names = new HashSet<>();
        for (final Row row : rs.getRows()) {
            names.add(String.valueOf(row.getValue(0)).toUpperCase());
        }
        return names;
    }

    @Test
    public void describeResultViaLastQueryId() {
        engine.executeQuery("SELECT 1 AS a, 'x' AS b");
        final Set<String> cols = columnNames(engine.executeQuery("DESCRIBE RESULT LAST_QUERY_ID()"));
        assertTrue(cols.contains("A"), cols.toString());
        assertTrue(cols.contains("B"), cols.toString());
    }

    @Test
    public void describeResultViaId() {
        engine.executeQuery("SELECT 7 AS only_col");
        final ResultSet lq = engine.executeQuery("SELECT LAST_QUERY_ID()");
        final String qid = String.valueOf(lq.getRows().get(0).getValue(0));

        final Set<String> cols = columnNames(engine.executeQuery("DESCRIBE RESULT '" + qid + "'"));
        assertTrue(cols.contains("ONLY_COL"), cols.toString());
    }
}
