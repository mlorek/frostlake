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
        schemas.put("PUBLIC", new Schema("PUBLIC"));

        // Create INFORMATION_SCHEMA - a special schema that exists in every database
        Schema infoSchema = new Schema("INFORMATION_SCHEMA");
        schemas.put("INFORMATION_SCHEMA", infoSchema);

        // Add system views to INFORMATION_SCHEMA
        // These are special views intercepted by QueryExecutor
        for (final String v : new String[]{
            "DATABASES", "SCHEMATA", "TABLES", "COLUMNS", "VIEWS",
            "TABLE_CONSTRAINTS", "REFERENTIAL_CONSTRAINTS",
            "PROCEDURES", "FUNCTIONS", "SEQUENCES",
            "STAGES", "PIPES", "STREAMS", "TASKS",
            "ENABLED_ROLES", "APPLICABLE_ROLES",
            "TABLE_PRIVILEGES", "OBJECT_PRIVILEGES", "USAGE_PRIVILEGES",
            "TAGS", "TAG_REFERENCES",
            "INDEXES", "INDEX_COLUMNS", "DYNAMIC_TABLES"
        }) {
            infoSchema.addView(new View(v, "/* system view */"));
        }
    }

    public void addSchema(final Schema schema) {
        String upperName = schema.getName().toUpperCase();
        if (schemas.containsKey(upperName)) {
            throw new RuntimeException("Schema already exists: " + schema.getName());
        }
        schemas.put(upperName, schema);
    }

    public void dropSchema(final String name, final boolean cascade) {
        String upperName = name.toUpperCase();
        if ("PUBLIC".equals(upperName)) {
            throw new RuntimeException("Cannot drop PUBLIC schema");
        }
        if ("INFORMATION_SCHEMA".equals(upperName)) {
            throw new RuntimeException("Cannot drop INFORMATION_SCHEMA schema");
        }
        if (!schemas.containsKey(upperName)) {
            throw new RuntimeException("Schema does not exist: " + name);
        }
        Schema schema = schemas.get(upperName);
        if (!cascade && (!schema.getTables().isEmpty() || !schema.getViews().isEmpty())) {
            throw new RuntimeException("Schema is not empty. Use CASCADE to drop.");
        }
        schemas.remove(upperName);
    }

    public Schema getSchema(final String name) {
        Schema schema = schemas.get(name.toUpperCase());
        if (schema == null) {
            throw new RuntimeException("Schema does not exist: " + name);
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
                    View clonedView = new View(view.getName(), view.getDefinition());
                    clonedView.setComment(view.getComment());
                    targetPublic.addView(clonedView);
                }
            } else {
                // Clone user-created schemas
                Schema clonedSchema = schema.clone();
                clonedDb.schemas.put(schemaName.toUpperCase(), clonedSchema);
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
