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

import dev.frostlake.metastore.model.StreamVersion;
import dev.frostlake.metastore.model.StreamVersionKind;

import java.time.LocalDateTime;

/**
 * What one statement did to one table, the version of the table SYSTEM$STREAM_BACKLOG lists for it: every statement
 * that changes a table writes a version of its own, also inside an explicit transaction, and one that changes no row
 * writes one where it commits alone (live-verified):
 *
 * <ul>
 *   <li>a version that changed rows rewrote the table, so it added the table's rows after it and removed those
 *       before it;</li>
 *   <li>an INSERT, an UPDATE or a DELETE that changed no row adds and removes none, and inside an explicit
 *       transaction writes no version at all;</li>
 *   <li>a TRUNCATE writes a version even of an empty table, and a MERGE rewrites the table whatever its branches
 *       did, a MERGE that only inserts or changes nothing included.</li>
 * </ul>
 */
final class StatementTableChange {

    private final long ordinal;
    private final String table;
    private final LocalDateTime time;
    private StreamVersionKind kind;
    private long inserted;
    private long updated;
    private long deleted;
    private boolean truncated;

    /**
     * @param ordinal the statement's ordinal in its transaction, -1 for a statement that commits alone
     * @param table   the table, fully qualified
     * @param time    when the statement ran
     */
    StatementTableChange(final long ordinal, final String table, final LocalDateTime time) {
        this.ordinal = ordinal;
        this.table = table;
        this.time = time;
    }

    /** The same change made to the table under another name. */
    StatementTableChange movedTo(final String movedTable) {
        final StatementTableChange moved = new StatementTableChange(ordinal, movedTable, time);
        moved.kind = kind;
        moved.inserted = inserted;
        moved.updated = updated;
        moved.deleted = deleted;
        moved.truncated = truncated;
        return moved;
    }

    long getOrdinal() {
        return ordinal;
    }

    String getTable() {
        return table;
    }

    /** The statement says what kind of DML it is: INSERT, UPDATE, DELETE or MERGE. */
    void noteKind(final StreamVersionKind statementKind) {
        kind = statementKind;
    }

    void addInserted() {
        inserted++;
    }

    void addUpdated() {
        updated++;
    }

    void addDeleted() {
        deleted++;
    }

    /** The statement emptied the table, which held {@code rows} rows as the transaction saw it. */
    void addTruncated(final long rows) {
        truncated = true;
        deleted += rows;
    }

    /** The table's rows after the statement, given those before it. */
    long rowsAfter(final long before) {
        return before + inserted - deleted;
    }

    /**
     * The version the statement wrote, or null where it wrote none.
     *
     * @param before   the table's rows before the statement
     * @param sequence the version's place among the stream offsets
     * @return the version, or null
     */
    StreamVersion version(final long before, final long sequence) {
        final boolean changed = inserted > 0 || updated > 0 || deleted > 0 || truncated;
        final StreamVersionKind written = kind != null && !truncated ? kind : changedKind();
        if (written == null) {
            return null;
        }
        if (changed || written == StreamVersionKind.MERGE) {
            return new StreamVersion(table, time, written, rowsAfter(before), before, inserted, sequence);
        }
        // A statement that changed no row commits a version only where it commits alone.
        return ordinal < 0 ? new StreamVersion(table, time, written, 0L, 0L, 0L, sequence) : null;
    }

    /** What the rows the statement changed make it, where the statement did not say. */
    private StreamVersionKind changedKind() {
        if (truncated && inserted == 0 && updated == 0) {
            return StreamVersionKind.TRUNCATE;
        }
        if (inserted > 0) {
            return updated > 0 || deleted > 0 ? StreamVersionKind.MERGE : StreamVersionKind.INSERT;
        }
        if (updated > 0) {
            return StreamVersionKind.UPDATE;
        }
        return deleted > 0 ? StreamVersionKind.DELETE : null;
    }
}
