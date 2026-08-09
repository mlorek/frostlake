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
import dev.frostlake.types.VectorElementType;
import dev.frostlake.types.VectorType;
import dev.frostlake.values.VectorValue;

import java.util.List;

/**
 * EMBED_TEXT_768 / EMBED_TEXT_1024(model, text) — the text as a VECTOR(FLOAT, n), ready for
 * VECTOR_COSINE_SIMILARITY and the rest of the engine's vector functions.
 *
 * <p>The dimension is fixed by the function name, as it is in Snowflake, and the embedding model is
 * whatever {@code ai.ollama.embedModel} names — the model argument is carried for compatibility. A
 * local model rarely produces exactly 768 or 1024 numbers, so a shorter answer is zero-padded and a
 * longer one truncated: the declared width is part of the type, and a VECTOR whose length disagreed
 * with its type would not survive being stored in a column.
 */
public class CortexEmbedText extends BuiltInFunction {

    private final int dimension;

    public CortexEmbedText(final String name, final int dimension) {
        super(name, new VectorType(VectorElementType.FLOAT, dimension));
        this.dimension = dimension;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final String subject = args.size() > 1 ? CortexText.text(args.get(1)) : null;
        if (subject == null) {
            return null;
        }
        final List<Double> embedding = OllamaClient.embed(OllamaConfig.embedModel(), subject);
        final double[] fixedWidth = new double[dimension];
        for (int i = 0; i < dimension && i < embedding.size(); i++) {
            fixedWidth[i] = embedding.get(i).doubleValue();
        }
        return VectorValue.of(VectorElementType.FLOAT, fixedWidth);
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }
}
