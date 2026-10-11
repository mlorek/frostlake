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

import dev.frostlake.executor.udf.HandlerJson;
import dev.frostlake.executor.udf.JavaScriptIdentifiers;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.TypeCategory;
import dev.frostlake.values.BinaryValue;

import java.time.temporal.TemporalAccessor;
import java.util.List;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 * A JavaScript handler as the account runs one: a function under the routine's own name, taking the routine's
 * arguments as its parameters. Inside the body the arguments are parameters and nothing else — {@code arguments}
 * holds them, {@code typeof globalThis.X} is undefined — the handler's name is the handler itself, {@code this}
 * is the global object, or undefined under {@code 'use strict'}, and while it runs the handler is a global of
 * that name (live-verified).
 *
 * <p>The script evaluates to an invoker, which the executors call with the argument values. The invoker turns a
 * semi-structured argument's JSON text into a native value — see {@link JavaScriptArgumentJson} for what the
 * text marks — wraps a non-array given for an ARRAY parameter in one, binds the global name for the length of
 * the call, and hands the handler's value back as the declared RETURNS type takes it.
 */
final class JavaScriptHandler {

    /** The line of the evaluated source that holds the body's first line. */
    static final int BODY_FIRST_LINE = 3;

    /**
     * What the invoker reads a semi-structured argument's text with, in a scope the handler's body cannot see.
     * Every such text is led (see {@link JavaScriptArgumentJson}): {@code arrive} parses the engine's own JSON
     * strictly — with {@code revive} where the text holds marks — and reads a text of another origin as JSON
     * where it is JSON and as itself where it is not. {@code revive} makes a mark the value it stands
     * for: a hole (the property is deleted, which leaves the array its length), a {@code Date}, a number JSON
     * cannot spell, a {@code Uint8Array}, or the text a mark-like text was led with.
     */
    private static final String ARRIVE = """
        var newDate = Date, newBytes = Uint8Array, toNumber = Number, toByte = parseInt;
        var revive = function (key, value) {
        if (typeof value !== 'string' || value.length < 3) { return value; }
        var lead = value.charCodeAt(0);
        if (lead === 0) { return value === '\\u0000undefined\\u0000' ? undefined : value; }
        if (lead !== 2 || value.charCodeAt(2) !== 58) { return value; }
        var rest = value.substring(3);
        switch (value.charCodeAt(1)) {
        case 68: return new newDate(toNumber(rest));
        case 70: return toNumber(rest);
        case 83: return rest;
        case 66: var bytes = new newBytes(rest.length / 2);
        for (var i = 0; i < bytes.length; i++) { bytes[i] = toByte(rest.substr(2 * i, 2), 16); }
        return bytes;
        }
        return value; };
        var arrive = function (text) {
        var kind = text.charCodeAt(1), rest = text.substring(3);
        if (kind === 74) { return parse(rest); }
        if (kind === 77) { return parse(rest, revive); }
        try { return parse(rest); } catch (notJson) { return rest; } };
        """;

    /** A {@code Uint8Array} as upper-case hex. */
    private static final String HEX = """
        var hex = function (bytes) { var out = '';
        for (var i = 0; i < bytes.length; i++) { out += (bytes[i] < 16 ? '0' : '') + bytes[i].toString(16); }
        return out.toUpperCase(); };
        """;

