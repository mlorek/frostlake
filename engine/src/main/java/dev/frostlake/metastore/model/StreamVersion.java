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


package dev.frostlake.metastore.model;

import java.time.LocalDateTime;

/**
 * A version of a table a stream reads, written by one commit, as SYSTEM$STREAM_BACKLOG lists it (live-verified): the
 * table is rewritten whole, so the rows the version added are the table's rows after it and the rows it removed those
 * before it; an INSERT alone is an INSERT, a TRUNCATE a TRUNCATE, and every other change an UPDATE.
 */
public final class StreamVersion {

    private final String baseTable;
    private final LocalDateTime time;
    private final StreamVersionKind kind;
    private final long rowsAdded;
    private final long rowsRemoved;
    private final long inserted;
    private final long sequence;

    /**
     * @param baseTable   the table written, {@code DB.SCHEMA.TABLE}
     * @param time        when the version was committed
     * @param kind        what it changed
     * @param rowsAdded   the table's rows after it
     * @param rowsRemoved the table's rows before it
     * @param inserted    the rows it inserted
     * @param sequence    its place among the stream's offsets (see Stream#getOffsetSequence)
     */
    public StreamVersion(final String baseTable, final LocalDateTime time, final StreamVersionKind kind,
                         final long rowsAdded, final long rowsRemoved, final long inserted, final long sequence) {
        this.baseTable = baseTable;
        this.time = time;
        this.kind = kind;
        this.rowsAdded = rowsAdded;
        this.rowsRemoved = rowsRemoved;
        this.inserted = inserted;
        this.sequence = sequence;
    }

    public String getBaseTable() {
        return baseTable;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public long getRowsAdded() {
        return rowsAdded;
    }

    public long getRowsRemoved() {
        return rowsRemoved;
    }

    public long getSequence() {
        return sequence;
    }

    /** @return the DML_MODE: INSERT, TRUNCATE, or UPDATE for every other change */
    public String getDmlMode() {
        return kind == StreamVersionKind.INSERT || kind == StreamVersionKind.TRUNCATE ? kind.name() : "UPDATE";
    }

    /**
     * The change records the account estimates the version holds for a stream (live-verified): the rows an INSERT
     * inserted, none for a TRUNCATE, and three in ten of the rows added and removed for any other change, rounded up;
     * an append-only stream reads none of an UPDATE, the rows a DELETE left, and the rows a MERGE inserted.
     *
     * @param appendOnly whether the stream reads inserts alone
     * @return the estimate
     */
    public long estimatedRows(final boolean appendOnly) {
        switch (kind) {
            case INSERT:
                return inserted;
            case TRUNCATE:
                return 0L;
            default:
                if (!appendOnly) {
                    return (3L * (rowsAdded + rowsRemoved) + 9L) / 10L;
                }
                if (kind == StreamVersionKind.DELETE) {
                    return rowsAdded;
                }
                return kind == StreamVersionKind.MERGE ? inserted : 0L;
        }
    }
}
