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

import dev.frostlake.metastore.model.Integration;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.security.SecurityManager;

/**
 * The USAGE a statement needs on an integration it names: a stage's or a COPY's STORAGE_INTEGRATION, a routine's
 * EXTERNAL_ACCESS_INTEGRATIONS and an external function's API_INTEGRATION (documented, each statement's access control
 * table). The owner holds it, as does a role granted USAGE; ACCOUNTADMIN holds only what it was granted, as for every
 * object. An integration the session may not use is refused as one that is not there, in the sentence live words a
 * missing integration in: {@code Integration 'SI' does not exist or not authorized.}.
 */
public final class IntegrationUsage {

    private IntegrationUsage() {
    }

    /**
     * Whether the session may use an integration.
     *
     * @param security    the session's rights, or null when nothing is checked
     * @param integration the integration named
     * @return whether the session holds USAGE or OWNERSHIP on it
     */
    public static boolean usable(final SecurityManager security, final Integration integration) {
        return security == null || security.holdsPrivilege(Privilege.USAGE, "INTEGRATION", integration.getName(),
            integration.getOwner());
    }

    /**
     * Refuses an integration the session may not use, as one that is not there.
     *
     * @param security    the session's rights, or null when nothing is checked
     * @param integration the integration named
     * @param written     the name as the refusal echoes it
     */
    public static void require(final SecurityManager security, final Integration integration, final String written) {
        if (!usable(security, integration)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Integration", written));
        }
    }
}
