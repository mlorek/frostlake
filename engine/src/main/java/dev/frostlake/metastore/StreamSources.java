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
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.StreamSourceType;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.View;

import java.util.ArrayList;
import java.util.List;

/**
 * The table a table stream reads, bound to the table itself rather than to its name.
 *
 * <p>A stream records its source's name READ FROM WHERE THE STREAM LIVES: bare for a table in the stream's own
 * schema, schema-qualified for one elsewhere in its database, in full for one in another database. That name
 * resolves the same whatever the session's context, which the stream's reads and its change capture both
 * rely on, and a clone of the stream's database or schema reads it to the clone's own copy of the table.
 *
 * <p>Once bound, a stream follows its table through renames, moves and swaps, and the recorded name is
 * re-spelled after each so that a snapshot or a clone finds the same table again. A table dropped, or replaced
 * by CREATE OR REPLACE, leaves its streams unbound to anything the catalog holds. Such a stream captures
 * nothing and refuses to be read, even when a new table takes the old name, as Snowflake documents.
 *
 * <p>A stream on a view is bound the same way to every table its view's query reads, each name read where the VIEW
 * lives, as the query itself reads it. Dropping or recreating any of them "makes any stream on the view stale"
 * (Snowflake's documentation), and a same-named table elsewhere feeds it nothing.
 */
public final class StreamSources {

    /** What a table stream lost in a clone names as its table, in GET_DDL and in a refusal (live-verified). */
    public static final String LOST_SOURCE = "CLONED_FROM_STREAM_WITH_DROPPED_SOURCE";

    private StreamSources() {
    }

    /**
     * The sentence a read of a table stream lost in a clone is refused with (live-verified).
     *
     * @param stream the stream
     * @return the sentence
     */
    public static String lostInCloneRefusal(final Stream stream) {
        return "Table for stream '" + stream.getName()
            + "' invalid due to loss of table before or during cloning of parent entity";
    }

    /**
     * Give a clone's streams their sources' state where a source is gone, as a clone keeps what each stream is bound
     * to (live-verified). A table stream whose table went before the clone is lost in the clone, whatever table now
     * holds the name, and so is one a clone made lost. A view stream any of whose view's tables went keeps the
     * tables it read, so it is as stale in the clone as in place. A stream whose source is there reads, in the
     * clone, what its recorded name reads there.
     *
     * @param catalog the catalog
     * @param source  the schema cloned
     * @param clone   its clone
     */
    public static void carryClonedStates(final Catalog catalog, final Schema source, final Schema clone) {
        for (final Stream stream : source.getStreams()) {
            if (!clone.hasStreamExact(stream.getName())) {
                continue;
            }
            final Stream copy = clone.getStream(stream.getName());
            if (stream.getSourceType() == StreamSourceType.TABLE) {
                if (stream.isLostInClone() || sourceOf(catalog, source, stream) == null) {
                    copy.markLostInClone();
                }
            } else if (stream.getSourceType() == StreamSourceType.VIEW && missingBaseOf(catalog, stream) != null) {
                copy.bindBaseTables(stream.getBaseTables());
            }
        }
    }

    /**
     * The table a table stream reads, or null when that table is gone or the stream reads no table. A stream not
     * yet bound binds now, by its recorded name.
     *
     * @param catalog the catalog
     * @param home    the schema the stream lives in
     * @param stream  the stream
     * @return the table, or null
     */
    public static Table sourceOf(final Catalog catalog, final Schema home, final Stream stream) {
        final Table bound = bound(catalog, home, stream);
        return bound != null && holderOf(catalog, bound) != null ? bound : null;
    }

    /**
     * Whether a change to {@code changed} feeds this table stream: whether it is the very table the stream is bound
     * to. No catalog walk is needed; a table no schema holds any more takes no DML.
     *
     * @param catalog the catalog
     * @param home    the schema the stream lives in
     * @param stream  the stream
     * @param changed the table the DML changed
     * @return whether the stream captures the change
     */
    public static boolean captures(final Catalog catalog, final Schema home, final Stream stream, final Table changed) {
        final Table bound = bound(catalog, home, stream);
        return bound != null && bound == changed;
    }

