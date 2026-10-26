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

import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * GENERATOR's ROWCOUNT and TIMELIMIT are constants (live-verified): an argument that reads a column of a
 * relation to the call's left, or calls a function that answers anew for every row - RANDOM and the SEQ
 * family - is refused while the statement compiles, {@code argument 1 to function GENERATOR needs to be
 * constant, found 'GN.N'}, ROWCOUNT being argument 1 and TIMELIMIT argument 2 however the call names them.
 * Arithmetic over constants, a session variable and a scalar subquery the planner folds are constants; any other
 * subquery is refused as the plan holds it,
 * {@code found '(SELECT SUM(CAST(GN.N AS NUMBER(38,0))) AS "SUM(N)" FROM GN AS GN)'}. A bound reading the row
 * around its query is a correlation, {@code found 'CORRELATION(GN.N)'}, numbered by its place among the arguments
 * written. UUID_STRING is held to be a per-row function with the others.
 *
 * <p>An expression that reads a row is a constant all the same when no row can change it (live-verified):
 * <pre>
 *   NULL + t.n   t.n - NULL   -(NULL + t.n)   ABS(NULL + t.n)     a NULL operand makes the whole NULL
 *   NULL + RANDOM()                                               likewise
 *   t.n * 0 + 2                                                   a product with zero is zero
 *   IFF(TRUE, NULL, t.n)   IFF(FALSE, t.n, 2)                     a constant condition picks its branch
 *   NVL(2, t.n)   COALESCE(NULL + t.n, 2)                         the first argument that is no NULL
 *   CASE WHEN t.n IS NULL THEN NULL ELSE NULL END                 every branch the same NULL
 * </pre>
 * while {@code NVL(NULL, t.n)} is refused for {@code 'T.N'}, what is left of it, and {@code NULLIF(t.n, t.n)}
 * and {@code a.n + b.n} whole.
 */
final class GeneratorBounds {

    private static final String GENERATOR = "GENERATOR";

    /** The functions whose answer is the row's own. */
    private static final Set<String> PER_ROW = Set.of("RANDOM", "SEQ1", "SEQ2", "SEQ4", "SEQ8", "UUID_STRING");

    /**
     * The functions the account does not fold while it judges a bound: a bound calling one is no constant, whatever
     * its arguments (live-verified) - {@code LENGTH('abc')}, {@code SQRT(9)}, {@code CEIL(1.1)}, where ABS, FLOOR,
     * MOD, the conversions and the date parts fold. A function's other names are not folded either: TIMEDIFF and
     * TIMESTAMPDIFF as DATEDIFF, POSITION, in either spelling, as CHARINDEX.
     */
    private static final Set<String> UNFOLDED = Set.of("LENGTH", "LEN", "DATEDIFF", "TIMEDIFF", "TIMESTAMPDIFF",
        "SQRT", "TRUNC", "TRUNCATE", "CEIL", "ROUND", "POWER", "POW", "BITAND", "ARRAY_SIZE", "REGEXP_COUNT", "HASH",
        "SIGN", "LN", "EXP", "LOG", "CHARINDEX", "POSITION");

    /** The functions that answer the first of their arguments that is no NULL. */
    private static final Set<String> FIRST_NON_NULL = Set.of("NVL", "IFNULL", "COALESCE");

    /** The functions a NULL argument does not make NULL, beside the two families above. */
    private static final Set<String> NULL_TAKING = Set.of("NVL2", "DECODE", "NULLIF", "ZEROIFNULL", "EQUAL_NULL",
        "IS_NULL_VALUE", "HASH", "CONCAT_WS", "ARRAY_CONSTRUCT", "ARRAY_CONSTRUCT_COMPACT", "OBJECT_CONSTRUCT",
        "OBJECT_CONSTRUCT_KEEP_NULL", "BOOLAND", "BOOLOR", "BOOLXOR");

    private final Map<String, Table> scope;
    private final List<Table> scopeTables;
    private final RowFreeValues values;

    private GeneratorBounds(final Map<String, Table> scope, final List<Table> scopeTables,
                            final RowFreeValues values) {
        this.scope = scope;
        this.scopeTables = scopeTables;
        this.values = values;
    }

