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
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.View;
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
 * {@code INFORMATION_SCHEMA.POLICY_REFERENCES} — every policy attached to one object, or every
 * object one policy is attached to. It is THE readback surface for attachments: a masking or
 * projection policy names its column in {@code REF_COLUMN_NAME}, while a row access or aggregation
 * policy leaves that null and lists its argument columns in {@code REF_ARG_COLUMN_NAMES}.
 *
 * <p>It takes either {@code POLICY_NAME => 'p'} or the pair {@code REF_ENTITY_NAME => 'o',
 * REF_ENTITY_DOMAIN => 'TABLE'|'VIEW'} — one of the two, never a mixture and never neither
 * (live-verified, including the sentence that says so). Either name is read as an identifier is: an unquoted part
 * folds to upper case, a double-quoted one is kept as written, and a name with fewer than three parts is completed
 * from the session's database and schema, so {@code 'mydb.myschema.mytable'} finds the table wherever the session
 * is (live-verified, in either case). A name that finds nothing is refused without the privilege sentence other
 * refusals carry, a missing policy as a missing table (live-verified).
 */
public class PolicyReferencesFunction extends TableFunction {

    /** The table-like domains POLICY_REFERENCES reads beside the ones TAG_REFERENCES knows. */
    private static final Set<String> TABLE_LIKE_DOMAINS = new HashSet<String>(Arrays.asList("VIEW",
        "MATERIALIZED VIEW", "EXTERNAL TABLE"));

    /** The domains whose object POLICY_REFERENCES finds as TAG_REFERENCES does, listing no attachment of it. */
    private static final Set<String> OBJECT_DOMAINS = new HashSet<String>(Arrays.asList("TAG", "USER", "ACCOUNT"));

    /** Live's column order. */
    private static final String[] COLUMN_NAMES = {
        "POLICY_DB", "POLICY_SCHEMA", "POLICY_NAME", "POLICY_KIND", "REF_DATABASE_NAME",
        "REF_SCHEMA_NAME", "REF_ENTITY_NAME", "REF_ENTITY_DOMAIN", "REF_COLUMN_NAME",
        "REF_ARG_COLUMN_NAMES", "TAG_DATABASE", "TAG_SCHEMA", "TAG_NAME", "POLICY_STATUS"
    };

    /** The engine has no policy lifecycle, so every attachment reads back active. */
    private static final String ACTIVE = "ACTIVE";

    private final Catalog catalog;

    public PolicyReferencesFunction(final Catalog catalog) {
        super("POLICY_REFERENCES");
        this.catalog = catalog;
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // Validated in execute, where the argument combination is judged as a whole.
    }

    @Override
    public ResultSet execute(final List<Object> positionalArgs) {
        // Live reads a positional call as (policy_name, policy_kind) and refuses the kind it finds.
        if (positionalArgs != null && positionalArgs.size() > 1 && positionalArgs.get(1) != null) {
            throw new RuntimeException("Unknown policy kind: "
                + positionalArgs.get(1).toString().toUpperCase(Locale.ROOT) + ".");
        }
        throw new RuntimeException(argumentRequirement());
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        final String policyName = argument(namedArgs, "POLICY_NAME");
        final String entityName = argument(namedArgs, "REF_ENTITY_NAME");
        final String entityDomain = argument(namedArgs, "REF_ENTITY_DOMAIN");
        if (policyName == null && (entityName == null || entityDomain == null)) {
            throw new RuntimeException(argumentRequirement());
        }
        final List<Row> rows = new ArrayList<>();
        if (policyName != null) {
            appendByPolicy(policyName, rows);
        } else {
            appendByEntity(entityName, entityDomain, rows);
        }
        return new ResultSet(columns(), rows);
    }

    /** Live's sentence for a call that names neither shape, or only half of the entity pair. */
    private String argumentRequirement() {
        return SqlCompilationError.PREFIX + " function 'information_schema.policy_references' expects"
            + " argument (policy_name=>'policyName') or (ref_entity_name=>'name',"
            + " ref_entity_domain=>'domain').";
    }

