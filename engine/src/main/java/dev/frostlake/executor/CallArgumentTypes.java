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

import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.BindVariableExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.PredicateExpressions;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.expressions.UntypedNullFold;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
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
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.CodePointText;
import dev.frostlake.values.VariantValue;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import java.math.BigDecimal;
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
 *   NUMBER             BOOLEAN, DATE, TIME, TIMESTAMP_*, BINARY, OBJECT, ARRAY
 *   FLOAT              BOOLEAN, DATE, TIME, TIMESTAMP_*, BINARY, OBJECT, ARRAY
 *   BOOLEAN            DATE, TIME, TIMESTAMP_*, BINARY, OBJECT, ARRAY
 *   DATE               NUMBER, FLOAT, BOOLEAN, TIME, BINARY, OBJECT, ARRAY
 *   TIMESTAMP_NTZ/LTZ  TIMESTAMP_TZ, NUMBER, FLOAT, BOOLEAN, BINARY, OBJECT, ARRAY
 *   TIMESTAMP_TZ       NUMBER, FLOAT, BOOLEAN, BINARY, OBJECT, ARRAY
 *   TIME               NUMBER, FLOAT, BOOLEAN, DATE, BINARY, OBJECT, ARRAY
 *   BINARY             VARCHAR, NUMBER, FLOAT, BOOLEAN, DATE, TIME, TIMESTAMP_*, OBJECT, ARRAY
 *   VARIANT            VARCHAR, DATE, TIME, TIMESTAMP_*, BINARY
 *   OBJECT             NUMBER, FLOAT, VARCHAR, BOOLEAN, DATE, TIME, TIMESTAMP_*, BINARY, ARRAY
 *   ARRAY              NUMBER, FLOAT, VARCHAR, BOOLEAN, DATE, TIME, TIMESTAMP_*, OBJECT, BINARY
 * </pre>
 *
 * <p>A TIME into a timestamp parameter has a sentence of its own, {@code incompatible types: [TIME(9)] and
 * [TIMESTAMP_NTZ(9)]}, and a PREDICATE — a comparison, an IS NULL, an IN and the like — is refused by every
 * parameter but a BOOLEAN one as a BOOLEAN, a text and a VARIANT parameter included, which take a BOOLEAN value
 * ({@code TRUE}, {@code NOT TRUE}, {@code TRUE AND FALSE}).
 *
 * Live-verified, every cell. Choosing among a procedure's overloads is not modelled here. A CALL a block or
 * another procedure's body runs is judged the same way, each {@code :name} by the type its variable is declared
 * with, holding NULL or a value: {@code DECLARE d DATE; … CALL p(:d)} into a NUMBER parameter is "Invalid argument
 * types for function 'P': (DATE)", and a NUMBER variable into a DATE parameter "(NUMBER(38,0))". A text or a
 * binary variable that holds a value is spelled at the value's own width — a VARCHAR(10) holding 'abc' is
 * VARCHAR(3) — and one that holds NULL at its declared width.
 */
final class CallArgumentTypes {

    /** A parameter family to the argument families it refuses. */
    private static final Map<String, Set<String>> REFUSED = new HashMap<String, Set<String>>();

    static {
        refuse("TEXT", "OBJECT", "ARRAY", "BINARY");
        refuse("FIXED", "BOOLEAN", "DATE", "TIME", "NTZ", "LTZ", "TZ", "BINARY", "OBJECT", "ARRAY");
        refuse("FLOAT", "BOOLEAN", "DATE", "TIME", "NTZ", "LTZ", "TZ", "BINARY", "OBJECT", "ARRAY");
        refuse("BOOLEAN", "DATE", "TIME", "NTZ", "LTZ", "TZ", "BINARY", "OBJECT", "ARRAY");
        refuse("DATE", "FIXED", "FLOAT", "DECFLOAT", "BOOLEAN", "TIME", "BINARY", "OBJECT", "ARRAY");
        refuse("NTZ", "TZ", "FIXED", "FLOAT", "DECFLOAT", "BOOLEAN", "BINARY", "OBJECT", "ARRAY");
        refuse("LTZ", "TZ", "FIXED", "FLOAT", "DECFLOAT", "BOOLEAN", "BINARY", "OBJECT", "ARRAY");
        refuse("TZ", "FIXED", "FLOAT", "DECFLOAT", "BOOLEAN", "BINARY", "OBJECT", "ARRAY");
        refuse("TIME", "FIXED", "FLOAT", "DECFLOAT", "BOOLEAN", "DATE", "BINARY", "OBJECT", "ARRAY");
        refuse("BINARY", "TEXT", "FIXED", "FLOAT", "DECFLOAT", "BOOLEAN", "DATE", "TIME", "NTZ", "LTZ", "TZ", "OBJECT",
            "ARRAY");
        // A DECFLOAT parameter takes a DECFLOAT and nothing else (live-verified: CALL p(2) is refused).
        refuse("DECFLOAT", "TEXT", "FIXED", "FLOAT", "BOOLEAN", "DATE", "TIME", "NTZ", "LTZ", "TZ", "BINARY", "OBJECT",
            "ARRAY", "VARIANT");
        refuse("VARIANT", "TEXT", "DATE", "TIME", "NTZ", "LTZ", "TZ", "BINARY");
        refuse("OBJECT", "FIXED", "FLOAT", "DECFLOAT", "TEXT", "BOOLEAN", "DATE", "TIME", "NTZ", "LTZ", "TZ", "BINARY",
            "ARRAY");
        refuse("ARRAY", "FIXED", "FLOAT", "DECFLOAT", "TEXT", "BOOLEAN", "DATE", "TIME", "NTZ", "LTZ", "TZ", "OBJECT",
            "BINARY");
    }

