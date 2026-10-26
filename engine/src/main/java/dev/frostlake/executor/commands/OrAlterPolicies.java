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
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.PolicyReferences;
import dev.frostlake.parser.FrostlakeParser;

import java.util.List;

/**
 * A CREATE OR ALTER TABLE or VIEW that attaches a policy sets none: every policy it names must exist, and then the
 * statement is refused, {@code CREATE OR ALTER does not support setting policies or tags.} - a table's row access,
 * aggregation and join policy, a column's masking and projection policy and its tags, a view's row access policy and
 * its columns' policies and tags (live-verified). The object's own tag clause is refused the same way (see
 * {@link InlineTags#refuseOnOrAlter}).
 */
final class OrAlterPolicies {

    private static final String REFUSAL = "CREATE OR ALTER does not support setting policies or tags.";

    private OrAlterPolicies() {
    }

    /**
     * Refuse a CREATE OR ALTER TABLE that attaches a policy or a column's tag.
     *
     * @param ctx           the statement
     * @param queryExecutor the executor whose catalog resolves the policies and the tags
     */
    static void refuseTable(final FrostlakeParser.CreateStatementContext ctx, final QueryExecutor queryExecutor) {
        final Catalog catalog = queryExecutor.getCatalog();
        boolean attaches = false;
        for (final FrostlakeParser.TableTailOptionContext option : ctx.tableTailOption()) {
            if (option.rowAccessPolicyClause() != null) {
                PolicyReferences.rowAccessPolicy(catalog,
                    ParseTreeText.getQualifiedName(option.rowAccessPolicyClause().qualifiedName()), null);
                attaches = true;
            }
            if (option.aggregationPolicyClause() != null) {
                PolicyReferences.aggregationPolicy(catalog,
                    ParseTreeText.getQualifiedName(option.aggregationPolicyClause().qualifiedName()), null);
                attaches = true;
            }
            if (option.joinPolicyClause() != null) {
                PolicyReferences.joinPolicy(catalog,
                    ParseTreeText.getQualifiedName(option.joinPolicyClause().qualifiedName()), null);
                attaches = true;
            }
        }
        final List<FrostlakeParser.ColumnOrConstraintContext> columns = ctx.columnList() == null ? null
            : ctx.columnList().columnOrConstraint();
        if (columns != null) {
            for (final FrostlakeParser.ColumnOrConstraintContext column : columns) {
                if (column.columnDef() != null && attachesToColumn(column.columnDef(), queryExecutor)) {
                    attaches = true;
                }
            }
        }
        if (attaches) {
            throw new RuntimeException(REFUSAL);
        }
    }

    /**
     * Refuse a CREATE OR ALTER VIEW that attaches a row access policy.
     *
     * @param ctx           the statement
     * @param queryExecutor the executor whose catalog resolves the policy
     */
    static void refuseView(final FrostlakeParser.CreateStatementContext ctx, final QueryExecutor queryExecutor) {
        if (ctx.rowAccessPolicyClause() != null) {
            PolicyReferences.rowAccessPolicy(queryExecutor.getCatalog(),
                ParseTreeText.getQualifiedName(ctx.rowAccessPolicyClause().qualifiedName()), null);
            throw new RuntimeException(REFUSAL);
        }
        // So is a view's aggregation or join policy, over a policy that exists (live-verified).
        for (final FrostlakeParser.ViewPolicyClauseContext policy : ctx.viewPolicyClause()) {
            if (policy.aggregationPolicyClause() != null) {
                PolicyReferences.aggregationPolicy(queryExecutor.getCatalog(),
                    ParseTreeText.getQualifiedName(policy.aggregationPolicyClause().qualifiedName()), null);
            } else {
                PolicyReferences.joinPolicy(queryExecutor.getCatalog(),
                    ParseTreeText.getQualifiedName(policy.joinPolicyClause().qualifiedName()), null);
            }
        }
        if (!ctx.viewPolicyClause().isEmpty()) {
            throw new RuntimeException(REFUSAL);
        }
        // And a column's masking or projection policy or its tags, each of them resolved first (live-verified).
        if (ViewColumnClauses.attachesAny(ctx.viewColumnList())) {
            for (final FrostlakeParser.ViewColumnDefContext column : ctx.viewColumnList().viewColumnDef()) {
                for (final FrostlakeParser.ViewColumnClauseContext clause : column.viewColumnClause()) {
                    if (clause.tagList() != null) {
                        InlineTags.resolveAll(clause.tagList(), queryExecutor);
                    } else if (clause.MASKING() != null) {
                        PolicyReferences.maskingPolicy(queryExecutor.getCatalog(),
                            ParseTreeText.getQualifiedName(clause.qualifiedName()), null);
                    } else {
                        PolicyReferences.projectionPolicy(queryExecutor.getCatalog(),
                            ParseTreeText.getQualifiedName(clause.qualifiedName()), null);
                    }
                }
            }
            throw new RuntimeException(REFUSAL);
        }
    }

    /** Whether a column's definition attaches a policy or a tag, each of them resolved first. */
    private static boolean attachesToColumn(final FrostlakeParser.ColumnDefContext column,
                                            final QueryExecutor queryExecutor) {
        boolean attaches = false;
        for (final FrostlakeParser.ColumnConstraintContext constraint : column.columnConstraint()) {
            if (constraint.MASKING() != null) {
                PolicyReferences.maskingPolicy(queryExecutor.getCatalog(),
                    ParseTreeText.getQualifiedName(constraint.qualifiedName()), null);
                attaches = true;
            } else if (constraint.PROJECTION() != null) {
                PolicyReferences.projectionPolicy(queryExecutor.getCatalog(),
                    ParseTreeText.getQualifiedName(constraint.qualifiedName()), null);
                attaches = true;
            } else if (constraint.tagList() != null) {
                InlineTags.resolveAll(constraint.tagList(), queryExecutor);
                attaches = true;
            }
        }
        return attaches;
    }
}
