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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.RowAccessPolicy;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.types.DataType;
import dev.frostlake.types.SqlTypeNames;

import java.util.List;
import java.util.Locale;

/**
 * The checks a row access policy attachment must pass, shared by the ALTER form and the CREATE TABLE /
 * CREATE VIEW clauses — live makes the same ones wherever the attachment is written, with a single
 * measured difference: an unknown column is an {@code invalid identifier} on the ALTER path and a
 * {@code column 'X' does not exist} at CREATE, so the caller reports that one.
 */
public final class RowAccessPolicyAttachment {

    private RowAccessPolicyAttachment() {
    }

    /**
     * The policy a name resolves to, refused when it resolves to nothing.
     *
     * @param catalog    the catalog to resolve in
     * @param policyName the policy name as written
     */
    public static RowAccessPolicy require(final Catalog catalog, final String policyName) {
        final RowAccessPolicy policy = catalog.findRowAccessPolicy(policyName);
        if (policy == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Row access policy",
                catalog.qualifiedObjectName(policyName)));
        }
        return policy;
    }

    /**
     * The argument-count, masked-column and type checks, in live's order. The columns must already be
     * known to exist — resolving them is the caller's job, because the two paths word that refusal
     * differently.
     */
    public static void check(final RowAccessPolicy policy, final Table table, final String objectName,
                             final String policyName, final List<String> columns) {
        if (columns.size() != policy.getParameters().size()) {
            throw new RuntimeException(SqlCompilationError.of("Invalid number of arguments for attaching"
                + " policy '" + bare(policyName) + "' to '" + objectName.toUpperCase(Locale.ROOT)
                + "', expected " + policy.getParameters().size() + " arguments, got "
                + columns.size() + " arguments."));
        }
        if (table == null) {
            return;
        }
        for (int i = 0; i < columns.size(); i++) {
            final TableColumn column = table.getColumn(columns.get(i));
            if (column.hasMaskingPolicy()) {
                throw new RuntimeException(SqlCompilationError.PREFIX + " Column '"
                    + column.getName().toUpperCase(Locale.ROOT) + "' cannot be used as policy"
                    + " argument because it is masked by another policy.");
            }
            if (column.hasProjectionPolicy()) {
                // The same rule for the other column-level policy kind — and this one's sentence
                // carries NO compilation-error prefix, where the masked one's does (measured).
                throw new RuntimeException("Column '" + column.getName().toUpperCase(Locale.ROOT)
                    + "' cannot be used as policy argument because it has a projection policy"
                    + " attached.");
            }
            final DataType parameterType = policy.getParameters().get(i).getDataType();
            if (!SqlTypeNames.sameFamily(column.getDataType(), parameterType)) {
                throw new RuntimeException(SqlCompilationError.PREFIX + " Column '"
                    + column.getName().toUpperCase(Locale.ROOT) + "' data type '"
                    + SqlTypeNames.canonical(column.getDataType()) + "' does not match with Row"
                    + " access policy data type '" + SqlTypeNames.canonical(parameterType) + "'.");
            }
        }
    }

    private static String bare(final String qualifiedName) {
        final int dot = qualifiedName.lastIndexOf('.');
        return (dot < 0 ? qualifiedName : qualifiedName.substring(dot + 1)).toUpperCase(Locale.ROOT);
    }
}