    /** What a parameter of a type no pair has been measured for weighs: more than any conversion. */
    private static final int UNMEASURED_PARAMETER = 16;

    /**
     * What a conversion weighs that is not refused and that no row of {@link OverloadConversionCosts} names: as
     * much as a text read as a FLOAT, lighter than the conversions an argument reaches last.
     */
    private static final int UNNAMED_CONVERSION = 2;

    private CallArgumentTypes() {
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
                written.add(echoed(argument, typer));
            }
            throw new RuntimeException(SqlCompilationError.at(0, -1, "too many arguments for function ["
                + procName + "(" + String.join(", ", written) + ")] expected " + params.size() + ", got "
                + arguments.size()));
        }
        final List<String> spelled = new ArrayList<String>();
        boolean refused = false;
        String timeForTimestamp = null;
        for (int i = 0; i < arguments.size(); i++) {
            final ParserRuleContext value = positionalValue(arguments.get(i));
            if (value == null) {
                return;   // a bare subquery, refused on its own
            }
            if (isUntypedNull(value, typer)) {
                spelled.add("NULL");
                continue;
            }
            final DataType type = staticType(value, typer);
            if (type == null) {
                return;   // an argument this pass cannot type refuses nothing
            }
            if (timeForTimestamp == null && isTimeForTimestamp(params.get(i).getDataType(), type)) {
                timeForTimestamp = SqlCompilationError.of("incompatible types: [" + SqlTypeNames.refusalSpelling(type)
                    + "] and [" + SqlTypeNames.refusalSpelling(params.get(i).getDataType()) + "]");
            }
            spelled.add(SqlTypeNames.refusalSpelling(type));
            refused |= refuses(params.get(i).getDataType(), type) || refusesPredicate(params.get(i).getDataType(),
                value);
        }
        for (int i = arguments.size(); i < params.size(); i++) {
            refused |= !params.get(i).hasDefault();
        }
        if (refused) {
            throw new RuntimeException(SqlCompilationError.at(0, -1, "Invalid argument types for function '"
                + procName + "': (" + String.join(", ", spelled) + ")"));
        }
        if (timeForTimestamp != null) {
            throw new RuntimeException(timeForTimestamp);
        }
    }

    /**
     * Judge the arguments of a table procedure called as a FROM source, {@code TABLE(p(1, 'a'))}. A CALL converts
     * its arguments where it can; the FROM form matches them to the parameters' declared types as a table
     * function's are matched (see {@link TableFunctionSignatures}) — by family and exactly, an untyped NULL
     * matching nothing — and has one sentence for every misfit, too few and too many among them, placed at the
     * procedure's name: {@code Invalid argument types for function 'P': (NUMBER(1,0), VARCHAR(1))}, the name as the
     * call writes it; written with names it is {@code named arguments [X] do not match any signature for function
     * P} (live-verified):
     *
     * <pre>
     *   p(x NUMBER)          1, 20 digits, NULL::NUMBER      not '1', 1.5, a NUMBER(10,2), TRUE, NULL
     *   p(x NUMBER(5,2))     1.25, a NUMBER(38,2)            not 1, 1.2
     *   p(x VARCHAR(3))      a text of any width             not 1, TRUE
     *   p(x TIMESTAMP_NTZ)   a TIMESTAMP_NTZ(9)              not a TIMESTAMP_NTZ(3), a TIMESTAMP_LTZ
     * </pre>
     *
     * <p>A number written as a literal is matched at the scale its value needs: {@code 1.0} is an argument of a
     * NUMBER(38,0) parameter and {@code 1.250} of a NUMBER(5,2) one. An argument this pass cannot type refuses
     * nothing.
     *
     * @param procName the procedure's name as the sentences print it
     * @param params   its parameters, in declaration order
     * @param values   the arguments written by position, or null when they are written by name
     * @param named    the arguments written by name, or null when they are written by position
     * @param at       the procedure's name in the statement
     * @param typer    the evaluator whose static typing reads each argument
     */
    static void checkTableSource(final String procName, final List<Parameter> params,
                                 final List<? extends ParserRuleContext> values,
                                 final List<FrostlakeParser.NamedArgumentContext> named, final Token at,
                                 final ExpressionEvaluator typer) {
        if (values != null && values.size() > params.size() && declaresADefault(params)) {
            // Beside a parameter with a default, too many arguments are the CALL's to refuse (live-verified).
            return;
        }
        final List<String> spelled = new ArrayList<String>();
        final List<DataType> positionalTypes = new ArrayList<DataType>();
        final List<String> names = new ArrayList<String>();
        final List<String> writtenNames = new ArrayList<String>();
        final List<DataType> namedTypes = new ArrayList<DataType>();
        final List<Boolean> untypedNull = new ArrayList<Boolean>();
        if (values != null) {
            for (final ParserRuleContext value : values) {
                final boolean bare = isUntypedNull(value, typer);
                final DataType type = bare ? null : compiledType(value, typer);
                if (!bare && type == null) {
                    return;   // an argument this pass cannot type refuses nothing
                }
                spelled.add(bare ? "NULL" : SqlTypeNames.refusalSpelling(type));
                positionalTypes.add(atValueScale(value, type));
                untypedNull.add(Boolean.valueOf(bare));
            }
        }
        if (named != null) {
            for (final FrostlakeParser.NamedArgumentContext argument : named) {
                final FrostlakeParser.ExpressionContext value = argument.expression();
                final boolean bare = value != null && isUntypedNull(value, typer);
                names.add(SqlIdentifiers.canonical(argument.variableName()));
                writtenNames.add(argument.variableName().getText());
                namedTypes.add(value == null || bare ? null : atValueScale(value, staticType(value, typer)));
                untypedNull.add(Boolean.valueOf(bare));
            }
        }
        if (TableFunctionSignatures.fits(params, positionalTypes, names, namedTypes, untypedNull)) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
            names.isEmpty() ? "Invalid argument types for function '" + procName + "': (" + String.join(", ", spelled) + ")"
                : "named arguments [" + NamedArguments.listed(params, writtenNames)
                    + "] do not match any signature for function " + procName));
    }

    /** Whether a procedure declares a parameter with a default. */
    private static boolean declaresADefault(final List<Parameter> params) {
        for (final Parameter param : params) {
            if (param.hasDefault()) {
                return true;
            }
        }
        return false;
    }

    /** A number literal's type at the scale its value needs, {@code 1.250} as a NUMBER of scale 2; any other as is. */
    private static DataType atValueScale(final ParserRuleContext value, final DataType type) {
        if (!(type instanceof NumericType) || NumericType.isApproximate(type)
                || !(value instanceof FrostlakeParser.LiteralExprContext)) {
            return type;
        }
        final NumericType declared = (NumericType) type;
        final BigDecimal written;
        try {
            written = new BigDecimal(value.getText());
        } catch (final NumberFormatException unread) {
            return type;
        }
        final int needed = Math.max(0, written.stripTrailingZeros().scale());
        return needed >= declared.getScale() ? type
            : new NumericType("NUMBER", declared.getPrecision() - (declared.getScale() - needed), needed);
    }

    /**
     * Refuse an all-named CALL that names one argument twice, at the second writing — {@code duplicate named
     * argument A for function P} — or gives more arguments than its procedure has parameters, which is the
     * too-many-arguments refusal with each argument echoed by its value alone: {@code too many arguments for
     * function [P(1)] expected 0, got 1} (live-verified).
     *
     * @param procName  the procedure's name as the sentence prints it
     * @param params    its parameters, in declaration order
     * @param arguments the CALL's arguments
     */
    static void refuseNamedShape(final String procName, final List<Parameter> params,
                                 final List<FrostlakeParser.CallArgumentContext> arguments) {
        final List<String> seen = new ArrayList<String>();
        final List<String> values = new ArrayList<String>();
        for (final FrostlakeParser.CallArgumentContext argument : arguments) {
            if (argument.namedArgument() == null) {
                return;
            }
            final String name = SqlIdentifiers.canonical(argument.namedArgument().variableName());
            if (seen.contains(name)) {
                final Token at = argument.namedArgument().variableName().getStart();
                throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                    "duplicate named argument " + name + " for function " + procName));
            }
            seen.add(name);
            values.add(argument.namedArgument().expression() == null
                ? ParseTreeText.getOriginalText(argument.namedArgument())
                : ParseTreeText.getOriginalText(argument.namedArgument().expression()));
        }
        if (arguments.size() > params.size()) {
            throw new RuntimeException(SqlCompilationError.at(0, -1, "too many arguments for function ["
                + procName + "(" + String.join(", ", values) + ")] expected " + params.size() + ", got "
                + arguments.size()));
        }
    }

    /**
     * Refuse a CALL whose named arguments its procedure does not take — a name no parameter has, a quoted name of
     * another case, a parameter without a default left out — at any depth, as live refuses it while the CALL
     * compiles: {@code named arguments [X, Z] do not match any signature for function NP}, every name the CALL
     * writes (see {@link NamedArguments}).
     *
     * @param procName  the procedure's name as the sentence prints it
     * @param params    its parameters, in declaration order
     * @param arguments the CALL's arguments, all of them named
     */
    static void refuseMisnamed(final String procName, final List<Parameter> params,
                               final List<FrostlakeParser.CallArgumentContext> arguments) {
        final List<String> written = new ArrayList<String>();
        for (final FrostlakeParser.CallArgumentContext argument : arguments) {
            if (argument.namedArgument() == null) {
                return;
            }
            written.add(argument.namedArgument().variableName().getText());
        }
        if (written.isEmpty() || new HashSet<String>(written).size() != written.size()
                || NamedArguments.fit(params, written)) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.at(0, -1, "named arguments ["
            + NamedArguments.listed(params, written) + "] do not match any signature for function " + procName));
    }

    /**
     * Refuse a CALL one of whose named arguments is a parenthesized list, a ROW no parameter takes — at any depth,
     * as live refuses it while the CALL compiles: {@code named arguments [A, B] do not match any signature for
     * function PAB}, every name the CALL writes (see {@link NamedArguments}).
     *
     * @param procName  the procedure's name as the sentence prints it
     * @param params    its parameters, in declaration order
     * @param arguments the CALL's arguments
     */
    static void refuseRows(final String procName, final List<Parameter> params,
                           final List<FrostlakeParser.CallArgumentContext> arguments) {
        final List<String> written = new ArrayList<String>();
        boolean row = false;
        for (final FrostlakeParser.CallArgumentContext argument : arguments) {
            final FrostlakeParser.NamedArgumentContext named = argument.namedArgument();
            if (named != null) {
                written.add(named.variableName().getText());
                row = row || named.argumentRow() != null;
            }
        }
        if (!row) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.at(0, -1, "named arguments ["
            + NamedArguments.listed(params, written) + "] do not match any signature for function " + procName));
    }

    /**
     * An all-named CALL: each named parameter's argument against its type, every name the CALL writes listed. A
     * TIME given for a timestamp parameter has its own sentence when nothing else is refused, as by position.
     */
    private static void checkNamed(final String procName, final List<Parameter> params,
                                   final List<FrostlakeParser.CallArgumentContext> arguments,
                                   final ExpressionEvaluator typer) {
        final List<String> written = new ArrayList<String>();
        for (final FrostlakeParser.CallArgumentContext argument : arguments) {
            if (argument.namedArgument() != null) {
                written.add(argument.namedArgument().variableName().getText());
            }
        }
        boolean refused = false;
        String timeForTimestamp = null;
        for (final Parameter param : params) {
            final FrostlakeParser.ExpressionContext value = namedValue(arguments, param.getName());
            if (value == null) {
                continue;
            }
            if (isUntypedNull(value, typer)) {
                continue;
            }
            final DataType type = staticType(value, typer);
            if (type == null) {
                return;
            }
            if (timeForTimestamp == null && isTimeForTimestamp(param.getDataType(), type)) {
                timeForTimestamp = SqlCompilationError.of("incompatible types: [" + SqlTypeNames.refusalSpelling(type)
                    + "] and [" + SqlTypeNames.refusalSpelling(param.getDataType()) + "]");
            }
            refused |= refuses(param.getDataType(), type) || refusesPredicate(param.getDataType(), value);
        }
        if (refused) {
            throw new RuntimeException(SqlCompilationError.at(0, -1, "named arguments ["
                + NamedArguments.listed(params, written) + "] do not match any signature for function " + procName));
        }
        if (timeForTimestamp != null) {
            throw new RuntimeException(timeForTimestamp);
        }
    }

    /** The expression a named argument gives {@code name}, matched as the CALL binds it; null when none. */
    private static FrostlakeParser.ExpressionContext namedValue(
            final List<FrostlakeParser.CallArgumentContext> arguments, final String name) {
        for (final FrostlakeParser.CallArgumentContext argument : arguments) {
            final FrostlakeParser.NamedArgumentContext named = argument.namedArgument();
            if (named != null && named.variableName() != null
                    && named.variableName().getText().equalsIgnoreCase(name)) {
                return named.expression();
            }
        }
        return null;
    }

    /**
     * The overload a CALL of an overloaded procedure runs (live-verified over fifteen pairs and a triple of
     * one-argument overloads, and over pairs of two-argument ones). By position, the overloads that take the
     * argument COUNT are kept — a count past the widest signature is "too many arguments … expected N, got M", N
     * that signature's, and any other count none takes is the argument-type refusal. By name, the overloads that
     * declare every name written and are left no required parameter unfilled are kept, each value judged against
     * the parameter its name fills. Of those the CALL runs the one whose conversions weigh least over all its
     * arguments together (see {@link OverloadConversionCosts}): an argument's own family weighs nothing, an
     * untyped NULL weighs nothing anywhere, a NUMBER reaches TEXT and a FLOAT reaches FIXED only where nothing
     * lighter takes them. Overloads that weigh alike are told apart by how the CALL is written: by position the
     * first in the order of the internal type names runs (ARRAY, BINARY, BOOLEAN, DATE, FIXED, OBJECT, REAL,
     * TEXT, TIME, TIMESTAMP_LTZ, TIMESTAMP_NTZ, TIMESTAMP_TZ, VARIANT) — so an untyped NULL picks the first name,
     * whatever order the overloads were created in — and by name the CALL is refused as
     * {@link AmbiguousOverload}. An overload no argument can reach is the argument-type refusal.
     *
     * @param procName  the procedure's name as the sentences print it
     * @param namedName the procedure's name as the CALL writes it, which the refusal of an ambiguous CALL prints
     * @param overloads the procedure's overloads
     * @param arguments the CALL's arguments
     * @param typer     the evaluator whose static typing reads each argument
     * @return the overload to run
     */
    static Procedure chooseOverload(final String procName, final String namedName, final List<Procedure> overloads,
                                    final List<FrostlakeParser.CallArgumentContext> arguments,
                                    final ExpressionEvaluator typer) {
        final List<String> names = new ArrayList<String>();
        for (final FrostlakeParser.CallArgumentContext argument : arguments) {
            if (argument.namedArgument() != null && argument.namedArgument().variableName() != null) {
                names.add(argument.namedArgument().variableName().getText());
            }
        }
        final List<String> argumentFamilies = new ArrayList<String>();
        final List<DataType> argumentTypes = new ArrayList<DataType>();
        final List<String> spelled = new ArrayList<String>();
        for (final FrostlakeParser.CallArgumentContext argument : arguments) {
            final FrostlakeParser.ExpressionContext value = argument.namedArgument() != null
                ? argument.namedArgument().expression() : argument.expression();
            if (value == null || isUntypedNull(value, typer)) {
                argumentFamilies.add(null);
                argumentTypes.add(null);
                spelled.add("NULL");
                continue;
            }
            final DataType type = staticType(value, typer);
            argumentFamilies.add(family(type));
            argumentTypes.add(type);
            spelled.add(type == null ? "NULL" : SqlTypeNames.refusalSpelling(type));
        }
        if (!names.isEmpty() && names.size() == arguments.size()) {
            final Procedure named = chooseByName(namedName, overloads, names, argumentFamilies);
            if (named != null) {
                return named;
            }
        }
        final List<Procedure> counted = new ArrayList<Procedure>();
        int widest = 0;
        for (final Procedure overload : overloads) {
            final List<Parameter> params = overload.getParameters();
            widest = Math.max(widest, params.size());
            if (arguments.size() <= params.size() && requiredCount(params) <= arguments.size()) {
                counted.add(overload);
            }
        }
        if (counted.isEmpty() && arguments.size() > widest) {
            final List<String> written = new ArrayList<String>();
            for (final FrostlakeParser.CallArgumentContext argument : arguments) {
                written.add(ParseTreeText.getOriginalText(argument));
            }
            throw new RuntimeException(SqlCompilationError.at(0, -1, "too many arguments for function ["
                + procName + "(" + String.join(", ", written) + ")] expected " + widest + ", got "
                + arguments.size()));
        }
        Procedure best = null;
        long bestWeight = Long.MAX_VALUE;
        String bestSignature = null;
        for (final Procedure overload : counted) {
            final long weight = bindsShapes(overload.getParameters(), argumentTypes)
                ? weight(overload.getParameters(), null, argumentFamilies) : -1;
            if (weight < 0) {
                continue;
            }
            final String signature = signature(overload.getParameters());
            if (weight < bestWeight || weight == bestWeight && signature.compareTo(bestSignature) < 0) {
                best = overload;
                bestWeight = weight;
                bestSignature = signature;
            }
        }
        if (best == null) {
            throw new RuntimeException(SqlCompilationError.at(0, -1, "Invalid argument types for function '"
                + procName + "': (" + String.join(", ", spelled) + ")"));
        }
        return best;
    }

    /**
     * The overload an all-named CALL runs: the one overload that takes its names, or of several the one whose
     * conversions weigh least. Null when no overload takes the names, which the CALL's own judgement then refuses,
     * as it does the arguments of the first overload returned when every one refuses an argument's type.
     */
    private static Procedure chooseByName(final String namedName, final List<Procedure> overloads,
                                          final List<String> names, final List<String> argumentFamilies) {
        final List<Procedure> fitting = new ArrayList<Procedure>();
        final List<Procedure> lightest = new ArrayList<Procedure>();
        long least = Long.MAX_VALUE;
        for (final Procedure overload : overloads) {
            final int[] targets = NamedArguments.targets(overload.getParameters(), names);
            if (targets == null) {
                continue;
            }
            fitting.add(overload);
            final long weight = weight(overload.getParameters(), targets, argumentFamilies);
            if (weight < 0) {
                continue;
            }
            if (weight < least) {
                lightest.clear();
                least = weight;
            }
            if (weight == least) {
                lightest.add(overload);
            }
        }
        if (lightest.size() > 1) {
            throw new AmbiguousOverload(namedName);
        }
        if (lightest.size() == 1) {
            return lightest.get(0);
        }
        return fitting.isEmpty() ? null : fitting.get(0);
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
     * What an overload's conversions weigh for these arguments, all together, or -1 when an argument cannot
     * reach its parameter.
     *
     * @param params           the overload's parameters
     * @param targets          the parameter each argument fills, or null for arguments read by position
     * @param argumentFamilies each argument's family, null for an untyped NULL and for one nothing types
     */
    private static long weight(final List<Parameter> params, final int[] targets,
                               final List<String> argumentFamilies) {
        long total = 0;
        for (int i = 0; i < argumentFamilies.size(); i++) {
            final String parameter = family(params.get(targets == null ? i : targets[i]).getDataType());
            final String argument = argumentFamilies.get(i);
            if (parameter == null) {
                total += UNMEASURED_PARAMETER;
                continue;
            }
            if (argument == null || argument.equals(parameter)) {
                continue;
            }
            final Set<String> refused = REFUSED.get(parameter);
            if (refused != null && refused.contains(argument)) {
                return -1;
            }
            final String weighedArgument = weighedFamily(argument);
            final String weighedParameter = weighedFamily(parameter);
            if (weighedArgument.equals(weighedParameter)) {
                // One timestamp kind read as another: not its own, and lighter than any other conversion.
                total += 1;
                continue;
            }
            final int weight = OverloadConversionCosts.of(weighedArgument, weighedParameter);
            total += weight < 0 ? UNNAMED_CONVERSION : weight;
        }
        return total;
    }

    /** The parameters' internal type names in turn, which is what overloads that weigh alike sort by. */
    private static String signature(final List<Parameter> params) {
        final StringBuilder key = new StringBuilder();
        for (final Parameter param : params) {
            final String parameter = family(param.getDataType());
            key.append(parameter == null ? "~" : internalName(parameter)).append('~');
        }
        return key.toString();
    }

    /** A family as {@link OverloadConversionCosts} names it. */
    private static String weighedFamily(final String family) {
        switch (family) {
            case "FIXED":
                return "NUMBER";
            case "TEXT":
                return "VARCHAR";
            case "NTZ":
            case "LTZ":
            case "TZ":
                return "TIMESTAMP";
            default:
                return family;
        }
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
    /** A positional argument's value as written: an expression, or a NOT, AND or OR over values; null for none. */
    private static ParserRuleContext positionalValue(final FrostlakeParser.CallArgumentContext argument) {
        return argument.expression() != null ? argument.expression() : argument.booleanExpr();
    }

    /**
     * Whether a TIME is given for a timestamp parameter, which has a sentence of its own when no other argument is
     * refused: {@code incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]}; beside a refused argument the CALL is
     * the argument-type refusal over every argument (live-verified).
     */
    private static boolean isTimeForTimestamp(final DataType parameter, final DataType argument) {
        final String parameterFamily = family(parameter);
        return "TIME".equals(family(argument))
            && ("NTZ".equals(parameterFamily) || "LTZ".equals(parameterFamily) || "TZ".equals(parameterFamily));
    }

    /**
     * Whether a parameter refuses an argument for being a predicate: every parameter but a BOOLEAN one does, a
     * text and a VARIANT parameter among them, which take a BOOLEAN value — {@code CALL p(1 = 1)} and {@code CALL
     * p(CURRENT_USER() IS NULL)} into a VARCHAR are refused where {@code CALL p(TRUE)} binds (live-verified).
     */
    private static boolean refusesPredicate(final DataType parameter, final ParserRuleContext value) {
        final String family = family(parameter);
        if (family == null || "BOOLEAN".equals(family)) {
            return false;
        }
        try {
            return PredicateExpressions.isPredicate(ExpressionEvaluator.parse(ParseTreeText.getOriginalText(value)));
        } catch (final RuntimeException unread) {
            return false;
        }
    }

    /** Whether each argument written by position binds the structured parameter it fills (see {@link #refuses}). */
    private static boolean bindsShapes(final List<Parameter> params, final List<DataType> argumentTypes) {
        for (int i = 0; i < argumentTypes.size() && i < params.size(); i++) {
            if (refusesShape(params.get(i).getDataType(), argumentTypes.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a structured parameter refuses an argument: only one of its own shape binds it, its numbers' leaves
     * reading each other (see {@link StructuredTypes#sameBindingShape}). {@code p(x OBJECT(a INT))} takes an
     * {@code OBJECT(a FLOAT)} and refuses an {@code OBJECT(b INT)} and a plain OBJECT (live-verified).
     */
    private static boolean refusesShape(final DataType parameter, final DataType argument) {
        return argument != null && StructuredTypes.isStructured(parameter)
            && !StructuredTypes.sameBindingShape(argument, parameter);
    }

    private static boolean refuses(final DataType parameter, final DataType argument) {
        if (refusesShape(parameter, argument)) {
            return true;
        }
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
        if (NumericType.isDecfloat(type)) {
            return "DECFLOAT";
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

    /**
     * Whether an argument is an untyped NULL: the bare word, or date arithmetic over a variable that holds NULL,
     * which folds to one as it does over the bare word — {@code CALL p(:d + 1)}, {@code CALL p(:d - 1)} and
     * {@code CALL p(DATEADD(day, 1, :d))} over a NULL DATE variable bind into any parameter and are spelled
     * {@code NULL}, where over a date they are a DATE (live-verified). Arithmetic over a NULL number stays a NUMBER.
     */
    private static boolean isUntypedNull(final ParserRuleContext value, final ExpressionEvaluator typer) {
        if (value instanceof FrostlakeParser.LiteralExprContext
                && ((FrostlakeParser.LiteralExprContext) value).literal().NULL() != null) {
            return true;
        }
        final Expression parsed;
        try {
            parsed = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(value));
        } catch (final RuntimeException unread) {
            return false;
        }
        if (parsed instanceof BinaryOperationExpression) {
            final BinaryOperationExpression arithmetic = (BinaryOperationExpression) parsed;
            final BinaryOperator operator = arithmetic.getOperator();
            return (operator == BinaryOperator.ADD || operator == BinaryOperator.SUBTRACT)
                && (isNullDate(arithmetic.getLeft(), typer) || isNullDate(arithmetic.getRight(), typer));
        }
        if (!(parsed instanceof FunctionCallExpression)
                || !UntypedNullFold.isDateCall((FunctionCallExpression) parsed)) {
            return false;
        }
        for (final Expression argument : ((FunctionCallExpression) parsed).getArguments()) {
            if (typer.isNullVariable(argument)) {
                return true;
            }
        }
        return false;
    }

    /** Whether an operand is a variable declared DATE that holds NULL. */
    private static boolean isNullDate(final Expression operand, final ExpressionEvaluator typer) {
        if (!typer.isNullVariable(operand)) {
            return false;
        }
        final DataType declared = typer.inferStaticType(operand);
        return declared instanceof DateTimeType && "DATE".equalsIgnoreCase(declared.getName());
    }

    /**
     * An argument as the too-many-arguments sentence echoes it: as written, but for a {@code :name} of a
     * semi-structured variable that holds a value, which the statement binds as the JSON it holds —
     * {@code PARSE_JSON(:v)} for a VARIANT, cast to its type for an ARRAY and an OBJECT,
     * {@code CAST(PARSE_JSON(:o) AS OBJECT)}; one that holds NULL stays {@code :v} (live-verified).
     */
    private static String echoed(final FrostlakeParser.CallArgumentContext argument, final ExpressionEvaluator typer) {
        final String written = ParseTreeText.getOriginalText(argument);
        if (argument.expression() == null) {
            return written;
        }
        try {
            final Expression parsed = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(argument.expression()));
            final DataType declared = parsed instanceof BindVariableExpression && !typer.isNullVariable(parsed)
                ? typer.inferStaticType(parsed) : null;
            if (declared == null || StructuredTypes.isStructured(declared)) {
                return written;
            }
            if (declared instanceof VariantType) {
                return "PARSE_JSON(" + written + ")";
            }
            if (declared instanceof ObjectType) {
                return "CAST(PARSE_JSON(" + written + ") AS OBJECT)";
            }
            return declared instanceof ArrayType ? "CAST(PARSE_JSON(" + written + ") AS ARRAY)" : written;
        } catch (final RuntimeException untyped) {
            return written;
        }
    }

    /**
     * Whether an argument is a {@code :name} whose variable is declared VARIANT: a VARIANT to the CALL, whatever
     * the variable holds.
     *
     * @param written the argument as written
     * @param typer   types the argument
     * @return whether the CALL binds a VARIANT there
     */
    /**
     * Whether an argument is an exact number by its static type, whatever carrier its value arrives in.
     *
     * @param written the argument as written
     * @param typer   the evaluator whose static typing reads it
     * @return whether it is an exact NUMBER
     */
    public static boolean isExactNumber(final FrostlakeParser.ExpressionContext written,
                                        final ExpressionEvaluator typer) {
        final DataType type = staticType(written, typer);
        return type instanceof NumericType && !NumericType.isApproximate(type);
    }

    public static boolean bindsAVariant(final FrostlakeParser.ExpressionContext written,
                                        final ExpressionEvaluator typer) {
        try {
            final Expression parsed = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(written));
            return parsed instanceof BindVariableExpression && typer.inferStaticType(parsed) instanceof VariantType;
        } catch (final RuntimeException untyped) {
            return false;
        }
    }

    /**
     * A VARIANT variable's text as the CALL converts it to a number's, a boolean's, a date's or a time's
     * parameter: the variant it is, so a text the parameter does not read is refused as one — "Failed to cast
     * variant value "x" to DATE" (live-verified).
     *
     * @param value what the variable holds
     * @param type  the parameter's declared type
     * @return the value to convert
     */
    public static Object variantText(final Object value, final DataType type) {
        return value instanceof CharSequence
            && (type instanceof NumericType || type instanceof BooleanType || type instanceof DateTimeType)
            ? VariantValue.ofNode(ArrayFunctionHelper.MAPPER.getNodeFactory().textNode(value.toString())) : value;
    }

    /**
     * An argument's static type, or null when it cannot be read. A variable of a block that holds a text or a binary
     * value is typed at that value's own width, as a literal of it is; holding NULL it keeps its declared width.
     */
    /**
     * A FROM-form argument's type, the argument compiled with the statement: what its compilation refuses — a name that
     * is not there, a function that is not there — is the statement's own refusal, placed where the argument stands
     * (live-verified), where it used to come back from inside the procedure's CALL. Anything else this pass cannot type
     * answers null.
     */
    private static DataType compiledType(final ParserRuleContext value, final ExpressionEvaluator typer) {
        final SourcePosition displaced = ExpressionSource.beginNested(
            new SourcePosition(value.getStart().getLine(), value.getStart().getCharPositionInLine()));
        try {
            return typer.inferStaticType(ExpressionEvaluator.parse(ParseTreeText.getOriginalText(value))) == null
                ? null : staticType(value, typer);
        } catch (final RuntimeException refused) {
            if (SqlCompilationError.isCompilationError(refused.getMessage())) {
                throw refused;
            }
            return null;
        } finally {
            ExpressionSource.end(displaced);
        }
    }

    private static DataType staticType(final ParserRuleContext value, final ExpressionEvaluator typer) {
        try {
            final Expression parsed = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(value));
            final DataType declared = typer.inferStaticType(parsed);
            if (parsed instanceof BindVariableExpression && !typer.isNullVariable(parsed)) {
                final Object held = typer.evaluate(parsed, null);
                if (declared instanceof StringType && held instanceof CharSequence) {
                    return new StringType("VARCHAR", Math.max(1, CodePointText.length(held.toString())));
                }
                if (declared instanceof BinaryType && held instanceof BinaryValue) {
                    return new BinaryType("BINARY", Math.max(1, ((BinaryValue) held).bytes().length));
                }
            }
            return declared;
        } catch (final RuntimeException untyped) {
            return null;
        }
    }
}
