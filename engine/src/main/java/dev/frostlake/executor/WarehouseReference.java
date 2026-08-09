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

package dev.frostlake.executor;

import dev.frostlake.metastore.Catalog;

/**
 * Refusing a statement that names a warehouse which is not there.
 *
 * <p>Snowflake validates the reference at CREATE time, and phrases the refusal TWO different ways —
 * live-verified, both against the same missing warehouse:
 *
 * <pre>
 * CREATE TASK … WAREHOUSE = nosuch_wh            -&gt; Nonexistent warehouse NOSUCH_WH was specified.
 * CREATE DYNAMIC TABLE … WAREHOUSE = nosuch_wh   -&gt; Warehouse 'NOSUCH_WH' does not exist.
 * CREATE CORTEX SEARCH SERVICE … WAREHOUSE = …   -&gt; Warehouse 'NOSUCH_WH' does not exist.
 * </pre>
 *
 * <p>Note what is NOT validated: {@code CREATE USER … DEFAULT_WAREHOUSE = nosuch_wh} is ACCEPTED, so a
 * blanket rule over every warehouse-shaped property would be stricter than Snowflake. Only the
 * statements measured above check.
 */
public final class WarehouseReference {

    private WarehouseReference() {
    }

    /**
     * The TASK phrasing: the name bare and upper-cased, no quotes, and no
     * {@code SQL compilation error:} prefix — a task's warehouse check is not a compilation error live.
     */
    public static void requireForTask(final Catalog catalog, final String warehouse) {
        if (warehouse != null && !catalog.hasWarehouse(warehouse)) {
            throw new RuntimeException("Nonexistent warehouse " + warehouse.toUpperCase()
                + " was specified.");
        }
    }

    /**
     * The phrasing every other statement uses. Deliberately NOT
     * {@link SqlCompilationError#doesNotExist}: that shape ends "does not exist or not authorized." and
     * opens with a "SQL compilation error:" line, and this message has neither — measured verbatim as
     * {@code Warehouse 'NOSUCH_WH' does not exist.}
     */
    public static void require(final Catalog catalog, final String warehouse) {
        if (warehouse != null && !catalog.hasWarehouse(warehouse)) {
            throw new RuntimeException("Warehouse '" + warehouse.toUpperCase() + "' does not exist.");
        }
    }
}
