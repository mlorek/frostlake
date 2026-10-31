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

package dev.frostlake.metastore;

import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.View;
import dev.frostlake.storage.StorageEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;


/**
 * The end of what was created TEMPORARY: tables, and equally the views, functions, procedures, stages and file
 * formats the temporary spellings create. A temporary object's lifetime is the session that created it
 * (live-verified for every kind), so a session that ends - a released or expired one of the HTTP server, a closed
 * connection, a task's run, the replay of a log - takes its temporary objects with it, and the engine's own session
 * takes the rest when the engine shuts down. Another session neither reaches nor lists a temporary object, and may
 * create a temporary table, view or stage of the same name, its own (live-verified).
 */
public final class TemporaryObjects {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemporaryObjects.class);

    private TemporaryObjects() {
    }

    /**
     * Drop the temporary tables, views, functions, procedures, stages and file formats of one session, or of every
     * session.
     *
     * @param catalog       the catalog that holds them
     * @param storageEngine the storage their rows live in
     * @param session       the number of the session that ended, or null for all of them
     * @return how many objects were dropped
     */
    public static int drop(final Catalog catalog, final StorageEngine storageEngine, final Long session) {
        return drop(catalog, storageEngine, session, Collections.emptySet());
    }

    /**
     * The temporary objects a catalog holds: what an engine's own sessions created, before a copy of another engine's
     * state comes in (see {@link #dropAllBut}).
     *
     * @param catalog the catalog
     * @return the objects, by identity
     */
    public static Set<Object> held(final Catalog catalog) {
        final Set<Object> held = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        for (final Database db : catalog.getAllDatabases()) {
            for (final Schema schema : db.getAllSchemas()) {
                for (final Table table : schema.allTables()) {
                    if (table.isTemporary()) {
                        held.add(table);
                    }
                }
                for (final View view : schema.allViews()) {
                    if (view.isTemporary()) {
                        held.add(view);
                    }
                }
                for (final Function function : schema.allFunctions()) {
                    if (function.isTemporary()) {
                        held.add(function);
                    }
                }
                for (final Procedure procedure : schema.allProcedures()) {
                    if (procedure.isTemporary()) {
                        held.add(procedure);
                    }
                }
                for (final FileFormat format : schema.allFileFormats()) {
                    if (format.isTemporary()) {
                        held.add(format);
                    }
                }
                for (final Stage stage : schema.allStages()) {
                    if (stage.isTemporary()) {
                        held.add(stage);
                    }
                }
            }
        }
        return held;
    }

    /**
     * Drop every temporary object but those kept: what a copy of another engine's state brought in belonged to
     * sessions of that engine, none of which this engine has, so it ends here as it does when an engine restarts on
     * its data, while what this engine's own sessions hold stays theirs.
     *
     * @param catalog       the catalog that holds them
     * @param storageEngine the storage their rows live in
     * @param kept          the objects to keep (see {@link #held})
     * @return how many objects were dropped
     */
    public static int dropAllBut(final Catalog catalog, final StorageEngine storageEngine, final Set<Object> kept) {
        return drop(catalog, storageEngine, null, kept);
    }

    private static int drop(final Catalog catalog, final StorageEngine storageEngine, final Long session,
                            final Set<Object> kept) {
        int dropped = 0;
        for (final Database db : catalog.getAllDatabases()) {
            for (final Schema schema : db.getAllSchemas()) {
                for (final Table table : schema.allTables()) {
                    if (table.isTemporary() && endsWith(session, table) && !kept.contains(table)) {
                        final String fqn = db.getName() + "." + schema.getName() + "." + table.getName();
                        if (table.isSessionTemporary()) {
                            // A session's own table is dropped from its session, whichever session ends it.
                            schema.removeSessionTable(table);
                            storageEngine.dropSessionTable(QualifiedName.key(db.getName(), schema.getName(),
                                table.getName()), table.getCreatedInSession());
                        } else {
                            schema.dropTable(table.getName());
                            if (storageEngine.hasTable(fqn)) {
                                storageEngine.dropTable(fqn);
                            }
                        }
                        dropped++;
                        LOGGER.debug("Dropped temporary table: {}", fqn);
                    }
                }
                // Each permanent table a temporary one hid is uncovered.
                for (final Table hidden : schema.getShadowedTables()) {
                    TableShadows.settle(schema, storageEngine, db.getName(), hidden.getName());
                }
                for (final View view : schema.allViews()) {
                    if (view.isTemporary() && endsWith(session, view) && !kept.contains(view)) {
                        if (!schema.removeSessionView(view)) {
                            schema.dropView(view.getName());
                        }
                        dropped++;
                        LOGGER.debug("Dropped temporary view: {}", view.getName());
                    }
                }
                for (final Function function : schema.allFunctions()) {
                    if (function.isTemporary() && endsWith(session, function) && !kept.contains(function)) {
                        schema.removeFunction(function);
                        dropped++;
                        LOGGER.debug("Dropped temporary function: {}", function.getName());
                    }
                }
                for (final Procedure procedure : schema.allProcedures()) {
                    if (procedure.isTemporary() && endsWith(session, procedure) && !kept.contains(procedure)) {
                        schema.removeProcedure(procedure);
                        dropped++;
                        LOGGER.debug("Dropped temporary procedure: {}", procedure.getName());
                    }
                }
                for (final FileFormat format : schema.allFileFormats()) {
                    if (format.isTemporary() && (session == null || session.equals(format.getCreatedInSession()))
                            && !kept.contains(format)) {
                        schema.dropFileFormat(format.getName());
                        dropped++;
                        LOGGER.debug("Dropped temporary file format: {}", format.getName());
                    }
                }
                for (final Stage stage : schema.allStages()) {
                    if (stage.isTemporary() && endsWith(session, stage) && !kept.contains(stage)) {
                        schema.removeStage(stage);
                        dropped++;
                        LOGGER.debug("Dropped temporary stage: {}", stage.getName());
                    }
                }
            }
        }
        return dropped;
    }

    /**
     * Release every session's own temporary tables of a schema that is dropped or replaced: they go with it, from
     * whichever session holds them, and do not come back with an UNDROP. What is left are the tables every session
     * shares.
     *
     * @param schema        the schema
     * @param storageEngine the storage their rows live in
     * @param database      the schema's database
     */
    public static void releaseSessionTables(final Schema schema, final StorageEngine storageEngine,
                                            final String database) {
        for (final Table table : schema.allTables()) {
            if (table.isSessionTemporary()) {
                schema.removeSessionTable(table);
                storageEngine.dropSessionTable(QualifiedName.key(database, schema.getName(), table.getName()),
                    table.getCreatedInSession());
            }
        }
    }

    /** Whether an object goes with the session that ended: any object when every session did. */
    private static boolean endsWith(final Long session, final SqlObject object) {
        return session == null || session.equals(object.getCreatedInSession());
    }
}
