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
import dev.frostlake.types.DataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final NamedObjects<MaterializedView> materializedViews = new NamedObjects<>(this, "Materialized view");
    private final NamedObjects<DynamicTable> dynamicTables = new NamedObjects<>(this, "Dynamic table");
    private final OverloadStore<Procedure> procedures = new OverloadStore<>(this, "Procedure");
    private final OverloadStore<Function> functions = new OverloadStore<>(this, "Function");
    private final NamedObjects<Stream> streams = new NamedObjects<>(this, "Stream");
    private final NamedObjects<Task> tasks = new NamedObjects<>(this, "Task");
    private final NamedObjects<Alert> alerts = new NamedObjects<>(this, "Alert");
    private final NamedObjects<Pipe> pipes = new NamedObjects<>(this, "Pipe");
    private final NamedObjects<Sequence> sequences = new NamedObjects<>(this, "Sequence");
    private final NamedObjects<Contact> contacts = new NamedObjects<>(this, "Contact");
    private final NamedObjects<MaskingPolicy> maskingPolicies = new NamedObjects<>(this, "Masking policy");
    private final NamedObjects<ProjectionPolicy> projectionPolicies = new NamedObjects<>(this, "Projection policy");
    private final NamedObjects<AggregationPolicy> aggregationPolicies =
        new NamedObjects<>(this, "Aggregation policy");
    private final NamedObjects<JoinPolicy> joinPolicies = new NamedObjects<>(this, "Join policy");
    private final NamedObjects<RowAccessPolicy> rowAccessPolicies = new NamedObjects<>(this, "Row access policy");
    private final NamedObjects<Tag> tags = new NamedObjects<>(this, "Tag");
    private final NamedObjects<Stage> stages = new NamedObjects<>(this, "Stage");
    private final NamedObjects<CortexSearchService> cortexSearchServices =
        new NamedObjects<>(this, "Cortex Search Service");
    private final AppObjectStore appObjects = new AppObjectStore();
    private final NamedObjects<FileFormat> fileFormats = new NamedObjects<>(this, "File format");
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
        return SessionScoped.othersTemporary(table) && !shadowedTables.containsKey(table.getName());
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
        if (view == null || SessionScoped.othersTemporary(view)) {
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
        return view != null && !SessionScoped.othersTemporary(view);
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
        return view != null && !SessionScoped.othersTemporary(view);
    }

    /**
     * The view of exactly this canonical name, as SQL names one: a name that differs only in case is another view,
     * which is not here (live-verified).
     *
     * @param name the canonical name
     * @return the view
     */
    public View getViewExact(final String name) {
        if (!hasViewExact(name)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("View", qualified(name)));
        }
        return getView(name);
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
            if (!SessionScoped.othersTemporary(view)) {
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
        materializedViews.add(materializedView);
    }

    public void dropMaterializedView(final String name) {
        materializedViews.drop(name);
    }

    public MaterializedView getMaterializedView(final String name) {
        return materializedViews.get(name);
    }

    public boolean hasMaterializedView(final String name) {
        return materializedViews.has(name);
    }

    public List<MaterializedView> getMaterializedViews() {
        return materializedViews.values();
    }

    // Dynamic Tables
    public void addDynamicTable(final DynamicTable dt) {
        rejectNameHeldByOtherKind(dt.getName(), RelationKind.DYNAMIC_TABLE, false);
        dynamicTables.add(dt);
    }

    /*
     * A dynamic table is found by its canonical name EXACTLY, as a SQL reference finds a table (tableExact): one
     * created as "dt" and one created as DT are two dynamic tables.
     */
    public void dropDynamicTable(final String name) {
        dynamicTables.dropExact(name);
    }

    public DynamicTable getDynamicTable(final String name) {
        return dynamicTables.getExact(name);
    }

    public boolean hasDynamicTable(final String name) {
        return dynamicTables.hasExact(name);
    }

    public List<DynamicTable> getDynamicTables() {
        return dynamicTables.values();
    }

    // Contacts
    public void addContact(final Contact contact) {
        contacts.put(contact);
    }
    public void dropContact(final String name) { contacts.remove(name); }
    public Contact getContact(final String name) { return contacts.find(name); }
    public boolean hasContact(final String name) { return contacts.has(name); }
    public List<Contact> getContacts() { return contacts.values(); }

    private final SecurityObjectStore securityObjects = new SecurityObjectStore();

    /** The schema's network rules, password policies and secrets. */
    public SecurityObjectStore getSecurityObjects() {
        return securityObjects;
    }

    // Join Policies
    public void addJoinPolicy(final JoinPolicy policy) {
        joinPolicies.put(policy);
    }
    public void dropJoinPolicy(final String name) { joinPolicies.remove(name); }
    public JoinPolicy getJoinPolicy(final String name) { return joinPolicies.find(name); }
    public boolean hasJoinPolicy(final String name) { return joinPolicies.has(name); }
    public List<JoinPolicy> getJoinPolicies() { return joinPolicies.values(); }
    public void renameJoinPolicy(final String oldName, final String newName) {
        NamedObjects.rename(joinPolicies, oldName, newName.toUpperCase(Locale.ROOT));
    }

    // Aggregation Policies
    public void addAggregationPolicy(final AggregationPolicy policy) {
        aggregationPolicies.put(policy);
    }
    public void dropAggregationPolicy(final String name) { aggregationPolicies.remove(name); }
    public AggregationPolicy getAggregationPolicy(final String name) { return aggregationPolicies.find(name); }
    public boolean hasAggregationPolicy(final String name) { return aggregationPolicies.has(name); }
    public List<AggregationPolicy> getAggregationPolicies() { return aggregationPolicies.values(); }
    /** The aggregation policy of exactly this canonical name, or null: one differing only in case is another. */
    public AggregationPolicy getAggregationPolicyExact(final String name) {
        return aggregationPolicies.findExact(name);
    }
    /** Renames the policy, the new name kept as written: a quoted name keeps its case (live-verified). */
    public void renameAggregationPolicy(final String oldName, final String newName) {
        NamedObjects.rename(aggregationPolicies, oldName, newName);
    }

    // Projection Policies
    public void addProjectionPolicy(final ProjectionPolicy policy) {
        projectionPolicies.put(policy);
    }
    public void dropProjectionPolicy(final String name) { projectionPolicies.remove(name); }
    public ProjectionPolicy getProjectionPolicy(final String name) { return projectionPolicies.find(name); }
    public boolean hasProjectionPolicy(final String name) { return projectionPolicies.has(name); }
    public List<ProjectionPolicy> getProjectionPolicies() { return projectionPolicies.values(); }
    /** The projection policy of exactly this canonical name, or null: one differing only in case is another. */
    public ProjectionPolicy getProjectionPolicyExact(final String name) { return projectionPolicies.findExact(name); }
    /** Renames the policy, the new name kept as written: a quoted name keeps its case (live-verified). */
    public void renameProjectionPolicy(final String oldName, final String newName) {
        NamedObjects.rename(projectionPolicies, oldName, newName);
    }

    // Masking Policies
    public void addMaskingPolicy(final MaskingPolicy policy) {
        maskingPolicies.put(policy);
    }
    public void dropMaskingPolicy(final String name) { maskingPolicies.remove(name); }
    public MaskingPolicy getMaskingPolicy(final String name) { return maskingPolicies.find(name); }
    public boolean hasMaskingPolicy(final String name) { return maskingPolicies.has(name); }
    public List<MaskingPolicy> getMaskingPolicies() { return maskingPolicies.values(); }
    /** The masking policy of exactly this canonical name, or null: one differing only in case is another. */
    public MaskingPolicy getMaskingPolicyExact(final String name) { return maskingPolicies.findExact(name); }
    /** Renames the policy, the new name kept as written: a quoted name keeps its case (live-verified). */
    public void renameMaskingPolicy(final String oldName, final String newName) {
        NamedObjects.rename(maskingPolicies, oldName, newName);
    }

    // Row Access Policies
    public void addRowAccessPolicy(final RowAccessPolicy policy) {
        rowAccessPolicies.put(policy);
    }
    public void dropRowAccessPolicy(final String name) { rowAccessPolicies.remove(name); }
    public RowAccessPolicy getRowAccessPolicy(final String name) { return rowAccessPolicies.find(name); }
    public boolean hasRowAccessPolicy(final String name) { return rowAccessPolicies.has(name); }
    public List<RowAccessPolicy> getRowAccessPolicies() { return rowAccessPolicies.values(); }
    /** The row access policy of exactly this canonical name, or null: one differing only in case is another. */
    public RowAccessPolicy getRowAccessPolicyExact(final String name) { return rowAccessPolicies.findExact(name); }
    /** Renames the policy, the new name kept as written: a quoted name keeps its case (live-verified). */
    public void renameRowAccessPolicy(final String oldName, final String newName) {
        NamedObjects.rename(rowAccessPolicies, oldName, newName);
    }

    // Procedures and functions, each kind an OverloadStore. A routine is stored under its canonical name - an
    // unquoted name upper-cased, a quoted one as written - and resolved by that name EXACTLY, so "a" and A are two
    // routines, each reached only by its own spelling (live-verified). getProcedure and getFunction alone also answer
    // a differently-cased name, for the engine's Java callers.
    public void addProcedure(final Procedure procedure) {
        procedures.add(procedure);
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
        procedures.remove(procedure);
    }

    public Procedure getProcedure(final String name) {
        return procedures.get(name);
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
        return procedures.getBySignature(name, argumentTypes);
    }

    /**
     * The overload a statement names by the signature it writes (see {@link OverloadStore#getByWrittenSignature}).
     *
     * @param name          the procedure's exact canonical name
     * @param argumentTypes the signature written after it, each type with the parameters written on it
     * @return the overload
     */
    public Procedure getProcedureByWrittenSignature(final String name, final List<DataType> argumentTypes) {
        return procedures.getByWrittenSignature(name, argumentTypes);
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

    /** Every procedure this session reaches: the permanent ones and its own temporary ones. */
    public List<Procedure> getProcedures() {
        return procedures.values();
    }

    /** Every procedure, each session's temporary ones included, for what outlives or owns them all. */
    public List<Procedure> allProcedures() {
        return procedures.all();
    }

    /** Every overload carrying exactly this canonical name this session reaches, or none. */
    public List<Procedure> getProcedureOverloads(final String name) {
        return procedures.overloads(name);
    }

    // Functions
    public void addFunction(final Function function) {
        functions.add(function);
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
        if (functions.remove(function) && function.getHome() == this) {
            function.setHome(null);
        }
    }

    public Function getFunction(final String name) {
        return functions.get(name);
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
        return functions.getBySignature(name, argumentTypes);
    }

    /**
     * The overload a statement names by the signature it writes (see {@link OverloadStore#getByWrittenSignature}).
     *
     * @param name          the function's exact canonical name
     * @param argumentTypes the signature written after it, each type with the parameters written on it
     * @return the overload
     */
    public Function getFunctionByWrittenSignature(final String name, final List<DataType> argumentTypes) {
        return functions.getByWrittenSignature(name, argumentTypes);
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

    /** Every function this session reaches: the permanent ones and its own temporary ones. */
    public List<Function> getFunctions() {
        return functions.values();
    }

    /**
     * Every function, each session's temporary ones included, for what outlives or owns them all - and for
     * INFORMATION_SCHEMA.FUNCTIONS, which lists another session's temporary function (live-verified).
     */
    public List<Function> allFunctions() {
        return functions.all();
    }

    /** Every overload carrying exactly this canonical name this session reaches, or none. */
    public List<Function> getFunctionOverloads(final String name) {
        return functions.overloads(name);
    }

    /**
     * Whether a new function of that lifetime would find this signature taken (see
     * {@link OverloadStore#holdsSignature(String, List, boolean)}).
     *
     * @param name       the function's canonical name
     * @param parameters the new signature
     * @param temporary  whether the new function is temporary
     * @return whether the signature is taken
     */
    public boolean hasFunctionSignature(final String name, final List<Parameter> parameters, final boolean temporary) {
        return functions.holdsSignature(name, parameters, temporary);
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
        return procedures.holdsSignature(name, parameters, temporary);
    }

    /** Whether a function of that name already carries this signature (see {@link #addFunction}). */
    public boolean hasFunctionSignature(final String name, final List<Parameter> parameters) {
        return functions.holdsSignature(name, parameters);
    }

    /** Whether a procedure of that name already carries this signature (see {@link #addProcedure}). */
    public boolean hasProcedureSignature(final String name, final List<Parameter> parameters) {
        return procedures.holdsSignature(name, parameters);
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
     * Refuses a function signature that an overload the name already has would compete with for some call (see
     * {@link OverloadStore#requireUnambiguous}).
     *
     * @param name       the function's canonical name
     * @param parameters the new signature
     */
    public void requireUnambiguousFunction(final String name, final List<Parameter> parameters) {
        functions.requireUnambiguous(name, parameters);
    }

    /**
     * {@link #requireUnambiguousFunction} for a procedure.
     *
     * @param name       the procedure's canonical name
     * @param parameters the new signature
     */
    public void requireUnambiguousProcedure(final String name, final List<Parameter> parameters) {
        procedures.requireUnambiguous(name, parameters);
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
                final Sequence cloned = bound.getHome() == source ? clone.sequences.findExact(bound.getName()) : null;
                if (cloned != null) {
                    column.setDefaultValue(new SequenceDefault(cloned));
                }
            }
        }
    }

    // Streams
    public void addStream(final Stream stream) {
        rejectNameHeldByOtherKind(stream.getName(), RelationKind.STREAM, false);
        streams.add(stream);
    }

    public void dropStream(final String name) {
        streams.drop(name);
    }

    public Stream getStream(final String name) {
        return streams.get(name);
    }

    public List<Stream> getStreams() {
        return streams.values();
    }

    /**
     * Whether a stream of EXACTLY this name is here - the check a replace makes, where a name that differs
     * only in case is a different stream.
     */
    public boolean hasStreamExact(final String name) {
        return streams.hasExact(name);
    }

    /**
     * The stream of EXACTLY this name, as SQL names one: a name that differs only in case is another stream, which
     * is not here (live-verified).
     *
     * @param name the canonical name
     * @return the stream
     */
    public Stream getStreamExact(final String name) {
        return streams.getExact(name);
    }

    /**
     * Drops the stream of EXACTLY this name.
     *
     * @param name the canonical name
     */
    public void dropStreamExact(final String name) {
        streams.dropExact(name);
    }

    // Tasks
    public void addTask(final Task task) {
        tasks.add(task);
    }

    public void dropTask(final String name) {
        tasks.drop(name);
    }

    public boolean hasTask(final String name) {
        return tasks.has(name);
    }

    public Task getTask(final String name) {
        return tasks.get(name);
    }

    public List<Task> getTasks() {
        return tasks.values();
    }

    /** Whether a task of EXACTLY this name is here: a name that differs only in case is another task. */
    public boolean hasTaskExact(final String name) {
        return tasks.hasExact(name);
    }

    /**
     * The task of EXACTLY this name, as SQL names one: a name that differs only in case is another task, which is
     * not here (live-verified).
     *
     * @param name the canonical name
     * @return the task
     */
    public Task getTaskExact(final String name) {
        return tasks.getExact(name);
    }

    /**
     * Drops the task of EXACTLY this name.
     *
     * @param name the canonical name
     */
    public void dropTaskExact(final String name) {
        tasks.dropExact(name);
    }

    // Alerts
    /** Adds an alert; a name already taken by an alert is refused. */
    public void addAlert(final Alert alert) {
        alerts.add(alert);
    }

    /** Drops an alert, refused in the does-not-exist family when there is none of that name. */
    public void dropAlert(final String name) {
        alerts.drop(name);
    }

    /** Whether an alert of that name is here. */
    public boolean hasAlert(final String name) {
        return alerts.has(name);
    }

    /** The alert of that name, refused in the does-not-exist family when there is none. */
    public Alert getAlert(final String name) {
        return alerts.get(name);
    }

    /** Every alert of the schema. */
    public List<Alert> getAlerts() {
        return alerts.values();
    }

    // Pipes
    public void addPipe(final Pipe pipe) {
        pipes.add(pipe);
    }

    public void dropPipe(final String name) {
        pipes.drop(name);
    }

    public Pipe getPipe(final String name) {
        return pipes.get(name);
    }

    public List<Pipe> getPipes() {
        return pipes.values();
    }

    /** Whether a pipe of EXACTLY this name is here: a name that differs only in case is another pipe. */
    public boolean hasPipeExact(final String name) {
        return pipes.hasExact(name);
    }

    /**
     * The pipe of EXACTLY this name, as SQL names one: a name that differs only in case is another pipe, which is
     * not here (live-verified).
     *
     * @param name the canonical name
     * @return the pipe
     */
    public Pipe getPipeExact(final String name) {
        return pipes.getExact(name);
    }

    /**
     * Drops the pipe of EXACTLY this name.
     *
     * @param name the canonical name
     */
    public void dropPipeExact(final String name) {
        pipes.dropExact(name);
    }

    // Sequences
    public void addSequence(final Sequence sequence) {
        sequences.add(sequence);
        sequence.setHome(this);
    }

    public void dropSequence(final String name) {
        released(sequences.drop(name));
    }

    public Sequence getSequence(final String name) {
        return sequences.get(name);
    }

    public List<Sequence> getSequences() {
        return sequences.values();
    }

    /** Whether a sequence carries exactly this canonical name. */
    public boolean hasSequenceExact(final String name) {
        return sequences.hasExact(name);
    }

    /**
     * The sequence of exactly this canonical name, refused as missing when only another case of it exists.
     *
     * @param name the canonical name
     * @return the sequence
     */
    public Sequence getSequenceExact(final String name) {
        return sequences.getExact(name);
    }

    /**
     * Drops the sequence of exactly this canonical name, refused as missing when only another case of it exists.
     *
     * @param name the canonical name
     */
    public void dropSequenceExact(final String name) {
        released(sequences.dropExact(name));
    }

    /** A dropped sequence no longer counts this schema its home. */
    private void released(final Sequence dropped) {
        if (dropped.getHome() == this) {
            dropped.setHome(null);
        }
    }

    // File formats
    /**
     * Adds a file format, replacing the one of its name; another session's temporary format keeps its name, as one
     * session's format never replaces another's.
     */
    public void addFileFormat(final FileFormat fileFormat) {
        fileFormats.put(fileFormat);
    }

    public void dropFileFormat(final String name) {
        fileFormats.drop(name);
    }

    public FileFormat getFileFormat(final String name) {
        return fileFormats.find(name);
    }

    public boolean hasFileFormat(final String name) {
        return fileFormats.has(name);
    }

    /**
     * The file format of exactly this canonical name, or null: one differing only in case is another.
     *
     * @param name the canonical name
     * @return the format, or null
     */
    public FileFormat getFileFormatExact(final String name) {
        return fileFormats.findExact(name);
    }

    /** Whether a file format carries exactly this canonical name; one differing only in case is another. */
    public boolean hasFileFormatExact(final String name) {
        return fileFormats.hasExact(name);
    }

    /**
     * Drops the file format of exactly this canonical name, refused as missing when only another case of it exists.
     *
     * @param name the canonical name
     */
    public void dropFileFormatExact(final String name) {
        fileFormats.dropExact(name);
    }

    /** The file formats a statement here lists: every one but another session's temporary ones. */
    public List<FileFormat> getFileFormats() {
        return fileFormats.values();
    }

    /** Every file format held here, every session's temporary ones included: the engine's bookkeeping reads these. */
    public List<FileFormat> allFileFormats() {
        return fileFormats.all();
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
        for (final Tag tag : tags.all()) {
            final Tag clonedTag = new Tag(tag.getName(), tag.getAllowedValues(), tag.getComment());
            clonedTag.markAllowedValuesListed(tag.hasAllowedValues());
            clonedTag.setClonedFromId(tag.getId());
            clonedTag.setPropagate(tag.getPropagate());
            clonedTag.setOnConflict(tag.getOnConflict());
            clonedTag.setOwner(tag.getOwner());
            copyTags(tag, clonedTag);
            clonedSchema.tags.put(clonedTag);
        }

        // Dynamic tables, as CREATE DYNAMIC TABLE ... CLONE makes one: the definition and settings, the
        // clone suspended, its reclustering too. Their rows are their query's answer, so there is nothing
        // else to copy.
        for (final DynamicTable dynamicTable : dynamicTables.all()) {
            final DynamicTable clonedDynamic = dynamicTable.copyAs(dynamicTable.getName());
            clonedDynamic.setClone(true);
            clonedDynamic.setState(State.SUSPENDED);
            clonedDynamic.setSchedulingState("SUSPENDED");
            clonedDynamic.setLastSuspendedOn(StatementClock.instant());
            clonedDynamic.setReclusterSuspended(true);
            clonedDynamic.setOwner(dynamicTable.getOwner());
            copyTags(dynamicTable, clonedDynamic);
            clonedSchema.dynamicTables.put(clonedDynamic);
        }

        // Materialized views, which read what their definition names, as a cloned view does.
        for (final MaterializedView view : materializedViews.all()) {
            final MaterializedView clonedView = view.copy();
            clonedView.setOwner(view.getOwner());
            copyTags(view, clonedView);
            clonedSchema.materializedViews.put(clonedView);
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
        for (final Procedure proc : procedures.all()) {
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
            clonedSchema.procedures.hold(clonedProc);
        }

        // Clone functions — same story: keep LANGUAGE/handler/runtime plus the RETURNS TABLE columns and the
        // null-handling / volatility / secure / imports attributes, else a cloned Java (or JS/Python) UDF is
        // treated as a SQL UDF and its body fails to evaluate when called.
        for (final Function func : functions.all()) {
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
            clonedSchema.functions.hold(clonedFunc);
        }

        // Clone streams — preserve the source TYPE (TABLE vs VIEW), showInitialRows, the resolved base
        // tables (for a stream ON VIEW), owner and comment. The old 3-arg constructor dropped all of these
        // and defaulted sourceType to TABLE, so a cloned stream ON a VIEW failed to read in the clone
        // ("Table does not exist: <view>") because the read path resolved its source as a table.
        for (final Stream stream : streams.all()) {
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
            clonedSchema.streams.put(clonedStream);
        }

        // Clone tasks: the whole definition, AFTER and WHEN included, and suspended (documented). A task
        // graph lives in one schema and names its tasks bare, so the clone's graph links the clone's tasks.
        for (final Task task : tasks.all()) {
            clonedSchema.tasks.put(task.cloneDefinition());
        }
        // Clone alerts: a cloned alert keeps its definition and starts suspended.
        for (final Alert alert : alerts.all()) {
            clonedSchema.alerts.put(alert.copy(alert.getName()));
        }

        // Clone pipes. A pipe that loads from an internal stage is not cloned, and a cloned one does not run
        // until it is resumed: paused, or STOPPED_CLONED for an auto-ingest pipe (documented).
        for (final Pipe pipe : pipes.all()) {
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
            clonedSchema.pipes.put(clonedPipe);
        }

        // Clone sequences
        for (final Sequence sequence : sequences.all()) {
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
            clonedSchema.sequences.put(clonedSequence);
            clonedSequence.setHome(clonedSchema);
        }
        // A cloned table that drew from one of this schema's sequences draws from that sequence's clone.
        clonedSchema.rebindSequenceDefaults(this, clonedSchema);

        // Clone the policy OBJECTS too — a policy attached to a cloned table/view must still resolve
        // inside the clone (fresh instances: a later rename of the original must not affect the clone).
        for (final Contact contact : contacts.all()) {
            final Contact clonedContact = new Contact(contact.getName());
            clonedContact.setComment(contact.getComment());
            clonedContact.setUrl(contact.getUrl());
            clonedContact.setEmailDistributionList(contact.getEmailDistributionList());
            clonedSchema.contacts.put(clonedContact);
        }
        // Each policy keeps its comment, owner and tags.
        for (final ProjectionPolicy policy : projectionPolicies.all()) {
            clonedSchema.projectionPolicies.put(
                withPolicyDetails(policy, new ProjectionPolicy(policy.getName(), policy.getBody())));
        }
        for (final AggregationPolicy policy : aggregationPolicies.all()) {
            clonedSchema.aggregationPolicies.put(
                withPolicyDetails(policy, new AggregationPolicy(policy.getName(), policy.getBody())));
        }
        for (final JoinPolicy policy : joinPolicies.all()) {
            clonedSchema.joinPolicies.put(
                withPolicyDetails(policy, new JoinPolicy(policy.getName(), policy.getBody())));
        }
        for (final MaskingPolicy policy : maskingPolicies.all()) {
            final MaskingPolicy clonedPolicy = new MaskingPolicy(policy.getName(), policy.getParameters(),
                policy.getReturnType(), policy.getBody());
            clonedPolicy.setReturnDataType(policy.getReturnDataType());
            clonedPolicy.setReturnSpelling(policy.getReturnSpelling());
            clonedPolicy.setExemptOtherPolicies(policy.isExemptOtherPolicies());
            clonedSchema.maskingPolicies.put(withPolicyDetails(policy, clonedPolicy));
        }
        for (final RowAccessPolicy policy : rowAccessPolicies.all()) {
            final RowAccessPolicy clonedPolicy =
                new RowAccessPolicy(policy.getName(), policy.getParameters(), policy.getBody());
            clonedPolicy.setExemptOtherPolicies(policy.isExemptOtherPolicies());
            clonedSchema.rowAccessPolicies.put(withPolicyDetails(policy, clonedPolicy));
        }

        // Clone stages — the DEFINITION carries over (Snowflake's CLONE keeps stage definitions), its
        // file format options, copy options and directory setting included (Stage.copy). Dropping them
        // silently broke every jar-backed UDF in a cloned database ("Stage does not exist" at first
        // invocation).
        for (final Stage stage : stages.all()) {
            clonedSchema.stages.put(stage.copy());
        }

        // Clone file formats; a temporary one is no part of a clone, as a temporary table is not.
        for (final FileFormat fileFormat : fileFormats.all()) {
            if (fileFormat.isTemporary()) {
                continue;
            }
            final FileFormat clonedFormat = new FileFormat(fileFormat.getName(), fileFormat.getType());
            for (final Map.Entry<String, String> option : fileFormat.getOptions().entrySet()) {
                clonedFormat.setOption(option.getKey(), option.getValue());
            }
            clonedFormat.setComment(fileFormat.getComment());
            clonedFormat.setOwner(fileFormat.getOwner());
            clonedSchema.fileFormats.put(clonedFormat);
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
        final Stage stage = here ? stages.find(parts.get(parts.size() - 1)) : null;
        return stage != null && stage.getType() == StageType.INTERNAL;
    }

    // Stages
    public void addStage(final Stage stage) {
        stages.add(stage);
    }

    public void dropStage(final String name) {
        stages.drop(name);
    }

    public Stage getStage(final String name) {
        return stages.get(name);
    }

    /**
     * The stage of exactly this canonical name, refused as missing when only another case of it exists.
     *
     * @param name the canonical name
     * @return the stage
     */
    public Stage getStageExact(final String name) {
        return stages.getExact(name);
    }

    public boolean hasStage(final String name) {
        return stages.has(name);
    }

    /**
     * Whether a stage of EXACTLY this name is here - the check a create makes, where a name that differs
     * only in case is a different stage.
     */
    public boolean hasStageExact(final String name) {
        return stages.hasExact(name);
    }

    /**
     * Drops the stage of exactly this canonical name, refused as missing when only another case of it exists.
     *
     * @param name the canonical name
     */
    public void dropStageExact(final String name) {
        stages.dropExact(name);
    }

    /** The stages a statement here lists: every one but another session's temporary ones. */
    public List<Stage> getStages() {
        return stages.values();
    }

    /** Every stage held here, every session's temporary ones included: the engine's own bookkeeping reads these. */
    public List<Stage> allStages() {
        return stages.all();
    }

    // Cortex search services
    public void addCortexSearchService(final CortexSearchService service) {
        service.setDatabaseName(databaseName);
        service.setSchemaName(getName());
        cortexSearchServices.put(service);
    }

    public void dropCortexSearchService(final String name) {
        cortexSearchServices.drop(name);
    }

    public CortexSearchService getCortexSearchService(final String name) {
        return cortexSearchServices.get(name);
    }

    public boolean hasCortexSearchService(final String name) {
        return cortexSearchServices.has(name);
    }

    public List<CortexSearchService> getCortexSearchServices() {
        return cortexSearchServices.values();
    }

    /**
     * The service of exactly that canonical name, as a statement names one: a quoted name keeps its case, so
     * {@code "svc"} and {@code SVC} are two services.
     */
    public CortexSearchService getCortexSearchServiceExact(final String name) {
        return cortexSearchServices.getExact(name);
    }

    /** Whether a service of exactly that canonical name exists. */
    public boolean hasCortexSearchServiceExact(final String name) {
        return cortexSearchServices.hasExact(name);
    }

    /** Drops the service of exactly that canonical name; a missing one is refused as missing. */
    public void dropCortexSearchServiceExact(final String name) {
        cortexSearchServices.dropExact(name);
    }

    /** The schema's notebooks and Streamlit apps, live and dropped. */
    public AppObjectStore getAppObjects() {
        return appObjects;
    }

    // Tags
    public void addTag(final Tag tag) {
        tags.put(tag);
    }

    public void dropTag(final String name) {
        tags.drop(name);
    }

    public Tag getTag(final String name) {
        return tags.get(name);
    }

    public boolean hasTag(final String name) {
        return tags.has(name);
    }

    /**
     * Whether a tag of EXACTLY this name is here - the check a create or a rename makes, where a name that
     * differs only in case is a different tag (live-verified).
     */
    public boolean hasTagExact(final String name) {
        return tags.hasExact(name);
    }

    /**
     * The tag of EXACTLY this name, the one a statement names: a quoted name is that name alone (live-verified).
     *
     * @param name the tag's canonical name
     * @return the tag
     */
    public Tag getTagExact(final String name) {
        return tags.getExact(name);
    }

    /**
     * Drops the tag of EXACTLY this name, as DROP TAG and a replacement do.
     *
     * @param name the tag's canonical name
     */
    public void dropTagExact(final String name) {
        tags.dropExact(name);
    }

    public List<Tag> getTags() {
        return tags.values();
    }

    public void renameTag(final String oldName, final String newName) {
        NamedObjects.rename(tags, oldName, newName);
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
     * policies of a schema — masking, row access, join, aggregation, projection, password, session, authentication
     * and packages policies — share one namespace (live-verified).
     *
     * @param name the policy's name
     * @return the holder's kind, e.g. {@code MASKING_POLICY}, or null
     */
    public String policyKindHolding(final String name) {
        // A masking, row access, aggregation or projection policy holds exactly its own name: one that differs only
        // in case is another policy's (live-verified).
        if (getMaskingPolicyExact(name) != null) {
            return "MASKING_POLICY";
        }
        if (getRowAccessPolicyExact(name) != null) {
            return "ROW_ACCESS_POLICY";
        }
        if (hasJoinPolicy(name)) {
            return "JOIN_POLICY";
        }
        if (getAggregationPolicyExact(name) != null) {
            return "AGGREGATION_POLICY";
        }
        if (getProjectionPolicyExact(name) != null) {
            return "PROJECTION_POLICY";
        }
        for (final SecurityObjectKind kind : new SecurityObjectKind[] {SecurityObjectKind.PASSWORD_POLICY,
                SecurityObjectKind.SESSION_POLICY, SecurityObjectKind.AUTHENTICATION_POLICY,
                SecurityObjectKind.PACKAGES_POLICY}) {
            if (getSecurityObjects().get(kind, name) != null) {
                return kind.name();
            }
        }
        return null;
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
                return materializedViews.hasExact(name);
            case DYNAMIC_TABLE:
                return dynamicTables.hasExact(name);
            default:
                return streams.hasExact(name);
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
