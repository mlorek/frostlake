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

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.FileType;
import dev.frostlake.types.GeoTypes;
import dev.frostlake.types.MapType;
import dev.frostlake.types.NumericLiteralTypes;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorElementType;
import dev.frostlake.types.VectorType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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

    private final ExpressionEvaluatorVisitor visitor;

    TypeInferencer(final ExpressionEvaluatorVisitor visitor) {
        this.visitor = visitor;
    }

    /** The statically-known type of {@code expr}, or null when undetermined. */
    DataType infer(final Expression expr) {
        if (expr instanceof LiteralExpression) {
            switch (((LiteralExpression) expr).getType()) {
                case STRING:
                    return StringType.VARCHAR;
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
                    return BinaryType.BINARY;
                default:
                    return null;   // NULL literal: untyped
            }
        }
        if (expr instanceof ColumnReferenceExpression) {
            final TableColumn resolved = visitor.resolveDeclaredColumn((ColumnReferenceExpression) expr);
            return resolved != null ? resolved.getDataType() : null;
        }
        if (expr instanceof UnaryOperationExpression) {
            // A negated numeric literal keeps the literal's own measured NUMBER — the sign adds no
            // digit (live: -208 ∪ 1.25 declares NUMBER(5,2), so -208 counts as (3,0)).
            final UnaryOperationExpression unary = (UnaryOperationExpression) expr;
            if (unary.getOperator() == UnaryOperator.NEGATE
                    && unary.getOperand() instanceof LiteralExpression) {
                final DataType negated = infer(unary.getOperand());
                return negated instanceof NumericType ? negated : null;
            }
            return null;
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
        if (expr instanceof CaseExpression) {
            final List<Expression> branches = caseBranches((CaseExpression) expr);
            return allBranchesFile(branches) ? FileType.FILE : commonGeoBranch(branches);
        }
        if (expr instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) expr;
            if (call.getNameExpression() != null) {
                return null;   // IDENTIFIER(expr): the target function is dynamic
            }
            final String funcName = call.getFunctionName().toUpperCase();
            if (CONDITIONAL_FUNCTIONS.contains(funcName)) {
                final List<Expression> branches = conditionalBranches(funcName, call.getArguments());
                if (allBranchesFile(branches)) {
                    return FileType.FILE;
                }
                return GEO_PRESERVING_CONDITIONALS.contains(funcName) ? commonGeoBranch(branches) : null;
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
            return fn != null ? fn.getReturnType() : null;
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
        "IFF", "COALESCE", "NVL", "IFNULL", "GREATEST", "LEAST"));

    /**
     * The branch expressions whose type a conditional's result takes: every argument, except that
     * {@code IFF}'s first argument is the CONDITION rather than a value.
     */
    private static List<Expression> conditionalBranches(final String funcName, final List<Expression> args) {
        if (!funcName.equals("IFF")) {
            return args;
        }
        return args.size() > 1 ? args.subList(1, args.size()) : new ArrayList<Expression>();
    }

    /** A CASE expression's value branches: each WHEN's result, plus the ELSE when present. */
    private static List<Expression> caseBranches(final CaseExpression expr) {
        final List<Expression> branches = new ArrayList<>();
        for (final CaseExpression.WhenClause when : expr.getWhenClauses()) {
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

    static DataType typeForName(final String targetType) {
        final int paren = targetType.indexOf('(');
        final String base = (paren > 0 ? targetType.substring(0, paren) : targetType)
            .trim().toUpperCase().replace(" ", "");
        switch (base) {
            case "VARCHAR": case "CHAR": case "STRING": case "TEXT": case "CHARACTER":
            case "NVARCHAR": case "NCHAR":
            // The VARYING spellings the grammar allows — `CHAR VARYING`, `CHARACTER VARYING`,
            // `NCHAR VARYING` — reach here with their whitespace already removed.
            case "CHARVARYING": case "CHARACTERVARYING": case "NCHARVARYING":
                return StringType.VARCHAR;
            case "NUMBER": case "DECIMAL": case "NUMERIC": case "INT": case "INTEGER":
            case "BIGINT": case "SMALLINT": case "TINYINT": case "BYTEINT":
            case "FLOAT": case "FLOAT4": case "FLOAT8": case "DOUBLE": case "REAL":
            case "DOUBLEPRECISION": case "DECFLOAT":
                return NumericType.NUMBER;
            case "BOOLEAN":
                return BooleanType.BOOLEAN;
            case "BINARY": case "VARBINARY":
                return BinaryType.BINARY;
            case "DATE":
                return DateTimeType.DATE;
            case "TIME":
                return DateTimeType.TIME;
            case "DATETIME": case "TIMESTAMP": case "TIMESTAMP_NTZ": case "TIMESTAMPNTZ":
            case "TIMESTAMP_LTZ": case "TIMESTAMPLTZ": case "TIMESTAMP_TZ": case "TIMESTAMPTZ":
                return DateTimeType.TIMESTAMP_NTZ;
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
