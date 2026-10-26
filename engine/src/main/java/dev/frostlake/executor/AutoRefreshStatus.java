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
 * SYSTEM$AUTO_REFRESH_STATUS over the table its text names, answered while the statement compiles (live-verified): a
 * NULL is {@code inputs may not be null}; the text is read as an identifier is, so a table the account does not find
 * is refused as any missing one is - {@code Table 'X' does not exist or not authorized.}, a schema or a database that
 * is not there by its own kind - and a table, a view or a temporary table, which refresh nothing of themselves, is
 * {@code invalid parameter 'Auto Refresh is not supported for table t'}, the name as written. What an Iceberg table
 * answers is not measured, and it keeps the active state.
 */
public final class AutoRefreshStatus {

    /** The state an Iceberg table answers. */
    public static final String ACTIVE = "{\"state\": \"Active\"}";

    private AutoRefreshStatus() {
    }

    /**
     * The status of the table a call names, or the refusal of the name.
     *
     * @param catalog  the catalog the name is looked up in
     * @param argument the call's argument, the table's name as written
     * @return the status
     */
    public static String answer(final Catalog catalog, final Object argument) {
        if (argument == null) {
            throw new RuntimeException("inputs may not be null");
        }
        final String written = String.valueOf(argument);
        final String name = SqlIdentifiers.canonicalText(written.trim());
        if (!isView(catalog, name)
                && catalog.resolveTableAsWritten(name, "Table").getIcebergMetadata() != null) {
            return ACTIVE;
        }
        throw new RuntimeException(SqlCompilationError.of("invalid parameter 'Auto Refresh is not supported for table "
            + written + "'"));
    }

    /** Whether a name is a view's, which the call takes as a table that refreshes nothing. */
    private static boolean isView(final Catalog catalog, final String name) {
        try {
            return catalog.resolveView(name) != null;
        } catch (final RuntimeException notAView) {
            return false;
        }
    }
}
