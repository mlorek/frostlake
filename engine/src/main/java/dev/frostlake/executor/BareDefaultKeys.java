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

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;

/**
 * An ORDER BY or GROUP BY key written as the bare word DEFAULT. The word reads no column and no output name, not
 * even an item aliased DEFAULT: live refuses {@code SELECT 1 AS default ORDER BY default} and {@code SELECT 1 AS d
 * GROUP BY default} as {@code invalid identifier 'DEFAULT'} placed at the key, as it refuses the word wherever a value
 * is read (live-verified). Those two clauses match their keys to the select list by what the keys spell, so a key
 * written so is refused here, before any matching. A scripting block's statements are compiled as they run, and are
 * left to their own compilation.
 */
public final class BareDefaultKeys {

    private BareDefaultKeys() {
    }

    /**
     * Refuses the first ORDER BY or GROUP BY key of a statement, at any depth of its queries, that is the bare word
     * DEFAULT.
     *
     * @param statement the statement's parse tree
     */
    public static void refuse(final ParseTree statement) {
        final Token key = firstIn(statement);
        if (key != null) {
            throw new RuntimeException(SqlCompilationError.invalidIdentifier(key.getLine(),
                key.getCharPositionInLine(), "DEFAULT"));
        }
    }

    private static Token firstIn(final ParseTree node) {
        if (node instanceof FrostlakeParser.BeginEndBlockContext
                || node instanceof FrostlakeParser.ProceduralStatementContext) {
            return null;
        }
        final List<FrostlakeParser.ExpressionContext> keys = new ArrayList<>();
        if (node instanceof FrostlakeParser.OrderItemContext) {
            keys.add(((FrostlakeParser.OrderItemContext) node).expression());
        } else if (node instanceof FrostlakeParser.GroupByElementContext) {
            final FrostlakeParser.GroupByElementContext element = (FrostlakeParser.GroupByElementContext) node;
            if (element.expression() != null) {
                keys.add(element.expression());
            }
            if (element.groupByColumnList() != null) {
                keys.addAll(element.groupByColumnList().expression());
            }
            if (element.groupingSetList() != null) {
                for (final FrostlakeParser.GroupingSetContext set : element.groupingSetList().groupingSet()) {
                    keys.addAll(set.expression());
                }
            }
        }
        for (final FrostlakeParser.ExpressionContext key : keys) {
            final Token bare = bareDefault(key);
            if (bare != null) {
                return bare;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final Token found = firstIn(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** The word of a key that is DEFAULT alone and unquoted, or null. */
    private static Token bareDefault(final FrostlakeParser.ExpressionContext key) {
        if (!(key instanceof FrostlakeParser.QualifiedNameExprContext)) {
            return null;
        }
        final FrostlakeParser.QualifiedNameContext name = ((FrostlakeParser.QualifiedNameExprContext) key).qualifiedName();
        return name.DOT().isEmpty() && name.nameStartPart().DEFAULT() != null ? name.getStart() : null;
    }
}
