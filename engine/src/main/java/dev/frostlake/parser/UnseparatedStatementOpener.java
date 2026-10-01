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

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;

/**
 * The first token of a statement this parser opened straight after a complete one, with no semicolon between
 * them, where live refuses that token itself. Live never opens a second statement without its separator: it
 * refuses the first token the statement before cannot take, and reports nothing more of the statement that
 * token begins. This parser, free to open a fresh statement anywhere, instead named whatever inside that
 * statement it could not read (all live-verified):
 *
 * <pre>
 *   CREATE FUNCTION f() RETURNS INT AS '1' COMMENT = 'x'    'COMMENT'   this parser named the '='
 *   CREATE FUNCTION f() RETURNS INT AS '1' LANGUAGE SQL     'LANGUAGE'  and the 'SQL'
 *   DELETE FROM t1 ('x')                                    '('         and the string inside the bracket
 *   USE SCHEMA PUBLIC ('x')                                 '('
 *   SELECT 1 INTO t (1)                                     '('
 * </pre>
 *
 * <p>Nothing can follow a routine's quoted body, so every token after it is refused. A '(' is refused wherever
 * the statement before cannot go on with one: live reads it on after an operand, as the {@code (+)} outer-join
 * marker ({@code SELECT 1 ('x')} and {@code UPDATE t SET a = 1 ('x')} refuse the string); after a table
 * reference's alias, as its column list; and after the name a DROP or a DESCRIBE ends in, as a signature — which
 * no '(', string, number or SELECT can open, so the token after the '(' is the one refused:
 * {@code DESCRIBE TABLE t1 ((a))} and {@code DROP TABLE t1 (('a'))} name the inner '('. A membership test, an IS
 * NULL or an EXISTS is no operand: {@code WHERE a IN (1, 2) ('x')} refuses the '('.
 */
public final class UnseparatedStatementOpener {

    private UnseparatedStatementOpener() {
    }

    /**
     * The token live refuses when the fault the parser met lies in a statement it opened straight after a
     * complete one, or null when this is not that case.
     *
     * @param recognizer the parser that met the fault
     * @param reported the token the fault is currently reported at
     * @return the refused opening token, or null
     */
    static Token refused(final Recognizer<?, ?> recognizer, final Token reported, final boolean firstFault) {
        if (!(recognizer instanceof Parser)) {
            return null;
        }
        final Parser parser = (Parser) recognizer;
        final Token opener = openerOf(parser, reported);
        final FrostlakeParser.StatementContext last = opener == null ? null : runOnStatement(parser, reported);
        if (last == null) {
            return null;
        }
        if (endsInQuotedRoutineBody(last)) {
            return opener;
        }
        if (opener.getType() == FrostlakeLexer.LPAREN && endsInSignableName(last)) {
            return nextOnDefaultChannel(parser.getInputStream(), opener.getTokenIndex());
        }
        if (opener.getType() == FrostlakeLexer.LPAREN) {
            return takesParenthesis(last) ? null : opener;
        }
        // A word is judged only at the script's first fault, never in the wreckage of an earlier one, not after a
        // table's alias, which this parser may have read out of a word live reads as a clause of its own, and not
        // where live reads the word as the name of a property.
        return !firstFault || endsInTableAlias(last) || readsWordAsProperty(last) || takesWord(last, opener)
            ? null : opener;
    }

    /**
     * Whether live reads a word after this statement as the name of a property it goes on to assign: after a
     * DESCRIBE's object, after a COPY, after a CREATE that does not end in a query — a function or procedure still
     * without its body included — and after an ALTER whose last clause assigns a property, ALTER SESSION SET
     * included. There live refuses further on, at the end of input, at the '=' or as an invalid property, and never
     * at the word; everywhere else (a query, DML, DROP, SHOW, USE, GRANT, TRUNCATE, COMMENT ON, SET, an ALTER TABLE
     * adding or dropping a column) it refuses the word itself (live-verified).
     */
    private static boolean readsWordAsProperty(final FrostlakeParser.StatementContext statement) {
        final int leading = statement.getStart().getType();
        if (leading == FrostlakeLexer.DESCRIBE || leading == FrostlakeLexer.DESC || leading == FrostlakeLexer.COPY) {
            return true;
        }
        if (leading == FrostlakeLexer.CREATE) {
            return !endsInQuery(statement);
        }
        return leading == FrostlakeLexer.ALTER && endsInAssignment(statement);
    }

