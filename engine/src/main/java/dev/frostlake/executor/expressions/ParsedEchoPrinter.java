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

import dev.frostlake.executor.SessionNullOrdering;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.GeoTypes;
import dev.frostlake.types.SqlTypeNames;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Prints an expression the way the account names it when it refuses a Snowscript UDF's body: from the tree as
 * PARSED, before any name is resolved and any type checked, so nothing the plan would add shows — no conversion,
 * no qualifier, no rewrite of a conditional — and what the parser itself rewrote does (live-verified, shape by
 * shape):
 *
 * <pre>
 *   a::INT                        CAST(A AS NUMBER(38,0))         the target spelled in full
 *   TRY_CAST(a AS INT)            TRY_CAST(A)                     and left out of a TRY_CAST
 *   DATE '2020-01-02'             TO_DATE('2020-01-02')
 *   INTERVAL '1 day, 2 hours'     INTERVAL_LITERAL('day', '1', 'hours', '2')
 *   [1, 2]   {'k': 1}   v:k[0]    ARRAY_CONSTRUCT(1, 2)   OBJECT_CONSTRUCT('k', 1)   GET(GET(V, 'k'), 0)
 *   CASE a WHEN 1 THEN 2 END      CASE(A, 1, 2, null)
 *   a BETWEEN 1 AND 2             BETWEEN(A, 1, 2)
 *   a NOT IN (1, 2)               NOT(A IN (1, 2))                every negated predicate is a NOT of the plain one
 *   a IS DISTINCT FROM 1          NOT(EQUAL_NULL(A, 1))
 *   -a   +a   NOT a               NEGATE(A)   UNARY PLUS(A)   NOT(A)
 *   nosuch(x =&gt; 1)                NOSUCH(1)                       an argument's name is not kept
 *   "noSuch"(1)   t.a             "noSuch"(1)   T                 a variable's field is named by the variable
 * </pre>
 *
 * <p>An operator prints in one of two styles, chosen for the whole expression. With no query and no window call
 * anywhere in it the operators stand between their operands, an operand in parentheses of its own unless it is a
 * name, a literal, a call of no arguments or a window call: {@code (A > 1) AND (A < 3)}, {@code (NOSUCH(1)) + 1},
 * {@code F2() + 1}. An IS NULL is the one that brackets nothing, {@code A + 1 IS NULL}. With a query or a window
 * call in it, every operator is a call of its own name: {@code "+"(A, 1)}, {@code AND(x, y)},
 * {@code "IS NULL"(A)}, {@code LIKE(A, 'x')}, and a membership in a query is the comparison it stands for,
 * {@code "="(A, ANY(SELECT …))}. A query prints as {@link ParsedQueryEcho} spells it.
 *
 * <p>A membership list is printed member by member: the plain ones — whatever would stand bare as an operand, and
 * a query — share a list, and a computed one opens a list of its own, so {@code a IN (1 + 1, 2)} reads
 * {@code A IN (1 + 1) IN (2)}.
 */
public final class ParsedEchoPrinter extends AstPrinterVisitor {

    /** Whether every operator prints as a call of its name. */
    private final boolean calls;
    /** Whether the expression stands in a query's own text. */
    private final boolean inQuery;
    /** Prints the queries the expression holds. */
    private final ParsedQueryEcho queries;
    /** Whether a call is named as one no function has been found for yet (see {@link #printUnresolved}). */
    private final boolean unresolved;
    /** How the calls it spells its own way are printed, or null for none. */
    private final CallEcho spelled;
    /** The relation a bare name is read in, where a query is printed resolved; null for none. */
    private final String relation;

    ParsedEchoPrinter(final boolean calls, final boolean inQuery, final ParsedQueryEcho queries) {
        this(calls, inQuery, queries, false, null, null);
    }

    private ParsedEchoPrinter(final boolean calls, final boolean inQuery, final ParsedQueryEcho queries,
                              final boolean unresolved, final CallEcho spelled, final String relation) {
        this.calls = calls;
        this.inQuery = inQuery;
        this.queries = queries;
        this.unresolved = unresolved;
        this.spelled = spelled;
        this.relation = relation;
    }

