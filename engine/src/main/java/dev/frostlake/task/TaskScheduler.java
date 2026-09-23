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

package dev.frostlake.task;

import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Alert;
import dev.frostlake.metastore.model.AlertExecution;
import dev.frostlake.metastore.model.AlertState;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.ScheduleType;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskExecution;
import dev.frostlake.metastore.model.TaskExecutionState;
import dev.frostlake.metastore.model.TaskState;
import dev.frostlake.storage.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Schedules and executes tasks
 */
public class TaskScheduler {

    private static final Logger logger = LoggerFactory.getLogger(TaskScheduler.class);

    private final Catalog catalog;
    private ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks;
    private final TaskExecutor taskExecutor;
    private volatile boolean running;

    // The live generation of each armed CRON task. A CRON timer is one-shot and re-arms after its
    // run, but only while its generation is still the task's current one — a SUSPEND or a second
    // RESUME in between retires it, so no stale timer outlives the state change.
    private final Map<String, Long> cronGenerations = new ConcurrentHashMap<>();
    private final Object armLock = new Object();
    private long lastGeneration;

    // Tasks currently executing, for the ALLOW_OVERLAPPING_EXECUTION=FALSE guard.
    private final Set<String> runningTasks = ConcurrentHashMap.newKeySet();
    // The task whose body is executing on this thread — read by SYSTEM$SET_RETURN_VALUE /
    // SYSTEM$GET_PREDECESSOR_RETURN_VALUE / SYSTEM$CURRENT_USER_TASK_NAME.
    private static final ThreadLocal<Task> CURRENT_TASK = new ThreadLocal<>();
    // The configuration of the graph run executing on this thread — the root's CONFIG with an EXECUTE TASK …
    // USING CONFIG merged over it — read by SYSTEM$GET_TASK_GRAPH_CONFIG in every task of the run.
    private static final ThreadLocal<String> CURRENT_GRAPH_CONFIG = new ThreadLocal<>();

    /** The task whose body is executing on the current thread, or null outside a task run. */
    public static Task currentTask() {
        return CURRENT_TASK.get();
    }

    /** The configuration of the graph run executing on the current thread, or null when it has none. */
    public static String currentGraphConfig() {
        return CURRENT_GRAPH_CONFIG.get();
    }

    public TaskScheduler(final Catalog catalog, final TaskExecutor taskExecutor) {
        this.catalog = catalog;
        this.taskExecutor = taskExecutor;
        this.scheduler = newSchedulerPool();
        this.scheduledTasks = new ConcurrentHashMap<>();
        this.running = false;
    }

