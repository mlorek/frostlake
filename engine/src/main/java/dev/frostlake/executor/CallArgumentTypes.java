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

package dev.frostlake.executor;

import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredTypes;
import dev.frostlake.types.VariantType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A CALL's arguments against its procedure's parameter types, judged while the statement compiles and
 * before any argument is evaluated. Live refuses a pair no implicit conversion carries, anchored nowhere:
 *
 * <pre>
 *   CALL p(5, '2020-01-15 10:00:00 +0530'::TIMESTAMP_TZ)       -- p(a NUMBER, b TIMESTAMP_LTZ)
 *       SQL compilation error: error line 0 at position -1
 *       Invalid argument types for function 'P': (NUMBER(1,0), TIMESTAMP_TZ(9))
 * </pre>
 *
 * <p>The sentence lists EVERY argument's type, an untyped NULL as {@code NULL}, and a CALL that leaves a
 * parameter without a default unfilled is the same sentence over the arguments it gave. Too many is
 * "too many arguments for function [P(5, CURRENT_DATE(), 7)] expected 2, got 3", and an all-named CALL
 * whose types do not fit is "named arguments [A, B] do not match any signature for function P", the
 * names in declaration order.
 *
 * <p>The pairs refused are the measured ones below. Every other pair binds, as live binds it, and is
 * converted as the value arrives (a text into a NUMBER is parsed then). A VARIANT binds to anything and
 * is cast at run time, an untyped NULL binds to anything, and an argument this pass cannot type refuses
 * nothing:
 *
 * <pre>
 *   parameter          refuses
 *   VARCHAR            OBJECT, ARRAY, BINARY
 *   NUMBER             BOOLEAN, DATE, TIME, TIMESTAMP_*, BINARY, OBJECT
 *   FLOAT              BOOLEAN, DATE, TIMESTAMP_*
 *   BOOLEAN            DATE, TIME, TIMESTAMP_*
 *   DATE               NUMBER, FLOAT, BOOLEAN, TIME
 *   TIMESTAMP_NTZ/LTZ  TIMESTAMP_TZ, NUMBER, FLOAT, BOOLEAN
 *   TIMESTAMP_TZ       NUMBER, FLOAT, BOOLEAN
 *   TIME               NUMBER, DATE
 *   BINARY             VARCHAR, NUMBER
 *   VARIANT            VARCHAR, DATE
 *   OBJECT             NUMBER, VARCHAR, ARRAY
 *   ARRAY              NUMBER, VARCHAR, OBJECT
 * </pre>
 *
 * Live-verified, every cell. Choosing among a procedure's overloads is not modelled here, and neither is a
 * CALL issued from a block or from another procedure's body: its :binds reach the engine already substituted
 * into the text as literals, so a variable declared OBJECT arrives as a text and its declared type is lost.
 * Only a CALL written at the top level is judged.
 */
final class CallArgumentTypes {

    /** How many CALLs this thread is inside of; the one being judged counts itself. */
    private static final ThreadLocal<int[]> DEPTH = new ThreadLocal<int[]>() {
        @Override
        protected int[] initialValue() {
            return new int[1];
        }
    };

    /** A parameter family to the argument families it refuses. */
    private static final Map<String, Set<String>> REFUSED = new HashMap<String, Set<String>>();

    static {
        refuse("TEXT", "OBJECT", "ARRAY", "BINARY");
        refuse("FIXED", "BOOLEAN", "DATE", "TIME", "NTZ", "LTZ", "TZ", "BINARY", "OBJECT");
        refuse("FLOAT", "BOOLEAN", "DATE", "NTZ", "LTZ", "TZ");
        refuse("BOOLEAN", "DATE", "TIME", "NTZ", "LTZ", "TZ");
        refuse("DATE", "FIXED", "FLOAT", "BOOLEAN", "TIME");
        refuse("NTZ", "TZ", "FIXED", "FLOAT", "BOOLEAN");
        refuse("LTZ", "TZ", "FIXED", "FLOAT", "BOOLEAN");
        refuse("TZ", "FIXED", "FLOAT", "BOOLEAN");
        refuse("TIME", "FIXED", "DATE");
        refuse("BINARY", "TEXT", "FIXED");
        refuse("VARIANT", "TEXT", "DATE");
        refuse("OBJECT", "FIXED", "TEXT", "ARRAY");
        refuse("ARRAY", "FIXED", "TEXT", "OBJECT");
    }

