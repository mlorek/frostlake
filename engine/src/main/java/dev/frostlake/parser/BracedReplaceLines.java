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

package dev.frostlake.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 * The tokens live refuses after a braced star's REPLACE and the token after it when the star is the item of a derived
 * table's or a CTE's query, read as the item's alias written before a bracket (all live-verified):
 *
 * <pre>
 *   SELECT a FROM (SELECT {* REPLACE (1 AS id)} a FROM fz)                 then the bracket's ')'
 *   WITH c AS (SELECT {* REPLACE (1 AS id)} h FROM fz) SELECT * FROM c     then '1' and the '}'
 * </pre>
 *
 * Nothing more for a statement's own query.
 */
public final class BracedReplaceLines {

    private BracedReplaceLines() {
    }

    /**
     * @param star     the braced star
     * @param modifier its REPLACE
     * @return the tokens refused after the keyword's and the next token's lines, in order; empty when there are none
     */
    public static List<Token> after(final FrostlakeParser.ObjectStarItemContext star,
                                    final FrostlakeParser.StarModifierContext modifier) {
        if (modifier.REPLACE() == null || modifier.RPAREN() == null || modifier.starReplaceItem().isEmpty()) {
            return Collections.emptyList();
        }
        final FrostlakeParser.SelectStatementContext query = StarModifierPlace.queryOf(star);
        if (query == null || StarModifierPlace.enclosedQueryOpener(query) == null) {
            return Collections.emptyList();
        }
        final List<FrostlakeParser.StarReplaceItemContext> items = modifier.starReplaceItem();
        final List<Token> after = new ArrayList<>();
        if (StarModifierPlace.cteQuery(query)) {
            // The bracket reads as the statement's query: its first token is refused, then the token after it.
            final List<FrostlakeParser.StarModifierContext> modifiers = star.starModifier();
            if (modifiers.get(modifiers.size() - 1) != modifier || star.RBRACE() == null) {
                return Collections.emptyList();
            }
            after.add(items.get(0).getStart());
            after.add(star.RBRACE().getSymbol());
            return after;
        }
        if (items.size() > 1) {
            after.add(items.get(1).getStart());
        } else if (items.get(0).expression().getStart() == items.get(0).expression().getStop()) {
            after.add(modifier.RPAREN().getSymbol());
        }
        return after;
    }
}
