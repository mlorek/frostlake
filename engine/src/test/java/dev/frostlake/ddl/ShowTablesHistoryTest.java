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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SHOW TABLES HISTORY lists the dropped tables UNDROP can still restore, each with the dropped_on that SHOW SCHEMAS |
 * DATABASES HISTORY give theirs. It listed what SHOW TABLES did, with no dropped_on column and no dropped table.
 * Live-verified on a real account: the column sits right after retention_time, is TIMESTAMP_LTZ(3), and is NULL on a
 * live table's row.
 */
public class ShowTablesHistoryTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE th");
        engine.execute("USE SCHEMA th.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private List<String> column(final String sql, final String name) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        while (rs.next()) {
            out.add(String.valueOf(rs.getValue(name)));
        }
        return out;
    }

    private List<String> columnNames(final String sql) {
        final List<String> out = new ArrayList<>();
        for (final ResultSetColumn column : engine.executeQuery(sql).getColumns()) {
            out.add(column.getName());
        }
        return out;
    }

    /** Each row's name and whether it has a dropped_on, as NAME:dropped or NAME:live. */
    private List<String> listed(final String show) {
        final ResultSet rs = engine.executeQuery(show);
        final List<String> out = new ArrayList<>();
        while (rs.next()) {
            out.add(rs.getValue("name") + ":" + (rs.getValue("dropped_on") == null ? "live" : "dropped"));
        }
        return out;
    }

    @Test
    public void droppedOnFollowsRetentionTimeAndIsNullForALiveTable() {
        engine.execute("CREATE TABLE live_t (a INT)");
        final List<String> plain = columnNames("SHOW TABLES");
        final List<String> history = columnNames("SHOW TABLES HISTORY");
        assertFalse(plain.contains("dropped_on"), "SHOW TABLES has no dropped_on");
        final List<String> expected = new ArrayList<>(plain);
        expected.add(expected.indexOf("retention_time") + 1, "dropped_on");
        assertEquals(expected, history);
        assertEquals(List.of("LIVE_T:live"), listed("SHOW TABLES HISTORY"));
    }

    @Test
    public void aDroppedTableIsListedWithWhenItWasDropped() {
        engine.execute("CREATE TABLE d1 (a INT)");
        engine.execute("INSERT INTO d1 VALUES (1), (2)");
        engine.execute("SET before_drop = CURRENT_TIMESTAMP()");
        engine.execute("DROP TABLE d1");
        assertEquals(List.of(), column("SHOW TABLES", "name"), "SHOW TABLES lists no dropped table");
        engine.execute("SHOW TABLES HISTORY LIKE 'D1'");
        assertEquals(List.of("D1 2 TIMESTAMP_LTZ(3)[SB8] true"), column("SELECT \"name\" || ' ' || \"rows\" || ' '"
            + " || SYSTEM$TYPEOF(\"dropped_on\") || ' ' || (\"dropped_on\" > $before_drop)::VARCHAR AS r"
            + " FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "R"));
        engine.execute("UNDROP TABLE d1");
        assertEquals(List.of("D1:live"), listed("SHOW TABLES HISTORY LIKE 'D1'"), "undropped, it is live again");
    }

    @Test
    public void everyVersionIsListedInTheOrderItWasCreatedTheLiveTableLast() {
        // The first version holds one row, the second two: the rows tell the versions apart, which two drops in one
        // millisecond do not. Live lists a name's versions by created_on, ascending, so the dropped ones come oldest
        // first and the live table last (live-verified).
        engine.execute("CREATE TABLE v (n INT)");
        engine.execute("INSERT INTO v VALUES (1)");
        engine.execute("DROP TABLE v");
        engine.execute("CREATE TABLE v (n INT)");
        engine.execute("INSERT INTO v VALUES (1), (2)");
        engine.execute("DROP TABLE v");
        engine.execute("CREATE TABLE v (n INT)");
        assertEquals(List.of("V:dropped", "V:dropped", "V:live"), listed("SHOW TABLES HISTORY LIKE 'V'"));
        assertEquals(List.of("1", "2", "0"), column("SHOW TABLES HISTORY LIKE 'V'", "rows"));
        // Compared as moments, not as text: a whole second prints without its ".000" and sorts after ".001".
        final List<Instant> createdOn = new ArrayList<>();
        final ResultSet history = engine.executeQuery("SHOW TABLES HISTORY LIKE 'V'");
        while (history.next()) {
            createdOn.add(Instant.from((TemporalAccessor) history.getValue("created_on")));
        }
        assertTrue(!createdOn.get(0).isAfter(createdOn.get(1)) && !createdOn.get(1).isAfter(createdOn.get(2)),
            "created_on ascending: " + createdOn);
    }

    @Test
    public void likeStartsWithLimitAndScopesFilterAsTheyDoForSchemas() {
        engine.execute("CREATE SCHEMA other");
        engine.execute("CREATE TABLE other.o1 (a INT)");
        engine.execute("DROP TABLE other.o1");
        engine.execute("USE SCHEMA th.PUBLIC");
        engine.execute("CREATE TABLE a1 (a INT)");
        engine.execute("CREATE TABLE a2 (a INT)");
        engine.execute("DROP TABLE a2");
        engine.execute("CREATE TABLE b1 (a INT)");
        engine.execute("DROP TABLE b1");
        assertEquals(List.of("A1:live", "A2:dropped", "B1:dropped"), listed("SHOW TABLES HISTORY"));
        assertEquals(List.of("A2:dropped"), listed("SHOW TABLES HISTORY LIKE 'a2'"));
        assertEquals(List.of("A1:live", "A2:dropped"), listed("SHOW TABLES HISTORY STARTS WITH 'A'"));
        assertEquals(List.of("A1:live"), listed("SHOW TABLES HISTORY LIMIT 1"));
        assertEquals(List.of("B1:dropped"), listed("SHOW TABLES HISTORY LIMIT 5 FROM 'A2'"));
        assertEquals(List.of("O1:dropped"), listed("SHOW TABLES HISTORY IN SCHEMA th.other"));
        assertEquals(List.of("O1:dropped", "A1:live", "A2:dropped", "B1:dropped"),
            listed("SHOW TABLES HISTORY IN DATABASE th"));
        assertEquals(List.of("O1:dropped"), listed("SHOW TABLES HISTORY LIKE 'O%' IN ACCOUNT"));
    }

    @Test
    public void schemasAndDatabasesHistoryTypeDroppedOnAsCreatedOn() {
        // Live-verified: TIMESTAMP_LTZ(3) in every HISTORY listing, as created_on is.
        engine.execute("CREATE SCHEMA gone_s");
        engine.execute("DROP SCHEMA gone_s");
        engine.execute("SHOW SCHEMAS HISTORY LIKE 'GONE_S' IN DATABASE th");
        assertEquals(List.of("TIMESTAMP_LTZ(3)[SB8] TIMESTAMP_LTZ(3)[SB8]"), column("SELECT SYSTEM$TYPEOF(\"dropped_on\")"
            + " || ' ' || SYSTEM$TYPEOF(\"created_on\") AS r FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "R"));
        engine.execute("CREATE DATABASE gone_d");
        engine.execute("DROP DATABASE gone_d");
        engine.execute("USE SCHEMA th.PUBLIC");
        engine.execute("SHOW DATABASES HISTORY LIKE 'GONE_D'");
        assertEquals(List.of("TIMESTAMP_LTZ(3)[SB8] TIMESTAMP_LTZ(3)[SB8]"), column("SELECT SYSTEM$TYPEOF(\"dropped_on\")"
            + " || ' ' || SYSTEM$TYPEOF(\"created_on\") AS r FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "R"));
    }

    @Test
    public void aTableKeptForNoTimeIsNotListed() {
        engine.execute("CREATE TABLE gone (a INT) DATA_RETENTION_TIME_IN_DAYS = 0");
        engine.execute("DROP TABLE gone");
        engine.execute("CREATE TABLE kept (a INT) DATA_RETENTION_TIME_IN_DAYS = 3");
        engine.execute("DROP TABLE kept");
        assertEquals(List.of("KEPT:dropped"), listed("SHOW TABLES HISTORY"));
        assertEquals(List.of("3"), column("SHOW TABLES HISTORY LIKE 'KEPT'", "retention_time"));
    }
}
