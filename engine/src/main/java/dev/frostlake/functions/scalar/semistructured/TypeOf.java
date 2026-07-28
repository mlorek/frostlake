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

package dev.frostlake.functions.scalar.semistructured;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.JsonTypeHelper;
import dev.frostlake.types.StringType;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class TypeOf extends BuiltInFunction {
    public TypeOf() { super("TYPEOF", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        // Snowflake: a SQL NULL input yields NULL (NULL in, NULL out); only a JSON null VALUE
        // (below) reports "NULL_VALUE".
        if (args.get(0) == null) return null;
        Object v = args.get(0);
        if (v instanceof Boolean) return "BOOLEAN";
        if (v instanceof Long || v instanceof Integer) return "INTEGER";
        // Snowflake distinguishes fixed-point (DECIMAL) from floating-point (DOUBLE); it never uses "REAL".
        if (v instanceof BigDecimal) return "DECIMAL";
        if (v instanceof Double || v instanceof Float) return "DOUBLE";
        if (v instanceof List) return "ARRAY";
        if (v instanceof Map) return "OBJECT";
        // Try to parse as JSON (values from PARSE_JSON are compact JSON strings)
        JsonNode node = JsonTypeHelper.parse(v);
        if (node != null) {
            if (node.isObject()) return "OBJECT";
            if (node.isArray()) return "ARRAY";
            if (node.isNull()) return "NULL_VALUE";
            if (node.isBoolean()) return "BOOLEAN";
            if (node.isIntegralNumber()) return "INTEGER";
            if (node.isFloatingPointNumber()) return "DOUBLE";
            if (node.isTextual()) return "VARCHAR";
        }
        return "VARCHAR";
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }
}
