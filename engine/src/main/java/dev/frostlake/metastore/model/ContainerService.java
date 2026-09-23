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
import java.util.ArrayList;
import java.util.List;

/**
 * A Snowpark Container Services service or job service, kept as its declared state: the compute pool it names, its
 * specification, and its properties. No container ever runs, so a service reports no instances or containers, and a
 * job service never leaves the state it was recorded in.
 */
public final class ContainerService {

    private final String name;
    private final boolean job;
    private final Instant createdOn;
    private Instant updatedOn;
    private Instant resumedOn;
    private Instant suspendedOn;
    private String computePool;
    private String specification;
    private String specificationStage;
    private String specificationFile;
    private boolean template;
    private String status;
    private String owner;
    private String comment;
    private String queryWarehouse;
    private String logLevel;
    private Long minInstances;
    private Long maxInstances;
    private Long minReadyInstances;
    private Long autoSuspendSecs;
    private Boolean autoResume;
    private boolean asyncJob;
    private List<String> externalAccessIntegrations = new ArrayList<>();

    /**
     * @param name the canonical name
     * @param job whether it is a job service
     */
    public ContainerService(final String name, final boolean job) {
        this(name, job, StatementClock.instant());
    }

    /**
     * A service created at a given moment, as a restored snapshot brings it back.
     *
     * @param name the canonical name
     * @param job whether it is a job service
     * @param createdOn when it was created
     */
    public ContainerService(final String name, final boolean job, final Instant createdOn) {
        this.name = name;
        this.job = job;
        this.createdOn = createdOn;
        this.updatedOn = createdOn;
    }

    /** The canonical name. */
    public String getName() {
        return name;
    }

    /** Whether it is a job service. */
    public boolean isJob() {
        return job;
    }

    /** When it was created. */
    public Instant getCreatedOn() {
        return createdOn;
    }

    /** When it last changed. */
    public Instant getUpdatedOn() {
        return updatedOn;
    }

    /** Records a change. */
    public void touch() {
        this.updatedOn = StatementClock.instant();
    }

    /**
     * Puts back the change, resume and suspension times a restored snapshot recorded, over the ones
     * restoring the status stamped.
     */
    public void restoreTimes(final Instant updated, final Instant resumed, final Instant suspended) {
        this.updatedOn = updated;
        this.resumedOn = resumed;
        this.suspendedOn = suspended;
    }

    /** When it was last resumed, or null. */
    public Instant getResumedOn() {
        return resumedOn;
    }

    /** When it was last suspended, or null. */
    public Instant getSuspendedOn() {
        return suspendedOn;
    }

    /** The status SHOW SERVICES reports: PENDING, SUSPENDED, DONE, … */
    public String getStatus() {
        return status;
    }

    /** Sets the status, stamping a resume or a suspension. */
    public void setStatus(final String status) {
        if ("SUSPENDED".equals(status)) {
            suspendedOn = StatementClock.instant();
        } else if ("SUSPENDED".equals(this.status)) {
            resumedOn = StatementClock.instant();
        }
        this.status = status;
    }

    /** The compute pool it runs in. */
    public String getComputePool() {
        return computePool;
    }

    /** Sets the compute pool. */
    public void setComputePool(final String computePool) {
        this.computePool = computePool;
    }

    /** The inline specification text, or null when it comes from a staged file. */
    public String getSpecification() {
        return specification;
    }

    /** The stage holding the specification file, or null. */
    public String getSpecificationStage() {
        return specificationStage;
    }

    /** The staged specification file, or null. */
    public String getSpecificationFile() {
        return specificationFile;
    }

    /** Whether the specification is a template. */
    public boolean isTemplate() {
        return template;
    }

    /**
     * Sets where the specification comes from.
     *
     * @param text the inline text, or null
     * @param stage the stage of a staged file, or null
     * @param file the staged file, or null
     * @param isTemplate whether it is a template
     */
    public void setSpecification(final String text, final String stage, final String file,
                                 final boolean isTemplate) {
        this.specification = text;
        this.specificationStage = stage;
        this.specificationFile = file;
        this.template = isTemplate;
    }

    /** The owning role, or null. */
    public String getOwner() {
        return owner;
    }

    /** Sets the owning role. */
    public void setOwner(final String owner) {
        this.owner = owner;
    }

    /** The comment, or null. */
    public String getComment() {
        return comment;
    }

    /** Sets the comment; null clears it. */
    public void setComment(final String comment) {
        this.comment = comment;
    }

    /** The warehouse its queries run on, or null. */
    public String getQueryWarehouse() {
        return queryWarehouse;
    }

    /** Sets the query warehouse. */
    public void setQueryWarehouse(final String queryWarehouse) {
        this.queryWarehouse = queryWarehouse;
    }

    /** The log level, or null. */
    public String getLogLevel() {
        return logLevel;
    }

    /** Sets the log level. */
    public void setLogLevel(final String logLevel) {
        this.logLevel = logLevel;
    }

    /** MIN_INSTANCES, or null for the default. */
    public Long getMinInstances() {
        return minInstances;
    }

    /** Sets MIN_INSTANCES. */
    public void setMinInstances(final Long minInstances) {
        this.minInstances = minInstances;
    }

    /** MAX_INSTANCES, or null for the default. */
    public Long getMaxInstances() {
        return maxInstances;
    }

    /** Sets MAX_INSTANCES. */
    public void setMaxInstances(final Long maxInstances) {
        this.maxInstances = maxInstances;
    }

    /** MIN_READY_INSTANCES, or null for the default. */
    public Long getMinReadyInstances() {
        return minReadyInstances;
    }

    /** Sets MIN_READY_INSTANCES. */
    public void setMinReadyInstances(final Long minReadyInstances) {
        this.minReadyInstances = minReadyInstances;
    }

    /** AUTO_SUSPEND_SECS, or null for the default. */
    public Long getAutoSuspendSecs() {
        return autoSuspendSecs;
    }

    /** Sets AUTO_SUSPEND_SECS. */
    public void setAutoSuspendSecs(final Long autoSuspendSecs) {
        this.autoSuspendSecs = autoSuspendSecs;
    }

    /** AUTO_RESUME, or null for the default. */
    public Boolean getAutoResume() {
        return autoResume;
    }

    /** Sets AUTO_RESUME. */
    public void setAutoResume(final Boolean autoResume) {
        this.autoResume = autoResume;
    }

    /** Whether a job service was started with ASYNC = TRUE. */
    public boolean isAsyncJob() {
        return asyncJob;
    }

    /** Sets whether a job service runs asynchronously. */
    public void setAsyncJob(final boolean asyncJob) {
        this.asyncJob = asyncJob;
    }

    /** The external access integrations. */
    public List<String> getExternalAccessIntegrations() {
        return new ArrayList<>(externalAccessIntegrations);
    }

    /** Sets the external access integrations. */
    public void setExternalAccessIntegrations(final List<String> integrations) {
        this.externalAccessIntegrations = new ArrayList<>(integrations);
    }
}
