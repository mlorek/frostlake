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

import dev.frostlake.executor.udf.TablePartitionRows;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;
import org.graalvm.polyglot.Value;
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
public final class PythonTableFunctionExecutor {

    /** Static helpers only — never instantiated. */
    private PythonTableFunctionExecutor() {
    }

    private static final Logger logger = LoggerFactory.getLogger(PythonTableFunctionExecutor.class);

    /** The global a row's argument is bound to for its {@code process} call, numbered from 0. */
    private static final String TABLE_ARGUMENT = "__fl_table_argument";

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
        final TablePartitionRows written = executePartition(function, rows);
        final List<Row> all = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            all.addAll(written.writtenFor(i));
        }
        all.addAll(written.atEnd());
        return new ResultSet(buildOutputColumns(function), all);
    }

    /**
     * Runs the handler over one partition of the function's input: one instance reads each row with
     * {@code process} and is then told the partition is through with {@code end_partition}, when it has one.
     *
     * @param function the function
     * @param rows     the partition's rows, each the function's arguments
     * @return the rows the handler wrote for each input row and once the partition was through
     */
    public static TablePartitionRows executePartition(final Function function, final List<List<Object>> rows) {
        if (function.getUdfLanguage() != UdfLanguage.PYTHON || !function.isTableFunction()) {
            throw new RuntimeException("Function is not a Python table function");
        }

        final String handlerClassName = function.getHandler();
        if (handlerClassName == null || handlerClassName.isEmpty()) {
            throw new RuntimeException("HANDLER is required for Python table functions");
        }

        final List<ResultSetColumn> outputColumns = buildOutputColumns(function);
        // The declared columns convert what the handler writes; the one column a function declaring none answers
        // takes it as the runtime holds it.
        final boolean declared = function.getReturnColumns() != null && !function.getReturnColumns().isEmpty();
        final List<List<Row>> writtenPerRow = new ArrayList<>();
        final List<Row> atEnd = new ArrayList<>();

        PythonModuleFrame frame = null;
        try {
            frame = PythonRuntime.enterModule();
            PythonRuntime.eval(dedent(function.getBody()));

            // Instantiate the handler class; a dotted one is a module's attribute, imported as CREATE resolved it.
            if (PythonHandlerCheck.isDotted(handlerClassName)) {
                PythonRuntime.bind("__fl_handler_name", handlerClassName);
                PythonRuntime.eval(PythonHandlerCheck.RESOLVE_DOTTED + "__handler_instance = __fl_handler()");
            } else {
                PythonRuntime.eval("__handler_instance = " + handlerClassName + "()");
            }

            // Call process() for each input row, each argument bound as the Python value its parameter's type maps
            // to (see PythonRuntime.bindArgument).
            final List<Parameter> params = function.getParameters();
            for (final List<Object> rowArgs : rows) {
                final StringBuilder call = new StringBuilder("__process_result = __handler_instance.process(");
                final int count = Math.min(params.size(), rowArgs.size());
                for (int i = 0; i < count; i++) {
                    PythonRuntime.bindArgument(TABLE_ARGUMENT + i, rowArgs.get(i), params.get(i).getDataType());
                    call.append(i == 0 ? "" : ", ").append(TABLE_ARGUMENT).append(i);
                }
                PythonRuntime.eval(call.append(')').toString());
                final List<Row> written = new ArrayList<>();
                collectRows(PythonRuntime.global("__process_result"), function, outputColumns, declared, written);
                writtenPerRow.add(written);
            }

            // Call end_partition() if it exists
            PythonRuntime.eval("__has_end_partition = hasattr(__handler_instance, 'end_partition')");
            final Value hasEnd = PythonRuntime.global("__has_end_partition");
            if (hasEnd != null && hasEnd.isBoolean() && hasEnd.asBoolean()) {
                PythonRuntime.eval("__end_result = __handler_instance.end_partition()");
                collectRows(PythonRuntime.global("__end_result"), function, outputColumns, declared, atEnd);
            }

        } catch (final PythonReturnRefusal refused) {
            // What the handler wrote does not fit its column's type; the refusal is worded already.
            throw refused;
        } catch (final Exception e) {
            // Worded before the context is settled (see PythonRuntime.settleAfter).
            final String refusal = "Error executing Python table function " + function.getName() + ": "
                + e.getMessage();
            logger.error("Error executing Python table function {}: {}", function.getName(), e.getMessage(), e);
            if (!PythonRuntime.settleAfter(e)) {
                throw new RuntimeException(refusal);
            }
            throw new RuntimeException(refusal, e);
        } finally {
            PythonRuntime.leaveModule(frame);
        }

        return new TablePartitionRows(writtenPerRow, atEnd);
    }

    /**
     * Append the rows a handler call produced. A generator or any iterable yields one row per element;
     * anything else is a single row. A row is a tuple/list of column values, or a bare scalar for a
     * single-column output.
     */
    private static void collectRows(final Value result, final Function function,
                                    final List<ResultSetColumn> columns, final boolean declared,
                                    final List<Row> outputRows) {
        if (result == null || result.isNull()) {
            return;
        }
        // A str is iterable in Python but represents ONE value, so it must not be exploded per character.
        if (!result.isString() && (result.hasIterator() || result.hasArrayElements())) {
            final Value iterator = result.hasIterator() ? result.getIterator() : result;
            if (result.hasIterator()) {
                while (iterator.hasIteratorNextElement()) {
                    outputRows.add(tupleToRow(iterator.getIteratorNextElement(), function, columns, declared));
                }
                return;
            }
            for (long i = 0; i < result.getArraySize(); i++) {
                outputRows.add(tupleToRow(result.getArrayElement(i), function, columns, declared));
            }
            return;
        }
        outputRows.add(tupleToRow(result, function, columns, declared));
    }

    /**
     * One yielded item as a Row, padded or trimmed to the declared column count. A declared column holds its value
     * as its type does, converted as Python converts and judged as the account judges it (live-verified, see
     * {@link PythonReturn}): a NUMBER(10,2) column holds {@code Decimal('1.255')} as 1.26 and a VARCHAR one
     * {@code str()} of what it is given.
     */
    private static Row tupleToRow(final Value item, final Function function, final List<ResultSetColumn> columns,
                                  final boolean declared) {
        final List<Value> cells = new ArrayList<>();
        if (item != null && !item.isNull() && !item.isString() && item.hasArrayElements()) {
            for (long i = 0; i < item.getArraySize(); i++) {
                cells.add(item.getArrayElement(i));
            }
        } else {
            cells.add(item);
        }
        final List<Object> values = new ArrayList<>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            values.add(i < cells.size() ? cellValue(cells.get(i), function, declared ? columns.get(i) : null) : null);
        }
        return new Row(values);
    }

    /** One value a handler wrote, as its declared column holds it, or as the runtime holds it for no column. */
    private static Object cellValue(final Value cell, final Function function, final ResultSetColumn column) {
        if (column != null) {
            final Object held = PythonReturn.declared(cell, column.getDataType(), function.getName(),
                function.getHandler());
            if (held != PythonReturn.AS_IS) {
                return held;
            }
        }
        return PythonRuntime.toJava(cell);
    }

    private static List<ResultSetColumn> buildOutputColumns(final Function function) {
        final List<ResultSetColumn> cols = new ArrayList<>();
        final List<Parameter> returnCols = function.getReturnColumns();
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
        final String[] lines = text.split("\n");
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
        final StringBuilder sb = new StringBuilder();
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