    private CallArgumentTypes() {
    }

    /** A CALL begins on this thread. */
    static void enterCall() {
        DEPTH.get()[0]++;
    }

    /** The CALL begun last on this thread ends. */
    static void exitCall() {
        DEPTH.get()[0]--;
    }

    /** Whether the CALL being judged runs inside another one's body. */
    static boolean isNested() {
        return DEPTH.get()[0] > 1;
    }

    private static void refuse(final String parameter, final String... arguments) {
        final Set<String> refused = new HashSet<String>();
        for (final String argument : arguments) {
            refused.add(argument);
        }
        REFUSED.put(parameter, refused);
    }

    /**
     * Judge a CALL's arguments.
     *
     * @param procName  the procedure's name as the sentences print it
     * @param params    its parameters, in declaration order
     * @param arguments the CALL's arguments, all positional or all named
     * @param named     whether they are named
     * @param typer     the evaluator whose static typing reads each argument
     */
    static void check(final String procName, final List<Parameter> params,
                      final List<FrostlakeParser.CallArgumentContext> arguments, final boolean named,
                      final ExpressionEvaluator typer) {
        if (named) {
            checkNamed(procName, params, arguments, typer);
            return;
        }
        if (arguments.size() > params.size()) {
            final List<String> written = new ArrayList<String>();
            for (final FrostlakeParser.CallArgumentContext argument : arguments) {
                written.add(ParseTreeText.getOriginalText(argument));
            }
            throw new RuntimeException(SqlCompilationError.at(0, -1, "too many arguments for function ["
                + procName + "(" + String.join(", ", written) + ")] expected " + params.size() + ", got "
                + arguments.size()));
        }
        final List<String> spelled = new ArrayList<String>();
        boolean refused = false;
        for (int i = 0; i < arguments.size(); i++) {
            final FrostlakeParser.ExpressionContext value = arguments.get(i).expression();
            if (value == null) {
                return;   // a bare subquery, refused on its own
            }
            if (isUntypedNull(value)) {
                spelled.add("NULL");
                continue;
            }
            final DataType type = staticType(value, typer);
            if (type == null) {
                return;   // an argument this pass cannot type refuses nothing
            }
            spelled.add(SqlTypeNames.refusalSpelling(type));
            refused |= refuses(params.get(i).getDataType(), type);
        }
        for (int i = arguments.size(); i < params.size(); i++) {
            refused |= !params.get(i).hasDefault();
        }
        if (refused) {
            throw new RuntimeException(SqlCompilationError.at(0, -1, "Invalid argument types for function '"
                + procName + "': (" + String.join(", ", spelled) + ")"));
        }
    }

    /** An all-named CALL: each named parameter's argument against its type, named in declaration order. */
    private static void checkNamed(final String procName, final List<Parameter> params,
                                   final List<FrostlakeParser.CallArgumentContext> arguments,
                                   final ExpressionEvaluator typer) {
        final List<String> names = new ArrayList<String>();
        boolean refused = false;
        for (final Parameter param : params) {
            final FrostlakeParser.ExpressionContext value = namedValue(arguments, param.getName());
            if (value == null) {
                continue;
            }
            names.add(param.getName().toUpperCase(Locale.ROOT));
            if (isUntypedNull(value)) {
                continue;
            }
            final DataType type = staticType(value, typer);
            if (type == null) {
                return;
            }
            refused |= refuses(param.getDataType(), type);
        }
        if (refused) {
            throw new RuntimeException(SqlCompilationError.at(0, -1, "named arguments ["
                + String.join(", ", names) + "] do not match any signature for function " + procName));
        }
    }

