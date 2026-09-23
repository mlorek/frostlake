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

import dev.frostlake.config.AccountIdentity;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.QueryHistory;
import dev.frostlake.metastore.QueryHistoryTracker;
import dev.frostlake.metastore.model.Account;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.ManagedAccount;
import dev.frostlake.metastore.model.ObjectParameters;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.UniqueConstraint;
import dev.frostlake.metastore.model.Warehouse;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.transaction.TableLock;
import dev.frostlake.transaction.Transaction;
import dev.frostlake.transaction.TransactionManager;
import dev.frostlake.transaction.TransactionState;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.values.VariantJsonFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * SHOW / DESCRIBE handlers for session and runtime state: parameters, sessions, variables, locks,
 * transactions, query history, accounts, and primary/unique keys. Extracted from
 * {@link ShowCommandExecutor}, which delegates here. The {@code SecurityManager} is read live from the
 * facade because it is installed after construction.
 */
final class ShowSessionExecutor {

    /** The user parameter naming the network policy attached to the user. */
    private static final String NETWORK_POLICY = "NETWORK_POLICY";

    /**
     * When this engine's account was created. Nothing models an account's real birthday, so it is a
     * fixed moment rather than the engine's start time — a SHOW that answered a different created_on
     * on every run would be worse than one that answers a stable placeholder.
     */
    private static final LocalDateTime ACCOUNT_CREATED_ON = LocalDateTime.of(2020, 1, 1, 0, 0, 0);

    private final AccountIdentity identity;
    private final Catalog catalog;
    private final ShowCommandExecutor facade;
    private final TransactionManager transactionManager;
    private final QueryHistoryTracker queryHistoryTracker;
    private final Map<String, Object> sessionVariables;
    /** When this engine's listings began — the created_on / updated_on SHOW VARIABLES prints. */
    private final long startedOnMillis = System.currentTimeMillis();

    ShowSessionExecutor(final Catalog catalog, final ShowCommandExecutor facade,
                        final TransactionManager transactionManager,
                        final QueryHistoryTracker queryHistoryTracker,
                        final Map<String, Object> sessionVariables,
                        final AccountIdentity identity) {
        this.identity = identity;
        this.catalog = catalog;
        this.facade = facade;
        this.transactionManager = transactionManager;
        this.queryHistoryTracker = queryHistoryTracker;
        this.sessionVariables = sessionVariables;
    }

    /** The six columns every SHOW PARAMETERS form returns. */
    private List<ResultSetColumn> parameterColumns() {
        return Arrays.asList(
            new ResultSetColumn("key", StringType.VARCHAR),
            new ResultSetColumn("value", StringType.VARCHAR),
            new ResultSetColumn("default", StringType.VARCHAR),
            new ResultSetColumn("level", StringType.VARCHAR),
            new ResultSetColumn("description", StringType.VARCHAR),
            new ResultSetColumn("type", StringType.VARCHAR)
        );
    }

    public ResultSet showParameters(final String likePattern) {
        final SecurityManager securityManager = facade.getSecurityManager();
        final List<ResultSetColumn> columns = parameterColumns();
        // Every parameter a real account lists, with its default, description and type word —
        // transcribed rather than invented (see SessionParameterCatalog). Frostlake used to carry a
        // dozen hand-written rows, so SHOW PARAMETERS LIKE 'WEEK_START' returned NOTHING on a
        // parameter live describes perfectly well.
        final Map<String, Object> sessionParams = securityManager != null
            ? securityManager.getSessionContext().getAllSessionParameters()
            : new LinkedHashMap<>();

        final List<Row> rows = new ArrayList<>();
        final Set<String> listed = new HashSet<>();
        for (final SessionParameterRow parameter : SessionParameterCatalog.rows()) {
            final String key = parameter.getName();
            listed.add(key.toUpperCase());
            if (!matchesLike(key, likePattern)) {
                continue;
            }
            final boolean setBySession = sessionParams.containsKey(key);
            // JSON_INDENT is the one row whose unset value the ENGINE decides rather than the table:
            // it is a real setting here, and SHOW must report what the renderer is actually using.
            final String inForce;
            if (setBySession) {
                inForce = String.valueOf(sessionParams.get(key));
            } else if ("JSON_INDENT".equals(key)) {
                inForce = String.valueOf(VariantJsonFormat.indent());
            } else {
                inForce = parameter.getDefaultValue();
            }
            // The LEVEL is where the value in force came from: a parameter this session has SET reads
            // SESSION, and one still on its default reads EMPTY — live shows an unset parameter with a
            // blank level, never ACCOUNT.
            rows.add(new Row(Arrays.asList(key, inForce, parameter.getDefaultValue(),
                setBySession ? "SESSION" : "", parameter.getDescription(), parameter.getType())));
        }
        // A session parameter the catalog has never heard of still appears once it is set — with no
        // default and no description, because there is nothing to report for either.
        for (final Map.Entry<String, Object> e : sessionParams.entrySet()) {
            final String key = e.getKey();
            if (!listed.contains(key.toUpperCase()) && matchesLike(key, likePattern)) {
                rows.add(new Row(Arrays.asList(key, e.getValue() != null ? e.getValue().toString() : null,
                    null, "SESSION", null, "TEXT")));
            }
        }
        return new ResultSet(columns, rows);
    }

