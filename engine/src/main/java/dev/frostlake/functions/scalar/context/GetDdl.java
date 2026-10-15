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

package dev.frostlake.functions.scalar.context;

import dev.frostlake.executor.DefinitionCompile;
import dev.frostlake.executor.InformationSchemaGuard;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.ReturnColumnTypes;
import dev.frostlake.executor.RoutineReferenceText;
import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.table.QueryRunner;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.SqlObject;
import dev.frostlake.metastore.StreamSources;
import dev.frostlake.metastore.TagAssignment;
import dev.frostlake.metastore.TagAssignments;
import dev.frostlake.metastore.Taggable;
import dev.frostlake.metastore.model.AggregationPolicy;
import dev.frostlake.metastore.model.Alert;
import dev.frostlake.metastore.model.AppObject;
import dev.frostlake.metastore.model.AppObjectKind;
import dev.frostlake.metastore.model.CheckConstraint;
import dev.frostlake.metastore.model.ConstraintNames;
import dev.frostlake.metastore.model.Contact;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.DynamicTableColumn;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.JoinPolicy;
import dev.frostlake.metastore.model.MaskingPolicy;
import dev.frostlake.metastore.model.MaterializedView;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Pipe;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.ProjectionPolicy;
import dev.frostlake.metastore.model.RoutineEnvironment;
import dev.frostlake.metastore.model.RowAccessPolicy;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurityObject;
import dev.frostlake.metastore.model.SecurityObjectKind;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.metastore.model.SequenceDefault;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.StreamSourceType;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.UniqueConstraint;
import dev.frostlake.metastore.model.View;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.DataType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code GET_DDL('<object_type>', '<object_name>' [, <use_fully_qualified_names>])} — reconstructs the
 * {@code CREATE OR REPLACE} text of an object from the catalog, as a real account prints it.
 *
 * <p>SCHEMA and DATABASE are recursive: the container's own statement, then every object it holds
 * ({@code SchemaDdl}). A single object may be a TABLE, VIEW, MATERIALIZED VIEW or DYNAMIC TABLE — the four are
 * interchangeable, each names whichever relation holds the name — a SEQUENCE, FUNCTION, PROCEDURE, STREAM, TASK,
 * PIPE, TAG, POLICY (every policy kind), FILE FORMAT, ALERT, CONTACT or STREAMLIT. A space in the type reads as an
 * underscore. The object types the account renders but the engine does not model answer that GET_DDL does not
 * support them; any other type is refused as invalid, echoing the type upper-cased as written.
 *
 * <p>The third argument, read as a boolean ({@code TRUE}, {@code 'yes'}, {@code 1}), spells every recreated
 * object's own name fully qualified ({@code DdlNames}); it is read once the object has been found. A NULL argument
 * answers NULL. A missing object is refused naming its kind and the privilege the role would need to see it; the
 * name is read whole, spaces included, and echoed bare when written bare and completed to database, schema and name
 * otherwise — for a pipe, a policy, a dynamic table, a contact, a Streamlit app and a stage always completed. The
 * arguments must be constants, which the statement's compilation judges before this runs.
 */
public class GetDdl extends BuiltInFunction {

    /**
     * Whether this thread is deriving a relation's column list. Planning a query that computes as it plans — one
     * with no FROM — may call GET_DDL again; that nested call renders its relations without a column list rather
     * than derive them once more.
     */
    private static final ThreadLocal<Boolean> DERIVING = new ThreadLocal<Boolean>();

    /** Every object type the call takes, the ones it finds and then cannot recreate among them. */
    private static final Set<String> OBJECT_TYPES = Set.of(
        "SCHEMA", "DATABASE", "TABLE", "VIEW", "MATERIALIZED_VIEW", "DYNAMIC_TABLE", "EVENT_TABLE", "FUNCTION",
        "PROCEDURE", "SEQUENCE", "STREAM", "TASK", "PIPE", "TAG", "POLICY", "FILE_FORMAT", "ALERT", "CONTACT",
        "STREAMLIT", "STAGE", "WAREHOUSE", "INTEGRATION", "TYPE", "SEMANTIC_VIEW", "CORTEX_AGENT", "FAILOVER_GROUP",
        "REPLICATION_GROUP", "NOTEBOOK", "ICEBERG_TABLE", "ROLE", "DBT_PROJECT", "CORTEX_SEARCH_SERVICE", "ACCOUNT");

    private final Catalog catalog;
    /**
     * Answers a defining query's SHAPE — its columns and no row — without running it, to derive a view's, a
     * materialized view's or a dynamic table's output column names; null until the executor wires it.
     */
    private final QueryRunner queryRunner;
    private final RelationNameResolver relationNames;
    private final DefinitionOwnership ownership;
    /** The recursive text of a schema and a database, built from this function's renderers. */
    private final SchemaDdl containers = new SchemaDdl(this);

    public GetDdl(final Catalog catalog) {
        this(catalog, null);
    }

    public GetDdl(final Catalog catalog, final QueryRunner queryRunner) {
        this(catalog, queryRunner, null);
    }

    /**
     * @param catalog       the catalog the objects are read from
     * @param queryRunner   plans a relation's defining query, or null
     * @param relationNames finds a relation by a bare name along the session's search path, or null to look in
     *                      the current schema alone
     */
    public GetDdl(final Catalog catalog, final QueryRunner queryRunner, final RelationNameResolver relationNames) {
        this(catalog, queryRunner, relationNames, null);
    }

    /**
     * @param catalog       the catalog the objects are read from
     * @param queryRunner   plans a relation's defining query, or null
     * @param relationNames finds a relation by a bare name along the session's search path, or null to look in
     *                      the current schema alone
     * @param ownership     says whether the session may act as an object's owner, or null to show everything
     */
    public GetDdl(final Catalog catalog, final QueryRunner queryRunner, final RelationNameResolver relationNames,
                  final DefinitionOwnership ownership) {
        super("GET_DDL", StringType.VARCHAR);
        this.catalog = catalog;
        this.queryRunner = queryRunner;
        this.relationNames = relationNames;
        this.ownership = ownership;
    }

    /**
     * Whether the session may not act as an object's owner. Only its owner — a role the session holds or inherits —
     * is shown how a SECURE function, procedure or view is defined, and a procedure's body, secure or not: to any
     * other role the secure object is {@code Object does not exist, or operation cannot be performed.}, a schema's
     * DDL leaves it out, and a procedure's DDL carries an empty body (live-verified).
     *
     * @param owner the object's owner role
     * @return whether the definition is another role's
     */
    boolean isAnothers(final String owner) {
        return ownership != null && !ownership.actsAsOwner(owner);
    }

