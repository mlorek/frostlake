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
import dev.frostlake.types.ObjectType;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * AI_SENTIMENT(text [, categories]) — sentiment per aspect rather than one number: an OBJECT holding
 * {@code categories}, an array of {@code {name, sentiment}} entries. Without a category list the one
 * entry is named {@code overall}, which is how Snowflake reports the whole-text reading.
 *
 * <p>Each sentiment is one of positive, negative, neutral, mixed or unknown — a fixed vocabulary, so
 * the model is offered exactly those and anything else settles on unknown.
 */
public class CortexAiSentiment extends BuiltInFunction {

    private static final List<String> SENTIMENTS =
        Arrays.asList("positive", "negative", "neutral", "mixed", "unknown");
    private static final String OVERALL = "overall";

    public CortexAiSentiment(final String name) {
        super(name, ObjectType.OBJECT);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final String subject = CortexText.text(args.get(0));
        if (subject == null) {
            return null;
        }
        final List<String> aspects = args.size() > 1
            ? CortexText.categories(args.get(1)) : new ArrayList<String>();
        if (aspects.isEmpty()) {
            aspects.add(OVERALL);
        }
        final ArrayNode categories = CortexJson.array();
        for (final String aspect : aspects) {
            final ObjectNode entry = CortexJson.entry();
            entry.put("name", aspect);
            entry.put("sentiment", sentimentOf(subject, aspect));
            categories.add(entry);
        }
        final ObjectNode result = CortexJson.entry();
        result.set("categories", categories);
        return VariantValue.of(result.toString());
    }

    private String sentimentOf(final String subject, final String aspect) {
        final String instruction = OVERALL.equals(aspect)
            ? "Give the overall sentiment of the text below. Answer with one of: %s."
            : "Give the sentiment the text below expresses about " + aspect
              + ". Answer with one of: %s.";
        return CortexChoice.one(instruction, subject, SENTIMENTS).toLowerCase();
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }
}