    /**
     * SHOW PARAMETERS IN USER: the parameters a user carries, each at its default, and the network policy attached
     * to the user, whose level reads USER while one is attached and is empty otherwise. The pattern is a LIKE
     * pattern, matched case-insensitively.
     *
     * @param userName    the user, canonical
     * @param likePattern the LIKE pattern, or null for every row
     */
    public ResultSet showParametersInUser(final String userName, final String likePattern) {
        final String user = catalog.getUser(userName).getName();
        final Map<String, SessionParameterRow> known = new HashMap<>();
        for (final SessionParameterRow parameter : SessionParameterCatalog.rows()) {
            known.put(parameter.getName(), parameter);
        }
        for (final SessionParameterRow parameter : SessionParameterCatalog.userOnlyRows()) {
            known.put(parameter.getName(), parameter);
        }
        final List<Row> rows = new ArrayList<>();
        for (final String key : SessionParameterCatalog.userScopeNames()) {
            final Row row;
            final SessionParameterRow parameter = known.get(key);
            if (parameter == null) {
                continue;
            }
            if (NETWORK_POLICY.equals(key)) {
                final String policy = catalog.getSecurityObjects().attachment("NETWORK_POLICY|USER|" + user);
                row = new Row(Arrays.asList(key, policy == null ? "" : policy, parameter.getDefaultValue(),
                    policy == null ? "" : "USER", parameter.getDescription(), parameter.getType()));
            } else {
                row = new Row(Arrays.asList(key, parameter.getDefaultValue(), parameter.getDefaultValue(), "",
                    parameter.getDescription(), parameter.getType()));
            }
            addIfLike(rows, row, likePattern);
        }
        return new ResultSet(parameterColumns(), rows);
    }

    private static void addIfLike(final List<Row> rows, final Row row, final String likePattern) {
        if (likePattern == null || ShowResultHelpers.matchesLike(String.valueOf(row.getValue(0)), likePattern)) {
            rows.add(row);
        }
    }

    /**
     * SHOW PARAMETERS … LIKE matching: a LIKE pattern, case-insensitive, so {@code LIKE 'TIMESTAMP'} names only
     * a parameter called that and {@code LIKE 'week%'} the ones starting with it.
     */
    private boolean matchesLike(final String name, final String likePattern) {
        return likePattern == null || ShowResultHelpers.matchesLike(name, likePattern);
    }

