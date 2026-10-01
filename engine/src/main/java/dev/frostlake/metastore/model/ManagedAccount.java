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

import dev.frostlake.executor.StatementClock;

import java.time.Instant;

/** A reader account created with CREATE MANAGED ACCOUNT: its name, administrator, comment and locator. */
public class ManagedAccount {

    private final String name;
    private final String adminName;
    private final String comment;
    private final String locator;
    private final Instant createdOn;

    /**
     * @param name the managed account's name
     * @param adminName the administrator's user name
     * @param comment the comment, or null
     * @param locator the locator assigned to the account
     */
    public ManagedAccount(final String name, final String adminName, final String comment, final String locator) {
        this(name, adminName, comment, locator, StatementClock.instant());
    }

    /**
     * A managed account as a snapshot recorded it, keeping its creation time.
     *
     * @param createdOn when the account was created
     */
    public ManagedAccount(final String name, final String adminName, final String comment, final String locator,
                          final Instant createdOn) {
        this.name = name;
        this.adminName = adminName;
        this.comment = comment;
        this.locator = locator;
        this.createdOn = createdOn;
    }

    public String getName() {
        return name;
    }

    public String getAdminName() {
        return adminName;
    }

    public String getComment() {
        return comment;
    }

    public String getLocator() {
        return locator;
    }

    public Instant getCreatedOn() {
        return createdOn;
    }
}
