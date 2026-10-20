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

package dev.frostlake.rt.py;

import dev.frostlake.executor.SessionZone;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.VectorElementType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantText;
import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;
import dev.frostlake.values.VectorValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Values between the engine and the CPython worker: JSON, with the types JSON lacks carried as one-key objects
 * ({@code {"$dec": "1.25"}}, {@code {"$dt": iso}}, ... — see {@code cpython_worker.py}). Arguments are first
 * converted by the type their parameter declares, so a handler receives what Snowflake hands it: {@code int} for
 * a NUMBER without scale, {@code decimal.Decimal} with one, {@code float} for FLOAT, {@code date}, {@code time} and
 * naive or aware {@code datetime}, {@code bytes}, dict/list for semi-structured values and a memoryview for a VECTOR.
 */
final class CPythonCodec {

    static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    /** Six fraction digits: what every {@code datetime.fromisoformat} since Python 3.7 reads. */
    private static final DateTimeFormatter LOCAL_DATE_TIME =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS", Locale.ROOT);
    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSS", Locale.ROOT);
    /** {@code xxx} writes {@code +00:00} for UTC rather than {@code Z}, which Python before 3.11 cannot read. */
    private static final DateTimeFormatter OFFSET_DATE_TIME =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSxxx", Locale.ROOT);

    private CPythonCodec() {
    }

    /**
     * An argument as the handler should see the type its parameter declares. The engine may hand over a NUMBER
     * literal for a FLOAT parameter, or a timestamp for a DATE one; this settles the Python type first.
     */
    static Object argument(final DataType declared, final Object value) {
        if (value == null || declared == null || declared.getCategory() == null) {
            return value;
        }
        switch (declared.getCategory()) {
            case SEMI_STRUCTURED:
                return semiStructured(value);
            case NUMERIC:
                return numeric(declared, value);
            case DATE_TIME:
                return temporal(declared, value);
            default:
                return value;
        }
    }

    private static Object semiStructured(final Object value) {
        if (value instanceof VariantValue) {
            return value;
        }
        if (value instanceof CharSequence) {
            final String text = value.toString();
            try {
                MAPPER.readTree(text);
            } catch (final RuntimeException notJson) {
                return value;
            }
            return VariantValue.of(text);
        }
        return value;
    }

    private static Object numeric(final DataType declared, final Object value) {
        if (!(value instanceof Number)) {
            return value;
        }
        if (NumericType.isApproximate(declared)) {
            return ((Number) value).doubleValue();
        }
        final BigDecimal exact = value instanceof BigDecimal ? (BigDecimal) value
            : value instanceof BigInteger ? new BigDecimal((BigInteger) value)
            : value instanceof Double || value instanceof Float ? BigDecimal.valueOf(((Number) value).doubleValue())
            : BigDecimal.valueOf(((Number) value).longValue());
        final int scale = declared instanceof NumericType ? ((NumericType) declared).getScale() : 0;
        if (scale <= 0) {
            final BigDecimal whole = exact.stripTrailingZeros();
            if (whole.scale() <= 0) {
                return whole.toBigIntegerExact();
            }
            return exact;
        }
        return exact;
    }

