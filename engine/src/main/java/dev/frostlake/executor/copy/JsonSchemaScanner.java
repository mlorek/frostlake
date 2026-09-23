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

package dev.frostlake.executor.copy;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The columns one staged JSON file gives INFER_SCHEMA.
 *
 * <p>Every top-level value is a record, and under STRIP_OUTER_ARRAY every element of a top-level array is. A
 * record must be an object: anything else refuses the call, naming what it found —
 * {@code Schema Inference failed: FIXED detected instead of an OBJECT. Please validate the JSON data or use
 * CSV file format}, with the internal names FIXED, REAL, TEXT, BOOLEAN and NULL_VALUE, and a sentence of its own
 * for an ARRAY that points at STRIP_OUTER_ARRAY.
 *
 * <p>An object's keys are read in the order the account keeps them, sorted, so a column's place is its key's
 * index among its record's sorted keys — in the first record that carries the key. Only the first level is read:
 * a nested object is an OBJECT and a nested array an ARRAY.
 *
 * <p>An object that names a key twice, at any depth, refuses the call as a malformed document does —
 * {@code Error parsing JSON: duplicate object attribute "a"}, at the second key's closing quote — unless the file
 * format sets ALLOW_DUPLICATE, when the key's last value is the one read. Such a refusal names the record's row,
 * and the line the record starts on when that is not the line of the fault: a record starts where the one before
 * it ended, the first one on line 1.
 */
final class JsonSchemaScanner {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final InferenceFile file;
    private final SchemaScanOptions options;
    private final RetainingReader text;
    private final JsonParser parser;
    private final Map<String, FileColumn> columns = new LinkedHashMap<String, FileColumn>();

    /** The records read so far, which is the current one's row. */
    private long records;

    /** The line the current record starts on. */
    private int startLine = 1;

    private JsonSchemaScanner(final InferenceFile file, final SchemaScanOptions options, final RetainingReader text,
                              final JsonParser parser) {
        this.file = file;
        this.options = options;
        this.text = text;
        this.parser = parser;
    }

    /**
     * Read one file's columns.
     *
     * @param file the file
     * @param options the call's options
     * @return the columns in the order their keys first appear, sorted within each record
     * @throws IOException when the file cannot be read
     */
    static List<FileColumn> scan(final InferenceFile file, final SchemaScanOptions options) throws IOException {
        try (RetainingReader text = new RetainingReader(StagedFileIo.reader(file.path()));
             JsonParser parser = MAPPER.createParser(text)) {
            return new JsonSchemaScanner(file, options, text, parser).read();
        }
    }

    private List<FileColumn> read() {
        JsonToken token = next();
        while (token != null) {
            if (token == JsonToken.START_ARRAY && options.stripOuterArray()) {
                token = next();
                while (token != null && token != JsonToken.END_ARRAY) {
                    if (!options.readsAllRecords() && records >= options.maxRecordsPerFile()) {
                        return new ArrayList<FileColumn>(columns.values());
                    }
                    readRecord(token);
                    token = next();
                }
            } else {
                if (!options.readsAllRecords() && records >= options.maxRecordsPerFile()) {
                    break;
                }
                readRecord(token);
            }
            token = next();
        }
        return new ArrayList<FileColumn>(columns.values());
    }

    /** One record's keys, taken into the file's columns; the next record starts where this one ends. */
    private void readRecord(final JsonToken token) {
        if (token != JsonToken.START_OBJECT) {
            throw notAnObject(token);
        }
        final Map<String, InferredType> types = new TreeMap<String, InferredType>();
        final Map<String, String> texts = new TreeMap<String, String>();
        final Set<String> seen = new HashSet<String>();
        JsonToken field = next();
        while (field == JsonToken.PROPERTY_NAME) {
            final String key = parser.currentName();
            requireFirstNaming(seen, key);
            final JsonToken value = next();
            types.put(key, valueType(value));
            texts.put(key, value == JsonToken.VALUE_STRING ? parser.getString() : null);
            if (value == JsonToken.START_OBJECT || value == JsonToken.START_ARRAY) {
                skipNested();
            }
            field = next();
        }
        int position = 0;
        for (final Map.Entry<String, InferredType> entry : types.entrySet()) {
            final String name = options.reportedName(entry.getKey());
            FileColumn column = columns.get(name);
            if (column == null) {
                column = new FileColumn(name, position, null, true);
                columns.put(name, column);
            }
            column.setType(InferredType.absorb(column.type(), entry.getValue(), texts.get(entry.getKey()),
                options.iceberg()));
            position++;
        }
        final TokenStreamLocation end = parser.currentTokenLocation();
        startLine = end.getLineNr();
        text.discardBefore(end.getCharOffset());
        records++;
    }