    /** Whether a statement's last construct is a query, as a CREATE … AS query's is. */
    private static boolean endsInQuery(final ParseTree statement) {
        for (ParseTree node = statement; node != null && node.getChildCount() > 0;
                node = node.getChild(node.getChildCount() - 1)) {
            if (node instanceof FrostlakeParser.SelectStatementContext) {
                return true;
            }
        }
        return false;
    }

    /** Whether a statement's last construct assigns a value to a name, as {@code SET COMMENT = 'x'} does. */
    private static boolean endsInAssignment(final ParseTree statement) {
        for (ParseTree node = statement; node != null && node.getChildCount() > 0;
                node = node.getChild(node.getChildCount() - 1)) {
            for (int i = 0; i + 1 < node.getChildCount(); i++) {
                if (node.getChild(i) instanceof TerminalNode
                        && ((TerminalNode) node.getChild(i)).getSymbol().getType() == FrostlakeLexer.EQ) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether a statement ends in the alias of one of its table references. */
    private static boolean endsInTableAlias(final FrostlakeParser.StatementContext statement) {
        ParseTree leaf = statement;
        while (leaf.getChildCount() > 0) {
            leaf = leaf.getChild(leaf.getChildCount() - 1);
        }
        final int last = statement.getStop().getTokenIndex();
        for (ParseTree up = leaf.getParent(); up != null && up != statement; up = up.getParent()) {
            if (up instanceof FrostlakeParser.TableReferenceContext) {
                final FrostlakeParser.TableReferenceContext reference = (FrostlakeParser.TableReferenceContext) up;
                final ParserRuleContext alias = reference.aliasName() != null
                    ? reference.aliasName() : reference.nonJoinKeywordIdentifier();
                return alias != null && alias.getStop() != null && alias.getStop().getTokenIndex() == last;
            }
        }
        return false;
    }

    /**
     * Whether a complete statement can take {@code word} as its next token: SELECT 1 AS x cannot take COMMENT, so
     * SELECT 1 AS x COMMENT = 'x' is refused at COMMENT, while FROM products takes COMMENT as its alias and ORDER BY a
     * takes a LIMIT, whose faults lie further on. The statement's text up to and including the word is parsed again
     * as one statement chain and nothing more, watching every point where the parser decides what comes next; the
     * word is the statement's own when one of them expects it, or when the parse consumes it.
     */
    private static boolean takesWord(final FrostlakeParser.StatementContext statement, final Token word) {
        final CharStream input = word.getInputStream();
        if (input == null || statement.getStart() == null) {
            return true;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(
            input.getText(Interval.of(statement.getStart().getStartIndex(), word.getStopIndex()))));
        lexer.removeErrorListeners();
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        int wordIndex = tokens.size() - 2;
        while (wordIndex > 0 && tokens.get(wordIndex).getChannel() != Token.DEFAULT_CHANNEL) {
            wordIndex--;
        }
        final int expectedAt = wordIndex;
        final FrostlakeParser reparser = new FrostlakeParser(tokens);
        reparser.removeErrorListeners();
        final Token[] firstFault = new Token[1];
        reparser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> faulted, final Object offendingSymbol, final int line,
                                    final int charPositionInLine, final String msg, final RecognitionException e) {
                if (firstFault[0] == null && offendingSymbol instanceof Token) {
                    firstFault[0] = (Token) offendingSymbol;
                }
            }
        });
        final boolean[] expected = new boolean[1];
        reparser.setErrorHandler(new DefaultErrorStrategy() {
            @Override
            public void sync(final Parser syncing) {
                if (!expected[0] && syncing.getCurrentToken().getTokenIndex() == expectedAt
                        && syncing.isExpectedToken(syncing.getCurrentToken().getType())) {
                    expected[0] = true;
                }
                super.sync(syncing);
            }
        });
        reparser.singleFlowChain();
        return expected[0] || firstFault[0] == null || firstFault[0].getType() == Token.EOF
            || firstFault[0].getTokenIndex() < expectedAt;
    }