    /**
     * A printer for a query's own expressions where the query is printed resolved: the operators as written, and a
     * bare name its relation's.
     *
     * @param queries  prints the queries the expressions hold
     * @param relation the relation a bare name is read in, or null where the query reads more than one or none
     * @return the printer
     */
    static ParsedEchoPrinter resolvedIn(final ParsedQueryEcho queries, final String relation) {
        return new ParsedEchoPrinter(false, true, queries, false, null, relation);
    }

    /**
     * An expression as the plan prints it resolved, as a routine parameter's default is echoed: the operators as
     * written, and a query's bare names its relation's (see {@link ParsedQueryEcho}).
     *
     * @param expression the expression
     * @return its text
     */
    public static String printResolved(final Expression expression) {
        return expression.accept(new ParsedEchoPrinter(false, false, new ParsedQueryEcho(true), false, null, null));
    }

    /**
     * An expression as the tree as parsed prints it.
     *
     * @param expression the expression
     * @return its text
     */
    public static String print(final Expression expression) {
        return print(expression, null);
    }

    /**
     * An expression as the tree as parsed prints it, each call {@code spelled} spells printed its way.
     *
     * @param expression the expression
     * @param spelled    how the calls it knows are printed, or null for none
     * @return its text
     */
    public static String print(final Expression expression, final CallEcho spelled) {
        final QueryOrWindowWalk walk = new QueryOrWindowWalk();
        expression.accept(walk);
        return expression.accept(new ParsedEchoPrinter(walk.found(), false, new ParsedQueryEcho(), false, spelled,
            null));
    }

    /**
     * An expression printed by this printer, as a {@link CallEcho} prints a call's arguments.
     *
     * @param expression the expression
     * @return its text
     */
    public String printed(final Expression expression) {
        return expression.accept(this);
    }

    /**
     * An expression as a table function's argument list names it when it refuses one that is no constant
     * (live-verified): every operator a call, and every call named as one no function has been found for yet.
     *
     * <pre>
     *   TYPE = CSV              [unresolved function: "="](TYPE, CSV)
     *   UPPER('a' || 'b')       [unresolved function: UPPER]([unresolved function: "||"]('a', 'b'))
     *   x IN (1, 2)             X IN 1 IN 2
     *   1::INT   (1, 2)         CAST(1 AS NUMBER(38,0))   ROW(1, 2)
     * </pre>
     *
     * @param expression the expression
     * @return its text
     */
    public static String printUnresolved(final Expression expression) {
        return expression.accept(new ParsedEchoPrinter(true, true, new ParsedQueryEcho(), true, null, null));
    }

    /** A call's name, told as unresolved where the list being printed has not found its functions yet. */
    private String named(final String name) {
        return unresolved ? "[unresolved function: " + name + "]" : name;
    }

    @Override
    public String visitLiteral(final LiteralExpression expr) {
        if (unresolved && expr.getValue() == null) {
            return "NULL";
        }
        if (expr.getType() == LiteralType.STRING) {
            return "'" + escaped(String.valueOf(expr.getValue())) + "'";
        }
        if (expr.getType() == LiteralType.BINARY) {
            return "X'" + expr.getValue() + "'";
        }
        if (expr.getValue() instanceof BigDecimal) {
            // Never by its exponent: 0.000000001 reads as written.
            return ((BigDecimal) expr.getValue()).toPlainString();
        }
        return super.visitLiteral(expr);
    }