    /** Step over a nested object or array, which only its first level types, still judging its keys. */
    private void skipNested() {
        if (options.allowDuplicate()) {
            try {
                parser.skipChildren();
            } catch (final JacksonException malformed) {
                throw parseError(malformed);
            }
            return;
        }
        final Set<String> seen = new HashSet<String>();
        JsonToken token = next();
        while (token != null && token != JsonToken.END_OBJECT && token != JsonToken.END_ARRAY) {
            if (token == JsonToken.PROPERTY_NAME) {
                requireFirstNaming(seen, parser.currentName());
            } else if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                skipNested();
            }
            token = next();
        }
    }

    /** Refuse a key the object already named, unless ALLOW_DUPLICATE keeps the last value. */
    private void requireFirstNaming(final Set<String> seen, final String key) {
        if (seen.add(key) || options.allowDuplicate()) {
            return;
        }
        final TokenStreamLocation opening = parser.currentTokenLocation();
        long closing = opening.getCharOffset() + 1;
        int c = text.charAt(closing);
        while (c >= 0 && c != '"') {
            closing += c == '\\' ? 2 : 1;
            c = text.charAt(closing);
        }
        final int column = opening.getColumnNr() + (int) (closing - opening.getCharOffset());
        throw refusal("duplicate object attribute \"" + key + "\"", opening.getLineNr(), column);
    }

    /** A value's type; null for a JSON null. */
    private InferredType valueType(final JsonToken value) {
        switch (value) {
            case VALUE_NULL:
                return null;
            case VALUE_TRUE:
            case VALUE_FALSE:
                return InferredType.of(InferredKind.BOOLEAN);
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return StagedValueKinds.ofJsonNumber(parser.getString());
            case VALUE_STRING:
                return StagedValueKinds.ofJsonString(parser.getString());
            case START_ARRAY:
                return InferredType.of(InferredKind.ARRAY);
            case START_OBJECT:
                return InferredType.of(InferredKind.OBJECT);
            default:
                return InferredType.of(InferredKind.TEXT);
        }
    }

    /** The refusal of a record that is not an object. */
    private RuntimeException notAnObject(final JsonToken token) {
        if (token == JsonToken.START_ARRAY) {
            return new RuntimeException("Schema Inference failed: ARRAY detected instead of an OBJECT. Please consider"
                + " STRIP_OUTER_ARRAY or validate the JSON data");
        }
        final String found;
        switch (token) {
            case VALUE_STRING:
                found = InferredKind.TEXT.internalName();
                break;
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                found = StagedValueKinds.isJsonReal(parser.getString()) ? InferredKind.REAL.internalName()
                    : InferredKind.NUMBER.internalName();
                break;
            case VALUE_TRUE:
            case VALUE_FALSE:
                found = InferredKind.BOOLEAN.internalName();
                break;
            case VALUE_NULL:
                found = "NULL_VALUE";
                break;
            default:
                found = token.name();
                break;
        }
        return new RuntimeException("Schema Inference failed: " + found + " detected instead of an OBJECT. Please"
            + " validate the JSON data or use CSV file format");
    }

    /** The next token, a malformed document refused the way the account words it. */
    private JsonToken next() {
        try {
            return parser.nextToken();
        } catch (final JacksonException malformed) {
            throw parseError(malformed);
        }
    }

    /** A malformed document's refusal, naming where the parse stopped. */
    private RuntimeException parseError(final JacksonException malformed) {
        final TokenStreamLocation where = malformed.getLocation();
        final String detail = malformed.getOriginalMessage() == null ? "" : malformed.getOriginalMessage();
        final int end = detail.indexOf('\n');
        final RuntimeException refusal = where == null
            ? refusal(end < 0 ? detail : detail.substring(0, end), -1, -1)
            : refusal(end < 0 ? detail : detail.substring(0, end), where.getLineNr(), where.getColumnNr());
        refusal.initCause(malformed);
        return refusal;
    }

    /** A refusal of the current record at a line and character, or at none when the line is -1. */
    private RuntimeException refusal(final String detail, final int line, final int column) {
        final String at = line < 0 ? "" : ", line " + line + ", character " + column;
        final String row = line < 0 || line == startLine ? "Row " + records : "Row " + records + " starts at line "
            + startLine;
        return new RuntimeException("Error parsing JSON: " + detail + "\n  File '" + file.storedName() + "'" + at
            + "\n  " + row + ", column $1\n  File '" + file.relativeName() + "'" + at
            + "\n  Row 0 starts at line 0, column $1");
    }
}
