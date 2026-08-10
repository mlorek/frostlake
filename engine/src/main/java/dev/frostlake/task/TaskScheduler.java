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

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    // Tasks currently executing, for the ALLOW_OVERLAPPING_EXECUTION=FALSE guard.
    private final Set<String> runningTasks = ConcurrentHashMap.newKeySet();
    // The task whose body is executing on this thread — read by SYSTEM$SET_RETURN_VALUE /
    // SYSTEM$GET_PREDECESSOR_RETURN_VALUE / SYSTEM$CURRENT_USER_TASK_NAME.
    private static final ThreadLocal<Task> CURRENT_TASK = new ThreadLocal<>();

    /** The task whose body is executing on the current thread, or null outside a task run. */
    public static Task currentTask() {
        return CURRENT_TASK.get();
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

        // Parse schedule
        final long delayMinutes = parseSchedule(task.getSchedule(), task.getScheduleType());

        // Schedule the task. The first run happens at the NEXT schedule tick (Snowflake semantics),
        // not immediately on RESUME.
        final ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
            new Runnable() {
                @Override
                public void run() {
                    executeTask(qualifiedTaskName, task);
                }
            },
            delayMinutes,
            delayMinutes,
            TimeUnit.MINUTES
        );

        scheduledTasks.put(qualifiedTaskName, future);
        task.setNextRunTime(LocalDateTime.now().plusMinutes(delayMinutes));

        logger.info("Scheduled task: {} with interval: {} minutes", qualifiedTaskName, delayMinutes);
    }

    public void unscheduleTask(final String qualifiedTaskName) {
        final ScheduledFuture<?> future = scheduledTasks.remove(qualifiedTaskName);
        if (future != null) {
            future.cancel(false);
            logger.info("Unscheduled task: {}", qualifiedTaskName);
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
        executeTask(qualifiedTaskName, task, new HashSet<>());
    }

    private void executeTask(final String qualifiedTaskName, final Task task, final Set<String> visited) {
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
            runTaskOnce(qualifiedTaskName, task, visited);
        } finally {
            if (overlapGuard) {
                runningTasks.remove(runKey);
            }
        }
    }

    private void runTaskOnce(final String qualifiedTaskName, final Task task, final Set<String> visited) {
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
            runTaskOnceInScope(qualifiedTaskName, task, visited);
        } finally {
            catalog.restoreSessionScope(priorScope);
        }
    }

    private void runTaskOnceInScope(final String qualifiedTaskName, final Task task, final Set<String> visited) {
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

                    task.recordExecution(new TaskExecution(
                        scheduledTime, startTime, LocalDateTime.now(), TaskExecutionState.SUCCEEDED, null, rowsAffected));

                    // Calculate next run time (only for scheduled tasks)
                    if (task.getSchedule() != null) {
                        final long delayMinutes = parseSchedule(task.getSchedule(), task.getScheduleType());
                        task.setNextRunTime(LocalDateTime.now().plusMinutes(delayMinutes));
                    }

                    logger.info("Task {} completed successfully. Rows affected: {}", qualifiedTaskName, rowsAffected);

                    // Run dependent (AFTER this-task) tasks: a DAG child executes once its predecessor
                    // completes successfully, provided the child is in the resumed (STARTED) state. A
                    // skipped (WHEN false) or failed parent does not cascade.
                    runDependentTasks(task, visited);
                    return;

                } catch (final Exception e) {
                    task.recordExecution(new TaskExecution(
                        scheduledTime, startTime, LocalDateTime.now(), TaskExecutionState.FAILED, e.getMessage(), 0));
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

    private long parseSchedule(final String schedule, final ScheduleType scheduleType) {
        if (scheduleType == ScheduleType.MINUTES) {
            // Format: "5 MINUTES", "60 MINUTES"
            final String[] parts = schedule.split("\\s+");
            if (parts.length > 0) {
                try {
                    return Long.parseLong(parts[0]);
                } catch (final NumberFormatException e) {
                    logger.warn("Invalid schedule format: {}, defaulting to 60 minutes", schedule);
                    return 60;
                }
            }
        } else if (scheduleType == ScheduleType.CRON) {
            return parseCronInterval(schedule);
        }

        return 60; // Default to 60 minutes
    }

    /**
     * Approximate a cron expression ("USING CRON m h dom mon dow [tz]") as a fixed re-run interval
     * in minutes: minute steps ({@code *}{@code /N}) → N; every minute → 1; hourly (fixed minute,
     * any hour) → 60; weekly (day-of-week set) → 10080; monthly (day-of-month set) → 43200; else
     * daily → 1440. This is an interval scheduler, not a calendar scheduler — the cadence matches
     * the cron's period even though exact wall-clock alignment is not honored. (Previously every
     * cron parsed to a constant 5 minutes.)
     */
    private long parseCronInterval(final String schedule) {
        final List<String> fields = new ArrayList<>();
        for (final String token : schedule.trim().split("\\s+")) {
            if (token.equalsIgnoreCase("USING") || token.equalsIgnoreCase("CRON")) {
                continue;
            }
            fields.add(token);
        }
        // Drop trailing timezone token(s) beyond the 5 cron fields.
        while (fields.size() > 5) {
            fields.remove(fields.size() - 1);
        }
        if (fields.size() < 5) {
            logger.warn("Unrecognized CRON schedule '{}' — defaulting to 60 minutes", schedule);
            return 60;
        }
        final String minute = fields.get(0);
        final String hour = fields.get(1);
        final String dayOfMonth = fields.get(2);
        final String dayOfWeek = fields.get(4);

        if (minute.startsWith("*/")) {
            try {
                return Math.max(1, Long.parseLong(minute.substring(2)));
            } catch (final NumberFormatException e) {
                logger.warn("Unrecognized CRON minute step '{}' — defaulting to 60 minutes", minute);
                return 60;
            }
        }
        if (minute.equals("*")) {
            return 1;
        }
        if (hour.equals("*")) {
            return 60;             // fixed minute, every hour
        }
        if (!dayOfWeek.equals("*")) {
            return 7L * 24 * 60;   // weekly
        }
        if (!dayOfMonth.equals("*")) {
            return 30L * 24 * 60;  // ~monthly
        }
        return 24L * 60;           // daily
    }

    /**
     * Execute every resumed task that lists {@code parent} as an AFTER predecessor. Children are
     * looked up across the current database's schemas (a Snowflake task DAG lives in one schema);
     * predecessor references are matched on their bare task name, case-insensitively. Depth-first:
     * in a diamond DAG each task still runs exactly once (via the visited set), though not
     * necessarily after ALL of its predecessors — acceptable for this simplified scheduler.
     */
    private void runDependentTasks(final Task parent, final Set<String> visited) {
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
                        executeTask(schema.getName() + "." + candidate.getName(), candidate, visited);
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
        // Parse qualified name to extract schema and task name
        final String[] parts = QualifiedName.parse(qualifiedTaskName).parts();
        final String schemaName;
        final String taskName;

        if (parts.length == 1) {
            // Just task name, use current schema
            schemaName = catalog.getCurrentSchema();
            taskName = parts[0];
        } else if (parts.length == 2) {
            // schema.task
            schemaName = parts[0];
            taskName = parts[1];
        } else {
            // database.schema.task
            schemaName = parts[1];
            taskName = parts[2];
        }

        final Database db = catalog.getDatabase(catalog.getCurrentDatabase());
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

        // Execute the task
        executeTask(qualifiedTaskName, task);
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
