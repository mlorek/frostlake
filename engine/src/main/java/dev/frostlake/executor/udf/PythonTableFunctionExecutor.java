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

package dev.frostlake.executor.udf;

import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;
import org.python.core.PyGenerator;
import org.python.core.PyObject;
import org.python.core.PyTuple;
import org.python.util.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import org.python.core.Py;
import org.python.core.PyBoolean;
import org.python.core.PyException;
import org.python.core.PyFloat;
import org.python.core.PyInteger;
import org.python.core.PyLong;
import org.python.core.PyString;

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

        try (PythonInterpreter interp = new PythonInterpreter()) {
            String body = dedent(function.getBody());
            interp.exec(body);

            // Instantiate the handler class
            interp.exec("__handler_instance = " + handlerClassName + "()");
            PyObject instance = interp.get("__handler_instance");

            // Call process() for each input row
            List<Parameter> params = function.getParameters();
            for (final List<Object> rowArgs : rows) {
                String callCode = buildProcessCall(params, rowArgs);
                interp.exec("__process_result = __handler_instance.process(" + callCode + ")");
                PyObject processResult = interp.get("__process_result");
                collectRows(processResult, outputColumns.size(), outputRows);
            }

            // Call end_partition() if it exists
            interp.exec("__has_end_partition = hasattr(__handler_instance, 'end_partition')");
            PyObject hasEnd = interp.get("__has_end_partition");
            if (hasEnd != null && !hasEnd.equals(Py.False)) {
                interp.exec("__end_result = __handler_instance.end_partition()");
                PyObject endResult = interp.get("__end_result");
                collectRows(endResult, outputColumns.size(), outputRows);
            }

        } catch (final Exception e) {
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

    private static void collectRows(final PyObject result, final int expectedCols,
                                     final List<Row> outputRows) {
        if (result == null) return;

        // Handle generator / iterable
        if (result instanceof PyGenerator || isIterable(result)) {
            try {
                PyObject iter = result.__iter__();
                PyObject item;
                while ((item = iter.__iternext__()) != null) {
                    outputRows.add(pyTupleToRow(item, expectedCols));
                }
            } catch (final PyException ignored) {
                // StopIteration — normal end of generator
            }
        } else {
            // Single tuple/value returned directly
            outputRows.add(pyTupleToRow(result, expectedCols));
        }
    }

    private static boolean isIterable(final PyObject obj) {
        try {
            obj.__iter__();
            return true;
        } catch (final Exception e) {
            return false;
        }
    }

    private static Row pyTupleToRow(final PyObject item, final int expectedCols) {
        List<Object> values = new ArrayList<>();
        if (item instanceof PyTuple) {
            PyTuple tuple = (PyTuple) item;
            for (int i = 0; i < tuple.__len__(); i++) {
                values.add(pyToJava(tuple.__getitem__(i)));
            }
        } else {
            // Single-column output
            values.add(pyToJava(item));
        }
        // Pad or trim to expected column count
        while (values.size() < expectedCols) values.add(null);
        if (values.size() > expectedCols) values = values.subList(0, expectedCols);
        return new Row(values);
    }

    private static Object pyToJava(final PyObject obj) {
        if (obj == null || obj == Py.None) return null;
        try {
            Object j = obj.__tojava__(Object.class);
            if (j instanceof PyInteger) return ((PyInteger) j).getValue();
            if (j instanceof PyLong)    return ((PyLong) j).getValue().longValue();
            if (j instanceof PyFloat)   return ((PyFloat) j).getValue();
            if (j instanceof PyBoolean) return ((PyBoolean) j).getBooleanValue();
            if (j instanceof PyString)  return j.toString();
            if (j instanceof Number || j instanceof String || j instanceof Boolean) return j;
            return obj.toString();
        } catch (final Exception e) {
            return obj.toString();
        }
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
