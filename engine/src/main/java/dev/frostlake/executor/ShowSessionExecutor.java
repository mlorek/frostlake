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

package dev.frostlake.executor;

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.QueryHistory;
import dev.frostlake.metastore.QueryHistoryTracker;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.UniqueConstraint;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.transaction.TransactionManager;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SHOW / DESCRIBE handlers for session and runtime state: parameters, sessions, variables, locks,
 * transactions, query history, accounts, and primary/unique keys. Extracted from
 * {@link ShowCommandExecutor}, which delegates here. The {@code SecurityManager} is read live from the
 * facade because it is installed after construction.
 */
final class ShowSessionExecutor {

    private final Catalog catalog;
    private final ShowCommandExecutor facade;
    private final TransactionManager transactionManager;
    private final QueryHistoryTracker queryHistoryTracker;
    private final Map<String, Object> sessionVariables;

    ShowSessionExecutor(final Catalog catalog, final ShowCommandExecutor facade,
                        final TransactionManager transactionManager,
                        final QueryHistoryTracker queryHistoryTracker,
                        final Map<String, Object> sessionVariables) {
        this.catalog = catalog;
        this.facade = facade;
        this.transactionManager = transactionManager;
        this.queryHistoryTracker = queryHistoryTracker;
        this.sessionVariables = sessionVariables;
    }

    public ResultSet showParameters(final String likePattern) {
        final SecurityManager securityManager = facade.getSecurityManager();
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("key", StringType.VARCHAR),
            new ResultSetColumn("value", StringType.VARCHAR),
            new ResultSetColumn("default", StringType.VARCHAR),
            new ResultSetColumn("level", StringType.VARCHAR),
            new ResultSetColumn("description", StringType.VARCHAR),
            new ResultSetColumn("type", StringType.VARCHAR)
        );
        // Built-in Snowflake session parameters with defaults
        Object[][] params = {
            {"TIMEZONE",                     "UTC",   "UTC",   "ACCOUNT", "Time zone",                    "TEXT"},
            {"TIMESTAMP_OUTPUT_FORMAT",      "YYYY-MM-DD HH24:MI:SS.FF3 TZHTZM", "YYYY-MM-DD HH24:MI:SS.FF3 TZHTZM", "ACCOUNT", "Timestamp output format", "TEXT"},
            {"DATE_OUTPUT_FORMAT",           "YYYY-MM-DD", "YYYY-MM-DD", "ACCOUNT", "Date output format", "TEXT"},
            {"TIME_OUTPUT_FORMAT",           "HH24:MI:SS", "HH24:MI:SS", "ACCOUNT", "Time output format", "TEXT"},
            {"TIMESTAMP_TYPE_MAPPING",       "TIMESTAMP_NTZ", "TIMESTAMP_NTZ", "ACCOUNT", "Default timestamp type mapping", "TEXT"},
            {"MULTI_STATEMENT_COUNT",        "1",  "1",  "SESSION", "Number of statements in a multi-statement request", "NUMBER"},
            {"QUERY_TAG",                    "",   "",   "SESSION", "Query tag for resource tracking", "TEXT"},
            {"ROWS_PER_RESULTSET",           "0",  "0",  "SESSION", "Max rows in result set (0 = unlimited)", "NUMBER"},
            {"LOCK_TIMEOUT",                 "43200", "43200", "ACCOUNT", "Lock wait timeout in seconds", "NUMBER"},
            {"STATEMENT_TIMEOUT_IN_SECONDS", "0",  "0",  "ACCOUNT", "Statement execution timeout (0 = disabled)", "NUMBER"},
            {"AUTOCOMMIT",                   "true", "true", "ACCOUNT", "Auto-commit mode", "BOOLEAN"},
        };
        // Also include current session parameters
        Map<String, Object> sessionParams = securityManager != null
            ? securityManager.getSessionContext().getAllSessionParameters()
            : new LinkedHashMap<>();

