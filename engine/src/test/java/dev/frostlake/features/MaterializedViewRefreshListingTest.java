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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SHOW MATERIALIZED VIEWS describes the data a view materialized, not the view's own history: refreshed_on and
 * compacted_on name the source table's last write before the view was created — the epoch for a table never
 * written — and behind_by how much later the table's newest write came, {@code 0s} when there is none, and
 * {@code <h>h<m>m<s>s} once it is an hour or more. Later writes, an UPDATE included, leave the stamp where it
 * was. Every cell is live-verified.
 */
public class MaterializedViewRefreshListingTest extends BaseDatabaseTest {

    /** The first row's cells, a comma between them. */
    private String firstRow(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < row.getValues().size(); i++) {
            out.append(i > 0 ? ", " : "").append(row.getValue(i));
        }
        return out.toString();
    }

    @Test
    public void aViewOverATableNeverWrittenReadsTheEpoch() {
        engine.execute("CREATE TABLE never_written (a INT)");
        engine.execute("CREATE MATERIALIZED VIEW mv_never AS SELECT a FROM never_written");
        engine.executeQuery("SHOW MATERIALIZED VIEWS LIKE 'MV_NEVER'");
        assertEquals("0, 0, 0s",
            firstRow("SELECT DATE_PART(EPOCH_SECOND, \"refreshed_on\"), DATE_PART(EPOCH_SECOND, \"compacted_on\"), "
                + "\"behind_by\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));

        engine.execute("INSERT INTO never_written VALUES (1), (2)");
        engine.executeQuery("SHOW MATERIALIZED VIEWS LIKE 'MV_NEVER'");
        assertEquals("0, 0, true",
            firstRow("SELECT DATE_PART(EPOCH_SECOND, \"refreshed_on\"), DATE_PART(EPOCH_SECOND, \"compacted_on\"), "
                + "\"behind_by\" RLIKE '[0-9]+h[0-9]+m[0-9]+s' FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
    }

    @Test
    public void aViewReadsTheWriteBeforeItWasCreatedAndFallsBehindLaterWrites() {
        engine.execute("CREATE TABLE written (a INT)");
        engine.execute("SET before_write = CURRENT_TIMESTAMP()");
        engine.execute("INSERT INTO written VALUES (1), (2)");
        engine.execute("SET after_write = CURRENT_TIMESTAMP()");
        engine.execute("CREATE MATERIALIZED VIEW mv_written AS SELECT a FROM written");
        engine.execute("CREATE MATERIALIZED VIEW mv_written_too AS SELECT a FROM written WHERE a > 0");
        final String listing = "SELECT COUNT(*), COUNT(DISTINCT \"refreshed_on\"), "
            + "BOOLAND_AGG(\"refreshed_on\" = \"compacted_on\"), "
            + "BOOLAND_AGG(DATE_TRUNC('SECOND', \"refreshed_on\") >= DATE_TRUNC('SECOND', $before_write) "
            + "AND \"refreshed_on\" <= $after_write), MIN(\"behind_by\") = '0s', MAX(\"behind_by\") = '0s' "
            + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))";
        engine.executeQuery("SHOW MATERIALIZED VIEWS LIKE 'MV_WRITTEN%'");
        assertEquals("2, 1, true, true, true, true", firstRow(listing));

        engine.executeQuery("SELECT SYSTEM$WAIT(2)");
        engine.execute("UPDATE written SET a = a + 10 WHERE a = 1");
        engine.executeQuery("SHOW MATERIALIZED VIEWS LIKE 'MV_WRITTEN%'");
        assertEquals("2, 1, true, true, false, false", firstRow(listing));
        engine.executeQuery("SHOW MATERIALIZED VIEWS LIKE 'MV_WRITTEN%'");
        assertEquals("true",
            firstRow("SELECT BOOLAND_AGG(\"behind_by\" RLIKE '[0-9]+s') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
    }
}
