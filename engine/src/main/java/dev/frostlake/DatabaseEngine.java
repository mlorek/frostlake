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
import dev.frostlake.executor.ProceduralExecutor;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.QueryResultCache;
import dev.frostlake.executor.StatementBoundaries;
import dev.frostlake.executor.StatementClock;
import dev.frostlake.executor.udf.UdfLanguageRuntime;
import dev.frostlake.executor.udf.UdfRuntimes;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.TableShadows;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.View;
import dev.frostlake.parser.SqlSyntaxException;
import dev.frostlake.persistence.CatalogSnapshot;
import dev.frostlake.persistence.MemoryTableDataStore;
import dev.frostlake.persistence.PersistenceManager;
import dev.frostlake.persistence.WalRecord;
import dev.frostlake.persistence.WalRecordType;
import dev.frostlake.persistence.WalStatementKinds;
import dev.frostlake.persistence.WriteAheadLog;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.security.SessionContext;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.stream.StreamManager;
import dev.frostlake.system.SystemViews;
import dev.frostlake.task.TaskExecutor;
import dev.frostlake.task.TaskScheduler;
import dev.frostlake.transaction.TransactionManager;
import dev.frostlake.transaction.WalSink;
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
import java.util.List;

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
    private int txnsSinceCheckpoint = 0;  // committed transactions appended since the last WAL checkpoint
    private long checkpointSeq = 0;       // monotonic sequence for naming checkpoint snapshot directories

    public DatabaseEngine() {
        this(new EngineConfig());
    }

    public DatabaseEngine(final EngineConfig config) {
        logger.info("Initializing Frostlake SQL Engine");

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
        };

        this.taskScheduler = new TaskScheduler(catalog, taskExecutor);
        this.proceduralExecutor = new ProceduralExecutor();

        // Wire up dependencies
        queryExecutor.setStreamManager(streamManager);
        transactionManager.setStreamManager(streamManager);
        queryExecutor.setTaskScheduler(taskScheduler);
        functionRegistry.setTaskScheduler(taskScheduler);
        functionRegistry.setTransactionManager(transactionManager);

        initializeSystemObjects();
        initializePersistence();

        // Durability: open the write-ahead log, wire the commit-time sink, and replay any committed work
        // (last checkpoint snapshot + the transactions logged after it) from a prior run.
        this.wal = initWal();
        if (wal != null) {
            wireWalSink();
            checkpointSeq = scanMaxCheckpointSeq();
            replayWal();
        }

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
            return new WriteAheadLog(Path.of(config.getWalFile()));
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
                                          final List<Instant> instants) {
                appendTransactionToWal(statements, instants);
            }
        });
    }

    private void appendTransactionToWal(final List<String> statements,
                                        final List<Instant> instants) {
        try {
            wal.appendTransaction(statements, instants);
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
        try {
            final List<WalRecord> records = wal.readAll();
            if (records.isEmpty()) {
                return;
            }
            replaying = true;
            // The logged statements were authorized when they first ran; replaying them re-applies what was
            // committed, under whatever role and session happen to be current now.
            sessionContext.setSecurityEnabled(false);

            int lastCheckpoint = -1;
            for (int i = 0; i < records.size(); i++) {
                if (records.get(i).getType() == WalRecordType.CHECKPOINT) {
                    lastCheckpoint = i;
                }
            }

            String fromCheckpoint = null;
            int start = 0;
            if (lastCheckpoint >= 0) {
                fromCheckpoint = records.get(lastCheckpoint).getCheckpointRef();
                restoreCheckpoint(fromCheckpoint);
                start = lastCheckpoint + 1;
            }

            int replayed = 0;
            for (int i = start; i < records.size(); i++) {
                final WalRecord rec = records.get(i);
                if (rec.getType() != WalRecordType.TRANSACTION) {
                    continue;
                }
                replayTransaction(rec.getStatements(), rec);
                replayed++;
            }

            if (fromCheckpoint != null) {
                logger.info("Recovered from WAL checkpoint {} plus {} subsequent transaction(s)",
                    fromCheckpoint, replayed);
            } else {
                logger.info("Recovered {} transaction(s) from the write-ahead log", replayed);
            }
        } catch (final Exception e) {
            logger.error("Write-ahead log replay failed", e);
        } finally {
            if (replaying) {
                sessionContext.setSecurityEnabled(securityWasEnabled);
            }
            replaying = false;
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
    private void replayTransaction(final List<String> statements, final WalRecord record) {
        // While replaying, a procedural block's inner statements must NOT advance the clock —
        // the log recorded one instant per outer statement, and replay keeps it.
        StatementClock.setReplay(true);
        try {
            if (statements.size() == 1) {
                queryExecutor.setNextStatementInstant(record.getStatementInstant(0));
                execute(statements.get(0));
                return;
            }
            execute("BEGIN");
            for (int i = 0; i < statements.size(); i++) {
                queryExecutor.setNextStatementInstant(record.getStatementInstant(i));
                execute(statements.get(i));
            }
            execute("COMMIT");
        } catch (final Exception e) {
            logger.error("WAL replay failed for a transaction; rolling it back", e);
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
    private void recordStatementForWal(final String sql) {
        if (wal == null || replaying || !isLoggableStatement(sql)) {
            return;
        }
        if (transactionManager.hasActiveTransaction()) {
            transactionManager.getCurrentTransaction()
                .logStatementForWal(sql, queryExecutor.currentStatementInstant());
        } else {
            appendTransactionToWal(Collections.singletonList(sql),
                Collections.singletonList(queryExecutor.currentStatementInstant()));
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
        return queryExecutor.isDurableStatement(sql);
    }

    /** The loggability decision, exposed so tests can assert it family by family. */
    public boolean isDurableStatementForTesting(final String sql) {
        return isLoggableStatement(sql);
    }

    /**
     * Write a WAL checkpoint: snapshot the full state durably, append a checkpoint marker (the atomic pivot —
     * recovery keys off the LAST marker, so a crash before it leaves an ignored orphan snapshot), then
     * truncate the log to the marker and drop superseded snapshots. No-op while an explicit transaction is
     * open (its statements aren't committed yet). Safe to call explicitly; also driven by
     * {@code durability.checkpointInterval}.
     */
    public synchronized void checkpoint() {
        if (wal == null) {
            return;
        }
        if (transactionManager.isExplicitTransaction()) {
            logger.warn("Skipping WAL checkpoint while an explicit transaction is open");
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
        if (interval > 0 && txnsSinceCheckpoint >= interval && !transactionManager.isExplicitTransaction()) {
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
        final String defaultDatabase = config.getDefaultDatabase().toUpperCase();
        final String defaultSchema = config.getDefaultSchema().toUpperCase();
        try {
            catalog.getDatabase(defaultDatabase);
        } catch (final RuntimeException e) {
            catalog.createDatabase(defaultDatabase);
        }
        catalog.useDatabase(defaultDatabase);
        final Database database = catalog.getDatabase(defaultDatabase);
        try {
            database.getSchema(defaultSchema);
        } catch (final RuntimeException e) {
            database.addSchema(new Schema(defaultSchema));
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
            (config.isDeferredApply() && transactionManager.isExplicitTransaction())
                ? transactionManager.getCurrentTransaction().getWriteSet().beginStatementSavepoint() : null;

        // A request of several statements commits, logs and rolls back statement by statement, as the
        // account does; the flag records that its boundaries were reported, so the request as a whole is
        // neither logged again nor rolled back again below.
        final boolean[] statementByStatement = {false};
        final WriteSetSavepoint[] statementSavepoint = {null};
        final StatementBoundaries displacedBoundaries = queryExecutor.bindStatementBoundaries(new StatementBoundaries() {
            @Override
            public void begin() {
                statementByStatement[0] = true;
                statementSavepoint[0] = config.isDeferredApply() && transactionManager.isExplicitTransaction()
                    ? transactionManager.getCurrentTransaction().getWriteSet().beginStatementSavepoint() : null;
            }

            @Override
            public void complete(final String statementText) {
                recordStatementForWal(statementText);
                transactionManager.autocommitStatementEnd();
                if (statementSavepoint[0] != null && transactionManager.isExplicitTransaction()) {
                    transactionManager.getCurrentTransaction().getWriteSet().endStatementSavepoint();
                }
                statementSavepoint[0] = null;
            }

            @Override
            public void fail() {
                rollbackFailedStatement(statementSavepoint[0]);
                statementSavepoint[0] = null;
            }
        });

        try {
            // Parse and execute the SQL. Dirty-mark BEFORE executing: an attempt is enough (see
            // noteStatementExecuted), and marking after a success would miss failure side effects.
            persistenceManager.noteStatementExecuted();
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

            if (stmtSavepoint != null && transactionManager.isExplicitTransaction()) {
                transactionManager.getCurrentTransaction().getWriteSet().endStatementSavepoint();
            }

            return new ExecutionResult(true, results, null, queryId);

        } catch (final SqlSyntaxException e) {
            // Log syntax errors with the failed query
            logger.error("SQL syntax error in query:\n{}\nError details:\n{}", sql, e.getMessage());
            if (!statementByStatement[0]) {
                rollbackFailedStatement(stmtSavepoint);
            }
            // Re-throw exception so tests and callers can handle it
            throw e;
        } catch (final Exception e) {
            logger.error("Error executing SQL:\n{}\nError: {}", sql, e.getMessage(), e);
            if (!statementByStatement[0]) {
                rollbackFailedStatement(stmtSavepoint);
            }
            // Re-throw exception so tests can catch it
            throw e;
        } finally {
            queryExecutor.restoreStatementBoundaries(displacedBoundaries);
        }
    }

    /**
     * Undo a failed statement. Snowflake keeps an explicit transaction OPEN after a statement error,
     * rolling back only that statement — in deferred-apply mode, restore the write-set savepoint taken
     * before the statement. Otherwise (implicit/autocommit txn, or legacy immediate-apply) roll back the
     * whole transaction, which for an autocommit statement is exactly that one statement.
     */
    private void rollbackFailedStatement(final WriteSetSavepoint stmtSavepoint) {
        if (stmtSavepoint != null && transactionManager.isExplicitTransaction()) {
            transactionManager.getCurrentTransaction().getWriteSet().rollbackToSavepoint(stmtSavepoint);
        } else if (transactionManager.hasActiveTransaction()) {
            try {
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
     * tasks, stages, roles and policies — to {@code dir}, from which {@link #restoreStateFrom(Path)}
     * can rebuild an equivalent engine. Holds the engine monitor, so it is mutually exclusive with
     * statements running on direct connections. Works regardless of {@code persistence.enabled}.
     */
    public synchronized void checkpointStateTo(final Path dir) throws IOException {
        persistenceManager.checkpointTo(dir, catalog, storageEngine);
    }

    /**
     * Rebuild this engine's state from a {@link #checkpointStateTo(Path)} snapshot. Intended for a
     * freshly constructed engine (the template-clone pattern: migrate once, checkpoint, boot many);
     * restored functions and procedures recompile lazily from their stored source on first use.
     */
    public synchronized void restoreStateFrom(final Path dir) throws IOException, ClassNotFoundException {
        persistenceManager.restoreFrom(dir, catalog, storageEngine);
    }

    /**
     * A new, fully independent engine carrying a copy of this engine's complete state — databases,
     * schemas, tables with rows, views, sequences at their current positions, functions, procedures,
     * streams with pending records, tasks, stages, roles and policies. Writes to either engine are
     * invisible to the other. The copy happens entirely in memory (no filesystem involvement); use
     * {@link #checkpointStateTo(Path)} / {@link #restoreStateFrom(Path)} instead when the clone must
     * cross a process boundary — a serialized checkpoint is the only form of this engine that can.
     */
    public DatabaseEngine cloneInstance() throws IOException, ClassNotFoundException {
        final MemoryTableDataStore tableData = new MemoryTableDataStore();
        final CatalogSnapshot state;
        synchronized (this) {
            state = persistenceManager.buildState(catalog, storageEngine, tableData);
        }
        final DatabaseEngine clone = new DatabaseEngine();
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
            VariantJsonFormat.beginSessionScope(Integer.parseInt(indent.toString().trim()));
        } catch (final NumberFormatException notANumber) {
            VariantJsonFormat.clearSessionScope();
        }
    }

    /** The query-result cache backing RESULT_SCAN and LAST_QUERY_ID. */
    public QueryResultCache getQueryResultCache() {
        return queryExecutor.getResultCache();
    }

    public Catalog getCatalog() {
        return catalog;
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

    public void setCurrentUser(final String username) {
        sessionContext.setCurrentUser(username);
    }

    public void setCurrentRole(final String roleName) {
        sessionContext.setCurrentRole(roleName);
    }

    public String getCurrentUser() {
        return sessionContext.getCurrentUser();
    }

    public String getCurrentRole() {
        return sessionContext.getCurrentRole();
    }

    /**
     * Discard everything created TEMPORARY when the session ends — tables, and equally the views,
     * functions and procedures the temporary spellings can now create.
     *
     * <p>A temporary object's lifetime is the session, live-verified for all four kinds: a second
     * session sees neither the view ({@code Object 'V' does not exist or not authorized}) nor the
     * routine ({@code Unknown function F}). Frostlake keeps one namespace per catalog, so it models
     * that lifetime rather than the isolation — see the divergence recorded on the temporary-object
     * tests.
     */
    private void dropTemporaryObjects() {
        for (final Database db : catalog.getAllDatabases()) {
            for (final Schema schema : db.getAllSchemas()) {
                for (final Table table : new ArrayList<>(schema.getTables())) {
                    if (table.isTemporary()) {
                        final String fqn = db.getName() + "." + schema.getName() + "." + table.getName();
                        schema.dropTable(table.getName());
                        if (storageEngine.hasTable(fqn)) {
                            storageEngine.dropTable(fqn);
                        }
                        logger.debug("Dropped temporary table: {}", fqn);
                    }
                }
                // Each permanent table a temporary one hid is uncovered.
                for (final Table hidden : schema.getShadowedTables()) {
                    TableShadows.settle(schema, storageEngine, db.getName(), hidden.getName());
                }
                for (final View view : new ArrayList<>(schema.getViews())) {
                    if (view.isTemporary()) {
                        schema.dropView(view.getName());
                        logger.debug("Dropped temporary view: {}", view.getName());
                    }
                }
                for (final Function function : new ArrayList<>(schema.getFunctions())) {
                    if (function.isTemporary()) {
                        schema.removeFunction(function);
                        logger.debug("Dropped temporary function: {}", function.getName());
                    }
                }
                for (final Procedure procedure : new ArrayList<>(schema.getProcedures())) {
                    if (procedure.isTemporary()) {
                        schema.removeProcedure(procedure);
                        logger.debug("Dropped temporary procedure: {}", procedure.getName());
                    }
                }
            }
        }
    }

    /**
     * Shutdown the engine and release resources
     */
    public void shutdown() {
        logger.info("Shutting down Frostlake SQL Engine");

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

        logger.info("Frostlake SQL Engine shut down");
    }

}
