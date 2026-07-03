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

    private OperatorContext(final Builder builder) {
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Table table;
        private FunctionRegistry functionRegistry;
        private Map<String, Table> aliasToTable;
        private List<Table> allTables;
        private Map<String, Object> lateralContext;
        private Map<String, Object> additionalContext;
        private RowExpressionEvaluator expressionEvaluator;
        private QueryExecutor queryExecutor;

        public Builder table(final Table table) {
            this.table = table;
            return this;
        }

        public Builder functionRegistry(final FunctionRegistry functionRegistry) {
            this.functionRegistry = functionRegistry;
            return this;
        }

        public Builder aliasToTable(final Map<String, Table> aliasToTable) {
            this.aliasToTable = aliasToTable;
            return this;
        }

        public Builder allTables(final List<Table> allTables) {
            this.allTables = allTables;
            return this;
        }

        public Builder lateralContext(final Map<String, Object> lateralContext) {
            this.lateralContext = lateralContext;
            return this;
        }

        public Builder additionalContext(final Map<String, Object> additionalContext) {
            this.additionalContext = additionalContext;
            return this;
        }

        public Builder expressionEvaluator(final RowExpressionEvaluator expressionEvaluator) {
            this.expressionEvaluator = expressionEvaluator;
            return this;
        }

        public Builder queryExecutor(final QueryExecutor queryExecutor) {
            this.queryExecutor = queryExecutor;
            return this;
        }

        public OperatorContext build() {
            // Table is optional for operators like TableFunctionOperator that don't need input tables
            if (functionRegistry == null) {
                throw new IllegalArgumentException("FunctionRegistry is required");
            }
            return new OperatorContext(this);
        }
    }
}
