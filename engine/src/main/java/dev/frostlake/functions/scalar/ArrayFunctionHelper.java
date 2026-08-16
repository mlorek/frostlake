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

package dev.frostlake.functions.scalar;

import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.TypedScalarNode;
import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** Shared helpers for array/object scalar functions. */
public final class ArrayFunctionHelper {

    /** Static helpers only — never instantiated. */
    private ArrayFunctionHelper() {
    }

    public static final ObjectMapper MAPPER = JsonMapper.builder().enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    /** Parse a value to JsonNode. Returns null if unparseable. */
    public static JsonNode parseNode(final Object value) {
        if (value == null) return null;
        if (value instanceof JsonNode) return (JsonNode) value;
        if (value instanceof VariantValue) return ((VariantValue) value).node();
        // Array text can carry Snowflake's bare `undefined` element token — see VariantUndefined.
        try { return VariantUndefined.readTree(MAPPER, value.toString().trim()); }
        catch (final Exception e) { return null; }
    }

    /** Parse value as ArrayNode, or return null. */
    public static ArrayNode parseArray(final Object value) {
        final JsonNode node = parseNode(value);
        return (node != null && node.isArray()) ? (ArrayNode) node : null;
    }

    /**
     * The node a value takes as an ARRAY ELEMENT: a SQL NULL becomes the VARIANT {@code undefined} sentinel
     * — live-verified {@code ARRAY_CONSTRUCT(1, NULL, 2)} is {@code [1,undefined,2]},
     * {@code ARRAY_APPEND([1], NULL)} is {@code [1,undefined]} and {@code ARRAY_REPEAT(NULL, 3)} is
     * {@code [undefined,undefined,undefined]}, while an OBJECT member keeps a JSON null
     * ({@code OBJECT_CONSTRUCT_KEEP_NULL('k', NULL)} is {@code {"k":null}}). Every other value converts
     * exactly as {@link #toNode(ObjectMapper, Object)} does.
     */
    public static JsonNode toElementNode(final ObjectMapper mapper, final Object value) {
        if (value == null) return VariantUndefined.node();
        return toNode(mapper, value);
    }

    /** Convert a Java value to a JsonNode for insertion into arrays/objects. */
    public static JsonNode toNode(final ObjectMapper mapper, final Object value) {
        if (value == null) return mapper.nullNode();
        if (value instanceof VariantValue) return ((VariantValue) value).node();
        if (value instanceof Boolean) return mapper.getNodeFactory().booleanNode((Boolean) value);
        if (value instanceof Long || value instanceof Integer)
            return mapper.getNodeFactory().numberNode(((Number) value).longValue());
        // NUMBER(38,0) values exceed both long and double precision — embed them exactly, never via
        // doubleValue() (which turned 21000000006420544706 into 21000000006420546000).
        if (value instanceof BigDecimal) return mapper.getNodeFactory().numberNode((BigDecimal) value);
        if (value instanceof BigInteger) return mapper.getNodeFactory().numberNode((BigInteger) value);
        if (value instanceof Number) return mapper.getNodeFactory().numberNode(((Number) value).doubleValue());
        if (value instanceof LocalDate || value instanceof LocalDateTime || value instanceof LocalTime) {
            // A temporal embedded in a VARIANT keeps Snowflake's default output text (space + FF3), not
            // java.time's T-separated form — and, in a container, its own type: live reports
            // TYPEOF(OBJECT_CONSTRUCT('d', <date>):d) as DATE, not VARCHAR. TypedScalarNode carries the
            // typed value alongside that exact text, so the JSON stays byte-identical.
            return new TypedScalarNode(SharedFunctionHelpers.textOf(value), value);
        }
        if (value instanceof BinaryValue) {
            // A BINARY embedded in a VARIANT becomes its hex text, Snowflake's JSON rendering of binary,
            // and keeps its BINARY type for TYPEOF / AS_BINARY (see TypedScalarNode).
            return new TypedScalarNode(((BinaryValue) value).toHex(), value);
        }
        final String s = value.toString();
        // In this engine's value model a VARIANT JSON null IS the text "null" (path extraction of a
        // present-but-null field yields it, distinct from SQL NULL for an absent field). Embedding it
        // back into an object/array restores a real JSON null — so OBJECT_CONSTRUCT keeps the pair with
        // a null value, exactly as Snowflake keeps a VARIANT-null pair while dropping SQL-NULL ones.
        if ("null".equals(s)) {
            return mapper.getNodeFactory().nullNode();
        }
        // A VARCHAR stays a STRING member however much its text looks like JSON. Live:
        // ARRAY_CONSTRUCT('[1,2]') is ["[1,2]"], OBJECT_CONSTRUCT('a','[1,2]') is {"a":"[1,2]"}, and
        // TYPEOF of either member is VARCHAR — the same rule PARSE_JSON follows for its argument. A
        // value that really IS semi-structured arrives as a VariantValue and was handled above; text
        // was being re-read as structure here, which made ARRAY_CONSTRUCT('[1,2]')[0] an ARRAY.
        final String trimmed = s.trim();
        // A path access over {"v": "[]"} or {"v": "null"} yields the QUOTED carrier form ("\"[]\"") so
        // a string whose content merely LOOKS structural — or IS the JSON-null marker text — stays
        // distinguishable from a real array/object/null (see JsonPathExtractor). Embedding the carrier
        // must restore the plain STRING member — without this it double-encoded, e.g. OBJECT_AGG stored
        // {"plans":"\"[]\""} instead of {"plans":"[]"}.
        if (trimmed.startsWith("\"")) {
            try {
                final JsonNode parsed = mapper.readTree(trimmed);
                if (parsed.isTextual()) {
                    final String content = parsed.asText().trim();
                    if (content.startsWith("{") || content.startsWith("[") || "null".equals(content)) {
                        return parsed;
                    }
                }
            } catch (final Exception ignored) {}
        }
        return mapper.getNodeFactory().textNode(s);
    }

