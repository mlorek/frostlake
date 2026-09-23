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
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.executor.WarehouseReference;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Alert;
import dev.frostlake.metastore.model.AlertState;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.task.TaskScheduler;
import dev.frostlake.types.StringType;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The ALERT statements: CREATE [OR REPLACE | OR ALTER] ALERT (with its properties, condition and action, or as a
 * CLONE), ALTER ALERT (RESUME, SUSPEND, SET, UNSET, MODIFY CONDITION, MODIFY ACTION), DROP ALERT, SHOW ALERTS,
 * DESCRIBE ALERT and EXECUTE ALERT. The condition and the action are kept as written; they are parsed, not
 * resolved, when the alert is created, so a missing object fails the evaluation rather than the CREATE.
 */
public final class AlertCommandHandler {

    /** SHOW ALERTS' columns, in the documented order; DESCRIBE ALERT answers the same columns. */
    private static final String[] COLUMNS = {
        "created_on", "name", "database_name", "schema_name", "owner", "comment", "warehouse", "schedule", "state",
        "condition", "action", "owner_role_type", "runbook", "config", "was_auto_suspended"
    };

    private final QueryExecutor queryExecutor;
    private final Catalog catalog;

    /** @param queryExecutor the executor whose catalog and scheduler the statements act on */
    public AlertCommandHandler(final QueryExecutor queryExecutor) {
        this.queryExecutor = queryExecutor;
        this.catalog = queryExecutor.getCatalog();
    }

    // ---- CREATE -------------------------------------------------------------------------------------------

    /** CREATE [OR REPLACE | OR ALTER] ALERT [IF NOT EXISTS] name … */
    public Object create(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String[] parts = ParseTreeText.qualifiedNameParts(ctx.qualifiedName(0));
        final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
        final String name = parts[parts.length - 1];
        final FrostlakeParser.AlertDefinitionContext definition = ctx.alertDefinition();
        if (ctx.or_alter() != null && definition.CLONE() != null) {
            throw new RuntimeException(SqlCompilationError.of("Unsupported feature 'CREATE OR ALTER ALERT … CLONE'."));
        }
        if (ctx.or_alter() != null && definition.tagList() != null) {
            throw new RuntimeException(SqlCompilationError.of(
                "Unsupported feature 'CREATE OR ALTER ALERT … WITH TAG'."));
        }
        // A property written twice and a warehouse that does not exist are refused before the alert is looked
        // for, so IF NOT EXISTS forgives neither; the schedule is judged after it.
        rejectDuplicates(definition.alertProperty());
        for (final FrostlakeParser.AlertPropertyContext property : definition.alertProperty()) {
            if (property.warehouseClause() != null) {
                WarehouseReference.requireForTask(catalog, warehouseName(property.warehouseClause()));
            }
        }
        if (schema.hasAlert(name)) {
            if (ctx.or_alter() != null) {
                alterInPlace(schema, schema.getAlert(name), definition);
                // An existing alert is altered, and the statement answers as an ALTER does.
                ConditionalDdlOutcome.alteredInPlace();
                return null;
            }
            if (ifNotExists) {
                ConditionalDdlOutcome.createSkipped();
                return null;
            }
            if (ctx.or_replace() == null) {
                throw new RuntimeException(SqlCompilationError.of("Object '" + name + "' already exists."));
            }
        }
        final Alert alert;
        if (definition.CLONE() != null) {
            final String[] sourceParts = ParseTreeText.qualifiedNameParts(definition.qualifiedName());
            final Schema sourceSchema = catalog.requireOwningSchema(QualifiedName.of(sourceParts));
            alert = sourceSchema.getAlert(sourceParts[sourceParts.length - 1]).copy(name);
        } else {
            alert = new Alert(name, sourceText(definition.alertCondition()), sourceText(definition.taskBody()));
            applyProperties(catalog, alert, definition.alertProperty());
        }
        if (schema.hasAlert(name)) {
            final Alert replaced = schema.getAlert(name);
            final TaskScheduler scheduler = queryExecutor.getTaskScheduler();
            if (scheduler != null) {
                scheduler.suspendAlert(schema.getDatabaseName(), schema.getName(), replaced);
            }
            schema.dropAlert(name);
        }
        alert.setOwner(catalog.currentRoleForOwner());
        if (definition.tagList() != null) {
            InlineTags.apply(alert, definition.tagList(), queryExecutor);
        }
        schema.addAlert(alert);
        return null;
    }

    /**
     * CREATE OR ALTER ALERT over an existing alert: the statement describes the whole alert, so its condition,
     * action and properties replace the alert's, a property it leaves out is unset, and the state and the tags
     * stay as they were.
     */
    private void alterInPlace(final Schema schema, final Alert alert,
                              final FrostlakeParser.AlertDefinitionContext definition) {
        // The properties are judged on a copy first, so a refused statement leaves the alert as it was.
        applyProperties(catalog, alert.copy(alert.getName()), definition.alertProperty());
        final String schedule = alert.getSchedule();
        alert.setWarehouse(null);
        alert.setSchedule(null);
        alert.setComment(null);
        alert.setConfig(null);
        alert.setRunbook(null);
        alert.setSuspendAfterNumFailures(null);
        applyProperties(catalog, alert, definition.alertProperty());
        alert.setCondition(sourceText(definition.alertCondition()));
        alert.setAction(sourceText(definition.taskBody()));
        rescheduleIfChanged(schema, alert, schedule);
    }

