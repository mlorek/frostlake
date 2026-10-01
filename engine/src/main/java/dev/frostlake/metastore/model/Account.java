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

/**
 * An account of the organization, created with CREATE ACCOUNT. The engine serves one account; the others are
 * records of what was created: their name, edition, region, comment and administrator, never a password.
 */
public class Account {

    private final String name;
    private final String edition;
    private final String region;
    private final String regionGroup;
    private final String comment;
    private final String adminName;
    private final String email;
    private final String locator;
    private final Instant createdOn;
    private Instant droppedOn;
    private Instant scheduledDeletionTime;
    private Instant restoredOn;

    /**
     * @param name the account name
     * @param edition the edition
     * @param region the region, or null for the organization's own
     * @param regionGroup the region group, or null
     * @param comment the comment, or null
     * @param adminName the administrator's user name
     * @param email the administrator's email
     * @param locator the locator assigned to the account
     */
    public Account(final String name, final String edition, final String region, final String regionGroup,
                   final String comment, final String adminName, final String email, final String locator) {
        this(name, edition, region, regionGroup, comment, adminName, email, locator, StatementClock.instant());
    }

    /**
     * An account as a snapshot recorded it, keeping its creation time.
     *
     * @param createdOn when the account was created
     */
    public Account(final String name, final String edition, final String region, final String regionGroup,
                   final String comment, final String adminName, final String email, final String locator,
                   final Instant createdOn) {
        this.name = name;
        this.edition = edition;
        this.region = region;
        this.regionGroup = regionGroup;
        this.comment = comment;
        this.adminName = adminName;
        this.email = email;
        this.locator = locator;
        this.createdOn = createdOn;
    }

    public String getName() {
        return name;
    }

    public String getEdition() {
        return edition;
    }

    public String getRegion() {
        return region;
    }

    public String getRegionGroup() {
        return regionGroup;
    }

    public String getComment() {
        return comment;
    }

    public String getAdminName() {
        return adminName;
    }

    public String getEmail() {
        return email;
    }

    public String getLocator() {
        return locator;
    }

    public Instant getCreatedOn() {
        return createdOn;
    }

    /** When the account was dropped, or null while it is not dropped. */
    public Instant getDroppedOn() {
        return droppedOn;
    }

    /** When a dropped account is deleted for good, or null. */
    public Instant getScheduledDeletionTime() {
        return scheduledDeletionTime;
    }

    /** When the account was last restored, or null. */
    public Instant getRestoredOn() {
        return restoredOn;
    }

    /** Whether the account is dropped and awaiting deletion. */
    public boolean isDropped() {
        return droppedOn != null;
    }

    /** Drops the account, restorable for the grace period. */
    public void drop(final int gracePeriodInDays) {
        droppedOn = StatementClock.instant();
        scheduledDeletionTime = droppedOn.plusSeconds(86400L * gracePeriodInDays);
    }

    /** Puts back the drop and restore times a snapshot recorded. */
    public void restoreLifecycle(final Instant dropped, final Instant scheduledDeletion, final Instant restored) {
        this.droppedOn = dropped;
        this.scheduledDeletionTime = scheduledDeletion;
        this.restoredOn = restored;
    }

    /** Restores the dropped account. */
    public void restore() {
        droppedOn = null;
        scheduledDeletionTime = null;
        restoredOn = StatementClock.instant();
    }
}
