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

package dev.frostlake;

import dev.frostlake.config.EngineConfig;
import dev.frostlake.config.S3PathResolver;
import dev.frostlake.executor.ApartRecording;
import dev.frostlake.executor.CallDepth;
import dev.frostlake.executor.InheritedSessionParameters;
import dev.frostlake.executor.OwnSessionStatementSink;
import dev.frostlake.executor.ProceduralExecutor;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.QueryResultCache;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.StatementBoundaries;
import dev.frostlake.executor.StatementClock;
import dev.frostlake.executor.StatementNesting;
import dev.frostlake.executor.udf.UdfLanguageRuntime;
import dev.frostlake.executor.udf.UdfRuntimes;
import dev.frostlake.executor.udf.UserAggregates;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.BuiltInObjects;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.CounterDraw;
import dev.frostlake.metastore.QueryHistory;
import dev.frostlake.metastore.StatementAnswers;
import dev.frostlake.metastore.StatementDraws;
import dev.frostlake.metastore.TemporaryObjects;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.metastore.model.User;
import dev.frostlake.parser.SqlSyntaxException;
import dev.frostlake.persistence.CatalogSnapshot;
import dev.frostlake.persistence.CheckpointDebt;
import dev.frostlake.persistence.MemoryTableDataStore;
import dev.frostlake.persistence.PersistenceManager;
import dev.frostlake.persistence.WalRecord;
import dev.frostlake.persistence.WalRecordType;
import dev.frostlake.persistence.WalReplayFailure;
import dev.frostlake.persistence.WalReplaySchedule;
import dev.frostlake.persistence.WalStatementKinds;
import dev.frostlake.persistence.WriteAheadLog;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.security.SessionContext;
import dev.frostlake.security.SessionDirectory;
import dev.frostlake.security.SessionSettings;
import dev.frostlake.storage.RecordedResult;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.StatementResults;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.stream.StreamManager;
import dev.frostlake.system.SystemViews;
import dev.frostlake.task.TaskExecutor;
import dev.frostlake.task.TaskScheduler;
import dev.frostlake.transaction.TransactionManager;
import dev.frostlake.transaction.WalSink;
import dev.frostlake.transaction.WalStatementContext;
import dev.frostlake.transaction.WriteSetSavepoint;
import dev.frostlake.values.VariantJsonFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main Database SQL Engine
 * Provides a complete SQL engine implementing Snowflake DB features
 */
