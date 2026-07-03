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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the current user session context including active user and role
 */
public class SessionContext {
    private final String sessionId = String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits()));
    private String currentUser;
    private String currentRole;
    private String displayUser;
    private final Set<String> activeRoles;
    private boolean securityEnabled;
    private final Map<String, Object> sessionParameters;

    public SessionContext() {
        this.activeRoles = new HashSet<>();
        this.securityEnabled = true;
        this.sessionParameters = new HashMap<>();
        // By default, start as a system admin
        this.currentUser = "SYSTEM";
        this.displayUser = "SYSTEM";
        this.currentRole = "SYSADMIN";
        this.activeRoles.add("SYSADMIN");
        this.activeRoles.add("PUBLIC");
        // Initialize default session parameters
        this.sessionParameters.put("MULTI_STATEMENT_COUNT", 1);
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(final String currentUser) {
        this.currentUser = currentUser != null ? currentUser.toUpperCase() : null;
    }

    /** Display name returned by CURRENT_USER() — may differ from the internal security user. */
    public String getDisplayUser() {
        return displayUser != null ? displayUser : currentUser;
    }

    public void setDisplayUser(final String displayUser) {
        this.displayUser = displayUser != null ? displayUser.toUpperCase() : null;
    }

    public String getCurrentRole() {
        return currentRole;
    }

    public void setCurrentRole(final String currentRole) {
        this.currentRole = currentRole != null ? currentRole.toUpperCase() : null;
        if (currentRole != null) {
            activeRoles.add(currentRole.toUpperCase());
        }
    }

    public Set<String> getActiveRoles() {
        return new HashSet<>(activeRoles);
    }

    public void addActiveRole(final String role) {
        if (role != null) {
            activeRoles.add(role.toUpperCase());
        }
    }

    public void clearActiveRoles() {
        activeRoles.clear();
        // Always keep PUBLIC role
        activeRoles.add("PUBLIC");
    }

    public boolean isSecurityEnabled() {
        return securityEnabled;
    }

    public void setSecurityEnabled(final boolean securityEnabled) {
        this.securityEnabled = securityEnabled;
    }

    public void reset() {
        currentUser = "SYSTEM";
        displayUser = "SYSTEM";
        currentRole = "SYSADMIN";
        activeRoles.clear();
        activeRoles.add("SYSADMIN");
        activeRoles.add("PUBLIC");
        securityEnabled = true;
        sessionParameters.clear();
        sessionParameters.put("MULTI_STATEMENT_COUNT", 1);
    }

    public void setSessionParameter(final String name, final Object value) {
        if (name != null) {
            sessionParameters.put(name.toUpperCase(), value);
        }
    }

    public Object getSessionParameter(final String name) {
        if (name == null) {
            return null;
        }
        return sessionParameters.get(name.toUpperCase());
    }

    public void unsetSessionParameter(final String name) {
        if (name != null) {
            sessionParameters.remove(name.toUpperCase());
        }
    }

    public Map<String, Object> getAllSessionParameters() {
        return new HashMap<>(sessionParameters);
    }
}
