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

package dev.frostlake.metastore;

import dev.frostlake.metastore.model.FutureGrant;
import dev.frostlake.metastore.model.Role;

import java.util.List;

/**
 * A schema-level object about to be created: the owner the future grants give it, and the grants they make on it
 * once it is added. {@link #apply} is what changes the catalog, and it runs once, after the object is in its
 * schema. {@link FutureGrants#ofNew} works it out.
 */
public final class CreationGrants {

    private final Catalog catalog;
    private final String objectKind;
    private final String qualified;
    private final boolean takesName;
    private final List<Role> holders;
    private final List<FutureGrant> covering;
    private final String owner;
    private boolean applied;

    CreationGrants(final Catalog catalog, final String objectKind, final String qualified,
                   final boolean takesName, final List<Role> holders, final List<FutureGrant> covering) {
        this.catalog = catalog;
        this.objectKind = objectKind;
        this.qualified = qualified;
        this.takesName = takesName;
        this.holders = holders;
        this.covering = covering;
        this.owner = FutureGrants.ownerAmong(catalog, holders, covering);
    }

    /**
     * The role that owns the new object: a future OWNERSHIP grant's holder, else the current role.
     *
     * @return the owner's name
     */
    public String owner() {
        return owner;
    }

    /**
     * The object is in its schema now: the privileges a dropped object of its name left recorded are taken back,
     * and every future grant that covers it is granted on it. Asked again, it does nothing.
     */
    public void apply() {
        if (applied || qualified == null) {
            return;
        }
        applied = true;
        if (takesName) {
            GrantNames.created(catalog, objectKind, qualified);
        }
        FutureGrants.apply(catalog, objectKind, qualified, owner, holders, covering);
    }
}
