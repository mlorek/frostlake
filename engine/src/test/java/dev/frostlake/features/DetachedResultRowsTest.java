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
package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.ExecutionResult;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The rows the embedding API answers are the caller's own: changing one changes nothing the engine holds — not the
 * table it was read from, not the table's earlier versions, not a result RESULT_SCAN reads back.
 */
public class DetachedResultRowsTest extends BaseDatabaseTest {

    @BeforeEach
    public void embeddedOnly() {
        assumeFalse(isLiveSnowflake(), "the rows are the embedded engine's own objects");
    }

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (x INT, s VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'a'), (2, 'b')");
    }

    /** The rows of a query, each as its values' text. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " " : "").append(row.getValues());
        }
        return out.toString();
    }

    @Test
    public void changingAReturnedRowChangesNothingElse() {
        final String before = engine.execute("SELECT COUNT(*) FROM t").getQueryId();
        final ExecutionResult earlier = engine.execute("SELECT * FROM t ORDER BY x");
        final Row row = engine.execute("SELECT * FROM t WHERE x = 1").getResultSets().get(0).getRows().get(0);
        row.setValue(0, Integer.valueOf(999));
        row.getValues().set(1, "changed");
        earlier.getResultSets().get(0).getRows().get(1).setValue(1, "also changed");
        assertEquals("[1, a] [2, b]", rows("SELECT * FROM t ORDER BY x"));
        assertEquals("[1, a] [2, b]", rows("SELECT * FROM t AT(STATEMENT => '" + before + "') ORDER BY x"));
        assertEquals("[1, a] [2, b]", rows("SELECT * FROM TABLE(RESULT_SCAN('" + earlier.getQueryId() + "'))"));
        assertEquals("[1]", rows("SELECT COUNT(*) FROM t WHERE x = 1"));
    }
}
