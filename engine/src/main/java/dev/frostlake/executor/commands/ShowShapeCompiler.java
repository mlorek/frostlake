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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.parser.BlockReadOn;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * What a SHOW statement's shape alone rules out is the refusal of the block that holds it: the account raises it
 * while it compiles the block — before any statement runs, on a branch that is never taken, past every handler,
 * and at CREATE for a procedure's body (all live-verified):
 *
 * <pre>
 *   BEGIN INSERT INTO t VALUES (1); SHOW TABLES IN USER LIMIT; END;   syntax error … unexpected ';'.  — t stays empty
 *   BEGIN IF (FALSE) THEN SHOW TABLES LIMIT 0; END IF; RETURN 1; END; page size "0" must be greater than 0 …
 *   BEGIN SHOW TABLES IN ACCOUNT x; EXCEPTION WHEN OTHER THEN …       Must specify the full search path …
 * </pre>
 *
 * <p>A syntax fault comes first, in whichever SHOW it stands: {@code BEGIN SHOW TABLES IN ACCOUNT x; SHOW TABLES IN
 * USER LIMIT; END;} is the second statement's. Of the other refusals the one written first wins, and a DESCRIBE TAG
 * ({@link DescribeTagRefusal}) is one of them. Of a statement whose name waits for a value — a variable, a bind —
 * only what no name decides is judged here, the rest when it runs; and so is everything that is looked up: a schema,
 * a class, a role that is not there.
 *
 * <p>★ A MODIFIER'S KEYWORD REFUSED IN A BLOCK STACKS THE BLOCK'S LINE, not the statement's own second line — a
 * LIMIT, STARTS, ROOT or PRIVILEGES the listing does not read there, or the IDENTIFIER of a reference after a keyword
 * scope. The keyword is read as a name and the token after it is refused; with the statement's semicolon there the
 * rest of the block is read on ({@link BlockReadOn}), and it is for a statement of a control construct's body
 * whatever follows the keyword. A PRIVILEGES is no name, so the word after it is the one read; a STARTS written
 * after a privilege list, and any other refused token, is one line (all live-verified):
 *
 * <pre>
 *   BEGIN SHOW TABLES LIMIT 1 LIMIT 2; END;                      'LIMIT', then '2'
 *   BEGIN SHOW LOCKS STARTS WITH 'A'; END;                       'STARTS', then 'WITH'
 *   BEGIN SHOW TABLES IN DATASET IDENTIFIER('y'); END;           'IDENTIFIER', then '('
 *   BEGIN SHOW TABLES LIMIT 1 LIMIT; END;                        'LIMIT', then 'END'
 *   BEGIN SHOW TASKS WITH PRIVILEGES USAGE; RETURN 1; END;       'PRIVILEGES', then 'RETURN'
 *   BEGIN IF (a) THEN SHOW TABLES IN DATASET IDENTIFIER('y'); END IF; END;    'IDENTIFIER', then the last 'END'
 *   BEGIN SHOW TABLES WITH PRIVILEGES USAGE STARTS WITH 'a'; END;             'STARTS' alone
 * </pre>
 */
final class ShowShapeCompiler {

    /** How a syntax error's line opens. */
    private static final String SYNTAX_ERROR = "syntax error line ";

    private ShowShapeCompiler() {
    }

    /**
     * Refuse a block for the first of its SHOW statements whose shape is ruled out, or do nothing.
     *
     * @param block the block being compiled
     * @param shows the handler that judges a SHOW statement's shape
     */
    static void requireShapes(final ParseTree block, final ShowCommandHandler shows) {
        final List<ParserRuleContext> statements = new ArrayList<>();
        collect(block, statements);
        RuntimeException first = null;
        for (final ParserRuleContext statement : statements) {
            final RuntimeException refused = refusalOf(statement, shows);
            if (refused == null) {
                continue;
            }
            if (isSyntaxError(refused)) {
                throw stackedInBlock(statement, refused);
            }
            if (first == null) {
                first = refused;
            }
        }
        if (first != null) {
            throw first;
        }
    }

    /** The statements judged, in the order written: each SHOW and each DESCRIBE TAG, a nested block's included. */
    private static void collect(final ParseTree node, final List<ParserRuleContext> statements) {
        if (node instanceof FrostlakeParser.ShowStatementContext
                || node instanceof FrostlakeParser.ShowClassStatementContext
                || node instanceof FrostlakeParser.DescribeStatementContext
                    && ((FrostlakeParser.DescribeStatementContext) node).TAG() != null) {
            statements.add((ParserRuleContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), statements);
        }
    }

