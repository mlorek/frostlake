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
import dev.frostlake.metastore.SqlObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Database extends SqlObject {

    private final Map<String, Schema> schemas;
    private boolean readOnly = false;

    public Database(final String name) {
        super(name);
        this.schemas = new ConcurrentHashMap<>();

        // Create default PUBLIC schema
        registerSchema("PUBLIC", new Schema("PUBLIC"));

        // Create INFORMATION_SCHEMA - a special schema that exists in every database. Nobody owns
        // it: a real account reports its owner as absent, where PUBLIC and every created schema
        // name a role. SHOW SCHEMAS spells that absence as an empty string and
        // INFORMATION_SCHEMA.SCHEMATA as NULL, which is the usual split between the two surfaces.
        Schema infoSchema = new Schema("INFORMATION_SCHEMA");
        infoSchema.setOwner(null);
        registerSchema("INFORMATION_SCHEMA", infoSchema);

        // The INFORMATION_SCHEMA views a real account exposes, listed so they appear in
        // INFORMATION_SCHEMA.TABLES / VIEWS and SHOW VIEWS exactly as they do live (this list and
        // the reader in QueryExecutor.executeSystemViewIfApplicable must name the same set — a
        // name listed here but not routed there would resolve to this placeholder definition).
        // STREAMS, TASKS and TAGS are deliberately absent: a real account has no such views.
        for (final String v : new String[]{
            "APPLICABLE_ROLES", "APPLICATION_CONFIGURATIONS", "APPLICATION_SPECIFICATIONS", "BACKUPS",
            "BACKUP_POLICIES", "BACKUP_SETS", "CHECK_CONSTRAINTS", "CLASSES", "CLASS_INSTANCES",
            "CLASS_INSTANCE_FUNCTIONS", "CLASS_INSTANCE_PROCEDURES", "COLUMNS",
            "CORTEX_SEARCH_SERVICES", "CORTEX_SEARCH_SERVICE_SCORING_PROFILES",
            "CURRENT_PACKAGES_POLICY", "DATABASES", "ELEMENT_TYPES", "ENABLED_ROLES", "EVENT_TABLES",
            "EXTERNAL_TABLES", "FIELDS", "FILE_FORMATS", "FUNCTIONS", "GIT_REPOSITORIES",
            "HYBRID_TABLES", "INDEXES", "INDEX_COLUMNS", "INFORMATION_SCHEMA_CATALOG_NAME", "LISTINGS",
            "LOAD_HISTORY", "MODEL_VERSIONS", "NOTEBOOKS", "OBJECT_PRIVILEGES", "PACKAGES", "PIPES",
            "PROCEDURES", "REFERENTIAL_CONSTRAINTS", "REPLICATION_DATABASES", "REPLICATION_GROUPS",
            "SCHEMATA", "SEMANTIC_DIMENSIONS", "SEMANTIC_FACTS", "SEMANTIC_METRICS",
            "SEMANTIC_RELATIONSHIPS", "SEMANTIC_TABLES", "SEMANTIC_VARIABLES", "SEMANTIC_VIEWS",
            "SEQUENCES", "SERVICES", "SHARES", "SNAPSHOTS", "SNAPSHOT_POLICIES", "SNAPSHOT_SETS",
            "STAGES", "STREAMLITS", "TABLES", "TABLE_CONSTRAINTS", "TABLE_PRIVILEGES",
            "TABLE_STORAGE_METRICS", "TYPES", "USAGE_PRIVILEGES", "VIEWS"
        }) {
            infoSchema.addView(new View(v, "/* system view */"));
        }
    }

    /** Register a schema under this database, stamping it so it can spell its members' full names. */
    private void registerSchema(final String key, final Schema schema) {
        schema.setDatabaseName(getName());
        schemas.put(key, schema);
    }

    public void addSchema(final Schema schema) {
        String upperName = schema.getName().toUpperCase();
        if (schemas.containsKey(upperName)) {
            throw new RuntimeException("Schema already exists: " + schema.getName());
        }
        registerSchema(upperName, schema);
    }

    public void dropSchema(final String name, final boolean cascade) {
        String upperName = name.toUpperCase();
        if ("INFORMATION_SCHEMA".equals(upperName)) {
            throw new RuntimeException("Cannot drop INFORMATION_SCHEMA schema");
        }
        if (!schemas.containsKey(upperName)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Schema", getName() + "." + name));
        }
        // Live-verified Snowflake semantics: PUBLIC is droppable like any schema, and
        // DROP SCHEMA ... RESTRICT drops a NON-EMPTY schema too (the callers snapshot and
        // release the contained tables' storage before this removal either way).
        schemas.remove(upperName);
    }

    /**
     * A schema by name, matched ignoring case — the INTERNAL Java API. SQL resolution goes through
     * {@link #schemaExact}.
     */
    public Schema getSchema(final String name) {
        Schema schema = schemas.get(name.toUpperCase());
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
        final Schema schema = schemas.get(name.toUpperCase());
        if (schema == null || !schema.getName().equals(name)) {
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
        Database clonedDb = new Database(newName);
        clonedDb.setComment(this.getComment());

        // Clone all schemas
        for (final Schema schema : schemas.values()) {
            String schemaName = schema.getName();
            if ("INFORMATION_SCHEMA".equals(schemaName)) {
                // Skip INFORMATION_SCHEMA (it's a system schema with views that are auto-created)
                continue;
            } else if ("PUBLIC".equals(schemaName)) {
                // Clone PUBLIC schema content into the existing PUBLIC schema
                Schema sourcePublic = schema;
                Schema targetPublic = clonedDb.getSchema("PUBLIC");

                // Clone tables from source PUBLIC to target PUBLIC
                for (final Table table : sourcePublic.getTables()) {
                    List<TableColumn> clonedColumns = new ArrayList<>();
                    for (final TableColumn col : table.getColumns()) {
                        TableColumn clonedCol = new TableColumn(
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
                    Table clonedTable = new Table(table.getName(), clonedColumns, table.isTemporary(), table.isTransient());
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
                Schema clonedSchema = schema.clone();
                clonedDb.registerSchema(schemaName.toUpperCase(), clonedSchema);
            }
        }

        return clonedDb;
    }

    /**
     * Clone a schema within this database
     */
    public Schema cloneSchema(final String sourceName, final String targetName) {
        Schema sourceSchema = getSchema(sourceName);
        Schema clonedSchema = sourceSchema.clone();
        clonedSchema.rename(targetName);
        addSchema(clonedSchema);
        return clonedSchema;
    }

    /**
     * Clone a schema from this database to another database
     */
    public Schema cloneSchemaTo(final String sourceName, final Database targetDb, final String targetName) {
        Schema sourceSchema = getSchema(sourceName);
        Schema clonedSchema = sourceSchema.clone();
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
}
