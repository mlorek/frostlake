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
package dev.frostlake.functions.table;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.Taggable;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DatabaseRole;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurityObjectKind;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The INFORMATION_SCHEMA.TAG_REFERENCES(object_name, object_domain) TABLE FUNCTION — the tags associated with
 * one object, as {@code SELECT … FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('db.schema.t', 'TABLE'))}.
 *
 * <p>Both arguments are required. The object name is read as an identifier is: an unquoted part folds to upper
 * case and a double-quoted one is kept as written ({@code '"MyWh"'}); a name with fewer parts resolves against
 * the session's current database and schema. A real account takes TABLE for every table-like object — passing
 * VIEW is refused with "Please use object type TABLE for all kinds of table-like objects" — and takes COLUMN
 * with a name whose last part is the column.
 *
 * <p>The rows follow tag inheritance through the securable object hierarchy: an object reports the tags set on
 * it ({@code APPLY_METHOD} {@code MANUAL}) and those it inherits from its table, schema and database
 * ({@code INHERITED}), each row's {@code LEVEL} naming the domain the tag is set on. A tag set at several levels
 * is reported once, from the level nearest the object, whose value overrides the others.
 */
public class TagReferencesFunction extends TableFunction {

    /** Live's column order. */
    private static final String[] COLUMN_NAMES = {
        "TAG_DATABASE", "TAG_SCHEMA", "TAG_NAME", "TAG_VALUE", "LEVEL", "OBJECT_DATABASE",
        "OBJECT_SCHEMA", "OBJECT_NAME", "DOMAIN", "COLUMN_NAME", "APPLY_METHOD"
    };

    /** A tag set on the object itself. */
    private static final String MANUAL = "MANUAL";

    /** A tag the object inherits from an object above it. */
    private static final String INHERITED = "INHERITED";

    /**
     * The documented domains, and PIPE, which the account accepts although its documentation does not list it; one
     * this function resolves no object for answers no rows.
     */
    private static final Set<String> DOMAINS = new HashSet<String>(Arrays.asList(
        "ACCOUNT", "ALERT", "BACKUP POLICY", "BACKUP SET", "COLUMN", "COMPUTE POOL", "CORTEX AGENT",
        "CORTEX SEARCH SERVICE", "DATABASE", "DATABASE ROLE", "FAILOVER GROUP", "FUNCTION", "INTEGRATION",
        "INSTANCE", "NETWORK POLICY", "PIPE", "PROCEDURE", "REPLICATION GROUP", "ROLE", "SCHEMA", "SHARE",
        "SNAPSHOT POLICY", "SNAPSHOT SET", "SNOWFLAKE INTELLIGENCE", "STAGE", "STREAM", "TABLE", "TASK", "USER",
        "WAREHOUSE"));

    private final Catalog catalog;

    public TagReferencesFunction(final Catalog catalog) {
        super("TAG_REFERENCES");
        this.catalog = catalog;
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // Validated in execute, where both positional arguments are in hand.
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        throw new RuntimeException("TAG_REFERENCES requires an object name and an object domain");
    }

