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

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * An ALL beside an INTERSECT, an EXCEPT or a MINUS, which the account does not run: {@code Unsupported feature
 * 'INTERSECT ALL'.}, and {@code 'MINUS ALL'} for EXCEPT and MINUS alike, wherever the operation stands — in a query,
 * under EXPLAIN and inside a GENERATOR bound, which is refused for it before its bound is judged (live-verified).
 */
final class UnsupportedSetOperatorAll {

    private UnsupportedSetOperatorAll() {
    }

    /**
     * Refuse the first such operator in a parse tree, in the order written.
     *
     * @param node the tree
     */
    static void requireNone(final ParseTree node) {
        if (node instanceof FrostlakeParser.SetOperatorContext) {
            final FrostlakeParser.SetOperatorContext operator = (FrostlakeParser.SetOperatorContext) node;
            if (operator.ALL() != null && operator.UNION() == null) {
                throw refusal(operator);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            requireNone(node.getChild(i));
        }
    }

    /**
     * The refusal of an operator.
     *
     * @param operator an INTERSECT, an EXCEPT or a MINUS written with ALL
     * @return the refusal
     */
    static RuntimeException refusal(final FrostlakeParser.SetOperatorContext operator) {
        return new RuntimeException("Unsupported feature '"
            + (operator.INTERSECT() != null ? "INTERSECT ALL" : "MINUS ALL") + "'.");
    }
}
