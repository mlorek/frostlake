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

package dev.frostlake.rt.py;

import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import org.graalvm.polyglot.Value;
import dev.frostlake.types.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes Snowflake-style Python UDTF (User-Defined Table Functions).
 *
 * Supports the class-based handler pattern:
 *   class Handler:
 *     def process(self, *args) -> Iterator[tuple]   # called per row
 *     def end_partition(self) -> Iterator[tuple]    # called at partition end (optional)
 */
public class PythonTableFunctionExecutor {

    private static final Logger logger = LoggerFactory.getLogger(PythonTableFunctionExecutor.class);

    /**
     * Execute a Python UDTF with a single set of arguments (no partition).
     * Calls process(*args) once then end_partition() if defined.
     */
    public static ResultSet executePythonTableFunction(final Function function,
                                                        final List<Object> arguments) {
        return executePythonTableFunctionPartitioned(function, List.of(arguments));
    }

    /**
     * Execute a Python UDTF over multiple input rows (partition semantics).
     * Calls process(*rowArgs) for each row, then end_partition() once.
     */
    public static ResultSet executePythonTableFunctionPartitioned(final Function function,
                                                                   final List<List<Object>> rows) {
        if (function.getUdfLanguage() != UdfLanguage.PYTHON || !function.isTableFunction()) {
            throw new RuntimeException("Function is not a Python table function");
        }

        String handlerClassName = function.getHandler();
        if (handlerClassName == null || handlerClassName.isEmpty()) {
            throw new RuntimeException("HANDLER is required for Python table functions");
        }

        List<ResultSetColumn> outputColumns = buildOutputColumns(function);
        List<Row> outputRows = new ArrayList<>();

        try {
            PythonRuntime.eval(dedent(function.getBody()));

            // Instantiate the handler class
            PythonRuntime.eval("__handler_instance = " + handlerClassName + "()");

            // Call process() for each input row
            List<Parameter> params = function.getParameters();
            for (final List<Object> rowArgs : rows) {
                String callCode = buildProcessCall(params, rowArgs);
                PythonRuntime.eval("__process_result = __handler_instance.process(" + callCode + ")");
                collectRows(PythonRuntime.global("__process_result"), outputColumns.size(), outputRows);
            }

            // Call end_partition() if it exists
            PythonRuntime.eval("__has_end_partition = hasattr(__handler_instance, 'end_partition')");
            final Value hasEnd = PythonRuntime.global("__has_end_partition");
            if (hasEnd != null && hasEnd.isBoolean() && hasEnd.asBoolean()) {
                PythonRuntime.eval("__end_result = __handler_instance.end_partition()");
                collectRows(PythonRuntime.global("__end_result"), outputColumns.size(), outputRows);
            }

        } catch (final Exception e) {
            PythonRuntime.discardContext();
            logger.error("Error executing Python table function {}: {}", function.getName(), e.getMessage(), e);
            throw new RuntimeException("Error executing Python table function " + function.getName()
                + ": " + e.getMessage(), e);
        }

        return new ResultSet(outputColumns, outputRows);
    }

    private static String buildProcessCall(final List<Parameter> params, final List<Object> args) {
        StringBuilder sb = new StringBuilder();
        int count = Math.min(params.size(), args.size());
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(", ");
            Object v = args.get(i);
            if (v == null) {
                sb.append("None");
            } else if (v instanceof String) {
                sb.append("'").append(v.toString().replace("'", "\\'")).append("'");
            } else {
                sb.append(v);
            }
        }
        return sb.toString();
    }

    /**
     * Append the rows a handler call produced. A generator or any iterable yields one row per element;
     * anything else is a single row. A row is a tuple/list of column values, or a bare scalar for a
     * single-column output.
     */
    private static void collectRows(final Value result, final int expectedCols, final List<Row> outputRows) {
        if (result == null || result.isNull()) {
            return;
        }
        // A str is iterable in Python but represents ONE value, so it must not be exploded per character.
        if (!result.isString() && (result.hasIterator() || result.hasArrayElements())) {
            final Value iterator = result.hasIterator() ? result.getIterator() : result;
            if (result.hasIterator()) {
                while (iterator.hasIteratorNextElement()) {
                    outputRows.add(tupleToRow(iterator.getIteratorNextElement(), expectedCols));
                }
                return;
            }
            for (long i = 0; i < result.getArraySize(); i++) {
                outputRows.add(tupleToRow(result.getArrayElement(i), expectedCols));
            }
            return;
        }
        outputRows.add(tupleToRow(result, expectedCols));
    }

    /** One yielded item as a Row, padded or trimmed to the declared column count. */
    private static Row tupleToRow(final Value item, final int expectedCols) {
        List<Object> values = new ArrayList<>();
        if (item != null && !item.isNull() && !item.isString() && item.hasArrayElements()) {
            for (long i = 0; i < item.getArraySize(); i++) {
                values.add(PythonRuntime.toJava(item.getArrayElement(i)));
            }
        } else {
            values.add(PythonRuntime.toJava(item));
        }
        while (values.size() < expectedCols) {
            values.add(null);
        }
        if (values.size() > expectedCols) {
            values = values.subList(0, expectedCols);
        }
        return new Row(values);
    }

    private static List<ResultSetColumn> buildOutputColumns(final Function function) {
        List<ResultSetColumn> cols = new ArrayList<>();
        List<Parameter> returnCols = function.getReturnColumns();
        if (returnCols != null && !returnCols.isEmpty()) {
            for (final Parameter p : returnCols) {
                cols.add(new ResultSetColumn(p.getName(), p.getDataType()));
            }
        } else {
            // Fallback: generic output columns
            cols.add(new ResultSetColumn("VALUE", StringType.VARCHAR));
        }
        return cols;
    }

    private static String dedent(final String text) {
        if (text == null || text.isEmpty()) return text;
        String[] lines = text.split("\n");
        int minIndent = Integer.MAX_VALUE;
        for (final String line : lines) {
            if (line.trim().isEmpty()) continue;
            int indent = 0;
            for (final char c : line.toCharArray()) {
                if (c == ' ') indent++;
                else if (c == '\t') indent += 4;
                else break;
            }
            minIndent = Math.min(minIndent, indent);
        }
        if (minIndent == 0 || minIndent == Integer.MAX_VALUE) return text;
        StringBuilder sb = new StringBuilder();
        for (final String line : lines) {
            if (line.trim().isEmpty()) sb.append("\n");
            else sb.append(line.substring(Math.min(minIndent, line.length()))).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Called per-row when function is used as a LATERAL table function over a table's rows.
     */
    public static ResultSet executePartitioned(final Function function,
                                                final List<List<Object>> inputRows) {
        return executePythonTableFunctionPartitioned(function, inputRows);
    }
}
