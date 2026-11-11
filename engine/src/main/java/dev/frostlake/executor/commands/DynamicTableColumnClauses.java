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
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.List;

/**
 * The clauses of a dynamic table's own column list beside a column's name and type (all live-verified): a projection
 * policy, a masking policy with the columns its USING names, tags and NOT NULL attach to the column as a table's
 * column definition attaches them, each policy looked up as the table's column looks it up, and they apply where the
 * table is read; a DEFAULT is refused, {@code Unsupported feature 'Column constraints [ WITH DEFAULT null ]'.}
 */
final class DynamicTableColumnClauses {

    private DynamicTableColumnClauses() {
    }

    /**
     * Refuse a DEFAULT written in a dynamic table's column list, before anything else is judged.
     *
     * @param list the column list, or null
     */
    static void refuseDefaults(final FrostlakeParser.DynamicTableColumnsContext list) {
        if (list == null) {
            return;
        }
        for (final FrostlakeParser.DynamicTableColumnContext column : list.dynamicTableColumn()) {
            if (column.DEFAULT() != null) {
                throw new RuntimeException("Unsupported feature 'Column constraints [ WITH DEFAULT null ]'.");
            }
        }
    }

    /**
     * Attach the clauses of a dynamic table's column list to the columns it resolved to.
     *
     * @param list          the column list, or null
     * @param resolved      the table's columns as its query resolved them, under the list's names
     * @param catalog       the catalog the policies are found in
     * @param queryExecutor the executor tags resolve through
     */
    static void apply(final FrostlakeParser.DynamicTableColumnsContext list, final List<TableColumn> resolved,
                      final Catalog catalog, final QueryExecutor queryExecutor) {
        if (list == null || resolved == null) {
            return;
        }
        for (final FrostlakeParser.DynamicTableColumnContext column : list.dynamicTableColumn()) {
            final TableColumn target = columnNamed(resolved, SqlIdentifiers.canonical(column.identifier()));
            if (target == null) {
                continue;
            }
            if (column.projectionPolicy != null) {
                final String policy = ParseTreeText.getQualifiedName(column.projectionPolicy);
                if (catalog.findProjectionPolicyExact(policy) == null) {
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Projection policy",
                        catalog.qualifiedExactName(policy)));
                }
                target.setProjectionPolicyName(catalog.qualifiedExactName(policy));
            }
            if (column.maskingPolicy != null) {
                final String policy = ParseTreeText.getQualifiedName(column.maskingPolicy);
                if (catalog.findMaskingPolicyExact(policy) == null) {
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Masking policy",
                        catalog.qualifiedExactName(policy)));
                }
                MaskingPolicyAttachment.requireTypeMatch(catalog.findMaskingPolicyExact(policy), target);
                final List<String> arguments = new ArrayList<String>();
                if (column.identifierList() != null) {
                    for (final FrostlakeParser.IdentifierContext id : column.identifierList().identifier()) {
                        arguments.add(SqlIdentifiers.canonical(id));
                    }
                }
                target.setMaskingPolicyName(catalog.qualifiedExactName(policy));
                target.setMaskingPolicyArguments(arguments);
            }
            if (column.tagList() != null) {
                InlineTags.apply(target, column.tagList(), queryExecutor);
            }
            if (column.NOT() != null) {
                target.setNullable(false);
            }
        }
    }

    private static TableColumn columnNamed(final List<TableColumn> columns, final String name) {
        for (final TableColumn column : columns) {
            if (column.getName().equals(name)) {
                return column;
            }
        }
        return null;
    }
}