    /**
     * Refuses a GENERATOR bound that is no constant; any other function's arguments are not judged here.
     *
     * @param functionName the called table function
     * @param named        the call's named arguments
     * @param scope        the relations to the call's left by the names they answer to, or null
     * @param scopeTables  those relations in combined-row order, or null
     * @param correlated   the qualifiers of the relations in scope that are the row around the call's query
     * @param values       evaluates the parts of a bound that read no row
     */
    static void requireConstants(final String functionName, final List<FrostlakeParser.NamedArgumentContext> named,
                                 final Map<String, Table> scope, final List<Table> scopeTables,
                                 final Set<String> correlated, final RowFreeValues values) {
        final RuntimeException refused = refusal(functionName, named, scope, scopeTables, correlated, values);
        if (refused != null) {
            throw refused;
        }
    }

    /**
     * The refusal of a GENERATOR in a subquery's FROM whose bound reads the row around the subquery, or null: the
     * account judges a table function's arguments as it binds the call, before it plans the query around it, so the
     * bound is refused as one reading a correlation ({@code found 'CORRELATION(GN.N) + 1'}) where the subquery
     * would otherwise be no plan it can make (live-verified for value and EXISTS subqueries).
     *
     * @param executor  the executor, which evaluates and prints the parts of a bound that read no row
     * @param subquery  the subquery
     * @param relations the relations around the subquery, by the names they answer to
     * @return the refusal, or null
     */
    static RuntimeException outerBoundRefusal(final QueryExecutor executor,
                                              final FrostlakeParser.SelectStatementContext subquery,
                                              final Map<String, Table> relations) {
        for (final FrostlakeParser.SelectOperandContext operand : subquery.selectOperand()) {
            final FrostlakeParser.SelectClauseContext select = operand.selectClause();
            if (select == null || select.tableExpression() == null) {
                continue;
            }
            final List<FrostlakeParser.TableReferenceContext> references =
                new ArrayList<>(select.tableExpression().tableReference());
            for (final FrostlakeParser.JoinClauseContext join : select.tableExpression().joinClause()) {
                references.add(join.tableReference());
            }
            for (final FrostlakeParser.TableReferenceContext reference : references) {
                final RuntimeException refused = outerBoundRefusal(executor, reference, relations);
                if (refused != null) {
                    return refused;
                }
            }
        }
        return null;
    }

    /** The refusal of a GENERATOR call the FROM item is whose bounds read the outer row alone, or null. */
    private static RuntimeException outerBoundRefusal(final QueryExecutor executor,
                                                      final FrostlakeParser.TableReferenceContext reference,
                                                      final Map<String, Table> relations) {
        final FrostlakeParser.TableSourceContext source = reference == null ? null : reference.tableSource();
        if (source == null || source.TABLE() == null
                || !(source.expression() instanceof FrostlakeParser.FunctionCallNamedArgsExprContext)) {
            return null;
        }
        final FrostlakeParser.FunctionCallNamedArgsExprContext call =
            (FrostlakeParser.FunctionCallNamedArgsExprContext) source.expression();
        if (call.namedArgumentList() == null || !GENERATOR.equalsIgnoreCase(call.functionName().getText())) {
            return null;
        }
        final List<FrostlakeParser.NamedArgumentContext> named = call.namedArgumentList().namedArgument();
        final Set<String> correlated = correlatedQualifiers(relations, named.isEmpty() ? null : named.get(0));
        boolean readsOuterRow = false;
        for (final FrostlakeParser.NamedArgumentContext argument : named) {
            if (argument.expression() == null || !readsQualifier(argument.expression(), correlated)) {
                continue;
            }
            if (!readsOnly(argument.expression(), correlated)) {
                // A bound that reads a row of the subquery's own as well is judged as the subquery runs.
                return null;
            }
            readsOuterRow = true;
        }
        return readsOuterRow ? refusal(GENERATOR, named, relations, new ArrayList<>(relations.values()), correlated,
            executor.rowFreeValues(relations, correlated)) : null;
    }