    public ResultSet showParametersInTask(final String taskName, final String likePattern) {
        final String dbName = catalog.getCurrentDatabase();
        final String scName = catalog.getCurrentSchema();
        if (dbName == null || scName == null) {
            throw new RuntimeException("No database or schema selected");
        }
        final Task task = catalog.getDatabase(dbName).getSchema(scName).getTask(taskName);
        if (task == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Task", taskName));
        }
        final Object[][] params = {
            {"SERVERLESS_TASK_MAX_STATEMENT_SIZE",
                task.getServerlessTaskMaxStatementSize() != null
                    ? task.getServerlessTaskMaxStatementSize() : "X2Large",
                "X2Large", "STRING",
                "The maximum warehouse size to use for a serverless Task"},
            {"SERVERLESS_TASK_MIN_STATEMENT_SIZE",
                task.getServerlessTaskMinStatementSize() != null
                    ? task.getServerlessTaskMinStatementSize() : "XSMALL",
                "XSMALL", "STRING",
                "The minimum warehouse size to use for a serverless Task"},
            {"SUSPEND_TASK_AFTER_NUM_FAILURES",
                String.valueOf(task.getSuspendTaskAfterNumFailures()), "10", "NUMBER",
                "How many times a task must fail in a row before it is automatically suspended. "
                    + "0 disables auto-suspending."},
            {"TASK_AUTO_RETRY_ATTEMPTS",
                String.valueOf(task.getTaskAutoRetryAttempts()), "0", "NUMBER",
                "Maximum Automatic Retries Allowed For A User Task"},
            {"USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE",
                task.getUserTaskManagedInitialWarehouseSize() != null
                    ? task.getUserTaskManagedInitialWarehouseSize() : "Medium",
                "Medium", "STRING",
                "The initial size of warehouse to use for managed warehouses in the absence of history"},
            {"USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS",
                String.valueOf(task.getUserTaskMinimumTriggerIntervalInSeconds()), "30", "NUMBER",
                "Minimum amount of time between Triggered Task executions in seconds"},
            {"USER_TASK_TIMEOUT_MS",
                String.valueOf(task.getUserTaskTimeoutMs()), "3600000", "NUMBER",
                "User task execution timeout in milliseconds"}
        };
        final Map<String, Object[]> taskOwn = new HashMap<>();
        for (final Object[] param : params) {
            taskOwn.put((String) param[0], param);
        }
        // Every parameter a task carries, in live's order: the task's own above, and the session parameters a
        // task may set for its runs, each at its default until the task sets it.
        final List<Row> rows = new ArrayList<>();
        for (final String key : SessionParameterCatalog.taskScopeNames()) {
            if (!matchesParameterFilter(key, likePattern)) {
                continue;
            }
            final Object[] param = taskOwn.get(key);
            if (param != null) {
                rows.add(new Row(Arrays.asList(key, param[1], param[2],
                    task.isParameterSetOnTask(key) ? "TASK" : "", param[4], param[3])));
                continue;
            }
            final SessionParameterRow row = SessionParameterCatalog.find(key);
            if (row == null) {
                continue;
            }
            final String set = task.getSessionParameters().get(key);
            rows.add(new Row(Arrays.asList(key, set != null ? set : row.getDefaultValue(), row.getDefaultValue(),
                set != null ? "TASK" : "", row.getDescription(), row.getType())));
        }
        return new ResultSet(parameterColumns(), rows);
    }

    /**
     * SHOW PARAMETERS IN WAREHOUSE: the warehouse-scoped parameters. As with tasks, the level is
     * WAREHOUSE where the warehouse set the parameter itself and empty where it inherits.
     */
    public ResultSet showParametersInWarehouse(final String warehouseName, final String likePattern) {
        final Warehouse warehouse = catalog.getWarehouse(warehouseName);
        if (warehouse == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Warehouse", warehouseName));
        }
        final Object[][] params = {
            {"MAX_CONCURRENCY_LEVEL", String.valueOf(warehouse.getMaxConcurrencyLevel()), "8", "NUMBER",
                "Maximum number of SQL statements a warehouse cluster can execute concurrently "
                    + "before queuing them. Small SQL statements count as a fraction of 1."},
            {"STATEMENT_QUEUED_TIMEOUT_IN_SECONDS",
                String.valueOf(warehouse.getStatementQueuedTimeoutSeconds()), "0", "NUMBER",
                "Timeout in seconds for queued statements: statements will automatically be "
                    + "canceled if they are queued on a warehouse for longer than this amount of "
                    + "time; disabled if set to zero."},
            {"STATEMENT_TIMEOUT_IN_SECONDS",
                String.valueOf(warehouse.getStatementTimeoutSeconds()), "172800", "NUMBER",
                "Timeout in seconds for statements: statements are automatically canceled if they "
                    + "run for longer; if set to zero, max value (604800) is enforced."}
        };
        final List<Row> rows = new ArrayList<>();
        for (final Object[] param : params) {
            final String key = (String) param[0];
            if (!matchesParameterFilter(key, likePattern)) {
                continue;
            }
            rows.add(new Row(Arrays.asList(key, param[1], param[2],
                warehouse.isParameterSetOnWarehouse(key) ? "WAREHOUSE" : "", param[4], param[3])));
        }
        return new ResultSet(parameterColumns(), rows);
    }

    /**
     * SHOW PARAMETERS IN DATABASE: the parameters a database can set on itself, each with the value in force —
     * the database's own (level {@code DATABASE}) or the account default (level empty).
     */
    public ResultSet showParametersInDatabase(final String databaseName, final String likePattern) {
        final Database database = catalog.databaseExact(databaseName);
        final List<Row> rows = new ArrayList<>();
        for (final SessionParameterRow parameter : ContainerParameterCatalog.databaseRows()) {
            final String key = parameter.getName();
            if (!matchesParameterFilter(key, likePattern)) {
                continue;
            }
            final String own = containerValue(key, database.getDataRetentionTimeInDays(), database.getParameters());
            rows.add(new Row(Arrays.asList(key, own != null ? own : parameter.getDefaultValue(),
                parameter.getDefaultValue(), own != null ? "DATABASE" : "", parameter.getDescription(),
                parameter.getType())));
        }
        return new ResultSet(parameterColumns(), rows);
    }

