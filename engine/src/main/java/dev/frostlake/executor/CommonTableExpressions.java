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
 * The common table expression a FROM's one-part name reads: the nearest WITH clause around the name that defines
 * it, read from the parse tree. A name with a qualifier, a time-travel clause or no such definition names a table or
 * a view instead.
 */
public final class CommonTableExpressions {

    private CommonTableExpressions() {
    }

    /**
     * The definition a FROM source reads, or null.
     *
     * @param source the source
     * @return the common table expression it names, or null when it names none
     */
    public static FrostlakeParser.CteDefinitionContext named(final FrostlakeParser.TableSourceContext source) {
        if (source == null || source.tableQualifiedName() == null || source.timeTravelClause() != null) {
            return null;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(source.tableQualifiedName());
        if (parts.length != 1) {
            return null;
        }
        ParseTree up = source.getParent();
        while (up != null) {
            if (up instanceof FrostlakeParser.SelectStatementContext
                    && ((FrostlakeParser.SelectStatementContext) up).withClause() != null) {
                for (final FrostlakeParser.CteDefinitionContext cte
                        : ((FrostlakeParser.SelectStatementContext) up).withClause().cteDefinition()) {
                    if (parts[0].equals(ParseTreeText.namePartText(cte.nameStartPart()))) {
                        return cte;
                    }
                }
            }
            up = up.getParent();
        }
        return null;
    }
}
