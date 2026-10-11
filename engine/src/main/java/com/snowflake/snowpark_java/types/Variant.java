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

import dev.frostlake.executor.udf.SnowparkVariantKind;
import dev.frostlake.executor.udf.SnowparkVariantNodes;
import dev.frostlake.executor.udf.SnowparkVariantReader;
import dev.frostlake.executor.udf.SnowparkVariantTimes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Snowpark {@code Variant} a Java handler takes for a VARIANT argument and hands back for a VARIANT result,
 * with the constructors and the readers the real class has (live-verified).
 *
 * <p>A value remembers what it was made as, and that decides what reads it: one made from a text - which is
 * read as JSON where it is JSON - answers every reader, every value answers as a text, a number answers as a
 * whole or a real number and as a timestamp, a boolean as a number, and a date, a time, a timestamp and bytes
 * as their own kind alone. Any other reading is refused with {@code Conversion from Variant of Date to
 * FixedNumber is not supported}. A date, a time and a timestamp are held as their texts in the session's time
 * zone.
 */
public class Variant implements Serializable {

    private static final long serialVersionUID = 1L;

    private final JsonNode value;
    private final SnowparkVariantKind kind;

    public Variant(final float num) {
        this(JsonNodeFactory.instance.numberNode(num), SnowparkVariantKind.REAL_NUMBER);
    }

    public Variant(final double num) {
        this(JsonNodeFactory.instance.numberNode(num), SnowparkVariantKind.REAL_NUMBER);
    }

    public Variant(final long num) {
        this(JsonNodeFactory.instance.numberNode(num), SnowparkVariantKind.FIXED_NUMBER);
    }

    public Variant(final int num) {
        this(JsonNodeFactory.instance.numberNode(num), SnowparkVariantKind.FIXED_NUMBER);
    }

    public Variant(final short num) {
        this(JsonNodeFactory.instance.numberNode(num), SnowparkVariantKind.FIXED_NUMBER);
    }

    public Variant(final BigDecimal num) {
        this(JsonNodeFactory.instance.numberNode(num), SnowparkVariantKind.FIXED_NUMBER);
    }

    public Variant(final BigInteger num) {
        this(JsonNodeFactory.instance.numberNode(num), SnowparkVariantKind.FIXED_NUMBER);
    }

    public Variant(final boolean bool) {
        this(JsonNodeFactory.instance.booleanNode(bool), SnowparkVariantKind.BOOLEAN);
    }

    /**
     * A text, read as JSON where it is JSON: {@code 7} is a number, {@code {"a":1}} an object, {@code null} a
     * JSON null, and {@code s} the text itself.
     */
    public Variant(final String str) {
        this(SnowparkVariantNodes.parse(str), SnowparkVariantKind.STRING);
    }

    public Variant(final byte[] bytes) {
        this(JsonNodeFactory.instance.binaryNode(bytes), SnowparkVariantKind.BINARY);
    }

    public Variant(final Time time) {
        this(JsonNodeFactory.instance.stringNode(SnowparkVariantTimes.text(time)), SnowparkVariantKind.TIME);
    }

    public Variant(final Date date) {
        this(JsonNodeFactory.instance.stringNode(SnowparkVariantTimes.text(date)), SnowparkVariantKind.DATE);
    }

    public Variant(final Timestamp timestamp) {
        this(JsonNodeFactory.instance.stringNode(SnowparkVariantTimes.text(timestamp)),
            SnowparkVariantKind.TIMESTAMP);
    }

    /** A list as an array; a {@code Variant} among its elements is the value it holds. */
    public Variant(final List<Object> list) {
        this(SnowparkVariantNodes.ofElements(list), SnowparkVariantKind.ARRAY);
    }

    public Variant(final Object[] arr) {
        this(SnowparkVariantNodes.ofElements(Arrays.asList(arr)), SnowparkVariantKind.ARRAY);
    }

    /** A map as an object; any other value as JSON writes it, read from then on as a text is. */
    public Variant(final Object obj) {
        this(SnowparkVariantNodes.ofObject(obj),
            obj instanceof Map ? SnowparkVariantKind.OBJECT : SnowparkVariantKind.STRING);
    }

    private Variant(final JsonNode value, final SnowparkVariantKind kind) {
        this.value = value;
        this.kind = kind;
    }

    public float asFloat() {
        readAs(SnowparkVariantKind.REAL_NUMBER);
        return (float) SnowparkVariantNodes.asDouble(held());
    }

    public double asDouble() {
        readAs(SnowparkVariantKind.REAL_NUMBER);
        return SnowparkVariantNodes.asDouble(held());
    }

