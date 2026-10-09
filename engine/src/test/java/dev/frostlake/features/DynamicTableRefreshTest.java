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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a dynamic table holds between refreshes: a refresh tells the rows it inserted, copied and deleted, a
 * listing counts the rows the last refresh held, and the table's query reads the table's own schema whoever
 * reads the table.
 */
public class DynamicTableRefreshTest extends BaseDatabaseTest {

    private String options;

    @BeforeEach
    public void aBaseTable() {
        final Object warehouse = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        options = " TARGET_LAG = '1 hour' WAREHOUSE = " + (warehouse == null ? "COMPUTE_WH" : warehouse) + " ";
        engine.execute("CREATE TABLE base (i INT, n VARCHAR)");
        engine.execute("INSERT INTO base VALUES (1, 'a'), (2, 'b'), (3, 'c'), (4, 'd')");
    }

    /** What a refresh answers: the table, the statistics and whether anything changed. */
    private String refresh(final String table) {
        final ResultSet rs = engine.executeQuery("ALTER DYNAMIC TABLE " + table + " REFRESH");
        final Row row = rs.getRows().get(0);
        return row.getValue(0) + "|" + row.getValue(1) + "|" + row.getValue(2);
    }

    private Object cell(final String listing, final String column) {
        final ResultSet rs = engine.executeQuery(listing);
        return rs.getRows().get(0).getValue(rs.getColumnIndex(column));
    }

