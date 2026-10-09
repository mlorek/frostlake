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

package dev.frostlake.metastore;

import dev.frostlake.executor.StatementClock;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base class for SQL database objects
 * Provides common properties and behavior for objects like databases, schemas, tables, views, etc.
 */
public abstract class SqlObject implements Taggable {

    protected String name;
    protected Instant createdTime;
    protected String comment;
    /** Owning role; defaults to SYSADMIN until stamped with the creating role at CREATE time. */
    protected String owner = "SYSADMIN";
    /** The user whose DDL created this object, as INFORMATION_SCHEMA's LAST_DDL_BY names it; null when none did. */
    private String lastDdlBy;
    /** When the last DDL after the CREATE ran, or null while the CREATE is the last; see {@link #getLastDdlTime}. */
    private Instant lastDdlTime;
    /** Object tags applied via ALTER ... SET TAG (the assignment's key, see TagAssignments -> value). */
    private final Map<String, String> tags = new HashMap<>();

    /**
     * Constructor for SQL objects
     * @param name The name of the object (will be stored as-is, normalization is subclass responsibility)
     */
    protected SqlObject(final String name) {
        this.name = name;
        // The creating statement's instant, as its CURRENT_TIMESTAMP reads it: an object is never created after a
        // later statement's CURRENT_TIMESTAMP, and a replayed statement, given back the instant it first ran at,
        // stamps the object it creates exactly as the original run did.
        this.createdTime = StatementClock.instant();
    }

    /**
     * Constructor with explicit creation time (for testing or deserialization)
     * @param name The name of the object
     * @param createdTime The creation timestamp
     */
    protected SqlObject(final String name, final Instant createdTime) {
        this.name = name;
        this.createdTime = createdTime;
    }

    /**
     * Get the name of this SQL object
     * @return The object name
     */
    public String getName() {
        return name;
    }

    /** Rename this object (the catalog/schema map re-keying is the caller's responsibility). */
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * Get the creation timestamp of this SQL object
     * @return The instant when this object was created
     */
    public Instant getCreatedTime() {
        return createdTime;
    }

    /**
     * Put back when this object was created, as a snapshot recorded it, so an engine clone or a restore reports the
     * original creation time. A null, from a snapshot that never recorded it, keeps the time it was rebuilt.
     *
     * @param created the recorded creation time, or null
     */
    public void restoreCreatedTime(final Instant created) {
        if (created != null) {
            this.createdTime = created;
        }
    }

    /**
     * Get the comment associated with this SQL object
     * @return The comment, or null if no comment is set
     */
    public String getComment() {
        return comment;
    }

    /**
     * Set or update the comment for this SQL object
     * @param comment The comment text
     */
    public void setComment(final String comment) {
        this.comment = comment;
    }

    /**
     * Get the owning role of this SQL object
     * @return The owner role name (defaults to SYSADMIN)
     */
    public String getOwner() {
        return owner;
    }

    /**
     * The user whose DDL created this object — a USER, never a role — or null for an object no statement
     * created, as INFORMATION_SCHEMA's own views are.
     * @return the user name, or null
     */
    public String getLastDdlBy() {
        return lastDdlBy;
    }

    /**
     * Record the user whose DDL created this object (stamped at CREATE time, restored from a snapshot).
     * @param lastDdlBy the user name
     */
    public void setLastDdlBy(final String lastDdlBy) {
        this.lastDdlBy = lastDdlBy;
    }

    /**
     * When the last DDL statement ran on this object, which is INFORMATION_SCHEMA's LAST_DDL. That is its CREATE
     * until an ALTER or a COMMENT ON moves it; a new object's LAST_DDL is its CREATED (documented).
     *
     * @return the instant
     */
    public Instant getLastDdlTime() {
        return lastDdlTime != null ? lastDdlTime : createdTime;
    }

    /**
     * A DDL statement changed this object: LAST_DDL moves to the statement's instant and LAST_DDL_BY to its user. The
     * documentation has every ALTER do so, a comment, a parameter or a column included.
     *
     * @param user the user running the statement, or null to leave LAST_DDL_BY as it is
     */
    public void markDdl(final String user) {
        lastDdlTime = StatementClock.instant();
        if (user != null) {
            lastDdlBy = user;
        }
    }

    /**
     * Put back when the last DDL ran on this object, as a snapshot recorded it, so an engine clone or a restore
     * reports the original LAST_DDL. A null, from a snapshot that never recorded it, leaves LAST_DDL at CREATED.
     *
     * @param lastDdl the recorded last DDL, or null
     */
    public void restoreLastDdlTime(final Instant lastDdl) {
        if (lastDdl != null) {
            this.lastDdlTime = lastDdl;
        }
    }

    /**
     * Set the owning role for this SQL object (stamped with the creating role at CREATE time)
     * @param owner The owner role name
     */
    public void setOwner(final String owner) {
        this.owner = owner;
    }

    @Override
    public void setTag(final String tagName, final String value) {
        tags.put(tagName.toUpperCase(), value);
    }

    @Override
    public void unsetTag(final String tagName) {
        tags.remove(tagName.toUpperCase());
    }

    @Override
    public String getTagValue(final String tagName) {
        return tags.get(tagName.toUpperCase());
    }

    @Override
    public Map<String, String> getTagValues() {
        return new HashMap<>(tags);
    }

    /**
     * Rename this SQL object
     * Note: Subclasses may override to add validation or mark as unsupported
     * @param newName The new name for the object
     */
    public void rename(final String newName) {
        this.name = newName;
    }

    /**
     * Get the type of this SQL object (e.g., "TABLE", "VIEW", "DATABASE")
     * Subclasses should override to provide their specific type
     * @return The object type as a string
     */
    public abstract String getObjectType();

    /**
     * A stored SQL text without its trailing semicolon, for embedding into a reconstructed
     * {@code CREATE OR REPLACE} statement (GET_DDL, VIEW_DEFINITION, SHOW ... text).
     */
    public static String withoutTrailingSemicolon(final String sql) {
        final String trimmed = sql == null ? "" : sql.trim();
        return trimmed.endsWith(";") ? trimmed.substring(0, trimmed.length() - 1).trim() : trimmed;
    }

    @Override
    public String toString() {
        return getObjectType() + " " + name;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final SqlObject other = (SqlObject) obj;
        return name != null && name.equalsIgnoreCase(other.name);
    }

    @Override
    public int hashCode() {
        return name != null ? name.toUpperCase().hashCode() : 0;
    }
}
