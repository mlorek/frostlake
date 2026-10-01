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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializable snapshot of database metadata
 */
public class DatabaseSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public String comment;
    public Instant createdAt;
    // READ ONLY flag. Primitive → old snapshots (predating this field) deserialize it as false (writable).
    public boolean readOnly;
    public List<SchemaSnapshot> schemas = new ArrayList<>();

    // Null on snapshots that predate the field (deserialization bypasses field initializers): restore null-checks.
    public Integer dataRetentionTimeInDays;
    public boolean transientObject;
    public HashMap<String, String> parameters = new HashMap<>();
    public List<RoleSnapshot> databaseRoles = new ArrayList<>();

    // The object's tag associations, tag name -> value. Null in a snapshot written before tags were
    // recorded, which reads back as an object carrying none.
    public Map<String, String> tags;
}