    /**
     * JsonNode equality that also treats two numeric nodes of equal value as equal (2 == 2 whether one is an
     * int node and the other a long/double node). A strict {@link JsonNode#equals} distinguishes IntNode from
     * LongNode, which makes ARRAY_CONTAINS / ARRAY_REMOVE miss numeric members built by ARRAY_CONSTRUCT.
     */
    public static boolean nodesEqual(final JsonNode a, final JsonNode b) {
        if (a.equals(b)) {
            return true;
        }
        // Exact decimal comparison — a double comparison would merge NUMBER(38,0) values that differ
        // only below double precision.
        return a.isNumber() && b.isNumber() && a.decimalValue().compareTo(b.decimalValue()) == 0;
    }

    /**
     * Ordering comparison between two JsonNodes: numeric-aware (two numeric nodes compare by value),
     * otherwise a lexical comparison of their textual form. Used by ARRAY_SORT / ARRAY_MIN / ARRAY_MAX.
     */
    public static int compareNodes(final JsonNode a, final JsonNode b) {
        // A JSON null is a VALUE that ranks ABOVE every other variant type — live-verified:
        // ARRAY_SORT(['z', PARSE_JSON('null')]) is ["z",null], ARRAY_SORT([TRUE, null, 1]) is [true,1,null],
        // ARRAY_SORT([{"a":1}, 1, null]) is [1,{"a":1},null] and ARRAY_MAX(PARSE_JSON('[1,null,2]')) is the
        // JSON null. A lexical fallback ranked it by the text "null", which put it before 'z'.
        final boolean aNull = a.isNull();
        final boolean bNull = b.isNull();
        if (aNull || bNull) {
            return aNull && bNull ? 0 : aNull ? 1 : -1;
        }
        if (a.isNumber() && b.isNumber()) {
            return a.decimalValue().compareTo(b.decimalValue());
        }
        final String sa = a.isTextual() ? a.asText() : a.toString();
        final String sb = b.isTextual() ? b.asText() : b.toString();
        return sa.compareTo(sb);
    }

    /**
     * Convert a JsonNode back to a plain Java value, keeping a PRESENT JSON null as the typed VARIANT
     * JSON null instead of collapsing it to SQL NULL. Use this wherever the distinction is observable —
     * an extraction that found the key, as opposed to one that did not.
     *
     * <p>Live-verified: {@code TYPEOF(GET(PARSE_JSON('{"b":null}'),'b'))} is
     * {@code 'NULL_VALUE'} and {@code GET(...) IS NULL} is FALSE, while a MISSING key —
     * {@code GET(PARSE_JSON('{"a":1}'),'zz')} — is SQL NULL and its TYPEOF is SQL NULL.
     */
    public static Object fromNodeKeepingJsonNull(final JsonNode node) {
        if (node == null) return null;
        // An `undefined` ELEMENT is the exception: it reads as SQL NULL, never as a JSON null. Live
        // TYPEOF(GET(ARRAY_CONSTRUCT(1,NULL,2),1)) is SQL NULL and GET(...) IS NULL is TRUE,
        // while the same access over PARSE_JSON('[1,null,2]') reports 'NULL_VALUE' and IS NULL is FALSE.
        if (VariantUndefined.isUndefined(node)) return null;
        if (node.isNull()) return VariantValue.of("null");
        return fromNode(node);
    }