    private static Object temporal(final DataType declared, final Object value) {
        final String name = declared.getName() == null ? "" : declared.getName().toUpperCase(Locale.ROOT);
        final boolean zoned = declared instanceof DateTimeType && ((DateTimeType) declared).hasTimeZone();
        if ("DATE".equals(name)) {
            if (value instanceof LocalDateTime) {
                return ((LocalDateTime) value).toLocalDate();
            }
            if (value instanceof ZonedDateTime) {
                return ((ZonedDateTime) value).toLocalDate();
            }
            if (value instanceof OffsetDateTime) {
                return ((OffsetDateTime) value).toLocalDate();
            }
            if (value instanceof java.sql.Date) {
                return ((java.sql.Date) value).toLocalDate();
            }
            return value;
        }
        if ("TIME".equals(name)) {
            if (value instanceof LocalDateTime) {
                return ((LocalDateTime) value).toLocalTime();
            }
            if (value instanceof java.sql.Time) {
                return ((java.sql.Time) value).toLocalTime();
            }
            return value;
        }
        if (zoned) {
            // A TIMESTAMP_TZ keeps its offset; a TIMESTAMP_LTZ is the session's wall clock at that instant, without
            // a zone, as PythonRuntime.bindArgument binds it for GraalPy.
            final ZonedDateTime instant = value instanceof ZonedDateTime ? (ZonedDateTime) value
                : value instanceof OffsetDateTime ? ((OffsetDateTime) value).toZonedDateTime()
                : value instanceof Instant ? ((Instant) value).atZone(SessionZone.current())
                : value instanceof LocalDateTime ? ((LocalDateTime) value).atZone(SessionZone.current())
                : null;
            if (instant == null) {
                return value;
            }
            return name.endsWith("_TZ") ? instant.toOffsetDateTime()
                : instant.withZoneSameInstant(SessionZone.current()).toLocalDateTime();
        }
        if (value instanceof ZonedDateTime) {
            return ((ZonedDateTime) value).toLocalDateTime();
        }
        if (value instanceof OffsetDateTime) {
            return ((OffsetDateTime) value).toLocalDateTime();
        }
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime();
        }
        if (value instanceof LocalDate) {
            return ((LocalDate) value).atStartOfDay();
        }
        return value;
    }

    /** A Java value as the worker's JSON. */
    static JsonNode encode(final Object value) {
        if (value == null) {
            return NODES.nullNode();
        }
        if (value instanceof Boolean) {
            return NODES.booleanNode((Boolean) value);
        }
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return NODES.numberNode(((Number) value).longValue());
        }
        if (value instanceof BigInteger) {
            return NODES.numberNode((BigInteger) value);
        }
        if (value instanceof BigDecimal) {
            return tagged("$dec", ((BigDecimal) value).toString());
        }
        if (value instanceof Double || value instanceof Float) {
            final double d = ((Number) value).doubleValue();
            if (Double.isNaN(d)) {
                return tagged("$f", "nan");
            }
            if (Double.isInfinite(d)) {
                return tagged("$f", d > 0 ? "inf" : "-inf");
            }
            return NODES.numberNode(d);
        }
        if (value instanceof Number) {
            return tagged("$dec", new BigDecimal(value.toString()).toString());
        }
        if (value instanceof CharSequence || value instanceof Character) {
            return NODES.textNode(value.toString());
        }
        if (value instanceof LocalDateTime) {
            return tagged("$dt", LOCAL_DATE_TIME.format((LocalDateTime) value));
        }
        if (value instanceof LocalDate) {
            return tagged("$d", value.toString());
        }
        if (value instanceof LocalTime) {
            return tagged("$t", LOCAL_TIME.format((LocalTime) value));
        }
        if (value instanceof ZonedDateTime) {
            return tagged("$dtz", OFFSET_DATE_TIME.format(((ZonedDateTime) value).toOffsetDateTime()));
        }
        if (value instanceof OffsetDateTime) {
            return tagged("$dtz", OFFSET_DATE_TIME.format((OffsetDateTime) value));
        }
        if (value instanceof Instant) {
            return tagged("$dtz", OFFSET_DATE_TIME.format(((Instant) value).atOffset(ZoneOffset.UTC)));
        }
        if (value instanceof java.sql.Timestamp) {
            return encode(((java.sql.Timestamp) value).toLocalDateTime());
        }
        if (value instanceof java.sql.Date) {
            return encode(((java.sql.Date) value).toLocalDate());
        }
        if (value instanceof java.sql.Time) {
            return encode(((java.sql.Time) value).toLocalTime());
        }
        if (value instanceof BinaryValue) {
            return tagged("$b", ((BinaryValue) value).toHex());
        }
        if (value instanceof byte[]) {
            return tagged("$b", BinaryValue.of((byte[]) value).toHex());
        }
        if (value instanceof VariantValue) {
            return tagged("$json", VariantText.standard(VariantUndefined.withNulls(((VariantValue) value).node())));
        }
        if (value instanceof VectorValue) {
            return vector((VectorValue) value);
        }
        if (value instanceof PythonResultSet) {
            return resultSet((PythonResultSet) value);
        }
        if (value instanceof SnowparkOutcome) {
            // The worker's $outcome: the engine's refusal as text, or what the statement answered.
            final SnowparkOutcome outcome = (SnowparkOutcome) value;
            final ObjectNode payload = NODES.objectNode();
            payload.set("refusal", outcome.refused() ? NODES.textNode(outcome.refusal()) : NODES.nullNode());
            payload.set("result", outcome.refused() ? NODES.nullNode() : encode(outcome.result()));
            final ObjectNode object = NODES.objectNode();
            object.set("$outcome", payload);
            return object;
        }
        if (value instanceof Row) {
            return encode(((Row) value).getValues());
        }
        if (value instanceof Map) {
            final ArrayNode entries = NODES.arrayNode();
            for (final Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                final ArrayNode pair = NODES.arrayNode();
                pair.add(String.valueOf(entry.getKey()));
                pair.add(encode(entry.getValue()));
                entries.add(pair);
            }
            final ObjectNode object = NODES.objectNode();
            object.set("$o", entries);
            return object;
        }
        if (value instanceof Iterable) {
            final ArrayNode array = NODES.arrayNode();
            for (final Object element : (Iterable<?>) value) {
                array.add(encode(element));
            }
            return array;
        }
        if (value instanceof Object[]) {
            final ArrayNode array = NODES.arrayNode();
            for (final Object element : (Object[]) value) {
                array.add(encode(element));
            }
            return array;
        }
        return NODES.textNode(value.toString());
    }

    /**
     * A VECTOR as the worker's {@code $vec}: its element type ({@code f} or {@code i}, the {@code array} type code the
     * worker builds its memoryview from, as {@link PythonRuntime#bindVector} does on GraalPy) and its elements, a
     * float one that is no finite number as the text {@code float()} reads.
     */
    private static JsonNode vector(final VectorValue vector) {
        final boolean integral = vector.getElementType() == VectorElementType.INT;
        final ArrayNode elements = NODES.arrayNode();
        for (int i = 0; i < vector.dimension(); i++) {
            final double element = vector.element(i);
            if (integral) {
                elements.add((long) element);
            } else if (Double.isFinite(element)) {
                elements.add(element);
            } else {
                elements.add(Double.isNaN(element) ? "nan" : element > 0 ? "inf" : "-inf");
            }
        }
        final ObjectNode payload = NODES.objectNode();
        payload.put("t", integral ? "i" : "f");
        payload.set("v", elements);
        final ObjectNode object = NODES.objectNode();
        object.set("$vec", payload);
        return object;
    }

    /** A result set as the worker's {@code $rs}: the same column names, types and plain row data the shim reads. */
    private static JsonNode resultSet(final PythonResultSet rs) {
        final ObjectNode payload = NODES.objectNode();
        payload.set("columnNames", encode(rs.columnNames()));
        payload.set("columnTypes", encode(rs.columnTypes()));
        payload.set("data", encode(rs.data()));
        final ObjectNode object = NODES.objectNode();
        object.set("$rs", payload);
        return object;
    }

    private static ObjectNode tagged(final String tag, final String text) {
        final ObjectNode object = NODES.objectNode();
        object.put(tag, text);
        return object;
    }

    /** The worker's JSON as a plain Java value: Map, List, Long or BigDecimal, Double, String, java.time, binary. */
    static Object decode(final JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isIntegralNumber()) {
            return node.canConvertToLong() ? (Object) node.longValue() : new BigDecimal(node.bigIntegerValue());
        }
        if (node.isNumber()) {
            return node.doubleValue();
        }
        if (node.isString()) {
            return node.stringValue();
        }
        if (node.isArray()) {
            final List<Object> list = new ArrayList<>();
            for (final JsonNode element : node) {
                list.add(decode(element));
            }
            return list;
        }
        if (node.has("$o")) {
            final Map<String, Object> map = new LinkedHashMap<>();
            for (final JsonNode pair : node.get("$o")) {
                map.put(pair.get(0).asString(), decode(pair.get(1)));
            }
            return map;
        }
        if (node.has("$dec")) {
            return new BigDecimal(node.get("$dec").asString());
        }
        if (node.has("$f")) {
            final String special = node.get("$f").asString();
            return "nan".equals(special) ? Double.NaN
                : "-inf".equals(special) ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        if (node.has("$dt")) {
            return LocalDateTime.parse(node.get("$dt").asString());
        }
        if (node.has("$dtz")) {
            return OffsetDateTime.parse(node.get("$dtz").asString()).toZonedDateTime();
        }
        if (node.has("$d")) {
            return LocalDate.parse(node.get("$d").asString());
        }
        if (node.has("$t")) {
            return LocalTime.parse(node.get("$t").asString());
        }
        if (node.has("$b")) {
            return BinaryValue.fromHex(node.get("$b").asString());
        }
        final Map<String, Object> map = new LinkedHashMap<>();
        for (final Map.Entry<String, JsonNode> field : node.properties()) {
            map.put(field.getKey(), decode(field.getValue()));
        }
        return map;
    }

    /** The values a callback received, decoded. */
    static List<Object> decodeAll(final JsonNode array) {
        final List<Object> values = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (final JsonNode element : array) {
                values.add(decode(element));
            }
        }
        return values;
    }

    /** Whether a value is an engine object the worker reaches by callback rather than by value. */
    static boolean isHostObject(final Object value) {
        return value instanceof SnowparkSession;
    }
}
