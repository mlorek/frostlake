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

import dev.frostlake.metastore.Taggable;
import dev.frostlake.storage.SnapshotSequence;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a Snowflake Stream - Change Data Capture (CDC) mechanism
 * Streams record DML changes (INSERT, UPDATE, DELETE) made to a table
 */
public class Stream implements Taggable {

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
    /**
     * The rows a SHOW_INITIAL_ROWS stream reports until it is first consumed — the source's rows at the
     * stream's starting point, as inserts — or null once consumed. While they are pending no change shows;
     * consuming them leaves the offset at the starting point, so the changes made since then show next.
     */
    private List<StreamRecord> initialRecords;
    /** The same moment in the order table snapshots are taken in, which tells apart moments one millisecond holds. */
    private long offsetSequence = SnapshotSequence.mark();
    /** A dynamic-table stream's image of the table's rows at the last refresh it saw. */
    private List<List<Object>> refreshImage;

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

    /**
     * Whether a change record APPENDED a row: an INSERT that no UPDATE produced. The insert half of an
     * update pair carries the same change type and is deliberately not an append — live-verified, a row
     * that already existed contributes nothing to an append-only view however often it is rewritten.
     */
    public static boolean isAppend(final StreamRecord record) {
        return record.getChangeType() == ChangeType.INSERT && !record.isUpdate();
    }

    public void addRecord(final StreamRecord record) {
        // For append-only streams, only track actual INSERTs (not UPDATE-generated INSERTs)
        if (streamType == StreamType.APPEND_ONLY && !isAppend(record)) {
            return;
        }
        records.add(record);
    }

    /**
     * Removes a dropped column's slot from the change records, consumed or not, so a record captured
     * before a DROP COLUMN reads through the table's current columns as the account's streams do. Offsets
     * index the record list, which keeps its length and order.
     *
     * @param index       the dropped column's position before the drop
     * @param capturedFrom the table the records must have been captured from (upper-case bare name), or
     *                     null for every record — a table stream's records all come from its one table
     */
    public void dropColumnSlot(final int index, final String capturedFrom) {
        for (int i = 0; i < records.size(); i++) {
            final StreamRecord record = records.get(i);
            if (capturedFrom == null || capturedFrom.equalsIgnoreCase(record.getSourceTable())) {
                records.set(i, record.withoutSlot(index));
            }
        }
    }

    /**
     * Appends a newly added column's value to the change records, consumed or not, when a record is
     * exactly one slot short — every record captured before the ADD COLUMN.
     *
     * @param width        the table's column count after the add
     * @param value        the value the new column takes in rows that already exist
     * @param capturedFrom the table the records must have been captured from (upper-case bare name), or
     *                     null for every record
     */
    public void appendColumnSlot(final int width, final Object value, final String capturedFrom) {
        for (int i = 0; i < records.size(); i++) {
            final StreamRecord record = records.get(i);
            if ((capturedFrom == null || capturedFrom.equalsIgnoreCase(record.getSourceTable()))
                    && record.getValues() != null && record.getValues().size() == width - 1) {
                records.set(i, record.withSlotAppended(value));
            }
        }
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
        if (initialRecords != null) {
            return new ArrayList<>(initialRecords);
        }
        return netRecordsOf(getUnconsumedRecords());
    }

    /**
     * The net unconsumed records as {@link #getUnconsumedNetRecords}, but with {@code extraRaw} transient
     * change records (e.g. the current transaction's not-yet-committed base-table changes) folded in AFTER
     * the committed unconsumed ones, so a stream read inside a transaction reflects that transaction's own
     * uncommitted DML — the deferred-apply equivalent of read-your-writes for streams. The extras are never
     * added to the stream, so a rollback (which just discards the write set) needs no undo here. Extras that
     * an {@link #addRecord} would drop (an APPEND_ONLY stream keeps only true INSERTs) are filtered the same
     * way before consolidation.
     */
    public List<StreamRecord> getUnconsumedNetRecordsWith(final List<StreamRecord> extraRaw) {
        if (initialRecords != null) {
            return new ArrayList<>(initialRecords);
        }
        return netRecordsOf(unconsumedRecordsWith(extraRaw));
    }

