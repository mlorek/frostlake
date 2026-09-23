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

package dev.frostlake.parser;

import dev.frostlake.executor.LeadingCommentOffset;
import dev.frostlake.executor.SqlCompilationError;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * The shapes a name with an empty middle part, {@code db..t}, cannot take where it is written — live's
 * syntax errors, judged over the parse tree and the token stream. The grammar reads every name position with
 * the one name rule, which takes any number of parts after the empty one, so the shapes are refused here
 * instead:
 *
 * <pre>
 *   SELECT T..x FROM db..t            unexpected 'FROM'   a column reference needs its fourth part
 *   SELECT db..t.x.y FROM db..t       unexpected '.'      and takes no fifth, refused at its dot
 *   SELECT db..t.x.* FROM db..t       '.' then 'FROM'     a star over db..t.x, at its dot and after it
 *   CREATE TABLE db..t.c (x INT)      unexpected '.'      an object name ends with its third part
 *   SHOW COLUMNS IN db..t             unexpected '&lt;EOF&gt;' a bare scope, at the token after db..t
 *   SHOW COLUMNS IN TABLE db..t       unexpected '.'      a scope named with its kind, at the second dot
 * </pre>
 *
 * <p>★ A COLUMN REFERENCE over the empty part is {@code db..t.c}. With three parts, {@code t..c}, the token
 * after it is refused whatever it is — the FROM, a comma, an alias, a cast's {@code ::}, a bracket, the end
 * of the input. A fifth part is refused at its dot, and a sixth adds a second line at the dot after that;
 * live stops at two. A three-part reference leading an argument of a call, a bracket or a parenthesis written
 * in a select item adds a line at its first dot, and one that is a select item's CAST operand repeats its
 * line; live reports nothing after either.
 *
 * <p>★ A QUALIFIED STAR reads its name like a column reference: {@code db..t.*} is a star, {@code db..t.x.*}
 * is refused at the dot before the star and at the token after it — none after an EXCLUDE, the pattern after
 * an ILIKE — and {@code db..t.x.y.*} at its two last dots.
 *
 * <p>★ AN OBJECT NAME — a table, a view, a sequence, a FROM reference, the column of COMMENT ON COLUMN —
 * ends with the part after the empty one: a fourth part is refused at its dot, in one line however many
 * parts follow.
 *
 * <p>★ A BARE SHOW SCOPE, {@code IN db..t}, names a class, so live reads the word after it as the instance
 * of a class scope, {@code IN <class> <instance>}, refused when the statement runs. Without one it is refused
 * at the token after {@code db..t} — or past a LIMIT or STARTS that live takes for the instance, at the count
 * or the prefix. A scope naming its kind ({@code IN TABLE}, {@code IN SCHEMA}, {@code IN ACCOUNT}, …) is
 * refused at its second dot.
 *
 * <p>Every column reference and star refused in the statement is reported, in the order written, and a
 * statement the parser could not finish still has those written before its own fault reported first, its own
 * lines after them. Inside a Snowflake Scripting block, see {@link EmptyPartBlockLines}.
 */
public final class EmptySchemaPartSyntax {

    /** The most lines live reports for the dots past a column reference's fourth part. */
    private static final int MAX_EXTRA_PART_LINES = 2;

    private EmptySchemaPartSyntax() {
    }

    /**
     * Refuse a parsed script holding a name whose empty middle part its position does not take.
     *
     * @param script the parsed script
     * @param tokens the token stream it was parsed from
     * @param sql the script's source text
     */
    public static void requireWellFormed(final FrostlakeParser.SqlScriptContext script,
                                         final TokenStream tokens, final String sql) {
        if (script == null || tokens == null || !hasAdjacentDots(tokens)) {
            return;
        }
        final List<EmptyPartFault> faults = new ArrayList<>();
        collect(script, tokens, faults);
        if (!faults.isEmpty()) {
            final List<Token> reported = new ArrayList<>();
            report(faults, tokens, reported);
            refuse(reported, new ArrayList<String>(), sql);
        }
    }