    /**
     * The attachments on one entity. The domain is one TAG_REFERENCES knows, matched as written but for case, or it
     * is {@code Unknown domain: <as written>.}; TABLE and VIEW list their kind's attachments, EXTERNAL TABLE none
     * here, TAG, USER and ACCOUNT find their object as TAG_REFERENCES finds it and list none, and any other domain is
     * an invalid value, named with underscores (live-verified).
     */
    private void appendByEntity(final String entityName, final String entityDomain,
                                final List<Row> rows) {
        final String domain = entityDomain.toUpperCase(Locale.ROOT);
        final String words = domain.replace('_', ' ');
        if (!TagReferencesFunction.isDomain(words) && !TABLE_LIKE_DOMAINS.contains(words)) {
            throw new RuntimeException("Unknown domain: " + entityDomain + ".");
        }
        if ("EXTERNAL TABLE".equals(words)) {
            return;
        }
        if (OBJECT_DOMAINS.contains(words)) {
            new TagReferencesFunction(catalog).requireObject(entityName, words);
            return;
        }
        if (!"TABLE".equals(words) && !"VIEW".equals(words)) {
            throw new RuntimeException(SqlCompilationError.inline("Invalid value '" + words.replace(' ', '_')
                + "' for argument 'ref_entity_domain' for function 'information_schema.policy_references'"));
        }
        final String[] written = SqlIdentifiers.canonicalTextParts(entityName);
        requireContainers(written);
        final Schema schema = owningSchema(written);
        final String name = written[written.length - 1];
        final Table table = schema == null ? null : schema.tableExact(name);
        final View view = schema == null || table != null ? null : viewExact(schema, name);
        if (table == null && view == null) {
            // Refused in the domain's word, whichever kind of object the name was meant for.
            throw missingEntity("TABLE".equals(domain) ? "Table" : "View", written);
        }
        // Either domain takes a table or a view, and lists the attachments of its own kind only: a view named under
        // 'table', or a table under 'view', answers no rows (live-verified).
        if (table != null && "TABLE".equals(domain)) {
            appendTableRows(schema, table, rows);
        } else if (view != null && "VIEW".equals(domain)) {
            appendViewRows(schema, view, rows);
        }
    }