    /**
     * The tables a view stream reads, bound to the tables themselves, binding them by their recorded names first when
     * they are not bound yet.
     *
     * @param catalog the catalog
     * @param stream  the stream
     * @return the tables, in the order the view's query names them; null when the stream reads no view, or when any
     *         of them is gone, which leaves the stream stale
     */
    public static List<Table> baseTablesOf(final Catalog catalog, final Stream stream) {
        if (stream.getSourceType() != StreamSourceType.VIEW || stream.getBaseTableNames().isEmpty()) {
            return null;
        }
        if (stream.getBaseTables() == null && !stream.isStale()) {
            final List<Table> named = viewBases(catalog, viewHomeOf(catalog, homeOf(catalog, stream), stream),
                stream.getBaseTableNames());
            if (named == null) {
                return null;
            }
            stream.bindBaseTables(named);
        }
        final List<Table> bound = stream.getBaseTables();
        if (bound == null) {
            return null;
        }
        for (final Table table : bound) {
            if (holderOf(catalog, table) == null) {
                return null;
            }
        }
        return bound;
    }

    /**
     * Whether a change to {@code changed} feeds this view stream: whether it is one of the very tables the view reads.
     *
     * @param catalog the catalog
     * @param stream  the stream
     * @param changed the table the DML changed
     * @return whether the stream captures the change
     */
    public static boolean capturesThroughView(final Catalog catalog, final Stream stream, final Table changed) {
        final List<Table> bound = baseTablesOf(catalog, stream);
        if (bound == null || changed == null) {
            return false;
        }
        for (final Table table : bound) {
            if (table == changed) {
                return true;
            }
        }
        return false;
    }

    /**
     * The tables a view's query names, each read where the view lives.
     *
     * @param catalog  the catalog
     * @param viewHome the schema the view lives in
     * @param names    the names its query writes
     * @return the tables, or null when any name reads no table
     */
    public static List<Table> viewBases(final Catalog catalog, final Schema viewHome, final List<String> names) {
        if (viewHome == null) {
            return null;
        }
        final List<Table> tables = new ArrayList<>();
        for (final String name : names) {
            final Table table = named(catalog, viewHome, name);
            if (table == null) {
                return null;
            }
            tables.add(table);
        }
        return tables;
    }

    /**
     * Re-binds a view stream whose view's tables were dropped or replaced to the tables its view's names read now, as
     * the account does the first time such a stream is read or SYSTEM$STREAM_HAS_DATA asks of it (live-verified). A
     * stream holding no change it has not read reads those tables from then on; one that holds some keeps reading the
     * tables it had, and only lists the new ones (see Stream#showOn). A table that tracked no changes takes change
     * tracking on with the re-bind.
     *
     * @param catalog the catalog
     * @param stream  the stream
     * @return the full name of the first table whose change tracking the re-bind turned on, which the read that
     *         re-bound the stream is refused over; null where every table tracked its changes already, and where the
     *         stream was not re-bound - it is not stale, or a name its view reads holds no table
     */
    public static String rebind(final Catalog catalog, final Stream stream) {
        if (stream.getSourceType() != StreamSourceType.VIEW || stream.getBaseTableNames().isEmpty()
                || baseTablesOf(catalog, stream) != null) {
            return null;
        }
        final Schema viewHome = viewHomeOf(catalog, homeOf(catalog, stream), stream);
        final List<Table> now = viewBases(catalog, viewHome, stream.getBaseTableNames());
        if (now == null) {
            return null;
        }
        String untracked = null;
        for (int i = 0; i < now.size(); i++) {
            if (!now.get(i).isChangeTracking()) {
                now.get(i).setChangeTracking(true);
                if (untracked == null) {
                    untracked = completed(viewHome, stream.getBaseTableNames().get(i));
                }
            }
        }
        if (stream.getUnconsumedCount() > 0) {
            stream.showOn(now);
        } else {
            stream.restartOn(now);
        }
        return untracked;
    }

    /**
     * The full names of the tables a re-bind that kept a view stream's changes lists it as reading, each under the name
     * it has now; null when there was no such re-bind, or when one of them is gone.
     *
     * @param catalog the catalog
     * @param stream  the stream
     * @return the names, or null
     */
    public static List<String> shownBaseTableNames(final Catalog catalog, final Stream stream) {
        if (stream.getShownTables() == null) {
            return null;
        }
        final List<String> names = new ArrayList<>();
        for (final Table table : stream.getShownTables()) {
            final Schema holder = holderOf(catalog, table);
            if (holder == null) {
                return null;
            }
            names.add(holder.qualifiedName(table.getName()));
        }
        return names;
    }

