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
import tools.jackson.databind.node.ArrayNode;

import java.util.List;

/**
 * SPLIT_TEXT_RECURSIVE_CHARACTER(text, format, chunk_size [, overlap] [, separators]) — the text cut
 * into an ARRAY of chunks no longer than {@code chunk_size}, each carrying {@code overlap} characters
 * of its predecessor.
 *
 * <p>The only Cortex function in the pack that never contacts a model: chunking is arithmetic over the
 * text, so it runs locally, deterministically and for free. {@code format} chooses which structural
 * boundaries are preferred — {@code none}, {@code markdown}, {@code html}, {@code latex} — and an
 * explicit separator array overrides that choice entirely.
 */
public class SplitTextRecursiveCharacter extends BuiltInFunction {

    /** Snowflake's default when the call names no overlap. */
    private static final int DEFAULT_OVERLAP = 0;

    public SplitTextRecursiveCharacter(final String name) {
        super(name, ArrayType.ARRAY);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final String text = CortexText.text(args.get(0));
        final String format = args.size() > 1 ? CortexText.text(args.get(1)) : null;
        if (text == null || format == null || args.size() < 3 || args.get(2) == null) {
            return null;
        }
        final int chunkSize = ((Number) args.get(2)).intValue();
        final int overlap = args.size() > 3 && args.get(3) != null
            ? ((Number) args.get(3)).intValue() : DEFAULT_OVERLAP;

        List<String> separators = args.size() > 4 && args.get(4) != null
            ? CortexText.categories(args.get(4)) : null;
        if (separators == null || separators.isEmpty()) {
            separators = ChunkSeparators.forFormat(format);
        }
        if (separators == null) {
            throw new RuntimeException("Unsupported format for SPLIT_TEXT_RECURSIVE_CHARACTER: "
                + format + ". Supported: none, markdown, html, latex.");
        }
        final ArrayNode chunks = CortexJson.array();
        for (final String chunk : TextChunker.chunk(text, separators, chunkSize, overlap)) {
            chunks.add(chunk);
        }
        return VariantValue.of(chunks.toString());
    }

    @Override public int getMinArgCount() { return 3; }
    @Override public int getMaxArgCount() { return 5; }
}
