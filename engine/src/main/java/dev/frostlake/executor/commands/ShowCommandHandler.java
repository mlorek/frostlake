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
import dev.frostlake.metastore.Catalog;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import java.util.ArrayList;
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
        return applyLikeFilter(result, getLikePattern(ctx));
    }

    private ResultSet handleShowStatementInternal(final FrostlakeParser.ShowStatementContext ctx) {
        final ShowCommandExecutor showExecutor = queryExecutor.getShowExecutor();
        if (ctx.DATABASES() != null) {
            return showExecutor.showDatabases();
        } else if (ctx.SCHEMAS() != null) {
            String dbName = null;
            if (ctx.IN() != null && ctx.identifier() != null) {
                dbName = getText(ctx.identifier());
            }
            return showExecutor.showSchemas(dbName);
        } else if (ctx.DYNAMIC() != null && ctx.TABLES() != null) {
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
        } else if (ctx.TABLES() != null) {
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showTablesInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.IN() != null && ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showTables(schemaName);
        } else if (ctx.COLUMNS() != null) {
            String tableName = getText(ctx.qualifiedName());
            return showExecutor.showColumns(tableName);
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
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showFunctionsInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showFunctions(schemaName, ctx.USER() != null);
        } else if (ctx.PROCEDURES() != null) {
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showProceduresInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showProcedures(schemaName, ctx.USER() != null);
        } else if (ctx.STREAMS() != null) {
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showStreamsInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showStreams(schemaName);
        } else if (ctx.TASKS() != null) {
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showTasksInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = ctx.qualifiedName() != null ? getText(ctx.qualifiedName()) : null;
            return showExecutor.showTasks(schemaName);
        } else if (ctx.PIPES() != null) {
            String like = null;
            if (ctx.LIKE() != null && ctx.STRING_LITERAL() != null) {
                String raw = ctx.STRING_LITERAL().getText();
                like = raw.startsWith("'") && raw.endsWith("'") ? raw.substring(1, raw.length() - 1) : raw;
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
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showSequencesInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showSequences(schemaName);
        } else if (ctx.WAREHOUSES() != null) {
            return showExecutor.showWarehouses();
        } else if (ctx.STAGES() != null) {
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showStagesInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showStages(schemaName);
        } else if (ctx.FILE() != null && ctx.FORMATS() != null) {
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showFileFormatsInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showFileFormats(schemaName);
        } else if (ctx.MASKING() != null && ctx.POLICIES() != null) {
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showMaskingPoliciesInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showMaskingPolicies(schemaName);
        } else if (ctx.ROW() != null && ctx.POLICIES() != null) {
            if (ctx.DATABASE() != null && ctx.qualifiedName() != null) {
                return showExecutor.showRowAccessPoliciesInDatabase(getText(ctx.qualifiedName()));
            }
            String schemaName = null;
            if (ctx.qualifiedName() != null) {
                schemaName = getText(ctx.qualifiedName());
            }
            return showExecutor.showRowAccessPolicies(schemaName);
        } else if (ctx.PRIMARY() != null && ctx.KEYS() != null) {
            return showExecutor.showPrimaryKeys(ctx.qualifiedName() != null ? getText(ctx.qualifiedName()) : null);
        } else if (ctx.UNIQUE() != null && ctx.KEYS() != null) {
            return showExecutor.showUniqueKeys(ctx.qualifiedName() != null ? getText(ctx.qualifiedName()) : null);
        } else if (ctx.TAGS() != null) {
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
                String raw = ctx.STRING_LITERAL().getText();
                like = raw.startsWith("'") && raw.endsWith("'") ? raw.substring(1, raw.length() - 1) : raw;
            }
            return showExecutor.showParameters(like);
        } else if (ctx.SESSIONS() != null) {
            String like = null;
            if (ctx.LIKE() != null && ctx.STRING_LITERAL() != null) {
                String raw = ctx.STRING_LITERAL().getText();
                like = raw.startsWith("'") && raw.endsWith("'") ? raw.substring(1, raw.length() - 1) : raw;
            }
            return showExecutor.showSessions(like);
        } else if (ctx.OBJECTS() != null) {
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
            return showExecutor.showLocks();
        } else if (ctx.TRANSACTIONS() != null) {
            String like = null;
            if (ctx.LIKE() != null && ctx.STRING_LITERAL() != null) {
                String raw = ctx.STRING_LITERAL().getText();
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
            String dtName = ctx.qualifiedName() != null ? getText(ctx.qualifiedName()) : getText(ctx.identifier());
            return showExecutor.describeDynamicTable(dtName);
        }
        if (ctx.TABLE() != null || ctx.VIEW() != null || (ctx.MATERIALIZED() != null && ctx.VIEW() != null)) {
            String tableName = getText(ctx.qualifiedName());
            return showExecutor.showColumns(tableName);
        } else if (ctx.PIPE() != null) {
            String pipeName = getText(ctx.identifier());
            return showExecutor.describePipe(pipeName);
        } else if (ctx.SEQUENCE() != null) {
            String sequenceName = getText(ctx.identifier());
            return showExecutor.describeSequence(sequenceName);
        } else if (ctx.TASK() != null) {
            String taskName = getText(ctx.identifier());
            return showExecutor.describeTask(taskName);
        } else if (ctx.STREAM() != null) {
            String streamName = getText(ctx.identifier());
            return showExecutor.describeStream(streamName);
        } else if (ctx.WAREHOUSE() != null) {
            String warehouseName = getText(ctx.identifier());
            return showExecutor.describeWarehouse(warehouseName);
        } else if (ctx.STAGE() != null) {
            String stageName = getText(ctx.identifier());
            return showExecutor.describeStage(stageName);
        } else if (ctx.TAG() != null) {
            String tagName = getText(ctx.identifier());
            return showExecutor.describeTag(tagName);
        } else if (ctx.FUNCTION() != null) {
            return showExecutor.describeFunction(getText(ctx.qualifiedName()));
        } else if (ctx.PROCEDURE() != null) {
            return showExecutor.describeProcedure(getText(ctx.qualifiedName()));
        } else if (ctx.USER() != null) {
            return showExecutor.describeUser(getText(ctx.identifier()));
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
            String dtName = ctx.qualifiedName() != null ? getText(ctx.qualifiedName()) : getText(ctx.identifier());
            return showExecutor.describeDynamicTable(dtName);
        }
        // Default: assume it's a table
        String name = ctx.qualifiedName() != null ? getText(ctx.qualifiedName()) : getText(ctx.identifier());
        return showExecutor.showColumns(name);
    }

    /** Extract LIKE pattern from context — returns null if none specified. */
    private String getLikePattern(final FrostlakeParser.ShowStatementContext ctx) {
        if (ctx.LIKE() == null || ctx.STRING_LITERAL() == null) return null;
        String raw = ctx.STRING_LITERAL().getText();
        return raw.startsWith("'") && raw.endsWith("'") ? raw.substring(1, raw.length() - 1) : raw;
    }

    /** Filter a ResultSet by a LIKE pattern applied to the 'name' column (column index 1). */
    private ResultSet applyLikeFilter(final ResultSet rs, final String pattern) {
        if (pattern == null || rs == null) return rs;
        // Find a name-like column to filter on
        int nameIdx = -1;
        for (final String col : new String[]{"name", "parameter_name", "key", "account_name"}) {
            try { nameIdx = rs.getColumnIndex(col); break; }
            catch (final RuntimeException ignored) {}
        }
        if (nameIdx < 0) return rs; // no filterable column — return unfiltered
        // Convert SQL LIKE pattern to regex: % -> .*, _ -> .
        String regex = pattern.replace(".", "\\.").replace("%", ".*").replace("_", ".");
        List<Row> filtered = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            Object val = row.getValue(nameIdx);
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
            String objectType = ctx.objectType().getText().toUpperCase();
            String objectName = getText(ctx.identifier());
            return showExecutor.showGrantsOnObject(objectType, objectName);
        } else if (ctx.TO() != null) {
            // SHOW GRANTS TO USER/ROLE identifier
            String targetType = ctx.USER() != null ? "USER" : "ROLE";
            String targetName = getText(ctx.identifier());
            return showExecutor.showGrantsTo(targetType, targetName);
        }
        throw new RuntimeException("Invalid SHOW GRANTS syntax");
    }
}
