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

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.*;
import dev.frostlake.metastore.model.*;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.*;

import org.antlr.v4.runtime.tree.TerminalNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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
        String taskQualifiedName = getText(ctx.qualifiedName(0));
        String taskName = ddl.extractObjectName(taskQualifiedName);
        boolean orReplace = ctx.or_replace() != null;

        try {
            ddl.checkCreatePrivilege(Privilege.CREATE_TASK, ContainerType.SCHEMA,
                ddl.resolveSchemaFromQualifiedName(taskQualifiedName).getName());
            if (orReplace) {
                try {
                    ddl.resolveSchemaFromQualifiedName(taskQualifiedName).dropTask(taskName);
                } catch (final RuntimeException ignored) {}
            }
            String warehouse = ctx.warehouseClause() != null ? ddl.extractWarehouseName(ctx.warehouseClause()) : null;

            // Schedule is now inside taskOptions
            String schedule = null;
            ScheduleType scheduleType = null;

            List<String> predecessors = new ArrayList<>();
            if (ctx.afterClause() != null) {
                for (final FrostlakeParser.QualifiedNameContext qn : ctx.afterClause().qualifiedName()) {
                    predecessors.add(getText(qn).toUpperCase());
                }
            }

            String sqlText;
            if (ctx.taskBody().sqlStatement() != null) {
                sqlText = ddl.getOriginalText(ctx.taskBody().sqlStatement());
            } else if (ctx.taskBody().callStatement() != null) {
                sqlText = ddl.getOriginalText(ctx.taskBody().callStatement());
            } else if (ctx.taskBody().executeImmediateStatement() != null) {
                sqlText = ddl.getOriginalText(ctx.taskBody().executeImmediateStatement());
            } else {
                throw new RuntimeException("Task body is required");
            }

            // Parse taskOptions first to extract schedule before constructing Task
            boolean allowOverlapping = false;
            long timeoutMs = 3600000L;
            String optionComment = null;
            int suspendAfterFailures = 10;
            int autoRetryAttempts = 0;
            String managedWarehouseSize = null;
            String serverlessMaxStmtSize = null;
            String targetInterval = null;
            String errorIntegration = null;
            int minTriggerInterval = 30;
            if (ctx.taskOptions() != null) {
                for (final FrostlakeParser.TaskOptionContext opt : ctx.taskOptions().taskOption()) {
                    if (opt.scheduleClause() != null) {
                        schedule = ddl.extractStringLiteral(opt.scheduleClause().STRING_LITERAL());
                        scheduleType = schedule.toUpperCase().contains("CRON")
                            ? ScheduleType.CRON : ScheduleType.MINUTES;
                        if (scheduleType == ScheduleType.CRON) {
                            // Snowflake validates the expression: USING CRON <5 fields> <time zone>.
                            final String[] cronTokens = schedule.trim().split("\\s+");
                            final boolean shaped = cronTokens.length >= 8
                                && "USING".equalsIgnoreCase(cronTokens[0]) && "CRON".equalsIgnoreCase(cronTokens[1]);
                            if (!shaped) {
                                throw new RuntimeException("Invalid schedule: expected 'USING CRON "
                                    + "<minute> <hour> <day-of-month> <month> <day-of-week> <time zone>', got '"
                                    + schedule + "'");
                            }
                            for (int fieldIndex = 2; fieldIndex < 7; fieldIndex++) {
                                if (!cronTokens[fieldIndex].matches("[0-9*,/\\-LW#?A-Za-z]+")) {
                                    throw new RuntimeException("Invalid CRON field '" + cronTokens[fieldIndex]
                                        + "' in task schedule '" + schedule + "'");
                                }
                            }
                        }
                    } else if (opt.ALLOW_OVERLAPPING_EXECUTION() != null) {
                        allowOverlapping = "TRUE".equalsIgnoreCase(opt.booleanValue().getText());
                    } else if (opt.USER_TASK_TIMEOUT_MS() != null) {
                        timeoutMs = Long.parseLong(opt.INTEGER_LITERAL().getText());
                    } else if (opt.SUSPEND_TASK_AFTER_NUM_FAILURES() != null) {
                        suspendAfterFailures = Integer.parseInt(opt.INTEGER_LITERAL().getText());
                    } else if (opt.TASK_AUTO_RETRY_ATTEMPTS() != null) {
                        autoRetryAttempts = Integer.parseInt(opt.INTEGER_LITERAL().getText());
                    } else if (opt.USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE() != null) {
                        managedWarehouseSize = ddl.extractStringLiteral(opt.STRING_LITERAL());
                    } else if (opt.SERVERLESS_TASK_MAX_STATEMENT_SIZE() != null) {
                        serverlessMaxStmtSize = opt.STRING_LITERAL() != null
                            ? ddl.extractStringLiteral(opt.STRING_LITERAL())
                            : (opt.identifier() != null ? getText(opt.identifier()) : null);
                    } else if (opt.TARGET_COMPLETION_INTERVAL() != null) {
                        targetInterval = ddl.extractStringLiteral(opt.STRING_LITERAL());
                    } else if (opt.USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS() != null) {
                        minTriggerInterval = Integer.parseInt(opt.INTEGER_LITERAL().getText());
                    } else if (opt.ERROR_INTEGRATION() != null) {
                        errorIntegration = getText(opt.identifier());
                    } else if (opt.COMMENT() != null) {
                        optionComment = ddl.extractStringLiteral(opt.STRING_LITERAL());
                    }
                }
            }

            // Snowflake: a task is scheduled OR a DAG child, never both.
            if (!predecessors.isEmpty() && schedule != null) {
                throw new RuntimeException(
                    "Task " + taskName.toUpperCase()
                        + " cannot have both a schedule and a predecessor.");
            }

            Task task = new Task(taskName, schedule, scheduleType, sqlText, warehouse);
            for (final String pred : predecessors) task.addPredecessor(pred);
            task.setAllowOverlappingExecution(allowOverlapping);
            task.setUserTaskTimeoutMs(timeoutMs);
            task.setSuspendTaskAfterNumFailures(suspendAfterFailures);
            task.setTaskAutoRetryAttempts(autoRetryAttempts);
            if (managedWarehouseSize != null) task.setUserTaskManagedInitialWarehouseSize(managedWarehouseSize);
            if (serverlessMaxStmtSize != null) task.setServerlessTaskMaxStatementSize(serverlessMaxStmtSize);
            if (targetInterval != null) task.setTargetCompletionInterval(targetInterval);
            if (errorIntegration != null) task.setErrorIntegration(errorIntegration);
            task.setUserTaskMinimumTriggerIntervalInSeconds(minTriggerInterval);

            if (ctx.WHEN() != null && ctx.booleanExpr() != null) {
                task.setCondition(ddl.getOriginalText(ctx.booleanExpr()));
            }

            // Prefer COMMENT from taskOptions; fall back to standalone commentClause
            if (optionComment != null) {
                task.setComment(optionComment);
            } else {
                String comment = ddl.extractCommentFromList(ctx.commentClause());
                if (comment != null) task.setComment(comment);
            }

            Schema schema = ddl.resolveSchemaFromQualifiedName(taskQualifiedName);
            task.setOwner(catalog.currentRoleForOwner());
            schema.addTask(task);
            logger.trace("Created task: {}", taskQualifiedName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Task already exists (IF NOT EXISTS): {}", taskName);
        }
        return null;
    }

    public Object handleCreatePipe(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        String pipeName = getText(ctx.qualifiedName(0));
        if (ctx.or_replace() != null) {
            try { ddl.resolveCurrentSchema().dropPipe(pipeName.toUpperCase()); } catch (final RuntimeException ignored) {}
        }
        try {
            ddl.checkCreatePrivilege(Privilege.CREATE_PIPE, ContainerType.SCHEMA, ddl.resolveCurrentSchema().getName());
            // Extract COPY statement text
            String copyStatement = ddl.getOriginalText(ctx.copyStatement());

            // Parse pipe options
            boolean autoIngest = false;
            String awsSnsTopicArn = null;
            String errorIntegration = null;
            String integration = null;

            if (ctx.pipeOptions() != null) {
                for (final FrostlakeParser.PipeOptionContext optionCtx : ctx.pipeOptions().pipeOption()) {
                    if (optionCtx.AUTO_INGEST() != null) {
                        autoIngest = optionCtx.booleanValue().TRUE() != null;
                    } else if (optionCtx.AWS_SNS_TOPIC() != null) {
                        awsSnsTopicArn = ddl.extractStringLiteral(optionCtx.STRING_LITERAL());
                    } else if (optionCtx.ERROR_INTEGRATION() != null) {
                        errorIntegration = ddl.extractStringLiteral(optionCtx.STRING_LITERAL());
                    } else if (optionCtx.INTEGRATION() != null) {
                        integration = ddl.extractStringLiteral(optionCtx.STRING_LITERAL());
                    }
                }
            }

            // Create pipe with notification channel if AWS SNS topic is provided
            Pipe pipe;
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

            String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                pipe.setComment(comment);
            }

            Schema schema = ddl.resolveCurrentSchema();
            pipe.setOwner(catalog.currentRoleForOwner());
            schema.addPipe(pipe);
            logger.trace("Created pipe: {}", pipeName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Pipe already exists (IF NOT EXISTS): {}", pipeName);
        }
        return null;
    }

    public Object handleCreateSequence(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        String sequenceQualifiedName = getText(ctx.qualifiedName(0));
        String sequenceName = ddl.extractObjectName(sequenceQualifiedName);
        if (ctx.or_replace() != null) {
            try { ddl.resolveSchemaFromQualifiedName(sequenceQualifiedName).dropSequence(sequenceName); } catch (final RuntimeException ignored) {}
        }
        try {
            long startValue = 1;
            long increment = 1;
            boolean order = false; // Default is NOORDER

            if (ctx.sequenceOptions() != null) {
                for (final FrostlakeParser.SequenceOptionContext optionCtx : ctx.sequenceOptions().sequenceOption()) {
                    if (optionCtx.START() != null) {
                        long value = Long.parseLong(optionCtx.INTEGER_LITERAL().getText());
                        startValue = optionCtx.MINUS() != null ? -value : value;
                    } else if (optionCtx.INCREMENT() != null) {
                        long value = Long.parseLong(optionCtx.INTEGER_LITERAL().getText());
                        increment = optionCtx.MINUS() != null ? -value : value;
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
            Sequence sequence = new Sequence(sequenceName, startValue, increment, order, comment);

            Schema schema = ddl.resolveSchemaFromQualifiedName(sequenceQualifiedName);
            ddl.checkCreatePrivilege(Privilege.CREATE_SEQUENCE, ContainerType.SCHEMA, schema.getName());
            sequence.setOwner(catalog.currentRoleForOwner());
            schema.addSequence(sequence);
            logger.trace("Created sequence: {} with START={}, INCREMENT={}", sequenceQualifiedName, startValue, increment);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Sequence already exists (IF NOT EXISTS): {}", sequenceName);
        }
        return null;
    }

    public Object handleCreateWarehouse(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        String warehouseName = getText(ctx.identifier(0));
        if (ctx.or_replace() != null) {
            try { catalog.dropWarehouse(warehouseName); } catch (final RuntimeException ignored) {}
        }
        try {
            WarehouseSize size = WarehouseSize.X_SMALL;
            if (ctx.warehouseProperties() != null) {
                size = ddl.parseWarehouseSize(ctx.warehouseProperties());
            }

            catalog.createWarehouse(warehouseName, size);

            Warehouse warehouse = catalog.getWarehouse(warehouseName);
            if (ctx.warehouseProperties() != null) {
                ddl.applyWarehouseProperties(warehouse, ctx.warehouseProperties());
            }

            String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                warehouse.setComment(comment);
            }

            logger.trace("Created warehouse: {}", warehouseName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Warehouse already exists (IF NOT EXISTS): {}", warehouseName);
        }
        return null;
    }

    public Object handleCreateStage(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        String stageName = getText(ctx.qualifiedName(0));
        if (ctx.or_replace() != null) {
            try { catalog.dropStage(stageName); } catch (final RuntimeException ignored) {}
        }
        try {
            ddl.checkCreatePrivilege(Privilege.CREATE_STAGE, ContainerType.SCHEMA,
                ddl.resolveSchemaFromQualifiedName(stageName).getName());
            // A URL-less CREATE STAGE makes an internal named stage (Snowflake's default when URL is omitted);
            // a URL (except file://) denotes an external stage.
            final boolean hasProps = ctx.stageProperties() != null;
            String url = hasProps && ctx.stageProperties().URL() != null
                ? ddl.extractStringLiteral(ctx.stageProperties().STRING_LITERAL()) : null;
            String fileFormat = "CSV";
            boolean encryption = false;
            String comment = null;

            if (hasProps) {
                for (final FrostlakeParser.StageOptionContext option : ctx.stageProperties().stageOption()) {
                    if (option.FILE_FORMAT() != null) {
                        // FILE_FORMAT = 'name' | db.schema.name | (TYPE=X ... | FORMAT_NAME=...)
                        if (option.STRING_LITERAL() != null) {
                            fileFormat = ddl.extractStringLiteral(option.STRING_LITERAL());
                        } else if (option.qualifiedName() != null) {
                            fileFormat = getText(option.qualifiedName());
                        } else if (option.parenOptionList() != null) {
                            fileFormat = formatFromOptions(option.parenOptionList(), fileFormat);
                        }
                    } else if (option.ENCRYPTION() != null) {
                        encryption = option.booleanValue() != null && option.booleanValue().TRUE() != null;
                    } else if (option.COMMENT() != null) {
                        comment = ddl.extractStringLiteral(option.STRING_LITERAL());
                    }
                    // The generic identifier-keyed options (CREDENTIALS=(...), ...) are accepted and inert.
                }
            }

            StageType type = (url == null || url.startsWith("file://")) ? StageType.INTERNAL : StageType.EXTERNAL;
            catalog.createStage(stageName, type, url, fileFormat, encryption, comment);

            String statementComment = ddl.extractCommentFromList(ctx.commentClause());
            if (statementComment != null) {
                Stage stage = catalog.getStage(stageName);
                stage.setComment(statementComment);
            }

            logger.trace("Created stage: {}", stageName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Stage already exists (IF NOT EXISTS): {}", stageName);
        }
        return null;
    }

    public Object handleCreateFileFormat(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String name = getText(ctx.qualifiedName(0));
        final String simpleName = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
        if (ctx.or_replace() != null) {
            try {
                catalog.dropFileFormat(name);
            } catch (final RuntimeException ignored) {
                // OR REPLACE: nothing to drop
            }
        }
        try {
            ddl.checkCreatePrivilege(Privilege.CREATE_FILE_FORMAT, ContainerType.SCHEMA,
                ddl.resolveSchemaFromQualifiedName(name).getName());
            if (catalog.hasFileFormat(name)) {
                if (!ifNotExists) {
                    throw new RuntimeException("File format already exists: " + simpleName);
                }
                return null;
            }
            final FileFormat fileFormat = new FileFormat(simpleName, "CSV");
            applyFileFormatOptions(fileFormat, ctx.copyFormatOption());
            final String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                fileFormat.setComment(comment);
            }
            catalog.addFileFormat(name, fileFormat);
            logger.trace("Created file format: {}", simpleName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
        }
        return null;
    }

    public Object handleAlterFileFormat(final FrostlakeParser.AlterStatementContext ctx) {
        final String name = getText(ctx.qualifiedName());
        final FileFormat fileFormat = catalog.getFileFormat(name);
        if (fileFormat == null) {
            if (ctx.if_exists() != null) {
                return null;
            }
            throw new RuntimeException(SqlCompilationError.doesNotExist("File format", name));
        }
        final FrostlakeParser.FileFormatActionContext action = ctx.fileFormatAction();
        if (action.RENAME() != null) {
            final String newName = getText(action.qualifiedName());
            final String newSimple = newName.contains(".") ? newName.substring(newName.lastIndexOf('.') + 1) : newName;
            catalog.dropFileFormat(name);
            fileFormat.setName(newSimple);
            catalog.addFileFormat(newName, fileFormat);
        } else {
            applyFileFormatOptions(fileFormat, action.copyFormatOption());
        }
        return null;
    }

    private void applyFileFormatOptions(final FileFormat fileFormat, final List<FrostlakeParser.CopyFormatOptionContext> options) {
        for (final FrostlakeParser.CopyFormatOptionContext opt : options) {
            if (opt.TYPE() != null) {
                fileFormat.setType(fileFormatOptValue(opt.copyOptionValue()));
            } else if (opt.FIELD_DELIMITER() != null) {
                fileFormat.setOption("FIELD_DELIMITER", fileFormatOptValue(opt.copyOptionValue()));
            } else if (opt.SKIP_HEADER() != null) {
                fileFormat.setOption("SKIP_HEADER", opt.INTEGER_LITERAL().getText());
            } else if (opt.DATE_FORMAT() != null) {
                fileFormat.setOption("DATE_FORMAT", fileFormatOptValue(opt.copyOptionValue()));
            } else if (opt.COMPRESSION() != null) {
                fileFormat.setOption("COMPRESSION", fileFormatOptValue(opt.copyOptionValue()));
            } else if (opt.RECORD_DELIMITER() != null) {
                fileFormat.setOption("RECORD_DELIMITER", fileFormatOptValue(opt.copyOptionValue()));
            } else if (opt.ESCAPE() != null) {
                fileFormat.setOption("ESCAPE", fileFormatOptValue(opt.copyOptionValue()));
            } else if (opt.identifier() != null && opt.copyOptionValue() != null) {
                fileFormat.setOption(getText(opt.identifier()), fileFormatOptValue(opt.copyOptionValue()));
            } else if (opt.identifier() != null && opt.LPAREN() != null) {
                // A string-list option such as NULL_IF = ('\\N', '') — store the values comma-joined so the
                // named format round-trips without a parse error (COPY applies NULL_IF from its inline form).
                final StringBuilder joined = new StringBuilder();
                if (opt.stringLiteralList() != null) {
                    for (final TerminalNode node : opt.stringLiteralList().STRING_LITERAL()) {
                        if (joined.length() > 0) {
                            joined.append(',');
                        }
                        joined.append(ddl.extractStringLiteral(node));
                    }
                }
                fileFormat.setOption(getText(opt.identifier()), joined.toString());
            }
        }
    }

    private String fileFormatOptValue(final FrostlakeParser.CopyOptionValueContext v) {
        if (v == null) {
            return null;
        }
        return v.STRING_LITERAL() != null ? ddl.extractStringLiteral(v.STRING_LITERAL()) : v.getText();
    }


    /** The format a parenthesized FILE_FORMAT group names: FORMAT_NAME wins, else TYPE, else the default. */
    private String formatFromOptions(final FrostlakeParser.ParenOptionListContext options, final String fallback) {
        String result = fallback;
        for (final FrostlakeParser.ParenOptionContext option : options.parenOption()) {
            final String key = option.optionKey().getText().toUpperCase();
            if (!"FORMAT_NAME".equals(key) && !"TYPE".equals(key)) {
                continue;
            }
            String value = option.copyOptionValue() != null ? option.copyOptionValue().getText() : null;
            if (value == null) {
                continue;
            }
            if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            result = value;
            if ("FORMAT_NAME".equals(key)) {
                return result;
            }
        }
        return result;
    }

}
