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

import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.StarArgument;
import dev.frostlake.executor.SubqueryCompilation;
import dev.frostlake.executor.commands.DataTypeParser;
import dev.frostlake.executor.udf.UserAggregateFunction;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.IntervalDayTimeType;
import dev.frostlake.types.LengthlessStringType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.StructuredObjectType;
import dev.frostlake.types.StructuredTypes;
import dev.frostlake.types.UuidType;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The expression renderer for STRICTNESS messages. Snowflake renders the offending call from its
 * analysed plan, not from the source text: a bare column reference comes out QUALIFIED with its
 * source relation — live, {@code TO_VARCHAR(f)} over table {@code fk} is
 * "invalid type [TO_VARCHAR(FK.F)] for parameter 'TO_VARCHAR'" and {@code CAST(so AS VARCHAR)} over
 * {@code stt} renders {@code CAST(STT.SO AS VARCHAR(134217728))}. Everything else prints exactly as
 * {@link AstPrinterVisitor} does, which is why this is a subclass overriding only the column node.
 *
 * <p>The qualifier comes from the EVALUATION context ({@link ExpressionEvaluatorVisitor}) — the same
 * resolution the value read uses — and is omitted when the owner is a synthetic relation (a derived
 * table, the FROM-less DUMMY), whose invented name live would never print.
 *
 * <p>How much of the plan shows depends on the {@link StrictPrintMode}. In the PLAN mode — the arity
 * sentences, the conversion sentence and the two calls a nesting message brackets — the plan's own
 * spellings are printed rather than the written ones (see {@link #visitFunctionCall} and
 * {@link #visitCast}): a plain {@code AVG} is its own definition, {@code MEDIAN} and the two ordered
 * percentiles are the percentile call with the ordered value first, a cast is spelled
 * {@code CAST(x AS T)}, a conversion call is the cast it stands for and an interval shift is its
 * {@code DATE_ADD…} function. In the CONVERSION mode — an invalid-type sentence — the conversions are
 * still the functions they were planned as. The window-frame refusal prints as written:
 * {@code [MEDIAN(WF.N) OVER (ORDER BY WF.N ASC NULLS LAST)]}.
 */
final class StrictMessagePrinter extends AstPrinterVisitor {
    /** How far SUM widens an exact input: NUMBER(p, s) sums to NUMBER(min(38, p + 12), s), live-measured. */
    private static final int SUM_EXTRA_DIGITS = 12;
    /** What COUNT declares, the divisor of every plain AVG. */
    private static final NumericType COUNT_TYPE = new NumericType("NUMBER", 18, 0);
    private static final int MAX_PRECISION = 38;
    /** The most decimals a NUMBER holds. */
    private static final int MAX_SCALE = 37;
    /** The digits and the decimals an average of distinct values adds to its argument's. */
    private static final int DISTINCT_AVERAGE_DIGITS = 15;
    private static final int DISTINCT_AVERAGE_DECIMALS = 3;
    /** The whole number an interval amount and a vector dimension are planned as. */
    private static final String WHOLE_NUMBER_TYPE = "NUMBER(9,0)";
    /** What a DATEADD target that is no timestamp yet is converted to before a time unit moves it. */
    private static final String TIMESTAMP_TARGET = "TIMESTAMP_NTZ(9)";

    private final ExpressionEvaluatorVisitor context;
    /** Which spelling this printer gives: as written, the resolved plan, or an invalid-type sentence's. */
    private final StrictPrintMode mode;
    /** Whether a routine parameter's default is told back — see {@link #tellingDefault()}. */
    private boolean tellingDefault;
    /** Whether a column prints as written, unresolved — see {@link #columnsAsWritten()}. */
    private boolean columnsAsWritten;
    /** Whether what the plan folds early prints as its value — see {@link #foldingConstants()}. */
    private boolean foldingConstants;
    /** Whether DATE_PART and EXTRACT print as EXTRACT(part from …) — see {@link #extractingParts()}. */
    private boolean extractingParts;
    /** The functions whose answer a column default's echo converts, by name — see {@link #echoingDefault}. */
    private Map<String, DataType> convertedCalls;
    /** The calls being printed bare inside their conversion. */
    private final Set<FunctionCallExpression> convertingCalls = Collections.newSetFromMap(
        new IdentityHashMap<FunctionCallExpression, Boolean>());

    /** A division's scale grows by this much over the dividend's, to a cap of {@link #DIVISION_SCALE_CAP}. */
    private static final int DIVISION_EXTRA_SCALE = 6;
    private static final int DIVISION_SCALE_CAP = 12;
    /** The precision a NUMBER declared without one carries. */
    private static final int WIDEST_NUMBER = 38;

    StrictMessagePrinter(final ExpressionEvaluatorVisitor context) {
        this(context, StrictPrintMode.WRITTEN);
    }

    StrictMessagePrinter(final ExpressionEvaluatorVisitor context, final StrictPrintMode mode) {
        this.context = context;
        this.mode = mode;
    }

    /**
     * Prints as a routine parameter's default is told back, on top of the plan's spelling: an arithmetic operation
     * written in parentheses as an operand of another is the cast to its type, {@code 1 * (CAST(2 + 3 AS
     * NUMBER(2,0)))}, a cast or a conversion to a text written without a width is to the bare {@code VARCHAR}, and
     * TO_VARCHAR given a format is TO_CHAR (live-verified).
     *
     * @return this printer
     */
    StrictMessagePrinter tellingDefault() {
        this.tellingDefault = true;
        return this;
    }

    /**
     * Prints as the refusal of a column default echoes it, on top of the plan's spelling: a conversion to FLOAT is
     * TO_DOUBLE, {@code (TO_DOUBLE(0.5))}, and the call of each function named here is the conversion of its answer
     * to the number it declares, {@code TO_NUMBER(FBLK())} and as an operand {@code (TO_NUMBER(FBLK())) + 1}
     * (live-verified).
     *
     * @param converted the functions whose answer is converted, by upper-cased name, with the type each declares
     * @return this printer
     */
    StrictMessagePrinter echoingDefault(final Map<String, DataType> converted) {
        this.convertedCalls = converted;
        return this;
    }

    /** Whether conversions to FLOAT print as TO_DOUBLE: an invalid-type sentence and a column default's echo. */
    private boolean doublesAsConversions() {
        return conversionShaped() || convertedCalls != null;
    }

    /** The type a column default's echo converts a call's answer to, or null for a call it prints as it is. */
    private DataType convertedCallType(final Expression expression) {
        if (convertedCalls == null || !(expression instanceof FunctionCallExpression)) {
            return null;
        }
        final FunctionCallExpression call = (FunctionCallExpression) expression;
        if (call.getNameExpression() != null || call.getFunctionName() == null || convertingCalls.contains(call)) {
            return null;
        }
        return convertedCalls.get(call.getFunctionName().toUpperCase(Locale.ROOT));
    }

    /**
     * A block's variable bound into one of the block's own SQL expressions — the query of an assignment, a LET or a
     * RETURN — prints as the plan converts it to the variable's declared type: an exact number through TO_NUMBER, a
     * FLOAT and a DATE through a CAST, a text as written — {@code ABS(TO_NUMBER(:s), 1)}, {@code ABS(TO_NUMBER(:s,
     * 10, 2), 1)}, {@code ABS(CAST(:s AS FLOAT), 1)}, {@code YEAR(CAST(:s AS DATE), 1)}, an operand in parentheses,
     * {@code (TO_NUMBER(:s)) + 1}. A statement the block runs keeps the bind as written (all live-verified).
     */
    @Override
    public String visitBindVariable(final BindVariableExpression expr) {
        final String conversion = blockExpressionConversion(expr);
        return conversion != null ? conversion : super.visitBindVariable(expr);
    }

    /** The conversion a bind prints as in a block's own SQL expression, or null where it prints as written. */
    private String blockExpressionConversion(final BindVariableExpression expr) {
        final DataType declared = context.blockExpressionBindType(expr);
        final String written = ":" + expr.getVarName();
        if (declared instanceof NumericType && !NumericType.isApproximate(declared)) {
            final NumericType number = (NumericType) declared;
            return number.getPrecision() == WIDEST_NUMBER && number.getScale() == 0
                ? "TO_NUMBER(" + written + ")"
                : "TO_NUMBER(" + written + ", " + number.getPrecision() + ", " + number.getScale() + ")";
        }
        if (declared instanceof NumericType) {
            return "CAST(" + written + " AS FLOAT)";
        }
        if (declared instanceof DateTimeType && "DATE".equals(declared.getName())) {
            return "CAST(" + written + " AS DATE)";
        }
        return null;
    }

    /**
     * Prints each column as written, before its name is resolved: {@code N}, {@code T.N} — as a call that reads
     * its arguments before their names are resolved echoes them (see SystemConstantArguments).
     *
     * @return this printer
     */
    StrictMessagePrinter columnsAsWritten() {
        this.columnsAsWritten = true;
        return this;
    }

    /**
     * Prints what the plan folds before a constant argument is judged as the value it folds to — a context function,
     * UPPER or LOWER over a literal: {@code 'TEST_SCHEMA'}, {@code 'E1'}, a date as {@code '2024-01-02'} — and a
     * text joined to the empty string as the text alone (see SystemConstantArguments).
     *
     * @return this printer
     */
    StrictMessagePrinter foldingConstants() {
        this.foldingConstants = true;
        return this;
    }

    /**
     * Prints a DATE_PART call and an EXTRACT as the plan of a constant argument holds them, {@code EXTRACT(year from
     * x)}, as a call of YEAR is printed (live-verified: SYSTEM$ABORT_TRANSACTION's refusal of {@code DATE_PART(year,
     * NULL::DATE)} finds {@code EXTRACT(year from SYSTEM$NULL_TO_DATE(null))}).
     *
     * @return this printer
     */
    StrictMessagePrinter extractingParts() {
        this.extractingParts = true;
        return this;
    }

    /** Whether the plan's resolved spellings are printed — the arity, conversion and nesting sentences. */
    private boolean planShaped() {
        return mode == StrictPrintMode.PLAN;
    }

    /** Whether the conversions are printed as the functions they were planned as — an invalid-type sentence. */
    private boolean conversionShaped() {
        return mode == StrictPrintMode.CONVERSION;
    }

    /**
     * Whether an untyped NULL is printed as the plan types it — {@code (SYSTEM$NULL_TO_FIXED(null)) + 1} — which
     * every plan-shaped sentence does, the invalid-type sentence in its own spellings ({@link #inConversionSpelling}).
     */
    private boolean typesNulls() {
        return mode != StrictPrintMode.WRITTEN;
    }

    /**
     * A call named as it was written, its arguments printed in this printer's mode — the arity
     * sentences' echo, where the refused call keeps its own name ({@code TO_DATE(CAST('2020-01-15' AS
     * DATE), 'YYYY-MM-DD', 1)}) while everything inside it is re-printed from the plan.
     */
    String calledAsWritten(final FunctionCallExpression call) {
        if (isVariableRead(call) && call.getArguments().size() != 1) {
            return infixVariableRead(call.getArguments());
        }
        final String written = super.visitFunctionCall(call);
        // The account knows GROUPING as GROUPING_ID wherever it echoes the call (live-verified).
        return call.getNameExpression() == null && "GROUPING".equalsIgnoreCase(call.getFunctionName())
            && written.regionMatches(true, 0, "GROUPING(", 0, 9) ? "GROUPING_ID(" + written.substring(9) : written;
    }

    /**
     * Whether a call is GETVARIABLE, which the plan holds as an OPERATOR rather than a call: its one argument
     * alone, as an operand ({@code RANDOM(GETVARIABLE('SV'))} is "found ''SV''" and over a nested call
     * "found '(UPPER('sv'))'"), and the arity echo's other widths infixed ({@link #infixVariableRead}).
     */
    private static boolean isVariableRead(final FunctionCallExpression call) {
        return call.getNameExpression() == null && !call.isStar() && call.getFunctionName() != null
            && call.getFunctionName().equalsIgnoreCase("GETVARIABLE");
    }

    /** GETVARIABLE over its one argument, which prints as an operand already (see {@link #visitFunctionCall}). */
    private static boolean isSoleVariableRead(final Expression expression) {
        return expression instanceof FunctionCallExpression && isVariableRead((FunctionCallExpression) expression)
            && ((FunctionCallExpression) expression).getArguments().size() == 1;
    }

    /**
     * GETVARIABLE written with any other number of arguments than one, as the arity sentences echo it: the
     * name between each two arguments, {@code 'SV' GETVARIABLE 1 GETVARIABLE 2}, and nothing at all for none.
     */
    private String infixVariableRead(final List<Expression> args) {
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            text.append(i > 0 ? " GETVARIABLE " : "").append(operand(args.get(i)));
        }
        return text.toString();
    }

    /**
     * GETVARIABLE's name as the plan holds it: a text argument as it is, anything else converted to text,
     * {@code CAST(RT.N AS VARCHAR(134217728))} — the argument GETVARIABLE's own constant-name refusal names.
     */
    String variableNameText(final Expression name) {
        final String printed = name.accept(this);
        return convertsToText(name)
            ? "CAST(" + printed + " AS VARCHAR(" + DataTypeParser.CAST_STRING_DEFAULT + "))" : printed;
    }

    /**
     * A plan-shaped text with each NULL left bare, as GETVARIABLE's constant-name refusal quotes it: no NULL is
     * typed there, {@code IFF(1 = 1, null, 'x')} where the other sentences read
     * {@code IFF(1 = 1, SYSTEM$NULL_TO_TEXT(null), 'x')}, and none is converted to the NULL type,
     * {@code HASH(null)} for {@code HASH(CAST(null AS NULL))} (live-verified). A string literal is left as it is.
     *
     * @param printed the plan-shaped text
     * @return the text with its NULLs bare
     */
    static String withBareNulls(final String printed) {
        final StringBuilder bare = new StringBuilder(printed.length());
        int i = 0;
        while (i < printed.length()) {
            final char c = printed.charAt(i);
            if (c == '\'') {
                // A literal runs to its closing quote, a doubled quote inside it being one of its own.
                int end = i + 1;
                while (end < printed.length()) {
                    if (printed.charAt(end) == '\'') {
                        if (end + 1 < printed.length() && printed.charAt(end + 1) == '\'') {
                            end += 2;
                            continue;
                        }
                        break;
                    }
                    end++;
                }
                bare.append(printed, i, Math.min(end + 1, printed.length()));
                i = end + 1;
                continue;
            }
            if (printed.startsWith(NULL_TYPE_CAST, i)) {
                bare.append("null");
                i += NULL_TYPE_CAST.length();
                continue;
            }
            if (printed.startsWith(TYPED_NULL_PREFIX, i)) {
                final int open = printed.indexOf('(', i);
                if (open > 0 && printed.startsWith("(null)", open)
                        && isFamilyWord(printed, i + TYPED_NULL_PREFIX.length(), open)) {
                    bare.append("null");
                    i = open + "(null)".length();
                    continue;
                }
            }
            bare.append(c);
            i++;
        }
        return bare.toString();
    }

    /**
     * A text printed with the plan's typed NULLs, in the invalid-type sentence's spellings: the word upper-cased,
     * and a conversion to the NULL type written COERCE — {@code NVL(COERCE(NULL AS NULL), NULL)} for the plan's
     * {@code NVL(CAST(null AS NULL), null)} (live-verified). A string literal is left as it is.
     *
     * @param printed the text
     * @return the text in the sentence's spellings
     */
    static String inConversionSpelling(final String printed) {
        if (printed.indexOf("null") < 0 && printed.indexOf(" AS NULL)") < 0) {
            return printed;
        }
        final StringBuilder text = new StringBuilder(printed);
        // Each CAST(… AS NULL) is renamed from the innermost outwards; the word is upper-cased last.
        int close = indexOutsideLiterals(text, " AS NULL)", 0);
        while (close >= 0) {
            int depth = 0;
            int open = -1;
            for (int i = close - 1; i >= 0 && open < 0; i--) {
                final char c = text.charAt(i);
                if (c == ')') {
                    depth++;
                } else if (c == '(' && depth-- == 0) {
                    open = i;
                }
            }
            if (open >= "CAST".length() && text.substring(open - "CAST".length(), open).equals("CAST")) {
                text.replace(open - "CAST".length(), open, "COERCE");
                close += "COERCE".length() - "CAST".length();
            }
            close = indexOutsideLiterals(text, " AS NULL)", close + 1);
        }
        int word = indexOutsideLiterals(text, "null", 0);
        while (word >= 0) {
            final boolean wholeWord = (word == 0 || !isWordCharacter(text.charAt(word - 1)))
                && (word + "null".length() == text.length() || !isWordCharacter(text.charAt(word + "null".length())));
            if (wholeWord) {
                text.replace(word, word + "null".length(), "NULL");
            }
            word = indexOutsideLiterals(text, "null", word + "null".length());
        }
        return text.toString();
    }

    /** The first place at or after {@code from} where {@code sought} stands outside every string literal, or -1. */
    private static int indexOutsideLiterals(final CharSequence text, final String sought, final int from) {
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\'') {
                quoted = !quoted;
                continue;
            }
            if (!quoted && i >= from && i + sought.length() <= text.length()
                    && text.subSequence(i, i + sought.length()).toString().equals(sought)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isWordCharacter(final char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /** The NULL the plan holds in the NULL type's own. */
    private static final String NULL_TYPE_CAST = "CAST(null AS NULL)";
    /** What a typed NULL's conversion opens with. */
    private static final String TYPED_NULL_PREFIX = "SYSTEM$NULL_TO_";

    private static boolean isFamilyWord(final String text, final int from, final int to) {
        if (to <= from) {
            return false;
        }
        for (int i = from; i < to; i++) {
            final char c = text.charAt(i);
            if (!(c >= 'A' && c <= 'Z') && c != '_') {
                return false;
            }
        }
        return true;
    }

    /** Whether the plan converts a text argument's value to text first: every typed value that is no text. */
    private boolean convertsToText(final Expression argument) {
        final DataType type = argumentType(argument);
        return type != null && !(type instanceof StringType) && !isUntypedNull(argument);
    }

    /**
     * NULL is upper-cased in an invalid-type sentence ({@code ARRAY_CONSTRUCT(1, 2, NULL)}) and
     * lower-cased elsewhere. A string is re-printed from the plan with its quote and its backslash
     * escaped again, {@code UPPER('it''s', 1)} and {@code 'a\\b'}, where the written spelling is gone. A
     * binary literal keeps its hexadecimal spelling, {@code X'00'}.
     */
    /**
     * A unit-suffixed interval literal as the plan names it: the conversion of its TEXT to the literal's type. A
     * refusal naming the operand spells it {@code CAST('1 02' AS INTERVAL DAY(9) TO HOUR)}, and an invalid-type
     * or arity sentence the conversion function, {@code TO_INTERVAL_DAY_TIME('1 02')} (live-verified). The
     * quoted-string form is named {@code INTERVAL_LITERAL(...)} over its parts as written.
     */
    @Override
    public String visitInterval(final IntervalExpression expr) {
        if (mode != StrictPrintMode.WRITTEN && expr.isUnitInString() && expr.getWrittenAmount() != null) {
            return quotedUnitLiteral(expr);
        }
        final IntervalLiteralSpec literal = mode == StrictPrintMode.WRITTEN ? null : expr.getLiteral();
        if (literal == null) {
            return super.visitInterval(expr);
        }
        final String text = "'" + literal.getText() + "'";
        if (conversionShaped()) {
            return (literal.isDayTime() ? "TO_INTERVAL_DAY_TIME(" : "TO_INTERVAL_YEAR_MONTH(") + text + ")";
        }
        return "CAST(" + text + " AS " + literal.getType().getName() + ")";
    }

    /**
     * A quoted-unit interval literal as the plan names it: INTERVAL_LITERAL over each part's unit word and amount
     * as written, SECOND for a part written without a unit (all live-verified):
     *
     * <pre>
     *   INTERVAL '1 hour'           INTERVAL_LITERAL('hour', '1')
     *   INTERVAL ' +01  Hour '      INTERVAL_LITERAL('Hour', '+01')
     *   INTERVAL '1 day, 2 hours'   INTERVAL_LITERAL('day', '1', 'hours', '2')
     *   INTERVAL '10'               INTERVAL_LITERAL('SECOND', '10')
     * </pre>
     */
    private static String quotedUnitLiteral(final IntervalExpression expr) {
        final StringBuilder out = new StringBuilder("INTERVAL_LITERAL(");
        for (IntervalExpression part = expr; part != null; part = part.getRest()) {
            if (part != expr) {
                out.append(", ");
            }
            out.append('\'').append(part.getWrittenUnit() == null ? "SECOND" : part.getWrittenUnit())
                .append("', '").append(part.getWrittenAmount()).append('\'');
        }
        return out.append(')').toString();
    }

    @Override
    public String visitLiteral(final LiteralExpression expr) {
        if (expr instanceof FoldedConstantExpression) {
            return ((FoldedConstantExpression) expr).printedValue();
        }
        if (typesNulls() && expr.getType() == LiteralType.NULL && nullFamily != null) {
            return "SYSTEM$NULL_TO_" + nullFamily + "(null)";
        }
        if (conversionShaped() && expr.getType() == LiteralType.NULL) {
            return "NULL";
        }
        if (mode != StrictPrintMode.WRITTEN && expr.getType() == LiteralType.STRING) {
            return "'" + escapedText(String.valueOf(expr.getValue())) + "'";
        }
        if (mode != StrictPrintMode.WRITTEN && expr.getType() == LiteralType.BINARY) {
            return "X'" + expr.getValue() + "'";
        }
        return super.visitLiteral(expr);
    }

    /** A string literal's text as the plan re-prints it: each quote and each backslash doubled. */
    private static String escapedText(final String value) {
        final StringBuilder text = new StringBuilder(value.length() + 2);
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (c == '\'') {
                text.append("''");
            } else if (c == '\\') {
                text.append("\\\\");
            } else {
                text.append(c);
            }
        }
        return text.toString();
    }

    /**
     * An infix operator's operands as live's plan prints them — ONE rule on every surface it was
     * measured (the nesting brackets, the arity and invalid-type echoes, the window-specification
     * sentence):
     *
     * <ul>
     *   <li>a column reference, a literal and a window call are printed BARE; everything else — a
     *       function call, a cast, a NEGATE or NOT, a nested operator, a predicate — is bracketed as an
     *       operand: {@code (SUM(T.N)) + 1}, {@code (SUM(T.I)) + (MIN(T.I))}, {@code T.N + (CAST(1 AS
     *       NUMBER(10,2)))}, {@code (SUM(T.I)) + (1 * 2)}, {@code (SUM(T.I)) + ROW_NUMBER() OVER (…)};</li>
     *   <li>the plan's CONVERSIONS are printed with it: beside a FLOAT an exact number is
     *       {@code CAST(x AS FLOAT)}; between two exact numbers whose SCALES differ, the lower-scale
     *       operand of +, -, % and every comparison is cast to the higher scale at the wider precision,
     *       {@code NUMBER(max(p_low + growth, p_high), s_high)} — {@code (SUM(T.N102)) + (CAST(1 AS
     *       NUMBER(22,2)))}, {@code (CAST(SUM(T.I) AS NUMBER(18,1))) + 1.5} — while a multiplication
     *       casts nothing and a division rescales its DIVIDEND to the quotient's width (six extra
     *       decimals to a cap of twelve) or, past 38 digits, becomes live's internal
     *       {@code SCALED_ROUND_INT_DIVIDE(x, y)}; a concatenation casts a non-text operand to
     *       {@code VARCHAR(134217728)}. Precision alone triggers nothing: NUMBER(38,2) beside
     *       NUMBER(17,2) prints bare.</li>
     * </ul>
     *
     * <p>An invalid-type sentence spells the same two conversions by their plan functions instead:
     * the rescale is {@code FIXED_TO_FIXED(x AS NUMBER(p,s)[UNKNOWN])} and the move to FLOAT
     * {@code TO_DOUBLE(x)} — {@code (a + 1)::DATE} over a NUMBER(10,2) is "invalid type [CAST(RE.A +
     * (FIXED_TO_FIXED(1 AS NUMBER(10,2)[UNKNOWN])) AS DATE)]", where its arity echo says
     * {@code RE.A + (CAST(1 AS NUMBER(10,2)))}.
     *
     * <p>In the plan a DATE moved by a whole number is no operator at all: {@code d + 1} and
     * {@code 1 + d} are {@code DATE_ADDDAYSTODATE(1, FT.D)} and {@code d - n} moves by the negated
     * amount, {@code DATE_ADDDAYSTODATE(NEGATE(FT.N), FT.D)}.
     *
     * <p>Kept to the message printer deliberately. The shared printer's unconditional brackets are an
     * expression KEY (the aggregate map, the window select-item index, the HAVING result context), and
     * changing them there would make {@code (a + b) * c} and {@code a + (b * c)} print alike, so two
     * different aggregates would share one key and one would read the other's value.
     */
    @Override
    public String visitBinaryOperation(final BinaryOperationExpression expr) {
        final BinaryOperator operator = expr.getOperator();
        final Expression left = expr.getLeft();
        final Expression right = expr.getRight();
        final DataType leftType = argumentType(left);
        final DataType rightType = argumentType(right);
        final String typedNulls = planShaped() || conversionShaped()
            ? withTypedNullOperands(operator, left, leftType, right, rightType) : null;
        if (typedNulls != null) {
            return typedNulls;
        }
        if (mode != StrictPrintMode.WRITTEN) {
            final String intervalOperation = intervalOperation(operator, left, leftType, right, rightType);
            if (intervalOperation != null) {
                return intervalOperation;
            }
            final String dayShift = dayShift(operator, left, leftType, right, rightType);
            if (dayShift != null) {
                return dayShift;
            }
            final String difference = operator == BinaryOperator.SUBTRACT
                ? dateDifference(left, leftType, right, rightType) : null;
            if (difference != null) {
                return difference;
            }
        }
        if (operator == BinaryOperator.CONCAT) {
            if (foldingConstants && isEmptyText(right)) {
                return left.accept(this);
            }
            if (foldingConstants && isEmptyText(left)) {
                return right.accept(this);
            }
            return asText(left, leftType) + " || " + asText(right, rightType);
        }
        if (planShaped() && (isArithmetic(operator) || isComparison(operator))) {
            final String met = textOperandMeeting(operator, left, leftType, right, rightType);
            if (met != null) {
                return met;
            }
        }
        if (leftType instanceof NumericType && rightType instanceof NumericType
                && (isArithmetic(operator) || isComparison(operator))) {
            final NumericType l = (NumericType) leftType;
            final NumericType r = (NumericType) rightType;
            final boolean leftApproximate = NumericType.isApproximate(l);
            final boolean rightApproximate = NumericType.isApproximate(r);
            if (leftApproximate != rightApproximate) {
                return (leftApproximate ? operand(left) : asFloat(left)) + " " + symbol(operator) + " "
                    + (rightApproximate ? operand(right) : asFloat(right));
            }
            if (!leftApproximate) {
                if (operator == BinaryOperator.DIVIDE) {
                    return exactDivision(left.accept(this), l, right.accept(this), operand(right), r);
                }
                if (operator != BinaryOperator.MULTIPLY && l.getScale() != r.getScale()) {
                    final boolean leftLower = l.getScale() < r.getScale();
                    final boolean arithmetic = isArithmetic(operator);
                    return (leftLower ? rescaled(left, l, r, arithmetic) : operand(left)) + " " + symbol(operator)
                        + " " + (leftLower ? operand(right) : rescaled(right, r, l, arithmetic));
                }
            }
        }
        return operand(left) + " " + symbol(operator) + " " + operand(right);
    }

    /**
     * Interval arithmetic as the plan names it, the interval operand first whatever the written order:
     * {@code (i) INTERVAL DAY TIME PLUS (j)} and {@code … MINUS …} for two intervals,
     * {@code (i) INTERVAL DAY TIME MULTIPLY 2} for {@code i * 2} and {@code 2 * i} alike, and
     * {@code (i) INTERVAL DAY TIME DIVIDE 2} (live-verified). Null for any other shape.
     */
    private String intervalOperation(final BinaryOperator operator, final Expression left, final DataType leftType,
                                     final Expression right, final DataType rightType) {
        final boolean leftInterval = leftType instanceof IntervalDayTimeType;
        final boolean rightInterval = rightType instanceof IntervalDayTimeType;
        if (leftInterval && rightInterval
                && (operator == BinaryOperator.ADD || operator == BinaryOperator.SUBTRACT)) {
            return operand(left) + (operator == BinaryOperator.ADD ? " INTERVAL DAY TIME PLUS "
                : " INTERVAL DAY TIME MINUS ") + operand(right);
        }
        if (operator == BinaryOperator.MULTIPLY && leftInterval != rightInterval) {
            return leftInterval ? operand(left) + " INTERVAL DAY TIME MULTIPLY " + operand(right)
                : operand(right) + " INTERVAL DAY TIME MULTIPLY " + operand(left);
        }
        if (operator == BinaryOperator.DIVIDE && leftInterval && !rightInterval) {
            return operand(left) + " INTERVAL DAY TIME DIVIDE " + operand(right);
        }
        return null;
    }

    /**
     * A DATE moved by an exact number of days, as the plan holds it — {@code DATE_ADDDAYSTODATE} with
     * the amount first, negated for a subtraction. Null for any other operator or operand family: a
     * FLOAT or a text beside a DATE is refused by the account rather than planned.
     */
    private String dayShift(final BinaryOperator operator, final Expression left, final DataType leftType,
                            final Expression right, final DataType rightType) {
        if (operator == BinaryOperator.ADD && isDate(leftType) && isExactNumber(rightType)) {
            return dateAdd(IntervalUnit.DAY, right.accept(this), rightType, left);
        }
        if (operator == BinaryOperator.ADD && isExactNumber(leftType) && isDate(rightType)) {
            return dateAdd(IntervalUnit.DAY, left.accept(this), leftType, right);
        }
        if (operator == BinaryOperator.SUBTRACT && isDate(leftType) && isExactNumber(rightType)) {
            return dateAdd(IntervalUnit.DAY, "NEGATE(" + right.accept(this) + ")", rightType, left);
        }
        return null;
    }

    /**
     * A subtraction of two dates or of two timestamps as the plan holds it, the operands SWAPPED:
     * {@code d - d2} is {@code DATE_DIFFDATEINDAYS(FT.D2, FT.D)} and {@code ts - ts2} is
     * {@code DATE_DIFFTIMESTAMPTOINTERVAL(FT.TS2, FT.TS)}, each timestamp first moved to the pair's
     * common type ({@link #commonTimestamp}). Null for any other pair, which the account refuses.
     */
    private String dateDifference(final Expression left, final DataType leftType,
                                  final Expression right, final DataType rightType) {
        if (isDate(leftType) && isDate(rightType)) {
            return "DATE_DIFFDATEINDAYS(" + right.accept(this) + ", " + left.accept(this) + ")";
        }
        if (isTimestamp(leftType) && isTimestamp(rightType)) {
            final String common = commonTimestamp(leftType, rightType);
            return "DATE_DIFFTIMESTAMPTOINTERVAL(" + movedTo(right, rightType, common) + ", "
                + movedTo(left, leftType, common) + ")";
        }
        return null;
    }

    /**
     * The TIMESTAMP two temporal operands meet in: the widest flavour either one carries — TIMESTAMP_TZ
     * over TIMESTAMP_LTZ over TIMESTAMP_NTZ, and TIMESTAMP_NTZ when neither is a timestamp — at the
     * plan's precision, {@code TIMESTAMP_LTZ(9)} for an NTZ beside an LTZ whatever their own precisions.
     */
    private static String commonTimestamp(final DataType first, final DataType second) {
        final int rank = Math.max(flavourRank(first), flavourRank(second));
        return (rank == 2 ? "TIMESTAMP_TZ" : rank == 1 ? "TIMESTAMP_LTZ" : "TIMESTAMP_NTZ")
            + PLAN_TIMESTAMP_PRECISION;
    }

    private static int flavourRank(final DataType type) {
        if (!isTimestamp(type)) {
            return 0;
        }
        final String flavour = timestampFlavour(type);
        return "TIMESTAMP_TZ".equals(flavour) ? 2 : "TIMESTAMP_LTZ".equals(flavour) ? 1 : 0;
    }

    /** The precision every TIMESTAMP the plan converts to carries. */
    private static final String PLAN_TIMESTAMP_PRECISION = "(9)";

    /** The text target the plan converts a value to when a text is what it needs. */
    private static final String TEXT_TARGET = "VARCHAR(" + DataTypeParser.CAST_STRING_DEFAULT + ")";

    /**
     * An operand moved to a temporal target, named by its full type text — {@code DATE} or a TIMESTAMP at
     * {@link #PLAN_TIMESTAMP_PRECISION}: bare when its own type is already that one, converted otherwise
     * ({@link #converted}).
     */
    private String movedTo(final Expression operand, final DataType type, final String target) {
        final String printed = operand.accept(this);
        if (target.equals(typeText(operand)) || ("DATE".equals(target) && isDate(type))) {
            return printed;
        }
        return converted(printed, target);
    }

    /**
     * A value converted to one of the plan's own targets: {@code CAST(x AS T)} in the plan, and in an
     * invalid-type sentence the function the conversion is planned as — {@code TO_DATE(x)},
     * {@code TO_TIMESTAMP_LTZ(x)}, {@code TO_CHAR(x)} for the text target.
     */
    private String converted(final String printed, final String target) {
        final String function = conversionShaped() ? conversionFunctionOf(target) : null;
        return function != null ? function + "(" + printed + ")" : "CAST(" + printed + " AS " + target + ")";
    }

    private static String conversionFunctionOf(final String target) {
        switch (target) {
            case "DATE":
                return "TO_DATE";
            case "TIMESTAMP_NTZ" + PLAN_TIMESTAMP_PRECISION:
                return "TO_TIMESTAMP_NTZ";
            case "TIMESTAMP_LTZ" + PLAN_TIMESTAMP_PRECISION:
                return "TO_TIMESTAMP_LTZ";
            case "TIMESTAMP_TZ" + PLAN_TIMESTAMP_PRECISION:
                return "TO_TIMESTAMP_TZ";
            case TEXT_TARGET:
                return "TO_CHAR";
            default:
                return null;
        }
    }

    /**
     * An operand of an infix operator: bare for a column, a literal, a window call, a star call or a
     * variable, bracketed otherwise — {@code COUNT(*) + 1} beside {@code (MAX(RT.G)) > 'a'}.
     */
    private String operand(final Expression expression) {
        if (expression instanceof BindVariableExpression
                && blockExpressionConversion((BindVariableExpression) expression) != null
                || convertedCallType(expression) != null) {
            return "(" + expression.accept(this) + ")";
        }
        if (tellingDefault && expression instanceof BinaryOperationExpression
                && ((BinaryOperationExpression) expression).isWrittenInParentheses()
                && isArithmetic(((BinaryOperationExpression) expression).getOperator())) {
            final String type = typeText(expression);
            if (type != null) {
                return "(CAST(" + expression.accept(this) + " AS " + type + "))";
            }
        }
        final String printed = expression.accept(this);
        if (typesNulls() && isUntypedNull(expression)) {
            return printed.startsWith("SYSTEM$NULL_TO_") ? "(" + printed + ")" : printed;
        }
        return bareAsOperand(expression) || printsAsCorrelatedCall(expression)
            || planShaped() && (isNiladicCall(expression) || isSoleVariableRead(expression)
                || isNegatedNumber(expression) && printed.startsWith("-"))
            || expression instanceof UnaryOperationExpression
                && negatedNegativeNumber((UnaryOperationExpression) expression) != null
            ? printed : "(" + printed + ")";
    }

    /**
     * A minus written before a number, which the plan holds as the negative number it is: an operand the
     * plan prints bare, {@code RT.N + -5} and {@code -1.5 * RT.N} (live-verified).
     */
    private static boolean isNegatedNumber(final Expression expression) {
        return expression instanceof UnaryOperationExpression
            && ((UnaryOperationExpression) expression).getOperator() == UnaryOperator.NEGATE
            && isNumericLiteral(((UnaryOperationExpression) expression).getOperand());
    }

    /** Whether an operand prints as one correlated value — the plan reads it bare, as it reads a column. */
    private boolean printsAsCorrelatedCall(final Expression expression) {
        return (correlationScope != null || SubqueryCompilation.outerScope() != null)
            && expression instanceof FunctionCallExpression && readsOuterAlone((FunctionCallExpression) expression);
    }

    /**
     * An infix operation over the bare word NULL as the plan types it: an arithmetic operand as the other
     * operand's family, {@code (SYSTEM$NULL_TO_FIXED(null)) + 1} and {@code RT.F + (SYSTEM$NULL_TO_REAL(null))}, a
     * concatenation's as text and a logical one's as BOOLEAN. Null when neither operand is an untyped NULL, and
     * for a division, which the plan also rescales.
     */
    private String withTypedNullOperands(final BinaryOperator operator, final Expression left, final DataType leftType,
                                         final Expression right, final DataType rightType) {
        final boolean leftNull = isNullValue(left);
        final boolean rightNull = isNullValue(right);
        if (!leftNull && !rightNull) {
            return null;
        }
        final String leftFamily;
        final String rightFamily;
        if (operator == BinaryOperator.CONCAT) {
            leftFamily = "TEXT";
            rightFamily = "TEXT";
        } else if (operator == BinaryOperator.AND || operator == BinaryOperator.OR) {
            leftFamily = "BOOLEAN";
            rightFamily = "BOOLEAN";
        } else if (operator == BinaryOperator.ADD || operator == BinaryOperator.SUBTRACT
                || operator == BinaryOperator.MULTIPLY || operator == BinaryOperator.MODULO
                || operator == BinaryOperator.DIVIDE) {
            leftFamily = leftNull && rightNull ? "FIXED" : leftNull ? operandFamily(rightType) : null;
            rightFamily = leftNull && rightNull ? "FIXED" : rightNull ? operandFamily(leftType) : null;
            if (leftNull && leftFamily == null || rightNull && rightFamily == null) {
                return null;
            }
            if (operator == BinaryOperator.DIVIDE && !"REAL".equals(leftFamily) && !"REAL".equals(rightFamily)) {
                // An exact division rescales its dividend whatever typed it: (CAST(SYSTEM$NULL_TO_FIXED(null) AS
                // NUMBER(24,6))) / 2, and past 38 digits SCALED_ROUND_INT_DIVIDE(RT.N, SYSTEM$NULL_TO_FIXED(null));
                // the invalid-type sentence spells the rescaling FIXED_TO_FIXED (live-verified).
                return exactDivision(typedArgument(left, leftFamily), leftNull ? TYPED_NULL_FIXED : (NumericType) leftType,
                    typedArgument(right, rightFamily), typedOperand(right, rightFamily),
                    rightNull ? TYPED_NULL_FIXED : (NumericType) rightType);
            }
        } else if (conversionShaped() && isComparison(operator)) {
            // A comparison's NULL is typed by the number beside it in the invalid-type sentence.
            leftFamily = leftNull ? operandFamily(rightType) : null;
            rightFamily = rightNull ? operandFamily(leftType) : null;
            if (leftNull && leftFamily == null || rightNull && rightFamily == null) {
                return null;
            }
        } else {
            return null;
        }
        if (operator == BinaryOperator.CONCAT) {
            // The other operand is text as any concatenation's is: (SYSTEM$NULL_TO_TEXT(null)) || (CAST(RT.N AS
            // VARCHAR(134217728))) (live-verified).
            return (leftNull ? typedOperand(left, leftFamily) : asText(left, leftType)) + " || "
                + (rightNull ? typedOperand(right, rightFamily) : asText(right, rightType));
        }
        return typedOperand(left, leftFamily) + " " + symbol(operator) + " " + typedOperand(right, rightFamily);
    }

    /** The number the plan types an untyped NULL as beside an exact one: {@code SYSTEM$NULL_TO_FIXED(null)} is NUMBER(18,0). */
    private static final NumericType TYPED_NULL_FIXED = new NumericType("NUMBER", 18, 0);

    /**
     * The family an arithmetic operand of this type gives an untyped NULL beside it: FIXED for an exact number
     * of any scale, {@code (SYSTEM$NULL_TO_FIXED(null)) - RT.N52}, REAL for a FLOAT.
     */
    private static String operandFamily(final DataType type) {
        if (type instanceof NumericType) {
            return NumericType.isApproximate(type) ? "REAL" : "FIXED";
        }
        return null;
    }

    /** An operand printed with an untyped NULL planned as {@code family}. */
    private String typedOperand(final Expression operand, final String family) {
        final String previous = nullFamily;
        nullFamily = isNullValue(operand) ? family : null;
        try {
            return operand(operand);
        } finally {
            nullFamily = previous;
        }
    }

    /** Whether a condition is the NOT of a call folding to NULL, which the plan converts beneath the NOT. */
    private boolean negatesAFoldedNull(final Expression condition) {
        return planShaped() && condition instanceof UnaryOperationExpression
            && ((UnaryOperationExpression) condition).getOperator() == UnaryOperator.NOT
            && foldsToNullByCall(((UnaryOperationExpression) condition).getOperand());
    }

    /** Whether an expression is a call that folds to an untyped NULL, and not the bare word itself. */
    private boolean foldsToNullByCall(final Expression expression) {
        return !isUntypedNullLiteral(expression) && UntypedNullFold.foldsToUntypedNull(expression);
    }

    private static boolean isUntypedNullLiteral(final Expression expression) {
        return expression instanceof LiteralExpression && ((LiteralExpression) expression).getType() == LiteralType.NULL;
    }

    /** An argument printed with an untyped NULL planned as {@code family}. */
    private String typedArgument(final Expression argument, final String family) {
        final String previous = nullFamily;
        nullFamily = isNullValue(argument) ? family : null;
        try {
            return argument.accept(this);
        } finally {
            nullFamily = previous;
        }
    }

    /** A call with no argument at all, {@code PI()}, which the plan prints bare as an operand. */
    private static boolean isNiladicCall(final Expression expression) {
        return expression instanceof FunctionCallExpression && !((FunctionCallExpression) expression).isStar()
            && ((FunctionCallExpression) expression).getArguments().isEmpty()
            && ((FunctionCallExpression) expression).getNameExpression() == null;
    }

    /** {@link #operand} for a caller outside this printer: the subquery re-print names its subject so. */
    String operandText(final Expression expression) {
        return operand(expression);
    }

    private static boolean bareAsOperand(final Expression expression) {
        return expression instanceof ColumnReferenceExpression || expression instanceof LiteralExpression
            || expression instanceof WindowFunctionExpression || expression instanceof SessionVarExpression
            || expression instanceof BindVariableExpression || expression instanceof SubqueryExpression
            || (expression instanceof FunctionCallExpression && ((FunctionCallExpression) expression).isStar());
    }

    private static boolean isComparison(final BinaryOperator operator) {
        switch (operator) {
            case EQUAL:
            case NOT_EQUAL:
            case LESS_THAN:
            case LESS_THAN_OR_EQUAL:
            case GREATER_THAN:
            case GREATER_THAN_OR_EQUAL:
                return true;
            default:
                return false;
        }
    }

    private static boolean isArithmetic(final BinaryOperator operator) {
        switch (operator) {
            case ADD:
            case SUBTRACT:
            case MULTIPLY:
            case DIVIDE:
            case MODULO:
                return true;
            default:
                return false;
        }
    }

    /** How many integer digits an ARITHMETIC operand's rescale keeps at least. */
    private static final int ARITHMETIC_INTEGER_DIGITS = 2;

    /**
     * The lower-scale operand cast to the higher scale, at the wider of its grown precision and the
     * other's. An ARITHMETIC operand keeps {@link #ARITHMETIC_INTEGER_DIGITS} integer digits at least:
     * the 2 of {@code 2 + 0.5} is rescaled to NUMBER(3,1) and the 1 of {@code 1 + 0.25} to
     * NUMBER(4,2), where the 2 of the comparison {@code 2 > 0.5} is rescaled to NUMBER(2,1).
     */
    private String rescaled(final Expression lower, final NumericType lowType, final NumericType highType,
                            final boolean arithmetic) {
        final int growth = highType.getScale() - lowType.getScale();
        final int widened = Math.max(lowType.getPrecision() + growth, highType.getPrecision());
        final int precision = arithmetic
            ? Math.max(widened, ARITHMETIC_INTEGER_DIGITS + highType.getScale()) : widened;
        return "(" + fixedToFixed(lower.accept(this),
            "NUMBER(" + Math.min(MAX_PRECISION, precision) + "," + highType.getScale() + ")") + ")";
    }

    /** An exact number's move to another width: a CAST in the plan, FIXED_TO_FIXED in an invalid-type sentence. */
    private String fixedToFixed(final String value, final String target) {
        return conversionShaped()
            ? "FIXED_TO_FIXED(" + value + " AS " + target + "[UNKNOWN])"
            : "CAST(" + value + " AS " + target + ")";
    }

    private String asFloat(final Expression expression) {
        return doublesAsConversions()
            ? "(TO_DOUBLE(" + expression.accept(this) + "))"
            : "(CAST(" + expression.accept(this) + " AS FLOAT))";
    }

    /**
     * A concatenation's operand as text. A concatenation inside one is part of the same chain, which the plan
     * holds flat whichever way it nests: {@code g || g || g} and {@code g || (g || g)} are both
     * {@code RT.G || RT.G || RT.G} (live-verified).
     */
    private String asText(final Expression expression, final DataType type) {
        if (expression instanceof BinaryOperationExpression
                && ((BinaryOperationExpression) expression).getOperator() == BinaryOperator.CONCAT) {
            return expression.accept(this);
        }
        if (type == null || type instanceof StringType) {
            return operand(expression);
        }
        final String converted = conversionShaped() ? textConversion(type) : null;
        if (converted != null) {
            return "(" + converted + "(" + expression.accept(this) + "))";
        }
        return "(CAST(" + expression.accept(this) + " AS VARCHAR(" + DataTypeParser.CAST_STRING_DEFAULT + ")))";
    }

    /**
     * The function an invalid-type sentence converts a value to text with beside a concatenation: TO_CHAR for a
     * number or a date and time, BOOLEAN_TO_TEXT for a BOOLEAN — {@code (TO_CHAR(1)) || (TO_CHAR(2))},
     * {@code (BOOLEAN_TO_TEXT(TRUE)) || 'a'} (live-verified). Null for a type not measured.
     */
    private static String textConversion(final DataType type) {
        if (type instanceof BooleanType) {
            return "BOOLEAN_TO_TEXT";
        }
        return type instanceof NumericType || type instanceof DateTimeType ? "TO_CHAR" : null;
    }

    /** The number a text operand reads as beside an exact one, before the two meet. */
    private static final NumericType TEXT_AS_NUMBER = new NumericType("NUMBER", 18, 5);

    /** A text VALUE — not a literal, which is typed by its own decimals, and no UUID — as an operand's type says. */
    private static boolean isTextValue(final Expression expression, final DataType type) {
        return type instanceof StringType && !(type instanceof UuidType) && !(expression instanceof LiteralExpression);
    }

    /** A written text literal. */
    private static boolean isTextLiteral(final Expression expression, final DataType type) {
        return type instanceof StringType && expression instanceof LiteralExpression
            && ((LiteralExpression) expression).getType() == LiteralType.STRING;
    }

    /** A text operand, a value or a written literal. */
    private static boolean isTextOperand(final Expression expression, final DataType type) {
        return isTextValue(expression, type) || isTextLiteral(expression, type);
    }

    /**
     * The written text of a text literal that spells a number with nothing about it ({@link PlanTextNumber}), which
     * reads at its own width; null for any other operand — a text with a blank about it, or with no number, reads
     * as a text value does.
     */
    private static String spelledNumberText(final Expression operand) {
        if (!(operand instanceof LiteralExpression) || ((LiteralExpression) operand).getType() != LiteralType.STRING) {
            return null;
        }
        final String text = String.valueOf(((LiteralExpression) operand).getValue());
        return PlanTextNumber.spelled(text) != null ? text : null;
    }

    /** A text converted to a number of this width; the default NUMBER(38,0) is not spelled. */
    private static String textToNumber(final String text, final int precision, final int scale) {
        return precision == MAX_PRECISION && scale == 0
            ? "TO_NUMBER(" + text + ")" : "TO_NUMBER(" + text + ", " + precision + ", " + scale + ")";
    }

    /**
     * An arithmetic or a comparison over a text operand as the plan converts it (live-verified). Two texts,
     * or a text beside a FLOAT, meet as FLOAT: {@code (CAST(RT.G AS FLOAT)) + (CAST(RT.G AS FLOAT))},
     * {@code RT.F + (CAST(RT.G AS FLOAT))}. A text value beside an exact number reads as NUMBER(18,5), and the
     * two meet in their common width — the integer digits of the wider, the larger scale, 38 digits at most —
     * the text converted and the number rescaled only where its scale differs:
     * {@code (CAST(1 AS NUMBER(18,5))) + (TO_NUMBER(RT.G, 18, 5))},
     * {@code (CAST(RT.N AS NUMBER(38,5))) - (TO_NUMBER(RT.G, 38, 5))}, {@code 1.123456 + (TO_NUMBER(RT.G, 19, 6))},
     * {@code (TO_NUMBER(RT.G, 20, 7)) = RT.N107}. A text LITERAL spelling a number reads at its own width
     * ({@link PlanTextNumber}: {@code '1.50'} NUMBER(18,1), {@code '1e2'} NUMBER(18,0)) and meets in the larger
     * precision and the larger scale: {@code (TO_NUMBER('5')) + RT.N}, {@code (TO_NUMBER('5', 18, 2)) + RT.N52},
     * {@code (TO_NUMBER('5.5', 38, 1)) + (CAST(RT.N AS NUMBER(38,1)))}; one with a blank about it or with no
     * number reads as a text value does, {@code RT.N * (TO_NUMBER(' 1 ', 18, 5))}.
     * A product converts the text alone to the number it reads as, {@code (TO_NUMBER(RT.G, 18, 5)) * RT.N}, and
     * a quotient is the exact division of that number, {@code (CAST(TO_NUMBER(RT.G, 18, 5) AS NUMBER(24,11))) / 2}.
     * Null for any other shape, a comparison of two texts among them.
     */
    private String textOperandMeeting(final BinaryOperator operator, final Expression left, final DataType leftType,
                                      final Expression right, final DataType rightType) {
        final boolean leftText = isTextOperand(left, leftType);
        final boolean rightText = isTextOperand(right, rightType);
        if (leftText == rightText) {
            return leftText && isArithmetic(operator)
                ? asFloat(left) + " " + symbol(operator) + " " + asFloat(right) : null;
        }
        final Expression text = leftText ? left : right;
        final Expression other = leftText ? right : left;
        final DataType otherType = leftText ? rightType : leftType;
        if (!(otherType instanceof NumericType) || isUntypedNull(other)) {
            return null;
        }
        final NumericType number = (NumericType) otherType;
        if (NumericType.isApproximate(number)) {
            return leftText ? asFloat(left) + " " + symbol(operator) + " " + operand(right)
                : operand(left) + " " + symbol(operator) + " " + asFloat(right);
        }
        final String spelled = spelledNumberText(text);
        final boolean literal = spelled != null;
        final NumericType textType = literal ? PlanTextNumber.type(spelled) : TEXT_AS_NUMBER;
        final String textValue = text.accept(this);
        if (operator == BinaryOperator.MULTIPLY || operator == BinaryOperator.DIVIDE) {
            final String asNumber = textToNumber(textValue, textType.getPrecision(), textType.getScale());
            if (operator == BinaryOperator.MULTIPLY) {
                return leftText ? "(" + asNumber + ") * " + operand(right) : operand(left) + " * (" + asNumber + ")";
            }
            return leftText ? exactDivision(asNumber, textType, right.accept(this), operand(right), number)
                : exactDivision(left.accept(this), number, asNumber, "(" + asNumber + ")", textType);
        }
        final int scale = Math.max(number.getScale(), textType.getScale());
        final int precision = literal
            ? Math.min(MAX_PRECISION, Math.max(number.getPrecision(), textType.getPrecision()))
            : Math.min(MAX_PRECISION, Math.max(number.getPrecision() - number.getScale(),
                textType.getPrecision() - textType.getScale()) + scale);
        final String textSide = "(" + textToNumber(textValue, precision, scale) + ")";
        final String numberSide = number.getScale() == scale ? operand(other)
            : "(" + fixedToFixed(other.accept(this), "NUMBER(" + precision + "," + scale + ")") + ")";
        return leftText ? textSide + " " + symbol(operator) + " " + numberSide
            : numberSide + " " + symbol(operator) + " " + textSide;
    }

    /**
     * An exact division as the plan holds it: the dividend rescaled to the quotient's width — the
     * scale grows by six, to a cap of twelve and never narrowing, and the precision keeps the
     * dividend's integer digits plus the divisor's scale plus that — or, once that width passes 38
     * digits, live's internal {@code SCALED_ROUND_INT_DIVIDE(x, y)} with both operands bare. The
     * plain AVG is this very division of SUM by COUNT, which is why one rule serves both.
     */
    private String exactDivision(final String dividend, final NumericType l, final String divisor,
                                 final String divisorOperand, final NumericType r) {
        final int scale = Math.max(l.getScale(),
            Math.min(l.getScale() + r.getScale() + DIVISION_EXTRA_SCALE, DIVISION_SCALE_CAP));
        final int precision = l.getPrecision() - l.getScale() + r.getScale() + scale;
        if (precision > MAX_PRECISION) {
            return "SCALED_ROUND_INT_DIVIDE(" + dividend + ", " + divisor + ")";
        }
        return "(" + fixedToFixed(dividend, "NUMBER(" + precision + "," + scale + ")") + ") / " + divisorOperand;
    }

    /**
     * The unary operators as the plan names them — {@code NEGATE(x)}, {@code UNARY PLUS(x)} and
     * {@code NOT(x)}, the operand bare inside — measured on the nesting, arity and window-specification
     * surfaces alike. A minus written before a number is part of the NUMBER, not an operator: {@code -1}
     * prints {@code -1} and {@code -(1)} does too, where {@code -a} is {@code NEGATE(RE.A)}; a minus before
     * such a minus is folded with it, {@code -(-1)} printing {@code 1}. A plus is an operator even there:
     * {@code UNARY PLUS(1)}. A sign moves a text or a VARIANT to FLOAT first,
     * {@code NEGATE(CAST(RT.G AS FLOAT))}, {@code UNARY PLUS(TO_DOUBLE(RT.G))} in an invalid-type sentence
     * (all live-verified). A doubled NOT is folded away in a plan-shaped message
     * ({@code BOOLOR_AGG(NOT NOT MAX(b))} is {@code MAX(MAX(T.B))}) and kept elsewhere ({@code NOT(NOT(T.B))}).
     */
    @Override
    public String visitUnaryOperation(final UnaryOperationExpression expr) {
        switch (expr.getOperator()) {
            case NEGATE:
            case PLUS: {
                final Expression operand = expr.getOperand();
                final String name = expr.getOperator() == UnaryOperator.NEGATE ? "NEGATE" : "UNARY PLUS";
                if (typesNulls() && isNullValue(operand)) {
                    // NEGATE(SYSTEM$NULL_TO_FIXED(null)), a conditional folding to NULL with its text inside.
                    return name + "(" + typedArgument(operand, "FIXED") + ")";
                }
                if (expr.getOperator() == UnaryOperator.NEGATE) {
                    final String number = negatedNegativeNumber(expr);
                    if (number != null) {
                        return number;
                    }
                    if (isNumericLiteral(operand)) {
                        final String printed = operand.accept(this);
                        return printed.startsWith("-") ? "NEGATE(" + printed + ")" : "-" + printed;
                    }
                }
                return name + "(" + signOperand(operand) + ")";
            }
            case NOT: {
                final Expression operand = expr.getOperand();
                if (planShaped() && operand instanceof UnaryOperationExpression
                        && ((UnaryOperationExpression) operand).getOperator() == UnaryOperator.NOT) {
                    return ((UnaryOperationExpression) operand).getOperand().accept(this);
                }
                if (conversionShaped() && operand instanceof UnaryOperationExpression
                        && ((UnaryOperationExpression) operand).getOperator() == UnaryOperator.NOT) {
                    // A doubled NOT over what is no condition is the condition it converts: NOT NOT n is
                    // TO_BOOLEAN(RT.N) (live-verified).
                    final String converted = asCondition(((UnaryOperationExpression) operand).getOperand());
                    if (converted != null) {
                        return converted;
                    }
                }
                final String notExists = notExists(operand);
                if (notExists == null && planShaped() && foldsToNullByCall(operand)) {
                    // What NOT negates is a condition: NOT(SYSTEM$NULL_TO_ROWINDEX(NVL(CAST(null AS NULL), null))).
                    return "NOT(" + typedArgument(operand, "ROWINDEX") + ")";
                }
                final String condition = notExists == null ? asCondition(operand) : null;
                return notExists != null ? notExists
                    : "NOT(" + (condition != null ? condition : operand.accept(this)) + ")";
            }
            case EXISTS: {
                // The plan spells the test by its own word over the subquery's re-print, without the
                // parentheses a scalar subquery carries: EXISTS(SELECT 1 AS "1" FROM G AS G2 WHERE …).
                final String select = !planShaped() || !(expr.getOperand() instanceof SubqueryExpression) ? null
                    : new SubqueryPlanPrint(context, mode).select((SubqueryExpression) expr.getOperand());
                return select != null ? "EXISTS(" + select + ")" : super.visitUnaryOperation(expr);
            }
            default:
                return super.visitUnaryOperation(expr);
        }
    }

    /**
     * NOT over an EXISTS, which the plan spells as one test over the subquery's re-print:
     * {@code NOT EXISTS(SELECT 1 AS "1" FROM (VALUES (null)) DUAL)} (live-verified); null for any other operand, or a
     * subquery whose re-print is not modelled.
     */
    private String notExists(final Expression operand) {
        if (!planShaped() || !(operand instanceof UnaryOperationExpression)
                || ((UnaryOperationExpression) operand).getOperator() != UnaryOperator.EXISTS
                || !(((UnaryOperationExpression) operand).getOperand() instanceof SubqueryExpression)) {
            return null;
        }
        final String select = new SubqueryPlanPrint(context, mode)
            .select((SubqueryExpression) ((UnaryOperationExpression) operand).getOperand());
        return select == null ? null : "NOT EXISTS(" + select + ")";
    }

    /**
     * A minus before a minus before a number, which the plan folds into the number it makes: {@code -(-1)} is
     * {@code 1} and {@code -(-(-1))} {@code -1} (live-verified); null for any other operation.
     */
    private String negatedNegativeNumber(final UnaryOperationExpression expr) {
        int negations = 0;
        Expression at = expr;
        while (at instanceof UnaryOperationExpression
                && ((UnaryOperationExpression) at).getOperator() == UnaryOperator.NEGATE) {
            negations++;
            at = ((UnaryOperationExpression) at).getOperand();
        }
        if (negations < 2 || !isNumericLiteral(at)) {
            return null;
        }
        final String printed = at.accept(this);
        if (printed.startsWith("-")) {
            return null;
        }
        return negations % 2 == 0 ? printed : "-" + printed;
    }

    /** A sign's operand, a text or a VARIANT moved to FLOAT first as the sign moves it. */
    private String signOperand(final Expression operand) {
        final DataType type = argumentType(operand);
        final boolean floated = type instanceof StringType && !(type instanceof UuidType) || type instanceof VariantType;
        if (!floated) {
            return operand.accept(this);
        }
        return doublesAsConversions() ? "TO_DOUBLE(" + operand.accept(this) + ")"
            : "CAST(" + operand.accept(this) + " AS FLOAT)";
    }

    /**
     * What NOT reads as the condition it negates, where the plan converts it: the bare word NULL is
     * {@code BOOLEAN_TO_ROWINDEX(NULL)} in an invalid-type sentence and {@code CAST(null AS BOOLEAN)} in the other
     * plan-shaped ones, and a number, a text or a VARIANT {@code TO_BOOLEAN(RT.N)} and {@code CAST(RT.N AS BOOLEAN)}
     * (live-verified). Null where nothing is converted.
     */
    private String asCondition(final Expression operand) {
        if (mode == StrictPrintMode.WRITTEN) {
            return null;
        }
        if (isUntypedNullLiteral(operand)) {
            return conversionShaped() ? "BOOLEAN_TO_ROWINDEX(NULL)" : "CAST(null AS BOOLEAN)";
        }
        final DataType type = argumentType(operand);
        if (!(type instanceof NumericType) && !(type instanceof StringType) && !(type instanceof VariantType)
                || type instanceof UuidType) {
            return null;
        }
        return conversionShaped() ? "TO_BOOLEAN(" + operand.accept(this) + ")"
            : "CAST(" + operand.accept(this) + " AS BOOLEAN)";
    }

    /**
     * {@code x IS NULL} bare; as an operand it is bracketed by the operator that holds it. The bare word NULL tested
     * so is the plan's NULL of the NULL type, {@code COERCE(NULL AS NULL) IS NULL} in an invalid-type sentence and
     * {@code CAST(null AS NULL) IS NULL} in the other plan-shaped ones (live-verified).
     */
    @Override
    public String visitIsNull(final IsNullExpression expr) {
        final String tested = mode != StrictPrintMode.WRITTEN && isUntypedNullLiteral(expr.getOperand())
            ? (conversionShaped() ? "COERCE(NULL AS NULL)" : NULL_TYPE_CAST) : expr.getOperand().accept(this);
        return tested + (expr.isNot() ? " IS NOT NULL" : " IS NULL");
    }

    /** BETWEEN as the plan holds it: the two comparisons it stands for, each with its own conversions. */
    @Override
    public String visitBetween(final BetweenExpression expr) {
        if (expr.isNot()) {
            return super.visitBetween(expr);
        }
        final BinaryOperationExpression low = new BinaryOperationExpression(expr.getValue(),
            BinaryOperator.GREATER_THAN_OR_EQUAL, expr.getLower());
        final BinaryOperationExpression high = new BinaryOperationExpression(expr.getValue(),
            BinaryOperator.LESS_THAN_OR_EQUAL, expr.getUpper());
        return "(" + low.accept(this) + ") AND (" + high.accept(this) + ")";
    }

    /**
     * {@code (x) IN (a, b)}: the subject an operand, the list bare. The plan holds a NOT IN as the NOT
     * of the membership test, {@code NOT('b' IN ('a', 'b'))}, and an invalid-type sentence spells the
     * list as a chain of memberships, {@code 'b' IN 'a' IN 'b'}. A membership test over a SUBQUERY is
     * the comparison it is planned as, the subquery re-printed from its plan — see
     * {@link SubqueryPlanPrint} — or as written where that re-print is not modelled.
     */
    @Override
    public String visitIn(final InExpression expr) {
        if (expr.hasSubquery()) {
            final String planned = mode == StrictPrintMode.WRITTEN ? null
                : new SubqueryPlanPrint(context, mode).membership(expr, this);
            return planned != null ? planned : super.visitIn(expr);
        }
        final List<Expression> values = expr.getValues();
        final String subject = operand(expr.getValue());
        if (conversionShaped() && !expr.isNot()) {
            final StringBuilder chain = new StringBuilder(subject);
            for (final Expression value : values) {
                chain.append(" IN ").append(value.accept(this));
            }
            return chain.toString();
        }
        final StringBuilder list = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            list.append(i > 0 ? ", " : "").append(values.get(i).accept(this));
        }
        if (!expr.isNot()) {
            return subject + " IN (" + list + ")";
        }
        return planShaped() ? "NOT(" + subject + " IN (" + list + "))" : subject + " NOT IN (" + list + ")";
    }

    /**
     * A window call reached INSIDE an expression, re-printed the way the plan spells it — every key
     * qualified, each sort key with its direction and null placement, the frame and the null
     * treatment dropped — rather than as the source text it was written in: {@code ROW_NUMBER() OVER
     * (PARTITION BY T.I ORDER BY T.N DESC NULLS FIRST)}. A call whose OVER clause was never described
     * (a star call) keeps its text.
     */
    @Override
    public String visitWindowFunction(final WindowFunctionExpression expr) {
        if (expr.getPartitionKeys() == null || expr.getFunctionName() == null) {
            return super.visitWindowFunction(expr);
        }
        final StringBuilder text = new StringBuilder(expr.getFunctionName()).append('(');
        if (expr.isDistinct()) {
            text.append("DISTINCT ");
        }
        final List<Expression> args = expr.getArguments();
        for (int i = 0; i < args.size(); i++) {
            text.append(i > 0 ? ", " : "").append(args.get(i).accept(this));
        }
        text.append(") OVER (");
        final List<Expression> partition = expr.getPartitionKeys();
        for (int i = 0; i < partition.size(); i++) {
            text.append(i == 0 ? "PARTITION BY " : ", ").append(partition.get(i).accept(this));
        }
        final List<Expression> order = expr.getOrderKeys();
        for (int i = 0; i < order.size(); i++) {
            final boolean ascending = expr.getOrderAscending().get(i).booleanValue();
            final Boolean nullsFirst = expr.getOrderNullsFirst().get(i);
            text.append(i == 0 ? (partition.isEmpty() ? "ORDER BY " : " ORDER BY ") : ", ")
                .append(order.get(i).accept(this))
                .append(ascending ? " ASC NULLS " : " DESC NULLS ")
                .append((nullsFirst != null ? nullsFirst.booleanValue() : !ascending) ? "FIRST" : "LAST");
        }
        return text.append(')').toString();
    }

    /**
     * A CASE as the plan holds it. A searched CASE is {@code CASE_FLATTENED(<condition>, <result>, …, <else>)},
     * each condition a predicate or {@code CAST(x AS BOOLEAN)}; a simple CASE keeps its shape,
     * {@code CASE RT.N WHEN CAST(1 AS NUMBER(38,0)) THEN 'x' ELSE 'y' END}, each value converted to the subject's
     * type where two exact numbers differ. A missing ELSE, and an untyped NULL result, is the results' typed null,
     * {@code SYSTEM$NULL_TO_FIXED(null)}. An invalid-type sentence plans a searched CASE alike, in its own spellings:
     * {@code CASE_FLATTENED(BOOLEAN_TO_ROWINDEX(TRUE), COERCE(NULL AS NULL), NULL)}, {@code CASE_FLATTENED(RT.N = 1,
     * SYSTEM$NULL_TO_FIXED(NULL), 2)} (live-verified). The written mode, a simple CASE in an invalid-type sentence
     * and a CASE whose results have no typed null print as written.
     */
    @Override
    public String visitCaseExpression(final CaseExpression expr) {
        if (!planShaped() && !conversionShaped() || expr.getWhenClauses().isEmpty()
                || conversionShaped() && !isSearched(expr)) {
            return super.visitCaseExpression(expr);
        }
        if (nullFamily != null && isFoldedConditional(expr)) {
            // Converted where it stands, a CASE of NULL results keeps its planned text inside the typed NULL:
            // (SYSTEM$NULL_TO_FIXED(CASE_FLATTENED(RT.N > 0, CAST(null AS NULL), null))) + 1 (live-verified).
            final String family = nullFamily;
            nullFamily = null;
            try {
                return "SYSTEM$NULL_TO_" + family + "(" + visitCaseExpression(expr) + ")";
            } finally {
                nullFamily = family;
            }
        }
        final List<Expression> results = new ArrayList<>();
        boolean simple = true;
        for (final WhenClause when : expr.getWhenClauses()) {
            results.add(when.getResult());
            simple = simple && when.isOperandMatch() && when.getCondition() instanceof BinaryOperationExpression;
        }
        if (expr.getElseExpression() != null) {
            results.add(expr.getElseExpression());
        }
        final String family = meetingFamily(results);
        if (family == null) {
            return UntypedNullFold.foldsToUntypedNull(expr) ? nullCase(expr, simple) : super.visitCaseExpression(expr);
        }
        final List<Expression> valued = new ArrayList<>();
        for (final Expression result : results) {
            if (!isUntypedNull(result)) {
                valued.add(result);
            }
        }
        // Numeric results meet in one number: CASE_FLATTENED(RT.N > 0, RT.F, CAST(1 AS FLOAT)) (live-verified).
        final NumericType met = numericMeeting(valued);
        final String elseText = expr.getElseExpression() == null ? "SYSTEM$NULL_TO_" + family + "(null)"
            : caseResult(expr.getElseExpression(), family, met);
        if (simple) {
            final Expression subject = ((BinaryOperationExpression) expr.getWhenClauses().get(0).getCondition()).getLeft();
            final DataType subjectType = argumentType(subject);
            final StringBuilder text = new StringBuilder("CASE ").append(subject.accept(this));
            for (final WhenClause when : expr.getWhenClauses()) {
                text.append(" WHEN ").append(caseValue(subjectType, when))
                    .append(" THEN ").append(caseResult(when.getResult(), family, met));
            }
            return text.append(" ELSE ").append(elseText).append(" END").toString();
        }
        final StringBuilder text = new StringBuilder("CASE_FLATTENED(");
        for (final WhenClause when : expr.getWhenClauses()) {
            text.append(conditionText(when.getCondition(), true)).append(", ")
                .append(caseResult(when.getResult(), family, met)).append(", ");
        }
        return text.append(elseText).append(')').toString();
    }

    /** Whether a CASE is searched: no WHEN of it matches a subject. */
    private static boolean isSearched(final CaseExpression expr) {
        for (final WhenClause when : expr.getWhenClauses()) {
            if (when.isOperandMatch()) {
                return false;
            }
        }
        return true;
    }

    /** A CASE result as the plan holds it: the bare word NULL typed by the others, a number met with them. */
    private String caseResult(final Expression result, final String family, final NumericType met) {
        final String moved = met == null || isUntypedNull(result) ? null : metNumber(result, met);
        return moved != null ? moved : typedArgument(result, family);
    }

    /** A simple CASE's WHEN value, converted to the subject's type where two exact numbers differ. */
    private String caseValue(final DataType subjectType, final WhenClause when) {
        final Expression value = ((BinaryOperationExpression) when.getCondition()).getRight();
        final DataType valueType = argumentType(value);
        final String printed = value.accept(this);
        final boolean converted = isExactNumber(subjectType) && isExactNumber(valueType)
            && !SqlTypeNames.canonical(subjectType).equals(SqlTypeNames.canonical(valueType));
        return converted ? "CAST(" + printed + " AS " + SqlTypeNames.canonical(subjectType) + ")" : printed;
    }

    private static boolean allNull(final List<Expression> results) {
        for (final Expression result : results) {
            if (!isUntypedNull(result)) {
                return false;
            }
        }
        return true;
    }

    /**
     * A CASE whose every result is an untyped NULL, its first result converted to the NULL type and a missing
     * ELSE the bare word ({@link #nullBranch}): {@code CASE_FLATTENED(RT.N > 0, CAST(null AS NULL), RT.N < 0,
     * null, null)}, {@code CASE RT.N WHEN CAST(1 AS NUMBER(38,0)) THEN CAST(null AS NULL) ELSE null END}
     * (live-verified).
     */
    private String nullCase(final CaseExpression expr, final boolean simple) {
        final String elseText = expr.getElseExpression() == null ? "null" : nullBranch(expr.getElseExpression(), false);
        boolean first = true;
        if (simple) {
            final Expression subject = ((BinaryOperationExpression) expr.getWhenClauses().get(0).getCondition()).getLeft();
            final DataType subjectType = argumentType(subject);
            final StringBuilder text = new StringBuilder("CASE ").append(subject.accept(this));
            for (final WhenClause when : expr.getWhenClauses()) {
                text.append(" WHEN ").append(caseValue(subjectType, when))
                    .append(" THEN ").append(nullBranch(when.getResult(), first));
                first = false;
            }
            return text.append(" ELSE ").append(elseText).append(" END").toString();
        }
        final StringBuilder text = new StringBuilder("CASE_FLATTENED(");
        for (final WhenClause when : expr.getWhenClauses()) {
            if (when.isOperandMatch()) {
                return super.visitCaseExpression(expr);
            }
            text.append(conditionText(when.getCondition(), true)).append(", ")
                .append(nullBranch(when.getResult(), first)).append(", ");
            first = false;
        }
        return text.append(elseText).append(')').toString();
    }

    /**
     * A scalar subquery as the plan re-prints its SELECT (see {@link SubqueryPlanPrint}), or as written where it is
     * not modelled. A sentence that echoes its operands as written re-prints a subquery among them all the same:
     * the account has no other way to name one (live-verified).
     */
    @Override
    public String visitSubquery(final SubqueryExpression expr) {
        final String select = new SubqueryPlanPrint(context, planShaped() ? mode : StrictPrintMode.PLAN).select(expr);
        return select != null ? "(" + select + ")" : super.visitSubquery(expr);
    }

    /** Whether the GROUPING call being printed is already being given the name the account echoes. */
    private boolean namingGrouping;

    /** The family a COALESCE chain being printed types its untyped NULLs as; null elsewhere. */
    private String coalesceFamily;
    /** The family the untyped NULL being printed is planned as, set around one argument or operand; null elsewhere. */
    private String nullFamily;

    /** Where each bare name printed without a relation is noted, or null when nobody asked. */
    private List<String> bareColumns;
    /** Where each qualifier a reference was written with is noted, or null when nobody asked. */
    private List<String> writtenQualifiers;
    /** The scope of the query around a re-printed subquery, whose names print as correlations; null elsewhere. */
    private ExpressionEvaluatorVisitor correlationScope;
    /** The names the re-printed subquery's own relations go by. */
    private Set<String> ownQualifiers;
    /** Written qualifiers, upper-cased, that print as another relation's name; null elsewhere. */
    private Map<String, String> qualifierRenames;

    @Override
    public String visitColumnReference(final ColumnReferenceExpression expr) {
        if (columnsAsWritten && expr.getStarArgument() == null) {
            return SystemConstantArguments.writtenName(expr);
        }
        if (expr.getStarArgument() != null && planShaped() && context != null) {
            // A star among a call's arguments is the columns it stands for: HASH(FZ.ID, FZ.B, 1) (live-verified).
            return String.join(", ", context.strictStarColumns(expr.getStarArgument()));
        }
        if (expr.isQualified()) {
            final String renamed = qualifierRenames == null ? null
                : qualifierRenames.get(expr.getTableName().toUpperCase(Locale.ROOT));
            if (renamed != null) {
                if (writtenQualifiers != null) {
                    writtenQualifiers.add(renamed);
                }
                return renamed + "." + spelledColumn(expr);
            }
            if (correlationScope == null && context.isEnclosingQualifier(expr.getTableName())
                    && !namesARelationOf(context, expr.getTableName())) {
                // A name of the row around this query, which none of its own relations answers to.
                return "CORRELATION(" + expr.getTableName().toUpperCase(Locale.ROOT) + "." + spelledColumn(expr) + ")";
            }
            if (correlationScope != null && !ownQualifiers.contains(expr.getTableName())
                    && namesAnOuterRelation(expr.getTableName())
                    || correlationScope == null && compiledOuterQualifier(expr) != null) {
                final String read = expr.getTableName().toUpperCase(Locale.ROOT) + "." + spelledColumn(expr);
                return printingCorrelatedCall ? read : "CORRELATION(" + read + ")";
            }
            if (writtenQualifiers != null) {
                writtenQualifiers.add(expr.getTableName());
            }
            return expr.getTableName().toUpperCase(Locale.ROOT) + "." + spelledColumn(expr);
        }
        final String qualifier = context.strictMessageQualifier(expr);
        if (qualifier == null && correlationScope != null) {
            final String outerQualifier = correlationScope.strictMessageQualifier(expr);
            if (outerQualifier != null) {
                return "CORRELATION(" + outerQualifier + "." + spelledColumn(expr) + ")";
            }
        }
        if (qualifier == null && correlationScope == null) {
            final String outerQualifier = compiledOuterQualifier(expr);
            if (outerQualifier != null) {
                final String read = outerQualifier + "." + spelledColumn(expr);
                return printingCorrelatedCall ? read : "CORRELATION(" + read + ")";
            }
        }
        if (qualifier == null && bareColumns != null) {
            bareColumns.add(expr.getColumnName());
        }
        return qualifier != null
            ? qualifier + "." + spelledColumn(expr)
            : expr.getColumnName();
    }

    /**
     * A column part as the plan prints it: its canonical name, quoted only when it needs the quotes — a
     * quoted {@code "c"} stays {@code "c"} beside its relation ({@code T1."c"}), where folding it would
     * name a different column, C.
     */
    private static String spelledColumn(final ColumnReferenceExpression expr) {
        return SqlIdentifiers.spellCanonical(expr.getColumnName());
    }

    /**
     * Have this printer spell a reference written with one of {@code renames}' qualifiers through the relation
     * the plan reads it from instead, as a USING join's columns are read through its derived relation.
     *
     * @param renames each written qualifier, upper-cased, and the relation that replaces it
     */
    void renameQualifiers(final Map<String, String> renames) {
        this.qualifierRenames = renames;
    }

    /**
     * Have this printer spell a reference to the query around a subquery as the plan holds it,
     * {@code CORRELATION(RT.G)}: a qualifier none of the subquery's own relations carries but a relation of
     * {@code outer} does, or a bare name only {@code outer} resolves.
     *
     * @param outer         the scope of the query the subquery stands in
     * @param ownQualifiers the names the subquery's own relations go by
     */
    void watchCorrelations(final ExpressionEvaluatorVisitor outer, final Set<String> ownQualifiers) {
        this.correlationScope = outer;
        this.ownQualifiers = ownQualifiers;
    }

    /**
     * Whether a call is an aggregate every name of which reads the query around this subquery — the shape the
     * plan holds as one correlated value.
     */
    private boolean readsOuterAlone(final FunctionCallExpression call) {
        if (context.getFunctionRegistry() == null || call.getFunctionName() == null
                || !context.getFunctionRegistry().hasAggregateFunction(call.getFunctionName().toUpperCase(Locale.ROOT))) {
            return false;
        }
        final List<ColumnReferenceExpression> names = new ArrayList<>();
        collectReferences(call, names);
        if (names.isEmpty()) {
            return false;
        }
        for (final ColumnReferenceExpression name : names) {
            if (correlationScope == null) {
                if (compiledOuterQualifier(name) == null) {
                    return false;
                }
            } else if (!name.isQualified() || ownQualifiers.contains(name.getTableName())
                    || !namesAnOuterRelation(name.getTableName())) {
                return false;
            }
        }
        return true;
    }

    /**
     * The relation of the query around a subquery being COMPILED that a reference reads, as a plan-shaped echo
     * qualifies it — {@code RANDOM(n)} inside {@code (SELECT RANDOM(n)) FROM rt} is "found 'CORRELATION(RT.N)'"
     * (live-verified) — or null when the reference reads the subquery's own relations, no subquery compiles, or
     * the echo is the as-written one, which names the outer column bare: "Window function [MAX(G.V) OVER
     * (PARTITION BY FZ.ID)] contains a correlation."
     */
    private String compiledOuterQualifier(final ColumnReferenceExpression reference) {
        final ExpressionEvaluatorVisitor outerScope = SubqueryCompilation.outerScope();
        if (outerScope == null || outerScope == context || mode == StrictPrintMode.WRITTEN) {
            return null;
        }
        if (!reference.isQualified()) {
            return context.strictMessageQualifier(reference) != null ? null : outerScope.strictMessageQualifier(reference);
        }
        final String qualifier = reference.getTableName();
        return namesARelationOf(context, qualifier) || !namesARelationOf(outerScope, qualifier) ? null
            : qualifier.toUpperCase(Locale.ROOT);
    }

    /** Every column reference an expression reads, through the calls and operators it holds. */
    private static void collectReferences(final Expression expr, final List<ColumnReferenceExpression> into) {
        if (expr instanceof ColumnReferenceExpression) {
            into.add((ColumnReferenceExpression) expr);
            return;
        }
        if (expr instanceof FunctionCallExpression) {
            for (final Expression argument : ((FunctionCallExpression) expr).getArguments()) {
                collectReferences(argument, into);
            }
            return;
        }
        if (expr instanceof BinaryOperationExpression) {
            collectReferences(((BinaryOperationExpression) expr).getLeft(), into);
            collectReferences(((BinaryOperationExpression) expr).getRight(), into);
            return;
        }
        if (expr instanceof UnaryOperationExpression) {
            collectReferences(((UnaryOperationExpression) expr).getOperand(), into);
        }
    }

    /** Whether a written qualifier names a relation of the scope correlations read. */
    private boolean namesAnOuterRelation(final String qualifier) {
        return namesARelationOf(correlationScope, qualifier);
    }

    /** Whether a written qualifier names one of a scope's relations. */
    private static boolean namesARelationOf(final ExpressionEvaluatorVisitor scope, final String qualifier) {
        final Map<String, Table> relations = scope.getMultiTableAliasToTable();
        if (relations != null && !relations.isEmpty()) {
            for (final String key : relations.keySet()) {
                if (key.equalsIgnoreCase(qualifier)) {
                    return true;
                }
            }
            return false;
        }
        return scope.getTable() != null && scope.getTable().getName().equalsIgnoreCase(qualifier);
    }

    /**
     * Have this printer note the columns it prints: each qualifier a reference was written with into
     * {@code qualifiers}, and each bare name it finds no relation for into {@code bare} — how the
     * subquery re-print tells a reference it can print from one that reads an outer query.
     */
    void watchColumns(final List<String> bare, final List<String> qualifiers) {
        this.bareColumns = bare;
        this.writtenQualifiers = qualifiers;
    }

    /** An expression's static type as this printer's context types it, or null when it does not type. */
    DataType typeOf(final Expression expression) {
        return argumentType(expression);
    }

    /** A value moved to another NUMBER width, spelled as this printer spells it — see {@link #fixedToFixed}. */
    String rescaledText(final String printed, final String target) {
        return fixedToFixed(printed, target);
    }

    /** An array literal is the constructor call the plan makes of it: {@code ARRAY_CONSTRUCT(1, 2, 3)}. */
    @Override
    public String visitJsonArray(final JsonArrayExpression expr) {
        if (mode == StrictPrintMode.WRITTEN) {
            return super.visitJsonArray(expr);
        }
        final StringBuilder text = new StringBuilder("ARRAY_CONSTRUCT(");
        final List<Expression> elements = expr.getElements();
        for (int i = 0; i < elements.size(); i++) {
            text.append(i > 0 ? ", " : "").append(elements.get(i).accept(this));
        }
        return text.append(')').toString();
    }

    /** An object literal is the constructor call the plan makes of it: {@code OBJECT_CONSTRUCT('a', 1)}. */
    @Override
    public String visitJsonObject(final JsonObjectExpression expr) {
        if (mode == StrictPrintMode.WRITTEN) {
            return super.visitJsonObject(expr);
        }
        final StringBuilder text = new StringBuilder("OBJECT_CONSTRUCT(");
        boolean first = true;
        for (final Map.Entry<String, Expression> entry : expr.getProperties().entrySet()) {
            text.append(first ? "" : ", ").append('\'').append(entry.getKey()).append("', ")
                .append(entry.getValue().accept(this));
            first = false;
        }
        return text.append(')').toString();
    }

    /**
     * A cast as the plan spells it.
     *
     * <p>In the PLAN mode it is {@code CAST(x AS NUMBER(5,1))} — never the {@code ::} it may have
     * been written with, the target named as the argument-type text names it, {@code TRY_CAST(x AS T)}
     * for the TRY form — unless it changes nothing, when only its operand is left: {@code d::DATE},
     * {@code n::NUMBER(5,0)} over a NUMBER(5,0), {@code f::DOUBLE}, {@code b::BOOLEAN},
     * {@code v::VARIANT} and a text widened or kept ({@code g::VARCHAR}, {@code g::TEXT}) all print the
     * bare column, while a TIME or TIMESTAMP cast, a narrower text and any change of precision or
     * scale keep theirs. A cast of the bare word NULL is the plan's typed null,
     * {@code SYSTEM$NULL_TO_DATE(null)}, {@code SYSTEM$NULL_TO_FIXED(null)},
     * {@code SYSTEM$NULL_TO_TEXT(null)} or {@code SYSTEM$NULL_TO_REAL(null)}.
     *
     * <p>In the CONVERSION mode it is the conversion function it was planned as —
     * {@code TO_DATE(FT.G)}, {@code TO_TIME(FT.TS)}, {@code TO_TIMESTAMP(FT.D)},
     * {@code TO_NUMBER(FT.G)}, {@code TO_DOUBLE(FT.N)}, {@code TO_BOOLEAN(FT.G)}, {@code TO_CHAR(FT.N)},
     * {@code TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3))} — {@code identity(x)} when it changes nothing in a
     * family that spells it so ({@link #printsIdentity}), {@code TEXT_TO_TEXT(x)} onto a narrower text,
     * {@code FIXED_TO_FIXED(x AS NUMBER(p,s)[UNKNOWN])} onto another precision or scale, and
     * {@code SYSTEM$NULL_TO_VECTOR(NULL)} or {@code SYSTEM$NULL_TO_FIXED(NULL)} over the bare word
     * NULL. A target outside those keeps the {@code CAST(x AS T)} spelling, and a TRY_CAST the
     * shared printer's.
     */
    @Override
    public String visitCast(final CastExpression expr) {
        if (mode == StrictPrintMode.WRITTEN) {
            return super.visitCast(expr);
        }
        if (expr instanceof PlannedCastExpression) {
            return "CAST(" + expr.getExpression().accept(this) + " AS " + expr.getTargetType() + ")";
        }
        if (conversionShaped() && expr.isTryMode()) {
            final String conversion = tryConversion(expr);
            return conversion != null ? conversion : super.visitCast(expr);
        }
        final Expression source = expr.getExpression();
        final DataType targetType = argumentType(expr);
        if (convertedCalls != null && targetType instanceof NumericType && NumericType.isApproximate(targetType)
                && !expr.isTryMode()) {
            // A column default's echo spells a cast to FLOAT as the conversion: (TO_DOUBLE(0.5)) (live-verified).
            return "TO_DOUBLE(" + source.accept(this) + ")";
        }
        final String target = typeText(expr);
        final String targetText = tellingDefault && targetType instanceof StringType && expr.getTargetType() != null
            && expr.getTargetType().indexOf('(') < 0 ? "VARCHAR" : target != null ? target : expr.getTargetType();
        if (conversionShaped()) {
            if (isUntypedNull(source)) {
                final String family = targetType instanceof VectorType ? "VECTOR"
                    : isExactNumber(targetType) ? "FIXED" : targetType instanceof StringType ? "TEXT"
                    : targetType instanceof VariantType ? "VARIANT" : null;
                if (family != null) {
                    return "SYSTEM$NULL_TO_" + family + "(NULL)";
                }
            }
            final DataType sourceType = argumentType(source);
            final String shaped = shapedConversion(source, sourceType, targetType);
            if (shaped != null) {
                return shaped;
            }
            if (printsIdentity(sourceType, targetType)) {
                return "identity(" + source.accept(this) + ")";
            }
            if (sourceType instanceof StringType && targetType instanceof StringType) {
                return "TEXT_TO_TEXT(" + source.accept(this) + ")";
            }
            if (NumericType.isDecfloat(targetType)) {
                // A cast to DECFLOAT is the conversion it names: TO_DATE(TO_DECFLOAT(1.5)) (live-verified).
                return "TO_DECFLOAT(" + source.accept(this) + ")";
            }
            if (isExactNumber(sourceType) && isExactNumber(targetType)) {
                return fixedToFixed(source.accept(this), targetText);
            }
            final String conversion = plannedConversion(sourceType, targetType, targetText);
            return conversion != null
                ? conversion + "(" + source.accept(this) + ")"
                : "CAST(" + source.accept(this) + " AS " + targetText + ")";
        }
        if (isUntypedNull(source) && !expr.isTryMode()) {
            final String family = targetType instanceof VariantType ? "VARIANT" : typedNullFamily(targetType);
            if (family != null) {
                return "SYSTEM$NULL_TO_" + family + "(null)";
            }
        }
        if (!isUntypedNull(source) && isNullValue(source)) {
            // A call folding to NULL is the typed null too, a conditional with its text inside, TRY_CAST alike:
            // NVL(NULL, NULL)::INT is SYSTEM$NULL_TO_FIXED(NVL(CAST(null AS NULL), null)) (live-verified).
            final String family = targetType instanceof VariantType ? "VARIANT" : typedNullFamily(targetType);
            if (family != null) {
                return typedArgument(source, family);
            }
        }
        final DataType sourceType = argumentType(source);
        if (!expr.isTryMode() && (sourceType instanceof StringType || sourceType instanceof VariantType)
                && isExactNumber(targetType)) {
            // A text or a VARIANT moved to an exact number is TO_NUMBER in the plan, its width spelled unless it is
            // the default: TO_NUMBER('5') for '5'::NUMBER, TO_NUMBER('5', 10, 2) for '5'::NUMBER(10,2),
            // TO_NUMBER(RT.V) for v::INT (live-verified).
            final NumericType number = (NumericType) targetType;
            final boolean defaultWidth = number.getPrecision() == MAX_PRECISION && number.getScale() == 0;
            return "TO_NUMBER(" + source.accept(this)
                + (defaultWidth ? "" : ", " + number.getPrecision() + ", " + number.getScale()) + ")";
        }
        if (isNoOpCast(argumentType(source), targetType)) {
            return source.accept(this);
        }
        return (expr.isTryMode() ? "TRY_CAST(" : "CAST(") + source.accept(this) + " AS " + targetText + ")";
    }

    /**
     * A cast into or out of a shape, as an invalid-type sentence names it: a vector read into an array is
     * {@code FROM_VECTOR(x)}, and any other cast whose source or target is a structured type — an ARRAY, an
     * OBJECT or a MAP written with its shape — is {@code STRUCTURED_CAST(x)}, the target never spelled:
     * {@code [1,2]::ARRAY(INT)::ARRAY} is {@code STRUCTURED_CAST(STRUCTURED_CAST(ARRAY_CONSTRUCT(1, 2)))}
     * (live-verified). Null for a cast between plain types.
     */
    private String shapedConversion(final Expression source, final DataType sourceType, final DataType targetType) {
        if (sourceType instanceof VectorType) {
            return targetType instanceof ArrayType ? "FROM_VECTOR(" + source.accept(this) + ")" : null;
        }
        return StructuredTypes.isStructured(sourceType) || StructuredTypes.isStructured(targetType)
            ? "STRUCTURED_CAST(" + source.accept(this) + ")" : null;
    }

    /**
     * TO_ARRAY and TO_OBJECT over a value that is one already, as an invalid-type sentence names them: a
     * vector's elements are read by {@code FROM_VECTOR}, a structured value is cast out of its shape, and a
     * plain one is left as it is — {@code identity(x)} (live-verified). Null over any other argument, which
     * keeps the call.
     */
    private String keptContainer(final String name, final Expression operand) {
        final DataType held = argumentType(operand);
        final boolean array = name.equals("TO_ARRAY");
        if (array && held instanceof VectorType) {
            return "FROM_VECTOR(" + operand.accept(this) + ")";
        }
        if (array ? held instanceof StructuredArrayType : StructuredTypes.isStructuredObjectFamily(held)) {
            return "STRUCTURED_CAST(" + operand.accept(this) + ")";
        }
        if (array ? held instanceof ArrayType : held instanceof ObjectType) {
            return "identity(" + operand.accept(this) + ")";
        }
        return null;
    }

    /** Whether a type is a plain ARRAY or OBJECT, written without a shape. */
    private static boolean isPlainContainer(final DataType type) {
        return (type instanceof ArrayType || type instanceof ObjectType) && !StructuredTypes.isStructured(type);
    }

    /**
     * A TRY_CAST of a text as the conversion echo spells it: the TRY_ function of its target, whatever width
     * the target carries — {@code TRY_TO_DATE(T.S)}, {@code TRY_TO_NUMBER(T.S)} for any exact number,
     * {@code TRY_TO_DOUBLE}, {@code TRY_TO_BOOLEAN}, {@code TRY_TO_TIME}, {@code TRY_TO_TIMESTAMP_NTZ} — and onto
     * a text {@code identity(T.S)} where it keeps the width, {@code TRY_TO_TEXT(T.S)} where it narrows it
     * (live-verified). Null for a source that is no text, or a target outside those.
     */
    private String tryConversion(final CastExpression expr) {
        final Expression source = expr.getExpression();
        final DataType sourceType = argumentType(source);
        final DataType targetType = argumentType(expr);
        if (!(sourceType instanceof StringType) || targetType == null) {
            return null;
        }
        final String printed = source.accept(this);
        if (targetType instanceof StringType) {
            return printsIdentity(sourceType, targetType) ? "identity(" + printed + ")" : "TRY_TO_TEXT(" + printed + ")";
        }
        final String function;
        if (isDate(targetType)) {
            function = "TRY_TO_DATE";
        } else if (targetType instanceof BooleanType) {
            function = "TRY_TO_BOOLEAN";
        } else if (isExactNumber(targetType)) {
            function = "TRY_TO_NUMBER";
        } else if (targetType instanceof NumericType) {
            function = "TRY_TO_DOUBLE";
        } else if (isTime(targetType)) {
            function = "TRY_TO_TIME";
        } else if (isTimestamp(targetType)) {
            function = "TRY_TO_" + timestampFlavour(targetType);
        } else {
            return null;
        }
        return function + "(" + printed + ")";
    }

    /**
     * The function an invalid-type sentence names a cast by, or null when the target is not one it
     * spells. A TIMESTAMP of any flavour or precision is {@code TO_TIMESTAMP(x)} over a DATE or a
     * TIMESTAMP, and its flavour's own conversion over a text, {@code TO_TIMESTAMP_LTZ(FAM.G)}.
     */
    private static String plannedConversion(final DataType source, final DataType target,
                                            final String targetText) {
        if (target instanceof VectorType) {
            return "TO_VECTOR";
        }
        // A text read as an interval is the interval's conversion: '1 02:03:04'::INTERVAL DAY TO SECOND is
        // TO_INTERVAL_DAY_TIME('1 02:03:04') in an invalid-type sentence (live-verified).
        if (IntervalCasts.isIntervalType(target) && source instanceof StringType) {
            return IntervalCasts.conversionName(target);
        }
        // A text converts to BINARY, and a scalar to VARIANT, through their own functions; a source they
        // refuse keeps the CAST it was written as.
        if (target instanceof BinaryType && source instanceof StringType) {
            return "TO_BINARY";
        }
        if (target instanceof VariantType && (source instanceof StringType || source instanceof NumericType
                || source instanceof BooleanType || source instanceof DateTimeType || source instanceof BinaryType)) {
            return "TO_VARIANT";
        }
        if (isDate(target)) {
            return "TO_DATE";
        }
        if (target instanceof BooleanType) {
            return "TO_BOOLEAN";
        }
        if (target instanceof NumericType && NumericType.isApproximate(target)) {
            return "TO_DOUBLE";
        }
        if (isTimestamp(target)) {
            if (isDate(source) || isTimestamp(source)) {
                return "TO_TIMESTAMP";
            }
            if (source instanceof StringType) {
                return "TO_" + timestampFlavour(target);
            }
        }
        switch (targetText) {
            case "TIME(9)":
                return "TO_TIME";
            case "TIMESTAMP_NTZ(9)":
                return "TO_TIMESTAMP";
            case "NUMBER(38,0)":
                return "TO_NUMBER";
            case "VARCHAR(134217728)":
                return "TO_CHAR";
            default:
                return null;
        }
    }

    /** The family a SYSTEM$NULL_TO_ call names for a typed NULL in the plan, or null for another target. */
    static String typedNullFamily(final DataType target) {
        if (isDate(target)) {
            return "DATE";
        }
        if (target instanceof NumericType) {
            return NumericType.isApproximate(target) ? "REAL" : "FIXED";
        }
        if (target instanceof BooleanType) {
            return "BOOLEAN";
        }
        return target instanceof StringType ? "TEXT" : null;
    }

    /**
     * Whether a cast from {@code source} to {@code target} changes nothing, so the plan drops it: the
     * same DATE, BOOLEAN, FLOAT, VARIANT or VECTOR, the same NUMBER(p, s), or a text no narrower than
     * its source. A TIME or TIMESTAMP cast always stays, even onto its own type.
     */
    private static boolean isNoOpCast(final DataType source, final DataType target) {
        if (source == null || target == null) {
            return false;
        }
        if (source instanceof StringType && target instanceof StringType) {
            return textLength(target) >= textLength(source);
        }
        if (source instanceof NumericType && target instanceof NumericType) {
            final boolean sourceApproximate = NumericType.isApproximate(source);
            final boolean targetApproximate = NumericType.isApproximate(target);
            if (sourceApproximate || targetApproximate) {
                return sourceApproximate && targetApproximate;
            }
            final NumericType from = (NumericType) source;
            final NumericType to = (NumericType) target;
            return from.getPrecision() == to.getPrecision() && from.getScale() == to.getScale();
        }
        if (source instanceof DateTimeType || target instanceof DateTimeType) {
            return isDate(source) && isDate(target);
        }
        final String from = SqlTypeNames.canonical(source);
        final boolean sameName = from != null && from.equals(SqlTypeNames.canonical(target));
        return sameName && (source instanceof BooleanType || source instanceof VectorType
            || "VARIANT".equals(from));
    }

    /**
     * Whether an invalid-type sentence names a cast from {@code source} to {@code target}
     * {@code identity(x)}. It does for every cast the plan drops ({@link #isNoOpCast}) — a DATE, a
     * BOOLEAN, a FLOAT, a VARIANT or a VECTOR onto its own type, a NUMBER onto its own precision and
     * scale, a text no narrower than its source — and also for a BINARY no narrower than its source
     * and for a plain OBJECT or ARRAY onto its own type. A TIME or TIMESTAMP cast onto its own type is
     * no identity: it keeps its conversion function, {@code TO_TIME(FAM.TM)}, {@code TO_TIMESTAMP(FAM.TS)}.
     */
    private static boolean printsIdentity(final DataType source, final DataType target) {
        if (isNoOpCast(source, target)) {
            return true;
        }
        if (source instanceof BinaryType && target instanceof BinaryType) {
            return binaryWidth((BinaryType) target) >= binaryWidth((BinaryType) source);
        }
        if (source == null || target == null) {
            return false;
        }
        final String from = SqlTypeNames.canonical(source);
        return from != null && from.equals(SqlTypeNames.canonical(target))
            && ("OBJECT".equals(from) || "ARRAY".equals(from));
    }

    /** Whether an invalid-type sentence names this cast {@code identity(x)} — see {@link #printsIdentity}. */
    boolean printsAsIdentity(final CastExpression cast) {
        return !cast.isTryMode() && printsIdentity(argumentType(cast.getExpression()), argumentType(cast));
    }

    private static int binaryWidth(final BinaryType binary) {
        final int width = binary.getMaxLength();
        return width > 0 ? width : Integer.MAX_VALUE;
    }

    private static int textLength(final DataType text) {
        if (text instanceof LengthlessStringType) {
            return Integer.MAX_VALUE;
        }
        final int length = ((StringType) text).getMaxLength();
        return length > 0 ? length : Integer.MAX_VALUE;
    }

    /**
     * The aggregates live's plan has already rewritten by the time it names them in a message:
     *
     * <pre>
     *   AVG(n)                                     over NUMBER(10,2)
     *       (CAST(SUM(T.N) AS NUMBER(28,8))) / (COUNT(T.N))
     *   AVG(n)                                     over NUMBER(38,0)
     *       SCALED_ROUND_INT_DIVIDE(SUM(T.N), COUNT(T.N))
     *   AVG(f)                                     over FLOAT
     *       (SUM(T.F)) / (CAST(COUNT(T.F) AS FLOAT))
     *   AVG(t)                                     over VARCHAR or VARIANT
     *       (SUM(CAST(T.T AS FLOAT))) / (CAST(COUNT(T.T) AS FLOAT))
     *   MEDIAN(x)                                  PERCENTILE_CONT(CAST(x AS &lt;call type&gt;), 0.5)
     *   PERCENTILE_CONT(f) WITHIN GROUP (ORDER BY x)   PERCENTILE_CONT(CAST(x AS &lt;call type&gt;), f)
     *   PERCENTILE_DISC(f) WITHIN GROUP (ORDER BY x)   PERCENTILE_DISC(x, f)
     * </pre>
     *
     * <p>★ AVG IS A SUM OVER A COUNT, printed as that DIVISION ({@link #exactDivision}): the SUM is cast
     * to the quotient's width — six more decimals to a cap of twelve, never narrowing — whenever that
     * width fits in 38 digits; past it live plans the division through its internal
     * {@code SCALED_ROUND_INT_DIVIDE} instead: NUMBER(20,0) is the last CAST (38,6), NUMBER(26,0) and
     * every INTEGER column take the internal, while NUMBER(38,37) and NUMBER(38,12), whose scale
     * cannot grow, keep the CAST at 38. Twenty widths measured. A FLOAT argument divides the plain sum
     * by the count cast to FLOAT, and a VARCHAR or VARIANT argument is first cast to FLOAT inside the
     * SUM — the count still takes the bare column.
     *
     * <p>★ THE PERCENTILES' CAST APPEARS ONLY WHERE THE TYPE CHANGES: PERCENTILE_DISC hands back an
     * input value and keeps its type, so it prints none; PERCENTILE_CONT and MEDIAN interpolate and
     * widen, so they print one. MEDIAN is printed as PERCENTILE_CONT(x, 0.5) — the plan knows no MEDIAN.
     *
     * <p>A DISTINCT or star form, and an argument whose type does not resolve, print as written: an
     * invented definition would read as live's and be wrong.
     *
     * <p>An invalid-type sentence rewrites less: COALESCE, LEFT and RIGHT, the interval shifts, the
     * date/time calls and IFF ({@link #plannedRespelling}) and a nested TO_VARCHAR, which it names
     * {@code TO_CHAR} ({@link #conversionRewrittenCall}).
     */
    /**
     * An aggregate the query AROUND this subquery computes — every name it reads is that query's — is ONE
     * correlated value in the plan: {@code CORRELATION(MAX(FZ.ID))}, the call inside the wrapper rather than
     * each of its names (live-verified).
     */
    private boolean printingCorrelatedCall;

    @Override
    public String visitSessionVar(final SessionVarExpression expr) {
        // The plan holds the variable's canonical name, so a refusal names $NUM however it was written.
        return mode == StrictPrintMode.WRITTEN ? super.visitSessionVar(expr)
            : "$" + expr.getVarName().toUpperCase(Locale.ROOT);
    }

    @Override
    public String visitFunctionCall(final FunctionCallExpression expr) {
        if (!namingGrouping && expr.getNameExpression() == null && "GROUPING".equalsIgnoreCase(expr.getFunctionName())) {
            // The account knows GROUPING as GROUPING_ID wherever it echoes the call (live-verified).
            namingGrouping = true;
            try {
                final String printed = visitFunctionCall(expr);
                return printed.regionMatches(true, 0, "GROUPING(", 0, 9) ? "GROUPING_ID(" + printed.substring(9)
                    : printed;
            } finally {
                namingGrouping = false;
            }
        }
        if (mode == StrictPrintMode.WRITTEN) {
            return super.visitFunctionCall(expr);
        }
        final DataType converted = convertedCallType(expr);
        if (converted instanceof NumericType) {
            convertingCalls.add(expr);
            try {
                final NumericType number = (NumericType) converted;
                final String call = visitFunctionCall(expr);
                return number.getPrecision() == WIDEST_NUMBER && number.getScale() == 0 ? "TO_NUMBER(" + call + ")"
                    : "TO_NUMBER(" + call + ", " + number.getPrecision() + ", " + number.getScale() + ")";
            } finally {
                convertingCalls.remove(expr);
            }
        }
        if (foldingConstants && expr.getNameExpression() == null && SystemConstantArguments.foldsEarly(expr, context)) {
            final String folded = foldedValue(expr);
            if (folded != null) {
                return folded;
            }
        }
        if (tellingDefault && "TO_VARCHAR".equalsIgnoreCase(expr.getFunctionName()) && expr.getArguments().size() > 1
                && expr.getNameExpression() == null) {
            final StringBuilder told = new StringBuilder("TO_CHAR(");
            for (int i = 0; i < expr.getArguments().size(); i++) {
                told.append(i > 0 ? ", " : "").append(expr.getArguments().get(i).accept(this));
            }
            return told.append(')').toString();
        }
        if (expr instanceof NullableConstantExpression) {
            return "ENSURE_NULLABLE(" + ((NullableConstantExpression) expr).getValue().accept(this) + ")";
        }
        if (isNullIfZeroOfNumber(expr)) {
            // NULLIFZERO is the NULLIF it stands for: NULLIF(RT.N, 0) (live-verified).
            return new FunctionCallExpression("NULLIF", new ArrayList<Expression>(Arrays.asList(
                expr.getArguments().get(0), new LiteralExpression(Long.valueOf(0), LiteralType.INTEGER)))).accept(this);
        }
        // IDENTIFIER(<value>) names a column, and the plan holds the column, not the call: live's
        // predicate refusal reads [T1.A] for `WHERE IDENTIFIER('a')` (live-verified).
        final ColumnReferenceExpression named = context == null ? null : context.identifierCallReference(expr);
        if (named != null) {
            return visitColumnReference(named);
        }
        if (!printingCorrelatedCall && (correlationScope != null || SubqueryCompilation.outerScope() != null)
                && readsOuterAlone(expr)) {
            if (correlationScope != null && planShaped()) {
                // The call is the enclosing query's, typed and rewritten in its scope: an AVG there is
                // CORRELATION(SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID))) (live-verified).
                return "CORRELATION(" + expr.accept(new StrictMessagePrinter(correlationScope, mode)) + ")";
            }
            printingCorrelatedCall = true;
            try {
                return "CORRELATION(" + visitFunctionCall(expr) + ")";
            } finally {
                printingCorrelatedCall = false;
            }
        }
        if (typesNulls() && nullFamily != null && foldsToNullItself(expr)) {
            // A call the plan folds to the bare word NULL is typed as it would be: YEAR(NULL) * n is
            // (SYSTEM$NULL_TO_FIXED(null)) * RT.N (live-verified).
            return "SYSTEM$NULL_TO_" + nullFamily + "(null)";
        }
        if (typesNulls() && nullFamily != null && isFoldedConditional(expr)) {
            // A conditional folding to an untyped NULL keeps its planned text inside the typed NULL:
            // IFF(TRUE, NULL, NULL) + 1 is (SYSTEM$NULL_TO_FIXED(IFF(CAST(TRUE AS BOOLEAN), CAST(null AS NULL),
            // null))) + 1 (live-verified).
            final String family = nullFamily;
            nullFamily = null;
            try {
                return "SYSTEM$NULL_TO_" + family + "(" + visitFunctionCall(expr) + ")";
            } finally {
                nullFamily = family;
            }
        }
        if (planShaped() && isVariableRead(expr)) {
            if (expr.getArguments().size() != 1) {
                return infixVariableRead(expr.getArguments());
            }
            final Expression variable = expr.getArguments().get(0);
            return convertsToText(variable) ? "(" + variableNameText(variable) + ")" : operand(variable);
        }
        final String name = expr.getFunctionName().toUpperCase(Locale.ROOT);
        if (planShaped() && context != null && context.getFunctionRegistry() != null && !expr.isStar()
                && context.getFunctionRegistry().isUserAggregateFunction(name)) {
            // The plan holds a user's aggregate function as EXTENSION_FUNC_A, whatever it is called (live-verified).
            final StringBuilder planned = new StringBuilder(UserAggregateFunction.PLANNED_NAME).append('(');
            for (int i = 0; i < expr.getArguments().size(); i++) {
                planned.append(i == 0 ? "" : ", ").append(expr.getArguments().get(i).accept(this));
            }
            return planned.append(')').toString();
        }
        if (conversionShaped()) {
            final String rewritten = conversionRewrittenCall(expr, name);
            if (rewritten != null) {
                return rewritten;
            }
            final String typed = nullTypedCall(expr, name);
            return typed != null ? typed : super.visitFunctionCall(expr);
        }
        if (planShaped() && context != null && expr.isStar() && expr.getNameExpression() == null
                && !name.equals("COUNT")) {
            // A star argument is the columns it stands for. An object built of the star keeps the star and names
            // them after the call, OBJECT_CONSTRUCT(*)(FZ.ID, FZ.B); any other call reads them as its arguments,
            // HASH(FZ.ID, FZ.B) (live-verified).
            final String columns = String.join(", ", context.strictStarColumns(StarArgument.of(expr)));
            return name.equals("OBJECT_CONSTRUCT") || name.equals("OBJECT_CONSTRUCT_KEEP_NULL")
                ? name + "(*)(" + columns + ")"
                : name + "(" + (expr.isDistinct() ? "DISTINCT " : "") + columns + ")";
        }
        if (planShaped()) {
            final String planned = plannedCall(expr, name);
            if (planned != null) {
                return planned;
            }
        }
        if (isPlainAverage(expr)) {
            final String expansion = averageExpansion(expr.getArguments().get(0));
            return expansion != null ? expansion : super.visitFunctionCall(expr);
        }
        if (name.equals("AVG") && expr.isDistinct() && !expr.isStar() && expr.getArguments().size() == 1
                && expr.getWithinGroupOrdered() == null) {
            final String distinct = distinctAverage(expr.getArguments().get(0));
            if (distinct != null) {
                return distinct;
            }
        }
        final boolean median = name.equals("MEDIAN") && expr.getWithinGroupOrdered() == null;
        final boolean ordered = (name.equals("PERCENTILE_CONT") || name.equals("PERCENTILE_DISC"))
            && expr.getWithinGroupOrdered() != null;
        if ((median || ordered) && !expr.isDistinct() && !expr.isStar() && expr.getArguments().size() == 1) {
            final Expression value = median ? expr.getArguments().get(0) : expr.getWithinGroupOrdered();
            final String fraction = median ? "0.5" : expr.getArguments().get(0).accept(this);
            final String printed = name.equals("PERCENTILE_DISC")
                ? wholeNumberOf(value) : convertedToCallType(value, expr);
            return (median ? "PERCENTILE_CONT" : name) + "(" + printed + ", " + fraction + ")";
        }
        final String rewritten = planRewrittenScalar(expr, name);
        if (rewritten != null) {
            return rewritten;
        }
        final String withCasts = withArgumentCasts(expr, name);
        return withCasts != null ? withCasts : super.visitFunctionCall(expr);
    }

    /** The calls whose first argument the plan reads as text, an untyped NULL there being {@code SYSTEM$NULL_TO_TEXT(null)}. */
    /** Whether a call is NULLIFZERO over an exact number. */
    private boolean isNullIfZeroOfNumber(final FunctionCallExpression call) {
        return call.getNameExpression() == null && !call.isStar() && call.getArguments().size() == 1
            && "NULLIFZERO".equalsIgnoreCase(call.getFunctionName())
            && isExactNumber(argumentType(call.getArguments().get(0)));
    }

    private static final Set<String> TEXT_FIRST_ARGUMENT = new HashSet<>(Arrays.asList(
        "UPPER", "LOWER", "TRIM", "REPLACE", "SUBSTR", "LENGTH", "PARSE_JSON"));
    /** The calls whose first argument the plan reads as a whole number, an untyped NULL there being FIXED's. */
    private static final Set<String> FIXED_FIRST_ARGUMENT = new HashSet<>(Arrays.asList(
        "ABS", "ROUND", "CEIL", "FLOOR", "SIGN", "ZEROIFNULL"));
    /** The calls whose arguments all meet in one type, an untyped NULL among them typed by the others. */
    private static final Set<String> MEETING_ARGUMENTS = new HashSet<>(Arrays.asList("GREATEST", "LEAST", "NVL", "IFNULL"));
    /** The date part a one-argument extraction call is planned as, {@code EXTRACT(day from …)}, or null. */
    /** A date part written as a word or a string, upper-cased, or null for anything else. */
    private static String unitWord(final Expression unit) {
        if (unit instanceof ColumnReferenceExpression && !((ColumnReferenceExpression) unit).isQualified()) {
            return ((ColumnReferenceExpression) unit).getColumnName().toUpperCase(Locale.ROOT);
        }
        if (unit instanceof LiteralExpression && ((LiteralExpression) unit).getType() == LiteralType.STRING) {
            return String.valueOf(((LiteralExpression) unit).getValue()).toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private static String extractedPart(final String name) {
        switch (name) {
            case "YEAR":
                return "year";
            case "MONTH":
                return "month";
            case "DAY":
            case "DAYOFMONTH":
                return "day";
            case "HOUR":
                return "hour";
            case "MINUTE":
                return "minute";
            case "SECOND":
                return "second";
            case "WEEK":
            case "WEEKOFYEAR":
                return "week";
            case "QUARTER":
                return "quarter";
            case "DAYOFWEEK":
                return "dayofweek";
            case "DAYOFYEAR":
                return "dayofyear";
            case "YEAROFWEEK":
                return "year_of_week";
            default:
                return null;
        }
    }

    /**
     * The calls the plan re-prints over the bare word NULL, as a date part extraction, or as DECODE, measured
     * alike through the constant-argument, the arity and the predicate sentences:
     *
     * <pre>
     *   TO_NUMBER(NULL)            SYSTEM$NULL_TO_FIXED(null)         TO_DECIMAL, TO_NUMERIC, and with a precision
     *   TO_VARIANT(NULL)           SYSTEM$NULL_TO_VARIANT(null)
     *   ABS(NULL)                  ABS(SYSTEM$NULL_TO_FIXED(null))    ROUND, CEIL, FLOOR, SIGN, ZEROIFNULL
     *   UPPER(NULL)                UPPER(SYSTEM$NULL_TO_TEXT(null))   LOWER, TRIM, REPLACE, SUBSTR, LENGTH; CONCAT's every argument
     *   SQRT(NULL)                 SQRT(SYSTEM$NULL_TO_REAL(null))
     *   GREATEST(NULL, 1)          GREATEST(SYSTEM$NULL_TO_FIXED(null), 1)   LEAST, NVL, IFNULL: the other arguments' family
     *   DAYOFMONTH(d)              EXTRACT(day from RT.D)             YEAR, MONTH, HOUR, WEEK, DAYOFWEEK, YEAROFWEEK …
     *   DECODE(n, 1, 2)            DECODE(RT.N, CAST(1 AS NUMBER(38,0)), 2, SYSTEM$NULL_TO_FIXED(null))
     * </pre>
     *
     * <p>DECODE's search values meet the subject's type, and a missing default is the results' typed null. Null
     * for any other call.
     */
    /**
     * A call one of whose arguments is an untyped NULL value, as the invalid-type sentence prints it: the NULL
     * typed as the plan types it ({@link #plannedCall}) — {@code ABS(SYSTEM$NULL_TO_FIXED(NULL))},
     * {@code NVL(COERCE(NULL AS NULL), NULL)}, a DECODE as the plan holds it ({@link #plannedDecode}). Null for a
     * call with no such argument, and for the calls whose other arguments the plan converts too: a date part, a
     * CONCAT of anything but texts, a conversion to a number.
     */
    private String nullTypedCall(final FunctionCallExpression expr, final String name) {
        final List<Expression> args = expr.getArguments();
        boolean holdsNull = false;
        boolean textsOnly = true;
        for (final Expression argument : args) {
            final boolean isNull = isNullValue(argument);
            holdsNull = holdsNull || isNull;
            textsOnly = textsOnly && (isNull || argumentType(argument) instanceof StringType);
        }
        if (!holdsNull || extractedPart(name) != null
                || (name.equals("CONCAT") || name.equals("CONCAT_WS")) && !textsOnly
                || name.equals("TO_NUMBER") || name.equals("TO_DECIMAL") || name.equals("TO_NUMERIC")) {
            // A conversion call over a NULL may be a bound value's own conversion, which the sentence keeps.
            return null;
        }
        return plannedCall(expr, name);
    }

    private String plannedCall(final FunctionCallExpression expr, final String name) {
        if (expr.isDistinct() || expr.isStar() || expr.getNameExpression() != null
                || expr.getArgumentNames() != null && !expr.getArgumentNames().isEmpty()
                    && expr.getArgumentNames().get(0) != null) {
            return null;
        }
        final List<Expression> args = expr.getArguments();
        if (args.isEmpty()) {
            return null;
        }
        final boolean firstNull = isUntypedNull(args.get(0));
        final boolean firstNullValue = isNullValue(args.get(0));
        if (firstNullValue && (name.equals("TO_NUMBER") || name.equals("TO_DECIMAL") || name.equals("TO_NUMERIC"))) {
            // A conditional folding to NULL keeps its text inside: SYSTEM$NULL_TO_FIXED(NVL(CAST(null AS NULL), null)).
            return typedArgument(args.get(0), "FIXED");
        }
        if (firstNullValue && name.equals("TO_VARIANT") && args.size() == 1) {
            return typedArgument(args.get(0), "VARIANT");
        }
        if (args.size() == 1 && extractedPart(name) != null && !firstNull) {
            return "EXTRACT(" + extractedPart(name) + " from " + args.get(0).accept(this) + ")";
        }
        if (extractingParts && args.size() == 2 && (name.equals("DATE_PART") || name.equals("EXTRACT"))) {
            final String word = unitWord(args.get(0));
            final String part = word == null ? null : extractedPart(word);
            if (part != null && !isUntypedNull(args.get(1))) {
                return "EXTRACT(" + part + " from " + args.get(1).accept(this) + ")";
            }
        }
        if (name.equals("DECODE") && args.size() >= 3) {
            return plannedDecode(args);
        }
        if (name.equals("CONCAT") || name.equals("CONCAT_WS") && args.size() >= 2) {
            return plannedConcat(args, name.equals("CONCAT_WS"));
        }
        final String nullMet = isFoldedConditional(expr) ? nullMetCall(expr, name) : null;
        if (nullMet != null) {
            return nullMet;
        }
        if (name.equals("NULLIF") && args.size() == 2 && firstNull && !isNullValue(args.get(1))) {
            // Over the bare word NULL the plan folds NULLIF into the IFF it stands for, both branches null.
            final String family = typedNullFamily(argumentType(args.get(1)));
            return family == null ? null : "IFF((SYSTEM$NULL_TO_" + family + "(null)) = " + operand(args.get(1))
                + ", CAST(null AS NULL), null)";
        }
        final List<String> families = new ArrayList<>();
        boolean typedNull = false;
        for (int i = 0; i < args.size(); i++) {
            final String family = argumentFamily(name, args, i);
            families.add(family);
            typedNull = typedNull || family != null && isNullValue(args.get(i));
        }
        if (!typedNull) {
            return null;
        }
        final StringBuilder text = new StringBuilder(expr.getFunctionName()).append('(');
        for (int i = 0; i < args.size(); i++) {
            text.append(i > 0 ? ", " : "").append(typedArgument(args.get(i), families.get(i)));
        }
        return text.append(')').toString();
    }

    /**
     * CONCAT as the plan holds it, every argument a text: a NULL the text's typed null, a text as it is and
     * anything else converted — {@code CONCAT(CAST(RT.N AS VARCHAR(134217728)), 'a', CAST(TRUE AS
     * VARCHAR(134217728)))}. CONCAT_WS is the CONCAT of its elements with the separator between them: {@code
     * CONCAT_WS(',', n, g)} is {@code CONCAT(CAST(RT.N AS VARCHAR(134217728)), ',', RT.G)} (live-verified).
     */
    private String plannedConcat(final List<Expression> args, final boolean separated) {
        final StringBuilder text = new StringBuilder("CONCAT(");
        final String separator = separated ? concatenated(args.get(0)) : null;
        for (int i = separated ? 1 : 0; i < args.size(); i++) {
            if (i > (separated ? 1 : 0)) {
                text.append(", ").append(separated ? separator + ", " : "");
            }
            text.append(concatenated(args.get(i)));
        }
        return text.append(')').toString();
    }

    /** One argument of a CONCAT as the text the plan reads it as. */
    private String concatenated(final Expression argument) {
        if (isNullValue(argument)) {
            return typedArgument(argument, "TEXT");
        }
        final DataType type = argumentType(argument);
        return type == null || type instanceof StringType ? argument.accept(this)
            : "CAST(" + argument.accept(this) + " AS VARCHAR(" + DataTypeParser.CAST_STRING_DEFAULT + "))";
    }

    /** The family argument {@code index} of a call plans an untyped NULL as, or null where it is not modelled. */
    private String argumentFamily(final String name, final List<Expression> args, final int index) {
        if (name.equals("CONCAT") || index == 0 && TEXT_FIRST_ARGUMENT.contains(name)) {
            return "TEXT";
        }
        if (index == 0 && FIXED_FIRST_ARGUMENT.contains(name)) {
            return "FIXED";
        }
        if (index == 0 && name.equals("SQRT")) {
            return "REAL";
        }
        if (index == 0 && args.size() == 1 && name.equals("ARRAY_SIZE")) {
            return "ARRAY";
        }
        if (index == 0 && args.size() == 1 && name.equals("TYPEOF")) {
            return "VARIANT";
        }
        if (MEETING_ARGUMENTS.contains(name) || name.equals("NULLIF") && index == 1) {
            return meetingFamily(args);
        }
        return null;
    }

    /** The typed-null family of the first argument that is no untyped NULL value ({@link #isNullValue}), or null. */
    private String meetingFamily(final List<Expression> args) {
        for (final Expression arg : args) {
            if (!isNullValue(arg)) {
                return typedNullFamily(argumentType(arg));
            }
        }
        return null;
    }

    /**
     * NVL, IFNULL, GREATEST, LEAST, NULLIF and COALESCE whose every argument is an untyped NULL, as the plan
     * holds them: the arguments meet in the NULL type, the first converted to it ({@link #nullBranch}) —
     * {@code NVL(CAST(null AS NULL), null)}, {@code GREATEST(CAST(null AS NULL), null, null)} — and COALESCE is
     * its right-nested IFNULL chain whose head it converts once more, {@code IFNULL(CAST(CAST(null AS NULL) AS
     * NULL), IFNULL(CAST(null AS NULL), null))} (live-verified). Null for any other call.
     */
    private String nullMetCall(final FunctionCallExpression expr, final String name) {
        final List<Expression> args = expr.getArguments();
        if (hasNamedArgument(expr) || args.size() < 2) {
            return null;
        }
        for (final Expression arg : args) {
            if (!isNullValue(arg)) {
                return null;
            }
        }
        if (name.equals("COALESCE")) {
            return "IFNULL(CAST(" + nullBranch(args.get(0), true) + " AS NULL), " + nullChain(args, 1) + ")";
        }
        if (!MEETING_ARGUMENTS.contains(name) && !name.equals("NULLIF")
                || args.size() != 2 && !name.equals("GREATEST") && !name.equals("LEAST")) {
            return null;
        }
        final StringBuilder text = new StringBuilder(expr.getFunctionName()).append('(');
        for (int i = 0; i < args.size(); i++) {
            text.append(i > 0 ? ", " : "").append(nullBranch(args.get(i), i == 0));
        }
        return text.append(')').toString();
    }

    /** Whether any of the arguments is an untyped NULL value ({@link #isNullValue}). */
    private boolean holdsNullValue(final List<Expression> args) {
        for (final Expression arg : args) {
            if (isNullValue(arg)) {
                return true;
            }
        }
        return false;
    }

    /** COALESCE's NULL arguments from {@code from} on, as the rest of its IFNULL chain. */
    private String nullChain(final List<Expression> args, final int from) {
        if (from == args.size() - 1) {
            return nullBranch(args.get(from), false);
        }
        return "IFNULL(" + nullBranch(args.get(from), true) + ", " + nullChain(args, from + 1) + ")";
    }

    /**
     * DECODE as the plan holds it: each search value converted to the subject's type where two exact numbers
     * differ, an untyped NULL subject typed by the search values, and a missing default the results' typed null.
     */
    private String plannedDecode(final List<Expression> args) {
        final Expression subject = args.get(0);
        final DataType subjectType = argumentType(subject);
        final List<Expression> searches = new ArrayList<>();
        final List<Expression> results = new ArrayList<>();
        for (int i = 1; i + 1 < args.size(); i += 2) {
            searches.add(args.get(i));
            results.add(args.get(i + 1));
        }
        final boolean hasDefault = args.size() % 2 == 0;
        // A subject folding to NULL takes the searches' family as the bare word does, its text inside:
        // DECODE(SYSTEM$NULL_TO_FIXED(NVL(CAST(null AS NULL), null)), 1, 2, 3) (live-verified).
        final String subjectFamily = isUntypedNull(subject) || foldsToNullByCall(subject)
            ? meetingFamily(searches) : null;
        // A NULL subject beside NULL searches has no type to take: the plan holds it as CAST(null AS NULL).
        final StringBuilder text = new StringBuilder("DECODE(")
            .append(isUntypedNull(subject) && subjectFamily == null && allNull(searches) ? "CAST(null AS NULL)"
                : typedArgument(subject, subjectFamily));
        final List<Expression> values = new ArrayList<>();
        for (final Expression result : results) {
            if (!isUntypedNull(result)) {
                values.add(result);
            }
        }
        if (hasDefault && !isUntypedNull(args.get(args.size() - 1))) {
            values.add(args.get(args.size() - 1));
        }
        final NumericType met = numericMeeting(values);
        // In an invalid-type sentence a NULL result beside a valued one is typed by it: DECODE(RT.N, COERCE(1 AS
        // NUMBER(38,0)), SYSTEM$NULL_TO_FIXED(NULL), 2) (live-verified).
        final String valuedFamily = conversionShaped() ? meetingFamily(values) : null;
        // Results that are all NULL meet in the NULL type, the first converted to it: DECODE(1, 1, CAST(null AS
        // NULL), 2, null, null), a missing default the bare word (live-verified).
        boolean nullResults = !hasDefault || isNullValue(args.get(args.size() - 1));
        for (final Expression result : results) {
            nullResults = nullResults && isNullValue(result);
        }
        for (int i = 0; i < searches.size(); i++) {
            final Expression search = searches.get(i);
            final DataType searchType = argumentType(search);
            final String printed = search.accept(this);
            final boolean converted = isExactNumber(subjectType) && isExactNumber(searchType)
                && !SqlTypeNames.canonical(subjectType).equals(SqlTypeNames.canonical(searchType));
            // The conversion is COERCE in an invalid-type sentence: COERCE(1 AS NUMBER(38,0)) (live-verified).
            text.append(", ").append(converted ? (conversionShaped() ? "COERCE(" : "CAST(") + printed + " AS "
                + SqlTypeNames.canonical(subjectType) + ")" : printed).append(", ")
                .append(nullResults ? nullBranch(results.get(i), i == 0)
                    : decodedResult(results.get(i), met, valuedFamily));
        }
        if (hasDefault) {
            text.append(", ").append(nullResults ? nullBranch(args.get(args.size() - 1), false)
                : decodedResult(args.get(args.size() - 1), met, valuedFamily));
        } else if (nullResults) {
            text.append(", null");
        } else {
            final String family = meetingFamily(results);
            if (family == null) {
                return null;
            }
            text.append(", SYSTEM$NULL_TO_").append(family).append("(null)");
        }
        return text.append(')').toString();
    }

    /** A DECODE result: a NULL typed by the valued results where a family is given, any other met or printed. */
    private String decodedResult(final Expression result, final NumericType met, final String valuedFamily) {
        return valuedFamily != null && isNullValue(result) ? typedArgument(result, valuedFamily)
            : metOrPrinted(result, met);
    }

    /**
     * The SCALAR calls live's plan spells by another name when it re-prints them — measured through the
     * conversion sentence a CASE condition earns and through the arity echo:
     *
     * <pre>
     *   COALESCE(g, 'a')            IFNULL(RT.G, 'a')
     *   COALESCE(g, 'a', 'b')       IFNULL(RT.G, IFNULL('a', 'b'))     nested to the right, any width
     *   LEFT(g, 2)                  SUBSTR(RT.G, 1, 2)
     *   RIGHT(g, 2)                 RIGHT2(RT.G, 2)
     *   TO_VARIANT(TRUE)            CAST(TRUE AS VARIANT)
     *   TO_DECIMAL('1')             TO_NUMBER('1')                      TO_NUMERIC too
     *   TO_TIMESTAMP(s, 'YYYY')     TO_TIMESTAMP_NTZ(s, 'YYYY')
     *   v:a  /  v['a']  /  v:a.b    GET(RT.V, 'a')  /  GET(GET(RT.V, 'a'), 'b')   (the path visitors)
     * </pre>
     *
     * <p>The interval shifts are their {@code DATE_ADD…} functions ({@link #plannedDateAdd}), and a
     * one-argument conversion is the cast it stands for ({@link #conversionAsCast}); DATEDIFF and its
     * twins, DATE_TRUNC and a temporal TRUNC, DATE and IFF are re-spelled too ({@link #plannedRespelling}).
     * IFNULL, NVL, SUBSTR, UPPER, LOWER, LENGTH, NULLIF, TIME, TO_NUMBER, a DATE over a text and the
     * niladic context functions print as written ({@code CURRENT_DATE()} with its parentheses either
     * way), and so does everything else.
     */
    private String planRewrittenScalar(final FunctionCallExpression expr, final String name) {
        if (expr.isDistinct() || expr.isStar() || expr.getNameExpression() != null) {
            return null;
        }
        final List<Expression> args = expr.getArguments();
        for (final Expression arg : args) {
            if (arg instanceof SpreadExpression) {
                return null;
            }
        }
        if (name.equals("COALESCE") && args.size() >= 2) {
            // Over NULLs alone the chain meets in the NULL type (see nullMetCall).
            final String nullMet = typesNulls() && isFoldedConditional(expr) ? nullMetCall(expr, name) : null;
            if (nullMet != null) {
                return nullMet;
            }
            final String previous = coalesceFamily;
            coalesceFamily = meetingFamily(args);
            try {
                return nestedIfNull(args, 0, false);
            } finally {
                coalesceFamily = previous;
            }
        }
        if (planShaped() && !hasNamedArgument(expr)) {
            final String planned = plannedNumericCall(name, args);
            if (planned != null) {
                return planned;
            }
        }
        final String converted = convertedArguments(expr, name);
        if (converted != null) {
            return converted;
        }
        final String met = metArguments(expr, name);
        if (met != null) {
            return met;
        }
        if (name.equals("HASH") && !hasNamedArgument(expr)) {
            // HASH converts nothing, so a bare NULL is held as the NULL type's own: HASH(CAST(null AS NULL), RT.N),
            // and a conditional folding to NULL likewise, HASH(CAST(NVL(CAST(null AS NULL), null) AS NULL), RT.N).
            boolean held = false;
            final StringBuilder text = new StringBuilder(expr.getFunctionName()).append('(');
            for (int i = 0; i < args.size(); i++) {
                held = held || isNullValue(args.get(i));
                text.append(i > 0 ? ", " : "").append(isNullValue(args.get(i)) ? nullBranch(args.get(i), true)
                    : args.get(i).accept(this));
            }
            if (held) {
                return text.append(')').toString();
            }
        }
        if (name.equals("NVL2") && args.size() == 3 && !hasNamedArgument(expr)) {
            // The plan holds NVL2 as the IFF it stands for, its first argument tested for NULL.
            final Expression tested = args.get(0);
            return "IFF(" + (isUntypedNull(tested) ? "CAST(null AS NULL)" : operand(tested)) + " IS NOT NULL, "
                + iffBranches(args.get(1), args.get(2)) + ")";
        }
        final String substring = substringRewrite(name, args);
        if (substring != null) {
            return substring;
        }
        if (name.equals("TO_VARIANT") && args.size() == 1) {
            // A VARIANT, a plain ARRAY and a plain OBJECT are a VARIANT's value already, and the plan holds them bare.
            final DataType held = argumentType(args.get(0));
            return held instanceof VariantType || isPlainContainer(held) ? args.get(0).accept(this)
                : "CAST(" + args.get(0).accept(this) + " AS VARIANT)";
        }
        if (name.equals("TO_DECIMAL") || name.equals("TO_NUMERIC")) {
            return renamedCall("TO_NUMBER", args);
        }
        if (name.equals("TO_TIMESTAMP") && args.size() == 2) {
            return renamedCall("TO_TIMESTAMP_NTZ", args);
        }
        final String respelled = plannedRespelling(expr, name);
        if (respelled != null) {
            return respelled;
        }
        final String dateAdd = plannedDateAdd(expr, name);
        return dateAdd != null ? dateAdd : conversionAsCast(expr, name);
    }

    /**
     * The numeric calls the plan holds in another shape (live-verified):
     *
     * <pre>
     *   NULLIF(g, 1)          IFF((TO_NUMBER(RT.G, 18, 5)) = (CAST(1 AS NUMBER(18,5))), SYSTEM$NULL_TO_TEXT(null), RT.G)
     *   NULLIF(n, g)          IFF((CAST(RT.N AS NUMBER(38,5))) = (TO_NUMBER(RT.G, 38, 5)), SYSTEM$NULL_TO_FIXED(null), RT.N)
     *   NULLIF(f, g)          NULLIF(RT.F, CAST(RT.G AS FLOAT))
     *   DIV0(n52, 2)          DIV0(CAST(RT.N52 AS NUMBER(11,8)), 2)       the division's rescaled dividend
     *   DIV0(n, 2)            SCALED_ROUND_INT_DIV0(RT.N, 2)              past 38 digits
     *   DIV0(f, 2)            DIV0(RT.F, CAST(2 AS FLOAT))
     *   DIV0NULL(n52, 3)      DIV0(CAST(RT.N52 AS NUMBER(11,8)), ZEROIFNULL(3))
     *   TO_NUMBER(n52)        CAST(RT.N52 AS NUMBER(38,0))                TO_DECIMAL and TO_NUMERIC alike
     *   TO_NUMBER(n, 10, 2)   CAST(RT.N AS NUMBER(10,2))
     *   TO_NUMBER(n)          RT.N                                         the cast changes nothing
     * </pre>
     *
     * <p>A NULLIF compares its two arguments as a text and a number meet ({@link #textOperandMeeting}) and,
     * where that converts one, is the IFF it stands for, its NULL typed as the first argument; a FLOAT beside
     * a text converts the text alone and stays a NULLIF. A conversion of a text prints as written. Null for
     * any other shape.
     */
    private String plannedNumericCall(final String name, final List<Expression> args) {
        if (name.equals("NULLIF") && args.size() == 2) {
            return nullIfAsPlanned(args.get(0), args.get(1));
        }
        if ((name.equals("DIV0") || name.equals("DIV0NULL")) && args.size() == 2) {
            return div0AsPlanned(args.get(0), args.get(1), name.equals("DIV0NULL"));
        }
        if (name.equals("TO_NUMBER") || name.equals("TO_DECIMAL") || name.equals("TO_NUMERIC")) {
            return numberToNumber(args);
        }
        return null;
    }

    private String nullIfAsPlanned(final Expression first, final Expression second) {
        final DataType firstType = argumentType(first);
        final DataType secondType = argumentType(second);
        final boolean firstText = isTextOperand(first, firstType);
        final boolean secondText = isTextOperand(second, secondType);
        if (firstText == secondText) {
            return null;
        }
        final DataType numberType = firstText ? secondType : firstType;
        if (!(numberType instanceof NumericType)) {
            return null;
        }
        if (!firstText && NumericType.isApproximate(numberType)) {
            return "NULLIF(" + first.accept(this) + ", CAST(" + second.accept(this) + " AS FLOAT))";
        }
        final String comparison = textOperandMeeting(BinaryOperator.EQUAL, first, firstType, second, secondType);
        return comparison == null ? null : "IFF(" + comparison + ", SYSTEM$NULL_TO_" + (firstText ? "TEXT" : "FIXED")
            + "(null), " + first.accept(this) + ")";
    }

    /**
     * DIV0 and DIV0NULL as the plan holds them: the division rule ({@link #exactDivision}) in its function
     * spelling, DIV0NULL being DIV0 over {@code ZEROIFNULL} of its divisor; a FLOAT beside an exact number
     * casts the exact one. An untyped NULL is the typed NULL of an exact number and a text reads as
     * NUMBER(18,5), as they do beside the operators. Null for any other operand.
     */
    private String div0AsPlanned(final Expression dividend, final Expression divisor, final boolean nullToZero) {
        final NumericType[] types = new NumericType[2];
        final String[] printed = new String[2];
        final Expression[] operands = {dividend, divisor};
        for (int i = 0; i < 2; i++) {
            final DataType type = argumentType(operands[i]);
            if (isUntypedNull(operands[i])) {
                types[i] = TYPED_NULL_FIXED;
                printed[i] = "SYSTEM$NULL_TO_FIXED(null)";
            } else if (isTextValue(operands[i], type)) {
                types[i] = TEXT_AS_NUMBER;
                printed[i] = "TO_NUMBER(" + operands[i].accept(this) + ", " + TEXT_AS_NUMBER.getPrecision() + ", "
                    + TEXT_AS_NUMBER.getScale() + ")";
            } else if (type instanceof NumericType) {
                types[i] = (NumericType) type;
                printed[i] = operands[i].accept(this);
            } else {
                return null;
            }
        }
        if (nullToZero) {
            printed[1] = "ZEROIFNULL(" + printed[1] + ")";
        }
        final boolean leftApproximate = NumericType.isApproximate(types[0]);
        final boolean rightApproximate = NumericType.isApproximate(types[1]);
        if (leftApproximate || rightApproximate) {
            if (types[0] == TEXT_AS_NUMBER || types[1] == TEXT_AS_NUMBER) {
                return null;
            }
            return "DIV0(" + (leftApproximate ? printed[0] : "CAST(" + printed[0] + " AS FLOAT)") + ", "
                + (rightApproximate ? printed[1] : "CAST(" + printed[1] + " AS FLOAT)") + ")";
        }
        final int scale = Math.max(types[0].getScale(),
            Math.min(types[0].getScale() + types[1].getScale() + DIVISION_EXTRA_SCALE, DIVISION_SCALE_CAP));
        final int precision = types[0].getPrecision() - types[0].getScale() + types[1].getScale() + scale;
        if (precision > MAX_PRECISION) {
            return "SCALED_ROUND_INT_DIV0(" + printed[0] + ", " + printed[1] + ")";
        }
        return "DIV0(" + fixedToFixed(printed[0], "NUMBER(" + precision + "," + scale + ")") + ", " + printed[1] + ")";
    }

    /**
     * A conversion of a NUMBER to a NUMBER as the cast it is — {@code NUMBER(38,0)} unless a precision and a
     * scale are written — or the number itself when the cast changes nothing. Null for a text to convert, a
     * format, or a width that is not a written integer.
     */
    private String numberToNumber(final List<Expression> args) {
        if (args.isEmpty() || args.size() > 3) {
            return null;
        }
        final DataType type = argumentType(args.get(0));
        if (!(type instanceof NumericType)) {
            return null;
        }
        final int[] width = {MAX_PRECISION, 0};
        for (int i = 1; i < args.size(); i++) {
            final Expression written = args.get(i);
            if (!(written instanceof LiteralExpression)
                    || ((LiteralExpression) written).getType() != LiteralType.INTEGER) {
                return null;
            }
            width[i - 1] = ((Number) ((LiteralExpression) written).getValue()).intValue();
        }
        final NumericType number = (NumericType) type;
        if (!NumericType.isApproximate(number) && number.getPrecision() == width[0] && number.getScale() == width[1]) {
            return args.get(0).accept(this);
        }
        return fixedToFixed(args.get(0).accept(this), "NUMBER(" + width[0] + "," + width[1] + ")");
    }

    /**
     * The calls both plan-shaped modes re-spell: DATEDIFF and its twins ({@link #plannedDateDiff}),
     * DATE_TRUNC and a temporal TRUNC ({@link #plannedTrunc}), DATE ({@link #plannedDate}) and IFF
     * ({@link #plannedIff}). Null for any other call.
     */
    private String plannedRespelling(final FunctionCallExpression expr, final String name) {
        final String difference = plannedDateDiff(expr, name);
        if (difference != null) {
            return difference;
        }
        final String truncation = plannedTrunc(expr, name);
        if (truncation != null) {
            return truncation;
        }
        final String date = plannedDate(expr, name);
        return date != null ? date : plannedIff(expr, name);
    }

    /**
     * The calls an invalid-type sentence re-prints under another spelling, or null for any other: the
     * IFNULL chain, TO_CHAR for a nested TO_VARCHAR, LEFT and RIGHT, the re-spelled calls of
     * {@link #plannedRespelling} and the interval shifts.
     */
    private String conversionRewrittenCall(final FunctionCallExpression expr, final String name) {
        if (expr.isDistinct() || expr.isStar() || expr.getNameExpression() != null) {
            return null;
        }
        final List<Expression> args = expr.getArguments();
        for (final Expression arg : args) {
            if (arg instanceof SpreadExpression) {
                return null;
            }
        }
        final String nullTarget = NULL_CONVERSIONS.get(name);
        if (nullTarget != null && !args.isEmpty() && isUntypedNull(args.get(0)) && !(expr instanceof BoundValueCall)
                && (args.size() == 1 || !"TEXT".equals(nullTarget))) {
            // A conversion of the bare word NULL is its target's typed NULL, whatever else it is given:
            // TO_NUMBER(NULL, 5) is SYSTEM$NULL_TO_FIXED(NULL), TO_DATE(NULL, 'YYYY') SYSTEM$NULL_TO_DATE(NULL). A
            // text conversion given a format is refused for its arguments, echoed as written: TO_CHAR(NULL, 'x').
            return "SYSTEM$NULL_TO_" + nullTarget + "(NULL)";
        }
        if (WHOLE_NUMBER_CONVERSIONS.contains(name) && args.size() == 1 && isWholeNumber(argumentType(args.get(0)))) {
            // A NUMBER(38,0) converted to the NUMBER(38,0) it is changes nothing: identity(RT.N).
            return "identity(" + args.get(0).accept(this) + ")";
        }
        if (name.equals("CONCAT") && args.size() >= 2) {
            final String joined = convertedConcat(args);
            if (joined != null) {
                return joined;
            }
        }
        if (name.equals("COALESCE") && args.size() >= 2) {
            // Over NULLs alone the chain meets in the NULL type: IFNULL(COERCE(COERCE(NULL AS NULL) AS NULL), NULL)
            // (live-verified, see nullMetCall).
            final String nullMet = isFoldedConditional(expr) ? nullMetCall(expr, name) : null;
            if (nullMet != null) {
                return nullMet;
            }
            boolean vector = false;
            for (final Expression arg : args) {
                vector = vector || argumentType(arg) instanceof VectorType;
            }
            return nestedIfNull(args, 0, vector);
        }
        if (name.equals("NVL2") && args.size() == 3 && !hasNamedArgument(expr) && holdsNullValue(args)) {
            // Over a NULL the sentence names the IFF the plan holds, as the other sentences do:
            // IFF(COERCE(NULL AS NULL) IS NOT NULL, 1, SYSTEM$NULL_TO_FIXED(NULL)) (live-verified).
            final Expression tested = args.get(0);
            return "IFF(" + (isUntypedNull(tested) ? "CAST(null AS NULL)" : operand(tested)) + " IS NOT NULL, "
                + iffBranches(args.get(1), args.get(2)) + ")";
        }
        if (name.equals("TO_VARCHAR") && args.size() == 1) {
            return "TO_CHAR(" + args.get(0).accept(this) + ")";
        }
        if (name.equals("TO_VARIANT") && args.size() == 1) {
            // Over the bare word NULL the plan's typed null; over a VARIANT, a plain ARRAY or a plain OBJECT a
            // conversion that changes nothing.
            if (isUntypedNull(args.get(0))) {
                return "SYSTEM$NULL_TO_VARIANT(NULL)";
            }
            final DataType held = argumentType(args.get(0));
            if (held instanceof VariantType || isPlainContainer(held)) {
                return "identity(" + args.get(0).accept(this) + ")";
            }
            if (StructuredTypes.isStructured(held)) {
                return "STRUCTURED_CAST(" + args.get(0).accept(this) + ")";
            }
        }
        if ((name.equals("TO_ARRAY") || name.equals("TO_OBJECT")) && args.size() == 1) {
            final String kept = keptContainer(name, args.get(0));
            if (kept != null) {
                return kept;
            }
        }
        final String substring = substringRewrite(name, args);
        if (substring != null) {
            return substring;
        }
        final String respelled = plannedRespelling(expr, name);
        return respelled != null ? respelled : plannedDateAdd(expr, name);
    }

    /**
     * CONCAT over a value that is no text, as an invalid-type sentence converts it: {@code CONCAT(TO_CHAR(1), 'a')}
     * (live-verified; see {@link #textConversion}). Null where every argument is a text, or one is NULL or of a
     * type not measured.
     */
    private String convertedConcat(final List<Expression> args) {
        final StringBuilder joined = new StringBuilder("CONCAT(");
        boolean converted = false;
        for (int i = 0; i < args.size(); i++) {
            final Expression arg = args.get(i);
            final DataType type = argumentType(arg);
            if (isNullValue(arg) || type == null) {
                return null;
            }
            joined.append(i > 0 ? ", " : "");
            if (type instanceof StringType) {
                joined.append(arg.accept(this));
                continue;
            }
            final String conversion = textConversion(type);
            if (conversion == null) {
                return null;
            }
            joined.append(conversion).append('(').append(arg.accept(this)).append(')');
            converted = true;
        }
        return converted ? joined.append(')').toString() : null;
    }

    /** Whether a type is the NUMBER(38,0) a conversion to a number without a width answers. */
    private static boolean isWholeNumber(final DataType type) {
        return isExactNumber(type) && ((NumericType) type).getPrecision() == MAX_PRECISION
            && ((NumericType) type).getScale() == 0;
    }

    /** The conversions to a number that, given no width, answer NUMBER(38,0). */
    private static final Set<String> WHOLE_NUMBER_CONVERSIONS = new HashSet<>(Arrays.asList(
        "TO_NUMBER", "TO_DECIMAL", "TO_NUMERIC"));

    /** The conversions and the family of the typed NULL each makes of the bare word NULL (live-verified). */
    private static final Map<String, String> NULL_CONVERSIONS = new HashMap<>();

    static {
        NULL_CONVERSIONS.put("TO_NUMBER", "FIXED");
        NULL_CONVERSIONS.put("TO_DECIMAL", "FIXED");
        NULL_CONVERSIONS.put("TO_NUMERIC", "FIXED");
        NULL_CONVERSIONS.put("TO_DOUBLE", "REAL");
        NULL_CONVERSIONS.put("TO_BOOLEAN", "BOOLEAN");
        NULL_CONVERSIONS.put("TO_VARCHAR", "TEXT");
        NULL_CONVERSIONS.put("TO_CHAR", "TEXT");
        NULL_CONVERSIONS.put("TO_BINARY", "BINARY");
        NULL_CONVERSIONS.put("TO_ARRAY", "ARRAY");
        NULL_CONVERSIONS.put("TO_DATE", "DATE");
        NULL_CONVERSIONS.put("TO_TIME", "TIME");
        NULL_CONVERSIONS.put("TO_TIMESTAMP", "TIMESTAMP_NTZ");
        NULL_CONVERSIONS.put("TO_TIMESTAMP_NTZ", "TIMESTAMP_NTZ");
        NULL_CONVERSIONS.put("TO_TIMESTAMP_LTZ", "TIMESTAMP_LTZ");
        NULL_CONVERSIONS.put("TO_TIMESTAMP_TZ", "TIMESTAMP_TZ");
    }

    /** The functions the plan computes in FLOAT, each with the one argument count it takes. */
    private static final Map<String, Integer> FLOAT_ARGUMENT_FUNCTIONS = new HashMap<>();
    /** The rounding family, which reads a text or VARIANT as a FLOAT and a text given a scale as NUMBER(18,5). */
    private static final Set<String> ROUNDING_FAMILY = new HashSet<>(Arrays.asList(
        "ABS", "CEIL", "FLOOR", "ROUND", "SIGN"));
    /** The string functions whose first argument the plan reads as text, converting any other scalar to it. */
    private static final Set<String> TEXT_ARGUMENT_FUNCTIONS = new HashSet<>(Arrays.asList(
        "UPPER", "LOWER", "TRIM", "LTRIM", "RTRIM", "REVERSE", "INITCAP", "LENGTH", "LEN", "SUBSTR", "SUBSTRING",
        "REPLACE", "LPAD", "RPAD"));
    /** The calls whose arguments all meet in one number, each moved to it where it differs. */
    private static final Set<String> MEETING_NUMBERS = new HashSet<>(Arrays.asList(
        "NULLIF", "IFNULL", "NVL", "GREATEST", "LEAST"));

    static {
        for (final String unary : Arrays.asList("SQRT", "CBRT", "EXP", "LN", "SQUARE", "SIN", "COS", "TAN", "COT",
                "ASIN", "ACOS", "ATAN", "SINH", "COSH", "TANH", "ASINH", "ACOSH", "ATANH", "DEGREES", "RADIANS")) {
            FLOAT_ARGUMENT_FUNCTIONS.put(unary, Integer.valueOf(1));
        }
        for (final String binary : Arrays.asList("LOG", "POW", "POWER", "ATAN2")) {
            FLOAT_ARGUMENT_FUNCTIONS.put(binary, Integer.valueOf(2));
        }
        FLOAT_ARGUMENT_FUNCTIONS.put("HAVERSINE", Integer.valueOf(4));
    }

    /**
     * A call whose arguments the plan CONVERTS to the family the function computes in, printed with those
     * conversions — live-verified through the constant-argument, arity and predicate echoes:
     *
     * <pre>
     *   SQRT(n)            SQRT(CAST(RT.N AS FLOAT))                    every FLOAT function, over an exact number,
     *   POWER(2, n52)      POWER(CAST(2 AS FLOAT), CAST(RT.N52 AS FLOAT))   a text or a VARIANT; a FLOAT stays bare
     *   ABS(g)             ABS(CAST(RT.G AS FLOAT))                     the rounding family over a text or a VARIANT
     *   ROUND(g, 1)        ROUND(TO_NUMBER(RT.G, 18, 5), 1)             a text given a scale reads as NUMBER(18,5)
     *   UPPER(d)           UPPER(CAST(RT.D AS VARCHAR(134217728)))      a string function over a number, a date or
     *                                                                   time, a BOOLEAN or a VARIANT
     * </pre>
     *
     * <p>Null for any other call, and for one written with another number of arguments than it takes, whose
     * arity sentence prints its arguments unconverted.
     */
    private String convertedArguments(final FunctionCallExpression expr, final String name) {
        final List<Expression> args = expr.getArguments();
        if (args.isEmpty() || hasNamedArgument(expr)) {
            return null;
        }
        final Integer floatArity = FLOAT_ARGUMENT_FUNCTIONS.get(name);
        if (floatArity != null) {
            if (args.size() != floatArity.intValue()) {
                return null;
            }
            final StringBuilder text = new StringBuilder(expr.getFunctionName()).append('(');
            for (int i = 0; i < args.size(); i++) {
                text.append(i > 0 ? ", " : "").append(floatArgument(args.get(i)));
            }
            return text.append(')').toString();
        }
        final Expression first = args.get(0);
        if (isUntypedNull(first)) {
            return null;
        }
        final DataType type = argumentType(first);
        if (ROUNDING_FAMILY.contains(name) && (type instanceof StringType || type instanceof VariantType)) {
            final int most = name.equals("ABS") || name.equals("SIGN") ? 1 : 2;
            if (args.size() > most) {
                return null;
            }
            return withFirstArgument(expr, args.size() == 2 && type instanceof StringType
                ? "TO_NUMBER(" + first.accept(this) + ", 18, 5)" : "CAST(" + first.accept(this) + " AS FLOAT)");
        }
        if (TEXT_ARGUMENT_FUNCTIONS.contains(name) && convertsToVarchar(type)) {
            return withFirstArgument(expr, textOf(first));
        }
        return null;
    }

    /** An argument of a FLOAT function as the plan holds it: an exact number, a text or a VARIANT cast to FLOAT. */
    private String floatArgument(final Expression argument) {
        if (isUntypedNull(argument)) {
            return "SYSTEM$NULL_TO_REAL(null)";
        }
        final DataType type = argumentType(argument);
        return isExactNumber(type) || type instanceof StringType || type instanceof VariantType
            ? "CAST(" + argument.accept(this) + " AS FLOAT)" : argument.accept(this);
    }

    /** Whether the plan converts a value of this type to text for a string function: a number, a date or time, a BOOLEAN, a VARIANT. */
    private static boolean convertsToVarchar(final DataType type) {
        return type instanceof NumericType || type instanceof DateTimeType || type instanceof BooleanType
            || type instanceof VariantType;
    }

    /** A value converted to the text a string function reads. */
    private String textOf(final Expression value) {
        return "CAST(" + value.accept(this) + " AS VARCHAR(" + DataTypeParser.CAST_STRING_DEFAULT + "))";
    }

    /** The call under its written name, its first argument as given and the rest printed. */
    private String withFirstArgument(final FunctionCallExpression expr, final String first) {
        final StringBuilder text = new StringBuilder(expr.getFunctionName()).append('(').append(first);
        for (int i = 1; i < expr.getArguments().size(); i++) {
            text.append(", ").append(expr.getArguments().get(i).accept(this));
        }
        return text.append(')').toString();
    }

    private static boolean hasNamedArgument(final FunctionCallExpression expr) {
        if (expr.getArgumentNames() == null) {
            return false;
        }
        for (final String argumentName : expr.getArgumentNames()) {
            if (argumentName != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * NULLIF, IFNULL, NVL, GREATEST and LEAST over numbers, as the plan holds them: every argument met in one
     * number — an exact one beside a FLOAT is {@code CAST(x AS FLOAT)}, and of exact numbers each of a lower
     * scale is moved to the widest integer part at the highest scale, {@code NULLIF(RT.N52, CAST(1 AS
     * NUMBER(5,2)))}, {@code GREATEST(CAST(1 AS NUMBER(38,2)), RT.N52, CAST(RT.N AS NUMBER(38,2)))}
     * (live-verified). A precision alone moves nothing. Null when an argument is no number or is the bare word
     * NULL, or when nothing moves.
     */
    private String metArguments(final FunctionCallExpression expr, final String name) {
        final List<Expression> args = expr.getArguments();
        if (!MEETING_NUMBERS.contains(name) || args.size() < 2 || hasNamedArgument(expr)
                || (name.equals("NULLIF") || name.equals("IFNULL") || name.equals("NVL")) && args.size() != 2) {
            return null;
        }
        final NumericType met = numericMeeting(args);
        if (met == null) {
            return null;
        }
        final StringBuilder text = new StringBuilder(expr.getFunctionName()).append('(');
        boolean moved = false;
        for (int i = 0; i < args.size(); i++) {
            final String printed = metNumber(args.get(i), met);
            moved = moved || printed != null;
            text.append(i > 0 ? ", " : "").append(printed != null ? printed : args.get(i).accept(this));
        }
        return moved ? text.append(')').toString() : null;
    }

    /**
     * The number values meet in: FLOAT beside any FLOAT, else the widest integer part at the highest scale, to 38
     * digits. Null when a value is no number, or the bare word NULL.
     */
    private NumericType numericMeeting(final List<Expression> values) {
        boolean approximate = false;
        int integerDigits = 0;
        int scale = 0;
        for (final Expression value : values) {
            final DataType type = isUntypedNull(value) ? null : argumentType(value);
            if (!(type instanceof NumericType)) {
                return null;
            }
            final NumericType number = (NumericType) type;
            if (NumericType.isApproximate(number)) {
                approximate = true;
                continue;
            }
            integerDigits = Math.max(integerDigits, number.getPrecision() - number.getScale());
            scale = Math.max(scale, number.getScale());
        }
        return approximate ? NumericType.FLOAT
            : new NumericType("NUMBER", Math.min(MAX_PRECISION, integerDigits + scale), scale);
    }

    /** A value moved to the number values meet in, or null where the move changes nothing. */
    private String metNumber(final Expression value, final NumericType met) {
        final DataType type = argumentType(value);
        if (!isExactNumber(type)) {
            return null;
        }
        if (NumericType.isApproximate(met)) {
            return conversionShaped() ? "TO_DOUBLE(" + value.accept(this) + ")" : "CAST(" + value.accept(this) + " AS FLOAT)";
        }
        return ((NumericType) type).getScale() == met.getScale() ? null
            : fixedToFixed(value.accept(this), "NUMBER(" + met.getPrecision() + "," + met.getScale() + ")");
    }

    private String renamedCall(final String name, final List<Expression> args) {
        final StringBuilder text = new StringBuilder(name).append('(');
        for (int i = 0; i < args.size(); i++) {
            text.append(i > 0 ? ", " : "").append(args.get(i).accept(this));
        }
        return text.append(')').toString();
    }

    /**
     * LEFT and RIGHT as the plan names them, in both plan-shaped modes: {@code SUBSTR(RT.G, 1, 2)} and
     * {@code RIGHT2(RT.G, 2)}. Null for any other call.
     */
    private String substringRewrite(final String name, final List<Expression> args) {
        if (name.equals("LEFT") && args.size() == 2) {
            return "SUBSTR(" + cutValue(args.get(0)) + ", 1, " + args.get(1).accept(this) + ")";
        }
        if (name.equals("RIGHT") && args.size() == 2) {
            return "RIGHT2(" + cutValue(args.get(0)) + ", " + args.get(1).accept(this) + ")";
        }
        return null;
    }

    /** The value LEFT or RIGHT cuts: in the plan a scalar that is no text is converted to text first. */
    private String cutValue(final Expression value) {
        return planShaped() && !isUntypedNull(value) && convertsToVarchar(argumentType(value)) ? textOf(value)
            : value.accept(this);
    }

    /** The interval unit a unit slot names, or null when it names none an interval function takes. */
    private static IntervalUnit slotUnit(final Expression unitArgument) {
        final String unitText = DateTimeUnitSlot.unitTextOf(unitArgument);
        if (unitText == null || SharedFunctionHelpers.isComponentOnlyUnit(unitText)) {
            return null;
        }
        return IntervalUnit.fromSpelling(SharedFunctionHelpers.canonicalDateUnit(unitText));
    }

    /**
     * DATEDIFF, TIMEDIFF and TIMESTAMPDIFF as the plan names them, {@code DATE_DIFF<KIND>IN<UNITS>(from,
     * to)}, the unit in the plural and in full:
     *
     * <pre>
     *   DATEDIFF(day, d, d2)       DATE_DIFFDATEINDAYS(FT.D, FT.D2)       a DATE pair stays DATE, for hours too
     *   DATEDIFF(hour, tm, tm)     DATE_DIFFTIMEINHOURS(FT.TM, FT.TM)
     *   DATEDIFF(day, ts, ts2)     DATE_DIFFTIMESTAMPINDAYS(FT.TS, FT.TS2)
     *   DATEDIFF(day, d, ts)       DATE_DIFFTIMESTAMPINDAYS(CAST(FT.D AS TIMESTAMP_NTZ(9)), FT.TS)
     *   DATEDIFF(day, ts, tl)      DATE_DIFFTIMESTAMPINDAYS(CAST(FT.TS AS TIMESTAMP_LTZ(9)), FT.TL)
     *   DATEDIFF(day, g, d)        DATE_DIFFDATEINDAYS(CAST(FT.G AS DATE), FT.D)          a text or a VARIANT
     *   DATEDIFF(day, g, g)        DATE_DIFFTIMESTAMPINDAYS(CAST(FT.G AS TIMESTAMP_NTZ(9)), CAST(FT.G AS …))
     *   DATEDIFF(day, d, NULL)     null
     * </pre>
     *
     * <p>A TIMESTAMP beside anything puts the pair in their common timestamp ({@link #commonTimestamp}),
     * every operand not already of that very type moved to it. An invalid-type sentence names the moves
     * by their functions, {@code TO_TIMESTAMP_NTZ(FT.D)} and {@code TO_DATE(FT.G)}. Any other pair — a TIME
     * beside anything but a TIME, a number — is refused by the account and prints as written.
     */
    private String plannedDateDiff(final FunctionCallExpression expr, final String name) {
        final List<Expression> args = expr.getArguments();
        if (!(name.equals("DATEDIFF") || name.equals("TIMEDIFF") || name.equals("TIMESTAMPDIFF"))
                || args.size() != 3) {
            return null;
        }
        final IntervalUnit unit = slotUnit(args.get(0));
        if (unit == null) {
            return null;
        }
        final Expression from = args.get(1);
        final Expression to = args.get(2);
        if (isUntypedNull(from) || isUntypedNull(to)) {
            return conversionShaped() ? null : "null";
        }
        final DataType fromType = argumentType(from);
        final DataType toType = argumentType(to);
        final String kind;
        final String target;
        if (isDate(fromType) && isDate(toType)) {
            kind = "DATE";
            target = null;
        } else if (isTime(fromType) && isTime(toType)) {
            kind = "TIME";
            target = null;
        } else if ((isTimestamp(fromType) || isTimestamp(toType))
                && movableToTimestamp(fromType) && movableToTimestamp(toType)) {
            kind = "TIMESTAMP";
            target = commonTimestamp(fromType, toType);
        } else if ((isDate(fromType) && isTextual(toType)) || (isTextual(fromType) && isDate(toType))) {
            kind = "DATE";
            target = "DATE";
        } else if (isTextual(fromType) && isTextual(toType)) {
            kind = "TIMESTAMP";
            target = commonTimestamp(fromType, toType);
        } else {
            return null;
        }
        final String printedFrom = target == null ? from.accept(this) : movedTo(from, fromType, target);
        final String printedTo = target == null ? to.accept(this) : movedTo(to, toType, target);
        return "DATE_DIFF" + kind + "IN" + unit.name() + "S(" + printedFrom + ", " + printedTo + ")";
    }

    /** A text or a plain VARIANT — the families the plan converts to a date or a timestamp on demand. */
    private static boolean isTextual(final DataType type) {
        return type instanceof StringType || isPlainVariant(type);
    }

    /** Whether a value moves to a TIMESTAMP beside another: a date, a timestamp, a text or a VARIANT. */
    private static boolean movableToTimestamp(final DataType type) {
        return isDate(type) || isTimestamp(type) || isTextual(type);
    }

    /**
     * DATE_TRUNC and a temporal TRUNC as the plan names them, {@code TRUNC<KIND>TO<UNIT>(x)}, or the value
     * itself where the truncation changes nothing — the plan drops it, and an invalid-type sentence names
     * it {@code identity(x)}:
     *
     * <pre>
     *   DATE_TRUNC('month', d)          TRUNCDATETOMonth(FT.D)          YEAR, QUARTER, Month, Week
     *   DATE_TRUNC('day', d)            FT.D                            every unit of a day or less
     *   DATE_TRUNC('day', ts)           TRUNCTIMESTAMPTODay(FT.TS)      YEAR, Quarter, Month, Week, Day, HOUR …
     *   DATE_TRUNC('nanosecond', ts)    FT.TS                           a TIME alike
     *   DATE_TRUNC('hour', tm)          TRUNCTIMETOHOUR(FT.TM)          HOUR … MICROSECOND
     *   TRUNC(d, 'month')               TRUNCDATETOMonth(FT.D)
     *   DATE_TRUNC('month', NULL)       null
     * </pre>
     *
     * <p>The unit's spelling is the plan's own, capitals and all. A TIMESTAMP of another precision is first
     * moved to its flavour at the plan's precision, {@code TRUNCTIMESTAMPTOMonth(CAST(FT.TS3 AS
     * TIMESTAMP_NTZ(9)))}. Any other value — a text, a VARIANT — is refused by the account.
     */
    private String plannedTrunc(final FunctionCallExpression expr, final String name) {
        final List<Expression> args = expr.getArguments();
        if (args.size() != 2) {
            return null;
        }
        final Expression unitArgument;
        final Expression value;
        if (name.equals("DATE_TRUNC")) {
            unitArgument = args.get(0);
            value = args.get(1);
        } else if (name.equals("TRUNC") && isStringLiteral(args.get(1))) {
            unitArgument = args.get(1);
            value = args.get(0);
        } else {
            return null;
        }
        final IntervalUnit unit = slotUnit(unitArgument);
        if (unit == null) {
            return null;
        }
        if (isUntypedNull(value)) {
            return conversionShaped() || !name.equals("DATE_TRUNC") ? null : "null";
        }
        final DataType type = argumentType(value);
        final String kind;
        final String operand;
        if (isDate(type)) {
            kind = "DATE";
            operand = value.accept(this);
        } else if (isTime(type)) {
            kind = "TIME";
            operand = value.accept(this);
        } else if (isTimestamp(type)) {
            kind = "TIMESTAMP";
            operand = movedTo(value, type, timestampFlavour(type) + PLAN_TIMESTAMP_PRECISION);
        } else {
            return null;
        }
        final String truncation = truncationName(kind, unit);
        if (truncation == null) {
            return null;
        }
        if (truncation.isEmpty()) {
            return conversionShaped() ? "identity(" + operand + ")" : operand;
        }
        return "TRUNC" + kind + "TO" + truncation + "(" + operand + ")";
    }

    /**
     * The unit a truncation of {@code kind} is planned to, spelled as the plan spells it — the capitals
     * are the plan's own — "" where the truncation changes nothing, and null where the account refuses it.
     */
    private static String truncationName(final String kind, final IntervalUnit unit) {
        final boolean time = "TIME".equals(kind);
        final boolean date = "DATE".equals(kind);
        switch (unit) {
            case YEAR:
                return time ? null : "YEAR";
            case QUARTER:
                return time ? null : date ? "QUARTER" : "Quarter";
            case MONTH:
                return time ? null : "Month";
            case WEEK:
                return time ? null : "Week";
            case DAY:
                return time ? null : date ? "" : "Day";
            case NANOSECOND:
                return "";
            default:
                return date ? "" : unit.name();
        }
    }

    /**
     * DATE as the plan holds it, its argument converted to what the function reads:
     *
     * <pre>
     *   DATE(d)                  DATE(CAST(FT.D AS TIMESTAMP_LTZ(9)))     TO_TIMESTAMP_LTZ(FT.D) in an invalid-type sentence
     *   DATE(ts)   DATE(g)       DATE(FT.TS)   DATE(FT.G)                 a timestamp at the plan's precision, a text as it is
     *   DATE(n)    DATE(v)       DATE(CAST(FT.N AS VARCHAR(134217728)))   TO_CHAR(FT.N)
     *   DATE(NULL)               DATE(SYSTEM$NULL_TO_TIMESTAMP_LTZ(null))
     *   DATE(d, 'YYYY-MM-DD')    DATE(CAST(FT.D AS VARCHAR(134217728)), 'YYYY-MM-DD')   beside a format only a text is read as it is
     * </pre>
     */
    private String plannedDate(final FunctionCallExpression expr, final String name) {
        final List<Expression> args = expr.getArguments();
        final List<String> names = expr.getArgumentNames();
        if (!name.equals("DATE") || args.isEmpty() || args.size() > 2 || (names != null && names.get(0) != null)) {
            return null;
        }
        final Expression source = args.get(0);
        final boolean formatted = args.size() == 2;
        final String printed;
        if (isUntypedNull(source)) {
            if (formatted) {
                return null;
            }
            printed = "SYSTEM$NULL_TO_TIMESTAMP_LTZ(" + (conversionShaped() ? "NULL" : "null") + ")";
        } else {
            final DataType type = argumentType(source);
            if (type instanceof StringType) {
                printed = source.accept(this);
            } else if (!formatted && isDate(type)) {
                printed = converted(source.accept(this), "TIMESTAMP_LTZ" + PLAN_TIMESTAMP_PRECISION);
            } else if (!formatted && isTimestamp(type)) {
                printed = movedTo(source, type, timestampFlavour(type) + PLAN_TIMESTAMP_PRECISION);
            } else if ((!formatted && (isExactNumber(type) || isPlainVariant(type)))
                    || (formatted && (isDate(type) || isTimestamp(type) || isPlainVariant(type)))) {
                printed = converted(source.accept(this), TEXT_TARGET);
            } else {
                return null;
            }
        }
        return formatted ? "DATE(" + printed + ", " + args.get(1).accept(this) + ")" : "DATE(" + printed + ")";
    }

    /**
     * IFF as the plan holds it: its condition as a condition ({@link #conditionText}) —
     * {@code IFF(CAST(RT.B AS BOOLEAN), 1, 2)}, in an invalid-type sentence
     * {@code IFF(BOOLEAN_TO_ROWINDEX(RT.B), 1, 2)} — and its two branches met in one type: of two exact
     * numbers with different scales the lower-scale branch is moved to the higher scale,
     * {@code CAST(2 AS NUMBER(2,1))} or {@code FIXED_TO_FIXED(2 AS NUMBER(2,1)[UNKNOWN])}, and in the plan
     * an untyped NULL branch is the other branch's typed null, {@code SYSTEM$NULL_TO_TEXT(null)}. A
     * condition of another family is refused by the account and the call prints as written.
     */
    private String plannedIff(final FunctionCallExpression expr, final String name) {
        final List<Expression> args = expr.getArguments();
        if (!name.equals("IFF") || args.size() != 3) {
            return null;
        }
        final DataType conditionType = argumentType(args.get(0));
        if (conditionType != null && !(conditionType instanceof BooleanType)) {
            return null;
        }
        return "IFF(" + conditionText(args.get(0), true) + ", " + iffBranches(args.get(1), args.get(2)) + ")";
    }

    private String iffBranches(final Expression first, final Expression second) {
        if (typesNulls() && isNullValue(first) && isNullValue(second)) {
            // Two NULL branches meet in no type: IFF(RT.N > 1, CAST(null AS NULL), null) (live-verified).
            return nullBranch(first, true) + ", " + nullBranch(second, false);
        }
        final DataType firstType = argumentType(first);
        final DataType secondType = argumentType(second);
        String firstText = null;
        String secondText = null;
        // A NULL branch is the other's typed null, a conditional folding to NULL with its text inside:
        // IFF(RT.N > 0, SYSTEM$NULL_TO_FIXED(NVL(CAST(null AS NULL), null)), 1) (live-verified).
        if (typesNulls() && isNullValue(first) && typedNullFamily(secondType) != null) {
            firstText = typedArgument(first, typedNullFamily(secondType));
        }
        if (typesNulls() && isNullValue(second) && typedNullFamily(firstType) != null) {
            secondText = typedArgument(second, typedNullFamily(firstType));
        }
        if (isExactNumber(firstType) && isExactNumber(secondType)) {
            final NumericType a = (NumericType) firstType;
            final NumericType b = (NumericType) secondType;
            if (a.getScale() < b.getScale()) {
                firstText = branchMovedTo(first, a, b);
            } else if (b.getScale() < a.getScale()) {
                secondText = branchMovedTo(second, b, a);
            }
        } else if (firstType instanceof NumericType && secondType instanceof NumericType) {
            // An exact branch beside a FLOAT is moved to FLOAT: IFF(RT.N > 0, RT.F, CAST(1 AS FLOAT)).
            final List<Expression> branches = Arrays.asList(first, second);
            final NumericType met = numericMeeting(branches);
            firstText = met == null ? null : metNumber(first, met);
            secondText = met == null ? null : metNumber(second, met);
        }
        return (firstText != null ? firstText : first.accept(this)) + ", "
            + (secondText != null ? secondText : second.accept(this));
    }

    /** A value moved to the number its siblings meet in, or printed as it is. */
    private String metOrPrinted(final Expression value, final NumericType met) {
        final String moved = met == null || isUntypedNull(value) ? null : metNumber(value, met);
        return moved != null ? moved : value.accept(this);
    }

    /** A branch moved to the branches' common exact type: the wider integer part at the higher scale. */
    private String branchMovedTo(final Expression lower, final NumericType lowType, final NumericType highType) {
        final int integerDigits = Math.max(lowType.getPrecision() - lowType.getScale(),
            highType.getPrecision() - highType.getScale());
        return fixedToFixed(lower.accept(this), "NUMBER(" + Math.min(MAX_PRECISION, integerDigits + highType.getScale())
            + "," + highType.getScale() + ")");
    }

    /**
     * A condition — IFF's first argument, a WHERE, a HAVING, a QUALIFY or an ON — as the plan holds it. A
     * predicate prints as it is: a comparison, IS NULL, LIKE, IN, BETWEEN, a NOT of one, an AND or OR over
     * one ({@link #spellsOutAPredicate}). Anything else is converted to a boolean first,
     * {@code CAST(RT.B AS BOOLEAN)} in the plan and {@code BOOLEAN_TO_ROWINDEX(RT.B)} in an invalid-type
     * sentence. An AND or OR chain is read whole: over at least one predicate each operand is judged on
     * its own and bracketed, {@code (RT.N > 1) AND (RT.N < 5) AND (CAST(RT.B AS BOOLEAN))}; over none the
     * chain is converted as one, {@code CAST(RT.B AND RT.B AS BOOLEAN)}. A literal is converted only where
     * {@code convertLiterals} says so — IFF's {@code CAST(TRUE AS BOOLEAN)}, a WHERE's bare {@code TRUE}.
     */
    String conditionText(final Expression condition, final boolean convertLiterals) {
        if (isConnective(condition)) {
            final BinaryOperator connective = ((BinaryOperationExpression) condition).getOperator();
            final List<Expression> operands = new ArrayList<>();
            flattenConnective(condition, connective, operands);
            boolean predicate = false;
            for (final Expression operand : operands) {
                predicate = predicate || spellsOutAPredicate(operand);
            }
            if (!predicate) {
                return asBooleanCondition(condition);
            }
            final StringBuilder text = new StringBuilder();
            for (int i = 0; i < operands.size(); i++) {
                final Expression operand = operands.get(i);
                text.append(i > 0 ? " " + symbol(connective) + " " : "").append('(')
                    .append(isConnective(operand) ? conditionText(operand, convertLiterals)
                        : spellsOutAPredicate(operand) ? operand.accept(this) : asBooleanCondition(operand))
                    .append(')');
            }
            return text.toString();
        }
        if (spellsOutAPredicate(condition) || (!convertLiterals && condition instanceof LiteralExpression)
                || negatesAFoldedNull(condition)) {
            return condition.accept(this);
        }
        return asBooleanCondition(condition);
    }

    private static boolean isConnective(final Expression expression) {
        return expression instanceof BinaryOperationExpression
            && (((BinaryOperationExpression) expression).getOperator() == BinaryOperator.AND
                || ((BinaryOperationExpression) expression).getOperator() == BinaryOperator.OR);
    }

    /** One connective's chain of operands, left to right, however the parser nested them. */
    private static void flattenConnective(final Expression expression, final BinaryOperator connective,
                                          final List<Expression> operands) {
        if (expression instanceof BinaryOperationExpression
                && ((BinaryOperationExpression) expression).getOperator() == connective) {
            flattenConnective(((BinaryOperationExpression) expression).getLeft(), connective, operands);
            flattenConnective(((BinaryOperationExpression) expression).getRight(), connective, operands);
        } else {
            operands.add(expression);
        }
    }

    /**
     * A boolean that is no predicate, converted to one: CAST in the plan, BOOLEAN_TO_ROWINDEX in an invalid-type
     * sentence. A call folding to an untyped NULL is converted as the NULL it is, its text inside: {@code
     * SYSTEM$NULL_TO_ROWINDEX(NVL(CAST(null AS NULL), null))}.
     */
    private String asBooleanCondition(final Expression condition) {
        if (planShaped() && foldsToNullByCall(condition)) {
            return typedArgument(condition, "ROWINDEX");
        }
        final String printed = condition.accept(this);
        return conversionShaped() ? "BOOLEAN_TO_ROWINDEX(" + printed + ")" : "CAST(" + printed + " AS BOOLEAN)";
    }

    private static boolean isStringLiteral(final Expression expression) {
        return expression instanceof LiteralExpression
            && ((LiteralExpression) expression).getType() == LiteralType.STRING;
    }

    /**
     * COALESCE's arguments from {@code from} on, as the right-nested IFNULL chain live plans. Over
     * VECTOR branches a bare NULL is the vector's typed null, {@code SYSTEM$NULL_TO_VECTOR(NULL)}.
     */
    private String nestedIfNull(final List<Expression> args, final int from, final boolean vectorNulls) {
        final Expression head = args.get(from);
        final String printed = vectorNulls && isUntypedNull(head) ? "SYSTEM$NULL_TO_VECTOR(NULL)"
            : typesNulls() && coalesceFamily != null ? typedArgument(head, coalesceFamily) : head.accept(this);
        if (from == args.size() - 1) {
            return printed;
        }
        final String rest = nestedIfNull(args, from + 1, vectorNulls);
        // Over numbers each IFNULL meets its two sides: IFNULL(CAST(RT.N AS NUMBER(38,2)), IFNULL(RT.N52,
        // CAST(1 AS NUMBER(5,2)))) for COALESCE(n, n52, 1), the inner pair meeting first (live-verified).
        final NumericType met = planShaped() && !vectorNulls ? numericMeeting(args.subList(from, args.size())) : null;
        if (met != null) {
            final String headMoved = metNumber(head, met);
            final NumericType restMet = numericMeeting(args.subList(from + 1, args.size()));
            return "IFNULL(" + (headMoved != null ? headMoved : printed) + ", " + movedText(rest, restMet, met) + ")";
        }
        return "IFNULL(" + printed + ", " + rest + ")";
    }

    /** Printed text of one number moved to another: an exact one to FLOAT, or to another scale; else as it is. */
    private String movedText(final String printed, final NumericType from, final NumericType to) {
        if (NumericType.isApproximate(from)) {
            return printed;
        }
        if (NumericType.isApproximate(to)) {
            return conversionShaped() ? "TO_DOUBLE(" + printed + ")" : "CAST(" + printed + " AS FLOAT)";
        }
        return from.getScale() == to.getScale() ? printed
            : fixedToFixed(printed, "NUMBER(" + to.getPrecision() + "," + to.getScale() + ")");
    }

    /**
     * DATEADD, TIMEADD and TIMESTAMPADD as the plan names them, {@code DATE_ADD<UNITS>TO<KIND>(amount,
     * target)} with the amount FIRST:
     *
     * <pre>
     *   DATEADD(day, 1, d)          DATE_ADDDAYSTODATE(1, FT.D)          d a DATE
     *   DATEADD(hour, 1, d)         DATE_ADDHOURSTOTIMESTAMP(1, CAST(FT.D AS TIMESTAMP_NTZ(9)))
     *   DATEADD(month, 1, ts)       DATE_ADDMONTHSTOTIMESTAMP(1, FT.TS)  any TIMESTAMP flavour
     *   DATEADD(minute, 1, tm)      DATE_ADDMINUTESTOTIME(1, FT.TM)
     *   DATEADD(day, 1, g)          DATE_ADDDAYSTOTIMESTAMP(1, CAST(FT.G AS TIMESTAMP_NTZ(9)))   text, VARIANT
     *   DATEADD(day, 1.5, d)        DATE_ADDDAYSTODATE(CAST(1.5 AS NUMBER(9,0)), FT.D)
     *   DATEADD(day, g, d)          DATE_ADDDAYSTODATE(TO_NUMBER(MIX.G, 9, 0), MIX.D)
     *   DATEADD(day, NULL, d)       null                                  either argument NULL
     * </pre>
     *
     * <p>The units are named in the plural, MILLIS, MICROS and NANOS for the three smallest. An
     * invalid-type sentence prints the same function, but only where no conversion is planned beside
     * it; a shape outside the table prints as written.
     */
    private String plannedDateAdd(final FunctionCallExpression expr, final String name) {
        if (!(name.equals("DATEADD") || name.equals("TIMEADD") || name.equals("TIMESTAMPADD"))
                || expr.getArguments().size() != 3) {
            return null;
        }
        final List<Expression> args = expr.getArguments();
        final String unitText = DateTimeUnitSlot.unitTextOf(args.get(0));
        if (unitText == null || SharedFunctionHelpers.isComponentOnlyUnit(unitText)) {
            return null;
        }
        final IntervalUnit unit = IntervalUnit.fromSpelling(SharedFunctionHelpers.canonicalDateUnit(unitText));
        if (unit == null) {
            return null;
        }
        final Expression amount = args.get(1);
        final Expression target = args.get(2);
        if (isUntypedNull(amount) || isUntypedNull(target)) {
            return conversionShaped() ? null : "null";
        }
        return dateAdd(unit, amount.accept(this), argumentType(amount), target);
    }

    /** One interval shift as {@link #plannedDateAdd} spells it, or null when the shape is not planned that way. */
    private String dateAdd(final IntervalUnit unit, final String amount, final DataType amountType,
                           final Expression target) {
        final DataType targetType = argumentType(target);
        final boolean timeUnit = isTimeUnit(unit);
        final String kind;
        final boolean convertTarget;
        if (isDate(targetType)) {
            kind = timeUnit ? "TIMESTAMP" : "DATE";
            convertTarget = timeUnit;
        } else if (isTime(targetType)) {
            if (!timeUnit) {
                return null;
            }
            kind = "TIME";
            convertTarget = false;
        } else if (targetType instanceof DateTimeType) {
            kind = "TIMESTAMP";
            convertTarget = false;
        } else if (targetType instanceof StringType || isPlainVariant(targetType)) {
            kind = "TIMESTAMP";
            convertTarget = true;
        } else {
            return null;
        }
        final String wholeAmount = wholeNumber(amount, amountType);
        if (wholeAmount == null || (conversionShaped() && (convertTarget || !wholeAmount.equals(amount)))) {
            return null;
        }
        final String printedTarget = convertTarget
            ? "CAST(" + target.accept(this) + " AS " + TIMESTAMP_TARGET + ")" : target.accept(this);
        return "DATE_ADD" + IntervalCallArguments.shiftUnitName(unit) + "TO" + kind + "(" + wholeAmount + ", " + printedTarget + ")";
    }

    /**
     * An amount as the plan converts it to the whole number an interval takes: bare when it is one
     * already, {@code CAST(x AS NUMBER(9,0))} when it carries a scale or is a FLOAT,
     * {@code TO_NUMBER(x, 9, 0)} when it is text or VARIANT. Null for any other family.
     */
    private static String wholeNumber(final String printed, final DataType type) {
        if (type instanceof NumericType) {
            final NumericType number = (NumericType) type;
            return !NumericType.isApproximate(number) && number.getScale() <= 0
                ? printed : "CAST(" + printed + " AS " + WHOLE_NUMBER_TYPE + ")";
        }
        if (type instanceof StringType || isPlainVariant(type)) {
            return "TO_NUMBER(" + printed + ", 9, 0)";
        }
        return null;
    }

    /**
     * An expression converted to the NUMBER(9,0) a whole-number argument is planned as, spelled the
     * way {@link #wholeNumber} spells an interval amount, and the bare word NULL as the plan's typed
     * null {@code SYSTEM$NULL_TO_FIXED(null)}; null when its family has no such conversion.
     */
    String asWholeNumber(final Expression expression) {
        if (isUntypedNull(expression)) {
            return "SYSTEM$NULL_TO_FIXED(null)";
        }
        return wholeNumber(expression.accept(this), argumentType(expression));
    }

    private static boolean isTimeUnit(final IntervalUnit unit) {
        switch (unit) {
            case HOUR:
            case MINUTE:
            case SECOND:
            case MILLISECOND:
            case MICROSECOND:
            case NANOSECOND:
                return true;
            default:
                return false;
        }
    }

    /**
     * A one-argument conversion as the cast the plan makes of it:
     *
     * <pre>
     *   TO_DATE('2020-01-01')       CAST('2020-01-01' AS DATE)         TO_TIME is TIME(9)
     *   TO_TIMESTAMP(d)             CAST(FT.D AS TIMESTAMP_NTZ(9))     _LTZ and _TZ keep theirs
     *   TO_TIMESTAMP(1.5)           CAST(1.5 AS TIMESTAMP_NTZ(1))      a number's scale is the precision
     *   TO_DOUBLE(g)                CAST(FT.G AS FLOAT)
     *   TO_BOOLEAN('true')          CAST('true' AS BOOLEAN)
     *   TO_BOOLEAN(a)               CAST(RE.A &lt;&gt; (CAST(0 AS NUMBER(10,2))) AS BOOLEAN)   a number is compared with zero
     *   TO_VARCHAR(n)               CAST(RT.N AS VARCHAR(134217728))   TO_CHAR too
     *   TO_BINARY(g)                CAST(FT.G AS BINARY(67108864))     TO_ARRAY and TO_OBJECT likewise
     *   TRY_TO_DATE(g)              TRY_CAST(FT.G AS DATE)             TRY_TO_TIME, _TIMESTAMP, _DOUBLE, _BOOLEAN
     *   TO_DATE(d)                  FT.D                               a conversion that changes nothing is dropped
     *   TO_DATE(NULL)               SYSTEM$NULL_TO_DATE(null)          TEXT for TO_VARCHAR, REAL for TO_DOUBLE
     * </pre>
     *
     * <p>Null for any other call, and for a source family the conversion refuses or was not seen
     * converting — that call prints as written.
     */
    private String conversionAsCast(final FunctionCallExpression expr, final String name) {
        final List<String> names = expr.getArgumentNames();
        if (expr.getArguments().size() != 1 || (names != null && names.get(0) != null)) {
            return null;
        }
        final boolean tryForm = name.startsWith("TRY_");
        final String conversion = tryForm ? name.substring("TRY_".length()) : name;
        final String target = castTargetOf(conversion, tryForm);
        if (target == null) {
            return null;
        }
        final Expression source = expr.getArguments().get(0);
        if (isUntypedNull(source)) {
            final String family = tryForm ? null : nullConversionFamily(conversion);
            return family != null ? "SYSTEM$NULL_TO_" + family + "(null)" : null;
        }
        final DataType sourceType = argumentType(source);
        if (sourceType == null) {
            return null;
        }
        if (conversion.equals("TO_ARRAY") && !tryForm) {
            // A vector and a shaped array are cast to the plain array, and a plain one is that already.
            if (sourceType instanceof VectorType || sourceType instanceof StructuredArrayType) {
                return "CAST(" + source.accept(this) + " AS ARRAY)";
            }
            if (sourceType instanceof ArrayType) {
                return source.accept(this);
            }
        }
        if (conversion.equals("TO_OBJECT") && !tryForm && sourceType instanceof ObjectType
                && !(sourceType instanceof StructuredObjectType)) {
            return source.accept(this);
        }
        if (isNoOpCast(sourceType, argumentType(expr))) {
            return source.accept(this);
        }
        if (!tryForm && sourceType instanceof NumericType) {
            final NumericType number = (NumericType) sourceType;
            if (conversion.equals("TO_BOOLEAN") && !NumericType.isApproximate(number)) {
                final String zero = number.getScale() == 0 ? "0" : "(CAST(0 AS " + typeText(source) + "))";
                return "CAST(" + operand(source) + " <> " + zero + " AS BOOLEAN)";
            }
            if (conversion.startsWith("TO_TIMESTAMP") && !NumericType.isApproximate(number)) {
                // A number read as an instant keeps its own scale for the fraction, whatever flavour reads it.
                final String flavour = conversion.equals("TO_TIMESTAMP") ? "TIMESTAMP_NTZ"
                    : conversion.substring("TO_".length());
                return "CAST(" + source.accept(this) + " AS " + flavour + "(" + Math.max(number.getScale(), 0) + "))";
            }
        }
        if (!convertsFrom(conversion, tryForm, sourceType)) {
            return null;
        }
        final String told = tellingDefault && target.startsWith("VARCHAR(") ? "VARCHAR" : target;
        return (tryForm ? "TRY_CAST(" : "CAST(") + source.accept(this) + " AS " + told + ")";
    }

    /** The cast target a one-argument conversion stands for, or null when it is not one of them. */
    private static String castTargetOf(final String conversion, final boolean tryForm) {
        switch (conversion) {
            case "TO_DATE":
                return "DATE";
            case "TO_TIME":
                return "TIME(9)";
            case "TO_TIMESTAMP":
                return "TIMESTAMP_NTZ(9)";
            case "TO_DOUBLE":
                return "FLOAT";
            case "TO_BOOLEAN":
                return "BOOLEAN";
            default:
                break;
        }
        if (tryForm) {
            return null;
        }
        switch (conversion) {
            case "TO_TIMESTAMP_NTZ":
                return "TIMESTAMP_NTZ(9)";
            case "TO_TIMESTAMP_LTZ":
                return "TIMESTAMP_LTZ(9)";
            case "TO_TIMESTAMP_TZ":
                return "TIMESTAMP_TZ(9)";
            case "TO_VARCHAR":
            case "TO_CHAR":
                return "VARCHAR(" + DataTypeParser.CAST_STRING_DEFAULT + ")";
            case "TO_BINARY":
                return "BINARY(67108864)";
            case "TO_ARRAY":
                return "ARRAY";
            case "TO_OBJECT":
                return "OBJECT";
            default:
                return null;
        }
    }

    /** The typed null a conversion of the bare word NULL is planned as, or null when there is none. */
    private static String nullConversionFamily(final String conversion) {
        switch (conversion) {
            case "TO_DATE":
                return "DATE";
            case "TO_VARCHAR":
            case "TO_CHAR":
                return "TEXT";
            case "TO_DOUBLE":
                return "REAL";
            default:
                return null;
        }
    }

    /** Whether a conversion is planned as its cast over a source of this family. */
    private static boolean convertsFrom(final String conversion, final boolean tryForm, final DataType source) {
        final boolean text = source instanceof StringType;
        if (tryForm) {
            return text;
        }
        final boolean variant = isPlainVariant(source);
        switch (conversion) {
            case "TO_DATE":
            case "TO_TIME":
            case "TO_TIMESTAMP":
            case "TO_TIMESTAMP_NTZ":
            case "TO_TIMESTAMP_LTZ":
            case "TO_TIMESTAMP_TZ":
                return text || variant || source instanceof DateTimeType;
            case "TO_DOUBLE":
                return text || variant || source instanceof NumericType;
            case "TO_BOOLEAN":
                return text || variant;
            case "TO_VARCHAR":
            case "TO_CHAR":
                return variant || source instanceof NumericType || source instanceof DateTimeType
                    || source instanceof BooleanType;
            case "TO_BINARY":
                return text;
            case "TO_ARRAY":
                return text || source instanceof NumericType;
            case "TO_OBJECT":
                return variant;
            default:
                return false;
        }
    }

    /** A colon path as the plan spells it, in both plan-shaped modes: one GET per step, innermost first. */
    @Override
    public String visitObjectAccess(final ObjectAccessExpression expr) {
        if (mode == StrictPrintMode.WRITTEN) {
            return super.visitObjectAccess(expr);
        }
        String printed = expr.getBase().accept(this);
        for (final String part : expr.getPathParts()) {
            printed = "GET(" + printed + ", '" + part + "')";
        }
        return printed;
    }

    /** A bracket access as the plan spells it — the same GET a colon path becomes. */
    @Override
    public String visitArrayAccess(final ArrayAccessExpression expr) {
        if (mode == StrictPrintMode.WRITTEN) {
            return super.visitArrayAccess(expr);
        }
        return "GET(" + expr.getArray().accept(this) + ", " + expr.getIndex().accept(this) + ")";
    }

    /**
     * The conversion live's plan applies to an aggregate's own ARGUMENT, printed where the argument's
     * FAMILY differs from what the call consumes — a width alone changes nothing — and the handful of
     * aggregates the plan names by their definition. Live-verified:
     *
     * <pre>
     *   LISTAGG(SUM(n), ',')      LISTAGG(CAST(SUM(T.N) AS VARCHAR(134217728)), ',')
     *   LISTAGG(MIN(t), ',')      LISTAGG(MIN(T.T), ',')              VARCHAR(10) into VARCHAR: none
     *   SUM(MAX(t))               SUM(CAST(MAX(T.T) AS FLOAT))        text and VARIANT sum as FLOAT
     *   SUM(DISTINCT MAX(t))      SUM(DISTINCT CAST(DISTINCT MAX(T.T) AS FLOAT))
     *   OBJECT_AGG(t, SUM(n))     OBJECT_AGG(T.T, CAST(SUM(T.N) AS VARIANT))
     *   OBJECT_AGG(MAX(n), v)     OBJECT_AGG(CAST(MAX(T.N) AS VARCHAR(134217728)), MAX(T.V))
     *   BITOR_AGG(MAX(n))         BITOR_AGG(CAST(MAX(T.N) AS NUMBER(8,0)))    over NUMBER(10,2)
     *   BITOR_AGG(MAX(f))         BITOR_AGG(CAST(MAX(T.F) AS NUMBER(18,0)))
     *   BITOR_AGG(MAX(t))         BITOR_AGG(TO_NUMBER(MAX(T.T), 18, 0))
     *   COUNT_IF(MAX(b))          SUM(IFF(CAST(MAX(T.B) AS BOOLEAN), 1, 0))
     *   BOOLOR_AGG(MAX(b))        MAX(MAX(T.B))                       BOOLAND_AGG is MIN
     *   BOOLOR_AGG(MAX(n))        MAX(CAST((MAX(T.N)) &lt;&gt; (CAST(0 AS NUMBER(10,2))) AS BOOLEAN))
     *   BOOLOR_AGG(MAX(t))        MAX(CAST(MAX(T.T) AS BOOLEAN))
     *   APPROX_COUNT_DISTINCT(x)  HLL_ACCUMULATE(x)                   one argument only
     * </pre>
     *
     * <p>What takes NO cast is measured too: ARRAY_AGG, HASH_AGG, MAX, MIN, COUNT, ANY_VALUE, MODE and
     * MAX_BY print their argument as it is, so does a bit aggregate over a whole number and a
     * two-argument APPROX_COUNT_DISTINCT; a DISTINCT LISTAGG, SUM or BITXOR_AGG repeats the word inside
     * its cast, and a DISTINCT BITOR_AGG or BITAND_AGG drops it. The zero a boolean aggregate compares a number with is
     * cast to the number's type only when that type carries a scale. A call this does not know, or an
     * argument whose type does not resolve, prints as written — BOOLXOR_AGG's null-guarded sum, the
     * variance family's formulas and the refusals live raises instead (COUNT_IF over a non-boolean,
     * OBJECT_AGG with a text or DATE value) are not echoes to reproduce.
     */
    private String withArgumentCasts(final FunctionCallExpression expr, final String name) {
        final List<Expression> args = expr.getArguments();
        if (expr.isStar() || args.isEmpty()) {
            return null;
        }
        final Expression first = args.get(0);
        final String printed = first.accept(this);
        // An extreme over a PREDICATE — a comparison, IS NULL, IN, a NOT or AND/OR holding one — is
        // planned over the predicate cast to BOOLEAN: MAX(CAST((SUM(T.N)) > (CAST(1 AS NUMBER(22,2)))
        // AS BOOLEAN)); a bare boolean column or an AND of them takes no cast.
        if (args.size() == 1 && !expr.isDistinct() && spellsOutAPredicate(first)
                && (name.equals("MAX") || name.equals("MIN") || name.equals("BOOLOR_AGG")
                    || name.equals("BOOLAND_AGG"))) {
            final String extreme = name.equals("BOOLOR_AGG") ? "MAX" : name.equals("BOOLAND_AGG") ? "MIN" : name;
            return extreme + "(CAST(" + plannedCondition(first) + " AS BOOLEAN))";
        }
        // A count over a condition that spells out a predicate keeps the predicate as it is and casts only the
        // values beside it; the exclusive-or of one is the null-guarded sum over the condition cast whole.
        if (args.size() == 1 && !expr.isDistinct() && holdsPredicate(first)) {
            if (name.equals("COUNT_IF")) {
                return "SUM(IFF(" + plannedCondition(first) + ", 1, 0))";
            }
            if (name.equals("BOOLXOR_AGG")) {
                final String truth = "CAST(" + plannedCondition(first) + " AS BOOLEAN)";
                return exclusiveOrSum(truth, truth + " IS NULL");
            }
        }
        final DataType type = argumentType(first);
        // A NOT / AND / OR is boolean by construction even where the inferencer has no type for it.
        final boolean logical = type instanceof BooleanType || isLogicalOperator(first);
        if (type == null && !logical) {
            return null;
        }
        final boolean text = type instanceof StringType || type instanceof VariantType;
        final boolean exact = type instanceof NumericType && !NumericType.isApproximate(type);
        switch (name) {
            case "LISTAGG": {
                // A BINARY or a semi-structured value is refused on its type live, never converted.
                if (!(type instanceof NumericType || type instanceof BooleanType
                        || type instanceof DateTimeType || type instanceof VariantType)) {
                    return null;
                }
                final String distinct = expr.isDistinct() ? "DISTINCT " : "";
                final StringBuilder call = new StringBuilder("LISTAGG(").append(distinct)
                    .append("CAST(").append(distinct).append(printed)
                    .append(" AS VARCHAR(").append(DataTypeParser.CAST_STRING_DEFAULT).append("))");
                for (int i = 1; i < args.size(); i++) {
                    call.append(", ").append(args.get(i).accept(this));
                }
                return call.append(")").toString();
            }
            case "SUM": {
                if (args.size() != 1 || !text) {
                    return null;
                }
                final String distinct = expr.isDistinct() ? "DISTINCT " : "";
                return "SUM(" + distinct + "CAST(" + distinct + printed + " AS FLOAT))";
            }
            case "OBJECT_AGG": {
                if (args.size() != 2 || type == null) {
                    return null;
                }
                final Expression value = args.get(1);
                final DataType valueType = argumentType(value);
                final String key = type instanceof StringType
                    ? printed : "CAST(" + printed + " AS VARCHAR(" + DataTypeParser.CAST_STRING_DEFAULT + "))";
                final String member = valueType instanceof NumericType || valueType instanceof BooleanType
                    ? "CAST(" + value.accept(this) + " AS VARIANT)" : value.accept(this);
                return "OBJECT_AGG(" + key + ", " + member + ")";
            }
            case "BITOR_AGG":
            case "BITAND_AGG":
            case "BITXOR_AGG": {
                if (args.size() != 1) {
                    return null;
                }
                // An OR or AND of the same bits twice is the same OR or AND, so the plan drops their
                // DISTINCT; an XOR is not, so BITXOR_AGG keeps it, repeated inside the cast.
                final String distinct = expr.isDistinct() && name.equals("BITXOR_AGG") ? "DISTINCT " : "";
                if (text) {
                    return name + "(" + distinct + "TO_NUMBER(" + printed + ", 18, 0))";
                }
                if (type instanceof NumericType && NumericType.isApproximate(type)) {
                    return name + "(" + distinct + "CAST(" + distinct + printed + " AS NUMBER(18,0)))";
                }
                if (!exact || ((NumericType) type).getScale() == 0) {
                    return distinct.isEmpty() && expr.isDistinct() ? name + "(" + printed + ")" : null;
                }
                final NumericType number = (NumericType) type;
                return name + "(" + distinct + "CAST(" + distinct + printed + " AS NUMBER("
                    + (number.getPrecision() - number.getScale()) + ",0)))";
            }
            case "COUNT_IF":
                return args.size() == 1 && !expr.isDistinct() && logical
                    ? "SUM(IFF(CAST(" + printed + " AS BOOLEAN), 1, 0))" : null;
            case "BOOLOR_AGG":
            case "BOOLAND_AGG":
            case "BOOLXOR_AGG": {
                if (args.size() != 1 || expr.isDistinct()) {
                    return null;
                }
                final boolean exclusive = name.equals("BOOLXOR_AGG");
                final String extreme = name.equals("BOOLOR_AGG") ? "MAX" : "MIN";
                if (logical) {
                    return exclusive ? exclusiveOrSum(printed, printed + " IS NULL") : extreme + "(" + printed + ")";
                }
                final String truth;
                if (text) {
                    truth = "CAST(" + printed + " AS BOOLEAN)";
                } else if (exact) {
                    final String zero = ((NumericType) type).getScale() == 0
                        ? "0" : "(CAST(0 AS " + typeText(first) + "))";
                    truth = "CAST(" + operand(first) + " <> " + zero + " AS BOOLEAN)";
                } else {
                    return null;
                }
                return exclusive ? exclusiveOrSum(truth, printed + " IS NULL") : extreme + "(" + truth + ")";
            }
            case "APPROX_COUNT_DISTINCT":
            case "HLL":
                return args.size() == 1 ? "HLL_ACCUMULATE(" + printed + ")" : null;
            case "APPROX_PERCENTILE_ACCUMULATE":
                return args.size() == 1 && (text || exact) ? name + "(CAST(" + printed + " AS FLOAT))" : null;
            case "ARRAY_UNION_AGG":
                return args.size() == 1 && type instanceof VariantType
                    ? name + "(CAST(" + printed + " AS ARRAY))" : null;
            default:
                return null;
        }
    }

    /** The exclusive-or of a column of truth values as the plan computes it: exactly one of them true. */
    private static String exclusiveOrSum(final String truth, final String nullTest) {
        return "(SUM(IFF((" + truth + ") = TRUE, 1, IFF(" + nullTest + ", SYSTEM$NULL_TO_FIXED(null), 0)))) = 1";
    }

    /** The calls that are predicates of their arguments, which a condition holds as it holds a comparison. */
    private static final Set<String> PREDICATE_CALLS = new HashSet<>(Arrays.asList(
        "STARTSWITH", "ENDSWITH", "CONTAINS", "EQUAL_NULL"));

    /** Whether a condition holds a predicate — a comparison, IS NULL, IN, BETWEEN, LIKE, a predicate call — under its NOT / AND / OR. */
    private static boolean holdsPredicate(final Expression condition) {
        if (condition instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) condition;
            return call.getNameExpression() == null && call.getFunctionName() != null
                && PREDICATE_CALLS.contains(call.getFunctionName().toUpperCase(Locale.ROOT));
        }
        if (condition instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) condition;
            if (binary.getOperator() == BinaryOperator.AND || binary.getOperator() == BinaryOperator.OR) {
                return holdsPredicate(binary.getLeft()) || holdsPredicate(binary.getRight());
            }
        }
        if (condition instanceof UnaryOperationExpression
                && ((UnaryOperationExpression) condition).getOperator() == UnaryOperator.NOT) {
            return holdsPredicate(((UnaryOperationExpression) condition).getOperand());
        }
        return spellsOutAPredicate(condition);
    }

    /**
     * A condition as the plan holds it where a call counts by it. A predicate stands as it is, and so does a NOT,
     * AND or OR of predicates. A value that is no predicate — a BOOLEAN column, a literal, a call, a CASE — is cast
     * to BOOLEAN, and a NOT, AND or OR of such values is cast whole; beside a predicate each operand of an AND or
     * an OR is judged on its own, the chain written flat (live-verified):
     *
     * <pre>
     *   b                    CAST(T.B AS BOOLEAN)
     *   b AND b              CAST(T.B AND T.B AS BOOLEAN)
     *   n &gt; 5                T.N &gt; 5
     *   b OR n &gt; 5           (CAST(T.B AS BOOLEAN)) OR (T.N &gt; 5)
     *   b AND b AND n &gt; 5    (CAST(T.B AS BOOLEAN)) AND (CAST(T.B AS BOOLEAN)) AND (T.N &gt; 5)
     *   (b AND b) OR n &gt; 5   (CAST(T.B AND T.B AS BOOLEAN)) OR (T.N &gt; 5)
     *   NOT (b AND n &gt; 5)    NOT((CAST(T.B AS BOOLEAN)) AND (T.N &gt; 5))
     * </pre>
     */
    private String plannedCondition(final Expression condition) {
        if (!holdsPredicate(condition)) {
            return "CAST(" + flatChain(condition) + " AS BOOLEAN)";
        }
        if (condition instanceof BinaryOperationExpression) {
            final BinaryOperator operator = ((BinaryOperationExpression) condition).getOperator();
            if (operator == BinaryOperator.AND || operator == BinaryOperator.OR) {
                final List<Expression> operands = new ArrayList<>();
                collectOperands(condition, operator, operands);
                final StringBuilder chain = new StringBuilder();
                for (final Expression each : operands) {
                    chain.append(chain.length() > 0 ? operator == BinaryOperator.AND ? " AND " : " OR " : "")
                        .append('(').append(plannedCondition(each)).append(')');
                }
                return chain.toString();
            }
        }
        if (condition instanceof UnaryOperationExpression
                && ((UnaryOperationExpression) condition).getOperator() == UnaryOperator.NOT) {
            return "NOT(" + plannedCondition(((UnaryOperationExpression) condition).getOperand()) + ")";
        }
        // A negated range or pattern is the NOT of the plain one: NOT((T.N >= 1) AND (T.N <= 6)), NOT(T.S LIKE 'a%').
        if (condition instanceof BetweenExpression && ((BetweenExpression) condition).isNot()) {
            final BetweenExpression range = (BetweenExpression) condition;
            return "NOT(" + new BetweenExpression(range.getValue(), range.getLower(), range.getUpper(), false)
                .accept(this) + ")";
        }
        if (condition instanceof BinaryOperationExpression) {
            final BinaryOperationExpression pattern = (BinaryOperationExpression) condition;
            if (pattern.getOperator() == BinaryOperator.NOT_LIKE || pattern.getOperator() == BinaryOperator.NOT_ILIKE) {
                return "NOT(" + new BinaryOperationExpression(pattern.getLeft(),
                    pattern.getOperator() == BinaryOperator.NOT_LIKE ? BinaryOperator.LIKE : BinaryOperator.ILIKE,
                    pattern.getRight(), pattern.getEscape()).accept(this) + ")";
            }
        }
        return condition.accept(this);
    }

    /** A chain of one AND or OR over values, written flat as the plan holds it: {@code T.B OR T.B OR T.B}. */
    private String flatChain(final Expression condition) {
        if (!(condition instanceof BinaryOperationExpression)) {
            return condition.accept(this);
        }
        final BinaryOperator operator = ((BinaryOperationExpression) condition).getOperator();
        if (operator != BinaryOperator.AND && operator != BinaryOperator.OR) {
            return condition.accept(this);
        }
        final List<Expression> operands = new ArrayList<>();
        collectOperands(condition, operator, operands);
        if (operands.size() < 3) {
            return condition.accept(this);
        }
        final StringBuilder chain = new StringBuilder();
        for (final Expression each : operands) {
            chain.append(chain.length() > 0 ? operator == BinaryOperator.AND ? " AND " : " OR " : "")
                .append(operand(each));
        }
        return chain.toString();
    }

    /** The operands of a chain of one operator, in the order written. */
    private static void collectOperands(final Expression condition, final BinaryOperator operator,
                                        final List<Expression> into) {
        if (condition instanceof BinaryOperationExpression
                && ((BinaryOperationExpression) condition).getOperator() == operator) {
            collectOperands(((BinaryOperationExpression) condition).getLeft(), operator, into);
            collectOperands(((BinaryOperationExpression) condition).getRight(), operator, into);
            return;
        }
        into.add(condition);
    }

    /**
     * An aggregate the plan computes in two steps, named by both where the call itself is what a sentence names:
     * a distinct count is the estimate of the state it accumulates, {@code HLL_ESTIMATE(HLL_ACCUMULATE(T.N))},
     * and an approximate percentile the estimate of its own,
     * {@code APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE(CAST(T.N AS FLOAT)), CAST(0.5 AS FLOAT))}
     * (live-verified). Null for any other call.
     *
     * @param expr the call a sentence names
     * @return its two-step definition, or null
     */
    String twoStepDefinition(final Expression expr) {
        if (!(expr instanceof FunctionCallExpression)) {
            return null;
        }
        final FunctionCallExpression call = (FunctionCallExpression) expr;
        if (call.getNameExpression() != null || call.getFunctionName() == null || call.isStar()
                || call.getWithinGroupOrdered() != null) {
            return null;
        }
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        final List<Expression> args = call.getArguments();
        if ((name.equals("APPROX_COUNT_DISTINCT") || name.equals("HLL")) && args.size() == 1) {
            return "HLL_ESTIMATE(HLL_ACCUMULATE(" + args.get(0).accept(this) + "))";
        }
        if (name.equals("APPROX_PERCENTILE") && args.size() == 2 && !call.isDistinct()) {
            final DataType type = argumentType(args.get(0));
            final String value = args.get(0).accept(this);
            final boolean converted = type instanceof StringType || type instanceof VariantType
                || type instanceof NumericType && !NumericType.isApproximate(type);
            if (!converted && !(type instanceof NumericType)) {
                return null;
            }
            return "APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE("
                + (converted ? "CAST(" + value + " AS FLOAT)" : value) + "), CAST(" + args.get(1).accept(this)
                + " AS FLOAT))";
        }
        return null;
    }

    /**
     * An average of the distinct values as the plan holds it: the argument is cast to the width the average is
     * computed at, three more decimals and fifteen more digits to a cap of 38, and a text or VARIANT argument to
     * FLOAT with the word repeated inside the cast (live-verified):
     *
     * <pre>
     *   AVG(DISTINCT n)   over NUMBER(10,2)    AVG(DISTINCT CAST(T.N AS NUMBER(25,5)))
     *   AVG(DISTINCT n)   over NUMBER(38,0)    AVG(DISTINCT CAST(T.N AS NUMBER(38,3)))
     *   AVG(DISTINCT t)   over VARCHAR         AVG(DISTINCT CAST(DISTINCT T.T AS FLOAT))
     * </pre>
     *
     * A FLOAT argument prints as written, and so does one whose scale has no three decimals left to grow by.
     */
    private String distinctAverage(final Expression argument) {
        final DataType type = argumentType(argument);
        final String value = argument.accept(this);
        if (type instanceof StringType || type instanceof VariantType) {
            return "AVG(DISTINCT CAST(DISTINCT " + value + " AS FLOAT))";
        }
        if (!(type instanceof NumericType) || NumericType.isApproximate(type)) {
            return null;
        }
        final NumericType exact = (NumericType) type;
        if (exact.getScale() + DISTINCT_AVERAGE_DECIMALS > MAX_SCALE) {
            return null;
        }
        return "AVG(DISTINCT CAST(" + value + " AS NUMBER("
            + Math.min(MAX_PRECISION, exact.getPrecision() + DISTINCT_AVERAGE_DIGITS) + ","
            + (exact.getScale() + DISTINCT_AVERAGE_DECIMALS) + ")))";
    }

    /**
     * A percentile's ordered value as the plan holds it: a text or VARIANT value is converted to the
     * whole number the percentile computes on, {@code TO_NUMBER(x, 9, 0)} — measured as
     * {@code PERCENTILE_DISC(TO_NUMBER(MAX(T.T), 9, 0), 0.5)} and, interpolating,
     * {@code PERCENTILE_CONT(CAST(TO_NUMBER(MAX(T.T), 9, 0) AS NUMBER(12,3)), 0.5)}.
     */
    private String wholeNumberOf(final Expression value) {
        final DataType type = argumentType(value);
        final String printed = value.accept(this);
        if (type instanceof BooleanType) {
            // A truth value is the number it converts to: CAST(T.B AS NUMBER(9,0)) (live-verified).
            return "CAST(" + printed + " AS NUMBER(9,0))";
        }
        return type instanceof StringType || type instanceof VariantType
            ? "TO_NUMBER(" + printed + ", 9, 0)" : printed;
    }

    /**
     * The SUM half of a plain AVG, which is what a nesting message names when the AVG itself is one of
     * the two bracketed calls: {@code [SUM(T.N)] nested in [SUM(SUM(T.N))]} for {@code AVG(SUM(n))},
     * and {@code [SUM(T.N)] nested in [SUM((CAST(SUM(T.N) AS NUMBER(28,8))) / (COUNT(T.N)))]} for
     * {@code SUM(AVG(n))}. Null when the call is not a plain AVG, or its argument's type does not
     * resolve.
     */
    String averageSumHalf(final Expression expr) {
        if (!(expr instanceof FunctionCallExpression) || !isPlainAverage((FunctionCallExpression) expr)) {
            return null;
        }
        final Expression argument = ((FunctionCallExpression) expr).getArguments().get(0);
        final DataType type = argumentType(argument);
        if (type instanceof StringType || type instanceof VariantType) {
            return "SUM(CAST(" + argument.accept(this) + " AS FLOAT))";
        }
        return type instanceof NumericType ? "SUM(" + argument.accept(this) + ")" : null;
    }

    private static boolean isPlainAverage(final FunctionCallExpression expr) {
        return expr.getFunctionName().equalsIgnoreCase("AVG") && !expr.isDistinct() && !expr.isStar()
            && expr.getArguments().size() == 1 && expr.getWithinGroupOrdered() == null;
    }

    private String averageExpansion(final Expression argument) {
        final DataType type = argumentType(argument);
        final String value = argument.accept(this);
        if (type instanceof StringType || type instanceof VariantType) {
            return "(SUM(CAST(" + value + " AS FLOAT))) / (CAST(COUNT(" + value + ") AS FLOAT))";
        }
        if (!(type instanceof NumericType)) {
            return null;
        }
        if (NumericType.isApproximate(type)) {
            return "(SUM(" + value + ")) / (CAST(COUNT(" + value + ") AS FLOAT))";
        }
        final NumericType exact = (NumericType) type;
        final NumericType sum = new NumericType("NUMBER",
            Math.min(MAX_PRECISION, exact.getPrecision() + SUM_EXTRA_DIGITS), exact.getScale());
        return exactDivision("SUM(" + value + ")", sum, "COUNT(" + value + ")", "(COUNT(" + value + "))",
            COUNT_TYPE);
    }

    /** The value re-printed, wrapped in the CAST live inserts when the call's own type differs from it. */
    private String convertedToCallType(final Expression value, final FunctionCallExpression call) {
        final String printed = wholeNumberOf(value);
        if (argumentType(value) instanceof BooleanType) {
            // The whole number a truth value converts to is interpolated at three decimals, as a text's is.
            return "CAST(" + printed + " AS NUMBER(12,3))";
        }
        final String valueType = typeText(value);
        final String callType = typeText(call);
        if (valueType == null || callType == null || valueType.equals(callType)) {
            return printed;
        }
        return "CAST(" + printed + " AS " + callType + ")";
    }

    /** A NOT, AND or OR — boolean by construction, whatever the inferencer says of it. */
    private static boolean isLogicalOperator(final Expression expression) {
        if (expression instanceof UnaryOperationExpression) {
            return ((UnaryOperationExpression) expression).getOperator() == UnaryOperator.NOT;
        }
        return expression instanceof BinaryOperationExpression
            && (((BinaryOperationExpression) expression).getOperator() == BinaryOperator.AND
                || ((BinaryOperationExpression) expression).getOperator() == BinaryOperator.OR);
    }

    /** Whether an expression spells out a predicate — a comparison, IS NULL, IN, BETWEEN, LIKE — anywhere under its NOT / AND / OR. */
    private static boolean spellsOutAPredicate(final Expression expression) {
        if (expression instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expression;
            if (isComparison(binary.getOperator()) || binary.getOperator() == BinaryOperator.LIKE
                    || binary.getOperator() == BinaryOperator.ILIKE || binary.getOperator() == BinaryOperator.NOT_LIKE
                    || binary.getOperator() == BinaryOperator.NOT_ILIKE) {
                return true;
            }
            return (binary.getOperator() == BinaryOperator.AND || binary.getOperator() == BinaryOperator.OR)
                && (spellsOutAPredicate(binary.getLeft()) || spellsOutAPredicate(binary.getRight()));
        }
        if (expression instanceof UnaryOperationExpression) {
            final UnaryOperator operator = ((UnaryOperationExpression) expression).getOperator();
            return operator == UnaryOperator.EXISTS
                || operator == UnaryOperator.NOT
                    && spellsOutAPredicate(((UnaryOperationExpression) expression).getOperand());
        }
        return expression instanceof IsNullExpression || expression instanceof InExpression
            || expression instanceof BetweenExpression || expression instanceof LikeAnyAllExpression;
    }

    /** A number written as a literal — not a conversion the plan folded, which a minus before it NEGATEs. */
    private static boolean isNumericLiteral(final Expression expression) {
        return expression instanceof LiteralExpression && !(expression instanceof FoldedConstantExpression)
            && (((LiteralExpression) expression).getType() == LiteralType.INTEGER
                || ((LiteralExpression) expression).getType() == LiteralType.DECIMAL);
    }

    private static boolean isUntypedNull(final Expression expression) {
        return expression instanceof LiteralExpression
            && ((LiteralExpression) expression).getType() == LiteralType.NULL;
    }

    /**
     * The bare word NULL, a call the plan folds to it that is no conditional, or a conditional folding to it
     * ({@link UntypedNullFold}) — an operand the plan types as the NULL of its neighbour's family.
     */
    private static boolean isNullValue(final Expression expression) {
        return isUntypedNull(expression) || foldsToNullItself(expression) || isFoldedConditional(expression);
    }

    /**
     * A conditional every branch of which is an untyped NULL: IFF(TRUE, NULL, NULL), COALESCE(NULL, NULL),
     * CASE WHEN a > 1 THEN NULL END.
     */
    private static boolean isFoldedConditional(final Expression expression) {
        if (expression instanceof CaseExpression) {
            return UntypedNullFold.foldsToUntypedNull(expression);
        }
        return expression instanceof FunctionCallExpression && UntypedNullFold.foldsToUntypedNull(expression)
            && UntypedNullFold.picksABranch(((FunctionCallExpression) expression).getFunctionName().toUpperCase(Locale.ROOT));
    }

    /**
     * A NULL branch of a conditional whose every branch is one, as the plan holds it: the FIRST is converted to
     * the NULL type, {@code CAST(null AS NULL)}, and the others print as they are —
     * {@code NVL(CAST(null AS NULL), null)}, {@code GREATEST(CAST(null AS NULL), null, null)},
     * {@code NVL(CAST(NVL(CAST(null AS NULL), null) AS NULL), null)} (live-verified).
     */
    private String nullBranch(final Expression branch, final boolean first) {
        final String printed = foldsToNullItself(branch) ? "null" : branch.accept(this);
        return first ? "CAST(" + printed + " AS NULL)" : printed;
    }

    private static boolean foldsToNullItself(final Expression expression) {
        return expression instanceof FunctionCallExpression && UntypedNullFold.foldsToUntypedNull(expression)
            && !UntypedNullFold.picksABranch(((FunctionCallExpression) expression).getFunctionName().toUpperCase(Locale.ROOT));
    }

    private static boolean isExactNumber(final DataType type) {
        return type instanceof NumericType && !NumericType.isApproximate(type);
    }

    private static boolean isDate(final DataType type) {
        return type instanceof DateTimeType && "DATE".equalsIgnoreCase(type.getName());
    }

    private static boolean isTimestamp(final DataType type) {
        return type instanceof DateTimeType && type.getName().toUpperCase(Locale.ROOT).startsWith("TIMESTAMP");
    }

    /** The flavour a TIMESTAMP type is named by: TIMESTAMP_LTZ, TIMESTAMP_TZ, or else TIMESTAMP_NTZ. */
    private static String timestampFlavour(final DataType timestamp) {
        final String name = timestamp.getName().toUpperCase(Locale.ROOT);
        if (name.startsWith("TIMESTAMP_LTZ")) {
            return "TIMESTAMP_LTZ";
        }
        return name.startsWith("TIMESTAMP_TZ") ? "TIMESTAMP_TZ" : "TIMESTAMP_NTZ";
    }

    private static boolean isTime(final DataType type) {
        return type instanceof DateTimeType && "TIME".equalsIgnoreCase(type.getName());
    }

    /** A plain VARIANT — not an OBJECT or ARRAY, which are types of their own. */
    private static boolean isPlainVariant(final DataType type) {
        return type instanceof VariantType;
    }

    private DataType argumentType(final Expression expression) {
        try {
            final DataType typed = context.inferStaticType(expression);
            // A name of the query around a subquery being compiled is typed by that query's scope.
            if (typed == null && expression instanceof ColumnReferenceExpression && correlationScope == null
                    && compiledOuterQualifier((ColumnReferenceExpression) expression) != null) {
                return SubqueryCompilation.outerScope().inferStaticType(expression);
            }
            return typed;
        } catch (final RuntimeException notTypeable) {
            return null;
        }
    }

    /** A call the plan folds early, printed as the text literal of its value; null where it cannot be valued. */
    private String foldedValue(final FunctionCallExpression call) {
        final Object value;
        try {
            value = call.accept(context);
        } catch (final RuntimeException unvalued) {
            return null;
        }
        return value == null ? null : "'" + value.toString().replace("'", "''") + "'";
    }

    /** Whether an expression is the empty text literal. */
    private static boolean isEmptyText(final Expression expression) {
        return expression instanceof LiteralExpression
            && ((LiteralExpression) expression).getType() == LiteralType.STRING
            && "".equals(((LiteralExpression) expression).getValue());
    }

    private String typeText(final Expression expression) {
        try {
            return context.argumentTypeText(expression);
        } catch (final RuntimeException notTypeable) {
            return null;
        }
    }

    @Override
    public String visitRowComparison(final RowComparisonExpression expr) {
        final StringBuilder text = new StringBuilder("(");
        for (int i = 0; i < expr.getLeft().size(); i++) {
            text.append(i > 0 ? ", " : "").append(expr.getLeft().get(i).accept(this));
        }
        text.append(") ").append(expr.getOperator()).append(" (");
        for (int i = 0; i < expr.getRight().size(); i++) {
            text.append(i > 0 ? ", " : "").append(expr.getRight().get(i).accept(this));
        }
        return text.append(")").toString();
    }
}