        List<Row> rows = new ArrayList<>();
        for (final Object[] p : params) {
            String key = (String) p[0];
            String sessionVal = sessionParams.containsKey(key) ? sessionParams.get(key).toString() : (String) p[1];
            if (likePattern == null || key.toUpperCase().contains(likePattern.toUpperCase().replace("%", ""))) {
                rows.add(new Row(Arrays.asList(key, sessionVal, p[2], p[3], p[4], p[5])));
            }
        }
        // Add any extra session-only parameters
        for (final Map.Entry<String, Object> e : sessionParams.entrySet()) {
            String key = e.getKey();
            boolean already = false;
            for (final Object[] p : params) { if (key.equalsIgnoreCase((String) p[0])) { already = true; break; } }
            if (!already && (likePattern == null || key.toUpperCase().contains(likePattern.toUpperCase().replace("%", "")))) {
                rows.add(new Row(Arrays.asList(key, e.getValue() != null ? e.getValue().toString() : null, null, "SESSION", null, "TEXT")));
            }
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet showSessions(final String likePattern) {
        final SecurityManager securityManager = facade.getSecurityManager();
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("session_id", StringType.VARCHAR),
            new ResultSetColumn("login_name", StringType.VARCHAR),
            new ResultSetColumn("user_name", StringType.VARCHAR),
            new ResultSetColumn("role_name", StringType.VARCHAR),
            new ResultSetColumn("warehouse_name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("client_application", StringType.VARCHAR),
            new ResultSetColumn("created_on", StringType.VARCHAR)
        );
        String user = securityManager != null ? securityManager.getSessionContext().getDisplayUser() : "SYSTEM";
        String role = securityManager != null ? securityManager.getSessionContext().getCurrentRole() : "SYSADMIN";
        if (likePattern != null && !user.toUpperCase().contains(likePattern.toUpperCase().replace("%", ""))) {
            return new ResultSet(columns, new ArrayList<>());
        }
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(Arrays.asList(
            UUID.randomUUID().toString(),
            user, user, role,
            catalog.getCurrentWarehouse(),
            catalog.getCurrentDatabase(),
            catalog.getCurrentSchema(),
            "FrostlakeSQLEngine/1.0",
            Instant.now().toString()
        )));
        return new ResultSet(columns, rows);
    }