    private List<String> rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            final StringBuilder line = new StringBuilder();
            for (int i = 0; i < rs.getColumns().size(); i++) {
                line.append(i == 0 ? "" : "|").append(row.getValue(i));
            }
            out.add(line.toString());
        }
        return out;
    }

    @Test
    public void aRefreshTellsWhatItChanged() {
        engine.execute("CREATE DYNAMIC TABLE dt1" + options + "AS SELECT i, n FROM base");
        final List<String> names = new ArrayList<>();
        for (final ResultSetColumn column : engine.executeQuery("ALTER DYNAMIC TABLE dt1 REFRESH").getColumns()) {
            names.add(column.getName());
        }
        assertEquals(Arrays.asList("dt_name", "statistics", "refreshed_dt_count", "data_timestamp"), names);
        assertEquals("TEST_DB.TEST_SCHEMA.DT1|No new data|0", refresh("dt1"));
        engine.execute("DELETE FROM base WHERE i = 1");
        assertEquals("TEST_DB.TEST_SCHEMA.DT1|{\"insertedRows\":0,\"copiedRows\":3,\"deletedRows\":1}|1", refresh("dt1"));
        engine.execute("UPDATE base SET n = 'zz' WHERE i = 2");
        assertEquals("TEST_DB.TEST_SCHEMA.DT1|{\"insertedRows\":1,\"copiedRows\":2,\"deletedRows\":1}|1", refresh("dt1"));
        engine.execute("INSERT INTO base VALUES (5, 'e'), (6, 'f')");
        assertEquals("TEST_DB.TEST_SCHEMA.DT1|{\"insertedRows\":2,\"copiedRows\":0,\"deletedRows\":0}|1", refresh("dt1"));
        engine.execute("DELETE FROM base");
        assertEquals("TEST_DB.TEST_SCHEMA.DT1|{\"insertedRows\":0,\"copiedRows\":0,\"deletedRows\":5}|1", refresh("dt1"));
        engine.execute("INSERT INTO base VALUES (7, 'g')");
        assertEquals("TEST_DB.TEST_SCHEMA.DT1|{\"insertedRows\":1,\"copiedRows\":0,\"deletedRows\":0}|1", refresh("dt1"));
        // A row that came and went, and a row written back as it was, are no change.
        engine.execute("INSERT INTO base VALUES (8, 'h')");
        engine.execute("DELETE FROM base WHERE i = 8");
        assertEquals("TEST_DB.TEST_SCHEMA.DT1|No new data|0", refresh("dt1"));
        engine.execute("UPDATE base SET n = n");
        assertEquals("TEST_DB.TEST_SCHEMA.DT1|No new data|0", refresh("dt1"));
    }

    @Test
    public void aFullRefreshReplacesEveryRowAndAnAggregateItsOneRow() {
        engine.execute("CREATE DYNAMIC TABLE dt2" + options + "AS SELECT COUNT(*) AS c FROM base");
        engine.execute("CREATE DYNAMIC TABLE dt3" + options + "REFRESH_MODE = FULL AS SELECT i, n FROM base");
        engine.execute("INSERT INTO base VALUES (9, 'i')");
        assertEquals("TEST_DB.TEST_SCHEMA.DT2|{\"insertedRows\":1,\"copiedRows\":0,\"deletedRows\":1}|1", refresh("dt2"));
        assertEquals("TEST_DB.TEST_SCHEMA.DT2|No new data|0", refresh("dt2"));
        assertEquals("TEST_DB.TEST_SCHEMA.DT3|{\"insertedRows\":5,\"copiedRows\":0,\"deletedRows\":4}|1", refresh("dt3"));
        assertEquals("TEST_DB.TEST_SCHEMA.DT3|No new data|0", refresh("dt3"));
    }

    @Test
    public void aListingCountsTheRowsOfTheLastRefresh() {
        engine.execute("CREATE DYNAMIC TABLE dt1" + options + "AS SELECT i, n FROM base");
        assertEquals("4", String.valueOf(cell("SHOW TABLES LIKE 'DT1'", "rows")));
        assertEquals("Y", cell("SHOW TABLES LIKE 'DT1'", "is_dynamic"));
        assertEquals("4", String.valueOf(cell("SHOW DYNAMIC TABLES LIKE 'DT1'", "rows")));
        assertEquals("4", String.valueOf(cell("SHOW OBJECTS LIKE 'DT1'", "rows")));
        assertEquals(Arrays.asList("DT1|BASE TABLE|4|YES"), rows("SELECT TABLE_NAME, TABLE_TYPE, ROW_COUNT, IS_DYNAMIC"
            + " FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'DT1'"));
        engine.execute("INSERT INTO base VALUES (5, 'e')");
        assertEquals("4", String.valueOf(cell("SHOW TABLES LIKE 'DT1'", "rows")), "not until the table refreshes");
        refresh("dt1");
        assertEquals("5", String.valueOf(cell("SHOW TABLES LIKE 'DT1'", "rows")));
        assertEquals("5", String.valueOf(cell("SHOW DYNAMIC TABLES LIKE 'DT1'", "rows")));
    }

    @Test
    public void aTableInitializedOnScheduleHoldsNothingUntilItsFirstRefresh() {
        engine.execute("CREATE DYNAMIC TABLE dt4" + options + "INITIALIZE = ON_SCHEDULE AS SELECT i, n FROM base");
        assertEquals("0", String.valueOf(cell("SHOW TABLES LIKE 'DT4'", "rows")));
        assertEquals("TEST_DB.TEST_SCHEMA.DT4|{\"insertedRows\":4,\"copiedRows\":0,\"deletedRows\":0}|1", refresh("dt4"));
        assertEquals("4", String.valueOf(cell("SHOW TABLES LIKE 'DT4'", "rows")));
    }

    @Test
    public void aMaterializedViewCountsTheRowsItMaterialized() {
        engine.execute("CREATE MATERIALIZED VIEW mv1 AS SELECT i, n FROM base");
        assertEquals("4", String.valueOf(cell("SHOW MATERIALIZED VIEWS LIKE 'MV1'", "rows")));
    }

    @Test
    public void theTablesQueryReadsTheTablesOwnSchema() {
        engine.execute("CREATE DYNAMIC TABLE dt1" + options + "AS SELECT i, n FROM base");
        engine.execute("CREATE SCHEMA dq_other");
        engine.execute("CREATE TABLE dq_other.base (i INT, n VARCHAR)");
        engine.execute("INSERT INTO dq_other.base VALUES (9, 'other')");
        engine.execute("USE SCHEMA test_db.dq_other");
        try {
            assertEquals(Arrays.asList("1|a", "2|b", "3|c", "4|d"),
                rows("SELECT i, n FROM test_db.test_schema.dt1 ORDER BY 1"));
            assertEquals("TEST_DB.TEST_SCHEMA.DT1|No new data|0", refresh("test_db.test_schema.dt1"));
            assertEquals(Arrays.asList("1|a", "2|b", "3|c", "4|d"),
                rows("SELECT i, n FROM test_db.test_schema.dt1 ORDER BY 1"));
        } finally {
            engine.execute("USE SCHEMA test_db.test_schema");
        }
    }
}