    /**
     * SHOW PARAMETERS IN SCHEMA: the parameters a schema can set on itself, each with the value in force — the
     * schema's own (level {@code SCHEMA}), its database's (level {@code DATABASE}) or the account default.
     */
    public ResultSet showParametersInSchema(final String databaseName, final String schemaName,
                                            final String likePattern) {
        final Database database = catalog.databaseExact(databaseName);
        final Schema schema = database.schemaExact(schemaName);
        final List<Row> rows = new ArrayList<>();
        for (final SessionParameterRow parameter : ContainerParameterCatalog.schemaRows()) {
            final String key = parameter.getName();
            if (!matchesParameterFilter(key, likePattern)) {
                continue;
            }
            String value = containerValue(key, schema.getDataRetentionTimeInDays(), schema.getParameters());
            String level = "SCHEMA";
            if (value == null) {
                value = containerValue(key, database.getDataRetentionTimeInDays(), database.getParameters());
                level = "DATABASE";
            }
            if (value == null) {
                value = parameter.getDefaultValue();
                level = "";
            }
            rows.add(new Row(Arrays.asList(key, value, parameter.getDefaultValue(), level,
                parameter.getDescription(), parameter.getType())));
        }
        return new ResultSet(parameterColumns(), rows);
    }

    /** A container's own setting of a parameter, or null when it inherits it; the retention is kept apart. */
    private static String containerValue(final String key, final Integer retention,
                                         final ObjectParameters parameters) {
        if ("DATA_RETENTION_TIME_IN_DAYS".equals(key)) {
            return retention == null ? null : String.valueOf(retention);
        }
        return parameters.get(key);
    }

    /** Whether a parameter name survives a SHOW PARAMETERS … LIKE filter. */
    private boolean matchesParameterFilter(final String key, final String likePattern) {
        return likePattern == null
            || key.toUpperCase().contains(likePattern.toUpperCase().replace("%", ""));
    }

