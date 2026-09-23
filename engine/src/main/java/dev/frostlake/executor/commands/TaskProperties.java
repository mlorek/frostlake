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

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SessionParameterCatalog;
import dev.frostlake.executor.SessionParameterRow;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.ScheduleType;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.User;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.task.TaskGraphConfig;
import dev.frostlake.task.TaskGraphs;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The task properties CREATE TASK and ALTER TASK … SET name — the ones with a clause of their own (SCHEDULE,
 * WAREHOUSE, COMMENT, ALLOW_OVERLAPPING_EXECUTION, …), CONFIG, OVERLAP_POLICY, FINALIZE, SUCCESS_INTEGRATION,
 * LOG_LEVEL, the serverless sizes and every session parameter — and the graph rules a task's predecessors,
 * finalizer and configuration obey. Every refusal is the account's sentence.
 *
 * <p>A statement is checked in two steps, as the account checks it. The first reads the statement alone: a
 * property named twice, a name no task carries, a value of the wrong kind, and a warehouse, integration or
 * user that does not exist. CREATE TASK IF NOT EXISTS over an existing task stops after it. The second weighs
 * the values and the graph: a number outside its range, a word outside its vocabulary, a schedule, a
 * configuration, the predecessors, the finalizer.
 *
 * <p>A task is a ROOT when it has no predecessor and finalizes no graph; only a root carries a configuration,
 * an overlap policy or overlapping runs. A FINALIZER task is attached to one root, which has at most one; it
 * has no schedule, no predecessor and no child.
 */
final class TaskProperties {

    /** The overlap policies, the first being the default. A quoted one is matched exactly, a bare one folds. */
    private static final String[] OVERLAP_POLICIES = {"NO_OVERLAP", "ALLOW_CHILD_OVERLAP", "ALLOW_ALL_OVERLAP"};

    /** The task parameters SHOW PARAMETERS IN TASK lists beside the session ones, with their value types. */
    private static final String[][] TASK_PARAMETERS = {
        {"SERVERLESS_TASK_MAX_STATEMENT_SIZE", "STRING"},
        {"SERVERLESS_TASK_MIN_STATEMENT_SIZE", "STRING"},
        {"SUSPEND_TASK_AFTER_NUM_FAILURES", "NUMBER"},
        {"TASK_AUTO_RETRY_ATTEMPTS", "NUMBER"},
        {"USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE", "STRING"},
        {"USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS", "NUMBER"},
        {"USER_TASK_TIMEOUT_MS", "NUMBER"},
    };

    /** The level parameters, whose bare value keeps the case it is written in. */
    private static final Set<String> LEVELS = new HashSet<>(Arrays.asList("LOG_LEVEL", "TRACE_LEVEL"));