    /** A text as a literal holds it between its quotes: each quote and each backslash doubled. */
    private static String escaped(final String value) {
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

    @Override
    public String visitColumnReference(final ColumnReferenceExpression expr) {
        final String column = expr.getPositionalOrdinal() > 0
            ? "$" + expr.getPositionalOrdinal() : SqlIdentifiers.spellCanonical(expr.getColumnName());
        if (!expr.isQualified()) {
            return relation != null && expr.getPositionalOrdinal() <= 0 ? relation + "." + column : column;
        }
        final String qualifier = SqlIdentifiers.spellAlreadyCanonicalPath(expr.getTableName());
        // Outside a query a qualified name is a variable and its field, and the variable alone is named.
        return inQuery ? qualifier + "." + column : qualifier;
    }

    @Override
    public String visitBinaryOperation(final BinaryOperationExpression expr) {
        final BinaryOperator operator = expr.getOperator();
        if (operator == BinaryOperator.LIKE || operator == BinaryOperator.ILIKE) {
            return like(expr, operator == BinaryOperator.LIKE ? "LIKE" : "ILIKE");
        }
        if (operator == BinaryOperator.NOT_LIKE || operator == BinaryOperator.NOT_ILIKE) {
            return "NOT(" + like(expr, operator == BinaryOperator.NOT_LIKE ? "LIKE" : "ILIKE") + ")";
        }
        final String symbol = expr.isBangSpelled() ? "!=" : symbol(operator);
        if (calls) {
            final String name = operator == BinaryOperator.AND || operator == BinaryOperator.OR
                ? symbol : "\"" + symbol + "\"";
            return named(name) + "(" + expr.getLeft().accept(this) + ", " + expr.getRight().accept(this) + ")";
        }
        return operand(expr, expr.getLeft()) + " " + symbol + " " + operand(expr, expr.getRight());
    }

    /** An operand of an operation: as the echo spells it beside its operator, else as {@link #operand} prints it. */
    private String operand(final BinaryOperationExpression operation, final Expression expr) {
        final String own = spelled == null ? null : spelled.spellOperand(operation, expr, this);
        return own != null ? own : operand(expr);
    }

    /** A LIKE or an ILIKE: its escape is one more operand of the same operator, a third argument of the call. */
    private String like(final BinaryOperationExpression expr, final String name) {
        final Expression escape = expr.getEscape();
        if (calls) {
            return named(name) + "(" + expr.getLeft().accept(this) + ", " + expr.getRight().accept(this)
                + (escape == null ? "" : ", " + escape.accept(this)) + ")";
        }
        return operand(expr.getLeft()) + " " + name + " " + operand(expr.getRight())
            + (escape == null ? "" : " " + name + " " + operand(escape));
    }

    /** An operand between operators: bare where it {@link #standsBare}, in parentheses of its own otherwise. */
    private String operand(final Expression expr) {
        final String text = expr.accept(this);
        return standsBare(expr) ? text : "(" + text + ")";
    }

    /**
     * Whether an operand needs no parentheses: a name, a literal — a negative number included —, a call of no
     * arguments and a window call. A bind prints as the null it holds while the body compiles, and in parentheses.
     */
    private static boolean standsBare(final Expression expr) {
        if (expr instanceof BindVariableExpression) {
            return false;
        }
        if (expr instanceof LiteralExpression || expr instanceof ColumnReferenceExpression
                || expr instanceof SessionVarExpression || expr instanceof WindowFunctionExpression) {
            return true;
        }
        if (expr instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) expr;
            return unary.getOperator() == UnaryOperator.NEGATE && isNumber(unary.getOperand());
        }
        if (expr instanceof FunctionCallExpression && !(expr instanceof RowExpression)) {
            final FunctionCallExpression call = (FunctionCallExpression) expr;
            return call.getArguments().isEmpty() && !call.isDistinct() && (!call.isStar() || isCountStar(call));
        }
        return false;
    }

    private static boolean isNumber(final Expression expr) {
        return expr instanceof LiteralExpression && (((LiteralExpression) expr).getType() == LiteralType.INTEGER
            || ((LiteralExpression) expr).getType() == LiteralType.DECIMAL);
    }

    private static boolean isCountStar(final FunctionCallExpression call) {
        return call.isStar() && !call.isDistinct() && "COUNT".equals(call.getFunctionName());
    }

