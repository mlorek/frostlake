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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.ChangeType;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.StreamRecord;
import dev.frostlake.metastore.model.StreamSourceType;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.TableSnapshot;
import dev.frostlake.storage.TableStorage;
import dev.frostlake.stream.StreamManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Where a new stream starts: now, or at the point an {@code AT | BEFORE} clause names.
 *
 * <p>{@code STREAM => '<name>'} starts it at another stream's offset: over the same source it takes that stream's
 * pending changes, over another source it starts at the moment of that stream's offset. A TIMESTAMP, OFFSET or
 * STATEMENT starts it at that version of the table — for a view, of each table the view reads. The changes
 * between the point and now are then pending in the new stream, row by row: a row the point did not hold is an
 * insert, a row gone since is a delete, and a row rewritten since is an update, its old and new values a pair.
 * A point is refused when change tracking was off while the table changed after it. A SHOW_INITIAL_ROWS stream
 * first reports the rows the source held at the point.
 *
 * <p>A dynamic table keeps no history of its refreshes here, so a stream over one starts at a point only when
 * the point is another stream's over the same table; a point before the table's first refresh is refused as the
 * account refuses it, and a later one as unsupported.
 */
final class StreamStart {

    private final QueryExecutor queryExecutor;
    private final Catalog catalog;

    StreamStart(final QueryExecutor queryExecutor, final Catalog catalog) {
        this.queryExecutor = queryExecutor;
        this.catalog = catalog;
    }

    /**
     * Start a stream over a table.
     *
     * @param stream          the new stream
     * @param table           its table
     * @param tableName       the table's qualified name
     * @param point           the AT | BEFORE point, or null
     * @param showInitialRows whether the stream first reports the table's rows at its start
     */
    void forTable(final Stream stream, final Table table, final String tableName,
                  final FrostlakeParser.StreamPointContext point, final boolean showInitialRows) {
        final TableStorage storage = storageOf(table, tableName);
        if (point == null) {
            if (showInitialRows) {
                stream.setInitialRecords(inserts(storage.scan(), null, new long[] {1L}));
            }
            return;
        }
        final TableSnapshot atPoint;
        if (point.timeTravelPoint().STREAM() != null) {
            final Stream source = sourceStream(point);
            if (sameSource(source, stream)) {
                stream.inheritChanges(source);
                if (showInitialRows) {
                    stream.setInitialRecords(inserts(queryExecutor.rowsAtStreamOffset(storage.scan(), source), null,
                        new long[] {1L}));
                }
                return;
            }
            atPoint = queryExecutor.tableSnapshotAtSequence(table, storage, source.getOffsetSequence());
        } else {
            requirePointValue(point, table.getName());
            atPoint = queryExecutor.tableSnapshotAtStreamPoint(table, storage, point);
        }
        requireTracked(table, storage, atPoint);
        final long[] rowId = {1L};
        addPendingChanges(stream, atPoint, storage, null, rowId);
        if (showInitialRows) {
            stream.setInitialRecords(inserts(rowsOf(atPoint), null, new long[] {1L}));
        }
    }

