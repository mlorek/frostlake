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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public class StreamRecord {
    private final List<Object> values;
    private final ChangeType changeType;
    private final boolean isUpdate;
    private final long rowId;
    private final LocalDateTime timestamp;
    private final String sourceTable;

    public StreamRecord(final List<Object> values, final ChangeType changeType, final boolean isUpdate, final long rowId) {
        this(values, changeType, isUpdate, rowId, null);
    }

    public StreamRecord(final List<Object> values, final ChangeType changeType, final boolean isUpdate,
                        final long rowId, final String sourceTable) {
        this.values = values;
        this.changeType = changeType;
        this.isUpdate = isUpdate;
        this.rowId = rowId;
        // UTC wall time, the engine's session zone — change-window comparisons read this beside
        // UTC-domain statement clocks.
        this.timestamp = LocalDateTime.now(ZoneOffset.UTC);
        this.sourceTable = sourceTable;
    }

    private StreamRecord(final StreamRecord from, final List<Object> values) {
        this.values = values;
        this.changeType = from.changeType;
        this.isUpdate = from.isUpdate;
        this.rowId = from.rowId;
        this.timestamp = from.timestamp;
        this.sourceTable = from.sourceTable;
    }

    /**
     * This change without the value at {@code index} — what a record captured before a DROP COLUMN reads
     * as afterwards: a stream answers its changes through the table's CURRENT columns (live-verified).
     * Everything else, the capture time included, is kept, so change windows do not move.
     *
     * @param index the dropped column's position before the drop
     * @return the narrowed record, or this one when it is too short to hold that slot
     */
    /**
     * This change with a newly added column's value appended — what a record captured before an ADD COLUMN
     * reads as afterwards: the column's default, or NULL (live-verified). The capture time is kept.
     *
     * @param value the value the new column takes in rows that already exist
     * @return the widened record
     */
    public StreamRecord withSlotAppended(final Object value) {
        final List<Object> kept = values == null ? new ArrayList<>() : new ArrayList<>(values);
        kept.add(value);
        return new StreamRecord(this, kept);
    }

    public StreamRecord withoutSlot(final int index) {
        if (values == null || index >= values.size()) {
            return this;
        }
        final List<Object> kept = new ArrayList<>(values);
        kept.remove(index);
        return new StreamRecord(this, kept);
    }

    public List<Object> getValues() {
        return values;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public boolean isUpdate() {
        return isUpdate;
    }

    public long getRowId() {
        return rowId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * Bare (upper-case) name of the base table this change was captured from. Used to route records to the
     * matching branch of a {@code UNION ALL} view stream; null for table streams and single-branch views.
     */
    public String getSourceTable() {
        return sourceTable;
    }
}
