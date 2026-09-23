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

import dev.frostlake.executor.SessionTimestampMapping;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.commands.DataTypeParser;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.datetime.DateDifferenceWidths;
import dev.frostlake.functions.scalar.datetime.PlannedDateAdd;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BinaryWidthSpelling;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.ConvertedValueType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.DeclaredTypeFold;
import dev.frostlake.types.FileType;
import dev.frostlake.types.GeoTypes;
import dev.frostlake.types.IntegerResultWidths;
import dev.frostlake.types.IntervalDayTimeType;
import dev.frostlake.types.IntervalYearMonthType;
import dev.frostlake.types.LengthlessStringType;
import dev.frostlake.types.MapType;
import dev.frostlake.types.NumericLiteralTypes;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringResultWidths;
import dev.frostlake.types.StringType;
import dev.frostlake.types.UuidType;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorElementType;
import dev.frostlake.types.VectorType;
import dev.frostlake.types.WidthlessStringType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.CodePointText;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Static type inference over the expression AST: the SQL type an expression is KNOWN to produce, or
 * null when it cannot be determined (derived columns, subqueries, dynamic sources). Used by the
 * strict-argument checks so Snowflake's argument-type errors fire from the expression's declared
 * shape — including through function calls like {@code TYPEOF(UPPER(s))} — rather than only for
 * bare literals and column references. Inference is deliberately conservative: null means "let the
 * typed runtime value decide", so an over-broad UNKNOWN can never cause a false rejection.
 */
final class TypeInferencer {

    /**
     * An argument-type refusal anchored on the OPERATOR, which is where Snowflake points: over
     * {@code SELECT bn || s AS c FROM t} live reads {@code error line 1 at position 10}, the offset of
     * {@code ||} itself rather than the expression's or the select item's start.
     *
     * <p>The position is resolved through {@link ExpressionSource} so a fragment reports its offset in
     * the WHOLE statement — a CTAS body's operator reads position 42, counting from {@code CREATE}.
     * Without a resolvable origin the sentence stands alone, which is what every caller that has no
     * parse tree behind it gets.
     */
    private String positioned(final String detail, final BinaryOperationExpression binary) {
        return positionedAt(detail, binary.getPosition());
    }

