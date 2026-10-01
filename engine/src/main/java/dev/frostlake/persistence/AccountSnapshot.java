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
package dev.frostlake.persistence;

import java.io.Serializable;
import java.time.Instant;

/** An account of the organization, dropped or not, as a snapshot holds it. */
public class AccountSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The account name. */
    public String name;

    /** The edition. */
    public String edition;

    /** The region. */
    public String region;

    /** The region group, or null. */
    public String regionGroup;

    /** The comment, or null. */
    public String comment;

    /** The administrator's user name. */
    public String adminName;

    /** The administrator's email. */
    public String email;

    /** The account locator. */
    public String locator;

    /** When the account was created. */
    public Instant createdOn;

    /** When it was dropped, or null. */
    public Instant droppedOn;

    /** When a dropped account is purged, or null. */
    public Instant scheduledDeletionTime;

    /** When a dropped account was restored, or null. */
    public Instant restoredOn;
}