    /**
     * Applies each property a CREATE or an ALTER … SET writes; a property may be written once, and a warehouse must
     * exist: {@code Nonexistent warehouse NOSUCH_WH was specified.}
     */
    private static void applyProperties(final Catalog catalog, final Alert alert,
                                        final List<FrostlakeParser.AlertPropertyContext> properties) {
        rejectDuplicates(properties);
        for (final FrostlakeParser.AlertPropertyContext property : properties) {
            if (property.scheduleClause() != null) {
                final String schedule = SqlStringLiterals.decode(property.scheduleClause().STRING_LITERAL().getText());
                TaskOptions.requireValidSchedule(schedule);
                alert.setSchedule(schedule);
            } else if (property.warehouseClause() != null) {
                final String warehouse = warehouseName(property.warehouseClause());
                WarehouseReference.requireForTask(catalog, warehouse);
                alert.setWarehouse(warehouse);
            } else if (property.COMMENT() != null) {
                alert.setComment(SqlStringLiterals.decode(property.STRING_LITERAL().getText()));
            } else if (property.CONFIG() != null) {
                alert.setConfig(SqlStringLiterals.decode(property.STRING_LITERAL().getText()));
            } else if (property.RUNBOOK() != null) {
                alert.setRunbook(SqlStringLiterals.decode(property.STRING_LITERAL().getText()));
            } else {
                alert.setSuspendAfterNumFailures(Integer.valueOf(property.INTEGER_LITERAL().getText()));
            }
        }
    }

    /** Refuses a property written twice, {@code duplicate property 'COMMENT';}. */
    private static void rejectDuplicates(final List<FrostlakeParser.AlertPropertyContext> properties) {
        final List<String> keys = new ArrayList<>();
        for (final FrostlakeParser.AlertPropertyContext property : properties) {
            keys.add(property.getStart().getText());
        }
        PropertyDuplicates.reject(keys);
    }

    private static String warehouseName(final FrostlakeParser.WarehouseClauseContext clause) {
        if (clause.STRING_LITERAL() != null) {
            return SqlStringLiterals.decode(clause.STRING_LITERAL().getText());
        }
        if (clause.identifier() != null) {
            return SqlIdentifiers.canonical(clause.identifier());
        }
        return clause.getChild(clause.getChildCount() - 1).getText();
    }

    // ---- ALTER --------------------------------------------------------------------------------------------

    /** The alert an ALTER, a DESCRIBE or an EXECUTE names, refused in the does-not-exist family when absent. */
    public Alert require(final FrostlakeParser.QualifiedNameContext name) {
        final String[] parts = ParseTreeText.qualifiedNameParts(name);
        final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
        final String alertName = parts[parts.length - 1];
        if (!schema.hasAlert(alertName)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Alert", schema.qualifiedName(alertName)));
        }
        return schema.getAlert(alertName);
    }

    /** ALTER ALERT … RESUME | SUSPEND | SET | UNSET | MODIFY CONDITION | MODIFY ACTION (tags are the caller's). */
    public void alter(final FrostlakeParser.QualifiedNameContext name, final FrostlakeParser.AlertActionContext action) {
        final String[] parts = ParseTreeText.qualifiedNameParts(name);
        final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
        final Alert alert = require(name);
        final TaskScheduler scheduler = queryExecutor.getTaskScheduler();
        if (action.RESUME() != null) {
            if (scheduler != null) {
                scheduler.resumeAlert(schema.getDatabaseName(), schema.getName(), alert);
            } else {
                alert.setState(AlertState.STARTED);
            }
        } else if (action.SUSPEND() != null) {
            if (scheduler != null) {
                scheduler.suspendAlert(schema.getDatabaseName(), schema.getName(), alert);
            } else {
                alert.setState(AlertState.SUSPENDED);
            }
        } else if (action.SET() != null) {
            final String schedule = alert.getSchedule();
            applyProperties(catalog, alert.copy(alert.getName()), action.alertProperty());
            applyProperties(catalog, alert, action.alertProperty());
            rescheduleIfChanged(schema, alert, schedule);
        } else if (action.UNSET() != null) {
            for (final FrostlakeParser.AlertParameterContext parameter : action.alertParameter()) {
                if (parameter.WAREHOUSE() != null) {
                    alert.setWarehouse(null);
                } else if (parameter.COMMENT() != null) {
                    alert.setComment(null);
                } else if (parameter.CONFIG() != null) {
                    alert.setConfig(null);
                } else if (parameter.RUNBOOK() != null) {
                    alert.setRunbook(null);
                } else {
                    alert.setSuspendAfterNumFailures(null);
                }
            }
        } else if (action.CONDITION() != null) {
            alert.setCondition(sourceText(action.alertCondition()));
        } else if (action.ACTION() != null) {
            alert.setAction(sourceText(action.taskBody()));
        }
    }

