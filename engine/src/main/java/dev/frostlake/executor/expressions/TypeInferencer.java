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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.DeclaredTypeFold;
import dev.frostlake.types.FileType;
import dev.frostlake.types.GeoTypes;
import dev.frostlake.types.IntegerResultWidths;
import dev.frostlake.types.MapType;
import dev.frostlake.types.NumericLiteralTypes;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringResultWidths;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorElementType;
import dev.frostlake.types.VectorType;
import dev.frostlake.values.BinaryValue;

import java.util.ArrayList;
import java.util.Arrays;
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
        final SourcePosition at = ExpressionSource.resolve(binary.getPosition());
        return at != null
            ? SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(), detail)
            : SqlCompilationError.of(detail);
    }

    private final ExpressionEvaluatorVisitor visitor;

    TypeInferencer(final ExpressionEvaluatorVisitor visitor) {
        this.visitor = visitor;
    }

    // Static types are row-invariant, but infer() was re-walking the argument AST per ROW for
    // every strictness check (temporal-argument, variant-coercion, colon-path, cast paths).
    // Memoized by node IDENTITY (the cached expression AST is shared across rows); cleared when
    // the visitor's multi-table context changes — the single-table context is fixed at
    // construction. UNDETERMINED caches the null verdict so misses stop re-walking too.
    private final Map<Expression, Object> memo = new IdentityHashMap<Expression, Object>();
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
        if (!(binary.getRight() instanceof IntervalExpression)) {
            return null;
        }
        final DataType left = infer(binary.getLeft());
        if (left instanceof DateTimeType && !"DATE".equals(((DateTimeType) left).getName())) {
            return left;                       // a timestamp keeps its own flavour
        }
        if (!(left instanceof DateTimeType)) {
            return null;
        }
        return wholeDayUnit((IntervalExpression) binary.getRight())
            ? left : DateTimeType.TIMESTAMP_NTZ;
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
        if (expr instanceof LiteralExpression) {
            switch (((LiteralExpression) expr).getType()) {
                case STRING:
                    // The literal's OWN length, like the numeric branch below: live spells the
                    // expression VARCHAR(3) for 'abc' in type-matching refusals and derives CTAS
                    // column lengths the same way.
                    final Object text = ((LiteralExpression) expr).getValue();
                    return text instanceof String
                        ? new StringType("VARCHAR", ((String) text).length())
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
                        ? new BinaryType("VARBINARY", ((BinaryValue) bytes).bytes().length)
                        : BinaryType.VARBINARY;
                default:
                    return null;   // NULL literal: untyped
            }
        }
        if (expr instanceof ColumnReferenceExpression) {
            final TableColumn resolved = visitor.resolveDeclaredColumn((ColumnReferenceExpression) expr);
            return resolved != null ? resolved.getDataType() : null;
        }
        if (expr instanceof UnaryOperationExpression) {
            // A negated numeric keeps its operand's own NUMBER — the sign adds no digit (live: -208 ∪
            // 1.25 declares NUMBER(5,2), so -208 counts as (3,0), and a view over -a declares exactly
            // a's type). It holds for a column reference and a computed operand, not just a literal.
            final UnaryOperationExpression unary = (UnaryOperationExpression) expr;
            if (unary.getOperator() == UnaryOperator.NEGATE) {
                final DataType negated = infer(unary.getOperand());
                return negated instanceof NumericType ? negated : null;
            }
            return null;
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
            final DataType leftType = infer(binary.getLeft());
            final DataType rightType = infer(binary.getRight());
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
            if (cast.getDeclaredTarget() != null) {
                return cast.getDeclaredTarget();
            }
            final NumericType declaredNumber = numberTargetWithParameters(cast.getTargetType());
            return declaredNumber != null ? declaredNumber : typeForName(cast.getTargetType());
        }
        if (expr instanceof WindowFunctionExpression) {
            // A window call is otherwise opaque here — its value arrives precomputed, keyed by source
            // text — but the ones that hand their argument back still declare that argument's type.
            final WindowFunctionExpression window = (WindowFunctionExpression) expr;
            if (window.getFunctionName() != null && window.getArguments() != null
                    && !window.getArguments().isEmpty()
                    && passesItsArgumentThrough(window.getFunctionName())) {
                final DataType passed = infer(window.getArguments().get(0));
                if (passed instanceof BinaryType) {
                    return passed;
                }
            }
            // The COUNTERS declare a width of their own — the same eighteen digits a COUNT or a LENGTH
            // declares, not the thirty-eight a NUMBER column carries.
            if (isRowCounter(window.getFunctionName())) {
                return IntegerResultWidths.COUNTER;
            }
            return null;
        }
        if (expr instanceof CaseExpression) {
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
            return foldedBranchType(branches);
        }
        if (expr instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) expr;
            if (call.getNameExpression() != null) {
                return null;   // IDENTIFIER(expr): the target function is dynamic
            }
            // A name that resolves to nothing is refused HERE, at compile time. Raising it from the
            // evaluator instead made the refusal data-dependent: over a table with no rows nothing
            // was evaluated, so the statement was accepted — and a view or CTAS over it was created.
            visitor.requireResolvableFunctionName(call);
            final String funcName = call.getFunctionName().toUpperCase();
            if (CONDITIONAL_FUNCTIONS.contains(funcName)) {
                final List<Expression> branches = conditionalBranches(funcName, call.getArguments());
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
                return funcName.equals("NULLIF") ? nullIfResultType(branches)
                    : foldedBranchType(branches);
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
            final DataType temporal = temporalFunctionResultType(funcName, call.getArguments());
            if (temporal != null) {
                return temporal;
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
            // SCALAR functions only. The registry keeps aggregates in their own map, and their declared
            // types are NOMINAL where live's are pass-through — live, a derived
            // {@code MAX(n)} over a NUMBER(10,2) column reports NUMBER(10,2), not the VARIANT the
            // registry names — so reading them here would hand every rule a type live disagrees with.
            // The one thing that IS safe to read off an aggregate is its semi-structured family; that
            // narrow lookup lives in {@link #aggregateReturnType}.
            final BuiltInFunction fn = visitor.getFunctionRegistry().getFunction(funcName);
            if (fn == null) {
                return null;
            }
            final DataType declared = fn.getReturnType();
            if (declared instanceof StringType) {
                // The registry declares one nominal VARCHAR for every string function; live computes a
                // width from the arguments. See StringResultWidths for the measured table.
                final DataType width = StringResultWidths.forFunction(funcName,
                    argumentTypes(call.getArguments()));
                if (width != null) {
                    return width;
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
    private static List<Expression> conditionalBranches(final String funcName, final List<Expression> args) {
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
            final FunctionCallExpression call = (FunctionCallExpression) expr;
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
        return declared instanceof ObjectType || declared instanceof ArrayType ? declared : null;
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

    /** A BINARY's widest declared width, the ceiling every derived binary saturates at. */
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
        for (final Expression branch : branches) {
            final DataType branchType = infer(branch);
            if (branchType instanceof BinaryType) {
                anyBinary = true;
                width = Math.max(width, ((BinaryType) branchType).getMaxLength());
                allFixed &= ((BinaryType) branchType).isFixed();
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
        return anyBinary ? new BinaryType(allFixed ? "BINARY" : "VARBINARY", width) : null;
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
    private DataType foldedBranchType(final List<Expression> branches) {
        final List<DataType> branchTypes = new ArrayList<>(branches.size());
        boolean sawNullBranch = false;
        for (final Expression branch : branches) {
            if (isNullLiteral(branch)) {
                sawNullBranch = true;
                continue;
            }
            branchTypes.add(infer(branch));
        }
        if (branchTypes.isEmpty()) {
            return sawNullBranch ? new StringType("VARCHAR", 0) : null;
        }
        final DataType folded = DeclaredTypeFold.foldBranches(branchTypes);
        if (sawNullBranch && folded instanceof StringType) {
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
            return new StringType("VARCHAR", 0);
        }
        final DataType first = infer(branches.get(0));
        if (first == null) {
            return null;
        }
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
    private DataType temporalFunctionResultType(final String funcName, final List<Expression> args) {
        if (funcName.equals("LAST_DAY") || funcName.equals("NEXT_DAY")) {
            return DateTimeType.DATE;
        }
        if (funcName.equals("DATEDIFF") || funcName.equals("TIMEDIFF")
                || funcName.equals("TIMESTAMPDIFF")) {
            return args.isEmpty() ? null : dateDifferenceWidth(unitText(args.get(0)));
        }
        if (funcName.equals("DATEADD") || funcName.equals("TIMEADD")
                || funcName.equals("TIMESTAMPADD")) {
            return args.size() < 3 ? null : shiftedTemporal(infer(args.get(2)), unitText(args.get(0)));
        }
        if (funcName.equals("DATE_TRUNC") && args.size() >= 2) {
            final DataType subject = infer(args.get(1));
            return subject instanceof DateTimeType ? subject : null;
        }
        if (funcName.equals("ADD_MONTHS") || funcName.equals("TRUNC")) {
            final DataType subject = args.isEmpty() ? null : infer(args.get(0));
            return subject instanceof DateTimeType ? subject : null;
        }
        return null;
    }

    /**
     * How wide a DATEDIFF is, which the UNIT decides rather than the arguments: the same two instants
     * are nine digits apart counted in hours, eighteen in minutes and thirty-eight in nanoseconds.
     * Live-measured unit by unit — the step sits between HOUR and MINUTE, not where a reading of
     * "sub-day units are wider" would put it.
     *
     * @param unit the unit as written, in any spelling
     * @return the declared width, or null when the spelling is not a unit
     */
    private DataType dateDifferenceWidth(final String unit) {
        final IntervalUnit measured = IntervalUnit.fromSpelling(unit);
        if (measured == null) {
            return null;
        }
        if (measured == IntervalUnit.MILLISECOND || measured == IntervalUnit.MICROSECOND
                || measured == IntervalUnit.NANOSECOND) {
            return IntegerResultWidths.WIDEST;
        }
        if (measured == IntervalUnit.MINUTE || measured == IntervalUnit.SECOND) {
            return IntegerResultWidths.COUNTER;
        }
        return IntegerResultWidths.POSITION;
    }

    /**
     * The type a temporal takes once a unit's worth of time is added to it.
     *
     * @param subject the shifted expression's type
     * @param unit    the unit as written
     * @return the shifted type, or null when either is not determined
     */
    private DataType shiftedTemporal(final DataType subject, final String unit) {
        final IntervalUnit measured = IntervalUnit.fromSpelling(unit);
        if (!(subject instanceof DateTimeType) || measured == null) {
            return null;
        }
        if (!"DATE".equalsIgnoreCase(subject.getName())) {
            return subject;                        // a TIME and every timestamp flavour keep their own
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
            return null;
        }
        return DeclaredTypeFold.foldBranches(
            Arrays.asList(argType, new NumericType("NUMBER", 2, 0)));
    }

    /**
     * Whether a branch is the word NULL itself.
     *
     * @param branch the branch expression
     * @return true for a NULL literal
     */
    private boolean isNullLiteral(final Expression branch) {
        return branch instanceof LiteralExpression
            && ((LiteralExpression) branch).getType() == LiteralType.NULL;
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
    private boolean branchesConvertible(final DataType expected, final DataType offered) {
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
            // refused outright.
            return !(expected instanceof BinaryType) && !(offered instanceof BinaryType);
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
     * Whether a type belongs to a family whose pairings were measured. Anything else — a structured
     * type, a geo, a FILE, a VECTOR — is left alone.
     *
     * @param type the branch's type
     * @return true when a refusal may be raised for it
     */
    private boolean knownRefusableFamily(final DataType type) {
        return type instanceof NumericType || type instanceof StringType
            || type instanceof DateTimeType || type instanceof BooleanType
            || type instanceof BinaryType;
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
            if (branchType != null && !branchesConvertible(expected, branchType)) {
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
            if (branchType != null && !branchesConvertible(expected, branchType)) {
                offender = branch;
                offenderType = branchType;
                break;
            }
        }
        if (offenderType == null) {
            return;
        }
        if (branchesConvertible(expected, offenderType)) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.of(
            "Can not convert parameter '" + parameterName(offender) + "' of type ["
                + SqlTypeNames.canonical(offenderType) + "] into expected type ["
                + SqlTypeNames.canonical(expected) + "]"));
    }

    /**
     * How live spells an operand in a conversion refusal: a column QUALIFIED by the relation it came
     * from ({@code BM.S}), a string literal as its own text inside doubled quotes ({@code ''abc''}),
     * and anything else by its rendered form.
     */
    private String parameterName(final Expression expr) {
        if (expr instanceof ColumnReferenceExpression) {
            final ColumnReferenceExpression ref = (ColumnReferenceExpression) expr;
            final String owner = visitor.declaringTableName(ref);
            final String column = ref.getColumnName().toUpperCase(Locale.ROOT);
            return owner != null ? owner + "." + column : column;
        }
        if (expr instanceof LiteralExpression
                && ((LiteralExpression) expr).getType() == LiteralType.STRING) {
            return "'" + ((LiteralExpression) expr).getValue() + "'";
        }
        return AstPrinterVisitor.print(expr);
    }

    /**
     * The aggregates and window functions that hand their argument BACK — its family, width and
     * spelling alike, so {@code MAX(b)} over a BINARY(4) column is a BINARY(4) and over a
     * concatenation is the concatenation's own width. REVERSE belongs here too: it is the one
     * binary-taking string function that keeps the fixed spelling.
     */
    /** The window functions that number rows rather than return a value from one. */
    private static boolean isRowCounter(final String funcName) {
        return funcName != null && (funcName.equals("ROW_NUMBER") || funcName.equals("RANK")
            || funcName.equals("DENSE_RANK") || funcName.equals("NTILE"));
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
     *   AVG(NUMBER(p,s))      NUMBER(min(38, p+18), s+6)   AVG over NUMBER(10,2) is NUMBER(28,8),
     *                                                      over NUMBER(38,0) it is NUMBER(38,6)
     *   SUM/AVG over FLOAT    FLOAT                        the float family does not widen
     *   MEDIAN(x)             NUMBER(38,3)
     *   STDDEV(x)             FLOAT
     *   LISTAGG(x)            VARCHAR(134217728)           the 128MB conversion width
     *   MIN/MAX/ANY_VALUE/MODE                             the ARGUMENT's own type, handled above
     *   COUNT and its kin     NUMBER(18,0)                 handled above
     * </pre>
     *
     * @return the declared type, or null when this call is not one of these aggregates
     */
    private DataType aggregateResultType(final String funcName,
                                         final FunctionCallExpression call) {
        if (funcName.equals("MEDIAN")) {
            return new NumericType("NUMBER", 38, 3);
        }
        if (funcName.equals("STDDEV")) {
            return new NumericType("FLOAT", 38, 9);
        }
        if (funcName.equals("LISTAGG")) {
            return new StringType("VARCHAR", 134217728);
        }
        final boolean sum = funcName.equals("SUM");
        if (!sum && !funcName.equals("AVG") || call.getArguments().isEmpty()) {
            return null;
        }
        final DataType argument = infer(call.getArguments().get(0));
        if (!(argument instanceof NumericType)) {
            return null;
        }
        final NumericType numeric = (NumericType) argument;
        if (numeric.getName().equalsIgnoreCase("FLOAT")) {
            return numeric;
        }
        return sum
            ? new NumericType("NUMBER", Math.min(38, numeric.getPrecision() + 12), numeric.getScale())
            : new NumericType("NUMBER", Math.min(38, numeric.getPrecision() + 18), numeric.getScale() + 6);
    }

    private static boolean passesItsArgumentThrough(final String funcName) {
        return funcName.equals("MAX") || funcName.equals("MIN") || funcName.equals("ANY_VALUE")
            || funcName.equals("FIRST_VALUE") || funcName.equals("LAST_VALUE")
            || funcName.equals("LAG") || funcName.equals("LEAD") || funcName.equals("NTH_VALUE")
            || funcName.equals("MODE") || funcName.equals("REVERSE");
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
            boolean anyBinary = false;
            boolean anyOtherFamily = false;
            for (final Expression argument : args) {
                final DataType argumentType = infer(argument);
                if (argumentType instanceof BinaryType) {
                    anyBinary = true;
                    total += ((BinaryType) argumentType).getMaxLength();
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
            return new BinaryType("VARBINARY", (int) Math.min(total, MAX_BINARY_LENGTH));
        }
        final DataType first = infer(args.get(0));
        if (!(first instanceof BinaryType)) {
            return null;
        }
        if (funcName.equals("SUBSTR") || funcName.equals("SUBSTRING")
                || funcName.equals("LEFT") || funcName.equals("RIGHT")) {
            return new BinaryType("VARBINARY", ((BinaryType) first).getMaxLength());
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
     * are TO_BINARY and the crypto pair, which live reports at the full 8MB whatever they are given.
     */
    private static boolean widthFollowsArgument(final String funcName) {
        return funcName.equals("HEX_DECODE_BINARY") || funcName.equals("TRY_HEX_DECODE_BINARY")
            || funcName.equals("BASE64_DECODE_BINARY") || funcName.equals("TRY_BASE64_DECODE_BINARY")
            || funcName.equals("STRING_AS_BINARY");
    }

    /**
     * The width such a function's result declares, or null when the argument's own width is unknown
     * (in which case the declared 8MB stands, which is what an undetermined argument means).
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
        final long bytes;
        if (funcName.equals("STRING_AS_BINARY")) {
            bytes = characters * 4;
        } else if (funcName.startsWith("HEX_") || funcName.startsWith("TRY_HEX_")) {
            bytes = characters / 2;
        } else {
            bytes = characters * 3 / 4;
        }
        return new BinaryType("VARBINARY", (int) Math.min(bytes, MAX_BINARY_LENGTH));
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

    static DataType typeForName(final String targetType) {
        final int paren = targetType.indexOf('(');
        final String base = (paren > 0 ? targetType.substring(0, paren) : targetType)
            .trim().toUpperCase().replace(" ", "");
        final int parameter = firstParameter(targetType);
        switch (base) {
            case "VARCHAR": case "CHAR": case "STRING": case "TEXT": case "CHARACTER":
            case "NVARCHAR": case "NCHAR":
            // The VARYING spellings the grammar allows — `CHAR VARYING`, `CHARACTER VARYING`,
            // `NCHAR VARYING` — reach here with their whitespace already removed.
            case "CHARVARYING": case "CHARACTERVARYING": case "NCHARVARYING":
                // An UNPARAMETERISED string target is the 128MB conversion width, not the 16MB a
                // declared column defaults to: live reports CAST(i AS VARCHAR), i::VARCHAR and
                // CAST(v AS VARCHAR) — a string cast to a bare VARCHAR included — as VARCHAR(134217728).
                return parameter < 0 ? new StringType("VARCHAR", StringResultWidths.UNBOUNDED)
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
                return parameter < 0 ? BinaryType.BINARY : new BinaryType("BINARY", parameter);
            case "VARBINARY":
                // The two spellings cast identically and differ in one metadata cell: a cast to BINARY
                // reads fixed, a cast to VARBINARY does not (live-verified).
                return parameter < 0 ? BinaryType.VARBINARY : new BinaryType("VARBINARY", parameter);
            case "DATE":
                return DateTimeType.DATE;
            case "TIME":
                // The parameter of a temporal target is its fractional-second PRECISION, reported as the
                // descriptor's scale. Unparameterised it is 9, the family default.
                return parameter < 0 ? DateTimeType.TIME : new DateTimeType("TIME", parameter, false);
            // The timezone flavors stay distinct: the scalar-UDF return-type check refuses
            // NTZ<->LTZ, so a cast to a flavored timestamp must carry its flavor.
            case "DATETIME": case "TIMESTAMP": case "TIMESTAMP_NTZ": case "TIMESTAMPNTZ":
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
            default:
                return null;
        }
    }
}
