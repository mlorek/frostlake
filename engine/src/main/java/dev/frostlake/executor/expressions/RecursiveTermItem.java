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

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One select item of a recursive CTE's anchor or recursive term, read for what the CTE's column takes from it:
 * the columns the item reads, whether it is one column passed through unchanged, and whether it can answer NULL.
 *
 * <p>The account types a recursive CTE's columns from both branches, and tells a column that holds no NULL from
 * one that may: a number or a text written out holds none, nor does arithmetic, a concatenation, a cast or one of
 * the plain scalar functions over operands that hold none; a NULL, a boolean written out, a comparison, a CASE
 * without ELSE and every other function may (live-verified).
 *
 * <pre>
 *   n + 1                 reads N, holds no NULL when N holds none
 *   NULLIF(n + 1, 3)      reads N, may be NULL
 *   n                     reads N, and is N itself
 *   (SELECT MAX(k) …)     opaque: what it reads is not followed
 * </pre>
 */
public final class RecursiveTermItem implements ExpressionVisitor<Boolean> {

    /** The scalar functions that answer no NULL over arguments that hold none (live-verified, each of them). */
    private static final Set<String> NULL_PRESERVING = new HashSet<String>(Arrays.asList(
        "ABS", "SIGN", "FLOOR", "CEIL", "ROUND", "TRUNC", "MOD", "LEAST", "GREATEST", "LENGTH", "LEFT", "UPPER",
        "LOWER", "TRIM", "SUBSTR", "REVERSE", "REPLACE", "LPAD", "CONCAT", "TO_VARCHAR", "TO_NUMBER", "DATEADD",
        "CURRENT_DATE"));

    /** The functions that answer their first argument holding a value. */
    private static final Set<String> FIRST_NOT_NULL = new HashSet<String>(Arrays.asList("COALESCE", "NVL", "IFNULL"));

    private final ColumnNullability columns;
    private final Set<String> qualifiers;
    private final Set<String> reads = new LinkedHashSet<String>();
    private final String passedThrough;
    private final boolean foreignColumn;
    private final boolean notNull;
    private boolean opaque;

    private RecursiveTermItem(final Expression item, final ColumnNullability columns, final Set<String> qualifiers) {
        this.columns = columns;
        this.qualifiers = qualifiers;
        final ColumnReferenceExpression column = item instanceof ColumnReferenceExpression
            && ((ColumnReferenceExpression) item).getStarArgument() == null ? (ColumnReferenceExpression) item : null;
        this.passedThrough = column != null && ofTheCte(column) ? upper(column.getColumnName()) : null;
        this.foreignColumn = column != null && !ofTheCte(column);
        this.notNull = Boolean.TRUE.equals(item.accept(this));
    }

    /**
     * Read one item.
     *
     * @param item       the item's expression
     * @param columns    what is known of the CTE's columns the item may read
     * @param qualifiers the names the CTE goes by where the item stands, upper-cased: a column written under any
     *                   other name is another relation's
     * @return what the CTE's column takes from it
     */
    public static RecursiveTermItem of(final Expression item, final ColumnNullability columns,
                                       final Set<String> qualifiers) {
        return new RecursiveTermItem(item, columns, qualifiers);
    }

    /** Whether a column reference may name a column of the CTE: unqualified, or under one of its names. */
    private boolean ofTheCte(final ColumnReferenceExpression column) {
        return !column.isQualified() || qualifiers.contains(upper(column.getTableName()));
    }

    /**
     * Whether the item is one column of another relation, passed through unchanged.
     *
     * @return true for such an item
     */
    public boolean passesForeignColumn() {
        return foreignColumn;
    }

    /**
     * The columns the item reads, upper-cased.
     *
     * @return their names
     */
    public Set<String> reads() {
        return reads;
    }

    /**
     * The column the item passes through unchanged.
     *
     * @return its name, upper-cased, or null when the item is anything but one column
     */
    public String passedThrough() {
        return passedThrough;
    }

    /**
     * Whether the item reads through something this reading does not follow: a subquery or a window.
     *
     * @return true when what it reads is unknown
     */
    public boolean isOpaque() {
        return opaque;
    }

    /**
     * Whether the item holds no NULL.
     *
     * @return true when it holds none
     */
    public boolean isNotNull() {
        return notNull;
    }

    private static String upper(final String name) {
        return name == null ? "" : name.toUpperCase(Locale.ROOT);
    }

    private boolean all(final List<Expression> expressions) {
        boolean every = true;
        for (final Expression expression : expressions) {
            if (expression != null && !Boolean.TRUE.equals(expression.accept(this))) {
                every = false;
            }
        }
        return every;
    }

    @Override
    public Boolean visitLiteral(final LiteralExpression expr) {
        return expr.getType() != LiteralType.NULL && expr.getType() != LiteralType.BOOLEAN;
    }

    @Override
    public Boolean visitDefaultMarker(final DefaultMarkerExpression expr) {
        return false;
    }

    @Override
    public Boolean visitColumnReference(final ColumnReferenceExpression expr) {
        if (expr.getStarArgument() != null) {
            opaque = true;
            return false;
        }
        if (!ofTheCte(expr)) {
            return false;
        }
        final String name = upper(expr.getColumnName());
        reads.add(name);
        return columns.isNotNull(name);
    }

    @Override
    public Boolean visitBinaryOperation(final BinaryOperationExpression expr) {
        final boolean left = Boolean.TRUE.equals(expr.getLeft().accept(this));
        final boolean right = Boolean.TRUE.equals(expr.getRight().accept(this));
        if (expr.getEscape() != null) {
            expr.getEscape().accept(this);
        }
        switch (expr.getOperator()) {
            case ADD:
            case SUBTRACT:
            case MULTIPLY:
            case DIVIDE:
            case MODULO:
            case CONCAT:
                return left && right;
            default:
                return false;
        }
    }