    private void rescheduleIfChanged(final Schema schema, final Alert alert, final String previousSchedule) {
        final TaskScheduler scheduler = queryExecutor.getTaskScheduler();
        final String schedule = alert.getSchedule();
        final boolean changed = schedule == null ? previousSchedule != null : !schedule.equals(previousSchedule);
        if (changed && scheduler != null && alert.getState() == AlertState.STARTED) {
            scheduler.rescheduleAlert(schema.getDatabaseName(), schema.getName(), alert);
        }
    }

    // ---- DROP ---------------------------------------------------------------------------------------------

    /** DROP ALERT [IF EXISTS] name: a missing alert is refused by its qualified name, or skipped by IF EXISTS. */
    public Object drop(final FrostlakeParser.DropStatementContext ctx, final boolean ifExists) {
        final String[] parts = ctx.objectName() != null
            ? queryExecutor.resolveObjectNameParts(ctx.objectName())
            : ParseTreeText.qualifiedNameParts(ctx.qualifiedName());
        final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
        final String name = parts[parts.length - 1];
        if (!schema.hasAlert(name)) {
            if (ifExists) {
                ConditionalDdlOutcome.dropSkipped();
                return null;
            }
            throw new RuntimeException(SqlCompilationError.doesNotExist("Alert", schema.qualifiedName(name)));
        }
        final TaskScheduler scheduler = queryExecutor.getTaskScheduler();
        if (scheduler != null) {
            scheduler.suspendAlert(schema.getDatabaseName(), schema.getName(), schema.getAlert(name));
        }
        schema.dropAlert(name);
        return null;
    }

    // ---- EXECUTE ------------------------------------------------------------------------------------------

    /**
     * EXECUTE ALERT name: the condition is evaluated now, and the action run when it returns rows. It answers
     * {@code Alert <NAME> is scheduled to run immediately.}
     */
    public ResultSet execute(final FrostlakeParser.QualifiedNameContext name) {
        final String[] parts = ParseTreeText.qualifiedNameParts(name);
        final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
        final Alert alert = require(name);
        final TaskScheduler scheduler = queryExecutor.getTaskScheduler();
        if (scheduler == null) {
            throw new RuntimeException("Task scheduler not initialized");
        }
        scheduler.executeAlertNow(schema.getDatabaseName(), schema.getName(), alert);
        return StatusResults.of("Alert " + alert.getName() + " is scheduled to run immediately.");
    }

    // ---- SHOW / DESCRIBE ----------------------------------------------------------------------------------

    /**
     * SHOW ALERTS over a scope: every database when {@code databaseName} is null, every schema of the database
     * when {@code schemaName} is null, else the one schema.
     */
    public ResultSet show(final String databaseName, final String schemaName) {
        final List<Row> rows = new ArrayList<>();
        for (final Database database : catalog.getAllDatabases()) {
            if (databaseName != null && !databaseName.equals(database.getName())) {
                continue;
            }
            for (final Schema schema : database.getAllSchemas()) {
                if (schemaName != null && !schemaName.equals(schema.getName())) {
                    continue;
                }
                for (final Alert alert : schema.getAlerts()) {
                    rows.add(row(database.getName(), schema.getName(), alert));
                }
            }
        }
        return new ResultSet(columns(), rows);
    }

    /** DESCRIBE ALERT name: the alert's SHOW ALERTS row. */
    public ResultSet describe(final FrostlakeParser.QualifiedNameContext name) {
        final String[] parts = ParseTreeText.qualifiedNameParts(name);
        final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
        final Alert alert = require(name);
        final List<Row> rows = new ArrayList<>();
        rows.add(row(schema.getDatabaseName(), schema.getName(), alert));
        return new ResultSet(columns(), rows);
    }

    private static List<ResultSetColumn> columns() {
        final List<ResultSetColumn> columns = new ArrayList<>();
        for (final String column : COLUMNS) {
            columns.add(new ResultSetColumn(column,
                "created_on".equals(column) ? ShowResultHelpers.CREATED_ON : StringType.VARCHAR));
        }
        return columns;
    }

    private static Row row(final String databaseName, final String schemaName, final Alert alert) {
        final Boolean autoSuspended = alert.getWasAutoSuspended();
        return new Row(Arrays.<Object>asList(
            ShowResultHelpers.createdOn(alert.getCreatedTime()),
            alert.getName(),
            databaseName,
            schemaName,
            alert.getOwner(),
            ShowResultHelpers.text(alert.getComment()),
            alert.getWarehouse(),
            alert.getSchedule(),
            alert.getState().reported(),
            alert.getCondition(),
            alert.getAction(),
            ShowResultHelpers.ownerRoleType(alert.getOwner()),
            alert.getRunbook(),
            alert.getConfig(),
            autoSuspended == null ? null : autoSuspended.toString()));
    }

    /** A statement's text as written, whitespace and comments kept. */
    private static String sourceText(final ParserRuleContext ctx) {
        return ctx.start.getInputStream().getText(new Interval(ctx.start.getStartIndex(), ctx.stop.getStopIndex()));
    }
}
