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

    /** The name of the module {@link #ROWS} is made into on a CPython worker. */
    private static final String ROWS_MODULE = "__frostlake_rows";

    /** The name of the module {@link #SHAPE} is made into on GraalPy. */
    private static final String SHAPE_MODULE = "__frostlake_shape";

    /**
     * Shapes what a handler call answered into rows of cells, the same on both backends ({@code raw}): None is no row,
     * a str, a bytes or a bytearray is one value and anything else that is not iterable one row, and any other
     * iterable one row per element; an item that is a tuple or a list is the row's cells, any other item the row's one
     * cell. A None item ends the call's rows: {@code return None}, {@code [None]} or {@code []} skips the input row,
     * and with {@code yield} the rows yielded after a None are ignored (Snowflake's UDTF documentation).
     */
    private static final String SHAPE = """
        def raw(result):
            if result is None:
                return []
            if isinstance(result, (str, bytes, bytearray)) or not hasattr(result, '__iter__'):
                items = [result]
            else:
                items = list(result)
            shaped = []
            for item in items:
                if item is None:
                    break
                shaped.append(list(item) if isinstance(item, (list, tuple)) else [item])
            return shaped
        """;

    /**
     * {@link #SHAPE} on the CPython worker, each row padded or trimmed to the declared width and each cell reported as
     * {@code [outcome, text, value]}: {@code absent} for padding, {@link PythonReturn#CONVERT}'s pair for a declared
     * column, and {@code pass} with the value itself for a column that takes it as the runtime holds it. GraalPy reads
     * the shaped cells as polyglot values instead (see {@link #rowsOf}).
     */
    private static final String ROWS = SHAPE + """
        import __frostlake_returns as _returns
        def rows(result, kinds, scales, width):
            shaped = []
            for cells in raw(result):
                row = []
                for i in range(width):
                    if i >= len(cells):
                        row.append(['absent', '', None])
                    elif kinds[i] is None:
                        row.append(['pass', '', cells[i]])
                    else:
                        outcome, text = _returns.__fl_converted(cells[i], kinds[i], scales[i])
                        row.append([outcome, text, cells[i] if outcome == 'pass' else None])
                shaped.append(row)
            return shaped
        """;

    /** The global a row's argument is bound to for its {@code process} call, numbered from 0. */
    private static final String TABLE_ARGUMENT = "__fl_table_argument";

    /** Python that reads a generator a handler answered, at {@code @}, into a list. */
    private static final String READ_THROUGH =
        "\nif isinstance(@, __import__('types').GeneratorType):\n    @ = list(@)\n";

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

        Object frame = null;
        try {
            frame = PythonInterpreter.enterModule();
            PythonFailureReport.runSource(dedent(function.getBody()), handlerClassName);

            // Instantiate the handler class; a dotted one is a module's attribute, imported as CREATE resolved it.
            if (PythonHandlerCheck.isDotted(handlerClassName)) {
                PythonInterpreter.bind("__fl_handler_name", handlerClassName);
                PythonFailureReport.run(PythonHandlerCheck.RESOLVE_DOTTED + "__handler_instance = __fl_handler()");
            } else {
                PythonFailureReport.run("__handler_instance = " + handlerClassName + "()");
            }

            // Call process() for each input row, each argument bound as the Python value its parameter's type maps
            // to (see PythonInterpreter.bindArgument).
            final List<Parameter> params = function.getParameters();
            for (final List<Object> rowArgs : rows) {
                final StringBuilder call = new StringBuilder("__process_result = __handler_instance.process(");
                final int count = Math.min(params.size(), rowArgs.size());
                for (int i = 0; i < count; i++) {
                    PythonInterpreter.bindArgument(params.get(i), TABLE_ARGUMENT + i, rowArgs.get(i));
                    call.append(i == 0 ? "" : ", ").append(TABLE_ARGUMENT).append(i);
                }
                // A generator is read through here, so what it raises while it yields is reported as the handler's.
                PythonFailureReport.run(call.append(')').append(READ_THROUGH.replace("@", "__process_result"))
                    .toString());
                writtenPerRow.add(rowsOf("__process_result", function, outputColumns, declared));
            }

            // Call end_partition() if it exists
            PythonInterpreter.eval("__has_end_partition = hasattr(__handler_instance, 'end_partition')");
            if (Boolean.TRUE.equals(PythonInterpreter.global("__has_end_partition"))) {
                PythonFailureReport.run("__end_result = __handler_instance.end_partition()"
                    + READ_THROUGH.replace("@", "__end_result"));
                atEnd.addAll(rowsOf("__end_result", function, outputColumns, declared));
            }

        } catch (final PythonReturnRefusal refused) {
            // What the handler wrote does not fit its column's type; the refusal is worded already.
            throw refused;
        } catch (final Exception e) {
            // Worded before the context is settled (see PythonInterpreter.settleAfter).
            final String reported = PythonFailureReport.wording(function.getName(), handlerClassName);
            final String refusal = reported != null ? reported
                : "Error executing Python table function " + function.getName() + ": " + e.getMessage();
            logger.error("Error executing Python table function {}: {}", function.getName(), e.getMessage(), e);
            if (!PythonInterpreter.settleAfter(e)) {
                throw new RuntimeException(refusal);
            }
            throw new RuntimeException(refusal, e);
        } finally {
            PythonInterpreter.leaveModule(frame);
        }

        return new TablePartitionRows(writtenPerRow, atEnd);
    }

    /**
     * The rows a handler call left in a global, on whichever backend runs, shaped by {@link #ROWS}. GraalPy's cells
     * are read as polyglot values, each padded or trimmed to the declared width and converted here; the CPython
     * worker converts each declared cell by {@link PythonReturn#CONVERT} on its side, built into its SQL value here.
     */
    private static List<Row> rowsOf(final String global, final Function function,
                                    final List<ResultSetColumn> columns, final boolean declared) {
        final List<Row> rows = new ArrayList<>();
        if (PythonInterpreter.holdsPolyglotValues()) {
            PythonInterpreter.bind("__fl_shape_module", PythonRuntime.module(SHAPE_MODULE, SHAPE));
            PythonInterpreter.eval("__fl_raw_rows = __fl_shape_module.raw(" + global + ")\n");
            final Value raw = PythonRuntime.global("__fl_raw_rows");
            for (long r = 0; raw != null && r < raw.getArraySize(); r++) {
                final Value cells = raw.getArrayElement(r);
                final List<Object> values = new ArrayList<>(columns.size());
                for (int i = 0; i < columns.size(); i++) {
                    values.add(i < cells.getArraySize()
                        ? cellValue(cells.getArrayElement(i), function, declared ? columns.get(i) : null) : null);
                }
                rows.add(new Row(values));
            }
            return rows;
        }
        final StringBuilder kinds = new StringBuilder("[");
        final StringBuilder scales = new StringBuilder("[");
        for (int i = 0; i < columns.size(); i++) {
            final String kind = declared ? PythonReturn.kindOf(columns.get(i).getDataType()) : null;
            kinds.append(i == 0 ? "" : ", ").append(kind == null ? "None" : "'" + kind + "'");
            scales.append(i == 0 ? "" : ", ").append(declared ? PythonReturn.scaleOf(columns.get(i).getDataType()) : 0);
        }
        PythonReturn.installConvert();
        PythonInterpreter.installModule(ROWS_MODULE, ROWS);
        PythonInterpreter.eval("import " + ROWS_MODULE + " as __fl_rows_module\n__fl_rows = __fl_rows_module.rows("
            + global + ", " + kinds.append(']') + ", " + scales.append(']') + ", " + columns.size() + ")\n");
        final Object shaped = PythonInterpreter.global("__fl_rows");
        if (!(shaped instanceof List)) {
            return rows;
        }
        for (final Object row : (List<?>) shaped) {
            final List<Object> values = new ArrayList<>(columns.size());
            int i = 0;
            for (final Object cell : (List<?>) row) {
                final List<?> triple = (List<?>) cell;
                final String outcome = String.valueOf(triple.get(0));
                if ("absent".equals(outcome)) {
                    values.add(null);
                } else {
                    final Object held = PythonReturn.fromPair(outcome, String.valueOf(triple.get(1)),
                        columns.get(i).getDataType(), function.getName(), function.getHandler());
                    values.add(held != PythonReturn.AS_IS ? held : triple.get(2));
                }
                i++;
            }
            rows.add(new Row(values));
        }
        return rows;
    }

    /**
     * One value a handler wrote, as its declared column holds it, or as the runtime holds it for no column. A
     * declared column holds its value as its type does, converted as Python converts and judged as the account judges
     * it (live-verified, see {@link PythonReturn}): a NUMBER(10,2) column holds {@code Decimal('1.255')} as 1.26 and a
     * VARCHAR one {@code str()} of what it is given.
     */
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
