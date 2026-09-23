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
import java.util.HashMap;
import java.util.LinkedHashMap;

/** An API, catalog, notification, storage, security or external-access integration, as a snapshot holds it. */
public class IntegrationSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The canonical name. */
    public String name;

    /** The integration's kind, as the enum constant's name. */
    public String kind;

    /** Whether it is enabled. */
    public boolean enabled;

    /** The owning role. */
    public String owner;

    /** The comment, or null. */
    public String comment;

    /** The kind-specific properties, in the order they were set. */
    public LinkedHashMap<String, PropertyValueSnapshot> properties = new LinkedHashMap<>();

    /** The tags set on the integration. */
    public HashMap<String, String> tags = new HashMap<>();

    /** When it was created. Null in a snapshot written before it was kept, which restores as the moment of the restore. */
    public Instant createdOn;
}
