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

/** The kinds of integration SQL names: the word before INTEGRATION, and the category SHOW INTEGRATIONS reports. */
public enum IntegrationKind {
    /** An API integration: external functions and Git repositories. */
    API("API"),
    /** A catalog integration: the external catalog of Iceberg tables. */
    CATALOG("CATALOG"),
    /** A notification integration: e-mail, webhooks and cloud queues. */
    NOTIFICATION("NOTIFICATION"),
    /** A storage integration. */
    STORAGE("STORAGE"),
    /** A security integration. */
    SECURITY("SECURITY"),
    /** An external access integration. */
    EXTERNAL_ACCESS("EXTERNAL ACCESS");

    private final String words;

    IntegrationKind(final String words) {
        this.words = words;
    }

    /** The kind as SQL writes it before INTEGRATION. */
    public String getWords() {
        return words;
    }

    /** The category SHOW INTEGRATIONS reports for the kind. */
    public String getCategory() {
        return this == EXTERNAL_ACCESS ? "SECURITY" : words;
    }
}