    /** The refusal of a GENERATOR bound that is no constant, or null; see {@link #requireConstants}. */
    private static RuntimeException refusal(final String functionName,
                                            final List<FrostlakeParser.NamedArgumentContext> named,
                                            final Map<String, Table> scope, final List<Table> scopeTables,
                                            final Set<String> correlated, final RowFreeValues values) {
        if (!GENERATOR.equals(functionName)) {
            return null;
        }
        final GeneratorBounds bounds = new GeneratorBounds(scope, scopeTables, values);
        for (int index = 0; index < named.size(); index++) {
            final FrostlakeParser.NamedArgumentContext argument = named.get(index);
            final String name = argument.variableName().getText().toUpperCase(Locale.ROOT);
            final int place = "ROWCOUNT".equals(name) ? 1 : "TIMELIMIT".equals(name) ? 2 : 0;
            if (place == 0 || argument.expression() == null) {
                continue;
            }
            final int position = readsQualifier(argument.expression(), correlated) ? index + 1 : place;
            final ParserRuleContext bound = unwrapped(argument.expression());
            if (bound instanceof FrostlakeParser.ScalarSubqueryExprContext
                    && !values.folds((FrostlakeParser.ScalarSubqueryExprContext) bound)) {
                return new RuntimeException(SqlCompilationError.of("argument " + position
                    + " to function GENERATOR needs to be constant, found '" + values.planText(bound) + "'"));
            }
            if (callsUnfolded(argument.expression()) || bounds.foldsLate(argument.expression())
                    || bounds.subtractsDates(argument.expression())) {
                return new RuntimeException(SqlCompilationError.of("argument " + position
                    + " to function GENERATOR needs to be constant, found '"
                    + bounds.lateResidual(argument.expression()) + "'"));
            }
            if (bounds.readsNoRow(argument.expression())) {
                continue;
            }
            final BoundFold fold = bounds.fold(argument.expression());
            if (!fold.isConstant()) {
                // What is left of it is echoed as the plan holds it: NORMAL(2.0, 0.0, RANDOM()) (live-verified).
                return new RuntimeException(SqlCompilationError.of("argument " + position
                    + " to function GENERATOR needs to be constant, found '" + values.planText(fold.residual())
                    + "'"));
            }
        }
        return null;
    }

    /**
     * The qualifiers of the relations in scope that the query around the call holds, where the call's own FROM holds
     * none of that name: what a bound reads of them is the row around its query.
     *
     * @param scope the relations in scope, by the names they answer to, or null
     * @param call  a node of the call
     * @return the qualifiers, upper-cased
     */
    static Set<String> correlatedQualifiers(final Map<String, Table> scope, final ParseTree call) {
        final Set<String> outer = new HashSet<>();
        if (scope == null) {
            return outer;
        }
        final Set<String> own = new HashSet<>();
        ParseTree at = call;
        FrostlakeParser.TableReferenceContext holder = null;
        while (at != null && !(at instanceof FrostlakeParser.TableExpressionContext)) {
            if (at instanceof FrostlakeParser.TableReferenceContext && holder == null) {
                holder = (FrostlakeParser.TableReferenceContext) at;
            }
            at = at.getParent();
        }
        if (at != null) {
            final FrostlakeParser.TableExpressionContext from = (FrostlakeParser.TableExpressionContext) at;
            for (final FrostlakeParser.TableReferenceContext reference : from.tableReference()) {
                addQualifier(own, reference, holder);
            }
            for (final FrostlakeParser.JoinClauseContext join : from.joinClause()) {
                addQualifier(own, join.tableReference(), holder);
            }
        }
        for (final String key : scope.keySet()) {
            final String upper = key.toUpperCase(Locale.ROOT);
            if (!own.contains(upper)) {
                outer.add(upper);
            }
        }
        return outer;
    }

    /**
     * The qualifiers of the relations in scope, upper-cased.
     *
     * @param scope the relations in scope, by the names they answer to, or null
     * @return the qualifiers
     */
    static Set<String> qualifiersOf(final Map<String, Table> scope) {
        final Set<String> qualifiers = new HashSet<>();
        if (scope != null) {
            for (final String key : scope.keySet()) {
                qualifiers.add(key.toUpperCase(Locale.ROOT));
            }
        }
        return qualifiers;
    }

