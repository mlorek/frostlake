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

package dev.frostlake.executor.expressions;

import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlIdentifierSubstitution;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.executor.udf.JarHandlerLoader;
import dev.frostlake.executor.udf.JavaFunctionCompiler;
import dev.frostlake.executor.udf.UdfRuntimes;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.NullHandling;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.TypeCategory;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorType;
import dev.frostlake.values.VariantValue;

import tools.jackson.databind.node.StringNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Function / user-defined-function dispatch extracted from {@link ExpressionEvaluatorVisitor}: overload
 * resolution, named-argument reordering, and evaluation of SQL / Java / JavaScript / Python / Scala
 * UDFs. Holds the immutable {@code catalog} and {@code functionRegistry}; the mutable query executor is
 * read live from the owning visitor via {@link ExpressionEvaluatorVisitor#getQueryExecutor()} so a later
 * {@code setQueryExecutor} is observed.
 */
final class UdfInvoker {

    private static final JavaFunctionCompiler javaCompiler = new JavaFunctionCompiler();

    private final Catalog catalog;
    private final FunctionRegistry functionRegistry;
    private final ExpressionEvaluatorVisitor visitor;

    UdfInvoker(final Catalog catalog, final FunctionRegistry functionRegistry,
               final ExpressionEvaluatorVisitor visitor) {
        this.catalog = catalog;
        this.functionRegistry = functionRegistry;
        this.visitor = visitor;
    }

    /**
     * Whether {@code func} can be called with {@code argCount} positional arguments. A parameter declared with a
     * DEFAULT may be omitted (Snowflake requires the defaulted ones to be trailing), so the acceptable count is
     * a RANGE, not one number: everything from the last non-defaulted parameter up to the full list. Requiring an
     * exact match made a call that relied on defaults resolve to no function at all, and because the caller
     * swallows the resulting exception it surfaced as the misleading "Unknown function".
     */
    private static boolean acceptsArgumentCount(final Function func, final int argCount) {
        final List<Parameter> params = func.getParameters();
        return argCount >= requiredParameterCount(params) && argCount <= params.size();
    }

    /** The number of leading parameters that must be supplied — the index after the last one with no DEFAULT. */
    private static int requiredParameterCount(final List<Parameter> params) {
        int required = 0;
        for (int i = 0; i < params.size(); i++) {
            if (!params.get(i).hasDefault()) {
                required = i + 1;
            }
        }
        return required;
    }

    /**
     * Coerce argument values to the function's DECLARED parameter types, as Snowflake does when binding a
     * call: a date-flavored value passed to a TIMESTAMP_NTZ parameter becomes a timestamp BEFORE the body
     * runs, so date arithmetic inside the body stays in the timestamp domain and OBJECT/VARIANT output
     * renders it in full timestamp form rather than date-only.
     */
    private List<Object> coerceArgsToParameterTypes(final Function function, final List<Object> args) {
        final List<Parameter> params = function.getParameters();
        List<Object> coerced = null;
        for (int i = 0; i < args.size() && i < params.size(); i++) {
            final Object value = args.get(i);
            if (value == null) {
                continue;
            }
            final DataType paramType = params.get(i).getDataType();
            // Snowflake: casting a variant JSON null to OBJECT or ARRAY yields SQL NULL (only a VARIANT
            // target keeps the JSON null). The engine carries JSON null as the text "null", which passed
            // the RETURNS NULL ON NULL INPUT check as a non-null argument — so a strict handler was
            // invoked with Python None (path leaf like source_type.CloudGroup = null) and crashed.
            if (paramType instanceof ObjectType || paramType instanceof ArrayType) {
                final boolean jsonNull = value instanceof VariantValue
                    ? ((VariantValue) value).isJsonNull()
                    : "null".equals(value instanceof String ? ((String) value).trim() : null);
                if (jsonNull) {
                    if (coerced == null) {
                        coerced = new ArrayList<>(args);
                    }
                    coerced.set(i, null);
                }
                continue;
            }
            if (!(paramType instanceof DateTimeType)) {
                continue;
            }
            final Object converted = SharedFunctionHelpers.toTemporalValue(
                paramType.getName().toUpperCase(), value);
            if (converted != value) {
                if (coerced == null) {
                    coerced = new ArrayList<>(args);
                }
                coerced.set(i, converted);
            }
        }
        return coerced != null ? coerced : args;
    }

    /**
     * Extend {@code args} to the function's full parameter list by evaluating the DEFAULT expression of each
     * trailing parameter the caller omitted, as a stored-procedure CALL already does for procedures. Returns
     * {@code args} untouched when nothing is missing.
     */
    private List<Object> withParameterDefaults(final Function function, final List<Object> args) {
        final List<Parameter> params = function.getParameters();
        if (args.size() >= params.size()) {
            return args;
        }
        final List<Object> full = new ArrayList<>(args);
        for (int i = args.size(); i < params.size(); i++) {
            final Parameter param = params.get(i);
            if (!param.hasDefault()) {
                throw new RuntimeException("Missing required argument for parameter: " + param.getName());
            }
            full.add(evaluateParameterDefault(param));
        }
        return full;
    }

    /** Evaluate a parameter's DEFAULT expression text (it is stored as source, e.g. {@code 'str'} or TRUE). */
    private Object evaluateParameterDefault(final Parameter param) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final ExpressionEvaluator eval = new ExpressionEvaluator(
            null, queryExecutor.getFunctionRegistry(), queryExecutor.getCatalog(), queryExecutor);
        return eval.evaluate(param.getDefaultValue(), null);
    }

    Function resolveOverloadedFunction(final Schema schema, final String funcName, final List<Object> argValues) {
        final List<Function> overloads = schema.getFunctionOverloads(funcName);
        if (overloads.isEmpty()) {
            return null;
        }

        // If only one overload, use it
        if (overloads.size() == 1) {
            final Function func = overloads.get(0);
            if (acceptsArgumentCount(func, argValues.size())) {
                return func;
            }
            throw new RuntimeException("Function " + funcName + " expects " + func.getParameters().size() +
                " parameters but got " + argValues.size());
        }

        // Multiple overloads - match by argument count and types
        final List<Function> candidatesByCount = new ArrayList<>();
        for (final Function func : overloads) {
            if (acceptsArgumentCount(func, argValues.size())) {
                candidatesByCount.add(func);
            }
        }

        if (candidatesByCount.isEmpty()) {
            throw new RuntimeException("No overload of function " + funcName + " accepts " + argValues.size() + " parameters");
        }

        if (candidatesByCount.size() == 1) {
            return candidatesByCount.get(0);
        }

        // Multiple candidates with same parameter count - try type matching
        for (final Function func : candidatesByCount) {
            boolean typesMatch = true;
            final List<Parameter> params = func.getParameters();
            for (int i = 0; i < params.size(); i++) {
                final Parameter param = params.get(i);
                final Object argValue = argValues.get(i);
                if (!isCompatibleArgument(argValue, param.getDataType())) {
                    typesMatch = false;
                    break;
                }
            }
            if (typesMatch) {
                return func;
            }
        }

        // No exact match found, return first candidate as fallback
        return candidatesByCount.get(0);
    }

    private boolean isCompatibleArgument(final Object argValue, final DataType expectedType) {
        if (argValue == null) {
            return true; // NULL is compatible with all types
        }

        final String typeName = expectedType.getName().toUpperCase();

        // String types
        if (typeName.equals("STRING") || typeName.equals("VARCHAR") || typeName.equals("TEXT")) {
            return argValue instanceof String;
        }

        // Integer types
        if (typeName.equals("INTEGER") || typeName.equals("INT") || typeName.equals("BIGINT") || typeName.equals("SMALLINT")) {
            return argValue instanceof Long || argValue instanceof Integer || argValue instanceof Short || argValue instanceof Byte;
        }

        // Decimal/Numeric types
        if (typeName.equals("DECIMAL") || typeName.equals("NUMERIC") || typeName.equals("NUMBER")) {
            return argValue instanceof Number;
        }

        // Float/Double types
        if (typeName.equals("FLOAT") || typeName.equals("DOUBLE")) {
            return argValue instanceof Double || argValue instanceof Float;
        }

        // Boolean type
        if (typeName.equals("BOOLEAN")) {
            return argValue instanceof Boolean;
        }

        // Date/Time types
        if (typeName.equals("DATE") || typeName.equals("TIMESTAMP") || typeName.equals("TIME")) {
            return argValue instanceof LocalDate || argValue instanceof LocalDateTime ||
                   argValue instanceof LocalTime || argValue instanceof String;
        }

        // Default: allow anything
        return true;
    }

    /**
     * Reorder positionally-evaluated argument values to a function's declared parameter order when the call
     * used named arguments — {@code f(b => 2, a => 1)}. Any leading positional arguments (name == null) map
     * to the first parameters in order; each named argument maps to the parameter whose name matches
     * (case-insensitive). An unknown name or too many positional arguments is an error.
     */
    List<Object> reorderNamedArgs(final List<String> names, final List<Object> argValues,
            final List<Parameter> params, final String funcName) {
        final Object[] out = new Object[params.size()];
        final boolean[] supplied = new boolean[params.size()];
        int positional = 0;
        while (positional < names.size() && names.get(positional) == null) {
            if (positional >= params.size()) {
                throw new RuntimeException("Too many arguments for function " + funcName);
            }
            out[positional] = argValues.get(positional);
            supplied[positional] = true;
            positional++;
        }
        for (int j = positional; j < names.size(); j++) {
            final String argName = names.get(j);
            int target = -1;
            for (int p = 0; p < params.size(); p++) {
                if (params.get(p).getName().equalsIgnoreCase(argName)) {
                    target = p;
                    break;
                }
            }
            if (target < 0) {
                throw new RuntimeException("Unknown argument name '" + argName + "' for function " + funcName);
            }
            out[target] = argValues.get(j);
            supplied[target] = true;
        }
        // A slot no argument named keeps its DEFAULT rather than NULL. This array is already full length, so the
        // positional padding in evaluateUserDefinedFunction cannot see the gap — it has to be filled here.
        for (int p = 0; p < params.size(); p++) {
            if (!supplied[p] && params.get(p).hasDefault()) {
                out[p] = evaluateParameterDefault(params.get(p));
            }
        }
        return Arrays.asList(out);
    }

    Object evaluateUserDefinedFunction(final Function function, final List<Object> rawArgs) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final List<Object> args = coerceArgsToParameterTypes(function, withParameterDefaults(function, rawArgs));
        // RETURNS NULL ON NULL INPUT (a.k.a. STRICT): a NULL in ANY argument short-circuits to NULL and the
        // body is NEVER entered. The clause was parsed and stored but not enforced, so a handler written on
        // that guarantee ran anyway and blew up on the null — e.g. `for k, v in OBJ.items()` raised
        // "AttributeError: 'NoneType' object has no attribute 'items'". Checked AFTER defaults are applied, so
        // an omitted parameter uses its default rather than counting as a NULL argument.
        if (NullHandling.RETURNS_NULL_ON_NULL_INPUT == NullHandling.fromString(function.getNullHandling())) {
            for (final Object arg : args) {
                if (arg == null) {
                    return null;
                }
            }
        }
        final UdfLanguage language = function.getUdfLanguage();

        if (language == UdfLanguage.JAVA) {
            return evaluateJavaFunction(function, args);
        } else if (language == UdfLanguage.SQL) {
            return evaluateSqlFunction(function, args);
        } else if (language == UdfLanguage.JAVASCRIPT || language == UdfLanguage.PYTHON
                || language == UdfLanguage.SCALA) {
            final Object guestResult = UdfRuntimes.require(language).executeFunction(function, args,
                catalog, queryExecutor != null ? queryExecutor.getS3PathResolver() : null);
            return wrapSemiStructuredReturn(function, guestResult);
        } else {
            throw new RuntimeException("Unsupported function language: " + language);
        }
    }


    /**
     * A guest-language handler returns OBJECT / ARRAY values as JSON text; a function DECLARED to
     * return a semi-structured type hands that text back as a real semi-structured value, so a
     * container embeds it structurally — OBJECT_CONSTRUCT('k', js_array_fn(...)) nests the array
     * instead of quoting its text — and a scalar string under a VARIANT declaration becomes a
     * VARIANT STRING, exactly as the SQL-side cast would make it.
     */
    private Object wrapSemiStructuredReturn(final Function function, final Object result) {
        if (!(result instanceof String)) {
            return result;
        }
        final DataType returnType = function.getReturnType();
        if (!(returnType instanceof ArrayType || returnType instanceof ObjectType
                || returnType instanceof VariantType)) {
            return result;
        }
        final String text = ((String) result).trim();
        if (text.startsWith("[") || text.startsWith("{") || text.startsWith("\"")
                || "null".equals(text)) {
            return VariantValue.of(text);
        }
        if (returnType instanceof VariantType) {
            return VariantValue.of(StringNode.valueOf((String) result).toString());
        }
        return result;
    }

    private Object evaluateSqlFunction(final Function function, final List<Object> args) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final String body = function.getBody();
        if (body == null || body.isEmpty()) return null;

        // Bind parameter names to argument values
        final List<Parameter> params = function.getParameters();

        // If the body is a query statement (SELECT / WITH … SELECT), execute it via queryExecutor; a bare
        // scalar expression body falls through to expression evaluation. Decided by parsing, not a prefix.
        final String trimmedBody = body.trim();
        if (queryExecutor != null && queryExecutor.isQueryStatement(trimmedBody)) {
            // Substitute parameter placeholders
            final String sql = substituteParams(trimmedBody, params, args);
            try {
                final List<ResultSet> results = queryExecutor.execute(sql);
                if (!results.isEmpty() && results.get(0).getRowCount() > 0) {
                    return results.get(0).getRows().get(0).getValue(0);
                }
                return null;
            } catch (final Exception e) {
                throw new RuntimeException("Error executing SQL function: " + describe(e), e);
            }
        }

        // A body that is a Snowflake-Scripting block runs as one — parameters bound as procedural
        // variables in an isolated scope, control flow and all — rather than being substituted into text.
        // Live-verified on a real account: a scalar SQL UDF whose body is `BEGIN … END` is
        // created AND executed there (`AS $$ BEGIN RETURN 1; END $$` → SELECT f() = 1), with variables,
        // DECLARE sections, IF/loops and EXCEPTION handlers all working. Delimiters are irrelevant here:
        // extractBodyDefinition already stripped $$…$$ / '…', and the unquoted `AS BEGIN … END` form
        // parses to the same text.
        if (queryExecutor != null && queryExecutor.isProceduralBlock(trimmedBody)) {
            rejectUnsupportedScriptingReturnType(function);
            final Object returned = queryExecutor.executeScriptingFunctionBody(function, args);
            // Snowflake casts the RETURNed value to the DECLARED return type: `RETURNS INT` over
            // `RETURN '7'` is 7, `RETURNS VARCHAR` over `RETURN 42` is the string '42', and
            // `RETURNS INT` over `RETURN 'abc'` fails "Numeric value 'abc' is not recognized".
            return queryExecutor.getProceduralExecutor().coerceToType(returned, function.getReturnType());
        }
        if (queryExecutor != null) {
            // A body that OPENS a block but does not parse as one is not an expression either, and its
            // own syntax error says why. Live refuses such a body at CREATE ("Compilation of SQL UDF
            // failed: …"); the CREATE-time check here fails open on a body the grammar cannot read, so
            // the refusal lands on the call instead — with the same compilation error, not an opaque
            // evaluator failure.
            queryExecutor.reportBlockSyntaxError(trimmedBody);
        }

        // Otherwise evaluate the body as an expression with param substitution. The body arrives ALREADY
        // unquoted — extractBodyDefinition removed the $$…$$ or '…' delimiters when the routine was created —
        // so it must not be unquoted again here. Stripping a second time corrupted every body that IS an
        // expression containing a string literal: `AS $$ 'plain' $$` became the column reference `plain`
        // ("Column not found: PLAIN") and `AS $$ 'x' || 'y' $$` became `x' || 'y`.
        //
        // Params are substituted into the TEXT here too (not only bound via the __UDF__ table below):
        // an expression body may contain a scalar SUBQUERY — even over nested derived tables — and the
        // subquery executes through the query engine, where the parameter table's bindings are not
        // visible. Substituting first (lexer-driven, quote-safe) makes the body self-contained; the
        // table binding then covers nothing but is kept as a harmless fallback.
        final String exprBody = substituteParams(trimmedBody, params, args);

        // Bind all parameters into a single multi-column table row
        if (!params.isEmpty() && queryExecutor != null) {
            final List<TableColumn> cols = new ArrayList<>();
            final List<Object> rowVals = new ArrayList<>();
            for (int i = 0; i < params.size() && i < args.size(); i++) {
                cols.add(new TableColumn(
                    params.get(i).getName(), params.get(i).getDataType(), true, null, false, false, false));
                rowVals.add(args.get(i));
            }
            final Table paramTable =
                new Table("__UDF__", cols, false);
            final Row paramRow = new Row(rowVals);
            final ExpressionEvaluator eval =
                new ExpressionEvaluator(paramTable, functionRegistry,
                    queryExecutor.getCatalog(), queryExecutor);
            try {
                return eval.evaluate(exprBody, paramRow);
            } catch (final Exception e) {
                throw new RuntimeException("Error in SQL function body: " + describe(e), e);
            }
        }

        // No params — evaluate body directly
        if (queryExecutor != null) {
            final Table emptyTable =
                new Table("__UDF__", Collections.emptyList(), false);
            final Row emptyRow =
                new Row(Collections.emptyList());
            final ExpressionEvaluator eval =
                new ExpressionEvaluator(emptyTable, functionRegistry,
                    queryExecutor.getCatalog(), queryExecutor);
            try {
                return eval.evaluate(exprBody, emptyRow);
            } catch (final Exception e) {
                throw new RuntimeException("Error in SQL function body: " + describe(e), e);
            }
        }
        return null;
    }

    /**
     * A scripting-block UDF may not RETURN a semi-structured value. Snowflake compiles such a body lazily,
     * so this surfaces on the CALL and not on the CREATE (a block-bodied
     * function declared {@code RETURNS VARIANT} / {@code ARRAY} / {@code OBJECT} is created without
     * complaint, then every call fails "Unsupported return type for Snowscript UDF: VARIANT"). Every other
     * type was verified to work — VARCHAR, NUMBER(p,s), FLOAT, BOOLEAN, BINARY, DATE, TIMESTAMP_NTZ — and
     * a semi-structured PARAMETER is fine; only the return type is refused.
     */
    private void rejectUnsupportedScriptingReturnType(final Function function) {
        final DataType returnType = function.getReturnType();
        if (returnType != null && returnType.getCategory() == TypeCategory.SEMI_STRUCTURED) {
            throw new RuntimeException("Unsupported return type for Snowscript UDF: "
                + returnType.getName().toUpperCase());
        }
    }

    /** The exception's message, or its class name when the message is null (an NPE's message usually is) —
     *  so a wrapped failure never surfaces as the bare text "null". */
    private String describe(final Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    /** Substitute each parameter NAME in {@code body} with its argument rendered as a SQL literal
     *  (NULL / numbers and booleans raw / anything else as a quoted, escaped string), via the
     *  lexer-driven identifier substitution — quote- and comment-safe by construction. A parameter
     *  DECLARED semi-structured (OBJECT / VARIANT / ARRAY) is wrapped as {@code PARSE_JSON('…')}
     *  instead of a bare string: bodies apply path access to such parameters ({@code p:field}),
     *  which must stay parseable and yield the structure, not text. */
    private String substituteParams(final String body, final List<Parameter> params, final List<Object> args) {
        String sql = body;
        for (int i = 0; i < params.size() && i < args.size(); i++) {
            final Parameter param = params.get(i);
            final Object argVal = args.get(i);
            final String argStr;
            if (argVal == null) {
                argStr = "NULL";
            } else if (argVal instanceof Number || argVal instanceof Boolean) {
                argStr = argVal.toString();
            } else if (argVal instanceof LocalDateTime || argVal instanceof LocalDate || argVal instanceof LocalTime) {
                // A temporal substitutes as a typed literal in Snowflake's output text form, not
                // java.time's T-separated toString — so the body keeps a real temporal (date arithmetic
                // works) and VARIANT/OBJECT output renders it as '2026-01-03 00:00:00.000'.
                final String cast = argVal instanceof LocalDate ? "DATE"
                    : argVal instanceof LocalTime ? "TIME" : "TIMESTAMP_NTZ";
                argStr = "'" + SharedFunctionHelpers.textOf(argVal) + "'::" + cast;
            } else if (param.getDataType() != null
                    && param.getDataType().getCategory() == TypeCategory.SEMI_STRUCTURED) {
                argStr = "PARSE_JSON(" + SqlStringLiterals.encode(argVal.toString()) + ")";
            } else if (param.getDataType() instanceof VectorType) {
                // A VECTOR parameter substitutes as a TYPED vector literal, like the temporals above:
                // the vector functions' argument rules are compile-time and read the STATIC type, so a
                // bare string body would fail "Invalid argument types … (VARCHAR(13))" where Snowflake
                // evaluates the body fine (live: a UDF whose body is
                // VECTOR_L2_DISTANCE(a, b) over two VECTOR(FLOAT,3) parameters returns 5.196152422706632).
                argStr = SqlStringLiterals.encode(argVal.toString()) + "::" + param.getDataType().getName();
            } else {
                argStr = SqlStringLiterals.encode(argVal.toString());
            }
            sql = SqlIdentifierSubstitution.substitute(sql, param.getName(), argStr);
        }
        return sql;
    }

    private Object evaluateJavaFunction(final Function function, final List<Object> args) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final String handler = function.getHandler();
        if (handler == null || handler.isEmpty()) {
            throw new RuntimeException("Java function must specify a HANDLER");
        }

        // Parse handler: "ClassName.methodName"
        final int dotIndex = handler.lastIndexOf('.');
        if (dotIndex < 0) {
            throw new RuntimeException("Invalid handler format. Expected 'ClassName.methodName', got: " + handler);
        }

        final String className = handler.substring(0, dotIndex);
        final String methodName = handler.substring(dotIndex + 1);

        // The handler class comes from the IMPORTS jar(s) if given, else from compiling the inline body.
        final Class<?> compiledClass;
        if (!function.getImports().isEmpty()) {
            compiledClass = JarHandlerLoader.load(function.getImports(), className, catalog,
                queryExecutor != null ? queryExecutor.getS3PathResolver() : null);
        } else {
            final String sourceCode = function.getBody();
            if (sourceCode == null || sourceCode.trim().isEmpty()) {
                throw new RuntimeException("Java function must specify an inline body (AS ...) or IMPORTS");
            }
            compiledClass = javaCompiler.compile(sourceCode, className);
        }

        // Convert SQL argument values to Java types
        final Object[] javaArgs = convertSqlArgsToJava(args);

        // Invoke the method
        final Object result = javaCompiler.invokeMethod(compiledClass, methodName, javaArgs);

        // Convert result back to SQL type
        return convertJavaResultToSql(result);
    }

    private Object[] convertSqlArgsToJava(final List<Object> sqlArgs) {
        final Object[] javaArgs = new Object[sqlArgs.size()];
        for (int i = 0; i < sqlArgs.size(); i++) {
            final Object arg = sqlArgs.get(i);
            if (arg == null) {
                javaArgs[i] = null;
            } else if (arg instanceof Long) {
                javaArgs[i] = ((Long) arg).intValue();
            } else if (arg instanceof Number || arg instanceof String || arg instanceof Boolean) {
                javaArgs[i] = arg;
            } else {
                javaArgs[i] = arg.toString();
            }
        }
        return javaArgs;
    }

    private Object convertJavaResultToSql(final Object javaResult) {
        if (javaResult == null) {
            return null;
        } else if (javaResult instanceof Integer) {
            return ((Integer) javaResult).longValue();
        } else if (javaResult instanceof Number || javaResult instanceof String || javaResult instanceof Boolean) {
            return javaResult;
        } else {
            return javaResult.toString();
        }
    }
}
