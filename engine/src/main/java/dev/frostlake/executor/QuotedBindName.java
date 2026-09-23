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

package dev.frostlake.executor;

import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlSyntaxException;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Collections;

/**
 * A bind written with a quoted name — {@code :"x"} — which the account's grammar does not read: a bind names its
 * variable with a plain word, and a quoted one is a syntax error at the name, as a value and as an INTO target, in a
 * block, in a procedure's body at CREATE and in a statement of its own alike (live-verified).
 */
public final class QuotedBindName {

    private QuotedBindName() {
    }

    /**
     * The quoted name of a bind, when {@code node} is a bind written with one.
     *
     * @param node any node of a parse tree
     * @return the quoted name's token, or null
     */
    public static Token of(final ParseTree node) {
        FrostlakeParser.IdentifierContext name = null;
        if (node instanceof FrostlakeParser.IntoTargetContext) {
            final FrostlakeParser.IntoTargetContext target = (FrostlakeParser.IntoTargetContext) node;
            if (target.COLON() != null) {
                name = target.identifier();
            }
        } else if (node instanceof FrostlakeParser.BindVarExprContext) {
            name = ((FrostlakeParser.BindVarExprContext) node).identifier();
        }
        if (name == null) {
            return null;
        }
        final Token start = name.getStart();
        return start.getType() == FrostlakeLexer.QUOTED_IDENTIFIER ? start : null;
    }

    /**
     * The first bind with a quoted name under {@code node}, in the order of the text, leaving alone the blocks and
     * task bodies it holds, which are compiled on their own.
     *
     * @param node any node of a parse tree
     * @return the quoted name's token, or null
     */
    public static Token first(final ParseTree node) {
        if (node instanceof FrostlakeParser.BeginEndBlockContext || node instanceof FrostlakeParser.TaskBodyContext) {
            return null;
        }
        final Token quoted = of(node);
        if (quoted != null) {
            return quoted;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final Token nested = first(node.getChild(i));
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    /**
     * The syntax error a bind with a quoted name earns, at the name.
     *
     * @param name the quoted name
     * @return the refusal to throw
     */
    public static RuntimeException refusal(final Token name) {
        final int[] at = LeadingCommentOffset.rebase(name.getLine(), name.getCharPositionInLine());
        final String line = "syntax error line " + at[0] + " at position " + at[1] + " unexpected '" + name.getText()
            + "'.";
        return new SqlSyntaxException(SqlCompilationError.of(line), Collections.singletonList(line),
            name.getInputStream() == null ? null : name.getInputStream().toString());
    }
}
