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

/** A notebook or a Streamlit app, as a snapshot holds it. */
public class AppObjectSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The object's kind, as the enum constant's name. */
    public String kind;

    /** The canonical name. */
    public String name;

    /** The owning role. */
    public String owner;

    /** The comment, or null. */
    public String comment;

    /** FROM, or null. */
    public String fromLocation;

    /** ROOT_LOCATION, or null. */
    public String rootLocation;

    /** MAIN_FILE, or null. */
    public String mainFile;

    /** QUERY_WAREHOUSE, or null. */
    public String queryWarehouse;

    /** WAREHOUSE, or null. */
    public String warehouse;

    /** RUNTIME_NAME, or null. */
    public String runtimeName;

    /** COMPUTE_POOL, or null. */
    public String computePool;

    /** TITLE, or null. */
    public String title;

    /** IDLE_AUTO_SHUTDOWN_TIME_SECONDS, or null. */
    public Long idleAutoShutdownTimeSeconds;

    /** IMPORTS. */
    public ArrayList<String> imports = new ArrayList<>();

    /** EXTERNAL_ACCESS_INTEGRATIONS. */
    public ArrayList<String> externalAccessIntegrations = new ArrayList<>();

    /** How many versions the object has. */
    public int versionCount;

    /** Whether it has a live version. */
    public boolean liveVersion;
    /** The committed versions. Null in a snapshot written before they were kept, which restores versionCount. */
    public ArrayList<AppObjectVersionSnapshot> versions;
    /** The live version, or null; read only when {@link #versions} is present. */
    public AppObjectVersionSnapshot live;

    /** When it was created, on the host's wall clock. Null in a snapshot written before it was kept, which restores as the moment of the restore. */
    public LocalDateTime createdOn;

    /** The URL id. Null in a snapshot written before it was kept, which restores with a new one. */
    public String urlId;

    /** When it was dropped, for an object UNDROP can still restore; null for a live one. */
    public LocalDateTime droppedOn;
}