    public short asShort() {
        readAs(SnowparkVariantKind.FIXED_NUMBER);
        return (short) SnowparkVariantNodes.asInt(held());
    }

    public int asInt() {
        readAs(SnowparkVariantKind.FIXED_NUMBER);
        return SnowparkVariantNodes.asInt(held());
    }

    public long asLong() {
        readAs(SnowparkVariantKind.FIXED_NUMBER);
        return SnowparkVariantNodes.asLong(held());
    }

    public BigDecimal asBigDecimal() {
        readAs(SnowparkVariantKind.REAL_NUMBER);
        return held().isBoolean() ? new BigDecimal(SnowparkVariantNodes.asInt(held()))
            : SnowparkVariantNodes.decimal(held());
    }

    public BigInteger asBigInteger() {
        readAs(SnowparkVariantKind.FIXED_NUMBER);
        return held().isBoolean() ? BigInteger.valueOf(SnowparkVariantNodes.asInt(held()))
            : SnowparkVariantNodes.bigInteger(held());
    }

    /** The value as a text: a text without its quotes, bytes as hexadecimal digits, a tree as its JSON. */
    public String asString() {
        if (held().isBinary()) {
            return SnowparkVariantNodes.hex(held().binaryValue());
        }
        return held().isValueNode() ? SnowparkVariantNodes.text(held()) : SnowparkVariantNodes.json(held());
    }

    public String asJsonString() {
        if (held().isBinary()) {
            return "\"" + SnowparkVariantNodes.hex(held().binaryValue()) + "\"";
        }
        return SnowparkVariantNodes.json(held());
    }

    public JsonNode asJsonNode() {
        return value;
    }

    public byte[] asBinary() {
        readAs(SnowparkVariantKind.BINARY);
        return SnowparkVariantNodes.binary(held());
    }

    public Time asTime() {
        readAs(SnowparkVariantKind.TIME);
        return SnowparkVariantTimes.time(SnowparkVariantNodes.text(held()));
    }

    public Date asDate() {
        readAs(SnowparkVariantKind.DATE);
        return SnowparkVariantTimes.date(SnowparkVariantNodes.text(held()));
    }

    public Timestamp asTimestamp() {
        readAs(SnowparkVariantKind.TIMESTAMP);
        return held().isNumber() ? new Timestamp(SnowparkVariantNodes.asLong(held()))
            : SnowparkVariantTimes.timestamp(SnowparkVariantNodes.text(held()));
    }

    public boolean asBoolean() {
        readAs(SnowparkVariantKind.BOOLEAN);
        return SnowparkVariantNodes.asBoolean(held());
    }

    public Variant[] asArray() {
        if (!held().isArray()) {
            throw new UncheckedIOException(new IOException("Failed to convert " + SnowparkVariantNodes.text(held())
                + " to Array"));
        }
        final Variant[] elements = new Variant[held().size()];
        for (int i = 0; i < elements.length; i++) {
            elements[i] = new Variant(held().get(i), SnowparkVariantKind.STRING);
        }
        return elements;
    }

    public List<Variant> asList() {
        return Arrays.asList(asArray());
    }

    public Map<String, Variant> asMap() {
        if (!held().isObject()) {
            throw new UncheckedIOException(new IOException("Failed to convert " + SnowparkVariantNodes.text(held())
                + " to Map"));
        }
        final Map<String, Variant> members = new HashMap<>();
        for (final Map.Entry<String, JsonNode> member : held().properties()) {
            members.put(member.getKey(), new Variant(member.getValue(), SnowparkVariantKind.STRING));
        }
        return members;
    }

    /**
     * Hands the value to the engine's reader with what it was made as, which no reader of the class tells.
     *
     * @param reader the engine's reader
     * @return what the reader makes of the value
     */
    public Object handedTo(final SnowparkVariantReader reader) {
        return reader.read(kind, value);
    }

    /** Whether another {@code Variant} holds the same value, whatever either was made as. */
    @Override
    public boolean equals(final Object other) {
        return other instanceof Variant && SnowparkVariantNodes.same(held(), ((Variant) other).held());
    }

    @Override
    public int hashCode() {
        return SnowparkVariantNodes.hash(held());
    }

    /** An alias of {@link #asString()}. */
    @Override
    public String toString() {
        return asString();
    }

    /** The value, which one made from no text does not have. */
    private JsonNode held() {
        if (value == null) {
            throw new NullPointerException();
        }
        return value;
    }

    /** Refuses a reading the value's kind does not answer. */
    private void readAs(final SnowparkVariantKind target) {
        if (!kind.readsAs(target)) {
            throw new UncheckedIOException(new IOException("Conversion from Variant of " + kind.spelled() + " to "
                + target.spelled() + " is not supported"));
        }
    }
}
