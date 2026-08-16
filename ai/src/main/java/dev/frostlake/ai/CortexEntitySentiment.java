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

import java.util.List;

/**
 * ENTITY_SENTIMENT(text [, entities]) — the earlier per-entity form of {@link CortexAiSentiment},
 * answering with the same {@code categories} object. Kept a separate function rather than an alias
 * because Snowflake keeps them separate, and a caller that names one is entitled to find it.
 */
public class CortexEntitySentiment extends BuiltInFunction {

    private final CortexAiSentiment aspects = new CortexAiSentiment("SNOWFLAKE.CORTEX.AI_SENTIMENT");

    public CortexEntitySentiment(final String name) {
        super(name, ObjectType.OBJECT);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object answered = aspects.evaluate(args);
        return answered instanceof VariantValue ? answered : null;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }
}