    @Override
    public String visitUnaryOperation(final UnaryOperationExpression expr) {
        final Expression operand = expr.getOperand();
        switch (expr.getOperator()) {
            case EXISTS:
                return "EXISTS(" + queryText(operand) + ")";
            case NOT:
                if (isExistence(operand)) {
                    return "NOT " + operand.accept(this);
                }
                if (isNegatedExistence(operand)) {
                    // The NOT of a NOT EXISTS is the EXISTS again.
                    return ((UnaryOperationExpression) operand).getOperand().accept(this);
                }
                return named("NOT") + "(" + operand.accept(this) + ")";
            case NEGATE:
                if (isNumber(operand)) {
                    final String number = operand.accept(this);
                    return isZero(number) || number.startsWith("-") ? number : "-" + number;
                }
                return named("NEGATE") + "(" + operand.accept(this) + ")";
            case PLUS:
                return named(calls ? "\"UNARY PLUS\"" : "UNARY PLUS") + "(" + operand.accept(this) + ")";
            default:
                return super.visitUnaryOperation(expr);
        }
    }

    private static boolean isExistence(final Expression expr) {
        return expr instanceof UnaryOperationExpression
            && ((UnaryOperationExpression) expr).getOperator() == UnaryOperator.EXISTS;
    }

    private static boolean isNegatedExistence(final Expression expr) {
        return expr instanceof UnaryOperationExpression
            && ((UnaryOperationExpression) expr).getOperator() == UnaryOperator.NOT
            && isExistence(((UnaryOperationExpression) expr).getOperand());
    }

    /** Whether a printed number is a zero, which carries no sign. */
    private static boolean isZero(final String number) {
        for (int i = 0; i < number.length(); i++) {
            final char c = number.charAt(i);
            if (c != '0' && c != '.') {
                return false;
            }
        }
        return true;
    }

    @Override
    public String visitIsNull(final IsNullExpression expr) {
        final String test = expr.isNot() ? "IS NOT NULL" : "IS NULL";
        final String operand = expr.getOperand().accept(this);
        return calls ? named("\"" + test + "\"") + "(" + operand + ")" : operand + " " + test;
    }

    @Override
    public String visitIn(final InExpression expr) {
        if (expr.hasSubquery()) {
            return quantified(expr.isNot() ? "!=" : "=", expr.getValue().accept(this),
                expr.isNot() ? "ALL" : "ANY", expr.getSubquery());
        }
        if (unresolved) {
            // The members follow their value one by one, each after an IN of its own: a plain one bare, a
            // computed one in parentheses of its own.
            final StringBuilder text = new StringBuilder(expr.getValue().accept(this));
            for (final Expression member : expr.getValues()) {
                text.append(" IN ").append(operand(member));
            }
            return expr.isNot() ? "NOT(" + text + ")" : text.toString();
        }
        final List<String> members = new ArrayList<>();
        final List<Boolean> plain = new ArrayList<>();
        for (final Expression member : expr.getValues()) {
            members.add(member.accept(this));
            plain.add(Boolean.valueOf(standsBare(member) || member instanceof SubqueryExpression));
        }
        final String membership = membership(operand(expr.getValue()), members, plain);
        return expr.isNot() ? "NOT(" + membership + ")" : membership;
    }

    /**
     * A value IN its members: the plain members share a list, opened by the first of them, and every other
     * member is a list of its own, opened where it stands. Its closing parenthesis is the only one written for a
     * list open before it, so {@code a IN (1, 1 + 1)} reads {@code A IN (1 IN (1 + 1)} — one short, as the
     * account prints it — and a plain member after it goes on with the list the plain ones share, closed after
     * the last of them: {@code a IN (1, 1 + 1, 3)} reads {@code A IN (1 IN (1 + 1), 3)}.
     */
    private static String membership(final String value, final List<String> members, final List<Boolean> plain) {
        final StringBuilder text = new StringBuilder(value);
        boolean shared = false;
        boolean open = false;
        for (int i = 0; i < members.size(); i++) {
            if (plain.get(i).booleanValue()) {
                text.append(shared ? ", " : " IN (").append(members.get(i));
                shared = true;
                open = true;
            } else {
                text.append(" IN (").append(members.get(i)).append(')');
                open = false;
            }
        }
        return open ? text.append(')').toString() : text.toString();
    }

    /** A comparison with ANY or ALL of a query's rows: the call of its operator over the value and the rows. */
    private String quantified(final String symbol, final String value, final String quantifier,
                              final Expression query) {
        return "\"" + symbol + "\"(" + value + ", " + quantifier + "(" + queryText(query) + "))";
    }

