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

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.StreamSources;
import dev.frostlake.metastore.model.ChangeType;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.StreamRecord;
import dev.frostlake.metastore.model.StreamSourceType;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.storage.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages streams and tracks changes to tables
 */
public class StreamManager {

    private static final Logger logger = LoggerFactory.getLogger(StreamManager.class);

    private final Catalog catalog;
    private final ConcurrentHashMap<String, AtomicLong> rowIdCounters;

    public StreamManager(final Catalog catalog) {
        this.catalog = catalog;
        this.rowIdCounters = new ConcurrentHashMap<>();
    }

    /**
     * Track an INSERT operation.
     *
     * @param qualifiedTableName the table written, DB.SCHEMA.NAME
     * @param row                the row inserted
     * @param rowId              the row's identity in the table's storage, which is its METADATA$ROW_ID: an
     *                           UPDATE keeps it and a DELETE reports it, so a change is told from the row it
     *                           happened to (live-verified)
     */
    public void trackInsert(final String qualifiedTableName, final Row row, final long rowId) {
        final String[] parts = QualifiedName.parse(qualifiedTableName).parts();
        if (parts.length != 3) return;

        final String dbName = parts[0];
        final String schemaName = parts[1];
        final String tableName = parts[2];

        try {
            final List<Stream> streams = streamsCapturing(dbName, schemaName, tableName);

            for (final Stream stream : streams) {
                final StreamRecord record = new StreamRecord(
                    row.getValues(),
                    ChangeType.INSERT,
                    false,
                    rowId,
                    tableName.toUpperCase(Locale.ROOT)
                );
                stream.addRecord(record);
                logger.debug("Tracked INSERT to stream: {}", stream.getName());
            }
        } catch (final Exception e) {
            logger.warn("Failed to track INSERT for streams: {}", e.getMessage());
        }
    }

