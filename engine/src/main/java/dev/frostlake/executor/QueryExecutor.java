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

package dev.frostlake.executor;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.executor.expressions.SqlTruth;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.config.S3PathResolver;
import dev.frostlake.executor.copy.CopyCommandExecutor;
import dev.frostlake.executor.expressions.AstPrinterVisitor;
import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.operators.*;
import dev.frostlake.executor.procedural.ProceduralException;
import dev.frostlake.executor.streaming.FilterRowStream;
import dev.frostlake.executor.streaming.LimitRowStream;
import dev.frostlake.executor.streaming.ListRowStream;
import dev.frostlake.executor.streaming.RowPredicate;
import dev.frostlake.executor.streaming.RowStream;
import dev.frostlake.executor.udf.UdfRuntimes;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.functions.aggregate.PercentileCont;
import dev.frostlake.functions.aggregate.PercentileDisc;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.functions.scalar.context.CurrVal;
import dev.frostlake.functions.scalar.context.CurrentAccount;
import dev.frostlake.functions.scalar.context.LastQueryId;
import dev.frostlake.functions.scalar.context.NextVal;
import dev.frostlake.functions.table.QueryRunner;
import dev.frostlake.functions.table.ResultScan;
import dev.frostlake.functions.table.ToQuery;
import dev.frostlake.functions.window.WindowFunctionHelper;
import dev.frostlake.metastore.*;
import dev.frostlake.metastore.model.*;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.TaskState;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlSyntaxException;
import dev.frostlake.parser.SyntaxErrorListener;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.stream.StreamManager;
import dev.frostlake.system.SystemViews;
import dev.frostlake.task.TaskScheduler;
import dev.frostlake.transaction.StreamReadScope;
import dev.frostlake.transaction.TransactionManager;
import dev.frostlake.transaction.TransactionWriteSet;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Query Executor - Executes SQL statements using ANTLR-based parsing
 */
public class QueryExecutor {

    private static final Logger logger = LoggerFactory.getLogger(QueryExecutor.class);

    private final Catalog catalog;
    private final StorageEngine storageEngine;
    private final FunctionRegistry functionRegistry;
    private final TransactionManager transactionManager;
    private final boolean deferredApply;   // transaction.deferredApply: buffer DML into a write set, apply on COMMIT
    private final boolean enforceTypes;    // constraints.enforce.types: coerce write values to the column type
    private final SystemViews systemViews;
    private StreamManager streamManager;
    // Stream consumption (Snowflake CDC): a stream read inside a consuming DML is registered on the current
    // transaction and its offset advances when that txn COMMITS (discarded on ROLLBACK); a plain SELECT reads
    // it non-destructively. consumingStreamContext marks the DML window during which reads register; the
    // window opens in SQLCommandVisitor.visitDmlStatement so EVERY execution path (top-level statement,
    // procedural block body, task body) marks it — not just the string entry point.
    private boolean consumingStreamContext = false;
    // Streams read by a CTAS source SELECT (atomic DDL — no transaction to register on); consumed once the
    // CTAS table is created + populated (consumeCtasStreams), so a failed CTAS does not consume.
    private final List<Stream> ctasStreamsRead = new ArrayList<>();
    private TaskScheduler taskScheduler;
    private SecurityManager securityManager;
    private DatabaseEngine databaseEngine;
    private final Map<String, Object> sessionVariables = new LinkedHashMap<>();
    private final SQLCommandVisitor visitor;
    // Re-entry depth for execute(): 0 means the next call is the outermost (true top-level) statement.
    // Used to clear leftover procedural cursor/exception state only at the top level, never mid-block.
    private int executeReentryDepth = 0;
    private final QueryResultCache resultCache;
    private final QueryHistoryTracker queryHistoryTracker;
    private final ShowCommandExecutor showExecutor;
    private final SystemFunctionEvaluator systemFunctionEvaluator;
    private final EngineConfig engineConfig;
    // Resolves s3:// (and S3-backed @stage) references to local files — used to load IMPORTS JARs locally.
    private final S3PathResolver s3PathResolver;
    // Window-function query stage (partitioning, ranking/navigation/value/aggregate window fns, frames).
    private final WindowFunctionEvaluator windowEvaluator = new WindowFunctionEvaluator(this);
    // The multi-table (alias) context in force while window functions / QUALIFY compute over the rows of a
    // JOIN. The window stage resolves each PARTITION BY / ORDER BY / argument expression through
    // evaluateExpression(expr, row, table), which builds a single-table evaluator; over joined rows an
    // alias-qualified column (e.g. o.region) only resolves with the alias context, so we make it
    // available here. A per-thread stack (never shared: SELECTs run under the engine read lock, so several
    // may evaluate at once) keeps it re-entrancy-safe for a subquery nested inside an ORDER BY / PARTITION key.
    private final ThreadLocal<Deque<Map<String, Table>>> windowAliasToTableStack =
        new ThreadLocal<Deque<Map<String, Table>>>() {
            @Override
            protected Deque<Map<String, Table>> initialValue() {
                return new ArrayDeque<>();
            }
        };
    private final ThreadLocal<Deque<List<Table>>> windowAllTablesStack =
        new ThreadLocal<Deque<List<Table>>>() {
            @Override
            protected Deque<List<Table>> initialValue() {
                return new ArrayDeque<>();
            }
        };
    // PIVOT / UNPIVOT query stage (row<->column transforms).
    private final PivotUnpivotExecutor pivotExecutor = new PivotUnpivotExecutor(this);
    // COPY subsystem: COPY INTO <table> load, COPY INTO <stage> unload, VALIDATION_MODE, transformations,
    // plus its COPY-local state (load history, pipe-REFRESH filters). Shared stage helpers stay on this executor.
    private final CopyCommandExecutor copyExecutor = new CopyCommandExecutor(this);
    // Column-masking + row-access-policy query stage.
    private final MaskingPolicyApplier maskingApplier = new MaskingPolicyApplier(this);
    // ORDER BY query stage (positional/alias/expression sort keys, NULLS FIRST/LAST).
    private final OrderByExecutor orderByExecutor = new OrderByExecutor(this);
    // GROUP BY + aggregate-evaluation query stage (ROLLUP/CUBE/GROUPING SETS, aggregate plans/eval).
    private final GroupByAggregateEvaluator groupByEvaluator = new GroupByAggregateEvaluator(this);
    // INSERT write-path query stage (single-table + multi-table INSERT, row build, deferred PK/UK checks).
    private final InsertExecutor insertExecutor = new InsertExecutor(this);
    // MERGE write-path query stage (source resolution, WHEN [NOT] MATCHED UPDATE/DELETE/INSERT).
    private final MergeExecutor mergeExecutor = new MergeExecutor(this);
    // UPDATE / DELETE write-path query stage (plain + UPDATE…FROM / DELETE…USING + deferred-apply variants).
    private final UpdateDeleteExecutor updateDeleteExecutor = new UpdateDeleteExecutor(this);

    // Parsed scripts keyed by SQL text. Correlated subqueries re-run the same SQL once per outer row,
    // so caching the (immutable, read-only re-walked) parse tree turns N re-parses into one; top-level
    // execute() also flows through here, so repeated identical statements parse once. Bounded (LRU +
    // max-size guard) so a bulk load issuing thousands of distinct multi-row INSERTs can't grow it
    // without limit — ANTLR trees run ~100x their source text. See BoundedParseCache.
    private static final BoundedParseCache<FrostlakeParser.SqlScriptContext> SCRIPT_CACHE =
        new BoundedParseCache<>(256, 4096);
    // Caches whether a SQL-UDF body parses as a query statement (vs a bare scalar expression), so the
    // classifying parse — and its throw for expression bodies — happens once per distinct body, not per row.
    private static final BoundedParseCache<Boolean> QUERY_BODY_CACHE = new BoundedParseCache<>(256, 4096);

    public QueryExecutor(final Catalog catalog, final StorageEngine storageEngine,
                        final FunctionRegistry functionRegistry, final TransactionManager transactionManager,
                        final EngineConfig config) {
        this.catalog = catalog;
        this.storageEngine = storageEngine;
        this.functionRegistry = functionRegistry;
        this.transactionManager = transactionManager;
        this.deferredApply = config != null && config.isDeferredApply();
        this.enforceTypes = config != null && config.isEnforceTypes();
        this.engineConfig = config;
        this.s3PathResolver = config != null ? new S3PathResolver(config) : null;
        this.systemViews = new SystemViews(catalog);
        this.visitor = new SQLCommandVisitor(catalog, this);
        this.resultCache = new QueryResultCache(
            config != null ? config.getQueryResultCacheSize() : 100);
        this.queryHistoryTracker = new QueryHistoryTracker(
            config != null ? config.getQueryHistorySize() : 10000);
        this.showExecutor = new ShowCommandExecutor(catalog, transactionManager, queryHistoryTracker, sessionVariables, functionRegistry);
        this.systemFunctionEvaluator = new SystemFunctionEvaluator(catalog, storageEngine, transactionManager);

        // Register RESULT_SCAN table function with access to result cache
        functionRegistry.registerTableFunction(new ResultScan(resultCache));

        // Register TO_QUERY with a callback that runs its SQL text through this executor
        functionRegistry.registerTableFunction(new ToQuery(new QueryRunner() {
            @Override
            public ResultSet runQuery(final String sql) {
                final List<ResultSet> results = execute(sql);
                return results.isEmpty() ? null : results.get(results.size() - 1);
            }
        }));

        // Register LAST_QUERY_ID() scalar function with access to result cache
        functionRegistry.register(new LastQueryId(resultCache));

        // Register NEXTVAL and CURRVAL sequence functions
        // NEXTVAL('seq') as a FUNCTION is not Snowflake syntax (live-verified: "Unknown function
        // NEXTVAL") — only the member form seq.NEXTVAL exists, handled by the evaluator directly.
        functionRegistry.register(new CurrVal(catalog));

        // Register CURRENT_ACCOUNT() function with access to config
        functionRegistry.register(new CurrentAccount(config));
    }

    public void setStreamManager(final StreamManager streamManager) {
        this.streamManager = streamManager;
        this.visitor.setStreamManager(streamManager);
    }

    public void setTaskScheduler(final TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
        this.visitor.setTaskScheduler(taskScheduler);
    }

    public void setDatabaseEngine(final DatabaseEngine engine) {
        this.databaseEngine = engine;
    }

    public DatabaseEngine getDatabaseEngine() {
        return databaseEngine;
    }

    public void setSecurityManager(final SecurityManager securityManager) {
        this.securityManager = securityManager;
        showExecutor.setSecurityManager(securityManager);
    }

    void checkNotReadOnly() {
        String dbName = catalog.getCurrentDatabase();
        if (dbName != null && !"SNOWFLAKE".equalsIgnoreCase(dbName)) {
            // Only enforce read-only for user-created databases, not the system SNOWFLAKE db
            try {
                Database db = catalog.getDatabase(dbName);
                if (db != null && db.isReadOnly()) {
                    throw new RuntimeException("Database '" + dbName + "' is read-only");
                }
            } catch (final RuntimeException e) {
                if (e.getMessage() != null && e.getMessage().contains("read-only")) throw e;
            }
        }
    }

    public SecurityManager getSecurityManager() {
        return securityManager;
    }

    public Map<String, Object> getSessionVariables() {
        return sessionVariables;
    }

    public QueryResultCache getResultCache() {
        return resultCache;
    }

    /** Evaluate a SYSTEM$FUNCNAME call from expression context (no ANTLR ctx available). */
    public Object evaluateSystemFunction(final String funcName, final List<Object> args) {
        return systemFunctionEvaluator.evaluateSystemFunction(funcName, args);
    }

    public QueryHistoryTracker getQueryHistoryTracker() {
        return queryHistoryTracker;
    }

    /**
     * Execute SQL using ANTLR parsing and visitor pattern
     */
    public List<ResultSet> execute(final String sql) {
        // At the OUTERMOST execution, drop procedural cursors/exceptions left over from a prior top-level
        // statement so a re-declared cursor/exception on this shared session doesn't fail with "already
        // declared". Guarded by a re-entry depth so a nested execute() from inside a running block (e.g.
        // opening a cursor, or a SQL statement within BEGIN…END) does NOT wipe that block's live cursors.
        // This mirrors what EXECUTE IMMEDIATE already does for its dynamic procedural scripts.
        // A live BEGIN…END block also protects its own state: the re-entry depth only sees nesting that goes
        // through execute(), so a statement reaching the engine by another entry point (or from another
        // thread on this shared engine, e.g. a scheduled task) could still look "outermost" and wipe the
        // exceptions/cursors/variable types of a procedure that is mid-flight.
        final boolean outermost = executeReentryDepth == 0
            && !visitor.getProceduralExecutor().isExecutingBlock();
        executeReentryDepth++;
        try {
            if (outermost) {
                visitor.getProceduralExecutor().clearCursorsAndExceptions();
            }
            return executeWithLateralContext(sql, null);
        } finally {
            executeReentryDepth--;
        }
    }

    // Parse a SQL script to a parse tree, caching by text. The tree is immutable and re-walking it is
    // read-only, so reuse across executions (and concurrent sessions) is safe; only successfully-parsed
    // SQL is cached, so syntax errors still surface every call.
    private FrostlakeParser.SqlScriptContext parseScript(final String sql) {
        return parseScript(sql, false);
    }