    @Override
    public String visitTupleIn(final TupleInExpression expr) {
        final String value = expr.isScalarLeft() && expr.getValues().size() == 1
            ? operand(expr.getValues().get(0)) : row(expr.getValues());
        if (expr.hasSubquery()) {
            return quantified(expr.isNot() ? "!=" : "=", value, expr.isNot() ? "ALL" : "ANY", expr.getSubquery());
        }
        final List<String> members = new ArrayList<>();
        final List<Boolean> plain = new ArrayList<>();
        if (expr.hasTupleRows()) {
            for (final List<Expression> member : expr.getTupleRows()) {
                members.add(member.size() == 1 && expr.isScalarLeft() ? member.get(0).accept(this) : row(member));
                plain.add(Boolean.TRUE);
            }
        } else {
            for (final Expression member : expr.getFlatListValues()) {
                members.add(member.accept(this));
                plain.add(Boolean.valueOf(standsBare(member) || member instanceof SubqueryExpression));
            }
        }
        final String membership = membership(value, members, plain);
        return expr.isNot() ? "NOT(" + membership + ")" : membership;
    }

    /** A parenthesized list of values, which the tree holds as a ROW. */
    private String row(final List<Expression> values) {
        return "ROW(" + list(values) + ")";
    }

    private String list(final List<Expression> values) {
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            text.append(i > 0 ? ", " : "").append(values.get(i).accept(this));
        }
        return text.toString();
    }

    @Override
    public String visitQuantifiedComparison(final QuantifiedComparisonExpression expr) {
        final String symbol = expr.isBangSpelled() ? "!=" : symbol(expr.getOperator());
        return quantified(symbol, expr.getLeft().accept(this),
            expr.getQuantifier() == Quantifier.ALL ? "ALL" : "ANY", expr.getSubquery());
    }

    @Override
    public String visitRowComparison(final RowComparisonExpression expr) {
        final String left = expr.isScalarLeft() && expr.getLeft().size() == 1
            ? expr.getLeft().get(0).accept(this) : row(expr.getLeft());
        final String right = expr.isScalarRight() && expr.getRight().size() == 1
            ? expr.getRight().get(0).accept(this) : row(expr.getRight());
        return calls ? "\"" + expr.getOperator() + "\"(" + left + ", " + right + ")"
            : left + " " + expr.getOperator() + " " + right;
    }

    @Override
    public String visitBetween(final BetweenExpression expr) {
        final String between = named("BETWEEN") + "(" + expr.getValue().accept(this) + ", "
            + expr.getLower().accept(this) + ", " + expr.getUpper().accept(this) + ")";
        return expr.isNot() ? "NOT(" + between + ")" : between;
    }

    @Override
    public String visitLikeAnyAll(final LikeAnyAllExpression expr) {
        final String name = (expr.isCaseInsensitive() ? "ILIKE" : "LIKE") + (expr.isAll() ? "_ALL" : "_ANY");
        // The escape stands second, a null where none is written.
        final String escape = expr.getEscape() == null ? "null" : expr.getEscape().accept(this);
        final StringBuilder text = new StringBuilder();
        if (calls) {
            text.append(name).append('(').append(expr.getSubject().accept(this)).append(", ").append(escape);
            for (final Expression pattern : expr.getPatterns()) {
                text.append(", ").append(pattern.accept(this));
            }
            return text.append(')').toString();
        }
        text.append(operand(expr.getSubject())).append(' ').append(name).append(' ').append(escape);
        for (final Expression pattern : expr.getPatterns()) {
            text.append(' ').append(name).append(' ').append(operand(pattern));
        }
        return text.toString();
    }

    @Override
    public String visitCaseExpression(final CaseExpression expr) {
        final Expression subject = subjectOf(expr);
        final StringBuilder text = new StringBuilder(named("CASE")).append('(');
        if (subject != null) {
            text.append(subject.accept(this)).append(", ");
        }
        for (final WhenClause when : expr.getWhenClauses()) {
            final Expression test = subject == null
                ? when.getCondition() : ((BinaryOperationExpression) when.getCondition()).getRight();
            text.append(test.accept(this)).append(", ").append(when.getResult().accept(this)).append(", ");
        }
        final Expression otherwise = expr.getElseExpression();
        return text.append(otherwise == null ? (unresolved ? "NULL" : "null") : otherwise.accept(this))
            .append(')').toString();
    }

    /** The subject of a simple CASE, which every WHEN compares its value with, or null for a searched CASE. */
    private static Expression subjectOf(final CaseExpression expr) {
        Expression subject = null;
        for (final WhenClause when : expr.getWhenClauses()) {
            if (!when.isOperandMatch() || !(when.getCondition() instanceof BinaryOperationExpression)
                    || !((BinaryOperationExpression) when.getCondition()).isSimpleCaseTest()) {
                return null;
            }
            if (subject == null) {
                subject = ((BinaryOperationExpression) when.getCondition()).getLeft();
            }
        }
        return subject;
    }

    @Override
    public String visitCast(final CastExpression expr) {
        final String value = expr.getExpression().accept(this);
        if (expr.isTypedLiteral()) {
            // A DECFLOAT literal is echoed as written, its word in lower case: decfloat '1' (live-verified).
            return "DECFLOAT".equalsIgnoreCase(expr.getTargetType()) ? "decfloat " + value
                : "TO_" + expr.getTargetType() + "(" + value + ")";
        }
        if (expr.isTryMode()) {
            return "TRY_CAST(" + value + ")";
        }
        return "CAST(" + value + " AS " + target(expr) + ")";
    }

    /**
     * A cast's target as the tree holds it: the type the written name stands for, every width spelled — a text
     * and a binary of no width at the widest a conversion reaches — and UNKNOWN for a type the parser gives no
     * name of its own, a geospatial one included.
     */
    private static String target(final CastExpression expr) {
        final DataType declared = expr.getDeclaredTarget() != null
            ? expr.getDeclaredTarget() : TypeInferencer.declaredCastTarget(expr.getTargetType());
        if (declared == null || GeoTypes.isGeo(declared)) {
            return "UNKNOWN";
        }
        return declared instanceof BinaryType
            ? ((BinaryType) declared).refusalText() : SqlTypeNames.canonical(declared);
    }

    @Override
    public String visitObjectAccess(final ObjectAccessExpression expr) {
        String text = expr.getBase().accept(this);
        for (final String key : expr.getPathParts()) {
            text = "GET(" + text + ", '" + escaped(key) + "')";
        }
        return text;
    }

    @Override
    public String visitArrayAccess(final ArrayAccessExpression expr) {
        return "GET(" + expr.getArray().accept(this) + ", " + expr.getIndex().accept(this) + ")";
    }

    @Override
    public String visitJsonObject(final JsonObjectExpression expr) {
        final StringBuilder text = new StringBuilder("OBJECT_CONSTRUCT(");
        boolean first = true;
        for (final Map.Entry<String, Expression> entry : expr.getProperties().entrySet()) {
            text.append(first ? "'" : ", '").append(escaped(entry.getKey())).append("', ")
                .append(entry.getValue().accept(this));
            first = false;
        }
        return text.append(')').toString();
    }

    @Override
    public String visitJsonArray(final JsonArrayExpression expr) {
        return "ARRAY_CONSTRUCT(" + list(expr.getElements()) + ")";
    }

    @Override
    public String visitInterval(final IntervalExpression expr) {
        if (expr.getWrittenAmount() == null) {
            return super.visitInterval(expr);
        }
        final StringBuilder text = new StringBuilder("INTERVAL_LITERAL(");
        for (IntervalExpression part = expr; part != null; part = part.getRest()) {
            // Each part's unit word and amount as written, SECOND for a part written without a unit.
            text.append(part == expr ? "'" : ", '")
                .append(part.getWrittenUnit() == null ? "SECOND" : part.getWrittenUnit())
                .append("', '").append(part.getWrittenAmount()).append('\'');
        }
        return text.append(')').toString();
    }

    @Override
    public String visitBindVariable(final BindVariableExpression expr) {
        // A bind holds nothing while the body compiles.
        return "null";
    }

    @Override
    public String visitLambda(final LambdaExpression expr) {
        final StringBuilder text = new StringBuilder("(");
        for (int i = 0; i < expr.getParameters().size(); i++) {
            text.append(i > 0 ? ", " : "").append(expr.getParameters().get(i).toUpperCase(Locale.ROOT));
        }
        // The parenthesis the account opens before the parameters is never closed.
        return text.append(" -> ").append(expr.getBody().accept(this)).toString();
    }

    @Override
    public String visitSubquery(final SubqueryExpression expr) {
        return "(" + queryText(expr) + ")";
    }

    /** A query's text as the tree prints it, or as written where its shape is not one this printer models. */
    private String queryText(final Expression query) {
        if (!(query instanceof SubqueryExpression)) {
            return query.accept(this);
        }
        final String written = ((SubqueryExpression) query).getSubquery();
        final String echoed = queries.echo(written);
        if (echoed != null && unresolved) {
            // The row a query with no FROM reads is told with the word in capitals here.
            return echoed.replace("(VALUES (null)) DUAL", "(VALUES (NULL)) DUAL");
        }
        return echoed != null ? echoed : written;
    }

    @Override
    public String visitFunctionCall(final FunctionCallExpression expr) {
        final String own = spelled == null ? null : spelled.spell(expr, this);
        if (own != null) {
            return own;
        }
        final String name = name(expr.getNameParts(), expr.getFunctionName());
        final List<Expression> arguments = expr.getArguments();
        if (expr.getNameParts() == null && expr.getNameExpression() == null && arguments.isEmpty()
                && !expr.isStar() && !(expr instanceof RowExpression)) {
            // A keyword that reads as a call, CURRENT_DATE: a query's text spells the call out.
            return inQuery ? name + "()" : name;
        }
        if (isCountStar(expr)) {
            return calls ? "\"COUNT(*)\"()" : "COUNT(*)";
        }
        final String matcher = matcherOf(expr);
        if (matcher != null && !calls) {
            final StringBuilder text = new StringBuilder();
            for (int i = 0; i < arguments.size(); i++) {
                text.append(i > 0 ? " " + matcher + " " : "").append(operand(arguments.get(i)));
            }
            return text.toString();
        }
        final String told = matcher != null ? matcher : name;
        final StringBuilder text = new StringBuilder(expr instanceof RowExpression ? told : named(told)).append('(');
        if (!calls && "EXTRACT".equals(name) && arguments.size() == 2) {
            text.append("from ");
        }
        if (expr.isDistinct()) {
            text.append("DISTINCT ");
        }
        return text.append(expr.isStar() ? "*" : list(arguments)).append(')').toString();
    }

    /**
     * A call with the names of its arguments kept, the way a table function's call in FROM is printed.
     *
     * @param call the call
     * @return its text
     */
    String namedCall(final FunctionCallExpression call) {
        final StringBuilder text = new StringBuilder(name(call.getNameParts(), call.getFunctionName())).append('(');
        final List<Expression> arguments = call.getArguments();
        final List<String> names = call.getArgumentNames();
        for (int i = 0; i < arguments.size(); i++) {
            text.append(i > 0 ? ", " : "");
            if (names != null && names.get(i) != null) {
                text.append(names.get(i).toUpperCase(Locale.ROOT)).append(" => ");
            }
            text.append(arguments.get(i).accept(this));
        }
        return text.append(')').toString();
    }

    /**
     * The operator a regular-expression match stands between its operands under, or null for any other call: the
     * operator as written, and the function's own name where the match was written as a call.
     */
    private static String matcherOf(final FunctionCallExpression call) {
        if (call.getOperatorSpelling() != null) {
            return call.getOperatorSpelling();
        }
        final List<String> parts = call.getNameParts();
        if (parts != null && parts.size() != 1 || call.getArguments().size() < 2) {
            return null;
        }
        final String name = parts == null ? call.getFunctionName() : parts.get(0);
        return "REGEXP_LIKE".equals(name) || "RLIKE".equals(name) || "REGEXP".equals(name) ? name : null;
    }

    /** A call's name, each part in quotes only where it needs them. */
    private static String name(final List<String> parts, final String folded) {
        if (parts == null || parts.isEmpty()) {
            return folded;
        }
        final StringBuilder name = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            name.append(i > 0 ? "." : "").append(SqlIdentifiers.spellCanonical(parts.get(i)));
        }
        return name.toString();
    }

    /**
     * A window call: its arguments, its PARTITION BY keys and its ORDER BY keys, each of those with its direction
     * and where its nulls sort; the frame, the null treatment and a WITHIN GROUP are not kept. Two calls are
     * rewritten as the tree is built: an AVG over an ordered window that no ROWS frame bounds is the SUM over
     * the COUNT, and a RATIO_TO_REPORT is its value over the window's SUM, a null where that is zero.
     */
    @Override
    public String visitWindowFunction(final WindowFunctionExpression expr) {
        if (expr.getPartitionKeys() == null || expr.getFunctionName() == null) {
            return super.visitWindowFunction(expr);
        }
        final String name = name(expr.getNameParts(), expr.getFunctionName());
        final boolean average = "AVG".equals(name) && !expr.isDistinct();
        // An aggregate over the whole partition reads the same rows in any order, and names none: AVG, SUM and MAX
        // over ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING are OVER (), where a navigation function
        // keeps its order, LAST_VALUE's ORDER BY is printed (live-verified).
        final boolean navigation = "FIRST_VALUE".equals(name) || "LAST_VALUE".equals(name) || "NTH_VALUE".equals(name);
        final String over = over(expr, !(!navigation && expr.isWholeFramed()));
        final String arguments = (expr.isDistinct() ? "DISTINCT " : "") + list(expr.getArguments());
        if (average && expr.isOrdered() && !expr.isOwnFramed() && !expr.isWholeFramed()) {
            return "SUM(" + arguments + ")" + over + " / COUNT(" + arguments + ")" + over;
        }
        if ("RATIO_TO_REPORT".equals(name) && expr.getArguments().size() == 1) {
            final String total = "SUM(" + arguments + ")" + over;
            return operand(expr.getArguments().get(0)) + " / (IFF(" + total + " = 0, null, " + total + "))";
        }
        if (expr.getStarCall() != null) {
            return ("COUNT".equals(name) && !expr.isDistinct() ? "\"COUNT(*)\"()" : name + "(*)") + over;
        }
        return name + "(" + arguments + ")" + over;
    }

    private String over(final WindowFunctionExpression expr, final boolean ordered) {
        final StringBuilder text = new StringBuilder(" OVER (");
        final List<Expression> partition = expr.getPartitionKeys();
        for (int i = 0; i < partition.size(); i++) {
            text.append(i == 0 ? "PARTITION BY " : ", ").append(partition.get(i).accept(this));
        }
        final List<Expression> order = expr.getOrderKeys();
        for (int i = 0; ordered && i < order.size(); i++) {
            text.append(i == 0 ? (partition.isEmpty() ? "ORDER BY " : " ORDER BY ") : ", ")
                .append(order.get(i).accept(this))
                .append(sortSuffix(expr.getOrderAscending().get(i).booleanValue(), expr.getOrderNullsFirst().get(i)));
        }
        return text.append(')').toString();
    }

    /**
     * A sort key's direction and where its nulls sort, both always spelled: where the session's
     * DEFAULT_NULL_ORDERING puts them for the direction unless written otherwise (see {@link SessionNullOrdering}).
     *
     * @param ascending  whether the key sorts ascending
     * @param nullsFirst the NULLS FIRST (TRUE) or NULLS LAST (FALSE) written, or null for neither
     * @return the suffix, led by a space
     */
    static String sortSuffix(final boolean ascending, final Boolean nullsFirst) {
        return (ascending ? " ASC NULLS " : " DESC NULLS ")
            + (SessionNullOrdering.nullsFirst(ascending, nullsFirst) ? "FIRST" : "LAST");
    }
}