    @Override
    public ResultSet execute(final List<Object> positionalArgs) {
        if (positionalArgs == null || positionalArgs.size() != 2
            || positionalArgs.get(0) == null || positionalArgs.get(1) == null) {
            throw new RuntimeException("TAG_REFERENCES requires an object name and an object domain");
        }
        final String domain = positionalArgs.get(1).toString().trim().toUpperCase(Locale.ROOT);
        final String[] parts = SqlIdentifiers.canonicalTextParts(positionalArgs.get(0).toString());
        // A refusal names the object as resolved, each part quoted only where it has to be.
        final String objectName = QualifiedName.join(parts);
        final List<Row> rows = new ArrayList<Row>();
        if ("COLUMN".equals(domain)) {
            addColumnTags(parts, objectName, rows);
        } else if ("TABLE".equals(domain)) {
            addTableTags(parts, objectName, rows);
        } else if ("SCHEMA".equals(domain)) {
            addSchemaTags(parts, objectName, rows);
        } else if ("DATABASE".equals(domain)) {
            addDatabaseTags(parts, objectName, rows);
        } else if ("ALERT".equals(domain) || "STAGE".equals(domain) || "STREAM".equals(domain)
            || "TASK".equals(domain) || "PIPE".equals(domain) || "FUNCTION".equals(domain)
            || "PROCEDURE".equals(domain) || "PASSWORD POLICY".equals(domain)) {
            addSchemaObjectTags(domain, parts, objectName, rows);
        } else if ("WAREHOUSE".equals(domain) || "COMPUTE POOL".equals(domain) || "USER".equals(domain)
            || "ROLE".equals(domain) || "NETWORK POLICY".equals(domain) || "INTEGRATION".equals(domain)) {
            addAccountObjectTags(domain, parts, objectName, rows);
        } else if ("DATABASE ROLE".equals(domain)) {
            addDatabaseRoleTags(parts, objectName, rows);
        } else if ("VIEW".equals(domain) || "MATERIALIZED VIEW".equals(domain)) {
            throw new RuntimeException("Invalid value " + domain + " for argument OBJECT_TYPE. "
                + "Please use object type TABLE for all kinds of table-like objects.");
        } else if (!DOMAINS.contains(domain)) {
            throw new RuntimeException("Unknown domain: " + domain + ".");
        }
        final List<ResultSetColumn> columns = new ArrayList<ResultSetColumn>();
        for (final String name : COLUMN_NAMES) {
            columns.add(new ResultSetColumn(name, StringType.VARCHAR));
        }
        return new ResultSet(columns, rows);
    }

    /** Tags on a table-like object, and those it inherits from its schema and database. */
    private void addTableTags(final String[] written, final String objectName, final List<Row> rows) {
        final String[] name = qualify(written, 3, "Table", objectName);
        final Schema schema = resolveSchema(name[0], name[1], "Table", objectName);
        final Taggable table = tableLike(schema, name[2]);
        if (table == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Table", objectName));
        }
        addLineage(rows, new Taggable[] {table, schema, catalog.getDatabase(name[0])},
            new String[] {"TABLE", "SCHEMA", "DATABASE"}, name[0], name[1], name[2], "TABLE", null);
    }

    /** Tags on a schema, named as {@code db.schema} or bare. Live leaves OBJECT_SCHEMA null here. */
    private void addSchemaTags(final String[] written, final String objectName, final List<Row> rows) {
        final String[] name = qualify(written, 2, "Schema", objectName);
        final Schema schema = resolveSchema(name[0], name[1], "Schema", objectName);
        addLineage(rows, new Taggable[] {schema, catalog.getDatabase(name[0])}, new String[] {"SCHEMA", "DATABASE"},
            name[0], null, name[1], "SCHEMA", null);
    }

    /** Tags on a database. Live leaves both OBJECT_DATABASE and OBJECT_SCHEMA null here. */
    private void addDatabaseTags(final String[] written, final String objectName, final List<Row> rows) {
        final String simpleName = written[written.length - 1];
        final Database database = catalog.getDatabase(simpleName);
        if (written.length != 1 || database == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Database", objectName));
        }
        addLineage(rows, new Taggable[] {database}, new String[] {"DATABASE"}, null, null, simpleName, "DATABASE",
            null);
    }

