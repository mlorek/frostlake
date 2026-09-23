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
 */package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SHOW MATERIALIZED VIEWS answers live's own layout, TERSE included: beside the view's names it reports the
 * one table the view reads, whether the view is invalid and why, its comment, the CREATE statement as
 * written with the view's COMMENT clause re-printed as {@code comment = '…'}, and its security and
 * clustering flags. Every cell is live-verified.
 */
public class MaterializedViewShowColumnsTest extends BaseDatabaseTest {

    private static final String LAYOUT = "created_on|name|reserved|database_name|schema_name|cluster_by|rows|bytes"
        + "|source_database_name|source_schema_name|source_table_name|refreshed_on|compacted_on|owner|invalid"
        + "|invalid_reason|behind_by|comment|text|is_secure|automatic_clustering|owner_role_type";

    @BeforeEach
    public void createSource() {
        engine.execute("CREATE OR REPLACE TABLE st (x INT)");
        engine.execute("INSERT INTO st VALUES (1)");
    }

    /** The current database name, as the fixture created it. */
    private String currentDatabase() {
        return engine.executeQuery("SELECT CURRENT_DATABASE()").getRows().get(0).getValue(0).toString();
    }

    /** One view's cell of the listing, NULL spelled out. */
    private String cell(final String view, final String columnName) {
        final ResultSet rs = engine.executeQuery("SHOW MATERIALIZED VIEWS LIKE '" + view + "' IN SCHEMA "
            + currentDatabase() + ".test_schema");
        assertEquals(1, rs.getRowCount(), view);
        final Object value = rs.getRows().get(0).getValue(rs.getColumnIndex(columnName));
        return value == null ? "NULL" : value.toString();
    }

    private static String names(final ResultSet rs) {
        final StringBuilder names = new StringBuilder();
        for (final ResultSetColumn column : rs.getColumns()) {
            if (names.length() > 0) {
                names.append('|');
            }
            names.append(column.getName().toLowerCase());
        }
        return names.toString();
    }

    @Test
    public void theListingHasLivesLayoutTerseIncluded() {
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW p_mv AS SELECT x FROM st");
        assertEquals(LAYOUT, names(engine.executeQuery("SHOW MATERIALIZED VIEWS")));
        assertEquals(LAYOUT, names(engine.executeQuery("SHOW TERSE MATERIALIZED VIEWS")));
    }

    @Test
    public void aViewNamesTheTableItReads() {
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW p_mv AS SELECT x FROM st");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW q_mv AS SELECT x FROM " + currentDatabase() + ".test_schema.st");
        for (final String view : new String[] {"P_MV", "Q_MV"}) {
            assertEquals("", cell(view, "reserved"), view);
            assertEquals("", cell(view, "cluster_by"), view);
            assertEquals(currentDatabase(), cell(view, "source_database_name"), view);
            assertEquals("TEST_SCHEMA", cell(view, "source_schema_name"), view);
            assertEquals("ST", cell(view, "source_table_name"), view);
            assertEquals("false", cell(view, "invalid"), view);
            assertEquals("NULL", cell(view, "invalid_reason"), view);
            assertEquals("0s", cell(view, "behind_by"), view);
            assertEquals("false", cell(view, "is_secure"), view);
            assertEquals("OFF", cell(view, "automatic_clustering"), view);
            assertEquals("ROLE", cell(view, "owner_role_type"), view);
        }
        assertEquals("CREATE OR REPLACE MATERIALIZED VIEW p_mv AS SELECT x FROM st", cell("P_MV", "text"));
    }

    @Test
    public void theTextReprintsTheViewsCommentClause() {
        engine.execute("create materialized view mv_lower COMMENT='no spaces' as select x from st");
        engine.execute("CREATE MATERIALIZED VIEW mv_quote COMMENT = 'it''s' AS SELECT x FROM st");
        assertEquals("no spaces", cell("MV_LOWER", "comment"));
        assertEquals("create materialized view mv_lower comment = 'no spaces' as select x from st",
            cell("MV_LOWER", "text"));
        assertEquals("it's", cell("MV_QUOTE", "comment"));
        assertEquals("CREATE MATERIALIZED VIEW mv_quote comment = 'it''s' AS SELECT x FROM st", cell("MV_QUOTE", "text"));
    }

    @Test
    public void aSecureViewAndASuspendedOneSaySo() {
        engine.execute("CREATE SECURE MATERIALIZED VIEW mv_sec AS SELECT x FROM st");
        assertEquals("true", cell("MV_SEC", "is_secure"));
        assertEquals("CREATE SECURE MATERIALIZED VIEW mv_sec AS SELECT x FROM st", cell("MV_SEC", "text"));
        engine.execute("ALTER MATERIALIZED VIEW mv_sec SUSPEND");
        assertEquals("true", cell("MV_SEC", "invalid"));
        assertEquals("Marked Materialized View as invalid manually.", cell("MV_SEC", "invalid_reason"));
    }
}