    /**
     * The complete statement that the fault at {@code reported} runs straight on from: the last statement before the
     * one holding the fault — or before the fault itself, met between statements — when no semicolon separates the
     * two and it parsed without a fault of its own. Null when the fault lies in the script's first statement, after
     * a semicolon, or after a statement that failed.
     *
     * @param parser the parser that met the fault
     * @param reported the token the fault is reported at
     * @return the statement, or null
     */
    static FrostlakeParser.StatementContext runOnStatement(final Parser parser, final Token reported) {
        final Token opener = openerOf(parser, reported);
        ParserRuleContext root = parser.getContext();
        while (root != null && root.getParent() != null) {
            root = root.getParent();
        }
        if (opener == null || root == null) {
            return null;
        }
        ParserRuleContext previous = null;
        for (int i = 0; i < root.getChildCount(); i++) {
            final ParseTree child = root.getChild(i);
            if (child instanceof ParserRuleContext && ((ParserRuleContext) child).getStop() != null
                    && ((ParserRuleContext) child).getStop().getTokenIndex() < opener.getTokenIndex()) {
                previous = (ParserRuleContext) child;
            }
        }
        if (!(previous instanceof FrostlakeParser.FlowChainContext)
                || !runsStraightInto(parser.getInputStream(), previous.getStop(), opener) || !isClean(previous)) {
            return null;
        }
        final List<FrostlakeParser.StatementContext> statements = ((FrostlakeParser.FlowChainContext) previous).statement();
        if (statements.isEmpty()) {
            return null;
        }
        final FrostlakeParser.StatementContext last = statements.get(statements.size() - 1);
        return last.getStop() == null || last.getStop().getTokenIndex() != previous.getStop().getTokenIndex()
            ? null : last;
    }

    /**
     * The first token of the statement the parser was reading at the fault, or the fault's own token when the fault
     * lies between statements; null when that statement begins after the fault.
     */
    private static Token openerOf(final Parser parser, final Token reported) {
        ParserRuleContext current = null;
        ParserRuleContext root = parser.getContext();
        while (root != null && root.getParent() != null) {
            current = root;
            root = root.getParent();
        }
        if (current == null) {
            return reported;
        }
        return current.getStart() == null || current.getStart().getTokenIndex() > reported.getTokenIndex()
            ? null : current.getStart();
    }

    /**
     * The line live stacks after refusing the token a DESCRIBE's signature opens with: the first ')' after it, when a
     * word stands between the two — DESCRIBE TABLE t1 ((a)) is '(' then ')', and (((a))) names the ')' after the a —
     * and none when no word does, as in (()) or ((1)), nor after a DROP's (live-verified).
     *
     * @param recognizer the parser that met the fault
     * @param refused the token {@link #refused} named
     * @return the stacked token, or null
     */
    static Token stackedAfter(final Recognizer<?, ?> recognizer, final Token refused) {
        if (!(recognizer instanceof Parser) || refused.getType() != FrostlakeLexer.LPAREN) {
            return null;
        }
        final Parser parser = (Parser) recognizer;
        final FrostlakeParser.StatementContext last = runOnStatement(parser, refused);
        if (last == null || last.describeStatement() == null || !endsInSignableName(last)) {
            return null;
        }
        final TokenStream stream = parser.getInputStream();
        final IntervalSet words = parser.getATN().nextTokens(parser.getATN().ruleToStartState[FrostlakeParser.RULE_identifier]);
        boolean word = false;
        for (int i = refused.getTokenIndex() + 1; i < stream.size(); i++) {
            final Token token = stream.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (token.getType() == FrostlakeLexer.RPAREN) {
                return word ? token : null;
            }
            if (token.getType() == Token.EOF || token.getType() == FrostlakeLexer.SEMI) {
                return null;
            }
            word = word || words.contains(token.getType());
        }
        return null;
    }

    /** The first token on the default channel after the one at {@code index}. */
    private static Token nextOnDefaultChannel(final TokenStream stream, final int index) {
        int i = index + 1;
        while (i < stream.size() - 1 && stream.get(i).getChannel() != Token.DEFAULT_CHANNEL) {
            i++;
        }
        return stream.get(i);
    }

    /** Whether nothing but hidden tokens stands between a statement's last token and the next one's first. */
    private static boolean runsStraightInto(final TokenStream stream, final Token stop, final Token opener) {
        if (stop.getType() == FrostlakeLexer.SEMI) {
            return false;
        }
        for (int i = stop.getTokenIndex() + 1; i < opener.getTokenIndex(); i++) {
            if (stream.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return false;
            }
        }
        return true;
    }

    /** Whether a parse subtree holds no recognition fault and no error node. */
    private static boolean isClean(final ParseTree tree) {
        if (tree instanceof ErrorNode) {
            return false;
        }
        if (tree instanceof ParserRuleContext && ((ParserRuleContext) tree).exception != null) {
            return false;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            if (!isClean(tree.getChild(i))) {
                return false;
            }
        }
        return true;
    }

