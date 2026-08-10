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

/** SUMMARIZE(text) — a short summary of the text. */
public class CortexSummarize extends BuiltInFunction {

    public CortexSummarize(final String name) {
        super(name, StringType.VARCHAR);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final String subject = CortexText.text(args.get(0));
        if (subject == null) {
            return null;
        }
        return OllamaClient.generate(OllamaConfig.model(),
            "Summarize the text below in a few sentences. Reply with the summary only.\n\n" + subject,
            Double.valueOf(0.0), null).trim();
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
