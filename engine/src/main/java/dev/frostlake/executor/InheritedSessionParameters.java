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
import dev.frostlake.security.InheritedParameters;

/**
 * Reads what a session inherits for a parameter from the catalog: the value ALTER USER set on its user, else the one
 * ALTER ACCOUNT set. A value is handed over as a session holds its own — a NUMBER parameter's as a Long, a BOOLEAN
 * one's as a Boolean, any other as its text.
 */
public final class InheritedSessionParameters implements InheritedParameters {

    private final Catalog catalog;

    /**
     * @param catalog the catalog that keeps the users and the account's parameters
     */
    public InheritedSessionParameters(final Catalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public Object value(final String user, final String name) {
        String text = user != null && catalog.hasUser(user) ? catalog.getUser(user).getParameters().get(name) : null;
        if (text == null) {
            text = catalog.getAccountParameters().get(name);
        }
        return text == null ? null : typed(name, text);
    }

    private static Object typed(final String name, final String text) {
        final SessionParameterRow row = SessionParameterCatalog.find(name);
        if (row != null && "NUMBER".equals(row.getType())) {
            try {
                return Long.valueOf(text);
            } catch (final NumberFormatException kept) {
                return text;
            }
        }
        return row != null && "BOOLEAN".equals(row.getType()) ? (Object) Boolean.valueOf(text) : text;
    }
}