    /** Convert a JsonNode back to a plain Java value. Objects/arrays become typed semi-structured values. */
    public static Object fromNode(final JsonNode node) {
        if (node == null || node.isNull()) return null;
        // A member that kept its extended type (DATE/TIME/TIMESTAMP/BINARY) yields that typed value,
        // which is what makes TYPEOF and the AS_*/IS_* family answer like live.
        final Object typed = TypedScalarNode.typedValueOf(node);
        if (typed != null) return typed;
        if (node.isTextual()) return node.asText();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isLong() || node.isInt()) return node.asLong();
        if (node.isBigInteger() || node.isBigDecimal()) return node.decimalValue();
        if (node.isNumber()) return node.asDouble();
        // Object or array: a typed semi-structured value carrying the node's JSON text.
        return VariantValue.ofNode(node);
    }

    /**
     * Canonical form of a JSON node, matching Snowflake's OBJECT/VARIANT normalization: object keys are
     * sorted alphabetically (objects are unordered) and a whole-valued decimal loses its scale (406.0 ->
     * 406). Frostlake stores OBJECT/ARRAY values as their JSON text and compares them by that text (EXCEPT,
     * DISTINCT, GROUP BY, =), so without this two objects differing only in key order or number scale would
     * wrongly compare unequal. Arrays keep their order (arrays are ordered) but their elements are
     * canonicalized.
     */
    public static JsonNode canonicalize(final JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            final List<String> keys = new ArrayList<>();
            final Iterator<String> names = node.propertyNames().iterator();
            while (names.hasNext()) {
                keys.add(names.next());
            }
            Collections.sort(keys);
            final ObjectNode out = MAPPER.createObjectNode();
            for (final String key : keys) {
                out.set(key, canonicalize(node.get(key)));
            }
            return out;
        }
        if (node.isArray()) {
            final ArrayNode out = MAPPER.createArrayNode();
            for (final JsonNode element : node) {
                out.add(canonicalize(element));
            }
            return out;
        }
        if (node.isNumber() && !node.isIntegralNumber()) {
            // Live-verified number families: a SCIENTIFIC-notation JSON literal is DOUBLE (TYPEOF of
            // PARSE_JSON('1e5') and ('1.5e2') is DOUBLE) while a PLAIN fraction is DECIMAL with
            // trailing zeros stripped (PARSE_JSON('1.5') is DECIMAL, '1.50' descales to 1.5, '1.0'
            // to INTEGER); a programmatic double (a ::DOUBLE cast) keeps the DOUBLE family. After
            // BigDecimal parsing the notation is only PARTLY recoverable: a negative scale means a
            // positive exponent, and >15 significant digits is double-provenance in practice (a
            // float widened to double, e.g. 8.999999761581421e-01 — the loader-hash shape that must
            // keep Snowflake's 10-significant-digit FLOAT::VARCHAR rendering). A short negative
            // exponent ('8.99e-1') is indistinguishable from its plain spelling and lands DECIMAL.
            if (node.isBigDecimal()
                    && (node.decimalValue().scale() < 0
                        || (node.decimalValue().scale() > 0 && node.decimalValue().precision() > 15))) {
                return MAPPER.getNodeFactory().numberNode(node.decimalValue().doubleValue());
            }
            final BigDecimal stripped = new BigDecimal(node.asText()).stripTrailingZeros();
            if (stripped.scale() <= 0) {
                return MAPPER.getNodeFactory().numberNode(stripped.toBigInteger());
            }
            if (node.isDouble() || node.isFloat()) {
                return MAPPER.getNodeFactory().numberNode(stripped.doubleValue());
            }
            return MAPPER.getNodeFactory().numberNode(stripped);
        }
        return node;
    }

    /** Canonical JSON text for an object/array value — see {@link #canonicalize(JsonNode)}. */
    public static String toCanonicalJson(final JsonNode node) {
        return canonicalize(node).toString();
    }

    /**
     * Canonical semi-structured runtime value for a node — the typed counterpart of
     * {@link #toCanonicalJson(JsonNode)}, carrying exactly that canonical text.
     */
    public static VariantValue toCanonicalVariant(final JsonNode node) {
        return VariantValue.ofNode(canonicalize(node));
    }
}
