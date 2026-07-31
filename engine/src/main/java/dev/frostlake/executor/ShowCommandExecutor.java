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

import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QueryHistoryTracker;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.transaction.TransactionManager;
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
                               final FunctionRegistry functionRegistry) {
        this.catalog = catalog;
        this.transactionManager = transactionManager;
        this.queryHistoryTracker = queryHistoryTracker;
        this.sessionVariables = sessionVariables;
        this.relationalExecutor = new ShowRelationalExecutor(catalog);
        this.pipelineExecutor = new ShowPipelineExecutor(catalog);
        this.routineExecutor = new ShowRoutineExecutor(catalog, functionRegistry);
        this.infraExecutor = new ShowInfraExecutor(catalog);
        // The security and session families read securityManager live through this facade, so they receive
        // a reference to it rather than the (still-null-at-construction) SecurityManager value.
        this.securityExecutor = new ShowSecurityExecutor(catalog, this);
        this.sessionExecutor = new ShowSessionExecutor(catalog, this, transactionManager,
            queryHistoryTracker, sessionVariables);
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

    public ResultSet showSchemas(final String databaseName) {
        return relationalExecutor.showSchemas(databaseName);
    }

    public ResultSet showTables(final String schemaName) {
        return relationalExecutor.showTables(schemaName);
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

    public ResultSet showViewsInDatabase(final String databaseName) {
        return relationalExecutor.showViewsInDatabase(databaseName);
    }

    public ResultSet showMaterializedViews(final String schemaName) {
        return relationalExecutor.showMaterializedViews(schemaName);
    }

    public ResultSet showMaterializedViewsInDatabase(final String databaseName) {
        return relationalExecutor.showMaterializedViewsInDatabase(databaseName);
    }

    public ResultSet showColumns(final String tableName) {
        return relationalExecutor.showColumns(tableName);
    }

    public ResultSet showStreamsInDatabase(final String databaseName) {
        return pipelineExecutor.showStreamsInDatabase(databaseName);
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

    public ResultSet showPipes(final String schemaName, final String like) {
        return pipelineExecutor.showPipes(schemaName, like);
    }

    public ResultSet showPipesInDatabase(final String databaseName, final String like) {
        return pipelineExecutor.showPipesInDatabase(databaseName, like);
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

    public ResultSet showProcedures(final String schemaName, final boolean userOnly) {
        return routineExecutor.showProcedures(schemaName, userOnly);
    }

    public ResultSet showFunctions(final String schemaName, final boolean userOnly) {
        return routineExecutor.showFunctions(schemaName, userOnly);
    }

    public ResultSet showProceduresInDatabase(final String databaseName) {
        return routineExecutor.showProceduresInDatabase(databaseName);
    }

    public ResultSet showFunctionsInDatabase(final String databaseName) {
        return routineExecutor.showFunctionsInDatabase(databaseName);
    }

    // ==================== DESCRIBE STATEMENTS ====================

    public ResultSet describeTable(final String tableName) {
        return relationalExecutor.describeTable(tableName);
    }

    public ResultSet describeView(final String viewName) {
        return relationalExecutor.describeView(viewName);
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

    public ResultSet describeTag(final String tagName) {
        return routineExecutor.describeTag(tagName);
    }

    public ResultSet describeFunction(final String name) {
        return routineExecutor.describeFunction(name);
    }

    public ResultSet describeProcedure(final String name) {
        return routineExecutor.describeProcedure(name);
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

    public ResultSet showMaskingPolicies(final String schemaName) {
        return routineExecutor.showMaskingPolicies(schemaName);
    }

    public ResultSet showMaskingPoliciesInDatabase(final String databaseName) {
        return routineExecutor.showMaskingPoliciesInDatabase(databaseName);
    }

    public ResultSet showRowAccessPolicies(final String schemaName) {
        return routineExecutor.showRowAccessPolicies(schemaName);
    }

    public ResultSet showRowAccessPoliciesInDatabase(final String databaseName) {
        return routineExecutor.showRowAccessPoliciesInDatabase(databaseName);
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

    public ResultSet showRoles() {
        return securityExecutor.showRoles();
    }

    public ResultSet showDynamicTables(final String schemaName) {
        return pipelineExecutor.showDynamicTables(schemaName);
    }

    public ResultSet showDynamicTablesInDatabase(final String databaseName) {
        return pipelineExecutor.showDynamicTablesInDatabase(databaseName);
    }

    public ResultSet describeDynamicTable(final String tableName) {
        return pipelineExecutor.describeDynamicTable(tableName);
    }

    public ResultSet showParameters(final String likePattern) {
        return sessionExecutor.showParameters(likePattern);
    }

    public ResultSet showSessions(final String likePattern) {
        return sessionExecutor.showSessions(likePattern);
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

    public ResultSet showLocks() {
        return sessionExecutor.showLocks();
    }

    public ResultSet showTransactions(final String likePattern) {
        return sessionExecutor.showTransactions(likePattern);
    }

    public ResultSet showVariables() {
        return sessionExecutor.showVariables();
    }

    public ResultSet showQueryHistory(final Integer limit) {
        return sessionExecutor.showQueryHistory(limit);
    }

    public ResultSet showGrantsOnObject(final String objectType, final String objectName) {
        return securityExecutor.showGrantsOnObject(objectType, objectName);
    }

    public ResultSet showGrantsTo(final String targetType, final String targetName) {
        return securityExecutor.showGrantsTo(targetType, targetName);
    }
}
