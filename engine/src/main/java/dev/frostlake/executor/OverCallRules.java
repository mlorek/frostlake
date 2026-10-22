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

import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.NamedCallRewrite;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.functions.AggregateFunction;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.window.NamedArgumentWindowFunctions;
import dev.frostlake.functions.window.WindowFunctionArity;
import dev.frostlake.functions.window.WindowFunctionNames;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.KeyContexts;
import dev.frostlake.parser.OperandRefusal;
import dev.frostlake.parser.OperandRefusals;
import dev.frostlake.parser.SyntaxErrorListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The compile-time rules for a call written with {@code OVER} that do not wait for a row: the KIND of
 * the function it names, and whether a window-capable function takes the named arguments it was
 * written with. A call counts in every spelling the grammar has for it — positional
 * ({@code ABS(a) OVER ()}), named ({@code GENERATOR(ROWCOUNT => 1) OVER ()}) and mixed
 * ({@code UPPER(g, x => 2) OVER ()}) — and the calls are judged in the order they are written.
 *
 * <p>A name that is neither a window function nor an aggregate is refused the same way whatever it is —
 * a scalar, a table function or no function at all, and whatever its arguments look like
 * (live-verified):
 *
 * <pre>
 *   ABS(a) OVER (…)                        Invalid function type [ABS] for window function.
 *   GENERATOR(ROWCOUNT => 1) OVER ()       Invalid function type [GENERATOR] for window function.
 *   NOSUCHFN(x => 1) OVER ()               Invalid function type [NOSUCHFN] for window function.
 * </pre>
 *
 * <p>A window function or an aggregate written with named arguments is judged for its ARITY first, the
 * named values counted and echoed as the positional ones they stand for, and then for the named
 * arguments themselves, positioned at the call:
 *
 * <pre>
 *   SUM(x => 1, y => 2) OVER ()            too many arguments for function [SUM(1, 2)] expected 1, got 2
 *   RANK(x => 1) OVER (ORDER BY n)         too many arguments for function [RANK(1)] expected 0, got 1
 *   SUM(x => 1) OVER ()                    function SUM does not support named arguments
 *   LAG(n, x => 1, y => 0) OVER (…)        function LAG does not support named arguments
 * </pre>
 *
 * <p>The account takes named arguments for a measured set of these functions, under any argument name —
 * {@code MEDIAN(x => n) OVER ()}, {@code FIRST_VALUE(abc => g) OVER (ORDER BY n)} — which is not a rule
 * of the arity or the family; see {@link NamedArgumentWindowFunctions}.
 */
final class OverCallRules {

    /** The ordered-value aggregates, whose value arrives through WITHIN GROUP: no upper bound here. */
    private static final Set<String> ORDERED_VALUE_AGGREGATES = Set.of("PERCENTILE_CONT", "PERCENTILE_DISC");

    private static final String CONDITIONAL_CHANGE_EVENT = "CONDITIONAL_CHANGE_EVENT";

    private static final String RATIO_TO_REPORT = "RATIO_TO_REPORT";

    private static final String KIND_SENTENCE_START = "Invalid function type [";

    private static final String KIND_SENTENCE_END = "] for window function.";

    private final FunctionRegistry functionRegistry;
    private final PlanEcho echo;

    OverCallRules(final FunctionRegistry functionRegistry, final PlanEcho echo) {
        this.functionRegistry = functionRegistry;
        this.echo = echo;
    }

    /**
     * Refuses the first operand of a parsed text that read ({@link OperandRefusals}). A refusal that names a kind
     * of function gives way to an earlier call written with OVER whose function takes no window: the account names
     * kinds in the order written, so {@code SELECT ABS(x) OVER (), x::INT OVER ()} is ABS and
     * {@code SELECT x::INT OVER (), ABS(x) OVER ()} is CAST (live-verified).
     *
     * @param script    the parsed script, or one statement of it
     * @param functions the registry that tells a window function or an aggregate from any other name
     */
    static void requireOperands(final ParseTree script, final FunctionRegistry functions) {
        final OperandRefusal refusal = OperandRefusals.firstOfStatement(script);
        if (refusal == null) {
            return;
        }
        if (!refusal.isOfValue()) {
            final List<ParserRuleContext> windowed = new ArrayList<>();
            collectEverywhere(script, windowed);
            ParserRuleContext earliest = null;
            for (final ParserRuleContext call : windowed) {
                final String canonical = functionName(call).getText().toUpperCase(Locale.ROOT);
                if (overToken(call) < refusal.order() && !WindowFunctionNames.handles(canonical)
                        && !functions.hasBuiltInAggregateFunction(canonical)
                        && (earliest == null || overToken(call) < overToken(earliest))) {
                    earliest = call;
                }
            }
            if (earliest != null) {
                throw new RuntimeException(SqlCompilationError.of(KIND_SENTENCE_START
                    + spelledName(functionName(earliest)) + KIND_SENTENCE_END));
            }
        }
        throw new RuntimeException(SqlCompilationError.of(refusal.message()));
    }

