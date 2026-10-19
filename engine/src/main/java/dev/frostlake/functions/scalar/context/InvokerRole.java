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
import dev.frostlake.security.SessionContext;
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * INVOKER_ROLE() — the role that owns the object executing: a view's owner while the view's body runs, a function's
 * inside its body, an owner's-rights procedure's inside the procedure, and the session's current role outside them,
 * a caller's-rights procedure included, as Snowflake's INVOKER_ROLE page lays out. A policy on a table read through a
 * view therefore sees the view's owner. An owner that is a database role is no account role, and the answer is NULL.
 */
public class InvokerRole extends BuiltInFunction {

    private final SessionContext sessionContext;
    private final Catalog catalog;

    public InvokerRole(final SessionContext sessionContext, final Catalog catalog) {
        super("INVOKER_ROLE", StringType.VARCHAR);
        this.sessionContext = sessionContext;
        this.catalog = catalog;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (sessionContext == null) {
            return "SYSADMIN";
        }
        final String owner = sessionContext.getInvokerOwner();
        if (owner == null) {
            return sessionContext.getCurrentRole();
        }
        if (catalog != null && catalog.roleExists(owner)) {
            return catalog.resolveRoleName(owner);
        }
        return owner.indexOf('.') >= 0 ? null : owner;
    }

    @Override
    public int getMinArgCount() { return 0; }
    @Override
    public int getMaxArgCount() { return 0; }
}
