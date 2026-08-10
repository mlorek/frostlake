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

package com.snowflake.snowpark_java.types;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An ordered list of {@link StructField}s — the shape of a DataFrame, and itself a {@link DataType} so
 * it can nest.
 */
public class StructType extends DataType implements Iterable<StructField> {

    private final List<StructField> fields;

    public StructType(final StructField[] fields) {
        this.fields = new ArrayList<StructField>();
        if (fields != null) {
            for (final StructField field : fields) {
                this.fields.add(field);
            }
        }
    }

    public StructType(final List<StructField> fields) {
        this.fields = new ArrayList<StructField>(fields);
    }

    /** Snowpark's factory spelling. */
    public static StructType create(final StructField... fields) {
        return new StructType(fields);
    }

    public int size() {
        return fields.size();
    }

    public StructField get(final int index) {
        return fields.get(index);
    }

    /** The field of that name, or null. Column names are matched the way SQL resolves them: ignoring case. */
    public StructField nameToField(final String name) {
        for (final StructField field : fields) {
            if (field.name().equalsIgnoreCase(name)) {
                return field;
            }
        }
        return null;
    }

    public String[] names() {
        final String[] out = new String[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            out[i] = fields.get(i).name();
        }
        return out;
    }

    @Override
    public Iterator<StructField> iterator() {
        return fields.iterator();
    }

    @Override
    public String toString() {
        final StringBuilder text = new StringBuilder("StructType[");
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(fields.get(i).toString());
        }
        return text.append("]").toString();
    }
}