    /**
     * A text's refusal as the account orders it: one that names a kind of function, raised where the text was
     * parsed and no registry was at hand, gives way to the refusal {@link #requireOperands} makes of the same
     * text. Any other failure is handed back as it is.
     *
     * @param refused   what parsing the text threw
     * @param sql       the text
     * @param functions the registry that tells a window function or an aggregate from any other name
     * @return the refusal to throw
     */
    static RuntimeException inWrittenOrder(final RuntimeException refused, final String sql,
                                           final FunctionRegistry functions) {
        final String told = refused.getMessage();
        if (told == null || !told.endsWith(KIND_SENTENCE_END) || !told.contains(KIND_SENTENCE_START)) {
            return refused;
        }
        final ParseTree script = SyntaxErrorListener.cleanParse(sql);
        if (script == null) {
            return refused;
        }
        try {
            requireOperands(script, functions);
        } catch (final RuntimeException ordered) {
            return ordered;
        }
        return refused;
    }

    /** Every call written with OVER under a node, the queries nested in it included. */
    private static void collectEverywhere(final ParseTree node, final List<ParserRuleContext> out) {
        if (!isTableFunctionCall(node) && (node instanceof FrostlakeParser.FunctionCallExprContext
                && ((FrostlakeParser.FunctionCallExprContext) node).overClause() != null
                || node instanceof FrostlakeParser.FunctionCallNamedArgsExprContext
                && ((FrostlakeParser.FunctionCallNamedArgsExprContext) node).overClause() != null
                || node instanceof FrostlakeParser.FunctionCallMixedArgsExprContext
                && ((FrostlakeParser.FunctionCallMixedArgsExprContext) node).overClause() != null)) {
            out.add((ParserRuleContext) node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectEverywhere(node.getChild(i), out);
        }
    }

    /** Where a call's OVER stands among the statement's tokens. */
    private static int overToken(final ParserRuleContext call) {
        final FrostlakeParser.OverClauseContext over = call instanceof FrostlakeParser.FunctionCallExprContext
            ? ((FrostlakeParser.FunctionCallExprContext) call).overClause() : overClause(call);
        return over.OVER().getSymbol().getTokenIndex();
    }

    /**
     * Refuses the first call written with OVER in these clauses, outside any query nested in them, whose function is
     * neither a window function nor an aggregate.
     *
     * @param clauses the clauses to search, in the order they are written; an absent one is null
     */
    void rejectKinds(final List<ParseTree> clauses) {
        for (final ParserRuleContext call : calls(clauses)) {
            final String canonical = functionName(call).getText().toUpperCase(Locale.ROOT);
            // A user's aggregate function takes no window (live-verified).
            if (!WindowFunctionNames.handles(canonical) && !functionRegistry.hasBuiltInAggregateFunction(canonical)) {
                throw new RuntimeException(SqlCompilationError.of("Invalid function type ["
                    + spelledName(functionName(call)) + "] for window function."));
            }
            rejectOrderedDistinctCount(canonical, call);
        }
    }

    /**
     * Refuses the first call written with OVER and named arguments that the account refuses, outside any query nested
     * in these clauses. A call of the select list is judged in the list's own item order, after the argument walk of
     * the items written before it, and ahead of the WHERE's types; one in another clause after the walk of the whole
     * list, and the caller judges those after the WHERE's types. Within the call the names written inside it come
     * first, then its count and its named arguments: {@code SELECT UPPER(1, 2), SUM(x => 1) OVER ()} is UPPER's
     * arity, {@code SELECT SUM(x => nosuch) OVER ()} is NOSUCH and {@code SELECT SUM(x => 1) OVER () FROM rt WHERE
     * n + TRUE = 1} SUM's refusal, while {@code SELECT n FROM rt WHERE n + TRUE = 1 QUALIFY SUM(x => 1) OVER () = 1}
     * is the '+' (all live-verified).
     *
     * @param clauses    the clauses to search, in the order they are judged; an absent one is null
     * @param selectList the select list whose items are walked ahead of a named call written in it
     * @param itemWalk   the strict argument walk those items take, over the query's relations
     */
    void rejectNamed(final List<ParseTree> clauses, final FrostlakeParser.SelectListContext selectList,
                     final ExpressionEvaluator itemWalk) {
        for (final ParserRuleContext call : calls(clauses)) {
            final String kind = functionName(call).getText().toUpperCase(Locale.ROOT);
            // A call of the wrong kind is refused for its kind, whatever its arguments look like.
            if (call instanceof FrostlakeParser.FunctionCallExprContext
                    || !WindowFunctionNames.handles(kind) && !functionRegistry.hasBuiltInAggregateFunction(kind)) {
                continue;
            }
            walkItemsBefore(call, selectList, itemWalk);
            walkNamesWithin(call, itemWalk);
            final String name = SqlIdentifiers.canonicalText(functionName(call).getText());
            // The account plans CONDITIONAL_CHANGE_EVENT over LAG before it counts the arguments, and
            // RATIO_TO_REPORT over SUM, whose count and refusal it reports.
            if (CONDITIONAL_CHANGE_EVENT.equals(name)) {
                throw positioned(call, "function LAG does not support named arguments");
            }
            final String planned = RATIO_TO_REPORT.equals(name) ? "SUM" : name;
            final List<ParserRuleContext> values = argumentValues(call);
            final String arity = arityRefusal(planned, values);
            if (arity != null) {
                throw positioned(call, arity);
            }
            if (NamedCallRewrite.plannedAsNthValue(name, overClause(call))) {
                throw positioned(call, "function NTH_VALUE does not support named arguments");
            }
            if (!NamedArgumentWindowFunctions.accepts(name)) {
                throw positioned(call, "function " + planned + " does not support named arguments");
            }
        }
    }

    /** Every call written with OVER in these clauses, outside a nested query, in written order. */
    private static List<ParserRuleContext> calls(final List<ParseTree> clauses) {
        final List<ParserRuleContext> calls = new ArrayList<>();
        for (final ParseTree clause : clauses) {
            if (clause != null) {
                collect(clause, calls);
            }
        }
        return calls;
    }

    /** The names written inside a named call — its values, then its window's keys — each at its own place. */
    private static void walkNamesWithin(final ParserRuleContext call, final ExpressionEvaluator itemWalk) {
        final List<ParserRuleContext> written = new ArrayList<>(argumentValues(call));
        final FrostlakeParser.OverClauseContext over = overClause(call);
        if (over.partitionByClause() != null) {
            written.addAll(KeyContexts.partitionKeys(over.partitionByClause()));
        }
        if (over.orderByClause() != null) {
            for (final FrostlakeParser.OrderItemContext item : over.orderByClause().orderItem()) {
                written.add(KeyContexts.of(item));
            }
        }
        for (final ParserRuleContext node : written) {
            if (node == null || node instanceof FrostlakeParser.SelectStatementContext) {
                continue;
            }
            final SourcePosition displaced = ExpressionSource.beginNested(new SourcePosition(
                node.getStart().getLine(), node.getStart().getCharPositionInLine()));
            try {
                itemWalk.validateColumnScope(ExpressionEvaluator.parse(ParseTreeText.getOriginalText(node)));
            } finally {
                ExpressionSource.end(displaced);
            }
        }
    }

    /**
     * Whether {@code node} holds a call written with OVER, outside a query nested in it — in any of its
     * spellings, the named ones included.
     *
     * @param node the clause or expression to search; null holds none
     * @return whether such a call is written there
     */
    static boolean writesOverCall(final ParseTree node) {
        if (node == null) {
            return false;
        }
        final List<ParserRuleContext> calls = new ArrayList<>();
        collect(node, calls);
        return !calls.isEmpty();
    }

    /**
     * The strict argument walk over the select items written before the one holding {@code call} — over every
     * item for a call in another clause, which waits for the whole select list — each at its own place and with
     * the aliases of the items before it exempt, as the windowed select list's walk runs.
     */
    static void walkItemsBefore(final ParserRuleContext call, final FrostlakeParser.SelectListContext selectList,
                                final ExpressionEvaluator itemWalk) {
        final FrostlakeParser.SelectItemContext holder = itemHolding(call, selectList);
        final Set<String> earlierOutputNames = new HashSet<>();
        itemWalk.setScopeExemptNames(earlierOutputNames);
        for (final FrostlakeParser.SelectItemContext item : selectList.selectItem()) {
            if (item == holder) {
                return;
            }
            if (!SelectItemAccessors.isExprItem(item)) {
                continue;
            }
            final ParserRuleContext itemExpr = SelectItemAccessors.getItemExpression(item);
            final SourcePosition displaced = ExpressionSource.beginNested(new SourcePosition(
                itemExpr.getStart().getLine(), itemExpr.getStart().getCharPositionInLine()));
            try {
                itemWalk.validateStrict(ExpressionEvaluator.parse(ParseTreeText.getOriginalText(itemExpr)));
            } finally {
                ExpressionSource.end(displaced);
            }
            final String alias = SelectItemAccessors.getItemAlias(item);
            if (alias != null) {
                earlierOutputNames.add(SqlIdentifiers.canonicalText(alias));
            }
        }
    }

    /** The item of {@code selectList} a call is written in, or null when it stands in another clause. */
    private static FrostlakeParser.SelectItemContext itemHolding(final ParserRuleContext call,
                                                                 final FrostlakeParser.SelectListContext selectList) {
        ParserRuleContext node = call;
        while (node != null) {
            if (node instanceof FrostlakeParser.SelectItemContext && node.getParent() == selectList) {
                return (FrostlakeParser.SelectItemContext) node;
            }
            node = node.getParent();
        }
        return null;
    }

    /** Every call written with OVER under {@code node}, outside a nested query, in written order. */
    /**
     * Whether a node is the call a TABLE( … ) source wraps: a table function's, whose OVER names the partitions
     * it reads its input by and is no window call (live-verified).
     */
    static boolean isTableFunctionCall(final ParseTree node) {
        return node instanceof ParserRuleContext
            && ((ParserRuleContext) node).getParent() instanceof FrostlakeParser.TableSourceContext
            && ((FrostlakeParser.TableSourceContext) ((ParserRuleContext) node).getParent()).TABLE() != null;
    }

    private static void collect(final ParseTree node, final List<ParserRuleContext> out) {
        if (node instanceof FrostlakeParser.SelectClauseContext) {
            return;
        }
        if (!isTableFunctionCall(node) && (node instanceof FrostlakeParser.FunctionCallExprContext
                && ((FrostlakeParser.FunctionCallExprContext) node).overClause() != null
                || node instanceof FrostlakeParser.FunctionCallNamedArgsExprContext
                && ((FrostlakeParser.FunctionCallNamedArgsExprContext) node).overClause() != null
                || node instanceof FrostlakeParser.FunctionCallMixedArgsExprContext
                && ((FrostlakeParser.FunctionCallMixedArgsExprContext) node).overClause() != null)) {
            out.add((ParserRuleContext) node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), out);
        }
    }

