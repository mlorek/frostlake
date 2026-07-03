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

import java.util.Map;

/**
 * Represents a JSON object literal (e.g., {'name': 'Alice', 'age': 30})
 */
public class JsonObjectExpression implements Expression {
    private final Map<String, Expression> properties;

    public JsonObjectExpression(final Map<String, Expression> properties) {
        this.properties = properties;
    }

    public Map<String, Expression> getProperties() {
        return properties;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitJsonObject(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
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
