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
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * EXTRACT_ANSWER(text, question) — the answer to the question, taken from the text.
 *
 * <p>The result is an ARRAY of {@code {answer, score}} objects rather than a bare string: Snowflake
 * declares this function {@code RETURN ARRAY}, and a caller reads it as
 * {@code EXTRACT_ANSWER(…)[0]:answer}. One candidate is returned, since a local model is asked for its
 * single best answer rather than for a ranked set.
 */
public class CortexExtractAnswer extends BuiltInFunction {

    /** The confidence reported alongside an answer the local model gave without one. */
    private static final double ASSUMED_SCORE = 1.0;

    public CortexExtractAnswer(final String name) {
        super(name, ArrayType.ARRAY);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final String source = CortexText.text(args.get(0));
        final String question = args.size() > 1 ? CortexText.text(args.get(1)) : null;
        if (source == null || question == null) {
            return null;
        }
        final String answered = CortexText.terse(
            "Answer the question using only the source text below.\n\nQuestion: " + question,
            source);
        final ArrayNode answers = CortexJson.array();
        if (!answered.isEmpty()) {
            final ObjectNode candidate = CortexJson.entry();
            candidate.put("answer", answered);
            candidate.put("score", ASSUMED_SCORE);
            answers.add(candidate);
        }
        return VariantValue.of(answers.toString());
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
