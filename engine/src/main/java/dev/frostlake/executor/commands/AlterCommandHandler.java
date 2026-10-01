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


package dev.frostlake.executor.commands;

import dev.frostlake.executor.ContainerParameterCatalog;
import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.FileFormatReference;
import dev.frostlake.executor.NumericRangeRefusal;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SQLCommandVisitor;
import dev.frostlake.executor.SessionParameterCatalog;
import dev.frostlake.executor.SqlAccessControlError;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.executor.StatementClock;
import dev.frostlake.executor.StatementErrors;
import dev.frostlake.executor.TransientRetentionLimit;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.IntervalCasts;
import dev.frostlake.executor.procedural.ProceduralException;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.DataMetricFunctions;
import dev.frostlake.metastore.InstanceFamilies;
import dev.frostlake.metastore.NoCurrentDatabaseRefusal;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.SqlObject;
import dev.frostlake.metastore.TableShadows;
import dev.frostlake.metastore.Taggable;
import dev.frostlake.metastore.model.AggregationPolicy;
import dev.frostlake.metastore.model.ComputePool;
import dev.frostlake.metastore.model.ComputePoolState;
import dev.frostlake.metastore.model.Contact;
import dev.frostlake.metastore.model.CortexSearchService;
import dev.frostlake.metastore.model.DataMetricAttachment;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.JoinPolicy;
import dev.frostlake.metastore.model.MaskingPolicy;
import dev.frostlake.metastore.model.Pipe;
import dev.frostlake.metastore.model.ProjectionPolicy;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.RowAccessPolicy;
import dev.frostlake.metastore.model.ScalingPolicy;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurableObjectType;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.StreamSourceType;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskState;
import dev.frostlake.metastore.model.User;
import dev.frostlake.metastore.model.View;
import dev.frostlake.metastore.model.Warehouse;
import dev.frostlake.metastore.model.WarehouseSize;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.TableStorage;
import dev.frostlake.task.TaskScheduler;
import dev.frostlake.transaction.TransactionManager;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.GeographyType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Handles {@code ALTER <object-type> …} statements for DATABASE / SCHEMA / MASKING &amp; ROW ACCESS
 * POLICY / TABLE / VIEW / STREAM / TASK / PIPE / WAREHOUSE / STAGE / USER / ROLE / SESSION / TAG,
 * extracted verbatim from {@link SQLCommandVisitor#visitAlterStatement}. Leaf object types the visitor
 * historically routed to the DDL side (FILE FORMAT / FUNCTION / PROCEDURE / DYNAMIC TABLE /
 * MATERIALIZED VIEW / SEQUENCE) are still forwarded to {@link DDLCommandHandler#handleAlterStatement}.
 * Parse-text extraction ({@code getText}, {@code extractStringLiteral}, {@code getOriginalText}) and the
 * shared engine helpers ({@code parseDataType}, {@code parseLiteral}, {@code resolveCurrentSchema}) are
 * reached through the {@code visitor} back-reference so their exact original behavior is preserved; the
 * task scheduler is read live via {@link SQLCommandVisitor#getTaskScheduler()} (it is wired onto the
 * visitor post-construction).
 */
public class AlterCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(AlterCommandHandler.class);

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;
    private final SQLCommandVisitor visitor;
    private final DDLCommandHandler ddlHandler;

    /**
     * Wires the handler to the engine collaborators every ALTER branch needs.
     *
     * @param catalog the metastore catalog whose objects the ALTER actions mutate
     * @param queryExecutor the engine's query executor, for session state and security checks
     * @param visitor back-reference to the visitor's shared parse-text and type helpers (the task
     *                scheduler is read through it live, as it is wired on post-construction)
     * @param ddlHandler the DDL-side handler that still owns the forwarded leaf object types
     */
    public AlterCommandHandler(final Catalog catalog, final QueryExecutor queryExecutor,
                               final SQLCommandVisitor visitor, final DDLCommandHandler ddlHandler) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
        this.visitor = visitor;
        this.ddlHandler = ddlHandler;
    }

    @Override
    public Catalog getCatalog() {
        return catalog;
    }

    @Override
    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    /**
     * Dispatches an {@code ALTER <object-type> …} statement on the object-type keyword in the parse
     * tree and applies the requested action (RENAME / SET / UNSET / COMMENT / SUSPEND / RESUME /
     * column and constraint changes, …) to the catalog object, after the owner/ALTER-privilege check.
     * {@code IF EXISTS} turns a missing target into a silent success instead of an error; leaf object
     * types the visitor historically routed to the DDL side are forwarded to
     * {@link DDLCommandHandler#handleAlterStatement}.
     *
     * @param ctx the ALTER statement's parse tree
     * @return null for the branches handled here (the executor renders the standard status result),
     *         or the DDL handler's result for a forwarded object type
     */
    public Object handle(final FrostlakeParser.AlterStatementContext ctx) {
        final boolean ifExists = ctx.if_exists() != null;
        // Set by the branches that gate IF EXISTS themselves (ALTER TABLE, ALTER COMPUTE POOL)
        // instead of leaning on the blanket catch below.
        boolean branchHandlesIfExists = false;
        try {
            if (ctx.ALERT() != null) {
                final AlertCommandHandler alerts = new AlertCommandHandler(queryExecutor);
                final FrostlakeParser.AlertActionContext action = ctx.alertAction();
                if (action.tagSet() != null) {
                    applyTagSet(alerts.require(ctx.qualifiedName()), action.tagSet());
                } else if (action.tagUnset() != null) {
                    applyTagUnset(alerts.require(ctx.qualifiedName()), action.tagUnset());
                } else {
                    alerts.alter(ctx.qualifiedName(), action);
                }
                return null;
            }
            if (ctx.DATABASE() != null) {
                final String dbName = visitor.getText(ctx.identifier());
                final Database database = catalog.databaseExact(dbName);
                checkAlter(SecurableObjectType.DATABASE, dbName);

                if (ctx.databaseAction().RENAME() != null) {
                    // Renaming an object needs OWNERSHIP of it, as replacing or dropping it does.
                    queryExecutor.requireOwnership("DATABASE", new String[] {dbName}, null);
                    final String newName = visitor.getText(ctx.databaseAction().identifier());
                    catalog.renameDatabase(dbName, newName);
                    queryExecutor.renameDatabaseStorage(dbName, newName);
                    logger.trace("Renamed database {} to {}", dbName, newName);
                } else if (ctx.databaseAction().COMMENT() != null) {
                    final String comment = visitor.extractStringLiteral(ctx.databaseAction().STRING_LITERAL());
                    database.setComment(comment);
                    logger.trace("Set comment on database: {}", dbName);
                } else if (ctx.databaseAction().READ_ONLY() != null) {
                    if (ctx.databaseAction().UNSET() != null) {
                        database.setReadOnly(false);
                    } else {
                        database.setReadOnly(ctx.databaseAction().booleanValue().TRUE() != null);
                    }
                    logger.trace("Set read_only={} on database: {}", database.isReadOnly(), dbName);
                } else if (ctx.databaseAction().tagSet() != null) {
                    applyTagSet(database, ctx.databaseAction().tagSet());
                } else if (ctx.databaseAction().tagUnset() != null) {
                    applyTagUnset(database, ctx.databaseAction().tagUnset());
                } else if (ctx.databaseAction().SET() != null && ctx.databaseAction().optionKey() != null) {
                    // A known parameter is accepted and inert; an unknown one refuses (live-verified).
                    final String rawKey = ctx.databaseAction().optionKey().getText();
                    if (!ParameterRegistry.isDatabaseParameter(ParameterRegistry.canonical(rawKey))) {
                        throw ParameterRegistry.invalidProperty(ParameterRegistry.spell(rawKey), "DATABASE");
                    }
                    requireLegalRetention(rawKey, ctx.databaseAction().copyOptionValue());
                    requireAccountRetention(rawKey, ctx.databaseAction().copyOptionValue());
                    requireTransientRetention(rawKey, database.isTransientObject(),
                        ctx.databaseAction().copyOptionValue());
                    if ("DATA_RETENTION_TIME_IN_DAYS".equalsIgnoreCase(rawKey)
                            && ctx.databaseAction().copyOptionValue() != null
                            && ctx.databaseAction().copyOptionValue().getText().matches("[0-9]+")) {
                        database.setDataRetentionTimeInDays(
                            Integer.valueOf(ctx.databaseAction().copyOptionValue().getText()));
                    } else if (ctx.databaseAction().copyOptionValue() != null) {
                        database.getParameters().set(ParameterRegistry.canonical(rawKey),
                            NamespaceProperties.render(ctx.databaseAction().copyOptionValue()));
                    }
                } else if (ctx.databaseAction().UNSET() != null
                        && ctx.databaseAction().optionKey() != null) {
                    // UNSET restores INHERITANCE: the database falls back to the account default,
                    // and SHOW answers that value afterwards (live-verified). Other parameters are
                    // accepted and inert, mirroring SET.
                    if ("DATA_RETENTION_TIME_IN_DAYS".equalsIgnoreCase(
                            ctx.databaseAction().optionKey().getText())) {
                        database.setDataRetentionTimeInDays(null);
                    } else if ("COMMENT".equals(ParameterRegistry.canonical(
                            ctx.databaseAction().optionKey().getText()))) {
                        database.setComment(null);
                    } else {
                        database.getParameters().unset(
                            ParameterRegistry.canonical(ctx.databaseAction().optionKey().getText()));
                    }
                }

            } else if (ctx.SCHEMA() != null) {
                final String schemaName = visitor.getText(ctx.qualifiedName());
                final Schema schema = catalog.resolveSchema(
                    QualifiedName.of(catalog.withoutAccount(qualifiedNameParts(ctx.qualifiedName()), 2)));
                checkAlter(SecurableObjectType.SCHEMA, schemaName);
                if (ctx.schemaAction() != null && ctx.schemaAction().SET() != null
                        && ctx.schemaAction().optionKey() != null) {
                    // Schema parameters are accepted and inert — except a value the account
                    // refuses (a negative retention, the 90-day ceiling, the transient cap) and
                    // the retention itself, which is STORED and read back by SHOW.
                    final String rawKey = ctx.schemaAction().optionKey().getText();
                    requireLegalRetention(rawKey, ctx.schemaAction().copyOptionValue());
                    requireAccountRetention(rawKey, ctx.schemaAction().copyOptionValue());
                    requireTransientRetention(rawKey,
                        schema.isTransientObject(), ctx.schemaAction().copyOptionValue());
                    if ("DATA_RETENTION_TIME_IN_DAYS".equalsIgnoreCase(rawKey)
                            && ctx.schemaAction().copyOptionValue() != null
                            && ctx.schemaAction().copyOptionValue().getText().matches("[0-9]+")) {
                        schema.setDataRetentionTimeInDays(
                            Integer.valueOf(ctx.schemaAction().copyOptionValue().getText()));
                    } else if (ctx.schemaAction().copyOptionValue() != null
                            && (ParameterRegistry.isSchemaParameter(ParameterRegistry.canonical(rawKey))
                                || ContainerParameterCatalog.isSchemaParameter(ParameterRegistry.canonical(rawKey)))) {
                        schema.getParameters().set(ParameterRegistry.canonical(rawKey),
                            NamespaceProperties.render(ctx.schemaAction().copyOptionValue()));
                    }
                }
                if (ctx.schemaAction() != null && ctx.schemaAction().UNSET() != null
                        && ctx.schemaAction().optionKey() != null) {
                    // UNSET restores INHERITANCE: the schema falls back to its database's CURRENT
                    // value (live-verified — SHOW answers the container's value afterwards).
                    if ("DATA_RETENTION_TIME_IN_DAYS".equalsIgnoreCase(
                            ctx.schemaAction().optionKey().getText())) {
                        schema.setDataRetentionTimeInDays(null);
                    } else if ("COMMENT".equals(ParameterRegistry.canonical(
                            ctx.schemaAction().optionKey().getText()))) {
                        schema.setComment(null);
                    } else {
                        schema.getParameters().unset(
                            ParameterRegistry.canonical(ctx.schemaAction().optionKey().getText()));
                    }
                }
                if (ctx.schemaAction() != null && ctx.schemaAction().MANAGED() != null) {
                    // ENABLE | DISABLE MANAGED ACCESS: only the schema owner grants on its objects.
                    schema.setManagedAccess(ctx.schemaAction().ENABLE() != null);
                }

                if (ctx.schemaAction().RENAME() != null) {
                    if ("INFORMATION_SCHEMA".equals(schema.getName())) {
                        throw new RuntimeException(SqlAccessControlError.insufficientPrivileges("schema",
                            "INFORMATION_SCHEMA"));
                    }
                    queryExecutor.requireOwnership("SCHEMA",
                        catalog.withoutAccount(qualifiedNameParts(ctx.qualifiedName()), 2), null);
                    // A two-part new name places the schema in that database; a bare one in the session's
                    // database, so the schema MOVES when that is another — live, `USE DATABASE d2` then
                    // `ALTER SCHEMA d1.s RENAME TO s2` lists S2 in d2. A third part names nothing
                    // ("Object does not exist, or operation cannot be performed."), as does `d..s`.
                    final String[] target = catalog.withoutAccount(
                        qualifiedNameParts(ctx.schemaAction().qualifiedName()), 2);
                    final String oldDatabase = schema.getDatabaseName();
                    final String oldName = schema.getName();
                    final String targetDatabase = target.length == 2 ? target[0]
                        : catalog.getCurrentDatabase() != null ? catalog.getCurrentDatabase() : oldDatabase;
                    catalog.moveSchema(schema, targetDatabase, target[target.length - 1]);
                    queryExecutor.renameSchemaStorage(oldDatabase, oldName, schema.getDatabaseName(), schema.getName());
                    logger.trace("Renamed schema {} to {}.{}", schemaName, schema.getDatabaseName(), schema.getName());
                } else if (ctx.schemaAction().COMMENT() != null) {
                    final String comment = visitor.extractStringLiteral(ctx.schemaAction().STRING_LITERAL());
                    schema.setComment(comment);
                    logger.trace("Set comment on schema: {}", schemaName);
                } else if (ctx.schemaAction().tagSet() != null) {
                    applyTagSet(schema, ctx.schemaAction().tagSet());
                } else if (ctx.schemaAction().tagUnset() != null) {
                    applyTagUnset(schema, ctx.schemaAction().tagUnset());
                }

            } else if (ctx.MASKING() != null && ctx.POLICY() != null) {
                final String policyName = visitor.getText(ctx.qualifiedName());
                final FrostlakeParser.PolicyActionContext action = ctx.policyAction();
                PolicyPropertyList.validate(action, "MASKING_POLICY");
                branchHandlesIfExists = true;
                final MaskingPolicy policy;
                try {
                    checkAlter(SecurableObjectType.MASKING_POLICY, policyName);
                    policy = existingPolicy(catalog.findMaskingPolicy(policyName), "Masking policy", policyName);
                } catch (final RuntimeException e) {
                    if (!ifExists) {
                        throw e;
                    }
                    logger.debug("Masking policy does not exist (IF EXISTS): {}", policyName);
                    return null;
                }
                if (action.RENAME() != null) {
                    catalog.renameMaskingPolicy(policyName, visitor.getText(action.qualifiedName()));
                } else if (action.BODY() != null) {
                    // A new body is compiled exactly as the CREATE compiled the first one.
                    final String newBody = policyBody(action);
                    PolicyBodyCheck.compile(queryExecutor, catalog, policy.getName(),
                        policy.getReturnDataType(), policy.getParameters(), newBody, null);
                    policy.setBody(newBody);
                } else {
                    applyPolicyProperties(policy, action);
                }
                logger.trace("Altered masking policy {}", policyName);

            } else if (ctx.JOIN() != null && ctx.POLICY() != null) {
                final String policyName = visitor.getText(ctx.qualifiedName());
                final FrostlakeParser.PolicyActionContext action = ctx.policyAction();
                PolicyPropertyList.validate(action, "JOIN_POLICY");
                branchHandlesIfExists = true;
                final JoinPolicy policy;
                try {
                    policy = existingPolicy(catalog.findJoinPolicy(policyName), "Join policy", policyName);
                } catch (final RuntimeException e) {
                    if (!ifExists) {
                        throw e;
                    }
                    logger.debug("Join policy does not exist (IF EXISTS): {}", policyName);
                    return null;
                }
                if (action.RENAME() != null) {
                    catalog.renameJoinPolicy(policyName, visitor.getText(action.qualifiedName()));
                } else if (action.BODY() != null) {
                    final String newBody = policyBody(action);
                    PolicyBodyCheck.requireNumericLimits(newBody);
                    policy.setBody(newBody);
                } else {
                    applyPolicyProperties(policy, action);
                }
                logger.trace("Altered join policy {}", policyName);

            } else if (ctx.AGGREGATION() != null && ctx.POLICY() != null) {
                final String policyName = visitor.getText(ctx.qualifiedName());
                final FrostlakeParser.PolicyActionContext action = ctx.policyAction();
                PolicyPropertyList.validate(action, "AGGREGATION_POLICY");
                branchHandlesIfExists = true;
                final AggregationPolicy policy;
                try {
                    policy = existingPolicy(catalog.findAggregationPolicy(policyName), "Aggregation policy", policyName);
                } catch (final RuntimeException e) {
                    if (!ifExists) {
                        throw e;
                    }
                    logger.debug("Aggregation policy does not exist (IF EXISTS): {}", policyName);
                    return null;
                }
                if (action.RENAME() != null) {
                    catalog.renameAggregationPolicy(policyName, visitor.getText(action.qualifiedName()));
                } else if (action.BODY() != null) {
                    final String newBody = policyBody(action);
                    PolicyBodyCheck.requireNumericLimits(newBody);
                    policy.setBody(newBody);
                } else {
                    applyPolicyProperties(policy, action);
                }
                logger.trace("Altered aggregation policy {}", policyName);

            } else if (ctx.PROJECTION() != null && ctx.POLICY() != null) {
                final String policyName = visitor.getText(ctx.qualifiedName());
                final FrostlakeParser.PolicyActionContext action = ctx.policyAction();
                PolicyPropertyList.validate(action, "PROJECTION_POLICY");
                branchHandlesIfExists = true;
                final ProjectionPolicy policy;
                try {
                    policy = existingPolicy(catalog.findProjectionPolicy(policyName), "Projection policy", policyName);
                } catch (final RuntimeException e) {
                    if (!ifExists) {
                        throw e;
                    }
                    logger.debug("Projection policy does not exist (IF EXISTS): {}", policyName);
                    return null;
                }
                if (action.RENAME() != null) {
                    catalog.renameProjectionPolicy(policyName, visitor.getText(action.qualifiedName()));
                } else if (action.BODY() != null) {
                    final String newBody = policyBody(action);
                    PolicyBodyCheck.requireNumericLimits(newBody);
                    policy.setBody(newBody);
                } else {
                    applyPolicyProperties(policy, action);
                }
                logger.trace("Altered projection policy {}", policyName);

            } else if (ctx.CONTACT() != null) {
                final String contactName = visitor.getText(ctx.qualifiedName());
                try {
                    final Contact contact = catalog.findContact(contactName);
                    if (contact == null) {
                        throw new RuntimeException(SqlCompilationError.doesNotExist("Contact",
                            catalog.qualifiedObjectName(contactName)));
                    }
                    // UNSET COMMENT is the form without a comment clause.
                    contact.setComment(ctx.commentClause() == null ? null
                        : visitor.extractStringLiteral(ctx.commentClause().STRING_LITERAL()));
                    logger.trace("Set comment on contact {}", contactName);
                } catch (final RuntimeException e) {
                    if (!ifExists) {
                        throw e;
                    }
                    logger.debug("Contact does not exist (IF EXISTS): {}", contactName);
                }

            } else if (ctx.ROW() != null && ctx.ACCESS() != null && ctx.POLICY() != null) {
                final String policyName = visitor.getText(ctx.qualifiedName());
                final FrostlakeParser.PolicyActionContext action = ctx.policyAction();
                PolicyPropertyList.validate(action, "ROW_ACCESS_POLICY");
                branchHandlesIfExists = true;
                final RowAccessPolicy policy;
                try {
                    checkAlter(SecurableObjectType.ROW_ACCESS_POLICY, policyName);
                    policy = existingPolicy(catalog.findRowAccessPolicy(policyName), "Row access policy", policyName);
                } catch (final RuntimeException e) {
                    if (!ifExists) {
                        throw e;
                    }
                    logger.debug("Row access policy does not exist (IF EXISTS): {}", policyName);
                    return null;
                }
                if (action.RENAME() != null) {
                    catalog.renameRowAccessPolicy(policyName, visitor.getText(action.qualifiedName()));
                } else if (action.BODY() != null) {
                    final String newBody = policyBody(action);
                    PolicyBodyCheck.compile(queryExecutor, catalog, policy.getName(),
                        BooleanType.BOOLEAN, policy.getParameters(), newBody, null);
                    policy.setBody(newBody);
                } else {
                    applyPolicyProperties(policy, action);
                }
                logger.trace("Altered row access policy {}", policyName);

            } else if (ctx.FILE() != null && ctx.FORMAT() != null) {
                return ddlHandler.handleAlterStatement(ctx);
            } else if (ctx.FUNCTION() != null || ctx.PROCEDURE() != null) {
                // IF EXISTS forgives only the routine's absence: a malformed signature or a taken new name
                // still refuses (live-verified).
                branchHandlesIfExists = true;
                return ddlHandler.handleAlterStatement(ctx);
            } else if (ctx.DYNAMIC() != null && ctx.TABLE() != null) {
                checkAlter(SecurableObjectType.DYNAMIC_TABLE, visitor.getText(ctx.qualifiedName()));
                final FrostlakeParser.DynamicTableActionContext dynamicAction = ctx.dynamicTableAction();
                if (dynamicAction.tagSet() != null) {
                    applyTagSet(ddlHandler.resolveDynamicTable(ctx.qualifiedName()), dynamicAction.tagSet());
                } else if (dynamicAction.tagUnset() != null) {
                    applyTagUnset(ddlHandler.resolveDynamicTable(ctx.qualifiedName()), dynamicAction.tagUnset());
                } else {
                    return ddlHandler.handleAlterStatement(ctx);
                }
            } else if (ctx.CORTEX() != null) {
                final String serviceName = visitor.getText(ctx.qualifiedName());
                // IF EXISTS forgives only the service's absence, as it does for a compute pool.
                branchHandlesIfExists = true;
                if (ifExists && !cortexSearchServiceExists(serviceName)) {
                    logger.debug("Cortex search service does not exist (IF EXISTS): {}", serviceName);
                } else {
                    applyCortexSearchAction(catalog.resolveCortexSearchService(serviceName),
                        ctx.cortexSearchAction());
                }
            } else if (ctx.COMPUTE() != null && ctx.POOL() != null) {
                final String poolName = visitor.getText(ctx.identifier());
                // IF EXISTS forgives only the pool's absence; an error the action itself raises
                // still propagates, so this branch gates existence itself.
                branchHandlesIfExists = true;
                if (ifExists && !catalog.hasComputePool(poolName)) {
                    logger.debug("Compute pool does not exist (IF EXISTS): {}", poolName);
                } else {
                    final ComputePool pool = catalog.getComputePool(poolName);
                    final FrostlakeParser.ComputePoolActionContext action = ctx.computePoolAction();
                    if (action.SUSPEND() != null) {
                        // Idempotent: suspending a suspended pool succeeds.
                        pool.setState(ComputePoolState.SUSPENDED);
                        pool.touchUpdatedOn();
                    } else if (action.RESUME() != null) {
                        pool.setState(ComputePoolState.STARTING);
                        pool.setResumedOn(StatementClock.instant());
                        pool.touchUpdatedOn();
                    } else if (action.STOP() != null) {
                        // STOP ALL stops the pool's workloads; none run here, so the pool is
                        // untouched — but the OF TYPE list is validated exactly as a real account
                        // validates it.
                        validateStopAllWorkloadTypes(action.computePoolWorkloadType());
                        logger.trace("STOP ALL on compute pool {}", poolName);
                    } else if (action.tagSet() != null) {
                        applyTagSet(pool, action.tagSet());
                    } else if (action.tagUnset() != null) {
                        applyTagUnset(pool, action.tagUnset());
                    } else if (action.SET() != null) {
                        applyComputePoolSetOptions(pool, action.computePoolSetOption());
                    } else if (action.UNSET() != null) {
                        for (final FrostlakeParser.ComputePoolUnsetKeyContext key
                                : action.computePoolUnsetKey()) {
                            if (key.COMMENT() != null) {
                                pool.setComment(null);
                            } else if (key.AUTO_SUSPEND_SECS() != null) {
                                // UNSET restores the default, live-verified.
                                pool.setAutoSuspendSecs(ComputePool.DEFAULT_AUTO_SUSPEND_SECS);
                            } else if (key.AUTO_RESUME() != null) {
                                pool.setAutoResume(true);
                            } else if (key.PLACEMENT_GROUP() != null) {
                                // The message says "set" even for UNSET, live-verified.
                                requireSuspendedForPlacementGroup(pool);
                                pool.setPlacementGroup(null);
                            } else if (key.BACKUP_INSTANCE_FAMILIES() != null) {
                                pool.setBackupInstanceFamilies(new ArrayList<>());
                            }
                        }
                        pool.touchUpdatedOn();
                    }
                }

            } else if (ctx.ICEBERG() != null && ctx.icebergTableAction() != null) {
                final String[] icebergParts = queryExecutor.alterTargetNameParts(ctx);
                if (!ifExists || alterTargetTableExists(icebergParts)) {
                    IcebergTables.alter(catalog.resolveTable(queryExecutor.alterTargetName(ctx)),
                        ctx.icebergTableAction());
                }
            } else if (ctx.TABLE() != null) {
                final String tableName = queryExecutor.alterTargetName(ctx);

                // This branch decides IF EXISTS for itself (the pre-gate below), so the outer
                // catch's blanket forgiveness must not apply: a missing SCHEMA and any error the
                // action itself raises both propagate even under IF EXISTS, live-verified.
                branchHandlesIfExists = true;
                final String[] tableParts = queryExecutor.alterTargetNameParts(ctx);
                if (ifExists && !alterTargetTableExists(tableParts)) {
                    // IF EXISTS forgives ONLY the named table's absence, live-verified: a missing
                    // SCHEMA still errors (alterTargetTableExists resolves it first), and an error
                    // the action itself raises — an unknown column, a bad type change — propagates.
                    // A CLUSTER BY action's expressions are still compiled, and with no table no
                    // column reference can resolve (live-verified, positioned at the reference).
                    if (ctx.tableAction() != null && ctx.tableAction().CLUSTER() != null) {
                        ClusterKeyValidation.requireResolvable(
                            ctx.tableAction().expressionList().expression(), null);
                    }
                    logger.debug("Table does not exist (IF EXISTS): {}", tableName);
                } else {
                    final Table table = catalog.resolveTable(QualifiedName.of(tableParts));
                    checkAlter(SecurableObjectType.TABLE, tableName);

                    if (ctx.tableAction().SWAP() != null) {
                        // ALTER TABLE a SWAP WITH b — exchange the two tables' row storage, which needs
                        // OWNERSHIP of both.
                        queryExecutor.requireOwnership("TABLE", tableParts, null);
                        queryExecutor.requireOwnership("TABLE", qualifiedNameParts(ctx.tableAction().qualifiedName()),
                            null);
                        final String other = visitor.getText(ctx.tableAction().qualifiedName());
                        queryExecutor.swapTables(tableName, other);
                        logger.trace("Swapped table {} with {}", tableName, other);
                    } else if (ctx.tableAction().RENAME() != null && ctx.tableAction().COLUMN() == null
                            && ctx.tableAction().CONSTRAINT() == null) {
                        // RENAME table. A qualified target that names a different schema/database MOVES the
                        // table there (Snowflake semantics); otherwise it is renamed in place. Both the
                        // catalog entry and the row storage are re-keyed so the new location is queryable.
                        queryExecutor.requireOwnership("TABLE", tableParts, null);
                        final String[] targetParts =
                            queryExecutor.resolveWholeObjectNameParts(ctx.tableAction().wholeObjectName());
                        if (targetParts.length < 3 && catalog.getCurrentDatabase() == null) {
                            // A new name that does not place itself is created in the session's schema, and
                            // a session with no current database has none (live-verified).
                            throw NoCurrentDatabaseRefusal.naming("CREATE TABLE");
                        }
                        final String[] srcParts = QualifiedName.parse(
                            queryExecutor.getFullyQualifiedTableName(tableName)).parts();
                        final String srcDb = srcParts[0];
                        final String srcSchema = srcParts[1];
                        final String targetDb;
                        final String targetSchema;
                        final String newName;
                        if (targetParts.length == 3) {
                            targetDb = targetParts[0];
                            targetSchema = targetParts[1];
                            newName = targetParts[2];
                        } else if (targetParts.length == 2) {
                            // The new name resolves as any created name does: in the SESSION's database,
                            // so a two-part one names a schema there and an unqualified one the session's
                            // own schema — the table MOVES there (live-verified).
                            targetDb = catalog.getCurrentDatabase();
                            targetSchema = targetParts[0];
                            newName = targetParts[1];
                        } else {
                            targetDb = catalog.getCurrentDatabase();
                            targetSchema = catalog.getCurrentSchema();
                            newName = targetParts[0];
                        }
                        if (targetDb.equalsIgnoreCase(srcDb) && targetSchema.equalsIgnoreCase(srcSchema)) {
                            // Catalog first: its taken-name check throws before anything mutates.
                            catalog.renameTable(tableName, newName);
                            queryExecutor.renameTableStorage(tableName, newName);
                            // Renaming a temporary table away uncovers the permanent table it hid.
                            TableShadows.settle(catalog.getDatabase(srcDb).getSchema(srcSchema),
                                queryExecutor.getStorageEngine(), srcDb, srcParts[2]);
                            logger.trace("Renamed table {} to {}", tableName, newName);
                        } else {
                            // Validate + move the catalog entry first (throws cleanly if the destination is
                            // missing or the name is taken), then re-key storage, so a rejected move mutates
                            // nothing.
                            catalog.moveTable(tableName, targetDb, targetSchema, newName);
                            queryExecutor.moveTableStorage(tableName, targetDb, targetSchema, newName);
                            TableShadows.settle(catalog.getDatabase(srcDb).getSchema(srcSchema),
                                queryExecutor.getStorageEngine(), srcDb, srcParts[2]);
                            logger.trace("Moved table {} to {}.{}.{}", tableName, targetDb, targetSchema, newName);
                        }
                    } else if (ctx.tableAction().ADD() != null && ctx.tableAction().columnDef() != null) {
                        // ADD COLUMN — one or more comma-separated columns (Snowflake: ADD col1 t1, col2 t2).
                        // The first column sits directly on the action; the rest arrive as
                        // alterAddColumnItem entries, each with its OWN optional IF NOT EXISTS. Build each
                        // column via the shared parser, then backfill existing rows to the new width.
                        final boolean firstIfNotExists = ctx.tableAction().if_not_exists() != null;
                        final List<FrostlakeParser.ColumnDefContext> defs = new ArrayList<>();
                        final List<Boolean> defIfNotExists = new ArrayList<>();
                        defs.add(ctx.tableAction().columnDef());
                        defIfNotExists.add(firstIfNotExists);
                        for (final FrostlakeParser.AlterAddColumnItemContext item : ctx.tableAction().alterAddColumnItem()) {
                            defs.add(item.columnDef());
                            defIfNotExists.add(firstIfNotExists || item.if_not_exists() != null);
                        }
                        for (int i = 0; i < defs.size(); i++) {
                            final FrostlakeParser.ColumnDefContext colDef = defs.get(i);
                            final String colName = ParseTreeText.namePartText(colDef.columnDefName());
                            // A column of EXACTLY this name exists or not: "n" beside an unquoted N is a new
                            // column, live-verified.
                            if (defIfNotExists.get(i) && table.hasColumnExactly(colName)) {
                                logger.debug("Column already exists (IF NOT EXISTS): {}", colName);
                                continue;
                            }
                            if (table.hasColumnExactly(colName)) {
                                // The account's own shape — lowercase 'column', no period, and the name as
                                // canonical, unquoted: 'N' for N or "N", 'n' for "n".
                                throw new RuntimeException(SqlCompilationError.of(
                                    "column '" + colName + "' already exists"));
                            }
                            final TableColumn newColumn =
                                ddlHandler.getColumnParser().parseSingleColumnDef(colDef, true);
                            table.addColumn(newColumn);
                            queryExecutor.backfillColumn(queryExecutor.getFullyQualifiedTableName(tableName),
                                table, newColumn);
                            logger.trace("Added column {} to table {}", colName, tableName);
                        }
                    } else if (ctx.tableAction().DROP() != null && ctx.tableAction().ALTER() == null
                            && ctx.tableAction().CONSTRAINT() == null
                            && ctx.tableAction().CLUSTERING() == null
                            && ctx.tableAction().PRIMARY() == null
                            && ctx.tableAction().UNIQUE() == null
                            && ctx.tableAction().FOREIGN() == null
                            && ctx.tableAction().ROW() == null
                            && !ctx.tableAction().identifier().isEmpty()) {
                        // DROP [COLUMN] — the COLUMN keyword is optional (live-verified), so this branch is
                        // identified by what the alternative PRODUCES: a DROP that names identifiers and is
                        // none of the other DROP forms. ALTER COLUMN … DROP NOT NULL / DROP DEFAULT carry a
                        // DROP token too, hence the ALTER guard.
                        dropColumns(tableName, table, ctx.tableAction());
                    } else if (ctx.tableAction().DROP() != null && ctx.tableAction().CONSTRAINT() != null) {
                        // DROP CONSTRAINT — a check is dropped by name like any other constraint, and a
                        // name no constraint carries is refused rather than ignored (live-verified).
                        final String constraintName = visitor.getText(ctx.tableAction().identifier(0));
                        if (!table.dropCheckConstraint(constraintName)) {
                            if (table.constraintColumns(constraintName).isEmpty()) {
                                throw new RuntimeException(SqlCompilationError.of("constraint '"
                                    + constraintName.toUpperCase(Locale.ROOT) + "' does not exist"));
                            }
                            table.dropForeignKey(constraintName);
                        }
                        logger.trace("Dropped constraint {} from table {}", constraintName, tableName);
                    } else if (ctx.tableAction().DROP() != null && ctx.tableAction().CLUSTERING() != null) {
                        // DROP CLUSTERING KEY
                        table.setClusterKeys(null);
                        logger.trace("Dropped clustering key from table {}", tableName);
                    } else if (ctx.tableAction().DROP() != null && ctx.tableAction().PRIMARY() != null) {
                        // DROP PRIMARY KEY
                        table.dropPrimaryKey();
                        logger.trace("Dropped PRIMARY KEY from table {}", tableName);
                    } else if (ctx.tableAction().DROP() != null && ctx.tableAction().UNIQUE() != null) {
                        // DROP UNIQUE (cols)
                        final List<String> columns = new ArrayList<>();
                        for (final FrostlakeParser.IdentifierContext idCtx : ctx.tableAction().identifierList().identifier()) {
                            columns.add(visitor.getText(idCtx));
                        }
                        table.dropUnique(columns);
                        logger.trace("Dropped UNIQUE {} from table {}", columns, tableName);
                    } else if (ctx.tableAction().DROP() != null && ctx.tableAction().FOREIGN() != null) {
                        // DROP FOREIGN KEY (cols)
                        final List<String> columns = new ArrayList<>();
                        for (final FrostlakeParser.IdentifierContext idCtx : ctx.tableAction().identifierList().identifier()) {
                            columns.add(visitor.getText(idCtx));
                        }
                        table.dropForeignKeyColumns(columns);
                        logger.trace("Dropped FOREIGN KEY {} from table {}", columns, tableName);
                    } else if (ctx.tableAction().CONTACT() != null
                            && !ctx.tableAction().contactAssignment().isEmpty()) {
                        // SET CONTACT purpose = contact, … — no parentheses, live refuses those.
                        for (final FrostlakeParser.ContactAssignmentContext assignment
                                : ctx.tableAction().contactAssignment()) {
                            final String purpose = contactPurpose(assignment.contactPurpose());
                            final String contactName = visitor.getText(assignment.qualifiedName());
                            if (catalog.findContact(contactName) == null) {
                                throw new RuntimeException(SqlCompilationError.doesNotExist("Contact",
                                    catalog.qualifiedObjectName(contactName)));
                            }
                            table.setContact(purpose, catalog.qualifiedObjectName(contactName));
                        }
                        logger.trace("Set contact(s) on table {}", tableName);
                    } else if (ctx.tableAction().CONTACT() != null) {
                        // UNSET CONTACT purpose, … — detaching a purpose nothing was attached for is
                        // no error; only the purpose itself is validated.
                        for (final FrostlakeParser.ContactPurposeContext purposeCtx
                                : ctx.tableAction().contactPurpose()) {
                            table.unsetContact(contactPurpose(purposeCtx));
                        }
                        logger.trace("Unset contact(s) on table {}", tableName);
                    } else if (ctx.tableAction().METRIC() != null) {
                        // ADD / DROP / MODIFY DATA METRIC FUNCTION <fn> ON (cols) — the attachment is
                        // recorded and the metric is never evaluated (see docs/scope.md).
                        applyDataMetricAction(table, ctx.tableAction());
                    } else if (ctx.tableAction().SEARCH() != null
                            && ctx.tableAction().ADD() != null) {
                        // ADD SEARCH OPTIMIZATION [ON method(target), …] — metadata only: the engine
                        // records the configuration and reports it, and nothing consults it.
                        if (ctx.tableAction().searchOptimizationTarget().isEmpty()) {
                            addDefaultSearchOptimization(table);
                        } else {
                            for (final FrostlakeParser.SearchOptimizationTargetContext target
                                    : ctx.tableAction().searchOptimizationTarget()) {
                                addSearchOptimization(table, target);
                            }
                        }
                        logger.trace("Added search optimization on table {}", tableName);
                    } else if (ctx.tableAction().SEARCH() != null) {
                        // DROP SEARCH OPTIMIZATION [ON …] — the bare form drops the lot, silently
                        // even when there was none.
                        if (ctx.tableAction().searchOptimizationDrop().isEmpty()) {
                            table.clearSearchOptimization();
                        } else {
                            for (final FrostlakeParser.SearchOptimizationDropContext dropped
                                    : ctx.tableAction().searchOptimizationDrop()) {
                                dropSearchOptimization(table, dropped);
                            }
                        }
                        logger.trace("Dropped search optimization on table {}", tableName);
                    } else if (ctx.tableAction().RECLUSTER() != null) {
                        // SUSPEND / RESUME RECLUSTER — only a CLUSTERED table has reclustering to pause,
                        // and live refuses the rest with its own sentence (no compilation-error prefix).
                        if (table.getClusterKeys().isEmpty()) {
                            throw new RuntimeException("Table '" + table.getName().toUpperCase(Locale.ROOT)
                                + "' is not clustered\n");
                        }
                        table.setReclusterSuspended(ctx.tableAction().SUSPEND() != null);
                        logger.trace("{} recluster on table {}",
                            ctx.tableAction().SUSPEND() != null ? "Suspended" : "Resumed", tableName);
                    } else if (ctx.tableAction().RENAME() != null && ctx.tableAction().CONSTRAINT() != null) {
                        // RENAME CONSTRAINT old TO new — the name may belong to a UNIQUE, the primary
                        // key or a FOREIGN KEY, so the table searches all of them.
                        final String oldName = visitor.getText(ctx.tableAction().identifier(0));
                        final String newName = visitor.getText(ctx.tableAction().identifier(1));
                        requireConstraintRenamed(table, oldName, newName);
                        logger.trace("Renamed constraint {} to {} on table {}", oldName, newName, tableName);
                    } else if (ctx.tableAction().CONSTRAINT() != null
                            && !ctx.tableAction().constraintProperty().isEmpty()) {
                        // {ALTER | MODIFY} CONSTRAINT name RELY | NORELY | [NOT] ENFORCED, several at once.
                        applyConstraintProperties(table, visitor.getText(ctx.tableAction().identifier(0)),
                            ctx.tableAction().constraintProperty());
                    } else if (ctx.tableAction().RENAME() != null && ctx.tableAction().COLUMN() != null) {
                        // RENAME COLUMN
                        final String oldName = visitor.getText(ctx.tableAction().identifier(0));
                        final String newName = visitor.getText(ctx.tableAction().identifier(1));
                        table.renameColumn(oldName, newName);
                        logger.trace("Renamed column {} to {} in table {}", oldName, newName, tableName);
                    } else if (ctx.tableAction().joinPolicyClause() != null) {
                        // SET JOIN POLICY p [FORCE] — one per table, guarded by the same 3549 sentence.
                        final String written = visitor.getText(
                            ctx.tableAction().joinPolicyClause().qualifiedName());
                        if (catalog.findJoinPolicy(written) == null) {
                            throw new RuntimeException(SqlCompilationError.doesNotExist(
                                "Join policy", catalog.qualifiedObjectName(written)));
                        }
                        final String policyName = catalog.qualifiedObjectName(written);
                        if (table.hasJoinPolicy()
                                && !sameMaskingPolicy(table.getJoinPolicyName(), policyName)
                                && ctx.tableAction().FORCE() == null) {
                            throw new RuntimeException("Object " + table.getName().toUpperCase(Locale.ROOT)
                                + " already has a JOIN_POLICY. Only one JOIN_POLICY is allowed"
                                + " at a time.");
                        }
                        table.setJoinPolicyName(policyName);
                        logger.trace("Set join policy on table {}", tableName);
                    } else if (ctx.tableAction().UNSET() != null && ctx.tableAction().JOIN() != null) {
                        if (!table.hasJoinPolicy()) {
                            throw new RuntimeException("Any policy of kind JOIN_POLICY is not attached"
                                + " to TABLE " + table.getName().toUpperCase(Locale.ROOT) + ".");
                        }
                        table.setJoinPolicyName(null);
                        logger.trace("Unset join policy on table {}", tableName);
                    } else if (ctx.tableAction().aggregationPolicyClause() != null) {
                        // SET AGGREGATION POLICY p [ENTITY KEY (…)] [FORCE] — one per table, and the
                        // same 3549 sentence the row access policy uses guards a second one.
                        final FrostlakeParser.AggregationPolicyClauseContext clause =
                            ctx.tableAction().aggregationPolicyClause();
                        final String written = visitor.getText(clause.qualifiedName());
                        if (catalog.findAggregationPolicy(written) == null) {
                            throw new RuntimeException(SqlCompilationError.doesNotExist(
                                "Aggregation policy", catalog.qualifiedObjectName(written)));
                        }
                        final String policyName = catalog.qualifiedObjectName(written);
                        if (table.hasAggregationPolicy()
                                && !sameMaskingPolicy(table.getAggregationPolicyName(), policyName)
                                && ctx.tableAction().FORCE() == null) {
                            throw new RuntimeException("Object " + table.getName().toUpperCase(Locale.ROOT)
                                + " already has a AGGREGATION_POLICY. Only one AGGREGATION_POLICY is"
                                + " allowed at a time.");
                        }
                        final List<String> entityKey = new ArrayList<>();
                        if (clause.identifierList() != null) {
                            for (final FrostlakeParser.IdentifierContext id
                                    : clause.identifierList().identifier()) {
                                requireColumn(table, visitor.getText(id), id);
                                entityKey.add(visitor.getText(id));
                            }
                        }
                        table.setAggregationPolicyName(policyName);
                        table.setAggregationEntityKey(entityKey);
                        logger.trace("Set aggregation policy on table {}", tableName);
                    } else if (ctx.tableAction().UNSET() != null && ctx.tableAction().AGGREGATION() != null) {
                        // Detaching when nothing is attached is an error, unlike the projection form.
                        if (!table.hasAggregationPolicy()) {
                            throw new RuntimeException("Any policy of kind AGGREGATION_POLICY is not"
                                + " attached to TABLE " + table.getName().toUpperCase(Locale.ROOT) + ".");
                        }
                        table.setAggregationPolicyName(null);
                        table.setAggregationEntityKey(new ArrayList<>());
                        logger.trace("Unset aggregation policy on table {}", tableName);
                    } else if (ctx.tableAction().ALTER() != null && ctx.tableAction().PROJECTION() != null) {
                        // ALTER COLUMN col SET/UNSET PROJECTION POLICY — one per column, and FORCE is
                        // what lets a different one replace it (live-verified, same rule as masking).
                        final String colName = visitor.getText(ctx.tableAction().identifier(0));
                        requireColumn(table, colName, ctx.tableAction().identifier(0));
                        final TableColumn col = table.getColumn(colName);
                        if (ctx.tableAction().UNSET() != null) {
                            col.setProjectionPolicyName(null);
                        } else {
                            final String written = visitor.getText(ctx.tableAction().qualifiedName());
                            if (catalog.findProjectionPolicy(written) == null) {
                                throw new RuntimeException(SqlCompilationError.doesNotExist(
                                    "Projection policy", catalog.qualifiedObjectName(written)));
                            }
                            final String policyName = catalog.qualifiedObjectName(written);
                            if (col.hasProjectionPolicy()
                                    && !sameMaskingPolicy(col.getProjectionPolicyName(), policyName)
                                    && ctx.tableAction().FORCE() == null) {
                                // Live's wording, missing spaces and all.
                                throw new RuntimeException("Specified column already attached to another"
                                    + " 'PROJECTION_POLICY'.A column cannot be attached to multiple"
                                    + " policies of same kind.please drop the current association in"
                                    + " order to attach a new policy.");
                            }
                            col.setProjectionPolicyName(policyName);
                        }
                        logger.trace("Set/unset projection policy on column {}.{}", tableName, colName);
                    } else if ((ctx.tableAction().ALTER() != null || ctx.tableAction().MODIFY() != null)
                            && ctx.tableAction().MASKING() != null) {
                        // {ALTER | MODIFY} COLUMN col SET/UNSET MASKING POLICY — one action, both
                        // spellings (live-verified: the MODIFY form runs and refuses identically).
                        final String colName = visitor.getText(ctx.tableAction().identifier(0));
                        requireColumn(table, colName, ctx.tableAction().identifier(0));
                        final TableColumn col = table.getColumn(colName);
                        if (ctx.tableAction().UNSET() != null) {
                            // Detaching what is not attached is a no-op, not an error (live-verified) —
                            // the opposite of the row access policy's DROP.
                            col.setMaskingPolicyName(null);
                        } else {
                            // Recorded in full, the way live reports an attachment however little of
                            // the name the statement wrote — and so that re-attaching the SAME policy
                            // under a different spelling stays the no-op live makes it.
                            final String policyName = catalog.qualifiedObjectName(
                                requireMaskingPolicy(visitor.getText(ctx.tableAction().qualifiedName())));
                            if (col.getMaskingPolicyName() != null
                                    && !sameMaskingPolicy(col.getMaskingPolicyName(), policyName)
                                    && ctx.tableAction().FORCE() == null) {
                                // FORCE replaces the attached policy instead of refusing (live-verified).
                                // Snowflake: one masking policy per column — UNSET the current one first.
                                // Re-attaching the SAME policy is a no-op, not an error: live-verified on
                                // a real account, repeating SET MASKING POLICY mp1 succeeds
                                // while switching to a different policy fails "Specified column already
                                // attached to another masking policy...".
                                throw new RuntimeException("Specified column already attached to another"
                                    + " masking policy. A column cannot be attached to multiple masking"
                                    + " policies. Please drop the current association in order to attach a"
                                    + " new masking policy.");
                            }
                            if (ctx.tableAction().identifierList() != null) {
                                // Conditional policy: a USING argument column that is itself masked is
                                // rejected (live-verified error shape).
                                for (final FrostlakeParser.IdentifierContext argCtx
                                        : ctx.tableAction().identifierList().identifier()) {
                                    final String argName = visitor.getText(argCtx);
                                    final TableColumn argCol = table.hasColumn(argName) ? table.getColumn(argName) : null;
                                    if (argCol != null && argCol != col && argCol.getMaskingPolicyName() != null) {
                                        throw new RuntimeException("Column '" + argName.toUpperCase()
                                            + "' cannot be used as policy argument because it is masked by another policy");
                                    }
                                }
                            }
                            MaskingPolicyAttachment.requireTypeMatch(
                                catalog.findMaskingPolicy(policyName), col);
                            col.setMaskingPolicyName(policyName);
                        }
                        logger.trace("Set/unset masking policy on column {}.{}", tableName, colName);
                    } else if (!ctx.tableAction().alterColumnItem().isEmpty()) {
                        // ALTER/MODIFY [(] [COLUMN] c1 action [, [COLUMN] c2 action]* [)]
                        applyAlterColumnItems(table, tableName, ctx.tableAction().alterColumnItem());
                    } else if (ctx.tableAction().COMMENT() != null) {
                        final String comment = visitor.extractStringLiteral(ctx.tableAction().STRING_LITERAL());
                        table.setComment(comment);
                        logger.trace("Set comment on table: {}", tableName);
                    } else if (ctx.tableAction().CLUSTER() != null) {
                        // CLUSTER BY — every referenced column must exist (live-verified,
                        // positioned refusal).
                        ClusterKeyValidation.requireResolvable(
                            ctx.tableAction().expressionList().expression(), table);
                        final List<String> clusterKeys = new ArrayList<>();
                        for (final FrostlakeParser.ExpressionContext exprCtx : ctx.tableAction().expressionList().expression()) {
                            clusterKeys.add(exprCtx.getText());
                        }
                        table.setClusterKeys(clusterKeys);
                        logger.trace("Set cluster keys on table {}: {}", tableName, clusterKeys);
                    } else if (ctx.tableAction().tableConstraint() != null) {
                        // ADD constraint
                        final FrostlakeParser.TableConstraintContext constraintCtx = ctx.tableAction().tableConstraint();
                        String constraintName = null;
                        if (constraintCtx.constraintName() != null) {
                            constraintName = visitor.getText(constraintCtx.constraintName().identifier());
                        }

                        if (constraintCtx.checkConstraint() != null) {
                            // A check added to a table that already holds rows would have to validate
                            // them, and live does not do that: it insists the statement say so with
                            // ENABLE NOVALIDATE and refuses every other spelling in one sentence.
                            final FrostlakeParser.CheckConstraintContext check = constraintCtx.checkConstraint();
                            if (check.checkEnforcement() == null || check.checkEnforcement().ENABLE() == null) {
                                throw new RuntimeException("ALTER TABLE ADD CHECK is not supported with"
                                    + " ENABLE VALIDATE (you may specify ENABLE NOVALIDATE instead).");
                            }
                            final List<String> checkColumns = new ArrayList<>();
                            for (final TableColumn column : table.getColumns()) {
                                checkColumns.add(column.getName());
                            }
                            table.addCheckConstraint(visitor.getColumnParser().buildCheckConstraint(
                                check.constraintName(), check.booleanExpr(), checkColumns));
                            logger.trace("Added check constraint to table {}", tableName);
                        } else if (constraintCtx.PRIMARY() != null) {
                            // PRIMARY KEY constraint - add to table's primary keys list. A second
                            // key refuses with the account's own sentence, bare table name.
                            if (!table.getPrimaryKeys().isEmpty()) {
                                throw new RuntimeException(SqlCompilationError.of(
                                    "primary key already exists for table '"
                                    + table.getName().toUpperCase() + "'"));
                            }
                            final List<String> columns = new ArrayList<>();
                            for (final FrostlakeParser.IdentifierContext idCtx : constraintCtx.identifierList(0).identifier()) {
                                requireColumn(table, visitor.getText(idCtx), idCtx);
                                columns.add(visitor.getText(idCtx));
                            }
                            table.addPrimaryKeyConstraint(columns);
                            // An explicit CONSTRAINT <name> names the key; without one it auto-names itself.
                            table.setPrimaryKeyConstraintName(constraintName);
                            logger.trace("Added PRIMARY KEY constraint to table {}: {}", tableName, columns);
                        } else if (constraintCtx.UNIQUE() != null) {
                            // UNIQUE constraint — one constraint over every listed column, not one each.
                            final List<String> columns = new ArrayList<>();
                            for (final FrostlakeParser.IdentifierContext idCtx : constraintCtx.identifierList(0).identifier()) {
                                requireColumn(table, visitor.getText(idCtx), idCtx);
                                columns.add(visitor.getText(idCtx));
                            }
                            table.addUniqueConstraint(constraintName, columns);
                            logger.trace("Added UNIQUE constraint to table {}: {}", tableName, columns);
                        } else if (constraintCtx.FOREIGN() != null) {
                            // FOREIGN KEY constraint
                            final List<String> columns = new ArrayList<>();
                            for (final FrostlakeParser.IdentifierContext idCtx : constraintCtx.identifierList(0).identifier()) {
                                columns.add(visitor.getText(idCtx));
                            }
                            final String referencedTable = visitor.getText(constraintCtx.qualifiedName());
                            final List<String> referencedColumns = new ArrayList<>();
                            for (final FrostlakeParser.IdentifierContext idCtx : constraintCtx.identifierList(1).identifier()) {
                                referencedColumns.add(visitor.getText(idCtx));
                            }

                            String onDelete = null;
                            String onUpdate = null;
                            if (constraintCtx.referentialActions() != null) {
                                final String[] actions = ColumnDefinitionParser.parseReferentialActions(
                                    constraintCtx.referentialActions());
                                onDelete = actions[0];
                                onUpdate = actions[1];
                            }

                            // Snowflake validates the constraint's local columns exist
                            // (live-verified: "invalid identifier '<COL>'").
                            for (final String fkColumn : columns) {
                                if (!table.hasColumn(fkColumn)) {
                                    throw new RuntimeException("invalid identifier '" + fkColumn.toUpperCase() + "'");
                                }
                            }
                            // A referential action other than NO ACTION drops the whole
                            // constraint, silently — exactly as on the CREATE TABLE paths.
                            if (!ColumnDefinitionParser.dropsForeignKey(onDelete, onUpdate)) {
                                // A nameless constraint is auto-named SYS_CONSTRAINT_<uuid> by the constructor.
                                final ForeignKeyConstraint fk = new ForeignKeyConstraint(
                                    constraintName,
                                    columns,
                                    referencedTable,
                                    referencedColumns,
                                    onDelete,
                                    onUpdate
                                );
                                table.addForeignKey(fk);
                                logger.trace("Added FOREIGN KEY constraint to table {}: {} -> {}", tableName, columns, referencedTable);
                            }
                        }
                    } else if (ctx.tableAction().ROW() != null && ctx.tableAction().ADD() != null) {
                        // ADD ROW ACCESS POLICY policyName ON (col1, col2)
                        final String policyName = visitor.getText(ctx.tableAction().qualifiedName());
                        final List<String> cols = rowAccessPolicyColumns(ctx.tableAction().identifierList(),
                            table, table.getName(), policyName, table.hasRowAccessPolicy());
                        table.setRowAccessPolicyName(policyName.toUpperCase());
                        table.setRowAccessPolicyColumns(cols);
                        logger.trace("Added row access policy {} to table {}", policyName, tableName);
                    } else if (ctx.tableAction().ROW() != null && ctx.tableAction().DROP() != null) {
                        // DROP ROW ACCESS POLICY p — or DROP ALL ROW ACCESS POLICIES, which names none
                        // and detaches whatever is there, silently when there is nothing.
                        if (ctx.tableAction().ALL() == null) {
                            requireRowAccessPolicyAttached(
                                visitor.getText(ctx.tableAction().qualifiedName()),
                                table.getName(), table.getRowAccessPolicyName());
                        }
                        table.setRowAccessPolicyName(null);
                        table.setRowAccessPolicyColumns(new ArrayList<>());
                        logger.trace("Dropped row access policy from table {}", tableName);
                    } else if (ctx.tableAction().columnTagAction() != null) {
                        final FrostlakeParser.ColumnTagActionContext cta = ctx.tableAction().columnTagAction();
                        final String colName = visitor.getText(cta.identifier());
                        final TableColumn col = table.getColumn(colName);
                        if (col == null) {
                            throw new RuntimeException(SqlCompilationError.invalidIdentifier(colName));
                        }
                        if (cta.tagSet() != null) {
                            applyTagSet(col, cta.tagSet());
                        } else {
                            applyTagUnset(col, cta.tagUnset());
                        }
                        logger.trace("Applied column tag action on {}.{}", tableName, colName);
                    } else if (ctx.tableAction().tagSet() != null) {
                        applyTagSet(table, ctx.tableAction().tagSet());
                        logger.trace("Set tag(s) on table {}", tableName);
                    } else if (ctx.tableAction().tagUnset() != null) {
                        applyTagUnset(table, ctx.tableAction().tagUnset());
                        logger.trace("Unset tag(s) on table {}", tableName);
                    } else if (ctx.tableAction().tableUnsetProperties() != null) {
                        // UNSET DATA_RETENTION_TIME_IN_DAYS, COMMENT, ... — each modeled property goes back to
                        // its default, the COMMENT to none; the rest are accepted and inert.
                        for (final FrostlakeParser.OptionKeyContext key
                                : ctx.tableAction().tableUnsetProperties().optionKey()) {
                            if ("CHANGE_TRACKING".equalsIgnoreCase(key.getText())) {
                                table.setChangeTracking(false);
                            } else if ("DATA_RETENTION_TIME_IN_DAYS".equalsIgnoreCase(key.getText())) {
                                // Back to INHERITING the container's value, like the schema's UNSET.
                                table.setDataRetentionTimeInDays(null);
                            } else if ("ENABLE_SCHEMA_EVOLUTION".equalsIgnoreCase(key.getText())) {
                                table.setSchemaEvolution(false);
                            } else if ("DATA_METRIC_SCHEDULE".equalsIgnoreCase(key.getText())) {
                                table.setDataMetricSchedule(null);
                            } else if ("COMMENT".equalsIgnoreCase(key.getText())) {
                                table.setComment(null);
                            }
                        }
                        logger.trace("Unset table properties on {}", tableName);
                    } else if (ctx.tableAction().SET() != null
                            && !ctx.tableAction().tableSetProperty().isEmpty()) {
                        // One SET may carry several properties; each is validated and applied in turn.
                        // A property repeated here is NOT refused — live accepts it on ALTER even
                        // though CREATE TABLE calls the same repeat a duplicate property.
                        for (final FrostlakeParser.TableSetPropertyContext property
                                : ctx.tableAction().tableSetProperty()) {
                            final String rawKey = property.optionKey().getText();
                            requireLegalRetention(rawKey, property.copyOptionValue());
                            requireAccountRetention(rawKey, property.copyOptionValue());
                            requireLegalExtensionTime(rawKey, property.copyOptionValue());
                            requireLegalErrorLogging(rawKey, property.copyOptionValue());
                            if ("DATA_RETENTION_TIME_IN_DAYS".equalsIgnoreCase(rawKey)
                                    && property.copyOptionValue() != null
                                    && property.copyOptionValue().getText().matches("[0-9]+")) {
                                table.setDataRetentionTimeInDays(
                                    Integer.valueOf(property.copyOptionValue().getText()));
                            }
                            if ("CHANGE_TRACKING".equalsIgnoreCase(rawKey)) {
                                // The one modeled table property: the CHANGES clause requires it.
                                final FrostlakeParser.CopyOptionValueContext value = property.copyOptionValue();
                                table.setChangeTracking(value != null
                                    && "TRUE".equalsIgnoreCase(value.getText()));
                                logger.trace("Set CHANGE_TRACKING on table {}", tableName);
                            } else if ("DATA_METRIC_SCHEDULE".equalsIgnoreCase(rawKey)) {
                                // Recorded so DATA_METRIC_FUNCTION_REFERENCES can render it as cron.
                                final FrostlakeParser.CopyOptionValueContext value = property.copyOptionValue();
                                table.setDataMetricSchedule(value == null ? null
                                    : SqlStringLiterals.decode(value.getText()));
                                logger.trace("Set DATA_METRIC_SCHEDULE on table {}", tableName);
                            } else if ("ENABLE_SCHEMA_EVOLUTION".equalsIgnoreCase(rawKey)) {
                                // Recorded, not merely accepted: SHOW TABLES answers Y once it is on.
                                final FrostlakeParser.CopyOptionValueContext value = property.copyOptionValue();
                                table.setSchemaEvolution(value != null
                                    && "TRUE".equalsIgnoreCase(value.getText()));
                                logger.trace("Set ENABLE_SCHEMA_EVOLUTION on table {}", tableName);
                            } else if ("COMMENT".equalsIgnoreCase(rawKey)) {
                                // COMMENT has its own single-property alternative, but inside a
                                // MULTI-property SET it arrives here as an ordinary key — and it
                                // still has to reach the table rather than merely be accepted.
                                final FrostlakeParser.CopyOptionValueContext value = property.copyOptionValue();
                                table.setComment(value != null && value.STRING_LITERAL() != null
                                    ? visitor.extractStringLiteral(value.STRING_LITERAL()) : null);
                                logger.trace("Set comment on table {}", tableName);
                            } else if (!ParameterRegistry.isTableParameter(ParameterRegistry.canonical(rawKey))) {
                                // A known parameter is accepted and inert; an unknown one refuses (live-verified).
                                throw ParameterRegistry.invalidProperty(ParameterRegistry.spell(rawKey), "TABLE");
                            }
                        }
                    }
                }

            } else if (ctx.VIEW() != null && ctx.MATERIALIZED() != null) {
                checkAlter(SecurableObjectType.MATERIALIZED_VIEW, queryExecutor.alterTargetName(ctx));
                return ddlHandler.handleAlterStatement(ctx);

            } else if (ctx.SEQUENCE() != null) {
                // IF EXISTS forgives only the sequence's absence: a taken new name still refuses (live-verified).
                branchHandlesIfExists = true;
                return ddlHandler.handleAlterStatement(ctx);

            } else if (ctx.VIEW() != null) {
                final String viewName = queryExecutor.alterTargetName(ctx);
                final View view = catalog.resolveView(QualifiedName.of(queryExecutor.alterTargetNameParts(ctx)));
                checkAlter(SecurableObjectType.VIEW, viewName);

                if (ctx.viewAction().RENAME() != null) {
                    // IF EXISTS forgave the view's absence above; a taken new name still refuses (live-verified).
                    branchHandlesIfExists = true;
                    queryExecutor.requireOwnership("VIEW", queryExecutor.alterTargetNameParts(ctx), null);
                    final String[] target = qualifiedNameParts(ctx.viewAction().qualifiedName());
                    if (target.length < 3 && catalog.getCurrentDatabase() == null) {
                        // The new name is created where a created name would be, as a table's is.
                        throw NoCurrentDatabaseRefusal.naming("CREATE VIEW");
                    }
                    // The new name resolves as a created name does: an unqualified one in the session's schema, a
                    // two-part one in a schema of the session's database. A view read elsewhere MOVES.
                    catalog.moveView(viewName, target.length == 3 ? target[0] : catalog.getCurrentDatabase(),
                        target.length >= 2 ? target[target.length - 2] : catalog.getCurrentSchema(), target);
                    logger.trace("Renamed view {} to {}", viewName, String.join(".", target));
                } else if (ctx.viewAction().UNSET() != null) {
                    view.setComment(null);
                    logger.trace("Unset comment on view: {}", viewName);
                } else if (ctx.viewAction().COMMENT() != null) {
                    final String comment = visitor.extractStringLiteral(ctx.viewAction().STRING_LITERAL());
                    view.setComment(comment);
                    logger.trace("Set comment on view: {}", viewName);
                } else if (ctx.viewAction().ROW() != null && ctx.viewAction().ADD() != null) {
                    // ADD ROW ACCESS POLICY policyName ON (col1, col2)
                    final String policyName = visitor.getText(ctx.viewAction().qualifiedName());
                    final List<String> cols = rowAccessPolicyColumns(ctx.viewAction().identifierList(),
                        null, view.getName(), policyName, view.hasRowAccessPolicy());
                    view.setRowAccessPolicyName(policyName.toUpperCase());
                    view.setRowAccessPolicyColumns(cols);
                    logger.trace("Added row access policy {} to view {}", policyName, viewName);
                } else if (ctx.viewAction().ROW() != null && ctx.viewAction().DROP() != null) {
                    if (ctx.viewAction().ALL() == null) {
                        requireRowAccessPolicyAttached(visitor.getText(ctx.viewAction().qualifiedName()),
                            view.getName(), view.getRowAccessPolicyName());
                    }
                    view.setRowAccessPolicyName(null);
                    view.setRowAccessPolicyColumns(new ArrayList<>());
                    logger.trace("Dropped row access policy from view {}", viewName);
                } else if (ctx.viewAction().tagSet() != null) {
                    applyTagSet(view, ctx.viewAction().tagSet());
                } else if (ctx.viewAction().tagUnset() != null) {
                    applyTagUnset(view, ctx.viewAction().tagUnset());
                }

            } else if (ctx.STREAM() != null) {
                final String streamQn = visitor.getText(ctx.qualifiedName());
                final String[] streamParts = qualifiedNameParts(ctx.qualifiedName());
                final String streamName = streamParts[streamParts.length - 1].toUpperCase();
                final Schema schema;
                if (streamParts.length == 3) {
                    schema = catalog.getDatabase(streamParts[0]).getSchema(streamParts[1]);
                } else if (streamParts.length == 2) {
                    schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(streamParts[0]);
                } else {
                    schema = visitor.resolveCurrentSchema();
                }
                final Stream stream = schema.getStream(streamName);
                checkAlter(SecurableObjectType.STREAM, streamQn);

                final FrostlakeParser.StreamActionContext streamAction = ctx.streamAction();
                if (streamAction.tagSet() != null) {
                    applyTagSet(stream, streamAction.tagSet());
                } else if (streamAction.tagUnset() != null) {
                    applyTagUnset(stream, streamAction.tagUnset());
                } else if (streamAction.SET() != null && streamAction.COMMENT() != null) {
                    stream.setComment(visitor.extractStringLiteral(streamAction.STRING_LITERAL()));
                    logger.trace("Set comment on stream: {}", streamName);
                } else if (streamAction.UNSET() != null && streamAction.COMMENT() != null) {
                    stream.setComment(null);
                    logger.trace("Unset comment on stream: {}", streamName);
                }

            } else if (ctx.TASK() != null) {
                final String taskQn = visitor.getText(ctx.qualifiedName());
                final String[] taskParts = qualifiedNameParts(ctx.qualifiedName());
                final String taskName = taskParts[taskParts.length - 1];
                final Schema schema;
                if (taskParts.length == 3) {
                    schema = catalog.getDatabase(taskParts[0]).getSchema(taskParts[1]);
                } else if (taskParts.length == 2) {
                    schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(taskParts[0]);
                } else {
                    schema = visitor.resolveCurrentSchema();
                }
                final Task task = schema.getTask(taskName);
                checkAlter(SecurableObjectType.TASK, taskQn);

                // Canonical scheduler key (DB.SCHEMA.TASK), built identically for RESUME and SUSPEND so the
                // scheduler arms and later cancels the same entry.
                final String taskDbName = taskParts.length == 3 ? taskParts[0] : catalog.getCurrentDatabase();
                final String taskKey = TaskScheduler.schedulerKey(taskDbName, schema.getName(), taskName);

                if (ctx.taskAction().tagSet() != null) {
                    applyTagSet(task, ctx.taskAction().tagSet());
                } else if (ctx.taskAction().tagUnset() != null) {
                    applyTagUnset(task, ctx.taskAction().tagUnset());
                } else if (ctx.taskAction().RESUME() != null) {
                    // RESUME flips state to STARTED AND arms the scheduler so the task fires on its SCHEDULE
                    // (scheduleTask is a no-op until the scheduler is started). resumeTask sets the state.
                    if (visitor.getTaskScheduler() != null) {
                        visitor.getTaskScheduler().resumeTask(taskKey, task);
                    } else {
                        task.setState(TaskState.STARTED);
                    }
                    logger.trace("Resumed task: {}", taskName);
                } else if (ctx.taskAction().SUSPEND() != null) {
                    // SUSPEND flips state to SUSPENDED AND cancels any armed timer. suspendTask sets the state.
                    if (visitor.getTaskScheduler() != null) {
                        visitor.getTaskScheduler().suspendTask(taskKey, task);
                    } else {
                        task.setState(TaskState.SUSPENDED);
                    }
                    logger.trace("Suspended task: {}", taskName);
                } else if (ctx.taskAction().SET() != null && ctx.taskAction().taskExecuteAs() != null) {
                    final String runAs = TaskProperties.runAsUser(ctx.taskAction().taskExecuteAs(), queryExecutor);
                    new TaskProperties(catalog, schema).requireRunAsUser(task, runAs);
                    task.setExecuteAsUser(runAs);
                } else if (ctx.taskAction().UNSET() != null && ctx.taskAction().EXECUTE() != null) {
                    task.setExecuteAsUser(null);
                } else if (ctx.taskAction().SET() != null) {
                    final List<TaskPropertySetting> settings = alterTaskSettings(ctx.taskAction());
                    final TaskProperties properties = new TaskProperties(catalog, schema);
                    properties.compile(settings);
                    // The statement is tried on a copy first, so a refused one leaves the task as it was.
                    final Task scratch = TaskProperties.scratchCopy(task);
                    for (final Task target : Arrays.asList(scratch, task)) {
                        properties.apply(target, settings);
                        properties.requireConsistentOverlap();
                        properties.requireScheduleFits(target);
                        if (properties.finalizeGiven()) {
                            properties.requireFinalizer(target);
                        }
                        properties.requireRootSettings(target);
                        properties.requireServerlessSettings(target);
                    }
                    logger.trace("Set options on task: {}", taskName);
                } else if (ctx.taskAction().UNSET() != null) {
                    final List<String> unsetNames = new ArrayList<>();
                    for (final FrostlakeParser.TaskParamNameContext pn : ctx.taskAction().taskParamName()) {
                        unsetNames.add(pn.getText());
                    }
                    TaskProperties.rejectRepeats(unsetNames);
                    for (final FrostlakeParser.TaskParamNameContext pn : ctx.taskAction().taskParamName()) {
                        requireUnsettableTaskParam(pn.getText().toUpperCase());
                    }
                    for (final FrostlakeParser.TaskParamNameContext pn : ctx.taskAction().taskParamName()) {
                        unsetTaskParam(task, pn.getText().toUpperCase());
                    }
                    logger.trace("Unset params on task: {}", taskName);
                } else if (ctx.taskAction().REMOVE() != null && ctx.taskAction().WHEN() != null) {
                    task.setCondition(null);
                } else if (ctx.taskAction().ADD() != null) {
                    addTaskPredecessors(task, ctx.taskAction().qualifiedName(), schema);
                } else if (ctx.taskAction().REMOVE() != null) {
                    removeTaskPredecessors(task, ctx.taskAction().qualifiedName(), schema);
                } else if (ctx.taskAction().MODIFY() != null && ctx.taskAction().AS() != null) {
                    // MODIFY AS <sql> replaces the task body.
                    task.setSqlStatement(visitor.getOriginalText(ctx.taskAction().taskBody()).trim());
                    logger.trace("Modified task body: {}", taskName);
                } else if (ctx.taskAction().MODIFY() != null && ctx.taskAction().WHEN() != null) {
                    // MODIFY WHEN <boolean> replaces the run condition.
                    task.setCondition(visitor.getOriginalText(ctx.taskAction().booleanExpr()).trim());
                    logger.trace("Modified task condition: {}", taskName);
                }

            } else if (ctx.PIPE() != null) {
                final String pipeName = visitor.getText(ctx.qualifiedName());
                final Schema schema = catalog.resolveOwningSchema(pipeName);
                final Pipe pipe = schema.getPipe(QualifiedName.parse(pipeName).last());
                checkAlter(SecurableObjectType.PIPE, pipeName);
                final FrostlakeParser.PipeActionContext action = ctx.pipeAction();

                if (action.tagSet() != null) {
                    applyTagSet(pipe, action.tagSet());
                } else if (action.tagUnset() != null) {
                    applyTagUnset(pipe, action.tagUnset());
                } else if (action.SET() != null) {
                    for (final FrostlakeParser.PipeSetOptionContext opt : action.pipeSetOption()) {
                        final String optName = visitor.getText(opt.identifier()).toUpperCase();
                        if ("PIPE_EXECUTION_PAUSED".equals(optName) && opt.booleanValue() != null) {
                            pipe.setPaused(opt.booleanValue().TRUE() != null);
                        } else if ("COMMENT".equals(optName) && opt.STRING_LITERAL() != null) {
                            pipe.setComment(visitor.extractStringLiteral(opt.STRING_LITERAL()));
                        } else {
                            throw new RuntimeException("Unsupported ALTER PIPE SET option: " + optName);
                        }
                    }
                    logger.trace("Set options on pipe: {}", pipeName);
                } else if (action.REFRESH() != null) {
                    // REFRESH triggers a one-time ingest: execute the pipe's COPY INTO … FROM @stage,
                    // optionally narrowed by PREFIX (stage-relative path prefix) and/or MODIFIED_AFTER
                    // (only files last-modified after the given timestamp are loaded).
                    final String copy = pipe.getCopyStatement();
                    if (copy != null && !copy.isBlank()) {
                        String refreshPrefix = null;
                        String refreshModifiedAfter = null;
                        for (final FrostlakeParser.PipeRefreshOptionContext opt : action.pipeRefreshOption()) {
                            final String optName = visitor.getText(opt.identifier()).toUpperCase();
                            final String optValue = visitor.extractStringLiteral(opt.STRING_LITERAL());
                            if ("PREFIX".equals(optName)) {
                                refreshPrefix = optValue;
                            } else if ("MODIFIED_AFTER".equals(optName)) {
                                refreshModifiedAfter = optValue;
                            } else {
                                throw new RuntimeException("Unsupported ALTER PIPE REFRESH option: " + optName);
                            }
                        }
                        // The pipe's COPY names resolve in the pipe's own schema, not the session's.
                        final String[] priorScope = catalog.currentSessionScope();
                        catalog.beginSessionScope(schema.getDatabaseName(), schema.getName());
                        try {
                            queryExecutor.executeCopyRefresh(copy, refreshPrefix, refreshModifiedAfter);
                        } finally {
                            catalog.restoreSessionScope(priorScope);
                        }
                    }
                    logger.trace("Refreshed pipe: {}", pipeName);
                }

            } else if (ctx.WAREHOUSE() != null) {
                // IF EXISTS forgives ONLY the warehouse's absence: an error the action itself
                // raises — an invalid resume/suspend state, an unknown UNSET property — propagates
                // (live-verified).
                branchHandlesIfExists = true;
                final String warehouseName = visitor.getText(ctx.identifier());
                if (ifExists && !catalog.hasWarehouse(warehouseName)) {
                    logger.debug("Warehouse does not exist (IF EXISTS): {}", warehouseName);
                } else {
                    final Warehouse warehouse = catalog.getWarehouse(warehouseName);
                    checkAlter(SecurableObjectType.WAREHOUSE, warehouseName);

                    if (ctx.warehouseAction().RENAME() != null) {
                        final String newName = visitor.getText(ctx.warehouseAction().identifier());
                        catalog.renameWarehouse(warehouseName, newName);
                        logger.trace("Renamed warehouse {} to {}", warehouseName, newName);
                    } else if (ctx.warehouseAction().RESUME_IF_SUSPENDED() != null) {
                        warehouse.resume(true);
                        logger.trace("Resumed warehouse if suspended: {}", warehouseName);
                    } else if (ctx.warehouseAction().RESUME() != null) {
                        warehouse.resume(false);
                        logger.trace("Resumed warehouse: {}", warehouseName);
                    } else if (ctx.warehouseAction().SUSPEND() != null) {
                        warehouse.suspend();
                        logger.trace("Suspended warehouse: {}", warehouseName);
                    } else if (ctx.warehouseAction().ENABLE() != null || ctx.warehouseAction().DISABLE() != null) {
                        // ENABLE | DISABLE applies to an adaptive warehouse only: whether it takes new jobs.
                        final String verb = ctx.warehouseAction().ENABLE() != null ? "ENABLE" : "DISABLE";
                        if (!"ADAPTIVE".equals(warehouse.getWarehouseType())) {
                            throw new RuntimeException(SqlCompilationError.of("Invalid operation " + verb
                                + " on warehouse '" + warehouse.getName() + "': it is not an adaptive warehouse."));
                        }
                        warehouse.setEnabled(ctx.warehouseAction().ENABLE() != null);
                        logger.trace("{} warehouse: {}", verb, warehouseName);
                    } else if (ctx.warehouseAction().ABORT_ALL_QUERIES() != null) {
                        // Accepted; the engine executes statements synchronously, so there is
                        // never a query in flight to abort.
                        logger.trace("Aborted all queries on warehouse: {}", warehouseName);
                    } else if (ctx.warehouseAction().SET() != null) {
                        requireSizeForWaitForCompletion(ctx.warehouseAction().warehouseProperty());
                        ddlHandler.requireResourceConstraintFits(warehouse, ctx.warehouseAction().warehouseProperty());
                        for (final FrostlakeParser.WarehousePropertyContext prop : ctx.warehouseAction().warehouseProperty()) {
                            applyWarehouseProperty(warehouse, prop);
                        }
                        logger.trace("Altered warehouse: {}", warehouseName);
                    } else if (ctx.warehouseAction().tagSet() != null) {
                        applyTagSet(warehouse, ctx.warehouseAction().tagSet());
                    } else if (ctx.warehouseAction().tagUnset() != null) {
                        applyTagUnset(warehouse, ctx.warehouseAction().tagUnset());
                    } else if (ctx.warehouseAction().UNSET() != null) {
                        for (final FrostlakeParser.WarehouseUnsetPropertyContext prop
                                : ctx.warehouseAction().warehouseUnsetProperty()) {
                            unsetWarehouseProperty(warehouse, prop);
                        }
                        logger.trace("Unset warehouse properties: {}", warehouseName);
                    }
                }

            } else if (ctx.STAGE() != null) {
                final String stageName = visitor.getText(ctx.qualifiedName());
                final Stage stage = catalog.getStage(stageName);
                checkAlter(SecurableObjectType.STAGE, stageName);
                final FrostlakeParser.StageActionContext stageAction = ctx.stageAction();
                if (stageAction.REFRESH() != null) {
                    refreshStageDirectory(stage, stageName, stageAction);
                } else if (stageAction.UNSET() != null) {
                    // Live's own refusal — UNSET simply is not a stage action there.
                    throw new RuntimeException("Unsupported feature 'UNSET'.");
                } else if (stageAction.URL() != null) {
                    stage.setUrl(visitor.extractStringLiteral(stageAction.STRING_LITERAL()));
                    logger.trace("Set URL on stage: {}", stageName);
                } else if (stageAction.FILE_FORMAT() != null) {
                    if (stageAction.STRING_LITERAL() != null) {
                        final String named = visitor.extractStringLiteral(stageAction.STRING_LITERAL());
                        FileFormatReference.require(catalog, SqlIdentifiers.canonicalText(named));
                        stage.setFileFormat(named);
                    } else if (stageAction.qualifiedName() != null) {
                        final String named = visitor.getText(stageAction.qualifiedName());
                        FileFormatReference.require(catalog, named);
                        stage.setFileFormat(named);
                    } else if (stageAction.parenOptionList() != null) {
                        applyStageFormatOptions(stage, stageAction.parenOptionList());
                    }
                    logger.trace("Set file format on stage: {}", stageName);
                } else if (stageAction.RENAME() != null) {
                    queryExecutor.requireOwnership("STAGE", qualifiedNameParts(ctx.qualifiedName()), null);
                    final String newName = visitor.getText(stageAction.identifier());
                    catalog.renameStage(stageName, newName);
                    logger.trace("Renamed stage {} to {}", stageName, newName);
                } else if (stageAction.COMMENT() != null) {
                    stage.setComment(visitor.extractStringLiteral(stageAction.STRING_LITERAL()));
                    logger.trace("Set comment on stage: {}", stageName);
                } else if (stageAction.identifier() != null) {
                    // SET <name> = … — COPY_OPTIONS and DIRECTORY are modeled; other names are accepted and inert.
                    final String key = visitor.getText(stageAction.identifier()).toUpperCase();
                    if ("DIRECTORY".equals(key) && stageAction.parenOptionList() != null) {
                        // DIRECTORY = (ENABLE = TRUE | FALSE) turns the stage's directory table on or off.
                        for (final FrostlakeParser.ParenOptionContext option
                                : stageAction.parenOptionList().parenOption()) {
                            if ("ENABLE".equalsIgnoreCase(option.optionKey().getText())
                                    && option.copyOptionValue() != null) {
                                stage.setDirectoryEnabled("TRUE".equalsIgnoreCase(option.copyOptionValue().getText()));
                            }
                        }
                    } else if ("COPY_OPTIONS".equals(key) && stageAction.parenOptionList() != null) {
                        for (final FrostlakeParser.ParenOptionContext option
                                : stageAction.parenOptionList().parenOption()) {
                            String value = option.copyOptionValue() != null
                                ? option.copyOptionValue().getText() : "";
                            if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
                                value = value.substring(1, value.length() - 1);
                            }
                            stage.getCopyOptions().put(option.optionKey().getText().toUpperCase(), value);
                        }
                    }
                    logger.trace("Set {} on stage: {}", key, stageName);
                }

            } else if (ctx.USER() != null) {
                branchHandlesIfExists = true;
                alterUser(visitor.getText(ctx.identifier()), ctx.userAction(), ifExists);

            } else if (ctx.ROLE() != null) {
                final String roleName = visitor.getText(ctx.identifier());
                final Role role = catalog.getRole(roleName);

                if (ctx.roleAction().tagSet() != null) {
                    applyTagSet(role, ctx.roleAction().tagSet());
                } else if (ctx.roleAction().tagUnset() != null) {
                    applyTagUnset(role, ctx.roleAction().tagUnset());
                } else if (ctx.roleAction().RENAME() != null) {
                    final String newName = visitor.getText(ctx.roleAction().identifier());
                    catalog.renameRole(roleName, newName);
                    logger.trace("Renamed role {} to {}", roleName, newName);
                } else if (ctx.roleAction().UNSET() != null) {
                    role.setComment(null);
                } else if (ctx.roleAction().COMMENT() != null) {
                    final String comment = visitor.extractStringLiteral(ctx.roleAction().STRING_LITERAL());
                    role.setComment(comment);
                    logger.trace("Set comment on role: {}", roleName);
                }

            } else if (ctx.SESSION() != null) {
                if (ctx.sessionAction().SET() != null) {
                    // ALTER SESSION SET p1 = v1 [, p2 = v2 ...] — apply each assignment.
                    for (final FrostlakeParser.SessionAssignmentContext assignment
                            : ctx.sessionAction().sessionAssignment()) {
                        final String rawName = assignment.sessionParameter().MULTI_STATEMENT_COUNT() != null
                            ? "MULTI_STATEMENT_COUNT"
                            : assignment.sessionParameter().identifier().getText();
                        // An unknown name refuses BEFORE anything applies (live-verified shape).
                        if (!ParameterRegistry.isSessionParameter(ParameterRegistry.canonical(rawName))) {
                            throw ParameterRegistry.invalidSessionParameter(ParameterRegistry.spell(rawName));
                        }
                        final String paramName = ParameterRegistry.canonical(rawName);

                        final Object value = visitor.parseLiteral(assignment.literal());
                        requireLegalSessionValue(paramName, value, assignment.literal());
                        if ("SEARCH_PATH".equals(paramName)) {
                            // Each schema the path names is resolved NOW, as the ALTER runs, not when a
                            // later listing reads the path.
                            SearchPathValue.requireResolvable(catalog, String.valueOf(value));
                        }

                        if (queryExecutor.getDatabaseEngine() != null) {
                            queryExecutor.getDatabaseEngine().getSessionContext().setSessionParameter(paramName, value);
                            // AUTOCOMMIT is not just a parameter: ALTER SESSION SET AUTOCOMMIT is
                            // Snowflake's way to drive the real transaction mode, so toggle it too —
                            // and an open transaction is COMMITTED first, whether or not the value
                            // actually changes. No other session parameter does that.
                            if ("AUTOCOMMIT".equalsIgnoreCase(paramName)) {
                                commitOpenTransactionForAutocommit();
                                final String text = String.valueOf(value);
                                final boolean autoCommitValue = Boolean.TRUE.equals(value)
                                    || "TRUE".equalsIgnoreCase(text) || "1".equals(text);
                                queryExecutor.getDatabaseEngine().setAutoCommit(autoCommitValue);
                            }
                            logger.trace("Set session parameter {} = {}", paramName, value);
                        }
                    }
                } else if (ctx.sessionAction().UNSET() != null) {
                    // ALTER SESSION UNSET <param> [, <param> ...] — clear each named session parameter.
                    if (queryExecutor.getDatabaseEngine() != null) {
                        for (final FrostlakeParser.SessionParameterContext param : ctx.sessionAction().sessionParameter()) {
                            final String rawName = param.MULTI_STATEMENT_COUNT() != null
                                ? "MULTI_STATEMENT_COUNT"
                                : param.identifier().getText();
                            // UNSET refuses an unknown name exactly like SET (live-verified).
                            if (!ParameterRegistry.isSessionParameter(ParameterRegistry.canonical(rawName))) {
                                throw ParameterRegistry.invalidSessionParameter(ParameterRegistry.spell(rawName));
                            }
                            final String paramName = ParameterRegistry.canonical(rawName);
                            // UNSET AUTOCOMMIT commits an open transaction exactly as SET does.
                            if ("AUTOCOMMIT".equalsIgnoreCase(paramName)) {
                                commitOpenTransactionForAutocommit();
                            }
                            queryExecutor.getDatabaseEngine().getSessionContext().unsetSessionParameter(paramName);
                            logger.trace("Unset session parameter {}", paramName);
                        }
                    }
                }

            } else if (ctx.TAG() != null) {
                final String tagName = visitor.getText(ctx.qualifiedName());
                // A property written twice and a propagation mode that is none of the three are compilation errors:
                // refused before the tag is looked up, with or without IF EXISTS, which forgives only its absence.
                branchHandlesIfExists = true;
                TagPropagation.requireCompilable(ctx.tagAction().tagSetProperty());
                if (ifExists && !catalog.hasTag(tagName)) {
                    logger.debug("Tag does not exist (IF EXISTS): {}", tagName);
                    return null;
                }
                final Tag tag = catalog.getTag(tagName);
                checkAlter(SecurableObjectType.TAG, tagName);

                if (ctx.tagAction().ADD() != null) {
                    // The conflict rule is judged against the values the statement would leave, before they change.
                    final List<String> values = tag.getAllowedValues();
                    for (final var stringLiteral : ctx.tagAction().stringLiteralList().STRING_LITERAL()) {
                        final String value = visitor.extractStringLiteral(stringLiteral);
                        if (!values.contains(value)) {
                            values.add(value);
                        }
                    }
                    TagPropagation.requireConsistent(values, tag.getPropagate(), tag.getOnConflict());
                    tag.setAllowedValues(values);
                    logger.trace("Added allowed values to tag: {}", tagName);
                } else if (ctx.tagAction().DROP() != null) {
                    final List<String> values = tag.getAllowedValues();
                    for (final var stringLiteral : ctx.tagAction().stringLiteralList().STRING_LITERAL()) {
                        values.remove(visitor.extractStringLiteral(stringLiteral));
                    }
                    TagPropagation.requireConsistentAfterDrop(values, tag.getPropagate(), tag.getOnConflict());
                    tag.setAllowedValues(values);
                    logger.trace("Dropped allowed values from tag: {}", tagName);
                } else if (ctx.tagAction().UNSET() != null) {
                    final FrostlakeParser.TagUnsetPropertyContext property = ctx.tagAction().tagUnsetProperty();
                    if (property.ALLOWED_VALUES() != null) {
                        // A conflict rule that orders by the allowed values keeps them.
                        TagPropagation.requireConsistent(new ArrayList<String>(), tag.getPropagate(),
                            tag.getOnConflict());
                        tag.clearAllowedValues();
                    } else if (property.PROPAGATE() != null) {
                        tag.setPropagate(null);
                        tag.setOnConflict(null);
                    } else if (property.ON_CONFLICT() != null) {
                        tag.setOnConflict(null);
                    } else {
                        tag.setComment(null);
                    }
                    logger.trace("Unset a property of tag: {}", tagName);
                } else if (ctx.tagAction().SET() != null && ctx.tagAction().MASKING() != null) {
                    // MASKING is not a tag property — Snowflake refuses it at compile time
                    // (live-verified wording).
                    throw new RuntimeException(
                        SqlCompilationError.of("invalid property 'MASKING' for 'TAG'"));
                } else if (ctx.tagAction().SET() != null && ctx.tagAction().ALLOWED_VALUES() != null) {
                    final List<String> values = new ArrayList<>();
                    for (final TerminalNode literal : ctx.tagAction().stringLiteralList().STRING_LITERAL()) {
                        values.add(visitor.extractStringLiteral(literal));
                    }
                    // The tag's conflict rule is judged against the new values before they are set.
                    TagPropagation.requireConsistent(values, tag.getPropagate(), tag.getOnConflict());
                    tag.setAllowedValues(values);
                    logger.trace("Set the allowed values of tag: {}", tagName);
                } else if (ctx.tagAction().SET() != null) {
                    // Each property sets only itself: a PROPAGATE keeps the conflict rule the tag has. The tag the
                    // statement would leave is judged first, so a refused SET changes nothing.
                    String propagate = tag.getPropagate();
                    String onConflict = tag.getOnConflict();
                    String comment = tag.getComment();
                    for (final FrostlakeParser.TagSetPropertyContext property : ctx.tagAction().tagSetProperty()) {
                        if (property.tagPropagation() != null) {
                            propagate = TagPropagation.mode(property.tagPropagation());
                        } else if (property.tagConflict() != null) {
                            onConflict = TagPropagation.conflict(property.tagConflict());
                        } else {
                            comment = ddlHandler.extractComment(property.commentClause());
                        }
                    }
                    TagPropagation.requireConsistent(tag.getAllowedValues(), propagate, onConflict);
                    tag.setPropagate(propagate);
                    tag.setOnConflict(onConflict);
                    tag.setComment(comment);
                    logger.trace("Set properties on tag: {}", tagName);
                } else if (ctx.tagAction().RENAME() != null) {
                    final String newName = visitor.getText(ctx.tagAction().qualifiedName());
                    catalog.renameTag(tagName, newName);
                    logger.trace("Renamed tag {} to {}", tagName, newName);
                }
            }

            return null;

        } catch (final Exception e) {
            if (ifExists && !branchHandlesIfExists) {
                logger.debug("ALTER IF EXISTS: object not found, suppressing error: {}", e.getMessage());
                return null;
            }
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        }
    }

    /** Only an owner / administrative role / ALTER-granted role may alter the object. */
    /**
     * ALTER COMPUTE POOL ... SET: apply each option, then hold the resulting node range to the same
     * rules CREATE enforces, with the same wording. INSTANCE_FAMILY only changes on a suspended
     * pool, PLACEMENT_GROUP always fails the region-registry lookup (this engine has none), and
     * every backup family must be in the catalog and differ from the primary — all live-verified.
     */
    private void applyComputePoolSetOptions(final ComputePool pool,
            final List<FrostlakeParser.ComputePoolSetOptionContext> options) {
        for (final FrostlakeParser.ComputePoolSetOptionContext option : options) {
            if (option.MIN_NODES() != null) {
                pool.setMinNodes(Integer.parseInt(option.INTEGER_LITERAL().getText()));
            } else if (option.MAX_NODES() != null) {
                pool.setMaxNodes(Integer.parseInt(option.INTEGER_LITERAL().getText()));
            } else if (option.AUTO_RESUME() != null) {
                pool.setAutoResume("TRUE".equalsIgnoreCase(option.booleanValue().getText()));
            } else if (option.AUTO_SUSPEND_SECS() != null) {
                pool.setAutoSuspendSecs(Integer.parseInt(option.INTEGER_LITERAL().getText()));
            } else if (option.INSTANCE_FAMILY() != null) {
                if (pool.getState() != ComputePoolState.SUSPENDED) {
                    throw new RuntimeException("Invalid property 'INSTANCE_FAMILY' for"
                        + " 'COMPUTE_POOL':\nCannot set INSTANCE_FAMILY for compute pool that is not"
                        + " suspended. Please suspend the compute pool first.");
                }
                final String family = visitor.getText(option.identifier()).toUpperCase();
                requireValidInstanceFamily(family);
                pool.setInstanceFamily(family);
            } else if (option.PLACEMENT_GROUP() != null) {
                final String group = visitor.extractStringLiteral(option.STRING_LITERAL());
                throw new RuntimeException("Invalid value '" + group + "' for property"
                    + " 'PLACEMENT_GROUP':\nPlacement group '" + group
                    + "' does not exist in this region.");
            } else if (option.BACKUP_INSTANCE_FAMILIES() != null) {
                final List<String> families = new ArrayList<>();
                for (final TerminalNode literal : option.stringLiteralList().STRING_LITERAL()) {
                    final String family = visitor.extractStringLiteral(literal);
                    requireValidInstanceFamily(family);
                    if (family.equalsIgnoreCase(pool.getInstanceFamily())) {
                        throw new RuntimeException("Invalid BACKUP_INSTANCE_FAMILIES for compute"
                            + " pool: BACKUP_INSTANCE_FAMILIES contains '" + family
                            + "' which matches the primary INSTANCE_FAMILY. Each backup must be"
                            + " different from the primary.");
                    }
                    families.add(family.toUpperCase());
                }
                pool.setBackupInstanceFamilies(families);
            } else if (option.COMMENT() != null) {
                pool.setComment(visitor.extractStringLiteral(option.STRING_LITERAL()));
            }
        }
        if (pool.getMinNodes() < 1) {
            throw new RuntimeException(SqlCompilationError.of(
                "invalid value '" + pool.getMinNodes() + "' for property 'MIN_NODES'"));
        }
        if (pool.getMinNodes() > pool.getMaxNodes()) {
            throw new RuntimeException(SqlCompilationError.of(
                "invalid property combination 'MIN_NODES'='" + pool.getMinNodes()
                + "' and 'MAX_NODES'='" + pool.getMaxNodes() + "'"));
        }
        pool.touchUpdatedOn();
    }

    /** The valid STOP ALL workload types, in the order a real account lists them. */
    private static final List<String> COMPUTE_POOL_WORKLOAD_TYPES =
        Arrays.asList("ALL", "USER", "NOTEBOOK", "MODEL_SERVING", "STREAMLIT", "ML_JOB");

    /**
     * STOP ALL OF TYPE list validation: each type must be one of the known set, reported in list
     * order, and ALL must stand alone — both messages verbatim from a real account.
     */
    private void validateStopAllWorkloadTypes(
            final List<FrostlakeParser.ComputePoolWorkloadTypeContext> typeContexts) {
        final List<String> types = new ArrayList<>();
        for (final FrostlakeParser.ComputePoolWorkloadTypeContext typeCtx : typeContexts) {
            types.add(typeCtx.getText().toUpperCase());
        }
        final String tail = " Expected comma separated list like: 'type1, type2'."
            + " Valid types are ALL, USER, NOTEBOOK, MODEL_SERVING, STREAMLIT, ML_JOB.";
        for (final String type : types) {
            if (!COMPUTE_POOL_WORKLOAD_TYPES.contains(type)) {
                throw new RuntimeException("Invalid input for OF TYPE option: Invalid workload type '"
                    + type + "'." + tail);
            }
        }
        if (types.contains("ALL") && types.size() > 1) {
            throw new RuntimeException("Invalid input for OF TYPE option: When 'ALL' is specified in '"
                + String.join(",", types) + "', no other workload types should be included."
                + " Use 'ALL' alone or specify individual types." + tail);
        }
    }

    /** The "Cannot set" wording covers UNSET too, live-verified. */
    private void requireSuspendedForPlacementGroup(final ComputePool pool) {
        if (pool.getState() != ComputePoolState.SUSPENDED) {
            throw new RuntimeException("Invalid property 'PLACEMENT_GROUP' for 'COMPUTE_POOL':"
                + "\nCannot set PLACEMENT_GROUP for compute pool that is not suspended."
                + " Please suspend the compute pool first.");
        }
    }

    private void requireValidInstanceFamily(final String family) {
        if (!InstanceFamilies.isValid(family)) {
            throw new RuntimeException("Invalid instance family " + family
                + ". Please refer to Snowflake documentation for supported instance families.");
        }
    }

    /**
     * Whether ALTER TABLE's target exists — for the IF EXISTS gate, which forgives only the TABLE's
     * absence. The schema is resolved first and its absence THROWS even under IF EXISTS, exactly as
     * a real account behaves ("Schema '&lt;db&gt;.&lt;schema&gt;' does not exist or not authorized.").
     */
    private boolean alterTargetTableExists(final String[] tableParts) {
        if (tableParts.length == 1 && catalog.getCurrentSchema() == null) {
            return false;
        }
        final Schema schema = tableParts.length >= 2
            ? catalog.resolveSchema(QualifiedName.of(Arrays.copyOf(tableParts, tableParts.length - 1)))
            : catalog.resolveSchema(QualifiedName.of(new String[] {catalog.getCurrentSchema()}));
        return schema.hasTable(tableParts[tableParts.length - 1]);
    }

    /**
     * The ALTER/MODIFY column-action list. Every item is validated before ANY is applied, so a list
     * whose second item fails — an unknown column, an illegal type change — leaves the table
     * untouched, exactly as a real account behaves.
     */
    private void applyAlterColumnItems(final Table table, final String tableName,
            final List<FrostlakeParser.AlterColumnItemContext> items) {
        for (final FrostlakeParser.AlterColumnItemContext item : items) {
            validateAlterColumnItem(table, tableName, item);
        }
        for (final FrostlakeParser.AlterColumnItemContext item : items) {
            applyAlterColumnItem(table, tableName, item);
        }
    }

    private void validateAlterColumnItem(final Table table, final String tableName,
            final FrostlakeParser.AlterColumnItemContext item) {
        final String colName = visitor.getText(item.identifier());
        if (!table.hasColumn(colName)) {
            // A missing column is a POSITIONED semantic refusal: the prefix line points at the
            // identifier itself, live-verified against ALTER COLUMN on a column the table lost.
            final Token at = item.identifier().getStart();
            throw new RuntimeException(SqlCompilationError.at(at.getLine(),
                at.getCharPositionInLine(), "invalid identifier '" + colName.toUpperCase() + "'"));
        }
        final TableColumn column = table.getColumn(colName);
        final FrostlakeParser.AlterColumnItemActionContext action = item.alterColumnItemAction();
        if (action.dataTypeName() != null) {
            // Snowflake only allows same-family changes
            // (live-verified: "cannot change column COL from type NUMBER(38,0) to VARCHAR"),
            // and within a family only the widening ones — see retypeRefusalReason. A collated
            // column must RESTATE its collation, verbatim: dropping it, changing it or adding one
            // is refused with a message that QUOTES both types (live-verified).
            final DataType newDataType =
                visitor.parseDataType(action.dataTypeName(), action.typeParameters());
            final String newCollation = ColumnDefinitionParser.storedCollation(action.collateClause());
            if (column.getDataType().getClass().equals(newDataType.getClass())
                    && !collationsMatch(column.getCollation(), newCollation)) {
                throw new RuntimeException(SqlCompilationError.trailing("cannot change column "
                    + colName.toUpperCase()
                    + " from type \"" + typeText(column.getDataType()) + collateSuffix(column.getCollation())
                    + "\" to \"" + typeText(newDataType) + collateSuffix(newCollation)
                    + "\" because they have incompatible collations."));
            }
            final String refusalReason = retypeRefusalReason(column.getDataType(), newDataType);
            if (refusalReason != null) {
                throw new RuntimeException(SqlCompilationError.trailing("cannot change column "
                    + colName.toUpperCase()
                    + " from type " + typeText(column.getDataType()) + " to " + typeText(newDataType)
                    + refusalReason));
            }
            requireStoredValuesRepresentable(table, tableName, colName, column.getDataType(), newDataType);
        } else if (action.SET() != null && action.DEFAULT() != null) {
            // ALTER COLUMN SET DEFAULT is refused in almost every shape — the one accepted case is
            // re-pointing a column that ALREADY carries a sequence default at a sequence, which may
            // be a DIFFERENT one. Measured: a sequence onto a column with no default, onto a
            // literal-defaulted column, or onto one whose default was just dropped, and a literal
            // onto any column at all, are each "Unsupported feature 'Alter Column Set Default'."
            // (Frostlake required only that the NEW default be a sequence, so it accepted the ADD.)
            // DROP DEFAULT is unrestricted and stays below.
            // The NEW default's classification comes from its expression AST — a qualified
            // reference whose last part is NEXTVAL — not from the spacing-sensitive source text
            // (live accepts `seq . NEXTVAL`). The EXISTING default is a catalog string, so its
            // check stays textual.
            final Expression newDefault =
                ExpressionEvaluator.parse(visitor.getOriginalText(action.defaultExpression()));
            final boolean newDefaultIsSequence = newDefault instanceof ColumnReferenceExpression
                && ((ColumnReferenceExpression) newDefault).isQualified()
                && "NEXTVAL".equalsIgnoreCase(((ColumnReferenceExpression) newDefault).getColumnName());
            final Object existingDefault = column.getDefaultValue();
            final boolean replacingASequenceDefault = existingDefault != null
                && String.valueOf(existingDefault).trim().toUpperCase().endsWith(".NEXTVAL");
            if (!newDefaultIsSequence || !replacingASequenceDefault) {
                throw new RuntimeException("Unsupported feature 'Alter Column Set Default'.");
            }
        }
    }

    /**
     * Why Snowflake refuses this retype, as the clause that closes its message — or null when the
     * change is allowed. Live-measured: a cross-family change is always refused; a NUMBER must keep
     * its SCALE but may move its precision either way (even with rows present); a VARCHAR may grow
     * but never shrink, and that one refusal spells its own reason; a datetime column may only
     * restate its own subtype, DATE / TIME / the timestamp variants being distinct (TIMESTAMP and
     * DATETIME are spellings of TIMESTAMP_NTZ).
     */
    /**
     * A NUMBER may NARROW its precision only when every stored value still fits: the retype scans the
     * column's data at DDL time and refuses the whole ALTER when any row cannot be represented at the
     * new width — naming the two PRECISIONS, never the value (live-verified). NULLs pass, an empty
     * column narrows freely, and the widths alone never refuse; the direction refusals above run
     * first, so a scale change or a varchar shrink keeps its own sentence whatever the data.
     */
    private void requireStoredValuesRepresentable(final Table table, final String tableName,
            final String colName, final DataType oldType, final DataType newType) {
        if (!(oldType instanceof NumericType) || !(newType instanceof NumericType)
                || NumericType.isApproximate(oldType) || NumericType.isApproximate(newType)) {
            return;
        }
        final int oldPrecision = ((NumericType) oldType).getPrecision();
        final int newPrecision = ((NumericType) newType).getPrecision();
        if (newPrecision >= oldPrecision) {
            return;
        }
        final TableStorage storage = queryExecutor.getStorageEngine()
            .getTableStorage(queryExecutor.getFullyQualifiedTableName(tableName));
        if (storage == null) {
            return;
        }
        int slot = -1;
        final List<TableColumn> columns = table.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getName().equalsIgnoreCase(colName)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            return;
        }
        final int scale = ((NumericType) newType).getScale();
        for (final Row row : storage.scan()) {
            final Object value = row.getValue(slot);
            if (value instanceof Number
                    && NumericRangeRefusal.exceeds(new BigDecimal(value.toString()), newPrecision, scale)) {
                throw new RuntimeException(SqlCompilationError.trailing("cannot change column "
                    + colName.toUpperCase()
                    + " from type " + typeText(oldType) + " to " + typeText(newType)
                    + " because some existing values cannot be represented using precision "
                    + newPrecision + " instead of precision " + oldPrecision + "."));
            }
        }
    }

    private static String retypeRefusalReason(final DataType oldType, final DataType newType) {
        if (!oldType.getClass().equals(newType.getClass())) {
            return "";
        }
        if (IntervalCasts.isIntervalType(oldType)) {
            // An interval keeps its fields: DAY TO SECOND to DAY is "cannot change column C from type INTERVAL
            // DAY(9) TO SECOND(9) to INTERVAL DAY(9)", with no reason given (live-verified).
            return oldType.equals(newType) ? null : "";
        }
        if (oldType instanceof NumericType) {
            if (((NumericType) newType).getScale() == ((NumericType) oldType).getScale()) {
                return null;
            }
            // The reason clause is for two EXACT numbers only. A FLOAT on either side is refused with
            // no reason at all, even though the scales differ there too — live-measured in both
            // directions, and the approximate family's scale is a placeholder rather than a decision.
            return NumericType.isApproximate(oldType) || NumericType.isApproximate(newType)
                ? "" : " because changing the scale of a number is not supported.";
        }
        if (oldType instanceof StringType) {
            return ((StringType) newType).getMaxLength() < ((StringType) oldType).getMaxLength()
                ? " because reducing the byte-length of a varchar is not supported." : null;
        }
        if (oldType instanceof DateTimeType) {
            return datetimeSubtype(oldType.getName()).equals(datetimeSubtype(newType.getName())) ? null : "";
        }
        return null;
    }

    /** Whether a retype keeps the column's collation: both absent, or both the same spec. */
    private static boolean collationsMatch(final String oldCollation, final String newCollation) {
        if (oldCollation == null) {
            return newCollation == null;
        }
        return oldCollation.equalsIgnoreCase(newCollation);
    }

    /** The COLLATE suffix a collated type carries inside the refusal's quotes, or nothing. */
    private static String collateSuffix(final String collation) {
        return collation == null ? "" : " COLLATE '" + collation + "'";
    }

    /**
     * A type as Snowflake spells it in a retype refusal — with the parameters that type really has,
     * which is not the same as "with its parameters". Measured across every target a retype can name:
     *
     * <pre>
     *   VARCHAR(10)  BINARY(8)  NUMBER(10,2)      the width or the pair, as declared
     *   TIME(9)  TIMESTAMP_NTZ(9)  TIMESTAMP_TZ(9)   a fractional-seconds precision, DEFAULTED to 9
     *   DATE  BOOLEAN  VARIANT  OBJECT  ARRAY     no parameters at all
     *   FLOAT                                     the approximate family has NO pair to quote
     * </pre>
     *
     * <p>The FLOAT line is the one that was wrong: every NumericType was printed with a precision and
     * scale, so an approximate column was quoted as FLOAT(38,9) — the same fabricated pair that no other
     * metadata surface shows. That pair is an engine-internal placeholder and must never be shown.
     * DOUBLE and REAL are stored as FLOAT and live names them FLOAT here too, so the name needs no
     * mapping of its own.
     */
    private static String typeText(final DataType type) {
        if (type instanceof StringType) {
            return type.getName() + "(" + ((StringType) type).getMaxLength() + ")";
        }
        if (type instanceof BinaryType) {
            return type.getName() + "(" + ((BinaryType) type).getMaxLength() + ")";
        }
        if (type instanceof DateTimeType) {
            // A DATE has no fractional seconds to state; every other temporal names its precision.
            return "DATE".equalsIgnoreCase(type.getName()) ? type.getName()
                : type.getName() + "(" + ((DateTimeType) type).getPrecision() + ")";
        }
        if (type instanceof NumericType) {
            if (NumericType.isApproximate(type)) {
                return type.getName();
            }
            final NumericType numeric = (NumericType) type;
            return type.getName() + "(" + numeric.getPrecision() + "," + numeric.getScale() + ")";
        }
        return type.getName();
    }

    /** TIMESTAMP and DATETIME are spellings of TIMESTAMP_NTZ; every other subtype names itself. */
    private static String datetimeSubtype(final String name) {
        if ("TIMESTAMP".equalsIgnoreCase(name) || "DATETIME".equalsIgnoreCase(name)) {
            return "TIMESTAMP_NTZ";
        }
        return name.toUpperCase();
    }

    private void applyAlterColumnItem(final Table table, final String tableName,
            final FrostlakeParser.AlterColumnItemContext item) {
        final String colName = visitor.getText(item.identifier());
        final FrostlakeParser.AlterColumnItemActionContext action = item.alterColumnItemAction();
        if (action.dataTypeName() != null) {
            table.alterColumnType(colName,
                visitor.parseDataType(action.dataTypeName(), action.typeParameters()));
            logger.trace("Altered column {} type in table {}", colName, tableName);
        } else if (action.NULL() != null) {
            // SET NOT NULL / DROP NOT NULL
            table.getColumn(colName).setNullable(action.DROP() != null);
            logger.trace("Set nullability on column {}.{}", tableName, colName);
        } else if (action.DEFAULT() != null) {
            // SET DEFAULT routes through the same default-expression parser CREATE TABLE uses: raw
            // getText() stored the literal WITH its quotes, so an insert that omitted the column got
            // the text 'X' (quotes included) instead of X.
            table.getColumn(colName).setDefaultValue(action.SET() != null
                ? ddlHandler.getColumnParser().parseDefaultExpression(action.defaultExpression())
                : null);
            logger.trace("Set/drop default on column {}.{}", tableName, colName);
        } else if (action.COMMENT() != null) {
            // ALTER COLUMN c COMMENT 'text' / UNSET COMMENT
            table.getColumn(colName).setComment(action.UNSET() != null
                ? null : visitor.extractStringLiteral(action.STRING_LITERAL()));
            logger.trace("Set/unset comment on column {}.{}", tableName, colName);
        }
    }

    /** The transient cap, which turns the modifier into a rule rather than a metadata word. */
    private static void requireTransientRetention(final String rawKey, final boolean transientObject,
            final FrostlakeParser.CopyOptionValueContext value) {
        if ("DATA_RETENTION_TIME_IN_DAYS".equalsIgnoreCase(rawKey) && value != null) {
            TransientRetentionLimit.requireWithinTransientLimit(transientObject, value.getText());
        }
    }

    /** The 90-day account ceiling, applied wherever a retention value is SET. */
    private static void requireAccountRetention(final String rawKey,
            final FrostlakeParser.CopyOptionValueContext value) {
        if ("DATA_RETENTION_TIME_IN_DAYS".equalsIgnoreCase(rawKey) && value != null) {
            TransientRetentionLimit.requireWithinAccountLimit(value.getText());
        }
    }

    /** A negative retention refuses with the bracketed invalid-value shape, bare integer echo. */
    private static void requireLegalRetention(final String rawKey,
            final FrostlakeParser.CopyOptionValueContext value) {
        if ("DATA_RETENTION_TIME_IN_DAYS".equalsIgnoreCase(rawKey) && value != null
                && value.getText().startsWith("-")) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(
                value.getText(), "DATA_RETENTION_TIME_IN_DAYS"));
        }
    }

    /** The extension-time cap: anything past 90 days refuses with the account's own sentence. */
    /** Rename a constraint, refusing an unknown name in live's wording for a missing constraint. */
    private static void requireConstraintRenamed(final Table table, final String oldName,
            final String newName) {
        if (!table.renameConstraint(oldName, newName)) {
            throw new RuntimeException(SqlCompilationError.of(
                "constraint '" + oldName.toUpperCase(Locale.ROOT) + "' does not exist"));
        }
    }

    /**
     * The enforcement properties of {@code {ALTER | MODIFY} CONSTRAINT}. RELY / NORELY are RECORDED —
     * the flag rides on the constraint's COLUMNS, which is where SHOW …KEYS reads it from — while
     * ENFORCED / NOT ENFORCED are accepted and inert: Snowflake enforces no constraint either way and
     * no read surface reports the flag.
     */
    private static void applyConstraintProperties(final Table table, final String constraintName,
            final List<FrostlakeParser.ConstraintPropertyContext> properties) {
        final List<String> columns = table.constraintColumns(constraintName);
        if (columns.isEmpty()) {
            // A DIFFERENT sentence from RENAME CONSTRAINT's, measured: this path answers with the
            // generic object-not-found shape, where the rename says "constraint 'X' does not exist".
            throw new RuntimeException(SqlCompilationError.doesNotExistWithoutHint(
                "Object", constraintName.toUpperCase(Locale.ROOT)));
        }
        for (final FrostlakeParser.ConstraintPropertyContext property : properties) {
            if (property.relyOption() != null) {
                final boolean rely = property.relyOption().RELY() != null;
                for (final String column : columns) {
                    if (table.hasColumn(column)) {
                        table.getColumn(column).setRely(rely);
                    }
                }
            }
        }
    }

    /**
     * ERROR_LOGGING takes a BAREWORD, not a string: live accepts {@code = DEFAULT} and refuses
     * {@code = 'DEFAULT'} with the bracketed invalid-value shape, quotes and all (live-measured).
     */
    private static void requireLegalErrorLogging(final String rawKey,
            final FrostlakeParser.CopyOptionValueContext value) {
        if (!"ERROR_LOGGING".equalsIgnoreCase(rawKey) || value == null) {
            return;
        }
        if (value.STRING_LITERAL() != null) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(
                value.getText(), "ERROR_LOGGING"));
        }
    }

    private static void requireLegalExtensionTime(final String rawKey,
            final FrostlakeParser.CopyOptionValueContext value) {
        if (!"MAX_DATA_EXTENSION_TIME_IN_DAYS".equalsIgnoreCase(rawKey) || value == null) {
            return;
        }
        try {
            if (Long.parseLong(value.getText()) > 90L) {
                throw new RuntimeException(SqlCompilationError.of(
                    "Exceeds maximum allowable extension time (90)."));
            }
        } catch (final NumberFormatException notANumber) {
            // A non-numeric value is out of this check's measured scope.
        }
    }

    /**
     * The session-parameter values the account refuses, live-measured: an unknown TIMEZONE and a
     * TIMESTAMP_TYPE_MAPPING outside the three timestamp types. The echo strips string quotes —
     * unlike the format-option family, which keeps them.
     */
    private static void requireLegalSessionValue(final String paramName, final Object value,
                                                 final FrostlakeParser.LiteralContext written) {
        // A whole number past 32 bits is no parameter's value, whatever the parameter takes: 2147483647 is a
        // LOCK_TIMEOUT and 2147483648 is refused, echoed as written (live-verified).
        if (written.INTEGER_LITERAL() != null && value instanceof Number
                && new BigDecimal(value.toString()).compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(written.getText(), paramName));
        }
        final String text = String.valueOf(value);
        if ("TIMEZONE".equals(paramName) && !isKnownTimezone(text)) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(text, "TIMEZONE"));
        }
        if ("TIMESTAMP_TYPE_MAPPING".equals(paramName)
                && !text.toUpperCase().matches("TIMESTAMP_(NTZ|LTZ|TZ)")) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(
                text, "TIMESTAMP_TYPE_MAPPING"));
        }
        // TIMESTAMP_NTZ_OUTPUT_FORMAT takes no AUTO, which the zoned formats take (live-verified).
        if ("TIMESTAMP_NTZ_OUTPUT_FORMAT".equals(paramName) && "AUTO".equalsIgnoreCase(text)) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(text, paramName));
        }
    }

    private static boolean isKnownTimezone(final String name) {
        try {
            ZoneId.of(name, ZoneId.SHORT_IDS);
            return true;
        } catch (final RuntimeException unknown) {
            return false;
        }
    }

    /**
     * Commit an open transaction, as ALTER SESSION SET or UNSET AUTOCOMMIT does.
     *
     * <p>Live-verified, and it is the SET itself that commits, not a change of value: a session inside
     * an explicit transaction that sets AUTOCOMMIT to the value it already held still has its inserted
     * row visible to another session afterwards, and a later ROLLBACK cannot take it back. Every other
     * session parameter — TIMEZONE, QUERY_TAG — leaves the transaction open.
     */
    private void commitOpenTransactionForAutocommit() {
        final TransactionManager transactions = queryExecutor.getTransactionManager();
        if (transactions != null && transactions.hasActiveTransaction()) {
            transactions.commit();
        }
    }

    private void checkAlter(final SecurableObjectType objectType, final String objectName) {
        if (queryExecutor.getSecurityManager() != null) {
            queryExecutor.getSecurityManager().checkAlter(objectType, objectName);
        }
    }

    /** The policy an ALTER found, its absence refused in live's sentence. */
    private <P extends SqlObject> P existingPolicy(final P found, final String kind, final String policyName) {
        if (found == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist(kind, catalog.qualifiedObjectName(policyName)));
        }
        return found;
    }

    /** A policy's new body as written: a quoted body keeps its quotes, the way DESCRIBE shows it. */
    private String policyBody(final FrostlakeParser.PolicyActionContext action) {
        return action.bodyDefinition() != null ? visitor.getOriginalText(action.bodyDefinition())
            : visitor.getOriginalText(action.booleanExpr());
    }

    /** Apply a policy's TAG list or its validated SET / UNSET property list, which only a comment survives. */
    private void applyPolicyProperties(final SqlObject policy, final FrostlakeParser.PolicyActionContext action) {
        if (action.tagSet() != null) {
            applyTagSet(policy, action.tagSet());
        } else if (action.tagUnset() != null) {
            applyTagUnset(policy, action.tagUnset());
        } else {
            policy.setComment(PolicyPropertyList.comment(action));
        }
    }

    /**
     * ALTER USER. What the account checks as it compiles the statement comes first — a property named twice, a value
     * a property cannot take, an UNSET of a property users do not have, a tag that does not exist — and only then is
     * the user looked up: IF EXISTS turns a missing user into success, and every refusal after that stands.
     *
     * @param userName the user the statement names
     * @param action the statement's action
     * @param ifExists whether the statement says IF EXISTS
     */
    private void alterUser(final String userName, final FrostlakeParser.UserActionContext action,
                           final boolean ifExists) {
        if (!action.userProperty().isEmpty()) {
            UserProperties.checkForm(action.userProperty());
        } else if (action.UNSET() != null) {
            UserProperties.checkUnset(action.userUnsetProperty());
        } else if (action.tagSet() != null) {
            for (final FrostlakeParser.TagAssignContext assign : action.tagSet().tagAssign()) {
                referencedTag(assign.qualifiedName());
            }
        } else if (action.tagUnset() != null) {
            for (final FrostlakeParser.QualifiedNameContext tag : action.tagUnset().qualifiedName()) {
                referencedTag(tag);
            }
        }
        if (ifExists && !catalog.hasUser(userName)) {
            logger.debug("ALTER USER IF EXISTS: user {} not found", userName);
            return;
        }
        final User user = catalog.getUser(userName);
        if (action.tagSet() != null) {
            applyTagSet(user, action.tagSet());
        } else if (action.tagUnset() != null) {
            applyTagUnset(user, action.tagUnset());
        } else if (action.RENAME() != null) {
            final String newName = visitor.getText(action.identifier());
            if (catalog.hasUser(newName)) {
                throw new RuntimeException(SqlCompilationError.of("Object '" + newName + "' already exists."));
            }
            catalog.renameUser(userName, newName);
            logger.trace("Renamed user {} to {}", userName, newName);
        } else if (!action.userProperty().isEmpty()) {
            UserProperties.apply(user, action.userProperty());
            logger.trace("Set properties on user: {}", userName);
        } else if (action.UNSET() != null) {
            UserProperties.unset(user, action.userUnsetProperty());
            logger.trace("Unset properties on user: {}", userName);
        } else if (action.COMMENT() != null) {
            user.setComment(visitor.extractStringLiteral(action.STRING_LITERAL()));
            logger.trace("Set comment on user: {}", userName);
        }
    }

    /** Apply a SET TAG action's assignments to a taggable object, validating tag existence and ALLOWED_VALUES. */
    private void applyTagSet(final Taggable target, final FrostlakeParser.TagSetContext set) {
        for (final FrostlakeParser.TagAssignContext assign : set.tagAssign()) {
            final Tag tag = referencedTag(assign.qualifiedName());
            final String value = TagValues.text(assign.qualifiedName(), assign.tagValue(), queryExecutor);
            TagValues.requireAllowed(tag, value);
            target.setTag(tag.getName(), value);
        }
    }

    /** Apply an UNSET TAG action, removing each named tag association from a taggable object. */
    private void applyTagUnset(final Taggable target, final FrostlakeParser.TagUnsetContext unset) {
        for (final FrostlakeParser.QualifiedNameContext qn : unset.qualifiedName()) {
            target.unsetTag(referencedTag(qn).getName());
        }
    }

    /**
     * The tag a SET TAG or an UNSET TAG names, which must exist for either (live-verified on every kind that takes
     * tags). A missing tag named bare is refused by that name alone — Tag 'NOSUCH', Tag '"nosuch"' — and one named by
     * a path by its full path.
     */
    private Tag referencedTag(final FrostlakeParser.QualifiedNameContext name) {
        final String tagName = visitor.getText(name);
        final QualifiedName written = QualifiedName.parse(tagName);
        if (written.size() == 1 && catalog.getCurrentDatabase() != null && catalog.getCurrentSchema() != null
                && !catalog.hasTag(tagName)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Tag", written.last()));
        }
        return catalog.getTag(tagName);
    }

    /** ALTER STAGE … SET FILE_FORMAT = (TYPE = X, …): store the TYPE and the sub-options. */
    private void applyStageFormatOptions(final Stage stage,
                                         final FrostlakeParser.ParenOptionListContext list) {
        for (final FrostlakeParser.ParenOptionContext option : list.parenOption()) {
            final String key = option.optionKey().getText().toUpperCase();
            String value = option.copyOptionValue() != null ? option.copyOptionValue().getText() : "";
            if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
                value = value.substring(1, value.length() - 1);
            }
            if ("TYPE".equals(key)) {
                stage.setFileFormat(value.toUpperCase());
            }
            stage.getFileFormatOptions().put(key, value);
        }
    }

    /**
     * The properties an ALTER TASK … SET names, in the order written: a WAREHOUSE clause and the task options
     * alike.
     */
    private List<TaskPropertySetting> alterTaskSettings(final FrostlakeParser.TaskActionContext action) {
        final List<TaskPropertySetting> settings = new ArrayList<>();
        for (final ParseTree child : action.children) {
            if (child instanceof FrostlakeParser.WarehouseClauseContext) {
                final FrostlakeParser.WarehouseClauseContext clause = (FrostlakeParser.WarehouseClauseContext) child;
                settings.add(TaskProperties.warehouseSetting(clause, warehouseClauseValue(clause)));
            } else if (child instanceof FrostlakeParser.TaskOptionContext) {
                settings.add(TaskProperties.setting((FrostlakeParser.TaskOptionContext) child));
            }
        }
        return settings;
    }

    /**
     * ALTER TASK … ADD AFTER: the named tasks become predecessors too. A scheduled task and a finalizer task
     * take none; every name must be a task, then the task must have no finalizer, and each name must be
     * another task of its schema that is no finalizer; a task with a configuration cannot become a child.
     * One already a predecessor stays once.
     */
    private void addTaskPredecessors(final Task task, final List<FrostlakeParser.QualifiedNameContext> names,
                                     final Schema schema) {
        if (task.getSchedule() != null) {
            throw new RuntimeException("Task " + task.getName() + " cannot have both a schedule and a predecessor.");
        }
        if (task.isFinalizer()) {
            throw new RuntimeException("Task " + schema.getDatabaseName() + "." + schema.getName() + "."
                + task.getName() + " cannot have predecessors as a finalizer task.");
        }
        final TaskProperties properties = new TaskProperties(catalog, schema);
        final List<String> resolved = properties.requirePredecessors(task, names);
        properties.requireConfigFree(task);
        for (final String predecessor : resolved) {
            if (!TaskProperties.hasPredecessor(task, predecessor)) {
                task.addPredecessor(predecessor);
            }
        }
    }

    /** ALTER TASK … REMOVE AFTER: every named task must be a predecessor, and each stops being one. */
    private void removeTaskPredecessors(final Task task, final List<FrostlakeParser.QualifiedNameContext> names,
                                        final Schema schema) {
        final List<String> removed = new TaskProperties(catalog, schema).requireRemovablePredecessors(task, names);
        for (final String name : removed) {
            final Iterator<String> it = task.getPredecessors().iterator();
            while (it.hasNext()) {
                if (QualifiedName.parse(it.next()).last().equalsIgnoreCase(name)) {
                    it.remove();
                }
            }
        }
    }

    /** Refuse an UNSET of a name no task carries, before anything is reset. */
    private static void requireUnsettableTaskParam(final String param) {
        switch (param) {
            case "COMMENT":
            case "SCHEDULE":
            case "WAREHOUSE":
            case "ERROR_INTEGRATION":
            case "ALLOW_OVERLAPPING_EXECUTION":
            case "TARGET_COMPLETION_INTERVAL":
            case "CONFIG":
            case "OVERLAP_POLICY":
            case "FINALIZE":
            case "SUCCESS_INTEGRATION":
                return;
            default:
                if (!SessionParameterCatalog.taskScopeNames().contains(param)) {
                    throw new RuntimeException(SqlCompilationError.invalidPropertyFor(param, "TASK"));
                }
        }
    }

    /**
     * ALTER STAGE … REFRESH [SUBPATH = '…']: the stage's files, or those under the subpath, are registered in its
     * directory table, and every stream on the stage records the files that left and arrived since the last
     * refresh — a file whose size or modification time changed leaves and arrives again.
     */
    private void refreshStageDirectory(final Stage stage, final String stageName,
                                       final FrostlakeParser.StageActionContext action) {
        if (!stage.isDirectoryEnabled()) {
            throw new RuntimeException("DIRECTORY not enabled for the stage " + stage.getName());
        }
        final String subpath = action.STRING_LITERAL() != null
            ? visitor.extractStringLiteral(action.STRING_LITERAL()) : "";
        final Schema schema = catalog.resolveOwningSchema(stageName);
        final String qualified = schema.getDatabaseName() + "." + schema.getName() + "." + stage.getName();
        final Map<String, List<Object>> now = new LinkedHashMap<>();
        for (final Row row : queryExecutor.stageDirectoryRows(qualified)) {
            final String path = String.valueOf(row.getValue(0));
            if (path.startsWith(subpath)) {
                now.put(path, new ArrayList<>(row.getValues()));
            }
        }
        final Map<String, List<Object>> registered = stage.getDirectoryRegistry();
        final List<List<Object>> removed = new ArrayList<>();
        final List<List<Object>> added = new ArrayList<>();
        for (final Map.Entry<String, List<Object>> file : registered.entrySet()) {
            if (file.getKey().startsWith(subpath) && !sameFile(file.getValue(), now.get(file.getKey()))) {
                removed.add(file.getValue());
            }
        }
        for (final Map.Entry<String, List<Object>> file : now.entrySet()) {
            if (!sameFile(file.getValue(), registered.get(file.getKey()))) {
                added.add(file.getValue());
            }
        }
        final Iterator<String> paths = registered.keySet().iterator();
        while (paths.hasNext()) {
            if (paths.next().startsWith(subpath)) {
                paths.remove();
            }
        }
        registered.putAll(now);
        if (queryExecutor.getStreamManager() != null) {
            queryExecutor.getStreamManager().trackRefresh(schema.getDatabaseName(), StreamSourceType.STAGE, qualified,
                removed, added);
        }
        logger.trace("Refreshed directory of stage: {}", stageName);
    }

    /** Whether two directory rows describe the same file: same path, size and modification time. */
    private static boolean sameFile(final List<Object> a, final List<Object> b) {
        return a != null && b != null && Objects.equals(a.get(0), b.get(0)) && Objects.equals(a.get(1), b.get(1))
            && Objects.equals(a.get(2), b.get(2));
    }

    /** The warehouse name from an ALTER TASK … SET WAREHOUSE = … clause (string, identifier, or session var). */
    private String warehouseClauseValue(final FrostlakeParser.WarehouseClauseContext wc) {
        if (wc.STRING_LITERAL() != null) {
            return visitor.extractStringLiteral(wc.STRING_LITERAL());
        }
        if (wc.identifier() != null) {
            return visitor.getText(wc.identifier());
        }
        return wc.getText();
    }

    /** Reset one ALTER TASK … UNSET parameter to its default (null / the model's default value). */
    private void unsetTaskParam(final Task task, final String param) {
        switch (param) {
            case "COMMENT":
                task.setComment(null);
                break;
            case "SCHEDULE":
                task.setSchedule(null);
                task.setScheduleType(null);
                break;
            case "WAREHOUSE":
                task.setWarehouse(null);
                break;
            case "USER_TASK_TIMEOUT_MS":
                task.setUserTaskTimeoutMs(3600000L);
                break;
            case "SUSPEND_TASK_AFTER_NUM_FAILURES":
                task.setSuspendTaskAfterNumFailures(10);
                break;
            case "TASK_AUTO_RETRY_ATTEMPTS":
                task.setTaskAutoRetryAttempts(0);
                break;
            case "USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE":
                task.setUserTaskManagedInitialWarehouseSize(null);
                break;
            case "SERVERLESS_TASK_MAX_STATEMENT_SIZE":
                task.setServerlessTaskMaxStatementSize(null);
                break;
            case "TARGET_COMPLETION_INTERVAL":
                task.setTargetCompletionInterval(null);
                break;
            case "USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS":
                task.setUserTaskMinimumTriggerIntervalInSeconds(30);
                break;
            case "ERROR_INTEGRATION":
                task.setErrorIntegration(null);
                break;
            case "ALLOW_OVERLAPPING_EXECUTION":
                task.setAllowOverlappingExecution(false);
                break;
            case "CONFIG":
                task.setConfig(null);
                break;
            case "OVERLAP_POLICY":
                task.setOverlapPolicy(null);
                break;
            case "FINALIZE":
                task.setFinalizedRootTask(null);
                break;
            case "SUCCESS_INTEGRATION":
                task.setSuccessIntegration(null);
                break;
            case "SERVERLESS_TASK_MIN_STATEMENT_SIZE":
                task.setServerlessTaskMinStatementSize(null);
                break;
            default:
                task.unsetSessionParameter(param);
                break;
        }
        // An unset parameter reads its default again, at no level.
        task.clearParameterSet(param);
    }

    /** WAIT_FOR_COMPLETION waits for a resize, so a SET that names it must also name WAREHOUSE_SIZE. */
    private static void requireSizeForWaitForCompletion(
            final List<FrostlakeParser.WarehousePropertyContext> properties) {
        boolean waits = false;
        boolean sized = false;
        for (final FrostlakeParser.WarehousePropertyContext prop : properties) {
            waits = waits || prop.WAIT_FOR_COMPLETION() != null;
            sized = sized || prop.WAREHOUSE_SIZE() != null;
        }
        if (waits && !sized) {
            throw new RuntimeException("Property WAREHOUSE_SIZE must be specified when using WAIT_FOR_COMPLETION");
        }
    }

    private void applyWarehouseProperty(final Warehouse warehouse,
                                       final FrostlakeParser.WarehousePropertyContext prop) {
        ddlHandler.applyWarehousePropertyPublic(warehouse, prop);
    }

    /**
     * ALTER WAREHOUSE … UNSET: AUTO_SUSPEND and COMMENT read back EMPTY afterwards — live leaves
     * the auto_suspend cell null, not at its creation default — while the remaining declared
     * properties return to their defaults. An unknown name is refused with live's
     * invalid-property wording.
     */
    private void unsetWarehouseProperty(final Warehouse warehouse,
                                        final FrostlakeParser.WarehouseUnsetPropertyContext prop) {
        if (prop.AUTO_SUSPEND() != null) {
            warehouse.setAutoSuspendSeconds(null);
        } else if (prop.COMMENT() != null) {
            warehouse.setComment(null);
        } else if (prop.AUTO_RESUME() != null) {
            warehouse.setAutoResume(true);
        } else if (prop.WAREHOUSE_SIZE() != null) {
            warehouse.setSize(WarehouseSize.X_SMALL);
        } else if (prop.MIN_CLUSTER_COUNT() != null) {
            warehouse.setMinClusterCount(1);
        } else if (prop.MAX_CLUSTER_COUNT() != null) {
            warehouse.setMaxClusterCount(1);
        } else if (prop.SCALING_POLICY() != null) {
            warehouse.setScalingPolicy(ScalingPolicy.STANDARD);
        } else if (prop.RESOURCE_MONITOR() != null) {
            warehouse.setResourceMonitor(null);
        } else if (prop.RESOURCE_CONSTRAINT() != null) {
            // A standard warehouse's constraint is its generation, which only GENERATION unsets.
            if (!"SNOWPARK-OPTIMIZED".equals(warehouse.getWarehouseType())) {
                throw new RuntimeException("Cannot unset resource constraint from '"
                    + warehouse.getResourceConstraint()
                    + "'. Use the GENERATION property to unset warehouse hardware generation.");
            }
            // The default constraint, MEMORY_16X, needs a MEDIUM or larger warehouse.
            if (warehouse.getSize() == WarehouseSize.X_SMALL || warehouse.getSize() == WarehouseSize.SMALL) {
                throw new RuntimeException(SqlCompilationError.of("invalid property combination"
                    + " 'RESOURCE_CONSTRAINT'='MEMORY_16X' and 'WAREHOUSE_SIZE'='"
                    + warehouse.getSize().getDisplayName() + "'"));
            }
            warehouse.setResourceConstraint(null);
        } else if (prop.GENERATION() != null) {
            warehouse.setGeneration("2");
        } else if (prop.identifier() != null) {
            throw new RuntimeException(SqlCompilationError.of("invalid property '"
                + visitor.getText(prop.identifier()).toUpperCase() + "' for 'WAREHOUSE'"));
        }
        // The other declared names (timeouts, acceleration, type, initial state, concurrency) are
        // accepted and keep their engine defaults.
    }

    /** Whether that Cortex search service resolves, without the throw a missing one raises. */
    private boolean cortexSearchServiceExists(final String serviceName) {
        try {
            catalog.resolveCortexSearchService(serviceName);
            return true;
        } catch (final RuntimeException absent) {
            return false;
        }
    }

    /**
     * ALTER CORTEX SEARCH SERVICE … SET / UNSET / SUSPEND / RESUME. SUSPEND and RESUME act on the layer named
     * (INDEXING or SERVING), or on both when none is. Only COMMENT is mutable among the properties: the warehouse, target
     * lag and embedding model are settled when the index is built, and re-setting them would leave the
     * catalog claiming an index the engine never rebuilt.
     */
    private void applyCortexSearchAction(final CortexSearchService service,
                                         final FrostlakeParser.CortexSearchActionContext action) {
        if (action.UNSET() != null) {
            service.setComment(null);
            return;
        }
        if (action.SUSPEND() != null || action.RESUME() != null) {
            final boolean suspend = action.SUSPEND() != null;
            if (action.SERVING() == null) {
                service.setIndexingSuspended(suspend);
            }
            if (action.INDEXING() == null) {
                service.setServingSuspended(suspend);
            }
            return;
        }
        for (final FrostlakeParser.CortexSearchOptionContext option : action.cortexSearchOption()) {
            if (option.COMMENT() != null) {
                service.setComment(visitor.extractStringLiteral(option.STRING_LITERAL()));
            } else {
                throw new RuntimeException(SqlCompilationError.of(
                    "Unsupported ALTER CORTEX SEARCH SERVICE option: " + option.getText()));
            }
        }
    }

    /**
     * Confirm a constrained column exists, reporting an unknown one at the place it was written.
     *
     * <p>The check lives HERE rather than in {@code Table}, which raises the same refusal without a
     * position: a model object holds no SQL text and should not learn to, so only a caller holding the
     * statement can say where the name was. Table's own throw stays as the guard for the callers that
     * hold none — snapshot restore among them.
     */
    private void requireColumn(final Table table, final String colName,
                               final FrostlakeParser.IdentifierContext where) {
        try {
            table.getColumn(colName);
        } catch (final RuntimeException unknown) {
            throw new RuntimeException(SqlCompilationError.invalidIdentifier(
                where.getStart().getLine(), where.getStart().getCharPositionInLine(),
                colName.toUpperCase()), unknown);
        }
    }

    /**
     * The masking policy an attachment names, refused when the name resolves to nothing. Live checks
     * this on the ALTER and the CREATE TABLE path alike, and answers with the fully qualified name.
     *
     * @return the name it was given, so callers can keep the check inline with what they resolve
     */
    private String requireMaskingPolicy(final String policyName) {
        if (catalog.findMaskingPolicy(policyName) == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Masking policy",
                catalog.qualifiedObjectName(policyName)));
        }
        return policyName;
    }

    /**
     * The policy an attachment statement names, refused when the name resolves to nothing. Live makes
     * this its FIRST check on both ADD and DROP — ahead of the ON columns, the argument count and the
     * one-policy rule — and reports the fully qualified name however little of it was written.
     */
    private RowAccessPolicy requireRowAccessPolicy(final String policyName) {
        final RowAccessPolicy policy = catalog.findRowAccessPolicy(policyName);
        if (policy == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Row access policy",
                queryExecutor.getFullyQualifiedTableName(policyName)));
        }
        return policy;
    }

    /**
     * The columns {@code ADD ROW ACCESS POLICY p ON (…)} attaches, having made every check live makes,
     * in live's order: the policy resolves, each ON column resolves, the column count equals the
     * policy's parameter count, and the object carries no policy yet. That last one is stricter than
     * the masking-policy rule it resembles — one row access policy at a time means re-attaching the
     * SAME policy is refused too, where a repeated SET MASKING POLICY is a no-op.
     *
     * @param onColumns  the ON list as written, so an unknown column is reported at its own position
     * @param table      the table whose columns the ON list must name, or null when the target is a
     *                   view (whose recorded columns are the ones its query resolved to at CREATE time)
     * @param objectName the attachment target, as the refusals spell it
     * @param policyName the policy name as written
     * @param attached   whether the target already carries a row access policy
     */
    private List<String> rowAccessPolicyColumns(final FrostlakeParser.IdentifierListContext onColumns,
                                                final Table table, final String objectName,
                                                final String policyName, final boolean attached) {
        final RowAccessPolicy policy = requireRowAccessPolicy(policyName);
        final List<String> cols = new ArrayList<>();
        for (final FrostlakeParser.IdentifierContext id : onColumns.identifier()) {
            final String colName = visitor.getText(id);
            if (table != null) {
                requireColumn(table, colName, id);
            }
            cols.add(colName);
        }
        RowAccessPolicyAttachment.check(policy, table, objectName, policyName, cols);
        if (attached) {
            throw new RuntimeException("Object " + objectName.toUpperCase(Locale.ROOT)
                + " already has a ROW_ACCESS_POLICY. Only one ROW_ACCESS_POLICY is allowed at a time.");
        }
        return cols;
    }

    /**
     * {@code DROP ROW ACCESS POLICY p}: the policy must exist and must be the one attached — dropping
     * a policy that is not attached is an error, not a no-op. Live words the second refusal around
     * TABLE whatever the target is, so a view is named as a table here too (live-verified).
     */
    private void requireRowAccessPolicyAttached(final String policyName, final String objectName,
                                                final String attachedName) {
        requireRowAccessPolicy(policyName);
        final String bare = bareName(policyName);
        // Either name may be qualified — the attachment records the name as it was written — so both
        // are compared bare, the way the catalog matches an attachment to its policy everywhere else.
        if (attachedName == null || !bareName(attachedName).equals(bare)) {
            throw new RuntimeException("Policy " + bare + " is not attached to TABLE "
                + objectName.toUpperCase(Locale.ROOT) + ".");
        }
    }

    /**
     * The purpose a contact is attached for, validated. Live takes exactly three, quoted or not, and
     * names all three back when refusing a fourth — on the UNSET form as well as the SET form.
     */
    private String contactPurpose(final FrostlakeParser.ContactPurposeContext ctx) {
        final String purpose = ctx.identifier() != null
            ? visitor.getText(ctx.identifier())
            : visitor.extractStringLiteral(ctx.STRING_LITERAL()).toUpperCase(Locale.ROOT);
        if (!"SUPPORT".equals(purpose) && !"STEWARD".equals(purpose) && !"ACCESS_APPROVAL".equals(purpose)) {
            throw new RuntimeException(SqlCompilationError.PREFIX + " Purpose type " + purpose
                + " is not valid, please use access_approval, steward and support.");
        }
        return purpose;
    }

    /**
     * ALTER TABLE … DROP [COLUMN] — one or several comma-separated names, all dropped or none
     * (live-verified). Each name carries its OWN optional IF EXISTS: the one written after DROP covers the
     * first name only, a later name's follows its comma. The whole list is judged before anything changes:
     * <ol>
     *   <li>in written order, a name the table lacks is refused unless its own IF EXISTS forgives it —
     *       {@code DROP COLUMN x, nosuch} drops nothing and names NOSUCH;</li>
     *   <li>then a list that would leave no column is refused — {@code cannot drop all the columns of a
     *       table}, unpositioned — even when every name is covered by IF EXISTS;</li>
     *   <li>then each named column goes, once however often it is named, and its value leaves every row.</li>
     * </ol>
     */
    private void dropColumns(final String tableName, final Table table,
                             final FrostlakeParser.TableActionContext action) {
        final List<String> named = new ArrayList<>();
        final List<Boolean> forgiven = new ArrayList<>();
        named.add(visitor.getText(action.identifier(0)));
        forgiven.add(action.if_exists() != null);
        for (final FrostlakeParser.AlterDropColumnItemContext item : action.alterDropColumnItem()) {
            named.add(visitor.getText(item.identifier()));
            forgiven.add(item.if_exists() != null);
        }
        final Set<String> dropping = new LinkedHashSet<>();
        for (int i = 0; i < named.size(); i++) {
            final String colName = named.get(i);
            // A name reaches only the column it spells: DROP COLUMN "a" beside an unquoted A is refused as
            // column 'a' does not exist, live-verified.
            if (!table.hasColumnExactly(colName)) {
                if (forgiven.get(i)) {
                    logger.debug("Column does not exist (IF EXISTS): {}", colName);
                    continue;
                }
                throw new RuntimeException(SqlCompilationError.columnDoesNotExist(colName));
            }
            dropping.add(table.columnAt(table.getColumnIndex(colName)).getName());
        }
        if (!dropping.isEmpty() && dropping.size() >= table.columnCount()) {
            throw new RuntimeException(SqlCompilationError.of("cannot drop all the columns of a table"));
        }
        final String fullyQualifiedName = queryExecutor.getFullyQualifiedTableName(tableName);
        for (final String colName : dropping) {
            final int index = table.getColumnIndex(colName);
            table.dropColumn(colName);
            queryExecutor.dropColumnValues(fullyQualifiedName, index, table.isTemporary());
            logger.trace("Dropped column {} from table {}", colName, tableName);
        }
    }

    /**
     * The three DATA METRIC FUNCTION actions. The metric must be a system one — live resolves only
     * SNOWFLAKE.CORE names and refuses a bare one — every named column must exist, and a DROP that
     * finds no attachment is refused while a repeated ADD is silently ignored.
     */
    private void applyDataMetricAction(final Table table,
                                       final FrostlakeParser.TableActionContext action) {
        final String metric = visitor.getText(action.qualifiedName());
        if (!DataMetricFunctions.exists(metric)) {
            // A metric that is not a system one is refused without a privilege hint (live-verified).
            throw new RuntimeException(SqlCompilationError.doesNotExistWithoutHint("Function",
                metric.toUpperCase(Locale.ROOT)));
        }
        final List<String> columns = new ArrayList<>();
        if (action.identifierList() != null) {
            for (final FrostlakeParser.IdentifierContext id : action.identifierList().identifier()) {
                final String colName = visitor.getText(id);
                if (!table.hasColumn(colName)) {
                    throw new RuntimeException(SqlCompilationError.columnDoesNotExist(
                        colName.toUpperCase(Locale.ROOT)));
                }
                columns.add(colName);
            }
        }
        if (action.ADD() != null) {
            table.addDataMetric(new DataMetricAttachment(metric.toUpperCase(Locale.ROOT), columns));
            return;
        }
        if (action.MODIFY() != null) {
            final DataMetricAttachment attachment = table.findDataMetric(
                metric.toUpperCase(Locale.ROOT), columns);
            if (attachment != null) {
                attachment.setSuspended(action.SUSPEND() != null);
            }
            return;
        }
        if (!table.dropDataMetric(metric.toUpperCase(Locale.ROOT), columns)) {
            throw new RuntimeException(SqlCompilationError.PREFIX + " Data metric function "
                + DataMetricFunctions.bareName(metric) + " is not attached to Table "
                + table.getName().toUpperCase(Locale.ROOT) + ".");
        }
    }

    /**
     * The bare {@code ADD SEARCH OPTIMIZATION}: an EQUALITY expression per column, skipping the
     * VARIANT ones — which {@code EQUALITY(*)} does include (both live-verified).
     */
    private void addDefaultSearchOptimization(final Table table) {
        for (final TableColumn column : table.getColumns()) {
            if (!(column.getDataType() instanceof VariantType)) {
                table.addSearchOptimization("EQUALITY", column.getName(),
                    SqlTypeNames.canonical(column.getDataType()));
            }
        }
    }

    /** One {@code ON method(target)} entry, with the star form expanded over every column. */
    private void addSearchOptimization(final Table table,
                                       final FrostlakeParser.SearchOptimizationTargetContext ctx) {
        final String method = visitor.getText(ctx.identifier()).toUpperCase(Locale.ROOT);
        if (ctx.STAR() != null) {
            for (final TableColumn column : table.getColumns()) {
                table.addSearchOptimization(searchOptimizationMethod(method), column.getName(),
                    SqlTypeNames.canonical(column.getDataType()));
            }
            return;
        }
        final String target = visitor.getText(ctx.qualifiedName());
        // Live points these refusals at the ARGUMENT inside the parens, not at the method name.
        final int line = ctx.qualifiedName().getStart().getLine();
        final int position = ctx.qualifiedName().getStart().getCharPositionInLine();
        if (!table.hasColumn(target)) {
            throw new RuntimeException(SqlCompilationError.at(line, position,
                "invalid identifier '" + SEARCH_SOURCE_ALIAS + "."
                + target.toUpperCase(Locale.ROOT) + "'"));
        }
        final TableColumn column = table.getColumn(target);
        // GEO indexes only a geography, so live refuses it over anything else.
        if ("GEO".equals(method) && !(column.getDataType() instanceof GeographyType)) {
            throw new RuntimeException(SqlCompilationError.at(line, position,
                "Expression GEO(" + SEARCH_SOURCE_ALIAS + "." + column.getName()
                + ") cannot be used in search optimization."));
        }
        table.addSearchOptimization(searchOptimizationMethod(method), column.getName(),
            SqlTypeNames.canonical(column.getDataType()));
    }

    /** FULL_TEXT reads back with the analyzer it used; the other methods read back as written. */
    private String searchOptimizationMethod(final String method) {
        return "FULL_TEXT".equals(method) ? "FULL_TEXT DEFAULT_ANALYZER" : method;
    }

    /** How live spells the source table inside a search-optimization expression. */
    private static final String SEARCH_SOURCE_ALIAS = "IDX_SRC_TABLE";

    /** One {@code DROP … ON …} entry: an expression number, or the method and target. */
    private void dropSearchOptimization(final Table table,
                                        final FrostlakeParser.SearchOptimizationDropContext ctx) {
        if (ctx.INTEGER_LITERAL() != null) {
            table.dropSearchOptimization(Integer.parseInt(ctx.INTEGER_LITERAL().getText()));
            return;
        }
        final FrostlakeParser.SearchOptimizationTargetContext target = ctx.searchOptimizationTarget();
        final String method = searchOptimizationMethod(
            visitor.getText(target.identifier()).toUpperCase(Locale.ROOT));
        final String column = target.STAR() != null ? "*" : visitor.getText(target.qualifiedName());
        // Dropping an expression that is not configured is refused — unless the table has NO search
        // optimization at all, where the whole statement is a silent no-op (live-verified).
        if (!table.dropSearchOptimization(method, column) && table.hasSearchOptimization()) {
            throw new RuntimeException(SqlCompilationError.at(
                target.qualifiedName().getStart().getLine(),
                target.qualifiedName().getStart().getCharPositionInLine(),
                "Expression " + method + "(" + table.getName().toUpperCase(Locale.ROOT) + "."
                + column.toUpperCase(Locale.ROOT) + ") is not indexed by search optimization."));
        }
    }

    /** The last part of a qualified name, upper-cased: how the policy refusals spell a policy. */
    private String bareName(final String qualifiedName) {
        return QualifiedName.parse(qualifiedName).last().toUpperCase(Locale.ROOT);
    }

    /**
     * Whether an attached masking policy and a freshly resolved one are the same policy — which is
     * what live judges, not the spelling: re-attaching a policy named bare, schema-qualified or in
     * full is a no-op every way round. An attachment recorded before names were qualified (a restored
     * snapshot) carries only the last part, so those are compared bare.
     */
    private boolean sameMaskingPolicy(final String attached, final String resolved) {
        return QualifiedName.parse(attached).parts().length == 3
            ? attached.equalsIgnoreCase(resolved)
            : bareName(attached).equals(bareName(resolved));
    }
}
