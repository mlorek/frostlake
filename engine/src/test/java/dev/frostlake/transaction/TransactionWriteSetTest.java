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
import dev.frostlake.storage.Row;
import dev.frostlake.storage.StorageEngine.TableStorage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the deferred-apply / overlay semantics of {@link TransactionWriteSet} against real storage
 * (Phase-1 foundation; see {@code docs/acid-snowflake-plan.md}). Verifies the base store is untouched
 * until apply (deferred), the transaction sees its own pending changes through the overlay (READ
 * COMMITTED for self), apply flushes correctly by stable row id, and discarding the set is the rollback.
 */
public class TransactionWriteSetTest extends BaseDatabaseTest {

    private static final String QN = "TEST_DB.TEST_SCHEMA.T";

    private TableStorage seedThreeRows() {
        engine.execute("CREATE TABLE t (id INTEGER, v VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'a'), (2, 'b'), (3, 'c')");
        return engine.getStorageEngine().getTableStorage(QN);
    }

    @Test
    public void overlayShowsOwnChangesWhileBaseIsUntouched() {
        final TableStorage base = seedThreeRows();
        final long id0 = base.getRowId(0);   // (1,'a')
        final long id1 = base.getRowId(1);   // (2,'b')

        final TransactionWriteSet ws = new TransactionWriteSet();
        ws.recordUpdate(QN, id1, new Row(2, "B"));   // (2,'b') -> (2,'B')
        ws.recordDelete(QN, id0);                    // delete (1,'a')
        ws.recordInsert(QN, new Row(4, "d"));        // + (4,'d')

        // The transaction's own view: delete removed, update applied, insert appended.
        final List<Row> view = ws.overlayRows(QN, base);
        assertEquals(3, view.size());
        assertEquals("2", view.get(0).getValue(0).toString());
        assertEquals("B", view.get(0).getValue(1).toString());
        assertEquals("3", view.get(1).getValue(0).toString());
        assertEquals("4", view.get(2).getValue(0).toString());

        // Deferred: the shared base store (what other transactions read) is untouched until apply.
        assertEquals(3, base.getRowCount(), "base must be untouched before apply");
        assertEquals(3, engine.executeQuery("SELECT id FROM t").getRowCount());
        assertFalse(ws.isEmpty());
    }

    @Test
    public void applyFlushesUpdatesDeletesInserts() {
        final TableStorage base = seedThreeRows();
        final TransactionWriteSet ws = new TransactionWriteSet();
        ws.recordUpdate(QN, base.getRowId(1), new Row(2, "B"));
        ws.recordDelete(QN, base.getRowId(0));
        ws.recordInsert(QN, new Row(4, "d"));

        ws.applyTo(engine.getStorageEngine());

        // After delete(1,'a') + update(2,'b'->'B') + insert(4,'d'): {(2,'B'),(3,'c'),(4,'d')}.
        // Read base directly + compare as strings — the write set deliberately doesn't coerce the
        // hand-built Row values to the column type, so avoid an ORDER BY that mixes numeric types.
        final List<String> rows = new ArrayList<>();
        for (final Row r : base.scan()) {
            rows.add(r.getValue(0).toString() + "|" + r.getValue(1).toString());
        }
        Collections.sort(rows);
        assertEquals(List.of("2|B", "3|c", "4|d"), rows);
    }

    @Test
    public void discardingWriteSetLeavesBaseUnchanged() {
        seedThreeRows();
        final TableStorage base = engine.getStorageEngine().getTableStorage(QN);
        final TransactionWriteSet ws = new TransactionWriteSet();
        ws.recordDelete(QN, base.getRowId(0));
        ws.recordInsert(QN, new Row(9, "z"));

        ws.clear();                              // ROLLBACK == discard, never apply
        ws.applyTo(engine.getStorageEngine());   // applying the now-empty set is a no-op

        assertEquals(3, engine.executeQuery("SELECT id FROM t").getRowCount());
        assertTrue(ws.isEmpty());
    }
}
