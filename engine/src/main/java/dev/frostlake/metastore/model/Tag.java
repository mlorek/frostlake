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
import java.util.Locale;
import java.util.UUID;

/**
 * Represents a Snowflake TAG - used for object categorization and metadata management
 * Tags can be applied to databases, schemas, tables, and other objects for governance and organization
 */
public class Tag extends SqlObject {

    /** What every identity starts with, which no tag's name does. */
    private static final String ID_PREFIX = "TAG#";

    private final List<String> allowedValues;
    /**
     * Whether the tag has an ALLOWED_VALUES list. A list stays one when its last value is dropped: the tag then
     * lists {@code []} and takes no value at all, where a tag that never had a list, or whose list was unset,
     * takes any (live-verified).
     */
    private boolean allowedValuesListed;
    /**
     * The tag's own identity, under which objects keep their assignments to it: a rename, a move and a renamed
     * schema or database leave it alone, and a tag created later under the same name has another.
     */
    private String id = ID_PREFIX + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    /** The identity of the tag this one was cloned from, until the clone's assignments are mapped to it. */
    private String clonedFromId;

    public Tag(final String name) {
        this(name, new ArrayList<>(), null);
    }

    public Tag(final String name, final List<String> allowedValues, final String comment) {
        super(name);
        this.allowedValues = new ArrayList<>(allowedValues);
        this.allowedValuesListed = !allowedValues.isEmpty();
        this.comment = comment;
    }

    /** The tag's identity; see {@link dev.frostlake.metastore.TagAssignments}. */
    public String getId() {
        return id;
    }

    /**
     * Gives a restored tag the identity it was saved with; a snapshot that recorded none leaves the new one.
     *
     * @param saved the saved identity, or null
     */
    public void restoreId(final String saved) {
        if (saved != null) {
            this.id = saved;
        }
    }

    /** Whether a key an object keeps an assignment under is a tag's identity rather than a tag's name. */
    public static boolean isId(final String key) {
        return key != null && key.startsWith(ID_PREFIX);
    }

    /** The identity of the tag this one was cloned from, or null once its assignments are mapped. */
    public String getClonedFromId() {
        return clonedFromId;
    }

    /** Records, or with null forgets, the tag this one was cloned from. */
    public void setClonedFromId(final String clonedFromId) {
        this.clonedFromId = clonedFromId;
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

    /** Replaces the whole ALLOWED_VALUES list: no value leaves the tag without one. */
    public void setAllowedValues(final List<String> values) {
        keepAllowedValues(values);
        allowedValuesListed = !allowedValues.isEmpty();
    }

    /**
     * The values a DROP ALLOWED_VALUES leaves. A tag that had a list keeps it, emptied or not; a tag that had none
     * still has none.
     *
     * @param values the values left
     */
    public void keepAllowedValues(final List<String> values) {
        allowedValues.clear();
        for (final String value : values) {
            if (!allowedValues.contains(value)) {
                allowedValues.add(value);
            }
        }
    }

    public List<String> getAllowedValues() {
        return new ArrayList<>(allowedValues);
    }

    public void addAllowedValue(final String value) {
        if (!allowedValues.contains(value)) {
            allowedValues.add(value);
        }
        allowedValuesListed = true;
    }

    public void removeAllowedValue(final String value) {
        allowedValues.remove(value);
    }

    /** Unsets ALLOWED_VALUES: the tag has no list and takes any value. */
    public void clearAllowedValues() {
        allowedValues.clear();
        allowedValuesListed = false;
    }

    /** Whether the tag has an ALLOWED_VALUES list, which may hold no value. */
    public boolean hasAllowedValues() {
        return allowedValuesListed;
    }

    /**
     * Says whether the tag has an ALLOWED_VALUES list, for a tag brought back or copied with a list that holds
     * no value.
     *
     * @param listed whether the tag has a list
     */
    public void markAllowedValuesListed(final boolean listed) {
        allowedValuesListed = listed || !allowedValues.isEmpty();
    }

    public boolean isValueAllowed(final String value) {
        return !hasAllowedValues() || allowedValues.contains(value);
    }

    /**
     * The tag's ALLOWED_VALUES as the account spells them: a JSON array of the values, {@code []} for a list that
     * holds none, or null for a tag without a list.
     */
    public String allowedValuesText() {
        if (!hasAllowedValues()) {
            return null;
        }
        final StringBuilder sb = new StringBuilder("[");
        for (final String value : allowedValues) {
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append('"').append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
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