    /**
     * The streams capturing {@code qualifiedTableName}, resolved ONCE for a bulk apply — commit
     * used to re-walk every schema's streams per committed ROW. An empty answer lets the caller
     * skip old-image copies and record construction entirely.
     */
    public List<Stream> capturingStreams(final String qualifiedTableName) {
        final String[] parts = QualifiedName.parse(qualifiedTableName).parts();
        if (parts.length != 3) {
            return Collections.emptyList();
        }
        try {
            return streamsCapturing(parts[0], parts[1], parts[2]);
        } catch (final Exception e) {
            logger.warn("Failed to resolve capturing streams: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** {@link #trackInsert(String, Row, long)} over a PRE-RESOLVED capture list (bulk commit apply). */
    public void trackInsert(final List<Stream> capturing, final String qualifiedTableName, final Row row,
                            final long rowId) {
        if (capturing.isEmpty()) {
            return;
        }
        final String tableName = QualifiedName.parse(qualifiedTableName).last();
        for (final Stream stream : capturing) {
            stream.addRecord(new StreamRecord(row.getValues(), ChangeType.INSERT, false, rowId,
                tableName.toUpperCase(Locale.ROOT)));
        }
    }

    /** {@link #trackUpdate(String, Row, Row, long)} over a PRE-RESOLVED capture list (bulk commit apply). */
    public void trackUpdate(final List<Stream> capturing, final String qualifiedTableName,
                            final Row oldRow, final Row newRow, final long rowId) {
        if (capturing.isEmpty()) {
            return;
        }
        final String tableName = QualifiedName.parse(qualifiedTableName).last();
        for (final Stream stream : capturing) {
            stream.addRecord(new StreamRecord(oldRow.getValues(), ChangeType.DELETE, true, rowId,
                tableName.toUpperCase(Locale.ROOT)));
            stream.addRecord(new StreamRecord(newRow.getValues(), ChangeType.INSERT, true, rowId,
                tableName.toUpperCase(Locale.ROOT)));
        }
    }

    /** {@link #trackDelete(String, Row, long)} over a PRE-RESOLVED capture list (bulk commit apply). */
    public void trackDelete(final List<Stream> capturing, final String qualifiedTableName, final Row row,
                            final long rowId) {
        if (capturing.isEmpty()) {
            return;
        }
        final String tableName = QualifiedName.parse(qualifiedTableName).last();
        for (final Stream stream : capturing) {
            stream.addRecord(new StreamRecord(row.getValues(), ChangeType.DELETE, false, rowId,
                tableName.toUpperCase(Locale.ROOT)));
        }
    }

    /**
     * Every stream that captures changes to {@code db.schema.table}, searched across every schema of every
     * database: a stream commonly lives in another schema from the table it reads (e.g. a {@code BASE_TRANSFORM}
     * stream ON an {@code INGEST} table), or in another database altogether, and looking only near the table meant
     * such a stream never saw a committed change. A stream is matched on the table ITSELF (StreamSources) — a table
     * stream on its table, a view stream on the tables its view reads — so two same-named tables in different
     * schemas cannot cross-capture, a renamed or swapped table keeps feeding its streams, and a new table under a
     * dropped one's name feeds none of them.
     */
    private List<Stream> streamsCapturing(final String dbName, final String schemaName, final String tableName) {
        final List<Stream> out = new ArrayList<>();
        final Database database = catalog.findDatabase(dbName);
        if (database == null) {
            return out;
        }
        final Schema changedSchema = database.hasSchema(schemaName) ? database.getSchema(schemaName) : null;
        final Table changed = changedSchema != null && changedSchema.hasTable(tableName)
            ? changedSchema.getTable(tableName) : null;
        if (changed == null) {
            return out;
        }
        for (final Database holder : catalog.getAllDatabases()) {
            for (final Schema schema : holder.getAllSchemas()) {
                for (final Stream stream : schema.getStreams()) {
                    if (stream.getSourceType() == StreamSourceType.STAGE
                            || stream.getSourceType() == StreamSourceType.DYNAMIC_TABLE) {
                        continue; // their changes come from a directory refresh or a table refresh, not DML
                    }
                    final boolean captures = stream.getSourceType() == StreamSourceType.VIEW
                        ? StreamSources.capturesThroughView(catalog, stream, changed)
                        : StreamSources.captures(catalog, schema, stream, changed);
                    if (captures) {
                        out.add(stream);
                    }
                }
            }
        }
        return out;
    }

    /**
     * Track an UPDATE operation: the row's old image leaves and its new image arrives, both under the row's id.
     *
     * @param qualifiedTableName the table written, DB.SCHEMA.NAME
     * @param oldRow             the row as it was
     * @param newRow             the row as it is
     * @param rowId              the row's identity in the table's storage, unchanged by the update
     */
    public void trackUpdate(final String qualifiedTableName, final Row oldRow, final Row newRow, final long rowId) {
        final String[] parts = QualifiedName.parse(qualifiedTableName).parts();
        if (parts.length != 3) return;

        final String dbName = parts[0];
        final String schemaName = parts[1];
        final String tableName = parts[2];

        try {
            final List<Stream> streams = streamsCapturing(dbName, schemaName, tableName);

            for (final Stream stream : streams) {
                // For updates, streams track both DELETE and INSERT
                final StreamRecord deleteRecord = new StreamRecord(
                    oldRow.getValues(),
                    ChangeType.DELETE,
                    true,
                    rowId,
                    tableName.toUpperCase(Locale.ROOT)
                );
                stream.addRecord(deleteRecord);

                final StreamRecord insertRecord = new StreamRecord(
                    newRow.getValues(),
                    ChangeType.INSERT,
                    true,
                    rowId,
                    tableName.toUpperCase(Locale.ROOT)
                );
                stream.addRecord(insertRecord);

                logger.debug("Tracked UPDATE to stream: {}", stream.getName());
            }
        } catch (final Exception e) {
            logger.warn("Failed to track UPDATE for streams: {}", e.getMessage());
        }
    }

    /**
     * Track a DELETE operation.
     *
     * @param qualifiedTableName the table written, DB.SCHEMA.NAME
     * @param row                the row removed
     * @param rowId              the identity the row had in the table's storage
     */
    public void trackDelete(final String qualifiedTableName, final Row row, final long rowId) {
        final String[] parts = QualifiedName.parse(qualifiedTableName).parts();
        if (parts.length != 3) return;

        final String dbName = parts[0];
        final String schemaName = parts[1];
        final String tableName = parts[2];

        try {
            final List<Stream> streams = streamsCapturing(dbName, schemaName, tableName);

            for (final Stream stream : streams) {
                final StreamRecord record = new StreamRecord(
                    row.getValues(),
                    ChangeType.DELETE,
                    false,
                    rowId,
                    tableName.toUpperCase(Locale.ROOT)
                );
                stream.addRecord(record);
                logger.debug("Tracked DELETE to stream: {}", stream.getName());
            }
        } catch (final Exception e) {
            logger.warn("Failed to track DELETE for streams: {}", e.getMessage());
        }
    }

    /**
     * Record the changes a source that is not written by DML reports at once — a stage's directory table at a
     * refresh, a dynamic table at a refresh — into every stream of that kind over it in the database: first the
     * rows that left, then the rows that arrived.
     *
     * @param databaseName the source's database
     * @param kind         the kind of source
     * @param sourceName   the source's qualified name, DB.SCHEMA.NAME
     * @param removed      the rows that left
     * @param added        the rows that arrived
     */
    public void trackRefresh(final String databaseName, final StreamSourceType kind, final String sourceName,
                             final List<List<Object>> removed, final List<List<Object>> added) {
        for (final Stream stream : streamsOver(databaseName, kind, sourceName)) {
            final String rowIdKey = sourceName.toUpperCase(Locale.ROOT) + "#" + stream.getName();
            for (final List<Object> values : removed) {
                stream.addRecord(new StreamRecord(values, ChangeType.DELETE, false, getNextRowId(rowIdKey)));
            }
            for (final List<Object> values : added) {
                stream.addRecord(new StreamRecord(values, ChangeType.INSERT, false, getNextRowId(rowIdKey)));
            }
        }
    }

    /**
     * Record a dynamic table's refresh into every stream over it: the rows that left and arrived since the
     * image of the table each stream last saw, which becomes the new image.
     *
     * @param databaseName the dynamic table's database
     * @param tableName    its qualified name, DB.SCHEMA.NAME
     * @param rows         its rows as the refresh leaves them
     */
    public void trackDynamicTableRefresh(final String databaseName, final String tableName,
                                         final List<List<Object>> rows) {
        trackDynamicTableRefresh(databaseName, tableName, rows, null);
    }

    /**
     * {@link #trackDynamicTableRefresh(String, String, List)} with each row's identity: where the stream's image
     * knows its rows' too, a row of one identity whose values changed is an update, a DELETE and an INSERT under one
     * row id with METADATA$ISUPDATE TRUE, and a row deleted and inserted again under another identity is both
     * (live-verified).
     *
     * @param databaseName the dynamic table's database
     * @param tableName    its qualified name, DB.SCHEMA.NAME
     * @param rows         its rows as the refresh leaves them
     * @param ids          their identities, in their order, or null when not known
     */
    public void trackDynamicTableRefresh(final String databaseName, final String tableName,
                                         final List<List<Object>> rows, final List<String> ids) {
        for (final Stream stream : streamsOver(databaseName, StreamSourceType.DYNAMIC_TABLE, tableName)) {
            addRefreshChanges(stream, tableName, rows, ids);
        }
    }

    /** The changes from a stream's image to a refresh's rows, recorded in the stream, which takes them as its image. */
    private void addRefreshChanges(final Stream stream, final String tableName, final List<List<Object>> rows,
                                   final List<String> ids) {
        final List<List<Object>> before = stream.getRefreshImage() == null
            ? new ArrayList<List<Object>>() : stream.getRefreshImage();
        final String rowIdKey = tableName.toUpperCase(Locale.ROOT) + "#" + stream.getName();
        final List<String> beforeIds = stream.getRefreshImage() == null ? new ArrayList<String>()
            : stream.getRefreshImageIds();
        if (ids != null && beforeIds != null && ids.size() == rows.size()) {
            addIdentifiedChanges(stream, before, beforeIds, rows, ids);
        } else {
            final List<List<Object>> removed = new ArrayList<>();
            final List<List<Object>> added = new ArrayList<>();
            diff(before, rows, removed, added);
            for (final List<Object> values : removed) {
                stream.addRecord(new StreamRecord(values, ChangeType.DELETE, false, getNextRowId(rowIdKey)));
            }
            for (final List<Object> values : added) {
                stream.addRecord(new StreamRecord(values, ChangeType.INSERT, false, getNextRowId(rowIdKey)));
            }
        }
        stream.setRefreshImage(new ArrayList<>(rows), ids);
    }

    /** The changes between two images whose rows are known by identity (see trackDynamicTableRefresh). */
    private void addIdentifiedChanges(final Stream stream, final List<List<Object>> before,
                                      final List<String> beforeIds, final List<List<Object>> after,
                                      final List<String> afterIds) {
        final Map<String, List<Integer>> was = new LinkedHashMap<>();
        for (int i = 0; i < before.size(); i++) {
            List<Integer> held = was.get(beforeIds.get(i));
            if (held == null) {
                held = new ArrayList<>();
                was.put(beforeIds.get(i), held);
            }
            held.add(Integer.valueOf(i));
        }
        final List<StreamRecord> deleted = new ArrayList<>();
        final List<StreamRecord> updated = new ArrayList<>();
        final List<StreamRecord> inserted = new ArrayList<>();
        for (int i = 0; i < after.size(); i++) {
            final List<Integer> held = was.get(afterIds.get(i));
            if (held == null || held.isEmpty()) {
                inserted.add(new StreamRecord(after.get(i), ChangeType.INSERT, false,
                    identityRowId(afterIds.get(i))));
                continue;
            }
            final List<Object> old = before.get(held.remove(0).intValue());
            if (!old.equals(after.get(i))) {
                // An update's two halves stand together, where the stream's net changes pair them.
                final long rowId = identityRowId(afterIds.get(i));
                updated.add(new StreamRecord(old, ChangeType.DELETE, true, rowId));
                updated.add(new StreamRecord(after.get(i), ChangeType.INSERT, true, rowId));
            }
        }
        for (final Map.Entry<String, List<Integer>> left : was.entrySet()) {
            for (final Integer index : left.getValue()) {
                deleted.add(new StreamRecord(before.get(index.intValue()), ChangeType.DELETE, false,
                    identityRowId(left.getKey())));
            }
        }
        for (final StreamRecord record : deleted) {
            stream.addRecord(record);
        }
        for (final StreamRecord record : updated) {
            stream.addRecord(record);
        }
        for (final StreamRecord record : inserted) {
            stream.addRecord(record);
        }
    }

    /**
     * Bring a new stream over a dynamic table, started from a past refresh's rows (its refresh image), up to the
     * table's last refresh: the rows that left and arrived since are pending in it, and the last refresh's rows
     * become its image, as {@link #trackDynamicTableRefresh} would have left them refresh by refresh.
     *
     * @param stream    the stream, its image the rows of the refresh it starts from
     * @param tableName the dynamic table's qualified name, DB.SCHEMA.NAME
     * @param rows      the rows of the table's last refresh
     */
    public void catchUpDynamicTableStream(final Stream stream, final String tableName,
                                          final List<List<Object>> rows) {
        catchUpDynamicTableStream(stream, tableName, rows, null);
    }

    /**
     * {@link #catchUpDynamicTableStream(Stream, String, List)} with the last refresh's row identities, an updated
     * row paired as {@link #trackDynamicTableRefresh(String, String, List, List)} pairs it.
     *
     * @param stream    the stream, its image the rows of the refresh it starts from
     * @param tableName the dynamic table's qualified name, DB.SCHEMA.NAME
     * @param rows      the rows of the table's last refresh
     * @param ids       their identities, in their order, or null when not known
     */
    public void catchUpDynamicTableStream(final Stream stream, final String tableName,
                                          final List<List<Object>> rows, final List<String> ids) {
        addRefreshChanges(stream, tableName, rows, ids);
    }

    /**
     * The multiset difference of two row images: each row of {@code before} no row of {@code after} matches is
     * removed, each row of {@code after} no row of {@code before} matches is added.
     */
    public static void diff(final List<List<Object>> before, final List<List<Object>> after,
                            final List<List<Object>> removed, final List<List<Object>> added) {
        // Rows are matched by their values themselves, so two rows whose values merely print alike stay apart.
        final Map<List<Object>, Integer> remaining = new HashMap<>();
        for (final List<Object> row : before) {
            final Integer count = remaining.get(row);
            remaining.put(row, count == null ? 1 : count.intValue() + 1);
        }
        for (final List<Object> row : after) {
            final Integer count = remaining.get(row);
            if (count != null && count.intValue() > 0) {
                remaining.put(row, count.intValue() - 1);
            } else {
                added.add(row);
            }
        }
        for (final List<Object> row : before) {
            final List<Object> key = row;
            final Integer count = remaining.get(key);
            if (count != null && count.intValue() > 0) {
                removed.add(row);
                remaining.put(key, count.intValue() - 1);
            }
        }
    }

    /**
     * The streams of one source kind over one source, across the database's schemas.
     *
     * @param databaseName the source's database
     * @param kind         the kind of source
     * @param sourceName   the source's qualified name, DB.SCHEMA.NAME
     * @return the streams
     */
    public List<Stream> streamsOver(final String databaseName, final StreamSourceType kind, final String sourceName) {
        final List<Stream> out = new ArrayList<>();
        final Database database = catalog.getDatabase(databaseName);
        if (database == null) {
            return out;
        }
        for (final Schema schema : database.getAllSchemas()) {
            for (final Stream stream : schema.getStreams()) {
                if (stream.getSourceType() == kind && sourceName.equalsIgnoreCase(stream.getSourceTableName())) {
                    out.add(stream);
                }
            }
        }
        return out;
    }

    /**
     * Removes a dropped column's slot from the change records every stream over {@code qualifiedTableName}
     * captured from it. A table stream's records all come from that table; a view stream narrows only the
     * records it captured from this one of its base tables.
     *
     * @param qualifiedTableName the table whose column was dropped
     * @param index              the dropped column's position before the drop
     */
    public void onColumnDropped(final String qualifiedTableName, final int index) {
        final String[] parts = QualifiedName.parse(qualifiedTableName).parts();
        if (parts.length != 3) {
            return;
        }
        final String tableName = parts[2].toUpperCase(Locale.ROOT);
        for (final Stream stream : capturingStreams(qualifiedTableName)) {
            final boolean viewStream = stream.getSourceType() == StreamSourceType.VIEW
                || !stream.getBaseTableNames().isEmpty();
            stream.dropColumnSlot(index, viewStream ? tableName : null);
        }
    }

    /**
     * Appends a newly added column's value to the change records every stream over
     * {@code qualifiedTableName} captured from it before the add.
     *
     * @param qualifiedTableName the table the column was added to
     * @param width              the table's column count after the add
     * @param value              the value the new column takes in rows that already exist
     */
    public void onColumnAdded(final String qualifiedTableName, final int width, final Object value) {
        final String[] parts = QualifiedName.parse(qualifiedTableName).parts();
        if (parts.length != 3) {
            return;
        }
        final String tableName = parts[2].toUpperCase(Locale.ROOT);
        for (final Stream stream : capturingStreams(qualifiedTableName)) {
            final boolean viewStream = stream.getSourceType() == StreamSourceType.VIEW
                || !stream.getBaseTableNames().isEmpty();
            stream.appendColumnSlot(width, value, viewStream ? tableName : null);
        }
    }

    /**
     * Forget the row-id counters kept under a dropped source's name, so {@link #rowIdCounters} does not grow
     * without bound as objects are created and dropped over the engine's lifetime. Keyed by the fully-qualified
     * name, normalized to upper case to match the write path ({@link #getNextRowId}).
     */
    public void onTableDropped(final String qualifiedTableName) {
        if (qualifiedTableName != null) {
            rowIdCounters.remove(qualifiedTableName.toUpperCase(Locale.ROOT));
        }
    }

    /** Forget the counters of every table in a dropped schema (DROP SCHEMA cascades to its tables). */
    public void onSchemaDropped(final String databaseName, final String schemaName) {
        if (databaseName != null && schemaName != null) {
            removeByPrefix((databaseName + "." + schemaName + ".").toUpperCase(Locale.ROOT));
        }
    }

    /** Forget the counters of every table in a dropped database (DROP DATABASE cascades). */
    public void onDatabaseDropped(final String databaseName) {
        if (databaseName != null) {
            removeByPrefix((databaseName + ".").toUpperCase(Locale.ROOT));
        }
    }

    private void removeByPrefix(final String prefix) {
        final Iterator<String> it = rowIdCounters.keySet().iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(prefix)) {
                it.remove();
            }
        }
    }

    /**
     * The row id of a dynamic table's row known by an identity: the same at every refresh, so a row that leaves and
     * comes back unchanged nets to no change (live-verified).
     *
     * @param identity the row's identity, a digest's hex text
     * @return its row id
     */
    private static long identityRowId(final String identity) {
        return Long.parseLong(identity.substring(0, 15), 16);
    }

    /**
     * The next row id of a source no DML writes, a stage's directory table or a dynamic table, counted per stream
     * over it. A table's rows carry their own identity instead, which the write paths hand to the track methods.
     */
    private long getNextRowId(final String sourceAndStream) {
        // putIfAbsent rather than get/put: two threads racing here must end up sharing ONE counter,
        // or the same row id would be handed out twice.
        final String key = sourceAndStream.toUpperCase(Locale.ROOT);
        AtomicLong counter = rowIdCounters.get(key);
        if (counter == null) {
            counter = new AtomicLong(1);
            final AtomicLong raced = rowIdCounters.putIfAbsent(key, counter);
            if (raced != null) {
                counter = raced;
            }
        }
        return counter.getAndIncrement();
    }
}
