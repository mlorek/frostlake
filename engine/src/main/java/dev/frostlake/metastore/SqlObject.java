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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base class for SQL database objects
 * Provides common properties and behavior for objects like databases, schemas, tables, views, etc.
 */
public abstract class SqlObject implements Taggable {

    protected String name;
    protected final Instant createdTime;
    protected String comment;
    /** Owning role; defaults to SYSADMIN until stamped with the creating role at CREATE time. */
    protected String owner = "SYSADMIN";
    /** Object tags applied via ALTER ... SET TAG (canonical upper-cased tag name -> value). */
    private final Map<String, String> tags = new HashMap<>();

    /**
     * Constructor for SQL objects
     * @param name The name of the object (will be stored as-is, normalization is subclass responsibility)
     */
    protected SqlObject(final String name) {
        this.name = name;
        this.createdTime = Instant.now();
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
        SqlObject other = (SqlObject) obj;
        return name != null && name.equalsIgnoreCase(other.name);
    }

    @Override
    public int hashCode() {
        return name != null ? name.toUpperCase().hashCode() : 0;
    }
}
