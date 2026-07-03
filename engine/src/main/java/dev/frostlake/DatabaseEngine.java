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
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.SqlSyntaxException;
import dev.frostlake.persistence.PersistenceManager;
import dev.frostlake.persistence.WalRecord;
import dev.frostlake.persistence.WalRecordType;
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
import dev.frostlake.transaction.TransactionWriteSet;
import dev.frostlake.transaction.WalSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private boolean autoCommit;
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
        this.catalog = new Catalog();
        this.storageEngine = new StorageEngine();
        this.storageEngine.setEnforcePrimaryKey(config.isEnforcePrimaryKey());
        this.storageEngine.setEnforceUniqueKey(config.isEnforceUniqueKey());
        this.storageEngine.setTimeTravelEnabled(config.isTimeTravelEnabled());
        this.sessionContext = new SessionContext();
        this.functionRegistry = new FunctionRegistry(catalog, sessionContext, config);
        this.transactionManager = new TransactionManager(catalog, storageEngine);
        this.systemViews = new SystemViews(catalog);
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

        // Create TaskExecutor that delegates to QueryExecutor
        TaskExecutor taskExecutor = new TaskExecutor() {
            @Override
            public int execute(final String sql) {
                final List<ResultSet> results = queryExecutor.execute(sql);
                // DML statements report their affected-row count as a Snowflake-style result set.
                return (int) new ExecutionResult(true, results, null).getRowsAffected();
            }

            @Override
            public List<ResultSet> executeQuery(final String sql) {
                return queryExecutor.execute(sql);
            }
        };

        this.taskScheduler = new TaskScheduler(catalog, taskExecutor);
        this.proceduralExecutor = new ProceduralExecutor();
        this.autoCommit = true;

        // Wire up dependencies
        queryExecutor.setStreamManager(streamManager);
        transactionManager.setStreamManager(streamManager);
        queryExecutor.setTaskScheduler(taskScheduler);

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
            public void appendTransaction(final List<String> statements) {
                appendTransactionToWal(statements);
            }
        });
    }

    private void appendTransactionToWal(final List<String> statements) {
        try {
            wal.appendTransaction(statements);
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
        try {
            final List<WalRecord> records = wal.readAll();
            if (records.isEmpty()) {
                return;
            }
            replaying = true;

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
                replayTransaction(rec.getStatements());
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
            replaying = false;
        }
    }

    /** Re-execute one committed transaction's statements. A single-statement record replays as an autocommit
     *  statement; a multi-statement record replays inside a BEGIN…COMMIT, exactly as it was committed. */
    private void replayTransaction(final List<String> statements) {
        try {
            if (statements.size() == 1) {
                execute(statements.get(0));
                return;
            }
            execute("BEGIN");
            for (final String sql : statements) {
                execute(sql);
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
            transactionManager.getCurrentTransaction().logStatementForWal(sql);
        } else {
            appendTransactionToWal(Collections.singletonList(sql));
        }
    }

    private boolean isLoggableStatement(final String sql) {
        final String u = sql.trim().toUpperCase();
        return !(u.startsWith("SELECT") || u.startsWith("WITH") || u.startsWith("SHOW")
                || u.startsWith("DESCRIBE") || u.startsWith("DESC") || u.startsWith("EXPLAIN")
                || u.startsWith("BEGIN") || u.startsWith("START") || u.startsWith("COMMIT")
                || u.startsWith("ROLLBACK"));
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
        String defaultUser = config.getDefaultUser();
        try {
            catalog.getUser(defaultUser);
        } catch (final RuntimeException e) {
            catalog.createUser(defaultUser);
        }
        catalog.grantRoleToUser("ORGADMIN", defaultUser);
        catalog.grantRoleToUser("ACCOUNTADMIN", defaultUser);
        sessionContext.setDisplayUser(defaultUser);
    }

    private void initializePersistence() {
        if (!config.isPersistenceEnabled()) {
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
        logger.debug("Executing SQL: {}", sql);

        // Snowflake: a failed statement inside an explicit transaction rolls back ITSELF but leaves the
        // transaction open. In deferred-apply mode, snapshot the write set so we can restore exactly this
        // statement's changes on failure (statement-level rollback); otherwise fall back to full rollback.
        final TransactionWriteSet stmtSavepoint =
            (config.isDeferredApply() && transactionManager.isExplicitTransaction())
                ? transactionManager.getCurrentTransaction().getWriteSet().copy() : null;

        try {
            // Parse and execute the SQL
            List<ResultSet> results = queryExecutor.execute(sql);

            // Record this statement for the WAL before the autocommit commit, so the commit logs it as part
            // of its transaction; an explicit BEGIN…COMMIT buffers each statement until COMMIT.
            recordStatementForWal(sql);

            // Autocommit commits at statement end — UNLESS an explicit BEGIN is in effect, which suspends
            // autocommit until COMMIT/ROLLBACK (Snowflake semantics).
            if (autoCommit && transactionManager.hasActiveTransaction()
                    && !transactionManager.isExplicitTransaction()) {
                transactionManager.commit();
            }

            // Bound the WAL by checkpointing once enough transactions accumulate (no-op unless configured).
            maybeAutoCheckpoint();

            // Get the query ID from the result cache
            String queryId = queryExecutor.getResultCache().getLastQueryId();

            return new ExecutionResult(true, results, null, queryId);

        } catch (final SqlSyntaxException e) {
            // Log syntax errors with the failed query
            logger.error("SQL syntax error in query:\n{}\nError details:\n{}", sql, e.getMessage());
            rollbackFailedStatement(stmtSavepoint);
            // Re-throw exception so tests and callers can handle it
            throw e;
        } catch (final Exception e) {
            logger.error("Error executing SQL:\n{}\nError: {}", sql, e.getMessage(), e);
            rollbackFailedStatement(stmtSavepoint);
            // Re-throw exception so tests can catch it
            throw e;
        }
    }

    /**
     * Undo a failed statement. Snowflake keeps an explicit transaction OPEN after a statement error,
     * rolling back only that statement — in deferred-apply mode, restore the write-set savepoint taken
     * before the statement. Otherwise (implicit/autocommit txn, or legacy immediate-apply) roll back the
     * whole transaction, which for an autocommit statement is exactly that one statement.
     */
    private void rollbackFailedStatement(final TransactionWriteSet stmtSavepoint) {
        if (stmtSavepoint != null && transactionManager.isExplicitTransaction()) {
            transactionManager.getCurrentTransaction().getWriteSet().restoreFrom(stmtSavepoint);
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
        ExecutionResult result = execute(sql);
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
        ExecutionResult result = execute(sql);
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
        this.autoCommit = autoCommit;
    }

    public boolean isAutoCommit() {
        return autoCommit;
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

    private void dropTemporaryTables() {
        for (final Database db : catalog.getAllDatabases()) {
            for (final Schema schema : db.getAllSchemas()) {
                for (final Table table : new ArrayList<>(schema.getTables())) {
                    if (table.isTemporary()) {
                        String fqn = db.getName() + "." + schema.getName() + "." + table.getName();
                        schema.dropTable(table.getName());
                        if (storageEngine.hasTable(fqn)) {
                            storageEngine.dropTable(fqn);
                        }
                        logger.debug("Dropped temporary table: {}", fqn);
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

        dropTemporaryTables();

        // Save catalog before shutdown
        if (config.isPersistenceEnabled()) {
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
