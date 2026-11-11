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
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.PrivacyPolicy;
import dev.frostlake.metastore.model.StorageLifecyclePolicy;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A table's privacy policy and its storage lifecycle policy: attached by ALTER TABLE or by CREATE TABLE ... WITH,
 * one of each kind at a time (all live-verified).
 *
 * <p>A privacy policy attaches with the one column its rows are counted by, {@code ADD PRIVACY POLICY p ENTITY KEY
 * (a)}, and is dropped by its name, {@code Policy P is not attached to TABLE T.} when the table holds no such one. A
 * storage lifecycle policy attaches ON the columns its arguments read, as many as it has arguments and of the same
 * kinds of type, and is dropped alone: {@code Successfully detached storage lifecycle policy 'DB.S.P' from the
 * table.}, or {@code No storage lifecycle policy attached.}. The policy is looked up first, then the columns, then a
 * second policy of the kind is refused: {@code Object T already has a PRIVACY_POLICY. Only one PRIVACY_POLICY is
 * allowed at a time.}
 *
 * <p>A column a storage lifecycle policy reads cannot be dropped while it is attached, and a renamed one is read by
 * its new name.
 */
final class PrivacyLifecycleAttachments {

    private static final String PRIVACY_NOUN = "Privacy policy";
    private static final String STORAGE_NOUN = "Storage lifecycle policy";

    private final Catalog catalog;

    PrivacyLifecycleAttachments(final Catalog catalog) {
        this.catalog = catalog;
    }

    /**
     * ALTER TABLE ... ADD or DROP PRIVACY POLICY, or ADD STORAGE LIFECYCLE POLICY.
     *
     * @param table  the table altered
     * @param action the action
     */
    void alter(final Table table, final FrostlakeParser.TableActionContext action) {
        final String written = ParseTreeText.getQualifiedName(action.qualifiedName());
        if (action.PRIVACY() == null) {
            attachStorage(table, written, action.identifierList().identifier(), false);
        } else if (action.ADD() != null) {
            attachPrivacy(table, written, action.identifier(0));
        } else {
            final PrivacyPolicy policy = requirePrivacyPolicy(written);
            if (!table.hasPrivacyPolicy() || !table.getPrivacyPolicyName().equals(
                    catalog.qualifiedExactName(written))) {
                throw new RuntimeException("Policy " + policy.getName() + " is not attached to TABLE "
                    + table.getName().toUpperCase(Locale.ROOT) + ".");
            }
            table.setPrivacyPolicyName(null);
            table.setPrivacyEntityKey(null);
        }
    }

    /**
     * ALTER TABLE ... DROP STORAGE LIFECYCLE POLICY: whatever storage lifecycle policy the table holds is detached.
     *
     * @param table the table altered
     * @return the sentence the statement answers
     */
    static String detachStorage(final Table table) {
        if (!table.hasStorageLifecyclePolicy()) {
            return "No storage lifecycle policy attached.";
        }
        final String name = table.getStorageLifecyclePolicyName();
        table.setStorageLifecyclePolicyName(null);
        table.setStorageLifecycleColumns(null);
        return "Successfully detached storage lifecycle policy '" + name + "' from the table.";
    }

    /**
     * CREATE TABLE ... WITH PRIVACY POLICY or WITH STORAGE LIFECYCLE POLICY: the ALTER forms' twins, but for a column
     * the table lacks, which is refused as {@code column 'X' does not exist}.
     *
     * @param tails the statement's table options
     * @param table the table created
     */
    void attachOnCreate(final List<FrostlakeParser.TableTailOptionContext> tails, final Table table) {
        for (final FrostlakeParser.TableTailOptionContext tail : tails) {
            if (tail.PRIVACY() != null) {
                attachPrivacy(table, ParseTreeText.getQualifiedName(tail.qualifiedName()), tail.identifier());
            } else if (tail.lifecycle != null) {
                attachStorage(table, ParseTreeText.getQualifiedName(tail.qualifiedName()),
                    tail.identifierList().identifier(), true);
            }
        }
    }

    private void attachPrivacy(final Table table, final String written,
                               final FrostlakeParser.IdentifierContext key) {
        requirePrivacyPolicy(written);
        final String column = column(table, key, false);
        if (table.hasPrivacyPolicy()) {
            throw onlyOne(table, "PRIVACY_POLICY");
        }
        table.setPrivacyPolicyName(catalog.qualifiedExactName(written));
        table.setPrivacyEntityKey(Collections.singletonList(column));
    }

