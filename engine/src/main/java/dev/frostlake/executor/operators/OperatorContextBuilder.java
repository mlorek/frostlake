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
import java.util.List;
import java.util.Map;

public class OperatorContextBuilder {
    // Package-private, not private: read by {@link OperatorContext} now that this class is a top-level
    // type in the same package rather than a nested one.
    Table table;
    FunctionRegistry functionRegistry;
    Map<String, Table> aliasToTable;
    List<Table> allTables;
    Map<String, Object> lateralContext;
    Map<String, Object> additionalContext;
    RowExpressionEvaluator expressionEvaluator;
    QueryExecutor queryExecutor;

    public OperatorContextBuilder table(final Table table) {
        this.table = table;
        return this;
    }

    public OperatorContextBuilder functionRegistry(final FunctionRegistry functionRegistry) {
        this.functionRegistry = functionRegistry;
        return this;
    }

    public OperatorContextBuilder aliasToTable(final Map<String, Table> aliasToTable) {
        this.aliasToTable = aliasToTable;
        return this;
    }

    public OperatorContextBuilder allTables(final List<Table> allTables) {
        this.allTables = allTables;
        return this;
    }

    public OperatorContextBuilder lateralContext(final Map<String, Object> lateralContext) {
        this.lateralContext = lateralContext;
        return this;
    }

    public OperatorContextBuilder additionalContext(final Map<String, Object> additionalContext) {
        this.additionalContext = additionalContext;
        return this;
    }

    public OperatorContextBuilder expressionEvaluator(final RowExpressionEvaluator expressionEvaluator) {
        this.expressionEvaluator = expressionEvaluator;
        return this;
    }

    public OperatorContextBuilder queryExecutor(final QueryExecutor queryExecutor) {
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
