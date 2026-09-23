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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.NameKeys;
import dev.frostlake.metastore.SqlObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Database extends SqlObject {

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


    private final Map<String, Schema> schemas;
    /** The database's own roles, by name, in the order they were created. */
    private final Map<String, DatabaseRole> databaseRoles = new LinkedHashMap<>();
    private boolean readOnly = false;

    /** TRANSIENT, as written on the CREATE; every schema inside inherits it. */
    private boolean transientObject;

    public boolean isTransientObject() {
        return transientObject;
    }

    /** The parameters the database sets on itself; the retention is kept apart, above. */
    private final ObjectParameters parameters = new ObjectParameters();

    /** The parameters the database sets on itself (its schemas inherit them). */
    public ObjectParameters getParameters() {
        return parameters;
    }

    public void setTransientObject(final boolean value) {
        this.transientObject = value;
    }

    public Database(final String name) {
        super(name);
        this.schemas = new ConcurrentHashMap<>();

        // Create default PUBLIC schema
        registerSchema("PUBLIC", new Schema("PUBLIC"));

        // Create INFORMATION_SCHEMA - a special schema that exists in every database. Nobody owns
        // it: a real account reports its owner as absent, where PUBLIC and every created schema
        // name a role. SHOW SCHEMAS spells that absence as an empty string and
        // INFORMATION_SCHEMA.SCHEMATA as NULL, which is the usual split between the two surfaces.
        final Schema infoSchema = new Schema("INFORMATION_SCHEMA");
        infoSchema.setOwner(null);
        // Its comment is fixed and account-wide — every database's INFORMATION_SCHEMA carries this
        // exact sentence, where a created schema carries none (live-verified).
        infoSchema.setComment("Views describing the contents of schemas in this database");
        registerSchema("INFORMATION_SCHEMA", infoSchema);

        // The INFORMATION_SCHEMA views a real account exposes, listed so they appear in
        // INFORMATION_SCHEMA.TABLES / VIEWS, SHOW VIEWS and SHOW OBJECTS exactly as they do live.
        InformationSchemaViews.seed(infoSchema);
    }

    /** A renamed database re-stamps its schemas, which spell their members' full names under it. */
    @Override
    public void rename(final String newName) {
        super.rename(newName);
        for (final Schema schema : schemas.values()) {
            schema.setDatabaseName(newName);
        }
    }

    /** Register a schema under this database, stamping it so it can spell its members' full names. */
    private void registerSchema(final String key, final Schema schema) {
        schema.setDatabaseName(getName());
        schemas.put(key, schema);
    }

    public void addSchema(final Schema schema) {
        final String name = schema.getName();
        if (schemas.containsKey(name)) {
            // Live names no KIND here — every object that finds its name taken gets the same sentence, with
            // the name quoted only where it has to be.
            throw new RuntimeException(SqlCompilationError.of(
                "Object '" + SqlIdentifiers.spellCanonical(name) + "' already exists."));
        }
        registerSchema(name, schema);
    }

    /**
     * Takes a schema out of this database for a rename or a move, without any of a drop's checks.
     *
     * @param name the schema's name
     * @return the schema
     */
    public Schema detachSchema(final String name) {
        return schemas.remove(NameKeys.keyFor(schemas, name));
    }

    /**
     * Puts a renamed or moved schema into this database under its (new) name, stamped with this database.
     *
     * @param schema the schema
     */
    public void attachSchema(final Schema schema) {
        registerSchema(schema.getName(), schema);
    }

    public void dropSchema(final String name, final boolean cascade) {
        final String key = NameKeys.keyFor(schemas, name);
        if ("INFORMATION_SCHEMA".equals(key)) {
            throw new RuntimeException("Cannot drop INFORMATION_SCHEMA schema");
        }
        if (!schemas.containsKey(key)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Schema", getName() + "." + name));
        }
        // Live-verified Snowflake semantics: PUBLIC is droppable like any schema, and
        // DROP SCHEMA ... RESTRICT drops a NON-EMPTY schema too (the callers snapshot and
        // release the contained tables' storage before this removal either way).
        schemas.remove(key);
    }

    /**
     * Whether a schema of this name exists, matched as {@link #getSchema} matches — the question it
     * cannot answer, because it THROWS for a name that is not there rather than returning null. Two
     * callers were written as {@code getSchema("PUBLIC") != null}, which reads like a check and is one
     * only for databases that happen to have a PUBLIC.
     *
     * @param name the schema name
     * @return whether it exists
     */
    public boolean hasSchema(final String name) {
        return schemas.containsKey(NameKeys.keyFor(schemas, name));
    }

    /**
     * Whether a schema of EXACTLY this name exists — the question a SQL statement asks, since a schema
     * created as {@code "ss"} and one created as {@code SS} are two schemas (live-verified).
     *
     * @param name the canonical schema name
     * @return whether it exists
     */
    public boolean hasSchemaExact(final String name) {
        return schemas.containsKey(name);
    }

    /**
     * A schema by name, matched exactly or else by the one schema whose name matches ignoring case
     * ({@link NameKeys#keyFor}) — the INTERNAL Java API. SQL resolution goes through {@link #schemaExact}.
     */
    public Schema getSchema(final String name) {
        final Schema schema = schemas.get(NameKeys.keyFor(schemas, name));
        if (schema == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Schema", getName() + "." + name));
        }
        return schema;
    }

    /**
     * The schema a SQL reference names, matched EXACTLY — live answers
     * {@code Schema 'DB.MIXEDSCH' does not exist or not authorized.} for an unquoted reference to a schema
     * created as {@code "mixedSch"}, echoing each qualifier as the reference resolved it.
     */
    public Schema schemaExact(final String name) {
        final Schema schema = schemas.get(name);
        if (schema == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Schema", getName() + "." + name));
        }
        return schema;
    }

    public List<Schema> getAllSchemas() {
        return new ArrayList<>(schemas.values());
    }

    /**
     * Clone this database with a new name
     */
    public Database clone(final String newName) {
        final Database clonedDb = new Database(newName);
        clonedDb.setComment(this.getComment());
        clonedDb.getParameters().copyFrom(parameters);
        clonedDb.setDataRetentionTimeInDays(dataRetentionTimeInDays);

        // Clone all schemas
        for (final Schema schema : schemas.values()) {
            final String schemaName = schema.getName();
            if ("INFORMATION_SCHEMA".equals(schemaName)) {
                // Skip INFORMATION_SCHEMA (it's a system schema with views that are auto-created)
                continue;
            } else if ("PUBLIC".equals(schemaName)) {
                // Clone PUBLIC schema content into the existing PUBLIC schema
                final Schema sourcePublic = schema;
                final Schema targetPublic = clonedDb.getSchema("PUBLIC");

                // Clone tables from source PUBLIC to target PUBLIC
                for (final Table table : sourcePublic.getNonTemporaryTables()) {
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
                        clonedColumns.add(clonedCol);
                    }
                    final Table clonedTable = new Table(table.getName(), clonedColumns, table.isTemporary(), table.isTransient());
                    clonedTable.setComment(table.getComment());
                    clonedTable.setClusterKeys(table.getClusterKeys());
                    targetPublic.addTable(clonedTable);
                }

                // Clone views from source PUBLIC to target PUBLIC
                for (final View view : sourcePublic.getViews()) {
                    targetPublic.addView(view.copy());
                }
            } else {
                // Clone user-created schemas
                final Schema clonedSchema = schema.clone();
                clonedDb.registerSchema(schemaName, clonedSchema);
            }
        }

        return clonedDb;
    }

    /**
     * Clone a schema within this database
     */
    public Schema cloneSchema(final String sourceName, final String targetName) {
        final Schema sourceSchema = schemaExact(sourceName);
        final Schema clonedSchema = sourceSchema.clone();
        clonedSchema.rename(targetName);
        addSchema(clonedSchema);
        return clonedSchema;
    }

    /**
     * Clone a schema from this database to another database
     */
    public Schema cloneSchemaTo(final String sourceName, final Database targetDb, final String targetName) {
        final Schema sourceSchema = schemaExact(sourceName);
        final Schema clonedSchema = sourceSchema.clone();
        clonedSchema.rename(targetName);
        targetDb.addSchema(clonedSchema);
        return clonedSchema;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override
    public String getObjectType() {
        return "DATABASE";
    }

    /** The database role of that name, or null. */
    public synchronized DatabaseRole getDatabaseRole(final String roleName) {
        return databaseRoles.get(roleName);
    }

    /** Every database role of this database, in the order they were created. */
    public synchronized List<DatabaseRole> getDatabaseRoles() {
        return new ArrayList<>(databaseRoles.values());
    }

    /** Adds a database role, replacing one of the same name. */
    public synchronized void putDatabaseRole(final DatabaseRole role) {
        databaseRoles.put(role.getName(), role);
    }

    /** Removes a database role, answering it, or null when there is none of that name. */
    public synchronized DatabaseRole removeDatabaseRole(final String roleName) {
        return databaseRoles.remove(roleName);
    }
}
