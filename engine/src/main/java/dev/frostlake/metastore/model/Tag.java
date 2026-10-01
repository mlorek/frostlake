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

import dev.frostlake.metastore.SqlObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Snowflake TAG - used for object categorization and metadata management
 * Tags can be applied to databases, schemas, tables, and other objects for governance and organization
 */
public class Tag extends SqlObject {

    private final List<String> allowedValues;

    public Tag(final String name) {
        this(name, new ArrayList<>(), null);
    }

    public Tag(final String name, final List<String> allowedValues, final String comment) {
        super(name);
        this.allowedValues = new ArrayList<>(allowedValues);
        this.comment = comment;
    }

    /** How the tag propagates to target objects ({@code ON_DEPENDENCY} …), or null when it does not. */
    private String propagate;
    /** What a conflicting propagated value becomes: a string or {@code ALLOWED_VALUES_SEQUENCE}; null when unset. */
    private String onConflict;

    /** The tag's propagation mode, or null when it does not propagate. */
    public String getPropagate() {
        return propagate;
    }

    /** Sets the tag's propagation mode; null stops propagation. */
    public void setPropagate(final String propagate) {
        this.propagate = propagate;
    }

    /** The tag's propagation conflict rule, or null when unset. */
    public String getOnConflict() {
        return onConflict;
    }

    /** Sets the tag's propagation conflict rule; null unsets it. */
    public void setOnConflict(final String onConflict) {
        this.onConflict = onConflict;
    }

    /** Replaces the whole ALLOWED_VALUES list. */
    public void setAllowedValues(final List<String> values) {
        allowedValues.clear();
        for (final String value : values) {
            addAllowedValue(value);
        }
    }

    public List<String> getAllowedValues() {
        return new ArrayList<>(allowedValues);
    }

    public void addAllowedValue(final String value) {
        if (!allowedValues.contains(value)) {
            allowedValues.add(value);
        }
    }

    public void removeAllowedValue(final String value) {
        allowedValues.remove(value);
    }

    public void clearAllowedValues() {
        allowedValues.clear();
    }

    public boolean hasAllowedValues() {
        return !allowedValues.isEmpty();
    }

    public boolean isValueAllowed(final String value) {
        return !hasAllowedValues() || allowedValues.contains(value);
    }

    @Override
    public String getObjectType() {
        return "TAG";
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Tag{name='").append(getName()).append("'");
        if (hasAllowedValues()) {
            sb.append(", allowedValues=").append(allowedValues);
        }
        if (comment != null) {
            sb.append(", comment='").append(comment).append("'");
        }
        sb.append("}");
        return sb.toString();
    }
}
