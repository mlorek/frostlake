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
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The contacts a CREATE TASK attaches: {@code [WITH] CONTACT (purpose = contact, …)}. Each contact has to exist,
 * which is looked at before its purpose is; a purpose is one of three, written bare or as a string; and no
 * purpose is named twice (live-verified).
 */
final class TaskContacts {

    private TaskContacts() {
    }

    /**
     * Attach a clause's contacts to the task being created.
     *
     * @param task     the task
     * @param contacts the clause
     * @param catalog  the catalog the contacts resolve in
     */
    static void apply(final Task task, final FrostlakeParser.TaskContactsContext contacts, final Catalog catalog) {
        final List<String> purposes = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        for (final FrostlakeParser.ContactAssignmentContext assignment : contacts.contactAssignment()) {
            // Named exactly, as a contact is everywhere: a quoted name keeps its case (live-verified).
            final String written = ParseTreeText.getQualifiedName(assignment.qualifiedName());
            if (catalog.findContact(written) == null) {
                throw new RuntimeException(SqlCompilationError.doesNotExist("Contact",
                    catalog.qualifiedExactName(written)));
            }
            if (assignment.listValue() != null) {
                throw ListValues.noPurpose(assignment.listValue());
            }
            final FrostlakeParser.ContactPurposeContext purposeWritten = assignment.contactPurpose();
            final String purpose = purposeWritten.identifier() != null
                ? SqlIdentifiers.canonical(purposeWritten.identifier())
                : ParseTreeText.extractStringLiteral(purposeWritten.STRING_LITERAL()).toUpperCase(Locale.ROOT);
            if (!"SUPPORT".equals(purpose) && !"STEWARD".equals(purpose) && !"ACCESS_APPROVAL".equals(purpose)) {
                throw new RuntimeException(SqlCompilationError.inline(
                    "Invalid contact purpose. Valid purposes are: ACCESS_APPROVAL, SUPPORT, STEWARD."));
            }
            if (purposes.contains(purpose)) {
                throw new RuntimeException("Try to attach duplicate purposes to the object to create, domain TASK.");
            }
            purposes.add(purpose);
            names.add(catalog.qualifiedExactName(written));
        }
        for (int i = 0; i < purposes.size(); i++) {
            task.setContact(purposes.get(i), names.get(i));
        }
    }
}