public class DatabaseEngine {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseEngine.class);

    private final EngineConfig config;
    private final Catalog catalog;
    private final StorageEngine storageEngine;
    private final FunctionRegistry functionRegistry;
    private final TransactionManager transactionManager;
    private final SystemViews systemViews;
    private final QueryExecutor queryExecutor;
    private final ProceduralExecutor proceduralExecutor;
    private final StreamManager streamManager;
    private final TaskScheduler taskScheduler;
    private final SessionContext sessionContext;
    private final SecurityManager securityManager;
    private final PersistenceManager persistenceManager;
    private WriteAheadLog wal;            // durability.walEnabled: append committed transactions, replay on startup
    private boolean replaying = false;    // true while replaying the WAL on startup (suppresses re-logging)
    /** The logged transactions recovery could not re-apply at startup; see {@link #getWalReplayFailures()}. */
    private final List<WalReplayFailure> walReplayFailures = new ArrayList<>();
    private int txnsSinceCheckpoint = 0;  // committed transactions appended since the last WAL checkpoint
    /** How many transaction records this engine appended to the WAL: what places a logged statement on replay. */
    private final AtomicLong walAppends = new AtomicLong();
    private long checkpointSeq = 0;       // monotonic sequence for naming checkpoint snapshot directories
    /** When this engine started: the instant its built-in objects are created at (see BuiltInObjects). */
    private final Instant started;

    public DatabaseEngine() {
        this(new EngineConfig());
    }

    public DatabaseEngine(final EngineConfig config) {
        logger.info("Initializing Frostlake SQL Engine");

        this.started = StatementClock.instant();
        this.config = config;
        // Hand every discovered language runtime this engine's configuration (e.g. python.venv).
        // Runtime state is JVM-wide by nature (polyglot contexts are per-thread, not per-engine), so
        // implementations are idempotent; without the optional udf modules the loop is empty.
        for (final UdfLanguageRuntime udfRuntime : UdfRuntimes.all()) {
            udfRuntime.configure(config);
        }
        this.catalog = new Catalog();
        this.catalog.setAccountLocator(config.getAccountId());
        this.storageEngine = new StorageEngine();
        this.storageEngine.setEnforcePrimaryKey(config.isEnforcePrimaryKey());
        this.storageEngine.setEnforceUniqueKey(config.isEnforceUniqueKey());
        this.storageEngine.setTimeTravelEnabled(config.isTimeTravelEnabled());
        this.sessionContext = new SessionContext();
        this.functionRegistry = new FunctionRegistry(catalog, sessionContext, config);
        this.transactionManager = new TransactionManager(catalog, storageEngine);
        this.systemViews = new SystemViews(catalog, storageEngine);
        this.streamManager = new StreamManager(catalog);
        this.securityManager = new SecurityManager(catalog, sessionContext);
        this.persistenceManager = new PersistenceManager(config);
        this.queryExecutor = new QueryExecutor(catalog, storageEngine, functionRegistry, transactionManager, config);
        this.queryExecutor.setDatabaseEngine(this);
        this.queryExecutor.setSecurityManager(securityManager);
        // Share the S3 -> local resolver with the catalog so stage ops (LIST/GET/PUT/REMOVE) honor the same
        // mapping as IMPORTS JAR loading. Set before any persistence restore (loadCatalog) below.
        this.catalog.setS3PathResolver(queryExecutor.getS3PathResolver());
        // Let the catalog stamp the session's current role as owner on newly-created objects.
        this.catalog.setSessionContext(sessionContext);
        // A session runs with what its user and the account set for the parameters it does not set itself.
        sessionContext.setInheritedParameters(new InheritedSessionParameters(catalog));
        this.transactionManager.setSessionContext(sessionContext);

        // Create TaskExecutor that delegates to QueryExecutor. Each task statement passes through the
        // same statement-end autocommit chokepoint as top-level execute(): the scheduler thread has
        // its own thread-local implicit transaction, and without the commit here a task's DML stayed
        // uncommitted forever — invisible to every other thread.
        final TaskExecutor taskExecutor = new TaskExecutor() {
            @Override
            public int execute(final String sql) {
                // Serialize with direct-connection statements (same monitor): the scheduler thread
                // must not interleave with an interactive statement inside the engine's procedural
                // state.
                synchronized (DatabaseEngine.this) {
                    persistenceManager.noteStatementExecuted();
                    final List<ResultSet> results = queryExecutor.execute(sql);
                    transactionManager.autocommitStatementEnd();
                    // DML statements report their affected-row count as a Snowflake-style result set.
                    return (int) new ExecutionResult(true, results, null).getRowsAffected();
                }
            }

            @Override
            public List<ResultSet> executeQuery(final String sql) {
                synchronized (DatabaseEngine.this) {
                    persistenceManager.noteStatementExecuted();
                    final List<ResultSet> results = queryExecutor.execute(sql);
                    transactionManager.autocommitStatementEnd();
                    return results;
                }
            }

            @Override
            public QueryHistory lastRecordedQuery() {
                return queryExecutor.getQueryHistoryTracker().getLastQuery();
            }

            @Override
            public String currentUser() {
                return sessionContext.getDisplayUser();
            }

            @Override
            public List<ResultSet> evaluateCondition(final String sql) {
                synchronized (DatabaseEngine.this) {
                    final List<ResultSet> results = queryExecutor.executeUnrecorded(sql);
                    transactionManager.autocommitStatementEnd();
                    return results;
                }
            }

            @Override
            public void runInOwnSession(final Runnable run) {
                runInOwnSession(run, null);
            }

            @Override
            public void runInOwnSession(final Runnable run, final Map<String, String> parameters) {
                ownSessionDepth.set(Integer.valueOf(ownSessionDepth.get().intValue() + 1));
                try {
                    if (parameters == null) {
                        queryExecutor.runInOwnSession(run);
                    } else {
                        queryExecutor.runInOwnSession(run, parameters);
                    }
                } finally {
                    ownSessionDepth.set(Integer.valueOf(ownSessionDepth.get().intValue() - 1));
                    if (ownSessionDepth.get().intValue() == 0) {
                        runCompleted();
                    }
                }
            }
        };

        this.taskScheduler = new TaskScheduler(catalog, taskExecutor);
        this.proceduralExecutor = new ProceduralExecutor();

        // Wire up dependencies
        queryExecutor.setStreamManager(streamManager);
        queryExecutor.setOwnSessionStatementSink(new OwnSessionStatementSink() {
            @Override
            public boolean logApart(final String statementText, final ApartRecording recorded) {
                return logRunApart(statementText, recorded);
            }
        });
        transactionManager.setStreamManager(streamManager);
        queryExecutor.setTaskScheduler(taskScheduler);
        functionRegistry.setTaskScheduler(taskScheduler);
        functionRegistry.setTransactionManager(transactionManager);
        functionRegistry.setUserAggregates(new UserAggregates(catalog, queryExecutor));

        initializeSystemObjects();
        // The objects the engine makes for itself are all created at its start.
        BuiltInObjects.stamp(catalog, started);
        queryExecutor.prepareStageRoot();
        initializePersistence();

        // Durability: open the write-ahead log, wire the commit-time sink, and replay any committed work
        // (last checkpoint snapshot + the transactions logged after it) from a prior run.
        this.wal = initWal();
        if (wal != null) {
            wireWalSink();
            checkpointSeq = scanMaxCheckpointSeq();
            replayWal();
            if (!walReplayFailures.isEmpty() && config.isWalReplayStrict()) {
                // Nothing has started yet but the log, which recovery only read: closing it leaves the log
                // exactly as it was, so a later start (strict or not) finds the same records.
                wal.close();
                throw new IllegalStateException("Write-ahead log recovery could not re-apply "
                    + walReplayFailures.size() + " logged transaction(s) and "
                    + EngineConfig.PROP_DURABILITY_WAL_REPLAY_STRICT + " is true; the first: "
                    + walReplayFailures.get(0));
            }
        }
        // A temporary object ends with the session that created it, and none of the sessions whose state a snapshot
        // restored or the log replayed exists any more: whatever they created TEMPORARY is gone.
        TemporaryObjects.drop(catalog, storageEngine, null);

        // After every restore path, so an existing database survives untouched and the
        // exists-check sees the restored world.
        applyConfiguredDefaults();

        // Last, so the arming scan sees every task both restore paths brought back.
        if (config.isTaskSchedulerAutoStart()) {
            taskScheduler.start();
            logger.info("Task scheduler auto-started ({}=true)", EngineConfig.PROP_TASKS_AUTOSTART);
        }
        logger.info("Frostlake SQL Engine initialized successfully");
    }

    private WriteAheadLog initWal() {
        if (!config.isWalEnabled()) {
            return null;
        }
        try {
            return new WriteAheadLog(Path.of(config.getWalFile()), started);
        } catch (final Exception e) {
            logger.error("Failed to open write-ahead log at {}", config.getWalFile(), e);
            return null;
        }
    }

    /** Hook the transaction manager so every successful commit appends that transaction's statements to the
     *  WAL and fsyncs (incrementing the since-checkpoint counter for interval-based auto-checkpointing). */
    private void wireWalSink() {
        transactionManager.setWalSink(new WalSink() {
            @Override
            public void appendTransaction(final List<String> statements,
                                          final List<Instant> instants,
                                          final List<WalStatementContext> contexts) {
                appendTransactionToWal(statements, instants, contexts);
            }
        });
    }

    private void appendTransactionToWal(final List<String> statements,
                                        final List<Instant> instants,
                                        final List<WalStatementContext> contexts) {
        try {
            synchronized (walAppends) {
                // Each statement keeps how many records came between it and this one, which places it on replay.
                final List<WalStatementContext> placed = new ArrayList<WalStatementContext>();
                if (contexts != null) {
                    for (final WalStatementContext context : contexts) {
                        placed.add(context == null ? null : context.loggedAfter(walAppends.get()));
                    }
                }
                wal.appendTransaction(statements, instants, placed);
                walAppends.incrementAndGet();
            }
            // A statement the log holds is one a restart's replay runs again, which a later statement's point may be
            // mapped through (see QueryExecutor.statementSequence).
            if (contexts != null) {
                for (final WalStatementContext context : contexts) {
                    final QueryHistory logged = context == null || context.getQueryId() == null ? null
                        : queryExecutor.getQueryHistoryTracker().getQueryById(context.getQueryId());
                    if (logged != null) {
                        logged.markLogged(context.getQueryId());
                    }
                }
            }
            txnsSinceCheckpoint++;
        } catch (final Exception e) {
            throw new RuntimeException("WAL append failed: " + e.getMessage(), e);
        }
    }

    /**
     * Recover state from the write-ahead log on startup: load the snapshot of the last checkpoint marker (if
     * any), then replay the committed transactions logged after it. Records before the last checkpoint are
     * already folded into that snapshot, so they are skipped — no double-apply.
     */
    private void replayWal() {
        final boolean securityWasEnabled = sessionContext.isSecurityEnabled();
        // A replayed USE ROLE or USE SECONDARY ROLES changes the context itself, not the replay's own settings.
        final String roleBefore = sessionContext.getCurrentRole();
        final Set<String> activeRolesBefore = sessionContext.getActiveRoles();
        // So does each logged session's user, database and schema, which the replay puts back statement by statement.
        final String userBefore = sessionContext.getCurrentUser();
        final String[] scopeBefore = catalog.currentSessionScope();
        try {
            final List<WalRecord> records = wal.readAll();
            int lastCheckpoint = -1;
            for (int i = 0; i < records.size(); i++) {
                if (records.get(i).getType() == WalRecordType.CHECKPOINT) {
                    lastCheckpoint = i;
                }
            }
            if (lastCheckpoint < 0) {
                // No checkpoint keeps the built-in objects' creation: they were created when the engine that began
                // the log first started, which the log keeps.
                BuiltInObjects.stamp(catalog, wal.firstStart());
            }
            if (records.isEmpty()) {
                return;
            }
            replaying = true;
            // The logged statements were authorized when they first ran; replaying them re-applies what was
            // committed, under whatever role and session happen to be current now.
            sessionContext.setSecurityEnabled(false);

            String fromCheckpoint = null;
            int start = 0;
            if (lastCheckpoint >= 0) {
                fromCheckpoint = records.get(lastCheckpoint).getCheckpointRef();
                restoreCheckpoint(fromCheckpoint);
                start = lastCheckpoint + 1;
            }

            // Each session of the log replays as a session of its own, so a SET, an ALTER SESSION, a USE or a USE ROLE
            // one session made is in force for that session's later statements and for no other session's; a
            // record from a log before v4 names no session and replays in the recovery's own. The sessions end
            // with the replay: the statements a former engine ran are not the new one's, so the session it starts
            // with holds none of their query IDs, history rows, variables or parameters.
            final int from = start;
            final int[] replayed = {0};
            final List<Long> replaySessions = new ArrayList<Long>();
            try {
                queryExecutor.runInOwnSession(new Runnable() {
                    @Override
                    public void run() {
                        final SessionSettings own = sessionContext.getBoundSettings();
                        final Map<String, SessionSettings> sessions = new HashMap<String, SessionSettings>();
                        replaySessions.add(Long.valueOf(sessionContext.getSessionNumber()));
                        try {
                            final List<WalRecord> transactions = new ArrayList<WalRecord>();
                            final List<Integer> logIndexes = new ArrayList<Integer>();
                            for (int i = from; i < records.size(); i++) {
                                if (records.get(i).getType() == WalRecordType.TRANSACTION) {
                                    transactions.add(records.get(i));
                                    logIndexes.add(Integer.valueOf(i));
                                }
                            }
                            replayed[0] = replayInPlace(transactions, logIndexes, own, sessions);
                        } finally {
                            for (final SessionSettings session : sessions.values()) {
                                replaySessions.add(Long.valueOf(session.getSessionNumber()));
                            }
                        }
                    }
                });
            } finally {
                for (final Long session : replaySessions) {
                    queryExecutor.getQueryHistoryTracker().forgetSession(session.longValue());
                }
            }
            if (!walReplayFailures.isEmpty()) {
                logger.error("Write-ahead log recovery rolled back {} of {} transaction(s) it could not re-apply;"
                    + " DatabaseEngine.getWalReplayFailures() lists them", walReplayFailures.size(), replayed[0]);
            }

            if (fromCheckpoint != null) {
                logger.info("Recovered from WAL checkpoint {} plus {} subsequent transaction(s)",
                    fromCheckpoint, replayed[0]);
            } else {
                logger.info("Recovered {} transaction(s) from the write-ahead log", replayed[0]);
            }
        } catch (final Exception e) {
            logger.error("Write-ahead log replay failed", e);
            walReplayFailures.add(new WalReplayFailure(-1, null, String.valueOf(e.getMessage())));
        } finally {
            if (replaying) {
                sessionContext.setSecurityEnabled(securityWasEnabled);
                sessionContext.setCurrentUser(userBefore);
                catalog.restoreSessionScope(scopeBefore);
                sessionContext.setCurrentRole(roleBefore);
                sessionContext.clearActiveRoles();
                for (final String role : activeRolesBefore) {
                    sessionContext.addActiveRole(role);
                }
            }
            replaying = false;
        }
    }

    /**
     * Replays the logged transactions, each statement in the place it first ran (see {@link WalReplaySchedule}): a
     * transaction whose statements ran while other sessions committed has them run between those sessions' records,
     * inside its transaction, which commits when the replay reaches its own record. A transaction the replay cannot
     * re-apply is rolled back and listed among the replay's failures.
     *
     * @return how many transaction records were replayed
     */
    private int replayInPlace(final List<WalRecord> transactions, final List<Integer> logIndexes,
                              final SessionSettings own, final Map<String, SessionSettings> sessions) {
        final WalReplaySchedule schedule = WalReplaySchedule.of(transactions);
        final Map<String, Long> open = new HashMap<String, Long>();
        final Set<Integer> failed = new HashSet<Integer>();
        int replayed = 0;
        for (int k = 0; k < transactions.size(); k++) {
            for (final int[] early : schedule.runBefore(k)) {
                if (!failed.contains(Integer.valueOf(early[0]))) {
                    replayInTransaction(transactions.get(early[0]), early[0], early[1],
                        logIndexes.get(early[0]).intValue(), own, sessions, open, failed);
                }
            }
            final WalRecord rec = transactions.get(k);
            replayed++;
            if (!schedule.isInterleaved(k)) {
                replayTransaction(rec.getStatements(), rec, logIndexes.get(k).intValue(), own, sessions);
                continue;
            }
            for (int j = 0; j < rec.getStatements().size(); j++) {
                if (!schedule.runsEarly(k, j) && !failed.contains(Integer.valueOf(k))) {
                    replayInTransaction(rec, k, j, logIndexes.get(k).intValue(), own, sessions, open, failed);
                }
            }
            if (!failed.contains(Integer.valueOf(k))) {
                commitReplayed(rec, k, logIndexes.get(k).intValue(), own, sessions, open, failed);
            }
        }
        // A transaction the log holds no commit for cannot be open here; whatever is, the replay does not keep.
        for (final Long left : open.values()) {
            transactionManager.setCurrentTransaction(left);
            if (transactionManager.hasActiveTransaction()) {
                transactionManager.rollback();
            }
            transactionManager.setCurrentTransaction(null);
        }
        return replayed;
    }

    /** The session a logged statement ran in, or the empty name for one from a log that names none. */
    private static String replaySessionOf(final WalStatementContext context) {
        return context == null || context.getSession() == null ? "" : context.getSession();
    }

    /**
     * Replays one statement of an interleaved transaction in its session's open transaction, beginning it when the
     * statement is the transaction's first. A statement that fails rolls its transaction back.
     */
    private void replayInTransaction(final WalRecord rec, final int position, final int index, final int logIndex,
                                     final SessionSettings own, final Map<String, SessionSettings> sessions,
                                     final Map<String, Long> open, final Set<Integer> failed) {
        final WalStatementContext context = rec.getStatementContext(index);
        final String session = replaySessionOf(context);
        String running = rec.getStatements().get(index);
        StatementClock.setReplay(true);
        try {
            enterReplayContext(context, own, sessions);
            transactionManager.setCurrentTransaction(open.get(session));
            if (!transactionManager.hasActiveTransaction()) {
                final String statement = running;
                running = "BEGIN";
                execute(running);
                open.put(session, transactionManager.getCurrentTransactionId());
                running = statement;
            }
            queryExecutor.setNextStatementInstant(rec.getStatementInstant(index));
            replayStatement(running, context);
            open.put(session, transactionManager.getCurrentTransactionId());
        } catch (final Exception e) {
            replayedTransactionFailed(position, logIndex, running, e, session, open, failed);
        } finally {
            transactionManager.setCurrentTransaction(null);
            StatementClock.setReplay(false);
        }
    }

    /** Commits an interleaved transaction when the replay reaches its own record. */
    private void commitReplayed(final WalRecord rec, final int position, final int logIndex,
                                final SessionSettings own, final Map<String, SessionSettings> sessions,
                                final Map<String, Long> open, final Set<Integer> failed) {
        final WalStatementContext context = rec.getStatementContext(0);
        final String session = replaySessionOf(context);
        try {
            enterReplayContext(context, own, sessions);
            transactionManager.setCurrentTransaction(open.get(session));
            execute("COMMIT");
            open.remove(session);
        } catch (final Exception e) {
            replayedTransactionFailed(position, logIndex, "COMMIT", e, session, open, failed);
        } finally {
            transactionManager.setCurrentTransaction(null);
        }
    }

    /** Lists an interleaved transaction the replay could not re-apply and rolls it back. */
    private void replayedTransactionFailed(final int position, final int logIndex, final String running,
                                           final Exception cause, final String session,
                                           final Map<String, Long> open, final Set<Integer> failed) {
        logger.error("WAL replay failed for a transaction; rolling it back", cause);
        walReplayFailures.add(new WalReplayFailure(logIndex, running, String.valueOf(cause.getMessage())));
        failed.add(Integer.valueOf(position));
        transactionManager.setCurrentTransaction(open.remove(session));
        if (transactionManager.hasActiveTransaction()) {
            try {
                execute("ROLLBACK");
            } catch (final Exception ignored) {
                // best-effort — a failed replay must not abort recovery of the remaining records
            }
        }
    }

    /**
     * Puts a logged statement's context back before it replays: its session's own settings — variables, parameters,
     * roles, query IDs — its user, roles, database and schema, and the variables and parameters the session held when
     * the statement first ran, which a session set before a checkpoint, or was given when it opened, has nowhere else.
     * A statement from a log before v4 carries none and replays in the recovery's own session.
     */
    private void enterReplayContext(final WalStatementContext context, final SessionSettings own,
                                    final Map<String, SessionSettings> sessions) {
        if (context == null) {
            sessionContext.bindSettings(own);
            queryExecutor.getResultCache().beginSessionScope(String.valueOf(own.getSessionNumber()));
            return;
        }
        SessionSettings settings = sessions.get(context.getSession());
        if (settings == null) {
            settings = new SessionSettings();
            sessions.put(context.getSession(), settings);
        }
        sessionContext.bindSettings(settings);
        queryExecutor.getResultCache().beginSessionScope(String.valueOf(settings.getSessionNumber()));
        sessionContext.setCurrentUser(context.getUser());
        sessionContext.clearActiveRoles();
        sessionContext.setCurrentRole(context.getRole());
        for (final String role : context.getActiveRoles()) {
            sessionContext.addActiveRole(role);
        }
        catalog.beginSessionScope(context.getDatabase(), context.getSchema());
        if (context.carriesSessionState()) {
            sessionContext.restoreSessionState(context.getParameters(), context.getVariables(),
                context.getUnkeptParameters(), context.getUnkeptVariables());
        }
    }

    /**
     * Replays one logged statement, handing it the values it first drew from counters, and lets the query ID it first
     * answered with name what it answers now (see {@code QueryResultCache.alias}).
     */
    private void replayStatement(final String sql, final WalStatementContext context) {
        StatementDraws.beginReplay(context == null ? Collections.<CounterDraw>emptyList() : context.getDraws());
        StatementResults.beginReplay(context == null ? Collections.<RecordedResult>emptyList() : context.getResults());
        StatementAnswers.beginReplay(context == null
            ? Collections.<String, List<String>>emptyMap() : context.getAnswers());
        // Work the statement ran in sessions of their own that the log keeps in records of their own has replayed in
        // their places already.
        queryExecutor.replayRunsLoggedApart(context != null && context.isRunsLoggedApart());
        // The log holds each transaction's statements together, so a replayed one commits when its record says:
        // an ALTER SESSION SET AUTOCOMMIT it replays reaches this statement only.
        transactionManager.beginSessionAutoCommit(true);
        try {
            execute(sql);
        } finally {
            transactionManager.clearSessionAutoCommit();
            queryExecutor.replayRunsLoggedApart(false);
            StatementDraws.endReplay();
            StatementResults.endReplay();
            StatementAnswers.endReplay();
        }
        if (context != null && context.getQueryId() != null) {
            final String replayedId = queryExecutor.getResultCache().getLastQueryId();
            queryExecutor.getResultCache().alias(context.getQueryId(), replayedId);
            // A later statement, replayed or run after the restart, naming the first run's query ID as a time-travel
            // point names this replay of it, placed by the order the snapshots were taken in.
            final QueryHistory replayed = queryExecutor.getQueryHistoryTracker().getQueryById(replayedId);
            queryExecutor.getQueryHistoryTracker().anchor(context.getQueryId(), replayed);
            // The log still holds the statement under its first ID, which a point read after the restart maps
            // through on the next one.
            if (replayed != null) {
                replayed.markLogged(context.getQueryId());
            }
        }
    }

    /** Re-execute one committed transaction's statements. A single-statement record replays as an autocommit
     *  statement; a multi-statement record replays inside a BEGIN…COMMIT, exactly as it was committed. */
    /**
     * Re-execute one logged transaction. Each statement is given back the instant it originally ran at,
     * so a replayed {@code CURRENT_TIMESTAMP} resolves to the value the row was written with instead of
     * to the recovery time. A record from a pre-v3 log carries no instant and replays at the wall clock,
     * which is what it did before.
     */
    private void replayTransaction(final List<String> statements, final WalRecord record, final int index,
                                   final SessionSettings own, final Map<String, SessionSettings> sessions) {
        // While replaying, a procedural block's inner statements must NOT advance the clock —
        // the log recorded one instant per outer statement, and replay keeps it.
        StatementClock.setReplay(true);
        String running = null;
        try {
            if (statements.size() == 1) {
                enterReplayContext(record.getStatementContext(0), own, sessions);
                queryExecutor.setNextStatementInstant(record.getStatementInstant(0));
                running = statements.get(0);
                replayStatement(running, record.getStatementContext(0));
                return;
            }
            enterReplayContext(record.getStatementContext(0), own, sessions);
            running = "BEGIN";
            execute(running);
            for (int i = 0; i < statements.size(); i++) {
                enterReplayContext(record.getStatementContext(i), own, sessions);
                queryExecutor.setNextStatementInstant(record.getStatementInstant(i));
                running = statements.get(i);
                replayStatement(running, record.getStatementContext(i));
            }
            running = "COMMIT";
            execute(running);
        } catch (final Exception e) {
            logger.error("WAL replay failed for a transaction; rolling it back", e);
            walReplayFailures.add(new WalReplayFailure(index, running, String.valueOf(e.getMessage())));
            if (transactionManager.hasActiveTransaction()) {
                try {
                    execute("ROLLBACK");
                } catch (final Exception ignored) {
                    // best-effort — a failed replay must not abort recovery of the remaining records
                }
            }
        } finally {
            StatementClock.setReplay(false);
        }
    }

    private void restoreCheckpoint(final String checkpointRef) {
        try {
            final Path dir = checkpointDir().resolve(checkpointRef);
            persistenceManager.restoreFrom(dir, catalog, storageEngine);
            logger.info("Restored WAL checkpoint snapshot {}", checkpointRef);
        } catch (final Exception e) {
            logger.error("Failed to restore WAL checkpoint {}", checkpointRef, e);
        }
    }

    /**
     * Record a committed mutating statement for the write-ahead log. With an active transaction the statement
     * is buffered into it and logged atomically when that transaction commits (covers autocommit DML and
     * explicit BEGIN…COMMIT blocks); without one (e.g. a DDL/USE that starts no transaction) it is appended
     * immediately as a one-statement record. Reads and transaction-control statements are not logged.
     */
    /** This engine's run, telling its sessions' log records apart from another run's in one log. */
    private final String lifetime = UUID.randomUUID().toString();

    /** What the calling thread's outermost statement started under (see {@link #beginWalStatement}). */
    private final ThreadLocal<WalStatementContext> walStatementStart = new ThreadLocal<WalStatementContext>();

    /** How many statements the calling thread is running through {@link #execute}, one inside the other. */
    private final ThreadLocal<Integer> statementDepth = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return Integer.valueOf(0);
        }
    };

    /** How many task, alert or pipe runs the calling thread has open, a graph's child inside its parent. */
    private final ThreadLocal<Integer> ownSessionDepth = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return Integer.valueOf(0);
        }
    };

    /** Whether the calling thread's statement started a run whose work is not in the write-ahead log. */
    private final ThreadLocal<Boolean> runCheckpointDue = new ThreadLocal<Boolean>();

    /**
     * Whether a statement the calling thread's outermost statement ran committed work in a session of its own that the
     * write-ahead log keeps in a record of its own (see {@link #logRunApart}).
     */
    private final ThreadLocal<Boolean> runsLoggedApart = new ThreadLocal<Boolean>();

    /**
     * A task's, an alert's or a pipe's run is over. The run executed its statements below the layer that writes
     * the write-ahead log, in a session of its own, so the log holds neither what it changed nor its row of run
     * history; a checkpoint keeps both. A run the schedule started is checkpointed here, on the scheduler's
     * thread; one a statement started — EXECUTE TASK, EXECUTE ALERT — when that statement ends, so a recovery
     * starts after the run and does not run its body a second time.
     */
    private void runCompleted() {
        if (wal == null || replaying) {
            return;
        }
        if (statementDepth.get().intValue() > 0) {
            runCheckpointDue.set(Boolean.TRUE);
            return;
        }
        checkpoint();
    }

    /**
     * A sequence never hands a value out twice, and a value it handed out stays drawn whatever becomes of the
     * statement that drew it: a ROLLBACK and a statement's failure undo none (documented). A draw no record of the log
     * carries for certain owes a checkpoint, which keeps the sequences as they stand: one of a statement that failed,
     * one of a statement the log leaves out though it drew - a read of a view or a function that draws - and one of a
     * statement kept inside a transaction that may yet roll back.
     *
     * @param drawn  what the statement drew
     * @param logged whether the log keeps the statement
     */
    private void oweCheckpointForUnloggedDraws(final List<CounterDraw> drawn, final boolean logged) {
        if (wal == null || replaying || logged && !transactionManager.isLastingTransaction()) {
            return;
        }
        for (final CounterDraw draw : drawn) {
            if (draw.getCounter().startsWith(Sequence.COUNTER_PREFIX)) {
                runCheckpointDue.set(Boolean.TRUE);
                return;
            }
        }
    }

    /**
     * The checkpoint a statement owes for a run it started. While its session holds a transaction open the debt waits
     * for the statement that ends the transaction: the transaction's own writes are not the checkpoint's yet, and a
     * checkpoint skipped would leave the run's statement in the log for a restart to run again.
     */
    private void checkpointAfterRun() {
        if (!Boolean.TRUE.equals(runCheckpointDue.get())) {
            return;
        }
        if (wal != null && !replaying && transactionManager.isLastingTransaction()) {
            return;
        }
        runCheckpointDue.remove();
        if (wal != null && !replaying) {
            checkpoint();
        }
    }

    /**
     * Notes what an outermost statement starts under — its session, user, roles, database and schema, variables and
     * parameters — and starts recording what it draws from counters and reads by RESULT_SCAN, for its write-ahead log
     * record (see {@link WalStatementContext}).
     */
    private void beginWalStatement() {
        if (wal == null || replaying || statementDepth.get().intValue() != 1) {
            return;
        }
        walStatementStart.set(new WalStatementContext(lifetime + ":" + sessionContext.getSessionNumber(),
            sessionContext.getCurrentUser(), sessionContext.getCurrentRole(),
            new ArrayList<String>(sessionContext.getActiveRoles()), catalog.getCurrentDatabase(),
            catalog.getCurrentSchema(), null, Collections.<CounterDraw>emptyList(),
            sessionContext.getAllSessionVariables(), sessionContext.getAllSessionParameters(),
            Collections.<String>emptySet(), Collections.<String>emptySet(), Collections.<RecordedResult>emptyList(),
            -1L, 0));
        StatementDraws.beginRecording();
        StatementResults.beginRecording();
        StatementAnswers.beginRecording();
        runsLoggedApart.remove();
    }

    /** Lets go of what {@link #beginWalStatement} noted, once the outermost statement is over. */
    private void endWalStatement() {
        walStatementStart.remove();
        oweCheckpointForUnloggedDraws(StatementDraws.endRecording(), false);
        StatementResults.endRecording(false);
        StatementAnswers.endRecording();
        runsLoggedApart.remove();
    }

    /**
     * A statement a block, a procedure or a procedure's handler ran committed work in a session of its own — EXECUTE
     * TASK, EXECUTE ALERT, ALTER PIPE … REFRESH — which no ROLLBACK of the transaction running it undoes. It is logged
     * at once, in a record of its own with what it drew, read and answered, placed where it ran; the statement that ran
     * it stays in its transaction and replays without running that work again.
     *
     * @param statementText the statement, as it ran
     * @param recorded      what it drew, read and answered
     * @return whether it was logged
     */
    private boolean logRunApart(final String statementText, final ApartRecording recorded) {
        if (wal == null || replaying || walStatementStart.get() == null || !isLoggableStatement(statementText)) {
            return false;
        }
        final WalStatementContext context = new WalStatementContext(
            lifetime + ":" + sessionContext.getSessionNumber(), sessionContext.getCurrentUser(),
            sessionContext.getCurrentRole(), new ArrayList<String>(sessionContext.getActiveRoles()),
            catalog.getCurrentDatabase(), catalog.getCurrentSchema(), null, recorded.getDraws(),
            sessionContext.getAllSessionVariables(), sessionContext.getAllSessionParameters(),
            Collections.<String>emptySet(), Collections.<String>emptySet(), recorded.getResults(),
            recorded.getAnswers(), false, walAppends.get(), 0);
        appendTransactionToWal(Collections.singletonList(statementText),
            Collections.singletonList(recorded.getStarted()), Collections.singletonList(context));
        runsLoggedApart.set(Boolean.TRUE);
        return true;
    }

    private void recordStatementForWal(final String sql) {
        if (statementDepth.get().intValue() > 1) {
            // A statement a procedure's handler issues runs inside the CALL, whose replay runs the handler and
            // issues it again: logging it too applied it twice. Work it commits in a session of its own is logged
            // apart as it runs (see logRunApart); one that could not be stays with the CALL.
            return;
        }
        // A statement that ran a task, an alert or a pipe's load (EXECUTE TASK, EXECUTE ALERT, ALTER PIPE …
        // REFRESH) committed that work in a session of its own, so it is logged at once rather than inside the
        // caller's transaction: a ROLLBACK there leaves the work in place, and must leave its record too.
        final boolean ranOwnSession = queryExecutor.takeRanInOwnSession();
        final WalStatementContext started = statementDepth.get().intValue() == 1 ? walStatementStart.get() : null;
        final boolean logged = wal != null && !replaying && isLoggableStatement(sql);
        final List<CounterDraw> drawn =
            started != null ? StatementDraws.endRecording() : Collections.<CounterDraw>emptyList();
        // The rows the statement read by RESULT_SCAN go with its record, so the replay reads them again whatever its
        // own results are.
        final List<RecordedResult> read =
            started != null ? StatementResults.endRecording(logged) : Collections.<RecordedResult>emptyList();
        // So do the values its identities answered, which a replay in a session of its own answers again.
        final Map<String, List<String>> answered = started != null
            ? StatementAnswers.endRecording() : Collections.<String, List<String>>emptyMap();
        final boolean apart = Boolean.TRUE.equals(runsLoggedApart.get());
        runsLoggedApart.remove();
        oweCheckpointForUnloggedDraws(drawn, logged);
        if (started != null) {
            walStatementStart.remove();
        }
        if (!logged) {
            return;
        }
        final List<String> statements = new ArrayList<String>();
        final List<Instant> instants = new ArrayList<Instant>();
        final List<WalStatementContext> contexts = new ArrayList<WalStatementContext>();
        statements.add(sql);
        instants.add(queryExecutor.currentStatementInstant());
        // The statement's own query ID goes with it, which a later statement may name as a time-travel point.
        contexts.add(started == null ? null : started.finished(queryExecutor.getResultCache().getLastQueryId(), drawn,
            read, answered, apart, walAppends.get()));
        if (transactionManager.hasActiveTransaction() && !ranOwnSession) {
            for (int i = 0; i < statements.size(); i++) {
                transactionManager.getCurrentTransaction()
                    .logStatementForWal(statements.get(i), instants.get(i), contexts.get(i));
            }
        } else {
            appendTransactionToWal(statements, instants, contexts);
        }
    }


    /**
     * Whether this statement belongs in the write-ahead log. The answer comes from the parse tree —
     * see {@link WalStatementKinds}, which owns the rule — because the leading keyword does not
     * identify the family: {@code BEGIN} opens a transaction AND a procedural block, so reading the
     * text dropped the DML inside an anonymous {@code BEGIN … END} block and lost those rows on the
     * next restart.
     */
    private boolean isLoggableStatement(final String sql) {
        return queryExecutor.isDurableStatement(sql) || WalStatementKinds.writesDurably(sql);
    }

    /** The loggability decision, exposed so tests can assert it family by family. */
    public boolean isDurableStatementForTesting(final String sql) {
        return isLoggableStatement(sql);
    }

    /**
     * Write a WAL checkpoint: snapshot the full state durably, append a checkpoint marker (the atomic pivot —
     * recovery keys off the LAST marker, so a crash before it leaves an ignored orphan snapshot), then
     * truncate the log to the marker and drop superseded snapshots. No-op while a transaction is open, an
     * explicit one or the implicit one AUTOCOMMIT off keeps (its statements aren't committed yet). Safe to call
     * explicitly; also driven by {@code durability.checkpointInterval}.
     */
    public synchronized void checkpoint() {
        if (wal == null) {
            return;
        }
        if (transactionManager.isLastingTransaction()) {
            logger.warn("Skipping WAL checkpoint while a transaction is open");
            return;
        }
        try {
            checkpointSeq++;
            final String ref = "cp-" + checkpointSeq;
            final Path dir = checkpointDir().resolve(ref);
            persistenceManager.checkpointTo(dir, catalog, storageEngine);   // 1. durable snapshot
            wal.appendCheckpoint(ref);                                       // 2. marker + fsync = pivot
            wal.truncateToCheckpoint(ref);                                   // 3. bound the log
            deleteSupersededCheckpoints(ref);
            txnsSinceCheckpoint = 0;
            logger.info("WAL checkpoint {} written; log truncated", ref);
        } catch (final Exception e) {
            logger.error("WAL checkpoint failed", e);
        }
    }

    /** Auto-checkpoint once enough transactions have accumulated since the last checkpoint (off when the
     *  configured interval is 0). Called after each statement, outside any open explicit transaction. */
    private void maybeAutoCheckpoint() {
        if (wal == null || replaying) {
            return;
        }
        final int interval = config.getWalCheckpointInterval();
        if (interval > 0 && txnsSinceCheckpoint >= interval && !transactionManager.isLastingTransaction()) {
            checkpoint();
        }
    }

    /** Directory holding checkpoint snapshots, beside the WAL file. */
    private Path checkpointDir() {
        final Path walPath = Path.of(config.getWalFile());
        final Path parent = walPath.getParent();
        final Path base = (parent != null) ? parent : Path.of(".");
        return base.resolve("wal-checkpoints");
    }

    /** Highest existing {@code cp-<n>} sequence under the checkpoint directory, so a fresh run names new
     *  checkpoints without colliding with snapshots left by a prior run. */
    private long scanMaxCheckpointSeq() {
        final Path dir = checkpointDir();
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        long max = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "cp-*")) {
            for (final Path p : stream) {
                final String name = p.getFileName().toString();
                try {
                    final long n = Long.parseLong(name.substring("cp-".length()));
                    if (n > max) {
                        max = n;
                    }
                } catch (final NumberFormatException ignored) {
                    // not a cp-<n> directory — skip
                }
            }
        } catch (final IOException e) {
            logger.warn("Could not scan WAL checkpoint directory {}", dir, e);
        }
        return max;
    }

    private void deleteSupersededCheckpoints(final String keepRef) {
        final Path dir = checkpointDir();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "cp-*")) {
            for (final Path p : stream) {
                if (!p.getFileName().toString().equals(keepRef)) {
                    deleteRecursively(p);
                }
            }
        } catch (final IOException e) {
            logger.warn("Could not clean up superseded WAL checkpoints in {}", dir, e);
        }
    }

    private void deleteRecursively(final Path p) {
        try {
            if (Files.isDirectory(p)) {
                try (DirectoryStream<Path> children = Files.newDirectoryStream(p)) {
                    for (final Path child : children) {
                        deleteRecursively(child);
                    }
                }
            }
            Files.deleteIfExists(p);
        } catch (final IOException e) {
            logger.warn("Could not delete {}", p, e);
        }
    }

    private void initializeSystemObjects() {
        // SNOWFLAKE database is already created by Catalog constructor
        // with PUBLIC and INFORMATION_SCHEMA schemas
        // Just ensure we're using it
        catalog.useDatabase("SNOWFLAKE");
        catalog.useSchema("PUBLIC");

        // Create the default user from config and set it as the current session user
        // Create the default user and set it as the current session user.
        // Security enforcement continues to use the SYSTEM bypass for existing behaviour;
        // the default user is visible via CURRENT_USER() and in query history.
        final String defaultUser = config.getDefaultUser();
        try {
            catalog.getUser(defaultUser);
        } catch (final RuntimeException e) {
            catalog.createUser(defaultUser);
        }
        catalog.grantRoleToUser("ORGADMIN", defaultUser);
        catalog.grantRoleToUser("ACCOUNTADMIN", defaultUser);
        sessionContext.setDisplayUser(defaultUser);
    }

    /**
     * Point the fresh session at the configured default database/schema ({@code database.default}
     * / {@code schema.default}), creating them on first boot. The built-in read-only SNOWFLAKE
     * database stays the default when the properties are unset — which is exactly why the
     * properties exist: a writable landing database for servers and containers.
     */
    private void applyConfiguredDefaults() {
        final String defaultDatabase = config.getDefaultDatabase().toUpperCase(Locale.ROOT);
        final String defaultSchema = config.getDefaultSchema().toUpperCase(Locale.ROOT);
        // Created at the engine's first start, as the built-in objects are: a restart that makes them again from no
        // checkpoint reports the creation they had.
        final Instant firstStart = wal != null ? wal.firstStart() : started;
        try {
            catalog.getDatabase(defaultDatabase);
        } catch (final RuntimeException e) {
            catalog.createDatabase(defaultDatabase);
            BuiltInObjects.stamp(catalog.getDatabase(defaultDatabase), firstStart);
        }
        catalog.useDatabase(defaultDatabase);
        final Database database = catalog.getDatabase(defaultDatabase);
        try {
            database.getSchema(defaultSchema);
        } catch (final RuntimeException e) {
            final Schema schema = new Schema(defaultSchema);
            schema.restoreCreatedTime(firstStart);
            database.addSchema(schema);
        }
        catalog.useSchema(defaultSchema);
    }

    private void initializePersistence() {
        if (!config.isPersistenceEnabled()) {
            return;
        }
        if (config.isWalEnabled()) {
            // Snapshot persistence and WAL recovery cannot both restore at startup: loadCatalog would
            // restore the newest autosave and the WAL replay would then re-apply every transaction since
            // the last WAL checkpoint on top of it (double-apply). With the WAL on, its checkpoint+replay
            // is the authoritative restore path, so snapshot persistence is ignored entirely.
            logger.warn("persistence.enabled is ignored while durability.walEnabled=true — "
                + "WAL recovery (checkpoint + replay) is the authoritative restore path");
            return;
        }

        try {
            persistenceManager.initialize();
            persistenceManager.loadCatalog(catalog, storageEngine);
            persistenceManager.startAutoSave(catalog, storageEngine);
            logger.info("Persistence loaded from: {}", config.getPersistenceDirectory());
        } catch (final Exception e) {
            logger.error("Failed to initialize persistence", e);
            throw new RuntimeException("Failed to initialize persistence", e);
        }
    }

    /**
     * Execute a SQL statement or script
     */
    public ExecutionResult execute(final String sql) {
        // A statement a handler issues from inside a procedure arrives here too: one issued past the depth bodies
        // run to ends its body (see CallDepth). A statement the session sends runs in no body.
        CallDepth.requireMayIssue(transactionManager.scopeDepth());
        sessionContext.setCurrentStatement(sql);
        logger.debug("Executing SQL: {}", sql);

        // Bind this session's JSON_INDENT for the statement's duration. Reading it per statement,
        // rather than pushing it from ALTER SESSION, keeps a width one session set from surviving
        // into whatever runs on this thread next.
        bindJsonIndent();

        // Snowflake: a failed statement inside an explicit transaction rolls back ITSELF but leaves the
        // transaction open. In deferred-apply mode, snapshot the write set so we can restore exactly this
        // statement's changes on failure (statement-level rollback); otherwise fall back to full rollback.
        final WriteSetSavepoint stmtSavepoint =
            (config.isDeferredApply() && transactionManager.isLastingTransaction())
                ? transactionManager.getCurrentTransaction().beginStatementSavepoint() : null;

        // A request of several statements commits, logs and rolls back statement by statement, as the
        // account does; the flag records that its boundaries were reported, so the request as a whole is
        // neither logged again nor rolled back again below.
        final boolean[] statementByStatement = {false};
        final WriteSetSavepoint[] statementSavepoint = {null};
        statementDepth.set(Integer.valueOf(statementDepth.get().intValue() + 1));
        if (statementDepth.get().intValue() == 1) {
            // A debt an earlier statement on this thread left unsettled is not this one's.
            CheckpointDebt.settle();
        }
        final StatementBoundaries displacedBoundaries = queryExecutor.bindStatementBoundaries(new StatementBoundaries() {
            @Override
            public void begin() {
                statementByStatement[0] = true;
                queryExecutor.takeRanInOwnSession();
                beginWalStatement();
                statementSavepoint[0] = config.isDeferredApply() && transactionManager.isLastingTransaction()
                    ? transactionManager.getCurrentTransaction().beginStatementSavepoint() : null;
            }

            @Override
            public void complete(final String statementText) {
                recordStatementForWal(statementText);
                transactionManager.autocommitStatementEnd();
                if (statementSavepoint[0] != null && transactionManager.isLastingTransaction()) {
                    transactionManager.getCurrentTransaction().getWriteSet()
                        .endStatementSavepoint(statementSavepoint[0]);
                }
                statementSavepoint[0] = null;
            }

            @Override
            public void fail(final RuntimeException cause) {
                oweCheckpointForUnloggedDraws(StatementDraws.endRecording(), false);
                rollbackFailedStatement(statementSavepoint[0], cause);
                statementSavepoint[0] = null;
            }
        });

        try {
            // Parse and execute the SQL. Dirty-mark BEFORE executing: an attempt is enough (see
            // noteStatementExecuted), and marking after a success would miss failure side effects.
            persistenceManager.noteStatementExecuted();
            // What an earlier statement on this thread ran in a session of its own is no concern of this one's.
            queryExecutor.takeRanInOwnSession();
            beginWalStatement();
            final List<ResultSet> results = queryExecutor.execute(sql);

            // Record this statement for the WAL before the autocommit commit, so the commit logs it as part
            // of its transaction; an explicit BEGIN…COMMIT buffers each statement until COMMIT.
            if (!statementByStatement[0]) {
                recordStatementForWal(sql);
            }

            // Autocommit commits at statement end — UNLESS an explicit BEGIN is in effect, which suspends
            // autocommit until COMMIT/ROLLBACK (Snowflake semantics).
            transactionManager.autocommitStatementEnd();

            // Bound the WAL by checkpointing once enough transactions accumulate (no-op unless configured).
            maybeAutoCheckpoint();

            // Get the query ID from the result cache
            final String queryId = queryExecutor.getResultCache().getLastQueryId();

            if (stmtSavepoint != null && transactionManager.isLastingTransaction()) {
                transactionManager.getCurrentTransaction().getWriteSet().endStatementSavepoint(stmtSavepoint);
            }

            // The caller's rows are its own: a SELECT that reads a table's columns as they are would otherwise hand
            // out the stored rows themselves, and a change to one would rewrite the table and its history.
            final List<ResultSet> detached = new ArrayList<>(results.size());
            for (final ResultSet result : results) {
                detached.add(result == null ? null : result.detachedCopy());
            }
            return new ExecutionResult(true, detached, null, queryId);

        } catch (final SqlSyntaxException e) {
            // Log syntax errors with the failed query
            logger.error("SQL syntax error in query:\n{}\nError details:\n{}", sql, e.getMessage());
            if (!statementByStatement[0]) {
                rollbackFailedStatement(stmtSavepoint, e);
            } else {
                releaseRequestSavepoint(stmtSavepoint);
            }
            // Re-throw exception so tests and callers can handle it
            throw e;
        } catch (final Exception e) {
            logger.error("Error executing SQL:\n{}\nError: {}", sql, e.getMessage(), e);
            if (!statementByStatement[0]) {
                rollbackFailedStatement(stmtSavepoint, e);
            } else {
                releaseRequestSavepoint(stmtSavepoint);
            }
            oweCheckpointForFailedStatements(sql);
            // Re-throw exception so tests can catch it
            throw e;
        } catch (final StackOverflowError overflow) {
            // Nesting the stack could not hold is the statement's failure like any other, undone and reported as
            // one, where it escaped as an Error past both.
            final RuntimeException failure = new RuntimeException(StatementNesting.REFUSAL);
            logger.error("Error executing SQL:\n{}\nError: {}", sql, failure.getMessage());
            if (!statementByStatement[0]) {
                rollbackFailedStatement(stmtSavepoint, failure);
            } else {
                releaseRequestSavepoint(stmtSavepoint);
            }
            oweCheckpointForFailedStatements(sql);
            throw failure;
        } finally {
            queryExecutor.restoreStatementBoundaries(displacedBoundaries);
            statementDepth.set(Integer.valueOf(statementDepth.get().intValue() - 1));
            if (statementDepth.get().intValue() == 0) {
                endWalStatement();
                oweCheckpointForUnreplayableReads(sql);
                checkpointAfterRun();
                UdfRuntimes.statementEnded();
            }
        }
    }

    /**
     * An outermost CALL, block or EXECUTE IMMEDIATE that failed may leave work its statements committed, or kept in
     * the session's transaction, which no log record carries: the statement is logged only when it succeeds, and the
     * statements it ran never are. Such a failure owes a checkpoint, as a task's run does (see {@link
     * #checkpointAfterRun}), which keeps that work, the sequence values it drew included, across a restart.
     */
    private void oweCheckpointForFailedStatements(final String sql) {
        if (wal == null || replaying || statementDepth.get().intValue() != 1
                || !queryExecutor.runsStatementsOfItsOwn(sql)) {
            return;
        }
        runCheckpointDue.set(Boolean.TRUE);
    }

    /**
     * A logged statement that read what its record cannot read again — a stage's files, a handler function's answers
     * (see {@link CheckpointDebt}) — owes a checkpoint, as a task's run does: a restart then starts after it and does
     * not run it a second time over files a purge or an overwrite changed, or a handler that answers otherwise.
     */
    private void oweCheckpointForUnreplayableReads(final String sql) {
        if (CheckpointDebt.settle() && wal != null && !replaying && isLoggableStatement(sql)) {
            runCheckpointDue.set(Boolean.TRUE);
        }
    }

    /**
     * Lets go of the savepoint of a request whose statements each ended or undid themselves: the statement that
     * failed rolled itself back, and the ones before it stand.
     */
    private void releaseRequestSavepoint(final WriteSetSavepoint stmtSavepoint) {
        if (stmtSavepoint != null && stmtSavepoint.isSuperseded() && transactionManager.isLastingTransaction()) {
            transactionManager.getCurrentTransaction().rollbackToSavepoint(stmtSavepoint);
        }
    }

    /**
     * Undo a failed statement. Snowflake keeps an explicit transaction OPEN after a statement error,
     * rolling back only that statement — in deferred-apply mode, restore the write-set savepoint taken
     * before the statement. Otherwise (implicit/autocommit txn, or legacy immediate-apply) roll back the
     * whole transaction, which for an autocommit statement is exactly that one statement.
     */
    private void rollbackFailedStatement(final WriteSetSavepoint stmtSavepoint, final Exception cause) {
        if (stmtSavepoint != null && transactionManager.isLastingTransaction()) {
            transactionManager.getCurrentTransaction().rollbackToSavepoint(stmtSavepoint);
        } else if (transactionManager.hasActiveTransaction()) {
            try {
                // A statement refused while it compiled never ran, so it ran in no transaction: LAST_TRANSACTION
                // answers what it answered before, where a statement that failed over a value leaves its own
                // rolled-back transaction there (live-verified).
                if (cause instanceof SqlSyntaxException
                        || cause != null && SqlCompilationError.isCompilationError(cause.getMessage())) {
                    transactionManager.rollbackUnrecorded();
                    return;
                }
                transactionManager.rollback();
            } catch (final Exception rollbackEx) {
                logger.error("Error rolling back transaction", rollbackEx);
            }
        }
    }

    /**
     * Execute a query and return a single result set
     */
    public ResultSet executeQuery(final String sql) {
        sessionContext.setCurrentStatement(sql);
        final ExecutionResult result = execute(sql);
        if (!result.isSuccess()) {
            throw new RuntimeException("Query execution failed: " + result.getErrorMessage());
        }
        if (result.getResultSets().isEmpty()) {
            return new ResultSet(new ArrayList<>());
        }
        return result.getResultSets().get(0);
    }

    /**
     * Execute an update statement (INSERT, UPDATE, DELETE)
     */
    public int executeUpdate(final String sql) {
        final ExecutionResult result = execute(sql);
        if (!result.isSuccess()) {
            throw new RuntimeException("Update execution failed: " + result.getErrorMessage());
        }
        return (int) result.getRowsAffected();
    }

    // Transaction Control
    public void beginTransaction() {
        transactionManager.beginTransaction();
    }

    public void commit() {
        transactionManager.commit();
    }

    public void rollback() {
        transactionManager.rollback();
    }

    public void setSavepoint(final String name) {
        transactionManager.setSavepoint(name);
    }

    public void rollbackToSavepoint(final String name) {
        transactionManager.rollbackToSavepoint(name);
    }

    public void releaseSavepoint(final String name) {
        transactionManager.releaseSavepoint(name);
    }

    public void setAutoCommit(final boolean autoCommit) {
        transactionManager.setAutoCommit(autoCommit);
    }

    public boolean isAutoCommit() {
        return transactionManager.isAutoCommit();
    }

    // Database Operations
    public void useDatabase(final String database) {
        catalog.useDatabase(database);
    }

    public void useSchema(final String schema) {
        catalog.useSchema(schema);
    }

    public String getCurrentDatabase() {
        return catalog.getCurrentDatabase();
    }

    public String getCurrentSchema() {
        return catalog.getCurrentSchema();
    }

    // System Information
    public ResultSet showDatabases() {
        return systemViews.queryDatabases();
    }

    public ResultSet showSchemas() {
        return systemViews.querySchemata(catalog.getCurrentDatabase());
    }

    public ResultSet showTables() {
        return systemViews.queryTables(
            catalog.getCurrentDatabase(),
            catalog.getCurrentSchema()
        );
    }

    public ResultSet showColumns(final String tableName) {
        return systemViews.queryColumns(
            catalog.getCurrentDatabase(),
            catalog.getCurrentSchema(),
            tableName
        );
    }

    public ResultSet showViews() {
        return systemViews.queryViews(
            catalog.getCurrentDatabase(),
            catalog.getCurrentSchema()
        );
    }

    public ResultSet showProcedures() {
        return systemViews.queryProcedures(
            catalog.getCurrentDatabase(),
            catalog.getCurrentSchema()
        );
    }

    public ResultSet showFunctions() {
        return systemViews.queryFunctions(
            catalog.getCurrentDatabase(),
            catalog.getCurrentSchema()
        );
    }

    // Component Access
    /**
     * Write this engine's complete state — every database with its schemas, tables and rows, views,
     * sequences (at their current positions), functions, procedures, streams with pending records,
     * tasks, stages with the files of the internal ones, roles and policies — to {@code dir}, from which
     * {@link #restoreStateFrom(Path)} can rebuild an equivalent engine. Holds the engine monitor, so it is
     * mutually exclusive with statements running on direct connections. Works regardless of
     * {@code persistence.enabled}.
     */
    public synchronized void checkpointStateTo(final Path dir) throws IOException {
        persistenceManager.checkpointTo(dir, catalog, storageEngine);
        // The files of its internal stages are part of its state, as the rows of its tables are.
        queryExecutor.copyInternalStagesTo(dir.resolve("stages"));
    }

    /**
     * Rebuild this engine's state from a {@link #checkpointStateTo(Path)} snapshot. Intended for a
     * freshly constructed engine (the template-clone pattern: migrate once, checkpoint, boot many);
     * restored functions and procedures recompile lazily from their stored source on first use.
     */
    public synchronized void restoreStateFrom(final Path dir) throws IOException, ClassNotFoundException {
        persistenceManager.restoreFrom(dir, catalog, storageEngine);
        queryExecutor.copyInternalStagesFrom(dir.resolve("stages"));
    }

    /**
     * A new, fully independent engine carrying a copy of this engine's complete state — databases,
     * schemas, tables with rows, views, sequences at their current positions, functions, procedures,
     * streams with pending records, tasks, stages, roles and policies. Writes to either engine are
     * invisible to the other. The copy happens in memory, but for the files of the internal stages, which
     * the clone's own stages start with copies of; use
     * {@link #checkpointStateTo(Path)} / {@link #restoreStateFrom(Path)} instead when the clone must
     * cross a process boundary — a serialized checkpoint is the only form of this engine that can.
     */
    public DatabaseEngine cloneInstance() throws IOException, ClassNotFoundException {
        final MemoryTableDataStore tableData = new MemoryTableDataStore();
        final CatalogSnapshot state;
        final DatabaseEngine clone = new DatabaseEngine();
        synchronized (this) {
            state = persistenceManager.buildState(catalog, storageEngine, tableData);
            // The clone's internal stages start with copies of this engine's files, taken with its rows.
            clone.queryExecutor.copyInternalStagesOf(queryExecutor);
        }
        clone.persistenceManager.applyState(state, tableData, clone.catalog, clone.storageEngine);
        return clone;
    }

    /** This engine's effective configuration (read-only introspection, e.g. by embedders and tests). */
    public EngineConfig getConfig() {
        return config;
    }

    /**
     * Bind the session's JSON_INDENT so displayed variants use its width for this statement. The HTTP
     * wire binds it again while it renders a statement's rows, which happens after the statement's own
     * scope has closed; the caller clears it with {@link VariantJsonFormat#clearSessionScope}.
     */
    public void bindJsonIndent() {
        final Object indent = securityManager != null
            ? securityManager.getSessionContext().getSessionParameter("JSON_INDENT") : null;
        if (indent == null) {
            VariantJsonFormat.clearSessionScope();
            return;
        }
        try {
            // The parameter takes any 32-bit number; the layout uses the widths from none to sixteen.
            VariantJsonFormat.beginSessionScope(
                Math.max(0, Math.min(VariantJsonFormat.WIDEST_INDENT, Integer.parseInt(indent.toString().trim()))));
        } catch (final NumberFormatException notANumber) {
            VariantJsonFormat.clearSessionScope();
        }
    }

    /**
     * The sessions of this engine that SYSTEM$ABORT_SESSION can end. A front end that serves sessions of its own
     * registers each under the number CURRENT_SESSION() answers there.
     */
    public SessionDirectory getSessionDirectory() {
        return queryExecutor.getSessionDirectory();
    }

    /** The query-result cache backing RESULT_SCAN and LAST_QUERY_ID. */
    public QueryResultCache getQueryResultCache() {
        return queryExecutor.getResultCache();
    }

    public Catalog getCatalog() {
        return catalog;
    }

    /**
     * A routine's imports as its handler loader reads them: a file an internal named stage keeps is given by its
     * local path.
     *
     * @param imports the imports a routine keeps
     * @return the imports, in the same order
     */
    public List<String> localImports(final List<String> imports) {
        return queryExecutor.localImports(imports);
    }

    /** Resolver for s3:// (and S3-backed @stage) references to local files — see {@link S3PathResolver}. */
    public S3PathResolver getS3PathResolver() {
        return queryExecutor.getS3PathResolver();
    }

    public QueryExecutor getExecutor() {
        return queryExecutor;
    }

    public StorageEngine getStorageEngine() {
        return storageEngine;
    }

    public FunctionRegistry getFunctionRegistry() {
        return functionRegistry;
    }

    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    public SystemViews getSystemViews() {
        return systemViews;
    }

    // Stream and Task Management
    public void startTaskScheduler() {
        taskScheduler.start();
    }

    public void stopTaskScheduler() {
        taskScheduler.stop();
    }

    public StreamManager getStreamManager() {
        return streamManager;
    }

    public TaskScheduler getTaskScheduler() {
        return taskScheduler;
    }

    // Security Management
    public SecurityManager getSecurityManager() {
        return securityManager;
    }

    public SessionContext getSessionContext() {
        return sessionContext;
    }

    /**
     * Makes a user the one the engine's sessions run as. A session of the user starts with the secondary roles its
     * DEFAULT_SECONDARY_ROLES names, when a statement wrote it: ('ALL') puts every role granted to the user in use,
     * () none (documented). A user whose roles were never written leaves the secondary roles as they are.
     *
     * @param username the user's name
     */
    public void setCurrentUser(final String username) {
        sessionContext.setCurrentUser(username);
        final User user = username == null || !catalog.hasUser(username) ? null : catalog.getUser(username);
        final String defaults = user == null || !user.isDefaultSecondaryRolesWritten() ? null
            : user.getDefaultSecondaryRoles();
        if (defaults != null) {
            // The primary role is still the previous user's here: the embedder names the new user's with
            // setCurrentRole next. useSecondaryRoles keeps the primary role active, so a role the new user may not
            // activate is taken out again, or a user granted nothing but R1 would act with the former user's
            // SYSADMIN (FL-428).
            final String former = sessionContext.getCurrentRole();
            catalog.useSecondaryRoles(defaults.contains("\"ALL\"") ? null : Collections.singletonList("NONE"));
            if (former != null && !catalog.mayActivate(former)) {
                sessionContext.removeActiveRole(former);
            }
        }
    }

    /**
     * Makes a role the session's primary one, named in any case: the role's own name when one answers to it as
     * given, the name in upper case otherwise.
     *
     * @param roleName the role's name
     */
    public void setCurrentRole(final String roleName) {
        sessionContext.setCurrentRole(catalog.resolveRoleName(roleName));
    }

    public String getCurrentUser() {
        return sessionContext.getCurrentUser();
    }

    public String getCurrentRole() {
        return sessionContext.getCurrentRole();
    }

    /**
     * Discard everything created TEMPORARY as the engine shuts down, whatever session created it (see
     * {@link TemporaryObjects}).
     */
    private void dropTemporaryObjects() {
        TemporaryObjects.drop(catalog, storageEngine, null);
    }

    /**
     * Discard what a session created TEMPORARY, as that session ends: its tables, views, functions and procedures
     * go with it, and a later session finds the names free. The caller holds whatever keeps other statements out,
     * as it does for a statement.
     *
     * @param sessionNumber the number CURRENT_SESSION() answered in the session that ended
     * @return how many objects were dropped
     */
    public int dropTemporaryObjectsOf(final long sessionNumber) {
        return TemporaryObjects.drop(catalog, storageEngine, Long.valueOf(sessionNumber));
    }

    /**
     * Shutdown the engine and release resources
     */
    /**
     * The transactions write-ahead-log recovery could not re-apply when this engine started, in log order. Each was
     * rolled back and recovery went on with the rest, so the engine holds everything committed except these.
     * Empty when recovery re-applied everything, or when the log is off. With
     * {@code durability.walReplayStrict} an engine refuses to start instead of starting without them.
     *
     * @return the failures, unmodifiable
     */
    public List<WalReplayFailure> getWalReplayFailures() {
        return Collections.unmodifiableList(new ArrayList<>(walReplayFailures));
    }

    public void shutdown() {
        logger.info("Shutting down Frostlake SQL Engine");
        // A statement waiting for another session's table lock ends rather than waiting on (TableLockWait).
        transactionManager.shutDown();

        // Stop task scheduler
        if (taskScheduler.isRunning()) {
            taskScheduler.stop();
        }

        if (transactionManager.hasActiveTransaction()) {
            try {
                transactionManager.rollback();
            } catch (final Exception e) {
                logger.error("Error rolling back active transaction during shutdown", e);
            }
        }

        if (wal != null) {
            wal.close();
        }

        dropTemporaryObjects();

        // Save catalog before shutdown (snapshot-persistence mode only: with the WAL on, the log
        // already holds everything committed and startup ignores snapshot persistence anyway).
        if (config.isPersistenceEnabled() && !config.isWalEnabled()) {
            try {
                persistenceManager.stopAutoSave();
                persistenceManager.saveCatalog(catalog, storageEngine);
                logger.info("Catalog saved to: {}", config.getPersistenceDirectory());
            } catch (final Exception e) {
                logger.error("Failed to save catalog during shutdown", e);
            }
        }

        // An engine whose data ends with it deletes its own stage root: its stages' files and those it set aside.
        queryExecutor.releaseSetAsideStages();

        logger.info("Frostlake SQL Engine shut down");
    }

}
