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
package dev.frostlake.metastore;

import dev.frostlake.metastore.model.ContactHolder;
import dev.frostlake.metastore.model.Schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The attachments that name a contact — a table's, a view's and a task's, each a purpose and the contact's full name.
 * They hold
 * the contact itself (live-verified): a contact renamed or moved keeps them, its new name written into each, and one
 * dropped or replaced by another of its name leaves them naming nothing.
 */
final class ContactReferences {

    private ContactReferences() {
    }

    /**
     * Point every attachment of a renamed contact at its new name.
     *
     * @param schemas every schema
     * @param was     the contact's full name before
     * @param now     its full name now, or null when the contact is gone
     */
    static void repoint(final List<Schema> schemas, final String was, final String now) {
        for (final Schema schema : schemas) {
            final List<ContactHolder> holders = new ArrayList<ContactHolder>(schema.allTables());
            holders.addAll(schema.allViews());
            holders.addAll(schema.getTasks());
            for (final ContactHolder holder : holders) {
                for (final Map.Entry<String, String> attached : holder.getContacts().entrySet()) {
                    if (attached.getValue().equals(was)) {
                        if (now == null) {
                            holder.unsetContact(attached.getKey());
                        } else {
                            holder.setContact(attached.getKey(), now);
                        }
                    }
                }
            }
        }
    }
}
