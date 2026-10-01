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
import dev.frostlake.executor.operators.ResultSetProvider;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QueryHistoryTracker;
import dev.frostlake.metastore.model.Account;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.ManagedAccount;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.transaction.TransactionManager;
import dev.frostlake.types.DataType;
import java.util.List;
import java.util.Map;

/**
 * Executes SHOW / DESCRIBE catalog-introspection commands. Extracted from {@link QueryExecutor} (which keeps
 * thin forwarders) so that class can stay focused on statement dispatch and query execution. Depends only on
 * the catalog plus a few session/security collaborators — no SELECT-engine or expression-evaluator machinery.
 *
 * <p>This class is now a thin facade: each command is delegated to one of the per-object-family sub-executors
 * ({@link ShowRelationalExecutor}, {@link ShowPipelineExecutor}, {@link ShowRoutineExecutor},
 * {@link ShowSecurityExecutor}, {@link ShowInfraExecutor}, {@link ShowSessionExecutor}). The
 * {@link #securityManager} is installed after construction via {@link #setSecurityManager}, so the security
 * and session sub-executors read it back live through {@link #getSecurityManager()} rather than caching it.
 */
public class ShowCommandExecutor {

    private final Catalog catalog;
    private SecurityManager securityManager;
    private final TransactionManager transactionManager;
    private final QueryHistoryTracker queryHistoryTracker;
    private final Map<String, Object> sessionVariables;

    private final ShowRelationalExecutor relationalExecutor;
    private final ShowPipelineExecutor pipelineExecutor;
    private final ShowRoutineExecutor routineExecutor;
    private final ShowSecurityExecutor securityExecutor;
    private final ShowInfraExecutor infraExecutor;
    private final ShowSessionExecutor sessionExecutor;

    public ShowCommandExecutor(final Catalog catalog, final TransactionManager transactionManager,
                               final QueryHistoryTracker queryHistoryTracker,
                               final Map<String, Object> sessionVariables,
                               final FunctionRegistry functionRegistry,
                               final AccountIdentity identity, final StorageEngine storageEngine) {
        this.catalog = catalog;
        this.transactionManager = transactionManager;
        this.queryHistoryTracker = queryHistoryTracker;
        this.sessionVariables = sessionVariables;
        this.relationalExecutor = new ShowRelationalExecutor(catalog, storageEngine);
        this.pipelineExecutor = new ShowPipelineExecutor(catalog);
        this.routineExecutor = new ShowRoutineExecutor(catalog, functionRegistry);
        this.infraExecutor = new ShowInfraExecutor(catalog);
        // The security and session families read securityManager live through this facade, so they receive
        // a reference to it rather than the (still-null-at-construction) SecurityManager value.
        this.securityExecutor = new ShowSecurityExecutor(catalog, this);
        this.sessionExecutor = new ShowSessionExecutor(catalog, this, transactionManager,
            queryHistoryTracker, sessionVariables, identity);
    }

    public void setSecurityManager(final SecurityManager securityManager) {
        this.securityManager = securityManager;
    }

    /** Live accessor used by the security/session sub-executors, since {@link #securityManager} is set late. */
    SecurityManager getSecurityManager() {
        return securityManager;
    }

    public ResultSet showDatabases() {
        return relationalExecutor.showDatabases();
    }

    /** SHOW DATABASES, with the dropped databases UNDROP can still restore when {@code history} is set. */
    public ResultSet showDatabases(final boolean history) {
        return relationalExecutor.showDatabases(history);
    }

    public ResultSet showSchemas(final String databaseName) {
        return relationalExecutor.showSchemas(databaseName);
    }

    /** SHOW SCHEMAS, with the dropped schemas UNDROP can still restore when {@code history} is set. */
    public ResultSet showSchemas(final String databaseName, final boolean history) {
        return relationalExecutor.showSchemas(databaseName, history);
    }

    /** SHOW SCHEMAS IN ACCOUNT, with the dropped schemas when {@code history} is set. */
    public ResultSet showSchemasInAccount(final boolean history) {
        return relationalExecutor.showSchemasInAccount(history);
    }