    /**
     * The first table a re-bound view stream reads whose change tracking began after the point the stream reads from,
     * while no change to it has come since: the range the stream reads holds a time the table tracked nothing, so the
     * read is refused, as it is on the account until the table changes (live-verified).
     *
     * @param catalog the catalog
     * @param stream  the stream
     * @return DB.SCHEMA.NAME of such a table, or null when the stream reads none
     */
    public static String trackingGapOf(final Catalog catalog, final Stream stream) {
        final List<Table> bound = stream.isRebound() ? baseTablesOf(catalog, stream) : null;
        if (bound == null) {
            return null;
        }
        final Schema viewHome = viewHomeOf(catalog, homeOf(catalog, stream), stream);
        for (int i = 0; i < bound.size() && i < stream.getBaseTableNames().size(); i++) {
            final long since = bound.get(i).getChangeTrackingSince();
            final String name = completed(viewHome, stream.getBaseTableNames().get(i));
            if (since != Long.MAX_VALUE && since > stream.getOffsetSequence()
                    && !stream.holdsVersionAfter(name, since)) {
                return name;
            }
        }
        return null;
    }

    /**
     * The first table a view stream reads that a temporary table of its name hides from the session, while the
     * temporary table tracks no changes: the view reads the temporary table in that session, and the stream's range
     * finds no change tracking on it (live-verified).
     *
     * @param catalog the catalog
     * @param stream  the stream
     * @return DB.SCHEMA.NAME of such a table, or null when the view reads none
     */
    public static String untrackedShadowOf(final Catalog catalog, final Stream stream) {
        final List<Table> bound = baseTablesOf(catalog, stream);
        if (bound == null) {
            return null;
        }
        final Schema viewHome = viewHomeOf(catalog, homeOf(catalog, stream), stream);
        for (int i = 0; i < bound.size() && i < stream.getBaseTableNames().size(); i++) {
            final String recorded = stream.getBaseTableNames().get(i);
            final Schema schema = schemaNamed(catalog, viewHome, recorded);
            final String name = QualifiedName.parse(recorded).last();
            final Table seen = schema != null && schema.hasTable(name) ? schema.getTable(name) : null;
            final boolean hidden = seen != null && seen != bound.get(i) && seen.isTemporary()
                && (schema.heldTable(name) == bound.get(i) || schema.shadowedTable(name) == bound.get(i));
            if (hidden && !seen.isChangeTracking()) {
                return completed(viewHome, recorded);
            }
        }
        return null;
    }

    /**
     * The first of a view stream's tables that is gone, in full, for a refusal that names it.
     *
     * @param catalog the catalog
     * @param stream  the stream
     * @return DB.SCHEMA.NAME of a table the stream can no longer read, or null when every one is there
     */
    public static String missingBaseOf(final Catalog catalog, final Stream stream) {
        if (stream.getSourceType() != StreamSourceType.VIEW || baseTablesOf(catalog, stream) != null) {
            return null;
        }
        final Schema viewHome = viewHomeOf(catalog, homeOf(catalog, stream), stream);
        final List<Table> bound = stream.getBaseTables();
        for (int i = 0; i < stream.getBaseTableNames().size(); i++) {
            final String name = stream.getBaseTableNames().get(i);
            final boolean gone = bound != null && i < bound.size() ? holderOf(catalog, bound.get(i)) == null
                : named(catalog, viewHome, name) == null;
            if (gone) {
                return completed(viewHome, name);
            }
        }
        return completed(viewHome, stream.getBaseTableNames().get(0));
    }

    /**
     * The schema a view stream's view lives in: its recorded source read from where the stream lives.
     *
     * @param catalog the catalog
     * @param home    the schema the stream lives in
     * @param stream  the stream
     * @return the schema, or null when it is gone
     */
    /**
     * The full names of the tables a view stream's view reads, each read where the view lives, in the order the
     * stream recorded them; null for any other stream.
     *
     * @param catalog the catalog
     * @param stream  the stream
     * @return the names, or null
     */
    public static List<String> baseTableFullNames(final Catalog catalog, final Stream stream) {
        if (stream.getSourceType() != StreamSourceType.VIEW || stream.getBaseTableNames().isEmpty()) {
            return null;
        }
        final Schema viewHome = viewHomeOf(catalog, homeOf(catalog, stream), stream);
        final List<String> names = new ArrayList<>();
        for (final String name : stream.getBaseTableNames()) {
            names.add(completed(viewHome, name));
        }
        return names;
    }

