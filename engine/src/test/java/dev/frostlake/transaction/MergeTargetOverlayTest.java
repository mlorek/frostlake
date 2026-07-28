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

package dev.frostlake.transaction;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A MERGE inside an explicit transaction must see its TARGET through the transaction overlay: rows the
 * same transaction INSERTed (still pending in the write set) must match ON and be updatable/deletable,
 * and pending updates/deletes must be visible. This is the loader idiom
 * {@code BEGIN TRANSACTION; INSERT INTO tmp …; MERGE INTO tmp …; COMMIT} — without the overlay the
 * MERGE silently matched nothing and the freshly inserted rows kept their initial values.
 */
public class MergeTargetOverlayTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE agg_tmp (k VARCHAR, v NUMBER)");
        engine.execute("CREATE TABLE feed (k VARCHAR, amt NUMBER)");
        engine.execute("INSERT INTO feed VALUES ('a', 42), ('b', 7)");
    }

    @Test
    public void mergeMatchesRowsInsertedEarlierInTheSameTransaction() {
        engine.execute("BEGIN TRANSACTION");
        engine.execute("INSERT INTO agg_tmp VALUES ('a', 0), ('b', 0)");
        engine.execute(
            """
            MERGE INTO agg_tmp t USING (SELECT k, amt FROM feed) AS s (k2, v2)
            ON t.k = s.k2
            WHEN MATCHED THEN UPDATE SET t.v = s.v2
            """);
        engine.execute("COMMIT");
        final ResultSet result = engine.executeQuery("SELECT k, v FROM agg_tmp ORDER BY k");
        assertEquals(42L, ((Number) result.getRows().get(0).getValue(1)).longValue());
        assertEquals(7L, ((Number) result.getRows().get(1).getValue(1)).longValue());
    }

    @Test
    public void mergeDeleteRemovesAPendingInsert() {
        engine.execute("BEGIN TRANSACTION");
        engine.execute("INSERT INTO agg_tmp VALUES ('a', 0), ('keepme', 0)");
        engine.execute(
            """
            MERGE INTO agg_tmp t USING (SELECT k FROM feed) AS s (k2)
            ON t.k = s.k2
            WHEN MATCHED THEN DELETE
            """);
        engine.execute("COMMIT");
        final ResultSet result = engine.executeQuery("SELECT k FROM agg_tmp");
        assertEquals(1, result.getRows().size());
        assertEquals("keepme", result.getRows().get(0).getValue(0));
    }

    @Test
    public void mergeSeesPendingUpdatesOfCommittedRows() {
        engine.execute("INSERT INTO agg_tmp VALUES ('renamed', 0)");
        engine.execute("BEGIN TRANSACTION");
        engine.execute("UPDATE agg_tmp SET k = 'a' WHERE k = 'renamed'");
        engine.execute(
            """
            MERGE INTO agg_tmp t USING (SELECT k, amt FROM feed) AS s (k2, v2)
            ON t.k = s.k2
            WHEN MATCHED THEN UPDATE SET t.v = s.v2
            """);
        engine.execute("COMMIT");
        final ResultSet result = engine.executeQuery("SELECT k, v FROM agg_tmp");
        assertEquals("a", result.getRows().get(0).getValue(0));
        assertEquals(42L, ((Number) result.getRows().get(0).getValue(1)).longValue());
    }

    @Test
    public void mergeDoesNotResurrectRowsDeletedInTheSameTransaction() {
        engine.execute("INSERT INTO agg_tmp VALUES ('a', 5)");
        engine.execute("BEGIN TRANSACTION");
        engine.execute("DELETE FROM agg_tmp WHERE k = 'a'");
        engine.execute(
            """
            MERGE INTO agg_tmp t USING (SELECT k, amt FROM feed) AS s (k2, v2)
            ON t.k = s.k2
            WHEN MATCHED THEN UPDATE SET t.v = s.v2
            WHEN NOT MATCHED THEN INSERT (k, v) VALUES (s.k2, s.v2)
            """);
        engine.execute("COMMIT");
        final ResultSet result = engine.executeQuery("SELECT k, v FROM agg_tmp ORDER BY k");
        assertEquals(2, result.getRows().size());
        assertEquals(42L, ((Number) result.getRows().get(0).getValue(1)).longValue());
    }
}
