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
import org.antlr.v4.runtime.misc.IntervalSet;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The comma of a script's SELECT … INTO target list that the account refuses in place of a fault after it. Reading
 * the list, the account decides at each comma from the tokens that follow: a target, and after a plain name either
 * another comma or a word the list may end at. When they are not, the comma itself is refused, as the statement's one
 * line: {@code SELECT 1 INTO t, u VALUES (1)} is ',' at 15, {@code INTO t, u, v VALUES (1)} the second comma, and
 * {@code INTO t, 1} and {@code INTO t,, u} the first — while a bind target reads one token further, so {@code INTO
 * :t, :u VALUES (1)} is 'VALUES' (all live-verified).
 *
 * <p>A statement of a BEGIN … END block or of an exception handler is refused at the same comma, and the recovery then
 * takes the first name after it and refuses the token after that, as for a block statement run into the next one; the
 * report ends there: {@code BEGIN SELECT 1 INTO t, u VALUES (1); RETURN 1 1; END} is ',' and 'VALUES', {@code INTO t,,
 * u;} ',' and the block's END, and {@code INTO t, 1;} the ',' alone (all live-verified).
 *
 * <p>In the body of a control construct standing in the script's own block or one of its handlers — the ',' first, and
 * no more when no name follows it before the statement's semicolon — the recovery goes on by the construct (all
 * live-verified):
 *
 * <ul>
 *   <li>a LOOP, WHILE or FOR body refuses the token after that name and then the token after the first name from there
 *       on: {@code BEGIN LOOP SELECT 1 INTO t, u x; END LOOP; END} is ',', 'x' and the LOOP's END. With no such name the
 *       recovery reads on to the construct's own END when the statement is the last before it, as for a statement run
 *       into the next one ({@code INTO t, u VALUES (1);} is ',', 'VALUES' and the block's END) — not in a FOR — and
 *       after a semicolon right after the name it always does ({@code INTO t,, u;} is ',' and the block's END);</li>
 *   <li>an IF, CASE or REPEAT body passes that name over: a name after it is taken and the token after that refused
 *       ({@code INTO t, u x;} is ',' and the construct's END), and otherwise the recovery reads on to the construct's
 *       own END when the statement is the last before it ({@code BEGIN IF (TRUE) THEN SELECT 1 INTO t, u VALUES (1);
 *       END IF; END} is ',' and the block's END) — not in a REPEAT, which UNTIL closes — and stops otherwise.</li>
 * </ul>
 *
 * <p>A construct inside another construct or inside a nested block is refused at the ',' alone — the recovery reaches
 * further out there — but for an IF or CASE branch whose first name after the comma is followed by another name,
 * where the recovery reads on to that construct's own END and refuses what follows it ({@code BEGIN LOOP IF (TRUE)
 * THEN SELECT 1 INTO t, u x; END IF; END LOOP; END} is ',' and the LOOP's END).
 */
public final class IntoListLookahead {

    private static final IntervalSet NAME_START =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]);

    private IntoListLookahead() {
    }

    /**
     * Refuse the first such comma of the script's own SELECT … INTO statements, or of its blocks' statements, when no
     * parse fault stands before it.
     *
     * @param script   the parsed script
     * @param tokens   the token stream the script was parsed from
     * @param sql      the script's source text
     * @param listener the parse's error listener
     */
    public static void requireBefore(final FrostlakeParser.SqlScriptContext script, final TokenStream tokens,
                                     final String sql, final SyntaxErrorListener listener) {
        if (script == null || tokens == null) {
            return;
        }
        final List<FrostlakeParser.SelectIntoStatementContext> statements = new ArrayList<>();
        collect(script, statements);
        for (final FrostlakeParser.SelectIntoStatementContext statement : statements) {
            final ParserRuleContext owner = listOwner(statement);
            final boolean inBlock = owner instanceof FrostlakeParser.BeginEndBlockContext
                || owner instanceof FrostlakeParser.ExceptionHandlerContext;
            final boolean inBody = isConstruct(owner);
            if (statement.INTO() == null || !inBlock && !inBody && !atScriptLevel(statement)) {
                continue;
            }
            final Token comma = deadComma(tokens, statement.INTO().getSymbol());
            if (comma != null && !faultBefore(listener, comma)) {
                final List<Token> refused = new ArrayList<>();
                refused.add(comma);
                if (inBlock) {
                    refused.add(SyntaxErrorListener.afterFirstName(tokens, comma));
                } else if (inBody && inOutermostBlock(owner)) {
                    refused.addAll(inConstructBody(tokens, owner, comma));
                } else if (owner instanceof FrostlakeParser.IfStatementContext
                        || owner instanceof FrostlakeParser.CaseStatementContext) {
                    refused.add(inNestedBranch(tokens, owner, comma));
                }
                final List<String> lines = new ArrayList<>();
                for (final Token token : refused) {
                    if (token != null) {
                        lines.add(SyntaxErrorListener.sentence(token));
                    }
                }
                throw new SqlSyntaxException(SqlCompilationError.of(String.join("\n", lines)), lines, sql);
            }
        }
    }

    /** Every SELECT … INTO statement under {@code tree}, in the order of the text. */
    private static void collect(final ParseTree tree, final List<FrostlakeParser.SelectIntoStatementContext> found) {
        if (tree instanceof FrostlakeParser.SelectIntoStatementContext) {
            found.add((FrostlakeParser.SelectIntoStatementContext) tree);
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            collect(tree.getChild(i), found);
        }
    }

    /** Whether the statement is one of the script's own statements. */
    private static boolean atScriptLevel(final FrostlakeParser.SelectIntoStatementContext statement) {
        final ParserRuleContext chain = grandParent(statement, 2);
        return chain instanceof FrostlakeParser.FlowChainContext
            && chain.getParent() instanceof FrostlakeParser.SqlScriptContext;
    }

    /** What holds the statement list the statement stands in — a block, a handler or a construct — or null. */
    private static ParserRuleContext listOwner(final FrostlakeParser.SelectIntoStatementContext statement) {
        final ParserRuleContext list = grandParent(statement, 2);
        return list instanceof FrostlakeParser.StatementListContext ? list.getParent() : null;
    }

    /**
     * The tokens refused after the dead comma of a statement in the body of {@code construct}, a control construct
     * standing in the script's own block — see the class comment.
     */
    private static List<Token> inConstructBody(final TokenStream tokens, final ParserRuleContext construct,
                                               final Token comma) {
        final List<Token> refused = new ArrayList<>();
        final Token name = SyntaxErrorListener.firstNameBeforeSemicolon(tokens, comma);
        final Token after = name == null ? null : nextSpoken(tokens, name.getTokenIndex());
        if (after == null || after.getType() == Token.EOF || after.getType() == FrostlakeLexer.END) {
            return refused;
        }
        if (construct instanceof FrostlakeParser.LoopStatementContext
                || construct instanceof FrostlakeParser.WhileStatementContext
                || construct instanceof FrostlakeParser.ForStatementContext) {
            if (after.getType() == FrostlakeLexer.SEMI) {
                refused.add(SyntaxErrorListener.afterEnclosingConstruct(tokens, construct.getStart()));
                return refused;
            }
            refused.add(after);
            final Token again = SyntaxErrorListener.afterFirstName(tokens, after);
            if (again != null) {
                refused.add(again);
            } else if (!(construct instanceof FrostlakeParser.ForStatementContext) && closesBody(tokens, after)) {
                refused.add(SyntaxErrorListener.afterEnclosingConstruct(tokens, construct.getStart()));
            }
            return refused;
        }
        if (SyntaxErrorListener.isNameLike(after)) {
            refused.add(SyntaxErrorListener.afterFirstName(tokens, after));
        } else if (!(construct instanceof FrostlakeParser.RepeatStatementContext) && closesBody(tokens, after)) {
            refused.add(SyntaxErrorListener.afterEnclosingConstruct(tokens, construct.getStart()));
        }
        return refused;
    }

    /**
     * The token refused after the dead comma of a statement in an IF or CASE branch nested in another construct or in
     * an inner block: when a name follows the first name after the comma, the token after the construct — see the
     * class comment — and otherwise none.
     */
    private static Token inNestedBranch(final TokenStream tokens, final ParserRuleContext construct,
                                        final Token comma) {
        final Token name = SyntaxErrorListener.firstNameBeforeSemicolon(tokens, comma);
        final Token after = name == null ? null : nextSpoken(tokens, name.getTokenIndex());
        return after == null || after.getType() == FrostlakeLexer.END || !SyntaxErrorListener.isNameLike(after) ? null
            : SyntaxErrorListener.afterEnclosingConstruct(tokens, construct.getStart());
    }

    /** Whether the statement holding {@code token} is the last of its body: its semicolon is followed by an END. */
    private static boolean closesBody(final TokenStream tokens, final Token token) {
        Token end = token;
        while (end != null && end.getType() != FrostlakeLexer.SEMI && end.getType() != Token.EOF) {
            end = nextSpoken(tokens, end.getTokenIndex());
        }
        final Token next = end == null || end.getType() != FrostlakeLexer.SEMI ? null
            : nextSpoken(tokens, end.getTokenIndex());
        return next != null && next.getType() == FrostlakeLexer.END;
    }

    /** Whether {@code construct} stands in the statement list of the script's own block or of one of its handlers. */
    private static boolean inOutermostBlock(final ParserRuleContext construct) {
        ParserRuleContext up = construct.getParent();
        while (up != null && !(up instanceof FrostlakeParser.StatementListContext)) {
            up = up.getParent();
        }
        ParserRuleContext block = up == null ? null : up.getParent();
        if (block instanceof FrostlakeParser.ExceptionHandlerContext) {
            while (block != null && !(block instanceof FrostlakeParser.BeginEndBlockContext)) {
                block = block.getParent();
            }
        }
        if (!(block instanceof FrostlakeParser.BeginEndBlockContext)) {
            return false;
        }
        for (ParserRuleContext above = block.getParent(); above != null; above = above.getParent()) {
            if (above instanceof FrostlakeParser.BeginEndBlockContext) {
                return false;
            }
        }
        return true;
    }

    /** Whether {@code owner} is an IF, LOOP, CASE, FOR, WHILE or REPEAT statement, whose END the recovery reads. */
    private static boolean isConstruct(final ParserRuleContext owner) {
        return owner instanceof FrostlakeParser.IfStatementContext || owner instanceof FrostlakeParser.LoopStatementContext
            || owner instanceof FrostlakeParser.CaseStatementContext || owner instanceof FrostlakeParser.ForStatementContext
            || owner instanceof FrostlakeParser.WhileStatementContext
            || owner instanceof FrostlakeParser.RepeatStatementContext;
    }

    /** The context {@code levels} above the statement's own procedural and statement contexts, or null. */
    private static ParserRuleContext grandParent(final ParserRuleContext statement, final int levels) {
        ParserRuleContext up = statement.getParent();
        for (int i = 0; i < levels && up != null; i++) {
            up = up.getParent();
        }
        return up;
    }

    /** The comma of the list after {@code into} whose continuation is no target list, or null. */
    private static Token deadComma(final TokenStream tokens, final Token into) {
        Token target = nextSpoken(tokens, into.getTokenIndex());
        if (target == null || !startsTarget(target)) {
            return null;
        }
        Token after = pastTarget(tokens, target);
        while (after != null && after.getType() == FrostlakeLexer.COMMA) {
            final Token comma = after;
            target = nextSpoken(tokens, comma.getTokenIndex());
            if (target == null || !startsTarget(target)) {
                return comma;
            }
            after = pastTarget(tokens, target);
            if (target.getType() != FrostlakeLexer.COLON && (after == null || !mayFollowTarget(after))) {
                return comma;
            }
        }
        return null;
    }

    private static boolean startsTarget(final Token token) {
        return token.getType() == FrostlakeLexer.COLON || NAME_START.contains(token.getType());
    }

    /** The token after the target that opens at {@code target}: a name, or ':' and the name after it. */
    private static Token pastTarget(final TokenStream tokens, final Token target) {
        final Token name = target.getType() == FrostlakeLexer.COLON ? nextSpoken(tokens, target.getTokenIndex()) : target;
        return name == null ? null : nextSpoken(tokens, name.getTokenIndex());
    }

    /** Whether a plain name may stand before {@code token} in the list: another comma, or a word the list ends at. */
    private static boolean mayFollowTarget(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.COMMA || type == Token.EOF || type == FrostlakeLexer.SEMI
            || type == FrostlakeLexer.FROM || type == FrostlakeLexer.WHERE || type == FrostlakeLexer.GROUP
            || type == FrostlakeLexer.HAVING || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.ORDER
            || type == FrostlakeLexer.LIMIT || type == FrostlakeLexer.FETCH || type == FrostlakeLexer.OFFSET
            || type == FrostlakeLexer.UNION || type == FrostlakeLexer.EXCEPT || type == FrostlakeLexer.MINUS
            || type == FrostlakeLexer.INTERSECT;
    }

    private static boolean faultBefore(final SyntaxErrorListener listener, final Token comma) {
        final String first = listener.firstReportedLine();
        final int[] at = first == null ? null : SyntaxErrorListener.sentenceCoordinates(first);
        if (at == null) {
            return false;
        }
        final int[] commaAt = LeadingCommentOffset.rebase(comma.getLine(), comma.getCharPositionInLine());
        return at[0] < commaAt[0] || at[0] == commaAt[0] && at[1] < commaAt[1];
    }

    private static Token nextSpoken(final TokenStream tokens, final int after) {
        for (int i = after + 1; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (token.getChannel() == Token.DEFAULT_CHANNEL) {
                return token;
            }
        }
        return null;
    }
}
