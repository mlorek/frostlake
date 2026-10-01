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
 * An image repository: a schema object that would hold container images. Frostlake keeps its metadata only; no
 * image is ever pushed to it, so it always lists no images.
 */
public final class ImageRepository {

    private String name;
    private final Instant createdOn;
    private String owner;
    private String comment;
    private String encryption = "SNOWFLAKE_FULL";

    /** A repository of that (canonical) name. */
    public ImageRepository(final String name) {
        this(name, StatementClock.instant());
    }

    /**
     * A repository created at a given moment, as a restored snapshot brings it back.
     *
     * @param name the repository's canonical name
     * @param createdOn when it was created
     */
    public ImageRepository(final String name, final Instant createdOn) {
        this.name = name;
        this.createdOn = createdOn;
    }

    /** The canonical name. */
    public String getName() {
        return name;
    }

    /** Renames the repository. */
    public void setName(final String name) {
        this.name = name;
    }

    /** When it was created. */
    public Instant getCreatedOn() {
        return createdOn;
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

    /** The encryption type: SNOWFLAKE_FULL or SNOWFLAKE_SSE. */
    public String getEncryption() {
        return encryption;
    }

    /** Sets the encryption type. */
    public void setEncryption(final String encryption) {
        this.encryption = encryption;
    }
}