    private static final BigInteger INT_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);

    private final Catalog catalog;
    private final Schema schema;
    /** Set once a statement names OVERLAP_POLICY, so a non-root task refuses it. */
    private String overlapPolicyGiven;
    /** Set once a statement names ALLOW_OVERLAPPING_EXECUTION, compared with OVERLAP_POLICY. */
    private Boolean allowOverlappingGiven;
    /** The root a statement's FINALIZE names, as written, or null. */
    private TaskPropertyValue finalizeGiven;
    private boolean configGiven;
    private boolean scheduleGiven;
    /** The serverless-only settings a statement gives, refused together on a task with a warehouse. */
    private String managedSizeGiven;
    private String ceilingGiven;
    private String completionIntervalGiven;
    /** Set once a statement names SERVERLESS_TASK_MIN_STATEMENT_SIZE, whose bounds are checked then. */
    private boolean floorGiven;
    /** Set once a statement names TASK_AUTO_RETRY_ATTEMPTS, which only a root takes. */
    private boolean retryAttemptsGiven;

    /**
     * @param catalog the catalog the task's references resolve in
     * @param schema  the schema the task lives in
     */
    TaskProperties(final Catalog catalog, final Schema schema) {
        this.catalog = catalog;
        this.schema = schema;
    }

    // ------------------------------------------------------------------ reading a statement

    /**
     * The properties a list of task options names, in the order written.
     *
     * @param options the options
     * @return the settings
     */
    static List<TaskPropertySetting> settings(final List<FrostlakeParser.TaskOptionContext> options) {
        final List<TaskPropertySetting> settings = new ArrayList<>();
        for (final FrostlakeParser.TaskOptionContext option : options) {
            settings.add(setting(option));
        }
        return settings;
    }

    /**
     * One option as a setting: its name as written and the value, whichever clause reads it.
     *
     * @param option the option
     * @return the setting
     */
    static TaskPropertySetting setting(final FrostlakeParser.TaskOptionContext option) {
        if (option.taskPropertyValue() != null) {
            // A quoted property name is its content; a bare one is kept as spelled, which tells repeats apart.
            final FrostlakeParser.IdentifierContext name = option.identifier();
            return new TaskPropertySetting(name.QUOTED_IDENTIFIER() != null ? SqlIdentifiers.canonical(name)
                : name.getText(), TaskPropertyValue.of(option.taskPropertyValue()));
        }
        if (option.scheduleClause() != null) {
            return new TaskPropertySetting(option.scheduleClause().SCHEDULE().getText(),
                TaskPropertyValue.quoted(option.scheduleClause().STRING_LITERAL().getText()));
        }
        final String spelling = option.getStart().getText();
        if (option.booleanValue() != null) {
            return new TaskPropertySetting(spelling, TaskPropertyValue.bool(option.booleanValue().getText()));
        }
        if (option.INTEGER_LITERAL() != null) {
            return new TaskPropertySetting(spelling, TaskPropertyValue.number(option.INTEGER_LITERAL().getText()));
        }
        if (option.STRING_LITERAL() != null) {
            return new TaskPropertySetting(spelling, TaskPropertyValue.quoted(option.STRING_LITERAL().getText()));
        }
        return new TaskPropertySetting(spelling, TaskPropertyValue.name(option.identifier()));
    }

    /**
     * A WAREHOUSE clause as a setting: a quoted name as written, a bare one folded, a session variable's value.
     *
     * @param clause   the clause
     * @param resolved the warehouse's name as the clause resolves it
     * @return the setting
     */
    static TaskPropertySetting warehouseSetting(final FrostlakeParser.WarehouseClauseContext clause,
                                                final String resolved) {
        final TaskPropertyValue value = clause.STRING_LITERAL() != null
            ? TaskPropertyValue.quoted(clause.STRING_LITERAL().getText())
            : clause.identifier() != null ? TaskPropertyValue.name(clause.identifier())
            : TaskPropertyValue.resolvedName(resolved);
        return new TaskPropertySetting(clause.WAREHOUSE().getText(), value);
    }

    // ------------------------------------------------------------------ first step: the statement alone

    /**
     * Refuse a property named twice. Two spellings are the same property only when they are spelled alike,
     * case included, and the refusal names it as spelled.
     *
     * @param spellings the property names as written, in order
     */
    static void rejectRepeats(final List<String> spellings) {
        final Set<String> seen = new HashSet<>();
        for (final String spelling : spellings) {
            if (!seen.add(spelling)) {
                throw new RuntimeException(SqlCompilationError.duplicateProperty(spelling));
            }
        }
    }

    /**
     * The first step for a statement's properties: each is refused when no task carries it, when its value is
     * of the wrong kind, or when it names a warehouse or an integration that does not exist.
     *
     * @param settings the statement's properties
     */
    void compile(final List<TaskPropertySetting> settings) {
        final List<String> spellings = new ArrayList<>();
        for (final TaskPropertySetting setting : settings) {
            spellings.add(setting.spelling());
        }
        rejectRepeats(spellings);
        for (final TaskPropertySetting setting : settings) {
            requireKind(setting.key(), setting.value());
        }
    }

    private void requireKind(final String key, final TaskPropertyValue value) {
        switch (key) {
            case "CONFIG":
            case "SCHEDULE":
            case "TARGET_COMPLETION_INTERVAL":
                // A name is text too, weighed as the parameter's text is (live-verified).
                requireListFree(key, value);
                if (value.isNumeric() || value.isBoolean()) {
                    throw invalidKind(key, value);
                }
                return;
            case "COMMENT":
                if (value.isList()) {
                    throw new RuntimeException(SqlCompilationError.invalidValueForParameter(
                        value.listItems().toString(), "comment"));
                }
                if (value.isNumeric() || value.isBoolean()) {
                    throw invalidKind(key, value);
                }
                return;
            case "ALLOW_OVERLAPPING_EXECUTION":
                requireListFree(key, value);
                if (!value.isBoolean()) {
                    throw invalidKind(key, value);
                }
                return;
            case "OVERLAP_POLICY":
                requireListFree(key, value);
                return;
            case "FINALIZE":
                requireListFree(key, value);
                if (value.isNumeric() || value.isBoolean()) {
                    throw invalidKind(key, value);
                }
                return;
            case "WAREHOUSE":
                requireListFree(key, value);
                if (value.isNumeric() || value.isBoolean()) {
                    throw invalidKind(key, value);
                }
                // A quoted name is echoed as written, a bare one upper-cased (live-verified).
                if (!catalog.hasWarehouse(value.text())) {
                    throw new RuntimeException("Nonexistent warehouse " + (value.isQuoted() ? value.text()
                        : value.text().toUpperCase(Locale.ROOT)) + " was specified.");
                }
                return;
            case "SUCCESS_INTEGRATION":
            case "ERROR_INTEGRATION":
                requireListFree(key, value);
                if (value.isNumeric() || value.isBoolean()) {
                    throw invalidKind(key, value);
                }
                // A quoted name is the integration's exact name; a bare one folds as an identifier does.
                if (catalog.getIntegrations().find(value.text()) == null) {
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Integration", value.text()));
                }
                return;
            default:
                break;
        }
        final String type = parameterType(key);
        if (type == null) {
            throw new RuntimeException(SqlCompilationError.invalidPropertyFor(key, "TASK"));
        }
        requireKindOf(key, type, value);
    }

    /** The property takes no parenthesised list: the account refuses one in its own sentence. */
    private static void requireListFree(final String key, final TaskPropertyValue value) {
        if (value.isList()) {
            throw new RuntimeException("Invalid value specified for property '" + key + "'");
        }
    }

    /**
     * Refuse a parameter's value of the wrong kind: a NUMBER takes a whole number (within 32 bits), a BOOLEAN
     * takes TRUE or FALSE, a STRING takes a string or a name. The refusal echoes the value as written.
     */
    private static void requireKindOf(final String key, final String type, final TaskPropertyValue value) {
        if ("NUMBER".equals(type)) {
            if (!value.isNumeric() || !isWholeNumber(value.written())) {
                throw invalidKind(key, value);
            }
            return;
        }
        if ("BOOLEAN".equals(type)) {
            if (!value.isBoolean()) {
                throw invalidKind(key, value);
            }
            return;
        }
        if (value.isNumeric() || value.isBoolean()) {
            throw invalidKind(key, value);
        }
    }

    private static boolean isWholeNumber(final String written) {
        final String digits = written.startsWith("-") ? written.substring(1) : written;
        if (digits.isEmpty()) {
            return false;
        }
        for (int i = 0; i < digits.length(); i++) {
            if (!Character.isDigit(digits.charAt(i))) {
                return false;
            }
        }
        final BigInteger number = new BigInteger(written);
        return number.compareTo(INT_MIN) >= 0 && number.compareTo(INT_MAX) <= 0;
    }

    private static RuntimeException invalidKind(final String key, final TaskPropertyValue value) {
        return new RuntimeException(SqlCompilationError.invalidValueForParameter(value.written(), key));
    }

    /** The value type of a task or session parameter a task carries, or null for any other name. */
    private static String parameterType(final String key) {
        for (final String[] parameter : TASK_PARAMETERS) {
            if (parameter[0].equals(key)) {
                return parameter[1];
            }
        }
        final SessionParameterRow row = SessionParameterCatalog.find(key);
        if (row == null || !SessionParameterCatalog.taskScopeNames().contains(key)) {
            return null;
        }
        return row.getType();
    }

    // ------------------------------------------------------------------ second step: values and the graph

    /**
     * Apply the statement's properties to the task in the order written, each value weighed first.
     *
     * @param task     the task being created or altered
     * @param settings the statement's properties, through the first step
     */
    void apply(final Task task, final List<TaskPropertySetting> settings) {
        for (final TaskPropertySetting setting : settings) {
            apply(task, setting.key(), setting.value());
        }
    }

    private void apply(final Task task, final String key, final TaskPropertyValue value) {
        switch (key) {
            case "WAREHOUSE":
                task.setWarehouse(value.text());
                return;
            case "SCHEDULE":
                TaskOptions.requireValidSchedule(value.text());
                task.setSchedule(value.text());
                task.setScheduleType(value.text().toUpperCase(Locale.ROOT).contains("CRON")
                    ? ScheduleType.CRON : ScheduleType.MINUTES);
                scheduleGiven = true;
                return;
            case "COMMENT":
                task.setComment(value.text());
                return;
            case "ALLOW_OVERLAPPING_EXECUTION":
                allowOverlappingGiven = Boolean.valueOf("true".equals(value.text()));
                task.setAllowOverlappingExecution(allowOverlappingGiven.booleanValue());
                return;
            case "SUCCESS_INTEGRATION":
                task.setSuccessIntegration(value.text());
                return;
            case "ERROR_INTEGRATION":
                task.setErrorIntegration(value.text());
                return;
            case "TARGET_COMPLETION_INTERVAL":
                TaskParameterValues.requireText(key, value.text());
                task.setTargetCompletionInterval(value.text());
                completionIntervalGiven = value.text();
                return;
            case "CONFIG":
                TaskGraphConfig.requireObject(value.text());
                task.setConfig(value.text());
                configGiven = true;
                return;
            case "OVERLAP_POLICY":
                final String policy = overlapPolicy(value);
                if (policy == null) {
                    throw new RuntimeException(SqlCompilationError.invalidValueForProperty(value.text(), key));
                }
                overlapPolicyGiven = policy;
                task.setOverlapPolicy(policy);
                return;
            case "FINALIZE":
                finalizeGiven = value;
                return;
            case "SEARCH_PATH":
                throw new RuntimeException("The parameter SEARCH_PATH is not supported with Tasks. Remove the "
                    + "parameter and try again.");
            default:
                break;
        }
        final String type = parameterType(key);
        final String text = LEVELS.contains(key) ? value.unfoldedText() : value.text();
        if ("NUMBER".equals(type)) {
            TaskParameterValues.requireNumber(key, Long.parseLong(text));
        } else if ("STRING".equals(type)) {
            TaskParameterValues.requireText(key, text);
        } else if ("AUTOCOMMIT".equals(key) && "false".equals(text)) {
            throw new RuntimeException("The parameter AUTOCOMMIT = false is not supported with Tasks. Remove the "
                + "parameter and try again.");
        }
        if (!applyTaskParameter(task, key, text)) {
            task.setSessionParameter(key, text);
        }
    }

    /** A task parameter set on the task, which then reads at level TASK; false for a session parameter. */
    private boolean applyTaskParameter(final Task task, final String key, final String text) {
        switch (key) {
            case "SERVERLESS_TASK_MAX_STATEMENT_SIZE":
                task.setServerlessTaskMaxStatementSize(text);
                ceilingGiven = text;
                break;
            case "SERVERLESS_TASK_MIN_STATEMENT_SIZE":
                task.setServerlessTaskMinStatementSize(text);
                floorGiven = true;
                break;
            case "USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE":
                task.setUserTaskManagedInitialWarehouseSize(text);
                managedSizeGiven = text;
                break;
            case "SUSPEND_TASK_AFTER_NUM_FAILURES":
                task.setSuspendTaskAfterNumFailures(Integer.parseInt(text));
                break;
            case "TASK_AUTO_RETRY_ATTEMPTS":
                task.setTaskAutoRetryAttempts(Integer.parseInt(text));
                retryAttemptsGiven = true;
                break;
            case "USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS":
                task.setUserTaskMinimumTriggerIntervalInSeconds(Integer.parseInt(text));
                break;
            case "USER_TASK_TIMEOUT_MS":
                task.setUserTaskTimeoutMs(Long.parseLong(text));
                break;
            default:
                return false;
        }
        task.markParameterSet(key);
        return true;
    }

    /** The overlap policy a value names, or null: a quoted policy is matched exactly, a bare one as it folds. */
    private static String overlapPolicy(final TaskPropertyValue value) {
        if (!value.isQuoted() && !value.isName()) {
            return null;
        }
        for (final String candidate : OVERLAP_POLICIES) {
            if (candidate.equals(value.text())) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Refuse ALLOW_OVERLAPPING_EXECUTION and OVERLAP_POLICY given in one statement with different meanings:
     * TRUE means ALLOW_CHILD_OVERLAP and FALSE means NO_OVERLAP.
     */
    void requireConsistentOverlap() {
        if (overlapPolicyGiven != null && allowOverlappingGiven != null) {
            final String meant = allowOverlappingGiven.booleanValue() ? "ALLOW_CHILD_OVERLAP" : "NO_OVERLAP";
            if (!meant.equals(overlapPolicyGiven)) {
                throw new RuntimeException("Cannot specify ALLOW_OVERLAPPING_EXECUTION and OVERLAP_POLICY to "
                    + "different values in the same command.");
            }
        }
    }

    /** Whether the statement names SCHEDULE. */
    boolean scheduleGiven() {
        return scheduleGiven;
    }

    /** Whether the statement names FINALIZE. */
    boolean finalizeGiven() {
        return finalizeGiven != null;
    }

    /**
     * FINALIZE: the task becomes the finalizer of the root the statement names, refused in this order when the
     * root does not exist, lives in another schema, is no root, or is the task itself; when the task carries a
     * configuration or is given an overlap setting; and when it has a schedule, a predecessor or a child, or
     * the root already has another finalizer.
     *
     * @param task the task, with the statement's other properties applied
     */
    void requireFinalizer(final Task task) {
        final String shown = shownName(finalizeGiven);
        final List<String> parts = finalizeGiven.isName() ? finalizeGiven.canonicalParts()
            : Arrays.asList(SqlIdentifiers.canonicalTextParts(finalizeGiven.text()));
        final Schema home = schemaOf(parts);
        final Task root = home == null ? null : exactTask(home, parts.get(parts.size() - 1));
        if (root == null) {
            throw new RuntimeException("Invalid finalized root task " + shown + " was specified.");
        }
        if (!sameSchema(home)) {
            throw new RuntimeException("Cannot finalize graph " + shown + " in a different schema.");
        }
        if (!TaskGraphs.isRoot(root) || root.isFinalizer()) {
            throw new RuntimeException("Task " + qualified(task) + " cannot finalize a non-root task "
                + qualified(root) + ".");
        }
        if (root.getName().equals(task.getName())) {
            throw new RuntimeException("Task " + task.getName() + " cannot finalize itself.");
        }
        if (task.getConfig() != null) {
            throw nonRootConfig(task);
        }
        if (overlapPolicyGiven != null) {
            throw nonRootOverlapPolicy(task);
        }
        if (Boolean.TRUE.equals(allowOverlappingGiven)) {
            throw nonRootOverlapping(task);
        }
        if (task.getSchedule() != null) {
            throw new RuntimeException("Task " + task.getName() + " cannot have a schedule and be a finalizer task.");
        }
        if (!task.getPredecessors().isEmpty()) {
            throw new RuntimeException("Task " + qualified(task) + " cannot have predecessors as a finalizer task.");
        }
        if (hasChildren(task)) {
            throw new RuntimeException("Task " + qualified(task) + " cannot have children as a finalizer task.");
        }
        final Task existing = TaskGraphs.finalizerOf(schema, root);
        if (existing != null && !existing.getName().equals(task.getName())) {
            throw new RuntimeException("Finalize target " + root.getName() + " already have finalizer.");
        }
        task.setFinalizedRootTask(root.getName());
    }

    /**
     * A schedule the statement sets on a task that runs after others or finalizes a graph is refused, each in
     * its sentence.
     *
     * @param task the task as the statement leaves it
     */
    void requireScheduleFits(final Task task) {
        if (!scheduleGiven || task.getSchedule() == null) {
            return;
        }
        if (!task.getPredecessors().isEmpty()) {
            throw new RuntimeException("Task " + task.getName().toUpperCase(Locale.ROOT)
                + " cannot have both a schedule and a predecessor.");
        }
        if (task.isFinalizer() && finalizeGiven == null) {
            throw new RuntimeException("Task " + task.getName() + " cannot have a schedule and be a finalizer task.");
        }
    }

    /**
     * The settings only a root takes — a configuration, an overlap policy, overlapping runs — refused on a task
     * that runs after others or finalizes a graph.
     *
     * @param task the task as the statement leaves it
     */
    void requireRootSettings(final Task task) {
        final boolean nonRoot = !task.getPredecessors().isEmpty() || task.isFinalizer();
        if (!nonRoot) {
            return;
        }
        if (configGiven && task.getConfig() != null) {
            throw nonRootConfig(task);
        }
        if (overlapPolicyGiven != null) {
            throw nonRootOverlapPolicy(task);
        }
        if (Boolean.TRUE.equals(allowOverlappingGiven)) {
            throw nonRootOverlapping(task);
        }
        if (retryAttemptsGiven) {
            throw new RuntimeException("Cannot set parameter TASK_AUTO_RETRY_ATTEMPTS on non-root task "
                + task.getName() + ". Non-root tasks use the parameter setting from their root task. Please set "
                + "this on the root task for this task instead.");
        }
    }

    /**
     * A task that carries a configuration cannot be made to run after others.
     *
     * @param task the task
     */
    void requireConfigFree(final Task task) {
        if (task.getConfig() != null) {
            throw nonRootConfig(task);
        }
    }

    /**
     * The serverless settings: those only a serverless task takes are refused on a task with a warehouse, and a
     * statement-size floor must fit under the ceiling and the initial size.
     *
     * @param task the task as the statement leaves it
     */
    void requireServerlessSettings(final Task task) {
        TaskOptions.rejectServerlessOptionsOnWarehouseTask(task.getWarehouse(), managedSizeGiven, ceilingGiven,
            completionIntervalGiven, schema, task.getName());
        final String floor = task.getServerlessTaskMinStatementSize();
        if (floorGiven && floor != null && task.getWarehouse() != null && !task.getWarehouse().isEmpty()) {
            throw new RuntimeException("Cannot set SERVERLESS_TASK_MIN_STATEMENT_SIZE on a non-serverless task.");
        }
        // The floor, the ceiling and the initial size are weighed together whenever one of them is set.
        if ((floorGiven || ceilingGiven != null || managedSizeGiven != null) && floor != null) {
            final String ceiling = task.getServerlessTaskMaxStatementSize();
            if (ceiling != null && TaskParameterValues.sizeRank(ceiling) >= 0
                    && TaskParameterValues.sizeRank(floor) > TaskParameterValues.sizeRank(ceiling)) {
                throw new RuntimeException("SERVERLESS_TASK_MIN_STATEMENT_SIZE (" + floor + ") cannot be greater "
                    + "than SERVERLESS_TASK_MAX_STATEMENT_SIZE (" + ceiling + ").");
            }
            final String initial = task.getUserTaskManagedInitialWarehouseSize();
            if (initial != null && TaskParameterValues.sizeRank(initial) >= 0
                    && TaskParameterValues.sizeRank(initial) < TaskParameterValues.sizeRank(floor)) {
                throw new RuntimeException("USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE (" + initial + ") cannot be "
                    + "less than SERVERLESS_TASK_MIN_STATEMENT_SIZE (" + floor + ").");
            }
        }
    }

    // ------------------------------------------------------------------ predecessors

    /**
     * The tasks an AFTER list names, refused when one does not exist (every name is looked up first), when the
     * task has a finalizer, and then per name when it is the task itself, lives in another schema or is a
     * finalizer task, which no task may follow. A name given twice is kept once.
     *
     * @param task  the task that would run after them
     * @param names the predecessors as written
     * @return the predecessors' names, in the task's schema
     */
    List<String> requirePredecessors(final Task task, final List<FrostlakeParser.QualifiedNameContext> names) {
        final List<Task> found = new ArrayList<>();
        final List<Schema> homes = new ArrayList<>();
        for (final FrostlakeParser.QualifiedNameContext name : names) {
            final List<String> parts = Arrays.asList(ParseTreeText.qualifiedNameParts(name));
            final Schema home = schemaOf(parts);
            final Task predecessor = home == null ? null : exactTask(home, parts.get(parts.size() - 1));
            if (predecessor == null) {
                throw new RuntimeException("Invalid predecessor " + shownName(TaskPropertyValue.name(name))
                    + " was specified.");
            }
            found.add(predecessor);
            homes.add(home);
        }
        if (TaskGraphs.finalizerOf(schema, task) != null) {
            throw new RuntimeException("This task has a finalizer and cannot be made a child task. Please remove the "
                + "finalizer before adding a predecessor.");
        }
        final List<String> resolved = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            final Task predecessor = found.get(i);
            final boolean home = sameSchema(homes.get(i));
            if (home && predecessor.getName().equals(task.getName())) {
                throw new RuntimeException("Task " + task.getName() + " cannot use itself as its predecessor.");
            }
            if (!home) {
                throw new RuntimeException("Cannot have predecessor " + predecessor.getName()
                    + " from a different schema.");
            }
            if (predecessor.isFinalizer()) {
                throw new RuntimeException("Task " + qualified(predecessor)
                    + " cannot have children as a finalizer task.");
            }
            if (!resolved.contains(predecessor.getName())) {
                resolved.add(predecessor.getName());
            }
        }
        return resolved;
    }

    /**
     * ALTER TASK … REMOVE AFTER: every name that is a task must be one of the task's predecessors, and only then
     * must every name be a task.
     *
     * @param task  the task
     * @param names the predecessors to remove, as written
     * @return the names to remove, in the task's schema
     */
    List<String> requireRemovablePredecessors(final Task task, final List<FrostlakeParser.QualifiedNameContext> names) {
        final List<String> removed = new ArrayList<>();
        final List<FrostlakeParser.QualifiedNameContext> missing = new ArrayList<>();
        for (final FrostlakeParser.QualifiedNameContext name : names) {
            final List<String> parts = Arrays.asList(ParseTreeText.qualifiedNameParts(name));
            final Schema home = schemaOf(parts);
            final Task predecessor = home == null ? null : exactTask(home, parts.get(parts.size() - 1));
            if (predecessor == null) {
                missing.add(name);
                continue;
            }
            if (!sameSchema(home) || !hasPredecessor(task, predecessor.getName())) {
                throw new RuntimeException("Task " + task.getName() + " does not have predecessor "
                    + predecessor.getName() + ".");
            }
            removed.add(predecessor.getName());
        }
        if (!missing.isEmpty()) {
            throw new RuntimeException("Invalid predecessor " + shownName(TaskPropertyValue.name(missing.get(0)))
                + " was specified.");
        }
        return removed;
    }

    // ------------------------------------------------------------------ EXECUTE AS USER

    /**
     * The user an {@code EXECUTE AS USER} clause names, written directly or through IDENTIFIER().
     *
     * @param clause        the clause
     * @param queryExecutor resolves an IDENTIFIER() argument
     * @return the user's canonical name
     */
    static String runAsUser(final FrostlakeParser.TaskExecuteAsContext clause, final QueryExecutor queryExecutor) {
        if (clause.identifier() != null) {
            return SqlIdentifiers.canonical(clause.identifier());
        }
        final String[] parts = queryExecutor.resolveIdentifierArgument(clause.identifierArgument());
        return parts[parts.length - 1];
    }

    /**
     * Refuse a user a task cannot run as: one that does not exist, or one without access to the role that
     * owns the task. The session's own user is always accepted.
     *
     * @param task     the task
     * @param userName the user, canonical
     */
    void requireRunAsUser(final Task task, final String userName) {
        final String sessionUser = catalog.currentUserForDdl();
        if (sessionUser != null && sessionUser.equalsIgnoreCase(userName)) {
            return;
        }
        final User user = catalog.getUser(userName);
        final String owner = task.getOwner();
        if (owner != null && !reaches(user, owner)) {
            throw new RuntimeException("Task execution failed because the user " + user.getName()
                + " no longer has access to the task owner role " + owner + ". To fix, an administrator must run "
                + "GRANT ROLE " + owner + " TO USER " + user.getName() + ", or alter the task to execute as a "
                + "different user who has access to that role.");
        }
    }

    /** Whether a role is granted to the user, directly or through the roles granted to its roles. */
    private boolean reaches(final User user, final String roleName) {
        final Set<String> seen = new HashSet<>();
        final List<String> frontier = new ArrayList<>(user.getGrantedRoles());
        for (int i = 0; i < frontier.size(); i++) {
            final String role = frontier.get(i).toUpperCase(Locale.ROOT);
            if (!seen.add(role)) {
                continue;
            }
            if (role.equalsIgnoreCase(roleName)) {
                return true;
            }
            try {
                final Role granted = catalog.getRole(role);
                if (granted != null) {
                    frontier.addAll(granted.getGrantedRoles());
                }
            } catch (final RuntimeException unknownRole) {
                // a role granted to the user that no longer exists grants nothing
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * A detached copy of what the graph rules read from a task — its schedule, warehouse, predecessors, finalizer
     * link, configuration, overlap settings and statement sizes — for a statement to be tried on before it
     * changes the task itself.
     *
     * @param task the task
     * @return the copy, which no schema holds
     */
    static Task scratchCopy(final Task task) {
        final Task copy = new Task(task.getName(), task.getSchedule(), task.getScheduleType(), task.getSqlStatement(),
            task.getWarehouse());
        for (final String predecessor : task.getPredecessors()) {
            copy.addPredecessor(predecessor);
        }
        copy.setOwner(task.getOwner());
        copy.setFinalizedRootTask(task.getFinalizedRootTask());
        copy.setConfig(task.getConfig());
        copy.setOverlapPolicy(task.getOverlapPolicy());
        copy.setAllowOverlappingExecution(task.isAllowOverlappingExecution());
        copy.setServerlessTaskMinStatementSize(task.getServerlessTaskMinStatementSize());
        copy.setServerlessTaskMaxStatementSize(task.getServerlessTaskMaxStatementSize());
        copy.setUserTaskManagedInitialWarehouseSize(task.getUserTaskManagedInitialWarehouseSize());
        return copy;
    }

    /** The schema a name's parts place a task in: the task's own for a bare name, or null when there is none. */
    private Schema schemaOf(final List<String> parts) {
        if (parts.size() == 1) {
            return schema;
        }
        try {
            final String current = catalog.getCurrentDatabase();
            final Database database = parts.size() == 2
                ? catalog.databaseExact(current != null ? current : schema.getDatabaseName())
                : catalog.databaseExact(parts.get(0));
            final String schemaName = parts.get(parts.size() - 2);
            return database.hasSchemaExact(schemaName) ? database.schemaExact(schemaName) : null;
        } catch (final RuntimeException missing) {
            return null;
        }
    }

    /** The task of exactly that name in the schema, or null. */
    private static Task exactTask(final Schema home, final String name) {
        for (final Task candidate : home.getTasks()) {
            if (candidate.getName().equals(name)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean sameSchema(final Schema other) {
        return other == schema || (other != null && other.getName().equals(schema.getName())
            && other.getDatabaseName().equals(schema.getDatabaseName()));
    }

    /** Whether another task of the schema names this one among its predecessors. */
    private boolean hasChildren(final Task task) {
        for (final Task candidate : schema.getTasks()) {
            if (candidate != task && hasPredecessor(candidate, task.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a task runs after the task of that name.
     *
     * @param task the task
     * @param name the other task's name
     * @return whether the task names it among its predecessors
     */
    static boolean hasPredecessor(final Task task, final String name) {
        for (final String predecessor : task.getPredecessors()) {
            if (QualifiedName.parse(predecessor).last().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** A name as a refusal names it: each bare part folded, a quoted part as written; a string as its parts. */
    private static String shownName(final TaskPropertyValue value) {
        if (!value.isName()) {
            return String.join(".", SqlIdentifiers.canonicalTextParts(value.text()));
        }
        final List<String> shown = new ArrayList<>();
        for (final String part : value.nameParts()) {
            shown.add(part.startsWith("\"") ? part : part.toUpperCase(Locale.ROOT));
        }
        return String.join(".", shown);
    }

    private RuntimeException nonRootConfig(final Task task) {
        return new RuntimeException("Task " + qualified(task) + " cannot both be a non-root task and have a config.");
    }

    private RuntimeException nonRootOverlapPolicy(final Task task) {
        return new RuntimeException("Cannot set overlap policy on non-root task " + qualified(task) + ".");
    }

    private RuntimeException nonRootOverlapping(final Task task) {
        return new RuntimeException("Cannot set allow_overlapping_execution on non-root task " + qualified(task) + ".");
    }

    private String qualified(final Task task) {
        return schema.getDatabaseName() + "." + schema.getName() + "." + task.getName();
    }
}