    /**
     * Refuse a script the parser could not finish when such a name is written before the parser's own fault:
     * live reports the name first, then — unless the name's refusal is the last thing it reports — the
     * parser's lines.
     *
     * @param script the partial parse
     * @param tokens the token stream it was parsed from
     * @param sql the script's source text
     * @param listener the parse's error listener
     */
    public static void requireWellFormedBefore(final FrostlakeParser.SqlScriptContext script,
                                               final TokenStream tokens, final String sql,
                                               final SyntaxErrorListener listener) {
        if (script == null || tokens == null || !listener.hasErrors() || !hasAdjacentDots(tokens)) {
            return;
        }
        final List<String> parserLines = listener.reportedSyntaxLines();
        final int[] parseFaultAt = parserLines.isEmpty() ? null
            : SyntaxErrorListener.sentenceCoordinates(parserLines.get(0));
        if (parseFaultAt == null) {
            return;
        }
        final List<EmptyPartFault> written = new ArrayList<>();
        collect(script, tokens, written);
        final List<EmptyPartFault> before = new ArrayList<>();
        for (final EmptyPartFault fault : written) {
            if (precedes(fault.first(), parseFaultAt, sql)) {
                before.add(fault);
            }
        }
        if (before.isEmpty()) {
            return;
        }
        final List<Token> reported = new ArrayList<>();
        final boolean complete = report(before, tokens, reported);
        refuse(reported, complete ? new ArrayList<String>() : parserLines, sql);
    }

    /**
     * Whether two dots stand next to each other anywhere in the statement — the only way an empty part
     * can be written — so a statement without one skips the tree walk.
     */
    private static boolean hasAdjacentDots(final TokenStream tokens) {
        boolean previousWasDot = false;
        for (int i = 0; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            final boolean dot = token.getType() == FrostlakeLexer.DOT;
            if (dot && previousWasDot) {
                return true;
            }
            previousWasDot = dot;
        }
        return false;
    }

    /**
     * The tokens reported for the refusals, in order: the first one's lines inside a block, else every
     * column reference and star of the first refusal's statement until one live reports nothing after.
     *
     * @return whether the report is complete — nothing reported after it
     */
    private static boolean report(final List<EmptyPartFault> faults, final TokenStream tokens,
                                  final List<Token> reported) {
        final EmptyPartFault first = faults.get(0);
        if (first.shape() == EmptyPartShape.COLUMN || first.shape() == EmptyPartShape.COLUMN_IN_CALL
                || first.shape() == EmptyPartShape.COLUMN_IN_CAST) {
            final List<Token> stacked = EmptyPartBlockLines.stacked(first, tokens);
            if (stacked != null) {
                reported.addAll(stacked);
                return true;
            }
        }
        final FrostlakeParser.StatementContext statement = EmptyPartBlockLines.statementOf(first.name());
        for (final EmptyPartFault fault : faults) {
            if (EmptyPartBlockLines.statementOf(fault.name()) != statement) {
                return false;
            }
            reported.addAll(fault.refused());
            if (fault.closing() || fault.shape() == EmptyPartShape.NAME) {
                if (fault.shape() == EmptyPartShape.COLUMN_IN_CALL && inDerivedTable(fault.name())) {
                    // A derived table's select item reports its first line once more.
                    reported.add(fault.first());
                }
                return fault.closing();
            }
        }
        return false;
    }

