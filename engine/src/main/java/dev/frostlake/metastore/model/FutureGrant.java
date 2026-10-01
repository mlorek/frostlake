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
 * A privilege granted on the objects of one kind that are yet to be created in a schema or a database:
 * {@code GRANT <privilege> ON FUTURE <kinds> IN SCHEMA | DATABASE <name>}.
 */
public final class FutureGrant {

    private final String objectKind;
    private final String scopeKind;
    private final String scopeName;
    private final String privilege;
    private final Instant createdOn;
    private boolean grantOption;

    /**
     * @param objectKind the kind of the future objects, singular (TABLE, SCHEMA, ...)
     * @param scopeKind SCHEMA or DATABASE
     * @param scopeName the schema's qualified name or the database's name
     * @param privilege the privilege, as the grant names it
     * @param grantOption whether the grant carries the grant option
     * @param createdOn when the future grant was made
     */
    public FutureGrant(final String objectKind, final String scopeKind, final String scopeName,
                       final String privilege, final boolean grantOption, final Instant createdOn) {
        this.objectKind = objectKind;
        this.scopeKind = scopeKind;
        this.scopeName = scopeName;
        this.privilege = privilege;
        this.grantOption = grantOption;
        this.createdOn = createdOn;
    }

    /** Whether this grant is the one on these future objects of that privilege. */
    public boolean matches(final String kind, final String scope, final String name, final String privilegeName) {
        return objectKind.equals(kind) && scopeKind.equals(scope) && scopeName.equals(name)
            && privilege.equals(privilegeName);
    }

    /** The kind of the future objects, singular. */
    public String getObjectKind() {
        return objectKind;
    }

    /** SCHEMA or DATABASE. */
    public String getScopeKind() {
        return scopeKind;
    }

    /** The schema's qualified name or the database's name. */
    public String getScopeName() {
        return scopeName;
    }

    /** The privilege, as the grant names it. */
    public String getPrivilege() {
        return privilege;
    }

    /** When the grant was made. */
    public Instant getCreatedOn() {
        return createdOn;
    }

    /** Whether the grant carries the grant option. */
    public boolean hasGrantOption() {
        return grantOption;
    }

    /** Records or takes back the grant option. */
    public void setGrantOption(final boolean grantOption) {
        this.grantOption = grantOption;
    }
}
