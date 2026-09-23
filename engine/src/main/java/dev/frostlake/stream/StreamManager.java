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
import dev.frostlake.metastore.model.ChangeType;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.StreamRecord;
import dev.frostlake.metastore.model.StreamSourceType;
import dev.frostlake.storage.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
     * Track an INSERT operation
     */
    public void trackInsert(final String qualifiedTableName, final Row row) {
        final String[] parts = QualifiedName.parse(qualifiedTableName).parts();
        if (parts.length != 3) return;

        final String dbName = parts[0];
        final String schemaName = parts[1];
        final String tableName = parts[2];

        try {
            final List<Stream> streams = streamsCapturing(dbName, schemaName, tableName);

            final long rowId = getNextRowId(qualifiedTableName);

            for (final Stream stream : streams) {
                final StreamRecord record = new StreamRecord(
                    row.getValues(),
                    ChangeType.INSERT,
                    false,
                    rowId,
                    tableName.toUpperCase()
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

    /** {@link #trackInsert(String, Row)} over a PRE-RESOLVED capture list (bulk commit apply). */
    public void trackInsert(final List<Stream> capturing, final String qualifiedTableName, final Row row) {
        if (capturing.isEmpty()) {
            return;
        }
        final String tableName = QualifiedName.parse(qualifiedTableName).last();
        final long rowId = getNextRowId(qualifiedTableName);
        for (final Stream stream : capturing) {
            stream.addRecord(new StreamRecord(row.getValues(), ChangeType.INSERT, false, rowId,
                tableName.toUpperCase()));
        }
    }

    /** {@link #trackUpdate(String, Row, Row)} over a PRE-RESOLVED capture list (bulk commit apply). */
    public void trackUpdate(final List<Stream> capturing, final String qualifiedTableName,
                            final Row oldRow, final Row newRow) {
        if (capturing.isEmpty()) {
            return;
        }
        final String tableName = QualifiedName.parse(qualifiedTableName).last();
        final long rowId = getNextRowId(qualifiedTableName);
        for (final Stream stream : capturing) {
            stream.addRecord(new StreamRecord(oldRow.getValues(), ChangeType.DELETE, true, rowId,
                tableName.toUpperCase()));
            stream.addRecord(new StreamRecord(newRow.getValues(), ChangeType.INSERT, true, rowId,
                tableName.toUpperCase()));
        }
    }

    /** {@link #trackDelete(String, Row)} over a PRE-RESOLVED capture list (bulk commit apply). */
    public void trackDelete(final List<Stream> capturing, final String qualifiedTableName, final Row row) {
        if (capturing.isEmpty()) {
            return;
        }
        final String tableName = QualifiedName.parse(qualifiedTableName).last();
        final long rowId = getNextRowId(qualifiedTableName);
        for (final Stream stream : capturing) {
            stream.addRecord(new StreamRecord(row.getValues(), ChangeType.DELETE, false, rowId,
                tableName.toUpperCase()));
        }
    }

    /**
     * Every stream in the database that captures changes to {@code db.schema.table}, searched across ALL of
     * the database's schemas — a stream commonly lives in a different schema from the table it reads (e.g. a
     * {@code BASE_TRANSFORM} stream ON a {@code INGEST} table), and looking only in the mutated table's own
     * schema meant such a stream never saw a committed change at all. A TABLE stream is matched on the FULL
     * name (its source resolved against the STREAM's own database/schema for any part it omits) so two
     * same-named tables in different schemas cannot cross-capture; a VIEW stream keeps the bare-name match on
     * its recorded base tables, which a view may draw from any schema.
     */
    private List<Stream> streamsCapturing(final String dbName, final String schemaName, final String tableName) {
        final List<Stream> out = new ArrayList<>();
        final Database database = catalog.getDatabase(dbName);
        if (database == null) {
            return out;
        }
        for (final Schema schema : database.getAllSchemas()) {
            for (final Stream stream : schema.getStreams()) {
                if (stream.getSourceType() == StreamSourceType.STAGE
                        || stream.getSourceType() == StreamSourceType.DYNAMIC_TABLE) {
                    continue; // their changes come from a directory refresh or a table refresh, not DML
                }
                if (stream.getSourceType() == StreamSourceType.VIEW || !stream.getBaseTableNames().isEmpty()) {
                    if (capturesTable(stream, tableName)) {
                        out.add(stream);
                    }
                } else if (matchesSourceTable(stream, dbName, schema.getName(), schemaName, tableName)) {
                    out.add(stream);
                }
            }
        }
        return out;
    }

    /**
     * Whether a TABLE stream's source names exactly the mutated table. The source may be written unqualified
     * or schema-qualified, so the missing parts are filled from the schema the STREAM itself lives in.
     */
    private boolean matchesSourceTable(final Stream stream, final String dbName, final String streamSchemaName,
                                       final String mutatedSchemaName, final String mutatedTableName) {
        final String[] parts = QualifiedName.parse(stream.getSourceTableName()).parts();
        final String sourceTable = parts[parts.length - 1];
        final String sourceSchema = parts.length >= 2 ? parts[parts.length - 2] : streamSchemaName;
        final String sourceDb = parts.length >= 3 ? parts[parts.length - 3] : dbName;
        return sourceTable.equalsIgnoreCase(mutatedTableName)
            && sourceSchema.equalsIgnoreCase(mutatedSchemaName)
            && sourceDb.equalsIgnoreCase(dbName);
    }

    /**
     * Track an UPDATE operation
     */
    public void trackUpdate(final String qualifiedTableName, final Row oldRow, final Row newRow) {
        final String[] parts = QualifiedName.parse(qualifiedTableName).parts();
        if (parts.length != 3) return;

        final String dbName = parts[0];
        final String schemaName = parts[1];
        final String tableName = parts[2];

        try {
            final List<Stream> streams = streamsCapturing(dbName, schemaName, tableName);

            final long rowId = getNextRowId(qualifiedTableName);

            for (final Stream stream : streams) {
                // For updates, streams track both DELETE and INSERT
                final StreamRecord deleteRecord = new StreamRecord(
                    oldRow.getValues(),
                    ChangeType.DELETE,
                    true,
                    rowId,
                    tableName.toUpperCase()
                );
                stream.addRecord(deleteRecord);

                final StreamRecord insertRecord = new StreamRecord(
                    newRow.getValues(),
                    ChangeType.INSERT,
                    true,
                    rowId,
                    tableName.toUpperCase()
                );
                stream.addRecord(insertRecord);

                logger.debug("Tracked UPDATE to stream: {}", stream.getName());
            }
        } catch (final Exception e) {
            logger.warn("Failed to track UPDATE for streams: {}", e.getMessage());
        }
    }

    /**
     * Track a DELETE operation
     */
    public void trackDelete(final String qualifiedTableName, final Row row) {
        final String[] parts = QualifiedName.parse(qualifiedTableName).parts();
        if (parts.length != 3) return;

        final String dbName = parts[0];
        final String schemaName = parts[1];
        final String tableName = parts[2];

        try {
            final List<Stream> streams = streamsCapturing(dbName, schemaName, tableName);

            final long rowId = getNextRowId(qualifiedTableName);

            for (final Stream stream : streams) {
                final StreamRecord record = new StreamRecord(
                    row.getValues(),
                    ChangeType.DELETE,
                    false,
                    rowId,
                    tableName.toUpperCase()
                );
                stream.addRecord(record);
                logger.debug("Tracked DELETE to stream: {}", stream.getName());
            }
        } catch (final Exception e) {
            logger.warn("Failed to track DELETE for streams: {}", e.getMessage());
        }
    }

    /**
     * True when a change to {@code tableName} (the bare table name of the mutated table) feeds this
     * stream: its source table, or — for a stream defined ON VIEW — the view's resolved base table.
     * A qualified source name is reduced to its last segment before comparing.
     */
    private boolean capturesTable(final Stream stream, final String tableName) {
        // A view stream tracks one base table per branch (UNION ALL); a table stream tracks its source.
        final List<String> captureNames = stream.getBaseTableNames().isEmpty()
            ? Collections.singletonList(stream.getSourceTableName())
            : stream.getBaseTableNames();
        for (final String captureName : captureNames) {
            String name = captureName;
            final int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                name = name.substring(dot + 1);
            }
            if (name.equalsIgnoreCase(tableName)) {
                return true;
            }
        }
        return false;
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
            final String rowIdKey = sourceName.toUpperCase() + "#" + stream.getName();
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
        for (final Stream stream : streamsOver(databaseName, StreamSourceType.DYNAMIC_TABLE, tableName)) {
            final List<List<Object>> before = stream.getRefreshImage() == null
                ? new ArrayList<List<Object>>() : stream.getRefreshImage();
            final List<List<Object>> removed = new ArrayList<>();
            final List<List<Object>> added = new ArrayList<>();
            diff(before, rows, removed, added);
            final String rowIdKey = tableName.toUpperCase() + "#" + stream.getName();
            for (final List<Object> values : removed) {
                stream.addRecord(new StreamRecord(values, ChangeType.DELETE, false, getNextRowId(rowIdKey)));
            }
            for (final List<Object> values : added) {
                stream.addRecord(new StreamRecord(values, ChangeType.INSERT, false, getNextRowId(rowIdKey)));
            }
            stream.setRefreshImage(new ArrayList<>(rows));
        }
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
        final String tableName = parts[2].toUpperCase();
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
        final String tableName = parts[2].toUpperCase();
        for (final Stream stream : capturingStreams(qualifiedTableName)) {
            final boolean viewStream = stream.getSourceType() == StreamSourceType.VIEW
                || !stream.getBaseTableNames().isEmpty();
            stream.appendColumnSlot(width, value, viewStream ? tableName : null);
        }
    }

    /**
     * Forget a table's change-tracking row-id counter when the table is dropped, so {@link #rowIdCounters}
     * does not grow without bound as tables are created and dropped over the engine's lifetime. Keyed by
     * the fully-qualified name, normalized to upper case to match the write path ({@link #getNextRowId}).
     */
    public void onTableDropped(final String qualifiedTableName) {
        if (qualifiedTableName != null) {
            rowIdCounters.remove(qualifiedTableName.toUpperCase());
        }
    }

    /** Forget the counters of every table in a dropped schema (DROP SCHEMA cascades to its tables). */
    public void onSchemaDropped(final String databaseName, final String schemaName) {
        if (databaseName != null && schemaName != null) {
            removeByPrefix((databaseName + "." + schemaName + ".").toUpperCase());
        }
    }

    /** Forget the counters of every table in a dropped database (DROP DATABASE cascades). */
    public void onDatabaseDropped(final String databaseName) {
        if (databaseName != null) {
            removeByPrefix((databaseName + ".").toUpperCase());
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

    private long getNextRowId(final String qualifiedTableName) {
        // putIfAbsent rather than get/put: two threads racing here must end up sharing ONE counter,
        // or the same row id would be handed out twice.
        final String key = qualifiedTableName.toUpperCase();
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