    /** The expression a named argument gives {@code name}, matched as the CALL binds it; null when none. */
    private static FrostlakeParser.ExpressionContext namedValue(
            final List<FrostlakeParser.CallArgumentContext> arguments, final String name) {
        for (final FrostlakeParser.CallArgumentContext argument : arguments) {
            final FrostlakeParser.NamedArgumentContext named = argument.namedArgument();
            if (named != null && named.identifier() != null
                    && named.identifier().getText().equalsIgnoreCase(name)) {
                return named.expression();
            }
        }
        return null;
    }

    /**
     * The overload a positional CALL of an overloaded procedure runs (live-verified over fifteen pairs and
     * a triple). The overloads that take the argument COUNT are kept — a count none takes is "too many
     * arguments … expected N, got M", N the widest signature. Of those, each argument ranks a parameter:
     * its own family first; then any family that takes it, in the order of the internal type names
     * (ARRAY, BINARY, BOOLEAN, DATE, FIXED, OBJECT, REAL, TEXT, TIME, TIMESTAMP_LTZ, TIMESTAMP_NTZ,
     * TIMESTAMP_TZ, VARIANT) — so an untyped NULL picks the first name, a NUMBER(38,0) over DATE —
     * except that a NUMBER reaches TEXT only when nothing else takes it, and a FLOAT or a text reaches
     * FIXED only when nothing else takes it (a FLOAT picks VARCHAR over NUMBER, a '5' picks FLOAT over
     * NUMBER). An overload no argument can reach is the argument-type refusal.
     *
     * @param procName  the procedure's name as the sentences print it
     * @param overloads the procedure's overloads
     * @param arguments the CALL's positional arguments
     * @param typer     the evaluator whose static typing reads each argument
     * @return the overload to run
     */
    static Procedure chooseOverload(final String procName, final List<Procedure> overloads,
                                    final List<FrostlakeParser.CallArgumentContext> arguments,
                                    final ExpressionEvaluator typer) {
        final List<Procedure> counted = new ArrayList<Procedure>();
        int widest = 0;
        for (final Procedure overload : overloads) {
            final List<Parameter> params = overload.getParameters();
            widest = Math.max(widest, params.size());
            if (arguments.size() <= params.size() && requiredCount(params) <= arguments.size()) {
                counted.add(overload);
            }
        }
        if (counted.isEmpty()) {
            final List<String> written = new ArrayList<String>();
            for (final FrostlakeParser.CallArgumentContext argument : arguments) {
                written.add(ParseTreeText.getOriginalText(argument));
            }
            throw new RuntimeException(SqlCompilationError.at(0, -1, "too many arguments for function ["
                + procName + "(" + String.join(", ", written) + ")] expected " + widest + ", got "
                + arguments.size()));
        }
        final List<String> argumentFamilies = new ArrayList<String>();
        final List<String> spelled = new ArrayList<String>();
        for (final FrostlakeParser.CallArgumentContext argument : arguments) {
            final FrostlakeParser.ExpressionContext value = argument.expression();
            if (value == null || isUntypedNull(value)) {
                argumentFamilies.add(null);
                spelled.add("NULL");
                continue;
            }
            final DataType type = staticType(value, typer);
            argumentFamilies.add(family(type));
            spelled.add(type == null ? "NULL" : SqlTypeNames.refusalSpelling(type));
        }
        Procedure best = null;
        String bestKey = null;
        for (final Procedure overload : counted) {
            final String key = rankKey(overload.getParameters(), argumentFamilies);
            if (key != null && (bestKey == null || key.compareTo(bestKey) < 0)) {
                best = overload;
                bestKey = key;
            }
        }
        if (best == null) {
            throw new RuntimeException(SqlCompilationError.at(0, -1, "Invalid argument types for function '"
                + procName + "': (" + String.join(", ", spelled) + ")"));
        }
        return best;
    }

