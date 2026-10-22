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
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.security.SecurityManager;

/**
 * The APPLY privileges a statement that attaches or detaches a governance object asks: setting or unsetting a masking
 * policy on a column, adding or dropping a row access policy, and setting or unsetting a tag. Owning the object the
 * policy or tag goes on is not enough: the session needs the account's APPLY MASKING POLICY, APPLY ROW ACCESS POLICY
 * or APPLY TAG, or APPLY on the policy or the tag itself (documented: "This privilege controls who can [un]set masking
 * policies on columns", and a table owner may do so only after {@code GRANT APPLY ON MASKING POLICY … TO ROLE
 * table_owner}). Without it a role that may SELECT from a protected table clones it, owns the clone, and detaches the
 * clone's policies. Owning the policy or the tag counts as APPLY on it, as OWNERSHIP counts for every other
 * privilege this engine asks. The refusal's wording is the engine's own form for a missing privilege; live's is not
 * measured.
 */
final class ApplyPrivileges {

    private ApplyPrivileges() {
    }

    /**
     * Refuses attaching or detaching a policy or a tag the session may not apply.
     *
     * @param queryExecutor    the executor, for the session's rights
     * @param accountPrivilege the account's privilege that applies every object of the kind
     * @param kind             the object's kind as privileges record it ({@code MASKING_POLICY}, {@code TAG})
     * @param fullName         the object's name in full
     * @param owner            the role that owns the object, or null when none is recorded, which passes as it
     *                         does for every privilege
     */
    static void require(final QueryExecutor queryExecutor, final Privilege accountPrivilege, final String kind,
                        final String fullName, final String owner) {
        final SecurityManager security = queryExecutor.getSecurityManager();
        if (security == null || security.holdsAccountPrivilege(accountPrivilege)
                || security.holdsPrivilege(Privilege.APPLY, kind, fullName, owner)) {
            return;
        }
        throw new PrivilegeRefusal(SqlAccessControlError.privilegeRequired(kind, QualifiedName.parse(fullName).last(),
            security.getSessionContext().getCurrentRole(), security.getSessionContext().isSecondaryRolesInUse(),
            "APPLY", fullName));
    }
}