    /** Collect the refusals at or under {@code node}, in the order written. */
    private static void collect(final ParseTree node, final TokenStream tokens, final List<EmptyPartFault> faults) {
        final EmptyPartFault own = faultOf(node, tokens);
        if (own != null) {
            faults.add(own);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), tokens, faults);
        }
    }

    private static EmptyPartFault faultOf(final ParseTree node, final TokenStream tokens) {
        if (node instanceof FrostlakeParser.QualifiedNameContext) {
            return faultOf((FrostlakeParser.QualifiedNameContext) node, tokens);
        }
        if (node instanceof FrostlakeParser.StarQualifiedNameContext) {
            return starFault((FrostlakeParser.StarQualifiedNameContext) node, tokens);
        }
        if (node instanceof FrostlakeParser.TableQualifiedNameContext) {
            final FrostlakeParser.TableQualifiedNameContext name = (FrostlakeParser.TableQualifiedNameContext) node;
            final int afterFirst = name.namePart().size();
            if (name.DOT().size() > afterFirst && afterFirst > 1) {
                return nameFault(one(name.DOT(2).getSymbol()), name);
            }
        }
        return null;
    }

    private static EmptyPartFault faultOf(final FrostlakeParser.QualifiedNameContext name, final TokenStream tokens) {
        // The parts written after the first; an empty middle part adds a dot without adding a part.
        final int afterFirst = name.namePart().size();
        if (name.DOT().size() <= afterFirst) {
            return null;
        }
        final ParserRuleContext owner = name.getParent();
        if (isColumnReference(owner)) {
            if (afterFirst == 1) {
                return columnFault(name, owner, tokens);
            }
            final List<Token> dots = new ArrayList<>();
            for (int i = 3; i < name.DOT().size() && dots.size() < MAX_EXTRA_PART_LINES; i++) {
                dots.add(name.DOT(i).getSymbol());
            }
            return dots.isEmpty() ? null : nameFault(dots, name);
        }
        if (owner instanceof FrostlakeParser.ObjectNameContext
                && owner.getParent() instanceof FrostlakeParser.ShowStatementContext) {
            if (!writtenRightAfterIn(tokens, name)) {
                return nameFault(one(name.DOT(1).getSymbol()), name);
            }
            final FrostlakeParser.ShowStatementContext show = (FrostlakeParser.ShowStatementContext) owner.getParent();
            return show.showInstanceName() != null ? null : nameFault(one(afterBareScope(show, tokens, name)), name);
        }
        return afterFirst > 1 ? nameFault(one(name.DOT(2).getSymbol()), name) : null;
    }

    /** A three-part column reference: refused at the token after it, with the lines its place adds. */
    private static EmptyPartFault columnFault(final FrostlakeParser.QualifiedNameContext name,
                                              final ParserRuleContext owner, final TokenStream tokens) {
        final Token next = nextSpoken(tokens, name.getStop().getTokenIndex());
        final ParserRuleContext cast = owner.getParent();
        if (cast instanceof FrostlakeParser.CastExprContext
                && ((FrostlakeParser.CastExprContext) cast).expression() == owner && inSelectItem(cast)) {
            return castFault(next, name, ((FrostlakeParser.CastExprContext) cast).RPAREN());
        }
        if (cast instanceof FrostlakeParser.TryCastExprContext
                && ((FrostlakeParser.TryCastExprContext) cast).expression() == owner && inSelectItem(cast)) {
            return castFault(next, name, ((FrostlakeParser.TryCastExprContext) cast).RPAREN());
        }
        final ParserRuleContext bracket = leadingArgumentBracket(owner);
        if (bracket != null && inSelectItem(bracket)) {
            final List<Token> lines = one(next);
            lines.add(name.DOT(0).getSymbol());
            return new EmptyPartFault(EmptyPartShape.COLUMN_IN_CALL, lines, name, null, true);
        }
        return new EmptyPartFault(EmptyPartShape.COLUMN, one(next), name, null, false);
    }

    private static EmptyPartFault castFault(final Token next, final FrostlakeParser.QualifiedNameContext name,
                                            final TerminalNode close) {
        final List<Token> lines = one(next);
        lines.add(next);
        return new EmptyPartFault(EmptyPartShape.COLUMN_IN_CAST, lines, name, close.getSymbol(), true);
    }

    /**
     * The call, bracket or parenthesis whose argument a column reference leads — reached through operators of
     * which it is the left-most operand — or null.
     */
    private static ParserRuleContext leadingArgumentBracket(final ParserRuleContext reference) {
        ParserRuleContext node = reference;
        while (true) {
            final ParserRuleContext parent = node.getParent();
            if (parent instanceof FrostlakeParser.FunctionArgContext) {
                final ParserRuleContext list = parent.getParent();
                return list == null ? null : list.getParent();
            }
            if (parent instanceof FrostlakeParser.ArrayElementContext) {
                final ParserRuleContext literal = parent.getParent();
                return literal == null ? null : literal.getParent();
            }
            if (parent instanceof FrostlakeParser.ParenExprContext) {
                return parent;
            }
            if (!isOperator(parent) || parent.getChild(0) != node) {
                return null;
            }
            node = parent;
        }
    }

    /**
     * Whether an expression stands in a select item through nothing but operators, in a query that is not a
     * scalar subquery or an IN subquery.
     */
    private static boolean inSelectItem(final ParserRuleContext expression) {
        ParserRuleContext node = expression;
        while (true) {
            final ParserRuleContext parent = node.getParent();
            if (parent instanceof FrostlakeParser.ExprItemContext) {
                for (ParserRuleContext above = parent.getParent(); above != null; above = above.getParent()) {
                    if (above instanceof FrostlakeParser.ScalarSubqueryExprContext
                            || above instanceof FrostlakeParser.InSubqueryExprContext
                            || above instanceof FrostlakeParser.TupleInSubqueryExprContext
                            || above instanceof FrostlakeParser.QuantifiedComparisonExprContext) {
                        return false;
                    }
                }
                return true;
            }
            if (!isOperator(parent)) {
                return false;
            }
            node = parent;
        }
    }

    /** Whether a node is an operator over its operands, or the pass-through between the expression tiers. */
    private static boolean isOperator(final ParserRuleContext node) {
        return node instanceof FrostlakeParser.ValueExprContext
            || node instanceof FrostlakeParser.NotExprContext
            || node instanceof FrostlakeParser.AndExprContext
            || node instanceof FrostlakeParser.OrExprContext
            || node instanceof FrostlakeParser.UnaryExprContext
            || node instanceof FrostlakeParser.ConcatExprContext
            || node instanceof FrostlakeParser.MultiplicativeExprContext
            || node instanceof FrostlakeParser.AdditiveExprContext
            || node instanceof FrostlakeParser.IsNullExprContext
            || node instanceof FrostlakeParser.BetweenExprContext
            || node instanceof FrostlakeParser.ComparisonExprContext
            || node instanceof FrostlakeParser.CastExpr2Context;
    }

    /** Whether a name stands in a select item of a derived table, FROM (SELECT …). */
    private static boolean inDerivedTable(final ParserRuleContext name) {
        ParserRuleContext node = name;
        while (node != null && !(node instanceof FrostlakeParser.SelectStatementContext)) {
            node = node.getParent();
        }
        return node != null && node.getParent() instanceof FrostlakeParser.TableSourceContext;
    }

    /**
     * A qualified star over a name with an empty middle part: {@code db..t.*} is well formed, {@code db..t.x.*}
     * is refused at the dot before the star and at the token after it, and a longer name at its two last dots.
     */
    private static EmptyPartFault starFault(final FrostlakeParser.StarQualifiedNameContext name,
                                            final TokenStream tokens) {
        final int afterFirst = name.namePart().size();
        final ParserRuleContext owner = name.getParent();
        if (name.DOT().size() <= afterFirst || afterFirst < 2 || owner == null) {
            return null;
        }
        final TerminalNode starDot = owner.getToken(FrostlakeLexer.DOT, 0);
        final TerminalNode star = owner.getToken(FrostlakeLexer.STAR, 0);
        if (starDot == null || star == null) {
            return null;
        }
        if (afterFirst > 2) {
            final List<Token> dots = one(name.DOT(3).getSymbol());
            dots.add(afterFirst > 3 ? name.DOT(4).getSymbol() : starDot.getSymbol());
            return nameFault(dots, name);
        }
        final List<Token> lines = one(starDot.getSymbol());
        final ParserRuleContext modifier = firstStarModifier(owner);
        if (modifier == null) {
            final Token next = nextSpoken(tokens, star.getSymbol().getTokenIndex());
            lines.add(next);
            return new EmptyPartFault(EmptyPartShape.STAR, lines, name, null,
                next != null && next.getType() == FrostlakeLexer.AS);
        }
        if (modifier.getStart().getType() == FrostlakeLexer.ILIKE
                && modifier.getToken(FrostlakeLexer.STRING_LITERAL, 0) != null) {
            // Live multiplies by ILIKE, read as a name, and refuses the pattern after it.
            lines.add(modifier.getToken(FrostlakeLexer.STRING_LITERAL, 0).getSymbol());
        }
        return new EmptyPartFault(EmptyPartShape.STAR, lines, name, null, false);
    }

    /** The first EXCLUDE, ILIKE, RENAME or REPLACE written after a star, or null. */
    private static ParserRuleContext firstStarModifier(final ParserRuleContext owner) {
        for (int i = 0; i < owner.getChildCount(); i++) {
            final ParseTree child = owner.getChild(i);
            if (child instanceof FrostlakeParser.StarModifierContext
                    || child instanceof FrostlakeParser.StarArgumentModifierContext) {
                return (ParserRuleContext) child;
            }
        }
        return null;
    }

    private static EmptyPartFault nameFault(final List<Token> refused, final ParserRuleContext name) {
        return new EmptyPartFault(EmptyPartShape.NAME, refused, name, null, false);
    }

    /**
     * The token refused after a bare scope written with no instance: the one after the name, unless the
     * listing's modifiers begin with a word live reads as the instance — {@code LIMIT 1} is refused at the
     * count, and {@code STARTS WITH 'x'} at the prefix, the WITH having begun a WITH PRIVILEGES.
     */
    private static Token afterBareScope(final FrostlakeParser.ShowStatementContext show, final TokenStream tokens,
                                        final FrostlakeParser.QualifiedNameContext name) {
        final Token next = nextSpoken(tokens, name.namePart(0).getStop().getTokenIndex());
        final FrostlakeParser.ShowTailContext tail = show.showTail();
        if (next == null || tail == null || tail.getStart() != next) {
            return next;
        }
        if (tail.STARTS() != null && tail.STARTS().getSymbol() == next) {
            return tail.STRING_LITERAL().isEmpty() ? next : tail.STRING_LITERAL(0).getSymbol();
        }
        if (tail.LIMIT() != null && tail.LIMIT().getSymbol() == next) {
            return tail.INTEGER_LITERAL() == null ? next : tail.INTEGER_LITERAL().getSymbol();
        }
        return next;
    }

    /** Whether a name stands where an expression reads a column — the positions that take db..t.c. */
    private static boolean isColumnReference(final ParserRuleContext owner) {
        return owner instanceof FrostlakeParser.QualifiedNameExprContext
            || owner instanceof FrostlakeParser.PriorExprContext
            || owner instanceof FrostlakeParser.ConnectByRootExprContext
            || owner instanceof FrostlakeParser.OuterJoinColumnExprContext;
    }

    /** Whether the token spoken just before the name is the IN of a SHOW scope, with no kind between. */
    private static boolean writtenRightAfterIn(final TokenStream tokens, final ParserRuleContext name) {
        for (int i = name.getStart().getTokenIndex() - 1; i >= 0; i--) {
            final Token token = tokens.get(i);
            if (token.getChannel() == Token.DEFAULT_CHANNEL) {
                return token.getType() == FrostlakeLexer.IN;
            }
        }
        return false;
    }

    /** The next default-channel token after token index {@code after}, the end of input included. */
    private static Token nextSpoken(final TokenStream tokens, final int after) {
        for (int i = after + 1; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (token.getChannel() == Token.DEFAULT_CHANNEL || token.getType() == Token.EOF) {
                return token;
            }
        }
        return null;
    }

    private static List<Token> one(final Token token) {
        final List<Token> tokens = new ArrayList<>();
        tokens.add(token);
        return tokens;
    }

    /** Whether a refused token stands before the place the parser's first line names. */
    private static boolean precedes(final Token token, final int[] parseFaultAt, final String sql) {
        final int[] shown = shownAt(token, sql);
        return shown[0] < parseFaultAt[0] || shown[0] == parseFaultAt[0] && shown[1] < parseFaultAt[1];
    }

    /** A refused token's line and position as a refusal prints them; a missing token is the end of the input. */
    private static int[] shownAt(final Token token, final String sql) {
        final boolean atEnd = token == null || token.getType() == Token.EOF;
        return atEnd
            ? LeadingCommentOffset.rebase(EndOfInput.line(sql), EndOfInput.position(sql))
            : LeadingCommentOffset.rebase(token.getLine(), token.getCharPositionInLine());
    }

    /** Live's refusal, one line per refused token, then the parser's own lines. */
    private static void refuse(final List<Token> refused, final List<String> parserLines, final String sql) {
        final List<String> lines = new ArrayList<>();
        for (final Token token : refused) {
            final boolean atEnd = token == null || token.getType() == Token.EOF;
            final int[] shown = shownAt(token, sql);
            lines.add("syntax error line " + shown[0] + " at position " + shown[1]
                + " unexpected '" + (atEnd ? "<EOF>" : token.getText()) + "'.");
        }
        lines.addAll(parserLines);
        final StringBuilder detail = new StringBuilder();
        for (final String line : lines) {
            if (detail.length() > 0) {
                detail.append('\n');
            }
            detail.append(line);
        }
        throw new SqlSyntaxException(SqlCompilationError.of(detail.toString()), lines, sql);
    }
}
