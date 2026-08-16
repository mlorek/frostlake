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

package dev.frostlake.ai;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.ArrayType;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SPLIT_TEXT_MARKDOWN_HEADER(text, headers_to_split_on, chunk_size [, overlap]) — markdown cut at its
 * headings into an ARRAY of objects, each carrying the chunk text under {@code chunk} and the headings
 * in force where it begins under a NESTED {@code headers} object.
 *
 * <p>{@code headers_to_split_on} maps a heading marker to the name it is reported under —
 * {@code {#: h1, ##: h2}} — so a chunk under "# Title / ## Sub" comes back as
 * {@code {"chunk": "…", "headers": {"h1": "Title", "h2": "Sub"}}}. Measured, and NOT the flattened
 * shape this once guessed at: the headings live in their own object beside the text, so a heading
 * named "chunk" could not collide with it. A section longer than {@code chunk_size} is cut further and
 * every piece repeats the same heading metadata.
 *
 * <p>Two edges, both measured: text with no heading above it still yields one element, carrying an
 * EMPTY headers object rather than none; and a heading with no body under it yields NOTHING, so
 * {@code '# Only'} comes back as an empty array. A deeper heading keeps the shallower ones in force,
 * and a new shallow heading drops them.
 *
 * <p>Like its sibling, no model is involved.
 */
public class SplitTextMarkdownHeader extends BuiltInFunction {

    /** The key each element carries its text under. */
    private static final String CHUNK = "chunk";
    private static final String HEADERS = "headers";
    private static final int DEFAULT_OVERLAP = 0;

    public SplitTextMarkdownHeader(final String name) {
        super(name, ArrayType.ARRAY);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final String text = CortexText.text(args.get(0));
        final Map<String, String> headers = headerNames(args.size() > 1 ? args.get(1) : null);
        if (text == null || headers.isEmpty() || args.size() < 3 || args.get(2) == null) {
            return null;
        }
        final int chunkSize = ((Number) args.get(2)).intValue();
        final int overlap = args.size() > 3 && args.get(3) != null
            ? ((Number) args.get(3)).intValue() : DEFAULT_OVERLAP;

        final ArrayNode elements = CortexJson.array();
        final Map<String, String> inForce = new LinkedHashMap<>();
        final StringBuilder section = new StringBuilder();
        Map<String, String> sectionHeadings = new LinkedHashMap<>();
        for (final String line : text.split("\n", -1)) {
            final String marker = headerMarker(line, headers);
            if (marker == null) {
                if (section.length() > 0) {
                    section.append('\n');
                }
                section.append(line);
                continue;
            }
            emit(elements, sectionHeadings, section.toString(), chunkSize, overlap);
            section.setLength(0);
            demoteBelow(inForce, headers, marker);
            inForce.put(headers.get(marker), line.substring(marker.length()).trim());
            sectionHeadings = new LinkedHashMap<>(inForce);
        }
        emit(elements, sectionHeadings, section.toString(), chunkSize, overlap);
        return VariantValue.of(elements.toString());
    }

    /** One section, cut to size if it needs it, as one element per piece. */
    private void emit(final ArrayNode elements, final Map<String, String> headings,
                      final String section, final int chunkSize, final int overlap) {
        final String body = section.trim();
        if (body.isEmpty()) {
            return;
        }
        for (final String piece
                : TextChunker.chunk(body, ChunkSeparators.forFormat("none"), chunkSize, overlap)) {
            final ObjectNode element = CortexJson.entry();
            element.put(CHUNK, piece);
            final ObjectNode inForce = element.putObject(HEADERS);
            for (final Map.Entry<String, String> heading : headings.entrySet()) {
                inForce.put(heading.getKey(), heading.getValue());
            }
            elements.add(element);
        }
    }

    /** The heading marker this line opens with, or null when it is not a heading we split on. */
    private String headerMarker(final String line, final Map<String, String> headers) {
        String longest = null;
        for (final String marker : headers.keySet()) {
            if (line.startsWith(marker + " ")
                    && (longest == null || marker.length() > longest.length())) {
                longest = marker;
            }
        }
        return longest;
    }

    /**
     * A heading closes every deeper one: an {@code h1} after an {@code h2} means the {@code h2} no
     * longer applies, so the deeper names are dropped before the new one is recorded.
     */
    private void demoteBelow(final Map<String, String> inForce, final Map<String, String> headers,
                             final String marker) {
        final List<String> dropped = new ArrayList<>();
        for (final Map.Entry<String, String> header : headers.entrySet()) {
            if (header.getKey().length() >= marker.length()) {
                dropped.add(header.getValue());
            }
        }
        for (final String name : dropped) {
            inForce.remove(name);
        }
    }

    /** The marker-to-name map, however the engine handed the OBJECT over. */
    private Map<String, String> headerNames(final Object argument) {
        final Map<String, String> headers = new LinkedHashMap<>();
        if (argument == null) {
            return headers;
        }
        if (argument instanceof Map) {
            for (final Map.Entry<?, ?> entry : ((Map<?, ?>) argument).entrySet()) {
                headers.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
            return headers;
        }
        final JsonNode node = argument instanceof VariantValue
            ? ((VariantValue) argument).node()
            : OllamaClient.json().readTree(String.valueOf(argument));
        if (node != null && node.isObject()) {
            for (final Map.Entry<String, JsonNode> field : node.properties()) {
                headers.put(field.getKey(), field.getValue().asString());
            }
        }
        return headers;
    }

    @Override
    public int getMinArgCount() { return 3; }
    @Override
    public int getMaxArgCount() { return 4; }
}