    /** SHOW PARAMETERS IN DATABASE. */
    public ResultSet showParametersInDatabase(final String databaseName, final String likePattern) {
        return sessionExecutor.showParametersInDatabase(databaseName, likePattern);
    }

    /** SHOW PARAMETERS IN SCHEMA. */
    public ResultSet showParametersInSchema(final String databaseName, final String schemaName,
                                            final String likePattern) {
        return sessionExecutor.showParametersInSchema(databaseName, schemaName, likePattern);
    }

    public ResultSet showTables(final String schemaName) {
        return relationalExecutor.showTables(schemaName);
    }

    public ResultSet showTablesInAccount() {
        return relationalExecutor.showTablesInAccount();
    }

    public ResultSet showSchemasInAccount() {
        return relationalExecutor.showSchemasInAccount();
    }

    public ResultSet showObjectsInAccount() {
        return relationalExecutor.showObjectsInAccount();
    }

    public ResultSet showViews(final String schemaName) {
        return relationalExecutor.showViews(schemaName);
    }

    public ResultSet showTablesInDatabase(final String databaseName) {
        return relationalExecutor.showTablesInDatabase(databaseName);
    }

    public ResultSet showHybridTables(final String schemaName) {
        return relationalExecutor.showHybridTables(schemaName);
    }

    public ResultSet showHybridTablesInDatabase(final String databaseName) {
        return relationalExecutor.showHybridTablesInDatabase(databaseName);
    }

    public ResultSet showHybridTablesInAccount() {
        return relationalExecutor.showHybridTablesInAccount();
    }

    public ResultSet showViewsInDatabase(final String databaseName) {
        return relationalExecutor.showViewsInDatabase(databaseName);
    }

    public ResultSet showMaterializedViews(final String schemaName) {
        return relationalExecutor.showMaterializedViews(schemaName);
    }

    public ResultSet showMaterializedViewsInDatabase(final String databaseName) {
        return relationalExecutor.showMaterializedViewsInDatabase(databaseName);
    }

    public ResultSet showMaterializedViewsInAccount() {
        return relationalExecutor.showMaterializedViewsInAccount();
    }

    public ResultSet showColumns(final String tableName) {
        return relationalExecutor.showColumns(tableName);
    }

    public ResultSet showStreamsInDatabase(final String databaseName) {
        return pipelineExecutor.showStreamsInDatabase(databaseName);
    }

    public ResultSet showStreamsInAccount() {
        return pipelineExecutor.showStreamsInAccount();
    }

    public ResultSet showStreams(final String schemaName) {
        return pipelineExecutor.showStreams(schemaName);
    }

    public ResultSet showStreams(final String databaseNameOverride, final String schemaName) {
        return pipelineExecutor.showStreams(databaseNameOverride, schemaName);
    }

    public ResultSet showTasks(final String schemaName) {
        return pipelineExecutor.showTasks(schemaName);
    }

    public ResultSet showTasks(final String databaseNameOverride, final String schemaName) {
        return pipelineExecutor.showTasks(databaseNameOverride, schemaName);
    }

    public ResultSet showTasksInDatabase(final String databaseName) {
        return pipelineExecutor.showTasksInDatabase(databaseName);
    }

    public ResultSet showTasksInAccount() {
        return pipelineExecutor.showTasksInAccount();
    }

    public ResultSet showPipes(final String schemaName, final String like) {
        return pipelineExecutor.showPipes(schemaName, like);
    }

    public ResultSet showPipesInDatabase(final String databaseName, final String like) {
        return pipelineExecutor.showPipesInDatabase(databaseName, like);
    }

    public ResultSet showPipesInAccount(final String like) {
        return pipelineExecutor.showPipesInAccount(like);
    }

    public ResultSet showWarehouses() {
        return infraExecutor.showWarehouses();
    }

    public ResultSet showStages(final String schemaName) {
        return infraExecutor.showStages(schemaName);
    }

    public ResultSet showStagesInDatabase(final String databaseName) {
        return infraExecutor.showStagesInDatabase(databaseName);
    }

    public ResultSet showStagesInAccount() {
        return infraExecutor.showStagesInAccount();
    }

