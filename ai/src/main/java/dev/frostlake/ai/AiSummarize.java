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
 * AI_SUMMARIZE(text [, options] [, return_error_details]) — the newer spelling of
 * {@link CortexSummarize}, and a separate function rather than an alias because its arity is wider:
 * Snowflake gives it two trailing optional arguments where SUMMARIZE takes only an options object.
 */
public class AiSummarize extends BuiltInFunction {

    private final CortexSummarize summarize = new CortexSummarize("SNOWFLAKE.CORTEX.SUMMARIZE");

    public AiSummarize(final String name) {
        super(name, StringType.VARCHAR);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return summarize.evaluate(args.subList(0, 1));
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 3; }
}
