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

import java.util.Arrays;
import java.util.List;

/**
 * AI_COUNT_TOKENS(function_name, …) — how many tokens a call to {@code function_name} would cost.
 * The leading argument names the function being priced, so where the text sits depends on it:
 * {@code AI_COUNT_TOKENS(AI_TRANSLATE, text, from, to)} prices a translation while
 * {@code AI_COUNT_TOKENS(AI_COMPLETE, model, prompt)} prices a completion, and the one-text form
 * {@code AI_COUNT_TOKENS(AI_SENTIMENT, input)} prices a classification.
 *
 * <p>Counted the same way {@link CortexCountTokens} counts, and for the same reason: a caller uses this
 * to size a prompt before sending it, so the answer should not itself cost a model call.
 */
public class AiCountTokens extends BuiltInFunction {

    /** The functions whose FIRST argument after the name is the text, not a model. */
    private static final List<String> TEXT_FIRST = Arrays.asList(
        "AI_SENTIMENT", "AI_SUMMARIZE", "AI_TRANSLATE", "AI_CLASSIFY", "AI_FILTER", "AI_EXTRACT");

    private final CortexCountTokens tokens = new CortexCountTokens("SNOWFLAKE.CORTEX.COUNT_TOKENS");

    public AiCountTokens(final String name) {
        super(name, NumericType.NUMBER);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final String pricedFunction = CortexText.text(args.get(0));
        if (pricedFunction == null || args.size() < 2) {
            return null;
        }
        final int textAt = TEXT_FIRST.contains(pricedFunction.trim().toUpperCase()) ? 1 : 2;
        if (args.size() <= textAt) {
            return null;
        }
        return tokens.evaluate(Arrays.asList(pricedFunction, args.get(textAt)));
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 6; }
}
