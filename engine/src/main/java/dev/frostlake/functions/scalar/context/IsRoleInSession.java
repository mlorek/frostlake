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
import dev.frostlake.metastore.model.Role;
import dev.frostlake.security.SessionContext;
import dev.frostlake.types.BooleanType;

import java.util.List;

/**
 * IS_ROLE_IN_SESSION(name) — TRUE when the session acts with the named role: its current role, a role granted to
 * that one directly or through another, or PUBLIC. The name is read as {@link RoleNameText} reads it; one that
 * names no role is FALSE, and a NULL from a row answers NULL. The bare word NULL and a number are refused while the
 * statement compiles (see the visitor's argument rules).
 */
public class IsRoleInSession extends BuiltInFunction {

    private final Catalog catalog;
    private final SessionContext sessionContext;

    /**
     * @param catalog the catalog holding the roles
     * @param sessionContext the session whose roles are asked about
     */
    public IsRoleInSession(final Catalog catalog, final SessionContext sessionContext) {
        super("IS_ROLE_IN_SESSION", BooleanType.BOOLEAN);
        this.catalog = catalog;
        this.sessionContext = sessionContext;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (RoleNameText.isNull(args.get(0))) {
            return null;
        }
        final String name = RoleNameText.of(args.get(0));
        if (name == null) {
            return Boolean.FALSE;
        }
        if ("PUBLIC".equals(name) || name.equals(SessionRoles.current(sessionContext))) {
            return Boolean.TRUE;
        }
        for (final Role held : SessionRoles.held(catalog, sessionContext)) {
            if (held.getName().equals(name)) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
