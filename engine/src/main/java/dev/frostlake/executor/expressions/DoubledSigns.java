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
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Two signs in a row are no expression: {@code - -1}, {@code -+1}, {@code +-1} and {@code ++1} are refused as an
 * invalid function named by the outer sign, a tilde counting as a sign ({@code -~1} names '-', {@code ~-1} '~'). The
 * account refuses them before it looks any name up, in a sort or a grouping key over a missing table as in a select
 * list (live-verified), so a statement is judged for them first. A parenthesised inner sign, {@code -(-1)}, and a
 * sign after a binary operator, {@code 1 - -1}, are ordinary.
 */
public final class DoubledSigns {

    private DoubledSigns() {
    }

    /**
     * Refuse a statement that writes two signs in a row anywhere. A text whose lexer read no sign straight after a
     * sign is not searched.
     *
     * @param statement the statement as parsed
     */
    public static void rejectInStatement(final ParserRuleContext statement) {
        final Token first = statement == null ? null : statement.getStart();
        if (first != null && first.getTokenSource() instanceof FrostlakeLexer
                && ((FrostlakeLexer) first.getTokenSource()).hasReadSignPair()) {
            rejectIn(statement);
        }
    }

    /**
     * Refuse the text a tree was parsed from when it writes two signs in a row, the first pair in the text's order.
     *
     * @param node a parse tree, or null
     */
    public static void rejectIn(final ParseTree node) {
        if (node == null) {
            return;
        }
        if (node instanceof FrostlakeParser.UnaryExprContext) {
            final FrostlakeParser.UnaryExprContext signed = (FrostlakeParser.UnaryExprContext) node;
            if (isSign(signed.expression())) {
                throw refusal(signed.op.getText());
            }
        } else if (node instanceof FrostlakeParser.TildeExprContext
                && isSign(((FrostlakeParser.TildeExprContext) node).expression())) {
            throw refusal("~");
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            rejectIn(node.getChild(i));
        }
    }

    /** Whether an operand is itself signed: a sign or a tilde straight before it, no parenthesis between. */
    private static boolean isSign(final ParseTree operand) {
        return operand instanceof FrostlakeParser.UnaryExprContext
            || operand instanceof FrostlakeParser.TildeExprContext;
    }

    /** The refusal of two signs in a row, named by the outer one. */
    private static RuntimeException refusal(final String sign) {
        return new RuntimeException(SqlCompilationError.of("invalid function '" + sign + "'"));
    }
}
