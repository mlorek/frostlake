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

import java.util.ArrayList;
import java.util.List;

/**
 * Whether a materialized view can still be read, and why not. A view is marked invalid when its base table's
 * shape is altered under it — a column renamed or dropped, a column's type or nullability set, whether or not
 * the view reads that column — and when the table is dropped or replaced; it stays readable through an added
 * column, a comment, a clustering key, a constraint, a retention time and every write. Once invalid it stays so
 * whatever is done to the table next, until the view is created again: only a table dropped and restored by
 * UNDROP brings its views back, and a RESUME those a column's type or nullability alone invalidated. ALTER
 * MATERIALIZED VIEW … SUSPEND marks one invalid by hand, and RESUME takes that mark away (all live-verified).
 *
 * <p>A read of an invalid view is refused under the view's name, with the reason; SHOW MATERIALIZED VIEWS
 * reports the same reason in {@code invalid_reason}.
 *
 * <p>A view is tied to the table it was created over, not to the table's name. A table swapped in under the name,
 * or created anew there, is "not accessible": the first read is refused so, at position 14, and a RESUME at
 * position 24, and either leaves the view invalid for that reason; swapped back before anything read it, the
 * view reads as before. A RESUME makes a view valid again whose base table changed only a column's type or
 * nullability; one whose table lost or renamed a column is refused by the reason alone (all live-verified).
 */
public final class MaterializedViewValidity {

    /** The reason of a view suspended by hand. */
    public static final String SUSPENDED = "Marked Materialized View as invalid manually.";

    private static final String MARKED = " Marked Materialized View as invalid.";

    private static final String INACCESSIBLE = "Invalid materialized view definition. Source table used in the "
        + "definition is not accessible or been deleted.";

    /** Where a read of a view over another table is refused. */
    public static final int READ_POSITION = 14;

    /** Where a RESUME of a view over another table is refused. */
    public static final int RESUME_POSITION = 24;

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
     * Mark the views over a table that lost or renamed a column, which a RESUME does not make valid again.
     *
     * @param catalog the catalog
     * @param table   the table, as the catalog holds it
     */
    public static void baseTableReshaped(final Catalog catalog, final Table table) {
        final Schema home = StreamSources.holderOf(catalog, table);
        if (home == null) {
            return;
        }
        baseTableAltered(catalog, home.getDatabaseName(), home.getName(), table.getName());
        final String name = table.getName();
        for (final MaterializedView view : viewsOver(catalog, home.getDatabaseName(), home.getName(), name)) {
            view.setBaseReshaped(true);
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

    /**
     * Bind every view not yet bound to the table its name finds now, before a statement moves tables between
     * names, so that each stays with the table it reads.
     *
     * @param catalog the catalog
     */
    public static void bindAll(final Catalog catalog) {
        for (final Database each : catalog.getAllDatabases()) {
            for (final Schema home : each.getAllSchemas()) {
                for (final MaterializedView view : home.getMaterializedViews()) {
                    if (view.getBaseTable() == null && view.getSourceTable() != null) {
                        view.bindBaseTable(tableNamed(catalog, view));
                    }
                }
            }
        }
    }

    /**
     * Refuse a view whose base table's name now finds another table, and mark it invalid for that reason: a read
     * is refused at {@link #READ_POSITION}, a RESUME at {@link #RESUME_POSITION}. A name that finds no table is
     * refused where the view's body compiles, in that refusal's own words.
     *
     * @param catalog  the catalog
     * @param view     the view
     * @param position where the refusal is placed
     * @return the refusal, or null when the name finds the view's own table
     */
    public static String replacedBase(final Catalog catalog, final MaterializedView view, final int position) {
        if (view.getSourceTable() == null) {
            return null;
        }
        final Table current = tableNamed(catalog, view);
        if (current == null) {
            return null;
        }
        final Table bound = view.getBaseTable();
        if (bound == current) {
            return null;
        }
        if (bound == null && !droppedReason(view.getSourceTable()).equals(view.getInvalidReason())) {
            // Brought back by a snapshot or a clone: the table of the name is the view's own.
            view.bindBaseTable(current);
            return null;
        }
        final String refusal = "SQL compilation error: error line 1 at position " + position + "\n" + INACCESSIBLE;
        view.setInvalidReason(refusal);
        view.setResumedWhileInvalid(false);
        return refusal;
    }

    /**
     * What an accepted RESUME does to a view still invalid: one whose base table only changed a column's type or
     * nullability is valid again, and one whose table lost or renamed a column stays invalid, refused by the reason
     * alone.
     *
     * @param view the view
     */
    public static void resumed(final MaterializedView view) {
        if (view.getInvalidReason() == null) {
            return;
        }
        if (view.isBaseReshaped()) {
            view.setResumedWhileInvalid(true);
        } else {
            view.setInvalidReason(null);
            view.setResumedWhileInvalid(false);
        }
    }

    /**
     * The database, schema and name SHOW MATERIALIZED VIEWS lists the view's source table under: where the table it
     * is bound to stands now — a swapped table under its new name — or the names the view recorded.
     *
     * @param catalog the catalog
     * @param view    the view
     * @return the three names
     */
    public static String[] listedSource(final Catalog catalog, final MaterializedView view) {
        final Table bound = view.getBaseTable();
        final Schema home = bound == null ? null : StreamSources.holderOf(catalog, bound);
        if (home == null) {
            return new String[] {view.getSourceDatabase(), view.getSourceSchema(), view.getSourceTable()};
        }
        return new String[] {home.getDatabaseName(), home.getName(), bound.getName()};
    }

    /**
     * The table a view's recorded source names now, or null when it names none.
     *
     * @param catalog the catalog
     * @param view    the view
     * @return the table, or null
     */
    public static Table tableNamed(final Catalog catalog, final MaterializedView view) {
        final Database database = view.getSourceDatabase() == null ? null
            : catalog.getDatabase(view.getSourceDatabase());
        final Schema schema = database == null || view.getSourceSchema() == null ? null
            : database.getSchema(view.getSourceSchema());
        final String table = view.getSourceTable();
        return schema == null || !schema.hasTable(table) ? null : schema.getTable(table);
    }

    private static String droppedReason(final String table) {
        return "Base table '" + table + "' dropped." + MARKED;
    }

    private static List<MaterializedView> viewsOver(final Catalog catalog, final String database,
                                                              final String schema, final String table) {
        final List<MaterializedView> views = new ArrayList<>();
        for (final Database each : catalog.getAllDatabases()) {
            for (final Schema home : each.getAllSchemas()) {
                for (final MaterializedView view : home.getMaterializedViews()) {
                    if (reads(view, database, schema, table)) {
                        views.add(view);
                    }
                }
            }
        }
        return views;
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
