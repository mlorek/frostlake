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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * A CHANGES clause gives the table's columns followed by METADATA$ACTION, METADATA$ISUPDATE and METADATA$ROW_ID, as a
 * stream does (documented). Its rows lacked METADATA$ROW_ID, so {@code SELECT *} read past their end; METADATA$ISUPDATE
 * was a string that was TRUE on every DELETE; and APPEND_ONLY reported updates and deletes too.
 */
public class ChangesSelectStarTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() throws InterruptedException {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE ch_db");
        engine.execute("USE SCHEMA ch_db.PUBLIC");
        engine.execute("CREATE TABLE c (id INT, v VARCHAR)");
        engine.execute("ALTER TABLE c SET CHANGE_TRACKING = TRUE");
        engine.execute("INSERT INTO c VALUES (1, 'one'), (2, 'two'), (3, 'three')");
        Thread.sleep(15);
        engine.execute("SET t0 = CURRENT_TIMESTAMP()");
        Thread.sleep(15);
        engine.execute("INSERT INTO c VALUES (4, 'four')");
        engine.execute("UPDATE c SET v = 'TWO' WHERE id = 2");
        engine.execute("DELETE FROM c WHERE id = 3");
        engine.execute("INSERT INTO c VALUES (5, 'five')");
        engine.execute("DELETE FROM c WHERE id = 5");
        Thread.sleep(15);
        engine.execute("SET t1 = CURRENT_TIMESTAMP()");
        Thread.sleep(15);
        engine.execute("INSERT INTO c VALUES (6, 'six')");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? "; " : "").append(row.getValues());
        }
        return out.toString();
    }

    @Test
    public void aStarGivesTheTableColumnsThenTheStreamMetadataColumns() {
        final ResultSet star = engine.executeQuery(
            "SELECT * FROM c CHANGES(INFORMATION => DEFAULT) AT(TIMESTAMP => $t0) WHERE id = 4");
        final List<ResultSetColumn> columns = star.getColumns();
        final StringBuilder names = new StringBuilder();
        for (final ResultSetColumn column : columns) {
            names.append(names.length() > 0 ? "," : "").append(column.getName());
        }
        assertEquals("ID,V,METADATA$ACTION,METADATA$ISUPDATE,METADATA$ROW_ID", names.toString());
        final Row row = star.getRows().get(0);
        assertEquals("[4, four, INSERT, false]", row.getValues().subList(0, 4).toString());
        assertEquals(String.class, row.getValue(4).getClass());
    }

    @Test
    public void defaultIsTheNetDeltaWithAnUpdateAsADeleteAndInsertPair() {
        assertEquals("[2, two, DELETE, true]; [2, TWO, INSERT, true]; [3, three, DELETE, false]; [4, four, INSERT, false];"
                + " [6, six, INSERT, false]",
            rows("SELECT id, v, METADATA$ACTION, METADATA$ISUPDATE FROM c CHANGES(INFORMATION => DEFAULT)"
                + " AT(TIMESTAMP => $t0) ORDER BY id, METADATA$ACTION"));
    }

    @Test
    public void anUpdatesPairSharesItsRowIdAndOtherRowsHaveTheirOwn() {
        final List<Row> pair = engine.executeQuery("SELECT METADATA$ROW_ID FROM c CHANGES(INFORMATION => DEFAULT)"
            + " AT(TIMESTAMP => $t0) WHERE id = 2").getRows();
        assertEquals(2, pair.size());
        assertEquals(pair.get(0).getValue(0), pair.get(1).getValue(0));
        assertEquals("[3]", rows("SELECT COUNT(DISTINCT METADATA$ROW_ID) FROM c CHANGES(INFORMATION => DEFAULT)"
            + " AT(TIMESTAMP => $t0) WHERE id <> 2"));
        assertNotEquals(pair.get(0).getValue(0), engine.executeQuery("SELECT METADATA$ROW_ID FROM c"
            + " CHANGES(INFORMATION => DEFAULT) AT(TIMESTAMP => $t0) WHERE id = 4").getRows().get(0).getValue(0));
    }

    @Test
    public void appendOnlyReportsEveryInsertAndNoUpdateOrDelete() {
        assertEquals("[4, four, INSERT, false]; [5, five, INSERT, false]; [6, six, INSERT, false]",
            rows("SELECT * EXCLUDE METADATA$ROW_ID FROM c CHANGES(INFORMATION => APPEND_ONLY) AT(TIMESTAMP => $t0)"
                + " ORDER BY id"));
    }

    @Test
    public void anEndPointBoundsTheChanges() {
        assertEquals("[2, two, DELETE]; [2, TWO, INSERT]; [3, three, DELETE]; [4, four, INSERT]",
            rows("SELECT id, v, METADATA$ACTION FROM c CHANGES(INFORMATION => DEFAULT) AT(TIMESTAMP => $t0)"
                + " END(TIMESTAMP => $t1) ORDER BY id, METADATA$ACTION"));
        assertEquals("[4, four]; [5, five]",
            rows("SELECT id, v FROM c CHANGES(INFORMATION => APPEND_ONLY) AT(TIMESTAMP => $t0)"
                + " END(TIMESTAMP => $t1) ORDER BY id"));
    }

    @Test
    public void joinsSubqueriesAndCommonTableExpressionsReadTheChanges() {
        engine.execute("CREATE TABLE d (id INT, name VARCHAR)");
        engine.execute("INSERT INTO d VALUES (2, 'd2'), (4, 'd4')");

        assertEquals("[2, d2, DELETE]; [2, d2, INSERT]; [4, d4, INSERT]",
            rows("SELECT ch.id, d.name, ch.METADATA$ACTION FROM c CHANGES(INFORMATION => DEFAULT)"
                + " AT(TIMESTAMP => $t0) ch JOIN d ON ch.id = d.id ORDER BY ch.id, ch.METADATA$ACTION"));
        assertEquals("[5]", rows("SELECT COUNT(*) FROM (SELECT * FROM c CHANGES(INFORMATION => DEFAULT)"
            + " AT(TIMESTAMP => $t0))"));
        assertEquals("[2, TWO]; [4, four]; [6, six]", rows("WITH x AS (SELECT * FROM c CHANGES(INFORMATION => DEFAULT)"
            + " AT(TIMESTAMP => $t0)) SELECT id, v FROM x WHERE METADATA$ACTION = 'INSERT' ORDER BY id"));
        assertEquals("[2]", rows("SELECT id FROM d WHERE id IN (SELECT id FROM c CHANGES(INFORMATION => DEFAULT)"
            + " AT(TIMESTAMP => $t0) WHERE METADATA$ISUPDATE) ORDER BY id"));
        assertEquals("[2, two, DELETE, true]", rows("SELECT ch.* EXCLUDE METADATA$ROW_ID FROM c"
            + " CHANGES(INFORMATION => DEFAULT) AT(TIMESTAMP => $t0) AS ch WHERE ch.id = 2"
            + " AND ch.METADATA$ACTION = 'DELETE'"));
    }
}
