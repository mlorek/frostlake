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
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * METADATA$ROW_ID is the row's own identity, and a stream on a view lists it first. The engine numbered change
 * EVENTS instead: an UPDATE's pair and a DELETE each took a new number, so a change could not be told from the row
 * it happened to, and the id read "1", "2", "3".
 *
 * <p>On the account a table row's id is forty lower-case hex digits; it is the same in every stream over the table,
 * in a stream over a view of it and in a CHANGES clause; an UPDATE's DELETE and INSERT carry the id the row was
 * inserted under, and so does a later DELETE. A row of a UNION ALL view's branch reads the base row's id, a colon
 * and twenty-eight base64 characters; a join view's row forty hex digits of its own. A stream on a table and a
 * CHANGES clause list METADATA$ACTION, METADATA$ISUPDATE, METADATA$ROW_ID; a stream on a view, whatever the view,
 * lists METADATA$ROW_ID first. All live-verified; the digits themselves are the account's own and are not compared.
 */
public class StreamRowIdentityTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE r1 (id INTEGER, name VARCHAR(5))");
        engine.execute("CREATE TABLE r2 (id INTEGER, name VARCHAR(5))");
        engine.execute("ALTER TABLE r1 SET CHANGE_TRACKING = TRUE");
        engine.execute("ALTER TABLE r2 SET CHANGE_TRACKING = TRUE");
        engine.execute("CREATE VIEW rv AS SELECT * FROM r1");
        engine.execute("CREATE VIEW rvp AS SELECT id + 1 AS nid, UPPER(name) AS uname, 'lit' AS l FROM r1");
        engine.execute("CREATE VIEW ru AS SELECT id, 'a' AS src FROM r1 UNION ALL SELECT id, 'b' AS src FROM r2");
        engine.execute("CREATE VIEW rj AS SELECT a.id, a.name AS an, b.name AS bn FROM r1 a JOIN r2 b ON a.id = b.id");
        engine.execute("CREATE STREAM rst ON TABLE r1");
        engine.execute("CREATE STREAM rst2 ON TABLE r1");
        engine.execute("CREATE STREAM rsa ON TABLE r1 APPEND_ONLY = TRUE");
        engine.execute("CREATE STREAM rsv ON VIEW rv");
        engine.execute("CREATE STREAM rsvp ON VIEW rvp");
        engine.execute("CREATE STREAM rsu ON VIEW ru");
        engine.execute("CREATE STREAM rsj ON VIEW rj");
        engine.execute("INSERT INTO r1 VALUES (1, 'ab'), (2, 'cd')");
        engine.execute("INSERT INTO r2 VALUES (1, 'xy')");
    }

    /** The names of a query's columns, joined by a comma. */
    private String columnsOf(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final List<String> names = new ArrayList<>();
        for (final ResultSetColumn column : result.getColumns()) {
            names.add(column.getName());
        }
        return String.join(",", names);
    }

    /** A query's rows, each one's cells joined by a bar. */
    private List<String> rowsOf(final String sql) {
        final List<String> rows = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            final List<String> cells = new ArrayList<>();
            for (final Object value : row.getValues()) {
                cells.add(String.valueOf(value));
            }
            rows.add(String.join("|", cells));
        }
        return rows;
    }

    @Test
    public void aViewStreamListsTheRowIdFirstAndATableStreamLast() {
        assertEquals("ID,NAME,METADATA$ACTION,METADATA$ISUPDATE,METADATA$ROW_ID", columnsOf("SELECT * FROM rst"));
        assertEquals("ID,NAME,METADATA$ACTION,METADATA$ISUPDATE,METADATA$ROW_ID", columnsOf("SELECT * FROM rsa"));
        assertEquals("ID,NAME,METADATA$ACTION,METADATA$ISUPDATE,METADATA$ROW_ID",
            columnsOf("SELECT * FROM r1 CHANGES (INFORMATION => DEFAULT) AT (STREAM => 'rst')"));
        assertEquals("ID,NAME,METADATA$ROW_ID,METADATA$ACTION,METADATA$ISUPDATE", columnsOf("SELECT * FROM rsv"));
        assertEquals("NID,UNAME,L,METADATA$ROW_ID,METADATA$ACTION,METADATA$ISUPDATE", columnsOf("SELECT * FROM rsvp"));
        assertEquals("ID,SRC,METADATA$ROW_ID,METADATA$ACTION,METADATA$ISUPDATE", columnsOf("SELECT * FROM rsu"));
        assertEquals("ID,AN,BN,METADATA$ROW_ID,METADATA$ACTION,METADATA$ISUPDATE", columnsOf("SELECT * FROM rsj"));
        // The values follow their columns.
        assertEquals("[1|ab|INSERT|false]", rowsOf(
            "SELECT id, name, METADATA$ACTION, METADATA$ISUPDATE FROM rsv WHERE id = 1").toString());
        assertEquals("[2|AB|lit|INSERT|false]", rowsOf("SELECT * EXCLUDE METADATA$ROW_ID FROM rsvp WHERE nid = 2").toString());
    }

    @Test
    public void aRowIdIsFortyHexDigitsAndTheSameWhereverTheRowIsRead() {
        final List<String> ids = rowsOf("SELECT METADATA$ROW_ID FROM rst ORDER BY id");
        assertEquals(2, ids.size());
        for (final String id : ids) {
            assertTrue(id.matches("[0-9a-f]{40}"), id);
        }
        assertNotEquals(ids.get(0), ids.get(1), "each row has its own");
        assertEquals(ids, rowsOf("SELECT METADATA$ROW_ID FROM rst ORDER BY id"), "a second read spells them alike");
        assertEquals(ids, rowsOf("SELECT METADATA$ROW_ID FROM rst2 ORDER BY id"), "another stream on the table");
        assertEquals(ids, rowsOf("SELECT METADATA$ROW_ID FROM rsa ORDER BY id"), "an append-only stream");
        assertEquals(ids, rowsOf("SELECT METADATA$ROW_ID FROM rsv ORDER BY id"), "a stream on a view of the table");
        assertEquals(ids, rowsOf("SELECT METADATA$ROW_ID FROM rsvp ORDER BY nid"), "a projecting view");
        assertEquals(ids, rowsOf("SELECT METADATA$ROW_ID FROM r1 CHANGES (INFORMATION => DEFAULT)"
            + " AT (STREAM => 'rst') ORDER BY id"), "a CHANGES clause");
        assertEquals("[2|2]", rowsOf("SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM rst").toString());
        assertEquals("[40|true]", rowsOf("SELECT LENGTH(METADATA$ROW_ID), METADATA$ROW_ID = LOWER(METADATA$ROW_ID)"
            + " FROM rst WHERE id = 1").toString());

        engine.execute("CREATE TABLE r3 (id INTEGER)");
        engine.execute("CREATE STREAM rs3 ON TABLE r3");
        engine.execute("INSERT INTO r3 VALUES (1), (2)");
        final List<String> others = rowsOf("SELECT METADATA$ROW_ID FROM rs3 ORDER BY id");
        assertEquals(2, others.size());
        for (final String other : others) {
            assertTrue(other.matches("[0-9a-f]{40}"), other);
            assertTrue(!ids.contains(other), "another table's rows have other ids");
        }
    }

    @Test
    public void anUpdateAndADeleteReportTheIdTheRowWasInsertedUnder() {
        final List<String> ids = rowsOf("SELECT METADATA$ROW_ID FROM rst ORDER BY id");
        // Consume the second stream, so that it reports what follows as changes of rows it has seen.
        engine.execute("CREATE TABLE consumed AS SELECT id FROM rst2");
        engine.execute("UPDATE r1 SET name = 'zz' WHERE id = 1");
        assertEquals("[1|ab|DELETE|true|" + ids.get(0) + ", 1|zz|INSERT|true|" + ids.get(0) + "]", rowsOf(
            "SELECT id, name, METADATA$ACTION, METADATA$ISUPDATE, METADATA$ROW_ID FROM rst2 ORDER BY METADATA$ACTION")
            .toString());
        // The stream that never was consumed nets the insert and the update into one INSERT, under the same id.
        assertEquals("[1|zz|INSERT|false|" + ids.get(0) + ", 2|cd|INSERT|false|" + ids.get(1) + "]", rowsOf(
            "SELECT id, name, METADATA$ACTION, METADATA$ISUPDATE, METADATA$ROW_ID FROM rst ORDER BY id").toString());
        engine.execute("DELETE FROM r1 WHERE id = 2");
        assertEquals("[2|cd|DELETE|false|" + ids.get(1) + "]", rowsOf(
            "SELECT id, name, METADATA$ACTION, METADATA$ISUPDATE, METADATA$ROW_ID FROM rst2 WHERE id = 2").toString());
        assertEquals("[2|3]", rowsOf("SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM rst2").toString());
    }

    @Test
    public void aUnionBranchAddsToTheBaseRowsIdAndAJoinRowHasItsOwn() {
        final List<String> ids = rowsOf("SELECT METADATA$ROW_ID FROM rst ORDER BY id");
        final List<String> union = rowsOf("SELECT METADATA$ROW_ID FROM rsu ORDER BY src, id");
        assertEquals(3, union.size());
        for (final String id : union) {
            assertTrue(id.matches("[0-9a-f]{40}:[A-Za-z0-9+/]{27}="), id);
        }
        assertEquals(ids.get(0), union.get(0).substring(0, 40), "the first branch's row leads with its table row's id");
        assertEquals(ids.get(1), union.get(1).substring(0, 40));
        assertTrue(!ids.contains(union.get(2).substring(0, 40)), "the second branch's row is another table's");
        assertEquals("[69|69|69]", rowsOf("SELECT LISTAGG(LENGTH(METADATA$ROW_ID), '|') WITHIN GROUP (ORDER BY src, id)"
            + " FROM rsu").toString());

        final List<String> joined = rowsOf("SELECT METADATA$ROW_ID FROM rsj");
        assertEquals(1, joined.size());
        assertTrue(joined.get(0).matches("[0-9a-f]{40}"), joined.get(0));
        assertTrue(!ids.contains(joined.get(0)), "a join row is neither of the rows it joins");
        assertEquals(joined, rowsOf("SELECT METADATA$ROW_ID FROM rsj"), "a second read spells it alike");
    }
}
