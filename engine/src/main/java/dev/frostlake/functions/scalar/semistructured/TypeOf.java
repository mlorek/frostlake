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

import dev.frostlake.functions.StructuredArgumentFunction;
import dev.frostlake.functions.scalar.JsonTypeHelper;
import dev.frostlake.types.StringType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.GeoValue;
import dev.frostlake.values.TypedVectorNode;
import dev.frostlake.values.UuidTextNode;
import dev.frostlake.values.VariantValue;
import dev.frostlake.values.VectorValue;
import dev.frostlake.values.XmlVariants;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public class TypeOf extends StructuredArgumentFunction {
    public TypeOf() { super("TYPEOF", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        // Snowflake: a SQL NULL input yields NULL (NULL in, NULL out); only a JSON null VALUE
        // (below) reports "NULL_VALUE".
        if (args.get(0) == null) return null;
        final Object v = args.get(0);
        if (v instanceof VariantValue) {
            // A typed semi-structured value reports its EXACT inner type from the parsed tree —
            // no text sniffing, so a variant string "123" is VARCHAR, never INTEGER.
            final JsonNode root = ((VariantValue) v).node();
            // XML is structural in Snowflake: any object shaped {"$": …, "@": "tag", …} reports XML
            // (live-verified even for PARSE_JSON('{"$":1,"@":"b"}')), so test the shape first.
            if (XmlVariants.isXmlElement(root)) return "XML";
            if (UuidTextNode.holds(root)) return "UUID";
            if (root.isObject()) return "OBJECT";
            // ★ Asked BEFORE the array test, which it would otherwise answer: a VECTOR member prints
            // as an array but reports its own kind, and live agrees it is not an ARRAY.
            if (TypedVectorNode.vectorValueOf(root) != null) return "VECTOR";
            if (root.isArray()) return "ARRAY";
            if (root.isNull()) return "NULL_VALUE";
            if (root.isBoolean()) return "BOOLEAN";
            if (root.isIntegralNumber()) return "INTEGER";
            // A BigDecimal node is fixed-point DECIMAL; only double/float nodes are DOUBLE.
            if (root.isBigDecimal()) return "DECIMAL";
            if (root.isFloatingPointNumber()) return "DOUBLE";
            return "VARCHAR";
        }
        if (v instanceof GeoValue) return ((GeoValue) v).kindName();
        // A VECTOR reports the bare family name, not its parameterization — live,
        // TYPEOF([1,2,3]::VECTOR(FLOAT,3)) is 'VECTOR' (only SYSTEM$TYPEOF spells out the dimension).
        if (v instanceof VectorValue) return "VECTOR";
        if (v instanceof BinaryValue) return "BINARY";
        if (v instanceof Boolean) return "BOOLEAN";
        if (v instanceof Long || v instanceof Integer) return "INTEGER";
        // Snowflake distinguishes fixed-point (DECIMAL) from floating-point (DOUBLE); it never uses
        // "REAL". The SCALE decides: a scale-0 value (3.14::NUMBER rounds to 3 in NUMBER(38,0)) is
        // INTEGER while a scale-carrying value stays DECIMAL even when whole (an extracted 1.0
        // member reports DECIMAL) — both live-verified.
        if (v instanceof BigDecimal) {
            return ((BigDecimal) v).scale() <= 0 ? "INTEGER" : "DECIMAL";
        }
        if (v instanceof Double || v instanceof Float) return "DOUBLE";
        if (v instanceof LocalDate) return "DATE";
        if (v instanceof LocalTime) return "TIME";
        // An LTZ arrives as an OffsetDateTime; only the naive class is a TIMESTAMP_NTZ.
        if (v instanceof ZonedDateTime) return "TIMESTAMP_TZ";
        if (v instanceof OffsetDateTime) return "TIMESTAMP_LTZ";
        if (v instanceof LocalDateTime) return "TIMESTAMP_NTZ";
        if (v instanceof List) return "ARRAY";
        if (v instanceof Map) return "OBJECT";
        if (v instanceof String) {
            final String s = ((String) v).trim();
            // The pre-typed JSON-null marker and legacy structural text cells (persisted snapshots,
            // COPY-loaded data) keep their variant classification; every other string is a VARCHAR —
            // a numeric-looking string is never sniffed into a number.
            if ("null".equals(s)) return "NULL_VALUE";
            if (s.startsWith("{") || s.startsWith("[")) {
                final JsonNode node = JsonTypeHelper.parse(v);
                if (node != null && node.isObject()) return "OBJECT";
                if (node != null && node.isArray()) return "ARRAY";
            }
        }
        return "VARCHAR";
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
