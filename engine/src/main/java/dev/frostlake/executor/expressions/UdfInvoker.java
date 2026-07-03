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
import dev.frostlake.executor.udf.JarHandlerLoader;
import dev.frostlake.executor.udf.JavaFunctionCompiler;
import dev.frostlake.executor.udf.JavaScriptExecutor;
import dev.frostlake.executor.udf.PythonExecutor;
import dev.frostlake.executor.udf.ScalaFunctionExecutor;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;

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

    Function resolveOverloadedFunction(final Schema schema, final String funcName, final List<Object> argValues) {
        List<Function> overloads = schema.getFunctionOverloads(funcName);
        if (overloads.isEmpty()) {
            return null;
        }

        // If only one overload, use it
        if (overloads.size() == 1) {
            Function func = overloads.get(0);
            if (func.getParameters().size() == argValues.size()) {
                return func;
            }
            throw new RuntimeException("Function " + funcName + " expects " + func.getParameters().size() +
                " parameters but got " + argValues.size());
        }

        // Multiple overloads - match by argument count and types
        List<Function> candidatesByCount = new ArrayList<>();
        for (final Function func : overloads) {
            if (func.getParameters().size() == argValues.size()) {
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
            List<Parameter> params = func.getParameters();
            for (int i = 0; i < params.size(); i++) {
                Parameter param = params.get(i);
                Object argValue = argValues.get(i);
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

        String typeName = expectedType.getName().toUpperCase();

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
        int positional = 0;
        while (positional < names.size() && names.get(positional) == null) {
            if (positional >= params.size()) {
                throw new RuntimeException("Too many arguments for function " + funcName);
            }
            out[positional] = argValues.get(positional);
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
        }
        return Arrays.asList(out);
    }

    Object evaluateUserDefinedFunction(final Function function, final List<Object> args) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final UdfLanguage language = function.getUdfLanguage();

        if (language == UdfLanguage.JAVA) {
            return evaluateJavaFunction(function, args);
        } else if (language == UdfLanguage.SQL) {
            return evaluateSqlFunction(function, args);
        } else if (language == UdfLanguage.JAVASCRIPT) {
            return JavaScriptExecutor.executeJavaScriptFunction(function, args);
        } else if (language == UdfLanguage.PYTHON) {
            return PythonExecutor.executePythonFunction(function, args);
        } else if (language == UdfLanguage.SCALA) {
            return ScalaFunctionExecutor.executeScalaFunction(function, args, catalog,
                queryExecutor != null ? queryExecutor.getS3PathResolver() : null);
        } else {
            throw new RuntimeException("Unsupported function language: " + language);
        }
    }

    private Object evaluateSqlFunction(final Function function, final List<Object> args) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        String body = function.getBody();
        if (body == null || body.isEmpty()) return null;

        // Bind parameter names to argument values
        List<Parameter> params = function.getParameters();

        // If the body is a query statement (SELECT / WITH … SELECT), execute it via queryExecutor; a bare
        // scalar expression body falls through to expression evaluation. Decided by parsing, not a prefix.
        String trimmedBody = body.trim();
        if (queryExecutor != null && queryExecutor.isQueryStatement(trimmedBody)) {
            // Substitute parameter placeholders
            String sql = trimmedBody;
            for (int i = 0; i < params.size() && i < args.size(); i++) {
                String paramName = params.get(i).getName();
                Object argVal = args.get(i);
                String argStr = argVal == null ? "NULL"
                    : argVal instanceof String ? "'" + argVal.toString().replace("'", "''") + "'"
                    : argVal.toString();
                sql = SqlIdentifierSubstitution.substitute(sql, paramName, argStr);
            }
            try {
                List<ResultSet> results = queryExecutor.execute(sql);
                if (!results.isEmpty() && results.get(0).getRowCount() > 0) {
                    return results.get(0).getRows().get(0).getValue(0);
                }
                return null;
            } catch (final Exception e) {
                throw new RuntimeException("Error executing SQL function: " + e.getMessage(), e);
            }
        }

        // Otherwise evaluate body as an expression with param substitution
        String exprBody = trimmedBody;
        // Strip surrounding quotes if it's a string literal body
        if ((exprBody.startsWith("'") && exprBody.endsWith("'")) ||
            (exprBody.startsWith("$$") && exprBody.endsWith("$$"))) {
            if (exprBody.startsWith("$$")) {
                exprBody = exprBody.substring(2, exprBody.length() - 2).trim();
            } else {
                exprBody = exprBody.substring(1, exprBody.length() - 1);
            }
        }

        // Bind all parameters into a single multi-column table row
        if (!params.isEmpty() && queryExecutor != null) {
            List<TableColumn> cols = new ArrayList<>();
            List<Object> rowVals = new ArrayList<>();
            for (int i = 0; i < params.size() && i < args.size(); i++) {
                cols.add(new TableColumn(
                    params.get(i).getName(), params.get(i).getDataType(), true, null, false, false, false));
                rowVals.add(args.get(i));
            }
            Table paramTable =
                new Table("__UDF__", cols, false);
            Row paramRow = new Row(rowVals);
            ExpressionEvaluator eval =
                new ExpressionEvaluator(paramTable, functionRegistry,
                    queryExecutor.getCatalog(), queryExecutor);
            try {
                return eval.evaluate(exprBody, paramRow);
            } catch (final Exception e) {
                throw new RuntimeException("Error in SQL function body: " + e.getMessage(), e);
            }
        }

        // No params — evaluate body directly
        if (queryExecutor != null) {
            Table emptyTable =
                new Table("__UDF__", Collections.emptyList(), false);
            Row emptyRow =
                new Row(Collections.emptyList());
            ExpressionEvaluator eval =
                new ExpressionEvaluator(emptyTable, functionRegistry,
                    queryExecutor.getCatalog(), queryExecutor);
            try {
                return eval.evaluate(exprBody, emptyRow);
            } catch (final Exception e) {
                throw new RuntimeException("Error in SQL function body: " + e.getMessage(), e);
            }
        }
        return null;
    }

    private Object evaluateJavaFunction(final Function function, final List<Object> args) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        String handler = function.getHandler();
        if (handler == null || handler.isEmpty()) {
            throw new RuntimeException("Java function must specify a HANDLER");
        }

        // Parse handler: "ClassName.methodName"
        int dotIndex = handler.lastIndexOf('.');
        if (dotIndex < 0) {
            throw new RuntimeException("Invalid handler format. Expected 'ClassName.methodName', got: " + handler);
        }

        String className = handler.substring(0, dotIndex);
        String methodName = handler.substring(dotIndex + 1);

        // The handler class comes from the IMPORTS jar(s) if given, else from compiling the inline body.
        Class<?> compiledClass;
        if (!function.getImports().isEmpty()) {
            compiledClass = JarHandlerLoader.load(function.getImports(), className, catalog,
                queryExecutor != null ? queryExecutor.getS3PathResolver() : null);
        } else {
            String sourceCode = function.getBody();
            if (sourceCode == null || sourceCode.trim().isEmpty()) {
                throw new RuntimeException("Java function must specify an inline body (AS ...) or IMPORTS");
            }
            compiledClass = javaCompiler.compile(sourceCode, className);
        }

        // Convert SQL argument values to Java types
        Object[] javaArgs = convertSqlArgsToJava(args);

        // Invoke the method
        Object result = javaCompiler.invokeMethod(compiledClass, methodName, javaArgs);

        // Convert result back to SQL type
        return convertJavaResultToSql(result);
    }

    private Object[] convertSqlArgsToJava(final List<Object> sqlArgs) {
        Object[] javaArgs = new Object[sqlArgs.size()];
        for (int i = 0; i < sqlArgs.size(); i++) {
            Object arg = sqlArgs.get(i);
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