    /**
     * The copy a semi-structured answer is written from, member by member (live-verified). A number with a
     * fraction, one from 1e21 on, a NaN and an infinity are marked as DOUBLEs and a {@code Date} as an instant,
     * an invalid one as the epoch — see {@link HandlerJson}. A {@code Uint8Array} is its hex text in a
     * procedure's answer and marked as bytes in a function's. A function is an empty object, and any other
     * object its own enumerable members: {@code toJSON} is not called, so an object that has one keeps it as a
     * member. A {@code Number}, {@code String} or {@code Boolean} object is the value it wraps. An array's
     * trailing {@code undefined} elements are dropped and any other stays one of its SQL NULLs; an object's
     * {@code undefined} member is left out. A symbol is a JSON null in an array and left out of an object. A
     * text that would read as a mark is led so that it stays a text. A BigInt, a member whose read throws and
     * a value that holds itself have no copy, which the invoker answers as a late exception.
     */
    private static final String COPY = """
        var keysOf = Object.keys, define = Object.defineProperty, floor = Math.floor, abs = Math.abs;
        var copy = function (value, inArray, path) {
        if (value === null) { return null; }
        var kind = typeof value;
        if (kind === 'undefined') { return inArray ? '@HOLE@' : undefined; }
        if (kind === 'symbol') { return inArray ? null : undefined; }
        if (kind === 'bigint') { throw new TypeError('BigInt'); }
        if (kind === 'boolean') { return value; }
        if (kind === 'function') { return {}; }
        if (kind === 'object') {
        if (value instanceof Number) { value = +value; kind = 'number'; }
        else if (value instanceof String) { value = String(value); kind = 'string'; }
        else if (value instanceof Boolean) { return value.valueOf(); }
        }
        if (kind === 'string') { var lead = value.charCodeAt(0);
        return lead === 2 || lead === 0 ? '@TEXT@' + value : value; }
        if (kind === 'number') { return value === floor(value) && abs(value) < 1e21 ? value
        : '@DOUBLE@' + String(value); }
        if (value instanceof Date) { var at = value.getTime(); return '@INSTANT@' + (at === at ? at : 0); }
        if (value instanceof Uint8Array) { return @BYTES@hex(value); }
        if (path.indexOf(value) >= 0) { throw new TypeError('cyclic'); }
        path.push(value);
        var out;
        if (isArray(value)) {
        var kept = value.length;
        while (kept > 0 && value[kept - 1] === undefined) { kept--; }
        out = new Array(kept);
        for (var i = 0; i < kept; i++) { out[i] = copy(value[i], true, path); }
        } else {
        out = {};
        var keys = keysOf(value);
        for (var k = 0; k < keys.length; k++) { var member = copy(value[keys[k]], false, path);
        if (member !== undefined) { define(out, keys[k], {value: member, enumerable: true, writable: true,
        configurable: true}); } }
        }
        path.pop();
        return out; };
        """;

    /** Makes the {@code Uint8Array} of a BINARY argument's hex text. */
    private static final String BYTES = """
        var bytesOf = function (hex) { var bytes = new Uint8Array(hex.length / 2);
        for (var i = 0; i < bytes.length; i++) { bytes[i] = parseInt(hex.substr(2 * i, 2), 16); }
        return bytes; };
        """;

