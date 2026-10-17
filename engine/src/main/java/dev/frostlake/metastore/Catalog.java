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

import dev.frostlake.config.S3PathResolver;
import dev.frostlake.executor.SqlAccessControlError;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.StatementClock;
import dev.frostlake.metastore.model.AccountTags;
import dev.frostlake.metastore.model.AggregationPolicy;
import dev.frostlake.metastore.model.ComputePool;
import dev.frostlake.metastore.model.Contact;
import dev.frostlake.metastore.model.CortexSearchService;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.JoinPolicy;
import dev.frostlake.metastore.model.MaskingPolicy;
import dev.frostlake.metastore.model.ObjectParameters;
import dev.frostlake.metastore.model.Pipe;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.ProjectionPolicy;
import dev.frostlake.metastore.model.RelationKind;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.RowAccessPolicy;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurityObjectStore;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.StageType;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.metastore.model.User;
import dev.frostlake.metastore.model.View;
import dev.frostlake.metastore.model.Warehouse;
import dev.frostlake.metastore.model.WarehouseSize;
import dev.frostlake.security.SessionContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public class Catalog {

    // The account locator a fully qualified name may lead with; null until the engine sets it.
    private String accountLocator;
    private final Map<String, Database> databases;
    private final Map<String, Warehouse> warehouses;
    private final Map<String, ComputePool> computePools = new ConcurrentHashMap<>();
    private final IntegrationRegistry integrations = new IntegrationRegistry();
    /** The files COPY has loaded, for LOAD_HISTORY and COPY_HISTORY. */
    private final LoadHistory loadHistory = new LoadHistory();
    /**
     * The files each permanent table has loaded, by the table's storage key: what a COPY skips unless FORCE = TRUE,
     * kept with the catalog so a snapshot, a clone and a restored state carry it.
     */
    private final Map<String, Set<String>> copyLoadedFiles = new ConcurrentHashMap<>();
    private final ExternalVolumeRegistry externalVolumes = new ExternalVolumeRegistry();
    private final Map<String, User> users;
    private final Map<String, Role> roles;
    private final AccountDirectory accountDirectory = new AccountDirectory();
    private String currentDatabase;
    private String currentSchema;
    private String currentWarehouse;
    // Per-thread session scope for the concurrent front-end: {database, schema} applied around each
    // statement so parallel read-locked statements from DIFFERENT sessions resolve unqualified names
    // against their own context instead of clobbering the one shared pair above (a USE in one session
    // must never leak into another). No scope on the thread → the global fields; embedded use never
    // begins a scope and behaves exactly as before.
    private final ThreadLocal<String[]> sessionScope = new ThreadLocal<>();
    // Resolves s3:// stage URLs to local paths (stage.s3.localMappings / .localRoot); set by the engine
    // so LIST/GET/PUT/REMOVE on S3 stages and IMPORTS JAR loading share one S3 -> local mapping.
    private S3PathResolver s3PathResolver;
    // Session context used to stamp the creating role as owner on new objects; set by the engine.
    private SessionContext sessionContext;
    /** The parameters set on the account, which every user and session inherits. */
    private final ObjectParameters accountParameters = new ObjectParameters();
    /** The tags set on the account itself. */
    private final AccountTags accountTags = new AccountTags();
    /**
     * The names user aggregate functions have been created under, so that a call no built-in answers to is looked
     * for among the user's aggregates only when one may go by its name. A name stays after its function is gone.
     */
    private final Set<String> aggregateFunctionNames = ConcurrentHashMap.newKeySet();

    /**
     * Note a name a user aggregate function is created, restored or renamed under.
     *
     * @param name the function's canonical name
     */
    public void noteAggregateFunction(final String name) {
        aggregateFunctionNames.add(name);
    }

    /**
     * Whether any user aggregate function was ever created here, so that a name may be one's.
     *
     * @return false while none was
     */
    public boolean holdsAggregateFunctionNames() {
        return !aggregateFunctionNames.isEmpty();
    }

    /**
     * Whether a user aggregate function may go by this name: false for a name no aggregate was ever created under.
     *
     * @param name a canonical function name
     * @return whether a user aggregate may answer to it
     */
    public boolean mayNameAggregateFunction(final String name) {
        return aggregateFunctionNames.contains(name);
    }

    /** The parameters set on the account (its users and their sessions inherit them). */
    public ObjectParameters getAccountParameters() {
        return accountParameters;
    }

    /** The tags set on the account itself. */
    public AccountTags getAccountTags() {
        return accountTags;
    }

    // Dropped-object retention for UNDROP (Time Travel restore): "KIND:fqName" → every dropped version of that name,
    // oldest first. UNDROP restores the newest, and the next UNDROP of the name the one dropped before it.
    private final Map<String, List<DroppedObject>> droppedObjects = new ConcurrentHashMap<>();
    /** Told of every dropped version {@link #purgeExpiredDropped} forgets for good; null for none. */
    private volatile DroppedVersionPurge droppedVersionPurge;

    public Catalog() {
        this.databases = new ConcurrentHashMap<>();
        this.warehouses = new ConcurrentHashMap<>();
        this.users = new ConcurrentHashMap<>();
        this.roles = new ConcurrentHashMap<>();
        createSystemDatabase();
        createDefaultWarehouse();
        createSystemRoles();
    }

    private void createDefaultWarehouse() {
        final Warehouse defaultWh = new Warehouse("COMPUTE_WH", WarehouseSize.X_SMALL);
        warehouses.put("COMPUTE_WH", defaultWh);
    }

    private void createSystemDatabase() {
        // Create default SNOWFLAKE database
        // Marked read-only so external callers cannot write to it
        createDatabase("SNOWFLAKE");
        final Database system = getDatabase("SNOWFLAKE");
        // The schemas every account's SNOWFLAKE database holds, empty here unless something is modelled in them.
        for (final SystemSchema seeded : SystemSchema.values()) {
            final Schema schema = new Schema(seeded.name());
            // No role owns one: the owner a listing names for some is the SNOWFLAKE application (SystemSchema).
            schema.setOwner(null);
            schema.setComment(seeded.comment());
            system.addSchema(schema);
        }
        system.setReadOnly(true);
        this.currentDatabase = "SNOWFLAKE";
        this.currentSchema = "PUBLIC";
    }

    public void createDatabase(final String name) {
        if (databases.containsKey(name)) {
            throw new RuntimeException(alreadyExists(name));
        }
        // The name is stored, and keyed, as the reference resolved it — bare folded to upper, quoted
        // verbatim — so a database created as "mixedDb" is called mixedDb and MIXEDDB is another database
        // beside it (live-verified).
        final Database db = new Database(name);
        db.setOwner(currentRoleForOwner());
        databases.put(name, db);
        // A dropped database's privileges are not the new one's (GrantNames).
        GrantNames.created(this, "DATABASE", name);
    }

    public void cloneDatabase(final String sourceName, final String targetName) {
        if (databases.containsKey(targetName)) {
            throw new RuntimeException(alreadyExists(targetName));
        }

        final Database sourceDb = databaseExact(sourceName);
        final Database targetDb = sourceDb.clone(targetName);
        // The cloning role owns the clone, as it would a database it created; the clone's children keep their owners,
        // except that the role that clones a pipe owns the clone (documented).
        targetDb.setOwner(currentRoleForOwner());
        for (final Schema schema : targetDb.getAllSchemas()) {
            for (final Pipe pipe : schema.getPipes()) {
                pipe.setOwner(currentRoleForOwner());
            }
        }
        databases.put(targetName, targetDb);
        // The clone's members are granted what the source's are; the clone itself nothing (GrantNames).
        GrantNames.created(this, "DATABASE", targetName);
        GrantNames.containerCloned(this, new String[] {sourceDb.getName()}, new String[] {targetName});
    }

    /**
     * The sentence live gives for a name already taken — the same one a renamed table gets, so every
     * kind of object answers alike. The name is canonical and is spelled as every refusal spells one,
     * quoted only where it has to be: {@code Object '"lower_db"' already exists.}
     *
     * @param canonicalName the taken name, canonical
     * @return the refusal message
     */
    private static String alreadyExists(final String canonicalName) {
        return SqlCompilationError.of("Object '" + SqlIdentifiers.spellCanonical(canonicalName) + "' already exists.");
    }

    /**
     * The refusal of a rename onto a name that is taken, the object's own included: the new name as the
     * statement wrote it, path and all, {@code Object 'DB.S."t"' already exists.} (live-verified).
     *
     * @param writtenName the new name as written, canonical
     * @return the refusal
     */
    private static RuntimeException renamedOntoTaken(final String writtenName) {
        final StringBuilder written = new StringBuilder();
        for (final String part : QualifiedName.parse(writtenName).parts()) {
            written.append(written.length() > 0 ? "." : "").append(SqlIdentifiers.spellCanonical(part));
        }
        return new RuntimeException(SqlCompilationError.of("Object '" + written + "' already exists."));
    }

    public void dropDatabase(final String name, final boolean cascade) {
        final String key = NameKeys.keyFor(databases, name);
        if (!databases.containsKey(key)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Database", name));
        }
        final Database dropped = databases.remove(key);
        // ★ DROPPING THE CURRENT DATABASE LEAVES THE SESSION WITH NO CONTEXT AT ALL. Live answers NULL
        // to both CURRENT_DATABASE() and CURRENT_SCHEMA() afterwards; keeping the dropped name current
        // leaves a pair that names nothing and fails whenever an unqualified name is resolved. Live compares
        // IGNORING case here: dropping "db" clears a session whose current database is DB (live-verified).
        if (dropped.getName().equalsIgnoreCase(getCurrentDatabase())) {
            setCurrentDatabaseName(null);
            setCurrentSchemaName(null);
        }
    }

    /**
     * Drop a schema and move the session off it when it was the current one.
     *
     * <p>★ THE SCHEMA FALLS BACK TO PUBLIC, not to nothing — live leaves the DATABASE alone and puts
     * the schema back to PUBLIC, which is the same landing place {@link #useDatabase} chooses. Only
     * dropping the database itself clears both.
     *
     * @param databaseName the schema's database
     * @param schemaName the schema to drop
     * @param cascade whether to drop the schema's contents with it
     */
    public void dropSchema(final String databaseName, final String schemaName, final boolean cascade) {
        final Database database = getDatabase(databaseName);
        database.dropSchema(schemaName, cascade);
        if (schemaName.equalsIgnoreCase(getCurrentSchema())
                && database.getName().equalsIgnoreCase(getCurrentDatabase())) {
            setCurrentSchemaName(database.hasSchema("PUBLIC") ? "PUBLIC" : null);
        }
    }

    /**
     * A database by name, matched exactly or else by the one database whose name matches ignoring case
     * ({@link NameKeys#keyFor}) — the INTERNAL Java API, used by engine plumbing and by tests that name
     * {@code test_db} in lower case. SQL resolution must not come through here; see {@link #databaseExact}.
     */
    public Database getDatabase(final String name) {
        final Database db = databases.get(NameKeys.keyFor(databases, name));
        if (db == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Database", name));
        }
        return db;
    }

    /**
     * {@link #getDatabase} for a name that may name nothing, matched the same way.
     *
     * @param name the database name
     * @return the database, or null when there is none of that name
     */
    public Database findDatabase(final String name) {
        return name == null ? null : databases.get(NameKeys.keyFor(databases, name));
    }

    /**
     * The database a SQL reference names, matched EXACTLY.
     *
     * <p>A reference resolves to a name first — bare folds to upper, quoted keeps its case — and that name
     * must then equal a stored one. Live rejects {@code mixedDb} for a database created as
     * {@code "mixedDb"} with {@code Database 'MIXEDDB' does not exist or not authorized.}, naming the
     * resolved form rather than the spelling; so does this.
     */
    public Database databaseExact(final String name) {
        final Database db = name == null ? null : databases.get(name);
        if (db == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Database", name));
        }
        return db;
    }

    /**
     * ALTER SCHEMA … RENAME TO: a schema renamed in its database, or moved to another one with every member.
     * The target database must exist and hold no schema of the new name, the schema's own name included, both
     * checked before anything changes (live: {@code Database 'NOSUCH_DB' does not exist or not authorized.},
     * {@code Object 'DB.EXISTING' already exists.}, the taken name worded as the statement wrote it).
     *
     * @param schema         the schema
     * @param targetDatabase the database it is to be in, canonical
     * @param written        its new name as the statement wrote it, canonical: the name, or database and name
     */
    public void moveSchema(final Schema schema, final String targetDatabase, final String[] written) {
        final String newName = written[written.length - 1];
        final Database target = databaseExact(targetDatabase);
        final Database source = databaseExact(schema.getDatabaseName());
        if (target.hasSchemaExact(newName)) {
            // A schema whose name differs only in case is another schema, so SS may become "ss" beside
            // nothing, and never beside an "ss"; a schema renamed to its own name finds it taken too
            // (live-verified).
            throw renamedOntoTaken(QualifiedName.of(written).toString());
        }
        final String[] from = {source.getName(), schema.getName()};
        StreamSources.bindAll(this);
        final ForeignKeyParents parents = ForeignKeyParents.bind(this);
        source.detachSchema(schema.getName());
        schema.rename(newName);
        target.attachSchema(schema);
        StreamSources.respellAll(this);
        parents.respell(this);
        GrantNames.containerRenamed(this, from, new String[] {target.getName(), schema.getName()});
        // The policies it holds go with it, and so do the attachments that name them, wherever those are.
        PolicyReferences.repoint(allSchemas(), null, from, new String[] {target.getName(), schema.getName()});
    }

    /** Every schema of every database: the places an attachment to a policy anywhere can be. */
    private List<Schema> allSchemas() {
        final List<Schema> schemas = new ArrayList<>();
        for (final Database database : getAllDatabases()) {
            schemas.addAll(database.getAllSchemas());
        }
        return schemas;
    }

    /**
     * A renamed or moved policy keeps its attachments: each name that pointed at it points where it is now.
     *
     * @param kind    the policy's kind
     * @param from    the schema it was in
     * @param oldName its name there
     * @param to      the schema it is in now
     * @param newName its name now
     */
    private void repointPolicy(final PolicyKind kind, final Schema from, final String oldName, final Schema to,
                               final String newName) {
        PolicyReferences.repoint(allSchemas(), kind,
            new String[] {from.getDatabaseName(), from.getName(), oldName},
            new String[] {to.getDatabaseName(), to.getName(), newName});
        // The privileges granted on it go with it too (GrantNames).
        GrantNames.renamed(this, kind.name() + "_POLICY", from.qualifiedName(oldName), to.qualifiedName(newName));
    }

    /** True when a masking policy ({@code masking}) or row access policy is attached to any column,
     *  table or view — Snowflake refuses to drop or replace such a policy (live-verified). Attachment
     *  names may be stored bare or qualified, so the comparison uses the last segment. */
    public boolean isPolicyInUse(final String policyName, final boolean masking) {
        final String bare = policyName.toUpperCase(Locale.ROOT);
        for (final Database database : getAllDatabases()) {
            for (final Schema schema : database.getAllSchemas()) {
                for (final Table table : schema.getTables()) {
                    if (masking) {
                        for (final TableColumn column : table.getColumns()) {
                            if (policyNameMatches(column.getMaskingPolicyName(), bare)) {
                                return true;
                            }
                        }
                    } else if (policyNameMatches(table.getRowAccessPolicyName(), bare)) {
                        return true;
                    }
                }
                if (!masking) {
                    for (final View view : schema.getViews()) {
                        if (policyNameMatches(view.getRowAccessPolicyName(), bare)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean policyNameMatches(final String attached, final String bare) {
        if (attached == null) {
            return false;
        }
        final int dot = attached.lastIndexOf('.');
        return (dot >= 0 ? attached.substring(dot + 1) : attached).equalsIgnoreCase(bare);
    }

    public List<Database> getAllDatabases() {
        return new ArrayList<>(databases.values());
    }

    /**
     * Begin a per-thread session scope: until {@link #clearSessionScope()} runs on this thread, the
     * current database/schema are read from and written to this scope only. The concurrent front-end
     * brackets every statement with begin/clear so sessions cannot see each other's context.
     */
    public void beginSessionScope(final String database, final String schema) {
        beginSessionScope(database, schema, currentWarehouseName());
    }

    /**
     * Begin a per-thread session scope that also carries the session's current warehouse; the two-argument form
     * keeps the warehouse of the scope it nests in.
     *
     * @param database the session's current database, or null
     * @param schema the session's current schema, or null
     * @param warehouse the session's current warehouse, or null for none
     */
    public void beginSessionScope(final String database, final String schema, final String warehouse) {
        sessionScope.set(new String[] {storedDatabaseName(database), storedSchemaName(database, schema),
            warehouse == null ? null : warehouse.toUpperCase(Locale.ROOT)});
    }

    /**
     * The name a database is stored under, for a name a session or a saved context already holds. The
     * index is case-insensitive, so the object is found whatever case the holder kept, and the context
     * then carries the object's own spelling: a quoted lower-case name folded to upper case names nothing
     * an exact lookup can find. A name matching no database is folded, as it always was.
     */
    private String storedDatabaseName(final String name) {
        if (name == null) {
            return null;
        }
        final Database database = databases.get(NameKeys.keyFor(databases, name));
        return database != null ? database.getName() : name.toUpperCase(Locale.ROOT);
    }

    /** {@link #storedDatabaseName}'s counterpart for a schema of that database. */
    private String storedSchemaName(final String databaseName, final String schemaName) {
        if (schemaName == null) {
            return null;
        }
        final Database database = databaseName == null ? null : databases.get(NameKeys.keyFor(databases, databaseName));
        return database != null && database.hasSchema(schemaName)
            ? database.getSchema(schemaName).getName() : schemaName.toUpperCase(Locale.ROOT);
    }

    /** End this thread's session scope (idempotent). Read the final values via the getters first. */
    public void clearSessionScope() {
        sessionScope.remove();
    }

    /** This thread's active scope (or null) — capture before nesting another scope on the same thread. */
    public String[] currentSessionScope() {
        return sessionScope.get();
    }

    /** Reinstate a scope captured by {@link #currentSessionScope()} (null clears), unwinding a nested scope. */
    public void restoreSessionScope(final String[] scope) {
        if (scope != null) {
            sessionScope.set(scope);
        } else {
            sessionScope.remove();
        }
    }

    private void setCurrentDatabaseName(final String name) {
        final String[] scope = sessionScope.get();
        if (scope != null) {
            scope[0] = name;
        } else {
            this.currentDatabase = name;
        }
    }

    private void setCurrentSchemaName(final String name) {
        final String[] scope = sessionScope.get();
        if (scope != null) {
            scope[1] = name;
        } else {
            this.currentSchema = name;
        }
    }

    public void useDatabase(final String name) {
        final Database database = getDatabase(name); // Validates existence
        // The session carries the database's STORED name, not the caller's spelling folded: a database
        // created as "php dsn db" is current as php dsn db, which is what CURRENT_DATABASE() answers and
        // what the exact lookup of every unqualified name needs.
        setCurrentDatabaseName(database.getName());
        // Switching database also moves the current schema, as Snowflake does: leaving a schema of the OLD
        // database current produces an impossible (database, schema) pair that then fails whenever anything
        // resolves an unqualified name. PUBLIC when the new database has one, otherwise unset.
        setCurrentSchemaName(database.hasSchema("PUBLIC") ? "PUBLIC" : null);
    }

    /**
     * Restore a previously-saved (database, schema) pair WITHOUT re-validating it — for unwinding a CALL or a
     * task back to the caller's context. Re-running {@link #useDatabase}/{@link #useSchema} there can throw
     * (the objects may have been dropped meanwhile, or the saved pair may no longer be valid), and an
     * exception raised while unwinding replaces the statement's real result and escapes the procedure's own
     * EXCEPTION handler. Names that still match an object are restored in its stored spelling.
     */
    public void restoreContext(final String databaseName, final String schemaName) {
        setCurrentDatabaseName(storedDatabaseName(databaseName));
        setCurrentSchemaName(storedSchemaName(databaseName, schemaName));
    }

    public void useSchema(final String name) {
        if (getCurrentDatabase() == null) {
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
        final Database db = getDatabase(getCurrentDatabase());
        final String schemaName = db.getSchema(name).getName(); // Validates existence
        requireUsable(db.getName(), schemaName);
        setCurrentSchemaName(schemaName);
    }

    /** Two schemas of the SNOWFLAKE database are listed and not reached by a USE, which fails as any USE fails. */
    private static void requireUsable(final String databaseName, final String schemaName) {
        final SystemSchema system = SystemSchema.of(databaseName, schemaName);
        if (system != null && !system.isUsable()) {
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
    }

    /**
     * USE DATABASE as a SQL reference resolves it — EXACTLY: the reference's resolved name (a bare one
     * folded to upper case, a quoted one verbatim) must equal a stored name, so {@code "audit_db"} does
     * not select AUDIT_DB and a bare {@code mixeddb} does not select {@code "MiXedDb"}. A miss names
     * nothing — live answers every failed USE with the same sentence — and leaves the context as it was.
     *
     * @param resolvedName the database reference, resolved
     */
    public void useDatabaseReference(final String resolvedName) {
        final Database database = exactDatabaseOrNull(resolvedName);
        if (database == null) {
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
        useDatabase(database.getName());
    }

    /**
     * USE SCHEMA as a SQL reference resolves it, exactly as {@link #useDatabaseReference} does, for a
     * schema of the named database or, with no database part, of the current one. Both parts resolve
     * before anything moves, so a missing schema leaves the current database unchanged too.
     *
     * @param resolvedDatabase the database part, resolved, or null for the current database
     * @param resolvedSchema the schema part, resolved
     */
    public void useSchemaReference(final String resolvedDatabase, final String resolvedSchema) {
        if (resolvedDatabase == null && getCurrentDatabase() == null) {
            // Live answers USE SCHEMA with no current database in the sentence of any failed USE.
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
        final Database database = exactDatabaseOrNull(resolvedDatabase != null ? resolvedDatabase : getCurrentDatabase());
        final Schema schema = database == null || !database.hasSchema(resolvedSchema)
            ? null : database.getSchema(resolvedSchema);
        if (schema == null || !schema.getName().equals(resolvedSchema)) {
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
        requireUsable(database.getName(), schema.getName());
        setCurrentDatabaseName(database.getName());
        setCurrentSchemaName(schema.getName());
    }

    /** The database whose stored name is exactly {@code resolvedName}, or null. */
    private Database exactDatabaseOrNull(final String resolvedName) {
        return resolvedName == null ? null : databases.get(resolvedName);
    }

    public String getCurrentDatabase() {
        final String[] scope = sessionScope.get();
        return scope != null ? scope[0] : currentDatabase;
    }

    public String getCurrentSchema() {
        final String[] scope = sessionScope.get();
        return scope != null ? scope[1] : currentSchema;
    }

    public Table resolveTable(final String qualifiedName) {
        return resolveTable(QualifiedName.parse(qualifiedName));
    }


    /**
     * The table a SQL reference names, matched exactly, reported as {@code reportedKind 'reportedName'}.
     *
     * <p>Exactness lives here rather than in {@link Schema#getTable(String)} for the same reason it lives
     * outside {@code Table}'s column accessors: those are an internal Java API that engine plumbing and
     * tests call with whatever case is convenient, while this is the boundary where a SQL reference — bare
     * folded to upper, quoted verbatim — meets the catalog and must match a stored name outright.
     */
    private Table tableForReference(final Schema schema, final String name,
                                    final String reportedName, final String reportedKind) {
        final Table table = schema.tableExact(name);
        if (table == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist(reportedKind, reportedName));
        }
        return table;
    }

    /**
     * The account locator a name may lead with — set from the engine's configuration, upper-cased as a
     * bare identifier folds; null leaves every name as written.
     *
     * @param locator the locator CURRENT_ACCOUNT() answers with
     */
    public void setAccountLocator(final String locator) {
        this.accountLocator = locator == null ? null : locator.toUpperCase(Locale.ROOT);
    }

    /** The account locator CURRENT_ACCOUNT() answers with, upper-cased; null when none was set. */
    public String getAccountLocator() {
        return accountLocator;
    }

    /**
     * An object name without the account locator it may lead with. A fully qualified name has up to
     * {@code objectParts} parts (three for a table or view, two for a schema); live accepts one more in
     * front when it is THIS account's locator — {@code am68630.db.s.t}, a quoted {@code "AM68630"} too —
     * and resolves the rest exactly as the shorter spelling. Any other extra part, a wrong account or a
     * fifth part, names nothing and is refused in the sentence live gives.
     *
     * @param parts the resolved parts (a bare one folded, a quoted one verbatim)
     * @param objectParts how many parts the name may have without the account
     * @return the parts naming the object within the account
     */
    /**
     * How many parts of a name name something within the account: all of them, less the account locator a name of
     * several parts may lead with.
     *
     * @param parts the resolved parts
     * @return the count without a leading locator of this account
     */
    public int partsWithinAccount(final String[] parts) {
        return parts.length > 1 && accountLocator != null && accountLocator.equals(parts[0]) ? parts.length - 1
            : parts.length;
    }

    public String[] withoutAccount(final String[] parts, final int objectParts) {
        if (parts.length <= objectParts) {
            return parts;
        }
        if (parts.length == objectParts + 1 && accountLocator != null && accountLocator.equals(parts[0])) {
            return Arrays.copyOfRange(parts, 1, parts.length);
        }
        throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
    }

    private QualifiedName withoutAccount(final QualifiedName qn, final int objectParts) {
        return qn.size() <= objectParts ? qn : QualifiedName.of(withoutAccount(qn.parts(), objectParts));
    }

    public Table resolveTable(final QualifiedName written) {
        final QualifiedName qn = withoutAccount(written, 3);
        if (qn.size() == 1) {
            // table name only
            if (getCurrentDatabase() == null || getCurrentSchema() == null) {
                throw NoCurrentDatabaseRefusal.forStatement();
            }
            final Schema owner = databaseExact(getCurrentDatabase()).schemaExact(getCurrentSchema());
            return tableForReference(owner, qn.part(0), owner.qualifiedName(qn.part(0)), "Table");
        } else if (qn.size() == 2) {
            // schema.table
            if (getCurrentDatabase() == null) {
                throw NoCurrentDatabaseRefusal.forStatement();
            }
            final Schema owner = databaseExact(getCurrentDatabase()).schemaExact(qn.part(0));
            return tableForReference(owner, qn.part(1), owner.qualifiedName(qn.part(1)), "Table");
        } else if (qn.size() == 3) {
            // database.schema.table
            final Schema owner = databaseExact(qn.part(0)).schemaExact(qn.part(1));
            return tableForReference(owner, qn.part(2), owner.qualifiedName(qn.part(2)), "Table");
        } else {
            throw new RuntimeException("Invalid qualified name: " + qn);
        }
    }

    /**
     * The table a QUERY or DML statement named, with a miss reported the way Snowflake reports it there:
     * the name exactly as the writer spelled it when they spelled it bare, expanded in full when they
     * supplied any qualifier at all, and under {@code reportedKind} — {@code Object} for a FROM clause, an
     * UPDATE or a DELETE, {@code Table} for INSERT and DESCRIBE. Measured on a real account:
     * {@code SELECT 1 FROM nosuch} answers {@code Object 'NOSUCH' …} while {@code SELECT 1 FROM s.nosuch}
     * answers {@code Object 'DB.S.NOSUCH' …}. DDL differs — it always spells the name in full — which is
     * what plain {@link #resolveTable(QualifiedName)} does.
     */
    public Table resolveTableAsWritten(final String qualifiedName, final String reportedKind) {
        return resolveTableAsWritten(QualifiedName.parse(qualifiedName), reportedKind, "SELECT");
    }

    /**
     * {@link #resolveTableAsWritten(String, String)} for a lookup a query does not make. With no current
     * database, a schema-qualified name is refused naming that lookup: CLONE for a CLONE source, DUPLICATE
     * for a LIKE source, where every FROM clause says SELECT (live-verified).
     *
     * @param lookup what the refusal names when the session cannot place a schema-qualified name
     */
    public Table resolveTableAsWritten(final String qualifiedName, final String reportedKind,
                                       final String lookup) {
        return resolveTableAsWritten(QualifiedName.parse(qualifiedName), reportedKind, lookup);
    }

    public Table resolveTableAsWritten(final QualifiedName written, final String reportedKind) {
        return resolveTableAsWritten(written, reportedKind, "SELECT");
    }

    /**
     * Whether the table a SQL reference names exists, asked without the throw. False for a missing table
     * and for a name the session cannot place: a bare one with no current schema, or a schema-qualified
     * one with no current database. A database or schema the reference does name must still exist.
     *
     * @param written the reference, resolved part by part
     * @return whether the table exists
     */
    public boolean hasTableAsWritten(final QualifiedName written) {
        final QualifiedName qn = withoutAccount(written, 3);
        final boolean placeable = qn.size() == 3
            || getCurrentDatabase() != null && (qn.size() == 2 || getCurrentSchema() != null);
        return placeable && schemaOwning(qn).tableExact(qn.last()) != null;
    }

    private Table resolveTableAsWritten(final QualifiedName written, final String reportedKind,
                                        final String lookup) {
        final QualifiedName qn = withoutAccount(written, 3);
        if (qn.size() != 1) {
            // Any qualifier at all and the reported name is the fully expanded one, which is exactly what
            // the schema already spells — only the kind can differ.
            if ("Table".equals(reportedKind)) {
                return resolveTable(qn);
            }
            // Only a TWO-part name needs the session: it names a schema, and the database has to come
            // from somewhere. A fully qualified one names its own database and resolves with no
            // current context at all — which is what dropping the current database now leaves.
            //
            // With none, a FROM clause is refused as a SELECT whatever the statement, and so is the target
            // an UPDATE, a DELETE or a MERGE reads. DESCRIBE VIEW names its own verb (live-verified).
            if (qn.size() == 2 && getCurrentDatabase() == null) {
                throw "Object".equals(reportedKind)
                    ? NoCurrentDatabaseRefusal.naming(lookup) : NoCurrentDatabaseRefusal.forStatement();
            }
            final Schema schema = qn.size() == 2
                ? databaseExact(getCurrentDatabase()).schemaExact(qn.part(0))
                : databaseExact(qn.part(0)).schemaExact(qn.part(1));
            final String last = qn.part(qn.size() - 1);
            return tableForReference(schema, last,
                QualifiedName.join(schema.getDatabaseName(), schema.getName(), last), reportedKind);
        }
        if (getCurrentDatabase() == null || getCurrentSchema() == null) {
            // A bare name the session cannot place names nothing, and misses as any other name would.
            throw new RuntimeException(SqlCompilationError.doesNotExist(reportedKind, QualifiedName.join(qn.part(0))));
        }
        return tableForReference(databaseExact(getCurrentDatabase()).schemaExact(getCurrentSchema()),
            qn.part(0), QualifiedName.join(qn.part(0)), reportedKind);
    }

    public View resolveView(final String qualifiedName) {
        return resolveView(QualifiedName.parse(qualifiedName));
    }

    public View resolveView(final QualifiedName written) {
        final QualifiedName qn = withoutAccount(written, 3);
        if (qn.size() == 1) {
            // view name only
            if (getCurrentDatabase() == null || getCurrentSchema() == null) {
                throw NoCurrentDatabaseRefusal.forStatement();
            }
            return databaseExact(getCurrentDatabase())
                    .schemaExact(getCurrentSchema())
                    .getView(qn.part(0));
        } else if (qn.size() == 2) {
            // schema.view
            if (getCurrentDatabase() == null) {
                throw NoCurrentDatabaseRefusal.forStatement();
            }
            return databaseExact(getCurrentDatabase())
                    .schemaExact(qn.part(0))
                    .getView(qn.part(1));
        } else if (qn.size() == 3) {
            // database.schema.view
            return databaseExact(qn.part(0))
                    .schemaExact(qn.part(1))
                    .getView(qn.part(2));
        } else {
            throw new RuntimeException("Invalid qualified name: " + qn);
        }
    }

    /**
     * A Cortex search service by name, however much of the path the caller wrote. The owning schema is
     * resolved first so a missing DATABASE or SCHEMA is reported as such, and only a resolvable schema
     * that lacks the service reports the service missing.
     */
    public CortexSearchService resolveCortexSearchService(final String qualifiedName) {
        return resolveCortexSearchService(QualifiedName.parse(qualifiedName));
    }

    public CortexSearchService resolveCortexSearchService(final QualifiedName qn) {
        return schemaOwning(qn).getCortexSearchService(qn.last());
    }

    /** Whether that service exists, without the throw — what CREATE … IF NOT EXISTS asks. */
    public boolean hasCortexSearchService(final QualifiedName qn) {
        return schemaOwning(qn).hasCortexSearchService(qn.last());
    }

    /**
     * The service a statement names, by the canonical parts of its name: a quoted name is the service of exactly
     * that spelling, so {@code "svc"} does not reach {@code SVC}.
     *
     * @param parts the name's parts off the parse tree
     */
    public CortexSearchService resolveCortexSearchService(final String[] parts) {
        return schemaOwning(QualifiedName.of(parts)).getCortexSearchServiceExact(parts[parts.length - 1]);
    }

    /** Whether the service a statement names by those parts exists; its schema must. */
    public boolean hasCortexSearchService(final String[] parts) {
        return schemaOwning(QualifiedName.of(parts)).hasCortexSearchServiceExact(parts[parts.length - 1]);
    }

    /**
     * The schema an object name passes through, which must exist even where the object itself need not:
     * DROP … IF EXISTS and TRUNCATE … IF EXISTS forgive only the object's own absence (live-verified). A
     * name the session cannot place is refused naming the statement.
     *
     * @param written the object's name, resolved part by part
     * @return the schema that holds, or would hold, the object
     */
    public Schema requireOwningSchema(final QualifiedName written) {
        return schemaOwning(written);
    }

    /**
     * The schema a qualified object name belongs to: the current one for a bare name, the named one
     * within the current database for {@code schema.object}, and the fully spelled one for three parts.
     */
    private Schema schemaOwning(final QualifiedName written) {
        final QualifiedName qn = withoutAccount(written, 3);
        if (qn.size() == 1) {
            if (getCurrentDatabase() == null || getCurrentSchema() == null) {
                throw NoCurrentDatabaseRefusal.forStatement();
            }
            return databaseExact(getCurrentDatabase()).schemaExact(getCurrentSchema());
        } else if (qn.size() == 2) {
            if (getCurrentDatabase() == null) {
                throw NoCurrentDatabaseRefusal.forStatement();
            }
            return databaseExact(getCurrentDatabase()).schemaExact(qn.part(0));
        } else if (qn.size() == 3) {
            return databaseExact(qn.part(0)).schemaExact(qn.part(1));
        } else {
            throw new RuntimeException("Invalid qualified name: " + qn);
        }
    }

    public Schema resolveSchema(final String qualifiedName) {
        return resolveSchema(QualifiedName.parse(qualifiedName));
    }

    public Schema resolveSchema(final QualifiedName written) {
        final QualifiedName qn = withoutAccount(written, 2);
        if (qn.size() == 1) {
            if (getCurrentDatabase() == null) {
                throw NoCurrentDatabaseRefusal.forStatement();
            }
            return databaseExact(getCurrentDatabase()).schemaExact(qn.part(0));
        } else if (qn.size() == 2) {
            return databaseExact(qn.part(0)).schemaExact(qn.part(1));
        } else {
            throw new RuntimeException("Invalid qualified schema name: " + qn);
        }
    }

    // Warehouse Management
    public void createWarehouse(final String name, final WarehouseSize size) {
        if (warehouses.containsKey(name.toUpperCase(Locale.ROOT))) {
            throw new RuntimeException(SqlCompilationError.of("Object '" + name.toUpperCase(Locale.ROOT) + "' already exists."));
        }
        final Warehouse warehouse = new Warehouse(name, size);
        warehouse.setOwner(currentRoleForOwner());
        warehouses.put(name.toUpperCase(Locale.ROOT), warehouse);
    }

    public void dropWarehouse(final String name) {
        final String upperName = name.toUpperCase(Locale.ROOT);
        if (!warehouses.containsKey(upperName)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Warehouse", name));
        }
        if ("COMPUTE_WH".equals(upperName)) {
            throw new RuntimeException("Cannot drop default warehouse");
        }
        warehouses.remove(upperName);
        forgetCurrentWarehouse(upperName);
    }

    // Compute pools (account-level, like warehouses)

    /** A real account phrases the duplicate as a generic object clash, not a pool-specific one. */
    public void createComputePool(final ComputePool pool) {
        if (computePools.containsKey(pool.getName().toUpperCase(Locale.ROOT))) {
            throw new RuntimeException(SqlCompilationError.of(
                "Object '" + pool.getName().toUpperCase(Locale.ROOT) + "' already exists."));
        }
        pool.setOwner(currentRoleForOwner());
        computePools.put(pool.getName().toUpperCase(Locale.ROOT), pool);
    }

    public boolean hasComputePool(final String name) {
        return computePools.containsKey(name.toUpperCase(Locale.ROOT));
    }

    public ComputePool getComputePool(final String name) {
        final ComputePool pool = computePools.get(name.toUpperCase(Locale.ROOT));
        if (pool == null) {
            throw new RuntimeException(
                SqlCompilationError.doesNotExist("Compute pool", name.toUpperCase(Locale.ROOT)));
        }
        return pool;
    }

    public void dropComputePool(final String name) {
        getComputePool(name);
        computePools.remove(name.toUpperCase(Locale.ROOT));
    }

    /** Every pool, sorted by name — the order SHOW COMPUTE POOLS lists them in. */
    public List<ComputePool> getComputePools() {
        final List<ComputePool> pools = new ArrayList<>(computePools.values());
        pools.sort(new Comparator<ComputePool>() {
            @Override
            public int compare(final ComputePool a, final ComputePool b) {
                return a.getName().compareTo(b.getName());
            }
        });
        return pools;
    }

    /** Whether that warehouse exists, without the throw — for the statements that must VALIDATE a
     *  warehouse they only reference. */
    public boolean hasWarehouse(final String name) {
        return name != null && warehouses.containsKey(name.toUpperCase(Locale.ROOT));
    }

    public Warehouse getWarehouse(final String name) {
        final Warehouse wh = warehouses.get(name.toUpperCase(Locale.ROOT));
        if (wh == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Warehouse", name));
        }
        return wh;
    }

    public List<Warehouse> getAllWarehouses() {
        return new ArrayList<>(warehouses.values());
    }

    public void useWarehouse(final String name) {
        getWarehouse(name); // Validates existence
        setCurrentWarehouseName(name.toUpperCase(Locale.ROOT));
    }

    /**
     * The session's current warehouse, or null when it has none: a warehouse dropped or renamed since it was
     * made current is current no longer (live-verified).
     */
    public String getCurrentWarehouse() {
        final String name = currentWarehouseName();
        return name != null && warehouses.containsKey(name) ? name : null;
    }

    /**
     * The warehouse a new session starts in: the one its user's DEFAULT_WAREHOUSE names when that warehouse exists,
     * otherwise the engine's own current warehouse.
     *
     * @return the warehouse's name, or null for none
     */
    public String sessionStartWarehouse() {
        final String userName = sessionContext == null ? null : sessionContext.getCurrentUser();
        final User user = userName == null ? null : users.get(userName.toUpperCase(Locale.ROOT));
        final String wanted = user == null ? null : user.getDefaultWarehouse();
        if (wanted != null && warehouses.containsKey(wanted.toUpperCase(Locale.ROOT))) {
            return wanted.toUpperCase(Locale.ROOT);
        }
        return getCurrentWarehouse();
    }

    public Warehouse getCurrentWarehouseObject() {
        final String name = getCurrentWarehouse();
        return name == null ? null : getWarehouse(name);
    }

    /** The warehouse name the session scope carries, or the engine's own outside one; not checked to exist. */
    private String currentWarehouseName() {
        final String[] scope = sessionScope.get();
        return scope != null ? scope[2] : currentWarehouse;
    }

    private void setCurrentWarehouseName(final String name) {
        final String[] scope = sessionScope.get();
        if (scope != null) {
            scope[2] = name;
        } else {
            this.currentWarehouse = name;
        }
    }

    /** The session that held the warehouse as its current one holds none now. */
    private void forgetCurrentWarehouse(final String name) {
        if (name.equalsIgnoreCase(currentWarehouseName())) {
            setCurrentWarehouseName(null);
        }
    }

    // Stage Management — stages now live in Schema
    private Schema resolveSchemaForObject(final String qualifiedName) {
        final String[] parts = QualifiedName.parse(qualifiedName).parts();
        // A fully qualified name carries its own database and does not consult the session's — naming
        // db.schema.object while another database is current is exactly what qualifying it fully is for.
        if (parts.length == 3) return getDatabase(parts[0]).getSchema(parts[1]);
        final String dbName = getCurrentDatabase();
        if (dbName == null) throw NoCurrentDatabaseRefusal.forStatement();
        final Database db = getDatabase(dbName);
        if (parts.length == 2) return db.getSchema(parts[0]);
        final String scName = getCurrentSchema();
        if (scName == null) throw NoCurrentDatabaseRefusal.forStatement();
        return db.getSchema(scName);
    }

    private String objectName(final String qualifiedName) {
        return QualifiedName.parse(qualifiedName).last();
    }

    /** Set the resolver used to map s3:// stage URLs to local paths (see {@link S3PathResolver}). */
    public void setS3PathResolver(final S3PathResolver s3PathResolver) {
        this.s3PathResolver = s3PathResolver;
    }

    /** Set the session context used to stamp object ownership at CREATE time. */
    /** The account's integrations. */
    public IntegrationRegistry getIntegrations() {
        return integrations;
    }

    /** The files COPY has loaded into the account's tables. */
    public LoadHistory getLoadHistory() {
        return loadHistory;
    }

    /**
     * The files each permanent table has loaded, by the table's storage key, each file as its load key: what a COPY
     * of the table skips unless FORCE = TRUE.
     *
     * @return the live map
     */
    public Map<String, Set<String>> getCopyLoadedFiles() {
        return copyLoadedFiles;
    }

    /** The account's external volumes. */
    public ExternalVolumeRegistry getExternalVolumes() {
        return externalVolumes;
    }

    public void setSessionContext(final SessionContext sessionContext) {
        this.sessionContext = sessionContext;
    }

    /**
     * The role to record as owner on newly-created objects: the session's current role — inside a stored procedure
     * running with owner's rights, the procedure's owner — else SYSADMIN.
     */
    public String currentRoleForOwner() {
        return sessionContext != null && sessionContext.getCreatingRole() != null
            ? sessionContext.getCreatingRole() : "SYSADMIN";
    }

    /**
     * The owner role of a schema-level object, for ownership-based authorization. Returns null when
     * the type is not one that carries an owner here, or the object cannot be resolved.
     */
    public String getObjectOwnerRole(final String objectType, final String objectName) {
        if (objectType == null || objectName == null) {
            return null;
        }
        try {
            switch (objectType.toUpperCase(Locale.ROOT)) {
                case "TABLE": return resolveTable(objectName).getOwner();
                case "VIEW": return resolveView(objectName).getOwner();
                case "SCHEMA": return resolveSchema(objectName).getOwner();
                case "DATABASE": return getDatabase(objectName).getOwner();
                case "STAGE": return getStage(objectName).getOwner();
                case "WAREHOUSE": return getWarehouse(objectName).getOwner();
                default: {
                    // Schema-level objects: resolve the containing schema, then the object by bare name.
                    final Schema schema = resolveSchemaForObject(objectName);
                    final String bare = objectName(objectName);
                    switch (objectType.toUpperCase(Locale.ROOT)) {
                        case "STREAM": return schema.getStream(bare).getOwner();
                        case "TASK": return schema.getTask(bare).getOwner();
                        case "PIPE": return schema.getPipe(bare).getOwner();
                        case "DYNAMIC TABLE": return schema.getDynamicTable(bare).getOwner();
                        case "MATERIALIZED VIEW": return schema.getMaterializedView(bare).getOwner();
                        case "MASKING POLICY": return schema.getMaskingPolicy(bare).getOwner();
                        case "ROW ACCESS POLICY": return schema.getRowAccessPolicy(bare).getOwner();
                        case "TAG": return schema.getTag(bare).getOwner();
                        default: return null;
                    }
                }
            }
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /** The user a DDL statement runs as, which LAST_DDL_BY names — CURRENT_USER()'s answer; null with no session. */
    public String currentUserForDdl() {
        return sessionContext != null ? sessionContext.getDisplayUser() : null;
    }

    /** The user whose stage {@code @~} resolves to: the session's current user, else PUBLIC. */
    public String currentUserForStage() {
        return sessionContext != null && sessionContext.getCurrentUser() != null
            ? sessionContext.getCurrentUser() : "PUBLIC";
    }

    /**
     * USE ROLE &lt;name&gt;: switch the session's primary role; errors if the role does not exist.
     *
     * <p>The refusal NAMES NOTHING — live answers "Object does not exist, or operation cannot be
     * performed." for a role that is not there, without quoting the name the way its object-not-found
     * sentences do elsewhere. That is deliberate on a real account: a role a session cannot use is
     * indistinguishable from one that does not exist, so the message cannot confirm either.
     */
    public void useRole(final String roleName) {
        // The name is the statement's canonical one: a quoted name reaches only the role of exactly that name.
        if (roleName == null || !roles.containsKey(roleName)) {
            throw new RuntimeException(SqlCompilationError.of(
                "Object does not exist, or operation cannot be performed."));
        }
        requireAssigned(roleName);
        if (sessionContext != null) {
            sessionContext.setCurrentRole(roleName);
        }
    }

    /**
     * Refuses a role the session's user may not activate. A role is one the user may activate when it was
     * granted to the user, or to a role the user may activate - so a role two grants below one the user holds is
     * reached, and the system roles under ACCOUNTADMIN are for a user who holds it - and PUBLIC always is. A role
     * that merely exists, its creator's included, is refused, and the session keeps the role it has
     * (live-verified). A session whose user this catalog does not hold is held to no grants.
     *
     * @param role the role named, upper-cased
     */
    private void requireAssigned(final String role) {
        if (sessionContext == null || "PUBLIC".equals(role)) {
            return;
        }
        // The engine's own session runs as SYSTEM on behalf of the user CURRENT_USER() names.
        final String securityUser = sessionContext.getCurrentUser();
        final String executing = "SYSTEM".equals(securityUser) ? sessionContext.getDisplayUser() : securityUser;
        final User user = executing == null ? null : users.get(executing.toUpperCase(Locale.ROOT));
        if (user == null) {
            return;
        }
        final Set<String> reached = new HashSet<String>();
        final List<String> pending = new ArrayList<String>(user.getGrantedRoles());
        while (!pending.isEmpty()) {
            final String next = pending.remove(pending.size() - 1);
            final Role held = roles.get(next);
            if (reached.add(next) && held != null) {
                pending.addAll(held.getGrantedRoles());
            }
        }
        if (!reached.contains(role)) {
            throw new RuntimeException(SqlAccessControlError.roleNotAssigned(role));
        }
    }

    /**
     * USE SECONDARY ROLES {ALL | NONE | role, …}: which roles beside the primary one are active. What is named
     * replaces what was in use before, each role once and in alphabetical order; NONE among the names stands for
     * no role, and ALL or NONE in quotes, whatever its case, is the word; a named role that is not there is
     * refused as any object USE cannot reach, one that is there and that the user may not activate as USE ROLE
     * refuses it, and the session keeps what it had (live-verified).
     *
     * @param named the roles named, canonical; null for ALL
     */
    public void useSecondaryRoles(final List<String> named) {
        if (sessionContext == null) {
            return;
        }
        final boolean all = named == null || named.size() == 1 && "ALL".equalsIgnoreCase(named.get(0));
        final Set<String> wanted = new TreeSet<String>();
        if (!all) {
            for (final String role : named) {
                if ("NONE".equalsIgnoreCase(role)) {
                    continue;
                }
                if (!roles.containsKey(role)) {
                    throw new RuntimeException(SqlCompilationError.of(
                        "Object does not exist, or operation cannot be performed."));
                }
                wanted.add(role);
            }
            // Each role named is there: now each must be one the user may activate, as USE ROLE asks.
            for (final String role : wanted) {
                requireAssigned(role);
            }
        }
        sessionContext.clearActiveRoles();
        if (sessionContext.getCurrentRole() != null) {
            sessionContext.addActiveRole(sessionContext.getCurrentRole());
        }
        if (!all) {
            sessionContext.setSecondaryRoles(String.join(",", wanted));
            for (final String role : wanted) {
                sessionContext.addActiveRole(role);
            }
        } else {
            // ALL is every role granted to the session's USER, not every role of the account: a role nobody
            // was granted stays out of reach (live-verified).
            sessionContext.setSecondaryRoles("ALL");
            final User user = users.get(String.valueOf(sessionContext.getCurrentUser()).toUpperCase(Locale.ROOT));
            if (user != null) {
                for (final String granted : user.getGrantedRoles()) {
                    sessionContext.addActiveRole(granted);
                }
            }
        }
    }

    /**
     * Record a dropped object's snapshot under "KIND:fqName" so a subsequent UNDROP can restore it. Earlier dropped
     * versions of the name stay behind it: each can be undropped in turn, newest first (documented).
     *
     * <p>The name is the object's exact canonical one ({@link QualifiedName#key}), matched exactly as a live object's
     * is: a schema created as {@code "s1"} and one created as {@code S1} are two schemas (live-verified), and a table
     * dropped from one is no table of the other's to UNDROP. The key was upper-cased, which let
     * {@code UNDROP TABLE S1.t} restore the table dropped from {@code "s1"} into {@code S1}.
     */
    public void recordDropped(final String key, final DroppedObject dropped) {
        synchronized (droppedObjects) {
            // The versions whose retention ran out go first, so a long run of drops holds no rows nothing can restore.
            purgeExpiredDropped();
            List<DroppedObject> versions = droppedObjects.get(key);
            if (versions == null) {
                versions = new ArrayList<>();
                droppedObjects.put(key, versions);
            }
            versions.add(dropped);
        }
    }

    /**
     * The dropped-object snapshots an UNDROP can still restore, of one kind (the key prefix before the colon,
     * e.g. {@code DATABASE} or {@code SCHEMA}): every dropped version of each name, the newest first.
     */
    public List<DroppedObject> droppedOfKind(final String kind) {
        final String prefix = kind.toUpperCase(Locale.ROOT) + ":";
        final List<DroppedObject> out = new ArrayList<>();
        synchronized (droppedObjects) {
            purgeExpiredDropped();
            for (final Map.Entry<String, List<DroppedObject>> entry : droppedObjects.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    for (int i = entry.getValue().size() - 1; i >= 0; i--) {
                        out.add(entry.getValue().get(i));
                    }
                }
            }
        }
        return out;
    }

    /**
     * Every dropped version UNDROP can still restore, by "KIND:fqName", each name's versions the oldest first: what
     * a snapshot keeps, and gives back through {@link #recordDropped} in the same order.
     *
     * @return a copy of the versions
     */
    public Map<String, List<DroppedObject>> droppedVersions() {
        final Map<String, List<DroppedObject>> out = new TreeMap<>();
        synchronized (droppedObjects) {
            purgeExpiredDropped();
            for (final Map.Entry<String, List<DroppedObject>> entry : droppedObjects.entrySet()) {
                out.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        return out;
    }

    /**
     * Who is told when a dropped version is forgotten for good: the engine, which deletes the stage files the version
     * set aside.
     *
     * @param purge the listener, or null for none
     */
    public void setDroppedVersionPurge(final DroppedVersionPurge purge) {
        this.droppedVersionPurge = purge;
    }

    /**
     * Forget the dropped versions whose retention has run out by the current statement's instant: UNDROP refuses them
     * as purged and SHOW ... HISTORY no longer lists them, and the {@link DroppedVersionPurge} is told of each. The
     * caller holds the store's lock.
     */
    private void purgeExpiredDropped() {
        final Instant now = StatementClock.instant();
        final List<String> emptied = new ArrayList<>();
        final DroppedVersionPurge purge = droppedVersionPurge;
        for (final Map.Entry<String, List<DroppedObject>> entry : droppedObjects.entrySet()) {
            final List<DroppedObject> kept = new ArrayList<>();
            for (final DroppedObject version : entry.getValue()) {
                if (!version.isPurgedAt(now)) {
                    kept.add(version);
                } else if (purge != null) {
                    purge.purged(entry.getKey(), version);
                }
            }
            if (kept.size() != entry.getValue().size()) {
                entry.getValue().clear();
                entry.getValue().addAll(kept);
            }
            if (kept.isEmpty()) {
                emptied.add(entry.getKey());
            }
        }
        for (final String key : emptied) {
            droppedObjects.remove(key);
        }
    }

    /**
     * Remove and return the most recent dropped-object snapshot for "KIND:fqName" whose retention has not run out, or
     * null if none.
     */
    public DroppedObject takeDropped(final String key) {
        synchronized (droppedObjects) {
            purgeExpiredDropped();
            final List<DroppedObject> versions = droppedObjects.get(key);
            if (versions == null || versions.isEmpty()) {
                return null;
            }
            final DroppedObject newest = versions.remove(versions.size() - 1);
            if (versions.isEmpty()) {
                droppedObjects.remove(key);
            }
            return newest;
        }
    }

    /**
     * Whether a database of EXACTLY this name exists, as {@link #databaseExact} would find it.
     *
     * @param name the canonical database name
     * @return whether it exists
     */
    public boolean hasDatabaseExact(final String name) {
        return name != null && databases.containsKey(name);
    }

    /** Re-insert a previously-dropped database object (UNDROP DATABASE). */
    public void restoreDatabase(final Database database) {
        databases.put(database.getName(), database);
    }

    public void createStage(final String name, final StageType type, final String url) {
        final Schema schema = resolveSchemaForObject(name);
        final String objName = objectName(name);
        if (schema.hasStageExact(objName)) {
            throw new NameTakenException(alreadyExists(objName));
        }
        final Stage stage = new Stage(objName, type, url, "CSV", false, null, s3PathResolver);
        stage.setOwner(FutureGrants.ownerOfNew(this, "STAGE", schema, objName));
        schema.addStage(stage);
    }

    public void createStage(final String name, final StageType type, final String url,
                           final String fileFormat, final boolean encryption, final String comment) {
        final Schema schema = resolveSchemaForObject(name);
        final String objName = objectName(name);
        if (schema.hasStageExact(objName)) {
            throw new NameTakenException(alreadyExists(objName));
        }
        final Stage stage = new Stage(objName, type, url, fileFormat, encryption, comment, s3PathResolver);
        stage.setOwner(FutureGrants.ownerOfNew(this, "STAGE", schema, objName));
        schema.addStage(stage);
    }

    public void dropStage(final String name) {
        resolveSchemaForObject(name).dropStage(objectName(name));
    }

    /**
     * Drops the stage a statement names, by exactly its canonical name: one differing only in case is another stage.
     *
     * @param name the name as written, qualified or not
     */
    public void dropStageExact(final String name) {
        resolveSchemaForObject(name).dropStageExact(objectName(name));
    }

    /** ALTER STAGE … RENAME TO — rekey the schema's map and rename the object itself. */
    public void renameStage(final String name, final String newName) {
        final Schema schema = resolveSchemaForObject(name);
        // A new name written with a path places the stage in the schema it names.
        final Schema target = QualifiedName.parse(newName).size() == 1 ? schema : resolveSchemaForObject(newName);
        final Stage stage = schema.getStage(objectName(name));
        // The new name is taken: refused before the old one is given up.
        if (target.hasStageExact(objectName(newName))) {
            throw renamedOntoTaken(newName);
        }
        final String grantedBefore = schema.qualifiedName(stage.getName());
        schema.dropStage(objectName(name));
        stage.setName(objectName(newName));
        target.addStage(stage);
        GrantNames.renamed(this, "STAGE", grantedBefore, target.qualifiedName(stage.getName()));
    }

    public Stage getStage(final String name) {
        return resolveSchemaForObject(name).getStage(objectName(name));
    }

    /** The schema a stage or table reference resolves in, as {@link #getStage} resolves it. */
    public Schema resolveOwningSchema(final String name) {
        return resolveSchemaForObject(name);
    }

    /**
     * Resolve a stream by its (optionally schema- or db-qualified) name against the current context —
     * {@code SYSTEM$STREAM_HAS_DATA('SCHEMA.STREAM')} passes the qualified text at runtime. Returns
     * null when the schema or stream does not exist.
     */
    public Stream resolveStream(final String qualifiedName) {
        final Schema schema = resolveSchemaForObject(qualifiedName);
        if (schema == null) {
            return null;
        }
        return schema.getStream(objectName(qualifiedName));
    }

    public void addFileFormat(final String qualifiedName, final FileFormat fileFormat) {
        resolveSchemaForObject(qualifiedName).addFileFormat(fileFormat);
    }

    public FileFormat getFileFormat(final String name) {
        return resolveSchemaForObject(name).getFileFormat(objectName(name));
    }

    public boolean hasFileFormat(final String name) {
        return resolveSchemaForObject(name).hasFileFormat(objectName(name));
    }

    public void dropFileFormat(final String name) {
        resolveSchemaForObject(name).dropFileFormat(objectName(name));
    }

    /**
     * Whether a file format of exactly the canonical name a statement writes exists; one differing only in case is
     * another file format.
     *
     * @param name the name as written, qualified or not
     * @return whether it exists
     */
    public boolean hasFileFormatExact(final String name) {
        return resolveSchemaForObject(name).hasFileFormatExact(objectName(name));
    }

    /**
     * Drops the file format a statement names, by exactly its canonical name.
     *
     * @param name the name as written, qualified or not
     */
    public void dropFileFormatExact(final String name) {
        resolveSchemaForObject(name).dropFileFormatExact(objectName(name));
    }

    public Pipe getPipe(final String name) {
        return resolveSchemaForObject(name).getPipe(objectName(name));
    }

    public List<Stage> getAllStages() {
        try {
            if (getCurrentDatabase() == null || getCurrentSchema() == null) return new ArrayList<>();
            return getDatabase(getCurrentDatabase()).getSchema(getCurrentSchema()).getStages();
        } catch (final Exception e) { return new ArrayList<>(); }
    }

    // Tag Management — tags now live in Schema
    public void createTag(final String name) {
        final Schema schema = resolveSchemaForObject(name);
        final String objName = objectName(name);
        if (schema.hasTagExact(objName)) {
            throw new NameTakenException(alreadyExists(objName));
        }
        final Tag tag = new Tag(objName);
        tag.setOwner(currentRoleForOwner());
        schema.addTag(tag);
    }

    public void createTag(final String name, final List<String> allowedValues, final String comment) {
        final Schema schema = resolveSchemaForObject(name);
        final String objName = objectName(name);
        if (schema.hasTagExact(objName)) {
            throw new NameTakenException(alreadyExists(objName));
        }
        final Tag tag = new Tag(objName, allowedValues, comment);
        tag.setOwner(currentRoleForOwner());
        schema.addTag(tag);
    }

    public void dropTag(final String name) {
        resolveSchemaForObject(name).dropTag(objectName(name));
    }

    public Tag getTag(final String name) {
        return resolveSchemaForObject(name).getTag(objectName(name));
    }

    public boolean hasTag(final String name) {
        try { return resolveSchemaForObject(name).hasTag(objectName(name)); }
        catch (final Exception e) { return false; }
    }

    /**
     * The tag a statement names, by exactly its canonical name: a quoted name is that name alone (live-verified).
     *
     * @param name the name as written, qualified or not
     * @return the tag
     */
    public Tag getTagExact(final String name) {
        return resolveSchemaForObject(name).getTagExact(objectName(name));
    }

    /**
     * Whether a tag of exactly the canonical name a statement writes is there.
     *
     * @param name the name as written, qualified or not
     * @return whether it is
     */
    public boolean hasTagExact(final String name) {
        try {
            return resolveSchemaForObject(name).hasTagExact(objectName(name));
        } catch (final RuntimeException noSchema) {
            return false;
        }
    }

    /**
     * Drops the tag a statement names, by exactly its canonical name.
     *
     * @param name the name as written, qualified or not
     */
    public void dropTagExact(final String name) {
        resolveSchemaForObject(name).dropTagExact(objectName(name));
    }

    public List<Tag> getAllTags() {
        try {
            if (getCurrentDatabase() == null || getCurrentSchema() == null) return new ArrayList<>();
            return getDatabase(getCurrentDatabase()).getSchema(getCurrentSchema()).getTags();
        } catch (final Exception e) { return new ArrayList<>(); }
    }

    /**
     * ALTER TAG … RENAME TO: a bare new name renames the tag in its schema, a qualified one moves it to the schema
     * that name resolves to.
     */
    public void renameTag(final String oldName, final String newName) {
        final Schema source = resolveSchemaForObject(oldName);
        final Schema target = QualifiedName.parse(newName).size() == 1 ? source : resolveSchemaForObject(newName);
        target.requireCreatable(Privilege.CREATE_TAG);
        final Tag renamed = source.getTag(objectName(oldName));
        final String grantedBefore = renamed != null ? source.qualifiedName(renamed.getName()) : null;
        if (target.hasTagExact(objectName(newName))) {
            // The name that is taken is echoed as the statement wrote it (live-verified).
            throw renamedOntoTaken(newName);
        }
        if (target == source) {
            source.renameTag(objectName(oldName), objectName(newName));
        } else {
            final Tag tag = source.getTag(objectName(oldName));
            source.dropTag(objectName(oldName));
            tag.setName(objectName(newName));
            target.addTag(tag);
        }
        if (grantedBefore != null) {
            GrantNames.renamed(this, "TAG", grantedBefore, target.qualifiedName(renamed.getName()));
        }
    }

    public void renameMaskingPolicy(final String oldName, final String newName) {
        final Schema from = resolveSchemaForObject(oldName);
        final Schema to = resolveSchemaForObject(newName);
        to.requireCreatable(Privilege.CREATE_MASKING_POLICY);
        final MaskingPolicy policy = from.getMaskingPolicy(objectName(oldName));
        final String was = policy != null ? policy.getName() : null;
        if (to.hasMaskingPolicy(objectName(newName))) {
            throw renamedOntoTaken(newName);
        }
        from.renameMaskingPolicy(objectName(oldName), objectName(newName));
        if (from != to) {
            // A qualified target naming ANOTHER schema moves the policy there (live-verified).
            final MaskingPolicy moved = from.getMaskingPolicy(objectName(newName));
            from.dropMaskingPolicy(objectName(newName));
            to.addMaskingPolicy(moved);
        }
        repointPolicy(PolicyKind.MASKING, from, was, to, policy.getName());
    }

    public void renameJoinPolicy(final String oldName, final String newName) {
        final Schema from = resolveSchemaForObject(oldName);
        final Schema to = resolveSchemaForObject(newName);
        to.requireCreatable(Privilege.CREATE_JOIN_POLICY);
        final JoinPolicy policy = from.getJoinPolicy(objectName(oldName));
        final String was = policy != null ? policy.getName() : null;
        from.renameJoinPolicy(objectName(oldName), objectName(newName));
        if (from != to) {
            // A qualified target naming ANOTHER schema moves the policy there (live-verified).
            final JoinPolicy moved = from.getJoinPolicy(objectName(newName));
            from.dropJoinPolicy(objectName(newName));
            to.addJoinPolicy(moved);
        }
        repointPolicy(PolicyKind.JOIN, from, was, to, policy.getName());
    }

    public void renameAggregationPolicy(final String oldName, final String newName) {
        final Schema from = resolveSchemaForObject(oldName);
        final Schema to = resolveSchemaForObject(newName);
        to.requireCreatable(Privilege.CREATE_AGGREGATION_POLICY);
        final AggregationPolicy policy = from.getAggregationPolicy(objectName(oldName));
        final String was = policy != null ? policy.getName() : null;
        from.renameAggregationPolicy(objectName(oldName), objectName(newName));
        if (from != to) {
            // A qualified target naming ANOTHER schema moves the policy there (live-verified).
            final AggregationPolicy moved = from.getAggregationPolicy(objectName(newName));
            from.dropAggregationPolicy(objectName(newName));
            to.addAggregationPolicy(moved);
        }
        repointPolicy(PolicyKind.AGGREGATION, from, was, to, policy.getName());
    }

    public void renameProjectionPolicy(final String oldName, final String newName) {
        final Schema from = resolveSchemaForObject(oldName);
        final Schema to = resolveSchemaForObject(newName);
        to.requireCreatable(Privilege.CREATE_PROJECTION_POLICY);
        final ProjectionPolicy policy = from.getProjectionPolicy(objectName(oldName));
        final String was = policy != null ? policy.getName() : null;
        from.renameProjectionPolicy(objectName(oldName), objectName(newName));
        if (from != to) {
            // A qualified target naming ANOTHER schema moves the policy there (live-verified).
            final ProjectionPolicy moved = from.getProjectionPolicy(objectName(newName));
            from.dropProjectionPolicy(objectName(newName));
            to.addProjectionPolicy(moved);
        }
        repointPolicy(PolicyKind.PROJECTION, from, was, to, policy.getName());
    }

    public void renameRowAccessPolicy(final String oldName, final String newName) {
        final Schema from = resolveSchemaForObject(oldName);
        final Schema to = resolveSchemaForObject(newName);
        to.requireCreatable(Privilege.CREATE_ROW_ACCESS_POLICY);
        final RowAccessPolicy policy = from.getRowAccessPolicy(objectName(oldName));
        final String was = policy != null ? policy.getName() : null;
        if (to.hasRowAccessPolicy(objectName(newName))) {
            throw renamedOntoTaken(newName);
        }
        from.renameRowAccessPolicy(objectName(oldName), objectName(newName));
        if (from != to) {
            // A qualified target naming ANOTHER schema moves the policy there (live-verified).
            final RowAccessPolicy moved = from.getRowAccessPolicy(objectName(newName));
            from.dropRowAccessPolicy(objectName(newName));
            to.addRowAccessPolicy(moved);
        }
        repointPolicy(PolicyKind.ROW_ACCESS, from, was, to, policy.getName());
    }

    /**
     * The row access policy a possibly-qualified name resolves to, or null when nothing does — the
     * lookup the attachment statements need before they may touch the object they attach to.
     *
     * @param qualifiedName the policy name as written: bare, schema-qualified or fully qualified
     * @return the policy, or null when the name resolves to no policy in that schema
     */
    public RowAccessPolicy findRowAccessPolicy(final String qualifiedName) {
        return resolveSchemaForObject(qualifiedName).getRowAccessPolicy(objectName(qualifiedName));
    }

    /**
     * The join policy a possibly-qualified name resolves to, or null when nothing does.
     *
     * @param qualifiedName the policy name as written
     * @return the policy, or null when the name resolves to none in that schema
     */
    public JoinPolicy findJoinPolicy(final String qualifiedName) {
        return resolveSchemaForObject(qualifiedName).getJoinPolicy(objectName(qualifiedName));
    }

    /** True when a join policy is attached to any table — live refuses to drop or replace one. */
    public boolean isJoinPolicyInUse(final String policyName) {
        final String bare = policyName.toUpperCase(Locale.ROOT);
        for (final Database database : databases.values()) {
            for (final Schema schema : database.getAllSchemas()) {
                for (final Table table : schema.getTables()) {
                    if (policyNameMatches(table.getJoinPolicyName(), bare)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * The aggregation policy a possibly-qualified name resolves to, or null when nothing does.
     *
     * @param qualifiedName the policy name as written
     * @return the policy, or null when the name resolves to none in that schema
     */
    public AggregationPolicy findAggregationPolicy(final String qualifiedName) {
        return resolveSchemaForObject(qualifiedName).getAggregationPolicy(objectName(qualifiedName));
    }

    /** True when an aggregation policy is attached to any table — live refuses to drop or replace one. */
    public boolean isAggregationPolicyInUse(final String policyName) {
        final String bare = policyName.toUpperCase(Locale.ROOT);
        for (final Database database : databases.values()) {
            for (final Schema schema : database.getAllSchemas()) {
                for (final Table table : schema.getTables()) {
                    if (policyNameMatches(table.getAggregationPolicyName(), bare)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * The projection policy a possibly-qualified name resolves to, or null when nothing does.
     *
     * @param qualifiedName the policy name as written
     * @return the policy, or null when the name resolves to none in that schema
     */
    public ProjectionPolicy findProjectionPolicy(final String qualifiedName) {
        return resolveSchemaForObject(qualifiedName).getProjectionPolicy(objectName(qualifiedName));
    }

    /** True when a projection policy is attached to any column — live refuses to drop or replace one. */
    public boolean isProjectionPolicyInUse(final String policyName) {
        final String bare = policyName.toUpperCase(Locale.ROOT);
        for (final Database database : databases.values()) {
            for (final Schema schema : database.getAllSchemas()) {
                for (final Table table : schema.getTables()) {
                    for (final TableColumn column : table.getColumns()) {
                        if (policyNameMatches(column.getProjectionPolicyName(), bare)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * The contact a possibly-qualified name resolves to, or null when nothing does.
     *
     * @param qualifiedName the contact name as written: bare, schema-qualified or fully qualified
     * @return the contact, or null when the name resolves to none in that schema
     */
    public Contact findContact(final String qualifiedName) {
        return resolveSchemaForObject(qualifiedName).getContact(objectName(qualifiedName));
    }

    /**
     * The masking policy a possibly-qualified name resolves to, or null when nothing does. A name
     * whose SCHEMA is missing does not answer null — it raises the schema's own refusal, which is
     * what live answers there too.
     *
     * @param qualifiedName the policy name as written: bare, schema-qualified or fully qualified
     * @return the policy, or null when the name resolves to no policy in that schema
     */
    public MaskingPolicy findMaskingPolicy(final String qualifiedName) {
        return resolveSchemaForObject(qualifiedName).getMaskingPolicy(objectName(qualifiedName));
    }

    /**
     * A schema-level object's name spelled in full — {@code database.schema.OBJECT} — resolving
     * however much of the path was written against the session, the way a reference to it resolves.
     * Live reports an attached policy this way whatever the attaching statement wrote, so a name
     * recorded for later display is qualified once, when it is attached, rather than at every read.
     *
     * @param qualifiedName the name as written: bare, schema-qualified or already fully qualified
     * @return the fully qualified name, upper-cased in its last part as the reference resolved it
     */
    public String qualifiedObjectName(final String qualifiedName) {
        return resolveSchemaForObject(qualifiedName)
            .qualifiedName(objectName(qualifiedName).toUpperCase(Locale.ROOT));
    }

    // User and Role Management
    private void createSystemRoles() {
        // Create all Snowflake system roles
        // Role hierarchy (top to bottom) — ORGADMIN stands apart, outside the hierarchy:
        // ACCOUNTADMIN -> SECURITYADMIN -> USERADMIN
        //             -> SYSADMIN
        //             -> PUBLIC (granted to all users)

        // ORGADMIN - Organization administrator (highest level)
        final Role orgAdmin = new Role("ORGADMIN");
        orgAdmin.setComment("Organization administrator can manage organizations and accounts in organizations");
        roles.put("ORGADMIN", orgAdmin);

        // ACCOUNTADMIN - Account administrator (manages account-level objects)
        final Role accountAdmin = new Role("ACCOUNTADMIN");
        accountAdmin.setComment("Account administrator can manage all aspects of the account.");
        roles.put("ACCOUNTADMIN", accountAdmin);

        // SECURITYADMIN - Security administrator (manages users, roles, and security)
        final Role securityAdmin = new Role("SECURITYADMIN");
        securityAdmin.setComment("Security administrator can manage security aspects of the account.");
        roles.put("SECURITYADMIN", securityAdmin);

        // USERADMIN - User administrator (manages users and roles)
        final Role userAdmin = new Role("USERADMIN");
        userAdmin.setComment("User administrator can create and manage users and roles");
        roles.put("USERADMIN", userAdmin);

        // SYSADMIN - System administrator (manages warehouses, databases, and other objects)
        final Role sysAdmin = new Role("SYSADMIN");
        sysAdmin.setComment("System administrator can create and manage databases and warehouses.");
        roles.put("SYSADMIN", sysAdmin);

        // PUBLIC - Default role (granted to all users automatically)
        final Role publicRole = new Role("PUBLIC");
        publicRole.setComment("Public role is automatically available to every user in the account.");
        roles.put("PUBLIC", publicRole);

        // Set up role hierarchy (roles inherit privileges from granted roles)
        // ACCOUNTADMIN inherits from SECURITYADMIN and SYSADMIN
        accountAdmin.grantRole("SECURITYADMIN");
        accountAdmin.grantRole("SYSADMIN");

        // SECURITYADMIN inherits from USERADMIN
        securityAdmin.grantRole("USERADMIN");

        // ORGADMIN sits OUTSIDE the hierarchy: it holds no role grants at all (live-verified:
        // SHOW GRANTS TO ROLE ORGADMIN lists no roles).
    }

    public void createUser(final String name) {
        if (users.containsKey(name.toUpperCase(Locale.ROOT))) {
            throw new RuntimeException("User already exists: " + name);
        }
        final User user = new User(name);
        user.setOwner(currentRoleForOwner());
        // Automatically grant PUBLIC role to all users
        user.grantRole("PUBLIC");
        users.put(name.toUpperCase(Locale.ROOT), user);
    }

    public void createUser(final String name, final String password, final String defaultRole) {
        if (users.containsKey(name.toUpperCase(Locale.ROOT))) {
            throw new RuntimeException("User already exists: " + name);
        }
        final User user = new User(name, password, defaultRole);
        user.setOwner(currentRoleForOwner());
        // Automatically grant PUBLIC role to all users
        user.grantRole("PUBLIC");
        users.put(name.toUpperCase(Locale.ROOT), user);
    }

    public void dropUser(final String name) {
        final String upperName = name.toUpperCase(Locale.ROOT);
        if (!users.containsKey(upperName)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("User", name));
        }
        users.remove(upperName);
    }

    private final SecurityObjectStore securityObjects = new SecurityObjectStore();

    /**
     * The account's network policies, and the password and network policies attached to the account and to its
     * users.
     */
    public SecurityObjectStore getSecurityObjects() {
        return securityObjects;
    }

    /** Whether a user of this name exists, for callers that must not throw on a miss. */
    public boolean hasUser(final String name) {
        return name != null && users.containsKey(name.toUpperCase(Locale.ROOT));
    }

    public User getUser(final String name) {
        final User user = users.get(name.toUpperCase(Locale.ROOT));
        if (user == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("User", name));
        }
        return user;
    }

    public List<User> getAllUsers() {
        return new ArrayList<>(users.values());
    }

    /**
     * Creates a role under the canonical name a statement resolved: an unquoted name in upper case, a quoted one
     * exactly as written, so {@code "low"} and {@code LOW} are two roles (live-verified).
     *
     * @param name the role's canonical name
     */
    public void createRole(final String name) {
        if (isSystemRole(name)) {
            throw new RuntimeException("Cannot create system role: " + name);
        }
        if (roles.containsKey(name)) {
            throw new RuntimeException(SqlCompilationError.of("Object '" + name + "' already exists."));
        }
        final Role role = new Role(name);
        role.setOwner(currentRoleForOwner());
        roles.put(name, role);
    }

    /**
     * Drops the role of exactly that canonical name.
     *
     * @param name the role's canonical name
     */
    public void dropRole(final String name) {
        if (!roles.containsKey(name)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Role", name));
        }
        // Cannot drop system roles
        if (isSystemRole(name)) {
            throw new RuntimeException("Cannot drop system role: " + name);
        }
        final String heldAs = roles.get(name).getName();
        roles.remove(name);
        // What the role owned is the dropping role's from now on (see RoleOwnership).
        RoleOwnership.handOver(this, heldAs, currentRoleForOwner());
    }

    /**
     * Check if a role is a system role
     */
    public boolean isSystemRole(final String roleName) {
        final String upperName = roleName.toUpperCase(Locale.ROOT);
        return upperName.equals("ORGADMIN") ||
               upperName.equals("ACCOUNTADMIN") ||
               upperName.equals("SECURITYADMIN") ||
               upperName.equals("USERADMIN") ||
               upperName.equals("SYSADMIN") ||
               upperName.equals("PUBLIC");
    }

    /**
     * Whether a role answers to that name: the role of exactly that name, or else of the name in upper case — the
     * engine's own Java callers name roles in any case. A statement's name reaches a role through
     * {@link #roleExistsExact}.
     */
    public boolean roleExists(final String name) {
        return name != null && (roles.containsKey(name) || roles.containsKey(name.toUpperCase(Locale.ROOT)));
    }

    /**
     * Whether the role of exactly that canonical name exists: a quoted name reaches no role of another case.
     *
     * @param name the role's canonical name
     * @return whether it exists
     */
    public boolean roleExistsExact(final String name) {
        return name != null && roles.containsKey(name);
    }

    /** The role answering to that name, as {@link #roleExists} finds it. */
    public Role getRole(final String name) {
        Role role = roles.get(name);
        if (role == null) {
            role = roles.get(name.toUpperCase(Locale.ROOT));
        }
        if (role == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Role", name));
        }
        return role;
    }

    /**
     * The role of exactly that canonical name, the one a statement names.
     *
     * @param name the role's canonical name
     * @return the role
     */
    public Role getRoleExact(final String name) {
        final Role role = roles.get(name);
        if (role == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Role", name));
        }
        return role;
    }

    /**
     * The name a role is kept under for a name of any case the engine's own Java callers hand in: the role's own
     * name when one answers to it, the name in upper case otherwise.
     *
     * @param name the name as handed in
     * @return the role's name
     */
    public String resolveRoleName(final String name) {
        if (name == null) {
            return null;
        }
        return roles.containsKey(name) ? name : name.toUpperCase(Locale.ROOT);
    }

    /** The organization's other accounts and this account's managed accounts. */
    public AccountDirectory getAccountDirectory() {
        return accountDirectory;
    }

    public List<Role> getAllRoles() {
        return new ArrayList<>(roles.values());
    }

    public void grantRoleToUser(final String roleName, final String userName) {
        final User user = getUser(userName);
        final Role role = getRoleExact(roleName);
        user.grantRole(role.getName(), currentRoleForOwner());
    }

    public void revokeRoleFromUser(final String roleName, final String userName) {
        final User user = getUser(userName);
        final Role role = getRoleExact(roleName);
        user.revokeRole(role.getName());
    }

    public void grantPrivilegeToRole(final String privilege, final String objectType, final String objectName, final String roleName) {
        grantPrivilegeToRole(privilege, objectType, objectName, roleName, null);
    }

    /**
     * Grant a privilege on an object to a role, recorded as granted by {@code grantor}. Live records a grant on an
     * object in its owner's name, whichever role runs the GRANT.
     *
     * @param grantor the object's owner, or null to record the current role
     */
    public void grantPrivilegeToRole(final String privilege, final String objectType, final String objectName,
                                     final String roleName, final String grantor) {
        final Role role = getRoleExact(roleName);
        final Privilege priv = Privilege.valueOf(privilege.toUpperCase(Locale.ROOT));
        role.grantPrivilege(objectType, objectName, priv, grantor != null ? grantor : currentRoleForOwner());
    }

    public void revokePrivilegeFromRole(final String privilege, final String objectType, final String objectName, final String roleName) {
        final Role role = getRoleExact(roleName);
        final Privilege priv = Privilege.valueOf(privilege.toUpperCase(Locale.ROOT));
        role.revokePrivilege(objectType, objectName, priv);
    }

    public void grantPrivilegeToUser(final String privilege, final String objectType, final String objectName, final String userName) {
        grantPrivilegeToUser(privilege, objectType, objectName, userName, null);
    }

    /**
     * Grant a privilege on an object to a user, recorded as granted by {@code grantor}.
     *
     * @param grantor the object's owner, or null to record the current role
     */
    public void grantPrivilegeToUser(final String privilege, final String objectType, final String objectName,
                                     final String userName, final String grantor) {
        final User user = getUser(userName);
        final Privilege priv = Privilege.valueOf(privilege.toUpperCase(Locale.ROOT));
        user.grantPrivilege(objectType, objectName, priv, grantor != null ? grantor : currentRoleForOwner());
    }

    public void revokePrivilegeFromUser(final String privilege, final String objectType, final String objectName, final String userName) {
        final User user = getUser(userName);
        final Privilege priv = Privilege.valueOf(privilege.toUpperCase(Locale.ROOT));
        user.revokePrivilege(objectType, objectName, priv);
    }

    public void grantColumnPrivilegeToRole(final String privilege, final String objectType, final String objectName,
                                           final String columnName, final String roleName) {
        final Role role = getRoleExact(roleName);
        final Privilege priv = Privilege.valueOf(privilege.toUpperCase(Locale.ROOT));
        role.grantColumnPrivilege(objectType, objectName, columnName, priv);
    }

    public void revokeColumnPrivilegeFromRole(final String privilege, final String objectType, final String objectName,
                                              final String columnName, final String roleName) {
        final Role role = getRoleExact(roleName);
        final Privilege priv = Privilege.valueOf(privilege.toUpperCase(Locale.ROOT));
        role.revokeColumnPrivilege(objectType, objectName, columnName, priv);
    }

    public void grantColumnPrivilegeToUser(final String privilege, final String objectType, final String objectName,
                                           final String columnName, final String userName) {
        final User user = getUser(userName);
        final Privilege priv = Privilege.valueOf(privilege.toUpperCase(Locale.ROOT));
        user.grantColumnPrivilege(objectType, objectName, columnName, priv);
    }

    public void revokeColumnPrivilegeFromUser(final String privilege, final String objectType, final String objectName,
                                              final String columnName, final String userName) {
        final User user = getUser(userName);
        final Privilege priv = Privilege.valueOf(privilege.toUpperCase(Locale.ROOT));
        user.revokeColumnPrivilege(objectType, objectName, columnName, priv);
    }

    public void grantRoleToRole(final String grantedRoleName, final String targetRoleName) {
        // The role that is to hold the grant is found first (live-verified).
        final Role targetRole = getRoleExact(targetRoleName);
        final Role grantedRole = getRoleExact(grantedRoleName);

        // Prevent circular grants
        if (hasCircularRoleGrant(grantedRoleName, targetRoleName)) {
            throw new RuntimeException("Cannot grant role: would create circular role hierarchy");
        }

        targetRole.grantRole(grantedRole.getName(), currentRoleForOwner());
    }

    public void revokeRoleFromRole(final String revokedRoleName, final String targetRoleName) {
        final Role targetRole = getRoleExact(targetRoleName);
        final Role revokedRole = getRoleExact(revokedRoleName);
        targetRole.revokeRole(revokedRole.getName());
    }

    private boolean hasCircularRoleGrant(final String grantedRoleName, final String targetRoleName) {
        // Check if granting would create a cycle
        final Set<String> visited = new HashSet<>();
        return checkCircular(grantedRoleName.toUpperCase(Locale.ROOT), targetRoleName.toUpperCase(Locale.ROOT), visited);
    }

    private boolean checkCircular(final String currentRole, final String targetRole, final Set<String> visited) {
        // Check if currentRole transitively contains targetRole in its hierarchy
        if (currentRole.equals(targetRole)) {
            return true;
        }

        // Avoid infinite loops
        if (visited.contains(currentRole)) {
            return false;
        }

        visited.add(currentRole);

        final Role currentRoleObj = roles.get(currentRole);
        if (currentRoleObj == null) {
            return false;
        }

        // Check all roles granted to currentRole
        for (final String childRole : currentRoleObj.getGrantedRoles()) {
            if (checkCircular(childRole, targetRole, visited)) {
                return true;
            }
        }

        return false;
    }

    // Rename operations
    /**
     * ALTER DATABASE … RENAME TO: the database answers to its new name, stored as resolved like a created
     * one's, so a rename that changes only case is a rename. The new name must be free exactly. A session
     * whose current database it was is left with no current database and no current schema (live-verified).
     * The caller moves the tables' rows to the new name.
     *
     * @param oldName the database's name, canonical
     * @param newName its new name, canonical
     */
    public void renameDatabase(final String oldName, final String newName) {
        final Database db = databaseExact(oldName);
        if (databases.containsKey(newName)) {
            throw new RuntimeException(alreadyExists(newName));
        }
        final String[] from = {db.getName()};
        StreamSources.bindAll(this);
        final ForeignKeyParents parents = ForeignKeyParents.bind(this);
        databases.remove(db.getName());
        db.rename(newName);
        databases.put(newName, db);
        StreamSources.respellAll(this);
        parents.respell(this);
        GrantNames.containerRenamed(this, from, new String[] {newName});
        // Its policies' attachments, in this database or any other, name them under the new name.
        PolicyReferences.repoint(allSchemas(), null, from, new String[] {newName});
        if (oldName.equals(getCurrentDatabase())) {
            setCurrentDatabaseName(null);
            setCurrentSchemaName(null);
        }
    }

    public void renameTable(final String qualifiedName, final String newName) {
        final Table table = resolveTable(qualifiedName);
        final Schema schema = resolveSchemaForTable(qualifiedName);
        // The taken-name check comes FIRST, so a refused rename leaves the catalog untouched —
        // and refuses with the account's own object-exists sentence, which a rename gives PLAIN:
        // the "already exists as KIND" form belongs to a create (live-verified).
        if (schema.relationKindOf(newName) != null) {
            throw new NameTakenException(alreadyExists(newName));
        }
        // Its streams stay on it (StreamSources): bound while their recorded names still find it, re-spelled after.
        StreamSources.bindAll(this);
        final ForeignKeyParents parents = ForeignKeyParents.bind(this);
        final String was = schema.qualifiedName(table.getName());
        schema.dropTable(table.getName());
        table.rename(newName);
        schema.addTable(table);
        StreamSources.respellAll(this);
        parents.respell(this);
        // Its privileges go with it (GrantNames).
        GrantNames.renamed(this, "TABLE", was, schema.qualifiedName(table.getName()));
    }

    /**
     * ALTER TABLE a SWAP WITH b — the two tables exchange names in place, so each keeps its own
     * columns, constraints and comment but answers to the other's name (and schema). Both must
     * exist; resolution failures throw before anything mutates.
     */
    /**
     * ALTER TABLE a SWAP WITH b: the two tables trade names. A name the statement gives a view or another relation is
     * refused by its kind; a table swapped with itself, however either name is spelled, stays as it was
     * (live-verified).
     *
     * @return whether two tables traded names
     */
    public boolean swapTables(final String nameA, final String nameB) {
        final Table a = resolveTable(nameA);
        refuseOtherRelationKind(nameB);
        final Table b = resolveTable(nameB);
        if (a == b) {
            return false;
        }
        if (a.isTemporary() != b.isTemporary()) {
            // Live-verified, and so for a temporary table that hides a permanent one of its name.
            throw new RuntimeException("Swapping of a temporary table with another non-temporary table is not allowed.");
        }
        final Schema schemaA = resolveSchemaForTable(nameA);
        final Schema schemaB = resolveSchemaForTable(nameB);
        final String bareA = a.getName();
        final String bareB = b.getName();
        StreamSources.bindAll(this);
        final ForeignKeyParents parents = ForeignKeyParents.bind(this);
        final String fullA = schemaA.qualifiedName(bareA);
        final String fullB = schemaB.qualifiedName(bareB);
        schemaA.dropTable(bareA);
        schemaB.dropTable(bareB);
        a.rename(bareB);
        b.rename(bareA);
        schemaB.addTable(a);
        schemaA.addTable(b);
        // Each table's streams and privileges follow the table, not the name it gave up.
        StreamSources.respellAll(this);
        parents.respell(this);
        GrantNames.swapped(this, "TABLE", fullA, fullB);
        return true;
    }

    /**
     * Refuse a name a relation other than a table holds, as {@code Object found is of type 'VIEW', not specified type
     * 'TABLE'.} (live-verified for SWAP WITH).
     *
     * @param name the name as the statement writes it
     */
    public void refuseOtherRelationKind(final String name) {
        // A name the session cannot place, or whose database or schema is missing, is refused where the statement
        // resolves the table, in its own words.
        final String[] parts = QualifiedName.parse(name).parts();
        if (parts.length < 3 && getCurrentDatabase() == null || parts.length == 1 && getCurrentSchema() == null) {
            return;
        }
        final Schema schema;
        try {
            schema = resolveSchemaForTable(name);
        } catch (final RuntimeException unplaced) {
            return;
        }
        if (schema == null) {
            return;
        }
        final RelationKind kind = schema.relationKindOf(objectName(name));
        if (kind != null && kind != RelationKind.TABLE) {
            throw new RuntimeException(SqlCompilationError.objectOfOtherType(kind.spelling(), "TABLE"));
        }
    }

    /**
     * Move a table to a (possibly different) schema/database, optionally renaming it — the qualified-target
     * form of {@code ALTER TABLE ... RENAME TO db.schema.name}. The destination database and schema must
     * already exist (else {@link #getDatabase}/{@code getSchema} throw), and no table of the new name may
     * already exist there. Membership is checked before any mutation, so a rejected move leaves the catalog
     * untouched.
     */
    public void moveTable(final String sourceQualifiedName, final String targetDatabase,
                          final String targetSchema, final String newName) {
        final Table table = resolveTable(sourceQualifiedName);
        final Schema source = resolveSchemaForTable(sourceQualifiedName);
        final Schema target = getDatabase(targetDatabase).getSchema(targetSchema);
        target.requireCreatable(Privilege.CREATE_TABLE);
        if (target.relationKindOf(newName) != null) {
            // The account's own object-exists sentence, naming the new name as written (live-verified).
            throw new NameTakenException(alreadyExists(newName));
        }
        StreamSources.bindAll(this);
        final ForeignKeyParents parents = ForeignKeyParents.bind(this);
        final String was = source.qualifiedName(table.getName());
        source.dropTable(table.getName());
        table.rename(newName);
        target.addTable(table);
        StreamSources.respellAll(this);
        parents.respell(this);
        GrantNames.renamed(this, "TABLE", was, target.qualifiedName(table.getName()));
    }

    /**
     * Move a view to a (possibly different) schema/database under a new name — ALTER VIEW's RENAME TO,
     * whose new name the session's context places. The destination must exist and hold no view of
     * that name, both checked before anything changes; a move within the view's own schema is a rename.
     */
    /**
     * ALTER VIEW … RENAME TO: the view renamed into the schema its new name resolves to, moving there when that
     * is another one. A missing schema or database is refused by name, and a name any relation there holds -
     * the view's own included - is refused as the statement wrote it, {@code Object 'OTHER.OTAKEN' already
     * exists.}, before anything moves (live-verified).
     *
     * @param sourceQualifiedName the view as the statement names it
     * @param targetDatabase      the database the new name resolves to
     * @param targetSchema        the schema the new name resolves to
     * @param written             the new name's canonical parts as written
     */
    public void moveView(final String sourceQualifiedName, final String targetDatabase,
                         final String targetSchema, final String[] written) {
        final View view = resolveView(sourceQualifiedName);
        final Schema source = resolveSchemaForTable(sourceQualifiedName);
        final Schema target = databaseExact(targetDatabase).schemaExact(targetSchema);
        target.requireCreatable(Privilege.CREATE_VIEW);
        final String newName = written[written.length - 1];
        if (target.relationKindOf(newName) != null) {
            final StringBuilder spelled = new StringBuilder();
            for (final String part : written) {
                spelled.append(spelled.length() > 0 ? "." : "").append(SqlIdentifiers.spellCanonical(part));
            }
            throw new RuntimeException(SqlCompilationError.of("Object '" + spelled + "' already exists."));
        }
        final String was = source.qualifiedName(view.getName());
        source.dropView(view.getName());
        view.rename(newName);
        target.addView(view);
        // Its privileges go with it (GrantNames).
        GrantNames.renamed(this, "VIEW", was, target.qualifiedName(view.getName()));
    }

    public void renameUser(final String oldName, final String newName) {
        final User user = getUser(oldName);
        if (users.containsKey(newName.toUpperCase(Locale.ROOT))) {
            throw new RuntimeException("User already exists: " + newName);
        }
        users.remove(oldName.toUpperCase(Locale.ROOT));
        user.rename(newName);
        users.put(newName.toUpperCase(Locale.ROOT), user);
    }

    public void renameRole(final String oldName, final String newName) {
        final Role role = getRole(oldName);
        if (roles.containsKey(newName.toUpperCase(Locale.ROOT))) {
            throw new RuntimeException("Role already exists: " + newName);
        }
        final String heldAs = role.getName();
        roles.remove(oldName.toUpperCase(Locale.ROOT));
        role.rename(newName);
        roles.put(newName.toUpperCase(Locale.ROOT), role);
        // The role goes on owning its objects under its new name.
        RoleOwnership.handOver(this, heldAs, role.getName());
    }

    public void renameWarehouse(final String oldName, final String newName) {
        final Warehouse warehouse = getWarehouse(oldName);
        if (warehouses.containsKey(newName.toUpperCase(Locale.ROOT))) {
            throw new RuntimeException("Warehouse already exists: " + newName);
        }
        final String grantedBefore = warehouse.getName();
        warehouses.remove(oldName.toUpperCase(Locale.ROOT));
        warehouse.rename(newName);
        warehouses.put(newName.toUpperCase(Locale.ROOT), warehouse);
        // A renamed warehouse is not followed: the session that had it as current has none (live-verified).
        forgetCurrentWarehouse(oldName);
        GrantNames.renamed(this, "WAREHOUSE", grantedBefore, warehouse.getName());
    }

    private Schema resolveSchemaForTable(final String qualifiedName) {
        final String[] parts = QualifiedName.parse(qualifiedName).parts();
        if (parts.length == 1) {
            return getDatabase(getCurrentDatabase()).getSchema(getCurrentSchema());
        } else if (parts.length == 2) {
            return getDatabase(getCurrentDatabase()).getSchema(parts[0]);
        } else {
            return getDatabase(parts[0]).getSchema(parts[1]);
        }
    }
}