    public ResultSet showProcedures(final String schemaName, final boolean userOnly) {
        return routineExecutor.showProcedures(schemaName, userOnly);
    }

    public ResultSet showFunctions(final String schemaName, final boolean userOnly) {
        return routineExecutor.showFunctions(schemaName, userOnly);
    }

    public ResultSet showBuiltinFunctions() {
        return routineExecutor.showBuiltinFunctions();
    }

    public ResultSet showBuiltinProcedures() {
        return routineExecutor.showBuiltinProcedures();
    }

    public ResultSet showProceduresInDatabase(final String databaseName) {
        return routineExecutor.showProceduresInDatabase(databaseName);
    }

    public ResultSet showFunctionsInDatabase(final String databaseName) {
        return routineExecutor.showFunctionsInDatabase(databaseName);
    }

    public ResultSet showUserFunctionsInDatabase(final String databaseName) {
        return routineExecutor.showUserFunctionsInDatabase(databaseName);
    }

    public ResultSet showUserProceduresInDatabase(final String databaseName) {
        return routineExecutor.showUserProceduresInDatabase(databaseName);
    }

    public ResultSet showFunctionsInAccount(final boolean userOnly) {
        return routineExecutor.showFunctionsInAccount(userOnly);
    }

    public ResultSet showProceduresInAccount(final boolean userOnly) {
        return routineExecutor.showProceduresInAccount(userOnly);
    }

    // ==================== DESCRIBE STATEMENTS ====================

    public ResultSet describeTable(final String tableName) {
        return relationalExecutor.describeTable(tableName);
    }

    public ResultSet describeView(final String viewName) {
        return relationalExecutor.describeView(viewName);
    }

    public ResultSet describeRelation(final String name, final String reportedKind) {
        return relationalExecutor.describeRelation(name, reportedKind);
    }

    /**
     * DESCRIBE … TYPE = STAGE: a table's own stage in DESC STAGE's property tree without its DIRECTORY
     * group — the CSV file format and the copy options at their defaults, then the empty location — and a
     * view's or a materialized view's location alone (live-verified). The relation is looked up as its columns
     * are, so a missing one is refused in the named kind's words.
     */
    public ResultSet describeRelationStage(final String name, final String reportedKind) {
        return infraExecutor.describeRelationStage(relationalExecutor.namesTable(name, reportedKind));
    }

    /**
     * Whether a name reaches a table, view or materialized view — see
     * {@link ShowRelationalExecutor#namesAStoredRelation}.
     *
     * @param name the written name
     * @return whether one of those carries it
     */
    public boolean namesAStoredRelation(final String name) {
        return relationalExecutor.namesAStoredRelation(name);
    }

    /**
     * Whether a name reaches a DYNAMIC TABLE of the current schema.
     *
     * @param name the written name
     * @return whether one carries it
     */
    /**
     * The stage properties of a relation already known to be one, without the kind lookup: a dynamic
     * table answers the table's properties.
     *
     * @return the property rows
     */
    public ResultSet describeStoredRelationStage() {
        return infraExecutor.describeRelationStage(true);
    }

    /**
     * The stage properties of a TABLE, which reports the file format and copy options it was created with.
     *
     * @param name the table's written name
     * @return the property rows
     */
    /**
     * Whether a described name reaches a TABLE rather than a view or a materialized view.
     *
     * @param name the written name
     * @param reportedKind the kind a missing name is refused as
     * @return whether a table carries it
     */
    public boolean namesTable(final String name, final String reportedKind) {
        return relationalExecutor.namesTable(name, reportedKind);
    }

    public ResultSet describeTableStage(final String name) {
        final Table described = catalog.resolveTableAsWritten(name, "Table");
        return infraExecutor.describeTableStage(described.getStageFileFormat(), described.getStageCopyOptions());
    }

    public boolean namesDynamicTable(final String name) {
        if (catalog.getCurrentDatabase() == null || catalog.getCurrentSchema() == null) {
            return false;   // with no current schema the lookup has nowhere to look
        }
        try {
            return catalog.getDatabase(catalog.getCurrentDatabase())
                .getSchema(catalog.getCurrentSchema()).hasDynamicTable(name);
        } catch (final RuntimeException noSuchSchema) {
            return false;
        }
    }