    private static FrostlakeParser.FunctionNameContext functionName(final ParserRuleContext call) {
        if (call instanceof FrostlakeParser.FunctionCallNamedArgsExprContext) {
            return ((FrostlakeParser.FunctionCallNamedArgsExprContext) call).functionName();
        }
        if (call instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            return ((FrostlakeParser.FunctionCallMixedArgsExprContext) call).functionName();
        }
        return ((FrostlakeParser.FunctionCallExprContext) call).functionName();
    }

    /** A named or mixed call's OVER clause. */
    private static FrostlakeParser.OverClauseContext overClause(final ParserRuleContext call) {
        if (call instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            return ((FrostlakeParser.FunctionCallMixedArgsExprContext) call).overClause();
        }
        return ((FrostlakeParser.FunctionCallNamedArgsExprContext) call).overClause();
    }

    /** A named or mixed call's argument values in written order, positional ones first as written. */
    private static List<ParserRuleContext> argumentValues(final ParserRuleContext call) {
        final List<ParserRuleContext> values = new ArrayList<>();
        final List<FrostlakeParser.NamedArgumentContext> named;
        if (call instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            final FrostlakeParser.FunctionCallMixedArgsExprContext mixed =
                (FrostlakeParser.FunctionCallMixedArgsExprContext) call;
            values.addAll(mixed.expression());
            named = mixed.namedArgument();
        } else {
            named = ((FrostlakeParser.FunctionCallNamedArgsExprContext) call).namedArgumentList().namedArgument();
        }
        for (final FrostlakeParser.NamedArgumentContext argument : named) {
            values.add(argument.expression() != null ? argument.expression()
                : argument.selectStatement() != null ? argument.selectStatement() : argument.argumentRow());
        }
        return values;
    }

