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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A STRUCTURED object type — {@code OBJECT(x VARCHAR, y NUMBER)} — as opposed to the plain
 * semi-structured {@link ObjectType}. Snowflake treats the two as different types: a plain OBJECT is
 * rejected where a structured one is required and vice versa, and only a structured value may carry
 * the {@code RENAME FIELDS} / {@code ADD FIELDS} cast modifiers (live-verified).
 *
 * <p>Structured-ness is a STATIC property of the type, not a tag on the value: live,
 * {@code SYSTEM$TYPEOF(NULL::OBJECT(x VARCHAR))} still reports {@code OBJECT(x VARCHAR)[LOB]} even
 * though the value is SQL NULL, and it propagates through table columns, CTEs and subqueries.
 *
 * <p>Field ORDER is significant and is preserved on output: live,
 * {@code CAST(<OBJECT(x VARCHAR)> AS OBJECT(z INT, x VARCHAR) ADD FIELDS)} renders
 * {@code {"z":null,"x":"a"}} — declaration order, NOT the sorted key order a semi-structured OBJECT
 * renders in.
 */
public class StructuredObjectType extends ObjectType {

    private final List<StructuredField> fields;

    public StructuredObjectType(final List<StructuredField> fields) {
        super();
        this.fields = Collections.unmodifiableList(new ArrayList<>(fields));
    }

    public List<StructuredField> getFields() {
        return fields;
    }

    /** The declared field with this VERBATIM name, or null when the type has no such field. */
    public StructuredField field(final String name) {
        for (final StructuredField candidate : fields) {
            if (candidate.getName().equals(name)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public boolean isCompatible(final DataType other) {
        return other instanceof ObjectType || other instanceof MapType;
    }

    @Override
    public String toString() {
        return StructuredTypes.describe(this);
    }
}
