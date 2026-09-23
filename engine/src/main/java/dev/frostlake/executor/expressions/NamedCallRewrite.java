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
import dev.frostlake.functions.window.NamedArgumentWindowFunctions;
import dev.frostlake.parser.FrostlakeParser;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * A call written with named arguments that the account answers exactly as the positional call its values spell —
 * the names ignored, the values taken in the order written (see {@link NamedArgumentWindowFunctions}) — is turned
 * into that positional call in the parse tree, so every stage that reads calls off the tree (the aggregate and
 * window stages, the plan-time rules, the echoes) meets the call it answers:
 *
 * <pre>
 *   MEDIAN(x =&gt; n) OVER ()                 MEDIAN(n) OVER ()
 *   CORR(n, y =&gt; f)                        CORR(n, f)
 *   FIRST_VALUE(x =&gt; n) IGNORE NULLS OVER (ORDER BY n)   FIRST_VALUE(n) IGNORE NULLS OVER (ORDER BY n)
 *   FIRST_VALUE(x =&gt; n)                    FIRST_VALUE(n): Missing window specification for function [FIRST_VALUE(RT.N)].
 * </pre>
 *
 * <p>The new node spans the tokens of the call as written, so its text — the derived column name
 * {@code MEDIAN(X => N) OVER ()} and the key the window and aggregate stages share with the expression tree — is the
 * call as written. A call the account refuses keeps its named form and is refused where it is judged; so does a
 * value that is a bare query, which has no positional spelling.
 */
public final class NamedCallRewrite {

    private NamedCallRewrite() {
    }

