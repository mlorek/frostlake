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
package dev.frostlake.metastore.model;

import java.time.Instant;

/**
 * A caller grant a role holds: one of its caller's privileges an executable the role owns may use while it runs with
 * restricted caller's rights - {@code GRANT CALLER <privilege> ON <kind> <name>} on one object, or
 * {@code GRANT INHERITED CALLER <privilege> ON ALL <kinds> IN ACCOUNT | DATABASE d | SCHEMA s} on every object of a
 * kind there; the privilege ALL stands for every one (documented).
 */
public final class CallerGrant {

    private final String privilege;
    private final String objectKind;
    private final String objectName;
    private final String inheritedFrom;
    private final String scopeName;
    private final Instant createdOn;

    /**
     * @param privilege     the privilege, as the grant names it, or ALL
     * @param objectKind    the object's kind, singular (TABLE, WAREHOUSE, ...)
     * @param objectName    the object's name in full for a grant on one object, null for an inherited one
     * @param inheritedFrom ACCOUNT, DATABASE or SCHEMA for an inherited grant, null for one on an object
     * @param scopeName     the database's name or the schema's in full an inherited grant covers, null for the account
     * @param createdOn     when the grant was made
     */
    public CallerGrant(final String privilege, final String objectKind, final String objectName,
                       final String inheritedFrom, final String scopeName, final Instant createdOn) {
        this.privilege = privilege;
        this.objectKind = objectKind;
        this.objectName = objectName;
        this.inheritedFrom = inheritedFrom;
        this.scopeName = scopeName;
        this.createdOn = createdOn;
    }

    /** Whether this grant is the one of that privilege on those objects. */
    public boolean matches(final String privilegeName, final String kind, final String name, final String from,
                           final String scope) {
        return privilege.equals(privilegeName) && objectKind.equals(kind) && same(objectName, name)
            && same(inheritedFrom, from) && same(scopeName, scope);
    }

    /**
     * Whether this grant lets an executable use a privilege on an object.
     *
     * @param privilegeName the privilege the statement needs
     * @param kind          the object's kind, singular
     * @param name          the object's name in full
     * @param database      the database the object lives in, or null for one no database holds
     * @param schema        the schema the object lives in, in full, or null for one no schema holds
     * @return whether it does
     */
    public boolean covers(final String privilegeName, final String kind, final String name, final String database,
                          final String schema) {
        if (!objectKind.equals(kind) || !"ALL".equals(privilege) && !privilege.equals(privilegeName)) {
            return false;
        }
        if (inheritedFrom == null) {
            return objectName.equals(name);
        }
        if ("ACCOUNT".equals(inheritedFrom)) {
            return true;
        }
        return "DATABASE".equals(inheritedFrom) ? scopeName.equals(database) : scopeName.equals(schema);
    }

    private static boolean same(final String a, final String b) {
        return a == null ? b == null : a.equals(b);
    }

    /** The privilege, as the grant names it, or ALL. */
    public String getPrivilege() {
        return privilege;
    }

    /** The object's kind, singular. */
    public String getObjectKind() {
        return objectKind;
    }

    /** The object's name in full, or null for an inherited grant. */
    public String getObjectName() {
        return objectName;
    }

    /** ACCOUNT, DATABASE or SCHEMA for an inherited grant, or null. */
    public String getInheritedFrom() {
        return inheritedFrom;
    }

    /** The database's name or the schema's in full an inherited grant covers, or null. */
    public String getScopeName() {
        return scopeName;
    }

    /** When the grant was made. */
    public Instant getCreatedOn() {
        return createdOn;
    }
}
