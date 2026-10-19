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

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;

import java.util.List;

/** One node of a parse tree and the text that stands in for it when the tree around it is written out. */
final class TextReplacement {

    private final int start;
    private final int stop;
    private final String text;

    /**
     * @param node the node replaced
     * @param text what is written in its place
     */
    TextReplacement(final ParserRuleContext node, final String text) {
        this.start = node.getStart().getStartIndex();
        this.stop = node.getStop().getStopIndex();
        this.text = text;
    }

    /**
     * A node's source text with some of the nodes under it replaced.
     *
     * @param root         the node written out
     * @param replacements the replacements, in source order and not overlapping
     * @return the text; the node's own when nothing is replaced
     */
    static String apply(final ParserRuleContext root, final List<TextReplacement> replacements) {
        if (replacements.isEmpty()) {
            return ParseTreeText.getOriginalText(root);
        }
        final StringBuilder out = new StringBuilder();
        int cursor = root.getStart().getStartIndex();
        for (final TextReplacement replacement : replacements) {
            if (replacement.start > cursor) {
                out.append(root.getStart().getInputStream().getText(Interval.of(cursor, replacement.start - 1)));
            }
            out.append(replacement.text);
            cursor = replacement.stop + 1;
        }
        final int end = root.getStop().getStopIndex();
        if (cursor <= end) {
            out.append(root.getStart().getInputStream().getText(Interval.of(cursor, end)));
        }
        return out.toString();
    }
}
