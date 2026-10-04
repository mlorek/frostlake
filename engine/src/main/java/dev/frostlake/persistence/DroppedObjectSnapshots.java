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

package dev.frostlake.persistence;

import dev.frostlake.config.S3PathResolver;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.DroppedObject;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.StorageEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The dropped tables, dynamic tables, tags, schemas and databases UNDROP can still restore, through an engine clone
 * and a checkpoint: a snapshot that left them out made UNDROP refuse on the copy what it restored on the original,
 * and SHOW ... HISTORY list nothing. Each version is saved and restored by the code that saves and restores a live
 * object of its kind ({@link CatalogSnapshotWriter#saveSchema}, {@link CatalogSnapshotReader#loadDatabaseContent}),
 * so a version comes back as complete as a live object does. Its rows are kept beside it, never under a name a live
 * table may hold again.
 */
final class DroppedObjectSnapshots {

    private static final Logger logger = LoggerFactory.getLogger(DroppedObjectSnapshots.class);

    private DroppedObjectSnapshots() {
    }

    /**
     * Every dropped version the catalog still keeps, each name's versions the oldest first.
     *
     * @param catalog the catalog
     * @return the saved versions
     * @throws IOException never in practice: no rows are written through a store
     */
    static List<DroppedObjectSnapshot> save(final Catalog catalog) throws IOException {
        final List<DroppedObjectSnapshot> out = new ArrayList<>();
        for (final Map.Entry<String, List<DroppedObject>> entry : catalog.droppedVersions().entrySet()) {
            for (final DroppedObject version : entry.getValue()) {
                final DroppedObjectSnapshot saved = save(entry.getKey(), version);
                if (saved != null) {
                    out.add(saved);
                } else {
                    logger.debug("Dropped version not saved: {}", entry.getKey());
                }
            }
        }
        return out;
    }

    private static DroppedObjectSnapshot save(final String key, final DroppedObject version) throws IOException {
        final int colon = key.indexOf(':');
        if (colon < 0) {
            return null;
        }
        final String kind = key.substring(0, colon);
        final String[] parts = QualifiedName.parse(key.substring(colon + 1)).parts();
        final Object object = version.getObject();
        final DroppedObjectSnapshot saved = new DroppedObjectSnapshot();
        saved.key = key;
        saved.droppedOn = version.getDroppedOn();
        saved.retentionDays = version.getRetentionDays();
        saved.stageToken = version.getStageToken();
        if ("DATABASE".equals(kind) && object instanceof Database) {
            final Database database = (Database) object;
            saved.database = database.getName();
            saved.droppedDatabase = CatalogSnapshotWriter.saveDatabase(null, database, null, null);
        } else if ("SCHEMA".equals(kind) && object instanceof Schema) {
            final Schema schema = (Schema) object;
            saved.database = schema.getDatabaseName() != null ? schema.getDatabaseName() : parts[0];
            saved.schema = CatalogSnapshotWriter.saveSchema(null, saved.database, schema, null, null);
        } else if (parts.length == 3) {
            // A table, dynamic table or tag: saved inside a schema of its own, by the schema code.
            final Schema holder = new Schema(parts[1]);
            if ("TABLE".equals(kind) && object instanceof Table) {
                holder.addTable((Table) object);
            } else if ("DYNAMIC TABLE".equals(kind) && object instanceof DynamicTable) {
                holder.addDynamicTable((DynamicTable) object);
            } else if ("TAG".equals(kind) && object instanceof Tag) {
                holder.addTag((Tag) object);
            } else {
                return null;
            }
            saved.database = parts[0];
            saved.schema = CatalogSnapshotWriter.saveSchema(null, saved.database, holder, null, null);
        } else {
            return null;
        }
        saved.rows = values(version.getRows());
        saved.tableRows = values(version.getTableRows());
        saved.hiddenTableRows = values(version.getHiddenTableRows());
        return saved;
    }

    /**
     * Put the saved dropped versions back into the catalog, each name's in the order they were dropped. One that
     * cannot be rebuilt is left out with a warning, as a grant that cannot be restored is: it never stops the restore.
     *
     * @param saved the saved versions, or null for a snapshot written before they were kept
     * @param catalog the catalog
     * @param s3Resolver where a stage of a dropped schema keeps its files
     */
    static void restore(final List<DroppedObjectSnapshot> saved, final Catalog catalog,
                        final S3PathResolver s3Resolver) {
        if (saved == null) {
            return;
        }
        for (final DroppedObjectSnapshot version : saved) {
            try {
                final Object object = rebuild(version, catalog, s3Resolver);
                if (object == null) {
                    logger.warn("Dropped version not restored: {}", version.key);
                    continue;
                }
                catalog.recordDropped(keyOf(version, object), DroppedObject.restored(object, rows(version.rows),
                    rows(version.tableRows), rows(version.hiddenTableRows), version.retentionDays, version.droppedOn,
                    version.stageToken));
            } catch (final IOException | ClassNotFoundException | RuntimeException e) {
                logger.warn("Dropped version not restored: {}: {}", version.key, e.getMessage());
            }
        }
    }

    /**
     * The key a restored version goes back under: its exact canonical name ({@link Catalog#recordDropped}), spelled
     * from the names saved with it. An older snapshot's key was upper-cased; the names a database, a schema or any
     * dropped object saved for itself are exact, so only the containers of a table, dynamic table or tag such a
     * snapshot kept come back as upper-cased as it saved them.
     */
    private static String keyOf(final DroppedObjectSnapshot version, final Object object) {
        if (object instanceof Database) {
            return "DATABASE:" + ((Database) object).getName();
        }
        if (object instanceof Schema) {
            return "SCHEMA:" + QualifiedName.key(version.database, ((Schema) object).getName());
        }
        final String name = object instanceof Table ? ((Table) object).getName()
            : object instanceof DynamicTable ? ((DynamicTable) object).getName() : ((Tag) object).getName();
        return version.key.substring(0, version.key.indexOf(':')) + ":"
            + QualifiedName.key(version.database, version.schema.name, name);
    }

    /** The dropped object a saved version holds, rebuilt apart from the catalog; null when it holds none. */
    private static Object rebuild(final DroppedObjectSnapshot version, final Catalog catalog,
                                  final S3PathResolver s3Resolver) throws IOException, ClassNotFoundException {
        if (version.droppedDatabase != null) {
            final Database database = new Database(version.droppedDatabase.name);
            CatalogSnapshotReader.restoreDatabaseAttributes(database, version.droppedDatabase);
            CatalogSnapshotReader.loadDatabaseContent(new MemoryTableDataStore(), catalog, database,
                version.droppedDatabase, s3Resolver, new StorageEngine());
            return database;
        }
        if (version.schema == null) {
            return null;
        }
        // The schema is rebuilt inside a database of its own, whose storage is thrown away: the rows are the
        // version's, kept beside it.
        final Database holder = new Database(version.database);
        final DatabaseSnapshot holderSnapshot = new DatabaseSnapshot();
        holderSnapshot.name = version.database;
        holderSnapshot.schemas.add(version.schema);
        CatalogSnapshotReader.loadDatabaseContent(new MemoryTableDataStore(), catalog, holder, holderSnapshot,
            s3Resolver, new StorageEngine());
        final Schema schema = holder.schemaExact(version.schema.name);
        final String kind = version.key.substring(0, version.key.indexOf(':'));
        if ("SCHEMA".equals(kind)) {
            return holder.detachSchema(version.schema.name);
        }
        if ("TABLE".equals(kind) && version.schema.tables.size() == 1) {
            return schema.getTable(version.schema.tables.get(0).name);
        }
        if ("DYNAMIC TABLE".equals(kind) && version.schema.dynamicTables.size() == 1) {
            return schema.getDynamicTable(version.schema.dynamicTables.get(0).name);
        }
        if ("TAG".equals(kind) && version.schema.tags.size() == 1) {
            return schema.getTag(version.schema.tags.get(0).name);
        }
        return null;
    }

    private static List<List<Object>> values(final List<Row> rows) {
        if (rows == null) {
            return null;
        }
        final List<List<Object>> out = new ArrayList<>(rows.size());
        for (final Row row : rows) {
            out.add(new ArrayList<>(row.getValues()));
        }
        return out;
    }

    private static HashMap<String, List<List<Object>>> values(final Map<String, List<Row>> tables) {
        if (tables == null) {
            return null;
        }
        final HashMap<String, List<List<Object>>> out = new HashMap<>();
        for (final Map.Entry<String, List<Row>> table : tables.entrySet()) {
            out.put(table.getKey(), values(table.getValue()));
        }
        return out;
    }

    private static List<Row> rows(final List<List<Object>> values) {
        if (values == null) {
            return null;
        }
        final List<Row> out = new ArrayList<>(values.size());
        for (final List<Object> row : values) {
            out.add(new Row(new ArrayList<>(row)));
        }
        return out;
    }

    private static Map<String, List<Row>> rows(final Map<String, List<List<Object>>> tables) {
        if (tables == null) {
            return null;
        }
        final Map<String, List<Row>> out = new HashMap<>();
        for (final Map.Entry<String, List<List<Object>>> table : tables.entrySet()) {
            out.put(table.getKey(), rows(table.getValue()));
        }
        return out;
    }
}
