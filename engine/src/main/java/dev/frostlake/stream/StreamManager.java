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

import dev.frostlake.metastore.*;
import dev.frostlake.metastore.model.ChangeType;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.StreamRecord;
import dev.frostlake.storage.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
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
        String[] parts = QualifiedName.parse(qualifiedTableName).parts();
        if (parts.length != 3) return;

        String dbName = parts[0];
        String schemaName = parts[1];
        String tableName = parts[2];

        try {
            Schema schema = catalog.getDatabase(dbName).getSchema(schemaName);
            List<Stream> streams = schema.getStreams();

            long rowId = getNextRowId(qualifiedTableName);

            for (final Stream stream : streams) {
                if (capturesTable(stream, tableName)) {
                    StreamRecord record = new StreamRecord(
                        row.getValues(),
                        ChangeType.INSERT,
                        false,
                        rowId
                    );
                    stream.addRecord(record);
                    logger.debug("Tracked INSERT to stream: {}", stream.getName());
                }
            }
        } catch (final Exception e) {
            logger.warn("Failed to track INSERT for streams: {}", e.getMessage());
        }
    }

    /**
     * Track an UPDATE operation
     */
    public void trackUpdate(final String qualifiedTableName, final Row oldRow, final Row newRow) {
        String[] parts = QualifiedName.parse(qualifiedTableName).parts();
        if (parts.length != 3) return;

        String dbName = parts[0];
        String schemaName = parts[1];
        String tableName = parts[2];

        try {
            Schema schema = catalog.getDatabase(dbName).getSchema(schemaName);
            List<Stream> streams = schema.getStreams();

            long rowId = getNextRowId(qualifiedTableName);

            for (final Stream stream : streams) {
                if (capturesTable(stream, tableName)) {
                    // For updates, streams track both DELETE and INSERT
                    StreamRecord deleteRecord = new StreamRecord(
                        oldRow.getValues(),
                        ChangeType.DELETE,
                        true,
                        rowId
                    );
                    stream.addRecord(deleteRecord);

                    StreamRecord insertRecord = new StreamRecord(
                        newRow.getValues(),
                        ChangeType.INSERT,
                        true,
                        rowId
                    );
                    stream.addRecord(insertRecord);

                    logger.debug("Tracked UPDATE to stream: {}", stream.getName());
                }
            }
        } catch (final Exception e) {
            logger.warn("Failed to track UPDATE for streams: {}", e.getMessage());
        }
    }

    /**
     * Track a DELETE operation
     */
    public void trackDelete(final String qualifiedTableName, final Row row) {
        String[] parts = QualifiedName.parse(qualifiedTableName).parts();
        if (parts.length != 3) return;

        String dbName = parts[0];
        String schemaName = parts[1];
        String tableName = parts[2];

        try {
            Schema schema = catalog.getDatabase(dbName).getSchema(schemaName);
            List<Stream> streams = schema.getStreams();

            long rowId = getNextRowId(qualifiedTableName);

            for (final Stream stream : streams) {
                if (capturesTable(stream, tableName)) {
                    StreamRecord record = new StreamRecord(
                        row.getValues(),
                        ChangeType.DELETE,
                        false,
                        rowId
                    );
                    stream.addRecord(record);
                    logger.debug("Tracked DELETE to stream: {}", stream.getName());
                }
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
        String captureName = stream.getBaseTableName() != null
            ? stream.getBaseTableName() : stream.getSourceTableName();
        final int dot = captureName.lastIndexOf('.');
        if (dot >= 0) {
            captureName = captureName.substring(dot + 1);
        }
        return captureName.equalsIgnoreCase(tableName);
    }

    /**
     * Seed a freshly created SHOW_INITIAL_ROWS stream with the source table's existing rows as
     * INSERT records, so the stream's first read returns the current table contents (Snowflake
     * semantics). Subsequent reads after consumption then return only new changes as usual.
     */
    public void seedInitialRows(final Stream stream, final String qualifiedTableName, final List<Row> rows) {
        for (final Row row : rows) {
            stream.addRecord(new StreamRecord(
                row.getValues(),
                ChangeType.INSERT,
                false,
                getNextRowId(qualifiedTableName)
            ));
        }
        logger.debug("Seeded stream {} with {} initial rows", stream.getName(), rows.size());
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
        return rowIdCounters
            .computeIfAbsent(qualifiedTableName.toUpperCase(), (final var k) -> new AtomicLong(1))
            .getAndIncrement();
    }
}
