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

import java.time.Instant;

/**
 * One version of a notebook or a Streamlit app: a committed one, {@code VERSION$n}, or the live one, which has no
 * name until it is committed. A version carries the alias it was given, its comment, and the location its files were
 * copied from, as SHOW VERSIONS and DESCRIBE list them.
 */
public class AppObjectVersion {

    private final int number;
    private final String alias;
    private final String comment;
    private final String sourceLocation;
    private final Instant createdOn;

    /**
     * @param number the committed version's number, or 0 for the live version
     * @param alias the alias, or null
     * @param comment the comment, or null
     * @param sourceLocation the location its files came from, as listed, or null for the template
     * @param createdOn when it was added
     */
    public AppObjectVersion(final int number, final String alias, final String comment, final String sourceLocation,
                            final Instant createdOn) {
        this.number = number;
        this.alias = alias;
        this.comment = comment;
        this.sourceLocation = sourceLocation;
        this.createdOn = createdOn;
    }

    /** The committed version's number, or 0 for the live version. */
    public int getNumber() {
        return number;
    }

    /** The version's name, {@code VERSION$n}, or null for the live version. */
    public String getName() {
        return number == 0 ? null : AppObject.versionName(number);
    }

    /** The alias, or null. */
    public String getAlias() {
        return alias;
    }

    /** The comment, or null. */
    public String getComment() {
        return comment;
    }

    /** The location the version's files came from, as SHOW VERSIONS lists it, or null. */
    public String getSourceLocation() {
        return sourceLocation;
    }

    /** When the version was added: committed, created, or opened as the live version. */
    public Instant getCreatedOn() {
        return createdOn;
    }
}