    /**
     * The API objects a procedure's handler holds, made in the script's own world over the host's (live-verified).
     * A {@code ResultSet} and a {@code Statement} are objects of constructors so named whose methods are their own
     * members; a result set's columns are members too once a row is read, named as the result names them. A
     * column's value arrives as a semi-structured argument's does (see {@code ARRIVE}). A statement that fails is
     * thrown as an object that is no {@code Error}, holding {@code code}, {@code state}, {@code message},
     * {@code stackTraceTxt} and {@code stack} — the last two the place of the call, {@code Snowflake.execute, line
     * 1 position 11} — and a misuse of the API as one holding all but {@code stackTraceTxt}. The handler reaches
     * no host object through any of them.
     *
     * <p>The place is read from the one frame that called the API: the stack is not walked past it, since below
     * the handler it may hold another language's frames, which the script engine cannot name.
     */
    private static final String API = """
        var defineOwn = Object.defineProperty, captureStack = Error.captureStackTrace;
        var hasOwn = Object.prototype.hasOwnProperty, text = String;
        var placed = function (api, method, below) {
        var saved = Error.prepareStackTrace, limit = Error.stackTraceLimit, holder = {}, frame = null;
        Error.stackTraceLimit = 1;
        Error.prepareStackTrace = function (error, frames) { return frames; };
        try { captureStack(holder, below); frame = holder.stack && holder.stack[0]; } catch (unplaced) { frame = null; }
        finally { Error.prepareStackTrace = saved; Error.stackTraceLimit = limit; }
        return frame ? api.place(method, frame.getLineNumber(), frame.getColumnNumber()) : method; };
        var createExecQueryError = function createExecQueryError(told, at) { this.code = told.code;
        this.state = told.state; this.message = told.message; this.stack = at; this.stackTraceTxt = at;
        this.toString = function () { return this.message; }; };
        var createExecError = function createExecError(told, at) { this.code = told.code;
        this.state = told.state; this.message = told.message; this.stack = at;
        this.toString = function () { return this.message; }; };
        var raise = function (api, told, method, below) { var at = placed(api, method, below);
        throw told.statement ? new createExecQueryError(told, at) : new createExecError(told, at); };
        var failed = function (out) {
        return typeof out === 'string' && out.charCodeAt(0) === 1 && out.charCodeAt(1) === 69; };
        var answered = function (api, out, method, below) {
        if (failed(out)) { raise(api, parse(out.substring(3)), method, below); }
        return out; };
        var unread = function (api, options, method, below) {
        if (options === undefined || options === null) { raise(api, {statement: false, code: 100183, state: 'P0000',
        message: "TypeError: Cannot read properties of " + options + " (reading 'sqlText')"}, method, below); } };
        var column = function (self, host, index) { var name = host.columnName(index);
        if (name === null || hasOwn.call(self, name)) { return; }
        defineOwn(self, name, {enumerable: true, configurable: true, get: function () {
        var led = host.columnValue(index, text(index)); return failed(led) ? undefined : arrive(led); }}); };
        var ResultSet = function ResultSet(api, host) { var self = this, named = false;
        this.next = function () { var more = host.next();
        if (more && !named) { named = true; var count = host.getColumnCount();
        for (var i = 1; i <= count; i++) { column(self, host, i); } }
        return more; };
        this.getColumnValue = function (which) {
        var asked = typeof which === 'number' || typeof which === 'string' ? which : null;
        return arrive(answered(api, host.columnValue(asked, text(which)), 'ResultSet.getColumnValue',
        self.getColumnValue)); };
        this.getRowCount = function () { return host.getRowCount(); };
        this.getColumnCount = function () { return host.getColumnCount(); }; };
        var Statement = function Statement(api, host) { var self = this;
        this.execute = function () {
        return new ResultSet(api, answered(api, host.execute(), 'Statement.execute', self.execute)); };
        this.getColumnCount = function () {
        return answered(api, host.getColumnCount(), 'Statement.getColumnCount', self.getColumnCount); };
        this.getColumnName = function (index) {
        return answered(api, host.getColumnName(index), 'Statement.getColumnName', self.getColumnName); };
        this.getRowCount = function () {
        return answered(api, host.getRowCount(), 'Statement.getRowCount', self.getRowCount); };
        this.getSqlText = function () { return host.getSqlText(); }; };
        var ran = function (api, options, method, below) { unread(api, options, method, below);
        return new ResultSet(api, answered(api, api.execute(options), method, below)); };
        var made = function (api, options, below) { unread(api, options, 'Snowflake.createStatement', below);
        return new Statement(api, answered(api, api.createStatement(options), 'Snowflake.createStatement', below)); };
        """;

    private JavaScriptHandler() {
    }

    /**
     * The script whose value is the handler's invoker. A procedure's invoker takes the procedure API object
     * first, and gives the {@code snowflake} object its statement methods from it.
     *
     * @param name       the routine's name as the catalog holds it
     * @param parameters the routine's parameters
     * @param body       the handler body as written
     * @param procedure  whether the handler is a procedure's
     * @return the script
     */
    static String source(final String name, final List<Parameter> parameters, final String body,
                         final boolean procedure) {
        return source(name, parameters, body, procedure, null);
    }

