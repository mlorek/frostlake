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
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;

/**
 * A positional argument written after a named one. The account reads such a call whole and refuses it while the
 * statement compiles, before any name in it is looked up — a table that is not there, a function nobody made —
 * and after every syntax error (live-verified):
 *
 * <pre>
 *   SELECT n1(a =&gt; 1, 2)                    error line 1 at position 18
 *                                           positional argument cannot follow named argument in function call 'N1'
 *   SELECT UPPER(x =&gt; 'a', 'b')             … in function call 'UPPER'    a built-in, a user's and an unknown name alike
 *   SELECT test_schema."n1"(a =&gt; 1, 2)      … in function call 'n1'       the name's last part, as it resolves
 *   SELECT n1(a =&gt; 1, 1 + 1)                at the '+': the argument's own place (see {@link ExpressionPlace})
 *   SELECT n1(a =&gt; 1, DATE '2020-01-01')    at the string; a COLLATE at the word, a NOT IN list at the NOT and an
 *                                           IS DISTINCT FROM at the IS
 *   SELECT n1(a =&gt; 1, t1.*)                 at the T1; a bare star at line 0, position 0
 *   SELECT n1(a =&gt; UPPER(x =&gt; 'a', 'b'), 2) the inner call's: a call is judged after the calls it holds
 * </pre>
 *
 * <p>Of several such calls the first in the text is the one refused, a WITH clause's queries read after the
 * statement they head. The call a {@code TABLE( … )} names is the table function's to judge, which refuses a mix
 * of the two ways in words of its own, so it is passed over here.
 */
public final class StrayArguments {

    private StrayArguments() {
    }

    /**
     * Refuse the first call under {@code root} that writes a positional argument after a named one.
     *
     * @param root a parsed text; null is left alone
     */
    public static void require(final ParseTree root) {
        if (!(root instanceof ParserRuleContext)) {
            return;
        }
        final ParserRuleContext node = (ParserRuleContext) root;
        if (node.children == null) {
            return;
        }
        ParseTree heading = null;
        for (final ParseTree child : node.children) {
            if (child instanceof FrostlakeParser.WithClauseContext) {
                heading = child;
            } else {
                require(child);
            }
        }
        require(heading);
        final FrostlakeParser.StrayArgumentsContext stray = of(node);
        if (stray != null && !namesATableFunction(node)) {
            throw new RuntimeException(refusal(node, stray));
        }
    }

    /**
     * What a call writes after its named arguments.
     *
     * @param call a call as parsed
     * @return its trailing arguments, or null when it writes none or is no call
     */
    public static FrostlakeParser.StrayArgumentsContext of(final ParseTree call) {
        if (call instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            return ((FrostlakeParser.FunctionCallMixedArgsExprContext) call).strayArguments();
        }
        if (call instanceof FrostlakeParser.FunctionCallNamedArgsExprContext) {
            return ((FrostlakeParser.FunctionCallNamedArgsExprContext) call).strayArguments();
        }
        return null;
    }

    /**
     * The refusal of a call for its first positional argument after a named one, placed at that argument.
     *
     * @param call  the call
     * @param stray what it writes after its named arguments
     * @return the refusal
     */
    public static String refusal(final ParserRuleContext call, final FrostlakeParser.StrayArgumentsContext stray) {
        final String sentence = "positional argument cannot follow named argument in function call '"
            + calledName(call) + "'";
        if (stray.getChild(0) instanceof FrostlakeParser.MixedStarArgumentContext) {
            final FrostlakeParser.MixedStarArgumentContext star =
                (FrostlakeParser.MixedStarArgumentContext) stray.getChild(0);
            if (star.starQualifiedName() == null) {
                // A bare star stands nowhere the account tells.
                return SqlCompilationError.at(0, 0, sentence);
            }
            final Token qualifier = star.getStart();
            final SourcePosition placed = ExpressionSource.place(qualifier.getLine(),
                qualifier.getCharPositionInLine());
            return SqlCompilationError.at(placed.getLine(), placed.getCharPositionInLine(), sentence);
        }
        final FrostlakeParser.BooleanExprContext argument = stray.booleanExpr(0);
        SourcePosition at = windowPlace(argument);
        if (at == null) {
            at = operatorPlace(argument);
        }
        if (at == null) {
            try {
                at = ExpressionPlace.of(new ExpressionAstBuilder().visit(argument));
            } catch (final RuntimeException unread) {
                // An argument this builder does not read keeps the place of its first token.
                at = null;
            }
        }
        final Token first = argument.getStart();
        final SourcePosition within = at != null ? at
            : new SourcePosition(first.getLine(), first.getCharPositionInLine());
        final SourcePosition placed = ExpressionSource.place(within.getLine(), within.getCharPositionInLine());
        return SqlCompilationError.at(placed.getLine(), placed.getCharPositionInLine(), sentence);
    }

