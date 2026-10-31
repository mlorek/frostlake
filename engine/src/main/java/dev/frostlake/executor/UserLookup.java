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
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.User;
import dev.frostlake.security.SecurityManager;

/**
 * The user named by a statement that works under an account privilege. A missing user is refused in one sentence,
 * and only a role without that privilege hears a second, the hint asking for it: MANAGE GRANTS for the GRANT, REVOKE
 * and SHOW statements over a user's grants and parameters, APPLY TAG for an ALTER USER that sets or unsets a tag
 * (live-verified role by role: a custom role holding the privilege hears no more than ACCOUNTADMIN does). DROP USER,
 * DESCRIBE USER, COMMENT ON USER and every other ALTER USER ask nothing of any role, and find the user in the catalog.
 */
public final class UserLookup {

    private UserLookup() {
    }

    /**
     * The user of exactly that canonical name.
     *
     * @param catalog the catalog
     * @param security the session's rights, or null when none are kept
     * @param name the user's canonical name
     * @param privilege the account privilege the statement works under
     * @return the user
     */
    public static User asking(final Catalog catalog, final SecurityManager security, final String name,
                              final Privilege privilege) {
        if (catalog.hasUserExact(name) || security == null || security.holdsAccountPrivilege(privilege)) {
            return catalog.getUserExact(name);
        }
        throw new RuntimeException(SqlCompilationError.userDoesNotExistAsking(name, privilege.displayName()));
    }
}