    /**
     * {@link #source(String, List, String, boolean)} for a routine whose answer is typed: the invoker hands the
     * handler's value back as the declared RETURNS type takes it (see {@link #typedReturn}).
     *
     * @param returns the routine's declared RETURNS type, or null for a table
     */
    static String source(final String name, final List<Parameter> parameters, final String body,
                         final boolean procedure, final DataType returns) {
        final boolean named = JavaScriptIdentifiers.isDeclarable(name);
        final StringBuilder declared = new StringBuilder();
        final StringBuilder passed = new StringBuilder();
        final StringBuilder converted = new StringBuilder();
        boolean semiStructured = false;
        boolean binary = false;
        for (int i = 0; i < parameters.size(); i++) {
            final String parameterName = parameters.get(i).getName();
            declared.append(i > 0 ? ", " : "")
                .append(JavaScriptIdentifiers.isDeclarable(parameterName) ? parameterName : "__fl_p" + i);
            passed.append(", a").append(i);
            final DataType type = parameters.get(i).getDataType();
            handlerArgument(converted, i, type);
            binary |= type instanceof BinaryType;
            if (type != null && type.getCategory() == TypeCategory.SEMI_STRUCTURED) {
                semiStructured = true;
                converted.append("if (typeof a").append(i).append(" === 'string') { a").append(i)
                    .append(" = arrive(a").append(i).append("); }\n");
                if (type instanceof ArrayType) {
                    // An ARRAY parameter converts as TO_ARRAY does: a non-array arrives as a one-element array.
                    converted.append("if (a").append(i).append(" !== null && a").append(i)
                        .append(" !== undefined && !isArray(a").append(i).append(")) { a").append(i)
                        .append(" = [a").append(i).append("]; }\n");
                }
            }
        }
        final String arguments = passed.length() > 0 ? passed.substring(2) : "";
        final String global = "'" + name.replace("\\", "\\\\").replace("'", "\\'") + "'";
        final StringBuilder source = new StringBuilder()
            .append("(function () { var parse = JSON.parse, toJson = JSON.stringify, isArray = Array.isArray;\n")
            .append("var handler = function ").append(named ? name : "").append('(').append(declared).append(") {\n")
            .append(body)
            .append("\n};\n")
            .append("return (function () {\n")
            .append(semiStructured || procedure ? ARRIVE : "")
            .append(binary ? BYTES : "")
            .append(procedure ? API : "")
            .append(returns == null ? "" : returnHelpers(returns, procedure))
            .append("return function (").append(procedure ? "api" : "").append(procedure && arguments.length() > 0 ? ", " : "")
            .append(arguments).append(") {\n")
            .append(converted);
        if (procedure) {
            // executeAsync answers the result set too: the engine runs the statement before it returns.
            source.append("Snowflake.prototype.createStatement = function createStatement(options) {")
                .append(" return made(api, options, Snowflake.prototype.createStatement); };\n")
                .append("Snowflake.prototype.execute = function execute(options, context) {")
                .append(" return ran(api, options, 'Snowflake.execute', Snowflake.prototype.execute); };\n")
                .append("Snowflake.prototype.executeAsync = function executeAsync(options, context) {")
                .append(" return ran(api, options, 'Snowflake.executeAsync', Snowflake.prototype.executeAsync); };\n");
        }
        if (named) {
            source.append("var had = Object.prototype.hasOwnProperty.call(globalThis, ").append(global)
                .append("), saved = globalThis[").append(global).append("];\n")
                .append("globalThis[").append(global).append("] = handler;\n")
                .append("try {\n");
        }
        source.append("var result = handler.call(undefined").append(arguments.length() > 0 ? ", " : "")
            .append(arguments).append(");\n")
            .append(returns == null
                ? "return result !== null && typeof result === 'object' ? toJson(result) : result;\n"
                : typedReturn(returns));
        if (named) {
            source.append("} finally { if (had) { globalThis[").append(global).append("] = saved; } else { delete globalThis[")
                .append(global).append("]; } }\n");
        }
        return source.append("};\n})();\n})()").toString();
    }

