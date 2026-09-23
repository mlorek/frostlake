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

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * The stage {@code DIRECTORY(@stage)} reads, judged as live judges it (live-verified). DIRECTORY takes a named stage
 * alone and reads its name on its own:
 *
 * <pre>
 *   DIRECTORY(&#64;st/x)       syntax error at the '&#64;' naming the whole reference, '&#64;st/x' — a path is no name
 *   DIRECTORY(&#64;..st)       syntax error line 1 at position 0 unexpected '.'   a place in the name's own text:
 *   DIRECTORY(&#64;db..st)     … position 3 …   an empty part at its second dot, a fourth part at its third
 *   DIRECTORY(&#64;st.)        … position 3 unexpected '&lt;EOF&gt;'   a name that ends in a dot — the reference's own end
 *   DIRECTORY(&#64;%t), (&#64;~)  Argument 1 to function 'DIRECTORY' provides a user or table stage. …
 *   DIRECTORY(&#64;nosuch)     Stage '&#64;nosuch' provided to the function 'DIRECTORY' does not exist or is not
 *                          authorized.   a missing database or schema too, the name as written
 *   DIRECTORY(&#64;st)         DIRECTORY not enabled for the stage st   a stage without its directory table
 * </pre>
 */
final class DirectoryStageReference {

    private DirectoryStageReference() {
    }

    /**
     * Refuse the stage a DIRECTORY() call cannot read, in live's order.
     *
     * @param ctx the stage reference inside DIRECTORY()
     * @param catalog the catalog the stage resolves in
     */
    static void refuse(final FrostlakeParser.StageRefContext ctx, final Catalog catalog) {
        if (ctx.stagePath() != null) {
            final Token at = ctx.AT().getSymbol();
            final int[] shown = LeadingCommentOffset.rebase(at.getLine(), at.getCharPositionInLine());
            throw new RuntimeException(SqlCompilationError.of("syntax error line " + shown[0] + " at position "
                + shown[1] + " unexpected '" + ParseTreeText.getOriginalText(ctx) + "'."));
        }
        final Token fault = nameFault(ctx);
        if (fault != null) {
            final int position = fault.getStartIndex() - ctx.AT().getSymbol().getStopIndex() - 1;
            throw new RuntimeException(SqlCompilationError.of("syntax error line 1 at position " + position
                + " unexpected '" + fault.getText() + "'."));
        }
        final Token trailing = trailingDot(ctx);
        if (trailing != null) {
            // A name that ENDS in a dot has nothing after it to read: the fault is the end of the reference's own
            // text, counted in that frame like the other name faults — and only after them, so a doubled dot
            // is still named where it stands.
            final int position = trailing.getStopIndex() - ctx.AT().getSymbol().getStopIndex();
            throw new RuntimeException(SqlCompilationError.of("syntax error line 1 at position " + position
                + " unexpected '<EOF>'."));
        }
        if (ctx.TILDE() != null || ctx.PERCENT() != null) {
            throw new RuntimeException(SqlCompilationError.inline("Argument 1 to function 'DIRECTORY' provides a user"
                + " or table stage. These stage kinds are not supported by this function."));
        }
        final String written = StageReferenceShape.writtenName(ctx);
        final Stage stage;
        try {
            stage = StageReferenceShape.namedStage(ctx, catalog);
        } catch (final RuntimeException missing) {
            throw new RuntimeException(SqlCompilationError.inline("Stage '@" + written
                + "' provided to the function 'DIRECTORY' does not exist or is not authorized."));
        }
        if (!stage.isDirectoryEnabled()) {
            throw new RuntimeException("DIRECTORY not enabled for the stage " + written);
        }
    }

    /** The dot that ENDS the reference, when the last thing written in it is a dot; null otherwise. */
    private static Token trailingDot(final FrostlakeParser.StageRefContext ctx) {
        final ParseTree last = ctx.getChild(ctx.getChildCount() - 1);
        if (last instanceof TerminalNode && ((TerminalNode) last).getSymbol().getType() == FrostlakeLexer.DOT
                && ctx.getChildCount() > 2) {
            return ((TerminalNode) last).getSymbol();
        }
        return null;
    }

    /**
     * The token where the name read on its own stops: a dot opening it, a dot after a dot, or the dot that would
     * open a fourth part — null when the name reads whole.
     */
    private static Token nameFault(final FrostlakeParser.StageRefContext ctx) {
        boolean afterDot = true;
        int dots = 0;
        for (int i = 1; i < ctx.getChildCount(); i++) {
            final ParseTree child = ctx.getChild(i);
            if (!(child instanceof TerminalNode)) {
                afterDot = false;
                continue;
            }
            final Token token = ((TerminalNode) child).getSymbol();
            if (token.getType() != FrostlakeLexer.DOT) {
                afterDot = false;
                continue;
            }
            dots++;
            if (afterDot || dots == 3) {
                return token;
            }
            afterDot = true;
        }
        return null;
    }
}