    private static RuntimeException refusalOf(final ParserRuleContext statement, final ShowCommandHandler shows) {
        if (statement instanceof FrostlakeParser.DescribeStatementContext) {
            return new RuntimeException(DescribeTagRefusal.SENTENCE);
        }
        final boolean namesKnown = !waitsForValue(statement);
        try {
            if (statement instanceof FrostlakeParser.ShowStatementContext) {
                shows.requireShape((FrostlakeParser.ShowStatementContext) statement, namesKnown);
            } else if (namesKnown) {
                shows.requireShape((FrostlakeParser.ShowClassStatementContext) statement);
            }
            return null;
        } catch (final RuntimeException refused) {
            return refused;
        }
    }

    /** Whether the statement holds a variable's reference, a session variable or a bind: its names are not known yet. */
    private static boolean waitsForValue(final ParseTree node) {
        if (node instanceof TerminalNode) {
            final int type = ((TerminalNode) node).getSymbol().getType();
            return type == FrostlakeLexer.COLON || type == FrostlakeLexer.QUESTION
                || type == FrostlakeLexer.SESSION_VAR_REF || type == FrostlakeLexer.POSITIONAL_PARAMETER;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (waitsForValue(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** The refusal with the lines a block stacks after a refused modifier keyword; itself for any other token. */
    private static RuntimeException stackedInBlock(final ParserRuleContext statement,
                                                   final RuntimeException refused) {
        final String message = refused.getMessage();
        final int at = message.indexOf(SYNTAX_ERROR);
        final int lineEnd = message.indexOf('\n', at);
        final String first = lineEnd < 0 ? message.substring(at) : message.substring(at, lineEnd);
        final CharStream source = statement.getStart().getInputStream();
        if (at == 0 || source == null || source.size() == 0) {
            return refused;
        }
        final List<Token> tokens = BlockReadOn.spoken(source.getText(Interval.of(0, source.size() - 1)));
        final int keyword = BlockReadOn.indexOfLine(tokens, first);
        final int type = keyword < 0 ? Token.EOF : tokens.get(keyword).getType();
        if (type != FrostlakeLexer.LIMIT && type != FrostlakeLexer.STARTS && type != FrostlakeLexer.ROOT
                && type != FrostlakeLexer.PRIVILEGES && type != FrostlakeParser.KW_IDENTIFIER_REF) {
            return refused;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(first);
        final Boolean body = standsInBody(statement);
        final int name = type == FrostlakeLexer.PRIVILEGES ? keyword + 1 : keyword;
        final int after = tokens.get(name + 1).getType();
        final boolean afterPrivileges = type == FrostlakeLexer.STARTS && followsPrivileges(tokens, keyword);
        if (body != null && !afterPrivileges && after != FrostlakeLexer.END && after != Token.EOF) {
            if (after == FrostlakeLexer.SEMI || body.booleanValue()) {
                final int semicolon = BlockReadOn.semicolonFrom(tokens, keyword);
                if (semicolon >= 0) {
                    lines.addAll(BlockReadOn.linesAfter(tokens, semicolon));
                }
            } else {
                lines.add(BlockReadOn.lineOf(tokens.get(name + 1)));
            }
        }
        return new RuntimeException(SqlCompilationError.of(String.join("\n", lines)));
    }

    /**
     * Whether the statement stands in a control construct's body (true) or among a block's or a handler's own
     * statements (false); null when it is no statement of a list — the listing a RESULTSET is filled from.
     */
    private static Boolean standsInBody(final ParserRuleContext statement) {
        for (ParserRuleContext up = statement.getParent(); up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.ResultSetStatementContext) {
                return null;
            }
            if (up instanceof FrostlakeParser.StatementListContext) {
                final ParserRuleContext owner = up.getParent();
                return Boolean.valueOf(!(owner instanceof FrostlakeParser.BeginEndBlockContext)
                    && !(owner instanceof FrostlakeParser.ExceptionHandlerContext));
            }
        }
        return null;
    }

    /** Whether a PRIVILEGES stands before the token in its statement. */
    private static boolean followsPrivileges(final List<Token> tokens, final int index) {
        for (int i = index - 1; i >= 0 && tokens.get(i).getType() != FrostlakeLexer.SEMI
                && tokens.get(i).getType() != FrostlakeLexer.SHOW; i--) {
            if (tokens.get(i).getType() == FrostlakeLexer.PRIVILEGES) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSyntaxError(final RuntimeException refused) {
        return refused.getMessage() != null && refused.getMessage().contains(SYNTAX_ERROR);
    }
}
