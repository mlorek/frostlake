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

/**
 * The shapes a name with an empty middle part, {@code db..t}, cannot take where it is written — live's
 * syntax errors, judged once the statement has parsed. The grammar reads every name position with the
 * one name rule, which takes any number of parts after the empty one, so the shapes are refused here
 * instead, over the parse tree and the token stream, once per parse:
 *
 * <pre>
 *   SELECT T..x FROM db..t            unexpected 'FROM'   a column reference needs its fourth part
 *   SELECT db..t.x.y FROM db..t       unexpected '.'      and takes no fifth, refused at its dot
 *   CREATE TABLE db..t.c (x INT)      unexpected '.'      an object name ends with its third part
 *   SHOW COLUMNS IN db..t             unexpected '&lt;EOF&gt;' a bare scope, at the token after db..t
 *   SHOW COLUMNS IN TABLE db..t       unexpected '.'      a scope named with its kind, at the second dot
 * </pre>
 *
 * <p>★ A COLUMN REFERENCE over the empty part is {@code db..t.c}. With three parts, {@code t..c}, the token
 * after it is refused whatever it is — the FROM, a comma, an alias, a cast's {@code ::}, a bracket, the end
 * of the input. A fifth part is refused at its dot, and a sixth adds a second line at the dot after that;
 * live stops at two.
 *
 * <p>★ AN OBJECT NAME — a table, a view, a sequence, a FROM reference, the column of COMMENT ON COLUMN —
 * ends with the part after the empty one: a fourth part is refused at its dot, in one line however many
 * parts follow.
 *
 * <p>★ A BARE SHOW SCOPE, {@code IN db..t}, is refused at the token after {@code db..t}, where a scope
 * naming its kind ({@code IN TABLE}, {@code IN SCHEMA}, …) is refused at its second dot. Live reads a WORD
 * after the bare name as the instance name of a class scope, {@code IN <class> <instance>}, which is not
 * modelled: that spelling is refused at the word.
 */
public final class EmptySchemaPartSyntax {

    /** The most lines live reports for the dots past a column reference's fourth part. */
    private static final int MAX_EXTRA_PART_LINES = 2;

    private EmptySchemaPartSyntax() {
    }

    /**
     * Refuse the first name, in the order written, whose empty middle part its position does not take.
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
        final List<Token> refused = firstRefusal(script, tokens);
        if (refused != null && !refused.isEmpty()) {
            refuse(refused, sql);
        }
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

    /** The tokens refused for the first offending name at or under {@code node}, or null for none. */
    private static List<Token> firstRefusal(final ParseTree node, final TokenStream tokens) {
        final List<Token> own = refusalOf(node, tokens);
        if (own != null) {
            return own;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final List<Token> inner = firstRefusal(node.getChild(i), tokens);
            if (inner != null) {
                return inner;
            }
        }
        return null;
    }

    private static List<Token> refusalOf(final ParseTree node, final TokenStream tokens) {
        if (node instanceof FrostlakeParser.QualifiedNameContext) {
            return refusalOf((FrostlakeParser.QualifiedNameContext) node, tokens);
        }
        if (node instanceof FrostlakeParser.TableQualifiedNameContext) {
            final FrostlakeParser.TableQualifiedNameContext name = (FrostlakeParser.TableQualifiedNameContext) node;
            final int afterFirst = name.namePart().size();
            if (name.DOT().size() > afterFirst && afterFirst > 1) {
                return one(name.DOT(2).getSymbol());
            }
        }
        return null;
    }

    private static List<Token> refusalOf(final FrostlakeParser.QualifiedNameContext name,
                                         final TokenStream tokens) {
        // The parts written after the first; an empty middle part adds a dot without adding a part.
        final int afterFirst = name.namePart().size();
        if (name.DOT().size() <= afterFirst) {
            return null;
        }
        final ParserRuleContext owner = name.getParent();
        if (isColumnReference(owner)) {
            if (afterFirst == 1) {
                return one(nextSpoken(tokens, name.getStop().getTokenIndex()));
            }
            final List<Token> dots = new ArrayList<>();
            for (int i = 3; i < name.DOT().size() && dots.size() < MAX_EXTRA_PART_LINES; i++) {
                dots.add(name.DOT(i).getSymbol());
            }
            return dots.isEmpty() ? null : dots;
        }
        if (owner instanceof FrostlakeParser.ShowStatementContext
                && ((FrostlakeParser.ShowStatementContext) owner).IN() != null) {
            return writtenRightAfterIn(tokens, name)
                ? one(nextSpoken(tokens, name.namePart(0).getStop().getTokenIndex()))
                : one(name.DOT(1).getSymbol());
        }
        return afterFirst > 1 ? one(name.DOT(2).getSymbol()) : null;
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

    /** Live's refusal, one line per refused token; a missing token is the end of the input. */
    private static void refuse(final List<Token> refused, final String sql) {
        final List<String> lines = new ArrayList<>();
        for (final Token token : refused) {
            final boolean atEnd = token == null || token.getType() == Token.EOF;
            final int[] shown = atEnd
                ? LeadingCommentOffset.rebase(EndOfInput.line(sql), EndOfInput.position(sql))
                : LeadingCommentOffset.rebase(token.getLine(), token.getCharPositionInLine());
            lines.add("syntax error line " + shown[0] + " at position " + shown[1]
                + " unexpected '" + (atEnd ? "<EOF>" : token.getText()) + "'.");
        }
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
