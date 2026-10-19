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

package dev.frostlake.security;

import java.util.HashSet;
import java.util.Set;

/**
 * A session's roles: the primary role USE ROLE set, the roles it has activated, and what USE SECONDARY ROLES asked for.
 * Each session of an engine keeps its own, so a USE ROLE in one never becomes another's role.
 */
public final class SessionRoles {

    private String currentRole;
    private final Set<String> activeRoles;
    private boolean secondaryRolesInUse;
    private String secondaryRolesValue;

    /** The roles a fresh context starts with: SYSADMIN, and PUBLIC beside it. */
    public SessionRoles() {
        this.currentRole = "SYSADMIN";
        this.activeRoles = new HashSet<String>();
        this.activeRoles.add("SYSADMIN");
        this.activeRoles.add("PUBLIC");
        this.secondaryRolesInUse = false;
        this.secondaryRolesValue = "";
    }

    /** A copy of another session's roles, for a session that takes them over before it sets its own. */
    public SessionRoles(final SessionRoles from) {
        this.currentRole = from.currentRole;
        this.activeRoles = new HashSet<String>(from.activeRoles);
        this.secondaryRolesInUse = from.secondaryRolesInUse;
        this.secondaryRolesValue = from.secondaryRolesValue;
    }

    public String getCurrentRole() {
        return currentRole;
    }

    public void setCurrentRole(final String currentRole) {
        this.currentRole = currentRole;
    }

    /** The activated roles, as the session holds them; callers copy before they hand them out. */
    public Set<String> activeRoles() {
        return activeRoles;
    }

    public boolean isSecondaryRolesInUse() {
        return secondaryRolesInUse;
    }

    public void setSecondaryRolesInUse(final boolean secondaryRolesInUse) {
        this.secondaryRolesInUse = secondaryRolesInUse;
    }

    public String getSecondaryRolesValue() {
        return secondaryRolesValue;
    }

    public void setSecondaryRolesValue(final String secondaryRolesValue) {
        this.secondaryRolesValue = secondaryRolesValue;
    }
}
