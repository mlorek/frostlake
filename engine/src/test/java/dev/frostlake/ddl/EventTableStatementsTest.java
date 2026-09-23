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
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** CREATE EVENT TABLE, SHOW EVENT TABLES and the event table seen as a table. */
public class EventTableStatementsTest extends BaseDatabaseTest {

    private List<String> column(final String sql, final String column) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            out.add(String.valueOf(row.getValue(rs.getColumnIndex(column))));
        }
        return out;
    }

    @Test
    public void anEventTableHasTheFixedColumnSet() {
        assertEquals("Table EVENTS1 successfully created.", String.valueOf(engine.executeQuery(
            "CREATE EVENT TABLE events1 COMMENT = 'telemetry' CHANGE_TRACKING = TRUE").getRows().get(0).getValue(0)));
        assertEquals("[TIMESTAMP, START_TIMESTAMP, OBSERVED_TIMESTAMP, TRACE, RESOURCE, RESOURCE_ATTRIBUTES, SCOPE,"
            + " SCOPE_ATTRIBUTES, RECORD_TYPE, RECORD, RECORD_ATTRIBUTES, VALUE, EXEMPLARS]",
            column("DESCRIBE EVENT TABLE events1", "name").toString());
        assertEquals("[TIMESTAMP_NTZ(9), OBJECT, VARIANT, ARRAY]", "[" + column("DESCRIBE TABLE events1", "type")
            .get(0) + ", " + column("DESCRIBE TABLE events1", "type").get(3) + ", "
            + column("DESCRIBE TABLE events1", "type").get(11) + ", "
            + column("DESCRIBE TABLE events1", "type").get(12) + "]");
        engine.executeQuery("INSERT INTO events1 (RECORD_TYPE, VALUE) SELECT 'LOG', PARSE_JSON('\"hello\"')");
        assertEquals("[LOG]", column("SELECT RECORD_TYPE FROM events1", "RECORD_TYPE").toString());
    }

    @Test
    public void showEventTablesListsOnlyEventTablesAndShowTablesFlagsThem() {
        engine.executeQuery("CREATE EVENT TABLE events2 COMMENT = 'e2'");
        engine.executeQuery("CREATE TABLE plain2 (a INT)");
        assertEquals("[EVENTS2]", column("SHOW EVENT TABLES LIKE '%2'", "name").toString());
        assertEquals("[e2]", column("SHOW EVENT TABLES LIKE 'EVENTS2' IN SCHEMA test_db.test_schema", "comment")
            .toString());
        assertEquals("[EVENTS2]", column("SHOW EVENT TABLES IN DATABASE test_db STARTS WITH 'EVENTS2'", "name")
            .toString());
        assertEquals("[Y, N]", column("SHOW TABLES LIKE '%2'", "is_event").toString());
        final ResultSet listing = engine.executeQuery("SHOW EVENT TABLES LIKE 'EVENTS2'");
        assertEquals("created_on,name,database_name,schema_name,owner,comment,rows,bytes,automatic_clustering,"
            + "cluster_by,retention_time,change_tracking,search_optimization,search_optimization_progress,"
            + "search_optimization_bytes,owner_role_type,cluster_at_ingest_time", names(listing));
        assertEquals("[EVENT_TABLE]", column("SHOW TERSE EVENT TABLES LIKE 'EVENTS2'", "kind").toString());
        assertEquals("[timestamp when event record was added]",
            column("DESCRIBE TABLE events2", "comment").subList(0, 1).toString());
    }

    @Test
    public void createModesAndTheTableNamespace() {
        engine.executeQuery("CREATE EVENT TABLE events3");
        assertEquals("EVENTS3 already exists, statement succeeded.", String.valueOf(engine.executeQuery(
            "CREATE EVENT TABLE IF NOT EXISTS events3").getRows().get(0).getValue(0)));
        engine.executeQuery("CREATE OR REPLACE EVENT TABLE events3 COMMENT = 'again'");
        assertEquals("[again]", column("SHOW EVENT TABLES LIKE 'EVENTS3'", "comment").toString());
        engine.executeQuery("ALTER TABLE events3 RENAME TO events3b");
        assertEquals("[EVENTS3B]", column("SHOW EVENT TABLES LIKE 'EVENTS3%'", "name").toString());
        engine.executeQuery("DROP TABLE events3b");
        assertEquals("[]", column("SHOW EVENT TABLES LIKE 'EVENTS3%'", "name").toString());
    }

    private static String names(final ResultSet rs) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < rs.getColumns().size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(rs.getColumns().get(i).getName());
        }
        return out.toString();
    }

    @Test
    public void aSecondCreateNamesTheTableAsWritten() {
        engine.executeQuery("CREATE EVENT TABLE test_db.test_schema.events4");
        final RuntimeException refusal = assertThrows(RuntimeException.class,
            new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery("CREATE EVENT TABLE test_db.test_schema.events4");
                }
            });
        assertEquals(true, refusal.getMessage().contains("Object 'TEST_DB.TEST_SCHEMA.EVENTS4' already exists."),
            refusal.getMessage());
    }
}
