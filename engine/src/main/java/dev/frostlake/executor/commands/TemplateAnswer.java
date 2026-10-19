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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.values.VariantValue;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * What a USING TEMPLATE query answered, read as the column descriptions it must be. An ARRAY is taken as it is.
 * A text is read as JSON — {@code '[{"COLUMN_NAME":"a","TYPE":"TEXT","NULLABLE":true}]'} makes the table — and
 * one that is no JSON is refused in the JSON reader's own words, the text quoted as its source (live-verified):
 *
 * <pre>
 *   'x'    Invalid template: Unrecognized token 'x': was expecting (JSON String, Number, Array, Object or token
 *          'null', 'true' or 'false')
 *           at [Source: (String)"x"; line: 1, column: 2]
 *   '[1'   Invalid template: Unexpected end-of-input: expected close marker for Array (start marker at [Source:
 *          (String)"[1"; line: 1, column: 1])
 *           at [Source: (String)"[1"; line: 1, column: 3]
 * </pre>
 *
 * <p>An empty text and any other value answer nothing, which the caller refuses as no array.
 */
final class TemplateAnswer {

    /** Reads with the text kept as its source, so that a place the reader names quotes it. */
    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build();

    private static final String UNRECOGNIZED = "Unrecognized token '";

    private TemplateAnswer() {
    }

    /**
     * The descriptions a template query's one cell holds.
     *
     * @param cell the cell
     * @return the JSON it holds, or null when it holds none
     */
    static JsonNode descriptions(final Object cell) {
        if (cell instanceof VariantValue) {
            return ((VariantValue) cell).node();
        }
        if (!(cell instanceof String) || ((String) cell).trim().isEmpty()) {
            return null;
        }
        final String text = (String) cell;
        try {
            return MAPPER.readTree(text);
        } catch (final JacksonException malformed) {
            throw new RuntimeException(SqlCompilationError.of("Invalid template: " + worded(malformed, text) + "\n"),
                malformed);
        }
    }

    /**
     * The reader's sentence and the place it stopped at. A token it does not know is placed after the token, not
     * before it.
     */
    private static String worded(final JacksonException malformed, final String text) {
        final String detail = malformed.getOriginalMessage() == null ? "" : malformed.getOriginalMessage();
        final TokenStreamLocation where = malformed.getLocation();
        if (where == null) {
            return detail;
        }
        int column = where.getColumnNr();
        if (detail.startsWith(UNRECOGNIZED)) {
            final int end = detail.indexOf('\'', UNRECOGNIZED.length());
            column += end < 0 ? 0 : end - UNRECOGNIZED.length();
        }
        return detail + "\n at [Source: (String)\"" + text + "\"; line: " + where.getLineNr() + ", column: " + column
            + "]";
    }
}
