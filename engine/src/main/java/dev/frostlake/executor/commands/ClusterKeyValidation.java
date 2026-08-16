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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;

/**
 * Validates clustering-key expressions: every column reference must name a column of the table.
 * Snowflake compiles the expressions even when IF EXISTS forgave a MISSING table — with no table,
 * no reference can resolve — and the refusal is positioned at the reference itself
 * (live-verified: {@code error line 1 at position 31\ninvalid identifier 'NOSUCHCOL'}).
 */
public final class ClusterKeyValidation {

    private ClusterKeyValidation() {
    }

    /** Refuses the first cluster expression column reference that does not resolve in the table
     *  ({@code null} table: refuses the first reference outright). */
    public static void requireResolvable(final List<FrostlakeParser.ExpressionContext> expressions,
            final Table tableOrNull) {
        for (final FrostlakeParser.ExpressionContext expression : expressions) {
            requireIn(expression, tableOrNull);
        }
    }

    private static void requireIn(final ParseTree node, final Table tableOrNull) {
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameExprContext ref = (FrostlakeParser.QualifiedNameExprContext) node;
            final FrostlakeParser.QualifiedNameContext name = ref.qualifiedName();
            final List<FrostlakeParser.NamePartContext> afterDot = name.namePart();
            final String lastPart = afterDot.isEmpty()
                ? ParseTreeText.namePartText(name.nameStartPart())
                : ParseTreeText.namePartText(afterDot.get(afterDot.size() - 1));
            final String column = SqlIdentifiers.canonicalText(lastPart);
            if (tableOrNull == null || !tableOrNull.hasColumn(column)) {
                final Token start = ref.getStart();
                throw new RuntimeException(SqlCompilationError.at(start.getLine(),
                    start.getCharPositionInLine(), "invalid identifier '" + column + "'"));
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            requireIn(node.getChild(i), tableOrNull);
        }
    }
}
