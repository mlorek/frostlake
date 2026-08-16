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
import dev.frostlake.functions.TableFunction;
import dev.frostlake.metastore.Catalog;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code INFORMATION_SCHEMA.POLICY_REFERENCES} — every policy attached to one object, or every
 * object one policy is attached to. It is THE readback surface for attachments: a masking or
 * projection policy names its column in {@code REF_COLUMN_NAME}, while a row access or aggregation
 * policy leaves that null and lists its argument columns in {@code REF_ARG_COLUMN_NAMES}.
 *
 * <p>It takes either {@code POLICY_NAME => 'p'} or the pair {@code REF_ENTITY_NAME => 'o',
 * REF_ENTITY_DOMAIN => 'TABLE'|'VIEW'} — one of the two, never a mixture and never neither
 * (live-verified, including the sentence that says so).
 */
public class PolicyReferencesFunction extends TableFunction {

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

    private void appendByEntity(final String entityName, final String entityDomain,
                                final List<Row> rows) {
        final String domain = entityDomain.toUpperCase(Locale.ROOT);
        final Schema schema = currentSchema();
        if ("TABLE".equals(domain)) {
            // Live names the entity as the ARGUMENT spelled it, bare — not the qualified form the
            // catalog's own miss would report — so the lookup is guarded rather than left to throw.
            if (!schema.hasTable(entityName)) {
                throw new RuntimeException(SqlCompilationError.doesNotExist("Table", entityName));
            }
            appendTableRows(schema, schema.getTable(entityName), rows);
        } else if ("VIEW".equals(domain)) {
            if (!schema.hasView(entityName)) {
                throw new RuntimeException(SqlCompilationError.doesNotExist("View", entityName));
            }
            appendViewRows(schema, schema.getView(entityName), rows);
        } else {
            throw new RuntimeException("Unknown domain: " + domain + ".");
        }
    }

    private void appendByPolicy(final String policyName, final List<Row> rows) {
        final String bare = policyName.toUpperCase(Locale.ROOT);
        final Schema schema = currentSchema();
        if (!policyExists(schema, bare)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Policy",
                schema.qualifiedName(bare)));
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
            if (bare.equals(String.valueOf(row.getValue(2)))) {
                matching.add(row);
            }
        }
        rows.clear();
        rows.addAll(matching);
    }

    private boolean policyExists(final Schema schema, final String name) {
        return schema.getMaskingPolicy(name) != null || schema.getRowAccessPolicy(name) != null
            || schema.getProjectionPolicy(name) != null || schema.getAggregationPolicy(name) != null
            || schema.getJoinPolicy(name) != null;
    }

    /** One row per attachment on a table, ordered by policy name as live orders them. */
    private void appendTableRows(final Schema schema, final Table table, final List<Row> rows) {
        final Map<String, Row> byPolicy = new TreeMap<>();
        for (final TableColumn column : table.getColumns()) {
            if (column.hasMaskingPolicy()) {
                byPolicy.put(bare(column.getMaskingPolicyName()), attachment(schema,
                    column.getMaskingPolicyName(), "MASKING_POLICY", table.getName(), "TABLE",
                    column.getName(), null));
            }
            if (column.hasProjectionPolicy()) {
                byPolicy.put(bare(column.getProjectionPolicyName()), attachment(schema,
                    column.getProjectionPolicyName(), "PROJECTION_POLICY", table.getName(), "TABLE",
                    column.getName(), null));
            }
        }
        if (table.hasRowAccessPolicy()) {
            byPolicy.put(bare(table.getRowAccessPolicyName()), attachment(schema,
                table.getRowAccessPolicyName(), "ROW_ACCESS_POLICY", table.getName(), "TABLE",
                null, table.getRowAccessPolicyColumns()));
        }
        if (table.hasAggregationPolicy()) {
            byPolicy.put(bare(table.getAggregationPolicyName()), attachment(schema,
                table.getAggregationPolicyName(), "AGGREGATION_POLICY", table.getName(), "TABLE",
                null, table.getAggregationEntityKey()));
        }
        if (table.hasJoinPolicy()) {
            byPolicy.put(bare(table.getJoinPolicyName()), attachment(schema,
                table.getJoinPolicyName(), "JOIN_POLICY", table.getName(), "TABLE", null, null));
        }
        rows.addAll(byPolicy.values());
    }

    private void appendViewRows(final Schema schema, final View view, final List<Row> rows) {
        if (view.hasRowAccessPolicy()) {
            rows.add(attachment(schema, view.getRowAccessPolicyName(), "ROW_ACCESS_POLICY",
                view.getName(), "VIEW", null, view.getRowAccessPolicyColumns()));
        }
    }

    private Row attachment(final Schema schema, final String policyName, final String kind,
                           final String entityName, final String domain, final String columnName,
                           final List<String> argumentColumns) {
        return new Row(Arrays.asList(
            schema.getDatabaseName(), schema.getName(), bare(policyName), kind,
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

    private String bare(final String qualifiedName) {
        final int dot = qualifiedName.lastIndexOf('.');
        return (dot < 0 ? qualifiedName : qualifiedName.substring(dot + 1)).toUpperCase(Locale.ROOT);
    }

    private Schema currentSchema() {
        return catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(catalog.getCurrentSchema());
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
