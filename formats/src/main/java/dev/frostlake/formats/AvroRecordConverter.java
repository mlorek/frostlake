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

package dev.frostlake.formats;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericFixed;
import org.apache.avro.generic.GenericRecord;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;

/**
 * Converts an Avro {@link GenericRecord} to a {@link JsonNode} (fields by name). Shared by the Avro reader
 * and the Parquet reader, which both surface rows as Avro {@code GenericRecord}s. Nested records/arrays/maps
 * recurse; bytes and fixed become base64; Utf8 / enum symbols become text.
 */
public final class AvroRecordConverter {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private AvroRecordConverter() {
    }

    public static ObjectNode toJson(final GenericRecord record) {
        final ObjectNode object = NODES.objectNode();
        for (final Schema.Field field : record.getSchema().getFields()) {
            object.set(field.name(), valueToJson(record.get(field.name())));
        }
        return object;
    }

    private static JsonNode valueToJson(final Object value) {
        if (value == null) {
            return NODES.nullNode();
        }
        if (value instanceof GenericRecord) {
            return toJson((GenericRecord) value);
        }
        if (value instanceof CharSequence) {
            return NODES.textNode(value.toString());   // Utf8 or String, also enum symbols
        }
        if (value instanceof Boolean) {
            return NODES.booleanNode((Boolean) value);
        }
        if (value instanceof Integer) {
            return NODES.numberNode(((Integer) value).intValue());
        }
        if (value instanceof Long) {
            return NODES.numberNode(((Long) value).longValue());
        }
        if (value instanceof Float) {
            return NODES.numberNode(((Float) value).floatValue());
        }
        if (value instanceof Double) {
            return NODES.numberNode(((Double) value).doubleValue());
        }
        if (value instanceof ByteBuffer) {
            final ByteBuffer buffer = ((ByteBuffer) value).duplicate();
            final byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return NODES.textNode(Base64.getEncoder().encodeToString(bytes));
        }
        if (value instanceof GenericFixed) {
            return NODES.textNode(Base64.getEncoder().encodeToString(((GenericFixed) value).bytes()));
        }
        if (value instanceof Collection) {
            final ArrayNode array = NODES.arrayNode();
            for (final Object element : (Collection<?>) value) {
                array.add(valueToJson(element));
            }
            return array;
        }
        if (value instanceof Map) {
            final ObjectNode object = NODES.objectNode();
            for (final Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                object.set(String.valueOf(entry.getKey()), valueToJson(entry.getValue()));
            }
            return object;
        }
        return NODES.textNode(value.toString());
    }
}