    /**
     * A fresh timer pool. Daemon threads: an engine that is never shut down must not keep the JVM
     * alive. Built by a factory rather than inline because {@link #stop()} shuts the pool down
     * permanently, so a later {@link #start()} needs a new one.
     */
    private static ScheduledExecutorService newSchedulerPool() {
        return Executors.newScheduledThreadPool(4, new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                final Thread t = new Thread(r, "frostlake-task-scheduler");
                t.setDaemon(true);
                return t;
            }
        });
    }

    /**
     * The scheduler key for a task: {@code DB.SCHEMA.TASK}, upper-cased. Every path that arms or
     * cancels a task must build it the same way, or a later SUSPEND cancels nothing because it looks
     * up a key that RESUME never wrote. Shared by {@code ALTER TASK … RESUME/SUSPEND} and by
     * {@link #start()}'s arming scan.
     */
    public static String schedulerKey(final String databaseName, final String schemaName, final String taskName) {
        return (databaseName + "." + schemaName + "." + taskName).toUpperCase();
    }

    /**
     * Start the scheduler and arm every task already in the STARTED state.
     *
     * <p>The scan matters because a task can reach STARTED without anyone calling
     * {@link #resumeTask}: a snapshot restore writes the state straight onto the model
     * ({@code CatalogSnapshotReader}), so a task that was running when the engine was persisted comes
     * back STARTED but unarmed, and would never fire again until it was manually RESUMEd. Arming here
     * makes "it was running before the restart" survive the restart.
     *
     * <p>Still OPT-IN — nothing starts the scheduler at engine boot, so an embedded engine spawns no
     * timer threads unless the embedder asks for them (or a RESUME lazy-starts it).
     */
    public void start() {
        if (running) {
            return;
        }
        // A previous stop() shut the pool down for good; a restart needs a fresh one, or every
        // scheduleAtFixedRate below would be rejected.
        if (scheduler.isShutdown()) {
            scheduler = newSchedulerPool();
        }
        running = true;
        armStartedTasks();
        armStartedAlerts();
        logger.info("Task scheduler started");
    }

    /** Arm every STARTED task in the catalog that is not already scheduled. */
    private void armStartedTasks() {
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema schema : database.getAllSchemas()) {
                for (final Task task : schema.getTasks()) {
                    if (task.getState() != TaskState.STARTED) {
                        continue;
                    }
                    final String key = schedulerKey(database.getName(), schema.getName(), task.getName());
                    if (scheduledTasks.containsKey(key)) {
                        continue; // already armed — do not stack a second timer on the same task
                    }
                    scheduleTask(key, task);
                }
            }
        }
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        // Cancel all scheduled tasks
        for (final ScheduledFuture<?> future : scheduledTasks.values()) {
            future.cancel(false);
        }
        scheduledTasks.clear();
        cronGenerations.clear();

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (final InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("Task scheduler stopped");
    }

    public void scheduleTask(final String qualifiedTaskName, final Task task) {
        if (!running) {
            logger.warn("Task scheduler not running, cannot schedule task: {}", qualifiedTaskName);
            return;
        }

        if (task.getState() != TaskState.STARTED) {
            logger.debug("Task {} is not in STARTED state, skipping schedule", qualifiedTaskName);
            return;
        }

        // Skip scheduling for manual-only tasks (no schedule)
        if (task.getSchedule() == null) {
            logger.info("Task {} is manual-only (no schedule), skipping automatic scheduling", qualifiedTaskName);
            return;
        }

        // A CRON schedule names calendar instants, not an interval: see armCron.
        if (task.getScheduleType() == ScheduleType.CRON) {
            armCron(qualifiedTaskName, task);
            return;
        }

        // Parse schedule
        final long delaySeconds = parseSchedule(task.getSchedule(), task.getScheduleType());

        // Schedule the task. The first run happens at the NEXT schedule tick (Snowflake semantics),
        // not immediately on RESUME.
        final ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
            new Runnable() {
                @Override
                public void run() {
                    executeTask(qualifiedTaskName, task);
                }
            },
            delaySeconds,
            delaySeconds,
            TimeUnit.SECONDS
        );

        scheduledTasks.put(qualifiedTaskName, future);
        task.setNextRunTime(LocalDateTime.now().plusSeconds(delaySeconds));

        logger.info("Scheduled task: {} with interval: {} seconds", qualifiedTaskName, delaySeconds);
    }

    public void unscheduleTask(final String qualifiedTaskName) {
        final ScheduledFuture<?> future;
        synchronized (armLock) {
            cronGenerations.remove(qualifiedTaskName);
            future = scheduledTasks.remove(qualifiedTaskName);
        }
        if (future != null) {
            future.cancel(false);
            logger.info("Unscheduled task: {}", qualifiedTaskName);
        }
    }

    /**
     * Arm a CRON task's NEXT calendar fire as a one-shot timer that re-arms itself once its run is
     * over. The schedule names wall-clock instants in its own zone — {@code 30 9 * * 1-5
     * America/Los_Angeles} is 09:30 on weekdays there — which no fixed rate counted from RESUME can
     * express. Computing the next fire only after a run finishes also gives NO_OVERLAP for free: a
     * fire that falls due while the previous run is still going is skipped, not queued.
     */
    private void armCron(final String qualifiedTaskName, final Task task) {
        final CronSchedule cron;
        try {
            cron = CronSchedule.parse(task.getSchedule());
        } catch (final RuntimeException unusable) {
            logger.warn("Task {} has an unusable CRON schedule '{}' — not armed", qualifiedTaskName,
                task.getSchedule());
            return;
        }
        final ZonedDateTime now = ZonedDateTime.now(cron.zone());
        final ZonedDateTime next = cron.nextFireAfter(now);
        if (next == null) {
            logger.warn("Task {} CRON schedule '{}' names no future instant — not armed", qualifiedTaskName,
                task.getSchedule());
            return;
        }
        synchronized (armLock) {
            if (!running || task.getState() != TaskState.STARTED) {
                return;
            }
            final long generation = ++lastGeneration;
            cronGenerations.put(qualifiedTaskName, generation);
            final ScheduledFuture<?> future = scheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    try {
                        executeTask(qualifiedTaskName, task);
                    } finally {
                        rearmCron(qualifiedTaskName, task, generation);
                    }
                }
            }, Math.max(0L, Duration.between(now, next).toMillis()), TimeUnit.MILLISECONDS);
            scheduledTasks.put(qualifiedTaskName, future);
            task.setNextRunTime(next.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime());
        }
        logger.info("Scheduled task: {} for {} ({})", qualifiedTaskName, next, task.getSchedule());
    }

    /** Re-arm a CRON task after its run, unless a SUSPEND or a later RESUME retired this timer. */
    private void rearmCron(final String qualifiedTaskName, final Task task, final long generation) {
        synchronized (armLock) {
            final Long current = cronGenerations.get(qualifiedTaskName);
            if (current == null || current.longValue() != generation) {
                return;
            }
            armCron(qualifiedTaskName, task);
        }
    }

    /** The scheduler key of an alert: its qualified name, kept apart from the task keys. */
    public static String alertKey(final String databaseName, final String schemaName, final String alertName) {
        return "ALERT " + databaseName + "." + schemaName + "." + alertName;
    }

    /** Arm every STARTED alert in the catalog that is not already scheduled. */
    private void armStartedAlerts() {
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema schema : database.getAllSchemas()) {
                for (final Alert alert : schema.getAlerts()) {
                    if (alert.getState() == AlertState.STARTED
                            && !scheduledTasks.containsKey(alertKey(database.getName(), schema.getName(),
                                alert.getName()))) {
                        armAlert(database.getName(), schema.getName(), alert);
                    }
                }
            }
        }
    }

    /**
     * ALTER ALERT … RESUME: the alert is started and its schedule armed, the first evaluation falling due one
     * interval (or at the next CRON instant) after the resume. Starts the scheduler when it is not running.
     */
    public void resumeAlert(final String databaseName, final String schemaName, final Alert alert) {
        start();
        alert.setState(AlertState.STARTED);
        if (Boolean.TRUE.equals(alert.getWasAutoSuspended())) {
            alert.setWasAutoSuspended(Boolean.FALSE);
        }
        armAlert(databaseName, schemaName, alert);
    }

    /** ALTER ALERT … SUSPEND, or a dropped alert: the alert's schedule is disarmed. */
    public void suspendAlert(final String databaseName, final String schemaName, final Alert alert) {
        alert.setState(AlertState.SUSPENDED);
        alert.setNextScheduledTime(null);
        unscheduleTask(alertKey(databaseName, schemaName, alert.getName()));
    }

    /** Re-arms a started alert whose schedule changed. */
    public void rescheduleAlert(final String databaseName, final String schemaName, final Alert alert) {
        unscheduleTask(alertKey(databaseName, schemaName, alert.getName()));
        armAlert(databaseName, schemaName, alert);
    }

    /** EXECUTE ALERT: the alert is evaluated now, in the calling thread, whatever its state. */
    public AlertExecution executeAlertNow(final String databaseName, final String schemaName, final Alert alert) {
        return new AlertRunner(catalog, taskExecutor).run(databaseName, schemaName, alert, "EXECUTE ALERT");
    }

    /**
     * Arms a started alert's schedule: an interval schedule fires every {@code n} minutes from now, a CRON one
     * at its next calendar instant and then re-arms after each evaluation. An evaluation that falls due while
     * the previous one still runs is skipped.
     */
    private void armAlert(final String databaseName, final String schemaName, final Alert alert) {
        if (!running || alert.getState() != AlertState.STARTED || alert.getSchedule() == null) {
            return;
        }
        final String key = alertKey(databaseName, schemaName, alert.getName());
        final Runnable evaluation = new Runnable() {
            @Override
            public void run() {
                if (alert.getState() != AlertState.STARTED || !runningTasks.add(key)) {
                    return;
                }
                try {
                    new AlertRunner(catalog, taskExecutor).run(databaseName, schemaName, alert, "SCHEDULE");
                } catch (final RuntimeException failure) {
                    logger.error("Alert {} evaluation failed: {}", key, failure.getMessage());
                } finally {
                    runningTasks.remove(key);
                }
                if (alert.getState() != AlertState.STARTED) {
                    unscheduleTask(key);
                }
            }
        };
        if (alert.getSchedule().toUpperCase(Locale.ROOT).contains("CRON")) {
            armAlertCron(key, alert, evaluation);
            return;
        }
        final long seconds = AlertRunner.seconds(alert.getSchedule());
        synchronized (armLock) {
            scheduledTasks.put(key, scheduler.scheduleAtFixedRate(evaluation, seconds, seconds, TimeUnit.SECONDS));
        }
        alert.setNextScheduledTime(Instant.now().plusSeconds(seconds));
    }

    private void armAlertCron(final String key, final Alert alert, final Runnable evaluation) {
        final CronSchedule cron;
        try {
            cron = CronSchedule.parse(alert.getSchedule());
        } catch (final RuntimeException unusable) {
            logger.warn("Alert {} has an unusable CRON schedule '{}' — not armed", key, alert.getSchedule());
            return;
        }
        final ZonedDateTime now = ZonedDateTime.now(cron.zone());
        final ZonedDateTime next = cron.nextFireAfter(now);
        if (next == null) {
            return;
        }
        synchronized (armLock) {
            if (!running || alert.getState() != AlertState.STARTED) {
                return;
            }
            final long generation = ++lastGeneration;
            cronGenerations.put(key, generation);
            scheduledTasks.put(key, scheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    try {
                        evaluation.run();
                    } finally {
                        synchronized (armLock) {
                            final Long current = cronGenerations.get(key);
                            if (current != null && current.longValue() == generation) {
                                armAlertCron(key, alert, evaluation);
                            }
                        }
                    }
                }
            }, Math.max(0L, Duration.between(now, next).toMillis()), TimeUnit.MILLISECONDS));
            alert.setNextScheduledTime(next.toInstant());
        }
    }

    public void resumeTask(final String qualifiedTaskName, final Task task) {
        // Lazy-start: ALTER TASK … RESUME arms the schedule without requiring the embedder to have
        // called startTaskScheduler() first (Snowflake semantics — RESUME is what activates a task).
        start();
        task.setState(TaskState.STARTED);
        scheduleTask(qualifiedTaskName, task);
    }

    public void suspendTask(final String qualifiedTaskName, final Task task) {
        task.setState(TaskState.SUSPENDED);
        unscheduleTask(qualifiedTaskName);
    }

    private void executeTask(final String qualifiedTaskName, final Task task) {
        runGraph(qualifiedTaskName, task, TaskTrigger.SCHEDULE, task.getConfig());
    }

    /**
     * One run of the graph a root task starts: the root, the resumed tasks after it, and once they are done
     * the root's finalizer task when it is resumed — whether the graph succeeded or failed, but not when the
     * root's condition skipped the run. Every task of the run reads the one configuration.
     *
     * @param rootKey the root's scheduler key
     * @param root    the root task
     * @param trigger what started the run
     * @param config  the run's configuration, or null
     */
    private void runGraph(final String rootKey, final Task root, final TaskTrigger trigger, final String config) {
        final String prior = CURRENT_GRAPH_CONFIG.get();
        CURRENT_GRAPH_CONFIG.set(config);
        try {
            final int runsBefore = root.getExecutionHistory().size();
            final Set<String> visited = new HashSet<>();
            executeTask(rootKey, root, visited, trigger);
            if (root.getExecutionHistory().size() > runsBefore) {
                runFinalizer(rootKey, root, visited, trigger);
            }
        } finally {
            if (prior == null) {
                CURRENT_GRAPH_CONFIG.remove();
            } else {
                CURRENT_GRAPH_CONFIG.set(prior);
            }
        }
    }

    /** Run the resumed finalizer task of a root, after the rest of its graph run. */
    private void runFinalizer(final String rootKey, final Task root, final Set<String> visited,
                              final TaskTrigger trigger) {
        final String[] parts = QualifiedName.parse(rootKey).parts();
        if (parts.length != 3) {
            return;
        }
        final Database database = catalog.getDatabase(parts[0]);
        final Schema schema = database == null ? null : database.getSchema(parts[1]);
        final Task finalizer = schema == null ? null : TaskGraphs.finalizerOf(schema, root);
        if (finalizer != null && finalizer.getState() == TaskState.STARTED) {
            executeTask(schedulerKey(parts[0], parts[1], finalizer.getName()), finalizer, visited, trigger);
        }
    }

    private void executeTask(final String qualifiedTaskName, final Task task, final Set<String> visited,
                             final TaskTrigger trigger) {
        // Each task runs at most once per DAG run; the set also guards against dependency cycles.
        if (!visited.add(task.getName().toUpperCase())) {
            return;
        }
        // ALLOW_OVERLAPPING_EXECUTION = FALSE (the default): a run that would overlap an in-flight
        // run of the same task is skipped rather than executed concurrently.
        final String runKey = qualifiedTaskName.toUpperCase();
        final boolean overlapGuard = !task.isAllowOverlappingExecution();
        if (overlapGuard && !runningTasks.add(runKey)) {
            logger.debug("Task {} is already running and overlapping execution is not allowed — skipping",
                qualifiedTaskName);
            return;
        }
        try {
            runTaskOnce(qualifiedTaskName, task, visited, trigger);
        } finally {
            if (overlapGuard) {
                runningTasks.remove(runKey);
            }
        }
    }

    private void runTaskOnce(final String qualifiedTaskName, final Task task, final Set<String> visited,
                             final TaskTrigger trigger) {
        // Run the whole task (WHEN condition, body, DAG cascade) with its HOME database/schema bound to
        // this thread only. The scheduler fires on its own thread against the shared engine: resolving
        // and save/restoring context through the global fields raced the interactive thread — a slow
        // task's unwind restored a stale (possibly meanwhile-dropped) database over whatever that
        // thread had switched to, and the task body itself resolved unqualified names against the
        // interactive session's database instead of its own. Nested scopes (DAG children) restore the
        // parent's scope on exit.
        final String[] nameParts = QualifiedName.parse(qualifiedTaskName).parts();
        final String taskDatabase = nameParts.length == 3 ? nameParts[0] : catalog.getCurrentDatabase();
        final String taskSchema = nameParts.length >= 2 ? nameParts[nameParts.length - 2] : catalog.getCurrentSchema();
        final String[] priorScope = catalog.currentSessionScope();
        catalog.beginSessionScope(taskDatabase, taskSchema);
        try {
            runTaskOnceInScope(qualifiedTaskName, task, visited, trigger);
        } finally {
            catalog.restoreSessionScope(priorScope);
        }
    }

    private void runTaskOnceInScope(final String qualifiedTaskName, final Task task, final Set<String> visited,
                                    final TaskTrigger trigger) {
        final LocalDateTime scheduledTime = LocalDateTime.now();
        logger.info("Executing task: {}", qualifiedTaskName);
        // (Predecessor gating is implemented by the cascade itself: a child runs only via
        // runDependentTasks after its predecessor completes successfully. USER_TASK_TIMEOUT_MS is
        // NOT enforced — the engine has no statement-cancellation mechanism to interrupt a run.)

        // Evaluate WHEN condition — skip execution if condition is false
        if (task.getCondition() != null && !task.getCondition().isEmpty()) {
            try {
                final List<ResultSet> condResult =
                    taskExecutor.executeQuery("SELECT (" + task.getCondition() + ")");
                if (!condResult.isEmpty() && !condResult.get(0).getRows().isEmpty()) {
                    final Object condVal = condResult.get(0).getRows().get(0).getValue(0);
                    final boolean condMet = condVal instanceof Boolean ? (Boolean) condVal
                        : condVal != null && !"false".equalsIgnoreCase(condVal.toString())
                            && !"0".equals(condVal.toString());
                    if (!condMet) {
                        logger.debug("Task {} WHEN condition evaluated to false, skipping", qualifiedTaskName);
                        return;
                    }
                }
            } catch (final Exception e) {
                // A WHEN condition that cannot be evaluated (e.g. references a dropped object)
                // skips the run — it must not fall through and execute the body unconditionally.
                logger.warn("Failed to evaluate WHEN condition for task {} — skipping run: {}",
                    qualifiedTaskName, e.getMessage());
                return;
            }
        }

        // Execute the body, with TASK_AUTO_RETRY_ATTEMPTS retries on failure. Each attempt is
        // recorded in the task history. On the final failed attempt, SUSPEND_TASK_AFTER_NUM_FAILURES
        // suspends the task once its CONSECUTIVE failure count reaches the threshold.
        CURRENT_TASK.set(task);
        try {
            final int maxAttempts = 1 + Math.max(0, task.getTaskAutoRetryAttempts());
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                final LocalDateTime startTime = LocalDateTime.now();
                try {
                    final int rowsAffected = taskExecutor.execute(task.getSqlStatement());

                    task.recordExecution(new TaskExecution(scheduledTime, startTime, LocalDateTime.now(),
                        TaskExecutionState.SUCCEEDED, null, rowsAffected, trigger));

                    // Calculate next run time (only for interval-scheduled tasks — a CRON task
                    // stamps its next fire when it re-arms)
                    if (task.getSchedule() != null && task.getScheduleType() != ScheduleType.CRON) {
                        final long delaySeconds = parseSchedule(task.getSchedule(), task.getScheduleType());
                        task.setNextRunTime(LocalDateTime.now().plusSeconds(delaySeconds));
                    }

                    logger.info("Task {} completed successfully. Rows affected: {}", qualifiedTaskName, rowsAffected);

                    // Run dependent (AFTER this-task) tasks: a DAG child executes once its predecessor
                    // completes successfully, provided the child is in the resumed (STARTED) state. A
                    // skipped (WHEN false) or failed parent does not cascade.
                    runDependentTasks(task, visited, trigger);
                    return;

                } catch (final Exception e) {
                    task.recordExecution(new TaskExecution(scheduledTime, startTime, LocalDateTime.now(),
                        TaskExecutionState.FAILED, e.getMessage(), 0, trigger));
                    logger.error("Task {} failed (attempt {}/{}): {}",
                        qualifiedTaskName, attempt, maxAttempts, e.getMessage());

                    if (attempt == maxAttempts
                            && task.getSuspendTaskAfterNumFailures() > 0
                            && task.getFailureCount() >= task.getSuspendTaskAfterNumFailures()) {
                        logger.warn("Task {} reached {} consecutive failures — suspending",
                            qualifiedTaskName, task.getFailureCount());
                        suspendTask(qualifiedTaskName, task);
                    }
                }
            }
        } finally {
            CURRENT_TASK.remove();
        }
    }

    /** An interval schedule's length in seconds ({@code 90 SECONDS}, {@code 1 HOUR}); an hour when it is none. */
    private long parseSchedule(final String schedule, final ScheduleType scheduleType) {
        if (scheduleType == ScheduleType.MINUTES) {
            final long seconds = IntervalSchedule.seconds(schedule);
            if (seconds > 0L) {
                return seconds;
            }
            logger.warn("Invalid schedule format: {}, defaulting to 60 minutes", schedule);
        }
        return 3600L;
    }

    /**
     * Execute every resumed task that lists {@code parent} as an AFTER predecessor. Children are
     * looked up across the current database's schemas (a Snowflake task DAG lives in one schema);
     * predecessor references are matched on their bare task name, case-insensitively. Depth-first:
     * in a diamond DAG each task still runs exactly once (via the visited set), though not
     * necessarily after ALL of its predecessors — acceptable for this simplified scheduler.
     */
    private void runDependentTasks(final Task parent, final Set<String> visited, final TaskTrigger trigger) {
        final String currentDb = catalog.getCurrentDatabase();
        final Database db = currentDb != null ? catalog.getDatabase(currentDb) : null;
        if (db == null) {
            return;
        }
        for (final Schema schema : db.getAllSchemas()) {
            for (final Task candidate : schema.getTasks()) {
                if (candidate.getState() != TaskState.STARTED) {
                    continue; // only resumed children run
                }
                for (final String predecessor : candidate.getPredecessors()) {
                    final int dot = predecessor.lastIndexOf('.');
                    final String bareName = dot >= 0 ? predecessor.substring(dot + 1) : predecessor;
                    if (bareName.equalsIgnoreCase(parent.getName())) {
                        executeTask(schema.getName() + "." + candidate.getName(), candidate, visited, trigger);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Execute a task immediately (for testing)
     */
    public void executeTaskNow(final String qualifiedTaskName, final Task task) {
        executeTask(qualifiedTaskName, task);
    }

    /**
     * Execute a task manually by name (for EXECUTE TASK command)
     */
    public void executeTaskManually(final String qualifiedTaskName) {
        executeTaskManually(qualifiedTaskName, false);
    }

    /**
     * Execute a root task manually by name: EXECUTE TASK, or with {@code retryLast} EXECUTE TASK … RETRY LAST,
     * which starts a new graph run at the task runs that failed in the root task's last graph run.
     */
    public void executeTaskManually(final String qualifiedTaskName, final boolean retryLast) {
        executeTaskManually(qualifiedTaskName, retryLast, null, null);
    }

    /**
     * Execute a root task manually by name: EXECUTE TASK, optionally {@code USING CONFIG} a configuration merged
     * over the task's own for this run alone; with {@code retryLast} EXECUTE TASK … RETRY LAST, and with a
     * group id EXECUTE TASK … RETRY GRAPH RUN GROUP, each starting a new graph run at the task runs that failed
     * in that graph run.
     *
     * @param qualifiedTaskName  the task as named
     * @param retryLast          whether to retry the last graph run
     * @param retryGraphRunGroup the GRAPH_RUN_GROUP_ID of the graph run to retry, or null
     * @param usingConfig        the run's configuration, or null
     */
    public void executeTaskManually(final String qualifiedTaskName, final boolean retryLast,
                                    final String retryGraphRunGroup, final String usingConfig) {
        // Parse qualified name to extract database, schema and task name
        final String[] parts = QualifiedName.parse(qualifiedTaskName).parts();
        final String databaseName;
        final String schemaName;
        final String taskName;

        if (parts.length == 1) {
            // Just task name, use current schema
            databaseName = catalog.getCurrentDatabase();
            schemaName = catalog.getCurrentSchema();
            taskName = parts[0];
        } else if (parts.length == 2) {
            // schema.task, in the current database
            databaseName = catalog.getCurrentDatabase();
            schemaName = parts[0];
            taskName = parts[1];
        } else {
            // database.schema.task — the database part is honoured, so a task in another database
            // runs from wherever the session is (live-verified)
            databaseName = parts[0];
            schemaName = parts[1];
            taskName = parts[2];
        }

        final Database db = databaseName == null ? null : catalog.getDatabase(databaseName);
        if (db == null) {
            throw new RuntimeException("No current database selected");
        }

        final Schema schema = db.getSchema(schemaName);
        if (schema == null) {
            throw new RuntimeException("Schema not found: " + schemaName);
        }

        final Task task = schema.getTask(taskName);
        if (task == null) {
            throw new RuntimeException("Task not found: " + qualifiedTaskName);
        }

        // ★ ONLY A ROOT RUNS ON DEMAND. A task with an AFTER list is refused whether it is resumed or
        // suspended — its graph runs from the root, which cascades to the resumed children. The
        // sentence carries no compilation prefix and names the task fully qualified (live-verified).
        final String key = schedulerKey(db.getName(), schema.getName(), task.getName());
        if (!task.getPredecessors().isEmpty() || task.isFinalizer()) {
            throw new RuntimeException("Execute task cannot be called on non-root task " + key
                + ". Call EXECUTE TASK on the root task of its graph instead.");
        }
        // The configuration is weighed once the task is known to be a root (live-verified order).
        if (usingConfig != null) {
            TaskGraphConfig.requireObject(usingConfig);
        }

        if (retryLast) {
            retryLastGraphRun(key, task, schema);
            return;
        }
        if (retryGraphRunGroup != null) {
            retryGraphRunGroup(key, task, schema, retryGraphRunGroup);
            return;
        }
        // Run under the canonical key: the run binds the task's HOME database whatever the session's
        // current one is, and shares the overlap guard with the scheduled runs of the same task.
        runGraph(key, task, TaskTrigger.EXECUTE_TASK, TaskGraphConfig.merge(task.getConfig(), usingConfig));
    }

    /**
     * RETRY GRAPH RUN GROUP: the graph run of the root with that GRAPH_RUN_GROUP_ID runs again from the task
     * runs of it that failed, as RETRY LAST does for the latest one.
     *
     * @throws RuntimeException when no graph run of the root has that id, or when it has no failed task run
     */
    private void retryGraphRunGroup(final String rootKey, final Task root, final Schema schema, final String group) {
        final List<TaskExecution> rootRuns = root.getExecutionHistory();
        for (int i = rootRuns.size() - 1; i >= 0; i--) {
            final TaskExecution run = rootRuns.get(i);
            if (group.equals(TaskGraphs.graphRunGroupId(root, TaskGraphs.runId(run.getScheduledTime())))) {
                final LocalDateTime until = i + 1 < rootRuns.size() ? rootRuns.get(i + 1).getStartTime() : null;
                retryGraphRun(rootKey, root, schema, run, until);
                return;
            }
        }
        throw new RuntimeException("Cannot perform retry: Graph run group with ID " + group + " not found.");
    }

    /**
     * RETRY LAST: when the root's own last run failed the whole graph runs again; otherwise every task of the
     * graph whose run in the last graph run failed runs again, cascading to its dependents.
     *
     * @throws RuntimeException when the last graph run has no failed task run
     */
    private void retryLastGraphRun(final String rootKey, final Task root, final Schema schema) {
        final List<TaskExecution> rootRuns = root.getExecutionHistory();
        if (rootRuns.isEmpty()) {
            throw new RuntimeException("Cannot perform retry: no suitable run of graph with root task " + root.getName()
                + " to retry.");
        }
        retryGraphRun(rootKey, root, schema, rootRuns.get(rootRuns.size() - 1), null);
    }

    /**
     * Run one graph run again from its failures: the whole graph when the root's own run failed, otherwise
     * every task of the graph whose run in it failed, cascading to its dependents.
     *
     * @param run   the root's run that started the graph run
     * @param until when the next graph run began, or null for the latest one
     */
    private void retryGraphRun(final String rootKey, final Task root, final Schema schema, final TaskExecution run,
                               final LocalDateTime until) {
        final String prior = CURRENT_GRAPH_CONFIG.get();
        CURRENT_GRAPH_CONFIG.set(root.getConfig());
        try {
            if (TaskExecutionState.FAILED.name().equals(run.getState())) {
                executeTask(rootKey, root, new HashSet<>(), TaskTrigger.MANUAL_RETRY);
                return;
            }
            final LocalDateTime graphStart = run.getStartTime();
            final List<Task> failed = new ArrayList<>();
            for (final Task candidate : TaskGraphs.descendants(schema, root, true)) {
                TaskExecution last = null;
                for (final TaskExecution candidateRun : candidate.getExecutionHistory()) {
                    final LocalDateTime began = candidateRun.getStartTime();
                    if (began != null && (graphStart == null || !began.isBefore(graphStart))
                            && (until == null || began.isBefore(until))) {
                        last = candidateRun;
                    }
                }
                if (last != null && TaskExecutionState.FAILED.name().equals(last.getState())) {
                    failed.add(candidate);
                }
            }
            if (failed.isEmpty()) {
                throw new RuntimeException(noFailures(root, run));
            }
            final Set<String> visited = new HashSet<>();
            for (final Task task : failed) {
                executeTask(schedulerKey(schema.getDatabaseName(), schema.getName(), task.getName()), task, visited,
                    TaskTrigger.MANUAL_RETRY);
            }
        } finally {
            if (prior == null) {
                CURRENT_GRAPH_CONFIG.remove();
            } else {
                CURRENT_GRAPH_CONFIG.set(prior);
            }
        }
    }

    /** The refusal of a retry whose graph run failed nowhere, naming the run as the graph functions do. */
    private static String noFailures(final Task root, final TaskExecution rootRun) {
        final Long runId = rootRun.getScheduledTime() == null ? null
            : Long.valueOf(ShowResultHelpers.createdOn(rootRun.getScheduledTime()).toInstant().toEpochMilli());
        return "Cannot perform retry: run (GRAPH_RUN_GROUP_ID = " + GraphRunGroup.id(root.getId(), runId)
            + ", attempt = 1) of graph with root task " + root.getName() + " had no failures.";
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Whether this task currently holds a live timer entry. The key must be built with
     * {@link #schedulerKey}; anything else answers false because it is not the key the scheduler
     * armed under.
     */
    public boolean isScheduled(final String qualifiedTaskName) {
        return scheduledTasks.containsKey(qualifiedTaskName);
    }

    /**
     * Whether a run of this task is in flight right now. Only tracked for tasks that forbid
     * overlapping execution (the default) — a task with {@code ALLOW_OVERLAPPING_EXECUTION = TRUE}
     * needs no in-flight bookkeeping, so it always reports false.
     */
    public boolean hasRunningExecutions(final String qualifiedTaskName) {
        return runningTasks.contains(qualifiedTaskName.toUpperCase());
    }
}