    /** As {@link #parseScript(String)}; {@code quiet} demotes syntax-error logging for speculative parses. */
    private FrostlakeParser.SqlScriptContext parseScript(final String sql, final boolean quiet) {
        FrostlakeParser.SqlScriptContext cached = SCRIPT_CACHE.get(sql);
        if (cached != null) {
            return cached;
        }
        FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        SyntaxErrorListener errorListener = new SyntaxErrorListener(sql, quiet);
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FrostlakeParser parser = new FrostlakeParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);
        FrostlakeParser.SqlScriptContext tree = parser.sqlScript();
        errorListener.throwIfErrors();
        SCRIPT_CACHE.put(sql, tree);
        return tree;
    }

    /**
     * True when {@code sql} parses as a single query statement (SELECT / WITH … SELECT). Lets a SQL-UDF body
     * that is a query be distinguished from a bare scalar expression via the parse tree, not a string prefix
     * (so {@code WITH … SELECT} CTE bodies are recognised too). A body that does not parse as a statement
     * (e.g. {@code x * 2}) returns false. Cached per body.
     */
    public boolean isQueryStatement(final String sql) {
        if (sql == null) {
            return false;
        }
        final Boolean cached = QUERY_BODY_CACHE.get(sql);
        if (cached != null) {
            return cached;
        }
        boolean result;
        try {
            final FrostlakeParser.SqlScriptContext tree = parseScript(sql, true);
            result = !tree.flowChain().isEmpty()
                && tree.flowChain().get(0).statement().get(0).queryStatement() != null;
        } catch (final RuntimeException e) {
            result = false;   // not a parseable statement → treat as a scalar expression body
        }
        QUERY_BODY_CACHE.put(sql, result);
        return result;
    }

    /**
     * Execute SQL with lateral context support (for correlated subqueries)
     */
    public List<ResultSet> executeWithLateralContext(final String sql, final Map<String, Object> lateralContext) {
        List<ResultSet> results = new ArrayList<>();
        boolean hasResultSet = false;

        // Track query history (only for top-level queries, not lateral subqueries)
        QueryHistory queryHistory = null;
        if (lateralContext == null) {
            queryHistory = new QueryHistory(
                sql,
                catalog.getCurrentDatabase(),
                catalog.getCurrentSchema(),
                catalog.getCurrentWarehouse(),
                securityManager != null ? securityManager.getSessionContext().getDisplayUser() : "SYSTEM",
                securityManager != null ? securityManager.getSessionContext().getCurrentRole() : "SYSADMIN"
            );
        }

        try {
            // Parse once and cache (correlated subqueries re-enter with identical SQL per outer row).
            FrostlakeParser.SqlScriptContext tree = parseScript(sql);

            // Visit each flow chain (a chain is usually a single statement; with the ->> flow
            // operator it is several, executed in order — each stage may read a prior stage's
            // result via $n, and only the LAST stage's result is the chain's result).
            for (final FrostlakeParser.FlowChainContext chainCtx : tree.flowChain()) {
                final List<FrostlakeParser.StatementContext> stages = chainCtx.statement();
                final List<ResultSet> priorFlowResults = flowChainResults;
                Object result;
                try {
                    if (stages.size() > 1) {
                        flowChainResults = new ArrayList<>();
                        for (int stage = 0; stage < stages.size() - 1; stage++) {
                            final Object stageResult = visitor.visit(stages.get(stage));
                            flowChainResults.add(stageResult instanceof ResultSet ? (ResultSet) stageResult : null);
                            // Time-travel snapshots per stage, mirroring the per-statement behavior below.
                            if (lateralContext == null) {
                                storageEngine.snapshotDirtyTables();
                            }
                        }
                    }
                    final FrostlakeParser.StatementContext stmtCtx = stages.get(stages.size() - 1);

                    // If we have lateral context and this is a query statement (SELECT), pass it along.
                    // (The stream-consuming DML window and DDL's implicit pre-commit both live in the
                    // visitor's visitDmlStatement/visitDdlStatement, so procedural bodies that dispatch
                    // through the visitor directly are covered too.)
                    if (lateralContext != null && stmtCtx.queryStatement() != null) {
                        FrostlakeParser.QueryStatementContext queryCtx = stmtCtx.queryStatement();
                        if (queryCtx.selectStatement() != null) {
                            result = executeSelectFromContext(queryCtx.selectStatement(), lateralContext);
                        } else {
                            result = visitor.visit(stmtCtx);
                        }
                    } else {
                        result = visitor.visit(stmtCtx);
                    }
                } finally {
                    flowChainResults = priorFlowResults;
                }

                if (result instanceof ResultSet) {
                    ResultSet resultSet = (ResultSet) result;
                    // Cache the result for RESULT_SCAN (only for non-correlated queries)
                    if (lateralContext == null) {
                        String queryId = resultCache.cacheResult(sql, resultSet);
                        if (queryHistory != null) {
                            queryHistory.setQueryId(queryId);
                        }
                        hasResultSet = true;
                    }
                    results.add(resultSet);
                }
                // transactionStatement is handled by visitTransactionStatement in the visitor above

                // One time-travel snapshot per statement for any table it mutated (was per row → O(N^2)).
                if (lateralContext == null) {
                    storageEngine.snapshotDirtyTables();
                }
            }

            // Generate a query ID for non-result-producing queries (only for non-correlated queries).
            // Skip purely procedural control-flow statements (LET, FOR, DECLARE, OPEN, FETCH, CLOSE)
            // so they don't displace SHOW/SELECT results from LAST_QUERY_ID().
            if (!hasResultSet && lateralContext == null && !isProceduralControlFlow(tree)) {
                String queryId = resultCache.generateQueryId(sql);
                if (queryHistory != null) {
                    queryHistory.setQueryId(queryId);
                }
            }

            // Mark query as successful and add to history
            if (queryHistory != null) {
                long rowsProduced = 0;
                for (final ResultSet rs : results) {
                    rowsProduced += rs.getRowCount();
                }
                queryHistory.markSuccess(LocalDateTime.now(), rowsProduced);
                queryHistoryTracker.addQuery(queryHistory);
            }

            return results;

        } catch (final SqlSyntaxException e) {
            // Mark query as failed and add to history
            if (queryHistory != null) {
                // Generate query ID for failed query
                String queryId = resultCache.generateQueryId(sql);
                queryHistory.setQueryId(queryId);
                queryHistory.markFailed(LocalDateTime.now(), e.getMessage());
                queryHistoryTracker.addQuery(queryHistory);
            }
            // Re-throw syntax exceptions without wrapping (already logged)
            throw e;
        } catch (final SecurityException e) {
            // Mark query as failed and add to history
            if (queryHistory != null) {
                // Generate query ID for failed query
                String queryId = resultCache.generateQueryId(sql);
                queryHistory.setQueryId(queryId);
                queryHistory.markFailed(LocalDateTime.now(), e.getMessage());
                queryHistoryTracker.addQuery(queryHistory);
            }
            // Re-throw security exceptions without wrapping
            throw e;
        } catch (final ProceduralException e) {
            // Mark query as failed and add to history
            if (queryHistory != null) {
                // Generate query ID for failed query
                String queryId = resultCache.generateQueryId(sql);
                queryHistory.setQueryId(queryId);
                queryHistory.markFailed(LocalDateTime.now(), e.getMessage());
                queryHistoryTracker.addQuery(queryHistory);
            }
            // Re-throw procedural exceptions without wrapping
            throw e;
        } catch (final Exception e) {
            // Mark query as failed and add to history
            if (queryHistory != null) {
                // Generate query ID for failed query
                String queryId = resultCache.generateQueryId(sql);
                queryHistory.setQueryId(queryId);
                queryHistory.markFailed(LocalDateTime.now(), e.getMessage());
                queryHistoryTracker.addQuery(queryHistory);
            }
            logger.error("Error executing SQL: {}", sql, e);
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw new RuntimeException("Failed to execute SQL: " + e.getMessage(), e);
        }
    }

    // ==================== CONTEXT-BASED EXECUTION METHODS ====================
    // These methods are called from the visitor with parsed ANTLR contexts

    /**
     * Execute INSERT from parsed context
     */
    public Object executeInsertFromContext(final FrostlakeParser.InsertStatementContext ctx) {
        return insertExecutor.executeInsertFromContext(ctx);
    }

    /** Snowflake-style DML result: a single row carrying the affected-row count. */
    ResultSet dmlCountResult(final String columnName, final long count) {
        final List<ResultSetColumn> columns = new ArrayList<>();
        columns.add(new ResultSetColumn(columnName, NumericType.NUMBER));
        final List<Object> values = new ArrayList<>();
        values.add(count);
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(values));
        return new ResultSet(columns, rows);
    }

    /**
     * The Snowflake UPDATE result shape: TWO columns — "number of rows updated" and "number of
     * multi-joined rows updated". Consumers classify DML results by column count (the vendor stats
     * collector matches UPDATE only as a two-column result), so the single-column form silently
     * dropped every UPDATE from its statistics. The engine does not track multi-join duplicates;
     * that column is always 0.
     */
    ResultSet updateCountResult(final long updated) {
        final List<ResultSetColumn> columns = new ArrayList<>();
        columns.add(new ResultSetColumn("number of rows updated", NumericType.NUMBER));
        columns.add(new ResultSetColumn("number of multi-joined rows updated", NumericType.NUMBER));
        final List<Object> values = new ArrayList<>();
        values.add(updated);
        values.add(0L);
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(values));
        return new ResultSet(columns, rows);
    }

    /** Resolve a per-column INSERT value: the provided value, else auto-increment, else the column DEFAULT, else null. */
    Object insertColumnValue(final TableColumn col, final String fullyQualifiedName, final Object provided) {
        Object value = provided;
        if (value == null && col.isAutoIncrement()) {
            value = storageEngine.getTableStorage(fullyQualifiedName).getNextAutoIncrementValue();
        }
        if (value == null && col.getDefaultValue() != null) {
            value = evaluateDefaultValue(col.getDefaultValue());
        }
        return value;
    }

    /**
     * Snowflake multi-table INSERT — route each row of the subquery to one or more target tables.
     * INSERT [OVERWRITE] ALL INTO ... (unconditional), or INSERT [OVERWRITE] {FIRST | ALL}
     * WHEN cond THEN INTO ... [ELSE INTO ...]. FIRST stops at the first matching WHEN; ALL applies every
     * matching WHEN; ELSE applies only when no WHEN matched.
     */
    public Object executeMultiTableInsert(final FrostlakeParser.MultiTableInsertStatementContext ctx) {
        return insertExecutor.executeMultiTableInsert(ctx);
    }

    /**
     * Execute SELECT from parsed context
     */
    public ResultSet executeSelectFromContext(final FrostlakeParser.SelectStatementContext ctx) {
        return executeSelectFromContext(ctx, null);
    }

    /**
     * Execute SELECT with pre-computed CTE results (for use in DML statements)
     */
    ResultSet executeSelectFromContextWithCTEs(final FrostlakeParser.SelectStatementContext ctx,
                                                       final Map<String, Object> lateralContext,
                                                       final Map<String, ResultSet> cteResults) {
        try {
            // If CTEs are already computed (from parent DML statement), use them
            // Otherwise, compute them from the SELECT statement's WITH clause
            Map<String, ResultSet> allCTEs = cteResults;
            if (ctx.withClause() != null) {
                Map<String, ResultSet> selectCTEs = executeCTEs(ctx.withClause(), lateralContext, cteResults);
                if (allCTEs == null) {
                    allCTEs = selectCTEs;
                } else {
                    // Merge CTEs (SELECT-level CTEs override parent-level CTEs with same name)
                    allCTEs = new HashMap<>(allCTEs);
                    allCTEs.putAll(selectCTEs);
                }
            }

            // Expose the (possibly merged) CTEs so subqueries in this query's clauses can resolve them.
            final Map<String, ResultSet> savedCteContext = currentCteContext;
            if (allCTEs != null) {
                currentCteContext = allCTEs;
            }
            try {
                // Handle set operators (UNION, INTERSECT, EXCEPT)
                if (ctx.selectOperand().size() > 1) {
                    return executeSetOperations(ctx, lateralContext, allCTEs);
                }

                // Single SELECT clause - execute normally
                FrostlakeParser.SelectOperandContext singleOp = ctx.selectOperand().get(0);
                if (singleOp.selectStatement() != null) {
                    return executeSelectFromContext(singleOp.selectStatement(), lateralContext);
                }
                return executeSingleSelect(ctx, singleOp.selectClause(), lateralContext, allCTEs);
            } finally {
                currentCteContext = savedCteContext;
            }
        } catch (final SecurityException e) {
            // Re-throw security exceptions as-is
            throw e;
        } catch (final Exception e) {
            // getOriginalText, not getText: ANTLR getText concatenates tokens without whitespace,
            // which rendered every logged statement as an unreadable single word.
            logger.error("Error executing SQL: {}", getOriginalText(ctx), e);
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw new RuntimeException("Failed to execute SELECT: " + e.getMessage(), e);
        }
    }

    /**
     * Execute SELECT from parsed context with LATERAL context support
     */
    private ResultSet executeSelectFromContext(final FrostlakeParser.SelectStatementContext ctx, final Map<String, Object> lateralContext) {
        try {
            // Handle CTEs (WITH clause)
            Map<String, ResultSet> cteResults = null;
            if (ctx.withClause() != null) {
                cteResults = executeCTEs(ctx.withClause(), lateralContext);
            }

            // Expose the WITH-clause CTEs as the current CTE context so subqueries in this query's clauses
            // (e.g. WHERE NOT EXISTS (SELECT … FROM <cte> …)) can resolve them, not just the top-level FROM.
            // Saved/restored so nested queries and per-row correlated re-entry keep the correct context.
            final Map<String, ResultSet> savedCteContext = currentCteContext;
            if (cteResults != null) {
                currentCteContext = cteResults;
            }
            try {
                // Handle set operators (UNION, INTERSECT, EXCEPT)
                if (ctx.selectOperand().size() > 1) {
                    return executeSetOperations(ctx, lateralContext, cteResults);
                }

                // Single SELECT clause - execute normally
                FrostlakeParser.SelectOperandContext singleOp2 = ctx.selectOperand().get(0);
                if (singleOp2.selectStatement() != null) {
                    return executeSelectFromContext(singleOp2.selectStatement(), lateralContext);
                }
                return executeSingleSelect(ctx, singleOp2.selectClause(), lateralContext, cteResults);
            } finally {
                currentCteContext = savedCteContext;
            }
        } catch (final SecurityException e) {
            // Re-throw security exceptions as-is
            throw e;
        } catch (final Exception e) {
            // getOriginalText, not getText: ANTLR getText concatenates tokens without whitespace,
            // which rendered every logged statement as an unreadable single word.
            logger.error("Error executing SQL: {}", getOriginalText(ctx), e);
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw new RuntimeException("Failed to execute SELECT: " + e.getMessage(), e);
        }
    }

    /**
     * Execute CTEs defined in WITH clause
     * CTEs are executed sequentially, and each CTE can reference earlier CTEs
     */
    Map<String, ResultSet> executeCTEs(final FrostlakeParser.WithClauseContext withCtx, final Map<String, Object> lateralContext) {
        return executeCTEs(withCtx, lateralContext, null);
    }

    /**
     * As {@link #executeCTEs(FrostlakeParser.WithClauseContext, Map)}, seeded with the ENCLOSING
     * scope's CTEs: a nested WITH inside a CTE body may reference its parent's siblings (Snowflake
     * scoping) — {@code WITH outputs AS (...), x AS (WITH cleaned AS (... FROM outputs) ...)}.
     * Executing the inner definitions with an empty map made every such reference fail with
     * "Table does not exist". Inner names shadow outer ones, as in standard scoping.
     */
    Map<String, ResultSet> executeCTEs(final FrostlakeParser.WithClauseContext withCtx,
                                       final Map<String, Object> lateralContext,
                                       final Map<String, ResultSet> outerCtes) {
        Map<String, ResultSet> cteResults = outerCtes == null ? new HashMap<>() : new HashMap<>(outerCtes);

        for (final FrostlakeParser.CteDefinitionContext cteCtx : withCtx.cteDefinition()) {
            String cteName = cteCtx.identifier().getText().toUpperCase();
            ResultSet cteResult;

            // Snowflake's RECURSIVE keyword is OPTIONAL: a CTE that references its own name as a
            // table source recurses with or without it, so detection is by the parse tree alone.
            boolean recursive = isRecursiveCte(cteCtx, cteName);
            if (recursive) {
                // Column aliases are applied inside executeRecursiveCte (to the anchor)
                // so the recursive step can reference named columns
                cteResult = executeRecursiveCte(cteCtx, cteName, lateralContext, cteResults);
            } else {
                cteResult = executeSelectFromContextWithCTEs(cteCtx.selectStatement(), lateralContext, cteResults);
                // Apply column aliases for non-recursive CTEs: WITH cte(a, b) AS (...)
                if (cteCtx.columnListOptional() != null && cteCtx.columnListOptional().identifierList() != null) {
                    cteResult = renameColumns(cteResult, cteCtx.columnListOptional().identifierList());
                }
            }

            cteResults.put(cteName, cteResult);
        }

        return cteResults;
    }

    /**
     * Detect whether a CTE definition is recursive (references its own name in the query body).
     */
    /** Returns true for procedural control-flow statements that should not update LAST_QUERY_ID. */
    private boolean isProceduralControlFlow(final FrostlakeParser.SqlScriptContext tree) {
        if (tree == null || tree.flowChain().isEmpty()) {
            return false;
        }
        final FrostlakeParser.ProceduralStatementContext p =
            tree.flowChain().get(0).statement().get(0).proceduralStatement();
        return p != null && (
               p.letStatement() != null
            || p.declareStatement() != null
            || p.ifStatement() != null
            || p.loopStatement() != null
            || p.whileStatement() != null
            || p.forStatement() != null
            || p.openStatement() != null
            || p.fetchStatement() != null
            || p.closeStatement() != null
            || p.beginEndBlock() != null);
    }

    private boolean isRecursiveCte(final FrostlakeParser.CteDefinitionContext cteCtx, final String cteName) {
        return referencesAsTableSource(cteCtx.selectStatement(), cteName);
    }

    /** True when the subtree contains a FROM-item table source whose (single-segment) name is
     *  {@code cteName} — the parse-tree definition of a self-reference. A text contains() would
     *  misfire constantly with the RECURSIVE keyword optional (a CTE named R matches any body). */
    private boolean referencesAsTableSource(final ParseTree node, final String cteName) {
        if (node instanceof FrostlakeParser.TableSourceContext) {
            final FrostlakeParser.TableSourceContext source = (FrostlakeParser.TableSourceContext) node;
            if (source.qualifiedName() != null) {
                final String[] parts = ParseTreeText.qualifiedNameParts(source.qualifiedName());
                if (parts.length == 1 && parts[0].equalsIgnoreCase(cteName)) {
                    return true;
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (referencesAsTableSource(node.getChild(i), cteName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Execute a recursive CTE using iterative fixpoint evaluation.
     * Snowflake recursive CTE pattern:
     *   WITH RECURSIVE cte AS (
     *       <anchor>              -- non-recursive base case
     *       UNION ALL
     *       SELECT ... FROM cte   -- recursive step referencing itself
     *   )
     */
    private ResultSet executeRecursiveCte(final FrostlakeParser.CteDefinitionContext cteCtx,
                                           final String cteName,
                                           final Map<String, Object> lateralContext,
                                           final Map<String, ResultSet> outerCteResults) {
        FrostlakeParser.SelectStatementContext stmtCtx = cteCtx.selectStatement();
        List<FrostlakeParser.SelectClauseContext> clauses = getSelectClauses(stmtCtx);
        List<FrostlakeParser.SetOperatorContext> operators = stmtCtx.setOperator();

        // Find the UNION ALL boundary that separates anchor from recursive step
        int splitIdx = -1;
        for (int i = 0; i < operators.size(); i++) {
            if (operators.get(i).UNION() != null) {
                splitIdx = i;
                break;
            }
        }

        if (splitIdx < 0) {
            // No UNION — treat as non-recursive
            return executeSelectFromContextWithCTEs(stmtCtx, lateralContext, outerCteResults);
        }

        // Build a merged CTE context that starts with outer CTEs
        Map<String, ResultSet> workingCtes = new HashMap<>(outerCteResults);

        // Execute anchor (all SELECT clauses before the first UNION)
        ResultSet anchor = executeSingleSelect(stmtCtx, clauses.get(splitIdx), lateralContext, workingCtes);

        // Apply CTE column aliases to anchor so the recursive step can reference named columns
        if (cteCtx.columnListOptional() != null && cteCtx.columnListOptional().identifierList() != null) {
            anchor = renameColumns(anchor, cteCtx.columnListOptional().identifierList());
        }

        List<Row> accumulated = new ArrayList<>(anchor.getRows());

        // Register anchor result so the recursive step can reference it
        workingCtes.put(cteName, anchor);

        // Iterate: each step receives only the PREVIOUS step's output (not all accumulated rows)
        boolean hasAll = splitIdx < operators.size() && operators.get(splitIdx).ALL() != null;
        int maxIterations = 1000; // guard against infinite recursion
        ResultSet lastStep = anchor;

        for (int iter = 0; iter < maxIterations; iter++) {
            // The recursive reference gets only the rows from the previous step
            workingCtes.put(cteName, lastStep);

            List<Row> stepRows = new ArrayList<>();
            for (int i = splitIdx + 1; i < clauses.size(); i++) {
                final FrostlakeParser.SelectClauseContext stepClause = clauses.get(i);
                // executeSingleSelect now projects each set-operation operand itself, so the step's SELECT
                // list is already applied here — re-projecting it a second time (as this code used to, to
                // compensate for the old skip-projection-in-UNION behavior) corrupts the recursion.
                final ResultSet stepResult = executeSingleSelect(stmtCtx, stepClause, lateralContext, workingCtes);
                stepRows.addAll(stepResult.getRows());
            }

            if (stepRows.isEmpty()) break; // fixpoint reached

            if (hasAll) {
                accumulated.addAll(stepRows);
            } else {
                accumulated = SetOperations.applyUnion(accumulated, stepRows, false);
            }
            lastStep = new ResultSet(anchor.getColumns(), stepRows);
        }

        return new ResultSet(anchor.getColumns(), accumulated);
    }

    /**
     * Rename columns in a ResultSet according to an explicit column list.
     * Used for: WITH cte(col1, col2) AS (SELECT a, b FROM ...)
     */
    private ResultSet renameColumns(final ResultSet original, final FrostlakeParser.IdentifierListContext idList) {
        List<String> newNames = new ArrayList<>();
        for (final FrostlakeParser.IdentifierContext idCtx : idList.identifier()) {
            newNames.add(getIdentifier(idCtx));
        }
        if (newNames.isEmpty() || newNames.size() != original.getColumns().size()) {
            return original; // mismatch — leave unchanged
        }
        List<ResultSetColumn> newCols = new ArrayList<>();
        for (int i = 0; i < original.getColumns().size(); i++) {
            ResultSetColumn old = original.getColumns().get(i);
            newCols.add(new ResultSetColumn(newNames.get(i), old.getDataType(), old.getTableName()));
        }
        return new ResultSet(newCols, original.getRows());
    }

    /**
     * Execute set operations (UNION, INTERSECT, EXCEPT) on multiple SELECT statements
     */
    private ResultSet executeSelectOperand(final FrostlakeParser.SelectStatementContext stmtCtx,
                                            final FrostlakeParser.SelectOperandContext op,
                                            final Map<String, Object> lateralContext,
                                            final Map<String, ResultSet> cteResults) {
        if (op.selectStatement() != null) {
            return executeSelectFromContext(op.selectStatement(), lateralContext);
        }
        return executeSingleSelect(stmtCtx, op.selectClause(), lateralContext, cteResults);
    }

    private ResultSet executeSetOperations(final FrostlakeParser.SelectStatementContext ctx, final Map<String, Object> lateralContext, final Map<String, ResultSet> cteResults) {
        final List<FrostlakeParser.SelectOperandContext> operands = ctx.selectOperand();
        final List<FrostlakeParser.SetOperatorContext> setOperators = ctx.setOperator();

        final ResultSet firstResult = executeSelectOperand(ctx, operands.get(0), lateralContext, cteResults);
        final int columnCount = firstResult.getColumns().size();

        // Snowflake operator precedence: INTERSECT binds tighter than UNION and EXCEPT/MINUS, which are
        // left-associative among themselves. Two-level fold: first collapse each maximal run of INTERSECTs
        // into a "term", then left-fold the terms with their separating UNION/EXCEPT operators. A plain
        // left-to-right fold would wrongly evaluate "A UNION B INTERSECT C" as "(A UNION B) INTERSECT C".
        // Each term also carries its column layout (its first operand's columns) so a UNION [ALL] BY NAME
        // between terms can align by column name; INTERSECT/EXCEPT stay positional (same-columns) as before.
        final List<List<Row>> terms = new ArrayList<>();
        final List<List<ResultSetColumn>> termColumns = new ArrayList<>();
        final List<FrostlakeParser.SetOperatorContext> termOperators = new ArrayList<>();
        List<Row> currentTerm = new ArrayList<>(firstResult.getRows());
        List<ResultSetColumn> currentTermColumns = firstResult.getColumns();

        for (int i = 1; i < operands.size(); i++) {
            final ResultSet nextResult = executeSelectOperand(ctx, operands.get(i), lateralContext, cteResults);
            final FrostlakeParser.SetOperatorContext operator = setOperators.get(i - 1);
            // ALL applies only to UNION in Snowflake (live-verified error shapes below).
            if (operator.ALL() != null && operator.UNION() == null) {
                throw new RuntimeException("Unsupported feature '"
                    + (operator.INTERSECT() != null ? "INTERSECT ALL" : "MINUS ALL") + "'.");
            }
            // BY NAME aligns columns by name and so allows different column counts; every other operator
            // requires matching column counts.
            if (!isUnionByName(operator) && nextResult.getColumns().size() != columnCount) {
                throw new RuntimeException("Set operation queries must have the same number of columns");
            }
            final boolean hasAll = operator.ALL() != null;
            if (operator.INTERSECT() != null) {
                currentTerm = SetOperations.applyIntersect(currentTerm, nextResult.getRows(), hasAll);
            } else {
                // UNION or EXCEPT/MINUS: close the current INTERSECT term and start a new one.
                terms.add(currentTerm);
                termColumns.add(currentTermColumns);
                termOperators.add(operator);
                currentTerm = new ArrayList<>(nextResult.getRows());
                currentTermColumns = nextResult.getColumns();
            }
        }
        terms.add(currentTerm);
        termColumns.add(currentTermColumns);

        List<Row> allRows = terms.get(0);
        List<ResultSetColumn> accColumns = termColumns.get(0);
        for (int j = 1; j < terms.size(); j++) {
            final FrostlakeParser.SetOperatorContext operator = termOperators.get(j - 1);
            final boolean hasAll = operator.ALL() != null;
            if (operator.UNION() != null) {
                if (isUnionByName(operator)) {
                    // Align both sides to a merged layout (accumulated columns, then the right side's
                    // columns that aren't already present); missing columns become NULL.
                    final List<ResultSetColumn> rightColumns = termColumns.get(j);
                    final List<ResultSetColumn> merged = mergeColumnsByName(accColumns, rightColumns);
                    final List<Row> leftReshaped = reshapeRowsByName(allRows, accColumns, merged);
                    final List<Row> rightReshaped = reshapeRowsByName(terms.get(j), rightColumns, merged);
                    allRows = SetOperations.applyUnion(leftReshaped, rightReshaped, hasAll);
                    accColumns = merged;
                } else {
                    allRows = SetOperations.applyUnion(allRows, terms.get(j), hasAll);
                }
            } else {
                // EXCEPT / MINUS (MINUS is a Snowflake synonym for EXCEPT).
                allRows = SetOperations.applyExcept(allRows, terms.get(j), hasAll);
            }
        }

        // ORDER BY / LIMIT / FETCH apply once to the combined result (statement level).
        if (ctx.orderByClause() != null) {
            allRows = orderByAfterGroupBy(allRows, ctx);
        }
        if (ctx.limitClause() != null) {
            // LIMIT NULL means no limit; its INTEGER_LITERAL list then holds only the OFFSET (if any).
            final boolean unlimited = ctx.limitClause().NULL() != null;
            final List<TerminalNode> limitInts = ctx.limitClause().INTEGER_LITERAL();
            final int limit = unlimited ? Integer.MAX_VALUE : Integer.parseInt(limitInts.get(0).getText());
            int offset = 0;
            if (limitInts.size() > (unlimited ? 0 : 1)) {
                offset = Integer.parseInt(limitInts.get(limitInts.size() - 1).getText());
            }
            final int subListEnd = (int) Math.min((long) offset + (long) limit, (long) allRows.size());
            allRows = allRows.subList(Math.min(offset, allRows.size()), subListEnd);
        }
        if (ctx.fetchClause() != null) {
            final int fetch = Integer.parseInt(ctx.fetchClause().INTEGER_LITERAL().getText());
            allRows = allRows.subList(0, Math.min(fetch, allRows.size()));
        }

        return new ResultSet(accColumns, allRows);
    }

    /**
     * Whether a set operator is {@code UNION [ALL] BY NAME}. Only UNION supports the BY NAME modifier;
     * the identifier after BY must be {@code NAME} (case-insensitive) — anything else is an error.
     */
    private static boolean isUnionByName(final FrostlakeParser.SetOperatorContext op) {
        if (op.UNION() == null || op.BY() == null) {
            return false;
        }
        final String modifier = op.identifier().getText();
        if (!modifier.equalsIgnoreCase("NAME")) {
            throw new RuntimeException("Unsupported UNION modifier 'BY " + modifier + "' (only BY NAME is supported)");
        }
        return true;
    }

    /**
     * The merged column layout for {@code UNION BY NAME}: the left columns in order, followed by any right
     * column whose (case-insensitive) name is not already present. Snowflake fills the absent side with NULL.
     */
    private static List<ResultSetColumn> mergeColumnsByName(final List<ResultSetColumn> left,
                                                            final List<ResultSetColumn> right) {
        final List<ResultSetColumn> merged = new ArrayList<>(left);
        final Set<String> present = new HashSet<>();
        for (final ResultSetColumn c : left) {
            present.add(c.getName().toUpperCase());
        }
        for (final ResultSetColumn c : right) {
            if (present.add(c.getName().toUpperCase())) {
                merged.add(c);
            }
        }
        return merged;
    }

    /**
     * Reshape rows from a {@code source} column layout to a {@code target} layout by matching column names
     * (case-insensitive). A target column with no same-named source column is filled with NULL.
     */
    private static List<Row> reshapeRowsByName(final List<Row> rows, final List<ResultSetColumn> source,
                                               final List<ResultSetColumn> target) {
        final int[] sourceIndexForTarget = new int[target.size()];
        for (int t = 0; t < target.size(); t++) {
            sourceIndexForTarget[t] = -1;
            final String targetName = target.get(t).getName();
            for (int s = 0; s < source.size(); s++) {
                if (source.get(s).getName().equalsIgnoreCase(targetName)) {
                    sourceIndexForTarget[t] = s;
                    break;
                }
            }
        }
        final List<Row> reshaped = new ArrayList<>(rows.size());
        for (final Row row : rows) {
            final List<Object> values = new ArrayList<>(target.size());
            for (int t = 0; t < target.size(); t++) {
                values.add(sourceIndexForTarget[t] >= 0 ? row.getValue(sourceIndexForTarget[t]) : null);
            }
            reshaped.add(new Row(values));
        }
        return reshaped;
    }

    /**
     * Execute a single SELECT clause (helper for both normal and UNION queries)
     */
    private ResultSet executeSingleSelect(final FrostlakeParser.SelectStatementContext stmtCtx,
                                         final FrostlakeParser.SelectClauseContext ctx,
                                         final Map<String, Object> lateralContext,
                                         final Map<String, ResultSet> cteResults) {
        try {
            FrostlakeParser.TableExpressionContext tableExpr = ctx.tableExpression();

            // Handle SELECT without FROM clause (e.g., SELECT 1, SELECT {'key': 'value'})
            if (tableExpr == null) {
                return executeSelectWithoutFrom(ctx);
            }

            // Get the first table reference
            List<FrostlakeParser.TableReferenceContext> allTableRefs = tableExpr.tableReference();
            if (allTableRefs.isEmpty()) {
                throw new RuntimeException("No table reference found in SELECT");
            }
            // A parenthesized FROM join — FROM ( a JOIN b ON c ) — is pure grouping: identical to
            // FROM a JOIN b ON c and, unlike a derived-table subquery, keeps the inner aliases (a, b)
            // visible to the outer WHERE/SELECT. Flatten any such reference by splicing its inner
            // tableReferences/joinClauses inline so the ordinary join path below handles it unchanged.
            List<FrostlakeParser.JoinClauseContext> allJoins = tableExpr.joinClause();
            if (hasParenthesizedJoin(allTableRefs)) {
                final List<FrostlakeParser.TableReferenceContext> flatRefs = new ArrayList<>();
                final List<FrostlakeParser.JoinClauseContext> innerJoins = new ArrayList<>();
                for (final FrostlakeParser.TableReferenceContext ref : allTableRefs) {
                    flattenParenthesizedJoin(ref, flatRefs, innerJoins);
                }
                innerJoins.addAll(allJoins);   // inner (grouped) joins first, then the outer explicit joins
                allTableRefs = flatRefs;
                allJoins = innerJoins;
            }
            FrostlakeParser.TableReferenceContext firstTableRef = allTableRefs.get(0);

            // Execute the first table to get initial rows and table metadata
            TableData tableData = executeTableReference(firstTableRef, null, cteResults);
            List<Row> rows = tableData.rows;
            Table table = tableData.table;
            String tableName = table.getName();

            // Check SELECT permission on first table (if it's not a subquery)
            if (securityManager != null && firstTableRef.tableSource().qualifiedName() != null) {
                String qualifiedTableName = getQualifiedName(firstTableRef.tableSource().qualifiedName());
                securityManager.checkPermission(Privilege.SELECT, SecurableObjectType.TABLE, qualifiedTableName);
            }

            // Track table aliases and tables for WHERE clause evaluation
            Map<String, Table> aliasToTable = new HashMap<>();
            aliasToTable.put(tableData.alias != null ? tableData.alias : table.getName(), table);
            List<Table> allTables = new ArrayList<>();
            allTables.add(table);

            // Oracle legacy (+) outer join — FROM a, b WHERE a.k = b.k(+) ...  ≡  a LEFT JOIN b ON a.k=b.k
            // WHERE <non-(+) predicates>. Detected at the parse-tree level for the common two-comma-table,
            // no-explicit-join form; the (+) side is the null-supplying (right) side of the LEFT join, and
            // conjuncts carrying a (+) become the ON condition while the rest stay in the (residual) WHERE.
            boolean plusHandled = false;
            String plusResidualWhere = null;
            final FrostlakeParser.WhereClauseContext plusWhere = getWhereClause(ctx);
            if (allTableRefs.size() == 2 && allJoins.isEmpty()
                    && plusWhere != null && containsOuterJoinMarker(plusWhere.booleanExpr())) {
                final Set<String> plusAliases = new HashSet<>();
                outerJoinMarkedAliases(plusWhere.booleanExpr(), plusAliases);
                final String alias0 = (tableData.alias != null ? tableData.alias : table.getName()).toUpperCase();
                final TableData rightData0 = executeTableReference(allTableRefs.get(1), null, cteResults);
                final String alias1 = (rightData0.alias != null ? rightData0.alias : rightData0.table.getName()).toUpperCase();
                final boolean plusOn0 = plusAliases.contains(alias0);
                final boolean plusOn1 = plusAliases.contains(alias1);
                if (plusOn0 ^ plusOn1) {   // exactly one side is null-supplied — the standard Oracle form
                    final List<FrostlakeParser.BooleanExprContext> conjuncts = new ArrayList<>();
                    collectConjuncts(plusWhere.booleanExpr(), conjuncts);
                    final List<String> onParts = new ArrayList<>();
                    final List<String> residualParts = new ArrayList<>();
                    for (final FrostlakeParser.BooleanExprContext cj : conjuncts) {
                        (containsOuterJoinMarker(cj) ? onParts : residualParts).add(getOriginalText(cj));
                    }
                    plusResidualWhere = residualParts.isEmpty() ? null : String.join(" AND ", residualParts);
                    final Table leftTable = plusOn1 ? table : rightData0.table;   // preserved side
                    final List<Row> leftRows = plusOn1 ? rows : rightData0.rows;
                    final String leftAlias = plusOn1 ? alias0 : alias1;
                    final Table rightTable = plusOn1 ? rightData0.table : table;   // (+) / null-supplied side
                    final List<Row> rightRows = plusOn1 ? rightData0.rows : rows;
                    final String rightAlias = plusOn1 ? alias1 : alias0;
                    aliasToTable.clear();
                    aliasToTable.put(leftAlias, leftTable);
                    aliasToTable.put(rightAlias, rightTable);
                    allTables.clear();
                    allTables.add(leftTable);
                    allTables.add(rightTable);
                    rows = conditionJoin(leftRows, leftTable, rightRows, rightTable, JoinType.LEFT,
                        String.join(" AND ", onParts), aliasToTable, allTables);
                    table = mergeTableMetadata(leftTable, rightTable);
                    plusHandled = true;
                }
            }

            // A LATERAL item written after an explicit JOIN — FROM a JOIN b USING (k), LATERAL FLATTEN(b.col)
            // — may correlate to the JOINED table, but comma items are applied before the joins here, so it
            // would be evaluated while only the first table is in scope ("Column not found: b.col"). Collect
            // those and apply them once the joins have brought their tables into scope.
            final List<FrostlakeParser.TableReferenceContext> deferredLaterals = new ArrayList<>();

            // Process comma-separated tables (CROSS JOIN or LATERAL)
            // If there are multiple table references, it's comma-separated syntax
            if (!plusHandled && allTableRefs.size() > 1) {
                for (int i = 1; i < allTableRefs.size(); i++) {
                    FrostlakeParser.TableReferenceContext rightTableRef = allTableRefs.get(i);
                    boolean isLateral = rightTableRef.LATERAL() != null || isImplicitlyLateral(rightTableRef);

                    if (isLateral) {
                        // Defer only a LATERAL written AFTER the first explicit join (FROM a JOIN b
                        // USING (k), LATERAL FLATTEN(b.col)) — it may correlate to a joined table not in
                        // scope yet. A lateral written BEFORE the joins (FROM d, TABLE(FLATTEN(tag_ids)) t
                        // INNER JOIN x ON x.k = t.value::VARCHAR) must be applied here, in textual order,
                        // so the join's ON condition can see its output columns.
                        if (!allJoins.isEmpty()
                                && rightTableRef.getStart().getTokenIndex() > allJoins.get(0).getStart().getTokenIndex()) {
                            deferredLaterals.add(rightTableRef);
                            continue;
                        }
                        // LATERAL with comma syntax: evaluate right side for each left side row
                        TableData lateralData = executeLateralJoin(rows, table, rightTableRef, null, aliasToTable, allTables, ctx);
                        rows = lateralData.rows;
                        // Update table metadata after lateral join
                        table = mergeTableMetadata(table, lateralData.table);
                        aliasToTable.put(lateralData.alias != null ? lateralData.alias : lateralData.table.getName(),
                                        lateralData.table);
                        allTables.add(lateralData.table);
                    } else {
                        // Regular comma-separated tables are CROSS JOIN
                        TableData rightData = executeTableReference(rightTableRef, null, cteResults);

                        // Self-join guard: a distinct Table instance so each alias resolves to its own side.
                        final Table rightJoinTable = distinctJoinTable(rightData.table, allTables);

                        // Execute CROSS JOIN using operator
                        OperatorContext opContext = OperatorContext.builder()
                            .table(table)
                            .functionRegistry(functionRegistry).queryExecutor(this)
                            .build();
                        JoinOperator joinOp = JoinOperator.cross(table, rightJoinTable, rightData.rows);
                        rows = joinOp.execute(rows, opContext);

                        // Update table metadata
                        table = mergeTableMetadata(table, rightJoinTable);
                        aliasToTable.put(rightData.alias != null ? rightData.alias : rightJoinTable.getName(),
                                        rightJoinTable);
                        allTables.add(rightJoinTable);
                    }
                }
            }

            // Process explicit joins
            for (final FrostlakeParser.JoinClauseContext joinCtx : allJoins) {
                FrostlakeParser.TableReferenceContext rightTableRef = joinCtx.tableReference();
                // A table function joined WITHOUT the LATERAL keyword is still implicitly lateral in
                // Snowflake: FROM t JOIN TABLE(FLATTEN(t_col:path)) references the left row's columns
                // (vendor loaders use exactly this shape, with no ON clause). Only the CONDITION-LESS
                // form goes lateral — the lateral path performs no ON filtering, so a non-correlated
                // JOIN TABLE(GENERATOR(...)) g ON t.id = g.seq must keep the regular join path.
                if (isImplicitlyLateral(rightTableRef) && joinCtx.ON() != null) {
                    // Live-verified Snowflake restriction on lateral table FUNCTIONS — even ON TRUE is
                    // rejected. A lateral SUBQUERY joined with ON stays valid.
                    throw new RuntimeException("Unsupported feature 'lateral table function called with "
                        + "OUTER JOIN syntax or a join predicate (ON clause)'.");
                }
                boolean isLateral = joinCtx.LATERAL() != null
                    || (isImplicitlyLateral(rightTableRef) && joinCtx.ON() == null && joinCtx.USING() == null);

                if (isLateral) {
                    // LATERAL join: evaluate right side for each left side row
                    TableData lateralData = executeLateralJoin(rows, table, rightTableRef, joinCtx, aliasToTable, allTables, ctx);
                    rows = lateralData.rows;
                    // Update table metadata after lateral join
                    table = mergeTableMetadata(table, lateralData.table);
                    aliasToTable.put(lateralData.alias != null ? lateralData.alias : lateralData.table.getName(),
                                    lateralData.table);
                    allTables.add(lateralData.table);
                } else if (isParenthesizedJoin(rightTableRef.tableSource())) {
                    // A join group on the RIGHT side — x JOIN (a JOIN b ON ...) ON ... : execute the
                    // inner chain first; its aliases join the outer scope so the outer ON can see them.
                    final TableData groupData =
                        executeJoinGroup(rightTableRef.tableSource(), aliasToTable, allTables, ctx, cteResults);
                    rows = applyJoin(rows, table, groupData.rows, groupData.table, joinCtx, aliasToTable, allTables, ctx);
                    table = mergeTableMetadata(table, groupData.table,
                        usingJoinColumnNames(joinCtx, table, groupData.table));
                } else {
                    // Regular join
                    TableData rightData = executeTableReference(rightTableRef, null, cteResults);

                    // Self-join guard: a distinct Table instance so each alias resolves to its own side.
                    final Table rightJoinTable = distinctJoinTable(rightData.table, allTables);

                    // Add right table to maps BEFORE executing join so condition evaluation can resolve columns
                    aliasToTable.put(rightData.alias != null ? rightData.alias : rightJoinTable.getName(),
                                    rightJoinTable);
                    allTables.add(rightJoinTable);

                    // Execute JOIN using operator
                    rows = applyJoin(rows, table, rightData.rows, rightJoinTable, joinCtx, aliasToTable, allTables, ctx);

                    // Update table metadata to include both tables
                    table = mergeTableMetadata(table, rightJoinTable,
                        usingJoinColumnNames(joinCtx, table, rightJoinTable));
                }
            }

            // LATERAL items written after the joins, now that the joined tables are in scope.
            for (final FrostlakeParser.TableReferenceContext lateralRef : deferredLaterals) {
                final TableData lateralData = executeLateralJoin(rows, table, lateralRef, null, aliasToTable, allTables, ctx);
                rows = lateralData.rows;
                table = mergeTableMetadata(table, lateralData.table);
                aliasToTable.put(lateralData.alias != null ? lateralData.alias : lateralData.table.getName(),
                                lateralData.table);
                allTables.add(lateralData.table);
            }

            // Apply Row Access Policy (RLS) before WHERE — filters rows the user can't see
            rows = applyRowAccessPolicy(rows, table);

            // Apply WHERE clause. For a simple single-table SELECT ... LIMIT (no join / group / order /
            // distinct / window / aggregate / pivot), stream scan -> WHERE -> LIMIT so the predicate is
            // evaluated only until LIMIT is satisfied (LimitRowStream stops pulling) and the full filtered
            // set is never materialized. Projection is 1:1, so it runs afterwards on the <= LIMIT rows.
            FrostlakeParser.WhereClauseContext whereCtx = getWhereClause(ctx);
            // When a (+) outer join consumed the WHERE, only its residual (non-(+)) predicates remain here
            // (the (+) join predicates already became the join's ON condition).
            String whereExpr = plusHandled
                ? plusResidualWhere
                : (whereCtx != null ? getOriginalText(whereCtx.booleanExpr()) : null);
            boolean limitApplied = false;
            // A PIVOT/UNPIVOT reshapes the relation, and in Snowflake WHERE filters the pivot's OUTPUT (it may
            // reference the pivoted columns), so defer it until after the pivot below. isStreamableSelect
            // already excludes a pivot, so the streaming branch is unaffected.
            final boolean hasPivotSource =
                firstTableRef.pivotClause() != null || firstTableRef.unpivotClause() != null;
            if (lateralContext == null
                    && isStreamableSelect(ctx, stmtCtx, firstTableRef, allTables)) {
                rows = streamFilterLimit(rows, table, whereExpr, stmtCtx.limitClause());
                limitApplied = true;
            } else if (whereExpr != null && !hasPivotSource) {
                rows = applyWhereClause(rows, table, whereExpr, lateralContext, aliasToTable, allTables, tableExpr, ctx);
            }

            // Check for PIVOT/UNPIVOT on the first table reference (they follow the source + optional alias).
            // The pivoted relation becomes this query's SOURCE, so everything after this point — the SELECT
            // list, GROUP BY / HAVING, QUALIFY, DISTINCT, ORDER BY, TOP / LIMIT / FETCH — applies to it, as in
            // Snowflake. Returning the pivoted ResultSet straight from here silently DISCARDED all of them:
            // `SELECT c FROM t PIVOT(…)` handed back the whole pivoted relation, and a derived column such as
            // `CASE … END AS record_type` never existed for an outer query to reference.
            FrostlakeParser.TableSourceContext firstSource = firstTableRef.tableSource();
            if (hasPivotSource) {
                final ResultSet pivoted = renamePivotColumns(
                    firstTableRef.pivotClause() != null
                        ? executePivot(ctx, table, rows, firstTableRef.pivotClause())
                        : executeUnpivot(ctx, table, rows, firstTableRef.unpivotClause()),
                    firstTableRef.pivotAlias());
                final String pivotName = firstTableRef.pivotAlias() != null
                    ? getIdentifier(firstTableRef.pivotAlias().identifier())
                    : table.getName();
                table = resultSetToTable(pivoted, pivotName);
                rows = pivoted.getRows();
                // Re-point column resolution at the pivot output; its alias (PIVOT(…) AS p) is the only name
                // by which a qualified reference such as p.region can reach it.
                aliasToTable.clear();
                aliasToTable.put(pivotName, table);
                allTables.clear();
                allTables.add(table);
                if (whereExpr != null) {
                    rows = applyWhereClause(rows, table, whereExpr, lateralContext, aliasToTable, allTables,
                        tableExpr, ctx);
                }
            }

            // Check if SELECT contains aggregate functions or window functions. A present HAVING also makes
            // this an aggregate query even when no SELECT item is one (SELECT 'X' FROM t HAVING COUNT(*) > 1):
            // Snowflake forms a single implicit group, so such a query returns at most one row. Detecting
            // aggregates from the SELECT list alone left it a plain row scan AND then skipped applyHaving
            // entirely (see the guard below), so every input row was returned unfiltered.
            boolean hasAggregates = hasAggregateFunction(ctx) || ctx.havingClause() != null;
            boolean hasWindowFunctions = hasWindowFunction(ctx);

            // Apply GROUP BY or handle implicit grouping for aggregates. Capture each output row's source
            // group so HAVING can evaluate aggregates that are not SELECT items over the original group.
            boolean hasGroupBy = ctx.groupByClause() != null;
            final List<List<Row>> havingGroupRows = new ArrayList<>();
            if (hasGroupBy) {
                rows = applyGroupBy(rows, table, ctx, aliasToTable, allTables, havingGroupRows);
            } else if (hasAggregates) {
                // No GROUP BY but has aggregates - treat entire result as one group
                rows = applyImplicitGroupBy(rows, table, ctx, aliasToTable, allTables, havingGroupRows);
            }

            // Map each output row instance to its source group, so a later ORDER BY key that is a grouped
            // column / aggregate NOT in the SELECT can be computed fresh over the group. Identity-keyed so it
            // survives HAVING filtering (the same Row instances flow through).
            final Map<Row, List<Row>> orderRowToGroup = new IdentityHashMap<>();
            for (int gi = 0; gi < rows.size() && gi < havingGroupRows.size(); gi++) {
                orderRowToGroup.put(rows.get(gi), havingGroupRows.get(gi));
            }

            // Apply HAVING clause (only after GROUP BY)
            if (ctx.havingClause() != null && (hasGroupBy || hasAggregates)) {
                rows = applyHaving(rows, ctx, table, aliasToTable, allTables, havingGroupRows);
            }

            // Compute window functions if present and store results. Over a JOIN, publish the alias context
            // so the window stage's per-key expression evaluation resolves alias-qualified columns (see
            // windowAliasToTableStack); a single-table query needs no alias context and is left untouched.
            Map<Integer, Map<Integer, Object>> windowFunctionResults = new HashMap<>();
            // ORDER BY keys a window-function query references but does NOT select: the projection in
            // addWindowFunctionsToRows drops the FROM columns, so precompute those keys THERE (the FROM
            // columns are still on the source row), keyed by the projected row so they survive QUALIFY, and
            // resolve ORDER BY from them below.
            final List<String> windowOrderKeyExprs = new ArrayList<>();
            final Map<String, Integer> windowOrderKeyIndex = new HashMap<>();
            final Map<Row, Object[]> windowOrderKeyValues = new IdentityHashMap<>();
            if (hasWindowFunctions && stmtCtx.orderByClause() != null && stmtCtx.selectOperand().size() == 1) {
                for (final FrostlakeParser.OrderItemContext oi : orderByExecutor.unmatchedOrderItems(stmtCtx)) {
                    final String txt = getOriginalText(oi.expression());
                    if (!windowOrderKeyIndex.containsKey(txt)) {
                        windowOrderKeyIndex.put(txt, windowOrderKeyExprs.size());
                        windowOrderKeyExprs.add(txt);
                    }
                }
            }
            final boolean pushJoinAliasContext =
                (hasWindowFunctions || ctx.qualifyClause() != null) && allTables.size() > 1;
            if (pushJoinAliasContext) {
                windowAliasToTableStack.get().push(aliasToTable);
                windowAllTablesStack.get().push(allTables);
            }
            try {
                // After GROUP BY / implicit aggregation the rows are ALREADY in SELECT-list shape, so the
                // window stage must resolve names against the PROJECTED columns (a window's ORDER BY commonly
                // references an aggregate's alias — ROW_NUMBER() OVER (ORDER BY total DESC)), and the
                // non-window values must be taken positionally. Re-evaluating each item against the BASE table
                // instead sent aggregate text (ARRAY_AGG(…)) to the scalar evaluator — "Unknown function".
                final boolean rowsAreProjected = hasGroupBy || hasAggregates;
                final Table windowShape = rowsAreProjected && hasWindowFunctions
                    ? projectedShapeTable(ctx, table) : table;
                if (hasWindowFunctions) {
                    windowFunctionResults = windowEvaluator.computeWindowFunctions(rows, ctx, windowShape, rowsAreProjected);
                    if (ctx.qualifyClause() != null && !rowsAreProjected) {
                        // QUALIFY evaluates over the PRE-projection rows: its predicate and its inline
                        // windows may reference FROM columns that are NOT in the SELECT list (or partition
                        // by expressions over them). Reshaping first handed the select-shape rows to the
                        // base-table resolver, so such a key read the wrong slot positionally and every
                        // row landed in one NULL partition. Filter first, then reshape only the
                        // survivors, remapping their precomputed window values to the new indices.
                        final List<Row> keptRows = applyQualify(rows, ctx, windowFunctionResults, table, true);
                        final Map<Row, Integer> oldIndexByRow = new IdentityHashMap<>();
                        for (int i = 0; i < rows.size(); i++) {
                            oldIndexByRow.put(rows.get(i), i);
                        }
                        final Map<Integer, Map<Integer, Object>> remapped = new HashMap<>();
                        for (int i = 0; i < keptRows.size(); i++) {
                            final Integer oldIdx = oldIndexByRow.get(keptRows.get(i));
                            if (oldIdx != null && windowFunctionResults.containsKey(oldIdx)) {
                                remapped.put(i, windowFunctionResults.get(oldIdx));
                            }
                        }
                        rows = keptRows;
                        windowFunctionResults = remapped;
                    }
                    // Add window function values to rows (also precomputes any not-selected ORDER BY keys).
                    rows = addWindowFunctionsToRows(rows, windowFunctionResults, ctx, windowShape,
                        rowsAreProjected, windowOrderKeyExprs, windowOrderKeyValues);
                }

                // Apply QUALIFY clause (filters on window functions) for the flows whose rows are already
                // in SELECT-list shape at this point (grouped windows) or never reshaped (no windows).
                if (ctx.qualifyClause() != null && (!hasWindowFunctions || rowsAreProjected)) {
                    rows = applyQualify(rows, ctx, windowFunctionResults,
                        rowsAreProjected && hasWindowFunctions ? windowShape : table, !rowsAreProjected);
                }
            } finally {
                if (pushJoinAliasContext) {
                    windowAliasToTableStack.get().pop();
                    windowAllTablesStack.get().pop();
                }
            }

            // A set-operation operand projects its own SELECT list HERE, so the operation combines the
            // projected columns rather than the raw base rows; ORDER BY / LIMIT / FETCH for a set
            // operation are applied once to the combined result at the statement level.
            boolean isPartOfUnion = stmtCtx.selectOperand().size() > 1;
            // Projection is needed unless the SELECT list is a bare star or grouping/window functions have
            // already reshaped the rows into SELECT-list form.
            boolean needsProjection = !isSimpleStar(ctx) && !hasWindowFunctions && !hasGroupBy && !hasAggregates;
            if (isPartOfUnion) {
                if (needsProjection) {
                    rows = applyProjection(rows, table, ctx, aliasToTable, allTables);
                }
            } else {
                boolean orderByApplied = false;

                // If we need projection and there's ORDER BY, apply ORDER BY first (before projection)
                // so that ORDER BY can reference all columns from the FROM clause
                if (needsProjection && stmtCtx.orderByClause() != null) {
                    rows = orderBy(rows, table, stmtCtx, aliasToTable, allTables);
                    orderByApplied = true;
                }

                // Apply projection
                if (needsProjection) {
                    rows = applyProjection(rows, table, ctx, aliasToTable, allTables);
                }

                // Apply ORDER BY after GROUP BY/aggregates/window functions if not already applied
                if (!orderByApplied && stmtCtx.orderByClause() != null) {
                    // If we've done GROUP BY, aggregates, or window functions,
                    // the row structure matches the SELECT list, not the original table
                    if (hasGroupBy || hasAggregates || hasWindowFunctions) {
                        // Let ORDER BY reference a key that is NOT in the SELECT list (valid in Snowflake):
                        //   • pure GROUP BY / aggregate → a grouped column / aggregate, computed over the
                        //     row's group (the identity map still holds — no window recreated the rows);
                        //   • window function → a FROM column dropped by projection, precomputed during the
                        //     window projection (windowOrderKeyValues) and looked up by projected-row identity.
                        final GroupOrderKeyResolver orderResolver;
                        if ((hasGroupBy || hasAggregates) && !hasWindowFunctions) {
                            orderResolver = newGroupOrderResolver(rows, orderRowToGroup, table, aliasToTable, allTables);
                        } else if (hasWindowFunctions && !hasGroupBy && !hasAggregates) {
                            orderResolver = newWindowOrderResolver(rows, windowOrderKeyValues, windowOrderKeyIndex);
                        } else {
                            orderResolver = null;
                        }
                        rows = orderByAfterGroupBy(rows, stmtCtx, orderResolver);
                    } else {
                        rows = orderBy(rows, table, stmtCtx, aliasToTable, allTables);
                    }
                }

                // Apply TOP clause (after ORDER BY)
                if (ctx.topClause() != null) {
                    int top = Integer.parseInt(ctx.topClause().INTEGER_LITERAL().getText());
                    rows = rows.subList(0, Math.min(top, rows.size()));
                }

                // Apply LIMIT with optional OFFSET
                if (!limitApplied && stmtCtx.limitClause() != null) {
                    int offset = 0;
                    int limit;

                    // LIMIT NULL means no limit; the INTEGER_LITERAL list then holds only the OFFSET.
                    final boolean unlimited = stmtCtx.limitClause().NULL() != null;
                    List<TerminalNode> intLiterals = stmtCtx.limitClause().INTEGER_LITERAL();
                    limit = unlimited ? Integer.MAX_VALUE : Integer.parseInt(intLiterals.get(0).getText());
                    if (intLiterals.size() > (unlimited ? 0 : 1)) {
                        offset = Integer.parseInt(intLiterals.get(intLiterals.size() - 1).getText());
                    }

                    // Apply offset and limit
                    int start = Math.min(offset, rows.size());
                    int end = (int) Math.min((long) start + (long) limit, (long) rows.size());
                    rows = rows.subList(start, end);
                }

                // Apply FETCH FIRST n ROWS ONLY
                if (stmtCtx.fetchClause() != null) {
                    int fetch = Integer.parseInt(stmtCtx.fetchClause().INTEGER_LITERAL().getText());
                    rows = rows.subList(0, Math.min(fetch, rows.size()));
                }
            }

            // Build result columns
            List<ResultSetColumn> columns = new ArrayList<>();
            // Handle select items - iterate through all items, expanding STAR if present
            for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
                if (SelectItemAccessors.isStarItem(item)) {
                    // Expand STAR (honoring EXCLUDE/RENAME/REPLACE/ILIKE) to its effective columns.
                    for (final StarColumn sc : expandStarColumns(item, table.getColumns(), "")) {
                        columns.add(new ResultSetColumn(sc.getOutputName(), sc.getDataType(), tableName));
                    }
                } else if (SelectItemAccessors.isQualifiedStarItem(item) || SelectItemAccessors.isSpreadItem(item)) {
                    // Expand t.* or t.** to all columns of the referenced table/alias
                    String qualifier = SelectItemAccessors.getItemQualifier(item).toUpperCase();
                    for (final Table t : allTables) {
                        String tAlias = (aliasToTable.entrySet().stream()
                            .filter((final var e) -> e.getValue() == t).map(Map.Entry::getKey).findFirst().orElse(t.getName())).toUpperCase();
                        if (qualifier.equals(tAlias) || qualifier.equals(t.getName().toUpperCase())) {
                            for (final TableColumn col : t.getColumns()) {
                                columns.add(new ResultSetColumn(col.getName(), col.getDataType(), t.getName()));
                            }
                            break;
                        }
                    }
                } else if (SelectItemAccessors.isSpreadExprItem(item)) {
                    // SELECT ** <array>: one output column per element, labeled '<item text>[N]' —
                    // mirrors the projection expansion in applyProjection.
                    final FrostlakeParser.SpreadExprItemContext spread =
                        (FrostlakeParser.SpreadExprItemContext) item;
                    final List<Object> spreadValues = spreadElements(new ExpressionEvaluator(
                        new Table("DUMMY", new ArrayList<>(), false), functionRegistry, catalog, this)
                        .evaluate(getOriginalText(spread.expression()), new Row(new ArrayList<>())));
                    final String itemText = getOriginalText(item);
                    for (int i = 0; i < spreadValues.size(); i++) {
                        columns.add(new ResultSetColumn(itemText + "[" + (i + 1) + "]", StringType.VARCHAR, null));
                    }
                } else {
                    // Handle expression select item — the output name/type come from the parse tree.
                    final FrostlakeParser.ExpressionContext valueExpr = SelectItemAccessors.getItemValueExpr(item);
                    final boolean hasAlias = SelectItemAccessors.getItemAlias(item) != null;

                    // Column name: an explicit alias wins; otherwise a (qualified) column reference yields
                    // its column name and any other expression its source text.
                    String colName = hasAlias
                        ? getIdentifier(SelectItemAccessors.getItemAlias(item))
                        : selectItemColumnName(valueExpr);

                    // Data type: COUNT/SUM/AVG → BIGINT; a column reference → the table column's type.
                    DataType colType = StringType.VARCHAR;
                    if (isNumericAggregateItem(valueExpr)) {
                        colType = NumericType.BIGINT;
                    } else if (valueExpr instanceof FrostlakeParser.QualifiedNameExprContext) {
                        final String baseColName = selectItemColumnName(valueExpr);
                        if (table.hasColumn(baseColName)) {
                            final int colIndex = table.getColumnIndex(baseColName);
                            colType = table.getColumns().get(colIndex).getDataType();
                            // An unaliased, unqualified simple column takes the table's properly-cased name.
                            final boolean unqualified = ((FrostlakeParser.QualifiedNameExprContext) valueExpr)
                                .qualifiedName().identifier().size() == 1;
                            if (!hasAlias && unqualified) {
                                colName = table.getColumns().get(colIndex).getName();
                            }
                        }
                    }

                    columns.add(new ResultSetColumn(colName, colType, null));
                }
            }

            if (columns.isEmpty()) {
                // A star over a zero-column source (e.g. GENERATOR) — Snowflake's error shape; the
                // pass-through star path skips applyProjection, so the guard lives here too.
                throw new RuntimeException("SELECT with no columns");
            }

            // Apply DISTINCT if specified
            if (ctx.DISTINCT() != null) {
                rows = SetOperations.applyDistinct(rows);
                logger.trace("Applied DISTINCT, reduced to {} unique rows", rows.size());
            }

            logger.trace("Selected {} rows from table: {}", rows.size(), tableName);
            return new ResultSet(columns, rows);

        } catch (final SecurityException e) {
            throw e; // Let security exceptions propagate
        } catch (final Exception e) {
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw new RuntimeException("Failed to execute SELECT: " + e.getMessage(), e);
        }
    }

    /**
     * Execute SELECT without FROM clause (e.g., SELECT 1, SELECT {'key': 'value'})
     */
    private ResultSet executeSelectWithoutFrom(final FrostlakeParser.SelectClauseContext ctx) {
        try {
            // Parse select list
            FrostlakeParser.SelectListContext selectList = ctx.selectList();

            // Create a single-row result with no input table
            List<ResultSetColumn> columns = new ArrayList<>();
            List<Object> values = new ArrayList<>();

            // Shared dummy table/evaluator for the whole select list
            Table dummyTable = new Table("DUMMY", new ArrayList<>(), false);
            ExpressionEvaluator evaluator = new ExpressionEvaluator(dummyTable, functionRegistry, catalog, this);
            // alias -> computed value, built up left-to-right for forward-reference resolution
            Map<String, Object> selectAliasValues = new HashMap<>();
            // Offer those values as lateral (outer) values so a LATER item can reference an earlier alias
            // INSIDE an expression — SELECT 1 AS x, x + 1 AS y. Matching the whole expression text against an
            // alias (below) only handles a bare `x`. The evaluator holds this map by reference, so entries
            // added as the list is walked are visible to the items that follow.
            evaluator.setOuterLateralContext(selectAliasValues);

            // Process each select item
            for (final FrostlakeParser.SelectItemContext item : selectList.selectItem()) {
                if (SelectItemAccessors.isStarItem(item)) {
                    throw new RuntimeException("SELECT * requires a FROM clause");
                }
                if (SelectItemAccessors.isSpreadExprItem(item)) {
                    // SELECT ** <array>: one output column per element, labeled '<item text>[N]' (1-based).
                    final FrostlakeParser.SpreadExprItemContext spread =
                        (FrostlakeParser.SpreadExprItemContext) item;
                    final List<Object> spreadValues = spreadElements(evaluator.evaluate(
                        getOriginalText(spread.expression()), new Row(values)));
                    final String itemText = getOriginalText(item);
                    int elementIndex = 1;
                    for (final Object element : spreadValues) {
                        columns.add(new ResultSetColumn(itemText + "[" + elementIndex + "]", StringType.VARCHAR));
                        values.add(element);
                        elementIndex++;
                    }
                    continue;
                }

                String exprText = getOriginalText(SelectItemAccessors.getItemExpression(item)).trim();

                Object value;
                // If the bare expression matches a previously-defined alias, reuse that value
                if (selectAliasValues.containsKey(exprText.toUpperCase())) {
                    value = selectAliasValues.get(exprText.toUpperCase());
                } else {
                    Row dummyRow = new Row(values);
                    value = evaluator.evaluate(exprText, dummyRow);
                }

                // Get column name/alias — use COLUMN<N> for unlabelled literals to support $N positional refs
                String columnName;
                if (SelectItemAccessors.getItemAlias(item) != null) {
                    columnName = getIdentifier(SelectItemAccessors.getItemAlias(item));
                } else if (exprText.startsWith("'") || exprText.equalsIgnoreCase("true")
                        || exprText.equalsIgnoreCase("false") || exprText.equalsIgnoreCase("null")
                        || exprText.matches("-?\\d+(\\.\\d+)?")) {
                    // Unlabelled literal: name as COLUMN<N> so $1, $2, etc. resolve correctly
                    columnName = "COLUMN" + (columns.size() + 1);
                } else {
                    columnName = exprText.length() > 30 ? exprText.substring(0, 30) : exprText;
                }

                columns.add(new ResultSetColumn(columnName, StringType.VARCHAR));
                values.add(value);
                selectAliasValues.put(columnName.toUpperCase(), value);
            }

            // Apply WHERE clause if present
            FrostlakeParser.WhereClauseContext whereCtxNoFrom = getWhereClause(ctx);
            if (whereCtxNoFrom != null) {
                Table dummyTable2 = new Table("DUMMY", new ArrayList<>(), false);
                Row dummyRow2 = new Row(values);
                String whereExpr = getOriginalText(whereCtxNoFrom.booleanExpr());
                ExpressionEvaluator whereEval = new ExpressionEvaluator(dummyTable2, functionRegistry, catalog, this);
                Object whereResult = whereEval.evaluate(whereExpr, dummyRow2);
                boolean condMet = SqlTruth.isTrue(whereResult)
                    ? true : whereResult != null && !"false".equalsIgnoreCase(whereResult.toString()) && !"0".equals(whereResult.toString());
                if (!condMet) {
                    return new ResultSet(columns, new ArrayList<>());
                }
            }

            // Create single row result
            List<Row> rows = new ArrayList<>();
            rows.add(new Row(values));

            // Apply DISTINCT if specified
            if (ctx.DISTINCT() != null) {
                rows = SetOperations.applyDistinct(rows);
            }

            return new ResultSet(columns, rows);

        } catch (final Exception e) {
            throw new RuntimeException("Failed to execute SELECT without FROM: " + e.getMessage(), e);
        }
    }

    /**
     * Execute UPDATE from parsed context
     */
    public Object executeUpdateFromContext(final FrostlakeParser.UpdateStatementContext ctx) {
        return updateDeleteExecutor.executeUpdateFromContext(ctx);
    }

    /**
     * Execute DELETE from parsed context
     */
    public Object executeDeleteFromContext(final FrostlakeParser.DeleteStatementContext ctx) {
        return updateDeleteExecutor.executeDeleteFromContext(ctx);
    }

    /**
     * Enforce NOT NULL at statement time (Snowflake always enforces it, unlike informational PK/UK/FK). A
     * column declared NOT NULL — after defaults and auto-increment are applied — must not hold null. Called
     * on the write path so the violation surfaces at the statement, not deferred to commit.
     */
    /**
     * Enforce column constraints at statement time on every write path: coerce values to the column type
     * (only when constraints.enforce.types is on), then check NOT NULL (always — Snowflake enforces it).
     */
    public void enforceColumnConstraints(final Table table, final Row row) {
        if (enforceTypes) {
            coerceRowTypes(table, row);
        }
        final List<TableColumn> cols = table.getColumns();
        for (int i = 0; i < cols.size(); i++) {
            if (!cols.get(i).isNullable() && i < row.getValues().size() && row.getValue(i) == null) {
                throw new RuntimeException("NULL result in a non-nullable column: " + cols.get(i).getName()
                    + " (table " + table.getName() + ")");
            }
        }
    }

    /** Coerce each value in {@code row} to its column's declared type, in place (constraints.enforce.types). */
    private void coerceRowTypes(final Table table, final Row row) {
        final List<TableColumn> cols = table.getColumns();
        for (int i = 0; i < cols.size() && i < row.getValues().size(); i++) {
            row.setValue(i, coerceWriteValue(row.getValue(i), cols.get(i).getDataType()));
        }
    }

    /**
     * Lenient per-column coercion for CTAS rows: coerce each value to the created table's column type
     * when the conversion works, and keep the ORIGINAL value when it does not. The result-column types a
     * SELECT reports are best-effort (an expression column can say NUMBER while a row carries a UUID
     * string), so the strict INSERT-style coercion would reject rows Snowflake accepts.
     */
    public void coerceRowTypesLenient(final Table table, final Row row) {
        final List<TableColumn> cols = table.getColumns();
        for (int i = 0; i < cols.size() && i < row.getValues().size(); i++) {
            try {
                row.setValue(i, coerceWriteValue(row.getValue(i), cols.get(i).getDataType()));
            } catch (final RuntimeException keepOriginal) {
                // Value does not fit the reported type — store it as produced by the SELECT.
            }
        }
    }

    /**
     * Coerce a single value to {@code type} the way Snowflake does on write: enforce VARCHAR(n) length, parse
     * numeric strings (rejecting non-numeric ones), and otherwise leave the value as-is. Already-numeric values
     * are kept unchanged — normalizing every numeric would needlessly churn results, and FLOAT/DOUBLE are
     * NUMBER(38,9) so a precision check would wrongly reject large floats. null passes through.
     */
    private Object coerceWriteValue(final Object value, final DataType type) {
        if (value == null) {
            return null;
        }
        if (type instanceof StringType) {
            final StringType st = (StringType) type;
            final String s = (value instanceof String) ? (String) value : value.toString();
            if (st.getMaxLength() > 0 && s.length() > st.getMaxLength()) {
                throw new RuntimeException("String of length " + s.length()
                    + " exceeds the column maximum of " + st.getMaxLength() + " for " + st.getName());
            }
            return s;
        }
        if (type instanceof NumericType && value instanceof String) {
            try {
                return applyColumnScale(new BigDecimal(((String) value).trim()), (NumericType) type);
            } catch (final NumberFormatException e) {
                throw new RuntimeException("Numeric value '" + value + "' is not recognized");
            }
        }
        if (type instanceof NumericType) {
            return applyColumnScale(value, (NumericType) type);
        }
        if (type instanceof DateTimeType) {
            // A string (or other temporal) written into a DATE/TIME/TIMESTAMP column becomes a real
            // LocalDate/LocalTime/LocalDateTime — the same value TO_DATE/TO_TIMESTAMP would produce — so a
            // string-inserted timestamp compares equal to a computed one (e.g. under EXCEPT / joins).
            return SharedFunctionHelpers.toTemporalValue(type.getName().toUpperCase(), value);
        }
        if (type instanceof ArrayType) {
            // Writing into an ARRAY column follows TO_ARRAY semantics: an existing array passes through
            // unchanged, any other non-null variant value is wrapped in a one-element array — the fixture
            // idiom `INSERT ... SELECT PARSE_JSON('{...}')` then reads it back as arr[0].
            if (ArrayFunctionHelper.parseArray(value) != null) {
                return value;
            }
            final JsonNode node = ArrayFunctionHelper.parseNode(value);
            if (node != null && node.isNull()) {
                return value;
            }
            final ArrayNode wrapped = ArrayFunctionHelper.MAPPER.createArrayNode();
            wrapped.add(ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, value));
            return wrapped.toString();
        }
        if (type instanceof BooleanType) {
            // Snowflake implicitly converts on write into a BOOLEAN column: numbers by zero/non-zero
            // (IFF(x, 1, 0) inserted into BOOLEAN stores TRUE/FALSE), strings by TO_BOOLEAN literals.
            if (value instanceof Number) {
                if (value instanceof BigDecimal) {
                    return ((BigDecimal) value).signum() != 0;
                }
                return ((Number) value).doubleValue() != 0.0;
            }
            if (value instanceof String) {
                final String text = ((String) value).trim().toLowerCase();
                if (text.equals("true") || text.equals("t") || text.equals("yes") || text.equals("y")
                        || text.equals("on") || text.equals("1")) {
                    return Boolean.TRUE;
                }
                if (text.equals("false") || text.equals("f") || text.equals("no") || text.equals("n")
                        || text.equals("off") || text.equals("0")) {
                    return Boolean.FALSE;
                }
                throw new RuntimeException("Boolean value '" + value + "' is not recognized");
            }
            return value;
        }
        return value;
    }

    /**
     * Normalize a value written into a fixed-point NUMBER/DECIMAL(p,s) column to the column's declared scale
     * the way Snowflake does on write: round down when it has too many fractional digits (HALF_UP) and pad
     * with trailing zeros when it has too few, so the stored BigDecimal has exactly {@code scale} digits.
     * Padding matters as much as rounding — a value inserted as {@code 0} must become {@code 0.0000} in a
     * NUMBER(8,4) column so it {@code equals()} (and so groups/dedups/EXCEPTs) identically to the same value
     * arriving via a {@code ::NUMBER(8,4)} cast; BigDecimal equality is scale-sensitive ({@code 0 != 0.0000}).
     * Applies only to NUMBER/DECIMAL/NUMERIC with an explicit scale &gt; 0; FLOAT/DOUBLE (approximate) and
     * bare/scale-0 columns are returned unchanged, as is a value already at the target scale.
     */
    private Object applyColumnScale(final Object value, final NumericType type) {
        if (value == null || type.getScale() <= 0 || !isFixedPointNumeric(type.getName())) {
            return value;
        }
        final BigDecimal bd = (value instanceof BigDecimal) ? (BigDecimal) value : new BigDecimal(value.toString());
        if (bd.scale() != type.getScale()) {
            return bd.setScale(type.getScale(), RoundingMode.HALF_UP);
        }
        return value;
    }

    private static boolean isFixedPointNumeric(final String name) {
        final String upper = name.toUpperCase();
        return upper.equals("NUMBER") || upper.equals("DECIMAL") || upper.equals("NUMERIC");
    }

    /**
     * Execute TRUNCATE TABLE
     */
    public void executeTruncate(final String tableName, final boolean ifExists) {
        try {
            // Resolve the table
            Table table;
            try {
                table = catalog.resolveTable(tableName);
            } catch (final RuntimeException e) {
                if (ifExists) {
                    logger.debug("Table does not exist (IF EXISTS): {}", tableName);
                    return;
                }
                throw e;
            }

            // Check DELETE permission (TRUNCATE requires DELETE privilege)
            if (securityManager != null) {
                securityManager.checkPermission(Privilege.DELETE, SecurableObjectType.TABLE, tableName);
            }

            // Get fully qualified table name
            String fullyQualifiedName = getFullyQualifiedTableName(tableName);

            emptyTableContents(fullyQualifiedName);
            logger.trace("Truncated table: {}", tableName);

        } catch (final SecurityException e) {
            throw e; // Let security exceptions propagate
        } catch (final Exception e) {
            throw new RuntimeException("Failed to execute TRUNCATE: " + e.getMessage(), e);
        }
    }

    /**
     * Discard every row of {@code fullyQualifiedName}, shared by TRUNCATE TABLE and INSERT OVERWRITE.
     *
     * <p>Under deferred-apply this MUST route through the write set (like INSERT/UPDATE): every committed row
     * becomes a pending delete and the transaction's own buffered inserts/updates for the table are dropped, so
     * the rest of the transaction sees the table empty and the change applies atomically at COMMIT (a ROLLBACK
     * restores the rows). Clearing the base store directly would leave this transaction's not-yet-committed
     * inserts in place, to reappear on the next read — which is precisely what made INSERT OVERWRITE APPEND to,
     * instead of replace, rows written earlier in the same transaction (the stage/rebuild pattern
     * {@code INSERT INTO tmp …; INSERT OVERWRITE INTO tmp … SELECT … FROM tmp …}). A transaction is begun when
     * none is active, as INSERT does, so an autocommit truncate applies at statement end and an explicit one
     * stays rollback-safe.
     */
    public void emptyTableContents(final String fullyQualifiedName) {
        if (deferredApply) {
            if (!transactionManager.hasActiveTransaction()) {
                transactionManager.beginTransaction();
            }
            transactionManager.getCurrentTransaction().getWriteSet()
                .recordTruncate(fullyQualifiedName, storageEngine.getTableStorage(fullyQualifiedName).getRowIds());
        } else {
            storageEngine.truncateTable(fullyQualifiedName);
        }
    }

    /**
     * Execute MERGE from parsed context
     */
    public Object executeMergeFromContext(final FrostlakeParser.MergeStatementContext ctx) {
        return mergeExecutor.executeMergeFromContext(ctx);
    }

    public Object executeCopyIntoFromContext(final FrostlakeParser.CopyIntoStatementContext ctx) {
        return copyExecutor.executeCopyIntoFromContext(ctx);
    }

    /** PUT 'file://local' @stage — copy local file(s) (single path or a simple glob) into the stage directory. */
    public ResultSet executePutFromContext(final FrostlakeParser.PutStatementContext ctx) {
        final Path stageDir = resolveCopyBaseDir(stageRefToLocation(ctx.stageRef()));
        if (stageDir == null) {
            throw new RuntimeException("PUT target stage has no local directory");
        }
        final String localPath = stripFileScheme(ctx.STRING_LITERAL() != null
            ? extractStringLiteral(ctx.STRING_LITERAL()) : ctx.FILE_URL().getText());
        final List<ResultSetColumn> cols = Arrays.asList(
            new ResultSetColumn("source", StringType.VARCHAR),
            new ResultSetColumn("target", StringType.VARCHAR),
            new ResultSetColumn("source_size", NumericType.INTEGER),
            new ResultSetColumn("target_size", NumericType.INTEGER),
            new ResultSetColumn("status", StringType.VARCHAR));
        final List<Row> rows = new ArrayList<>();
        try {
            Files.createDirectories(stageDir);
            for (final Path src : resolveLocalSources(localPath)) {
                final Path dest = stageDir.resolve(src.getFileName().toString());
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                final int size = (int) Files.size(dest);
                rows.add(new Row(Arrays.asList(src.getFileName().toString(), dest.getFileName().toString(),
                    size, size, "UPLOADED")));
            }
        } catch (final IOException e) {
            throw new RuntimeException("PUT failed: " + e.getMessage(), e);
        }
        return new ResultSet(cols, rows);
    }

    /** GET @stage 'file://localdir' — copy staged files into a local directory. */
    /** GET's local destination: a quoted string, or an unquoted file URL. */
    private static String stripTargetUrl(final FrostlakeParser.GetStatementContext ctx) {
        return ctx.STRING_LITERAL() != null
            ? ParseTreeText.extractStringLiteral(ctx.STRING_LITERAL()) : ctx.FILE_URL().getText();
    }

    public ResultSet executeGetFromContext(final FrostlakeParser.GetStatementContext ctx) {
        final Path stageDir = resolveCopyBaseDir(stageRefToLocation(ctx.stageRef()));
        final String localDir = stripFileScheme(stripTargetUrl(ctx));
        final List<ResultSetColumn> cols = Arrays.asList(
            new ResultSetColumn("file", StringType.VARCHAR),
            new ResultSetColumn("size", NumericType.INTEGER),
            new ResultSetColumn("status", StringType.VARCHAR));
        final List<Row> rows = new ArrayList<>();
        if (stageDir == null || !Files.isDirectory(stageDir)) {
            return new ResultSet(cols, rows);
        }
        try {
            final Path dest = Paths.get(localDir);
            Files.createDirectories(dest);
            for (final Path src : listCopyFiles(stageDir, null, null)) {
                final Path out = dest.resolve(src.getFileName().toString());
                Files.copy(src, out, StandardCopyOption.REPLACE_EXISTING);
                rows.add(new Row(Arrays.asList(src.getFileName().toString(), (int) Files.size(out), "DOWNLOADED")));
            }
        } catch (final IOException e) {
            throw new RuntimeException("GET failed: " + e.getMessage(), e);
        }
        return new ResultSet(cols, rows);
    }

    /** REMOVE/RM @stage [PATTERN = '…'] — delete staged files (optionally matching a regex pattern). */
    public ResultSet executeRemoveFromContext(final FrostlakeParser.RemoveStatementContext ctx) {
        final EngineConfig cfg = getEngineConfig();
        if (cfg == null || !cfg.isRemoveCommandEnabled()) {
            throw new RuntimeException(
                "REMOVE/RM is disabled by default. Enable it by setting 'command.removeEnabled=true' in frostlake.properties.");
        }
        final Path stageDir = resolveCopyBaseDir(stageRefToLocation(ctx.stageRef()));
        final String pattern = ctx.PATTERN() != null ? extractStringLiteral(ctx.STRING_LITERAL()) : null;
        final List<ResultSetColumn> cols = Arrays.asList(
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("result", StringType.VARCHAR));
        final List<Row> rows = new ArrayList<>();
        if (stageDir == null || !Files.isDirectory(stageDir)) {
            return new ResultSet(cols, rows);
        }
        for (final Path f : listCopyFiles(stageDir, pattern, null)) {
            final boolean deleted = f.toFile().delete();
            rows.add(new Row(Arrays.asList(f.getFileName().toString(), deleted ? "REMOVED" : "FAILED")));
        }
        return new ResultSet(cols, rows);
    }

    /** Strip a {@code file://} scheme from a local path reference. */
    private String stripFileScheme(final String url) {
        return url != null && url.startsWith("file://") ? url.substring(7) : url;
    }

    /** Resolve a PUT source: a single local file, or — when the file name has a {@code *}/{@code ?} — a glob over its directory. */
    private List<Path> resolveLocalSources(final String localPath) {
        final Path p = Paths.get(localPath);
        final String fileName = p.getFileName().toString();
        if (!fileName.contains("*") && !fileName.contains("?")) {
            return Collections.singletonList(p);
        }
        final List<Path> matches = new ArrayList<>();
        final File dir = p.getParent() != null ? p.getParent().toFile() : new File(".");
        final String regex = fileName.replace(".", "\\.").replace("*", ".*").replace("?", ".");
        final File[] children = dir.listFiles();
        if (children != null) {
            Arrays.sort(children);
            for (final File child : children) {
                if (child.isFile() && child.getName().matches(regex)) {
                    matches.add(child.toPath());
                }
            }
        }
        return matches;
    }


    /**
     * ALTER PIPE … REFRESH: run the pipe's stored COPY, but restrict the files it loads to those whose
     * stage-relative path starts with {@code prefix} and/or whose last-modified time is strictly after
     * {@code modifiedAfter} (matching Snowflake's REFRESH PREFIX / MODIFIED_AFTER). Either filter may be
     * null (absent). The filters are scoped to this single COPY and cleared afterwards.
     */
    public Object executeCopyRefresh(final String copyStatement, final String prefix, final String modifiedAfter) {
        return copyExecutor.executeCopyRefresh(copyStatement, prefix, modifiedAfter);
    }


    /** Local directory backing a COPY FROM location: {@code @stage} (resolver-aware), {@code s3://…} (via the resolver), or {@code file://…}. */
    /** Resolve a stage reference (e.g. {@code @stg/script.sql}) to its local file path — for
     *  {@code EXECUTE IMMEDIATE FROM}. */
    public Path resolveStageFilePath(final FrostlakeParser.StageRefContext stageRef) {
        return resolveCopyBaseDir(stageRefToLocation(stageRef));
    }

    /** Resolve a location string (e.g. {@code 'file://…/script.sql'}) to its local file path. */
    public Path resolveStageFilePath(final String location) {
        return resolveCopyBaseDir(location);
    }

    public Path resolveCopyBaseDir(final String fromLocation) {
        if (fromLocation == null) {
            return null;
        }
        if (fromLocation.startsWith("@~")) {
            // User stage @~[/path] → <internalRoot>/users/<user>[/path]; exists implicitly (no CREATE STAGE).
            return internalStageDir("users", catalog.currentUserForStage(), fromLocation.substring(2));
        }
        if (fromLocation.startsWith("@%")) {
            // Table stage @%table[/path] → <internalRoot>/tables/<fully-qualified-table>[/path].
            final String tableRef = fromLocation.substring(2);
            final int sep = tableRef.indexOf('/');
            final String tableName = sep >= 0 ? tableRef.substring(0, sep) : tableRef;
            return internalStageDir("tables", getFullyQualifiedTableName(tableName), sep >= 0 ? tableRef.substring(sep) : "");
        }
        if (fromLocation.startsWith("@")) {
            // @stage or @stage/sub/path — split the stage name from an optional path beneath it.
            final String ref = fromLocation.substring(1);
            final int slash = ref.indexOf('/');
            final Stage stage = catalog.getStage(slash >= 0 ? ref.substring(0, slash) : ref);
            if (stage == null || stage.getLocalPath() == null) {
                return null;
            }
            final String subPath = slash >= 0 ? ref.substring(slash + 1) : "";
            return subPath.isEmpty() ? stage.getLocalPath() : stage.getLocalPath().resolve(subPath);
        }
        if (S3PathResolver.isS3Url(fromLocation)) {
            return s3PathResolver != null ? s3PathResolver.toLocalPath(fromLocation) : null;
        }
        if (fromLocation.startsWith("file://")) {
            return Paths.get(fromLocation.substring(7));
        }
        return Paths.get(fromLocation);
    }

    /** Encode a stage reference as a COPY location string: {@code @name[/path]}, {@code @~[/path]}, or {@code @%table[/path]}. */
    public String stageRefToLocation(final FrostlakeParser.StageRefContext ctx) {
        final String path = ctx.stagePath() != null ? ctx.stagePath().getText() : "";
        if (ctx.TILDE() != null) {
            return "@~" + path;
        }
        if (ctx.PERCENT() != null) {
            // @%table or @namespace.%table_name — with the TABLE token, every identifier is namespace;
            // otherwise the LAST identifier is the table name and the rest the namespace.
            final List<FrostlakeParser.IdentifierContext> ids = ctx.identifier();
            final int namespaceCount = ctx.TABLE() != null ? ids.size() : ids.size() - 1;
            final StringBuilder location = new StringBuilder("@");
            for (int i = 0; i < namespaceCount; i++) {
                location.append(getIdentifier(ids.get(i))).append('.');
            }
            location.append('%')
                .append(ctx.TABLE() != null ? "TABLE" : getIdentifier(ids.get(ids.size() - 1)));
            return location.append(path).toString();
        }
        // A stage name may be schema- or database-qualified: @stage, @schema.stage, @db.schema.stage.
        final StringBuilder name = new StringBuilder();
        for (final FrostlakeParser.IdentifierContext part : ctx.identifier()) {
            if (name.length() > 0) {
                name.append('.');
            }
            name.append(getIdentifier(part));
        }
        return "@" + name + path;
    }

    /** Local directory for an implicit internal stage: {@code <internalRoot>/<kind>/<name>[/subPath]}. */
    private Path internalStageDir(final String kind, final String name, final String subPath) {
        final String root = engineConfig != null ? engineConfig.getStageInternalLocalRoot()
            : System.getProperty("user.home") + "/.frostlake_stages/internal";
        final Path dir = Paths.get(root, kind, sanitizeStageSegment(name));
        final String sub = subPath.startsWith("/") ? subPath.substring(1) : subPath;
        return sub.isEmpty() ? dir : dir.resolve(sub);
    }

    /** Make a stage owner name (user / fully-qualified table) safe as a single path segment. */
    private static String sanitizeStageSegment(final String name) {
        return name.replaceAll("[/\\\\]", "_");
    }


    /** Regular files in {@code baseDir}, filtered by an explicit FILES list or a PATTERN regex; stable order. */
    public List<Path> listCopyFiles(final Path baseDir, final String pattern, final List<String> files) {
        final List<Path> result = new ArrayList<>();
        final File[] children = baseDir.toFile().listFiles();
        if (children == null) {
            return result;
        }
        Arrays.sort(children);
        for (final File child : children) {
            if (!child.isFile()) {
                continue;
            }
            final String name = child.getName();
            if (files != null && !files.isEmpty()) {
                if (!files.contains(name)) {
                    continue;
                }
            } else if (pattern != null && !name.matches(pattern)) {
                continue;
            }
            result.add(child.toPath());
        }
        return result;
    }

    // ==================== HELPER METHODS ====================

    // ── selectItem helpers (needed after grammar added labeled alternatives) ──

    /** Get the effective whereClause from a selectClause (handles the list produced by the grammar). */
    private FrostlakeParser.WhereClauseContext getWhereClause(final FrostlakeParser.SelectClauseContext ctx) {
        return ParseTreeText.getWhereClause(ctx);
    }

    /** Collect all top-level SelectClauseContexts from selectOperands (non-parenthesised ones). */
    private List<FrostlakeParser.SelectClauseContext> getSelectClauses(final FrostlakeParser.SelectStatementContext ctx) {
        return ParseTreeText.getSelectClauses(ctx);
    }

    String getIdentifier(final FrostlakeParser.IdentifierContext ctx) {
        return ParseTreeText.getIdentifier(ctx);
    }

    String getQualifiedName(final FrostlakeParser.QualifiedNameContext ctx) {
        return ParseTreeText.getQualifiedName(ctx);
    }

    String getOriginalText(final ParserRuleContext ctx) {
        return ParseTreeText.getOriginalText(ctx);
    }

    private String extractStringLiteral(final TerminalNode node) {
        return ParseTreeText.extractStringLiteral(node);
    }

    private String getIdentifier(final TerminalNode node) {
        return ParseTreeText.getIdentifier(node);
    }

    /** ALTER TABLE a SWAP WITH b — exchange the two tables' row storage (compatible structure assumed). */
    public void swapTables(final String tableA, final String tableB) {
        storageEngine.swapTables(getFullyQualifiedTableName(tableA), getFullyQualifiedTableName(tableB));
    }

    /**
     * Resolve an {@code objectName} parse node to the name it denotes: {@code IDENTIFIER(<expr>)} evaluates
     * the expression (a string literal or a session/bind variable) to the name; otherwise it is the literal
     * qualified name as written. Lets object names be supplied dynamically (Snowflake IDENTIFIER()).
     */
    public String resolveObjectName(final FrostlakeParser.ObjectNameContext ctx) {
        if (ctx.KW_IDENTIFIER() != null && ctx.expression() != null) {
            final ExpressionEvaluator eval = new ExpressionEvaluator(null, functionRegistry, catalog, this);
            final Object nameVal = eval.evaluate(getOriginalText(ctx.expression()), null);
            if (nameVal == null) {
                throw new RuntimeException("IDENTIFIER() expression evaluated to null");
            }
            return nameVal.toString();
        }
        // Fold the identifier parts (unquoted -> upper-case, quoted preserved) rather than returning the
        // raw text, so a created object's canonical name matches Snowflake.
        return getQualifiedName(ctx.qualifiedName());
    }

    /** Re-key a renamed table's row storage: the new name keeps the old table's database/schema. */
    public void renameTableStorage(final String oldName, final String newShortName) {
        final String oldQualified = getFullyQualifiedTableName(oldName);
        final int lastDot = oldQualified.lastIndexOf('.');
        final String newQualified = lastDot >= 0
            ? oldQualified.substring(0, lastDot + 1) + newShortName.toUpperCase()
            : newShortName.toUpperCase();
        storageEngine.renameTable(oldQualified, newQualified);
    }

    /** Re-key a moved table's row storage to a new database/schema/name (cross-schema RENAME TO). */
    public void moveTableStorage(final String oldName, final String targetDatabase,
                                 final String targetSchema, final String newShortName) {
        final String oldQualified = getFullyQualifiedTableName(oldName);
        final String newQualified =
            (targetDatabase + "." + targetSchema + "." + newShortName).toUpperCase();
        storageEngine.renameTable(oldQualified, newQualified);
    }

    /**
     * Backfill every existing row of a table after ALTER TABLE ADD COLUMN, appending one value for the
     * new column so stored rows match the widened schema — otherwise every read of the new column throws
     * IndexOutOfBounds. Snowflake populates existing rows with the column's DEFAULT literal when present,
     * otherwise NULL; the default is evaluated once (via the same path INSERT uses) and shared across
     * rows.
     */
    public void backfillColumn(final String tableName, final TableColumn newColumn) {
        final Object value = evaluateDefaultValue(newColumn.getDefaultValue());
        final StorageEngine.TableStorage storage =
            storageEngine.getTableStorage(getFullyQualifiedTableName(tableName));
        final int rowCount = storage.getRowCount();
        for (int i = 0; i < rowCount; i++) {
            final List<Object> values = new ArrayList<>(storage.getRow(i).getValues());
            values.add(value);
            storage.update(i, new Row(values));
        }
    }

    /**
     * The positional argument expressions of a function call. The grammar's function-argument list is a
     * {@code booleanExpr} list (a superset of {@code expression}); a plain argument is a {@code ValueExpr}
     * wrapping its {@code expression}, unwrapped here so callers keep working with {@code ExpressionContext}
     * (e.g. the {@code LAST_QUERY_ID()} instanceof check for RESULT_SCAN).
     */
    private static List<FrostlakeParser.ExpressionContext> funcArgExprs(final FrostlakeParser.FunctionCallExprContext funcCtx) {
        final List<FrostlakeParser.ExpressionContext> out = new ArrayList<>();
        if (funcCtx.functionArgList() != null) {
            for (final FrostlakeParser.BooleanExprContext arg : ParseTreeText.functionBooleanArgs(funcCtx.functionArgList())) {
                if (arg instanceof FrostlakeParser.ValueExprContext) {
                    out.add(((FrostlakeParser.ValueExprContext) arg).expression());
                }
            }
        }
        return out;
    }

    /**
     * The output column name for a select-item value expression: a (qualified) column reference's column
     * name read from the parse tree (its last identifier), or the expression's source text otherwise.
     */
    private String selectItemColumnName(final FrostlakeParser.ExpressionContext valueExpr) {
        if (valueExpr instanceof FrostlakeParser.QualifiedNameExprContext) {
            final List<FrostlakeParser.IdentifierContext> ids =
                ((FrostlakeParser.QualifiedNameExprContext) valueExpr).qualifiedName().identifier();
            return getIdentifier(ids.get(ids.size() - 1));
        }
        return valueExpr != null ? valueExpr.getText() : "";
    }

    /** Whether a select-item value expression is a COUNT/SUM/AVG aggregate (numeric BIGINT result). */
    private static boolean isNumericAggregateItem(final FrostlakeParser.ExpressionContext valueExpr) {
        if (valueExpr instanceof FrostlakeParser.FunctionCallExprContext) {
            final String n = ((FrostlakeParser.FunctionCallExprContext) valueExpr).functionName().getText().toUpperCase();
            return "COUNT".equals(n) || "SUM".equals(n) || "AVG".equals(n);
        }
        if (valueExpr instanceof FrostlakeParser.FunctionCallStarExprContext) {
            return "COUNT".equalsIgnoreCase(((FrostlakeParser.FunctionCallStarExprContext) valueExpr).functionName().getText());
        }
        return false;
    }

    public String getFullyQualifiedTableName(final String tableName) {
        String[] parts = QualifiedName.parse(tableName).parts();
        if (parts.length == 3) {
            // Already fully qualified - uppercase all parts
            return parts[0].toUpperCase() + "." + parts[1].toUpperCase() + "." + parts[2].toUpperCase();
        } else if (parts.length == 2) {
            // Has schema.table, need database
            return catalog.getCurrentDatabase() + "." + parts[0].toUpperCase() + "." + parts[1].toUpperCase();
        } else {
            // Just table name, need both database and schema
            return catalog.getCurrentDatabase() + "." + catalog.getCurrentSchema() + "." + tableName.toUpperCase();
        }
    }

    private Object evaluateDefaultValue(final Object defaultValue) {
        if (defaultValue == null) {
            return null;
        }

        // A complex-expression default (DEFAULT (1+2), DEFAULT ('a'||'b'), DEFAULT UPPER('x'), …) is
        // evaluated per row here rather than inserted as its literal text. An invalid expression (e.g. a
        // reference to a non-existent column) surfaces as an error at INSERT, which is the correct outcome.
        if (defaultValue instanceof DefaultValueExpression) {
            return evaluateDefaultExpressionText(((DefaultValueExpression) defaultValue).getExpressionText());
        }

        // If it's a string, check if it's a function name or special value
        if (defaultValue instanceof String) {
            String expr = ((String) defaultValue).toUpperCase().trim();
            switch (expr) {
                case "CURRENT_TIMESTAMP":
                case "CURRENT_TIMESTAMP()":
                    return LocalDateTime.now();
                case "CURRENT_DATE":
                case "CURRENT_DATE()":
                    return LocalDate.now();
                case "CURRENT_TIME":
                case "CURRENT_TIME()":
                    return LocalTime.now();
                case "UUID_STRING()":
                    return UUID.randomUUID().toString();
                case "NULL":
                    return null;
                case "TRUE":
                    return true;
                case "FALSE":
                    return false;
                default:
                    return defaultValue;
            }
        }

        // Otherwise return as-is (literal value)
        return defaultValue;
    }

    /** Evaluate a column DEFAULT expression's text against an empty row (defaults cannot see other columns). */
    private Object evaluateDefaultExpressionText(final String expressionText) {
        final Table dummyTable = new Table("DUMMY", new ArrayList<>(), false);
        final Row dummyRow = new Row(new ArrayList<>());
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(dummyTable, functionRegistry, catalog, this);
        return evaluator.evaluate(expressionText, dummyRow);
    }

    private View resolveView(final String viewName) {
        final String[] parts = QualifiedName.parse(viewName).parts();
        final Schema schema;
        final String actualViewName;

        if (parts.length == 1) {
            // Unqualified: use current schema
            schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(catalog.getCurrentSchema());
            actualViewName = viewName;
        } else if (parts.length == 2) {
            // schema.view (using current database)
            schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
            actualViewName = parts[1];
        } else {
            // database.schema.view
            schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
            actualViewName = parts[2];
        }

        return schema.getView(actualViewName);
    }

    /** Resolve a MATERIALIZED VIEW by (optionally qualified) name; the schema getter is case-insensitive. */
    private MaterializedView resolveMaterializedView(final String name) {
        final String[] parts = QualifiedName.parse(name).parts();
        final Schema schema = parts.length == 1
            ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(catalog.getCurrentSchema())
            : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
            : catalog.getDatabase(parts[0]).getSchema(parts[1]);
        return schema.getMaterializedView(parts[parts.length - 1]);
    }

    /** Resolve a DYNAMIC TABLE by (optionally qualified) name; the schema getter is case-insensitive. */
    private DynamicTable resolveDynamicTable(final String name) {
        final String[] parts = QualifiedName.parse(name).parts();
        final Schema schema = parts.length == 1
            ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(catalog.getCurrentSchema())
            : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
            : catalog.getDatabase(parts[0]).getSchema(parts[1]);
        return schema.getDynamicTable(parts[parts.length - 1]);
    }

    /**
     * Check if the view is an INFORMATION_SCHEMA system view and execute it using SystemViews
     * @return ResultSet if it's a system view, null otherwise
     */
    private ResultSet executeSystemViewIfApplicable(final String tableName, final View view) {
        String[] parts = QualifiedName.parse(tableName).parts();
        String database = null;
        String schema = null;
        String viewName = null;

        if (parts.length == 1) {
            database = catalog.getCurrentDatabase();
            schema = catalog.getCurrentSchema();
            viewName = parts[0].toUpperCase();
        } else if (parts.length == 2) {
            // schema.view (using current database)
            database = catalog.getCurrentDatabase();
            schema = parts[0].toUpperCase();
            viewName = parts[1].toUpperCase();
        } else if (parts.length == 3) {
            // database.schema.view
            database = parts[0].toUpperCase();
            schema = parts[1].toUpperCase();
            viewName = parts[2].toUpperCase();
        }

        // Check if this is INFORMATION_SCHEMA schema
        if (!"INFORMATION_SCHEMA".equals(schema)) {
            return null;
        }

        // Route to appropriate system view method
        switch (viewName) {
            case "DATABASES":
                return systemViews.queryDatabases();
            case "SCHEMATA":
                return systemViews.querySchemata(database);
            case "TABLES":
                return systemViews.queryTables(database, null);
            case "COLUMNS":
                return systemViews.queryColumns(database, null, null);
            case "VIEWS":
                return systemViews.queryViews(database, null);
            case "TABLE_CONSTRAINTS":
                return systemViews.queryTableConstraints(database, null);
            case "REFERENTIAL_CONSTRAINTS":
                return systemViews.queryReferentialConstraints(database, null);
            case "PROCEDURES":
                return systemViews.queryProcedures(database, null);
            case "FUNCTIONS":
                return systemViews.queryFunctions(database, null);
            case "SEQUENCES":
                return systemViews.querySequences(database, null);
            case "STAGES":
                return systemViews.queryStages(database, null);
            case "PIPES":
                return systemViews.queryPipes(database, null);
            case "STREAMS":
                return systemViews.queryStreams(database, null);
            case "TASKS":
                return systemViews.queryTasks(database, null);
            case "ENABLED_ROLES":
                return systemViews.queryEnabledRoles();
            case "APPLICABLE_ROLES":
                return systemViews.queryApplicableRoles();
            case "TABLE_PRIVILEGES":
                return systemViews.queryTablePrivileges(database, null);
            case "OBJECT_PRIVILEGES":
                return systemViews.queryObjectPrivileges();
            case "USAGE_PRIVILEGES":
                return systemViews.queryUsagePrivileges();
            case "TAGS":
                return systemViews.queryTags(database, null);
            case "TAG_REFERENCES":
                return systemViews.queryTagReferences(database, null);
            case "QUERY_HISTORY":
                return showQueryHistory(null);
            default:
                return null;
        }
    }

    List<Row> filterRows(final List<Row> rows, final Table table, final String whereExpr) {
        return filterRows(rows, table, null, whereExpr);
    }

    /**
     * Row filter for UPDATE/DELETE WHERE clauses. {@code tableAlias} is the statement's target alias
     * (UPDATE t AS x … / DELETE FROM t x …): registering it as multi-table context is what lets a
     * correlated subquery reference the outer row by that alias (WHERE EXISTS (… WHERE x.id = s.id)) —
     * without it the alias-qualified lookup misses and the strip-qualifier fallback binds the reference
     * to the INNER table's same-named column, turning the correlation into a tautology.
     */
    List<Row> filterRows(final List<Row> rows, final Table table, final String tableAlias, final String whereExpr) {
        ExpressionEvaluator evaluator = new ExpressionEvaluator(table, functionRegistry, catalog, this);
        if (tableAlias != null && !tableAlias.equalsIgnoreCase(table.getName())) {
            final Map<String, Table> aliasToTable = new HashMap<>();
            aliasToTable.put(tableAlias.toUpperCase(), table);
            final List<Table> allTables = new ArrayList<>();
            allTables.add(table);
            evaluator.setMultiTableContext(aliasToTable, allTables);
        }
        List<Row> filtered = new ArrayList<>();
        for (final Row row : rows) {
            Object result = evaluator.evaluate(whereExpr, row);
            if (SqlTruth.isTrue(result)) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    /**
     * For each projection expression that is a direct reference to a masked column,
     * wrap it with the masking policy body — substituting the policy parameter with the column name.
     * Security check: masking is skipped for ACCOUNTADMIN / SYSADMIN roles.
     */
    private List<String> applyMaskingPolicies(final List<String> exprs, final Table table) {
        return maskingApplier.applyMaskingPolicies(exprs, table);
    }

    private List<Row> applyRowAccessPolicy(final List<Row> rows, final Table table) {
        return maskingApplier.applyRowAccessPolicy(rows, table);
    }

    /**
     * A row access policy attached to a view filters the view's OUTPUT rows: stamp the attachment
     * onto the virtual result table so the scan-level enforcement treats it like a policied table.
     */
    private void stampViewRowAccessPolicy(final View view, final Table virtualTable) {
        if (view.hasRowAccessPolicy()) {
            virtualTable.setRowAccessPolicyName(view.getRowAccessPolicyName());
            virtualTable.setRowAccessPolicyColumns(view.getRowAccessPolicyColumns());
        }
    }

    private List<ResultSetColumn> buildProjectionColumns(final FrostlakeParser.SelectClauseContext ctx,
                                                          final Table table) {
        List<ResultSetColumn> cols = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (SelectItemAccessors.isStarItem(item)) {
                for (final TableColumn col : table.getColumns()) {
                    cols.add(new ResultSetColumn(col.getName(), col.getDataType()));
                }
            } else if (SelectItemAccessors.isQualifiedStarItem(item) || SelectItemAccessors.isSpreadItem(item)) {
                // qualified star — columns added later in applyProjection; skip here
            } else {
                String alias = SelectItemAccessors.getItemAlias(item) != null ? getIdentifier(SelectItemAccessors.getItemAlias(item)) : SelectItemAccessors.getItemExpression(item).getText();
                cols.add(new ResultSetColumn(alias, StringType.VARCHAR));
            }
        }
        return cols;
    }

    /** Filter rows with CTE support for UPDATE/DELETE WHERE clauses. */
    List<Row> filterRowsWithCTEs(final List<Row> rows, final Table table,
                                          final String whereExpr,
                                          final Map<String, ResultSet> cteResults) {
        return filterRowsWithCTEs(rows, table, null, whereExpr, cteResults);
    }

    /** Alias-aware variant of {@link #filterRowsWithCTEs}; see {@link #filterRows(List, Table, String, String)}. */
    List<Row> filterRowsWithCTEs(final List<Row> rows, final Table table, final String tableAlias,
                                          final String whereExpr,
                                          final Map<String, ResultSet> cteResults) {
        // Build a combined table from actual table + CTE virtual tables (for subqueries in WHERE)
        // For simple column references, the regular evaluator works fine;
        // CTEs in WHERE are typically used as subquery sources (e.g. WHERE id IN (SELECT id FROM cte))
        // We wire them via the query executor which already resolves CTEs during subquery evaluation.
        this.currentCteContext = cteResults;
        try {
            return filterRows(rows, table, tableAlias, whereExpr);
        } finally {
            this.currentCteContext = null;
        }
    }

    // Thread-local CTE context for UPDATE/DELETE WHERE clause evaluation
    private volatile Map<String, ResultSet> currentCteContext = null;

    /** Live read of the per-query CTE context (WITH-clause results visible to SET/WHERE subqueries); used by write-path collaborators to preserve the save/set/restore idiom exactly. */
    Map<String, ResultSet> getCurrentCteContext() {
        return currentCteContext;
    }

    /** Live write of the per-query CTE context; the caller is responsible for save-old / set-new / restore-old around the window that needs it. */
    void setCurrentCteContext(final Map<String, ResultSet> cteContext) {
        this.currentCteContext = cteContext;
    }

    /**
     * Filter rows with support for table aliases in WHERE clause
     */
    /**
     * Filter rows with LATERAL context support - allows WHERE clause to reference outer query columns
     */
    private List<Row> filterRowsWithLateralContext(final List<Row> rows, final Table table, final String whereExpr,
                                                    final Map<String, Object> lateralContext,
                                                    final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final ExpressionEvaluator ev = new ExpressionEvaluator(table, functionRegistry, catalog, this);
        ev.setOuterLateralContext(lateralContext);
        ev.setMultiTableContext(aliasToTable, allTables);
        final Expression parsed = ExpressionEvaluator.parse(whereExpr);
        List<Row> filtered = new ArrayList<>();
        for (final Row row : rows) {
            try {
                Object result = ev.evaluate(parsed, row);
                if (SqlTruth.isTrue(result)) {
                    filtered.add(row);
                }
            } catch (final Exception e) {
                logger.warn("Failed to evaluate WHERE clause with lateral context: {}", e.getMessage());
            }
        }
        return filtered;
    }

    private List<Row> filterRowsWithAliases(final List<Row> rows, final Table combinedTable, final String whereExpr,
                                            final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final ExpressionEvaluator ev = new ExpressionEvaluator(combinedTable, functionRegistry, catalog, this);
        ev.setMultiTableContext(aliasToTable, allTables);
        final Expression parsed = ExpressionEvaluator.parse(whereExpr);
        List<Row> filtered = new ArrayList<>();
        for (final Row row : rows) {
            try {
                Object result = ev.evaluate(parsed, row);
                if (SqlTruth.isTrue(result)) {
                    filtered.add(row);
                }
            } catch (final Exception e) {
                logger.error("WHERE clause evaluation failed: {}", e.getMessage());
            }
        }
        return filtered;
    }

    /**
     * Apply WHERE clause using operator pipeline architecture.
     * This method determines the appropriate evaluation mode and delegates to the WhereOperator.
     */
    /**
     * A simple single-table {@code SELECT ... LIMIT} with no join, aggregate, window function, GROUP BY,
     * HAVING, QUALIFY, ORDER BY, DISTINCT, PIVOT/UNPIVOT, TOP/FETCH, set operation, or correlated
     * (lateral) context — the only shape where scan -> WHERE -> LIMIT can stream with early stop and
     * produce identical results. Projection is 1:1 (it never adds or drops rows), so it commutes with
     * LIMIT and is applied afterwards on the limited rows.
     */
    private boolean isStreamableSelect(final FrostlakeParser.SelectClauseContext ctx,
                                       final FrostlakeParser.SelectStatementContext stmtCtx,
                                       final FrostlakeParser.TableReferenceContext firstRef,
                                       final List<Table> allTables) {
        return allTables.size() == 1
            && stmtCtx.limitClause() != null
            && stmtCtx.selectOperand().size() == 1
            && stmtCtx.orderByClause() == null
            && stmtCtx.fetchClause() == null
            && ctx.topClause() == null
            && ctx.groupByClause() == null
            && ctx.havingClause() == null
            && ctx.qualifyClause() == null
            && ctx.DISTINCT() == null
            && firstRef.pivotClause() == null
            && firstRef.unpivotClause() == null
            && !hasAggregateFunction(ctx)
            && !hasWindowFunction(ctx);
    }

    /**
     * Stream scan -> (WHERE) -> LIMIT for a {@link #isStreamableSelect streamable} SELECT: the predicate
     * is evaluated only until LIMIT is satisfied ({@link LimitRowStream} stops pulling its source), and
     * the full filtered set is never built. Returns at most {@code limit} rows (after {@code offset}).
     * The predicate mirrors {@code WhereOperator}'s SIMPLE mode exactly (same evaluator, same parse).
     */
    private List<Row> streamFilterLimit(final List<Row> rows, final Table table, final String whereExpr,
                                        final FrostlakeParser.LimitClauseContext limitClause) {
        long limit;
        long offset = 0;
        // LIMIT NULL means no limit; the INTEGER_LITERAL list then holds only the OFFSET (if any).
        final boolean unlimited = limitClause.NULL() != null;
        List<TerminalNode> intLiterals = limitClause.INTEGER_LITERAL();
        limit = unlimited ? Long.MAX_VALUE : Long.parseLong(intLiterals.get(0).getText());
        if (intLiterals.size() > (unlimited ? 0 : 1)) {
            offset = Long.parseLong(intLiterals.get(intLiterals.size() - 1).getText());
        }

        RowStream stream = new ListRowStream(rows);
        if (whereExpr != null && !whereExpr.trim().isEmpty()) {
            final ExpressionEvaluator evaluator = new ExpressionEvaluator(table, functionRegistry, catalog, this);
            final Expression predicate = ExpressionEvaluator.parse(whereExpr);
            stream = new FilterRowStream(stream, new RowPredicate() {
                @Override
                public boolean test(final Row row) {
                    Object result = evaluator.evaluate(predicate, row);
                    return SqlTruth.isTrue(result);
                }
            });
        }
        stream = new LimitRowStream(stream, limit, offset);

        List<Row> result = new ArrayList<>();
        try {
            Row row = stream.next();
            while (row != null) {
                result.add(row);
                row = stream.next();
            }
        } finally {
            stream.close();
        }
        return result;
    }

    private List<Row> applyWhereClause(final List<Row> rows, final Table table, final String whereExpr,
                                       final Map<String, Object> lateralContext,
                                       final Map<String, Table> aliasToTable, final List<Table> allTables,
                                       final FrostlakeParser.TableExpressionContext tableExpr,
                                       final FrostlakeParser.SelectClauseContext selectCtx) {
        // Build operator context
        OperatorContext.Builder contextBuilder = OperatorContext.builder()
            .table(table)
            .functionRegistry(functionRegistry).queryExecutor(this)
            .aliasToTable(aliasToTable)
            .allTables(allTables);

        if (lateralContext != null && !lateralContext.isEmpty()) {
            contextBuilder.lateralContext(lateralContext);
        }

        // Determine evaluation mode
        WhereEvaluationMode mode;
        if (lateralContext != null && !lateralContext.isEmpty()) {
            mode = WhereEvaluationMode.WITH_LATERAL_CONTEXT;
            // Provide custom evaluator for lateral context
            final ExpressionEvaluator lateralEval = new ExpressionEvaluator(table, functionRegistry, catalog, this);
            lateralEval.setOuterLateralContext(lateralContext);
            lateralEval.setMultiTableContext(aliasToTable, allTables);
            contextBuilder.expressionEvaluator(new RowExpressionEvaluator() {
                @Override
                public Object evaluate(final Expression expr, final Row row) {
                    return lateralEval.evaluate(expr, row);
                }
            });
        } else if (allTables.size() > 1 || tableExpr.joinClause().size() > 0
                || hasAliasDistinctFromTableName(aliasToTable, table)) {
            mode = WhereEvaluationMode.WITH_ALIASES;
            // Provide custom evaluator for JOIN queries — and for a SINGLE aliased table, whose alias
            // a correlated subquery in the WHERE may use to reference the outer row (the alias-qualified
            // context keys only assemble when the evaluator carries the alias map).
            final ExpressionEvaluator joinEval = new ExpressionEvaluator(table, functionRegistry, catalog, this);
            joinEval.setMultiTableContext(aliasToTable, allTables);
            contextBuilder.expressionEvaluator(new RowExpressionEvaluator() {
                @Override
                public Object evaluate(final Expression expr, final Row row) {
                    return joinEval.evaluate(expr, row);
                }
            });
        } else {
            mode = WhereEvaluationMode.SIMPLE;
        }

        // A SELECT-list alias is visible in this query's own WHERE: Snowflake evaluates the aliased expression
        // once and lets other parts of the same query reference it, so `SELECT <expr> AS ft … WHERE ft = 'v'`
        // must resolve rather than fail with "Column not found: FT". The alias values are offered as lateral
        // (outer) values, which the evaluator consults only AFTER the table's own columns — so a real column of
        // the same name still wins. The WHERE operator and its row loop are untouched; only the per-row
        // evaluator is augmented.
        final Map<String, String> whereAliases = selectAliasExpressions(selectCtx, table);
        if (!whereAliases.isEmpty()) {
            final ExpressionEvaluator aliasEval = new ExpressionEvaluator(table, functionRegistry, catalog, this);
            aliasEval.setMultiTableContext(aliasToTable, allTables);
            final ExpressionEvaluator predicateEval = new ExpressionEvaluator(table, functionRegistry, catalog, this);
            predicateEval.setMultiTableContext(aliasToTable, allTables);
            final Map<String, Object> outerValues = new HashMap<>();
            if (lateralContext != null) {
                outerValues.putAll(lateralContext);
            }
            contextBuilder.expressionEvaluator(new RowExpressionEvaluator() {
                @Override
                public Object evaluate(final Expression expr, final Row row) {
                    final Map<String, Object> values = new HashMap<>(outerValues);
                    // An alias's defining expression may reference OTHER select aliases — Snowflake
                    // resolves left-to-right (`… AS ces_diff, ces_diff / x AS pct … WHERE ABS(pct) >= 1`).
                    // Resolve iteratively: each pass offers the aliases already computed as outer values
                    // and retries the rest, until a pass adds nothing; a still-unresolvable alias
                    // (aggregate-dependent, forward-only) stays NULL as before.
                    final Map<String, String> pending = new HashMap<>(whereAliases);
                    boolean progressed = true;
                    while (progressed && !pending.isEmpty()) {
                        progressed = false;
                        final Iterator<Map.Entry<String, String>> remaining = pending.entrySet().iterator();
                        while (remaining.hasNext()) {
                            final Map.Entry<String, String> alias = remaining.next();
                            try {
                                aliasEval.setOuterLateralContext(values);
                                values.put(alias.getKey(), aliasEval.evaluate(alias.getValue(), row));
                                remaining.remove();
                                progressed = true;
                            } catch (final RuntimeException notEvaluableYet) {
                                // retry next pass once more sibling aliases are available
                            }
                        }
                    }
                    for (final String unresolved : pending.keySet()) {
                        values.put(unresolved, null);
                    }
                    predicateEval.setOuterLateralContext(values);
                    return predicateEval.evaluate(expr, row);
                }
            });
            mode = WhereEvaluationMode.WITH_ALIASES;
        }

        OperatorContext context = contextBuilder.build();

        // Create and execute WHERE operator
        WhereOperator whereOperator = new WhereOperator(whereExpr, mode);
        return whereOperator.execute(rows, context);
    }

    /**
     * The SELECT-list aliases of {@code ctx} that this query's WHERE may reference, as alias to
     * defining-expression text. Skips an alias that merely renames an existing column of {@code table} (that
     * name resolves on its own) and any aggregate/window item — those cannot be evaluated per row and are not
     * legal in WHERE anyway. Empty when there is nothing to offer, leaving the WHERE path exactly as it was.
     */
    private Map<String, String> selectAliasExpressions(final FrostlakeParser.SelectClauseContext ctx,
                                                       final Table table) {
        final Map<String, String> aliases = new HashMap<>();
        if (ctx == null || ctx.selectList() == null) {
            return aliases;
        }
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (!SelectItemAccessors.isExprItem(item) || SelectItemAccessors.getItemAlias(item) == null) {
                continue;
            }
            final FrostlakeParser.ExpressionContext valueExpr = SelectItemAccessors.getItemValueExpr(item);
            if (valueExpr == null || hasAggregateFunctionInExpression(valueExpr)) {
                continue;
            }
            final String alias = ParseTreeText.getIdentifier(SelectItemAccessors.getItemAlias(item)).toUpperCase();
            // NB: Table.getColumnIndex THROWS for an unknown name — use hasColumn, which is the whole point
            // here (the alias we care about is precisely the one that is NOT a column).
            if (table != null && table.hasColumn(alias)) {
                continue;
            }
            aliases.put(alias, getOriginalText(SelectItemAccessors.getItemExpression(item)));
        }
        return aliases;
    }


    /**
     * Apply JOIN operation using operator pipeline.
     */
    /** Equi-join on the named columns (shared by USING and NATURAL). Rows match when every join column is
     *  equal on both sides; an empty column list matches every pair (a cross join). */
    private List<Row> executeUsingJoin(final List<Row> leftRows, final Table leftTable,
                                       final List<Row> rightRows, final Table rightTable,
                                       final JoinType joinType, final List<String> joinColumns) {
        final Table lt = leftTable;
        final Table rt = rightTable;
        final JoinConditionEvaluator usingEval = new JoinConditionEvaluator() {
            @Override
            public boolean matches(final Row leftRow, final Row rightRow) {
                for (final String col : joinColumns) {
                    final int lIdx = lt.getColumnIndex(col);
                    final int rIdx = rt.getColumnIndex(col);
                    if (lIdx < 0 || rIdx < 0) {
                        return false;
                    }
                    final Object lv = leftRow.getValue(lIdx);
                    final Object rv = rightRow.getValue(rIdx);
                    if (lv == null && rv == null) {
                        continue;
                    }
                    if (lv == null || rv == null) {
                        return false;
                    }
                    if (!lv.toString().equals(rv.toString())) {
                        return false;
                    }
                }
                return true;
            }
        };
        final OperatorContext context = OperatorContext.builder()
            .table(leftTable).functionRegistry(functionRegistry).queryExecutor(this).build();
        final JoinOperator joinOp = new JoinOperator(leftTable, rightTable, rightRows, joinType, null, usingEval);
        return joinOp.execute(leftRows, context);
    }

    /** Column names present in both tables (case-insensitive), in the left table's order — the join keys of
     *  a NATURAL join. */
    private List<String> commonColumnNames(final Table left, final Table right) {
        final List<String> common = new ArrayList<>();
        for (final TableColumn lc : left.getColumns()) {
            for (final TableColumn rc : right.getColumns()) {
                if (rc.getName().equalsIgnoreCase(lc.getName())) {
                    common.add(lc.getName().toUpperCase());
                    break;
                }
            }
        }
        return common;
    }

    /** Cross (cartesian) join of two row sets — used for comma-separated sources in UPDATE…FROM / DELETE…USING. */
    List<Row> crossJoinRows(final List<Row> leftRows, final Table leftTable,
                            final List<Row> rightRows, final Table rightTable) {
        final OperatorContext context = OperatorContext.builder()
            .table(leftTable)
            .functionRegistry(functionRegistry).queryExecutor(this)
            .build();
        return JoinOperator.cross(leftTable, rightTable, rightRows).execute(leftRows, context);
    }

    /** The upper-cased identifier tokens of {@code sql}, collected by LEXING it (never substring/regex). */
    private static Set<String> lexedIdentifiers(final String sql) {
        final Set<String> out = new HashSet<>();
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        for (Token t = lexer.nextToken(); t.getType() != Token.EOF; t = lexer.nextToken()) {
            if (t.getType() == FrostlakeLexer.IDENTIFIER) {
                out.add(t.getText().toUpperCase());
            } else if (t.getType() == FrostlakeLexer.QUOTED_IDENTIFIER && t.getText().length() >= 2) {
                out.add(t.getText().substring(1, t.getText().length() - 1).toUpperCase());
            }
        }
        return out;
    }

    List<Row> applyJoin(final List<Row> leftRows, final Table leftTable,
                                final List<Row> rightRows, final Table rightTable,
                                final FrostlakeParser.JoinClauseContext joinCtx,
                                final Map<String, Table> aliasToTable,
                                final List<Table> allTables,
                                final FrostlakeParser.SelectClauseContext selectCtx) {
        // Determine join type
        JoinType joinType = JoinType.INNER; // default
        if (joinCtx.joinType() != null) {
            if (joinCtx.joinType().LEFT() != null) {
                joinType = JoinType.LEFT;
            } else if (joinCtx.joinType().RIGHT() != null) {
                joinType = JoinType.RIGHT;
            } else if (joinCtx.joinType().FULL() != null) {
                joinType = JoinType.FULL;
            } else if (joinCtx.joinType().CROSS() != null) {
                joinType = JoinType.CROSS;
            }
        }

        // NATURAL JOIN: an implicit equi-join on the columns common to both inputs (no ON/USING). Handled
        // before the CROSS/no-condition check below, which a NATURAL join (also lacking ON/USING) would
        // otherwise fall into. No common columns ⇒ every pair matches (a cross join), as in standard SQL.
        if (joinCtx.NATURAL() != null) {
            return executeUsingJoin(leftRows, leftTable, rightRows, rightTable, joinType,
                commonColumnNames(leftTable, rightTable));
        }

        // Handle CROSS JOIN or no ON/USING condition
        if (joinType == JoinType.CROSS || (joinCtx.ON() == null && joinCtx.USING() == null)) {
            OperatorContext context = OperatorContext.builder()
                .table(leftTable)
                .functionRegistry(functionRegistry).queryExecutor(this)
                .build();
            JoinOperator joinOp = JoinOperator.cross(leftTable, rightTable, rightRows);
            return joinOp.execute(leftRows, context);
        }

        // Expand USING (col1, col2, ...) into an equi-join on those columns.
        if (joinCtx.USING() != null) {
            final List<String> usingCols = new ArrayList<>();
            // A USING column may arrive qualified (USING (t2.c)); only the column part joins.
            for (final FrostlakeParser.QualifiedNameContext qn : joinCtx.usingColumnList().qualifiedName()) {
                final List<FrostlakeParser.IdentifierContext> parts = qn.identifier();
                usingCols.add(SqlIdentifiers.canonical(parts.get(parts.size() - 1)));
            }
            return executeUsingJoin(leftRows, leftTable, rightRows, rightTable, joinType, usingCols);
        }

        // Get join condition expression
        String joinCondition = getOriginalText(joinCtx.booleanExpr());

        // Parse the condition AST and build the alias-aware multi-table evaluator ONCE, then reuse both
        // for every candidate row-pair below. The old path re-extracted the text, re-parsed it, and
        // allocated a fresh evaluator on every pair — quadratic for non-equi (nested-loop) joins.
        final Expression joinConditionAst = ExpressionEvaluator.parse(joinCondition);
        final ExpressionEvaluator joinConditionEval =
            new ExpressionEvaluator(leftTable, functionRegistry, catalog, this);
        joinConditionEval.setMultiTableContext(aliasToTable, allTables);

        // Snowflake lets the ON condition reference a SELECT-list alias of the same query
        // (… HASH(…) AS _chk … JOIN r ON r.chk != _chk). Collect the aliases this ON actually mentions
        // (identified by LEXING the condition, not by substring matching); each is evaluated per candidate
        // pair against the combined row and offered as lateral context — a real column of the same name
        // still wins, because the evaluator consults the tables first. Aliases chain (… UPPER(c.st) _st,
        // HASH(…, _st, …) _chk … ON m.ck != _chk), so the collection is the TRANSITIVE closure: every
        // alias reachable from the ON through alias-defining expressions is pulled in too.
        final Map<String, Expression> onAliasAsts = new LinkedHashMap<>();
        if (selectCtx != null) {
            final Map<String, String> selectAliases = selectAliasExpressions(selectCtx, leftTable);
            if (!selectAliases.isEmpty()) {
                final Set<String> referenced = new HashSet<>(lexedIdentifiers(joinCondition));
                boolean grew = true;
                while (grew) {
                    grew = false;
                    for (final Map.Entry<String, String> alias : selectAliases.entrySet()) {
                        if (referenced.contains(alias.getKey()) && !onAliasAsts.containsKey(alias.getKey())) {
                            onAliasAsts.put(alias.getKey(), ExpressionEvaluator.parse(alias.getValue()));
                            referenced.addAll(lexedIdentifiers(alias.getValue()));
                            grew = true;
                        }
                    }
                }
            }
        }

        // Create condition evaluator that combines rows and evaluates the condition
        JoinConditionEvaluator conditionEvaluator = new JoinConditionEvaluator() {
            @Override
            public boolean matches(final Row leftRow, final Row rightRow) {
                // Combine rows to evaluate condition
                List<Object> combinedValues = new ArrayList<>(leftRow.getValues());
                combinedValues.addAll(rightRow.getValues());
                Row combinedRow = new Row(combinedValues);

                try {
                    if (!onAliasAsts.isEmpty()) {
                        // Compute the referenced aliases for THIS pair, with no stale context in scope.
                        // Chained aliases resolve iteratively: each pass offers the values computed so
                        // far and retries the rest, until a pass makes no progress.
                        final Map<String, Object> aliasValues = new HashMap<>();
                        final Map<String, Expression> pending = new LinkedHashMap<>(onAliasAsts);
                        boolean progressed = true;
                        while (progressed && !pending.isEmpty()) {
                            progressed = false;
                            final Iterator<Map.Entry<String, Expression>> pendingIt = pending.entrySet().iterator();
                            while (pendingIt.hasNext()) {
                                final Map.Entry<String, Expression> aliasAst = pendingIt.next();
                                joinConditionEval.setOuterLateralContext(aliasValues);
                                try {
                                    aliasValues.put(aliasAst.getKey(),
                                        joinConditionEval.evaluate(aliasAst.getValue(), combinedRow));
                                    pendingIt.remove();
                                    progressed = true;
                                } catch (final RuntimeException notComputableYet) {
                                    // retry next pass once more aliases are bound
                                }
                            }
                        }
                        joinConditionEval.setOuterLateralContext(aliasValues);
                    }
                    // Evaluate the pre-parsed AST with the shared evaluator (qualified columns resolve
                    // per-table across the join; AND/OR/NOT handled natively by the AST evaluator).
                    Object result = joinConditionEval.evaluate(joinConditionAst, combinedRow);
                    return SqlTruth.isTrue(result);
                } catch (final RuntimeException e) {
                    logger.warn("Failed to evaluate join condition: {}", e.getMessage());
                    return false;
                }
            }
        };

        // Build operator context
        OperatorContext context = OperatorContext.builder()
            .table(leftTable)
            .functionRegistry(functionRegistry).queryExecutor(this)
            .aliasToTable(aliasToTable)
            .allTables(allTables)
            .build();

        // Hash-join candidate filter for equi-join conditions (nested-loop fallback otherwise).
        final int[][] equiKeys = extractEquiJoinKeys(joinConditionAst,
            leftTable, rightTable, aliasToTable);

        // Create and execute JOIN operator
        JoinOperator joinOp = new JoinOperator(leftTable, rightTable, rightRows,
            joinType, joinCondition, conditionEvaluator);
        if (equiKeys != null) {
            joinOp.withEquiKeys(equiKeys[0], equiKeys[1]);
        }
        return joinOp.execute(leftRows, context);
    }

    /** Join two row sets on an arbitrary condition text for a given join type (the shared ON-join body,
     *  used by the Oracle {@code (+)} outer-join rewrite). {@code aliasToTable}/{@code allTables} must be in
     *  left-then-right order so qualified columns resolve to the correct side of the combined row. */
    private List<Row> conditionJoin(final List<Row> leftRows, final Table leftTable,
            final List<Row> rightRows, final Table rightTable, final JoinType joinType,
            final String onText, final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final Expression onAst = ExpressionEvaluator.parse(onText);
        final ExpressionEvaluator onEval = new ExpressionEvaluator(leftTable, functionRegistry, catalog, this);
        onEval.setMultiTableContext(aliasToTable, allTables);
        final JoinConditionEvaluator conditionEvaluator = new JoinConditionEvaluator() {
            @Override
            public boolean matches(final Row leftRow, final Row rightRow) {
                final List<Object> combined = new ArrayList<>(leftRow.getValues());
                combined.addAll(rightRow.getValues());
                final Object r = onEval.evaluate(onAst, new Row(combined));
                return SqlTruth.isTrue(r);
            }
        };
        final OperatorContext context = OperatorContext.builder()
            .table(leftTable).functionRegistry(functionRegistry).queryExecutor(this)
            .aliasToTable(aliasToTable).allTables(allTables).build();
        final int[][] equiKeys = extractEquiJoinKeys(onAst, leftTable, rightTable, aliasToTable);
        final JoinOperator joinOp = new JoinOperator(leftTable, rightTable, rightRows, joinType, onText, conditionEvaluator);
        if (equiKeys != null) {
            joinOp.withEquiKeys(equiKeys[0], equiKeys[1]);
        }
        return joinOp.execute(leftRows, context);
    }

    /** True when the FROM clause aliased its single table to a name other than the table's own. */
    private static boolean hasAliasDistinctFromTableName(final Map<String, Table> aliasToTable,
                                                         final Table table) {
        if (aliasToTable == null || table == null) {
            return false;
        }
        for (final String alias : aliasToTable.keySet()) {
            if (!alias.equalsIgnoreCase(table.getName())) {
                return true;
            }
        }
        return false;
    }

    /** Recursively flatten a WHERE booleanExpr's TOP-LEVEL AND conjuncts (an OR/NOT/leaf stops the split). */
    private void collectConjuncts(final FrostlakeParser.BooleanExprContext expr,
                                  final List<FrostlakeParser.BooleanExprContext> out) {
        if (expr instanceof FrostlakeParser.AndExprContext) {
            final FrostlakeParser.AndExprContext and = (FrostlakeParser.AndExprContext) expr;
            collectConjuncts(and.booleanExpr(0), out);
            collectConjuncts(and.booleanExpr(1), out);
        } else {
            out.add(expr);
        }
    }

    /** True if a parse subtree contains an Oracle {@code (+)} outer-join marker (an OuterJoinColumnExpr). */
    boolean containsOuterJoinMarker(final ParseTree tree) {
        if (tree instanceof FrostlakeParser.OuterJoinColumnExprContext) {
            return true;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            if (containsOuterJoinMarker(tree.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** Collect the (upper-cased) table-qualifier alias of every {@code (+)}-marked column in a parse subtree. */
    private void outerJoinMarkedAliases(final ParseTree tree, final Set<String> out) {
        if (tree instanceof FrostlakeParser.OuterJoinColumnExprContext) {
            final List<FrostlakeParser.IdentifierContext> parts =
                ((FrostlakeParser.OuterJoinColumnExprContext) tree).qualifiedName().identifier();
            if (parts.size() >= 2) {
                out.add(getIdentifier(parts.get(parts.size() - 2)).toUpperCase());
            }
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            outerJoinMarkedAliases(tree.getChild(i), out);
        }
    }

    /**
     * Apply projection (SELECT list evaluation) using operator pipeline.
     */
    private List<Row> applyProjection(final List<Row> rows, final Table table,
                                      final FrostlakeParser.SelectClauseContext ctx,
                                      final Map<String, Table> aliasToTable,
                                      final List<Table> allTables) {
        // Extract projection expressions from SELECT list
        List<String> projectionExpressions = new ArrayList<>();
        List<String> columnAliases = new ArrayList<>();

        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (SelectItemAccessors.isStarItem(item)) {
                // Expand STAR (honoring EXCLUDE/RENAME/REPLACE/ILIKE) to its effective columns.
                for (final StarColumn sc : expandStarColumns(item, table.getColumns(), "")) {
                    projectionExpressions.add(sc.getExpression());
                    columnAliases.add(sc.isRenamed() ? sc.getOutputName() : null);
                }
            } else if (SelectItemAccessors.isQualifiedStarItem(item) || SelectItemAccessors.isSpreadItem(item)) {
                // Expand t.* or t.** to all columns of the referenced table/alias
                String qualifier = SelectItemAccessors.getItemQualifier(item).toUpperCase();
                Table target = null;
                for (final Map.Entry<String, Table> e : aliasToTable.entrySet()) {
                    if (qualifier.equals(e.getKey().toUpperCase()) || qualifier.equals(e.getValue().getName().toUpperCase())) {
                        target = e.getValue();
                        break;
                    }
                }
                if (target == null) target = table;
                for (final TableColumn col : target.getColumns()) {
                    projectionExpressions.add(qualifier + "." + col.getName());
                    columnAliases.add(null);
                }
            } else if (SelectItemAccessors.isSpreadExprItem(item)) {
                // SELECT ** <array>: expand to one projection per element (the array must be constant —
                // the element COUNT fixes the column list before any row is seen). Each projection is
                // the 0-based element access; the label is '<item text>[N]' (1-based).
                final FrostlakeParser.SpreadExprItemContext spread =
                    (FrostlakeParser.SpreadExprItemContext) item;
                final String innerText = getOriginalText(spread.expression());
                final List<Object> spreadValues = spreadElements(new ExpressionEvaluator(
                    new Table("DUMMY", new ArrayList<>(), false), functionRegistry, catalog, this)
                    .evaluate(innerText, new Row(new ArrayList<>())));
                final String itemText = getOriginalText(item);
                for (int i = 0; i < spreadValues.size(); i++) {
                    projectionExpressions.add("(" + innerText + ")[" + i + "]");
                    columnAliases.add(itemText + "[" + (i + 1) + "]");
                }
            } else {
                // Regular expression select item
                String exprText = getOriginalText(SelectItemAccessors.getItemExpression(item));
                projectionExpressions.add(exprText);

                // Get alias if present
                String alias = null;
                if (SelectItemAccessors.getItemAlias(item) != null) {
                    alias = getIdentifier(SelectItemAccessors.getItemAlias(item));
                }
                columnAliases.add(alias);
            }
        }

        if (projectionExpressions.isEmpty()) {
            // A star over a zero-column source (e.g. GENERATOR) — Snowflake's error shape.
            throw new RuntimeException("SELECT with no columns");
        }

        // Apply masking policy substitution — wrap masked column expressions with policy body
        projectionExpressions = applyMaskingPolicies(projectionExpressions, table);

        // Lateral column aliases: a map shared between the projection evaluator (as its lateral context) and
        // the PROJECT operator, which fills it left-to-right as each aliased item is computed so a later item
        // can reference an earlier alias by name (Snowflake). The evaluator consults it only AFTER table
        // columns, so a real column of the same name still takes precedence.
        final Map<String, Object> lateralAliases = new HashMap<>();

        // Create expression evaluator based on context
        RowExpressionEvaluator expressionEvaluator;
        if (allTables.size() > 1) {
            // Multi-table query - use alias-aware evaluation
            final ExpressionEvaluator projEvalMt = new ExpressionEvaluator(table, functionRegistry, catalog, this);
            projEvalMt.setMultiTableContext(aliasToTable, allTables);
            projEvalMt.setOuterLateralContext(lateralAliases);
            expressionEvaluator = new RowExpressionEvaluator() {
                @Override
                public Object evaluate(final Expression expr, final Row row) {
                    return projEvalMt.evaluate(expr, row);
                }
            };
        } else {
            // Single table query - simple evaluation. The alias map still matters: a correlated
            // subquery references the outer row by its FROM alias (FROM idents ident … WHERE acc.ref =
            // ident.asset_key) — without it, the alias-qualified outer key was never assembled and the
            // strip-qualifier fallback bound the reference to the INNER table's same-named column, so
            // the correlation compared the inner row with itself.
            final ExpressionEvaluator projEval = new ExpressionEvaluator(table, functionRegistry, catalog, this);
            projEval.setMultiTableContext(aliasToTable, allTables);
            projEval.setOuterLateralContext(lateralAliases);
            expressionEvaluator = new RowExpressionEvaluator() {
                @Override
                public Object evaluate(final Expression expr, final Row row) {
                    return projEval.evaluate(expr, row);
                }
            };
        }

        // Build operator context
        OperatorContext context = OperatorContext.builder()
            .table(table)
            .functionRegistry(functionRegistry).queryExecutor(this)
            .aliasToTable(aliasToTable)
            .allTables(allTables)
            .build();

        // Create and execute PROJECT operator (lateralAliases is filled per-item by the operator)
        ProjectOperator projectOp = new ProjectOperator(projectionExpressions, columnAliases,
            expressionEvaluator, lateralAliases);
        return projectOp.execute(rows, context);
    }

    /**
     * Expand a {@code SELECT *} (or {@code t.*}) into its effective output columns after applying the item's
     * EXCLUDE / RENAME / REPLACE / ILIKE modifiers (Snowflake column-list modifiers). {@code qualifierPrefix}
     * is "" for a bare star, or "alias." for a qualified star, so each projected value expression resolves
     * against the intended table.
     */
    private List<StarColumn> expandStarColumns(final FrostlakeParser.SelectItemContext item,
                                               final List<TableColumn> columns, final String qualifierPrefix) {
        final Set<String> excluded = new HashSet<>();
        final Map<String, String> renames = new HashMap<>();
        final Map<String, String> replaces = new HashMap<>();
        Pattern ilike = null;
        for (final FrostlakeParser.StarModifierContext mod : SelectItemAccessors.getStarModifiers(item)) {
            if (mod.EXCLUDE() != null) {
                for (final FrostlakeParser.IdentifierContext id : mod.identifier()) {
                    excluded.add(getIdentifier(id).toUpperCase());
                }
            } else if (mod.ILIKE() != null) {
                final String lit = mod.STRING_LITERAL().getText();
                ilike = Pattern.compile(ilikeToRegex(lit.substring(1, lit.length() - 1)), Pattern.CASE_INSENSITIVE);
            } else if (mod.RENAME() != null) {
                for (final FrostlakeParser.StarRenameItemContext r : mod.starRenameItem()) {
                    renames.put(getIdentifier(r.identifier(0)).toUpperCase(), getIdentifier(r.identifier(1)));
                }
            } else if (mod.REPLACE() != null) {
                for (final FrostlakeParser.StarReplaceItemContext r : mod.starReplaceItem()) {
                    replaces.put(getIdentifier(r.identifier()).toUpperCase(), getOriginalText(r.expression()));
                }
            }
        }
        final List<StarColumn> result = new ArrayList<>();
        for (final TableColumn col : columns) {
            final String name = col.getName();
            final String key = name.toUpperCase();
            // Staged-file metadata columns resolve by name but never expand from * (Snowflake keeps
            // them out of SELECT * over a stage). Only these two: STREAM metadata (METADATA$ACTION,
            // METADATA$ISUPDATE, METADATA$ROW_ID) legitimately DOES appear in star expansion.
            if (key.equals("METADATA$FILENAME") || key.equals("METADATA$FILE_ROW_NUMBER")) {
                continue;
            }
            if (col.isHiddenFromStar()) {
                continue;   // the right-side duplicate of a USING / NATURAL join column
            }
            if (excluded.contains(key) || (ilike != null && !ilike.matcher(name).matches())) {
                continue;
            }
            final String replaceExpr = replaces.get(key);
            final String expression = replaceExpr != null ? replaceExpr : (qualifierPrefix + name);
            result.add(new StarColumn(expression, renames.getOrDefault(key, name), name, col.getDataType()));
        }
        return result;
    }

    /** Convert a SQL ILIKE pattern (%, _) into a case-insensitive regex for matching column names. */
    private static String ilikeToRegex(final String pattern) {
        final StringBuilder regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            final char c = pattern.charAt(i);
            if (c == '%') {
                regex.append(".*");
            } else if (c == '_') {
                regex.append('.');
            } else if ("[](){}+*?.^$|\\".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        return regex.toString();
    }

    /**
     * Apply GROUP BY using operator pipeline.
     */
    // Sentinel to distinguish ROLLUP/CUBE NULLs from real NULLs in GROUPING()
    private static final Object GROUPING_NULL = new Object() {
        @Override public String toString() { return "NULL"; }
    };

    private List<Row> applyGroupBy(final List<Row> rows, final Table table,
                                   final FrostlakeParser.SelectClauseContext ctx,
                                   final Map<String, Table> aliasToTable,
                                   final List<Table> allTables,
                                   final List<List<Row>> groupRowsSink) {
        return groupByEvaluator.applyGroupBy(rows, table, ctx, aliasToTable, allTables, groupRowsSink);
    }

    private List<Row> applyImplicitGroupBy(final List<Row> rows, final Table table,
                                           final FrostlakeParser.SelectClauseContext ctx,
                                           final Map<String, Table> aliasToTable, final List<Table> allTables,
                                           final List<List<Row>> groupRowsSink) {
        return groupByEvaluator.applyImplicitGroupBy(rows, table, ctx, aliasToTable, allTables, groupRowsSink);
    }

    /**
     * Apply HAVING clause using operator pipeline.
     * HAVING filters rows after GROUP BY based on aggregate conditions.
     */
    private List<Row> applyHaving(final List<Row> rows, final FrostlakeParser.SelectClauseContext ctx,
                                  final Table table, final Map<String, Table> aliasToTable,
                                  final List<Table> allTables, final List<List<Row>> groupRows) {
        String havingExpr = getOriginalText(ctx.havingClause().booleanExpr());
        final FrostlakeParser.BooleanExprContext havingBool = ctx.havingClause().booleanExpr();

        // Per output column, the canonical AST form of the SELECT item (+ its alias). A HAVING
        // condition referencing an alias / group column / an aggregate that IS a SELECT item resolves
        // to the already-computed value via this result context.
        final List<FrostlakeParser.SelectItemContext> selectItems = ctx.selectList().selectItem();
        final List<String> canonicalKeys = new ArrayList<>();
        final List<String> aliasKeys = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : selectItems) {
            String canonical = null;
            if (SelectItemAccessors.isExprItem(item)) {
                try {
                    canonical = AstPrinterVisitor.print(
                        ExpressionEvaluator.parse(getOriginalText(SelectItemAccessors.getItemExpression(item))));
                } catch (final RuntimeException ignored) { /* unparseable item — leave unmatched */ }
            }
            canonicalKeys.add(canonical);
            aliasKeys.add(SelectItemAccessors.getItemAlias(item) != null ? getIdentifier(SelectItemAccessors.getItemAlias(item)) : null);
        }

        // Map each output row to its source group rows, so an aggregate referenced in HAVING but NOT
        // projected in SELECT (e.g. SELECT grp, COUNT(*)::VARCHAR … HAVING COUNT(*) > 1) can be computed
        // fresh over the group instead of read back from the already-projected row. Keyed by identity —
        // HavingOperator passes the same Row instances through.
        final Map<Row, List<Row>> rowToGroup = new IdentityHashMap<>();
        if (groupRows != null) {
            for (int i = 0; i < rows.size() && i < groupRows.size(); i++) {
                rowToGroup.put(rows.get(i), groupRows.get(i));
            }
        }

        final ExpressionEvaluator ev = new ExpressionEvaluator(null, functionRegistry, catalog, this);
        HavingEvaluator havingEvaluator = new HavingEvaluator() {
            @Override
            public boolean evaluate(final Expression condition, final Row aggregatedRow) {
                final Map<String, Object> rc = new HashMap<>();
                final int n = Math.min(canonicalKeys.size(), aggregatedRow.getValues().size());
                for (int i = 0; i < n; i++) {
                    final Object v = aggregatedRow.getValue(i);
                    if (canonicalKeys.get(i) != null) rc.put(canonicalKeys.get(i), v);
                    if (aliasKeys.get(i) != null) {
                        rc.put(aliasKeys.get(i), v);
                        rc.put(aliasKeys.get(i).toUpperCase(), v);
                    }
                }
                // Aggregates in HAVING computed fresh over this row's group take precedence — they cover
                // aggregates that are not SELECT items and are always correct for the group.
                final List<Row> group = rowToGroup.get(aggregatedRow);
                if (group != null) {
                    rc.putAll(groupByEvaluator.havingAggregateContext(havingBool, group, table, aliasToTable, allTables));
                }
                ev.setResultContext(rc);
                final Object result = ev.evaluate(condition, aggregatedRow);
                return SqlTruth.isTrue(result);
            }
        };

        // Build operator context
        OperatorContext context = OperatorContext.builder()
            .table(null) // HAVING doesn't need the original table, it works on aggregated results
            .functionRegistry(functionRegistry).queryExecutor(this)
            .build();

        // Create and execute HAVING operator
        HavingOperator havingOp = new HavingOperator(havingExpr, havingEvaluator);
        return havingOp.execute(rows, context);
    }

    /**
     * Apply QUALIFY clause using operator pipeline.
     * QUALIFY filters rows based on window function results.
     */
    private List<Row> applyQualify(final List<Row> rows, final FrostlakeParser.SelectClauseContext ctx,
                                    final Map<Integer, Map<Integer, Object>> windowFunctionResults,
                                    final Table table, final boolean rowsAreBaseShape) {
        final FrostlakeParser.BooleanExprContext qualifyBool = ctx.qualifyClause().booleanExpr();
        final String qualifyExpr = getOriginalText(qualifyBool);

        // Over BASE-shape rows a QUALIFY reference to a NON-window select alias (QUALIFY score > 1 with
        // `x+y AS score`) cannot resolve positionally — supply each such alias by evaluating its defining
        // expression on the row.
        final Map<String, String> nonWindowAliasExprs = new LinkedHashMap<>();
        if (rowsAreBaseShape) {
            for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
                if (!SelectItemAccessors.isExprItem(item) || SelectItemAccessors.getItemAlias(item) == null) {
                    continue;
                }
                if (windowEvaluator.hasWindowFunctionInExpression(SelectItemAccessors.getItemValueExpr(item))) {
                    continue;
                }
                nonWindowAliasExprs.put(getIdentifier(SelectItemAccessors.getItemAlias(item)).toUpperCase(),
                    getOriginalText(SelectItemAccessors.getItemExpression(item)));
            }
        }

        // Window functions written INLINE in QUALIFY (e.g. QUALIFY ROW_NUMBER() OVER (...) = 1), rather
        // than referenced through a SELECT alias, are absent from windowFunctionResults. Precompute each
        // over all rows; each stays a WindowFunctionExpression node in the parsed predicate (keyed by its
        // source text) and the evaluator resolves it per row from the result context under that same key.
        final List<FrostlakeParser.FunctionCallExprContext> inlineWindowFns = new ArrayList<>();
        collectWindowFunctionCalls(qualifyBool, inlineWindowFns);
        final Map<String, List<Object>> inlineWindowValues = new LinkedHashMap<>();
        if (!inlineWindowFns.isEmpty()) {
            final Map<FrostlakeParser.OverClauseContext, Map<List<Object>, List<Row>>> overCache = new HashMap<>();
            // Expose the SELECT aliases so an inline QUALIFY window's PARTITION BY / ORDER BY can name one
            // (e.g. QUALIFY ROW_NUMBER() OVER (PARTITION BY <select-alias> ...) = 1).
            final Map<String, String> savedAliases = windowEvaluator.beginWindowAliasScope(ctx);
            try {
                for (final FrostlakeParser.FunctionCallExprContext wfn : inlineWindowFns) {
                    final List<Object> perRowValues = new ArrayList<>(rows.size());
                    for (int r = 0; r < rows.size(); r++) {
                        perRowValues.add(evaluateWindowFunction(wfn, rows, r, ctx, table, overCache));
                    }
                    inlineWindowValues.put(getOriginalText(wfn), perRowValues);
                }
            } finally {
                windowEvaluator.endWindowAliasScope(savedAliases);
            }
        }

        // Per select item, its alias (window-function results are keyed by select-item index). A
        // QUALIFY condition references window functions by their alias; resolve those to the
        // precomputed window value via the result context, and ordinary columns via the row.
        final List<FrostlakeParser.SelectItemContext> qSelectItems = ctx.selectList().selectItem();
        final List<String> qAliases = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : qSelectItems) {
            qAliases.add(SelectItemAccessors.getItemAlias(item) != null ? getIdentifier(SelectItemAccessors.getItemAlias(item)) : null);
        }
        final ExpressionEvaluator qEv = new ExpressionEvaluator(table, functionRegistry, catalog, this);
        QualifyEvaluator qualifyEvaluator = new QualifyEvaluator() {
            @Override
            public boolean evaluate(final Expression condition, final Row row, final int rowIndex) {
                final Map<Integer, Object> rowWindowResults = windowFunctionResults.get(rowIndex);
                final Map<String, Object> rc = new HashMap<>();
                if (rowWindowResults != null) {
                    for (int i = 0; i < qAliases.size(); i++) {
                        if (qAliases.get(i) != null && rowWindowResults.containsKey(i)) {
                            rc.put(qAliases.get(i), rowWindowResults.get(i));
                            rc.put(qAliases.get(i).toUpperCase(), rowWindowResults.get(i));
                        }
                    }
                }
                for (final Map.Entry<String, List<Object>> wf : inlineWindowValues.entrySet()) {
                    // Keyed by the inline window call's source text — matches its WindowFunctionExpression node.
                    rc.put(wf.getKey(), wf.getValue().get(rowIndex));
                }
                for (final Map.Entry<String, String> alias : nonWindowAliasExprs.entrySet()) {
                    if (!rc.containsKey(alias.getKey())) {
                        try {
                            rc.put(alias.getKey(), evaluateExpression(alias.getValue(), row, table));
                        } catch (final RuntimeException unavailableHere) {
                            // The alias's defining expression cannot evaluate on this row — leave it
                            // unresolved so a real column of the same name still resolves normally.
                        }
                    }
                }
                qEv.setResultContext(rc);
                final Object result = qEv.evaluate(condition, row);
                return SqlTruth.isTrue(result);
            }
        };

        // Build operator context
        OperatorContext context = OperatorContext.builder()
            .table(table)
            .functionRegistry(functionRegistry).queryExecutor(this)
            .build();

        // Create and execute QUALIFY operator
        QualifyOperator qualifyOp = new QualifyOperator(qualifyExpr, qualifyEvaluator);
        return qualifyOp.execute(rows, context);
    }

    /**
     * Collect window-function calls ({@code fn(...) OVER (...)}) anywhere in a parse subtree. Does not
     * descend into a window call's own OVER spec — those expressions belong to the window definition,
     * not the surrounding predicate.
     */
    private void collectWindowFunctionCalls(final ParseTree node,
                                            final List<FrostlakeParser.FunctionCallExprContext> out) {
        windowEvaluator.collectWindowFunctionCalls(node, out);
    }

    /**
     * Apply table function using operator pipeline.
     */
    private List<Row> applyTableFunction(final FrostlakeParser.ExpressionContext expr) {
        return applyTableFunction(expr, null);
    }

    private List<Row> applyTableFunction(final FrostlakeParser.ExpressionContext expr, final Map<String, Object> lateralContext) {
        // Build operator context
        OperatorContext context = OperatorContext.builder()
            .table(null) // Table functions don't have an input table
            .functionRegistry(functionRegistry).queryExecutor(this)
            .build();

        TableFunctionOperator tableFuncOp = createTableFunctionOperator(expr, lateralContext);
        return tableFuncOp.execute(new ArrayList<>(), context);
    }

    /**
     * Create a TableFunctionOperator from an expression context with lateral context. Resolution + execution
     * is done by the single {@link #executeTableFunction} path; its ResultSet is wrapped in a provider so the
     * operator pipeline ({@code applyTableFunction}) can consume the rows. (This used to carry a near-duplicate
     * copy of the dispatch that was missing the SYSTEM$ table-function branch.)
     */
    private TableFunctionOperator createTableFunctionOperator(final FrostlakeParser.ExpressionContext expr, final Map<String, Object> lateralContext) {
        return new TableFunctionOperator(tableFunctionName(expr), new ResultSetProvider() {
            @Override
            public ResultSet getResultSet() {
                return executeTableFunction(expr, lateralContext);
            }
        });
    }

    /** Best-effort display name of a table-function call expression (operator label / EXPLAIN). */
    private String tableFunctionName(final FrostlakeParser.ExpressionContext expr) {
        final String raw;
        if (expr instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            raw = ((FrostlakeParser.FunctionCallMixedArgsExprContext) expr).functionName().getText();
        } else if (expr instanceof FrostlakeParser.FunctionCallNamedArgsExprContext) {
            raw = ((FrostlakeParser.FunctionCallNamedArgsExprContext) expr).functionName().getText();
        } else if (expr instanceof FrostlakeParser.FunctionCallExprContext) {
            raw = ((FrostlakeParser.FunctionCallExprContext) expr).functionName().getText();
        } else {
            return "TABLE_FUNCTION";
        }
        return raw.contains(".") ? raw.substring(raw.lastIndexOf('.') + 1) : raw;
    }

    private Function resolveUdtf(final String name) {
        try {
            String db = catalog.getCurrentDatabase();
            String sc = catalog.getCurrentSchema();
            if (db == null || sc == null) return null;
            Schema schema = catalog.getDatabase(db).getSchema(sc);
            Function f = schema.getFunction(name);
            return f != null && f.isTableFunction() ? f : null;
        } catch (final Exception e) {
            return null;
        }
    }

    private String substituteSqlParams(final String body,
                                        final List<Parameter> params,
                                        final List<Object> args) {
        String sql = body;
        for (int i = 0; i < params.size() && i < args.size(); i++) {
            String paramName = params.get(i).getName();
            Object value = args.get(i);
            String literal = value == null ? "NULL"
                : value instanceof String ? "'" + value.toString().replace("'", "''") + "'"
                : value.toString();
            // Replace :name or just bare param name used as identifier
            sql = SqlIdentifierSubstitution.substitute(sql, paramName, literal);
        }
        return sql;
    }

    private boolean isSimpleStar(final FrostlakeParser.SelectClauseContext ctx) {
        return windowEvaluator.isSimpleStar(ctx);
    }

    private boolean hasAggregateFunction(final FrostlakeParser.SelectClauseContext ctx) {
        return windowEvaluator.hasAggregateFunction(ctx);
    }

    boolean hasAggregateFunctionInExpression(final FrostlakeParser.ExpressionContext expr) {
        return windowEvaluator.hasAggregateFunctionInExpression(expr);
    }

    private boolean hasWindowFunction(final FrostlakeParser.SelectClauseContext ctx) {
        return windowEvaluator.hasWindowFunction(ctx);
    }

    /** True when the expression contains a window call ({@code fn(...) OVER (...)}) anywhere. */
    boolean hasWindowFunctionInExpression(final FrostlakeParser.ExpressionContext expr) {
        return windowEvaluator.hasWindowFunctionInExpression(expr);
    }

    private Map<Integer, Map<Integer, Object>> computeWindowFunctions(final List<Row> rows, final FrostlakeParser.SelectClauseContext ctx, final Table table) {
        return windowEvaluator.computeWindowFunctions(rows, ctx, table);
    }

    /**
     * A synthetic table describing the SELECT list's OUTPUT shape — one column per select item, named by its
     * alias (else its derived output name) — used as the resolution table for the window stage when the rows
     * have already been projected by GROUP BY / implicit aggregation. A star item contributes the base
     * table's columns.
     */
    private Table projectedShapeTable(final FrostlakeParser.SelectClauseContext ctx, final Table base) {
        final List<TableColumn> columns = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (SelectItemAccessors.isStarItem(item)
                    || SelectItemAccessors.isQualifiedStarItem(item) || SelectItemAccessors.isSpreadItem(item)) {
                columns.addAll(base.getColumns());
                continue;
            }
            final String name = SelectItemAccessors.getItemAlias(item) != null
                ? getIdentifier(SelectItemAccessors.getItemAlias(item))
                : selectItemColumnName(SelectItemAccessors.getItemValueExpr(item));
            columns.add(new TableColumn(name, StringType.VARCHAR, true, null, false, false, false));
        }
        return new Table("__WINDOW_PROJECTED__", columns, false);
    }

    private List<Row> addWindowFunctionsToRows(final List<Row> rows,
                                                final Map<Integer, Map<Integer, Object>> windowFunctionResults,
                                                final FrostlakeParser.SelectClauseContext ctx,
                                                final Table table,
                                                final boolean rowsAreProjected,
                                                final List<String> extraOrderKeyExprs,
                                                final Map<Row, Object[]> extraKeyValuesOut) {
        return windowEvaluator.addWindowFunctionsToRows(
            rows, windowFunctionResults, ctx, table, rowsAreProjected, extraOrderKeyExprs, extraKeyValuesOut);
    }

    private Object evaluateWindowFunction(final FrostlakeParser.ExpressionContext expr,
                                          final List<Row> allRows, final int currentRowIndex,
                                          final FrostlakeParser.SelectClauseContext ctx, final Table table,
                                          final Map<FrostlakeParser.OverClauseContext, Map<List<Object>, List<Row>>> overCache) {
        return windowEvaluator.evaluateWindowFunction(expr, allRows, currentRowIndex, ctx, table, overCache);
    }

    List<Row> sortRowsForWindow(final List<Row> rows, final FrostlakeParser.OrderByClauseContext orderByClause, final Table table) {
        return windowEvaluator.sortRowsForWindow(rows, orderByClause, table);
    }

    private List<Row> projectRows(final List<Row> rows, final Table table, final FrostlakeParser.SelectClauseContext ctx,
                                  final Map<String, Table> aliasToTable, final List<Table> allTables) {
        // Project each row to only include selected columns
        List<Row> projectedRows = new ArrayList<>();
        for (final Row row : rows) {
            List<Object> projectedValues = new ArrayList<>();
            for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
                if (!SelectItemAccessors.isExprItem(item)) continue;
                String exprText = getOriginalText(SelectItemAccessors.getItemExpression(item));
                Object value;

                // For multi-table queries, use alias-aware evaluation
                if (allTables.size() > 1) {
                    value = evaluateExpressionWithAliases(exprText, row, table, aliasToTable, allTables);
                } else {
                    value = evaluateExpression(exprText, row, table);
                }

                projectedValues.add(value);
            }
            projectedRows.add(new Row(projectedValues));
        }
        return projectedRows;
    }

    private List<Row> orderBy(final List<Row> rows, final Table table, final FrostlakeParser.SelectStatementContext ctx,
                              final Map<String, Table> aliasToTable, final List<Table> allTables) {
        return orderByExecutor.orderBy(rows, table, ctx, aliasToTable, allTables);
    }

    private List<Row> orderByAfterGroupBy(final List<Row> rows, final FrostlakeParser.SelectStatementContext ctx) {
        return orderByExecutor.orderByAfterGroupBy(rows, ctx);
    }

    private List<Row> orderByAfterGroupBy(final List<Row> rows, final FrostlakeParser.SelectStatementContext ctx,
                                          final GroupOrderKeyResolver resolver) {
        return orderByExecutor.orderByAfterGroupBy(rows, ctx, resolver);
    }

    /**
     * A resolver that computes an ORDER BY key not present in the SELECT list (a grouped column or an
     * aggregate) over the source group of each output row, keyed by the row's original pre-sort index.
     */
    private GroupOrderKeyResolver newGroupOrderResolver(final List<Row> outputRows,
            final Map<Row, List<Row>> rowToGroup, final Table table,
            final Map<String, Table> aliasToTable, final List<Table> allTables) {
        return new GroupOrderKeyResolver() {
            @Override
            public Object resolve(final int rowIndex, final FrostlakeParser.OrderItemContext item) {
                final List<Row> group = rowToGroup.get(outputRows.get(rowIndex));
                if (group == null || group.isEmpty()) {
                    return null;
                }
                return groupByEvaluator.evaluateOverGroup(item.expression(), group, table, aliasToTable, allTables);
            }
        };
    }

    /**
     * A resolver for a window-function query's ORDER BY key that is not selected: the value was precomputed
     * during the window projection (from the FROM columns, before they were dropped) and stored by
     * projected-row identity, so it survives QUALIFY. Looks it up by the row and the key's original text.
     */
    private GroupOrderKeyResolver newWindowOrderResolver(final List<Row> outputRows,
            final Map<Row, Object[]> keyValues, final Map<String, Integer> keyIndex) {
        return new GroupOrderKeyResolver() {
            @Override
            public Object resolve(final int rowIndex, final FrostlakeParser.OrderItemContext item) {
                final Object[] vals = keyValues.get(outputRows.get(rowIndex));
                final Integer idx = keyIndex.get(getOriginalText(item.expression()));
                if (vals == null || idx == null || idx >= vals.length) {
                    return null;
                }
                return vals[idx];
            }
        };
    }

    int getColumnIndex(final Table table, final String columnName) {
        return ValueComparisons.getColumnIndex(table, columnName);
    }

    Object evaluateExpression(final String expr, final Row row, final Table table) {
        return evaluateExpression(expr, row, table, null);
    }

    /**
     * As {@link #evaluateExpression(String, Row, Table)}, additionally offering {@code lateralAliases} — the
     * SELECT list's already-computed column aliases, keyed by uppercased alias. They are consulted only AFTER
     * the row's real columns, so a column of the same name keeps precedence.
     */
    Object evaluateExpression(final String expr, final Row row, final Table table,
                             final Map<String, Object> lateralAliases) {
        ExpressionEvaluator evaluator = new ExpressionEvaluator(table, functionRegistry, catalog, this);
        evaluator.setOuterLateralContext(lateralAliases);
        // When a window function / QUALIFY is computing over the rows of a JOIN, resolve alias-qualified
        // columns (e.g. o.region) with that join's alias context — otherwise they resolve to NULL
        // against the single merged table and every row collapses into one partition.
        final Deque<Map<String, Table>> aliasStack = windowAliasToTableStack.get();
        if (!aliasStack.isEmpty()) {
            evaluator.setMultiTableContext(aliasStack.peek(), windowAllTablesStack.get().peek());
        }
        return evaluator.evaluate(expr, row);
    }

    /**
     * The alias-to-table map of the JOIN whose rows the window stage is currently computing over, or null
     * when none is in force (single-table window, or no window computation). Package-private so
     * WindowFunctionEvaluator's nested-expression path can resolve alias-qualified columns the same way.
     */
    Map<String, Table> currentWindowAliasToTable() {
        final Deque<Map<String, Table>> stack = windowAliasToTableStack.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    /** The table list paired with {@link #currentWindowAliasToTable()}, or null when none is in force. */
    List<Table> currentWindowAllTables() {
        final Deque<List<Table>> stack = windowAllTablesStack.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    private Object evaluateExpressionWithAliases(final String expr, final Row row, final Table table,
                                                final Map<String, Table> aliasToTable, final List<Table> allTables) {
        return evaluateExpressionWithAliases(expr, row, table, aliasToTable, allTables, null);
    }

    private Object evaluateExpressionWithAliases(final String expr, final Row row, final Table table,
                                                final Map<String, Table> aliasToTable, final List<Table> allTables,
                                                final Map<String, Object> outerContext) {
        // Evaluate via the AST evaluator with alias-aware multi-table resolution — handles scalar
        // subqueries, object access (a:b), and qualified/bare column references uniformly. An outer
        // context supplies values for identifiers that are not columns (chained select-list aliases).
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(table, functionRegistry, catalog, this);
        evaluator.setMultiTableContext(aliasToTable, allTables);
        if (outerContext != null) {
            evaluator.setOuterLateralContext(outerContext);
        }
        return evaluator.evaluate(ExpressionEvaluator.parse(expr), row);
    }

    private int compareValues(final Object v1, final Object v2) {
        return ValueComparisons.compareValues(v1, v2);
    }

    public Catalog getCatalog() {
        return catalog;
    }

    /** transaction.deferredApply: whether DML is buffered into a write set and applied on COMMIT. Read live by write-path collaborators. */
    boolean isDeferredApply() {
        return deferredApply;
    }

    /** The session's procedural (Snowflake Scripting) executor — used to resolve :name bind variables. */
    public ProceduralExecutor getProceduralExecutor() {
        return visitor.getProceduralExecutor();
    }

    public EngineConfig getEngineConfig() {
        return engineConfig;
    }

    /** Resolver for s3:// (and S3-backed @stage) references to local files; null if no config was supplied. */
    public S3PathResolver getS3PathResolver() {
        return s3PathResolver;
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

    public StreamManager getStreamManager() {
        return streamManager;
    }

    public TaskScheduler getTaskScheduler() {
        return taskScheduler;
    }

    public ShowCommandExecutor getShowExecutor() {
        return showExecutor;
    }

    // ==================== SHOW STATEMENTS ====================

    /** DESCRIBE RESULT '&lt;queryId&gt;' | LAST_QUERY_ID() — the column metadata of a prior query's cached result. */
    public ResultSet describeResult(final FrostlakeParser.DescribeStatementContext ctx) {
        final String queryId = ctx.STRING_LITERAL() != null
            ? extractStringLiteral(ctx.STRING_LITERAL()) : resultCache.getLastQueryId();
        final ResultSet cached = queryId != null ? resultCache.getResult(queryId) : null;
        if (cached == null) {
            throw new RuntimeException("No cached result for query id: " + queryId);
        }
        final List<ResultSetColumn> cols = Arrays.asList(
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("type", StringType.VARCHAR),
            new ResultSetColumn("null?", StringType.VARCHAR));
        final List<Row> rows = new ArrayList<>();
        for (final ResultSetColumn c : cached.getColumns()) {
            rows.add(new Row(Arrays.asList(c.getName(), String.valueOf(c.getDataType()), "Y")));
        }
        return new ResultSet(cols, rows);
    }

    public ResultSet showQueryHistory(final Integer limit) {
        return showExecutor.showQueryHistory(limit);
    }


    /**
     * Execute PIVOT operation to transform rows into columns
     */
    /**
     * Apply a pivot alias's derived-column alias list — {@code PIVOT(…) AS p (empid, q1, q2)} — renaming the
     * pivoted result columns positionally. A pivot's column names are otherwise derived from the pivoted
     * values, so this list is the only way to name them, and an outer query that references those names (the
     * reason the list is written at all) cannot work without it.
     */
    private ResultSet renamePivotColumns(final ResultSet pivoted, final FrostlakeParser.PivotAliasContext aliasCtx) {
        if (aliasCtx == null || pivoted == null) {
            return pivoted;
        }
        final List<String> names = columnAliasList(aliasCtx.identifierList());
        if (names == null) {
            return pivoted;
        }
        final List<ResultSetColumn> renamed = new ArrayList<>();
        for (int i = 0; i < pivoted.getColumns().size(); i++) {
            final ResultSetColumn column = pivoted.getColumns().get(i);
            renamed.add(i < names.size()
                ? new ResultSetColumn(names.get(i), column.getDataType())
                : column);
        }
        return new ResultSet(renamed, pivoted.getRows());
    }

    private ResultSet executePivot(final FrostlakeParser.SelectClauseContext ctx, final Table table, final List<Row> rows, final FrostlakeParser.PivotClauseContext pivotCtx) {
        return pivotExecutor.executePivot(ctx, table, rows, pivotCtx);
    }

    /**
     * Execute UNPIVOT operation to transform columns into rows
     */
    private ResultSet executeUnpivot(final FrostlakeParser.SelectClauseContext ctx, final Table table, final List<Row> rows, final FrostlakeParser.UnpivotClauseContext unpivotCtx) {
        return pivotExecutor.executeUnpivot(ctx, table, rows, unpivotCtx);
    }

    /**
     * Apply aggregate function to a list of values
     */
    private Object applyAggregateFunction(final String funcName, final List<Object> values) {
        return AggregateFunctions.applyAggregateFunction(funcName, values);
    }

    /** True if any of these table references is a parenthesized FROM join — {@code FROM ( a JOIN b ON c )}. */
    private boolean hasParenthesizedJoin(final List<FrostlakeParser.TableReferenceContext> refs) {
        for (final FrostlakeParser.TableReferenceContext ref : refs) {
            if (isParenthesizedJoin(ref.tableSource())) {
                return true;
            }
        }
        return false;
    }

    /** A tableSource that is a parenthesized join {@code ( tableReference (, tableReference | joinClause)+ )} —
     *  the only tableSource alternative that carries child tableReferences. */
    private boolean isParenthesizedJoin(final FrostlakeParser.TableSourceContext src) {
        return src != null && src.tableReference() != null && !src.tableReference().isEmpty();
    }

    /** Execute a parenthesized join group — {@code ( a JOIN b ON ... )} — standalone: the inner chain
     *  runs first and its tables/aliases are registered into the CALLER's scope (so an outer ON
     *  condition can reference them); the returned {@link TableData} carries the combined rows and
     *  merged metadata. The combined row layout is the inner tables in join order, matching the
     *  per-table offsets the qualified-column resolver derives from {@code allTables}. */
    private TableData executeJoinGroup(final FrostlakeParser.TableSourceContext src,
                                       final Map<String, Table> aliasToTable, final List<Table> allTables,
                                       final FrostlakeParser.SelectClauseContext ctx,
                                       final Map<String, ResultSet> cteResults) {
        final List<FrostlakeParser.TableReferenceContext> innerRefs = new ArrayList<>();
        final List<FrostlakeParser.JoinClauseContext> innerJoins = new ArrayList<>();
        for (final FrostlakeParser.TableReferenceContext inner : src.tableReference()) {
            flattenParenthesizedJoin(inner, innerRefs, innerJoins);
        }
        for (final FrostlakeParser.JoinClauseContext join : src.joinClause()) {
            innerJoins.add(join);
            flattenParenthesizedJoin(join.tableReference(), innerRefs, innerJoins);
        }

        // The inner chain evaluates in its OWN scope: its combined rows contain only the group's
        // tables, so the offset-based qualified-column resolution must not see the outer tables.
        final Map<String, Table> groupAliases = new HashMap<>();
        final List<Table> groupTables = new ArrayList<>();

        final TableData firstData = executeTableReference(innerRefs.get(0), null, cteResults);
        Table groupTable = distinctJoinTable(firstData.table, allTables);
        List<Row> groupRows = firstData.rows;
        groupAliases.put(firstData.alias != null ? firstData.alias : groupTable.getName(), groupTable);
        groupTables.add(groupTable);

        int refIndex = 1;
        for (final FrostlakeParser.JoinClauseContext join : innerJoins) {
            final TableData rightData = executeTableReference(innerRefs.get(refIndex), null, cteResults);
            refIndex++;
            final Table rightJoinTable = distinctJoinTable(rightData.table, groupTables);
            groupAliases.put(rightData.alias != null ? rightData.alias : rightJoinTable.getName(), rightJoinTable);
            groupTables.add(rightJoinTable);
            groupRows = applyJoin(groupRows, groupTable, rightData.rows, rightJoinTable, join,
                groupAliases, groupTables, ctx);
            groupTable = mergeTableMetadata(groupTable, rightJoinTable,
                usingJoinColumnNames(join, groupTable, rightJoinTable));
        }

        // Now expose the group's tables/aliases to the caller — the outer ON references them, and the
        // outer combined-row layout is (left tables..., group tables in join order).
        aliasToTable.putAll(groupAliases);
        allTables.addAll(groupTables);
        return new TableData(groupTable, groupRows, null);
    }

    /** Expand a (possibly parenthesized-join) table reference into flat tableReference + joinClause lists: a
     *  parenthesized join contributes its inner references and joins (recursively, so grouped nesting like
     *  {@code ((a JOIN b) JOIN c)} flattens); an ordinary reference contributes itself. A join nested on the
     *  RIGHT of an inner join is left intact (executed as-is) — only leading/grouping nesting is flattened. */
    private void flattenParenthesizedJoin(final FrostlakeParser.TableReferenceContext ref,
                                          final List<FrostlakeParser.TableReferenceContext> outRefs,
                                          final List<FrostlakeParser.JoinClauseContext> outJoins) {
        final FrostlakeParser.TableSourceContext src = ref.tableSource();
        if (isParenthesizedJoin(src)) {
            for (final FrostlakeParser.TableReferenceContext inner : src.tableReference()) {
                flattenParenthesizedJoin(inner, outRefs, outJoins);
            }
            outJoins.addAll(src.joinClause());
        } else {
            outRefs.add(ref);
        }
    }

    /**
     * Execute a table reference (table or subquery) and return rows with table metadata, applying a trailing
     * SAMPLE / TABLESAMPLE clause if present.
     */
    TableData executeTableReference(final FrostlakeParser.TableReferenceContext ctx, final Map<String, Object> lateralContext, final Map<String, ResultSet> cteResults) {
        TableData resolved = resolveTableReference(ctx, lateralContext, cteResults);
        // An ANSI derived-column alias list renames the source's output columns: FROM t AS d (a, b). It was
        // parsed but only honoured for a few source kinds (subquery / VALUES / table function), so over a base
        // table, view or stream the grammar accepted it and the query then failed on the renamed columns.
        // Re-applying it to a source that already handled it is harmless (same names, same positions).
        final List<String> referenceAliases = columnAliasList(ctx.identifierList());
        if (referenceAliases != null) {
            resolved = new TableData(applyColumnAliases(resolved.table, referenceAliases), resolved.rows, resolved.alias);
        }
        if (ctx.sampleClause() == null) {
            return resolved;
        }
        return new TableData(resolved.table, sampleRows(resolved.rows, ctx.sampleClause()), resolved.alias);
    }

    /**
     * The names of a derived-column alias list ({@code (a, b, c)}), canonicalised the way every other
     * identifier is (unquoted names fold to upper case, quoted ones keep their case); null when absent.
     */
    List<String> columnAliasList(final FrostlakeParser.IdentifierListContext ctx) {
        if (ctx == null) {
            return null;
        }
        final List<String> names = new ArrayList<>();
        for (final FrostlakeParser.IdentifierContext idCtx : ctx.identifier()) {
            final String name = getIdentifier(idCtx);
            names.add(idCtx.QUOTED_IDENTIFIER() == null ? name.toUpperCase() : name);
        }
        return names;
    }

    /**
     * Apply a SAMPLE / TABLESAMPLE clause to a table source's rows. Fixed-size sampling ({@code SAMPLE (n
     * ROWS)}) keeps n rows; fractional sampling ({@code SAMPLE (p)}) keeps each row with probability p%.
     * A REPEATABLE/SEED makes the selection deterministic; without one it is random. BERNOULLI/ROW and
     * SYSTEM all sample per row in this in-memory engine (there are no storage blocks to sample).
     */
    private List<Row> sampleRows(final List<Row> rows, final FrostlakeParser.SampleClauseContext sample) {
        if (rows.isEmpty()) {
            return rows;
        }
        final double sizeVal;
        if (sample.SESSION_VAR_REF() != null) {
            // SAMPLE BERNOULLI ($s): the size comes from a session variable set with SET s = ...
            final String varName = sample.SESSION_VAR_REF().getText().substring(1).toUpperCase();
            final Object varValue = securityManager != null
                ? securityManager.getSessionContext().getSessionParameter(varName)
                : sessionVariables.get(varName);
            if (varValue == null) {
                throw new RuntimeException("Session variable not defined: $" + varName.toLowerCase());
            }
            sizeVal = Double.parseDouble(String.valueOf(varValue));
        } else {
            final String sizeText = (sample.INTEGER_LITERAL() != null ? sample.INTEGER_LITERAL() : sample.FLOAT_LITERAL()).getText();
            sizeVal = Double.parseDouble(sizeText);
        }
        final Random random = sample.sampleSeed() != null
            ? new Random(Long.parseLong(sample.sampleSeed().INTEGER_LITERAL().getText()))
            : new Random();

        if (sample.ROWS() != null) {
            final int n = Math.min((int) sizeVal, rows.size());
            final List<Row> shuffled = new ArrayList<>(rows);
            Collections.shuffle(shuffled, random);
            return new ArrayList<>(shuffled.subList(0, n));
        }
        // Fractional (percentage) sampling: keep each row with probability p/100.
        final double probability = sizeVal / 100.0;
        final List<Row> kept = new ArrayList<>();
        for (final Row row : rows) {
            if (random.nextDouble() < probability) {
                kept.add(row);
            }
        }
        return kept;
    }

    /** The results of the current ->> flow chain's already-executed stages (in order), or null when
     *  no chain is executing. A stage's $n table reference counts n statements BACK from itself. */
    private List<ResultSet> flowChainResults;

    private TableData resolveTableReference(final FrostlakeParser.TableReferenceContext ctx, final Map<String, Object> lateralContext, final Map<String, ResultSet> cteResults) {
        FrostlakeParser.TableSourceContext source = ctx.tableSource();
        String alias = null;
        if (ctx.identifier() != null) {
            alias = ctx.identifier().getText();
        } else if (ctx.nonJoinKeywordIdentifier() != null) {
            alias = ctx.nonJoinKeywordIdentifier().getText();
        }

        // Extract column aliases if provided (e.g., AS t(i,j))
        List<String> columnAliases = null;
        if (ctx.identifierList() != null) {
            columnAliases = new ArrayList<>();
            for (final FrostlakeParser.IdentifierContext idCtx : ctx.identifierList().identifier()) {
                String colName = getIdentifier(idCtx);
                // Uppercase unquoted identifiers (quoted identifiers keep their case)
                if (idCtx.QUOTED_IDENTIFIER() == null) {
                    colName = colName.toUpperCase();
                }
                columnAliases.add(colName);
            }
        }

        // Handle IDENTIFIER(expr) — dynamic table name from session variable or string
        if (source.KW_IDENTIFIER() != null && source.expression() != null) {
            ExpressionEvaluator eval = new ExpressionEvaluator(null, functionRegistry, catalog, this);
            Object nameVal = eval.evaluate(getOriginalText(source.expression()), null);
            if (nameVal == null) {
                throw new RuntimeException("IDENTIFIER() expression evaluated to null");
            }
            String dynamicTableName = nameVal.toString();
            // IDENTIFIER() can name a view / materialized view / dynamic table / stream, not only a base
            // table — resolve it the same way a direct FROM reference does. A common pattern is
            // `FROM IDENTIFIER(:src)` where :src toggles between a view or base table (backfill) and a
            // stream (incremental).
            final TableData identifierSource = resolveDynamicNamedSource(dynamicTableName, alias);
            if (identifierSource != null) {
                return identifierSource;
            }
            Table dynamicTable = catalog.resolveTable(dynamicTableName);
            String fqName = getFullyQualifiedTableName(dynamicTableName);
            // Overlay-aware read: a loader fills IDENTIFIER(:tmp) and joins it ONE statement later
            // inside the same explicit transaction — a raw scan() was blind to the buffered rows, so
            // the join silently saw an empty table.
            List<Row> dynamicRows = readTableRowsForTransaction(fqName);
            return new TableData(dynamicTable, new ArrayList<>(dynamicRows), alias != null ? alias : dynamicTableName);
        }

        // Handle FLATTEN(...) shorthand
        if (source.FLATTEN() != null && source.flattenArgList() != null) {
            Map<String, Object> namedArgs = new HashMap<>();
            FrostlakeParser.FlattenArgListContext fal = source.flattenArgList();
            if (fal.namedArgumentList() != null) {
                // All named: FLATTEN(INPUT => expr, OUTER => true, ...)
                for (final FrostlakeParser.NamedArgumentContext argCtx : fal.namedArgumentList().namedArgument()) {
                    String argName = argCtx.identifier().getText().toUpperCase();
                    String argValueExpr = namedArgumentText(argCtx);
                    Object argValue = lateralContext != null && !lateralContext.isEmpty()
                        ? evaluateExpressionWithLateralContextSimple(argValueExpr, lateralContext)
                        : evaluateExpression(argValueExpr, null, (Table) null);
                    namedArgs.put(argName, argValue);
                }
            } else {
                // Mixed: first positional arg = INPUT, then optional named args
                String inputExpr = getOriginalText(fal.expression());
                Object inputValue = lateralContext != null && !lateralContext.isEmpty()
                    ? evaluateExpressionWithLateralContextSimple(inputExpr, lateralContext)
                    : evaluateExpression(inputExpr, null, (Table) null);
                namedArgs.put("INPUT", inputValue);
                for (final FrostlakeParser.NamedArgumentContext argCtx : fal.namedArgument()) {
                    String argName = argCtx.identifier().getText().toUpperCase();
                    String argValueExpr = namedArgumentText(argCtx);
                    Object argValue = lateralContext != null && !lateralContext.isEmpty()
                        ? evaluateExpressionWithLateralContextSimple(argValueExpr, lateralContext)
                        : evaluateExpression(argValueExpr, null, (Table) null);
                    namedArgs.put(argName, argValue);
                }
            }
            TableFunction flattenFunc = functionRegistry.getTableFunction("FLATTEN");
            if (flattenFunc == null) throw new RuntimeException("FLATTEN function not available");
            ResultSet flattenResult = flattenFunc.execute(namedArgs);
            Table virtualTable = resultSetToTable(flattenResult, alias != null ? alias : "flatten");
            if (columnAliases != null) virtualTable = applyColumnAliases(virtualTable, columnAliases);
            return new TableData(virtualTable, flattenResult.getRows(), alias);
        }

        // Handle TABLE(function_call) - table function
        if (source.TABLE() != null && source.expression() != null) {
            List<Row> tableFunctionRows = applyTableFunction(source.expression(), lateralContext);
            // We need to convert rows back to ResultSet to get table metadata
            ResultSet tableFunctionResult = executeTableFunction(source.expression(), lateralContext);
            Table virtualTable = resultSetToTable(tableFunctionResult, alias != null ? alias : "table_function");

            // Apply column aliases if provided
            if (columnAliases != null) {
                virtualTable = applyColumnAliases(virtualTable, columnAliases);
            }

            return new TableData(virtualTable, tableFunctionRows, alias);
        }

        // Handle subquery
        if (source.selectStatement() != null) {
            ResultSet subqueryResult = executeSelectFromContext(source.selectStatement(), lateralContext);
            // Convert ResultSet to Table and rows
            Table virtualTable = resultSetToTable(subqueryResult, alias != null ? alias : "subquery");

            // Apply column aliases if provided
            if (columnAliases != null) {
                virtualTable = applyColumnAliases(virtualTable, columnAliases);
            }

            return new TableData(virtualTable, subqueryResult.getRows(), alias);
        }

        // $n — a prior flow-chain stage's result (n statements back from the current stage).
        if (source.POSITIONAL_PARAMETER() != null) {
            final int back = Integer.parseInt(source.POSITIONAL_PARAMETER().getText().substring(1));
            if (flowChainResults == null) {
                throw new RuntimeException(
                    "$" + back + " table references are only valid after ->> in a flow chain");
            }
            if (back < 1 || back > flowChainResults.size()) {
                throw new RuntimeException("$" + back
                    + " does not reference a previous statement in the flow chain");
            }
            final ResultSet stageResult = flowChainResults.get(flowChainResults.size() - back);
            if (stageResult == null) {
                throw new RuntimeException("$" + back
                    + " references a flow-chain statement that produced no result set");
            }
            final Table stageTable = resultSetToTable(stageResult, alias != null ? alias : "$" + back);
            return new TableData(stageTable, stageResult.getRows(), alias);
        }

        // Handle VALUES clause
        if (source.DIRECTORY() != null && source.stageRef() != null) {
            // DIRECTORY(@stage): the directory table of file-level stage metadata.
            return new StageQueryExecutor(this).directoryTable(source.stageRef(), alias);
        }
        if (source.stageRef() != null) {
            // FROM @stage[/path] [(FILE_FORMAT => ..., PATTERN => ...)]: query the staged files.
            return new StageQueryExecutor(this).queryStage(
                stageRefToLocation(source.stageRef()), source.stageQueryParams(), alias);
        }
        if (source.VALUES() != null && source.valueTupleList() != null) {
            // (VALUES ... [AS] v (c1, c2)) — Snowflake allows the alias inside the parens; an outer
            // alias (after the closing paren) wins when both are present.
            if (alias == null && source.identifier() != null) {
                alias = source.identifier().getText();
            }
            if (columnAliases == null && source.columnListOptional() != null) {
                columnAliases = new ArrayList<>();
                for (final FrostlakeParser.IdentifierContext idCtx
                        : source.columnListOptional().identifierList().identifier()) {
                    String colName = getIdentifier(idCtx);
                    if (idCtx.QUOTED_IDENTIFIER() == null) {
                        colName = colName.toUpperCase();
                    }
                    columnAliases.add(colName);
                }
            }
            List<Row> rows = new ArrayList<>();
            List<TableColumn> columns = new ArrayList<>();

            // Parse all value tuples
            for (final FrostlakeParser.ValueTupleContext tuple : source.valueTupleList().valueTuple()) {
                List<Object> values = new ArrayList<>();
                int colIndex = 0;

                // Parse each value as an expression
                for (final FrostlakeParser.ExpressionContext expr : tuple.valueList().expression()) {
                    String exprText = getOriginalText(expr);
                    // Create dummy table/row for expression evaluation
                    Table dummyTable = new Table("DUMMY", new ArrayList<>(), false);
                    Row dummyRow = new Row(new ArrayList<>());
                    ExpressionEvaluator evaluator = new ExpressionEvaluator(dummyTable, functionRegistry, catalog, this);
                    evaluator.setOuterLateralContext(lateralContext);
                    Object value = evaluator.evaluate(exprText, dummyRow);
                    values.add(value);

                    // Create columns based on first row
                    if (rows.isEmpty()) {
                        // Use column alias if provided, otherwise default to COLUMN1, COLUMN2, etc.
                        String colName;
                        if (columnAliases != null && colIndex < columnAliases.size()) {
                            colName = columnAliases.get(colIndex);
                        } else {
                            colName = "COLUMN" + (colIndex + 1);
                        }
                        DataType colType = inferDataType(value);
                        columns.add(new TableColumn(colName, colType, true, null, false, false, false));
                    }
                    colIndex++;
                }

                rows.add(new Row(values));
            }

            // Create virtual table
            String tableName = alias != null ? alias : "values";
            Table virtualTable = new Table(tableName, columns, false);
            return new TableData(virtualTable, rows, alias);
        }

        // A parenthesized join reaching here is one nested on the RIGHT of an outer join
        // (x JOIN (a JOIN b) ON ...); the leading/grouping form FROM (a JOIN b) ... is flattened before
        // we get here. The right-side form needs the inner aliases exposed to the outer ON condition —
        // not yet wired — so fail clearly instead of NPEing on the null qualifiedName below.
        if (isParenthesizedJoin(source)) {
            throw new RuntimeException("A parenthesized join is only supported as a leading FROM item, "
                + "not as the right side of another join");
        }

        // Handle regular table or CTE
        String tableName = getQualifiedName(source.qualifiedName());

        // Check if it's a CTE first (also check thread-local CTE context for UPDATE/DELETE)
        Map<String, ResultSet> effectiveCtes = cteResults != null ? cteResults
            : (currentCteContext != null ? currentCteContext : null);
        if (effectiveCtes != null && effectiveCtes.containsKey(tableName.toUpperCase())) {
            ResultSet cteResult = effectiveCtes.get(tableName.toUpperCase());
            Table virtualTable = resultSetToTable(cteResult, alias != null ? alias : tableName);
            // Make a defensive copy of the rows to avoid aliasing issues when CTE is used multiple times
            List<Row> rowsCopy = new ArrayList<>(cteResult.getRows());
            return new TableData(virtualTable, rowsCopy, alias);
        }

        // Check if it's an INFORMATION_SCHEMA system view first (before checking regular views)
        ResultSet systemViewResult = executeSystemViewIfApplicable(tableName, null);
        if (systemViewResult != null) {
            Table virtualTable = resultSetToTable(systemViewResult, alias != null ? alias : tableName);
            return new TableData(virtualTable, systemViewResult.getRows(), alias);
        }

        // Check if it's a view
        View view = null;
        try {
            view = resolveView(tableName);
        } catch (final Exception e) {
            // Not a view
        }

        if (view != null) {
            // Execute the view definition
            List<ResultSet> results = execute(view.getDefinition());
            ResultSet viewResult = results.isEmpty() ? null : results.get(0);
            Table virtualTable;
            if (view.hasExplicitColumnNames()) {
                virtualTable = resultSetToTable(viewResult, alias != null ? alias : tableName, view.getColumnNames());
            } else {
                virtualTable = resultSetToTable(viewResult, alias != null ? alias : tableName);
            }
            stampViewRowAccessPolicy(view, virtualTable);
            return new TableData(virtualTable, viewResult.getRows(), alias);
        }

        // MATERIALIZED VIEW / DYNAMIC TABLE — no backing storage is maintained, so materialize on read:
        // execute the defining query and expose its result set as a virtual table (correct results, not
        // an incrementally-refreshed cache).
        MaterializedView materializedView = null;
        try { materializedView = resolveMaterializedView(tableName); } catch (final Exception e) { /* not an MV */ }
        if (materializedView != null) {
            final List<ResultSet> results = execute(materializedView.getDefinition());
            final ResultSet mvResult = results.isEmpty() ? null : results.get(0);
            final Table virtualTable = materializedView.hasExplicitColumnNames()
                ? resultSetToTable(mvResult, alias != null ? alias : tableName, materializedView.getColumnNames())
                : resultSetToTable(mvResult, alias != null ? alias : tableName);
            return new TableData(virtualTable, mvResult.getRows(), alias);
        }

        DynamicTable dynamicTable = null;
        try { dynamicTable = resolveDynamicTable(tableName); } catch (final Exception e) { /* not a dynamic table */ }
        if (dynamicTable != null) {
            final List<ResultSet> results = execute(dynamicTable.getQuery());
            final ResultSet dtResult = results.isEmpty() ? null : results.get(0);
            final Table virtualTable = resultSetToTable(dtResult, alias != null ? alias : tableName);
            return new TableData(virtualTable, dtResult.getRows(), alias);
        }

        // Check if it's a STREAM — read its unconsumed change records as a virtual table source.
        final TableData streamData = resolveStreamTableData(tableName, alias);
        if (streamData != null) {
            // Time Travel (AT/BEFORE) does not apply to streams (Snowflake). Reject it rather than
            // silently reading the stream at its current offset as if the clause were absent.
            if (source.timeTravelClause() != null) {
                throw new RuntimeException(
                    "Time Travel (AT / BEFORE) is not supported on a stream: " + tableName);
            }
            return streamData;
        }

        // Regular table. A real (possibly user-defined) table wins — including one named DUAL, which
        // therefore shadows the DUAL pseudo-table fallback below.
        Table table;
        try {
            table = catalog.resolveTable(tableName);
        } catch (final RuntimeException notFound) {
            // DUAL — the legacy one-row pseudo-table used by `SELECT <expr> FROM DUAL`. It is synthesized
            // ONLY when nothing named DUAL exists (a real DUAL table/view/stream, resolved above or here,
            // shadows it) and is NOT registered in the catalog — so SHOW TABLES / INFORMATION_SCHEMA.TABLES
            // / DESCRIBE never list it. Shape: one nullable column COLUMN1, one row whose value is NULL.
            if ("DUAL".equalsIgnoreCase(tableName)) {
                final List<TableColumn> dualCols = new ArrayList<>();
                dualCols.add(new TableColumn("COLUMN1", StringType.VARCHAR, true, null, false, false, false));
                final List<Object> dualValues = new ArrayList<>();
                dualValues.add(null);
                final List<Row> dualRows = new ArrayList<>();
                dualRows.add(new Row(dualValues));
                return new TableData(new Table(alias != null ? alias : "DUAL", dualCols, false), dualRows, alias);
            }
            throw notFound;
        }
        String fullyQualifiedName = getFullyQualifiedTableName(tableName);
        StorageEngine.TableStorage tableStorage =
            storageEngine.getTableStorage(fullyQualifiedName);

        // Handle time travel clause: AT / BEFORE
        if (source.timeTravelClause() != null) {
            FrostlakeParser.TimeTravelClauseContext ttCtx = source.timeTravelClause();

            // CHANGES clause
            if (ttCtx.changesClause() != null) {
                List<Row> rows = executeChangesClause(ttCtx.changesClause(), tableStorage, table);
                return new TableData(table, rows, alias);
            }

            // AT / BEFORE
            boolean isBefore = ttCtx.BEFORE() != null && ttCtx.changesClause() == null;
            FrostlakeParser.TimeTravelPointContext ptCtx = ttCtx.timeTravelPoint();
            long targetMillis = resolveTimeTravelPoint(ptCtx);

            StorageEngine.TableStorage.Snapshot snap =
                isBefore ? tableStorage.snapshotBefore(targetMillis)
                         : tableStorage.snapshotAt(targetMillis);

            List<Row> rows = snap != null ? new ArrayList<>(snap.rows) : new ArrayList<>();
            return new TableData(table, rows, alias);
        }

        if (deferredApply && transactionManager.hasActiveTransaction()) {
            // Overlay this transaction's buffered writes on the committed base (READ COMMITTED for self).
            final List<Row> overlaid =
                transactionManager.getCurrentTransaction().getWriteSet().overlayRows(fullyQualifiedName, tableStorage);
            return new TableData(table, overlaid, alias);
        }
        List<Row> rows = tableStorage.scan();
        return new TableData(table, rows, alias);
    }

    /**
     * Rows of a table as THIS transaction sees them: the committed base overlaid with the transaction's
     * buffered writes when deferred-apply is active, else a plain scan. For read-only consumers (a MERGE
     * source, for example) — a raw {@code scan()} was blind to rows the same transaction had just
     * inserted, so a stage table populated and merged within one procedure produced an empty merge.
     */
    List<Row> readTableRowsForTransaction(final String fullyQualifiedName) {
        final StorageEngine.TableStorage tableStorage = storageEngine.getTableStorage(fullyQualifiedName);
        if (deferredApply && transactionManager.hasActiveTransaction()) {
            return transactionManager.getCurrentTransaction().getWriteSet().overlayRows(fullyQualifiedName, tableStorage);
        }
        return tableStorage.scan();
    }

    /** Resolve a stream by name in the current schema, or null if there is no such stream. */
    private Stream findStream(final String name) {
        if (name == null) {
            return null;
        }
        // The reference may be qualified (schema.stream or db.schema.stream) — resolve it against the
        // named schema rather than only the current one, falling back to the current db/schema for the
        // parts the reference omits. A bare name still resolves in the current schema.
        final QualifiedName qn = QualifiedName.parse(name);
        final String db = qn.size() == 3 ? qn.part(0) : catalog.getCurrentDatabase();
        final String sc = qn.size() >= 2 ? qn.part(qn.size() - 2) : catalog.getCurrentSchema();
        final String streamName = qn.last();
        if (db == null || sc == null) {
            return null;
        }
        try {
            return catalog.getDatabase(db).getSchema(sc).getStream(streamName.toUpperCase());
        } catch (final Exception e) {
            return null;   // getStream throws when the name is not a stream
        }
    }

    /**
     * A stream as a queryable table source: the source table's columns plus the change-tracking metadata
     * columns METADATA$ACTION / METADATA$ISUPDATE / METADATA$ROW_ID, one row per UNCONSUMED change record.
     * Reading is non-destructive; if this read happens inside a consuming DML (see consumingStreamContext)
     * the stream is queued to advance its offset once that DML succeeds. Returns null if not a stream.
     */
    /**
     * Resolve a runtime object name (e.g. from {@code IDENTIFIER(<expr>)}) to a non-base-table queryable
     * source — a view, materialized view, dynamic table, or stream — mirroring how a direct FROM reference
     * resolves. Returns null when the name is (or resolves to) a plain base table, which the caller reads
     * from storage directly.
     */
    private TableData resolveDynamicNamedSource(final String name, final String alias) {
        View view = null;
        try { view = resolveView(name); } catch (final Exception e) { /* not a view */ }
        if (view != null) {
            final List<ResultSet> results = execute(view.getDefinition());
            final ResultSet viewResult = results.isEmpty() ? null : results.get(0);
            final Table virtualTable = view.hasExplicitColumnNames()
                ? resultSetToTable(viewResult, alias != null ? alias : name, view.getColumnNames())
                : resultSetToTable(viewResult, alias != null ? alias : name);
            stampViewRowAccessPolicy(view, virtualTable);
            return new TableData(virtualTable, viewResult.getRows(), alias);
        }
        MaterializedView mv = null;
        try { mv = resolveMaterializedView(name); } catch (final Exception e) { /* not an MV */ }
        if (mv != null) {
            final List<ResultSet> results = execute(mv.getDefinition());
            final ResultSet mvResult = results.isEmpty() ? null : results.get(0);
            final Table virtualTable = mv.hasExplicitColumnNames()
                ? resultSetToTable(mvResult, alias != null ? alias : name, mv.getColumnNames())
                : resultSetToTable(mvResult, alias != null ? alias : name);
            return new TableData(virtualTable, mvResult.getRows(), alias);
        }
        DynamicTable dt = null;
        try { dt = resolveDynamicTable(name); } catch (final Exception e) { /* not a dynamic table */ }
        if (dt != null) {
            final List<ResultSet> results = execute(dt.getQuery());
            final ResultSet dtResult = results.isEmpty() ? null : results.get(0);
            final Table virtualTable = resultSetToTable(dtResult, alias != null ? alias : name);
            return new TableData(virtualTable, dtResult.getRows(), alias);
        }
        return resolveStreamTableData(name, alias);   // a stream, or null for a plain base table
    }

    TableData resolveStreamTableData(final String name, final String alias) {
        final Stream stream = findStream(name);
        if (stream == null) {
            return null;
        }
        final TableData data = stream.getSourceType() == StreamSourceType.VIEW
            ? buildViewStreamTableData(stream, alias)
            : buildTableStreamTableData(stream, alias);
        if (consumingStreamContext) {
            if (transactionManager.hasActiveTransaction()) {
                // DML: consume on commit — scoped to what THIS read saw (the committed prefix plus the
                // txn's already-buffered changes), so anything the txn writes AFTER the read stays.
                transactionManager.getCurrentTransaction().registerStreamConsumption(stream,
                    new StreamReadScope(stream.recordCount(), bufferedTransientRecords(stream)));
            } else {
                ctasStreamsRead.add(stream);   // CTAS (atomic DDL): consumed by consumeCtasStreams() after success
            }
        }
        return data;
    }

    private TableData buildTableStreamTableData(final Stream stream, final String alias) {
        final Table sourceTable = catalog.resolveTable(stream.getSourceTableName());
        final List<TableColumn> columns = new ArrayList<>(sourceTable.getColumns());
        appendStreamMetadataColumns(columns);
        final Table virtual = new Table(alias != null ? alias : stream.getName(), columns, false);

        final List<Row> rows = new ArrayList<>();
        for (final StreamRecord record : unconsumedNetRecords(stream)) {
            final List<Object> values = new ArrayList<>(record.getValues());
            appendStreamMetadataValues(values, record);
            rows.add(new Row(values));
        }
        return new TableData(virtual, rows, alias);
    }

    /**
     * A stream's net unconsumed records, folding in the current transaction's buffered (uncommitted) changes
     * to its base table(s) so a stream read inside a transaction reflects that transaction's own DML — the
     * deferred-apply equivalent of read-your-writes, which a stored procedure relies on when it inserts into a
     * table and then reads a stream over it in the same transaction. Works for both a TABLE stream (its source
     * table) and a VIEW stream (each UNION-ALL branch's base table): the write set's changed tables are matched
     * to the stream's captured base tables by bare name, and each match's buffered changes are synthesized as
     * transient records tagged with that bare name (so the view-branch routing in {@link #buildViewStreamTableData}
     * matches them the same way it matches committed records). Outside a transaction, or with nothing buffered
     * for a captured table, this is exactly {@link Stream#getUnconsumedNetRecords()}.
     */
    private List<StreamRecord> unconsumedNetRecords(final Stream stream) {
        final List<StreamRecord> buffered = bufferedTransientRecords(stream);
        return buffered.isEmpty()
            ? stream.getUnconsumedNetRecords()
            : stream.getUnconsumedNetRecordsWith(buffered);
    }

    /** The current transaction's buffered changes to {@code stream}'s base table(s), synthesized as
     *  transient stream records (see {@link #unconsumedNetRecords}); empty outside a transaction. Also
     *  captured at consuming-read registration as the read's seen-scope, so commit can consume exactly
     *  these once the write set re-emits them as real records. */
    private List<StreamRecord> bufferedTransientRecords(final Stream stream) {
        if (!transactionManager.hasActiveTransaction()) {
            return new ArrayList<>();
        }
        final Set<String> captureBareNames = streamCaptureBareNames(stream);
        final TransactionWriteSet writeSet = transactionManager.getCurrentTransaction().getWriteSet();
        final List<StreamRecord> buffered = new ArrayList<>();
        for (final String changedFqn : writeSet.changedTables()) {
            final String bare = QualifiedName.parse(changedFqn).last().toUpperCase();
            if (!captureBareNames.contains(bare) || !storageEngine.hasTable(changedFqn)) {
                continue;
            }
            buffered.addAll(writeSet.bufferedChangeRecords(changedFqn,
                storageEngine.getTableStorage(changedFqn), bare));
        }
        return buffered;
    }

    /** The bare (last-segment, upper-case) names of the base table(s) a stream captures: its source table
     *  for a TABLE stream, or every UNION-ALL branch base table for a VIEW stream. */
    private Set<String> streamCaptureBareNames(final Stream stream) {
        final Set<String> names = new HashSet<>();
        if (stream.getSourceType() == StreamSourceType.VIEW && !stream.getBaseTableNames().isEmpty()) {
            for (final String baseName : stream.getBaseTableNames()) {
                names.add(QualifiedName.parse(baseName).last().toUpperCase());
            }
        } else {
            names.add(QualifiedName.parse(stream.getSourceTableName()).last().toUpperCase());
        }
        return names;
    }

    /**
     * Materialize a VIEW-sourced stream: change records are captured against the view's base table,
     * then projected through the view's select list here — rows outside the view's WHERE predicate
     * are not part of the view, so their changes don't surface (an update that moves a row out of
     * the view surfaces only its DELETE half, and vice versa).
     */
    private TableData buildViewStreamTableData(final Stream stream, final String alias) {
        final View view = resolveView(stream.getSourceTableName());
        if (view == null) {
            throw new RuntimeException("Source view not found for stream "
                + stream.getName() + ": " + stream.getSourceTableName());
        }
        final List<FrostlakeParser.SelectClauseContext> branches = resolveViewStreamBranches(view);

        // Column shape follows the FIRST branch (a UNION ALL takes its output names/types from the
        // first query), then explicit view column names override when the counts match.
        final FrostlakeParser.SelectClauseContext firstBranch = branches.get(0);
        final Table firstBase = catalog.resolveTable(branchBaseTable(firstBranch));
        final List<TableColumn> columns = viewStreamColumns(firstBranch, firstBase);
        if (view.hasExplicitColumnNames() && view.getColumnNames().size() == columns.size()) {
            for (int i = 0; i < columns.size(); i++) {
                columns.set(i, new TableColumn(view.getColumnNames().get(i).toUpperCase(),
                    columns.get(i).getDataType(), true, null, false, false, false));
            }
        }
        appendStreamMetadataColumns(columns);
        final Table virtual = new Table(alias != null ? alias : stream.getName(), columns, false);

        // Each UNION ALL branch replays the change records captured from its own base table through its
        // own projection and filter; records are routed to a branch by the table they were captured from.
        final List<Row> rows = new ArrayList<>();
        final List<StreamRecord> netRecords = unconsumedNetRecords(stream);
        for (final FrostlakeParser.SelectClauseContext branch : branches) {
            final String branchTable = branchBaseTable(branch);
            // A record is routed to a branch by BARE table name: branchTable keeps its full qualification for
            // catalog.resolveTable (cross-schema views), but trackInsert tags records with the bare mutated
            // table name, so the branch match must compare last segments.
            final String branchTableBare = QualifiedName.parse(branchTable).last().toUpperCase();
            final Table baseTable = catalog.resolveTable(branchTable);
            final String whereText = branch.whereClause().isEmpty()
                ? null : getOriginalText(branch.whereClause().get(0).booleanExpr());
            final List<FrostlakeParser.SelectItemContext> items = branch.selectList().selectItem();
            final boolean identity = items.size() == 1 && SelectItemAccessors.isStarItem(items.get(0));
            final List<String> projectionExprs = new ArrayList<>();
            if (!identity) {
                for (final FrostlakeParser.SelectItemContext item : items) {
                    projectionExprs.add(getOriginalText(SelectItemAccessors.getItemExpression(item)));
                }
            }
            final ExpressionEvaluator projectionEval =
                new ExpressionEvaluator(baseTable, functionRegistry, catalog, this);
            for (final StreamRecord record : netRecords) {
                // A null tag (single-branch view, or a legacy record) is kept — a lone branch owns
                // every record; otherwise the record belongs to the branch it was captured from.
                if (record.getSourceTable() != null && !record.getSourceTable().equalsIgnoreCase(branchTableBare)) {
                    continue;
                }
                final Row baseRow = new Row(record.getValues());
                if (whereText != null) {
                    final Object keep = projectionEval.evaluate(whereText, baseRow);
                    if (!SqlTruth.isTrue(keep)) {
                        continue;
                    }
                }
                final List<Object> values = new ArrayList<>();
                if (identity) {
                    values.addAll(record.getValues());
                } else {
                    for (final String exprText : projectionExprs) {
                        values.add(projectionEval.evaluate(exprText, baseRow));
                    }
                }
                appendStreamMetadataValues(values, record);
                rows.add(new Row(values));
            }
        }
        return new TableData(virtual, rows, alias);
    }

    /**
     * Build the output columns for one view-stream branch: the base table's columns for a {@code SELECT *}
     * identity projection, otherwise one column per projected select item (alias name when present, else
     * the expression text; type taken from the base column when the item is a bare column, else VARIANT).
     */
    private List<TableColumn> viewStreamColumns(final FrostlakeParser.SelectClauseContext branch, final Table baseTable) {
        final List<FrostlakeParser.SelectItemContext> items = branch.selectList().selectItem();
        final boolean identity = items.size() == 1 && SelectItemAccessors.isStarItem(items.get(0));
        final List<TableColumn> columns = new ArrayList<>();
        if (identity) {
            columns.addAll(baseTable.getColumns());
        } else {
            for (final FrostlakeParser.SelectItemContext item : items) {
                final String exprText = getOriginalText(SelectItemAccessors.getItemExpression(item));
                final String columnName = SelectItemAccessors.getItemAlias(item) != null
                    ? getIdentifier(SelectItemAccessors.getItemAlias(item)) : exprText;
                final DataType columnType = baseTable.hasColumn(exprText.trim())
                    ? baseTable.getColumn(exprText.trim()).getDataType() : VariantType.VARIANT;
                columns.add(new TableColumn(columnName.toUpperCase(), columnType, true, null, false, false, false));
            }
        }
        return columns;
    }

    private void appendStreamMetadataColumns(final List<TableColumn> columns) {
        columns.add(new TableColumn("METADATA$ACTION", new StringType("VARCHAR", 16777216), true, null, false, false, false));
        columns.add(new TableColumn("METADATA$ISUPDATE", new BooleanType(), true, null, false, false, false));
        columns.add(new TableColumn("METADATA$ROW_ID", new StringType("VARCHAR", 16777216), true, null, false, false, false));
    }

    private void appendStreamMetadataValues(final List<Object> values, final StreamRecord record) {
        values.add(record.getChangeType().name());        // METADATA$ACTION: INSERT / DELETE
        values.add(record.isUpdate());                     // METADATA$ISUPDATE
        values.add(String.valueOf(record.getRowId()));     // METADATA$ROW_ID
    }

    /**
     * Resolve the base tables of a view eligible for change tracking (CREATE STREAM ON VIEW). A view
     * stream supports projections, WHERE filters, and {@code UNION ALL} over single-table branches —
     * {@code SELECT ... FROM t [WHERE ...] [UNION ALL SELECT ... FROM t2 [WHERE ...]] ...}. Plain
     * {@code UNION} (which deduplicates), joins, GROUP BY/HAVING/QUALIFY, DISTINCT, LIMIT, CTEs, and
     * subquery sources are rejected, matching Snowflake's change-tracking restrictions. Returns one
     * bare (upper-case) base table name per branch — the names change capture matches on.
     */
    public List<String> resolveViewStreamBaseTables(final View view) {
        final List<String> tables = new ArrayList<>();
        for (final FrostlakeParser.SelectClauseContext branch : resolveViewStreamBranches(view)) {
            tables.add(branchBaseTable(branch));
        }
        return tables;
    }

    /** Back-compat single-table resolver: the first (often only) branch's base table. */
    public String resolveViewStreamBaseTable(final View view) {
        return resolveViewStreamBaseTables(view).get(0);
    }

    /**
     * Parse and validate a change-tracking-eligible view, returning one {@code selectClause} per
     * branch (a plain view has one; a {@code UNION ALL} view has one per arm). Throws with a
     * descriptive message when the view uses a construct change tracking does not support.
     */
    private List<FrostlakeParser.SelectClauseContext> resolveViewStreamBranches(final View view) {
        final String reject = "CREATE STREAM on view " + view.getName()
            + ": change tracking supports projections, filters, and UNION ALL over single-table branches"
            + " (SELECT ... FROM t [WHERE ...] [UNION ALL ...]); plain UNION, DISTINCT, GROUP BY, QUALIFY,"
            + " LIMIT, joins, and subquery sources are not supported";
        final FrostlakeParser.SelectStatementContext sel;
        try {
            sel = parseSelectStatement(view.getDefinition());
        } catch (final Exception e) {
            throw new RuntimeException(reject, e);
        }
        if (sel.withClause() != null || sel.limitClause() != null || sel.fetchClause() != null) {
            throw new RuntimeException(reject);
        }
        for (final FrostlakeParser.SetOperatorContext op : sel.setOperator()) {
            // Only UNION ALL is allowed between branches: plain UNION deduplicates (which change
            // tracking cannot express), and INTERSECT / EXCEPT / MINUS are unsupported.
            if (op.UNION() == null || op.ALL() == null) {
                throw new RuntimeException(reject);
            }
        }
        final List<FrostlakeParser.SelectClauseContext> branches = new ArrayList<>();
        for (final FrostlakeParser.SelectOperandContext operand : sel.selectOperand()) {
            final FrostlakeParser.SelectClauseContext clause = operand.selectClause();
            if (clause == null || clause.DISTINCT() != null || clause.groupByClause() != null
                    || clause.havingClause() != null || clause.qualifyClause() != null
                    || clause.tableExpression() == null) {
                throw new RuntimeException(reject);
            }
            final FrostlakeParser.TableExpressionContext tableExpr = clause.tableExpression();
            if (tableExpr.tableReference().size() != 1 || !tableExpr.joinClause().isEmpty()
                    || tableExpr.tableReference(0).tableSource().qualifiedName() == null) {
                throw new RuntimeException(reject);
            }
            branches.add(clause);
        }
        return branches;
    }

    /** Bare (upper-case) base table name of a single view-stream branch. */
    private String branchBaseTable(final FrostlakeParser.SelectClauseContext branch) {
        // Keep the FULL qualification (e.g. BASE.FACT_X), not just the last segment: a stream ON a VIEW
        // whose base tables live in a DIFFERENT schema than the read context (e.g. a BASE_TRANSFORM view
        // over BASE.* tables) must resolve them by their own schema, not the current one. capturesTable()
        // already reduces a qualified capture name to its last segment before matching, so this is safe.
        final String[] parts = ParseTreeText.qualifiedNameParts(
            branch.tableExpression().tableReference(0).tableSource().qualifiedName());
        final StringBuilder qualified = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                qualified.append('.');
            }
            qualified.append(parts[i].toUpperCase());
        }
        return qualified.toString();
    }

    /**
     * Open the stream-consuming DML window: stream reads until {@link #restoreDmlStreamWindow} register on the
     * current transaction for offset-advance-on-commit. Called by {@code SQLCommandVisitor.visitDmlStatement}
     * around every DML dispatch (top-level, procedural body, task body). Returns the previous window state.
     */
    boolean enterDmlStreamWindow() {
        final boolean prev = consumingStreamContext;
        consumingStreamContext = true;
        return prev;
    }

    /** Close the stream-consuming DML window, restoring the state {@link #enterDmlStreamWindow} returned. */
    void restoreDmlStreamWindow(final boolean prev) {
        consumingStreamContext = prev;
    }

    /**
     * Execute the source SELECT of a CTAS in a stream-consuming context. Streams read are captured (CTAS has
     * no transaction to register on) and consumed by {@link #consumeCtasStreams()} once the table is created.
     */
    public ResultSet executeCtasSourceSelect(final FrostlakeParser.SelectStatementContext ctx) {
        final boolean prev = consumingStreamContext;
        consumingStreamContext = true;
        ctasStreamsRead.clear();
        try {
            return executeSelectFromContext(ctx);
        } finally {
            consumingStreamContext = prev;
        }
    }

    /** Advance the offset of streams read by the last CTAS source SELECT. Call only after the CTAS succeeds. */
    public void consumeCtasStreams() {
        for (final Stream stream : ctasStreamsRead) {
            stream.consume();
        }
        ctasStreamsRead.clear();
    }

    /** Resolve a timeTravelPoint context to epoch millis. */
    private long resolveTimeTravelPoint(final FrostlakeParser.TimeTravelPointContext ctx) {
        String valueExpr = getOriginalText(ctx.expression());
        Object value = evaluateExpression(valueExpr, null, (Table) null);

        if (ctx.TIMESTAMP() != null) {
            // TIMESTAMP => '<value>' or timestamp expression
            if (value instanceof LocalDateTime) {
                return ((LocalDateTime) value)
                    .toInstant(ZoneOffset.UTC).toEpochMilli();
            }
            if (value instanceof LocalDate) {
                return ((LocalDate) value).atStartOfDay()
                    .toInstant(ZoneOffset.UTC).toEpochMilli();
            }
            if (value instanceof Instant) {
                return ((Instant) value).toEpochMilli();
            }
            if (value instanceof Number) {
                // Unix epoch seconds — add 999ms to include all sub-second snapshots in that second
                return ((Number) value).longValue() * 1000L + 999L;
            }
            // Try parsing string
            try {
                return Instant.parse(value.toString()).toEpochMilli();
            } catch (final Exception e) {
                try {
                    return LocalDateTime.parse(value.toString())
                        .toInstant(ZoneOffset.UTC).toEpochMilli();
                } catch (final Exception e2) {
                    throw new RuntimeException("Cannot parse time travel timestamp: " + value);
                }
            }
        } else if (ctx.OFFSET() != null) {
            // OFFSET => -N (seconds, negative means past)
            long offsetSeconds = ((Number) value).longValue();
            return System.currentTimeMillis() + offsetSeconds * 1000L;
        } else if (ctx.STATEMENT() != null) {
            // STATEMENT => '<query_id>' — use snapshot closest to when that query ran
            String queryId = value.toString();
            QueryHistory qh = queryHistoryTracker.getQueryById(queryId);
            if (qh != null && qh.getStartTime() != null) {
                return qh.getStartTime().toInstant(ZoneOffset.UTC).toEpochMilli();
            }
            throw new RuntimeException("Query ID not found in history: " + queryId);
        } else if (ctx.STREAM() != null) {
            throw new RuntimeException(
                "Time travel AT (STREAM => ...) is not supported; query the stream object directly");
        }
        throw new RuntimeException("Unknown time travel point type");
    }

    /** Execute CHANGES clause — returns inserted/updated/deleted rows between two snapshots. */
    /** The elements of a SELECT-list spread (** <array>) value; the engine's ARRAY values are
     *  canonical JSON text, so both the native-List and JSON-text forms are accepted. */
    private List<Object> spreadElements(final Object value) {
        if (value instanceof List) {
            return new ArrayList<Object>((List<?>) value);
        }
        final JsonNode node = ArrayFunctionHelper.parseNode(value);
        if (node != null && node.isArray()) {
            final List<Object> elements = new ArrayList<>();
            for (final JsonNode element : node) {
                elements.add(ArrayFunctionHelper.fromNode(element));
            }
            return elements;
        }
        throw new RuntimeException("The spread operator (**) in the SELECT list requires an ARRAY value");
    }

    /** The evaluable source text of a named argument's value; a bare subquery value
     *  ({@code INPUT => SELECT ...}) is parenthesized into the scalar-subquery expression form. */
    private String namedArgumentText(final FrostlakeParser.NamedArgumentContext argCtx) {
        return argCtx.expression() != null
            ? getOriginalText(argCtx.expression())
            : "(" + getOriginalText(argCtx.selectStatement()) + ")";
    }

    private List<Row> executeChangesClause(final FrostlakeParser.ChangesClauseContext ctx,
                                            final StorageEngine.TableStorage storage,
                                            final Table table) {
        // Resolve start point
        long startMillis;
        if (ctx.AT_KEYWORD() != null && ctx.timeTravelPoint(0) != null) {
            startMillis = resolveTimeTravelPoint(ctx.timeTravelPoint(0));
        } else if (ctx.BEFORE() != null && ctx.timeTravelPoint(0) != null) {
            startMillis = resolveTimeTravelPoint(ctx.timeTravelPoint(0)) - 1;
        } else {
            startMillis = 0;
        }

        // Resolve end point (default = now)
        long endMillis = System.currentTimeMillis();
        if (ctx.END() != null && ctx.timeTravelPoint().size() > 1) {
            endMillis = resolveTimeTravelPoint(ctx.timeTravelPoint(ctx.timeTravelPoint().size() - 1));
        } else if (ctx.timeTravelPoint().size() == 2) {
            endMillis = resolveTimeTravelPoint(ctx.timeTravelPoint(1));
        }

        StorageEngine.TableStorage.Snapshot startSnap = storage.snapshotAt(startMillis);
        StorageEngine.TableStorage.Snapshot endSnap   = storage.snapshotAt(endMillis);

        Set<String> startKeys = new HashSet<>();
        Map<String, Row> startMap = new LinkedHashMap<>();
        if (startSnap != null) {
            for (final Row r : startSnap.rows) {
                String key = r.getValues().toString();
                startKeys.add(key); startMap.put(key, r);
            }
        }

        List<Row> changes = new ArrayList<>();
        Set<String> endKeys = new HashSet<>();
        if (endSnap != null) {
            for (final Row r : endSnap.rows) {
                String key = r.getValues().toString();
                endKeys.add(key);
                if (!startKeys.contains(key)) {
                    // Inserted row — add METADATA$ACTION = INSERT
                    changes.add(withChangeMetadata(r, "INSERT", "False"));
                }
            }
        }
        if (startSnap != null) {
            for (final Row r : startSnap.rows) {
                String key = r.getValues().toString();
                if (!endKeys.contains(key)) {
                    // Deleted row — add METADATA$ACTION = DELETE
                    changes.add(withChangeMetadata(r, "DELETE", "True"));
                }
            }
        }
        return changes;
    }

    private Row withChangeMetadata(final Row base, final String action, final String isUpdate) {
        List<Object> vals = new ArrayList<>(base.getValues());
        vals.add(action);
        vals.add(isUpdate);
        return new Row(vals);
    }

    /**
     * Execute a simple CROSS JOIN
     */
    private List<Row> executeCrossJoin(final List<Row> leftRows, final List<Row> rightRows) {
        List<Row> resultRows = new ArrayList<>();
        for (final Row leftRow : leftRows) {
            for (final Row rightRow : rightRows) {
                List<Object> combinedValues = new ArrayList<>(leftRow.getValues());
                combinedValues.addAll(rightRow.getValues());
                resultRows.add(new Row(combinedValues));
            }
        }
        return resultRows;
    }

    /**
     * A stored procedure declared {@code RETURNS TABLE(…)} used as a FROM source — {@code TABLE(proc(args))}.
     * Executes by synthesizing the equivalent CALL, so argument binding (defaults, named args), the
     * procedure's home-schema context, variable scoping and per-statement autocommit behave exactly as a
     * real CALL; the returned table's columns are then renamed to the DECLARED signature, as Snowflake
     * names them. Returns null when the name does not resolve to a procedure, so the caller reports the
     * unknown table function.
     */
    private ResultSet executeProcedureAsTableSource(final FrostlakeParser.FunctionCallExprContext funcCtx) {
        final Procedure procedure = resolveProcedureByFunctionName(funcCtx.functionName());
        if (procedure == null) {
            return null;
        }
        final StringBuilder call = new StringBuilder("CALL ");
        call.append(getOriginalText(funcCtx.functionName())).append('(');
        final List<FrostlakeParser.ExpressionContext> argExprs = funcArgExprs(funcCtx);
        for (int i = 0; i < argExprs.size(); i++) {
            if (i > 0) {
                call.append(", ");
            }
            call.append(getOriginalText(argExprs.get(i)));
        }
        call.append(')');
        // The procedure runs as PART of the enclosing statement (Snowflake: same transaction), so its
        // per-statement autocommit must not commit the enclosing INSERT/MERGE's implicit transaction.
        final List<ResultSet> results;
        transactionManager.beginAtomicSection();
        try {
            results = execute(call.toString());
        } finally {
            transactionManager.endAtomicSection();
        }
        final ResultSet callResult = results.isEmpty()
            ? new ResultSet(new ArrayList<>(), new ArrayList<>())
            : results.get(results.size() - 1);
        final List<Parameter> declared = procedure.getReturnColumns();
        if (declared.isEmpty() || declared.size() != callResult.getColumns().size()) {
            return callResult;
        }
        final List<ResultSetColumn> renamed = new ArrayList<>(declared.size());
        for (final Parameter col : declared) {
            renamed.add(new ResultSetColumn(col.getName(), col.getDataType()));
        }
        return new ResultSet(renamed, callResult.getRows());
    }

    /** Resolve a (possibly schema-/db-qualified) function-call name against the catalog's PROCEDURES. */
    private Procedure resolveProcedureByFunctionName(final FrostlakeParser.FunctionNameContext nameCtx) {
        final List<FrostlakeParser.IdentifierContext> ids = nameCtx.identifier();
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        try {
            final Schema schema;
            if (ids.size() == 3) {
                schema = catalog.getDatabase(getIdentifier(ids.get(0))).getSchema(getIdentifier(ids.get(1)));
            } else if (ids.size() == 2) {
                if (catalog.getCurrentDatabase() == null) {
                    return null;
                }
                schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(getIdentifier(ids.get(0)));
            } else {
                if (catalog.getCurrentDatabase() == null || catalog.getCurrentSchema() == null) {
                    return null;
                }
                schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(catalog.getCurrentSchema());
            }
            return schema.getProcedure(getIdentifier(ids.get(ids.size() - 1)).toUpperCase());
        } catch (final RuntimeException notAProcedure) {
            return null;
        }
    }

    /**
     * Execute a table function call
     */
    private ResultSet executeTableFunction(final FrostlakeParser.ExpressionContext expr) {
        return executeTableFunction(expr, null);
    }

    /**
     * Execute a table function call with lateral context
     */
    private ResultSet executeTableFunction(final FrostlakeParser.ExpressionContext expr, final Map<String, Object> lateralContext) {
        // Handle mixed positional+named args: FLATTEN(col, outer => true)
        if (expr instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            FrostlakeParser.FunctionCallMixedArgsExprContext mixCtx = (FrostlakeParser.FunctionCallMixedArgsExprContext) expr;
            String rawName = mixCtx.functionName().getText();
            String functionName = rawName.contains(".") ? rawName.substring(rawName.lastIndexOf('.') + 1) : rawName;
            TableFunction tableFunc = functionRegistry.getTableFunction(functionName);
            if (tableFunc == null) throw new RuntimeException("Unknown table function: " + rawName);
            Map<String, Object> namedArgs = new HashMap<>();
            String posExpr = getOriginalText(mixCtx.expression(0));   // table functions take one positional INPUT
            Object posVal = lateralContext != null && !lateralContext.isEmpty()
                ? evaluateExpressionWithLateralContextSimple(posExpr, lateralContext)
                : evaluateExpression(posExpr, null, (Table) null);
            namedArgs.put("INPUT", posVal);
            for (final FrostlakeParser.NamedArgumentContext argCtx : mixCtx.namedArgument()) {
                String argName = argCtx.identifier().getText().toUpperCase();
                String argValueExpr = getOriginalText(argCtx.expression());
                Object argValue = lateralContext != null && !lateralContext.isEmpty()
                    ? evaluateExpressionWithLateralContextSimple(argValueExpr, lateralContext)
                    : evaluateExpression(argValueExpr, null, (Table) null);
                namedArgs.put(argName, argValue);
            }
            return tableFunc.execute(namedArgs);
        }

        // Check if it's a function call with named arguments (e.g., GENERATOR)
        if (expr instanceof FrostlakeParser.FunctionCallNamedArgsExprContext) {
            FrostlakeParser.FunctionCallNamedArgsExprContext funcCtx =
                (FrostlakeParser.FunctionCallNamedArgsExprContext) expr;

            String rawName = funcCtx.functionName().getText();
            String functionName = rawName.contains(".") ? rawName.substring(rawName.lastIndexOf('.') + 1) : rawName;
            TableFunction tableFunc = functionRegistry.getTableFunction(functionName);

            if (tableFunc == null) {
                throw new RuntimeException("Unknown table function: " + rawName);
            }

            // Parse named arguments
            Map<String, Object> namedArgs = new HashMap<>();
            if (funcCtx.namedArgumentList() != null) {
                for (final FrostlakeParser.NamedArgumentContext argCtx : funcCtx.namedArgumentList().namedArgument()) {
                    String argName = argCtx.identifier().getText().toUpperCase();
                    String argValueExpr = namedArgumentText(argCtx);

                    // If lateral context is provided, try to resolve column references from it
                    Object argValue;
                    if (lateralContext != null && !lateralContext.isEmpty()) {
                        argValue = evaluateExpressionWithLateralContextSimple(argValueExpr, lateralContext);
                    } else {
                        argValue = evaluateExpression(argValueExpr, null, null);
                    }
                    namedArgs.put(argName, argValue);
                }
            }

            // Execute the table function
            return tableFunc.execute(namedArgs);
        }

        // Check if it's a regular function call (e.g., RESULT_SCAN)
        if (expr instanceof FrostlakeParser.FunctionCallExprContext) {
            FrostlakeParser.FunctionCallExprContext funcCtx =
                (FrostlakeParser.FunctionCallExprContext) expr;

            String rawFuncName2 = funcCtx.functionName().getText().toUpperCase();
            String functionName = rawFuncName2.contains(".") ? rawFuncName2.substring(rawFuncName2.lastIndexOf('.') + 1) : rawFuncName2;

            // Special handling for RESULT_SCAN
            if ("RESULT_SCAN".equals(functionName)) {
                ResultScan resultScan =
                    (ResultScan) functionRegistry.getTableFunction("RESULT_SCAN");

                if (resultScan == null) {
                    throw new RuntimeException("RESULT_SCAN table function not available");
                }

                // Parse the single argument (query ID or LAST_QUERY_ID())
                if (funcArgExprs(funcCtx).isEmpty()) {
                    throw new RuntimeException("RESULT_SCAN requires one argument (query ID)");
                }

                FrostlakeParser.ExpressionContext argExpr = funcArgExprs(funcCtx).get(0);

                // Check if argument is LAST_QUERY_ID()
                if (argExpr instanceof FrostlakeParser.FunctionCallExprContext) {
                    FrostlakeParser.FunctionCallExprContext argFunc =
                        (FrostlakeParser.FunctionCallExprContext) argExpr;
                    if ("LAST_QUERY_ID".equalsIgnoreCase(argFunc.functionName().getText())) {
                        // Get last query ID from cache
                        String lastQueryId = resultCache.getLastQueryId();
                        if (lastQueryId == null) {
                            throw new RuntimeException("No previous query results available");
                        }
                        return resultScan.execute(lastQueryId);
                    }
                }

                // Otherwise, evaluate the expression to get query ID string
                String argValueExpr = getOriginalText(argExpr);
                Object queryIdObj = evaluateExpression(argValueExpr, null, null);
                if (queryIdObj == null) {
                    throw new RuntimeException("Query ID cannot be null");
                }
                String queryId = queryIdObj.toString();
                return resultScan.execute(queryId);
            }

            // Check if it's a known built-in table function callable with no/positional args
            TableFunction tableFunc = functionRegistry.getTableFunction(functionName);
            if (tableFunc != null) {
                if (!funcArgExprs(funcCtx).isEmpty()) {
                    // Positional args, e.g. SPLIT_TO_TABLE('a,b', ',') or FLATTEN(col) — evaluate and pass through.
                    List<Object> positionalArgs = new ArrayList<>();
                    for (final FrostlakeParser.ExpressionContext argExpr : funcArgExprs(funcCtx)) {
                        String argText = getOriginalText(argExpr);
                        positionalArgs.add(lateralContext != null && !lateralContext.isEmpty()
                            ? evaluateExpressionWithLateralContextSimple(argText, lateralContext)
                            : evaluateExpression(argText, null, (Table) null));
                    }
                    return tableFunc.execute(positionalArgs);
                }
                return tableFunc.execute(new HashMap<>());
            }

            // Check if it's a user-defined table function (UDTF)
            Function udtf = resolveUdtf(functionName);
            if (udtf != null && udtf.isTableFunction()) {
                List<Object> callArgs = new ArrayList<>();
                if (!funcArgExprs(funcCtx).isEmpty()) {
                    for (final FrostlakeParser.ExpressionContext argExpr : funcArgExprs(funcCtx)) {
                        String argText = getOriginalText(argExpr);
                        Object val = lateralContext != null && !lateralContext.isEmpty()
                            ? evaluateExpressionWithLateralContextSimple(argText, lateralContext)
                            : evaluateExpression(argText, null, (Table) null);
                        callArgs.add(val);
                    }
                }
                if (udtf.getUdfLanguage() == UdfLanguage.PYTHON) {
                    return UdfRuntimes.require(UdfLanguage.PYTHON)
                        .executeTableFunction(udtf, callArgs);
                }
                String sql = substituteSqlParams(udtf.getBody(), udtf.getParameters(), callArgs);
                List<ResultSet> results = execute(sql);
                return results.isEmpty() ? new ResultSet(new ArrayList<>(), new ArrayList<>()) : results.get(0);
            }

            // Snowflake also allows a stored PROCEDURE declared RETURNS TABLE(…) as a FROM source:
            // TABLE(proc(args)) runs the procedure and uses its returned table as the row source.
            // Resolved last so built-in and user-defined table functions keep precedence.
            final ResultSet procResult = executeProcedureAsTableSource(funcCtx);
            if (procResult != null) {
                return procResult;
            }

            throw new RuntimeException("Unknown table function: " + functionName);
        }

        // Handle SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS('task') as table function
        if (expr instanceof FrostlakeParser.SystemUserTaskCancelExprContext) {
            FrostlakeParser.SystemUserTaskCancelExprContext sctx = (FrostlakeParser.SystemUserTaskCancelExprContext) expr;
            String argExpr = getOriginalText(sctx.expression());
            Object taskNameVal = lateralContext != null && !lateralContext.isEmpty()
                ? evaluateExpressionWithLateralContextSimple(argExpr, lateralContext)
                : evaluateExpression(argExpr, null, (Table) null);
            TableFunction cancelFunc =
                functionRegistry.getTableFunction("SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS");
            if (cancelFunc != null) {
                Map<String, Object> cancelArgs = new HashMap<>();
                cancelArgs.put("INPUT", taskNameVal);
                return cancelFunc.execute(cancelArgs);
            }
        }

        // FROM TABLE(<resultset_var>): a bare name that resolves to an in-scope procedural RESULTSET
        // (Snowflake's "table from a RESULTSET"). Surface its rows before giving up.
        if (visitor != null && visitor.getProceduralExecutor() != null) {
            final ResultSet rsVar = visitor.getProceduralExecutor().lookupResultSet(getOriginalText(expr));
            if (rsVar != null) {
                return rsVar;
            }
        }

        // FROM TABLE(<string_literal> | <session_variable> | <bind_variable>) — a Snowflake TABLE LITERAL,
        // where the operand is a VALUE naming a table/view/stream rather than a table-function call:
        // "where TABLE() is supported, it is equivalent to using IDENTIFIER()". A bind variable has already
        // been substituted to a literal by this point. Anything that neither evaluates to a name nor resolves
        // to a relation falls through to the error below.
        final ResultSet tableLiteral = resolveTableLiteral(expr, lateralContext);
        if (tableLiteral != null) {
            return tableLiteral;
        }

        throw new RuntimeException("Table function must be called with arguments (e.g., GENERATOR(ROWCOUNT => 10), RESULT_SCAN('<query_id>'))");
    }

    /**
     * Resolve {@code TABLE(<expr>)} as a table literal: evaluate {@code expr} to a name and return the rows of
     * the table / view / stream it names, exactly as {@code IDENTIFIER(<expr>)} in a FROM clause would.
     * Returns null when the operand does not evaluate to a name or names nothing resolvable.
     */
    private ResultSet resolveTableLiteral(final FrostlakeParser.ExpressionContext expr,
                                          final Map<String, Object> lateralContext) {
        final Object nameValue;
        try {
            final String exprText = getOriginalText(expr);
            nameValue = lateralContext != null && !lateralContext.isEmpty()
                ? evaluateExpressionWithLateralContextSimple(exprText, lateralContext)
                : evaluateExpression(exprText, null, (Table) null);
        } catch (final RuntimeException notAValue) {
            return null;
        }
        if (nameValue == null || nameValue.toString().trim().isEmpty()) {
            return null;
        }
        final String name = nameValue.toString().trim();
        try {
            final TableData dynamic = resolveDynamicNamedSource(name, null);
            if (dynamic != null) {
                return tableDataToResultSet(dynamic);
            }
            final Table resolved = catalog.resolveTable(name);
            final String fqName = getFullyQualifiedTableName(name);
            return tableDataToResultSet(
                new TableData(resolved, new ArrayList<>(readTableRowsForTransaction(fqName)), null));
        } catch (final RuntimeException notARelation) {
            return null;
        }
    }

    /** Present a resolved FROM source as a {@link ResultSet} (columns taken from its table metadata). */
    private ResultSet tableDataToResultSet(final TableData data) {
        final List<ResultSetColumn> columns = new ArrayList<>();
        for (final TableColumn column : data.table.getColumns()) {
            columns.add(new ResultSetColumn(column.getName(), column.getDataType()));
        }
        return new ResultSet(columns, data.rows);
    }

    /**
     * Execute a LATERAL join - evaluate right side for each left side row
     */
    /**
     * True when a comma-separated FROM item is a table function, which Snowflake treats as implicitly
     * LATERAL: it may reference columns of the tables written before it and the LATERAL keyword is
     * optional, as in the ubiquitous {@code FROM t, TABLE(FLATTEN(input => t.col)) f}. Such a source was
     * previously executed once with no lateral context, so any correlated reference failed with
     * "Column not found: &lt;alias&gt;.&lt;column&gt;". A table function that does NOT correlate is unaffected:
     * evaluating it per left row produces exactly the rows of the cross join it would otherwise perform.
     */
    private boolean isImplicitlyLateral(final FrostlakeParser.TableReferenceContext ref) {
        final FrostlakeParser.TableSourceContext src = ref.tableSource();
        if (src == null) {
            return false;
        }
        return src.FLATTEN() != null || (src.TABLE() != null && src.expression() != null);
    }

    private TableData executeLateralJoin(final List<Row> leftRows, final Table leftTable,
                                          final FrostlakeParser.TableReferenceContext rightTableRef,
                                          final FrostlakeParser.JoinClauseContext joinCtx,
                                          final Map<String, Table> aliasToTable,
                                          final List<Table> allTables,
                                          final FrostlakeParser.SelectClauseContext selectCtx) {
        List<Row> resultRows = new ArrayList<>();
        Table rightTable = null;
        String rightAlias = null;

        // Snowflake lets a LATERAL in the FROM reference a SELECT-list alias of the same query — the shape
        // `SELECT ARRAY_CONSTRUCT(…) obs, … FROM t, TABLE(FLATTEN(obs))`. Those aliases are not columns of any
        // FROM table, so without them the lateral failed with "Column not found". Resolved per left row below,
        // alongside the columns; aggregates and aliases that merely rename a column are already excluded.
        final Map<String, String> selectAliases = selectAliasExpressions(selectCtx, leftTable);

        for (final Row leftRow : leftRows) {
            // Create context with left row values accessible by column name
            Map<String, Object> lateralContext = new HashMap<>();

            // Add columns from all tables in the context, walking allTables in COMBINED-ROW ORDER — the
            // offsets must follow the row layout. Iterating the alias map instead accumulated offsets in
            // HASH order, so after a JOIN the lateral read the wrong slots: FLATTEN(a.attrs) after
            // `f INNER JOIN a` got some other column (usually NULL) and expanded to nothing.
            int offset = 0;
            for (final Table t : allTables != null && !allTables.isEmpty() ? allTables : List.of(leftTable)) {
                for (int i = 0; i < t.getColumns().size() && offset + i < leftRow.getValues().size(); i++) {
                    String colName = t.getColumns().get(i).getName();
                    final Object value = leftRow.getValue(offset + i);
                    // Keys are upper-cased so a lateral/correlated reference matches regardless of the
                    // case its alias and column were written in (references now fold to upper-case).
                    lateralContext.put(colName.toUpperCase(), value);
                    // Also add with table prefix and every alias prefix that names this table
                    lateralContext.put((t.getName() + "." + colName).toUpperCase(), value);
                    for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
                        if (entry.getValue() == t) {
                            lateralContext.put((entry.getKey() + "." + colName).toUpperCase(), value);
                        }
                    }
                }
                offset += t.getColumns().size();
            }

            // A real column of the same name always wins, so the aliases go in only where nothing is bound.
            // Aliases chain (an alias defined over another alias), so resolve iteratively: each pass offers
            // everything computed so far and retries the rest, until a pass makes no progress. An alias that
            // still cannot be computed (it references the lateral's own output) is simply skipped — the
            // reference then fails as it did before.
            final Map<String, String> pendingAliases = new LinkedHashMap<>();
            for (final Map.Entry<String, String> alias : selectAliases.entrySet()) {
                if (!lateralContext.containsKey(alias.getKey())) {
                    pendingAliases.put(alias.getKey(), alias.getValue());
                }
            }
            boolean progressed = true;
            while (progressed && !pendingAliases.isEmpty()) {
                progressed = false;
                final Iterator<Map.Entry<String, String>> pendingIt = pendingAliases.entrySet().iterator();
                while (pendingIt.hasNext()) {
                    final Map.Entry<String, String> alias = pendingIt.next();
                    try {
                        lateralContext.put(alias.getKey(),
                            evaluateExpressionWithAliases(alias.getValue(), leftRow, leftTable, aliasToTable,
                                null, lateralContext));
                        pendingIt.remove();
                        progressed = true;
                    } catch (final RuntimeException notComputableYet) {
                        // retry next pass once more aliases are bound
                    }
                }
            }

            // Execute right side with lateral context
            TableData rightData = executeTableReference(rightTableRef, lateralContext, null);

            // Capture table metadata from first iteration
            if (rightTable == null) {
                rightTable = rightData.table;
                rightAlias = rightData.alias;
            }

            // A LEFT (or FULL) lateral join keeps a left row whose lateral produced nothing, null-extended
            // over the lateral's columns — LEFT JOIN LATERAL (...) ON TRUE must never drop left rows
            // (12-month trend queries rely on zero-count rows surviving). INNER/comma laterals drop such
            // rows, as in Snowflake.
            if (rightData.rows.isEmpty() && joinCtx != null && joinCtx.joinType() != null
                    && (joinCtx.joinType().LEFT() != null || joinCtx.joinType().FULL() != null)) {
                final List<Object> combinedValues = new ArrayList<>(leftRow.getValues());
                final int rightWidth = rightData.table != null ? rightData.table.getColumns().size() : 0;
                for (int i = 0; i < rightWidth; i++) {
                    combinedValues.add(null);
                }
                resultRows.add(new Row(combinedValues));
                continue;
            }

            // Combine left row with each right row
            for (final Row rightRow : rightData.rows) {
                List<Object> combinedValues = new ArrayList<>(leftRow.getValues());
                combinedValues.addAll(rightRow.getValues());
                resultRows.add(new Row(combinedValues));
            }
        }

        // Empty left input: the loop never ran, so the right side's schema was never captured. Derive it by
        // executing the right side once against a NULL-filled lateral context (its rows are discarded), so
        // the join yields a correctly-shaped EMPTY result rather than a null right table — which would NPE
        // in mergeTableMetadata / the join operator downstream.
        if (rightTable == null) {
            final Map<String, Object> nullContext = new HashMap<>();
            for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
                final String alias = entry.getKey();
                final Table t = entry.getValue();
                for (int i = 0; i < t.getColumns().size(); i++) {
                    final String colName = t.getColumns().get(i).getName();
                    nullContext.put(colName.toUpperCase(), null);
                    nullContext.put((t.getName() + "." + colName).toUpperCase(), null);
                    nullContext.put((alias + "." + colName).toUpperCase(), null);
                }
            }
            try {
                final TableData probe = executeTableReference(rightTableRef, nullContext, null);
                rightTable = probe.table;
                rightAlias = probe.alias;
            } catch (final RuntimeException schemaProbeFailed) {
                // The right side could not be evaluated with NULL inputs (e.g. a correlated subquery that
                // errors on a NULL correlation value). Fall back to an empty (0-column) table so the empty
                // join still completes without an NPE.
                rightTable = new Table(rightAlias != null ? rightAlias : "lateral", new ArrayList<>(), false);
            }
        }

        return new TableData(rightTable, resultRows, rightAlias);
    }

    /**
     * Get column value from combined row, handling qualified names like "table.column"
     */
    private Object getQualifiedColumnValue(final Row combinedRow, final Table leftTable, final Table rightTable, final String qualifiedName) {
        String[] parts = qualifiedName.split("\\.");
        String columnName;
        String tableName = null;

        if (parts.length == 2) {
            tableName = parts[0].toUpperCase();
            columnName = parts[1].toUpperCase();
        } else {
            columnName = qualifiedName.toUpperCase();
        }

        // Search in left table first
        for (int i = 0; i < leftTable.getColumns().size(); i++) {
            TableColumn col = leftTable.getColumns().get(i);
            if (col.getName().toUpperCase().equals(columnName)) {
                if (tableName == null || leftTable.getName().toUpperCase().contains(tableName)) {
                    return combinedRow.getValue(i);
                }
            }
        }

        // Search in right table
        int offset = leftTable.getColumns().size();
        for (int i = 0; i < rightTable.getColumns().size(); i++) {
            TableColumn col = rightTable.getColumns().get(i);
            if (col.getName().toUpperCase().equals(columnName)) {
                if (tableName == null || rightTable.getName().toUpperCase().contains(tableName)) {
                    return combinedRow.getValue(offset + i);
                }
            }
        }

        throw new RuntimeException("Column not found: " + qualifiedName);
    }

    /**
     * Merge metadata from two tables for join result
     */
    Table mergeTableMetadata(final Table left, final Table right) {
        return mergeTableMetadata(left, right, Collections.emptySet());
    }

    /** Merge metadata for a join result; the right-side columns named in {@code mergedNames} (a USING /
     *  NATURAL join's key columns, upper-cased) stay resolvable but are hidden from {@code SELECT *} —
     *  Snowflake outputs one merged column, in the left table's position. */
    Table mergeTableMetadata(final Table left, final Table right, final Set<String> mergedNames) {
        List<TableColumn> allColumns = new ArrayList<>(left.getColumns());
        for (final TableColumn col : right.getColumns()) {
            allColumns.add(mergedNames.contains(col.getName().toUpperCase()) ? col.starHiddenCopy() : col);
        }
        return new Table("joined", allColumns, false);
    }

    /** The upper-cased key column names of a USING / NATURAL join, or an empty set for ON / CROSS. */
    private Set<String> usingJoinColumnNames(final FrostlakeParser.JoinClauseContext joinCtx,
                                             final Table leftTable, final Table rightTable) {
        if (joinCtx.NATURAL() != null) {
            return new HashSet<>(commonColumnNames(leftTable, rightTable));
        }
        if (joinCtx.USING() == null) {
            return Collections.emptySet();
        }
        final Set<String> names = new HashSet<>();
        for (final FrostlakeParser.QualifiedNameContext qn : joinCtx.usingColumnList().qualifiedName()) {
            final List<FrostlakeParser.IdentifierContext> parts = qn.identifier();
            names.add(SqlIdentifiers.canonical(parts.get(parts.size() - 1)));
        }
        return names;
    }

    /**
     * Give the right side of a join a distinct {@link Table} instance when it is the SAME object as a
     * table already in the join — i.e. a self-join such as {@code FROM t a JOIN t b}. Regular tables
     * resolve to the shared catalog {@code Table}, and the qualified-column resolver locates a
     * reference's segment by table identity; without this both aliases would bind to the first
     * segment. The returned copy carries the same columns, so {@code alias.column} still resolves,
     * but its distinct identity lets each alias map to its own side of the combined row.
     */
    private Table distinctJoinTable(final Table rightTable, final List<Table> existingTables) {
        for (final Table existing : existingTables) {
            if (existing == rightTable) {
                return new Table(rightTable.getName(), rightTable.getColumns(),
                    rightTable.isTemporary(), rightTable.isTransient());
            }
        }
        return rightTable;
    }

    /**
     * Convert a ResultSet to a Table object
     */
    Table resultSetToTable(final ResultSet rs, final String tableName) {
        List<TableColumn> columns = new ArrayList<>();
        for (final ResultSetColumn col : rs.getColumns()) {
            columns.add(new TableColumn(col.getName(), col.getDataType(), true, null, false, false, false));
        }
        return new Table(tableName, columns, false);
    }

    private Table resultSetToTable(final ResultSet rs, final String tableName, final List<String> columnNames) {
        List<TableColumn> columns = new ArrayList<>();
        List<ResultSetColumn> rsColumns = rs.getColumns();

        if (columnNames.size() != rsColumns.size()) {
            throw new RuntimeException("View column count (" + columnNames.size() +
                ") does not match SELECT column count (" + rsColumns.size() + ")");
        }

        for (int i = 0; i < rsColumns.size(); i++) {
            ResultSetColumn rsCol = rsColumns.get(i);
            String colName = columnNames.get(i);
            columns.add(new TableColumn(colName, rsCol.getDataType(), true, null, false, false, false));
        }
        return new Table(tableName, columns, false);
    }

    /**
     * Infer data type from a value
     */
    private DataType inferDataType(final Object value) {
        if (value == null) {
            return StringType.VARCHAR;
        } else if (value instanceof Integer || value instanceof Long) {
            return NumericType.INTEGER;
        } else if (value instanceof Double || value instanceof Float) {
            return NumericType.FLOAT;
        } else if (value instanceof Boolean) {
            return BooleanType.BOOLEAN;
        } else if (value instanceof java.sql.Date) {
            return DateTimeType.DATE;
        } else if (value instanceof Timestamp) {
            return DateTimeType.TIMESTAMP_NTZ;
        } else if (value instanceof String) {
            return StringType.VARCHAR;
        } else {
            return VariantType.VARIANT;
        }
    }

    /**
     * Apply column aliases to a table
     */
    Table applyColumnAliases(final Table table, final List<String> columnAliases) {
        List<TableColumn> newColumns = new ArrayList<>();
        List<TableColumn> originalColumns = table.getColumns();

        for (int i = 0; i < originalColumns.size(); i++) {
            TableColumn originalCol = originalColumns.get(i);
            String newName = i < columnAliases.size() ? columnAliases.get(i) : originalCol.getName();
            TableColumn newCol = new TableColumn(
                newName,
                originalCol.getDataType(),
                originalCol.isNullable(),
                originalCol.getDefaultValue(),
                originalCol.isPrimaryKey(),
                originalCol.isUnique(),
                originalCol.isAutoIncrement()
            );
            newColumns.add(newCol);
        }

        return new Table(table.getName(), newColumns, table.isTemporary());
    }

    // Extract equi-join key column indices ({leftIdx[], rightIdx[]}) from a join condition, for the
    // hash-join candidate filter. Returns null unless the condition is a conjunction containing
    // column=column equalities across the two tables. The full condition is always re-confirmed per
    // candidate pair, so this must only avoid FALSE NEGATIVES — hence it bails (null) on anything it
    // cannot classify with certainty (OR, NOT, expression keys, ambiguous/unqualified columns, self-join).
    private int[][] extractEquiJoinKeys(final Expression condition, final Table leftTable,
                                        final Table rightTable, final Map<String, Table> aliasToTable) {
        final List<Integer> leftIdx = new ArrayList<>();
        final List<Integer> rightIdx = new ArrayList<>();
        if (!collectEquiKeys(condition, leftTable, rightTable, aliasToTable, leftIdx, rightIdx)) {
            return null;
        }
        if (leftIdx.isEmpty()) {
            return null;
        }
        final int[] l = new int[leftIdx.size()];
        final int[] r = new int[rightIdx.size()];
        for (int i = 0; i < l.length; i++) {
            l[i] = leftIdx.get(i);
            r[i] = rightIdx.get(i);
        }
        return new int[][] { l, r };
    }

    private boolean collectEquiKeys(final Expression e, final Table lt, final Table rt,
                                    final Map<String, Table> alias,
                                    final List<Integer> li, final List<Integer> ri) {
        if (!(e instanceof BinaryOperationExpression)) {
            return false;  // NOT / unknown node — cannot guarantee a safe conjunction
        }
        final BinaryOperationExpression b = (BinaryOperationExpression) e;
        final BinaryOperator op = b.getOperator();
        if (op == BinaryOperator.AND) {
            return collectEquiKeys(b.getLeft(), lt, rt, alias, li, ri)
                && collectEquiKeys(b.getRight(), lt, rt, alias, li, ri);
        }
        if (op == BinaryOperator.OR) {
            return false;  // OR breaks conjunction-based partitioning
        }
        if (op == BinaryOperator.EQUAL) {
            final int[] lc = classifyJoinColumn(b.getLeft(), lt, rt, alias);
            final int[] rc = classifyJoinColumn(b.getRight(), lt, rt, alias);
            if (lc != null && rc != null) {
                if (lc[0] == 0 && rc[0] == 1) {
                    li.add(lc[1]);
                    ri.add(rc[1]);
                } else if (lc[0] == 1 && rc[0] == 0) {
                    li.add(rc[1]);
                    ri.add(lc[1]);
                }
                // same-table equality: a valid conjunct but not a join key (re-confirmed later)
            }
            return true;
        }
        // Other comparisons (>, <, LIKE, …): valid conjuncts, not hash keys; re-confirmed per candidate.
        return true;
    }

    // {tableSide (0=left, 1=right), columnIndex} for a column reference, or null when it is not a
    // column of exactly one of the two joined tables.
    private int[] classifyJoinColumn(final Expression e, final Table lt, final Table rt,
                                     final Map<String, Table> alias) {
        if (!(e instanceof ColumnReferenceExpression)) {
            return null;
        }
        final ColumnReferenceExpression c = (ColumnReferenceExpression) e;
        final String col = c.getColumnName();
        if (c.isQualified()) {
            final String q = c.getTableName();
            final boolean matchesLeft = qualifierMatches(q, lt, alias);
            final boolean matchesRight = qualifierMatches(q, rt, alias);
            if (matchesLeft && !matchesRight && lt.hasColumn(col)) {
                return new int[] { 0, lt.getColumnIndex(col) };
            }
            if (matchesRight && !matchesLeft && rt.hasColumn(col)) {
                return new int[] { 1, rt.getColumnIndex(col) };
            }
            return null;  // ambiguous (e.g. self-join) or unknown qualifier
        }
        final boolean inLeft = lt.hasColumn(col);
        final boolean inRight = rt.hasColumn(col);
        if (inLeft && !inRight) {
            return new int[] { 0, lt.getColumnIndex(col) };
        }
        if (inRight && !inLeft) {
            return new int[] { 1, rt.getColumnIndex(col) };
        }
        return null;  // ambiguous (in both) or in neither
    }

    // True when the qualifier names the given table (by table name or by an alias that maps to a
    // table of the same name). Name-based so it is robust to distinct Table instances for the same
    // table; self-joins (both sides same name) therefore match both and are left to nested-loop.
    private boolean qualifierMatches(final String qualifier, final Table t, final Map<String, Table> alias) {
        if (t.getName().equalsIgnoreCase(qualifier)) {
            return true;
        }
        if (alias != null) {
            for (final Map.Entry<String, Table> en : alias.entrySet()) {
                if (en.getKey().equalsIgnoreCase(qualifier)
                        && en.getValue() != null
                        && en.getValue().getName().equalsIgnoreCase(t.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get column value with alias support
     */
    /**
     * Resolve a (possibly qualified) column reference against a multi-table row context — the
     * alias-aware resolution used by JOIN evaluation, exposed for the AST evaluator so a parsed
     * Expression can be evaluated directly over joined rows (e.g. {@code a.id = b.id} resolves each
     * side to its own table). Delegates to {@link #getQualifiedColumnValueFromTables}; throws if the
     * name is not a column of any table.
     */
    public Object resolveColumnInTables(final Row row, final List<Table> tables,
                                        final Map<String, Table> aliasToTable, final String qualifiedName) {
        // Strict qualifier check: a qualified reference whose qualifier is not one of these tables
        // or aliases is NOT a column here (it is most likely a correlated/lateral outer reference),
        // so throw rather than mis-binding to a same-named local column via the lenient bare-name
        // search below. The caller then falls through to lateral/context resolution.
        String cleaned = qualifiedName;
        if (cleaned.indexOf('(') >= 0 || cleaned.indexOf(')') >= 0) {
            cleaned = cleaned.replace("(", "").replace(")", "");
        }
        cleaned = cleaned.trim();
        final int dot = cleaned.indexOf('.');
        if (dot > 0 && !isNumericDotted(cleaned)) {
            final String qualifier = cleaned.substring(0, dot);
            boolean known = false;
            for (final Table t : tables) {
                if (t.getName().equalsIgnoreCase(qualifier)) { known = true; break; }
            }
            if (!known && aliasToTable != null) {
                for (final String a : aliasToTable.keySet()) {
                    if (a.equalsIgnoreCase(qualifier)) { known = true; break; }
                }
            }
            if (!known) {
                throw new RuntimeException("Unknown qualifier (not a joined table/alias): " + qualifier);
            }
        }
        return getQualifiedColumnValueFromTables(row, tables, aliasToTable, qualifiedName);
    }

    /** Whether {@code name} is exactly a column of one of {@code tables} (a null-safe scan). */
    private static boolean namesAColumnOf(final List<Table> tables, final String name) {
        if (tables == null) {
            return false;
        }
        for (final Table t : tables) {
            if (t != null && t.hasColumn(name)) {
                return true;
            }
        }
        return false;
    }

    Object getQualifiedColumnValueFromTables(final Row row, final List<Table> tables,
                                                     final Map<String, Table> aliasToTable,
                                                     String qualifiedName) {
        // Remove parentheses only if present (avoid a per-access regex on the common path)
        if (qualifiedName.indexOf('(') >= 0 || qualifiedName.indexOf(')') >= 0) {
            qualifiedName = qualifiedName.replace("(", "").replace(")", "");
        }
        qualifiedName = qualifiedName.trim();

        // Try to parse as string literal (remove quotes) — but NOT when the text actually NAMES a column.
        // A quoted identifier may legitimately contain single quotes, which is exactly how Snowflake names
        // PIVOT output columns ('q1', referenced as "'q1'"); stripping them here made MAX("'q1'") return
        // the STRING q1 instead of reading the column, while every path that does not come through this
        // resolver (plain projection, WHERE, ORDER BY, scalar functions) read it correctly.
        if (qualifiedName.startsWith("'") && qualifiedName.endsWith("'")
                && !namesAColumnOf(tables, qualifiedName)) {
            return qualifiedName.substring(1, qualifiedName.length() - 1);
        }

        // Try to parse as a numeric literal — only when it actually looks numeric, so column names
        // (which start with a letter/underscore) don't pay a thrown-and-caught exception per access.
        // Guarded like the string-literal branch above: a numeric PIVOT value (FOR sev IN (1, 2))
        // names its output column "1", and reading it must win over the literal interpretation —
        // otherwise ZEROIFNULL("1") silently returned 1 for every row.
        if (!qualifiedName.isEmpty() && !namesAColumnOf(tables, qualifiedName)) {
            final char c0 = qualifiedName.charAt(0);
            if (c0 == '-' || c0 == '+' || c0 == '.' || (c0 >= '0' && c0 <= '9')) {
                try {
                    if (qualifiedName.indexOf('.') >= 0) {
                        return Double.parseDouble(qualifiedName);
                    }
                    return Integer.parseInt(qualifiedName);
                } catch (final NumberFormatException e) {
                    // Not a number, continue
                }
            }
        }

        // Split qualifier.column without a regex (exactly one dot => table.column)
        String tableName = null;
        String columnName;
        final int dotIdx = qualifiedName.indexOf('.');
        if (dotIdx > 0 && qualifiedName.indexOf('.', dotIdx + 1) < 0) {
            tableName = qualifiedName.substring(0, dotIdx);  // Keep original case for alias lookup
            columnName = qualifiedName.substring(dotIdx + 1).toUpperCase();
        } else {
            columnName = qualifiedName.toUpperCase();
        }

        // Resolve table by alias or name (case-insensitive)
        Table targetTable = null;
        if (tableName != null) {
            // Try exact match first
            if (aliasToTable.containsKey(tableName)) {
                targetTable = aliasToTable.get(tableName);
            } else {
                // Try case-insensitive match
                for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(tableName)) {
                        targetTable = entry.getValue();
                        break;
                    }
                }
            }
        }

        // Search through all tables
        int offset = 0;
        for (final Table table : tables) {
            boolean isTargetTable = (targetTable == null) || (table == targetTable);

            if (isTargetTable) {
                final List<TableColumn> cols = table.getColumns();
                for (int i = 0; i < cols.size(); i++) {
                    if (cols.get(i).getName().equalsIgnoreCase(columnName)) {
                        return row.getValue(offset + i);
                    }
                }
            }

            offset += table.getColumns().size();
        }

        throw new RuntimeException("Column not found: " + qualifiedName);
    }

    /** True when s matches \d+\.\d+ (a numeric literal like 1.5), to tell it from a qualified name. */
    private static boolean isNumericDotted(final String s) {
        return ValueComparisons.isNumericDotted(s);
    }

    /**
     * Parse a SELECT statement from a string
     */
    private FrostlakeParser.SelectStatementContext parseSelectStatement(final String sql) {
        FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        SyntaxErrorListener errorListener = new SyntaxErrorListener(sql);
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FrostlakeParser parser = new FrostlakeParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        FrostlakeParser.SelectStatementContext selectStatementContext = parser.selectStatement();
        errorListener.throwIfErrors();

        return selectStatementContext;
    }

    /**
     * Evaluate expression with lateral context - simplified version for table function arguments
     * Tries to resolve column references from lateralContext first, then falls back to standard evaluation
     */
    private Object evaluateExpressionWithLateralContextSimple(String expr, final Map<String, Object> lateralContext) {
        expr = expr.trim();

        // Try direct lookup in lateral context (e.g., "n" or "N")
        String upperExpr = expr.toUpperCase();
        if (lateralContext.containsKey(upperExpr)) {
            return lateralContext.get(upperExpr);
        }

        // Try qualified name lookup (e.g., "n.n" or "N.N")
        if (lateralContext.containsKey(expr)) {
            return lateralContext.get(expr);
        }

        // Check all keys in lateral context for case-insensitive match
        for (final Map.Entry<String, Object> entry : lateralContext.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(expr)) {
                return entry.getValue();
            }
        }

        // Not a bare correlated column (e.g. it's parse_json(column5) or column5::VARIANT): evaluate the
        // whole expression WITH the lateral context in scope, so column references inside a function
        // wrapper or cast resolve against the outer row rather than failing as "column not found".
        try {
            final Table dummyTable = new Table("DUMMY", new ArrayList<>(), false);
            final Row dummyRow = new Row(new ArrayList<>());
            final ExpressionEvaluator evaluator = new ExpressionEvaluator(dummyTable, functionRegistry, catalog, this);
            evaluator.setOuterLateralContext(lateralContext);
            return evaluator.evaluate(expr, dummyRow);
        } catch (final Exception e) {
            throw new RuntimeException("Unable to evaluate expression '" + expr + "' in LATERAL context: " + e.getMessage(), e);
        }
    }

    /**
     * Get procedural variables as a context map for expression evaluation
     */
    public Map<String, Object> getProceduralVariablesAsContext() {
        if (visitor != null) {
            return visitor.getProceduralExecutor().getAllVariables();
        }
        return new HashMap<>();
    }
}
