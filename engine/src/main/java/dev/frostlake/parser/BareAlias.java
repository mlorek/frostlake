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

import org.antlr.v4.runtime.ParserRuleContext;

/**
 * A select item's alias written without AS. It is a name, or one of the eight words that may stand there although
 * no expression may open with them or carry them as a column's name: {@code SELECT 1 case}, {@code cast},
 * {@code constraint}, {@code cross}, {@code default}, {@code inner}, {@code join} and {@code when} each answer a
 * column so named, and {@code SELECT CASE WHEN a THEN 1 END CASE} is a CASE expression aliased CASE
 * (live-verified).
 */
public final class BareAlias {

    private BareAlias() {
    }

    /**
     * @param item a select item
     * @return the node of its AS-less alias, a name or one of the alias words, or null when it has none
     */
    public static ParserRuleContext of(final FrostlakeParser.ExprItemContext item) {
        return item.identifier() != null ? item.identifier() : item.bareAliasWord();
    }
}
