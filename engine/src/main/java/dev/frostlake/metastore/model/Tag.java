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