    /**
     * Where an argument of four shapes stands, which the account tells by a token of its own: a typed literal at
     * its string, a COLLATE at the word, a NOT IN list at the NOT and an IS DISTINCT FROM at the IS; null for any
     * other argument.
     */
    private static SourcePosition operatorPlace(final FrostlakeParser.BooleanExprContext argument) {
        if (!(argument instanceof FrostlakeParser.ValueExprContext)) {
            return null;
        }
        final FrostlakeParser.ExpressionContext value = ((FrostlakeParser.ValueExprContext) argument).expression();
        final TerminalNode token;
        if (value instanceof FrostlakeParser.TypedDateTimeLiteralExprContext) {
            token = ((FrostlakeParser.TypedDateTimeLiteralExprContext) value).STRING_LITERAL();
        } else if (value instanceof FrostlakeParser.CollateExprContext) {
            token = ((FrostlakeParser.CollateExprContext) value).COLLATE();
        } else if (value instanceof FrostlakeParser.InListExprContext) {
            token = ((FrostlakeParser.InListExprContext) value).NOT();
        } else if (value instanceof FrostlakeParser.IsDistinctExprContext) {
            token = ((FrostlakeParser.IsDistinctExprContext) value).IS();
        } else {
            token = null;
        }
        return token == null ? null
            : new SourcePosition(token.getSymbol().getLine(), token.getSymbol().getCharPositionInLine());
    }

    /** The OVER of an argument that is a call over a window, which is where such a call stands; null for any other. */
    private static SourcePosition windowPlace(final FrostlakeParser.BooleanExprContext argument) {
        if (!(argument instanceof FrostlakeParser.ValueExprContext)) {
            return null;
        }
        final FrostlakeParser.ExpressionContext value = ((FrostlakeParser.ValueExprContext) argument).expression();
        if (!(value instanceof FrostlakeParser.FunctionCallExprContext)
                || ((FrostlakeParser.FunctionCallExprContext) value).overClause() == null) {
            return null;
        }
        final Token over = ((FrostlakeParser.FunctionCallExprContext) value).overClause().getStart();
        return new SourcePosition(over.getLine(), over.getCharPositionInLine());
    }

    /** The last part of the name a call writes, as it resolves: upper-cased unless it is quoted. */
    private static String calledName(final ParserRuleContext call) {
        final FrostlakeParser.FunctionNameContext name = call instanceof FrostlakeParser.FunctionCallMixedArgsExprContext
            ? ((FrostlakeParser.FunctionCallMixedArgsExprContext) call).functionName()
            : ((FrostlakeParser.FunctionCallNamedArgsExprContext) call).functionName();
        final Token last = name.getStop();
        return SqlIdentifiers.canonicalText(last.getText());
    }

    /** Whether a call is the whole operand of a {@code TABLE( … )} in a FROM clause. */
    private static boolean namesATableFunction(final ParserRuleContext call) {
        final ParserRuleContext parent = call.getParent();
        return parent instanceof FrostlakeParser.TableSourceContext
            && ((FrostlakeParser.TableSourceContext) parent).TABLE() != null
            && ((FrostlakeParser.TableSourceContext) parent).expression() == call;
    }

    /**
     * The arguments a call writes after its named ones, in the order written.
     *
     * @param stray the trailing arguments, or null
     * @param out   the list each is added to: a {@code NamedArgumentContext}, a {@code BooleanExprContext} or a
     *              {@code MixedStarArgumentContext}
     */
    public static void collect(final FrostlakeParser.StrayArgumentsContext stray, final List<ParserRuleContext> out) {
        if (stray == null || stray.children == null) {
            return;
        }
        for (final ParseTree child : stray.children) {
            if (child instanceof FrostlakeParser.NamedArgumentContext
                    || child instanceof FrostlakeParser.BooleanExprContext
                    || child instanceof FrostlakeParser.MixedStarArgumentContext) {
                out.add((ParserRuleContext) child);
            }
        }
    }
}
