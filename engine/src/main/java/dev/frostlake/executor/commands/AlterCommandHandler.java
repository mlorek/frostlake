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

import dev.frostlake.executor.ClusteringKeySpelling;
import dev.frostlake.executor.ConditionalDdlOutcome;
import dev.frostlake.executor.ContainerParameterCatalog;
import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.InformationSchemaGuard;
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
import dev.frostlake.executor.WarehouseReference;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.IntervalCasts;
import dev.frostlake.executor.expressions.PolicyObjectTagCalls;
import dev.frostlake.executor.procedural.ProceduralException;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.ColumnRenames;
import dev.frostlake.metastore.DataMetricFunctions;
import dev.frostlake.metastore.InstanceFamilies;
import dev.frostlake.metastore.MaterializedViewValidity;
import dev.frostlake.metastore.NameTakenException;
import dev.frostlake.metastore.NoCurrentDatabaseRefusal;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.SqlObject;
import dev.frostlake.metastore.StreamSources;
import dev.frostlake.metastore.SystemDatabase;
import dev.frostlake.metastore.TableShadows;
import dev.frostlake.metastore.TagAssignments;
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
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.ProjectionPolicy;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.RowAccessPolicy;
import dev.frostlake.metastore.model.ScalingPolicy;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurableObjectType;
import dev.frostlake.metastore.model.SequenceDefault;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskState;
import dev.frostlake.metastore.model.UniqueConstraint;
import dev.frostlake.metastore.model.User;
import dev.frostlake.metastore.model.View;
import dev.frostlake.metastore.model.Warehouse;
import dev.frostlake.metastore.model.WarehouseSize;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.TableStorage;
import dev.frostlake.task.TaskScheduler;
import dev.frostlake.transaction.TransactionManager;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.DeclaredTypeFold;
import dev.frostlake.types.GeographyType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredTypes;
import dev.frostlake.types.UuidType;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorType;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

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
        if (ctx.externalTableAction() != null) {
            return new ExternalTableAlteration(catalog, queryExecutor).run(ctx);
        }
        final boolean ifExists = ctx.if_exists() != null;
        // Set by the branches that gate IF EXISTS themselves (ALTER TABLE, ALTER COMPUTE POOL)
        // instead of leaning on the blanket catch below.
        boolean branchHandlesIfExists = false;
        // The object altered, when INFORMATION_SCHEMA reports a LAST_DDL for it: stamped once the action has
        // succeeded, so a refused ALTER moves nothing (see SqlObject.markDdl).
        SqlObject altered = null;
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
                SystemDatabase.refuseChange(database.getName());
                altered = database;

                if (ctx.databaseAction().RENAME() != null) {
                    // Renaming an object needs OWNERSHIP of it, as replacing or dropping it does.
                    queryExecutor.requireOwnership("DATABASE", new String[] {dbName}, null);
                    final String newName = visitor.getText(ctx.databaseAction().identifier());
                    catalog.renameDatabase(dbName, newName);
                    queryExecutor.renameDatabaseStorage(dbName, newName);
                    logger.trace("Renamed database {} to {}", dbName, newName);
                } else if (ctx.databaseAction().SWAP() != null) {
                    swapDatabases(database, visitor.getText(ctx.databaseAction().identifier()));
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
                if (ctx.schemaAction() != null && ctx.schemaAction().SWAP() != null) {
                    // The schema to swap with is looked for first: a missing one is refused, IF EXISTS or not, and
                    // IF EXISTS forgives only the named schema's absence (live-verified).
                    branchHandlesIfExists = true;
                    catalog.resolveSchema(QualifiedName.of(
                        catalog.withoutAccount(qualifiedNameParts(ctx.schemaAction().qualifiedName()), 2)));
                    try {
                        catalog.resolveSchema(
                            QualifiedName.of(catalog.withoutAccount(qualifiedNameParts(ctx.qualifiedName()), 2)));
                    } catch (final RuntimeException missing) {
                        if (ifExists) {
                            logger.debug("Schema does not exist (IF EXISTS): {}", schemaName);
                            return null;
                        }
                        throw missing;
                    }
                }
                final Schema schema = catalog.resolveSchema(
                    QualifiedName.of(catalog.withoutAccount(qualifiedNameParts(ctx.qualifiedName()), 2)));
                checkAlter(SecurableObjectType.SCHEMA, schemaName);
                if ("INFORMATION_SCHEMA".equals(schema.getName())) {
                    // INFORMATION_SCHEMA is read-only: no ALTER changes it - not its name, its comment, its
                    // parameters, its tags or its managed access.
                    throw new RuntimeException(SqlAccessControlError.insufficientPrivileges("schema",
                        "INFORMATION_SCHEMA"));
                }
                SystemDatabase.refuseChange(schema.getDatabaseName());
                altered = schema;
                if (ctx.schemaAction() != null && ctx.schemaAction().RENAME() == null
                        && ctx.schemaAction().SWAP() == null) {
                    queryExecutor.requirePrivilege(Privilege.MODIFY, "SCHEMA",
                        new String[] {schema.getDatabaseName(), schema.getName()}, null);
                }
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

                if (ctx.schemaAction().SWAP() != null) {
                    queryExecutor.requireOwnership("SCHEMA",
                        catalog.withoutAccount(qualifiedNameParts(ctx.qualifiedName()), 2), null);
                    swapSchemas(schema,
                        catalog.withoutAccount(qualifiedNameParts(ctx.schemaAction().qualifiedName()), 2));
                } else if (ctx.schemaAction().RENAME() != null) {
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
                    // Nor is a schema moved into the SNOWFLAKE database.
                    SystemDatabase.refuseChange(targetDatabase);
                    catalog.moveSchema(schema, targetDatabase, target);
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
                    requirePolicyRenameTarget(action.qualifiedName(), Privilege.CREATE_MASKING_POLICY);
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
                    requirePolicyRenameTarget(action.qualifiedName(), Privilege.CREATE_JOIN_POLICY);
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
                    requirePolicyRenameTarget(action.qualifiedName(), Privilege.CREATE_AGGREGATION_POLICY);
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
                    requirePolicyRenameTarget(action.qualifiedName(), Privilege.CREATE_PROJECTION_POLICY);
                    catalog.renameProjectionPolicy(policyName, visitor.getText(action.qualifiedName()));
                } else if (action.BODY() != null) {
                    final String newBody = policyBody(action);
                    PolicyBodyCheck.requireNumericLimits(newBody);
                    PolicyObjectTagCalls.refuseIn(action.bodyDefinition() != null ? action.bodyDefinition()
                        : action.booleanExpr(), PolicyObjectTagCalls.ON_CURRENT_TABLE);
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
                    requirePolicyRenameTarget(action.qualifiedName(), Privilege.CREATE_ROW_ACCESS_POLICY);
                    catalog.renameRowAccessPolicy(policyName, visitor.getText(action.qualifiedName()));
                } else if (action.BODY() != null) {
                    final String newBody = policyBody(action);
                    PolicyObjectTagCalls.refuseIn(action.bodyDefinition() != null ? action.bodyDefinition()
                        : action.booleanExpr(), PolicyObjectTagCalls.ON_CURRENT_COLUMN);
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
            } else if (ctx.DYNAMIC() != null && ctx.TABLE() != null && ctx.dynamicTableAction().RENAME() != null) {
                // IF EXISTS forgives only the table's absence: a taken new name still refuses (live-verified).
                branchHandlesIfExists = true;
                final String[] renamedParts = qualifiedNameParts(ctx.qualifiedName());
                final String[] newParts =
                    queryExecutor.resolveWholeObjectNameParts(ctx.dynamicTableAction().wholeObjectName());
                if (ddlHandler.findDynamicTable(renamedParts) != null) {
                    checkAlter(SecurableObjectType.DYNAMIC_TABLE, visitor.getText(ctx.qualifiedName()));
                    ddlHandler.renameDynamicTable(renamedParts, newParts);
                } else if (alterTargetTableExists(renamedParts)) {
                    // The statement renames whatever table the name finds: a plain one as ALTER TABLE does.
                    final String plainName = visitor.getText(ctx.qualifiedName());
                    final Table plain = catalog.resolveTable(QualifiedName.of(renamedParts));
                    checkAlter(SecurableObjectType.TABLE, plainName);
                    renameTable(plainName, renamedParts, plain, newParts);
                } else if (!ifExists) {
                    ddlHandler.resolveDynamicTable(ctx.qualifiedName());
                }
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
                final String[] serviceParts = qualifiedNameParts(ctx.qualifiedName());
                // IF EXISTS forgives only the service's absence, as it does for a compute pool.
                branchHandlesIfExists = true;
                // What the statement sets is judged before the service is looked for (live-verified).
                rejectCortexSearchSettings(ctx.cortexSearchAction());
                if (ifExists && !catalog.hasCortexSearchService(serviceParts)) {
                    // A service that is not there is named in the statement's answer.
                    ConditionalDdlOutcome.alterSkipped();
                    logger.debug("Cortex search service does not exist (IF EXISTS): {}", serviceParts[serviceParts.length - 1]);
                } else {
                    applyCortexSearchAction(catalog.resolveCortexSearchService(serviceParts),
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
                if (ctx.ICEBERG() != null) {
                    requireIcebergAction(ctx.tableAction());
                }
                final String[] tableParts = queryExecutor.alterTargetNameParts(ctx);
                InformationSchemaGuard.refuseWrite(catalog, tableParts);
                if (ctx.tableAction() != null && ctx.tableAction().RENAME() != null
                        && ctx.tableAction().COLUMN() == null && ctx.tableAction().CONSTRAINT() == null
                        && !alterTargetTableExists(tableParts) && ddlHandler.findDynamicTable(tableParts) != null) {
                    // A dynamic table is renamed by this statement too (live-verified).
                    checkAlter(SecurableObjectType.DYNAMIC_TABLE, tableName);
                    ddlHandler.renameDynamicTable(tableParts,
                        queryExecutor.resolveWholeObjectNameParts(ctx.tableAction().wholeObjectName()));
                } else if (ifExists && !alterTargetTableExists(tableParts)) {
                    // IF EXISTS forgives ONLY the named table's absence, live-verified: a missing
                    // SCHEMA still errors (alterTargetTableExists resolves it first), and an error
                    // the action itself raises — an unknown column, a bad type change — propagates.
                    // The table a SWAP names is looked for first: a missing one is refused all the same.
                    if (ctx.tableAction() != null && ctx.tableAction().SWAP() != null) {
                        final String other = visitor.getText(ctx.tableAction().qualifiedName());
                        catalog.refuseOtherRelationKind(other);
                        catalog.resolveTable(other);
                    }
                    // A CLUSTER BY action's expressions are still compiled, and with no table no
                    // column reference can resolve (live-verified, positioned at the reference).
                    if (ctx.tableAction() != null && ctx.tableAction().CLUSTER() != null) {
                        ClusterKeyValidation.requireResolvable(
                            ctx.tableAction().expressionList().expression(), null);
                    }
                    logger.debug("Table does not exist (IF EXISTS): {}", tableName);
                } else {
                    if (tableParts.length == 1 && ctx.tableAction() != null && ctx.tableAction().RENAME() != null
                            && ctx.tableAction().COLUMN() != null && !alterTargetTableExists(tableParts)) {
                        // RENAME COLUMN names a missing table as it was written, where the table's other
                        // actions name it by its full path (live-verified).
                        throw new RuntimeException(SqlCompilationError.doesNotExist("Table", tableParts[0]));
                    }
                    final Table table = catalog.resolveTable(QualifiedName.of(tableParts));
                    requireStatementOfTableKind(ctx, table);
                    checkAlter(SecurableObjectType.TABLE, tableName);
                    if (ctx.tableAction() != null && ctx.tableAction().SWAP() == null
                            && ctx.tableAction().wholeObjectName() == null) {
                        // Every change but a rename or a swap, which ask for OWNERSHIP, asks for MODIFY.
                        queryExecutor.requirePrivilege(Privilege.MODIFY, "TABLE", tableParts, null);
                    }
                    refuseEventTableShapeChange(table, ctx.tableAction());
                    altered = table;

                    if (ctx.tableAction().SWAP() != null) {
                        // ALTER TABLE a SWAP WITH b — exchange the two tables' row storage, which needs
                        // OWNERSHIP of both.
                        queryExecutor.requireOwnership("TABLE", tableParts, null);
                        final String other = visitor.getText(ctx.tableAction().qualifiedName());
                        // A view or another relation named as the other table is refused by its kind first.
                        catalog.refuseOtherRelationKind(other);
                        queryExecutor.requireOwnership("TABLE", qualifiedNameParts(ctx.tableAction().qualifiedName()),
                            null);
                        queryExecutor.swapTables(tableName, other);
                        // A swap is DDL on both tables; the other now answers to this table's name.
                        catalog.resolveTable(QualifiedName.of(tableParts)).markDdl(catalog.currentUserForDdl());
                        logger.trace("Swapped table {} with {}", tableName, other);
                    } else if (ctx.tableAction().RENAME() != null && ctx.tableAction().COLUMN() == null
                            && ctx.tableAction().CONSTRAINT() == null) {
                        renameTable(tableName, tableParts, table,
                            queryExecutor.resolveWholeObjectNameParts(ctx.tableAction().wholeObjectName()));
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
                        // Every column is judged before any is added, so a refused one leaves the table as it was;
                        // a table that holds rows takes no column it cannot fill (live-verified).
                        final String storageName = queryExecutor.getFullyQualifiedTableName(tableName);
                        final boolean holdsRows = AddedColumns.holdsRows(queryExecutor, storageName);
                        final List<TableColumn> added = new ArrayList<>();
                        final Set<String> addedNames = new HashSet<>();
                        for (int i = 0; i < defs.size(); i++) {
                            final FrostlakeParser.ColumnDefContext colDef = defs.get(i);
                            final String colName = ParseTreeText.namePartText(colDef.columnDefName());
                            // A column of EXACTLY this name exists or not: "n" beside an unquoted N is a new
                            // column, live-verified.
                            if (defIfNotExists.get(i) && table.hasColumnExactly(colName)) {
                                logger.debug("Column already exists (IF NOT EXISTS): {}", colName);
                                continue;
                            }
                            if (table.hasColumnExactly(colName) || addedNames.contains(colName)) {
                                // The account's own shape — lowercase 'column', no period, and the name as
                                // canonical, unquoted: 'N' for N or "N", 'n' for "n".
                                throw new RuntimeException(SqlCompilationError.of(
                                    "column '" + colName + "' already exists"));
                            }
                            if (holdsRows) {
                                AddedColumns.refuseNonConstantDefault(colDef, colName, table.getName());
                            }
                            final TableColumn newColumn =
                                ddlHandler.getColumnParser().parseSingleColumnDef(colDef, true,
                                    table.getIcebergMetadata() != null);
                            if (holdsRows) {
                                AddedColumns.refuseUnfillableNotNull(newColumn, table.getName());
                            }
                            added.add(newColumn);
                            addedNames.add(colName);
                        }
                        DefaultDdlCollation.apply(table, added, catalog.resolveOwningSchema(tableName), catalog);
                        for (final TableColumn newColumn : added) {
                            table.addColumn(newColumn);
                            queryExecutor.backfillColumn(storageName, table, newColumn);
                            logger.trace("Added column {} to table {}", newColumn.getName(), tableName);
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
                        // DROP CONSTRAINT — any named constraint (check, primary key, unique, foreign key)
                        // is dropped by name, and a name no constraint carries is refused rather than
                        // ignored (live-verified).
                        final String constraintName = visitor.getText(ctx.tableAction().identifier(0));
                        // A key takes the foreign keys other tables hold on it; under RESTRICT a referenced key
                        // is not dropped (live-verified).
                        final List<String> key = table.keyColumnsNamed(constraintName);
                        final boolean primaryKey = key != null && !table.getPrimaryKeys().isEmpty()
                            && constraintName.equalsIgnoreCase(table.primaryKeyConstraintName());
                        refuseReferencedKey(ctx.tableAction(), tableName, table, key, primaryKey);
                        if (!table.dropCheckConstraint(constraintName) && !table.dropNamedConstraint(constraintName)) {
                            throw new RuntimeException(SqlCompilationError.of("constraint '"
                                + constraintName.toUpperCase(Locale.ROOT) + "' does not exist"));
                        }
                        releaseKey(tableName, table, key, primaryKey);
                        logger.trace("Dropped constraint {} from table {}", constraintName, tableName);
                    } else if (ctx.tableAction().DROP() != null && ctx.tableAction().CLUSTERING() != null) {
                        // DROP CLUSTERING KEY
                        table.setClusterKeys(null);
                        logger.trace("Dropped clustering key from table {}", tableName);
                    } else if (ctx.tableAction().DROP() != null && ctx.tableAction().PRIMARY() != null) {
                        // DROP PRIMARY KEY — and with the key go the foreign keys other tables hold on it, those
                        // on its UNIQUE keys staying (live-verified: the child keeps its column and loses the
                        // constraint).
                        final List<String> key = new ArrayList<>(table.getPrimaryKeys());
                        refuseReferencedKey(ctx.tableAction(), tableName, table, key, true);
                        table.dropPrimaryKey();
                        releaseKey(tableName, table, key, true);
                        logger.trace("Dropped PRIMARY KEY from table {}", tableName);
                    } else if (ctx.tableAction().DROP() != null && ctx.tableAction().UNIQUE() != null) {
                        // DROP UNIQUE (cols)
                        final List<String> columns = new ArrayList<>();
                        for (final FrostlakeParser.IdentifierContext idCtx : ctx.tableAction().identifierList().identifier()) {
                            columns.add(visitor.getText(idCtx));
                        }
                        refuseReferencedKey(ctx.tableAction(), tableName, table, columns, false);
                        table.dropUnique(columns);
                        releaseKey(tableName, table, columns, false);
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
                        table.markConstraintRenamed(oldName, newName);
                        logger.trace("Renamed constraint {} to {} on table {}", oldName, newName, tableName);
                    } else if (ctx.tableAction().CONSTRAINT() != null
                            && !ctx.tableAction().constraintProperty().isEmpty()) {
                        // {ALTER | MODIFY} CONSTRAINT name RELY | NORELY | [NOT] ENFORCED, several at once.
                        applyConstraintProperties(table, visitor.getText(ctx.tableAction().identifier(0)),
                            ctx.tableAction().constraintProperty());
                        table.markConstraintAltered(visitor.getText(ctx.tableAction().identifier(0)));
                    } else if (ctx.tableAction().RENAME() != null && ctx.tableAction().COLUMN() != null) {
                        // RENAME COLUMN: the column keeps every policy, tag and constraint it has, and the foreign
                        // keys naming it in other tables follow it.
                        final String oldName = visitor.getText(ctx.tableAction().identifier(0));
                        final String newName = visitor.getText(ctx.tableAction().identifier(1));
                        // A column a clustering key reads keeps its name, whatever the new one is (live-verified).
                        if (table.hasColumnExactly(oldName)) {
                            final String keyed = table.columnAt(table.getColumnIndex(oldName)).getName();
                            if (table.isClusteringKeyColumn(keyed)) {
                                throw new RuntimeException("Cannot rename column '" + keyed
                                    + "' which belongs to a clustering key\n");
                            }
                        }
                        final String was = table.renameColumn(oldName, newName);
                        MaterializedViewValidity.baseTableAltered(catalog, table);
                        final Schema home = StreamSources.holderOf(catalog, table);
                        if (home != null) {
                            ColumnRenames.followInForeignKeys(catalog, home, table, was, newName);
                        }
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
                    } else if ((ctx.tableAction().ALTER() != null || ctx.tableAction().MODIFY() != null)
                            && ctx.tableAction().PROJECTION() != null) {
                        // {ALTER | MODIFY} [COLUMN] col SET/UNSET PROJECTION POLICY — one per column, and
                        // FORCE is what lets a different one replace it (live-verified, same rule as masking).
                        final String colName = visitor.getText(ctx.tableAction().identifier(0));
                        // The policy a SET names is resolved before the column (live-verified).
                        final String policyName = ctx.tableAction().UNSET() != null ? null
                            : requireProjectionPolicy(visitor.getText(ctx.tableAction().qualifiedName()));
                        requireColumn(table, colName, ctx.tableAction().identifier(0));
                        final TableColumn col = table.getColumn(colName);
                        if (policyName == null) {
                            col.setProjectionPolicyName(null);
                        } else {
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
                        // Recorded in full, the way live reports an attachment however little of the name the
                        // statement wrote — and so that re-attaching the SAME policy under a different spelling
                        // stays the no-op live makes it. The policy a SET names is resolved before the column
                        // (live-verified).
                        final String policyName = ctx.tableAction().UNSET() != null ? null
                            : catalog.qualifiedObjectName(
                                requireMaskingPolicy(visitor.getText(ctx.tableAction().qualifiedName())));
                        requireColumn(table, colName, ctx.tableAction().identifier(0));
                        final TableColumn col = table.getColumn(colName);
                        if (policyName == null) {
                            // Detaching what is not attached is a no-op, not an error (live-verified) —
                            // the opposite of the row access policy's DROP.
                            col.setMaskingPolicyName(null);
                            col.setMaskingPolicyArguments(null);
                        } else {
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
                            // A column another policy reads is masked only by an exempt policy (MaskedArguments).
                            MaskedArguments.requireMaskable(table, col, catalog.findMaskingPolicy(policyName));
                            if (ctx.tableAction().identifierList() != null) {
                                // Conditional policy: a USING argument column that is itself masked is
                                // rejected, unless its own policy is exempt (live-verified).
                                for (final FrostlakeParser.IdentifierContext argCtx
                                        : ctx.tableAction().identifierList().identifier()) {
                                    final String argName = visitor.getText(argCtx);
                                    final TableColumn argCol = table.hasColumn(argName) ? table.getColumn(argName) : null;
                                    if (argCol != null && argCol != col) {
                                        MaskedArguments.requireReadable(catalog, argCol);
                                    }
                                }
                            }
                            MaskingPolicyAttachment.requireTypeMatch(
                                catalog.findMaskingPolicy(policyName), col);
                            col.setMaskingPolicyName(policyName);
                            // A conditional policy reads its further arguments from the columns USING names, in
                            // order; a policy attached without USING reads only the column.
                            final List<String> arguments = new ArrayList<>();
                            if (ctx.tableAction().identifierList() != null) {
                                for (final FrostlakeParser.IdentifierContext argCtx
                                        : ctx.tableAction().identifierList().identifier()) {
                                    arguments.add(visitor.getText(argCtx));
                                }
                            }
                            col.setMaskingPolicyArguments(arguments);
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
                        final List<String> clusterKeys = ClusteringKeySpelling.keys(ctx.tableAction().expressionList());
                        table.setClusterKeys(clusterKeys, ClusteringKeySpelling.written(
                            ctx.tableAction().clusterKeyOrder(), ctx.tableAction().expressionList()));
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
                        } else if (keyLeftOut(table, constraintCtx, constraintName)) {
                            // A key the account cannot keep as written is left out and the statement succeeds,
                            // as in a CREATE TABLE; a foreign key's parent has to be there all the same.
                            if (constraintCtx.FOREIGN() != null) {
                                ForeignKeyTargets.requireParent(catalog, visitor.getText(constraintCtx.qualifiedName()));
                            }
                            logger.trace("Left out a constraint of table {}", tableName);
                        } else if (constraintCtx.PRIMARY() != null) {
                            // PRIMARY KEY constraint - add to table's primary keys list. A second
                            // key refuses with the account's own sentence, bare table name.
                            if (!table.getPrimaryKeys().isEmpty()) {
                                throw new RuntimeException(SqlCompilationError.of(
                                    "primary key already exists for table '"
                                    + table.getName().toUpperCase(Locale.ROOT) + "'"));
                            }
                            final List<String> columns = new ArrayList<>();
                            for (final FrostlakeParser.IdentifierContext idCtx : constraintCtx.identifierList(0).identifier()) {
                                requireColumn(table, visitor.getText(idCtx), idCtx);
                                columns.add(visitor.getText(idCtx));
                            }
                            table.addPrimaryKeyConstraint(columns);
                            applyRely(table, columns, constraintCtx);
                            // An explicit CONSTRAINT <name> names the key; without one it auto-names itself.
                            table.setPrimaryKeyConstraintName(constraintName);
                            // Created now, where a key the CREATE TABLE declared dates from the table.
                            table.markConstraintCreated(table.primaryKeyConstraintName());
                            logger.trace("Added PRIMARY KEY constraint to table {}: {}", tableName, columns);
                        } else if (constraintCtx.UNIQUE() != null) {
                            // UNIQUE constraint — one constraint over every listed column, not one each.
                            final List<String> columns = new ArrayList<>();
                            for (final FrostlakeParser.IdentifierContext idCtx : constraintCtx.identifierList(0).identifier()) {
                                requireColumn(table, visitor.getText(idCtx), idCtx);
                                columns.add(visitor.getText(idCtx));
                            }
                            final UniqueConstraint unique = new UniqueConstraint(constraintName, columns);
                            table.addUniqueConstraint(unique);
                            applyRely(table, columns, constraintCtx);
                            table.markConstraintCreated(unique.getConstraintName());
                            logger.trace("Added UNIQUE constraint to table {}: {}", tableName, columns);
                        } else if (constraintCtx.FOREIGN() != null) {
                            // FOREIGN KEY constraint
                            final List<String> columns = new ArrayList<>();
                            for (final FrostlakeParser.IdentifierContext idCtx : constraintCtx.identifierList(0).identifier()) {
                                columns.add(visitor.getText(idCtx));
                            }
                            final String referencedTable = visitor.getText(constraintCtx.qualifiedName());
                            final List<String> referencedColumns = new ArrayList<>();
                            if (constraintCtx.identifierList().size() > 1) {
                                for (final FrostlakeParser.IdentifierContext idCtx
                                        : constraintCtx.identifierList(1).identifier()) {
                                    referencedColumns.add(visitor.getText(idCtx));
                                }
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
                                    throw new RuntimeException("invalid identifier '" + fkColumn.toUpperCase(Locale.ROOT) + "'");
                                }
                            }
                            // A nameless constraint is auto-named SYS_CONSTRAINT_<uuid> by the constructor. The key
                            // has to point at a key of its parent, whose columns it takes when it names none.
                            final ForeignKeyConstraint fk = ForeignKeyTargets.settled(catalog,
                                catalog.resolveOwningSchema(tableName), table,
                                new ForeignKeyConstraint(constraintName, columns, referencedTable, referencedColumns,
                                    onDelete, onUpdate,
                                    KeyConstraintProperties.read(constraintCtx.keyConstraintProperty()).rely()));
                            table.addForeignKey(fk);
                            table.markConstraintCreated(fk.getConstraintName());
                            logger.trace("Added FOREIGN KEY constraint to table {}: {} -> {}", tableName, columns,
                                referencedTable);
                        }
                    } else if (ctx.tableAction().ROW() != null && ctx.tableAction().ADD() != null) {
                        // ADD ROW ACCESS POLICY policyName ON (col1, col2)
                        final String policyName = visitor.getText(ctx.tableAction().qualifiedName());
                        final List<String> cols = rowAccessPolicyColumns(ctx.tableAction().identifierList(),
                            table, table.getName(), policyName, table.hasRowAccessPolicy());
                        // Recorded in full, as every policy attachment is, so a query from another schema
                        // still finds it (see PolicyReferences).
                        table.setRowAccessPolicyName(catalog.qualifiedObjectName(policyName));
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
                        IcebergTables.requireChangeTrackingKept(table, ctx.tableAction());
                        for (final FrostlakeParser.OptionKeyContext key
                                : ctx.tableAction().tableUnsetProperties().optionKey()) {
                            if ("CHANGE_TRACKING".equalsIgnoreCase(key.getText())) {
                                table.setChangeTracking(false);
                            } else if ("DATA_RETENTION_TIME_IN_DAYS".equalsIgnoreCase(key.getText())) {
                                // Back to INHERITING the container's value, like the schema's UNSET.
                                table.setDataRetentionTimeInDays(null);
                            } else if ("MAX_DATA_EXTENSION_TIME_IN_DAYS".equalsIgnoreCase(key.getText())) {
                                table.setMaxDataExtensionTimeInDays(null);
                            } else if ("DEFAULT_DDL_COLLATION".equalsIgnoreCase(key.getText())) {
                                table.setDefaultDdlCollation(null);
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
                        IcebergTables.requireChangeTrackingKept(table, ctx.tableAction());
                        for (final FrostlakeParser.TableSetPropertyContext property
                                : ctx.tableAction().tableSetProperty()) {
                            final String rawKey = property.optionKey().getText();
                            requireLegalRetention(rawKey, property.copyOptionValue());
                            requireAccountRetention(rawKey, property.copyOptionValue());
                            // A transient table keeps a day at most, as its transient containers do.
                            requireTransientRetention(rawKey, table.isTransient(), property.copyOptionValue());
                            requireLegalExtensionTime(rawKey, property.copyOptionValue());
                            requireLegalErrorLogging(rawKey, property.copyOptionValue());
                            if ("DATA_RETENTION_TIME_IN_DAYS".equalsIgnoreCase(rawKey)
                                    && property.copyOptionValue() != null
                                    && property.copyOptionValue().getText().matches("[0-9]+")) {
                                table.setDataRetentionTimeInDays(
                                    Integer.valueOf(property.copyOptionValue().getText()));
                            }
                            if ("MAX_DATA_EXTENSION_TIME_IN_DAYS".equalsIgnoreCase(rawKey)
                                    && property.copyOptionValue() != null
                                    && property.copyOptionValue().getText().matches("[0-9]+")) {
                                table.setMaxDataExtensionTimeInDays(
                                    Integer.valueOf(property.copyOptionValue().getText()));
                            } else if ("DEFAULT_DDL_COLLATION".equalsIgnoreCase(rawKey)
                                    && property.copyOptionValue() != null
                                    && property.copyOptionValue().STRING_LITERAL() != null) {
                                table.setDefaultDdlCollation(
                                    SqlStringLiterals.decode(property.copyOptionValue().getText()));
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
                // This branch decides IF EXISTS for itself: it forgives the view's absence alone, not for every
                // action, and never a fault of the action over a view that is there.
                branchHandlesIfExists = true;
                final FrostlakeParser.MaterializedViewActionContext action = ctx.materializedViewAction();
                if (action != null && !ddlHandler.materializedViewExists(queryExecutor.alterTargetNameParts(ctx))) {
                    refuseMissingMaterializedView(ctx, action, ifExists);
                    return null;
                }
                checkAlter(SecurableObjectType.MATERIALIZED_VIEW, queryExecutor.alterTargetName(ctx));
                if (ctx.materializedViewAction() != null && ctx.materializedViewAction().RENAME() == null) {
                    queryExecutor.requireOnRelation(Privilege.MODIFY, queryExecutor.alterTargetName(ctx));
                }
                return ddlHandler.handleAlterStatement(ctx);

            } else if (ctx.SEQUENCE() != null) {
                // IF EXISTS forgives only the sequence's absence: a taken new name still refuses (live-verified).
                branchHandlesIfExists = true;
                return ddlHandler.handleAlterStatement(ctx);

            } else if (ctx.VIEW() != null) {
                final String viewName = queryExecutor.alterTargetName(ctx);
                if (ctx.viewAction().SECURE() != null) {
                    if (ifExists) {
                        // Refused whether or not the view is there (live-verified).
                        branchHandlesIfExists = true;
                        throw new RuntimeException("Unsupported feature 'IF EXISTS in ALTER VIEW SET/UNSET SECURE'.");
                    }
                    if (alterTargetTableExists(queryExecutor.alterTargetNameParts(ctx))) {
                        // A table takes the statement and stays what it is (live-verified).
                        return null;
                    }
                }
                final View view = catalog.resolveView(QualifiedName.of(queryExecutor.alterTargetNameParts(ctx)));
                checkAlter(SecurableObjectType.VIEW, viewName);
                if (ctx.viewAction().RENAME() == null) {
                    queryExecutor.requirePrivilege(Privilege.MODIFY, "VIEW", queryExecutor.alterTargetNameParts(ctx),
                        null);
                }
                if ("INFORMATION_SCHEMA".equals(catalog.resolveOwningSchema(viewName).getName())) {
                    // An INFORMATION_SCHEMA view is read-only: no ALTER changes it, as no RENAME or DROP does.
                    throw new RuntimeException(SqlAccessControlError.insufficientPrivileges("view", view.getName()));
                }
                altered = view;

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
                    // two-part one in a schema of the session's database. A view read elsewhere MOVES. The schema
                    // it lands in is asked for CREATE VIEW, as a created view's is (live-verified).
                    refuseTakenRelation(target.length == 3 ? target[0] : catalog.getCurrentDatabase(),
                        target.length >= 2 ? target[target.length - 2] : catalog.getCurrentSchema(),
                        target[target.length - 1], target);
                    if (!view.isTemporary()) {
                        queryExecutor.requireCreatePrivilege(Privilege.CREATE_VIEW, "SCHEMA", new String[] {
                            target.length == 3 ? target[0] : catalog.getCurrentDatabase(),
                            target.length >= 2 ? target[target.length - 2] : catalog.getCurrentSchema()});
                    }
                    catalog.moveView(viewName, target.length == 3 ? target[0] : catalog.getCurrentDatabase(),
                        target.length >= 2 ? target[target.length - 2] : catalog.getCurrentSchema(), target);
                    logger.trace("Renamed view {} to {}", viewName, String.join(".", target));
                } else if (ctx.viewAction().SECURE() != null) {
                    view.setSecure(ctx.viewAction().SET() != null);
                    logger.trace("Set secure on view {}: {}", viewName, view.isSecure());
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
                    view.setRowAccessPolicyName(catalog.qualifiedObjectName(policyName));
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
                final String streamName = streamParts[streamParts.length - 1];
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
                } else if (ctx.taskAction().CONTACT() != null && !ctx.taskAction().contactAssignment().isEmpty()) {
                    // SET CONTACT purpose = contact, … as on a table.
                    for (final FrostlakeParser.ContactAssignmentContext assignment
                            : ctx.taskAction().contactAssignment()) {
                        final String purpose = contactPurpose(assignment.contactPurpose());
                        final String contactName = visitor.getText(assignment.qualifiedName());
                        if (catalog.findContact(contactName) == null) {
                            throw new RuntimeException(SqlCompilationError.doesNotExist("Contact",
                                catalog.qualifiedObjectName(contactName)));
                        }
                        task.setContact(purpose, catalog.qualifiedObjectName(contactName));
                    }
                } else if (ctx.taskAction().CONTACT() != null) {
                    // UNSET CONTACT purpose, … - a purpose nothing was attached for is no error.
                    for (final FrostlakeParser.ContactPurposeContext purpose : ctx.taskAction().contactPurpose()) {
                        task.unsetContact(contactPurpose(purpose));
                    }
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
                        requireUnsettableTaskParam(pn.getText().toUpperCase(Locale.ROOT));
                    }
                    for (final FrostlakeParser.TaskParamNameContext pn : ctx.taskAction().taskParamName()) {
                        unsetTaskParam(task, pn.getText().toUpperCase(Locale.ROOT));
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
                    TaskBodySyntax.requireOneStatement(ctx);
                    task.setSqlStatement(ParseTreeText.taskBodyText(ctx.taskAction().taskBody()));
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
                // Every ALTER is DDL on the pipe but a REFRESH, which queues files rather than changing the pipe.
                if (action.REFRESH() == null) {
                    altered = pipe;
                }

                if (action.tagSet() != null) {
                    applyTagSet(pipe, action.tagSet());
                } else if (action.tagUnset() != null) {
                    applyTagUnset(pipe, action.tagUnset());
                } else if (action.SET() != null) {
                    for (final FrostlakeParser.PipeSetOptionContext opt : action.pipeSetOption()) {
                        final String optName = visitor.getText(opt.identifier(0)).toUpperCase(Locale.ROOT);
                        if ("PIPE_EXECUTION_PAUSED".equals(optName) && opt.booleanValue() != null) {
                            pipe.setPaused(opt.booleanValue().TRUE() != null);
                        } else if ("COMMENT".equals(optName) && opt.STRING_LITERAL() != null) {
                            pipe.setComment(visitor.extractStringLiteral(opt.STRING_LITERAL()));
                        } else if ("ERROR_INTEGRATION".equals(optName) && opt.booleanValue() == null) {
                            // A string is the integration's exact name; an identifier folds as one does.
                            final String integration = opt.STRING_LITERAL() != null
                                ? visitor.extractStringLiteral(opt.STRING_LITERAL())
                                : visitor.getText(opt.identifier(1));
                            new IntegrationReferences(catalog).requirePipeError(integration);
                            pipe.setErrorIntegration(integration);
                        } else {
                            throw new RuntimeException(SqlCompilationError.invalidPropertyFor(optName, "PIPE"));
                        }
                    }
                    logger.trace("Set options on pipe: {}", pipeName);
                } else if (action.UNSET() != null) {
                    for (final FrostlakeParser.IdentifierContext unset : action.identifier()) {
                        final String optName = visitor.getText(unset).toUpperCase(Locale.ROOT);
                        if ("ERROR_INTEGRATION".equals(optName)) {
                            pipe.setErrorIntegration(null);
                        } else if ("COMMENT".equals(optName)) {
                            pipe.setComment(null);
                        } else if ("PIPE_EXECUTION_PAUSED".equals(optName)) {
                            pipe.setPaused(false);
                        } else {
                            throw new RuntimeException(SqlCompilationError.invalidPropertyFor(optName, "PIPE"));
                        }
                    }
                    logger.trace("Unset options on pipe: {}", pipeName);
                } else if (action.REFRESH() != null) {
                    // REFRESH triggers a one-time ingest: execute the pipe's COPY INTO … FROM @stage,
                    // optionally narrowed by PREFIX (stage-relative path prefix) and/or MODIFIED_AFTER
                    // (only files last-modified after the given timestamp are loaded).
                    final String copy = pipe.getCopyStatement();
                    if (copy != null && !copy.isBlank()) {
                        String refreshPrefix = null;
                        String refreshModifiedAfter = null;
                        for (final FrostlakeParser.PipeRefreshOptionContext opt : action.pipeRefreshOption()) {
                            final String optName = visitor.getText(opt.identifier()).toUpperCase(Locale.ROOT);
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
                            if (prop.WAREHOUSE_TYPE() != null && warehouse.getEnableQueryAccelerationSetting() == null) {
                                // A warehouse whose type is altered keeps the acceleration setting it has: the
                                // new type's default is a new warehouse's (live-verified).
                                warehouse.setEnableQueryAcceleration(warehouse.isEnableQueryAcceleration());
                            }
                            applyWarehouseProperty(warehouse, prop);
                        }
                        logger.trace("Altered warehouse: {}", warehouseName);
                    } else if (ctx.warehouseAction().tagSet() != null) {
                        applyTagSet(warehouse, ctx.warehouseAction().tagSet());
                    } else if (ctx.warehouseAction().tagUnset() != null) {
                        applyTagUnset(warehouse, ctx.warehouseAction().tagUnset());
                    } else if (ctx.warehouseAction().UNSET() != null) {
                        requireUnsettable(ctx.warehouseAction().warehouseUnsetProperty());
                        for (final FrostlakeParser.WarehouseUnsetPropertyContext prop
                                : ctx.warehouseAction().warehouseUnsetProperty()) {
                            unsetWarehouseProperty(warehouse, prop);
                        }
                        logger.trace("Unset warehouse properties: {}", warehouseName);
                    }
                }

            } else if (ctx.STAGE() != null) {
                // IF EXISTS forgives the stage's absence alone, and a REFRESH not even that (see AlterStageHandler).
                branchHandlesIfExists = true;
                final AlterStageHandler stages = new AlterStageHandler(queryExecutor, visitor);
                final ResultSet refreshed = stages.alter(ctx, ifExists);
                altered = stages.altered();
                if (refreshed != null) {
                    return refreshed;
                }

            } else if (ctx.USER() != null) {
                branchHandlesIfExists = true;
                alterUser(ctx.identifier(), ctx.userAction(), ifExists);

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

            } else if (ctx.ACCOUNT() != null && (ctx.sessionAction() != null || ctx.tagSet() != null
                    || ctx.tagUnset() != null)) {
                branchHandlesIfExists = true;
                alterAccount(ctx, ifExists);

            } else if (ctx.SESSION() != null) {
                if (ctx.sessionAction().SET() != null) {
                    // ALTER SESSION SET p1 = v1 [,] p2 = v2 ... — the list is judged whole, so nothing applies
                    // when one name or value is refused; NULL puts a parameter back on its default.
                    for (final SessionParameterSetting setting : SessionParameterSettings.judged(
                            ctx.sessionAction().sessionAssignment(), queryExecutor, catalog)) {
                        if (setting.stored() == null) {
                            unsetSessionParameter(setting.key());
                        } else {
                            setSessionParameter(setting.key(), setting.stored());
                        }
                    }
                } else if (ctx.sessionAction().UNSET() != null) {
                    // ALTER SESSION UNSET <param> [, <param> ...] — clear each named session parameter.
                    for (final SessionParameterSetting setting : SessionParameterSettings.named(
                            ctx.sessionAction().sessionParameter())) {
                        unsetSessionParameter(setting.key());
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
                    // A list keeps being one when its last value goes: the tag then takes no value.
                    tag.keepAllowedValues(values);
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
                    // A renamed tag is created under its new name: a name a tag of that schema holds is refused
                    // first, and the schema is then asked for CREATE TAG (live-verified).
                    final String[] newTagParts = qualifiedNameParts(ctx.tagAction().qualifiedName());
                    final Schema tagTarget = newTagParts.length == 1 ? catalog.resolveOwningSchema(tagName)
                        : catalog.resolveOwningSchema(newName);
                    DDLCommandHandler.refuseTakenName(true,
                        tagTarget.hasTagExact(newTagParts[newTagParts.length - 1]), newTagParts);
                    queryExecutor.requireCreatePrivilege(Privilege.CREATE_TAG, tagTarget);
                    catalog.renameTag(tagName, newName);
                    logger.trace("Renamed tag {} to {}", tagName, newName);
                }
            }

            if (altered != null) {
                altered.markDdl(catalog.currentUserForDdl());
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

    /**
     * An ALTER MATERIALIZED VIEW whose view is not there. The actions on the clustering key — CLUSTER BY, DROP
     * CLUSTERING KEY, SUSPEND and RESUME RECLUSTER — are a table's, and name the missing view a Table; every other
     * action names it a Materialized view. IF EXISTS forgives the absence, but not for SUSPEND, RESUME and SET or
     * UNSET SECURE, and a CLUSTER BY's key is compiled all the same: with no view none of its columns resolves, and
     * the first is refused where it stands (all live-verified).
     *
     * @param ctx      the statement
     * @param action   its action
     * @param ifExists whether the statement says IF EXISTS
     */
    private void refuseMissingMaterializedView(final FrostlakeParser.AlterStatementContext ctx,
                                               final FrostlakeParser.MaterializedViewActionContext action,
                                               final boolean ifExists) {
        final boolean keyed = action.clusterByClause() != null || action.CLUSTERING() != null
            || action.RECLUSTER() != null;
        final boolean forgiven = keyed || action.RENAME() != null || action.COMMENT() != null;
        if (ifExists && forgiven) {
            if (action.clusterByClause() != null) {
                ClusterKeyValidation.requireResolvable(action.clusterByClause().expressionList().expression(), null);
            }
            return;
        }
        final String[] parts = queryExecutor.alterTargetNameParts(ctx);
        if (keyed) {
            // The table's own refusal, or the table itself where one of that name is there.
            catalog.resolveTable(QualifiedName.of(parts));
        }
        checkAlter(SecurableObjectType.MATERIALIZED_VIEW, queryExecutor.alterTargetName(ctx));
        ddlHandler.handleAlterStatement(ctx);
    }

    /**
     * ALTER TABLE … RENAME TO, which ALTER DYNAMIC TABLE … RENAME TO does to a plain table as well.
     *
     * @param tableName   the table as the statement names it
     * @param tableParts  that name's parts
     * @param table       the table
     * @param targetParts the new name's parts
     */
    private void renameTable(final String tableName, final String[] tableParts, final Table table,
                             final String[] targetParts) {
        // RENAME table. A qualified target that names a different schema/database MOVES the
        // table there (Snowflake semantics); otherwise it is renamed in place. Both the
        // catalog entry and the row storage are re-keyed so the new location is queryable.
        queryExecutor.requireOwnership("TABLE", tableParts, null);
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
        // A name a relation of that schema holds — the table's own included — is refused for the name, as the
        // statement wrote it, before the privilege is asked (live-verified).
        refuseTakenRelation(targetDb, targetSchema, newName, targetParts);
        // A renamed table is created under its new name, in place or elsewhere: its schema is
        // asked for CREATE TABLE, a temporary table's for nothing (live-verified).
        if (!table.isTemporary()) {
            queryExecutor.requireCreatePrivilege(Privilege.CREATE_TABLE, "SCHEMA",
                new String[] {targetDb, targetSchema});
        }
        if (targetDb.equalsIgnoreCase(srcDb) && targetSchema.equalsIgnoreCase(srcSchema)) {
            // Catalog first: its taken-name check throws before anything mutates.
            try {
                catalog.renameTable(tableName, newName);
            } catch (final NameTakenException taken) {
                throw DDLCommandHandler.namedAsWritten(taken, targetParts);
            }
            queryExecutor.renameTableStorage(tableName, newName);
            // Renaming a temporary table away uncovers the permanent table it hid.
            TableShadows.settle(catalog.getDatabase(srcDb).getSchema(srcSchema),
                queryExecutor.getStorageEngine(), srcDb, srcParts[2]);
            logger.trace("Renamed table {} to {}", tableName, newName);
        } else {
            // Validate + move the catalog entry first (throws cleanly if the destination is
            // missing or the name is taken), then re-key storage, so a rejected move mutates
            // nothing.
            try {
                catalog.moveTable(tableName, targetDb, targetSchema, newName);
            } catch (final NameTakenException taken) {
                throw DDLCommandHandler.namedAsWritten(taken, targetParts);
            }
            queryExecutor.moveTableStorage(tableName, targetDb, targetSchema, newName);
            TableShadows.settle(catalog.getDatabase(srcDb).getSchema(srcSchema),
                queryExecutor.getStorageEngine(), srcDb, srcParts[2]);
            logger.trace("Moved table {} to {}.{}.{}", tableName, targetDb, targetSchema, newName);
        }
    }

    /**
     * A rename onto a name a relation of the schema it lands in holds — a table, a view, a stream, the renamed
     * object itself — is refused for the name as the statement wrote it, {@code Object 'DB.S.T' already exists.},
     * before the privilege to create there is asked. A schema or database that is missing is left to the refusal
     * that names it (live-verified).
     */
    private void refuseTakenRelation(final String database, final String schemaName, final String name,
                                     final String[] written) {
        final Database target = database == null ? null : catalog.findDatabase(database);
        if (target == null || schemaName == null || !target.hasSchema(schemaName)) {
            return;
        }
        DDLCommandHandler.refuseTakenName(true, target.getSchema(schemaName).relationKindOf(name) != null, written);
    }

    /**
     * A policy renamed is created under its new name: a name any policy of the schema it lands in holds — its own
     * included — is refused as the statement wrote it, and the schema is then asked for the privilege to create
     * one (live-verified).
     */
    private void requirePolicyRenameTarget(final FrostlakeParser.QualifiedNameContext written,
                                           final Privilege privilege) {
        final String[] parts = qualifiedNameParts(written);
        final Schema target = catalog.resolveOwningSchema(visitor.getText(written));
        DDLCommandHandler.refuseTakenName(true, target.policyKindHolding(parts[parts.length - 1]) != null, parts);
        queryExecutor.requireCreatePrivilege(privilege, target);
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
                final String family = visitor.getText(option.identifier()).toUpperCase(Locale.ROOT);
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
                    families.add(family.toUpperCase(Locale.ROOT));
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
            types.add(typeCtx.getText().toUpperCase(Locale.ROOT));
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
                at.getCharPositionInLine(), "invalid identifier '" + colName.toUpperCase(Locale.ROOT) + "'"));
        }
        final TableColumn column = table.getColumn(colName);
        final FrostlakeParser.AlterColumnItemActionContext action = item.alterColumnItemAction();
        if (action.dataTypeName() != null) {
            // Snowflake only allows same-family changes
            // (live-verified: "cannot change column COL from type NUMBER(38,0) to VARCHAR"),
            // and within a family only the widening ones — see retypeRefusalReason. A collated
            // column must RESTATE its collation, verbatim: dropping it, changing it or adding one
            // is refused with a message that QUOTES both types (live-verified).
            final DataType newDataType = alteredColumnType(table, item);
            final String newCollation = ColumnDefinitionParser.storedCollation(action.collateClause());
            if (column.getDataType().getClass().equals(newDataType.getClass())
                    && !collationsMatch(column.getCollation(), newCollation)) {
                throw new RuntimeException(SqlCompilationError.trailing("cannot change column "
                    + colName.toUpperCase(Locale.ROOT)
                    + " from type \"" + typeText(column.getDataType()) + collateSuffix(column.getCollation())
                    + "\" to \"" + typeText(newDataType) + collateSuffix(newCollation)
                    + "\" because they have incompatible collations."));
            }
            final String refusalReason = retypeRefusalReason(column.getDataType(), newDataType);
            if (refusalReason != null) {
                throw new RuntimeException(SqlCompilationError.trailing("cannot change column "
                    + colName.toUpperCase(Locale.ROOT)
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
            // A column bound to a sequence that is gone still carries a sequence default (live-verified).
            final boolean replacingASequenceDefault = existingDefault instanceof SequenceDefault
                || existingDefault != null
                    && String.valueOf(existingDefault).trim().toUpperCase(Locale.ROOT).endsWith(".NEXTVAL");
            if (!newDefaultIsSequence || !replacingASequenceDefault) {
                throw new RuntimeException("Unsupported feature 'Alter Column Set Default'.");
            }
        } else if (action.NULL() != null && action.DROP() == null) {
            requireNotNullHolds(table, tableName, colName, column);
        }
    }

    /**
     * SET NOT NULL over the rows already stored. Snowflake documents two refusals: "if the column contains
     * NULL values, an error is returned and no changes are applied to the column", and a semi-structured
     * column (ARRAY, OBJECT, VARIANT) "cannot be set to NOT NULL, except when the table is empty". It gives
     * no wording for either, so these sentences are Frostlake's own and not live-verified. The flag used to
     * flip over any data, leaving a NOT NULL column that held NULLs.
     */
    private void requireNotNullHolds(final Table table, final String tableName, final String colName,
            final TableColumn column) {
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
        final List<Row> rows = storage.scan();
        final DataType type = column.getDataType();
        if (!rows.isEmpty()
                && (type instanceof VariantType || type instanceof ObjectType || type instanceof ArrayType)) {
            throw new RuntimeException(SqlCompilationError.trailing("cannot change column "
                + colName.toUpperCase(Locale.ROOT) + " of type " + typeText(type)
                + " to NOT NULL because the table is not empty."));
        }
        for (final Row row : rows) {
            if (row.getValue(slot) == null) {
                throw new RuntimeException(SqlCompilationError.trailing("cannot change column "
                    + colName.toUpperCase(Locale.ROOT) + " to NOT NULL because it contains NULL values."));
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
                    + colName.toUpperCase(Locale.ROOT)
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
            // A FLOAT meets only a FLOAT. FLOAT to NUMBER and back are cross-family changes, which the
            // scale test below let through whenever the NUMBER's scale happened to be FLOAT's placeholder 9.
            if (NumericType.isApproximate(oldType) != NumericType.isApproximate(newType)) {
                return "";
            }
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
        if (oldType instanceof BinaryType) {
            // A binary may widen and not narrow: a stored value longer than the new width would stay in a
            // column whose own INSERT refuses it. The documentation lists the binary shrink without a
            // reason clause, so the plain sentence is used; its exact wording is not live-verified.
            return ((BinaryType) newType).getMaxLength() < ((BinaryType) oldType).getMaxLength() ? "" : null;
        }
        if (oldType instanceof VectorType || StructuredTypes.isStructured(oldType)) {
            // A VECTOR's element type and dimension, and a structured type's shape, have no conversion for
            // the values already stored: any change is refused, and the same type again is no change.
            return DeclaredTypeFold.sameDeclaredType(oldType, newType) ? null : "";
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
        if (StructuredTypes.isStructured(type)) {
            // A structured type is named with its shape, as every other refusal names one; its bare name
            // made a shape change read "from type OBJECT to OBJECT".
            return SqlTypeNames.canonical(type);
        }
        if (type instanceof UuidType) {
            // A UUID takes no length: UUID(36) is a syntax error (UuidType).
            return "UUID";
        }
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
        return name.toUpperCase(Locale.ROOT);
    }

    /** The type an ALTER COLUMN … SET DATA TYPE writes: an Iceberg table's column takes the one the format holds. */
    private DataType alteredColumnType(final Table table, final FrostlakeParser.AlterColumnItemContext item) {
        final FrostlakeParser.AlterColumnItemActionContext action = item.alterColumnItemAction();
        final DataType written = visitor.parseDataType(action.dataTypeName(), action.typeParameters());
        return table.getIcebergMetadata() == null ? written : IcebergColumnTypes.declared(action.dataTypeName(),
            action.typeParameters(), written, SqlIdentifiers.canonical(item.identifier()));
    }

    /**
     * What an ALTER ICEBERG TABLE writes is judged before its table is looked for: a column's type must be one
     * the format holds, and there is no SWAP WITH, a syntax error at the word (live-verified).
     */
    private void requireIcebergAction(final FrostlakeParser.TableActionContext action) {
        if (action.SWAP() != null) {
            final Token swap = action.SWAP().getSymbol();
            throw new RuntimeException(SqlCompilationError.of("syntax error line " + swap.getLine()
                + " at position " + swap.getCharPositionInLine() + " unexpected '" + swap.getText() + "'."));
        }
        if (action.ADD() != null && action.columnDef() != null) {
            final List<FrostlakeParser.ColumnDefContext> added = new ArrayList<>();
            added.add(action.columnDef());
            for (final FrostlakeParser.AlterAddColumnItemContext item : action.alterAddColumnItem()) {
                added.add(item.columnDef());
            }
            IcebergColumnTypes.validate(added, ddlHandler.getColumnParser(), true);
        }
        for (final FrostlakeParser.AlterColumnItemContext item : action.alterColumnItem()) {
            final FrostlakeParser.AlterColumnItemActionContext change = item.alterColumnItemAction();
            if (change.dataTypeName() != null) {
                IcebergColumnTypes.declared(change.dataTypeName(), change.typeParameters(),
                    visitor.parseDataType(change.dataTypeName(), change.typeParameters()),
                    SqlIdentifiers.canonical(item.identifier()));
            }
        }
    }

    /**
     * A table is altered by the statement of its kind: an Iceberg table by ALTER ICEBERG TABLE and any other by
     * ALTER TABLE. A swap is refused in words of its own when either table is an Iceberg table, and a tag or a row
     * access policy the statement names is looked for first (live-verified).
     */
    private void requireStatementOfTableKind(final FrostlakeParser.AlterStatementContext ctx, final Table table) {
        final boolean iceberg = table.getIcebergMetadata() != null;
        final FrostlakeParser.TableActionContext action = ctx.tableAction();
        if (ctx.ICEBERG() != null) {
            if (!iceberg) {
                throw new RuntimeException("SQL Compilation error: The table " + table.getName() + " is a legacy"
                    + " Iceberg table. Legacy Iceberg tables should use the ALTER TABLE commands. Please ensure the"
                    + " appropriate command format is used.");
            }
            return;
        }
        if (action.SWAP() != null) {
            final String[] other = qualifiedNameParts(action.qualifiedName());
            if (iceberg || alterTargetTableExists(other)
                    && catalog.resolveTable(QualifiedName.of(other)).getIcebergMetadata() != null) {
                throw new RuntimeException("SWAP WITH is not supported for ICEBERG TABLE.");
            }
            return;
        }
        if (!iceberg) {
            return;
        }
        if (action.tagSet() != null) {
            for (final FrostlakeParser.TagAssignContext assign : action.tagSet().tagAssign()) {
                referencedTag(assign.qualifiedName());
            }
        }
        if (action.tagUnset() != null) {
            for (final FrostlakeParser.QualifiedNameContext tag : action.tagUnset().qualifiedName()) {
                referencedTag(tag);
            }
        }
        if (action.ROW() != null && action.ADD() != null) {
            requireRowAccessPolicy(visitor.getText(action.qualifiedName()));
        }
        throw IcebergTables.alteredAsTable(table);
    }

    /**
     * An event table's columns are the account's own: adding, dropping, renaming or altering one, and swapping
     * the table with another, are refused, while its name, comment, clustering key and properties may change
     * (live-verified).
     */
    private static void refuseEventTableShapeChange(final Table table,
            final FrostlakeParser.TableActionContext action) {
        if (!table.isEventTable() || action == null) {
            return;
        }
        final boolean addsColumn = action.ADD() != null && action.columnDef() != null;
        final boolean dropsColumn = action.DROP() != null && !action.identifier().isEmpty()
            && action.CONSTRAINT() == null && action.CLUSTERING() == null;
        final boolean renamesColumn = action.RENAME() != null && action.COLUMN() != null
            && action.CONSTRAINT() == null;
        if (action.SWAP() != null || addsColumn || dropsColumn || renamesColumn
                || !action.alterColumnItem().isEmpty()) {
            throw new RuntimeException("Unsupported feature 'INVALID OPERATION FOR EVENT TABLES'.");
        }
    }

    private void applyAlterColumnItem(final Table table, final String tableName,
            final FrostlakeParser.AlterColumnItemContext item) {
        final String colName = visitor.getText(item.identifier());
        final FrostlakeParser.AlterColumnItemActionContext action = item.alterColumnItemAction();
        if (action.dataTypeName() != null) {
            table.alterColumnType(colName, alteredColumnType(table, item));
            MaterializedViewValidity.baseTableAltered(catalog, table);
            logger.trace("Altered column {} type in table {}", colName, tableName);
        } else if (action.NULL() != null) {
            // SET NOT NULL / DROP NOT NULL
            table.getColumn(colName).setNullable(action.DROP() != null);
            MaterializedViewValidity.baseTableAltered(catalog, table);
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
     * Judges the key an ALTER TABLE … ADD declares before it is added: each of its columns is the table's, and a
     * key that is kept does not take a name the table already has.
     *
     * @return whether the key is left out although the statement succeeds
     */
    private boolean keyLeftOut(final Table table, final FrostlakeParser.TableConstraintContext constraint,
                               final String constraintName) {
        for (final FrostlakeParser.IdentifierContext column : constraint.identifierList(0).identifier()) {
            requireColumn(table, visitor.getText(column), column);
        }
        if (ColumnDefinitionParser.leavesOut(constraint)) {
            return true;
        }
        if (constraintName != null && table.hasConstraintNamed(constraintName)) {
            throw ColumnDefinitionParser.constraintNameTaken(constraintName, constraint.FOREIGN() != null,
                table.getName());
        }
        return false;
    }

    /** RELY or NORELY written after a key an ALTER TABLE … ADD declares rides on the key's columns. */
    private static void applyRely(final Table table, final List<String> columns,
                                  final FrostlakeParser.TableConstraintContext constraint) {
        final Boolean rely = KeyConstraintProperties.read(constraint.keyConstraintProperty()).rely();
        if (rely == null) {
            return;
        }
        for (final String column : columns) {
            if (table.hasColumn(column)) {
                table.getColumn(column).setRely(rely);
            }
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

    /** Give a session parameter its judged value. */
    private void setSessionParameter(final String paramName, final Object value) {
        if (queryExecutor.getDatabaseEngine() == null) {
            return;
        }
        queryExecutor.getDatabaseEngine().getSessionContext().setSessionParameter(paramName, value);
        // AUTOCOMMIT is not just a parameter: ALTER SESSION SET AUTOCOMMIT is Snowflake's way to drive the
        // real transaction mode, so toggle it too — and an open transaction is COMMITTED first, whether or
        // not the value actually changes. No other session parameter does that.
        if ("AUTOCOMMIT".equals(paramName)) {
            commitOpenTransactionForAutocommit();
            queryExecutor.getDatabaseEngine().setAutoCommit(Boolean.TRUE.equals(value));
        }
        logger.trace("Set session parameter {} = {}", paramName, value);
    }

    /** Put a session parameter back on its default. */
    private void unsetSessionParameter(final String paramName) {
        if (queryExecutor.getDatabaseEngine() == null) {
            return;
        }
        // UNSET AUTOCOMMIT commits an open transaction exactly as SET does.
        if ("AUTOCOMMIT".equals(paramName)) {
            commitOpenTransactionForAutocommit();
        }
        queryExecutor.getDatabaseEngine().getSessionContext().unsetSessionParameter(paramName);
        logger.trace("Unset session parameter {}", paramName);
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
    /** What an ALTER of the account, or of the session's own user, is told when it is written with IF EXISTS. */
    private static final String IF_EXISTS_WITHOUT_NAME = "Cannot specify IF EXISTS when no object name is specified";

    /**
     * ALTER ACCOUNT SET and UNSET: the account's parameters, which every user and session inherits. The list is
     * judged whole (see {@link LeveledParameterSettings}), so nothing applies when a name or a value is refused.
     */
    private void alterAccount(final FrostlakeParser.AlterStatementContext ctx, final boolean ifExists) {
        final FrostlakeParser.AlteredAccountContext named = ctx.alteredAccount();
        if (ifExists && named == null && ctx.sessionAction() == null) {
            // A list of tags cannot follow an IF EXISTS that names no account: its SET or UNSET is refused where
            // it stands, as a word no statement reads there.
            final Token verb = (ctx.tagSet() != null ? ctx.tagSet() : ctx.tagUnset()).getStart();
            throw new RuntimeException(SqlCompilationError.of(ShowScopeRefusal.syntaxErrorAt(verb, verb.getText())));
        }
        if (ifExists && named == null) {
            throw new RuntimeException(SqlCompilationError.of(IF_EXISTS_WITHOUT_NAME));
        }
        final FrostlakeParser.SessionActionContext action = ctx.sessionAction();
        if (action == null) {
            // The account's tags: a named account is looked for before its tags are.
            if (!isThisAccount(named, ifExists)) {
                return;
            }
            if (ctx.tagSet() != null) {
                applyTagSet(catalog.getAccountTags(), ctx.tagSet());
            } else {
                applyTagUnset(catalog.getAccountTags(), ctx.tagUnset());
            }
            return;
        }
        if (action.SET() != null) {
            final List<SessionParameterSetting> settings =
                LeveledParameterSettings.assignments(action.sessionAssignment());
            LeveledParameterSettings.requireNames(settings, "ACCOUNT");
            LeveledParameterSettings.requireKind(settings, "NUMBER", queryExecutor);
            LeveledParameterSettings.requireKind(settings, "STRING", queryExecutor);
            LeveledParameterSettings.requireKind(settings, "BOOLEAN", queryExecutor);
            // The list is judged before a named account is looked for.
            if (!isThisAccount(named, ifExists)) {
                return;
            }
            LeveledParameterSettings.requireTaken(settings, catalog);
            LeveledParameterSettings.set(catalog.getAccountParameters(), settings);
            return;
        }
        final List<String> names = new ArrayList<>();
        for (final FrostlakeParser.SessionParameterContext name : action.sessionParameter()) {
            names.add(SessionParameterSetting.writtenOf(name));
        }
        final List<SessionParameterSetting> settings = LeveledParameterSettings.names(names);
        LeveledParameterSettings.requireNames(settings, "ACCOUNT");
        if (!isThisAccount(named, ifExists)) {
            return;
        }
        LeveledParameterSettings.unset(catalog.getAccountParameters(), settings);
    }

    /**
     * Whether an ALTER ACCOUNT alters this engine's account: one written without a name, or under the account's
     * own. Another name is an account that is not there, which IF EXISTS forgives.
     */
    private boolean isThisAccount(final FrostlakeParser.AlteredAccountContext named, final boolean ifExists) {
        if (named == null) {
            return true;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(named.qualifiedName());
        final String own = queryExecutor.getEngineConfig() == null ? null
            : queryExecutor.getEngineConfig().getAccountId();
        if (parts.length == 1 && own != null && own.equalsIgnoreCase(parts[0])) {
            return true;
        }
        if (ifExists) {
            return false;
        }
        throw new RuntimeException(SqlCompilationError.doesNotExist("Account",
            visitor.getText(named.qualifiedName())));
    }

    /**
     * ALTER USER. Written without a name it alters the session's own user, and then takes no IF EXISTS. What the
     * statement compiles for is judged before the user is looked up, so IF EXISTS forgives the user's absence alone;
     * a tag, though, is looked up after a user the statement must find and before one it need not (live-verified).
     */
    private void alterUser(final FrostlakeParser.IdentifierContext written,
                           final FrostlakeParser.UserActionContext action, final boolean ifExists) {
        if (written == null && ifExists) {
            throw new RuntimeException(SqlCompilationError.of(IF_EXISTS_WITHOUT_NAME));
        }
        final String sessionUser = catalog.currentUserForDdl();
        final String userName = written != null ? visitor.getText(written) : sessionUser != null ? sessionUser : "";
        final UserParameterList parameters = UserParameterList.reads(action) ? new UserParameterList(action) : null;
        if (parameters != null) {
            parameters.compile(queryExecutor);
        }
        if (!action.userProperty().isEmpty()) {
            UserProperties.checkForm(action.userProperty());
        } else if (action.UNSET() != null) {
            UserProperties.checkUnset(action.userUnsetProperty());
        } else if (action.tagSet() != null || action.tagUnset() != null) {
            if (!ifExists) {
                catalog.getUser(userName);
            }
            if (action.tagSet() != null) {
                for (final FrostlakeParser.TagAssignContext assign : action.tagSet().tagAssign()) {
                    referencedTag(assign.qualifiedName());
                }
            } else {
                for (final FrostlakeParser.QualifiedNameContext tag : action.tagUnset().qualifiedName()) {
                    referencedTag(tag);
                }
            }
        }
        if (ifExists && !catalog.hasUser(userName)) {
            logger.debug("ALTER USER IF EXISTS: user {} not found", userName);
            return;
        }
        final User user = catalog.getUser(userName);
        if (parameters != null) {
            parameters.requireTaken(catalog);
        }
        if (action.tagSet() != null) {
            applyTagSet(user, action.tagSet());
        } else if (action.tagUnset() != null) {
            applyTagUnset(user, action.tagUnset());
        } else if (action.RENAME() != null) {
            final String newName = visitor.getText(action.identifier());
            if (catalog.hasUser(newName)) {
                throw new RuntimeException(SqlCompilationError.of("Object '" + newName + "' already exists."));
            }
            final String heldName = user.getName();
            catalog.renameUser(userName, newName);
            SecurityObjectCommandHandler.renameUser(catalog, heldName, user.getName());
            logger.trace("Renamed user {} to {}", userName, newName);
        } else if (!action.userProperty().isEmpty() || parameters != null) {
            // A network policy the list names has to exist before any of the list is applied.
            final String policy = UserNetworkPolicy.named(catalog, action.userProperty());
            UserProperties.apply(user, action.userProperty());
            if (UserNetworkPolicy.clears(action.userProperty())) {
                UserNetworkPolicy.detach(catalog, user);
            }
            UserNetworkPolicy.attach(catalog, user, policy);
            if (parameters != null) {
                parameters.apply(user);
            }
            logger.trace("Set properties on user: {}", userName);
        } else if (action.UNSET() != null) {
            UserProperties.unset(user, action.userUnsetProperty());
            UserNetworkPolicy.detach(catalog, user, action.userUnsetProperty());
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
            TagAssignments.set(target, tag, value);
        }
    }

    /** Apply an UNSET TAG action, removing each named tag association from a taggable object. */
    private void applyTagUnset(final Taggable target, final FrostlakeParser.TagUnsetContext unset) {
        for (final FrostlakeParser.QualifiedNameContext qn : unset.qualifiedName()) {
            TagAssignments.unset(target, referencedTag(qn));
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
     * The checks an ALTER WAREHOUSE … UNSET list gets before any of it is applied: a name that is no property of
     * a warehouse, INITIALLY_SUSPENDED among them — {@code invalid property 'X' for 'WAREHOUSE'}; the size, which
     * a warehouse cannot be without — {@code invalid type of property 'null' for 'WAREHOUSE_SIZE'}; and a
     * property named twice — {@code duplicate property 'X';} (live-verified).
     */
    private void requireUnsettable(final List<FrostlakeParser.WarehouseUnsetPropertyContext> properties) {
        final List<String> names = new ArrayList<>();
        for (final FrostlakeParser.WarehouseUnsetPropertyContext prop : properties) {
            final String name = prop.identifier() != null ? visitor.getText(prop.identifier()).toUpperCase(Locale.ROOT)
                : prop.getText().toUpperCase(Locale.ROOT);
            if (prop.identifier() != null || prop.INITIALLY_SUSPENDED() != null) {
                throw new RuntimeException(SqlCompilationError.of("invalid property '" + name + "' for 'WAREHOUSE'"));
            }
            if (prop.WAREHOUSE_SIZE() != null) {
                throw new RuntimeException(SqlCompilationError.of(
                    "invalid type of property 'null' for 'WAREHOUSE_SIZE'"));
            }
            if (names.contains(name)) {
                throw new RuntimeException(SqlCompilationError.of("duplicate property '" + name + "';"));
            }
            names.add(name);
        }
    }

    /**
     * ALTER WAREHOUSE … UNSET: AUTO_SUSPEND and COMMENT read back EMPTY afterwards — live leaves
     * the auto_suspend cell null, not at its creation default — while the remaining declared
     * properties return to their defaults: MAX_CONCURRENCY_LEVEL 8, the queued timeout 0, the statement
     * timeout 172800 and the type STANDARD among them (live-verified).
     */
    private void unsetWarehouseProperty(final Warehouse warehouse,
                                        final FrostlakeParser.WarehouseUnsetPropertyContext prop) {
        if (prop.AUTO_SUSPEND() != null) {
            warehouse.setAutoSuspendSeconds(null);
        } else if (prop.COMMENT() != null) {
            warehouse.setComment(null);
        } else if (prop.AUTO_RESUME() != null) {
            warehouse.setAutoResume(true);
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
        } else if (prop.ENABLE_QUERY_ACCELERATION() != null) {
            warehouse.unsetEnableQueryAcceleration();
        } else if (prop.QUERY_ACCELERATION_MAX_SCALE_FACTOR() != null) {
            warehouse.setQueryAccelerationMaxScaleFactor(8);
        } else if (prop.MAX_CONCURRENCY_LEVEL() != null) {
            warehouse.unsetParameter("MAX_CONCURRENCY_LEVEL");
        } else if (prop.STATEMENT_QUEUED_TIMEOUT_IN_SECONDS() != null) {
            warehouse.unsetParameter("STATEMENT_QUEUED_TIMEOUT_IN_SECONDS");
        } else if (prop.STATEMENT_TIMEOUT_IN_SECONDS() != null) {
            warehouse.unsetParameter("STATEMENT_TIMEOUT_IN_SECONDS");
        } else if (prop.WAREHOUSE_TYPE() != null) {
            warehouse.setWarehouseType(null);
        }
    }

    /** The properties an ALTER CORTEX SEARCH SERVICE … SET takes by name, beside the ones the grammar spells. */
    private static final Set<String> CORTEX_SEARCH_PROPERTIES = new HashSet<>(Arrays.asList(
        "FULL_INDEX_BUILD_INTERVAL_DAYS", "REQUEST_LOGGING", "AUTO_SUSPEND"));

    /**
     * What an ALTER CORTEX SEARCH SERVICE … SET may not set, refused before the service is looked for: the
     * embedding model is settled when the index is built, a name that is no property of a service is refused the
     * same way, and a target lag must read as a count and a unit (live-verified).
     */
    private void rejectCortexSearchSettings(final FrostlakeParser.CortexSearchActionContext action) {
        for (final FrostlakeParser.CortexSearchSettingContext setting : action.cortexSearchSetting()) {
            final FrostlakeParser.CortexSearchOptionContext option = setting.cortexSearchOption();
            if (option != null && option.EMBEDDING_MODEL() != null) {
                throw new RuntimeException(SqlCompilationError.of(
                    "invalid property 'EMBEDDING_MODEL' for 'CORTEX_SEARCH_SERVICE'"));
            }
            if (option != null && option.TARGET_LAG() != null) {
                final String lag = visitor.extractStringLiteral(option.STRING_LITERAL());
                if (!TargetLag.parses(lag)) {
                    throw new RuntimeException(SqlCompilationError.of(
                        "invalid value '" + lag + "' for property 'TARGET_LAG'"));
                }
            }
            if (setting.identifier() != null) {
                final String property = SqlIdentifiers.canonical(setting.identifier());
                if (!CORTEX_SEARCH_PROPERTIES.contains(property)) {
                    throw new RuntimeException(SqlCompilationError.of(
                        "invalid property '" + property + "' for 'CORTEX_SEARCH_SERVICE'"));
                }
            }
        }
    }

    /**
     * ALTER CORTEX SEARCH SERVICE. SET changes the refresh options, the primary key and the comment; SET and UNSET
     * ATTRIBUTES replace the attribute columns; SUSPEND and RESUME act on the layer named (INDEXING or SERVING),
     * or on both when none is; REFRESH asks for a refresh, which changes nothing the catalog keeps. The engine
     * holds no index, so nothing is rebuilt.
     */
    private void applyCortexSearchAction(final CortexSearchService service,
                                         final FrostlakeParser.CortexSearchActionContext action) {
        if (action.tagSet() != null) {
            applyTagSet(service, action.tagSet());
            return;
        }
        if (action.tagUnset() != null) {
            applyTagUnset(service, action.tagUnset());
            return;
        }
        if (action.REFRESH() != null) {
            return;
        }
        if (action.UNSET() != null) {
            if (action.ATTRIBUTES() != null) {
                service.setAttributeColumns(new ArrayList<String>());
            } else {
                service.setPrimaryKeyColumns(new ArrayList<String>());
            }
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
        if (action.ATTRIBUTES() != null) {
            service.setAttributeColumns(canonicalNames(action.identifierList()));
            return;
        }
        for (final FrostlakeParser.CortexSearchSettingContext setting : action.cortexSearchSetting()) {
            final FrostlakeParser.CortexSearchOptionContext option = setting.cortexSearchOption();
            if (option != null && option.COMMENT() != null) {
                service.setComment(visitor.extractStringLiteral(option.STRING_LITERAL()));
            } else if (option != null && option.TARGET_LAG() != null) {
                service.setTargetLag(visitor.extractStringLiteral(option.STRING_LITERAL()));
            } else if (option != null && option.WAREHOUSE() != null) {
                final String warehouse = SqlIdentifiers.canonical(option.identifier());
                WarehouseReference.require(catalog, warehouse);
                service.setWarehouse(warehouse);
            } else if (setting.PRIMARY() != null) {
                service.setPrimaryKeyColumns(canonicalNames(setting.identifierList()));
            } else if (setting.identifier() != null) {
                applyCortexSearchProperty(service, SqlIdentifiers.canonical(setting.identifier()), setting);
            }
        }
    }

    /** One of the properties a SET names by a plain word, with the number, boolean or NULL it is given. */
    private static void applyCortexSearchProperty(final CortexSearchService service, final String property,
                                                  final FrostlakeParser.CortexSearchSettingContext setting) {
        final Integer number = setting.INTEGER_LITERAL() == null ? null
            : Integer.valueOf(setting.INTEGER_LITERAL().getText());
        if ("REQUEST_LOGGING".equals(property)) {
            service.setRequestLogging(setting.TRUE() != null);
        } else if ("AUTO_SUSPEND".equals(property)) {
            service.setAutoSuspend(number);
        } else {
            service.setFullIndexBuildIntervalDays(number);
        }
    }

    /** The canonical names of a parenthesised list. */
    private static List<String> canonicalNames(final FrostlakeParser.IdentifierListContext list) {
        final List<String> names = new ArrayList<>();
        for (final FrostlakeParser.IdentifierContext name : list.identifier()) {
            names.add(SqlIdentifiers.canonical(name));
        }
        return names;
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
                colName.toUpperCase(Locale.ROOT)), unknown);
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
     * The projection policy a column's SET names, refused when the name resolves to nothing.
     *
     * @return the policy's name in full, as an attachment records it
     */
    private String requireProjectionPolicy(final String policyName) {
        if (catalog.findProjectionPolicy(policyName) == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Projection policy",
                catalog.qualifiedObjectName(policyName)));
        }
        return catalog.qualifiedObjectName(policyName);
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
        RowAccessPolicyAttachment.check(catalog, policy, table, objectName, policyName, cols, false);
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
        // A column a clustering key reads stays, and is named before a column the list misses, IF EXISTS or not
        // (live-verified).
        for (final String colName : named) {
            if (table.hasColumnExactly(colName)) {
                final String keyed = table.columnAt(table.getColumnIndex(colName)).getName();
                if (table.isClusteringKeyColumn(keyed)) {
                    throw new RuntimeException("Cannot drop column '" + keyed
                        + "' which belongs to a clustering key\n");
                }
            }
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
        final Schema columnOwner = catalog.resolveOwningSchema(tableName);
        for (final String colName : dropping) {
            final int index = table.getColumnIndex(colName);
            // The column takes the keys it belongs to, and with them the foreign keys other tables hold on them.
            new ForeignKeyDependents(catalog).releaseColumn(columnOwner.getDatabaseName(), columnOwner, table, colName,
                table.getPrimaryKeys().contains(colName));
            table.dropColumn(colName);
            queryExecutor.dropColumnValues(fullyQualifiedName, index, table.isTemporary());
            logger.trace("Dropped column {} from table {}", colName, tableName);
        }
        if (!dropping.isEmpty()) {
            MaterializedViewValidity.baseTableAltered(catalog, table);
        }
    }

    /**
     * ALTER SCHEMA a SWAP WITH b: the two schemas trade names, each keeping its objects and metadata, as three renames
     * through a name of no one's would leave them; the other schema must exist, and a schema swapped with itself stays
     * as it was (documented).
     */
    private void swapSchemas(final Schema schema, final String[] otherParts) {
        final Schema other = catalog.resolveSchema(QualifiedName.of(otherParts));
        if (other == schema) {
            return;
        }
        if ("INFORMATION_SCHEMA".equals(other.getName())) {
            throw new RuntimeException(SqlAccessControlError.insufficientPrivileges("schema", "INFORMATION_SCHEMA"));
        }
        SystemDatabase.refuseChange(other.getDatabaseName());
        queryExecutor.requireOwnership("SCHEMA", new String[] {other.getDatabaseName(), other.getName()}, null);
        final String databaseA = schema.getDatabaseName();
        final String nameA = schema.getName();
        final String databaseB = other.getDatabaseName();
        final String nameB = other.getName();
        final String parked = "SWAP_" + UUID.randomUUID().toString().replace("-", "_").toUpperCase(Locale.ROOT);
        moveSchema(schema, databaseA, nameA, databaseA, parked);
        moveSchema(other, databaseB, nameB, databaseA, nameA);
        moveSchema(schema, databaseA, parked, databaseB, nameB);
        logger.trace("Swapped schema {}.{} with {}.{}", databaseA, nameA, databaseB, nameB);
    }

    /** One rename of a swap: the schema and its rows' storage move to the new name together. */
    private void moveSchema(final Schema schema, final String fromDatabase, final String fromName,
                            final String toDatabase, final String toName) {
        catalog.moveSchema(schema, toDatabase, new String[] {toDatabase, toName});
        queryExecutor.renameSchemaStorage(fromDatabase, fromName, toDatabase, toName);
    }

    /**
     * ALTER DATABASE a SWAP WITH b: the two databases trade names, each keeping its schemas and objects, as three
     * renames through a name of no one's would leave them; the other database must exist, and a database swapped with
     * itself stays as it was (documented).
     */
    private void swapDatabases(final Database database, final String otherName) {
        queryExecutor.requireOwnership("DATABASE", new String[] {database.getName()}, null);
        final Database other = catalog.databaseExact(otherName);
        if (other == database) {
            return;
        }
        SystemDatabase.refuseChange(other.getName());
        queryExecutor.requireOwnership("DATABASE", new String[] {other.getName()}, null);
        final String nameA = database.getName();
        final String nameB = other.getName();
        final String parked = "SWAP_" + UUID.randomUUID().toString().replace("-", "_").toUpperCase(Locale.ROOT);
        catalog.renameDatabase(nameA, parked);
        queryExecutor.renameDatabaseStorage(nameA, parked);
        catalog.renameDatabase(nameB, nameA);
        queryExecutor.renameDatabaseStorage(nameB, nameA);
        catalog.renameDatabase(parked, nameB);
        queryExecutor.renameDatabaseStorage(parked, nameB);
        logger.trace("Swapped database {} with {}", nameA, nameB);
    }

    /**
     * Refuse a key's DROP ... RESTRICT while another table's foreign key references it: {@code cannot drop constraint
     * because of dependencies} (live-verified).
     */
    private void refuseReferencedKey(final FrostlakeParser.TableActionContext action, final String tableName,
                                     final Table table, final List<String> key, final boolean primaryKey) {
        if (action.RESTRICT() == null || key == null || key.isEmpty()) {
            return;
        }
        final Schema keyOwner = catalog.resolveOwningSchema(tableName);
        if (new ForeignKeyDependents(catalog).referencesKey(keyOwner.getDatabaseName(), keyOwner, table, key,
                primaryKey)) {
            throw new RuntimeException(SqlCompilationError.of("cannot drop constraint because of dependencies"));
        }
    }

    /** Drop the foreign keys other tables hold on a key of the table that is gone. */
    private void releaseKey(final String tableName, final Table table, final List<String> key,
                            final boolean primaryKey) {
        if (key == null || key.isEmpty()) {
            return;
        }
        final Schema keyOwner = catalog.resolveOwningSchema(tableName);
        new ForeignKeyDependents(catalog).releaseKey(keyOwner.getDatabaseName(), keyOwner, table, key, primaryKey);
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
