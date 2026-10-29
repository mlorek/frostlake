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

import dev.frostlake.executor.StatementClock;
import dev.frostlake.metastore.Named;
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

/**
 * Represents a Snowflake Stream - Change Data Capture (CDC) mechanism
 * Streams record DML changes (INSERT, UPDATE, DELETE) made to a table
 */
public class Stream implements Named, Taggable {

    private final String name;
    private String sourceTableName;
    /**
     * The table a table stream reads, once bound: the table ITSELF, not whatever its name later names. A rename,
     * a move or a swap keeps the stream on it, and a table created afterwards under the old name is another
     * table. Snowflake documents both: "Renaming a source object does not break a stream", and when "a source
     * object is dropped and a new object is created with the same name", the stream "is not linked to the new
     * object". Null until bound; a stream restored from a snapshot or copied by a clone binds on first use, by
     * its recorded name read where the stream lives (see {@code StreamSources}).
     */
    private Table sourceTable;
    /** The view a view stream is bound to, null while it is not bound yet. */
    private View sourceView;
    /**
     * The tables a VIEW stream's view reads, bound like {@link #sourceTable}, in the order of
     * {@link #getBaseTableNames}: null until bound.
     */
    private List<Table> baseTables;
    private final StreamSourceType sourceType;
    private final StreamType streamType;
    private final boolean showInitialRows;
    private final List<StreamRecord> records;
    private LocalDateTime createdAt;
    private long currentOffset;
    private boolean stale;
    /** Whether the stream is a table stream cloned with its schema or database after its table went. */
    private boolean lostInClone;
    /** Whether the stream was re-bound to tables that replaced the ones it read (see {@link #restartOn}). */
    private boolean rebound;
    /** The tables a re-bind that kept the stream's changes lists it as reading (see {@link #showOn}), or null. */
    private List<Table> shownTables;
    private String comment;
    /**
     * The rows a SHOW_INITIAL_ROWS stream reports until it is first consumed — the source's rows at the
     * stream's starting point, as inserts — or null once consumed. While they are pending no change shows;
     * consuming them leaves the offset at the starting point, so the changes made since then show next.
     */
    private List<StreamRecord> initialRecords;
    /** The same moment in the order table snapshots are taken in, which tells apart moments one millisecond holds. */
    private long offsetSequence = SnapshotSequence.mark();
    /** The versions of the tables the stream reads that commits wrote past its offset, the oldest first. */
    private final List<StreamVersion> versions = new ArrayList<>();
    /** A dynamic-table stream's image of the table's rows at the last refresh it saw. */
    private List<List<Object>> refreshImage;
    /** The identities of {@link #refreshImage}'s rows, in its order, or null when not known. */
    private List<String> refreshImageIds;

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
        // The creating statement's instant, which a replayed statement reproduces (see SqlObject).
        this.createdAt = StatementClock.hostLocal();
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
     * ids; a dynamic table's row known by an identity keeps one id, so its INSERT after its DELETE is
     * the row coming back, an update of it or no change at all. APPEND_ONLY streams record only true
     * INSERTs, so they are returned as-is.
     */
    public List<StreamRecord> getUnconsumedNetRecords() {
        if (initialRecords != null) {
            return new ArrayList<>(initialRecords);
        }
        return netRecordsOf(getUnconsumedRecords());
    }

    /**
     * The net unconsumed records as {@link #getUnconsumedNetRecords}, of the records before {@code cut} alone: what a
     * read inside a transaction lists, the changes captured before the transaction began (see
     * {@link #recordsBefore}). The transaction's own changes are not among them (live-verified).
     *
     * @param cut the records the read ends at
     * @return the net records
     */
    public List<StreamRecord> getUnconsumedNetRecordsUpTo(final long cut) {
        if (initialRecords != null) {
            return new ArrayList<>(initialRecords);
        }
        return netRecordsOf(unconsumedRecordsUpTo(cut));
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
     * <p>{@code cut} ends the window as it ends {@link #getUnconsumedNetRecordsUpTo}'s.
     */
    public List<StreamRecord> getUnconsumedAppendsUpTo(final long cut) {
        if (initialRecords != null) {
            return new ArrayList<>(initialRecords);
        }
        final List<StreamRecord> appends = new ArrayList<>();
        for (final StreamRecord record : unconsumedRecordsUpTo(cut)) {
            if (isAppend(record)) {
                appends.add(record);
            }
        }
        return appends;
    }

    private List<StreamRecord> unconsumedRecordsUpTo(final long cut) {
        final int end = (int) Math.min(cut, records.size());
        if (currentOffset >= end) {
            return new ArrayList<>();
        }
        return new ArrayList<>(records.subList((int) currentOffset, end));
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
        // A deleted row by its id, which an INSERT of the same id brings back.
        final Map<List<Object>, StreamNetChange> deletedById = new HashMap<>();
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
                final StreamNetChange back = deletedById.remove(idKey(record));
                if (back != null) {
                    back.setNewValues(record.getValues());
                    indexImage(byImage, creationSeq, back);
                    continue;
                }
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
                    deletedById.put(idKey(record), change);
                } else if (existing.getOldValues() == null) {
                    unindexImage(byImage, existing);
                    net.remove(existing);
                } else {
                    unindexImage(byImage, existing);
                    existing.setNewValues(null);
                    deletedById.put(idKey(record), existing);
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
            } else if (change.getOldValues().equals(change.getNewValues())) {
                // An update that leaves the row as it was is no change of it (live-verified).
                continue;
            } else {
                out.add(new StreamRecord(change.getOldValues(), ChangeType.DELETE, true, change.getRowId(), change.getSourceTable()));
                out.add(new StreamRecord(change.getNewValues(), ChangeType.INSERT, true, change.getRowId(), change.getSourceTable()));
            }
        }
        return out;
    }

