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
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.View;
import dev.frostlake.storage.StorageEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

/**
 * The end of what was created TEMPORARY: tables, and equally the views, functions, procedures and stages the
 * temporary spellings create. A temporary object's lifetime is the session that created it (live-verified for
 * all five kinds), so a session that ends - a released or expired one of the HTTP server, a closed connection, a task's
 * run, the replay of a log - takes its temporary objects with it, and the engine's own session takes the rest
 * when the engine shuts down. Another session never sees a temporary object on an account; here one namespace
 * serves every session, so until its session ends the object is there for all of them.
 */
public final class TemporaryObjects {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemporaryObjects.class);

    private TemporaryObjects() {
    }

    /**
     * Drop the temporary tables, views, functions, procedures and stages of one session, or of every session.
     *
     * @param catalog       the catalog that holds them
     * @param storageEngine the storage their rows live in
     * @param session       the number of the session that ended, or null for all of them
     * @return how many objects were dropped
     */
    public static int drop(final Catalog catalog, final StorageEngine storageEngine, final Long session) {
        int dropped = 0;
        for (final Database db : catalog.getAllDatabases()) {
            for (final Schema schema : db.getAllSchemas()) {
                for (final Table table : new ArrayList<>(schema.getTables())) {
                    if (table.isTemporary() && endsWith(session, table)) {
                        final String fqn = db.getName() + "." + schema.getName() + "." + table.getName();
                        schema.dropTable(table.getName());
                        if (storageEngine.hasTable(fqn)) {
                            storageEngine.dropTable(fqn);
                        }
                        dropped++;
                        LOGGER.debug("Dropped temporary table: {}", fqn);
                    }
                }
                // Each permanent table a temporary one hid is uncovered.
                for (final Table hidden : schema.getShadowedTables()) {
                    TableShadows.settle(schema, storageEngine, db.getName(), hidden.getName());
                }
                for (final View view : new ArrayList<>(schema.getViews())) {
                    if (view.isTemporary() && endsWith(session, view)) {
                        schema.dropView(view.getName());
                        dropped++;
                        LOGGER.debug("Dropped temporary view: {}", view.getName());
                    }
                }
                for (final Function function : new ArrayList<>(schema.getFunctions())) {
                    if (function.isTemporary() && endsWith(session, function)) {
                        schema.removeFunction(function);
                        dropped++;
                        LOGGER.debug("Dropped temporary function: {}", function.getName());
                    }
                }
                for (final Procedure procedure : new ArrayList<>(schema.getProcedures())) {
                    if (procedure.isTemporary() && endsWith(session, procedure)) {
                        schema.removeProcedure(procedure);
                        dropped++;
                        LOGGER.debug("Dropped temporary procedure: {}", procedure.getName());
                    }
                }
                for (final Stage stage : new ArrayList<>(schema.getStages())) {
                    if (stage.isTemporary() && endsWith(session, stage)) {
                        schema.dropStage(stage.getName());
                        dropped++;
                        LOGGER.debug("Dropped temporary stage: {}", stage.getName());
                    }
                }
            }
        }
        return dropped;
    }

    /** Whether an object goes with the session that ended: any object when every session did. */
    private static boolean endsWith(final Long session, final SqlObject object) {
        return session == null || session.equals(object.getCreatedInSession());
    }
}