    /** The name a FROM item answers to, its alias or its table's name, unless it is the call's own item. */
    private static void addQualifier(final Set<String> into, final FrostlakeParser.TableReferenceContext reference,
                                     final FrostlakeParser.TableReferenceContext holder) {
        if (reference == null || reference == holder) {
            return;
        }
        final String qualifier = qualifierOf(reference);
        if (qualifier != null) {
            into.add(qualifier);
        }
    }

    /**
     * The name a FROM item answers to: its alias, or its table's name.
     *
     * @param reference the item
     * @return the name, upper-cased, or null for an item that answers to none
     */
    static String qualifierOf(final FrostlakeParser.TableReferenceContext reference) {
        if (reference.aliasName() != null) {
            return ParseTreeText.getIdentifier(reference.aliasName()).toUpperCase(Locale.ROOT);
        }
        if (reference.nonJoinKeywordIdentifier() != null && reference.nonJoinKeywordIdentifier().identifier() != null) {
            return ParseTreeText.getIdentifier(reference.nonJoinKeywordIdentifier().identifier())
                .toUpperCase(Locale.ROOT);
        }
        if (reference.tableSource() != null && reference.tableSource().tableQualifiedName() != null) {
            final String[] parts = ParseTreeText.qualifiedNameParts(reference.tableSource().tableQualifiedName());
            return parts[parts.length - 1].toUpperCase(Locale.ROOT);
        }
        return null;
    }

