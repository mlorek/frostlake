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
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Snowflake Stream - Change Data Capture (CDC) mechanism
 * Streams record DML changes (INSERT, UPDATE, DELETE) made to a table
 */
public class Stream {

    private final String name;
    private final String sourceTableName;
    private final StreamSourceType sourceType;
    private final StreamType streamType;
    private final boolean showInitialRows;
    private final List<StreamRecord> records;
    private final LocalDateTime createdAt;
    private long currentOffset;
    private boolean stale;
    private String comment;

    public Stream(final String name, final String sourceTableName, final StreamType streamType) {
        this(name, sourceTableName, StreamSourceType.TABLE, streamType, false);
    }

    public Stream(final String name, final String sourceTableName, final StreamType streamType, final boolean showInitialRows) {
        this(name, sourceTableName, StreamSourceType.TABLE, streamType, showInitialRows);
    }

    public Stream(final String name, final String sourceTableName, final StreamSourceType sourceType, final StreamType streamType, final boolean showInitialRows) {
        this.name = name;
        this.sourceTableName = sourceTableName;
        this.sourceType = sourceType;
        this.streamType = streamType;
        this.showInitialRows = showInitialRows;
        this.records = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.currentOffset = 0;
        this.stale = false;
    }

    public void addRecord(final StreamRecord record) {
        // For append-only streams, only track actual INSERTs (not UPDATE-generated INSERTs)
        if (streamType == StreamType.APPEND_ONLY) {
            if (record.getChangeType() != ChangeType.INSERT || record.isUpdate()) {
                return;
            }
        }
        records.add(record);
    }

    public List<StreamRecord> getUnconsumedRecords() {
        if (currentOffset >= records.size()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(records.subList((int) currentOffset, records.size()));
    }

    /**
     * The unconsumed change records consolidated to the NET delta per logical row, which is what a
     * SELECT from a stream returns in Snowflake: a row inserted then updated inside the window is a
     * single INSERT of its final image (ISUPDATE false); inserted then deleted cancels out
     * entirely; a pre-existing row updated repeatedly nets to one DELETE(original)+INSERT(final)
     * pair (ISUPDATE true); updated then deleted nets to a plain DELETE of the original image.
     * Logical rows are chained by value image (an update pair's DELETE half matches the entry
     * whose current image it removes), since change records carry per-operation — not per-row —
     * ids. APPEND_ONLY streams record only true INSERTs, so they are returned as-is.
     */
    public List<StreamRecord> getUnconsumedNetRecords() {
        final List<StreamRecord> raw = getUnconsumedRecords();
        if (streamType == StreamType.APPEND_ONLY) {
            return raw;
        }

        final List<StreamNetChange> net = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            final StreamRecord record = raw.get(i);
            final boolean isUpdatePair = record.isUpdate()
                && record.getChangeType() == ChangeType.DELETE
                && i + 1 < raw.size()
                && raw.get(i + 1).isUpdate()
                && raw.get(i + 1).getChangeType() == ChangeType.INSERT
                && raw.get(i + 1).getRowId() == record.getRowId();

            if (isUpdatePair) {
                // UPDATE (old -> new): advance the matching entry's current image, else start a pair.
                final List<Object> oldImage = record.getValues();
                final List<Object> newImage = raw.get(i + 1).getValues();
                i++;
                final StreamNetChange existing = findByCurrentImage(net, oldImage);
                if (existing != null) {
                    existing.setNewValues(newImage);
                } else {
                    net.add(new StreamNetChange(oldImage, newImage, record.getRowId()));
                }
            } else if (record.getChangeType() == ChangeType.INSERT) {
                net.add(new StreamNetChange(null, record.getValues(), record.getRowId()));
            } else {
                // Plain DELETE: cancel a row born in this window, close out an updated row, or
                // record the delete of a pre-existing row.
                final StreamNetChange existing = findByCurrentImage(net, record.getValues());
                if (existing == null) {
                    net.add(new StreamNetChange(record.getValues(), null, record.getRowId()));
                } else if (existing.getOldValues() == null) {
                    net.remove(existing);
                } else {
                    existing.setNewValues(null);
                }
            }
        }

        final List<StreamRecord> out = new ArrayList<>();
        for (final StreamNetChange change : net) {
            if (change.getOldValues() == null && change.getNewValues() == null) {
                continue;
            }
            if (change.getOldValues() == null) {
                out.add(new StreamRecord(change.getNewValues(), ChangeType.INSERT, false, change.getRowId()));
            } else if (change.getNewValues() == null) {
                out.add(new StreamRecord(change.getOldValues(), ChangeType.DELETE, false, change.getRowId()));
            } else {
                out.add(new StreamRecord(change.getOldValues(), ChangeType.DELETE, true, change.getRowId()));
                out.add(new StreamRecord(change.getNewValues(), ChangeType.INSERT, true, change.getRowId()));
            }
        }
        return out;
    }

    /** The net entry whose CURRENT image equals the given row values, or null. First match wins
     *  (identical duplicate rows are indistinguishable in a value-chained model). */
    private StreamNetChange findByCurrentImage(final List<StreamNetChange> net, final List<Object> image) {
        for (final StreamNetChange change : net) {
            if (change.getNewValues() != null && change.getNewValues().equals(image)) {
                return change;
            }
        }
        return null;
    }

    public void consume() {
        // Consuming a stream advances the offset
        currentOffset = records.size();
    }

    public void reset() {
        currentOffset = 0;
    }

    /** Owning role; defaults to SYSADMIN until stamped with the creating role at CREATE. */
    private String owner = "SYSADMIN";

    // Bare (upper-case) name of the single base table backing a VIEW-sourced stream — change
    // capture matches DML against it. Null for TABLE-sourced streams.
    private String baseTableName;

    public String getBaseTableName() {
        return baseTableName;
    }

    public void setBaseTableName(final String baseTableName) {
        this.baseTableName = baseTableName;
    }

    public String getName() {
        return name;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(final String owner) {
        this.owner = owner;
    }

    public String getSourceTableName() {
        return sourceTableName;
    }

    public StreamSourceType getSourceType() {
        return sourceType;
    }

    public StreamType getStreamType() {
        return streamType;
    }

    public boolean isShowInitialRows() {
        return showInitialRows;
    }

    public List<StreamRecord> getRecords() {
        return records;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public long getCurrentOffset() {
        return currentOffset;
    }

    public boolean isStale() {
        return stale;
    }

    public void setStale(final boolean stale) {
        this.stale = stale;
    }

    public int getUnconsumedCount() {
        return records.size() - (int) currentOffset;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(final String comment) {
        this.comment = comment;
    }
}