    /** Tags on one column, and those it inherits from its table, schema and database. */
    private void addColumnTags(final String[] written, final String objectName, final List<Row> rows) {
        if (written.length < 2) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Column", objectName));
        }
        final String[] name = qualify(written, 4, "Table", objectName);
        final Schema schema = resolveSchema(name[0], name[1], "Table", objectName);
        if (!schema.hasTable(name[2])) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Table", objectName));
        }
        final Table table = schema.getTable(name[2]);
        for (final TableColumn column : table.getColumns()) {
            if (name[3].equalsIgnoreCase(column.getName())) {
                addLineage(rows, new Taggable[] {column, table, schema, catalog.getDatabase(name[0])},
                    new String[] {"COLUMN", "TABLE", "SCHEMA", "DATABASE"}, name[0], name[1], name[2], "COLUMN",
                    column.getName());
                return;
            }
        }
        throw new RuntimeException(SqlCompilationError.doesNotExist("Column", name[3]));
    }

    /** Tags on an object that lives in a schema, and those it inherits from its schema and database. */
    private void addSchemaObjectTags(final String domain, final String[] written, final String objectName,
                                     final List<Row> rows) {
        final String kind = domain.charAt(0) + domain.substring(1).toLowerCase(Locale.ROOT);
        final String[] name = qualify(written, 3, kind, objectName);
        final Schema schema = resolveSchema(name[0], name[1], kind, objectName);
        final Object found = schemaObject(schema, domain, name[2]);
        if (found == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist(kind, objectName));
        }
        final Taggable object = found instanceof Taggable ? (Taggable) found : null;
        addLineage(rows, new Taggable[] {object, schema, catalog.getDatabase(name[0])},
            new String[] {domain, "SCHEMA", "DATABASE"}, name[0], name[1], name[2], domain, null);
    }

    /** Tags on an object that lives in the account, which inherits nothing Frostlake models. */
    private void addAccountObjectTags(final String domain, final String[] written, final String objectName,
                                      final List<Row> rows) {
        final String kind = domain.charAt(0) + domain.substring(1).toLowerCase(Locale.ROOT);
        final String simpleName = written[written.length - 1];
        Object found = null;
        if (written.length == 1) {
            try {
                if ("WAREHOUSE".equals(domain)) {
                    found = catalog.getWarehouse(simpleName);
                } else if ("COMPUTE POOL".equals(domain)) {
                    found = catalog.getComputePool(simpleName);
                } else if ("USER".equals(domain)) {
                    found = catalog.getUser(simpleName);
                } else if ("NETWORK POLICY".equals(domain)) {
                    found = catalog.getSecurityObjects().get(SecurityObjectKind.NETWORK_POLICY, simpleName);
                } else if ("INTEGRATION".equals(domain)) {
                    found = catalog.getIntegrations().find(simpleName);
                } else {
                    found = catalog.getRole(simpleName);
                }
            } catch (final RuntimeException missing) {
                found = null;
            }
        }
        if (found == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist(kind, objectName));
        }
        final Taggable object = found instanceof Taggable ? (Taggable) found : null;
        addLineage(rows, new Taggable[] {object}, new String[] {domain}, null, null, simpleName, domain, null);
    }

    /** Tags on a database role, named {@code db.role} or by its bare name in the current database. */
    private void addDatabaseRoleTags(final String[] written, final String objectName, final List<Row> rows) {
        final String[] name = qualify(written, 2, "Database role", objectName);
        final Database database = catalog.getDatabase(name[0]);
        final DatabaseRole role = database == null ? null : database.getDatabaseRole(name[1]);
        if (role == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Database role", objectName));
        }
        // The DOMAIN cell spells it with an underscore, where the ARGUMENT that asked for it is the
        // two-word form: TAG_REFERENCES('db.role', 'DATABASE ROLE') answers DATABASE_ROLE.
        addLineage(rows, new Taggable[] {role}, new String[] {"DATABASE_ROLE"}, name[0], null, role.getName(),
            "DATABASE_ROLE", null);
    }

    /** The table, view, dynamic table or materialized view of that name, or null. */
    private static Taggable tableLike(final Schema schema, final String name) {
        if (schema.hasTable(name)) {
            return schema.getTable(name);
        }
        if (schema.hasView(name)) {
            return schema.getView(name);
        }
        try {
            final Object dynamic = schema.getDynamicTable(name);
            if (dynamic instanceof Taggable) {
                return (Taggable) dynamic;
            }
        } catch (final RuntimeException notDynamic) {
            // not a dynamic table
        }
        try {
            final Object materialized = schema.getMaterializedView(name);
            if (materialized instanceof Taggable) {
                return (Taggable) materialized;
            }
        } catch (final RuntimeException notMaterialized) {
            // not a materialized view
        }
        return null;
    }

    /** The schema object of the domain and name, or null when there is none. */
    private static Object schemaObject(final Schema schema, final String domain, final String name) {
        try {
            if ("ALERT".equals(domain)) {
                return schema.hasAlert(name) ? schema.getAlert(name) : null;
            }
            if ("STAGE".equals(domain)) {
                return schema.getStage(name);
            }
            if ("STREAM".equals(domain)) {
                return schema.getStream(name);
            }
            if ("TASK".equals(domain)) {
                return schema.getTask(name);
            }
            if ("PIPE".equals(domain)) {
                return schema.getPipe(name);
            }
            if ("PASSWORD POLICY".equals(domain)) {
                return schema.getSecurityObjects().get(SecurityObjectKind.PASSWORD_POLICY, name);
            }
            final List<?> overloads = "FUNCTION".equals(domain) ? schema.getFunctionOverloads(name)
                : schema.getProcedureOverloads(name);
            return overloads == null || overloads.isEmpty() ? null : overloads.get(0);
        } catch (final RuntimeException missing) {
            return null;
        }
    }

    /**
     * One row per tag reaching the object: the levels are ordered from the object itself outwards, and a tag is
     * reported from the first level that sets it — MANUAL on the object, INHERITED above it.
     */
    private void addLineage(final List<Row> rows, final Taggable[] levels, final String[] levelNames,
                            final String objectDatabase, final String objectSchema, final String objectName,
                            final String domain, final String columnName) {
        final Set<String> seen = new HashSet<String>();
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] == null) {
                continue;
            }
            for (final Map.Entry<String, String> tag : new TreeMap<String, String>(levels[i].getTagValues())
                    .entrySet()) {
                final String[] tagName = tagLocation(tag.getKey(), objectDatabase, objectSchema);
                if (!seen.add(QualifiedName.join(tagName))) {
                    continue;
                }
                rows.add(new Row(Arrays.<Object>asList(
                    tagName[0], tagName[1], tagName[2], tag.getValue(), levelNames[i], objectDatabase,
                    objectSchema, objectName, domain, columnName, i == 0 ? MANUAL : INHERITED)));
            }
        }
    }

    /**
     * Where the tag an assignment names is defined: the assignment names the tag by its name, so the tag is
     * looked for in the object's schema, then the object's database, then the session's schema, then the whole
     * account; a tag found nowhere is reported in the session's schema.
     */
    private String[] tagLocation(final String key, final String objectDatabase, final String objectSchema) {
        final String[] written = QualifiedName.parse(key).parts();
        if (written.length == 3) {
            return written;
        }
        final String name = written[written.length - 1];
        final Database home = objectDatabase == null ? null : catalog.getDatabase(objectDatabase);
        if (home != null && objectSchema != null && home.hasSchema(objectSchema)
                && home.getSchema(objectSchema).hasTag(name)) {
            return new String[] {home.getName(), objectSchema, name};
        }
        if (home != null) {
            for (final Schema schema : home.getAllSchemas()) {
                if (schema.hasTag(name)) {
                    return new String[] {home.getName(), schema.getName(), name};
                }
            }
        }
        final String currentDatabase = catalog.getCurrentDatabase();
        final String currentSchema = catalog.getCurrentSchema();
        final Database current = currentDatabase == null ? null : catalog.getDatabase(currentDatabase);
        if (current != null && currentSchema != null && current.hasSchema(currentSchema)
                && current.getSchema(currentSchema).hasTag(name)) {
            return new String[] {currentDatabase, currentSchema, name};
        }
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema schema : database.getAllSchemas()) {
                if (schema.hasTag(name)) {
                    return new String[] {database.getName(), schema.getName(), name};
                }
            }
        }
        return new String[] {currentDatabase, currentSchema, name};
    }

    /**
     * The name's parts filled out to {@code size} with the session's current database and schema, so that
     * TAG_REFERENCES('t', 'TABLE') resolves the way a real account resolves it.
     */
    private String[] qualify(final String[] written, final int size, final String kind, final String objectName) {
        if (written.length >= size) {
            return written;
        }
        final List<String> filled = new ArrayList<String>();
        final int missing = size - written.length;
        if (missing >= 2) {
            filled.add(catalog.getCurrentDatabase());
            filled.add(catalog.getCurrentSchema());
        } else {
            filled.add(catalog.getCurrentDatabase());
        }
        if (missing > 2 || filled.contains(null)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist(kind, objectName));
        }
        filled.addAll(Arrays.asList(written));
        return filled.toArray(new String[0]);
    }

    private Schema resolveSchema(final String databaseName, final String schemaName, final String kind,
                                 final String objectName) {
        final Database database = databaseName == null ? null : catalog.getDatabase(databaseName);
        if (database == null || schemaName == null || !database.hasSchema(schemaName)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist(kind, objectName));
        }
        return database.getSchema(schemaName);
    }
}
