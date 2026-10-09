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
import dev.frostlake.metastore.model.MaterializedView;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;

/**
 * Whether a materialized view can still be read, and why not. A view is marked invalid when its base table's
 * shape is altered under it — a column renamed or dropped, a column's type or nullability set, whether or not
 * the view reads that column — and when the table is dropped or replaced; it stays readable through an added
 * column, a comment, a clustering key, a constraint, a retention time and every write. Once invalid it stays so
 * whatever is done to the table next, until the view is created again: only a table dropped and restored by
 * UNDROP brings its views back. ALTER MATERIALIZED VIEW … SUSPEND marks one invalid by hand, and RESUME takes
 * that mark alone away (all live-verified).
 *
 * <p>A read of an invalid view is refused under the view's name, with the reason; SHOW MATERIALIZED VIEWS
 * reports the same reason in {@code invalid_reason}.
 */
public final class MaterializedViewValidity {

    /** The reason of a view suspended by hand. */
    public static final String SUSPENDED = "Marked Materialized View as invalid manually.";

    private static final String MARKED = " Marked Materialized View as invalid.";

    private MaterializedViewValidity() {
    }

    /**
     * Mark the views over a table whose shape a DDL statement altered.
     *
     * @param catalog  the catalog
     * @param database the table's database
     * @param schema   the table's schema
     * @param table    the table
     */
    public static void baseTableAltered(final Catalog catalog, final String database, final String schema,
                                        final String table) {
        mark(catalog, database, schema, table,
            "DDL Statement was executed on the base table '" + table + "'." + MARKED);
    }

    /**
     * Mark the views over a table whose shape a DDL statement altered.
     *
     * @param catalog the catalog
     * @param table   the table, as the catalog holds it
     */
    public static void baseTableAltered(final Catalog catalog, final Table table) {
        final Schema home = StreamSources.holderOf(catalog, table);
        if (home != null) {
            baseTableAltered(catalog, home.getDatabaseName(), home.getName(), table.getName());
        }
    }

    /**
     * Mark the views over a table that was dropped, or replaced by a new table of its name.
     *
     * @param catalog  the catalog
     * @param database the table's database
     * @param schema   the table's schema
     * @param table    the table
     */
    public static void baseTableDropped(final Catalog catalog, final String database, final String schema,
                                        final String table) {
        mark(catalog, database, schema, table, droppedReason(table));
    }

    /**
     * Bring back the views a table's drop marked, now that UNDROP restored the table itself.
     *
     * @param catalog  the catalog
     * @param database the table's database
     * @param schema   the table's schema
     * @param table    the table
     */
    public static void baseTableRestored(final Catalog catalog, final String database, final String schema,
                                         final String table) {
        final String dropped = droppedReason(table);
        for (final Database each : catalog.getAllDatabases()) {
            for (final Schema home : each.getAllSchemas()) {
                for (final MaterializedView view : home.getMaterializedViews()) {
                    if (reads(view, database, schema, table) && dropped.equals(view.getInvalidReason())) {
                        view.setInvalidReason(null);
                        view.setResumedWhileInvalid(false);
                    }
                }
            }
        }
    }

    /**
     * Why a view cannot be read, as SHOW MATERIALIZED VIEWS lists it.
     *
     * @param view the view
     * @return the reason, or null while the view is valid
     */
    public static String reason(final MaterializedView view) {
        return view.isSuspended() ? SUSPENDED : view.getInvalidReason();
    }

    /**
     * What a read of the view is refused with, after the view's name.
     *
     * @param view the view
     * @return the refusal's detail, or null while the view is valid
     */
    public static String refusal(final MaterializedView view) {
        final String reason = reason(view);
        if (reason == null) {
            return null;
        }
        if (!view.isSuspended() && view.isResumedWhileInvalid()) {
            // A view resumed while its base table's DDL still stands is refused by the reason alone.
            return reason;
        }
        return "SQL compilation error: Materialized View " + view.getName() + " is invalid. Invalidation reason: "
            + reason;
    }

    private static String droppedReason(final String table) {
        return "Base table '" + table + "' dropped." + MARKED;
    }

    private static void mark(final Catalog catalog, final String database, final String schema, final String table,
                             final String reason) {
        for (final Database each : catalog.getAllDatabases()) {
            for (final Schema home : each.getAllSchemas()) {
                for (final MaterializedView view : home.getMaterializedViews()) {
                    if (reads(view, database, schema, table) && view.getInvalidReason() == null) {
                        view.setInvalidReason(reason);
                    }
                }
            }
        }
    }

    private static boolean reads(final MaterializedView view, final String database, final String schema,
                                 final String table) {
        return table.equals(view.getSourceTable()) && schema.equals(view.getSourceSchema())
            && database.equals(view.getSourceDatabase());
    }
}