    /**
     * The attachments of one policy. The policy is the one its name places — a bare name in the session's schema —
     * and only attachments to that very policy are listed, not those to a namesake in another schema.
     */
    private void appendByPolicy(final String policyName, final List<Row> rows) {
        final String[] written = SqlIdentifiers.canonicalTextParts(policyName);
        requireContainers(written);
        final Schema schema = owningSchema(written);
        final String name = written[written.length - 1];
        if (schema == null || !policyExists(schema, name)) {
            // Live names the policy in full however it was written (live-verified).
            throw new RuntimeException(SqlCompilationError.doesNotExist("Policy",
                QualifiedName.join(completed(written))));
        }
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema each : database.getAllSchemas()) {
                for (final Table table : each.getTables()) {
                    appendTableRows(each, table, rows);
                }
                for (final View view : each.getViews()) {
                    appendViewRows(each, view, rows);
                }
            }
        }
        final List<Row> matching = new ArrayList<>();
        for (final Row row : rows) {
            if (schema.getDatabaseName().equals(row.getValue(0)) && schema.getName().equals(row.getValue(1))
                && name.equals(row.getValue(2))) {
                matching.add(row);
            }
        }
        rows.clear();
        rows.addAll(matching);
    }

    /**
     * The database and the schema a name places its object in, which a miss names before the object: {@code
     * Database 'D' does not exist or not authorized.} or {@code Schema 'D.S' …}, each asking for USAGE
     * (live-verified).
     *
     * @param written the name's canonical parts
     */
    private void requireContainers(final String[] written) {
        final String[] full = completed(written);
        if (full.length != 3 || full[0] == null || full[1] == null) {
            return;
        }
        final Database database = catalog.findDatabase(full[0]);
        if (database == null || !database.getName().equals(full[0])) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Database", full[0]));
        }
        if (!database.hasSchemaExact(full[1])) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Schema", QualifiedName.join(full[0],
                full[1])));
        }
    }

    private boolean policyExists(final Schema schema, final String name) {
        return schema.getMaskingPolicy(name) != null || schema.getRowAccessPolicy(name) != null
            || schema.getProjectionPolicy(name) != null || schema.getAggregationPolicy(name) != null
            || schema.getJoinPolicy(name) != null;
    }

    /**
     * The schema a name places its object in, read as a FROM reference reads one: a bare name in the session's
     * schema, a two-part one in a schema of the session's database, a three-part one where it says, each part
     * matched exactly. Null when that database or schema is not there, or the session cannot place the name.
     *
     * @param written the name's canonical parts ({@link SqlIdentifiers#canonicalTextParts})
     */
    private Schema owningSchema(final String[] written) {
        final String[] full = completed(written);
        if (full.length != 3 || full[0] == null || full[1] == null) {
            return null;
        }
        final Database database = catalog.findDatabase(full[0]);
        if (database == null || !database.getName().equals(full[0]) || !database.hasSchemaExact(full[1])) {
            return null;
        }
        return database.schemaExact(full[1]);
    }

    /** A name's parts completed to three from the session's database and schema, as far as the session has them. */
    private String[] completed(final String[] written) {
        if (written.length == 1) {
            return new String[] {catalog.getCurrentDatabase(), catalog.getCurrentSchema(), written[0]};
        }
        if (written.length == 2) {
            return new String[] {catalog.getCurrentDatabase(), written[0], written[1]};
        }
        return written;
    }

    /**
     * The refusal for a REF_ENTITY_NAME that names nothing, worded as live words it: {@code Table 'DB.PUBLIC.T' does
     * not exist or not authorized.} with the privilege sentence on TABLE, or {@code View '...'} under 'view'
     * (live-verified). The name is the one a FROM reference reports: as written when it is bare, in full once any
     * qualifier is written.
     */
    private RuntimeException missingEntity(final String kind, final String[] written) {
        final String[] full = completed(written);
        final String reported = written.length > 1 && full.length == 3 && full[0] != null
            ? QualifiedName.join(full) : QualifiedName.join(written);
        return new RuntimeException(SqlCompilationError.doesNotExist(kind, reported));
    }

    /** The view of exactly that name, or null: a reference matches a name exactly, as for a table. */
    private static View viewExact(final Schema schema, final String name) {
        if (!schema.hasView(name)) {
            return null;
        }
        final View view = schema.getView(name);
        return view.getName().equals(name) ? view : null;
    }

    /**
     * One row per attachment on a table, ordered by policy name as live orders them, and a policy attached to
     * several columns once for each.
     */
    private void appendTableRows(final Schema schema, final Table table, final List<Row> rows) {
        final Map<String, List<Row>> byPolicy = new TreeMap<>();
        for (final TableColumn column : table.getColumns()) {
            if (column.hasMaskingPolicy()) {
                listed(byPolicy, attachment(schema, column.getMaskingPolicyName(), "MASKING_POLICY",
                    table.getName(), "TABLE", column.getName(), null));
            }
            if (column.hasProjectionPolicy()) {
                listed(byPolicy, attachment(schema, column.getProjectionPolicyName(), "PROJECTION_POLICY",
                    table.getName(), "TABLE", column.getName(), null));
            }
        }
        if (table.hasRowAccessPolicy()) {
            listed(byPolicy, attachment(schema, table.getRowAccessPolicyName(), "ROW_ACCESS_POLICY",
                table.getName(), "TABLE", null, table.getRowAccessPolicyColumns()));
        }
        if (table.hasAggregationPolicy()) {
            listed(byPolicy, attachment(schema, table.getAggregationPolicyName(), "AGGREGATION_POLICY",
                table.getName(), "TABLE", null, table.getAggregationEntityKey()));
        }
        if (table.hasJoinPolicy()) {
            listed(byPolicy, attachment(schema, table.getJoinPolicyName(), "JOIN_POLICY", table.getName(), "TABLE",
                null, null));
        }
        for (final List<Row> attachments : byPolicy.values()) {
            rows.addAll(attachments);
        }
    }

    /**
     * File an attachment row under its policy: by name first, as live orders them, then by the policy's database
     * and schema, so two policies of one name in different schemas stay apart.
     */
    private static void listed(final Map<String, List<Row>> byPolicy, final Row row) {
        final String key = row.getValue(2) + "\u0000" + row.getValue(0) + "\u0000" + row.getValue(1);
        List<Row> attachments = byPolicy.get(key);
        if (attachments == null) {
            attachments = new ArrayList<>();
            byPolicy.put(key, attachments);
        }
        attachments.add(row);
    }

    private void appendViewRows(final Schema schema, final View view, final List<Row> rows) {
        if (view.hasRowAccessPolicy()) {
            rows.add(attachment(schema, view.getRowAccessPolicyName(), "ROW_ACCESS_POLICY",
                view.getName(), "VIEW", null, view.getRowAccessPolicyColumns()));
        }
    }

    /**
     * One attachment's row. POLICY_DB and POLICY_SCHEMA are where the policy lives, which the attachment records in
     * full, and REF_DATABASE_NAME and REF_SCHEMA_NAME where the object holding it does; a shorter recorded name is
     * read in the object's schema.
     */
    private Row attachment(final Schema schema, final String policyName, final String kind,
                           final String entityName, final String domain, final String columnName,
                           final List<String> argumentColumns) {
        final String[] policy = QualifiedName.parse(policyName).parts();
        final String policyDatabase = policy.length >= 3 ? policy[policy.length - 3] : schema.getDatabaseName();
        final String policySchema = policy.length >= 2 ? policy[policy.length - 2] : schema.getName();
        return new Row(Arrays.asList(
            policyDatabase, policySchema, policy[policy.length - 1], kind,
            schema.getDatabaseName(), schema.getName(), entityName, domain,
            columnName, renderArgumentColumns(argumentColumns),
            null, null, null, ACTIVE));
    }

    /** Live renders the argument columns as a spaced JSON array: {@code [ "A", "B" ]}. */
    private String renderArgumentColumns(final List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            return null;
        }
        final StringBuilder out = new StringBuilder("[ ");
        for (int i = 0; i < columns.size(); i++) {
            out.append(i == 0 ? "" : ", ").append('"')
               .append(columns.get(i).toUpperCase(Locale.ROOT)).append('"');
        }
        return out.append(" ]").toString();
    }

    private String argument(final Map<String, Object> namedArgs, final String name) {
        if (namedArgs == null) {
            return null;
        }
        for (final Map.Entry<String, Object> entry : namedArgs.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name) && entry.getValue() != null) {
                return entry.getValue().toString();
            }
        }
        return null;
    }

    private List<ResultSetColumn> columns() {
        final List<ResultSetColumn> columns = new ArrayList<>();
        for (final String name : COLUMN_NAMES) {
            columns.add(new ResultSetColumn(name, StringType.VARCHAR));
        }
        return columns;
    }
}
