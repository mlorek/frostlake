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

package dev.frostlake.rt.js;

import dev.frostlake.executor.SessionZone;
import dev.frostlake.executor.udf.ConstantTableArguments;
import dev.frostlake.executor.udf.HandlerStateFactory;
import dev.frostlake.executor.udf.StatementHandlerStates;
import dev.frostlake.executor.udf.TablePartitionRows;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import tools.jackson.databind.JsonNode;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 * Runs a table function written in JavaScript over one partition of its input (live-verified): the body's
 * object is made once, its {@code initialize} is called when it has one, {@code processRow} reads each row and
 * {@code finalize}, when the object has one, is called once the rows are through. The rows are the ones the
 * handler writes with {@code rowWriter.writeRow}. A body whose value has no {@code processRow} function is
 * refused when the function is called, {@code User Defined Table Function object is missing property
 * processRow}, as is a declared column of an exact NUMBER type.
 */
final class JavaScriptTableFunction {

    /** The account's lines that call a handler object's members. */
    private static final String INITIALIZE_CALL = "            udtfObject.initialize(argInfo, context);";
    private static final String PROCESS_ROW_CALL =
        "    var keepGoing = udtfObject.processRow(row, rowWriter, context);";
    private static final String FINALIZE_CALL = "        udtfObject.finalize(rowWriter, context);";

    /** What a table function's world is kept under among its function's states, before the session's zone. */
    private static final String TABLE_WORLD = "table-world:";

    private static final int BEGIN = 0;
    private static final int ROW = 1;
    private static final int END = 2;

    /** Static helpers only — never instantiated. */
    private JavaScriptTableFunction() {
    }

    /**
     * @param function the function
     * @param rows     the partition's rows, each the function's arguments
     * @return the rows the handler wrote for each input row and once the partition was through
     */
    static TablePartitionRows run(final Function function, final List<List<Object>> rows) {
        if (function.getUdfLanguage() != UdfLanguage.JAVASCRIPT) {
            throw new RuntimeException("Function is not a JavaScript function");
        }
        final List<Parameter> parameters = function.getParameters();
        final List<Parameter> columns = function.getReturnColumns();
        for (final Parameter column : columns) {
            final DataType type = column.getDataType();
            if (type instanceof NumericType && !NumericType.isApproximate(type)) {
                final NumericType number = (NumericType) type;
                throw new RuntimeException("Snowflake type FIXED[SB" + storageBytes(number.getPrecision()) + "]("
                    + number.getPrecision() + "," + number.getScale() + "){nullable} is not supported in function "
                    + function.getName());
            }
        }
        // A constant argument's value is every row's; the handler is told it as the account tells it.
        final List<Object> constants = new ArrayList<>();
        for (int i = 0; i < parameters.size(); i++) {
            constants.add(ConstantTableArguments.isConstant(i) && !rows.isEmpty() && i < rows.get(0).size()
                ? rows.get(0).get(i) : JavaScriptHandler.NOT_CONSTANT);
        }
        final String source = JavaScriptHandler.tableSource(parameters, columns, function.getBody(), constants);
        // One world for the statement's call: its handler object, made once, reads every partition in turn, so what
        // it keeps in itself carries from one partition to the next (live-verified). A call outside any statement
        // has a world of its own.
        final ZoneId zone = SessionZone.current();
        final AutoCloseable kept = StatementHandlerStates.state(function, TABLE_WORLD + zone.getId(),
            new HandlerStateFactory() {
                @Override
                public AutoCloseable create() {
                    return new JavaScriptWorld(zone);
                }
            });
        final JavaScriptWorld world = kept != null ? (JavaScriptWorld) kept : new JavaScriptWorld(zone);
        final ScriptEngine engine = world.engine();
        Object driver = null;
        try {
            driver = world.invoker(source);
            final Object begun = JavaScriptHandler.invokeTable(engine, driver, BEGIN, function.getName(), parameters,
                null);
            if (begun == null) {
                throw new RuntimeException("User Defined Table Function object is missing property processRow");
            }
            refuseMember(begun, function.getName());
            final List<List<Row>> written = new ArrayList<>();
            for (final List<Object> arguments : rows) {
                final Object read = JavaScriptHandler.invokeTable(engine, driver, ROW, function.getName(), parameters,
                    arguments);
                refuseMember(read, function.getName());
                written.add(rowsOf(read, columns));
            }
            final Object ended = JavaScriptHandler.invokeTable(engine, driver, END, function.getName(), parameters,
                null);
            refuseMember(ended, function.getName());
            final List<Row> atEnd = rowsOf(ended, columns);
            return new TablePartitionRows(written, atEnd);
        } catch (final ScriptException e) {
            // A body that does not parse is refused as the account refuses it, before any of it runs.
            final String unparsed = driver == null
                ? JavaScriptSyntaxWording.of(engine, function.getName(), parameters, function.getBody(), true) : null;
            if (unparsed != null) {
                throw new RuntimeException(unparsed, e);
            }
            throw new RuntimeException(JavaScriptErrorText.of(e, function.getName(), "(" + function.getBody() + ")",
                JavaScriptHandler.BODY_FIRST_LINE, source, GraalJsEngine.hostOf(engine),
                "Error executing JavaScript function " + function.getName() + ": " + e.getMessage(), true), e);
        } catch (final NoSuchMethodException e) {
            throw new RuntimeException("Error executing JavaScript function " + function.getName() + ": "
                + e.getMessage(), e);
        } finally {
            if (kept == null) {
                world.close();
            }
        }
    }

