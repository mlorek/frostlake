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
 * Refuses a scripting statement written where a script's own statements stand. Outside a BEGIN … END block the
 * account's grammar has no LET, assignment, IF, CASE, LOOP, WHILE, REPEAT, FOR, RETURN, BREAK, CONTINUE, RAISE,
 * ASYNC, AWAIT, OPEN, FETCH, CLOSE or NULL statement: each is a syntax error at its first word, and that word is the
 * whole report — the rest of the statement, and a later statement's own fault, are not named ({@code LET a := 1;
 * SELECT 1 x y} is 'LET' alone). A fault earlier in the text still wins ({@code SELECT 1 x y; LET a := 1} is 'y').
 * The text an EXECUTE IMMEDIATE runs is a script of its own, so the rule holds there too, even while a block runs
 * it (all live-verified).
 *
 * <p>Only a statement that opens the text, or follows a statement separator or the flow arrow, is judged: a
 * scripting word written straight after a statement is that statement's business — {@code SELECT 1 FROM t BREAK x}
 * takes BREAK as the table's alias. A block's own statements are not a script's: the engine runs some of them (a
 * NULL, a cursor's OPEN) as texts of their own, and those texts are not judged.
 */
public final class ScriptingStatementPlacement {

    private ScriptingStatementPlacement() {
    }

    /**
     * Refuse the script's first scripting statement when no parse fault stands before it.
     *
     * @param script   the parsed script
     * @param tokens   the token stream the script was parsed from
     * @param sql      the script's source text
     * @param listener the parse's error listener
     */
    public static void requireSqlStatementsBefore(final FrostlakeParser.SqlScriptContext script,
                                                  final TokenStream tokens, final String sql,
                                                  final SyntaxErrorListener listener) {
        Token word = firstScriptingStatement(script);
        if (word == null && listener.hasErrors()) {
            word = strayOpeningKeyword(tokens);
        }
        if (word == null) {
            return;
        }
        final int[] wordAt = LeadingCommentOffset.rebase(word.getLine(), word.getCharPositionInLine());
        final String parseFault = listener.firstReportedLine();
        final int[] parseFaultAt = parseFault == null ? null : SyntaxErrorListener.sentenceCoordinates(parseFault);
        if (parseFaultAt != null && (parseFaultAt[0] < wordAt[0]
                || parseFaultAt[0] == wordAt[0] && parseFaultAt[1] < wordAt[1])) {
            // A block statement refused after a keyword SHOW scope may name this word next (KeywordScopeLines).
            listener.takesLaterRefusal(word);
            return;
        }
        throw refusal(word, sql);
    }

    /**
     * Refuse the first scripting statement of a script that parsed without a fault.
     *
     * @param script the parsed script
     * @param sql    the script's source text
     */
    public static void requireSqlStatements(final FrostlakeParser.SqlScriptContext script, final String sql) {
        final Token word = firstScriptingStatement(script);
        if (word != null) {
            throw refusal(word, sql);
        }
    }

    private static SqlSyntaxException refusal(final Token word, final String sql) {
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(word));
        return new SqlSyntaxException(SqlCompilationError.of(lines.get(0)), lines, sql);
    }

    /** The first word of the script's first scripting statement that opens a statement, or null. */
    private static Token firstScriptingStatement(final FrostlakeParser.SqlScriptContext script) {
        if (script == null) {
            return null;
        }
        for (final FrostlakeParser.FlowChainContext chain : script.flowChain()) {
            for (final FrostlakeParser.StatementContext statement : chain.statement()) {
                final FrostlakeParser.ProceduralStatementContext procedural = statement.proceduralStatement();
                if (procedural != null && procedural.getStart() != null && isScriptingOnly(procedural)
                        && opensAStatement(statement)) {
                    return procedural.getStart();
                }
            }
        }
        return null;
    }

    /** Whether the statement is one only a block's body may hold. */
    private static boolean isScriptingOnly(final FrostlakeParser.ProceduralStatementContext statement) {
        return statement.letStatement() != null || statement.assignmentStatement() != null
            || statement.ifStatement() != null || statement.caseStatement() != null
            || statement.loopStatement() != null || statement.whileStatement() != null
            || statement.repeatStatement() != null || statement.forStatement() != null
            || statement.returnStatement() != null || statement.breakStatement() != null
            || statement.continueStatement() != null || statement.raiseStatement() != null
            || statement.asyncStatement() != null || statement.awaitStatement() != null
            || statement.openStatement() != null || statement.fetchStatement() != null
            || statement.closeStatement() != null || statement.nullStatement() != null;
    }

    /**
     * Whether the statement opens the script, or follows a statement separator, or the flow arrow — read off the
     * tree, where a chain's arrows stand between its statements and a separator either stands between two chains or
     * ends the statement before it.
     */
    private static boolean opensAStatement(final FrostlakeParser.StatementContext statement) {
        final ParserRuleContext chain = statement.getParent();
        if (!(chain instanceof FrostlakeParser.FlowChainContext)) {
            return false;
        }
        final int inChain = childIndex(chain, statement);
        if (inChain > 0) {
            return isTerminal(chain.getChild(inChain - 1), FrostlakeLexer.FLOW_ARROW);
        }
        final ParserRuleContext script = chain.getParent();
        if (script == null) {
            return false;
        }
        final int inScript = childIndex(script, chain);
        return inScript == 0 || inScript > 0 && endsWith(script.getChild(inScript - 1), FrostlakeLexer.SEMI);
    }

    /** Whether {@code node} is the token {@code type}, or a statement whose own last token it is. */
    private static boolean endsWith(final ParseTree node, final int type) {
        if (node instanceof ParserRuleContext) {
            final Token stop = ((ParserRuleContext) node).getStop();
            return stop != null && stop.getType() == type;
        }
        return isTerminal(node, type);
    }

    private static int childIndex(final ParserRuleContext parent, final ParseTree child) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChild(i) == child) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isTerminal(final ParseTree node, final int type) {
        return node instanceof TerminalNode && ((TerminalNode) node).getSymbol().getType() == type;
    }

    /**
     * The text's first word when it is a keyword only a scripting statement opens with. The parse builds no
     * statement from such a word followed straight by the end of the input or a semicolon ({@code LET}, {@code IF;}),
     * so the word itself is read: no SQL statement opens with any of these.
     */
    private static Token strayOpeningKeyword(final TokenStream tokens) {
        if (tokens == null) {
            return null;
        }
        for (int i = 0; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            final int type = token.getType();
            final boolean scripting = type == FrostlakeLexer.LET || type == FrostlakeLexer.IF
                || type == FrostlakeLexer.WHILE || type == FrostlakeLexer.LOOP || type == FrostlakeLexer.REPEAT
                || type == FrostlakeLexer.FOR || type == FrostlakeLexer.OPEN || type == FrostlakeLexer.FETCH
                || type == FrostlakeLexer.CLOSE || type == FrostlakeLexer.AWAIT || type == FrostlakeLexer.ASYNC
                || type == FrostlakeLexer.BREAK || type == FrostlakeLexer.CONTINUE || type == FrostlakeLexer.RETURN
                || type == FrostlakeLexer.RAISE;
            return scripting ? token : null;
        }
        return null;
    }
}
