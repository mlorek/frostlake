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
import dev.frostlake.types.NumericType;

import java.util.List;

/**
 * AI_SIMILARITY(a, b) — how alike two texts are, as the cosine similarity of their embeddings. The
 * value runs from -1 to 1 and is 1 for a text compared with itself, which makes it usable as an
 * ORDER BY key without further scaling.
 *
 * <p>Registered under the bare name only: Snowflake has no {@code SNOWFLAKE.CORTEX.AI_SIMILARITY}.
 */
public class AiSimilarity extends BuiltInFunction {

    public AiSimilarity(final String name) {
        super(name, NumericType.FLOAT);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final String left = CortexText.text(args.get(0));
        final String right = args.size() > 1 ? CortexText.text(args.get(1)) : null;
        if (left == null || right == null) {
            return null;
        }
        final List<Double> a = OllamaClient.embed(OllamaConfig.embedModel(), left);
        final List<Double> b = OllamaClient.embed(OllamaConfig.embedModel(), right);
        return Double.valueOf(CosineSimilarity.of(a, b));
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }
}
