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
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.RoutineEnvironment;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurityObjectKind;

/**
 * What a call asks of the secrets its routine names. Every secret still has to be there, and a routine that
 * names secrets and no external access integration to allow them cannot be called at all; the routine itself
 * may be created and described either way (live-verified).
 */
public final class RoutineSecretsCheck {

    private RoutineSecretsCheck() {
    }

    /**
     * Refuse a call of a routine whose secrets cannot be used.
     *
     * @param catalog     the catalog the secrets live in
     * @param environment what the called routine is given to run with
     */
    public static void require(final Catalog catalog, final RoutineEnvironment environment) {
        if (!environment.hasSecrets()) {
            return;
        }
        for (final String secret : environment.getSecrets().values()) {
            final String[] parts = QualifiedName.parse(secret).parts();
            final String name = parts[parts.length - 1];
            if (!exists(catalog, parts, name)) {
                throw new RuntimeException(SqlCompilationError.of("Object '" + name
                    + "' does not exist or not authorized."));
            }
        }
        if (environment.getExternalAccessIntegrations().isEmpty()) {
            // Told as a function's property for a procedure too.
            throw new RuntimeException(SqlCompilationError.invalidPropertyFor("SECRETS", "FUNCTION"));
        }
    }

    private static boolean exists(final Catalog catalog, final String[] parts, final String name) {
        try {
            final Schema owner = catalog.requireOwningSchema(QualifiedName.of(parts));
            return owner.getSecurityObjects().get(SecurityObjectKind.SECRET, name) != null;
        } catch (final RuntimeException missing) {
            return false;
        }
    }
}