    private void requireShown(final boolean secure, final String owner) {
        if (secure && isAnothers(owner)) {
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
    }

    /**
     * Whether the session sees an object of a schema: its roles own it or hold a privilege on it, or hold MANAGE
     * GRANTS. GET_DDL needs what DESCRIBE and SHOW need to show the object (documented), and answers one the
     * session does not see as one that does not exist.
     */
    private boolean sees(final String grantKind, final Schema schema, final String name, final String objectOwner) {
        return ownership == null || ownership.sees(grantKind, schema.qualifiedName(name), objectOwner);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        // A definition compiled to be kept computes nothing: the lookup waits for whoever runs it.
        return DefinitionCompile.isActive() ? null : ddl(args, false);
    }

    /**
     * Finds the object a call names and renders nothing: the lookup a statement's compilation makes, so that a
     * missing object, or a type GET_DDL cannot recreate, is refused before anything runs and wherever the call
     * stands. A NULL type or name names nothing.
     *
     * @param type the object type argument's value
     * @param name the object name argument's value
     */
    public void requireObject(final Object type, final Object name) {
        ddl(Arrays.asList(type, name), true);
    }

    /** The named object's text, or, for a lookup alone, null once the object is found. */
    private Object ddl(final List<Object> args, final boolean lookupOnly) {
        for (final Object arg : args) {
            if (arg == null) {
                return null;
            }
        }
        final String typeText = args.get(0).toString().toUpperCase(Locale.ROOT);
        final String objectType = typeText.replace(' ', '_');
        if (!OBJECT_TYPES.contains(objectType)) {
            throw invalidObjectType(typeText);
        }
        final DdlNames names = new DdlNames(args.size() > 2 ? args.get(2) : null);
        final String written = args.get(1).toString();
        switch (objectType) {
            case "SCHEMA": {
                final Schema schema =
                    catalog.resolveSchema(QualifiedName.of(SqlIdentifiers.canonicalTextParts(written)));
                return lookupOnly ? null : containers.schema(schema, names);
            }
            case "DATABASE": {
                final Database database = databaseNamed(written);
                return lookupOnly ? null : containers.database(database, names);
            }
            case "TABLE":
            case "VIEW":
            case "MATERIALIZED_VIEW":
            case "DYNAMIC_TABLE":
                return relationDdl(objectType, relationNames == null ? written : relationNames.resolve(written), names,
                    lookupOnly);
            case "EVENT_TABLE":
                throw new RuntimeException("Unsupported feature 'EVENT_TABLE'.");
            case "FUNCTION": {
                final Function function = (Function) routine(written.trim(), false);
                requireShown(function.isSecure(), function.getOwner());
                return lookupOnly ? null : functionDdl(function, routineOwner(written.trim()), names);
            }
            case "PROCEDURE": {
                final Procedure procedure = (Procedure) routine(written.trim(), true);
                requireShown(procedure.isSecure(), procedure.getOwner());
                return lookupOnly ? null : procedureDdl(procedure, routineOwner(written.trim()), names);
            }
            case "SEQUENCE":
            case "STREAM":
            case "TASK":
            case "PIPE":
            case "TAG":
            case "POLICY":
            case "FILE_FORMAT":
            case "ALERT":
            case "CONTACT":
            case "STREAMLIT":
            case "STAGE":
                return memberDdl(objectType, typeText, written, names);
            case "WAREHOUSE":
                // A warehouse is looked up before anything else is said about it.
                if (!catalog.hasWarehouse(SqlIdentifiers.canonicalTextParts(written)[0])) {
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Warehouse",
                        SqlIdentifiers.canonicalTextParts(written)[0]));
                }
                throw new RuntimeException("GET_DDL does not support object type: " + objectType);
            case "INTEGRATION":
            case "TYPE":
            case "SEMANTIC_VIEW":
            case "CORTEX_AGENT":
            case "FAILOVER_GROUP":
            case "REPLICATION_GROUP":
            case "NOTEBOOK":
            case "ICEBERG_TABLE":
            case "ROLE":
            case "DBT_PROJECT":
            case "CORTEX_SEARCH_SERVICE":
            case "ACCOUNT":
                throw new RuntimeException("GET_DDL does not support object type: " + objectType);
            default:
                throw invalidObjectType(typeText);
        }
    }

    /**
     * The refusal for a name written as a whole number, which names no object: a type GET_DDL does not know is
     * refused as ever, and a known one is then refused as the object, spelled as written —
     * {@code GET_DDL('table', 1)} is {@code Object 'table' does not exist or not authorized.}
     *
     * @param type the object type argument's value
     * @return the refusal
     */
    public RuntimeException numberedObject(final Object type) {
        final String typeText = type.toString().toUpperCase(Locale.ROOT);
        return OBJECT_TYPES.contains(typeText.replace(' ', '_')) ? objectDoesNotExist(type.toString())
            : invalidObjectType(typeText);
    }

    private static RuntimeException invalidObjectType(final String typeText) {
        return new RuntimeException(SqlCompilationError.of("Invalid object type: '" + typeText + "'"));
    }

    /** The database a name argument names; a name of more than one part names no database. */
    private Database databaseNamed(final String written) {
        final String[] parts = catalog.withoutAccount(SqlIdentifiers.canonicalTextParts(written), 1);
        return catalog.databaseExact(parts[0]);
    }

    // ─────────────────────────── lookups ───────────────────────────

    /** The canonical parts of a name argument: a bare part folded to upper case, a quoted one verbatim. */
    private static String[] nameParts(final String written) {
        return SqlIdentifiers.canonicalTextParts(written);
    }

    /** The schema a name argument places its object in: the current one for a bare name. */
    private Schema owner(final String[] parts) {
        return catalog.requireOwningSchema(QualifiedName.of(parts));
    }

    /**
     * The refusal for a missing object: {@code Kind 'NAME' does not exist or not authorized.} and the privilege
     * hint. A bare name is echoed as resolved, a qualified one completed to three parts; {@code alwaysQualified}
     * completes a bare one too.
     */
    private static RuntimeException missing(final String[] parts, final Schema owner, final String kind,
                                            final boolean alwaysQualified) {
        final String name = parts[parts.length - 1];
        final String echoed = parts.length == 1 && !alwaysQualified ? QualifiedName.join(name)
            : QualifiedName.join(owner.getDatabaseName(), owner.getName(), name);
        return new RuntimeException(SqlCompilationError.doesNotExist(kind, echoed));
    }

    /** Whether a catalog lookup found the object of exactly this name, rather than one that differs by case. */
    private static boolean named(final String actual, final String name) {
        return actual != null && actual.equals(name);
    }

    /** TABLE, VIEW, MATERIALIZED VIEW or DYNAMIC TABLE: whichever relation of the four holds the name. */
    private String relationDdl(final String objectType, final String written, final DdlNames names,
                               final boolean lookupOnly) {
        final String[] parts = nameParts(written);
        final Schema owner = owner(parts);
        final String name = parts[parts.length - 1];
        final Table table = owner.tableExact(name);
        if (table != null) {
            requireSeen(objectType, "TABLE", parts, owner, table.getOwner());
            return lookupOnly ? null : tableDdl(table, owner, names);
        }
        if (owner.hasView(name) && named(owner.getView(name).getName(), name)) {
            if (InformationSchemaGuard.SCHEMA.equals(owner.getName())) {
                // INFORMATION_SCHEMA's own views have no definition to give, whichever kind is asked for
                // (live-verified).
                throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
            }
            requireSeen(objectType, "VIEW", parts, owner, owner.getView(name).getOwner());
            requireShown(owner.getView(name).isSecure(), owner.getView(name).getOwner());
            return lookupOnly ? null : viewDdl(owner.getView(name), owner, names);
        }
        if (owner.hasMaterializedView(name) && named(owner.getMaterializedView(name).getName(), name)) {
            requireSeen(objectType, "MATERIALIZED_VIEW", parts, owner, owner.getMaterializedView(name).getOwner());
            requireShown(owner.getMaterializedView(name).isSecure(), owner.getMaterializedView(name).getOwner());
            return lookupOnly ? null : materializedViewDdl(owner.getMaterializedView(name), owner, names);
        }
        if (owner.hasDynamicTable(name) && named(owner.getDynamicTable(name).getName(), name)) {
            requireSeen(objectType, "DYNAMIC_TABLE", parts, owner, owner.getDynamicTable(name).getOwner());
            return lookupOnly ? null : dynamicTableDdl(owner.getDynamicTable(name), owner, names);
        }
        throw missingRelation(objectType, parts, owner);
    }

    /** Refuses a relation the session does not see as the missing one of the kind asked for. */
    private void requireSeen(final String objectType, final String grantKind, final String[] parts,
                             final Schema owner, final String objectOwner) {
        if (!sees(grantKind, owner, parts[parts.length - 1], objectOwner)) {
            throw missingRelation(objectType, parts, owner);
        }
    }

    /** The refusal for a relation that is not there, in the words of the kind asked for. */
    private static RuntimeException missingRelation(final String objectType, final String[] parts,
                                                    final Schema owner) {
        switch (objectType) {
            case "VIEW":
                return missing(parts, owner, "View", false);
            case "MATERIALIZED_VIEW":
                return missing(parts, owner, "Materialized view", false);
            case "DYNAMIC_TABLE":
                return missing(parts, owner, "Dynamic table", true);
            default:
                return missing(parts, owner, "Table", false);
        }
    }

    /** A single schema object that is neither a relation nor a routine. */
    private String memberDdl(final String objectType, final String typeText, final String written,
                             final DdlNames names) {
        final String[] parts = nameParts(written);
        final Schema owner = owner(parts);
        final String name = parts[parts.length - 1];
        switch (objectType) {
            case "SEQUENCE":
                if (!owner.hasSequenceExact(name)
                        || !sees("SEQUENCE", owner, name, owner.getSequence(name).getOwner())) {
                    throw missing(parts, owner, "Sequence", false);
                }
                return sequenceDdl(owner.getSequence(name), names.object(owner, name));
            case "STREAM": {
                final Stream stream = find(owner, name, objectType);
                if (stream == null || !sees("STREAM", owner, name, stream.getOwner())) {
                    throw missing(parts, owner, "Stream", false);
                }
                final boolean gone = stream.getSourceType() == StreamSourceType.TABLE
                    && StreamSources.sourceOf(catalog, owner, stream) == null;
                return ObjectDdl.stream(stream, names.object(owner, name),
                    gone ? StreamSources.fullName(owner, stream) : null);
            }
            case "TASK": {
                final Task task = find(owner, name, objectType);
                if (task == null || !sees("TASK", owner, name, task.getOwner())) {
                    throw missing(parts, owner, "Task", false);
                }
                return ObjectDdl.task(task, owner, names.object(owner, name), tagAssignments(task, owner));
            }
            case "PIPE": {
                final Pipe pipe = find(owner, name, objectType);
                if (pipe == null || !sees("PIPE", owner, name, pipe.getOwner())) {
                    throw missing(parts, owner, "Pipe", true);
                }
                return ObjectDdl.pipe(pipe, names.object(owner, name));
            }
            case "TAG": {
                final Tag tag = find(owner, name, objectType);
                if (tag == null) {
                    throw missing(parts, owner, "Tag", false);
                }
                return ObjectDdl.tag(tag, names.object(owner, name));
            }
            case "POLICY":
                return policyDdl(owner, parts, names);
            case "FILE_FORMAT": {
                final FileFormat format = owner.getFileFormat(name);
                if (format == null || !named(format.getName(), name)) {
                    throw missing(parts, owner, "File format", false);
                }
                return ObjectDdl.fileFormat(format, names.object(owner, name));
            }
            case "ALERT": {
                final Alert alert = find(owner, name, objectType);
                if (alert == null) {
                    throw missing(parts, owner, "Alert", false);
                }
                return ObjectDdl.alert(alert, names.object(owner, name));
            }
            case "CONTACT": {
                final Contact contact = owner.getContact(name);
                if (contact == null || !named(contact.getName(), name)) {
                    throw missing(parts, owner, "Contact", true);
                }
                return ObjectDdl.contact(contact, names.object(owner, name));
            }
            case "STREAMLIT":
                for (final AppObject app : owner.getAppObjects().all(AppObjectKind.STREAMLIT)) {
                    if (named(app.getName(), name)) {
                        return ObjectDdl.streamlit(app, names.object(owner, name));
                    }
                }
                throw missing(parts, owner, "Streamlit", true);
            default:
                // A stage is looked up and then refused as a type GET_DDL cannot recreate.
                if (!owner.hasStageExact(name)) {
                    throw missing(parts, owner, "Stage", true);
                }
                throw invalidObjectType(typeText);
        }
    }

    /**
     * A stream, task, pipe, tag or alert of exactly this name, or null. The catalog's getters refuse a missing
     * object in their own words, which GET_DDL does not use.
     */
    @SuppressWarnings("unchecked")
    private static <T> T find(final Schema owner, final String name, final String objectType) {
        final Object found;
        try {
            switch (objectType) {
                case "STREAM":
                    found = owner.getStream(name);
                    break;
                case "TASK":
                    found = owner.getTask(name);
                    break;
                case "PIPE":
                    found = owner.getPipe(name);
                    break;
                case "TAG":
                    found = owner.getTag(name);
                    break;
                default:
                    found = owner.getAlert(name);
                    break;
            }
        } catch (final RuntimeException missing) {
            return null;
        }
        final String actual = found instanceof Stream ? ((Stream) found).getName()
            : found instanceof Task ? ((Task) found).getName() : ((SqlObject) found).getName();
        return named(actual, name) ? (T) found : null;
    }

    /** POLICY: whichever policy kind holds the name. */
    private String policyDdl(final Schema owner, final String[] parts, final DdlNames names) {
        final String name = parts[parts.length - 1];
        for (final MaskingPolicy policy : owner.getMaskingPolicies()) {
            if (named(policy.getName(), name)) {
                return ObjectDdl.maskingPolicy(policy, names.object(owner, name));
            }
        }
        for (final RowAccessPolicy policy : owner.getRowAccessPolicies()) {
            if (named(policy.getName(), name)) {
                return ObjectDdl.rowAccessPolicy(policy, names.object(owner, name));
            }
        }
        for (final AggregationPolicy policy : owner.getAggregationPolicies()) {
            if (named(policy.getName(), name)) {
                return ObjectDdl.aggregationPolicy(policy, names.object(owner, name));
            }
        }
        for (final ProjectionPolicy policy : owner.getProjectionPolicies()) {
            if (named(policy.getName(), name)) {
                return ObjectDdl.projectionPolicy(policy, names.object(owner, name));
            }
        }
        for (final JoinPolicy policy : owner.getJoinPolicies()) {
            if (named(policy.getName(), name)) {
                return ObjectDdl.joinPolicy(policy, names.object(owner, name));
            }
        }
        for (final SecurityObject policy : owner.getSecurityObjects().list(SecurityObjectKind.PASSWORD_POLICY)) {
            if (named(policy.getName(), name)) {
                return ObjectDdl.passwordPolicy(policy, names.object(owner, name));
            }
        }
        throw missing(parts, owner, "Policy", true);
    }

    // ─────────────────────────── TABLE ───────────────────────────

    /**
     * A table's DDL: {@code create or replace [TEMPORARY |TRANSIENT ]TABLE T[ cluster by (…)](}, one tab-indented
     * line per column and table-level constraint, the closing paren, then the table's attachments and its COMMENT
     * ({@link #tableTail}). A table in a transient schema is transient. An event table is only its name and COMMENT.
     */
    String tableDdl(final Table table, final Schema schema, final DdlNames names) {
        final String name = names.object(schema, table.getName());
        if (table.isEventTable()) {
            final StringBuilder event = new StringBuilder("create or replace event table ").append(name);
            if (ObjectDdl.hasText(table.getComment())) {
                event.append(" COMMENT=").append(ObjectDdl.quoted(table.getComment())).append('\n');
            }
            return event.append(';').toString();
        }
        final StringBuilder sb = new StringBuilder("create or replace ");
        if (table.isTemporary()) {
            sb.append("TEMPORARY ");
        } else if (table.isTransient() || schema.isTransientObject()) {
            sb.append("TRANSIENT ");
        }
        sb.append("TABLE ").append(name);
        // A clustering key is spelled between the name and the column list, as its statement wrote it, the paren
        // following it directly.
        if (!table.getClusterKeys().isEmpty()) {
            sb.append(" cluster by ").append(table.getClusterByText()).append("(");
        } else {
            sb.append(" (");
        }

        final List<String> lines = new ArrayList<String>();
        for (final TableColumn col : table.getColumns()) {
            if (col.getDefaultValue() instanceof SequenceDefault
                    && ((SequenceDefault) col.getDefaultValue()).sequenceIn(catalog) == null) {
                // A default whose sequence is gone cannot be written out: the table has no DDL (live-verified).
                throw SequenceDefault.lost(SqlIdentifiers.spellCanonical(schema.getDatabaseName()) + "."
                    + SqlIdentifiers.spellCanonical(schema.getName()) + "."
                    + SqlIdentifiers.spellCanonical(table.getName()), SqlIdentifiers.spellCanonical(col.getName()));
            }
            lines.add(columnLine(col, schema));
        }
        // Every key and check follows the columns as a TABLE-level line, however it was written: the unique keys
        // — a column's own first, then the table-level ones as declared — the checks, the primary key, and the
        // foreign keys, a column's own first. A name of the constraint's own is spelled and a generated one is
        // not, and RELY ends the line with a space after it (live-verified).
        final List<UniqueConstraint> declared = table.getDeclaredUniqueConstraints();
        for (final UniqueConstraint unique : table.getUniqueConstraints()) {
            if (!declaredAs(declared, unique)) {
                lines.add(keyLine(unique.getConstraintName(), "unique (" + spelledNames(unique.getColumnNames()) + ")",
                    relies(table, unique.getColumnNames())));
            }
        }
        for (final UniqueConstraint unique : table.getUniqueConstraints()) {
            if (declaredAs(declared, unique)) {
                lines.add(keyLine(unique.getConstraintName(), "unique (" + spelledNames(unique.getColumnNames()) + ")",
                    relies(table, unique.getColumnNames())));
            }
        }
        for (final CheckConstraint check : table.getCheckConstraints()) {
            lines.add((check.isAutoNamed() ? "" : "constraint " + check.getName() + " ")
                + "check (" + check.getExpression() + ")");
        }
        if (!table.getPrimaryKeys().isEmpty()) {
            lines.add(keyLine(table.primaryKeyConstraintName(), "primary key (" + spelledNames(table.getPrimaryKeys())
                + ")", relies(table, table.getPrimaryKeys())));
        }
        for (final TableColumn col : table.getColumns()) {
            if (col.getReferencedTable() != null) {
                lines.add(keyLine(table.columnForeignKeyConstraintName(col.getName()), "foreign key ("
                    + SqlIdentifiers.spellCanonicalEscaped(col.getName()) + ") references "
                    + parentName(schema, col.getReferencedTable())
                    + (col.getReferencedColumn() == null ? ""
                        : "(" + SqlIdentifiers.spellCanonicalEscaped(col.getReferencedColumn()) + ")"),
                    Boolean.TRUE.equals(col.getRely())));
            }
        }
        for (final ForeignKeyConstraint fk : table.getForeignKeys()) {
            final StringBuilder referenced = new StringBuilder();
            for (final String column : fk.getReferencedColumns()) {
                referenced.append(referenced.length() > 0 ? "," : "").append(SqlIdentifiers.spellCanonicalEscaped(column));
            }
            lines.add(keyLine(fk.getConstraintName(), "foreign key (" + spelledNames(fk.getColumnNames())
                + ") references " + parentName(schema, fk.getReferencedTable()) + "(" + referenced + ")",
                Boolean.TRUE.equals(fk.getRely())));
        }
        for (int i = 0; i < lines.size(); i++) {
            sb.append(i == 0 ? "\n\t" : ",\n\t").append(lines.get(i));
        }
        sb.append("\n)");
        return sb.append(tableTail(table, schema)).toString();
    }

    /**
     * What follows a table's closing paren: its attachments — the aggregation policy with its entity key, the row
     * access policy with its columns, the join policy and the tags, in that order — the first after a space and each
     * other on a line of its own led by a space, then the COMMENT on the next line, and the semicolon. With neither
     * the COMMENT follows the paren directly, and a table with nothing ends on the paren.
     */
    private String tableTail(final Table table, final Schema schema) {
        final List<String> attachments = new ArrayList<String>();
        if (table.hasAggregationPolicy()) {
            attachments.add("WITH AGGREGATION POLICY " + table.getAggregationPolicyName()
                + (table.getAggregationEntityKey().isEmpty() ? ""
                    : " ENTITY KEY (" + String.join(", ", table.getAggregationEntityKey()) + ")"));
        }
        if (table.hasRowAccessPolicy()) {
            attachments.add(rowAccessPolicy(table.getRowAccessPolicyName(), table.getRowAccessPolicyColumns(),
                columnNames(table.getColumns()), schema));
        }
        if (table.hasJoinPolicy()) {
            attachments.add("WITH JOIN POLICY " + table.getJoinPolicyName());
        }
        final String tableTags = tagAssignments(table, schema);
        if (!tableTags.isEmpty()) {
            attachments.add("WITH TAG (" + tableTags + ")");
        }
        final StringBuilder tail = new StringBuilder();
        for (int i = 0; i < attachments.size(); i++) {
            tail.append(i == 0 ? " " : "\n ").append(attachments.get(i));
        }
        if (ObjectDdl.hasText(table.getComment())) {
            return tail.append(attachments.isEmpty() ? "" : "\n").append("COMMENT=")
                .append(ObjectDdl.quoted(table.getComment())).append("\n;").toString();
        }
        return tail.append(attachments.isEmpty() ? ";" : "\n;").toString();
    }

    /**
     * {@code WITH ROW ACCESS POLICY DB.S.P ON (A, "b")} — the policy by its full name, found from the name the
     * attachment recorded: completed from the relation's own database, and for a bare name from the relation's own
     * schema or else the one schema of its database holding it; the columns in the order attached, each spelled as the
     * relation names it.
     */
    private String rowAccessPolicy(final String attached, final List<String> columns, final List<String> relationColumns,
                                   final Schema schema) {
        final String[] parts = QualifiedName.parse(attached).parts();
        final String policyName = parts[parts.length - 1];
        Schema home = schema;
        if (parts.length >= 2) {
            try {
                home = catalog.getDatabase(parts.length == 3 ? parts[0] : schema.getDatabaseName())
                    .getSchema(parts[parts.length - 2]);
            } catch (final RuntimeException noSchema) {
                home = schema;
            }
        } else if (!schema.hasRowAccessPolicy(policyName)) {
            home = schemaHolding(schema, policyName);
        }
        final RowAccessPolicy policy = home.getRowAccessPolicy(policyName);
        final String spelled = SqlIdentifiers.spellCanonicalEscaped(policy != null ? policy.getName() : policyName);
        final StringBuilder clause = new StringBuilder("WITH ROW ACCESS POLICY ").append(DdlNames.path(home))
            .append('.').append(spelled).append(" ON (");
        for (int i = 0; i < columns.size(); i++) {
            clause.append(i > 0 ? ", " : "")
                .append(SqlIdentifiers.spellCanonicalEscaped(relationColumn(relationColumns, columns.get(i))));
        }
        return clause.append(')').toString();
    }

    /** The schema of the relation's database that holds the row access policy, or the relation's own schema. */
    private Schema schemaHolding(final Schema schema, final String policyName) {
        try {
            for (final Schema other : catalog.getDatabase(schema.getDatabaseName()).getAllSchemas()) {
                if (other.hasRowAccessPolicy(policyName)) {
                    return other;
                }
            }
        } catch (final RuntimeException noDatabase) {
            // The relation's own schema names the policy.
        }
        return schema;
    }

    /** A recorded column name as the relation spells it: the column it names exactly, else ignoring case. */
    private static String relationColumn(final List<String> relationColumns, final String recorded) {
        for (final String column : relationColumns) {
            if (column.equals(recorded)) {
                return column;
            }
        }
        for (final String column : relationColumns) {
            if (column.equalsIgnoreCase(recorded)) {
                return column;
            }
        }
        return recorded;
    }

    /** The names of a table's columns, in order. */
    private static List<String> columnNames(final List<TableColumn> columns) {
        final List<String> names = new ArrayList<String>();
        for (final TableColumn column : columns) {
            names.add(column.getName());
        }
        return names;
    }

    /** A key's line: the name it was given, the key, and {@code rely} with a space after it. */
    private static String keyLine(final String constraintName, final String key, final boolean rely) {
        return (ConstraintNames.isGenerated(constraintName) ? ""
            : "constraint " + SqlIdentifiers.spellCanonicalEscaped(constraintName) + " ") + key + (rely ? " rely " : "");
    }

    /** Whether a unique key is one a table-level clause declared, as against a column's own UNIQUE. */
    private static boolean declaredAs(final List<UniqueConstraint> declared, final UniqueConstraint unique) {
        for (final UniqueConstraint candidate : declared) {
            if (candidate.getConstraintName().equals(unique.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    /** Whether a key relies: RELY rides on the key's columns. */
    private static boolean relies(final Table table, final List<String> columnNames) {
        for (final String columnName : columnNames) {
            if (table.hasColumn(columnName) && Boolean.TRUE.equals(table.getColumn(columnName).getRely())) {
                return true;
            }
        }
        return false;
    }

    /** A foreign key's parent in full, whatever the key's statement wrote: DATABASE.SCHEMA.TABLE. */
    private static String parentName(final Schema schema, final String referencedTable) {
        final String[] parts = QualifiedName.parse(referencedTable).parts();
        final StringBuilder name = new StringBuilder();
        if (parts.length < 3) {
            name.append(SqlIdentifiers.spellCanonical(schema.getDatabaseName())).append('.');
        }
        if (parts.length < 2) {
            name.append(SqlIdentifiers.spellCanonical(schema.getName())).append('.');
        }
        for (int i = 0; i < parts.length; i++) {
            name.append(i > 0 ? "." : "").append(SqlIdentifiers.spellCanonical(parts[i]));
        }
        return name.toString();
    }

    /** Column names as SQL text, joined by commas: {@code "c""d", X}. */
    private static String spelledNames(final List<String> names) {
        final StringBuilder spelled = new StringBuilder();
        for (final String name : names) {
            spelled.append(spelled.length() > 0 ? ", " : "").append(SqlIdentifiers.spellCanonicalEscaped(name));
        }
        return spelled.toString();
    }

    private String columnLine(final TableColumn col, final Schema schema) {
        final StringBuilder c = new StringBuilder();
        c.append(SqlIdentifiers.spellCanonicalEscaped(col.getName())).append(" ").append(renderType(col.getDataType()));
        if (col.getCollation() != null && !col.getCollation().isEmpty()) {
            c.append(" COLLATE '").append(col.getCollation()).append("'");
        }
        if (col.isAutoIncrement()) {
            // An identity column is NOT NULL only when declared so, and its generator ends in order or noorder
            // (live-verified).
            if (!col.isNullable()) {
                c.append(" NOT NULL");
            }
            c.append(" autoincrement start ").append(col.getIdentityStart())
             .append(" increment ").append(col.getIdentityIncrement())
             .append(col.isIdentityOrdered() ? " order" : " noorder");
        } else {
            if (!col.isNullable()) {
                c.append(" NOT NULL");
            }
            if (col.getDefaultValue() != null) {
                c.append(" DEFAULT ").append(renderDefault(col.getDefaultValue()));
            }
        }
        // A projection policy renders before a masking one when a column carries both (live-verified).
        if (col.hasProjectionPolicy()) {
            c.append(" WITH PROJECTION POLICY ").append(col.getProjectionPolicyName());
        }
        // A masked column carries its policy here, fully qualified, and always spelled WITH MASKING
        // POLICY even when the column was declared without the WITH. Live puts it after DEFAULT and
        // before COMMENT — and refuses that order reversed, so the position is the measured one.
        if (col.getMaskingPolicyName() != null && !col.getMaskingPolicyName().isEmpty()) {
            c.append(" WITH MASKING POLICY ").append(col.getMaskingPolicyName());
            // A conditional policy's USING columns, as the attachment named them.
            final List<String> using = col.getMaskingPolicyArguments();
            if (!using.isEmpty()) {
                c.append(" USING (");
                for (int u = 0; u < using.size(); u++) {
                    c.append(u > 0 ? ", " : "").append(SqlIdentifiers.spellCanonicalEscaped(using.get(u)));
                }
                c.append(')');
            }
        }
        final String columnTags = tagAssignments(col, schema);
        if (!columnTags.isEmpty()) {
            c.append(" WITH TAG (").append(columnTags).append(")");
        }
        if (col.getComment() != null && !col.getComment().isEmpty()) {
            c.append(" COMMENT ").append(ObjectDdl.quoted(col.getComment()));
        }
        return c.toString();
    }

    /** Render a column type back to SQL — canonically, exactly as DESCRIBE spells it. */
    private String renderType(final DataType type) {
        return SqlTypeNames.canonical(type);
    }

    /** Render a DEFAULT value: expression text and keyword/number/boolean defaults bare, strings quoted. */
    private String renderDefault(final Object def) {
        return ShowResultHelpers.renderDefaultExpression(def);
    }

    /**
     * {@code DB.S.T='v', …} — the tags an object or a column carries, each named where it lives, in the order of
     * those full names; a value is printed between quotes exactly as stored, its own quotes and backslashes too.
     * Empty when the object carries no tag that still exists.
     */
    String tagAssignments(final Taggable object, final Schema schema) {
        final StringBuilder out = new StringBuilder();
        for (final TagAssignment tag : TagAssignments.on(catalog, object, schema.getDatabaseName(),
                schema.getName())) {
            out.append(out.length() > 0 ? ", " : "").append(tag.path()).append("='").append(tag.getValue())
               .append('\'');
        }
        return out.toString();
    }

    // ─────────────────────────── VIEW ───────────────────────────

    /**
     * Live Snowflake's GET_DDL always renders a view's parenthesized output column list, one
     * tab-indented column per line with its COMMENT: {@code create or replace view V(\n\tA,\n\tB\n) as SELECT ...;},
     * then its row access policy, its tags and its own COMMENT, each after a space on a line of its own. Columns come
     * from the explicit column list when declared, otherwise from planning the defining query; if neither is
     * available the list is omitted.
     */
    String viewDdl(final View view, final Schema schema, final DdlNames names) {
        final String name = names.object(schema, view.getName());
        final List<String> columns = viewOutputColumns(view, schema);
        if (columns == null || columns.isEmpty()) {
            return view.ddl(name);
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("create or replace ");
        if (view.isSecure()) {
            sb.append("secure ");
        }
        if (view.isRecursive()) {
            sb.append("recursive ");
        }
        sb.append("view ").append(name).append("(");
        for (int i = 0; i < columns.size(); i++) {
            sb.append(i == 0 ? "\n\t" : ",\n\t").append(SqlIdentifiers.spellCanonicalEscaped(columns.get(i)))
              .append(columnComment(view.getResolvedColumns(), columns.get(i)));
        }
        sb.append("\n)");
        if (view.hasRowAccessPolicy()) {
            sb.append(' ').append(rowAccessPolicy(view.getRowAccessPolicyName(), view.getRowAccessPolicyColumns(),
                columns, schema)).append('\n');
        }
        final String viewTags = tagAssignments(view, schema);
        if (!viewTags.isEmpty()) {
            sb.append(" WITH TAG (").append(viewTags).append(")\n");
        }
        sb.append(commentClause(view.getComment())).append(" as ");
        return sb.append(SqlObject.withoutTrailingSemicolon(view.getShownBody())).append(";").toString();
    }

    /** {@code  COMMENT 'c'} for a derived column that carries a comment, else nothing. */
    private static String columnComment(final List<TableColumn> resolved, final String column) {
        if (resolved != null) {
            for (final TableColumn candidate : resolved) {
                if (candidate.getName().equals(column) && ObjectDdl.hasText(candidate.getComment())) {
                    return " COMMENT " + ObjectDdl.quoted(candidate.getComment());
                }
            }
        }
        return "";
    }

    /** A derived relation's COMMENT after its column list, ending its own line; nothing without one. */
    private static String commentClause(final String comment) {
        return ObjectDdl.hasText(comment) ? " COMMENT=" + ObjectDdl.quoted(comment) + "\n" : "";
    }

    /**
     * The view's output column names: the declared list, else the columns its definition resolved to when it
     * was created, else derived by running the definition.
     */
    private List<String> viewOutputColumns(final View view, final Schema schema) {
        if (view.hasExplicitColumnNames()) {
            return view.getColumnNames();
        }
        if (view.hasResolvedColumns()) {
            final List<String> names = new ArrayList<String>();
            for (final TableColumn column : view.getResolvedColumns()) {
                names.add(column.getName());
            }
            return names;
        }
        return queryColumns(view.getDefinition(), schema);
    }

    /**
     * The column names a query produces, planned in the schema that holds its relation and never run, or null when
     * there is no runner or the query does not plan. The session's own database and schema come back afterwards.
     */
    private List<String> queryColumns(final String query, final Schema schema) {
        if (queryRunner == null || query == null || Boolean.TRUE.equals(DERIVING.get())) {
            return null;
        }
        DERIVING.set(Boolean.TRUE);
        final String[] sessionScope = catalog.currentSessionScope();
        catalog.beginSessionScope(schema.getDatabaseName(), schema.getName());
        try {
            final ResultSet rs = queryRunner.runQuery(query);
            if (rs == null) {
                return null;
            }
            final List<String> names = new ArrayList<String>();
            for (final ResultSetColumn column : rs.getColumns()) {
                names.add(column.getName());
            }
            return names;
        } catch (final RuntimeException e) {
            // A definition that no longer plans (e.g. a dropped base table) falls back to the list-free form.
            return null;
        } finally {
            catalog.restoreSessionScope(sessionScope);
            DERIVING.remove();
        }
    }

    /**
     * {@code create or replace [secure ]materialized view M(\n\tX COMMENT 'c'\n)[ COMMENT='…'\n] as SELECT …;}
     * — the column list as a view's, from the declared names or the columns the definition resolved to, each with
     * the comment it declares (live-verified).
     */
    String materializedViewDdl(final MaterializedView view, final Schema schema, final DdlNames names) {
        final String name = names.object(schema, view.getName());
        List<String> columns = view.hasExplicitColumnNames() ? view.getColumnNames() : null;
        if (columns == null && view.hasResolvedColumns()) {
            columns = new ArrayList<String>();
            for (final TableColumn column : view.getResolvedColumns()) {
                columns.add(column.getName());
            }
        }
        if (columns == null) {
            columns = queryColumns(view.getDefinition(), schema);
        }
        if (columns == null || columns.isEmpty()) {
            return view.ddl(name);
        }
        final StringBuilder sb = new StringBuilder("create or replace ");
        if (view.isSecure()) {
            sb.append("secure ");
        }
        sb.append("materialized view ").append(name).append("(");
        for (int i = 0; i < columns.size(); i++) {
            sb.append(i == 0 ? "\n\t" : ",\n\t").append(SqlIdentifiers.spellCanonicalEscaped(columns.get(i)))
              .append(columnComment(view.getResolvedColumns(), columns.get(i)));
        }
        sb.append("\n)").append(commentClause(view.getComment()));
        if (!view.getClusterKeys().isEmpty()) {
            sb.append(" cluster by (").append(String.join(", ", view.getClusterKeys())).append(")");
        }
        sb.append(" as ");
        return sb.append(SqlObject.withoutTrailingSemicolon(view.getDefinition()).trim()).append(";").toString();
    }

    /**
     * {@code create or replace [transient ]dynamic table D(\n\tX\n) target_lag = '…' refresh_mode = AUTO
     * initialize = ON_CREATE warehouse = W}, a line break, then {@code  COMMENT='…'} on a line of its own and
     * {@code  cluster by (…)} when set, and {@code  as} and the query.
     */
    String dynamicTableDdl(final DynamicTable table, final Schema schema, final DdlNames names) {
        final String name = names.object(schema, table.getName());
        final StringBuilder sb = new StringBuilder("create or replace ");
        if (table.isTransient()) {
            sb.append("transient ");
        }
        sb.append("dynamic table ").append(name);
        if (!table.getDeclaredColumns().isEmpty()) {
            sb.append(declaredColumnList(table.getDeclaredColumns()));
        } else {
            final List<String> columns = queryColumns(table.getQuery(), schema);
            if (columns != null && !columns.isEmpty()) {
                sb.append(columnList(columns));
            }
        }
        sb.append(" target_lag = ").append(ObjectDdl.doubledQuotes(String.valueOf(table.getTargetLag())))
          .append(" refresh_mode = ").append(table.getRefreshMode())
          .append(" initialize = ").append(table.getInitialize())
          .append(" warehouse = ").append(table.getWarehouse()).append('\n');
        if (ObjectDdl.hasText(table.getComment())) {
            sb.append(" COMMENT=").append(ObjectDdl.quoted(table.getComment())).append('\n');
        }
        if (!table.getClusterKeys().isEmpty()) {
            sb.append(" cluster by (").append(String.join(", ", table.getClusterKeys())).append(")");
        }
        return sb.append(" as ").append(SqlObject.withoutTrailingSemicolon(table.getQuery()).trim()).append(";")
            .toString();
    }

    /**
     * {@code (\n\tX COMMENT 'c',\n\tY\n)} — the columns a dynamic table declares, each with its comment; a declared
     * type is not written (live-verified).
     */
    private static String declaredColumnList(final List<DynamicTableColumn> columns) {
        final StringBuilder list = new StringBuilder("(");
        for (int i = 0; i < columns.size(); i++) {
            list.append(i == 0 ? "\n\t" : ",\n\t").append(SqlIdentifiers.spellCanonicalEscaped(columns.get(i).getName()));
            if (columns.get(i).getComment() != null) {
                list.append(" COMMENT ").append(ObjectDdl.quoted(columns.get(i).getComment()));
            }
        }
        return list.append("\n)").toString();
    }

    /** {@code (\n\tA,\n\tB\n)} — a derived relation's output columns, one tab-indented name per line. */
    private static String columnList(final List<String> columns) {
        final StringBuilder list = new StringBuilder("(");
        for (int i = 0; i < columns.size(); i++) {
            list.append(i == 0 ? "\n\t" : ",\n\t").append(SqlIdentifiers.spellCanonicalEscaped(columns.get(i)));
        }
        return list.append("\n)").toString();
    }

    // ─────────────────────────── SEQUENCE ───────────────────────────

    /** {@code create or replace sequence S start with N increment by N noorder;} — a COMMENT is not rendered. */
    String sequenceDdl(final Sequence seq, final String name) {
        return "create or replace sequence " + name + " start with " + seq.getStartValue()
            + " increment by " + seq.getIncrement() + (seq.isOrder() ? " order" : " noorder") + ";";
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 3; }

    // ─────────────────────────── FUNCTION / PROCEDURE ───────────────────────────

    /**
     * A routine's DDL, as a real account renders it: the name and every parameter double-quoted, the
     * types canonical, LANGUAGE always spelled even where the CREATE left it out, STRICT and IMMUTABLE
     * each on a line of their own when set, then a handler's RUNTIME_VERSION and HANDLER, MEMOIZABLE
     * prefixing whatever line follows it, and the body re-quoted as ONE single-quoted literal whatever
     * quoting the CREATE used. CALLED ON NULL INPUT, a NOT NULL return and TEMPORARY are not rendered at
     * all (live-verified).
     */
    String functionDdl(final Function function, final Schema schema, final DdlNames names) {
        final StringBuilder sb = new StringBuilder("CREATE OR REPLACE ");
        if (function.isSecure()) {
            sb.append("SECURE ");
        }
        sb.append("FUNCTION ").append(names.routine(schema, function.getName()))
          .append(routineParameters(function.getParameters())).append("\n");
        sb.append("RETURNS ").append(function.isTableFunction()
            ? "TABLE (" + returnColumns(function.getReturnColumns()) + ")"
            : SqlTypeNames.routineType(function.getReturnType(), function.getReturnSized())).append("\n");
        sb.append("LANGUAGE ").append(function.getLanguage().toUpperCase(Locale.ROOT)).append("\n");
        if ("RETURNS NULL ON NULL INPUT".equals(function.getNullHandling())) {
            sb.append("STRICT\n");
        }
        if ("IMMUTABLE".equals(function.getVolatility())) {
            sb.append("IMMUTABLE\n");
        }
        sb.append(handlerProperties(function.getLanguage(), function.getRuntimeVersion(),
            function.getEnvironment().getPackages(), function.getHandler(), function.getImports(),
            function.getEnvironment()));
        final String memoizable = function.isMemoizable() ? "MEMOIZABLE " : "";
        if (function.getComment() != null && !function.getComment().isEmpty()) {
            sb.append(memoizable).append("COMMENT=").append(ObjectDdl.quoted(function.getComment())).append("\n");
        } else {
            sb.append(memoizable);
        }
        return sb.append(bodyClause(function.getBody())).toString();
    }

    /**
     * A procedure's DDL, which adds EXECUTE AS where a function has nothing: SECURE before the kind, then LANGUAGE,
     * STRICT and IMMUTABLE each on a line of their own when declared, a handler's RUNTIME_VERSION, PACKAGES and
     * HANDLER, then COMMENT, then EXECUTE AS, then the body (live-verified).
     */
    String procedureDdl(final Procedure procedure, final Schema schema, final DdlNames names) {
        final StringBuilder sb = new StringBuilder("CREATE OR REPLACE ");
        if (procedure.isSecure()) {
            sb.append("SECURE ");
        }
        sb.append("PROCEDURE ");
        sb.append(names.routine(schema, procedure.getName())).append(routineParameters(procedure.getParameters()))
          .append("\n");
        sb.append("RETURNS ").append(procedure.returnsTable()
            ? "TABLE (" + returnColumns(procedure.getReturnColumns()) + ")"
            : SqlTypeNames.routineType(procedure.getReturnType(), procedure.getReturnSized())).append("\n");
        sb.append("LANGUAGE ").append(procedure.getLanguage().toUpperCase(Locale.ROOT)).append("\n");
        if ("RETURNS NULL ON NULL INPUT".equals(procedure.getNullHandling())) {
            sb.append("STRICT\n");
        }
        if ("IMMUTABLE".equals(procedure.getVolatility())) {
            sb.append("IMMUTABLE\n");
        }
        sb.append(handlerProperties(procedure.getLanguage(), procedure.getRuntimeVersion(), procedure.getPackages(),
            procedure.getHandler(), procedure.getImports(), procedure.getEnvironment()));
        if (procedure.getComment() != null && !procedure.getComment().isEmpty()) {
            sb.append("COMMENT=").append(ObjectDdl.quoted(procedure.getComment())).append("\n");
        }
        sb.append("EXECUTE AS ").append(procedure.getExecuteAs() == null ? "OWNER"
            : procedure.getExecuteAs().toUpperCase(Locale.ROOT)).append("\n");
        // A procedure's body is its owner's to read: any other role is given an empty one.
        return sb.append(bodyClause(procedure.getBody() != null && isAnothers(procedure.getOwner())
            ? "" : procedure.getBody())).toString();
    }

    /**
     * How a routine's DDL ends: {@code AS '<body>';}, and for a routine whose handler comes from an import and
     * which so has no body, the semicolon alone on a line of its own (live-verified).
     */
    private String bodyClause(final String body) {
        return body == null ? ";" : "AS " + quotedBody(body) + ";";
    }

    /**
     * The properties a handler-backed routine (Java, Python, Scala) carries, each on a line of its own in
     * the account's order: {@code RUNTIME_VERSION = '3.11'}, a Python routine's {@code ARTIFACT_REPOSITORY},
     * {@code PACKAGES = ('a','b')} with no space after a comma, {@code HANDLER = 'run'}, {@code IMPORTS} and
     * {@code TARGET_PATH}. Each is left out when the routine has none, so a SQL or JavaScript routine renders
     * nothing here; the secrets and integrations a routine names are not rendered at all (live-verified).
     */
    private static String handlerProperties(final String language, final String runtimeVersion,
                                            final List<String> packages, final String handler,
                                            final List<String> imports, final RoutineEnvironment environment) {
        final StringBuilder out = new StringBuilder();
        if (runtimeVersion != null) {
            out.append("RUNTIME_VERSION = ").append(quotedText(runtimeVersion)).append("\n");
        }
        if ("PYTHON".equalsIgnoreCase(language)) {
            final RoutineEnvironment described = environment.copy();
            described.setPackages(packages);
            if (described.describedArtifactRepository() != null) {
                out.append("ARTIFACT_REPOSITORY = ").append(described.describedArtifactRepository()).append("\n");
            }
        }
        out.append(quotedList("PACKAGES", packages));
        if (handler != null) {
            out.append("HANDLER = ").append(quotedText(handler)).append("\n");
        }
        out.append(quotedList("IMPORTS", imports));
        if (environment.getTargetPath() != null) {
            out.append("TARGET_PATH = ").append(quotedText(environment.getTargetPath())).append("\n");
        }
        return out.toString();
    }

    /** A property whose value is a list of strings, on a line of its own; nothing for an empty list. */
    private static String quotedList(final String property, final List<String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        final StringBuilder out = new StringBuilder(property).append(" = (");
        for (int i = 0; i < items.size(); i++) {
            out.append(i > 0 ? "," : "").append(quotedText(items.get(i)));
        }
        return out.append(")\n").toString();
    }

    /** A property value as one single-quoted literal, its own quotes doubled. */
    private static String quotedText(final String text) {
        return "'" + text.replace("'", "''") + "'";
    }

    /**
     * The routine a {@code name(TYPES)} argument names. The argument is read whole as a routine reference: the
     * name resolves as an identifier reference does, and the types pick the overload as DROP FUNCTION's do.
     * Anything else — no parentheses, an argument written with its name, a type no overload takes — names
     * nothing, and is refused echoing the argument AS WRITTEN, which is how the account echoes it.
     */
    private Object routine(final String written, final boolean procedure) {
        final FrostlakeParser.RoutineReferenceContext reference = RoutineReferenceText.parse(written);
        final List<DataType> types = reference == null ? null
            : RoutineReferenceText.writtenTypes(reference.dataTypeList());
        if (types == null) {
            throw objectDoesNotExist(written);
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(reference.qualifiedName());
        try {
            final Schema owner = owner(parts);
            return procedure
                ? owner.getProcedureByWrittenSignature(parts[parts.length - 1], types)
                : owner.getFunctionByWrittenSignature(parts[parts.length - 1], types);
        } catch (final RuntimeException missing) {
            throw objectDoesNotExist(written);
        }
    }

    /** The schema a routine argument places its routine in; {@code routine} has already found the routine there. */
    private Schema routineOwner(final String written) {
        return owner(ParseTreeText.qualifiedNameParts(RoutineReferenceText.parse(written).qualifiedName()));
    }

    private static RuntimeException objectDoesNotExist(final String written) {
        return new RuntimeException(SqlCompilationError.of(
            "Object '" + written + "' does not exist or not authorized."));
    }

    /**
     * {@code ("X" NUMBER(38,0) DEFAULT 1, …)} — every parameter quoted, its default as the plan it compiled to
     * prints it: {@code DEFAULT CAST('2020-01-01' AS DATE)} for {@code '2020-01-01'::DATE}, {@code DEFAULT null}
     * for {@code NULL} (live-verified).
     */
    private String routineParameters(final List<Parameter> parameters) {
        final StringBuilder out = new StringBuilder("(");
        for (int i = 0; parameters != null && i < parameters.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            final Parameter parameter = parameters.get(i);
            // A text or a binary declared with no length reads bare, and one declared with its length keeps it.
            out.append(quotedName(parameter.getName())).append(" ")
               .append(SqlTypeNames.routineType(parameter.getDataType(), Boolean.valueOf(!parameter.isUnsized())));
            if (parameter.hasDefault()) {
                out.append(" DEFAULT ").append(parameter.getToldDefault());
            }
        }
        return out.append(")").toString();
    }

    /** A table return's columns, quoted and typed as the parameters are. */
    private String returnColumns(final List<Parameter> columns) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; columns != null && i < columns.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(quotedName(columns.get(i).getName())).append(" ")
               .append(ReturnColumnTypes.ddlText(columns.get(i).getDataType()));
        }
        return out.toString();
    }


    /** The body as one single-quoted literal, its own quotes doubled, whatever quoting the CREATE used. */
    private String quotedBody(final String body) {
        return "'" + (body == null ? "" : body).replace("'", "''") + "'";
    }

    /** A routine's parameter names are always double-quoted in its DDL. */
    private String quotedName(final String name) {
        return "\"" + name + "\"";
    }
}