    /**
     * The arity refusal the call earns with its named values counted as positional ones, or null when
     * the count is legal: a window-only function by its own measured arity, an aggregate by its declared
     * one (the ordered-value pair by its written minimum alone, as the plain call is judged).
     */
    private String arityRefusal(final String name, final List<ParserRuleContext> values) {
        final String echoed = echoedCall(name, values);
        if (WindowFunctionArity.isMeasured(name)) {
            return WindowFunctionArity.refusal(name, echoed, values.size());
        }
        final AggregateFunction aggregate = functionRegistry.getAggregateFunction(name);
        if (aggregate == null) {
            return null;
        }
        final boolean orderedValue = ORDERED_VALUE_AGGREGATES.contains(name);
        final int minimum = orderedValue ? 1 : aggregate.getMinArgCount();
        if (values.size() < minimum) {
            return "not enough arguments for function [" + echoed + "], expected " + minimum
                + ", got " + values.size();
        }
        if (!orderedValue && values.size() > aggregate.getMaxArgCount()) {
            return "too many arguments for function [" + echoed + "] expected " + aggregate.getMaxArgCount()
                + ", got " + values.size();
        }
        return null;
    }

    /** The call as its arity sentence echoes it: the name and its values, the argument names dropped. */
    private String echoedCall(final String name, final List<ParserRuleContext> values) {
        final StringBuilder text = new StringBuilder(name).append('(');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(echo.print(values.get(i)));
        }
        return text.append(')').toString();
    }

    /**
     * COUNT(DISTINCT a, b) over a window that orders its rows counts one argument alone, and refuses more at its OVER:
     * "too many arguments for function [Window function COUNT(DISTINCT ...)] expected 1, got 2" (live-verified over
     * an ordered, framed window); over an unordered window it is taken.
     */
    private static void rejectOrderedDistinctCount(final String canonical, final ParserRuleContext call) {
        if (!"COUNT".equals(canonical) || !(call instanceof FrostlakeParser.FunctionCallExprContext)) {
            return;
        }
        final FrostlakeParser.FunctionCallExprContext count = (FrostlakeParser.FunctionCallExprContext) call;
        final int arguments = count.functionArgList() == null ? 0 : count.functionArgList().functionArg().size();
        if (count.DISTINCT() == null || arguments < 2 || count.overClause() == null
                || count.overClause().orderByClause() == null) {
            return;
        }
        final Token over = count.overClause().OVER().getSymbol();
        throw new RuntimeException(SqlCompilationError.at(over.getLine(), over.getCharPositionInLine(),
            "too many arguments for function [Window function COUNT(DISTINCT ...)] expected 1, got " + arguments));
    }

    private static RuntimeException positioned(final ParserRuleContext call, final String detail) {
        return new RuntimeException(SqlCompilationError.at(
            call.getStart().getLine(), call.getStart().getCharPositionInLine(), detail));
    }

    /**
     * A call's name spelled the way a refusal echoes it — every dotted part read from the parse tree,
     * each folded to upper case unless it was written quoted, in which case it keeps its case and its
     * quotes. A name the grammar reaches by another route (the {@code IDENTIFIER('fn')} form, or a
     * keyword-headed call) has no identifier parts and is echoed as written.
     */
    private static String spelledName(final FrostlakeParser.FunctionNameContext name) {
        final List<FrostlakeParser.IdentifierContext> parts = name.identifier();
        if (parts == null || parts.isEmpty()) {
            return name.getText().toUpperCase(Locale.ROOT);
        }
        if (parts.size() == 1 && "GROUPING".equalsIgnoreCase(parts.get(0).getText())) {
            // The account knows GROUPING as GROUPING_ID wherever it names the call (live-verified).
            return "GROUPING_ID";
        }
        final StringBuilder spelled = new StringBuilder();
        for (final FrostlakeParser.IdentifierContext part : parts) {
            if (spelled.length() > 0) {
                spelled.append('.');
            }
            spelled.append(SqlIdentifiers.spellAsWritten(part.getText()));
        }
        return spelled.toString();
    }
}
