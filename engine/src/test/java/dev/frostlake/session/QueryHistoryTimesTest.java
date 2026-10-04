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

package dev.frostlake.session;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * QUERY_HISTORY's START_TIME and END_TIME are TIMESTAMP_LTZ, as the documentation types them, and a SET is one row.
 * The columns were ISO strings typed VARCHAR. Outside UTC they compared as text against CURRENT_TIMESTAMP and lost,
 * and a SET with an expression value listed the SELECT that evaluated it as a query the session never ran.
 */
public class QueryHistoryTimesTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        engine.executeQuery("SELECT 'first'");
        engine.execute("SET t1 = CURRENT_TIMESTAMP()");
        engine.executeQuery("SELECT 'third'");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String first(final String sql) {
        for (final Row row : engine.executeQuery(sql).getRows()) {
            return String.valueOf(row.getValue(0));
        }
        return "no row";
    }

    @Test
    public void startAndEndTimesAreTimestampsInTheSessionsZone() {
        final ResultSet history = engine.executeQuery(
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION())");
        for (final ResultSetColumn column : history.getColumns()) {
            if ("START_TIME".equals(column.getName()) || "END_TIME".equals(column.getName())) {
                assertEquals("TIMESTAMP_LTZ", column.getDataType().getName(), column.getName());
            }
        }
        assertEquals("TIMESTAMP_LTZ(3)[SB8]", first("SELECT SYSTEM$TYPEOF(start_time)"
            + " FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) LIMIT 1"));
        assertEquals("true", first("SELECT COUNT_IF(start_time <= CURRENT_TIMESTAMP() AND end_time >= start_time)"
            + " = COUNT(*) FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION())"));
        assertEquals("true", first("SELECT start_time <= $t1"
            + " FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) WHERE query_text = 'SELECT ''first'''"));
    }

    /** The listing's own order, most recent first: statements this quick share a millisecond, so start times tie. */
    @Test
    public void aSetIsOneRow() {
        final List<String> texts = new ArrayList<>();
        for (final Row row : engine.executeQuery("SELECT query_text"
                + " FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION())").getRows()) {
            texts.add(String.valueOf(row.getValue(0)));
        }
        assertEquals("[SELECT 'third', SET t1 = CURRENT_TIMESTAMP(), SELECT 'first',"
            + " ALTER SESSION SET TIMEZONE = 'America/Los_Angeles']", texts.toString());
    }
}
