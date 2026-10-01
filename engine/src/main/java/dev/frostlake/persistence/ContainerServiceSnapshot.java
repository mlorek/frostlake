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

/** A service or a job service, as a snapshot holds it. */
public class ContainerServiceSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The canonical name. */
    public String name;

    /** Whether it is a job service. */
    public boolean job;

    /** The compute pool it runs in. */
    public String computePool;

    /** The inline specification, or null. */
    public String specification;

    /** The stage holding the specification file, or null. */
    public String specificationStage;

    /** The specification file, or null. */
    public String specificationFile;

    /** Whether the specification is a template. */
    public boolean template;

    /** The status. */
    public String status;

    /** The owning role. */
    public String owner;

    /** The comment, or null. */
    public String comment;

    /** QUERY_WAREHOUSE, or null. */
    public String queryWarehouse;

    /** LOG_LEVEL, or null. */
    public String logLevel;

    /** MIN_INSTANCES, or null. */
    public Long minInstances;

    /** MAX_INSTANCES, or null. */
    public Long maxInstances;

    /** MIN_READY_INSTANCES, or null. */
    public Long minReadyInstances;

    /** AUTO_SUSPEND_SECS, or null. */
    public Long autoSuspendSecs;

    /** AUTO_RESUME, or null. */
    public Boolean autoResume;

    /** Whether a job service runs asynchronously. */
    public boolean asyncJob;

    /** EXTERNAL_ACCESS_INTEGRATIONS. */
    public ArrayList<String> externalAccessIntegrations = new ArrayList<>();

    /**
     * When it was created. Null in a snapshot written before the times were kept, which restores every one
     * of them as the moment of the restore.
     */
    public Instant createdOn;

    /** When it last changed. */
    public Instant updatedOn;

    /** When it was last resumed, or null. */
    public Instant resumedOn;

    /** When it was last suspended, or null. */
    public Instant suspendedOn;
}
