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

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * The lines live stacks when a three-part column reference is refused inside a Snowflake Scripting block —
 * only the first such refusal speaks, and nothing after the block's own lines (live-verified):
 *
 * <pre>
 *   BEGIN SELECT t..c FROM t; END                         'FROM'  'FROM'          the line again
 *   BEGIN SELECT ABS(t..c) FROM t; END                    ')'  '.'  ')'           the first line again
 *   BEGIN SELECT CAST(t..c AS INT) FROM t; END            'AS'  'AS'  ')'         the CAST's parenthesis
 *   BEGIN SELECT t..c; RETURN 1; END                      ';'  'RETURN'           the token after the ';'
 *   BEGIN WHILE (TRUE) DO SELECT t..c FROM t; END WHILE;  'FROM'  'FROM'  'END'   a loop or an inner block
 *   BEGIN IF (TRUE) THEN SELECT t..c FROM t; END IF;      'FROM'  'END'           IF, CASE or REPEAT
 *   BEGIN IF (TRUE) THEN SELECT t..c; END IF; END         ';'  'END'              the token after END IF;
 *   BEGIN FOR i IN 1 TO 2 DO SELECT t..c; END FOR; END    ';'  'FOR'              FOR is no name
 * </pre>
 *
 * <p>The first four hold for a statement of the outermost block or of its exception handlers; in a loop, an
 * inner block, an IF, a CASE or a REPEAT the last token is the one after the statement. A statement is a
 * query or DML statement, or a LET whose value is a query; a reference inside a subquery written as an
 * operand, and every other shape, keeps the refusal as it is.
 */
final class EmptyPartBlockLines {

    private EmptyPartBlockLines() {
    }

    /**
     * The tokens live reports for a first refusal inside a block, or null when the refusal is not inside one
     * or its shape there is not one of the above.
     *
     * @param fault  the first refusal, a three-part column reference
     * @param tokens the statement's token stream
     * @return the refused tokens in order, or null
     */
    static List<Token> stacked(final EmptyPartFault fault, final TokenStream tokens) {
        final FrostlakeParser.StatementContext statement = statementOf(fault.name());
        if (statement == null || !(statement.getParent() instanceof FrostlakeParser.StatementListContext)) {
            return null;
        }
        final FrostlakeParser.ProceduralStatementContext procedural = statement.proceduralStatement();
        if (procedural != null && procedural.letStatement() == null || inSubqueryExpression(fault.name(), statement)) {
            return null;
        }
        final ParserRuleContext construct = statement.getParent().getParent();
        final Token first = fault.first();
        final boolean terminator = first != null && first.getType() == FrostlakeLexer.SEMI;
        final List<Token> lines = new ArrayList<>(fault.refused());
        if (isOutermostBlock(construct)) {
            if (fault.shape() == EmptyPartShape.COLUMN_IN_CAST) {
                lines.add(fault.castClose());
            } else if (terminator) {
                lines.add(nextSpoken(tokens, first.getTokenIndex()));
            } else {
                lines.add(first);
            }
            return lines;
        }
        if (fault.shape() != EmptyPartShape.COLUMN) {
            return null;
        }
        if (terminator) {
            final Token past = pastConstruct(construct, tokens);
            if (past == null) {
                return null;
            }
            lines.add(past);
            return lines;
        }
        final Token after = afterStatement(statement, tokens);
        if (construct instanceof FrostlakeParser.WhileStatementContext
                || construct instanceof FrostlakeParser.LoopStatementContext
                || construct instanceof FrostlakeParser.ForStatementContext
                || construct instanceof FrostlakeParser.BeginEndBlockContext) {
            lines.add(first);
            lines.add(after);
            return lines;
        }
        if (construct instanceof FrostlakeParser.IfStatementContext
                || construct instanceof FrostlakeParser.CaseStatementContext
                || construct instanceof FrostlakeParser.RepeatStatementContext) {
            lines.add(after);
            return lines;
        }
        return null;
    }

    /**
     * The token live refuses after an IF, a CASE, a LOOP or a FOR a statement refused at its semicolon stands in:
     * the keyword after the construct's END is read as a name and the semicolon after it consumed, so the token
     * after them is refused — but FOR is no name, and is itself refused. Null for any other construct.
     */
    private static Token pastConstruct(final ParserRuleContext construct, final TokenStream tokens) {
        if (!(construct instanceof FrostlakeParser.IfStatementContext
                || construct instanceof FrostlakeParser.CaseStatementContext
                || construct instanceof FrostlakeParser.LoopStatementContext
                || construct instanceof FrostlakeParser.ForStatementContext)) {
            return null;
        }
        Token end = null;
        for (int i = 0; i < construct.getChildCount(); i++) {
            if (construct.getChild(i) instanceof TerminalNode
                    && ((TerminalNode) construct.getChild(i)).getSymbol().getType() == FrostlakeLexer.END) {
                end = ((TerminalNode) construct.getChild(i)).getSymbol();
            }
        }
        final Token keyword = end == null ? null : nextSpoken(tokens, end.getTokenIndex());
        if (keyword == null || keyword.getType() == Token.EOF || keyword.getType() == FrostlakeLexer.FOR) {
            return keyword == null || keyword.getType() == Token.EOF ? null : keyword;
        }
        Token next = nextSpoken(tokens, keyword.getTokenIndex());
        while (next != null && next.getType() == FrostlakeLexer.SEMI) {
            next = nextSpoken(tokens, next.getTokenIndex());
        }
        return next;
    }

    /** Whether a name stands in a query written as an expression's operand, below its statement. */
    private static boolean inSubqueryExpression(final ParserRuleContext name, final ParserRuleContext statement) {
        for (ParserRuleContext above = name.getParent(); above != null && above != statement; above = above.getParent()) {
            if (above instanceof FrostlakeParser.ScalarSubqueryExprContext
                    || above instanceof FrostlakeParser.InSubqueryExprContext
                    || above instanceof FrostlakeParser.ExistsExprContext) {
                return true;
            }
        }
        return false;
    }

    /** The innermost statement holding {@code node}, or null. */
    static FrostlakeParser.StatementContext statementOf(final ParserRuleContext node) {
        ParserRuleContext current = node;
        while (current != null && !(current instanceof FrostlakeParser.StatementContext)) {
            current = current.getParent();
        }
        return (FrostlakeParser.StatementContext) current;
    }

    /** Whether a statement list's owner is a block, or its exception handler, that no statement list holds. */
    private static boolean isOutermostBlock(final ParserRuleContext construct) {
        ParserRuleContext block = construct;
        if (block instanceof FrostlakeParser.ExceptionHandlerContext) {
            while (block != null && !(block instanceof FrostlakeParser.BeginEndBlockContext)) {
                block = block.getParent();
            }
        }
        if (!(block instanceof FrostlakeParser.BeginEndBlockContext)) {
            return false;
        }
        for (ParserRuleContext above = block.getParent(); above != null; above = above.getParent()) {
            if (above instanceof FrostlakeParser.StatementListContext) {
                return false;
            }
        }
        return true;
    }

    /** The token after a statement and its semicolon. */
    private static Token afterStatement(final FrostlakeParser.StatementContext statement, final TokenStream tokens) {
        Token next = nextSpoken(tokens, statement.getStop().getTokenIndex());
        while (next != null && next.getType() == FrostlakeLexer.SEMI) {
            next = nextSpoken(tokens, next.getTokenIndex());
        }
        return next;
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
}
