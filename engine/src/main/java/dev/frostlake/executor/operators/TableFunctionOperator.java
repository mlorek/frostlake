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

import dev.frostlake.functions.TableFunction;
import dev.frostlake.functions.table.ResultScan;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TABLE FUNCTION operator - executes table-valued functions.
 *
 * This operator handles execution of table functions like GENERATOR,
 * RESULT_SCAN, and other table-valued functions that generate rows.
 *
 * Table functions differ from regular operators as they don't take
 * input rows - they generate rows from scratch based on their arguments.
 */
public class TableFunctionOperator implements Operator {
    private static final Logger logger = LoggerFactory.getLogger(TableFunctionOperator.class);

    private final String functionName;
    private final TableFunction tableFunction;
    private final Map<String, Object> namedArguments;
    private final List<Object> positionalArguments;
    private final ResultSetProvider resultProvider;

    /**
     * Create a table function operator with named arguments.
     *
     * @param functionName Name of the table function
     * @param tableFunction The table function to execute
     * @param namedArguments Named arguments (e.g., ROWCOUNT => 10)
     */
    public TableFunctionOperator(final String functionName,
                                 final TableFunction tableFunction,
                                 final Map<String, Object> namedArguments) {
        this.functionName = functionName;
        this.tableFunction = tableFunction;
        this.namedArguments = namedArguments;
        this.positionalArguments = null;
        this.resultProvider = null;
    }

    /**
     * Create a table function operator with positional arguments.
     *
     * @param functionName Name of the table function
     * @param tableFunction The table function to execute
     * @param positionalArguments Positional arguments
     */
    public TableFunctionOperator(final String functionName,
                                 final TableFunction tableFunction,
                                 final List<Object> positionalArguments) {
        this.functionName = functionName;
        this.tableFunction = tableFunction;
        this.namedArguments = null;
        this.positionalArguments = positionalArguments;
        this.resultProvider = null;
    }

    /**
     * Create a table function operator with a custom result provider.
     * Useful for special cases like RESULT_SCAN with LAST_QUERY_ID().
     *
     * @param functionName Name of the table function
     * @param resultProvider Provider that produces the ResultSet
     */
    public TableFunctionOperator(final String functionName,
                                 final ResultSetProvider resultProvider) {
        this.functionName = functionName;
        this.tableFunction = null;
        this.namedArguments = null;
        this.positionalArguments = null;
        this.resultProvider = resultProvider;
    }

    @Override
    public List<Row> execute(final List<Row> input, final OperatorContext context) {
        logger.debug("Executing table function: {}", functionName);

        ResultSet result;

        // Use custom provider if provided
        if (resultProvider != null) {
            result = resultProvider.getResultSet();
        } else if (namedArguments != null) {
            // Execute with named arguments
            result = executeWithNamedArgs();
        } else if (positionalArguments != null) {
            // Execute with positional arguments
            result = executeWithPositionalArgs();
        } else {
            throw new IllegalStateException("No arguments or provider provided for table function");
        }

        if (result == null) {
            logger.warn("Table function {} returned null result", functionName);
            return new ArrayList<>();
        }

        logger.debug("Table function {} generated {} rows with {} columns",
            functionName, result.getRows().size(),
            result.getColumns().size());

        return result.getRows();
    }

    @Override
    public String getDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append("TABLE[").append(functionName).append("(");

        if (namedArguments != null && !namedArguments.isEmpty()) {
            List<String> argStrs = new ArrayList<>();
            namedArguments.forEach((final var key, final var value) ->
                argStrs.add(key + " => " + value));
            desc.append(String.join(", ", argStrs));
        } else if (positionalArguments != null && !positionalArguments.isEmpty()) {
            desc.append(positionalArguments.size()).append(" args");
        } else if (resultProvider != null) {
            desc.append("custom provider");
        }

        desc.append(")]");
        return desc.toString();
    }

    /**
     * Execute table function with named arguments.
     */
    private ResultSet executeWithNamedArgs() {
        if (tableFunction == null) {
            throw new IllegalStateException("No table function provided");
        }

        logger.debug("Executing {} with named arguments: {}", functionName, namedArguments);

        try {
            return tableFunction.execute(namedArguments);
        } catch (final Exception e) {
            logger.error("Failed to execute table function {}: {}", functionName, e.getMessage());
            throw new RuntimeException("Failed to execute table function " + functionName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Execute table function with positional arguments.
     */
    private ResultSet executeWithPositionalArgs() {
        if (tableFunction == null) {
            throw new IllegalStateException("No table function provided");
        }

        logger.debug("Executing {} with {} positional arguments", functionName, positionalArguments.size());

        try {
            // For positional arguments, need special handling per function
            // Most table functions in Snowflake use named arguments
            // RESULT_SCAN is an exception that takes a single query ID

            if ("RESULT_SCAN".equalsIgnoreCase(functionName) && positionalArguments.size() == 1) {
                if (tableFunction instanceof ResultScan) {
                    String queryId = positionalArguments.get(0).toString();
                    return ((ResultScan) tableFunction).execute(queryId);
                }
            }

            // Delegate to the function's own positional-arg execute method (e.g. UDTFs)
            return tableFunction.execute(positionalArguments);
        } catch (final Exception e) {
            logger.error("Failed to execute table function {}: {}", functionName, e.getMessage());
            throw new RuntimeException("Failed to execute table function " + functionName + ": " + e.getMessage(), e);
        }
    }
}
