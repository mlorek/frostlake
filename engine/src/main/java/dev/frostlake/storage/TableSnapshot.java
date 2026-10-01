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

package dev.frostlake.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable timestamped snapshot of the table for time travel. */
public class TableSnapshot {
    public final long epochMillis;
    /** The snapshot's place in the order every table's snapshots were taken in; see {@link SnapshotSequence}. */
    public final long sequence;
    public final List<Row> rows;
    /**
     * Each row's identity, index-aligned with {@link #rows}: a row an UPDATE rewrote keeps its identity, so two
     * snapshots tell an updated row from one deleted and inserted again.
     */
    public final List<Long> rowIds;

    public TableSnapshot(final long epochMillis, final long sequence, final List<Row> rows, final List<Long> rowIds) {
        this.epochMillis = epochMillis;
        this.sequence = sequence;
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        this.rowIds = Collections.unmodifiableList(new ArrayList<>(rowIds));
    }
}
