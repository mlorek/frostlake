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

import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The caret written between two values, {@code 2 ^ 2}. The account reads it as an operator and has no use for
 * it: the statement is refused while it compiles, before any of its names is looked up, by the operator's own
 * character and with no place — in a select list, in a WHERE clause, in a view's query, in a block's branch that
 * never runs and in a routine's body as it is created. A caret left without an operand never gets that far — it is
 * the syntax error it is, at the token that follows it or at the caret itself (all live-verified):
 * <pre>
 *   SELECT 2 ^ 2                          Unsupported feature '^'.
 *   SELECT 2 ^ 2 FROM nosuch_table        Unsupported feature '^'.
 *   EXPLAIN SELECT 2 ^ 2                  Unsupported feature '^'.
 *   … AS $$ BEGIN RETURN a ^ 2; END $$    Unsupported feature '^'.
 *   … RETURNS INT AS 'a ^ 2'              Compilation of SQL UDF failed: Unsupported feature '^'.
 *   SELECT 2 ^                            syntax error … unexpected '&lt;EOF&gt;'.
 *   SELECT ^ 2                            syntax error … unexpected '^'.
 * </pre>
 */
public final class CaretOperator {

    private CaretOperator() {
    }

    /**
     * @return the refusal of a statement that writes the operator
     */
    public static RuntimeException refusal() {
        return new RuntimeException("Unsupported feature '^'.");
    }

    /**
     * Refuse a statement that writes the operator anywhere: in a block's branch that never runs, in a view's
     * query, under EXPLAIN. The account refuses it before it looks any name up, so this is the first thing a
     * statement is judged for. A text whose lexer met no caret is not searched.
     *
     * @param statement the statement as parsed
     */
    public static void rejectInStatement(final ParserRuleContext statement) {
        final Token first = statement == null ? null : statement.getStart();
        if (first != null && first.getTokenSource() instanceof FrostlakeLexer
                && ((FrostlakeLexer) first.getTokenSource()).hasReadCaret()) {
            rejectIn(statement);
        }
    }

    /**
     * Refuse the text {@code node} was parsed from when the operator is written anywhere in it.
     *
     * @param node a parse tree, or null
     */
    public static void rejectIn(final ParseTree node) {
        if (node == null) {
            return;
        }
        if (node instanceof FrostlakeParser.CaretExprContext) {
            throw refusal();
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            rejectIn(node.getChild(i));
        }
    }
}