    /**
     * DESCRIBE SCHEMA: the schema's tables and views as SHOW OBJECTS lists them, by name, each with its
     * creation time and kind (TABLE, TEMPORARY or VIEW). INFORMATION_SCHEMA's views were never created and
     * carry the epoch (live-verified).
     */
    public ResultSet describeSchema(final String schemaName) {
        return relationalExecutor.describeSchema(schemaName);
    }

    /**
     * DESCRIBE DATABASE: the database's schemas by name, each with its creation time and the kind SCHEMA.
     * INFORMATION_SCHEMA is made as it is read, so it carries the statement's time (live-verified).
     */
    public ResultSet describeDatabase(final String databaseName) {
        return relationalExecutor.describeDatabase(databaseName);
    }

    public ResultSet describeStream(final String streamName) {
        return pipelineExecutor.describeStream(streamName);
    }

    public ResultSet describeTask(final String taskName) {
        return pipelineExecutor.describeTask(taskName);
    }

    public ResultSet describePipe(final String pipeName) {
        return pipelineExecutor.describePipe(pipeName);
    }

    public ResultSet showSequences(final String schemaName) {
        return pipelineExecutor.showSequences(schemaName);
    }

    public ResultSet showSequencesInDatabase(final String databaseName) {
        return pipelineExecutor.showSequencesInDatabase(databaseName);
    }

    public ResultSet describeSequence(final String sequenceName) {
        return pipelineExecutor.describeSequence(sequenceName);
    }

    public ResultSet showComputePools() {
        return infraExecutor.showComputePools();
    }

    public ResultSet showComputePoolInstanceFamilies() {
        return infraExecutor.showComputePoolInstanceFamilies();
    }

    public ResultSet describeComputePool(final String poolName) {
        return infraExecutor.describeComputePool(poolName);
    }

    public ResultSet describeWarehouse(final String warehouseName) {
        return infraExecutor.describeWarehouse(warehouseName);
    }

    public ResultSet describeStage(final String stageName) {
        return infraExecutor.describeStage(stageName);
    }

    public ResultSet showTags(final String schemaName) {
        return routineExecutor.showTags(schemaName);
    }

    public ResultSet showTagsInDatabase(final String databaseName) {
        return routineExecutor.showTagsInDatabase(databaseName);
    }

    public ResultSet showTagsInAccount() {
        return routineExecutor.showTagsInAccount();
    }

    public ResultSet describeTag(final String tagName) {
        return routineExecutor.describeTag(tagName);
    }

    public ResultSet describeFunction(final String[] parts, final List<DataType> argumentTypes) {
        return routineExecutor.describeFunction(parts, argumentTypes);
    }

    public ResultSet describeProcedure(final String[] parts, final List<DataType> argumentTypes) {
        return routineExecutor.describeProcedure(parts, argumentTypes);
    }

    public ResultSet describeUser(final String name) {
        return securityExecutor.describeUser(name);
    }

    public ResultSet describeMaskingPolicy(final String name) {
        return routineExecutor.describeMaskingPolicy(name);
    }

    public ResultSet describeRowAccessPolicy(final String name) {
        return routineExecutor.describeRowAccessPolicy(name);
    }

    public ResultSet describeFileFormat(final String name) {
        return routineExecutor.describeFileFormat(name);
    }

    public ResultSet showFileFormats(final String schemaName) {
        return routineExecutor.showFileFormats(schemaName);
    }

    public ResultSet showFileFormatsInDatabase(final String databaseName) {
        return routineExecutor.showFileFormatsInDatabase(databaseName);
    }

    public ResultSet showFileFormatsInAccount() {
        return routineExecutor.showFileFormatsInAccount();
    }

    public ResultSet describeSearchOptimization(final String tableName) {
        return routineExecutor.describeSearchOptimization(tableName);
    }

    public ResultSet showJoinPolicies(final String schemaName) {
        return routineExecutor.showJoinPolicies(schemaName);
    }

    public ResultSet showJoinPoliciesInDatabase(final String databaseName) {
        return routineExecutor.showJoinPoliciesInDatabase(databaseName);
    }

