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
import dev.frostlake.types.BooleanType;

import java.util.List;

/**
 * AI_FILTER(predicate [, value]) — TRUE or FALSE for a question asked in English, so a WHERE clause
 * can read {@code WHERE AI_FILTER(is this review about shipping?, body)}.
 *
 * <p>An answer the model hedges on is FALSE rather than NULL: the function exists to be a predicate,
 * and a three-valued answer would quietly drop rows from both sides of a filter.
 */
public class CortexAiFilter extends BuiltInFunction {

    public CortexAiFilter(final String name) {
        super(name, BooleanType.BOOLEAN);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final String question = CortexText.text(args.get(0));
        if (question == null) {
            return null;
        }
        final String subject = args.size() > 1 ? CortexText.text(args.get(1)) : null;
        if (args.size() > 1 && subject == null) {
            return null;
        }
        final String asked = subject == null ? question : question + "\n\n" + subject;
        final String reply = OllamaClient.generate(OllamaConfig.model(),
            "Answer the following with the single word true or false.\n\n" + asked,
            Double.valueOf(0.0), null);
        return Boolean.valueOf(CortexText.strip(reply).toLowerCase().startsWith("true"));
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }
}
