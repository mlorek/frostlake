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

package dev.frostlake.stream;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stream on a view is bound to the tables its view reads, and every stream keeps its row ids and its changes
 * wherever its table goes. View streams matched their base tables by bare name, so a same-named table in another
 * schema fed them. Row ids were keyed by the table's name, so a rename handed out ids the stream already held. Capture
 * looked only in the changed table's own database, so a stream in another database saw nothing. Snowflake documents
 * that "recreating or dropping any of the underlying tables for a view makes any stream on the view stale", and that
 * METADATA$ROW_ID is "a unique, immutable row ID".
 */
public class ViewStreamFollowsItsTablesTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE vd");
        engine.execute("CREATE SCHEMA vd.other");
        engine.execute("USE SCHEMA vd.PUBLIC");
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("CREATE TABLE vd.other.t (a INT)");
        engine.execute("CREATE VIEW v AS SELECT a FROM t");
        engine.execute("CREATE STREAM vs ON VIEW v");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** A query's rows, each one's values joined by '|', and the rows joined by " ; ". */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " ; " : "");
            for (int i = 0; i < row.size(); i++) {
                out.append(i > 0 ? "|" : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    private String shownStale(final String stream) {
        final ResultSet listed = engine.executeQuery("SHOW STREAMS LIKE '" + stream + "' IN SCHEMA vd.PUBLIC");
        return String.valueOf(listed.getRows().get(0).getValue(listed.getColumnIndex("stale")));
    }

    @Test
    public void aViewStreamTakesOnlyItsViewsTable() {
        engine.execute("INSERT INTO vd.other.t VALUES (99)");
        engine.execute("INSERT INTO t VALUES (1)");
        assertEquals("1", rows("SELECT a FROM vs"), "the other schema's T feeds nothing");
        engine.execute("USE SCHEMA vd.other");
        assertEquals("1", rows("SELECT a FROM vd.PUBLIC.vs"), "read from another schema, the same table");
    }

    @Test
    public void droppingAViewsTableLeavesTheStreamStaleAndUndropRestoresIt() {
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("DROP TABLE t");
        assertEquals("true", shownStale("VS"));
        // The stream reads its view as a query does, and fails as the view's expansion does (live-verified).
        assertTrue(refusal("SELECT a FROM vs").contains("Failure during expansion of view 'VD.PUBLIC.V':"),
            refusal("SELECT a FROM vs"));
        assertTrue(refusal("SELECT a FROM vs").contains("Object 'VD.PUBLIC.T' does not exist or not authorized."),
            refusal("SELECT a FROM vs"));
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (2)");
        assertTrue(refusal("SELECT a FROM vs").contains("Change tracking is not enabled or has been missing for the"
            + " time range requested on table 'VD.PUBLIC.T'."), "a new T is not the view's table");
        // UNDROP restores the latest T dropped, so the new one is moved out of the way first.
        engine.execute("ALTER TABLE t RENAME TO t_new");
        engine.execute("UNDROP TABLE t");
        assertEquals("false", shownStale("VS"));
        assertEquals("1", rows("SELECT a FROM vs"));
    }

    @Test
    public void rowIdsStayUniqueAcrossARename() {
        engine.execute("CREATE TABLE r (a INT)");
        engine.execute("CREATE STREAM rs ON TABLE r");
        engine.execute("INSERT INTO r VALUES (1), (2)");
        engine.execute("ALTER TABLE r RENAME TO r2");
        engine.execute("INSERT INTO r2 VALUES (3), (4)");
        final Set<String> ids = new HashSet<>();
        int count = 0;
        for (final Row row : engine.executeQuery("SELECT METADATA$ROW_ID FROM rs").getRows()) {
            ids.add(String.valueOf(row.getValue(0)));
            count++;
        }
        assertEquals(4, count);
        assertEquals(4, ids.size(), "no row id is handed out twice: " + ids);
    }

    @Test
    public void aStreamInAnotherDatabaseCapturesItsTable() {
        engine.execute("CREATE DATABASE vd2");
        engine.execute("CREATE STREAM vd2.PUBLIC.far ON TABLE vd.PUBLIC.t");
        engine.execute("USE SCHEMA vd.PUBLIC");
        engine.execute("INSERT INTO t VALUES (7)");
        assertEquals("7", rows("SELECT a FROM vd2.PUBLIC.far"));
    }
}
