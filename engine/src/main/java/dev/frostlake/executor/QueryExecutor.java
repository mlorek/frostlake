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
import dev.frostlake.executor.udf.PythonTableFunctionExecutor;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.functions.aggregate.PercentileCont;
import dev.frostlake.functions.aggregate.PercentileDisc;
import dev.frostlake.functions.scalar.context.CurrVal;
import dev.frostlake.functions.scalar.context.CurrentAccount;
import dev.frostlake.functions.scalar.context.LastQueryId;
import dev.frostlake.functions.scalar.context.NextVal;
import dev.frostlake.functions.table.ResultScan;
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
import dev.frostlake.transaction.TransactionManager;
import dev.frostlake.transaction.TransactionWriteSet;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
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
    // it non-destructively. consumingStreamContext marks the DML window during which reads register.
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

        // Register LAST_QUERY_ID() scalar function with access to result cache
        functionRegistry.register(new LastQueryId(resultCache));

        // Register NEXTVAL and CURRVAL sequence functions
        functionRegistry.register(new NextVal(catalog));
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
        final boolean outermost = executeReentryDepth == 0;
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
        FrostlakeParser.SqlScriptContext cached = SCRIPT_CACHE.get(sql);
        if (cached != null) {
            return cached;
        }
        FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        SyntaxErrorListener errorListener = new SyntaxErrorListener(sql);
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
            final FrostlakeParser.SqlScriptContext tree = parseScript(sql);
            result = !tree.statement().isEmpty() && tree.statement().get(0).queryStatement() != null;
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

            // Visit each statement
            for (final FrostlakeParser.StatementContext stmtCtx : tree.statement()) {
                Object result;

                // Snowflake: a DDL statement implicitly commits the current transaction (DDL runs as its
                // own transaction). Commit any open txn — explicit or auto-started — before the DDL.
                if (stmtCtx.ddlStatement() != null && transactionManager.hasActiveTransaction()) {
                    transactionManager.commit();
                }

                // A DML statement consumes any stream it reads (offset advances when its txn commits); mark
                // the window so stream reads register for consumption. A plain SELECT must NOT consume.
                final boolean prevConsuming = consumingStreamContext;
                if (stmtCtx.dmlStatement() != null) {
                    consumingStreamContext = true;
                }
                try {
                    // If we have lateral context and this is a query statement (SELECT), pass it along
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
                    consumingStreamContext = prevConsuming;
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
                Map<String, ResultSet> selectCTEs = executeCTEs(ctx.withClause(), lateralContext);
                if (allCTEs == null) {
                    allCTEs = selectCTEs;
                } else {
                    // Merge CTEs (SELECT-level CTEs override parent-level CTEs with same name)
                    allCTEs = new HashMap<>(allCTEs);
                    allCTEs.putAll(selectCTEs);
                }
            }

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
        } catch (final SecurityException e) {
            // Re-throw security exceptions as-is
            throw e;
        } catch (final Exception e) {
            logger.error("Error executing SQL: {}", ctx.getText(), e);
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
        } catch (final SecurityException e) {
            // Re-throw security exceptions as-is
            throw e;
        } catch (final Exception e) {
            logger.error("Error executing SQL: {}", ctx.getText(), e);
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw new RuntimeException("Failed to execute SELECT: " + e.getMessage(), e);
        }
    }

    /**
     * Execute CTEs defined in WITH clause
     * CTEs are executed sequentially, and each CTE can reference earlier CTEs
     */
    Map<String, ResultSet> executeCTEs(final FrostlakeParser.WithClauseContext withCtx, final Map<String, Object> lateralContext) {
        Map<String, ResultSet> cteResults = new HashMap<>();
        boolean isRecursive = withCtx.RECURSIVE() != null;

        for (final FrostlakeParser.CteDefinitionContext cteCtx : withCtx.cteDefinition()) {
            String cteName = cteCtx.identifier().getText().toUpperCase();
            ResultSet cteResult;

            boolean recursive = isRecursive && isRecursiveCte(cteCtx, cteName);
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
        if (tree == null || tree.statement().isEmpty()) {
            return false;
        }
        final FrostlakeParser.ProceduralStatementContext p = tree.statement().get(0).proceduralStatement();
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
        String body = getOriginalText(cteCtx.selectStatement()).toUpperCase();
        return body.contains(cteName);
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
        final List<List<Row>> terms = new ArrayList<>();
        final List<FrostlakeParser.SetOperatorContext> termOperators = new ArrayList<>();
        List<Row> currentTerm = new ArrayList<>(firstResult.getRows());

        for (int i = 1; i < operands.size(); i++) {
            final ResultSet nextResult = executeSelectOperand(ctx, operands.get(i), lateralContext, cteResults);
            if (nextResult.getColumns().size() != columnCount) {
                throw new RuntimeException("Set operation queries must have the same number of columns");
            }
            final FrostlakeParser.SetOperatorContext operator = setOperators.get(i - 1);
            final boolean hasAll = operator.ALL() != null;
            if (operator.INTERSECT() != null) {
                currentTerm = SetOperations.applyIntersect(currentTerm, nextResult.getRows(), hasAll);
            } else {
                // UNION or EXCEPT/MINUS: close the current INTERSECT term and start a new one.
                terms.add(currentTerm);
                termOperators.add(operator);
                currentTerm = new ArrayList<>(nextResult.getRows());
            }
        }
        terms.add(currentTerm);

        List<Row> allRows = terms.get(0);
        for (int j = 1; j < terms.size(); j++) {
            final FrostlakeParser.SetOperatorContext operator = termOperators.get(j - 1);
            final boolean hasAll = operator.ALL() != null;
            if (operator.UNION() != null) {
                allRows = SetOperations.applyUnion(allRows, terms.get(j), hasAll);
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
            final int limit = Integer.parseInt(ctx.limitClause().INTEGER_LITERAL(0).getText());
            int offset = 0;
            if (ctx.limitClause().INTEGER_LITERAL().size() > 1) {
                offset = Integer.parseInt(ctx.limitClause().INTEGER_LITERAL(1).getText());
            }
            allRows = allRows.subList(Math.min(offset, allRows.size()),
                                     Math.min(offset + limit, allRows.size()));
        }
        if (ctx.fetchClause() != null) {
            final int fetch = Integer.parseInt(ctx.fetchClause().INTEGER_LITERAL().getText());
            allRows = allRows.subList(0, Math.min(fetch, allRows.size()));
        }

        return new ResultSet(firstResult.getColumns(), allRows);
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

            // Process comma-separated tables (CROSS JOIN or LATERAL)
            // If there are multiple table references, it's comma-separated syntax
            if (allTableRefs.size() > 1) {
                for (int i = 1; i < allTableRefs.size(); i++) {
                    FrostlakeParser.TableReferenceContext rightTableRef = allTableRefs.get(i);
                    boolean isLateral = rightTableRef.LATERAL() != null;

                    if (isLateral) {
                        // LATERAL with comma syntax: evaluate right side for each left side row
                        TableData lateralData = executeLateralJoin(rows, table, rightTableRef, null, aliasToTable);
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
            for (final FrostlakeParser.JoinClauseContext joinCtx : tableExpr.joinClause()) {
                boolean isLateral = joinCtx.LATERAL() != null;
                FrostlakeParser.TableReferenceContext rightTableRef = joinCtx.tableReference();

                if (isLateral) {
                    // LATERAL join: evaluate right side for each left side row
                    TableData lateralData = executeLateralJoin(rows, table, rightTableRef, joinCtx, aliasToTable);
                    rows = lateralData.rows;
                    // Update table metadata after lateral join
                    table = mergeTableMetadata(table, lateralData.table);
                    aliasToTable.put(lateralData.alias != null ? lateralData.alias : lateralData.table.getName(),
                                    lateralData.table);
                    allTables.add(lateralData.table);
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
                    rows = applyJoin(rows, table, rightData.rows, rightJoinTable, joinCtx, aliasToTable, allTables);

                    // Update table metadata to include both tables
                    table = mergeTableMetadata(table, rightJoinTable);
                }
            }

            // Apply Row Access Policy (RLS) before WHERE — filters rows the user can't see
            rows = applyRowAccessPolicy(rows, table);

            // Apply WHERE clause. For a simple single-table SELECT ... LIMIT (no join / group / order /
            // distinct / window / aggregate / pivot), stream scan -> WHERE -> LIMIT so the predicate is
            // evaluated only until LIMIT is satisfied (LimitRowStream stops pulling) and the full filtered
            // set is never materialized. Projection is 1:1, so it runs afterwards on the <= LIMIT rows.
            FrostlakeParser.WhereClauseContext whereCtx = getWhereClause(ctx);
            String whereExpr = whereCtx != null ? getOriginalText(whereCtx.booleanExpr()) : null;
            boolean limitApplied = false;
            if (lateralContext == null
                    && isStreamableSelect(ctx, stmtCtx, firstTableRef.tableSource(), allTables)) {
                rows = streamFilterLimit(rows, table, whereExpr, stmtCtx.limitClause());
                limitApplied = true;
            } else if (whereCtx != null) {
                rows = applyWhereClause(rows, table, whereExpr, lateralContext, aliasToTable, allTables, tableExpr);
            }

            // Check for PIVOT/UNPIVOT in the first table source
            FrostlakeParser.TableSourceContext firstSource = firstTableRef.tableSource();
            if (firstSource.pivotClause() != null) {
                return executePivot(ctx, table, rows, firstSource);
            } else if (firstSource.unpivotClause() != null) {
                return executeUnpivot(ctx, table, rows, firstSource);
            }

            // Check if SELECT contains aggregate functions or window functions
            boolean hasAggregates = hasAggregateFunction(ctx);
            boolean hasWindowFunctions = hasWindowFunction(ctx);

            // Apply GROUP BY or handle implicit grouping for aggregates
            boolean hasGroupBy = ctx.groupByClause() != null;
            if (hasGroupBy) {
                rows = applyGroupBy(rows, table, ctx, aliasToTable, allTables);
            } else if (hasAggregates) {
                // No GROUP BY but has aggregates - treat entire result as one group
                rows = applyImplicitGroupBy(rows, table, ctx, aliasToTable, allTables);
            }

            // Apply HAVING clause (only after GROUP BY)
            if (ctx.havingClause() != null && (hasGroupBy || hasAggregates)) {
                rows = applyHaving(rows, ctx);
            }

            // Compute window functions if present and store results
            Map<Integer, Map<Integer, Object>> windowFunctionResults = new HashMap<>();
            if (hasWindowFunctions) {
                windowFunctionResults = computeWindowFunctions(rows, ctx, table);
                // Add window function values to rows
                rows = addWindowFunctionsToRows(rows, windowFunctionResults, ctx, table);
            }

            // Apply QUALIFY clause (filters on window functions)
            if (ctx.qualifyClause() != null) {
                rows = applyQualify(rows, ctx, windowFunctionResults, table);
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
                        rows = orderByAfterGroupBy(rows, stmtCtx);
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

                    List<TerminalNode> intLiterals = stmtCtx.limitClause().INTEGER_LITERAL();
                    if (intLiterals.size() == 2) {
                        // LIMIT n OFFSET m
                        limit = Integer.parseInt(intLiterals.get(0).getText());
                        offset = Integer.parseInt(intLiterals.get(1).getText());
                    } else {
                        // LIMIT n
                        limit = Integer.parseInt(intLiterals.get(0).getText());
                    }

                    // Apply offset and limit
                    int start = Math.min(offset, rows.size());
                    int end = Math.min(start + limit, rows.size());
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

            // Process each select item
            for (final FrostlakeParser.SelectItemContext item : selectList.selectItem()) {
                if (SelectItemAccessors.isStarItem(item)) {
                    throw new RuntimeException("SELECT * requires a FROM clause");
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
                boolean condMet = whereResult instanceof Boolean ? (Boolean) whereResult
                    : whereResult != null && !"false".equalsIgnoreCase(whereResult.toString()) && !"0".equals(whereResult.toString());
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
                throw new RuntimeException("NULL result in a non-nullable column: " + cols.get(i).getName());
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
        return value;
    }

    /**
     * Round a value written into a fixed-point NUMBER/DECIMAL(p,s) column to the column's declared scale
     * (HALF_UP), the way Snowflake does on write. Applies only to NUMBER/DECIMAL/NUMERIC with an explicit
     * scale &gt; 0; FLOAT/DOUBLE (approximate) and bare/scale-0 columns are returned unchanged, and a value
     * that already fits the scale is returned as-is to avoid churning its representation.
     */
    private Object applyColumnScale(final Object value, final NumericType type) {
        if (value == null || type.getScale() <= 0 || !isFixedPointNumeric(type.getName())) {
            return value;
        }
        final BigDecimal bd = (value instanceof BigDecimal) ? (BigDecimal) value : new BigDecimal(value.toString());
        if (bd.scale() > type.getScale()) {
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

            // Truncate the table in storage engine
            storageEngine.truncateTable(fullyQualifiedName);
            logger.trace("Truncated table: {}", tableName);

        } catch (final SecurityException e) {
            throw e; // Let security exceptions propagate
        } catch (final Exception e) {
            throw new RuntimeException("Failed to execute TRUNCATE: " + e.getMessage(), e);
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
        final String localPath = stripFileScheme(extractStringLiteral(ctx.STRING_LITERAL()));
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
    public ResultSet executeGetFromContext(final FrostlakeParser.GetStatementContext ctx) {
        final Path stageDir = resolveCopyBaseDir(stageRefToLocation(ctx.stageRef()));
        final String localDir = stripFileScheme(extractStringLiteral(ctx.STRING_LITERAL()));
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
            return "@%" + getIdentifier(ctx.identifier()) + path;
        }
        return "@" + getIdentifier(ctx.identifier()) + path;
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
        return ctx.qualifiedName().getText();
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
            for (final FrostlakeParser.BooleanExprContext arg : funcCtx.functionArgList().booleanExpr()) {
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
        ExpressionEvaluator evaluator = new ExpressionEvaluator(table, functionRegistry, catalog, this);
        List<Row> filtered = new ArrayList<>();
        for (final Row row : rows) {
            Object result = evaluator.evaluate(whereExpr, row);
            if (result instanceof Boolean && (Boolean) result) {
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
        // Build a combined table from actual table + CTE virtual tables (for subqueries in WHERE)
        // For simple column references, the regular evaluator works fine;
        // CTEs in WHERE are typically used as subquery sources (e.g. WHERE id IN (SELECT id FROM cte))
        // We wire them via the query executor which already resolves CTEs during subquery evaluation.
        this.currentCteContext = cteResults;
        try {
            return filterRows(rows, table, whereExpr);
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
                if (result instanceof Boolean && (Boolean) result) {
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
                if (result instanceof Boolean && (Boolean) result) {
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
                                       final FrostlakeParser.TableSourceContext firstSource,
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
            && firstSource.pivotClause() == null
            && firstSource.unpivotClause() == null
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
        List<TerminalNode> intLiterals = limitClause.INTEGER_LITERAL();
        if (intLiterals.size() == 2) {
            limit = Long.parseLong(intLiterals.get(0).getText());
            offset = Long.parseLong(intLiterals.get(1).getText());
        } else {
            limit = Long.parseLong(intLiterals.get(0).getText());
        }

        RowStream stream = new ListRowStream(rows);
        if (whereExpr != null && !whereExpr.trim().isEmpty()) {
            final ExpressionEvaluator evaluator = new ExpressionEvaluator(table, functionRegistry, catalog, this);
            final Expression predicate = ExpressionEvaluator.parse(whereExpr);
            stream = new FilterRowStream(stream, new RowPredicate() {
                @Override
                public boolean test(final Row row) {
                    Object result = evaluator.evaluate(predicate, row);
                    return result instanceof Boolean && (Boolean) result;
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
                                       final FrostlakeParser.TableExpressionContext tableExpr) {
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
        } else if (allTables.size() > 1 || tableExpr.joinClause().size() > 0) {
            mode = WhereEvaluationMode.WITH_ALIASES;
            // Provide custom evaluator for JOIN queries
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

        OperatorContext context = contextBuilder.build();

        // Create and execute WHERE operator
        WhereOperator whereOperator = new WhereOperator(whereExpr, mode);
        return whereOperator.execute(rows, context);
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

    private List<Row> applyJoin(final List<Row> leftRows, final Table leftTable,
                                final List<Row> rightRows, final Table rightTable,
                                final FrostlakeParser.JoinClauseContext joinCtx,
                                final Map<String, Table> aliasToTable,
                                final List<Table> allTables) {
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
            for (final FrostlakeParser.IdentifierContext id : joinCtx.identifierList().identifier()) {
                usingCols.add(id.getText().toUpperCase());
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

        // Create condition evaluator that combines rows and evaluates the condition
        JoinConditionEvaluator conditionEvaluator = new JoinConditionEvaluator() {
            @Override
            public boolean matches(final Row leftRow, final Row rightRow) {
                // Combine rows to evaluate condition
                List<Object> combinedValues = new ArrayList<>(leftRow.getValues());
                combinedValues.addAll(rightRow.getValues());
                Row combinedRow = new Row(combinedValues);

                try {
                    // Evaluate the pre-parsed AST with the shared evaluator (qualified columns resolve
                    // per-table across the join; AND/OR/NOT handled natively by the AST evaluator).
                    Object result = joinConditionEval.evaluate(joinConditionAst, combinedRow);
                    return result instanceof Boolean && (Boolean) result;
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

        // Apply masking policy substitution — wrap masked column expressions with policy body
        projectionExpressions = applyMaskingPolicies(projectionExpressions, table);

        // Create expression evaluator based on context
        RowExpressionEvaluator expressionEvaluator;
        if (allTables.size() > 1) {
            // Multi-table query - use alias-aware evaluation
            final ExpressionEvaluator projEvalMt = new ExpressionEvaluator(table, functionRegistry, catalog, this);
            projEvalMt.setMultiTableContext(aliasToTable, allTables);
            expressionEvaluator = new RowExpressionEvaluator() {
                @Override
                public Object evaluate(final Expression expr, final Row row) {
                    return projEvalMt.evaluate(expr, row);
                }
            };
        } else {
            // Single table query - simple evaluation
            final ExpressionEvaluator projEval = new ExpressionEvaluator(table, functionRegistry, catalog, this);
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

        // Create and execute PROJECT operator
        ProjectOperator projectOp = new ProjectOperator(projectionExpressions, columnAliases,
            expressionEvaluator);
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
                                   final List<Table> allTables) {
        return groupByEvaluator.applyGroupBy(rows, table, ctx, aliasToTable, allTables);
    }

    private List<Row> applyImplicitGroupBy(final List<Row> rows, final Table table,
                                           final FrostlakeParser.SelectClauseContext ctx,
                                           final Map<String, Table> aliasToTable, final List<Table> allTables) {
        return groupByEvaluator.applyImplicitGroupBy(rows, table, ctx, aliasToTable, allTables);
    }

    /**
     * Apply HAVING clause using operator pipeline.
     * HAVING filters rows after GROUP BY based on aggregate conditions.
     */
    private List<Row> applyHaving(final List<Row> rows, final FrostlakeParser.SelectClauseContext ctx) {
        String havingExpr = getOriginalText(ctx.havingClause().booleanExpr());

        // Per output column, the canonical AST form of the SELECT item (+ its alias). A HAVING
        // condition referencing an aggregate / alias / group column resolves to the already-computed
        // value via this result context (instead of re-aggregating).
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
                ev.setResultContext(rc);
                final Object result = ev.evaluate(condition, aggregatedRow);
                return result instanceof Boolean && (Boolean) result;
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
                                    final Table table) {
        final FrostlakeParser.BooleanExprContext qualifyBool = ctx.qualifyClause().booleanExpr();
        final String qualifyExpr = getOriginalText(qualifyBool);

        // Window functions written INLINE in QUALIFY (e.g. QUALIFY ROW_NUMBER() OVER (...) = 1), rather
        // than referenced through a SELECT alias, are absent from windowFunctionResults. Precompute each
        // over all rows; each stays a WindowFunctionExpression node in the parsed predicate (keyed by its
        // source text) and the evaluator resolves it per row from the result context under that same key.
        final List<FrostlakeParser.FunctionCallExprContext> inlineWindowFns = new ArrayList<>();
        collectWindowFunctionCalls(qualifyBool, inlineWindowFns);
        final Map<String, List<Object>> inlineWindowValues = new LinkedHashMap<>();
        if (!inlineWindowFns.isEmpty()) {
            final Map<FrostlakeParser.OverClauseContext, Map<List<Object>, List<Row>>> overCache = new HashMap<>();
            for (final FrostlakeParser.FunctionCallExprContext wfn : inlineWindowFns) {
                final List<Object> perRowValues = new ArrayList<>(rows.size());
                for (int r = 0; r < rows.size(); r++) {
                    perRowValues.add(evaluateWindowFunction(wfn, rows, r, ctx, table, overCache));
                }
                inlineWindowValues.put(getOriginalText(wfn), perRowValues);
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
                qEv.setResultContext(rc);
                final Object result = qEv.evaluate(condition, row);
                return result instanceof Boolean && (Boolean) result;
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

    private Map<Integer, Map<Integer, Object>> computeWindowFunctions(final List<Row> rows, final FrostlakeParser.SelectClauseContext ctx, final Table table) {
        return windowEvaluator.computeWindowFunctions(rows, ctx, table);
    }

    private List<Row> addWindowFunctionsToRows(final List<Row> rows,
                                                final Map<Integer, Map<Integer, Object>> windowFunctionResults,
                                                final FrostlakeParser.SelectClauseContext ctx,
                                                final Table table) {
        return windowEvaluator.addWindowFunctionsToRows(rows, windowFunctionResults, ctx, table);
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

    int getColumnIndex(final Table table, final String columnName) {
        return ValueComparisons.getColumnIndex(table, columnName);
    }

    Object evaluateExpression(final String expr, final Row row, final Table table) {
        ExpressionEvaluator evaluator = new ExpressionEvaluator(table, functionRegistry, catalog, this);
        return evaluator.evaluate(expr, row);
    }

    private Object evaluateExpressionWithAliases(final String expr, final Row row, final Table table,
                                                final Map<String, Table> aliasToTable, final List<Table> allTables) {
        // Evaluate via the AST evaluator with alias-aware multi-table resolution — handles scalar
        // subqueries, object access (a:b), and qualified/bare column references uniformly.
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(table, functionRegistry, catalog, this);
        evaluator.setMultiTableContext(aliasToTable, allTables);
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
    private ResultSet executePivot(final FrostlakeParser.SelectClauseContext ctx, final Table table, final List<Row> rows, final FrostlakeParser.TableSourceContext tableSource) {
        return pivotExecutor.executePivot(ctx, table, rows, tableSource);
    }

    /**
     * Execute UNPIVOT operation to transform columns into rows
     */
    private ResultSet executeUnpivot(final FrostlakeParser.SelectClauseContext ctx, final Table table, final List<Row> rows, final FrostlakeParser.TableSourceContext tableSource) {
        return pivotExecutor.executeUnpivot(ctx, table, rows, tableSource);
    }

    /**
     * Apply aggregate function to a list of values
     */
    private Object applyAggregateFunction(final String funcName, final List<Object> values) {
        return AggregateFunctions.applyAggregateFunction(funcName, values);
    }

    /**
     * Execute a table reference (table or subquery) and return rows with table metadata, applying a trailing
     * SAMPLE / TABLESAMPLE clause if present.
     */
    TableData executeTableReference(final FrostlakeParser.TableReferenceContext ctx, final Map<String, Object> lateralContext, final Map<String, ResultSet> cteResults) {
        final TableData resolved = resolveTableReference(ctx, lateralContext, cteResults);
        if (ctx.sampleClause() == null) {
            return resolved;
        }
        return new TableData(resolved.table, sampleRows(resolved.rows, ctx.sampleClause()), resolved.alias);
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
        final String sizeText = (sample.INTEGER_LITERAL() != null ? sample.INTEGER_LITERAL() : sample.FLOAT_LITERAL()).getText();
        final double sizeVal = Double.parseDouble(sizeText);
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
            Table dynamicTable = catalog.resolveTable(dynamicTableName);
            String fqName = getFullyQualifiedTableName(dynamicTableName);
            List<Row> dynamicRows = storageEngine.getTableStorage(fqName).scan();
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
                    String argValueExpr = getOriginalText(argCtx.expression());
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
                    String argValueExpr = getOriginalText(argCtx.expression());
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

        // Handle VALUES clause
        if (source.VALUES() != null && source.valueTupleList() != null) {
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

        // Regular table
        Table table = catalog.resolveTable(tableName);
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

    /** Resolve a stream by name in the current schema, or null if there is no such stream. */
    private Stream findStream(final String name) {
        if (name == null) {
            return null;
        }
        final String db = catalog.getCurrentDatabase();
        final String sc = catalog.getCurrentSchema();
        if (db == null || sc == null) {
            return null;
        }
        try {
            return catalog.getDatabase(db).getSchema(sc).getStream(name.toUpperCase());
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
                transactionManager.getCurrentTransaction().registerStreamConsumption(stream);   // DML: consume on commit
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
        for (final StreamRecord record : stream.getUnconsumedNetRecords()) {
            final List<Object> values = new ArrayList<>(record.getValues());
            appendStreamMetadataValues(values, record);
            rows.add(new Row(values));
        }
        return new TableData(virtual, rows, alias);
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
        for (final FrostlakeParser.SelectClauseContext branch : branches) {
            final String branchTable = branchBaseTable(branch);
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
            for (final StreamRecord record : stream.getUnconsumedNetRecords()) {
                // A null tag (single-branch view, or a legacy record) is kept — a lone branch owns
                // every record; otherwise the record belongs to the branch it was captured from.
                if (record.getSourceTable() != null && !record.getSourceTable().equalsIgnoreCase(branchTable)) {
                    continue;
                }
                final Row baseRow = new Row(record.getValues());
                if (whereText != null) {
                    final Object keep = projectionEval.evaluate(whereText, baseRow);
                    if (!(keep instanceof Boolean) || !((Boolean) keep).booleanValue()) {
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
        final String[] parts = ParseTreeText.qualifiedNameParts(
            branch.tableExpression().tableReference(0).tableSource().qualifiedName());
        return parts[parts.length - 1].toUpperCase();
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
        }
        throw new RuntimeException("Unknown time travel point type");
    }

    /** Execute CHANGES clause — returns inserted/updated/deleted rows between two snapshots. */
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
            String posExpr = getOriginalText(mixCtx.expression());
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
                    String argValueExpr = getOriginalText(argCtx.expression());

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
                    return PythonTableFunctionExecutor
                        .executePythonTableFunction(udtf, callArgs);
                }
                String sql = substituteSqlParams(udtf.getBody(), udtf.getParameters(), callArgs);
                List<ResultSet> results = execute(sql);
                return results.isEmpty() ? new ResultSet(new ArrayList<>(), new ArrayList<>()) : results.get(0);
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

        throw new RuntimeException("Table function must be called with arguments (e.g., GENERATOR(ROWCOUNT => 10), RESULT_SCAN('<query_id>'))");
    }

    /**
     * Execute a LATERAL join - evaluate right side for each left side row
     */
    private TableData executeLateralJoin(final List<Row> leftRows, final Table leftTable,
                                          final FrostlakeParser.TableReferenceContext rightTableRef,
                                          final FrostlakeParser.JoinClauseContext joinCtx,
                                          final Map<String, Table> aliasToTable) {
        List<Row> resultRows = new ArrayList<>();
        Table rightTable = null;
        String rightAlias = null;

        for (final Row leftRow : leftRows) {
            // Create context with left row values accessible by column name
            Map<String, Object> lateralContext = new HashMap<>();

            // Add columns from all tables in the context
            int offset = 0;
            for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
                String alias = entry.getKey();
                Table t = entry.getValue();
                for (int i = 0; i < t.getColumns().size(); i++) {
                    String colName = t.getColumns().get(i).getName();
                    lateralContext.put(colName, leftRow.getValue(offset + i));
                    // Also add with table prefix and alias prefix
                    lateralContext.put(t.getName() + "." + colName, leftRow.getValue(offset + i));
                    lateralContext.put(alias + "." + colName, leftRow.getValue(offset + i));
                }
                offset += t.getColumns().size();
            }

            // Execute right side with lateral context
            TableData rightData = executeTableReference(rightTableRef, lateralContext, null);

            // Capture table metadata from first iteration
            if (rightTable == null) {
                rightTable = rightData.table;
                rightAlias = rightData.alias;
            }

            // Combine left row with each right row
            for (final Row rightRow : rightData.rows) {
                List<Object> combinedValues = new ArrayList<>(leftRow.getValues());
                combinedValues.addAll(rightRow.getValues());
                resultRows.add(new Row(combinedValues));
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
        List<TableColumn> allColumns = new ArrayList<>(left.getColumns());
        allColumns.addAll(right.getColumns());
        return new Table("joined", allColumns, false);
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

    Object getQualifiedColumnValueFromTables(final Row row, final List<Table> tables,
                                                     final Map<String, Table> aliasToTable,
                                                     String qualifiedName) {
        // Remove parentheses only if present (avoid a per-access regex on the common path)
        if (qualifiedName.indexOf('(') >= 0 || qualifiedName.indexOf(')') >= 0) {
            qualifiedName = qualifiedName.replace("(", "").replace(")", "");
        }
        qualifiedName = qualifiedName.trim();

        // Try to parse as string literal (remove quotes)
        if (qualifiedName.startsWith("'") && qualifiedName.endsWith("'")) {
            return qualifiedName.substring(1, qualifiedName.length() - 1);
        }

        // Try to parse as a numeric literal — only when it actually looks numeric, so column names
        // (which start with a letter/underscore) don't pay a thrown-and-caught exception per access.
        if (!qualifiedName.isEmpty()) {
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

        // If not found in lateral context, try standard evaluation
        try {
            return evaluateExpression(expr, null, null);
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
