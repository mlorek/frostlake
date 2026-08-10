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

package dev.frostlake.executor.operators;

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.model.Table;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Context object passed through the operator pipeline.
 * Contains table metadata, function registry, and other execution state.
 */
public class OperatorContext {

    private final Table table;
    private final FunctionRegistry functionRegistry;
    private final Map<String, Table> aliasToTable;
    private final List<Table> allTables;
    private final Map<String, Object> lateralContext;
    private final Map<String, Object> additionalContext;
    private final RowExpressionEvaluator expressionEvaluator;
    private final QueryExecutor queryExecutor;

    OperatorContext(final OperatorContextBuilder builder) {
        this.table = builder.table;
        this.functionRegistry = builder.functionRegistry;
        this.aliasToTable = builder.aliasToTable != null ? builder.aliasToTable : new HashMap<>();
        this.allTables = builder.allTables;
        this.lateralContext = builder.lateralContext;
        this.additionalContext = builder.additionalContext != null ? builder.additionalContext : new HashMap<>();
        this.expressionEvaluator = builder.expressionEvaluator;
        this.queryExecutor = builder.queryExecutor;
    }

    public Table getTable() {
        return table;
    }

    public FunctionRegistry getFunctionRegistry() {
        return functionRegistry;
    }

    public Map<String, Table> getAliasToTable() {
        return aliasToTable;
    }

    public List<Table> getAllTables() {
        return allTables;
    }

    public Map<String, Object> getLateralContext() {
        return lateralContext;
    }

    public Map<String, Object> getAdditionalContext() {
        return additionalContext;
    }

    public RowExpressionEvaluator getExpressionEvaluator() {
        return expressionEvaluator;
    }

    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    public boolean hasMultipleTables() {
        return allTables != null && allTables.size() > 1;
    }

    public boolean hasLateralContext() {
        return lateralContext != null && !lateralContext.isEmpty();
    }

    public boolean hasCustomExpressionEvaluator() {
        return expressionEvaluator != null;
    }

    public static OperatorContextBuilder builder() {
        return new OperatorContextBuilder();
    }

}
