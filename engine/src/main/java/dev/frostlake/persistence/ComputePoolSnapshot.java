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

/** A compute pool, as a snapshot holds it. */
public class ComputePoolSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The canonical name. */
    public String name;

    /** The owning role. */
    public String owner;

    /** The comment, or null. */
    public String comment;

    /** MIN_NODES. */
    public int minNodes;

    /** MAX_NODES. */
    public int maxNodes;

    /** INSTANCE_FAMILY. */
    public String instanceFamily;

    /** AUTO_RESUME. */
    public boolean autoResume;

    /** AUTO_SUSPEND_SECS. */
    public int autoSuspendSecs;

    /** The pool's state, as the enum constant's name. */
    public String state;

    /** The owning application, or null. */
    public String application;

    /** PLACEMENT_GROUP, or null. */
    public String placementGroup;

    /** The backup instance families, or null. */
    public ArrayList<String> backupInstanceFamilies;

    /** The tags set on the pool. */
    public HashMap<String, String> tags = new HashMap<>();

    /** When it was created. Null in a snapshot written before it was kept, which restores as the moment of the restore. */
    public Instant createdOn;

    /** When it was last resumed, or null when it never was. */
    public Instant resumedOn;

    /** When it last changed. */
    public Instant updatedOn;
}