    /** A record's row id with the table it is of, which another record of the row shares. */
    private static List<Object> idKey(final StreamRecord record) {
        final List<Object> key = new ArrayList<>(2);
        key.add(record.getSourceTable());
        key.add(Long.valueOf(record.getRowId()));
        return key;
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
        dropConsumedVersions();
    }

    /**
     * Notes a version a commit wrote of a table the stream reads.
     *
     * @param version the version
     */
    public void addVersion(final StreamVersion version) {
        versions.add(version);
    }

    /** @return the versions past the stream's offset, the oldest first, as SYSTEM$STREAM_BACKLOG lists them */
    public List<StreamVersion> getPendingVersions() {
        final List<StreamVersion> pending = new ArrayList<>();
        for (final StreamVersion version : versions) {
            if (version.getSequence() > offsetSequence) {
                pending.add(version);
            }
        }
        return pending;
    }

    private void dropConsumedVersions() {
        for (int i = versions.size() - 1; i >= 0; i--) {
            if (versions.get(i).getSequence() <= offsetSequence) {
                versions.remove(i);
            }
        }
    }

    /** Total records ever captured (consumed + unconsumed): the window a read outside any transaction ends at. */
    public long recordCount() {
        return records.size();
    }

    /**
     * How many of the records were captured before a moment: a read inside a transaction that began at it lists the
     * changes up to there, and the changes committed since, by any session, wait for a later read (live-verified).
     *
     * @param sequence the snapshot number the transaction began at
     * @return the records captured before it, never fewer than the consumed ones
     */
    public long recordsBefore(final long sequence) {
        int cut = records.size();
        while (cut > currentOffset && records.get(cut - 1).getSequence() >= sequence) {
            cut--;
        }
        return cut;
    }

