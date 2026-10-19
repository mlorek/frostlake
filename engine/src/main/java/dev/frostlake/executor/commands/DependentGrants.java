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

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DatabaseRole;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.User;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The grants a REVOKE reaches beside the one it names (documented). A role holding a privilege WITH GRANT OPTION
 * that passes it on makes a grant recorded in its own name, which depends on the grant that gave it the option.
 * When a REVOKE takes that option away - with the privilege, or alone through GRANT OPTION FOR - the grants made
 * through it are left without it: RESTRICT, the default, refuses the REVOKE before anything changes, and CASCADE takes
 * those grants back too, and the grants made through theirs after them. Grants an owner makes stand on its ownership,
 * so revoking a privilege from the object's owner leaves them.
 */
final class DependentGrants {

    private final Catalog catalog;

    /**
     * @param catalog the catalog whose roles and users hold the grants
     */
    DependentGrants(final Catalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Refuses a RESTRICT revoke that would leave a grant made through the grantee's option without it.
     *
     * @param objectType the object's kind as privileges record it
     * @param objectName the object's name as privileges record it
     * @param privilege  the privilege revoked, or whose grant option is
     * @param grantee    the account role losing the option
     * @param owner      the object's owner, whose grants stand on its ownership
     */
    void requireNone(final String objectType, final String objectName, final Privilege privilege,
                     final Role grantee, final String owner) {
        if (!losesOption(objectType, objectName, privilege, grantee, owner)) {
            return;
        }
        for (final Role dependent : roles()) {
            if (madeBy(dependent.recordedPrivilegeGrantor(objectType, objectName, privilege), grantee)
                    && dependent.hasPrivilege(objectType, objectName, privilege)) {
                throw refusal(privilege, objectName, dependent instanceof DatabaseRole ? "database role" : "role",
                    dependent instanceof DatabaseRole ? ((DatabaseRole) dependent).getQualifiedName()
                        : dependent.getName());
            }
        }
        for (final User dependent : catalog.getAllUsers()) {
            if (madeBy(dependent.recordedPrivilegeGrantor(objectType, objectName, privilege), grantee)
                    && dependent.hasPrivilege(objectType, objectName, privilege)) {
                throw refusal(privilege, objectName, "user", dependent.getName());
            }
        }
    }

    /**
     * Whether the grantee holds the grant option a revoke takes away, and grants can have been made through it:
     * the object's owner grants on its ownership instead.
     *
     * @param objectType the object's kind as privileges record it
     * @param objectName the object's name as privileges record it
     * @param privilege  the privilege revoked, or whose grant option is
     * @param grantee    the role losing the option
     * @param owner      the object's owner
     * @return whether grants made in the grantee's name depend on the option
     */
    boolean losesOption(final String objectType, final String objectName, final Privilege privilege,
                        final Role grantee, final String owner) {
        return !(grantee instanceof DatabaseRole) && grantee.hasGrantOption(objectType, objectName, privilege)
            && (owner == null || !catalog.resolveRoleName(owner).equals(grantee.getName()));
    }

    /**
     * Takes back the grants made through an option the grantee lost, and the grants made through theirs.
     *
     * @param objectType the object's kind as privileges record it
     * @param objectName the object's name as privileges record it
     * @param privilege  the privilege whose grants are taken back
     * @param grantee    the account role that lost the option
     */
    void revokeAll(final String objectType, final String objectName, final Privilege privilege, final Role grantee) {
        final Set<String> visited = new HashSet<>();
        visited.add(grantee.getName());
        final List<Role> pending = new ArrayList<>();
        pending.add(grantee);
        while (!pending.isEmpty()) {
            final Role grantor = pending.remove(pending.size() - 1);
            for (final Role dependent : roles()) {
                if (!madeBy(dependent.recordedPrivilegeGrantor(objectType, objectName, privilege), grantor)
                        || !dependent.hasPrivilege(objectType, objectName, privilege)) {
                    continue;
                }
                final boolean passedOn = !(dependent instanceof DatabaseRole)
                    && dependent.hasGrantOption(objectType, objectName, privilege);
                dependent.revokePrivilege(objectType, objectName, privilege);
                if (passedOn && visited.add(dependent.getName())) {
                    pending.add(dependent);
                }
            }
            for (final User dependent : catalog.getAllUsers()) {
                if (madeBy(dependent.recordedPrivilegeGrantor(objectType, objectName, privilege), grantor)
                        && dependent.hasPrivilege(objectType, objectName, privilege)) {
                    catalog.revokePrivilegeFromUser(privilege.name(), objectType, objectName, dependent.getName());
                }
            }
        }
    }

    /** Whether a grant's recorded grantor is the role: grants with no grantor recorded depend on no option. */
    private boolean madeBy(final String recorded, final Role grantor) {
        return recorded != null && catalog.resolveRoleName(recorded).equals(grantor.getName());
    }

    /** The account roles and every database's database roles: whatever a grant on an object can be made to. */
    private List<Role> roles() {
        final List<Role> roles = new ArrayList<>(catalog.getAllRoles());
        for (final Database database : catalog.getAllDatabases()) {
            roles.addAll(database.getDatabaseRoles());
        }
        return roles;
    }

    /** The refusal of a RESTRICT revoke, naming one grant that depends on the option. */
    private static RuntimeException refusal(final Privilege privilege, final String objectName, final String kind,
                                            final String grantee) {
        return new RuntimeException("SQL execution error: Dependent grant of privilege '"
            + privilege.name().replace('_', ' ') + "' on securable '" + objectName + "' to " + kind + " '" + grantee
            + "' exists. It must be revoked first. More than one dependent grant may exist: use CASCADE option to"
            + " revoke them all.");
    }
}
