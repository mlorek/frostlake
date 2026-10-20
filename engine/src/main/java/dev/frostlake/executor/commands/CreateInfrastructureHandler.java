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

import dev.frostlake.executor.ConditionalDdlOutcome;
import dev.frostlake.executor.FileFormatClause;
import dev.frostlake.executor.FileFormatReference;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.StageDirectoryRefresh;
import dev.frostlake.executor.StatementClock;
import dev.frostlake.executor.StorageIntegrationReference;
import dev.frostlake.executor.WarehouseReference;
import dev.frostlake.executor.udf.TemporaryObjectStatements;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.CreationGrants;
import dev.frostlake.metastore.FutureGrants;
import dev.frostlake.metastore.GrantNames;
import dev.frostlake.metastore.InstanceFamilies;
import dev.frostlake.metastore.NameTakenException;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.StatementSession;
import dev.frostlake.metastore.model.ComputePool;
import dev.frostlake.metastore.model.ComputePoolState;
import dev.frostlake.metastore.model.CortexSearchService;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.Integration;
import dev.frostlake.metastore.model.Pipe;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.StageType;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskState;
import dev.frostlake.metastore.model.Warehouse;
import dev.frostlake.metastore.model.WarehouseSize;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.task.TaskGraphs;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Handles CREATE for infrastructure objects — TASK, PIPE, SEQUENCE, WAREHOUSE, STAGE — extracted from
 * {@link DDLCommandHandler}, which keeps the CREATE dispatch and delegates here. Shared schema/parsing
 * helpers are reached via the {@code ddl} back-reference.
 */
