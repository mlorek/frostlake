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

package dev.frostlake.functions.scalar.context;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.User;
import dev.frostlake.security.SessionContext;
import dev.frostlake.types.StringType;

import java.util.List;
import java.util.TreeSet;

/**
 * CURRENT_SECONDARY_ROLES() — what USE SECONDARY ROLES last asked of the session, as a JSON object in text:
 * {@code {"roles":"","value":""}} with none, {@code {"roles":"R1,R2","value":"R1,R2"}} for the roles it named and
 * {@code {"roles":"…","value":"ALL"}} for ALL, whose roles are those granted to the session's user but its primary
 * one, in alphabetical order — read when the function is called, so a USE ROLE after it is heard (live-verified).
 */
public class CurrentSecondaryRoles extends BuiltInFunction {

    private static final String ALL = "ALL";
    private static final String PUBLIC = "PUBLIC";

    private final Catalog catalog;
    private final SessionContext sessionContext;

    public CurrentSecondaryRoles(final Catalog catalog, final SessionContext sessionContext) {
        super("CURRENT_SECONDARY_ROLES", StringType.VARCHAR);
        this.catalog = catalog;
        this.sessionContext = sessionContext;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final String value = sessionContext == null ? "" : sessionContext.getSecondaryRolesValue();
        return "{\"roles\":\"" + (ALL.equals(value) ? allRoles() : value) + "\",\"value\":\"" + value + "\"}";
    }

    /** The roles ALL stands for: every role granted to the session's user but the primary one. */
    private String allRoles() {
        final TreeSet<String> roles = new TreeSet<String>();
        try {
            final User user = catalog.getUser(sessionContext.getCurrentUser());
            roles.addAll(user.getGrantedRoles());
        } catch (final RuntimeException noSuchUser) {
            // A session user without a catalog entry was granted no role.
        }
        // PUBLIC is every user's, and is no secondary role the account lists.
        roles.remove(PUBLIC);
        if (sessionContext.getCurrentRole() != null) {
            roles.remove(sessionContext.getCurrentRole());
        }
        return String.join(",", roles);
    }

    @Override
    public int getMinArgCount() {
        return 0;
    }

    @Override
    public int getMaxArgCount() {
        return 0;
    }
}