    @Override
    public Boolean visitRowComparison(final RowComparisonExpression expr) {
        all(expr.getLeft());
        all(expr.getRight());
        return false;
    }

    @Override
    public Boolean visitUnaryOperation(final UnaryOperationExpression expr) {
        if (expr.getOperator() == UnaryOperator.EXISTS) {
            opaque = true;
            return false;
        }
        return Boolean.TRUE.equals(expr.getOperand().accept(this));
    }

    @Override
    public Boolean visitFunctionCall(final FunctionCallExpression expr) {
        final List<Expression> arguments = expr.getArguments();
        final String name = upper(expr.getFunctionName());
        if (FIRST_NOT_NULL.contains(name)) {
            boolean any = false;
            for (final Expression argument : arguments) {
                if (Boolean.TRUE.equals(argument.accept(this))) {
                    any = true;
                }
            }
            return any;
        }
        if ("IFF".equals(name) && arguments.size() == 3) {
            arguments.get(0).accept(this);
            final boolean then = Boolean.TRUE.equals(arguments.get(1).accept(this));
            final boolean otherwise = Boolean.TRUE.equals(arguments.get(2).accept(this));
            return then && otherwise;
        }
        // DATEADD's first argument is the word naming a part, not a value.
        final boolean every = all("DATEADD".equals(name) && arguments.size() == 3 ? arguments.subList(1, 3)
            : arguments);
        return every && NULL_PRESERVING.contains(name);
    }

    @Override
    public Boolean visitLambda(final LambdaExpression expr) {
        opaque = true;
        return false;
    }

    @Override
    public Boolean visitCaseExpression(final CaseExpression expr) {
        boolean every = expr.getElseExpression() != null;
        for (final WhenClause when : expr.getWhenClauses()) {
            if (when.getCondition() != null) {
                when.getCondition().accept(this);
            }
            if (!Boolean.TRUE.equals(when.getResult().accept(this))) {
                every = false;
            }
        }
        if (expr.getElseExpression() != null && !Boolean.TRUE.equals(expr.getElseExpression().accept(this))) {
            every = false;
        }
        return every;
    }

    @Override
    public Boolean visitCast(final CastExpression expr) {
        final boolean operand = Boolean.TRUE.equals(expr.getExpression().accept(this));
        return operand && !expr.isTryMode();
    }

    @Override
    public Boolean visitObjectAccess(final ObjectAccessExpression expr) {
        expr.getBase().accept(this);
        return false;
    }

    @Override
    public Boolean visitArrayAccess(final ArrayAccessExpression expr) {
        expr.getArray().accept(this);
        expr.getIndex().accept(this);
        return false;
    }

    @Override
    public Boolean visitSubquery(final SubqueryExpression expr) {
        opaque = true;
        return false;
    }

    @Override
    public Boolean visitExecuteImmediate(final ExecuteImmediateExpression expr) {
        opaque = true;
        return false;
    }

    @Override
    public Boolean visitJsonObject(final JsonObjectExpression expr) {
        for (final Expression value : expr.getProperties().values()) {
            value.accept(this);
        }
        return false;
    }

    @Override
    public Boolean visitJsonArray(final JsonArrayExpression expr) {
        all(expr.getElements());
        return false;
    }

    @Override
    public Boolean visitBetween(final BetweenExpression expr) {
        expr.getValue().accept(this);
        expr.getLower().accept(this);
        expr.getUpper().accept(this);
        return false;
    }

    @Override
    public Boolean visitLikeAnyAll(final LikeAnyAllExpression expr) {
        expr.getSubject().accept(this);
        all(expr.getPatterns());
        if (expr.getEscape() != null) {
            expr.getEscape().accept(this);
        }
        return false;
    }

    @Override
    public Boolean visitIn(final InExpression expr) {
        expr.getValue().accept(this);
        if (expr.getValues() != null) {
            all(expr.getValues());
        }
        if (expr.getSubquery() != null) {
            opaque = true;
        }
        return false;
    }

    @Override
    public Boolean visitQuantifiedComparison(final QuantifiedComparisonExpression expr) {
        expr.getLeft().accept(this);
        opaque = true;
        return false;
    }

    @Override
    public Boolean visitInterval(final IntervalExpression expr) {
        if (expr.getValueExpression() != null) {
            expr.getValueExpression().accept(this);
        }
        return false;
    }

    @Override
    public Boolean visitSessionVar(final SessionVarExpression expr) {
        return false;
    }

    @Override
    public Boolean visitBindVariable(final BindVariableExpression expr) {
        return false;
    }

    @Override
    public Boolean visitSystemStreamHasData(final SystemStreamHasDataExpression expr) {
        return false;
    }

    @Override
    public Boolean visitSystemUserTaskCancel(final SystemUserTaskCancelExpression expr) {
        return false;
    }

    @Override
    public Boolean visitIsNull(final IsNullExpression expr) {
        expr.getOperand().accept(this);
        return false;
    }

    @Override
    public Boolean visitTupleIn(final TupleInExpression expr) {
        opaque = true;
        return false;
    }

    @Override
    public Boolean visitWindowFunction(final WindowFunctionExpression expr) {
        opaque = true;
        return false;
    }

    @Override
    public Boolean visitSpread(final SpreadExpression expr) {
        expr.getInner().accept(this);
        return false;
    }
}