    public ResultSet showJoinPoliciesInAccount() {
        return routineExecutor.showJoinPoliciesInAccount();
    }

    public ResultSet describeJoinPolicy(final String policyName) {
        return routineExecutor.describeJoinPolicy(policyName);
    }

    public ResultSet showAggregationPolicies(final String schemaName) {
        return routineExecutor.showAggregationPolicies(schemaName);
    }

    public ResultSet showAggregationPoliciesInDatabase(final String databaseName) {
        return routineExecutor.showAggregationPoliciesInDatabase(databaseName);
    }

    public ResultSet showAggregationPoliciesInAccount() {
        return routineExecutor.showAggregationPoliciesInAccount();
    }

    public ResultSet describeAggregationPolicy(final String policyName) {
        return routineExecutor.describeAggregationPolicy(policyName);
    }

    public ResultSet showProjectionPolicies(final String schemaName) {
        return routineExecutor.showProjectionPolicies(schemaName);
    }

    public ResultSet showProjectionPoliciesInDatabase(final String databaseName) {
        return routineExecutor.showProjectionPoliciesInDatabase(databaseName);
    }

    public ResultSet showProjectionPoliciesInAccount() {
        return routineExecutor.showProjectionPoliciesInAccount();
    }

    public ResultSet describeProjectionPolicy(final String policyName) {
        return routineExecutor.describeProjectionPolicy(policyName);
    }

    public ResultSet showContacts(final String schemaName) {
        return routineExecutor.showContacts(schemaName);
    }

    public ResultSet showContactsInDatabase(final String databaseName) {
        return routineExecutor.showContactsInDatabase(databaseName);
    }

    public ResultSet showContactsInAccount() {
        return routineExecutor.showContactsInAccount();
    }

    public ResultSet showMaskingPolicies(final String schemaName) {
        return routineExecutor.showMaskingPolicies(schemaName);
    }

    public ResultSet showMaskingPoliciesInDatabase(final String databaseName) {
        return routineExecutor.showMaskingPoliciesInDatabase(databaseName);
    }

    public ResultSet showMaskingPoliciesInAccount() {
        return routineExecutor.showMaskingPoliciesInAccount();
    }

    public ResultSet showRowAccessPolicies(final String schemaName) {
        return routineExecutor.showRowAccessPolicies(schemaName);
    }

    public ResultSet showRowAccessPoliciesInDatabase(final String databaseName) {
        return routineExecutor.showRowAccessPoliciesInDatabase(databaseName);
    }

    public ResultSet showRowAccessPoliciesInAccount() {
        return routineExecutor.showRowAccessPoliciesInAccount();
    }

    public ResultSet showKeysScoped(final boolean primary, final String scopeKind, final String scopeName) {
        return sessionExecutor.showKeysScoped(primary, scopeKind, scopeName);
    }

    public ResultSet showImportedKeys(final String scopeKind, final String scopeName) {
        return sessionExecutor.showImportedKeys(scopeKind, scopeName);
    }

    public ResultSet showColumnsScoped(final String name, final boolean view) {
        return relationalExecutor.showColumnsScoped(name, view);
    }

    public ResultSet showColumnsInSchema(final String name) {
        return relationalExecutor.showColumnsInSchema(name);
    }

    public ResultSet showColumnsInDatabase(final String databaseName) {
        return relationalExecutor.showColumnsInDatabase(databaseName);
    }

    public ResultSet showColumnsInAccount() {
        return relationalExecutor.showColumnsInAccount();
    }

    public ResultSet showViewsInAccount() {
        return relationalExecutor.showViewsInAccount();
    }

    public ResultSet showSequencesInAccount() {
        return pipelineExecutor.showSequencesInAccount();
    }

    public ResultSet showPrimaryKeys(final String tableName) {
        return sessionExecutor.showPrimaryKeys(tableName);
    }

    public ResultSet showUniqueKeys(final String tableName) {
        return sessionExecutor.showUniqueKeys(tableName);
    }

    // ==================== USER AND ROLE STATEMENTS ====================

    public ResultSet showUsers() {
        return securityExecutor.showUsers();
    }

