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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.PrivilegeRefusal;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlAccessControlError;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.User;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.security.SecurityManager;

import java.util.Locale;

/**
 * The privileges a statement on the account or on a user asks: MODIFY on the account for ALTER ACCOUNT SET and
 * UNSET, CREATE USER on the account for CREATE USER, and MODIFY on the user for ALTER USER, which users altering
 * themselves need only for what they may not change on themselves (documented). Each is asked once the statement's
 * names and values are judged: a name or a value it refuses is refused first (live-verified).
 */
final class AccountPrivileges {

    private AccountPrivileges() {
    }

    /**
     * Refuses a statement whose session holds no such privilege on the account:
     * {@code Insufficient privileges to operate on account '<ACCOUNT>'. Your primary role R must have MODIFY granted
     * on ACCOUNT <ACCOUNT>.} (live-verified).
     *
     * @param queryExecutor the executor, for the session's rights
     * @param catalog       the catalog, for the account's name
     * @param privilege     the privilege the statement needs
     */
    static void requireOnAccount(final QueryExecutor queryExecutor, final Catalog catalog,
                                 final Privilege privilege) {
        final SecurityManager security = queryExecutor.getSecurityManager();
        if (security == null || security.holdsAccountPrivilege(privilege)) {
            return;
        }
        final String account = catalog.getAccountLocator();
        throw new PrivilegeRefusal(SqlAccessControlError.privilegeRequired("ACCOUNT", account,
            security.getSessionContext().getCurrentRole(), security.getSessionContext().isSecondaryRolesInUse(),
            privilege.name().replace('_', ' '), account));
    }

    /**
     * Refuses an ALTER USER the session may not make: {@code Insufficient privileges to operate on user 'U'. Your
     * primary role R must have MODIFY granted on USER U.} (live-verified). Users may change their own
     * DEFAULT_WAREHOUSE, DEFAULT_NAMESPACE and DEFAULT_ROLE and their session parameters' defaults without it
     * (documented).
     *
     * @param queryExecutor the executor, for the session's rights
     * @param user          the user altered
     * @param action        what the statement does to it
     */
    static void requireOnUser(final QueryExecutor queryExecutor, final User user,
                              final FrostlakeParser.UserActionContext action) {
        final SecurityManager security = queryExecutor.getSecurityManager();
        if (security == null || security.mayModifyUser(user.getName(), user.getOwner())) {
            return;
        }
        final String executing = security.getSessionContext().getExecutingUser();
        if (executing != null && executing.equals(user.getName().toUpperCase(Locale.ROOT)) && ownChange(action)) {
            return;
        }
        throw new PrivilegeRefusal(SqlAccessControlError.privilegeRequired("USER", user.getName(),
            security.getSessionContext().getCurrentRole(), security.getSessionContext().isSecondaryRolesInUse(),
            "MODIFY", user.getName()));
    }

    /**
     * Whether an ALTER USER changes only what users may change on themselves: it sets or unsets DEFAULT_WAREHOUSE,
     * DEFAULT_NAMESPACE, DEFAULT_ROLE and session parameters, and nothing else.
     */
    private static boolean ownChange(final FrostlakeParser.UserActionContext action) {
        if (action.SET() == null && action.UNSET() == null || action.COMMENT() != null) {
            return false;
        }
        for (final FrostlakeParser.UserPropertyContext property : action.userProperty()) {
            if (property.DEFAULT_WAREHOUSE() == null && property.DEFAULT_NAMESPACE() == null
                    && property.DEFAULT_ROLE() == null) {
                return false;
            }
        }
        for (final FrostlakeParser.UserPropertyAssignmentContext assignment : action.userPropertyAssignment()) {
            final FrostlakeParser.UserPropertyNameContext name = assignment.userPropertyName();
            if (name.DEFAULT_WAREHOUSE() == null && name.DEFAULT_NAMESPACE() == null && name.DEFAULT_ROLE() == null) {
                return false;
            }
        }
        for (final FrostlakeParser.UserUnsetPropertyContext unset : action.userUnsetProperty()) {
            final boolean ownProperty = unset.DEFAULT_WAREHOUSE() != null || unset.DEFAULT_NAMESPACE() != null
                || unset.DEFAULT_ROLE() != null;
            final boolean sessionParameter = unset.identifier() != null
                && ParameterRegistry.isSessionParameter(unset.getText().toUpperCase(Locale.ROOT));
            if (!ownProperty && !sessionParameter) {
                return false;
            }
        }
        return true;
    }
}
