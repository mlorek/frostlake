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
 * The parent container a CREATE authorization check targets — the scope that must grant the CREATE
 * privilege. A schema-level object (table, view, stream, function, ...) is contained by a
 * {@link #SCHEMA}; a schema is contained by a {@link #DATABASE}. Restricting the check's container
 * argument to these two kinds (rather than a bare string or the broader {@code SecurableObjectType})
 * makes an invalid container unrepresentable. The catalog name matches the object-type string used
 * by owner resolution and the privilege store.
 */
public enum ContainerType {
    DATABASE("DATABASE"),
    SCHEMA("SCHEMA");

    private final String catalogName;

    ContainerType(final String catalogName) {
        this.catalogName = catalogName;
    }

    /** The canonical object-type string used by the catalog and the privilege store. */
    public String getCatalogName() {
        return catalogName;
    }
}
