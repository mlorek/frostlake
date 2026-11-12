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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * METADATA$ROW_ID is a row's own, unchanging identity (documented), so the initial rows of a SHOW_INITIAL_ROWS
 * stream report the ids the same rows report in their later changes, and a replica loaded from the initial rows
 * and merged on the id stays a copy of the table. The engine numbered the initial rows from 1 instead, so one
 * row's initial id was another row's id in its later changes. The changes pending at a stream's AT | BEFORE point
 * carry the rows' own ids too. Unmeasured on live.
 */
public class StreamInitialRowIdentityTest extends BaseDatabaseTest {

    /** A query's rows, each one's cells joined by a bar. */
    private List<String> rowsOf(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final List<String> rows = new ArrayList<>();
        for (final Row row : result.getRows()) {
            final List<String> cells = new ArrayList<>();
            for (final Object value : row.getValues()) {
                cells.add(String.valueOf(value));
            }
            rows.add(String.join("|", cells));
        }
        return rows;
    }

    /** The one cell a query answers. */
    private String cellOf(final String sql) {
        final List<String> rows = rowsOf(sql);
        assertEquals(1, rows.size(), sql);
        return rows.get(0);
    }

    @Test
    public void anInitialRowKeepsItsIdInItsLaterChanges() {
        engine.execute("CREATE TABLE rt (a INT, b STRING)");
        engine.execute("INSERT INTO rt VALUES (1, 'x'), (2, 'y')");
        engine.execute("CREATE STREAM srt ON TABLE rt SHOW_INITIAL_ROWS = TRUE");
        final String first = cellOf("SELECT METADATA$ROW_ID FROM srt WHERE a = 1");
        final String second = cellOf("SELECT METADATA$ROW_ID FROM srt WHERE a = 2");
        assertNotEquals(first, second);
        engine.execute("CREATE TABLE w1 (rid STRING, a INT, b STRING)");
        engine.execute("INSERT INTO w1 SELECT METADATA$ROW_ID, a, b FROM srt");
        engine.execute("UPDATE rt SET b = 'y2' WHERE a = 2");
        assertEquals(List.of("2|y|DELETE|true|" + second, "2|y2|INSERT|true|" + second),
            rowsOf("""
                SELECT a, b, METADATA$ACTION, METADATA$ISUPDATE, METADATA$ROW_ID FROM srt
                ORDER BY METADATA$ACTION"""));
        engine.execute("""
            MERGE INTO w1 USING (SELECT * FROM srt WHERE METADATA$ACTION = 'INSERT') c ON w1.rid = c.METADATA$ROW_ID
              WHEN MATCHED THEN UPDATE SET w1.a = c.a, w1.b = c.b
              WHEN NOT MATCHED THEN INSERT VALUES (c.METADATA$ROW_ID, c.a, c.b)""");
        assertEquals(List.of(first + "|1|x", second + "|2|y2"), rowsOf("SELECT rid, a, b FROM w1 ORDER BY a"));
    }

    @Test
    public void anInitialRowsIdIsTheIdAnotherStreamReportsForTheRow() {
        engine.execute("CREATE TABLE rt (a INT, b STRING) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE STREAM plain ON TABLE rt");
        engine.execute("INSERT INTO rt VALUES (1, 'x'), (2, 'y')");
        engine.execute("CREATE STREAM srt ON TABLE rt SHOW_INITIAL_ROWS = TRUE");
        assertEquals(rowsOf("SELECT a, METADATA$ROW_ID FROM plain ORDER BY a"),
            rowsOf("SELECT a, METADATA$ROW_ID FROM srt ORDER BY a"));
    }

    @Test
    public void aViewStreamsInitialRowKeepsItsIdInItsLaterChanges() {
        engine.execute("CREATE TABLE rt (a INT, b STRING) CHANGE_TRACKING = TRUE");
        engine.execute("INSERT INTO rt VALUES (1, 'x'), (2, 'y')");
        engine.execute("CREATE VIEW rv AS SELECT a, b FROM rt");
        engine.execute("CREATE STREAM srv ON VIEW rv SHOW_INITIAL_ROWS = TRUE");
        final String first = cellOf("SELECT METADATA$ROW_ID FROM srv WHERE a = 1");
        final String second = cellOf("SELECT METADATA$ROW_ID FROM srv WHERE a = 2");
        assertNotEquals(first, second);
        engine.execute("CREATE TABLE w1 (rid STRING)");
        engine.execute("INSERT INTO w1 SELECT METADATA$ROW_ID FROM srv");
        engine.execute("DELETE FROM rt WHERE a = 2");
        assertEquals(List.of("2|DELETE|" + second), rowsOf("SELECT a, METADATA$ACTION, METADATA$ROW_ID FROM srv"));
    }

    @Test
    public void aChangePendingAtAPointCarriesItsRowsId() {
        engine.execute("CREATE TABLE ct (a INT, b STRING) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE STREAM plain ON TABLE ct");
        engine.execute("INSERT INTO ct VALUES (1, 'x'), (2, 'y')");
        engine.execute("SET qid = LAST_QUERY_ID()");
        final List<String> inserted = rowsOf("SELECT a, METADATA$ROW_ID FROM plain ORDER BY a");
        final String second = inserted.get(1).substring(2);
        engine.execute("UPDATE ct SET b = 'y2' WHERE a = 2");
        engine.execute("DELETE FROM ct WHERE a = 1");
        engine.execute("INSERT INTO ct VALUES (3, 'z')");
        engine.execute("CREATE STREAM since ON TABLE ct AT (STATEMENT => $qid)");
        final List<String> changes = rowsOf("""
            SELECT a, METADATA$ACTION, METADATA$ISUPDATE, METADATA$ROW_ID FROM since
            ORDER BY a, METADATA$ACTION""");
        assertEquals(4, changes.size(), changes.toString());
        assertEquals("1|DELETE|false|" + inserted.get(0).substring(2), changes.get(0));
        assertEquals("2|DELETE|true|" + second, changes.get(1));
        assertEquals("2|INSERT|true|" + second, changes.get(2));
        final String third = changes.get(3).substring(changes.get(3).lastIndexOf('|') + 1);
        assertEquals("3|INSERT|false|" + third, changes.get(3));
        assertEquals(List.of("3|" + third), rowsOf("SELECT a, METADATA$ROW_ID FROM plain WHERE a = 3"));
    }
}
