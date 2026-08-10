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

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.ShowCommandExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.operators.ResultSetProvider;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for SHOW and DESCRIBE commands:
 * SHOW DATABASES, SHOW SCHEMAS, SHOW TABLES, DESCRIBE TABLE, etc.
 */
public class ShowCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(ShowCommandHandler.class);

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;

    public ShowCommandHandler(final Catalog catalog, final QueryExecutor queryExecutor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
    }

    @Override
    public Catalog getCatalog() {
        return catalog;
    }

    @Override
    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    public ResultSet handleShowStatement(final FrostlakeParser.ShowStatementContext ctx) {
        ResultSet result = handleShowStatementInternal(ctx);
        result = applyLikeFilter(result, getLikePattern(ctx));
        final ShowModifierProfile profile = ShowModifierProfile.forStatement(ctx);
        if (profile.sortsByName()) {
            result = sortByName(result);
        }
        final FrostlakeParser.ShowTailContext tail = ctx.showTail();
        if (tail != null) {
            int literalIndex = 0;
            if (tail.STARTS() != null) {
                final String prefix = stripQuotes(tail.STRING_LITERAL(literalIndex++).getText());
                if (profile.honorsStartsWith()) {
                    result = applyStartsWith(result, prefix);
                }
            }
            if (tail.LIMIT() != null) {
                final int limit = Integer.parseInt(tail.INTEGER_LITERAL().getText());
                requirePositiveLimit(limit);
                final String fromName = tail.FROM() != null
                    ? stripQuotes(tail.STRING_LITERAL(literalIndex).getText()) : null;
                if (profile.honorsLimit()) {
                    result = applyLimitFrom(result, limit, fromName);
                }
            }
            // WITH PRIVILEGES p1, p2 is accepted but the listing is not privilege-filtered.
        }
        if (ctx.TERSE() != null) {
            result = applyTerse(result, profile);
        }
        return result;
    }

    /**
     * {@code LIMIT 0} is rejected, and rejected before the listing is even scoped.
     *
     * <p>Live-verified on a real account: every one of the twenty listings probed answers
     * {@code SHOW ... LIMIT 0} with "page size "0" must be greater than 0 in limit clause" — including
     * STAGES, SEQUENCES, WAREHOUSES and FILE FORMATS, which go on to ignore a positive LIMIT entirely.
     * The check is therefore on the clause, not on whether the listing paginates, so it lives here
     * rather than behind {@link ShowModifierProfile#honorsLimit()}.
     */
    private static void requirePositiveLimit(final int limit) {
        if (limit <= 0) {
            throw new RuntimeException("page size \"" + limit + "\" must be greater than 0 in limit clause");
        }
    }

    private ResultSet handleShowStatementInternal(final FrostlakeParser.ShowStatementContext ctx) {
        final ShowCommandExecutor showExecutor = queryExecutor.getShowExecutor();
        if (ctx.DATABASES() != null) {
            return showExecutor.showDatabases();
        } else if (ctx.SCHEMAS() != null) {
            if (ctx.ACCOUNT() != null) {
                return showExecutor.showSchemasInAccount();
            }
            String dbName = null;
            if (ctx.IN() != null && ctx.identifier() != null) {
                dbName = getText(ctx.identifier());
            }
            return showExecutor.showSchemas(dbName);
        } else if (ctx.DYNAMIC() != null && ctx.TABLES() != null) {
            if (ctx.ACCOUNT() != null) {
                return showExecutor.showDynamicTablesInAccount();
            }
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showDynamicTablesInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) schemaName = getText(ctx.qualifiedName());
            return showExecutor.showDynamicTables(schemaName);
        } else if (ctx.HYBRID() != null && ctx.TABLES() != null) {
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showHybridTablesInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.IN() != null && ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showHybridTables(schemaName);
        } else if (ctx.ICEBERG() != null) {
            return withoutRows(showExecutor.showTables(null));
        } else if (ctx.TABLES() != null) {
            if (ctx.ACCOUNT() != null) {
                return showExecutor.showTablesInAccount();
            }
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showTablesInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.IN() != null && ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showTables(schemaName);
        } else if (ctx.COLUMNS() != null) {
            final String name = ctx.qualifiedName() != null ? getText(ctx.qualifiedName()) : null;
            // The SCOPE keywords list every relation in a container rather than resolving one object.
            if (ctx.ACCOUNT() != null) {
                return showExecutor.showColumnsInAccount();
            }
            if (ctx.SCHEMA() != null) {
                return showExecutor.showColumnsInSchema(name);
            }
            if (ctx.DATABASE() != null) {
                return showExecutor.showColumnsInDatabase(name);
            }
            return showExecutor.showColumnsScoped(name, ctx.VIEW() != null);
        } else if (ctx.MATERIALIZED() != null && ctx.VIEWS() != null) {
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showMaterializedViewsInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showMaterializedViews(schemaName);
        } else if (ctx.VIEWS() != null) {
            if (ctx.ACCOUNT() != null) {
                return showExecutor.showViewsInAccount();
            }
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showViewsInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.IN() != null && ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showViews(schemaName);
        } else if (ctx.USERS() != null) {
            return showExecutor.showUsers();
        } else if (ctx.ROLES() != null) {
            return showExecutor.showRoles();
        } else if (ctx.FUNCTIONS() != null) {
            if (ctx.BUILTIN() != null) {
                // SHOW BUILTIN FUNCTIONS never lists user functions and ignores the IN scope entirely —
                // live-verified: IN SCHEMA / IN DATABASE / IN ACCOUNT all return the same 1134
                // rows the bare form does, and the one UDF in the current schema is in none of them.
                return showExecutor.showBuiltinFunctions();
            }
            if (ctx.CLASS() != null || ctx.APPLICATION() != null) {
                throw showScopeDoesNotExist(ctx);
            }
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return ctx.USER() != null
                    ? showExecutor.showUserFunctionsInDatabase(getText(ctx.qualifiedName()))
                    : showExecutor.showFunctionsInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showFunctions(schemaName, ctx.USER() != null);
        } else if (ctx.PROCEDURES() != null) {
            if (ctx.BUILTIN() != null) {
                // SHOW BUILTIN PROCEDURES never lists user procedures and ignores the IN scope, exactly
                // as SHOW BUILTIN FUNCTIONS does — live-verified: with one user procedure in
                // the current schema it returns 32 rows to SHOW PROCEDURES' 33, and IN SCHEMA /
                // IN DATABASE return that same 32 without the user procedure.
                return showExecutor.showBuiltinProcedures();
            }
            if (ctx.APPLICATION() != null || ctx.CLASS() != null) {
                throw showScopeDoesNotExist(ctx);
            }
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return ctx.USER() != null
                    ? showExecutor.showUserProceduresInDatabase(getText(ctx.qualifiedName()))
                    : showExecutor.showProceduresInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showProcedures(schemaName, ctx.USER() != null);
        } else if (ctx.STREAMS() != null) {
            if (ctx.ACCOUNT() != null) {
                return showExecutor.showStreamsInAccount();
            }
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showStreamsInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showStreams(schemaName);
        } else if (ctx.TASKS() != null) {
            if (ctx.ACCOUNT() != null) {
                return showExecutor.showTasksInAccount();
            }
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showTasksInDatabase(getText(ctx.qualifiedName()));
            }
            final String schemaName = ctx.qualifiedName() != null ? getText(ctx.qualifiedName()) : null;
            return showExecutor.showTasks(schemaName);
        } else if (ctx.PIPES() != null) {
            String like = null;
            if (ctx.LIKE() != null && ctx.STRING_LITERAL() != null) {
                final String raw = ctx.STRING_LITERAL().getText();
                like = raw.startsWith("'") && raw.endsWith("'") ? raw.substring(1, raw.length() - 1) : raw;
            }
            if (ctx.ACCOUNT() != null) {
                return showExecutor.showPipesInAccount(like);
            }
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showPipesInDatabase(getText(ctx.qualifiedName()), like);
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showPipes(schemaName, like);
        } else if (ctx.SEQUENCES() != null) {
            if (ctx.ACCOUNT() != null) {
                return showExecutor.showSequencesInAccount();
            }
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showSequencesInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showSequences(schemaName);
        } else if (ctx.CORTEX() != null && ctx.SERVICES() != null) {
            String cortexLike = null;
            if (ctx.LIKE() != null && ctx.STRING_LITERAL() != null) {
                final String raw = ctx.STRING_LITERAL().getText();
                cortexLike = raw.startsWith("'") && raw.endsWith("'")
                    ? raw.substring(1, raw.length() - 1) : raw;
            }
            if (ctx.ACCOUNT() != null) {
                return showExecutor.showCortexSearchServicesInAccount(cortexLike);
            }
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showCortexSearchServicesInDatabase(
                    getText(ctx.qualifiedName()), cortexLike);
            }
            String cortexSchemaName = null;
            if (ctx.qualifiedName() != null) {
                cortexSchemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showCortexSearchServices(cortexSchemaName, cortexLike);
        } else if (ctx.COMPUTE() != null && ctx.POOLS() != null) {
            return showExecutor.showComputePools();
        } else if (ctx.COMPUTE() != null && ctx.POOL() != null && ctx.FAMILIES() != null) {
            return showExecutor.showComputePoolInstanceFamilies();
        } else if (ctx.WAREHOUSES() != null) {
            return showExecutor.showWarehouses();
        } else if (ctx.STAGES() != null) {
            if (ctx.ACCOUNT() != null) {
                return showExecutor.showStagesInAccount();
            }
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showStagesInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showStages(schemaName);
        } else if (ctx.FILE() != null && ctx.FORMATS() != null) {
            if (ctx.ACCOUNT() != null) {
                return showExecutor.showFileFormatsInAccount();
            }
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showFileFormatsInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showFileFormats(schemaName);
        } else if (ctx.JOIN() != null && ctx.POLICIES() != null) {
            if (ctx.ACCOUNT() != null) {
                return showExecutor.showJoinPoliciesInAccount();
            }
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showJoinPoliciesInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showJoinPolicies(schemaName);
        } else if (ctx.AGGREGATION() != null && ctx.POLICIES() != null) {
            if (ctx.ACCOUNT() != null) {
                return showExecutor.showAggregationPoliciesInAccount();
            }
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showAggregationPoliciesInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showAggregationPolicies(schemaName);
        } else if (ctx.PROJECTION() != null && ctx.POLICIES() != null) {
            if (ctx.ACCOUNT() != null) {
                return showExecutor.showProjectionPoliciesInAccount();
            }
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showProjectionPoliciesInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showProjectionPolicies(schemaName);
        } else if (ctx.CONTACTS() != null) {
            if (ctx.ACCOUNT() != null) {
                return showExecutor.showContactsInAccount();
            }
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showContactsInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showContacts(schemaName);
        } else if (ctx.MASKING() != null && ctx.POLICIES() != null) {
            if (ctx.ACCOUNT() != null) {
                return showExecutor.showMaskingPoliciesInAccount();
            }
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showMaskingPoliciesInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showMaskingPolicies(schemaName);
        } else if (ctx.ROW() != null && ctx.POLICIES() != null) {
            if (ctx.ACCOUNT() != null) {
                return showExecutor.showRowAccessPoliciesInAccount();
            }
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showRowAccessPoliciesInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showRowAccessPolicies(schemaName);
        } else if (ctx.KEYS() != null) {
            final String scopeKind;
            final String scopeName;
            if (ctx.IN() == null) {
                scopeKind = "TABLE";
                scopeName = null;
            } else if (ctx.ACCOUNT() != null) {
                scopeKind = "ACCOUNT";
                scopeName = null;
            } else if (ctx.DATABASE() != null) {
                scopeKind = "DATABASE";
                scopeName = ctx.identifier() != null ? getText(ctx.identifier()) : null;
            } else if (ctx.SCHEMA() != null) {
                scopeKind = "SCHEMA";
                scopeName = ctx.qualifiedName() != null ? getText(ctx.qualifiedName()) : null;
            } else {
                scopeKind = "TABLE";     // IN TABLE name?, or a bare qualified table name
                scopeName = ctx.qualifiedName() != null ? getText(ctx.qualifiedName()) : null;
            }
            if (ctx.IMPORTED() != null) {
                return showExecutor.showImportedKeys(scopeKind, scopeName);
            }
            return showExecutor.showKeysScoped(ctx.PRIMARY() != null, scopeKind, scopeName);
        } else if (ctx.TAGS() != null) {
            if (ctx.ACCOUNT() != null) {
                return showExecutor.showTagsInAccount();
            }
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showTagsInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showTags(schemaName);
        } else if (ctx.GRANTS() != null) {
            return handleShowGrants(ctx);
        } else if (ctx.PARAMETERS() != null) {
            String like = null;
            if (ctx.LIKE() != null && ctx.STRING_LITERAL() != null) {
                final String raw = ctx.STRING_LITERAL().getText();
                like = raw.startsWith("'") && raw.endsWith("'") ? raw.substring(1, raw.length() - 1) : raw;
            }
            if (ctx.TASK() != null && ctx.identifier() != null) {
                return showExecutor.showParametersInTask(getText(ctx.identifier()), like);
            }
            if (ctx.WAREHOUSE() != null && ctx.identifier() != null) {
                return showExecutor.showParametersInWarehouse(getText(ctx.identifier()), like);
            }
            return showExecutor.showParameters(like);
        } else if (ctx.OBJECTS() != null) {
            if (ctx.ACCOUNT() != null) {
                return showExecutor.showObjectsInAccount();
            }
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showObjectsInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showObjects(schemaName);
        } else if (ctx.ORGANIZATION() != null && ctx.ACCOUNTS() != null) {
            return showExecutor.showOrganizationAccounts();
        } else if (ctx.ACCOUNTS() != null) {
            return showExecutor.showAccounts();
        } else if (ctx.LOCKS() != null) {
            // SHOW LOCKS [IN ACCOUNT] — the account scope prepends a session column (live shape).
            return showExecutor.showLocks(ctx.ACCOUNT() != null);
        } else if (ctx.TRANSACTIONS() != null) {
            String like = null;
            if (ctx.LIKE() != null && ctx.STRING_LITERAL() != null) {
                final String raw = ctx.STRING_LITERAL().getText();
                like = raw.startsWith("'") && raw.endsWith("'") ? raw.substring(1, raw.length() - 1) : raw;
            }
            return showExecutor.showTransactions(like);
        } else if (ctx.VARIABLES() != null) {
            return showExecutor.showVariables();
        }

        throw new RuntimeException("Unsupported SHOW statement");
    }

    public ResultSet handleDescribeStatement(final FrostlakeParser.DescribeStatementContext ctx) {
        final ShowCommandExecutor showExecutor = queryExecutor.getShowExecutor();
        if (ctx.DYNAMIC() != null && ctx.TABLE() != null) {
            final String dtName = ctx.qualifiedName() != null ? getText(ctx.qualifiedName()) : getText(ctx.identifier());
            // The column list comes from projecting the dynamic table itself, zero rows needed.
            return showExecutor.describeDynamicTable(dtName, new ResultSetProvider() {
                @Override
                public ResultSet getResultSet() {
                    final List<ResultSet> results = queryExecutor.execute("SELECT * FROM " + dtName + " LIMIT 0");
                    return results.get(results.size() - 1);
                }
            });
        }
        if (ctx.TABLE() != null || ctx.VIEW() != null) {
            // DESCRIBE has its own column shape (name|type|kind|null?|default|primary key|…) —
            // NOT the SHOW COLUMNS shape, which leads with table_name/schema_name. The three relation
            // kinds describe each other freely; the one they named is spoken only when the object is
            // missing ("Materialized view 'X' does not exist or not authorized." — live-verified).
            final String describedKind = ctx.MATERIALIZED() != null ? "Materialized view"
                : ctx.VIEW() != null ? "View" : "Table";
            return showExecutor.describeRelation(getText(ctx.qualifiedName()), describedKind);
        } else if (ctx.PIPE() != null) {
            final String pipeName = getText(ctx.identifier());
            return showExecutor.describePipe(pipeName);
        } else if (ctx.SEQUENCE() != null) {
            final String sequenceName = getText(ctx.identifier());
            return showExecutor.describeSequence(sequenceName);
        } else if (ctx.TASK() != null) {
            final String taskName = getText(ctx.identifier());
            return showExecutor.describeTask(taskName);
        } else if (ctx.STREAM() != null) {
            final String streamName = getText(ctx.identifier());
            return showExecutor.describeStream(streamName);
        } else if (ctx.CORTEX() != null) {
            return showExecutor.describeCortexSearchService(getText(ctx.qualifiedName()));
        } else if (ctx.COMPUTE() != null && ctx.POOL() != null) {
            return showExecutor.describeComputePool(getText(ctx.identifier()));
        } else if (ctx.WAREHOUSE() != null) {
            final String warehouseName = getText(ctx.identifier());
            return showExecutor.describeWarehouse(warehouseName);
        } else if (ctx.STAGE() != null) {
            final String stageName = getText(ctx.identifier());
            return showExecutor.describeStage(stageName);
        } else if (ctx.TAG() != null) {
            final String tagName = getText(ctx.identifier());
            return showExecutor.describeTag(tagName);
        } else if (ctx.FUNCTION() != null) {
            requireRoutineArgumentTypes(ctx, getText(ctx.qualifiedName()));
            return showExecutor.describeFunction(getText(ctx.qualifiedName()));
        } else if (ctx.PROCEDURE() != null) {
            requireRoutineArgumentTypes(ctx, getText(ctx.qualifiedName()));
            return showExecutor.describeProcedure(getText(ctx.qualifiedName()));
        } else if (ctx.USER() != null) {
            return showExecutor.describeUser(getText(ctx.identifier()));
        } else if (ctx.SEARCH() != null && ctx.OPTIMIZATION() != null) {
            return showExecutor.describeSearchOptimization(getText(ctx.qualifiedName()));
        } else if (ctx.JOIN() != null) {
            return showExecutor.describeJoinPolicy(getText(ctx.qualifiedName()));
        } else if (ctx.AGGREGATION() != null) {
            return showExecutor.describeAggregationPolicy(getText(ctx.qualifiedName()));
        } else if (ctx.PROJECTION() != null) {
            return showExecutor.describeProjectionPolicy(getText(ctx.qualifiedName()));
        } else if (ctx.MASKING() != null) {
            return showExecutor.describeMaskingPolicy(getText(ctx.qualifiedName()));
        } else if (ctx.ROW() != null) {
            return showExecutor.describeRowAccessPolicy(getText(ctx.qualifiedName()));
        } else if (ctx.FILE() != null && ctx.FORMAT() != null) {
            return showExecutor.describeFileFormat(getText(ctx.qualifiedName()));
        } else if (ctx.RESULT() != null) {
            return queryExecutor.describeResult(ctx);
        }

        if (ctx.DYNAMIC() != null && ctx.TABLE() != null) {
            final String dtName = ctx.qualifiedName() != null ? getText(ctx.qualifiedName()) : getText(ctx.identifier());
            // The column list comes from projecting the dynamic table itself, zero rows needed.
            return showExecutor.describeDynamicTable(dtName, new ResultSetProvider() {
                @Override
                public ResultSet getResultSet() {
                    final List<ResultSet> results = queryExecutor.execute("SELECT * FROM " + dtName + " LIMIT 0");
                    return results.get(results.size() - 1);
                }
            });
        }
        // Default: assume it's a table
        final String name = ctx.qualifiedName() != null ? getText(ctx.qualifiedName()) : getText(ctx.identifier());
        return showExecutor.showColumns(name);
    }

    /** Extract LIKE pattern from context — returns null if none specified. */
    private String getLikePattern(final FrostlakeParser.ShowStatementContext ctx) {
        if (ctx.LIKE() == null || ctx.STRING_LITERAL() == null) return null;
        final String raw = ctx.STRING_LITERAL().getText();
        return raw.startsWith("'") && raw.endsWith("'") ? raw.substring(1, raw.length() - 1) : raw;
    }

    /** The index of the name-ish column SHOW filters (LIKE / STARTS WITH / LIMIT FROM) apply to, or -1. */
    private static int nameColumnIndex(final ResultSet rs) {
        for (final String col : new String[]{"name", "parameter_name", "key", "account_name", "column_name"}) {
            try {
                return rs.getColumnIndex(col);
            } catch (final RuntimeException ignored) {
            }
        }
        return -1;
    }

    private static String stripQuotes(final String raw) {
        return raw != null && raw.startsWith("'") && raw.endsWith("'")
            ? raw.substring(1, raw.length() - 1) : raw;
    }

    /**
     * Order an object listing the way a real account returns it: by database, then schema, then
     * name, each byte-wise. Single-scope listings reduce to the name ordering; the wider
     * {@code IN DATABASE} / {@code IN ACCOUNT} scopes group by database and schema first
     * (live-verified).
     *
     * <p>Byte-wise and not case-insensitively: live, a schema holding DT_A, T_A…T_D and a quoted
     * "t_lower" lists the lowercase name last, which is {@link String#compareTo}'s order and not
     * {@code CASE_INSENSITIVE_ORDER}'s. Nulls sort first so a listing with an unnamed row cannot throw.
     *
     * <p>This is also what makes {@code LIMIT}/{@code FROM} deterministic, so it runs before them.
     */
    private ResultSet sortByName(final ResultSet rs) {
        if (rs == null) return rs;
        final int nameIdx = columnIndexOrMissing(rs, "name");
        if (nameIdx < 0) return rs;
        final int databaseIdx = columnIndexOrMissing(rs, "database_name");
        final int schemaIdx = columnIndexOrMissing(rs, "schema_name");
        final List<Row> sorted = new ArrayList<>(rs.getRows());
        Collections.sort(sorted, new ShowNameComparator(databaseIdx, schemaIdx, nameIdx));
        return new ResultSet(rs.getColumns(), sorted);
    }

    /** SHOW ... STARTS WITH 'prefix': case-sensitive prefix filter on the name column (Snowflake semantics). */
    private ResultSet applyStartsWith(final ResultSet rs, final String prefix) {
        if (prefix == null || rs == null) return rs;
        final int nameIdx = nameColumnIndex(rs);
        if (nameIdx < 0) return rs;
        final List<Row> filtered = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            final Object val = row.getValue(nameIdx);
            if (val != null && val.toString().startsWith(prefix)) {
                filtered.add(row);
            }
        }
        return new ResultSet(rs.getColumns(), filtered);
    }

    /** SHOW ... LIMIT n [FROM 'name']: keep rows whose name sorts after 'name', then the first n. */
    private ResultSet applyLimitFrom(final ResultSet rs, final int limit, final String fromName) {
        if (rs == null) return rs;
        final int nameIdx = nameColumnIndex(rs);
        final List<Row> kept = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            if (fromName != null && nameIdx >= 0) {
                final Object val = row.getValue(nameIdx);
                if (val == null || val.toString().compareTo(fromName) <= 0) {
                    continue;
                }
            }
            kept.add(row);
            if (kept.size() >= limit) {
                break;
            }
        }
        return new ResultSet(rs.getColumns(), kept);
    }

    /**
     * SHOW TERSE ...: project onto the column shape the profile prescribes for this listing.
     *
     * <p>The target list is a shape to produce, not a subset to keep. A column absent from the untrimmed
     * listing still appears — Snowflake's own TERSE output does that, and it is why the previous
     * keep-what-we-have projection came out wrong: {@code SHOW TERSE SCHEMAS} lost {@code kind} and
     * {@code schema_name} (3 columns instead of 5), {@code SHOW TERSE DATABASES} lost three of its five,
     * and {@code SHOW TERSE USERS} answered 2 columns where a real account answers 14.
     *
     * <p>An empty target list means TERSE is inert for this listing and the result is returned whole.
     */
    private ResultSet applyTerse(final ResultSet rs, final ShowModifierProfile profile) {
        if (rs == null || profile.terseColumns().isEmpty()) return rs;
        final List<Integer> sourceIndex = new ArrayList<>();
        final List<ResultSetColumn> cols = new ArrayList<>();
        for (final String col : profile.terseColumns()) {
            final int idx = columnIndexOrMissing(rs, col);
            sourceIndex.add(idx);
            cols.add(idx >= 0 ? rs.getColumns().get(idx) : new ResultSetColumn(col, StringType.VARCHAR));
        }
        final int materializedIdx = columnIndexOrMissing(rs, "is_materialized");
        final int tableNameIdx = columnIndexOrMissing(rs, "table_name");
        final List<Row> rows = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            final List<Object> values = new ArrayList<>();
            for (int i = 0; i < sourceIndex.size(); i++) {
                final int idx = sourceIndex.get(i);
                if (idx >= 0) {
                    values.add(row.getValue(idx));
                } else {
                    values.add(terseFallback(profile.terseColumns().get(i), profile, row,
                        materializedIdx, tableNameIdx));
                }
            }
            rows.add(new Row(values));
        }
        return new ResultSet(cols, rows);
    }

    /**
     * The value for a TERSE column the untrimmed listing does not carry.
     *
     * <p>Only three of them are ever anything but null. {@code kind} is the listing's own object kind —
     * the literal STANDARD for DATABASES and DELTA for STREAMS, or VIEW / MATERIALIZED_VIEW decided per
     * row from {@code is_materialized} for VIEWS, which is the one case where two kinds share a listing.
     * {@code tableOn} is the stream's base table under its bare name, which the untrimmed
     * SHOW STREAMS carries fully qualified as {@code table_name}.
     */
    private Object terseFallback(final String column, final ShowModifierProfile profile, final Row row,
                                 final int materializedIdx, final int tableNameIdx) {
        if ("kind".equals(column)) {
            if (profile.terseKindFromMaterializedFlag() && materializedIdx >= 0) {
                return isTruthy(row.getValue(materializedIdx)) ? "MATERIALIZED_VIEW" : "VIEW";
            }
            return profile.terseKind();
        }
        if ("tableOn".equals(column) && tableNameIdx >= 0) {
            final Object tableName = row.getValue(tableNameIdx);
            // The terse form names the base table alone, where the full listing qualifies it.
            return tableName == null ? null : QualifiedName.parse(tableName.toString()).last();
        }
        return null;
    }

    /** Whether a listing's yes/no cell reads as set, however the listing spells it. */
    private static boolean isTruthy(final Object value) {
        if (value == null) return false;
        final String text = value.toString();
        return "Y".equalsIgnoreCase(text) || "true".equalsIgnoreCase(text);
    }

    /** {@code getColumnIndex} but answering -1 instead of throwing when the listing has no such column. */
    private static int columnIndexOrMissing(final ResultSet rs, final String column) {
        try {
            return rs.getColumnIndex(column);
        } catch (final RuntimeException absent) {
            return -1;
        }
    }

    /** The same columns with no rows — for accepted scopes that list nothing (ICEBERG tables). */
    private static ResultSet withoutRows(final ResultSet rs) {
        return new ResultSet(rs.getColumns(), new ArrayList<>());
    }

    /**
     * The refusal an APPLICATION / APPLICATION PACKAGE / CLASS scope earns on an engine that has no
     * such objects — matching a real account, which parses these scopes and then refuses the named
     * object (live-verified per kind): the APPLICATION flavors answer
     * {@code Application [package] '<NAME>' does not exist or not authorized.} on its own line,
     * while CLASS answers the single-line {@code Object type or Class '<NAME>' …} family. The name
     * is spelled as WRITTEN, upper-folded when unquoted and kept verbatim — quotes included — when
     * quoted.
     */
    private RuntimeException showScopeDoesNotExist(final FrostlakeParser.ShowStatementContext ctx) {
        final String raw = ctx.qualifiedName() != null ? ctx.qualifiedName().getText() : "";
        final String spelled = raw.startsWith("\"") ? raw : raw.toUpperCase();
        if (ctx.CLASS() != null) {
            return new RuntimeException(SqlCompilationError.PREFIX
                + " Object type or Class '" + spelled + "' does not exist or not authorized.");
        }
        final String kind = ctx.PACKAGE() != null ? "Application package" : "Application";
        return new RuntimeException(SqlCompilationError.of(
            kind + " '" + spelled + "' does not exist or not authorized."));
    }

    /** Filter a ResultSet by a LIKE pattern applied to the 'name' column (column index 1). */
    private ResultSet applyLikeFilter(final ResultSet rs, final String pattern) {
        if (pattern == null || rs == null) return rs;
        final int nameIdx = nameColumnIndex(rs);
        if (nameIdx < 0) return rs; // no filterable column — return unfiltered
        // Convert SQL LIKE pattern to regex: % -> .*, _ -> .
        final String regex = pattern.replace(".", "\\.").replace("%", ".*").replace("_", ".");
        final List<Row> filtered = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            final Object val = row.getValue(nameIdx);
            if (val != null && val.toString().matches("(?i)" + regex)) {
                filtered.add(row);
            }
        }
        return new ResultSet(rs.getColumns(), filtered);
    }

    private ResultSet handleShowGrants(final FrostlakeParser.ShowStatementContext ctx) {
        final ShowCommandExecutor showExecutor = queryExecutor.getShowExecutor();
        if (ctx.ON() != null) {
            // SHOW GRANTS ON objectType identifier
            final String objectType = ctx.objectType().getText().toUpperCase();
            final String objectName = getText(ctx.identifier());
            return showExecutor.showGrantsOnObject(objectType, objectName);
        } else if (ctx.TO() != null) {
            // SHOW GRANTS TO USER/ROLE identifier
            final String targetType = ctx.USER() != null ? "USER" : "ROLE";
            final String targetName = getText(ctx.identifier());
            return showExecutor.showGrantsTo(targetType, targetName);
        }
        if (ctx.OF() != null && ctx.ROLE() != null && ctx.identifier() != null) {
            // SHOW GRANTS OF ROLE identifier
            return showExecutor.showGrantsOfRole(getText(ctx.identifier()));
        }
        if (ctx.ON() == null && ctx.TO() == null && ctx.OF() == null) {
            // Bare SHOW GRANTS: what the current user holds.
            return showExecutor.showGrantsForCurrentUser();
        }
        throw new RuntimeException("Invalid SHOW GRANTS syntax");
    }

    /**
     * A routine name is not enough to describe it — routines overload, so Snowflake insists on the
     * argument-type list. Live-verified on a real account: {@code DESCRIBE PROCEDURE qr}
     * and {@code DESCRIBE FUNCTION dfn} both fail "Argument types of function '&lt;NAME&gt;' must be
     * specified.", while {@code DESCRIBE FUNCTION dfn(INTEGER)} and {@code DESCRIBE PROCEDURE qr()}
     * describe. The list itself may be empty for a no-argument routine.
     */
    private void requireRoutineArgumentTypes(final FrostlakeParser.DescribeStatementContext ctx,
                                             final String routineName) {
        if (ctx.LPAREN() == null) {
            throw new RuntimeException("Argument types of function '"
                + routineName.toUpperCase() + "' must be specified.");
        }
    }
}
