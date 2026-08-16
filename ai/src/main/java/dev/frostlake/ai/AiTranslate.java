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
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * AI_TRANSLATE(text, from, to [, return_error_details]) — the newer spelling of
 * {@link CortexTranslate}, with the trailing error-detail flag Snowflake gives the whole AI_ family.
 */
public class AiTranslate extends BuiltInFunction {

    private final CortexTranslate translate = new CortexTranslate("SNOWFLAKE.CORTEX.TRANSLATE");

    public AiTranslate(final String name) {
        super(name, StringType.VARCHAR);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return translate.evaluate(args.subList(0, 3));
    }

    @Override
    public int getMinArgCount() { return 3; }
    @Override
    public int getMaxArgCount() { return 4; }
}