    public ResultSet showOrganizationAccounts() {
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("organization_name", StringType.VARCHAR),
            new ResultSetColumn("account_name", StringType.VARCHAR),
            new ResultSetColumn("snowflake_region", StringType.VARCHAR),
            new ResultSetColumn("edition", StringType.VARCHAR),
            new ResultSetColumn("account_url", StringType.VARCHAR),
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("account_locator", StringType.VARCHAR),
            new ResultSetColumn("account_locator_url", StringType.VARCHAR),
            new ResultSetColumn("managed_accounts", NumericType.INTEGER),
            new ResultSetColumn("consumption_billing_entity_name", StringType.VARCHAR),
            new ResultSetColumn("marketplace_consumer_billing_entity_name", StringType.VARCHAR),
            new ResultSetColumn("marketplace_provider_billing_entity_name", StringType.VARCHAR),
            new ResultSetColumn("old_account_url", StringType.VARCHAR),
            new ResultSetColumn("is_org_admin", StringType.VARCHAR)
        );
        // Return a single simulated account row matching the current engine instance
        String orgName = "SIMORG";
        String accountName = "SIMACCOUNT";
        Row row = new Row(Arrays.asList(
            orgName, accountName, "AWS_US_EAST_1", "ENTERPRISE",
            "https://simaccount.snowflakecomputing.com", "2020-01-01 00:00:00.000",
            null, "SIMACCT", "https://simacct.snowflakecomputing.com",
            0L, orgName, null, null, null, "true"
        ));
        return new ResultSet(columns, List.of(row));
    }

    public ResultSet showAccounts() {
        // SHOW ACCOUNTS — account-manager-level view; columns match Snowflake docs
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("organization_name", StringType.VARCHAR),
            new ResultSetColumn("account_name", StringType.VARCHAR),
            new ResultSetColumn("region_group", StringType.VARCHAR),
            new ResultSetColumn("snowflake_region", StringType.VARCHAR),
            new ResultSetColumn("edition", StringType.VARCHAR),
            new ResultSetColumn("account_url", StringType.VARCHAR),
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("account_locator", StringType.VARCHAR),
            new ResultSetColumn("account_locator_url", StringType.VARCHAR),
            new ResultSetColumn("account_oldurl_saved_on", StringType.VARCHAR),
            new ResultSetColumn("old_account_url", StringType.VARCHAR),
            new ResultSetColumn("is_org_admin", StringType.VARCHAR),
            new ResultSetColumn("account_old_url_last_used", StringType.VARCHAR),
            new ResultSetColumn("org_default_region", StringType.VARCHAR),
            new ResultSetColumn("is_events_account", StringType.VARCHAR)
        );
        Row row = new Row(Arrays.asList(
            "SIMORG", "SIMACCOUNT", "PUBLIC", "AWS_US_EAST_1", "ENTERPRISE",
            "https://simaccount.snowflakecomputing.com", "2020-01-01 00:00:00.000",
            null, "SIMACCT", "https://simacct.snowflakecomputing.com",
            null, null, "true", null, "AWS_US_EAST_1", "false"
        ));
        return new ResultSet(columns, List.of(row));
    }

    public ResultSet showLocks() {
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("transaction", NumericType.BIGINT),
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("resource", StringType.VARCHAR),
            new ResultSetColumn("type", StringType.VARCHAR),
            new ResultSetColumn("status", StringType.VARCHAR)
        );
        List<Row> rows = new ArrayList<>();
        for (final TransactionManager.Transaction txn : transactionManager.getActiveTransactions()) {
            rows.add(new Row(Arrays.asList(
                txn.getId(),
                new Timestamp(txn.getStartTime()).toString(),
                "TABLE",
                "DML",
                "HOLDING"
            )));
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet showTransactions(final String likePattern) {
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("id", NumericType.BIGINT),
            new ResultSetColumn("user_name", StringType.VARCHAR),
            new ResultSetColumn("session_id", NumericType.BIGINT),
            new ResultSetColumn("status", StringType.VARCHAR),
            new ResultSetColumn("started_on", StringType.VARCHAR),
            new ResultSetColumn("statement_count", NumericType.INTEGER)
        );
        List<Row> rows = new ArrayList<>();
        for (final TransactionManager.Transaction txn : transactionManager.getActiveTransactions()) {
            String status = txn.getState().name();
            if (likePattern != null && !status.toUpperCase().contains(likePattern.toUpperCase())) continue;
            rows.add(new Row(Arrays.asList(
                txn.getId(),
                "CURRENT_USER",
                1L,
                status,
                new Timestamp(txn.getStartTime()).toString(),
                (long) txn.getLogCount()
            )));
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet showVariables() {
        final SecurityManager securityManager = facade.getSecurityManager();
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("value", StringType.VARCHAR)
        );
        List<Row> rows = new ArrayList<>();
        Map<String, Object> vars = securityManager != null
            ? securityManager.getSessionContext().getAllSessionParameters()
            : sessionVariables;
        for (final Map.Entry<String, Object> e : vars.entrySet()) {
            rows.add(new Row(Arrays.asList(e.getKey(), e.getValue() != null ? e.getValue().toString() : null)));
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet showQueryHistory(final Integer limit) {
        List<Row> rows = new ArrayList<>();
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("query_id", StringType.VARCHAR),
            new ResultSetColumn("query_text", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("query_type", StringType.VARCHAR),
            new ResultSetColumn("warehouse_name", StringType.VARCHAR),
            new ResultSetColumn("user_name", StringType.VARCHAR),
            new ResultSetColumn("role_name", StringType.VARCHAR),
            new ResultSetColumn("start_time", StringType.VARCHAR),
            new ResultSetColumn("end_time", StringType.VARCHAR),
            new ResultSetColumn("execution_status", StringType.VARCHAR),
            new ResultSetColumn("execution_time_ms", NumericType.INTEGER),
            new ResultSetColumn("rows_produced", NumericType.INTEGER),
            new ResultSetColumn("rows_inserted", NumericType.INTEGER),
            new ResultSetColumn("rows_updated", NumericType.INTEGER),
            new ResultSetColumn("rows_deleted", NumericType.INTEGER),
            new ResultSetColumn("error_message", StringType.VARCHAR)
        );

        List<QueryHistory> history;
        if (limit != null && limit > 0) {
            history = queryHistoryTracker.getHistory(limit);
        } else {
            history = queryHistoryTracker.getAllHistory();
            Collections.reverse(history); // Most recent first
        }

        for (final QueryHistory query : history) {
            rows.add(new Row(Arrays.asList(
                query.getQueryId(),
                query.getQueryText(),
                query.getDatabase(),
                query.getSchema(),
                query.getQueryType(),
                query.getWarehouse(),
                query.getUser(),
                query.getRole(),
                query.getStartTimeFormatted(),
                query.getEndTimeFormatted(),
                query.getStatus(),
                (int) query.getExecutionTimeMs(),
                (int) query.getRowsProduced(),
                (int) query.getRowsInserted(),
                (int) query.getRowsUpdated(),
                (int) query.getRowsDeleted(),
                query.getErrorMessage()
            )));
        }

        return new ResultSet(columns, rows);
    }

    public ResultSet showPrimaryKeys(final String tableName) {
        return showKeysScoped(true, "TABLE", tableName);
    }

    public ResultSet showUniqueKeys(final String tableName) {
        return showKeysScoped(false, "TABLE", tableName);
    }

    /**
     * SHOW PRIMARY/UNIQUE KEYS with a scope: one row per key column of every table in the scope —
     * TABLE name (or a bare qualified name), a SCHEMA, a DATABASE, or the whole ACCOUNT; a null
     * scope name means the current one (a nameless TABLE scope lists the current schema's tables).
     *
     * <p>The column shape is Snowflake's (live-verified): {@code created_on, database_name, schema_name,
     * table_name, column_name, key_sequence, constraint_name, rely, comment}. Unlike
     * {@code INFORMATION_SCHEMA.TABLE_CONSTRAINTS}, which reports one row per CONSTRAINT, SHOW … KEYS keeps
     * one row per key COLUMN (live-verified): a composite PRIMARY KEY over (a, b) is two rows numbered
     * {@code key_sequence} 1 and 2 that SHARE one {@code constraint_name}. A multi-column UNIQUE behaves the
     * same way; a column-level UNIQUE is its own single-column constraint and always numbers 1.
     */
    public ResultSet showKeysScoped(final boolean primary, final String scopeKind, final String scopeName) {
        final List<ResultSetColumn> cols = Arrays.asList(
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("table_name", StringType.VARCHAR),
            new ResultSetColumn("column_name", StringType.VARCHAR),
            new ResultSetColumn("key_sequence", NumericType.INTEGER),
            new ResultSetColumn("constraint_name", StringType.VARCHAR),
            new ResultSetColumn("rely", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR));
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : schemasInScope(scopeKind, scopeName)) {
            final String dbName = databaseNameOf(schema);
            for (final Table table : tablesInScope(schema, scopeKind, scopeName)) {
                if (primary) {
                    int seq = 1;
                    for (final TableColumn col : table.getColumns()) {
                        if (col.isPrimaryKey()) {
                            rows.add(keyRow(dbName, schema, table, col.getName(), seq++,
                                table.primaryKeyConstraintName(), col.getRely()));
                        }
                    }
                } else {
                    for (final UniqueConstraint unique : table.getUniqueConstraints()) {
                        int seq = 1;
                        for (final String columnName : unique.getColumnNames()) {
                            final Boolean rely = table.hasColumn(columnName)
                                ? table.getColumn(columnName).getRely() : null;
                            rows.add(keyRow(dbName, schema, table, columnName, seq++,
                                unique.getConstraintName(), rely));
                        }
                    }
                }
            }
        }
        return new ResultSet(cols, rows);
    }

    /** One SHOW … KEYS row: a key column, its position within its constraint, and the constraint's name. */
    private Row keyRow(final String dbName, final Schema schema, final Table table, final String columnName,
                       final int keySequence, final String constraintName, final Boolean rely) {
        return new Row(Arrays.asList(
            table.getCreatedTime().toString(),
            dbName,
            schema.getName(),
            table.getName(),
            columnName,
            keySequence,
            constraintName,
            relyText(rely),
            null));
    }

    /** SHOW … KEYS reports RELY as a lower-case boolean; a constraint without RELY reports "false". */
    private static String relyText(final Boolean rely) {
        return rely != null && rely ? "true" : "false";
    }

    /** SHOW IMPORTED KEYS: one row per foreign-key column of every table in the scope (see showKeysScoped). */
    public ResultSet showImportedKeys(final String scopeKind, final String scopeName) {
        final List<ResultSetColumn> cols = Arrays.asList(
            new ResultSetColumn("pk_schema_name", StringType.VARCHAR),
            new ResultSetColumn("pk_table_name", StringType.VARCHAR),
            new ResultSetColumn("pk_column_name", StringType.VARCHAR),
            new ResultSetColumn("fk_schema_name", StringType.VARCHAR),
            new ResultSetColumn("fk_table_name", StringType.VARCHAR),
            new ResultSetColumn("fk_column_name", StringType.VARCHAR),
            new ResultSetColumn("key_sequence", NumericType.INTEGER),
            new ResultSetColumn("update_rule", StringType.VARCHAR),
            new ResultSetColumn("delete_rule", StringType.VARCHAR),
            new ResultSetColumn("fk_name", StringType.VARCHAR),
            new ResultSetColumn("pk_name", StringType.VARCHAR));
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : schemasInScope(scopeKind, scopeName)) {
            for (final Table table : tablesInScope(schema, scopeKind, scopeName)) {
                for (final ForeignKeyConstraint fk : table.getForeignKeys()) {
                    final List<String> fkColumns = fk.getColumnNames();
                    final List<String> pkColumns = fk.getReferencedColumns();
                    for (int i = 0; i < fkColumns.size(); i++) {
                        rows.add(new Row(Arrays.asList(
                            schema.getName(),
                            fk.getReferencedTable(),
                            i < pkColumns.size() ? pkColumns.get(i) : null,
                            schema.getName(),
                            table.getName(),
                            fkColumns.get(i),
                            i + 1,
                            fk.getOnUpdate() != null ? fk.getOnUpdate() : "NO ACTION",
                            fk.getOnDelete() != null ? fk.getOnDelete() : "NO ACTION",
                            fk.getConstraintName(),
                            referencedPrimaryKeyName(schema, fk.getReferencedTable()))));
                    }
                }
            }
        }
        return new ResultSet(cols, rows);
    }

    /** The database a scope schema belongs to — Schema itself carries no back-reference to its database. */
    private String databaseNameOf(final Schema schema) {
        for (final Database db : catalog.getAllDatabases()) {
            for (final Schema candidate : db.getAllSchemas()) {
                if (candidate == schema) {
                    return db.getName();
                }
            }
        }
        return catalog.getCurrentDatabase();
    }

    /** The name of the PRIMARY KEY constraint a foreign key points at, or null when it cannot be resolved. */
    private String referencedPrimaryKeyName(final Schema schema, final String referencedTable) {
        final String bare = QualifiedName.parse(referencedTable).last();
        Table target = schema.hasTable(bare) ? schema.getTable(bare) : null;
        if (target == null) {
            try {
                target = catalog.resolveTable(referencedTable);
            } catch (final RuntimeException e) {
                return null;   // the referenced table is gone or lives outside this catalog
            }
        }
        return target != null ? target.primaryKeyConstraintName() : null;
    }

    /** The schemas a keys listing spans: current schema, a named schema, a database's schemas, or all. */
    private List<Schema> schemasInScope(final String scopeKind, final String scopeName) {
        final List<Schema> schemas = new ArrayList<>();
        if ("ACCOUNT".equals(scopeKind)) {
            for (final Database db : catalog.getAllDatabases()) {
                schemas.addAll(db.getAllSchemas());
            }
        } else if ("DATABASE".equals(scopeKind)) {
            final Database db = scopeName != null
                ? catalog.getDatabase(scopeName)
                : catalog.getDatabase(catalog.getCurrentDatabase());
            if (db != null) {
                schemas.addAll(db.getAllSchemas());
            }
        } else if ("SCHEMA".equals(scopeKind) && scopeName != null) {
            schemas.add(catalog.resolveSchema(scopeName));
        } else {
            schemas.add(resolveDescribeSchema());
        }
        return schemas;
    }

    /** The tables of one scope schema — the single named table for a TABLE scope, else all of them. */
    private List<Table> tablesInScope(final Schema schema, final String scopeKind, final String scopeName) {
        if ("TABLE".equals(scopeKind) && scopeName != null) {
            final List<Table> one = new ArrayList<>();
            one.add(catalog.resolveTable(scopeName));
            return one;
        }
        return schema.getTables();
    }

    /** Current database.schema for describe lookups. */
    private Schema resolveDescribeSchema() {
        return ShowResultHelpers.resolveDescribeSchema(catalog);
    }
}