public class CreateInfrastructureHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(CreateInfrastructureHandler.class);

    private final DDLCommandHandler ddl;
    private final Catalog catalog;
    private final QueryExecutor queryExecutor;

    CreateInfrastructureHandler(final DDLCommandHandler ddl, final Catalog catalog, final QueryExecutor queryExecutor) {
        this.ddl = ddl;
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
    }

    @Override
    public Catalog getCatalog() {
        return catalog;
    }

    @Override
    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    public Object handleCreateTask(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String taskQualifiedName = getText(ctx.qualifiedName(0));
        final String taskName = ddl.extractObjectName(taskQualifiedName);
        final boolean orReplace = ctx.or_replace() != null;
        final boolean orAlter = ctx.or_alter() != null;
        TaskBodySyntax.requireOneStatement(ctx);

        try {
            DDLCommandHandler.refuseTakenName(!orReplace && !orAlter && !ifNotExists,
                hasTaskNamed(ddl.resolveSchemaFromQualifiedName(taskQualifiedName), taskName),
                qualifiedNameParts(ctx.qualifiedName(0)));
            ddl.checkCreatePrivilege(Privilege.CREATE_TASK,
                ddl.resolveSchemaFromQualifiedName(taskQualifiedName));
            final Schema schema = ddl.resolveSchemaFromQualifiedName(taskQualifiedName);
            if (orReplace || orAlter && hasTaskNamed(schema, taskName)) {
                queryExecutor.requireOwnership("TASK", qualifiedNameParts(ctx.qualifiedName(0)), null);
            }
            final String sqlText = taskBodyText(ctx.taskBody());

            // The first step reads the statement alone — a property named twice, a name no task carries, a
            // value of the wrong kind, a warehouse, integration or user that does not exist — and refuses even
            // when IF NOT EXISTS finds the task already there (live-verified).
            final List<TaskPropertySetting> settings = new ArrayList<>();
            if (ctx.warehouseClause() != null) {
                settings.add(TaskProperties.warehouseSetting(ctx.warehouseClause(),
                    ddl.extractWarehouseName(ctx.warehouseClause())));
            }
            if (ctx.taskOptions() != null) {
                settings.addAll(TaskProperties.settings(ctx.taskOptions().taskOption()));
            }
            final TaskProperties properties = new TaskProperties(catalog, schema);
            properties.compile(settings);
            final Task task = new Task(taskName, null, null, sqlText, null);
            // The owner is worked out here, for the checks that read it; the future grants apply only once the
            // task is added, so CREATE OR ALTER and a refused CREATE OR REPLACE leave the grants on the task that
            // stays as they were (FL-436).
            final CreationGrants grantsOfNew = FutureGrants.ofNew(catalog, "TASK", schema, task.getName());
            task.setOwner(grantsOfNew.owner());
            String runAs = null;
            if (ctx.taskExecuteAs() != null) {
                runAs = TaskProperties.runAsUser(ctx.taskExecuteAs(), queryExecutor);
                properties.requireRunAsUser(task, runAs);
            }
            if (ifNotExists && hasTaskNamed(schema, taskName)) {
                logger.debug("Task already exists (IF NOT EXISTS): {}", taskName);
                return null;
            }

            // The second step weighs the values and the graph. A task being replaced stays until the new one
            // has passed every check, so a refused statement leaves it as it was.
            task.setCreatedByUser(catalog.currentUserForStage());
            properties.apply(task, settings);
            task.setExecuteAsUser(runAs);
            if (ctx.WHEN() != null && ctx.booleanExpr() != null) {
                task.setCondition(ddl.getOriginalText(ctx.booleanExpr()));
            }
            // A COMMENT among the options wins over a standalone comment clause.
            if (task.getComment() == null) {
                final String comment = ddl.extractCommentFromList(ctx.commentClause());
                if (comment != null) {
                    task.setComment(comment);
                }
            }
            properties.requireConsistentOverlap();
            final List<FrostlakeParser.QualifiedNameContext> after = ctx.afterClause() != null
                ? ctx.afterClause().qualifiedName() : new ArrayList<FrostlakeParser.QualifiedNameContext>();
            for (final FrostlakeParser.QualifiedNameContext predecessor : after) {
                task.addPredecessor(getText(predecessor).toUpperCase(Locale.ROOT));
            }
            properties.requireScheduleFits(task);
            if (properties.finalizeGiven()) {
                properties.requireFinalizer(task);
            }
            if (orReplace && !after.isEmpty() && TaskGraphs.finalizerOf(schema, task) != null) {
                throw new RuntimeException("Cannot replace task " + schema.getDatabaseName() + "." + schema.getName()
                    + "." + task.getName() + " with a non-root task because it has a finalizer task. Please remove "
                    + "the finalizer task first.");
            }
            if (!after.isEmpty()) {
                final List<String> resolved = properties.requirePredecessors(task, after);
                task.getPredecessors().clear();
                for (final String predecessor : resolved) {
                    task.addPredecessor(predecessor);
                }
            }
            properties.requireRootSettings(task);
            properties.requireServerlessSettings(task);
            // The tags are written right after the name, before every property, and each has to exist.
            if (ctx.tagList() != null) {
                InlineTags.apply(task, ctx.tagList(), queryExecutor);
            }
            if (ctx.taskContacts() != null) {
                TaskContacts.apply(task, ctx.taskContacts(), catalog);
            }
            if (orAlter && hasTaskNamed(schema, taskName)) {
                // The task there becomes what the statement writes: a property it leaves out is unset, while its
                // state, its runs and its tags stay (live-verified, documented). A root that runs is not changed.
                final Task standing = schema.getTaskExact(taskName);
                if (standing.getPredecessors().isEmpty() && standing.getState() == TaskState.STARTED) {
                    throw new RuntimeException("Unable to update graph with root task "
                        + schema.qualifiedName(standing.getName()) + " since that root task is not suspended.");
                }
                standing.redefineAs(task);
                ConditionalDdlOutcome.alteredInPlace();
                logger.trace("Altered task: {}", taskQualifiedName);
                return null;
            }
            if (orReplace && hasTaskNamed(schema, taskName)) {
                schema.dropTask(taskName);
            }
            schema.addTask(task);
            grantsOfNew.apply();
            logger.trace("Created task: {}", taskQualifiedName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object", ctx.qualifiedName(0));
            logger.debug("Task already exists (IF NOT EXISTS): {}", taskName);
        }
        return null;
    }

    /**
     * The SQL a task body runs, as written: any statement — a query or DML, a CALL, an EXECUTE IMMEDIATE, a
     * scripting block, a DDL statement — kept as its own text and re-issued when the task runs.
     */
    private String taskBodyText(final FrostlakeParser.TaskBodyContext body) {
        if (body == null) {
            throw new RuntimeException("Task body is required");
        }
        return ParseTreeText.taskBodyText(body);
    }

    /** Whether the schema holds a task of exactly that name. */
    private static boolean hasTaskNamed(final Schema schema, final String name) {
        for (final Task candidate : schema.getTasks()) {
            if (candidate.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the schema holds a pipe of exactly that name. */
    private static boolean hasPipeNamed(final Schema schema, final String name) {
        for (final Pipe candidate : schema.getPipes()) {
            if (candidate.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public Object handleCreatePipe(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String pipeQualifiedName = getText(ctx.qualifiedName(0));
        final Schema pipeSchema = catalog.resolveOwningSchema(pipeQualifiedName);
        final String pipeName = QualifiedName.parse(pipeQualifiedName).last();
        if (ctx.or_replace() != null) {
            queryExecutor.requireOwnership("PIPE", qualifiedNameParts(ctx.qualifiedName(0)), null);
            // Asked before the pipe a replacement drops is gone.
            ddl.checkCreatePrivilege(Privilege.CREATE_PIPE, pipeSchema);
            try { pipeSchema.dropPipe(pipeName); } catch (final RuntimeException ignored) {}
        }
        try {
            DDLCommandHandler.refuseTakenName(ctx.or_replace() == null && !ifNotExists,
                hasPipeNamed(pipeSchema, pipeName), qualifiedNameParts(ctx.qualifiedName(0)));
            ddl.checkCreatePrivilege(Privilege.CREATE_PIPE, pipeSchema);
            // Extract COPY statement text
            final String copyStatement = ddl.getOriginalText(ctx.copyStatement());
            // The COPY's names resolve in the pipe's schema, as they do when the pipe loads.
            final String[] priorScope = catalog.currentSessionScope();
            catalog.beginSessionScope(pipeSchema.getDatabaseName(), pipeSchema.getName());
            final Stage pipeStage;
            try {
                pipeStage = validatePipeCopyBody(ctx.copyStatement());
                requirePipeCopyPrivileges(ctx.copyStatement(), pipeStage);
            } finally {
                catalog.restoreSessionScope(priorScope);
            }

            // Parse pipe options
            Boolean autoIngestWritten = null;
            boolean autoIngest = false;
            String awsSnsTopicArn = null;
            String errorIntegration = null;
            String integration = null;
            String optionComment = null;

            if (ctx.pipeOptions() != null) {
                for (final FrostlakeParser.PipeOptionContext optionCtx : ctx.pipeOptions().pipeOption()) {
                    if (optionCtx.AUTO_INGEST() != null) {
                        autoIngest = optionCtx.booleanValue().TRUE() != null;
                        autoIngestWritten = Boolean.valueOf(autoIngest);
                    } else if (optionCtx.AWS_SNS_TOPIC() != null) {
                        awsSnsTopicArn = ddl.extractStringLiteral(optionCtx.STRING_LITERAL());
                    } else if (optionCtx.ERROR_INTEGRATION() != null) {
                        errorIntegration = optionCtx.STRING_LITERAL() != null
                            ? ddl.extractStringLiteral(optionCtx.STRING_LITERAL())
                            : getText(optionCtx.identifier());
                    } else if (optionCtx.INTEGRATION() != null) {
                        integration = ddl.extractStringLiteral(optionCtx.STRING_LITERAL());
                    } else if (optionCtx.COMMENT() != null) {
                        optionComment = ddl.extractStringLiteral(optionCtx.STRING_LITERAL());
                    }
                }
            }

            // An integration the pipe names has to exist and to be a queue of the right direction.
            final IntegrationReferences references = new IntegrationReferences(catalog);
            if (errorIntegration != null) {
                references.requirePipeError(errorIntegration);
            }
            if (integration != null) {
                references.requirePipeNotification(integration, autoIngestWritten, pipeStage);
            } else if (autoIngest) {
                references.requireNotificationFor(pipeStage);
            }

            // An SNS topic feeds automatic ingestion: without AUTO_INGEST = TRUE the pipe is refused.
            if (awsSnsTopicArn != null && !autoIngest) {
                throw new RuntimeException("Pipe Notifications bind failure \"Cannot set AWS SNS topic for pipe without"
                    + " auto_ingest\"");
            }

            // Create pipe with notification channel if AWS SNS topic is provided
            final Pipe pipe;
            if (awsSnsTopicArn != null) {
                pipe = new Pipe(pipeName, copyStatement, autoIngest, awsSnsTopicArn);
                pipe.setAwsSnsTopicArn(awsSnsTopicArn);
            } else {
                pipe = new Pipe(pipeName, copyStatement, autoIngest);
            }

            if (errorIntegration != null) {
                pipe.setErrorIntegration(errorIntegration);
            }
            if (integration != null) {
                pipe.setIntegration(integration);
            }

            final String comment = optionComment != null ? optionComment
                : ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                pipe.setComment(comment);
            }

            final CreationGrants grantsOfNew = FutureGrants.ofNew(catalog, "PIPE", pipeSchema, pipe.getName());
            pipe.setOwner(grantsOfNew.owner());
            pipeSchema.addPipe(pipe);
            grantsOfNew.apply();
            logger.trace("Created pipe: {}", pipeName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object", ctx.qualifiedName(0));
            logger.debug("Pipe already exists (IF NOT EXISTS): {}", pipeName);
        }
        return null;
    }

    /**
     * The compile-time checks a real account runs over a pipe's COPY body at CREATE: the target
     * table must exist (its own sentence, without the usual "or not authorized"), the stage must
     * exist, a user stage is refused outright, and the pipe-incompatible copy options
     * (VALIDATION_MODE, FILES) are refused by name. Deeper option-VALUE validation (bad ON_ERROR
     * values, unknown format options) belongs to the engine-wide option-value surface and is not
     * pipe-specific.
     */
    private Stage validatePipeCopyBody(final FrostlakeParser.CopyStatementContext copyCtx) {
        if (copyCtx == null) {
            return null;
        }
        requirePipeTargetTableExists(copyCtx);
        final Stage stage = requirePipeStageExists(copyCtx);
        refusePipeOnlyCopyOptions(copyCtx);
        return stage;
    }

    /** COPY INTO's target must exist when the PIPE is created, live-verified wording. */
    private void requirePipeTargetTableExists(final FrostlakeParser.CopyStatementContext copyCtx) {
        final List<String> parts = pipeTargetParts(copyCtx);
        if (parts.isEmpty()) {
            return;
        }
        final String written = String.join(".", parts);
        final Schema container = ddl.resolveSchemaFromQualifiedName(QualifiedName.join(parts.toArray(new String[0])));
        // Named exactly: a quoted name in another case is another table, which is not there.
        if (container.tableExact(parts.get(parts.size() - 1)) == null) {
            throw new RuntimeException(SqlCompilationError.of(
                "Table '" + written + "' does not exist"));
        }
    }

    /** The canonical parts of the table a pipe's COPY loads, as the COPY names it; empty when it names none. */
    private static List<String> pipeTargetParts(final FrostlakeParser.CopyStatementContext copyCtx) {
        final List<String> parts = new ArrayList<>();
        final FrostlakeParser.QualifiedNameContext target = copyCtx.qualifiedName();
        if (target == null) {
            return parts;
        }
        parts.add(ParseTreeText.namePartText(target.nameStartPart()));
        for (final FrostlakeParser.NamePartContext part : target.namePart()) {
            parts.add(ParseTreeText.namePartText(part));
        }
        return parts;
    }

    /**
     * What a pipe's COPY asks of the role creating the pipe, as Snowflake's CREATE PIPE page lists it: READ on an
     * internal stage the COPY reads, USAGE on an external one, and SELECT and INSERT on the table it loads. The pipe
     * loads with its owner's rights later, so they are asked of the creator now.
     */
    private void requirePipeCopyPrivileges(final FrostlakeParser.CopyStatementContext copyCtx, final Stage stage) {
        if (copyCtx == null || queryExecutor.getSecurityManager() == null) {
            return;
        }
        if (stage != null) {
            final List<String> stageParts = pipeStageParts(copyCtx);
            queryExecutor.requirePrivilege(stage.getUrl() != null ? Privilege.USAGE : Privilege.READ, "STAGE",
                stageParts.toArray(new String[0]), null);
        }
        final List<String> target = pipeTargetParts(copyCtx);
        if (!target.isEmpty()) {
            queryExecutor.requireOnRelation(Privilege.INSERT, String.join(".", target));
            queryExecutor.requireOnRelation(Privilege.SELECT, String.join(".", target));
        }
    }

    /** VALIDATION_MODE and FILES are not valid inside a pipe's COPY, live-verified single line. */
    private void refusePipeOnlyCopyOptions(final FrostlakeParser.CopyStatementContext copyCtx) {
        if (copyCtx.copyRestOfStatement() == null || copyCtx.copyRestOfStatement().children == null) {
            return;
        }
        for (final ParseTree child : copyCtx.copyRestOfStatement().children) {
            if (!(child instanceof TerminalNode)) {
                continue;
            }
            final int type = ((TerminalNode) child).getSymbol().getType();
            if (type == FrostlakeParser.VALIDATION_MODE || type == FrostlakeParser.FILES) {
                throw new RuntimeException(SqlCompilationError.PREFIX + " Invalid copy option '"
                    + child.getText().toUpperCase(Locale.ROOT) + "' in pipe definition.");
            }
        }
    }

    /**
     * A pipe's COPY must read from a stage that exists — a missing one refuses at CREATE with the
     * stage's own does-not-exist shape, live-verified. The pipe body is parsed loosely
     * ({@code copyRestOfStatement} keeps raw tokens), so the named-stage reference is read off the
     * token stream: the first {@code @} followed by an identifier chain. A user stage ({@code @~})
     * is refused outright; a table stage ({@code @%t}) resolves implicitly and is not checked.
     *
     * @return the named stage, or null when the pipe reads a table's own
     */
    private Stage requirePipeStageExists(final FrostlakeParser.CopyStatementContext copyCtx) {
        final List<String> parts = pipeStageParts(copyCtx);
        if (parts.isEmpty()) {
            return null;
        }
        return ddl.resolveSchemaFromQualifiedName(QualifiedName.join(parts.toArray(new String[0])))
            .getStageExact(parts.get(parts.size() - 1));
    }

    /**
     * The canonical parts of the named stage a pipe's COPY reads, read off the body's tokens as
     * {@link #requirePipeStageExists} describes; empty for a table's stage or a body naming none. A user stage is
     * refused.
     */
    private static List<String> pipeStageParts(final FrostlakeParser.CopyStatementContext copyCtx) {
        final List<String> parts = new ArrayList<>();
        if (copyCtx == null || copyCtx.copyRestOfStatement() == null
                || copyCtx.copyRestOfStatement().children == null) {
            return parts;
        }
        boolean inStageName = false;
        boolean expectPart = false;
        for (final ParseTree child : copyCtx.copyRestOfStatement().children) {
            if (!(child instanceof TerminalNode)) {
                continue;
            }
            final int type = ((TerminalNode) child).getSymbol().getType();
            final String text = child.getText();
            if (!inStageName) {
                if (type == FrostlakeParser.AT) {
                    inStageName = true;
                    expectPart = true;
                }
                continue;
            }
            if (type == FrostlakeParser.TILDE) {
                throw new RuntimeException(SqlCompilationError.PREFIX
                    + " Stage: '~' cannot be a user stage in the pipe definition.");
            }
            if (type == FrostlakeParser.PERCENT) {
                return new ArrayList<>();
            }
            if (expectPart && (type == FrostlakeParser.IDENTIFIER
                    || type == FrostlakeParser.QUOTED_IDENTIFIER
                    || text.matches("[A-Za-z_][A-Za-z0-9_$]*"))) {
                parts.add(SqlIdentifiers.canonicalText(text));
                expectPart = false;
                continue;
            }
            if (!expectPart && type == FrostlakeParser.DOT) {
                expectPart = true;
                continue;
            }
            break;
        }
        return parts;
    }

    public Object handleCreateSequence(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        CreatedObjectProperties.requireSequenceProperties(ctx);
        final String sequenceQualifiedName = getText(ctx.qualifiedName(0));
        final String sequenceName = ddl.extractObjectName(sequenceQualifiedName);
        // A clone continues from where its source stands: the same next value, step, ordering and comment.
        final Sequence cloneSource = ctx.CLONE() == null ? null
            : ddl.resolveSchemaFromQualifiedName(getText(ctx.qualifiedName(1)))
                .getSequence(ddl.extractObjectName(getText(ctx.qualifiedName(1))));
        final Schema sequenceSchema = ddl.resolveSchemaFromQualifiedName(sequenceQualifiedName);
        if (ctx.or_replace() != null) {
            queryExecutor.requireOwnership("SEQUENCE", qualifiedNameParts(ctx.qualifiedName(0)), null);
        }
        // Asked before the sequence a replacement drops is gone; a plain CREATE of a name a sequence already
        // holds is refused for the name ahead of the privilege (live-verified).
        if (ctx.or_replace() != null || ifNotExists || !sequenceSchema.hasSequenceExact(sequenceName)) {
            ddl.checkCreatePrivilege(Privilege.CREATE_SEQUENCE, sequenceSchema);
        }
        if (ctx.or_replace() != null && sequenceSchema.hasSequenceExact(sequenceName)) {
            // A sequence whose name differs only in case is another sequence, which OR REPLACE leaves alone.
            sequenceSchema.dropSequenceExact(sequenceName);
        }
        try {
            long startValue = 1;
            long increment = 1;
            boolean order = false; // Default is NOORDER

            if (ctx.sequenceOptions() != null) {
                // Each bound may be given ONCE, and live reports the INTERNAL property name
                // (START is SEQUENCE_START, INCREMENT is SEQUENCE_INCREMENT) — live-verified.
                final List<String> sequenceKeys = new ArrayList<>();
                for (final FrostlakeParser.SequenceOptionContext optionCtx : ctx.sequenceOptions().sequenceOption()) {
                    if (optionCtx.START() != null) {
                        sequenceKeys.add("SEQUENCE_START");
                    } else if (optionCtx.INCREMENT() != null) {
                        sequenceKeys.add("SEQUENCE_INCREMENT");
                    }
                }
                PropertyDuplicates.reject(sequenceKeys);
                for (final FrostlakeParser.SequenceOptionContext optionCtx : ctx.sequenceOptions().sequenceOption()) {
                    if (optionCtx.START() != null) {
                        final long value = Long.parseLong(optionCtx.INTEGER_LITERAL().getText());
                        startValue = optionCtx.MINUS() != null ? -value : value;
                    } else if (optionCtx.INCREMENT() != null) {
                        final long value = Long.parseLong(optionCtx.INTEGER_LITERAL().getText());
                        increment = optionCtx.MINUS() != null ? -value : value;
                        // A zero step refuses with the single-quoted property shape; negatives run.
                        if (increment == 0L) {
                            throw new RuntimeException(SqlCompilationError.invalidValueForProperty(
                                "0", "SEQUENCE_INCREMENT"));
                        }
                    } else if (optionCtx.ORDER() != null) {
                        order = true;
                    } else if (optionCtx.NOORDER() != null) {
                        order = false;
                    }
                }
            }

            String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment == null && ctx.sequenceOptions() != null) {
                // COMMENT may sit inline among the options (CREATE SEQUENCE s START=5 COMMENT 'x' INCREMENT=10).
                for (final FrostlakeParser.SequenceOptionContext optionCtx : ctx.sequenceOptions().sequenceOption()) {
                    if (optionCtx.commentClause() != null) {
                        comment = ddl.extractComment(optionCtx.commentClause());
                    }
                }
            }
            final Sequence sequence = cloneSource != null
                ? new Sequence(sequenceName, cloneSource.peekNextValue(), cloneSource.getIncrement(),
                    cloneSource.isOrder(), cloneSource.getComment())
                : new Sequence(sequenceName, startValue, increment, order, comment);

            final Schema schema = sequenceSchema;
            schema.requireCreatable(Privilege.CREATE_SEQUENCE);
            final CreationGrants grantsOfNew = FutureGrants.ofNew(catalog, "SEQUENCE", schema,
                sequence.getName());
            sequence.setOwner(grantsOfNew.owner());
            schema.addSequence(sequence);
            grantsOfNew.apply();
            logger.trace("Created sequence: {} with START={}, INCREMENT={}", sequenceQualifiedName, startValue, increment);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object", ctx.qualifiedName(0));
            logger.debug("Sequence already exists (IF NOT EXISTS): {}", sequenceName);
        }
        return null;
    }

    /**
     * CREATE COMPUTE POOL. The name must be upper case; MIN_NODES, MAX_NODES and INSTANCE_FAMILY
     * are required and reported missing in that order; MIN_NODES is at least 1 and no greater than
     * MAX_NODES; INSTANCE_FAMILY must name a family from the account catalog. Every rejection uses
     * a real account's wording.
     */
    public Object handleCreateComputePool(final FrostlakeParser.CreateStatementContext ctx,
            final boolean ifNotExists) {
        final String poolName = getText(ctx.identifier(0));
        if (!poolName.equals(poolName.toUpperCase(Locale.ROOT))) {
            throw new RuntimeException(
                "Invalid compute pool name: '" + poolName + "'. Name must be uppercase.");
        }
        if (ifNotExists && catalog.hasComputePoolExact(poolName)) {
            logger.debug("Compute pool already exists (IF NOT EXISTS): {}", poolName);
            return null;
        }
        final ComputePool pool = new ComputePool(poolName);
        Integer minNodes = null;
        Integer maxNodes = null;
        String instanceFamily = null;
        for (final FrostlakeParser.ComputePoolOptionContext option : ctx.computePoolOption()) {
            if (option.MIN_NODES() != null) {
                minNodes = Integer.valueOf(option.INTEGER_LITERAL().getText());
            } else if (option.MAX_NODES() != null) {
                maxNodes = Integer.valueOf(option.INTEGER_LITERAL().getText());
            } else if (option.INSTANCE_FAMILY() != null) {
                instanceFamily = getText(option.identifier()).toUpperCase(Locale.ROOT);
            } else if (option.AUTO_RESUME() != null) {
                pool.setAutoResume("TRUE".equalsIgnoreCase(option.booleanValue().getText()));
            } else if (option.INITIALLY_SUSPENDED() != null) {
                if ("TRUE".equalsIgnoreCase(option.booleanValue().getText())) {
                    pool.setState(ComputePoolState.SUSPENDED);
                }
            } else if (option.AUTO_SUSPEND_SECS() != null) {
                pool.setAutoSuspendSecs(Integer.parseInt(option.INTEGER_LITERAL().getText()));
            } else if (option.COMMENT() != null) {
                pool.setComment(ddl.extractStringLiteral(option.STRING_LITERAL()));
            } else if (option.PLACEMENT_GROUP() != null) {
                // Placement groups are a region registry this engine has none of, so the lookup
                // fails exactly as a real account's does.
                final String group = ddl.extractStringLiteral(option.STRING_LITERAL());
                throw new RuntimeException("Invalid value '" + group + "' for property"
                    + " 'PLACEMENT_GROUP':\nPlacement group '" + group
                    + "' does not exist in this region.");
            } else if (option.BACKUP_INSTANCE_FAMILIES() != null) {
                final List<String> families = new ArrayList<>();
                for (final TerminalNode family : option.stringLiteralList().STRING_LITERAL()) {
                    families.add(ddl.extractStringLiteral(family));
                }
                pool.setBackupInstanceFamilies(families);
            }
        }
        final List<String> missing = new ArrayList<>();
        if (minNodes == null) {
            missing.add("MIN_NODES");
        }
        if (maxNodes == null) {
            missing.add("MAX_NODES");
        }
        if (instanceFamily == null) {
            missing.add("INSTANCE_FAMILY");
        }
        if (!missing.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of("Missing option(s): " + missing));
        }
        if (minNodes.intValue() < 1) {
            throw new RuntimeException(SqlCompilationError.of(
                "invalid value '" + minNodes + "' for property 'MIN_NODES'"));
        }
        if (minNodes.intValue() > maxNodes.intValue()) {
            throw new RuntimeException(SqlCompilationError.of(
                "invalid property combination 'MIN_NODES'='" + minNodes
                + "' and 'MAX_NODES'='" + maxNodes + "'"));
        }
        if (!InstanceFamilies.isValid(instanceFamily)) {
            throw new RuntimeException("Invalid instance family " + instanceFamily
                + ". Please refer to Snowflake documentation for supported instance families.");
        }
        // Backup families obey the same rules at CREATE as at ALTER: each must be in the catalog
        // and none may match the primary.
        for (final String backup : pool.getBackupInstanceFamilies()) {
            if (!InstanceFamilies.isValid(backup)) {
                throw new RuntimeException("Invalid instance family " + backup
                    + ". Please refer to Snowflake documentation for supported instance families.");
            }
            if (backup.equalsIgnoreCase(instanceFamily)) {
                throw new RuntimeException("Invalid BACKUP_INSTANCE_FAMILIES for compute pool:"
                    + " BACKUP_INSTANCE_FAMILIES contains '" + backup
                    + "' which matches the primary INSTANCE_FAMILY. Each backup must be different"
                    + " from the primary.");
            }
        }
        pool.setMinNodes(minNodes.intValue());
        pool.setMaxNodes(maxNodes.intValue());
        pool.setInstanceFamily(instanceFamily);
        if (ctx.FOR() != null && ctx.APPLICATION() != null) {
            pool.setApplication(getText(ctx.identifier(1)));
        }
        if (ctx.tagList() != null) {
            InlineTags.apply(pool, ctx.tagList(), queryExecutor);
        }
        catalog.createComputePool(pool);
        logger.trace("Created compute pool: {}", poolName);
        return null;
    }

    /**
     * What a warehouse's type implies at creation: a Snowpark-optimized warehouse is MEDIUM unless the statement
     * sizes it or gives it a 1X memory constraint, and an adaptive warehouse takes no INITIALLY_SUSPENDED.
     */
    private static void applyWarehouseTypeDefaults(final Warehouse warehouse,
                                                   final FrostlakeParser.WarehousePropertiesContext properties) {
        boolean sized = false;
        for (final FrostlakeParser.WarehousePropertyContext property : properties.warehouseProperty()) {
            if (property.WAREHOUSE_SIZE() != null) {
                sized = true;
            }
            if (property.INITIALLY_SUSPENDED() != null && "ADAPTIVE".equals(warehouse.getWarehouseType())) {
                throw new RuntimeException(SqlCompilationError.of(
                    "invalid property 'INITIALLY_SUSPENDED' for 'WAREHOUSE'"));
            }
        }
        // The 16X default constraint needs a MEDIUM warehouse; a 1X one stays X-Small.
        if (!sized && "SNOWPARK-OPTIMIZED".equals(warehouse.getWarehouseType())
                && !warehouse.getResourceConstraint().startsWith("MEMORY_1X")) {
            warehouse.setSize(WarehouseSize.MEDIUM);
        }
    }

    public Object handleCreateWarehouse(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String warehouseName = getText(ctx.identifier(0));
        if (ctx.or_replace() != null) {
            try { catalog.dropWarehouseExact(warehouseName); } catch (final RuntimeException ignored) {}
        }
        try {
            WarehouseSize size = WarehouseSize.X_SMALL;
            if (ctx.warehouseProperties() != null) {
                size = ddl.parseWarehouseSize(ctx.warehouseProperties());
            }

            catalog.createWarehouse(warehouseName, size);

            final Warehouse warehouse = catalog.getWarehouseExact(warehouseName);
            if (ctx.warehouseProperties() != null) {
                try {
                    ddl.applyWarehouseProperties(warehouse, ctx.warehouseProperties());
                    applyWarehouseTypeDefaults(warehouse, ctx.warehouseProperties());
                } catch (final RuntimeException refused) {
                    // A refused property leaves no warehouse behind.
                    catalog.dropWarehouseExact(warehouseName);
                    throw refused;
                }
            }

            final String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                warehouse.setComment(comment);
            }
            // A new warehouse becomes the session's current warehouse.
            catalog.useWarehouse(warehouseName);

            logger.trace("Created warehouse: {}", warehouseName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
            // The warehouse that was already there becomes the session's current one all the same (live-verified).
            catalog.useWarehouse(warehouseName);
            logger.debug("Warehouse already exists (IF NOT EXISTS): {}", warehouseName);
        }
        return null;
    }

    /**
     * The CREATE STAGE option vocabulary a real account accepts — anything else refuses with the
     * invalid-property shape. The cloud-side names are accepted and inert (the engine cannot reach
     * a real bucket); unmeasured names are deliberately NOT refused here.
     */
    private static final Set<String> STAGE_OPTION_NAMES = Set.of(
        "CREDENTIALS", "STORAGE_INTEGRATION", "DIRECTORY", "COPY_OPTIONS",
        "AWS_ROLE", "AWS_EXTERNAL_ID", "SNOWFLAKE_IAM_USER", "ENCRYPTION");

    /** Whether a word names a stage property a CREATE STAGE or an ALTER STAGE … SET takes by a plain name. */
    static boolean isStageOption(final String name) {
        return STAGE_OPTION_NAMES.contains(name);
    }

    /** Collect a parenthesized option list's key=value pairs (values unquoted, keys upper). */
    private void collectParenOptions(final FrostlakeParser.ParenOptionListContext list,
                                     final Map<String, String> into) {
        for (final FrostlakeParser.ParenOptionContext option : list.parenOption()) {
            final String key = option.optionKey().getText().toUpperCase(Locale.ROOT);
            if (option.LPAREN() != null) {
                into.put(key, listedValues(option.stringLiteralList()));
                continue;
            }
            String value = option.copyOptionValue() != null ? option.copyOptionValue().getText() : "";
            if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
                value = value.substring(1, value.length() - 1);
            }
            into.put(key, value);
        }
    }

    /**
     * A list option's values as a stage lists them: {@code NULL_IF = ('\\N', 'NULL')} reads {@code [\\N, NULL]},
     * a backslash doubled, and a list of nothing but empty texts reads {@code []} (live-verified).
     *
     * @param list the written list, or null for an empty pair of parentheses
     * @return the listed text
     */
    static String listedValues(final FrostlakeParser.StringLiteralListContext list) {
        final List<String> values = new ArrayList<>();
        if (list != null) {
            for (final TerminalNode node : list.STRING_LITERAL()) {
                final String value = ParseTreeText.extractStringLiteral(node);
                if (!value.isEmpty()) {
                    values.add(value.replace("\\", "\\\\"));
                }
            }
        }
        return "[" + String.join(", ", values) + "]";
    }

    /**
     * The CREATE privilege a new object of a schema asks, unless the statement makes a temporary one, which needs
     * no privilege on the schema it is named in (live-verified); nothing is created in INFORMATION_SCHEMA either way.
     */
    private void requireCreateUnlessTemporary(final FrostlakeParser.CreateStatementContext ctx,
                                              final Privilege createPrivilege, final String qualifiedName) {
        final Schema schema = ddl.resolveSchemaFromQualifiedName(qualifiedName);
        if (TemporaryObjectStatements.isTemporary(ctx)) {
            schema.requireCreatable(createPrivilege);
        } else {
            ddl.checkCreatePrivilege(createPrivilege, schema);
        }
    }

    /**
     * CREATE [OR REPLACE] GIT REPOSITORY: ORIGIN and API_INTEGRATION are required, each missing one named (live's
     * {@code Missing option(s): [ORIGIN]}), then the API integration is judged ({@link ExternalFunctions}). A
     * repository whose integration passes is refused all the same: it would be cloned from its ORIGIN, which this
     * engine does not reach.
     *
     * @param ctx the statement
     * @return null, as every CREATE handler answers
     */
    public Object handleCreateGitRepository(final FrostlakeParser.CreateStatementContext ctx) {
        final String repositoryName = getText(ctx.qualifiedName(0));
        ddl.resolveSchemaFromQualifiedName(repositoryName);
        boolean origin = false;
        FrostlakeParser.QualifiedNameContext integration = null;
        for (final FrostlakeParser.GitRepositoryOptionContext option : ctx.gitRepositoryOption()) {
            if (option.optionName == null) {
                continue;
            }
            final String name = SqlIdentifiers.canonical(option.optionName);
            if ("ORIGIN".equals(name) && option.STRING_LITERAL() != null) {
                origin = true;
            } else if ("API_INTEGRATION".equals(name) && option.qualifiedName() != null) {
                integration = option.qualifiedName();
            } else if (!"GIT_CREDENTIALS".equals(name) || option.qualifiedName() == null) {
                throw ExternalFunctions.unknownOption(option.optionName.getStart());
            }
        }
        final List<String> missing = new ArrayList<>();
        if (!origin) {
            missing.add("ORIGIN");
        }
        if (integration == null) {
            missing.add("API_INTEGRATION");
        }
        if (!missing.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of("Missing option(s): " + missing));
        }
        ExternalFunctions.requireGitIntegration(catalog, integration);
        throw new RuntimeException(SqlCompilationError.of("Git repository "
            + SqlIdentifiers.spellCanonical(ddl.extractObjectName(repositoryName))
            + " cannot be created: its ORIGIN is not reached from this engine."));
    }

    public Object handleCreateStage(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String stageName = getText(ctx.qualifiedName(0));
        if (ctx.or_replace() != null) {
            queryExecutor.requireOwnership("STAGE", qualifiedNameParts(ctx.qualifiedName(0)), null);
            // Asked before the stage a replacement drops is gone.
            requireCreateUnlessTemporary(ctx, Privilege.CREATE_STAGE, stageName);
        }
        try {
            DDLCommandHandler.refuseTakenName(ctx.or_replace() == null && !ifNotExists
                    && !TemporaryObjectStatements.isTemporary(ctx),
                ddl.resolveSchemaFromQualifiedName(stageName).hasStageExact(ddl.extractObjectName(stageName)),
                qualifiedNameParts(ctx.qualifiedName(0)));
            requireCreateUnlessTemporary(ctx, Privilege.CREATE_STAGE, stageName);
            // A URL-less CREATE STAGE makes an internal named stage (Snowflake's default when URL is omitted);
            // a URL (except file://) denotes an external stage.
            final boolean hasProps = ctx.stageProperties() != null;
            String url = hasProps && ctx.stageProperties().URL() != null
                ? ddl.extractStringLiteral(ctx.stageProperties().STRING_LITERAL()) : null;
            String fileFormat = "CSV";
            boolean encryption = false;
            String comment = null;
            boolean sawFileFormat = false;
            boolean sawCloudAccessOption = false;
            Integration storageIntegration = null;
            boolean credentials = false;
            boolean encryptionKey = false;
            StageDirectoryOptions directory = null;
            final Map<String, String> formatOptions = new LinkedHashMap<>();
            final Map<String, String> copyOptions = new LinkedHashMap<>();
            String declaredEncryptionType = null;

            if (hasProps) {
                for (final FrostlakeParser.StageOptionContext option : ctx.stageProperties().stageOption()) {
                    if (option.FILE_FORMAT() != null) {
                        // A SECOND FILE_FORMAT group conflicts on its parameters, live-verified
                        // (single-line refusal naming the parameter).
                        if (sawFileFormat) {
                            throw new RuntimeException(SqlCompilationError.PREFIX
                                + " conflicting values file format parameter 'TYPE'");
                        }
                        sawFileFormat = true;
                        // FILE_FORMAT = 'name' | db.schema.name | (TYPE=X ... | FORMAT_NAME=...)
                        if (option.STRING_LITERAL() != null) {
                            fileFormat = ddl.extractStringLiteral(option.STRING_LITERAL());
                            FileFormatReference.require(catalog, SqlIdentifiers.canonicalText(fileFormat));
                            formatOptions.put("FORMAT_NAME", fileFormat);
                        } else if (option.qualifiedName() != null) {
                            fileFormat = getText(option.qualifiedName());
                            FileFormatReference.require(catalog, fileFormat);
                            // The listing keeps the name as the statement wrote it.
                            formatOptions.put("FORMAT_NAME", ddl.getOriginalText(option.qualifiedName()));
                        } else if (option.parenOptionList() != null) {
                            // Read and judged as every FILE_FORMAT list is (see FileFormatClause): the format
                            // FORMAT_NAME names wins, else TYPE as written, and the options are kept as a named
                            // format keeps its own.
                            final FileFormatClause written = FileFormatClause.read(option.parenOptionList(),
                                new FileFormat("WRITTEN", "CSV"), true);
                            if (written.formatName() != null) {
                                FileFormatReference.require(catalog,
                                    SqlIdentifiers.canonicalText(written.formatName()));
                                fileFormat = written.formatName();
                                formatOptions.put("FORMAT_NAME", written.formatNameWritten());
                            } else if (written.writtenType() != null) {
                                fileFormat = written.writtenType();
                                formatOptions.put("TYPE", written.writtenType());
                            }
                            formatOptions.putAll(written.format().getOptions());
                        }
                    } else if (option.ENCRYPTION() != null) {
                        encryption = option.booleanValue() != null && option.booleanValue().TRUE() != null;
                        if (option.parenOptionList() != null) {
                            final Map<String, String> encryptionOptions = new LinkedHashMap<>();
                            collectParenOptions(option.parenOptionList(), encryptionOptions);
                            declaredEncryptionType = encryptionOptions.get("TYPE");
                            encryptionKey = encryptionOptions.containsKey("MASTER_KEY")
                                || encryptionOptions.containsKey("KMS_KEY_ID");
                            // Only Snowflake-managed encryption fits an internal stage, live-verified.
                            if (url == null && declaredEncryptionType != null
                                    && !declaredEncryptionType.toUpperCase(Locale.ROOT).startsWith("SNOWFLAKE_")) {
                                throw new RuntimeException(SqlCompilationError.of("Cannot set URL,"
                                    + " credentials, or encryption key of an internal or temporary stage."));
                            }
                        }
                    } else if (option.COMMENT() != null) {
                        comment = ddl.extractStringLiteral(option.STRING_LITERAL());
                    } else if (option.COPY_OPTIONS() != null) {
                        if (option.stageCopyOptionList() != null) {
                            copyOptions.putAll(StageCopyOptions.read(option.stageCopyOptionList()));
                        }
                    } else if (option.identifier() != null) {
                        if (option.identifier().getText().startsWith("\"")) {
                            // A quoted word is no property's name, whatever it spells (live-verified).
                            throw ParameterRegistry.invalidProperty(option.identifier().getText(), "STAGE");
                        }
                        final String key = ParseTreeText.getIdentifier(option.identifier()).toUpperCase(Locale.ROOT);
                        if ("URL".equals(key) && option.copyOptionValue() != null
                                && option.copyOptionValue().STRING_LITERAL() != null) {
                            // Stage options come in ANY order live, URL included. The grammar anchors
                            // URL first, so one written after another option arrives here instead.
                            url = ddl.extractStringLiteral(option.copyOptionValue().STRING_LITERAL());
                            continue;
                        }
                        if (!STAGE_OPTION_NAMES.contains(key)) {
                            throw ParameterRegistry.invalidProperty(key, "STAGE");
                        }
                        if ("STORAGE_INTEGRATION".equals(key)) {
                            // The integration is looked up before the stage's own shape is judged.
                            storageIntegration = StorageIntegrationReference.require(catalog,
                                queryExecutor.getSecurityManager(), option.copyOptionValue());
                        }
                        if ("CREDENTIALS".equals(key) || "STORAGE_INTEGRATION".equals(key)) {
                            sawCloudAccessOption = true;
                            credentials = credentials || "CREDENTIALS".equals(key);
                        } else if ("DIRECTORY".equals(key)) {
                            directory = StageDirectoryOptions.read(option.parenOptionList(), true);
                        } else if ("COPY_OPTIONS".equals(key) && option.parenOptionList() != null) {
                            copyOptions.putAll(StageCopyOptions.read(option.parenOptionList()));
                        }
                    }
                }
            }

            // Cloud access options make no sense without a location, live-verified wording.
            if (sawCloudAccessOption && url == null) {
                throw new RuntimeException(SqlCompilationError.of("invalid stage ("
                    + ddl.extractObjectName(stageName).toUpperCase(Locale.ROOT) + "): location not specified"));
            }

            CloudStageUrls.require(url);
            StorageIntegrationReference.requireLocation(storageIntegration, url);
            refuseLocalUrlUnlessEnabled(url);
            // A replacement drops the stage it replaces once the new one is judged whole, so a refused statement
            // leaves that stage as it was (live-verified).
            if (ctx.or_replace() != null) {
                try {
                    catalog.dropStageExact(stageName);
                } catch (final RuntimeException ignored) {
                    // No stage of that name to replace.
                }
            }

            final StageType type = (url == null || url.startsWith("file://")) ? StageType.INTERNAL : StageType.EXTERNAL;
            catalog.createStage(stageName, type, url, fileFormat, encryption, comment);
            if (url == null) {
                // A new internal stage is empty on a real account too.
                queryExecutor.resetInternalStageDir(stageName);
            }

            final Stage created = catalog.getStage(stageName);
            created.getFileFormatOptions().putAll(formatOptions);
            created.getCopyOptions().putAll(copyOptions);
            created.setEncryptionType(declaredEncryptionType);
            created.setTemporary(ctx.TEMPORARY() != null || ctx.TEMP() != null);
            created.setCredentials(credentials);
            created.setEncryptionKey(encryptionKey);
            if (directory != null) {
                directory.applyTo(created);
                // A directory table enabled at creation registers the files its stage already holds, unless
                // REFRESH_ON_CREATE says otherwise; one enabled later registers none before its first refresh. A new
                // internal stage holds none, so its replayed CREATE registers none either: the files on disk then are
                // the ones the statements after it staged.
                final boolean replayedInternal = url == null && StatementClock.isReplay();
                if (directory.enables() && directory.refreshesOnCreate() && !replayedInternal) {
                    final Schema owner = catalog.resolveOwningSchema(stageName);
                    StageDirectoryRefresh.refresh(queryExecutor, created, owner.getDatabaseName(),
                        owner.getDatabaseName() + "." + owner.getName() + "." + created.getName(), "");
                }
            }

            final String statementComment = ddl.extractCommentFromList(ctx.commentClause());
            if (statementComment != null) {
                final Stage stage = catalog.getStage(stageName);
                stage.setComment(statementComment);
            }

            logger.trace("Created stage: {}", stageName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object", ctx.qualifiedName(0));
            logger.debug("Stage already exists (IF NOT EXISTS): {}", stageName);
        }
        return null;
    }

    /**
     * CREATE CORTEX SEARCH SERVICE. WAREHOUSE and TARGET_LAG are required and reported the way a real
     * account reports them — {@code Missing option(s): [WAREHOUSE]} — rather than as a syntax error, so
     * the grammar accepts the options in any order and the check lives here.
     *
     * <p>The searched column and every attribute column must be projected by the defining query, which
     * is run once at create time both to establish that column list and to reject a bad name the way
     * live does: as an {@code invalid identifier}.
     */
    public Object handleCreateCortexSearchService(final FrostlakeParser.CreateStatementContext ctx,
                                                  final boolean ifNotExists) {
        final String serviceName = getText(ctx.qualifiedName(0));
        final String searchColumn = SqlIdentifiers.canonical(ctx.identifier(0));
        final List<String> attributeColumns = new ArrayList<>();
        if (ctx.identifierList() != null) {
            for (final FrostlakeParser.IdentifierContext attribute : ctx.identifierList().identifier()) {
                attributeColumns.add(SqlIdentifiers.canonical(attribute));
            }
        }

        String warehouse = null;
        String targetLag = null;
        String embeddingModel = null;
        String comment = null;
        for (final FrostlakeParser.CortexSearchOptionContext option : ctx.cortexSearchOption()) {
            if (option.WAREHOUSE() != null) {
                warehouse = SqlIdentifiers.canonical(option.identifier());
            } else if (option.TARGET_LAG() != null) {
                targetLag = ddl.extractStringLiteral(option.STRING_LITERAL());
            } else if (option.EMBEDDING_MODEL() != null) {
                embeddingModel = ddl.extractStringLiteral(option.STRING_LITERAL());
            } else if (option.COMMENT() != null) {
                comment = ddl.extractStringLiteral(option.STRING_LITERAL());
            }
        }
        if (warehouse == null) {
            throw new RuntimeException(SqlCompilationError.of("Missing option(s): [WAREHOUSE]"));
        }
        if (targetLag == null) {
            throw new RuntimeException(SqlCompilationError.of("Missing option(s): [TARGET_LAG]"));
        }
        WarehouseReference.require(catalog, warehouse);
        ddl.checkCreatePrivilege(Privilege.CREATE_CORTEX_SEARCH_SERVICE, ddl.resolveSchemaFromQualifiedName(serviceName));

        // The name's parts off the parse tree: a quoted name keeps its case, and is that service alone.
        final String[] serviceParts = qualifiedNameParts(ctx.qualifiedName(0));
        final Schema serviceSchema = catalog.requireOwningSchema(QualifiedName.of(serviceParts));
        final String ownName = serviceParts[serviceParts.length - 1];
        if (ctx.or_replace() != null && serviceSchema.hasCortexSearchServiceExact(ownName)) {
            serviceSchema.dropCortexSearchServiceExact(ownName);
        }
        if (ctx.or_replace() == null && serviceSchema.hasCortexSearchServiceExact(ownName)) {
            if (ifNotExists) {
                ConditionalDdlOutcome.createSkipped();
                return null;
            }
            throw new RuntimeException(SqlCompilationError.of("Object '"
                + SqlIdentifiers.spellCanonical(ownName) + "' already exists."));
        }

        final String definition = ddl.getOriginalText(ctx.selectStatement());
        final List<String> columns = definitionColumns(ctx.selectStatement());
        requireProjected(columns, searchColumn);
        for (final String attribute : attributeColumns) {
            requireProjected(columns, attribute);
        }

        final CortexSearchService service = new CortexSearchService(
            ownName, searchColumn, attributeColumns, columns,
            warehouse, targetLag, embeddingModel, definition, comment);
        service.setOwner(catalog.currentRoleForOwner());
        serviceSchema.addCortexSearchService(service);
        logger.trace("Created Cortex search service: {}", serviceName);
        return null;
    }

    /** The column names the defining query projects, taken from running it once. */
    private List<String> definitionColumns(final FrostlakeParser.SelectStatementContext select) {
        final List<String> columns = new ArrayList<>();
        for (final ResultSetColumn column
                : queryExecutor.executeSelectFromContext(select).getColumns()) {
            columns.add(column.getName());
        }
        return columns;
    }

    private void requireProjected(final List<String> columns, final String column) {
        for (final String projected : columns) {
            if (projected.equalsIgnoreCase(column)) {
                return;
            }
        }
        throw new RuntimeException(SqlCompilationError.invalidIdentifier(column));
    }

    public Object handleCreateFileFormat(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String name = getText(ctx.qualifiedName(0));
        // The simple name is the parse tree's last identifier part — never a re-split of the
        // flattened text, which breaks a quoted name containing a dot.
        final String[] nameParts = qualifiedNameParts(ctx.qualifiedName(0));
        final String simpleName = nameParts[nameParts.length - 1];
        if (ctx.or_replace() != null) {
            queryExecutor.requireOwnership("FILE_FORMAT", nameParts, null);
            // Asked before the file format a replacement drops is gone.
            requireCreateUnlessTemporary(ctx, Privilege.CREATE_FILE_FORMAT, name);
        }
        try {
            // A file format whose name differs only in case is another one (live-verified).
            DDLCommandHandler.refuseTakenName(ctx.or_replace() == null && !ifNotExists
                    && !TemporaryObjectStatements.isTemporary(ctx), catalog.hasFileFormatExact(name), nameParts);
            requireCreateUnlessTemporary(ctx, Privilege.CREATE_FILE_FORMAT, name);
            final FileFormat fileFormat = new FileFormat(simpleName, "CSV");
            if (TemporaryObjectStatements.isTemporary(ctx)) {
                // Its session's alone, and gone when that session ends (live-verified).
                fileFormat.markTemporary(StatementSession.current());
            }
            // The future grants of the schema or its database cover the new format (live-verified).
            final CreationGrants grantsOfNew = TemporaryObjectStatements.isTemporary(ctx) ? null
                : FutureGrants.ofNew(catalog, "FILE_FORMAT", catalog.requireOwningSchema(QualifiedName.of(nameParts)),
                    simpleName);
            fileFormat.setOwner(grantsOfNew == null ? catalog.currentRoleForOwner() : grantsOfNew.owner());
            final FileFormatClause written = FileFormatClause.read(ctx.copyFormatOption(), fileFormat, true, false);
            if (written.comment() != null) {
                fileFormat.setComment(written.comment());
            }
            final String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                fileFormat.setComment(comment);
            }
            if (ctx.or_replace() != null) {
                // The options are judged before the format a replacement drops is gone: a refused CREATE OR REPLACE
                // leaves the old one as it was (live-verified).
                try {
                    catalog.dropFileFormatExact(name);
                } catch (final RuntimeException ignored) {
                    // OR REPLACE: nothing to drop
                }
            }
            // The options are judged BEFORE existence: an invalid option refuses even over an existing
            // format, IF NOT EXISTS or not (live-verified).
            if (catalog.hasFileFormatExact(name)) {
                throw new NameTakenException(SqlCompilationError.of("Object '"
                    + SqlIdentifiers.spellCanonical(catalog.getFileFormat(name).getName()) + "' already exists."));
            }
            catalog.addFileFormat(name, fileFormat);
            if (grantsOfNew != null) {
                grantsOfNew.apply();
            }
            logger.trace("Created file format: {}", simpleName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object", ctx.qualifiedName(0));
        }
        return null;
    }

    public Object handleAlterFileFormat(final FrostlakeParser.AlterStatementContext ctx) {
        final String name = getText(ctx.qualifiedName());
        // Named exactly: a quoted name in another case is another file format, which is not there, refused by its
        // full path (live-verified).
        final FileFormat fileFormat = catalog.getFileFormatExact(name);
        if (fileFormat == null) {
            if (ctx.if_exists() != null) {
                return null;
            }
            throw new RuntimeException(SqlCompilationError.doesNotExist("File format",
                catalog.qualifiedExactName(name)));
        }
        final FrostlakeParser.FileFormatActionContext action = ctx.fileFormatAction();
        if (action.RENAME() == null) {
            queryExecutor.requirePrivilege(Privilege.MODIFY, "FILE_FORMAT", name);
        }
        if (action.RENAME() != null) {
            final String newName = getText(action.qualifiedName());
            final String[] newParts = qualifiedNameParts(action.qualifiedName());
            final String newSimple = newParts[newParts.length - 1];
            catalog.resolveOwningSchema(newName).requireCreatable(Privilege.CREATE_FILE_FORMAT);
            // The new name is taken: refused before the old one is given up.
            if (catalog.hasFileFormat(newName)) {
                throw DDLCommandHandler.namedAsWritten(new NameTakenException(SqlCompilationError.of("Object '"
                    + SqlIdentifiers.spellCanonical(newSimple) + "' already exists.")), newParts);
            }
            // A renamed file format is created under its new name: its schema is asked for the privilege to
            // create one, once the name is found free (live-verified).
            queryExecutor.requireCreatePrivilege(Privilege.CREATE_FILE_FORMAT, catalog.resolveOwningSchema(newName));
            final String grantedBefore = catalog.resolveOwningSchema(name).qualifiedName(fileFormat.getName());
            catalog.dropFileFormat(name);
            fileFormat.setName(newSimple);
            catalog.addFileFormat(newName, fileFormat);
            GrantNames.renamed(catalog, "FILE_FORMAT", grantedBefore,
                catalog.resolveOwningSchema(newName).qualifiedName(newSimple));
        } else {
            // An ALTER may not change the type; its COMMENT is the format's.
            final FileFormatClause written = FileFormatClause.read(action.copyFormatOption(), fileFormat, false, false);
            if (written.comment() != null) {
                fileFormat.setComment(written.comment());
            }
        }
        // Every ALTER is DDL on the file format, a rename included: its LAST_ALTERED moves.
        fileFormat.markDdl();
        return null;
    }

    /**
     * The refusal a local stage URL earns unless {@code stage.file.urlEnabled} opts in — matching a
     * real account (live-verified, three shapes): a well-formed URL with a local scheme
     * ({@code file:///dir}) answers {@code invalid URL prefix found in: '<url>'}, the quoted form;
     * a malformed {@code file://host} spelling and a bare path with no scheme both answer the
     * unquoted {@code invalid URL: <url>}. Cloud spellings ({@code s3://}, …) and URL-less internal
     * stages pass untouched.
     */
    private void refuseLocalUrlUnlessEnabled(final String url) {
        if (url == null || queryExecutor.getEngineConfig() == null
                || queryExecutor.getEngineConfig().isStageFileUrlEnabled()) {
            return;
        }
        if (!url.contains("://")) {
            throw new RuntimeException(SqlCompilationError.of("invalid URL: " + url));
        }
        if (url.startsWith("file:///")) {
            throw new RuntimeException(SqlCompilationError.of(
                "invalid URL prefix found in: '" + url + "'"));
        }
        if (url.startsWith("file://")) {
            throw new RuntimeException(SqlCompilationError.of("invalid URL: " + url));
        }
    }
}
