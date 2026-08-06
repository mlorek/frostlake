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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializable snapshot of user metadata
 */
public class UserSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public String password;
    public String defaultRole;
    public List<String> grantedRoles = new ArrayList<>();
    public String comment;
    public LocalDateTime createdAt;
    // Login-enabled flag as a nullable Boolean: an old snapshot that predates the field deserializes it
    // as null, which the restore path reads as enabled=true (a legacy user was never disabled). A boolean
    // here would deserialize to false and silently disable every restored user.
    public Boolean enabled;
    public String owner;
    // Privileges granted directly to this user (object- and column-level). Null on old snapshots.
    public List<PrivilegeSnapshot> privileges;

    // ── The CREATE USER property set ─────────────────────────────────────────────────────────────
    // One nullable marker covers the whole group: a writer that knows these fields sets it TRUE, so
    // the reader can apply them VERBATIM — including the nulls, which is what makes an explicitly
    // unset property survive a round trip. A snapshot predating the group deserializes it as null,
    // and the reader then leaves the restored user's own defaults alone rather than nulling them.
    public Boolean propertiesWritten;
    public String loginName;
    public String displayName;
    public String firstName;
    public String middleName;
    public String lastName;
    public String email;
    public String defaultWarehouse;
    public String defaultNamespace;
    public String defaultSecondaryRoles;
    public boolean mustChangePassword;
    public String userType;
}