    /**
     * The unconsumed records that APPENDED a row, in the order the appends happened, with the values each
     * row was appended WITH — the append-only view of the change window.
     *
     * <p>This is not the net delta filtered to its inserts, and the difference is visible three ways,
     * all live-verified: a row inserted and then updated inside the window reports the value it was
     * inserted with rather than its current one; a row inserted and then deleted inside the window is
     * still reported, because a later delete does not un-append it; and a row that already existed
     * reports nothing however it is rewritten, because rewriting is not appending.
     *
     * <p>{@code extraRaw} folds in transient records the same way {@link #getUnconsumedNetRecordsWith}
     * does.
     */
    public List<StreamRecord> getUnconsumedAppendsWith(final List<StreamRecord> extraRaw) {
        if (initialRecords != null) {
            return new ArrayList<>(initialRecords);
        }
        final List<StreamRecord> appends = new ArrayList<>();
        for (final StreamRecord record : unconsumedRecordsWith(extraRaw)) {
            if (isAppend(record)) {
                appends.add(record);
            }
        }
        return appends;
    }

    private List<StreamRecord> unconsumedRecordsWith(final List<StreamRecord> extraRaw) {
        final List<StreamRecord> combined = getUnconsumedRecords();
        for (final StreamRecord record : extraRaw) {
            if (streamType == StreamType.APPEND_ONLY && !isAppend(record)) {
                continue;
            }
            combined.add(record);
        }
        return combined;
    }

