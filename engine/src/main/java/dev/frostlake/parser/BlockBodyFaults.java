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
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

/**
 * The faults of a block written as a procedure's body that are its own for certain, rather than a construct only the
 * account's grammar reads. The account compiles the body as the procedure is created and refuses such a body there,
 * in the lines the body run on its own is refused in (all live-verified):
 *
 * <pre>
 *   BEGIN LET x := ; RETURN 1; END            ';' — an operator, an assignment, a bracket or a comma left without
 *                                             its operand; SELECT * FROM; a FROM without its table
 *   BEGIN RETURN (1; END                      ';' — a bracket left open
 *   BEGIN INSERT INTO t VALUES (1,); END      ')' — a list's comma with nothing after it
 *   BEGIN SELECT FROM; END                    'FROM', then ';' — a select list missing, a FROM's table before WHERE
 *   BEGIN IF (TRUE) THEN RETURN 1; END; END   ';' — an END where IF, LOOP, WHILE, FOR or REPEAT is due after it
 *   BEGIN RETURN 1; END SELECT 2              'SELECT' — anything after the block's own END, its semicolon aside
 * </pre>
 *
 * A body this grammar reads whole is refused by a rule over that reading, which is as certain. A fault anywhere else
 * may stand where a construct this grammar lacks does, and such a body is left to its call.
 */
public final class BlockBodyFaults {

    private BlockBodyFaults() {
    }

    /**
     * Whether a block's first fault is its own for certain.
     *
     * @param sql the block's text
     * @return whether it is
     */
    public static boolean isOwn(final String sql) {
        final List<Token> raised = new ArrayList<>();
        final BaseErrorListener first = new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol, final int line,
                                    final int charPositionInLine, final String msg, final RecognitionException e) {
                if (raised.isEmpty()) {
                    // A character the lexer cannot read names no token: such a fault is no certain one.
                    raised.add(offendingSymbol instanceof Token ? (Token) offendingSymbol : null);
                }
            }
        };
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(sql));
        lexer.removeErrorListeners();
        lexer.addErrorListener(first);
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final FrostlakeParser parser = new FrostlakeParser(tokens);
        ColdPrediction.arm(parser);
        parser.removeErrorListeners();
        parser.addErrorListener(first);
        final FrostlakeParser.SqlScriptContext script = parser.sqlScript();
        if (raised.isEmpty()) {
            return true;
        }
        final Token fault = raised.get(0);
        if (fault == null) {
            return false;
        }
        final List<Token> spoken = new ArrayList<>();
        int at = -1;
        for (final Token token : tokens.getTokens()) {
            if (token.getChannel() == Token.DEFAULT_CHANNEL) {
                if (token.getTokenIndex() == fault.getTokenIndex()) {
                    at = spoken.size();
                }
                spoken.add(token);
            }
        }
        if (at < 1) {
            return false;
        }
        final int type = fault.getType();
        final int before = spoken.get(at - 1).getType();
        if (type == FrostlakeLexer.SEMI && (takesAnOperand(spoken, at - 1) || before == FrostlakeLexer.END
                || leftOpen(spoken, at))) {
            return true;
        }
        if (type == FrostlakeLexer.RPAREN && before == FrostlakeLexer.COMMA) {
            return true;
        }
        if (opensAClause(type) && (before == FrostlakeLexer.SELECT || (before == FrostlakeLexer.DISTINCT
                || before == FrostlakeLexer.ALL) && at >= 2 && spoken.get(at - 2).getType() == FrostlakeLexer.SELECT)) {
            return true;
        }
        return type == FrostlakeLexer.WHERE && before == FrostlakeLexer.FROM || pastTheBlock(script, fault);
    }

    /** Whether the token at {@code at} is an operator, or a bracket, a comma or a FROM, that an operand must follow. */
    private static boolean takesAnOperand(final List<Token> spoken, final int at) {
        final int type = spoken.get(at).getType();
        if (type == FrostlakeLexer.STAR) {
            // A star stands for a select list's columns after these.
            final int before = at > 0 ? spoken.get(at - 1).getType() : Token.EOF;
            return before != FrostlakeLexer.SELECT && before != FrostlakeLexer.DISTINCT && before != FrostlakeLexer.ALL
                && before != FrostlakeLexer.DOT && before != FrostlakeLexer.COMMA && before != FrostlakeLexer.LPAREN;
        }
        return type == FrostlakeLexer.PLUS || type == FrostlakeLexer.MINUS || type == FrostlakeLexer.SLASH
            || type == FrostlakeLexer.PERCENT || type == FrostlakeLexer.PIPE_PIPE || type == FrostlakeLexer.EQ
            || type == FrostlakeLexer.NEQ || type == FrostlakeLexer.LT || type == FrostlakeLexer.LTE
            || type == FrostlakeLexer.GT || type == FrostlakeLexer.GTE || type == FrostlakeLexer.COLON_EQ
            || type == FrostlakeLexer.ARROW || type == FrostlakeLexer.DOUBLE_COLON || type == FrostlakeLexer.LPAREN
            || type == FrostlakeLexer.COMMA || type == FrostlakeLexer.FROM || type == FrostlakeLexer.AND
            || type == FrostlakeLexer.OR || type == FrostlakeLexer.NOT;
    }

    /** Whether a bracket opened before the token at {@code at} is still open there. */
    private static boolean leftOpen(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = 0; i < at; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
            }
        }
        return depth > 0;
    }

    /** Whether a token is the word of a clause a select list stands before: there the list is missing. */
    private static boolean opensAClause(final int type) {
        return type == FrostlakeLexer.FROM || type == FrostlakeLexer.WHERE || type == FrostlakeLexer.GROUP
            || type == FrostlakeLexer.HAVING || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.INTO;
    }

    /** Whether the fault stands after the END of the block the text opens with, which the parse read whole. */
    private static boolean pastTheBlock(final FrostlakeParser.SqlScriptContext script, final Token fault) {
        if (script == null || script.flowChain().isEmpty() || script.flowChain().get(0).statement().isEmpty()) {
            return false;
        }
        final FrostlakeParser.ProceduralStatementContext statement =
            script.flowChain().get(0).statement().get(0).proceduralStatement();
        final FrostlakeParser.BeginEndBlockContext block = statement == null ? null : statement.beginEndBlock();
        return block != null && block.exception == null && block.END() != null && block.getStop() != null
            && block.getStop().getTokenIndex() < fault.getTokenIndex();
    }
}
