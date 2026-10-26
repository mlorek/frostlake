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
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.MaskingPolicy;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlSyntaxException;

import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A view's or a materialized view's column list that attaches policies and tags to its columns (all live-verified):
 * each column takes a masking policy, with a USING list where the policy is conditional, or a projection policy, then
 * a tag list and a comment, the WITH before each optional. A list one of whose columns holds both policies, or a
 * clause twice or out of that order, is refused at its first column,
 * {@code syntax error line 1 at position 29 unexpected 'id'.}.
 */
final class ViewColumnClauses {

    private ViewColumnClauses() {
    }

    /**
     * Refuse a view's column list one of whose columns holds its clauses in a shape the account does not read.
     *
     * @param list the list
     * @param sql  the statement's text
     */
    static void requireReadable(final FrostlakeParser.ViewColumnListContext list, final String sql) {
        if (list == null) {
            return;
        }
        for (final FrostlakeParser.ViewColumnDefContext column : list.viewColumnDef()) {
            boolean policy = false;
            boolean tag = false;
            for (final FrostlakeParser.ViewColumnClauseContext clause : column.viewColumnClause()) {
                if (column.columnCommentClause() != null && clause.getStart().getStartIndex()
                        > column.columnCommentClause().getStart().getStartIndex()) {
                    // The comment comes last.
                    throw atFirstColumn(list, sql);
                }
                if (clause.tagList() != null && !tag) {
                    tag = true;
                } else if (clause.tagList() == null && !policy && !tag) {
                    policy = true;
                } else {
                    throw atFirstColumn(list, sql);
                }
            }
        }
    }

    /** Whether a list attaches anything to its columns. */
    static boolean attachesAny(final FrostlakeParser.ViewColumnListContext list) {
        if (list == null) {
            return false;
        }
        for (final FrostlakeParser.ViewColumnDefContext column : list.viewColumnDef()) {
            if (!column.viewColumnClause().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Attach each column's policy and tags to the view's column at its place, as an ALTER VIEW would: the policy must
     * exist, a masking policy must take the column's type, and a USING list names the view's columns a conditional
     * policy reads.
     *
     * @param list          the list
     * @param resolved      the view's columns, in order
     * @param queryExecutor the executor whose catalog resolves the policies and the tags
     */
    static void attach(final FrostlakeParser.ViewColumnListContext list, final List<TableColumn> resolved,
                       final QueryExecutor queryExecutor) {
        if (list == null || resolved == null) {
            return;
        }
        final Catalog catalog = queryExecutor.getCatalog();
        final List<FrostlakeParser.ViewColumnDefContext> declared = list.viewColumnDef();
        for (int i = 0; i < declared.size() && i < resolved.size(); i++) {
            final TableColumn column = resolved.get(i);
            for (final FrostlakeParser.ViewColumnClauseContext clause : declared.get(i).viewColumnClause()) {
                if (clause.tagList() != null) {
                    InlineTags.apply(column, clause.tagList(), queryExecutor);
                    continue;
                }
                final String written = ParseTreeText.getQualifiedName(clause.qualifiedName());
                if (clause.PROJECTION() != null) {
                    if (catalog.findProjectionPolicyExact(written) == null) {
                        throw new RuntimeException(SqlCompilationError.doesNotExist("Projection policy",
                            catalog.qualifiedExactName(written)));
                    }
                    column.setProjectionPolicyName(catalog.qualifiedExactName(written));
                    continue;
                }
                final MaskingPolicy policy = catalog.findMaskingPolicyExact(written);
                if (policy == null) {
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Masking policy",
                        catalog.qualifiedExactName(written)));
                }
                MaskingPolicyAttachment.requireTypeMatch(policy, column);
                final List<String> arguments = new ArrayList<>();
                if (clause.identifierList() != null) {
                    for (final FrostlakeParser.IdentifierContext argument : clause.identifierList().identifier()) {
                        arguments.add(ParseTreeText.getIdentifier(argument));
                    }
                }
                column.setMaskingPolicyName(catalog.qualifiedExactName(written));
                column.setMaskingPolicyArguments(arguments);
            }
        }
    }

    private static SqlSyntaxException atFirstColumn(final FrostlakeParser.ViewColumnListContext list,
                                                    final String sql) {
        final Token at = list.viewColumnDef(0).identifier().getStart();
        final String line = "syntax error line " + at.getLine() + " at position " + at.getCharPositionInLine()
            + " unexpected '" + at.getText() + "'.";
        return new SqlSyntaxException(SqlCompilationError.of(line), Collections.singletonList(line), sql);
    }
}
