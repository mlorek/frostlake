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

import dev.frostlake.executor.copy.StagedFileIo;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericFixed;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
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

    /** A record as a JSON object, a string holding a byte that is no part of UTF-8 read with U+FFFD in its place. */
    public static ObjectNode toJson(final GenericRecord record) {
        return toJson(record, true);
    }

    /**
     * A record as a JSON object, its strings read as UTF-8: a string holding a byte that is no part of UTF-8 is refused
     * (see {@link AvroUnreadableText}) unless REPLACE_INVALID_CHARACTERS reads each such byte as U+FFFD
     * (live-verified).
     *
     * @param record         the record
     * @param replaceInvalid whether REPLACE_INVALID_CHARACTERS = TRUE
     * @return the object
     */
    public static ObjectNode toJson(final GenericRecord record, final boolean replaceInvalid) {
        final ObjectNode object = NODES.objectNode();
        for (final Schema.Field field : record.getSchema().getFields()) {
            object.set(field.name(), valueToJson(record.get(field.name()), replaceInvalid));
        }
        return object;
    }

    /** An Avro string's text: its bytes read as UTF-8, a byte that is no part of it refused or replaced. */
    private static String text(final Utf8 value, final boolean replaceInvalid) {
        final byte[] bytes = new byte[value.getByteLength()];
        System.arraycopy(value.getBytes(), 0, bytes, 0, bytes.length);
        final String marked = StagedFileIo.markedUtf8(bytes);
        if (StagedFileIo.firstUnreadable(marked) < 0) {
            return marked;
        }
        if (!replaceInvalid) {
            throw new AvroUnreadableText(StagedFileIo.unreadableShown(marked));
        }
        return StagedFileIo.unreadableReplaced(marked);
    }

    private static JsonNode valueToJson(final Object value, final boolean replaceInvalid) {
        if (value == null) {
            return NODES.nullNode();
        }
        if (value instanceof GenericRecord) {
            return toJson((GenericRecord) value, replaceInvalid);
        }
        if (value instanceof Utf8) {
            return NODES.textNode(text((Utf8) value, replaceInvalid));
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
                array.add(valueToJson(element, replaceInvalid));
            }
            return array;
        }
        if (value instanceof Map) {
            final ObjectNode object = NODES.objectNode();
            for (final Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                object.set(String.valueOf(entry.getKey()), valueToJson(entry.getValue(), replaceInvalid));
            }
            return object;
        }
        return NODES.textNode(value.toString());
    }
}
