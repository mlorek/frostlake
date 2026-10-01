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

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.commands.DataTypeParser;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.table.QueryRunner;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.SqlObject;
import dev.frostlake.metastore.model.AggregationPolicy;
import dev.frostlake.metastore.model.Alert;
import dev.frostlake.metastore.model.AppObject;
import dev.frostlake.metastore.model.AppObjectKind;
import dev.frostlake.metastore.model.CheckConstraint;
import dev.frostlake.metastore.model.Contact;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DynamicTable;
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
import dev.frostlake.metastore.model.RowAccessPolicy;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurityObject;
import dev.frostlake.metastore.model.SecurityObjectKind;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.UniqueConstraint;
import dev.frostlake.metastore.model.View;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.DataType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;

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

    private final Catalog catalog;
    /**
     * Answers a defining query's SHAPE — its columns and no row — without running it, to derive a view's, a
     * materialized view's or a dynamic table's output column names; null until the executor wires it.
     */
    private final QueryRunner queryRunner;
    /** The recursive text of a schema and a database, built from this function's renderers. */
    private final SchemaDdl containers = new SchemaDdl(this);

    public GetDdl(final Catalog catalog) {
        this(catalog, null);
    }

    public GetDdl(final Catalog catalog, final QueryRunner queryRunner) {
        super("GET_DDL", StringType.VARCHAR);
        this.catalog = catalog;
        this.queryRunner = queryRunner;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        for (final Object arg : args) {
            if (arg == null) {
                return null;
            }
        }
        final String typeText = args.get(0).toString().toUpperCase();
        final String objectType = typeText.replace(' ', '_');
        final DdlNames names = new DdlNames(args.size() > 2 ? args.get(2) : null);
        final String written = args.get(1).toString();
        switch (objectType) {
            case "SCHEMA":
                return containers.schema(
                    catalog.resolveSchema(QualifiedName.of(SqlIdentifiers.canonicalTextParts(written))), names);
            case "DATABASE":
                return containers.database(databaseNamed(written), names);
            case "TABLE":
            case "VIEW":
            case "MATERIALIZED_VIEW":
            case "DYNAMIC_TABLE":
                return relationDdl(objectType, written, names);
            case "EVENT_TABLE":
                throw new RuntimeException("Unsupported feature 'EVENT_TABLE'.");
            case "FUNCTION":
                return functionDdl((Function) routine(written.trim(), false), routineOwner(written.trim()), names);
            case "PROCEDURE":
                return procedureDdl((Procedure) routine(written.trim(), true), routineOwner(written.trim()), names);
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
    private String relationDdl(final String objectType, final String written, final DdlNames names) {
        final String[] parts = nameParts(written);
        final Schema owner = owner(parts);
        final String name = parts[parts.length - 1];
        final Table table = owner.tableExact(name);
        if (table != null) {
            return tableDdl(table, owner, names);
        }
        if (owner.hasView(name) && named(owner.getView(name).getName(), name)) {
            return viewDdl(owner.getView(name), owner, names);
        }
        if (owner.hasMaterializedView(name) && named(owner.getMaterializedView(name).getName(), name)) {
            return materializedViewDdl(owner.getMaterializedView(name), owner, names);
        }
        if (owner.hasDynamicTable(name) && named(owner.getDynamicTable(name).getName(), name)) {
            return dynamicTableDdl(owner.getDynamicTable(name), owner, names);
        }
        switch (objectType) {
            case "VIEW":
                throw missing(parts, owner, "View", false);
            case "MATERIALIZED_VIEW":
                throw missing(parts, owner, "Materialized view", false);
            case "DYNAMIC_TABLE":
                throw missing(parts, owner, "Dynamic table", true);
            default:
                throw missing(parts, owner, "Table", false);
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
                if (!owner.hasSequenceExact(name)) {
                    throw missing(parts, owner, "Sequence", false);
                }
                return sequenceDdl(owner.getSequence(name), names.object(owner, name));
            case "STREAM": {
                final Stream stream = find(owner, name, objectType);
                if (stream == null) {
                    throw missing(parts, owner, "Stream", false);
                }
                return ObjectDdl.stream(stream, names.object(owner, name));
            }
            case "TASK": {
                final Task task = find(owner, name, objectType);
                if (task == null) {
                    throw missing(parts, owner, "Task", false);
                }
                return ObjectDdl.task(task, owner, names.object(owner, name));
            }
            case "PIPE": {
                final Pipe pipe = find(owner, name, objectType);
                if (pipe == null) {
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
        // A clustering key is spelled between the name and the column list, the paren following it directly.
        if (!table.getClusterKeys().isEmpty()) {
            sb.append(" cluster by (").append(String.join(", ", table.getClusterKeys())).append(")(");
        } else {
            sb.append(" (");
        }

        // A UNIQUE constraint spanning several columns is ONE constraint, so it renders as a table-level
        // line; re-emitting it as an inline UNIQUE per column would recreate it as several independent
        // single-column constraints. A single-column UNIQUE keeps the inline form Snowflake prints.
        final List<UniqueConstraint> multiColumnUniques = new ArrayList<UniqueConstraint>();
        for (final UniqueConstraint unique : table.getUniqueConstraints()) {
            if (unique.getColumnNames().size() > 1) {
                multiColumnUniques.add(unique);
            }
        }

        final List<String> lines = new ArrayList<String>();
        for (final TableColumn col : table.getColumns()) {
            lines.add(columnLine(col, spannedByMultiColumnUnique(multiColumnUniques, col.getName()), schema));
        }
        for (final UniqueConstraint unique : multiColumnUniques) {
            lines.add("unique (" + spelledNames(unique.getColumnNames()) + ")");
        }
        if (!table.getPrimaryKeys().isEmpty()) {
            lines.add("primary key (" + spelledNames(table.getPrimaryKeys()) + ")");
        }
        for (final ForeignKeyConstraint fk : table.getForeignKeys()) {
            lines.add("foreign key (" + String.join(", ", fk.getColumnNames()) + ") references "
                + fk.getReferencedTable() + " (" + String.join(", ", fk.getReferencedColumns()) + ")");
        }
        // Every CHECK constraint renders as a TABLE-level line, even one written on the column, and in
        // declaration order (live-verified). An explicit name is spelled, a generated one is not.
        for (final CheckConstraint check : table.getCheckConstraints()) {
            lines.add((check.isAutoNamed() ? "" : "constraint " + check.getName() + " ")
                + "check (" + check.getExpression() + ")");
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
        if (!table.getTagValues().isEmpty()) {
            attachments.add("WITH TAG (" + tagAssignments(table.getTagValues(), schema) + ")");
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

    /** True when a multi-column UNIQUE already renders the column, so its inline UNIQUE must be skipped. */
    private boolean spannedByMultiColumnUnique(final List<UniqueConstraint> uniques, final String columnName) {
        for (final UniqueConstraint unique : uniques) {
            if (unique.covers(columnName)) {
                return true;
            }
        }
        return false;
    }

    /** Column names as SQL text, joined by commas: {@code "c""d", X}. */
    private static String spelledNames(final List<String> names) {
        final StringBuilder spelled = new StringBuilder();
        for (final String name : names) {
            spelled.append(spelled.length() > 0 ? ", " : "").append(SqlIdentifiers.spellCanonicalEscaped(name));
        }
        return spelled.toString();
    }

    private String columnLine(final TableColumn col, final boolean uniqueRenderedAtTableLevel, final Schema schema) {
        final StringBuilder c = new StringBuilder();
        c.append(SqlIdentifiers.spellCanonicalEscaped(col.getName())).append(" ").append(renderType(col.getDataType()));
        if (col.getCollation() != null && !col.getCollation().isEmpty()) {
            c.append(" COLLATE '").append(col.getCollation()).append("'");
        }
        if (col.isAutoIncrement()) {
            c.append(" NOT NULL autoincrement start ").append(col.getIdentityStart())
             .append(" increment ").append(col.getIdentityIncrement());
        } else {
            if (!col.isNullable()) {
                c.append(" NOT NULL");
            }
            if (col.getDefaultValue() != null) {
                c.append(" DEFAULT ").append(renderDefault(col.getDefaultValue()));
            }
        }
        if (col.isUnique() && !col.isPrimaryKey() && !uniqueRenderedAtTableLevel) {
            c.append(" UNIQUE");
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
        }
        if (!col.getTagValues().isEmpty()) {
            c.append(" WITH TAG (").append(tagAssignments(col.getTagValues(), schema)).append(")");
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
     * {@code DB.S.T='v', …} — the tags an object or a column carries, each named where it is defined, in the order
     * of those full names; a value is printed between quotes exactly as stored, its own quotes and backslashes too.
     */
    private String tagAssignments(final Map<String, String> tags, final Schema schema) {
        final Map<String, String> byPath = new TreeMap<String, String>();
        for (final Map.Entry<String, String> tag : tags.entrySet()) {
            byPath.put(tagPath(tag.getKey(), schema), String.valueOf(tag.getValue()));
        }
        final StringBuilder out = new StringBuilder();
        for (final Map.Entry<String, String> tag : byPath.entrySet()) {
            out.append(out.length() > 0 ? ", " : "").append(tag.getKey()).append("='").append(tag.getValue())
               .append('\'');
        }
        return out.toString();
    }

    /**
     * The fully qualified name of the tag an assignment names: the tag of that name in the object's own schema, else
     * in another schema of its database, spelled as the tag itself is named.
     */
    private String tagPath(final String key, final Schema schema) {
        final String[] written = QualifiedName.parse(key).parts();
        if (written.length == 3) {
            return SqlIdentifiers.spellCanonicalEscaped(written[0]) + "." + SqlIdentifiers.spellCanonicalEscaped(written[1])
                + "." + SqlIdentifiers.spellCanonicalEscaped(written[2]);
        }
        final String name = written[written.length - 1];
        Schema home = schema;
        if (!schema.hasTag(name)) {
            try {
                for (final Schema other : catalog.getDatabase(schema.getDatabaseName()).getAllSchemas()) {
                    if (other.hasTag(name)) {
                        home = other;
                        break;
                    }
                }
            } catch (final RuntimeException noDatabase) {
                // The object's own schema names the tag.
            }
        }
        final String spelled = home.hasTag(name) ? home.getTag(name).getName() : name;
        return DdlNames.path(home) + "." + SqlIdentifiers.spellCanonicalEscaped(spelled);
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
        if (!view.getTagValues().isEmpty()) {
            sb.append(" WITH TAG (").append(tagAssignments(view.getTagValues(), schema)).append(")\n");
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
     * {@code create or replace [secure ]materialized view M(\n\tX\n)[ COMMENT='…'\n] as SELECT …;} — the column
     * list as a view's, from the declared names or the columns the definition resolved to.
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
        sb.append("materialized view ").append(name).append(columnList(columns)).append(commentClause(view.getComment()))
          .append(" as ");
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
        final List<String> columns = queryColumns(table.getQuery(), schema);
        if (columns != null && !columns.isEmpty()) {
            sb.append(columnList(columns));
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
            : routineTypeText(function.getReturnType())).append("\n");
        sb.append("LANGUAGE ").append(function.getLanguage().toUpperCase()).append("\n");
        if ("RETURNS NULL ON NULL INPUT".equals(function.getNullHandling())) {
            sb.append("STRICT\n");
        }
        if ("IMMUTABLE".equals(function.getVolatility())) {
            sb.append("IMMUTABLE\n");
        }
        sb.append(handlerProperties(function.getRuntimeVersion(), null, function.getHandler()));
        final String memoizable = function.isMemoizable() ? "MEMOIZABLE " : "";
        if (function.getComment() != null && !function.getComment().isEmpty()) {
            sb.append(memoizable).append("COMMENT=").append(ObjectDdl.quoted(function.getComment()))
              .append("\n").append("AS ");
        } else {
            sb.append(memoizable).append("AS ");
        }
        return sb.append(quotedBody(function.getBody())).append(";").toString();
    }

    /**
     * A procedure's DDL, which adds EXECUTE AS where a function has nothing: LANGUAGE, STRICT and IMMUTABLE each on
     * a line of their own when declared, a handler's RUNTIME_VERSION, PACKAGES and HANDLER, then COMMENT, then
     * EXECUTE AS, then the body (live-verified).
     */
    String procedureDdl(final Procedure procedure, final Schema schema, final DdlNames names) {
        final StringBuilder sb = new StringBuilder("CREATE OR REPLACE PROCEDURE ");
        sb.append(names.routine(schema, procedure.getName())).append(routineParameters(procedure.getParameters()))
          .append("\n");
        sb.append("RETURNS ").append(procedure.returnsTable()
            ? "TABLE (" + returnColumns(procedure.getReturnColumns()) + ")"
            : routineTypeText(procedure.getReturnType())).append("\n");
        sb.append("LANGUAGE ").append(procedure.getLanguage().toUpperCase()).append("\n");
        if ("RETURNS NULL ON NULL INPUT".equals(procedure.getNullHandling())) {
            sb.append("STRICT\n");
        }
        if ("IMMUTABLE".equals(procedure.getVolatility())) {
            sb.append("IMMUTABLE\n");
        }
        sb.append(handlerProperties(procedure.getRuntimeVersion(), procedure.getPackages(), procedure.getHandler()));
        if (procedure.getComment() != null && !procedure.getComment().isEmpty()) {
            sb.append("COMMENT=").append(ObjectDdl.quoted(procedure.getComment())).append("\n");
        }
        sb.append("EXECUTE AS ").append(procedure.getExecuteAs() == null ? "OWNER"
            : procedure.getExecuteAs().toUpperCase()).append("\n");
        return sb.append("AS ").append(quotedBody(procedure.getBody())).append(";").toString();
    }

    /**
     * The properties a handler-backed routine (Java, Python, Scala) carries, each on a line of its own in
     * the account's order: {@code RUNTIME_VERSION = '3.11'}, {@code PACKAGES = ('a','b')} with no space
     * after a comma, {@code HANDLER = 'run'}. Each is left out when the routine has none, so a SQL or
     * JavaScript routine renders nothing here (live-verified).
     */
    private static String handlerProperties(final String runtimeVersion, final List<String> packages,
                                            final String handler) {
        final StringBuilder out = new StringBuilder();
        if (runtimeVersion != null) {
            out.append("RUNTIME_VERSION = ").append(quotedText(runtimeVersion)).append("\n");
        }
        if (packages != null && !packages.isEmpty()) {
            out.append("PACKAGES = (");
            for (int i = 0; i < packages.size(); i++) {
                out.append(i > 0 ? "," : "").append(quotedText(packages.get(i)));
            }
            out.append(")\n");
        }
        if (handler != null) {
            out.append("HANDLER = ").append(quotedText(handler)).append("\n");
        }
        return out.toString();
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
        final FrostlakeParser.RoutineReferenceContext reference = routineReference(written);
        final List<DataType> types = reference == null ? null : writtenTypes(reference.dataTypeList());
        if (types == null) {
            throw objectDoesNotExist(written);
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(reference.qualifiedName());
        try {
            final Schema owner = owner(parts);
            return procedure
                ? owner.getProcedureBySignature(parts[parts.length - 1], types)
                : owner.getFunctionBySignature(parts[parts.length - 1], types);
        } catch (final RuntimeException missing) {
            throw objectDoesNotExist(written);
        }
    }

    /** The schema a routine argument places its routine in; {@code routine} has already found the routine there. */
    private Schema routineOwner(final String written) {
        return owner(ParseTreeText.qualifiedNameParts(routineReference(written).qualifiedName()));
    }

    /** The routine reference a GET_DDL argument spells, or null when the text is not exactly one. */
    private static FrostlakeParser.RoutineReferenceContext routineReference(final String written) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(written));
        lexer.removeErrorListeners();
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.setErrorHandler(new BailErrorStrategy());
        try {
            return parser.routineReference();
        } catch (final ParseCancellationException notAReference) {
            return null;
        }
    }

    /** The types a reference lists, in order, or null when an argument is written with a name or is no type. */
    private static List<DataType> writtenTypes(final FrostlakeParser.DataTypeListContext list) {
        final List<DataType> types = new ArrayList<DataType>();
        if (list == null) {
            return types;
        }
        for (int i = 0; i < list.getChildCount(); i++) {
            final ParseTree child = list.getChild(i);
            if (child instanceof FrostlakeParser.IdentifierContext) {
                return null;
            }
            if (child instanceof FrostlakeParser.DataTypeNameContext) {
                final ParseTree next = i + 1 < list.getChildCount() ? list.getChild(i + 1) : null;
                types.add(DataTypeParser.parse((FrostlakeParser.DataTypeNameContext) child,
                    next instanceof FrostlakeParser.TypeParametersContext
                        ? (FrostlakeParser.TypeParametersContext) next : null));
            }
        }
        return types;
    }

    private static RuntimeException objectDoesNotExist(final String written) {
        return new RuntimeException(SqlCompilationError.of(
            "Object '" + written + "' does not exist or not authorized."));
    }

    /** {@code ("X" NUMBER(38,0) DEFAULT 1, …)} — every parameter quoted, its default kept. */
    private String routineParameters(final List<Parameter> parameters) {
        final StringBuilder out = new StringBuilder("(");
        for (int i = 0; parameters != null && i < parameters.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            final Parameter parameter = parameters.get(i);
            out.append(quotedName(parameter.getName())).append(" ")
               .append(routineTypeText(parameter.getDataType()));
            if (parameter.hasDefault()) {
                out.append(" DEFAULT ").append(parameter.getDefaultValue());
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
               .append(routineTypeText(columns.get(i).getDataType()));
        }
        return out.toString();
    }

    /**
     * A routine's type as the account spells it: the canonical name, except that a string of the
     * default width is spelled BARE - live renders {@code RETURNS VARCHAR} for a bare declaration and
     * {@code VARCHAR(10)} for a sized one.
     */
    private String routineTypeText(final DataType type) {
        return SqlTypeNames.routineType(type);
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
