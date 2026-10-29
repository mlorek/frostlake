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
package dev.frostlake.metastore.model;

import java.util.Map;

/** An object contacts are attached to, one for each purpose: a table, a view or a task. */
public interface ContactHolder {

    /**
     * The contacts attached, by purpose.
     *
     * @return purpose to the contact's full name
     */
    Map<String, String> getContacts();

    /**
     * Attach a contact for a purpose, replacing whatever that purpose held.
     *
     * @param purpose     the purpose
     * @param contactName the contact's full name
     */
    void setContact(String purpose, String contactName);

    /**
     * Detach the contact held for a purpose, if any.
     *
     * @param purpose the purpose
     */
    void unsetContact(String purpose);
}