    public ResultSet showGrantsOfRole(final String roleName) {
        return securityExecutor.showGrantsOfRole(roleName);
    }

    public ResultSet showGrantsForCurrentUser() {
        return securityExecutor.showGrantsForCurrentUser();
    }

    public ResultSet showRoles() {
        return securityExecutor.showRoles();
    }

    public ResultSet showDatabaseRoles() {
        return securityExecutor.showDatabaseRoles();
    }

    /** SHOW ROLES IN DATABASE: the database's own roles. */
    public ResultSet showDatabaseRoles(final Database database) {
        return securityExecutor.showDatabaseRoles(database);
    }

    public ResultSet showCortexSearchServices(final String schemaName, final String like) {
        return pipelineExecutor.showCortexSearchServices(schemaName, like);
    }

    public ResultSet showCortexSearchServicesInDatabase(final String databaseName, final String like) {
        return pipelineExecutor.showCortexSearchServicesInDatabase(databaseName, like);
    }

    public ResultSet showCortexSearchServicesInAccount(final String like) {
        return pipelineExecutor.showCortexSearchServicesInAccount(like);
    }

    public ResultSet describeCortexSearchService(final String serviceName) {
        return pipelineExecutor.describeCortexSearchService(serviceName);
    }

    public ResultSet showDynamicTables(final String schemaName) {
        return pipelineExecutor.showDynamicTables(schemaName);
    }

    public ResultSet showDynamicTablesInDatabase(final String databaseName) {
        return pipelineExecutor.showDynamicTablesInDatabase(databaseName);
    }

    public ResultSet showDynamicTablesInAccount() {
        return pipelineExecutor.showDynamicTablesInAccount();
    }

    public ResultSet describeDynamicTable(final String tableName, final ResultSetProvider projection) {
        return pipelineExecutor.describeDynamicTable(tableName, projection);
    }

    public ResultSet showParameters(final String likePattern) {
        return sessionExecutor.showParameters(likePattern);
    }

    public ResultSet showParametersInTask(final String taskName, final String likePattern) {
        return sessionExecutor.showParametersInTask(taskName, likePattern);
    }

    public ResultSet showParametersInUser(final String userName, final String likePattern) {
        return sessionExecutor.showParametersInUser(userName, likePattern);
    }

    public ResultSet showParametersInWarehouse(final String warehouseName, final String likePattern) {
        return sessionExecutor.showParametersInWarehouse(warehouseName, likePattern);
    }

    public ResultSet showObjects(final String schemaName) {
        return relationalExecutor.showObjects(schemaName);
    }

    public ResultSet showObjectsInDatabase(final String databaseName) {
        return relationalExecutor.showObjectsInDatabase(databaseName);
    }

    public ResultSet showOrganizationAccounts() {
        return sessionExecutor.showOrganizationAccounts();
    }

    public ResultSet showAccounts() {
        return sessionExecutor.showAccounts();
    }

    /** SHOW ACCOUNTS [HISTORY]: this account and the accounts CREATE ACCOUNT recorded. */
    public ResultSet showAccounts(final boolean history, final List<Account> created, final int managed) {
        return sessionExecutor.showAccounts(history, created, managed);
    }

    /** SHOW MANAGED ACCOUNTS. */
    public ResultSet showManagedAccounts(final List<ManagedAccount> accounts) {
        return sessionExecutor.showManagedAccounts(accounts);
    }

    public ResultSet showLocks(final boolean inAccount) {
        return sessionExecutor.showLocks(inAccount);
    }

    public ResultSet showTransactions(final String likePattern) {
        return sessionExecutor.showTransactions(likePattern);
    }

    public ResultSet showVariables(final String likePattern) {
        return sessionExecutor.showVariables(likePattern);
    }

    public ResultSet showQueryHistory(final Integer limit) {
        return sessionExecutor.showQueryHistory(limit);
    }

    public ResultSet showGrantsOnObject(final String objectType, final String objectName, final GrantedObject granted) {
        return securityExecutor.showGrantsOnObject(objectType, objectName, granted);
    }

    public ResultSet showGrantsTo(final String targetType, final String targetName) {
        return securityExecutor.showGrantsTo(targetType, targetName);
    }
}