    /**
     * Start a stream over a view: the changes of each table the view reads are captured as that table's, and
     * the view is applied to them when the stream is read.
     *
     * @param stream          the new stream
     * @param baseTables      the tables the view reads, as the view names them
     * @param point           the AT | BEFORE point, or null
     * @param showInitialRows whether the stream first reports the view's rows at its start
     */
    void forView(final Stream stream, final List<String> baseTables, final FrostlakeParser.StreamPointContext point,
                 final boolean showInitialRows) {
        final List<Table> tables = new ArrayList<>();
        final List<TableStorage> storages = new ArrayList<>();
        for (final String name : baseTables) {
            final Table table = catalog.resolveTable(name);
            boolean seen = false;
            for (final Table known : tables) {
                seen = seen || known == table;
            }
            if (!seen) {
                tables.add(table);
                storages.add(storageOf(table, name));
            }
        }
        if (point == null) {
            if (showInitialRows) {
                final List<StreamRecord> initial = new ArrayList<>();
                final long[] rowId = {1L};
                for (int i = 0; i < tables.size(); i++) {
                    initial.addAll(inserts(storages.get(i).scan(), tag(tables.get(i)), rowId));
                }
                stream.setInitialRecords(initial);
            }
            return;
        }
        long sourceSequence = -1L;
        if (point.timeTravelPoint().STREAM() != null) {
            final Stream source = sourceStream(point);
            if (sameSource(source, stream)) {
                stream.inheritChanges(source);
                if (showInitialRows) {
                    final List<StreamRecord> initial = new ArrayList<>();
                    final long[] rowId = {1L};
                    for (int i = 0; i < tables.size(); i++) {
                        initial.addAll(inserts(rowsOf(queryExecutor.tableSnapshotAtSequence(tables.get(i),
                            storages.get(i), source.getOffsetSequence())), tag(tables.get(i)), rowId));
                    }
                    stream.setInitialRecords(initial);
                }
                return;
            }
            sourceSequence = source.getOffsetSequence();
        } else if (!tables.isEmpty()) {
            requirePointValue(point, tables.get(0).getName());
        }
        final List<TableSnapshot> atPoint = new ArrayList<>();
        for (int i = 0; i < tables.size(); i++) {
            final TableSnapshot snapshot = sourceSequence >= 0
                ? queryExecutor.tableSnapshotAtSequence(tables.get(i), storages.get(i), sourceSequence)
                : queryExecutor.tableSnapshotAtStreamPoint(tables.get(i), storages.get(i), point);
            requireTracked(tables.get(i), storages.get(i), snapshot);
            atPoint.add(snapshot);
        }
        final long[] rowId = {1L};
        for (int i = 0; i < tables.size(); i++) {
            addPendingChanges(stream, atPoint.get(i), storages.get(i), tag(tables.get(i)), rowId);
        }
        if (showInitialRows) {
            final List<StreamRecord> initial = new ArrayList<>();
            final long[] initialRowId = {1L};
            for (int i = 0; i < tables.size(); i++) {
                initial.addAll(inserts(rowsOf(atPoint.get(i)), tag(tables.get(i)), initialRowId));
            }
            stream.setInitialRecords(initial);
        }
    }

    /**
     * Start a stream over a dynamic table: it holds the table's rows as of its last refresh, and records what
     * each later refresh changes. {@code STREAM => '<name>'} over the same table takes that stream's pending
     * changes. Any other point before the table's first refresh is refused as a time-travel read before the
     * table was; a later one is unsupported, for the history of the table's refreshes is not kept.
     *
     * @param stream          the new stream
     * @param dynamicTable    its dynamic table
     * @param point           the AT | BEFORE point, or null
     * @param showInitialRows whether the stream first reports the table's rows
     */
    void forDynamicTable(final Stream stream, final DynamicTable dynamicTable,
                         final FrostlakeParser.StreamPointContext point, final boolean showInitialRows) {
        final List<List<Object>> image = new ArrayList<>();
        if (dynamicTable.getLastRefreshedTime() != null) {
            for (final Row row : queryExecutor.dynamicTableRows(dynamicTable)) {
                image.add(new ArrayList<>(row.getValues()));
            }
        }
        stream.setRefreshImage(image);
        if (point != null) {
            if (point.timeTravelPoint().STREAM() != null) {
                final Stream source = sourceStream(point);
                if (sameSource(source, stream)) {
                    stream.inheritChanges(source);
                } else {
                    refuseDynamicTablePoint(dynamicTable, source.getOffsetSequence() < dynamicTable.getFirstRefreshedSequence());
                }
            } else {
                requirePointValue(point, dynamicTable.getName());
                final long millis = queryExecutor.streamPointMillis(point);
                refuseDynamicTablePoint(dynamicTable, millis < dynamicTable.getFirstRefreshedMillis()
                    || point.BEFORE() != null && millis <= dynamicTable.getFirstRefreshedMillis());
            }
        }
        if (showInitialRows) {
            final List<StreamRecord> initial = new ArrayList<>();
            for (final List<Object> values : image) {
                initial.add(new StreamRecord(values, ChangeType.INSERT, false, initial.size() + 1L));
            }
            stream.setInitialRecords(initial);
        }
    }

