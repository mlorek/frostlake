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

import java.util.ArrayDeque;
import java.util.Deque;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * A block nested in another whose END carries a word, and that declares nothing, is read and then neither compiled
 * nor run: the account answers 0 to {@code BEGIN LET r := 0; BEGIN r := 1; END IF; RETURN r; END;} and 5 to
 * {@code BEGIN BEGIN nosuch := 1; END lbl; RETURN 5; END;}, whatever the word — a keyword, a quoted name or a plain
 * one — while a fault in the block's syntax is still the text's. A nested block with a DECLARE section runs as if
 * the word were not there, and so does the top block of a body (all live-verified).
 *
 * <p>Such a block is emptied in the parse tree once the text has parsed, so that every stage that compiles a body
 * off the tree meets a block that holds nothing. The block's tokens stay where they are: its text is the text
 * written.
 */
public final class UnrunBlocks {

    private UnrunBlocks() {
    }

    /**
     * Empty every block under {@code root} that the account does not run, in place.
     *
     * @param root   the parsed text
     * @param tokens the text's tokens
     */
    public static void apply(final ParseTree root, final CommonTokenStream tokens) {
        if (root == null || !holdsAnEndWord(tokens)) {
            return;
        }
        final Deque<ParseTree> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            final ParseTree node = pending.pop();
            if (!(node instanceof ParserRuleContext) || ((ParserRuleContext) node).children == null) {
                continue;
            }
            final ParserRuleContext context = (ParserRuleContext) node;
            if (context instanceof FrostlakeParser.BeginEndBlockContext
                    && isUnrun((FrostlakeParser.BeginEndBlockContext) context)) {
                empty((FrostlakeParser.BeginEndBlockContext) context);
                continue;
            }
            for (final ParseTree child : context.children) {
                pending.push(child);
            }
        }
    }

    /**
     * @param block a block nested in another
     * @return whether the account reads the block and runs none of it: a word ends it and it declares nothing
     */
    public static boolean endsUnrun(final FrostlakeParser.BeginEndBlockContext block) {
        return block.blockEndWord() != null && block.declareSection() == null;
    }

    private static boolean isUnrun(final FrostlakeParser.BeginEndBlockContext block) {
        return endsUnrun(block) && isNested(block);
    }

    /** Whether another block of the same body stands around the block. */
    private static boolean isNested(final FrostlakeParser.BeginEndBlockContext block) {
        for (ParserRuleContext up = block.getParent(); up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.BodyDefinitionContext) {
                // A routine's body is a text of its own.
                return false;
            }
            if (up instanceof FrostlakeParser.BeginEndBlockContext) {
                return true;
            }
        }
        return false;
    }

    private static void empty(final FrostlakeParser.BeginEndBlockContext block) {
        if (block.statementList() != null && block.statementList().children != null) {
            block.statementList().children.clear();
        }
        if (block.exceptionSection() != null) {
            block.children.remove(block.exceptionSection());
        }
    }

    /** Whether an END of the text is followed by anything but a statement's end: the only texts read further. */
    private static boolean holdsAnEndWord(final CommonTokenStream tokens) {
        if (tokens == null) {
            return false;
        }
        boolean afterEnd = false;
        for (final Token token : tokens.getTokens()) {
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            final int type = token.getType();
            if (afterEnd && type != FrostlakeLexer.SEMI && type != Token.EOF && type != FrostlakeLexer.END) {
                return true;
            }
            afterEnd = type == FrostlakeLexer.END;
        }
        return false;
    }
}
