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

import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * A materialized view's clustering key, read from a CLUSTER BY clause. Each expression is kept as written — SHOW
 * MATERIALIZED VIEWS lists {@code LINEAR(x + 1, y)} — and read over the view's own columns, under the names its
 * column list gives them, so one naming none of them is refused where it stands: {@code invalid identifier
 * 'NOSUCH'} (live-verified). A constant is no key: {@code Invalid clustering key 1}.
 */
final class MaterializedViewKeys {

    private MaterializedViewKeys() {
    }

    /**
     * @param clause   the CLUSTER BY clause
     * @param columns  the view's columns, or null when they are not known
     * @param ddl      reads the statement's text
     * @param executor checks an expression over the columns
     * @return the key's expressions, in order
     */
    static List<String> read(final FrostlakeParser.ClusterByClauseContext clause, final List<TableColumn> columns,
                             final DDLCommandHandler ddl, final QueryExecutor executor) {
        final List<String> keys = new ArrayList<>();
        for (final FrostlakeParser.ExpressionContext key : clause.expressionList().expression()) {
            final String text = ddl.getOriginalText(key);
            if (ExpressionEvaluator.parse(text) instanceof LiteralExpression) {
                // A constant orders nothing; the refusal is no compilation error (live-verified).
                throw new RuntimeException("Invalid clustering key " + text + "\n");
            }
            // The view's columns are named bare: a qualified name is none of them.
            final FrostlakeParser.QualifiedNameContext qualified = qualifiedReference(key);
            if (qualified != null) {
                final SourcePosition at = ExpressionSource.place(qualified.getStart().getLine(),
                    qualified.getStart().getCharPositionInLine());
                throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                    "invalid identifier '" + String.join(".", ParseTreeText.qualifiedNameParts(qualified)) + "'"));
            }
            if (columns != null) {
                final SourcePosition at = ExpressionSource.place(key.getStart().getLine(),
                    key.getStart().getCharPositionInLine());
                executor.requireExpressionOver(columns, text, at);
            }
            keys.add(text);
        }
        return keys;
    }

    /** The first column reference written with a qualifier under a node, or null when every one is bare. */
    private static FrostlakeParser.QualifiedNameContext qualifiedReference(final ParseTree node) {
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameContext name =
                ((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName();
            return ParseTreeText.qualifiedNameParts(name).length > 1 ? name : null;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final FrostlakeParser.QualifiedNameContext found = qualifiedReference(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
