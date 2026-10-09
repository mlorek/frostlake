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

import java.util.ArrayList;
import java.util.List;

/**
 * How a table's clustering key is spelled. The account keeps what the statement wrote after {@code CLUSTER BY}
 * character for character — spaces, comments and the word {@code LINEAR} when it was written — and every surface
 * reads that text back: GET_DDL prints it after {@code cluster by}, while SHOW TABLES and INFORMATION_SCHEMA print
 * it under {@code LINEAR} when the statement did not name the ordering itself (live-verified).
 */
public final class ClusteringKeySpelling {

    private ClusteringKeySpelling() {
    }

    /**
     * The text a CLUSTER BY clause wrote after its two keywords: {@code (a, b)} or {@code linear(a, b)}.
     *
     * @param order the ordering the clause names, or null when it names none
     * @param keys the clause's key list
     * @return the text, as written
     */
    public static String written(final FrostlakeParser.ClusterKeyOrderContext order,
                                 final FrostlakeParser.ExpressionListContext keys) {
        final ParserRuleContext clause = keys.getParent();
        Token open = null;
        Token close = null;
        for (int i = 0; i < clause.getChildCount(); i++) {
            if (clause.getChild(i) != keys) {
                continue;
            }
            open = tokenOf(i > 0 ? clause.getChild(i - 1) : null);
            close = tokenOf(i + 1 < clause.getChildCount() ? clause.getChild(i + 1) : null);
        }
        if (open == null || close == null || open.getInputStream() == null) {
            return "(" + String.join(", ", keys(keys)) + ")";
        }
        final int from = order != null ? order.getStart().getStartIndex() : open.getStartIndex();
        return open.getInputStream().getText(new Interval(from, close.getStopIndex()));
    }

    /**
     * The key expressions of a clause, each as written.
     *
     * @param keys the clause's key list
     * @return the expressions' texts, in order
     */
    public static List<String> keys(final FrostlakeParser.ExpressionListContext keys) {
        final List<String> texts = new ArrayList<>();
        for (final FrostlakeParser.ExpressionContext key : keys.expression()) {
            texts.add(ParseTreeText.getOriginalText(key));
        }
        return texts;
    }

    /**
     * The key as SHOW TABLES' {@code cluster_by} and INFORMATION_SCHEMA's {@code CLUSTERING_KEY} read it: the
     * written text, under {@code LINEAR} when the statement named no ordering.
     *
     * @param written the text written after CLUSTER BY, or null for a table with no key
     * @return the listed text, or null for a table with no key
     */
    public static String listed(final String written) {
        if (written == null) {
            return null;
        }
        return written.startsWith("(") ? "LINEAR" + written : written;
    }

    private static Token tokenOf(final ParseTree node) {
        return node instanceof TerminalNode ? ((TerminalNode) node).getSymbol() : null;
    }
}
