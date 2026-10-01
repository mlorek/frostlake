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

/**
 * The kinds of object that describe who may reach the account and with what credentials. A network policy
 * belongs to the account; the other three live in a schema.
 */
public enum SecurityObjectKind {

    /** A network rule: a schema object grouping network identifiers. */
    NETWORK_RULE("Network rule", "NETWORK RULE", true),
    /** A network policy: an account object allowing or blocking network identifiers and rules. */
    NETWORK_POLICY("Network policy", "NETWORK POLICY", false),
    /** A password policy: a schema object stating the rules a password must follow. */
    PASSWORD_POLICY("Password policy", "PASSWORD POLICY", true),
    /** A secret: a schema object holding credentials. */
    SECRET("Secret", "SECRET", true);

    private final String noun;
    private final String keywords;
    private final boolean schemaObject;

    SecurityObjectKind(final String noun, final String keywords, final boolean schemaObject) {
        this.noun = noun;
        this.keywords = keywords;
        this.schemaObject = schemaObject;
    }

    /** The kind as a refusal names it, e.g. {@code Network rule}. */
    public String noun() {
        return noun;
    }

    /** The kind's words in SQL, e.g. {@code NETWORK RULE}. */
    public String keywords() {
        return keywords;
    }

    /** Whether objects of this kind live in a schema rather than in the account. */
    public boolean isSchemaObject() {
        return schemaObject;
    }
}