    /** The same, for any expression that carries its own origin. */
    private String positionedAt(final String detail, final SourcePosition origin) {
        final SourcePosition at = ExpressionSource.resolve(origin);
        return at != null
            ? SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(), detail)
            : SqlCompilationError.of(detail);
    }

    private final ExpressionEvaluatorVisitor visitor;

    /** Whether every element has a type the channel can name, an untyped NULL literal included. */
    private boolean allTyped(final List<Expression> elements) {
        for (final Expression element : elements) {
            final boolean untypedNull = element instanceof LiteralExpression
                && ((LiteralExpression) element).getType() == LiteralType.NULL;
            if (!untypedNull && infer(element) == null) {
                return false;
            }
        }
        return true;
    }

    TypeInferencer(final ExpressionEvaluatorVisitor visitor) {
        this.visitor = visitor;
    }

    // Static types are row-invariant, but infer() was re-walking the argument AST per ROW for
    // every strictness check (temporal-argument, variant-coercion, colon-path, cast paths).
    // Memoized by node IDENTITY (the cached expression AST is shared across rows); cleared when
    // the visitor's multi-table context changes — the single-table context is fixed at
    // construction. UNDETERMINED caches the null verdict so misses stop re-walking too.
    private final Map<Expression, Object> memo = new IdentityHashMap<Expression, Object>();
    /** The types a lambda's parameters take while its body is typed, innermost lambda first. */
    private final Deque<Map<String, DataType>> lambdaTypes = new ArrayDeque<>();
    /** The width a text or VARIANT value is converted to by the interpolating and picking percentiles, live: NUMBER(9,0). */
    private static final int COERCED_WHOLE_DIGITS = 9;

    private static final Object UNDETERMINED = new Object();

    /** Drop every memoized verdict — called when the resolution context changes. */
    void clearMemo() {
        memo.clear();
    }

    /** The statically-known type of {@code expr}, or null when undetermined. */
    DataType infer(final Expression expr) {
        if (expr instanceof DefaultMarkerExpression) {
            // A bare DML DEFAULT that reached the STATIC channel is not standing alone as a value —
            // the write paths take the marker before anything types it. Refusing here rather than only
            // at evaluation is what makes it data-INDEPENDENT: over an empty table nothing is
            // evaluated, and the statement was accepted.
            final DefaultMarkerExpression marker = (DefaultMarkerExpression) expr;
            final SourcePosition at = ExpressionSource.resolve(marker.getWhere());
            final SourcePosition where = at == null ? marker.getWhere() : at;
            throw new RuntimeException(where == null
                ? SqlCompilationError.invalidIdentifier("DEFAULT")
                : SqlCompilationError.invalidIdentifier(
                    where.getLine(), where.getCharPositionInLine(), "DEFAULT"));
        }
        // Inside a lambda's body a parameter takes the type the call gives it, which the memo, keyed by node
        // alone, cannot hold: the body is typed afresh each time.
        if (!lambdaTypes.isEmpty()) {
            return inferUncached(expr);
        }
        final Object cached = memo.get(expr);
        if (cached != null) {
            return cached == UNDETERMINED ? null : (DataType) cached;
        }
        final DataType inferred = inferUncached(expr);
        memo.put(expr, inferred != null ? inferred : UNDETERMINED);
        return inferred;
    }


    /**
     * The type of a temporal shifted by an INTERVAL, which the operand channel cannot reach: an
     * interval is not a value with a type of its own here, so both operands would have to be typed for
     * the ordinary algebra to run.
     *
     * <p>The line is drawn at the DAY, not the month. A whole-day unit (year, month, day) leaves a DATE
     * a DATE; a SUB-DAY unit (hour, minute, second) promotes it to TIMESTAMP_NTZ. A timestamp stays
     * whatever timestamp it already was. Live-verified across every unit.
     *
     * @return the shifted type, or null when this is not a temporal-plus-interval at all
     */
    private DataType temporalShiftedByInterval(final BinaryOperationExpression binary) {
        if (binary.getOperator() != BinaryOperator.ADD
                && binary.getOperator() != BinaryOperator.SUBTRACT) {
            return null;
        }
        if (!(binary.getRight() instanceof IntervalExpression)
                || ((IntervalExpression) binary.getRight()).getLiteral() != null) {
            // A unit-suffixed literal is a typed interval: the operand algebra types the shift.
            return null;
        }
        final DataType left = infer(binary.getLeft());
        if (left instanceof DateTimeType && !"DATE".equals(((DateTimeType) left).getName())) {
            // A timestamp keeps its own flavour, at the family's full nine digits.
            return atFullPrecision((DateTimeType) left);
        }
        if (!(left instanceof DateTimeType)) {
            return null;
        }
        return wholeDayUnit((IntervalExpression) binary.getRight())
            ? left : DateTimeType.TIMESTAMP_NTZ;
    }

    /**
     * A unit-suffixed interval literal's own type: {@code INTERVAL '1' DAY} is an {@code INTERVAL DAY(9)},
     * {@code INTERVAL '1' SECOND} an {@code INTERVAL SECOND(9,9)}, {@code INTERVAL '1-2' YEAR TO MONTH} an
     * {@code INTERVAL YEAR(9) TO MONTH} and {@code INTERVAL '1' DAY(2)} an {@code INTERVAL DAY(2)}
     * (live-verified). The in-string spelling and a multi-part one stay untyped — neither projects on its own,
     * and date arithmetic types both by its own rule above.
     *
     * @param interval the literal
     * @return its type, or null
     */
    private static DataType intervalLiteralType(final IntervalExpression interval) {
        return interval.getLiteral() == null ? null : interval.getLiteral().getType();
    }

    /** Whether an operand is the quoted-string interval form, {@code INTERVAL '1 day'}, which has no type. */
    private static boolean isQuotedInterval(final Expression operand) {
        return operand instanceof IntervalExpression && ((IntervalExpression) operand).getLiteral() == null;
    }

    /** Whether every part of the interval measures a whole day or more. */
    private boolean wholeDayUnit(final IntervalExpression interval) {
        for (IntervalExpression part = interval; part != null; part = part.getRest()) {
            if (!part.getUnit().isWholeDay()) {
                return false;
            }
            // Only the in-string spelling keeps a DATE a DATE for a DAY amount.
            if (part.getUnit() == IntervalUnit.DAY && !part.isUnitInString()) {
                return false;
            }
        }
        return true;
    }

    private DataType inferUncached(final Expression expr) {
        if (expr instanceof BindVariableExpression) {
            // ★ THE DECLARATION IS THE VARIABLE'S TYPE (live): a column derived from :v carries the
            // DECLARED type WITH its width — declare v varchar(2) := 42 makes a VARCHAR(2) column
            // and number(5,2) keeps its (5,2) — not whatever object the initialiser produced.
            return visitor.declaredBindVariableType(((BindVariableExpression) expr).getVarName());
        }
        if (expr instanceof SessionVarExpression) {
            // A session variable is typed by the VALUE it holds, as a literal of that value is typed.
            return visitor.sessionVariableType(((SessionVarExpression) expr).getVarName());
        }
        if (expr instanceof IntervalExpression) {
            return intervalLiteralType((IntervalExpression) expr);
        }
        if (expr instanceof SystemStreamHasDataExpression) {
            return BooleanType.BOOLEAN;
        }
        if (expr instanceof FoldedConstantExpression) {
            // A conversion the plan folded keeps its target's type, whatever digits its value has.
            return ((FoldedConstantExpression) expr).getDeclaredType();
        }
        if (expr instanceof LiteralExpression) {
            switch (((LiteralExpression) expr).getType()) {
                case STRING:
                    // The literal's OWN length, like the numeric branch below: live spells the
                    // expression VARCHAR(3) for 'abc' in type-matching refusals and derives CTAS
                    // column lengths the same way. One character at least: '' is VARCHAR(1), and
                    // '' || '' VARCHAR(2) (live-verified).
                    final Object text = ((LiteralExpression) expr).getValue();
                    return text instanceof String
                        ? new StringType("VARCHAR", Math.max(1, CodePointText.length((String) text)))
                        : StringType.VARCHAR;
                case INTEGER:
                case DECIMAL:
                    // The literal's OWN precision and scale, not a blanket NUMBER(38,0). This type
                    // becomes a derived relation's real column type, so a scale-0 answer here does not
                    // just misreport 0.05 — it stores it as 0. See NumericLiteralTypes.
                    final NumericType measured = NumericLiteralTypes.of(
                        ((LiteralExpression) expr).getValue());
                    return measured != null ? measured : NumericType.NUMBER;
                case BOOLEAN:
                    return BooleanType.BOOLEAN;
                case BINARY:
                    // Its OWN byte count, like the string branch above, and NOT the fixed spelling: an
                    // X'..' literal reads fixed false live, the same as every other derived binary.
                    final Object bytes = ((LiteralExpression) expr).getValue();
                    return bytes instanceof BinaryValue
                        ? new BinaryType("VARBINARY", BinaryLiteralText.declaredWidth((BinaryValue) bytes))
                        : BinaryType.VARBINARY;
                default:
                    return null;   // NULL literal: untyped
            }
        }
        if (expr instanceof SubqueryExpression) {
            // A scalar subquery carries its one select item's declared type (live: SYSTEM$TYPEOF of
            // (SELECT f FROM t) is the column's FLOAT, of (SELECT 1.5) is NUMBER(2,1)), read off the
            // subquery's planned shape — no row read — and memoised with every other verdict.
            return visitor.scalarSubqueryType((SubqueryExpression) expr);
        }
        if (expr instanceof ColumnReferenceExpression) {
            final ColumnReferenceExpression reference = (ColumnReferenceExpression) expr;
            if (!reference.isQualified()) {
                for (final Map<String, DataType> scope : lambdaTypes) {
                    if (scope.containsKey(reference.getColumnName().toUpperCase())) {
                        return scope.get(reference.getColumnName().toUpperCase());
                    }
                }
            }
            final TableColumn resolved = visitor.resolveDeclaredColumn((ColumnReferenceExpression) expr);
            if (resolved == null && visitor.readsSequence(reference)) {
                return SequenceRead.TYPE;
            }
            if (resolved != null) {
                return resolved.getDataType();
            }
            // A name no relation carries may be a SELECT output alias, typed from the expression it
            // names — how a HAVING or QUALIFY predicate over an alias is judged, and an item reading an
            // earlier item's alias is typed.
            final DataType aliasType = visitor.outputAliasType(reference);
            if (aliasType != null) {
                return aliasType;
            }
            // Inside a subquery, a name of the query AROUND it — a correlation — takes the type the
            // enclosing scope gives it, so (SELECT fz.id + 1) is typed NUMBER as live types it.
            return visitor.outerScopeType(reference);
        }
        if (expr instanceof UnaryOperationExpression) {
            // A negated numeric keeps its operand's own NUMBER — the sign adds no digit (live: -208 ∪
            // 1.25 declares NUMBER(5,2), so -208 counts as (3,0), and a view over -a declares exactly
            // a's type). It holds for a column reference and a computed operand, not just a literal.
            final UnaryOperationExpression unary = (UnaryOperationExpression) expr;
            if (unary.getOperator() == UnaryOperator.NEGATE && isNullLiteral(unary.getOperand())) {
                // A negated bare NULL is the arithmetic NULL's own NUMBER(18,0) (live-verified).
                return new NumericType("NUMBER", 18, 0);
            }
            if (unary.getOperator() == UnaryOperator.PLUS && isNullLiteral(unary.getOperand())) {
                return UnaryPlusType.OVER_NULL;
            }
            if (unary.getOperator() == UnaryOperator.NEGATE || unary.getOperator() == UnaryOperator.PLUS) {
                final DataType signed = infer(unary.getOperand());
                if (signed instanceof NumericType) {
                    // A unary plus widens an exact number, where a negation does not (see UnaryPlusType).
                    return unary.getOperator() == UnaryOperator.PLUS && !NumericType.isApproximate(signed)
                        ? UnaryPlusType.of((NumericType) signed) : signed;
                }
                if ((signed instanceof IntervalDayTimeType || signed instanceof IntervalYearMonthType)
                        && unary.getOperator() == UnaryOperator.NEGATE) {
                    // A negated interval is still one, of its own type (live-verified).
                    return signed;
                }
                // A sign CONVERTS a text or a VARIANT operand, and to a FLOAT: SYSTEM$TYPEOF(-'3'),
                // (+'5'), (-v) and (+v) over a VARIANT 7 all read FLOAT[DOUBLE] on the account — the
                // same arithmetic conversion the binary operators apply to those two families.
                if (signed instanceof StringType || signed instanceof VariantType) {
                    return NumericType.FLOAT;
                }
                return null;
            }
            if (unary.getOperator() == UnaryOperator.NOT) {
                // NOT reads the same families a logical operator does; the rest are refused before any
                // row, positioned on the operator as live positions them.
                final DataType negated = infer(unary.getOperand());
                if (!BinaryOperationTypes.isLogicalOperand(negated)) {
                    throw new RuntimeException(positionedAt("Invalid argument types for function"
                        + " 'NOT': (" + SqlTypeNames.canonical(negated) + ")", unary.getPosition()));
                }
            }
            // NOT and EXISTS answer a BOOLEAN whatever their operand: live declares NOT 1, NOT NULL
            // and EXISTS (SELECT 1) as BOOLEAN alike.
            return BooleanType.BOOLEAN;
        }
        if (expr instanceof RowComparisonExpression) {
            // A row comparison is a BOOLEAN, refused while the statement compiles when its rows cannot be
            // compared — once every element has a type to name.
            final RowComparisonExpression rows = (RowComparisonExpression) expr;
            if (allTyped(rows.getLeft()) && allTyped(rows.getRight())) {
                visitor.requireComparableRows(rows);
            }
            return BooleanType.BOOLEAN;
        }
        if (expr instanceof BinaryOperationExpression) {
            // Arithmetic and concatenation carry a KNOWN precision, scale or length — see
            // BinaryOperationTypes for the measured algebra. An operand the channel could not type
            // makes the whole expression untyped, as everywhere else here.
            final BinaryOperationExpression binary = (BinaryOperationExpression) expr;
            final DataType shifted = temporalShiftedByInterval(binary);
            if (shifted != null) {
                return shifted;
            }
            DataType leftType = infer(binary.getLeft());
            DataType rightType = infer(binary.getRight());
            visitor.validateOperatorCollations(binary);
            if (isNumericOperator(binary.getOperator())) {
                // A text or a VARIANT operand is judged FIRST, on the types as declared: a text beside
                // a bare NULL is a FLOAT on the account, not the NULL's NUMBER(18,0) beside a number.
                final DataType floated = textOrVariantArithmetic(binary.getLeft(), leftType,
                    binary.getRight(), rightType);
                if (floated != null) {
                    return floated;
                }
                // A bare NULL in arithmetic is a NUMBER(18,0) on the account — n4_0 + NULL declares
                // NUMBER(19,0), NULL * n10_2 NUMBER(28,2), NULL / n10_2 NUMBER(26,6), NULL + NULL
                // NUMBER(19,0) — and a NUMBER(1,0) under the remainder operator (NULL % 7 is
                // NUMBER(2,0), NULL % n10_2 NUMBER(10,2)); a FLOAT or a text beside it keeps its own
                // rule. Beside a DATE, a TIME, a TIMESTAMP or a BOOLEAN the pair is refused at the
                // operator, the NULL spelled as such: "Invalid argument types for function '+':
                // (DATE, NULL)" (all live-verified).
                final boolean leftNull = leftType == null && isNullLiteral(binary.getLeft());
                final boolean rightNull = rightType == null && isNullLiteral(binary.getRight());
                if (leftNull && refusesNullBeside(rightType) || rightNull && refusesNullBeside(leftType)) {
                    throw new RuntimeException(positioned("Invalid argument types for function '"
                        + operatorSymbol(binary.getOperator()) + "': ("
                        + (leftNull ? "NULL" : SqlTypeNames.canonical(leftType)) + ", "
                        + (rightNull ? "NULL" : SqlTypeNames.canonical(rightType)) + ")", binary));
                }
                if (leftNull && (rightNull || acceptsNullBeside(rightType))) {
                    leftType = arithmeticNullType(binary.getOperator(), rightType);
                }
                if (rightNull && (leftNull || acceptsNullBeside(leftType))) {
                    rightType = arithmeticNullType(binary.getOperator(), leftType);
                }
            }
            if (isNumericOperator(binary.getOperator())) {
                leftType = impliedNumeric(binary.getLeft(), leftType, rightType, binary.getOperator());
                rightType = impliedNumeric(binary.getRight(), rightType, leftType, binary.getOperator());
            }
            // The quoted-string interval has no type of its own to meet a typed interval with: live refuses
            // INTERVAL '1' DAY + INTERVAL '1 hour' by its argument types, the quoted one spelled INTERVAL.
            if (isNumericOperator(binary.getOperator())
                    && (isQuotedInterval(binary.getLeft()) && IntervalArithmeticTypes.isInterval(rightType)
                        || isQuotedInterval(binary.getRight()) && IntervalArithmeticTypes.isInterval(leftType))) {
                throw new RuntimeException(positioned("Invalid argument types for function '"
                    + operatorSymbol(binary.getOperator()) + "': ("
                    + (isQuotedInterval(binary.getLeft()) ? "INTERVAL" : SqlTypeNames.canonical(leftType)) + ", "
                    + (isQuotedInterval(binary.getRight()) ? "INTERVAL" : SqlTypeNames.canonical(rightType)) + ")",
                    binary));
            }
            // An operand pair Snowflake refuses outright is refused HERE, where both declared types
            // are known — the runtime values could not name them, since a value carries its own width
            // rather than its column's. The static channel is consulted at compile time, so the
            // refusal lands even over a table with no rows, as live's does.
            final String refusal = BinaryOperationTypes.refusalFor(binary.getOperator(), leftType,
                rightType);
            if (refusal != null) {
                throw new RuntimeException(positioned(refusal, binary));
            }
            return BinaryOperationTypes.resultOf(binary.getOperator(), leftType, rightType);
        }
        if (expr instanceof CastExpression) {
            // A STRUCTURED or VECTOR target carries declared parameters that the target-type TEXT
            // cannot express (getText() drops the whitespace). Those parameters are a static property
            // of the type in Snowflake — live, SYSTEM$TYPEOF(NULL::OBJECT(x VARCHAR)) still reports
            // OBJECT(x VARCHAR) and SYSTEM$TYPEOF(NULL::VECTOR(FLOAT,3)) reports VECTOR(FLOAT, 3) —
            // so the cast is where an expression takes on that type.
            final CastExpression cast = (CastExpression) expr;
            // A source the target cannot take is refused HERE, as the cast's type is asked for, so
            // the refusal lands inside-out and ahead of any rule about the enclosing expression.
            visitor.rejectCastSourceStatically(cast);
            if (cast.getDeclaredTarget() != null) {
                return cast.getDeclaredTarget();
            }
            final NumericType declaredNumber = numberTargetWithParameters(cast.getTargetType());
            final DataType target = declaredNumber != null ? declaredNumber : typeForName(cast.getTargetType());
            // A NUMBER cast to a TIMESTAMP flavour declares the number's own SCALE as its precision,
            // and a BOOLEAN cast to an exact number is NUMBER(2,0) whatever width the cast spells.
            // Both rules live in ConvertedValueType, because a typed scripting declaration applies the
            // same conversion and has to report the same type.
            return ConvertedValueType.of(infer(cast.getExpression()), target);
        }
        if (expr instanceof WindowFunctionExpression) {
            return windowResultType((WindowFunctionExpression) expr);
        }
        if (expr instanceof CaseExpression) {
            visitor.collationOf(expr);
            final List<Expression> branches = caseBranches((CaseExpression) expr);
            if (allBranchesFile(branches)) {
                return FileType.FILE;
            }
            final DataType binaryBranches = commonBinaryBranch(branches);
            if (binaryBranches != null) {
                return binaryBranches;
            }
            final DataType geoBranches = commonGeoBranch(branches);
            if (geoBranches != null) {
                return geoBranches;
            }
            rejectIncompatibleBranches(branches);
            final DataType semiStructured = semiStructuredBranchFold(branches);
            if (semiStructured != null) {
                return semiStructured;
            }
            final DataType variantMixed = variantBranchFold(branches);
            if (variantMixed != null) {
                return variantMixed;
            }
            return foldedBranchType(branches);
        }
        if (expr instanceof FunctionCallExpression) {
            final FunctionCallExpression call = visitor.splicedStarArguments((FunctionCallExpression) expr);
            if (call.getNameExpression() != null) {
                return null;   // IDENTIFIER(expr): the target function is dynamic
            }
            // IDENTIFIER(<value>) names a column, so it carries the column's own type — which is what
            // the predicate rule reads to refuse `WHERE IDENTIFIER('a')` over a NUMBER (live-verified).
            final ColumnReferenceExpression named = visitor.identifierCallReference(call);
            if (named != null) {
                return infer(named);
            }
            // A name that resolves to nothing is refused HERE, at compile time. Raising it from the
            // evaluator instead made the refusal data-dependent: over a table with no rows nothing
            // was evaluated, so the statement was accepted — and a view or CTAS over it was created.
            visitor.requireResolvableFunctionName(call);
            final String funcName = call.getFunctionName().toUpperCase();
            // A COLLATE call is judged here — its operand, then its specification — and every call that
            // hands a collation on settles its arguments' here, refusing two that disagree.
            if ("COLLATE".equals(funcName) && call.getArguments().size() == 2) {
                final DataType collated = visitor.collateResultType(call);
                if (collated != null) {
                    return collated;
                }
            }
            visitor.collationOf(call);
            // A comparing function's own collation after the one it hands on: a DECODE whose results
            // and whose search values both disagree is refused for its results (live-verified).
            visitor.validateComparingCollation(call);
            // SYSTEM$TYPEOF answers a string of no width: its result column is VARCHAR(134217728), typed
            // again it reads VARCHAR, and a table built over it stores VARCHAR(16777216) (live-verified).
            if ("SYSTEM$TYPEOF".equals(funcName)) {
                return WidthlessStringType.WIDTHLESS;
            }
            if (CONDITIONAL_FUNCTIONS.contains(funcName)) {
                return conditionalFoldType(funcName, conditionalBranches(funcName, call.getArguments()));
            }
            // A BINARY argument survives the constructs that hand it back or reshape it. Restricted to
            // binaries on purpose: an aggregate's declared type is NOMINAL in the registry, so reading
            // it for the other families would hand every rule a type live disagrees with.
            if (passesItsArgumentThrough(funcName) && !call.getArguments().isEmpty()) {
                // The whole type travels, not just the binary family: live declares MIN(v) over a
                // VARCHAR(5) as VARCHAR(5), MIN(n) over NUMBER(10,2) as NUMBER(10,2) and MIN(d) over a
                // DATE as DATE — the argument's type exactly, since the answer IS one of the values.
                final DataType passed = infer(call.getArguments().get(0));
                if (passed != null) {
                    // NULLIFZERO is the exception within the pass-through family: a VARCHAR argument
                    // comes back at the UNKNOWN length, not its own — live declares NULLIFZERO over a
                    // VARCHAR(16777216) column as VARCHAR(134217728), where MIN keeps VARCHAR(5).
                    if ("NULLIFZERO".equals(funcName) && passed instanceof StringType) {
                        return new StringType("VARCHAR", DeclaredTypeFold.UNKNOWN_LENGTH_VARCHAR);
                    }
                    // REVERSE is not really one of them: it is a STRING function that happens to hand
                    // a text and a binary back at their own widths. Over anything else it converts
                    // first, and its result is the 128MB text every converting string function
                    // declares — live types REVERSE(n) over a NUMBER, a DATE, a BOOLEAN, a FLOAT, a
                    // VARIANT and an integer literal alike as VARCHAR(134217728), and a CTAS over it
                    // declares TEXT.
                    if ("REVERSE".equals(funcName)
                            && !(passed instanceof StringType) && !(passed instanceof BinaryType)) {
                        return new StringType("VARCHAR", DeclaredTypeFold.UNKNOWN_LENGTH_VARCHAR);
                    }
                    return passed;
                }
            }
            if ("ZEROIFNULL".equals(funcName) && call.getArguments().size() == 1) {
                final DataType zeroed = zeroIfNullResultType(call.getArguments().get(0));
                if (zeroed != null) {
                    return zeroed;
                }
            }
            final DataType aggregated = aggregateResultType(funcName, call);
            if (aggregated != null) {
                return aggregated;
            }
            // COUNT is the one AGGREGATE whose declared type is worth reading: it never passes an
            // argument through — it always answers a row count — so the eighteen digits live declares
            // are the whole story, where SUM or MAX would have to take their argument's type.
            if (funcName.equals("COUNT")) {
                return IntegerResultWidths.COUNTER;
            }
            // A cardinality read out of a HyperLogLog state declares the counter's width too, as the
            // approximate count it stands for does.
            if (funcName.equals("HLL_ESTIMATE")) {
                return IntegerResultWidths.COUNTER;
            }
            final DataType bitwise = bitwiseResultType(funcName, call.getArguments());
            if (bitwise != null) {
                return bitwise;
            }
            final DataType uniform = uniformResultType(funcName, call.getArguments());
            if (uniform != null) {
                return uniform;
            }
            if (UntypedNullFold.foldsToUntypedNull(call)) {
                return null;
            }
            final DataType intervalTyped = IntervalFunctions.resultType(funcName, call.getArguments(), this);
            if (intervalTyped != null) {
                return intervalTyped;
            }
            final DataType temporal = temporalFunctionResultType(funcName, call.getArguments());
            if (temporal != null) {
                return temporal;
            }
            final DataType rounded = roundingFamilyResultType(funcName, call.getArguments());
            if (rounded != null) {
                return rounded;
            }
            final DataType divided = divisionFamilyResultType(funcName, call.getArguments());
            if (divided != null) {
                return divided;
            }
            final DataType signed = signResultType(funcName, call.getArguments());
            if (signed != null) {
                return signed;
            }
            final DataType bucketed = widthBucketResultType(funcName, call.getArguments());
            if (bucketed != null) {
                return bucketed;
            }
            if (funcName.equals("DATE_PART") || funcName.equals("EXTRACT")) {
                final DataType partWidth = datePartWidth(call.getArguments());
                if (partWidth != null) {
                    return partWidth;
                }
            }
            final DataType reshapedBinary = binaryFromStringFunction(funcName, call);
            if (reshapedBinary != null) {
                return reshapedBinary;
            }
            if (widthFollowsArgument(funcName)) {
                final DataType derived = argumentDerivedBinary(funcName, call.getArguments());
                if (derived != null) {
                    return derived;
                }
            }
            if (producesVector(funcName)) {
                // Their result type is argument-dependent, so the registry's nominal declaration must
                // never be consulted: an undetermined argument makes the RESULT undetermined too.
                return vectorReturnType(funcName, call.getArguments());
            }
            // FILTER and TRANSFORM are not in the registry, and whatever their lambda computes they answer
            // an ARRAY. REDUCE answers its accumulator, whose type follows the lambda's body (reduceType).
            if (("FILTER".equals(funcName) || "TRANSFORM".equals(funcName)) && !call.getArguments().isEmpty()
                    && call.getArguments().get(call.getArguments().size() - 1) instanceof LambdaExpression) {
                return ArrayType.ARRAY;
            }
            if ("REDUCE".equals(funcName) && call.getArguments().size() == 3
                    && call.getArguments().get(2) instanceof LambdaExpression) {
                return reduceType(call.getArguments());
            }
            // SCALAR functions only. The registry keeps aggregates in their own map, and their declared
            // types are NOMINAL where live's are pass-through — live, a derived
            // {@code MAX(n)} over a NUMBER(10,2) column reports NUMBER(10,2), not the VARIANT the
            // registry names — so reading them here would hand every rule a type live disagrees with.
            // The one thing that IS safe to read off an aggregate is its semi-structured family; that
            // narrow lookup lives in {@link #aggregateReturnType}.
            final BuiltInFunction fn = visitor.getFunctionRegistry().getFunction(funcName);
            if (fn == null) {
                // Not a built-in: a user-defined function is typed as live types it — see udfCallType.
                return visitor.udfCallType(call);
            }
            final DataType declared = fn.getReturnType();
            // A clock function's one argument is its fractional-seconds precision, and the call's type
            // carries it: CURRENT_TIMESTAMP(3) is TIMESTAMP_LTZ(3) and CURRENT_TIME(0) is TIME(0)
            // (live-verified), which is what a column DEFAULT over one is judged on.
            if (declared instanceof DateTimeType && CLOCK_FUNCTIONS.contains(funcName)) {
                final Integer precision = literalPrecisionArgument(call.getArguments());
                if (precision != null) {
                    return new DateTimeType(declared.getName(), precision,
                        ((DateTimeType) declared).hasTimeZone());
                }
            }
            if (declared instanceof DateTimeType && isTimestampConversion(funcName)
                    && !call.getArguments().isEmpty()) {
                final DataType epochSource = infer(call.getArguments().get(0));
                if (epochSource instanceof NumericType && !NumericType.isApproximate(epochSource)) {
                    // A numeric epoch's conversion declares the number's scale plus the scale argument
                    // as its precision, capped at nine: TO_TIMESTAMP(a) over a NUMBER(10,2) is
                    // TIMESTAMP_NTZ(2), TO_TIMESTAMP(a, 3) TIMESTAMP_NTZ(5) and TO_TIMESTAMP(1500, 3)
                    // TIMESTAMP_NTZ(3) (live-verified).
                    return new DateTimeType(declared.getName(),
                        epochPrecision((NumericType) epochSource, literalScaleArgument(call.getArguments())),
                        ((DateTimeType) declared).hasTimeZone());
                }
            }
            // The numeric conversions declare their (precision, scale) IN THE CALL — live types
            // TO_DECIMAL(x, 5, 3) as NUMBER(5,3), which is what a conditional folding over it must
            // see — where the registry holds only the nominal NUMBER(38,0). Without this, a CASE
            // over TO_DECIMAL(x, 5, 3) folded at scale 0 and rounded the fraction away.
            if (declared instanceof NumericType && NUMERIC_CONVERSIONS.contains(funcName)) {
                // A BOOLEAN source declares NUMBER(2,0) WHATEVER the call spells: live types
                // TO_NUMBER(TRUE) and TO_DECIMAL(TRUE, 5, 1) alike as NUMBER(2,0), and the value is
                // the unscaled 1 — the same pair a BOOLEAN cast to any exact number declares.
                if (!call.getArguments().isEmpty()
                        && infer(call.getArguments().get(0)) instanceof BooleanType) {
                    return new NumericType("NUMBER", 2, 0);
                }
                return declaredNumericConversionType(call.getArguments());
            }
            // GET_ABSOLUTE_PATH declares its stage's location and its path together, the other stage functions a
            // bare VARCHAR (see StageFunctionArguments).
            final DataType staged = StageFunctionArguments.resultType(funcName, call, visitor, this);
            if (staged != null) {
                return staged;
            }
            if (declared instanceof StringType && !(declared instanceof UuidType)) {
                // The registry declares one nominal VARCHAR for every string function; live computes a
                // width from the arguments. See StringResultWidths for the measured table.
                final List<DataType> argumentTypes = argumentTypes(call.getArguments());
                final DataType width = StringResultWidths.forFunction(funcName, argumentTypes);
                if (width != null) {
                    return width;
                }
                // An UNTYPED argument leaves the width unknowable, and live spells that as the 128MB
                // unknown length rather than the 16MB storage default — UPPER(NULL) and
                // SUBSTR(NULL, 1, 2) are both VARCHAR(134217728). The column-declaring paths clamp it
                // back to 16MB, which is what a CTAS or a view over the same expression stores.
                for (final DataType argumentType : argumentTypes) {
                    if (argumentType == null) {
                        return new StringType("VARCHAR", DeclaredTypeFold.UNKNOWN_LENGTH_VARCHAR);
                    }
                }
            }
            return declared;
        }
        if (expr instanceof JsonObjectExpression) {
            return ObjectType.OBJECT;
        }
        if (expr instanceof JsonArrayExpression) {
            return ArrayType.ARRAY;
        }
        if (expr instanceof ObjectAccessExpression || expr instanceof ArrayAccessExpression) {
            // Path extraction (v:field, v['key'], arr[0]) yields VARIANT.
            return VariantType.VARIANT;
        }
        if (expr instanceof IsNullExpression || expr instanceof InExpression
                || expr instanceof BetweenExpression || expr instanceof TupleInExpression
                || expr instanceof QuantifiedComparisonExpression || expr instanceof LikeAnyAllExpression) {
            visitor.validatePredicateCollations(expr);
            return BooleanType.BOOLEAN;
        }
        return null;
    }

    /**
     * The value-choosing functions, whose result is one of their branches rather than a fixed type.
     * Live-verified over a FILE column: {@code IFF(TRUE, f, f)}, {@code COALESCE(f, f)},
     * {@code NVL(f, f)}, {@code GREATEST(f, f)} and {@code LEAST(f, f)} all report a result column of
     * type FILE — which is why {@code GROUP BY IFF(TRUE, f, f)} is rejected as a FILE key there while
     * the same expression is perfectly legal in a projection.
     */
    private static final Set<String> CONDITIONAL_FUNCTIONS = new HashSet<>(Arrays.asList(
        "IFF", "COALESCE", "NVL", "IFNULL", "GREATEST", "LEAST", "DECODE", "NVL2",
        // NULLIF folds its two arguments like the rest — and refuses a binary beside a string in the
        // same sentence — but keeps the FIRST one's flavour; see nullIfResultType.
        "NULLIF",
        // The IGNORE_NULLS pair takes the same branch vocabulary and gives the same refusal live:
        // GREATEST_IGNORE_NULLS(b, s) is "Can not convert parameter 'QX.S' …", word for word.
        "GREATEST_IGNORE_NULLS", "LEAST_IGNORE_NULLS"));


    /**
     * The branch expressions whose type a conditional's result takes: every argument, except that
     * {@code IFF}'s first argument is the CONDITION rather than a value.
     */
    static List<Expression> conditionalBranches(final String funcName, final List<Expression> args) {
        if (funcName.equals("IFF") || funcName.equals("NVL2")) {
            // Both lead with an argument that is TESTED rather than returned: IFF's condition and
            // NVL2's subject. Live proves it for NVL2 — NVL2(s100, b4, b100) declares BINARY(100), so
            // the VARCHAR test contributes nothing.
            return args.size() > 1 ? args.subList(1, args.size()) : new ArrayList<Expression>();
        }
        if (funcName.equals("DECODE")) {
            return decodeBranches(args);
        }
        return args;
    }

    /**
     * DECODE's value branches. Its layout is {@code (expr, search1, result1, …, [default])}, so the
     * RESULTS are the arguments at odd positions counting from the second — indices 2, 4, 6 … — and the
     * trailing default is present only when the argument count is EVEN. Everything else is a search
     * value, which live ignores for the result type: {@code DECODE(s100, 'x', b4, b100)} declares
     * BINARY(100) though its expression and search are strings.
     */
    private static List<Expression> decodeBranches(final List<Expression> args) {
        final List<Expression> branches = new ArrayList<>();
        for (int i = 2; i < args.size(); i += 2) {
            branches.add(args.get(i));
        }
        if (args.size() % 2 == 0 && args.size() >= 4) {
            branches.add(args.get(args.size() - 1));
        }
        return branches;
    }

    /** A CASE expression's value branches: each WHEN's result, plus the ELSE when present. */
    private static List<Expression> caseBranches(final CaseExpression expr) {
        final List<Expression> branches = new ArrayList<>();
        for (final WhenClause when : expr.getWhenClauses()) {
            branches.add(when.getResult());
        }
        if (expr.getElseExpression() != null) {
            branches.add(expr.getElseExpression());
        } else {
            // No ELSE is an ELSE NULL, and the fold sees the implicit branch exactly as a written one:
            // a text CASE without ELSE declares VARCHAR(134217728) whatever width its branches carry,
            // while a NUMBER, DATE, BOOLEAN or VARIANT one keeps its own type (live-verified).
            branches.add(new LiteralExpression(null, LiteralType.NULL));
        }
        return branches;
    }

    /**
     * Whether EVERY branch is statically a FILE. Deliberately one-directional: this rule only ever
     * promotes a conditional to FILE, never to any other type, so it cannot change how a non-FILE
     * expression is judged anywhere. A single undetermined branch leaves the whole thing undetermined,
     * keeping the "null can never cause a false rejection" contract intact.
     */
    private boolean allBranchesFile(final List<Expression> branches) {
        if (branches.isEmpty()) {
            return false;
        }
        for (final Expression branch : branches) {
            if (!(infer(branch) instanceof FileType)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The value-choosing functions that PRESERVE a geospatial branch type — every conditional except
     * the two that ORDER their arguments. Live, {@code IFF(TRUE, g, g)},
     * {@code COALESCE(g, g)}, {@code NVL(g, g)}, {@code IFNULL(g, g)}, {@code NVL2(g, g, g)},
     * {@code DECODE(n, 10, g, g)}, {@code COALESCE(g, NULL)} and {@code CASE WHEN TRUE THEN g ELSE g
     * END} all report a result column of type GEOGRAPHY, which is why {@code GROUP BY IFF(TRUE, g, g)}
     * is refused as a GEOGRAPHY key there. {@code GREATEST} and {@code LEAST} are OUT: live refuses a
     * geo argument to either outright ("Invalid argument types for function 'GREATEST': (GEOGRAPHY,
     * GEOGRAPHY)"), so they never produce one and reporting a type for them would only change which
     * message a key position gives.
     */
    private static final Set<String> GEO_PRESERVING_CONDITIONALS = new HashSet<>(Arrays.asList(
        "IFF", "COALESCE", "NVL", "IFNULL"));

    /**
     * The one GEOSPATIAL type EVERY branch produces, or null when they disagree or any branch is not
     * statically geo — the same "a single undetermined branch leaves the whole thing undetermined"
     * contract {@link #allBranchesFile} keeps, so this can only ever narrow to a type the whole
     * conditional really has.
     */
    private DataType commonGeoBranch(final List<Expression> branches) {
        if (branches.isEmpty()) {
            return null;
        }
        DataType common = null;
        for (final Expression branch : branches) {
            final DataType branchType = infer(branch);
            if (!GeoTypes.isGeo(branchType)) {
                return null;
            }
            if (common == null) {
                common = branchType;
            } else if (!common.getClass().equals(branchType.getClass())) {
                return null;
            }
        }
        return common;
    }

    /**
     * The SEMI-STRUCTURED type an expression is known to produce — OBJECT or ARRAY, never VARIANT —
     * reading through the conditionals, which {@link #infer} deliberately does not. Returns null for
     * anything else, including VARIANT.
     *
     * <p>Live-verified over an OBJECT column and an ARRAY column: {@code MAX(o)} is
     * rejected, and so are {@code MAX(IFF(TRUE, o, o))}, {@code MAX(COALESCE(o, o))},
     * {@code MAX(GREATEST(o, o))} and {@code MAX(CASE WHEN TRUE THEN o ELSE o END)} — a conditional
     * over OBJECT branches IS an OBJECT there. A VARIANT never is: {@code MAX(v)} is accepted even
     * when the VARIANT HOLDS an object ({@code MAX(vo)} returned {@code {"x": 2}}), so the rule reads
     * the DECLARED type and never the runtime value.
     *
     * <p>The producing FUNCTIONS are no longer listed here: each one declares its real return type in
     * the registry, so {@link #infer} already answers OBJECT for {@code OBJECT_CONSTRUCT}
     * and ARRAY for {@code SPLIT} — and does so for their ALIASES too, which a name map could not.
     * What remains is only the conditional read-through, still kept out of {@link #infer} because a
     * conditional's branches unify by rules that have been measured for the agreeing case alone.
     */
    DataType inferSemiStructured(final Expression expr) {
        final DataType direct = infer(expr);
        // A structured MAP counts, even though it is deliberately not an ObjectType subclass (see
        // MapType): live treats it exactly like an OBJECT here — MAX(sm) is "Function MAX
        // does not support MAP(VARCHAR(16777216), NUMBER(38,0)) argument type", 'x' || sm and SUM(sm)
        // name the whole MAP type in their argument lists, and MEDIAN(sm) names it in the
        // incompatible-types pair. Frostlake used to accept all four and hand back the JSON text.
        if (direct instanceof ObjectType || direct instanceof ArrayType || direct instanceof MapType) {
            return direct;
        }
        if (expr instanceof CaseExpression) {
            return commonSemiStructuredBranch(caseBranches((CaseExpression) expr));
        }
        if (expr instanceof FunctionCallExpression) {
            final FunctionCallExpression call = visitor.splicedStarArguments((FunctionCallExpression) expr);
            if (call.getNameExpression() == null) {
                final String funcName = call.getFunctionName().toUpperCase();
                if (CONDITIONAL_FUNCTIONS.contains(funcName)) {
                    return commonSemiStructuredBranch(conditionalBranches(funcName, call.getArguments()));
                }
                return aggregateReturnType(funcName);
            }
        }
        return null;
    }

    /** The conversions whose result type is the (p, s) their own arguments declare. */
    /** The clock functions whose optional argument is a fractional-seconds precision. */
    private static final Set<String> CLOCK_FUNCTIONS = Set.of(
        "CURRENT_TIMESTAMP", "LOCALTIMESTAMP", "CURRENT_TIME", "LOCALTIME");

    private static final Set<String> NUMERIC_CONVERSIONS = Set.of(
        "TO_NUMBER", "TO_DECIMAL", "TO_NUMERIC", "TRY_TO_NUMBER", "TRY_TO_DECIMAL", "TRY_TO_NUMERIC");

    /**
     * The NUMBER a numeric conversion declares: the trailing integer literals after the source (and
     * optional format) are (precision) or (precision, scale) — a lone precision carries scale 0, and
     * a call declaring none is the nominal NUMBER(38,0) (live-verified through the refusal echo and
     * SYSTEM$TYPEOF alike).
     */
    private DataType declaredNumericConversionType(final List<Expression> args) {
        Integer last = null;
        Integer secondLast = null;
        if (args.size() >= 2) {
            last = intLiteral(args.get(args.size() - 1));
        }
        if (args.size() >= 3) {
            secondLast = intLiteral(args.get(args.size() - 2));
        }
        if (secondLast != null && last != null) {
            return new NumericType("NUMBER", secondLast.intValue(), last.intValue());
        }
        if (last != null) {
            return new NumericType("NUMBER", last.intValue(), 0);
        }
        return new NumericType("NUMBER", 38, 0);
    }

    /** The int a literal argument holds, or null when it is not an integer literal. */
    private static Integer intLiteral(final Expression arg) {
        if (!(arg instanceof LiteralExpression)) {
            return null;
        }
        final Object value = ((LiteralExpression) arg).getValue();
        if (value instanceof Integer || value instanceof Long) {
            return Integer.valueOf(((Number) value).intValue());
        }
        return null;
    }

    /**
     * The declared return type of an AGGREGATE, kept when it is OBJECT or ARRAY and dropped otherwise.
     * {@link #infer} reads only the scalar registry, so without this {@code ARRAY_AGG} and
     * {@code ARRAY_UNIQUE_AGG} (ARRAY) and {@code OBJECT_AGG} (OBJECT) would be undetermined even though
     * live names them so ({@code SYSTEM$TYPEOF}, ) and rejects {@code MAX(ARRAY_AGG(n))} for it.
     *
     * <p>Only those two families come through. An aggregate's declared type is nominal in the registry
     * — {@code MAX} says VARIANT where live says whatever it was given — so letting a NUMBER or VARCHAR
     * out of here would feed the strict-argument rules a type live does not agree with.
     */
    private DataType aggregateReturnType(final String funcName) {
        final BuiltInFunction aggregate = visitor.getFunctionRegistry().getAggregateFunction(funcName);
        if (aggregate == null) {
            return null;
        }
        final DataType declared = aggregate.getReturnType();
        // A BINARY aggregate is let out too: the HLL STATE family's states are BINARY on the account, and
        // a state read back by HLL_ESTIMATE or HLL_COMBINE has to type as one.
        return declared instanceof ObjectType || declared instanceof ArrayType
            || declared instanceof BinaryType ? declared : null;
    }

    /**
     * The one semi-structured type EVERY branch produces, or null when they disagree or any branch is
     * undetermined — the same "a single undetermined branch leaves the whole thing undetermined"
     * contract {@link #allBranchesFile} keeps, so this can only ever narrow to a type the whole
     * conditional really has.
     */
    private DataType commonSemiStructuredBranch(final List<Expression> branches) {
        if (branches.isEmpty()) {
            return null;
        }
        DataType common = null;
        for (final Expression branch : branches) {
            final DataType branchType = inferSemiStructured(branch);
            if (branchType == null) {
                return null;
            }
            if (common == null) {
                common = branchType;
            } else if (!common.getClass().equals(branchType.getClass())) {
                return null;
            }
        }
        return common;
    }

    /**
     * The VECTOR-producing functions' result type, which depends on their ARGUMENTS rather than being
     * fixed — so a nested call like {@code VECTOR_L2_DISTANCE(VECTOR_TRUNC(a, 2), VECTOR_TRUNC(b, 2))}
     * still type-checks. All live-verified via {@code SYSTEM$TYPEOF}:
     *
     * <ul>
     *   <li>{@code VECTOR_TRUNC} / {@code VECTOR_TRUNCATE} keep the element type and take the requested
     *       dimension — {@code VECTOR(FLOAT, 2)} from a {@code VECTOR(FLOAT, 3)},
     *       {@code VECTOR(INT, 2)} from a {@code VECTOR(INT, 3)};</li>
     *   <li>{@code VECTOR_NORMALIZE} is always FLOAT, even over an INT vector
     *       ({@code VECTOR_NORMALIZE([1,2,3]::VECTOR(INT,3))} is {@code VECTOR(FLOAT, 3)});</li>
     *   <li>{@code VECTOR_SUM} / {@code VECTOR_MIN} / {@code VECTOR_MAX} keep the element type
     *       ({@code VECTOR_SUM} over a {@code VECTOR(INT,3)} column is {@code VECTOR(INT, 3)}) while
     *       {@code VECTOR_AVG} is always FLOAT.</li>
     * </ul>
     *
     * Returns null when this is not one of them, or when the argument's own type is undetermined.
     */
    private DataType vectorReturnType(final String funcName, final List<Expression> args) {
        if (args.isEmpty()) {
            return null;
        }
        final boolean alwaysFloat = funcName.equals("VECTOR_NORMALIZE") || funcName.equals("VECTOR_AVG");
        final DataType source = infer(args.get(0));
        if (!(source instanceof VectorType)) {
            return null;
        }
        final VectorType vector = (VectorType) source;
        final VectorElementType element = alwaysFloat ? VectorElementType.FLOAT : vector.getElementType();
        if (!funcName.equals("VECTOR_TRUNC") && !funcName.equals("VECTOR_TRUNCATE")) {
            return new VectorType(element, vector.getDimension());
        }
        if (args.size() < 2 || !(args.get(1) instanceof LiteralExpression)) {
            return null;
        }
        final Object dimension = ((LiteralExpression) args.get(1)).getValue();
        if (!(dimension instanceof Number)) {
            return null;
        }
        return new VectorType(element, ((Number) dimension).intValue());
    }

    /** The width STRING_AS_BINARY's result saturates at: a bare BINARY column's. */
    private static final int MAX_BINARY_LENGTH = 8388608;

    /**
     * The binary type a CONDITIONAL takes from its branches: the WIDEST of them, fixed only when EVERY
     * branch is. Null when any branch is not a binary at all, which leaves the whole thing undetermined
     * the way {@link #commonSemiStructuredBranch} does.
     *
     * <p>THE CONDITIONALS AND A UNION DO NOT AGREE, and the difference is measured rather than
     * reasoned: over a BINARY(4) beside a VARBINARY(4) — same width, one fixed, one not — a UNION
     * answers fixed TRUE while {@code IFF} answers FALSE. So this cannot delegate to
     * {@link BinaryType#getCommonType}, which carries the union's rule.
     */
    private DataType commonBinaryBranch(final List<Expression> branches) {
        if (branches.isEmpty()) {
            return null;
        }
        int width = 0;
        boolean allFixed = true;
        boolean anyBinary = false;
        // The width's spelling is the LEADING binary branch's, widened by what follows it — see
        // BinaryWidthSpelling#meeting. A bare NULL branch has no type and takes no part.
        BinaryWidthSpelling spelling = null;
        for (final Expression branch : branches) {
            final DataType branchType = infer(branch);
            if (branchType instanceof BinaryType) {
                anyBinary = true;
                width = Math.max(width, ((BinaryType) branchType).getMaxLength());
                allFixed &= ((BinaryType) branchType).isFixed();
                final BinaryWidthSpelling own = ((BinaryType) branchType).getWidthSpelling();
                spelling = spelling == null ? own : spelling.meeting(own);
            } else if (branchType != null) {
                // A determinate branch of another family: refuse, whichever SIDE the binary is on.
                // Scanning rather than stopping at the first non-binary is what makes the reversed
                // order work — in IFF(c, s, b) the offender is the SECOND branch, not the first.
                anyBinary = anyBinary || laterBranchIsBinary(branches, branch);
                if (anyBinary) {
                    rejectUnconvertibleBranch(branches, branch, branchType);
                }
                return null;
            }
        }
        return anyBinary ? new BinaryType(width, allFixed, spelling) : null;
    }

    /**
     * Live REFUSES a binary branch beside another family rather than widening to something that holds
     * both, and it names the offending operand and both types:
     *
     * <pre>
     *   IFF(c, b, s)   Can not convert parameter 'BM.S' of type [VARCHAR(10)] into expected type [BINARY(4)]
     *   IFF(c, s, b)   Can not convert parameter 'BM.B' of type [BINARY(4)] into expected type [VARCHAR(10)]
     * </pre>
     *
     * <p>The EXPECTED type is the FIRST branch's, whichever family that is, and the parameter named is
     * the first later branch that does not fit. Measured across VARCHAR, NUMBER, DATE, BOOLEAN,
     * VARIANT, OBJECT and ARRAY, and the sentence carries no position — unlike the operator refusals.
     *
     * <p>Only raised when a BINARY is one of the two, which is the pairing that was measured. A branch
     * whose type is UNDETERMINED is never refused: it may hold anything, so refusing it would be a
     * guess.
     */
    /** Whether any branch AFTER {@code from} is a binary — the reversed-order case. */
    /**
     * The declared type a conditional's BRANCHES agree on, by the same rule a set operation's arms
     * follow — see DeclaredTypeFold, where both surfaces' measured cells live. Undetermined stays
     * undetermined: a branch whose type is unknown may hold anything.
     *
     * <p>A branch written as the word NULL is not an undetermined one and does not make the whole
     * conditional undetermined — it simply carries no type of its own, so the others decide:
     *
     * <pre>
     *   COALESCE(i, NULL)      NUMBER(38,0)        the number decides, in either order
     *   COALESCE(NULL, d)      DATE                and so does a date, a time, a boolean, a binary
     *   COALESCE(NULL, v)      VARCHAR(134217728)  a STRING keeps its family at the 128MB width
     *   COALESCE(NULL, NULL)   VARCHAR(0)          nothing to decide: a zero-width string
     * </pre>
     */
    /**
     * The type a conditional declares over its value branches — the fold every member of the family
     * shares, refusals included: a FILE beside a FILE, a common BINARY, a common GEO, the
     * cannot-convert refusal, the semi-structured and VARIANT folds, and the numeric / string / temporal
     * fold last. LAG and LEAD with a default declare exactly this over the argument and the default —
     * live plans the default as one more branch, so {@code LAG(a, 1, 1.555)} over a NUMBER(10,2) is
     * NUMBER(11,3) and {@code LAG(d, 1, 0)} over a DATE is refused in the conditional's own words.
     *
     * @param funcName the conditional's name, which decides the NULLIF and GEO exceptions
     * @param branches the value branches
     * @return the declared type, or null when undetermined
     */
    private DataType conditionalFoldType(final String funcName, final List<Expression> branches) {
        if (allBranchesFile(branches)) {
            return FileType.FILE;
        }
        final DataType binaryBranches = commonBinaryBranch(branches);
        if (binaryBranches != null) {
            return binaryBranches;
        }
        final DataType geo = GEO_PRESERVING_CONDITIONALS.contains(funcName)
            ? commonGeoBranch(branches) : null;
        if (geo != null) {
            return geo;
        }
        rejectIncompatibleBranches(branches);
        final DataType semiStructured = funcName.equals("NULLIF") ? null
            : semiStructuredBranchFold(branches);
        if (semiStructured != null) {
            return semiStructured;
        }
        final DataType variantMixed = funcName.equals("NULLIF") ? null
            : variantBranchFold(branches);
        if (variantMixed != null) {
            return variantMixed;
        }
        return funcName.equals("NULLIF") ? nullIfResultType(branches)
            : foldedBranchType(branches);
    }

    private DataType foldedBranchType(final List<Expression> branches) {
        final List<DataType> branchTypes = new ArrayList<>(branches.size());
        // Per branch, the numeric type a STRING LITERAL measures as — a string literal beside a
        // number contributes exactly what the same number unquoted would, which only its own text
        // can say. A string COLUMN has no text to measure and stays null here.
        final List<DataType> literalMeasurements = new ArrayList<>(branches.size());
        boolean sawNullBranch = false;
        for (final Expression branch : branches) {
            if (isNullLiteral(branch)) {
                sawNullBranch = true;
                continue;
            }
            branchTypes.add(infer(branch));
            literalMeasurements.add(stringLiteralNumericType(branch));
        }
        if (branchTypes.isEmpty()) {
            // Every branch the bare word NULL: the conditional has no type at all (see UntypedNullFold).
            return null;
        }
        final DataType folded = DeclaredTypeFold.foldBranches(branchTypes, literalMeasurements);
        // A UUID is no text here: IFF(TRUE, u, NULL) is a UUID on the account, not the 128MB VARCHAR. Nor is a
        // string of no width widened: IFF(TRUE, NULL, NULL::VARCHAR) is bare VARCHAR (live-verified).
        if (sawNullBranch && folded instanceof StringType && !(folded instanceof UuidType)
                && !(folded instanceof WidthlessStringType) && !(folded instanceof LengthlessStringType)) {
            return new StringType("VARCHAR", DeclaredTypeFold.UNKNOWN_LENGTH_VARCHAR);
        }
        return folded;
    }

    /**
     * NULLIF's declared type: its FIRST argument, widened by the second only within that argument's own
     * family. It answers the first argument or NULL, never the second, and live's widths say exactly
     * that — a wider partner of the SAME family widens it, a partner of another does not:
     *
     * <pre>
     *   NULLIF(v, w)      VARCHAR(9)          the wider string wins — so the second IS consulted
     *   NULLIF(i, n)      NUMBER(38,2)        and so is its scale
     *   NULLIF(v, bo)     VARCHAR(134217728)  a partner with no length of its own widens it fully
     *   NULLIF(d, ts)     DATE                but a TIMESTAMP does not make it a timestamp
     *   NULLIF(i, f)      NUMBER(38,0)        nor a FLOAT make it approximate
     *   NULLIF(NULL, v)   VARCHAR(0)          nothing there to widen
     * </pre>
     *
     * @param branches the two arguments
     * @return the declared type, or null when undetermined
     */
    private DataType nullIfResultType(final List<Expression> branches) {
        if (branches.size() != 2) {
            return null;
        }
        if (isNullLiteral(branches.get(0))) {
            return null;
        }
        final DataType first = infer(branches.get(0));
        if (first == null) {
            return null;
        }
        final DataType second = infer(branches.get(1));
        // The SECOND argument's own family decides, not the fold's: a number beside a TEXT first
        // argument is cast to text at the conversion width, so NULLIF(VARCHAR(10), NUMBER(10,2))
        // declares VARCHAR(134217728) live, while a text or a FLOAT beside a NUMBER first argument is
        // cast to that argument's own type and NULLIF(NUMBER(10,2), VARCHAR(10)) keeps NUMBER(10,2).
        // Only a second argument of the first's family widens it: NULLIF(n, 1/3) is NUMBER(14,6).
        if (first instanceof StringType && second != null && !(second instanceof StringType)) {
            return new StringType("VARCHAR", DataTypeParser.CAST_STRING_DEFAULT);
        }
        if (second != null && (!second.getClass().equals(first.getClass())
                || !second.getName().equalsIgnoreCase(first.getName()))) {
            return first;
        }
        // A NULL or untyped second argument folds as it always did (NULLIF(v, NULL) is the unbounded text).
        final DataType folded = foldedBranchType(branches);
        if (folded == null || !folded.getClass().equals(first.getClass())
                || !folded.getName().equalsIgnoreCase(first.getName())) {
            return first;
        }
        return folded;
    }

    /**
     * What the temporal functions declare, which the registry's one nominal TIMESTAMP_NTZ cannot say:
     * the answer is the ARGUMENT's own temporal type, and only DATEADD over a DATE is decided by the
     * unit as well. Measured across DATE, TIME and all three timestamp flavours:
     *
     * <pre>
     *   DATEADD(day, 1, d)      DATE            a whole-day unit leaves a DATE a DATE
     *   DATEADD(hour, 1, d)     TIMESTAMP_NTZ   a sub-day one promotes it
     *   DATEADD(hour, 1, tm)    TIME            a TIME stays a TIME
     *   DATEADD(day, 1, tl)     TIMESTAMP_LTZ   a timestamp keeps its FLAVOUR, whatever the unit
     *   DATE_TRUNC(hour, d)     DATE            truncation never changes the family at all
     *   ADD_MONTHS(tz, 1)       TIMESTAMP_TZ    nor does adding months
     *   LAST_DAY(tl)            DATE            these two always answer a DATE
     *   TRUNC(ts, 'month')      TIMESTAMP_NTZ   TRUNC is temporal when its subject is
     * </pre>
     *
     * @param funcName the function's name, upper-cased
     * @param args     the call's arguments
     * @return the declared type, or null when this is not one of these functions
     */
    /**
     * The width a bitwise function declares, which follows its arguments' INTEGER digits rather than
     * the family's NUMBER(38,0) (live-measured cell by cell): a NUMBER(p,s) counts p - s and a literal its
     * own digits. BITAND and BITXOR take the wider argument, at least two digits, a FLOAT counting for
     * nothing and a text for thirteen — the integer part of the NUMBER(18,5) a text reads as — and a text
     * in SECOND place caps the width at thirty-three. BITOR is one digit wider, capped at thirty-eight,
     * with a FLOAT or a text counting eighteen. BITNOT and BITSHIFTRIGHT keep the argument's width, at
     * least two; BITSHIFTLEFT is always thirty-eight. Null for an argument of any other kind.
     */
    private DataType bitwiseResultType(final String funcName, final List<Expression> args) {
        final boolean or = funcName.equals("BITOR");
        final boolean twoOperands = or || funcName.equals("BITAND") || funcName.equals("BITXOR");
        if (!twoOperands && !funcName.equals("BITNOT") && !funcName.equals("BITSHIFTRIGHT")
                && !funcName.equals("BITSHIFTLEFT")) {
            return null;
        }
        if (funcName.equals("BITSHIFTLEFT")) {
            return IntegerResultWidths.WIDEST;
        }
        final int operands = Math.min(twoOperands ? 2 : 1, args.size());
        int digits = 0;
        for (int i = 0; i < operands; i++) {
            final int counted = bitwiseDigits(args.get(i), or);
            if (counted < 0) {
                return null;
            }
            digits = Math.max(digits, counted);
        }
        if (or) {
            digits = Math.min(digits + 1, 38);
        }
        if (twoOperands && !or && operands == 2 && infer(args.get(1)) instanceof StringType) {
            digits = Math.min(digits, 33);
        }
        return new NumericType("NUMBER", Math.max(digits, 2), 0);
    }

    /**
     * UNIFORM's type, which its two BOUNDS decide (live-verified across widths, signs and scales): a FLOAT
     * or text bound draws a FLOAT, and exact bounds draw a NUMBER as wide as the wider bound's integer
     * digits — two at the least — with the larger scale beside them.
     *
     * <pre>
     *   UNIFORM(1, 10, g)      NUMBER(2,0)[SB1]      UNIFORM(0, 9, g) and UNIFORM(5, 5, g) too
     *   UNIFORM(1, 1000, g)    NUMBER(4,0)[SB2]      UNIFORM(-100, 10, g) is NUMBER(3,0)
     *   UNIFORM(1.5, 10, g)    NUMBER(3,1)[SB2]      UNIFORM(0.5, 0.7, g) is NUMBER(3,1) as well
     *   UNIFORM(0.123, 99, g)  NUMBER(5,3)[SB4]      UNIFORM('1', '10', g) and a ::FLOAT bound are FLOAT
     * </pre>
     *
     * <p>An untyped NULL bound takes no part. The tag follows the declared precision, not the bounds'
     * values: UNIFORM(0, 127, g) is NUMBER(3,0)[SB2].
     *
     * @return the type, or null when this is not UNIFORM or a bound's family is not one measured
     */
    private DataType uniformResultType(final String funcName, final List<Expression> args) {
        if (!funcName.equals("UNIFORM") || args.size() != 3) {
            return null;
        }
        int integerDigits = 2;
        int scale = 0;
        for (int i = 0; i < 2; i++) {
            if (isNullLiteral(args.get(i))) {
                continue;
            }
            final DataType bound = infer(args.get(i));
            if (bound instanceof StringType || bound instanceof NumericType && NumericType.isApproximate(bound)) {
                return NumericType.FLOAT;
            }
            if (!(bound instanceof NumericType)) {
                return null;
            }
            final NumericType exact = (NumericType) bound;
            integerDigits = Math.max(integerDigits, exact.getPrecision() - exact.getScale());
            scale = Math.max(scale, exact.getScale());
        }
        return new NumericType("NUMBER", Math.min(integerDigits + scale, 38), scale);
    }

    /** One argument's integer digits for {@link #bitwiseResultType}, or -1 for a kind it does not count. */
    private int bitwiseDigits(final Expression arg, final boolean or) {
        if (isNullLiteral(arg)) {
            return 0;
        }
        final DataType type = infer(arg);
        if (type instanceof StringType) {
            return or ? 18 : 13;
        }
        if (type instanceof NumericType) {
            if (NumericType.isApproximate(type)) {
                return or ? 18 : 0;
            }
            final NumericType exact = (NumericType) type;
            return Math.max(exact.getPrecision() - exact.getScale(), 0);
        }
        return -1;
    }

    private DataType temporalFunctionResultType(final String funcName, final List<Expression> args) {
        if (funcName.equals("LAST_DAY") || funcName.equals("NEXT_DAY")) {
            return DateTimeType.DATE;
        }
        if (funcName.equals("DATEDIFF") || funcName.equals("TIMEDIFF")
                || funcName.equals("TIMESTAMPDIFF")) {
            return args.isEmpty() ? null : dateDifferenceWidth(unitText(args.get(0)),
                args.size() == 3 ? DateDifferenceWidths.plannedKind(infer(args.get(1)), infer(args.get(2))) : null);
        }
        final BuiltInFunction planned = visitor.getFunctionRegistry().getFunction(funcName);
        if (planned instanceof PlannedDateAdd && args.size() == 2) {
            return ((PlannedDateAdd) planned).resultType(infer(args.get(1)));
        }
        if (funcName.equals("DATEADD") || funcName.equals("TIMEADD")
                || funcName.equals("TIMESTAMPADD")) {
            return args.size() < 3 ? null : shiftedTemporal(infer(args.get(2)), unitText(args.get(0)));
        }
        if (funcName.equals("DATE_TRUNC") && args.size() >= 2) {
            final DataType subject = infer(args.get(1));
            return subject instanceof DateTimeType ? atFullPrecision((DateTimeType) subject) : null;
        }
        if (funcName.equals("ADD_MONTHS") || funcName.equals("TRUNC")) {
            final DataType subject = args.isEmpty() ? null : infer(args.get(0));
            return subject instanceof DateTimeType ? atFullPrecision((DateTimeType) subject) : null;
        }
        if (funcName.equals("CONVERT_TIMEZONE")) {
            // Two arguments read the source in a zone and answer WITH one, so the result carries an
            // offset; three arguments convert between two named zones and answer without (live-verified).
            return args.size() == 2 ? DateTimeType.TIMESTAMP_TZ
                : args.size() == 3 ? DateTimeType.TIMESTAMP_NTZ : null;
        }
        return null;
    }

    /**
     * How wide a DATEDIFF is, which the UNIT and the KIND the operands are planned in decide together: two
     * timestamps are nine digits apart counted in hours, eighteen in minutes and thirty-eight in milliseconds,
     * while two DATEs are eighteen apart already in hours and two TIMEs nine in milliseconds (see
     * {@link DateDifferenceWidths}). A pair whose kind is not settled is read as timestamps.
     *
     * @param unit the unit as written, in any spelling
     * @param kind the kind the operands are planned in, or null when it is not settled
     * @return the declared width, or null when the spelling is not a unit
     */
    private DataType dateDifferenceWidth(final String unit, final String kind) {
        final IntervalUnit measured = IntervalUnit.fromSpelling(unit);
        if (measured == null) {
            return null;
        }
        return DateDifferenceWidths.of(kind == null ? DateDifferenceWidths.TIMESTAMP : kind, measured);
    }

    /**
     * The type a temporal takes once a unit's worth of time is added to it.
     *
     * @param subject the shifted expression's type
     * @param unit    the unit as written
     * @return the shifted type, or null when either is not determined
     */
    /**
     * The same temporal family at its full nine fractional digits, which is what every function that
     * KEEPS its subject's family answers with, whatever the subject declared: live types
     * {@code DATE_TRUNC('day', ts3)} over a TIMESTAMP_NTZ(3) as TIMESTAMP_NTZ(9), and the same for
     * ADD_MONTHS, TRUNC, DATEADD and a shift by an INTERVAL, over a TIME and all three timestamp
     * flavours alike. A DATE has no fractional digits and stays as it is.
     *
     * @param subject the subject's temporal type
     * @return the type at precision nine, or the subject itself when it is a DATE
     */
    private DataType atFullPrecision(final DateTimeType subject) {
        if ("DATE".equalsIgnoreCase(subject.getName()) || subject.getPrecision() == 9) {
            return subject;
        }
        return new DateTimeType(subject.getName(), 9, subject.hasTimeZone());
    }

    private DataType shiftedTemporal(final DataType subject, final String unit) {
        final IntervalUnit measured = IntervalUnit.fromSpelling(unit);
        if (!(subject instanceof DateTimeType) || measured == null) {
            return null;
        }
        if (!"DATE".equalsIgnoreCase(subject.getName())) {
            // A TIME and every timestamp flavour keep their own family — at the family's full nine
            // digits, whatever the argument declared: live types DATEADD(day, 1, ts3) over a
            // TIMESTAMP_NTZ(3) column as TIMESTAMP_NTZ(9).
            final DateTimeType kept = (DateTimeType) subject;
            return kept.getPrecision() == 9 ? subject
                : new DateTimeType(kept.getName(), 9, kept.hasTimeZone());
        }
        return measured.isWholeDay() ? subject : DateTimeType.TIMESTAMP_NTZ;
    }

    /**
     * A date-part argument's word, however it was written — {@code day}, {@code 'day'} and {@code dd}
     * all reach here as the text between the parentheses.
     *
     * @param arg the argument holding the unit
     * @return the word, or null when the argument is not a plain one
     */
    private String unitText(final Expression arg) {
        if (arg instanceof LiteralExpression) {
            final Object value = ((LiteralExpression) arg).getValue();
            return value == null ? null : value.toString();
        }
        if (arg instanceof ColumnReferenceExpression) {
            return ((ColumnReferenceExpression) arg).getColumnName();
        }
        return null;
    }

    /**
     * Each argument's declared type, in written order, with null where one is undetermined.
     *
     * @param args the call's arguments
     * @return one entry per argument
     */
    private List<DataType> argumentTypes(final List<Expression> args) {
        final List<DataType> types = new ArrayList<>(args.size());
        for (final Expression arg : args) {
            types.add(infer(arg));
        }
        return types;
    }

    /**
     * ZEROIFNULL's declared type: its argument folded with the zero it substitutes, which live sizes at
     * TWO integer digits rather than the one the literal 0 would carry. The widths measured live say so
     * plainly — the argument's own scale is kept, and only a narrow integer part moves:
     *
     * <pre>
     *   NUMBER(1,0)   NUMBER(2,0)        NUMBER(9,9)    NUMBER(11,9)
     *   NUMBER(2,1)   NUMBER(3,1)        NUMBER(5,4)    NUMBER(6,4)
     *   NUMBER(4,0)   NUMBER(4,0)        NUMBER(10,2)   NUMBER(10,2)
     *   NUMBER(38,0)  NUMBER(38,0)       FLOAT          FLOAT
     * </pre>
     *
     * <p>COALESCE(x, 0) is NOT the same call: live declares {@code COALESCE(m, 0)} NUMBER(5,4) where
     * {@code ZEROIFNULL(m)} is NUMBER(6,4), so the zero this one folds with is its own.
     *
     * @param argument the single argument
     * @return the declared type, or null when the argument has none
     */
    private DataType zeroIfNullResultType(final Expression argument) {
        final DataType argType = infer(argument);
        if (argType == null) {
            // Nothing to fold with — the substituted zero IS the answer's type, and live sizes that at
            // two integer digits: SYSTEM$TYPEOF(ZEROIFNULL(NULL)) is NUMBER(2,0).
            return new NumericType("NUMBER", 2, 0);
        }
        if (argType instanceof VariantType || argType instanceof StringType) {
            // Neither family carries a numeric width to keep, and live does not invent one: it converts
            // the argument to REAL, so ZEROIFNULL over a VARIANT or a VARCHAR is FLOAT whatever it
            // holds — 7.0 for a stored 7, and a run-time cast failure for text that is not a number.
            return NumericType.FLOAT;
        }
        return DeclaredTypeFold.foldBranches(
            Arrays.asList(argType, new NumericType("NUMBER", 2, 0)));
    }

    /**
     * The numeric type a branch written as a STRING LITERAL measures as, or null when the branch is
     * not one or its text is not a number. {@code '5.12345'} measures NUMBER(6,5), exactly as the
     * unquoted literal does, which is what live folds it as — and the text is read as written, so a
     * blank beside the digits leaves it a text: {@code COALESCE(' 12.5 ', n)} folds at NUMBER(18,5).
     *
     * <p>A COLUMN measures the same way when it projects such a literal out of a derived table, a CTE,
     * a LATERAL or a view, at any depth: live folds {@code COALESCE(t, n)} over
     * {@code (SELECT '12.5' AS t, n FROM cf)} as NUMBER(10,2), exactly as {@code COALESCE('12.5', n)},
     * where a cast or any other expression of the literal, a set operation over it or a table stored
     * from it is a string column again (all live-verified; see TableColumn#getSpelledNumber).
     */
    private DataType stringLiteralNumericType(final Expression branch) {
        if (branch instanceof ColumnReferenceExpression) {
            final TableColumn resolved = visitor.resolveDeclaredColumn((ColumnReferenceExpression) branch);
            return resolved == null ? null : resolved.getSpelledNumber();
        }
        return spelledNumber(branch);
    }

    /**
     * The NUMBER a bare string literal spells, or null for anything else — see
     * {@link #stringLiteralNumericType}.
     *
     * @param expr the expression
     * @return the literal's measured NUMBER, or null
     */
    static DataType spelledNumber(final Expression expr) {
        if (!(expr instanceof LiteralExpression)
                || ((LiteralExpression) expr).getType() != LiteralType.STRING) {
            return null;
        }
        final Object value = ((LiteralExpression) expr).getValue();
        if (value == null) {
            return null;
        }
        try {
            return NumericLiteralTypes.forDecimal(new BigDecimal(String.valueOf(value)));
        } catch (final NumberFormatException notANumber) {
            return null;
        }
    }

    /**
     * What RATIO_TO_REPORT declares, which is read off its ARGUMENT rather than fixed:
     *
     * <pre>
     *   RATIO_TO_REPORT(NUMBER(1,0))    NUMBER(7,6)
     *   RATIO_TO_REPORT(NUMBER(10,2))   NUMBER(18,8)
     *   RATIO_TO_REPORT(NUMBER(20,10))  NUMBER(32,12)   the scale stops at twelve
     *   RATIO_TO_REPORT(NUMBER(38,37))  NUMBER(38,37)   …unless the input is already past it
     *   RATIO_TO_REPORT(NUMBER(33,5))   NUMBER(38,11)   the precision stops at thirty-eight
     *   RATIO_TO_REPORT(FLOAT)          FLOAT
     *   RATIO_TO_REPORT(VARCHAR)        FLOAT           and a VARIANT the same
     *   RATIO_TO_REPORT(NULL)           NUMBER(24,6)    an untyped NULL counts as NUMBER(18,0)
     * </pre>
     *
     * <p>★ THE SCALE GROWS BY SIX AND STOPS AT TWELVE, but never NARROWS — an input scale already past
     * twelve is handed straight back. The precision then adds that WHOLE new scale to the input's own
     * digits, which is why NUMBER(10,2) gains eight and NUMBER(10,6) gains twelve.
     *
     * <p>★ IT TYPES WHATEVER ITS ARGUMENT IS, not just a column: over {@code a * 100} it is NUMBER(21,8)
     * and over {@code SUM(a)} NUMBER(30,8), each the rule applied to that expression's own declared
     * width. Live-verified across thirteen widths.
     *
     * @param args the call's arguments
     * @return the declared type, or null when there is no argument to read
     */
    private DataType ratioToReportType(final List<Expression> args) {
        if (args == null || args.isEmpty()) {
            return null;
        }
        final DataType argument = infer(args.get(0));
        NumericType input = argument instanceof NumericType ? (NumericType) argument : null;
        // An untyped NULL counts as the eighteen-digit integer an untyped numeric position always
        // gives it.
        if (input == null && isNullLiteral(args.get(0))) {
            input = new NumericType("NUMBER", 18, 0);
        }
        if (input == null) {
            // A KNOWN type that is not an exact number — a VARCHAR, a VARIANT — divides as a double
            // and answers FLOAT. An argument nothing could type stays undetermined, like every other
            // call over one.
            return argument != null ? NumericType.FLOAT : null;
        }
        if (NumericType.isApproximate(input)) {
            return input;
        }
        final int scale = Math.max(input.getScale(), Math.min(input.getScale() + 6, 12));
        return new NumericType("NUMBER", Math.min(38, input.getPrecision() + scale), scale);
    }

    /**
     * Whether a branch has no type at all: the word NULL itself, or a call that folds to one — see
     * {@link UntypedNullFold}, whose shapes every rule here reads as it reads the word.
     *
     * @param branch the branch expression
     * @return true for an untyped NULL
     */
    private boolean isNullLiteral(final Expression branch) {
        return UntypedNullFold.isUntypedNull(branch);
    }

    private boolean laterBranchIsBinary(final List<Expression> branches, final Expression from) {
        boolean seen = false;
        for (final Expression branch : branches) {
            if (branch == from) {
                seen = true;
                continue;
            }
            if (seen && infer(branch) instanceof BinaryType) {
                return true;
            }
        }
        return false;
    }

    /** Whether any branch at all is a binary. */
    private boolean containsBinaryBranch(final List<Expression> branches) {
        for (final Expression branch : branches) {
            if (infer(branch) instanceof BinaryType) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a branch meets the type the first branch expects. A PREDICATE is a BOOLEAN that meets only a
     * BOOLEAN: {@code NVL(g, 1 = 1)} over a text is refused, where {@code NVL(g, TRUE)} answers and
     * {@code NVL(1 = 1, g)} expects the BOOLEAN it leads with (live-verified).
     */
    private boolean branchFits(final DataType expected, final Expression branch, final DataType branchType) {
        if (PredicateExpressions.isPredicate(branch) && !(expected instanceof BooleanType)) {
            return false;
        }
        return branchesConvertible(expected, branchType);
    }

    /**
     * Whether one branch's type CONVERTS into the type the first branch set, which is the question live
     * answers before it refuses. Measured pair by pair, and the shape worth remembering is that a
     * conversion which can only fail at ROW time is still accepted at COMPILE time:
     *
     * <pre>
     *   VARIANT with anything but a BINARY   converts   COALESCE(va, i) runs, COALESCE(va, bn) does not
     *   VARCHAR with a number, temporal, boolean   converts — 'ab' fails later, not now
     *   NUMBER with a BOOLEAN                converts
     *   everything else across families      does NOT — number/temporal, boolean/temporal,
     *                                        BINARY with anything, OBJECT or ARRAY with a scalar
     * </pre>
     *
     * <p>A family this does not model is treated as convertible, so an unmeasured pairing keeps the
     * behaviour it had rather than gaining a refusal nobody measured.
     *
     * @param expected the first branch's type
     * @param offered  the later branch's type
     * @return true when live accepts the pairing at compile time
     */
    boolean branchesConvertible(final DataType expected, final DataType offered) {
        if (expected == null || offered == null) {
            return true;
        }
        if (expected instanceof DateTimeType && offered instanceof DateTimeType) {
            // Within the temporal family only ONE split matters: a TIME does not meet a DATE or a
            // TIMESTAMP, while the date-and-timestamp side widens freely among itself.
            return "TIME".equalsIgnoreCase(expected.getName())
                == "TIME".equalsIgnoreCase(offered.getName());
        }
        if (expected.getClass().equals(offered.getClass())) {
            return true;                       // one family always meets itself
        }
        if (expected instanceof VariantType || offered instanceof VariantType) {
            // A VARIANT absorbs every scalar, and a scalar reads back out of one; only a BINARY is
            // refused outright, and an interval, which converts to no VARIANT and out of none:
            // IFF(c, v, ts - ts2) names the interval into [VARIANT], COALESCE(ts - ts2, v) the VARIANT into
            // [INTERVAL DAY(9) TO SECOND(9)] (live-verified).
            return !(expected instanceof BinaryType) && !(offered instanceof BinaryType)
                && !IntervalCasts.isIntervalType(expected) && !IntervalCasts.isIntervalType(offered);
        }
        if (!knownRefusableFamily(expected) || !knownRefusableFamily(offered)) {
            return true;
        }
        if (expected instanceof BinaryType || offered instanceof BinaryType
                || expected instanceof ObjectType || offered instanceof ObjectType
                || expected instanceof ArrayType || offered instanceof ArrayType) {
            return false;
        }
        if (expected instanceof StringType || offered instanceof StringType) {
            return true;
        }
        return expected instanceof NumericType && offered instanceof BooleanType
            || expected instanceof BooleanType && offered instanceof NumericType;
    }

    /**
     * A conditional whose branches are ALL semi-structured takes the FIRST branch's type — not a
     * supertype of them, and not the VARCHAR placeholder it used to fall to. Live, measured both ways
     * round on both containers:
     *
     * <pre>
     *   CASE … THEN OBJECT_CONSTRUCT(…) ELSE va END   OBJECT     an OBJECT beside a VARIANT
     *   CASE … THEN va ELSE OBJECT_CONSTRUCT(…) END   VARIANT    the same pair, written the other way
     *   COALESCE(ar, va)                              ARRAY
     *   COALESCE(va, ar)                              VARIANT
     * </pre>
     *
     * <p>So the container does NOT widen to VARIANT when a VARIANT stands beside it: whichever branch
     * comes first decides, which is the same first-branch rule the refusal sentence names as the
     * "expected type". An OBJECT beside an ARRAY is refused rather than folded, and the refusal runs
     * before this, so the pair never reaches here.
     *
     * <p>This matters well beyond metadata: a CTAS takes its column types from here, so the placeholder
     * made {@code CREATE TABLE t AS SELECT CASE … END AS drivers} a VARCHAR column, and the next
     * statement to read that column saw a string where the query had written a container.
     *
     * <p>Distinct from {@link #commonSemiStructuredBranch}, which answers only when every branch is the
     * SAME container and is used to decide whether a value really has that type. This one spans the
     * three of them and picks by POSITION, which is a fold and not an agreement.
     *
     * @param branches the conditional's branches
     * @return the first branch's type when every branch is semi-structured, else null
     */
    private DataType semiStructuredBranchFold(final List<Expression> branches) {
        if (branches.isEmpty()) {
            return null;
        }
        for (final Expression branch : branches) {
            final DataType branchType = infer(branch);
            if (!(branchType instanceof ObjectType) && !(branchType instanceof ArrayType)
                    && !(branchType instanceof VariantType)) {
                return null;
            }
        }
        return infer(branches.get(0));
    }

    /**
     * A conditional with a VARIANT branch beside a SCALAR one, which the fold above does not reach —
     * it answers only when EVERY branch is semi-structured. Frostlake used to fall to the
     * VARCHAR(16777216) placeholder for every one of these; live has a specific answer per pairing,
     * measured over an EMPTY table so no row-time cast could mask the declared type:
     *
     * <pre>
     *   VARIANT beside NUMBER / FLOAT       VARIANT     — either way round
     *   VARIANT beside VARCHAR              VARCHAR     — either way round
     *   VARIANT beside DATE / TIME / TIMESTAMP  that temporal type, either way round
     *   VARIANT beside BOOLEAN / OBJECT / ARRAY whichever branch came FIRST
     * </pre>
     *
     * <p>THE RELATION IS NOT TRANSITIVE, which is why this cannot be a precedence order: VARCHAR beats
     * VARIANT, VARIANT beats NUMBER, and NUMBER beats VARCHAR. A three-branch conditional therefore
     * depends on the order, and the way live resolves it is a RIGHT-TO-LEFT pairwise fold — verified
     * against fifteen multi-branch spellings, every one of which it predicts:
     * {@code COALESCE(i, va, s)} is NUMBER because {@code (va, s)} folds to VARCHAR first and NUMBER
     * beats that, while {@code COALESCE(i, va, i)} is VARIANT because {@code (va, i)} folds to VARIANT.
     * A left-to-right fold gets the first of those wrong.
     *
     * <p>Only a conditional carrying a VARIANT takes this path; everything else keeps the fold it had,
     * which is measured-correct for 105 of the 121 ordered type pairs.
     *
     * @param branches the conditional's branches
     * @return the folded type, or null when no branch is a VARIANT or the pair has no answer
     */
    private DataType variantBranchFold(final List<Expression> branches) {
        boolean anyVariant = false;
        final List<DataType> types = new ArrayList<>(branches.size());
        for (final Expression branch : branches) {
            final DataType branchType = infer(branch);
            if (branchType == null) {
                return null;
            }
            anyVariant = anyVariant || branchType instanceof VariantType;
            types.add(branchType);
        }
        if (!anyVariant) {
            return null;
        }
        DataType folded = types.get(types.size() - 1);
        for (int i = types.size() - 2; i >= 0; i--) {
            folded = foldBranchPair(types.get(i), folded);
            if (folded == null) {
                return null;
            }
        }
        return folded;
    }

    /** One step of the right-to-left fold: the measured VARIANT table, or the ordinary fold. */
    private DataType foldBranchPair(final DataType left, final DataType right) {
        if (left instanceof VariantType && right instanceof VariantType) {
            return left;
        }
        if (left instanceof VariantType) {
            return variantBeside(right, true);
        }
        if (right instanceof VariantType) {
            return variantBeside(left, false);
        }
        return DeclaredTypeFold.foldBranches(Arrays.asList(left, right));
    }

    /**
     * What a VARIANT folds to beside one other type — see {@link #variantBranchFold} for the table.
     *
     * @param other        the branch that is not the VARIANT
     * @param variantFirst whether the VARIANT was written first, which decides the three pairings
     *                     that turn on position
     * @return the folded type, or null when the pair has no measured answer
     */
    private DataType variantBeside(final DataType other, final boolean variantFirst) {
        if (other instanceof NumericType) {
            return new VariantType();
        }
        if (other instanceof StringType) {
            // The string WIDENS rather than keeping its own length — the same UNKNOWN_LENGTH_VARCHAR a
            // string takes beside any family whose text it cannot measure. A CTAS then clamps that to
            // the 16MB a stored column may hold, which is why the two surfaces read different numbers
            // for one expression; keeping the branch's own VARCHAR(5) got BOTH of them wrong.
            return new StringType("VARCHAR", DeclaredTypeFold.UNKNOWN_LENGTH_VARCHAR);
        }
        if (other instanceof DateTimeType) {
            return other;
        }
        if (other instanceof BooleanType || other instanceof ObjectType
                || other instanceof ArrayType) {
            return variantFirst ? new VariantType() : other;
        }
        return null;
    }

    /**
     * Whether a type belongs to a family whose pairings were measured. Anything else — a structured
     * type, a geo, a FILE, a VECTOR — is left alone.
     *
     * @param type the branch's type
     * @return true when a refusal may be raised for it
     */
    private boolean knownRefusableFamily(final DataType type) {
        return type instanceof NumericType || type instanceof StringType
            || type instanceof DateTimeType || type instanceof BooleanType
            || type instanceof BinaryType
            || type instanceof ObjectType || type instanceof ArrayType;
    }

    /**
     * A TIME beside a TIMESTAMP, which is the one incompatible pair live words differently — and it
     * names the TIME first however the branches were written:
     *
     * <pre>
     *   COALESCE(tm, ts)   incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]
     *   COALESCE(ts, tm)   the same sentence, TIME still first
     * </pre>
     *
     * <p>A TIME beside a DATE is NOT this case: that one takes the ordinary conversion sentence, in the
     * order the branches were written.
     *
     * @param branches the conditional's branches
     */
    /**
     * Refuses a conditional whose branches cannot be brought together at all. Live decides this at
     * COMPILE time — over an empty table too — and words it two ways: a TIME beside a TIMESTAMP has its
     * own "incompatible types" sentence, everything else names the offending operand and the type the
     * FIRST branch expects.
     *
     * @param branches the conditional's branches
     */
    private void rejectIncompatibleBranches(final List<Expression> branches) {
        if (branches.size() < 2) {
            return;
        }
        rejectTimeBesideTimestamp(branches);
        final DataType expected = infer(branches.get(0));
        if (expected == null) {
            return;
        }
        for (final Expression branch : branches) {
            final DataType branchType = infer(branch);
            // An UNDETERMINED branch is never refused: it may hold anything, so refusing it is a guess.
            if (branchType != null && !branchFits(expected, branch, branchType)) {
                rejectUnconvertibleBranch(branches, branch, branchType);
            }
        }
    }

    private void rejectTimeBesideTimestamp(final List<Expression> branches) {
        DateTimeType time = null;
        DateTimeType timestamp = null;
        for (final Expression branch : branches) {
            final DataType branchType = infer(branch);
            if (!(branchType instanceof DateTimeType)) {
                continue;
            }
            final DateTimeType temporal = (DateTimeType) branchType;
            if ("TIME".equalsIgnoreCase(temporal.getName())) {
                time = temporal;
            } else if (temporal.getName().toUpperCase(Locale.ROOT).startsWith("TIMESTAMP")) {
                timestamp = temporal;
            }
        }
        if (time == null || timestamp == null) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.of("incompatible types: ["
            + SqlTypeNames.canonical(time) + "] and [" + SqlTypeNames.canonical(timestamp) + "]"));
    }

    private void rejectUnconvertibleBranch(final List<Expression> branches,
                                           final Expression stoppedAt, final DataType stoppedType) {
        final DataType expected = infer(branches.get(0));
        if (expected == null) {
            return;
        }
        // The OFFENDER is the first branch whose family differs from the FIRST branch's, which is not
        // always the branch the fold stopped on: in IFF(c, s, b) the fold stops on the string — branch
        // zero — and the operand live names is the BINARY that follows it.
        Expression offender = stoppedAt;
        DataType offenderType = stoppedType;
        for (final Expression branch : branches) {
            final DataType branchType = infer(branch);
            if (branchType != null && !branchFits(expected, branch, branchType)) {
                offender = branch;
                offenderType = branchType;
                break;
            }
        }
        if (offenderType == null) {
            return;
        }
        if (branchFits(expected, offender, offenderType)) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.of(
            "Can not convert parameter '" + parameterName(offender) + "' of type ["
                + SqlTypeNames.canonical(offenderType) + "] into expected type ["
                + SqlTypeNames.canonical(expected) + "]"));
    }

    /**
     * How live spells an operand in a conversion refusal: a column QUALIFIED by the FROM-clause key it
     * came from ({@code BM.S}, or the alias where one was written), a string literal as its own text
     * inside doubled quotes ({@code ''abc''}), and anything else as the plan re-prints it.
     */
    String parameterName(final Expression expr) {
        // A column is QUALIFIED by its FROM-clause key — the alias when one was written (R.G, A.N,
        // D.X for a derived table, C.X for a CTE), else the table name — which is how every strict
        // message spells a reference; a SELECT alias that names no relation prints bare.
        if (expr instanceof LiteralExpression
                && ((LiteralExpression) expr).getType() == LiteralType.STRING) {
            return "'" + ((LiteralExpression) expr).getValue() + "'";
        }
        // Anything else is named from the PLAN, not the source: live spells a call UPPER(EB.G) and an
        // operator EB.G || 'x', qualifying the columns inside and printing the outermost operator
        // bare, and it prints the plan's own spellings — CAST(RT.N AS VARCHAR(5)) for a ::, IFNULL for
        // COALESCE, GET for a colon path. The raw print left the columns unqualified.
        return visitor.strictPlanText(expr);
    }

    /**
     * What the ROUNDING family declares, which follows the ARGUMENT rather than being fixed. Two rules,
     * measured a width at a time over eleven numeric columns:
     *
     * <pre>
     *   CEIL / FLOOR / ROUND / TRUNC, target scale s (0 when none is written)
     *       s &gt;= the input's own scale   the input type UNCHANGED — nothing is being dropped
     *       otherwise                     NUMBER(min(38, p + 1 + max(0, -s - (p - si))), max(s, 0))
     *   ABS                               NUMBER(min(38, max(p, si + 2)), si)
     * </pre>
     *
     * <p>So the widening is a single digit, for the carry a round-up can produce — and only when the
     * result is actually losing decimals. {@code CEIL(<NUMBER(37,0)>)} stays NUMBER(37,0) rather than
     * growing to 38, which is what pins the rule: there is nothing to round.
     *
     * <p>A NEGATIVE scale grows it further, but not immediately: it costs a digit only once it reaches
     * PAST the integer digits the input already has, because until then the value cannot outgrow the
     * input's range. Over a NUMBER(10,2) — eight integer digits — a scale of -8 is still NUMBER(11,0)
     * and -9 is NUMBER(12,0).
     *
     * <p>ABS never widens for a carry, since it cannot produce one; the {@code si + 2} floor is the
     * minimum shape a NUMBER takes, which only shows on a column with no integer digits to spare —
     * ABS(NUMBER(1,1)) is NUMBER(3,1).
     *
     * <p>A scale that is NOT an integral numeric literal — a column, a constant expression, a string,
     * a fractional literal — cannot be read while compiling, and live falls back to a fixed eighteen
     * digits with the input's own scale: NUMBER(min(38, max(18, p + 1)), si). That is the same
     * literal-not-value rule BASE64_ENCODE's line length follows.
     *
     * <p>An APPROXIMATE input passes straight through as a FLOAT, scale argument or not.
     */
    /**
     * The number a VARCHAR reads as when the rounding family is given a SCALE. Not a guess: it is the
     * only (p, si) that reproduces all five measured widths through the rule above —
     *
     * <pre>
     *   ROUND(&lt;VARCHAR&gt;, 0)   NUMBER(19,0)      ROUND(&lt;VARCHAR&gt;, 2)   NUMBER(19,2)
     *   ROUND(&lt;VARCHAR&gt;, 1)   NUMBER(19,1)      ROUND(&lt;VARCHAR&gt;, -1)  NUMBER(19,0)
     *   ROUND(&lt;VARCHAR&gt;, 5)   NUMBER(18,5)
     * </pre>
     *
     * — the last of which is the one that pins it, because 5 is where the scale stops widening and the
     * "nothing is being dropped" branch returns the implied type ITSELF, printing its p and si outright.
     * The VARCHAR's own declared LENGTH does not enter: a VARCHAR(3) and a VARCHAR(10) both give
     * NUMBER(19,1) at scale 1.
     */
    private static final NumericType IMPLIED_VARCHAR_NUMERIC = new NumericType("NUMBER", 18, 5);

    /**
     * MOD, DIV0 and DIV0NULL declare exactly what the operator they stand for declares — MOD the type
     * of {@code a % b}, DIV0 and DIV0NULL the type of {@code a / b}: {@code SYSTEM$TYPEOF(MOD(n10_2, 7))}
     * is NUMBER(10,2) and {@code SYSTEM$TYPEOF(DIV0(n4_0, 2))} is NUMBER(10,6) on the account, the
     * operators' own answers, and a FLOAT operand makes either FLOAT (live-verified). An operand the
     * operator algebra cannot type leaves the registered nominal type in place.
     *
     * @param funcName the called function's name
     * @param args     its arguments
     * @return the operator's type, or null for another function or an untyped operand
     */
    private DataType divisionFamilyResultType(final String funcName, final List<Expression> args) {
        final boolean remainder = funcName.equals("MOD");
        if (!remainder && !funcName.equals("DIV0") && !funcName.equals("DIV0NULL")) {
            return null;
        }
        if (args.size() != 2) {
            return null;
        }
        final BinaryOperator operator = remainder ? BinaryOperator.MODULO : BinaryOperator.DIVIDE;
        DataType leftType = infer(args.get(0));
        DataType rightType = infer(args.get(1));
        // The bare NULL is the operator's NULL here too: MOD(NULL, 7) is NUMBER(2,0), DIV0(NULL, 2)
        // NUMBER(24,6), DIV0(n10_2, NULL) NUMBER(16,8) (live-verified).
        final boolean leftNull = leftType == null && isNullLiteral(args.get(0));
        final boolean rightNull = rightType == null && isNullLiteral(args.get(1));
        if (leftNull && (rightNull || acceptsNullBeside(rightType))) {
            leftType = arithmeticNullType(operator, rightType);
        }
        if (rightNull && (leftNull || acceptsNullBeside(leftType))) {
            rightType = arithmeticNullType(operator, leftType);
        }
        // The function forms read a text or a VARIANT argument as the operators do: MOD(t, 2) over a
        // VARCHAR column is NUMBER(18,5), DIV0(t, 2) NUMBER(24,11), MOD('5', 2) NUMBER(2,0) from the
        // literal's NUMBER(18,0), and MOD(v, 2) over a VARIANT is FLOAT (all live-verified).
        final DataType floated = textOrVariantArithmetic(args.get(0), leftType, args.get(1), rightType);
        if (floated != null) {
            return floated;
        }
        leftType = impliedNumeric(args.get(0), leftType, rightType, operator);
        rightType = impliedNumeric(args.get(1), rightType, leftType, operator);
        return BinaryOperationTypes.resultOf(operator, leftType, rightType);
    }

    /**
     * The FLOAT that a VARIANT operand, or a text beside a text, a FLOAT, a VARIANT or a bare NULL,
     * makes of an arithmetic pair — live declares {@code t + g}, {@code t + f}, {@code t + NULL},
     * {@code v + 0}, {@code v + n} and {@code v + t} FLOAT alike — or null when the pair is not one of
     * those (a text beside an exact number reads as a number instead, see {@link #impliedNumeric}).
     */
    private DataType textOrVariantArithmetic(final Expression left, final DataType leftType,
                                             final Expression right, final DataType rightType) {
        final boolean leftText = leftType instanceof StringType;
        final boolean rightText = rightType instanceof StringType;
        final boolean leftVariant = leftType instanceof VariantType;
        final boolean rightVariant = rightType instanceof VariantType;
        if (!leftText && !rightText && !leftVariant && !rightVariant) {
            return null;
        }
        if (leftVariant && floatsBesideVariant(right, rightType) || rightVariant && floatsBesideVariant(left, leftType)) {
            return NumericType.FLOAT;
        }
        if (leftText && rightText) {
            return NumericType.FLOAT;
        }
        if (leftText && (NumericType.isApproximate(rightType) || rightType == null && isNullLiteral(right))
                || rightText && (NumericType.isApproximate(leftType) || leftType == null && isNullLiteral(left))) {
            return NumericType.FLOAT;
        }
        return null;
    }

    private boolean floatsBesideVariant(final Expression other, final DataType otherType) {
        return otherType instanceof NumericType || otherType instanceof StringType
            || otherType instanceof VariantType || otherType == null && isNullLiteral(other);
    }

    /**
     * A text operand beside an EXACT number, read as the number live reads it: a column or an
     * expression as NUMBER(18,5); a literal as NUMBER(18, its own scale) under {@code + - * /} and
     * DIV0, and at its OWN width under the remainder — {@code t + 0} declares NUMBER(19,5),
     * {@code '5' + 1} NUMBER(19,0), {@code '2.5' + 1} NUMBER(19,1), {@code '5' / 2} NUMBER(24,6), but
     * {@code '5' % 2} and {@code MOD('5', 2)} NUMBER(2,0) and {@code MOD('12345', 2)} NUMBER(5,0), as
     * the digits themselves would (all live-verified). Every other operand is returned as it is.
     */
    private static DataType impliedNumeric(final Expression operand, final DataType type, final DataType other,
                                           final BinaryOperator operator) {
        if (!(type instanceof StringType) || !(other instanceof NumericType) || NumericType.isApproximate(other)) {
            return type;
        }
        if (ImpliedTextNumber.isTextLiteral(operand)) {
            final String written = String.valueOf(((LiteralExpression) operand).getValue());
            final NumericType literal = operator == BinaryOperator.MODULO
                ? ImpliedTextNumber.literalOwnWidth(written) : ImpliedTextNumber.literalReading(written);
            return literal != null ? literal : type;
        }
        return ImpliedTextNumber.COLUMN_READING;
    }

    private DataType roundingFamilyResultType(final String funcName, final List<Expression> args) {
        final boolean absolute = funcName.equals("ABS");
        if (!absolute && !funcName.equals("CEIL") && !funcName.equals("CEILING")
                && !funcName.equals("FLOOR") && !funcName.equals("ROUND")
                && !funcName.equals("TRUNC") && !funcName.equals("TRUNCATE")) {
            return null;
        }
        if (args.isEmpty()) {
            return null;
        }
        if (isNullLiteral(args.get(0))) {
            // The bare word NULL is planned as SYSTEM$NULL_TO_FIXED(null), eighteen digits the family keeps;
            // ABS over it declares two (live-verified).
            return absolute ? new NumericType("NUMBER", 2, 0) : new NumericType("NUMBER", 18, 0);
        }
        DataType input = infer(args.get(0));
        if (!(input instanceof NumericType)) {
            if (!(input instanceof StringType) && !(input instanceof VariantType)) {
                return null;
            }
            // A VARIANT is FLOAT whatever else is written; so is a VARCHAR with NO scale to hold. Only
            // a VARCHAR that is being given a scale reads as a number, and the number it reads as is
            // NUMBER(18,5) — see IMPLIED_VARCHAR_NUMERIC.
            if (input instanceof VariantType || args.size() < 2) {
                return NumericType.FLOAT;
            }
            input = IMPLIED_VARCHAR_NUMERIC;
        }
        if (NumericType.isApproximate(input)) {
            return input;
        }
        final int precision = ((NumericType) input).getPrecision();
        final int inputScale = ((NumericType) input).getScale();
        if (absolute) {
            return new NumericType("NUMBER", Math.min(38, Math.max(precision, inputScale + 2)),
                inputScale);
        }
        final Integer target = args.size() > 1 ? integralLiteralValue(args.get(1)) : Integer.valueOf(0);
        if (target == null) {
            return new NumericType("NUMBER", Math.min(38, Math.max(18, precision + 1)), inputScale);
        }
        final int scale = target.intValue();
        if (scale >= inputScale) {
            return input;
        }
        final int grown = precision + 1 + Math.max(0, -scale - (precision - inputScale));
        return new NumericType("NUMBER", Math.min(38, grown), Math.max(scale, 0));
    }

    /**
     * An expression's value when it is an INTEGRAL numeric literal, and null for everything else — a
     * fractional literal, a string, NULL, a cast, an arithmetic expression or a column. Snowflake
     * reads the literal rather than the value, so {@code -1} and {@code 0 - 1} are decided
     * differently; a written negation is still part of the literal.
     */
    static Integer integralLiteralValue(final Expression expr) {
        final Expression bare = expr instanceof UnaryOperationExpression
            && (((UnaryOperationExpression) expr).getOperator() == UnaryOperator.NEGATE
                || ((UnaryOperationExpression) expr).getOperator() == UnaryOperator.PLUS)
            ? ((UnaryOperationExpression) expr).getOperand() : expr;
        if (!(bare instanceof LiteralExpression)
                || !(((LiteralExpression) bare).getValue() instanceof Number)) {
            return null;
        }
        final BigDecimal written = new BigDecimal(((LiteralExpression) bare).getValue().toString());
        if (written.stripTrailingZeros().scale() > 0) {
            return null;
        }
        final boolean negated = bare != expr
            && ((UnaryOperationExpression) expr).getOperator() == UnaryOperator.NEGATE;
        final BigDecimal signed = negated ? written.negate() : written;
        return Integer.valueOf(signed.intValue());
    }

    /**
     * SIGN's declared type: {@code NUMBER(2,0)} over an exact argument, whatever its width, and FLOAT
     * over an APPROXIMATE one — the same passes-through rule the rounding family follows. Two digits
     * for a value that is only ever -1, 0 or 1: the sign takes a digit of its own.
     *
     * <p>A VARCHAR or a VARIANT argument is FLOAT as well, which is not the reading of "approximate"
     * one would guess: neither is approximate, but neither carries a width live can widen from either.
     */
    private DataType signResultType(final String funcName, final List<Expression> args) {
        if (!funcName.equals("SIGN") || args.isEmpty()) {
            return null;
        }
        final DataType input = infer(args.get(0));
        if (input instanceof StringType || input instanceof VariantType
                || NumericType.isApproximate(input)) {
            return NumericType.FLOAT;
        }
        return new NumericType("NUMBER", 2, 0);
    }

    /**
     * WIDTH_BUCKET's declared type, whose width follows the BUCKET COUNT rather than anything about
     * the value: {@code NUMBER(max(2, digits(count)), 0)}. Measured up a ladder — 1 through 99 buckets
     * all declare two digits, 100 through 999 three, 1000 through 9999 four — so the count's own digit
     * count decides it, under the two-digit floor a NUMBER always carries. The overflow bucket does
     * NOT buy a digit: 99 buckets can answer 100 and still declares two.
     *
     * <p>The count is read from any CONSTANT expression, which is a wider rule than the one
     * BASE64_ENCODE's line length and the rounding family's scale follow — {@code 500+500} declares
     * four digits there where {@code 0 - 1} is unreadable here. A string literal is read too, and a
     * fractional one rounds. Only a value the ROW decides falls back, and it falls back to the full
     * thirty-eight.
     */
    private DataType widthBucketResultType(final String funcName, final List<Expression> args) {
        if (!funcName.equals("WIDTH_BUCKET") || args.size() < 4) {
            return null;
        }
        final BigDecimal count = constantNumber(args.get(3));
        if (count == null) {
            final RelationReferenceWalk walk = new RelationReferenceWalk();
            args.get(3).accept(walk);
            return walk.found() ? new NumericType("NUMBER", 38, 0) : new NumericType("NUMBER", 2, 0);
        }
        final String digits = count.setScale(0, RoundingMode.HALF_UP).abs().toBigInteger().toString();
        return new NumericType("NUMBER", Math.max(2, digits.length()), 0);
    }

    /**
     * An expression's value when the STATEMENT can work it out — a literal, a string spelling a
     * number, or arithmetic over those — and null when only a row could. Nothing here reads the row,
     * so the walk is a fold rather than an evaluation.
     */
    private BigDecimal constantNumber(final Expression expr) {
        if (expr instanceof LiteralExpression) {
            final Object value = ((LiteralExpression) expr).getValue();
            if (value == null) {
                return null;
            }
            try {
                return new BigDecimal(value.toString());
            } catch (final NumberFormatException notANumber) {
                return null;
            }
        }
        if (expr instanceof UnaryOperationExpression
                && ((UnaryOperationExpression) expr).getOperator() == UnaryOperator.NEGATE) {
            final BigDecimal operand = constantNumber(((UnaryOperationExpression) expr).getOperand());
            return operand == null ? null : operand.negate();
        }
        if (expr instanceof UnaryOperationExpression
                && ((UnaryOperationExpression) expr).getOperator() == UnaryOperator.PLUS) {
            return constantNumber(((UnaryOperationExpression) expr).getOperand());
        }
        if (!(expr instanceof BinaryOperationExpression)) {
            return null;
        }
        final BinaryOperationExpression binary = (BinaryOperationExpression) expr;
        final BigDecimal left = constantNumber(binary.getLeft());
        final BigDecimal right = constantNumber(binary.getRight());
        if (left == null || right == null) {
            return null;
        }
        switch (binary.getOperator()) {
            case ADD: return left.add(right);
            case SUBTRACT: return left.subtract(right);
            case MULTIPLY: return left.multiply(right);
            default: return null;
        }
    }

    /**
     * The aggregates and window functions that hand their argument BACK — its family, width and
     * spelling alike, so {@code MAX(b)} over a BINARY(4) column is a BINARY(4) and over a
     * concatenation is the concatenation's own width. REVERSE belongs here too: it is the one
     * binary-taking string function that keeps the fixed spelling.
     */
    /** The window functions that number rows rather than return a value from one. */
    private static boolean isRowCounter(final String funcName) {
        // The two CONDITIONAL event windows belong here: each answers how many times something has
        // happened so far, which the account declares at the counter's width like the rankings.
        return funcName != null && (funcName.equals("ROW_NUMBER") || funcName.equals("RANK")
            || funcName.equals("DENSE_RANK") || funcName.equals("NTILE")
            || funcName.equals("CONDITIONAL_TRUE_EVENT") || funcName.equals("CONDITIONAL_CHANGE_EVENT"));
    }

    /**
     * The width DATE_PART and EXTRACT declare, which follows the PART they are asked for rather than
     * being fixed: 'year' is four digits and 'month' or 'second' are two, exactly as YEAR and MONTH
     * declare when called by name (live-verified). Only the parts measured through their own functions
     * are mapped; any other part keeps the ordinary width, because a part like 'epoch_second' has a
     * range nothing here has measured.
     *
     * @return the declared type, or null when the part is not a literal or is not one of the measured
     */
    private DataType datePartWidth(final List<Expression> args) {
        if (args.isEmpty() || !(args.get(0) instanceof LiteralExpression)) {
            return null;
        }
        final Object part = ((LiteralExpression) args.get(0)).getValue();
        if (part == null) {
            return null;
        }
        final String word = part.toString().toUpperCase(Locale.ROOT);
        if (word.equals("YEAR") || word.equals("Y") || word.equals("YY") || word.equals("YYYY")
                || word.equals("DAYOFYEAR") || word.equals("YEAROFWEEK")) {
            return IntegerResultWidths.YEAR_PART;
        }
        if (word.equals("MONTH") || word.equals("MM") || word.equals("DAY") || word.equals("DD")
                || word.equals("DAYOFMONTH") || word.equals("DAYOFWEEK") || word.equals("WEEK")
                || word.equals("QUARTER") || word.equals("HOUR") || word.equals("MINUTE")
                || word.equals("SECOND")) {
            return IntegerResultWidths.DATE_PART_SMALL;
        }
        return null;
    }


    /**
     * What an aggregate DECLARES, measured on the account and identical whether the query groups
     * explicitly or aggregates implicitly:
     *
     * <pre>
     *   SUM(NUMBER(p,s))      NUMBER(min(38, p+12), s)     SUM over NUMBER(10,2) is NUMBER(22,2)
     *   AVG(NUMBER(p,s))      NUMBER(min(38, p+12+g), s')  s' = max(s, min(s+6, 12)), g = s' - s:
     *                                                      NUMBER(10,2) is NUMBER(28,8), NUMBER(20,10)
     *                                                      NUMBER(34,12), NUMBER(38,37) NUMBER(38,37)
     *   SUM/AVG over FLOAT    FLOAT                        the float family does not widen
     *   MEDIAN(NUMBER(p,s))   NUMBER(min(38, p+3), s+3)    MEDIAN over NUMBER(10,2) is NUMBER(13,5),
     *                                                      and past s+3 = 38 it is REFUSED
     *   VARIANCE(NUMBER(p,s)) NUMBER(38, max(s, min(12, 2s+6)))  the SQUARE of a scale, capped at
     *                                                      twelve and never narrower than the input
     *   STDDEV and its kin    FLOAT                        the root of that square is inexact
     *   LISTAGG(x)            VARCHAR(134217728)           the 128MB conversion width
     *   MIN/MAX/ANY_VALUE/MODE                             the ARGUMENT's own type, handled above
     *   COUNT and its kin     NUMBER(18,0)                 handled above
     * </pre>
     *
     * <p>The averaging family is measured, not derived — the three widening steps are all different
     * and none of them follows from the arithmetic. AVG adds six decimals, MEDIAN three, and VARIANCE
     * doubles the scale before adding six and then stops at twelve however wide the input is: over
     * NUMBER(5,4) and NUMBER(30,5) alike it is NUMBER(38,12). The precision widens with the scale for
     * AVG and MEDIAN and is simply 38 for VARIANCE.
     *
     * <p>★ THE SCALE NEVER NARROWS, which a HIGH input scale is what shows: a growth that would take
     * the scale past its cap is not applied at all. AVG over NUMBER(38,37) is NUMBER(38,37) and
     * VARIANCE over it NUMBER(38,37), not (38,12); and AVG's precision is SUM's own twelve plus
     * however much the scale actually grew, which collapses to the familiar p+18 exactly when the
     * scale grows by the full six — NUMBER(20,10) → NUMBER(34,12) is the cell that tells them apart.
     * Live-verified across seventeen widths from NUMBER(5,3) to NUMBER(38,37).
     *
     * <p>★ MEDIAN AND PERCENTILE_CONT HAVE NO CAP, AND ARE REFUSED PAST IT: their intermediate is
     * NUMBER(p+3, s+3) uncapped, and once its scale passes 38 the call is a compile-time refusal
     * naming that intermediate — "Invalid intermediate datatype: NUMBER(41,40)." over NUMBER(38,37),
     * "NUMBER(39,39)." over NUMBER(36,36). At s+3 = 38 exactly the call is DECLARED NUMBER(38,38)
     * and the value is judged at row time instead.
     *
     * <p>The whole family hands a FLOAT argument straight back, and the STDDEV spellings answer FLOAT
     * whatever they were given — an exact input included, which is what separates them from VARIANCE.
     *
     * @return the declared type, or null when this call is not one of these aggregates
     */
    /**
     * The NUMBER a bare NULL stands for under an arithmetic operator: eighteen digits at the OTHER
     * operand's scale under + and - (n10_2 + NULL is NUMBER(19,2), n5_3 + NULL NUMBER(19,3)), at
     * scale 0 under * and / (NULL * n10_2 is NUMBER(28,2), NULL / n10_2 NUMBER(26,6)), and a single
     * digit under % (NULL % 7 is NUMBER(2,0)) — all live-verified.
     */
    private static DataType arithmeticNullType(final BinaryOperator operator, final DataType other) {
        if (operator == BinaryOperator.MODULO) {
            return new NumericType("NUMBER", 1, 0);
        }
        final boolean additive = operator == BinaryOperator.ADD || operator == BinaryOperator.SUBTRACT;
        final int scale = additive && other instanceof NumericType && !NumericType.isApproximate(other)
            ? Math.max(0, ((NumericType) other).getScale()) : 0;
        return new NumericType("NUMBER", 18, scale);
    }

    /** The five arithmetic operators, the remainder included. */
    private static boolean isNumericOperator(final BinaryOperator operator) {
        return operator == BinaryOperator.ADD || operator == BinaryOperator.SUBTRACT
            || operator == BinaryOperator.MULTIPLY || operator == BinaryOperator.DIVIDE
            || operator == BinaryOperator.MODULO;
    }

    private static String operatorSymbol(final BinaryOperator operator) {
        switch (operator) {
            case ADD: return "+";
            case SUBTRACT: return "-";
            case MULTIPLY: return "*";
            case DIVIDE: return "/";
            case MODULO: return "%";
            default: return operator.name();
        }
    }

    /** A bare NULL beside one of these is refused in arithmetic. */
    private static boolean refusesNullBeside(final DataType other) {
        return other instanceof DateTimeType || other instanceof BooleanType || other instanceof IntervalDayTimeType
            || other instanceof IntervalYearMonthType;
    }

    /** A bare NULL beside one of these takes the arithmetic NULL's NUMBER type. */
    private static boolean acceptsNullBeside(final DataType other) {
        return other instanceof NumericType || other instanceof StringType;
    }

    /** Whether a type is one of the TIMESTAMP flavours (the bare word included), not a DATE or TIME. */
    private static boolean isTimestampFlavour(final DataType type) {
        return type.getName() != null && type.getName().toUpperCase(Locale.ROOT).contains("TIMESTAMP");
    }

    /** Whether a function is a TO_TIMESTAMP-family conversion, the TRY spellings included. */
    private static boolean isTimestampConversion(final String funcName) {
        return funcName.startsWith("TO_TIMESTAMP") || funcName.startsWith("TRY_TO_TIMESTAMP");
    }

    /** The precision a numeric epoch declares: its scale plus the scale argument, within 0 to 9. */
    private static int epochPrecision(final NumericType source, final int scaleArgument) {
        return Math.min(9, Math.max(0, Math.max(0, source.getScale()) + scaleArgument));
    }

    /** The whole-number scale a TO_TIMESTAMP call writes as its second argument, or 0. */
    private static int literalScaleArgument(final List<Expression> args) {
        if (args.size() < 2 || !(args.get(1) instanceof LiteralExpression)
                || ((LiteralExpression) args.get(1)).getType() != LiteralType.INTEGER) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(((LiteralExpression) args.get(1)).getValue()));
    }

    /** The fractional-seconds precision, 0 to 9, that a clock call writes as its one argument, or null. */
    private static Integer literalPrecisionArgument(final List<Expression> args) {
        if (args.size() != 1 || !(args.get(0) instanceof LiteralExpression)
                || ((LiteralExpression) args.get(0)).getType() != LiteralType.INTEGER) {
            return null;
        }
        final String digits = String.valueOf(((LiteralExpression) args.get(0)).getValue());
        if (digits.length() != 1 || !Character.isDigit(digits.charAt(0))) {
            return null;
        }
        return digits.charAt(0) - '0';
    }

    private DataType aggregateResultType(final String funcName,
                                         final FunctionCallExpression call) {
        if (funcName.equals("COUNT_IF")) {
            // A count of matching rows is the SUM of a one-digit indicator, and declares its width:
            // NUMBER(13,0), the same width SUM(1) declares (live-verified).
            return new NumericType("NUMBER", 13, 0);
        }
        if (funcName.equals("APPROX_COUNT_DISTINCT") || funcName.equals("HLL")
                || funcName.equals("APPROXIMATE_COUNT_DISTINCT")) {
            // The approximate count declares the counter's eighteen digits whatever it counts — one
            // argument or a tuple of them (live: NUMBER(18,0)[SB8] over (n102, n380)).
            return IntegerResultWidths.COUNTER;
        }
        if (funcName.equals("BOOLOR_AGG") || funcName.equals("BOOLAND_AGG")
                || funcName.equals("BOOLXOR_AGG")) {
            // The three boolean aggregates declare BOOLEAN whatever they are given, as live does.
            return BooleanType.BOOLEAN;
        }
        if (isStandardDeviation(funcName)) {
            return new NumericType("FLOAT", 38, 9);
        }
        if (funcName.equals("LISTAGG")) {
            return new StringType("VARCHAR", 134217728);
        }
        // APPROX_PERCENTILE is approximate by construction and says so: FLOAT whatever it is given,
        // where the two exact percentiles below take their width from the column they order by.
        if (funcName.equals("APPROX_PERCENTILE")) {
            return new NumericType("FLOAT", 38, 9);
        }
        // The two ORDERED percentiles are typed from the WITHIN GROUP expression, never from the
        // fraction — PERCENTILE_CONT(0.5) and PERCENTILE_CONT(0.123456789) declare the same width. They
        // split on whether the answer is a value FROM the input or a value BETWEEN two of them:
        // PERCENTILE_DISC returns an actual row's value and hands its type straight back, while
        // PERCENTILE_CONT interpolates and widens exactly as MEDIAN does — which is no coincidence,
        // MEDIAN being PERCENTILE_CONT(0.5). Both live-verified across seven NUMBER widths and FLOAT.
        final boolean continuous = funcName.equals("PERCENTILE_CONT");
        if (continuous || funcName.equals("PERCENTILE_DISC")) {
            final Expression ordered = call.getWithinGroupOrdered();
            if (ordered == null) {
                return null;
            }
            final DataType orderedType = infer(ordered);
            if (orderedType instanceof StringType || orderedType instanceof VariantType
                    || orderedType instanceof IntervalDayTimeType || orderedType instanceof IntervalYearMonthType) {
                // A text or VARIANT key is converted value by value to NUMBER(9,0) — the MEDIAN rule
                // below — so PERCENTILE_DISC declares that width and PERCENTILE_CONT interpolates from it.
                return continuous
                    ? interpolatedWidth(COERCED_WHOLE_DIGITS, 0)
                    : new NumericType("NUMBER", COERCED_WHOLE_DIGITS, 0);
            }
            if (!(orderedType instanceof NumericType)) {
                return null;
            }
            final NumericType orderedNumeric = (NumericType) orderedType;
            if (!continuous || orderedNumeric.getName().equalsIgnoreCase("FLOAT")) {
                return orderedNumeric;
            }
            return interpolatedWidth(orderedNumeric.getPrecision(), orderedNumeric.getScale());
        }
        if (!isComputingAggregate(funcName) || call.getArguments().isEmpty()) {
            return null;
        }
        return computingAggregateType(funcName, infer(call.getArguments().get(0)));
    }

    /** Whether the aggregate computes its answer from its argument's values: SUM, AVG, MEDIAN or a variance. */
    private static boolean isComputingAggregate(final String funcName) {
        return funcName.equals("SUM") || funcName.equals("AVG") || funcName.equals("MEDIAN")
            || isVariance(funcName);
    }

    /**
     * What a computing aggregate (SUM, AVG, MEDIAN or a variance) declares over an argument of the given type,
     * or null for another aggregate or an argument type with no rule. A grouped SELECT arrives with its
     * argument's inferred type; a PIVOT, which aggregates a column rather than an expression, with the
     * column's declared type.
     *
     * @param funcName the aggregate's upper-case name
     * @param argument the argument's type
     * @return the declared type, or null
     */
    static DataType computingAggregateType(final String funcName, final DataType argument) {
        if (!isComputingAggregate(funcName)) {
            return null;
        }
        final boolean median = funcName.equals("MEDIAN");
        final boolean variance = isVariance(funcName);
        final boolean sum = funcName.equals("SUM");
        // A VARCHAR or VARIANT argument carries no declared scale for the result to be derived from,
        // so the computing aggregates land on the FLOAT tier whatever it holds — live-verified for SUM,
        // AVG, STDDEV, VARIANCE and their POP spellings over a VARCHAR of integers, of decimals and of
        // exponents alike, and over a VARIANT of numeric strings. MEDIAN is NOT in this: it converts
        // each value to a whole number — NUMBER(9,0), whatever length the text declares — and
        // interpolates from that, so it declares NUMBER(12,3) over any VARCHAR or VARIANT.
        if (argument instanceof StringType || argument instanceof VariantType) {
            return median ? interpolatedWidth(COERCED_WHOLE_DIGITS, 0) : new NumericType("FLOAT", 38, 9);
        }
        if (argument instanceof IntervalDayTimeType && (sum || funcName.equals("AVG"))) {
            // SUM and AVG add day-time intervals and answer the widest one, INTERVAL DAY(9) TO SECOND(9), whatever
            // digits the argument declares: SUM over an INTERVAL DAY(3) TO SECOND(3) prints nine fractional
            // digits (live-verified).
            return IntervalDayTimeType.DAY_TO_SECOND;
        }
        if (median && (argument instanceof IntervalDayTimeType || argument instanceof IntervalYearMonthType)) {
            // MEDIAN reads an interval as the whole number its cast gives, as it reads a text (live-verified).
            return interpolatedWidth(COERCED_WHOLE_DIGITS, 0);
        }
        if (!(argument instanceof NumericType)) {
            return null;
        }
        final NumericType numeric = (NumericType) argument;
        if (numeric.getName().equalsIgnoreCase("FLOAT")) {
            return numeric;
        }
        final int precision = numeric.getPrecision();
        final int scale = numeric.getScale();
        if (variance) {
            return new NumericType("NUMBER", 38, Math.max(scale, Math.min(12, 2 * scale + 6)));
        }
        if (median) {
            return interpolatedWidth(precision, scale);
        }
        return sum
            ? new NumericType("NUMBER", Math.min(38, precision + 12), scale)
            : averageWidth(precision, scale);
    }

    /**
     * What AVG declares over an exact input: the scale grows by six to a cap of twelve and never
     * narrows, and the precision is SUM's own widening plus however much the scale grew.
     *
     * @param precision the input's precision
     * @param scale     the input's scale
     * @return the declared type
     */
    private static NumericType averageWidth(final int precision, final int scale) {
        final int grown = Math.max(scale, Math.min(scale + 6, 12));
        return new NumericType("NUMBER", Math.min(38, precision + 12 + (grown - scale)), grown);
    }

    /**
     * What MEDIAN and PERCENTILE_CONT declare over an exact input, or the refusal live raises once
     * the uncapped intermediate NUMBER(p+3, s+3) can no longer be a type at all.
     *
     * @param precision the input's precision
     * @param scale     the input's scale
     * @return the declared type
     */
    private static NumericType interpolatedWidth(final int precision, final int scale) {
        if (scale + 3 > 38) {
            throw new RuntimeException(invalidIntermediate(precision + 3, scale + 3));
        }
        return new NumericType("NUMBER", Math.min(38, precision + 3), scale + 3);
    }

    /** Live's refusal of an intermediate type no NUMBER can be, spelled with its own parameters. */
    private static String invalidIntermediate(final int precision, final int scale) {
        return SqlCompilationError.withoutPosition(
            "Invalid intermediate datatype: NUMBER(" + precision + "," + scale + ").");
    }

    /**
     * What a WINDOW call declares. Its VALUE is opaque here — it arrives precomputed, keyed by source
     * text — but its type is a static property of the call, and every aggregate spelled over a window
     * declares what the same aggregate declares, so the two share one rule.
     *
     * <p>AVG is the exception, and the shape of the OVER clause is what decides it. Measured over
     * NUMBER(10,2), whose aggregate AVG is NUMBER(28,8):
     *
     * <pre>
     *   OVER ()                                          NUMBER(25,5)
     *   OVER (PARTITION BY x)                            NUMBER(25,5)
     *   OVER (ORDER BY y)                                NUMBER(28,8)   the aggregate's own width
     *   OVER (PARTITION BY x ORDER BY y)                 NUMBER(28,8)
     *   OVER (ORDER BY y ROWS BETWEEN … AND …)           NUMBER(25,5)   a ROWS frame puts it back
     *   OVER (ORDER BY y RANGE BETWEEN … AND …)          NUMBER(28,8)   a RANGE frame does NOT
     * </pre>
     *
     * <p>So a CUMULATIVE window — a bare ORDER BY, or one carrying a RANGE frame — widens AVG
     * the way the aggregate widens, and every other shape adds fifteen digits and three decimals
     * instead of eighteen and six. Confirmed on NUMBER(38,0) (38,3 against 38,6) and NUMBER(5,4)
     * (20,7 against 23,10), so it is the rule and not one number's coincidence. Nothing else forks:
     * SUM over a window is NUMBER(22,2) either way.
     *
     * @return the declared type, or null when nothing here can type this call
     */
    private DataType windowResultType(final WindowFunctionExpression window) {
        final String funcName = window.getFunctionName();
        if (funcName == null) {
            return null;
        }
        // The COUNTERS declare a width of their own — the same eighteen digits a COUNT or a LENGTH
        // declares, not the thirty-eight a NUMBER column carries.
        // COUNT joins them: it never passes an argument through, so it needs no argument to be typed —
        // which is as well, because COUNT(*) OVER () carries none to read.
        if (isRowCounter(funcName) || funcName.equals("COUNT")) {
            return IntegerResultWidths.COUNTER;
        }
        // The two FRACTION-returning rankings, which take no argument to be typed from and are
        // approximate — where RATIO_TO_REPORT, which does take one, is exact and is typed from it.
        if (funcName.equals("CUME_DIST") || funcName.equals("PERCENT_RANK")) {
            return NumericType.FLOAT;
        }
        if (funcName.equals("RATIO_TO_REPORT")) {
            return ratioToReportType(window.getArguments());
        }
        final List<Expression> args = window.getArguments();
        // An ORDERED aggregate is typed from its WITHIN GROUP key, exactly as the non-windowed spelling
        // is — the arguments carry only the fraction, which types nothing.
        if (window.getWithinGroupOrdered() != null) {
            final FunctionCallExpression reformed =
                new FunctionCallExpression(funcName, args == null ? new ArrayList<>() : args);
            reformed.describeWithinGroup(window.getWithinGroupOrdered());
            return aggregateResultType(funcName, reformed);
        }
        if (args == null || args.isEmpty()) {
            return null;
        }
        if ((funcName.equals("LAG") || funcName.equals("LEAD")) && args.size() >= 3) {
            // The DEFAULT is one more branch of a conditional: the call declares the fold of the
            // argument and the default, and both are converted to it (live-verified).
            return conditionalFoldType(funcName, Arrays.asList(args.get(0), args.get(2)));
        }
        if (passesItsArgumentThrough(funcName)) {
            return infer(args.get(0));
        }
        if (funcName.equals("AVG") && !(window.isOrdered() && !window.isRowsFramed())) {
            final DataType argument = infer(args.get(0));
            if (!(argument instanceof NumericType)) {
                return null;
            }
            final NumericType numeric = (NumericType) argument;
            if (numeric.getName().equalsIgnoreCase("FLOAT")) {
                return numeric;
            }
            // The whole-partition window has the same uncapped three-decimal intermediate the
            // percentiles have, refused in the same words once its scale passes 38 — over
            // NUMBER(36,36) the intermediate live names is NUMBER(41,39): the capped precision this
            // shape declares, plus three.
            final int declaredPrecision = Math.min(38, numeric.getPrecision() + 15);
            if (numeric.getScale() + 3 > 38) {
                throw new RuntimeException(
                    invalidIntermediate(declaredPrecision + 3, numeric.getScale() + 3));
            }
            return new NumericType("NUMBER", declaredPrecision, numeric.getScale() + 3);
        }
        return aggregateResultType(funcName, new FunctionCallExpression(funcName, args));
    }

    /** The three spellings that answer FLOAT for an exact input, unlike the variance they root. */
    private static boolean isStandardDeviation(final String funcName) {
        return funcName.equals("STDDEV") || funcName.equals("STDDEV_POP")
            || funcName.equals("STDDEV_SAMP");
    }

    /** Every spelling of the variance, which share one width. */
    private static boolean isVariance(final String funcName) {
        return funcName.equals("VARIANCE") || funcName.equals("VAR_POP")
            || funcName.equals("VAR_SAMP") || funcName.equals("VARIANCE_POP")
            || funcName.equals("VARIANCE_SAMP");
    }

    private static boolean passesItsArgumentThrough(final String funcName) {
        return funcName.equals("MAX") || funcName.equals("MIN") || funcName.equals("ANY_VALUE")
            || funcName.equals("FIRST_VALUE") || funcName.equals("LAST_VALUE")
            || funcName.equals("LAG") || funcName.equals("LEAD") || funcName.equals("NTH_VALUE")
            || funcName.equals("MODE") || funcName.equals("REVERSE")
            // NULLIFZERO answers its own argument or NULL, so the type travels untouched — live
            // declares it VARIANT over a VARIANT, VARCHAR(134217728) over a VARCHAR, BOOLEAN over a
            // BOOLEAN and NUMBER(10,2) over a NUMBER(10,2). Its twin ZEROIFNULL does NOT: it
            // substitutes a number, so it folds and converts. The two look symmetric and are not.
            || funcName.equals("NULLIFZERO");
    }

    /**
     * The width a string function returns when handed a BINARY. Measured: CONCAT sums its arguments
     * exactly as {@code ||} does, while SUBSTR and the trimming pair keep the INPUT's width — they do
     * NOT narrow to the length asked for, so {@code SUBSTR(b4, 1, 2)} is still four bytes wide. None of
     * them keeps the fixed spelling.
     *
     * @return the binary type, or null when this is not one of them over a binary argument
     */
    /**
     * CONCAT refuses a binary beside another family the way the OPERATOR spelling does — the same
     * sentence, the same argument list, positioned at the CALL rather than at an operator:
     *
     * <pre>
     *   CONCAT(b, s)   error line 1 at position 38 Invalid argument types for function 'CONCAT': (BINARY(4), VARCHAR(10))
     *   b || s         error line 1 at position 40 Invalid argument types for function '||': (BINARY(4), VARCHAR(10))
     * </pre>
     *
     * <p>Both measured pairs have two arguments, so the types named are the first two — a longer mixed
     * call is refused just as surely, but which pair live would name in the sentence is not measured.
     */
    private void rejectMixedConcat(final FunctionCallExpression call) {
        final List<Expression> args = call.getArguments();
        if (args.size() < 2) {
            return;
        }
        final DataType first = infer(args.get(0));
        final DataType second = infer(args.get(1));
        if (first == null || second == null) {
            return;
        }
        final String detail = "Invalid argument types for function 'CONCAT': ("
            + SqlTypeNames.canonical(first) + ", " + SqlTypeNames.canonical(second) + ")";
        final SourcePosition at = ExpressionSource.resolve(call.getPosition());
        throw new RuntimeException(at != null
            ? SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(), detail)
            : SqlCompilationError.of(detail));
    }

    private DataType binaryFromStringFunction(final String funcName,
                                              final FunctionCallExpression call) {
        final List<Expression> args = call.getArguments();
        if (args.isEmpty()) {
            return null;
        }
        if (funcName.equals("CONCAT")) {
            // Scanned over EVERY argument rather than led by the first: live refuses CONCAT(s, b) as
            // readily as CONCAT(b, s), so a binary anywhere in the list decides the call's fate.
            long total = 0;
            boolean unsizedOperand = false;
            boolean anyBinary = false;
            boolean anyOtherFamily = false;
            for (final Expression argument : args) {
                final DataType argumentType = infer(argument);
                if (argumentType instanceof BinaryType) {
                    anyBinary = true;
                    if (((BinaryType) argumentType).getWidthSpelling() == BinaryWidthSpelling.DECLARED) {
                        total += ((BinaryType) argumentType).getMaxLength();
                    } else {
                        unsizedOperand = true;
                    }
                } else if (argumentType != null) {
                    anyOtherFamily = true;
                }
            }
            if (!anyBinary) {
                return null;
            }
            if (anyOtherFamily) {
                rejectMixedConcat(call);
                return null;
            }
            return BinaryType.concatenation(total, unsizedOperand);
        }
        final DataType first = infer(args.get(0));
        if (!(first instanceof BinaryType)) {
            return null;
        }
        if (funcName.equals("SUBSTR") || funcName.equals("SUBSTRING")
                || funcName.equals("LEFT") || funcName.equals("RIGHT")) {
            return ((BinaryType) first).piece();
        }
        if (funcName.equals("INSERT") && args.size() == 4) {
            // Two binaries splice as a concatenation of the base's two SUBSTR pieces around the insertion,
            // so the widths add as a concatenation's do (live: INSERT(X'6162', 1, 1, X'63') is BINARY(5),
            // over two 8MB columns BINARY(25165824)), and past the 64MB maximum, or over an unsized base
            // or insertion, the splice is unsized — a NULL insertion included.
            final DataType insertion = infer(args.get(3));
            if (insertion instanceof BinaryType) {
                final BinaryType base = (BinaryType) first;
                final BinaryType inserted = (BinaryType) insertion;
                return BinaryType.concatenation(2L * base.getMaxLength() + inserted.getMaxLength(),
                    base.getWidthSpelling() != BinaryWidthSpelling.DECLARED
                        || inserted.getWidthSpelling() != BinaryWidthSpelling.DECLARED);
            }
            return insertion == null && isNullLiteral(args.get(3)) ? BinaryType.UNSIZED : null;
        }
        return null;
    }

    /**
     * The functions whose binary result is as wide as their ARGUMENT rather than a fixed size. The
     * ratio is per function and live-measured — a hex string decodes to half its characters, base64 to
     * three quarters, and STRING_AS_BINARY to the string's four-byte-per-character budget:
     *
     * <pre>
     *   HEX_DECODE_BINARY(VARCHAR(100))     BINARY(50)
     *   BASE64_DECODE_BINARY(VARCHAR(100))  BINARY(75)
     *   STRING_AS_BINARY(VARCHAR(100))      BINARY(400)
     * </pre>
     *
     * <p>The HASHES are not here: their width is their digest's, declared on the function itself. Nor
     * are TO_BINARY and the crypto family, which are unsized whatever they are given (see
     * BinaryWidthSpelling).
     */
    private static boolean widthFollowsArgument(final String funcName) {
        return funcName.equals("HEX_DECODE_BINARY") || funcName.equals("TRY_HEX_DECODE_BINARY")
            || funcName.equals("BASE64_DECODE_BINARY") || funcName.equals("TRY_BASE64_DECODE_BINARY")
            || funcName.equals("STRING_AS_BINARY");
    }

    /**
     * The width such a function's result declares, or null when the argument's own width is unknown, in
     * which case the function's declared type stands: over a VARIANT live reads HEX_DECODE_BINARY at
     * the 64MB maximum and BASE64_DECODE_BINARY unsized.
     */
    private DataType argumentDerivedBinary(final String funcName, final List<Expression> args) {
        if (args.isEmpty()) {
            return null;
        }
        final DataType argument = infer(args.get(0));
        if (!(argument instanceof StringType)) {
            return null;
        }
        final long characters = ((StringType) argument).getMaxLength();
        if (funcName.equals("STRING_AS_BINARY")) {
            return new BinaryType("VARBINARY", (int) Math.min(characters * 4, MAX_BINARY_LENGTH));
        }
        // A decoder is sized in whole groups — two hex digits or four base64 characters per group, so
        // BASE64_DECODE_BINARY over a VARCHAR(7) is BINARY(3) and over a VARCHAR(9) BINARY(6) — and is
        // not held to a column's 8MB: over a bare VARCHAR it is BINARY(12582912) (live-verified).
        final long bytes = funcName.startsWith("HEX_") || funcName.startsWith("TRY_HEX_")
            ? characters / 2 : characters / 4 * 3;
        return new BinaryType("VARBINARY", (int) Math.min(bytes, BinaryType.NOMINAL_MAXIMUM));
    }

    /** The functions whose result is a VECTOR whose element type / dimension come from the arguments. */
    private static boolean producesVector(final String funcName) {
        return funcName.equals("VECTOR_NORMALIZE") || funcName.equals("VECTOR_AVG")
            || funcName.equals("VECTOR_TRUNC") || funcName.equals("VECTOR_TRUNCATE")
            || funcName.equals("VECTOR_SUM") || funcName.equals("VECTOR_MIN")
            || funcName.equals("VECTOR_MAX");
    }

    /**
     * Whether {@code expr} is a CONSTANT (a literal, possibly under casts): Snowflake coerces
     * constant strings in temporal argument positions while rejecting non-constant VARCHAR
     * expressions, so the temporal strictness check must tell the two apart.
     */
    boolean isConstantExpression(final Expression expr) {
        // Bare literals only: Snowflake coerces a constant string in a temporal position but
        // REJECTS the same value under an explicit ::VARCHAR cast (live-verified).
        return expr instanceof LiteralExpression;
    }

    /**
     * A cast-target base name as a DataType category carrier, or null for unknown names. Package-private
     * so the strict-argument rules can ask what CATEGORY a written cast target is in — "is this a text
     * target?" — instead of keeping a second copy of the spellings beside them.
     */
    /**
     * A written {@code NUMBER(p, s)} / {@code DECIMAL(p, s)} / {@code NUMERIC(p, s)} cast target as the
     * parameterised type, or null for every other target. {@link #typeForName} deliberately drops the
     * parameters — it answers "which CATEGORY is this?" for the strict-argument rules — but a cast is
     * where an expression genuinely takes the declared scale on, and losing it here costs data: live
     * {@code 1.5::NUMBER(5,2)} is NUMBER(5,2) holding 1.50, while a scale-0 answer stores 2.
     */
    private static NumericType numberTargetWithParameters(final String targetType) {
        if (targetType == null) {
            return null;
        }
        final int open = targetType.indexOf('(');
        final int close = targetType.lastIndexOf(')');
        if (open <= 0 || close < open) {
            return null;
        }
        final String base = targetType.substring(0, open).trim().toUpperCase();
        if (!base.equals("NUMBER") && !base.equals("DECIMAL") && !base.equals("NUMERIC")
                && !base.equals("DEC")) {
            return null;
        }
        final String[] parameters = targetType.substring(open + 1, close).split(",");
        try {
            final int precision = Integer.parseInt(parameters[0].trim());
            final int scale = parameters.length > 1 ? Integer.parseInt(parameters[1].trim()) : 0;
            return new NumericType("NUMBER", precision, scale);
        } catch (final NumberFormatException notParameters) {
            return null;
        }
    }

    /**
     * The FIRST parameter of a cast target — the {@code 10} of {@code VARCHAR(10)} — or -1 when the
     * target names none. A cast declares a real type, not just a family: {@code CAST(s AS VARCHAR(10))}
     * is a VARCHAR(10) live, and dropping the parameter widened every such cast to the family maximum.
     *
     * @param targetType the target type text as written
     * @return the first parameter, or -1 when there is none or it does not parse
     */
    private static int firstParameter(final String targetType) {
        final int open = targetType.indexOf('(');
        final int close = targetType.lastIndexOf(')');
        if (open < 0 || close < open) {
            return -1;
        }
        final String first = targetType.substring(open + 1, close).split(",")[0].trim();
        try {
            final int value = Integer.parseInt(first);
            return value >= 0 ? value : -1;
        } catch (final NumberFormatException notAParameter) {
            return -1;
        }
    }

    /**
     * REDUCE's type: its accumulator, which live types from the initial value and the lambda's body. The
     * body is typed with the accumulator taking the initial value's type and the element the array's element
     * type, VARIANT for an untyped array, unless the lambda declares either. Live declares:
     *
     * <pre>
     *   REDUCE([1, 2, 3], 0, (acc, x) -&gt; acc + x)               FLOAT          VARIANT arithmetic
     *   REDUCE([1, 2, 3]::ARRAY(INT), 0, (acc, x) -&gt; acc + x)   NUMBER(38,0)   an exact number widens to 38
     *   REDUCE([1, 2], 0.5, (acc, x) -&gt; acc + 1)                NUMBER(38,1)   and keeps its scale
     *   REDUCE([1, 2], 0, (acc, x) -&gt; acc || x)                 NUMBER(18,5)   a text body joins the number
     *   REDUCE(['a', 'b'], '', (acc, x) -&gt; acc || x)            VARCHAR(134217728)
     *   REDUCE([1, 2], 'ab', (acc, x) -&gt; acc)                   VARCHAR(2)     a text keeps its width, at least 1
     *   REDUCE([1, 2], 0, (acc, x) -&gt; x)                        VARIANT
     *   REDUCE([1, 2], NULL, (acc, x) -&gt; x)                     VARIANT        a NULL start takes the body's
     *   REDUCE([1, 2], 0, (acc, x) -&gt; acc &gt; x)                  incompatible types: [BOOLEAN] and [NUMBER(1,0)]
     *   REDUCE([1, 2], 1.5, (acc, x) -&gt; NULL)                   NUMBER(2,1)    a NULL body keeps the start's own
     *   REDUCE([1, 2], NULL, (acc, x) -&gt; NULL)                  VARIANT
     * </pre>
     */
    private DataType reduceType(final List<Expression> args) {
        final LambdaExpression lambda = (LambdaExpression) args.get(2);
        final DataType initial = infer(args.get(1));
        if (UntypedNullFold.isUntypedNull(lambda.getBody())) {
            return initial != null ? initial : VariantType.VARIANT;
        }
        final DataType array = infer(args.get(0));
        final DataType element = array instanceof ArrayType && ((ArrayType) array).getElementType() != null
            ? ((ArrayType) array).getElementType() : VariantType.VARIANT;
        final Map<String, DataType> scope = new HashMap<>();
        final List<String> parameters = lambda.getParameters();
        final List<String> declared = lambda.getParameterTypes();
        for (int i = 0; i < parameters.size() && i < 2; i++) {
            final String written = declared != null && i < declared.size() ? declared.get(i) : null;
            scope.put(parameters.get(i).toUpperCase(),
                written != null ? typeForName(written) : i == 0 ? initial : element);
        }
        lambdaTypes.push(scope);
        final DataType body;
        try {
            body = infer(lambda.getBody());
        } finally {
            lambdaTypes.pop();
        }
        return accumulatorType(initial, body);
    }

    /** The accumulator's type from the initial value's and the body's, by the rules {@link #reduceType} lists. */
    private static DataType accumulatorType(final DataType initial, final DataType body) {
        if (body == null) {
            return null;
        }
        if (initial == null || body instanceof VariantType) {
            return body;
        }
        if (initial instanceof NumericType && body instanceof NumericType) {
            if (NumericType.isApproximate(initial) || NumericType.isApproximate(body)) {
                return NumericType.FLOAT;
            }
            return new NumericType("NUMBER", 38,
                Math.max(((NumericType) initial).getScale(), ((NumericType) body).getScale()));
        }
        if (initial instanceof NumericType && body instanceof StringType) {
            final DataType joined = DeclaredTypeFold.stringContribution(initial);
            return joined == null ? null : DeclaredTypeFold.combine(initial, joined);
        }
        if (initial instanceof StringType && body instanceof StringType) {
            return new StringType("VARCHAR", Math.max(1,
                Math.max(((StringType) initial).getMaxLength(), ((StringType) body).getMaxLength())));
        }
        if (initial instanceof NumericType && body instanceof BooleanType) {
            throw new RuntimeException(SqlCompilationError.of("incompatible types: ["
                + SqlTypeNames.refusalSpelling(body) + "] and [" + SqlTypeNames.refusalSpelling(initial) + "]"));
        }
        return DeclaredTypeFold.sameDeclaredType(initial, body) ? initial : null;
    }

    static DataType typeForName(final String targetType) {
        final int paren = targetType.indexOf('(');
        final String base = (paren > 0 ? targetType.substring(0, paren) : targetType)
            .trim().toUpperCase().replace(" ", "");
        final int parameter = firstParameter(targetType);
        switch (base) {
            case "CHAR": case "CHARACTER": case "NCHAR":
                // An unparameterised CHAR is one character wide: NULL::CHAR and 'ab'::NCHAR are VARCHAR(1)
                // in SYSTEM$TYPEOF, and a NULL beside one widens it as it widens any sized text (live-verified).
                return new StringType("VARCHAR", parameter < 0 ? 1 : parameter);
            case "VARCHAR": case "STRING": case "TEXT":
            case "NVARCHAR":
            // The VARYING spellings the grammar allows — `CHAR VARYING`, `CHARACTER VARYING`,
            // `NCHAR VARYING` — reach here with their whitespace already removed.
            case "CHARVARYING": case "CHARACTERVARYING": case "NCHARVARYING":
                // An UNPARAMETERISED string target has no width of its own: it counts as the 128MB
                // conversion width, not the 16MB a declared column defaults to, and SYSTEM$TYPEOF spells it
                // bare — CAST(i AS VARCHAR), i::STRING and NULL::VARCHAR are VARCHAR (live-verified).
                return parameter < 0 ? WidthlessStringType.WIDTHLESS
                    : new StringType("VARCHAR", parameter);
            case "NUMBER": case "DECIMAL": case "NUMERIC": case "INT": case "INTEGER":
            case "BIGINT": case "SMALLINT": case "TINYINT": case "BYTEINT":
                return NumericType.NUMBER;
            // Floating point is its own kind, not a NUMBER spelling: the scalar-UDF return-type
            // check refuses NUMBER<->FLOAT in both directions, so a cast target must keep the
            // distinction. Consumers that reason in coarser families still see a NumericType.
            case "FLOAT": case "FLOAT4": case "FLOAT8": case "REAL":
                return NumericType.FLOAT;
            case "DOUBLE": case "DOUBLEPRECISION": case "DECFLOAT":
                return NumericType.DOUBLE;
            case "BOOLEAN":
                return BooleanType.BOOLEAN;
            case "BINARY":
                // A cast to a bare BINARY sizes nothing: SYSTEM$TYPEOF(X'00'::BINARY) is bare BINARY live,
                // where X'00'::BINARY(3) is BINARY(3).
                return parameter < 0 ? BinaryType.unsized(true) : new BinaryType("BINARY", parameter);
            case "VARBINARY":
                // The two spellings cast identically and differ in one metadata cell: a cast to BINARY
                // reads fixed, a cast to VARBINARY does not (live-verified).
                return parameter < 0 ? BinaryType.unsized(false) : new BinaryType("VARBINARY", parameter);
            case "DATE":
                return DateTimeType.DATE;
            case "TIME":
                // The parameter of a temporal target is its fractional-second PRECISION, reported as the
                // descriptor's scale. Unparameterised it is 9, the family default.
                return parameter < 0 ? DateTimeType.TIME : new DateTimeType("TIME", parameter, false);
            // The timezone flavors stay distinct: the scalar-UDF return-type check refuses
            // NTZ<->LTZ, so a cast to a flavored timestamp must carry its flavor.
            case "TIMESTAMP":
                // The BARE word resolves against the session's TIMESTAMP_TYPE_MAPPING. DATETIME below
                // does NOT — it is an alias for TIMESTAMP_NTZ specifically, measured under every mapping.
                if (SessionTimestampMapping.isZoned()) {
                    final String mapped = SessionTimestampMapping.current();
                    return new DateTimeType(mapped, parameter < 0 ? 9 : parameter, true);
                }
                return parameter < 0 ? DateTimeType.TIMESTAMP_NTZ
                    : new DateTimeType("TIMESTAMP_NTZ", parameter, false);
            case "DATETIME": case "TIMESTAMP_NTZ": case "TIMESTAMPNTZ":
                return parameter < 0 ? DateTimeType.TIMESTAMP_NTZ
                    : new DateTimeType("TIMESTAMP_NTZ", parameter, false);
            case "TIMESTAMP_LTZ": case "TIMESTAMPLTZ":
                return parameter < 0 ? DateTimeType.TIMESTAMP_LTZ
                    : new DateTimeType("TIMESTAMP_LTZ", parameter, true);
            case "TIMESTAMP_TZ": case "TIMESTAMPTZ":
                return parameter < 0 ? DateTimeType.TIMESTAMP_TZ
                    : new DateTimeType("TIMESTAMP_TZ", parameter, true);
            case "VARIANT":
                return VariantType.VARIANT;
            case "OBJECT":
                return ObjectType.OBJECT;
            case "ARRAY":
                return ArrayType.ARRAY;
            case "UUID":
                return UuidType.UUID;
            default:
                return null;
        }
    }
}
