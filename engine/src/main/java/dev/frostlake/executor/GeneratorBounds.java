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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * GENERATOR's ROWCOUNT and TIMELIMIT are constants (live-verified): an argument that reads a column of a
 * relation to the call's left, or calls a function that answers anew for every row - RANDOM and the SEQ
 * family - is refused while the statement compiles, {@code argument 1 to function GENERATOR needs to be
 * constant, found 'GN.N'}, ROWCOUNT being argument 1 and TIMELIMIT argument 2 however the call names them.
 * Arithmetic over constants, a session variable and a scalar subquery are constants. UUID_STRING is held to
 * be a per-row function with the others.
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
     * @param values       evaluates the parts of a bound that read no row
     */
    static void requireConstants(final String functionName, final List<FrostlakeParser.NamedArgumentContext> named,
                                 final Map<String, Table> scope, final List<Table> scopeTables,
                                 final RowFreeValues values) {
        if (!GENERATOR.equals(functionName)) {
            return;
        }
        final GeneratorBounds bounds = new GeneratorBounds(scope, scopeTables, values);
        for (final FrostlakeParser.NamedArgumentContext argument : named) {
            final String name = argument.identifier().getText().toUpperCase(Locale.ROOT);
            final int position = "ROWCOUNT".equals(name) ? 1 : "TIMELIMIT".equals(name) ? 2 : 0;
            if (position == 0 || argument.expression() == null || bounds.readsNoRow(argument.expression())) {
                continue;
            }
            final BoundFold fold = bounds.fold(argument.expression());
            if (!fold.isConstant()) {
                throw new RuntimeException(SqlCompilationError.of("argument " + position
                    + " to function GENERATOR needs to be constant, found '"
                    + ParseTreeText.getOriginalText(fold.residual()).toUpperCase(Locale.ROOT) + "'"));
            }
        }
    }

    /** Whether an expression reads no column of the relations in scope and calls no per-row function. */
    private boolean readsNoRow(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return true;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext && scope != null && scopeTables != null
                && TablePartitioning.slotOf((FrostlakeParser.QualifiedNameExprContext) node, scope, scopeTables)
                    >= 0) {
            return false;
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
        if (node instanceof FrostlakeParser.ConcatExprContext) {
            return combined(((FrostlakeParser.ConcatExprContext) node).expression(), false, node);
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
