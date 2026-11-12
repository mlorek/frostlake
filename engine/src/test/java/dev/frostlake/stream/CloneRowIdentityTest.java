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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A stream on a table and a stream on its clone report the same METADATA$ROW_ID for the rows that stood when the clone
 * was made (documented: a clone's rows keep their row ids), in their later changes as in their initial rows, for a
 * table clone, a clone at a point and the tables of a schema clone. A clone's rows took new identities, so the two
 * streams named one row by two ids. Unmeasured on live beyond the documented rule.
 */
public class CloneRowIdentityTest extends BaseDatabaseTest {

    /** A query's one column, row by row. */
    private List<String> column(final String sql) {
        final List<String> cells = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            cells.add(String.valueOf(row.getValue(0)));
        }
        return cells;
    }

    /** The ids of the update pair a stream lists for the row a = 1. */
    private List<String> updatedIds(final String stream) {
        return column("SELECT METADATA$ROW_ID FROM " + stream + " WHERE a = 1 ORDER BY METADATA$ACTION");
    }

    @Test
    public void aTableClonesRowsKeepTheirIds() {
        engine.execute("CREATE OR REPLACE TABLE crs (a INT, b STRING) CHANGE_TRACKING = TRUE");
        engine.execute("INSERT INTO crs VALUES (1, 'x'), (2, 'y')");
        engine.execute("CREATE OR REPLACE TABLE crc CLONE crs");
        engine.execute("ALTER TABLE crc SET CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE STREAM crss ON TABLE crs");
        engine.execute("CREATE OR REPLACE STREAM crcs ON TABLE crc");
        engine.execute("UPDATE crs SET b = 'x2' WHERE a = 1");
        engine.execute("UPDATE crc SET b = 'x3' WHERE a = 1");
        final List<String> source = updatedIds("crss");
        assertEquals(2, source.size(), source.toString());
        assertEquals(source, updatedIds("crcs"));
        engine.execute("CREATE OR REPLACE STREAM crsi ON TABLE crs SHOW_INITIAL_ROWS = TRUE");
        engine.execute("CREATE OR REPLACE STREAM crci ON TABLE crc SHOW_INITIAL_ROWS = TRUE");
        assertEquals(column("SELECT METADATA$ROW_ID FROM crsi ORDER BY a"),
            column("SELECT METADATA$ROW_ID FROM crci ORDER BY a"));
    }

    @Test
    public void aSchemaClonesTablesKeepTheirIds() {
        engine.execute("CREATE OR REPLACE SCHEMA crsch");
        engine.execute("CREATE OR REPLACE TABLE crsch.t (a INT, b STRING) CHANGE_TRACKING = TRUE");
        engine.execute("INSERT INTO crsch.t VALUES (1, 'x'), (2, 'y')");
        engine.execute("CREATE OR REPLACE SCHEMA crsch2 CLONE crsch");
        engine.execute("ALTER TABLE crsch2.t SET CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE STREAM crsch.s ON TABLE crsch.t");
        engine.execute("CREATE OR REPLACE STREAM crsch2.s ON TABLE crsch2.t");
        engine.execute("DELETE FROM crsch.t WHERE a = 1");
        engine.execute("DELETE FROM crsch2.t WHERE a = 1");
        final List<String> source = column("SELECT METADATA$ROW_ID FROM crsch.s");
        assertEquals(1, source.size(), source.toString());
        assertEquals(source, column("SELECT METADATA$ROW_ID FROM crsch2.s"));
    }

    @Test
    public void aCloneAtAPointKeepsTheIdsOfThen() {
        engine.execute("CREATE OR REPLACE TABLE crp (a INT, b STRING) CHANGE_TRACKING = TRUE");
        engine.execute("INSERT INTO crp VALUES (1, 'x')");
        engine.execute("SET crq = LAST_QUERY_ID()");
        engine.execute("CREATE OR REPLACE STREAM crps ON TABLE crp");
        engine.execute("INSERT INTO crp VALUES (2, 'y')");
        engine.execute("CREATE OR REPLACE TABLE crpc CLONE crp AT (STATEMENT => $crq)");
        engine.execute("ALTER TABLE crpc SET CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE STREAM crpcs ON TABLE crpc");
        engine.execute("DELETE FROM crp WHERE a = 1");
        engine.execute("DELETE FROM crpc WHERE a = 1");
        assertEquals(column("SELECT METADATA$ROW_ID FROM crps WHERE a = 1"),
            column("SELECT METADATA$ROW_ID FROM crpcs WHERE a = 1"));
    }
}
