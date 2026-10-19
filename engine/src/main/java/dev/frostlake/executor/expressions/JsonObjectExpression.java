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

package dev.frostlake.executor.expressions;

import java.util.List;
import java.util.Map;

/**
 * Represents a JSON object literal (e.g., {'name': 'Alice', 'age': 30})
 */
public class JsonObjectExpression implements Expression {
    private final Map<String, Expression> properties;
    /** Every key as written, in order, a key written twice included; null when no key is written twice. */
    private final List<String> writtenKeys;
    /** The values of {@link #writtenKeys}, index for index; null alongside it. */
    private final List<Expression> writtenValues;

    public JsonObjectExpression(final Map<String, Expression> properties) {
        this(properties, null, null);
    }

    /**
     * An object constant one of whose keys is written twice: the pairs as written are kept, and the object is
     * refused once two of them hold a value under one key.
     *
     * @param properties    the pairs, the later of a repeated key's
     * @param writtenKeys   every key as written, in order
     * @param writtenValues their values, index for index
     */
    public JsonObjectExpression(final Map<String, Expression> properties, final List<String> writtenKeys,
                                final List<Expression> writtenValues) {
        this.properties = properties;
        this.writtenKeys = writtenKeys;
        this.writtenValues = writtenValues;
    }

    public Map<String, Expression> getProperties() {
        return properties;
    }

    /** Every key as written when one is written twice, else null. */
    public List<String> getWrittenKeys() {
        return writtenKeys;
    }

    /** The values of {@link #getWrittenKeys}, index for index, else null. */
    public List<Expression> getWrittenValues() {
        return writtenValues;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitJsonObject(this);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (final Map.Entry<String, Expression> entry : properties.entrySet()) {
            if (!first) sb.append(", ");
            sb.append("'").append(entry.getKey()).append("': ").append(entry.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