    /**
     * Consume the changes a transaction's reads listed: advance the offset to {@code cut}, the records captured before
     * the transaction began, and no further. The changes committed since, the transaction's own among them, stay for
     * the next read (live-verified).
     *
     * @param cut      the records the reads ended at
     * @param sequence the snapshot number the transaction began at, where the offset now falls among the snapshots
     */
    public void consumeTo(final long cut, final long sequence) {
        if (initialRecords != null) {
            initialRecords = null;
            return;
        }
        offsetSequence = Math.max(offsetSequence, sequence);
        dropConsumedVersions();
        if (cut > currentOffset) {
            currentOffset = Math.min(cut, records.size());
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

    @Override
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

    /**
     * Re-spells the recorded source after its table was renamed or moved, so a snapshot or a clone binds the
     * stream to the same table again.
     *
     * @param sourceTableName the table's name now, read from where the stream lives
     */
    public void setSourceTableName(final String sourceTableName) {
        this.sourceTableName = sourceTableName;
    }

    /** The table this table stream is bound to, or null while it is not bound yet. */
    public Table getSourceTable() {
        return sourceTable;
    }

    /** Binds this table stream to its table. */
    public void bindSourceTable(final Table table) {
        this.sourceTable = table;
    }

    /** The view this view stream is bound to, or null while it is not bound yet. */
    public View getSourceView() {
        return sourceView;
    }

    /** Binds this view stream to its view, which it follows through a rename or a move. */
    public void bindSourceView(final View view) {
        this.sourceView = view;
    }

    /** The tables this view stream is bound to, or null while it is not bound yet. */
    public List<Table> getBaseTables() {
        return baseTables;
    }

    /** Binds this view stream to the tables its view reads, in the order its view's query names them. */
    public void bindBaseTables(final List<Table> tables) {
        this.baseTables = tables == null ? null : new ArrayList<>(tables);
    }

    /**
     * Binds this view stream, which holds no change it has not read, to the tables its view's names read now in place
     * of tables that were dropped or replaced: it reads those from then on, its offset where it was.
     *
     * @param tables the tables, in the order its view's query names them
     */
    public void restartOn(final List<Table> tables) {
        bindBaseTables(tables);
        rebound = true;
        shownTables = null;
        initialRecords = null;
        currentOffset = records.size();
        versions.clear();
        stale = false;
    }

    /**
     * Lists this view stream as reading the tables its view's names read now, while it keeps the changes it has not
     * read of the tables it was bound to, and reads those tables still (live-verified: after UNDROP brings one back the
     * stream reads its changes, while SHOW STREAMS lists the table that replaced it, renamed or not).
     *
     * @param tables the tables, in the order its view's query names them
     */
    public void showOn(final List<Table> tables) {
        shownTables = new ArrayList<>(tables);
    }

    /** @return the tables a re-bind that kept the stream's changes lists it as reading, or null */
    public List<Table> getShownTables() {
        return shownTables;
    }

    /**
     * Whether this view stream was re-bound to tables that replaced the ones it read: its range then begins before
     * the time those tables tracked changes from (see StreamSources#trackingGapOf).
     *
     * @return true for such a stream
     */
    public boolean isRebound() {
        return rebound;
    }

    /** Marks this stream re-bound, as a restored one whose snapshot says so. */
    public void markRebound() {
        rebound = true;
    }

    /**
     * Whether this stream holds a version a commit wrote of a table after a point.
     *
     * @param baseTable the table, {@code DB.SCHEMA.TABLE}
     * @param sequence  the point
     * @return true when one of its pending versions of that table comes after it
     */
    public boolean holdsVersionAfter(final String baseTable, final long sequence) {
        for (final StreamVersion version : versions) {
            if (version.getSequence() > sequence && baseTable.equalsIgnoreCase(version.getBaseTable())) {
                return true;
            }
        }
        return false;
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

    /**
     * Put back when this object was created, as a snapshot recorded it, so an engine clone or a restore reports the
     * original creation time. A null, from a snapshot that never recorded it, keeps the time it was rebuilt.
     *
     * @param created the recorded creation time, or null
     */
    public void restoreCreatedTime(final LocalDateTime created) {
        if (created != null) {
            this.createdAt = created;
        }
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

    /**
     * Whether the stream is a table stream cloned with its schema or database after its table went: the clone has no
     * table to read, and says so where it is read, listed and described (live-verified).
     */
    public boolean isLostInClone() {
        return lostInClone;
    }

    /** Mark the stream lost in the clone that made it: stale, and bound to no table, whatever later takes the name. */
    public void markLostInClone() {
        this.lostInClone = true;
        this.stale = true;
    }

    public int getUnconsumedCount() {
        if (initialRecords != null) {
            return initialRecords.size();
        }
        return records.size() - (int) currentOffset;
    }

    /**
     * Whether SYSTEM$STREAM_HAS_DATA answers TRUE: the stream lists changes, or a table it reads took a version past
     * its offset, a change the stream does not list among them - a DELETE under an APPEND_ONLY stream, a row a view
     * does not show. The function diffs the tables' versions, not the stream's rows (documented; live-verified for an
     * APPEND_ONLY view stream after a DELETE), so it may answer TRUE where a read lists nothing.
     *
     * @return whether the stream has data
     */
    public boolean hasData() {
        return getUnconsumedCount() > 0 || !getPendingVersions().isEmpty();
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

    /**
     * Puts back where the offset fell, as a snapshot of the stream recorded it: a clone, a checkpoint's restore and a
     * write-ahead log's recovery restore the tables' snapshots under their own numbers, which the offset is read
     * against, and snapshots taken after the restore are numbered past it.
     *
     * @param sequence the offset's place, a number {@link SnapshotSequence#mark()} gave
     */
    public void restoreOffsetSequence(final long sequence) {
        offsetSequence = sequence;
        SnapshotSequence.advancePast(sequence);
    }

    /** A dynamic-table stream's image of the table's rows at the last refresh it saw, or null. */
    public List<List<Object>> getRefreshImage() {
        return refreshImage;
    }

    public void setRefreshImage(final List<List<Object>> refreshImage) {
        this.refreshImage = refreshImage;
        this.refreshImageIds = null;
    }

    /**
     * A dynamic-table stream's image with each row's identity, in its order (see
     * {@link DynamicTableRefresh#getRowIds()}).
     *
     * @param refreshImage the rows
     * @param ids          their identities, or null when not known
     */
    public void setRefreshImage(final List<List<Object>> refreshImage, final List<String> ids) {
        this.refreshImage = refreshImage;
        this.refreshImageIds = ids == null || refreshImage == null || ids.size() != refreshImage.size() ? null
            : new ArrayList<>(ids);
    }

    /** The identities of the image's rows, in its order, or null when not known. */
    public List<String> getRefreshImageIds() {
        return refreshImageIds;
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
        rebound = source.rebound;
        offsetSequence = source.offsetSequence;
        versions.clear();
        versions.addAll(source.versions);
    }

    /** Takes over another stream's pending changes and offset, as a clone of that stream does. */
    public void inheritOffset(final Stream source) {
        records.clear();
        records.addAll(source.records);
        currentOffset = source.currentOffset;
        stale = source.stale;
        rebound = source.rebound;
        offsetSequence = source.offsetSequence;
        versions.clear();
        versions.addAll(source.versions);
        initialRecords = source.initialRecords == null ? null : new ArrayList<>(source.initialRecords);
        refreshImage = source.refreshImage == null ? null : new ArrayList<>(source.refreshImage);
        refreshImageIds = source.refreshImageIds == null ? null : new ArrayList<>(source.refreshImageIds);
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