    /** Whether a statement is a CREATE FUNCTION or PROCEDURE ending at its single- or dollar-quoted body. */
    private static boolean endsInQuotedRoutineBody(final FrostlakeParser.StatementContext statement) {
        if (statement.ddlStatement() == null || statement.ddlStatement().createStatement() == null) {
            return false;
        }
        final FrostlakeParser.CreateStatementContext create = statement.ddlStatement().createStatement();
        final FrostlakeParser.BodyDefinitionContext body = create.bodyDefinition();
        return (create.FUNCTION() != null || create.PROCEDURE() != null) && body != null
            && body.beginEndBlock() == null && body.getStop() != null
            && body.getStop().getTokenIndex() == statement.getStop().getTokenIndex();
    }

    /**
     * Whether live reads a '(' on as part of a statement ending where this one ends: after a list's trailing comma,
     * after an operand, after a table reference's alias, and after the name a DROP or a DESCRIBE ends in.
     */
    private static boolean takesParenthesis(final FrostlakeParser.StatementContext statement) {
        if (statement.getStop().getType() == FrostlakeLexer.COMMA) {
            return true;
        }
        ParseTree leaf = statement;
        while (leaf.getChildCount() > 0) {
            leaf = leaf.getChild(leaf.getChildCount() - 1);
        }
        if (!(leaf instanceof TerminalNode)) {
            return false;
        }
        final int last = statement.getStop().getTokenIndex();
        for (ParseTree up = leaf.getParent(); up != null && up != statement; up = up.getParent()) {
            if (up instanceof FrostlakeParser.ExpressionContext || up instanceof FrostlakeParser.BooleanExprContext) {
                return !isPredicate((ParserRuleContext) up);
            }
            if (up instanceof FrostlakeParser.TableReferenceContext) {
                final FrostlakeParser.TableReferenceContext reference = (FrostlakeParser.TableReferenceContext) up;
                final ParserRuleContext alias = reference.aliasName() != null
                    ? reference.aliasName() : reference.nonJoinKeywordIdentifier();
                return alias != null && alias.getStop() != null && alias.getStop().getTokenIndex() == last;
            }
        }
        return endsInSignableName(statement, last);
    }

    /** Whether an expression is a test that ends in its own bracket or keyword, which no '(' can go on. */
    private static boolean isPredicate(final ParserRuleContext expression) {
        return expression instanceof FrostlakeParser.InListExprContext
            || expression instanceof FrostlakeParser.InSubqueryExprContext
            || expression instanceof FrostlakeParser.TupleInListExprContext
            || expression instanceof FrostlakeParser.TupleInSubqueryExprContext
            || expression instanceof FrostlakeParser.TupleInFlatListExprContext
            || expression instanceof FrostlakeParser.IsNullExprContext
            || expression instanceof FrostlakeParser.ExistsExprContext
            || expression instanceof FrostlakeParser.LikeAnyAllExprContext;
    }

    /**
     * Whether a DROP or DESCRIBE statement ends with the object name it takes, where live reads a '(' on as the
     * name's signature.
     *
     * @param statement the statement
     * @return whether it ends in such a name
     */
    static boolean endsInSignableName(final FrostlakeParser.StatementContext statement) {
        return statement.getStop() != null && endsInSignableName(statement, statement.getStop().getTokenIndex());
    }

    /** Whether a DROP or DESCRIBE statement's last token is the last token of the object name it takes. */
    private static boolean endsInSignableName(final FrostlakeParser.StatementContext statement, final int last) {
        final ParserRuleContext command;
        if (statement.describeStatement() != null) {
            command = statement.describeStatement();
        } else if (statement.ddlStatement() != null && statement.ddlStatement().dropStatement() != null) {
            command = statement.ddlStatement().dropStatement();
        } else {
            return false;
        }
        for (int i = command.getChildCount() - 1; i >= 0; i--) {
            final ParseTree child = command.getChild(i);
            if (child instanceof FrostlakeParser.ObjectNameContext || child instanceof FrostlakeParser.QualifiedNameContext
                    || child instanceof FrostlakeParser.IdentifierContext
                    || child instanceof FrostlakeParser.OpenedIdentifierReferenceContext) {
                final Token stop = ((ParserRuleContext) child).getStop();
                return stop != null && stop.getTokenIndex() == last;
            }
        }
        return false;
    }
}
