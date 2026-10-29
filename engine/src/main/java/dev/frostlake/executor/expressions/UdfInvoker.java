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

import dev.frostlake.config.S3PathResolver;
import dev.frostlake.executor.AmbiguousOverload;
import dev.frostlake.executor.DeferredFault;
import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.NamedArguments;
import dev.frostlake.executor.OverloadConversionCosts;
import dev.frostlake.executor.ParameterReferences;
import dev.frostlake.executor.PrivilegeRefusal;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.ReadOnlyRequest;
import dev.frostlake.executor.RoutineBody;
import dev.frostlake.executor.RoutineSecretsCheck;
import dev.frostlake.executor.SqlArgumentText;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.executor.commands.DeferredHandlerCheck;
import dev.frostlake.executor.commands.ExternalFunctions;
import dev.frostlake.executor.commands.ServiceFunctions;
import dev.frostlake.executor.udf.GuestResults;
import dev.frostlake.executor.udf.HandlerClassNotFound;
import dev.frostlake.executor.udf.HandlerResults;
import dev.frostlake.executor.udf.HandlerStateFactory;
import dev.frostlake.executor.udf.ImportLocalizer;
import dev.frostlake.executor.udf.JarHandlerLoader;
import dev.frostlake.executor.udf.JavaFunctionCompiler;
import dev.frostlake.executor.udf.JavaHandlerArguments;
import dev.frostlake.executor.udf.JavaHandlerClass;
import dev.frostlake.executor.udf.JavaHandlerFailure;
import dev.frostlake.executor.udf.JavaHandlerInstance;
import dev.frostlake.executor.udf.JavaHandlerInstances;
import dev.frostlake.executor.udf.JavaHandlerMethods;
import dev.frostlake.executor.udf.JavaRuntimeLevel;
import dev.frostlake.executor.udf.StatementHandlerStates;
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
import dev.frostlake.metastore.model.Volatility;
import dev.frostlake.persistence.CheckpointDebt;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StructuredTypes;
import dev.frostlake.types.TypeCategory;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantValue;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Function / user-defined-function dispatch extracted from {@link ExpressionEvaluatorVisitor}: overload
 * resolution, named-argument reordering, and evaluation of SQL / Java / JavaScript / Python / Scala
 * UDFs. Holds the immutable {@code catalog} and {@code functionRegistry}; the mutable query executor is
 * read live from the owning visitor via {@link ExpressionEvaluatorVisitor#getQueryExecutor()} so a later
 * {@code setQueryExecutor} is observed.
 */
final class UdfInvoker {

    /**
     * What a conversion weighs that no row of {@link OverloadConversionCosts} names: more than any that is named,
     * so an overload reached that way is chosen last.
     */
    private static final int UNNAMED_CONVERSION = 64;

    private static final JavaFunctionCompiler javaCompiler = new JavaFunctionCompiler();

    /** Names the state that holds a statement's own load of a Java handler's class. */
    private static final String JAVA_HANDLER_CLASS = "java-handler-class";

    /** Names the state that holds a statement's own load of a handler class an IMPORTS jar holds. */
    private static final String JAR_HANDLER_CLASS = "jar-handler-class";

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
    static boolean acceptsArgumentCount(final Function func, final int argCount) {
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
    private List<Object> coerceArgsToParameterTypes(final Function function, final List<Object> args,
                                                    final List<DataType> argTypes) {
        final List<Parameter> params = function.getParameters();
        final boolean sqlBody = function.getUdfLanguage() == UdfLanguage.SQL;
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
                    continue;
                }
                // Anything else falls through to the conversion below: a scalar variant becomes a
                // one-element ARRAY, and one handed to an OBJECT parameter is refused, exactly as a
                // CAST to that type answers (live-verified).
            }
            if (sqlBody && !(paramType instanceof VariantType)) {
                // A VARIANT parameter takes the value AS IT IS - that is what a VARIANT holds - so it is
                // the one target with nothing to convert.
                //
                // A SQL UDF converts each argument to its parameter's DECLARED type as the value
                // arrives - the same conversion a CAST to that type performs, refusing in the same
                // words where the value will not convert (live-verified). Without it the argument
                // reached the body as its text and the declared type never spoke.
                // A DECFLOAT is no NUMBER of its own: it converts to the parameter's type and rounds to its scale,
                // f(CAST(1.5 AS DECFLOAT)) into NUMBER reading 2 (live-verified).
                if (keepsItsOwnNumber(paramType, value) && !isDecfloat(argTypes, i)) {
                    // An exact number reaches an exact NUMBER parameter unconverted, so the parameter's
                    // SCALE never rounds it: num_p(1.257) into NUMBER(10,2) answers 1.257. One of a smaller
                    // scale is read at the parameter's where the body names it — 1 is 1.00 there — which is
                    // the body's text to say (see SqlArgumentText). A TEXT argument still converts — and
                    // rounds — because it has to become a number at all (live-verified both ways).
                    continue;
                }
                Object cast;
                try {
                    cast = ValueCaster.castValue(value, SqlTypeNames.canonical(paramType));
                } catch (final RuntimeException notConvertible) {
                    // The account converts where the body READS the parameter, so the refusal waits there.
                    cast = new DeferredConversion(value, paramType, notConvertible);
                }
                if (cast != value) {
                    if (coerced == null) {
                        coerced = new ArrayList<>(args);
                    }
                    coerced.set(i, cast);
                }
                continue;
            }
            if (handlerTakesSqlTypes(function.getUdfLanguage()) && paramType != null) {
                // A JavaScript, a Java, a Scala or a Python handler's argument becomes its parameter's SQL type as
                // it arrives, before the handler's own mapping, as a procedure's does (live-verified): a NUMBER
                // parameter rounds a fraction away (1.5 is 2 for an int handler) and reads a text ('42'), a
                // NUMBER(10,2) one holds two decimals. For a JavaScript handler: a NUMBER or a text for a FLOAT
                // parameter is a FLOAT - the handler computes with a number, where a host decimal concatenated
                // under + - anything scalar for a VARCHAR its text, a text for a BOOLEAN or a DATE that value, and
                // one that does not convert is the call's refusal, "Numeric value 'abc' is not recognized". A
                // variant that is no array is a one-element ARRAY, and one that is no object is refused for an
                // OBJECT: "Failed to cast variant value 1 to OBJECT".
                final Object arrived = visitor.getQueryExecutor().getProceduralExecutor()
                    .coerceArgument(value, paramType, false);
                if (arrived != value) {
                    if (coerced == null) {
                        coerced = new ArrayList<>(args);
                    }
                    coerced.set(i, arrived);
                }
                continue;
            }
            if (!(paramType instanceof DateTimeType)) {
                continue;
            }
            final Object converted = SharedFunctionHelpers.toTemporalValue(
                paramType.getName().toUpperCase(Locale.ROOT), value);
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
        return resolveOverloadedFunction(schema, funcName, argValues, null);
    }

    /**
     * The overload a call reaches, ranking the candidates by each argument's family.
     *
     * @param argumentFamilies each argument's family as {@link UdfOverloadPreference#familyOf} reads it, in
     *                         argument order, or null to read every family off the values
     */
    Function resolveOverloadedFunction(final Schema schema, final String funcName, final List<Object> argValues,
                                       final List<String> argumentFamilies) {
        return resolveOverloadedFunction(schema, funcName, argValues, argumentFamilies, null);
    }

    /**
     * {@link #resolveOverloadedFunction(Schema, String, List, List)} told each argument's static type, so that
     * an overload the structured binding rule refuses is never chosen: a structured argument goes to the
     * overload declaring its shape and a plain OBJECT or ARRAY to the plain one, where both rank alike by
     * family and the first declared used to win, only to be refused once called.
     *
     * @param argumentStaticTypes each argument's static type (null where unknown), or null for none
     */
    Function resolveOverloadedFunction(final Schema schema, final String funcName, final List<Object> argValues,
                                       final List<String> argumentFamilies, final List<DataType> argumentStaticTypes) {
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

        // Several overloads take the count: of those that ACCEPT the arguments the call runs the one whose
        // conversions weigh least over all its arguments together, and of several that weigh alike the first in
        // the order of their signatures (see OverloadConversionCosts). The account does not pick the nearest
        // type argument by argument, and taking the first declared one answered differently.
        final List<Function> accepting = new ArrayList<>();
        for (final Function func : candidatesByCount) {
            boolean typesMatch = true;
            final List<Parameter> params = func.getParameters();
            // The arguments written are judged; a parameter the call leaves to its default has none.
            for (int i = 0; typesMatch && i < argValues.size(); i++) {
                final Parameter param = params.get(i);
                typesMatch = isCompatibleArgument(argValues.get(i), param.getDataType())
                    && (argumentStaticTypes == null || i >= argumentStaticTypes.size()
                        || StructuredTypes.bindsAsArgument(argumentStaticTypes.get(i), param.getDataType()));
            }
            if (typesMatch) {
                accepting.add(func);
            }
        }
        if (accepting.isEmpty()) {
            // No overload accepts the arguments: the first one's conversion says why.
            return candidatesByCount.get(0);
        }
        return firstBySignature(lightest(accepting, null, argumentFamilies, argValues));
    }

    /**
     * The overload a call written with named arguments reaches (live-verified): only an overload that declares
     * every name written and is left no required parameter unfilled is a candidate, each value is judged against
     * the parameter its name fills — not the one at its place in the call — and of several candidates the one
     * whose conversions weigh least runs. Candidates that weigh alike are not told apart by a call written so:
     * it is refused as {@link AmbiguousOverload}.
     *
     * @param schema           the schema the function's overloads live in
     * @param funcName         the function's canonical name
     * @param spelledName      the name as the call writes it, for the refusal
     * @param names            the names as written, one per argument, null for a positional one
     * @param argValues        the arguments' values, in the order written
     * @param argumentFamilies each argument's family, in the order written, or null to read them off the values
     * @return the overload, or null when the schema holds none of that name
     */
    Function resolveNamedOverload(final Schema schema, final String funcName, final String spelledName,
                                  final List<String> names, final List<Object> argValues,
                                  final List<String> argumentFamilies) {
        final List<Function> fitting = new ArrayList<>();
        final List<int[]> targets = new ArrayList<>();
        for (final Function overload : schema.getFunctionOverloads(funcName)) {
            final int[] filled = NamedArguments.targets(overload.getParameters(), names);
            if (filled != null) {
                fitting.add(overload);
                targets.add(filled);
            }
        }
        if (fitting.isEmpty()) {
            // No overload takes the names: the call is refused where its names are read.
            return resolveOverloadedFunction(schema, funcName, argValues, argumentFamilies, null);
        }
        if (fitting.size() == 1) {
            return fitting.get(0);
        }
        final List<Function> accepting = new ArrayList<>();
        final List<int[]> acceptingTargets = new ArrayList<>();
        for (int c = 0; c < fitting.size(); c++) {
            final List<Parameter> params = fitting.get(c).getParameters();
            boolean typesMatch = true;
            for (int i = 0; typesMatch && i < argValues.size(); i++) {
                typesMatch = isCompatibleArgument(argValues.get(i), params.get(targets.get(c)[i]).getDataType());
            }
            if (typesMatch) {
                accepting.add(fitting.get(c));
                acceptingTargets.add(targets.get(c));
            }
        }
        if (accepting.isEmpty()) {
            return fitting.get(0);
        }
        final List<Function> lightest = lightest(accepting, acceptingTargets, argumentFamilies, argValues);
        if (lightest.size() > 1) {
            throw new AmbiguousOverload(spelledName);
        }
        return lightest.get(0);
    }

    /**
     * The candidates whose conversions weigh least over all the arguments together (see
     * {@link OverloadConversionCosts}), in the order given: one where the call is decided, several where they
     * weigh alike.
     *
     * @param candidates the overloads that take the call
     * @param targets    per candidate, the parameter each argument fills, or null for arguments read by position
     * @param families   each argument's family, or null to read them off the values
     * @param values     the arguments' values, read where {@code families} is null
     * @return the lightest candidates, never empty for a non-empty list
     */
    static List<Function> lightest(final List<Function> candidates, final List<int[]> targets,
                                   final List<String> families, final List<Object> values) {
        final int count = families != null ? families.size() : values.size();
        final List<Function> lightest = new ArrayList<>();
        long least = Long.MAX_VALUE;
        for (int c = 0; c < candidates.size(); c++) {
            final List<Parameter> params = candidates.get(c).getParameters();
            long total = 0;
            for (int i = 0; i < count; i++) {
                final int target = targets == null ? i : targets.get(c)[i];
                if (target >= params.size()) {
                    total += UNNAMED_CONVERSION;
                    continue;
                }
                final DataType type = params.get(target).getDataType();
                final int weight = families != null ? UdfOverloadPreference.rankFamily(families.get(i), type)
                    : UdfOverloadPreference.rank(values.get(i), type);
                total += weight == Integer.MAX_VALUE ? UNNAMED_CONVERSION : weight;
            }
            if (total < least) {
                lightest.clear();
                least = total;
            }
            if (total == least) {
                lightest.add(candidates.get(c));
            }
        }
        return lightest;
    }

    /**
     * The first of several overloads in the order of their signatures — the account's internal type names,
     * parameter by parameter — which is the one a call written by position runs among overloads that weigh alike,
     * whatever order they were created in (live-verified). Of an OBJECT's or an ARRAY's overloads the plain one
     * comes first and the structured ones after it by their shapes: {@code f(NULL)} runs {@code f(OBJECT)} beside
     * {@code f(OBJECT(a INT))}, and that one beside {@code f(OBJECT(a VARCHAR))} and {@code f(OBJECT(b INT))}.
     *
     * @param candidates the overloads that weigh alike, never empty
     * @return the first by signature
     */
    static Function firstBySignature(final List<Function> candidates) {
        Function first = candidates.get(0);
        for (int c = 1; c < candidates.size(); c++) {
            if (sortsBefore(candidates.get(c).getParameters(), first.getParameters())) {
                first = candidates.get(c);
            }
        }
        return first;
    }

    private static boolean sortsBefore(final List<Parameter> signature, final List<Parameter> other) {
        for (int i = 0; i < signature.size() && i < other.size(); i++) {
            final int own = UdfOverloadPreference.signatureOrder(signature.get(i).getDataType());
            final int others = UdfOverloadPreference.signatureOrder(other.get(i).getDataType());
            if (own != others) {
                return own < others;
            }
            final int shapes = shapeOrder(signature.get(i).getDataType())
                .compareTo(shapeOrder(other.get(i).getDataType()));
            if (shapes != 0) {
                return shapes < 0;
            }
        }
        return signature.size() < other.size();
    }

    /** What orders an OBJECT's or an ARRAY's overloads among themselves: its spelled shape, the plain one first. */
    private static String shapeOrder(final DataType type) {
        return type instanceof ObjectType || type instanceof ArrayType || StructuredTypes.isStructured(type)
            ? StructuredTypes.signatureText(type) : "";
    }

    /**
     * The overload a call reaches among candidates that take it, chosen by each argument's family alone as
     * {@link #resolveOverloadedFunction} and {@link #resolveNamedOverload} choose it; null when no candidate's
     * parameters are named by every argument's row, and for a call written by name that several candidates weigh
     * alike for.
     *
     * @param candidates the overloads that take the call's arguments by their count, or by their names
     * @param targets    per candidate, the parameter each argument fills, or null for arguments read by position
     * @param families   each argument's family (see {@link UdfOverloadPreference#familyOf}), none of them null
     * @return the overload, or null
     */
    static Function preferredByFamilies(final List<Function> candidates, final List<int[]> targets,
                                        final List<String> families) {
        final List<Function> reachable = new ArrayList<>();
        final List<int[]> reachableTargets = targets == null ? null : new ArrayList<int[]>();
        for (int c = 0; c < candidates.size(); c++) {
            final List<Parameter> params = candidates.get(c).getParameters();
            boolean reaches = targets != null || params.size() >= families.size();
            for (int i = 0; reaches && i < families.size(); i++) {
                final int target = targets == null ? i : targets.get(c)[i];
                reaches = UdfOverloadPreference.rankFamily(families.get(i), params.get(target).getDataType())
                    != Integer.MAX_VALUE;
            }
            if (reaches) {
                reachable.add(candidates.get(c));
                if (reachableTargets != null) {
                    reachableTargets.add(targets.get(c));
                }
            }
        }
        if (reachable.isEmpty()) {
            return null;
        }
        final List<Function> lightest = lightest(reachable, reachableTargets, families, null);
        if (targets != null) {
            return lightest.size() == 1 ? lightest.get(0) : null;
        }
        return firstBySignature(lightest);
    }

    /**
     * Whether an overload ACCEPTS this argument. The account's answer is the conversion's: an overload
     * takes an argument when the value can be converted to the parameter's declared type, which is what
     * the call then does with it - so a VARCHAR parameter takes a number, a boolean and a date, while
     * neither a NUMBER nor a VARIANT one takes a DATE at all (live-verified). Which of several accepting
     * overloads is CHOSEN is a separate question, answered by {@link UdfOverloadPreference}.
     */
    static boolean isCompatibleArgument(final Object argValue, final DataType expectedType) {
        if (argValue == null || expectedType == null) {
            return true; // NULL is compatible with all types
        }
        if (expectedType instanceof VariantType) {
            // A VARIANT holds anything it is handed - except a temporal, which the account refuses.
            return !(argValue instanceof LocalDate || argValue instanceof LocalDateTime
                || argValue instanceof LocalTime || argValue instanceof OffsetDateTime
                || argValue instanceof ZonedDateTime);
        }
        // The pair of TYPES decides, not this value: an overload whose body never reads the argument
        // still runs, so a VARCHAR argument reaches a NUMBER parameter while a DATE reaches neither a
        // NUMBER nor a VARIANT one (live-verified). The order that ranks the candidates is the same
        // table that says which pairs exist at all.
        if (UdfOverloadPreference.accepts(argValue, expectedType)) {
            return true;
        }
        try {
            ValueCaster.castValue(argValue, SqlTypeNames.canonical(expectedType));
            return true;
        } catch (final RuntimeException notConvertible) {
            return false;
        }
    }

    /**
     * Reorder positionally-evaluated argument values to a function's declared parameter order when the call
     * used named arguments — {@code f(b => 2, a => 1)}. Any leading positional arguments (name == null) map
     * to the first parameters in order; each named argument maps to the parameter it names, read as an
     * identifier. An unknown name or too many positional arguments is an error.
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
            // A name is read as an identifier is — "A" names the parameter A — and matched exactly; a call no
            // compile-time rule judged still finds its parameter whatever the case it was written in.
            final String canonical = SqlIdentifiers.canonicalText(argName);
            int target = -1;
            for (int p = 0; p < params.size() && target < 0; p++) {
                if (params.get(p).getName().equals(canonical)) {
                    target = p;
                }
            }
            for (int p = 0; p < params.size() && target < 0; p++) {
                if (params.get(p).getName().equalsIgnoreCase(argName)) {
                    target = p;
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
        return evaluateUserDefinedFunction(function, rawArgs, null);
    }

    /**
     * The arguments' static types, with a DECFLOAT that converted to an exact NUMBER parameter given that parameter's
     * type: the body reads the converted value (see coerceArgsToParameterTypes).
     */
    private static List<DataType> decfloatsConverted(final Function function, final List<DataType> argTypes) {
        if (argTypes == null) {
            return null;
        }
        List<DataType> converted = null;
        final List<Parameter> params = function.getParameters();
        for (int i = 0; i < argTypes.size() && i < params.size(); i++) {
            final DataType param = params.get(i).getDataType();
            if (NumericType.isDecfloat(argTypes.get(i)) && param instanceof NumericType
                    && !NumericType.isApproximate(param) && !NumericType.isDecfloat(param)) {
                if (converted == null) {
                    converted = new ArrayList<>(argTypes);
                }
                converted.set(i, param);
            }
        }
        return converted == null ? argTypes : converted;
    }

    /** Whether the argument at {@code index} is a DECFLOAT by its static type. */
    private static boolean isDecfloat(final List<DataType> argTypes, final int index) {
        return argTypes != null && index < argTypes.size() && NumericType.isDecfloat(argTypes.get(index));
    }

    /**
     * {@link #evaluateUserDefinedFunction(Function, List)} with each argument's own static type, which a SQL
     * function's parameter takes its exact type and its binary width from (see SqlArgumentText).
     *
     * @param function the function
     * @param rawArgs  the arguments in the parameters' order
     * @param argTypes the arguments' static types in the same order, or null where they are not known
     * @return the function's answer
     */
    Object evaluateUserDefinedFunction(final Function function, final List<Object> rawArgs,
                                       final List<DataType> argTypes) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        RoutineSecretsCheck.require(catalog, function.getEnvironment());
        final List<Object> given = withParameterDefaults(function, rawArgs);
        final List<Object> args = coerceArgsToParameterTypes(function, given, argTypes);
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
        if (function.isServiceFunction()) {
            throw new RuntimeException(ServiceFunctions.cannotCall(function));
        }
        if (function.isExternalFunction()) {
            throw new RuntimeException(ExternalFunctions.cannotCall(function));
        }
        final UdfLanguage language = function.getUdfLanguage();
        // A temporary handler function's handler is judged as a row first reaches it.
        DeferredHandlerCheck.require(function);
        // A handler that is not declared IMMUTABLE may answer otherwise when called again (see CheckpointDebt).
        if (language != UdfLanguage.SQL && !Volatility.IMMUTABLE.name().equals(function.getVolatility())) {
            CheckpointDebt.owe();
        }
        if (language == UdfLanguage.JAVASCRIPT && !Volatility.IMMUTABLE.name().equals(function.getVolatility())) {
            // A subquery that calls a JavaScript function not declared IMMUTABLE is evaluated again for each outer
            // row however uncorrelated it is, the function's state kept; a Python function's call is evaluated
            // once (live-verified).
            visitor.drawnAfresh();
        }

        if (language == UdfLanguage.JAVA) {
            return evaluateJavaFunction(function, args);
        } else if (language == UdfLanguage.SQL) {
            return evaluateSqlFunction(function, args, given, decfloatsConverted(function, argTypes));
        } else if (language == UdfLanguage.JAVASCRIPT || language == UdfLanguage.PYTHON
                || language == UdfLanguage.SCALA) {
            // A handler loaded from IMPORTS finds a file an internal named stage keeps where the engine keeps it.
            final ImportLocalizer outer = JarHandlerLoader.localizeWith(queryExecutor);
            final Object guestResult;
            try {
                guestResult = UdfRuntimes.require(language).executeFunction(function, args,
                    catalog, queryExecutor != null ? queryExecutor.getS3PathResolver() : null);
            } finally {
                JarHandlerLoader.restore(outer);
            }
            return GuestResults.declared(function, guestResult);
        } else {
            throw new RuntimeException("Unsupported function language: " + language);
        }
    }


    /**
     * Whether an argument reaches its parameter as the number it already is, with no conversion and so
     * no rounding to the parameter's declared scale.
     *
     * @param paramType the parameter's declared type
     * @param value     the argument's value
     * @return whether the value passes through untouched
     */
    private static boolean keepsItsOwnNumber(final DataType paramType, final Object value) {
        return paramType instanceof NumericType && !NumericType.isApproximate(paramType)
            && (value instanceof BigDecimal || value instanceof Long || value instanceof Integer
                || value instanceof Short || value instanceof Byte || value instanceof BigInteger);
    }

    /** Whether a language's handler is given its arguments as its parameters' SQL types. */
    private static boolean handlerTakesSqlTypes(final UdfLanguage language) {
        return language == UdfLanguage.JAVASCRIPT || language == UdfLanguage.JAVA || language == UdfLanguage.SCALA
            || language == UdfLanguage.PYTHON;
    }


    /**
     * A SQL function's body, run where the function lives: the names it writes without a schema are looked for
     * in the function's own schema — then along the search path from there — whatever schema the calling
     * session is in, and CURRENT_SCHEMA() inside it is the function's (live-verified).
     */
    private Object evaluateSqlFunction(final Function function, final List<Object> args, final List<Object> given,
                                       final List<DataType> argTypes) {
        // A function met again while its own body is evaluated is a cycle no CREATE refused (see SqlFunctionCycle).
        SqlFunctionCycle.enter(function);
        try {
            final QueryExecutor queryExecutor = visitor.getQueryExecutor();
            if (queryExecutor == null) {
                return evaluateSqlFunctionBody(function, args, given, argTypes);
            }
            return queryExecutor.inRoutineHome(function.getHome(), function.getOwner(), new RoutineBody() {
                @Override
                public Object run() {
                    return evaluateSqlFunctionBody(function, args, given, argTypes);
                }
            });
        } finally {
            SqlFunctionCycle.exit(function);
        }
    }

    private Object evaluateSqlFunctionBody(final Function function, final List<Object> args,
                                           final List<Object> given, final List<DataType> argTypes) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        // A body that closes its own frame before a semicolon runs what the frame holds (see SqlUdfBodyFrame).
        final String body = SqlUdfBodyFrame.executableBody(function.getBody());
        if (body == null || body.isEmpty()) return null;

        // Bind parameter names to argument values
        final List<Parameter> params = function.getParameters();

        // If the body is a query statement (SELECT / WITH … SELECT), execute it via queryExecutor; a bare
        // scalar expression body falls through to expression evaluation. Decided by parsing, not a prefix.
        final String trimmedBody = body.trim();
        if (queryExecutor != null && queryExecutor.isQueryStatement(trimmedBody)) {
            // Substitute parameter placeholders
            final String sql = substituteParams(trimmedBody, params, args, given, argTypes);
            final List<ResultSet> results;
            // A refusal the body earns is placed in the body as the account frames it, inside parentheses: a
            // windowless QUALIFY at body position 16 is refused at 17 (live-verified).
            final SourcePosition displaced = ExpressionSource.begin(new SourcePosition(1, 1));
            try {
                // The body is part of the calling query, not a statement of its own: Snowflake inlines it, so
                // it takes no query ID and adds nothing to QUERY_HISTORY, however many rows call it.
                results = queryExecutor.executeUnrecorded(sql);
            } catch (final Exception e) {
                rethrowDeferredConversion(args, e);
                if (e instanceof RuntimeException && SqlCompilationError.isCompilationError(e.getMessage())) {
                    // A name the body's query cannot resolve is the statement's own compilation error.
                    throw (RuntimeException) e;
                }
                if (cycleIn(e) != null) {
                    throw cycleIn(e);
                }
                if (privilegeRefusalIn(e) != null) {
                    // What neither the caller nor the function's owner may read is refused as the statement's own.
                    throw privilegeRefusalIn(e);
                }
                throw new RuntimeException("Error executing SQL function: " + describe(e), e);
            } finally {
                ExpressionSource.end(displaced);
            }
            if (results.isEmpty() || results.get(0).getRowCount() == 0) {
                return null;
            }
            if (results.get(0).getRowCount() > 1) {
                // The body is the scalar subquery the call inlines, and answers as one (live-verified).
                throw new RuntimeException("Single-row subquery returns more than one row.");
            }
            return results.get(0).getRows().get(0).getValue(0);
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
            // A block binds its parameters as variables when it starts, so it converts every argument then: an
            // exact number to its parameter's declared type too, 1.239 into a NUMBER(10,2) reading 1.24 and 1
            // reading 1.00 (live-verified), where an expression body keeps the caller's number.
            final List<Object> bound = new ArrayList<>(args);
            for (int i = 0; i < bound.size(); i++) {
                final Object arg = bound.get(i);
                if (arg instanceof DeferredConversion) {
                    throw ((DeferredConversion) arg).getFailure();
                }
                final DataType declared = i < params.size() ? params.get(i).getDataType() : null;
                if (arg instanceof Number && !(arg instanceof Double) && !(arg instanceof Float)
                        && declared instanceof NumericType && !NumericType.isApproximate(declared)
                        && !NumericType.isDecfloat(declared)) {
                    bound.set(i, ValueCaster.castValue(arg, SqlTypeNames.canonical(declared)));
                }
            }
            final Object returned = queryExecutor.executeScriptingFunctionBody(function, bound);
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
        final String exprBody = substituteParams(trimmedBody, params, args, given, argTypes);

        // Bind all parameters into a single multi-column table row
        if (!params.isEmpty() && queryExecutor != null) {
            final List<TableColumn> cols = new ArrayList<>();
            final List<Object> rowVals = new ArrayList<>();
            for (int i = 0; i < params.size() && i < args.size(); i++) {
                cols.add(new TableColumn(
                    params.get(i).getName(), params.get(i).getDataType(), true, null, false, false, false));
                rowVals.add(args.get(i) instanceof DeferredConversion
                    ? new DeferredFault(((DeferredConversion) args.get(i)).getFailure()) : args.get(i));
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
                rethrowDeferredConversion(args, e);
                throw inBody(e);
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
                throw inBody(e);
            }
        }
        return null;
    }

    /**
     * A failure of a function's expression body. A name the body cannot resolve is the statement's own
     * compilation error, as the account reports it; anything else says where it came from.
     */
    private RuntimeException inBody(final Exception failure) {
        if (failure instanceof RuntimeException && (SqlCompilationError.isCompilationError(failure.getMessage())
                || ReadOnlyRequest.isRefusal(failure.getMessage()))) {
            return (RuntimeException) failure;
        }
        if (cycleIn(failure) != null) {
            return cycleIn(failure);
        }
        if (privilegeRefusalIn(failure) != null) {
            return privilegeRefusalIn(failure);
        }
        return new RuntimeException("Error in SQL function body: " + describe(failure), failure);
    }

    /** The privilege refusal a failure is or was raised for, however the layers between wrapped it, or null. */
    private static PrivilegeRefusal privilegeRefusalIn(final Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof PrivilegeRefusal) {
                return (PrivilegeRefusal) cause;
            }
            if (cause.getCause() == cause) {
                return null;
            }
        }
        return null;
    }

    /** The cycle failure a failure is or was raised for, however the layers between wrapped it, or null. */
    private static SqlFunctionCycleFailure cycleIn(final Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SqlFunctionCycleFailure) {
                return (SqlFunctionCycleFailure) cause;
            }
            if (cause.getCause() == cause) {
                return null;
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
                + returnType.getName().toUpperCase(Locale.ROOT));
        }
    }

    /**
     * Raise a deferred argument's own refusal when the body failed by reading that argument: the cast the body
     * read it through refuses in the same words, and the account answers with that sentence alone rather than
     * inside the function-body wrapper.
     */
    private static void rethrowDeferredConversion(final List<Object> args, final Exception failure) {
        final String message = failure.getMessage();
        for (final Object arg : args) {
            if (arg instanceof DeferredConversion && message != null
                    && message.equals(((DeferredConversion) arg).getFailure().getMessage())) {
                throw ((DeferredConversion) arg).getFailure();
            }
        }
    }

    /** The exception's message, or its class name when the message is null (an NPE's message usually is) —
     *  so a wrapped failure never surfaces as the bare text "null". */
    private String describe(final Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    /** Substitute each reference to a parameter in {@code body} with its argument written as SQL text that
     *  answers the value again, of the parameter's type (see {@link SqlArgumentText}): every parameter in one
     *  pass, and only where the body reads a value (see {@link ParameterReferences}). */
    private String substituteParams(final String body, final List<Parameter> params, final List<Object> args,
                                    final List<Object> given, final List<DataType> argTypes) {
        final List<String> names = new ArrayList<>();
        final List<String> bound = new ArrayList<>();
        for (int i = 0; i < params.size() && i < args.size(); i++) {
            final Parameter param = params.get(i);
            final Object argVal = args.get(i);
            final String argStr;
            if (argVal instanceof DeferredConversion) {
                // The argument the caller passed, converted where the body reads it.
                final DeferredConversion deferred = (DeferredConversion) argVal;
                argStr = "CAST(" + SqlArgumentText.ofValue(deferred.getRaw()) + " AS "
                    + SqlTypeNames.canonical(deferred.getType()) + ")";
            } else if (argVal != null && param.getDataType() instanceof VectorType) {
                // A VECTOR parameter substitutes as a TYPED vector literal, like the temporals above:
                // the vector functions' argument rules are compile-time and read the STATIC type, so a
                // bare string body would fail "Invalid argument types … (VARCHAR(13))" where Snowflake
                // evaluates the body fine (live: a UDF whose body is
                // VECTOR_L2_DISTANCE(a, b) over two VECTOR(FLOAT,3) parameters returns 5.196152422706632).
                // The text goes through PARSE_JSON first: a VARIANT converts to a vector, where a text cast
                // straight to one is refused ("Unsupported data type 'TEXT'.").
                argStr = "PARSE_JSON(" + SqlStringLiterals.encode(argVal.toString()) + ")::"
                    + param.getDataType().getName();
            } else {
                // The value itself, of its own type and at its full precision (see SqlArgumentText). An
                // argument the call converted is the parameter's type through and through.
                argStr = SqlArgumentText.forParameter(argVal, param.getDataType(),
                    i >= given.size() || given.get(i) != argVal,
                    argTypes != null && i < argTypes.size() ? argTypes.get(i) : null);
            }
            names.add(param.getName());
            bound.add(argStr);
        }
        return ParameterReferences.bound(body, names, bound);
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

        // The handler class comes from the inline body when one is written and holds it, else from the IMPORTS
        // jar(s): a body may be written beside imports it does not use (live-verified).
        final String sourceCode = function.getBody();
        final boolean inline = sourceCode != null && !sourceCode.trim().isEmpty();
        if (!inline && function.getImports().isEmpty()) {
            throw new RuntimeException("Java function must specify an inline body (AS ...) or IMPORTS");
        }
        Class<?> fromBody = null;
        if (inline) {
            try {
                fromBody = handlerClass(function, sourceCode, className, importedJars(function, queryExecutor));
            } catch (final RuntimeException notInTheBody) {
                if (function.getImports().isEmpty()) {
                    throw notInTheBody;
                }
            }
        }
        final String where = " in function " + function.getName() + " with handler " + handler;
        final Class<?> compiledClass;
        try {
            compiledClass = fromBody != null ? fromBody
                : importedClass(function, className, queryExecutor != null
                        ? queryExecutor.localImports(function.getImports()) : function.getImports(),
                    queryExecutor != null ? queryExecutor.getS3PathResolver() : null);
        } catch (final HandlerClassNotFound missing) {
            // A class the imports no longer hold is refused as the account's class loader refuses one, as the
            // function's CREATE would refuse it now (live-verified).
            throw new RuntimeException(JavaHandlerFailure.missingClass(className,
                JavaRuntimeLevel.declared(function.getRuntimeVersion()), where), missing);
        }

        final Method found = javaCompiler.handlerMethod(compiledClass, methodName, args.size());
        // No one public method of that name and argument count - the function's imports changed since it was
        // created - is refused as its CREATE would refuse it now (live-verified).
        final Method method = found != null ? found
            : JavaHandlerMethods.resolve(compiledClass, methodName, args.size(), function.getName(), handler);
        // Each argument, already of its parameter's SQL type, becomes the Java type the handler declares.
        final Object[] javaArgs = JavaHandlerArguments.of(method.getParameterTypes(), function.getParameters(), args,
            where);
        final Object receiver = Modifier.isStatic(method.getModifiers()) ? null
            : handlerInstance(function, compiledClass, where);
        final Object answered = javaCompiler.invokeHandler(compiledClass, method, receiver, javaArgs, where);
        // What the handler hands back becomes the declared RETURNS type's value, as a procedure's does: bytes a
        // BINARY, a String[] an ARRAY, a Map an OBJECT, a java.sql value a DATE, a TIME or a timestamp, a
        // BigDecimal the declared scale's, and a text read as the type reads one.
        final Object converted = HandlerResults.fromJava(answered, function.getReturnType(), function.getName(),
            handler);
        // A value of the engine's own - bytes, a semi-structured value, a date or a time - is the answer as it is.
        return converted instanceof BinaryValue || converted instanceof VariantValue
            || converted instanceof TemporalAccessor ? converted : convertJavaResultToSql(converted);
    }

    /**
     * The class an inline body's handler runs in: loaded for the statement that is running and the place that
     * calls the function, so what its static fields hold is there for the statement's later rows and for
     * nothing else - not for a later statement, nor for another call of the function in the same statement
     * (live-verified). Outside a statement the one class every such caller shares.
     */
    private static Class<?> handlerClass(final Function function, final String sourceCode, final String className,
                                         final List<File> jars) {
        final int release = JavaRuntimeLevel.of(function.getRuntimeVersion());
        final String where = " in function " + function.getName() + " with handler " + function.getHandler();
        final AutoCloseable kept = StatementHandlerStates.state(function, JAVA_HANDLER_CLASS,
            new HandlerStateFactory() {
                @Override
                public AutoCloseable create() {
                    return new JavaHandlerClass(javaCompiler.load(sourceCode, className, null, release, where, true,
                        jars));
                }
            });
        return kept != null ? ((JavaHandlerClass) kept).handled()
            : javaCompiler.compile(sourceCode, className, release, where, jars);
    }

    /**
     * The jars a function's IMPORTS name, on the class path its inline body compiles against and beside the class it
     * runs (live-verified); none without imports.
     */
    private List<File> importedJars(final Function function, final QueryExecutor queryExecutor) {
        if (function.getImports().isEmpty()) {
            return Collections.emptyList();
        }
        return JarHandlerLoader.localJars(queryExecutor != null ? queryExecutor.localImports(function.getImports())
            : function.getImports(), catalog, queryExecutor != null ? queryExecutor.getS3PathResolver() : null);
    }

    /**
     * The class an IMPORTS jar holds for the handler: loaded anew for the statement that is running and the place
     * that calls the function, as an inline body's is, so what the jar's static fields hold is there for the
     * statement's later rows and for nothing else (live-verified). Outside a statement the one class every such
     * caller shares.
     */
    private Class<?> importedClass(final Function function, final String className, final List<String> imports,
                                   final S3PathResolver s3Resolver) {
        final AutoCloseable kept = StatementHandlerStates.state(function, JAR_HANDLER_CLASS,
            new HandlerStateFactory() {
                @Override
                public AutoCloseable create() {
                    return new JavaHandlerClass(JarHandlerLoader.loadAnew(imports, className, catalog, s3Resolver));
                }
            });
        return kept != null ? ((JavaHandlerClass) kept).handled()
            : JarHandlerLoader.load(imports, className, catalog, s3Resolver);
    }

    /**
     * The instance a handler method that is not static is called on: one for the statement that is running and
     * the place that calls the function, so what it holds is there for the statement's later rows and for
     * nothing else (live-verified); outside a statement, one for the call.
     */
    private static Object handlerInstance(final Function function, final Class<?> handled, final String where) {
        final AutoCloseable kept = StatementHandlerStates.state(function, handled, new HandlerStateFactory() {
            @Override
            public AutoCloseable create() {
                return new JavaHandlerInstance(JavaHandlerInstances.create(handled, where));
            }
        });
        return kept != null ? ((JavaHandlerInstance) kept).instance() : JavaHandlerInstances.create(handled, where);
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
