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
import dev.frostlake.metastore.NameKeys;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.SqlObject;
import dev.frostlake.types.DataType;

import java.util.ArrayList;
import java.util.List;
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

    // Tables
    public void addTable(final Table table) {
        // Protect INFORMATION_SCHEMA from modifications
        if ("INFORMATION_SCHEMA".equals(this.getName())) {
            throw new RuntimeException("Cannot create tables in INFORMATION_SCHEMA");
        }
        rejectNameHeldByOtherKind(table.getName(), RelationKind.TABLE, table.isTemporary());
        final String upperName = table.getName();
        if (tables.containsKey(upperName)) {
            throw new RuntimeException(SqlCompilationError.of(
                "Object '" + table.getName() + "' already exists."));
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
        final Table table = tables.get(keyFor(tables, name));
        if (table == null || !table.getName().equals(name)) {
            return null;
        }
        return table;
    }

    /**
     * The table, or a miss reported as {@code reportedKind 'reportedName'}. A DDL statement always spells
     * the name in full, while a query or DML statement echoes what the writer wrote and calls a missing
     * FROM-clause name an {@code Object} — so the caller, which knows the statement, chooses both.
     */
    public Table getTable(final String name, final String reportedName, final String reportedKind) {
        final Table table = tables.get(keyFor(tables, name));
        if (table == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist(reportedKind, reportedName));
        }
        return table;
    }

    public boolean hasTable(final String name) {
        return tables.containsKey(keyFor(tables, name));
    }

    public List<Table> getTables() {
        return new ArrayList<>(tables.values());
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

    /** Every permanent table a temporary table hides in this schema. */
    public List<Table> getShadowedTables() {
        return new ArrayList<>(shadowedTables.values());
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
        if (views.containsKey(upperName)) {
            throw new RuntimeException(SqlCompilationError.of(
                "Object '" + view.getName() + "' already exists."));
        }
        views.put(upperName, view);
    }

    public void dropView(final String name) {
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
        final View view = views.get(keyFor(views, name));
        if (view == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("View", qualified(name)));
        }
        return view;
    }

    /** Whether a view of this name exists, without the throwing lookup {@link #getView} performs. */
    public boolean hasView(final String name) {
        return views.containsKey(keyFor(views, name));
    }

    public List<View> getViews() {
        return new ArrayList<>(views.values());
    }

    // Materialized Views
    public void addMaterializedView(final MaterializedView materializedView) {
        rejectNameHeldByOtherKind(materializedView.getName(), RelationKind.MATERIALIZED_VIEW, false);
        final String upperName = materializedView.getName();
        if (materializedViews.containsKey(upperName)) {
            throw new RuntimeException(SqlCompilationError.of(
                "Object '" + materializedView.getName() + "' already exists."));
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
            throw new RuntimeException(SqlCompilationError.of("Object '" + dt.getName() + "' already exists."));
        }
        dynamicTables.put(upperName, dt);
    }

    public void dropDynamicTable(final String name) {
        final String upperName = keyFor(dynamicTables, name);
        if (!dynamicTables.containsKey(upperName)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Dynamic table", qualified(name)));
        }
        dynamicTables.remove(upperName);
    }

    public DynamicTable getDynamicTable(final String name) {
        final DynamicTable dt = dynamicTables.get(keyFor(dynamicTables, name));
        if (dt == null) throw new RuntimeException(SqlCompilationError.doesNotExist("Dynamic table", qualified(name)));
        return dt;
    }

    public boolean hasDynamicTable(final String name) {
        return dynamicTables.containsKey(keyFor(dynamicTables, name));
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
        policy.rename(newName.toUpperCase());
        joinPolicies.put(newName.toUpperCase(), policy);
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
        policy.rename(newName.toUpperCase());
        aggregationPolicies.put(newName.toUpperCase(), policy);
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
        policy.rename(newName.toUpperCase());
        projectionPolicies.put(newName.toUpperCase(), policy);
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
        policy.rename(newName.toUpperCase());
        maskingPolicies.put(newName.toUpperCase(), policy);
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
        policy.rename(newName.toUpperCase());
        rowAccessPolicies.put(newName.toUpperCase(), policy);
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

        // Check for duplicate signature
        for (final Procedure existing : overloads) {
            if (hasSameSignature(existing.getParameters(), procedure.getParameters())) {
                throw new RuntimeException(alreadyExists(name));
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
        final List<Procedure> overloads = procedures.get(upperName);
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

    public List<Procedure> getProcedures() {
        final List<Procedure> allProcedures = new ArrayList<>();
        for (final List<Procedure> overloads : procedures.values()) {
            allProcedures.addAll(overloads);
        }
        return allProcedures;
    }

    /** Every overload carrying exactly this canonical name, or none. */
    public List<Procedure> getProcedureOverloads(final String name) {
        final List<Procedure> overloads = procedures.get(name);
        return overloads != null ? new ArrayList<>(overloads) : new ArrayList<>();
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

        // Check for duplicate signature
        for (final Function existing : overloads) {
            if (hasSameSignature(existing.getParameters(), function.getParameters())) {
                throw new RuntimeException(alreadyExists(name));
            }
        }

        overloads.add(function);
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
                break;
            }
        }
        if (overloads.isEmpty()) {
            functions.remove(function.getName());
        }
    }

    public Function getFunction(final String name) {
        final String upperName = keyFor(functions, name);
        final List<Function> overloads = functions.get(upperName);
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

    public List<Function> getFunctions() {
        final List<Function> allFunctions = new ArrayList<>();
        for (final List<Function> overloads : functions.values()) {
            allFunctions.addAll(overloads);
        }
        return allFunctions;
    }

    /** Every overload carrying exactly this canonical name, or none. */
    public List<Function> getFunctionOverloads(final String name) {
        final List<Function> overloads = functions.get(name);
        return overloads != null ? new ArrayList<>(overloads) : new ArrayList<>();
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

    // Helper methods for signature matching
    private boolean hasSameSignature(final List<Parameter> params1, final List<Parameter> params2) {
        if (params1.size() != params2.size()) {
            return false;
        }

        for (int i = 0; i < params1.size(); i++) {
            if (!params1.get(i).getDataType().equals(params2.get(i).getDataType())) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesSignature(final List<Parameter> params, final List<DataType> argumentTypes) {
        if (params.size() != argumentTypes.size()) {
            return false;
        }

        for (int i = 0; i < params.size(); i++) {
            if (!signatureFamily(params.get(i).getDataType()).equals(signatureFamily(argumentTypes.get(i)))) {
                return false;
            }
        }

        return true;
    }

    /**
     * The CANONICAL type family a routine's signature is stored under: Snowflake normalizes a
     * parameter's declared alias away, so a DROP / DESCRIBE names the FAMILY. Live-verified on a real
     * account, each spelling re-creating the routine between attempts: an
     * {@code x INTEGER} parameter is dropped by INTEGER, INT, BIGINT and NUMBER alike; a
     * {@code x DECIMAL(10,2)} parameter by DECIMAL and by NUMBER; a {@code x DOUBLE} parameter by
     * DOUBLE; and a {@code x CHAR(5)} parameter by STRING and by VARCHAR. Length is not compared —
     * bare {@code VARCHAR} matches a {@code CHAR(10)} parameter.
     *
     * <p>ONE probed spelling is deliberately not emulated: {@code DROP FUNCTION f(CHAR)} against a
     * {@code CHAR(5)} parameter fails on Snowflake even though STRING and VARCHAR succeed — an
     * inconsistency in its own alias handling (bare CHAR appears to mean CHAR(1)). Frostlake accepts
     * that spelling too rather than encoding the quirk.
     */
    private static String signatureFamily(final DataType type) {
        final String name = type.getName().toUpperCase();
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
            throw new RuntimeException(SqlCompilationError.of("Object '" + stream.getName() + "' already exists."));
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

    // Tasks
    public void addTask(final Task task) {
        final String upperName = task.getName();
        if (tasks.containsKey(upperName)) {
            throw new RuntimeException(SqlCompilationError.of("Object '" + task.getName() + "' already exists."));
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
            throw new RuntimeException(SqlCompilationError.of("Object '" + alert.getName() + "' already exists."));
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
            throw new RuntimeException(SqlCompilationError.of("Object '" + pipe.getName() + "' already exists."));
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
            throw new RuntimeException(SqlCompilationError.of("Object '" + sequence.getName() + "' already exists."));
        }
        sequences.put(upperName, sequence);
    }

    public void dropSequence(final String name) {
        final String upperName = keyFor(sequences, name);
        if (!sequences.containsKey(upperName)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Sequence", qualified(name)));
        }
        sequences.remove(upperName);
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

    // File formats
    public void addFileFormat(final FileFormat fileFormat) {
        fileFormats.put(fileFormat.getName(), fileFormat);
    }

    public void dropFileFormat(final String name) {
        final String upperName = keyFor(fileFormats, name);
        if (!fileFormats.containsKey(upperName)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("File format", qualified(name)));
        }
        fileFormats.remove(upperName);
    }

    public FileFormat getFileFormat(final String name) {
        return fileFormats.get(keyFor(fileFormats, name));
    }

    public boolean hasFileFormat(final String name) {
        return fileFormats.containsKey(keyFor(fileFormats, name));
    }

    public List<FileFormat> getFileFormats() {
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
        clonedSchema.setDataRetentionTimeInDays(dataRetentionTimeInDays);

        // Clone tables (structure only, not data). A temporary table is not cloned, and the permanent
        // table it hides is.
        for (final Table table : getNonTemporaryTables()) {
            final List<TableColumn> clonedColumns = new ArrayList<>();
            for (final TableColumn col : table.getColumns()) {
                final TableColumn clonedCol = new TableColumn(
                    col.getName(),
                    col.getDataType(),
                    col.isNullable(),
                    col.getDefaultValue(),
                    col.isPrimaryKey(),
                    col.isUnique(),
                    col.isAutoIncrement()
                );
                clonedCol.setComment(col.getComment());
                clonedCol.setCollation(col.getCollation());
                clonedCol.setMaskingPolicyName(col.getMaskingPolicyName());
                clonedColumns.add(clonedCol);
            }

            final Table clonedTable = new Table(table.getName(), clonedColumns, table.isTemporary(), table.isTransient());
            clonedTable.setComment(table.getComment());
            clonedTable.setClusterKeys(table.getClusterKeys());
            if (table.hasRowAccessPolicy()) {
                clonedTable.setRowAccessPolicyName(table.getRowAccessPolicyName());
                clonedTable.setAggregationPolicyName(table.getAggregationPolicyName());
                clonedTable.setJoinPolicyName(table.getJoinPolicyName());
                for (final SearchOptimizationExpression expression : table.getSearchOptimization()) {
                    clonedTable.restoreSearchOptimization(expression);
                }
                clonedTable.setAggregationEntityKey(table.getAggregationEntityKey());
                for (final CheckConstraint check : table.getCheckConstraints()) {
                    clonedTable.addCheckConstraint(check);
                }
                for (final Map.Entry<String, String> attached : table.getContacts().entrySet()) {
                    clonedTable.setContact(attached.getKey(), attached.getValue());
                }
                clonedTable.setRowAccessPolicyColumns(table.getRowAccessPolicyColumns());
            }
            clonedSchema.register(clonedTable);
        }

        // Clone views — INFORMATION_SCHEMA's system views are seeded in the target, never copied;
        // every view of an ordinary schema (whatever its name) is cloned.
        if (!"INFORMATION_SCHEMA".equals(getName())) {
            for (final View view : views.values()) {
                clonedSchema.views.put(view.getName(), view.copy());
            }
        }

        // Clone procedures — preserve LANGUAGE/handler/runtime/packages (and EXECUTE AS/imports). The short
        // constructor defaults the language to SQL, which turned a cloned JavaScript/Python/Java procedure
        // into a SQL one whose body then failed to parse at CALL time.
        for (final Map.Entry<String, List<Procedure>> entry : procedures.entrySet()) {
            final List<Procedure> clonedOverloads = new ArrayList<>();
            for (final Procedure proc : entry.getValue()) {
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
                clonedProc.setImports(proc.getImports());
                clonedProc.setReturnsTable(proc.returnsTable());
                clonedProc.setReturnColumns(proc.getReturnColumns());
                clonedProc.setExecuteAs(proc.getExecuteAs());
                clonedProc.setNullHandling(proc.getNullHandling());
                clonedProc.setVolatility(proc.getVolatility());
                clonedOverloads.add(clonedProc);
            }
            clonedSchema.procedures.put(entry.getKey(), clonedOverloads);
        }

        // Clone functions — same story: keep LANGUAGE/handler/runtime plus the RETURNS TABLE columns and the
        // null-handling / volatility / secure / imports attributes, else a cloned Java (or JS/Python) UDF is
        // treated as a SQL UDF and its body fails to evaluate when called.
        for (final Map.Entry<String, List<Function>> entry : functions.entrySet()) {
            final List<Function> clonedOverloads = new ArrayList<>();
            for (final Function func : entry.getValue()) {
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
                clonedFunc.setNullHandling(func.getNullHandling());
                clonedFunc.setVolatility(func.getVolatility());
                clonedFunc.setSecure(func.isSecure());
                clonedFunc.setImports(func.getImports());
                clonedOverloads.add(clonedFunc);
            }
            clonedSchema.functions.put(entry.getKey(), clonedOverloads);
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
            clonedSchema.streams.put(stream.getName(), clonedStream);
        }

        // Clone tasks
        for (final Task task : tasks.values()) {
            final Task clonedTask = new Task(
                task.getName(),
                task.getSchedule(),
                task.getScheduleType(),
                task.getSqlStatement(),
                task.getWarehouse()
            );
            clonedTask.setComment(task.getComment());
            clonedTask.setState(task.getState());
            clonedSchema.tasks.put(task.getName(), clonedTask);
        }
        // Clone alerts: a cloned alert keeps its definition and starts suspended.
        for (final Alert alert : alerts.values()) {
            clonedSchema.alerts.put(alert.getName(), alert.copy(alert.getName()));
        }

        // Clone pipes
        for (final Pipe pipe : pipes.values()) {
            final Pipe clonedPipe = new Pipe(
                pipe.getName(),
                pipe.getCopyStatement(),
                pipe.isAutoIngest(),
                pipe.getNotificationChannel()
            );
            clonedPipe.setComment(pipe.getComment());
            clonedPipe.setPaused(pipe.isPaused());
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
            clonedSchema.sequences.put(sequence.getName(), clonedSequence);
        }

        // Clone the policy OBJECTS too — a policy attached to a cloned table/view must still resolve
        // inside the clone (fresh instances: a later rename of the original must not affect the clone).
        for (final Contact contact : contacts.values()) {
            final Contact clonedContact = new Contact(contact.getName());
            clonedContact.setComment(contact.getComment());
            clonedContact.setUrl(contact.getUrl());
            clonedContact.setEmailDistributionList(contact.getEmailDistributionList());
            clonedSchema.contacts.put(contact.getName(), clonedContact);
        }
        for (final ProjectionPolicy policy : projectionPolicies.values()) {
            clonedSchema.projectionPolicies.put(policy.getName().toUpperCase(),
                new ProjectionPolicy(policy.getName(), policy.getBody()));
        }
        for (final AggregationPolicy policy : aggregationPolicies.values()) {
            clonedSchema.aggregationPolicies.put(policy.getName().toUpperCase(),
                new AggregationPolicy(policy.getName(), policy.getBody()));
        }
        for (final JoinPolicy policy : joinPolicies.values()) {
            clonedSchema.joinPolicies.put(policy.getName().toUpperCase(),
                new JoinPolicy(policy.getName(), policy.getBody()));
        }
        for (final MaskingPolicy policy : maskingPolicies.values()) {
            clonedSchema.maskingPolicies.put(policy.getName().toUpperCase(),
                new MaskingPolicy(policy.getName(), policy.getParameters(), policy.getReturnType(), policy.getBody()));
        }
        for (final RowAccessPolicy policy : rowAccessPolicies.values()) {
            clonedSchema.rowAccessPolicies.put(policy.getName().toUpperCase(),
                new RowAccessPolicy(policy.getName(), policy.getParameters(), policy.getBody()));
        }

        // Clone stages — the DEFINITION carries over (Snowflake's CLONE keeps stage definitions);
        // the constructor recomputes the local directory from the URL. Dropping them silently broke
        // every jar-backed UDF in a cloned database ("Stage does not exist" at first invocation).
        for (final Stage stage : stages.values()) {
            final Stage clonedStage = new Stage(stage.getName(), stage.getType(), stage.getUrl(),
                stage.getFileFormat(), stage.isEncryption(), stage.getComment(), stage.getS3Resolver());
            clonedSchema.stages.put(stage.getName(), clonedStage);
        }

        // Clone file formats
        for (final FileFormat fileFormat : fileFormats.values()) {
            final FileFormat clonedFormat = new FileFormat(fileFormat.getName(), fileFormat.getType());
            for (final Map.Entry<String, String> option : fileFormat.getOptions().entrySet()) {
                clonedFormat.setOption(option.getKey(), option.getValue());
            }
            clonedFormat.setComment(fileFormat.getComment());
            clonedSchema.fileFormats.put(fileFormat.getName(), clonedFormat);
        }

        return clonedSchema;
    }

    // Stages
    public void addStage(final Stage stage) {
        final String key = stage.getName();
        if (stages.containsKey(key)) {
            throw new RuntimeException(SqlCompilationError.of("Object '" + stage.getName() + "' already exists."));
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
        if (s == null) throw new RuntimeException(SqlCompilationError.doesNotExist("Stage", qualified(name)));
        return s;
    }

    public boolean hasStage(final String name) {
        return stages.containsKey(keyFor(stages, name));
    }

    /**
     * Whether a stage of EXACTLY this name is here - the check a create makes, where a name that differs
     * only in case is a different stage.
     */
    public boolean hasStageExact(final String name) {
        return stages.containsKey(name);
    }

    public List<Stage> getStages() {
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

    public List<Tag> getTags() {
        return new ArrayList<>(tags.values());
    }

    public void renameTag(final String oldName, final String newName) {
        final Tag tag = getTag(oldName);
        if (tags.containsKey(keyFor(tags, newName))) {
            throw new RuntimeException(SqlCompilationError.of("Object '" + newName.toUpperCase() + "' already exists."));
        }
        tags.remove(keyFor(tags, oldName));
        tag.rename(newName);
        tags.put(newName.toUpperCase(), tag);
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
        // A PERMANENT create looks past a temporary holder of the name and is judged against the
        // permanent object underneath, naming ITS kind. A temporary create sees the temporary holder,
        // and so does a stream, whose name check reaches the session's own objects (live-verified).
        final boolean seesTemporary = temporary || kind == RelationKind.STREAM;
        for (final RelationKind holder : RelationKind.values()) {
            if (holder == kind || !holds(holder, name, seesTemporary)
                    || yieldsTo(holder, kind, temporary, holderIsTemporary(holder, name))) {
                continue;
            }
            throw new RuntimeException(SqlCompilationError.of(
                "Object '" + name + "' already exists as " + holder.spelling()));
        }
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
        final Table table = tables.get(name);
        return table != null && table.isTemporary();
    }

    /** Whether a kind holds that name in this schema, counting a temporary object only when asked. */
    private boolean holds(final RelationKind kind, final String name, final boolean seesTemporary) {
        switch (kind) {
            case TABLE:
                return tables.containsKey(name) && (seesTemporary || !tables.get(name).isTemporary());
            case VIEW:
                return views.containsKey(name) && (seesTemporary || !views.get(name).isTemporary());
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
            return tables.containsKey(name) && tables.get(name).isTemporary();
        }
        return holder == RelationKind.VIEW && views.containsKey(name) && views.get(name).isTemporary();
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
