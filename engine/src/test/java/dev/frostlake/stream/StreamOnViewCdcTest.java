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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Change data capture for a stream defined ON a VIEW (previously inert — DDL was accepted but no
 * changes were ever captured). Changes to the view's single base table are captured and projected
 * through the view: only the view's columns surface (aliases apply), rows outside the view's WHERE
 * predicate don't appear, and an update that moves a row out of the view surfaces only its DELETE
 * half. Views with joins / aggregation are rejected at CREATE STREAM, mirroring Snowflake's
 * change-tracking restrictions.
 */
public class StreamOnViewCdcTest extends BaseDatabaseTest {

    private ResultSet run(final String sql) {
        return engine.executeQuery(sql);
    }

    @Test
    public void insertCapturedThroughView() {
        engine.execute("CREATE TABLE src (id INTEGER, v VARCHAR)");
        engine.execute("CREATE VIEW vw AS SELECT * FROM src");
        engine.execute("CREATE STREAM st ON VIEW vw");
        engine.execute("INSERT INTO src VALUES (1, 'a')");
        final ResultSet rs = run("SELECT id, v, METADATA$ACTION FROM st");
        assertEquals(1, rs.getRowCount());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals("a", rs.getRows().get(0).getValue(1));
        assertEquals("INSERT", rs.getRows().get(0).getValue(2));
    }

    // The stream carries the VIEW's shape: only the projected column, under its alias.
    @Test
    public void projectionAndAliasApply() {
        engine.execute("CREATE TABLE src (id INTEGER, v VARCHAR)");
        engine.execute("CREATE VIEW vw AS SELECT v AS label FROM src");
        engine.execute("CREATE STREAM st ON VIEW vw");
        engine.execute("INSERT INTO src VALUES (1, 'hello')");
        final ResultSet rs = run("SELECT label, METADATA$ACTION FROM st");
        assertEquals(1, rs.getRowCount());
        assertEquals("hello", rs.getRows().get(0).getValue(0));
        assertEquals("INSERT", rs.getRows().get(0).getValue(1));
    }

    // Rows outside the view's WHERE predicate are not part of the view — their changes don't surface.
    @Test
    public void whereFilterExcludesRows() {
        engine.execute("CREATE TABLE src (id INTEGER)");
        engine.execute("CREATE VIEW vw AS SELECT id FROM src WHERE id > 10");
        engine.execute("CREATE STREAM st ON VIEW vw");
        engine.execute("INSERT INTO src VALUES (1), (11)");
        final ResultSet rs = run("SELECT id FROM st");
        assertEquals(1, rs.getRowCount());
        assertEquals(11L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void updateThroughViewProducesPair() {
        engine.execute("CREATE TABLE src (id INTEGER, v VARCHAR)");
        engine.execute("INSERT INTO src VALUES (1, 'old')");
        engine.execute("CREATE VIEW vw AS SELECT v FROM src");
        engine.execute("CREATE STREAM st ON VIEW vw");
        engine.execute("UPDATE src SET v = 'new' WHERE id = 1");
        final ResultSet rs = run("SELECT v, METADATA$ACTION FROM st ORDER BY METADATA$ACTION");
        assertEquals(2, rs.getRowCount());
        assertEquals("old", rs.getRows().get(0).getValue(0));
        assertEquals("DELETE", rs.getRows().get(0).getValue(1));
        assertEquals("new", rs.getRows().get(1).getValue(0));
        assertEquals("INSERT", rs.getRows().get(1).getValue(1));
    }

    // An update that moves a row OUT of the view surfaces only the DELETE half of the pair.
    @Test
    public void updateMovingRowOutOfViewShowsDeleteOnly() {
        engine.execute("CREATE TABLE src (id INTEGER, flag INTEGER)");
        engine.execute("INSERT INTO src VALUES (1, 1)");
        engine.execute("CREATE VIEW vw AS SELECT id FROM src WHERE flag = 1");
        engine.execute("CREATE STREAM st ON VIEW vw");
        engine.execute("UPDATE src SET flag = 0 WHERE id = 1");
        final ResultSet rs = run("SELECT id, METADATA$ACTION FROM st");
        assertEquals(1, rs.getRowCount());
        assertEquals("DELETE", rs.getRows().get(0).getValue(1));
    }

    @Test
    public void dmlConsumptionAdvancesViewStream() {
        engine.execute("CREATE TABLE src (id INTEGER)");
        engine.execute("CREATE TABLE dst (id INTEGER)");
        engine.execute("CREATE VIEW vw AS SELECT id FROM src");
        engine.execute("CREATE STREAM st ON VIEW vw");
        engine.execute("INSERT INTO src VALUES (1)");
        assertEquals(1, run("SELECT id FROM st").getRowCount());
        engine.execute("INSERT INTO dst SELECT id FROM st");
        assertEquals(0, run("SELECT id FROM st").getRowCount());
    }

    // Views beyond a simple single-table projection are rejected at CREATE STREAM.
    @Test
    public void joinViewIsRejected() {
        engine.execute("CREATE TABLE a (id INTEGER)");
        engine.execute("CREATE TABLE b (id INTEGER)");
        engine.execute("CREATE VIEW vw AS SELECT a.id FROM a JOIN b ON a.id = b.id");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE STREAM st ON VIEW vw");
            }
        });
        assertTrue(ex.getMessage().contains("change tracking"),
            "unexpected message: " + ex.getMessage());
    }

    @Test
    public void aggregateViewIsRejected() {
        engine.execute("CREATE TABLE a (id INTEGER)");
        engine.execute("CREATE VIEW vw AS SELECT COUNT(*) AS c FROM a GROUP BY id");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE STREAM st ON VIEW vw");
            }
        });
        assertTrue(ex.getMessage().contains("change tracking"),
            "unexpected message: " + ex.getMessage());
    }
}