    /**
     * The account's refusal of a handler object that is no object, or of a member it calls that is no function,
     * when the driver reports one (see {@link JavaScriptHandler#tableSource}): the latter quotes the account's own
     * line calling the member, at the member's name (live-verified).
     */
    private static void refuseMember(final Object answered, final String name) {
        if (!(answered instanceof String) || !((String) answered).startsWith(JavaScriptHandler.NO_MEMBER)) {
            return;
        }
        final String member = ((String) answered).substring(JavaScriptHandler.NO_MEMBER.length());
        final String calling = "initialize".equals(member) ? INITIALIZE_CALL
            : "finalize".equals(member) ? FINALIZE_CALL : "processRow".equals(member) ? PROCESS_ROW_CALL : null;
        if (calling == null) {
            throw new RuntimeException("User Defined Table Function not an object");
        }
        throw new RuntimeException("JavaScript execution error: Uncaught TypeError: udtfObject." + member
            + " is not a function in " + name + " at '" + calling + "' position " + calling.indexOf(member));
    }

    /** The rows a driver operation answered: each cell's tagged text as its declared column's value. */
    private static List<Row> rowsOf(final Object answered, final List<Parameter> columns) {
        final List<Row> rows = new ArrayList<>();
        if (answered == null) {
            return rows;
        }
        final JsonNode written = ArrayFunctionHelper.MAPPER.readTree(String.valueOf(answered));
        for (final JsonNode row : written) {
            final List<Object> values = new ArrayList<>(columns.size());
            for (int i = 0; i < columns.size(); i++) {
                values.add(JavaScriptReturn.toSql(untagged(row.get(i).stringValue()), columns.get(i).getDataType(),
                    false));
            }
            rows.add(new Row(values));
        }
        return rows;
    }

    /** A cell's value from its tagged text. */
    private static Object untagged(final String cell) {
        final char tag = cell.charAt(0);
        if (tag == 'u') {
            return null;
        }
        if (tag == 'b') {
            return Boolean.valueOf(cell.charAt(1) == '1');
        }
        if (tag == 'n') {
            return Double.valueOf(cell.substring(1));
        }
        return cell.substring(1);
    }

    /** The bytes an exact number of a declared precision is stored in, as a type's spelling names them. */
    private static int storageBytes(final int precision) {
        if (precision <= 2) {
            return 1;
        }
        if (precision <= 4) {
            return 2;
        }
        if (precision <= 9) {
            return 4;
        }
        return precision <= 18 ? 8 : 16;
    }
}
