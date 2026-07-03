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
 * The kind of catalog object an authorization check targets — the "securable object" in Snowflake
 * terms. Each constant carries the canonical name used by the catalog's owner resolution
 * ({@code Catalog.getObjectOwnerRole}) and the privilege store (the same object-type string produced
 * by {@code GRANT … ON <type>}), so multi-word kinds keep their spaces (e.g. {@code MATERIALIZED
 * VIEW}). Using this in place of bare string literals keeps DROP/ALTER enforcement type-checked.
 */
public enum SecurableObjectType {
    DATABASE("DATABASE"),
    SCHEMA("SCHEMA"),
    TABLE("TABLE"),
    VIEW("VIEW"),
    MATERIALIZED_VIEW("MATERIALIZED VIEW"),
    DYNAMIC_TABLE("DYNAMIC TABLE"),
    STREAM("STREAM"),
    TASK("TASK"),
    PIPE("PIPE"),
    STAGE("STAGE"),
    WAREHOUSE("WAREHOUSE"),
    TAG("TAG"),
    MASKING_POLICY("MASKING POLICY"),
    ROW_ACCESS_POLICY("ROW ACCESS POLICY");

    private final String catalogName;

    SecurableObjectType(final String catalogName) {
        this.catalogName = catalogName;
    }

    /** The canonical object-type string used by the catalog and the privilege store. */
    public String getCatalogName() {
        return catalogName;
    }
}