    private List<StreamRecord> netRecordsOf(final List<StreamRecord> raw) {
        if (streamType == StreamType.APPEND_ONLY) {
            return raw;
        }

        final List<StreamNetChange> net = new ArrayList<>();
        // Hash-chained index of each OPEN entry's CURRENT image (scoped by source table): the
        // former linear findByCurrentImage scan cost O(k²·cells) per stream read. Buckets are kept
        // in NET (creation) order via the per-entry sequence, so the first-match rule for
        // identical duplicate rows is exactly the scan's.
        final Map<List<Object>, List<StreamNetChange>> byImage = new HashMap<>();
        final Map<StreamNetChange, Integer> creationSeq = new IdentityHashMap<>();
        int nextSeq = 0;
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
                final StreamNetChange existing = firstByImage(byImage, oldImage, record.getSourceTable());
                if (existing != null) {
                    unindexImage(byImage, existing);
                    existing.setNewValues(newImage);
                    indexImage(byImage, creationSeq, existing);
                } else {
                    final StreamNetChange change =
                        new StreamNetChange(oldImage, newImage, record.getRowId(), record.getSourceTable());
                    creationSeq.put(change, Integer.valueOf(nextSeq++));
                    net.add(change);
                    indexImage(byImage, creationSeq, change);
                }
            } else if (record.getChangeType() == ChangeType.INSERT) {
                final StreamNetChange change =
                    new StreamNetChange(null, record.getValues(), record.getRowId(), record.getSourceTable());
                creationSeq.put(change, Integer.valueOf(nextSeq++));
                net.add(change);
                indexImage(byImage, creationSeq, change);
            } else {
                // Plain DELETE: cancel a row born in this window, close out an updated row, or
                // record the delete of a pre-existing row.
                final StreamNetChange existing = firstByImage(byImage, record.getValues(), record.getSourceTable());
                if (existing == null) {
                    final StreamNetChange change =
                        new StreamNetChange(record.getValues(), null, record.getRowId(), record.getSourceTable());
                    creationSeq.put(change, Integer.valueOf(nextSeq++));
                    net.add(change);
                } else if (existing.getOldValues() == null) {
                    unindexImage(byImage, existing);
                    net.remove(existing);
                } else {
                    unindexImage(byImage, existing);
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
                out.add(new StreamRecord(change.getNewValues(), ChangeType.INSERT, false, change.getRowId(), change.getSourceTable()));
            } else if (change.getNewValues() == null) {
                out.add(new StreamRecord(change.getOldValues(), ChangeType.DELETE, false, change.getRowId(), change.getSourceTable()));
            } else {
                out.add(new StreamRecord(change.getOldValues(), ChangeType.DELETE, true, change.getRowId(), change.getSourceTable()));
                out.add(new StreamRecord(change.getNewValues(), ChangeType.INSERT, true, change.getRowId(), change.getSourceTable()));
            }
        }
        return out;
    }

    private static List<Object> imageKey(final List<Object> image, final String sourceTable) {
        final List<Object> key = new ArrayList<>(2);
        key.add(sourceTable);
        key.add(image);
        return key;
    }

    /** Index an OPEN entry (a live current image) under its image key, keeping the bucket in net
     *  (creation) order so {@link #firstByImage} answers exactly what the linear scan did. */
    private static void indexImage(final Map<List<Object>, List<StreamNetChange>> byImage,
                                   final Map<StreamNetChange, Integer> creationSeq,
                                   final StreamNetChange change) {
        if (change.getNewValues() == null) {
            return;
        }
        final List<Object> key = imageKey(change.getNewValues(), change.getSourceTable());
        List<StreamNetChange> bucket = byImage.get(key);
        if (bucket == null) {
            bucket = new ArrayList<>();
            byImage.put(key, bucket);
        }
        final int seq = creationSeq.get(change).intValue();
        int pos = bucket.size();
        while (pos > 0 && creationSeq.get(bucket.get(pos - 1)).intValue() > seq) {
            pos--;
        }
        bucket.add(pos, change);
    }

    private static void unindexImage(final Map<List<Object>, List<StreamNetChange>> byImage,
                                     final StreamNetChange change) {
        if (change.getNewValues() == null) {
            return;
        }
        final List<Object> key = imageKey(change.getNewValues(), change.getSourceTable());
        final List<StreamNetChange> bucket = byImage.get(key);
        if (bucket != null) {
            for (int i = 0; i < bucket.size(); i++) {
                if (bucket.get(i) == change) {
                    bucket.remove(i);
                    break;
                }
            }
            if (bucket.isEmpty()) {
                byImage.remove(key);
            }
        }
    }

    private static StreamNetChange firstByImage(final Map<List<Object>, List<StreamNetChange>> byImage,
                                                final List<Object> image, final String sourceTable) {
        final List<StreamNetChange> bucket = byImage.get(imageKey(image, sourceTable));
        return bucket == null || bucket.isEmpty() ? null : bucket.get(0);
    }

    public void consume() {
        // Consuming the initial rows leaves the offset at the starting point.
        if (initialRecords != null) {
            initialRecords = null;
            return;
        }
        // Consuming a stream advances the offset
        currentOffset = records.size();
        offsetSequence = SnapshotSequence.mark();
    }

    /** Total records ever captured (consumed + unconsumed) — the read-time cut for scoped consumption. */
    public long recordCount() {
        return records.size();
    }

    /**
     * Consume exactly what a reader SAW, not everything present at commit: advance past the committed
     * prefix visible at read time ({@code committedCut} records) and remove the records this
     * transaction's commit re-emitted for changes that were already buffered when the read happened
     * ({@code seenTransient} — matched by change kind, values and capture tag, at or after the cut).
     * Records appended after the read — by another transaction, or by this transaction's own LATER
     * DML — stay unconsumed. This mirrors Snowflake, where the common flush-then-load procedure
     * pattern runs each statement in its own autocommit transaction: a mid-procedure stream flush
     * must not consume rows the procedure inserts after it.
     */
    public void consumeSeen(final long committedCut, final List<StreamRecord> seenTransient) {
        if (initialRecords != null) {
            initialRecords = null;
            return;
        }
        offsetSequence = SnapshotSequence.mark();
        for (final StreamRecord seen : seenTransient) {
            // Only records at/after the cut can be this transaction's own re-emitted changes; matching
            // below it would eat an identical-valued committed record and then over-advance the offset.
            for (int i = (int) Math.max(currentOffset, committedCut); i < records.size(); i++) {
                final StreamRecord candidate = records.get(i);
                if (candidate.getChangeType() == seen.getChangeType()
                        && candidate.isUpdate() == seen.isUpdate()
                        && Objects.equals(candidate.getValues(), seen.getValues())
                        && Objects.equals(candidate.getSourceTable(), seen.getSourceTable())) {
                    records.remove(i);
                    break;
                }
            }
        }
        if (committedCut > currentOffset) {
            currentOffset = Math.min(committedCut, records.size());
        }
    }

    public void reset() {
        currentOffset = 0;
    }

    /** Owning role; defaults to SYSADMIN until stamped with the creating role at CREATE. */
    private String owner = "SYSADMIN";

    // Bare (upper-case) names of the base table(s) backing a VIEW-sourced stream — change capture
    // matches DML against any of them. A simple view has one; a UNION ALL view has one per branch.
    // Empty for TABLE-sourced streams.
    private final List<String> baseTableNames = new ArrayList<>();

    /** First base table, or null if none — kept for callers and persistence that assume a single table. */
    public String getBaseTableName() {
        return baseTableNames.isEmpty() ? null : baseTableNames.get(0);
    }

    public void setBaseTableName(final String baseTableName) {
        baseTableNames.clear();
        if (baseTableName != null) {
            baseTableNames.add(baseTableName);
        }
    }

    /** All base tables backing a VIEW-sourced stream (one per UNION ALL branch); empty for table streams. */
    public List<String> getBaseTableNames() {
        return baseTableNames;
    }

    public void setBaseTableNames(final List<String> names) {
        baseTableNames.clear();
        if (names != null) {
            baseTableNames.addAll(names);
        }
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
        if (initialRecords != null) {
            return initialRecords.size();
        }
        return records.size() - (int) currentOffset;
    }

    /** The initial rows pending before the first consumption, or null when none are. */
    public List<StreamRecord> getInitialRecords() {
        return initialRecords;
    }

    /** Hold the rows the stream reports until its first consumption, instead of its changes. */
    public void setInitialRecords(final List<StreamRecord> rows) {
        initialRecords = rows == null ? null : new ArrayList<>(rows);
    }

    /** Where the offset falls in the order table snapshots are taken in. */
    public long getOffsetSequence() {
        return offsetSequence;
    }

    /** A dynamic-table stream's image of the table's rows at the last refresh it saw, or null. */
    public List<List<Object>> getRefreshImage() {
        return refreshImage;
    }

    public void setRefreshImage(final List<List<Object>> refreshImage) {
        this.refreshImage = refreshImage;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(final String comment) {
        this.comment = comment;
    }
    /**
     * Takes over another stream's pending changes and offset, as a stream created AT another stream's offset
     * does — its initial rows and its refresh image stay its own.
     */
    public void inheritChanges(final Stream source) {
        records.clear();
        records.addAll(source.records);
        currentOffset = source.currentOffset;
        stale = source.stale;
        offsetSequence = source.offsetSequence;
    }

    /** Takes over another stream's pending changes and offset, as a clone of that stream does. */
    public void inheritOffset(final Stream source) {
        records.clear();
        records.addAll(source.records);
        currentOffset = source.currentOffset;
        stale = source.stale;
        offsetSequence = source.offsetSequence;
        initialRecords = source.initialRecords == null ? null : new ArrayList<>(source.initialRecords);
        refreshImage = source.refreshImage == null ? null : new ArrayList<>(source.refreshImage);
    }

    /** Object tags applied via ALTER ... SET TAG (canonical upper-cased tag name -&gt; value). */
    private final Map<String, String> tags = new LinkedHashMap<>();

    @Override
    public void setTag(final String tagName, final String value) {
        tags.put(tagName.toUpperCase(Locale.ROOT), value);
    }

    @Override
    public void unsetTag(final String tagName) {
        tags.remove(tagName.toUpperCase(Locale.ROOT));
    }

    @Override
    public String getTagValue(final String tagName) {
        return tags.get(tagName.toUpperCase(Locale.ROOT));
    }

    @Override
    public Map<String, String> getTagValues() {
        return new LinkedHashMap<>(tags);
    }
}