    /** A point on a dynamic table's stream that is no stream of the same table is refused either way. */
    private static void refuseDynamicTablePoint(final DynamicTable dynamicTable, final boolean beforeFirstRefresh) {
        if (dynamicTable.getFirstRefreshedSequence() == 0L || beforeFirstRefresh) {
            throw QueryExecutor.timeTravelUnavailable(dynamicTable.getName());
        }
        throw new RuntimeException(SqlCompilationError.of("Unsupported feature 'AT | BEFORE on a stream over a "
            + "dynamic table': a point after the table's first refresh needs the history of its refreshes, which "
            + "Frostlake does not keep."));
    }

    /** The stream a {@code STREAM => '<name>'} point names, refused with the name as written when missing. */
    private Stream sourceStream(final FrostlakeParser.StreamPointContext point) {
        final FrostlakeParser.ExpressionContext expression = point.timeTravelPoint().expression();
        final Object value;
        try {
            value = queryExecutor.evaluateStandalone(expression);
        } catch (final RuntimeException unreadable) {
            // A bare name is no stream name but a column the point cannot read, refused where it stands.
            if (unreadable.getMessage() != null && unreadable.getMessage().contains("invalid identifier")) {
                throw new RuntimeException(SqlCompilationError.invalidIdentifier(expression.getStart().getLine(),
                    expression.getStart().getCharPositionInLine(), expression.getText().toUpperCase()));
            }
            throw unreadable;
        }
        if (value == null) {
            throw new RuntimeException("Time travel AT(STREAM => _) expected a stream name, got NULL.");
        }
        final String name = String.valueOf(value);
        Stream found = null;
        try {
            found = catalog.resolveStream(name);
        } catch (final RuntimeException unresolved) {
            found = null;
        }
        if (found == null) {
            throw new RuntimeException("Stream '" + name + "' not found.");
        }
        return found;
    }

    /**
     * A TIMESTAMP, OFFSET or STATEMENT point whose value is NULL, refused as the account refuses each: a NULL
     * TIMESTAMP as a time before the table, a NULL OFFSET as a value of no type, a NULL STATEMENT as a statement
     * not found.
     */
    private void requirePointValue(final FrostlakeParser.StreamPointContext point, final String tableName) {
        final FrostlakeParser.TimeTravelPointContext ptCtx = point.timeTravelPoint();
        if (queryExecutor.evaluateStandalone(ptCtx.expression()) != null) {
            return;
        }
        if (ptCtx.TIMESTAMP() != null) {
            throw QueryExecutor.timeTravelUnavailable(tableName);
        }
        if (ptCtx.OFFSET() != null) {
            throw new RuntimeException(SqlCompilationError.of("Invalid data type [null] in "
                + (point.BEFORE() != null ? "BEFORE" : "AT") + "(OFFSET => null)"));
        }
        throw new RuntimeException("Statement null not found");
    }

    /**
     * Refuse a point the table's change tracking does not cover: the table changed after it while tracking
     * was off. A point after tracking was turned on, or a table unchanged since the point, is covered.
     */
    private static void requireTracked(final Table table, final TableStorage storage, final TableSnapshot atPoint) {
        final long trackedSince = table.getChangeTrackingSince();
        final long pointSequence = atPoint == null ? -1L : atPoint.sequence;
        if (atPoint != null && pointSequence >= trackedSince) {
            return;
        }
        final TableSnapshot lastUntracked = storage.snapshotUpTo(trackedSince);
        if (lastUntracked != null && lastUntracked.sequence > pointSequence) {
            throw new RuntimeException(SqlCompilationError.inline("Change tracking is not enabled or has been "
                + "missing for the time range requested on table '" + table.getName() + "'."));
        }
    }

