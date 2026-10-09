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
import dev.frostlake.metastore.TagAssignment;
import dev.frostlake.metastore.TagAssignments;
import dev.frostlake.metastore.Taggable;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DatabaseRole;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.MaterializedView;
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

/**
 * The INFORMATION_SCHEMA.TAG_REFERENCES(object_name, object_domain) TABLE FUNCTION — the tags associated with
 * one object, as {@code SELECT … FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('db.schema.t', 'TABLE'))}.
 *
 * <p>Both arguments are required. The object name is read as an identifier is: an unquoted part folds to upper
 * case and a double-quoted one is kept as written ({@code '"MyWh"'}); a name with fewer parts resolves against
 * the session's current database and schema. A real account takes TABLE for every table-like object — passing
 * VIEW is refused with "Please use object type TABLE for all kinds of table-like objects" — and takes COLUMN
 * with a name whose last part is the column. A table-like object that is not there is refused as any table is,
 * by its bare name when it was written bare and by its full name otherwise; a column is refused by its full name,
 * without the privilege sentence (live-verified).
 *
 * <p>The rows follow tag inheritance through the securable object hierarchy: an object reports the tags set on
 * it ({@code APPLY_METHOD} {@code MANUAL}) and those it inherits from its table, schema and database
 * ({@code INHERITED}), each row's {@code LEVEL} naming the domain the tag is set on. A tag set at several levels
 * is reported once, from the level nearest the object, whose value overrides the others. Two tags of one name
 * that live in two schemas are two tags, each reported (TagAssignments).
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
        requireArguments("TAG_REFERENCES", positionalArgs);
        return new ResultSet(columns(), rows(positionalArgs.get(0).toString(), positionalArgs.get(1).toString()));
    }

    /**
     * The two arguments, each given: one left out or given as NULL is refused by its name, at the position the
     * account reports for every such call (live-verified).
     *
     * @param function the function's name
     * @param positionalArgs the call's arguments
     */
    static void requireArguments(final String function, final List<Object> positionalArgs) {
        if (positionalArgs == null || positionalArgs.isEmpty() || positionalArgs.size() > 2) {
            throw new RuntimeException(function + " requires an object name and an object domain");
        }
        final String missing = positionalArgs.get(0) == null ? "OBJECT_NAME"
            : positionalArgs.size() < 2 || positionalArgs.get(1) == null ? "DOMAIN" : null;
        if (missing != null) {
            throw new RuntimeException(SqlCompilationError.PREFIX + " error line 1 at position 419\n"
                + "missing required argument [" + missing + "] for function [" + function + "]");
        }
    }

    /** The result's columns, in the account's order. */
    static List<ResultSetColumn> columns() {
        final List<ResultSetColumn> columns = new ArrayList<ResultSetColumn>();
        for (final String name : COLUMN_NAMES) {
            columns.add(new ResultSetColumn(name, StringType.VARCHAR));
        }
        return columns;
    }

    /**
     * The value a tag has on an object: the object's own, else the one it inherits from the nearest object above
     * it, else null. The tag and the object each have to exist (live-verified).
     *
     * @param tagName the tag's name, read as an identifier is
     * @param objectName the object's name, read as an identifier is
     * @param domain the object's domain
     * @return the value, or null when the tag does not reach the object
     */
    public String valueOf(final String tagName, final String objectName, final String domain) {
        final String[] written = SqlIdentifiers.canonicalTextParts(tagName);
        final Schema home = catalog.resolveOwningSchema(QualifiedName.join(written));
        final String name = written[written.length - 1];
        if (!home.hasTagExact(name)) {
            // A tag named bare is refused by that name, one named by a path by its full name. The name is matched
            // as an identifier is: a quoted "tg" is no tag TG.
            throw new RuntimeException(SqlCompilationError.doesNotExist("Tag", written.length == 1
                ? QualifiedName.join(written) : home.qualifiedName(name)));
        }
        final String stored = home.getTag(name).getName();
        for (final Row row : rows(objectName, domain)) {
            if (home.getDatabaseName().equals(row.getValue(0)) && home.getName().equals(row.getValue(1))
                    && stored.equals(row.getValue(2))) {
                return (String) row.getValue(3);
            }
        }
        return null;
    }

    /**
     * SYSTEM$GET_TAG(tag, object, domain): {@link #valueOf} of three arguments, none of them NULL (live-verified).
     *
     * @param catalog the catalog
     * @param args the call's arguments
     * @return the tag's value on the object, or null when the tag does not reach it
     */
    public static String systemGetTag(final Catalog catalog, final List<Object> args) {
        for (int i = 0; i < args.size() && i < 3; i++) {
            if (args.get(i) == null) {
                throw new RuntimeException(SqlCompilationError.of("argument " + i
                    + " to function SYSTEM$GET_TAG needs to be constant, found 'null'"));
            }
        }
        if (args.size() < 3) {
            return null;
        }
        return new TagReferencesFunction(catalog).valueOf(unquoted(args.get(0)), unquoted(args.get(1)),
            unquoted(args.get(2)));
    }

    /**
     * SYSTEM$GET_TAG_ALLOWED_VALUES(tag): the tag's ALLOWED_VALUES as a JSON array in text — {@code ["a","b"]},
     * {@code []} for a list emptied value by value — or NULL for a tag without a list. The one argument is a
     * text naming the tag, read as an identifier is, a bare name in the session's schema (live-verified).
     *
     * @param catalog the catalog
     * @param args the call's arguments
     * @return the list as text, or null
     */
    public static String systemGetTagAllowedValues(final Catalog catalog, final List<Object> args) {
        if (args.isEmpty()) {
            return null;
        }
        final Object argument = args.get(0);
        if (argument == null) {
            throw new RuntimeException(SqlCompilationError.of(
                "argument 0 to function SYSTEM$GET_TAG_ALLOWED_VALUES needs to be constant, found 'null'"));
        }
        if (!(argument instanceof String)) {
            throw new RuntimeException(SqlCompilationError.of(
                "argument needs to be a string: 'SYSTEM$GET_TAG_ALLOWED_VALUES'"));
        }
        final String[] written = SqlIdentifiers.canonicalTextParts(unquoted(argument));
        final Schema home = catalog.resolveOwningSchema(QualifiedName.join(written));
        final String name = written[written.length - 1];
        if (!home.hasTagExact(name)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Tag", written.length == 1
                ? QualifiedName.join(written) : home.qualifiedName(name)));
        }
        return home.getTag(name).allowedValuesText();
    }

    /** An argument's text, without the quotes of a string literal handed over as written. */
    private static String unquoted(final Object argument) {
        final String text = argument.toString();
        return text.length() >= 2 && text.startsWith("'") && text.endsWith("'")
            ? text.substring(1, text.length() - 1) : text;
    }

    /** One row per tag reaching the object of that name and domain. */
    private List<Row> rows(final String writtenName, final String writtenDomain) {
        final String domain = writtenDomain.trim().toUpperCase(Locale.ROOT);
        final String[] parts = SqlIdentifiers.canonicalTextParts(writtenName);
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
            throw new RuntimeException(tableLikeOnly(domain));
        } else if (!DOMAINS.contains(domain)) {
            throw new RuntimeException("Unknown domain: " + domain + ".");
        }
        return rows;
    }

    /** The refusal of a domain that names one kind of table-like object: TABLE stands for them all. */
    static String tableLikeOnly(final String domain) {
        return SqlCompilationError.inline("Invalid value " + domain + " for argument OBJECT_TYPE. "
            + "Please use object type TABLE for all kinds of table-like objects.");
    }

    /** Whether the account knows the domain, resolved here or not. */
    static boolean isDomain(final String domain) {
        return DOMAINS.contains(domain);
    }

    /** Tags on a table-like object, and those it inherits from its schema and database. */
    private void addTableTags(final String[] written, final String objectName, final List<Row> rows) {
        final String[] name = tableName(written, objectName);
        final Schema schema = resolveSchema(name[0], name[1], "Table", objectName);
        final Taggable table = requireTableLike(schema, name[2], written, objectName);
        addLineage(rows, new Taggable[] {table, schema, catalog.getDatabase(name[0])},
            new String[] {"TABLE", "SCHEMA", "DATABASE"}, name[0], name[1], name[2], "TABLE", null);
    }

    /** A table-like object's name completed to three parts from the session's database and schema. */
    String[] tableName(final String[] written, final String objectName) {
        return qualify(written, 3, "Table", objectName);
    }

    /** The schema a completed table name places its object in. */
    Schema tableSchema(final String[] name, final String objectName) {
        return resolveSchema(name[0], name[1], "Table", objectName);
    }

    /**
     * The table-like object of that name; a miss is refused by the name as written when it was written bare, and
     * by the full name otherwise (live-verified).
     */
    Taggable requireTableLike(final Schema schema, final String name, final String[] written,
                              final String objectName) {
        final Taggable table = tableLike(schema, name);
        if (table == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Table", written.length == 1 ? objectName
                : schema.qualifiedName(name)));
        }
        return table;
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
            throw new RuntimeException(SqlCompilationError.doesNotExistWithoutHint("Database", objectName));
        }
        addLineage(rows, new Taggable[] {database}, new String[] {"DATABASE"}, null, null, simpleName, "DATABASE",
            null);
    }

    /** Tags on one column, and those it inherits from its table, schema and database. */
    private void addColumnTags(final String[] written, final String objectName, final List<Row> rows) {
        if (written.length < 2) {
            throw new RuntimeException(SqlCompilationError.doesNotExistWithoutHint("Column", QualifiedName.join(
                catalog.getCurrentDatabase(), catalog.getCurrentSchema(), written[0])));
        }
        final String[] name = qualify(written, 4, "Table", objectName);
        final Schema schema = resolveSchema(name[0], name[1], "Table", objectName);
        final Table table = schema.tableExact(name[2]);
        if (table == null) {
            // A column whose table is not there is refused as a column, by its full name (live-verified).
            throw new RuntimeException(SqlCompilationError.doesNotExistWithoutHint("Column",
                QualifiedName.join(name)));
        }
        for (final TableColumn column : table.getColumns()) {
            // The column is matched as an identifier is, too: fz."id" names no column ID.
            if (name[3].equals(column.getName())) {
                addLineage(rows, new Taggable[] {column, table, schema, catalog.getDatabase(name[0])},
                    new String[] {"COLUMN", "TABLE", "SCHEMA", "DATABASE"}, name[0], name[1], name[2], "COLUMN",
                    column.getName());
                return;
            }
        }
        throw new RuntimeException(SqlCompilationError.doesNotExistWithoutHint("Column", QualifiedName.join(name)));
    }

    /** Tags on an object that lives in a schema, and those it inherits from its schema and database. */
    private void addSchemaObjectTags(final String domain, final String[] written, final String objectName,
                                     final List<Row> rows) {
        final String kind = domain.charAt(0) + domain.substring(1).toLowerCase(Locale.ROOT);
        final String[] name = qualify(written, 3, kind, objectName);
        final Schema schema = resolveSchema(name[0], name[1], kind, objectName);
        final Object found = schemaObject(schema, domain, name[2]);
        if (found == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExistWithoutHint(kind, objectName));
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
            // A missing warehouse is refused as any warehouse is (live-verified); the other kinds are not measured.
            throw new RuntimeException("WAREHOUSE".equals(domain) ? SqlCompilationError.doesNotExist(kind, objectName)
                : SqlCompilationError.doesNotExistWithoutHint(kind, objectName));
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
            throw new RuntimeException(SqlCompilationError.doesNotExistWithoutHint("Database role", objectName));
        }
        // The DOMAIN cell spells it with an underscore, where the ARGUMENT that asked for it is the
        // two-word form: TAG_REFERENCES('db.role', 'DATABASE ROLE') answers DATABASE_ROLE.
        addLineage(rows, new Taggable[] {role}, new String[] {"DATABASE_ROLE"}, name[0], null, role.getName(),
            "DATABASE_ROLE", null);
    }

    /** The table, view, dynamic table or materialized view of that name, or null. */
    static Taggable tableLike(final Schema schema, final String name) {
        // The name is matched as an identifier is: a quoted "fz" names no table FZ.
        final Table table = schema.tableExact(name);
        if (table != null) {
            return table;
        }
        if (schema.hasView(name) && name.equals(schema.getView(name).getName())) {
            return schema.getView(name);
        }
        try {
            final DynamicTable dynamic = schema.getDynamicTable(name);
            if (dynamic instanceof Taggable && name.equals(dynamic.getName())) {
                return (Taggable) dynamic;
            }
        } catch (final RuntimeException notDynamic) {
            // not a dynamic table
        }
        try {
            final MaterializedView materialized = schema.getMaterializedView(name);
            if (materialized instanceof Taggable && name.equals(materialized.getName())) {
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
    void addLineage(final List<Row> rows, final Taggable[] levels, final String[] levelNames,
                    final String objectDatabase, final String objectSchema, final String objectName,
                    final String domain, final String columnName) {
        final Set<String> seen = new HashSet<String>();
        for (int i = 0; i < levels.length; i++) {
            for (final TagAssignment tag : TagAssignments.on(catalog, levels[i], objectDatabase, objectSchema)) {
                if (!seen.add(tag.getTag().getId())) {
                    continue;
                }
                rows.add(new Row(Arrays.<Object>asList(
                    tag.getDatabase(), tag.getSchema(), tag.getTag().getName(), tag.getValue(), levelNames[i],
                    objectDatabase, objectSchema, objectName, domain, columnName, i == 0 ? MANUAL : INHERITED)));
            }
        }
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
            throw new RuntimeException(SqlCompilationError.doesNotExistWithoutHint(kind, objectName));
        }
        filled.addAll(Arrays.asList(written));
        return filled.toArray(new String[0]);
    }

    private Schema resolveSchema(final String databaseName, final String schemaName, final String kind,
                                 final String objectName) {
        final Database database = databaseName == null ? null : catalog.getDatabase(databaseName);
        if (database == null || schemaName == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExistWithoutHint(kind, objectName));
        }
        if (!database.hasSchema(schemaName)) {
            // The schema the name places its object in is what is missing, and is refused as such (live-verified).
            throw new RuntimeException(SqlCompilationError.doesNotExist("Schema",
                QualifiedName.join(database.getName(), schemaName)));
        }
        return database.getSchema(schemaName);
    }
}