    /** How many parameters a CALL must supply: those before the last one without a DEFAULT. */
    private static int requiredCount(final List<Parameter> params) {
        int required = 0;
        for (int i = 0; i < params.size(); i++) {
            if (!params.get(i).hasDefault()) {
                required = i + 1;
            }
        }
        return required;
    }

    /**
     * An overload's rank for these arguments, lowest first, as one comparable text — each argument's
     * tier and the parameter's internal type name in turn — or null when an argument cannot reach it.
     */
    private static String rankKey(final List<Parameter> params, final List<String> argumentFamilies) {
        final StringBuilder key = new StringBuilder();
        for (int i = 0; i < argumentFamilies.size(); i++) {
            final String parameter = family(params.get(i).getDataType());
            final String argument = argumentFamilies.get(i);
            if (parameter == null) {
                key.append("3~");
                continue;
            }
            if (argument != null) {
                final Set<String> refused = REFUSED.get(parameter);
                if (refused != null && refused.contains(argument)) {
                    return null;
                }
            }
            final int tier;
            if (argument != null && argument.equals(parameter)) {
                tier = 0;
            } else if ("FIXED".equals(argument) && "TEXT".equals(parameter)
                    || ("FLOAT".equals(argument) || "TEXT".equals(argument)) && "FIXED".equals(parameter)) {
                tier = 2;
            } else {
                tier = 1;
            }
            key.append(tier).append(internalName(parameter)).append('~');
        }
        return key.toString();
    }

    /** The internal type name a family sorts by. */
    private static String internalName(final String family) {
        switch (family) {
            case "FLOAT":
                return "REAL";
            case "NTZ":
                return "TIMESTAMP_NTZ";
            case "LTZ":
                return "TIMESTAMP_LTZ";
            case "TZ":
                return "TIMESTAMP_TZ";
            default:
                return family;
        }
    }

    /** Whether live refuses to bind an argument of type {@code argument} to a parameter of {@code parameter}. */
    private static boolean refuses(final DataType parameter, final DataType argument) {
        final String parameterFamily = family(parameter);
        final String argumentFamily = family(argument);
        if (parameterFamily == null || argumentFamily == null) {
            return false;
        }
        final Set<String> refused = REFUSED.get(parameterFamily);
        return refused != null && refused.contains(argumentFamily);
    }

    /** The family a type binds as, or null for one no pair has been measured for. */
    private static String family(final DataType type) {
        if (type == null || StructuredTypes.isStructured(type)) {
            return null;
        }
        if (type instanceof VariantType) {
            return "VARIANT";
        }
        if (type instanceof ObjectType) {
            return "OBJECT";
        }
        if (type instanceof ArrayType) {
            return "ARRAY";
        }
        if (type instanceof BinaryType) {
            return "BINARY";
        }
        if (type instanceof BooleanType) {
            return "BOOLEAN";
        }
        if (type instanceof StringType) {
            return "TEXT";
        }
        if (type instanceof NumericType) {
            return NumericType.isApproximate(type) ? "FLOAT" : "FIXED";
        }
        if (type instanceof DateTimeType && type.getName() != null) {
            final String name = type.getName().toUpperCase(Locale.ROOT);
            if ("DATE".equals(name) || "TIME".equals(name)) {
                return name;
            }
            if (name.contains("LTZ")) {
                return "LTZ";
            }
            return name.contains("TZ") && !name.contains("NTZ") ? "TZ" : "NTZ";
        }
        return null;
    }

    private static boolean isUntypedNull(final FrostlakeParser.ExpressionContext value) {
        return value instanceof FrostlakeParser.LiteralExprContext
            && ((FrostlakeParser.LiteralExprContext) value).literal().NULL() != null;
    }

    /** An argument's static type, or null when it cannot be read. */
    private static DataType staticType(final FrostlakeParser.ExpressionContext value,
                                       final ExpressionEvaluator typer) {
        try {
            return typer.inferStaticType(ExpressionEvaluator.parse(ParseTreeText.getOriginalText(value)));
        } catch (final RuntimeException untyped) {
            return null;
        }
    }
}
