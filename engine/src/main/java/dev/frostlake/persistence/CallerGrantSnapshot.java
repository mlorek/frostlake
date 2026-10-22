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

/** A caller grant a role holds, as a snapshot holds it. */
public class CallerGrantSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The privilege, as the grant names it, or ALL. */
    public String privilege;

    /** The object's kind, singular, e.g. TABLE. */
    public String objectKind;

    /** The object's name in full, or null for an inherited grant. */
    public String objectName;

    /** ACCOUNT, DATABASE or SCHEMA for an inherited grant, or null. */
    public String inheritedFrom;

    /** The database or schema an inherited grant covers, or null. */
    public String scopeName;

    /** When the grant was made. */
    public Instant createdOn;
}