    /**
     * The changes between a version of a table and now, pending in the stream row by row: a row gone since is
     * a delete, a row the version did not hold an insert, and a row rewritten since an update pair.
     */
    private static void addPendingChanges(final Stream stream, final TableSnapshot atPoint, final TableStorage storage,
                                          final String tag, final long[] rowId) {
        final List<Row> beforeRows = rowsOf(atPoint);
        final List<Long> beforeIds = atPoint == null ? new ArrayList<Long>() : atPoint.rowIds;
        final List<Row> afterRows = storage.scan();
        final List<Long> afterIds = storage.getRowIds();
        if (beforeIds.size() != beforeRows.size() || afterIds.size() != afterRows.size()) {
            addValueDifference(stream, beforeRows, afterRows, tag, rowId);
            return;
        }
        final Map<Long, Row> after = new LinkedHashMap<>();
        for (int i = 0; i < afterRows.size(); i++) {
            after.put(afterIds.get(i), afterRows.get(i));
        }
        final Map<Long, Row> before = new LinkedHashMap<>();
        for (int i = 0; i < beforeRows.size(); i++) {
            before.put(beforeIds.get(i), beforeRows.get(i));
        }
        for (final Map.Entry<Long, Row> old : before.entrySet()) {
            final Row now = after.get(old.getKey());
            if (now == null) {
                stream.addRecord(record(old.getValue().getValues(), ChangeType.DELETE, false, rowId[0]++, tag));
            } else if (!Objects.equals(old.getValue().getValues(), now.getValues())) {
                final long id = rowId[0]++;
                stream.addRecord(record(old.getValue().getValues(), ChangeType.DELETE, true, id, tag));
                stream.addRecord(record(now.getValues(), ChangeType.INSERT, true, id, tag));
            }
        }
        for (final Map.Entry<Long, Row> now : after.entrySet()) {
            if (!before.containsKey(now.getKey())) {
                stream.addRecord(record(now.getValue().getValues(), ChangeType.INSERT, false, rowId[0]++, tag));
            }
        }
    }

    /** The difference of two versions by value alone, for versions whose rows carry no identity. */
    private static void addValueDifference(final Stream stream, final List<Row> before, final List<Row> after,
                                           final String tag, final long[] rowId) {
        final List<List<Object>> removed = new ArrayList<>();
        final List<List<Object>> added = new ArrayList<>();
        StreamManager.diff(values(before), values(after), removed, added);
        for (final List<Object> row : removed) {
            stream.addRecord(record(row, ChangeType.DELETE, false, rowId[0]++, tag));
        }
        for (final List<Object> row : added) {
            stream.addRecord(record(row, ChangeType.INSERT, false, rowId[0]++, tag));
        }
    }

    private static StreamRecord record(final List<Object> values, final ChangeType type, final boolean update,
                                       final long rowId, final String tag) {
        final List<Object> copy = new ArrayList<>(values);
        return tag == null ? new StreamRecord(copy, type, update, rowId) : new StreamRecord(copy, type, update, rowId, tag);
    }

    /** Whether two streams read the same object: the same kind of source under the same qualified name. */
    private boolean sameSource(final Stream a, final Stream b) {
        return a.getSourceType() == b.getSourceType() && qualified(a).equalsIgnoreCase(qualified(b));
    }

    private String qualified(final Stream stream) {
        final String name = stream.getSourceTableName();
        if (stream.getSourceType() == StreamSourceType.STAGE
                || stream.getSourceType() == StreamSourceType.DYNAMIC_TABLE) {
            return name;
        }
        try {
            final Schema schema = catalog.resolveOwningSchema(name);
            return schema.getDatabaseName() + "." + schema.getName() + "." + QualifiedName.parse(name).last();
        } catch (final RuntimeException unresolved) {
            return name;
        }
    }

    private TableStorage storageOf(final Table table, final String name) {
        final Schema schema = catalog.resolveOwningSchema(name);
        return queryExecutor.getStorageEngine().getTableStorage(
            QualifiedName.key(schema.getDatabaseName(), schema.getName(), table.getName()));
    }

    /** The tag a view stream's records carry: the name of the table they were captured from. */
    private static String tag(final Table table) {
        return table.getName().toUpperCase(Locale.ROOT);
    }

    private static List<Row> rowsOf(final TableSnapshot snapshot) {
        return snapshot == null ? new ArrayList<Row>() : new ArrayList<>(snapshot.rows);
    }

    private static List<List<Object>> values(final List<Row> rows) {
        final List<List<Object>> values = new ArrayList<>();
        for (final Row row : rows) {
            values.add(new ArrayList<>(row.getValues()));
        }
        return values;
    }

    private static List<StreamRecord> inserts(final List<Row> rows, final String tag, final long[] rowId) {
        final List<StreamRecord> records = new ArrayList<>();
        for (final Row row : rows) {
            records.add(record(row.getValues(), ChangeType.INSERT, false, rowId[0]++, tag));
        }
        return records;
    }
}
