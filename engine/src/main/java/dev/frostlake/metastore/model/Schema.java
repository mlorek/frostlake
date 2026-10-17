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

package dev.frostlake.metastore.model;

import dev.frostlake.executor.SqlAccessControlError;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.StatementClock;
import dev.frostlake.metastore.NameKeys;
import dev.frostlake.metastore.NameTakenException;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.SqlObject;
import dev.frostlake.metastore.StatementSession;
import dev.frostlake.metastore.Taggable;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.MapType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.StructuredField;
import dev.frostlake.types.StructuredObjectType;
import dev.frostlake.types.StructuredTypes;
import dev.frostlake.types.WrittenSignatureType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class Schema extends SqlObject {

    /**
     * The DATA_RETENTION_TIME_IN_DAYS this object declares, or null when it declares none and
     * inherits its container's (the account default is 1 at the top of the chain).
     */
    private Integer dataRetentionTimeInDays;

    public Integer getDataRetentionTimeInDays() {
        return dataRetentionTimeInDays;
    }

    public void setDataRetentionTimeInDays(final Integer dataRetentionTimeInDays) {
        this.dataRetentionTimeInDays = dataRetentionTimeInDays;
    }


    private final Map<String, Table> tables;
    /**
     * The PERMANENT tables a TEMPORARY table of the same name hides, by that name. A name is here only
     * while {@link #tables} holds a temporary table under it (see {@link #swapShadow}).
     */
    private final Map<String, Table> shadowedTables;
    /**
     * Each session's own temporary tables, by session and then by name (see Table.isSessionTemporary). A session's
     * temporary table is its own: two sessions may each hold one of a name, and one above a permanent table of its
     * name leaves every other session reading the permanent one (live-verified). The current session's own tables
     * answer to a name before the tables every session shares.
     */
    private final Map<Long, Map<String, Table>> sessionTables = new ConcurrentHashMap<>();
    /** A session's own temporary table, set aside while the session creates a permanent table of its name. */
    private final Map<Long, Map<String, Table>> sessionTablesAside = new ConcurrentHashMap<>();
    /** Each session's own temporary views, by session and then by name, as {@link #sessionTables} holds tables. */
    private final Map<Long, Map<String, View>> sessionViews = new ConcurrentHashMap<>();
    private final Map<String, View> views;
    private final Map<String, MaterializedView> materializedViews;
    private final Map<String, DynamicTable> dynamicTables;
    private final Map<String, List<Procedure>> procedures;
    private final Map<String, List<Function>> functions;
    private final Map<String, Stream> streams;
    private final Map<String, Task> tasks;
    private final Map<String, Alert> alerts = new ConcurrentHashMap<>();
    private final Map<String, Pipe> pipes;
    private final Map<String, Sequence> sequences;
    private final Map<String, Contact> contacts;
    private final Map<String, MaskingPolicy> maskingPolicies;
    private final Map<String, ProjectionPolicy> projectionPolicies;
    private final Map<String, AggregationPolicy> aggregationPolicies;
    private final Map<String, JoinPolicy> joinPolicies;
    private final Map<String, RowAccessPolicy> rowAccessPolicies;
    private final Map<String, Tag> tags;
    private final Map<String, Stage> stages;
    private final Map<String, CortexSearchService> cortexSearchServices;
    private final AppObjectStore appObjects = new AppObjectStore();
    private final Map<String, FileFormat> fileFormats;
    private final ContainerObjects containerObjects = new ContainerObjects();

    /**
     * The database this schema belongs to, stamped when {@link Database} registers it. Snowflake spells a
     * missing object's name in full — {@code DROP TABLE nosuch} answers {@code Table 'DB.SCHEMA.NOSUCH'} —
     * and a schema that did not know its database could only report the bare name.
     */
    private String databaseName;

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(final String databaseName) {
        this.databaseName = databaseName;
    }

    /**
     * A member's name as Snowflake reports it: fully qualified, or as much of the path as is known when
     * this schema has not been registered with a database (a detached clone, say).
     */
    /** {@code database.schema.member} — the fully spelled name a DDL miss is reported under. */
    public String qualifiedName(final String memberName) {
        return qualified(memberName);
    }

    private String qualified(final String memberName) {
        return databaseName != null
            ? QualifiedName.join(databaseName, getName(), memberName) : QualifiedName.join(getName(), memberName);
    }

    /**
     * TRANSIENT — written on the CREATE, or INHERITED from a transient database.
     *
     * <p>Live-verified: every schema of a transient database is itself transient, PUBLIC included,
     * so this is not simply a copy of the CREATE's modifier. A permanent schema cannot be made in a
     * transient database, which is why the inheritance is applied when the schema is added rather
     * than left to whoever wrote the statement.
     */
    private boolean transientObject;

    public boolean isTransientObject() {
        return transientObject;
    }

    /** The parameters the schema sets on itself; the retention is kept apart. */
    private final ObjectParameters parameters = new ObjectParameters();

    /** Whether the schema is a managed access schema (WITH MANAGED ACCESS). */
    private boolean managedAccess;

    /** The parameters the schema sets on itself; the ones it does not set come from its database. */
    public ObjectParameters getParameters() {
        return parameters;
    }

    /** Whether the schema is a managed access schema: only its owner grants privileges on its objects. */
    public boolean isManagedAccess() {
        return managedAccess;
    }

    /** Makes the schema a managed access schema, or a regular one. */
    public void setManagedAccess(final boolean value) {
        this.managedAccess = value;
    }

    public void setTransientObject(final boolean value) {
        this.transientObject = value;
    }

    public Schema(final String name) {
        super(name);
        this.tables = new ConcurrentHashMap<>();
        this.shadowedTables = new ConcurrentHashMap<>();
        this.views = new ConcurrentHashMap<>();
        this.materializedViews = new ConcurrentHashMap<>();
        this.dynamicTables = new ConcurrentHashMap<>();
        this.procedures = new ConcurrentHashMap<>();
        this.functions = new ConcurrentHashMap<>();
        this.streams = new ConcurrentHashMap<>();
        this.tasks = new ConcurrentHashMap<>();
        this.pipes = new ConcurrentHashMap<>();
        this.sequences = new ConcurrentHashMap<>();
        this.contacts = new ConcurrentHashMap<>();
        this.maskingPolicies = new ConcurrentHashMap<>();
        this.projectionPolicies = new ConcurrentHashMap<>();
        this.aggregationPolicies = new ConcurrentHashMap<>();
        this.joinPolicies = new ConcurrentHashMap<>();
        this.rowAccessPolicies = new ConcurrentHashMap<>();
        this.tags = new ConcurrentHashMap<>();
        this.stages = new ConcurrentHashMap<>();
        this.cortexSearchServices = new ConcurrentHashMap<>();
        this.fileFormats = new ConcurrentHashMap<>();
    }

    /**
     * Refuses a new object in this schema when nothing may be created in it: a database's INFORMATION_SCHEMA is
     * read-only, so a CREATE there, and a RENAME that would move an object there, refuses before anything changes,
     * whatever the object's kind: {@code Insufficient privileges to operate on schema 'INFORMATION_SCHEMA'.}
     * (live-verified).
     *
     * @param kinds the kind of object, plural and in lower case ({@code "views"})
     */
    public void requireCreatable(final String kinds) {
        if ("INFORMATION_SCHEMA".equals(getName())) {
            throw new RuntimeException(SqlAccessControlError.insufficientPrivileges("schema", getName()));
        }
    }

    /**
     * {@link #requireCreatable(String)} for the objects a CREATE privilege creates: CREATE_ROW_ACCESS_POLICY creates
     * "row access policies".
     *
     * @param createPrivilege the CREATE privilege the new object needs
     */
    public void requireCreatable(final Privilege createPrivilege) {
        final String kind = createPrivilege.name().substring("CREATE_".length()).replace('_', ' ')
            .toLowerCase(Locale.ROOT);
        requireCreatable(kind.endsWith("y") ? kind.substring(0, kind.length() - 1) + "ies" : kind + "s");
    }

    // Tables
    public void addTable(final Table table) {
        requireCreatable("tables");
        rejectNameHeldByOtherKind(table.getName(), RelationKind.TABLE, table.isTemporary());
        final String upperName = table.getName();
        if (table.isSessionTemporary()) {
            final Map<String, Table> own = sessionMap(sessionTables, table.getCreatedInSession());
            if (own.containsKey(upperName)) {
                throw new NameTakenException(SqlCompilationError.of(
                    "Object '" + SqlIdentifiers.spellCanonical(table.getName()) + "' already exists."));
            }
            table.markCatalogResident();
            own.put(upperName, table);
            return;
        }
        if (tables.containsKey(upperName)) {
            throw new NameTakenException(SqlCompilationError.of(
                "Object '" + SqlIdentifiers.spellCanonical(table.getName()) + "' already exists."));
        }
        register(table);
    }

    /**
     * Puts a table into this schema's map and marks it as the catalog's own — the ONE place a table
     * becomes catalog-resident, so no registration path can forget to say so and leave the table
     * looking like a derived relation to the type rules (see {@link Table#isCatalogResident()}).
     * {@link #addTable} enforces the create-time checks first; {@code clone()} has its own and so
     * calls straight through.
     */
    private void register(final Table table) {
        table.markCatalogResident();
        tables.put(table.getName(), table);
    }

    public void dropTable(final String name) {
        final Map<String, Table> own = ownTables();
        if (own != null && own.remove(keyFor(own, name)) != null) {
            return;
        }
        if (!tables.containsKey(keyFor(tables, name))) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Table", qualified(name)));
        }
        tables.remove(keyFor(tables, name));
    }

    public Table getTable(final String name) {
        return getTable(name, qualified(name), "Table");
    }

    /**
     * The table a SQL reference names, matched EXACTLY. Live reports a miss as
     * {@code Object 'DB.SCHEMA.MIXEDTBL' does not exist or not authorized.} — "Object", not "Table".
     */
    public Table tableExact(final String name) {
        final Table own = ownTable(name);
        if (own != null && own.getName().equals(name)) {
            return own;
        }
        final Table table = heldTable(name);
        if (table == null) {
            final Table session = anySessionTable(name);
            return session != null && session.getName().equals(name) ? session : null;
        }
        return hiddenHere(table) ? null : table;
    }

    /**
     * The table held under exactly this name, whichever session's it is: what a statement's own lookups never
     * reach of another session's temporary table, and what the engine's own bookkeeping reaches all the same.
     *
     * @param name the canonical name
     * @return the table, or null
     */
    public Table heldTable(final String name) {
        final Table table = tables.get(keyFor(tables, name));
        if (table == null || !table.getName().equals(name)) {
            return null;
        }
        return table;
    }

    /**
     * Whether a table is another session's temporary one, which a statement neither reaches nor lists
     * (live-verified). A table a temporary one hides a permanent table beneath stays as it is, its storage
     * holding the hidden rows aside.
     */
    private boolean hiddenHere(final Table table) {
        return othersTemporary(table, table.isTemporary()) && !shadowedTables.containsKey(table.getName());
    }

    /**
     * Whether an object is another session's temporary one: one a session created, read in another session. The
     * engine's own session, which serves no bound session, reaches every one.
     */
    private static boolean othersTemporary(final SqlObject object, final boolean temporary) {
        final Long current = StatementSession.current();
        return temporary && current != null && object.getCreatedInSession() != null
            && !current.equals(object.getCreatedInSession());
    }

    /**
     * The table, or a miss reported as {@code reportedKind 'reportedName'}. A DDL statement always spells
     * the name in full, while a query or DML statement echoes what the writer wrote and calls a missing
     * FROM-clause name an {@code Object} — so the caller, which knows the statement, chooses both.
     */
    public Table getTable(final String name, final String reportedName, final String reportedKind) {
        final Table own = ownTable(name);
        if (own != null) {
            return own;
        }
        final Table table = tables.get(keyFor(tables, name));
        if (table == null && anySessionTable(name) != null) {
            return anySessionTable(name);
        }
        if (table == null || hiddenHere(table)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist(reportedKind, reportedName));
        }
        return table;
    }

    public boolean hasTable(final String name) {
        if (ownTable(name) != null) {
            return true;
        }
        final Table table = tables.get(keyFor(tables, name));
        return table != null && !hiddenHere(table) || table == null && anySessionTable(name) != null;
    }

    /**
     * The tables a statement here lists: the current session's own temporary tables and every shared one they do not
     * hide, but no other session's temporary tables. The engine's own session lists every session's.
     */
    public List<Table> getTables() {
        if (StatementSession.current() == null) {
            return allTables();
        }
        final Map<String, Table> own = ownTables();
        final List<Table> reached = new ArrayList<>();
        for (final Table table : tables.values()) {
            if (!hiddenHere(table) && (own == null || !own.containsKey(table.getName()))) {
                reached.add(table);
            }
        }
        if (own != null) {
            reached.addAll(own.values());
        }
        return reached;
    }

    /** Every table held here, every session's temporary ones included: the engine's own bookkeeping reads these. */
    public List<Table> allTables() {
        final List<Table> all = new ArrayList<>(tables.values());
        for (final Map<String, Table> own : sessionTables.values()) {
            all.addAll(own.values());
        }
        for (final Map<String, Table> aside : sessionTablesAside.values()) {
            all.addAll(aside.values());
        }
        return all;
    }

    /**
     * Remove a session's own temporary table, whichever session runs the statement: the session ended.
     *
     * @param table the table
     */
    public void removeSessionTable(final Table table) {
        final Map<String, Table> own = sessionTables.get(table.getCreatedInSession());
        if (own != null && own.get(table.getName()) == table) {
            own.remove(table.getName());
            if (own.isEmpty()) {
                sessionTables.remove(table.getCreatedInSession());
            }
        }
    }

    /**
     * Whether the current session holds a temporary table of its own under this exact name.
     *
     * @param name the canonical name
     * @return true when it does
     */
    public boolean holdsOwnTable(final String name) {
        final Table own = ownTable(name);
        return own != null && own.getName().equals(name);
    }

    /**
     * Set the current session's own temporary table of a name aside while the session creates a permanent table of
     * that name, so the CREATE meets only the tables every session shares; {@link #bringOwnTableBack} restores it.
     *
     * @param name the canonical name
     */
    public void setOwnTableAside(final String name) {
        final Map<String, Table> own = ownTables();
        final Table table = own == null ? null : own.remove(name);
        if (table != null) {
            sessionMap(sessionTablesAside, StatementSession.current()).put(name, table);
        }
    }

    /**
     * Bring the current session's own temporary table of a name back above the shared table of its name.
     *
     * @param name the canonical name
     * @return whether a table was set aside under it
     */
    public boolean bringOwnTableBack(final String name) {
        final Long session = StatementSession.current();
        final Map<String, Table> aside = session == null ? null : sessionTablesAside.get(session);
        final Table table = aside == null ? null : aside.remove(name);
        if (table != null) {
            sessionMap(sessionTables, session).put(name, table);
        }
        return table != null;
    }

    /** The current session's own temporary tables, or null for none (or the engine's own session). */
    private Map<String, Table> ownTables() {
        final Long session = StatementSession.current();
        return session == null ? null : sessionTables.get(session);
    }

    /** The current session's own temporary table that a name reaches, or null. */
    private Table ownTable(final String name) {
        final Map<String, Table> own = ownTables();
        return own == null ? null : own.get(keyFor(own, name));
    }

    /**
     * A session's own temporary table of a name, read by the engine's own session, which reaches every session's
     * temporary tables where no shared table holds the name; null in any other session.
     */
    private Table anySessionTable(final String name) {
        if (StatementSession.current() != null) {
            return null;
        }
        for (final Map<String, Table> held : sessionTables.values()) {
            final Table table = held.get(keyFor(held, name));
            if (table != null) {
                return table;
            }
        }
        return null;
    }

    /** A session's map in a per-session table, made when it has none. */
    private static <T> Map<String, T> sessionMap(final Map<Long, Map<String, T>> bySession, final Long session) {
        Map<String, T> held = bySession.get(session);
        if (held == null) {
            held = new ConcurrentHashMap<>();
            final Map<String, T> raced = bySession.putIfAbsent(session, held);
            if (raced != null) {
                held = raced;
            }
        }
        return held;
    }

    /**
     * The permanent table a temporary table of that name hides here, matched EXACTLY, or null. A
     * temporary table may take a permanent table's name and then shadows it: references, SHOW TABLES
     * and DDL reach the temporary table, INFORMATION_SCHEMA lists both, and dropping or renaming the
     * temporary table uncovers the permanent one (live-verified).
     */
    public Table shadowedTable(final String name) {
        return shadowedTables.get(name);
    }

    /**
     * Every permanent table a temporary table hides in this schema: beneath a temporary table every session reads,
     * and beneath one of the current session's own.
     */
    public List<Table> getShadowedTables() {
        final List<Table> hidden = new ArrayList<>(shadowedTables.values());
        final Map<String, Table> own = ownTables();
        if (own != null) {
            for (final String name : own.keySet()) {
                final Table shared = tables.get(name);
                if (shared != null && !hiddenHere(shared)) {
                    hidden.add(shared);
                }
            }
        }
        return hidden;
    }

    /**
     * Every table here that is not TEMPORARY, those a temporary table hides included — what a clone of
     * the schema copies (live-verified).
     */
    public List<Table> getNonTemporaryTables() {
        final List<Table> result = new ArrayList<>(shadowedTables.values());
        for (final Table table : tables.values()) {
            if (!table.isTemporary()) {
                result.add(table);
            }
        }
        return result;
    }

    /** Put a permanent table beneath the temporary table of its name, as a restored snapshot recorded it. */
    public void addShadowedTable(final Table table) {
        table.markCatalogResident();
        shadowedTables.put(table.getName(), table);
    }

    /**
     * Trade the table this schema answers to a name for the one hidden under it, either of which may be
     * absent: a temporary table steps aside for the permanent table beneath it, or a permanent table goes
     * beneath a temporary one. The storage engine trades their rows the same way under the same name.
     */
    public void swapShadow(final String name) {
        final Table visible = tables.remove(name);
        final Table hidden = shadowedTables.remove(name);
        if (hidden != null) {
            tables.put(name, hidden);
        }
        if (visible != null) {
            shadowedTables.put(name, visible);
        }
    }

    // Views
    public void addView(final View view) {
        // Allow system views to be added during initialization, but protect after that
        rejectNameHeldByOtherKind(view.getName(), RelationKind.VIEW, view.isTemporary());
        final String upperName = view.getName();
        final Map<String, View> own = view.isTemporary() && view.getCreatedInSession() != null
            ? sessionMap(sessionViews, view.getCreatedInSession()) : null;
        if (views.containsKey(upperName) || own != null && own.containsKey(upperName)) {
            throw new NameTakenException(SqlCompilationError.of(
                "Object '" + SqlIdentifiers.spellCanonical(view.getName()) + "' already exists."));
        }
        (own != null ? own : views).put(upperName, view);
    }

    public void dropView(final String name) {
        final Map<String, View> own = ownViews();
        if (own != null && own.remove(keyFor(own, name)) != null) {
            return;
        }
        final String upperName = keyFor(views, name);
        if (!views.containsKey(upperName)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("View", qualified(name)));
        }
        // INFORMATION_SCHEMA is read-only: dropping one of its views refuses with the
        // access-control wording. Protection is keyed on the SCHEMA — a user view elsewhere that
        // merely shares a system view's name is an ordinary view and drops freely (live-verified).
        if ("INFORMATION_SCHEMA".equals(getName())) {
            throw new RuntimeException(SqlAccessControlError.insufficientPrivileges("view", upperName));
        }
        views.remove(upperName);
    }

    public View getView(final String name) {
        final View own = ownView(name);
        if (own != null) {
            return own;
        }
        final View view = views.get(keyFor(views, name));
        if (view == null || othersTemporary(view, view.isTemporary())) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("View", qualified(name)));
        }
        return view;
    }

    /** Whether a view of this name exists, without the throwing lookup {@link #getView} performs. */
    public boolean hasView(final String name) {
        if (ownView(name) != null) {
            return true;
        }
        final View view = views.get(keyFor(views, name));
        return view != null && !othersTemporary(view, view.isTemporary());
    }

    /**
     * Whether a view carries exactly this canonical name - the check a statement makes, where a name that differs only
     * in case is another view (live-verified).
     */
    public boolean hasViewExact(final String name) {
        final Map<String, View> own = ownViews();
        if (own != null && own.containsKey(name)) {
            return true;
        }
        final View view = views.get(name);
        return view != null && !othersTemporary(view, view.isTemporary());
    }

    /**
     * Drops the view of exactly this canonical name, refused as missing when only another case of it exists.
     *
     * @param name the canonical name
     */
    public void dropViewExact(final String name) {
        if (!hasViewExact(name)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("View", qualified(name)));
        }
        dropView(name);
    }

    /** The views a statement here lists: every one but another session's temporary ones. */
    public List<View> getViews() {
        final List<View> reached = new ArrayList<>();
        for (final View view : views.values()) {
            if (!othersTemporary(view, view.isTemporary())) {
                reached.add(view);
            }
        }
        final Map<String, View> own = ownViews();
        if (own != null) {
            reached.addAll(own.values());
        }
        return reached;
    }

    /** Every view held here, every session's temporary ones included: the engine's own bookkeeping reads these. */
    public List<View> allViews() {
        final List<View> all = new ArrayList<>(views.values());
        for (final Map<String, View> own : sessionViews.values()) {
            all.addAll(own.values());
        }
        return all;
    }

    /**
     * Remove a session's own temporary view, whichever session runs the statement: the session ended.
     *
     * @param view the view
     * @return whether it was a session's own view
     */
    public boolean removeSessionView(final View view) {
        final Map<String, View> own = view.getCreatedInSession() == null ? null
            : sessionViews.get(view.getCreatedInSession());
        if (own == null || own.get(view.getName()) != view) {
            return false;
        }
        own.remove(view.getName());
        if (own.isEmpty()) {
            sessionViews.remove(view.getCreatedInSession());
        }
        return true;
    }

    /** The current session's own temporary views, or null for none (or the engine's own session). */
    private Map<String, View> ownViews() {
        final Long session = StatementSession.current();
        return session == null ? null : sessionViews.get(session);
    }

    /** The current session's own temporary view that a name reaches, or null. */
    private View ownView(final String name) {
        final Map<String, View> own = ownViews();
        return own == null ? null : own.get(keyFor(own, name));
    }

    // Materialized Views
    public void addMaterializedView(final MaterializedView materializedView) {
        rejectNameHeldByOtherKind(materializedView.getName(), RelationKind.MATERIALIZED_VIEW, false);
        final String upperName = materializedView.getName();
        if (materializedViews.containsKey(upperName)) {
            throw new NameTakenException(SqlCompilationError.of(
                "Object '" + SqlIdentifiers.spellCanonical(materializedView.getName()) + "' already exists."));
        }
        materializedViews.put(upperName, materializedView);
    }

    public void dropMaterializedView(final String name) {
        final String upperName = keyFor(materializedViews, name);
        if (!materializedViews.containsKey(upperName)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Materialized view", qualified(name)));
        }
        materializedViews.remove(upperName);
    }

    public MaterializedView getMaterializedView(final String name) {
        final MaterializedView mv = materializedViews.get(keyFor(materializedViews, name));
        if (mv == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Materialized view", qualified(name)));
        }
        return mv;
    }

    public boolean hasMaterializedView(final String name) {
        return materializedViews.containsKey(keyFor(materializedViews, name));
    }

    public List<MaterializedView> getMaterializedViews() {
        return new ArrayList<>(materializedViews.values());
    }

    // Dynamic Tables
    public void addDynamicTable(final DynamicTable dt) {
        rejectNameHeldByOtherKind(dt.getName(), RelationKind.DYNAMIC_TABLE, false);
        final String upperName = dt.getName();
        if (dynamicTables.containsKey(upperName)) {
            throw new NameTakenException(SqlCompilationError.of("Object '" + SqlIdentifiers.spellCanonical(dt.getName())
                + "' already exists."));
        }
        dynamicTables.put(upperName, dt);
    }

    /*
     * A dynamic table is found by its canonical name EXACTLY, as a SQL reference finds a table (tableExact): one
     * created as "dt" and one created as DT are two dynamic tables. Its name was upper-cased at CREATE and matched
     * ignoring case, so "dt" was stored as DT and the two were one object.
     */
    public void dropDynamicTable(final String name) {
        if (!hasDynamicTable(name)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Dynamic table", qualified(name)));
        }
        dynamicTables.remove(name);
    }

    public DynamicTable getDynamicTable(final String name) {
        final DynamicTable dt = name == null ? null : dynamicTables.get(name);
        if (dt == null) throw new RuntimeException(SqlCompilationError.doesNotExist("Dynamic table", qualified(name)));
        return dt;
    }

    public boolean hasDynamicTable(final String name) {
        return name != null && dynamicTables.containsKey(name);
    }

    public List<DynamicTable> getDynamicTables() {
        return new ArrayList<>(dynamicTables.values());
    }

    // Contacts
    public void addContact(final Contact contact) {
        contacts.put(contact.getName(), contact);
    }
    public void dropContact(final String name) { contacts.remove(keyFor(contacts, name)); }
    public Contact getContact(final String name) { return contacts.get(keyFor(contacts, name)); }
    public boolean hasContact(final String name) { return contacts.containsKey(keyFor(contacts, name)); }
    public List<Contact> getContacts() { return new ArrayList<>(contacts.values()); }

    private final SecurityObjectStore securityObjects = new SecurityObjectStore();

    /** The schema's network rules, password policies and secrets. */
    public SecurityObjectStore getSecurityObjects() {
        return securityObjects;
    }

    // Join Policies
    public void addJoinPolicy(final JoinPolicy policy) {
        joinPolicies.put(policy.getName(), policy);
    }
    public void dropJoinPolicy(final String name) { joinPolicies.remove(keyFor(joinPolicies, name)); }
    public JoinPolicy getJoinPolicy(final String name) { return joinPolicies.get(keyFor(joinPolicies, name)); }
    public boolean hasJoinPolicy(final String name) { return joinPolicies.containsKey(keyFor(joinPolicies, name)); }
    public List<JoinPolicy> getJoinPolicies() { return new ArrayList<>(joinPolicies.values()); }
    public void renameJoinPolicy(final String oldName, final String newName) {
        final JoinPolicy policy = joinPolicies.get(keyFor(joinPolicies, oldName));
        if (policy == null) {
            throw new RuntimeException("Join policy not found: " + oldName);
        }
        joinPolicies.remove(keyFor(joinPolicies, oldName));
        policy.rename(newName.toUpperCase(Locale.ROOT));
        joinPolicies.put(newName.toUpperCase(Locale.ROOT), policy);
    }

    // Aggregation Policies
    public void addAggregationPolicy(final AggregationPolicy policy) {
        aggregationPolicies.put(policy.getName(), policy);
    }
    public void dropAggregationPolicy(final String name) { aggregationPolicies.remove(keyFor(aggregationPolicies, name)); }
    public AggregationPolicy getAggregationPolicy(final String name) { return aggregationPolicies.get(keyFor(aggregationPolicies, name)); }
    public boolean hasAggregationPolicy(final String name) { return aggregationPolicies.containsKey(keyFor(aggregationPolicies, name)); }
    public List<AggregationPolicy> getAggregationPolicies() { return new ArrayList<>(aggregationPolicies.values()); }
    public void renameAggregationPolicy(final String oldName, final String newName) {
        final AggregationPolicy policy = aggregationPolicies.get(keyFor(aggregationPolicies, oldName));
        if (policy == null) {
            throw new RuntimeException("Aggregation policy not found: " + oldName);
        }
        aggregationPolicies.remove(keyFor(aggregationPolicies, oldName));
        policy.rename(newName.toUpperCase(Locale.ROOT));
        aggregationPolicies.put(newName.toUpperCase(Locale.ROOT), policy);
    }

    // Projection Policies
    public void addProjectionPolicy(final ProjectionPolicy policy) {
        projectionPolicies.put(policy.getName(), policy);
    }
    public void dropProjectionPolicy(final String name) { projectionPolicies.remove(keyFor(projectionPolicies, name)); }
    public ProjectionPolicy getProjectionPolicy(final String name) { return projectionPolicies.get(keyFor(projectionPolicies, name)); }
    public boolean hasProjectionPolicy(final String name) { return projectionPolicies.containsKey(keyFor(projectionPolicies, name)); }
    public List<ProjectionPolicy> getProjectionPolicies() { return new ArrayList<>(projectionPolicies.values()); }
    public void renameProjectionPolicy(final String oldName, final String newName) {
        final ProjectionPolicy policy = projectionPolicies.get(keyFor(projectionPolicies, oldName));
        if (policy == null) {
            throw new RuntimeException("Projection policy not found: " + oldName);
        }
        projectionPolicies.remove(keyFor(projectionPolicies, oldName));
        policy.rename(newName.toUpperCase(Locale.ROOT));
        projectionPolicies.put(newName.toUpperCase(Locale.ROOT), policy);
    }

    // Masking Policies
    public void addMaskingPolicy(final MaskingPolicy policy) {
        maskingPolicies.put(policy.getName(), policy);
    }
    public void dropMaskingPolicy(final String name) { maskingPolicies.remove(keyFor(maskingPolicies, name)); }
    public MaskingPolicy getMaskingPolicy(final String name) { return maskingPolicies.get(keyFor(maskingPolicies, name)); }
    public boolean hasMaskingPolicy(final String name) { return maskingPolicies.containsKey(keyFor(maskingPolicies, name)); }
    public List<MaskingPolicy> getMaskingPolicies() { return new ArrayList<>(maskingPolicies.values()); }
    public void renameMaskingPolicy(final String oldName, final String newName) {
        final MaskingPolicy policy = maskingPolicies.get(keyFor(maskingPolicies, oldName));
        if (policy == null) {
            throw new RuntimeException("Masking policy not found: " + oldName);
        }
        maskingPolicies.remove(keyFor(maskingPolicies, oldName));
        policy.rename(newName.toUpperCase(Locale.ROOT));
        maskingPolicies.put(newName.toUpperCase(Locale.ROOT), policy);
    }

    // Row Access Policies
    public void addRowAccessPolicy(final RowAccessPolicy policy) {
        rowAccessPolicies.put(policy.getName(), policy);
    }
    public void dropRowAccessPolicy(final String name) { rowAccessPolicies.remove(keyFor(rowAccessPolicies, name)); }
    public RowAccessPolicy getRowAccessPolicy(final String name) { return rowAccessPolicies.get(keyFor(rowAccessPolicies, name)); }
    public boolean hasRowAccessPolicy(final String name) { return rowAccessPolicies.containsKey(keyFor(rowAccessPolicies, name)); }
    public List<RowAccessPolicy> getRowAccessPolicies() { return new ArrayList<>(rowAccessPolicies.values()); }
    public void renameRowAccessPolicy(final String oldName, final String newName) {
        final RowAccessPolicy policy = rowAccessPolicies.get(keyFor(rowAccessPolicies, oldName));
        if (policy == null) {
            throw new RuntimeException("Row access policy not found: " + oldName);
        }
        rowAccessPolicies.remove(keyFor(rowAccessPolicies, oldName));
        policy.rename(newName.toUpperCase(Locale.ROOT));
        rowAccessPolicies.put(newName.toUpperCase(Locale.ROOT), policy);
    }

    // Procedures and functions. A routine is stored under its canonical name - an unquoted name
    // upper-cased, a quoted one as written - and resolved by that name EXACTLY, so "a" and A are two
    // routines, each reached only by its own spelling (live-verified). getProcedure and getFunction alone
    // also answer a differently-cased name, for the engine's Java callers (see keyFor).
    public void addProcedure(final Procedure procedure) {
        final String name = procedure.getName();
        // putIfAbsent rather than get/put: the map is concurrent, and losing a race here would
        // drop an overload registered by another thread.
        List<Procedure> overloads = procedures.get(name);
        if (overloads == null) {
            overloads = new ArrayList<>();
            final List<Procedure> raced = procedures.putIfAbsent(name, overloads);
            if (raced != null) {
                overloads = raced;
            }
        }

        for (final Procedure existing : overloads) {
            if (!hasSameSignature(existing.getParameters(), procedure.getParameters())) {
                continue;
            }
            if (hasExactSignature(existing.getParameters(), procedure.getParameters())) {
                refuseSignatureHeld(name, procedure.isTemporary(), procedure, existing.isTemporary(), existing,
                    existing.getParameters(), existing.getReturnType(), existing.getReturnSized());
            } else if (sameLifetime(procedure.isTemporary(), procedure, existing.isTemporary(), existing)) {
                throw notNullOnlyTwin(name, procedure.getParameters(), procedure.returnsTable(),
                    procedure.getReturnColumns(), procedure.getReturnType(), procedure.getReturnSized());
            }
        }

        overloads.add(procedure);
    }

    /**
     * Drops the overload whose parameters match these argument types by type family.
     *
     * @param name          the procedure's exact canonical name
     * @param argumentTypes the signature written after it
     */
    public void dropProcedureBySignature(final String name, final List<DataType> argumentTypes) {
        removeProcedure(getProcedureBySignature(name, argumentTypes));
    }

    /** Removes this very overload, and its name with it when no other overload carries that name. */
    public void removeProcedure(final Procedure procedure) {
        final List<Procedure> overloads = procedures.get(procedure.getName());
        if (overloads == null) {
            return;
        }
        // By identity: routines compare equal by name alone, so remove(Object) would take a sibling.
        for (int i = 0; i < overloads.size(); i++) {
            if (overloads.get(i) == procedure) {
                overloads.remove(i);
                break;
            }
        }
        if (overloads.isEmpty()) {
            procedures.remove(procedure.getName());
        }
    }

    public Procedure getProcedure(final String name) {
        final String upperName = keyFor(procedures, name);
        final List<Procedure> overloads = getProcedureOverloads(upperName);
        if (overloads == null || overloads.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Procedure", qualified(name)));
        }

        if (overloads.size() > 1) {
            throw new RuntimeException("Procedure " + name + " has multiple overloads. Use getProcedureBySignature() with parameter types.");
        }

        return overloads.get(0);
    }

    /**
     * The overload whose parameters match these argument types by type family; a name or a signature
     * nothing carries does not exist.
     *
     * @param name          the procedure's exact canonical name
     * @param argumentTypes the signature written after it
     * @return the overload
     */
    public Procedure getProcedureBySignature(final String name, final List<DataType> argumentTypes) {
        for (final Procedure proc : getProcedureOverloads(name)) {
            if (matchesSignature(proc.getParameters(), argumentTypes)) {
                return proc;
            }
        }
        throw new RuntimeException(SqlCompilationError.doesNotExist("Procedure", qualified(name)));
    }

    /**
     * The overload a statement names by the signature it writes: its parameters match by type family, and a type
     * written with a length, a precision or a scale matches only a parameter declared with the same (see
     * {@link #matchesWrittenSignature}).
     *
     * @param name          the procedure's exact canonical name
     * @param argumentTypes the signature written after it, each type with the parameters written on it
     * @return the overload
     */
    public Procedure getProcedureByWrittenSignature(final String name, final List<DataType> argumentTypes) {
        for (final Procedure proc : getProcedureOverloads(name)) {
            if (matchesWrittenSignature(proc.getParameters(), argumentTypes)) {
                return proc;
            }
        }
        throw new RuntimeException(SqlCompilationError.doesNotExist("Procedure", qualified(name)));
    }

    /**
     * Drops the overload a statement names by the signature it writes (see
     * {@link #getProcedureByWrittenSignature}).
     *
     * @param name          the procedure's exact canonical name
     * @param argumentTypes the signature written after it
     */
    public void dropProcedureByWrittenSignature(final String name, final List<DataType> argumentTypes) {
        removeProcedure(getProcedureByWrittenSignature(name, argumentTypes));
    }

    /** Every procedure this session reaches (see {@link #reachedProcedures}). */
    public List<Procedure> getProcedures() {
        final List<Procedure> allProcedures = new ArrayList<>();
        for (final List<Procedure> overloads : procedures.values()) {
            allProcedures.addAll(reachedProcedures(new ArrayList<>(overloads)));
        }
        return allProcedures;
    }

    /** Every procedure, each session's temporary ones included, for what outlives or owns them all. */
    public List<Procedure> allProcedures() {
        final List<Procedure> allProcedures = new ArrayList<>();
        for (final List<Procedure> overloads : procedures.values()) {
            allProcedures.addAll(overloads);
        }
        return allProcedures;
    }

    /** Every overload carrying exactly this canonical name this session reaches, or none. */
    public List<Procedure> getProcedureOverloads(final String name) {
        final List<Procedure> overloads = procedures.get(name);
        return overloads != null ? reachedProcedures(new ArrayList<>(overloads)) : new ArrayList<>();
    }

    /**
     * The overloads this session reaches of those carrying one name: the permanent ones and its own temporary ones
     * (live-verified: another session's temporary procedure is no procedure here). A permanent overload hides a
     * temporary one of its signature.
     */
    private List<Procedure> reachedProcedures(final List<Procedure> overloads) {
        final List<Procedure> reached = new ArrayList<>();
        for (final Procedure overload : overloads) {
            if (!overload.isTemporary()) {
                reached.add(overload);
                continue;
            }
            boolean hidden = !ownedHere(overload);
            for (final Procedure other : overloads) {
                hidden = hidden || !other.isTemporary() && hasExactSignature(other.getParameters(),
                    overload.getParameters());
            }
            if (!hidden) {
                reached.add(overload);
            }
        }
        return reached;
    }

    // Functions
    public void addFunction(final Function function) {
        final String name = function.getName();
        // putIfAbsent rather than get/put: the map is concurrent, and losing a race here would
        // drop an overload registered by another thread.
        List<Function> overloads = functions.get(name);
        if (overloads == null) {
            overloads = new ArrayList<>();
            final List<Function> raced = functions.putIfAbsent(name, overloads);
            if (raced != null) {
                overloads = raced;
            }
        }

        for (final Function existing : overloads) {
            if (!hasSameSignature(existing.getParameters(), function.getParameters())) {
                continue;
            }
            if (hasExactSignature(existing.getParameters(), function.getParameters())) {
                refuseSignatureHeld(name, function.isTemporary(), function, existing.isTemporary(), existing,
                    existing.getParameters(), existing.getReturnType(), existing.getReturnSized());
            } else if (sameLifetime(function.isTemporary(), function, existing.isTemporary(), existing)) {
                throw notNullOnlyTwin(name, function.getParameters(), function.isTableFunction(),
                    function.getReturnColumns(), function.getReturnType(), function.getReturnSized());
            }
        }

        overloads.add(function);
        function.setHome(this);
    }

    /**
     * Drops the overload whose parameters match these argument types by type family.
     *
     * @param name          the function's exact canonical name
     * @param argumentTypes the signature written after it
     */
    public void dropFunctionBySignature(final String name, final List<DataType> argumentTypes) {
        removeFunction(getFunctionBySignature(name, argumentTypes));
    }

    /** Removes this very overload, and its name with it when no other overload carries that name. */
    public void removeFunction(final Function function) {
        final List<Function> overloads = functions.get(function.getName());
        if (overloads == null) {
            return;
        }
        // By identity: routines compare equal by name alone, so remove(Object) would take a sibling.
        for (int i = 0; i < overloads.size(); i++) {
            if (overloads.get(i) == function) {
                overloads.remove(i);
                if (function.getHome() == this) {
                    function.setHome(null);
                }
                break;
            }
        }
        if (overloads.isEmpty()) {
            functions.remove(function.getName());
        }
    }

    public Function getFunction(final String name) {
        final String upperName = keyFor(functions, name);
        final List<Function> overloads = getFunctionOverloads(upperName);
        if (overloads == null || overloads.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Function", qualified(name)));
        }

        if (overloads.size() > 1) {
            throw new RuntimeException("Function " + name + " has multiple overloads. Use getFunctionBySignature() with parameter types.");
        }

        return overloads.get(0);
    }

    /**
     * The overload whose parameters match these argument types by type family; a name or a signature
     * nothing carries does not exist.
     *
     * @param name          the function's exact canonical name
     * @param argumentTypes the signature written after it
     * @return the overload
     */
    public Function getFunctionBySignature(final String name, final List<DataType> argumentTypes) {
        for (final Function func : getFunctionOverloads(name)) {
            if (matchesSignature(func.getParameters(), argumentTypes)) {
                return func;
            }
        }
        throw new RuntimeException(SqlCompilationError.doesNotExist("Function", qualified(name)));
    }

    /**
     * The overload a statement names by the signature it writes: its parameters match by type family, and a type
     * written with a length, a precision or a scale matches only a parameter declared with the same (see
     * {@link #matchesWrittenSignature}).
     *
     * @param name          the function's exact canonical name
     * @param argumentTypes the signature written after it, each type with the parameters written on it
     * @return the overload
     */
    public Function getFunctionByWrittenSignature(final String name, final List<DataType> argumentTypes) {
        for (final Function func : getFunctionOverloads(name)) {
            if (matchesWrittenSignature(func.getParameters(), argumentTypes)) {
                return func;
            }
        }
        throw new RuntimeException(SqlCompilationError.doesNotExist("Function", qualified(name)));
    }

    /**
     * Drops the overload a statement names by the signature it writes (see
     * {@link #getFunctionByWrittenSignature}).
     *
     * @param name          the function's exact canonical name
     * @param argumentTypes the signature written after it
     */
    public void dropFunctionByWrittenSignature(final String name, final List<DataType> argumentTypes) {
        removeFunction(getFunctionByWrittenSignature(name, argumentTypes));
    }

    /** Every function this session reaches (see {@link #reachedFunctions}). */
    public List<Function> getFunctions() {
        final List<Function> allFunctions = new ArrayList<>();
        for (final List<Function> overloads : functions.values()) {
            allFunctions.addAll(reachedFunctions(new ArrayList<>(overloads)));
        }
        return allFunctions;
    }

    /**
     * Every function, each session's temporary ones included, for what outlives or owns them all - and for
     * INFORMATION_SCHEMA.FUNCTIONS, which lists another session's temporary function (live-verified).
     */
    public List<Function> allFunctions() {
        final List<Function> allFunctions = new ArrayList<>();
        for (final List<Function> overloads : functions.values()) {
            allFunctions.addAll(overloads);
        }
        return allFunctions;
    }

    /** Every overload carrying exactly this canonical name this session reaches, or none. */
    public List<Function> getFunctionOverloads(final String name) {
        final List<Function> overloads = functions.get(name);
        return overloads != null ? reachedFunctions(new ArrayList<>(overloads)) : new ArrayList<>();
    }

    /**
     * The overloads this session reaches of those carrying one name: the permanent ones and its own temporary ones.
     * Another session's temporary function is no function here, and a permanent function created after a temporary
     * one of its signature is the one every session calls, its creator's included (live-verified).
     */
    private List<Function> reachedFunctions(final List<Function> overloads) {
        final List<Function> reached = new ArrayList<>();
        for (final Function overload : overloads) {
            if (!overload.isTemporary()) {
                reached.add(overload);
                continue;
            }
            boolean hidden = !ownedHere(overload);
            for (final Function other : overloads) {
                hidden = hidden || !other.isTemporary() && hasExactSignature(other.getParameters(),
                    overload.getParameters());
            }
            if (!hidden) {
                reached.add(overload);
            }
        }
        return reached;
    }

    /** Whether a temporary object is this session's own. */
    private static boolean ownedHere(final SqlObject temporary) {
        return Objects.equals(temporary.getCreatedInSession(), StatementSession.current());
    }

    /**
     * Refuses a new routine an existing one of its signature holds the name for (live-verified): a temporary routine
     * may not take a permanent one's signature, refused with that one's signature and return type spelled
     * ({@code Object 'PERMF():NUMBER(38,0)' already exists.}); a permanent routine beside a temporary one, and a
     * temporary one beside another session's, are each created; two of the same lifetime and session are refused
     * for the name alone.
     *
     * @param name             the routine's canonical name
     * @param temporary        whether the new routine is temporary
     * @param created          the new routine
     * @param existingTemporary whether the existing one is temporary
     * @param existing         the existing routine of the signature
     * @param parameters       the existing routine's parameters
     * @param returnType       the existing routine's return type
     * @param returnSized      whether that type was written with its width
     */
    private static void refuseSignatureHeld(final String name, final boolean temporary, final SqlObject created,
                                            final boolean existingTemporary, final SqlObject existing,
                                            final List<Parameter> parameters, final DataType returnType,
                                            final Boolean returnSized) {
        if (temporary && !existingTemporary) {
            final StringBuilder signature = new StringBuilder(SqlIdentifiers.spellCanonical(name)).append('(');
            for (int i = 0; i < parameters.size(); i++) {
                signature.append(i == 0 ? "" : ", ").append(SqlTypeNames.routineType(parameters.get(i).getDataType()));
            }
            signature.append("):").append(returnType == null ? "TABLE" : SqlTypeNames.routineType(returnType,
                returnSized));
            throw new RuntimeException(SqlCompilationError.of("Object '" + signature + "' already exists."));
        }
        if (sameLifetime(temporary, created, existingTemporary, existing)) {
            throw new RuntimeException(alreadyExists(name));
        }
    }

    /** Whether two routines are both permanent, or both temporary to one session. */
    private static boolean sameLifetime(final boolean temporary, final SqlObject created,
                                        final boolean existingTemporary, final SqlObject existing) {
        return temporary == existingTemporary
            && (!temporary || Objects.equals(created.getCreatedInSession(), existing.getCreatedInSession()));
    }

    /**
     * The refusal a routine gets whose signature an overload of the same lifetime holds but for a structured field's
     * NOT NULL, the new routine spelled with its parameters' names and what it returns:
     * {@code Object 'F1(X OBJECT(a NUMBER NOT NULL)):VARCHAR' already exists.}, and
     * {@code Object 'H1(X OBJECT(a NUMBER)):TABLE: (C NUMBER)' already exists.} for one that returns a table
     * (live-verified). It is judged when the routine is added, after everything else about it.
     */
    private static RuntimeException notNullOnlyTwin(final String name, final List<Parameter> parameters,
                                                    final boolean table, final List<Parameter> columns,
                                                    final DataType returnType, final Boolean returnSized) {
        final StringBuilder signature = new StringBuilder(SqlIdentifiers.spellCanonical(name)).append('(')
            .append(namedTypes(parameters)).append("):");
        if (table) {
            signature.append("TABLE: (").append(namedTypes(columns)).append(')');
        } else {
            signature.append(SqlTypeNames.routineType(returnType, returnSized));
        }
        return new RuntimeException(SqlCompilationError.of("Object '" + signature + "' already exists."));
    }

    /**
     * Parameters or columns by name and bare type, as a routine's spelled signature lists them:
     * {@code X OBJECT(a NUMBER NOT NULL), Z NUMBER}.
     */
    private static String namedTypes(final List<Parameter> items) {
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            text.append(i == 0 ? "" : ", ").append(SqlIdentifiers.spellCanonical(items.get(i).getName()))
                .append(' ').append(StructuredTypes.signatureText(items.get(i).getDataType()));
        }
        return text.toString();
    }

    /**
     * Whether a new function of that lifetime would find this signature taken (see {@link #addFunction}): a
     * temporary function by this session's own temporary one, a permanent function by a permanent one. A temporary
     * function over a permanent one is refused for its signature when it is added.
     *
     * @param name       the function's canonical name
     * @param parameters the new signature
     * @param temporary  whether the new function is temporary
     * @return whether the signature is taken
     */
    public boolean hasFunctionSignature(final String name, final List<Parameter> parameters, final boolean temporary) {
        for (final Function existing : getFunctionOverloads(name)) {
            if (existing.isTemporary() == temporary && hasExactSignature(existing.getParameters(), parameters)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@link #hasFunctionSignature(String, List, boolean)} for a procedure.
     *
     * @param name       the procedure's canonical name
     * @param parameters the new signature
     * @param temporary  whether the new procedure is temporary
     * @return whether the signature is taken
     */
    public boolean hasProcedureSignature(final String name, final List<Parameter> parameters, final boolean temporary) {
        for (final Procedure existing : getProcedureOverloads(name)) {
            if (existing.isTemporary() == temporary && hasExactSignature(existing.getParameters(), parameters)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a function of that name already carries this signature (see {@link #addFunction}). */
    public boolean hasFunctionSignature(final String name, final List<Parameter> parameters) {
        for (final Function existing : getFunctionOverloads(name)) {
            if (hasSameSignature(existing.getParameters(), parameters)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a procedure of that name already carries this signature (see {@link #addProcedure}). */
    public boolean hasProcedureSignature(final String name, final List<Parameter> parameters) {
        for (final Procedure existing : getProcedureOverloads(name)) {
            if (hasSameSignature(existing.getParameters(), parameters)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The refusal a routine name already carrying the signature gets: the name quoted only when it needs
     * quotes, {@code Object '"b"' already exists.} for a quoted {@code "b"} (live-verified).
     *
     * @param name the routine's canonical name
     * @return the refusal's message
     */
    public static String alreadyExists(final String name) {
        return SqlCompilationError.of("Object '" + SqlIdentifiers.spellCanonical(name) + "' already exists.");
    }

    /**
     * Refuses a routine's name and signature that a routine of the other kind holds: a function is not created
     * over a procedure, with OR REPLACE and with IF NOT EXISTS neither, nor renamed to one's name, and a
     * procedure not over a function (live-verified).
     *
     * @param function   true when the routine being named is a function, false for a procedure
     * @param name       the routine's canonical name
     * @param parameters its signature
     */
    public void requireNotHeldByOtherKind(final boolean function, final String name,
                                          final List<Parameter> parameters) {
        if (function ? hasProcedureSignature(name, parameters) : hasFunctionSignature(name, parameters)) {
            throw new RuntimeException(SqlCompilationError.of("Object '" + SqlIdentifiers.spellCanonical(name)
                + "' already exists as " + (function ? "PROCEDURE" : "FUNCTION")));
        }
    }

    /**
     * Refuses a function signature that an overload the name already has would compete with for some call: the
     * two take a common number of arguments and agree in the types of that many leading parameters, so only
     * optional arguments tell them apart (live-verified). {@code f(a NUMBER)} beside {@code f(a NUMBER, b NUMBER
     * DEFAULT 0)} is refused, in either order, as are {@code f()} beside {@code f(o NUMBER DEFAULT 0)} and two
     * overloads whose optional arguments alone differ in type; {@code f(a NUMBER, b VARCHAR)} beside
     * {@code f(a NUMBER, b NUMBER DEFAULT 0)} stands, a call of two arguments telling them apart by type. An
     * overload of exactly this signature is not judged here: it is the one a CREATE OR REPLACE replaces, or the
     * name already taken.
     *
     * @param name       the function's canonical name
     * @param parameters the new signature
     */
    public void requireUnambiguousFunction(final String name, final List<Parameter> parameters) {
        for (final Function existing : getFunctionOverloads(name)) {
            if (competes(existing.getParameters(), parameters)) {
                throw new RuntimeException(ambiguousOverload("FUNCTION", name));
            }
        }
    }

    /**
     * {@link #requireUnambiguousFunction} for a procedure.
     *
     * @param name       the procedure's canonical name
     * @param parameters the new signature
     */
    public void requireUnambiguousProcedure(final String name, final List<Parameter> parameters) {
        for (final Procedure existing : getProcedureOverloads(name)) {
            if (competes(existing.getParameters(), parameters)) {
                throw new RuntimeException(ambiguousOverload("PROCEDURE", name));
            }
        }
    }

    /**
     * The refusal an overload that would compete with an existing one gets, on one line (live-verified).
     *
     * @param kind FUNCTION or PROCEDURE
     * @param name the routine's canonical name
     * @return the refusal's message
     */
    public static String ambiguousOverload(final String kind, final String name) {
        return SqlCompilationError.inline("Cannot overload " + kind + " '" + SqlIdentifiers.spellCanonical(name)
            + "' as it would cause ambiguous " + kind + " overloading.");
    }

    /** Whether two different signatures take a call of some argument count with the same leading types. */
    private boolean competes(final List<Parameter> one, final List<Parameter> other) {
        if (hasExactSignature(one, other)) {
            return false;
        }
        // The fewest arguments both take: where the leading types differ there, they differ for every longer call.
        final int fewest = Math.max(requiredCount(one), requiredCount(other));
        if (fewest > Math.min(one.size(), other.size())) {
            return false;
        }
        for (int i = 0; i < fewest; i++) {
            if (!sameSignatureType(one.get(i).getDataType(), other.get(i).getDataType(), true)) {
                return false;
            }
        }
        return true;
    }

    /** How many leading parameters a call must supply: the index after the last one with no default. */
    private static int requiredCount(final List<Parameter> parameters) {
        int required = 0;
        for (int i = 0; i < parameters.size(); i++) {
            if (!parameters.get(i).hasDefault()) {
                required = i + 1;
            }
        }
        return required;
    }

    // Helper methods for signature matching
    /**
     * Whether two parameter lists are one overload: each parameter of one type family, and a structured parameter of
     * one shape besides — its fields by name and in order, each member's family — whatever the widths inside it and
     * whether a field says NOT NULL. {@code f(OBJECT(b INT))} and {@code f(OBJECT(a VARCHAR))} are overloads of
     * {@code f(OBJECT(a INT))}, and {@code f(OBJECT(a NUMBER(10,2)))} is the same signature (live-verified).
     */
    private boolean hasSameSignature(final List<Parameter> params1, final List<Parameter> params2) {
        if (params1.size() != params2.size()) {
            return false;
        }

        for (int i = 0; i < params1.size(); i++) {
            if (!sameSignatureType(params1.get(i).getDataType(), params2.get(i).getDataType(), false)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Whether two parameter lists are one signature to the letter: {@link #hasSameSignature}, and a structured field
     * that says NOT NULL in one says it in the other. A plain CREATE finds its name taken only by such an overload, an
     * overload competes with another by such types, and a temporary routine steps aside only for a permanent one of
     * such a signature; one that differs from an overload of its lifetime by a field's NOT NULL alone is refused
     * when it is added (live-verified).
     */
    private static boolean hasExactSignature(final List<Parameter> params1, final List<Parameter> params2) {
        if (params1.size() != params2.size()) {
            return false;
        }
        for (int i = 0; i < params1.size(); i++) {
            if (!sameSignatureType(params1.get(i).getDataType(), params2.get(i).getDataType(), true)) {
                return false;
            }
        }
        return true;
    }

    /** Whether two parameter types are one in a signature: one type, and one shape when structured. */
    private static boolean sameSignatureType(final DataType one, final DataType other, final boolean notNull) {
        return one.equals(other) && shapeOf(one, notNull).equals(shapeOf(other, notNull));
    }

    private boolean matchesSignature(final List<Parameter> params, final List<DataType> argumentTypes) {
        if (params.size() != argumentTypes.size()) {
            return false;
        }

        for (int i = 0; i < params.size(); i++) {
            final DataType declared = params.get(i).getDataType();
            if (!signatureFamily(declared).equals(signatureFamily(argumentTypes.get(i)))
                    || !shapeOf(declared, false).equals(shapeOf(argumentTypes.get(i), false))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Re-points the sequence defaults of this schema's tables from the sequences of one schema to the sequences
     * of the same names in its clone (see {@link SequenceDefault}). A default bound to a sequence of any other
     * schema, or to one the clone does not carry, is left as it is.
     *
     * @param source the schema that was cloned
     * @param clone  its clone
     */
    public void rebindSequenceDefaults(final Schema source, final Schema clone) {
        for (final Table table : tables.values()) {
            for (final TableColumn column : table.columnsView()) {
                if (!(column.getDefaultValue() instanceof SequenceDefault)) {
                    continue;
                }
                final Sequence bound = ((SequenceDefault) column.getDefaultValue()).getSequence();
                final Sequence cloned = bound.getHome() == source ? clone.sequences.get(bound.getName()) : null;
                if (cloned != null) {
                    column.setDefaultValue(new SequenceDefault(cloned));
                }
            }
        }
    }

    /**
     * Whether a signature a statement WRITES names these parameters. The family decides first (see
     * {@link #signatureFamily}); then a type written with a length, a precision or a scale names only a parameter
     * declared with the same, while one written bare names any of its family (live-verified):
     *
     * <pre>
     *   f(code CHAR(2))      VARCHAR, STRING, TEXT, CHAR(2), VARCHAR(2)     not CHAR, CHARACTER, NCHAR, VARCHAR(3)
     *   f(code VARCHAR(1))   CHAR — a bare CHAR is a text of length 1
     *   f(code VARCHAR)      VARCHAR, VARCHAR(134217728)                    not CHAR, VARCHAR(5), VARCHAR(16777216)
     *   f(code VARCHAR(16777216))   VARCHAR, VARCHAR(16777216)              not VARCHAR(134217728)
     *   f(n NUMBER(5,2))     NUMBER, INT, NUMBER(38,0), NUMBER(5,2)         not NUMBER(10,0)
     *   f(b BINARY(4))       BINARY, BINARY(4)                              not BINARY(5), BINARY(8388608)
     *   f(b BINARY)          BINARY, BINARY(67108864)                       not BINARY(8388608)
     *   f(t TIMESTAMP_NTZ(3)) TIMESTAMP_NTZ, TIMESTAMP_NTZ(3)               not TIMESTAMP_NTZ(6), TIMESTAMP_NTZ(9)
     *   f(t TIME)            TIME, TIME(9)                                  not TIME(3)
     * </pre>
     *
     * <p>A parameter declared without a length is as wide as a text or a binary gets, which is not the width a
     * column of that spelling has: it is named by the written maximum and by no other length. A number is the one
     * family whose default written out — NUMBER(38,0) — reads as written bare.
     */
    private boolean matchesWrittenSignature(final List<Parameter> params, final List<DataType> argumentTypes) {
        if (!matchesSignature(params, argumentTypes)) {
            return false;
        }
        for (int i = 0; i < params.size(); i++) {
            if (!namesDeclaredWidth(params.get(i), argumentTypes.get(i))
                    || !signatureShape(params.get(i).getDataType()).equals(signatureShape(argumentTypes.get(i)))) {
                return false;
            }
        }
        return true;
    }

    /**
     * What of a type a written signature has to repeat to name a parameter of it, beyond its family: a structured
     * type's shape — its fields by name and in order, each field's and element's own family, and a field's NOT
     * NULL — while the widths inside a shape count for nothing: {@code f(OBJECT(a NUMBER, b VARCHAR(5)))} names a
     * parameter declared {@code OBJECT(a INT, b VARCHAR(10))}, and neither {@code OBJECT(b VARCHAR(10), a INT)},
     * {@code OBJECT(a VARCHAR)} nor a shape without a field's NOT NULL does (live-verified). Empty for a type
     * that has no shape.
     */
    private static String signatureShape(final DataType type) {
        return shapeOf(type, true);
    }

    /** A type's shape (see {@link #signatureShape}), a field's NOT NULL in it or not. */
    private static String shapeOf(final DataType type, final boolean notNull) {
        if (type instanceof StructuredObjectType) {
            final StringBuilder shape = new StringBuilder("OBJECT(");
            for (final StructuredField field : ((StructuredObjectType) type).getFields()) {
                shape.append(field.getName()).append(' ').append(shapeMember(field.getDataType(), notNull))
                    .append(notNull && field.isNotNull() ? " NOT NULL" : "").append(',');
            }
            return shape.append(')').toString();
        }
        if (type instanceof MapType) {
            return "MAP(" + shapeMember(((MapType) type).getKeyType(), notNull) + ","
                + shapeMember(((MapType) type).getValueType(), notNull)
                + (notNull ? StructuredTypes.notNullMark(type) : "") + ")";
        }
        if (type instanceof StructuredArrayType) {
            return "ARRAY(" + shapeMember(((StructuredArrayType) type).getElementType(), notNull)
                + (notNull ? StructuredTypes.notNullMark(type) : "") + ")";
        }
        return "";
    }

    /** A member of a shape: its own shape when it has one, else its family. */
    private static String shapeMember(final DataType type, final boolean notNull) {
        final String shape = shapeOf(type, notNull);
        return shape.isEmpty() ? signatureFamily(type) : shape;
    }

    /** The length of a text parameter declared without one, as a written signature names it. */
    private static final int UNSIZED_TEXT_LENGTH = 134217728;

    /**
     * Whether a written type's length, precision and scale are the parameter's own, or were not written at all
     * (see {@link WrittenSignatureType}).
     */
    private static boolean namesDeclaredWidth(final Parameter parameter, final DataType written) {
        final DataType declared = parameter.getDataType();
        if (written instanceof StringType && declared instanceof StringType) {
            return WrittenSignatureType.isBare(written) || ((StringType) written).getMaxLength()
                == (parameter.isUnsized() ? UNSIZED_TEXT_LENGTH : ((StringType) declared).getMaxLength());
        }
        if (written instanceof BinaryType && declared instanceof BinaryType) {
            return WrittenSignatureType.isBare(written) || ((BinaryType) written).getMaxLength()
                == (parameter.isUnsized() ? BinaryType.NOMINAL_MAXIMUM : ((BinaryType) declared).getMaxLength());
        }
        if (written instanceof NumericType && declared instanceof NumericType
                && "NUMBER".equals(signatureFamily(written))) {
            final NumericType asWritten = (NumericType) written;
            final NumericType asDeclared = (NumericType) declared;
            return asWritten.getPrecision() == NumericType.NUMBER.getPrecision() && asWritten.getScale() == 0
                || asWritten.getPrecision() == asDeclared.getPrecision()
                    && asWritten.getScale() == asDeclared.getScale();
        }
        if (written instanceof DateTimeType && declared instanceof DateTimeType) {
            return WrittenSignatureType.isBare(written)
                || ((DateTimeType) written).getPrecision() == ((DateTimeType) declared).getPrecision();
        }
        return true;
    }

    /**
     * The CANONICAL type family a routine's signature is stored under: Snowflake normalizes a
     * parameter's declared alias away, so a DROP / DESCRIBE names the FAMILY. Live-verified on a real
     * account, each spelling re-creating the routine between attempts: an
     * {@code x INTEGER} parameter is dropped by INTEGER, INT, BIGINT and NUMBER alike; a
     * {@code x DECIMAL(10,2)} parameter by DECIMAL and by NUMBER; a {@code x DOUBLE} parameter by
     * DOUBLE; and a {@code x CHAR(5)} parameter by STRING and by VARCHAR. Two overloads are the same
     * signature by family alone, whatever their lengths: {@code f(CHAR(3))} beside {@code f(CHAR(2))}
     * already exists.
     */
    private static String signatureFamily(final DataType type) {
        // A structured OBJECT or ARRAY is another signature than the plain type of its name, as CREATE already
        // counts it (f(x OBJECT) and f(x OBJECT(a INT)) are two overloads), so DROP, DESCRIBE, GET_DDL and
        // OR REPLACE must not take one for the other. A MAP has a name of its own.
        if (type instanceof StructuredObjectType) {
            return "STRUCTURED OBJECT";
        }
        if (type instanceof StructuredArrayType) {
            return "STRUCTURED ARRAY";
        }
        return signatureFamily(type.getName());
    }

    /**
     * {@link #signatureFamily(DataType)} for a type written by name, as a GRANT on a routine writes its argument
     * types. A bare TIMESTAMP names the default TIMESTAMP_NTZ there, and an OBJECT or ARRAY written with a shape names
     * the structured type, as the declared parameter's own family does.
     *
     * @param typeName the type's name; any parameters after it are ignored but for an OBJECT's or ARRAY's shape
     * @return the family
     */
    public static String signatureFamily(final String typeName) {
        final int open = typeName.indexOf('(');
        final String name = (open >= 0 ? typeName.substring(0, open) : typeName).trim().toUpperCase(Locale.ROOT);
        if (open >= 0 && ("OBJECT".equals(name) || "ARRAY".equals(name))
                && !typeName.substring(open + 1).replace(")", "").trim().isEmpty()) {
            return "STRUCTURED " + name;
        }
        if ("TIMESTAMP".equals(name)) {
            return "TIMESTAMP_NTZ";
        }
        switch (name) {
            case "CHAR": case "CHARACTER": case "NCHAR": case "NVARCHAR": case "NVARCHAR2":
            case "VARCHAR2":
            case "CHAR VARYING": case "STRING": case "TEXT":
                return "VARCHAR";
            case "DECIMAL": case "NUMERIC": case "INT": case "INTEGER": case "BIGINT":
            case "SMALLINT": case "TINYINT": case "BYTEINT":
                return "NUMBER";
            case "DOUBLE": case "DOUBLE PRECISION": case "REAL": case "FLOAT4": case "FLOAT8":
                return "FLOAT";
            case "VARBINARY":
                return "BINARY";
            case "DATETIME":
                return "TIMESTAMP_NTZ";
            default:
                return name;
        }
    }

    /**
     * A routine's signature as its argument types' families, {@code (NUMBER, VARCHAR)}: what tells its overloads
     * apart, and what privileges on one overload are recorded under (GrantNames).
     *
     * @param parameters the routine's parameters
     * @return the signature
     */
    public static String signatureText(final List<Parameter> parameters) {
        final StringBuilder text = new StringBuilder("(");
        for (int i = 0; i < parameters.size(); i++) {
            // A parameter with a default is marked, as SHOW GRANTS names it: D(NUMBER, DEFAULT VARCHAR)
            // (live-verified).
            text.append(i > 0 ? ", " : "").append(parameters.get(i).hasDefault() ? "DEFAULT " : "")
                .append(signatureFamily(parameters.get(i).getDataType()));
        }
        return text.append(')').toString();
    }

    private String formatParameters(final List<Parameter> params) {
        if (params.isEmpty()) {
            return "()";
        }

        final StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(params.get(i).getDataType());
        }
        sb.append(")");
        return sb.toString();
    }

    // Streams
    public void addStream(final Stream stream) {
        rejectNameHeldByOtherKind(stream.getName(), RelationKind.STREAM, false);
        final String upperName = stream.getName();
        if (streams.containsKey(upperName)) {
            throw new NameTakenException(SqlCompilationError.of("Object '"
                + SqlIdentifiers.spellCanonical(stream.getName()) + "' already exists."));
        }
        streams.put(upperName, stream);
    }

    public void dropStream(final String name) {
        if (!streams.containsKey(keyFor(streams, name))) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Stream", qualified(name)));
        }
        streams.remove(keyFor(streams, name));
    }

    public Stream getStream(final String name) {
        final Stream stream = streams.get(keyFor(streams, name));
        if (stream == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Stream", qualified(name)));
        }
        return stream;
    }

    public List<Stream> getStreams() {
        return new ArrayList<>(streams.values());
    }

    /**
     * Whether a stream of EXACTLY this name is here - the check a replace makes, where a name that differs
     * only in case is a different stream.
     */
    public boolean hasStreamExact(final String name) {
        return streams.containsKey(name);
    }

    // Tasks
    public void addTask(final Task task) {
        final String upperName = task.getName();
        if (tasks.containsKey(upperName)) {
            throw new NameTakenException(SqlCompilationError.of("Object '"
                + SqlIdentifiers.spellCanonical(task.getName()) + "' already exists."));
        }
        tasks.put(upperName, task);
    }

    public void dropTask(final String name) {
        if (!tasks.containsKey(keyFor(tasks, name))) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Task", qualified(name)));
        }
        tasks.remove(keyFor(tasks, name));
    }

    public boolean hasTask(final String name) {
        return tasks.containsKey(keyFor(tasks, name));
    }

    public Task getTask(final String name) {
        final Task task = tasks.get(keyFor(tasks, name));
        if (task == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Task", qualified(name)));
        }
        return task;
    }

    public List<Task> getTasks() {
        return new ArrayList<>(tasks.values());
    }

    // Alerts
    /** Adds an alert; a name already taken by an alert is refused. */
    public void addAlert(final Alert alert) {
        if (alerts.containsKey(alert.getName())) {
            throw new NameTakenException(SqlCompilationError.of("Object '"
                + SqlIdentifiers.spellCanonical(alert.getName()) + "' already exists."));
        }
        alerts.put(alert.getName(), alert);
    }

    /** Drops an alert, refused in the does-not-exist family when there is none of that name. */
    public void dropAlert(final String name) {
        if (alerts.remove(keyFor(alerts, name)) == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Alert", qualified(name)));
        }
    }

    /** Whether an alert of that name is here. */
    public boolean hasAlert(final String name) {
        return alerts.containsKey(keyFor(alerts, name));
    }

    /** The alert of that name, refused in the does-not-exist family when there is none. */
    public Alert getAlert(final String name) {
        final Alert alert = alerts.get(keyFor(alerts, name));
        if (alert == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Alert", qualified(name)));
        }
        return alert;
    }

    /** Every alert of the schema. */
    public List<Alert> getAlerts() {
        return new ArrayList<>(alerts.values());
    }

    // Pipes
    public void addPipe(final Pipe pipe) {
        final String upperName = pipe.getName();
        if (pipes.containsKey(upperName)) {
            throw new NameTakenException(SqlCompilationError.of("Object '"
                + SqlIdentifiers.spellCanonical(pipe.getName()) + "' already exists."));
        }
        pipes.put(upperName, pipe);
    }

    public void dropPipe(final String name) {
        final String upperName = keyFor(pipes, name);
        if (!pipes.containsKey(upperName)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Pipe", qualified(name)));
        }
        pipes.remove(upperName);
    }

    public Pipe getPipe(final String name) {
        final Pipe pipe = pipes.get(keyFor(pipes, name));
        if (pipe == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Pipe", qualified(name)));
        }
        return pipe;
    }

    public List<Pipe> getPipes() {
        return new ArrayList<>(pipes.values());
    }

    // Sequences
    public void addSequence(final Sequence sequence) {
        final String upperName = sequence.getName();
        if (sequences.containsKey(upperName)) {
            throw new NameTakenException(SqlCompilationError.of("Object '"
                + SqlIdentifiers.spellCanonical(sequence.getName()) + "' already exists."));
        }
        sequences.put(upperName, sequence);
        sequence.setHome(this);
    }

    public void dropSequence(final String name) {
        final String upperName = keyFor(sequences, name);
        if (!sequences.containsKey(upperName)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Sequence", qualified(name)));
        }
        final Sequence dropped = sequences.remove(upperName);
        if (dropped != null && dropped.getHome() == this) {
            dropped.setHome(null);
        }
    }

    public Sequence getSequence(final String name) {
        final Sequence sequence = sequences.get(keyFor(sequences, name));
        if (sequence == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Sequence", qualified(name)));
        }
        return sequence;
    }

    public List<Sequence> getSequences() {
        return new ArrayList<>(sequences.values());
    }

    /** Whether a sequence carries exactly this canonical name. */
    public boolean hasSequenceExact(final String name) {
        return sequences.containsKey(name);
    }

    /**
     * The sequence of exactly this canonical name, refused as missing when only another case of it exists.
     *
     * @param name the canonical name
     * @return the sequence
     */
    public Sequence getSequenceExact(final String name) {
        if (!sequences.containsKey(name)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Sequence", qualified(name)));
        }
        return sequences.get(name);
    }

    /**
     * Drops the sequence of exactly this canonical name, refused as missing when only another case of it exists.
     *
     * @param name the canonical name
     */
    public void dropSequenceExact(final String name) {
        if (!sequences.containsKey(name)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Sequence", qualified(name)));
        }
        dropSequence(name);
    }

    // File formats
    public void addFileFormat(final FileFormat fileFormat) {
        final FileFormat held = fileFormats.get(fileFormat.getName());
        if (held != null && othersTemporary(held)) {
            // Another session's temporary format holds the name; one session's never replaces another's.
            throw new NameTakenException(SqlCompilationError.of(
                "Object '" + SqlIdentifiers.spellCanonical(fileFormat.getName()) + "' already exists."));
        }
        fileFormats.put(fileFormat.getName(), fileFormat);
    }

    /** Whether a file format is another session's temporary one, which a statement neither reaches nor lists. */
    private static boolean othersTemporary(final FileFormat format) {
        final Long current = StatementSession.current();
        return format.isTemporary() && current != null && format.getCreatedInSession() != null
            && !current.equals(format.getCreatedInSession());
    }

    public void dropFileFormat(final String name) {
        final String upperName = keyFor(fileFormats, name);
        if (!fileFormats.containsKey(upperName)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("File format", qualified(name)));
        }
        fileFormats.remove(upperName);
    }

    public FileFormat getFileFormat(final String name) {
        final FileFormat format = fileFormats.get(keyFor(fileFormats, name));
        return format == null || othersTemporary(format) ? null : format;
    }

    public boolean hasFileFormat(final String name) {
        return getFileFormat(name) != null;
    }

    /** Whether a file format carries exactly this canonical name; one differing only in case is another. */
    public boolean hasFileFormatExact(final String name) {
        final FileFormat format = fileFormats.get(name);
        return format != null && !othersTemporary(format);
    }

    /**
     * Drops the file format of exactly this canonical name, refused as missing when only another case of it exists.
     *
     * @param name the canonical name
     */
    public void dropFileFormatExact(final String name) {
        if (!hasFileFormatExact(name)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("File format", qualified(name)));
        }
        dropFileFormat(name);
    }

    /** The file formats a statement here lists: every one but another session's temporary ones. */
    public List<FileFormat> getFileFormats() {
        final List<FileFormat> reached = new ArrayList<>();
        for (final FileFormat format : fileFormats.values()) {
            if (!othersTemporary(format)) {
                reached.add(format);
            }
        }
        return reached;
    }

    /** Every file format held here, every session's temporary ones included: the engine's bookkeeping reads these. */
    public List<FileFormat> allFileFormats() {
        return new ArrayList<>(fileFormats.values());
    }

    /**
     * Create a deep clone of this schema
     * Note: Table data cloning must be handled separately at the storage engine level
     */
    public Schema clone() {
        final Schema clonedSchema = new Schema(this.getName());
        clonedSchema.setComment(this.getComment());
        clonedSchema.getParameters().copyFrom(parameters);
        clonedSchema.setManagedAccess(managedAccess);
        clonedSchema.setTransientObject(transientObject);
        clonedSchema.setDataRetentionTimeInDays(dataRetentionTimeInDays);
        copyTags(this, clonedSchema);

        // Clone tables (structure only, not data). A temporary table is not cloned, and the permanent
        // table it hides is. Each is a full copy of its definition (Table.copyAs) and keeps its owner, as
        // the children of a cloned container keep their grants. A clone suspends automatic clustering
        // (documented).
        for (final Table table : getNonTemporaryTables()) {
            final Table clonedTable = table.copyAs(table.getName(), false, table.isTransient());
            clonedTable.setOwner(table.getOwner());
            clonedTable.setLastDdlBy(table.getLastDdlBy());
            if (!clonedTable.getClusterKeys().isEmpty()) {
                clonedTable.setReclusterSuspended(true);
            }
            clonedSchema.register(clonedTable);
        }

        // Clone views — INFORMATION_SCHEMA's system views are seeded in the target, never copied;
        // every view of an ordinary schema (whatever its name) is cloned.
        if (!"INFORMATION_SCHEMA".equals(getName())) {
            for (final View view : views.values()) {
                if (view.isTemporary()) {
                    // A temporary object belongs to its session, so a clone leaves it out, as it leaves out a
                    // temporary table.
                    continue;
                }
                final View clonedView = view.copy();
                clonedView.setOwner(view.getOwner());
                copyTags(view, clonedView);
                clonedSchema.views.put(view.getName(), clonedView);
            }
        }

        // Tags are cloned with their schema, and what is cloned with them is mapped to the clones afterwards
        // (TagAssignments.mapToClones), so an assignment in the clone reads the clone's tag (documented).
        for (final Tag tag : tags.values()) {
            final Tag clonedTag = new Tag(tag.getName(), tag.getAllowedValues(), tag.getComment());
            clonedTag.markAllowedValuesListed(tag.hasAllowedValues());
            clonedTag.setClonedFromId(tag.getId());
            clonedTag.setPropagate(tag.getPropagate());
            clonedTag.setOnConflict(tag.getOnConflict());
            clonedTag.setOwner(tag.getOwner());
            copyTags(tag, clonedTag);
            clonedSchema.tags.put(tag.getName(), clonedTag);
        }

        // Dynamic tables, as CREATE DYNAMIC TABLE ... CLONE makes one: the definition and settings, the
        // clone suspended, its reclustering too. Their rows are their query's answer, so there is nothing
        // else to copy.
        for (final DynamicTable dynamicTable : dynamicTables.values()) {
            final DynamicTable clonedDynamic = dynamicTable.copyAs(dynamicTable.getName());
            clonedDynamic.setClone(true);
            clonedDynamic.setState(State.SUSPENDED);
            clonedDynamic.setSchedulingState("SUSPENDED");
            clonedDynamic.setLastSuspendedOn(StatementClock.instant());
            clonedDynamic.setReclusterSuspended(true);
            clonedDynamic.setOwner(dynamicTable.getOwner());
            copyTags(dynamicTable, clonedDynamic);
            clonedSchema.dynamicTables.put(dynamicTable.getName(), clonedDynamic);
        }

        // Materialized views, which read what their definition names, as a cloned view does.
        for (final MaterializedView view : materializedViews.values()) {
            final MaterializedView clonedView = view.copy();
            clonedView.setOwner(view.getOwner());
            copyTags(view, clonedView);
            clonedSchema.materializedViews.put(view.getName(), clonedView);
        }

        // Secrets, network rules and password policies live in the schema and are cloned with it.
        for (final SecurityObjectKind kind : SecurityObjectKind.values()) {
            for (final SecurityObject object : securityObjects.list(kind)) {
                clonedSchema.securityObjects.add(object.copy());
            }
        }

        // Clone procedures — preserve LANGUAGE/handler/runtime/packages (and EXECUTE AS/imports). The short
        // constructor defaults the language to SQL, which turned a cloned JavaScript/Python/Java procedure
        // into a SQL one whose body then failed to parse at CALL time.
        for (final Map.Entry<String, List<Procedure>> entry : procedures.entrySet()) {
            final List<Procedure> clonedOverloads = new ArrayList<>();
            for (final Procedure proc : entry.getValue()) {
                if (proc.isTemporary()) {
                    continue;   // a temporary procedure stays with its session (see the views above)
                }
                final Procedure clonedProc = new Procedure(
                    proc.getName(),
                    proc.getParameters(),
                    proc.getReturnType(),
                    proc.getBody(),
                    proc.getLanguage(),
                    proc.getHandler(),
                    proc.getRuntimeVersion(),
                    proc.getPackages()
                );
                clonedProc.setComment(proc.getComment());
                clonedProc.setReturnSized(proc.getReturnSized());
                clonedProc.setReturnNotNull(proc.isReturnNotNull());
                clonedProc.setImports(proc.getImports());
                clonedProc.setEnvironment(proc.getEnvironment().copy());
                clonedProc.setReturnsTable(proc.returnsTable());
                clonedProc.setReturnColumns(proc.getReturnColumns());
                clonedProc.setExecuteAs(proc.getExecuteAs());
                clonedProc.setNullHandling(proc.getNullHandling());
                clonedProc.setVolatility(proc.getVolatility());
                clonedProc.setOwner(proc.getOwner());
                copyTags(proc, clonedProc);
                clonedOverloads.add(clonedProc);
            }
            if (!clonedOverloads.isEmpty()) {
                clonedSchema.procedures.put(entry.getKey(), clonedOverloads);
            }
        }

        // Clone functions — same story: keep LANGUAGE/handler/runtime plus the RETURNS TABLE columns and the
        // null-handling / volatility / secure / imports attributes, else a cloned Java (or JS/Python) UDF is
        // treated as a SQL UDF and its body fails to evaluate when called.
        for (final Map.Entry<String, List<Function>> entry : functions.entrySet()) {
            final List<Function> clonedOverloads = new ArrayList<>();
            for (final Function func : entry.getValue()) {
                if (func.isTemporary()) {
                    continue;   // a temporary function stays with its session (see the views above)
                }
                final Function clonedFunc = new Function(
                    func.getName(),
                    func.getParameters(),
                    func.getReturnType(),
                    func.getReturnColumns(),
                    func.getBody(),
                    func.isTableFunction(),
                    func.getLanguage(),
                    func.getHandler(),
                    func.getRuntimeVersion()
                );
                clonedFunc.setComment(func.getComment());
                clonedFunc.setReturnSized(func.getReturnSized());
                clonedFunc.setReturnNotNull(func.isReturnNotNull());
                clonedFunc.setNullHandling(func.getNullHandling());
                clonedFunc.setVolatility(func.getVolatility());
                clonedFunc.setSecure(func.isSecure());
                clonedFunc.setImports(func.getImports());
                clonedFunc.setEnvironment(func.getEnvironment().copy());
                clonedFunc.setHome(clonedSchema);
                clonedFunc.setMemoizable(func.isMemoizable());
                if (func.isServiceFunction()) {
                    clonedFunc.setService(func.getServiceName(), func.getServiceEndpoint(), func.getMaxBatchRows());
                }
                if (func.isExternalFunction()) {
                    clonedFunc.setExternal(func.getApiIntegration(), func.getExternalUrl());
                }
                clonedFunc.setAggregate(func.isAggregate());
                clonedFunc.setOwner(func.getOwner());
                copyTags(func, clonedFunc);
                clonedOverloads.add(clonedFunc);
            }
            if (!clonedOverloads.isEmpty()) {
                clonedSchema.functions.put(entry.getKey(), clonedOverloads);
            }
        }

        // Clone streams — preserve the source TYPE (TABLE vs VIEW), showInitialRows, the resolved base
        // tables (for a stream ON VIEW), owner and comment. The old 3-arg constructor dropped all of these
        // and defaulted sourceType to TABLE, so a cloned stream ON a VIEW failed to read in the clone
        // ("Table does not exist: <view>") because the read path resolved its source as a table.
        for (final Stream stream : streams.values()) {
            final Stream clonedStream = new Stream(
                stream.getName(),
                stream.getSourceTableName(),
                stream.getSourceType(),
                stream.getStreamType(),
                stream.isShowInitialRows()
            );
            clonedStream.setBaseTableNames(stream.getBaseTableNames());
            clonedStream.setOwner(stream.getOwner());
            clonedStream.setComment(stream.getComment());
            copyTags(stream, clonedStream);
            clonedSchema.streams.put(stream.getName(), clonedStream);
        }

        // Clone tasks: the whole definition, AFTER and WHEN included, and suspended (documented). A task
        // graph lives in one schema and names its tasks bare, so the clone's graph links the clone's tasks.
        for (final Task task : tasks.values()) {
            clonedSchema.tasks.put(task.getName(), task.cloneDefinition());
        }
        // Clone alerts: a cloned alert keeps its definition and starts suspended.
        for (final Alert alert : alerts.values()) {
            clonedSchema.alerts.put(alert.getName(), alert.copy(alert.getName()));
        }

        // Clone pipes. A pipe that loads from an internal stage is not cloned, and a cloned one does not run
        // until it is resumed: paused, or STOPPED_CLONED for an auto-ingest pipe (documented).
        for (final Pipe pipe : pipes.values()) {
            if (readsInternalStage(pipe)) {
                continue;
            }
            final Pipe clonedPipe = new Pipe(
                pipe.getName(),
                pipe.getCopyStatement(),
                pipe.isAutoIngest(),
                pipe.getNotificationChannel()
            );
            clonedPipe.setComment(pipe.getComment());
            clonedPipe.setPaused(true);
            clonedPipe.setStoppedCloned(pipe.isAutoIngest());
            clonedPipe.setIntegration(pipe.getIntegration());
            clonedPipe.setOwner(pipe.getOwner());
            copyTags(pipe, clonedPipe);
            if (pipe.getErrorIntegration() != null) {
                clonedPipe.setErrorIntegration(pipe.getErrorIntegration());
            }
            if (pipe.getAwsSnsTopicArn() != null) {
                clonedPipe.setAwsSnsTopicArn(pipe.getAwsSnsTopicArn());
            }
            clonedPipe.setLastLoadedFileCount(pipe.getLastLoadedFileCount());
            if (pipe.getLastLoadedTime() != null) {
                clonedPipe.setLastLoadedTime(pipe.getLastLoadedTime());
            }
            clonedSchema.pipes.put(pipe.getName(), clonedPipe);
        }

        // Clone sequences
        for (final Sequence sequence : sequences.values()) {
            final Sequence clonedSequence = new Sequence(
                sequence.getName(),
                sequence.getStartValue(),
                sequence.getIncrement(),
                sequence.isOrder(),
                sequence.getComment()
            );
            // Preserve current value (use raw to avoid CURRVAL check)
            clonedSequence.setCurrentValue(sequence.getCurrentValueRaw());
            clonedSequence.setOwner(sequence.getOwner());
            clonedSchema.sequences.put(sequence.getName(), clonedSequence);
            clonedSequence.setHome(clonedSchema);
        }
        // A cloned table that drew from one of this schema's sequences draws from that sequence's clone.
        clonedSchema.rebindSequenceDefaults(this, clonedSchema);

        // Clone the policy OBJECTS too — a policy attached to a cloned table/view must still resolve
        // inside the clone (fresh instances: a later rename of the original must not affect the clone).
        for (final Contact contact : contacts.values()) {
            final Contact clonedContact = new Contact(contact.getName());
            clonedContact.setComment(contact.getComment());
            clonedContact.setUrl(contact.getUrl());
            clonedContact.setEmailDistributionList(contact.getEmailDistributionList());
            clonedSchema.contacts.put(contact.getName(), clonedContact);
        }
        // Each policy keeps its comment, owner and tags.
        for (final ProjectionPolicy policy : projectionPolicies.values()) {
            clonedSchema.projectionPolicies.put(policy.getName().toUpperCase(Locale.ROOT),
                withPolicyDetails(policy, new ProjectionPolicy(policy.getName(), policy.getBody())));
        }
        for (final AggregationPolicy policy : aggregationPolicies.values()) {
            clonedSchema.aggregationPolicies.put(policy.getName().toUpperCase(Locale.ROOT),
                withPolicyDetails(policy, new AggregationPolicy(policy.getName(), policy.getBody())));
        }
        for (final JoinPolicy policy : joinPolicies.values()) {
            clonedSchema.joinPolicies.put(policy.getName().toUpperCase(Locale.ROOT),
                withPolicyDetails(policy, new JoinPolicy(policy.getName(), policy.getBody())));
        }
        for (final MaskingPolicy policy : maskingPolicies.values()) {
            final MaskingPolicy clonedPolicy = new MaskingPolicy(policy.getName(), policy.getParameters(),
                policy.getReturnType(), policy.getBody());
            clonedPolicy.setReturnDataType(policy.getReturnDataType());
            clonedPolicy.setReturnSpelling(policy.getReturnSpelling());
            clonedPolicy.setExemptOtherPolicies(policy.isExemptOtherPolicies());
            clonedSchema.maskingPolicies.put(policy.getName().toUpperCase(Locale.ROOT), withPolicyDetails(policy, clonedPolicy));
        }
        for (final RowAccessPolicy policy : rowAccessPolicies.values()) {
            final RowAccessPolicy clonedPolicy =
                new RowAccessPolicy(policy.getName(), policy.getParameters(), policy.getBody());
            clonedPolicy.setExemptOtherPolicies(policy.isExemptOtherPolicies());
            clonedSchema.rowAccessPolicies.put(policy.getName().toUpperCase(Locale.ROOT),
                withPolicyDetails(policy, clonedPolicy));
        }

        // Clone stages — the DEFINITION carries over (Snowflake's CLONE keeps stage definitions), its
        // file format options, copy options and directory setting included (Stage.copy). Dropping them
        // silently broke every jar-backed UDF in a cloned database ("Stage does not exist" at first
        // invocation).
        for (final Stage stage : stages.values()) {
            clonedSchema.stages.put(stage.getName(), stage.copy());
        }

        // Clone file formats; a temporary one is no part of a clone, as a temporary table is not.
        for (final FileFormat fileFormat : fileFormats.values()) {
            if (fileFormat.isTemporary()) {
                continue;
            }
            final FileFormat clonedFormat = new FileFormat(fileFormat.getName(), fileFormat.getType());
            for (final Map.Entry<String, String> option : fileFormat.getOptions().entrySet()) {
                clonedFormat.setOption(option.getKey(), option.getValue());
            }
            clonedFormat.setComment(fileFormat.getComment());
            clonedFormat.setOwner(fileFormat.getOwner());
            clonedSchema.fileFormats.put(fileFormat.getName(), clonedFormat);
        }

        return clonedSchema;
    }

    /** Copies one object's tag assignments onto its clone. */
    private static void copyTags(final Taggable source, final Taggable clone) {
        for (final Map.Entry<String, String> tag : source.getTagValues().entrySet()) {
            clone.setTag(tag.getKey(), tag.getValue());
        }
    }

    /** A cloned policy given its source's comment, owner and tags. */
    private static <T extends SqlObject> T withPolicyDetails(final SqlObject source, final T clone) {
        clone.setComment(source.getComment());
        clone.setOwner(source.getOwner());
        copyTags(source, clone);
        return clone;
    }

    /**
     * Whether a pipe loads from an internal stage, which a clone of its schema leaves behind (documented). A
     * user or table stage is internal; a named stage is looked up here when the pipe names it in this schema.
     * A stage named elsewhere is taken as the external one a cloned pipe may still load from.
     */
    private boolean readsInternalStage(final Pipe pipe) {
        final List<String> parts = pipe.stageParts();
        if (parts == null) {
            return false;
        }
        if (parts.isEmpty()) {
            return true;
        }
        final boolean here = parts.size() == 1
            || parts.get(parts.size() - 2).equalsIgnoreCase(getName())
                && (parts.size() == 2 || databaseName == null || parts.get(0).equalsIgnoreCase(databaseName));
        final Stage stage = here ? stages.get(keyFor(stages, parts.get(parts.size() - 1))) : null;
        return stage != null && stage.getType() == StageType.INTERNAL;
    }

    // Stages
    public void addStage(final Stage stage) {
        final String key = stage.getName();
        if (stages.containsKey(key)) {
            throw new NameTakenException(SqlCompilationError.of("Object '"
                + SqlIdentifiers.spellCanonical(stage.getName()) + "' already exists."));
        }
        stages.put(key, stage);
    }

    public void dropStage(final String name) {
        final String upper = keyFor(stages, name);
        if (!stages.containsKey(upper)) throw new RuntimeException(SqlCompilationError.doesNotExist("Stage", qualified(name)));
        stages.remove(upper);
    }

    public Stage getStage(final String name) {
        final Stage s = stages.get(keyFor(stages, name));
        if (s == null || othersTemporary(s, s.isTemporary())) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Stage", qualified(name)));
        }
        return s;
    }

    public boolean hasStage(final String name) {
        final Stage stage = stages.get(keyFor(stages, name));
        return stage != null && !othersTemporary(stage, stage.isTemporary());
    }

    /**
     * Whether a stage of EXACTLY this name is here - the check a create makes, where a name that differs
     * only in case is a different stage.
     */
    public boolean hasStageExact(final String name) {
        final Stage stage = stages.get(name);
        return stage != null && !othersTemporary(stage, stage.isTemporary());
    }

    /**
     * Drops the stage of exactly this canonical name, refused as missing when only another case of it exists.
     *
     * @param name the canonical name
     */
    public void dropStageExact(final String name) {
        if (!hasStageExact(name)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Stage", qualified(name)));
        }
        dropStage(name);
    }

    /** The stages a statement here lists: every one but another session's temporary ones. */
    public List<Stage> getStages() {
        final List<Stage> reached = new ArrayList<>();
        for (final Stage stage : stages.values()) {
            if (!othersTemporary(stage, stage.isTemporary())) {
                reached.add(stage);
            }
        }
        return reached;
    }

    /** Every stage held here, every session's temporary ones included: the engine's own bookkeeping reads these. */
    public List<Stage> allStages() {
        return new ArrayList<>(stages.values());
    }

    // Cortex search services
    public void addCortexSearchService(final CortexSearchService service) {
        service.setDatabaseName(databaseName);
        service.setSchemaName(getName());
        cortexSearchServices.put(service.getName(), service);
    }

    public void dropCortexSearchService(final String name) {
        final String upper = keyFor(cortexSearchServices, name);
        if (!cortexSearchServices.containsKey(upper)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Cortex Search Service", qualified(name)));
        }
        cortexSearchServices.remove(upper);
    }

    public CortexSearchService getCortexSearchService(final String name) {
        final CortexSearchService service = cortexSearchServices.get(keyFor(cortexSearchServices, name));
        if (service == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Cortex Search Service", qualified(name)));
        }
        return service;
    }

    public boolean hasCortexSearchService(final String name) {
        return cortexSearchServices.containsKey(keyFor(cortexSearchServices, name));
    }

    public List<CortexSearchService> getCortexSearchServices() {
        return new ArrayList<>(cortexSearchServices.values());
    }

    /**
     * The service of exactly that canonical name, as a statement names one: a quoted name keeps its case, so
     * {@code "svc"} and {@code SVC} are two services.
     */
    public CortexSearchService getCortexSearchServiceExact(final String name) {
        final CortexSearchService service = cortexSearchServices.get(name);
        if (service == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Cortex Search Service", qualified(name)));
        }
        return service;
    }

    /** Whether a service of exactly that canonical name exists. */
    public boolean hasCortexSearchServiceExact(final String name) {
        return cortexSearchServices.containsKey(name);
    }

    /** Drops the service of exactly that canonical name; a missing one is refused as {@link #getCortexSearchServiceExact} refuses it. */
    public void dropCortexSearchServiceExact(final String name) {
        getCortexSearchServiceExact(name);
        cortexSearchServices.remove(name);
    }

    /** The schema's notebooks and Streamlit apps, live and dropped. */
    public AppObjectStore getAppObjects() {
        return appObjects;
    }

    // Tags
    public void addTag(final Tag tag) {
        tags.put(tag.getName(), tag);
    }

    public void dropTag(final String name) {
        final String upper = keyFor(tags, name);
        if (!tags.containsKey(upper)) throw new RuntimeException(SqlCompilationError.doesNotExist("Tag", qualified(name)));
        tags.remove(upper);
    }

    public Tag getTag(final String name) {
        final Tag tag = tags.get(keyFor(tags, name));
        if (tag == null) throw new RuntimeException(SqlCompilationError.doesNotExist("Tag", qualified(name)));
        return tag;
    }

    public boolean hasTag(final String name) {
        return tags.containsKey(keyFor(tags, name));
    }

    /**
     * Whether a tag of EXACTLY this name is here - the check a create or a rename makes, where a name that
     * differs only in case is a different tag (live-verified).
     */
    public boolean hasTagExact(final String name) {
        return tags.containsKey(name);
    }

    /**
     * The tag of EXACTLY this name, the one a statement names: a quoted name is that name alone (live-verified).
     *
     * @param name the tag's canonical name
     * @return the tag
     */
    public Tag getTagExact(final String name) {
        final Tag tag = tags.get(name);
        if (tag == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Tag", qualified(name)));
        }
        return tag;
    }

    /**
     * Drops the tag of EXACTLY this name, as DROP TAG and a replacement do.
     *
     * @param name the tag's canonical name
     */
    public void dropTagExact(final String name) {
        getTagExact(name);
        tags.remove(name);
    }

    public List<Tag> getTags() {
        return new ArrayList<>(tags.values());
    }

    public void renameTag(final String oldName, final String newName) {
        final Tag tag = getTag(oldName);
        if (tags.containsKey(newName)) {
            throw new RuntimeException(SqlCompilationError.of("Object '" + SqlIdentifiers.spellCanonical(newName)
                + "' already exists."));
        }
        tags.remove(keyFor(tags, oldName));
        tag.rename(newName);
        tags.put(newName, tag);
    }

    /**
     * The kinds that share one namespace in a schema: a table, a view, a materialized view, a dynamic table
     * and a stream (live-verified). A create over a name another of them holds is refused with the holder's
     * kind, {@code Object 'KT' already exists as TABLE}, before the body is compiled and before an OR
     * REPLACE drops anything; IF NOT EXISTS does not skip it.
     *
     * <p>A TEMPORARY table is the exception: it may take the name of a view, a materialized view or a
     * dynamic table, and it then shadows it. A stream's name it may not take. A TEMPORARY view may take a
     * table's name the same way.
     *
     * @param name      the name being created
     * @param kind      the kind being created
     * @param temporary whether the create says TEMPORARY
     */
    public void rejectNameHeldByOtherKind(final String name, final RelationKind kind, final boolean temporary) {
        rejectNameHeldByOtherKind(name, kind, temporary, false);
    }

    /**
     * {@link #rejectNameHeldByOtherKind(String, RelationKind, boolean)} for a table that may be an event table:
     * a table and an event table are two kinds of the one namespace, {@code Object 'ET' already exists as
     * EVENT_TABLE} for a table created over an event table and {@code as TABLE} the other way round
     * (live-verified).
     *
     * @param name       the name being created
     * @param kind       the kind being created
     * @param temporary  whether the create says TEMPORARY
     * @param eventTable whether the table being created is an event table
     */
    public void rejectNameHeldByOtherKind(final String name, final RelationKind kind, final boolean temporary,
                                          final boolean eventTable) {
        // A PERMANENT create looks past a temporary holder of the name and is judged against the
        // permanent object underneath, naming ITS kind. A temporary create sees the temporary holder,
        // and so does a stream, whose name check reaches the session's own objects (live-verified).
        final boolean seesTemporary = temporary || kind == RelationKind.STREAM;
        for (final RelationKind holder : RelationKind.values()) {
            final boolean heldEventTable = holder == RelationKind.TABLE && tables.containsKey(name)
                && tables.get(name).isEventTable();
            if (holder == kind && heldEventTable == eventTable || !holds(holder, name, seesTemporary)
                    || yieldsTo(holder, kind, temporary, holderIsTemporary(holder, name))) {
                continue;
            }
            throw new RuntimeException(SqlCompilationError.of(
                "Object '" + name + "' already exists as " + (heldEventTable ? "EVENT_TABLE" : holder.spelling())));
        }
    }

    /**
     * The kind of the policy that holds a name, as a refusal spells it, or null when no policy does: the
     * policies of a schema — masking, row access, join, aggregation, projection, password and session policies —
     * share one namespace (live-verified).
     *
     * @param name the policy's name
     * @return the holder's kind, e.g. {@code MASKING_POLICY}, or null
     */
    public String policyKindHolding(final String name) {
        if (hasMaskingPolicy(name)) {
            return "MASKING_POLICY";
        }
        if (hasRowAccessPolicy(name)) {
            return "ROW_ACCESS_POLICY";
        }
        if (hasJoinPolicy(name)) {
            return "JOIN_POLICY";
        }
        if (hasAggregationPolicy(name)) {
            return "AGGREGATION_POLICY";
        }
        if (hasProjectionPolicy(name)) {
            return "PROJECTION_POLICY";
        }
        if (getSecurityObjects().get(SecurityObjectKind.PASSWORD_POLICY, name) != null) {
            return "PASSWORD_POLICY";
        }
        return getSecurityObjects().get(SecurityObjectKind.SESSION_POLICY, name) != null ? "SESSION_POLICY" : null;
    }

    /**
     * The kind holding that name, or null when none of the five does. A temporary object counts: a DROP
     * reaches the session's own objects as a read does.
     */
    public RelationKind relationKindOf(final String name) {
        for (final RelationKind holder : RelationKind.values()) {
            if (holds(holder, name, true)) {
                return holder;
            }
        }
        return null;
    }

    /** Whether that kind holds the name here, a temporary object of it included. */
    public boolean holdsRelation(final String name, final RelationKind kind) {
        return holds(kind, name, true);
    }

    /** Whether a TEMPORARY table of this name is here, shadowing whatever else holds the name. */
    public boolean hasTemporaryTable(final String name) {
        if (holdsOwnTable(name)) {
            return true;
        }
        final Table table = tables.get(name);
        return table != null && table.isTemporary();
    }

    /** Whether a kind holds that name in this schema, counting a temporary object only when asked. */
    private boolean holds(final RelationKind kind, final String name, final boolean seesTemporary) {
        switch (kind) {
            case TABLE:
                return seesTemporary && holdsOwnTable(name)
                    || tables.containsKey(name) && (seesTemporary || !tables.get(name).isTemporary());
            case VIEW:
                return seesTemporary && ownViews() != null && ownViews().containsKey(name)
                    || views.containsKey(name) && (seesTemporary || !views.get(name).isTemporary());
            case MATERIALIZED_VIEW:
                return materializedViews.containsKey(name);
            case DYNAMIC_TABLE:
                return dynamicTables.containsKey(name);
            default:
                return streams.containsKey(name);
        }
    }

    /** Whether the object holding the name is itself a temporary one. */
    private boolean holderIsTemporary(final RelationKind holder, final String name) {
        if (holder == RelationKind.TABLE) {
            return holdsOwnTable(name) || tables.containsKey(name) && tables.get(name).isTemporary();
        }
        return holder == RelationKind.VIEW && (ownViews() != null && ownViews().containsKey(name)
            || views.containsKey(name) && views.get(name).isTemporary());
    }

    /**
     * Whether the holder gives its name up to a TEMPORARY create, as the account lets it. Only a
     * PERMANENT object gives way: a temporary view over a temporary table of that name is refused,
     * where the same view over a permanent table is created (live-verified).
     */
    private static boolean yieldsTo(final RelationKind holder, final RelationKind kind, final boolean temporary,
                                    final boolean holderTemporary) {
        if (!temporary || holderTemporary) {
            return false;
        }
        if (kind == RelationKind.TABLE) {
            return holder == RelationKind.VIEW || holder == RelationKind.MATERIALIZED_VIEW
                || holder == RelationKind.DYNAMIC_TABLE;
        }
        return kind == RelationKind.VIEW && holder == RelationKind.TABLE;
    }

    @Override
    public String getObjectType() {
        return "SCHEMA";
    }

    /** The key a map holds that name under, exact first; see {@link NameKeys#keyFor}. */
    private static String keyFor(final Map<String, ?> map, final String name) {
        return NameKeys.keyFor(map, name);
    }

    /** The Snowpark Container Services objects and artifact repositories this schema holds. */
    public ContainerObjects getContainerObjects() {
        return containerObjects;
    }
}