    /**
     * The script whose value drives a table function's handler (live-verified). The body is an expression - it
     * is read between parentheses, so a body that is none is a syntax error where it stands - whose value is
     * an object with a {@code processRow(row, rowWriter, context)} function and, when it wants them,
     * {@code initialize(argumentInfo, context)} and {@code finalize(rowWriter, context)}. A row arrives as an
     * object of the parameters by their names; {@code rowWriter.writeRow(object)} writes a row whose columns
     * are the object's members of the declared columns' names, each converted as a function's answer of that
     * type is, a member the object lacks a NULL.
     *
     * <p>The driver takes an operation first - 0 to begin a partition, 1 to read a row, 2 to end the
     * partition - and answers the rows written during it as JSON, each cell a tagged text: {@code u} for a
     * NULL, {@code b1} / {@code b0}, {@code n} and a number, {@code s} and a text.
     *
     * @param parameters the function's parameters
     * @param columns    the function's declared columns
     * @param body       the handler body as written
     * @return the script
     */
    static String tableSource(final List<Parameter> parameters, final List<Parameter> columns, final String body) {
        final StringBuilder passed = new StringBuilder();
        final StringBuilder converted = new StringBuilder();
        final StringBuilder row = new StringBuilder();
        final StringBuilder info = new StringBuilder();
        boolean semiStructured = false;
        boolean binary = false;
        for (int i = 0; i < parameters.size(); i++) {
            final DataType type = parameters.get(i).getDataType();
            final String key = quoted(parameters.get(i).getName());
            passed.append(", a").append(i);
            handlerArgument(converted, i, type);
            binary |= type instanceof BinaryType;
            if (type != null && type.getCategory() == TypeCategory.SEMI_STRUCTURED) {
                semiStructured = true;
                converted.append("if (typeof a").append(i).append(" === 'string') { a").append(i)
                    .append(" = arrive(a").append(i).append("); }\n");
            }
            row.append(i > 0 ? ", " : "").append(key).append(": a").append(i);
            info.append(i > 0 ? ", " : "").append(key).append(": {isNullable: true, isConst: false")
                .append(type instanceof NumericType ? ", type: 'real'" : "").append('}');
        }
        final StringBuilder cells = new StringBuilder();
        final StringBuilder names = new StringBuilder();
        boolean copies = false;
        boolean hexes = false;
        for (int i = 0; i < columns.size(); i++) {
            final DataType type = columns.get(i).getDataType();
            copies |= type != null && type.getCategory() == TypeCategory.SEMI_STRUCTURED;
            hexes |= type instanceof BinaryType;
            cells.append(i > 0 ? ",\n" : "").append("function (result) {\n")
                .append(type == null ? "return result;\n" : typedReturn(type)).append('}');
            names.append(i > 0 ? ", " : "").append(quoted(columns.get(i).getName()));
        }
        final String refuse = "var refuse = function (sentence) { return '" + JavaScriptReturn.REFUSAL_IN_SOURCE
            + "' + sentence; };\n";
        final String helpers = refuse + (copies || hexes ? HEX : "")
            + (copies ? COPY.replace("@HOLE@", JavaScriptReturn.HOLE_IN_SOURCE)
                .replace("@TEXT@", HandlerJson.TEXT_IN_SOURCE)
                .replace("@DOUBLE@", HandlerJson.DOUBLE_IN_SOURCE)
                .replace("@INSTANT@", HandlerJson.INSTANT_IN_SOURCE)
                .replace("@BYTES@", "'" + HandlerJson.BINARY_IN_SOURCE + "' + ") : "");
        return new StringBuilder()
            .append("(function () { var parse = JSON.parse, toJson = JSON.stringify, isArray = Array.isArray;\n")
            .append("var udtfObject =\n")
            .append('(').append(body).append(")\n")
            .append(";\nreturn (function () {\n")
            .append(semiStructured ? ARRIVE : "")
            .append(binary ? BYTES : "")
            .append(helpers)
            .append("var cells = [\n").append(cells).append("];\n")
            .append("var names = [").append(names).append("];\n")
            .append("var written = [];\n")
            .append("var tag = function (v) { if (v === null || v === undefined) { return 'u'; } ")
            .append("if (typeof v === 'boolean') { return v ? 'b1' : 'b0'; } ")
            .append("if (typeof v === 'number') { return 'n' + String(v); } return 's' + String(v); };\n")
            .append("var rowWriter = { writeRow: function (row) { var out = [], i, given = row !== null && ")
            .append("(typeof row === 'object' || typeof row === 'function'); ")
            .append("for (i = 0; i < names.length; i++) { out.push(tag(cells[i](given ? row[names[i]] : undefined))); } ")
            .append("written.push(out); } };\n")
            .append("var context = {};\n")
            .append("var argumentInfo = {").append(info).append("};\n")
            .append("var took = function () { var out = toJson(written); written = []; return out; };\n")
            .append("return function (operation").append(passed).append(") {\n")
            .append("if (operation === 0) {\n")
            .append("if (udtfObject === null || udtfObject === undefined || typeof udtfObject.processRow !== 'function') ")
            .append("{ return null; }\n")
            .append("if (udtfObject.initialize !== undefined) { udtfObject.initialize(argumentInfo, context); }\n")
            .append("return took();\n}\n")
            .append("if (operation === 2) {\n")
            .append("if (udtfObject.finalize !== undefined) { udtfObject.finalize(rowWriter, context); }\n")
            .append("return took();\n}\n")
            .append(converted)
            .append("udtfObject.processRow({").append(row).append("}, rowWriter, context);\n")
            .append("return took();\n")
            .append("};\n})();\n})()").toString();
    }

