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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the current user session context including active user and role
 */
public class SessionContext {

    private volatile String currentStatement;
    private volatile String lastTransactionId;

    /** The SQL text of the statement this session is currently executing. */
    public String getCurrentStatement() {
        return currentStatement;
    }

    public void setCurrentStatement(final String currentStatement) {
        this.currentStatement = currentStatement;
    }

    /** The id of the session's last completed (committed or rolled-back) transaction. */
    public String getLastTransactionId() {
        return lastTransactionId;
    }

    public void setLastTransactionId(final String lastTransactionId) {
        this.lastTransactionId = lastTransactionId;
    }

    private final String sessionId = String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits()));
    private String currentUser;
    private String currentRole;
    private String displayUser;
    private final Set<String> activeRoles;
    private boolean securityEnabled;
    private final Map<String, Object> sessionParameters;
    /**
     * The session VARIABLES SET defined, in the order SET created them. They are stored apart from the
     * session parameters: a variable and a parameter may carry the same name without either seeing the
     * other, so {@code SET timezone = 'abc'} leaves the TIMEZONE parameter — and the session's clock —
     * untouched, and an ALTER SESSION parameter never becomes readable as {@code $name}.
     */
    private final Map<String, Object> sessionVariables = new LinkedHashMap<>();

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
        sessionVariables.clear();
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

    /** Define, or redefine, the session variable SET names. */
    public void setSessionVariable(final String name, final Object value) {
        if (name != null) {
            sessionVariables.put(name.toUpperCase(), value);
        }
    }

    /** A session variable's value, or null when SET never defined one of that name. */
    public Object getSessionVariable(final String name) {
        if (name == null) {
            return null;
        }
        return sessionVariables.get(name.toUpperCase());
    }

    /** Forget a session variable UNSET removed. Removing a name no SET defined is silently accepted. */
    public void unsetSessionVariable(final String name) {
        if (name != null) {
            sessionVariables.remove(name.toUpperCase());
        }
    }

    /**
     * Whether SET defined a session variable of this name. A session PARAMETER is not one: live refuses
     * {@code $TIMEZONE} and {@code $QUERY_TAG} unless a SET made a variable of that name.
     */
    public boolean isSessionVariable(final String name) {
        return name != null && sessionVariables.containsKey(name.toUpperCase());
    }

    public Map<String, Object> getAllSessionParameters() {
        return new HashMap<>(sessionParameters);
    }

    /** Every session variable SET defined, in creation order — what SHOW VARIABLES lists. */
    public Map<String, Object> getAllSessionVariables() {
        return new LinkedHashMap<>(sessionVariables);
    }
}