    /** Whether an expression names a column by one of the given qualifiers, outside any query nested in it. */
    private static boolean readsQualifier(final ParseTree node, final Set<String> qualifiers) {
        if (qualifiers == null || qualifiers.isEmpty() || node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final String[] parts = ParseTreeText.qualifiedNameParts(
                ((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName());
            return parts.length > 1 && qualifiers.contains(parts[parts.length - 2].toUpperCase(Locale.ROOT));
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readsQualifier(node.getChild(i), qualifiers)) {
                return true;
            }
        }
        return false;
    }

    /** Whether every column an expression names is named by one of the given qualifiers, and it holds no query. */
    private static boolean readsOnly(final ParseTree node, final Set<String> qualifiers) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final String[] parts = ParseTreeText.qualifiedNameParts(
                ((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName());
            return parts.length > 1 && qualifiers.contains(parts[parts.length - 2].toUpperCase(Locale.ROOT));
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (!readsOnly(node.getChild(i), qualifiers)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether an expression calls a function the account folds while it judges a bound: none it leaves unfolded
     * ({@code LENGTH('ab')}) and none whose answer is the row's own ({@code RANDOM()}).
     *
     * @param node the expression
     * @return whether its calls fold
     */
    static boolean foldsItsCalls(final ParseTree node) {
        return !callsUnfolded(node) && !callsPerRow(node);
    }

    private static boolean callsPerRow(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext && PER_ROW.contains(nameOf(node))) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (callsPerRow(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** Whether an expression calls a function the account does not fold while it judges a bound. */
    private static boolean callsUnfolded(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext && UNFOLDED.contains(nameOf(node))) {
            return true;
        }
        if (node instanceof FrostlakeParser.PositionInExprContext && UNFOLDED.contains(
                ((FrostlakeParser.PositionInExprContext) node).functionName().getText().toUpperCase(Locale.ROOT))) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (callsUnfolded(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a bound adds or subtracts a value the account folds only after it has judged the bound: a
     * ZEROIFNULL of a NULL, or a product of a constant call with zero - {@code ABS(-2) + ZEROIFNULL(NULL)} is refused
     * as {@code '2 + 0'} and {@code DAYOFMONTH(CURRENT_DATE) * 0 + 2} as {@code '0 + 2'}, where {@code 2 + 0} and
     * {@code t.n * 0 + 2} fold (live-verified).
     */
    private boolean foldsLate(final ParserRuleContext written) {
        final ParserRuleContext node = unwrapped(written);
        if (!(node instanceof FrostlakeParser.AdditiveExprContext) || !readsNoRow(node)) {
            return false;
        }
        for (final FrostlakeParser.ExpressionContext operand : ((FrostlakeParser.AdditiveExprContext) node)
                .expression()) {
            if (lateZero(unwrapped(operand))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a bound subtracts one date from another, which the account plans as the days between them and does
     * not fold: {@code CURRENT_DATE - CURRENT_DATE + 2} is refused (live-verified).
     */
    private boolean subtractsDates(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.AdditiveExprContext
                && ((FrostlakeParser.AdditiveExprContext) node).MINUS() != null && readsNoRow(node)) {
            final List<FrostlakeParser.ExpressionContext> operands =
                ((FrostlakeParser.AdditiveExprContext) node).expression();
            try {
                if (values.valueOf(operands.get(0)) instanceof LocalDate
                        && values.valueOf(operands.get(1)) instanceof LocalDate) {
                    return true;
                }
            } catch (final RuntimeException unread) {
                return false;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (subtractsDates(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** Whether an operand is a zero the account folds late: ZEROIFNULL(NULL), or a constant call times zero. */
    private static boolean lateZero(final ParserRuleContext operand) {
        if (operand instanceof FrostlakeParser.FunctionCallExprContext && "ZEROIFNULL".equals(nameOf(operand))) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) operand;
            return call.functionArgList() != null && call.functionArgList().functionArg().size() == 1
                && call.functionArgList().functionArg(0).booleanExpr() != null
                && "NULL".equalsIgnoreCase(call.functionArgList().functionArg(0).booleanExpr().getText());
        }
        if (operand instanceof FrostlakeParser.MultiplicativeExprContext
                && ((FrostlakeParser.MultiplicativeExprContext) operand).STAR() != null) {
            final List<FrostlakeParser.ExpressionContext> factors =
                ((FrostlakeParser.MultiplicativeExprContext) operand).expression();
            return "0".equals(factors.get(1).getText())
                && unwrapped(factors.get(0)) instanceof FrostlakeParser.FunctionCallExprContext
                || "0".equals(factors.get(0).getText())
                && unwrapped(factors.get(1)) instanceof FrostlakeParser.FunctionCallExprContext;
        }
        return false;
    }

    /**
     * A refused bound as the account echoes it: its plan, or, for a sum the account folds only late, each operand's
     * value around the operator.
     */
    private String lateResidual(final ParserRuleContext written) {
        final ParserRuleContext node = unwrapped(written);
        if (!callsUnfolded(node) && foldsLate(node) && node instanceof FrostlakeParser.AdditiveExprContext) {
            final FrostlakeParser.AdditiveExprContext sum = (FrostlakeParser.AdditiveExprContext) node;
            final Object left = values.valueOf(sum.expression(0));
            final Object right = values.valueOf(sum.expression(1));
            if (left instanceof Number && right instanceof Number) {
                return plain(left) + " " + sum.getChild(1).getText() + " " + plain(right);
            }
        }
        return values.planText(written);
    }

    /** A number as the plan prints a folded one. */
    private static String plain(final Object number) {
        return number instanceof BigDecimal ? ((BigDecimal) number).stripTrailingZeros().toPlainString()
            : number.toString();
    }

    /**
     * Whether an expression reads no column of the relations in scope and calls no per-row function. A column the
     * statistics pin to one value reads none: the bound is read as that value (see BoundColumnPins).
     */
    private boolean readsNoRow(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return true;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext && scope != null && scopeTables != null
                && TablePartitioning.slotOf((FrostlakeParser.QualifiedNameExprContext) node, scope, scopeTables)
                    >= 0) {
            return values.pinsOneValue((FrostlakeParser.QualifiedNameExprContext) node);
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext && PER_ROW.contains(nameOf(node))) {
            return false;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (!readsNoRow(node.getChild(i))) {
                return false;
            }
        }
        return true;
    }

    /** What an expression is before any row is read. */
    private BoundFold fold(final ParserRuleContext written) {
        final ParserRuleContext node = unwrapped(written);
        if (readsNoRow(node)) {
            return holdsQuery(node) ? BoundFold.constant() : valueOf(node);
        }
        if (node instanceof FrostlakeParser.UnaryExprContext) {
            return carried(fold(((FrostlakeParser.UnaryExprContext) node).expression()), node);
        }
        if (node instanceof FrostlakeParser.CastExprContext) {
            return carried(fold(((FrostlakeParser.CastExprContext) node).expression()), node);
        }
        if (node instanceof FrostlakeParser.CastExpr2Context) {
            return carried(fold(((FrostlakeParser.CastExpr2Context) node).expression()), node);
        }
        if (node instanceof FrostlakeParser.TryCastExprContext) {
            return carried(fold(((FrostlakeParser.TryCastExprContext) node).expression()), node);
        }
        if (node instanceof FrostlakeParser.AdditiveExprContext) {
            return combined(((FrostlakeParser.AdditiveExprContext) node).expression(), false, node);
        }
        if (node instanceof FrostlakeParser.MultiplicativeExprContext) {
            final FrostlakeParser.MultiplicativeExprContext product = (FrostlakeParser.MultiplicativeExprContext) node;
            return combined(product.expression(), product.STAR() != null, node);
        }
        if (node instanceof FrostlakeParser.IsNullExprContext) {
            final FrostlakeParser.IsNullExprContext test = (FrostlakeParser.IsNullExprContext) node;
            final BoundFold operand = fold(test.expression());
            if (operand.isNull()) {
                return BoundFold.valued(Boolean.valueOf(test.NOT() == null));
            }
            return operand.isConstant() ? BoundFold.constant() : BoundFold.rowDecided(node);
        }
        if (node instanceof FrostlakeParser.CaseExprContext) {
            return caseOf(((FrostlakeParser.CaseExprContext) node).caseExpression(), node);
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            return callOf((FrostlakeParser.FunctionCallExprContext) node);
        }
        return BoundFold.rowDecided(node);
    }

    /** A sign or a cast: NULL of a NULL, a constant of a constant. */
    private static BoundFold carried(final BoundFold operand, final ParserRuleContext node) {
        if (operand.isNull()) {
            return BoundFold.valued(null);
        }
        return operand.isConstant() ? BoundFold.constant() : BoundFold.rowDecided(node);
    }

    /** Two operands under an operator: a NULL one makes the whole NULL, and a zero makes a product zero. */
    private BoundFold combined(final List<FrostlakeParser.ExpressionContext> operands, final boolean product,
                               final ParserRuleContext node) {
        final BoundFold left = fold(operands.get(0));
        final BoundFold right = fold(operands.get(1));
        if (left.isNull() || right.isNull()) {
            return BoundFold.valued(null);
        }
        if (product && (left.isZero() || right.isZero())) {
            return BoundFold.valued(BigDecimal.ZERO);
        }
        return left.isConstant() && right.isConstant() ? BoundFold.constant() : BoundFold.rowDecided(node);
    }

    private BoundFold callOf(final FrostlakeParser.FunctionCallExprContext call) {
        final String name = nameOf(call);
        final List<ParserRuleContext> arguments = new ArrayList<>();
        if (call.functionArgList() != null) {
            for (final FrostlakeParser.FunctionArgContext argument : call.functionArgList().functionArg()) {
                if (argument.booleanExpr() == null) {
                    return BoundFold.rowDecided(call);
                }
                arguments.add(argument.booleanExpr());
            }
        }
        if (PER_ROW.contains(name)) {
            return BoundFold.rowDecided(call);
        }
        if (FIRST_NON_NULL.contains(name)) {
            return firstNonNull(arguments, call);
        }
        if ("IFF".equals(name) && arguments.size() == 3) {
            final BoundFold condition = fold(arguments.get(0));
            if (condition.isValued()) {
                return fold(arguments.get(condition.isTrue() ? 1 : 2));
            }
            return sameOf(arguments.subList(1, 3), condition.isConstant(), call);
        }
        boolean constant = true;
        for (final ParserRuleContext argument : arguments) {
            final BoundFold folded = fold(argument);
            if (folded.isNull() && !NULL_TAKING.contains(name)) {
                return BoundFold.valued(null);
            }
            constant = constant && folded.isConstant();
        }
        return constant ? BoundFold.constant() : BoundFold.rowDecided(call);
    }

    /** NVL and its kin: the first argument that is no NULL decides, and what reads a row before it is found. */
    private BoundFold firstNonNull(final List<ParserRuleContext> arguments, final ParserRuleContext call) {
        for (int i = 0; i < arguments.size(); i++) {
            final BoundFold folded = fold(arguments.get(i));
            if (folded.isNull()) {
                continue;
            }
            if (folded.isValued()) {
                return folded;
            }
            if (!folded.isConstant()) {
                // What is left of the call when only its last argument remains is that argument.
                return i > 0 && i == arguments.size() - 1 ? folded : BoundFold.rowDecided(call);
            }
            // A constant whose value was not worked out may be a NULL: the arguments after it count too.
            return sameOf(arguments.subList(i + 1, arguments.size()), true, call);
        }
        return BoundFold.valued(null);
    }

    private BoundFold caseOf(final FrostlakeParser.CaseExpressionContext written, final ParserRuleContext node) {
        final List<ParserRuleContext> results = new ArrayList<>();
        final boolean searched = written instanceof FrostlakeParser.SearchedCaseExprContext;
        final List<FrostlakeParser.WhenClauseContext> whens = searched
            ? ((FrostlakeParser.SearchedCaseExprContext) written).whenClause()
            : ((FrostlakeParser.SimpleCaseExprContext) written).whenClause();
        final FrostlakeParser.BooleanExprContext otherwise = searched
            ? ((FrostlakeParser.SearchedCaseExprContext) written).booleanExpr()
            : ((FrostlakeParser.SimpleCaseExprContext) written).booleanExpr();
        boolean decidedByRow = !searched;
        for (final FrostlakeParser.WhenClauseContext when : whens) {
            if (!decidedByRow) {
                final BoundFold condition = fold(when.booleanExpr(0));
                if (condition.isValued() && condition.isTrue()) {
                    return fold(when.booleanExpr(1));
                }
                if (condition.isValued()) {
                    continue;
                }
                decidedByRow = true;
            }
            results.add(when.booleanExpr(1));
        }
        if (!decidedByRow) {
            return otherwise == null ? BoundFold.valued(null) : fold(otherwise);
        }
        // A condition a row decides: the CASE is a constant only when every branch left is the same NULL.
        boolean allNull = otherwise == null || fold(otherwise).isNull();
        for (final ParserRuleContext result : results) {
            allNull = allNull && fold(result).isNull();
        }
        return allNull ? BoundFold.valued(null) : BoundFold.rowDecided(node);
    }

    /** Branches of which any may be taken: a NULL when all are, a constant when all are and so is what picks. */
    private BoundFold sameOf(final List<ParserRuleContext> branches, final boolean constantPick,
                             final ParserRuleContext node) {
        boolean allNull = true;
        boolean allConstant = constantPick;
        for (final ParserRuleContext branch : branches) {
            final BoundFold folded = fold(branch);
            allNull = allNull && folded.isNull();
            allConstant = allConstant && folded.isConstant();
        }
        if (allNull) {
            return BoundFold.valued(null);
        }
        return allConstant ? BoundFold.constant() : BoundFold.rowDecided(node);
    }

    /** The value of an expression that reads no row; a fault is left for the call itself to raise. */
    private BoundFold valueOf(final ParserRuleContext node) {
        try {
            return BoundFold.valued(values.valueOf(node));
        } catch (final RuntimeException raisedByTheCall) {
            return BoundFold.constant();
        }
    }

    private static boolean holdsQuery(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holdsQuery(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** The expression inside the parentheses and wrappers around it. */
    private static ParserRuleContext unwrapped(final ParserRuleContext written) {
        ParserRuleContext node = written;
        while (true) {
            if (node instanceof FrostlakeParser.ValueExprContext) {
                node = ((FrostlakeParser.ValueExprContext) node).expression();
            } else if (node instanceof FrostlakeParser.ParenExprContext) {
                node = ((FrostlakeParser.ParenExprContext) node).booleanExpr();
            } else {
                return node;
            }
        }
    }

    private static String nameOf(final ParseTree call) {
        return ((FrostlakeParser.FunctionCallExprContext) call).functionName().getText().toUpperCase(Locale.ROOT);
    }
}