    /**
     * Rewrite every answered named call under {@code root}, in place.
     *
     * @param root a parse tree; null is left alone
     */
    public static void apply(final ParseTree root) {
        if (root == null) {
            return;
        }
        final Deque<ParseTree> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            final ParseTree node = pending.pop();
            if (!(node instanceof ParserRuleContext)) {
                continue;
            }
            final ParserRuleContext parent = (ParserRuleContext) node;
            if (parent.children == null) {
                continue;
            }
            for (int i = 0; i < parent.children.size(); i++) {
                final ParseTree child = parent.children.get(i);
                final ParseTree positional = positionalForm(child);
                if (positional != null) {
                    parent.children.set(i, positional);
                    ((ParserRuleContext) positional).setParent(parent);
                }
                pending.push(parent.children.get(i));
            }
        }
    }

    /**
     * Whether a ROWS frame makes a named FIRST_VALUE or LAST_VALUE the NTH_VALUE the account plans it as, which takes
     * no named arguments: FIRST_VALUE over a frame from a bounded offset or the current row to a row at or before the
     * current one, LAST_VALUE over a frame from the current row or after it to a bounded offset after it.
     *
     * <pre>
     *   FIRST_VALUE(x =&gt; n) OVER (ORDER BY n ROWS BETWEEN 1 PRECEDING AND CURRENT ROW)   NTH_VALUE
     *   FIRST_VALUE(x =&gt; n) OVER (ORDER BY n ROWS 1 PRECEDING)                           NTH_VALUE
     *   FIRST_VALUE(x =&gt; n) OVER (ORDER BY n ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING)    answered
     *   LAST_VALUE(x =&gt; n) OVER (ORDER BY n ROWS BETWEEN CURRENT ROW AND 1 FOLLOWING)     NTH_VALUE
     *   LAST_VALUE(x =&gt; n) OVER (ORDER BY n ROWS BETWEEN CURRENT ROW AND CURRENT ROW)     answered
     * </pre>
     *
     * @param name the call's canonical name
     * @param over its OVER clause, or null
     * @return whether the frame is one of those
     */
    public static boolean plannedAsNthValue(final String name, final FrostlakeParser.OverClauseContext over) {
        if (over == null || over.windowFrame() == null || over.windowFrame().ROWS() == null) {
            return false;
        }
        final List<FrostlakeParser.FrameBoundContext> bounds = over.windowFrame().frameBound();
        final FrostlakeParser.FrameBoundContext start = bounds.get(0);
        final FrostlakeParser.FrameBoundContext end = bounds.size() > 1 ? bounds.get(1) : null;
        if ("FIRST_VALUE".equals(name)) {
            final boolean endsAtOrBefore = end == null || end.CURRENT() != null
                || end.expression() != null && end.PRECEDING() != null;
            return endsAtOrBefore && start.UNBOUNDED() == null
                && (start.CURRENT() != null || start.PRECEDING() != null);
        }
        if ("LAST_VALUE".equals(name)) {
            return end != null && end.expression() != null && end.FOLLOWING() != null
                && (start.CURRENT() != null || start.expression() != null && start.FOLLOWING() != null);
        }
        return false;
    }

    /** The positional call a named call is answered as, or null when it keeps its named form. */
    private static ParseTree positionalForm(final ParseTree node) {
        if (node instanceof FrostlakeParser.FunctionCallNamedArgsExprContext) {
            final FrostlakeParser.FunctionCallNamedArgsExprContext call =
                (FrostlakeParser.FunctionCallNamedArgsExprContext) node;
            final List<ParserRuleContext> values = new ArrayList<>();
            for (final FrostlakeParser.NamedArgumentContext argument : call.namedArgumentList().namedArgument()) {
                values.add(argument.expression());
            }
            return answered(call.functionName(), values, call.overClause())
                ? positional(call, call.functionName(), values, call.namedArgumentList().COMMA()) : null;
        }
        if (node instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            final FrostlakeParser.FunctionCallMixedArgsExprContext call =
                (FrostlakeParser.FunctionCallMixedArgsExprContext) node;
            final List<ParserRuleContext> values = new ArrayList<ParserRuleContext>(call.expression());
            for (final FrostlakeParser.NamedArgumentContext argument : call.namedArgument()) {
                values.add(argument.expression());
            }
            return answered(call.functionName(), values, call.overClause())
                ? positional(call, call.functionName(), values, call.COMMA()) : null;
        }
        return null;
    }

    /** Whether the account answers this named call as its positional form. */
    private static boolean answered(final FrostlakeParser.FunctionNameContext name,
                                    final List<ParserRuleContext> values,
                                    final FrostlakeParser.OverClauseContext over) {
        if (name.identifierArgument() != null || name.identifier().size() != 1) {
            return false;
        }
        for (final ParserRuleContext value : values) {
            if (value == null) {
                return false;
            }
        }
        final String canonical = SqlIdentifiers.canonicalText(name.getText());
        if (over != null) {
            return NamedArgumentWindowFunctions.accepts(canonical) && !plannedAsNthValue(canonical, over);
        }
        return NamedArgumentWindowFunctions.acceptsPlain(canonical)
            || "FIRST_VALUE".equals(canonical) || "LAST_VALUE".equals(canonical);
    }

    /**
     * The positional call: the named call's own name, quantifier, null treatments, WITHIN GROUP and OVER, its values
     * as the argument list, spanning the tokens of the call as written.
     */
    private static FrostlakeParser.FunctionCallExprContext positional(final FrostlakeParser.ExpressionContext named,
                                                                   final FrostlakeParser.FunctionNameContext name,
                                                                   final List<ParserRuleContext> values,
                                                                   final List<TerminalNode> commas) {
        final FrostlakeParser.FunctionCallExprContext call = new FrostlakeParser.FunctionCallExprContext(named);
        call.children = new ArrayList<>();
        boolean argumentsPlaced = false;
        for (final ParseTree part : new ArrayList<ParseTree>(named.children)) {
            if (part instanceof FrostlakeParser.NamedArgumentListContext
                    || part instanceof FrostlakeParser.NamedArgumentContext
                    || part instanceof FrostlakeParser.ExpressionContext
                    || part instanceof TerminalNode
                        && ((TerminalNode) part).getSymbol().getType() == FrostlakeParser.COMMA) {
                if (!argumentsPlaced) {
                    adopt(call, argumentList(call, values, commas));
                    argumentsPlaced = true;
                }
                continue;
            }
            adopt(call, part);
        }
        return call;
    }

    /** The argument list the values make, each value a boolean-expression argument, separated by the commas. */
    private static FrostlakeParser.FunctionArgListContext argumentList(final ParserRuleContext parent,
                                                                    final List<ParserRuleContext> values,
                                                                    final List<TerminalNode> commas) {
        final FrostlakeParser.FunctionArgListContext list = new FrostlakeParser.FunctionArgListContext(parent, -1);
        list.start = values.get(0).getStart();
        list.stop = values.get(values.size() - 1).getStop();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0 && i - 1 < commas.size()) {
                adopt(list, commas.get(i - 1));
            }
            final ParserRuleContext value = values.get(i);
            final FrostlakeParser.FunctionArgContext argument = new FrostlakeParser.FunctionArgContext(list, -1);
            argument.start = value.getStart();
            argument.stop = value.getStop();
            final FrostlakeParser.ValueExprContext wrapped =
                new FrostlakeParser.ValueExprContext(new FrostlakeParser.BooleanExprContext(argument, -1));
            wrapped.start = value.getStart();
            wrapped.stop = value.getStop();
            adopt(wrapped, value);
            adopt(argument, wrapped);
            adopt(list, argument);
        }
        return list;
    }

    /** Add {@code child} under {@code parent}, re-pointing its parent. */
    private static void adopt(final ParserRuleContext parent, final ParseTree child) {
        if (parent.children == null) {
            parent.children = new ArrayList<>();
        }
        parent.children.add(child);
        child.setParent(parent);
    }
}