    private void attachStorage(final Table table, final String written,
                               final List<FrostlakeParser.IdentifierContext> named, final boolean creating) {
        final StorageLifecyclePolicy policy = catalog.findStorageLifecyclePolicy(written);
        if (policy == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist(STORAGE_NOUN,
                catalog.qualifiedExactName(written)));
        }
        final List<String> columns = new ArrayList<>();
        for (final FrostlakeParser.IdentifierContext id : named) {
            columns.add(column(table, id, creating));
        }
        final List<Parameter> parameters = policy.getParameters();
        if (parameters.size() != columns.size()) {
            throw new RuntimeException(SqlCompilationError.of("Invalid number of arguments for attaching policy '"
                + policy.getName() + "' to '" + table.getName() + "', expected " + parameters.size()
                + " arguments, got " + columns.size() + " arguments."));
        }
        for (int i = 0; i < columns.size(); i++) {
            final TableColumn column = table.getColumn(columns.get(i));
            if (ReturnTypeFamily.of(column.getDataType()) != ReturnTypeFamily.of(parameters.get(i).getDataType())) {
                throw new RuntimeException(SqlCompilationError.inline("Column '" + column.getName()
                    + "' data type '" + RoutineReturnTypeChecker.spellColumn(column.getDataType())
                    + "' does not match with " + STORAGE_NOUN + " data type '"
                    + RoutineReturnTypeChecker.spell(parameters.get(i).getDataType()) + "'."));
            }
        }
        if (table.hasStorageLifecyclePolicy()) {
            throw onlyOne(table, "STORAGE_LIFECYCLE_POLICY");
        }
        table.setStorageLifecyclePolicyName(catalog.qualifiedExactName(written));
        table.setStorageLifecycleColumns(columns);
    }

    private PrivacyPolicy requirePrivacyPolicy(final String written) {
        final PrivacyPolicy policy = catalog.findPrivacyPolicy(written);
        if (policy == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist(PRIVACY_NOUN,
                catalog.qualifiedExactName(written)));
        }
        return policy;
    }

    /**
     * The table's column a name reads, as the table holds it. A missing one is an invalid identifier where the
     * statement names it, or, as a created table's, a column that does not exist.
     */
    private static String column(final Table table, final FrostlakeParser.IdentifierContext id,
                                 final boolean creating) {
        final String name = ParseTreeText.getIdentifier(id);
        if (!table.hasColumnExactly(name)) {
            throw new RuntimeException(creating ? SqlCompilationError.of("column '" + name.toUpperCase(Locale.ROOT)
                + "' does not exist") : SqlCompilationError.invalidIdentifier(id.getStart().getLine(),
                id.getStart().getCharPositionInLine(), name.toUpperCase(Locale.ROOT)));
        }
        return table.columnAt(table.getColumnIndex(name)).getName();
    }

    private static RuntimeException onlyOne(final Table table, final String kind) {
        return new RuntimeException("Object " + table.getName().toUpperCase(Locale.ROOT) + " already has a " + kind
            + ". Only one " + kind + " is allowed at a time.");
    }

    /**
     * Refuses to drop a column the table's storage lifecycle policy reads.
     *
     * @param table  the table
     * @param column the column, as the table holds it
     */
    static void requireDroppable(final Table table, final String column) {
        if (table.hasStorageLifecyclePolicy() && table.getStorageLifecycleColumns().contains(column)) {
            throw new RuntimeException(SqlCompilationError.inline("Cannot drop column '" + column
                + "' which is associated with a " + STORAGE_NOUN + "."));
        }
    }

    /**
     * The columns the table's policies read, a renamed one by its new name.
     *
     * @param table the table
     * @param was   the column's name before
     * @param now   its name now
     */
    static void followRename(final Table table, final String was, final String now) {
        table.setStorageLifecycleColumns(renamed(table.getStorageLifecycleColumns(), was, now));
        table.setPrivacyEntityKey(renamed(table.getPrivacyEntityKey(), was, now));
    }

    private static List<String> renamed(final List<String> columns, final String was, final String now) {
        final List<String> named = new ArrayList<>();
        for (final String column : columns) {
            named.add(column.equals(was) ? now : column);
        }
        return named;
    }
}
