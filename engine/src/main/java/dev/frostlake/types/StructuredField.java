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

package dev.frostlake.types;

import java.io.Serializable;

/**
 * One declared field of a structured {@code OBJECT(name type [NOT NULL], ...)} type.
 *
 * <p>The name is kept VERBATIM — structured field names are case-sensitive and are NOT folded like
 * ordinary SQL identifiers. Live-verified on a real account:
 * {@code CAST(OBJECT_CONSTRUCT('x','a') AS OBJECT(x VARCHAR))} yields {@code {"x":"a"}} (lower case
 * preserved), {@code CAST(<OBJECT(x VARCHAR)> AS OBJECT(X VARCHAR) RENAME FIELDS)} yields
 * {@code {"X":"a"}}, and {@code CAST(OBJECT_CONSTRUCT('x','a') AS OBJECT(X VARCHAR))} FAILS with
 * "Typed object schema mismatch in conversion" — the declared {@code X} does not match the key
 * {@code x}.
 */
public final class StructuredField implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String name;
    private final DataType dataType;
    private final boolean notNull;

    public StructuredField(final String name, final DataType dataType, final boolean notNull) {
        this.name = name;
        this.dataType = dataType;
        this.notNull = notNull;
    }

    public String getName() {
        return name;
    }

    public DataType getDataType() {
        return dataType;
    }

    public boolean isNotNull() {
        return notNull;
    }

    @Override
    public String toString() {
        return name + " " + StructuredTypes.describe(dataType) + (notNull ? " NOT NULL" : "");
    }
}
