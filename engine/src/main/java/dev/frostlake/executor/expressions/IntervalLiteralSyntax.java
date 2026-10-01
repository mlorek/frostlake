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

import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.executor.commands.DataTypeParser;
import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The interval literals of a statement judged once it has parsed, ahead of every name it holds — the order the
 * account reports them in (all live-verified). A qualifier that names no type ("Invalid specification for type
 * INTERVAL: INTERVAL DAY") and a quoted-string text that does not read ("syntax error line 1 at position 6
 * unexpected '2'.") refuse the statement before an unknown column, table or function does, wherever each
 * stands: in a later select item, in WHERE, in a subquery, in a Snowflake Scripting block's untaken branch. The
 * first in the text wins, a cast's declared width among them ({@code 1::VARCHAR(0)} before the literal is refused
 * as the width). A unit word the account does not know is judged later, with the names, and a literal's text by
 * its qualifier only when a row reads it.
 */
public final class IntervalLiteralSyntax {

    private IntervalLiteralSyntax() {
    }

    /**
     * Refuse the first interval literal of {@code tree}, in the text's order, that names no type or does not read.
     *
     * @param tree a parsed statement or script
     */
    public static void requireReadable(final ParseTree tree) {
        if (holdsLiteral(tree)) {
            judge(tree);
        }
    }

    private static boolean holdsLiteral(final ParseTree tree) {
        if (tree instanceof FrostlakeParser.IntervalExprContext
                || tree instanceof FrostlakeParser.IntervalStringExprContext) {
            return true;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            if (holdsLiteral(tree.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    private static void judge(final ParseTree tree) {
        if (tree instanceof FrostlakeParser.IntervalExprContext) {
            final FrostlakeParser.IntervalExprContext literal = (FrostlakeParser.IntervalExprContext) tree;
            final String text = decoded(literal.STRING_LITERAL().getText());
            if (text != null) {
                IntervalQualifierReader.read(text, literal.intervalLiteralQualifier());
            }
            return;
        }
        if (tree instanceof FrostlakeParser.IntervalStringExprContext) {
            final String text = decoded(((FrostlakeParser.IntervalStringExprContext) tree).STRING_LITERAL().getText());
            if (text != null) {
                IntervalStringText.requireWellFormed(text);
            }
            return;
        }
        final FrostlakeParser.DataTypeNameContext castType = castType(tree);
        final FrostlakeParser.TypeParametersContext castWidth = castWidth(tree);
        for (int i = 0; i < tree.getChildCount(); i++) {
            final ParseTree child = tree.getChild(i);
            if (child == castType && castWidth != null) {
                // A width written on a cast's target is judged where it stands in the text.
                DataTypeParser.parse(castType, castWidth, DataTypeParser.CAST_STRING_DEFAULT);
            } else if (child != castWidth) {
                judge(child);
            }
        }
    }

    /** The target type of a cast node, or null for any other node. */
    private static FrostlakeParser.DataTypeNameContext castType(final ParseTree tree) {
        if (tree instanceof FrostlakeParser.CastExprContext) {
            return ((FrostlakeParser.CastExprContext) tree).dataTypeName();
        }
        if (tree instanceof FrostlakeParser.TryCastExprContext) {
            return ((FrostlakeParser.TryCastExprContext) tree).dataTypeName();
        }
        return tree instanceof FrostlakeParser.CastExpr2Context
            ? ((FrostlakeParser.CastExpr2Context) tree).dataTypeName() : null;
    }

    /** The parameters written on a cast node's target type, or null. */
    private static FrostlakeParser.TypeParametersContext castWidth(final ParseTree tree) {
        if (tree instanceof FrostlakeParser.CastExprContext) {
            return ((FrostlakeParser.CastExprContext) tree).typeParameters();
        }
        if (tree instanceof FrostlakeParser.TryCastExprContext) {
            return ((FrostlakeParser.TryCastExprContext) tree).typeParameters();
        }
        return tree instanceof FrostlakeParser.CastExpr2Context
            ? ((FrostlakeParser.CastExpr2Context) tree).typeParameters() : null;
    }

    /** A literal's text, or null when it does not decode — the literal's own reading refuses that, where it stands. */
    private static String decoded(final String token) {
        try {
            return SqlStringLiterals.decode(token);
        } catch (final RuntimeException undecodable) {
            return null;
        }
    }
}
