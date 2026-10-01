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
 * An artifact repository: a schema object naming a package index (TYPE = PYPI) reached through an API integration,
 * or the application artifacts (TYPE = APPLICATION). Frostlake keeps its metadata only.
 */
public final class ArtifactRepository {

    private final String name;
    private final Instant createdOn;
    private final String type;
    private String apiIntegration;
    private String owner;
    private String comment;

    /**
     * @param name the canonical name
     * @param type the repository type, APPLICATION or PYPI
     */
    public ArtifactRepository(final String name, final String type) {
        this(name, type, StatementClock.instant());
    }

    /**
     * A repository created at a given moment, as a restored snapshot brings it back.
     *
     * @param name the repository's canonical name
     * @param type its package type
     * @param createdOn when it was created
     */
    public ArtifactRepository(final String name, final String type, final Instant createdOn) {
        this.name = name;
        this.type = type;
        this.createdOn = createdOn;
    }

    /** The canonical name. */
    public String getName() {
        return name;
    }

    /** When it was created. */
    public Instant getCreatedOn() {
        return createdOn;
    }

    /** The repository type. */
    public String getType() {
        return type;
    }

    /** The API integration it reaches the index through, or null. */
    public String getApiIntegration() {
        return apiIntegration;
    }

    /** Sets the API integration. */
    public void setApiIntegration(final String apiIntegration) {
        this.apiIntegration = apiIntegration;
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
}
