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
import dev.frostlake.config.AccountIdentity;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.config.S3PathResolver;
import dev.frostlake.executor.copy.CopyCommandExecutor;
import dev.frostlake.executor.expressions.AntlrExpressionParser;
import dev.frostlake.executor.expressions.AstPrinterVisitor;
import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.CollationSpec;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionEvaluatorVisitor;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.IntervalCasts;
import dev.frostlake.executor.expressions.IntervalLiteralSyntax;
import dev.frostlake.executor.expressions.LateConstantRefusal;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.NamedCallRewrite;
import dev.frostlake.executor.expressions.PlannedCorrelations;
import dev.frostlake.executor.expressions.QualifyWithoutWindow;
import dev.frostlake.executor.expressions.RelationScopes;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.expressions.SqlTruth;
import dev.frostlake.executor.expressions.SqlUdfBodyFrame;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.executor.expressions.SubqueryTypeRanking;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.executor.expressions.UntypedNullFold;
import dev.frostlake.executor.operators.AsofJoinOperator;
import dev.frostlake.executor.operators.HavingEvaluator;
import dev.frostlake.executor.operators.HavingOperator;
import dev.frostlake.executor.operators.JoinConditionEvaluator;
import dev.frostlake.executor.operators.JoinOperator;
import dev.frostlake.executor.operators.JoinType;
import dev.frostlake.executor.operators.LimitOperator;
import dev.frostlake.executor.operators.MemoizedRows;
import dev.frostlake.executor.operators.Operator;
import dev.frostlake.executor.operators.OperatorContext;
import dev.frostlake.executor.operators.OperatorContextBuilder;
import dev.frostlake.executor.operators.PipelineStage;
import dev.frostlake.executor.operators.ProjectOperator;
import dev.frostlake.executor.operators.QualifyEvaluator;
import dev.frostlake.executor.operators.QualifyOperator;
import dev.frostlake.executor.operators.ResultSetProvider;
import dev.frostlake.executor.operators.RowExpressionEvaluator;
import dev.frostlake.executor.operators.RowsProvider;
import dev.frostlake.executor.operators.SourceOperator;
import dev.frostlake.executor.operators.StageOperator;
import dev.frostlake.executor.operators.WhereEvaluationMode;
import dev.frostlake.executor.operators.WhereOperator;
import dev.frostlake.executor.procedural.ProceduralException;
import dev.frostlake.executor.streaming.FilterRowStream;
import dev.frostlake.executor.streaming.LimitRowStream;
import dev.frostlake.executor.streaming.ListRowStream;
import dev.frostlake.executor.streaming.RowPredicate;
import dev.frostlake.executor.streaming.RowStream;
import dev.frostlake.executor.udf.UdfRuntimes;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.functions.scalar.AutoTemporalParser;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.functions.scalar.context.CurrentAccount;
import dev.frostlake.functions.scalar.context.GetDdl;
import dev.frostlake.functions.scalar.context.LastQueryId;
import dev.frostlake.functions.scalar.context.PolicyBodyScope;
import dev.frostlake.functions.scalar.conversion.ToUuid;
import dev.frostlake.functions.scalar.file.BuildScopedFileUrl;
import dev.frostlake.functions.scalar.file.BuildStageFileUrl;
import dev.frostlake.functions.scalar.file.GetAbsolutePath;
import dev.frostlake.functions.scalar.file.GetPresignedUrl;
import dev.frostlake.functions.scalar.file.GetRelativePath;
import dev.frostlake.functions.scalar.file.GetStageLocation;
import dev.frostlake.functions.scalar.file.NamedStage;
import dev.frostlake.functions.scalar.file.StageUrlFunction;
import dev.frostlake.functions.scalar.file.ToFile;
import dev.frostlake.functions.scalar.file.TryToFile;
import dev.frostlake.functions.table.InferSchema;
import dev.frostlake.functions.table.QueryHistoryFunction;
import dev.frostlake.functions.table.QueryHistoryScope;
import dev.frostlake.functions.table.QueryHistoryScopeDefaults;
import dev.frostlake.functions.table.QueryRunner;
import dev.frostlake.functions.table.ResultScan;
import dev.frostlake.functions.table.ToQuery;
import dev.frostlake.functions.window.WholePartitionAggregates;
import dev.frostlake.functions.window.WindowFunctionArity;
import dev.frostlake.functions.window.WindowFunctionNames;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.NoCurrentDatabaseRefusal;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.QueryHistory;
import dev.frostlake.metastore.QueryHistoryTracker;
import dev.frostlake.metastore.SqlObject;
import dev.frostlake.metastore.StatementKind;
import dev.frostlake.metastore.model.AggregationPolicy;
import dev.frostlake.metastore.model.ChangeType;
import dev.frostlake.metastore.model.CheckConstraint;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DefaultValueExpression;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.JoinPolicy;
import dev.frostlake.metastore.model.JoinedRelations;
import dev.frostlake.metastore.model.MaskingPolicy;
import dev.frostlake.metastore.model.MaterializedView;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Pipe;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.ProjectionPolicy;
import dev.frostlake.metastore.model.RelationSlot;
import dev.frostlake.metastore.model.RowAccessPolicy;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurableObjectType;
import dev.frostlake.metastore.model.SecurityObject;
import dev.frostlake.metastore.model.SecurityObjectKind;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.StageType;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.StreamRecord;
import dev.frostlake.metastore.model.StreamSourceType;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.metastore.model.View;
import dev.frostlake.parser.AnsiFromFormSyntax;
import dev.frostlake.parser.ColdPrediction;
import dev.frostlake.parser.EmptySchemaPartSyntax;
import dev.frostlake.parser.FinishedPredicateSyntax;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.IntoListLookahead;
import dev.frostlake.parser.LimitValueSyntax;
import dev.frostlake.parser.PositionNeedleSyntax;
import dev.frostlake.parser.PostParseRefusals;
import dev.frostlake.parser.QuantifiedComparisonOperand;
import dev.frostlake.parser.ScriptingStatementPlacement;
import dev.frostlake.parser.SignatureTailLine;
import dev.frostlake.parser.SqlSyntaxException;
import dev.frostlake.parser.StageArgumentSyntax;
import dev.frostlake.parser.StatementSeparation;
import dev.frostlake.parser.SyntaxErrorListener;
import dev.frostlake.parser.ValueFunctionSyntax;
import dev.frostlake.persistence.WalStatementKinds;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.storage.TableSnapshot;
import dev.frostlake.storage.TableStorage;
import dev.frostlake.stream.StreamManager;
import dev.frostlake.system.SystemViews;
import dev.frostlake.system.UnmodeledSystemViews;
import dev.frostlake.task.TaskScheduler;
import dev.frostlake.transaction.StreamReadScope;
import dev.frostlake.transaction.Transaction;
import dev.frostlake.transaction.TransactionManager;
import dev.frostlake.transaction.TransactionWriteSet;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.DeclaredTypeFold;
import dev.frostlake.types.FileType;
import dev.frostlake.types.GeographyType;
import dev.frostlake.types.GeometryType;
import dev.frostlake.types.NumericLiteralTypes;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StatementResultWidths;
import dev.frostlake.types.StringType;
import dev.frostlake.types.UuidType;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorType;
import dev.frostlake.values.ApproximateValues;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.CodePointText;
import dev.frostlake.values.ExactValues;
import dev.frostlake.values.GeoValue;
import dev.frostlake.values.ValueRange;
import dev.frostlake.values.VariantValue;
import dev.frostlake.values.VectorValue;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Query Executor - Executes SQL statements using ANTLR-based parsing
 */
public class QueryExecutor {

    private static final Logger logger = LoggerFactory.getLogger(QueryExecutor.class);

    private final Catalog catalog;

    /** True while CREATE VIEW compiles its body in the view's own schema. */
    private boolean compilingViewBody;

    /** A derived table's or a CTE's blanked body per body as written, and "" where none can be (see prunedBody). */
    private final Map<ParserRuleContext, String> prunedBodies =
        Collections.synchronizedMap(new WeakHashMap<ParserRuleContext, String>());
    /** The same, decided by a run that judges no names, which a run that judges them may not reuse (see prunedBody). */
    private final Map<ParserRuleContext, String> unjudgedPrunedBodies =
        Collections.synchronizedMap(new WeakHashMap<ParserRuleContext, String>());
    private final StorageEngine storageEngine;
    private final FunctionRegistry functionRegistry;
    private final TransactionManager transactionManager;
    private final boolean deferredApply;   // transaction.deferredApply: buffer DML into a write set, apply on COMMIT
    private final boolean enforceTypes;    // constraints.enforce.types: coerce write values to the column type
    private final SystemViews systemViews;
    private final UnmodeledSystemViews unmodeledSystemViews = new UnmodeledSystemViews();
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
    /** Told where each top-level statement of a request of several begins and ends; see {@link StatementBoundaries}. */
    private final ThreadLocal<StatementBoundaries> statementBoundaries = new ThreadLocal<StatementBoundaries>();
    /** The plan of the last SELECT operand this thread planned, as text — see {@link #describeLastSelectPlan()}. */
    private final ThreadLocal<String> lastSelectPlan = new ThreadLocal<String>();
    /** Set by WAL replay only; see {@link #setNextStatementInstant}. */
    private Instant pendingStatementInstant;
    /** What the last outermost statement called "now"; see {@link #currentStatementInstant}. */
    private Instant lastStatementInstant;
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
    /** The relations the running LATERAL item's arguments read, typing the names they evaluate. */
    private final LateralArgumentTypes lateralArgumentTypes = new LateralArgumentTypes();
    private final ThreadLocal<Deque<List<Table>>> windowAllTablesStack =
        new ThreadLocal<Deque<List<Table>>>() {
            @Override
            protected Deque<List<Table>> initialValue() {
                return new ArrayDeque<>();
            }
        };
    // The statistics bound of the SELECT clause executing on this thread (see CountStatisticsBound),
    // per thread like the stacks above; executeSingleSelect scopes it to its own clause.
    private final ThreadLocal<CountStatisticsBound> statisticsBound = new ThreadLocal<>();
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
    // A view's, a materialized view's or a dynamic table's body parses once, however many statements read it.
    private static final BoundedParseCache<FrostlakeParser.SelectStatementContext> SELECT_CACHE =
        new BoundedParseCache<>(256, 4096);

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
        this.systemViews = new SystemViews(catalog, storageEngine);
        this.visitor = new SQLCommandVisitor(catalog, this);
        this.resultCache = new QueryResultCache(
            config != null ? config.getQueryResultCacheSize() : 100);
        this.queryHistoryTracker = new QueryHistoryTracker(
            config != null ? config.getQueryHistorySize() : 10000);
        this.showExecutor = new ShowCommandExecutor(catalog, transactionManager, queryHistoryTracker, sessionVariables,
            functionRegistry, AccountIdentity.of(config), storageEngine);
        // INFORMATION_SCHEMA.QUERY_HISTORY is a TABLE FUNCTION in Snowflake (the bare-object form
        // does not exist there, live-verified); registered here because it needs the tracker.
        functionRegistry.registerTableFunction(new QueryHistoryFunction(queryHistoryTracker));
        // The scoped listings default to the CALLER's session, user and warehouse, read as each call runs.
        final QueryHistoryScopeDefaults callerScope = new QueryHistoryScopeDefaults() {
            @Override
            public Long currentSession() {
                return securityManager != null ? securityManager.getSessionContext().getSessionNumber() : null;
            }

            @Override
            public String currentUser() {
                return securityManager != null ? securityManager.getSessionContext().getDisplayUser() : null;
            }

            @Override
            public String currentWarehouse() {
                return catalog.getCurrentWarehouse();
            }
        };
        functionRegistry.registerTableFunction(
            new QueryHistoryFunction(queryHistoryTracker, QueryHistoryScope.SESSION, callerScope));
        functionRegistry.registerTableFunction(
            new QueryHistoryFunction(queryHistoryTracker, QueryHistoryScope.USER, callerScope));
        functionRegistry.registerTableFunction(
            new QueryHistoryFunction(queryHistoryTracker, QueryHistoryScope.WAREHOUSE, callerScope));
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

        // Re-register GET_DDL with a callback that PLANS a relation's defining query through this executor
        // and answers its shape: the DDL of a view or a dynamic table renders the output column list, and
        // planning derives it without running the query.
        functionRegistry.register(new GetDdl(catalog, new QueryRunner() {
            @Override
            public ResultSet runQuery(final String sql) {
                return planDetachedBody(sql).shape;
            }
        }));

        // The general runner an optional function pack reaches through the registry — the same callback,
        // published rather than captured, so a pack's function can run SQL without the executor knowing
        // that pack exists.
        functionRegistry.setQueryRunner(new QueryRunner() {
            @Override
            public ResultSet runQuery(final String sql) {
                final List<ResultSet> results = execute(sql);
                return results.isEmpty() ? null : results.get(results.size() - 1);
            }
        });

        // Register LAST_QUERY_ID() scalar function with access to result cache
        functionRegistry.register(new LastQueryId(resultCache));

        // Register the NEXTVAL sequence function (Snowflake has no CURRVAL — live-verified)
        // NEXTVAL('seq') as a FUNCTION is not Snowflake syntax (live-verified: "Unknown function
        // NEXTVAL") — only the member form seq.NEXTVAL exists, handled by the evaluator directly.

        // Register CURRENT_ACCOUNT() function with access to config
        functionRegistry.register(new CurrentAccount(config));

        // Re-register TO_FILE / TRY_TO_FILE with a resolver that can reach stages: a FILE value is the
        // metadata of a REAL staged file, so both must resolve '@stage/path' against the filesystem to
        // read its size, mtime and MD5 — and to fail "was not found" exactly as Snowflake does.
        final StageFileResolver stageFileResolver = new StageFileResolver(this, catalog);
        functionRegistry.register(new ToFile(stageFileResolver));
        functionRegistry.register(new TryToFile(stageFileResolver));
        functionRegistry.register(new GetPresignedUrl(stageFileResolver, engineConfig));
        functionRegistry.register(new BuildStageFileUrl(stageFileResolver, engineConfig));
        functionRegistry.register(new BuildScopedFileUrl(stageFileResolver, engineConfig));
        functionRegistry.register(new GetStageLocation(stageFileResolver, engineConfig));
        functionRegistry.register(new GetAbsolutePath(stageFileResolver, engineConfig));
        functionRegistry.register(new GetRelativePath(stageFileResolver, engineConfig));
        // INFER_SCHEMA reads the files of a stage the way COPY finds them; SHOW FUNCTIONS does not list it.
        functionRegistry.registerUnlistedTableFunction(new InferSchema(new InferSchemaStages(this)));
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
        final String dbName = catalog.getCurrentDatabase();
        if (dbName != null && !"SNOWFLAKE".equalsIgnoreCase(dbName)) {
            // Only enforce read-only for user-created databases, not the system SNOWFLAKE db
            try {
                final Database db = catalog.getDatabase(dbName);
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
     * Bind what this thread's next requests tell about the boundaries between their statements.
     *
     * @param boundaries the listener, or null for none
     * @return the listener it displaces, for {@link #restoreStatementBoundaries}
     */
    public StatementBoundaries bindStatementBoundaries(final StatementBoundaries boundaries) {
        final StatementBoundaries displaced = statementBoundaries.get();
        statementBoundaries.set(boundaries);
        return displaced;
    }

    /**
     * Put back the listener {@link #bindStatementBoundaries} displaced.
     *
     * @param displaced the displaced listener, or null for none
     */
    public void restoreStatementBoundaries(final StatementBoundaries displaced) {
        if (displaced == null) {
            statementBoundaries.remove();
        } else {
            statementBoundaries.set(displaced);
        }
    }

    /**
     * Execute SQL using ANTLR parsing and visitor pattern
     */
    public List<ResultSet> execute(final String sql) {
        // A statement run on its own - a UDF's query body, EXECUTE IMMEDIATE, a block's statement - reads its
        // values as it produces them, even inside a relation's body (see RelationBody).
        final boolean outerBody = RelationBody.suspend();
        try {
            return executeStatementText(sql);
        } finally {
            RelationBody.end(outerBody);
        }
    }

    /**
     * {@link #execute(String)} for a text written in place of another, whose places are reported as that other text's:
     * a place on the first line is moved back by {@code origin}'s column, a place on a later line keeps its own. A CALL
     * of a SYSTEM$ function runs as the SELECT of the same call, and reports in the CALL's own text.
     *
     * @param sql    the text
     * @param origin where the text's first line is counted from
     * @return the statement's results
     */
    public List<ResultSet> executeFrom(final String sql, final SourcePosition origin) {
        final boolean outerBody = RelationBody.suspend();
        try {
            rejectEmptyStatement(sql);
            final SourcePosition displaced = LeadingCommentOffset.begin(origin);
            try {
                return executeRebased(sql);
            } finally {
                LeadingCommentOffset.end(displaced);
            }
        } finally {
            RelationBody.end(outerBody);
        }
    }

    /** {@link #execute(String)} for a view's body, whose items wait for the statement reading the view. */
    private List<ResultSet> executeRelationBody(final String body) {
        final boolean outerBody = RelationBody.begin();
        try {
            return executeStatementText(body);
        } finally {
            RelationBody.end(outerBody);
        }
    }

    private List<ResultSet> executeStatementText(final String sql) {
        rejectEmptyStatement(sql);
        final SourcePosition displacedComment =
            LeadingCommentOffset.begin(LeadingCommentOffset.of(sql));
        try {
            return executeRebased(sql);
        } finally {
            LeadingCommentOffset.end(displacedComment);
        }
    }

    /** {@link #execute(String)}'s body, with the leading-comment origin already in force. */
    private List<ResultSet> executeRebased(final String sql) {
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
        // Read the clock ONCE for this statement, and only for the outermost one: Snowflake's
        // CURRENT_TIMESTAMP is statement-stable, so every row and every nested call inside a procedural
        // block sees the same instant. See StatementClock.
        final Instant displacedInstant = outermost ? StatementClock.pin(pendingStatementInstant) : null;
        // The session's zone is pinned on the same boundary and for the same reason: a TIMESTAMP_LTZ is
        // an instant, and reading or rendering one needs the zone it should be shown in. Both live deep
        // in static helpers with no session to ask, and one engine serves many sessions.
        final ZoneId displacedZone = outermost ? SessionZone.pin(sessionTimezoneName()) : null;
        // What a BARE TIMESTAMP means rides along: it is resolved wherever the word is written, which
        // includes the expression layer, and that has no session to ask either.
        final String displacedMapping = outermost
            ? SessionTimestampMapping.pin(sessionParameter("TIMESTAMP_TYPE_MAPPING")) : null;
        // So do the session's output formats, which every string rendering of a temporal follows.
        final Map<String, String> displacedFormats = outermost ? SessionOutputFormats.pin(sessionOutputFormats()) : null;
        // So does the role a missing-object refusal addresses, with the account its hint may name.
        final SessionRole displacedRole = outermost ? SessionRole.pin(
            securityManager == null ? null : securityManager.getSessionContext(),
            engineConfig == null ? null : engineConfig.getAccountId()) : null;
        if (outermost) {
            // Remember it as well as pinning it: the write-ahead log records the statement at COMMIT, by
            // which point the pin has been released, and reading the clock again there would log the
            // commit time instead of the statement's.
            lastStatementInstant = StatementClock.instant();
        }
        pendingStatementInstant = null;
        try {
            if (outermost) {
                visitor.getProceduralExecutor().clearCursorsAndExceptions();
            }
            final List<ResultSet> results = executeWithLateralContext(sql, null);
            return outermost ? ltzInSessionZone(results) : results;
        } finally {
            executeReentryDepth--;
            if (outermost) {
                StatementClock.restore(displacedInstant);
                SessionZone.restore(displacedZone);
                SessionTimestampMapping.restore(displacedMapping);
                SessionOutputFormats.restore(displacedFormats);
                SessionRole.restore(displacedRole);
            }
        }
    }

    /**
     * What a statement that produces no rows of its own answers: ONE ROW, as on a real account — a VARCHAR column
     * named {@code status} carrying a sentence ("Table T successfully created."). An EXECUTE IMMEDIATE answers with
     * the sentence of the statement its text ran, and only CREATE and DROP name their object in it.
     *
     * @param executedStatement the statement that ran
     * @return the status row
     */
    ResultSet statusAnswer(final FrostlakeParser.StatementContext executedStatement) {
        final List<ResultSetColumn> statusColumns = new ArrayList<>();
        statusColumns.add(new ResultSetColumn("status", StringType.VARCHAR));
        final List<Row> statusRows = new ArrayList<>();
        final FrostlakeParser.StatementContext described = visitor.describedStatement(executedStatement);
        final FrostlakeParser.ObjectNameContext reference = described == null
            || described.getStart().getType() != FrostlakeParser.CREATE
            && described.getStart().getType() != FrostlakeParser.DROP ? null
            : DdlStatusMessage.identifierReference(described);
        final String[] referencedParts = reference == null ? null : resolveObjectNameParts(reference);
        statusRows.add(new Row(Arrays.asList(
            (Object) DdlStatusMessage.forStatement(described, ConditionalDdlOutcome.take(),
                referencedParts == null ? null : referencedParts[referencedParts.length - 1]))));
        return new ResultSet(statusColumns, statusRows);
    }

    /**
     * The statement's results with every TIMESTAMP_LTZ column's instants in the session's zone, while that zone
     * is still pinned. A column reference already reads one that way (see SharedFunctionHelpers#inSessionZone);
     * this covers what reaches a client without one — a SELECT * or a MIN or MAX over a stored LTZ, which keeps
     * the offset in force when it was written. A row is copied before a cell changes, so a stored row is never
     * rewritten under its table.
     *
     * @param results the statement's result sets
     * @return the same result sets
     */
    private static List<ResultSet> ltzInSessionZone(final List<ResultSet> results) {
        for (final ResultSet rs : results) {
            if (rs == null) {
                continue;
            }
            final List<ResultSetColumn> columns = rs.getColumns();
            final List<Integer> ltzColumns = new ArrayList<Integer>();
            for (int c = 0; c < columns.size(); c++) {
                final DataType type = columns.get(c).getDataType();
                if (type != null && "TIMESTAMP_LTZ".equalsIgnoreCase(type.getName())) {
                    ltzColumns.add(c);
                }
            }
            if (ltzColumns.isEmpty()) {
                continue;
            }
            final List<Row> rows = rs.getRows();
            for (int r = 0; r < rows.size(); r++) {
                final Row row = rows.get(r);
                Row zoned = row;
                for (final Integer column : ltzColumns) {
                    if (column.intValue() >= row.size()) {
                        continue;
                    }
                    final Object value = row.getValue(column.intValue());
                    final Object here = SharedFunctionHelpers.inSessionZone(value);
                    if (here != value) {
                        if (zoned == row) {
                            zoned = row.copy();
                        }
                        zoned.setValue(column.intValue(), here);
                    }
                }
                if (zoned != row) {
                    rows.set(r, zoned);
                }
            }
        }
        return results;
    }

    /**
     * The collation each projected column compares under, for a DISTINCT over the projection.
     *
     * @param columnSources the expression each output column projects, null where it has none
     * @param table         the base relation, or null
     * @param aliasToTable  its alias map, or null
     * @param allTables     its joined relations, or null
     * @return one entry per column, null where the column carries no collation
     */
    private CollationSpec[] projectionCollations(final List<String> columnSources, final Table table,
                                                 final Map<String, Table> aliasToTable,
                                                 final List<Table> allTables) {
        if (!KeyCollations.reachable(columnSources, table, aliasToTable, allTables)) {
            return null;
        }
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(table, functionRegistry, catalog, this);
        if (aliasToTable != null && allTables != null) {
            evaluator.setMultiTableContext(aliasToTable, allTables);
        }
        return KeyCollations.resolve(columnSources, evaluator);
    }

    /**
     * Stamp each projected column with the collation its expression carries.
     *
     * @param columns    the output columns, replaced in place where a collation applies
     * @param collations one entry per column, null entries and a null array for none
     */
    private void stampCollations(final List<ResultSetColumn> columns, final CollationSpec[] collations) {
        if (collations == null) {
            return;
        }
        for (int i = 0; i < columns.size() && i < collations.length; i++) {
            if (collations[i] != null) {
                columns.set(i, columns.get(i).withCollation(collations[i].getText()));
            }
        }
    }

    /** The session's TIMEZONE parameter, or null when nothing has set one. */
    private Object sessionTimezoneName() {
        return sessionParameter("TIMEZONE");
    }

    /** A session parameter's value, or null when there is no session or it has not been set. */
    private Object sessionParameter(final String name) {
        return securityManager == null ? null
            : securityManager.getSessionContext().getSessionParameter(name);
    }

    /** The output formats the session has set, by parameter name, with their quotes removed. */
    private Map<String, String> sessionOutputFormats() {
        final Map<String, String> set = new HashMap<>();
        for (final String name : SessionOutputFormats.names()) {
            final Object value = sessionParameter(name);
            if (value != null) {
                set.put(name, unquotedParameter(String.valueOf(value)));
            }
        }
        return set;
    }

    /** A parameter value as stored, without the quotes a string literal kept. */
    private static String unquotedParameter(final String value) {
        return value.length() >= 2 && value.startsWith("'") && value.endsWith("'")
            ? value.substring(1, value.length() - 1).replace("''", "'") : value;
    }

    /**
     * Refuse a submission that carries no statement at all — only whitespace, comments, semicolons or
     * nothing. Live answers every one of those with the same sentence ("SQL compilation error: Empty SQL
     * statement.", error 900 / SQLSTATE 42601) rather than a syntax error at the stray token, so the
     * check happens before parsing. Asked of the LEXER, never of the text: a semicolon inside a string
     * literal or a comment is not a statement separator.
     */
    private void rejectEmptyStatement(final String sql) {
        if (sql == null) {
            throw new SqlSyntaxException(SqlCompilationError.of("Empty SQL statement."),
                Collections.singletonList("Empty SQL statement."), "");
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        lexer.removeErrorListeners();
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        for (final Token token : tokens.getTokens()) {
            if (token.getChannel() == Token.DEFAULT_CHANNEL
                    && token.getType() != Token.EOF && token.getType() != FrostlakeLexer.SEMI) {
                return;   // a real statement is in there
            }
        }
        throw new SqlSyntaxException(SqlCompilationError.of("Empty SQL statement."),
            Collections.singletonList("Empty SQL statement."), sql);
    }

    /**
     * Whether this SQL has anything the write-ahead log must replay, read from the parse tree rather
     * than from the text. Uses the same cached parse the execution just did, so classifying costs a
     * map lookup; SQL that does not parse is reported durable and the caller logs it, which never
     * happens in practice because a statement that failed to parse also failed to execute.
     */
    /**
     * The instant the NEXT statement should call "now", set only by write-ahead-log replay so a
     * re-executed statement resolves CURRENT_TIMESTAMP to the value it was originally written with.
     * Consumed by the next {@link #execute}; null means read the wall clock, which is every normal call.
     */
    public void setNextStatementInstant(final Instant instant) {
        this.pendingStatementInstant = instant;
    }

    /**
     * The instant the most recent outermost statement pinned, for the log to record alongside its SQL.
     * Read after execution — at commit — so it is the remembered value, not a fresh clock reading.
     */
    public Instant currentStatementInstant() {
        return lastStatementInstant != null ? lastStatementInstant : StatementClock.instant();
    }

    public boolean isDurableStatement(final String sql) {
        try {
            return WalStatementKinds.isDurable(parseScript(sql, true));
        } catch (final RuntimeException unparseable) {
            return true;
        }
    }

    // Parse a SQL script to a parse tree, caching by text. The tree is immutable and re-walking it is
    // read-only, so reuse across executions (and concurrent sessions) is safe; only successfully-parsed
    // SQL is cached, so syntax errors still surface every call.
    private FrostlakeParser.SqlScriptContext parseScript(final String sql) {
        return parseScript(sql, false, !visitor.getProceduralExecutor().isExecutingBlock());
    }

    /** As {@link #parseScript(String)}; {@code quiet} demotes syntax-error logging for speculative parses. */
    private static FrostlakeParser.SqlScriptContext parseScript(final String sql, final boolean quiet) {
        return parseScript(sql, quiet, true);
    }

    /**
     * As {@link #parseScript(String, boolean)}; {@code scriptLevel} is false for a text a running block hands over
     * as one of its own statements, where a scripting statement is at home (see ScriptingStatementPlacement).
     */
    private static FrostlakeParser.SqlScriptContext parseScript(final String sql, final boolean quiet,
                                                                final boolean scriptLevel) {
        return parseScript(sql, quiet, scriptLevel, true, true);
    }

    /**
     * As {@link #parseScript(String, boolean, boolean)}; {@code placesStages} is false for a routine body, whose bare
     * stages are judged in the frame the body compiles in (see StageArgumentSyntax), and with {@code compiled} false
     * the text is read only as far as the account's parser reads it: what a statement of it earns while it compiles —
     * a value operator after a quantified comparison, an interval literal that does not read — is left to the run.
     * Either way the tree is not kept.
     */
    private static FrostlakeParser.SqlScriptContext parseScript(final String sql, final boolean quiet,
                                                                final boolean scriptLevel, final boolean placesStages,
                                                                final boolean compiled) {
        final FrostlakeParser.SqlScriptContext cached = SCRIPT_CACHE.get(sql);
        if (cached != null) {
            if (scriptLevel) {
                ScriptingStatementPlacement.requireSqlStatements(cached, sql);
            }
            return cached;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        SyntaxErrorListener errorListener = new SyntaxErrorListener(sql, quiet);
        errorListener.statementParse();
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        final FrostlakeParser parser = new FrostlakeParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);
        FrostlakeParser.SqlScriptContext tree = parser.sqlScript();
        if (errorListener.hasErrors()) {
            // Where a refused statement's fault lands must not depend on what this JVM parsed before
            // it, and with ANTLR's shared prediction cache it does. Parse it again with a cache of its
            // own and report THAT parse's faults; only a statement already on its way to a refusal
            // pays for the second parse.
            final FrostlakeLexer coldLexer = new FrostlakeLexer(CharStreams.fromString(sql));
            final SyntaxErrorListener coldListener = new SyntaxErrorListener(sql, quiet);
            coldListener.statementParse();
            coldLexer.removeErrorListeners();
            coldLexer.addErrorListener(coldListener);
            final CommonTokenStream coldTokens = new CommonTokenStream(coldLexer);
            final FrostlakeParser coldParser = new FrostlakeParser(coldTokens);
            ColdPrediction.arm(coldParser);
            coldParser.removeErrorListeners();
            coldParser.addErrorListener(coldListener);
            tree = coldParser.sqlScript();
            tokens = coldTokens;
            errorListener = coldListener;
        }
        if (scriptLevel) {
            ScriptingStatementPlacement.requireSqlStatementsBefore(tree, tokens, sql, errorListener);
            IntoListLookahead.requireBefore(tree, tokens, sql, errorListener);
        }
        StatementSeparation.requireSeparatorsBefore(tree, tokens, sql, errorListener);
        EmptySchemaPartSyntax.requireWellFormedBefore(tree, tokens, sql, errorListener);
        PostParseRefusals.requireBefore(tokens, sql, errorListener);
        SignatureTailLine.requireBefore(tree, sql, errorListener);
        errorListener.throwIfErrors();
        StatementSeparation.requireSeparators(tree, tokens, sql);
        EmptySchemaPartSyntax.requireWellFormed(tree, tokens, sql);
        FinishedPredicateSyntax.requireOpenOperands(tree, tokens, sql);
        PositionNeedleSyntax.requireValueNeedles(tree, tokens, sql);
        AnsiFromFormSyntax.requireCallForms(tree, tokens, sql);
        ValueFunctionSyntax.requireValueFunctionForms(tree, sql);
        if (placesStages) {
            // Where a bare stage stands is the text's syntax; which call takes it is its own statement's to judge.
            StageArgumentSyntax.requireSyntax(tree, sql);
        }
        if (!compiled) {
            return tree;
        }
        QuantifiedComparisonOperand.requireNoValueOperator(tree);
        IntervalLiteralSyntax.requireReadable(tree);
        NamedCallRewrite.apply(tree);
        if (!placesStages) {
            return tree;
        }
        SCRIPT_CACHE.put(sql, tree);
        return tree;
    }

    /**
     * The script a routine body parses to before any bare stage in it is judged, or null when it does not parse or
     * holds no stage to judge: the body's stages are judged in the frame the body compiles in (see
     * RoutineBodyCompiler).
     *
     * @param body the body's text
     * @return the parsed body, or null
     */
    public FrostlakeParser.SqlScriptContext routineBodyScript(final String body) {
        if (body == null || body.trim().isEmpty() || body.indexOf('@') < 0) {
            return null;
        }
        try {
            return parseScript(body, true, true, false, false);
        } catch (final RuntimeException doesNotParse) {
            return null;
        }
    }

    /**
     * Refuse a bare stage the statement passes to a call that does not take it (see StageArgumentSyntax). A statement
     * of a request carrying several is compiled on its own, so its place then counts from its own first token.
     *
     * @param statement the statement
     * @param sql       the request's text
     * @param ownText   whether the request carries other statements
     */
    private static void requireStageArguments(final FrostlakeParser.StatementContext statement, final String sql,
                                              final boolean ownText) {
        if (sql.indexOf('@') < 0) {
            return;
        }
        if (!ownText) {
            StageArgumentSyntax.requireArguments(statement);
            return;
        }
        final SourcePosition displaced = LeadingCommentOffset.begin(new SourcePosition(
            statement.getStart().getLine(), statement.getStart().getCharPositionInLine()));
        try {
            StageArgumentSyntax.requireArguments(statement);
        } finally {
            LeadingCommentOffset.end(displaced);
        }
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
     * The query {@code sql} parses to, read from the cache statements use, or null when it is not one query.
     * A subquery's text is a statement of its own, so its shape can be read without running it.
     */
    public FrostlakeParser.SelectStatementContext subqueryStatement(final String sql) {
        final FrostlakeParser.SqlScriptContext tree;
        try {
            tree = parseScript(sql, true);
        } catch (final RuntimeException unparseable) {
            return null;
        }
        if (tree.flowChain().isEmpty() || tree.flowChain().get(0).statement().isEmpty()) {
            return null;
        }
        final FrostlakeParser.QueryStatementContext query =
            tree.flowChain().get(0).statement().get(0).queryStatement();
        return query == null ? null : query.selectStatement();
    }

    /**
     * The rows a catalog table's storage holds right now, by its storage key, or -1 when there is none: a
     * metadata question, as live's planner asks it before any row is read.
     */
    public long storedRowCount(final String storageKey) {
        if (storageKey == null || !storageEngine.hasTable(storageKey)) {
            return -1;
        }
        return storageEngine.getTableStorage(storageKey).getRowCount();
    }

    /** {@link #storedRowCount} for a table named as a query writes it, or -1 when the name reaches no storage. */
    public long storedRowCountOf(final String writtenName) {
        final String key;
        try {
            key = getFullyQualifiedTableName(writtenName);
        } catch (final RuntimeException unresolvable) {
            return -1;
        }
        return storedRowCount(key);
    }

    /**
     * True when {@code sql} parses as a single Snowflake-Scripting block — {@code BEGIN … END} or
     * {@code DECLARE … BEGIN … END}. Sibling of {@link #isQueryStatement(String)}: the decision is made on the
     * parse tree, never on a string prefix, so the transaction statement {@code BEGIN;} (a different grammar
     * rule that also starts with BEGIN) is correctly not a block, while a lower-cased or multi-line block is.
     *
     * <p>Used to compile a {@code LANGUAGE SQL} stored-procedure body at CREATE time: Snowflake requires such a
     * body to be a scripting block and rejects a bare statement outright.
     */
    public boolean isProceduralBlock(final String sql) {
        return proceduralBlockOf(sql) != null;
    }

    /**
     * Throw the syntax error of a body that OPENS a scripting block — its first token is {@code BEGIN}
     * or {@code DECLARE} — but does not parse as one; a body that opens no block, or parses as
     * something else, returns quietly.
     *
     * <p>The CREATE-time body checks fail OPEN on a body this grammar cannot read (see the note on
     * {@link #parsesAsScript(String)}), which is right there — the body may use a construct the engine
     * does not model. At CALL time the body has to RUN, so its real parse error is the honest answer;
     * without this the caller falls through to the expression evaluator and reports whatever opaque
     * failure that produces.
     */
    public void reportBlockSyntaxError(final String sql) {
        if (opensBlock(sql) && proceduralBlockOf(sql) == null) {
            parseScript(sql, false);
        }
    }

    /**
     * Throw the syntax error of a block-opening body that RUNS OUT before its block closes — the parse
     * error sits at end of input, so the engine has read every token and positively knows the block
     * never ended, which a real account also refuses at CREATE. Any other parse failure returns
     * quietly: this grammar is a SUBSET of Snowflake's, so a mid-body error may be a construct the
     * engine does not model, and the CREATE-time body checks fail OPEN on those (see the note on
     * {@link #parsesAsScript(String)}).
     */
    public void reportUnterminatedBlock(final String sql) {
        if (!opensBlock(sql) || proceduralBlockOf(sql) != null) {
            return;
        }
        try {
            parseScript(sql, true);
        } catch (final RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("'<EOF>'")) {
                throw e;
            }
        }
    }

    /** Whether the first real token of {@code sql} opens a scripting block — asked of the LEXER, not the text. */
    private boolean opensBlock(final String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return false;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        lexer.removeErrorListeners();
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        for (final Token token : tokens.getTokens()) {
            if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() != Token.EOF) {
                return token.getType() == FrostlakeLexer.BEGIN || token.getType() == FrostlakeLexer.DECLARE;
            }
        }
        return false;
    }

    /**
     * The parsed script {@code sql} forms when it is a single query statement, or null when it is not one.
     * The parse-tree half of {@link #isQueryStatement(String)}, for the CREATE-time SQL-UDF body check;
     * unlike that method this parses afresh rather than consulting the hot-path cache, because it is only
     * ever reached once per CREATE.
     */
    public FrostlakeParser.SqlScriptContext queryStatementOf(final String sql) {
        if (sql == null) {
            return null;
        }
        try {
            final FrostlakeParser.SqlScriptContext tree = parseScript(sql, true);
            final boolean isQuery = !tree.flowChain().isEmpty()
                && tree.flowChain().get(0).statement().get(0).queryStatement() != null;
            return isQuery ? tree : null;
        } catch (final RuntimeException notAStatement) {
            return null;
        }
    }

    /**
     * {@code sql} parsed as a script, or null when it does not parse.
     *
     * @param sql the text
     * @return the parse tree, or null
     */
    public FrostlakeParser.SqlScriptContext scriptOf(final String sql) {
        if (sql == null) {
            return null;
        }
        try {
            return parseScript(sql, true);
        } catch (final RuntimeException notAScript) {
            return null;
        }
    }

    /**
     * How many statements {@code sql} holds the way a request counts them — a ->> chain is one — once it
     * parses: a text that will not parse throws its syntax error instead. What a statement of it earns while it
     * compiles comes after the count (live-verified), when it runs.
     */
    public int requestStatementCountOf(final String sql) {
        return parseScript(sql, true, true, true, false).flowChain().size();
    }

    /**
     * How many statements {@code sql} parses into — every stage of every flow chain — or 0 when it does
     * not parse. The CREATE-time SQL-UDF body check refuses a body of more than one.
     */
    public int statementCountOf(final String sql) {
        if (sql == null) {
            return 0;
        }
        try {
            int count = 0;
            for (final FrostlakeParser.FlowChainContext chain : parseScript(sql, true).flowChain()) {
                count += chain.statement().size();
            }
            return count;
        } catch (final RuntimeException doesNotParse) {
            return 0;
        }
    }

    /**
     * The {@code BEGIN … END} block {@code sql} consists of, or null when it is not exactly one block.
     * The parse-tree half of {@link #isProceduralBlock(String)}, for callers that must INSPECT the block
     * rather than merely recognise it (the CREATE-time SQL-UDF body check reads which statements and
     * expressions it contains).
     */
    /**
     * The syntax error {@code sql} earns when the FIRST thing wrong with it is a statement inside a block
     * that lacks its semicolon, or null for any other text. The engine read that statement whole, so the
     * fault is positively known rather than a construct the grammar lacks, and a CREATE PROCEDURE refuses
     * it as the account does.
     */
    public String missingTerminatorRefusal(final String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return null;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        final SyntaxErrorListener errorListener = new SyntaxErrorListener(sql, true);
        errorListener.statementParse();
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);
        parser.sqlScript();
        if (!errorListener.firstErrorIsMissingTerminator()) {
            return null;
        }
        try {
            errorListener.throwIfErrors();
        } catch (final SqlSyntaxException refused) {
            return refused.getMessage();
        }
        return null;
    }

    public FrostlakeParser.BeginEndBlockContext proceduralBlockOf(final String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return null;
        }
        try {
            final FrostlakeParser.SqlScriptContext tree = parseScript(sql, true);
            if (tree.flowChain().size() != 1) {
                return null;
            }
            final List<FrostlakeParser.StatementContext> statements = tree.flowChain().get(0).statement();
            if (statements.isEmpty()) {
                return null;
            }
            // A DECLARE is part of the block itself (beginEndBlock carries declareSection), so the
            // whole thing is a single statement: anything before it means this is not one block.
            if (statements.size() != 1) {
                return null;
            }
            final FrostlakeParser.ProceduralStatementContext proceduralCtx = statements.get(0).proceduralStatement();
            return proceduralCtx != null ? proceduralCtx.beginEndBlock() : null;
        } catch (final RuntimeException e) {
            return null;   // does not even parse → certainly not a block
        }
    }

    /**
     * Whether {@code sql} parses at all under the engine's grammar. Frostlake's grammar is a SUBSET of
     * Snowflake's, so a parse FAILURE means "this engine does not recognise some construct" — never
     * "Snowflake would reject it". The CREATE-time routine-body checks use this to fail OPEN: they only
     * reject a body whose shape they can positively read and know to be wrong.
     */
    public boolean parsesAsScript(final String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return false;
        }
        try {
            parseScript(sql, true);
            return true;
        } catch (final RuntimeException doesNotParse) {
            return false;
        }
    }

    /**
     * Throw the syntax error {@code sql} earns, exactly as running it would throw it before any of its
     * statements runs, or return quietly when it parses. Nothing runs and no session is involved. The
     * account compiles a text before it counts its statements, so a client's statement-count gate asks this
     * first: a text that will not parse is refused for its syntax whatever its count. The parse is the one running
     * the text would make, not a speculative one, so it names the fault running it would name.
     */
    public static void requireParses(final String sql) {
        parseScript(sql, false);
    }

    /**
     * Whether {@code sql} is exactly ONE plain (non-procedural) SQL statement — a bare
     * {@code SELECT …} / {@code UPDATE …} and nothing else. This is the one routine-body shape the
     * engine can positively call wrong: Snowflake requires a LANGUAGE SQL procedure body to be a
     * scripting block and rejects a bare statement outright. A body that does not parse, or parses
     * as several statements, or contains any procedural construct, is NOT this shape.
     */
    public boolean isSinglePlainStatement(final String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return false;
        }
        try {
            final FrostlakeParser.SqlScriptContext tree = parseScript(sql, true);
            if (tree.flowChain().size() != 1) {
                return false;
            }
            final List<FrostlakeParser.StatementContext> statements = tree.flowChain().get(0).statement();
            return statements.size() == 1 && statements.get(0).proceduralStatement() == null;
        } catch (final RuntimeException doesNotParse) {
            return false;
        }
    }

    /**
     * Execute SQL with lateral context support (for correlated subqueries)
     */
    /** Depth of correlated / lateral re-execution — non-zero while a subquery runs with an OUTER
     *  context, whose names a plan-time scope walk cannot see (they resolve per row). */
    private int lateralExecutionDepth;

    /** Whether a correlated / lateral re-execution is on the stack. */
    public boolean isInLateralExecution() {
        return lateralExecutionDepth > 0;
    }

    public List<ResultSet> executeWithLateralContext(final String sql, final Map<String, Object> lateralContext) {
        if (lateralContext == null) {
            return executeWithLateralContextInner(sql, null);
        }
        // Any other lateral run inside a subquery's compilation runs as it runs anywhere else.
        final boolean suspends = SubqueryCompilation.outerNames() != null && !SubqueryCompilation.compiles(lateralContext);
        final Map<String, Object> compiling = suspends ? SubqueryCompilation.begin(null) : null;
        lateralExecutionDepth++;
        try {
            return executeWithLateralContextInner(sql, lateralContext);
        } finally {
            lateralExecutionDepth--;
            if (suspends) {
                SubqueryCompilation.end(compiling);
            }
        }
    }

    private List<ResultSet> executeWithLateralContextInner(final String sql, final Map<String, Object> lateralContext) {
        final List<ResultSet> results = new ArrayList<>();
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
            if (securityManager != null) {
                queryHistory.setSessionId(securityManager.getSessionContext().getSessionNumber());
            }
        }

        try {
            // Parse once and cache (correlated subqueries re-enter with identical SQL per outer row).
            final FrostlakeParser.SqlScriptContext tree = parseScript(sql);

            // Classify the recorded kind from the PARSED statement, not the raw text: a leading
            // comment or a parenthesized set operation reads as its real kind. The constructor's
            // text sniff stands only when the parse above refused the statement.
            if (queryHistory != null && !tree.flowChain().isEmpty()
                    && !tree.flowChain().get(0).statement().isEmpty()) {
                queryHistory.setQueryType(statementKindOf(tree.flowChain().get(0).statement().get(0)));
            }

            // A request of several statements runs each as a statement of its own: the owner of the
            // transaction commits one as it completes and undoes only the one that fails.
            final StatementBoundaries boundaries = lateralContext == null && executeReentryDepth == 1
                && tree.flowChain().size() > 1 ? statementBoundaries.get() : null;

            // Visit each flow chain (a chain is usually a single statement; with the ->> flow
            // operator it is several, executed in order — each stage may read a prior stage's
            // result via $n, and only the LAST stage's result is the chain's result).
            for (final FrostlakeParser.FlowChainContext chainCtx : tree.flowChain()) {
                final List<FrostlakeParser.StatementContext> stages = chainCtx.statement();
                // The statement actually run, kept for the status row built after the try below.
                FrostlakeParser.StatementContext executedStatement = null;
                final List<ResultSet> priorFlowResults = flowChainResults;
                final Object result;
                // The statement a failure names: the stage being run, so a flow chain names its own.
                FrostlakeParser.StatementContext failingStage = stages.get(stages.size() - 1);
                if (boundaries != null) {
                    boundaries.begin();
                }
                try {
                    // A LIMIT or OFFSET string anywhere in the chain is its syntax error, before any stage runs,
                    // and so is an INTO clause no block's SELECT … INTO carries (see IntoClausePlacement).
                    if (lateralContext == null) {
                        for (final FrostlakeParser.StatementContext judged : stages) {
                            failingStage = judged;
                            LimitValueSyntax.requireNumericValues(judged, sql);
                            IntoClausePlacement.rejectInStatement(judged,
                                visitor.getProceduralExecutor().isRunningBlockSelectInto());
                        }
                        // A bare stage a call does not take is refused as its statement compiles, ahead of every
                        // name the statement holds — after the statements before it in the request have run.
                        for (final FrostlakeParser.StatementContext judged : stages) {
                            failingStage = judged;
                            requireStageArguments(judged, sql, tree.flowChain().size() > 1);
                        }
                    }
                    if (stages.size() > 1) {
                        flowChainResults = new ArrayList<>();
                        for (int stage = 0; stage < stages.size() - 1; stage++) {
                            failingStage = stages.get(stage);
                            final Object stageResult = visitor.visit(stages.get(stage));
                            flowChainResults.add(stageResult instanceof ResultSet ? (ResultSet) stageResult : null);
                            // Time-travel snapshots per stage, mirroring the per-statement behavior below.
                            if (lateralContext == null) {
                                storageEngine.snapshotDirtyTables();
                            }
                        }
                    }
                    final FrostlakeParser.StatementContext stmtCtx = stages.get(stages.size() - 1);
                    executedStatement = stmtCtx;
                    failingStage = stmtCtx;
                    // The conditional-DDL branch is per STATEMENT: whatever an earlier statement's
                    // handler reported must not leak into this one's status sentence.
                    ConditionalDdlOutcome.clear();

                    // If we have lateral context and this is a query statement (SELECT), pass it along.
                    // (The stream-consuming DML window and DDL's implicit pre-commit both live in the
                    // visitor's visitDmlStatement/visitDdlStatement, so procedural bodies that dispatch
                    // through the visitor directly are covered too.)
                    if (lateralContext != null && stmtCtx.queryStatement() != null) {
                        final FrostlakeParser.QueryStatementContext queryCtx = stmtCtx.queryStatement();
                        if (queryCtx.selectStatement() != null) {
                            result = executeSelectFromContext(queryCtx.selectStatement(), lateralContext);
                        } else {
                            result = visitor.visit(stmtCtx);
                        }
                    } else {
                        result = visitor.visit(stmtCtx);
                    }
                    if (result instanceof ResultSet && !RelationBody.isActive()) {
                        // Handing the rows out reads every cell, so a fault a relation deferred raises here.
                        DeferredFault.requireNone(((ResultSet) result).getRows());
                    }
                } catch (final RuntimeException statementFailure) {
                    if (boundaries != null) {
                        boundaries.fail();
                    }
                    // A statement that fails inside a script of several is NAMED by the answer, the way
                    // the account names it; one sent on its own answers with its own error unadorned.
                    throw MultiStatementFailure.wrapping(tree, failingStage, statementFailure,
                        lateralContext == null);
                } finally {
                    flowChainResults = priorFlowResults;
                }
                // An anonymous block run as a statement answers one row even when it finished without a
                // RETURN: NULL, in the block's result column.
                final Object answer = result == null && lateralContext == null
                    && AnonymousBlockResult.isBlock(executedStatement)
                    ? AnonymousBlockResult.withoutReturn() : result;

                final int resultsBefore = results.size();
                if (!(answer instanceof ResultSet) && lateralContext == null
                        && !isProceduralControlFlow(tree)) {
                    results.add(statusAnswer(executedStatement));
                }
                // A request that is one EXECUTE IMMEDIATE of several statements hands its client each statement's
                // answer, in order, while RESULT_SCAN reads its own (see MultiStatementAnswer). As one statement of a
                // request of several, held in a RESULTSET or returned by a block, the answer is its own row alone
                // (live-verified).
                final boolean childAnswers = answer instanceof MultiStatementAnswer && lateralContext == null
                    && executeReentryDepth == 1 && tree.flowChain().size() == 1 && stages.size() == 1
                    && executedStatement.proceduralStatement() != null
                    && executedStatement.proceduralStatement().executeImmediateStatement() != null;
                if (answer instanceof ResultSet) {
                    final ResultSet resultSet = (ResultSet) answer;
                    // Cache the result for RESULT_SCAN (only for non-correlated queries)
                    if (lateralContext == null) {
                        final String queryId = resultCache.cacheResult(sql, resultSet);
                        if (queryHistory != null) {
                            queryHistory.setQueryId(queryId);
                        }
                        hasResultSet = true;
                    }
                    if (childAnswers) {
                        results.addAll(((MultiStatementAnswer) resultSet).getStatementAnswers());
                    } else {
                        results.add(resultSet);
                    }
                }
                if (lateralContext == null && results.size() > resultsBefore && !childAnswers) {
                    // What a JDBC client makes of the statement: rows to read, or an update count. Only the
                    // statement knows — a status row and a query naming its column "status" look alike.
                    final ResultSet answered = results.get(results.size() - 1);
                    answered.markJdbcUpdateCount(JdbcUpdateCounts.of(
                        visitor.describedStatement(executedStatement), answered));
                }
                // transactionStatement is handled by visitTransactionStatement in the visitor above

                // One time-travel snapshot per statement for any table it mutated (was per row → O(N^2)).
                if (lateralContext == null) {
                    storageEngine.snapshotDirtyTables();
                }
                if (boundaries != null) {
                    boundaries.complete(visitor.getOriginalText(chainCtx));
                }
            }

            // Generate a query ID for non-result-producing queries (only for non-correlated queries).
            // Skip purely procedural control-flow statements (LET, FOR, DECLARE, OPEN, FETCH, CLOSE)
            // so they don't displace SHOW/SELECT results from LAST_QUERY_ID().
            if (!hasResultSet && lateralContext == null && !isProceduralControlFlow(tree)) {
                final String queryId = resultCache.generateQueryId(sql);
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
                queryHistory.markSuccess(LocalDateTime.now(ZoneOffset.UTC), rowsProduced);
                // A statement's changes are visible once it finishes — or, inside an explicit transaction,
                // once that transaction commits, which is when its commit dates it.
                final Transaction runningIn = transactionManager.getCurrentTransaction();
                if (runningIn != null) {
                    runningIn.addStatement(queryHistory);
                } else {
                    queryHistory.setVisibleFrom(queryHistory.getEndTime());
                    queryHistory.markVisibleSequence();
                }
                queryHistoryTracker.addQuery(queryHistory);
            }

            return results;

        } catch (final SqlSyntaxException e) {
            // Mark query as failed and add to history
            if (queryHistory != null) {
                // Generate query ID for failed query
                final String queryId = resultCache.generateFailedQueryId(sql);
                queryHistory.setQueryId(queryId);
                queryHistory.markFailed(LocalDateTime.now(ZoneOffset.UTC), e.getMessage());
                queryHistoryTracker.addQuery(queryHistory);
            }
            // Re-throw syntax exceptions without wrapping (already logged)
            throw e;
        } catch (final SecurityException e) {
            // Mark query as failed and add to history
            if (queryHistory != null) {
                // Generate query ID for failed query
                final String queryId = resultCache.generateFailedQueryId(sql);
                queryHistory.setQueryId(queryId);
                queryHistory.markFailed(LocalDateTime.now(ZoneOffset.UTC), e.getMessage());
                queryHistoryTracker.addQuery(queryHistory);
            }
            // Re-throw security exceptions without wrapping
            throw e;
        } catch (final ProceduralException e) {
            // Mark query as failed and add to history
            if (queryHistory != null) {
                // Generate query ID for failed query
                final String queryId = resultCache.generateFailedQueryId(sql);
                queryHistory.setQueryId(queryId);
                queryHistory.markFailed(LocalDateTime.now(ZoneOffset.UTC), e.getMessage());
                queryHistoryTracker.addQuery(queryHistory);
            }
            // Re-throw procedural exceptions without wrapping
            throw e;
        } catch (final Exception e) {
            // Mark query as failed and add to history
            if (queryHistory != null) {
                // Generate query ID for failed query
                final String queryId = resultCache.generateFailedQueryId(sql);
                queryHistory.setQueryId(queryId);
                queryHistory.markFailed(LocalDateTime.now(ZoneOffset.UTC), e.getMessage());
                queryHistoryTracker.addQuery(queryHistory);
            }
            logger.error("Error executing SQL: {}", sql, e);
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
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
        columns.add(new ResultSetColumn(columnName, StatementResultWidths.DML_COUNT));
        final List<Object> values = new ArrayList<>();
        values.add(count);
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(values));
        return new ResultSet(columns, rows).markUpdateCount(count);
    }

    /**
     * The Snowflake UPDATE result shape: TWO columns — "number of rows updated" and "number of
     * multi-joined rows updated". Consumers classify DML results by column count (the vendor stats
     * collector matches UPDATE only as a two-column result), so the single-column form silently
     * dropped every UPDATE from its statistics. The engine does not track multi-join duplicates;
     * that column is always 0.
     */
    ResultSet updateCountResult(final long updated) {
        return updateCountResult(updated, 0L);
    }

    /**
     * The UPDATE result with its multi-joined count: the target rows that more than one source row joined,
     * each counted once. A driver that sums the grid (Snowflake's does) reports their sum.
     *
     * @param updated     the rows updated
     * @param multiJoined the updated rows more than one source row joined
     * @return the two-column grid
     */
    ResultSet updateCountResult(final long updated, final long multiJoined) {
        final List<ResultSetColumn> columns = new ArrayList<>();
        columns.add(new ResultSetColumn("number of rows updated", StatementResultWidths.DML_COUNT));
        columns.add(new ResultSetColumn("number of multi-joined rows updated", StatementResultWidths.DML_COUNT));
        final List<Object> values = new ArrayList<>();
        values.add(updated);
        values.add(multiJoined);
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(values));
        return new ResultSet(columns, rows).markUpdateCount(updated);
    }

    /** Resolve a per-column INSERT value: the provided value, else auto-increment, else the column DEFAULT, else null. */
    /**
     * What a bare DML DEFAULT writes into column {@code colIndex}: the column's declared default, its
     * AUTOINCREMENT value where it has one, and NULL when it has neither — measured live, where an
     * UPDATE of a defaultless column to DEFAULT writes NULL and a NOT NULL column refuses it.
     *
     * @param table              the target table
     * @param colIndex           the column's position
     * @param fullyQualifiedName the table's storage key, for the autoincrement counter
     * @return the value to write
     */
    Object declaredDefaultFor(final Table table, final int colIndex, final String fullyQualifiedName) {
        return insertColumnValue(table.columnsView().get(colIndex), fullyQualifiedName, null);
    }

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
        // A projection policy restricts the select list the USER sees; an inner select may project a
        // restricted column freely as long as the outermost one does not (live-verified), so the
        // statement's own select is remembered here and nowhere else.
        final FrostlakeParser.SelectStatementContext outerSelect = outermostSelect;
        outermostSelect = ctx;
        LateConstantRefusal.begin();
        try {
            final ResultSet selected = executeSelectFromContext(ctx, null);
            // The refusal live raises last, once the whole statement has been judged.
            LateConstantRefusal.raise();
            return selected;
        } catch (final RuntimeException refused) {
            throw sourceFaultAhead(ctx, LateConstantRefusal.ahead(refused));
        } finally {
            LateConstantRefusal.end();
            outermostSelect = outerSelect;
        }
    }

    /**
     * {@link #executeSelectFromContext(FrostlakeParser.SelectStatementContext)} as a plan: the statement is
     * the outermost select while it is planned and, again, while it runs, and a refusal from either is
     * answered as the statement's sources rank it.
     *
     * @param ctx the statement
     * @return the planned statement
     */
    PlannedQuery planSelectStatement(final FrostlakeParser.SelectStatementContext ctx) {
        final FrostlakeParser.SelectStatementContext outerSelect = outermostSelect;
        outermostSelect = ctx;
        try {
            final PlannedQuery planned = planSelectFromContext(ctx, null, null);
            return new PlannedQuery(planned.shape, planned.description) {
                @Override
                ResultSet run() {
                    final FrostlakeParser.SelectStatementContext outer = outermostSelect;
                    outermostSelect = ctx;
                    try {
                        return planned.run();
                    } catch (final RuntimeException refused) {
                        throw sourceFaultAhead(ctx, refused);
                    } finally {
                        outermostSelect = outer;
                    }
                }
            };
        } catch (final RuntimeException refused) {
            throw sourceFaultAhead(ctx, refused);
        } finally {
            outermostSelect = outerSelect;
        }
    }

    /**
     * The refusal a failed query answers with. A missing relation or a duplicate alias anywhere in the
     * statement outranks every other compilation fault but a window frame's (see StatementSourceCheck), while
     * this executor meets them scope by scope as it runs, so once the statement has been refused its sources
     * are checked in live's order, and a fault found there is the answer.
     */
    private RuntimeException sourceFaultAhead(final FrostlakeParser.SelectStatementContext statement,
                                              final RuntimeException refused) {
        if (!SqlCompilationError.isCompilationError(refused.getMessage())) {
            return refused;
        }
        try {
            windowEvaluator.rejectFrameWithoutOrderBy(statement);
            windowEvaluator.rejectUnsupportedFrameForWholePartitionAggregate(statement);
            new StatementSourceCheck(this, statement).check(statement);
        } catch (final RuntimeException ahead) {
            if (SqlCompilationError.isCompilationError(ahead.getMessage())) {
                return ahead;
            }
        }
        return refused;
    }

    /** The SELECT whose projection list is the statement's result — see the entry point above. */
    private FrostlakeParser.SelectStatementContext outermostSelect;

    /**
     * Execute SELECT with pre-computed CTE results (for use in DML statements)
     */
    ResultSet executeSelectFromContextWithCTEs(final FrostlakeParser.SelectStatementContext ctx,
                                                       final Map<String, Object> lateralContext,
                                                       final Map<String, ResultSet> cteResults) {
        return planSelectFromContext(ctx, lateralContext, cteResults).run();
    }

    /**
     * Plan a SELECT statement — its WITH clause's CTEs computed, its set operation or single select planned
     * under those CTEs — into a query that runs, under the same CTE scope and lateral depth, when asked.
     *
     * @param ctx            the statement
     * @param lateralContext the enclosing row's values for a correlated evaluation, or null
     * @param cteResults     CTEs already computed by the statement around this one, or null
     * @return the planned query
     */
    PlannedQuery planSelectFromContext(final FrostlakeParser.SelectStatementContext ctx,
                                       final Map<String, Object> lateralContext,
                                       final Map<String, ResultSet> cteResults) {
        if (lateralContext == null) {
            return planSelectFromContextInner(ctx, null, cteResults);
        }
        lateralExecutionDepth++;
        try {
            return planSelectFromContextInner(ctx, lateralContext, cteResults);
        } finally {
            lateralExecutionDepth--;
        }
    }

    private PlannedQuery planSelectFromContextInner(final FrostlakeParser.SelectStatementContext ctx,
                                                    final Map<String, Object> lateralContext,
                                                    final Map<String, ResultSet> cteResults) {
        try {
            // If CTEs are already computed (from parent DML statement), use them
            // Otherwise, compute them from the SELECT statement's WITH clause
            Map<String, ResultSet> allCTEs = cteResults;
            if (ctx.withClause() != null) {
                final Map<String, ResultSet> selectCTEs = executeCTEs(ctx.withClause(), lateralContext, cteResults);
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
            final PlannedQuery planned;
            try {
                if (ctx.selectOperand().size() > 1) {
                    // Handle set operators (UNION, INTERSECT, EXCEPT)
                    planned = planSetOperations(ctx, lateralContext, allCTEs);
                } else {
                    // Single SELECT clause - plan normally
                    final FrostlakeParser.SelectOperandContext singleOp = ctx.selectOperand().get(0);
                    planned = singleOp.selectStatement() != null
                        ? planSelectFromContext(singleOp.selectStatement(), lateralContext, null)
                        : planSingleSelectScoped(ctx, singleOp.selectClause(), lateralContext, allCTEs);
                }
            } finally {
                currentCteContext = savedCteContext;
            }
            final Map<String, ResultSet> ctes = allCTEs;
            return new PlannedQuery(planned.shape, planned.description) {
                @Override
                ResultSet run() {
                    if (lateralContext != null) {
                        lateralExecutionDepth++;
                    }
                    try {
                        final Map<String, ResultSet> saved = currentCteContext;
                        if (ctes != null) {
                            currentCteContext = ctes;
                        }
                        try {
                            return planned.run();
                        } finally {
                            currentCteContext = saved;
                        }
                    } catch (final SecurityException e) {
                        // Re-throw security exceptions as-is
                        throw e;
                    } catch (final Exception e) {
                        logger.error("Error executing SQL: {}", getOriginalText(ctx), e);
                        if (e instanceof ProceduralException) throw (ProceduralException) e;
                        throw StatementErrors.propagate(e);
                    } finally {
                        if (lateralContext != null) {
                            lateralExecutionDepth--;
                        }
                    }
                }
            };
        } catch (final SecurityException e) {
            // Re-throw security exceptions as-is
            throw e;
        } catch (final Exception e) {
            // getOriginalText, not getText: ANTLR getText concatenates tokens without whitespace,
            // which rendered every logged statement as an unreadable single word.
            logger.error("Error executing SQL: {}", getOriginalText(ctx), e);
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        }
    }

    /**
     * Execute SELECT from parsed context with LATERAL context support
     */
    private ResultSet executeSelectFromContext(final FrostlakeParser.SelectStatementContext ctx, final Map<String, Object> lateralContext) {
        return planSelectFromContext(ctx, lateralContext, null).run();
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
     * "Table does not exist". An inner definition of a name the OUTER scope already defines is
     * IGNORED — the outer CTE wins (live-verified: {@code WITH t AS (SELECT 'outer'), u AS (WITH t
     * AS (SELECT 'inner') SELECT * FROM t) SELECT * FROM u} yields 'outer' on Snowflake).
     */
    Map<String, ResultSet> executeCTEs(final FrostlakeParser.WithClauseContext withCtx,
                                       final Map<String, Object> lateralContext,
                                       final Map<String, ResultSet> outerCtes) {
        final Map<String, ResultSet> cteResults = outerCtes == null ? new HashMap<>() : new HashMap<>(outerCtes);

        for (final FrostlakeParser.CteDefinitionContext cteCtx : withCtx.cteDefinition()) {
            final String cteName = cteCtx.nameStartPart().getText().toUpperCase();
            if (outerCtes != null && outerCtes.containsKey(cteName)) {
                // The outer CTE of the same name wins; the inner definition is never executed.
                continue;
            }
            ResultSet cteResult;

            // Snowflake's RECURSIVE keyword is OPTIONAL: a CTE that references its own name as a
            // table source recurses with or without it, so detection is by the parse tree alone.
            final boolean recursive = isRecursiveCte(cteCtx, cteName);
            if (recursive) {
                // Column aliases are applied inside executeRecursiveCte (to the anchor)
                // so the recursive step can reference named columns
                cteResult = executeRecursiveCte(cteCtx, cteName, lateralContext, cteResults);
            } else {
                final String prunedBody = prunedBody(cteCtx.selectStatement(), cteCtx,
                    cteColumnNames(cteCtx), lateralContext, cteResults);
                // The body's items wait for the reading statement (see RelationBody).
                final boolean outerBody = RelationBody.begin();
                try {
                    cteResult = executeSelectFromContextWithCTEs(prunedBody != null
                        ? parseSelectStatement(prunedBody) : cteCtx.selectStatement(), lateralContext, cteResults);
                } finally {
                    RelationBody.end(outerBody);
                }
                // Apply column aliases for non-recursive CTEs: WITH cte(a, b) AS (...)
                if (cteCtx.columnListOptional() != null) {
                    cteResult = renameColumns(cteResult, cteCtx.columnListOptional());
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
            if (source.tableQualifiedName() != null) {
                final String[] parts = ParseTreeText.qualifiedNameParts(source.tableQualifiedName());
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
     * A recursive CTE as a plan: the anchor's rows are the source, and one stage runs the recursion — each step
     * a planned SELECT over the PREVIOUS step's rows, its rows unioned into the result until a step answers
     * nothing (or the iteration guard trips). A definition with no UNION is an ordinary CTE.
     */
    private ResultSet executeRecursiveCte(final FrostlakeParser.CteDefinitionContext cteCtx,
                                           final String cteName,
                                           final Map<String, Object> lateralContext,
                                           final Map<String, ResultSet> outerCteResults) {
        final FrostlakeParser.SelectStatementContext stmtCtx = cteCtx.selectStatement();
        final List<FrostlakeParser.SelectClauseContext> clauses = getSelectClauses(stmtCtx);
        final List<FrostlakeParser.SetOperatorContext> operators = stmtCtx.setOperator();
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
        final int anchorEnd = splitIdx;
        // Build a merged CTE context that starts with outer CTEs
        final Map<String, ResultSet> workingCtes = new HashMap<>(outerCteResults);
        // Execute anchor (all SELECT clauses before the first UNION)
        ResultSet anchorResult = executeSingleSelect(stmtCtx, clauses.get(anchorEnd), lateralContext, workingCtes);
        // Apply CTE column aliases to anchor so the recursive step can reference named columns
        if (cteCtx.columnListOptional() != null) {
            anchorResult = renameColumns(anchorResult, cteCtx.columnListOptional());
        }
        final ResultSet anchor = anchorResult;
        // Iterate: each step receives only the PREVIOUS step's output (not all accumulated rows)
        final boolean hasAll = anchorEnd < operators.size() && operators.get(anchorEnd).ALL() != null;
        // Each recursive term's columns as its first step declares them, which the result's types fold with.
        final List<List<ResultSetColumn>> termColumns = new ArrayList<>();
        final SelectPlan plan = new SelectPlan(this, functionRegistry);
        plan.source(new ArrayList<>(anchor.getRows()), "ANCHOR[" + cteName + "]");
        plan.add(new StageOperator("RECURSIVE UNION" + (hasAll ? " ALL" : "") + "[" + cteName + "]") {
            @Override
            protected List<Row> apply(final List<Row> anchorRows) {
                List<Row> accumulated = new ArrayList<>(anchorRows);
                final int maxIterations = 1000; // guard against infinite recursion
                ResultSet lastStep = anchor;
                for (int iter = 0; iter < maxIterations; iter++) {
                    // The recursive reference gets only the rows from the previous step
                    workingCtes.put(cteName, lastStep);
                    final List<Row> stepRows = new ArrayList<>();
                    for (int i = anchorEnd + 1; i < clauses.size(); i++) {
                        final FrostlakeParser.SelectClauseContext stepClause = clauses.get(i);
                        // executeSingleSelect projects each set-operation operand itself, so the step's SELECT
                        // list is already applied here — re-projecting it a second time (as this code used to,
                        // to compensate for the old skip-projection-in-UNION behavior) corrupts the recursion.
                        final ResultSet stepResult =
                            executeSingleSelect(stmtCtx, stepClause, lateralContext, workingCtes);
                        if (iter == 0) {
                            termColumns.add(stepResult.getColumns());
                        }
                        stepRows.addAll(stepResult.getRows());
                    }
                    if (stepRows.isEmpty()) {
                        break; // fixpoint reached
                    }
                    if (hasAll) {
                        accumulated.addAll(stepRows);
                    } else {
                        accumulated = SetOperations.applyUnion(accumulated, stepRows, false);
                    }
                    lastStep = new ResultSet(anchor.getColumns(), stepRows);
                }
                return accumulated;
            }
        });
        recordPlan(plan);
        final List<Row> rows = plan.execute();
        return new ResultSet(recursiveCteColumns(anchor.getColumns(), termColumns), rows);
    }

    /**
     * A recursive CTE's columns: the anchor's, each temporal one at the widest fractional-second precision
     * the anchor and the recursive terms declare it with (see {@link DeclaredTypeFold#recursiveColumn}).
     */
    private static List<ResultSetColumn> recursiveCteColumns(final List<ResultSetColumn> anchorColumns,
                                                             final List<List<ResultSetColumn>> termColumns) {
        final List<ResultSetColumn> columns = new ArrayList<>(anchorColumns.size());
        for (int i = 0; i < anchorColumns.size(); i++) {
            final ResultSetColumn anchor = anchorColumns.get(i);
            DataType reported = anchor.getDataType();
            DataType declared = anchor.getStaticType();
            for (final List<ResultSetColumn> term : termColumns) {
                if (i < term.size()) {
                    final DataType termType = term.get(i).getStaticType() != null
                        ? term.get(i).getStaticType() : term.get(i).getDataType();
                    reported = DeclaredTypeFold.recursiveColumn(reported, termType);
                    declared = declared == null ? null : DeclaredTypeFold.recursiveColumn(declared, termType);
                }
            }
            if (reported == anchor.getDataType() && declared == anchor.getStaticType()) {
                columns.add(anchor);
                continue;
            }
            ResultSetColumn widened = new ResultSetColumn(anchor.getName(), reported, anchor.getTableName(),
                declared, anchor.isNullable(), anchor.isNullabilityKnown(), anchor.getValueRange(),
                anchor.getSpelledNumber());
            if (anchor.getCollation() != null) {
                widened = widened.withCollation(anchor.getCollation());
            }
            if (anchor.isUncheckedConstant()) {
                widened = widened.withUncheckedConstant();
            }
            columns.add(anchor.isFoldedDouble() ? widened.withFoldedDouble() : widened);
        }
        return columns;
    }

    /**
     * Rename columns in a ResultSet according to an explicit column list.
     * Used for: WITH cte(col1, col2) AS (SELECT a, b FROM ...)
     */
    private ResultSet renameColumns(final ResultSet original, final FrostlakeParser.ColumnListOptionalContext columnList) {
        final List<String> newNames = new ArrayList<>();
        for (final FrostlakeParser.NamePartContext partCtx : columnList.namePart()) {
            newNames.add(ParseTreeText.namePartText(partCtx));
        }
        if (newNames.isEmpty() || newNames.size() != original.getColumns().size()) {
            return original; // mismatch — leave unchanged
        }
        final List<ResultSetColumn> newCols = new ArrayList<>();
        for (int i = 0; i < original.getColumns().size(); i++) {
            final ResultSetColumn old = original.getColumns().get(i);
            final ResultSetColumn renamed = new ResultSetColumn(newNames.get(i), old.getDataType(), old.getTableName(),
                old.getStaticType());
            final ResultSetColumn checked = old.isUncheckedConstant() ? renamed.withUncheckedConstant() : renamed;
            newCols.add(old.isFoldedDouble() ? checked.withFoldedDouble() : checked);
        }
        return new ResultSet(newCols, original.getRows());
    }

    /** A set operation's arm, planned: a nested statement, or a select clause under its own scopes. */
    private PlannedQuery planSelectOperand(final FrostlakeParser.SelectStatementContext stmtCtx,
                                           final FrostlakeParser.SelectOperandContext op,
                                           final Map<String, Object> lateralContext,
                                           final Map<String, ResultSet> cteResults) {
        if (op.selectStatement() != null) {
            return planSelectFromContext(op.selectStatement(), lateralContext, null);
        }
        return planSingleSelectScoped(stmtCtx, op.selectClause(), lateralContext, cteResults);
    }

    /**
     * A set operation as a plan: every arm is planned first, so the arms' declared shapes are unified before
     * any row is combined; the first arm's rows are the source, every further arm combines with them in a
     * stage — INTERSECT binding tighter than UNION and EXCEPT / MINUS, which are left-associative among
     * themselves — and the statement's ORDER BY, LIMIT and FETCH follow as stages over the combined rows. An
     * arm runs once, when the stage combining it does, its values converted toward the unified types as they
     * arrive.
     */
    private PlannedQuery planSetOperations(final FrostlakeParser.SelectStatementContext ctx,
                                           final Map<String, Object> lateralContext,
                                           final Map<String, ResultSet> cteResults) {
        final List<FrostlakeParser.SelectOperandContext> operands = ctx.selectOperand();
        final List<FrostlakeParser.SetOperatorContext> setOperators = ctx.setOperator();
        final List<PlannedQuery> arms = new ArrayList<>();
        arms.add(planSelectOperand(ctx, operands.get(0), lateralContext, cteResults));
        final List<ResultSetColumn> firstColumns = arms.get(0).shape.getColumns();
        final int columnCount = firstColumns.size();
        // Snowflake operator precedence: INTERSECT binds tighter than UNION and EXCEPT/MINUS, which are
        // left-associative among themselves. Two-level fold: first collapse each maximal run of INTERSECTs
        // into a "term", then left-fold the terms with their separating UNION/EXCEPT operators. A plain
        // left-to-right fold would wrongly evaluate "A UNION B INTERSECT C" as "(A UNION B) INTERSECT C".
        // Each term also carries its column layout (its first operand's columns) so a UNION [ALL] BY NAME
        // between terms can align by column name; INTERSECT/EXCEPT stay positional (same-columns) as before.
        // Every arm is planned first, so the arms' declared types can be unified BEFORE any rows are
        // combined — a string arm's values convert to the non-string side's type whichever side leads
        // (live: 'x' ∪ 1 and 1 ∪ 'x' both fail "Numeric value 'x' is not recognized"), and dedup must
        // compare the CONVERTED values. Every arm's own column layout is kept so the combined result can
        // only claim a STATIC type where all arms declare one type TOGETHER — the leading arm's layout
        // wins the fold.
        final List<List<ResultSetColumn>> branchColumns = new ArrayList<>();
        branchColumns.add(firstColumns);
        boolean anyByName = false;
        for (int i = 1; i < operands.size(); i++) {
            final PlannedQuery arm = planSelectOperand(ctx, operands.get(i), lateralContext, cteResults);
            arms.add(arm);
            final List<ResultSetColumn> armColumns = arm.shape.getColumns();
            branchColumns.add(armColumns);
            final FrostlakeParser.SetOperatorContext operator = setOperators.get(i - 1);
            // ALL applies only to UNION in Snowflake (live-verified error shapes below).
            if (operator.ALL() != null && operator.UNION() == null) {
                throw new RuntimeException("Unsupported feature '"
                    + (operator.INTERSECT() != null ? "INTERSECT ALL" : "MINUS ALL") + "'.");
            }
            // BY NAME aligns columns by name and so allows different column counts; every other operator
            // requires matching column counts.
            anyByName |= isUnionByName(operator);
            if (!isUnionByName(operator) && armColumns.size() != columnCount) {
                // Live NAMES the counts and the branch, and calls it a COMPILATION error — the prefix
                // being what marks a body that will not compile, so a view over this shape was created
                // with null columns instead of being refused.
                throw new RuntimeException(SqlCompilationError.of(
                    "invalid number of result columns for set operator input branches, expected "
                        + columnCount + ", got " + armColumns.size()
                        + " in branch " + (i + 1)));
            }
        }
        final List<List<NumericType>> literalMeasurements = stringLiteralMeasurements(operands);
        final List<List<Boolean>> nullArms = nullLiteralArms(operands);
        // Eager conversion applies to all-UNION chains only: a subtractive operator over an EMPTY
        // left side short-circuits WITHOUT converting the right side (live-verified in
        // SetOperations), so MINUS / EXCEPT / INTERSECT keep the lazy compare-time coercion.
        boolean allUnion = true;
        for (final FrostlakeParser.SetOperatorContext operator : setOperators) {
            allUnion &= operator.UNION() != null;
        }
        final List<ResultSetColumn> unified = !anyByName && allUnion
            ? reconcileBranchTypes(firstColumns, branchColumns, literalMeasurements, nullArms) : null;
        // A column any branch collates compares under that collation through the whole set operation.
        final CollationSpec[] setCollations = anyByName ? null : setOperationCollations(branchColumns, columnCount);
        // The arms' rows, each produced once when a stage first combines it.
        final List<RowsProvider> armRows = new ArrayList<>();
        for (int i = 0; i < arms.size(); i++) {
            armRows.add(plannedArmRows(arms.get(i), i, branchColumns, unified));
        }
        // The terms: each a run of INTERSECTs, its first arm's index, and its layout (that arm's columns).
        final List<List<Integer>> terms = new ArrayList<>();
        final List<List<ResultSetColumn>> termColumns = new ArrayList<>();
        final List<FrostlakeParser.SetOperatorContext> termOperators = new ArrayList<>();
        List<Integer> currentTerm = new ArrayList<>();
        currentTerm.add(0);
        for (int i = 1; i < operands.size(); i++) {
            final FrostlakeParser.SetOperatorContext operator = setOperators.get(i - 1);
            if (operator.INTERSECT() != null) {
                currentTerm.add(i);
            } else {
                // UNION or EXCEPT/MINUS: close the current INTERSECT term and start a new one.
                terms.add(currentTerm);
                termColumns.add(branchColumns.get(currentTerm.get(0)));
                termOperators.add(operator);
                currentTerm = new ArrayList<>();
                currentTerm.add(i);
            }
        }
        terms.add(currentTerm);
        termColumns.add(branchColumns.get(currentTerm.get(0)));
        // A column every arm projects as one folded constant wrap is judged from the arms' rows, so where an
        // arm's shape allows one the arms are read while planning.
        final boolean wrapsNeedRows = !anyByName && SetOperationFoldedWraps.needsRows(columnCount, branchColumns);
        // The plan: the first term's arms, then every later term combined with the rows so far.
        final SelectPlan plan = new SelectPlan(this, functionRegistry);
        final RowsProvider firstArm = armRows.get(0);
        plan.sourceStage(new StageOperator("SOURCE[arm 1]{" + firstArm.describe() + "}") {
            @Override
            protected List<Row> apply(final List<Row> input) {
                return firstArm.rows();
            }
        }, wrapsNeedRows);
        for (int k = 1; k < terms.get(0).size(); k++) {
            plan.add(intersectStage(armRows.get(terms.get(0).get(k)), setOperators.get(terms.get(0).get(k) - 1),
                setCollations, terms.get(0).get(k)));
        }
        List<ResultSetColumn> accColumns = termColumns.get(0);
        for (int j = 1; j < terms.size(); j++) {
            final FrostlakeParser.SetOperatorContext operator = termOperators.get(j - 1);
            final boolean hasAll = operator.ALL() != null;
            // A term of several arms is a relation of its own: its INTERSECTs run as their own pipeline.
            final List<Integer> term = terms.get(j);
            final RowsProvider termRows = termRows(term, armRows, setOperators, setCollations);
            final String stageName = setOperatorSpelling(operator) + "[" + termDescription(term) + "]{"
                + termRows.describe() + "}";
            if (operator.UNION() != null) {
                if (isUnionByName(operator)) {
                    // Align both sides to a merged layout (accumulated columns, then the right side's
                    // columns that aren't already present); missing columns become NULL.
                    final List<ResultSetColumn> leftColumns = accColumns;
                    final List<ResultSetColumn> rightColumns = termColumns.get(j);
                    final List<ResultSetColumn> merged = mergeColumnsByName(leftColumns, rightColumns);
                    plan.add(new StageOperator(stageName) {
                        @Override
                        protected List<Row> apply(final List<Row> input) {
                            final List<Row> leftReshaped = reshapeRowsByName(input, leftColumns, merged);
                            final List<Row> rightReshaped = reshapeRowsByName(termRows.rows(), rightColumns,
                                merged);
                            return SetOperations.applyUnion(leftReshaped, rightReshaped, hasAll, merged);
                        }
                    });
                    accColumns = merged;
                } else {
                    final List<ResultSetColumn> layout = accColumns;
                    plan.add(new StageOperator(stageName) {
                        @Override
                        protected List<Row> apply(final List<Row> input) {
                            return SetOperations.applyUnion(input, termRows.rows(), hasAll, layout, setCollations);
                        }
                    });
                }
            } else {
                // EXCEPT / MINUS (MINUS is a Snowflake synonym for EXCEPT).
                plan.add(new StageOperator(stageName) {
                    @Override
                    protected List<Row> apply(final List<Row> input) {
                        return SetOperations.applyExcept(input, termRows.rows(), hasAll, setCollations);
                    }
                });
            }
        }
        accColumns = new ArrayList<>(reconcileBranchTypes(accColumns, branchColumns, literalMeasurements, nullArms));
        stampCollations(accColumns, setCollations);
        if (wrapsNeedRows) {
            final List<List<Row>> branchRows = new ArrayList<>();
            for (final RowsProvider arm : armRows) {
                branchRows.add(arm.rows());
            }
            SetOperationFoldedWraps.keep(accColumns, branchColumns, branchRows);
        }
        // ORDER BY / LIMIT / FETCH apply once to the combined result (statement level). A set
        // operation's ORDER BY resolves against the combined OUTPUT only (its column names,
        // ordinals, and expressions over those names) — never any branch's FROM scope.
        final List<ResultSetColumn> outputColumns = accColumns;
        if (ctx.orderByClause() != null) {
            plan.add(new StageOperator("ORDER BY[set operation]") {
                @Override
                protected List<Row> apply(final List<Row> input) {
                    return orderByExecutor.orderBySetOperation(input, ctx, outputColumns);
                }
            });
        }
        // Every arm is planned, and its names judged, by now: an unset bind among the counts is refused next.
        RowLimitBindRule.reject(ctx, this);
        if (ctx.limitClause() != null) {
            final int[] window = limitWindow(ctx.limitClause());
            plan.add(new LimitOperator(window[0], window[1]));
        }
        if (ctx.fetchClause() != null) {
            final int[] window = fetchWindow(ctx.fetchClause());
            plan.add(new LimitOperator(window[0], window[1]));
        }
        recordPlan(plan);
        return new PlannedQuery(new ResultSet(outputColumns, new ArrayList<Row>()), plan.describeStages()) {
            @Override
            ResultSet run() {
                return new ResultSet(outputColumns, plan.execute());
            }
        };
    }

    /**
     * One arm's rows, produced once when first combined: the arm runs and, in an all-UNION chain, its values
     * convert toward the unified column types — the arm's rows are copied, never mutated, as they can alias
     * storage.
     *
     * @param arm           the planned arm
     * @param index         the arm's position, from 0
     * @param branchColumns every arm's own columns
     * @param unified       the unified column types, or null where the arms keep compare-time coercion
     */
    private static RowsProvider plannedArmRows(final PlannedQuery arm, final int index,
                                               final List<List<ResultSetColumn>> branchColumns,
                                               final List<ResultSetColumn> unified) {
        return new MemoizedRows(new RowsProvider() {
            @Override
            public List<Row> rows() {
                final List<Row> rows = new ArrayList<>(arm.run().getRows());
                if (unified != null) {
                    final List<List<Row>> one = new ArrayList<>();
                    one.add(rows);
                    SetOperations.coerceStringBranches(one, branchColumns, unified);
                }
                return rows;
            }

            @Override
            public String describe() {
                return arm.description;
            }
        });
    }

    /** The INTERSECT stage combining the rows so far with one arm's. */
    private static Operator intersectStage(final RowsProvider armRows,
                                           final FrostlakeParser.SetOperatorContext operator,
                                           final CollationSpec[] collations, final int armIndex) {
        final boolean hasAll = operator.ALL() != null;
        return new StageOperator("INTERSECT[arm " + (armIndex + 1) + "]{" + armRows.describe() + "}") {
            @Override
            protected List<Row> apply(final List<Row> input) {
                return SetOperations.applyIntersect(input, armRows.rows(), hasAll, collations);
            }
        };
    }

    /**
     * A later term's rows: its first arm's, intersected with the rest through a pipeline of its own, planned
     * here and run when the stage combining the term does.
     */
    private RowsProvider termRows(final List<Integer> term, final List<RowsProvider> armRows,
                                  final List<FrostlakeParser.SetOperatorContext> setOperators,
                                  final CollationSpec[] collations) {
        if (term.size() == 1) {
            return armRows.get(term.get(0));
        }
        final SelectPlan termPlan = new SelectPlan(this, functionRegistry);
        final RowsProvider firstArm = armRows.get(term.get(0));
        termPlan.sourceStage(new StageOperator("SOURCE[arm " + (term.get(0) + 1) + "]{" + firstArm.describe() + "}") {
            @Override
            protected List<Row> apply(final List<Row> input) {
                return firstArm.rows();
            }
        }, false);
        for (int k = 1; k < term.size(); k++) {
            termPlan.add(intersectStage(armRows.get(term.get(k)), setOperators.get(term.get(k) - 1), collations,
                term.get(k)));
        }
        return new MemoizedRows(new RowsProvider() {
            @Override
            public List<Row> rows() {
                return termPlan.execute();
            }

            @Override
            public String describe() {
                return termPlan.describeStages();
            }
        });
    }

    /** How a term reads in a plan: {@code arm 2}, or {@code arm 2 INTERSECT arm 3}. */
    private static String termDescription(final List<Integer> term) {
        final StringBuilder text = new StringBuilder();
        for (final Integer arm : term) {
            text.append(text.length() > 0 ? " INTERSECT arm " : "arm ").append(arm + 1);
        }
        return text.toString();
    }

    /** A set operator's canonical spelling: {@code UNION ALL BY NAME}, {@code INTERSECT}, {@code EXCEPT}. */
    private static String setOperatorSpelling(final FrostlakeParser.SetOperatorContext operator) {
        if (operator.INTERSECT() != null) {
            return "INTERSECT";
        }
        if (operator.UNION() == null) {
            return "EXCEPT";
        }
        return "UNION" + (operator.ALL() != null ? " ALL" : "") + (isUnionByName(operator) ? " BY NAME" : "");
    }

    /**
     * The collation each column of a set operation compares under: the one its branches' columns carry, from
     * whichever branch carries it — {@code SELECT 'A' UNION SELECT c} over an {@code en-ci} column is
     * {@code en-ci} (live-verified). Branches that carry DIFFERENT collations leave the column without one.
     *
     * @return one entry per column, null where the column carries none
     */
    private static CollationSpec[] setOperationCollations(final List<List<ResultSetColumn>> branchColumns,
                                                          final int columnCount) {
        final CollationSpec[] collations = new CollationSpec[columnCount];
        for (int i = 0; i < columnCount; i++) {
            String chosen = null;
            boolean conflicting = false;
            for (final List<ResultSetColumn> columns : branchColumns) {
                final String spec = i < columns.size() ? columns.get(i).getCollation() : null;
                if (spec == null) {
                    continue;
                }
                if (chosen == null) {
                    chosen = spec;
                } else if (!chosen.equalsIgnoreCase(spec)) {
                    conflicting = true;
                }
            }
            collations[i] = chosen == null || conflicting ? null : CollationSpec.parse(chosen);
        }
        return collations;
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
     * {@link #planSingleSelect} under the clause's own scopes: each clause answers to its own statistics bound
     * — a subquery's must not reach the clause that runs it, nor the enclosing clause's the subquery — and to
     * its own QUALIFY-without-window flag. Both are in force again while the planned query runs.
     */
    private PlannedQuery planSingleSelectScoped(final FrostlakeParser.SelectStatementContext stmtCtx,
                                                final FrostlakeParser.SelectClauseContext ctx,
                                                final Map<String, Object> lateralContext,
                                                final Map<String, ResultSet> cteResults) {
        final CountStatisticsBound enclosing = statisticsBound.get();
        statisticsBound.remove();
        final boolean windowlessQualify = qualifiesWithoutWindow(ctx);
        final boolean outerQualify = QualifyWithoutWindow.begin(windowlessQualify);
        final PlannedQuery planned;
        final CountStatisticsBound plannedBound;
        try {
            planned = planSingleSelect(stmtCtx, ctx, lateralContext, cteResults);
            plannedBound = statisticsBound.get();
        } finally {
            QualifyWithoutWindow.end(outerQualify);
            if (enclosing == null) {
                statisticsBound.remove();
            } else {
                statisticsBound.set(enclosing);
            }
        }
        return new PlannedQuery(planned.shape, planned.description) {
            @Override
            ResultSet run() {
                final CountStatisticsBound outer = statisticsBound.get();
                if (plannedBound == null) {
                    statisticsBound.remove();
                } else {
                    statisticsBound.set(plannedBound);
                }
                final boolean qualify = QualifyWithoutWindow.begin(windowlessQualify);
                try {
                    return planned.run();
                } finally {
                    QualifyWithoutWindow.end(qualify);
                    if (outer == null) {
                        statisticsBound.remove();
                    } else {
                        statisticsBound.set(outer);
                    }
                }
            }
        };
    }

    /** {@link #planSingleSelectScoped} run at once. */
    private ResultSet executeSingleSelect(final FrostlakeParser.SelectStatementContext stmtCtx,
                                         final FrostlakeParser.SelectClauseContext ctx,
                                         final Map<String, Object> lateralContext,
                                         final Map<String, ResultSet> cteResults) {
        return planSingleSelectScoped(stmtCtx, ctx, lateralContext, cteResults).run();
    }

    /**
     * Whether a clause's result still carries the statistics of the catalog table beneath it (see
     * RelationStatistics): it reads one table — or one relation that carries them — through a projection
     * and at most a WHERE, window functions and scalar subqueries in its items included. A join, a PIVOT,
     * a SAMPLE, time travel, DISTINCT, TOP, a grouping, an aggregate, HAVING, QUALIFY, CONNECT BY, an
     * ORDER BY, a LIMIT or FETCH and a set operation each take it past them (live: a derived table doing
     * any of these folds a typeof over its COUNT to the scan).
     */
    private boolean keepsSourceStatistics(final FrostlakeParser.SelectStatementContext stmtCtx,
                                          final FrostlakeParser.SelectClauseContext ctx,
                                          final FrostlakeParser.TableReferenceContext reference,
                                          final Table source, final boolean joined) {
        if (reference == null || joined || source == null || statisticsBound.get() == null
                || !source.isCatalogResident() && source.getRelationStatistics() == null) {
            return false;
        }
        if (reference.pivotClause() != null || reference.unpivotClause() != null
                || reference.sampleClause() != null || reference.tableSource().timeTravelClause() != null) {
            return false;
        }
        if (ctx.DISTINCT() != null || ctx.topClause() != null || ctx.groupByClause() != null
                || ctx.havingClause() != null || ctx.qualifyClause() != null || ctx.connectByClause() != null
                || hasAggregateFunction(ctx) || aggregatesInOrderByOrQualify(stmtCtx, ctx)) {
            return false;
        }
        return stmtCtx.selectOperand().size() == 1 && stmtCtx.orderByClause() == null
            && stmtCtx.limitClause() == null && stmtCtx.fetchClause() == null;
    }

    private PlannedQuery planSingleSelect(final FrostlakeParser.SelectStatementContext stmtCtx,
                                      final FrostlakeParser.SelectClauseContext ctx,
                                      final Map<String, Object> lateralContext,
                                      final Map<String, ResultSet> cteResults) {
        // The plan-time rules judge a statement, and a subquery being compiled with it (see SubqueryCompilation);
        // a subquery run for a row of the query around it is judged no further.
        final boolean planTime = lateralContext == null || SubqueryCompilation.compiles(lateralContext);
        // A subquery's refusal other than a name waits until this query has been judged (see SubqueryEvaluator.compile).
        RuntimeException subqueryRefusal = null;
        // Each join's ON compiles where the join is reached, in the scope in force there; a refusal waits for its rank.
        final JoinConditionCompilation onCompile = planTime ? new JoinConditionCompilation(this, ctx) : null;
        // A FRAME with no ORDER BY beside it is refused FIRST, ahead of everything below and ahead of
        // the relation being resolved at all — measured, it outranks every neighbour it was put beside
        // and loses only to a syntax error. The walk covers the whole statement, so a window written in
        // a SUBQUERY is refused at its own OVER even when that subquery would never have been reached.
        windowEvaluator.rejectFrameWithoutOrderBy(stmtCtx);
        // A windowed MEDIAN, MODE or PERCENTILE may not carry an ORDER BY or a moving frame — it
        // already ranks its input, and live refuses the second ordering outright. Ahead of the
        // relation, which it outranks.
        windowEvaluator.rejectUnsupportedFrameForWholePartitionAggregate(stmtCtx);
        if (ctx.tableExpression() == null && ctx.connectByClause() == null) {
            rejectQualifiedStarWithoutFrom(ctx);
        }
        // Outside the try so Snowflake's own wording reaches the caller unwrapped.
        SelectItemAccessors.rejectStandaloneInterval(ctx.selectList());
        Set<String> displacedCorrelations = null;
        boolean plannedCorrelations = false;
        try {
            // QUALIFY needs a window function somewhere in the query — in the select list or in the
            // predicate itself (live-verified, including the positioned refusal shape). An AGGREGATE
            // query is judged in the other order, though: the aggregate makes the whole query grouped,
            // and live checks the SELECT LIST against that first — `SELECT a FROM g QUALIFY SUM(b) > 0`
            // reads "[G.A] is not a valid group by expression", while `SELECT SUM(b) FROM g QUALIFY
            // SUM(b) > 0`, whose list is fine, reaches the sentence below after all. So it is deferred
            // there and raised once the grouped validation has had its say.
            // A statement carrying an unresolvable function NAME is deferred for the same reason: live
            // names the function before it complains about the clause, so the check waits for the site
            // below, which runs after the name scan.
            // An unresolvable COLUMN outranks it too, with or without a FROM (live: SELECT 1 AS a QUALIFY
            // a = 1 AND missing = 1 names MISSING), so every QUALIFY takes the deferred site below: a
            // FROM-less query that carries one runs over its synthesised row rather than returning early.

            final FrostlakeParser.TableExpressionContext tableExpr = ctx.tableExpression();

            // Handle SELECT without FROM clause (e.g., SELECT 1, SELECT {'key': 'value'})
            //
            // ★ AN AGGREGATE IS THE EXCEPTION, and it takes the ordinary path instead. A FROM-less query
            // is ONE ROW OF ONE GROUP on a real account — SELECT SUM(1) is 1, COUNT(*) is 1, and
            // SELECT SUM(1) WHERE 1 = 0 still answers a row, holding NULL, because the group survives
            // its own empty input. Evaluating the select list item by item, as the no-FROM path does,
            // can never produce any of that: the aggregate falls through to the scalar dispatch, which
            // does not know aggregate names and answers "Unknown function SUM." for a function the
            // engine registers and evaluates happily one clause later. So the row is SYNTHESISED and
            // everything below — WHERE, the implicit group, HAVING, ORDER BY, LIMIT — runs unchanged.
            final boolean synthesizedSingleRow = tableExpr == null;
            if (synthesizedSingleRow && !aggregatesAnywhere(stmtCtx, ctx)
                    && !hasWindowCallAtThisLevel(ctx) && !OverCallRules.writesOverCall(stmtCtx.orderByClause())
                    && ctx.qualifyClause() == null && havingFiltersFromlessRow(ctx)
                    && !FromlessDual.isRead(stmtCtx, ctx)) {
                rejectUnresolvableNamesWithoutFrom(stmtCtx, ctx);
                return planSelectWithoutFrom(stmtCtx, ctx, lateralContext);
            }

            // Get the first table reference
            List<FrostlakeParser.TableReferenceContext> allTableRefs = synthesizedSingleRow
                ? new ArrayList<FrostlakeParser.TableReferenceContext>() : tableExpr.tableReference();
            if (allTableRefs.isEmpty() && !synthesizedSingleRow) {
                throw new RuntimeException("No table reference found in SELECT");
            }
            // A parenthesized FROM join — FROM ( a JOIN b ON c ) — is pure grouping: identical to
            // FROM a JOIN b ON c and, unlike a derived-table subquery, keeps the inner aliases (a, b)
            // visible to the outer WHERE/SELECT. Flatten any such reference by splicing its inner
            // tableReferences/joinClauses inline so the ordinary join path below handles it unchanged.
            List<FrostlakeParser.JoinClauseContext> allJoins = synthesizedSingleRow
                ? new ArrayList<FrostlakeParser.JoinClauseContext>() : tableExpr.joinClause();
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
            AsofJoinCompilation.rejectMatchConditionWithoutAsof(allJoins);
            final FrostlakeParser.TableReferenceContext firstTableRef = synthesizedSingleRow
                ? null : allTableRefs.get(0);
            if (!synthesizedSingleRow) {
                rejectDuplicateSourceNames(tableExpr, cteResults);
                new StarQualifierCheck(this).rejectBeforeSources(ctx, cteResults);
            }

            // The plan: the source relation, then every stage in order, run as one pipeline once planned. The
            // source is the plan's first stage — its shape settled here, its rows produced when the pipeline
            // runs (see planTableReference); the synthesised source is DUAL's one row. The stage is added once
            // the legacy (+) form below has decided which side the plan starts from.
            final SelectPlan plan = new SelectPlan(this, functionRegistry);
            final TableData tableData = synthesizedSingleRow ? singleEmptyRowSource()
                : plannedReference(firstTableRef, sourceContext(firstTableRef, lateralContext), cteResults);
            Table table = tableData.table;
            final String tableName = table.getName();

            // Check SELECT permission on first table (if it's not a subquery)
            if (securityManager != null && firstTableRef != null
                    && firstTableRef.tableSource().tableQualifiedName() != null) {
                final String qualifiedTableName = ParseTreeText.getQualifiedName(firstTableRef.tableSource().tableQualifiedName());
                securityManager.checkPermission(Privilege.SELECT, SecurableObjectType.TABLE, qualifiedTableName);
            }

            // Track table aliases and tables for WHERE clause evaluation
            final FromClauseRelations aliasToTable = new FromClauseRelations();
            aliasToTable.putRelation(tableData.alias, table);
            final List<Table> allTables = new ArrayList<>();
            allTables.add(table);
            // Which comma-separated item each relation came from: a USING join merges its own item's only.
            final FromItemRelations fromItems = new FromItemRelations(tableExpr);
            if (firstTableRef != null) {
                fromItems.head(firstTableRef, table);
            }

            // Oracle legacy (+) outer join — FROM a, b WHERE a.k = b.k(+) ...  ≡  a LEFT JOIN b ON a.k=b.k
            // WHERE <non-(+) predicates>. Detected at the parse-tree level for the common two-comma-table,
            // no-explicit-join form; the (+) side is the null-supplying (right) side of the LEFT join, and
            // conjuncts carrying a (+) become the ON condition while the rest stay in the (residual) WHERE.
            boolean plusHandled = false;
            String plusResidualWhere = null;
            final FrostlakeParser.WhereClauseContext plusWhere = getWhereClause(ctx);
            if (allTableRefs.size() == 2 && allJoins.isEmpty()
                    && plusWhere != null && containsOuterJoinMarker(plusWhere.booleanExpr())) {
                final String alias0 = (tableData.alias != null ? tableData.alias : table.getName()).toUpperCase();
                final TableData rightData0 = executeTableReference(allTableRefs.get(1), sourceContext(allTableRefs.get(1), lateralContext), cteResults);
                final String alias1 = (rightData0.alias != null ? rightData0.alias : rightData0.table.getName()).toUpperCase();
                // A marked column names its relation by qualifier, or by carrying the column when written bare.
                final Map<String, Table> plusRelations = new LinkedHashMap<>();
                plusRelations.put(alias0, table);
                plusRelations.put(alias1, rightData0.table);
                final Set<String> plusAliases =
                    OuterJoinMarkerRules.markedRelations(plusWhere.booleanExpr(), plusRelations);
                final boolean plusOn0 = plusAliases.contains(alias0);
                final boolean plusOn1 = plusAliases.contains(alias1);
                if (plusOn0 ^ plusOn1) {   // exactly one side is null-supplied — the standard Oracle form
                    final List<FrostlakeParser.BooleanExprContext> conjuncts =
                        OuterJoinMarkerRules.conjuncts(plusWhere.booleanExpr());
                    final List<String> onParts = new ArrayList<>();
                    final List<String> residualParts = new ArrayList<>();
                    for (final FrostlakeParser.BooleanExprContext cj : conjuncts) {
                        (containsOuterJoinMarker(cj) ? onParts : residualParts).add(getOriginalText(cj));
                    }
                    plusResidualWhere = residualParts.isEmpty() ? null : String.join(" AND ", residualParts);
                    final TableData firstData = sampled(tableData, firstTableRef.sampleClause());
                    final TableData leftData = plusOn1 ? firstData : rightData0;   // preserved side
                    final TableData nullSuppliedData = plusOn1 ? rightData0 : firstData;   // the (+) side
                    final Table leftTable = leftData.table;
                    final String leftAlias = plusOn1 ? alias0 : alias1;
                    final Table rightTable = nullSuppliedData.table;
                    final String rightAlias = plusOn1 ? alias1 : alias0;
                    aliasToTable.clear();
                    aliasToTable.put(leftAlias, leftTable);
                    aliasToTable.put(rightAlias, rightTable);
                    allTables.clear();
                    allTables.add(leftTable);
                    allTables.add(rightTable);
                    plan.sourceStage(leftData.sourceStage());
                    plan.add(planConditionJoin(leftTable, nullSuppliedData.rows, rightTable, JoinType.LEFT,
                        String.join(" AND ", onParts), aliasToTable, allTables));
                    table = mergeTableMetadata(leftTable, rightTable);
                    recordNullExtension(table, leftTable, rightTable, JoinType.LEFT);
                    plusHandled = true;
                }
            }
            if (!plusHandled) {
                plan.sourceStage(tableData.sourceStage());
                if (!synthesizedSingleRow && firstTableRef.sampleClause() != null) {
                    plan.add(sampleStage(firstTableRef.sampleClause()));
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
                    final FrostlakeParser.TableReferenceContext rightTableRef = allTableRefs.get(i);
                    final boolean isLateral = rightTableRef.LATERAL() != null || isImplicitlyLateral(rightTableRef);

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
                        subqueryRefusal = firstRefusal(subqueryRefusal,
                    compileLateralSubquery(rightTableRef, null, table, aliasToTable, allTables, lateralContext, ctx));
                        final PlannedRelation lateralData = planLateralJoin(table, rightTableRef, null, aliasToTable,
                            allTables, ctx);
                        plan.add(lateralData.stage);
                        // Update table metadata after lateral join
                        table = mergeTableMetadata(table, lateralData.table);
                        aliasToTable.putRelation(lateralData.alias, lateralData.table);
                        allTables.add(lateralData.table);
                        fromItems.head(rightTableRef, lateralData.table);
                    } else {
                        // Regular comma-separated tables are CROSS JOIN
                        final TableData rightData = executeTableReference(rightTableRef, sourceContext(rightTableRef, lateralContext), cteResults);

                        // Self-join guard: a distinct Table instance so each alias resolves to its own side.
                        final Table rightJoinTable = distinctJoinTable(rightData.table, allTables);

                        // Execute CROSS JOIN using operator
                        final OperatorContext opContext = OperatorContext.builder()
                            .table(table)
                            .functionRegistry(functionRegistry).queryExecutor(this)
                            .build();
                        final JoinOperator joinOp = JoinOperator.cross(table, rightJoinTable, rightData.source);
                        // PREDICATE PUSHDOWN. `FROM l, r WHERE l.k = r.k` is the same query as
                        // `l INNER JOIN r ON l.k = r.k`, but its equality sits in WHERE, so the join
                        // itself sees no condition and would build the whole cartesian product for
                        // the WHERE to throw away — 100 million rows to keep 10 thousand. Lifting the
                        // equi-join conjuncts out lets the hash path run. WHERE still applies
                        // afterwards, so this is a PRE-FILTER: anything else the WHERE says is
                        // unaffected, and applying the equality twice is idempotent.
                        final int[][] whereKeys = whereEquiJoinKeys(ctx, table, rightJoinTable,
                            aliasToTable, rightData.alias != null ? rightData.alias
                                : rightJoinTable.getName());
                        if (whereKeys != null) {
                            joinOp.withEquiKeys(whereKeys[0], whereKeys[1]);
                        }
                        plan.add(joinOp, opContext);

                        // Update table metadata
                        table = mergeTableMetadata(table, rightJoinTable);
                        aliasToTable.putRelation(rightData.alias, rightJoinTable);
                        allTables.add(rightJoinTable);
                        fromItems.head(rightTableRef, rightJoinTable);
                    }
                }
            }

            // Process explicit joins
            for (final FrostlakeParser.JoinClauseContext joinCtx : allJoins) {
                final FrostlakeParser.TableReferenceContext rightTableRef = joinCtx.tableReference();
                fromItems.joinReached(joinCtx, allTables);

                // ASOF JOIN — the closest-match join. Its right side is an ordinary relation, but the
                // MATCH_CONDITION must be evaluated one side at a time (Snowflake requires the left operand
                // to reference only left-side columns and the right operand only right-side ones), so it
                // gets its own operator rather than the generic ON-condition path.
                if (joinCtx.ASOF() != null || joinCtx.asofMatchCondition() != null) {
                    final Map<String, Table> leftAliases = new LinkedHashMap<>(aliasToTable);
                    final List<Table> leftTables = new ArrayList<>(allTables);
                    final TableData asofRight = executeTableReference(rightTableRef, sourceContext(rightTableRef, lateralContext), cteResults);
                    final Table asofRightTable = distinctJoinTable(asofRight.table, allTables);
                    // `ASOF JOIN r LIMIT MATCH_CONDITION (…)` aliases the right table LIMIT — a word the
                    // ordinary alias rule keeps out, so the ASOF clause carries it (live-verified syntax).
                    final String asofAlias = joinCtx.asofMatchCondition() != null
                            && joinCtx.asofMatchCondition().LIMIT() != null
                        ? "LIMIT"
                        : (asofRight.alias != null ? asofRight.alias : asofRightTable.getName());
                    aliasToTable.put(asofAlias, asofRightTable);
                    if (asofRight.alias != null || !asofAlias.equals(asofRightTable.getName())) {
                        aliasToTable.markAlias(asofAlias);
                    }
                    allTables.add(asofRightTable);
                    AsofJoinCompilation.rejectMissingUsingColumns(joinCtx, table, asofRightTable);
                    // A condition that does not compile joins no rows: its refusal waits for its rank.
                    plan.add(AsofJoinCompilation.conditionsCompile(this, joinCtx, table, aliasToTable, allTables,
                            onCompile)
                        ? planAsofJoin(table, asofRight.rows, asofRightTable, joinCtx,
                            leftAliases, leftTables, asofAlias, aliasToTable, allTables, onCompile)
                        : new PipelineStage(emptyRelation("join condition refused"), null));
                    final Table asofLeft = table;
                    final Set<String> asofKeys = usingJoinColumnNames(joinCtx, table, asofRightTable);
                    table = mergeTableMetadata(table, asofRightTable, asofKeys);
                    // An ASOF join extends its right side with NULLs where no row matches.
                    recordNullExtension(table, asofLeft, asofRightTable, JoinType.LEFT);
                    fromItems.mergeScopes(table, asofLeft, asofRightTable, joinCtx, JoinType.LEFT, asofKeys, allTables);
                    continue;
                }
                // A table function joined WITHOUT the LATERAL keyword is still implicitly lateral in
                // Snowflake: FROM t JOIN TABLE(FLATTEN(t_col:path)) references the left row's columns
                // (vendor loaders use exactly this shape, with no ON clause). Only the CONDITION-LESS
                // form goes lateral — the lateral path performs no ON filtering, so a non-correlated
                // JOIN TABLE(GENERATOR(...)) g ON t.id = g.seq must keep the regular join path.
                // A table function joined with ON that reads nothing of the relations to its left — written
                // TABLE(f(…)) or LATERAL f(…) — joins as an ordinary relation (live-verified, every join kind).
                final boolean selfContainedFunction = isImplicitlyLateral(rightTableRef) && joinCtx.ON() != null
                    && (rightTableRef.tableSource().TABLE() != null || joinCtx.LATERAL() != null
                        || rightTableRef.LATERAL() != null)
                    && !TableFunctionCorrelation.readsLeft(this, rightTableRef.tableSource(), table, aliasToTable,
                        allTables, selectItemAliasNames(ctx), lateralContext);
                if (isImplicitlyLateral(rightTableRef) && joinCtx.ON() != null && !selfContainedFunction) {
                    // Live-verified Snowflake restriction on lateral table FUNCTIONS — even ON TRUE is
                    // rejected, with or without the LATERAL keyword.
                    rejectLateralTableWrapper(joinCtx.LATERAL() != null, rightTableRef.tableSource());
                    throw new RuntimeException("Unsupported feature 'lateral table function called with "
                        + "OUTER JOIN syntax or a join predicate (ON clause)'.");
                }
                // A lateral SUBQUERY may carry a join predicate — `LEFT JOIN LATERAL (<correlated>) l ON
                // TRUE` is live-verified to work over tables and CTEs, null-extending the unmatched rows.
                // (Only a lateral TABLE FUNCTION is restricted, handled above.) An earlier check suggested
                // otherwise, but its failure came from correlating into a UNION ALL derived table — an
                // unrelated Snowflake limitation that reports the same "Unsupported subquery type".
                rejectLateralTableWrapper(joinCtx.LATERAL() != null, rightTableRef.tableSource());
                // A table function joined with USING or NATURAL is lateral only when it reads the left side; one
                // that reads nothing of it joins by its key as any relation does (see TableFunctionKeyJoin).
                final TableFunctionKeyJoin keyed = isImplicitlyLateral(rightTableRef)
                        && (joinCtx.USING() != null || joinCtx.NATURAL() != null)
                    ? new TableFunctionKeyJoin(this, rightTableRef, table, aliasToTable, allTables) : null;
                final boolean isLateral = keyed != null ? keyed.readsTheLeftSide() : !selfContainedFunction
                    && (joinCtx.LATERAL() != null
                        || (isImplicitlyLateral(rightTableRef) && joinCtx.ON() == null && joinCtx.USING() == null));

                if (isLateral) {
                    // LATERAL join: evaluate right side for each left side row
                    subqueryRefusal = firstRefusal(subqueryRefusal,
                    compileLateralSubquery(rightTableRef, joinCtx, table, aliasToTable, allTables, lateralContext, ctx));
                    final PlannedRelation lateralData = planLateralJoin(table, rightTableRef, joinCtx, aliasToTable,
                        allTables, ctx);
                    final RuntimeException keyRefusal = keyed == null ? null
                        : TableFunctionKeyJoin.refusal(joinCtx, table, lateralData.table);
                    // Update table metadata after lateral join
                    final Table lateralLeft = table;
                    table = keyed == null ? mergeTableMetadata(table, lateralData.table)
                        : mergeTableMetadata(table, lateralData.table,
                            usingJoinColumnNames(joinCtx, table, lateralData.table));
                    recordNullExtension(table, lateralLeft, lateralData.table, lateralJoinKind(joinCtx));
                    aliasToTable.putRelation(lateralData.alias, lateralData.table);
                    allTables.add(lateralData.table);
                    if (keyRefusal != null) {
                        // The lateral restriction is the statement's last word: every other refusal speaks first.
                        if (onCompile == null) {
                            throw keyRefusal;
                        }
                        onCompile.holdShape(keyRefusal);
                        plan.add(emptyRelation("join condition refused"));
                        continue;
                    }
                    // A condition that does not compile judges no pair, so the lateral side is never read for it:
                    // its refusal waits for its rank in the statement.
                    if (onCompile != null && joinCtx.ON() != null
                            && !onCompile.compiles(joinCtx.booleanExpr(), lateralLeft, aliasToTable, allTables)) {
                        plan.add(emptyRelation("join condition refused"));
                    } else {
                        plan.add(lateralData.stage);
                    }
                } else if (isParenthesizedJoin(rightTableRef.tableSource())) {
                    // A join group on the RIGHT side — x JOIN (a JOIN b ON ...) ON ... : execute the
                    // inner chain first; its aliases join the outer scope so the outer ON can see them.
                    final PlannedJoinGroup groupData =
                        planJoinGroup(rightTableRef.tableSource(), aliasToTable, allTables, ctx, cteResults, onCompile);
                    plan.add(planJoin(table, groupData.rows, groupData.table, joinCtx, aliasToTable, allTables, ctx,
                        lateralContext, onCompile));
                    final Table groupLeft = table;
                    final Set<String> groupKeys = usingJoinColumnNames(joinCtx, table, groupData.table);
                    table = mergeTableMetadata(table, groupData.table, groupKeys);
                    recordNullExtension(table, groupLeft, groupData.table, joinTypeOf(joinCtx));
                    fromItems.mergeScopes(table, groupLeft, groupData.table, joinCtx, joinTypeOf(joinCtx), groupKeys,
                        allTables);
                } else {
                    // Regular join
                    final TableData rightData = executeTableReference(rightTableRef, sourceContext(rightTableRef, lateralContext), cteResults);

                    // Self-join guard: a distinct Table instance so each alias resolves to its own side.
                    final Table rightJoinTable = distinctJoinTable(rightData.table, allTables);

                    // Add right table to maps BEFORE executing join so condition evaluation can resolve columns
                    aliasToTable.putRelation(rightData.alias, rightJoinTable);
                    allTables.add(rightJoinTable);

                    // The JOIN stage; its condition compiles here, where the join is reached
                    plan.add(planJoin(table, rightData.source, rightJoinTable, joinCtx, aliasToTable, allTables, ctx,
                        lateralContext, onCompile));

                    // Update table metadata to include both tables. An OUTER join null-extends one
                    // side, and a column that can be manufactured as NULL is no longer NOT NULL.
                    final Table joinLeft = table;
                    final Set<String> joinKeys = usingJoinColumnNames(joinCtx, table, rightJoinTable);
                    table = mergeTableMetadata(table, rightJoinTable, joinKeys, joinTypeOf(joinCtx));
                    fromItems.mergeScopes(table, joinLeft, rightJoinTable, joinCtx, joinTypeOf(joinCtx), joinKeys,
                        allTables);
                }
            }

            // LATERAL items written after the joins, now that the joined tables are in scope.
            for (final FrostlakeParser.TableReferenceContext lateralRef : deferredLaterals) {
                subqueryRefusal = firstRefusal(subqueryRefusal,
                    compileLateralSubquery(lateralRef, null, table, aliasToTable, allTables, lateralContext, ctx));
                final PlannedRelation lateralData = planLateralJoin(table, lateralRef, null, aliasToTable, allTables,
                    ctx);
                plan.add(lateralData.stage);
                table = mergeTableMetadata(table, lateralData.table);
                aliasToTable.putRelation(lateralData.alias, lateralData.table);
                allTables.add(lateralData.table);
            }
            if (!synthesizedSingleRow) {
                new StarQualifierCheck(this).rejectAfterSources(ctx, aliasToTable);
            }
            StarModifierCheck.reject(ctx, table, aliasToTable);
            AmbiguousOutputNames.reject(ctx, stmtCtx.selectOperand().size() == 1 ? stmtCtx : null,
                synthesizedSingleRow ? null : allTables);

            // CONNECT BY expands the FROM clause into a hierarchy BEFORE the WHERE stage: Snowflake
            // filters the expanded rows, so a WHERE that removes a parent still keeps its children
            // (live-verified). Everything after this point sees an ordinary relation that also carries the
            // hidden LEVEL / CONNECT_BY_ROOT$<col> pseudo-columns.
            if (ctx.connectByClause() != null) {
                final ConnectByExpander hierarchy = new ConnectByExpander(this, functionRegistry, catalog);
                final Table hierarchyTable = hierarchy.expandedTable(table);
                plan.add(connectByStage(hierarchy, table, hierarchyTable, ctx.connectByClause(),
                    new FromClauseRelations(aliasToTable), new ArrayList<Table>(allTables)));
                // Re-point resolution at the expanded relation: the alias keeps naming the same rows, now
                // one row per hierarchy position and wider by the pseudo-columns.
                for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
                    if (entry.getValue() == table) {
                        entry.setValue(hierarchyTable);
                    }
                }
                for (int i = 0; i < allTables.size(); i++) {
                    if (allTables.get(i) == table) {
                        allTables.set(i, hierarchyTable);
                    }
                }
                table = hierarchyTable;
            }

            // Apply Row Access Policy (RLS) before WHERE — filters rows the user can't see
            if (table.hasRowAccessPolicy()) {
                plan.add(rowAccessPolicyStage(table));
            }

            // Apply WHERE clause. For a simple single-table SELECT ... LIMIT (no join / group / order /
            // distinct / window / aggregate / pivot), stream scan -> WHERE -> LIMIT so the predicate is
            // evaluated only until LIMIT is satisfied (LimitRowStream stops pulling) and the full filtered
            // set is never materialized. Projection is 1:1, so it runs afterwards on the <= LIMIT rows.
            final FrostlakeParser.WhereClauseContext whereCtx = getWhereClause(ctx);
            // When a (+) outer join consumed the WHERE, only its residual (non-(+)) predicates remain here
            // (the (+) join predicates already became the join's ON condition).
            final String whereExpr = plusHandled
                ? plusResidualWhere
                : (whereCtx != null ? getOriginalText(whereCtx.booleanExpr()) : null);
            StarArgumentPlacement.rejectOutsideSelectList(whereCtx, ctx.havingClause(), ctx.qualifyClause(),
                stmtCtx.orderByClause());
            boolean limitApplied = false;
            // A PIVOT/UNPIVOT reshapes the relation, and in Snowflake WHERE filters the pivot's OUTPUT (it may
            // reference the pivoted columns), so defer it until after the pivot below. isStreamableSelect
            // already excludes a pivot, so the streaming branch is unaffected.
            final boolean hasPivotSource = firstTableRef != null
                && (firstTableRef.pivotClause() != null || firstTableRef.unpivotClause() != null);
            // Plan-time WHERE scope validation — before either evaluation branch, so it fires over
            // empty inputs and under the streaming path alike. A pivot rewrites the referencable
            // columns, so its deferred WHERE is left to row-time resolution.
            // A name that resolves to no function at all is refused HERE, ahead of every clause rule
            // below AND ahead of the WHERE walk beside it, because that is the order live answers in —
            // see the method for the measured map of what outranks it and what it outranks. It runs
            // BEFORE the strict WHERE walk because that walk would otherwise stop at the FIRST bad name
            // it met, and live names every one of them in one sentence; the identifier half of the same
            // walk is run inside, so an unresolvable COLUMN in WHERE still speaks first.
            if (planTime) {
                rejectUnresolvableFunctionNames(stmtCtx, ctx, table, aliasToTable, allTables, onCompile);
            }
            // The SELECT LIST's names come first of all: live scans them ahead of every other clause,
            // so two bad names one clause apart report the LIST's. Measured against WHERE, HAVING,
            // QUALIFY and ORDER BY, each with a different name in each clause.
            if (!hasPivotSource && planTime) {
                validateSelectListNames(ctx, table, aliasToTable, allTables);
            }
            // A join condition's names come next, ahead of the WHERE's; its types wait for every clause's names.
            if (onCompile != null && onCompile.names() != null) {
                throw onCompile.names();
            }
            // The GROUP BY list's names and positions are judged with the select list's and the WHERE's names,
            // ahead of the WHERE's other rules and a join condition's types (see GroupKeyNames).
            if (ctx.groupByClause() != null && !hasPivotSource && planTime) {
                if (whereExpr != null) {
                    validateWhereScope(whereExpr, table, aliasToTable, allTables, ctx, true);
                }
                new GroupKeyNames(this).validate(ctx, table, aliasToTable, allTables);
            }
            // Every clause's names, the ORDER BY position and each subquery's names and types are settled
            // before this query's own types (see rejectAheadOfOwnTypes); a subquery's placement refusal waits.
            if (planTime && !hasPivotSource) {
                subqueryRefusal = firstRefusal(subqueryRefusal, rejectAheadOfOwnTypes(stmtCtx, ctx, whereExpr, table,
                    aliasToTable, allTables, lateralContext));
            }
            // The (+) markers are judged once every name has resolved and ahead of the query's types (see
            // OuterJoinMarkerRules); the conjuncts the legacy rewrite moved into its join have their names judged here.
            if (planTime && !hasPivotSource && whereCtx != null && containsOuterJoinMarker(whereCtx.booleanExpr())) {
                if (plusHandled) {
                    validateClauseColumnScope(getOriginalText(whereCtx.booleanExpr()), table, aliasToTable, allTables,
                        selectItemAliasNames(ctx), whereCtx.booleanExpr());
                }
                OuterJoinMarkerRules.validate(whereCtx.booleanExpr(), aliasToTable);
            }
            if (onCompile != null && onCompile.types() != null) {
                throw onCompile.types();
            }
            // A named call written with OVER in the select list is judged with the list's own walk, ahead of the
            // WHERE's types; one in another clause waits for them (see rejectNonWindowFunctionsWithOver).
            rejectNamedOverCalls(ctx, Collections.<ParseTree>singletonList(ctx.selectList()), table, aliasToTable,
                allTables);
            if (whereExpr != null && !hasPivotSource && planTime) {
                validateWhereScope(whereExpr, table, aliasToTable, allTables, ctx, false);
            }
            // A window function belongs to the SELECT list, QUALIFY and ORDER BY, and nowhere else.
            final PlanEcho echo =
                new PlanEcho(table, aliasToTable, allTables, functionRegistry, catalog);
            rejectNonWindowFunctionsWithOver(ctx, stmtCtx.orderByClause(), echo, table, aliasToTable, allTables);
            // The ordered percentiles' own compile-time rules — the WITHIN GROUP clause's shape, the
            // fraction's constancy and range, and the call's arity.
            new PercentileCallRules(this, functionRegistry, catalog)
                .validate(stmtCtx, table, aliasToTable, allTables);
            // The approximate summaries' constant limits and fractions, judged the same way.
            new ApproximateCallRules(functionRegistry, catalog)
                .validate(stmtCtx, table, aliasToTable, allTables);
            rejectWindowFunctionsOutsideAllowedClauses(ctx, echo,
                ctx.groupByClause() != null || aggregatesAnywhere(stmtCtx, ctx));
            if (SubqueryCompilation.compiles(lateralContext)) {
                new WindowCorrelationRule(table, aliasToTable, allTables, lateralContext).reject(stmtCtx, ctx, echo);
            }
            rejectAggregatesOutsideAllowedClauses(ctx, echo, table, aliasToTable, allTables, lateralContext);
            if (stmtCtx.orderByClause() != null && ctx.groupByClause() != null) {
                rejectWindowInGroupedOrderBy(stmtCtx.orderByClause(), echo, ctx, table, aliasToTable);
            }
            // The SELECT list's and ORDER BY list's own NAMES, resolved BEFORE the positional range
            // check below: live settles what every reference means before it judges a position, so
            // `SELECT nosuchcol FROM t ORDER BY 9` is "invalid identifier 'NOSUCHCOL'" there rather
            // than the out-of-range sentence, and so is a bad name in the ORDER BY list itself.
            if (!hasPivotSource && planTime) {
                validateNamesBeforeOrdinals(ctx, stmtCtx, table, aliasToTable, allTables);
            }
            // Below the names, because the sentence it raises echoes a RESOLVED plan and live orders it
            // the same way: an unresolvable column inside the very call it would echo speaks first.
            rejectWholeRangeFrameForWholePartitionAggregate(ctx, stmtCtx, echo, table, aliasToTable,
                allTables);
            // A positional ORDER BY key past the select list is a COMPILE-time refusal live, whatever
            // the query's shape — the per-shape sort paths each resolve ordinals their own way (and a
            // grouped or star-projected one did not range-check at all), so the range is judged once,
            // here, where the relation is known and the rows are not yet built.
            if (!isEarlierSetOperationArm(stmtCtx, ctx)) {
                orderByExecutor.validateOrderOrdinals(stmtCtx, table, aliasToTable);
            }
            // QUALIFY's own NAMES, resolved here rather than beside the clause's other rules further
            // down, because live settles what a reference means before it judges anything about the
            // clause it sits in: a bad name outranks BOTH the no-window refusal and the grouped
            // select-list rule that a QUALIFY aggregate triggers. Down there it lost to both.
            // It sits BELOW the ordinal, though — QUALIFY and HAVING are the two clauses live resolves
            // after the position, so an out-of-range ORDER BY beats a bad name in either of them.
            // HAVING before QUALIFY, measured: a bad name in each reports HAVING's.
            if (ctx.havingClause() != null && !hasPivotSource && planTime) {
                validateClauseColumnScope(getOriginalText(ctx.havingClause().booleanExpr()),
                    table, aliasToTable, allTables, selectItemAliasNames(ctx),
                    ctx.havingClause().booleanExpr());
                if (ctx.groupByClause() == null && !aggregatesAnywhere(stmtCtx, ctx)) {
                    validateWhereScope(getOriginalText(ctx.havingClause().booleanExpr()), table, aliasToTable,
                        allTables, ctx, true, ctx.havingClause().booleanExpr());
                }
            }
            if (ctx.qualifyClause() != null && !hasPivotSource && planTime) {
                validateClauseScope(getOriginalText(ctx.qualifyClause().booleanExpr()),
                    table, aliasToTable, allTables, selectItemAliasNames(ctx),
                    ctx.qualifyClause().booleanExpr());
            }
            // The predicate TYPES, once every clause's names are settled and in live's own order: the
            // WHERE's, then the HAVING's, then the QUALIFY's, each judged from the select list's aliases
            // too, so a bare alias is judged by what it PROJECTS — SELECT a AS z FROM t WHERE z is
            // "Invalid data type [NUMBER(38,0)] for predicate [Z]" whatever the rows hold. A join
            // condition's own type spoke earlier, where the join was reached. The WHERE's comes ahead of
            // the grouped select list, and the HAVING's ahead of the implicit grouping it would
            // otherwise be read against (live-verified, each measured against the other's clause).
            if (whereExpr != null && whereCtx != null && !plusHandled && !hasPivotSource && planTime) {
                validateClausePredicateType(whereExpr, table, aliasToTable, allTables, ctx,
                    whereCtx.booleanExpr());
            }
            if (ctx.havingClause() != null && !hasPivotSource && planTime) {
                validateClausePredicateType(getOriginalText(ctx.havingClause().booleanExpr()),
                    table, aliasToTable, allTables, ctx, ctx.havingClause().booleanExpr());
            }
            if (ctx.qualifyClause() != null && !hasPivotSource && planTime) {
                // Ahead of the deferred no-window refusal: live settles that QUALIFY g over a VARCHAR is
                // no predicate before it looks for a window in the statement.
                validateClausePredicateType(getOriginalText(ctx.qualifyClause().booleanExpr()),
                    table, aliasToTable, allTables, ctx, ctx.qualifyClause().booleanExpr());
            }
            // A correlated subquery live cannot evaluate is refused by the plan, whether or not a row reaches it.
            final CorrelationPlanJudgement correlations = planTime && !hasPivotSource
                ? CorrelationPlanJudgement.of(this, stmtCtx, ctx, table, aliasToTable, lateralContext) : null;
            if (correlations != null) {
                final RuntimeException correlated = correlations.refusal();
                // A QUALIFY with no window function is reported ahead of it (see rejectWindowlessQualifyBeforeRows).
                if (!qualifiesWithoutWindow(ctx)) {
                    subqueryRefusal = firstRefusal(subqueryRefusal, correlated);
                }
                displacedCorrelations = PlannedCorrelations.begin(correlations.refusedKeys());
                plannedCorrelations = true;
            }
            // A scalar subquery may not correlate to a table function's output unless it aggregates —
            // plan-time, because live reports it as a compilation error rather than a row-time one.
            if (planTime) {
                new TableFunctionCorrelationRule(functionRegistry, catalog)
                    .validate(ctx, tableExpr, aliasToTable);
            }
            // A join condition that did not compile joined no rows, so whatever of its refusal nothing above
            // raised is raised before any row is produced.
            if (onCompile != null) {
                onCompile.rejectWaiting();
            }
            if (correlations != null && correlations.prunesWhere()) {
                plan.add(emptyRelation("correlated subquery refused"));
            } else if (lateralContext == null && firstTableRef != null
                    && isStreamableSelect(ctx, stmtCtx, firstTableRef, allTables)) {
                plan.add(planStreamFilterLimit(table, whereExpr, stmtCtx.limitClause()));
                limitApplied = true;
            } else if (whereExpr != null && !hasPivotSource) {
                plan.add(planWhere(table, whereExpr, lateralContext, aliasToTable, allTables, tableExpr, ctx));
            }

            // Check for PIVOT/UNPIVOT on the first table reference (they follow the source + optional alias).
            // The pivoted relation becomes this query's SOURCE, so everything after this point — the SELECT
            // list, GROUP BY / HAVING, QUALIFY, DISTINCT, ORDER BY, TOP / LIMIT / FETCH — applies to it, as in
            // Snowflake. Returning the pivoted ResultSet straight from here silently DISCARDED all of them:
            // `SELECT c FROM t PIVOT(…)` handed back the whole pivoted relation, and a derived column such as
            // `CASE … END AS record_type` never existed for an outer query to reference.
            final FrostlakeParser.TableSourceContext firstSource = firstTableRef == null
                ? null : firstTableRef.tableSource();
            if (hasPivotSource) {
                final String pivotName = firstTableRef.pivotAlias() != null
                    ? getIdentifier(firstTableRef.pivotAlias().identifier())
                    : table.getName();
                final FrostlakeParser.PivotClauseContext pivotClause = firstTableRef.pivotClause();
                // IN (ANY) takes its column list from the FOR column's distinct values, resolved while planning
                // as the account resolves them: the stages before the pivot run now, the pivot itself in the
                // pipeline. Every other pivot knows its columns from its clause alone.
                final boolean valuesFromRows = pivotClause != null && pivotClause.pivotInList().ANY() != null;
                final PlannedRelation pivoted = pivotClause != null
                    ? planPivot(table, pivotClause, firstTableRef.pivotAlias(), pivotName,
                        valuesFromRows ? plan.materialize() : null)
                    : planUnpivot(table, firstTableRef.unpivotClause(), firstTableRef.pivotAlias(), pivotName);
                if (valuesFromRows) {
                    plan.addResolvedWhilePlanning(pivoted.stage);
                } else {
                    plan.add(pivoted.stage);
                }
                table = pivoted.table;
                // Re-point column resolution at the pivot output; its alias (PIVOT(…) AS p) is the only name
                // by which a qualified reference such as p.region can reach it.
                aliasToTable.clear();
                aliasToTable.put(pivotName, table);
                allTables.clear();
                allTables.add(table);
                if (whereExpr != null) {
                    plan.add(planWhere(table, whereExpr, lateralContext, aliasToTable, allTables, tableExpr, ctx));
                }
            }
            // A HAVING in a query that groups nothing filters the rows the WHERE kept, before anything is computed
            // from them (see havingAggregates).
            if (ctx.havingClause() != null && ctx.groupByClause() == null && !aggregatesAnywhere(stmtCtx, ctx)) {
                plan.add(planUngroupedHaving(ctx, table, lateralContext, aliasToTable, allTables, tableExpr));
            }

            // Check if SELECT contains aggregate functions or window functions. A HAVING that aggregates also makes
            // this an aggregate query even when no SELECT item is one (SELECT 'X' FROM t HAVING COUNT(*) > 1):
            // Snowflake forms a single implicit group, so such a query returns at most one row. Detecting
            // aggregates from the SELECT list alone left it a plain row scan AND then skipped applyHaving
            // entirely (see the guard below), so every input row was returned unfiltered.
            // An aggregate ANYWHERE makes the query aggregate — the select list, HAVING, and equally an
            // ORDER BY key or a QUALIFY predicate: live answers `SELECT a FROM g ORDER BY SUM(b)` with
            // "[G.A] is not a valid group by expression", which is the ungrouped SELECT LIST being
            // refused rather than any complaint about the aggregate's place.
            // A window call inside SYSTEM$TYPEOF is typed rather than computed, but its arguments are
            // still judged at plan time: SYSTEM$TYPEOF(RATIO_TO_REPORT(d) OVER ()) over a DATE is
            // "Invalid argument types for function 'SUM': (DATE)" on the account.
            if (planTime && !hasPivotSource) {
                final List<FrostlakeParser.FunctionCallExprContext> typeofWindowCalls = new ArrayList<>();
                for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
                    if (SelectItemAccessors.isExprItem(item)) {
                        windowEvaluator.collectTypeofWindowFunctionCalls(
                            SelectItemAccessors.getItemValueExpr(item), typeofWindowCalls);
                    }
                }
                if (!typeofWindowCalls.isEmpty()) {
                    windowEvaluator.rejectFileWindowArguments(typeofWindowCalls, table, selectItemAliasNames(ctx));
                }
            }
            // The clause's statistics bound, which a typeof over COUNT, MIN or MAX reads; decided on first
            // use, so no other query reads the table for it. A derived relation's own rows are what the
            // statistics of the table beneath it are proven over.
            final Table clauseSource = table;
            if (tableExpr != null && !hasPivotSource) {
                statisticsBound.set(new CountStatisticsBound(this, ctx, table, aliasToTable, whereExpr,
                    allTableRefs.size() > 1 || !allJoins.isEmpty(),
                    table.isCatalogResident() ? null : tableData.rows));
            }
            final boolean selectListAggregates = hasAggregateFunction(ctx);
            // A select list whose aggregates ALL sit inside SYSTEM$TYPEOF is an aggregate query for
            // validation — a bare column beside SYSTEM$TYPEOF(SUM(c)) is "[T.C] is not a valid group by
            // expression" on the account — but the typeof folds to a constant there and the aggregate
            // it wraps is never computed, so the query runs as a SCAN, one row per input row, unless
            // the account could have answered that aggregate from statistics (see
            // WindowFunctionEvaluator.typeofAggregatesFoldToScan). A lone BASE TABLE holding no rows
            // at all still answers one row, as an aggregate over nothing does — its emptiness is
            // metadata there — where a scan that keeps nothing (a filter, a derived table, a join
            // with an empty side) answers nothing.
            final boolean typeofFoldsToScan = selectListAggregates && ctx.groupByClause() == null
                && ctx.havingClause() == null && !aggregatesInOrderByOrQualify(stmtCtx, ctx)
                && !hasPivotSource && lateralContext == null
                && windowEvaluator.typeofAggregatesFoldToScan(ctx, table)
                && !(tableData.rows.isEmpty() && whereExpr == null && tableExpr != null
                    && tableExpr.tableReference().size() == 1 && tableExpr.joinClause().isEmpty()
                    && firstSource != null && firstSource.tableQualifiedName() != null);
            if (typeofFoldsToScan) {
                groupByEvaluator.validateImplicitSelectList(ctx, table, aliasToTable, allTables);
            }
            final boolean hasAggregates = (selectListAggregates && !typeofFoldsToScan)
                || havingAggregates(ctx) || aggregatesInOrderByOrQualify(stmtCtx, ctx);
            // A window call written ONLY in ORDER BY — never in the SELECT list and never in QUALIFY —
            // still needs the window stage. Without it the key reached the column resolver as plain
            // text and was refused as an invalid identifier, though live sorts by it perfectly well.
            final boolean hasWindowFunctions = hasWindowFunction(ctx)
                || (stmtCtx.orderByClause() != null
                    && windowEvaluator.hasWindowFunctionInTree(stmtCtx.orderByClause()));

            // Apply GROUP BY or handle implicit grouping for aggregates. Capture each output row's source
            // group so HAVING can evaluate aggregates that are not SELECT items over the original group.
            final boolean hasGroupBy = ctx.groupByClause() != null;
            // An aggregation policy decides HERE whether the query may run at all: the shape is known
            // (grouped / aggregated / DISTINCT) and the source table is in hand.
            rejectAggregationPolicyViolation(ctx, table, hasGroupBy || hasAggregates
                || ctx.DISTINCT() != null);
            rejectJoinPolicyViolation(ctx, allTables);
            // Every aggregate item's DECLARED type, before any group is computed: the type can itself
            // be the refusal — MEDIAN over a NUMBER(38,37) is "Invalid intermediate datatype:
            // NUMBER(41,40)." at compile time on the account, ahead of the value it would then fail
            // to hold, and over an empty input too.
            if ((hasGroupBy || hasAggregates) && !hasPivotSource && planTime) {
                rejectUntypeableAggregateItems(ctx, table, aliasToTable, allTables);
            }
            if (!hasPivotSource && planTime) {
                rejectWindowlessQualifyBeforeRows(ctx, table, aliasToTable, allTables, hasGroupBy, hasAggregates,
                    subqueryRefusal, lateralContext);
            }
            final List<List<Row>> havingGroupRows = new ArrayList<>();
            // Each output row instance to its source group, filled by the grouping stage, so a later ORDER BY
            // key or QUALIFY window key that is a grouped column / aggregate NOT in the SELECT can be computed
            // fresh over the group. Identity-keyed so it survives HAVING filtering (the same Row instances
            // flow through); the window projection does NOT preserve identity, so it re-registers its own rows.
            final Map<Row, List<Row>> orderRowToGroup = new IdentityHashMap<>();
            // A HAVING written of constants alone is settled before any row is grouped (see ConstantHaving).
            if ((hasGroupBy || hasAggregates) && ctx.havingClause() != null && ConstantHaving.isConstant(
                    ExpressionEvaluator.parse(getOriginalText(ctx.havingClause().booleanExpr())), functionRegistry)) {
                plan.add(constantHavingStage(ctx, hasGroupBy, table, aliasToTable, allTables, lateralContext));
            }
            if (hasGroupBy) {
                plan.add(groupStage(groupByEvaluator.planGroupBy(table, ctx, aliasToTable, allTables, havingGroupRows,
                    lateralContext), havingGroupRows, orderRowToGroup));
            } else if (hasAggregates) {
                // No GROUP BY but has aggregates - treat entire result as one group
                plan.add(implicitGroupStage(groupByEvaluator.planImplicitGroupBy(table, ctx, aliasToTable, allTables,
                    havingGroupRows, lateralContext), ctx, table, ctx.havingClause() == null && lateralContext == null,
                    havingGroupRows, orderRowToGroup));
            }

            // Plan-time HAVING scope validation. The clause is the one place a reference could still
            // reach row time unchecked: GroupByOperator answers NULL for a group it cannot evaluate,
            // so an unresolvable name filtered every group out instead of refusing. Live refuses it
            // at COMPILE time, at the reference's own offset, over an empty input too.
            if (ctx.havingClause() != null && planTime) {
                validateClauseScope(getOriginalText(ctx.havingClause().booleanExpr()),
                    table, aliasToTable, allTables, selectItemAliasNames(ctx),
                    ctx.havingClause().booleanExpr());
                validateClausePredicateType(getOriginalText(ctx.havingClause().booleanExpr()),
                    table, aliasToTable, allTables, ctx, ctx.havingClause().booleanExpr());
            }

            // Apply HAVING clause (only after GROUP BY)
            if (ctx.havingClause() != null && (hasGroupBy || hasAggregates)) {
                plan.add(planHaving(ctx, table, aliasToTable, allTables, havingGroupRows, lateralContext));
            }

            // The deferred QUALIFY-without-a-window refusal: an aggregate query reaches it only once the
            // grouped select list has passed, which is the order live answers in.
            rejectQualifyWithoutWindow(ctx);

            // Plan-time QUALIFY scope validation (the clause itself filters later, against window
            // results). Window CALLS are opaque text nodes, so references inside an OVER clause are
            // not walked — only the predicate around them.
            if (ctx.qualifyClause() != null && planTime) {
                validateClauseScope(getOriginalText(ctx.qualifyClause().booleanExpr()),
                    table, aliasToTable, allTables, selectItemAliasNames(ctx),
                    ctx.qualifyClause().booleanExpr());
                validateWindowKeyScope(ctx.qualifyClause().booleanExpr(), table, aliasToTable,
                    allTables, selectItemAliasNames(ctx));
            }

            // The window stage computes the window functions and stores their results. Over a JOIN it publishes
            // the alias context so its per-key expression evaluation resolves alias-qualified columns (see
            // windowAliasToTableStack); a single-table query needs no alias context and is left untouched.
            // ORDER BY keys a window-function query references but does NOT select: the projection in
            // addWindowFunctionsToRows drops the FROM columns, so precompute those keys THERE (the FROM
            // columns are still on the source row), keyed by the projected row so they survive QUALIFY, and
            // resolve ORDER BY from them below.
            final List<String> windowOrderKeyExprs = new ArrayList<>();
            // The same keys as PARSE TREES, index-aligned: a key carrying a window call has to be
            // computed by the window stage, and only the tree says whether it does.
            final List<FrostlakeParser.ExpressionContext> windowOrderKeyTrees = new ArrayList<>();
            final Map<String, Integer> windowOrderKeyIndex = new HashMap<>();
            final Map<Row, Object[]> windowOrderKeyValues = new IdentityHashMap<>();
            if (hasWindowFunctions && stmtCtx.orderByClause() != null && stmtCtx.selectOperand().size() == 1) {
                for (final FrostlakeParser.OrderItemContext oi : orderByExecutor.unmatchedOrderItems(stmtCtx)) {
                    final String txt = getOriginalText(oi.expression());
                    if (!windowOrderKeyIndex.containsKey(txt)) {
                        windowOrderKeyIndex.put(txt, windowOrderKeyExprs.size());
                        windowOrderKeyExprs.add(txt);
                        windowOrderKeyTrees.add(oi.expression());
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
                // The projected shape serves BOTH the select-list window stage and a QUALIFY whose
                // windows are written inline: over grouped rows either must resolve names (and raw
                // aggregates like SUM(x) in a window key) against the SELECT-list layout.
                final Table windowShape = rowsAreProjected
                    && (hasWindowFunctions || ctx.qualifyClause() != null)
                    ? projectedShapeTable(ctx, table, aliasToTable, allTables) : table;
                if (hasWindowFunctions) {
                    // The select list's OVER keys, before any row is computed. The walk runs against
                    // the BASE table even over grouped rows, which is what makes the grouped shapes
                    // safe: a key may legally be a GROUP BY key that the SELECT list never projects,
                    // or a RAW aggregate over a non-key column (OVER (ORDER BY MAX(c))), and both
                    // resolve against the base relation. Only the SELECT aliases need exempting.
                    if (planTime) {
                        validateWindowKeyScope(ctx.selectList(), table, aliasToTable, allTables,
                            selectItemAliasNames(ctx));
                        if (stmtCtx.orderByClause() != null) {
                            // A window written in ORDER BY is checked like any other: its keys are
                            // walked, so a name that resolves to nothing is refused at the reference
                            // rather than sorted by silently.
                            validateWindowKeyScope(stmtCtx.orderByClause(), table, aliasToTable,
                                allTables, selectItemAliasNames(ctx));
                        }
                    }
                    // AFTER the keys are resolved, because live reports an unresolvable key FIRST:
                    // OVER (PARTITION BY nosuchcol) with no ORDER BY answers "invalid identifier
                    // 'NOSUCHCOL'", not the missing-ORDER-BY sentence (measured both ways round).
                    windowEvaluator.rejectWindowWithoutRequiredOrderBy(ctx);
                }

                // Plan-time scope validation of a GROUPED / windowed query's ORDER BY keys, positioned
                // between the two families of refusal that surround it. AFTER the window-key walk and
                // the missing-ORDER-BY refusal, because live reports either of those first. BEFORE the
                // window stage, because that stage precomputes every unselected key per row: left later,
                // its row-time failure spoke first and said "invalid identifier" for a key this check
                // refuses in live's own words — and only over a NON-EMPTY input, so the same query
                // refused or answered depending on the data.
                if (hasGroupBy || hasAggregates || hasWindowFunctions) {
                    if (planTime) {
                        orderByExecutor.validateGroupedOrderKeyScope(stmtCtx, ctx, table, aliasToTable,
                            allTables);
                    }
                }
                // DISTINCT narrows the ORDER BY to the SELECT list's own output, whatever else the
                // query is. It is judged AFTER the checks above because live names an unresolvable
                // key first, and before the window stage for the same reason they are.
                if (planTime) {
                    // A grouped or distinct query's ORDER BY holds no subquery at all, whatever it reads —
                    // ahead of the DISTINCT rule, which would otherwise name the subquery's own aggregate.
                    orderByExecutor.rejectSubqueryInGroupedOrderKey(stmtCtx, ctx, table, aliasToTable,
                        allTables);
                    orderByExecutor.validateDistinctOrderKeyScope(stmtCtx, ctx, table, aliasToTable,
                        allTables);
                }

                // Over grouped rows a window's keys and arguments may be RAW AGGREGATES the SELECT list
                // never projects — OVER (ORDER BY SUM(b)), LAG(SUM(b)) — and the only place their values
                // exist is the group each output row came from. An ORDER BY key the SELECT list never
                // projects is that same shape, so one resolver serves both stages.
                final GroupedExpressionValues groupValues = rowsAreProjected
                    ? newGroupedExpressionValues(orderRowToGroup, table, aliasToTable, allTables) : null;
                if (hasWindowFunctions && !rowsAreProjected && planTime) {
                    // A windowed query is projected by the window stage, never by applyProjection, so the
                    // plan-time argument walk that method runs has to run here, before any row is
                    // evaluated — or an arity or argument-type refusal in any item is raised per row,
                    // unpositioned, and over an empty input not at all.
                    validateWindowedSelectArguments(ctx, table, aliasToTable, allTables);
                }
                if (hasWindowFunctions) {
                    plan.add(windowStage(ctx, table, windowShape, rowsAreProjected, groupValues, orderRowToGroup,
                        windowOrderKeyExprs, windowOrderKeyTrees, windowOrderKeyValues,
                        pushJoinAliasContext ? aliasToTable : null, allTables));
                } else if (ctx.qualifyClause() != null) {
                    // A QUALIFY with no window in the select list filters the rows as they are: SELECT-shape over
                    // grouped rows, FROM-shape otherwise. Its inline windows are computed by the stage itself.
                    plan.add(qualifyStage(ctx, rowsAreProjected ? windowShape : table, table, !rowsAreProjected,
                        groupValues, pushJoinAliasContext ? aliasToTable : null, allTables));
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
            final boolean isPartOfUnion = stmtCtx.selectOperand().size() > 1;
            // Projection is needed unless the SELECT list is a bare star or grouping/window functions have
            // already reshaped the rows into SELECT-list form. A CONNECT BY relation carries hidden
            // pseudo-columns and a USING / NATURAL join's relation carries hidden key duplicates, so even
            // a bare star must be projected there — the pass-through would hand back rows that are wider
            // than the star's column list.
            // A bare star projects every column, so its restriction check cannot wait for the
            // projection step — that step is skipped for exactly this shape.
            if (isSimpleStar(ctx)) {
                rejectRestrictedStar(ctx, table, aliasToTable);
            }
            final boolean needsProjection = (!isSimpleStar(ctx) || ctx.connectByClause() != null
                    || hasHiddenStarColumns(table))
                && !hasWindowFunctions && !hasGroupBy && !hasAggregates;
            // An aggregation policy folds DISTINCT the way it folds GROUP BY, and its ENTITY KEY
            // counts columns the projection may drop — so the rows going INTO the projection are kept,
            // index-aligned with the rows coming out.
            final List<List<Row>> preProjectionRows = new ArrayList<>();
            if (ctx.DISTINCT() != null && minimumGroupSize(table) > 0) {
                plan.add(snapshotStage(preProjectionRows));
            }
            if (isPartOfUnion) {
                if (needsProjection) {
                    plan.add(projectionStage(planProjection(table, ctx, aliasToTable, allTables, lateralContext),
                        preProjectionRows));
                }
            } else {
                boolean orderByApplied = false;
                boolean sortProjectedOutput = false;

                // If we need projection and there's ORDER BY, apply ORDER BY first (before projection)
                // so that ORDER BY can reference all columns from the FROM clause — UNLESS every key
                // matches a SELECT output column: then the sort runs AFTER projection over the output
                // values, so each item evaluates exactly once (a side-effecting item like seq.NEXTVAL
                // must not be drawn again as a sort key; live numbers ORDER BY 1 rows consecutively).
                if (needsProjection && stmtCtx.orderByClause() != null) {
                    if (orderByExecutor.allOrderKeysMatchOutput(stmtCtx)) {
                        // The key-TYPE rejections stay plan-time on this route too.
                        orderByExecutor.validateOrderKeyTypes(stmtCtx, table, aliasToTable, allTables);
                        sortProjectedOutput = true;
                    } else {
                        plan.add(orderByExecutor.planOrderBy(table, stmtCtx, aliasToTable, allTables));
                        orderByApplied = true;
                    }
                }

                // The projection
                if (needsProjection) {
                    plan.add(projectionStage(planProjection(table, ctx, aliasToTable, allTables, lateralContext),
                        preProjectionRows));
                    if (sortProjectedOutput) {
                        plan.add(outputOrderByStage(stmtCtx, table, aliasToTable));
                        orderByApplied = true;
                    }
                }

                // Apply ORDER BY after GROUP BY/aggregates/window functions if not already applied
                if (!orderByApplied && stmtCtx.orderByClause() != null) {
                    // If we've done GROUP BY, aggregates, or window functions,
                    // the row structure matches the SELECT list, not the original table
                    if (hasGroupBy || hasAggregates || hasWindowFunctions) {
                        // Let ORDER BY reference a key that is NOT in the SELECT list (valid in Snowflake).
                        // Whenever a window ran, its projection is what dropped the FROM columns, so the
                        // key was precomputed THERE (windowOrderKeyValues) and is looked up by
                        // projected-row identity — over grouped rows too, where that precompute answered
                        // the key from the row's own source group. Without a window the key is computed
                        // over that group here instead (the identity map still holds — nothing recreated
                        // the rows).
                        if (hasAggregates && !hasGroupBy
                                && windowEvaluator.hasWindowFunctionInTree(stmtCtx.orderByClause())) {
                            // Implicit aggregation answers exactly ONE row, so no ordering of it can
                            // change anything — live accepts a window key here, and sorting is a no-op.
                            orderByApplied = true;
                        } else {
                            plan.add(groupedOrderByStage(stmtCtx, ctx, table, aliasToTable, allTables,
                                hasWindowFunctions, windowOrderKeyValues, windowOrderKeyIndex, orderRowToGroup));
                        }
                    } else {
                        plan.add(orderByExecutor.planOrderBy(table, stmtCtx, aliasToTable, allTables));
                    }
                }

                // Apply TOP clause (after ORDER BY). TOP beside a LIMIT or FETCH is refused with
                // live's own sentence, naming the SECOND clause's keyword.
                if (ctx.topClause() != null) {
                    if (stmtCtx.limitClause() != null || stmtCtx.fetchClause() != null) {
                        throw new RuntimeException(SqlCompilationError.of("Duplicate LIMIT: "
                            + (stmtCtx.limitClause() != null ? "LIMIT" : "FETCH") + "."));
                    }
                    plan.add(new LimitOperator(Integer.parseInt(ctx.topClause().INTEGER_LITERAL().getText())));
                }

                // LIMIT with optional OFFSET, read here so a refused clause is refused before any row.
                if (!limitApplied && stmtCtx.limitClause() != null) {
                    final int[] window = limitWindow(stmtCtx.limitClause());
                    plan.add(new LimitOperator(window[0], window[1]));
                }

                // FETCH (with its optional ANSI OFFSET prefix)
                if (stmtCtx.fetchClause() != null) {
                    final int[] window = fetchWindow(stmtCtx.fetchClause());
                    plan.add(new LimitOperator(window[0], window[1]));
                }
            }

            // Build result columns
            final List<ResultSetColumn> columns = new ArrayList<>();
            // Each column's select item as written, which tells a derived relation's COUNT whether the
            // column passes a stored column through (see DerivedStatistics).
            final List<String> columnSources = new ArrayList<>();
            // The static type of each projected column, so a derived table / CTE / view built from this
            // result set carries REAL declared types into the enclosing query rather than the VARCHAR
            // placeholder below — that placeholder is why every type-based rule could be bypassed by
            // wrapping the query one level deeper. Built once for the whole select list.
            final ExpressionEvaluator projectionTypes =
                new ExpressionEvaluator(table, functionRegistry, catalog, this);
            projectionTypes.setMultiTableContext(aliasToTable, allTables);
            // A later item may read an EARLIER item's alias (SELECT a AS a2, a2 + 0) and is typed from it,
            // as live types it; a name the relations carry still means the column, and a LATER alias is
            // not in scope at all (live refuses it as an invalid identifier).
            final Map<String, DataType> earlierAliasTypes = new HashMap<>();
            projectionTypes.setOutputAliasTypes(earlierAliasTypes);
            // Handle select items - iterate through all items, expanding STAR if present
            for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
                if (SelectItemAccessors.isStarItem(item)) {
                    // Expand STAR (honoring EXCLUDE/RENAME/REPLACE/ILIKE) to its effective columns.
                    for (final StarColumn sc : expandStarColumns(item, starOrderedColumns(table), "")) {
                        columns.add(projectedColumn(sc.getOutputName(), sc.getDataType(), tableName,
                            sc.getExpression(), projectionTypes, sc.isNullable(),
                            sc.isNullabilityKnown()));
                        columnSources.add(sc.getExpression());
                    }
                } else if (SelectItemAccessors.isQualifiedStarItem(item)) {
                    // Expand t.* to all columns of the referenced table/alias
                    final String[] qualifierParts = SelectItemAccessors.getItemQualifierParts(item);
                    final String qualifier = SelectItemAccessors.getItemQualifierSpelled(item);
                    for (final Table t : allTables) {
                        // The alias this table was given, or its own name when it has none. An explicit
                        // loop rather than a stream: this codebase uses neither streams nor lambdas.
                        String alias = t.getName();
                        for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
                            if (entry.getValue() == t) {
                                alias = entry.getKey();
                                break;
                            }
                        }
                        if (starQualifierMatches(qualifierParts, alias, t, isAliasKey(aliasToTable, alias))) {
                            // Its modifiers apply exactly as they do to a plain star: EXCLUDE, ILIKE, REPLACE
                            // and RENAME over the one relation the qualifier names (live-verified).
                            for (final StarColumn sc : expandStarColumns(item, t.getColumns(), qualifier + ".")) {
                                columns.add(projectedColumn(sc.getOutputName(), sc.getDataType(), t.getName(),
                                    sc.getExpression(), projectionTypes, sc.isNullable(),
                                    sc.isNullabilityKnown()));
                                columnSources.add(sc.getExpression());
                            }
                            break;
                        }
                    }
                } else if (SelectItemAccessors.isObjectStarItem(item)) {
                    // One OBJECT-valued column, labelled with the item's own source form ({* EXCLUDE (A)}).
                    columns.add(new ResultSetColumn(SelectItemAccessors.objectStarLabel(item),
                        ObjectType.OBJECT, null));
                    columnSources.add(null);
                } else {
                    // Handle expression select item — the output name/type come from the parse tree.
                    final FrostlakeParser.ExpressionContext valueExpr = SelectItemAccessors.getItemValueExpr(item);
                    final boolean hasAlias = SelectItemAccessors.getItemAlias(item) != null;

                    // Column name: an explicit alias wins; otherwise a (qualified) column reference yields
                    // its column name and any other expression its source text folded to upper case.
                    String colName = hasAlias
                        ? (SelectItemAccessors.getItemAlias(item))
                        : unaliasedItemName(item);

                    // Data type: COUNT/SUM/AVG → BIGINT; a column reference → the table column's type.
                    DataType colType = StringType.VARCHAR;
                    // A NOT NULL travels out of the projection only under a COLUMN REFERENCE — an
                    // expression over the same column, a cast of it, an aggregate and a literal all
                    // accept NULL (live-verified).
                    boolean colNullable = true;
                    // ... and only a reference that RESOLVED knows its answer, which is the half the
                    // JDBC driver reports (an expression column reads columnNoNulls there).
                    boolean colNullabilityKnown = false;
                    // Parentheses are TRANSPARENT around a column reference at any depth: live gives
                    // (v), ((v)) and (t.v) the column's own name, canonical type and NOT NULL, exactly
                    // as the bare reference gets them.
                    final FrostlakeParser.ExpressionContext columnExpr =
                        valueExpr == null ? null : SelectItemAccessors.unwrapParens(valueExpr);
                    if (isNumericAggregateItem(valueExpr)) {
                        colType = NumericType.BIGINT;
                    } else if (columnExpr instanceof FrostlakeParser.QualifiedNameExprContext) {
                        final String baseColName = selectItemColumnName(columnExpr);
                        final int positional = positionalOrdinalOf(baseColName);
                        if (table.hasColumn(baseColName)) {
                            final int colIndex = table.getColumnIndex(baseColName);
                            colType = table.getColumns().get(colIndex).getDataType();
                            colNullable = table.getColumns().get(colIndex).isNullable();
                            colNullabilityKnown = true;
                            // An unaliased, unqualified simple column takes the table's properly-cased name.
                            final boolean unqualified = ((FrostlakeParser.QualifiedNameExprContext) columnExpr)
                                .qualifiedName().namePart().isEmpty();
                            if (!hasAlias && unqualified) {
                                colName = table.getColumns().get(colIndex).getName();
                            }
                        } else if (positional > 0 && (allTables == null || allTables.size() <= 1)
                                && RelationSlot.at(table, positional) != null) {
                            // $N is the Nth column of a single-relation source BY POSITION, and is
                            // typed as that column is: SELECT $1 FROM VALUES (TRUE) declares BOOLEAN
                            // live. It keeps its dollar name, as live names it. Past a staged file's
                            // fields it reads NULL, typed as the fields are.
                            final RelationSlot own = RelationSlot.at(table, positional);
                            colType = own.getDataType();
                            colNullable = own.isPastFields() || table.getColumns().get(own.getColumn()).isNullable();
                            colNullabilityKnown = true;
                        } else if (positional > 0 && allTables != null && allTables.size() > 1) {
                            // Over several relations $N (or t.$N) is typed as the column it reads there.
                            final RelationSlot slot = positionalSlot(
                                ((FrostlakeParser.QualifiedNameExprContext) columnExpr).qualifiedName(), positional,
                                table, aliasToTable, allTables);
                            if (slot != null && slot.getDataType() != null) {
                                colType = slot.getDataType();
                            }
                        }
                    }

                    // A boolean projection (SELECT a AND b) has no value expression — the whole item IS
                    // the boolean, so its own text is what carries the type.
                    final ParserRuleContext typeSource = valueExpr != null
                        ? valueExpr : SelectItemAccessors.getItemExpression(item);
                    final ResultSetColumn itemColumn = projectedColumn(colName, colType, null,
                        typeSource != null ? getOriginalText(typeSource) : null, projectionTypes,
                        colNullable, colNullabilityKnown, typeSource);
                    columns.add(itemColumn);
                    columnSources.add(typeSource != null ? getOriginalText(typeSource) : null);
                    if (hasAlias && itemColumn.getStaticType() != null) {
                        earlierAliasTypes.put(colName.toUpperCase(Locale.ROOT), itemColumn.getStaticType());
                    }
                }
            }

            if (columns.isEmpty()) {
                // A star over a zero-column source (e.g. GENERATOR) — Snowflake's error shape; the
                // pass-through star path skips applyProjection, so the guard lives here too.
                throw noColumns(ctx);
            }

            // Apply DISTINCT if specified
            // A projected column carries the collation of the expression it projects, so a derived
            // relation, a view or a CTAS over this result compares it under that collation.
            final CollationSpec[] projected = projectionCollations(columnSources, table, aliasToTable, allTables);
            stampCollations(columns, projected);
            if (ctx.DISTINCT() != null) {
                plan.add(distinctStage(table, projected, preProjectionRows));
            }

            // Planned whole. The result's shape is settled here — its statistics decided from the plan, so a
            // relation built over this query carries them before any row is produced — and the pipeline runs
            // when the plan is asked for its rows.
            recordPlan(plan);
            final ResultSet shape = new ResultSet(columns, new ArrayList<Row>());
            if (lateralContext == null && !hasPivotSource && keepsSourceStatistics(stmtCtx, ctx, firstTableRef,
                    clauseSource, allTableRefs.size() > 1 || !allJoins.isEmpty())) {
                shape.setRelationStatistics(
                    new DerivedStatistics(this, statisticsBound.get(), clauseSource, columnSources));
            }
            final RuntimeException waitingRefusal = subqueryRefusal;
            final Set<String> refusedKeys = correlations != null ? correlations.refusedKeys() : null;
            // A grouped item's fault waits in its cell for the rows that survive (see GroupByAggregateEvaluator);
            // outside a relation body the statement itself reads them, as a FROM-less select's are read.
            final boolean readsGroupedCells = (hasGroupBy || hasAggregates) && !RelationBody.isActive();
            return new PlannedQuery(shape, plan.describeStages()) {
                @Override
                ResultSet run() {
                    // A correlated subquery the plan refused stays refused while the rows are evaluated.
                    final Set<String> displaced = refusedKeys != null ? PlannedCorrelations.begin(refusedKeys) : null;
                    try {
                        final List<Row> rows = plan.execute();
                        logger.trace("Selected {} rows from table: {}", rows.size(), tableName);
                        final ResultSet selected = new ResultSet(columns, rows);
                        selected.setRelationStatistics(shape.getRelationStatistics());
                        if (waitingRefusal != null) {
                            throw waitingRefusal;
                        }
                        if (readsGroupedCells) {
                            try {
                                DeferredFault.requireNone(rows);
                            } catch (final RuntimeException cellFault) {
                                throw SubqueryAhead.fromOutputCell(cellFault);
                            }
                        }
                        return selected;
                    } catch (final SecurityException e) {
                        throw e; // Let security exceptions propagate
                    } catch (final Exception e) {
                        if (e instanceof ProceduralException) throw (ProceduralException) e;
                        throw StatementErrors.propagate(e);
                    } finally {
                        if (refusedKeys != null) {
                            PlannedCorrelations.end(displaced);
                        }
                    }
                }
            };
        } catch (final SecurityException e) {
            throw e; // Let security exceptions propagate
        } catch (final Exception e) {
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        } finally {
            if (plannedCorrelations) {
                PlannedCorrelations.end(displacedCorrelations);
            }
        }
    }

    /** {@link #planSingleSelect} run at once. */
    private ResultSet runSingleSelect(final FrostlakeParser.SelectStatementContext stmtCtx,
                                      final FrostlakeParser.SelectClauseContext ctx,
                                      final Map<String, Object> lateralContext,
                                      final Map<String, ResultSet> cteResults) {
        return planSingleSelect(stmtCtx, ctx, lateralContext, cteResults).run();
    }

    /**
     * The plan of the last statement this thread ran through the pipeline — a SELECT operand, a set operation,
     * a FROM-less select, an UPDATE, a DELETE or a MERGE — as text: its source relation, then every stage in
     * order, a stage run while planning marked with a trailing {@code *}. A diagnostic for embedders and
     * tests; empty before any such statement.
     *
     * @return the plan's description, or null when this thread has planned none
     */
    public String describeLastSelectPlan() {
        return lastSelectPlan.get();
    }

    /** Records a statement's plan as this thread's last, once it is planned whole and about to run. */
    void recordPlan(final SelectPlan plan) {
        lastSelectPlan.set(plan.describe());
        logger.debug("{}", plan.describe());
    }

    /** The row access policy stage: the rows the current role may not see are dropped ahead of the WHERE. */
    private Operator rowAccessPolicyStage(final Table table) {
        return new StageOperator("ROW ACCESS POLICY[" + table.getRowAccessPolicyName() + "]") {
            @Override
            protected List<Row> apply(final List<Row> input) {
                return applyRowAccessPolicy(input, table);
            }
        };
    }

    /** The CONNECT BY stage: the relation expanded into its hierarchy, over the FROM shape in force before it. */
    private static Operator connectByStage(final ConnectByExpander hierarchy, final Table base, final Table expanded,
                                           final FrostlakeParser.ConnectByClauseContext clause,
                                           final Map<String, Table> aliasToTable, final List<Table> allTables) {
        return new StageOperator("CONNECT BY") {
            @Override
            protected List<Row> apply(final List<Row> input) {
                return hierarchy.expand(input, base, expanded, clause, aliasToTable, allTables);
            }
        };
    }

    /**
     * The GROUP BY stage, which also indexes each output row to its source group for the stages after it.
     */
    private static Operator groupStage(final PipelineStage grouping, final List<List<Row>> groupRows,
                                       final Map<Row, List<Row>> rowToGroup) {
        return new StageOperator(grouping.getDescription()) {
            @Override
            protected List<Row> apply(final List<Row> input) {
                final List<Row> grouped = grouping.run(input);
                indexGroups(grouped, groupRows, rowToGroup);
                return grouped;
            }
        };
    }

    /**
     * The implicit aggregation stage. When every item is a typeof constant or a whole-input statistic, the
     * account's one row per input row is the folded row, repeated; DISTINCT, ORDER BY and LIMIT follow.
     */
    private Operator implicitGroupStage(final PipelineStage grouping, final FrostlakeParser.SelectClauseContext ctx,
                                        final Table table, final boolean mayReplicate,
                                        final List<List<Row>> groupRows, final Map<Row, List<Row>> rowToGroup) {
        return new StageOperator(grouping.getDescription()) {
            @Override
            protected List<Row> apply(final List<Row> input) {
                final int inputRows = input.size();
                List<Row> grouped = grouping.run(input);
                if (mayReplicate && grouped.size() == 1 && inputRows > 1
                        && windowEvaluator.typeofBesideStatisticsReplicates(ctx, table)) {
                    grouped = repeatedAggregateRow(grouped.get(0), inputRows, groupRows);
                }
                indexGroups(grouped, groupRows, rowToGroup);
                return grouped;
            }
        };
    }

    private static void indexGroups(final List<Row> grouped, final List<List<Row>> groupRows,
                                    final Map<Row, List<Row>> rowToGroup) {
        for (int gi = 0; gi < grouped.size() && gi < groupRows.size(); gi++) {
            rowToGroup.put(grouped.get(gi), groupRows.get(gi));
        }
    }

    /**
     * The window stage: computes the window functions, filters through a QUALIFY written over FROM-shape
     * rows before the reshaping, projects the rows into SELECT shape with the window values (precomputing
     * the ORDER BY keys the SELECT list never projects), and filters through a QUALIFY over grouped rows
     * after it. Over a JOIN the alias context is published for the duration.
     *
     * @param aliasContext the FROM-clause keys to publish, or null when a single relation needs none
     */
    private Operator windowStage(final FrostlakeParser.SelectClauseContext ctx, final Table table,
                                 final Table windowShape, final boolean rowsAreProjected,
                                 final GroupedExpressionValues groupValues, final Map<Row, List<Row>> rowToGroup,
                                 final List<String> orderKeyExprs,
                                 final List<FrostlakeParser.ExpressionContext> orderKeyTrees,
                                 final Map<Row, Object[]> orderKeyValues,
                                 final Map<String, Table> aliasContext, final List<Table> allTables) {
        return new StageOperator("WINDOW") {
            @Override
            protected List<Row> apply(final List<Row> input) {
                if (aliasContext != null) {
                    windowAliasToTableStack.get().push(aliasContext);
                    windowAllTablesStack.get().push(allTables);
                }
                try {
                    Map<Integer, Map<Integer, Object>> results;
                    final GroupedExpressionValues savedGroupValues = windowEvaluator.beginGroupedValues(groupValues);
                    try {
                        results = windowEvaluator.computeWindowFunctions(input, ctx, windowShape, rowsAreProjected, table);
                    } finally {
                        windowEvaluator.endGroupedValues(savedGroupValues);
                    }
                    List<Row> rows = input;
                    if (ctx.qualifyClause() != null && !rowsAreProjected) {
                        // QUALIFY evaluates over the PRE-projection rows: its predicate and its inline
                        // windows may reference FROM columns that are NOT in the SELECT list (or partition
                        // by expressions over them). Reshaping first handed the select-shape rows to the
                        // base-table resolver, so such a key read the wrong slot positionally and every
                        // row landed in one NULL partition. Filter first, then reshape only the
                        // survivors, remapping their precomputed window values to the new indices.
                        final List<Row> keptRows = applyQualify(rows, ctx, results, table, table, true);
                        final Map<Row, Integer> oldIndexByRow = new IdentityHashMap<>();
                        for (int i = 0; i < rows.size(); i++) {
                            oldIndexByRow.put(rows.get(i), i);
                        }
                        final Map<Integer, Map<Integer, Object>> remapped = new HashMap<>();
                        for (int i = 0; i < keptRows.size(); i++) {
                            final Integer oldIdx = oldIndexByRow.get(keptRows.get(i));
                            if (oldIdx != null && results.containsKey(oldIdx)) {
                                remapped.put(i, results.get(oldIdx));
                            }
                        }
                        rows = keptRows;
                        results = remapped;
                    }
                    // Add window function values to rows (also precomputes any not-selected ORDER BY keys,
                    // which over grouped rows are answered by the group resolver installed here).
                    final List<Row> preWindowRows = rows;
                    final GroupedExpressionValues savedKeyValues = windowEvaluator.beginGroupedValues(groupValues);
                    try {
                        rows = addWindowFunctionsToRows(rows, results, ctx, windowShape, table, rowsAreProjected,
                            orderKeyExprs, orderKeyTrees, orderKeyValues);
                    } finally {
                        windowEvaluator.endGroupedValues(savedKeyValues);
                    }
                    // That projection builds NEW Row instances, one per input row, so the identity-keyed
                    // group map stops answering for them — and a grouped QUALIFY runs AFTER it. Carry each
                    // group across to its projected row so the stages below can still reach it.
                    if (rowsAreProjected) {
                        for (int i = 0; i < rows.size() && i < preWindowRows.size(); i++) {
                            final List<Row> sourceGroup = rowToGroup.get(preWindowRows.get(i));
                            if (sourceGroup != null) {
                                rowToGroup.put(rows.get(i), sourceGroup);
                            }
                        }
                        if (ctx.qualifyClause() != null) {
                            // Grouped rows are SELECT-shape, and an inline QUALIFY window's key may be a RAW
                            // AGGREGATE the SELECT list never projects — QUALIFY ROW_NUMBER() OVER (ORDER BY
                            // SUM(b)) > 1 beside a select list of `a` — so the group resolver stays installed.
                            final GroupedExpressionValues savedQualifyValues =
                                windowEvaluator.beginGroupedValues(groupValues);
                            try {
                                rows = applyQualify(rows, ctx, results, windowShape, table, false);
                            } finally {
                                windowEvaluator.endGroupedValues(savedQualifyValues);
                            }
                        }
                    }
                    return rows;
                } finally {
                    if (aliasContext != null) {
                        windowAliasToTableStack.get().pop();
                        windowAllTablesStack.get().pop();
                    }
                }
            }
        };
    }

    /**
     * The QUALIFY stage of a query whose select list carries no window: the predicate and its inline windows
     * are evaluated over the rows as they are, under the group resolver when they are grouped.
     */
    private Operator qualifyStage(final FrostlakeParser.SelectClauseContext ctx, final Table shape,
                                  final Table baseTable, final boolean rowsAreBaseShape,
                                  final GroupedExpressionValues groupValues,
                                  final Map<String, Table> aliasContext, final List<Table> allTables) {
        return new StageOperator("QUALIFY[" + getOriginalText(ctx.qualifyClause().booleanExpr()) + "]") {
            @Override
            protected List<Row> apply(final List<Row> input) {
                if (aliasContext != null) {
                    windowAliasToTableStack.get().push(aliasContext);
                    windowAllTablesStack.get().push(allTables);
                }
                try {
                    final GroupedExpressionValues saved = windowEvaluator.beginGroupedValues(groupValues);
                    try {
                        return applyQualify(input, ctx, new HashMap<Integer, Map<Integer, Object>>(), shape, baseTable,
                            rowsAreBaseShape);
                    } finally {
                        windowEvaluator.endGroupedValues(saved);
                    }
                } finally {
                    if (aliasContext != null) {
                        windowAliasToTableStack.get().pop();
                        windowAllTablesStack.get().pop();
                    }
                }
            }
        };
    }

    /**
     * The projection stage, which keeps the rows it read for a DISTINCT under an aggregation policy: the
     * policy's ENTITY KEY counts columns the projection may drop.
     */
    private static Operator projectionStage(final PipelineStage projection, final List<List<Row>> preProjectionRows) {
        return new StageOperator(projection.getDescription()) {
            @Override
            protected List<Row> apply(final List<Row> input) {
                preProjectionRows.clear();
                preProjectionRows.add(input);
                return projection.run(input);
            }
        };
    }

    /** A stage that keeps the rows reaching it for the DISTINCT fold and passes them on unchanged. */
    private static Operator snapshotStage(final List<List<Row>> keptRows) {
        return new StageOperator("SNAPSHOT") {
            @Override
            protected List<Row> apply(final List<Row> input) {
                keptRows.clear();
                keptRows.add(input);
                return input;
            }
        };
    }

    /** The ORDER BY stage over the projected output, every key matching an output column. */
    private Operator outputOrderByStage(final FrostlakeParser.SelectStatementContext stmtCtx, final Table table,
                                        final Map<String, Table> aliasToTable) {
        return new StageOperator("ORDER BY[output]") {
            @Override
            protected List<Row> apply(final List<Row> input) {
                return orderByAfterGroupBy(input, stmtCtx, null, table, aliasToTable);
            }
        };
    }

    /**
     * The ORDER BY stage over grouped, aggregated or windowed rows, whose structure is the SELECT list's. A
     * key the SELECT list never projects is answered from the window stage's precomputed values, or over
     * the row's own source group.
     */
    private Operator groupedOrderByStage(final FrostlakeParser.SelectStatementContext stmtCtx,
                                         final FrostlakeParser.SelectClauseContext ctx, final Table table,
                                         final Map<String, Table> aliasToTable, final List<Table> allTables,
                                         final boolean windowed, final Map<Row, Object[]> windowKeyValues,
                                         final Map<String, Integer> windowKeyIndex,
                                         final Map<Row, List<Row>> rowToGroup) {
        return new StageOperator("ORDER BY[" + (windowed ? "windowed" : "grouped") + "]") {
            @Override
            protected List<Row> apply(final List<Row> input) {
                final GroupOrderKeyResolver resolver = windowed
                    ? newWindowOrderResolver(input, windowKeyValues, windowKeyIndex)
                    : newGroupOrderResolver(input, rowToGroup, table, aliasToTable, allTables, ctx);
                return orderByAfterGroupBy(input, stmtCtx, resolver, table, aliasToTable);
            }
        };
    }

    /**
     * The DISTINCT stage. An aggregation policy folds its small groups first; then a collated column
     * de-duplicates under its collation, and the row that survives reports the smallest of the values that
     * folded into it.
     */
    private Operator distinctStage(final Table table, final CollationSpec[] projected,
                                   final List<List<Row>> preProjectionRows) {
        return new StageOperator("DISTINCT") {
            @Override
            protected List<Row> apply(final List<Row> input) {
                final List<Row> folded = foldDistinctForAggregationPolicy(input,
                    preProjectionRows.isEmpty() ? input : preProjectionRows.get(0), table);
                final List<Row> distinct = SetOperations.applyDistinct(folded, projected);
                logger.trace("Applied DISTINCT, reduced to {} unique rows", distinct.size());
                return distinct;
            }
        };
    }

    /**
     * Execute SELECT without FROM clause (e.g., SELECT 1, SELECT {'key': 'value'})
     */
    /**
     * The one-row source a FROM-less query runs over: DUAL, whose one column COLUMN1 holds NULL (see
     * {@link FromlessDual}). An aggregate over it sees the one row live's implicit single group is made of.
     *
     * @return DUAL's one row
     */
    private TableData singleEmptyRowSource() {
        final List<Row> rows = new ArrayList<>();
        if (!RelationShapeOnly.isActive()) {
            final List<Object> values = new ArrayList<>();
            values.add(null);
            rows.add(new Row(values));
        }
        return new TableData(new FromlessDual(), rows, null).describedAs("DUAL");
    }

    /**
     * A FROM-less item's value while a relation is read for its shape only: computed when it can be,
     * NULL when the computation faults at value time — that fault belongs to whoever reads the view —
     * while a refusal the compiler raises still propagates.
     */
    private static Object shapeOnlyValue(final ExpressionEvaluator evaluator, final String exprText,
                                         final Row row) {
        try {
            return evaluator.evaluate(exprText, row);
        } catch (final RuntimeException failed) {
            if (SqlCompilationError.isCompilationError(failed.getMessage())) {
                throw failed;
            }
            return null;
        }
    }

    /**
     * A FROM-less item's value. A row fault waits in the cell for a reader, as a FROM query's item's does inside
     * a relation body (see {@link DeferredFault}): live computes the item only for a row something reads, so
     * {@code SELECT 1/0 WHERE 1 = 0}, {@code SELECT 1/0 HAVING 1 = 0} and {@code SELECT RANDOM(NULL) LIMIT 0}
     * answer no row, while a WHERE that reads the item's name, or handing the row out, raises the fault
     * (live-verified). What a subquery of the item raises ahead of the rows does not wait (see
     * {@link SubqueryAhead}): {@code SELECT (SELECT v FROM t) AS x WHERE 1 = 0} over a table of several rows is
     * "Single-row subquery returns more than one row."
     */
    private static Object fromlessItemValue(final ExpressionEvaluator evaluator, final String exprText,
                                            final Row row) {
        try {
            return evaluator.evaluate(exprText, row);
        } catch (final RuntimeException failed) {
            if (SubqueryAhead.waits(failed)) {
                return new DeferredFault(failed);
            }
            throw failed;
        }
    }

    /**
     * A SELECT with no FROM and no aggregate, as a plan over DUAL's one row: a projection stage that judges
     * and computes the items in order, then the WHERE, HAVING and QUALIFY stages over the one-row relation the
     * items publish, DISTINCT, and the statement's LIMIT, OFFSET or FETCH, which bound the row as they bound
     * any query's rows ({@code SELECT 1 LIMIT 0} and {@code SELECT 1 LIMIT 1 OFFSET 1} answer no row,
     * live-verified). The items are judged as they are computed, item by item — a FROM-less projection
     * computes the value before anything types it, so a refusal raised there is the one the user sees.
     * Outside a relation body, a row that survives is read here, so a fault its item deferred is raised by
     * the statement itself, wherever its result goes next.
     */
    private PlannedQuery planSelectWithoutFrom(final FrostlakeParser.SelectStatementContext stmtCtx,
                                               final FrostlakeParser.SelectClauseContext ctx,
                                               final Map<String, Object> lateralContext) {
        // Whether a surviving row's deferred fault is the reading statement's to raise, decided where the
        // select is planned: inside a relation body the fault waits in its cell for the reader.
        final boolean inRelationBody = RelationBody.isActive();
        try {
            // CONNECT BY parses without FROM but is then refused — live's single-line shape,
            // positioned at the statement start.
            if (ctx.connectByClause() != null) {
                final Token start = ctx.getStart();
                throw new RuntimeException(SqlCompilationError.PREFIX + " error line "
                    + start.getLine() + " at position " + start.getCharPositionInLine()
                    + " missing FROM clause");
            }
            // Parse select list
            final FrostlakeParser.SelectListContext selectList = ctx.selectList();
            // The result's columns, published by the projection stage as it computes the items.
            final List<ResultSetColumn> columns = new ArrayList<>();
            // Shared dummy table/evaluator for the whole select list: DUAL, of which nothing here reads a column
            final Table dummyTable = FromlessDual.unread();
            // Every subquery an item holds compiles before any item is computed (see SubqueryCompilation).
            RuntimeException subqueryRefusal = null;
            if (lateralContext == null || SubqueryCompilation.compiles(lateralContext)) {
                final List<ParserRuleContext> items = new ArrayList<>();
                for (final FrostlakeParser.SelectItemContext item : selectList.selectItem()) {
                    items.add(SelectItemAccessors.getItemExpression(item));
                }
                if (getWhereClause(ctx) != null) {
                    items.add(getWhereClause(ctx).booleanExpr());
                }
                if (ctx.havingClause() != null) {
                    items.add(ctx.havingClause().booleanExpr());
                }
                subqueryRefusal = compileSubqueriesIn(items, dummyTable, null, null, lateralContext);
            }
            final RuntimeException waitingSubqueryRefusal = subqueryRefusal;
            // The item names whose value is an aggregate — a WHERE may not read one of those.
            final Set<String> aggregateItemNames = new HashSet<>();
            final List<String> itemTexts = new ArrayList<>();
            for (final FrostlakeParser.SelectItemContext item : selectList.selectItem()) {
                itemTexts.add(SelectItemAccessors.isObjectStarItem(item) ? SelectItemAccessors.objectStarLabel(item)
                    : getOriginalText(SelectItemAccessors.getItemExpression(item)).trim());
            }
            // The projection is the row's origin: a FROM-less select is ONE row whatever DUAL holds — read for
            // its shape too, where DUAL answers no row at all — so the stage computes it whatever reaches it.
            // The items are typed as they are computed, so the projection — the one stage whose shape is
            // only known from its row — runs while the select is planned; the stages after it run over that
            // row when the plan does.
            final SelectPlan plan = new SelectPlan(this, functionRegistry);
            plan.source(new ArrayList<Row>(), "DUAL");
            plan.materialize(new StageOperator("PROJECT[" + String.join(", ", itemTexts) + "]") {
                @Override
                protected List<Row> apply(final List<Row> input) {
                    final List<Row> projected = new ArrayList<>();
                    try {
                        projected.add(new Row(projectWithoutFrom(selectList, dummyTable, lateralContext, columns,
                            aggregateItemNames)));
                    } catch (final RuntimeException raised) {
                        // A subquery's type refusal ranks ahead of what the items raise, a name excepted.
                        throw SubqueryTypeRanking.ahead(waitingSubqueryRefusal, raised);
                    }
                    if (waitingSubqueryRefusal != null) {
                        throw waitingSubqueryRefusal;
                    }
                    return projected;
                }
            });
            // Apply WHERE clause if present. The item list is a scope: a FROM-less predicate reads the
            // names this select publishes exactly as a query over a relation reads its columns, so the
            // predicate is evaluated over a one-row relation MADE of those items (live-verified). Its
            // reach ends there — a query nested in the predicate has a scope of its own and cannot see
            // these names, which is why they are a relation here rather than an outer lateral context.
            final FrostlakeParser.WhereClauseContext whereCtxNoFrom = getWhereClause(ctx);
            if (whereCtxNoFrom != null) {
                plan.add(new StageOperator("WHERE[" + getOriginalText(whereCtxNoFrom.booleanExpr()) + "]") {
                    @Override
                    protected List<Row> apply(final List<Row> input) {
                        return filterWithoutFrom(input, ctx, whereCtxNoFrom, whereCtxNoFrom.booleanExpr(),
                            "where clause", lateralContext, columns, aggregateItemNames);
                    }
                });
            }
            // The one row needs no sort, but the ORDER BY's keys are judged as any query's are.
            if (stmtCtx != null && stmtCtx.orderByClause() != null && stmtCtx.selectOperand().size() == 1) {
                plan.add(new FromlessOrderBy(this, orderByExecutor, stmtCtx, lateralContext).stage(columns));
            }
            // GROUP BY over the FROM-less single row is one group projecting that same row, so it
            // needs no work; HAVING and QUALIFY then filter it like WHERE does (live keeps or drops
            // the row). A QUALIFY here always has a window function somewhere — the windowless form
            // was refused at the statement entry. A HAVING here aggregates nothing (see havingAggregates),
            // so it reads the names the items publish exactly as the WHERE does.
            final FrostlakeParser.HavingClauseContext havingNoFrom = ctx.havingClause();
            if (havingNoFrom != null) {
                plan.add(new StageOperator("HAVING[" + getOriginalText(havingNoFrom.booleanExpr()) + "]") {
                    @Override
                    protected List<Row> apply(final List<Row> input) {
                        return filterWithoutFrom(input, ctx, havingNoFrom, havingNoFrom.booleanExpr(), null,
                            lateralContext, columns, aggregateItemNames);
                    }
                });
            }
            if (ctx.qualifyClause() != null) {
                plan.add(predicateStageWithoutFrom("QUALIFY", ctx.qualifyClause().booleanExpr()));
            }
            if (ctx.DISTINCT() != null) {
                plan.add(new StageOperator("DISTINCT") {
                    @Override
                    protected List<Row> apply(final List<Row> input) {
                        return SetOperations.applyDistinct(input);
                    }
                });
            }
            if (stmtCtx != null && stmtCtx.limitClause() != null) {
                final int[] window = limitWindow(stmtCtx.limitClause());
                plan.add(fromlessRowLimit(stmtCtx, window));
            }
            if (stmtCtx != null && stmtCtx.fetchClause() != null) {
                final int[] window = fetchWindow(stmtCtx.fetchClause());
                plan.add(fromlessRowLimit(stmtCtx, window));
            }
            recordPlan(plan);
            return new PlannedQuery(new ResultSet(columns, new ArrayList<Row>()), plan.describeStages()) {
                @Override
                ResultSet run() {
                    try {
                        final List<Row> rows = new ArrayList<>(plan.execute());
                        if (!inRelationBody) {
                            DeferredFault.requireNone(rows);
                        }
                        return new ResultSet(columns, rows);
                    } catch (final Exception e) {
                        throw StatementErrors.propagate(e);
                    }
                }
            };
        } catch (final Exception e) {
            throw StatementErrors.propagate(e);
        }
    }

    /**
     * A FROM-less select's LIMIT or FETCH window, which refuses an unset bind among the counts once the stages
     * before it have run: the one row's WHERE, HAVING and ORDER BY names are judged there, and live names those
     * ahead of the bind (see {@link RowLimitBindRule}).
     */
    private LimitOperator fromlessRowLimit(final FrostlakeParser.SelectStatementContext stmtCtx, final int[] window) {
        return new LimitOperator(window[0], window[1]) {
            @Override
            public List<Row> execute(final List<Row> input, final OperatorContext context) {
                RowLimitBindRule.reject(stmtCtx, QueryExecutor.this);
                return super.execute(input, context);
            }
        };
    }

    /**
     * The FROM-less items, judged and computed in order into one row's values; the columns they publish are
     * added to {@code columns} alongside.
     */
    private List<Object> projectWithoutFrom(final FrostlakeParser.SelectListContext selectList, final Table dummyTable,
                                            final Map<String, Object> lateralContext,
                                            final List<ResultSetColumn> columns, final Set<String> aggregateItemNames) {
        final List<Object> values = new ArrayList<>();
        columns.clear();
        aggregateItemNames.clear();
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(dummyTable, functionRegistry, catalog, this);
        // alias -> computed value, built up left-to-right for forward-reference resolution
        final Map<String, Object> selectAliasValues = new HashMap<>();
        // A correlated FROM-less subquery reads the outer row: (SELECT fz.id + 1) answers per outer
        // row (live-verified). Its own aliases, added as the list is walked, come on top. Only those
        // are reused directly below; an outer value is read through the evaluator, whose count of
        // outer reads is what keeps the subquery from being memoized as uncorrelated.
        final Set<String> ownAliases = new HashSet<>();
        if (lateralContext != null) {
            selectAliasValues.putAll(lateralContext);
        }
        // Offer those values as lateral (outer) values so a LATER item can reference an earlier alias
        // INSIDE an expression — SELECT 1 AS x, x + 1 AS y. Matching the whole expression text against an
        // alias (below) only handles a bare `x`. The evaluator holds this map by reference, so entries
        // added as the list is walked are visible to the items that follow.
        evaluator.setOuterLateralContext(selectAliasValues);
        // Their static types likewise, so SELECT 1 AS x, x declares NUMBER(1,0) twice, as live does.
        final Map<String, DataType> earlierItemTypes = new HashMap<>();
        evaluator.setOutputAliasTypes(earlierItemTypes);
        // Process each select item
        for (final FrostlakeParser.SelectItemContext item : selectList.selectItem()) {
            if (SelectItemAccessors.isObjectStarItem(item)) {
                // The braced star is OBJECT_CONSTRUCT over the row, whose star argument sees
                // nothing of DUAL — so it constructs the EMPTY object rather than failing.
                // Live-verified on a real account: SELECT {*} answers {}, exactly as SELECT
                // OBJECT_CONSTRUCT(*) does, and SELECT {* EXCLUDE (a)} answers {} too, while the
                // plain SELECT * reads DUAL's COLUMN1 over the pipeline.
                final Row emptyRow = new Row(values);
                columns.add(projectedColumn(SelectItemAccessors.objectStarLabel(item),
                    StringType.VARCHAR, null, "OBJECT_CONSTRUCT()", evaluator));
                values.add(evaluator.evaluate("OBJECT_CONSTRUCT()", emptyRow));
                continue;
            }
            final String exprText = getOriginalText(SelectItemAccessors.getItemExpression(item)).trim();
            // Only a REFERENCE-shaped item may reuse an earlier alias's value (SELECT 1 AS x, x).
            // Anything else evaluates on its own: two literals equal modulo case are different
            // values, and 'Container' after a column NAMED 'CONTAINER' keeps its own casing.
            final FrostlakeParser.ExpressionContext itemValue =
                SelectItemAccessors.getItemValueExpr(item);
            final boolean referenceShaped = itemValue != null
                && SelectItemAccessors.unwrapParens(itemValue)
                    instanceof FrostlakeParser.QualifiedNameExprContext;
            final Object value;
            if (referenceShaped && ownAliases.contains(exprText.toUpperCase())) {
                value = selectAliasValues.get(exprText.toUpperCase());
            } else {
                final Row dummyRow = new Row(values);
                // A FROM-less projection computes the VALUE before anything types it, so a refusal
                // raised here is the one the user sees — scope the item's origin around it so it
                // carries the same position the typed path would have given.
                final ParserRuleContext itemOrigin = SelectItemAccessors.getItemExpression(item);
                final SourcePosition displacedItem = ExpressionSource.beginNested(itemOrigin == null ? null
                    : new SourcePosition(itemOrigin.getStart().getLine(),
                                         itemOrigin.getStart().getCharPositionInLine()));
                try {
                    // The boolean positions are judged on their STATIC types first: a CASE over a
                    // literal or a VARIANT expression refuses here as it does over a table, where
                    // the value-first evaluation would have answered.
                    evaluator.validateBooleanPositions(ExpressionEvaluator.parse(exprText));
                    // A user-defined function's argument types are a plan-time refusal too.
                    evaluator.validateUdfArguments(ExpressionEvaluator.parse(exprText));
                    // And every other argument type, judged as the strict walk judges a FROM query's
                    // items: 'x' || ARRAY_CONSTRUCT(1) is refused before anything is computed.
                    evaluator.validateStrict(ExpressionEvaluator.parse(exprText));
                    value = RelationShapeOnly.isActive()
                        ? shapeOnlyValue(evaluator, exprText, dummyRow)
                        : fromlessItemValue(evaluator, exprText, dummyRow);
                } finally {
                    ExpressionSource.end(displacedItem);
                }
            }
            // Column name: an explicit alias, else the item's SOURCE TEXT folded to upper case —
            // live names an unlabelled literal after itself (SELECT 1 is named 1, SELECT 'x' is
            // named 'X') and never truncates, with string literals folding inside the text too.
            final String columnName;
            if (SelectItemAccessors.getItemAlias(item) != null) {
                columnName = (SelectItemAccessors.getItemAlias(item));
            } else {
                // A sequence read is named after its pseudo-column, as the FROM-bearing projection names it.
                columnName = SelectItemAccessors.isSequenceRead(itemValue)
                    ? selectItemColumnName(itemValue) : exprText.toUpperCase();
            }
            // Same channel as the FROM-bearing projection: live types the two identically
            // (OBJECT/ARRAY/VARIANT reported, everything else recovered from the values).
            final ResultSetColumn projectedItem = projectedColumn(columnName, StringType.VARCHAR, null,
                exprText, evaluator, true, false, SelectItemAccessors.getItemExpression(item));
            // With no FROM, a collation can only be one the item WROTE — and it travels out of the
            // projection like any other, so a derived relation over it collates the column.
            final CollationSpec itemCollation = KeyCollations.reachable(List.of(exprText), null, null, null)
                ? evaluator.keyCollation(ExpressionEvaluator.parse(exprText)) : null;
            columns.add(itemCollation == null ? projectedItem
                : projectedItem.withCollation(itemCollation.getText()));
            values.add(value);
            selectAliasValues.put(columnName.toUpperCase(), value);
            ownAliases.add(columnName.toUpperCase());
            if (projectedItem.getStaticType() != null) {
                earlierItemTypes.put(columnName.toUpperCase(Locale.ROOT), projectedItem.getStaticType());
            }
            final List<ParserRuleContext> itemAggregates = new ArrayList<>();
            collectAggregateCallsOutsideNestedQueries(
                SelectItemAccessors.getItemExpression(item), itemAggregates);
            if (!itemAggregates.isEmpty()) {
                aggregateItemNames.add(columnName);
            }
        }
        return values;
    }

    /**
     * The FROM-less WHERE, or HAVING, over the one-row relation the items publish: the clause rules a WHERE over
     * a relation is held to, in live's order, then the predicate; a row it refuses is dropped.
     *
     * @param clause              the WHERE or HAVING clause
     * @param predicate           its predicate
     * @param aggregateClauseName the clause's name in the refusal of an aggregate written in it, or null where
     *                            the clause may hold one
     */
    private List<Row> filterWithoutFrom(final List<Row> input, final FrostlakeParser.SelectClauseContext ctx,
                                        final ParserRuleContext clause,
                                        final FrostlakeParser.BooleanExprContext predicate,
                                        final String aggregateClauseName,
                                        final Map<String, Object> lateralContext,
                                        final List<ResultSetColumn> columns, final Set<String> aggregateItemNames) {
        final FromlessSelectScope publishedScope = new FromlessSelectScope();
        final List<TableColumn> publishedColumns = new ArrayList<>();
        for (final ResultSetColumn projected : columns) {
            publishedColumns.add(new TableColumn(projected.getName(), projected.getDataType(),
                true, null, false, false, false));
            publishedScope.publish(projected.getName(),
                aggregateItemNames.contains(projected.getName()));
        }
        // Judged before the predicate's own origin is scoped, so each name is reported at its
        // own place in the statement. A name the items do not publish may still be the outer
        // row's, which the predicate reads below: EXISTS (SELECT 1 WHERE v = 1) over q reads q.v.
        final ExpressionEvaluator outsideScope = new ExpressionEvaluator(
            new Table("DUMMY", new ArrayList<TableColumn>(), false), functionRegistry, catalog, this);
        outsideScope.setOuterLateralContext(lateralContext);
        publishedScope.rejectUnresolvableNames(predicate, outsideScope,
            new Row(new ArrayList<>()));
        final Table dummyTable2 = new Table("DUMMY", publishedColumns, false);
        final String whereExpr = getOriginalText(predicate);
        final ExpressionEvaluator whereEval = new ExpressionEvaluator(dummyTable2, functionRegistry, catalog, this);
        whereEval.setScopeOpaqueToSubqueries(true);
        // A FROM-less subquery's WHERE reads the row of the query around it, as its items do.
        whereEval.setOuterLateralContext(lateralContext);
        // The clause rules a WHERE over a relation is held to, in live's order. Each scopes the
        // predicate's origin for itself, so they run before the evaluation's own scope is open.
        if (aggregateClauseName != null) {
            rejectAggregatesIn(clause, aggregateClauseName,
                new PlanEcho(dummyTable2, new HashMap<String, Table>(), new ArrayList<Table>(),
                    functionRegistry, catalog));
        }
        validateClausePredicateType(whereExpr, dummyTable2, null, null, ctx, predicate);
        final List<Row> kept = new ArrayList<>();
        for (final Row row : input) {
            // Scoped to the predicate's origin like the items above, so a refusal raised while it is
            // evaluated carries its position in the statement.
            final SourcePosition displacedWhere = ExpressionSource.beginNested(new SourcePosition(
                predicate.getStart().getLine(), predicate.getStart().getCharPositionInLine()));
            final Object whereResult;
            try {
                // The predicate's argument types are judged before it is evaluated, as a FROM query's are.
                whereEval.validateStrict(ExpressionEvaluator.parse(whereExpr));
                whereResult = whereEval.evaluate(whereExpr, row);
            } finally {
                ExpressionSource.end(displacedWhere);
            }
            final boolean condMet = SqlTruth.isTrue(whereResult)
                ? true : whereResult != null && !"false".equalsIgnoreCase(whereResult.toString())
                    && !"0".equals(whereResult.toString());
            if (condMet) {
                kept.add(row);
            }
        }
        return kept;
    }

    /** A FROM-less QUALIFY stage: the predicate over the one row, which is kept or dropped. */
    private Operator predicateStageWithoutFrom(final String clause,
                                               final FrostlakeParser.BooleanExprContext predicate) {
        return new StageOperator(clause + "[" + getOriginalText(predicate) + "]") {
            @Override
            protected List<Row> apply(final List<Row> input) {
                final List<Row> kept = new ArrayList<>();
                for (final Row row : input) {
                    final ExpressionEvaluator filterEval =
                        new ExpressionEvaluator(new Table("DUMMY", new ArrayList<>(), false),
                            functionRegistry, catalog, QueryExecutor.this);
                    final Object filterResult = filterEval.evaluate(getOriginalText(predicate), row);
                    if (SqlTruth.isTrue(filterResult)) {
                        kept.add(row);
                    }
                }
                return kept;
            }
        };
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
     * Enforce column constraints at statement time on the COPY write path: coerce values to the column
     * type (only when constraints.enforce.types is on), then check NOT NULL (always — Snowflake enforces
     * NOT NULL, unlike the informational PK/UK/FK). The violation sentences here are BARE — a COPY
     * reports them as the file's {@code first_error}, naming the column separately in
     * {@code first_error_column_name}. DML statements go through
     * {@link #enforceColumnConstraintsForDml(Table, Row)}, which wraps the same sentences in the
     * per-statement envelope live uses.
     */
    public void enforceColumnConstraints(final Table table, final Row row) {
        if (enforceTypes) {
            coerceRowTypes(table, row);
        }
        final List<TableColumn> cols = table.getColumns();
        for (int i = 0; i < cols.size(); i++) {
            if (!cols.get(i).isNullable() && i < row.getValues().size() && row.getValue(i) == null) {
                // Snowflake's own wording, verbatim.
                throw new RuntimeException("NULL result in a non-nullable column");
            }
        }
    }

    /**
     * The DML flavour of {@link #enforceColumnConstraints(Table, Row)}: INSERT, {@code INSERT … SELECT},
     * UPDATE and MERGE all report a write violation inside a per-statement envelope naming the table and
     * the column — live-verified verbatim, {@code DML operation to table NN failed on column C with
     * error: NULL result in a non-nullable column} — around the same inner sentences the COPY path
     * reports bare. Enforcement is per column in declaration order, so the first violating column names
     * the failure.
     */
    public void enforceColumnConstraintsForDml(final Table table, final Row row) {
        enforceColumnConstraintsForDml(table, row, false, null);
    }

    /** {@link #enforceColumnConstraintsForDml(Table, Row, boolean, String)} naming the table bare. */
    public void enforceColumnConstraintsForDml(final Table table, final Row row,
                                               final boolean boundToColumnSlot) {
        enforceColumnConstraintsForDml(table, row, boundToColumnSlot, null);
    }

    /**
     * The same constraints, told whether the values were BOUND STRAIGHT INTO THE COLUMN'S SLOT — which is
     * what an {@code INSERT … VALUES} does and what nothing else does. Only the out-of-range refusal can
     * tell the difference, and it prints it: through VALUES the storage class is the COLUMN's, through
     * every query-sourced write it is the VALUE's.
     *
     * @param table the table being written
     * @param row the row as built
     * @param boundToColumnSlot whether the values came from a VALUES list rather than a query
     */
    /**
     * The write-time constraints of one row, each failure wrapped in the DML envelope that names the
     * table AS THE STATEMENT WROTE IT — {@code TEST_DB.TEST_SCHEMA.TWO} for a qualified INSERT,
     * {@code TWO} for a bare one, four parts when the account was written (live-verified for INSERT,
     * UPDATE, UPDATE … FROM and both MERGE branches). The spelling travels as a PARAMETER from the
     * handler that read the statement: a thread-local for it leaked into the next statement once.
     *
     * @param writtenName the table as written, upper-cased per part, or null for the bare catalog name
     */
    public void enforceColumnConstraintsForDml(final Table table, final Row row,
                                               final boolean boundToColumnSlot, final String writtenName) {
        final List<TableColumn> cols = table.getColumns();
        for (int i = 0; i < cols.size() && i < row.getValues().size(); i++) {
            try {
                if (enforceTypes) {
                    rejectValuePastColumnRange(row.getValue(i), cols.get(i), boundToColumnSlot);
                    final Object written = boundToColumnSlot && IntervalCasts.isIntervalType(cols.get(i).getDataType())
                        ? IntervalCasts.throughValuesSlot(row.getValue(i)) : row.getValue(i);
                    row.setValue(i, coerceWriteValue(written, cols.get(i).getDataType()));
                }
                if (!cols.get(i).isNullable() && row.getValue(i) == null) {
                    throw new RuntimeException("NULL result in a non-nullable column");
                }
            } catch (final RuntimeException violation) {
                throw DmlWriteTarget.failedOnColumn(writtenName != null ? writtenName : table.getName(),
                    cols.get(i).getName(), violation);
            }
        }
        enforceCheckConstraints(table, row);
    }

    /**
     * Refuse a number too wide for the column it is being written into — the write half of the
     * out-of-representable-range family, which Frostlake used to skip entirely: a 1000 SETTLED into a
     * NUMBER(2,0) column, four digits in a two-digit slot, and read back out again.
     *
     * <p>The check runs on the value AS WRITTEN, before the column's scale is applied, because the
     * sentence names that number rather than the rounded one. A STRING source earns the other sentence
     * of the family — live names the text and never mentions a type — and one that is not a number at
     * all is left to the coercion below, whose "is not recognized" is the right refusal for it.
     *
     * <p>Only the FIXED-POINT families reach it: a FLOAT column swallows 1e308 and answers Infinity past
     * that, live-verified, so an approximate column is never out of range.
     *
     * @param value the value about to be written, still in its source form
     * @param column the column it is destined for
     * @param boundToColumnSlot whether the class printed should be the column's rather than the value's
     */
    private void rejectValuePastColumnRange(final Object value, final TableColumn column,
                                            final boolean boundToColumnSlot) {
        if (value == null || !(column.getDataType() instanceof NumericType)) {
            return;
        }
        final NumericType type = (NumericType) column.getDataType();
        if (type.getScale() < 0 || !isFixedPointNumeric(type.getName())) {
            return;
        }
        final BigDecimal exact;
        try {
            exact = value instanceof BigDecimal ? (BigDecimal) value
                : new BigDecimal(value.toString().trim());
        } catch (final NumberFormatException notANumber) {
            return;
        }
        if (!NumericRangeRefusal.exceeds(exact, type.getPrecision(), type.getScale())) {
            return;
        }
        if (value instanceof CharSequence) {
            throw new RuntimeException(NumericRangeRefusal.unconvertibleText(value));
        }
        final String tag = boundToColumnSlot || value instanceof Double || value instanceof Float
            ? SignedStorageWidth.tagOfPrecision(type.getPrecision())
            : SignedStorageWidth.tagOf(exact, type.getScale());
        throw new RuntimeException(NumericRangeRefusal.typed(tag, type.getPrecision(), type.getScale(),
            column.isNullable(), exact));
    }

    /**
     * Refuse a row any CHECK constraint makes FALSE. Unlike the primary, unique and foreign keys —
     * which Snowflake records without enforcing — a check IS enforced, on INSERT, UPDATE and MERGE
     * alike (live-verified), and three-valued logic decides it: only FALSE violates, so a NULL
     * anywhere in the expression lets the row through.
     *
     * <p>The refusal names the table in FULL and the constraint the way it was created: an explicit
     * name bare, a generated SYS_CONSTRAINT_* in double quotes.
     */
    private void enforceCheckConstraints(final Table table, final Row row) {
        final List<CheckConstraint> checks = table.getCheckConstraints();
        if (checks.isEmpty()) {
            return;
        }
        final ExpressionEvaluator evaluator =
            new ExpressionEvaluator(table, getFunctionRegistry(), catalog, this);
        for (final CheckConstraint check : checks) {
            final Object verdict = evaluator.evaluate(check.getExpression(), row);
            if (verdict != null && !SqlTruth.isTrue(verdict)) {
                throw new RuntimeException("Operation on table "
                    + getFullyQualifiedTableName(table.getName()) + " failed because CHECK constraint "
                    + check.getReportedName() + ", which requires that " + check.getExpression()
                    + ", was violated");
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
     * A CTAS row written into the declared types of its column list, as live writes it: each value is
     * converted to its column the way an INSERT converts it — a number rounded to the scale, text parsed —
     * and a value that does not fit is refused inside the DML envelope naming the table and the column
     * ("DML operation to table DB.S.T failed on column X with error: String 'abcd' is too long and would be
     * truncated"). A NULL in a NOT NULL column is the bare "NULL result in a non-nullable column" there
     * (live-verified).
     *
     * @param envelopeName the table as the CTAS envelope names it, one level up from how it was written
     */
    public void writeCtasRow(final Table table, final Row row, final String envelopeName) {
        final List<TableColumn> cols = table.getColumns();
        for (int i = 0; i < cols.size() && i < row.getValues().size(); i++) {
            try {
                rejectValuePastColumnRange(row.getValue(i), cols.get(i), false);
                row.setValue(i, coerceWriteValue(row.getValue(i), cols.get(i).getDataType()));
            } catch (final RuntimeException violation) {
                throw DmlWriteTarget.failedOnColumn(envelopeName, cols.get(i).getName(), violation);
            }
            if (!cols.get(i).isNullable() && row.getValue(i) == null) {
                throw new RuntimeException("NULL result in a non-nullable column");
            }
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
        if (IntervalCasts.isIntervalType(type)) {
            // A text is read in the column's fields and an interval cut to them, as the cast to its type does.
            return IntervalCasts.convert(value, type, true);
        }
        if (type instanceof FileType) {
            // A write to a FILE column goes through TO_FILE, so both live-measured shapes work
            //: a stage-path string is RESOLVED against the stage — INSERT … SELECT
            // '@st/x.txt' stores the full descriptor of the real file and fails when it is absent —
            // and a metadata object is validated structurally but not resolved. Snowflake wraps the
            // failure with the column it happened on ("DML operation to table W failed on column F
            // with error: Remote file '@sse/nope.txt' was not found. …"); the same message reaches
            // the caller here, wrapped by the DML layer's own context.
            final List<Object> fileArgs = new ArrayList<Object>();
            fileArgs.add(value);
            return functionRegistry.getFunction("TO_FILE").evaluate(fileArgs);
        }
        if (type instanceof GeographyType || type instanceof GeometryType) {
            if (value instanceof GeoValue) {
                return value;
            }
            // A WKT/GeoJSON string coerces on write, like TO_GEOGRAPHY / TO_GEOMETRY. The parser
            // lives in the optional frostlake-geo module and is reached through the registry, so
            // the engine itself stays free of geo code.
            final BuiltInFunction geoParser = functionRegistry.getFunction(
                type instanceof GeographyType ? "TO_GEOGRAPHY" : "TO_GEOMETRY");
            if (geoParser == null) {
                throw new RuntimeException(type.getName()
                    + " input requires the frostlake-geo module on the classpath");
            }
            final List<Object> geoArgs = new ArrayList<Object>();
            geoArgs.add(value.toString());
            return geoParser.evaluate(geoArgs);
        }
        if (type instanceof VectorType) {
            // A VECTOR column keeps a typed VectorValue: the declared element type and dimension are
            // applied on write, so a stored vector always satisfies its column (an array of the wrong
            // length or with the wrong element kind raises Snowflake's own conversion error).
            return VectorValue.cast(value, (VectorType) type);
        }
        if (value instanceof VariantValue && !(type instanceof ArrayType
                || type instanceof ObjectType || type instanceof VariantType)) {
            // A semi-structured value written into a non-semi-structured column coerces via its
            // VALUE: a VARIANT STRING unquotes (live stores Theodore, not "Theodore", in a
            // VARCHAR column), while containers coerce via their JSON text.
            final JsonNode variantNode = ((VariantValue) value).node();
            final Object unwrapped = variantNode != null && variantNode.isTextual()
                ? variantNode.asText() : ((VariantValue) value).text();
            return coerceWriteValue(unwrapped, type);
        }
        if (type instanceof VariantType && value instanceof String) {
            // Live REFUSES a bare VARCHAR expression against a VARIANT column ("Expression type
            // does not match column data type"); this engine keeps its historical leniency, and
            // when the path IS taken the string stores as a VARIANT STRING — the same cell the
            // explicit ::VARIANT cast produces — so it compares equal to one built by PARSE_JSON.
            // Text that already IS a JSON form — structural, quoted, or the JSON-null marker —
            // keeps its pass-through: those cells hold their JSON shape.
            final String text = ((String) value).trim();
            if (!(text.startsWith("{") || text.startsWith("[") || text.startsWith("\"")
                    || "null".equals(text))) {
                final List<Object> variantArgs = new ArrayList<Object>();
                variantArgs.add(value);
                return functionRegistry.getFunction("TO_VARIANT").evaluate(variantArgs);
            }
        }
        if (type instanceof UuidType) {
            // A UUID column holds the canonical lower-case text and refuses anything else with the
            // conversion's own sentence, which the DML envelope then wraps (live-verified).
            return ToUuid.canonical(value instanceof String ? (String) value : value.toString());
        }
        if (type instanceof StringType) {
            final StringType st = (StringType) type;
            // A value written into a text column reads as its cast to VARCHAR would: a FLOAT in its display
            // form (2, 1e+20), a timestamp as 2024-02-03 00:00:00.000 (live-verified), never Java's text.
            final String s = (value instanceof String) ? (String) value : SharedFunctionHelpers.textOf(value);
            // The width counts characters: '😀😀' fits a VARCHAR(2) (live-verified).
            if (st.getMaxLength() > 0 && CodePointText.length(s) > st.getMaxLength()) {
                throw new ColumnLengthException(st.getMaxLength(), s);
            }
            return s;
        }
        if (type instanceof BinaryType && value instanceof BinaryValue) {
            // A BINARY column's declared width is enforced on every write, the way a VARCHAR's is: the
            // value is refused rather than truncated, and the DML envelope names the table and column.
            final BinaryType binary = (BinaryType) type;
            final BinaryValue bytes = (BinaryValue) value;
            if (binary.getMaxLength() > 0 && bytes.length() > binary.getMaxLength()) {
                throw ColumnLengthException.forBinary(binary.getMaxLength(), bytes.toHex());
            }
            return value;
        }
        if (type instanceof NumericType && NumericType.isApproximate(type)) {
            // A FLOAT column HOLDS A DOUBLE: every write path on the account converts the value to the
            // nearest double, so a stored 0.1 is 0.10000000000000000555 and a stored nineteen-digit
            // integer is 1234567890123456768. Keeping the value's arrival class instead — a BigDecimal
            // from a literal, a Long from an integer — made the same column hold three carriers and
            // answer three different things through ::NUMBER, comparison and SUM.
            final Double approximate = ApproximateValues.written(value);
            return approximate != null ? approximate : value;
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
            // Snowflake's DML write path is stricter about field WIDTH than its CAST: an over-wide field
            // (a 3-digit month/day/second, a 5-digit year) is rejected here even though the same literal
            // casts fine (live-verified — see AutoTemporalParser.hasOverWideIsoFields).
            if (value instanceof CharSequence
                    && AutoTemporalParser.hasOverWideIsoFields(value.toString())) {
                throw new RuntimeException((type.getName().toUpperCase().startsWith("DATE")
                    ? "Date '" : "Timestamp '") + value + "' is not recognized");
            }
            // A string (or other temporal) written into a DATE/TIME/TIMESTAMP column becomes a real
            // LocalDate/LocalTime/LocalDateTime — the same value TO_DATE/TO_TIMESTAMP would produce — so a
            // string-inserted timestamp compares equal to a computed one (e.g. under EXCEPT / joins).
            // A value written into a TIME or TIMESTAMP column is truncated to the column's DECLARED
            // fractional precision (live: '10:00:00.123456789' into a TIMESTAMP_NTZ(0) reads back
            // 10:00:00.000, into a TIMESTAMP_NTZ(3) 10:00:00.123).
            return SharedFunctionHelpers.atDeclaredPrecision(
                SharedFunctionHelpers.toTemporalValue(type.getName().toUpperCase(), value),
                ((DateTimeType) type).getPrecision());
        }
        if (type instanceof BinaryType) {
            if (value instanceof BinaryValue) {
                return value;
            }
            if (value instanceof byte[]) {
                return BinaryValue.of((byte[]) value);
            }
            // Snowflake's implicit VARCHAR-to-BINARY conversion interprets the text as hex.
            return BinaryValue.fromHex(value.toString());
        }
        if (type instanceof ArrayType) {
            // Writing into an ARRAY column follows TO_ARRAY semantics: an existing array passes through
            // (typed — a JSON-text string is wrapped keeping its exact text), any other non-null variant
            // value is wrapped in a one-element array — the fixture idiom
            // `INSERT ... SELECT PARSE_JSON('{...}')` then reads it back as arr[0].
            if (ArrayFunctionHelper.parseArray(value) != null) {
                return value instanceof VariantValue ? value : VariantValue.of(value.toString());
            }
            final JsonNode node = ArrayFunctionHelper.parseNode(value);
            if (node != null && node.isNull()) {
                return value instanceof VariantValue ? value : VariantValue.of(value.toString());
            }
            final ArrayNode wrapped = ArrayFunctionHelper.MAPPER.createArrayNode();
            wrapped.add(ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, value));
            return VariantValue.ofNode(wrapped);
        }
        if (type instanceof ObjectType && value instanceof String) {
            // An object-shaped JSON text written into an OBJECT column becomes a typed cell with its
            // exact text; anything else keeps today's permissive pass-through.
            final JsonNode objectNode = ArrayFunctionHelper.parseNode(value);
            if (objectNode != null && (objectNode.isObject() || objectNode.isNull())) {
                return VariantValue.of((String) value);
            }
        }
        if (type instanceof VariantType && value instanceof String) {
            // JSON text written into a VARIANT column becomes a typed cell (exact text kept); a plain
            // string that is not valid JSON stays a string, as before.
            final JsonNode variantNode = ArrayFunctionHelper.parseNode(value);
            if (variantNode != null
                    && (variantNode.isObject() || variantNode.isArray() || variantNode.isNull())) {
                return VariantValue.of((String) value);
            }
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
        if (value == null || type.getScale() < 0 || !isFixedPointNumeric(type.getName())) {
            return value;
        }
        // The column's ONE carrier: a scale-0 column rounds a fractional write to a whole value
        // (live-verified: INSERT of 10.5 into a NUMBER column stores 11) held as a Long, a scaled
        // column pads or rounds to its scale — the same rule the storage applies on every write.
        return ExactValues.written(value, type);
    }

    private static boolean isFixedPointNumeric(final String name) {
        final String upper = name.toUpperCase();
        return upper.equals("NUMBER") || upper.equals("DECIMAL") || upper.equals("NUMERIC")
            || upper.equals("INTEGER") || upper.equals("INT") || upper.equals("BIGINT")
            || upper.equals("SMALLINT") || upper.equals("TINYINT") || upper.equals("BYTEINT");
    }

    /**
     * Execute TRUNCATE TABLE
     */
    public void executeTruncate(final String tableName, final boolean ifExists) {
        // IF EXISTS forgives only the table's own absence, never its database's or schema's (live-verified).
        catalog.requireOwningSchema(QualifiedName.parse(tableName));
        try {
            // Resolve the table
            final Table table;
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
            final String fullyQualifiedName = getFullyQualifiedTableName(tableName);

            emptyTableContents(fullyQualifiedName);
            logger.trace("Truncated table: {}", tableName);

        } catch (final SecurityException e) {
            throw e; // Let security exceptions propagate
        } catch (final Exception e) {
            throw StatementErrors.propagate(e);
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
        // Truncation also discards the table's COPY load history: a re-COPY after TRUNCATE
        // reloads the same files, while after DELETE it still skips them.
        resetCopyLoadHistory(fullyQualifiedName);
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
    /**
     * PUT — live's nine-column result shape: compressions, the constant ENCRYPTED marker, and a
     * message cell. AUTO_COMPRESS defaults to TRUE, gzipping an uncompressed source and renaming
     * the target {@code name.gz}; re-PUTting an identical file answers SKIPPED with live's own
     * sentence (OVERWRITE=TRUE forces the upload).
     */
    public ResultSet executePutFromContext(final FrostlakeParser.PutStatementContext ctx) {
        final String putLocation = stageRefToLocation(ctx.stageRef());
        final Path stageDir = resolveCopyBaseDir(putLocation);
        if (stageDir == null) {
            throw new RuntimeException("PUT target stage has no local directory");
        }
        // The target path names a directory of the stage's flat namespace: each file lands at <path>/<name>, beside
        // a staged file that carries the path's own name (see StagePathSegments).
        final StagePrefix putTarget = resolveStagePrefix(putLocation);
        final String putPrefix = putTarget == null || putTarget.getRoot() == null ? null
            : putTarget.getPrefix().isEmpty() || putTarget.getPrefix().endsWith("/") ? putTarget.getPrefix()
            : putTarget.getPrefix() + "/";
        final String localPath = stripFileScheme(ctx.STRING_LITERAL() != null
            ? extractStringLiteral(ctx.STRING_LITERAL()) : ctx.FILE_URL().getText());
        boolean autoCompress = true;
        boolean overwrite = false;
        for (final FrostlakeParser.StageFileOptionContext option : ctx.stageFileOption()) {
            final String key = option.optionKey().getText().toUpperCase();
            final boolean value = "TRUE".equalsIgnoreCase(option.copyOptionValue().getText());
            if ("AUTO_COMPRESS".equals(key)) {
                autoCompress = value;
            } else if ("OVERWRITE".equals(key)) {
                overwrite = value;
            }
        }
        final List<ResultSetColumn> cols = Arrays.asList(
            new ResultSetColumn("source", StatementResultWidths.TRANSFER_TEXT),
            new ResultSetColumn("target", StatementResultWidths.TRANSFER_TEXT),
            new ResultSetColumn("source_size", StatementResultWidths.TRANSFER_SIZE),
            new ResultSetColumn("target_size", StatementResultWidths.TRANSFER_SIZE),
            new ResultSetColumn("source_compression", StatementResultWidths.TRANSFER_TEXT),
            new ResultSetColumn("target_compression", StatementResultWidths.TRANSFER_TEXT),
            new ResultSetColumn("status", StatementResultWidths.TRANSFER_TEXT),
            new ResultSetColumn("encryption", StatementResultWidths.TRANSFER_TEXT),
            new ResultSetColumn("message", StatementResultWidths.TRANSFER_TEXT));
        final List<Row> rows = new ArrayList<>();
        try {
            if (putPrefix == null) {
                Files.createDirectories(stageDir);
            }
            for (final Path src : resolveLocalSources(localPath)) {
                final String sourceName = src.getFileName().toString();
                final boolean sourceCompressed = sourceName.toLowerCase().endsWith(".gz");
                final boolean compressing = autoCompress && !sourceCompressed;
                final String targetName = compressing ? sourceName + ".gz" : sourceName;
                final Path dest = putPrefix == null ? stageDir.resolve(targetName)
                    : StagePathSegments.fileToWrite(putTarget.getRoot(), putPrefix + targetName);
                final byte[] payload = compressing
                    ? gzipBytes(Files.readAllBytes(src)) : Files.readAllBytes(src);
                final int sourceSize = (int) Files.size(src);
                final String sourceCompression = sourceCompressed ? "GZIP" : "NONE";
                final String targetCompression = compressing || sourceCompressed ? "GZIP" : "NONE";
                if (!overwrite && Files.isRegularFile(dest)
                        && Arrays.equals(Files.readAllBytes(dest), payload)) {
                    rows.add(new Row(Arrays.asList(sourceName, targetName, sourceSize, 0,
                        sourceCompression, targetCompression, "SKIPPED", "",
                        "File with same destination name and checksum already exists: " + targetName)));
                    continue;
                }
                Files.write(dest, payload);
                rows.add(new Row(Arrays.asList(sourceName, targetName, sourceSize,
                    (int) Files.size(dest), sourceCompression, targetCompression,
                    "UPLOADED", "ENCRYPTED", "")));
            }
        } catch (final IOException e) {
            throw new RuntimeException("PUT failed: " + e.getMessage(), e);
        }
        return new ResultSet(cols, rows);
    }

    /** The gzip of {@code bytes}; GZIPOutputStream writes no timestamp, so equal input gives equal output. */
    public static byte[] gzipBytes(final byte[] bytes) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (final GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(bytes);
        }
        return out.toByteArray();
    }

    /** GET @stage 'file://localdir' — copy staged files into a local directory. */
    /** GET's local destination: a quoted string, or an unquoted file URL. */
    private static String stripTargetUrl(final FrostlakeParser.GetStatementContext ctx) {
        return ctx.STRING_LITERAL() != null
            ? ParseTreeText.extractStringLiteral(ctx.STRING_LITERAL()) : ctx.FILE_URL().getText();
    }

    public ResultSet executeGetFromContext(final FrostlakeParser.GetStatementContext ctx) {
        final StagePrefix staged = resolveStagePrefix(stageRefToLocation(ctx.stageRef()));
        final Path stageDir = staged.getRoot();
        final String localDir = stripFileScheme(stripTargetUrl(ctx));
        // Live's five columns — the constant DECRYPTED marker beside an empty message.
        final List<ResultSetColumn> cols = Arrays.asList(
            new ResultSetColumn("file", StatementResultWidths.TRANSFER_TEXT),
            new ResultSetColumn("size", StatementResultWidths.TRANSFER_SIZE),
            new ResultSetColumn("status", StatementResultWidths.TRANSFER_TEXT),
            new ResultSetColumn("encryption", StatementResultWidths.TRANSFER_TEXT),
            new ResultSetColumn("message", StatementResultWidths.TRANSFER_TEXT));
        final List<Row> rows = new ArrayList<>();
        if (stageDir == null || !Files.exists(stageDir)) {
            return new ResultSet(cols, rows);
        }
        // The file column is the path relative to the STAGE ROOT (subdirectories kept, no stage
        // name), and the encryption marker follows the stage: a server-side-encrypted stage
        // answers an empty string where the client-side default answers DECRYPTED. Live-verified.
        final String location = stageRefToLocation(ctx.stageRef());
        final String reference = location.startsWith("@") ? location.substring(1) : location;
        final int slash = reference.indexOf('/');
        final String stagePart = slash >= 0 ? reference.substring(0, slash) : reference;
        final String encryptionMarker = serverSideEncryptedStage(stagePart) ? "" : "DECRYPTED";
        try {
            final Path dest = Paths.get(localDir);
            Files.createDirectories(dest);
            // The stage path is a prefix, so GET reaches files at any depth and lands them all in ONE local
            // directory by their bare names. Two that share a name cannot both arrive: one is downloaded and
            // each other one is answered as a COLLISION, with no size and no encryption marker.
            //
            // Which one wins follows the order the files are WALKED, and the account leaves that walk to its client:
            // snowflake-jdbc gathers the stage-relative paths into a HashSet, fills a HashMap sized to their count
            // from that set, and walks the map. Each file a later one displaces names the file that displaced it,
            // the last one walked is downloaded, and the rows follow the same walk, the files that collide with
            // nothing included. The same two collections over the same names reproduce it exactly (live-verified
            // over six name sets, each uploaded in several orders).
            final Map<String, Path> listed = new LinkedHashMap<>();
            for (final Path src : staged.files(null)) {
                listed.put(staged.relative(src), src);
            }
            final List<String> names = new ArrayList<>(listed.keySet());
            Collections.sort(names);
            final Set<String> sources = new HashSet<>(names);
            final Map<String, Path> walk = new HashMap<>(sources.size());
            for (final String name : sources) {
                walk.put(name, listed.get(name));
            }
            final List<String> walked = new ArrayList<>();
            final Map<String, String> displacedBy = new HashMap<>();
            final Map<String, String> holder = new HashMap<>();
            for (final Map.Entry<String, Path> entry : walk.entrySet()) {
                final String bare = StagePathSegments.diskName(entry.getValue());
                final String previous = holder.put(bare, entry.getKey());
                if (previous != null) {
                    displacedBy.put(previous, entry.getKey());
                }
                walked.add(entry.getKey());
            }
            for (final String displayed : walked) {
                final String displacer = displacedBy.get(displayed);
                if (displacer != null) {
                    rows.add(new Row(Arrays.asList(displayed, 0, "COLLISION", "",
                        displayed + " has same name as " + displacer)));
                    continue;
                }
                final Path src = walk.get(displayed);
                final Path out = dest.resolve(StagePathSegments.diskName(src));
                Files.copy(src, out, StandardCopyOption.REPLACE_EXISTING);
                rows.add(new Row(Arrays.asList(displayed, (int) Files.size(out),
                    "DOWNLOADED", encryptionMarker, "")));
            }
        } catch (final IOException e) {
            throw new RuntimeException("GET failed: " + e.getMessage(), e);
        }
        return new ResultSet(cols, rows);
    }

    /** Whether a named stage declares SNOWFLAKE_SSE encryption (user and table stages do not). */
    private boolean serverSideEncryptedStage(final String stagePart) {
        if (stagePart.startsWith("~") || stagePart.startsWith("%")) {
            return false;
        }
        try {
            final Stage stage = catalog.getStage(stagePart);
            return stage.getEncryptionType() != null
                && "SNOWFLAKE_SSE".equalsIgnoreCase(stage.getEncryptionType());
        } catch (final RuntimeException e) {
            return false;
        }
    }

    /** REMOVE/RM @stage [PATTERN = '…'] — delete staged files (optionally matching a regex pattern). */
    public ResultSet executeRemoveFromContext(final FrostlakeParser.RemoveStatementContext ctx) {
        final EngineConfig cfg = getEngineConfig();
        if (cfg == null || !cfg.isRemoveCommandEnabled()) {
            throw new RuntimeException(
                "REMOVE/RM is disabled by default. Enable it by setting 'command.removeEnabled=true' in frostlake.properties.");
        }
        final String location = stageRefToLocation(ctx.stageRef());
        final String pattern = ctx.PATTERN() != null ? extractStringLiteral(ctx.STRING_LITERAL()) : null;
        final List<ResultSetColumn> cols = Arrays.asList(
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("result", StringType.VARCHAR));
        final List<Row> rows = new ArrayList<>();
        // The path selects by prefix, at any depth, and PATTERN matches the file's stored path — the
        // same reading LIST and COPY give it (live-verified: REMOVE @st/f1 removes f1 and f1.csv).
        for (final Path f : resolveStagePrefix(location).files(pattern)) {
            final boolean deleted = f.toFile().delete();
            // Live names the file stage-root-relative (with the lowercase stage name prefixed for
            // a named stage) and answers a lowercase `removed`.
            rows.add(new Row(Arrays.asList(stagedDisplayName(location, f),
                deleted ? "removed" : "failed")));
        }
        return new ResultSet(cols, rows);
    }

    /**
     * How LIST and REMOVE display a staged file, live-verified: the path relative to the STAGE
     * ROOT (not the referenced subdirectory), prefixed with the lowercase stage name for a named
     * stage; user and table stages show the bare root-relative path.
     */
    public String stagedDisplayName(final String fromLocation, final Path file) {
        final String reference = fromLocation.startsWith("@") ? fromLocation.substring(1) : fromLocation;
        final int slash = reference.indexOf('/');
        final String stagePart = slash >= 0 ? reference.substring(0, slash) : reference;
        final Path root = resolveCopyBaseDir("@" + stagePart);
        final String relative = root != null && file.startsWith(root)
            ? StagePathSegments.written(root.relativize(file).toString().replace('\\', '/'))
            : StagePathSegments.diskName(file);
        if (stagePart.startsWith("~") || stagePart.startsWith("%")) {
            return relative;
        }
        return stagePart.toLowerCase() + "/" + relative;
    }

    /** LIST @stage[/path] [PATTERN] — see {@link StageListing}. */
    public ResultSet executeListFromContext(final FrostlakeParser.ListStatementContext ctx) {
        return new StageListing(this).list(ctx);
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
        return resolveStageFilePath(stageRefToLocation(stageRef));
    }

    /** Resolve a location string (e.g. {@code 'file://…/script.sql'}) to its local file path. */
    public Path resolveStageFilePath(final String location) {
        // Only a stage keeps a name other names continue inside the directory of that name.
        final Path resolved = resolveCopyBaseDir(location);
        return location != null && location.startsWith("@") ? StagePathSegments.ownFileOf(resolved) : resolved;
    }

    /**
     * What the account answers when a named stage holds no file at the location: {@code File '/dir/f.sql' not found
     * in stage 'DB.SCHEMA.ST'.}, the path as written after the stage. Null for any other location.
     *
     * @param location the location, {@code @stage/path}
     * @return the sentence, or null
     */
    public String missingStageFileSentence(final String location) {
        final String[] names = namedStageNames(location);
        if (names == null) {
            return null;
        }
        final int slash = location.indexOf('/');
        return "File '" + (slash >= 0 ? location.substring(slash) : "/") + "' not found in stage '" + names[0] + "."
            + names[1] + "." + names[2] + "'.";
    }

    /**
     * The file URL BUILD_STAGE_FILE_URL gives a named stage's file, its path spelled as the function spells a path it
     * reads from a row: the directory table's FILE_URL.
     *
     * @param location the stage, {@code @stage}
     * @param relative the file's stage-relative name
     * @return the URL, or null when the location names no named stage
     */
    public String stageFileUrl(final String location, final String relative) {
        final String[] names = relative == null ? null : namedStageNames(location);
        return names == null ? null : BuildStageFileUrl.fileUrl(StageUrlFunction.serverUrl(engineConfig), names[0],
            names[1], names[2], relative, false);
    }

    /** The database, schema and stage names, as stored, of the named stage a location reaches; null for any other. */
    private String[] namedStageNames(final String location) {
        if (location == null || !location.startsWith("@") || location.startsWith("@~") || location.startsWith("@%")) {
            return null;
        }
        final int slash = location.indexOf('/');
        final String stageName = slash >= 0 ? location.substring(1, slash) : location.substring(1);
        try {
            final Stage stage = catalog.getStage(stageName);
            if (stage == null) {
                return null;
            }
            final Schema owner = catalog.resolveOwningSchema(stageName);
            return new String[]{owner.getDatabaseName(), owner.getName(), stage.getName()};
        } catch (final RuntimeException unresolved) {
            return null;
        }
    }

    /**
     * A {@code @…} stage location split into the stage's root and the path written after it, which selects
     * files as a PREFIX of their stage-relative names (see {@link StagePrefix}). Answers null for any other
     * location — a URL or a local path keeps its own reading.
     *
     * @param location the location as {@link #stageRefToLocation} encodes it
     * @return the split reference, or null when the location is not a stage reference
     */
    public StagePrefix resolveStagePrefix(final String location) {
        if (location == null || !location.startsWith("@")) {
            return null;
        }
        final String body = location.substring(1);
        final int slash = body.indexOf('/');
        final String stagePart = slash >= 0 ? body.substring(0, slash) : body;
        final String path = slash >= 0 ? body.substring(slash + 1) : "";
        final String stageOnly = "@" + stagePart;
        final StageKind kind = stagePart.startsWith("~") ? StageKind.USER
            : stagePart.startsWith("%") ? StageKind.TABLE : StageKind.NAMED;
        final String tableName = kind == StageKind.TABLE
            ? QualifiedName.parse(stagePart.substring(1)).last() : null;
        return new StagePrefix(kind, resolveCopyBaseDir(stageOnly), path, slash >= 0,
            copyPatternPrefix(stageOnly), tableName);
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
            return internalStageDir("tables", tableStageOwner(tableName), sep >= 0 ? tableRef.substring(sep) : "");
        }
        if (fromLocation.startsWith("@")) {
            // @stage or @stage/sub/path — split the stage name from an optional path beneath it.
            final String ref = fromLocation.substring(1);
            final int slash = ref.indexOf('/');
            final String stageName = slash >= 0 ? ref.substring(0, slash) : ref;
            final Stage stage = catalog.getStage(stageName);
            if (stage == null) {
                return null;
            }
            final String subPath = slash >= 0 ? ref.substring(slash + 1) : "";
            if (stage.getLocalPath() == null) {
                if (stage.getType() == StageType.INTERNAL) {
                    // A URL-less internal named stage is backed by an engine-managed directory,
                    // so an EXISTING empty stage acts like one — a FILES list naming absent files
                    // refuses, a bare load answers the 0-files status — instead of disappearing
                    // behind a null directory. The directory is named after the stage the reference
                    // resolved to, under its stored names.
                    return internalStageDir("stages", stageOwner(stageName, stage), "/" + subPath);
                }
                return null;
            }
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

    /**
     * The named stage the stage functions reach, by its exact canonical names — the one an unquoted name folds to or a
     * quoted one keeps. Its location is the URL an external stage was created with, or an internal stage's own
     * directory as a {@code file://} URL ending in a slash.
     *
     * @param database the database, canonical
     * @param schema   the schema, canonical
     * @param name     the stage's name, canonical
     * @return the stage, or null when no named stage has that name
     */
    public NamedStage namedStage(final String database, final String schema, final String name) {
        final Schema owner;
        try {
            owner = catalog.databaseExact(database).schemaExact(schema);
        } catch (final RuntimeException missing) {
            return null;
        }
        if (name == null || !owner.hasStageExact(name)) {
            return null;
        }
        final Stage stage = owner.getStage(name);
        Path root = stage.getLocalPath();
        if (root == null && stage.getType() == StageType.INTERNAL) {
            root = internalStageDir("stages",
                QualifiedName.key(owner.getDatabaseName(), owner.getName(), stage.getName()), "");
        }
        String location = stage.getUrl();
        if (location == null && root != null) {
            final String local = root.toAbsolutePath().normalize().toString().replace('\\', '/');
            location = "file://" + (local.startsWith("/") ? "" : "/") + local + (local.endsWith("/") ? "" : "/");
        }
        return new NamedStage(owner.getDatabaseName(), owner.getName(), stage.getName(), location, root);
    }

    /** Encode a stage reference as a COPY location string: {@code @name[/path]}, {@code @~[/path]}, or {@code @%table[/path]}. */
    public String stageRefToLocation(final FrostlakeParser.StageRefContext ctx) {
        final String path = ctx.stagePath() != null ? ctx.stagePath().getText() : "";
        StageReferenceShape.refuseEmptyParts(ctx, catalog);
        if (ctx.TILDE() != null) {
            return "@~" + path;
        }
        if (ctx.PERCENT() != null) {
            // @%table or @namespace.%table: the stage of the table the parts name, spelled @%<table's name>.
            return "@%" + QualifiedName.join(StageReferenceShape.nameParts(ctx).toArray(new String[0])) + path;
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

    /**
     * A NEW internal named stage starts EMPTY, exactly like a real account's: the engine-managed
     * directory is durable on disk, so without this a re-created stage would inherit the files a
     * dropped same-name predecessor staged.
     */
    public void resetInternalStageDir(final String stageName) {
        resetInternalStageDir("stages", stageName);
    }

    /** The same reset for a TABLE's implicit stage — a new table's stage is empty likewise. */
    public void resetInternalTableStageDir(final String tableName) {
        resetInternalStageDir("tables", tableName);
    }

    private void resetInternalStageDir(final String kind, final String objectName) {
        final Stage stage = "stages".equals(kind) ? catalog.getStage(objectName) : null;
        final Path dir = internalStageDir(kind, stage != null ? stageOwner(objectName, stage)
            : "tables".equals(kind) ? tableStageOwner(objectName) : getFullyQualifiedTableName(objectName), "");
        if (Files.isDirectory(dir)) {
            try (final java.util.stream.Stream<Path> walk = Files.walk(dir)) {
                final List<Path> paths = new ArrayList<>();
                final Iterator<Path> iterator = walk.iterator();
                while (iterator.hasNext()) {
                    paths.add(iterator.next());
                }
                Collections.sort(paths, Collections.reverseOrder());
                for (final Path path : paths) {
                    Files.deleteIfExists(path);
                }
            } catch (final IOException e) {
                logger.warn("Could not reset internal stage directory {}", dir, e);
            }
        }
        // The empty root is materialized right away, so a fresh stage behaves like one — LIST
        // answers no rows and COPY reaches its normal dispatch — before the first PUT.
        try {
            Files.createDirectories(dir);
        } catch (final IOException e) {
            logger.warn("Could not create internal stage directory {}", dir, e);
        }
    }

    /**
     * Local directory for an implicit internal stage: {@code <internalRoot>/<kind>/<name>[/subPath]}.
     * The segment is folded, because the name reaching here is not always a parsed identifier: a stage
     * path is a STRING ({@code @st/hello.txt}), so the writer's spelling and the reader's need not match
     * where the object's own name would. Two stages whose names differ only in case therefore share one
     * directory.
     */
    private Path internalStageDir(final String kind, final String name, final String subPath) {
        final String root = engineConfig != null ? engineConfig.getStageInternalLocalRoot()
            : System.getProperty("user.home") + "/.frostlake_stages/internal";
        final Path base = Paths.get(root, kind, stageDirectory(kind, name));
        final String sub = subPath.startsWith("/") ? subPath.substring(1) : subPath;
        if (sub.isEmpty()) {
            return base;
        }
        final Path resolved = StagePathSegments.resolve(base, sub);
        // Nothing a stage path spells may reach outside the stage's own directory, whatever the
        // segments say. The escaping above already makes this unreachable; it stands as the rule.
        if (!resolved.normalize().startsWith(base.normalize())) {
            throw new RuntimeException("Stage path reaches outside its stage: " + subPath);
        }
        return resolved;
    }


    /**
     * The owner of a named stage's directory: the stage under its stored name, in the database and schema the
     * reference resolved to under theirs — so {@code @st} written in a string reaches the directory of the stage
     * created as {@code ST}, and a stage created as {@code "st"} keeps a directory of its own.
     */
    private String stageOwner(final String stageName, final Stage stage) {
        final Schema schema = catalog.resolveOwningSchema(stageName);
        return QualifiedName.key(schema.getDatabaseName(), schema.getName(), stage.getName());
    }

    /**
     * The owner of a table stage's directory: the table under its stored names when the reference resolves to
     * one, else the qualified name as given.
     */
    private String tableStageOwner(final String tableName) {
        final String qualified = getFullyQualifiedTableName(tableName);
        try {
            final Schema schema = catalog.resolveOwningSchema(tableName);
            if (schema.hasTable(QualifiedName.parse(qualified).last())) {
                final Table table = schema.getTable(QualifiedName.parse(qualified).last());
                return QualifiedName.key(schema.getDatabaseName(), schema.getName(), table.getName());
            }
        } catch (final RuntimeException unresolved) {
            // Not a table this session can resolve: the name as given names the directory.
        }
        return qualified;
    }

    /**
     * The directory segment an internal stage's files live under. A named stage and a table's stage belong to an
     * object named exactly, so two whose names differ only in case own two directories (live-verified: a file
     * written to {@code @ST} is not listed through {@code @"st"}); a user's stage keeps its folded name.
     */
    private static String stageDirectory(final String kind, final String name) {
        final String segment = sanitizeStageSegment(name);
        return "users".equals(kind) ? segment.toUpperCase() : segment;
    }

    /** Make a stage owner name (user / fully-qualified table) safe as a single path segment. */
    private static String sanitizeStageSegment(final String name) {
        return name.replaceAll("[/\\\\]", "_");
    }


    /**
     * The files a COPY from a URL or a local path addresses; a stage reference reads its path as a prefix
     * through {@link StagePrefix} instead. Regular files in {@code baseDir}, narrowed either by an explicit
     * {@code FILES = (…)} list or by a {@code PATTERN} regex — never by both. Live-verified on a real account: when a statement
     * carries both, the PATTERN is ignored outright. {@code FILES = ('good1.csv','good2.csv')} beside
     * {@code PATTERN = '.*good1.*'} loaded BOTH files, and {@code FILES = ('good1.csv')} beside a pattern
     * matching nothing still loaded good1 — so a FILES list is the whole selection, not a candidate set the
     * pattern then filters.
     *
     * <p>A FILES entry is a PATH relative to the FROM location, not a bare file name: the account loads
     * {@code FILES = ('sub/nested.csv')} from {@code @stage} and reports it as {@code stage/sub/nested.csv}.
     * Entries are returned in the order the statement wrote them (duplicates collapsed); a directory scan
     * keeps its name order.
     *
     * <p>{@code pattern} is a full-match regex; the string it must match is {@code patternPrefix + <name>},
     * because the account applies PATTERN to the file's whole internal path, not its bare name: a stage
     * holding {@code good.csv} matches {@code '.*good\.csv'} but neither {@code 'good\.csv'} nor
     * {@code '<stage>/good\.csv'}.
     */
    public List<Path> listCopyFiles(final Path baseDir, final String pattern, final List<String> files,
            final String patternPrefix) {
        final List<Path> result = new ArrayList<>();
        // A location naming ONE FILE selects just that file.
        if (baseDir != null && Files.isRegularFile(baseDir)) {
            result.add(baseDir);
            return result;
        }
        if (files != null && !files.isEmpty()) {
            for (final String name : new LinkedHashSet<>(files)) {
                final Path named = resolveStagedFile(baseDir, name);
                if (named != null && Files.isRegularFile(named)) {
                    result.add(named);
                }
            }
            return result;
        }
        final File[] children = baseDir.toFile().listFiles();
        if (children == null) {
            return result;
        }
        Arrays.sort(children);
        for (final File child : children) {
            if (!child.isFile()) {
                continue;
            }
            if (pattern != null && !(patternPrefix + child.getName()).matches(pattern)) {
                continue;
            }
            result.add(child.toPath());
        }
        return result;
    }

    /**
     * The local path a {@code FILES = (…)} entry names beneath {@code baseDir}, or null when it names nothing
     * addressable from there. A name is resolved as a relative path, so {@code 'sub/nested.csv'} reaches into
     * a subdirectory; one that escapes the stage — an absolute {@code '/good1.csv'} or a {@code '../'} climb —
     * resolves to null rather than to a file outside it. The account agrees on the escape: it reads
     * {@code FILES = ('/good1.csv')} as the (non-existent) {@code @stage//good1.csv} even though
     * {@code good1.csv} is right there, so a leading slash is not stripped. Returning null here does not mean
     * "absent" — the caller still has to test the path — but a null can only ever BE absent.
     */
    /**
     * The prefix PATTERN-matching prepends to a staged file's root-relative name, mirroring the
     * shape of the account's internal paths: {@code stages/<name>/} for a named stage,
     * {@code tables/<name>/} for a table stage and {@code users/<user>/} for the user stage (the
     * account puts an opaque id where the name sits here). Lower-cased, subpath included.
     */
    public String copyPatternPrefix(final String fromLocation) {
        if (fromLocation == null || !fromLocation.startsWith("@")) {
            return "";
        }
        String body = fromLocation.substring(1);
        while (body.endsWith("/")) {
            body = body.substring(0, body.length() - 1);
        }
        if (body.startsWith("%")) {
            return "tables/" + body.substring(1).toLowerCase() + "/";
        }
        if (body.startsWith("~")) {
            final String rest = body.substring(1);
            return "users/frostlake" + rest.toLowerCase() + "/";
        }
        return "stages/" + body.toLowerCase() + "/";
    }

    /** Forget a table's COPY load history — TRUNCATE and CREATE OR REPLACE / DROP TABLE all do. */
    public void resetCopyLoadHistory(final String fullyQualifiedTableName) {
        copyExecutor.forgetLoadHistory(fullyQualifiedTableName);
    }

    public Path resolveStagedFile(final Path baseDir, final String name) {
        if (baseDir == null || name == null || name.isEmpty()) {
            return null;
        }
        final Path root = baseDir.toAbsolutePath().normalize();
        final Path resolved = root.resolve(name).normalize();
        return resolved.startsWith(root) ? resolved : null;
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

    /** The canonical text of an ALIAS, which may be an identifier or one of the words legal only in a
     *  name position — see {@link ParseTreeText#getIdentifier(FrostlakeParser.AliasNameContext)}. */
    String getIdentifier(final FrostlakeParser.AliasNameContext ctx) {
        return ParseTreeText.getIdentifier(ctx);
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
        // Catalog first: the definitions trade places (and missing tables refuse there, with the
        // does-not-exist shape); then the row stores trade under the same keys, completing the swap.
        catalog.swapTables(tableA, tableB);
        storageEngine.swapTables(getFullyQualifiedTableName(tableA), getFullyQualifiedTableName(tableB));
    }

    /**
     * Resolve an {@code objectName} parse node to the name it denotes: {@code IDENTIFIER(<expr>)} evaluates
     * the expression (a string literal or a session/bind variable) to the name; otherwise it is the literal
     * qualified name as written. Lets object names be supplied dynamically (Snowflake IDENTIFIER()).
     */
    public String resolveObjectName(final FrostlakeParser.ObjectNameContext ctx) {
        if (ctx.KW_IDENTIFIER_OPEN() != null) {
            return ctx.KW_IDENTIFIER_OPEN().getText().toUpperCase(Locale.ROOT);
        }
        if (ctx.identifierArgument() != null) {
            rejectUnboundObjectBind(ctx.identifierArgument(), null, targetsARowChange(ctx));
            final ExpressionEvaluator eval = new ExpressionEvaluator(null, functionRegistry, catalog, this);
            final Object nameVal = eval.evaluate(getOriginalText(ctx.identifierArgument()), null);
            if (nameVal == null) {
                throw new RuntimeException("IDENTIFIER() expression evaluated to null");
            }
            rejectInvalidIdentifierReference(nameVal.toString(), ctx.identifierArgument());
            // The value is an identifier reference, not an already-resolved name: an unquoted string
            // folds to upper case, a double-quoted one keeps its case. Passing it through raw made
            // IDENTIFIER('test_table') reach a table stored as TEST_TABLE only because lookup was
            // case-insensitive; it is exact now.
            return SqlIdentifiers.identifierReferenceText(nameVal.toString().trim());
        }
        // Fold the identifier parts (unquoted -> upper-case, quoted preserved) rather than returning the
        // raw text, so a created object's canonical name matches Snowflake.
        return getQualifiedName(ctx.qualifiedName());
    }

    /**
     * The name a statement reads only as a WHOLE reference — a rename's target — resolved as an
     * {@code objectName} is, the {@code IDENTIFIER()} form through its argument's value.
     *
     * @param ctx the name
     * @return the name it denotes
     */
    public String resolveWholeObjectName(final FrostlakeParser.WholeObjectNameContext ctx) {
        return ctx.identifierArgument() == null ? ParseTreeText.getQualifiedName(ctx.qualifiedName())
            : SqlIdentifiers.identifierReferenceText(identifierArgumentValue(ctx.identifierArgument()));
    }

    /**
     * {@link #resolveWholeObjectName}'s canonical parts, one per dotted level.
     *
     * @param ctx the name
     * @return the parts
     */
    public String[] resolveWholeObjectNameParts(final FrostlakeParser.WholeObjectNameContext ctx) {
        return ctx.identifierArgument() == null ? ParseTreeText.qualifiedNameParts(ctx.qualifiedName())
            : SqlIdentifiers.identifierReferenceParts(identifierArgumentValue(ctx.identifierArgument()));
    }

    /** An IDENTIFIER() argument's value, refused as live refuses an unbound or unreadable one. */
    private String identifierArgumentValue(final FrostlakeParser.IdentifierArgumentContext argument) {
        rejectUnboundObjectBind(argument, null, false);
        final Object value = new ExpressionEvaluator(null, functionRegistry, catalog, this)
            .evaluate(getOriginalText(argument), null);
        if (value == null) {
            throw new RuntimeException("IDENTIFIER() expression evaluated to null");
        }
        rejectInvalidIdentifierReference(value.toString(), argument);
        return value.toString().trim();
    }

    /**
     * The object an ALTER statement names, read as a reference where it is written as one: a TABLE, VIEW or
     * MATERIALIZED VIEW takes {@code IDENTIFIER('t')} on live, where every other target takes a plain name.
     *
     * @param ctx the statement
     * @return the name it denotes
     */
    public String alterTargetName(final FrostlakeParser.AlterStatementContext ctx) {
        return ctx.objectName() != null ? resolveObjectName(ctx.objectName())
            : ParseTreeText.getQualifiedName(ctx.qualifiedName());
    }

    /**
     * {@link #alterTargetName}'s canonical parts, one per dotted level.
     *
     * @param ctx the statement
     * @return the parts
     */
    public String[] alterTargetNameParts(final FrostlakeParser.AlterStatementContext ctx) {
        return ctx.objectName() != null ? resolveObjectNameParts(ctx.objectName())
            : ParseTreeText.qualifiedNameParts(ctx.qualifiedName());
    }

    /**
     * Canonical name PARTS of an {@code objectName} — one element per dotted level, so a quoted
     * identifier containing a dot stays one part where splitting {@link #resolveObjectName}'s
     * joined spelling would break it. The literal path reads the parse tree; the
     * {@code IDENTIFIER(<expr>)} path evaluates the expression and canonicalises its VALUE per
     * dotted part (the string IS the identifier reference, so its dots and quotes are the value's
     * own syntax).
     */
    public String[] resolveObjectNameParts(final FrostlakeParser.ObjectNameContext ctx) {
        if (ctx.KW_IDENTIFIER_OPEN() != null) {
            return new String[] {ctx.KW_IDENTIFIER_OPEN().getText().toUpperCase(Locale.ROOT)};
        }
        if (ctx.identifierArgument() != null) {
            return resolveIdentifierArgument(ctx.identifierArgument(), targetsARowChange(ctx));
        }
        return ParseTreeText.qualifiedNameParts(ctx.qualifiedName());
    }

    /**
     * The name parts an IDENTIFIER() argument spells where it names no object a statement changes rows of — a
     * class, for one.
     *
     * @param argument the argument written inside IDENTIFIER()
     * @return the canonical name parts
     */
    public String[] resolveIdentifierArgument(final FrostlakeParser.IdentifierArgumentContext argument) {
        return resolveIdentifierArgument(argument, false);
    }

    private String[] resolveIdentifierArgument(final FrostlakeParser.IdentifierArgumentContext argument,
                                               final boolean unpositioned) {
        rejectUnboundObjectBind(argument, null, unpositioned);
        final ExpressionEvaluator eval = new ExpressionEvaluator(null, functionRegistry, catalog, this);
        final Object nameVal = eval.evaluate(getOriginalText(argument), null);
        if (nameVal == null) {
            throw new RuntimeException("IDENTIFIER() expression evaluated to null");
        }
        rejectInvalidIdentifierReference(nameVal.toString(), argument);
        return SqlIdentifiers.identifierReferenceParts(nameVal.toString().trim());
    }

    /**
     * Refuses an IDENTIFIER() object reference whose argument is a bind variable nothing binds, before the
     * argument is read, as live refuses it while compiling: {@code Bind variable for object "tn" AS tn not set}
     * at the argument, the name spelled as an identifier and then as written or as the alias the FROM gives it
     * ({@code AS X}). A positional {@code ?} is named by its place among the statement's. An UPDATE's or a
     * DELETE's target is refused at line 0, position -1 (all live-verified). A running block resolves its own
     * variables, and names an undeclared one an invalid identifier at the colon.
     */
    private void rejectUnboundObjectBind(final FrostlakeParser.IdentifierArgumentContext argument, final String alias,
                                         final boolean unpositioned) {
        final Token at = argument.getStart();
        final String name;
        if (argument.QUESTION() != null) {
            name = String.valueOf(positionalBindOrdinal(argument, at.getTokenIndex()));
        } else if (argument.COLON() != null) {
            name = argument.identifier() != null ? argument.identifier().getText() : argument.INTEGER_LITERAL().getText();
            final ProceduralExecutor procedural = getProceduralExecutor();
            if (procedural != null && procedural.hasVariable(name)) {
                return;
            }
            if (procedural != null && procedural.isExecutingBlock()) {
                throw new UndeclaredScriptVariableException(name.toUpperCase(Locale.ROOT),
                    new SourcePosition(at.getLine(), at.getCharPositionInLine()));
            }
        } else {
            return;
        }
        final String detail = "Bind variable for object " + SqlIdentifiers.spellCanonical(name) + " AS "
            + (alias != null ? alias : name) + " not set";
        throw new RuntimeException(unpositioned ? SqlCompilationError.at(0, -1, detail)
            : SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(), detail));
    }

    /** Whether an object name is the target an UPDATE or a DELETE changes rows of. */
    private static boolean targetsARowChange(final FrostlakeParser.ObjectNameContext name) {
        return name.getParent() instanceof FrostlakeParser.UpdateStatementContext
            || name.getParent() instanceof FrostlakeParser.DeleteStatementContext;
    }

    /** The place of the positional bind at {@code tokenIndex} among the {@code ?} marks of its statement. */
    private static int positionalBindOrdinal(final ParserRuleContext node, final int tokenIndex) {
        ParserRuleContext root = node;
        while (root.getParent() != null && !(root instanceof FrostlakeParser.StatementContext)) {
            root = root.getParent();
        }
        return countPositionalBinds(root, tokenIndex);
    }

    private static int countPositionalBinds(final ParseTree node, final int tokenIndex) {
        if (node instanceof TerminalNode) {
            final Token token = ((TerminalNode) node).getSymbol();
            return token.getType() == FrostlakeParser.QUESTION && token.getTokenIndex() <= tokenIndex ? 1 : 0;
        }
        int count = 0;
        for (int i = 0; i < node.getChildCount(); i++) {
            count += countPositionalBinds(node.getChild(i), tokenIndex);
        }
        return count;
    }

    /**
     * Refuses an IDENTIFIER() argument whose value is no identifier reference ({@code 'my table'},
     * {@code '1abc'}, {@code ''}), as the account does before any lookup. The echo is the argument as
     * written, quotes and all, or a variable by its own name, at the argument's position.
     */
    private void rejectInvalidIdentifierReference(final String value,
                                                  final FrostlakeParser.IdentifierArgumentContext argument) {
        if (SqlIdentifiers.isIdentifierReference(value)) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.at(argument.getStart().getLine(),
            argument.getStart().getCharPositionInLine(), "invalid identifier '" + getOriginalText(argument) + "'"));
    }

    /** Re-key a renamed table's row storage: the new name keeps the old table's database/schema. */
    public void renameTableStorage(final String oldName, final String newShortName) {
        final String oldQualified = getFullyQualifiedTableName(oldName);
        // Re-keyed by its parts, not at the last dot, which may sit inside a quoted "a.b".
        final String[] parts = QualifiedName.parse(oldQualified).parts();
        parts[parts.length - 1] = newShortName;
        storageEngine.renameTable(oldQualified, QualifiedName.key(parts));
    }

    /** Re-key a renamed database's row storage to its new name (ALTER DATABASE RENAME TO). */
    public void renameDatabaseStorage(final String oldDatabase, final String newDatabase) {
        storageEngine.renameDatabase(oldDatabase, newDatabase);
    }

    /** Re-key a renamed or moved schema's row storage to its new database and name (ALTER SCHEMA RENAME TO). */
    public void renameSchemaStorage(final String oldDatabase, final String oldSchema, final String newDatabase,
                                    final String newSchema) {
        storageEngine.renameSchema(oldDatabase, oldSchema, newDatabase, newSchema);
    }

    /** Re-key a moved table's row storage to a new database/schema/name (cross-schema RENAME TO). */
    public void moveTableStorage(final String oldName, final String targetDatabase,
                                 final String targetSchema, final String newShortName) {
        final String oldQualified = getFullyQualifiedTableName(oldName);
        storageEngine.renameTable(oldQualified, QualifiedName.key(targetDatabase, targetSchema, newShortName));
    }

    /**
     * Backfill every existing row of a table after ALTER TABLE ADD COLUMN, appending one value for the
     * new column so stored rows match the widened schema — otherwise every read of the new column throws
     * IndexOutOfBounds. Snowflake populates existing rows with the column's DEFAULT literal when present,
     * otherwise NULL; the default is evaluated once (via the same path INSERT uses) and shared across
     * rows.
     */
    /**
     * Removes a dropped column's slot from everything that holds the table's rows by POSITION: the stored
     * rows with their time-travel history, the change records of the streams over the table, and whatever
     * rows open transactions have buffered for it. The one step every column-dropping path shares — the
     * catalog drop alone left each row a slot too wide, so every later read was shifted.
     *
     * <p>A temporary table leaves the streams alone: they are found by name, and the name a temporary
     * table takes may be a permanent table's whose streams keep that table's width.
     *
     * @param fullyQualifiedName the table, fully qualified
     * @param index              the dropped column's position before the drop
     * @param temporary          whether the table is a temporary one
     */
    public void dropColumnValues(final String fullyQualifiedName, final int index, final boolean temporary) {
        final TableStorage storage = storageEngine.getTableStorage(fullyQualifiedName);
        if (storage != null) {
            storage.dropColumnSlot(index);
        }
        if (streamManager != null && !temporary) {
            streamManager.onColumnDropped(fullyQualifiedName, index);
        }
        for (final Transaction open : transactionManager.getActiveTransactions()) {
            open.getWriteSet().dropColumnSlot(fullyQualifiedName, index);
        }
    }

    /**
     * Fills a newly added column into everything that holds the table's rows by POSITION — the mirror of
     * {@link #dropColumnValues}: the stored rows with their time-travel history, the change records of the
     * streams over the table, and the rows open transactions have buffered for it. Every one of them takes
     * the column's default, or NULL, which is what the account answers for a row from before the add
     * through each of those surfaces.
     *
     * @param fullyQualifiedName the table, fully qualified
     * @param table              the table, with the new column already added as its last
     * @param newColumn          the added column
     */
    public void backfillColumn(final String fullyQualifiedName, final Table table, final TableColumn newColumn) {
        final Object value = evaluateDefaultValue(newColumn.getDefaultValue());
        final TableStorage storage = storageEngine.getTableStorage(fullyQualifiedName);
        final Object stored = storage != null ? storage.appendColumnSlot(value) : value;
        final int width = table.columnCount();
        if (streamManager != null && !table.isTemporary()) {
            streamManager.onColumnAdded(fullyQualifiedName, width, stored);
        }
        for (final Transaction open : transactionManager.getActiveTransactions()) {
            open.getWriteSet().appendColumnSlot(fullyQualifiedName, width, stored);
        }
    }

    /**
     * The positional argument expressions of a function call. The grammar's function-argument list is a
     * {@code booleanExpr} list (a superset of {@code expression}); a plain argument is a {@code ValueExpr}
     * wrapping its {@code expression}, unwrapped here so callers keep working with {@code ExpressionContext}
     * (e.g. the {@code LAST_QUERY_ID()} instanceof check for RESULT_SCAN).
     */
    /**
     * The positional arguments' texts, in order. A plain argument is its text as written. A subquery written
     * without parentheses of its own, FLATTEN(SELECT PARSE_JSON('[1,2]')), is parenthesised, so it evaluates
     * as the scalar subquery it is.
     */
    private static List<String> funcArgTexts(final FrostlakeParser.FunctionCallExprContext funcCtx) {
        final List<String> out = new ArrayList<>();
        if (funcCtx.functionArgList() != null) {
            for (final FrostlakeParser.FunctionArgContext arg : funcCtx.functionArgList().functionArg()) {
                if (arg.selectStatement() != null) {
                    out.add("(" + ParseTreeText.getOriginalText(arg.selectStatement()) + ")");
                } else if (arg.booleanExpr() instanceof FrostlakeParser.ValueExprContext) {
                    out.add(ParseTreeText.getOriginalText(
                        ((FrostlakeParser.ValueExprContext) arg.booleanExpr()).expression()));
                }
            }
        }
        return out;
    }

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
     * The output column name for a select-item value expression, live-verified: a (qualified) column
     * reference's column name read from the parse tree (its last identifier) — parentheses around a
     * plain reference are transparent, {@code SELECT (amount)} is named {@code AMOUNT} — and any other
     * expression is named by its SOURCE TEXT folded to upper case. The fold is blunt: whitespace and
     * newlines survive exactly as written ({@code amount    +    1} names {@code AMOUNT    +    1}),
     * and string literals and quoted identifiers INSIDE the text fold too ({@code "lc" || 'x'} names
     * {@code "LC" || 'X'}). Only an explicit quoted alias escapes it, at the call sites.
     */
    /** The ordinal a {@code $N} select item names, or 0 for any other name. */
    private static int positionalOrdinalOf(final String columnName) {
        if (columnName == null || columnName.length() < 2 || columnName.charAt(0) != '$') {
            return 0;
        }
        for (int i = 1; i < columnName.length(); i++) {
            if (!Character.isDigit(columnName.charAt(i))) {
                return 0;
            }
        }
        return Integer.parseInt(columnName.substring(1));
    }

    /** The {@code $N} spelling of a qualified name's last part, or null when that part is a name. */
    private static String positionalPartOf(final FrostlakeParser.QualifiedNameContext qn) {
        if (qn.namePart().isEmpty()) {
            return null;
        }
        final FrostlakeParser.NamePartContext last = qn.namePart().get(qn.namePart().size() - 1);
        final FrostlakeParser.IdentifierContext name = last.columnDefName() == null ? null
            : last.columnDefName().identifier();
        return name != null && name.POSITIONAL_PARAMETER() != null ? name.POSITIONAL_PARAMETER().getText() : null;
    }

    /**
     * The column a positional select item reads over several relations (see {@link RelationScopes}): the one
     * name scope wide enough for a bare {@code $N}, or the qualified relation's own column for {@code t.$N}.
     *
     * @param qn           the item's name as written
     * @param positional   its N
     * @param table        the FROM clause's merged relation
     * @param aliasToTable the FROM-clause keys
     * @param allTables    every relation in scope, in combined-row order
     * @return the column, or null when the item reads none (it is refused elsewhere)
     */
    private static RelationSlot positionalSlot(final FrostlakeParser.QualifiedNameContext qn, final int positional,
                                               final Table table, final Map<String, Table> aliasToTable,
                                               final List<Table> allTables) {
        final RelationScopes scopes = new RelationScopes(table, allTables);
        if (qn.namePart().isEmpty()) {
            final List<RelationSlot> candidates = scopes.positional(positional);
            return candidates.size() == 1 ? candidates.get(0) : null;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(qn);
        final String relationName = parts[parts.length - 2];
        Table relation = aliasToTable == null ? null : aliasToTable.get(relationName);
        if (relation == null && aliasToTable != null) {
            for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(relationName)) {
                    relation = entry.getValue();
                    break;
                }
            }
        }
        return scopes.qualifiedPositional(relation, positional);
    }

    private String selectItemColumnName(final FrostlakeParser.ExpressionContext valueExpr) {
        final FrostlakeParser.ExpressionContext simple = SelectItemAccessors.unwrapParens(valueExpr);
        if (simple instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameContext qn =
                ((FrostlakeParser.QualifiedNameExprContext) simple).qualifiedName();
            final String[] parts = ParseTreeText.qualifiedNameParts(qn);
            // A positional item keeps its dollar spelling — live names SELECT $2 exactly $2, not
            // the COLUMN<N> name it resolves through.
            if (parts.length == 1 && qn.nameStartPart().identifier() != null
                    && qn.nameStartPart().identifier().POSITIONAL_PARAMETER() != null) {
                return qn.nameStartPart().identifier().POSITIONAL_PARAMETER().getText();
            }
            // So does a qualified one: live names SELECT t.$1 exactly $1.
            final String qualifiedDollar = positionalPartOf(qn);
            if (qualifiedDollar != null) {
                return qualifiedDollar;
            }
            return parts[parts.length - 1];
        }
        // An unaliased CONNECT_BY_ROOT item is named after the COLUMN, like a plain column reference —
        // live-verified: `SELECT CONNECT_BY_ROOT nm …` comes back labelled NM, not after the item's text.
        if (simple instanceof FrostlakeParser.ConnectByRootExprContext) {
            final String[] parts = ParseTreeText.qualifiedNameParts(
                ((FrostlakeParser.ConnectByRootExprContext) simple).qualifiedName());
            return parts[parts.length - 1];
        }
        // An unaliased IDENTIFIER(<value>) item is named after the COLUMN the value names, as a written
        // reference is: SELECT IDENTIFIER('a') comes back labelled A, and IDENTIFIER('"c"') labelled c
        // (live-verified). Only the whole item — inside a larger expression the item keeps its own text.
        final String named = identifierItemColumnName(simple);
        if (named != null) {
            return named;
        }
        return valueExpr != null ? getOriginalText(valueExpr).toUpperCase() : "";
    }

    /**
     * The column an {@code IDENTIFIER(<value>)} select item names, when the item is one and its value
     * can be read without a row, else null.
     *
     * @param item the item's value expression, parentheses already unwrapped
     * @return the column's name, or null
     */
    private String identifierItemColumnName(final FrostlakeParser.ExpressionContext item) {
        if (!(item instanceof FrostlakeParser.FunctionCallExprContext)) {
            return null;
        }
        final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) item;
        if (!"IDENTIFIER".equalsIgnoreCase(call.functionName().getText())
                || call.functionArgList() == null || call.functionArgList().functionArg().size() != 1) {
            return null;
        }
        try {
            final Object value = new ExpressionEvaluator(null, functionRegistry, catalog, this)
                .evaluate(getOriginalText(call.functionArgList().functionArg(0)), null);
            if (value == null || !SqlIdentifiers.isIdentifierReference(value.toString())) {
                return null;
            }
            return FromSourceNames.lastPart(SqlIdentifiers.identifierReferenceParts(value.toString().trim()));
        } catch (final RuntimeException unreadable) {
            return null;   // the item's own evaluation reports it
        }
    }

    /**
     * The generated name of an UNALIASED select item — {@link #selectItemColumnName} for a value
     * expression, and the same upper-cased-source-text rule applied to the whole item when it has no
     * value expression (a boolean projection like {@code SELECT a AND b} names {@code A AND B}).
     */
    private String unaliasedItemName(final FrostlakeParser.SelectItemContext item) {
        final FrostlakeParser.ExpressionContext valueExpr = SelectItemAccessors.getItemValueExpr(item);
        if (valueExpr != null) {
            return selectItemColumnName(valueExpr);
        }
        final ParserRuleContext full = SelectItemAccessors.getItemExpression(item);
        return full != null ? getOriginalText(full).toUpperCase() : "";
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
        final String[] parts = catalog.withoutAccount(QualifiedName.parse(tableName).parts(), 3);
        if (parts.length == 3) {
            // Already fully qualified - uppercase all parts
            return QualifiedName.key(parts[0], parts[1], parts[2]);
        } else if (parts.length == 2) {
            // Has schema.table, need database. The current names are the stored ones, which keep a quoted
            // name's case; the storage key folds every part, as the three-part branch does.
            return QualifiedName.key(catalog.getCurrentDatabase(), parts[0], parts[1]);
        } else {
            // Just table name, need both database and schema
            return QualifiedName.key(catalog.getCurrentDatabase(), catalog.getCurrentSchema(), parts[0]);
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
            final String expr = ((String) defaultValue).toUpperCase().trim();
            switch (expr) {
                // The statement's own UTC reading — a host-zone clock here diverges from the
                // query-side CURRENT_* family around local midnight.
                case "CURRENT_TIMESTAMP":
                case "CURRENT_TIMESTAMP()":
                    return StatementClock.now();
                case "CURRENT_DATE":
                case "CURRENT_DATE()":
                    return StatementClock.now().toLocalDate();
                case "CURRENT_TIME":
                case "CURRENT_TIME()":
                    return StatementClock.now().toLocalTime();
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

    /**
     * Run a view's body where the VIEW lives: its bare names resolve in the view's own database and
     * schema, whatever the reading session's context, and CURRENT_DATABASE() / CURRENT_SCHEMA() inside
     * it answer the view's (live-verified). The caller's context is put back however the body ends.
     *
     * @param viewName the view as the statement names it
     * @param body     the body to run
     * @return its results
     */
    private List<ResultSet> executeInViewScope(final String viewName, final String body) {
        final String[] scope = viewScope(viewName);
        final String savedDatabase = catalog.getCurrentDatabase();
        final String savedSchema = catalog.getCurrentSchema();
        catalog.restoreContext(scope[0], scope[1]);
        try {
            return executeRelationBody(body);
        } finally {
            catalog.restoreContext(savedDatabase, savedSchema);
        }
    }

    /**
     * Plan a view's body where the VIEW lives, as {@link #executeInViewScope} runs one; the view's context is
     * in force again while the planned body runs.
     *
     * @param viewName the view as the statement names it
     * @param body     the body to plan
     * @return the planned body
     */
    private PlannedQuery planInViewScope(final String viewName, final String body) {
        final String[] scope = viewScope(viewName);
        final String savedDatabase = catalog.getCurrentDatabase();
        final String savedSchema = catalog.getCurrentSchema();
        catalog.restoreContext(scope[0], scope[1]);
        final PlannedQuery planned;
        try {
            planned = planRelationBody(body);
        } finally {
            catalog.restoreContext(savedDatabase, savedSchema);
        }
        return new PlannedQuery(planned.shape, planned.description) {
            @Override
            ResultSet run() {
                final String database = catalog.getCurrentDatabase();
                final String schema = catalog.getCurrentSchema();
                catalog.restoreContext(scope[0], scope[1]);
                try {
                    return planned.run();
                } finally {
                    catalog.restoreContext(database, schema);
                }
            }
        };
    }

    /**
     * A relation body — a view's — planned as {@link #executeRelationBody} runs one: under the relation-body
     * scope, its items waiting for the reading statement, which is in force again when the planned body runs.
     */
    private PlannedQuery planRelationBody(final String body) {
        final boolean outerBody = RelationBody.begin();
        try {
            return underRelationBody(planStatementText(body), true);
        } finally {
            RelationBody.end(outerBody);
        }
    }

    /**
     * A body run on its own — a dynamic table's query — planned as {@link #execute(String)} runs one: reading
     * its values as it produces them, even inside a relation body, while planned and while run.
     */
    private PlannedQuery planDetachedBody(final String body) {
        final boolean outerBody = RelationBody.suspend();
        try {
            return underRelationBody(planStatementText(body), false);
        } finally {
            RelationBody.end(outerBody);
        }
    }

    /** The planned body run with the relation-body scope entered, or suspended, as it was while planning. */
    private static PlannedQuery underRelationBody(final PlannedQuery planned, final boolean inBody) {
        return new PlannedQuery(planned.shape, planned.description) {
            @Override
            ResultSet run() {
                final boolean outerBody = inBody ? RelationBody.begin() : RelationBody.suspend();
                try {
                    return planned.run();
                } finally {
                    RelationBody.end(outerBody);
                }
            }
        };
    }

    /**
     * A SELECT statement's text planned as {@link #executeStatementText} runs one: its leading comment's
     * offset in force for the positions reported while planning and while running. The text is parsed once
     * however many statements read it.
     */
    private PlannedQuery planStatementText(final String sql) {
        rejectEmptyStatement(sql);
        final SourcePosition origin = LeadingCommentOffset.of(sql);
        final SourcePosition displacedComment = LeadingCommentOffset.begin(origin);
        try {
            final PlannedQuery planned = planSelectStatement(parseSelectStatementCached(sql));
            return new PlannedQuery(planned.shape, planned.description) {
                @Override
                ResultSet run() {
                    final SourcePosition displaced = LeadingCommentOffset.begin(origin);
                    try {
                        return planned.run();
                    } finally {
                        LeadingCommentOffset.end(displaced);
                    }
                }
            };
        } finally {
            LeadingCommentOffset.end(displacedComment);
        }
    }

    /** {@link #parseSelectStatement}, parsed once per distinct text: every statement over a view reads its body. */
    private FrostlakeParser.SelectStatementContext parseSelectStatementCached(final String sql) {
        final FrostlakeParser.SelectStatementContext cached = SELECT_CACHE.get(sql);
        if (cached != null) {
            return cached;
        }
        final FrostlakeParser.SelectStatementContext parsed = parseSelectStatement(sql);
        SELECT_CACHE.put(sql, parsed);
        return parsed;
    }

    /** The database and schema a view name places the view in, as {@link #resolveView} places it. */
    private String[] viewScope(final String viewName) {
        final String[] parts = catalog.withoutAccount(QualifiedName.parse(viewName).parts(), 3);
        if (parts.length >= 3) {
            return new String[] {parts[0], parts[1]};
        }
        if (parts.length == 2) {
            return new String[] {catalog.getCurrentDatabase(), parts[0]};
        }
        return new String[] {catalog.getCurrentDatabase(), catalog.getCurrentSchema()};
    }

    /**
     * Resolve a view's body shape at CREATE where the view will live, as {@link #executeInViewScope}
     * reads it: a bare name in the body is the view's schema's, and a missing one is named there.
     *
     * @param database   the view's database
     * @param schema     the view's schema
     * @param body       the body
     * @param columnNames the view's written column names, or null
     * @return the body's columns
     */
    public List<TableColumn> resolveViewShapeInScope(final String database, final String schema, final String body,
                                                     final List<String> columnNames) {
        final String savedDatabase = catalog.getCurrentDatabase();
        final String savedSchema = catalog.getCurrentSchema();
        final boolean savedCompiling = compilingViewBody;
        catalog.restoreContext(database, schema);
        compilingViewBody = true;
        try {
            return resolveRelationShape(body, columnNames);
        } finally {
            compilingViewBody = savedCompiling;
            catalog.restoreContext(savedDatabase, savedSchema);
        }
    }

    /**
     * The columns a query body's RESULT carries where a routine lives, each typed as the query types it rather
     * than settled to the widths a view stores: a cast to bare VARCHAR keeps its 134217728.
     *
     * @param database the routine's database
     * @param schema   the routine's schema
     * @param body     the query body
     * @return the result's columns, or null when the body resolves to no result
     */
    public List<TableColumn> resolveQueryResultColumnsInScope(final String database, final String schema,
                                                              final String body) {
        final String savedDatabase = catalog.getCurrentDatabase();
        final String savedSchema = catalog.getCurrentSchema();
        final boolean savedCompiling = compilingViewBody;
        catalog.restoreContext(database, schema);
        compilingViewBody = true;
        final boolean previous = RelationShapeOnly.begin();
        try {
            final List<ResultSet> results = execute(body);
            if (results.isEmpty() || results.get(0) == null) {
                return null;
            }
            return resultSetToTable(results.get(0), RESOLVED_SHAPE_NAME).getColumns();
        } finally {
            RelationShapeOnly.end(previous);
            compilingViewBody = savedCompiling;
            catalog.restoreContext(savedDatabase, savedSchema);
        }
    }

    /**
     * The name a FROM reference is looked up under: as written, or, while CREATE VIEW compiles its body,
     * completed to its full path in the view's schema, so that a miss names what live names.
     */
    private String viewScopeQualified(final String tableName) {
        if (!compilingViewBody) {
            return tableName;
        }
        final String[] parts = QualifiedName.parse(tableName).parts();
        if (parts.length == 1) {
            return QualifiedName.join(catalog.getCurrentDatabase(), catalog.getCurrentSchema(), parts[0]);
        }
        if (parts.length == 2) {
            return QualifiedName.join(catalog.getCurrentDatabase(), parts[0], parts[1]);
        }
        return tableName;
    }

    private View resolveView(final String viewName) {
        final String[] parts = catalog.withoutAccount(QualifiedName.parse(viewName).parts(), 3);
        final Schema schema;
        final String actualViewName;

        if (parts.length == 1) {
            // Unqualified: use current schema
            schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(catalog.getCurrentSchema());
            actualViewName = parts[0];
        } else if (parts.length == 2) {
            // schema.view (using current database)
            schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
            actualViewName = parts[1];
        } else {
            // database.schema.view
            schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
            actualViewName = parts[2];
        }

        // A TEMPORARY table of the name shadows the view for the session that made it, so the read
        // reaches the table - and uncovers the view again when the table is dropped (live-verified).
        return schema.hasTemporaryTable(actualViewName) ? null : schema.getView(actualViewName);
    }

    /** Resolve a MATERIALIZED VIEW by (optionally qualified) name; the schema getter is case-insensitive. */
    private MaterializedView resolveMaterializedView(final String name) {
        final String[] parts = QualifiedName.parse(name).parts();
        final Schema schema = parts.length == 1
            ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(catalog.getCurrentSchema())
            : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
            : catalog.getDatabase(parts[0]).getSchema(parts[1]);
        return schema.hasTemporaryTable(parts[parts.length - 1])
            ? null : schema.getMaterializedView(parts[parts.length - 1]);
    }

    /** Resolve a DYNAMIC TABLE by (optionally qualified) name; the schema getter is case-insensitive. */
    private DynamicTable resolveDynamicTable(final String name) {
        final String[] parts = QualifiedName.parse(name).parts();
        final Schema schema = parts.length == 1
            ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(catalog.getCurrentSchema())
            : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
            : catalog.getDatabase(parts[0]).getSchema(parts[1]);
        return schema.hasTemporaryTable(parts[parts.length - 1])
            ? null : schema.getDynamicTable(parts[parts.length - 1]);
    }

    /**
     * Find every relation an INSERT's source query reads, in the order written, and refuse the first
     * that names nothing, before the target is looked up. Live reports a missing source relation ahead
     * of a missing target or a bad column list, while a missing target still comes ahead of the source's
     * column and function errors (live-verified). Nothing is read: each name is only found, the way a
     * FROM clause finds it.
     *
     * @param source the INSERT's source query
     */
    public void requireSourceRelations(final FrostlakeParser.SelectStatementContext source) {
        requireRelations(source);
    }

    /**
     * {@link #requireSourceRelations} over any statement or part of one: every relation it reads, in
     * the order written, found as a FROM clause finds it, and the first that names nothing refused.
     *
     * @param statement the statement, or the part of it whose relations are to be found
     */
    /**
     * The object a GRANT, a REVOKE or a SHOW GRANTS names must exist, and is found before anything else
     * the statement names (live-verified). A table and a view resolve each other, the named kind wording
     * the miss; a schema, a database, a function, a sequence and a stage are each found in their own
     * place, a missing container refused as such. Other kinds are not looked up.
     *
     * @param objectType the kind the statement names, upper-case
     * @param parts      the object's name, part by part
     */
    public void requireSecurable(final String objectType, final String[] parts) {
        requireSecurable(objectType, parts, null);
    }

    /**
     * {@link #requireSecurable(String, String[])} with a routine's written argument count: a function none
     * of whose overloads takes that many arguments names nothing (live-verified).
     *
     * @param objectType     the kind the statement names, upper-case
     * @param parts          the object's name, part by part
     * @param signatureArity the number of argument types written after the name, or null for none
     */
    public void requireSecurable(final String objectType, final String[] parts, final Integer signatureArity) {
        switch (objectType) {
            case "TABLE":
            case "VIEW":
                requireNamedRelation(QualifiedName.join(parts), "TABLE".equals(objectType) ? "Table" : "View");
                return;
            case "SCHEMA":
                catalog.resolveSchema(QualifiedName.of(parts));
                return;
            case "DATABASE":
                catalog.databaseExact(parts[0]);
                return;
            case "FUNCTION":
            case "PROCEDURE": {
                // By the exact canonical name: GRANT USAGE ON PROCEDURE proccase() names no "procCase"
                // (live-verified).
                final Schema owner = catalog.requireOwningSchema(QualifiedName.of(parts));
                final String name = parts[parts.length - 1];
                final boolean function = "FUNCTION".equals(objectType);
                if (!takesArguments(function ? owner.getFunctionOverloads(name) : owner.getProcedureOverloads(name),
                        signatureArity)) {
                    throw new RuntimeException(SqlCompilationError.doesNotExist(function ? "Function" : "Procedure",
                        owner.qualifiedName(name)));
                }
                return;
            }
            case "SEQUENCE":
                catalog.requireOwningSchema(QualifiedName.of(parts)).getSequence(parts[parts.length - 1]);
                return;
            case "STAGE":
                catalog.requireOwningSchema(QualifiedName.of(parts)).getStage(parts[parts.length - 1]);
                return;
            case "PIPE":
                catalog.requireOwningSchema(QualifiedName.of(parts)).getPipe(parts[parts.length - 1]);
                return;
            case "TASK":
                catalog.requireOwningSchema(QualifiedName.of(parts)).getTask(parts[parts.length - 1]);
                return;
            case "WAREHOUSE":
                catalog.getWarehouse(parts[parts.length - 1]);
                return;
            default:
                requireSecurableMember(objectType, parts);
        }
    }

    /**
     * The kinds whose miss live words in a way of its own: a file format, a tag and a stream by the name as written, a
     * masking, row access or session policy by its full name, an integration and a resource monitor by theirs. This
     * engine holds no integration, resource monitor or session policy, so each of those is always a miss; a kind not
     * named here is not looked up (live-verified).
     */
    private void requireSecurableMember(final String objectType, final String[] parts) {
        final String written = QualifiedName.join(parts);
        final String name = parts[parts.length - 1];
        switch (objectType) {
            case "INTEGRATION":
                throw new RuntimeException(SqlCompilationError.doesNotExist("Integration", written));
            case "RESOURCE_MONITOR":
                throw new RuntimeException(SqlCompilationError.doesNotExist("Resource monitor", written));
            case "FILE_FORMAT":
                if (!catalog.requireOwningSchema(QualifiedName.of(parts)).hasFileFormat(name)) {
                    throw new RuntimeException(SqlCompilationError.doesNotExist("File format", written));
                }
                return;
            case "TAG":
                if (!catalog.requireOwningSchema(QualifiedName.of(parts)).hasTag(name)) {
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Tag", written));
                }
                return;
            case "STREAM": {
                final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                try {
                    schema.getStream(name);
                } catch (final RuntimeException missing) {
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Stream", written));
                }
                return;
            }
            case "MASKING_POLICY": {
                final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                if (schema.getMaskingPolicy(name) == null) {
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Masking policy",
                        schema.qualifiedName(name)));
                }
                return;
            }
            case "ROW_ACCESS_POLICY": {
                final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                if (schema.getRowAccessPolicy(name) == null) {
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Row access policy",
                        schema.qualifiedName(name)));
                }
                return;
            }
            case "SESSION_POLICY":
                throw new RuntimeException(SqlCompilationError.doesNotExist("Session policy",
                    catalog.requireOwningSchema(QualifiedName.of(parts)).qualifiedName(name)));
            case "NETWORK_POLICY":
                // An account object, named without a database or schema.
                if (catalog.getSecurityObjects().get(SecurityObjectKind.NETWORK_POLICY, name) == null) {
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Network policy", name));
                }
                return;
            case "NETWORK_RULE":
            case "PASSWORD_POLICY":
            case "SECRET": {
                final SecurityObjectKind kind = SecurityObjectKind.valueOf(objectType);
                final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                if (schema.getSecurityObjects().get(kind, name) == null) {
                    // A grant statement over a missing rule asks for two account privileges (live-verified).
                    throw new RuntimeException(kind == SecurityObjectKind.NETWORK_RULE
                        ? SqlCompilationError.networkRuleToGrantDoesNotExist(schema.qualifiedName(name))
                        : SqlCompilationError.doesNotExist(kind.noun(), schema.qualifiedName(name)));
                }
                return;
            }
            default:
                return;
        }
    }

    /**
     * The table or view a bare SHOW GRANTS ON name reaches. Live reads no other kind there, and refuses a name that
     * reaches neither as no object at all: {@code Object 'NOSUCH' does not exist or not authorized.}
     *
     * @param parts the name, part by part
     * @return the relation, as SHOW GRANTS reports it
     */
    public GrantedObject grantedRelation(final String[] parts) {
        final GrantedObject relation;
        try {
            relation = grantedObject("TABLE", parts, null);
        } catch (final RuntimeException missing) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Object", QualifiedName.join(parts)));
        }
        if (relation == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Object", QualifiedName.join(parts)));
        }
        return relation;
    }

    /**
     * The object a SHOW GRANTS ON names, found as {@link #requireSecurable} finds it: its kind as live
     * reports it, its name in full, its owner and when it was created. Null for a kind whose ownership is
     * not recorded here, or a routine none of whose overloads takes the arguments written.
     *
     * @param objectType the kind the statement names, upper-case
     * @param parts      the object's name, part by part
     * @param arity      the number of argument types written after a routine's name, or null for none
     */
    public GrantedObject grantedObject(final String objectType, final String[] parts, final Integer arity) {
        switch (objectType) {
            case "TABLE":
            case "VIEW": {
                final String written = QualifiedName.join(parts);
                try {
                    if (resolveMaterializedView(written) != null) {
                        return null;
                    }
                } catch (final RuntimeException notAMaterializedView) {
                    // not a materialized view
                }
                final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                View view = null;
                try {
                    view = resolveView(written);
                } catch (final RuntimeException notAView) {
                    // not a view
                }
                if (view != null) {
                    return new GrantedObject("VIEW", schema.qualifiedName(view.getName()), view.getOwner(),
                        view.getCreatedTime(), memberSpellings(schema, view.getName()));
                }
                final Table table = catalog.resolveTableAsWritten(written, "TABLE".equals(objectType) ? "Table" : "View");
                return new GrantedObject("TABLE", schema.qualifiedName(table.getName()), table.getOwner(),
                    table.getCreatedTime(), memberSpellings(schema, table.getName()));
            }
            case "SCHEMA": {
                final Schema schema = catalog.resolveSchema(QualifiedName.of(parts));
                final String full = QualifiedName.join(schema.getDatabaseName(), schema.getName());
                return new GrantedObject("SCHEMA", full, schema.getOwner(), schema.getCreatedTime(),
                    spellings(schema.getName(), full));
            }
            case "DATABASE": {
                final Database database = catalog.databaseExact(parts[0]);
                return new GrantedObject("DATABASE", database.getName(), database.getOwner(), database.getCreatedTime(),
                    spellings(database.getName()));
            }
            case "FUNCTION": {
                final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                for (final Function overload : schema.getFunctionOverloads(parts[parts.length - 1])) {
                    if (arity == null || overload.getParameters().size() == arity.intValue()) {
                        return functionGrant(schema, overload);
                    }
                }
                return null;
            }
            case "PROCEDURE": {
                final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                for (final Procedure overload : schema.getProcedureOverloads(parts[parts.length - 1])) {
                    if (arity == null || overload.getParameters().size() == arity.intValue()) {
                        return procedureGrant(schema, overload);
                    }
                }
                return null;
            }
            case "SEQUENCE": {
                final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                final Sequence sequence = schema.getSequence(parts[parts.length - 1]);
                return new GrantedObject("SEQUENCE", schema.qualifiedName(sequence.getName()), sequence.getOwner(),
                    sequence.getCreatedTime(), memberSpellings(schema, sequence.getName()));
            }
            case "STAGE": {
                final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                final Stage stage = schema.getStage(parts[parts.length - 1]);
                return new GrantedObject("STAGE", schema.qualifiedName(stage.getName()), stage.getOwner(),
                    stage.getCreatedTime(), memberSpellings(schema, stage.getName()));
            }
            case "FILE_FORMAT": {
                final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                final FileFormat format = schema.getFileFormat(parts[parts.length - 1]);
                return format == null ? null : new GrantedObject("FILE_FORMAT", schema.qualifiedName(format.getName()),
                    format.getOwner(), format.getCreatedTime(), memberSpellings(schema, format.getName()));
            }
            case "MASKING_POLICY": {
                final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                final MaskingPolicy policy = schema.getMaskingPolicy(parts[parts.length - 1]);
                return policy == null ? null : new GrantedObject("MASKING_POLICY", schema.qualifiedName(policy.getName()),
                    policy.getOwner(), policy.getCreatedTime(), memberSpellings(schema, policy.getName()));
            }
            case "ROW_ACCESS_POLICY": {
                final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                final RowAccessPolicy policy = schema.getRowAccessPolicy(parts[parts.length - 1]);
                return policy == null ? null : new GrantedObject("ROW_ACCESS_POLICY",
                    schema.qualifiedName(policy.getName()), policy.getOwner(), policy.getCreatedTime(),
                    memberSpellings(schema, policy.getName()));
            }
            case "STREAM": {
                final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                final Stream stream = schema.getStream(parts[parts.length - 1]);
                // A stream stamps the host's wall clock; read back in the host's zone it is the instant it was made.
                return new GrantedObject("STREAM", schema.qualifiedName(stream.getName()), stream.getOwner(),
                    stream.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant(),
                    memberSpellings(schema, stream.getName()));
            }
            case "TASK": {
                final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                final Task task = schema.getTask(parts[parts.length - 1]);
                if (task == null) {
                    return null;
                }
                return new GrantedObject("TASK", schema.qualifiedName(task.getName()), task.getOwner(),
                    task.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant(),
                    memberSpellings(schema, task.getName()));
            }
            case "PIPE": {
                final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                final Pipe pipe = schema.getPipe(parts[parts.length - 1]);
                if (pipe == null) {
                    return null;
                }
                return new GrantedObject("PIPE", schema.qualifiedName(pipe.getName()), pipe.getOwner(),
                    pipe.getCreatedTime(), memberSpellings(schema, pipe.getName()));
            }
            case "TAG": {
                final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                if (!schema.hasTag(parts[parts.length - 1])) {
                    return null;
                }
                final Tag tag = schema.getTag(parts[parts.length - 1]);
                return new GrantedObject("TAG", schema.qualifiedName(tag.getName()), tag.getOwner(), tag.getCreatedTime(),
                    memberSpellings(schema, tag.getName()));
            }
            case "NETWORK_POLICY": {
                // An account object: its full name is its own.
                final SecurityObject policy = catalog.getSecurityObjects().get(SecurityObjectKind.NETWORK_POLICY,
                    parts[parts.length - 1]);
                return policy == null ? null : new GrantedObject("NETWORK_POLICY", policy.getName(), policy.getOwner(),
                    policy.getCreatedTime(), spellings(policy.getName()));
            }
            case "NETWORK_RULE":
            case "PASSWORD_POLICY":
            case "SECRET": {
                final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                final SecurityObject object = schema.getSecurityObjects().get(SecurityObjectKind.valueOf(objectType),
                    parts[parts.length - 1]);
                return object == null ? null : new GrantedObject(objectType, schema.qualifiedName(object.getName()),
                    object.getOwner(), object.getCreatedTime(), memberSpellings(schema, object.getName()));
            }
            default:
                return null;
        }
    }

    /**
     * Make a role the owner of the object a GRANT OWNERSHIP names, found as {@link #grantedObject} finds it.
     *
     * @param objectType the kind the statement names, upper-case
     * @param parts      the object's name, part by part
     * @param arity      the number of argument types written after a routine's name, or null for none
     * @param owner      the new owner's name
     * @return false for a kind whose ownership is not recorded here, or a routine none of whose overloads takes the
     *         arguments written
     */
    public boolean assignOwner(final String objectType, final String[] parts, final Integer arity, final String owner) {
        switch (objectType) {
            case "TABLE":
            case "VIEW": {
                final String written = QualifiedName.join(parts);
                try {
                    if (resolveMaterializedView(written) != null) {
                        return false;
                    }
                } catch (final RuntimeException notAMaterializedView) {
                    // not a materialized view
                }
                View view = null;
                try {
                    view = resolveView(written);
                } catch (final RuntimeException notAView) {
                    // not a view
                }
                if (view != null) {
                    view.setOwner(owner);
                    return true;
                }
                catalog.resolveTableAsWritten(written, "TABLE".equals(objectType) ? "Table" : "View").setOwner(owner);
                return true;
            }
            case "SCHEMA":
                catalog.resolveSchema(QualifiedName.of(parts)).setOwner(owner);
                return true;
            case "DATABASE":
                catalog.databaseExact(parts[0]).setOwner(owner);
                return true;
            case "FUNCTION": {
                final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                for (final Function overload : schema.getFunctionOverloads(parts[parts.length - 1])) {
                    if (arity == null || overload.getParameters().size() == arity.intValue()) {
                        overload.setOwner(owner);
                        return true;
                    }
                }
                return false;
            }
            case "PROCEDURE": {
                final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                for (final Procedure overload : schema.getProcedureOverloads(parts[parts.length - 1])) {
                    if (arity == null || overload.getParameters().size() == arity.intValue()) {
                        overload.setOwner(owner);
                        return true;
                    }
                }
                return false;
            }
            case "SEQUENCE":
                catalog.requireOwningSchema(QualifiedName.of(parts)).getSequence(parts[parts.length - 1]).setOwner(owner);
                return true;
            case "STAGE":
                catalog.requireOwningSchema(QualifiedName.of(parts)).getStage(parts[parts.length - 1]).setOwner(owner);
                return true;
            case "STREAM":
                catalog.requireOwningSchema(QualifiedName.of(parts)).getStream(parts[parts.length - 1]).setOwner(owner);
                return true;
            case "FILE_FORMAT":
                catalog.requireOwningSchema(QualifiedName.of(parts)).getFileFormat(parts[parts.length - 1]).setOwner(owner);
                return true;
            case "MASKING_POLICY":
                catalog.requireOwningSchema(QualifiedName.of(parts)).getMaskingPolicy(parts[parts.length - 1]).setOwner(owner);
                return true;
            case "ROW_ACCESS_POLICY":
                catalog.requireOwningSchema(QualifiedName.of(parts)).getRowAccessPolicy(parts[parts.length - 1])
                    .setOwner(owner);
                return true;
            case "TASK":
                catalog.requireOwningSchema(QualifiedName.of(parts)).getTask(parts[parts.length - 1]).setOwner(owner);
                return true;
            case "PIPE": {
                final Pipe pipe = catalog.requireOwningSchema(QualifiedName.of(parts)).getPipe(parts[parts.length - 1]);
                // A running pipe keeps its owner: the account refuses the move until the pipe is paused.
                if (!pipe.isPaused()) {
                    throw new RuntimeException("SQL execution error:\nPipe " + pipe.getName().toUpperCase()
                        + " not in paused state. To pause pipe run ALTER PIPE " + pipe.getName().toUpperCase()
                        + " SET PIPE_EXECUTION_PAUSED=true");
                }
                pipe.setOwner(owner);
                return true;
            }
            case "TAG":
                catalog.requireOwningSchema(QualifiedName.of(parts)).getTag(parts[parts.length - 1]).setOwner(owner);
                return true;
            case "NETWORK_POLICY":
                catalog.getSecurityObjects().get(SecurityObjectKind.NETWORK_POLICY, parts[parts.length - 1]).setOwner(owner);
                return true;
            case "NETWORK_RULE":
            case "PASSWORD_POLICY":
            case "SECRET":
                catalog.requireOwningSchema(QualifiedName.of(parts)).getSecurityObjects()
                    .get(SecurityObjectKind.valueOf(objectType), parts[parts.length - 1]).setOwner(owner);
                return true;
            default:
                return false;
        }
    }

    /**
     * Refuse a statement that replaces, drops, renames or swaps an object the session does not own: OWNERSHIP
     * of it must be held by the primary role, a secondary role, or a role either inherits — ACCOUNTADMIN is no
     * exception. The object is found as {@link #grantedObject} finds it; when it is not there, or its kind
     * records no owner here, nothing is refused, and the statement's own lookup answers for a missing object.
     *
     * @param objectType the object's kind, upper-case, as GRANT OWNERSHIP names it
     * @param parts      the object's name, part by part
     * @param arity      the number of argument types written after a routine's name, or null for none
     */
    public void requireOwnership(final String objectType, final String[] parts, final Integer arity) {
        final GrantedObject granted;
        try {
            granted = grantedObject(objectType, parts, arity);
        } catch (final RuntimeException absent) {
            return;
        }
        requireOwnership(granted);
    }

    /**
     * As {@link #requireOwnership(String, String[], Integer)}, for the function overload a signature names:
     * the one a CREATE OR REPLACE FUNCTION or a DROP FUNCTION replaces or drops.
     */
    public void requireFunctionOwnership(final Schema schema, final String name, final List<DataType> argumentTypes) {
        final Function overload;
        try {
            overload = schema.getFunctionBySignature(name, argumentTypes);
        } catch (final RuntimeException absent) {
            return;
        }
        requireOwnership(functionGrant(schema, overload));
    }

    /** As {@link #requireFunctionOwnership}, for the procedure overload a signature names. */
    public void requireProcedureOwnership(final Schema schema, final String name, final List<DataType> argumentTypes) {
        final Procedure overload;
        try {
            overload = schema.getProcedureBySignature(name, argumentTypes);
        } catch (final RuntimeException absent) {
            return;
        }
        requireOwnership(procedureGrant(schema, overload));
    }

    private void requireOwnership(final GrantedObject granted) {
        if (securityManager == null || granted == null || securityManager.actsAsOwner(granted.getOwner())) {
            return;
        }
        throw new RuntimeException(SqlAccessControlError.ownershipRequired(granted.getKind(), granted.getBareName(),
            securityManager.getSessionContext().getCurrentRole(), granted.getName()));
    }

    /** A function overload as its grants name it: in full, with its argument types. */
    private static GrantedObject functionGrant(final Schema schema, final Function overload) {
        return new GrantedObject("FUNCTION",
            SqlIdentifiers.spellAlreadyCanonicalPath(schema.qualifiedName(overload.getName()))
                + argumentTypes(overload.getParameters()),
            overload.getOwner(), overload.getCreatedTime(), memberSpellings(schema, overload.getName()));
    }

    /** A procedure overload as its grants name it: in full, with its argument types. */
    private static GrantedObject procedureGrant(final Schema schema, final Procedure overload) {
        return new GrantedObject("PROCEDURE",
            SqlIdentifiers.spellAlreadyCanonicalPath(schema.qualifiedName(overload.getName()))
                + argumentTypes(overload.getParameters()),
            overload.getOwner(), overload.getCreatedTime(), memberSpellings(schema, overload.getName()));
    }

    /** A routine's argument types as its grants name it: {@code (NUMBER, VARCHAR)}. */
    private static String argumentTypes(final List<Parameter> parameters) {
        final StringBuilder types = new StringBuilder("(");
        for (int i = 0; i < parameters.size(); i++) {
            types.append(i > 0 ? ", " : "").append(parameters.get(i).getDataType().getName());
        }
        return types.append(')').toString();
    }

    /** The names a schema's member is reached by from where it lives: bare, with its schema, and in full. */
    private static List<String> memberSpellings(final Schema schema, final String name) {
        return spellings(name, QualifiedName.join(schema.getName(), name), schema.qualifiedName(name));
    }

    private static List<String> spellings(final String... names) {
        return new ArrayList<>(Arrays.asList(names));
    }

    /**
     * Whether one of a routine's overloads takes exactly {@code arity} arguments, or, with no arity written,
     * whether it has an overload at all.
     */
    private static boolean takesArguments(final List<? extends SqlObject> overloads, final Integer arity) {
        for (final SqlObject overload : overloads) {
            final List<Parameter> parameters = overload instanceof Function
                ? ((Function) overload).getParameters() : ((Procedure) overload).getParameters();
            if (arity == null || parameters.size() == arity.intValue()) {
                return true;
            }
        }
        return false;
    }

    /** A table, view or materialized view by that name, or the named kind's refusal. */
    private void requireNamedRelation(final String name, final String kind) {
        try {
            if (resolveView(name) != null) {
                return;
            }
        } catch (final RuntimeException notAView) {
            // not a view
        }
        try {
            if (resolveMaterializedView(name) != null) {
                return;
            }
        } catch (final RuntimeException notAMaterializedView) {
            // not a materialized view
        }
        catalog.resolveTableAsWritten(name, kind);
    }

    /**
     * A FROM clause whose sources register one name twice is refused, {@code duplicate alias 'A'} (see
     * FromSourceNames), once every relation the clause names has been found: live reports a missing
     * relation first, wherever it stands in the clause.
     */
    private void rejectDuplicateSourceNames(final FrostlakeParser.TableExpressionContext tableExpr,
                                            final Map<String, ResultSet> cteResults) {
        final FromSourceNames names = new FromSourceNames(this);
        names.registerSources(tableExpr);
        if (names.duplicate() == null) {
            return;
        }
        final Set<String> cteNames = new HashSet<>();
        collectCteNames(tableExpr, cteNames);
        if (cteResults != null) {
            for (final String cte : cteResults.keySet()) {
                cteNames.add(cte.toUpperCase());
            }
        }
        if (getCurrentCteContext() != null) {
            for (final String cte : getCurrentCteContext().keySet()) {
                cteNames.add(cte.toUpperCase());
            }
        }
        requireRelationsIn(tableExpr, cteNames);
        names.rejectDuplicate();
    }

    public void requireRelations(final ParseTree statement) {
        final Set<String> cteNames = new HashSet<>();
        collectCteNames(statement, cteNames);
        requireRelationsIn(statement, cteNames);
    }

    /**
     * An UPDATE's or DELETE's window and aggregate calls, judged once its names and argument types have
     * passed (live-verified):
     *
     * <pre>
     *   UPDATE t SET a = ROW_NUMBER() OVER (ORDER BY b)   Analytic function not allowed as target of UPDATE.
     *   UPDATE t SET a = 1 WHERE SUM(b) OVER () &gt; 0    the same, a window anywhere in the statement
     *   UPDATE t SET a = SUM(b)                           [PSEUDOCOLUMN(VOL_ID)] is not a valid group by expression
     *   UPDATE t SET a = GROUPING(b)                      GROUPING function without a GROUP BY.
     *   DELETE FROM t WHERE ROW_NUMBER() OVER (…) = 1     Window function […] appears outside of SELECT, …
     *   … WHERE SUM(b) &gt; 0                             Invalid aggregate function in where clause [SUM(T.B)]
     * </pre>
     *
     * The UPDATE's window sentence carries no compilation prefix on the account, and it outranks an
     * aggregate wherever each is written; a SET value's aggregate outranks the WHERE's.
     *
     * @param update       whether the statement is an UPDATE
     * @param setValues    the SET values, empty for a DELETE
     * @param where        the statement's WHERE, or null
     * @param table        the relation the statement's expressions read
     * @param aliasToTable the FROM or USING sources beside the target, or null when there are none
     * @param allTables    every relation in scope, or null when the target stands alone
     */
    public void rejectWindowsAndAggregatesInRewrite(final boolean update,
                                                    final List<? extends ParserRuleContext> setValues,
                                                    final FrostlakeParser.WhereClauseContext where, final Table table,
                                                    final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final List<FrostlakeParser.FunctionCallExprContext> windows = new ArrayList<>();
        for (final ParserRuleContext value : setValues) {
            collectWindowCallsOutsideNestedQueries(value, windows);
        }
        final List<FrostlakeParser.FunctionCallExprContext> whereWindows = new ArrayList<>();
        if (where != null) {
            collectWindowCallsOutsideNestedQueries(where, whereWindows);
        }
        final Map<String, Table> echoAliases = aliasToTable != null ? aliasToTable : new HashMap<String, Table>();
        final List<Table> echoTables = allTables != null ? allTables : new ArrayList<Table>();
        if (allTables == null) {
            echoTables.add(table);
        }
        final PlanEcho echo = new PlanEcho(table, echoAliases, echoTables, functionRegistry, catalog);
        // A name inside an OVER clause resolves first, as it does in a query.
        for (final FrostlakeParser.FunctionCallExprContext window : windows) {
            validateWindowKeyScope(window, table, echoAliases, echoTables, new HashSet<String>());
        }
        for (final FrostlakeParser.FunctionCallExprContext window : whereWindows) {
            validateWindowKeyScope(window, table, echoAliases, echoTables, new HashSet<String>());
        }
        if (update) {
            if (!windows.isEmpty() || !whereWindows.isEmpty()) {
                throw new RuntimeException("Analytic function not allowed as target of UPDATE.");
            }
            for (final ParserRuleContext value : setValues) {
                if (callsGrouping(value)) {
                    throw new RuntimeException(SqlCompilationError.of("GROUPING function without a GROUP BY."));
                }
                final List<ParserRuleContext> aggregates = new ArrayList<>();
                collectAggregateCallsOutsideNestedQueries(value, aggregates);
                if (!aggregates.isEmpty()) {
                    throw new RuntimeException(SqlCompilationError.of(
                        "[PSEUDOCOLUMN(VOL_ID)] is not a valid group by expression"));
                }
            }
        } else if (!whereWindows.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of("Window function [" + echo.printWindowCall(whereWindows.get(0))
                + "] appears outside of SELECT, QUALIFY, and ORDER BY clauses."));
        }
        if (where != null) {
            rejectAggregatesIn(where, "where clause", echo);
        }
    }

    /**
     * The calls written in these clauses, outside any query nested in them, whose names resolve to no
     * function: the list live's {@code Unknown functions …} sentence names, in the clauses' order.
     */
    List<FunctionCallExpression> unresolvableCallsIn(final List<? extends ParseTree> clauses) {
        final ExpressionEvaluator scan = new ExpressionEvaluator(null, functionRegistry, catalog, this);
        final List<FunctionCallExpression> unresolvable = new ArrayList<>();
        for (final ParseTree clause : clauses) {
            final List<ParserRuleContext> calls = new ArrayList<>();
            collectCallsOutsideNestedQueries(clause, calls);
            for (final ParserRuleContext call : calls) {
                try {
                    final Expression parsed = ExpressionEvaluator.parse(getOriginalText(call));
                    if (parsed instanceof FunctionCallExpression && !scan.resolvesToAFunctionName(parsed)) {
                        unresolvable.add((FunctionCallExpression) parsed);
                    }
                } catch (final RuntimeException notACallOnItsOwn) {
                    // judged where it is evaluated
                }
            }
        }
        return unresolvable;
    }

    /** Whether a clause holds an aggregate call of its own, outside any query nested in it. */
    boolean holdsAggregate(final ParseTree clause) {
        final List<ParserRuleContext> found = new ArrayList<>();
        collectAggregateCallsOutsideNestedQueries(clause, found);
        return !found.isEmpty();
    }

    /** Refuse the first aggregate call in a clause as {@code Invalid aggregate function in <clause> […]}. */
    void rejectAggregatesInClause(final ParserRuleContext clause, final String clauseName, final Table table,
                                  final Map<String, Table> aliasToTable, final List<Table> allTables) {
        rejectAggregatesIn(clause, clauseName, new PlanEcho(table, aliasToTable, allTables, functionRegistry, catalog));
    }

    /** Whether an expression calls GROUPING itself, outside any query nested in it. */
    private static boolean callsGrouping(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectClauseContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext
                && "GROUPING".equalsIgnoreCase(((FrostlakeParser.FunctionCallExprContext) node).functionName().getText())) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (callsGrouping(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    static void collectCteNames(final ParseTree node, final Set<String> into) {
        if (node instanceof FrostlakeParser.CteDefinitionContext) {
            into.add(ParseTreeText.namePartText(
                ((FrostlakeParser.CteDefinitionContext) node).nameStartPart()).toUpperCase());
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectCteNames(node.getChild(i), into);
        }
    }

    void requireRelationsIn(final ParseTree node, final Set<String> cteNames) {
        if (node instanceof FrostlakeParser.TableSourceContext
                && ((FrostlakeParser.TableSourceContext) node).tableQualifiedName() != null) {
            requireRelation(((FrostlakeParser.TableSourceContext) node).tableQualifiedName(), cteNames);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            requireRelationsIn(node.getChild(i), cteNames);
        }
    }

    /** One FROM name found as {@code planTableReference} finds it, in the same order, reading nothing. */
    private void requireRelation(final FrostlakeParser.TableQualifiedNameContext name, final Set<String> cteNames) {
        final String tableName = ParseTreeText.getQualifiedName(name);
        final boolean bareUnquotedDual = "DUAL".equalsIgnoreCase(tableName)
            && name.namePart().isEmpty()
            && name.nameStartPart().identifier() != null
            && name.nameStartPart().identifier().QUOTED_IDENTIFIER() == null;
        if (cteNames.contains(tableName.toUpperCase()) || bareUnquotedDual
                || executeSystemViewIfApplicable(tableName, null) != null) {
            return;
        }
        try {
            if (resolveView(tableName) != null) {
                return;
            }
        } catch (final RuntimeException notAView) {
            // not a view
        }
        try {
            if (resolveMaterializedView(tableName) != null) {
                return;
            }
        } catch (final RuntimeException notAMaterializedView) {
            // not a materialized view
        }
        try {
            if (resolveDynamicTable(tableName) != null) {
                return;
            }
        } catch (final RuntimeException notADynamicTable) {
            // not a dynamic table
        }
        if (findStream(tableName) == null) {
            catalog.resolveTableAsWritten(viewScopeQualified(tableName), "Object");
        }
    }

    /**
     * Check if the view is an INFORMATION_SCHEMA system view and execute it using SystemViews
     * @return ResultSet if it's a system view, null otherwise
     */
    private ResultSet executeSystemViewIfApplicable(final String tableName, final View view) {
        final String[] parts = QualifiedName.parse(tableName).parts();
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
            // database.schema.view — the database as resolved, since "db" and DB are two databases
            database = parts[0];
            schema = parts[1].toUpperCase();
            viewName = parts[2].toUpperCase();
        }

        // Check if this is INFORMATION_SCHEMA schema
        if (!"INFORMATION_SCHEMA".equals(schema)) {
            return null;
        }
        if (database == null) {
            // A schema-qualified INFORMATION_SCHEMA view belongs to the current database, and a session
            // with none has nowhere to read it from (live-verified).
            throw NoCurrentDatabaseRefusal.naming("SELECT");
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
            case "FILE_FORMATS":
                return systemViews.queryFileFormats(database, null);
            case "HYBRID_TABLES":
                return systemViews.queryHybridTables(database, null);
            case "TABLE_STORAGE_METRICS":
                return systemViews.queryTableStorageMetrics(database, null);
            case "INFORMATION_SCHEMA_CATALOG_NAME":
                return systemViews.queryInformationSchemaCatalogName(database);
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
            default:
                // Every other INFORMATION_SCHEMA view a real account exposes: present with its live
                // column shape and no rows, because this engine does not model those objects. A
                // name that is no INFORMATION_SCHEMA view at all falls through as null and is
                // reported as a non-existent object, exactly as live does. NB: STREAMS, TASKS and
                // TAGS deliberately live in NEITHER list — a real account has no such views (they
                // exist only under ACCOUNT_USAGE), and TAG_REFERENCES is a table FUNCTION there,
                // so answering any of them would accept a query Snowflake rejects.
                return unmodeledSystemViews.query(viewName);
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
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(table, functionRegistry, catalog, this);
        if (tableAlias != null && !tableAlias.equalsIgnoreCase(table.getName())) {
            final Map<String, Table> aliasToTable = new HashMap<>();
            aliasToTable.put(tableAlias.toUpperCase(), table);
            final List<Table> allTables = new ArrayList<>();
            allTables.add(table);
            evaluator.setMultiTableContext(aliasToTable, allTables);
        }
        // Parse the predicate once, then evaluate the AST per row — the string overload would take
        // the shared parse-cache monitor for every row.
        final Expression parsedWhere = evaluator.withNarrowingCastEqualitiesAnswered(ExpressionEvaluator.parse(whereExpr));
        final List<Row> filtered = new ArrayList<>();
        for (final Row row : rows) {
            final Object result = evaluator.evaluate(parsedWhere, row);
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

    /**
     * Refuse the statement when its OWN select list projects a column a projection policy denies.
     *
     * <p>A projection policy does not hide the value, it forbids the column from being projected at
     * all — so the column stays usable in a WHERE, an ORDER BY or an aggregate, and an inner select may
     * project it as long as the outermost one does not. That is why this runs for the statement's own
     * select and no other, and why the sentence lists the offending select-list ITEMS as written: a
     * plain reference reports the column, an expression over it reports the expression.
     */
    private void rejectRestrictedProjections(final FrostlakeParser.SelectClauseContext ctx,
                                             final List<String> projectionExpressions, final Table table,
                                             final Map<String, Table> aliasToTable) {
        if (outermostSelect == null || !isOutermostSelectClause(ctx)) {
            return;
        }
        final StringBuilder restricted = new StringBuilder();
        for (final String expression : projectionExpressions) {
            if (projectionDenied(expression, table, aliasToTable)) {
                // Each offending item on its own line, upper-cased however it was written.
                restricted.append("\n ").append(expression.toUpperCase(Locale.ROOT));
            }
        }
        if (restricted.length() > 0) {
            throw new RuntimeException(SqlCompilationError.PREFIX + " The following columns are"
                + " restricted by a Projection Policy. Please remove them from the list of projected"
                + " columns:\n" + restricted);
        }
    }

    /**
     * A JOIN POLICY forbids reading its table on its own: the query must INNER join it to a different
     * table on a column equality. Unlike the projection and aggregation policies, this one judges
     * EVERY select that reads the table, an inner one included (live-verified).
     */
    private void rejectJoinPolicyViolation(final FrostlakeParser.SelectClauseContext ctx,
                                           final List<Table> allTables) {
        for (final Table source : allTables) {
            if (source == null || !source.hasJoinPolicy() || !joinRequired(source)) {
                continue;
            }
            final String violation = JoinPolicyRules.violation(ctx.tableExpression(), source.getName());
            if (violation != null) {
                throw new RuntimeException(SqlCompilationError.PREFIX + " " + violation);
            }
        }
    }

    /** Whether a table's join policy answers JOIN_REQUIRED =&gt; TRUE. */
    private boolean joinRequired(final Table table) {
        final JoinPolicy policy = catalog.findJoinPolicy(table.getJoinPolicyName());
        if (policy == null) {
            return false;
        }
        final Object verdict = new ExpressionEvaluator(null, getFunctionRegistry(), catalog, this)
            .evaluate(policy.getBody(), new Row(new ArrayList<>()));
        return Boolean.TRUE.equals(verdict) || "true".equalsIgnoreCase(String.valueOf(verdict));
    }

    /**
     * An AGGREGATION POLICY forbids reading rows one at a time: the statement's own select must
     * aggregate, and may not use an aggregate that would hand back a single row's value. Both
     * refusals are live's, and a policy answering NO_AGGREGATION_CONSTRAINT() imposes neither.
     *
     * @param aggregating whether the select groups, aggregates or is DISTINCT
     */
    private void rejectAggregationPolicyViolation(final FrostlakeParser.SelectClauseContext ctx,
                                                  final Table table, final boolean aggregating) {
        if (table == null || !table.hasAggregationPolicy() || !isOutermostSelectClause(ctx)
                || minimumGroupSize(table) <= 0) {
            return;
        }
        final String leaking = AggregationPolicyRules.leakingAggregate(ctx);
        if (leaking != null) {
            throw new RuntimeException(SqlCompilationError.PREFIX + " "
                + leaking.toUpperCase(Locale.ROOT) + " violated Aggregation Policy.");
        }
        if (!aggregating) {
            throw new RuntimeException(SqlCompilationError.PREFIX
                + " Aggregation policy violation: aggregation required.");
        }
    }

    /**
     * The minimum group size the table's aggregation policy imposes, or 0 when it imposes none. The
     * body is evaluated inside the policy-body scope, which is where AGGREGATION_CONSTRAINT lives.
     */
    int minimumGroupSize(final Table table) {
        if (table == null || !table.hasAggregationPolicy()) {
            return 0;
        }
        final AggregationPolicy policy = catalog.findAggregationPolicy(table.getAggregationPolicyName());
        if (policy == null) {
            return 0;
        }
        PolicyBodyScope.enter();
        try {
            final Object verdict = new ExpressionEvaluator(null, getFunctionRegistry(), catalog, this)
                .evaluate(policy.getBody(), new Row(new ArrayList<>()));
            return AggregationPolicyRules.minGroupSize(String.valueOf(verdict));
        } finally {
            PolicyBodyScope.leave();
        }
    }

    /**
     * Fold a DISTINCT the way an aggregation policy folds a GROUP BY: a distinct value whose source
     * rows fall below the policy's floor is dropped, and ONE row of all-NULL keys stands for every
     * value that was dropped — emitted whether or not the folded rows themselves reach the floor
     * (live-verified, and the same rule applies to a multi-column DISTINCT, where every key reads NULL).
     *
     * @param projected         the rows as the select list produced them
     * @param preProjectionRows the rows that produced them, index-aligned — the ENTITY KEY columns
     *                          live here, not in the projection
     */
    private List<Row> foldDistinctForAggregationPolicy(final List<Row> projected,
                                                       final List<Row> preProjectionRows,
                                                       final Table table) {
        final int minGroupSize = minimumGroupSize(table);
        if (minGroupSize <= 0 || projected.isEmpty()) {
            return projected;
        }
        final Map<List<Object>, List<Integer>> byValue = new LinkedHashMap<>();
        for (int i = 0; i < projected.size(); i++) {
            final List<Object> key = new ArrayList<>(projected.get(i).getValues());
            List<Integer> bucket = byValue.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                byValue.put(key, bucket);
            }
            bucket.add(i);
        }
        final List<String> entityKey = table.getAggregationEntityKey();
        final List<Row> kept = new ArrayList<>();
        boolean folded = false;
        for (final Map.Entry<List<Object>, List<Integer>> entry : byValue.entrySet()) {
            if (groupReachesFloor(entry.getValue(), preProjectionRows, entityKey, minGroupSize, table)) {
                kept.add(projected.get(entry.getValue().get(0)));
            } else {
                folded = true;
            }
        }
        if (folded) {
            final List<Object> nulls = new ArrayList<>();
            for (int i = 0; i < projected.get(0).getValues().size(); i++) {
                nulls.add(null);
            }
            kept.add(new Row(nulls));
        }
        return kept;
    }

    /** Whether a distinct value's rows reach the floor — counted as entities when a key names them. */
    private boolean groupReachesFloor(final List<Integer> rowIndexes, final List<Row> sourceRows,
                                      final List<String> entityKey, final int minGroupSize,
                                      final Table table) {
        if (entityKey.isEmpty() || sourceRows.size() != rowIndexesUpperBound(rowIndexes, sourceRows)) {
            return rowIndexes.size() >= minGroupSize;
        }
        final Set<List<Object>> entities = new LinkedHashSet<>();
        for (final Integer index : rowIndexes) {
            final Row source = sourceRows.get(index);
            final List<Object> entity = new ArrayList<>();
            for (final String column : entityKey) {
                final int columnIndex = table.getColumnIndex(column);
                entity.add(columnIndex >= 0 && columnIndex < source.getValues().size()
                    ? source.getValue(columnIndex) : null);
            }
            entities.add(entity);
        }
        return entities.size() >= minGroupSize;
    }

    /** Guard: the alignment only holds when every index addresses a source row. */
    private int rowIndexesUpperBound(final List<Integer> rowIndexes, final List<Row> sourceRows) {
        for (final Integer index : rowIndexes) {
            if (index >= sourceRows.size()) {
                return -1;
            }
        }
        return sourceRows.size();
    }

    /** The bare-star flavour of {@link #rejectRestrictedProjections}: every column is projected. */
    private void rejectRestrictedStar(final FrostlakeParser.SelectClauseContext ctx, final Table table,
                                      final Map<String, Table> aliasToTable) {
        if (table == null) {
            return;
        }
        final List<String> projected = new ArrayList<>();
        for (final TableColumn column : table.getColumns()) {
            projected.add(column.getName());
        }
        rejectRestrictedProjections(ctx, projected, table, aliasToTable);
    }

    /** Whether this select clause belongs to the statement the user submitted. */
    private boolean isOutermostSelectClause(final FrostlakeParser.SelectClauseContext ctx) {
        for (ParseTree node = ctx; node != null; node = node.getParent()) {
            if (node == outermostSelect) {
                return true;
            }
            if (node instanceof FrostlakeParser.SelectStatementContext) {
                return node == outermostSelect;
            }
        }
        return false;
    }

    /** Whether a projected expression names a column whose projection policy answers ALLOW =&gt; FALSE. */
    private boolean projectionDenied(final String expression, final Table table,
                                     final Map<String, Table> aliasToTable) {
        final List<Table> sources = new ArrayList<>();
        if (table != null) {
            sources.add(table);
        }
        if (aliasToTable != null) {
            for (final Table aliased : aliasToTable.values()) {
                if (!sources.contains(aliased)) {
                    sources.add(aliased);
                }
            }
        }
        for (final Table source : sources) {
            for (final TableColumn column : source.getColumns()) {
                if (column.hasProjectionPolicy()
                        && ExpressionColumns.references(expression, column.getName())
                        && !projectionAllowed(column.getProjectionPolicyName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Evaluate a projection policy's body: it answers a PROJECTION_CONSTRAINT carrying the verdict. */
    private boolean projectionAllowed(final String policyName) {
        final ProjectionPolicy policy = catalog.findProjectionPolicy(policyName);
        if (policy == null) {
            return true;
        }
        final Object verdict = new ExpressionEvaluator(null, getFunctionRegistry(), catalog, this)
            .evaluate(policy.getBody(), new Row(new ArrayList<>()));
        return verdict == null || !String.valueOf(verdict).contains("\"allow\":false");
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
        final List<ResultSetColumn> cols = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (SelectItemAccessors.isStarItem(item)) {
                for (final TableColumn col : table.getColumns()) {
                    cols.add(new ResultSetColumn(col.getName(), col.getDataType()));
                }
            } else if (SelectItemAccessors.isQualifiedStarItem(item)) {
                // qualified star — columns added later in applyProjection; skip here
            } else {
                final String alias = SelectItemAccessors.getItemAlias(item) != null ? (SelectItemAccessors.getItemAlias(item)) : SelectItemAccessors.getItemExpression(item).getText();
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
        final List<Row> filtered = new ArrayList<>();
        for (final Row row : rows) {
            try {
                final Object result = ev.evaluate(parsed, row);
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
        final List<Row> filtered = new ArrayList<>();
        for (final Row row : rows) {
            try {
                final Object result = ev.evaluate(parsed, row);
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
     * The stream scan -> (WHERE) -> LIMIT stage for a {@link #isStreamableSelect streamable} SELECT: the
     * predicate is evaluated only until LIMIT is satisfied ({@link LimitRowStream} stops pulling its
     * source), and the full filtered set is never built. The stage answers at most {@code limit} rows
     * (after {@code offset}); the clause is read here, while planning, so its refusals come before any row.
     * The predicate mirrors {@code WhereOperator}'s SIMPLE mode exactly (same evaluator, same parse).
     */
    private Operator planStreamFilterLimit(final Table table, final String whereExpr,
                                           final FrostlakeParser.LimitClauseContext limitClause) {
        long limit = Long.MAX_VALUE;
        long offset = 0;
        // NULL and the empty string mean no limit (an empty OFFSET string means zero) — the same
        // reading as limitWindow, kept long-typed for the stream.
        boolean inOffset = false;
        for (final ParseTree child : limitClause.children) {
            if (!(child instanceof TerminalNode)) {
                continue;
            }
            final Token token = ((TerminalNode) child).getSymbol();
            if (token.getType() == FrostlakeParser.OFFSET) {
                inOffset = true;
            } else if (token.getType() == FrostlakeParser.INTEGER_LITERAL) {
                // The slot takes a written literal straight from the token stream, so the literal
                // reader's own width refusal has to be applied here too — otherwise the number reader
                // throws its raw complaint in place of live's positioned sentence.
                IntegerLiteralRange.reject(token);
                if (inOffset) {
                    offset = Long.parseLong(token.getText());
                } else {
                    limit = Long.parseLong(token.getText());
                }
            } else if (token.getType() == FrostlakeParser.STRING_LITERAL) {
                requireEmptyLimitString(token);
            }
        }
        final long streamLimit = limit;
        final long streamOffset = offset;
        final boolean filtered = whereExpr != null && !whereExpr.trim().isEmpty();
        final ExpressionEvaluator evaluator = filtered ? new ExpressionEvaluator(table, functionRegistry, catalog, this) : null;
        final Expression predicate = filtered ? ExpressionEvaluator.parse(whereExpr) : null;
        return new StageOperator("STREAM[" + (filtered ? "WHERE " + whereExpr + ", " : "") + "LIMIT " + streamLimit
                + (streamOffset > 0 ? " OFFSET " + streamOffset : "") + "]") {
            @Override
            protected List<Row> apply(final List<Row> input) {
                RowStream stream = new ListRowStream(input);
                if (filtered) {
                    stream = new FilterRowStream(stream, new RowPredicate() {
                        @Override
                        public boolean test(final Row row) {
                            final Object result = evaluator.evaluate(predicate, row);
                            return SqlTruth.isTrue(result);
                        }
                    });
                }
                stream = new LimitRowStream(stream, streamLimit, streamOffset);
                final List<Row> result = new ArrayList<>();
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
        };
    }

    private Operator planWhere(final Table table, final String whereExpr,
                                       final Map<String, Object> lateralContext,
                                       final Map<String, Table> aliasToTable, final List<Table> allTables,
                                       final FrostlakeParser.TableExpressionContext tableExpr,
                                       final FrostlakeParser.SelectClauseContext selectCtx) {
        // Build operator context
        final OperatorContextBuilder contextBuilder = OperatorContext.builder()
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
        } else if (allTables.size() > 1
                || (tableExpr != null && tableExpr.joinClause().size() > 0)
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
                    // resolves left-to-right (`… AS raw_diff, raw_diff / x AS pct … WHERE ABS(pct) >= 1`).
                    // Resolve iteratively: each pass offers the aliases already computed as outer values
                    // and retries the rest, until a pass adds nothing; a still-unresolvable alias
                    // (aggregate-dependent, forward-only) stays NULL as before.
                    final Map<String, String> pending = new HashMap<>(whereAliases);
                    final Map<String, RuntimeException> lastFailures = new HashMap<>();
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
                                lastFailures.put(alias.getKey(), notEvaluableYet);
                            }
                        }
                        // An alias whose value faults for this row holds the fault, raised if the predicate
                        // reads it (live: SELECT v/0 AS x FROM t WHERE x = 5 is "Division by zero"); the
                        // aliases written over it are tried again and hold the same fault. What a subquery
                        // raises ahead of the rows is raised at once (see SubqueryAhead).
                        if (!progressed) {
                            final Iterator<Map.Entry<String, String>> faulted = pending.entrySet().iterator();
                            while (faulted.hasNext()) {
                                final Map.Entry<String, String> alias = faulted.next();
                                final RuntimeException failure = lastFailures.get(alias.getKey());
                                if (SubqueryAhead.isAhead(failure)) {
                                    throw failure;
                                }
                                if (failure != null && DeferredFault.deferrable(failure)) {
                                    values.put(alias.getKey(), new DeferredFault(failure));
                                    faulted.remove();
                                    progressed = true;
                                }
                            }
                        }
                    }
                    // One that still cannot be evaluated reads NULL.
                    for (final String unresolved : pending.keySet()) {
                        values.put(unresolved, null);
                    }
                    predicateEval.setOuterLateralContext(values);
                    return predicateEval.evaluate(expr, row);
                }
            });
            mode = WhereEvaluationMode.WITH_ALIASES;
        }

        final OperatorContext context = contextBuilder.build();

        // The WHERE operator, run by the pipeline. A refusal raised while the rows are filtered — a correlated
        // subquery live cannot evaluate — is positioned in the WHERE, as the plan-time walk's refusals are.
        final WhereOperator whereOperator = new WhereOperator(whereExpr, mode);
        final FrostlakeParser.BooleanExprContext written = selectCtx == null || selectCtx.whereClause() == null
            ? null : selectCtx.whereClause().booleanExpr();
        final SourcePosition whereOrigin = written == null || !whereExpr.equals(getOriginalText(written))
            ? null : new SourcePosition(written.getStart().getLine(), written.getStart().getCharPositionInLine());
        return new StageOperator(whereOperator.getDescription()) {
            @Override
            protected List<Row> apply(final List<Row> input) {
                if (whereOrigin == null) {
                    return whereOperator.execute(input, context);
                }
                final SourcePosition displacedWhere = ExpressionSource.beginNested(whereOrigin);
                try {
                    return whereOperator.execute(input, context);
                } finally {
                    ExpressionSource.end(displacedWhere);
                }
            }
        };
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
            // A star RENAME target is an output alias too: `SELECT * RENAME (v AS w) … WHERE w > 15`
            // filters on the renamed column, live-verified. Its defining expression is simply the
            // original column name.
            for (final FrostlakeParser.StarModifierContext modifier : SelectItemAccessors.getStarModifiers(item)) {
                if (modifier.RENAME() != null) {
                    for (final FrostlakeParser.StarRenameItemContext rename : modifier.starRenameItem()) {
                        final String renamedTo = ParseTreeText.getIdentifier(rename.identifier(1)).toUpperCase();
                        if (table == null || !table.hasColumn(renamedTo)) {
                            aliases.put(renamedTo, ParseTreeText.getIdentifier(rename.identifier(0)));
                        }
                    }
                }
            }
            if (!SelectItemAccessors.isExprItem(item) || SelectItemAccessors.getItemAlias(item) == null) {
                continue;
            }
            final FrostlakeParser.ExpressionContext valueExpr = SelectItemAccessors.getItemValueExpr(item);
            if (valueExpr == null || hasAggregateFunctionInExpression(valueExpr)) {
                continue;
            }
            final String alias = (SelectItemAccessors.getItemAlias(item)).toUpperCase();
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
    /** The equi-join stage on the named columns (shared by USING and NATURAL). Rows match when every join
     *  column is equal on both sides; an empty column list matches every pair (a cross join). */
    private PipelineStage planUsingJoin(final Table leftTable,
                                       final RowsProvider rightSide, final Table rightTable,
                                       final JoinType joinType, final List<String> joinColumns) {
        // Each join column's per-side index is a per-JOIN fact — resolve once here, not per
        // candidate pair inside matches().
        final int[] leftIdx = new int[joinColumns.size()];
        final int[] rightIdx = new int[joinColumns.size()];
        for (int i = 0; i < joinColumns.size(); i++) {
            leftIdx[i] = leftTable.getColumnIndex(joinColumns.get(i));
            rightIdx[i] = rightTable.getColumnIndex(joinColumns.get(i));
        }
        final JoinConditionEvaluator usingEval = new JoinConditionEvaluator() {
            @Override
            public boolean matches(final Row leftRow, final Row rightRow) {
                for (int i = 0; i < leftIdx.length; i++) {
                    if (leftIdx[i] < 0 || rightIdx[i] < 0) {
                        return false;
                    }
                    final Object lv = DeferredFault.read(leftRow.getValue(leftIdx[i]));
                    final Object rv = DeferredFault.read(rightRow.getValue(rightIdx[i]));
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
        final JoinOperator joinOp = new JoinOperator(leftTable, rightTable, rightSide, joinType, null, usingEval);
        // USING/NATURAL is the purest equi-join: let the hash path pre-filter candidate pairs; every
        // candidate is still re-confirmed by the evaluator above, so results are identical.
        boolean keysResolved = joinColumns.size() > 0;
        for (int i = 0; i < joinColumns.size(); i++) {
            if (leftIdx[i] < 0 || rightIdx[i] < 0) {
                keysResolved = false;
            }
        }
        if (keysResolved) {
            joinOp.withEquiKeys(leftIdx, rightIdx);
        }
        return new PipelineStage(joinOp, context);
    }

    /** Whether a table has a column by exactly this canonical name. */
    private static boolean hasColumnNamedExactly(final Table table, final String name) {
        for (final TableColumn column : table.getColumns()) {
            if (column.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Column names present in both tables, in the left table's order — the join keys of a NATURAL join.
     *  Matched EXACTLY, as the names are spelled: a quoted "k" and an unquoted K are two columns, and only
     *  a column the other side spells the same way joins (live-verified). */
    private List<String> commonColumnNames(final Table left, final Table right) {
        final List<String> common = new ArrayList<>();
        for (final TableColumn lc : left.getColumns()) {
            for (final TableColumn rc : right.getColumns()) {
                if (rc.getName().equals(lc.getName())) {
                    common.add(lc.getName());
                    break;
                }
            }
        }
        return common;
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

    PipelineStage planJoin(final Table leftTable,
                                final List<Row> rightRows, final Table rightTable,
                                final FrostlakeParser.JoinClauseContext joinCtx,
                                final Map<String, Table> scopeAliases,
                                final List<Table> scopeTables,
                                final FrostlakeParser.SelectClauseContext selectCtx,
                                final Map<String, Object> lateralContext,
                                final JoinConditionCompilation onCompile) {
        return planJoin(leftTable, RowsProvider.of(rightRows), rightTable, joinCtx, scopeAliases, scopeTables,
            selectCtx, lateralContext, onCompile);
    }

    /** As {@link #planJoin(Table, List, Table, FrostlakeParser.JoinClauseContext, Map, List,
     *  FrostlakeParser.SelectClauseContext, Map, JoinConditionCompilation)}, over a right side read when the
     *  join runs — a relation planned as a pipeline of its own. */
    PipelineStage planJoin(final Table leftTable,
                                final RowsProvider rightSide, final Table rightTable,
                                final FrostlakeParser.JoinClauseContext joinCtx,
                                final Map<String, Table> scopeAliases,
                                final List<Table> scopeTables,
                                final FrostlakeParser.SelectClauseContext selectCtx,
                                final Map<String, Object> lateralContext,
                                final JoinConditionCompilation onCompile) {
        // The scope as it stands HERE: a relation joined after this one must not reach its condition.
        final Map<String, Table> aliasToTable = snapshotScope(scopeAliases);
        final List<Table> allTables = new ArrayList<>(scopeTables);
        final JoinType joinType = joinTypeOf(joinCtx);

        // NATURAL JOIN: an implicit equi-join on the columns common to both inputs (no ON/USING). Handled
        // before the CROSS/no-condition check below, which a NATURAL join (also lacking ON/USING) would
        // otherwise fall into. No common columns ⇒ every pair matches (a cross join), as in standard SQL.
        if (joinCtx.NATURAL() != null) {
            return planUsingJoin(leftTable, rightSide, rightTable, joinType,
                commonColumnNames(leftTable, rightTable));
        }

        // Handle CROSS JOIN or no ON/USING condition
        if (joinType == JoinType.CROSS || (joinCtx.ON() == null && joinCtx.USING() == null)) {
            final OperatorContext context = OperatorContext.builder()
                .table(leftTable)
                .functionRegistry(functionRegistry).queryExecutor(this)
                .build();
            final JoinOperator joinOp = JoinOperator.cross(leftTable, rightTable, rightSide);
            return new PipelineStage(joinOp, context);
        }

        // Expand USING (col1, col2, ...) into an equi-join on those columns.
        if (joinCtx.USING() != null) {
            final List<String> usingCols = new ArrayList<>();
            // A USING column may arrive qualified (USING (t2.c)); only the column part joins. One that either
            // side lacks by its exact name is refused in USING's own words, unquoted: "Invalid identifier N",
            // and USING (s) over a quoted "s" is "Invalid identifier S" (live-verified).
            for (final FrostlakeParser.QualifiedNameContext qn : joinCtx.usingColumnList().qualifiedName()) {
                final String[] parts = ParseTreeText.qualifiedNameParts(qn);
                final String column = parts[parts.length - 1];
                if (!hasColumnNamedExactly(leftTable, column) || !hasColumnNamedExactly(rightTable, column)) {
                    throw new RuntimeException(SqlCompilationError.of("Invalid identifier " + column));
                }
                usingCols.add(column);
            }
            return planUsingJoin(leftTable, rightSide, rightTable, joinType, usingCols);
        }

        // A condition that does not compile judges no pair: its refusal waits for its rank in the statement.
        if (onCompile != null && !onCompile.compiles(joinCtx.booleanExpr(), leftTable, aliasToTable, allTables)) {
            return new PipelineStage(emptyRelation("join condition refused"), null);
        }

        // Get join condition expression
        final String joinCondition = getOriginalText(joinCtx.booleanExpr());

        // Parse the condition AST and build the alias-aware multi-table evaluator ONCE, then reuse both
        // for every candidate row-pair below. The old path re-extracted the text, re-parsed it, and
        // allocated a fresh evaluator on every pair — quadratic for non-equi (nested-loop) joins.
        final ExpressionEvaluator joinConditionEval =
            new ExpressionEvaluator(leftTable, functionRegistry, catalog, this);
        joinConditionEval.setMultiTableContext(aliasToTable, allTables);
        // Inside a correlated subquery the ON reads the outer row too: ON h.k = g.id AND h.w > fz.id
        // (live-verified). The tables of the join are consulted first, so an inner column still wins.
        joinConditionEval.setOuterLateralContext(lateralContext);
        final Expression joinConditionAst =
            joinConditionEval.withNarrowingCastEqualitiesAnswered(ExpressionEvaluator.parse(joinCondition));
        joinConditionEval.validateCollations(joinConditionAst);

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

        // An ON holding an aggregate or a window call is refused by its clause rule once the FROM clause is
        // resolved (rejectAggregatesOutsideAllowedClauses, rejectWindowFunctionsOutsideAllowedClauses), which
        // is the sentence live gives; until then no pair is judged against it.
        final boolean refusedByClauseRule = holdsAggregate(joinCtx.booleanExpr())
            || windowEvaluator.hasWindowFunctionInTree(joinCtx.booleanExpr());

        // Create condition evaluator that combines rows and evaluates the condition
        final JoinConditionEvaluator conditionEvaluator = new JoinConditionEvaluator() {
            @Override
            public boolean matches(final Row leftRow, final Row rightRow) {
                if (refusedByClauseRule) {
                    return false;
                }
                // Combine rows to evaluate condition. Row.of wraps the fresh list — the plain
                // constructor would defensively RE-COPY it per candidate pair.
                final List<Object> combinedValues = new ArrayList<>(leftRow.getValues());
                combinedValues.addAll(rightRow.getValues());
                final Row combinedRow = Row.of(combinedValues);

                if (!onAliasAsts.isEmpty()) {
                    // Compute the referenced aliases for THIS pair, with no stale context in scope.
                    // Chained aliases resolve iteratively: each pass offers the values computed so
                    // far and retries the rest, until a pass makes no progress.
                    final Map<String, Object> aliasValues = new HashMap<>();
                    if (lateralContext != null) {
                        aliasValues.putAll(lateralContext);
                    }
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
                // per-table across the join; AND/OR/NOT handled natively by the AST evaluator). A value the
                // condition cannot compute refuses the statement: ON CAST(s AS VARCHAR(5)) = t over a longer
                // s is "String '…' is too long and would be truncated" (live-verified).
                final Object result = joinConditionEval.evaluate(joinConditionAst, combinedRow);
                return SqlTruth.isTrue(result);
            }
        };

        // Build operator context
        final OperatorContext context = OperatorContext.builder()
            .table(leftTable)
            .functionRegistry(functionRegistry).queryExecutor(this)
            .aliasToTable(aliasToTable)
            .allTables(allTables)
            .build();

        // Hash-join candidate filter for equi-join conditions (nested-loop fallback otherwise).
        final int[][] equiKeys = extractEquiJoinKeys(joinConditionAst,
            leftTable, rightTable, aliasToTable);

        // Create and execute JOIN operator
        final JoinOperator joinOp = new JoinOperator(leftTable, rightTable, rightSide,
            joinType, joinCondition, conditionEvaluator);
        if (equiKeys != null) {
            joinOp.withEquiKeys(equiKeys[0], equiKeys[1]);
        }
        // A refusal raised while the pairs are matched is positioned in the ON condition, as a WHERE's is.
        final SourcePosition onOrigin = new SourcePosition(
            joinCtx.booleanExpr().getStart().getLine(), joinCtx.booleanExpr().getStart().getCharPositionInLine());
        return new PipelineStage(new StageOperator(joinOp.getDescription()) {
            @Override
            protected List<Row> apply(final List<Row> input) {
                final SourcePosition displaced = ExpressionSource.beginNested(onOrigin);
                try {
                    return joinOp.execute(input, context);
                } finally {
                    ExpressionSource.end(displaced);
                }
            }
        }, null);
    }

    /** A stage answering no row at all, whatever reaches it. */
    private static Operator emptyRelation(final String why) {
        return new SourceOperator(new ArrayList<Row>(), "EMPTY[" + why + "]");
    }

    /**
     * Snowflake {@code ASOF JOIN … MATCH_CONDITION (<left> op <right>) [ON … | USING …]} — for each left
     * row the CLOSEST right row satisfying the condition, left-outer (unmatched left rows survive
     * null-extended). All the wording below is Snowflake's own, live-verified against a real account.
     *
     * @param leftAliases/leftTables the alias context of everything joined SO FAR, i.e. without the right
     *                               table, so the condition's left operand can only see left-side columns
     * @param onCompile              the block's join-condition compilation, which holds a shape refusal for the
     *                               statement's end; null when the block is not compiled, and the refusal is raised
     */
    private PipelineStage planAsofJoin(final Table leftTable,
                                    final List<Row> rightRows, final Table rightTable,
                                    final FrostlakeParser.JoinClauseContext joinCtx,
                                    final Map<String, Table> leftAliases, final List<Table> leftTables,
                                    final String rightAlias,
                                    final Map<String, Table> scopeAliases, final List<Table> scopeTables,
                                    final JoinConditionCompilation onCompile) {
        final Map<String, Table> aliasToTable = snapshotScope(scopeAliases);
        final List<Table> allTables = new ArrayList<>(scopeTables);
        if (joinCtx.ASOF() == null) {
            throw new RuntimeException("MATCH_CONDITION is allowed only in an ASOF JOIN.");
        }
        if (joinCtx.asofMatchCondition() == null) {
            throw new RuntimeException("ASOF JOIN requires a MATCH_CONDITION clause.");
        }
        final Expression condition =
            ExpressionEvaluator.parse(getOriginalText(joinCtx.asofMatchCondition().booleanExpr()));
        // Snowflake pins each operand to its own side of the join (live-verified: `MATCH_CONDITION
        // (r.t <= q.t)` is rejected even though it means the same thing), and allows only the four comparisons.
        final RuntimeException shape = AsofJoinCompilation.shapeRefusal(this, condition, leftTable, leftAliases,
            leftTables, rightAlias, rightTable);
        if (shape != null) {
            if (onCompile == null) {
                throw shape;
            }
            onCompile.holdShape(shape);
            return new PipelineStage(emptyRelation("join condition refused"), null);
        }
        final BinaryOperationExpression comparison = (BinaryOperationExpression) condition;
        final BinaryOperator op = comparison.getOperator();

        final ExpressionEvaluator leftSideEval = new ExpressionEvaluator(leftTable, functionRegistry, catalog, this);
        leftSideEval.setMultiTableContext(leftAliases, leftTables);
        final Map<String, Table> rightOnly = new LinkedHashMap<>();
        rightOnly.put(rightAlias, rightTable);
        final ExpressionEvaluator rightSideEval = new ExpressionEvaluator(rightTable, functionRegistry, catalog, this);
        rightSideEval.setMultiTableContext(rightOnly, Collections.singletonList(rightTable));
        // Each side's names were judged against its own side above, so a failure here is the value's own.
        final RowExpressionEvaluator leftMatch = new RowExpressionEvaluator() {
            @Override
            public Object evaluate(final Expression expr, final Row row) {
                return leftSideEval.evaluate(expr, row);
            }
        };
        final RowExpressionEvaluator rightMatch = new RowExpressionEvaluator() {
            @Override
            public Object evaluate(final Expression expr, final Row row) {
                return rightSideEval.evaluate(expr, row);
            }
        };

        final AsofJoinOperator asofOp = new AsofJoinOperator(leftTable, rightTable, rightRows, op,
            comparison.getLeft(), leftMatch, comparison.getRight(), rightMatch,
            asofPartitionEvaluator(joinCtx, leftTable, rightTable, aliasToTable, allTables));
        final OperatorContext context = OperatorContext.builder()
            .table(leftTable).functionRegistry(functionRegistry).queryExecutor(this)
            .aliasToTable(aliasToTable).allTables(allTables).build();
        return new PipelineStage(asofOp, context);
    }

    /**
     * The ON / USING part of an ASOF JOIN as a pair test, or null when the join has neither. Snowflake
     * restricts it to a conjunction of equalities (live-verified: {@code ON q.k > r.k} and a non-join
     * predicate are both rejected), and it merely narrows the candidate set — the closest-match choice
     * happens inside the operator.
     */
    private JoinConditionEvaluator asofPartitionEvaluator(final FrostlakeParser.JoinClauseContext joinCtx,
                                                          final Table leftTable, final Table rightTable,
                                                          final Map<String, Table> aliasToTable,
                                                          final List<Table> allTables) {
        final String conditionText;
        if (joinCtx.USING() != null) {
            final StringBuilder equalities = new StringBuilder();
            for (final FrostlakeParser.QualifiedNameContext qn : joinCtx.usingColumnList().qualifiedName()) {
                final String[] parts = ParseTreeText.qualifiedNameParts(qn);
                final String column = parts[parts.length - 1];
                if (equalities.length() > 0) {
                    equalities.append(" AND ");
                }
                equalities.append(leftTable.getName()).append('.').append(column)
                    .append(" = ").append(rightTable.getName()).append('.').append(column);
            }
            conditionText = equalities.toString();
        } else if (joinCtx.ON() != null) {
            conditionText = getOriginalText(joinCtx.booleanExpr());
        } else {
            return null;
        }
        final Expression conditionAst = ExpressionEvaluator.parse(conditionText);
        final ExpressionEvaluator conditionEval = new ExpressionEvaluator(leftTable, functionRegistry, catalog, this);
        conditionEval.setMultiTableContext(aliasToTable, allTables);
        conditionEval.validateCollations(conditionAst);
        return new JoinConditionEvaluator() {
            @Override
            public boolean matches(final Row leftRow, final Row rightRow) {
                final List<Object> combined = new ArrayList<>(leftRow.getValues());
                combined.addAll(rightRow.getValues());
                return SqlTruth.isTrue(conditionEval.evaluate(conditionAst, new Row(combined)));
            }
        };
    }

    /** The join stage over an arbitrary condition text for a given join type (the shared ON-join body,
     *  used by the Oracle {@code (+)} outer-join rewrite). {@code aliasToTable}/{@code allTables} must be in
     *  left-then-right order so qualified columns resolve to the correct side of the combined row. */
    private PipelineStage planConditionJoin(final Table leftTable,
            final List<Row> rightRows, final Table rightTable, final JoinType joinType,
            final String onText, final Map<String, Table> scopeAliases, final List<Table> scopeTables) {
        final Map<String, Table> aliasToTable = snapshotScope(scopeAliases);
        final List<Table> allTables = new ArrayList<>(scopeTables);
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
        return new PipelineStage(joinOp, context);
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

    /** True if a parse subtree contains an Oracle {@code (+)} outer-join marker (an OuterJoinColumnExpr). */
    boolean containsOuterJoinMarker(final ParseTree tree) {
        if (tree instanceof FrostlakeParser.OuterJoinColumnExprContext
                || tree instanceof FrostlakeParser.OuterJoinOperandExprContext) {
            return true;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            if (containsOuterJoinMarker(tree.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Apply projection (SELECT list evaluation) using operator pipeline.
     */
    /**
     * Plan-time scope validation for a non-SELECT-list clause expression (WHERE, a GROUP BY key,
     * HAVING, QUALIFY, an ORDER BY key): the invalid-qualifier and ambiguous-bare-duplicate
     * rejections fire over EMPTY inputs, matching live's compile-time behavior — measured per
     * clause on a real account. {@code outputNames} are the query's SELECT output aliases (plus,
     * for ORDER BY, its output column names): every one of these clauses may legally reference
     * them in Snowflake — aliases work even in WHERE — so they are exempt from the bare-name
     * rejection. The walk skips itself under LATERAL / correlated execution, where outer names
     * resolve per row.
     */
    void validateClauseScope(final String expressionText, final Table table,
                             final Map<String, Table> aliasToTable, final List<Table> allTables,
                             final Set<String> outputNames) {
        validateClauseScope(expressionText, table, aliasToTable, allTables, outputNames, null);
    }

    /** As above, reporting an unresolvable column at its place in the statement. */
    void validateClauseScope(final String expressionText, final Table table,
                             final Map<String, Table> aliasToTable, final List<Table> allTables,
                             final Set<String> outputNames, final ParserRuleContext clause) {
        validateClauseScope(expressionText, table, aliasToTable, allTables, outputNames, clause, null);
    }

    /**
     * As above, with the names a refusal's echo prints BARE because they read as output columns — the
     * ORDER BY walk's, where a bare name is the projected column whenever it names one.
     */
    void validateClauseScope(final String expressionText, final Table table,
                             final Map<String, Table> aliasToTable, final List<Table> allTables,
                             final Set<String> outputNames, final ParserRuleContext clause,
                             final Set<String> echoedBare) {
        final ExpressionEvaluator scopeEval = new ExpressionEvaluator(table, functionRegistry, catalog, this);
        if (aliasToTable != null && allTables != null && !allTables.isEmpty()) {
            scopeEval.setMultiTableContext(aliasToTable, allTables);
        }
        scopeEval.setScopeExemptNames(outputNames);
        if (echoedBare != null) {
            scopeEval.setOutputScopeNames(echoedBare);
        }
        final SourcePosition displacedOrigin = ExpressionSource.beginNested(clause == null ? null
            : new SourcePosition(clause.getStart().getLine(),
                                 clause.getStart().getCharPositionInLine()));
        try {
            scopeEval.validateStrict(ExpressionEvaluator.parse(expressionText));
        } finally {
            ExpressionSource.end(displacedOrigin);
        }
    }

    /**
     * Compile every subquery a query's clauses hold, in its own scope with the query's names bound (see
     * {@link SubqueryCompilation}): a join's ON condition's, then the select list's, the WHERE's, HAVING's,
     * QUALIFY's and ORDER BY's.
     *
     * @return the first refusal that waits for the rest of the query to be judged, or null
     */
    private RuntimeException compileClauseSubqueries(final FrostlakeParser.SelectStatementContext stmtCtx,
                                         final FrostlakeParser.SelectClauseContext ctx, final Table table,
                                         final Map<String, Table> aliasToTable, final List<Table> allTables,
                                         final Map<String, Object> lateralContext) {
        final List<ParserRuleContext> clauses = new ArrayList<>();
        final FrostlakeParser.TableExpressionContext from = ctx.tableExpression();
        if (from != null) {
            // A join's ON condition first. (A LATERAL derived table compiles where it joins, see compileLateralSubquery.)
            for (final FrostlakeParser.JoinClauseContext join : from.joinClause()) {
                clauses.add(join.booleanExpr());
            }
        }
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            clauses.add(SelectItemAccessors.getItemExpression(item));
        }
        final FrostlakeParser.WhereClauseContext where = getWhereClause(ctx);
        if (where != null) {
            clauses.add(where.booleanExpr());
        }
        if (ctx.havingClause() != null) {
            clauses.add(ctx.havingClause().booleanExpr());
        }
        final List<ParserRuleContext> orderKeys = new ArrayList<>();
        if (stmtCtx.orderByClause() != null && stmtCtx.selectOperand().size() == 1) {
            for (final FrostlakeParser.OrderItemContext key : stmtCtx.orderByClause().orderItem()) {
                orderKeys.add(key.expression());
            }
        }
        final boolean enclosingRanking = SubqueryTypeRanking.begin(true);
        try {
            RuntimeException waiting = compileSubqueriesIn(clauses, table, aliasToTable, allTables, lateralContext);
            if (ctx.qualifyClause() != null) {
                final RuntimeException qualifyWaiting = compileSubqueriesIn(
                    List.of(ctx.qualifyClause().booleanExpr()), table, aliasToTable, allTables, lateralContext);
                // A QUALIFY without a window is refused ahead of what its own subqueries wait with, and after what
                // the other clauses' subqueries do (live-verified), so theirs never speaks there.
                if (!qualifiesWithoutWindow(ctx)) {
                    waiting = firstRefusal(waiting, qualifyWaiting);
                }
            }
            return firstRefusal(waiting,
                compileSubqueriesIn(orderKeys, table, aliasToTable, allTables, lateralContext));
        } finally {
            SubqueryTypeRanking.end(enclosingRanking);
        }
    }

    /**
     * Compile the subqueries of each expression in turn, each whole before the next, in the scope of
     * {@code table} and of every relation beside it (see {@link SubqueryCompilation}). An expression holding
     * no subquery costs a walk of its parse tree.
     *
     * @param expressions    the expressions, in the order their subqueries compile; null entries are skipped
     * @param table          the relation the expressions are written over
     * @param aliasToTable   the FROM-clause keys, or null
     * @param allTables      every relation in scope, or null
     * @param lateralContext the names of the query around this one, when this one is itself compiling
     * @return the first refusal that waits for the statement around the subqueries, or null
     */
    public RuntimeException compileSubqueriesIn(final List<? extends ParserRuleContext> expressions, final Table table,
                                                final Map<String, Table> aliasToTable, final List<Table> allTables,
                                                final Map<String, Object> lateralContext) {
        RuntimeException waiting = null;
        ExpressionEvaluator scope = null;
        for (final ParserRuleContext expression : expressions) {
            if (expression == null || !holdsSubquery(expression)) {
                continue;
            }
            final Expression parsed;
            try {
                parsed = ExpressionEvaluator.parse(getOriginalText(expression));
            } catch (final RuntimeException unparseable) {
                continue;   // left to the evaluation, which reports it
            }
            if (scope == null) {
                scope = new ExpressionEvaluator(table, functionRegistry, catalog, this);
                if (aliasToTable != null && allTables != null && !allTables.isEmpty()) {
                    scope.setMultiTableContext(aliasToTable, allTables);
                }
            }
            final SourcePosition displaced = ExpressionSource.beginNested(new SourcePosition(
                expression.getStart().getLine(), expression.getStart().getCharPositionInLine()));
            try {
                waiting = firstRefusal(waiting, scope.compileSubqueries(parsed, lateralContext));
            } finally {
                ExpressionSource.end(displaced);
            }
        }
        return waiting;
    }

    /** The refusal that came first, of one already waiting and a new one. */
    static RuntimeException firstRefusal(final RuntimeException waiting, final RuntimeException refused) {
        return waiting != null ? waiting : refused;
    }

    /**
     * Compile a LATERAL derived table before any row of the relations to its left reaches it, the way a
     * subquery in an expression compiles, with those relations in scope.
     *
     * @param joinCtx the join the derived table stands in, or null for a comma
     * @param select  the query the derived table stands in
     */
    private RuntimeException compileLateralSubquery(final FrostlakeParser.TableReferenceContext source,
                                                    final FrostlakeParser.JoinClauseContext joinCtx, final Table table,
                                                    final Map<String, Table> aliasToTable, final List<Table> allTables,
                                                    final Map<String, Object> lateralContext,
                                                    final FrostlakeParser.SelectClauseContext select) {
        final FrostlakeParser.SelectStatementContext query = source.tableSource().selectStatement();
        if (query == null || !(lateralContext == null || SubqueryCompilation.compiles(lateralContext))) {
            return null;
        }
        final RuntimeException unsupported = UnsupportedLateralSubquery.refusal(query, joinCtx,
            aliasToTable == null ? null : aliasToTable.keySet(), table,
            table == null ? -1 : storedRowCount(table.getQualifiedName()),
            UnsupportedLateralSubquery.readsOutside(select, source, joinCtx));
        if (unsupported != null) {
            return unsupported;
        }
        final ExpressionEvaluator scope = new ExpressionEvaluator(table, functionRegistry, catalog, this);
        if (aliasToTable != null && allTables != null && !allTables.isEmpty()) {
            scope.setMultiTableContext(aliasToTable, allTables);
        }
        final SubqueryExpression subquery = new SubqueryExpression(getOriginalText(query));
        subquery.setQueryPosition(new SourcePosition(query.getStart().getLine(), query.getStart().getCharPositionInLine()));
        return scope.compileSubquery(subquery, lateralContext);
    }

    /** Whether a parse subtree holds a query of its own. */
    private static boolean holdsSubquery(final ParseTree node) {
        for (int i = 0; i < node.getChildCount(); i++) {
            final ParseTree child = node.getChild(i);
            if (child instanceof FrostlakeParser.SelectStatementContext || holdsSubquery(child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Plan-time PREDICATE-TYPE validation for HAVING and QUALIFY — the rule WHERE runs in its operator:
     * a condition whose static type is VARCHAR or NUMBER is refused while the statement compiles,
     * "Invalid data type [VARCHAR(10)] for predicate [RT.G]", the predicate spelled from the plan
     * ({@code MAX(RT.G)}, {@code COUNT(*)}, {@code RT.N + 1}, a window call in its canonical form, a
     * SELECT alias bare: {@code GG}). Live judges it before it asks whether QUALIFY has a window at
     * all, which is why the QUALIFY site runs ahead of that refusal. The SELECT aliases are typed from
     * their own expressions, so an alias in the predicate is judged by what it projects.
     *
     * @param expressionText the clause's predicate text
     * @param table          the query's base relation
     * @param aliasToTable   the FROM-clause keys, for a joined query
     * @param allTables      every relation of a joined query
     * @param ctx            the select clause, for its output aliases
     * @param clause         the predicate's parse node, for the refusal's origin
     */
    private void validateClausePredicateType(final String expressionText, final Table table,
                                             final Map<String, Table> aliasToTable,
                                             final List<Table> allTables,
                                             final FrostlakeParser.SelectClauseContext ctx,
                                             final ParserRuleContext clause) {
        final ExpressionEvaluator typeChecker = new ExpressionEvaluator(table, functionRegistry, catalog, this);
        if (aliasToTable != null && allTables != null && !allTables.isEmpty()) {
            typeChecker.setMultiTableContext(aliasToTable, allTables);
        }
        // A real COLUMN always wins over a same-named alias, so an alias one of the query's relations
        // already answers to is not an output name here at all: the predicate names the column, is typed
        // as one and is echoed as one — [FULL_T.A], not [A]. A FROM-less select has no such relation —
        // the items ARE its scope, published as a relation of their own — so nothing is shadowed there.
        final Set<String> shadowed = allTables == null
            ? new HashSet<String>() : relationColumnNames(table, allTables);
        final Set<String> outputNames = new HashSet<>();
        final Set<String> upperNames = new HashSet<>();
        for (final String name : selectItemAliasNames(ctx)) {
            if (!shadowed.contains(name.toUpperCase(Locale.ROOT))) {
                outputNames.add(name);
                upperNames.add(name.toUpperCase(Locale.ROOT));
            }
        }
        typeChecker.setScopeExemptNames(outputNames);
        typeChecker.setOutputScopeNames(upperNames);
        typeChecker.setOutputAliasTypes(selectItemAliasTypes(ctx, typeChecker, shadowed));
        final SourcePosition displacedOrigin = ExpressionSource.beginNested(clause == null ? null
            : new SourcePosition(clause.getStart().getLine(),
                                 clause.getStart().getCharPositionInLine()));
        try {
            typeChecker.validatePredicate(ExpressionEvaluator.parse(expressionText));
        } finally {
            ExpressionSource.end(displacedOrigin);
        }
    }

    /**
     * Each SELECT item alias with the static type of the expression it names, keyed upper-cased; an
     * item this context cannot parse or type is left out, so a reference to it stays undetermined, and
     * so is an alias a real COLUMN of the query's relations already answers to — a column always wins
     * over a same-named alias, so the predicate names the column and is echoed as one.
     *
     * @param ctx        the select clause
     * @param typer      an evaluator over the query's relation context
     * @param columnNames the names the query's relations answer to, upper-cased
     * @return alias to type
     */
    private Map<String, DataType> selectItemAliasTypes(final FrostlakeParser.SelectClauseContext ctx,
                                                       final ExpressionEvaluator typer,
                                                       final Set<String> columnNames) {
        final Map<String, DataType> types = new HashMap<>();
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            final String alias = SelectItemAccessors.getItemAlias(item);
            if (alias == null || !SelectItemAccessors.isExprItem(item)
                    || columnNames.contains(alias.toUpperCase(Locale.ROOT))) {
                continue;
            }
            try {
                final DataType type = typer.inferStaticType(ExpressionEvaluator.parse(
                    getOriginalText(SelectItemAccessors.getItemExpression(item))));
                if (type != null) {
                    types.put(alias.toUpperCase(Locale.ROOT), type);
                }
            } catch (final RuntimeException untyped) {
                // The item's own site refuses what must be refused; here it is merely untyped.
            }
        }
        return types;
    }


    /**
     * Every column name the query's relations answer to, upper-cased.
     *
     * @param table     the query's base relation, or null
     * @param allTables every relation of a joined query, or null
     * @return the names
     */
    private static Set<String> relationColumnNames(final Table table, final List<Table> allTables) {
        final List<Table> relations = new ArrayList<>();
        if (table != null) {
            relations.add(table);
        }
        if (allTables != null) {
            relations.addAll(allTables);
        }
        final Set<String> names = new HashSet<>();
        for (final Table relation : relations) {
            for (final TableColumn column : relation.getColumns()) {
                names.add(column.getName().toUpperCase(Locale.ROOT));
            }
        }
        return names;
    }

    /**
     * A window function may be written in the SELECT list, in QUALIFY and in ORDER BY — nowhere else.
     * Live refuses it in WHERE, in a GROUP BY key, in HAVING and in a join's ON condition, at COMPILE
     * time, and the sentence depends on the clause:
     *
     * <pre>
     *   WHERE / GROUP BY / ON   Window function [CALL] appears outside of SELECT, QUALIFY, and
     *                           ORDER BY clauses.
     *   HAVING                  [CALL] is not a valid group by expression
     * </pre>
     *
     * <p>HAVING gets the GROUPED-validation sentence rather than a window-specific one, which says
     * something about how live sees it: there the window is simply an expression the grouping does not
     * carry. Both were measured on the account, in the plain and the nested-in-an-expression forms.
     *
     * <p>The call itself is re-printed from the analysed plan rather than echoed from the source —
     * see {@link PlanEcho} for the form and for the two keys shapes that stay divergent.
     */
    /**
     * A name written WITH an {@code OVER} clause that is not a window function and not an aggregate.
     * Live refuses every such call the same way, whatever the name IS:
     *
     * <pre>
     *   nosuchfn(a) OVER (…)        Invalid function type [NOSUCHFN] for window function.
     *   ABS(a) OVER (…)             Invalid function type [ABS] for window function.
     *   FLATTEN(a) OVER (…)         Invalid function type [FLATTEN] for window function.
     * </pre>
     *
     * <p>So an unresolvable name, an ordinary scalar and a table function all land in one sentence —
     * what is judged is the name's KIND once resolved, not whether it resolves. An aggregate is a
     * window function in Snowflake and passes; so does anything {@code WindowFunctionNames} declares.
     *
     * <p>THE PREFIX IS THE POINT. Frostlake refused these from inside the row loop, unprefixed, so the
     * refusal was not a compile-time one: an empty relation answered, and a view or CTAS over the shape
     * was CREATED with null columns rather than refused. Running it here, before the rows exist, is what
     * makes it compile-time — and it must run before the clause-placement rule, because live reports
     * this sentence for a call in WHERE where Frostlake reported the misplacement.
     *
     * <p>The name is echoed the way every refusal echoes one: qualified parts kept, a quoted name kept
     * verbatim with its quotes ({@code ["nosuchfn"]}), anything else upper-cased.
     */
    private void rejectNonWindowFunctionsWithOver(final FrostlakeParser.SelectClauseContext ctx,
                                                  final FrostlakeParser.OrderByClauseContext orderBy,
                                                  final PlanEcho echo, final Table table,
                                                  final Map<String, Table> aliasToTable,
                                                  final List<Table> allTables) {
        final List<ParseTree> clauses = new ArrayList<>();
        clauses.add(ctx.selectList());
        clauses.add(ctx.whereClause());
        clauses.add(ctx.groupByClause());
        clauses.add(ctx.havingClause());
        clauses.add(ctx.qualifyClause());
        clauses.add(ctx.tableExpression());
        clauses.add(orderBy);
        new OverCallRules(functionRegistry, echo).rejectKinds(clauses);
        // A named call written with OVER outside the select list waits for the WHERE's types, and one in the ORDER
        // BY is judged ahead of one in QUALIFY: SELECT n FROM rt QUALIFY SUM(x => 1) OVER () = 1 ORDER BY SUM(y => 1)
        // OVER () is the ORDER BY's refusal (live-verified).
        final List<ParseTree> named = new ArrayList<>();
        named.add(ctx.whereClause());
        named.add(ctx.groupByClause());
        named.add(ctx.havingClause());
        named.add(ctx.tableExpression());
        named.add(orderBy);
        named.add(ctx.qualifyClause());
        rejectNamedOverCalls(ctx, named, table, aliasToTable, allTables);
        // A window function that needs an ORDER BY in its window needs it in QUALIFY and ORDER BY too.
        windowEvaluator.rejectWindowWithoutRequiredOrderBy(ctx.qualifyClause());
        windowEvaluator.rejectWindowWithoutRequiredOrderBy(orderBy);
    }

    /** The named half of the OVER call rules (see {@link OverCallRules#rejectNamed}), over these clauses. */
    private void rejectNamedOverCalls(final FrostlakeParser.SelectClauseContext ctx, final List<ParseTree> clauses,
                                      final Table table, final Map<String, Table> aliasToTable,
                                      final List<Table> allTables) {
        final ExpressionEvaluator itemWalk = new ExpressionEvaluator(table, functionRegistry, catalog, this);
        itemWalk.setMultiTableContext(aliasToTable, allTables);
        new OverCallRules(functionRegistry, new PlanEcho(table, aliasToTable, allTables, functionRegistry, catalog))
            .rejectNamed(clauses, ctx.selectList(), itemWalk);
    }

    /** {@link #collectWindowCallsOutsideNestedQueries} over a clause that may be absent. */
    private void collectWindowCallsIn(final ParseTree clause,
                                      final List<FrostlakeParser.FunctionCallExprContext> out) {
        if (clause != null) {
            collectWindowCallsOutsideNestedQueries(clause, out);
        }
    }

    /**
     * What a GROUP BY KEY may be — not a window call, not an aggregate. Both rules live here rather
     * than with their WHERE / ON / HAVING siblings because live runs them LAST of the grouped checks:
     * it compiles the SELECT LIST against the grouping keys first, so a list that is wrong for the
     * grouping speaks at its own item's offset whatever nonsense the GROUP BY holds. With a list that
     * IS valid for the grouping these two sentences are what live gives, unchanged.
     *
     * <p>Called from the grouped path immediately after the select-list validator.
     */
    void rejectGroupByKeyKinds(final FrostlakeParser.SelectClauseContext ctx, final Table table,
                               final Map<String, Table> aliasToTable, final List<Table> allTables) {
        if (ctx.groupByClause() == null) {
            return;
        }
        final PlanEcho echo = new PlanEcho(table, aliasToTable, allTables, functionRegistry, catalog);
        rejectWindowFunctionsIn(ctx.groupByClause(), false, echo);
        final List<ParserRuleContext> found = new ArrayList<>();
        collectAggregateCallsOutsideNestedQueries(ctx.groupByClause(), found);
        if (!found.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of("[" + echo.print(found.get(0))
                + "] is not a valid group by expression"));
        }
    }

    /**
     * A GROUP BY key that RESOLVES — by 1-based ordinal or by SELECT-list alias — to an aggregate or
     * window select item, which live refuses AFTER the grouped select-list validation, echoing the
     * item's OUTPUT NAME: {@code [A]} for an aliased item (the alias as written — canonical for an
     * unquoted one, verbatim for a quoted one) and the derived name — {@code [SUM(I)]} — for an
     * unaliased one. An aggregate anywhere in the item counts ({@code SUM(i)+1 AS a} refuses as
     * {@code [A]}), a key resolving to a PLAIN item stays accepted, and an out-of-range ordinal has
     * already spoken with its own bracketed sentence wherever in the list it sits (all
     * live-verified).
     *
     * <p>★ INSIDE ROLLUP / CUBE / GROUPING SETS THE ECHO IS THE CALL, NOT THE NAME: an aggregate
     * member — however the item spells it — reads {@code [SUM(GT.I)] is not a valid group by
     * expression}, and a window member gets the window sentence ("appears outside of SELECT,
     * QUALIFY, and ORDER BY clauses."), exactly what a call WRITTEN in the member gets.
     *
     * <p>{@code aliasNames} and {@code itemByIndex} are the star-EXPANDED parallel lists — the
     * ordinal counts output columns, so a key can resolve through a star's width.
     */
    void rejectSelectItemGroupKeys(final FrostlakeParser.GroupByClauseContext groupBy,
                                   final List<String> aliasNames,
                                   final List<FrostlakeParser.SelectItemContext> itemByIndex,
                                   final Table table, final Map<String, Table> aliasToTable,
                                   final List<Table> allTables, final boolean superGroup) {
        final List<FrostlakeParser.ExpressionContext> keys = new ArrayList<>();
        for (final FrostlakeParser.GroupByElementContext elem : groupBy.groupByElement()) {
            if (elem.expression() != null) {
                keys.add(elem.expression());
            }
            if (elem.groupByColumnList() != null) {
                keys.addAll(elem.groupByColumnList().expression());
            }
            if (elem.groupingSetList() != null) {
                for (final FrostlakeParser.GroupingSetContext set : elem.groupingSetList().groupingSet()) {
                    keys.addAll(set.expression());
                }
            }
        }
        for (final FrostlakeParser.ExpressionContext key : keys) {
            final int index = resolvedSelectItemIndex(
                ParseTreeText.getOriginalText(key), aliasNames, itemByIndex.size(), table, allTables);
            if (index < 0) {
                continue;
            }
            final FrostlakeParser.SelectItemContext item = itemByIndex.get(index);
            final ParserRuleContext itemExpr = SelectItemAccessors.getItemExpression(item);
            if (itemExpr == null) {
                continue;
            }
            final List<ParserRuleContext> aggregates = new ArrayList<>();
            collectAggregateCallsOutsideNestedQueries(itemExpr, aggregates);
            final List<FrostlakeParser.FunctionCallExprContext> windows = new ArrayList<>();
            collectWindowCallsOutsideNestedQueries(itemExpr, windows);
            if (aggregates.isEmpty() && windows.isEmpty()) {
                continue;
            }
            if (superGroup) {
                final PlanEcho echo =
                    new PlanEcho(table, aliasToTable, allTables, functionRegistry, catalog);
                if (!aggregates.isEmpty()) {
                    throw new RuntimeException(SqlCompilationError.of("[" + echo.print(aggregates.get(0))
                        + "] is not a valid group by expression"));
                }
                throw new RuntimeException(SqlCompilationError.of("Window function ["
                    + echo.printWindowCall(windows.get(0))
                    + "] appears outside of SELECT, QUALIFY, and ORDER BY clauses."));
            }
            final String name = aliasNames.get(index) != null
                ? aliasNames.get(index) : unaliasedItemName(item);
            throw new RuntimeException(SqlCompilationError.of(
                "[" + name + "] is not a valid group by expression"));
        }
    }

    /**
     * Whether a GROUP BY key resolves — by ordinal or alias — to a select item that contains a
     * WINDOW call. Such a key must not COVER its item during the grouped select-list validation:
     * live still holds the window's own references to the grouping (an uncovered one is refused at
     * the reference), and only a list that validates reaches the key-kind sentence for the window
     * item itself.
     */
    boolean groupKeyResolvesToWindowItem(final String rawKey, final List<String> aliasNames,
                                         final List<FrostlakeParser.SelectItemContext> itemByIndex,
                                         final Table table, final List<Table> allTables) {
        final int index =
            resolvedSelectItemIndex(rawKey, aliasNames, itemByIndex.size(), table, allTables);
        if (index < 0) {
            return false;
        }
        final ParserRuleContext itemExpr = SelectItemAccessors.getItemExpression(itemByIndex.get(index));
        if (itemExpr == null) {
            return false;
        }
        final List<FrostlakeParser.FunctionCallExprContext> windows = new ArrayList<>();
        collectWindowCallsOutsideNestedQueries(itemExpr, windows);
        return !windows.isEmpty();
    }

    /**
     * The star-expanded select-item index a GROUP BY key resolves to — the 1-based ordinal's slot,
     * or the item whose alias a BARE identifier names when no real column shadows it (a column
     * always wins over a same-named alias) — or -1 when the key is neither, is qualified, or is an
     * out-of-range ordinal (whose own sentence has already spoken).
     */
    private int resolvedSelectItemIndex(final String rawKey, final List<String> aliasNames,
                                        final int itemCount, final Table table,
                                        final List<Table> allTables) {
        final long position = OrdinalLiteral.positionOf(rawKey);
        if (position != OrdinalLiteral.NOT_AN_ORDINAL) {
            return position >= 1 && position <= itemCount ? (int) position - 1 : -1;
        }
        final Expression parsed;
        try {
            parsed = ExpressionEvaluator.parse(rawKey);
        } catch (final RuntimeException notAnExpression) {
            return -1;
        }
        if (!(parsed instanceof ColumnReferenceExpression)) {
            return -1;
        }
        final ColumnReferenceExpression ref = (ColumnReferenceExpression) parsed;
        if (ref.getTableName() != null) {
            return -1;
        }
        if (table != null && table.hasColumn(ref.getColumnName())) {
            return -1;
        }
        if (allTables != null) {
            for (final Table candidate : allTables) {
                if (candidate != null && candidate.hasColumn(ref.getColumnName())) {
                    return -1;
                }
            }
        }
        for (int i = 0; i < aliasNames.size(); i++) {
            if (aliasNames.get(i) != null && aliasNames.get(i).equalsIgnoreCase(ref.getColumnName())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * A {@link WholePartitionAggregates whole-partition aggregate} windowed over a RANGE frame that
     * spans the WHOLE partition — the one frame shape whose ROWS spelling is legal and whose RANGE
     * spelling is not. Live elides a range covering everything, and then has an ordered aggregate
     * window left over with an ORDER BY it cannot use:
     *
     * <pre>
     *   MEDIAN(n) OVER (ORDER BY n RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)
     *       Aggregate window function with order by clause is not supported:
     *       [MEDIAN(W.N) OVER (ORDER BY W.N ASC NULLS LAST)].
     * </pre>
     *
     * <p>★ THE SENTENCE CARRIES NO POSITION and echoes the call from the PLAN, frame dropped and sort
     * keys spelled out — so it is raised HERE rather than beside its cumulative and sliding siblings,
     * which are refused before anything is resolved. That ordering is live's own: this one LOSES to an
     * unknown relation and to an unresolvable column, where those two beat both.
     *
     * <p>★ IT IS PER-FUNCTION, NOT PER-FRAME. {@code SUM(n)} over the same frame answers.
     */
    private void rejectWholeRangeFrameForWholePartitionAggregate(
            final FrostlakeParser.SelectClauseContext ctx,
            final FrostlakeParser.SelectStatementContext stmtCtx, final PlanEcho echo,
            final Table table, final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final List<FrostlakeParser.FunctionCallExprContext> calls = new ArrayList<>();
        collectWindowCallsIn(ctx.selectList(), calls);
        collectWindowCallsIn(ctx.qualifyClause(), calls);
        collectWindowCallsIn(stmtCtx.orderByClause(), calls);
        for (final FrostlakeParser.FunctionCallExprContext call : calls) {
            final FrostlakeParser.WindowFrameContext frame = call.overClause().windowFrame();
            final String name =
                SqlIdentifiers.canonicalText(call.functionName().getText()).toUpperCase();
            if (frame == null || !WindowFrameShape.isRange(frame)
                    || !WindowFrameShape.spansWholePartition(frame)
                    || !WholePartitionAggregates.coversWholePartitionOnly(name)
                    || WholePartitionAggregates.answersWholeRangeFrame(name)) {
                continue;
            }
            // The call's own NAMES first — its arguments, then its keys. A reference that resolves to
            // nothing outranks this sentence, which is the ordinary consequence of the sentence being
            // built from a resolved plan; the checks below run for every query later on, and are
            // brought forward here for this one call so the two speak in live's order.
            final List<FrostlakeParser.FunctionCallExprContext> one = new ArrayList<>();
            one.add(call);
            windowEvaluator.rejectFileWindowArguments(one, table, selectItemAliasNames(ctx));
            validateWindowKeyScope(call, table, aliasToTable, allTables, selectItemAliasNames(ctx));
            throw new RuntimeException(SqlCompilationError.of(
                "Aggregate window function with order by clause is not supported: ["
                    + echo.printWindowCall(call) + "]."));
        }
    }

    private void rejectWindowFunctionsOutsideAllowedClauses(final FrostlakeParser.SelectClauseContext ctx,
                                                            final PlanEcho echo, final boolean grouped) {
        rejectWindowFunctionsIn(ctx.whereClause(), false, echo);
        // The GROUP BY half is NOT here: live compiles the SELECT LIST against the grouping keys
        // before it says anything about what those keys ARE, so both key-kind rules run after the
        // grouped validator instead — see rejectGroupByKeyKinds.
        rejectWindowFunctionsIn(ctx.tableExpression(), false, echo);
        // A HAVING of a query that groups nothing is a filter like the WHERE, and is refused in its words.
        rejectWindowFunctionsIn(ctx.havingClause(), grouped, echo);
    }


    /**
     * A window call in the ORDER BY of a GROUPED query is refused, whatever its keys name — measured on
     * the account over a group key, a SELECT alias, an aggregate and an ungrouped column alike, and in a
     * larger key expression:
     *
     * <pre>
     *   GROUP BY a ORDER BY ROW_NUMBER() OVER (ORDER BY a)     [ROW_NUMBER() OVER (…)] is not a valid
     *   GROUP BY a ORDER BY ROW_NUMBER() OVER (ORDER BY s)     order by expression
     * </pre>
     *
     * <p>So this is a rule about the CLAUSE, not about scope: nothing the window could name would make
     * it acceptable. Ordering by a window that the SELECT list carries stays legal, because that is an
     * ordinary reference to a projected column.
     *
     * <p>An IMPLICITLY aggregated query is different and is left alone: live runs
     * {@code SELECT SUM(b) FROM g ORDER BY ROW_NUMBER() OVER (…)}, which has exactly one row.
     *
     * <p>The call is re-printed from the analysed plan — see {@link PlanEcho}.
     */
    private void rejectWindowInGroupedOrderBy(final FrostlakeParser.OrderByClauseContext orderBy,
                                              final PlanEcho echo,
                                              final FrostlakeParser.SelectClauseContext ctx,
                                              final Table table, final Map<String, Table> aliasToTable) {
        final List<FrostlakeParser.FunctionCallExprContext> found = new ArrayList<>();
        collectWindowCallsOutsideNestedQueries(orderBy, found);
        if (!found.isEmpty()) {
            // An ORDER BY expression is re-printed as it resolves in the OUTPUT scope — see
            // PlanEcho.printInOutputScope — so the echo needs the names that scope carries.
            echo.printInOutputScope(selectOutputColumnNames(ctx, table, aliasToTable));
            throw new RuntimeException(SqlCompilationError.of("[" + echo.printWindowCall(found.get(0))
                + "] is not a valid order by expression"));
        }
    }

    /**
     * The names a SELECT list PROJECTS, upper-cased — its aliases where items are aliased, the plain
     * column's own name where one is selected bare or qualified, and every column a star expands to.
     *
     * <p>An item that is neither is deliberately absent: {@code SELECT a + 1} outputs a column named
     * "A + 1", and nothing a bare reference could spell. Live's echo agrees, and that is the cell that
     * separates this from the GROUP BY keys — {@code SELECT a AS z … ORDER BY a} prints A qualified,
     * because the output carries Z and not A.
     */
    private Set<String> selectOutputColumnNames(final FrostlakeParser.SelectClauseContext ctx,
                                                final Table table,
                                                final Map<String, Table> aliasToTable) {
        final Set<String> names = new HashSet<>();
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            final String alias = SelectItemAccessors.getItemAlias(item);
            if (alias != null) {
                names.add(alias.toUpperCase());
                continue;
            }
            if (SelectItemAccessors.isStarItem(item) || SelectItemAccessors.isQualifiedStarItem(item)) {
                if (table != null) {
                    for (final StarColumn starColumn : starItemColumns(item, table, aliasToTable)) {
                        names.add(starColumn.getOutputName().toUpperCase());
                    }
                }
                continue;
            }
            final FrostlakeParser.ExpressionContext value = SelectItemAccessors.getItemValueExpr(item);
            if (value instanceof FrostlakeParser.QualifiedNameExprContext) {
                // The output name is the LAST part of the reference — read from the tree, since the
                // dots of a quoted part are not separators.
                final FrostlakeParser.QualifiedNameContext reference =
                    ((FrostlakeParser.QualifiedNameExprContext) value).qualifiedName();
                final List<FrostlakeParser.NamePartContext> tail = reference.namePart();
                names.add((tail == null || tail.isEmpty()
                    ? reference.nameStartPart().getText()
                    : tail.get(tail.size() - 1).getText()).toUpperCase());
            }
        }
        return names;
    }


    /**
     * An AGGREGATE belongs to the SELECT list, to HAVING and to a QUALIFY over grouped rows — never to
     * WHERE, to a join's ON condition, or to a GROUP BY key, because all three are evaluated before the
     * grouping that would give the aggregate its rows. Live refuses each at compile time, naming the
     * clause:
     *
     * <pre>
     *   WHERE SUM(b) &gt; 0            Invalid aggregate function in where clause [SUM(G.B)]
     *   WHERE COUNT(*) &gt; 0          Invalid aggregate function in where clause [COUNT(*)]
     *   WHERE ABS(SUM(b)) &gt; 0       the same — nesting it inside another call changes nothing
     *   JOIN … ON SUM(g.b) = g2.a   Invalid aggregate function in ON clause [SUM(G.B)]
     *   GROUP BY SUM(b)             [SUM(G.B)] is not a valid group by expression
     * </pre>
     *
     * <p>Frostlake reported "Unknown function SUM." for the first three — actively misleading, since SUM
     * is perfectly well known — and ACCEPTED the last two outright.
     *
     * <p>A WINDOWED aggregate ({@code SUM(b) OVER (…)}) is a window, not an aggregate, and is judged by
     * {@link #rejectWindowFunctionsOutsideAllowedClauses} instead.
     */
    private void rejectAggregatesOutsideAllowedClauses(final FrostlakeParser.SelectClauseContext ctx,
                                                       final PlanEcho echo, final Table table,
                                                       final Map<String, Table> aliasToTable,
                                                       final List<Table> allTables,
                                                       final Map<String, Object> lateralContext) {
        rejectAggregatesIn(ctx.whereClause(), "where clause", echo, lateralContext, table, aliasToTable);
        rejectAggregatesIn(ctx.tableExpression(), "ON clause", echo, lateralContext, table, aliasToTable);
        // The GROUP BY half moved to rejectGroupByKeyKinds, below the grouped select-list validator.
        rejectMisplacedCallsInsideAggregate(ctx.selectList(), echo, table, aliasToTable, allTables);
        // HAVING is judged by the same rule: live refuses SUM(AVG(n)) > 0 there with the nesting
        // sentence, where it used to pass here unjudged.
        rejectMisplacedCallsInsideAggregate(ctx.havingClause(), echo, table, aliasToTable, allTables);
    }

    /**
     * An aggregate written INSIDE another aggregate — {@code SUM(SUM(x))}, and equally
     * {@code PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY SUM(x))} — which live refuses because the two
     * would have to consume the same grouping, one after the other, and there is only one.
     *
     * <pre>
     *   Aggregate functions cannot be nested: [SUM(T.N)] nested in [SUM(SUM(T.N))]
     * </pre>
     *
     * <p>★ BOTH CALLS ARE ECHOED AS THE PLAN HOLDS THEM, not as they were written — see
     * {@link PlanEcho#printAggregateCall}: the ordered aggregates are re-shaped and a plain AVG is
     * expanded into its definition, its SUM half naming it when it is the bracketed call itself.
     *
     * <p>★ A WINDOWED OUTER CALL IS LEGAL AND MUST STAY SO. {@code SUM(SUM(x)) OVER (PARTITION BY k)}
     * over a grouped query is the ordinary running-total-of-a-total, and live runs it: the window is
     * computed AFTER the grouping, so the two never compete. The collector already skips a call
     * carrying an OVER clause, which is exactly the exemption this rule needs.
     *
     * <p>★ THE PREFIX CARRIES NO POSITION but keeps its separator, leaving "SQL compilation error: "
     * with a trailing space — measured beside the window-nesting refusal, which in the same run has no
     * trailing space, so it is live's own shape rather than a harness artefact.
     */
    private void rejectNestedAggregates(final ParseTree node, final PlanEcho echo) {
        if (node == null) {
            return;
        }
        final List<ParserRuleContext> aggregates = new ArrayList<>();
        collectAggregateCallsOutsideNestedQueries(node, aggregates);
        for (final ParserRuleContext aggregate : aggregates) {
            final List<ParserRuleContext> nested = new ArrayList<>();
            for (int i = 0; i < aggregate.getChildCount(); i++) {
                collectAggregateCallsOutsideNestedQueries(aggregate.getChild(i), nested);
            }
            if (!nested.isEmpty()) {
                throw new RuntimeException(SqlCompilationError.withoutPosition(
                    "Aggregate functions cannot be nested: [" + echo.printAggregateCall(nested.get(0))
                        + "] nested in [" + echo.printAggregateCall(aggregate) + "]"));
            }
        }
    }

    /** Refuses the first aggregate written anywhere in {@code clause}, naming the clause live names. */
    private void rejectAggregatesIn(final ParserRuleContext clause, final String clauseName,
                                    final PlanEcho echo) {
        rejectAggregatesIn(clause, clauseName, echo, null, null, null);
    }

    /**
     * @param lateralContext the names the row around this query offers it, or null where there is none:
     *                       an aggregate over those names alone belongs to the GROUPED query around this
     *                       one, which computes it over its group, so this clause never sees it as its own
     *                       (live-verified)
     * @param table          this query's own relation, or null: a name it resolves is this query's own
     *                       whatever the row around offers, since the inner scope hides the outer one
     * @param aliasToTable   this query's relations by the names it gives them, or null
     */
    private void rejectAggregatesIn(final ParserRuleContext clause, final String clauseName,
                                    final PlanEcho echo, final Map<String, Object> lateralContext,
                                    final Table table, final Map<String, Table> aliasToTable) {
        if (clause == null) {
            return;
        }
        final List<ParserRuleContext> found = new ArrayList<>();
        collectAggregateCallsOutsideNestedQueries(clause, found);
        for (final ParserRuleContext call : found) {
            if (!readsOuterRowAlone(call, lateralContext, table, aliasToTable)) {
                throw new RuntimeException(SqlCompilationError.of("Invalid aggregate function in "
                    + clauseName + " [" + echo.print(call) + "]"));
            }
        }
    }

    /**
     * Whether every name a call reads is one the row around this query offers, and it reads at least one:
     * the call is then the enclosing query's own aggregate, read here as a value.
     *
     * @param call           the call
     * @param lateralContext the enclosing row's names, or null
     * @param table          this query's own relation, or null
     * @param aliasToTable   this query's relations by the names it gives them, or null
     * @return true when the call reads the enclosing row alone
     */
    private static boolean readsOuterRowAlone(final ParseTree call, final Map<String, Object> lateralContext,
                                              final Table table, final Map<String, Table> aliasToTable) {
        if (lateralContext == null || lateralContext.isEmpty()) {
            return false;
        }
        final List<ParserRuleContext> names = new ArrayList<>();
        collectQualifiedNames(call, names);
        if (names.isEmpty()) {
            return false;
        }
        for (final ParserRuleContext name : names) {
            if (!lateralContext.containsKey(ParseTreeText.getOriginalText(name).toUpperCase())
                    || resolvesInOwnScope(name, table, aliasToTable)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a name read in a query resolves among that query's own relations — a bare column one of them holds,
     * or a column qualified by the name the query gives one of them — and so is no name of the row around it.
     */
    private static boolean resolvesInOwnScope(final ParserRuleContext name, final Table table,
                                              final Map<String, Table> aliasToTable) {
        if (!(name instanceof FrostlakeParser.QualifiedNameExprContext)) {
            return false;
        }
        final String[] parts =
            ParseTreeText.qualifiedNameParts(((FrostlakeParser.QualifiedNameExprContext) name).qualifiedName());
        if (parts.length == 1) {
            return table != null && table.hasColumn(parts[0]);
        }
        if (parts.length == 2 && aliasToTable != null) {
            final Table qualified = aliasToTable.get(parts[0]);
            return qualified != null && qualified.hasColumn(parts[1]);
        }
        return false;
    }

    /** Every name a node reads, outside any query nested in it. */
    private static void collectQualifiedNames(final ParseTree node, final List<ParserRuleContext> into) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            into.add((ParserRuleContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectQualifiedNames(node.getChild(i), into);
        }
    }

    /**
     * A call written INSIDE an aggregate's arguments that cannot be computed there — a WINDOW call, or
     * another AGGREGATE — each of which live refuses with a sentence of its own:
     *
     * <pre>
     *   SUM(ROW_NUMBER() OVER (ORDER BY n))   Window function [ROW_NUMBER() OVER (ORDER BY T.N ASC
     *                                         NULLS LAST)] may not appear inside an aggregate function.
     *   SUM(SUM(n))                           Aggregate functions cannot be nested: [SUM(T.N)] nested
     *                                         in [SUM(SUM(T.N))]
     * </pre>
     *
     * <p>★ WHEN BOTH FAULTS ARE PRESENT, THE ONE WRITTEN FIRST IS REPORTED — the two rules do not rank
     * against each other, the source position does. Measured both ways round:
     *
     * <pre>
     *   SUM(SUM(n) + ROW_NUMBER() OVER (…))   the nesting sentence
     *   SUM(ROW_NUMBER() OVER (…) + SUM(n))   the window sentence
     * </pre>
     *
     * That is why the two are ONE walk rather than two checks in an order: any fixed order gets one of
     * these two statements wrong, and which one is wrong is decided by how the user typed it.
     *
     * <p>★ A WINDOWED OUTER CALL IS LEGAL AND MUST STAY SO. {@code SUM(SUM(x)) OVER (PARTITION BY k)}
     * over a grouped query is the ordinary running-total-of-a-total, and live runs it: the window is
     * computed AFTER the grouping, so the two never compete. The collector already skips a call
     * carrying an OVER clause, which is exactly the exemption this needs.
     *
     * <p>★ THE NESTING PREFIX CARRIES NO POSITION but keeps its separator, leaving "SQL compilation
     * error: " with a trailing space, where the window sentence has none. Both measured in one run, so
     * the difference is live's rather than a harness artefact.
     */
    private void rejectMisplacedCallsInsideAggregate(final ParseTree node, final PlanEcho echo,
                                                    final Table table,
                                                    final Map<String, Table> aliasToTable,
                                                    final List<Table> allTables) {
        if (node == null || node instanceof FrostlakeParser.SelectClauseContext
                && !(node instanceof FrostlakeParser.SelectListContext)) {
            return;
        }
        final List<ParserRuleContext> aggregates = new ArrayList<>();
        collectAggregateCallsOutsideNestedQueries(node, aggregates);
        for (final ParserRuleContext aggregate : aggregates) {
            final List<FrostlakeParser.FunctionCallExprContext> windows = new ArrayList<>();
            final List<ParserRuleContext> nested = new ArrayList<>();
            for (int i = 0; i < aggregate.getChildCount(); i++) {
                collectWindowCallsOutsideNestedQueries(aggregate.getChild(i), windows);
                collectAggregateCallsOutsideNestedQueries(aggregate.getChild(i), nested);
            }
            if (windows.isEmpty() && nested.isEmpty()) {
                continue;
            }
            if (nested.isEmpty() || !windows.isEmpty()
                    && windows.get(0).getStart().getStartIndex()
                        < nested.get(0).getStart().getStartIndex()) {
                throw new RuntimeException(SqlCompilationError.of("Window function ["
                    + echo.printWindowCall(windows.get(0))
                    + "] may not appear inside an aggregate function."));
            }
            // ★ AN ARGUMENT-TYPE COMPLAINT OUTRANKS THE NESTING ONE, measured: SUM(ARRAY_AGG(n)) is
            // "Invalid argument types for function 'SUM': (ARRAY)" live, not a nesting refusal, and
            // MAX(ARRAY_AGG(n)) reports its own does-not-support sentence. The types are settled before
            // the arrangement is judged, so the strict walk speaks first and only silence reaches the
            // throw below.
            rejectAggregateArgumentTypes(aggregate, table, aliasToTable, allTables);
            throw new RuntimeException(SqlCompilationError.withoutPosition(
                "Aggregate functions cannot be nested: [" + echo.printAggregateCall(nested.get(0))
                    + "] nested in [" + echo.printAggregateCall(aggregate) + "]"));
        }
    }

    /**
     * The outer aggregate's arguments judged by TYPE, through the same strict walk the select-item
     * validator uses, so an argument live refuses on its type is refused with live's own sentence
     * rather than being overtaken by the nesting rule. The call's own offset is scoped around the walk
     * because the sentence is positioned; a failure to type at all is left alone, since that is not
     * this rule's complaint to make.
     */
    private void rejectAggregateArgumentTypes(final ParserRuleContext aggregate, final Table table,
                                              final Map<String, Table> aliasToTable,
                                              final List<Table> allTables) {
        final ExpressionEvaluator strictEval =
            new ExpressionEvaluator(table, functionRegistry, catalog, this);
        if (aliasToTable != null && allTables != null && !allTables.isEmpty()) {
            strictEval.setMultiTableContext(aliasToTable, allTables);
        }
        final SourcePosition displaced = ExpressionSource.beginNested(new SourcePosition(
            aggregate.getStart().getLine(), aggregate.getStart().getCharPositionInLine()));
        try {
            strictEval.validateStrict(
                ExpressionEvaluator.parse(getOriginalText(aggregate)));
        } finally {
            ExpressionSource.end(displaced);
        }
    }

    /**
     * Every call in THIS statement whose name resolves to no function, refused in ONE sentence naming
     * them all — {@code Unknown functions FNC, FNA, FNB.} — which is live's own shape.
     *
     * <p>WHERE THIS SITS. Live resolves in phases: names first, then function names, then everything a
     * clause means. Frostlake found an unknown name only while TYPING the result columns, which happens
     * after every clause has been validated and after the rows have been produced, so any refusal raised
     * on the way there spoke first. Measured, the whole family did:
     *
     * <pre>
     *   … GROUP BY a ORDER BY b        [GW.B] is not a valid order by expression
     *   … , b FROM gw GROUP BY a       'GW.B' … is neither an aggregate nor in the group by clause.
     *   … QUALIFY nosuchfn(a) &gt; 1      found QUALIFY clause but no window function.
     *   … WHERE SUM(b) &gt; 1             Invalid aggregate function in where clause [SUM(GW.B)]
     *   … ORDER BY ROW_NUMBER() OVER…  [ROW_NUMBER() OVER (…)] is not a valid order by expression
     * </pre>
     *
     * <p>and live answers every one of them with the unknown NAME instead.
     *
     * <p>WHAT STILL OUTRANKS IT, measured in both directions: a syntax error, a missing relation, an
     * invalid identifier ANYWHERE (its own arguments, WHERE, the select list, HAVING, QUALIFY, a window
     * key — six clauses, all the same way), a window that needs an ORDER BY, and an out-of-range ORDER BY
     * ordinal. Those checks are therefore run HERE, before the sentence is raised; they are pure and
     * already run later on their own, so asking them twice changes only which one speaks first.
     *
     * <p>A statement whose every name resolves leaves this method at the first return, having done
     * nothing but the walk — that is deliberate, and it is what keeps a working query on exactly the
     * path it was on before.
     */
    private void rejectUnresolvableFunctionNames(final FrostlakeParser.SelectStatementContext stmtCtx,
                                                 final FrostlakeParser.SelectClauseContext ctx,
                                                 final Table table,
                                                 final Map<String, Table> aliasToTable,
                                                 final List<Table> allTables,
                                                 final JoinConditionCompilation onCompile) {
        final List<FunctionCallExpression> unresolvable = collectUnresolvableCalls(stmtCtx, ctx);
        // The two refusals raised here are INDEPENDENT: a name nothing can resolve, and a window name
        // written with no specification. Either one alone brings the walk in, because they no longer
        // share a cause — a window-only name is a KNOWN name, so it never reaches the list above.
        final List<FrostlakeParser.FunctionCallExprContext> specificationless = new ArrayList<>();
        collectSpecificationlessWindowCalls(ctx.selectList(), specificationless);
        collectSpecificationlessWindowCalls(ctx.whereClause(), specificationless);
        collectSpecificationlessWindowCalls(ctx.groupByClause(), specificationless);
        collectSpecificationlessWindowCalls(ctx.havingClause(), specificationless);
        collectSpecificationlessWindowCalls(ctx.tableExpression(), specificationless);
        collectSpecificationlessWindowCalls(stmtCtx.orderByClause(), specificationless);
        // ARITY is judged over every window call, specified or not: live refuses LEAD() for its
        // argument count whether or not an OVER follows it.
        final List<FrostlakeParser.FunctionCallExprContext> windowNamed = new ArrayList<>();
        collectWindowNamedCalls(ctx.selectList(), windowNamed);
        collectWindowNamedCalls(ctx.whereClause(), windowNamed);
        collectWindowNamedCalls(ctx.groupByClause(), windowNamed);
        collectWindowNamedCalls(ctx.havingClause(), windowNamed);
        collectWindowNamedCalls(ctx.qualifyClause(), windowNamed);
        collectWindowNamedCalls(stmtCtx.orderByClause(), windowNamed);
        final FrostlakeParser.FunctionCallExprContext miscounted = firstMiscountedCall(windowNamed);
        if (unresolvable.isEmpty() && specificationless.isEmpty() && miscounted == null) {
            return;
        }
        final Set<String> outputNames = selectItemAliasNames(ctx);
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (SelectItemAccessors.isExprItem(item)) {
                validateClauseColumnScope(getOriginalText(SelectItemAccessors.getItemExpression(item)),
                    table, aliasToTable, allTables, outputNames,
                    SelectItemAccessors.getItemExpression(item));
            }
        }
        // A join condition's names, compiled where its join was reached, rank next.
        if (onCompile != null && onCompile.names() != null) {
            throw onCompile.names();
        }
        if (ctx.whereClause() != null) {
            validateClauseColumnScope(getOriginalText(ctx.whereClause().booleanExpr()), table,
                aliasToTable, allTables, outputNames, ctx.whereClause().booleanExpr());
        }
        // The GROUP BY list's names and positions come right after the WHERE's (see GroupKeyNames).
        new GroupKeyNames(this).validate(ctx, table, aliasToTable, allTables);
        if (ctx.havingClause() != null) {
            validateClauseColumnScope(getOriginalText(ctx.havingClause().booleanExpr()), table,
                aliasToTable, allTables, outputNames, ctx.havingClause().booleanExpr());
        }
        if (ctx.qualifyClause() != null) {
            validateClauseColumnScope(getOriginalText(ctx.qualifyClause().booleanExpr()), table,
                aliasToTable, allTables, outputNames, ctx.qualifyClause().booleanExpr());
        }
        if (stmtCtx.orderByClause() != null) {
            for (final FrostlakeParser.OrderItemContext item : stmtCtx.orderByClause().orderItem()) {
                validateClauseColumnScope(getOriginalText(item.expression()), table, aliasToTable,
                    allTables, outputNames, item.expression());
            }
        }
        validateWindowKeyScope(ctx.selectList(), table, aliasToTable, allTables, outputNames);
        if (ctx.qualifyClause() != null) {
            validateWindowKeyScope(ctx.qualifyClause().booleanExpr(), table, aliasToTable, allTables,
                outputNames);
        }
        if (stmtCtx.orderByClause() != null) {
            validateWindowKeyScope(stmtCtx.orderByClause(), table, aliasToTable, allTables, outputNames);
        }
        windowEvaluator.rejectWindowWithoutRequiredOrderBy(ctx);
        orderByExecutor.validateOrderOrdinals(stmtCtx, table, aliasToTable);
        if (!isEarlierSetOperationArm(stmtCtx, ctx)) {
            RowLimitBindRule.reject(stmtCtx, this);
        }
        // A window-ONLY name reached here because the registry carries no callable scalar for it — but
        // it is not unknown, it is unspecified. Raised at the same seam so everything this method has
        // already settled (a bad argument name, a window key, an out-of-range position) still speaks
        // first, exactly as measured.
        final PlanEcho echo = new PlanEcho(table, aliasToTable, allTables, functionRegistry, catalog);
        if (miscounted != null) {
            // Judged with the select list's own walk, so an item written before it speaks first:
            // SELECT UPPER(1, 2), RANK(1) OVER (ORDER BY n) is UPPER's count (live-verified).
            final ExpressionEvaluator itemWalk = new ExpressionEvaluator(table, functionRegistry, catalog, this);
            itemWalk.setMultiTableContext(aliasToTable, allTables);
            OverCallRules.walkItemsBefore(miscounted, ctx.selectList(), itemWalk);
            // Positioned on the call, and raised BEFORE the missing specification — measured: LEAD()
            // with no OVER is refused for its arity, never for its absent window.
            throw new RuntimeException(SqlCompilationError.at(
                miscounted.getStart().getLine(), miscounted.getStart().getCharPositionInLine(),
                WindowFunctionArity.refusal(miscounted.functionName().getText().toUpperCase(),
                    echo.printBareCall(miscounted), argumentCount(miscounted))));
        }
        rejectWindowFunctionsWithoutOver(ctx, stmtCtx.orderByClause(), echo);
        if (unresolvable.isEmpty()) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.of(
            new ExpressionEvaluator(table, functionRegistry, catalog, this)
                .unknownFunctionSentence(unresolvable)));
    }

    /**
     * True when this clause is an arm of a set operation and not its LAST arm.
     *
     * <p>Every arm validates the SAME statement's ORDER BY ordinals, so an early arm's range check
     * would fire before a later arm's names had ever resolved — and live resolves every arm's names
     * first, reporting the bad one at its own arm's offset. Deferring the check to the last arm puts
     * it after all of them. A nested statement as the final operand is left alone: the check then runs
     * where it always did, because there is no clause to compare against.
     */
    private boolean isEarlierSetOperationArm(final FrostlakeParser.SelectStatementContext stmtCtx,
                                             final FrostlakeParser.SelectClauseContext ctx) {
        final List<FrostlakeParser.SelectOperandContext> operands = stmtCtx.selectOperand();
        if (operands == null || operands.size() < 2) {
            return false;
        }
        final FrostlakeParser.SelectClauseContext last = operands.get(operands.size() - 1).selectClause();
        return last != null && last != ctx;
    }

    /**
     * The SELECT LIST's own column references, resolved before every other clause's. Live scans the
     * list first: with a different bad name in the list and in WHERE, HAVING, QUALIFY or ORDER BY, the
     * one it reports is always the LIST's.
     *
     * <p>Phase one only, and the alias exemption grows item by item — which is what keeps a FORWARD
     * reference to a later item's alias the invalid identifier live calls it.
     */
    private void validateSelectListNames(final FrostlakeParser.SelectClauseContext ctx,
                                         final Table table,
                                         final Map<String, Table> aliasToTable,
                                         final List<Table> allTables) {
        final Set<String> earlierNames = new HashSet<>();
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            final ParserRuleContext expression = SelectItemAccessors.getItemExpression(item);
            if (expression != null) {
                validateClauseColumnScope(getOriginalText(expression), table, aliasToTable, allTables,
                    earlierNames, expression);
            }
            for (final ParserRuleContext replaced : SelectItemAccessors.getStarReplaceExpressions(item)) {
                validateClauseColumnScope(getOriginalText(replaced), table, aliasToTable, allTables,
                    earlierNames, replaced);
            }
            final String alias = SelectItemAccessors.getItemAlias(item);
            if (alias != null) {
                earlierNames.add(alias);
            }
            // A star's RENAME names an output column just as an alias does, and the items AFTER it may
            // read that name — a forward reference to it stays an invalid identifier, as for an alias.
            addStarRenameNames(item, earlierNames);
        }
    }

    /** The output names a star item's RENAME publishes, added to {@code names}. */
    private void addStarRenameNames(final FrostlakeParser.SelectItemContext item, final Set<String> names) {
        for (final FrostlakeParser.StarModifierContext modifier : SelectItemAccessors.getStarModifiers(item)) {
            if (modifier.RENAME() != null) {
                for (final FrostlakeParser.StarRenameItemContext rename : modifier.starRenameItem()) {
                    names.add(getIdentifier(rename.identifier(1)));
                }
            }
        }
    }

    /**
     * The SELECT list's and ORDER BY list's column references, resolved before the positional range
     * check. Live's precedence is measured, not assumed: a name in the select list, the ORDER BY
     * list, WHERE or the GROUP BY list outranks an out-of-range position, while a name in HAVING or
     * QUALIFY loses to it. Only the first two are walked here — WHERE already resolves above, and
     * the other two must stay below.
     *
     * <p>Phase one only, so an argument-type complaint keeps its place beneath the unknown-name scan.
     * The alias exemption grows item by item, which is what keeps a FORWARD reference to a later
     * item's alias the invalid identifier live calls it.
     */
    private void validateNamesBeforeOrdinals(final FrostlakeParser.SelectClauseContext ctx,
                                             final FrostlakeParser.SelectStatementContext stmtCtx,
                                             final Table table,
                                             final Map<String, Table> aliasToTable,
                                             final List<Table> allTables) {
        if (stmtCtx.orderByClause() != null) {
            // The ORDER BY half goes through the sort's OWN scope rule rather than a second one: a key
            // resolves against the SELECT OUTPUT before the relation, so `SELECT aa.d FROM aa JOIN bb …
            // ORDER BY d` reads the projected D and never sees the join's ambiguous base columns. That
            // rule also declines set operations, whose ORDER BY resolves against the combined output.
            orderByExecutor.validateOrderKeyScope(stmtCtx, table, aliasToTable, allTables);
        }
    }

    /**
     * {@link #validateClauseScope} narrowed to PHASE ONE — column references and nothing else. The full
     * walk also raises argument-type complaints, which live places BELOW an unknown function name, so
     * the unknown-name scan can only afford the identifier half.
     *
     * <p>A clause the expression grammar cannot parse on its own is left alone: it is validated by its
     * own site later, exactly as before.
     */
    void validateClauseColumnScope(final String expressionText, final Table table,
                                           final Map<String, Table> aliasToTable,
                                           final List<Table> allTables, final Set<String> outputNames,
                                           final ParserRuleContext clause) {
        final ExpressionEvaluator scopeEval = new ExpressionEvaluator(table, functionRegistry, catalog, this);
        if (aliasToTable != null && allTables != null && !allTables.isEmpty()) {
            scopeEval.setMultiTableContext(aliasToTable, allTables);
        }
        scopeEval.setScopeExemptNames(outputNames);
        final SourcePosition displacedOrigin = ExpressionSource.beginNested(clause == null ? null
            : new SourcePosition(clause.getStart().getLine(),
                                 clause.getStart().getCharPositionInLine()));
        try {
            scopeEval.validateColumnScope(ExpressionEvaluator.parse(expressionText));
        } catch (final RuntimeException unparseable) {
            if (SqlCompilationError.isCompilationError(unparseable.getMessage())) {
                throw unparseable;
            }
        } finally {
            ExpressionSource.end(displacedOrigin);
        }
    }

    /**
     * Everything live refuses ahead of a query's own argument and predicate types — a join condition's first, then
     * the WHERE's and every other clause's: the WHERE's, the GROUP BY keys' and the ORDER BY list's names, the ORDER BY
     * ordinal, HAVING's and QUALIFY's names, and then every subquery the clauses hold, each compiled whole in turn, its
     * names and its types refused as it compiles (see {@link SubqueryTypeRanking}). The aggregate and window
     * placement rules and the grouped select list come after the query's types, and so does a subquery's own
     * placement refusal, which is handed back to wait (live-verified). The name and position checks are pure and run
     * again at their own sites, so asking them here changes only which refusal speaks first.
     *
     * @return the first subquery refusal that waits for the rest of the query to be judged, or null
     */
    private RuntimeException rejectAheadOfOwnTypes(final FrostlakeParser.SelectStatementContext stmtCtx,
                                                   final FrostlakeParser.SelectClauseContext ctx, final String whereExpr,
                                                   final Table table, final Map<String, Table> aliasToTable,
                                                   final List<Table> allTables,
                                                   final Map<String, Object> lateralContext) {
        if (whereExpr != null && getWhereClause(ctx) != null) {
            validateWhereScope(whereExpr, table, aliasToTable, allTables, ctx, true);
        }
        final Set<String> outputNames = selectItemAliasNames(ctx);
        if (ctx.groupByClause() != null && ctx.groupByClause().ALL() == null) {
            for (final FrostlakeParser.GroupByElementContext element : ctx.groupByClause().groupByElement()) {
                final List<ParserRuleContext> keys = new ArrayList<>();
                if (element.expression() != null) {
                    keys.add(element.expression());
                }
                if (element.groupByColumnList() != null) {
                    keys.addAll(element.groupByColumnList().expression());
                }
                if (element.groupingSetList() != null) {
                    for (final FrostlakeParser.GroupingSetContext set : element.groupingSetList().groupingSet()) {
                        keys.addAll(set.expression());
                    }
                }
                for (final ParserRuleContext key : keys) {
                    validateClauseColumnScope(getOriginalText(key), table, aliasToTable, allTables, outputNames, key);
                }
            }
        }
        if (stmtCtx.orderByClause() != null) {
            orderByExecutor.validateOrderKeyNames(stmtCtx, table, aliasToTable, allTables);
        }
        if (!isEarlierSetOperationArm(stmtCtx, ctx)) {
            orderByExecutor.validateOrderOrdinals(stmtCtx, table, aliasToTable);
        }
        if (ctx.havingClause() != null) {
            validateClauseColumnScope(getOriginalText(ctx.havingClause().booleanExpr()), table, aliasToTable,
                allTables, outputNames, ctx.havingClause().booleanExpr());
        }
        if (ctx.qualifyClause() != null) {
            validateClauseColumnScope(getOriginalText(ctx.qualifyClause().booleanExpr()), table, aliasToTable,
                allTables, outputNames, ctx.qualifyClause().booleanExpr());
        }
        if (!isEarlierSetOperationArm(stmtCtx, ctx)) {
            RowLimitBindRule.reject(stmtCtx, this);
        }
        return compileClauseSubqueries(stmtCtx, ctx, table, aliasToTable, allTables, lateralContext);
    }

    /**
     * The WHERE's plan-time scope walk. The select aliases are readable there, except an alias of an item
     * holding an aggregate — an aggregate called as a window included — or a window call: the walk refuses
     * those in live's own sentences at the point it meets them, so a bad name earlier in the predicate still
     * speaks first (see {@code ExpressionEvaluatorVisitor.rejectWhereAlias}).
     */
    private void validateWhereScope(final String whereExpr, final Table table, final Map<String, Table> aliasToTable,
                                    final List<Table> allTables, final FrostlakeParser.SelectClauseContext ctx,
                                    final boolean namesOnly) {
        validateWhereScope(whereExpr, table, aliasToTable, allTables, ctx, namesOnly, ctx.whereClause().booleanExpr());
    }

    /**
     * {@link #validateWhereScope(String, Table, Map, List, FrostlakeParser.SelectClauseContext, boolean)} over
     * {@code clause}: the WHERE's predicate, or the HAVING's of a query that groups nothing, which reads the
     * select aliases as the WHERE does (live refuses a window item's alias there in the same sentence).
     */
    private void validateWhereScope(final String whereExpr, final Table table, final Map<String, Table> aliasToTable,
                                    final List<Table> allTables, final FrostlakeParser.SelectClauseContext ctx,
                                    final boolean namesOnly, final FrostlakeParser.BooleanExprContext clause) {
        final Set<String> readable = selectItemAliasNames(ctx);
        final Set<String> aggregateAliases = new HashSet<>();
        final Map<String, String> windowAliases = new HashMap<>();
        final PlanEcho echo = new PlanEcho(table, aliasToTable, allTables, functionRegistry, catalog);
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            final String alias = SelectItemAccessors.getItemAlias(item);
            final ParserRuleContext itemExpr = SelectItemAccessors.isExprItem(item)
                ? SelectItemAccessors.getItemExpression(item) : null;
            if (alias == null || itemExpr == null) {
                continue;
            }
            if (holdsAggregateCall(itemExpr)) {
                aggregateAliases.add(alias);
                readable.remove(alias);
                continue;
            }
            final List<FrostlakeParser.FunctionCallExprContext> windows = new ArrayList<>();
            collectWindowCallsOutsideNestedQueries(itemExpr, windows);
            if (!windows.isEmpty()) {
                windowAliases.put(alias, "Window function [" + echo.printWindowCall(windows.get(0))
                    + "] appears outside of SELECT, QUALIFY, and ORDER BY clauses.");
                readable.remove(alias);
            }
        }
        final ExpressionEvaluator scopeEval = new ExpressionEvaluator(table, functionRegistry, catalog, this);
        if (aliasToTable != null && allTables != null && !allTables.isEmpty()) {
            scopeEval.setMultiTableContext(aliasToTable, allTables);
        }
        scopeEval.setScopeExemptNames(readable);
        scopeEval.setWhereAliasRefusals(aggregateAliases, windowAliases);
        final SourcePosition displacedOrigin = ExpressionSource.beginNested(
            new SourcePosition(clause.getStart().getLine(), clause.getStart().getCharPositionInLine()));
        try {
            final Expression parsed = ExpressionEvaluator.parse(whereExpr);
            // Every name before any type, wherever each stands in the predicate (live-verified).
            scopeEval.validateColumnScope(parsed);
            if (!namesOnly) {
                scopeEval.validateStrict(parsed);
            }
        } finally {
            ExpressionSource.end(displacedOrigin);
        }
    }

    /** Whether an expression calls an aggregate function — as a window too — outside any query nested in it. */
    private boolean holdsAggregateCall(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectClauseContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.FunctionCallStarExprContext
                && functionRegistry.hasAggregateFunction(
                    ((FrostlakeParser.FunctionCallStarExprContext) node).functionName().getText().toUpperCase())) {
            return true;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext
                && functionRegistry.hasAggregateFunction(
                    ((FrostlakeParser.FunctionCallExprContext) node).functionName().getText().toUpperCase())) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holdsAggregateCall(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The statement's own calls whose NAME resolves to nothing, in the order live names them: the
     * statement's clauses in written order, and within each expression the ARGUMENTS before their call.
     * Nothing is de-duplicated, because live does not de-duplicate either.
     *
     * @param stmtCtx the statement, for its ORDER BY
     * @param ctx     the select clause
     * @return the unresolvable calls, empty when every name resolves
     */
    private List<FunctionCallExpression> collectUnresolvableCalls(
            final FrostlakeParser.SelectStatementContext stmtCtx,
            final FrostlakeParser.SelectClauseContext ctx) {
        final List<ParserRuleContext> calls = new ArrayList<>();
        for (int i = 0; i < ctx.getChildCount(); i++) {
            // The FROM clause's relations are left out. A call there is a TABLE function, resolved by the
            // relation machinery and not by the expression registry — a built-in written qualified
            // (INFORMATION_SCHEMA.QUERY_HISTORY) resolves perfectly well while looking, to this
            // question, like a user routine that does not exist. Judging it here refused 41 working
            // queries. Its join conditions are expressions like any other, and live names their unknown
            // functions in the same sentence, in written order.
            if (ctx.getChild(i) == ctx.tableExpression()) {
                collectJoinConditionCalls(ctx.tableExpression(), calls);
            } else {
                collectCallsOutsideNestedQueries(ctx.getChild(i), calls);
            }
        }
        if (stmtCtx.orderByClause() != null) {
            collectCallsOutsideNestedQueries(stmtCtx.orderByClause(), calls);
        }
        // No table: the question is only whether the NAME exists, which the registry and the catalog
        // answer between them — the relation has nothing to say about it.
        final ExpressionEvaluator scan = new ExpressionEvaluator(null, functionRegistry, catalog, this);
        final List<FunctionCallExpression> unresolvable = new ArrayList<>();
        for (final ParserRuleContext call : calls) {
            try {
                final Expression parsed = ExpressionEvaluator.parse(getOriginalText(call));
                if (parsed instanceof FunctionCallExpression && !scan.resolvesToAFunctionName(parsed)) {
                    unresolvable.add((FunctionCallExpression) parsed);
                }
            } catch (final RuntimeException notACallOnItsOwn) {
                // A windowed or otherwise non-standalone form: left to the paths that already judge it.
            }
        }
        return unresolvable;
    }

    /**
     * The calls of every join condition a FROM clause holds, a parenthesized join's own included, in written order;
     * nothing of its relations.
     */
    private void collectJoinConditionCalls(final ParseTree node, final List<ParserRuleContext> out) {
        if (node instanceof FrostlakeParser.JoinClauseContext) {
            final FrostlakeParser.JoinClauseContext join = (FrostlakeParser.JoinClauseContext) node;
            collectJoinConditionCalls(join.tableReference(), out);
            if (join.booleanExpr() != null) {
                collectCallsOutsideNestedQueries(join.booleanExpr(), out);
            }
            return;
        }
        if (node instanceof FrostlakeParser.TableReferenceContext
                && !isParenthesizedJoin(((FrostlakeParser.TableReferenceContext) node).tableSource())) {
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectJoinConditionCalls(node.getChild(i), out);
        }
    }

    /**
     * Calls written in this clause itself, innermost FIRST, skipping any nested query's own. A call written
     * with named arguments counts too, unless it is windowed.
     */
    private void collectCallsOutsideNestedQueries(final ParseTree node, final List<ParserRuleContext> out) {
        if (node == null || node instanceof FrostlakeParser.SelectClauseContext) {
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectCallsOutsideNestedQueries(node.getChild(i), out);
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext
                || (node instanceof FrostlakeParser.FunctionCallNamedArgsExprContext
                    && ((FrostlakeParser.FunctionCallNamedArgsExprContext) node).overClause() == null)
                || (node instanceof FrostlakeParser.FunctionCallMixedArgsExprContext
                    && ((FrostlakeParser.FunctionCallMixedArgsExprContext) node).overClause() == null)) {
            out.add((ParserRuleContext) node);
        }
    }

    /**
     * Aggregate calls written in this clause itself, not those belonging to a query nested inside it —
     * an aggregate is perfectly legal in a subquery's own SELECT list. A call carrying an OVER clause is
     * a WINDOW and is skipped here.
     */
    private void collectAggregateCallsOutsideNestedQueries(final ParseTree node,
                                                           final List<ParserRuleContext> out) {
        if (node instanceof FrostlakeParser.SelectClauseContext) {
            return;
        }
        if (node instanceof FrostlakeParser.FunctionCallStarExprContext) {
            final FrostlakeParser.FunctionCallStarExprContext star =
                (FrostlakeParser.FunctionCallStarExprContext) node;
            // The star form carries no OVER clause of its own in this grammar, so there is no
            // windowed flavour of it to exclude here.
            if (functionRegistry.hasAggregateFunction(star.functionName().getText().toUpperCase())) {
                out.add(star);
            }
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call =
                (FrostlakeParser.FunctionCallExprContext) node;
            if (call.overClause() == null
                    && functionRegistry.hasAggregateFunction(call.functionName().getText().toUpperCase())) {
                out.add(call);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectAggregateCallsOutsideNestedQueries(node.getChild(i), out);
        }
    }

    /** Refuses the first window call written anywhere in {@code clause}, with the clause's sentence. */
    private void rejectWindowFunctionsIn(final ParserRuleContext clause, final boolean groupedWording,
                                         final PlanEcho echo) {
        if (clause == null) {
            return;
        }
        final List<FrostlakeParser.FunctionCallExprContext> found = new ArrayList<>();
        collectWindowCallsOutsideNestedQueries(clause, found);
        if (found.isEmpty()) {
            return;
        }
        final String call = echo.printWindowCall(found.get(0));
        throw new RuntimeException(SqlCompilationError.of(groupedWording
            ? "[" + call + "] is not a valid group by expression"
            : "Window function [" + call
                + "] appears outside of SELECT, QUALIFY, and ORDER BY clauses."));
    }

    /**
     * Window calls written in this clause itself, NOT those belonging to a query nested inside it — a
     * subquery has its own SELECT list, where a window is perfectly legal
     * ({@code WHERE a IN (SELECT ROW_NUMBER() OVER (…) FROM …)} runs on both engines), so the walk
     * stops at every nested select.
     */
    /**
     * A WINDOW-ONLY function written with NO over clause. Live names the missing specification and
     * echoes the call in PLAN form — the argument qualified and upper-cased, with any implicit cast
     * printed — where Frostlake called the NAME unknown, which is false: the name is perfectly well
     * known, it is the specification that is absent. The registry simply does not carry these as
     * callable scalars, so the lookup missed and the unknown-name sentence won.
     *
     * <p>Only the names that are window-ONLY are caught, and that exclusion is what keeps working SQL
     * working: SUM, COUNT, AVG, MIN and MAX are aggregates as well and answer without an OVER, and
     * PERCENTILE_CONT / PERCENTILE_DISC take WITHIN GROUP instead — live accepts all seven.
     *
     * <p>QUALIFY is the one clause this does not speak in. A call with no OVER is not a window
     * function, so {@code QUALIFY ROW_NUMBER() = 1} is the no-window refusal there instead, raised
     * further down — measured, and the reason the qualify clause is not walked here.
     */
    private void rejectWindowFunctionsWithoutOver(final FrostlakeParser.SelectClauseContext ctx,
                                                  final FrostlakeParser.OrderByClauseContext orderBy,
                                                  final PlanEcho echo) {
        final List<FrostlakeParser.FunctionCallExprContext> found = new ArrayList<>();
        collectSpecificationlessWindowCalls(ctx.selectList(), found);
        collectSpecificationlessWindowCalls(ctx.whereClause(), found);
        collectSpecificationlessWindowCalls(ctx.groupByClause(), found);
        collectSpecificationlessWindowCalls(ctx.havingClause(), found);
        collectSpecificationlessWindowCalls(ctx.tableExpression(), found);
        collectSpecificationlessWindowCalls(orderBy, found);
        for (final FrostlakeParser.FunctionCallExprContext call : found) {
            throw new RuntimeException(SqlCompilationError.of(
                "Missing window specification for function ["
                    + echo.printSpecificationlessCall(call) + "]."));
        }
    }

    /** Calls naming a window-ONLY function and carrying no OVER, outside any nested query. */
    private void collectSpecificationlessWindowCalls(final ParseTree clause,
                                                     final List<FrostlakeParser.FunctionCallExprContext> out) {
        if (clause != null) {
            collectSpecificationlessWindowCallsIn(clause, out);
        }
    }

    /** Calls naming a WINDOW function — with or without an OVER — outside any nested query. */
    private void collectWindowNamedCalls(final ParseTree clause,
                                         final List<FrostlakeParser.FunctionCallExprContext> out) {
        if (clause == null || clause instanceof FrostlakeParser.SelectClauseContext) {
            return;
        }
        if (clause instanceof FrostlakeParser.FunctionCallExprContext
                && WindowFunctionArity.isMeasured(
                    ((FrostlakeParser.FunctionCallExprContext) clause)
                        .functionName().getText().toUpperCase())) {
            out.add((FrostlakeParser.FunctionCallExprContext) clause);
        }
        for (int i = 0; i < clause.getChildCount(); i++) {
            collectWindowNamedCalls(clause.getChild(i), out);
        }
    }

    /** The first of these calls whose argument count live refuses, or null when all of them fit. */
    private FrostlakeParser.FunctionCallExprContext firstMiscountedCall(
            final List<FrostlakeParser.FunctionCallExprContext> calls) {
        for (final FrostlakeParser.FunctionCallExprContext call : calls) {
            if (WindowFunctionArity.refusal(call.functionName().getText().toUpperCase(), "",
                    argumentCount(call)) != null) {
                return call;
            }
        }
        return null;
    }

    /** How many positional arguments a call was written with. */
    private int argumentCount(final FrostlakeParser.FunctionCallExprContext call) {
        return call.functionArgList() == null ? 0 : call.functionArgList().functionArg().size();
    }

    /**
     * Whether a name is WINDOW-ONLY — one this engine knows but cannot answer without an OVER clause.
     * The aggregate exclusion is what keeps working SQL working: SUM, COUNT, AVG, MIN and MAX are
     * window functions AND aggregates, and answer perfectly well with no specification at all.
     *
     * @param canonical the upper-cased function name
     * @return whether the name needs a window specification
     */
    private boolean isWindowOnlyName(final String canonical) {
        return WindowFunctionNames.handles(canonical)
            && functionRegistry.getAggregateFunction(canonical) == null;
    }

    private void collectSpecificationlessWindowCallsIn(final ParseTree node,
                                                       final List<FrostlakeParser.FunctionCallExprContext> out) {
        if (node instanceof FrostlakeParser.SelectClauseContext) {
            return;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            final String canonical = call.functionName().getText().toUpperCase();
            if (call.overClause() == null && isWindowOnlyName(canonical)) {
                out.add(call);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectSpecificationlessWindowCallsIn(node.getChild(i), out);
        }
    }

    private void collectWindowCallsOutsideNestedQueries(final ParseTree node,
                                                        final List<FrostlakeParser.FunctionCallExprContext> out) {
        if (node instanceof FrostlakeParser.SelectClauseContext) {
            return;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext
                && ((FrostlakeParser.FunctionCallExprContext) node).overClause() != null) {
            out.add((FrostlakeParser.FunctionCallExprContext) node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectWindowCallsOutsideNestedQueries(node.getChild(i), out);
        }
    }

    /**
     * Plan-time scope validation for the KEYS of every {@code OVER} clause written inside
     * {@code within} — a select item, or the QUALIFY predicate. The keys are read from the PARSE
     * TREE because that is the only place they survive: a window call parses to a single
     * {@code WindowFunctionExpression} carrying its call text, so the expression walk cannot see
     * what the OVER clause references and a name that resolved to nothing came back as an accepted
     * query. Live refuses each one at COMPILE time, at the reference's own offset:
     *
     * <pre>
     *   … OVER (ORDER BY nosuchcol)                     invalid identifier 'NOSUCHCOL'
     *   … OVER (PARTITION BY q.nosuchcol ORDER BY b)    invalid identifier 'Q.NOSUCHCOL'
     *   … OVER (PARTITION BY test_schema.q.nosuchcol …) invalid identifier 'TEST_SCHEMA.Q.NOSUCHCOL'
     * </pre>
     *
     * <p>Every SELECT alias stays exempt, in either direction: a window key naming one is legal
     * ({@code SELECT a AS x, ROW_NUMBER() OVER (ORDER BY x)} reads on both engines).
     */
    void validateWindowKeyScope(final ParseTree within, final Table table,
                                final Map<String, Table> aliasToTable, final List<Table> allTables,
                                final Set<String> outputNames) {
        final List<FrostlakeParser.FunctionCallExprContext> windowCalls = new ArrayList<>();
        collectWindowFunctionCalls(within, windowCalls);
        for (final FrostlakeParser.FunctionCallExprContext call : windowCalls) {
            final FrostlakeParser.OverClauseContext over = call.overClause();
            if (over == null) {
                continue;
            }
            if (over.partitionByClause() != null) {
                for (final FrostlakeParser.ExpressionContext key
                        : over.partitionByClause().expressionList().expression()) {
                    validateClauseScope(getOriginalText(key), table, aliasToTable, allTables,
                        outputNames, key);
                }
            }
            if (over.orderByClause() != null) {
                for (final FrostlakeParser.OrderItemContext item : over.orderByClause().orderItem()) {
                    validateClauseScope(getOriginalText(item.expression()), table, aliasToTable,
                        allTables, outputNames, item.expression());
                }
            }
        }
    }

    /**
     * The SELECT list's output alias names, upper-cased — item aliases plus star {@code RENAME}
     * targets ({@code SELECT * RENAME (v AS w)} makes W referencable in WHERE, live-verified) —
     * the names every other clause may reference.
     */
    Set<String> selectItemAliasNames(final FrostlakeParser.SelectClauseContext ctx) {
        final Set<String> names = new HashSet<>();
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            // CANONICAL, not upper-cased: the readers already fold an unquoted alias and leave a
            // quoted one alone, and upper-casing on top of that made "x" and X the same name — so a
            // clause referring to X resolved through an alias that is only ever spelled "x".
            final String alias = SelectItemAccessors.getItemAlias(item);
            if (alias != null) {
                names.add(alias);
            }
            addStarRenameNames(item, names);
        }
        return names;
    }

    /**
     * The plan-time argument walk over a windowed query's select list — the walk {@link #applyProjection}
     * runs for every other query, item by item at each item's own place, with the lateral aliases of the
     * items before it exempt. The names are settled before either runs, so an invalid identifier or an
     * unknown function still outranks what this refuses.
     */
    private void validateWindowedSelectArguments(final FrostlakeParser.SelectClauseContext ctx, final Table table,
                                                 final Map<String, Table> aliasToTable,
                                                 final List<Table> allTables) {
        final ExpressionEvaluator strictEval = new ExpressionEvaluator(table, functionRegistry, catalog, this);
        strictEval.setMultiTableContext(aliasToTable, allTables);
        final Set<String> earlierOutputNames = new HashSet<>();
        strictEval.setScopeExemptNames(earlierOutputNames);
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (!SelectItemAccessors.isExprItem(item)) {
                continue;
            }
            final ParserRuleContext itemExpr = SelectItemAccessors.getItemExpression(item);
            final SourcePosition displaced = ExpressionSource.beginNested(new SourcePosition(
                itemExpr.getStart().getLine(), itemExpr.getStart().getCharPositionInLine()));
            try {
                strictEval.validateStrict(ExpressionEvaluator.parse(getOriginalText(itemExpr)));
            } finally {
                ExpressionSource.end(displaced);
            }
            final String alias = SelectItemAccessors.getItemAlias(item);
            if (alias != null) {
                earlierOutputNames.add(SqlIdentifiers.canonicalText(alias));
            }
        }
    }

    private PipelineStage planProjection(final Table table,
                                      final FrostlakeParser.SelectClauseContext ctx,
                                      final Map<String, Table> aliasToTable,
                                      final List<Table> allTables,
                                      final Map<String, Object> lateralContext) {
        // Extract projection expressions from SELECT list
        List<String> projectionExpressions = new ArrayList<>();
        final List<String> columnAliases = new ArrayList<>();
        // Index-aligned with the expressions: where each item began in the statement, or null for the
        // ones star-expansion synthesises (nobody wrote those, so they report no position).
        final List<SourcePosition> projectionOrigins = new ArrayList<>();
        // Index-aligned too: the input-row slot an item reads instead of evaluating its expression,
        // -1 for every item that evaluates. Only a star over a relation holding two columns of one
        // name fills these — see starColumnSlots.
        final List<Integer> projectionSlots = new ArrayList<>();
        // The items a bare star projects as the relation's own columns: nobody wrote their names, so the
        // plan-time walks below have nothing to judge in them — a USING join's merged relation may hold a
        // name twice, which a written bare reference could not read.
        final Set<Integer> starProjected = new HashSet<>();

        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (SelectItemAccessors.isStarItem(item)) {
                // Expand STAR (honoring EXCLUDE/RENAME/REPLACE/ILIKE) to its effective columns.
                final List<TableColumn> starColumns = starOrderedColumns(table);
                final List<StarColumn> keptStar = expandStarColumns(item, starColumns, "");
                projectionSlots.addAll(bareStarSlots(table, starColumns, keptStar));
                for (final StarColumn sc : keptStar) {
                    if (!sc.isReplaced()) {
                        starProjected.add(projectionExpressions.size());
                    }
                    projectionExpressions.add(sc.getExpression());
                    columnAliases.add(sc.isRenamed() ? sc.getOutputName() : null);
                    projectionOrigins.add(sc.getOrigin());
                }
            } else if (SelectItemAccessors.isQualifiedStarItem(item)) {
                // Expand t.* or t.** to all columns of the referenced table/alias
                final String[] qualifierParts = SelectItemAccessors.getItemQualifierParts(item);
                final String qualifier = SelectItemAccessors.getItemQualifierSpelled(item);
                Table target = null;
                for (final Map.Entry<String, Table> e : aliasToTable.entrySet()) {
                    if (starQualifierMatches(qualifierParts, e.getKey(), e.getValue(), isAliasKey(aliasToTable, e.getKey()))) {
                        target = e.getValue();
                        break;
                    }
                }
                if (target == null && aliasToTable instanceof FromClauseRelations) {
                    // A qualifier naming no relation of the FROM clause names no object at all — live,
                    // `q.*` over `FROM t x` and `PUBLIC.T.*` over an aliased `FROM db.PUBLIC.T t` alike.
                    throw new RuntimeException(SqlCompilationError.of("Object '"
                        + starQualifierEcho(((FrostlakeParser.QualifiedStarItemContext) item).starQualifiedName())
                        + "' does not exist or not authorized."));
                }
                if (target == null) target = table;
                // EXCLUDE / ILIKE / REPLACE / RENAME over the named relation, as for a plain star.
                final List<StarColumn> keptQualified =
                    expandStarColumns(item, target.getColumns(), qualifier + ".");
                projectionSlots.addAll(starColumnSlots(target.getColumns(), keptQualified,
                    relationRowOffset(target, table, allTables), qualifier + "."));
                for (final StarColumn sc : keptQualified) {
                    projectionExpressions.add(sc.getExpression());
                    columnAliases.add(sc.isRenamed() ? sc.getOutputName() : null);
                    projectionOrigins.add(sc.getOrigin());
                }
            } else if (SelectItemAccessors.isObjectStarItem(item)) {
                // {*} builds ONE object over the row's columns — never the star's N columns.
                projectionExpressions.add(objectStarExpression(item, table, aliasToTable));
                projectionOrigins.add(null);
                projectionSlots.add(-1);
                columnAliases.add(SelectItemAccessors.getItemAlias(item) != null
                    ? (SelectItemAccessors.getItemAlias(item)) : null);
            } else {
                // Regular expression select item
                final ParserRuleContext itemExpr =
                    SelectItemAccessors.getItemExpression(item);
                final String exprText = getOriginalText(itemExpr);
                projectionExpressions.add(exprText);
                projectionOrigins.add(new SourcePosition(itemExpr.getStart().getLine(),
                    itemExpr.getStart().getCharPositionInLine()));
                projectionSlots.add(-1);

                // Get alias if present
                String alias = null;
                if (SelectItemAccessors.getItemAlias(item) != null) {
                    alias = (SelectItemAccessors.getItemAlias(item));
                }
                columnAliases.add(alias);
            }
        }

        if (projectionExpressions.isEmpty()) {
            // A star over a zero-column source (e.g. GENERATOR) — Snowflake's error shape.
            throw noColumns(ctx);
        }

        rejectRestrictedProjections(ctx, projectionExpressions, table, aliasToTable);

        // Apply masking policy substitution — wrap masked column expressions with policy body
        final List<String> beforeMasking = projectionExpressions;
        projectionExpressions = applyMaskingPolicies(projectionExpressions, table);
        // A masked column is no longer its own slot: the policy body has to run.
        for (int i = 0; i < projectionSlots.size() && i < projectionExpressions.size(); i++) {
            if (!projectionExpressions.get(i).equals(beforeMasking.get(i))) {
                projectionSlots.set(i, -1);
            }
        }

        // Lateral column aliases: a map shared between the projection evaluator (as its lateral context) and
        // the PROJECT operator, which fills it left-to-right as each aliased item is computed so a later item
        // can reference an earlier alias by name (Snowflake). The evaluator consults it only AFTER table
        // columns, so a real column of the same name still takes precedence.
        final Map<String, Object> lateralAliases = new HashMap<>();

        // Create expression evaluator based on context
        final RowExpressionEvaluator expressionEvaluator;
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
            // ident.item_key) — without it, the alias-qualified outer key was never assembled and the
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
        final OperatorContext context = OperatorContext.builder()
            .table(table)
            .functionRegistry(functionRegistry).queryExecutor(this)
            .aliasToTable(aliasToTable)
            .allTables(allTables)
            .build();

        // Plan-time strictness: Snowflake argument-type errors fire before any row is projected,
        // so a query over an EMPTY table rejects exactly like a populated one. The exemption set
        // grows item by item: a lateral column alias is referencable only by LATER items
        // (a FORWARD reference is "invalid identifier" live), so each item sees exactly the
        // aliases defined before it.
        final ExpressionEvaluator strictEval = new ExpressionEvaluator(table, functionRegistry, catalog, this);
        strictEval.setMultiTableContext(aliasToTable, allTables);
        final Set<String> earlierOutputNames = new HashSet<>();
        strictEval.setScopeExemptNames(earlierOutputNames);
        // Live orders these refusals by KIND, not by which item carries them: an invalid identifier
        // outranks an unknown function name, and an unknown name outranks every argument-type, arity
        // and semi-structured complaint — measured with the two problems written in both orders. One
        // walk per item cannot produce that, because whichever item comes first would win, so the
        // list is walked once per kind. The exemption set still grows item by item within each pass.
        for (int phase = 0; phase < 2; phase++) {
            earlierOutputNames.clear();
            for (int i = 0; i < projectionExpressions.size(); i++) {
                final SourcePosition phaseOrigin = ExpressionSource.beginNested(projectionOrigins.get(i));
                try {
                    final Expression parsed = ExpressionEvaluator.parse(projectionExpressions.get(i));
                    if (starProjected.contains(i)) {
                        // nothing written to judge
                    } else if (phase == 0) {
                        strictEval.validateColumnScope(parsed);
                    } else {
                        strictEval.validateFunctionNames(parsed);
                    }
                } catch (final RuntimeException unparseable) {
                    if (SqlCompilationError.isCompilationError(unparseable.getMessage())) {
                        throw unparseable;
                    }
                    // Not something these phases can judge — the full walk below reports it.
                } finally {
                    ExpressionSource.end(phaseOrigin);
                }
                if (columnAliases.get(i) != null) {
                    earlierOutputNames.add(SqlIdentifiers.canonicalText(columnAliases.get(i)));
                }
            }
        }
        earlierOutputNames.clear();
        for (int i = 0; i < projectionExpressions.size(); i++) {
            // Scope this item's origin around the walk that rejects it: the refusal is raised here,
            // at plan time, not per row, so the origin has to be in force for the walk.
            final SourcePosition displacedOrigin = ExpressionSource.beginNested(projectionOrigins.get(i));
            try {
                if (!starProjected.contains(i)) {
                    strictEval.validateStrict(ExpressionEvaluator.parse(projectionExpressions.get(i)));
                }
            } finally {
                ExpressionSource.end(displacedOrigin);
            }
            if (columnAliases.get(i) != null) {
                earlierOutputNames.add(SqlIdentifiers.canonicalText(columnAliases.get(i)));
            }
        }
        // The OVER clauses of the whole list, once: their keys live in the parse tree rather than in
        // the item text the walk above parses, and they may name any of the query's aliases — not
        // only the ones written before them.
        if (lateralContext == null) {
            validateWindowKeyScope(ctx.selectList(), table, aliasToTable, allTables,
                selectItemAliasNames(ctx));
        }

        // The PROJECT operator, run by the pipeline (lateralAliases is filled per-item by the operator;
        // under a lateral/correlated execution the outer row's bindings are its per-row base).
        final ProjectOperator projectOp = new ProjectOperator(projectionExpressions, columnAliases,
            expressionEvaluator, lateralAliases, lateralContext);
        projectOp.setExpressionOrigins(projectionOrigins);
        projectOp.setSourceSlots(projectionSlots);
        return new PipelineStage(projectOp, context);
    }

    /**
     * The input-row slot each expanded star column reads, or -1 where its expression is evaluated as
     * usual. A relation may hold two columns of one name — {@code (SELECT 1 a, 2 a) t} — and live's star
     * projects both, each its own value; resolving the expanded {@code t.a} by NAME finds the first one
     * twice. Only those columns read a slot, so every other star keeps resolving by name.
     *
     * @param columns         the relation's columns, in the order the star expanded them
     * @param kept            the expanded columns, a subsequence of {@code columns} in the same order
     * @param base            where the relation's columns start in the input row, or -1 when unknown
     * @param qualifierPrefix "" for a bare star, {@code "alias."} for a qualified one
     * @return one entry per kept column
     */
    private List<Integer> starColumnSlots(final List<TableColumn> columns, final List<StarColumn> kept,
                                          final int base, final String qualifierPrefix) {
        final List<Integer> slots = new ArrayList<>(kept.size());
        for (int i = 0; i < kept.size(); i++) {
            slots.add(-1);
        }
        if (base < 0) {
            return slots;
        }
        int next = 0;
        for (int i = 0; i < columns.size() && next < kept.size(); i++) {
            final String name = columns.get(i).getName();
            final StarColumn column = kept.get(next);
            if (!name.equals(column.getSourceName())) {
                continue;   // a column the star's EXCLUDE or ILIKE left out
            }
            if (countNamed(columns, name) > 1
                    && column.getExpression().equals(qualifierPrefix + columnReferenceText(name))) {
                slots.set(next, base + i);
            }
            next++;
        }
        return slots;
    }

    /**
     * The input-row slot each column of a bare star reads, or -1 where its expression is evaluated by name. A
     * relation may hold several columns of one name — a USING join's two sides' non-key columns, a later
     * relation's own copy of a key, the two sides of an ON join — and live's star projects each its own value
     * ({@code K B B J B} reads {@code 1, 10, 40, 1, 20} over {@code K1 JOIN K4 USING (k) JOIN K2 ON TRUE}), so
     * each of those reads its own place in the combined row; the merged keys a USING join's star leads with
     * read the first non-NULL of their copies, by name.
     *
     * @param table       the relation the star expands
     * @param starColumns its columns in star order (see {@link #starOrderedColumns})
     * @param kept        the expanded columns, a subsequence of {@code starColumns} in the same order
     * @return one entry per kept column
     */
    private List<Integer> bareStarSlots(final Table table, final List<TableColumn> starColumns,
                                        final List<StarColumn> kept) {
        int leadingKeys = 0;
        if (table.getJoinKeyNames() != null) {
            for (final String keyName : table.getJoinKeyNames()) {
                if (leadingKeys < starColumns.size() && starColumns.get(leadingKeys).getName().equals(keyName)) {
                    leadingKeys++;
                }
            }
        }
        final List<Integer> slots = new ArrayList<>(kept.size());
        int next = 0;
        for (int i = 0; i < starColumns.size() && next < kept.size(); i++) {
            final TableColumn column = starColumns.get(i);
            final StarColumn projected = kept.get(next);
            if (!column.getName().equals(projected.getSourceName())) {
                continue;   // a column the star's EXCLUDE or ILIKE left out
            }
            int slot = -1;
            if (i >= leadingKeys && !projected.isReplaced() && countNamed(starColumns, column.getName()) > 1) {
                for (int at = 0; at < table.getColumns().size(); at++) {
                    if (table.getColumns().get(at) == column) {
                        slot = at;
                        break;
                    }
                }
            }
            slots.add(slot);
            next++;
        }
        while (slots.size() < kept.size()) {
            slots.add(-1);
        }
        return slots;
    }

    /** How many of {@code columns} carry {@code name}. */
    private int countNamed(final List<TableColumn> columns, final String name) {
        int count = 0;
        for (final TableColumn column : columns) {
            if (column.getName().equals(name)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Where {@code relation}'s columns start in the FROM-shaped row: 0 when it IS the projection's
     * relation, its offset in the concatenation otherwise, and -1 when the row is not that
     * concatenation (a merged USING relation is wider than its parts) so no slot can be named.
     */
    private int relationRowOffset(final Table relation, final Table table, final List<Table> allTables) {
        if (relation == table) {
            return 0;
        }
        if (allTables == null || allTables.isEmpty()) {
            return -1;
        }
        int width = 0;
        for (final Table each : allTables) {
            width += each.getColumns().size();
        }
        if (table != null && width != table.getColumns().size()) {
            return -1;
        }
        int at = 0;
        for (final Table each : allTables) {
            if (each == relation) {
                return at;
            }
            at += each.getColumns().size();
        }
        return -1;
    }

    /**
     * Expand a {@code SELECT *} (or {@code t.*}) into its effective output columns after applying the item's
     * EXCLUDE / RENAME / REPLACE / ILIKE modifiers (Snowflake column-list modifiers). {@code qualifierPrefix}
     * is "" for a bare star, or "alias." for a qualified star, so each projected value expression resolves
     * against the intended table.
     */
    /**
     * The columns of {@code table} in {@code SELECT *} order: a USING / NATURAL join's key columns
     * first (USING-list / left-table order, one visible copy each), then the rest in physical order.
     * Live: {@code t1(a,k,b) JOIN t2(c,k,d) USING (k)} projects {@code K A B C D}; a chained
     * {@code … JOIN t3 USING (j)} projects {@code J K A B C}; the keys stay first through a later ON
     * join. An ordinary table comes back unchanged.
     */
    private List<TableColumn> starOrderedColumns(final Table table) {
        final List<String> keyNames = table.getJoinKeyNames();
        if (keyNames == null || keyNames.isEmpty()) {
            return table.getColumns();
        }
        final List<TableColumn> rest = new ArrayList<>(table.getColumns());
        final List<TableColumn> ordered = new ArrayList<>();
        for (final String keyName : keyNames) {
            final Iterator<TableColumn> remaining = rest.iterator();
            while (remaining.hasNext()) {
                final TableColumn candidate = remaining.next();
                if (!candidate.isHiddenFromStar() && candidate.getName().equals(keyName)) {
                    ordered.add(candidate);
                    remaining.remove();
                    break;
                }
            }
        }
        ordered.addAll(rest);
        return ordered;
    }

    /** Whether any column of {@code table} is hidden from {@code SELECT *} — true for a USING /
     *  NATURAL join's merged relation, whose combined rows are WIDER than the star's column list, so
     *  even a bare star must project rather than pass rows through. */
    private boolean hasHiddenStarColumns(final Table table) {
        for (final TableColumn col : table.getColumns()) {
            if (col.isHiddenFromStar()) {
                return true;
            }
        }
        return false;
    }

    /** The per-column expressions a bare {@code *} item projects over {@code table}, in star order —
     *  for row-reshaping paths (the window projection) that must never copy the raw combined row. */
    List<String> bareStarExpressions(final FrostlakeParser.SelectItemContext item, final Table table) {
        final List<String> expressions = new ArrayList<>();
        for (final StarColumn sc : expandStarColumns(item, starOrderedColumns(table), "")) {
            expressions.add(sc.getExpression());
        }
        return expressions;
    }

    /**
     * The values a bare star item projects from one FROM-shape row in the window stage: a column whose name
     * the relation holds more than once read from its own slot (see {@link #bareStarSlots}), every other one
     * evaluated as its expression.
     */
    List<Object> bareStarValues(final FrostlakeParser.SelectItemContext item, final Table table, final Row row) {
        final List<TableColumn> starColumns = starOrderedColumns(table);
        final List<StarColumn> kept = expandStarColumns(item, starColumns, "");
        final List<Integer> slots = bareStarSlots(table, starColumns, kept);
        final List<Object> values = new ArrayList<>(kept.size());
        for (int i = 0; i < kept.size(); i++) {
            final int slot = slots.get(i);
            values.add(slot >= 0 && slot < row.getValues().size() ? row.getValue(slot)
                : evaluateExpression(kept.get(i).getExpression(), row, table));
        }
        return values;
    }

    /**
     * How many output columns a qualified star item projects in the window stage: the columns of the relation its
     * qualifier names — resolved through the join's alias context while the window stage runs over a join — after
     * its modifiers.
     */
    int qualifiedStarWidth(final FrostlakeParser.SelectItemContext item, final Table table) {
        final Map<String, Table> aliases = currentWindowAliasToTable();
        return starItemColumns(item, table, aliases != null ? aliases : Collections.<String, Table>emptyMap()).size();
    }

    /**
     * The values a qualified star item projects from one FROM-shape row in the window stage: every column the star
     * keeps, read from its own slot of the row — the named relation's segment of it over a join — and every
     * REPLACE'd column evaluated. Read by POSITION rather than by name, so two columns of one name each keep their
     * own value, as live keeps them.
     */
    List<Object> qualifiedStarValues(final FrostlakeParser.SelectItemContext item, final Table table, final Row row) {
        final Map<String, Table> aliases = currentWindowAliasToTable();
        final List<Table> relations = currentWindowAllTables();
        Table target = table;
        int offset = 0;
        if (aliases != null && relations != null) {
            final String[] qualifierParts = SelectItemAccessors.getItemQualifierParts(item);
            for (final Map.Entry<String, Table> entry : aliases.entrySet()) {
                if (starQualifierMatches(qualifierParts, entry.getKey(), entry.getValue(),
                        isAliasKey(aliases, entry.getKey()))) {
                    int at = 0;
                    for (final Table relation : relations) {
                        if (relation == entry.getValue()) {
                            target = relation;
                            offset = at;
                            break;
                        }
                        at += relation.getColumns().size();
                    }
                    break;
                }
            }
        }
        final String prefix = SelectItemAccessors.getItemQualifierSpelled(item) + ".";
        final List<TableColumn> columns = target.getColumns();
        final List<StarColumn> kept = expandStarColumns(item, columns, prefix);
        final List<Object> values = new ArrayList<>();
        int next = 0;
        for (int i = 0; i < columns.size() && next < kept.size(); i++) {
            final StarColumn column = kept.get(next);
            final String name = columns.get(i).getName();
            if (!name.equals(column.getSourceName())) {
                continue;   // a column the star's EXCLUDE or ILIKE left out
            }
            next++;
            if (column.getExpression().equals(prefix + columnReferenceText(name))) {
                values.add(offset + i < row.getValues().size() ? row.getValue(offset + i) : null);
            } else {
                values.add(evaluateExpression(column.getExpression(), row, table));
            }
        }
        return values;
    }

    /**
     * A star select item's effective columns, for the GROUP BY paths: a bare {@code *} over the whole
     * relation, {@code t.*} over the one relation it names, each with its EXCLUDE / ILIKE / RENAME /
     * REPLACE modifiers honoured. Live holds every expanded column to the grouped-select rule as if it had
     * been written out, so the grouped machinery needs the same per-column expansion the plain
     * projection performs.
     */
    List<StarColumn> starItemColumns(final FrostlakeParser.SelectItemContext item, final Table table,
                                     final Map<String, Table> aliasToTable) {
        if (SelectItemAccessors.isStarItem(item)) {
            return expandStarColumns(item, starOrderedColumns(table), "");
        }
        final String[] qualifierParts = SelectItemAccessors.getItemQualifierParts(item);
        final String qualifier = SelectItemAccessors.getItemQualifierSpelled(item);
        Table target = null;
        for (final Map.Entry<String, Table> e : aliasToTable.entrySet()) {
            if (starQualifierMatches(qualifierParts, e.getKey(), e.getValue(), isAliasKey(aliasToTable, e.getKey()))) {
                target = e.getValue();
                break;
            }
        }
        if (target == null) {
            target = table;
        }
        return expandStarColumns(item, target.getColumns(), qualifier + ".");
    }

    /**
     * A column's name as select-item text that reads back as a reference to that column: bare where it
     * does, double-quoted otherwise. A column named DEFAULT, TRUE or CURRENT_DATE would read as the
     * keyword, a literal or a call, and one named {@code a b} or in lower case as no such column.
     */
    private static String columnReferenceText(final String name) {
        try {
            final Expression parsed = ExpressionEvaluator.parse(name);
            if (parsed instanceof ColumnReferenceExpression) {
                final ColumnReferenceExpression reference = (ColumnReferenceExpression) parsed;
                if (!reference.isQualified() && reference.getPositionalOrdinal() == 0
                        && name.equals(reference.getColumnName())) {
                    return name;
                }
            }
        } catch (final RuntimeException notAReference) {
            // Not an expression on its own: quoted below.
        }
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    private List<StarColumn> expandStarColumns(final FrostlakeParser.SelectItemContext item,
                                               final List<TableColumn> columns, final String qualifierPrefix) {
        final Set<String> excluded = new HashSet<>();
        final Map<String, String> renames = new HashMap<>();
        final Map<String, String> replaces = new HashMap<>();
        final Map<String, SourcePosition> replaceOrigins = new HashMap<>();
        Pattern ilike = null;
        for (final FrostlakeParser.StarModifierContext mod : SelectItemAccessors.getStarModifiers(item)) {
            // Each modifier names a column by its CANONICAL spelling and keys it exactly: EXCLUDE ("default")
            // drops the quoted lower-case column and leaves a DEFAULT beside it (live-verified).
            if (mod.EXCLUDE() != null) {
                for (final FrostlakeParser.ExcludedColumnContext id : mod.excludedColumn()) {
                    excluded.add(SelectItemAccessors.excludedName(id));
                }
            } else if (mod.ILIKE() != null) {
                final String lit = mod.STRING_LITERAL().getText();
                ilike = Pattern.compile(ilikeToRegex(lit.substring(1, lit.length() - 1)), Pattern.CASE_INSENSITIVE);
            } else if (mod.RENAME() != null) {
                for (final FrostlakeParser.StarRenameItemContext r : mod.starRenameItem()) {
                    renames.put(getIdentifier(r.identifier(0)), getIdentifier(r.identifier(1)));
                }
            } else if (mod.REPLACE() != null) {
                for (final FrostlakeParser.StarReplaceItemContext r : mod.starReplaceItem()) {
                    replaces.put(getIdentifier(r.identifier()), getOriginalText(r.expression()));
                    replaceOrigins.put(getIdentifier(r.identifier()), new SourcePosition(
                        r.expression().getStart().getLine(), r.expression().getStart().getCharPositionInLine()));
                }
            }
        }
        final List<StarColumn> result = new ArrayList<>();
        for (final TableColumn col : columns) {
            final String name = col.getName();
            final String key = name;
            if (!StarModifierCheck.starVisible(col)) {
                continue;
            }
            if (excluded.contains(key) || (ilike != null && !ilike.matcher(name).matches())) {
                continue;
            }
            final String replaceExpr = replaces.get(key);
            final String expression = replaceExpr != null ? replaceExpr : (qualifierPrefix + columnReferenceText(name));
            // A REPLACE'd column is an expression, and an expression always accepts NULL; the column
            // itself carries its own NOT NULL through, RENAME included.
            result.add(new StarColumn(expression, renames.getOrDefault(key, name), name,
                col.getDataType(), replaceExpr != null || col.isNullable(), replaceExpr == null,
                replaceOrigins.get(key), replaceExpr != null));
        }
        return result;
    }

    /**
     * Render Snowflake's braced star ({@code {*}}, {@code {t.* EXCLUDE (c)}}) as the OBJECT_CONSTRUCT call it
     * stands for: {@code {*}} is {@code OBJECT_CONSTRUCT(*)}, one object-valued column whose keys are the
     * star's effective column names and whose values are that row's values. The participating columns come
     * from {@link #expandStarColumns}, so EXCLUDE / ILIKE / RENAME / REPLACE are read by the one modifier
     * interpreter the plain star already uses, and the object itself is built by the ordinary function.
     */
    String objectStarExpression(final FrostlakeParser.SelectItemContext item, final Table table,
                                final Map<String, Table> aliasToTable) {
        final String[] qualifierParts = SelectItemAccessors.getItemQualifierParts(item);
        final String qualifier = SelectItemAccessors.getItemQualifierSpelled(item);
        Table source = table;
        String qualifierPrefix = "";
        if (qualifier != null) {
            qualifierPrefix = qualifier + ".";
            if (aliasToTable != null) {
                for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
                    if (starQualifierMatches(qualifierParts, entry.getKey(), entry.getValue(),
                            isAliasKey(aliasToTable, entry.getKey()))) {
                        source = entry.getValue();
                        break;
                    }
                }
            }
        }
        final StringBuilder call = new StringBuilder("OBJECT_CONSTRUCT(");
        boolean firstPair = true;
        for (final StarColumn sc : expandStarColumns(item, starOrderedColumns(source), qualifierPrefix)) {
            if (!firstPair) {
                call.append(", ");
            }
            firstPair = false;
            call.append(SqlStringLiterals.encode(sc.getOutputName())).append(", ").append(sc.getExpression());
        }
        return call.append(')').toString();
    }

    /** Convert a SQL ILIKE pattern (%, _) into a case-insensitive regex for matching column names. */
    static String ilikeToRegex(final String pattern) {
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
        @Override
        public String toString() { return "NULL"; }
    };

    private List<Row> applyGroupBy(final List<Row> rows, final Table table,
                                   final FrostlakeParser.SelectClauseContext ctx,
                                   final Map<String, Table> aliasToTable,
                                   final List<Table> allTables,
                                   final List<List<Row>> groupRowsSink,
                                   final Map<String, Object> lateralContext) {
        return groupByEvaluator.applyGroupBy(rows, table, ctx, aliasToTable, allTables, groupRowsSink, lateralContext);
    }

    /** The one implicit-group row repeated {@code count} times, each copy mapped to the same source group. */
    private static List<Row> repeatedAggregateRow(final Row row, final int count, final List<List<Row>> groupRows) {
        final List<Row> copies = new ArrayList<>(count);
        final List<Row> group = groupRows.isEmpty() ? null : groupRows.get(0);
        copies.add(row);
        for (int i = 1; i < count; i++) {
            copies.add(new Row(new ArrayList<>(row.getValues())));
            if (group != null) {
                groupRows.add(group);
            }
        }
        return copies;
    }

    /**
     * A constant HAVING of an aggregating query, settled before the rows are grouped (see {@link ConstantHaving}):
     * a predicate that is not TRUE leaves nothing to group, so no key and no aggregate is computed. The scalar
     * subqueries of the select list that live computes ahead of the rows are computed first, and what they raise
     * is raised (see {@link SubqueryAhead}). A predicate that faults is raised as the plan reaches it: with a
     * GROUP BY only when a row reaches the grouping, and without one after the aggregates, by the HAVING stage
     * after the grouping — {@code SELECT v FROM e GROUP BY v HAVING 1/0 = 1} over an empty {@code e} answers no
     * row, and {@code SELECT SUM(v/0) FROM t HAVING TO_NUMBER('x') = 1} is "Division by zero" (live-verified).
     * That later stage also filters the one group an implicit aggregation makes of no rows.
     */
    private Operator constantHavingStage(final FrostlakeParser.SelectClauseContext ctx, final boolean groupBy,
                                         final Table table, final Map<String, Table> aliasToTable,
                                         final List<Table> allTables, final Map<String, Object> lateralContext) {
        final String predicateText = getOriginalText(ctx.havingClause().booleanExpr());
        return new StageOperator("HAVING[" + predicateText + "] before grouping") {
            @Override
            protected List<Row> apply(final List<Row> input) {
                final ExpressionEvaluator itemEval =
                    new ExpressionEvaluator(table, functionRegistry, catalog, QueryExecutor.this);
                if (aliasToTable != null && allTables != null && !allTables.isEmpty()) {
                    itemEval.setMultiTableContext(aliasToTable, allTables);
                }
                itemEval.setOuterLateralContext(lateralContext);
                final List<Object> noValues = new ArrayList<>();
                for (int i = 0; table != null && i < table.getColumns().size(); i++) {
                    noValues.add(null);
                }
                SubqueryAhead.computeAhead(QueryExecutor.this, ctx.selectList(), itemEval,
                    input.isEmpty() ? new Row(noValues) : input.get(0));
                if (groupBy && input.isEmpty()) {
                    return input;
                }
                final ExpressionEvaluator constantEval =
                    new ExpressionEvaluator(null, functionRegistry, catalog, QueryExecutor.this);
                final Object settled;
                try {
                    settled = constantEval.evaluate(predicateText, new Row(new ArrayList<Object>()));
                } catch (final RuntimeException faulted) {
                    if (groupBy || !DeferredFault.deferrable(faulted)) {
                        throw faulted;
                    }
                    return input;
                }
                return SqlTruth.isTrue(settled) ? input : new ArrayList<Row>();
            }
        };
    }

    /**
     * The HAVING of a query that groups nothing: a second WHERE over the rows the first one kept, which may read
     * the select list's aliases as the WHERE does, positioned in the HAVING.
     */
    private Operator planUngroupedHaving(final FrostlakeParser.SelectClauseContext ctx, final Table table,
                                         final Map<String, Object> lateralContext,
                                         final Map<String, Table> aliasToTable, final List<Table> allTables,
                                         final FrostlakeParser.TableExpressionContext tableExpr) {
        final FrostlakeParser.BooleanExprContext predicate = ctx.havingClause().booleanExpr();
        final String predicateText = getOriginalText(predicate);
        final Operator filter = planWhere(table, predicateText, lateralContext, aliasToTable, allTables, tableExpr,
            ctx);
        final SourcePosition origin =
            new SourcePosition(predicate.getStart().getLine(), predicate.getStart().getCharPositionInLine());
        return new StageOperator("HAVING[" + predicateText + "]") {
            @Override
            protected List<Row> apply(final List<Row> input) {
                final SourcePosition displaced = ExpressionSource.beginNested(origin);
                try {
                    return filter.execute(input, null);
                } finally {
                    ExpressionSource.end(displaced);
                }
            }
        };
    }

    /**
     * The HAVING stage: filters the grouped rows on their aggregate conditions.
     */
    private Operator planHaving(final FrostlakeParser.SelectClauseContext ctx,
                                  final Table table, final Map<String, Table> aliasToTable,
                                  final List<Table> allTables, final List<List<Row>> groupRows,
                                  final Map<String, Object> lateralContext) {
        final String havingExpr = getOriginalText(ctx.havingClause().booleanExpr());
        final FrostlakeParser.BooleanExprContext havingBool = ctx.havingClause().booleanExpr();

        // Plan-time HAVING scope validation: an invalid qualifier inside an aggregate argument
        // ("HAVING max(r.v) > 0" with r aliased away) is a compile-time error live, empty group
        // set included.
        validateClauseScope(havingExpr, table, aliasToTable, allTables, selectItemAliasNames(ctx));

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
            aliasKeys.add(SelectItemAccessors.getItemAlias(item) != null ? (SelectItemAccessors.getItemAlias(item)) : null);
        }

        // Map each output row to its source group rows, so an aggregate referenced in HAVING but NOT
        // projected in SELECT (e.g. SELECT grp, COUNT(*)::VARCHAR … HAVING COUNT(*) > 1) can be computed
        // fresh over the group instead of read back from the already-projected row. Keyed by identity —
        // HavingOperator passes the same Row instances through.
        final Map<Row, List<Row>> rowToGroup = new IdentityHashMap<>();

        final ExpressionEvaluator ev = new ExpressionEvaluator(null, functionRegistry, catalog, this);
        // Inside a correlated subquery HAVING reads the outer row too (live-verified). The result context
        // below is consulted first, so the subquery's own group keys still win over an outer name.
        if (lateralContext != null) {
            ev.setOuterLateralContext(lateralContext);
        }
        // A grouping key the select list does not project is still the group's value in HAVING: GROUP BY id
        // HAVING id > 5 answers the groups past 5 (live-verified). Each plain key is read off the group's
        // first row, which every row of the group agrees on.
        final List<String> keyTexts = new ArrayList<>();
        final List<String> keyPrints = new ArrayList<>();
        final List<String> keyColumns = new ArrayList<>();
        if (ctx.groupByClause() != null && ctx.groupByClause().ALL() == null) {
            for (final FrostlakeParser.GroupByElementContext element : ctx.groupByClause().groupByElement()) {
                if (element.expression() == null) {
                    continue;
                }
                final String keyText = getOriginalText(element.expression());
                final Expression parsedKey;
                try {
                    parsedKey = ExpressionEvaluator.parse(keyText);
                } catch (final RuntimeException unparseable) {
                    continue;
                }
                if (parsedKey instanceof LiteralExpression) {
                    continue;   // an ordinal names a select item, which the context already holds
                }
                keyTexts.add(keyText);
                keyPrints.add(AstPrinterVisitor.print(parsedKey));
                keyColumns.add(parsedKey instanceof ColumnReferenceExpression
                    ? ((ColumnReferenceExpression) parsedKey).getColumnName().toUpperCase() : null);
            }
        }
        final ExpressionEvaluator keyEval = new ExpressionEvaluator(table, functionRegistry, catalog, this);
        if (aliasToTable != null && allTables != null && !allTables.isEmpty()) {
            keyEval.setMultiTableContext(aliasToTable, allTables);
        }
        // The HAVING tree's aggregate calls and their canonical keys are per-STATEMENT facts —
        // collected once here, evaluated per group inside the filter below.
        final List<FrostlakeParser.ExpressionContext> havingAggCalls =
            groupByEvaluator.collectHavingAggregateCalls(havingBool);
        final List<String> havingAggKeys = groupByEvaluator.havingAggregateKeys(havingAggCalls);
        // A subquery in HAVING reads the group's own row as its outer row, where the condition itself reads
        // the grouped output: HAVING (SELECT COUNT(*) FROM g WHERE g.id = fz.id) = 0 (live-verified).
        final boolean havingReadsSubqueries = holdsSubquery(havingBool);
        // An aggregate the subquery writes over THIS query's columns is this query's own, computed over the
        // group and offered to the subquery beside the row (live-verified).
        final List<FrostlakeParser.ExpressionContext> outerAggCalls = havingReadsSubqueries
            ? groupByEvaluator.outerAggregateCallsInsideSubqueries(havingBool, table, aliasToTable, allTables)
            : new ArrayList<FrostlakeParser.ExpressionContext>();
        final List<String> outerAggKeys = groupByEvaluator.havingAggregateKeys(outerAggCalls);
        final HavingEvaluator havingEvaluator = new HavingEvaluator() {
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
                    rc.putAll(groupByEvaluator.havingAggregateContext(
                        havingAggCalls, havingAggKeys, group, table, aliasToTable, allTables));
                    if (havingReadsSubqueries && !group.isEmpty()) {
                        final Map<String, Object> bindings = new HashMap<>(keyEval.rowBindings(group.get(0)));
                        bindings.putAll(groupByEvaluator.havingAggregateContext(outerAggCalls, outerAggKeys,
                            group, table, aliasToTable, allTables));
                        ev.setSubqueryOuterRow(bindings);
                    }
                    for (int k = 0; k < keyTexts.size() && !group.isEmpty(); k++) {
                        if (rc.containsKey(keyPrints.get(k))) {
                            continue;
                        }
                        final Object keyValue = keyEval.evaluate(keyTexts.get(k), group.get(0));
                        rc.put(keyPrints.get(k), keyValue);
                        if (keyColumns.get(k) != null && !rc.containsKey(keyColumns.get(k))) {
                            rc.put(keyColumns.get(k), keyValue);
                        }
                    }
                }
                ev.setResultContext(rc);
                final Object result = ev.evaluate(condition, aggregatedRow);
                return SqlTruth.isTrue(result);
            }
        };

        // Build operator context
        final OperatorContext context = OperatorContext.builder()
            .table(null) // HAVING doesn't need the original table, it works on aggregated results
            .functionRegistry(functionRegistry).queryExecutor(this)
            .build();

        // Create and execute HAVING operator. A refusal raised while the groups are filtered is positioned in
        // the HAVING, as a WHERE's is.
        final HavingOperator havingOp = new HavingOperator(havingExpr, havingEvaluator);
        final SourcePosition havingOrigin =
            new SourcePosition(havingBool.getStart().getLine(), havingBool.getStart().getCharPositionInLine());
        return new StageOperator(havingOp.getDescription()) {
            @Override
            protected List<Row> apply(final List<Row> input) {
                // Each output row to its source group, by identity: the operator passes the same instances on.
                rowToGroup.clear();
                if (groupRows != null) {
                    for (int i = 0; i < input.size() && i < groupRows.size(); i++) {
                        rowToGroup.put(input.get(i), groupRows.get(i));
                    }
                }
                final SourcePosition displaced = ExpressionSource.beginNested(havingOrigin);
                try {
                    return havingOp.execute(input, context);
                } finally {
                    ExpressionSource.end(displaced);
                }
            }
        };
    }

    /**
     * The canonical prints of the aggregates a GROUPED query offers the subqueries of its select list,
     * HAVING and ORDER BY — the ones written there over its own columns, which it computes over the group
     * (see {@link GroupByAggregateEvaluator#outerAggregateCallsInsideSubqueries}). A query that groups
     * only implicitly offers none: live refuses {@code SELECT (SELECT COUNT(*) FROM g WHERE g.id <=
     * MAX(fz.id)) FROM fz} (live-verified).
     *
     * @param clause       the query's select clause
     * @param table        its relation
     * @param aliasToTable its FROM-clause keys
     * @param allTables    every relation of its FROM clause
     * @return the prints, upper-cased as a name binding is
     */
    Set<String> groupedSubqueryAggregateKeys(final FrostlakeParser.SelectClauseContext clause, final Table table,
                                             final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final Set<String> keys = new HashSet<>();
        if (clause.groupByClause() == null) {
            return keys;
        }
        final List<FrostlakeParser.ExpressionContext> calls = new ArrayList<>();
        calls.addAll(groupByEvaluator.outerAggregateCallsInsideSubqueries(
            clause.selectList(), table, aliasToTable, allTables));
        if (clause.havingClause() != null) {
            calls.addAll(groupByEvaluator.outerAggregateCallsInsideSubqueries(
                clause.havingClause(), table, aliasToTable, allTables));
        }
        for (final String key : groupByEvaluator.havingAggregateKeys(calls)) {
            keys.add(key.toUpperCase());
        }
        return keys;
    }

    /**
     * Apply QUALIFY clause using operator pipeline.
     * QUALIFY filters rows based on window function results.
     *
     * @param table     the shape the ROWS are in — the SELECT-list layout over grouped rows
     * @param baseTable the FROM relation, which is what an inline window's arguments are SCOPED
     *                  against: over grouped rows the two differ, and a window argument may name a
     *                  base column the projection never carries (LAG(SUM(b)) beside a select list of a)
     */
    private List<Row> applyQualify(final List<Row> rows, final FrostlakeParser.SelectClauseContext ctx,
                                    final Map<Integer, Map<Integer, Object>> windowFunctionResults,
                                    final Table table, final Table baseTable,
                                    final boolean rowsAreBaseShape) {
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
                nonWindowAliasExprs.put((SelectItemAccessors.getItemAlias(item)).toUpperCase(),
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
            // Scoped against the BASE relation, not the row shape. An inline window's argument may be a
            // RAW AGGREGATE over a column the grouped projection dropped — QUALIFY LAG(SUM(b)) OVER (…)
            // beside `SELECT a … GROUP BY a` — and checking it against the SELECT-list shape refused a
            // query live runs, naming the aggregated column as an invalid identifier. This is the same
            // rule the SELECT list's own window-key walk follows.
            windowEvaluator.rejectFileWindowArguments(inlineWindowFns, baseTable,
                selectItemAliasNames(ctx));
            final Map<FrostlakeParser.OverClauseContext, Map<List<Object>, List<Row>>> overCache = new HashMap<>();
            windowEvaluator.resetPartitionOrderCache();
            // Expose the SELECT aliases so an inline QUALIFY window's PARTITION BY / ORDER BY can name one
            // (e.g. QUALIFY ROW_NUMBER() OVER (PARTITION BY <select-alias> ...) = 1). Over SELECT-shape
            // rows (grouped/aggregated), also open the canonical select-item index so a raw aggregate in
            // a window key — OVER (ORDER BY SUM(x)) — resolves to the item's computed value positionally.
            final Map<String, String> savedAliases =
                windowEvaluator.beginWindowAliasScope(ctx, !rowsAreBaseShape);
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
            qAliases.add(SelectItemAccessors.getItemAlias(item) != null ? (SelectItemAccessors.getItemAlias(item)) : null);
        }
        final ExpressionEvaluator qEv = new ExpressionEvaluator(table, functionRegistry, catalog, this);
        final QualifyEvaluator qualifyEvaluator = new QualifyEvaluator() {
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
        final OperatorContext context = OperatorContext.builder()
            .table(table)
            .functionRegistry(functionRegistry).queryExecutor(this)
            .build();

        // Create and execute QUALIFY operator
        final QualifyOperator qualifyOp = new QualifyOperator(qualifyExpr, qualifyEvaluator);
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
     * Whether a star's qualifier names a relation: its alias, or its own name; and for a relation with no
     * alias of its own, also its name qualified by its schema or by its database and schema, the way live
     * reads {@code db.s.t.*} and {@code db..t.*}. A written alias replaces the table's whole name, so an
     * aliased relation is never reached through a qualified one, even where the alias folds to that name.
     */
    static boolean starQualifierMatches(final String[] written, final String alias, final Table table,
                                        final boolean aliased) {
        // By canonical name: a quoted part names itself exactly, as an unquoted one names its upper-cased self.
        if (written.length == 1 && (written[0].equals(alias) || !aliased && written[0].equals(table.getName()))) {
            return true;
        }
        if (aliased || !alias.equals(table.getName()) || table.getQualifiedName() == null) {
            return false;
        }
        final String[] own = QualifiedName.parse(table.getQualifiedName()).parts();
        if (written.length > own.length) {
            return false;
        }
        for (int i = 1; i <= written.length; i++) {
            if (!written[written.length - i].equals(own[own.length - i])) {
                return false;
            }
        }
        return true;
    }

    /** Whether a FROM-clause key is an alias the query wrote (see {@link FromClauseRelations}). */
    private static boolean isAliasKey(final Map<String, Table> relations, final String key) {
        return relations instanceof FromClauseRelations && ((FromClauseRelations) relations).isAlias(key);
    }

    /**
     * A star's qualifier as live echoes an object it cannot find: each part by its name, quoted only where
     * the name needs it, and an omitted schema read as PUBLIC. {@code fz.*} and {@code "FZ".*} echo
     * {@code FZ}, {@code "fz".*} echoes {@code "fz"} and {@code db..fz.*} echoes {@code DB.PUBLIC.FZ}.
     */
    static String starQualifierEcho(final FrostlakeParser.StarQualifiedNameContext qualifier) {
        final String[] parts = ParseTreeText.qualifiedNameParts(qualifier);
        final StringBuilder echo = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                echo.append('.');
            }
            echo.append(SqlIdentifiers.spellCanonical(parts[i]));
        }
        return echo.toString();
    }

    /**
     * The refusal of a select list that projects nothing, a star whose modifiers or source left no column and
     * no other item: {@code SELECT * ILIKE 'zz%' FROM t} and {@code SELECT * EXCLUDE (column1)} alike, at the
     * select clause's first token (live-verified).
     *
     * @param ctx the select clause
     * @return the refusal
     */
    private static RuntimeException noColumns(final FrostlakeParser.SelectClauseContext ctx) {
        final Token start = ctx.getStart();
        final SourcePosition at = ExpressionSource.place(start.getLine(), start.getCharPositionInLine());
        return new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
            "SELECT with no columns"));
    }

    /**
     * Refuses a qualified star in a select list with no FROM. The star reads only its own query's FROM, so
     * its qualifier names no object but DUAL, whatever the queries around it read: {@code SELECT fz.*} is
     * "Object 'FZ' does not exist or not authorized.", in a subquery of a query over FZ too. Only a
     * window frame's rules come first; every name, function, subquery and clause rule waits for it
     * (live-verified).
     */
    private static void rejectQualifiedStarWithoutFrom(final FrostlakeParser.SelectClauseContext ctx) {
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (SelectItemAccessors.isQualifiedStarItem(item)
                    && !FromlessDual.namesDual(((FrostlakeParser.QualifiedStarItemContext) item).starQualifiedName())) {
                throw new RuntimeException(SqlCompilationError.of("Object '"
                    + starQualifierEcho(((FrostlakeParser.QualifiedStarItemContext) item).starQualifiedName())
                    + "' does not exist or not authorized."));
            }
        }
    }

    /** The user table function a call names: in its own schema when qualified, else the current one. */
    private Function resolveUdtf(final FrostlakeParser.FunctionNameContext nameCtx) {
        final String[] parts = ParseTreeText.functionNameParts(nameCtx);
        if (parts == null || parts.length > 3) {
            return null;
        }
        try {
            final String db = parts.length == 3 ? parts[0] : catalog.getCurrentDatabase();
            final String sc = parts.length >= 2 ? parts[parts.length - 2] : catalog.getCurrentSchema();
            if (db == null || sc == null) return null;
            // By the exact canonical name: TABLE(tabcase()) does not reach "tabCase" (live-verified).
            final List<Function> overloads =
                catalog.getDatabase(db).getSchema(sc).getFunctionOverloads(parts[parts.length - 1]);
            return overloads.size() == 1 && overloads.get(0).isTableFunction() ? overloads.get(0) : null;
        } catch (final Exception e) {
            return null;
        }
    }

    private String substituteSqlParams(final String body,
                                        final List<Parameter> params,
                                        final List<Object> args) {
        String sql = body;
        for (int i = 0; i < params.size() && i < args.size(); i++) {
            final String paramName = params.get(i).getName();
            final Object value = args.get(i);
            final String literal = value == null ? "NULL"
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

    /**
     * Whether an ORDER BY key or a QUALIFY predicate carries an aggregate. Either makes the whole query
     * aggregate, exactly as a select item would: live answers {@code SELECT a FROM g ORDER BY SUM(b)}
     * and {@code SELECT a FROM g QUALIFY SUM(b) > 0} with "[G.A] is not a valid group by expression" —
     * the ungrouped SELECT LIST being refused, with no complaint about the aggregate's place at all.
     *
     * @param stmtCtx the statement, which carries the ORDER BY
     * @param ctx     the select clause, which carries the QUALIFY
     * @return true when either clause aggregates
     */
    private boolean aggregatesInOrderByOrQualify(final FrostlakeParser.SelectStatementContext stmtCtx,
                                                 final FrostlakeParser.SelectClauseContext ctx) {
        if (stmtCtx != null && stmtCtx.orderByClause() != null
                && windowEvaluator.hasAggregateInTree(stmtCtx.orderByClause())) {
            return true;
        }
        return ctx.qualifyClause() != null
            && windowEvaluator.hasAggregateInTree(ctx.qualifyClause());
    }

    /**
     * Whether the query aggregates at all — the select list, HAVING, an ORDER BY key or a QUALIFY
     * predicate.
     *
     * @param stmtCtx the statement
     * @param ctx     the select clause
     * @return true when the query is an aggregate one
     */
    private boolean aggregatesAnywhere(final FrostlakeParser.SelectStatementContext stmtCtx,
                                       final FrostlakeParser.SelectClauseContext ctx) {
        return hasAggregateFunction(ctx) || havingAggregates(ctx)
            || aggregatesInOrderByOrQualify(stmtCtx, ctx);
    }

    /**
     * Whether the HAVING writes an aggregate of this query. A HAVING that writes none groups nothing by
     * itself: with no GROUP BY and no aggregate elsewhere, live filters the rows the WHERE keeps with it, one
     * row out per row in, before any item is computed — {@code SELECT v FROM t HAVING v = 1} answers the rows
     * holding 1, {@code SELECT 1 FROM t HAVING TRUE} one row per row of t, and {@code SELECT 1 WHERE FALSE
     * HAVING TRUE} no row at all.
     *
     * @param ctx the select clause
     * @return true when the HAVING holds an aggregate call outside its nested queries
     */
    private boolean havingAggregates(final FrostlakeParser.SelectClauseContext ctx) {
        return ctx.havingClause() != null && windowEvaluator.hasAggregateInTree(ctx.havingClause());
    }

    /**
     * Whether a FROM-less select's HAVING, if it has one, is a filter over its one row: no GROUP BY groups the
     * row first — a GROUP BY key is computed before the HAVING is — and the HAVING writes no window call, which
     * only the synthesised row's route refuses.
     */
    private boolean havingFiltersFromlessRow(final FrostlakeParser.SelectClauseContext ctx) {
        if (ctx.havingClause() == null) {
            return true;
        }
        final List<FrostlakeParser.FunctionCallExprContext> found = new ArrayList<>();
        collectWindowCallsOutsideNestedQueries(ctx.havingClause(), found);
        return ctx.groupByClause() == null && found.isEmpty();
    }

    /** Whether a select clause writes a QUALIFY with no window function in its select list or its predicate. */
    private boolean qualifiesWithoutWindow(final FrostlakeParser.SelectClauseContext ctx) {
        return ctx.qualifyClause() != null && !windowEvaluator.hasWindowFunctionInTreeOrSubqueries(ctx.selectList())
            && !windowEvaluator.hasWindowFunctionInTreeOrSubqueries(ctx.qualifyClause());
    }

    /**
     * Live's refusal for a QUALIFY with no window function anywhere — in the select list or in the
     * predicate itself — anchored on the clause's own keyword.
     *
     * @param ctx the select clause
     */
    private void rejectQualifyWithoutWindow(final FrostlakeParser.SelectClauseContext ctx) {
        if (!qualifiesWithoutWindow(ctx)) {
            return;
        }
        final Token qualifyStart = ctx.qualifyClause().getStart();
        final SourcePosition at = ExpressionSource.place(qualifyStart.getLine(), qualifyStart.getCharPositionInLine());
        throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
            "found QUALIFY clause but no window function."));
    }

    /**
     * The QUALIFY-without-a-window refusal, raised before any group or row is computed, so a value an
     * aggregate cannot compute never speaks first; but only once the rest of the query has compiled. The
     * select list's argument checks — a grouped list judged over no rows — the HAVING's, and a refusal a
     * subquery's compilation handed back all come ahead of it, which is live's order.
     *
     * @param ctx             the select clause
     * @param table           the relation the clauses are written over
     * @param aliasToTable    the FROM-clause keys
     * @param allTables       every relation in scope
     * @param hasGroupBy      whether the query groups by keys
     * @param hasAggregates   whether the query aggregates without keys
     * @param subqueryRefusal the refusal a subquery's compilation handed back, or null
     * @param lateralContext  the names of the query around this one, or null
     */
    private void rejectWindowlessQualifyBeforeRows(final FrostlakeParser.SelectClauseContext ctx, final Table table,
                                                   final Map<String, Table> aliasToTable,
                                                   final List<Table> allTables, final boolean hasGroupBy,
                                                   final boolean hasAggregates,
                                                   final RuntimeException subqueryRefusal,
                                                   final Map<String, Object> lateralContext) {
        if (!qualifiesWithoutWindow(ctx)) {
            return;
        }
        if (hasGroupBy) {
            applyGroupBy(new ArrayList<Row>(), table, ctx, aliasToTable, allTables, new ArrayList<List<Row>>(),
                lateralContext);
        } else if (hasAggregates) {
            groupByEvaluator.validateImplicitSelectList(ctx, table, aliasToTable, allTables);
        } else {
            validateWindowedSelectArguments(ctx, table, aliasToTable, allTables);
        }
        if (ctx.havingClause() != null) {
            validateClauseScope(getOriginalText(ctx.havingClause().booleanExpr()),
                table, aliasToTable, allTables, selectItemAliasNames(ctx), ctx.havingClause().booleanExpr());
            validateClausePredicateType(getOriginalText(ctx.havingClause().booleanExpr()),
                table, aliasToTable, allTables, ctx, ctx.havingClause().booleanExpr());
        }
        if (subqueryRefusal != null) {
            throw subqueryRefusal;
        }
        rejectQualifyWithoutWindow(ctx);
    }

    boolean hasAggregateFunctionInExpression(final FrostlakeParser.ExpressionContext expr) {
        return windowEvaluator.hasAggregateFunctionInExpression(expr);
    }

    /**
     * Whether a window call belongs to THIS query rather than to a subquery inside one of its items.
     * The ordinary detector counts a call anywhere in a select item, which is right for deciding
     * whether the window stage runs at all — but wrong for deciding whether a FROM-LESS query needs a
     * synthesised row: {@code SELECT (SELECT LAG(a) OVER () FROM t) = (…)} has no window of its own,
     * and giving it one made the inner call's columns resolve against the empty synthesised source.
     *
     * @param ctx the select clause
     * @return whether this level writes a window call
     */
    private boolean hasWindowCallAtThisLevel(final FrostlakeParser.SelectClauseContext ctx) {
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (!SelectItemAccessors.isExprItem(item)) {
                continue;
            }
            final FrostlakeParser.ExpressionContext expr = SelectItemAccessors.getItemValueExpr(item);
            if (expr == null) {
                continue;
            }
            final List<FrostlakeParser.FunctionCallExprContext> calls = new ArrayList<>();
            windowEvaluator.collectWindowFunctionCalls(expr, calls);
            for (final FrostlakeParser.FunctionCallExprContext call : calls) {
                // The ITEM is the limit, not the expression: the accessor may hand back a node the call
                // does not actually descend from, and a walk that never meets its limit runs on to the
                // statement itself and calls every window nested.
                if (!insideNestedSelect(call, item)) {
                    return true;
                }
            }
            // A call written with named arguments and OVER is judged with the rest of the statement's
            // OVER calls, which only the synthesised row's route runs.
            if (OverCallRules.writesOverCall(expr)) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code node} sits inside a SELECT of its own, somewhere below {@code limit}. */
    private boolean insideNestedSelect(final ParserRuleContext node, final ParserRuleContext limit) {
        ParserRuleContext parent = node.getParent();
        while (parent != null && parent != limit) {
            if (parent instanceof FrostlakeParser.SelectStatementContext) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
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
     *
     * <p>Each non-window item carries the STATIC type the projection infers for it, marked trusted, so
     * a window argument that names a projected item — an aliased aggregate, a group key that is also
     * selected — is typed exactly as the item is. A window item itself stays a placeholder: its type is
     * what the window stage is computing. A base column the SELECT list never projects is resolved
     * through the grouped resolver's base relation instead, so both routes read the same declarations.
     */
    /**
     * Types every aggregate select item against the base relation and lets a compilation error through.
     * An ordinary undetermined type is nothing; a declared width that is no NUMBER at all is a refusal
     * live raises before it computes anything, which is where it has to be raised here too — the
     * grouped evaluator would otherwise reach the value first and refuse THAT.
     *
     * <p>Window items are left to the window stage's own walk.
     *
     * @param ctx          the select clause
     * @param base         the relation the items read
     * @param aliasToTable the joined relations by alias
     * @param allTables    the joined relations in order
     */
    private void rejectUntypeableAggregateItems(final FrostlakeParser.SelectClauseContext ctx,
                                                final Table base,
                                                final Map<String, Table> aliasToTable,
                                                final List<Table> allTables) {
        ExpressionEvaluator itemTypes = null;
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (!SelectItemAccessors.isExprItem(item)) {
                continue;
            }
            final FrostlakeParser.ExpressionContext expr = SelectItemAccessors.getItemValueExpr(item);
            if (expr == null || !hasAggregateFunctionInExpression(expr)
                    || hasWindowFunctionInExpression(expr)) {
                continue;
            }
            if (itemTypes == null) {
                itemTypes = new ExpressionEvaluator(base, functionRegistry, catalog, this);
                itemTypes.setMultiTableContext(aliasToTable, allTables);
            }
            final ParserRuleContext origin = SelectItemAccessors.getItemExpression(item);
            staticProjectionType(getOriginalText(expr), itemTypes, origin);
            // The argument-family refusals too, from the same declared types and at the item's own
            // place — so SUM(bo) over an EMPTY table is refused at compile time, as live refuses it.
            final SourcePosition displaced = ExpressionSource.beginNested(origin == null ? null
                : new SourcePosition(origin.getStart().getLine(), origin.getStart().getCharPositionInLine()));
            try {
                itemTypes.rejectArgumentFamilies(ExpressionEvaluator.parse(getOriginalText(expr)));
            } catch (final RuntimeException refused) {
                if (SqlCompilationError.isCompilationError(refused.getMessage())) {
                    throw refused;
                }
            } finally {
                ExpressionSource.end(displaced);
            }
        }
    }

    private Table projectedShapeTable(final FrostlakeParser.SelectClauseContext ctx, final Table base,
                                      final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final ExpressionEvaluator itemTypes = new ExpressionEvaluator(base, functionRegistry, catalog, this);
        itemTypes.setMultiTableContext(aliasToTable, allTables);
        final List<TableColumn> columns = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (SelectItemAccessors.isStarItem(item)
                    || SelectItemAccessors.isQualifiedStarItem(item)) {
                columns.addAll(base.getColumns());
                continue;
            }
            if (SelectItemAccessors.isObjectStarItem(item)) {
                // The braced star occupies ONE output slot (the row object), not the base columns.
                columns.add(new TableColumn(SelectItemAccessors.objectStarLabel(item),
                    ObjectType.OBJECT, true, null, false, false, false));
                continue;
            }
            final String name = SelectItemAccessors.getItemAlias(item) != null
                ? (SelectItemAccessors.getItemAlias(item))
                : unaliasedItemName(item);
            DataType known = null;
            if (SelectItemAccessors.isExprItem(item)
                    && !hasWindowFunctionInExpression(SelectItemAccessors.getItemValueExpr(item))) {
                try {
                    known = staticProjectionType(
                        getOriginalText(SelectItemAccessors.getItemValueExpr(item)), itemTypes, null);
                } catch (final RuntimeException undetermined) {
                    known = null;
                }
            }
            final TableColumn column = new TableColumn(name, known != null ? known : StringType.VARCHAR,
                true, null, false, false, false);
            column.setStaticallyTyped(known != null);
            if (known != null) {
                column.setValueRange(staticProjectionRange(
                    getOriginalText(SelectItemAccessors.getItemValueExpr(item)), itemTypes));
            }
            columns.add(column);
        }
        return new Table("__WINDOW_PROJECTED__", columns, false);
    }

    private List<Row> addWindowFunctionsToRows(final List<Row> rows,
                                                final Map<Integer, Map<Integer, Object>> windowFunctionResults,
                                                final FrostlakeParser.SelectClauseContext ctx,
                                                final Table table,
                                                final Table baseTable,
                                                final boolean rowsAreProjected,
                                                final List<String> extraOrderKeyExprs,
                                                final List<FrostlakeParser.ExpressionContext> extraOrderKeyTrees,
                                                final Map<Row, Object[]> extraKeyValuesOut) {
        return windowEvaluator.addWindowFunctionsToRows(rows, windowFunctionResults, ctx, table, baseTable,
            rowsAreProjected, extraOrderKeyExprs, extraOrderKeyTrees, extraKeyValuesOut);
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
        final List<Row> projectedRows = new ArrayList<>();
        for (final Row row : rows) {
            final List<Object> projectedValues = new ArrayList<>();
            for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
                if (!SelectItemAccessors.isExprItem(item)) continue;
                final String exprText = getOriginalText(SelectItemAccessors.getItemExpression(item));
                final Object value;

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


    private List<Row> orderByAfterGroupBy(final List<Row> rows, final FrostlakeParser.SelectStatementContext ctx,
                                          final GroupOrderKeyResolver resolver, final Table table,
                                          final Map<String, Table> aliasToTable) {
        return orderByExecutor.orderByAfterGroupBy(rows, ctx, resolver, table, aliasToTable);
    }

    /**
     * A resolver that computes an ORDER BY key not present in the SELECT list (a grouped column or an
     * aggregate) over the source group of each output row, keyed by the row's original pre-sort index.
     */
    /**
     * How a window stage over GROUPED rows values an expression that the projection does not carry: over
     * the row's own source group, exactly as a grouped ORDER BY key is valued. Answers UNRESOLVED for a
     * row with no group and for text the group cannot compute, so the caller keeps its ordinary path.
     *
     * @param rowToGroup  output row to its source rows, identity-keyed
     * @param table       the base relation
     * @param aliasToTable the FROM shape, for a multi-relation scope
     * @param allTables   every relation in scope
     * @return the resolver
     */
    private GroupedExpressionValues newGroupedExpressionValues(final Map<Row, List<Row>> rowToGroup,
            final Table table, final Map<String, Table> aliasToTable, final List<Table> allTables) {
        return new GroupedExpressionValues() {
            @Override
            public Table baseTable() {
                return table;
            }

            @Override
            public Map<String, Table> aliasToTable() {
                return aliasToTable;
            }

            @Override
            public List<Table> allTables() {
                return allTables;
            }

            @Override
            public Object valueOf(final Row projectedRow, final String expressionText) {
                final List<Row> group = rowToGroup.get(projectedRow);
                if (group == null || group.isEmpty()) {
                    return GroupedExpressionValues.UNRESOLVED;
                }
                try {
                    final FrostlakeParser.ExpressionContext expr = expressionContextIn(
                        AntlrExpressionParser.parseTree(expressionText));
                    if (expr == null) {
                        return GroupedExpressionValues.UNRESOLVED;
                    }
                    return groupByEvaluator.evaluateOverGroup(expr, group, table,
                        aliasToTable, allTables, new HashMap<String, Object>());
                } catch (final RuntimeException notComputableOverTheGroup) {
                    return GroupedExpressionValues.UNRESOLVED;
                }
            }
        };
    }

    /**
     * The first EXPRESSION node in a parse tree — the way back from an expression's TEXT to the tree
     * the grouped evaluator reads. An expression string parses as a boolean expression, whose first
     * expression descendant is the expression itself.
     *
     * @param node the parsed tree
     * @return the expression node, or null when the tree holds none
     */
    private FrostlakeParser.ExpressionContext expressionContextIn(final ParseTree node) {
        if (node == null) {
            return null;
        }
        if (node instanceof FrostlakeParser.ExpressionContext) {
            return (FrostlakeParser.ExpressionContext) node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final FrostlakeParser.ExpressionContext found = expressionContextIn(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private GroupOrderKeyResolver newGroupOrderResolver(final List<Row> outputRows,
            final Map<Row, List<Row>> rowToGroup, final Table table,
            final Map<String, Table> aliasToTable, final List<Table> allTables,
            final FrostlakeParser.SelectClauseContext selectCtx) {
        // Output alias → SELECT column index, so a key referencing an aggregate's alias (ORDER BY
        // m + 1 beside max(v) AS m) reads the already-computed value off the output row.
        final Map<String, Integer> aliasIndex = new HashMap<>();
        final List<FrostlakeParser.SelectItemContext> selectItems = selectCtx.selectList().selectItem();
        for (int i = 0; i < selectItems.size(); i++) {
            final String alias = SelectItemAccessors.getItemAlias(selectItems.get(i));
            if (alias != null) {
                aliasIndex.put(alias.toUpperCase(), i);
            }
        }
        return new GroupOrderKeyResolver() {
            @Override
            public Object resolve(final int rowIndex, final FrostlakeParser.OrderItemContext item) {
                final Row outputRow = outputRows.get(rowIndex);
                final List<Row> group = rowToGroup.get(outputRow);
                if (group == null || group.isEmpty()) {
                    return null;
                }
                final Map<String, Object> outputAliasValues = new HashMap<>();
                for (final Map.Entry<String, Integer> entry : aliasIndex.entrySet()) {
                    if (entry.getValue() < outputRow.getValues().size()) {
                        outputAliasValues.put(entry.getKey(), outputRow.getValue(entry.getValue()));
                    }
                }
                return groupByEvaluator.evaluateOverGroup(item.expression(), group, table, aliasToTable,
                    allTables, outputAliasValues);
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
     * An UPDATE's SET value over the row being updated, typed like a projection under the target's
     * ALIAS when one was written — {@code UPDATE upd u SET d = COALESCE(u.va, u.d)} folds and casts
     * exactly as the bare form does, so the refusal is the cast's own sentence.
     */
    Object evaluateUpdateValue(final String expr, final Row row, final Table table, final String alias) {
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(table, functionRegistry, catalog, this);
        final Map<String, Table> aliasToTable = new HashMap<>();
        aliasToTable.put((alias != null ? alias : table.getName()).toUpperCase(), table);
        final List<Table> relations = new ArrayList<>();
        relations.add(table);
        evaluator.setDeclaredTypeBase(table, aliasToTable, relations);
        return evaluator.evaluate(expr, row);
    }

    /**
     * As {@link #evaluateExpression(String, Row, Table)}, additionally offering {@code lateralAliases} — the
     * SELECT list's already-computed column aliases, keyed by uppercased alias. They are consulted only AFTER
     * the row's real columns, so a column of the same name keeps precedence.
     */
    Object evaluateExpression(final String expr, final Row row, final Table table,
                             final Map<String, Object> lateralAliases) {
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(table, functionRegistry, catalog, this);
        evaluator.setOuterLateralContext(lateralAliases);
        // When a window function / QUALIFY is computing over the rows of a JOIN, resolve alias-qualified
        // columns (e.g. o.region) with that join's alias context — otherwise they resolve to NULL
        // against the single merged table and every row collapses into one partition.
        final Deque<Map<String, Table>> aliasStack = windowAliasToTableStack.get();
        if (!aliasStack.isEmpty()) {
            evaluator.setMultiTableContext(aliasStack.peek(), windowAllTablesStack.get().peek());
            evaluator.setDeclaredTypeBase(table, aliasStack.peek(), windowAllTablesStack.get().peek());
        } else {
            // The SET value of an UPDATE is typed like a projection: a conditional over a VARIANT and
            // a DATE folds to DATE and CASTS its branch, so the refusal is the cast's own sentence —
            // live's "Failed to cast variant value 1 to DATE" — rather than the write's.
            evaluator.setDeclaredTypeBase(table, null, null);
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

    /** @see SQLCommandVisitor#executeScriptingFunctionBody(Function, List) */
    public Object executeScriptingFunctionBody(final Function function, final List<Object> args) {
        return visitor.executeScriptingFunctionBody(function, args);
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
     * The PIVOT stage of {@code FROM t PIVOT (…) [AS p (c1, c2)]} and the relation it answers, named after
     * the alias when one is written; an IN (ANY) list reads its values off {@code rowsForAny}.
     */
    private PlannedRelation planPivot(final Table table, final FrostlakeParser.PivotClauseContext pivotCtx,
                                      final FrostlakeParser.PivotAliasContext aliasCtx, final String relationName,
                                      final List<Row> rowsForAny) {
        return pivotExecutor.planPivot(table, pivotCtx, rowsForAny,
            aliasCtx == null ? null : columnAliasList(aliasCtx.identifierList()), relationName);
    }

    /** The UNPIVOT stage of {@code FROM t UNPIVOT (…) [AS u (c1, c2)]} and the relation it answers. */
    private PlannedRelation planUnpivot(final Table table, final FrostlakeParser.UnpivotClauseContext unpivotCtx,
                                        final FrostlakeParser.PivotAliasContext aliasCtx, final String relationName) {
        return pivotExecutor.planUnpivot(table, unpivotCtx,
            aliasCtx == null ? null : columnAliasList(aliasCtx.identifierList()), relationName);
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

    /** A tableSource that is a parenthesized group {@code ( tableReference joinClause* )} — the only tableSource
     *  alternative that carries a child tableReference. */
    private boolean isParenthesizedJoin(final FrostlakeParser.TableSourceContext src) {
        return src != null && src.tableReference() != null;
    }

    /**
     * A parenthesized join group — {@code ( a JOIN b ON ... )} — planned as a pipeline of its own, whose rows the
     * join it is the right side of reads when it runs. The inner chain's tables and aliases are registered into
     * the CALLER's scope (so an outer ON condition can reference them); the group's combined shape is the inner
     * tables in join order, matching the per-table offsets the qualified-column resolver derives from
     * {@code allTables}.
     */
    private PlannedJoinGroup planJoinGroup(final FrostlakeParser.TableSourceContext src,
                                           final Map<String, Table> aliasToTable, final List<Table> allTables,
                                           final FrostlakeParser.SelectClauseContext ctx,
                                           final Map<String, ResultSet> cteResults,
                                           final JoinConditionCompilation onCompile) {
        final List<FrostlakeParser.TableReferenceContext> innerRefs = new ArrayList<>();
        final List<FrostlakeParser.JoinClauseContext> innerJoins = new ArrayList<>();
        flattenParenthesizedJoin(src.tableReference(), innerRefs, innerJoins);
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
        groupAliases.put(firstData.alias != null ? firstData.alias : groupTable.getName(), groupTable);
        groupTables.add(groupTable);
        final SelectPlan groupPlan = new SelectPlan(this, functionRegistry);
        groupPlan.sourceStage(firstData.sourceStage());
        int refIndex = 1;
        for (final FrostlakeParser.JoinClauseContext join : innerJoins) {
            final TableData rightData = executeTableReference(innerRefs.get(refIndex), null, cteResults);
            refIndex++;
            final Table rightJoinTable = distinctJoinTable(rightData.table, groupTables);
            groupAliases.put(rightData.alias != null ? rightData.alias : rightJoinTable.getName(), rightJoinTable);
            groupTables.add(rightJoinTable);
            groupPlan.add(planJoin(groupTable, rightData.source, rightJoinTable, join,
                groupAliases, groupTables, ctx, null, onCompile));
            final Table groupLeft = groupTable;
            final Set<String> innerKeys = usingJoinColumnNames(join, groupTable, rightJoinTable);
            groupTable = mergeTableMetadata(groupTable, rightJoinTable, innerKeys);
            recordNullExtension(groupTable, groupLeft, rightJoinTable, joinTypeOf(join));
            // A group holds no comma, so a USING join inside it merges everything joined before it.
            new FromItemRelations(null).mergeScopes(groupTable, groupLeft, rightJoinTable, join, joinTypeOf(join),
                innerKeys, groupTables);
        }
        // Now expose the group's tables/aliases to the caller — the outer ON references them, and the
        // outer combined-row layout is (left tables..., group tables in join order).
        aliasToTable.putAll(groupAliases);
        allTables.addAll(groupTables);
        final RowsProvider groupRows = new RowsProvider() {
            private List<Row> rows;

            @Override
            public List<Row> rows() {
                if (rows == null) {
                    rows = groupPlan.execute();
                }
                return rows;
            }

            @Override
            public String describe() {
                return groupPlan.describeStages();
            }
        };
        return new PlannedJoinGroup(groupRows, groupTable);
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
            flattenParenthesizedJoin(src.tableReference(), outRefs, outJoins);
            outJoins.addAll(src.joinClause());
        } else {
            outRefs.add(ref);
        }
    }

    /**
     * The outer names a query's FROM source resolves with. A table function's arguments may read the row of
     * the query around a subquery, as live's {@code (SELECT COUNT(*) FROM TABLE(FLATTEN(input => t.arr)))}
     * does, and so may a derived table's body: {@code (SELECT MAX(v) FROM (SELECT v FROM g WHERE g.id = fz.id))}
     * (live-verified). A catalog relation has no names to resolve.
     */
    private static Map<String, Object> sourceContext(final FrostlakeParser.TableReferenceContext ref,
                                                     final Map<String, Object> lateralContext) {
        if (lateralContext == null || ref.tableSource() == null) {
            return null;
        }
        final FrostlakeParser.TableSourceContext source = ref.tableSource();
        final boolean tableFunction = source.FLATTEN() != null || source.tableFunctionExpr() != null
            || source.TABLE() != null && source.expression() != null;
        return tableFunction || source.selectStatement() != null ? lateralContext : null;
    }

    /**
     * {@link #planTableReference}, with the derived-column alias list applied and, read for its shape only,
     * without its rows.
     */
    TableData plannedReference(final FrostlakeParser.TableReferenceContext ctx,
                               final Map<String, Object> lateralContext,
                               final Map<String, ResultSet> cteResults) {
        TableData resolved = planTableReference(ctx, lateralContext, cteResults);
        // An ANSI derived-column alias list renames the source's output columns: FROM t AS d (a, b). It was
        // parsed but only honoured for a few source kinds (subquery / VALUES / table function), so over a base
        // table, view or stream the grammar accepted it and the query then failed on the renamed columns.
        // Re-applying it to a source that already handled it is harmless (same names, same positions).
        final List<String> referenceAliases = columnAliasList(ctx.identifierList());
        if (referenceAliases != null) {
            resolved = resolved.withTable(applyColumnAliases(resolved.table, referenceAliases));
        }
        if (RelationShapeOnly.isActive()) {
            // The source's columns without its rows — see RelationShapeOnly.
            resolved = resolved.withoutRows();
        }
        return resolved;
    }

    /**
     * A FROM source planned (see {@link #planTableReference}), its trailing SAMPLE / TABLESAMPLE clause
     * applied to the rows as they are produced.
     */
    TableData executeTableReference(final FrostlakeParser.TableReferenceContext ctx, final Map<String, Object> lateralContext, final Map<String, ResultSet> cteResults) {
        return sampled(plannedReference(ctx, lateralContext, cteResults), ctx.sampleClause());
    }

    /** The source with its SAMPLE / TABLESAMPLE clause applied as its rows are produced; itself without one. */
    private TableData sampled(final TableData source, final FrostlakeParser.SampleClauseContext sample) {
        if (sample == null) {
            return source;
        }
        return new TableData(source.table, new RowsProvider() {
            @Override
            public List<Row> rows() {
                return sampleRows(source.source.rows(), sample);
            }

            @Override
            public String describe() {
                return null;
            }
        }, source.alias, source.describe() + " -> SAMPLE[" + getOriginalText(sample) + "]");
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
                ? securityManager.getSessionContext().getSessionVariable(varName)
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

    /**
     * LATERAL takes the BARE call, never a {@code TABLE()} wrapper. The two are alternatives, not a
     * pair that composes: {@code FROM t, LATERAL FLATTEN(…)} and {@code FROM t, TABLE(FLATTEN(…))}
     * both run, while {@code LATERAL TABLE(FLATTEN(…))} is a SYNTAX error that gets no further than
     * the keyword — in a comma list and after {@code CROSS JOIN} alike. Frostlake accepted all three,
     * so a whole test class was written in a spelling no account parses.
     *
     * <p>A syntax error spells its position inside the detail rather than on the first line, which is
     * why this does not use the positioned {@code at(...)} form.
     */
    /**
     * A table function's result columns are named by its {@code RETURNS TABLE} declaration, not by
     * whatever its body happened to call them: {@code RETURNS TABLE(msg VARCHAR)} answers a column
     * MSG, and {@code SELECT msg FROM TABLE(greet())} resolves. The body's own names were coming
     * through instead — a body with no aliases produced COLUMN1, which nothing could select by name.
     *
     * <p>Only the declared columns are renamed, positionally; a body that produces a different number
     * is left alone rather than truncated.
     */
    private ResultSet nameByDeclaredReturnColumns(final Function udtf, final ResultSet result) {
        final List<Parameter> declared = udtf.getReturnColumns();
        if (declared == null || declared.isEmpty() || result == null
                || declared.size() != result.getColumns().size()) {
            return result;
        }
        final List<ResultSetColumn> renamed = new ArrayList<>();
        for (int i = 0; i < declared.size(); i++) {
            final ResultSetColumn produced = result.getColumns().get(i);
            renamed.add(new ResultSetColumn(SqlIdentifiers.canonicalText(declared.get(i).getName()),
                produced.getDataType()));
        }
        return new ResultSet(renamed, result.getRows());
    }

    /** Whether the JOIN that owns this reference supplied the LATERAL keyword rather than the reference. */
    private boolean lateralFromEnclosingJoin(final FrostlakeParser.TableReferenceContext ctx) {
        return ctx.getParent() instanceof FrostlakeParser.JoinClauseContext
            && ((FrostlakeParser.JoinClauseContext) ctx.getParent()).LATERAL() != null;
    }

    private void rejectLateralTableWrapper(final boolean lateral,
                                           final FrostlakeParser.TableSourceContext source) {
        if (!lateral || source == null || source.TABLE() == null) {
            return;
        }
        final Token tableToken = source.TABLE().getSymbol();
        throw new RuntimeException(SqlCompilationError.of("syntax error line " + tableToken.getLine()
            + " at position " + tableToken.getCharPositionInLine() + " unexpected 'TABLE'."));
    }

    /**
     * Whether the query block reading a relation keeps no row, whatever the relation holds: a WHERE with a
     * constant conjunct that is FALSE or NULL, or a LIMIT 0. Live never produces such a relation's rows, so
     * a value that would fault in one is never computed (live-verified: {@code SELECT c FROM v WHERE 1=0}
     * answers no row over a view whose column c faults).
     */
    private boolean keepsNoRow(final ParserRuleContext reference) {
        if (UnreadColumnPruning.limitedToNothing(reference)) {
            return true;
        }
        for (final ParserRuleContext conjunct : UnreadColumnPruning.constantConjuncts(reference)) {
            try {
                final Object value = new ExpressionEvaluator(null, functionRegistry, catalog, this)
                    .evaluate(getOriginalText(conjunct), null);
                if (value == null || Boolean.FALSE.equals(value)) {
                    return true;
                }
            } catch (final RuntimeException notConstant) {
                // decided per row after all, as written
            }
        }
        return false;
    }

    /** A view's relation with its columns and no row, from the shape resolved when it was created. */
    private TableData emptyView(final View view, final String tableName, final String alias) {
        final List<ResultSetColumn> columns = new ArrayList<>();
        for (final TableColumn column : view.getResolvedColumns()) {
            columns.add(new ResultSetColumn(column.getName(), column.getDataType()));
        }
        final Table virtualTable = resultSetToTable(new ResultSet(columns, new ArrayList<>()), tableName);
        stampViewRowAccessPolicy(view, virtualTable);
        return new TableData(virtualTable, new ArrayList<>(), alias);
    }

    /**
     * A view's body as {@code reference} reads it: every select item the reading statement never reads is
     * blanked to NULL first (see UnreadColumnPruning), so a value that would fault in it is never computed.
     */
    private String readDefinition(final View view, final ParserRuleContext reference) {
        final String definition = view.getDefinition();
        if (view.hasRowAccessPolicy()) {
            return definition;   // the policy reads its own columns of every row
        }
        final Set<String> read = UnreadColumnPruning.namesRead(reference);
        if (read == null || readsEveryColumn(view, read)) {
            return definition;
        }
        try {
            final String pruned = UnreadColumnPruning.pruned(definition, parseSelectStatement(definition),
                view.hasExplicitColumnNames() ? view.getColumnNames() : null, read, functionRegistry);
            return pruned != null ? pruned : definition;
        } catch (final RuntimeException unreadable) {
            return definition;   // a body this cannot take apart runs, and refuses, as written
        }
    }

    /**
     * A derived table's or a CTE's body with every select item the statement never reads blanked, as a
     * view's are (see UnreadColumnPruning), or null when none can be. Live plans such a body into the query
     * that reads it, so an unread item is never computed and a value it would fault on never produced; but
     * the body still COMPILES whole, so before the blanked text runs, the body as written is run once for
     * its shape alone (see RelationShapeOnly), which raises an unknown name or function where it stands.
     * The blanked text is remembered per body, so a body run again for each outer row is decided once.
     *
     * @param body       the body as written
     * @param reference  where the statement names the relation
     * @param names      the relation's column names when a list renames the body's items, else null
     * @param lateral    the enclosing row's context, or null
     * @param cteResults the CTEs in scope for a CTE's body, or null for a derived table
     */
    private String prunedBody(final FrostlakeParser.SelectStatementContext body, final ParserRuleContext reference,
                              final List<String> names, final Map<String, Object> lateral,
                              final Map<String, ResultSet> cteResults) {
        // A run that judges no names — one for a row of the query around it, or one planning a subquery's shape
        // outside its statement's compilation — blanks a body without compiling it, so its decision is kept
        // apart: reused, a run that does judge names would never meet the unknown name a blanked item hid.
        final boolean judged = lateral == null || SubqueryCompilation.compiles(lateral);
        String known = prunedBodies.get(body);
        if (known == null && !judged) {
            known = unjudgedPrunedBodies.get(body);
        }
        if (known != null) {
            return known.isEmpty() ? null : known;
        }
        String pruned = null;
        final Set<String> read = UnreadColumnPruning.namesRead(reference, body);
        if (read != null) {
            final String text = getOriginalText(body);
            try {
                pruned = UnreadColumnPruning.pruned(text, parseSelectStatement(text), names, read, functionRegistry);
            } catch (final RuntimeException unreadable) {
                pruned = null;   // a body this cannot take apart runs as written
            }
        }
        if (pruned != null) {
            final boolean previous = RelationShapeOnly.begin();
            try {
                if (cteResults != null) {
                    executeSelectFromContextWithCTEs(body, lateral, cteResults);
                } else {
                    executeSelectFromContext(body, lateral);
                }
            } catch (final RuntimeException uncompilable) {
                // A body this cannot compile on its own — one reading the row beside it — keeps every item:
                // whatever it raises is raised where it runs, as it was before any item was blanked.
                pruned = null;
            } finally {
                RelationShapeOnly.end(previous);
            }
        }
        (judged ? prunedBodies : unjudgedPrunedBodies).put(body, pruned != null ? pruned : "");
        return pruned;
    }

    /** A CTE's column list, canonical as a written name is, or null when its items name its columns. */
    private List<String> cteColumnNames(final FrostlakeParser.CteDefinitionContext cte) {
        if (cte.columnListOptional() == null) {
            return null;
        }
        final List<String> names = new ArrayList<>();
        for (final FrostlakeParser.NamePartContext part : cte.columnListOptional().namePart()) {
            names.add(ParseTreeText.namePartText(part));
        }
        return names;
    }

    private static boolean readsEveryColumn(final View view, final Set<String> read) {
        if (!view.hasResolvedColumns()) {
            return false;
        }
        for (final TableColumn column : view.getResolvedColumns()) {
            if (!read.contains(column.getName().toUpperCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    /**
     * A FROM source planned: its shape — the relation's columns and the name it answers to — is settled here,
     * from the catalog, from the function called or from the plan of the body read, and its rows are produced
     * by the plan's source stage when the pipeline runs: a table's scan, snapshot or change set, a stream's
     * unconsumed records, a table function's run, a derived table's, a view's or a dynamic table's own plan. A
     * relation that is its own rows — VALUES, DUAL, a CTE the WITH clause computed, a prior flow-chain stage —
     * is in hand. A stage's files, a system view and a stream over a view are still read here, and the plan
     * says so.
     *
     * @param ctx            the reference
     * @param lateralContext the outer row's names a correlated source resolves with, or null
     * @param cteResults     the CTEs in scope, or null
     * @return the planned source
     */
    private TableData planTableReference(final FrostlakeParser.TableReferenceContext ctx,
                                         final Map<String, Object> lateralContext,
                                         final Map<String, ResultSet> cteResults) {
        final FrostlakeParser.TableSourceContext source = ctx.tableSource();
        // A bare table function is only a FROM source under LATERAL — `FROM t, LATERAL SPLIT_TO_TABLE(...)`.
        // Without LATERAL, Snowflake rejects it; only TABLE(function(...)) is valid there. Refusing it
        // here keeps Frostlake from accepting SQL live turns away.
        // LATERAL may be written on the reference itself (FROM t, LATERAL f(...)) or supplied by the
        // enclosing join (FROM t CROSS JOIN LATERAL f(...)); both spellings run on live.
        final boolean lateral = ctx.LATERAL() != null || lateralFromEnclosingJoin(ctx);
        if (source.tableFunctionExpr() != null && !lateral) {
            throw new RuntimeException(SqlCompilationError.of(
                "Unsupported table function reference: a table function used directly in FROM requires"
                + " LATERAL, or wrap it as TABLE(" + source.tableFunctionExpr().functionName().getText()
                + "(...))"));
        }
        rejectLateralTableWrapper(lateral, source);
        // The alias by its canonical name: a quoted one exactly as quoted, an unquoted one upper-cased, so a
        // qualifier naming it resolves by that name alone (live-verified: "fz".* reads FZ AS "fz", fz.* does not).
        String alias = sourceAlias(ctx);

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
        if (source.identifierArgument() != null) {
            rejectUnboundObjectBind(source.identifierArgument(), ctx.aliasName() != null
                ? ParseTreeText.getIdentifier(ctx.aliasName())
                : ctx.nonJoinKeywordIdentifier() != null && ctx.nonJoinKeywordIdentifier().identifier() != null
                    ? ParseTreeText.getIdentifier(ctx.nonJoinKeywordIdentifier().identifier())
                    : ctx.nonJoinKeywordIdentifier() != null
                        ? ctx.nonJoinKeywordIdentifier().getText().toUpperCase(Locale.ROOT) : null, false);
            final ExpressionEvaluator eval = new ExpressionEvaluator(null, functionRegistry, catalog, this);
            final Object nameVal = eval.evaluate(getOriginalText(source.identifierArgument()), null);
            if (nameVal == null) {
                throw new RuntimeException("IDENTIFIER() expression evaluated to null");
            }
            rejectInvalidIdentifierReference(nameVal.toString(), source.identifierArgument());
            final String dynamicTableName = SqlIdentifiers.identifierReferenceText(nameVal.toString().trim());
            // IDENTIFIER() can name a view / materialized view / dynamic table / stream, not only a base
            // table — resolve it the same way a direct FROM reference does. A common pattern is
            // `FROM IDENTIFIER(:src)` where :src toggles between a view or base table (backfill) and a
            // stream (incremental).
            final TableData identifierSource = planNamedSource(dynamicTableName, alias);
            if (identifierSource != null) {
                return identifierSource;
            }
            // A miss reads as a FROM clause's does: Object 'NOSUCH' as written when bare, expanded in full
            // once qualified (live-verified, the same sentences a plain FROM earns).
            final Table dynamicTable = catalog.resolveTableAsWritten(dynamicTableName, "Object");
            // Overlay-aware read: a loader fills IDENTIFIER(:tmp) and joins it ONE statement later
            // inside the same explicit transaction — a raw scan() was blind to the buffered rows, so
            // the join silently saw an empty table.
            return new TableData(dynamicTable, transactionScan(getFullyQualifiedTableName(dynamicTableName)),
                alias != null ? alias : dynamicTableName, "SCAN[" + dynamicTable.getName() + "]");
        }

        // Handle FLATTEN(...) shorthand
        if (source.FLATTEN() != null && source.flattenArgList() != null) {
            final Map<String, Object> namedArgs = new HashMap<>();
            final FrostlakeParser.FlattenArgListContext fal = source.flattenArgList();
            if (fal.namedArgumentList() != null) {
                // All named: FLATTEN(INPUT => expr, OUTER => true, ...)
                validateTableFunctionCall("FLATTEN", "", source.FLATTEN().getSymbol(),
                    null, fal.namedArgumentList().namedArgument());
                for (final FrostlakeParser.NamedArgumentContext argCtx : fal.namedArgumentList().namedArgument()) {
                    final String argName = argCtx.identifier().getText().toUpperCase();
                    final Object argValue = evaluateNamedTableFunctionArgument(argCtx, lateralContext);
                    namedArgs.put(argName, argValue);
                }
            } else {
                // Mixed: first positional arg = INPUT, then optional named args
                validateTableFunctionCall("FLATTEN", "FLATTEN", source.FLATTEN().getSymbol(),
                    Arrays.asList(fal.expression()), fal.namedArgument());
                final Object inputValue = evaluateTableFunctionArgument(fal.expression(),
                    getOriginalText(fal.expression()), lateralContext);
                namedArgs.put("INPUT", inputValue);
                for (final FrostlakeParser.NamedArgumentContext argCtx : fal.namedArgument()) {
                    final String argName = argCtx.identifier().getText().toUpperCase();
                    final Object argValue = evaluateNamedTableFunctionArgument(argCtx, lateralContext);
                    namedArgs.put(argName, argValue);
                }
            }
            final TableFunction flattenFunc = functionRegistry.getTableFunction("FLATTEN");
            if (flattenFunc == null) throw new RuntimeException("FLATTEN function not available");
            return tableFunctionSource(plannedCall("FLATTEN", getOriginalText(source), flattenFunc, namedArgs),
                alias, "flatten", columnAliases);
        }

        // Handle a bare table function used directly as a FROM source (no TABLE() wrapper):
        // LATERAL SPLIT_TO_TABLE(x, ','). Same machinery as TABLE(function_call).
        if (source.tableFunctionExpr() != null) {
            return tableFunctionSource(planBareTableFunction(source.tableFunctionExpr(), lateralContext),
                alias, "table_function", columnAliases);
        }

        // Handle TABLE(function_call) - table function
        if (source.TABLE() != null && source.expression() != null) {
            // TABLE(<value>) is a table literal, read exactly as IDENTIFIER(<value>) reads it.
            final TableData literal = tableLiteralSource(source.expression(), alias);
            if (literal != null) {
                return literal;
            }
            return tableFunctionSource(planTableFunction(source.expression(), lateralContext), alias,
                "table_function", columnAliases);
        }

        // Handle subquery
        if (source.selectStatement() != null) {
            final String prunedBody = lateralContext == null && !lateral
                ? prunedBody(source.selectStatement(), ctx, columnAliases, lateralContext, null) : null;
            final PlannedQuery body;
            // The body's items wait for the reading statement (see RelationBody).
            final boolean outerBody = RelationBody.begin();
            try {
                body = prunedBody != null
                    ? planSelectFromContext(parseSelectStatement(prunedBody), lateralContext, null)
                    : planSelectFromContext(source.selectStatement(), lateralContext, null);
            } finally {
                RelationBody.end(outerBody);
            }
            // Convert the planned shape to a Table; the rows come from the body's plan when the source runs
            final String name = alias != null ? alias : "values";
            Table virtualTable = resultSetToTable(body.shape, name);

            // Apply column aliases if provided
            if (columnAliases != null) {
                virtualTable = applyColumnAliases(virtualTable, columnAliases);
            }

            return new TableData(virtualTable, relationBodyRows(body), alias,
                "SUBQUERY[" + name + "]{" + body.description + "}");
        }

        // $n — a prior flow-chain stage's result (n statements back from the current stage).
        if (source.POSITIONAL_PARAMETER() != null) {
            final int back = Integer.parseInt(source.POSITIONAL_PARAMETER().getText().substring(1));
            if (flowChainResults == null) {
                // Live's own message embeds a dump of its internal AST node here
                // ("invalid pipe reference 'SqlNamedExpression{ aliasName=<null>, expression=1}'"),
                // which is a leak of Snowflake's internals rather than a shape worth reproducing.
                // The phrase it leads with is kept; the reference is named as the user wrote it.
                throw new RuntimeException(SqlCompilationError.at(1,
                    source.POSITIONAL_PARAMETER().getSymbol().getCharPositionInLine(),
                    "invalid pipe reference '$" + back + "'"));
            }
            if (back < 1 || back > flowChainResults.size()) {
                // Live reports a stage that is not in the chain as an unresolvable name, at a position
                // it does not have — literally "error line 0 at position -1", because the reference was
                // rewritten into a generated table name before anything knew where it came from.
                throw new RuntimeException(
                    SqlCompilationError.at(0, -1, "invalid identifier 'SQL_PIPE_" + back + "'"));
            }
            final ResultSet stageResult = flowChainResults.get(flowChainResults.size() - back);
            if (stageResult == null) {
                throw new RuntimeException("$" + back
                    + " references a flow-chain statement that produced no result set");
            }
            final Table stageTable = flowStageTable(stageResult, alias != null ? alias : "$" + back);
            return new TableData(stageTable, stageResult.getRows(), alias).describedAs("RESULT[$" + back + "]");
        }

        // Handle VALUES clause
        if (source.DIRECTORY() != null && source.stageRef() != null) {
            // DIRECTORY(@stage): the directory table of file-level stage metadata.
            return new StageQueryExecutor(this).directoryTable(source.stageRef(), alias)
                .describedAs("DIRECTORY[" + getOriginalText(source.stageRef()) + "]").readWhilePlanning();
        }
        if (source.stageRef() != null) {
            // FROM @stage[/path] [(FILE_FORMAT => ..., PATTERN => ...)]: query the staged files.
            StageReferenceShape.refuseEmptyTableStageSchema(source.stageRef());
            return new StageQueryExecutor(this).queryStage(
                stageRefToLocation(source.stageRef()), source.stageQueryParams(), alias)
                .describedAs("STAGE[" + getOriginalText(source.stageRef()) + "]").readWhilePlanning();
        }
        if (source.STRING_LITERAL() != null) {
            // FROM '<location>': a QUOTED stage location reads the stage exactly as the bare @ form
            // does. Anything else is not a location at all, and is refused while compiling.
            final String location = SqlStringLiterals.decode(source.STRING_LITERAL().getText());
            if (!location.startsWith("@")) {
                throw new RuntimeException(SqlCompilationError.of(
                    "invalid URL prefix found in: '" + location + "'"));
            }
            return new StageQueryExecutor(this).queryStage(location, source.stageQueryParams(), alias)
                .describedAs("STAGE[" + location + "]").readWhilePlanning();
        }
        if (source.VALUES() != null && source.valueTupleList() != null) {
            // (VALUES ... [AS] v (c1, c2)) — Snowflake allows the alias inside the parens; an outer
            // alias (after the closing paren) wins when both are present.
            if (alias == null && source.identifier() != null) {
                alias = getIdentifier(source.identifier());
            }
            if (columnAliases == null && source.columnListOptional() != null) {
                columnAliases = new ArrayList<>();
                for (final FrostlakeParser.NamePartContext partCtx
                        : source.columnListOptional().namePart()) {
                    String colName = ParseTreeText.namePartText(partCtx);
                    // A quoted identifier keeps its case; every other spelling — including the
                    // keyword-as-name parts, which have no identifier node at all — folds up.
                    final FrostlakeParser.IdentifierContext quoted = partCtx.columnDefName() == null
                        ? null : partCtx.columnDefName().identifier();
                    if (quoted == null || quoted.QUOTED_IDENTIFIER() == null) {
                        colName = colName.toUpperCase();
                    }
                    columnAliases.add(colName);
                }
            }
            final List<Row> rows = new ArrayList<>();
            final List<TableColumn> columns = new ArrayList<>();
            // Each column's DECLARED type is the fold of its rows' expression types, exactly as a set
            // operation folds its arms — (1), (100000) declares NUMBER(6,0), (1.5), (200.25)
            // NUMBER(5,2), ('a'), ('bb') VARCHAR(2) — a bare NULL row contributing nothing; and its
            // interval is the rows' own, so the storage tag follows (live-verified).
            final List<List<DataType>> columnTypes = new ArrayList<>();
            final List<ValueRange> columnRanges = new ArrayList<>();
            final List<Boolean> columnNulls = new ArrayList<>();

            // Every item's own refusal comes before any is computed: a division or a math function live does
            // not fold is refused in VALUES (see ValuesFoldRule).
            final ExpressionEvaluator valuesTyper = new ExpressionEvaluator(
                new Table("DUMMY", new ArrayList<>(), false), functionRegistry, catalog, this);
            for (final FrostlakeParser.ValueTupleContext tuple : source.valueTupleList().valueTuple()) {
                for (final FrostlakeParser.BooleanExprContext expr : tuple.valueList().booleanExpr()) {
                    final String exprText = getOriginalText(expr);
                    Expression parsed = null;
                    try {
                        parsed = ExpressionEvaluator.parse(exprText);
                    } catch (final RuntimeException notAnExpression) {
                        parsed = null;
                    }
                    final RuntimeException unfolded = ValuesFoldRule.refusalOf(parsed, exprText, valuesTyper);
                    if (unfolded != null) {
                        throw unfolded;
                    }
                }
            }

            // Parse all value tuples
            for (final FrostlakeParser.ValueTupleContext tuple : source.valueTupleList().valueTuple()) {
                final List<Object> values = new ArrayList<>();
                int colIndex = 0;

                // Parse each value as an expression
                for (final FrostlakeParser.BooleanExprContext expr : tuple.valueList().booleanExpr()) {
                    final String exprText = getOriginalText(expr);
                    // Create dummy table/row for expression evaluation
                    final Table dummyTable = new Table("DUMMY", new ArrayList<>(), false);
                    final Row dummyRow = new Row(new ArrayList<>());
                    final ExpressionEvaluator evaluator = new ExpressionEvaluator(dummyTable, functionRegistry, catalog, this);
                    evaluator.setOuterLateralContext(lateralContext);
                    final Object value = evaluator.evaluate(exprText, dummyRow);
                    values.add(value);
                    while (columnTypes.size() <= colIndex) {
                        columnTypes.add(new ArrayList<>());
                        columnRanges.add(ValueRange.EMPTY);
                        columnNulls.add(Boolean.FALSE);
                    }
                    if (!isBareNullLiteral(exprText)) {
                        columnTypes.get(colIndex).add(valuesItemStaticType(exprText, evaluator));
                    }
                    final BigDecimal exact = ValueRange.exactOf(value);
                    if (exact != null) {
                        columnRanges.set(colIndex, columnRanges.get(colIndex).including(exact));
                    }
                    if (value == null) {
                        columnNulls.set(colIndex, Boolean.TRUE);
                    }

                    // Create columns based on first row
                    if (rows.isEmpty()) {
                        // Use column alias if provided, otherwise default to COLUMN1, COLUMN2, etc.
                        final String colName;
                        if (columnAliases != null && colIndex < columnAliases.size()) {
                            colName = columnAliases.get(colIndex);
                        } else {
                            colName = "COLUMN" + (colIndex + 1);
                        }
                        final DataType colType = inferDataType(value);
                        columns.add(new TableColumn(colName, colType, true, null, false, false, false));
                    }
                    colIndex++;
                }

                rows.add(new Row(values));
            }

            for (int i = 0; i < columns.size() && i < columnTypes.size(); i++) {
                final DataType folded = columnTypes.get(i).isEmpty() ? null
                    : DeclaredTypeFold.foldBranches(columnTypes.get(i));
                if (folded != null) {
                    final TableColumn untyped = columns.get(i);
                    final TableColumn typed = new TableColumn(untyped.getName(), folded, true, null, false, false, false);
                    typed.setStaticallyTyped(true);
                    typed.setValueRange(columnRanges.get(i).withNullable(columnNulls.get(i)));
                    columns.set(i, typed);
                }
            }

            // Create virtual table
            // An unaliased VALUES clause is spelled VALUES where an unaliased subquery is "values":
            // CAST(VALUES.COLUMN1 AS DATE) against CAST("values".X AS DATE) in the same sentence.
            final String tableName = alias != null ? alias : "VALUES";
            final Table virtualTable = new Table(tableName, columns, false);
            return new TableData(virtualTable, rows, alias).describedAs("VALUES");
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
        final String tableName = ParseTreeText.getQualifiedName(source.tableQualifiedName());

        // Check if it's a CTE first (also check thread-local CTE context for UPDATE/DELETE)
        final Map<String, ResultSet> effectiveCtes = cteResults != null ? cteResults
            : (currentCteContext != null ? currentCteContext : null);
        if (effectiveCtes != null && effectiveCtes.containsKey(tableName.toUpperCase())) {
            final ResultSet cteResult = effectiveCtes.get(tableName.toUpperCase());
            final Table virtualTable = resultSetToTable(cteResult, alias != null ? alias : tableName);
            // Make a defensive copy of the rows to avoid aliasing issues when CTE is used multiple times
            final List<Row> rowsCopy = new ArrayList<>(cteResult.getRows());
            return new TableData(virtualTable, rowsCopy, alias).describedAs("CTE[" + tableName + "]");
        }

        // Check if it's an INFORMATION_SCHEMA system view first (before checking regular views)
        final ResultSet systemViewResult = executeSystemViewIfApplicable(tableName, null);
        if (systemViewResult != null) {
            final Table virtualTable = resultSetToTable(systemViewResult, alias != null ? alias : tableName);
            return new TableData(virtualTable, systemViewResult.getRows(), alias)
                .describedAs("SYSTEM VIEW[" + tableName + "]").readWhilePlanning();
        }

        // Check if it's a view
        View view = null;
        try {
            view = resolveView(tableName);
        } catch (final Exception e) {
            // Not a view
        }

        if (view != null) {
            // A block that keeps no row never runs the view, and a column nobody reads is blanked first:
            // a value that would fault in either is never computed (see UnreadColumnPruning).
            if (view.hasResolvedColumns() && keepsNoRow(ctx)) {
                return emptyView(view, alias != null ? alias : tableName, alias);
            }
            return plannedView(view, tableName, readDefinition(view, ctx), alias);
        }

        // MATERIALIZED VIEW / DYNAMIC TABLE — no backing storage is maintained, so materialize on read:
        // plan the defining query and expose its result as a virtual table (correct results, not
        // an incrementally-refreshed cache).
        MaterializedView materializedView = null;
        try { materializedView = resolveMaterializedView(tableName); } catch (final Exception e) { /* not an MV */ }
        if (materializedView != null) {
            return plannedMaterializedView(materializedView, tableName, alias);
        }

        DynamicTable dynamicTable = null;
        try { dynamicTable = resolveDynamicTable(tableName); } catch (final Exception e) { /* not a dynamic table */ }
        if (dynamicTable != null) {
            return plannedDynamicTable(dynamicTable, tableName, alias);
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

        // DUAL — the legacy one-row pseudo-table used by `SELECT <expr> FROM DUAL`. A BARE UNQUOTED
        // dual in FROM position is ALWAYS the pseudo-table, even when a real table named DUAL
        // exists (live-verified: `FROM DUAL` answers COLUMN1/NULL beside a populated user table,
        // which only `FROM "DUAL"` or a qualified name reaches). It is not registered in the
        // catalog, so SHOW TABLES / INFORMATION_SCHEMA.TABLES / DESCRIBE never list it. Shape: one
        // nullable column COLUMN1, one row whose value is NULL.
        final boolean bareUnquotedDual = "DUAL".equalsIgnoreCase(tableName)
            && source.tableQualifiedName() != null
            && source.tableQualifiedName().namePart().isEmpty()
            && source.tableQualifiedName().nameStartPart().identifier() != null
            && source.tableQualifiedName().nameStartPart().identifier().QUOTED_IDENTIFIER() == null;
        if (bareUnquotedDual) {
            final List<TableColumn> dualCols = new ArrayList<>();
            dualCols.add(new TableColumn("COLUMN1", StringType.VARCHAR, true, null, false, false, false));
            final List<Object> dualValues = new ArrayList<>();
            dualValues.add(null);
            final List<Row> dualRows = new ArrayList<>();
            dualRows.add(new Row(dualValues));
            return new TableData(new Table(alias != null ? alias : "DUAL", dualCols, false), dualRows, alias)
                .describedAs("DUAL");
        }

        // Regular table (a quoted "DUAL" or qualified ...DUAL reaches a real table here).
        final Table table;
        // A name missing from a FROM clause is an OBJECT, not a Table, and is echoed as written —
        // see Catalog#resolveTableAsWritten for the measurements — except while CREATE VIEW compiles
        // a body, where live spells it by its full path in the view's own schema.
        table = catalog.resolveTableAsWritten(viewScopeQualified(tableName), "Object");
        final String fullyQualifiedName = getFullyQualifiedTableName(tableName);
        table.setQualifiedName(fullyQualifiedName);
        final TableStorage tableStorage =
            storageEngine.getTableStorage(fullyQualifiedName);

        // Handle time travel clause: AT / BEFORE
        if (source.timeTravelClause() != null) {
            final FrostlakeParser.TimeTravelClauseContext ttCtx = source.timeTravelClause();

            // CHANGES clause
            if (ttCtx.changesClause() != null) {
                // CHANGES requires change tracking on the table — the option, ALTER … SET, or a
                // stream's creation turns it on. Live refuses with this single-line shape.
                if (!table.isChangeTracking()) {
                    throw new RuntimeException(SqlCompilationError.PREFIX
                        + " Change tracking is not enabled or has been missing for the time range"
                        + " requested on table '" + fullyQualifiedName + "'.");
                }
                // A change set carries the change-tracking metadata columns beside the table's own,
                // exactly as a stream read does.
                return new TableData(changeTrackingTable(table), new RowsProvider() {
                    @Override
                    public List<Row> rows() {
                        return executeChangesClause(ttCtx.changesClause(), tableStorage, table);
                    }

                    @Override
                    public String describe() {
                        return null;
                    }
                }, alias, "CHANGES[" + table.getName() + "]");
            }

            // AT / BEFORE
            final boolean isBefore = ttCtx.BEFORE() != null && ttCtx.changesClause() == null;
            final FrostlakeParser.TimeTravelPointContext ptCtx = ttCtx.timeTravelPoint();
            final long targetMillis = ptCtx.STATEMENT() != null
                ? resolveStatementPoint(ptCtx, isBefore)
                : resolveTimeTravelPoint(ptCtx);
            // A statement point reads by the order the snapshots were taken in, which tells apart two statements
            // the clock cannot: two writes inside one millisecond.
            final long targetSequence = ptCtx.STATEMENT() != null ? statementSequence(ptCtx, isBefore) : -1L;
            if (targetMillis > System.currentTimeMillis()) {
                throw new RuntimeException(
                    "Future data is not yet available for table " + table.getName() + ".");
            }
            // A point before the table came into being is refused with the retention wording,
            // live-verified; the engine keeps full history from creation, so creation IS the bound.
            if (targetMillis < tableStorage.getCreatedMillis()) {
                throw new RuntimeException("Time travel data is not available for table "
                    + table.getName() + ". The requested time is either beyond the allowed time"
                    + " travel period or before the object creation time.");
            }

            return new TableData(table, new RowsProvider() {
                @Override
                public List<Row> rows() {
                    final TableSnapshot snap = targetSequence >= 0 ? tableStorage.snapshotUpTo(targetSequence)
                        : isBefore ? tableStorage.snapshotBefore(targetMillis)
                        : tableStorage.snapshotAt(targetMillis);
                    return snap != null ? new ArrayList<>(snap.rows) : new ArrayList<>();
                }

                @Override
                public String describe() {
                    return null;
                }
            }, alias, "SCAN[" + table.getName() + " " + getOriginalText(ttCtx) + "]");
        }

        if (deferredApply && transactionManager.hasActiveTransaction()) {
            // Overlay this transaction's buffered writes on the committed base (READ COMMITTED for self).
            final TransactionWriteSet writeSet = transactionManager.getCurrentTransaction().getWriteSet();
            return new TableData(table, new RowsProvider() {
                @Override
                public List<Row> rows() {
                    return writeSet.overlayRows(fullyQualifiedName, tableStorage);
                }

                @Override
                public String describe() {
                    return null;
                }
            }, alias, "SCAN[" + table.getName() + "]");
        }
        return new TableData(table, new RowsProvider() {
            @Override
            public List<Row> rows() {
                return tableStorage.scan();
            }

            @Override
            public String describe() {
                return null;
            }
        }, alias, "SCAN[" + table.getName() + "]");
    }

    /** A table's rows as this transaction sees them, read when the source stage runs. */
    RowsProvider transactionScan(final String fullyQualifiedName) {
        return new RowsProvider() {
            @Override
            public List<Row> rows() {
                return new ArrayList<>(readTableRowsForTransaction(fullyQualifiedName));
            }

            @Override
            public String describe() {
                return null;
            }
        };
    }

    /**
     * A planned body's rows, produced inside a relation body's scope when the source stage runs: the body's
     * items wait for the reading statement, as they do while the body is planned (see RelationBody).
     */
    private static RowsProvider relationBodyRows(final PlannedQuery body) {
        return new RowsProvider() {
            @Override
            public List<Row> rows() {
                final boolean outerBody = RelationBody.begin();
                try {
                    return body.run().getRows();
                } finally {
                    RelationBody.end(outerBody);
                }
            }

            @Override
            public String describe() {
                return body.description;
            }
        };
    }

    /**
     * A table-function call as a source: its shape from the function, or — for a function that cannot tell
     * its columns without running — from running it here, once, which the plan marks; its rows are the
     * call's result, produced when the source stage runs.
     *
     * @param call          the planned call
     * @param alias         the alias written after the source, or null
     * @param defaultName   the name the relation answers to when unaliased
     * @param columnAliases the derived-column alias list, or null
     * @return the source
     */
    private TableData tableFunctionSource(final PlannedTableFunction call, final String alias,
                                          final String defaultName, final List<String> columnAliases) {
        List<ResultSetColumn> columns = call.columns;
        final boolean resolvedByRunning = columns == null;
        if (resolvedByRunning) {
            columns = call.result.getResultSet().getColumns();
        }
        Table shape = resultSetToTable(new ResultSet(columns, new ArrayList<Row>()),
            alias != null ? alias : defaultName);
        if (columnAliases != null) {
            shape = applyColumnAliases(shape, columnAliases);
        }
        final TableData planned = new TableData(shape, new RowsProvider() {
            @Override
            public List<Row> rows() {
                return call.result.getResultSet().getRows();
            }

            @Override
            public String describe() {
                return null;
            }
        }, alias, "TABLE[" + call.callText + "]");
        return resolvedByRunning ? planned.readWhilePlanning() : planned;
    }

    /**
     * A catalog column's statistics: the interval between the least and the greatest value in the WHOLE
     * table — every row, whatever the query's WHERE keeps — which is what the account tags the column by,
     * and whether any row holds a NULL, which decides whether the column may answer one. Empty for a
     * column holding only NULLs or no rows; null for a table this executor cannot read.
     *
     * @param owner      the catalog table, as the FROM clause resolved it
     * @param columnName the column
     * @return the interval, or null when the table's rows are not reachable
     */
    public ValueRange columnValueRange(final Table owner, final String columnName) {
        final String qualifiedName = owner.getQualifiedName();
        if (qualifiedName == null || !storageEngine.hasTable(qualifiedName)) {
            return null;
        }
        final int index = owner.getColumnIndex(columnName);
        if (index < 0) {
            return null;
        }
        ValueRange range = ValueRange.EMPTY;
        boolean holdsNull = false;
        for (final Row row : readTableRowsForTransaction(qualifiedName)) {
            final Object value = index < row.size() ? row.getValue(index) : null;
            holdsNull |= value == null;
            range = range.including(ValueRange.exactOf(value));
        }
        return range.withNullable(holdsNull);
    }

    /**
     * The rows a catalog table holds, as this transaction sees them, or null when the table's rows are
     * not reachable — the bound the account gives a COUNT over it.
     */
    public Long baseTableRowCount(final Table owner) {
        final String qualifiedName = owner.getQualifiedName();
        if (qualifiedName == null || !storageEngine.hasTable(qualifiedName)) {
            return null;
        }
        return (long) readTableRowsForTransaction(qualifiedName).size();
    }

    /**
     * Whether the SELECT clause executing on this thread reads past its table's statistics — a join, a
     * grouping key, a HAVING, or a WHERE the statistics cannot prove — so that its COUNT is tagged by its
     * declared width and a typeof over COUNT, MIN or MAX is no longer answered from them.
     */
    public boolean countIsUnbounded() {
        final CountStatisticsBound bound = statisticsBound.get();
        return bound != null && bound.isUnbounded();
    }

    /**
     * Rows of a table as THIS transaction sees them: the committed base overlaid with the transaction's
     * buffered writes when deferred-apply is active, else a plain scan. For read-only consumers (a MERGE
     * source, for example) — a raw {@code scan()} was blind to rows the same transaction had just
     * inserted, so a stage table populated and merged within one procedure produced an empty merge.
     */
    List<Row> readTableRowsForTransaction(final String fullyQualifiedName) {
        final TableStorage tableStorage = storageEngine.getTableStorage(fullyQualifiedName);
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
            return catalog.getDatabase(db).getSchema(sc).getStream(streamName);
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
     * True when a name reaches a relation a query can read — a view, materialized view, dynamic table,
     * stream or base table — looked up in the catalog alone, without reading or executing anything.
     *
     * @param name the canonical name
     * @return whether it names a relation
     */
    boolean namesARelation(final String name) {
        try {
            resolveView(name);
            return true;
        } catch (final RuntimeException notAView) {
            // Each kind is tried in turn below.
        }
        try {
            resolveMaterializedView(name);
            return true;
        } catch (final RuntimeException notAnMv) {
            // Each kind is tried in turn below.
        }
        try {
            resolveDynamicTable(name);
            return true;
        } catch (final RuntimeException notADynamicTable) {
            // Each kind is tried in turn below.
        }
        if (findStream(name) != null) {
            return true;
        }
        try {
            catalog.resolveTableAsWritten(name, "Object");
            return true;
        } catch (final RuntimeException notATable) {
            return false;
        }
    }

    /**
     * Plan a runtime object name (e.g. from {@code IDENTIFIER(<expr>)}) as a non-base-table queryable
     * source — a view, materialized view, dynamic table, or stream — mirroring how a direct FROM reference
     * resolves. Returns null when the name is (or resolves to) a plain base table, which the caller reads
     * from storage directly.
     */
    private TableData planNamedSource(final String name, final String alias) {
        View view = null;
        try { view = resolveView(name); } catch (final Exception e) { /* not a view */ }
        if (view != null) {
            return plannedView(view, name, view.getDefinition(), alias);
        }
        MaterializedView mv = null;
        try { mv = resolveMaterializedView(name); } catch (final Exception e) { /* not an MV */ }
        if (mv != null) {
            return plannedMaterializedView(mv, name, alias);
        }
        DynamicTable dt = null;
        try { dt = resolveDynamicTable(name); } catch (final Exception e) { /* not a dynamic table */ }
        if (dt != null) {
            return plannedDynamicTable(dt, name, alias);
        }
        return resolveStreamTableData(name, alias);   // a stream, or null for a plain base table
    }

    /**
     * A view as a source: its body planned where the view lives (see {@link #executeInViewScope}) and its
     * shape taken from that plan; the rows flow when the source stage runs the body under the same scope.
     */
    private TableData plannedView(final View view, final String tableName, final String body, final String alias) {
        // A refusal the stored body raises is positioned in the CREATE statement that declared the view,
        // not in the body alone and not in the statement doing the reading.
        final SourcePosition bodyOrigin = viewBodyOrigin(view, body);
        final PlannedQuery planned;
        final SourcePosition displacedOrigin = bodyOrigin == null
            ? null : ExpressionSource.begin(bodyOrigin);
        try {
            planned = planInViewScope(tableName, body);
        } finally {
            if (bodyOrigin != null) {
                ExpressionSource.end(displacedOrigin);
            }
        }
        rejectViewShapeDrift(view, tableName, planned);
        final TableData source = plannedBodySource(planned, alias != null ? alias : tableName,
            view.hasExplicitColumnNames() ? view.getColumnNames() : null, alias, "VIEW[" + tableName + "]");
        stampViewRowAccessPolicy(view, source.table);
        if (view.hasRowAccessPolicy()) {
            // A policy decides per row what the view hands out, which no statistics bound.
            source.table.setRelationStatistics(null);
        }
        return source;
    }

    /**
     * Where a view's body begins inside the CREATE statement that declared it. A refusal the body raises
     * while it is planned is anchored there, which is the text the account counts from. Null when the
     * stored DDL does not hold the body verbatim — a body rewritten before it is planned, or a view
     * restored without its statement — and the refusal then carries the body's own place.
     *
     * @param view the view
     * @param body the body about to be planned
     * @return the body's line and column within the stored CREATE text, or null
     */
    private SourcePosition viewBodyOrigin(final View view, final String body) {
        final String ddl = view.getOriginalDdl();
        if (ddl == null || body == null || body.isEmpty()) {
            return null;
        }
        final int at = ddl.lastIndexOf(body);
        if (at < 0) {
            return null;
        }
        int line = 1;
        int column = 0;
        for (int i = 0; i < at; i++) {
            if (ddl.charAt(i) == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
        }
        return new SourcePosition(line, column);
    }

    /**
     * A view carries the shape it was created with. When its body no longer produces that many columns —
     * a {@code SELECT *} view whose base table has since lost one — the read is refused rather than
     * answered at the narrower shape, which is what the account does.
     *
     * @param view      the view being read
     * @param tableName the name it was reached by, which the refusal spells fully qualified
     * @param planned   its body, compiled
     */
    private void rejectViewShapeDrift(final View view, final String tableName, final PlannedQuery planned) {
        final List<TableColumn> declared = view.getResolvedColumns();
        if (declared == null || declared.isEmpty() || planned.shape == null) {
            return;
        }
        final int produced = planned.shape.getColumns().size();
        if (produced == declared.size()) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.of("View definition for '"
            + getFullyQualifiedTableName(tableName) + "' declared " + declared.size()
            + " column(s), but view query produces " + produced + " column(s)."));
    }

    /** A materialized view as a source: no backing storage is maintained, so its defining query is the plan. */
    private TableData plannedMaterializedView(final MaterializedView view, final String tableName,
                                              final String alias) {
        final PlannedQuery planned = planInViewScope(tableName, view.getDefinition());
        return plannedBodySource(planned, alias != null ? alias : tableName,
            view.hasExplicitColumnNames() ? view.getColumnNames() : null, alias,
            "MATERIALIZED VIEW[" + tableName + "]");
    }

    /** A dynamic table as a source: its defining query is the plan, run on its own as {@link #execute} runs one. */
    private TableData plannedDynamicTable(final DynamicTable dynamicTable, final String tableName,
                                          final String alias) {
        return plannedBodySource(planDetachedBody(dynamicTable.getQuery()), alias != null ? alias : tableName,
            null, alias, "DYNAMIC TABLE[" + tableName + "]");
    }

    /**
     * A planned body as a source: the body's shape under {@code name}, renamed by {@code columnNames} where
     * given, and its rows the body's plan, run when the source stage runs.
     */
    private TableData plannedBodySource(final PlannedQuery planned, final String name,
                                        final List<String> columnNames, final String alias,
                                        final String description) {
        final Table virtualTable = columnNames != null
            ? resultSetToTable(planned.shape, name, columnNames)
            : resultSetToTable(planned.shape, name);
        return new TableData(virtualTable, plannedRows(planned), alias,
            description + "{" + planned.description + "}");
    }

    /** A planned query's rows, produced by running it. */
    static RowsProvider plannedRows(final PlannedQuery planned) {
        return new RowsProvider() {
            @Override
            public List<Row> rows() {
                return planned.run().getRows();
            }

            @Override
            public String describe() {
                return planned.description;
            }
        };
    }

    /**
     * A stream as a source: its unconsumed change records, with the change-tracking metadata columns beside
     * the source's own. A stream over a table has its shape from the table and its records read when the
     * source stage runs; a stream over a view is projected through the view's branches here, which the plan
     * marks. A consuming read — a DML statement's, a CTAS's — registers what it saw as it reads.
     *
     * @param name  the stream as named
     * @param alias the alias written after the source, or null
     * @return the source, or null when the name is not a stream's
     */
    TableData resolveStreamTableData(final String name, final String alias) {
        final Stream stream = findStream(name);
        if (stream == null) {
            return null;
        }
        final boolean consuming = consumingStreamContext;
        if (stream.getSourceType() == StreamSourceType.VIEW) {
            final TableData data = buildViewStreamTableData(stream, alias);
            registerStreamRead(stream, consuming);
            return data.describedAs("STREAM SCAN[" + stream.getName() + "]").readWhilePlanning();
        }
        final List<TableColumn> columns;
        final boolean stageStream = stream.getSourceType() == StreamSourceType.STAGE;
        if (stageStream) {
            // A stream on a stage reports the directory table's columns.
            columns = StageQueryExecutor.directoryColumns();
        } else if (stream.getSourceType() == StreamSourceType.DYNAMIC_TABLE) {
            final DynamicTable dynamicTable = resolveDynamicTable(stream.getSourceTableName());
            if (dynamicTable.getLastRefreshedTime() == null) {
                throw new RuntimeException("Dynamic Table '" + stream.getSourceTableName().toUpperCase()
                    + "' is not initialized. Please run a manual refresh or wait for a scheduled refresh before "
                    + "querying.");
            }
            columns = new ArrayList<>(resultSetToTable(planDetachedBody(dynamicTable.getQuery()).shape,
                stream.getName()).getColumns());
        } else {
            columns = new ArrayList<>(catalog.resolveTable(stream.getSourceTableName()).getColumns());
        }
        appendStreamMetadataColumns(columns);
        final Table virtual = new Table(alias != null ? alias : stream.getName(), columns, false);
        return new TableData(virtual, new RowsProvider() {
            @Override
            public List<Row> rows() {
                final List<Row> rows = new ArrayList<>();
                for (final StreamRecord record : unconsumedNetRecords(stream)) {
                    final List<Object> values = new ArrayList<>(record.getValues());
                    appendStreamMetadataValues(values, record);
                    if (stageStream) {
                        // A directory table's rows have no row id: the column reads empty.
                        values.set(values.size() - 1, "");
                    }
                    rows.add(new Row(values));
                }
                registerStreamRead(stream, consuming);
                return rows;
            }

            @Override
            public String describe() {
                return null;
            }
        }, alias, "STREAM SCAN[" + stream.getName() + "]");
    }

    /**
     * Registers a consuming read of a stream, scoped to what the read saw: consumed on commit — the committed
     * prefix plus the transaction's already-buffered changes, so anything the transaction writes AFTER the
     * read stays — or, for a CTAS (atomic DDL), by consumeCtasStreams() after it succeeds.
     */
    private void registerStreamRead(final Stream stream, final boolean consuming) {
        if (!consuming) {
            return;
        }
        if (transactionManager.hasActiveTransaction()) {
            transactionManager.getCurrentTransaction().registerStreamConsumption(stream,
                new StreamReadScope(stream.recordCount(), bufferedTransientRecords(stream)));
        } else {
            ctasStreamsRead.add(stream);
        }
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

    /** The appends in {@code stream}'s window — see {@link Stream#getUnconsumedAppendsWith}. */
    private List<StreamRecord> unconsumedAppendRecords(final Stream stream) {
        return stream.getUnconsumedAppendsWith(bufferedTransientRecords(stream));
    }

    /** The current transaction's buffered changes to {@code stream}'s base table(s), synthesized as
     *  transient stream records (see {@link #unconsumedNetRecords}); empty outside a transaction. Also
     *  captured at consuming-read registration as the read's seen-scope, so commit can consume exactly
     *  these once the write set re-emits them as real records. */
    private List<StreamRecord> bufferedTransientRecords(final Stream stream) {
        if (!transactionManager.hasActiveTransaction() || stream.getSourceType() == StreamSourceType.STAGE
                || stream.getSourceType() == StreamSourceType.DYNAMIC_TABLE) {
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
        if (branches.size() == 1 && branchIsJoin(branches.get(0))) {
            return buildJoinViewStreamTableData(stream, view, branches.get(0), alias);
        }

        // Column shape follows the FIRST branch (a UNION ALL takes its output names/types from the
        // first query), then explicit view column names override when the counts match.
        final FrostlakeParser.SelectClauseContext firstBranch = branches.get(0);
        final Table firstBase = catalog.resolveTable(branchBaseTable(firstBranch));
        final List<TableColumn> columns = viewStreamColumns(firstBranch, firstBase, view);
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
            final String whereText = branch.whereClause() == null
                ? null : getOriginalText(branch.whereClause().booleanExpr());
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
     * the expression text). A bare column keeps the base column's type; any other item takes the type the
     * view resolved for that position, as live types it: {@code 'lit' AS l} reads VARCHAR(3) and
     * {@code id + 1} NUMBER(38,0) through the stream, not VARIANT. VARIANT remains only for a view that
     * carries no resolved columns.
     */
    private List<TableColumn> viewStreamColumns(final FrostlakeParser.SelectClauseContext branch, final Table baseTable,
                                                final View view) {
        final List<FrostlakeParser.SelectItemContext> items = branch.selectList().selectItem();
        final boolean identity = items.size() == 1 && SelectItemAccessors.isStarItem(items.get(0));
        final List<TableColumn> columns = new ArrayList<>();
        if (identity) {
            columns.addAll(baseTable.getColumns());
        } else {
            final List<TableColumn> resolved = view.hasResolvedColumns()
                && view.getResolvedColumns().size() == items.size() ? view.getResolvedColumns() : null;
            for (int i = 0; i < items.size(); i++) {
                final FrostlakeParser.SelectItemContext item = items.get(i);
                final String exprText = getOriginalText(SelectItemAccessors.getItemExpression(item));
                final String columnName = SelectItemAccessors.getItemAlias(item) != null
                    ? (SelectItemAccessors.getItemAlias(item)) : exprText;
                final DataType columnType;
                if (baseTable.hasColumn(exprText.trim())) {
                    columnType = baseTable.getColumn(exprText.trim()).getDataType();
                } else if (resolved != null) {
                    columnType = resolved.get(i).getDataType();
                } else {
                    columnType = VariantType.VARIANT;
                }
                columns.add(new TableColumn(columnName.toUpperCase(), columnType, true, null, false, false, false));
            }
        }
        return columns;
    }

    /**
     * Materialize a JOIN-view stream by SNAPSHOT DIFF, which reproduces live's measured semantics
     * for every mutation shape: the base tables' PRE state (their current rows with this window's
     * net records reverse-applied) and CURRENT state each evaluate the view, and the multiset
     * difference is the stream — a row insert with no join partner surfaces nothing, either side
     * completing a match surfaces the joined row, an in-window insert+delete cancels, and a
     * one-side update pairs into DELETE+INSERT with METADATA$ISUPDATE=true. The pre-state relations
     * shadow the base tables as pre-computed CTEs, so no catalog or storage state is touched.
     */
    private TableData buildJoinViewStreamTableData(final Stream stream, final View view,
                                                   final FrostlakeParser.SelectClauseContext branch,
                                                   final String alias) {
        // Current view result — through the normal read path, so a transaction's own buffered DML
        // is visible exactly as it is to any other read.
        final List<ResultSet> curResults = execute(view.getDefinition());
        final ResultSet curResult = curResults.isEmpty() ? null : curResults.get(0);
        if (curResult == null) {
            throw new RuntimeException("Source view not found for stream "
                + stream.getName() + ": " + stream.getSourceTableName());
        }
        final List<TableColumn> columns = new ArrayList<>();
        for (int i = 0; i < curResult.getColumns().size(); i++) {
            final String name = view.hasExplicitColumnNames()
                    && view.getColumnNames().size() == curResult.getColumns().size()
                ? view.getColumnNames().get(i).toUpperCase()
                : curResult.getColumns().get(i).getName();
            columns.add(new TableColumn(name, curResult.getColumns().get(i).getDataType(),
                true, null, false, false, false));
        }
        appendStreamMetadataColumns(columns);
        final Table virtual = new Table(alias != null ? alias : stream.getName(), columns, false);

        final List<StreamRecord> netRecords = unconsumedNetRecords(stream);
        if (netRecords.isEmpty()) {
            return new TableData(virtual, new ArrayList<>(), alias);
        }

        // PRE state per base table, shadowing each table (bare and as-written spellings) as a CTE.
        final Map<String, ResultSet> preCtes = new HashMap<>();
        for (final String baseName : branchJoinTableNames(branch)) {
            final String bare = QualifiedName.parse(baseName).last().toUpperCase();
            if (preCtes.containsKey(bare)) {
                continue;   // self-join: one pre-relation serves every reference
            }
            final List<StreamRecord> tableRecords = new ArrayList<>();
            for (final StreamRecord record : netRecords) {
                if (bare.equalsIgnoreCase(record.getSourceTable())) {
                    tableRecords.add(record);
                }
            }
            final List<ResultSet> tableResults = execute("SELECT * FROM " + baseName);
            final ResultSet curTable = tableResults.isEmpty() ? null : tableResults.get(0);
            if (curTable == null) {
                throw new RuntimeException("Source view not found for stream "
                    + stream.getName() + ": " + stream.getSourceTableName());
            }
            final ResultSet preTable = new ResultSet(curTable.getColumns(),
                reverseApplyNetRecords(curTable.getRows(), tableRecords));
            preCtes.put(bare, preTable);
            preCtes.put(baseName, preTable);
        }
        final ResultSet preResult = executeSelectFromContextWithCTEs(
            parseSelectStatement(view.getDefinition()), null, preCtes);

        // Multiset diff: rows only in PRE were removed (DELETE), rows only in CURRENT were added
        // (INSERT). Keys are normalized value vectors, and each key keeps sample rows to render.
        final Map<List<Object>, Integer> balance = new LinkedHashMap<>();
        final Map<List<Object>, Row> sampleRows = new HashMap<>();
        for (final Row row : preResult.getRows()) {
            final List<Object> key = normalizedRowKey(row);
            final Integer count = balance.get(key);
            balance.put(key, (count == null ? 0 : count) + 1);
            sampleRows.putIfAbsent(key, row);
        }
        for (final Row row : curResult.getRows()) {
            final List<Object> key = normalizedRowKey(row);
            final Integer count = balance.get(key);
            balance.put(key, (count == null ? 0 : count) - 1);
            sampleRows.putIfAbsent(key, row);
        }
        final List<Row> removed = new ArrayList<>();
        final List<Row> added = new ArrayList<>();
        for (final Map.Entry<List<Object>, Integer> entry : balance.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                removed.add(sampleRows.get(entry.getKey()));
            }
            for (int i = 0; i < -entry.getValue(); i++) {
                added.add(sampleRows.get(entry.getKey()));
            }
        }

        // An update window (every net record is an update half) pairs each removed row with the
        // added row it most agrees with — live reports such a pair as DELETE+INSERT sharing a row
        // id, METADATA$ISUPDATE=true. Any other window renders plain DELETEs and INSERTs.
        final List<Row> rows = new ArrayList<>();
        long syntheticRowId = 0;
        boolean allUpdates = true;
        for (final StreamRecord record : netRecords) {
            allUpdates = allUpdates && record.isUpdate();
        }
        if (allUpdates && !removed.isEmpty() && removed.size() == added.size()) {
            final boolean[] taken = new boolean[added.size()];
            for (final Row gone : removed) {
                int best = -1;
                int bestAgreement = -1;
                for (int i = 0; i < added.size(); i++) {
                    if (taken[i]) {
                        continue;
                    }
                    final int agreement = agreementCount(gone, added.get(i));
                    if (agreement > bestAgreement) {
                        bestAgreement = agreement;
                        best = i;
                    }
                }
                taken[best] = true;
                final long rowId = syntheticRowId++;
                rows.add(streamOutputRow(gone, ChangeType.DELETE, true, rowId));
                rows.add(streamOutputRow(added.get(best), ChangeType.INSERT, true, rowId));
            }
        } else {
            for (final Row gone : removed) {
                rows.add(streamOutputRow(gone, ChangeType.DELETE, false, syntheticRowId++));
            }
            for (final Row fresh : added) {
                rows.add(streamOutputRow(fresh, ChangeType.INSERT, false, syntheticRowId++));
            }
        }
        return new TableData(virtual, rows, alias);
    }

    /** A base table's rows as they stood at the stream offset: the current rows with this window's
     *  net records un-done in reverse order (an INSERT removes one matching row, a DELETE restores
     *  its image; an update's DELETE+INSERT pair reverses back to the old image). */
    private List<Row> reverseApplyNetRecords(final List<Row> currentRows, final List<StreamRecord> records) {
        final List<Row> rows = new ArrayList<>(currentRows);
        for (int i = records.size() - 1; i >= 0; i--) {
            final StreamRecord record = records.get(i);
            if (record.getChangeType() == ChangeType.INSERT) {
                final List<Object> key = normalizedValues(record.getValues());
                for (int r = rows.size() - 1; r >= 0; r--) {
                    if (normalizedRowKey(rows.get(r)).equals(key)) {
                        rows.remove(r);
                        break;
                    }
                }
            } else {
                rows.add(new Row(new ArrayList<>(record.getValues())));
            }
        }
        return rows;
    }

    /**
     * A source's rows as they stood at a stream's offset: the rows now, with the stream's pending net changes
     * undone.
     *
     * @param currentRows the source's rows now
     * @param stream      the stream
     * @return the rows at the stream's offset
     */
    public List<Row> rowsAtStreamOffset(final List<Row> currentRows, final Stream stream) {
        return reverseApplyNetRecords(currentRows, stream.getUnconsumedNetRecords());
    }

    /**
     * The table as it stood at a stream's AT | BEFORE point, refused as a time-travel read of the table refuses
     * the point — a positive offset, a statement that is not there, a point in the future or before the table —
     * and first when a TIMESTAMP is computed from CURRENT_TIMESTAMP, which cannot anchor change tracking.
     *
     * @param table   the table
     * @param storage its rows
     * @param point   the point, AT or BEFORE a TIMESTAMP, OFFSET or STATEMENT
     * @return the snapshot at the point, or null when the table held no row yet
     */
    public TableSnapshot tableSnapshotAtStreamPoint(final Table table, final TableStorage storage,
                                                    final FrostlakeParser.StreamPointContext point) {
        final FrostlakeParser.TimeTravelPointContext ptCtx = point.timeTravelPoint();
        final boolean isBefore = point.BEFORE() != null;
        final long targetMillis = streamPointMillis(point);
        final long targetSequence = ptCtx.STATEMENT() != null ? statementSequence(ptCtx, isBefore) : -1L;
        return tableSnapshotAt(table, storage, targetMillis, targetSequence, isBefore);
    }

    /**
     * The instant a stream's AT | BEFORE point names — a TIMESTAMP, an OFFSET or a STATEMENT — refused first when
     * a TIMESTAMP is computed from CURRENT_TIMESTAMP, which cannot anchor change tracking.
     *
     * @param point the point
     * @return the instant, in epoch milliseconds
     */
    public long streamPointMillis(final FrostlakeParser.StreamPointContext point) {
        final FrostlakeParser.TimeTravelPointContext ptCtx = point.timeTravelPoint();
        if (ptCtx.TIMESTAMP() != null && ptCtx.expression() != null) {
            final Token nonDeterministic = firstTokenOfType(ptCtx.expression(), FrostlakeParser.CURRENT_TIMESTAMP);
            if (nonDeterministic != null) {
                throw new RuntimeException(SqlCompilationError.atLine(nonDeterministic.getLine(),
                    nonDeterministic.getCharPositionInLine(), "Query contains time travel to a version containing "
                        + "function 'CURRENT_TIMESTAMP', but change tracking is not supported on queries with "
                        + "non-deterministic functions in versions."));
            }
        }
        return ptCtx.STATEMENT() != null ? resolveStatementPoint(ptCtx, point.BEFORE() != null)
            : resolveTimeTravelPoint(ptCtx);
    }

    /**
     * The table as it stood at an instant, refused when the instant is in the future or before the table was.
     *
     * @param table          the table
     * @param storage        its rows
     * @param targetMillis   the instant
     * @param targetSequence the snapshot sequence a statement point reads by, or -1
     * @param isBefore       whether the point is just before the instant
     * @return the snapshot, or null when the table held no row yet
     */
    public TableSnapshot tableSnapshotAt(final Table table, final TableStorage storage, final long targetMillis,
                                         final long targetSequence, final boolean isBefore) {
        if (targetMillis > System.currentTimeMillis()) {
            throw new RuntimeException("Future data is not yet available for table " + table.getName() + ".");
        }
        if (targetMillis < storage.getCreatedMillis()) {
            throw timeTravelUnavailable(table.getName());
        }
        return targetSequence >= 0 ? storage.snapshotUpTo(targetSequence)
            : isBefore ? storage.snapshotBefore(targetMillis) : storage.snapshotAt(targetMillis);
    }

    /**
     * The refusal of a time-travel point before an object came into being.
     *
     * @param name the object's name
     * @return the refusal
     */
    public static RuntimeException timeTravelUnavailable(final String name) {
        return new RuntimeException("Time travel data is not available for table " + name
            + ". The requested time is either beyond the allowed time travel period or before the object "
            + "creation time.");
    }

    /**
     * The value of an expression that reads no row, such as the name a {@code STREAM => '<name>'} point gives.
     *
     * @param expression the expression
     * @return its value
     */
    public Object evaluateStandalone(final FrostlakeParser.ExpressionContext expression) {
        return evaluateExpression(getOriginalText(expression), null, (Table) null);
    }

    /**
     * The table as it stood at a moment in the order snapshots are taken in — another stream's offset — refused
     * when the table came into being after it.
     *
     * @param table    the table
     * @param storage  its rows
     * @param sequence the moment
     * @return the snapshot, or null when the table held no row yet
     */
    public TableSnapshot tableSnapshotAtSequence(final Table table, final TableStorage storage, final long sequence) {
        if (sequence < storage.getCreatedSequence()) {
            throw timeTravelUnavailable(table.getName());
        }
        return storage.snapshotUpTo(sequence);
    }

    /** The first token of that type under a parse tree, or null. */
    private static Token firstTokenOfType(final ParseTree tree, final int type) {
        if (tree instanceof TerminalNode) {
            final Token token = ((TerminalNode) tree).getSymbol();
            return token.getType() == type ? token : null;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            final Token found = firstTokenOfType(tree.getChild(i), type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * A dynamic table's rows now: its defining query's answer.
     *
     * @param dynamicTable the dynamic table
     * @return the rows
     */
    public List<Row> dynamicTableRows(final DynamicTable dynamicTable) {
        return planDetachedBody(dynamicTable.getQuery()).run().getRows();
    }

    /**
     * The rows a stage's directory table lists now, one per staged file.
     *
     * @param stageName the stage's qualified name
     * @return the rows: RELATIVE_PATH, SIZE, LAST_MODIFIED, MD5, ETAG and FILE_URL
     */
    public List<Row> stageDirectoryRows(final String stageName) {
        return new StageQueryExecutor(this).directoryListing("@" + stageName);
    }

    private List<Object> normalizedRowKey(final Row row) {
        return normalizedValues(row.getValues());
    }

    private List<Object> normalizedValues(final List<Object> values) {
        final List<Object> normalized = new ArrayList<>(values.size());
        for (final Object value : values) {
            normalized.add(value == null ? null : ValueComparisons.normalizeValueForDistinct(value));
        }
        return normalized;
    }

    /** How many column positions two view rows agree on (normalized), for update-pairing. */
    private int agreementCount(final Row left, final Row right) {
        final List<Object> a = normalizedRowKey(left);
        final List<Object> b = normalizedRowKey(right);
        int agree = 0;
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            if (Objects.equals(a.get(i), b.get(i))) {
                agree++;
            }
        }
        return agree;
    }

    /** One stream output row: the view row's values plus the METADATA$ columns. */
    private Row streamOutputRow(final Row viewRow, final ChangeType action, final boolean isUpdate,
                                final long rowId) {
        final List<Object> values = new ArrayList<>(viewRow.getValues());
        appendStreamMetadataValues(values,
            new StreamRecord(viewRow.getValues(), action, isUpdate, rowId));
        return new Row(values);
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
            if (branchIsJoin(branch)) {
                tables.addAll(branchJoinTableNames(branch));
            } else {
                tables.add(branchBaseTable(branch));
            }
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
            + ": change tracking supports projections, filters, inner/cross joins, and UNION ALL over"
            + " single-table branches (SELECT ... FROM t [JOIN u ON ...] [WHERE ...] [UNION ALL ...]);"
            + " plain UNION, DISTINCT, GROUP BY, QUALIFY, LIMIT, outer joins, joins inside UNION ALL"
            + " branches, and subquery sources are not supported";
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
            // Snowflake's own wording for the plain-UNION case (live-verified).
            if (op.UNION() == null || op.ALL() == null) {
                throw new RuntimeException(
                    "Change tracking is not supported on queries with joins of type '[UNION]'.");
            }
        }
        final List<FrostlakeParser.SelectClauseContext> branches = new ArrayList<>();
        for (final FrostlakeParser.SelectOperandContext operand : sel.selectOperand()) {
            final FrostlakeParser.SelectClauseContext clause = operand.selectClause();
            if (clause != null && clause.groupByClause() != null) {
                // Snowflake's own wording for the aggregate case (live-verified).
                throw new RuntimeException(
                    "Change tracking is not supported on queries with GROUP BY.");
            }
            if (clause == null || clause.DISTINCT() != null
                    || clause.havingClause() != null || clause.qualifyClause() != null
                    || clause.tableExpression() == null) {
                throw new RuntimeException(reject);
            }
            final FrostlakeParser.TableExpressionContext tableExpr = clause.tableExpression();
            if (branchIsJoin(clause)) {
                // An INNER / CROSS join view is change-trackable (live supports it; outer joins are
                // rejected with Snowflake's own per-type wording). Joins stay single-branch: a join
                // inside a UNION ALL arm keeps the generic rejection.
                if (sel.selectOperand().size() > 1) {
                    throw new RuntimeException(reject);
                }
                validateJoinViewBranch(tableExpr, reject);
            } else if (tableExpr.tableReference(0).tableSource().tableQualifiedName() == null) {
                throw new RuntimeException(reject);
            }
            branches.add(clause);
        }
        return branches;
    }

    /** Whether a view-stream branch's FROM clause joins relations (explicit JOINs or comma tables). */
    private boolean branchIsJoin(final FrostlakeParser.SelectClauseContext branch) {
        final FrostlakeParser.TableExpressionContext tableExpr = branch.tableExpression();
        return tableExpr.tableReference().size() > 1 || !tableExpr.joinClause().isEmpty();
    }

    /**
     * Validate a JOIN view-stream branch: every relation must be a plain table, and every join must
     * be INNER or CROSS. An outer join carries Snowflake's own rejection — measured:
     * "Change tracking is not supported on queries with joins of type '[LEFT_OUTER_JOIN]'."
     */
    private void validateJoinViewBranch(final FrostlakeParser.TableExpressionContext tableExpr,
                                        final String reject) {
        for (final FrostlakeParser.TableReferenceContext ref : tableExpr.tableReference()) {
            if (ref.tableSource().tableQualifiedName() == null || ref.LATERAL() != null) {
                throw new RuntimeException(reject);
            }
        }
        for (final FrostlakeParser.JoinClauseContext join : tableExpr.joinClause()) {
            if (join.NATURAL() != null || join.ASOF() != null || join.LATERAL() != null
                    || join.tableReference().tableSource().tableQualifiedName() == null) {
                throw new RuntimeException(reject);
            }
            final FrostlakeParser.JoinTypeContext type = join.joinType();
            if (type != null && (type.LEFT() != null || type.RIGHT() != null || type.FULL() != null)) {
                final String kind = type.LEFT() != null ? "LEFT_OUTER_JOIN"
                    : type.RIGHT() != null ? "RIGHT_OUTER_JOIN" : "FULL_OUTER_JOIN";
                throw new RuntimeException(
                    "Change tracking is not supported on queries with joins of type '[" + kind + "]'.");
            }
        }
    }

    /** Every base table of a JOIN view-stream branch (first relation + each join's right side),
     *  fully qualified as written, upper-cased. */
    private List<String> branchJoinTableNames(final FrostlakeParser.SelectClauseContext branch) {
        final List<String> names = new ArrayList<>();
        final FrostlakeParser.TableExpressionContext tableExpr = branch.tableExpression();
        for (final FrostlakeParser.TableReferenceContext ref : tableExpr.tableReference()) {
            names.add(qualifiedUpper(ref.tableSource().tableQualifiedName()));
        }
        for (final FrostlakeParser.JoinClauseContext join : tableExpr.joinClause()) {
            names.add(qualifiedUpper(join.tableReference().tableSource().tableQualifiedName()));
        }
        return names;
    }

    private String qualifiedUpper(final FrostlakeParser.TableQualifiedNameContext ctx) {
        final String[] parts = ParseTreeText.qualifiedNameParts(ctx);
        final StringBuilder qualified = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                qualified.append('.');
            }
            qualified.append(parts[i].toUpperCase());
        }
        return qualified.toString();
    }

    /** Bare (upper-case) base table name of a single view-stream branch. */
    private String branchBaseTable(final FrostlakeParser.SelectClauseContext branch) {
        // Keep the FULL qualification (e.g. BASE.FACT_X), not just the last segment: a stream ON a VIEW
        // whose base tables live in a DIFFERENT schema than the read context (e.g. a BASE_TRANSFORM view
        // over BASE.* tables) must resolve them by their own schema, not the current one. capturesTable()
        // already reduces a qualified capture name to its last segment before matching, so this is safe.
        final String[] parts = ParseTreeText.qualifiedNameParts(
            branch.tableExpression().tableReference(0).tableSource().tableQualifiedName());
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

    /**
     * The LIMIT/OFFSET window: {@code {limit, offset}}. NULL and the EMPTY string mean "no limit" (and an
     * empty OFFSET string means zero) — any other string literal is refused at its own position exactly
     * as the account refuses it; binds were inlined before parse.
     */
    private static int[] limitWindow(final FrostlakeParser.LimitClauseContext ctx) {
        int limit = Integer.MAX_VALUE;
        int offset = 0;
        boolean inOffset = false;
        for (final ParseTree child : ctx.children) {
            if (!(child instanceof TerminalNode)) {
                continue;
            }
            final Token token = ((TerminalNode) child).getSymbol();
            if (token.getType() == FrostlakeParser.OFFSET) {
                inOffset = true;
            } else if (token.getType() == FrostlakeParser.INTEGER_LITERAL) {
                if (inOffset) {
                    offset = Integer.parseInt(token.getText());
                } else {
                    limit = Integer.parseInt(token.getText());
                }
            } else if (token.getType() == FrostlakeParser.STRING_LITERAL) {
                requireEmptyLimitString(token);
            }
        }
        return new int[] {limit, offset};
    }

    /** The LIMIT/OFFSET window applied at once over {@code rows}. */
    private static List<Row> applyLimitClause(final FrostlakeParser.LimitClauseContext ctx, final List<Row> rows) {
        final int[] window = limitWindow(ctx);
        return new LimitOperator(window[0], window[1]).execute(rows, null);
    }

    /** Only the EMPTY string is a legal LIMIT/OFFSET value (its documented default), live-verified. */
    private static void requireEmptyLimitString(final Token token) {
        if (!"''".equals(token.getText())) {
            throw new RuntimeException(SqlCompilationError.of("syntax error line " + token.getLine()
                + " at position " + token.getCharPositionInLine()
                + " unexpected '" + token.getText() + "'."));
        }
    }

    /** The ANSI FETCH window as {@code {count, offset}}: an optional OFFSET-rows prefix, then the fetched count. */
    private static int[] fetchWindow(final FrostlakeParser.FetchClauseContext ctx) {
        // Each slot is read by WHERE IT SITS, not by counting INTEGER_LITERALs. NULL is legal in both
        // and means "no offset" / "no limit", so an index into the integer list silently read the FETCH
        // COUNT as the offset the moment the offset was written NULL.
        int offset = 0;
        int fetch = Integer.MAX_VALUE;
        boolean afterOffset = false;
        boolean afterFetch = false;
        for (int i = 0; i < ctx.getChildCount(); i++) {
            if (!(ctx.getChild(i) instanceof TerminalNode)) {
                continue;
            }
            final Token slot = ((TerminalNode) ctx.getChild(i)).getSymbol();
            if (slot.getType() == FrostlakeParser.OFFSET) {
                afterOffset = true;
                afterFetch = false;
            } else if (slot.getType() == FrostlakeParser.FETCH) {
                afterFetch = true;
                afterOffset = false;
            } else if (slot.getType() == FrostlakeParser.INTEGER_LITERAL) {
                if (afterOffset) {
                    offset = Integer.parseInt(slot.getText());
                } else if (afterFetch) {
                    fetch = Integer.parseInt(slot.getText());
                }
                afterOffset = false;
                afterFetch = false;
            } else if (slot.getType() == FrostlakeParser.NULL) {
                // "no offset" / "no limit" — both are the defaults already in force.
                afterOffset = false;
                afterFetch = false;
            }
        }
        return new int[] {fetch, offset};
    }

    /** The FETCH window applied at once over {@code rows}. */
    private static List<Row> applyFetchClause(final FrostlakeParser.FetchClauseContext ctx, final List<Row> rows) {
        final int[] window = fetchWindow(ctx);
        return new LimitOperator(window[0], window[1]).execute(rows, null);
    }

    /**
     * A CHANGES clause's AT/BEFORE point must be a CONSTANT — a literal, optionally negated — where
     * a plain time-travel AT accepts any expression. Live-verified refusal sentence; the found
     * clause echoes the source text where the account renders its internal resolved spelling.
     */
    private void requireConstantChangesPoint(final FrostlakeParser.TimeTravelPointContext ctx,
                                             final String clauseKeyword) {
        if (ctx.STREAM() != null || ctx.expression() == null) {
            return;
        }
        final String text = getOriginalText(ctx.expression());
        final Expression parsed = AntlrExpressionParser.parse(text);
        boolean constant = parsed instanceof LiteralExpression;
        if (parsed instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) parsed;
            constant = (unary.getOperator() == UnaryOperator.NEGATE
                || unary.getOperator() == UnaryOperator.PLUS)
                && unary.getOperand() instanceof LiteralExpression;
        }
        if (!constant) {
            final String argument = ctx.TIMESTAMP() != null ? "TIMESTAMP"
                : ctx.OFFSET() != null ? "OFFSET" : "STATEMENT";
            throw new RuntimeException(SqlCompilationError.of("argument " + argument
                + " to function " + clauseKeyword + " needs to be constant, found '" + text + "'"));
        }
    }

    /** Resolve a timeTravelPoint context to epoch millis. */
    private long resolveTimeTravelPoint(final FrostlakeParser.TimeTravelPointContext ctx) {
        final String valueExpr = getOriginalText(ctx.expression());
        final Object value = evaluateExpression(valueExpr, null, (Table) null);

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
            if (value instanceof OffsetDateTime) {
                return ((OffsetDateTime) value).toInstant().toEpochMilli();
            }
            if (value instanceof ZonedDateTime) {
                return ((ZonedDateTime) value).toInstant().toEpochMilli();
            }
            if (value instanceof Number) {
                // A bare number is read by magnitude — seconds, then milli/micro/nanoseconds —
                // exactly as TO_TIMESTAMP reads one (live-verified: 253402300799 is a 1978
                // MILLISECOND instant, not the year-9999 second). Seconds keep the +999ms so all
                // sub-second snapshots inside that second stay visible.
                final long raw = ((Number) value).longValue();
                if (raw < 31536000000L) {
                    return raw * 1000L + 999L;
                }
                if (raw < 31536000000000L) {
                    return raw;
                }
                if (raw < 31536000000000000L) {
                    return raw / 1000L;
                }
                return raw / 1000000L;
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
            final long offsetSeconds = ((Number) value).longValue();
            if (offsetSeconds > 0) {
                // A positive offset is refused at compile time with the value echoed twice,
                // live-verified shape and spelling.
                String keyword = "AT";
                if (ctx.getParent() instanceof FrostlakeParser.TimeTravelClauseContext
                        && ((FrostlakeParser.TimeTravelClauseContext) ctx.getParent()).BEFORE() != null
                        || ctx.getParent() instanceof FrostlakeParser.StreamPointContext
                        && ((FrostlakeParser.StreamPointContext) ctx.getParent()).BEFORE() != null) {
                    keyword = "BEFORE";
                }
                throw new RuntimeException(SqlCompilationError.of("Invalid data type ["
                    + offsetSeconds + "] in " + keyword + "(OFFSET => " + valueExpr.trim() + ")"));
            }
            return System.currentTimeMillis() + offsetSeconds * 1000L;
        } else if (ctx.STATEMENT() != null) {
            // STATEMENT => '<query_id>' — use snapshot closest to when that query ran
            final String queryId = value.toString();
            final QueryHistory qh = queryHistoryTracker.getQueryById(queryId);
            if (qh != null && qh.getStartTime() != null) {
                return qh.getStartTime().toInstant(ZoneOffset.UTC).toEpochMilli();
            }
            throw new RuntimeException("Query ID not found in history: " + queryId);
        } else if (ctx.STREAM() != null) {
            // Only CHANGES understands a stream offset; AT/BEFORE time travel over a plain table
            // does not, and never reaches here for CHANGES (see executeChangesClause).
            throw new RuntimeException(SqlCompilationError.of(
                "Unsupported feature 'Time travel AT (STREAM => ...)'."));
        }
        throw new RuntimeException("Unknown time travel point type");
    }

    /**
     * The instant a {@code STATEMENT => '<query id>'} point reads the table at. The point is the statement
     * itself, so the two sides of it differ: AT reads the table WITH the statement's changes, BEFORE reads it
     * without them. AT is therefore anchored on the statement's completion and BEFORE on its start — the
     * start alone missed the statement's own write whenever the write landed a millisecond after it began.
     *
     * <p>Only a statement that changed data can anchor time travel: a DDL statement's id is refused, in the
     * account's words.
     *
     * @param ctx      the time travel point
     * @param isBefore whether the clause is BEFORE rather than AT
     * @return the instant, in epoch milliseconds
     */
    private long resolveStatementPoint(final FrostlakeParser.TimeTravelPointContext ctx, final boolean isBefore) {
        final String queryId = String.valueOf(evaluateExpression(getOriginalText(ctx.expression()), null, (Table) null));
        final QueryHistory qh = queryHistoryTracker.getQueryById(queryId);
        // Both refusals are the account's plain sentences, with no compilation-error prefix.
        if (qh == null || qh.getStartTime() == null) {
            throw new RuntimeException("Statement " + queryId + " not found");
        }
        if (anchorsNoTimeTravel(qh)) {
            throw new RuntimeException("Statement " + queryId
                + " cannot be used to specify time for time travel query.");
        }
        final LocalDateTime anchor = isBefore ? qh.getStartTime()
            : qh.getVisibleFrom() != null ? qh.getVisibleFrom()
            : qh.getEndTime() != null ? qh.getEndTime() : qh.getStartTime();
        return anchor.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    /**
     * Where a {@code STATEMENT => '<query id>'} point falls in the order the snapshots were taken in: for BEFORE,
     * the latest snapshot taken when the statement began; for AT, the latest once its changes became visible.
     *
     * @param ctx      the time travel point, already resolved by {@link #resolveStatementPoint}
     * @param isBefore whether the clause is BEFORE rather than AT
     * @return the sequence number, or -1 when the statement's changes are not visible yet
     */
    private long statementSequence(final FrostlakeParser.TimeTravelPointContext ctx, final boolean isBefore) {
        final String queryId = String.valueOf(evaluateExpression(getOriginalText(ctx.expression()), null, (Table) null));
        final QueryHistory qh = queryHistoryTracker.getQueryById(queryId);
        if (qh == null) {
            return -1L;
        }
        return isBefore ? qh.getStartSequence() : qh.getVisibleSequence();
    }

    /** Whether a statement's kind cannot anchor time travel: a DDL statement changes no table's data. */
    private static boolean anchorsNoTimeTravel(final QueryHistory qh) {
        switch (qh.getQueryType()) {
            case "CREATE":
            case "DROP":
            case "ALTER":
            case "GRANT":
            case "REVOKE":
                return true;
            default:
                return false;
        }
    }

    /** Execute CHANGES clause — returns inserted/updated/deleted rows between two snapshots. */
    /** The evaluable source text of a named argument's value; a bare subquery value
     *  ({@code INPUT => SELECT ...}) is parenthesized into the scalar-subquery expression form. */
    private String namedArgumentText(final FrostlakeParser.NamedArgumentContext argCtx) {
        if (argCtx.argumentRow() != null) {
            return getOriginalText(argCtx.argumentRow());
        }
        return argCtx.expression() != null
            ? getOriginalText(argCtx.expression())
            : "(" + getOriginalText(argCtx.selectStatement()) + ")";
    }

    /**
     * CHANGES … AT (STREAM =&gt; '&lt;name&gt;') — the change set the named stream currently reports, as
     * data columns plus METADATA$ACTION / METADATA$ISUPDATE / METADATA$ROW_ID. Reading it here does
     * NOT advance the stream. A stream over a different table simply contributes nothing, matching a
     * real account, which answers no rows rather than an error.
     */
    private List<Row> changesFromStreamOffset(final FrostlakeParser.ChangesClauseContext ctx,
                                              final Table table) {
        final String streamName = SqlStringLiterals.decode(
            getOriginalText(ctx.timeTravelPoint(0).expression()).trim());
        final Stream stream = findStream(streamName);
        if (stream == null) {
            // The name is echoed exactly as written, live-verified.
            throw new RuntimeException("Stream '" + streamName + "' not found.");
        }
        // The END point is still checked: a window running into the future is refused before any
        // change is read, live-verified.
        if (ctx.END() != null && ctx.timeTravelPoint().size() > 1) {
            final long endMillis =
                resolveTimeTravelPoint(ctx.timeTravelPoint(ctx.timeTravelPoint().size() - 1));
            if (endMillis > System.currentTimeMillis()) {
                throw new RuntimeException(
                    "Future data is not yet available for table " + table.getName() + ".");
            }
        }
        final boolean appendOnly = changesAppendOnly(ctx);
        final String targetBareName = table.getName().toUpperCase();
        final List<Row> rows = new ArrayList<>();
        final List<StreamRecord> changes = appendOnly
            ? unconsumedAppendRecords(stream) : unconsumedNetRecords(stream);
        for (final StreamRecord record : changes) {
            if (!streamRecordTargets(record, stream, targetBareName)) {
                continue;
            }
            final List<Object> values = new ArrayList<>(record.getValues());
            appendStreamMetadataValues(values, record);
            rows.add(new Row(values));
        }
        return rows;
    }

    /** {@code table} widened with METADATA$ACTION / METADATA$ISUPDATE / METADATA$ROW_ID. */
    private Table changeTrackingTable(final Table table) {
        final List<TableColumn> columns = new ArrayList<>(table.getColumns());
        appendStreamMetadataColumns(columns);
        return new Table(table.getName(), columns, false);
    }

    /** Whether a change record belongs to the table CHANGES is being read over. */
    private boolean streamRecordTargets(final StreamRecord record, final Stream stream,
                                        final String targetBareName) {
        final String source = record.getSourceTable() != null
            ? record.getSourceTable() : stream.getSourceTableName();
        return source != null
            && QualifiedName.parse(source).last().toUpperCase().equals(targetBareName);
    }

    /** CHANGES (INFORMATION =&gt; APPEND_ONLY) keeps only the inserts; DEFAULT keeps the whole delta. */
    private boolean changesAppendOnly(final FrostlakeParser.ChangesClauseContext ctx) {
        return ctx.identifier() != null
            && "APPEND_ONLY".equalsIgnoreCase(getOriginalText(ctx.identifier()).trim());
    }

    private List<Row> executeChangesClause(final FrostlakeParser.ChangesClauseContext ctx,
                                            final TableStorage storage,
                                            final Table table) {
        // AT (STREAM => '<name>') starts from a stream's own offset. That change set is what the
        // stream itself reports, so it is taken from the stream rather than re-derived by diffing
        // snapshots — CHANGES only READS it, leaving the stream's offset where it was.
        if (ctx.AT_KEYWORD() != null && ctx.timeTravelPoint(0) != null
                && ctx.timeTravelPoint(0).STREAM() != null) {
            return changesFromStreamOffset(ctx, table);
        }

        // Resolve start point
        final long startMillis;
        if (ctx.AT_KEYWORD() != null && ctx.timeTravelPoint(0) != null) {
            requireConstantChangesPoint(ctx.timeTravelPoint(0), "AT");
            startMillis = resolveTimeTravelPoint(ctx.timeTravelPoint(0));
        } else if (ctx.BEFORE() != null && ctx.timeTravelPoint(0) != null) {
            requireConstantChangesPoint(ctx.timeTravelPoint(0), "BEFORE");
            startMillis = resolveTimeTravelPoint(ctx.timeTravelPoint(0)) - 1;
        } else {
            startMillis = 0;
        }
        // The same lower bound a plain AT/BEFORE enforces: a point before the table came into
        // being answers the retention wording (live-verified for CHANGES too).
        if (startMillis > 0 && startMillis < storage.getCreatedMillis()) {
            throw new RuntimeException("Time travel data is not available for table "
                + table.getName() + ". The requested time is either beyond the allowed time"
                + " travel period or before the object creation time.");
        }

        // Resolve end point (default = now)
        long endMillis = System.currentTimeMillis();
        if (ctx.END() != null && ctx.timeTravelPoint().size() > 1) {
            endMillis = resolveTimeTravelPoint(ctx.timeTravelPoint(ctx.timeTravelPoint().size() - 1));
        } else if (ctx.timeTravelPoint().size() == 2) {
            endMillis = resolveTimeTravelPoint(ctx.timeTravelPoint(1));
        }

        final TableSnapshot startSnap = storage.snapshotAt(startMillis);
        final TableSnapshot endSnap   = storage.snapshotAt(endMillis);

        final Set<String> startKeys = new HashSet<>();
        final Map<String, Row> startMap = new LinkedHashMap<>();
        if (startSnap != null) {
            for (final Row r : startSnap.rows) {
                final String key = r.getValues().toString();
                startKeys.add(key);
                startMap.put(key, r);
            }
        }

        final List<Row> changes = new ArrayList<>();
        final Set<String> endKeys = new HashSet<>();
        if (endSnap != null) {
            for (final Row r : endSnap.rows) {
                final String key = r.getValues().toString();
                endKeys.add(key);
                if (!startKeys.contains(key)) {
                    // Inserted row — add METADATA$ACTION = INSERT
                    changes.add(withChangeMetadata(r, "INSERT", "False"));
                }
            }
        }
        if (startSnap != null) {
            for (final Row r : startSnap.rows) {
                final String key = r.getValues().toString();
                if (!endKeys.contains(key)) {
                    // Deleted row — add METADATA$ACTION = DELETE
                    changes.add(withChangeMetadata(r, "DELETE", "True"));
                }
            }
        }
        return changes;
    }

    private Row withChangeMetadata(final Row base, final String action, final String isUpdate) {
        final List<Object> vals = new ArrayList<>(base.getValues());
        vals.add(action);
        vals.add(isUpdate);
        return new Row(vals);
    }

    /**
     * Execute a simple CROSS JOIN
     */
    private List<Row> executeCrossJoin(final List<Row> leftRows, final List<Row> rightRows) {
        final List<Row> resultRows = new ArrayList<>();
        for (final Row leftRow : leftRows) {
            for (final Row rightRow : rightRows) {
                final List<Object> combinedValues = new ArrayList<>(leftRow.getValues());
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
            // By the exact canonical name, as a CALL resolves it.
            final List<Procedure> overloads = schema.getProcedureOverloads(getIdentifier(ids.get(ids.size() - 1)));
            return overloads.size() == 1 ? overloads.get(0) : null;
        } catch (final RuntimeException notAProcedure) {
            return null;
        }
    }

    /**
     * Execute a table function call
     */
    /**
     * Execute a bare table-function FROM source (SPLIT_TO_TABLE(x, ','), GENERATOR(rowcount => n)),
     * reading the call straight off the {@code tableFunctionExpr} parse node rather than a wrapping
     * expression. Positional and named arguments mirror the TABLE(function_call) handling.
     */
    /**
     * Canonical simple (last) name of a function-call name, read from the name's identifier PARTS —
     * never by re-splitting the flattened text, which breaks a quoted part containing a dot
     * ({@code s."a.b"()}) and keeps quotes a canonical lookup must fold away
     * ({@code INFORMATION_SCHEMA."QUERY_HISTORY"()}). Keyword-named forms (LIKE / ILIKE /
     * IDENTIFIER(...)) have no identifier parts and fold as their bare text.
     */
    private String functionSimpleName(final FrostlakeParser.FunctionNameContext nameCtx) {
        final List<FrostlakeParser.IdentifierContext> ids = nameCtx.identifier();
        if (ids == null || ids.isEmpty()) {
            return nameCtx.getText().toUpperCase();
        }
        return getIdentifier(ids.get(ids.size() - 1));
    }

    /**
     * Statement kind from the PARSED statement: the query-statement context covers the
     * parenthesized and CTE shapes a leading-keyword check misses, and everything else classifies
     * by its leading TOKEN — comments live on the hidden channel, so a leading comment no longer
     * demotes a SELECT to OTHER.
     */
    private StatementKind statementKindOf(final FrostlakeParser.StatementContext stmt) {
        if (stmt.queryStatement() != null) {
            return StatementKind.SELECT;
        }
        switch (stmt.getStart().getType()) {
            case FrostlakeParser.INSERT: return StatementKind.INSERT;
            case FrostlakeParser.UPDATE: return StatementKind.UPDATE;
            case FrostlakeParser.DELETE: return StatementKind.DELETE;
            case FrostlakeParser.MERGE: return StatementKind.MERGE;
            case FrostlakeParser.COPY: return StatementKind.COPY;
            case FrostlakeParser.CREATE: return StatementKind.CREATE;
            case FrostlakeParser.DROP: return StatementKind.DROP;
            case FrostlakeParser.ALTER: return StatementKind.ALTER;
            case FrostlakeParser.TRUNCATE: return StatementKind.TRUNCATE;
            case FrostlakeParser.GRANT: return StatementKind.GRANT;
            case FrostlakeParser.REVOKE: return StatementKind.REVOKE;
            case FrostlakeParser.SHOW: return StatementKind.SHOW;
            case FrostlakeParser.DESCRIBE:
            case FrostlakeParser.DESC: return StatementKind.DESCRIBE;
            case FrostlakeParser.USE: return StatementKind.USE;
            case FrostlakeParser.BEGIN: return StatementKind.BEGIN;
            case FrostlakeParser.COMMIT: return StatementKind.COMMIT;
            case FrostlakeParser.ROLLBACK: return StatementKind.ROLLBACK;
            case FrostlakeParser.CALL: return StatementKind.CALL;
            case FrostlakeParser.WITH: return StatementKind.SELECT;
            default: return StatementKind.OTHER;
        }
    }

    /** Canonical schema part written immediately before the function's own name, or empty when the
     *  call is unqualified (or keyword-named). */
    private String schemaQualifierOf(final FrostlakeParser.FunctionNameContext nameCtx) {
        final String[] parts = ParseTreeText.functionNameParts(nameCtx);
        return parts == null || parts.length < 2 ? "" : parts[parts.length - 2];
    }

    /**
     * A bare table-function call — {@code LATERAL SPLIT_TO_TABLE(x, ',')} — as the planner reads it: the
     * arguments are evaluated here, the function runs when its result is first asked for.
     */
    private PlannedTableFunction planBareTableFunction(final FrostlakeParser.TableFunctionExprContext ctx,
                                                       final Map<String, Object> lateralContext) {
        final String rawName = ctx.functionName().getText();
        final String functionName = functionSimpleName(ctx.functionName());
        final TableFunction tableFunc = functionRegistry.getTableFunction(functionName);
        if (tableFunc == null) {
            throw new RuntimeException(SqlCompilationError.of("Unknown table function " + rawName));
        }
        final String callText = getOriginalText(ctx);
        final boolean hasNamed = ctx.namedArgument() != null && !ctx.namedArgument().isEmpty();
        if (hasNamed) {
            final Map<String, Object> namedArgs = new HashMap<>();
            validateTableFunctionCall(functionName, schemaQualifierOf(ctx.functionName()),
                ctx.functionName().getStart(), ctx.expression(), ctx.namedArgument());
            // A leading positional argument fills the function's first parameter — INPUT for
            // FLATTEN, SQL for TO_QUERY.
            if (ctx.expression() != null && !ctx.expression().isEmpty()) {
                namedArgs.put(TableFunctionArguments.firstPositionalParameter(functionName),
                    evaluateBareArg(ctx.expression(0), lateralContext));
            }
            for (final FrostlakeParser.NamedArgumentContext arg : ctx.namedArgument()) {
                namedArgs.put(arg.identifier().getText().toUpperCase(),
                    evaluateNamedTableFunctionArgument(arg, lateralContext));
            }
            return plannedCall(functionName, callText, tableFunc, namedArgs);
        }
        validateTableFunctionCall(functionName, schemaQualifierOf(ctx.functionName()),
            ctx.functionName().getStart(), ctx.expression(), null);
        if (ctx.expression() == null || ctx.expression().isEmpty()) {
            return plannedCall(functionName, callText, tableFunc, new HashMap<>());
        }
        final List<Object> positionalArgs = new ArrayList<>();
        for (final FrostlakeParser.ExpressionContext argExpr : ctx.expression()) {
            positionalArgs.add(evaluateBareArg(argExpr, lateralContext));
        }
        // The function's columns do not depend on which parameters the positional arguments fill.
        return new PlannedTableFunction(functionName, callText, new MemoizedResultSet(new ResultSetProvider() {
            @Override
            public ResultSet getResultSet() {
                return tableFunc.execute(positionalArgs);
            }
        }), tableFunc.outputColumns(new HashMap<String, Object>()));
    }

    /**
     * The argument rules of a table function written as a LATERAL item's source — {@code TABLE(f(...))},
     * a bare {@code f(...)} and the FLATTEN shorthand alike — run where the relations the arguments may
     * name are in scope. A source that is not a table function has nothing to judge here.
     *
     * @param ref the LATERAL item's reference
     */
    private void validateLateralTableFunctionTypes(final FrostlakeParser.TableReferenceContext ref) {
        final FrostlakeParser.TableSourceContext source = ref.tableSource();
        if (source == null) {
            return;
        }
        if (source.tableFunctionExpr() != null) {
            final FrostlakeParser.TableFunctionExprContext call = source.tableFunctionExpr();
            validateTableFunctionCall(functionSimpleName(call.functionName()),
                schemaQualifierOf(call.functionName()), call.functionName().getStart(),
                call.expression(), call.namedArgument());
            return;
        }
        if (source.TABLE() == null || source.expression() == null) {
            return;
        }
        final FrostlakeParser.ExpressionContext call = source.expression();
        if (call instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext plain = (FrostlakeParser.FunctionCallExprContext) call;
            validateKnownTableFunction(plain.functionName(), funcArgExprs(plain), null);
        } else if (call instanceof FrostlakeParser.FunctionCallNamedArgsExprContext) {
            final FrostlakeParser.FunctionCallNamedArgsExprContext named =
                (FrostlakeParser.FunctionCallNamedArgsExprContext) call;
            if (named.namedArgumentList() != null) {
                validateKnownTableFunction(named.functionName(), null,
                    named.namedArgumentList().namedArgument());
            }
        } else if (call instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            final FrostlakeParser.FunctionCallMixedArgsExprContext mixed =
                (FrostlakeParser.FunctionCallMixedArgsExprContext) call;
            validateKnownTableFunction(mixed.functionName(), mixed.expression(), mixed.namedArgument());
        }
    }

    /** The argument rules of {@code name}, when it names a table function this engine knows. */
    private void validateKnownTableFunction(final FrostlakeParser.FunctionNameContext name,
                                            final List<FrostlakeParser.ExpressionContext> positional,
                                            final List<FrostlakeParser.NamedArgumentContext> named) {
        final String functionName = functionSimpleName(name);
        if (functionRegistry.getTableFunction(functionName) == null) {
            return;
        }
        validateTableFunctionCall(functionName, schemaQualifierOf(name), name.getStart(), positional, named);
    }

    /**
     * Refuse a table-function call whose argument NAMES, argument COUNT or argument TYPES the function
     * does not take — all three are compile-time refusals on live, raised before a row is read, and in
     * that order (an unexpected name is reported even when the call is also short of arguments).
     *
     * <p>The type rule reads the argument's DECLARED type, not the value it produces, which is what
     * separates {@code FLATTEN(INPUT =&gt; '[1,2,3]')} — refused, a VARCHAR — from
     * {@code FLATTEN(INPUT =&gt; PARSE_JSON('[1,2,3]'))}. An argument that NAMES a column is typed from
     * the relations in scope where there are any; one whose type cannot be determined at all is let
     * through, so this can never reject a call live accepts.
     *
     * @param positional the positional arguments in order, or null when there are none
     * @param named      the named arguments in order, or null when there are none
     */
    private void validateTableFunctionCall(final String functionName, final String schemaQualifier,
                                           final Token nameToken,
                                           final List<FrostlakeParser.ExpressionContext> positional,
                                           final List<FrostlakeParser.NamedArgumentContext> named) {
        TableFunctionArguments.validateQualification(functionName, schemaQualifier);
        final List<String> namesInOrder = new ArrayList<>();
        final List<String> argumentTexts = new ArrayList<>();
        if (positional != null) {
            for (final FrostlakeParser.ExpressionContext argExpr : positional) {
                namesInOrder.add(null);
                argumentTexts.add(getOriginalText(argExpr));
            }
        }
        if (named != null) {
            for (final FrostlakeParser.NamedArgumentContext arg : named) {
                namesInOrder.add(arg.identifier().getText().toUpperCase());
                argumentTexts.add(namedArgumentText(arg));
            }
        }
        TableFunctionArguments.validateNames(functionName, nameToken, namesInOrder);
        if (InferSchemaArguments.FUNCTION.equals(functionName)) {
            new InferSchemaArguments(this, new ExpressionEvaluator(null, functionRegistry, catalog, this))
                .validate(nameToken, positional, named);
            return;
        }
        if (ArgumentRows.present(named)) {
            ArgumentRows.refuse(functionName, positional == null ? 0 : positional.size(), named,
                new ExpressionEvaluator(null, functionRegistry, catalog, this));
        }
        if (named == null || named.isEmpty()) {
            TableFunctionArguments.validateArity(functionName, nameToken, argumentTexts);
        }
        for (int i = 0; i < namesInOrder.size(); i++) {
            final String written = namesInOrder.get(i);
            validateTableFunctionArgumentType(
                written == null ? TableFunctionArguments.kindOfPositional(functionName, i)
                    : TableFunctionArguments.kindOfNamed(functionName, written),
                written == null ? TableFunctionArguments.nameOfPositional(functionName, i) : written,
                argumentTexts.get(i));
        }
    }

    /** Refuse one table-function argument whose declared type is not what its parameter takes. */
    private void validateTableFunctionArgumentType(final TableFunctionParameterKind kind,
                                                   final String parameterName,
                                                   final String argumentText) {
        if (kind == null) {
            return;
        }
        final Expression argument = ExpressionEvaluator.parse(argumentText);
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(null, functionRegistry, catalog, this);
        // A LATERAL item's argument may NAME a column, and the column's declared type is what the rule is
        // about: without the relations in scope only a literal could ever be typed, so a TIMESTAMP column
        // passed where a text is wanted went unchecked and the value was split as Java spells it.
        lateralArgumentTypes.typeArgumentsOf(evaluator);
        final DataType declared = evaluator.inferStaticType(argument);
        if (declared == null || kind.accepts(declared)) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.of("invalid type ["
            + evaluator.argumentTypeText(argument) + "] for parameter '" + parameterName + "'"));
    }

    /** Evaluate one bare table-function argument, honouring any lateral (per-row) context. */
    private Object evaluateBareArg(final FrostlakeParser.ExpressionContext argExpr,
                                   final Map<String, Object> lateralContext) {
        return evaluateTableFunctionArgument(argExpr, getOriginalText(argExpr), lateralContext);
    }

    private ResultSet executeTableFunction(final FrostlakeParser.ExpressionContext expr) {
        return executeTableFunction(expr, null);
    }

    /**
     * The alias a FROM reference gives its source, by its canonical name: a quoted one exactly as quoted, an
     * unquoted one upper-cased, so a qualifier naming it resolves by that name alone (live-verified: "fz".*
     * reads FZ AS "fz", fz.* does not). Null when none is written.
     */
    private static String sourceAlias(final FrostlakeParser.TableReferenceContext ctx) {
        if (ctx.aliasName() != null) {
            return ParseTreeText.getIdentifier(ctx.aliasName());
        }
        if (ctx.nonJoinKeywordIdentifier() != null) {
            return ctx.nonJoinKeywordIdentifier().identifier() != null
                ? ParseTreeText.getIdentifier(ctx.nonJoinKeywordIdentifier().identifier())
                : ctx.nonJoinKeywordIdentifier().getText().toUpperCase(Locale.ROOT);
        }
        return null;
    }

    /** The SAMPLE / TABLESAMPLE stage over a planned source's rows. */
    private Operator sampleStage(final FrostlakeParser.SampleClauseContext sample) {
        return new StageOperator("SAMPLE[" + getOriginalText(sample) + "]") {
            @Override
            protected List<Row> apply(final List<Row> input) {
                return sampleRows(input, sample);
            }
        };
    }

    /**
     * A table-function call as the planner reads it: the arguments are evaluated here, the function itself runs
     * when its result is first asked for, and the columns it will answer are known here when the function can
     * tell them without running.
     */
    private PlannedTableFunction planTableFunction(final FrostlakeParser.ExpressionContext expr,
                                                   final Map<String, Object> lateralContext) {
        final String callText = getOriginalText(expr);
        // Handle mixed positional+named args: FLATTEN(col, outer => true)
        if (expr instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            final FrostlakeParser.FunctionCallMixedArgsExprContext mixCtx = (FrostlakeParser.FunctionCallMixedArgsExprContext) expr;
            final String rawName = mixCtx.functionName().getText();
            final String functionName = functionSimpleName(mixCtx.functionName());
            final TableFunction tableFunc = functionRegistry.getTableFunction(functionName);
            if (tableFunc == null) throw new RuntimeException(SqlCompilationError.of("Unknown table function " + rawName));
            final Map<String, Object> namedArgs = new HashMap<>();
            validateTableFunctionCall(functionName.toUpperCase(), schemaQualifierOf(mixCtx.functionName()),
                mixCtx.functionName().getStart(),
                mixCtx.expression(), mixCtx.namedArgument());
            // the one leading positional argument
            final Object posVal = evaluateTableFunctionArgument(mixCtx.expression(0),
                getOriginalText(mixCtx.expression(0)), lateralContext);
            namedArgs.put(TableFunctionArguments.firstPositionalParameter(functionName.toUpperCase()), posVal);
            for (final FrostlakeParser.NamedArgumentContext argCtx : mixCtx.namedArgument()) {
                final String argName = argCtx.identifier().getText().toUpperCase();
                final Object argValue = evaluateNamedTableFunctionArgument(argCtx, lateralContext);
                namedArgs.put(argName, argValue);
            }
            return plannedCall(functionName, callText, tableFunc, namedArgs);
        }
        // Check if it's a function call with named arguments (e.g., GENERATOR)
        if (expr instanceof FrostlakeParser.FunctionCallNamedArgsExprContext) {
            final FrostlakeParser.FunctionCallNamedArgsExprContext funcCtx =
                (FrostlakeParser.FunctionCallNamedArgsExprContext) expr;
            final String rawName = funcCtx.functionName().getText();
            final String functionName = functionSimpleName(funcCtx.functionName());
            final TableFunction tableFunc = functionRegistry.getTableFunction(functionName);
            if (tableFunc == null) {
                throw new RuntimeException(SqlCompilationError.of("Unknown table function " + rawName));
            }
            // Parse named arguments
            final Map<String, Object> namedArgs = new HashMap<>();
            if (funcCtx.namedArgumentList() != null) {
                validateTableFunctionCall(functionName.toUpperCase(),
                    schemaQualifierOf(funcCtx.functionName()),
                    funcCtx.functionName().getStart(), null,
                    funcCtx.namedArgumentList().namedArgument());
                for (final FrostlakeParser.NamedArgumentContext argCtx : funcCtx.namedArgumentList().namedArgument()) {
                    final String argName = argCtx.identifier().getText().toUpperCase();
                    // If lateral context is provided, column references resolve from it
                    final Object argValue = evaluateNamedTableFunctionArgument(argCtx, lateralContext);
                    namedArgs.put(argName, argValue);
                }
            }
            return plannedCall(functionName, callText, tableFunc, namedArgs);
        }
        // Check if it's a regular function call (e.g., RESULT_SCAN)
        if (expr instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext funcCtx =
                (FrostlakeParser.FunctionCallExprContext) expr;
            final String functionName = functionSimpleName(funcCtx.functionName());
            // Special handling for RESULT_SCAN
            if ("RESULT_SCAN".equals(functionName)) {
                final ResultScan resultScan =
                    (ResultScan) functionRegistry.getTableFunction("RESULT_SCAN");
                if (resultScan == null) {
                    throw new RuntimeException("RESULT_SCAN table function not available");
                }
                // No-arg RESULT_SCAN() scans the most recent query, exactly like
                // RESULT_SCAN(LAST_QUERY_ID()) — live-verified. An argument is judged as written first:
                // a whole number names a statement as LAST_QUERY_ID's index does, and the shapes live
                // refuses are refused before anything runs (see ResultScanArgument).
                final Integer index = funcCtx.functionArgList() == null ? null
                    : ResultScanArgument.index(funcCtx.functionArgList().functionArg(0));
                Object queryIdObj;
                if (index != null) {
                    queryIdObj = resultCache.getQueryId(index.intValue());
                } else if (funcArgExprs(funcCtx).isEmpty()) {
                    queryIdObj = evaluateExpression("LAST_QUERY_ID()", null, null);
                } else {
                    final FrostlakeParser.ExpressionContext argExpr = funcArgExprs(funcCtx).get(0);
                    // Evaluate the argument like any other expression. It used to be special-cased by
                    // NAME when it was a LAST_QUERY_ID call, which silently dropped that call's own
                    // argument — RESULT_SCAN(LAST_QUERY_ID(-2)) then scanned the most recent query
                    // instead of the one before it.
                    queryIdObj = evaluateExpression(getOriginalText(argExpr), null, null);
                    if (queryIdObj == null && !isLastQueryIdCall(argExpr)) {
                        throw new RuntimeException("Query ID cannot be null");
                    }
                    // A number read as a value — a session variable holding one — is an index too.
                    if (queryIdObj instanceof Number) {
                        queryIdObj = resultCache.getQueryId(ResultScanArgument.indexOf(
                            (Number) queryIdObj, funcCtx.functionArgList().functionArg(0)));
                    }
                }
                final String queryId = queryIdObj == null ? null : queryIdObj.toString();
                IntervalResultScan.refuse(queryId == null ? null : resultCache.getResult(queryId), queryId, funcCtx,
                    funcCtx.getParent() != null && funcCtx.getParent().getParent() instanceof FrostlakeParser.TableReferenceContext
                        ? sourceAlias((FrostlakeParser.TableReferenceContext) funcCtx.getParent().getParent()) : null);
                return new PlannedTableFunction(functionName, callText, new MemoizedResultSet(new ResultSetProvider() {
                    @Override
                    public ResultSet getResultSet() {
                        return resultScan.execute(queryId);
                    }
                }), null);
            }
            // Check if it's a known built-in table function callable with no/positional args
            final TableFunction tableFunc = functionRegistry.getTableFunction(functionName);
            if (tableFunc != null) {
                validateTableFunctionCall(functionName.toUpperCase(),
                    schemaQualifierOf(funcCtx.functionName()),
                    funcCtx.functionName().getStart(), funcArgExprs(funcCtx), null);
                if (!funcArgTexts(funcCtx).isEmpty()) {
                    // Positional args, e.g. SPLIT_TO_TABLE('a,b', ',') or FLATTEN(col) — evaluate and pass through.
                    final List<Object> positionalArgs = new ArrayList<>();
                    for (final FrostlakeParser.FunctionArgContext arg : funcCtx.functionArgList().functionArg()) {
                        if (arg.selectStatement() != null || arg.booleanExpr() instanceof FrostlakeParser.ValueExprContext) {
                            positionalArgs.add(evaluatePositionalTableFunctionArgument(arg, lateralContext));
                        }
                    }
                    // A function whose columns do not depend on its arguments tells them for no arguments too.
                    return new PlannedTableFunction(functionName, callText,
                        new MemoizedResultSet(new ResultSetProvider() {
                            @Override
                            public ResultSet getResultSet() {
                                return tableFunc.execute(positionalArgs);
                            }
                        }), tableFunc.outputColumns(new HashMap<String, Object>()));
                }
                return plannedCall(functionName, callText, tableFunc, new HashMap<String, Object>());
            }
            // Check if it's a user-defined table function (UDTF)
            final Function udtf = resolveUdtf(funcCtx.functionName());
            if (udtf != null && udtf.isTableFunction()) {
                final List<Object> callArgs = new ArrayList<>();
                if (!funcArgTexts(funcCtx).isEmpty()) {
                    for (final FrostlakeParser.FunctionArgContext arg : funcCtx.functionArgList().functionArg()) {
                        if (arg.selectStatement() != null || arg.booleanExpr() instanceof FrostlakeParser.ValueExprContext) {
                            callArgs.add(evaluatePositionalTableFunctionArgument(arg, lateralContext));
                        }
                    }
                }
                return new PlannedTableFunction(functionName, callText, new MemoizedResultSet(new ResultSetProvider() {
                    @Override
                    public ResultSet getResultSet() {
                        if (udtf.getUdfLanguage() == UdfLanguage.PYTHON) {
                            return UdfRuntimes.require(UdfLanguage.PYTHON)
                                .executeTableFunction(udtf, callArgs);
                        }
                        // A body that closes its own frame before a semicolon runs what the frame holds.
                        final String sql = substituteSqlParams(SqlUdfBodyFrame.executableBody(udtf.getBody()),
                            udtf.getParameters(), callArgs);
                        final List<ResultSet> results = execute(sql);
                        return results.isEmpty() ? new ResultSet(new ArrayList<>(), new ArrayList<>())
                            : nameByDeclaredReturnColumns(udtf, results.get(0));
                    }
                }), null);
            }
            // Snowflake also allows a stored PROCEDURE declared RETURNS TABLE(…) as a FROM source:
            // TABLE(proc(args)) runs the procedure and uses its returned table as the row source.
            // Resolved last so built-in and user-defined table functions keep precedence.
            final ResultSet procResult = executeProcedureAsTableSource(funcCtx);
            if (procResult != null) {
                return new PlannedTableFunction(functionName, callText, new MemoizedResultSet(new ResultSetProvider() {
                    @Override
                    public ResultSet getResultSet() {
                        return procResult;
                    }
                }), procResult.getColumns());
            }
            throw new RuntimeException(SqlCompilationError.of("Unknown table function " + functionName));
        }
        // Handle SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS('task') as table function
        // SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS is a SCALAR function, not a table function:
        // `SELECT SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS('t')` answers a status sentence, while
        // wrapping it in TABLE(...) is refused. Frostlake used to run it either way.
        if (expr instanceof FrostlakeParser.SystemUserTaskCancelExprContext) {
            throw new RuntimeException(SqlCompilationError.of(
                "Unknown table function SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS"));
        }
        // FROM TABLE(<string_literal> | <session_variable> | <bind_variable>) — a Snowflake TABLE LITERAL,
        // where the operand is a VALUE naming a table/view/stream rather than a table-function call:
        // "where TABLE() is supported, it is equivalent to using IDENTIFIER()". A bind variable has already
        // been substituted to a literal by this point.
        final TableData tableLiteral = tableLiteralSource(expr, null);
        final ResultSet literal = tableDataToResultSet(tableLiteral);
        return new PlannedTableFunction("TABLE", callText, new MemoizedResultSet(new ResultSetProvider() {
            @Override
            public ResultSet getResultSet() {
                return literal;
            }
        }), literal.getColumns());
    }

    /** A built-in call over named arguments: run when first asked, its columns known when the function tells them. */
    private static PlannedTableFunction plannedCall(final String functionName, final String callText,
                                                    final TableFunction tableFunc,
                                                    final Map<String, Object> namedArgs) {
        return new PlannedTableFunction(functionName, callText, new MemoizedResultSet(new ResultSetProvider() {
            @Override
            public ResultSet getResultSet() {
                return tableFunc.execute(namedArgs);
            }
        }), tableFunc.outputColumns(namedArgs));
    }

    /** {@link #planTableFunction} run at once. */
    private ResultSet executeTableFunction(final FrostlakeParser.ExpressionContext expr,
                                           final Map<String, Object> lateralContext) {
        return planTableFunction(expr, lateralContext).result.getResultSet();
    }

    /**
     * Resolve {@code TABLE(<value>)} as a TABLE LITERAL — the rows of the table, view, stream or dynamic
     * table the value names. Snowflake's own words are that "where TABLE() is supported, it is equivalent
     * to using IDENTIFIER()", and it is: the value is an identifier reference, folding unquoted to upper
     * case, and a name that reaches nothing earns the very sentence the IDENTIFIER() source earns
     * (live-verified). The source registers the name it resolves to, so the relation answers to it and a
     * second source of that name is a duplicate alias.
     *
     * @param expr the operand
     * @param alias the alias written after the source, or null
     * @return the source, or null when the operand is a table-function call rather than a value
     */
    private TableData tableLiteralSource(final FrostlakeParser.ExpressionContext expr, final String alias) {
        if (namesATableFunction(expr)) {
            return null;
        }
        final Object nameValue = new ExpressionEvaluator(null, functionRegistry, catalog, this)
            .evaluate(getOriginalText(expr), null);
        if (nameValue == null) {
            throw new RuntimeException("IDENTIFIER() expression evaluated to null");
        }
        if (!SqlIdentifiers.isIdentifierReference(nameValue.toString())) {
            throw new RuntimeException(SqlCompilationError.at(expr.getStart().getLine(),
                expr.getStart().getCharPositionInLine(), "invalid identifier '" + getOriginalText(expr) + "'"));
        }
        final String name = SqlIdentifiers.identifierReferenceText(nameValue.toString().trim());
        final TableData dynamic = planNamedSource(name, alias);
        if (dynamic != null) {
            return dynamic;
        }
        final Table resolved = catalog.resolveTableAsWritten(name, "Object");
        return new TableData(resolved, transactionScan(getFullyQualifiedTableName(name)),
            alias != null ? alias : name, "SCAN[" + resolved.getName() + "]");
    }

    /** True when a {@code TABLE(...)} operand is a call — a table function or a procedure — rather than a value. */
    private boolean namesATableFunction(final FrostlakeParser.ExpressionContext expr) {
        return expr instanceof FrostlakeParser.FunctionCallExprContext
            || expr instanceof FrostlakeParser.FunctionCallStarExprContext
            || expr instanceof FrostlakeParser.FunctionCallMixedArgsExprContext
            || expr instanceof FrostlakeParser.FunctionCallNamedArgsExprContext
            || expr instanceof FrostlakeParser.SystemUserTaskCancelExprContext;
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
        return src.FLATTEN() != null || src.tableFunctionExpr() != null
            // A TABLE(<value>) table literal is a relation, not a call, so it joins as any relation does.
            || (src.TABLE() != null && src.expression() != null && namesATableFunction(src.expression()));
    }

    /**
     * The LATERAL stage of a comma or JOIN LATERAL item and the relation it answers. The right side's shape is
     * settled here, while planning, by reading it under {@link RelationShapeOnly} with every column in scope
     * bound to NULL — so the stages after it are planned over a known relation and an empty left input still
     * answers a correctly shaped empty result. The stage then evaluates the right side once per left row.
     *
     * @param leftTable     the relation joined so far
     * @param rightTableRef the LATERAL item
     * @param joinCtx       the join it is written as, or null for a comma item
     * @param aliasToTable  the FROM-clause keys in scope, as they stand here
     * @param allTables     every relation in scope, in combined-row order
     * @param selectCtx     the select clause, whose aliases the item may reference
     * @return the stage and the right side's shape and alias
     */
    private PlannedRelation planLateralJoin(final Table leftTable,
                                            final FrostlakeParser.TableReferenceContext rightTableRef,
                                            final FrostlakeParser.JoinClauseContext joinCtx,
                                            final Map<String, Table> aliasToTable,
                                            final List<Table> allTables,
                                            final FrostlakeParser.SelectClauseContext selectCtx) {
        // Snowflake lets a LATERAL in the FROM reference a SELECT-list alias of the same query — the shape
        // `SELECT ARRAY_CONSTRUCT(…) obs, … FROM t, TABLE(FLATTEN(obs))`. Those aliases are not columns of any
        // FROM table, so without them the lateral failed with "Column not found". Resolved per left row below,
        // alongside the columns; aggregates and aliases that merely rename a column are already excluded.
        final Map<String, String> selectAliases = selectAliasExpressions(selectCtx, leftTable);
        // The scope as it stands HERE: a relation joined after this item must not reach its evaluation.
        final Map<String, Table> scope = snapshotScope(aliasToTable);
        final List<Table> scopeTables = allTables != null && !allTables.isEmpty()
            ? new ArrayList<Table>(allTables) : List.of(leftTable);
        // The right side's shape, read with every name it may reference bound to NULL and no row produced.
        final Map<String, Object> nullContext = new HashMap<>();
        for (final Map.Entry<String, Table> entry : scope.entrySet()) {
            final String alias = entry.getKey();
            final Table t = entry.getValue();
            for (int i = 0; i < t.getColumns().size(); i++) {
                final String colName = t.getColumns().get(i).getName();
                nullContext.put(colName.toUpperCase(), null);
                nullContext.put((t.getName() + "." + colName).toUpperCase(), null);
                nullContext.put((alias + "." + colName).toUpperCase(), null);
            }
        }
        for (final String alias : selectAliases.keySet()) {
            if (!nullContext.containsKey(alias)) {
                nullContext.put(alias, null);
            }
        }
        // A table function written as this item's source has its ARGUMENT TYPES judged here, before the
        // shape pass, with the relations to the left in scope. The shape pass runs with NULL inputs and
        // answers a 0-column relation for anything it cannot read, which would swallow this refusal and
        // leave the item's own columns unresolvable in its place.
        lateralArgumentTypes.begin(scope, scopeTables);
        try {
            validateLateralTableFunctionTypes(rightTableRef);
        } finally {
            lateralArgumentTypes.end();
        }
        Table rightTable;
        String rightAlias;
        final boolean previous = RelationShapeOnly.begin();
        // The relations joined so far are the scope AROUND the lateral body, so an item that reads them —
        // JOIN LATERAL (SELECT f.a + 1 AS z) — is typed from the column it reads, as live types it, rather
        // than from the NULL the shape pass binds it to.
        final ExpressionEvaluator leftScope = new ExpressionEvaluator(leftTable, functionRegistry, catalog, this);
        leftScope.setMultiTableContext(scope, scopeTables);
        final ExpressionEvaluatorVisitor enclosingScope = SubqueryCompilation.beginScope(leftScope.scopeVisitor());
        try {
            final TableData shape = executeTableReference(rightTableRef, nullContext, null);
            rightTable = shape.table;
            rightAlias = shape.alias;
        } catch (final RuntimeException shapeUnreadable) {
            // The right side could not be read with NULL inputs (e.g. a correlated subquery that errors on
            // a NULL correlation value). An empty (0-column) relation lets the join still complete; the
            // rows, when there are any, are evaluated as written and raise their own fault.
            rightTable = new Table("lateral", new ArrayList<>(), false);
            rightAlias = null;
        } finally {
            SubqueryCompilation.endScope(enclosingScope);
            RelationShapeOnly.end(previous);
        }
        // Only a LEFT join written WITH an ON clause null-extends a left row the lateral side leaves unpaired:
        // live answers LEFT JOIN LATERAL without ON, and FULL or RIGHT JOIN LATERAL, as the inner join
        // (live-verified), so an empty lateral side drops its left row there.
        final FrostlakeParser.BooleanExprContext onCondition = joinCtx != null && joinCtx.ON() != null
            ? joinCtx.booleanExpr() : null;
        final boolean nullExtends = onCondition != null && joinCtx.joinType() != null
            && joinCtx.joinType().LEFT() != null;
        // An ON holding an aggregate or a window call is refused by its clause rule once the FROM clause is
        // resolved; until then no pair is judged against it.
        final boolean refusedByClauseRule = onCondition != null
            && (holdsAggregate(onCondition) || windowEvaluator.hasWindowFunctionInTree(onCondition));
        final Table lateralShape = rightTable;
        final String lateralAlias = rightAlias;
        final Operator stage = new StageOperator("LATERAL[" + (rightAlias != null ? rightAlias : rightTable.getName())
                + "]") {
            /** The ON condition, compiled when the stage first runs — by then the plan has accepted it. */
            private LateralJoinCondition condition;

            @Override
            protected List<Row> apply(final List<Row> leftRows) {
                if (onCondition != null && condition == null) {
                    condition = new LateralJoinCondition(QueryExecutor.this, onCondition, nullExtends,
                        refusedByClauseRule, leftTable, scope, scopeTables, lateralShape, lateralAlias);
                }
                final List<Row> resultRows = new ArrayList<>();
                for (final Row leftRow : leftRows) {
                    // Create context with left row values accessible by column name
                    final Map<String, Object> lateralContext = new HashMap<>();
                    // Add columns from all tables in the context, walking the relations in COMBINED-ROW ORDER —
                    // the offsets must follow the row layout. Iterating the alias map instead accumulated
                    // offsets in HASH order, so after a JOIN the lateral read the wrong slots: FLATTEN(a.attrs)
                    // after `f INNER JOIN a` got some other column (usually NULL) and expanded to nothing.
                    // Two passes: the upper-cased keys first, each keeping the FIRST column that folds to it,
                    // so a reference matches whatever case its alias and column were written in; then every
                    // column's EXACT spelling, which overrides them — of "x" and "X" beside each other, a.x and
                    // a."x" must read two different columns.
                    for (int pass = 0; pass < 2; pass++) {
                        int offset = 0;
                        for (final Table t : scopeTables) {
                            for (int i = 0; i < t.getColumns().size() && offset + i < leftRow.getValues().size(); i++) {
                                final String colName = t.getColumns().get(i).getName();
                                final Object value = leftRow.getValue(offset + i);
                                bindLateral(lateralContext, colName, value, pass);
                                // Also add with table prefix and every alias prefix that names this table
                                bindLateral(lateralContext, t.getName() + "." + colName, value, pass);
                                for (final Map.Entry<String, Table> entry : scope.entrySet()) {
                                    if (entry.getValue() == t) {
                                        bindLateral(lateralContext, entry.getKey() + "." + colName, value, pass);
                                    }
                                }
                            }
                            offset += t.getColumns().size();
                        }
                    }
                    // A real column of the same name always wins, so the aliases go in only where nothing is
                    // bound. Aliases chain (an alias defined over another alias), so resolve iteratively: each
                    // pass offers everything computed so far and retries the rest, until a pass makes no
                    // progress. An alias that still cannot be computed (it references the lateral's own output)
                    // is simply skipped — the reference then fails as it did before.
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
                                    evaluateExpressionWithAliases(alias.getValue(), leftRow, leftTable, scope,
                                        null, lateralContext));
                                pendingIt.remove();
                                progressed = true;
                            } catch (final RuntimeException notComputableYet) {
                                // retry next pass once more aliases are bound
                            }
                        }
                    }
                    // Execute right side with lateral context
                    final TableData rightData;
                    lateralArgumentTypes.begin(scope, scopeTables);
                    try {
                        rightData = executeTableReference(rightTableRef, lateralContext, null);
                    } finally {
                        lateralArgumentTypes.end();
                    }
                    // The ON pairs the lateral rows it accepts; a LEFT join keeps a left row the ON leaves unpaired,
                    // null-extended over the lateral's columns — LEFT JOIN LATERAL (...) ON TRUE must never drop
                    // left rows (12-month trend queries rely on zero-count rows surviving).
                    if (condition != null) {
                        final int rightWidth = rightData.table != null ? rightData.table.getColumns().size() : 0;
                        resultRows.addAll(condition.rowsFor(leftRow, rightData.rows, rightWidth, lateralContext));
                        continue;
                    }
                    // Combine left row with each right row
                    for (final Row rightRow : rightData.rows) {
                        final List<Object> combinedValues = new ArrayList<>(leftRow.getValues());
                        combinedValues.addAll(rightRow.getValues());
                        resultRows.add(new Row(combinedValues));
                    }
                }
                return resultRows;
            }
        };
        return new PlannedRelation(stage, rightTable, rightAlias);
    }

    /** The FROM-clause keys as they stand while a stage is planned: a relation joined later must not reach it. */
    private static Map<String, Table> snapshotScope(final Map<String, Table> aliasToTable) {
        if (aliasToTable instanceof FromClauseRelations) {
            return new FromClauseRelations((FromClauseRelations) aliasToTable);
        }
        return new LinkedHashMap<String, Table>(aliasToTable);
    }

    /**
     * Get column value from combined row, handling qualified names like "table.column"
     */
    private Object getQualifiedColumnValue(final Row combinedRow, final Table leftTable, final Table rightTable, final String qualifiedName) {
        final String[] parts = qualifiedName.split("\\.");
        final String columnName;
        String tableName = null;

        if (parts.length == 2) {
            tableName = parts[0].toUpperCase();
            columnName = parts[1].toUpperCase();
        } else {
            columnName = qualifiedName.toUpperCase();
        }

        // Search in left table first
        for (int i = 0; i < leftTable.getColumns().size(); i++) {
            final TableColumn col = leftTable.getColumns().get(i);
            if (col.getName().toUpperCase().equals(columnName)) {
                if (tableName == null || leftTable.getName().toUpperCase().contains(tableName)) {
                    return combinedRow.getValue(i);
                }
            }
        }

        // Search in right table
        final int offset = leftTable.getColumns().size();
        for (int i = 0; i < rightTable.getColumns().size(); i++) {
            final TableColumn col = rightTable.getColumns().get(i);
            if (col.getName().toUpperCase().equals(columnName)) {
                if (tableName == null || rightTable.getName().toUpperCase().contains(tableName)) {
                    return combinedRow.getValue(offset + i);
                }
            }
        }

        throw new RuntimeException(SqlCompilationError.invalidIdentifier(qualifiedName));
    }

    /**
     * Merge metadata from two tables for join result
     */
    Table mergeTableMetadata(final Table left, final Table right) {
        return mergeTableMetadata(left, right, Collections.emptySet());
    }

    /**
     * Merge metadata for a join result; the right-side columns named in {@code mergedNames} (a USING /
     * NATURAL join's key columns, canonical spellings, in USING-list / left-table order) stay resolvable but
     * are hidden from {@code SELECT *} — Snowflake outputs ONE merged column, and puts it FIRST:
     * live, {@code t1(a,k,b) JOIN t2(c,k,d) USING (k)} projects {@code K A B C D}, a chained
     * {@code … JOIN t3 USING (j)} puts J before the earlier K, and the key columns stay in front
     * through a later ON join. The physical column list keeps the left-then-right layout the combined
     * rows use; the star ORDER is applied at expansion time from {@link Table#getJoinKeyNames}.
     */
    Table mergeTableMetadata(final Table left, final Table right, final Set<String> mergedNames) {
        return mergeTableMetadata(left, right, mergedNames, JoinType.INNER);
    }

    /**
     * As above, with the join type so an OUTER join can null-extend a side: a LEFT join manufactures
     * NULLs for the right-hand columns, a RIGHT join for the left-hand ones and a FULL join for both,
     * and a column that can be manufactured as NULL is no longer NOT NULL (live-verified — l.K keeps
     * its NOT NULL across a LEFT JOIN where r.R loses it). The catalog's own column instances are
     * never touched; each affected column is copied.
     */
    Table mergeTableMetadata(final Table left, final Table right, final Set<String> mergedNames,
                             final JoinType joinType) {
        final boolean leftNullExtended = joinType == JoinType.RIGHT || joinType == JoinType.FULL;
        final boolean rightNullExtended = joinType == JoinType.LEFT || joinType == JoinType.FULL;
        final List<TableColumn> allColumns = new ArrayList<>();
        for (final TableColumn col : left.getColumns()) {
            allColumns.add(leftNullExtended && !col.isNullable() ? col.nullableCopy() : col);
        }
        // The key names are matched exactly as spelled: of two columns that differ only in case, only the
        // one the USING list names is the key.
        for (final TableColumn col : right.getColumns()) {
            final TableColumn merged =
                mergedNames.contains(col.getName()) ? col.starHiddenCopy() : col;
            allColumns.add(rightNullExtended && !merged.isNullable() ? merged.nullableCopy() : merged);
        }
        final Table joined = new Table("joined", allColumns, false);
        joined.setJoinedRelations(JoinedRelations.joining(left, right, leftNullExtended, rightNullExtended));
        final List<String> keyNames = new ArrayList<>(mergedNames);
        if (left.getJoinKeyNames() != null) {
            for (final String inherited : left.getJoinKeyNames()) {
                if (!keyNames.contains(inherited)) {
                    keyNames.add(inherited);
                }
            }
        }
        if (!keyNames.isEmpty()) {
            joined.setJoinKeyNames(keyNames);
        }
        return joined;
    }

    /**
     * Records on a join's merged relation which of its relations the join extends with NULLs, for a
     * merge made without the join's kind — an ASOF join, a legacy {@code (+)} join, a LATERAL join or a
     * parenthesized join group. The column metadata the merge built is left as it is.
     */
    private static void recordNullExtension(final Table joined, final Table left, final Table right,
                                            final JoinType joinType) {
        joined.setJoinedRelations(JoinedRelations.joining(left, right,
            joinType == JoinType.RIGHT || joinType == JoinType.FULL,
            joinType == JoinType.LEFT || joinType == JoinType.FULL));
    }

    /** The join type a clause declares — INNER when it names none. */
    /**
     * The kind a LATERAL join is EVALUATED as, which is not always the kind it is written as: only a
     * LEFT JOIN LATERAL carrying an ON null-extends the left row when the lateral side is empty. Written
     * without one — and written FULL or RIGHT either way — it drops that row, as an inner join does.
     */
    private JoinType lateralJoinKind(final FrostlakeParser.JoinClauseContext joinCtx) {
        return joinTypeOf(joinCtx) == JoinType.LEFT && joinCtx.ON() != null ? JoinType.LEFT : JoinType.INNER;
    }

    private JoinType joinTypeOf(final FrostlakeParser.JoinClauseContext joinCtx) {
        // CROSS is a clause of its own in the grammar, because it takes no ON and no USING.
        if (joinCtx.CROSS() != null) {
            return JoinType.CROSS;
        }
        if (joinCtx.joinType() == null) {
            return JoinType.INNER;
        }
        if (joinCtx.joinType().LEFT() != null) {
            return JoinType.LEFT;
        }
        if (joinCtx.joinType().RIGHT() != null) {
            return JoinType.RIGHT;
        }
        if (joinCtx.joinType().FULL() != null) {
            return JoinType.FULL;
        }
        return JoinType.INNER;
    }

    /** The upper-cased key column names of a USING / NATURAL join in USING-list / left-table order
     *  (the order {@code SELECT *} surfaces them in), or an empty set for ON / CROSS. */
    private Set<String> usingJoinColumnNames(final FrostlakeParser.JoinClauseContext joinCtx,
                                             final Table leftTable, final Table rightTable) {
        if (joinCtx.NATURAL() != null) {
            return new LinkedHashSet<>(commonColumnNames(leftTable, rightTable));
        }
        if (joinCtx.USING() == null) {
            return Collections.emptySet();
        }
        final Set<String> names = new LinkedHashSet<>();
        for (final FrostlakeParser.QualifiedNameContext qn : joinCtx.usingColumnList().qualifiedName()) {
            final String[] parts = ParseTreeText.qualifiedNameParts(qn);
            names.add(parts[parts.length - 1]);
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
                final Table copy = new Table(rightTable.getName(), rightTable.getColumns(),
                    rightTable.isTemporary(), rightTable.isTransient());
                // Still the same table's columns: typed and tagged as the first instance's are.
                copy.markCopiedFrom(rightTable);
                return copy;
            }
        }
        return rightTable;
    }

    /** The throwaway relation name {@link #resolveRelationColumns} converts under: only the resulting
     *  column list is kept, so the name it is briefly attached to is never observed. */
    private static final String RESOLVED_SHAPE_NAME = "";

    /**
     * The columns a view-shaped {@code definition} produces — the list a {@code CREATE VIEW} freezes
     * onto its catalog entry so {@code INFORMATION_SCHEMA.COLUMNS} can report them without ever
     * planning a query during a metadata read. {@code explicitColumnNames} is the view's declared
     * column list ({@code CREATE VIEW v (x, y) AS …}) or null when it has none.
     *
     * <p>A body that will not COMPILE is refused, exactly as live refuses the CREATE, and with the
     * select's own sentence — that is all live answers: a missing table gives
     * {@code Object 'DB.SCHEMA.T' does not exist or not authorized.}, a missing column the positioned
     * {@code invalid identifier 'C'}, an unknown function {@code Unknown function F.} (all
     * live-verified).
     *
     * <p>A failure that only shows up while ROWS ARE PRODUCED still returns null. Frostlake resolves
     * the shape by EXECUTING the definition where Snowflake merely plans it, so a body that compiles
     * but divides by zero on the data present must not become a refusal live never makes. That is the
     * whole reason the two are told apart rather than everything being rethrown.
     *
     * <p>The columns come from the same {@link #resultSetToTable} conversion a read of the view goes
     * through, so a view's metadata and a query over that view can never disagree about its shape —
     * including the static types threaded out of the inner projection, which is how an
     * {@code OBJECT_CONSTRUCT} column is reported OBJECT rather than as the VARCHAR placeholder.
     */
    /**
     * A relation's column list from its definition's SHAPE alone — no row is read and no value computed
     * (see {@link RelationShapeOnly}), which is how CREATE VIEW learns its columns: a value-time fault in
     * the body is the reader's, not the creator's.
     */
    public List<TableColumn> resolveRelationShape(final String definition,
                                                  final List<String> explicitColumnNames) {
        final boolean previous = RelationShapeOnly.begin();
        try {
            return resolveRelationColumns(definition, explicitColumnNames);
        } finally {
            RelationShapeOnly.end(previous);
        }
    }

    public List<TableColumn> resolveRelationColumns(final String definition,
                                                    final List<String> explicitColumnNames) {
        try {
            final List<ResultSet> results = execute(definition);
            if (results.isEmpty() || results.get(0) == null) {
                return null;
            }
            final ResultSet resolved = results.get(0);
            final Table shape = explicitColumnNames != null && !explicitColumnNames.isEmpty()
                ? resultSetToTable(resolved, RESOLVED_SHAPE_NAME, explicitColumnNames)
                : resultSetToTable(resolved, RESOLVED_SHAPE_NAME);
            // A VIEW is a stored column shape, and both string EDGES settle to the 16MB storage
            // default there: the 128MB unknown length an expression carries clamps down, and the
            // ZERO width a bare NULL item carries widens up — live declares a view over SELECT NULL
            // as VARCHAR(16777216), where the bare query's result column is VARCHAR(0). Derived
            // tables and CTEs are untouched: they go through resultSetToTable directly.
            final List<TableColumn> settled = new ArrayList<>();
            for (final TableColumn column : shape.getColumns()) {
                if (column.getDataType() instanceof StringType
                        && (((StringType) column.getDataType()).getMaxLength() == 0
                            || ((StringType) column.getDataType()).getMaxLength()
                                == DeclaredTypeFold.UNKNOWN_LENGTH_VARCHAR)) {
                    settled.add(settledCollation(new TableColumn(column.getName(), StringType.VARCHAR,
                        column.isNullable(), null, false, false, false), column));
                } else if (column.getDataType() instanceof BinaryType
                        && ((BinaryType) column.getDataType()).atColumnWidth() != column.getDataType()) {
                    // So does a binary wider than a column's default: live declares a view over a 16MB
                    // concatenation, or over CAST(x AS BINARY(67108864)), BINARY(8388608), where a query
                    // over the view reads the full width.
                    settled.add(settledCollation(new TableColumn(column.getName(),
                        ((BinaryType) column.getDataType()).atColumnWidth(),
                        column.isNullable(), null, false, false, false), column));
                } else {
                    settled.add(column);
                }
            }
            return settled;
        } catch (final RuntimeException undetermined) {
            if (SqlCompilationError.isCompilationError(undetermined.getMessage())) {
                throw undetermined;
            }
            logger.debug("View columns could not be resolved from its definition: {}",
                undetermined.getMessage());
            return null;
        }
    }

    /** A settled view column keeps the collation the resolved one carried. */
    private static TableColumn settledCollation(final TableColumn settled, final TableColumn resolved) {
        settled.setCollation(resolved.getCollation());
        return settled;
    }

    /**
     * Convert a ResultSet to a Table object
     */
    /**
     * The relation a {@code ->>} chain's {@code $n} reads: a prior stage's result, with its columns
     * DECLARED as that result reports them.
     *
     * <p>A stage's result is a relation in its own right, so the types it reports ARE its declared
     * types — {@code SHOW TABLES ->> SELECT SYSTEM$TYPEOF("created_on") FROM $1} answers
     * TIMESTAMP_LTZ(3), the type the listing declares, where an undeclared column would answer the
     * untyped NULL tag. What does NOT survive the stage is the value INTERVAL: a column that came
     * through a {@code $n} reports its family's full storage width (SB16 for a NUMBER(38,0)), not the
     * narrow interval the plan had for it on the other side.
     *
     * @param rs        the prior stage's result
     * @param tableName the name the stage is read under
     * @return the relation
     */
    Table flowStageTable(final ResultSet rs, final String tableName) {
        final List<TableColumn> columns = new ArrayList<>();
        for (final ResultSetColumn col : rs.getColumns()) {
            final TableColumn column = new TableColumn(col.getName(),
                col.getStaticType() != null ? col.getStaticType() : col.getDataType(),
                col.isNullable(), null, false, false, false);
            column.setStaticallyTyped(column.getDataType() != null);
            column.setCollation(col.getCollation());
            columns.add(column);
        }
        final Table derived = new Table(tableName, columns, false);
        derived.setRelationStatistics(rs.getRelationStatistics());
        return derived;
    }

    Table resultSetToTable(final ResultSet rs, final String tableName) {
        final List<TableColumn> columns = new ArrayList<>();
        for (final ResultSetColumn col : rs.getColumns()) {
            columns.add(derivedColumn(col.getName(), col));
        }
        final Table derived = new Table(tableName, columns, false);
        derived.setRelationStatistics(rs.getRelationStatistics());
        return derived;
    }

    private Table resultSetToTable(final ResultSet rs, final String tableName, final List<String> columnNames) {
        final List<TableColumn> columns = new ArrayList<>();
        final List<ResultSetColumn> rsColumns = rs.getColumns();

        if (columnNames.size() != rsColumns.size()) {
            throw new RuntimeException("View column count (" + columnNames.size() +
                ") does not match SELECT column count (" + rsColumns.size() + ")");
        }

        for (int i = 0; i < rsColumns.size(); i++) {
            columns.add(derivedColumn(columnNames.get(i), rsColumns.get(i)));
        }
        final Table derived = new Table(tableName, columns, false);
        derived.setRelationStatistics(rs.getRelationStatistics());
        return derived;
    }

    /**
     * The combined columns of a set operation, giving each column the STATIC type its branches support
     * TOGETHER: the numeric supertype for measured NUMBERs, the shared type where every branch declares
     * the same one — parameters included — and undetermined otherwise. The fold takes the leading
     * branch's layout, so a differing branch would otherwise hand the outer query the first branch's
     * type for values Snowflake coerces to a common one — live, {@code SELECT s UNION ALL
     * SELECT n} reports {@code NUMBER(18,5)}, neither branch's own type.
     *
     * <p>Name-level agreement is NOT enough for the parameter-blind families: NUMBER's name drops its
     * precision and scale, so a (3,0) branch and a (4,1) branch read as "agreeing" and the leading
     * branch's scale-0 type then stores 183 for the 182.5 the other branch produced — silent value
     * corruption once the union feeds a CTE, view or CTAS, not a metadata nicety.
     */
    private List<ResultSetColumn> reconcileBranchTypes(final List<ResultSetColumn> combined,
                                                       final List<List<ResultSetColumn>> branches,
                                                       final List<List<NumericType>> literalMeasurements,
                                                       final List<List<Boolean>> nullArms) {
        final List<ResultSetColumn> reconciled = new ArrayList<>();
        for (int i = 0; i < combined.size(); i++) {
            final ResultSetColumn column = combined.get(i);
            final DataType agreedStatic = branchStaticType(i, branches, literalMeasurements, nullArms);
            // The REPORTED type is the fold too, not the leading arm's. Live declares the folded arm
            // everywhere it can fold one: v UNION w is VARCHAR(9) and not VARCHAR(5), i UNION n is
            // NUMBER(38,2) and not NUMBER(38,0), n UNION f is FLOAT and d UNION ts TIMESTAMP_NTZ.
            // Where no fold exists the leading arm stays, which is what the arms-disagree scan needs.
            // EVERY arm a bare NULL declares VARCHAR(0) — a width no column can hold and no literal can
            // produce, which is live's way of saying "nothing was named here". It is set on the
            // REPORTED type only: the STATIC one stays undetermined, and that distinction is the whole
            // reason this can be done at all. Giving the column a static type instead handed the INSERT
            // type check a concrete type to compare for the first time, and a string does not match a
            // VARIANT / ARRAY / OBJECT column — fourteen vendor loaders insert an all-NULL set
            // operation into exactly those columns and every one of them was refused.
            final DataType reported = agreedStatic != null ? agreedStatic
                : everyArmIsNull(i, branches, nullArms) ? new StringType("VARCHAR", 0)
                    : column.getDataType();
            // A set operation's column always accepts NULL — even when EVERY branch is NOT NULL, and
            // even for INTERSECT (live-verified). The rebuild is unconditional for that reason.
            reconciled.add(new ResultSetColumn(column.getName(), reported,
                column.getTableName(), agreedStatic, true, false,
                agreedStatic != null ? branchValueRange(i, branches, nullArms) : null));
        }
        return reconciled;
    }

    /**
     * The union of every branch's interval for column {@code i} — a set operation's statistics are its
     * arms' together — or null when any arm's is unknown. A bare NULL arm adds no value, only the NULL.
     */
    private ValueRange branchValueRange(final int i, final List<List<ResultSetColumn>> branches,
                                        final List<List<Boolean>> nullArms) {
        ValueRange union = null;
        for (int k = 0; k < branches.size(); k++) {
            final List<ResultSetColumn> columns = branches.get(k);
            final ValueRange arm;
            if (i < columns.size() && isNullArm(nullArms, k, i)) {
                arm = ValueRange.EMPTY;
            } else if (i >= columns.size() || columns.get(i).getValueRange() == null) {
                return null;
            } else {
                arm = columns.get(i).getValueRange();
            }
            union = union == null ? arm : union.union(arm);
        }
        return union;
    }

    /**
     * The static type every branch supports for column {@code i}, or null when any branch is
     * undetermined or the branches cannot be combined. Undetermined stays undetermined: a branch whose
     * inference answered null may hold ANY runtime value, so skipping it would let the other branches'
     * narrow type truncate what it produces.
     *
     * <p>A VARCHAR branch beside exactly one non-string family unifies INTO that family (live):
     * beside NUMBER it contributes NUMBER(18,5) — the fold with the number side gives {@code s ∪
     * NUMBER(10,2)} NUMBER(18,5), {@code ∪ NUMBER(30,10)} NUMBER(30,10) and {@code ∪ NUMBER(38,0)}
     * NUMBER(38,5) — except that a bare STRING LITERAL branch contributes the literal's own numeric
     * measurement instead ({@code '5' ∪ 1} declares NUMBER(1,0), {@code '5' ∪ 1::NUMBER(10,2)}
     * NUMBER(10,2), {@code '2.75' ∪ 1} NUMBER(3,2)); beside FLOAT it contributes FLOAT, and beside
     * DATE, DATE.
     */
    private DataType branchStaticType(final int i, final List<List<ResultSetColumn>> branches,
                                      final List<List<NumericType>> literalMeasurements,
                                      final List<List<Boolean>> nullArms) {
        // A bare NULL arm contributes NOTHING to the fold: the other arms decide the type between
        // themselves, and the NULL simply joins whatever they settle on. Written SECOND that already
        // happened, because the walk kept the type it had; written FIRST the NULL's own reported
        // VARCHAR(16777216) became the answer and the real arms were folded INTO a 16MB string.
        // With every arm a NULL there is nothing to decide and the column is VARCHAR(0).
        final List<DataType> declared = new ArrayList<>();
        final List<Integer> declaredBranch = new ArrayList<>();
        for (int k = 0; k < branches.size(); k++) {
            final List<ResultSetColumn> branch = branches.get(k);
            if (i >= branch.size()) {
                return null;
            }
            if (isNullArm(nullArms, k, i)) {
                continue;
            }
            final DataType branchType = branch.get(i).getStaticType();
            if (branchType == null) {
                return null;
            }
            declared.add(branchType);
            // The ORIGINAL branch index, kept because skipping the NULL arms compacts the list and a
            // literal's measurement is recorded against the branch it was written in.
            declaredBranch.add(Integer.valueOf(k));
        }
        if (declared.isEmpty()) {
            // EVERY arm a bare NULL. Live declares VARCHAR(0) here; this leaves the column
            // undetermined instead, because naming a type where there was none turns an INSERT into a
            // VARIANT / ARRAY / OBJECT column into a refusal — the type check has a concrete type to
            // compare for the first time, and a string is not one of those families.
            return null;
        }
        // The arms fold LEFT TO RIGHT, and a STRING arm contributes whatever the fold it MEETS makes of
        // it — not itself. What it meets is the running fold; only the first arm has none yet, and then
        // it is the first non-string arm that decides, because that is the one it will meet next.
        //
        // This used to ask a different question: "do all the non-string arms agree with each other?",
        // and switched the substitution off entirely when they did not. That broke the fold an arm
        // EARLY. `i UNION v UNION bn` is refused by both engines, but live folds i with v into
        // NUMBER(38,5) first and breaks on the BINARY, while Frostlake never folded i with v at all —
        // it broke on the VARCHAR and reported the running fold as the NUMBER(38,0) it started with.
        // The arms after a string have nothing to say about what that string meets.
        DataType combined = null;
        for (int k = 0; k < declared.size(); k++) {
            DataType contribution = declared.get(k);
            if (contribution instanceof StringType) {
                final DataType met = combined != null ? combined : firstNonStringArm(declared);
                if (IntervalCasts.isIntervalType(met)) {
                    // A text after an interval arm is read in the interval's fields on the row; a text
                    // before one is refused: 'x' UNION ALL SELECT ts - ts2 is "incompatible types:
                    // [INTERVAL DAY(9) TO SECOND(9)] and [VARCHAR(1)]" (live-verified).
                    if (combined == null) {
                        throw new RuntimeException(SqlCompilationError.of("incompatible types: [" + met.getName()
                            + "] and [" + SqlTypeNames.canonical(contribution) + "]"));
                    }
                    contribution = met;
                } else if (met != null && !(met instanceof StringType)) {
                    final DataType substituted = stringBranchContribution(met,
                        literalMeasurement(literalMeasurements, declaredBranch.get(k).intValue(), i));
                    if (substituted == null) {
                        // A BINARY does not meet a string, and live refuses the pair rather than
                        // leaving the column undetermined — whether the BINARY has been folded into
                        // the running answer yet or is still ahead. Every OTHER non-string reaching
                        // here is a pairing nobody has measured, so it keeps its undetermined answer.
                        // The sentence names the RUNNING FOLD as what it got and the arm it could not
                        // take as what it expected. Which of the two the string is depends on where it
                        // stands: after a BINARY it is the arm being refused, and before one it is the
                        // running fold and the BINARY ahead is the arm.
                        if (met instanceof BinaryType) {
                            rejectUnfoldableArms(combined != null ? combined : contribution,
                                combined != null ? contribution : met);
                        }
                        return null;
                    }
                    contribution = substituted;
                }
            }
            if (combined == null) {
                combined = contribution;
                continue;
            }
            final DataType folded = combineDeclaredTypes(combined, contribution);
            if (folded == null) {
                rejectUnfoldableArms(combined, contribution);
                return null;
            }
            combined = folded;
        }
        return combined;
    }

    /** The first arm that is not a string, which is what a LEADING string arm will meet. */
    private DataType firstNonStringArm(final List<DataType> declared) {
        for (final DataType type : declared) {
            if (!(type instanceof StringType)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Arms whose declared types cannot be brought together at all. Live refuses these at COMPILE time,
     * and words them two ways — the same pair of sentences a CONDITIONAL uses for its branches, because
     * the two surfaces share one fold:
     *
     * <pre>
     *   SELECT d FROM t UNION SELECT tm FROM t
     *       inconsistent data type for result columns for set operator input branches,
     *       expected TIME(9), got DATE
     *   SELECT tm FROM t UNION SELECT ts FROM t
     *       incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]
     * </pre>
     *
     * <p>The wording of the first is worth reading carefully: "expected" is the arm the fold FAILED ON
     * and "got" is the running fold of everything before it — not the last arm and not the first.
     * Three arms is what shows the difference, because there the two readings disagree:
     * {@code i UNION v UNION bn} reports "expected BINARY(5), got NUMBER(38,5)", and NUMBER(38,5) is
     * no single arm's type — it is what {@code i} and {@code v} folded to before the BINARY broke it.
     *
     * <p>The TIME rule names the TIME first however the arms were written, exactly as it does between
     * branches, so the sentence is stable under reordering while the other one is not.
     *
     * @param foldedSoFar the running fold of the arms already read
     * @param arm         the arm that would not join it
     */
    private void rejectUnfoldableArms(final DataType foldedSoFar, final DataType arm) {
        // A VARIANT absorbs whatever stands beside it and the fold keeps the arm already read —
        // va UNION i is VARIANT and ob UNION va is OBJECT — so the pairing is not a refusal at all,
        // whatever the shared fold answers for it. Left undetermined here rather than refused; the
        // fold itself belongs to the shared declared-type fold.
        if (foldedSoFar instanceof VariantType || arm instanceof VariantType) {
            return;
        }
        final DataType time = timeOf(foldedSoFar, arm);
        final DataType timestamp = timestampOf(foldedSoFar, arm);
        if (time != null && timestamp != null) {
            throw new RuntimeException(SqlCompilationError.of("incompatible types: ["
                + SqlTypeNames.canonical(time) + "] and [" + SqlTypeNames.canonical(timestamp) + "]"));
        }
        throw new RuntimeException(SqlCompilationError.of(
            "inconsistent data type for result columns for set operator input branches, expected "
                + SqlTypeNames.canonical(arm) + ", got " + SqlTypeNames.canonical(foldedSoFar)));
    }

    /** Whichever of the two is a TIME, or null when neither is. */
    private DataType timeOf(final DataType left, final DataType right) {
        if (left instanceof DateTimeType && "TIME".equalsIgnoreCase(left.getName())) {
            return left;
        }
        if (right instanceof DateTimeType && "TIME".equalsIgnoreCase(right.getName())) {
            return right;
        }
        return null;
    }

    /** Whichever of the two is a TIMESTAMP of any zone, or null when neither is. */
    private DataType timestampOf(final DataType left, final DataType right) {
        if (left instanceof DateTimeType
                && left.getName().toUpperCase(Locale.ROOT).startsWith("TIMESTAMP")) {
            return left;
        }
        if (right instanceof DateTimeType
                && right.getName().toUpperCase(Locale.ROOT).startsWith("TIMESTAMP")) {
            return right;
        }
        return null;
    }

    /** What a VARCHAR branch contributes to the fold beside the given non-string type, or null when
     *  the pairing is not one of the measured ones. */
    private DataType stringBranchContribution(final DataType nonString,
                                              final NumericType literalMeasurement) {
        if (nonString instanceof NumericType) {
            final String name = nonString.getName();
            if ("NUMBER".equalsIgnoreCase(name)) {
                return literalMeasurement != null ? literalMeasurement
                    : new NumericType("NUMBER", 18, 5);
            }
            if ("FLOAT".equalsIgnoreCase(name) || "DOUBLE".equalsIgnoreCase(name)) {
                return nonString;
            }
            return null;
        }
        if (nonString instanceof DateTimeType && "DATE".equalsIgnoreCase(nonString.getName())) {
            return nonString;
        }
        return null;
    }

    /** The literal measurement recorded for branch {@code k}'s column {@code i}, or null. */
    private NumericType literalMeasurement(final List<List<NumericType>> literalMeasurements,
                                           final int k, final int i) {
        if (literalMeasurements == null || k >= literalMeasurements.size()) {
            return null;
        }
        final List<NumericType> branch = literalMeasurements.get(k);
        return i < branch.size() ? branch.get(i) : null;
    }

    /**
     * Per branch, per column: the numeric measurement of a bare STRING LITERAL select item, or null.
     * Only the plain {@code selectClause} operand shape is inspected — a parenthesized sub-statement
     * contributes no measurement and unifies through its declared types alone.
     */
    private List<List<NumericType>> stringLiteralMeasurements(
            final List<FrostlakeParser.SelectOperandContext> operands) {
        final List<List<NumericType>> perBranch = new ArrayList<>();
        for (final FrostlakeParser.SelectOperandContext operand : operands) {
            final List<NumericType> measurements = new ArrayList<>();
            if (operand.selectClause() != null && operand.selectClause().selectList() != null) {
                for (final FrostlakeParser.SelectItemContext item
                        : operand.selectClause().selectList().selectItem()) {
                    measurements.add(stringLiteralMeasurement(item));
                }
            }
            perBranch.add(measurements);
        }
        return perBranch;
    }

    /** The numeric measurement of one select item when it is a bare string literal spelling a
     *  number, else null. */
    /**
     * Per branch, per column: whether the select item is a BARE {@code NULL} literal. A NULL arm
     * contributes nothing to the fold — {@code SELECT NULL UNION SELECT i} is NUMBER(38,0) and
     * {@code SELECT NULL UNION SELECT d} is DATE — and when EVERY arm is one the column is VARCHAR(0).
     *
     * <p>It has to be read off the AST because the reported type cannot tell them apart: a bare NULL
     * reports VARCHAR(16777216), and so does a real 16MB VARCHAR column. Reading the type alone would
     * have made every wide VARCHAR arm vanish from the fold.
     *
     * <p>A TYPED null is NOT one of these — {@code NULL::INT} and {@code CAST(NULL AS DATE)} parse to a
     * cast rather than a literal, and both already fold as the type they name.
     */
    private List<List<Boolean>> nullLiteralArms(
            final List<FrostlakeParser.SelectOperandContext> operands) {
        final List<List<Boolean>> perBranch = new ArrayList<>();
        for (final FrostlakeParser.SelectOperandContext operand : operands) {
            final List<Boolean> flags = new ArrayList<>();
            if (operand.selectClause() != null && operand.selectClause().selectList() != null) {
                for (final FrostlakeParser.SelectItemContext item
                        : operand.selectClause().selectList().selectItem()) {
                    flags.add(Boolean.valueOf(isBareNullLiteral(item)));
                }
            }
            perBranch.add(flags);
        }
        return perBranch;
    }

    /** Whether a select item is the bare word NULL and nothing else. */
    private boolean isBareNullLiteral(final FrostlakeParser.SelectItemContext item) {
        if (!SelectItemAccessors.isExprItem(item)) {
            return false;
        }
        final FrostlakeParser.ExpressionContext valueExpr = SelectItemAccessors.getItemValueExpr(item);
        if (valueExpr == null) {
            return false;
        }
        try {
            return UntypedNullFold.isUntypedNull(ExpressionEvaluator.parse(ParseTreeText.getOriginalText(valueExpr)));
        } catch (final RuntimeException notALiteral) {
            return false;
        }
    }

    /** Whether branch {@code k}'s column {@code i} was written as a bare NULL. */
    /**
     * Whether column {@code i} is a bare NULL in EVERY arm — the one shape with no type to fold at all.
     * Mirrors the skip in {@link #branchStaticType}, whose declared list comes out empty for exactly
     * these columns.
     *
     * @param i        the column's position
     * @param branches every arm's columns
     * @param nullArms per arm, per column, whether the item was written as a bare NULL
     * @return true when no arm named a type
     */
    private boolean everyArmIsNull(final int i, final List<List<ResultSetColumn>> branches,
                                   final List<List<Boolean>> nullArms) {
        if (branches.isEmpty()) {
            return false;
        }
        for (int k = 0; k < branches.size(); k++) {
            if (i >= branches.get(k).size() || !isNullArm(nullArms, k, i)) {
                return false;
            }
        }
        return true;
    }

    private boolean isNullArm(final List<List<Boolean>> nullArms, final int k, final int i) {
        if (nullArms == null || k >= nullArms.size()) {
            return false;
        }
        final List<Boolean> branch = nullArms.get(k);
        return i < branch.size() && branch.get(i).booleanValue();
    }

    private NumericType stringLiteralMeasurement(final FrostlakeParser.SelectItemContext item) {
        if (!SelectItemAccessors.isExprItem(item)) {
            return null;
        }
        final FrostlakeParser.ExpressionContext valueExpr = SelectItemAccessors.getItemValueExpr(item);
        if (valueExpr == null) {
            return null;
        }
        try {
            final Expression parsed = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(valueExpr));
            if (!(parsed instanceof LiteralExpression)
                    || ((LiteralExpression) parsed).getType() != LiteralType.STRING) {
                return null;
            }
            return NumericLiteralTypes.forDecimal(
                new BigDecimal(String.valueOf(((LiteralExpression) parsed).getValue()).trim()));
        } catch (final RuntimeException notANumericLiteral) {
            return null;
        }
    }

    /**
     * Two branches' declared types folded into the one the combined column declares, or null when they
     * cannot be. Differently-parameterized NUMBERs fold to Snowflake's supertype; any other difference
     * is undetermined — a FLOAT-vs-NUMBER or INTEGER-vs-NUMBER union is left to the value scan rather
     * than guessed.
     */
    private DataType combineDeclaredTypes(final DataType left, final DataType right) {
        // The rule itself lives in DeclaredTypeFold, because a CONDITIONAL folds its branches exactly
        // as a set operation folds its arms — measured cell by cell on both surfaces.
        return DeclaredTypeFold.combine(left, right);
    }

    /**
     * A derived relation's column: the STATIC type where the projection could infer one, the reported
     * type otherwise. Only the inferred case is marked statically typed, so an undetermined column stays
     * undetermined and the enclosing query sees "no declared type" rather than a guess — which is what
     * lets a type-based rule fire through a subquery, CTE or view without ever firing on a placeholder.
     */
    private TableColumn derivedColumn(final String name, final ResultSetColumn source) {
        final DataType staticType = source.getStaticType();
        // Nullability rides along so a derived table, a CTE and a view over a view are TRANSPARENT to
        // a NOT NULL, exactly as live is. CTAS does NOT go through here — live drops NOT NULL there.
        final TableColumn column = new TableColumn(name,
            staticType != null ? staticType : source.getDataType(), source.isNullable(),
            null, false, false, false);
        column.setStaticallyTyped(staticType != null);
        column.setValueRange(source.getValueRange());
        column.setSpelledNumber(source.getSpelledNumber());
        column.setUncheckedConstant(source.isUncheckedConstant());
        column.setFoldedDouble(source.isFoldedDouble());
        // A collation rides along too: the outer query compares, sorts and groups the derived column
        // under the collation the projection settled on, as it would a catalog column's.
        column.setCollation(source.getCollation());
        return column;
    }

    /**
     * A projected result column: the type it REPORTS is exactly what it always reported, and the type it
     * is statically KNOWN to produce rides alongside. Keeping the two apart is deliberate — JDBC
     * metadata, CTAS and set-operation coercion all read the reported type, and this change is about
     * what the compile-time type rules may conclude, not about what a column claims to be.
     *
     * <p>The one exception is the SEMI-STRUCTURED family, which reports its static type. Live,
     * {@code CREATE TABLE t AS SELECT OBJECT_CONSTRUCT('k','v') AS c FROM s} declares {@code c} OBJECT
     * ({@code DESC TABLE} on a real account), an {@code ARRAY_CONSTRUCT} column ARRAY, and a
     * {@code PARSE_JSON} or path-extraction column VARIANT — an OBJECT even when the select matches ZERO
     * rows, and the same in a view and with no FROM clause at all — while a VARCHAR placeholder makes all
     * of them VARCHAR. See {@link #semiStructuredReportedType}.
     *
     * <p>The family restriction is itself a measured boundary, not caution: promoting EVERY static type
     * instead reports a bare {@code NUMBER} where the value scan downstream would have kept the scale, so
     * {@code CREATE TABLE t AS SELECT 1.5 AS c} becomes NUMBER(38,0) and STORES 2 where live declares
     * NUMBER(2,1) and stores 1.5. Every non-semi-structured family is already recovered from the VALUES by
     * the CTAS reader (widest scale seen, BOOLEAN, temporals); a semi-structured cell is not a
     * Number/Boolean/temporal and so falls through that recovery to the placeholder every time, which is
     * why it and nothing else needs the static channel. Reporting live's precision for computed numerics
     * needs Snowflake's arithmetic precision/scale rules and is a separate change.
     *
     * <p>The FROM-less projection ({@link #planSelectWithoutFrom}) routes through here too — live
     * types the two forms identically ({@code CREATE TABLE t AS SELECT
     * OBJECT_CONSTRUCT('k','v') AS c} with no FROM declares OBJECT, {@code SELECT {*}} declares OBJECT,
     * and a view over either does the same). That flip was blocked for a day by what looked like a
     * consumer of this column's two type channels: with FROM-less statics attached, one downstream
     * integration loader failed as a silent DATA mismatch. The culprit was not here at all —
     * {@link #reconcileBranchTypes} compared branch statics by NAME, NUMBER's name drops its
     * parameters, so a set operation's (3,0) and (4,1) branches "agreed" and the leading branch's
     * scale-0 type truncated the other branch's 182.5 to 183 through a CTE-shaped CTAS. FROM-less
     * statics merely made that reachable: literal-only union branches all carry measured NUMBERs.
     * Fixed by the numeric supertype fold on {@link #reconcileBranchTypes}.
     *
     * <p>Recorded because it looked like a regression and is not: the promotion types one downstream
     * integration suite's CTAS column ARRAY — the select is a {@code UNION ALL} of an
     * {@code ARRAY_CONSTRUCT} branch with a VARIANT branch — a later {@code FLATTEN} then yields one
     * element and {@code ::NUMBER} over it fails. That sequence was replayed statement-for-statement on a
     * real account and Snowflake does the SAME THING at every step: it declares the union
     * column ARRAY (either branch order, leading branch empty or not), {@code PARSE_JSON('{}')::ARRAY}
     * wraps to {@code [{}]} there too, FLATTEN over that yields one row there too, and the cast fails
     * there too with {@code Failed to cast variant value {} to FIXED}. The promotion REPRODUCES live
     * exactly; that suite had passed only because the placeholder called the column VARCHAR, and what was
     * wrong was one line of its own local scaffolding — it seeded the VARIANT stand-in column with
     * {@code PARSE_JSON('{}')}, an empty OBJECT, where the SQL under test unions it with
     * {@code ARRAY_CONSTRUCT(...)} and flattens it. An empty ARRAY is the shape that column must hold for
     * the procedure to run on Snowflake at all, and it is seeded that way now.
     */
    private ResultSetColumn projectedColumn(final String name, final DataType reportedType,
                                            final String sourceTableName, final String expressionText,
                                            final ExpressionEvaluator projectionTypes) {
        return projectedColumn(name, reportedType, sourceTableName, expressionText, projectionTypes, true);
    }

    private ResultSetColumn projectedColumn(final String name, final DataType reportedType,
                                            final String sourceTableName, final String expressionText,
                                            final ExpressionEvaluator projectionTypes,
                                            final boolean nullable) {
        return projectedColumn(name, reportedType, sourceTableName, expressionText, projectionTypes,
            nullable, !nullable);
    }

    private ResultSetColumn projectedColumn(final String name, final DataType reportedType,
                                            final String sourceTableName, final String expressionText,
                                            final ExpressionEvaluator projectionTypes,
                                            final boolean nullable, final boolean nullabilityKnown) {
        return projectedColumn(name, reportedType, sourceTableName, expressionText, projectionTypes,
            nullable, nullabilityKnown, null);
    }

    private ResultSetColumn projectedColumn(final String name, final DataType reportedType,
                                            final String sourceTableName, final String expressionText,
                                            final ExpressionEvaluator projectionTypes,
                                            final boolean nullable, final boolean nullabilityKnown,
                                            final ParserRuleContext origin) {
        final DataType staticType = staticProjectionType(expressionText, projectionTypes, origin);
        final ResultSetColumn column = new ResultSetColumn(name,
            isBareNullLiteral(expressionText) ? new StringType("VARCHAR", 0)
                : semiStructuredReportedType(reportedType, staticType),
            sourceTableName, staticType, nullable, nullabilityKnown,
            staticType != null ? staticProjectionRange(expressionText, projectionTypes) : null,
            staticType != null ? staticSpelledNumber(expressionText, projectionTypes) : null);
        final ResultSetColumn checked = staticType != null && projectsUncheckedConstant(expressionText, projectionTypes)
            ? column.withUncheckedConstant() : column;
        return staticType != null && projectsFoldedDouble(expressionText, projectionTypes)
            ? checked.withFoldedDouble() : checked;
    }

    /** Whether a select item projects a double live's compiler folds; see TableColumn#isFoldedDouble. */
    private static boolean projectsFoldedDouble(final String expressionText,
                                                final ExpressionEvaluator projectionTypes) {
        if (expressionText == null || expressionText.trim().isEmpty()) {
            return false;
        }
        try {
            return projectionTypes.projectsFoldedDouble(ExpressionEvaluator.parse(expressionText));
        } catch (final RuntimeException undetermined) {
            return false;
        }
    }

    /** Whether a select item projects a constant wrapped into a VARIANT; see TableColumn#isUncheckedConstant. */
    private static boolean projectsUncheckedConstant(final String expressionText,
                                                     final ExpressionEvaluator projectionTypes) {
        if (expressionText == null || expressionText.trim().isEmpty()) {
            return false;
        }
        try {
            return projectionTypes.projectsUncheckedConstant(ExpressionEvaluator.parse(expressionText));
        } catch (final RuntimeException undetermined) {
            return false;
        }
    }

    /**
     * The NUMBER a select item spells when it is a bare string literal, or a column carrying one out of a
     * derived relation; null otherwise — see TableColumn#getSpelledNumber.
     */
    private DataType staticSpelledNumber(final String expressionText,
                                         final ExpressionEvaluator projectionTypes) {
        if (expressionText == null || expressionText.trim().isEmpty()) {
            return null;
        }
        try {
            return projectionTypes.spelledNumber(ExpressionEvaluator.parse(expressionText));
        } catch (final RuntimeException undetermined) {
            return null;
        }
    }

    /** The interval a select item's values lie in, propagated from its source's statistics, or null. */
    private ValueRange staticProjectionRange(final String expressionText,
                                             final ExpressionEvaluator projectionTypes) {
        if (expressionText == null || expressionText.trim().isEmpty()) {
            return null;
        }
        try {
            return projectionTypes.inferStaticRange(ExpressionEvaluator.parse(expressionText));
        } catch (final RuntimeException undetermined) {
            return null;
        }
    }

    /**
     * Whether a select item is the word NULL itself. Live declares such a column VARCHAR(0) — a width no
     * column can hold and no literal can produce, which is its way of saying nothing was named — where
     * Frostlake reported the 16MB placeholder every other untyped item falls back to.
     *
     * <p>The REPORTED type only. The static type stays undetermined, as it always was, and that is what
     * keeps an INSERT of a NULL into a VARIANT / ARRAY / OBJECT column working: the type check has
     * nothing concrete to compare and skips the column. Setting the static type instead is what refused
     * fourteen vendor loaders when the same width was tried on the set-operation fold.
     *
     * @param expressionText the select item as written
     * @return true for the bare NULL literal, in any case, parenthesised or not
     */
    private boolean isBareNullLiteral(final String expressionText) {
        if (expressionText == null) {
            return false;
        }
        try {
            // A call folded to an untyped NULL projects as the bare word does (see UntypedNullFold).
            return UntypedNullFold.isUntypedNull(ExpressionEvaluator.parse(expressionText));
        } catch (final RuntimeException notAnExpression) {
            return false;
        }
    }

    /**
     * The type a projected column REPORTS: the static type for the semi-structured family, the placeholder
     * for everything else. See {@link #projectedColumn} for why the family restriction is exact — every
     * other family is recovered from the VALUES by the CTAS reader, while a semi-structured cell is not a
     * Number/Boolean/temporal and falls through that recovery to the placeholder every time.
     */
    private DataType semiStructuredReportedType(final DataType reportedType, final DataType staticType) {
        if (staticType != null) {
            return staticType;
        }
        return reportedType;
    }

    /** The static type of one VALUES cell's expression, or null when the channel cannot type it. */
    private static DataType valuesItemStaticType(final String exprText, final ExpressionEvaluator evaluator) {
        try {
            return evaluator.inferStaticType(ExpressionEvaluator.parse(exprText));
        } catch (final RuntimeException undetermined) {
            if (SqlCompilationError.isCompilationError(undetermined.getMessage())) {
                throw undetermined;
            }
            return null;
        }
    }

    /** The static type of a select item's expression text, or null when it cannot be determined —
     *  including when the text is not something the expression grammar can parse on its own. */
    private DataType staticProjectionType(final String expressionText,
                                          final ExpressionEvaluator projectionTypes,
                                          final ParserRuleContext origin) {
        if (expressionText == null || expressionText.trim().isEmpty()) {
            return null;
        }
        // The item's own place in the statement, so a refusal this walk reaches reports the offset of
        // the operator IN THE STATEMENT rather than within the re-parsed item text.
        // NESTED, not absolute: a select item's offset is relative to whatever fragment is already in
        // force — the whole statement for a CTAS, but a view's re-parsed definition for a view.
        final SourcePosition displaced = ExpressionSource.beginNested(origin == null ? null
            : new SourcePosition(origin.getStart().getLine(), origin.getStart().getCharPositionInLine()));
        try {
            return projectionTypes.inferStaticType(ExpressionEvaluator.parse(expressionText));
        } catch (final RuntimeException undetermined) {
            // A COMPILATION error is a refusal the channel reached deliberately — an operand pair
            // Snowflake rejects — and must not be swallowed with the ordinary "could not determine".
            if (SqlCompilationError.isCompilationError(undetermined.getMessage())) {
                throw undetermined;
            }
            return null;
        } finally {
            ExpressionSource.end(displaced);
        }
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
        } else if (value instanceof BinaryValue) {
            // Typed from a VALUE rather than a declaration, so it is derived by construction and takes
            // the non-fixed spelling — a declared column's type never comes from here.
            return BinaryType.VARBINARY;
        } else if (value instanceof GeoValue) {
            return ((GeoValue) value).isGeography() ? GeographyType.GEOGRAPHY : GeometryType.GEOMETRY;
        } else if (value instanceof VariantValue) {
            final VariantValue variant = (VariantValue) value;
            return variant.isJsonObject() ? ObjectType.OBJECT
                : variant.isJsonArray() ? ArrayType.ARRAY
                : VariantType.VARIANT;
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
        final List<TableColumn> newColumns = new ArrayList<>();
        final List<TableColumn> originalColumns = table.getColumns();

        for (int i = 0; i < originalColumns.size(); i++) {
            final TableColumn originalCol = originalColumns.get(i);
            final String newName = i < columnAliases.size() ? columnAliases.get(i) : originalCol.getName();
            final TableColumn newCol = new TableColumn(
                newName,
                originalCol.getDataType(),
                originalCol.isNullable(),
                originalCol.getDefaultValue(),
                originalCol.isPrimaryKey(),
                originalCol.isUnique(),
                originalCol.isAutoIncrement()
            );
            // Renaming a derived column keeps its declared type, so it keeps its static-type verdict too:
            // FROM (SELECT f FROM t) d (g) makes d.g exactly as FILE-typed as the f it renames.
            newCol.setStaticallyTyped(originalCol.isStaticallyTyped());
            // …and whatever literal it projects: FROM (SELECT '12.5' AS t, …) AS d(t, n) folds as the
            // literal as well (live-verified).
            newCol.setSpelledNumber(originalCol.getSpelledNumber());
            newCol.setUncheckedConstant(originalCol.isUncheckedConstant());
            newCol.setFoldedDouble(originalCol.isFoldedDouble());
            newColumns.add(newCol);
        }

        // Renaming keeps what the relation reads: a catalog table under new names still reads every row.
        final Table renamed = new Table(table.getName(), newColumns, table.isTemporary());
        renamed.setRelationStatistics(table.isCatalogResident()
            ? new DerivedStatistics(this, null, table, null) : table.getRelationStatistics());
        return renamed;
    }

    // Extract equi-join key column indices ({leftIdx[], rightIdx[]}) from a join condition, for the
    // hash-join candidate filter. Returns null unless the condition is a conjunction containing
    // column=column equalities across the two tables. The full condition is always re-confirmed per
    // candidate pair, so this must only avoid FALSE NEGATIVES — hence it bails (null) on anything it
    // cannot classify with certainty (OR, NOT, expression keys, ambiguous/unqualified columns, self-join).


    /**
     * The equi-join keys a comma join can lift out of its WHERE clause, or null when there are none.
     *
     * <p>Deliberately conservative: extractEquiJoinKeys already refuses an OR or a NOT and ignores any
     * conjunct that is not an equality between one column of each side, so what comes back is only ever
     * a PRE-FILTER. Everything the WHERE says is still applied by the WHERE operator afterwards, which
     * is what makes lifting safe rather than a semantic change.
     */
    private int[][] whereEquiJoinKeys(final FrostlakeParser.SelectClauseContext ctx,
                                      final Table leftTable, final Table rightTable,
                                      final Map<String, Table> aliasToTable, final String rightAlias) {
        final FrostlakeParser.WhereClauseContext where = getWhereClause(ctx);
        if (where == null || where.booleanExpr() == null) {
            return null;
        }
        final Map<String, Table> scoped = new HashMap<>(aliasToTable);
        scoped.put(rightAlias, rightTable);
        try {
            return extractEquiJoinKeys(ExpressionEvaluator.parse(getOriginalText(where.booleanExpr())),
                leftTable, rightTable, scoped);
        } catch (final RuntimeException notLiftable) {
            // A WHERE the expression grammar will not parse on its own is simply not pushed down.
            return null;
        }
    }

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
            if (lc != null && rc != null && HashJoinKeyTypes.keysByValue(lc, rc, lt, rt)) {
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
        return resolveColumnInTables(row, tables, aliasToTable, qualifiedName, null);
    }

    /**
     * Resolve like {@link #resolveColumnInTables(Row, List, Map, String)}, additionally reporting
     * HOW the reference resolved through {@code resolvedOffset[0]}: the flat row offset when the
     * value came from the plain table-column scan (a fixed layout position the caller may cache
     * for this context), left untouched when it resolved any other way (literal fallback, merged
     * join key — per-row decisions that must re-run).
     */
    public Object resolveColumnInTables(final Row row, final List<Table> tables,
                                        final Map<String, Table> aliasToTable, final String qualifiedName,
                                        final int[] resolvedOffset) {
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
                if (t.getName().equalsIgnoreCase(qualifier)) {
                    known = true;
                    break;
                }
            }
            if (!known && aliasToTable != null) {
                for (final String a : aliasToTable.keySet()) {
                    if (a.equalsIgnoreCase(qualifier)) {
                        known = true;
                        break;
                    }
                }
            }
            if (!known) {
                throw new RuntimeException("Unknown qualifier (not a joined table/alias): " + qualifier);
            }
        }
        return getQualifiedColumnValueFromTables(row, tables, aliasToTable, qualifiedName, null, resolvedOffset);
    }

    /** Whether {@code text} is a plain dotted reference (identifier parts only) rather than an
     *  expression text routed through this resolver's lenient cleaning. */
    private static boolean isPlainDottedReference(final String text) {
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            final boolean word = Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '.'
                || c == '"';
            if (!word) {
                return false;
            }
        }
        return true;
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
                                                     final String qualifiedName) {
        return getQualifiedColumnValueFromTables(row, tables, aliasToTable, qualifiedName, null, null);
    }

    /** Whether {@code name} is exactly one of the join-key names, which are canonical spellings. */
    private static boolean namesJoinKey(final List<String> joinKeyNames, final String name) {
        for (final String keyName : joinKeyNames) {
            if (keyName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    Object getQualifiedColumnValueFromTables(final Row row, final List<Table> tables,
                                                     final Map<String, Table> aliasToTable,
                                                     String qualifiedName,
                                                     final List<String> joinKeyNames) {
        return getQualifiedColumnValueFromTables(row, tables, aliasToTable, qualifiedName, joinKeyNames, null);
    }

    Object getQualifiedColumnValueFromTables(final Row row, final List<Table> tables,
                                                     final Map<String, Table> aliasToTable,
                                                     String qualifiedName,
                                                     final List<String> joinKeyNames,
                                                     final int[] resolvedOffset) {
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

        // Split qualifier.column without a regex (exactly one dot => table.column). A dot inside a
        // quoted part belongs to that part's name, so only the dots outside quotes split.
        String tableName = null;
        final String columnName;
        final String columnPart;
        final int dotIdx = soleUnquotedDot(qualifiedName);
        if (dotIdx > 0) {
            tableName = qualifiedName.substring(0, dotIdx);  // Keep original case for alias lookup
            columnPart = qualifiedName.substring(dotIdx + 1);
        } else {
            columnPart = qualifiedName;
        }
        columnName = columnPart.toUpperCase();
        // The column part names ONE spelling: a quoted part its content verbatim, and nothing else; an
        // unquoted part its upper-cased fold, and — for this resolver's lenient callers — failing that,
        // any column spelled the same ignoring case.
        final boolean quotedColumn = isClosedQuotedName(columnPart);
        final String exactColumn = quotedColumn
            ? columnPart.substring(1, columnPart.length() - 1).replace("\"\"", "\"") : columnName;

        // Resolve table by its FROM-clause key. A quoted qualifier names its key exactly; an unquoted one also
        // names a key held in another case, unless that key was quoted ("fz" is no FZ) (live-verified).
        Table targetTable = null;
        if (tableName != null) {
            final boolean quotedQualifier = tableName.length() > 1 && tableName.startsWith("\"")
                && tableName.endsWith("\"");
            final String qualifierKey = quotedQualifier
                ? tableName.substring(1, tableName.length() - 1).replace("\"\"", "\"") : tableName;
            if (aliasToTable.containsKey(qualifierKey)) {
                targetTable = aliasToTable.get(qualifierKey);
            } else if (!quotedQualifier) {
                for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
                    if (entry.getKey().equals(entry.getKey().toUpperCase(Locale.ROOT))
                            && entry.getKey().equalsIgnoreCase(qualifierKey)) {
                        targetTable = entry.getValue();
                        break;
                    }
                }
            }
            // Live: an alias REPLACES the table name — with FROM r AS x, the reference r.t is
            // "invalid identifier 'R.T'" in plain, JOIN and ASOF queries alike. A dotted qualifier
            // naming NO FROM-clause key is an invalid identifier, never a bare-name search.
            // Sequence value reads (seq.NEXTVAL / seq.CURRVAL) are not FROM-clause references and
            // resolve through the sequence machinery instead.
            if (targetTable == null && aliasToTable != null && !aliasToTable.isEmpty()
                    && !"NEXTVAL".equalsIgnoreCase(columnName) && !"CURRVAL".equalsIgnoreCase(columnName)
                    && isPlainDottedReference(qualifiedName)) {
                // TYPED only for a genuine dotted reference: this resolver also receives raw
                // EXPRESSION texts (GROUP BY / ORDER BY keys like COALESCE(a.b, c)) whose parens
                // were stripped above — those must soft-fail so the caller's expression path runs.
                throw new InvalidQualifierException((quotedQualifier ? tableName : tableName.toUpperCase()) + "."
                    + columnName);
            }
        }

        // A bare reference to a USING / NATURAL join key reads the MERGED column: the first non-null
        // among the per-side copies (an outer join null-extends one side's copy — live, the
        // right-only row of a FULL JOIN answers the right side's key for the bare name in SELECT,
        // WHERE, GROUP BY and ORDER BY alike). A qualified reference keeps reading its own side.
        if (tableName == null && joinKeyNames != null && namesJoinKey(joinKeyNames, exactColumn)) {
            int keyOffset = 0;
            boolean present = false;
            for (final Table table : tables) {
                final List<TableColumn> cols = table.getColumns();
                for (int i = 0; i < cols.size(); i++) {
                    if (cols.get(i).getName().equals(exactColumn)) {
                        present = true;
                        if (keyOffset + i < row.getValues().size()) {
                            final Object value = row.getValue(keyOffset + i);
                            if (value != null) {
                                return value;
                            }
                        }
                        break;
                    }
                }
                keyOffset += cols.size();
            }
            if (present) {
                return null;
            }
        }

        // Search through all tables. NOTE — live raises "ambiguous column name 'T'" for a bare
        // name carried by more than one table OF THE SAME FROM SCOPE, but this flat list also
        // carries OUTER tables injected for correlated subqueries, whose inner FROM must shadow
        // them (a vendor loader's `(SELECT MAX(file_date) FROM tmp_files)` inside a join over
        // tmp_files is live-legal). Until resolution is scope-aware, first match wins here.
        // The exact spelling is searched across every table before any case-insensitive match is: of two
        // columns that differ only in case, the one the name spells is the one it reads.
        for (int pass = 0; pass < (quotedColumn ? 1 : 2); pass++) {
            int offset = 0;
            for (final Table table : tables) {
                final boolean isTargetTable = (targetTable == null) || (table == targetTable);

                if (isTargetTable) {
                    final List<TableColumn> cols = table.getColumns();
                    for (int i = 0; i < cols.size(); i++) {
                        final String name = cols.get(i).getName();
                        if (pass == 0 ? name.equals(exactColumn) : name.equalsIgnoreCase(exactColumn)) {
                            // A plain column hit at a FIXED layout position — report it so the caller
                            // may cache the offset for this context.
                            if (resolvedOffset != null) {
                                resolvedOffset[0] = offset + i;
                            }
                            return row.getValue(offset + i);
                        }
                    }
                }

                offset += table.columnCount();
            }
        }

        throw new RuntimeException(SqlCompilationError.invalidIdentifier(qualifiedName));
    }

    /**
     * The position of the one dot outside double quotes in a reference's text, or -1 when there is none
     * or more than one — {@code t."a.b"} splits at its first dot only.
     */
    private static int soleUnquotedDot(final String text) {
        int found = -1;
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (c == '.' && !quoted) {
                if (found >= 0) {
                    return -1;
                }
                found = i;
            }
        }
        return found;
    }

    /**
     * One outer-row binding of a LATERAL join: on the first pass under its upper-cased key, keeping the
     * first column that folds to it; on the second under its exact spelling, overriding the fold.
     */
    private static void bindLateral(final Map<String, Object> context, final String key, final Object value,
                                    final int pass) {
        if (pass == 0) {
            context.putIfAbsent(key.toUpperCase(), value);
        } else {
            context.put(key, value);
        }
    }

    /** Whether a name part is one closed double-quoted identifier, its inner quotes doubled. */
    private static boolean isClosedQuotedName(final String part) {
        if (part.length() < 2 || part.charAt(0) != '"' || part.charAt(part.length() - 1) != '"') {
            return false;
        }
        for (int i = 1; i < part.length() - 1; i++) {
            if (part.charAt(i) == '"') {
                if (i + 1 < part.length() - 1 && part.charAt(i + 1) == '"') {
                    i++;
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    /** True when s matches \d+\.\d+ (a numeric literal like 1.5), to tell it from a qualified name. */
    private static boolean isNumericDotted(final String s) {
        return ValueComparisons.isNumericDotted(s);
    }

    /**
     * Parse a SELECT statement from a string
     */
    FrostlakeParser.SelectStatementContext parseSelectStatement(final String sql) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        final SyntaxErrorListener errorListener = new SyntaxErrorListener(sql);
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final FrostlakeParser parser = new FrostlakeParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        final FrostlakeParser.SelectStatementContext selectStatementContext = parser.selectStatement();
        errorListener.throwIfErrors();
        NamedCallRewrite.apply(selectStatementContext);

        return selectStatementContext;
    }

    /**
     * Evaluate one table-function argument where it is written: its origin is nested at its first token, so
     * a name it cannot resolve is refused at its place in the statement, inside a subquery's text too.
     *
     * @param written        the argument as written
     * @param text           the text evaluated for it
     * @param lateralContext the outer row's names, or null
     * @return the argument's value
     */
    private Object evaluateTableFunctionArgument(final ParserRuleContext written, final String text,
                                                 final Map<String, Object> lateralContext) {
        return evaluateTableFunctionArgument(new SourcePosition(written.getStart().getLine(),
            written.getStart().getCharPositionInLine()), text, lateralContext);
    }

    private Object evaluateTableFunctionArgument(final SourcePosition origin, final String text,
                                                 final Map<String, Object> lateralContext) {
        final SourcePosition displaced = ExpressionSource.beginNested(origin);
        try {
            return lateralContext != null && !lateralContext.isEmpty()
                ? evaluateExpressionWithLateralContextSimple(text, lateralContext)
                : evaluateExpression(text, null, (Table) null);
        } finally {
            ExpressionSource.end(displaced);
        }
    }

    /**
     * A named table-function argument's value (see {@link #namedArgumentText}): a subquery written bare is
     * evaluated parenthesised, so its text begins one column before the subquery.
     */
    private Object evaluateNamedTableFunctionArgument(final FrostlakeParser.NamedArgumentContext argCtx,
                                                      final Map<String, Object> lateralContext) {
        if (argCtx.argumentRow() != null) {
            // A parenthesized list is one value holding each of its elements, in order.
            final List<Object> values = new ArrayList<>();
            for (final FrostlakeParser.ExpressionContext element : argCtx.argumentRow().expression()) {
                values.add(evaluateTableFunctionArgument(element, getOriginalText(element), lateralContext));
            }
            return values;
        }
        if (argCtx.expression() != null) {
            return evaluateTableFunctionArgument(argCtx.expression(), namedArgumentText(argCtx), lateralContext);
        }
        final Token start = argCtx.selectStatement().getStart();
        return evaluateTableFunctionArgument(new SourcePosition(start.getLine(), start.getCharPositionInLine() - 1),
            namedArgumentText(argCtx), lateralContext);
    }

    /**
     * A positional table-function argument's value, as {@link #funcArgTexts} reads it: a subquery written bare
     * is evaluated parenthesised, so its text begins one column before the subquery.
     */
    private Object evaluatePositionalTableFunctionArgument(final FrostlakeParser.FunctionArgContext arg,
                                                           final Map<String, Object> lateralContext) {
        if (arg.selectStatement() != null) {
            final Token start = arg.selectStatement().getStart();
            return evaluateTableFunctionArgument(new SourcePosition(start.getLine(), start.getCharPositionInLine() - 1),
                "(" + getOriginalText(arg.selectStatement()) + ")", lateralContext);
        }
        final FrostlakeParser.ExpressionContext value = ((FrostlakeParser.ValueExprContext) arg.booleanExpr()).expression();
        return evaluateTableFunctionArgument(value, getOriginalText(value), lateralContext);
    }

    /**
     * Evaluate expression with lateral context - simplified version for table function arguments
     * Tries to resolve column references from lateralContext first, then falls back to standard evaluation
     */
    private Object evaluateExpressionWithLateralContextSimple(String expr, final Map<String, Object> lateralContext) {
        expr = expr.trim();

        // Try direct lookup in lateral context (e.g., "n" or "N")
        final String upperExpr = expr.toUpperCase();
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
            lateralArgumentTypes.typeArgumentsOf(evaluator);
            return evaluator.evaluate(expr, dummyRow);
        } catch (final Exception e) {
            // A name the arguments cannot resolve is the statement's compilation error, in its own words.
            if (e instanceof RuntimeException && SqlCompilationError.isCompilationError(e.getMessage())) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Unable to evaluate expression '" + expr + "' in LATERAL context: " + e.getMessage(), e);
        }
    }


    /** Whether a RESULT_SCAN argument is a LAST_QUERY_ID call, which gets its own "no results" wording. */
    private boolean isLastQueryIdCall(final FrostlakeParser.ExpressionContext argExpr) {
        if (!(argExpr instanceof FrostlakeParser.FunctionCallExprContext)) {
            return false;
        }
        final FrostlakeParser.FunctionCallExprContext call =
            (FrostlakeParser.FunctionCallExprContext) argExpr;
        return "LAST_QUERY_ID".equalsIgnoreCase(call.functionName().getText());
    }

    /**
     * The unknown-name sentence for a FROM-less projection, which returns before the walk that raises it
     * for every other query. Live names EVERY unresolvable call in one sentence there too - {@code
     * SELECT r1(), r2(), r3()} is {@code Unknown functions R2, R3.} - where evaluating the items one by
     * one stops at the first name it cannot resolve. A single name already reports itself that way, in
     * the same words, so only the plural case is raised here; and with no FROM there are no columns to
     * scope, which is what the fuller walk spends its time on.
     */
    private void rejectUnresolvableNamesWithoutFrom(final FrostlakeParser.SelectStatementContext stmtCtx,
                                                    final FrostlakeParser.SelectClauseContext ctx) {
        final List<FunctionCallExpression> unresolvable = collectUnresolvableCalls(stmtCtx, ctx);
        if (unresolvable.size() < 2) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.of(
            new ExpressionEvaluator(null, functionRegistry, catalog, this)
                .unknownFunctionSentence(unresolvable)));
    }

}