    /**
     * The 25 columns SHOW ACCOUNTS answers with, measured — SHOW ORGANIZATION ACCOUNTS answers with
     * exactly the same shape, which is why both listings share this. Both end with {@code contract_number}.
     *
     * <p>There is NO {@code region_group} column here, and no {@code org_default_region}: both were
     * Frostlake's own invention and asking a real account for either is an invalid identifier. Four
     * more timestamps ARE here that Frostlake did not have — the old-URL pairs — and the one it did
     * have was spelled {@code account_oldurl_saved_on} against live's {@code account_old_url_saved_on}.
     */
    private List<ResultSetColumn> accountColumns() {
        return Arrays.asList(
            new ResultSetColumn("organization_name", StringType.VARCHAR),
            new ResultSetColumn("account_name", StringType.VARCHAR),
            new ResultSetColumn("snowflake_region", StringType.VARCHAR),
            new ResultSetColumn("edition", StringType.VARCHAR),
            new ResultSetColumn("account_url", StringType.VARCHAR),
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("account_locator", StringType.VARCHAR),
            new ResultSetColumn("account_locator_url", StringType.VARCHAR),
            new ResultSetColumn("managed_accounts", NumericType.NUMBER),
            new ResultSetColumn("consumption_billing_entity_name", StringType.VARCHAR),
            new ResultSetColumn("marketplace_consumer_billing_entity_name", StringType.VARCHAR),
            new ResultSetColumn("marketplace_provider_billing_entity_name", StringType.VARCHAR),
            new ResultSetColumn("old_account_url", StringType.VARCHAR),
            new ResultSetColumn("is_org_admin", StringType.VARCHAR),
            new ResultSetColumn("account_old_url_saved_on", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("account_old_url_last_used", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("organization_old_url", StringType.VARCHAR),
            new ResultSetColumn("organization_old_url_saved_on", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("organization_old_url_last_used", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("is_events_account", StringType.VARCHAR),
            new ResultSetColumn("is_organization_account", StringType.VARCHAR),
            new ResultSetColumn("tenant_type", StringType.VARCHAR),
            new ResultSetColumn("domain_names", StringType.VARCHAR),
            new ResultSetColumn("contract_number", StringType.VARCHAR)
        );
    }

    /**
     * The one account this engine is, spelled from {@link AccountIdentity} rather than hardcoded — the
     * organization, name, locator and region all come from configuration and agree with what
     * CURRENT_ORGANIZATION_NAME / CURRENT_ACCOUNT_NAME / CURRENT_ACCOUNT / CURRENT_REGION answer.
     *
     * <p>An account with no old URL reports the EMPTY STRING for {@code old_account_url} and NULL for
     * the four old-URL timestamps beside it — measured, and the same "absent text is empty, absent
     * moment is null" split the rest of the SHOW family uses.
     */
    private Row accountRow() {
        return new Row(Arrays.asList(
            identity.getOrganization(),
            identity.getAccountName(),
            identity.getRegion(),
            "ENTERPRISE",
            identity.getAccountUrl(),
            ACCOUNT_CREATED_ON,
            ShowResultHelpers.text(null),
            identity.getAccountLocator(),
            identity.getAccountLocatorUrl(),
            0L,
            identity.getOrganization() + "_DefaultBE",
            null, null,
            ShowResultHelpers.text(null),
            "true",
            null, null,
            ShowResultHelpers.text(null),
            null, null,
            "false",
            "false",
            "INTERNAL",
            null,
            null
        ));
    }

    public ResultSet showOrganizationAccounts() {
        return new ResultSet(accountColumns(), List.of(accountRow()));
    }

    public ResultSet showAccounts() {
        return new ResultSet(accountColumns(), List.of(accountRow()));
    }

    /**
     * SHOW ACCOUNTS: this account, then every account CREATE ACCOUNT recorded, in the columns of the one row.
     * {@code HISTORY} lists the dropped accounts too, and adds their {@code dropped_on},
     * {@code scheduled_deletion_time} and {@code restored_on} after {@code is_org_admin}.
     *
     * @param history whether HISTORY was written
     * @param created the accounts CREATE ACCOUNT recorded
     * @param managed how many managed accounts this account has created
     */
    public ResultSet showAccounts(final boolean history, final List<Account> created, final int managed) {
        final List<ResultSetColumn> columns = new ArrayList<>(accountColumns());
        final int historyAt = 15;
        // HISTORY also reports where an account moved to and when its organization URL expires, after the old
        // organization URL's columns; both listings end with the contract number.
        final int movedAt = 23;
        if (history) {
            columns.add(historyAt, new ResultSetColumn("dropped_on", DateTimeType.TIMESTAMP_LTZ));
            columns.add(historyAt + 1, new ResultSetColumn("scheduled_deletion_time", DateTimeType.TIMESTAMP_LTZ));
            columns.add(historyAt + 2, new ResultSetColumn("restored_on", DateTimeType.TIMESTAMP_LTZ));
            columns.add(movedAt, new ResultSetColumn("moved_to_organization", StringType.VARCHAR));
            columns.add(movedAt + 1, new ResultSetColumn("moved_on", DateTimeType.TIMESTAMP_LTZ));
            columns.add(movedAt + 2, new ResultSetColumn("organization_URL_expiration_on", DateTimeType.TIMESTAMP_LTZ));
        }
        final List<Row> rows = new ArrayList<>();
        final List<Object> own = new ArrayList<>(accountRow().getValues());
        own.set(9, (long) managed);
        if (history) {
            own.add(historyAt, null);
            own.add(historyAt + 1, null);
            own.add(historyAt + 2, null);
            own.add(movedAt, null);
            own.add(movedAt + 1, null);
            own.add(movedAt + 2, null);
        }
        rows.add(new Row(own));
        final String organization = identity.getOrganization();
        for (final Account account : created) {
            if (account.isDropped() && !history) {
                continue;
            }
            final String locator = account.getLocator();
            final List<Object> cells = new ArrayList<>(Arrays.<Object>asList(
                organization,
                account.getName(),
                account.getRegion() != null ? account.getRegion() : identity.getRegion(),
                account.getEdition(),
                "https://" + organization.toLowerCase(Locale.ROOT) + "-"
                    + account.getName().toLowerCase(Locale.ROOT).replace('_', '-') + ".snowflakecomputing.com",
                ShowResultHelpers.createdOn(account.getCreatedOn()),
                ShowResultHelpers.text(account.getComment()),
                locator,
                "https://" + locator.toLowerCase(Locale.ROOT) + ".snowflakecomputing.com",
                0L,
                organization + "_DefaultBE",
                null, null,
                ShowResultHelpers.text(null),
                "false",
                null, null,
                ShowResultHelpers.text(null),
                null, null,
                "false",
                "false",
                "INTERNAL",
                null));
            if (history) {
                cells.add(historyAt, account.getDroppedOn() == null ? null
                    : ShowResultHelpers.createdOn(account.getDroppedOn()));
                cells.add(historyAt + 1, account.getScheduledDeletionTime() == null ? null
                    : ShowResultHelpers.createdOn(account.getScheduledDeletionTime()));
                cells.add(historyAt + 2, account.getRestoredOn() == null ? null
                    : ShowResultHelpers.createdOn(account.getRestoredOn()));
                cells.add(movedAt, null);
                cells.add(movedAt + 1, null);
                cells.add(movedAt + 2, null);
            }
            cells.add(null);
            rows.add(new Row(cells));
        }
        return new ResultSet(columns, rows);
    }

    /**
     * SHOW MANAGED ACCOUNTS: the reader accounts this account created, in this account's cloud and region.
     *
     * @param accounts the managed accounts
     */
    public ResultSet showManagedAccounts(final List<ManagedAccount> accounts) {
        final String region = identity.getRegion();
        final int split = region == null ? -1 : region.indexOf('_');
        final String cloud = split < 0 ? region : region.substring(0, split);
        final String location = split < 0 ? region
            : region.substring(split + 1).toLowerCase(Locale.ROOT).replace('_', '-');
        return new AccessControlListings(catalog).managedAccounts(accounts, cloud, location);
    }

    /**
     * SHOW LOCKS, in live's shape: one row per (transaction, table) a partition-rewriting DML
     * statement touched — UPDATE / DELETE / MERGE / TRUNCATE, while an append-only INSERT holds no
     * lock — with {@code type=PARTITIONS} and {@code status=HOLDING}, the resource as the table's
     * fully qualified name, and the lock's own {@code acquired_on} / {@code query_id} beside the
     * owning transaction's id and start. {@code IN ACCOUNT} prepends a {@code session} column; the
     * rows are otherwise identical.
     */
    public ResultSet showLocks(final boolean inAccount) {
        final List<ResultSetColumn> columns = new ArrayList<>();
        if (inAccount) {
            columns.add(new ResultSetColumn("session", NumericType.BIGINT));
        }
        columns.add(new ResultSetColumn("resource", StringType.VARCHAR));
        columns.add(new ResultSetColumn("type", StringType.VARCHAR));
        columns.add(new ResultSetColumn("transaction", NumericType.BIGINT));
        columns.add(new ResultSetColumn("transaction_started_on", ShowResultHelpers.CREATED_ON));
        columns.add(new ResultSetColumn("status", StringType.VARCHAR));
        columns.add(new ResultSetColumn("acquired_on", ShowResultHelpers.CREATED_ON));
        columns.add(new ResultSetColumn("query_id", StringType.VARCHAR));
        final List<Row> rows = new ArrayList<>();
        for (final Transaction txn : transactionManager.getActiveTransactions()) {
            if (txn.getState() != TransactionState.ACTIVE) {
                continue;
            }
            for (final Map.Entry<String, TableLock> entry : txn.getAllTableLocks().entrySet()) {
                final List<Object> cells = new ArrayList<>();
                if (inAccount) {
                    cells.add(sessionNumberOf(txn));
                }
                cells.add(entry.getKey());
                cells.add("PARTITIONS");
                cells.add(transactionDisplayId(txn));
                cells.add(ShowResultHelpers.createdOn(Instant.ofEpochMilli(txn.getStartTime())));
                cells.add("HOLDING");
                cells.add(ShowResultHelpers.createdOn(Instant.ofEpochMilli(entry.getValue().getAcquiredOn())));
                cells.add(entry.getValue().getQueryId());
                rows.add(new Row(cells));
            }
        }
        return new ResultSet(columns, rows);
    }

    /**
     * SHOW TRANSACTIONS, in live's shape: only OPEN transactions are listed (an idle session shows
     * nothing), {@code state} is {@code running}, {@code scope} is 0, {@code name} is the
     * transaction's system-generated UUID, and {@code id} carries the start instant at nanosecond
     * scale — the sub-millisecond digits distinguish transactions that began in the same
     * millisecond.
     */
    public ResultSet showTransactions(final String likePattern) {
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("id", NumericType.BIGINT),
            new ResultSetColumn("user", StringType.VARCHAR),
            new ResultSetColumn("session", NumericType.BIGINT),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("started_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("state", StringType.VARCHAR),
            new ResultSetColumn("scope", NumericType.BIGINT)
        );
        final List<Row> rows = new ArrayList<>();
        for (final Transaction txn : transactionManager.getActiveTransactions()) {
            if (txn.getState() != TransactionState.ACTIVE) {
                continue;
            }
            if (likePattern != null && !ShowResultHelpers.matchesLike(txn.getName(), likePattern)) {
                continue;
            }
            rows.add(new Row(Arrays.asList(
                transactionDisplayId(txn),
                currentUserName(),
                sessionNumberOf(txn),
                txn.getName(),
                ShowResultHelpers.createdOn(Instant.ofEpochMilli(txn.getStartTime())),
                "running",
                0L
            )));
        }
        return new ResultSet(columns, rows);
    }

    /** The transaction id both listings display: the start instant at NANOSECOND scale (live's id
     *  equals its {@code started_on} in epoch nanos), with the engine's own transaction counter in
     *  the sub-millisecond digits so same-millisecond transactions stay distinct. */
    private static long transactionDisplayId(final Transaction txn) {
        return txn.getPublicId();
    }

    /**
     * The session a transaction belongs to, as SHOW TRANSACTIONS and SHOW LOCKS IN ACCOUNT print it: the number
     * of the session that began it, which is that session's CURRENT_SESSION() (live-verified — the three agree).
     *
     * @param txn the transaction
     * @return its session's number
     */
    private long sessionNumberOf(final Transaction txn) {
        final Long owner = txn.getSessionNumber();
        return owner != null ? owner : currentSessionNumber();
    }

    /**
     * The number of the session running this statement — SHOW VARIABLES' session_id, the same value its
     * CURRENT_SESSION() answers.
     *
     * @return the session's number
     */
    private long currentSessionNumber() {
        final SecurityManager securityManager = facade.getSecurityManager();
        return securityManager != null && securityManager.getSessionContext() != null
            ? securityManager.getSessionContext().getSessionNumber() : startedOnMillis;
    }

    /** The session's user name, as SHOW TRANSACTIONS spells it. */
    private String currentUserName() {
        final SecurityManager securityManager = facade.getSecurityManager();
        if (securityManager == null || securityManager.getSessionContext() == null) {
            return null;
        }
        final String user = securityManager.getSessionContext().getCurrentUser();
        return user == null ? null : user.toUpperCase();
    }

    public ResultSet showVariables(final String likePattern) {
        final SecurityManager securityManager = facade.getSecurityManager();
        // Live layout: session_id | created_on | updated_on | name | value | type | comment —
        // the variable NAME is folded upper, an unset comment is '', and type names the value's
        // family (fixed / text / boolean).
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("session_id", NumericType.BIGINT),
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("updated_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("value", StringType.VARCHAR),
            new ResultSetColumn("type", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR)
        );
        final List<Row> rows = new ArrayList<>();
        final Map<String, Object> vars = securityManager != null
            ? securityManager.getSessionContext().getAllSessionVariables()
            : sessionVariables;
        for (final Map.Entry<String, Object> e : vars.entrySet()) {
            if (likePattern != null && !ShowResultHelpers.matchesLike(e.getKey(), likePattern)) {
                continue;
            }
            final Object value = e.getValue();
            final String type;
            if (value instanceof Number) {
                type = "fixed";
            } else if (value instanceof Boolean) {
                type = "boolean";
            } else {
                type = "text";
            }
            rows.add(new Row(Arrays.asList(
                currentSessionNumber(),
                ShowResultHelpers.createdOn(Instant.ofEpochMilli(startedOnMillis)),
                ShowResultHelpers.createdOn(Instant.ofEpochMilli(startedOnMillis)),
                e.getKey().toUpperCase(),
                value != null ? value.toString() : null,
                type,
                "")));
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet showQueryHistory(final Integer limit) {
        final List<Row> rows = new ArrayList<>();
        final List<ResultSetColumn> columns = Arrays.asList(
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

        final List<QueryHistory> history;
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
     * scope name means the current one (a nameless TABLE scope lists the current schema's tables), and a
     * NONE scope — a view or a dynamic table named as the table — lists nothing.
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
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
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
            ShowResultHelpers.createdOn(table.getCreatedTime()),
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

    /**
     * SHOW IMPORTED KEYS: one row per foreign-key column of every table in the scope (see
     * showKeysScoped).
     *
     * <p>Seventeen columns, live-verified — the shape its PRIMARY/UNIQUE sibling has, widened at both
     * ends: each side of the reference names its own DATABASE as well as its schema and table, and the
     * row carries {@code deferrability}, {@code rely} and {@code comment} after the two constraint
     * names. The database columns are what let a JDBC {@code getImportedKeys} answer PKTABLE_CAT and
     * FKTABLE_CAT at all.
     *
     * <p>{@code deferrability} is the constant "NOT DEFERRABLE": Snowflake has no deferred constraint
     * checking to report anything else for.
     */
    public ResultSet showImportedKeys(final String scopeKind, final String scopeName) {
        final List<ResultSetColumn> cols = Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("pk_database_name", StringType.VARCHAR),
            new ResultSetColumn("pk_schema_name", StringType.VARCHAR),
            new ResultSetColumn("pk_table_name", StringType.VARCHAR),
            new ResultSetColumn("pk_column_name", StringType.VARCHAR),
            new ResultSetColumn("fk_database_name", StringType.VARCHAR),
            new ResultSetColumn("fk_schema_name", StringType.VARCHAR),
            new ResultSetColumn("fk_table_name", StringType.VARCHAR),
            new ResultSetColumn("fk_column_name", StringType.VARCHAR),
            new ResultSetColumn("key_sequence", NumericType.INTEGER),
            new ResultSetColumn("update_rule", StringType.VARCHAR),
            new ResultSetColumn("delete_rule", StringType.VARCHAR),
            new ResultSetColumn("fk_name", StringType.VARCHAR),
            new ResultSetColumn("pk_name", StringType.VARCHAR),
            new ResultSetColumn("deferrability", StringType.VARCHAR),
            new ResultSetColumn("rely", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR));
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : schemasInScope(scopeKind, scopeName)) {
            for (final Table table : tablesInScope(schema, scopeKind, scopeName)) {
                // Inline column-level `REFERENCES parent[(col)]` is a single-column FK held on the
                // column, not in getForeignKeys() — enumerate those too (the same source
                // INFORMATION_SCHEMA.TABLE_CONSTRAINTS reads), or SHOW IMPORTED KEYS misses them.
                for (final dev.frostlake.metastore.model.TableColumn col : table.getColumns()) {
                    if (col.getReferencedTable() == null) {
                        continue;
                    }
                    final String databaseName = databaseNameOf(schema);
                    final String pkColumn = col.getReferencedColumn() != null
                        ? col.getReferencedColumn()
                        : firstPrimaryKeyColumn(schema, col.getReferencedTable());
                    // A qualified REFERENCES target ("sc.parent" / "db.sc.parent") splits across the
                    // pk_* columns; the raw dotted spelling is never a table NAME.
                    final QualifiedName ref = QualifiedName.parse(col.getReferencedTable());
                    rows.add(new Row(Arrays.asList(
                        ShowResultHelpers.createdOn(table.getCreatedTime()),
                        ref.size() >= 3 ? ref.part(0) : databaseName,
                        ref.size() >= 2 ? ref.part(ref.size() - 2) : schema.getName(),
                        ref.last(),
                        pkColumn,
                        databaseName,
                        schema.getName(),
                        table.getName(),
                        col.getName(),
                        1,
                        col.getOnUpdate() != null ? col.getOnUpdate() : "NO ACTION",
                        col.getOnDelete() != null ? col.getOnDelete() : "NO ACTION",
                        table.columnForeignKeyConstraintName(col.getName()),
                        referencedPrimaryKeyName(schema, col.getReferencedTable()),
                        "NOT DEFERRABLE",
                        relyText(col.getRely()),
                        null)));
                }
                for (final ForeignKeyConstraint fk : table.getForeignKeys()) {
                    final List<String> fkColumns = fk.getColumnNames();
                    final List<String> pkColumns = fk.getReferencedColumns();
                    for (int i = 0; i < fkColumns.size(); i++) {
                        final String databaseName = databaseNameOf(schema);
                        final QualifiedName ref = QualifiedName.parse(fk.getReferencedTable());
                        rows.add(new Row(Arrays.asList(
                            ShowResultHelpers.createdOn(table.getCreatedTime()),
                            ref.size() >= 3 ? ref.part(0) : databaseName,
                            ref.size() >= 2 ? ref.part(ref.size() - 2) : schema.getName(),
                            ref.last(),
                            i < pkColumns.size() ? pkColumns.get(i) : null,
                            databaseName,
                            schema.getName(),
                            table.getName(),
                            fkColumns.get(i),
                            i + 1,
                            fk.getOnUpdate() != null ? fk.getOnUpdate() : "NO ACTION",
                            fk.getOnDelete() != null ? fk.getOnDelete() : "NO ACTION",
                            fk.getConstraintName(),
                            referencedPrimaryKeyName(schema, fk.getReferencedTable()),
                            "NOT DEFERRABLE",
                            relyText(fk.getRely()),
                            null)));
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
    /** The referenced table's first primary-key column — the target of a bare {@code REFERENCES parent}. */
    private String firstPrimaryKeyColumn(final Schema schema, final String referencedTable) {
        final String bare = QualifiedName.parse(referencedTable).last();
        Table target = schema.hasTable(bare) ? schema.getTable(bare) : null;
        if (target == null) {
            try {
                target = catalog.resolveTable(referencedTable);
            } catch (final RuntimeException gone) {
                return null;
            }
        }
        if (target == null) {
            return null;
        }
        for (final dev.frostlake.metastore.model.TableColumn col : target.getColumns()) {
            if (col.isPrimaryKey()) {
                return col.getName();
            }
        }
        return null;
    }

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

    /** The schemas a keys listing spans: current schema, a named schema, a database's schemas, all, or none. */
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
        } else if (!"NONE".equals(scopeKind)) {
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
