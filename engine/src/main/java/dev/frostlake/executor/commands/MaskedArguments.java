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
import dev.frostlake.metastore.model.MaskingPolicy;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;

import java.util.List;
import java.util.Locale;

/**
 * A masked column against the policies that read columns as arguments: a row access policy, and a conditional masking
 * policy's further USING columns, read no column a masking policy masks, and no column one of them reads is masked -
 * in whichever order the two are attached - unless the masking policy was created EXEMPT_OTHER_POLICIES = TRUE
 * (documented). Each refusal is the account's own sentence (live-verified).
 */
final class MaskedArguments {

    private MaskedArguments() {
    }

    /** Whether the masking policy a column carries lets other policies read the column. */
    static boolean exempt(final Catalog catalog, final TableColumn column) {
        final MaskingPolicy policy =
            column.getMaskingPolicyName() == null ? null : catalog.findMaskingPolicy(column.getMaskingPolicyName());
        return policy != null && policy.isExemptOtherPolicies();
    }

    /**
     * Whether a column is masked so that no other policy may read it.
     *
     * @param catalog the catalog holding the masking policy
     * @param column  the column
     * @return whether its masking policy, if any, is not exempt
     */
    static boolean guarded(final Catalog catalog, final TableColumn column) {
        return column.getMaskingPolicyName() != null && !exempt(catalog, column);
    }

    /** Refuses reading a guarded column as another policy's argument, the column already masked. */
    static void requireReadable(final Catalog catalog, final TableColumn column) {
        if (guarded(catalog, column)) {
            throw new RuntimeException(SqlCompilationError.PREFIX + " Column '" + spelled(column)
                + "' cannot be used as policy argument because it is masked by another policy.");
        }
    }

    /**
     * Refuses masking a column the table's row access policy or another column's conditional masking policy reads,
     * unless the masking policy is exempt.
     *
     * @param table  the table
     * @param column the column to mask
     * @param policy the masking policy, or null when it is not known
     */
    static void requireMaskable(final Table table, final TableColumn column, final MaskingPolicy policy) {
        if (policy != null && policy.isExemptOtherPolicies()) {
            return;
        }
        if (readByRowAccessPolicy(table, column) || readByConditionalPolicy(table, column)) {
            throw cannotBeMasked(column);
        }
    }

    /** The refusal of masking a column another policy reads as an argument. */
    static RuntimeException cannotBeMasked(final TableColumn column) {
        return new RuntimeException(SqlCompilationError.PREFIX + " Column '" + spelled(column)
            + "' cannot be masked because it is used as argument by one or more masking/row-access policies.");
    }

    /**
     * A CREATE TABLE that masks a column and reads it as another column's conditional argument is refused, unless the
     * column's masking policy is exempt.
     *
     * @param catalog the catalog holding the masking policies
     * @param table   the table as created
     */
    static void requireNotMaskedAndConditional(final Catalog catalog, final Table table) {
        for (final TableColumn column : table.getColumns()) {
            if (guarded(catalog, column) && readByConditionalPolicy(table, column)) {
                throw new RuntimeException(SqlCompilationError.PREFIX + " Column '" + spelled(column)
                    + "' cannot be masked and used as conditional argument at the same time.");
            }
        }
    }

    private static boolean readByRowAccessPolicy(final Table table, final TableColumn column) {
        if (!table.hasRowAccessPolicy()) {
            return false;
        }
        for (final String read : table.getRowAccessPolicyColumns()) {
            if (read.equalsIgnoreCase(column.getName())) {
                return true;
            }
        }
        return false;
    }

    /** Whether another column's conditional masking policy reads the column beyond its own first argument. */
    private static boolean readByConditionalPolicy(final Table table, final TableColumn column) {
        for (final TableColumn other : table.getColumns()) {
            final List<String> arguments = other.getMaskingPolicyArguments();
            if (other == column || other.getMaskingPolicyName() == null || arguments == null) {
                continue;
            }
            for (int i = 1; i < arguments.size(); i++) {
                if (arguments.get(i).equalsIgnoreCase(column.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String spelled(final TableColumn column) {
        return column.getName().toUpperCase(Locale.ROOT);
    }
}
