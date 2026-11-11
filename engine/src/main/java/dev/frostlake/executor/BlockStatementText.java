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

import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * A script's statement as it runs and as QUERY_HISTORY lists it: as written, without the semicolon that ends it, or
 * the space and comments before that (live-verified).
 */
public final class BlockStatementText {

    private BlockStatementText() {
    }

    /**
     * @param statement the statement
     * @param written   its text as written, terminator included
     * @return its text without the terminator, or as written when it has none
     */
    public static String of(final ParserRuleContext statement, final String written) {
        final Token start = statement.getStart();
        final Token stop = statement.getStop();
        if (start == null || stop == null || stop.getType() != FrostlakeParser.SEMI || start.getInputStream() == null) {
            return written;
        }
        final Token last = lastBefore(statement, stop);
        return last == null || last.getStopIndex() < start.getStartIndex() ? written
            : start.getInputStream().getText(new Interval(start.getStartIndex(), last.getStopIndex()));
    }

    /** The last token read of {@code node} before {@code stop}, walking back from its end, or null. */
    private static Token lastBefore(final ParseTree node, final Token stop) {
        if (node instanceof TerminalNode) {
            final Token token = ((TerminalNode) node).getSymbol();
            return token.getTokenIndex() >= 0 && token.getTokenIndex() < stop.getTokenIndex()
                && token.getStartIndex() >= 0 ? token : null;
        }
        for (int i = node.getChildCount() - 1; i >= 0; i--) {
            final Token found = lastBefore(node.getChild(i), stop);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