    /**
     * How many of the tables a view stream's view reads are gone, dropped or replaced under their names: none for a
     * stream whose tables are all there, and at least one for a stale one.
     *
     * @param catalog the catalog
     * @param stream  the stream
     * @return the count
     */
    public static int missingBaseCount(final Catalog catalog, final Stream stream) {
        if (stream.getSourceType() != StreamSourceType.VIEW || baseTablesOf(catalog, stream) != null) {
            return 0;
        }
        final Schema viewHome = viewHomeOf(catalog, homeOf(catalog, stream), stream);
        final List<Table> bound = stream.getBaseTables();
        int missing = 0;
        for (int i = 0; i < stream.getBaseTableNames().size(); i++) {
            final boolean gone = bound != null && i < bound.size() ? holderOf(catalog, bound.get(i)) == null
                : named(catalog, viewHome, stream.getBaseTableNames().get(i)) == null;
            if (gone) {
                missing++;
            }
        }
        return Math.max(missing, 1);
    }

    public static Schema viewHomeOf(final Catalog catalog, final Schema home, final Stream stream) {
        return schemaNamed(catalog, home, stream.getSourceTableName());
    }

    /**
     * The schema a stream lives in, matched by identity.
     *
     * @param catalog the catalog
     * @param stream  the stream
     * @return the schema, or null when no schema holds the stream
     */
    public static Schema homeOf(final Catalog catalog, final Stream stream) {
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema schema : database.getAllSchemas()) {
                for (final Stream held : schema.getStreams()) {
                    if (held == stream) {
                        return schema;
                    }
                }
            }
        }
        return null;
    }

    /**
     * A stream's recorded source spelled in full, for a refusal that names it.
     *
     * @param home   the schema the stream lives in, or null when unknown
     * @param stream the stream
     * @return DB.SCHEMA.NAME, or the recorded name when it cannot be completed
     */
    public static String fullName(final Schema home, final Stream stream) {
        final String recorded = stream.getSourceTableName();
        if (recorded == null || home == null || home.getDatabaseName() == null) {
            return recorded;
        }
        final String[] parts = QualifiedName.parse(recorded).parts();
        if (parts.length == 1) {
            return home.qualifiedName(parts[0]);
        }
        return parts.length == 2 ? QualifiedName.join(home.getDatabaseName(), parts[0], parts[1]) : recorded;
    }

    /**
     * The name a stream living in {@code home} records for a table held by {@code holder}.
     *
     * @param home      the stream's schema
     * @param holder    the table's schema
     * @param tableName the table's name
     * @return the name, as short as reads the same table from the stream's schema
     */
    public static String spelled(final Schema home, final Schema holder, final String tableName) {
        if (holder == home) {
            return QualifiedName.join(tableName);
        }
        if (holder.getDatabaseName() != null && holder.getDatabaseName().equals(home.getDatabaseName())) {
            return QualifiedName.join(holder.getName(), tableName);
        }
        return holder.qualifiedName(tableName);
    }

    /**
     * Binds every table stream not bound yet, while the names they recorded still name their tables: the first
     * step of a rename or a move.
     *
     * @param catalog the catalog
     */
    public static void bindAll(final Catalog catalog) {
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema schema : database.getAllSchemas()) {
                for (final Stream stream : schema.getStreams()) {
                    bound(catalog, schema, stream);
                    boundView(catalog, schema, stream);
                    baseTablesOf(catalog, stream);
                }
            }
        }
    }

    /**
     * Re-spells every bound stream's recorded source as its table or its view is named now: the last step of a
     * rename or a move. A view stream follows its view, so a new view under the old name is not its source
     * (live-verified). A stream whose source is gone keeps the name it had.
     *
     * @param catalog the catalog
     */
    public static void respellAll(final Catalog catalog) {
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema schema : database.getAllSchemas()) {
                for (final Stream stream : schema.getStreams()) {
                    final Table bound = stream.getSourceTable();
                    final Schema holder = bound == null ? null : holderOf(catalog, bound);
                    if (holder != null) {
                        stream.setSourceTableName(spelled(schema, holder, bound.getName()));
                    }
                    final View view = stream.getSourceView();
                    final Schema viewHolder = view == null ? null : viewHolderOf(catalog, view);
                    if (viewHolder != null) {
                        stream.setSourceTableName(spelled(schema, viewHolder, view.getName()));
                    }
                }
            }
        }
    }

    /** The view a view stream is bound to, binding it by its recorded name first when it is not yet bound. */
    private static View boundView(final Catalog catalog, final Schema home, final Stream stream) {
        if (stream.getSourceType() != StreamSourceType.VIEW) {
            return null;
        }
        if (stream.getSourceView() == null && !stream.isStale() && stream.getSourceTableName() != null) {
            final Schema schema = schemaNamed(catalog, home, stream.getSourceTableName());
            final String name = QualifiedName.parse(stream.getSourceTableName()).last();
            if (schema != null && schema.hasView(name)) {
                stream.bindSourceView(schema.getView(name));
            }
        }
        return stream.getSourceView();
    }

    /** The schema holding a view, by the instance, or null when it is in none. */
    private static Schema viewHolderOf(final Catalog catalog, final View view) {
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema schema : database.getAllSchemas()) {
                for (final View held : schema.getViews()) {
                    if (held == view) {
                        return schema;
                    }
                }
            }
        }
        return null;
    }

    /**
     * The table a DB.SCHEMA.NAME names in the catalog.
     *
     * @param catalog  the catalog
     * @param fullName the table's name in full
     * @return the table, or null when there is none of that name
     */
    public static Table tableNamed(final Catalog catalog, final String fullName) {
        final String[] parts = QualifiedName.parse(fullName).parts();
        if (parts.length != 3) {
            return null;
        }
        final Database database = catalog.findDatabase(parts[0]);
        final Schema schema = database != null && database.hasSchema(parts[1]) ? database.getSchema(parts[1]) : null;
        return schema != null && schema.hasTable(parts[2]) ? schema.getTable(parts[2]) : null;
    }

    /** The table a table stream is bound to, binding it by its recorded name first when it is not yet bound. */
    private static Table bound(final Catalog catalog, final Schema home, final Stream stream) {
        if (stream.getSourceType() != StreamSourceType.TABLE) {
            return null;
        }
        if (stream.getSourceTable() == null && !stream.isStale()) {
            final Table named = named(catalog, home, stream.getSourceTableName());
            if (named != null) {
                stream.bindSourceTable(named);
            }
        }
        return stream.getSourceTable();
    }

    /**
     * The table a recorded name reads from {@code home} - a stream's schema, or the schema of a table whose foreign
     * key records it - or null when it reads none.
     */
    static Table named(final Catalog catalog, final Schema home, final String recorded) {
        final Schema schema = schemaNamed(catalog, home, recorded);
        if (schema == null) {
            return null;
        }
        final String name = QualifiedName.parse(recorded).last();
        return schema.hasTable(name) ? schema.getTable(name) : null;
    }

    /**
     * The view a name a view's query writes reads, read where the view lives.
     *
     * @param catalog the catalog
     * @param home    the schema the view lives in
     * @param written the name as the query writes it
     * @return the view, or null when the name reads none
     */
    public static View viewNamed(final Catalog catalog, final Schema home, final String written) {
        final Schema schema = schemaNamed(catalog, home, written);
        if (schema == null) {
            return null;
        }
        final String name = QualifiedName.parse(written).last();
        return schema.hasView(name) ? schema.getView(name) : null;
    }

    /**
     * The schema a recorded name's object lives in, read from {@code home}.
     *
     * @param catalog  the catalog
     * @param home     the schema the name is read from
     * @param recorded the name
     * @return the schema, or null when it is gone
     */
    public static Schema schemaNamed(final Catalog catalog, final Schema home, final String recorded) {
        if (recorded == null || home == null) {
            return null;
        }
        final String[] parts = QualifiedName.parse(recorded).parts();
        if (parts.length == 1) {
            return home;
        }
        final Database database = catalog.findDatabase(parts.length >= 3 ? parts[parts.length - 3]
            : home.getDatabaseName());
        return database != null && database.hasSchema(parts[parts.length - 2])
            ? database.getSchema(parts[parts.length - 2]) : null;
    }

    /**
     * A recorded name in full, read from {@code home}.
     *
     * @param home     the schema the name is read from
     * @param recorded the name
     * @return the name with its database and schema
     */
    public static String completed(final Schema home, final String recorded) {
        if (recorded == null || home == null || home.getDatabaseName() == null) {
            return recorded;
        }
        final String[] parts = QualifiedName.parse(recorded).parts();
        if (parts.length == 1) {
            return home.qualifiedName(parts[0]);
        }
        return parts.length == 2 ? QualifiedName.join(home.getDatabaseName(), parts[0], parts[1]) : recorded;
    }

    /**
     * The schema holding a table, matched by identity, a permanent table a temporary one hides included: the
     * temporary table a session holds under the name hides the permanent table from that session's own lookups, and
     * neither drops it nor takes its place.
     *
     * @param catalog the catalog
     * @param table   the table
     * @return the schema, or null when no schema holds the table any more
     */
    public static Schema holderOf(final Catalog catalog, final Table table) {
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema schema : database.getAllSchemas()) {
                if ((schema.hasTable(table.getName()) && schema.getTable(table.getName()) == table)
                        || schema.heldTable(table.getName()) == table
                        || schema.shadowedTable(table.getName()) == table) {
                    return schema;
                }
            }
        }
        return null;
    }
}