    /** A name as a JavaScript string literal. */
    private static String quoted(final String name) {
        return "'" + name.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    /**
     * Calls a table function's driver for one operation (see {@link #tableSource}).
     *
     * @param engine     the engine that evaluated the driver
     * @param driver     the driver
     * @param operation  0 to begin a partition, 1 to read a row, 2 to end the partition
     * @param name       the function's name, for an argument's refusal
     * @param parameters the function's parameters
     * @param arguments  the row's argument values; none for an operation that reads no row
     * @return the rows written during the operation, as JSON, or null for a body with no processRow
     * @throws ScriptException when the handler fails
     * @throws NoSuchMethodException when the driver is not callable
     */
    static Object invokeTable(final ScriptEngine engine, final Object driver, final int operation, final String name,
                              final List<Parameter> parameters, final List<Object> arguments)
            throws ScriptException, NoSuchMethodException {
        final Object[] values = new Object[2 + parameters.size()];
        values[0] = null;
        values[1] = Integer.valueOf(operation);
        for (int i = 0; i < parameters.size(); i++) {
            values[2 + i] = arguments == null || i >= arguments.size() ? null
                : argumentValue(name, parameters.get(i), arguments.get(i));
        }
        return ((Invocable) engine).invokeMethod(driver, "call", values);
    }

    /**
     * How an invoker hands the handler's value back, by the declared RETURNS type, a function's and a
     * procedure's alike (live-verified). A {@code null} and an {@code undefined} are a SQL NULL for a scalar
     * type. A text type takes the value's string, a FLOAT its number and a BOOLEAN its truthiness, each as the
     * language converts implicitly, so a symbol is no text and a BigInt no number: {@code Late exception in
     * return value conversion}, which is also what a conversion that throws answers. A DATE and a TIMESTAMP take
     * a {@code Date} or the number of milliseconds the value converts to, handed back as that number, and
     * refuse what names no instant: {@code Incorrect Timestamp returned}. A BINARY takes a {@code Uint8Array},
     * handed back as hex. A VARIANT takes a copy of the value (see {@link #COPY}), an ARRAY only an array and an
     * OBJECT any object that is no array; an {@code undefined} is a SQL NULL for all three, while a
     * {@code null} is a JSON null for a VARIANT, as a symbol is, and no array and no object. A value the type
     * does not take comes back as a refusal for {@link JavaScriptReturn} to raise.
     */
    private static String typedReturn(final DataType returns) {
        final String none = "if (result === null || result === undefined) { return null; }\n";
        final String absent = "if (result === undefined) { return null; }\n";
        final String late = "refuse('Late exception in return value conversion')";
        final String copied = "try { return toJson(copy(result, false, [])); } catch (thrown) { return " + late
            + "; }\n";
        if (returns instanceof StringType) {
            return none + "if (typeof result === 'symbol') { return " + late + "; }\n"
                + "try { var text = String(result); return text.charCodeAt(0) === 1 ? '"
                + JavaScriptReturn.TEXT_IN_SOURCE + "' + text : text; } catch (thrown) { return " + late + "; }\n";
        }
        if (returns instanceof BooleanType) {
            return none + "return !!result;\n";
        }
        if (returns instanceof NumericType) {
            return none + "try { return +result; } catch (thrown) { return " + late + "; }\n";
        }
        if (returns instanceof DateTimeType) {
            return none + "var ms; try { ms = result instanceof Date ? result.getTime() : +result; } "
                + "catch (thrown) { ms = NaN; }\n"
                + "return ms === ms && ms !== Infinity && ms !== -Infinity ? ms "
                + ": refuse('Incorrect Timestamp returned');\n";
        }
        if (returns instanceof BinaryType) {
            return none + "return result instanceof Uint8Array ? hex(result) "
                + ": refuse('Return value is not a BINARY');\n";
        }
        if (returns instanceof ArrayType) {
            return absent + "if (!isArray(result)) { return refuse('Return value is not an ARRAY'); }\n" + copied;
        }
        if (returns instanceof ObjectType) {
            return absent + "if (result === null || isArray(result) || typeof result !== 'object' "
                + "&& typeof result !== 'function') { return refuse('Return value is not an OBJECT'); }\n" + copied;
        }
        if (returns.getCategory() == TypeCategory.SEMI_STRUCTURED) {
            return absent + "if (result === null || typeof result === 'symbol') { return 'null'; }\n" + copied;
        }
        return "return result !== null && typeof result === 'object' ? toJson(result) : result;\n";
    }

    /** What {@link #typedReturn} calls for the declared type, made once in the scope the invoker closes over. */
    private static String returnHelpers(final DataType returns, final boolean procedure) {
        final String refuse = "var refuse = function (sentence) { return '" + JavaScriptReturn.REFUSAL_IN_SOURCE
            + "' + sentence; };\n";
        if (returns instanceof BinaryType) {
            return refuse + HEX;
        }
        if (returns.getCategory() == TypeCategory.SEMI_STRUCTURED) {
            return refuse + HEX + COPY.replace("@HOLE@", JavaScriptReturn.HOLE_IN_SOURCE)
                .replace("@TEXT@", HandlerJson.TEXT_IN_SOURCE)
                .replace("@DOUBLE@", HandlerJson.DOUBLE_IN_SOURCE)
                .replace("@INSTANT@", HandlerJson.INSTANT_IN_SOURCE)
                .replace("@BYTES@", procedure ? "" : "'" + HandlerJson.BINARY_IN_SOURCE + "' + ");
        }
        return refuse;
    }

    /**
     * What an invoker does to one argument before the handler sees it, a function's and a procedure's alike
     * (live-verified): a SQL NULL is {@code undefined}, a BOOLEAN the number 1 or 0, a DATE or a TIMESTAMP a
     * {@code Date} and a BINARY a {@code Uint8Array}. A semi-structured argument's JSON null arrives as its text
     * and is parsed after this, so it stays {@code null}.
     */
    private static void handlerArgument(final StringBuilder converted, final int i, final DataType type) {
        final String a = "a" + i;
        converted.append("if (").append(a).append(" === null) { ").append(a).append(" = undefined; }\n");
        if (type instanceof BooleanType) {
            converted.append("if (typeof ").append(a).append(" === 'boolean') { ").append(a).append(" = ").append(a)
                .append(" ? 1 : 0; }\n");
        } else if (type instanceof DateTimeType) {
            converted.append("if (typeof ").append(a).append(" === 'number') { ").append(a).append(" = new Date(")
                .append(a).append("); }\n");
        } else if (type instanceof BinaryType) {
            converted.append("if (").append(a).append(" !== undefined) { ").append(a).append(" = bytesOf(")
                .append(a).append("); }\n");
        }
    }

    /**
     * Calls a handler's invoker with the argument values: a BINARY as its hex text, a DATE or a TIMESTAMP as the
     * milliseconds of its instant, and a semi-structured value as the JSON text the invoker parses. No host
     * object is among them.
     *
     * @param engine     the engine that evaluated the invoker
     * @param invoker    the invoker
     * @param api        the procedure API object, or null for a function
     * @param name       the routine's name, for an argument's refusal
     * @param parameters the routine's parameters
     * @param arguments  the argument values
     * @return the handler's result
     * @throws ScriptException when the handler fails
     * @throws NoSuchMethodException when the invoker is not callable
     */
    static Object invoke(final ScriptEngine engine, final Object invoker, final Object api, final String name,
                         final List<Parameter> parameters, final List<Object> arguments)
            throws ScriptException, NoSuchMethodException {
        final Object[] values = new Object[(api != null ? 2 : 1) + arguments.size()];
        int at = 0;
        values[at++] = null;
        if (api != null) {
            values[at++] = api;
        }
        for (int i = 0; i < arguments.size(); i++) {
            values[at++] = argumentValue(name, parameters.get(i), arguments.get(i));
        }
        return ((Invocable) engine).invokeMethod(invoker, "call", values);
    }

    /** One argument as the invoker takes it. */
    private static Object argumentValue(final String name, final Parameter parameter, final Object value) {
        final DataType type = parameter.getDataType();
        if (value != null && type != null && type.getCategory() == TypeCategory.SEMI_STRUCTURED) {
            return semiStructured(name, value);
        }
        if (value instanceof BinaryValue) {
            return ((BinaryValue) value).toHex();
        }
        if (value instanceof TemporalAccessor && type instanceof DateTimeType) {
            final Long instant = JavaScriptArgumentJson.epochMillis((TemporalAccessor) value);
            if (instant != null) {
                return Double.valueOf(instant.doubleValue());
            }
        }
        return value;
    }

    /**
     * A semi-structured parameter's argument, as led text: the engine's own value as the JSON the invoker
     * parses strictly, a text of another origin for the invoker to read as JSON where it is JSON, and a host
     * container, which has no JavaScript Array or Object protocol, as its JSON.
     */
    private static Object semiStructured(final String name, final Object value) {
        if (value instanceof String) {
            return JavaScriptArgumentJson.LENIENT + value;
        }
        final String led = JavaScriptArgumentJson.of(value, name);
        if (led != null) {
            return led;
        }
        try {
            return JavaScriptArgumentJson.PLAIN + ArrayFunctionHelper.MAPPER.writeValueAsString(value);
        } catch (final Exception notJson) {
            return value;
        }
    }
}
