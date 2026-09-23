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

/**
 * One property of a network rule, network policy, password policy or secret: its name, the value it takes, and what
 * ALTER may do with it.
 */
public final class SecurityPropertySpec {

    private final String name;
    private final SecurityPropertyType type;
    private String[] choices = new String[0];
    private long min = Long.MIN_VALUE;
    private long max = Long.MAX_VALUE;
    private String defaultValue;
    private String description;
    private boolean alterable;
    private boolean unsettable;
    private boolean writeOnly;

    /**
     * @param name the property's name in SQL
     * @param type the value it takes
     */
    public SecurityPropertySpec(final String name, final SecurityPropertyType type) {
        this.name = name;
        this.type = type;
    }

    /** Restricts a {@link SecurityPropertyType#CHOICE} to these words. */
    public SecurityPropertySpec choices(final String... words) {
        this.choices = words.clone();
        return this;
    }

    /** Restricts an {@link SecurityPropertyType#INTEGER} to a closed range. */
    public SecurityPropertySpec range(final long lowest, final long highest) {
        this.min = lowest;
        this.max = highest;
        return this;
    }

    /** The value the property has when none is set, as DESCRIBE shows it. */
    public SecurityPropertySpec defaults(final String value) {
        this.defaultValue = value;
        return this;
    }

    /** The sentence DESCRIBE shows for the property. */
    public SecurityPropertySpec describedAs(final String sentence) {
        this.description = sentence;
        return this;
    }

    /** Marks the property as one ALTER … SET may change. */
    public SecurityPropertySpec alterable() {
        this.alterable = true;
        return this;
    }

    /** Marks the property as one ALTER … UNSET may reset. */
    public SecurityPropertySpec unsettable() {
        this.unsettable = true;
        return this;
    }

    /** Marks the property as one no listing or description ever shows. */
    public SecurityPropertySpec writeOnly() {
        this.writeOnly = true;
        return this;
    }

    /** The property's name in SQL. */
    public String getName() {
        return name;
    }

    /** The value the property takes. */
    public SecurityPropertyType getType() {
        return type;
    }

    /** The words a choice may be. */
    public String[] getChoices() {
        return choices.clone();
    }

    /** The lowest integer allowed. */
    public long getMin() {
        return min;
    }

    /** The highest integer allowed. */
    public long getMax() {
        return max;
    }

    /** The value the property has when none is set, or null. */
    public String getDefaultValue() {
        return defaultValue;
    }

    /** The sentence DESCRIBE shows for the property, or null. */
    public String getDescription() {
        return description;
    }

    /** Whether ALTER … SET may change the property. */
    public boolean isAlterable() {
        return alterable;
    }

    /** Whether ALTER … UNSET may reset the property. */
    public boolean isUnsettable() {
        return unsettable;
    }

    /** Whether the property's value is never shown back. */
    public boolean isWriteOnly() {
        return writeOnly;
    }
}
