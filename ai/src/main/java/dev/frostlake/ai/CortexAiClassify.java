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

import java.util.ArrayList;
import java.util.List;

/**
 * AI_CLASSIFY(input, categories [, config]) — the newer classifier, whose answer is an OBJECT holding
 * an ARRAY under {@code labels} rather than the single {@code label} of {@link CortexClassifyText}.
 * Multi-label classification is what the array is for; a single-label call still gets a one-element
 * array, which is how Snowflake shapes it.
 */
public class CortexAiClassify extends BuiltInFunction {

    public CortexAiClassify(final String name) {
        super(name, ObjectType.OBJECT);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final String subject = CortexText.text(args.get(0));
        final List<String> categories = CortexText.categories(args.size() > 1 ? args.get(1) : null);
        if (subject == null || categories.isEmpty()) {
            return null;
        }
        final List<String> chosen = new ArrayList<>();
        chosen.add(CortexChoice.one(subject, categories));
        return VariantValue.of(CortexJson.objectOfArray("labels", chosen));
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 3; }
}
